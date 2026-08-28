package net.austizz.ultimate_auction_system.webadmin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.austizz.ultimate_auction_system.AuctionActionResult;
import net.austizz.ultimate_auction_system.AuctionAdminAuditEntry;
import net.austizz.ultimate_auction_system.AuctionAdminDashboardSnapshot;
import net.austizz.ultimate_auction_system.AuctionAdminSavedData;
import net.austizz.ultimate_auction_system.AuctionBidRecord;
import net.austizz.ultimate_auction_system.AuctionDeliverySavedData;
import net.austizz.ultimate_auction_system.AuctionEconomyReport;
import net.austizz.ultimate_auction_system.AuctionHouse;
import net.austizz.ultimate_auction_system.AuctionListingSummary;
import net.austizz.ultimate_auction_system.AuctionPlayerBan;
import net.austizz.ultimate_auction_system.AuctionRecoveryEntry;
import net.austizz.ultimate_auction_system.AuctionState;
import net.austizz.ultimate_auction_system.AuctionSuspicionSignal;
import net.austizz.ultimate_auction_system.Config;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardComponentDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardComponents;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardLayoutDefaults;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardPageDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardPanelDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardRegistrationResult;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardRequestContext;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardResponse;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardWidgetDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardWidgetType;
import net.austizz.ultimatebankingsystem.api.dashboard.UltimateBankingDashboardApiProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class UasDashboardBootstrap {
    private static final String DATA_WIDGET_ID = "page-data";
    private static final UUID WEB_ADMIN_ID = UUID.nameUUIDFromBytes("ultimate_auction_system:web_admin".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final String WEB_ADMIN_NAME = "UBS Web Admin";

    private UasDashboardBootstrap() {
    }

    public static void register() {
        DashboardDefinition dashboard = DashboardDefinition.builder(UltimateAuctionSystem.MODID, "Ultimate Auction System")
                .subtitle("Auction house operations, moderation, recovery, and audit controls")
                .icon("UAS")
                .order(20)
                .defaults(DashboardLayoutDefaults.ubs())
                .panel(nav("overview", "Overview", "Auction health, economy KPIs, and risk alerts.", 0))
                .panel(nav("auctions", "Auctions", "Inspect all listings and run settlement-safe admin actions.", 10))
                .panel(nav("players", "Players", "Player auction activity, deliveries, and auction-house bans.", 20))
                .panel(nav("moderation", "Moderation", "Banned item rules, suspicious bid signals, and config-backed controls.", 30))
                .panel(nav("recovery", "Recovery", "Recovered item custody and failed-settlement repair queue.", 40))
                .panel(nav("audit", "Audit", "Admin action trail and moderation events.", 50))
                .page(overviewPage())
                .page(auctionsPage())
                .page(playersPage())
                .page(moderationPage())
                .page(recoveryPage())
                .page(auditPage())
                .build();

        DashboardRegistrationResult result = UltimateBankingDashboardApiProvider.registry().registerDashboard(dashboard);
        if (!result.success()) {
            UltimateAuctionSystem.LOGGER.warn("[UAS WebAdmin] Failed to register UBS dashboard addon: {}", result.message());
        }
    }

    private static DashboardPanelDefinition nav(String id, String title, String subtitle, int order) {
        return DashboardPanelDefinition.builder(id, title)
                .subtitle(subtitle)
                .order(order)
                .widget(DashboardWidgetDefinition.builder(DATA_WIDGET_ID, DashboardWidgetType.OUTPUT)
                        .title(title + " API")
                        .subtitle("Serves UAS dashboard data and actions for this page.")
                        .routeHandler(UasDashboardBootstrap::route)
                        .build())
                .build();
    }

    private static DashboardPageDefinition overviewPage() {
        return page("overview", "Overview", "Auction health, economy KPIs, and risk alerts.", 0, routeUrl("overview", "snapshot"),
                DashboardComponents.kpiGroup("overview-kpis", "metrics", List.of(
                        card("Active Auctions", "activeAuctions", "number"),
                        card("Claimable/Sold", "claimableAuctions", "number"),
                        card("Failed Settlements", "failedSettlements", "number"),
                        card("Bundles", "bundleAuctions", "number"),
                        card("Bid Volume", "bidVolume", "money"),
                        card("Sold Value", "soldValue", "money"),
                        card("Listing Fees", "estimatedListingFees", "raw"),
                        card("Sales Tax", "estimatedSalesTax", "raw")
                )).option("placement", "top").build(),
                DashboardComponents.panel("overview-alerts-panel", "Operational Alerts")
                        .child(component("overview-alerts", DashboardComponents.ALERT_LIST).dataPath("alerts").build())
                        .build(),
                DashboardComponents.twoColumn("overview-charts")
                        .child(DashboardComponents.chartPanel("auction-state-chart", "Auction State Mix", DashboardComponents.BAR_CHART, "charts.stateDistribution").build())
                        .child(DashboardComponents.chartPanel("economy-window-chart", "Economy Windows", DashboardComponents.BAR_CHART, "charts.economyWindows").build())
                        .build(),
                DashboardComponents.twoColumn("overview-top-tables")
                        .child(table("top-sellers", "Top Sellers", "topSellers", List.of(
                                col("Seller", "label"),
                                col("Sales", "count", "number"),
                                col("Amount", "amount")
                        )))
                        .child(table("top-items", "Top Items", "topItems", List.of(
                                col("Item", "label"),
                                col("Sales", "count", "number"),
                                col("Amount", "amount")
                        )))
                        .build(),
                DashboardComponents.twoColumn("overview-queues")
                        .child(table("failed-settlements", "Failed Settlements", "failedSettlementsRows", auctionQueueColumns()))
                        .child(table("restricted-listings", "Restricted Active Listings", "restrictedRows", auctionQueueColumns()))
                        .build()
        );
    }

    private static DashboardPageDefinition auctionsPage() {
        return page("auctions", "Auctions", "Inspect all listings and run settlement-safe admin actions.", 10, routeUrl("auctions", "snapshot"),
                DashboardComponents.kpiGroup("auction-kpis", "metrics", List.of(
                        card("Total Listings", "totalAuctions", "number"),
                        card("Active", "activeAuctions", "number"),
                        card("Ended", "endedAuctions", "number"),
                        card("Cancelled", "cancelledAuctions", "number"),
                        card("Sealed Bids", "sealedAuctions", "number"),
                        card("Watched", "watchedAuctions", "number")
                )).option("compact", true).build(),
                actionFormPanel("auction-admin-actions", "Auction Admin Actions", routeUrl("auctions", "action"), List.of(
                        section("Force-cancel auction", "Optionally moves auction contents to admin recovery for later release.",
                                List.of(
                                        field("auctionId", "Auction ID", "text", "UUID"),
                                        field("reason", "Reason", "text", "Required admin reason"),
                                        select("recoverItems", "Item handling", List.of(
                                                option("false", "Refund bidder and return item"),
                                                option("true", "Recover item for admin review")
                                        ))
                                ),
                                List.of(action("Force Cancel", "ADMIN_FORCE_CANCEL", "danger", List.of("auctionId", "reason"),
                                        map("auctionId", "$auctionId", "reason", "$reason", "recoverItems", "$recoverItems"),
                                        "Force-cancel this auction?")))
                        ,
                        section("Retry failed settlement", "Retries failed seller payout and delivery settlement.",
                                List.of(field("retryAuctionId", "Auction ID", "text", "Failed-settlement auction UUID")),
                                List.of(action("Retry Settlement", "ADMIN_RETRY_SETTLEMENT", "primary", List.of("retryAuctionId"),
                                        map("auctionId", "$retryAuctionId"),
                                        "Retry settlement for this auction?")))
                )),
                table("auction-table", "All Auctions", "auctionRows", auctionColumns())
        );
    }

    private static DashboardPageDefinition playersPage() {
        return page("players", "Players", "Player auction activity, deliveries, and auction-house bans.", 20, routeUrl("players", "snapshot"),
                DashboardComponents.kpiGroup("players-kpis", "metrics", List.of(
                        card("Tracked Players", "trackedPlayers", "number"),
                        card("Banned Players", "activeBans", "number"),
                        card("Open Deliveries", "openDeliveries", "number"),
                        card("Active Sellers", "activeSellers", "number"),
                        card("Active Bidders", "activeBidders", "number")
                )).option("compact", true).build(),
                actionFormPanel("player-ban-actions", "Player Controls", routeUrl("players", "action"), List.of(
                        section("Apply auction-house ban", "Blocks selected auction actions without banning the player from the server.",
                                List.of(
                                        field("playerId", "Player UUID", "text", "UUID"),
                                        field("playerName", "Player Name", "text", "Optional display name"),
                                        field("banReason", "Reason", "text", "Required moderation reason"),
                                        field("expiresAt", "Expires At", "text", "ISO date-time or blank"),
                                        select("blockCreate", "Block create", yesNoOptions(true)),
                                        select("blockBid", "Block bid", yesNoOptions(true)),
                                        select("blockBuyout", "Block buyout", yesNoOptions(true)),
                                        select("blockWatch", "Block watch/notify", yesNoOptions(false))
                                ),
                                List.of(action("Apply Ban", "APPLY_BAN", "danger", List.of("playerId", "banReason"),
                                        map("playerId", "$playerId", "playerName", "$playerName", "reason", "$banReason", "expiresAt", "$expiresAt",
                                                "blockCreate", "$blockCreate", "blockBid", "$blockBid", "blockBuyout", "$blockBuyout", "blockWatch", "$blockWatch"),
                                        "Apply this auction-house ban?")))
                        ,
                        section("Revoke auction-house ban", "Re-enables the player's auction-house actions.",
                                List.of(
                                        field("revokePlayerId", "Player UUID", "text", "UUID"),
                                        field("revokeReason", "Reason", "text", "Optional unban reason")
                                ),
                                List.of(action("Revoke Ban", "REVOKE_BAN", "primary", List.of("revokePlayerId"),
                                        map("playerId", "$revokePlayerId", "reason", "$revokeReason"),
                                        "Revoke this auction-house ban?")))
                )),
                DashboardComponents.twoColumn("players-tables")
                        .child(table("players-table", "Player Activity", "playerRows", List.of(
                                col("Player", "playerName"),
                                col("Active", "activeListings", "number"),
                                col("Bids", "bidCount", "number"),
                                col("Sold", "soldCount", "number"),
                                col("Bought", "boughtCount", "number"),
                                col("Deliveries", "deliveryCount", "number"),
                                col("Ban", "banActive", "boolean")
                        )))
                        .child(table("bans-table", "Auction-House Bans", "banRows", List.of(
                                col("Player", "playerName"),
                                col("Active", "active", "boolean"),
                                col("Create", "blockCreate", "boolean"),
                                col("Bid", "blockBid", "boolean"),
                                col("Buyout", "blockBuyout", "boolean"),
                                col("Reason", "reason"),
                                col("Expires", "expiresAt")
                        )))
                        .build()
        );
    }

    private static DashboardPageDefinition moderationPage() {
        return page("moderation", "Moderation", "Banned item rules, suspicious bid signals, and config-backed controls.", 30, routeUrl("moderation", "snapshot"),
                DashboardComponents.kpiGroup("moderation-kpis", "metrics", List.of(
                        card("Banned Rules", "bannedEntryCount", "number"),
                        card("Restricted Active", "restrictedActiveListings", "number"),
                        card("Suspicion Signals", "suspicionSignals", "number"),
                        card("Active Bans", "activeBans", "number")
                )).option("compact", true).build(),
                actionFormPanel("banned-entry-actions", "Banned Auction Entries", routeUrl("moderation", "action"), List.of(
                        section("Add banned entry", "Use item IDs, #tag IDs, or @mod IDs. Saved back into the UAS config.",
                                List.of(field("bannedEntry", "Entry", "text", "minecraft:bedrock, #minecraft:logs, or @examplemod")),
                                List.of(action("Add Entry", "ADD_BANNED_ENTRY", "danger", List.of("bannedEntry"),
                                        map("bannedEntry", "$bannedEntry"),
                                        "Add this banned auction entry?"))),
                        section("Remove banned entry", "Removes the normalized entry from the live config and saves it.",
                                List.of(field("removeBannedEntry", "Entry", "text", "Entry to remove")),
                                List.of(action("Remove Entry", "REMOVE_BANNED_ENTRY", "primary", List.of("removeBannedEntry"),
                                        map("bannedEntry", "$removeBannedEntry"),
                                        "Remove this banned auction entry?")))
                )),
                DashboardComponents.twoColumn("moderation-tables")
                        .child(table("banned-entries-table", "Current Banned Entries", "bannedEntryRows", List.of(
                                col("Entry", "entry"),
                                col("Type", "type"),
                                col("Label", "label"),
                                col("Active Matches", "matchingActiveAuctions", "number")
                        )))
                        .child(table("suspicion-table", "Suspicious Activity", "suspicionRows", List.of(
                                col("Type", "type"),
                                col("Auction", "auctionId", "id"),
                                col("Item", "itemName"),
                                col("Primary", "primaryPlayerName"),
                                col("Secondary", "secondaryPlayerName"),
                                col("Evidence", "evidenceCount", "number"),
                                col("Observed", "observedAt")
                        )))
                        .build()
        );
    }

    private static DashboardPageDefinition recoveryPage() {
        return page("recovery", "Recovery", "Recovered item custody and failed-settlement repair queue.", 40, routeUrl("recovery", "snapshot"),
                DashboardComponents.kpiGroup("recovery-kpis", "metrics", List.of(
                        card("Active Recoveries", "activeRecoveries", "number"),
                        card("Released Recoveries", "releasedRecoveries", "number"),
                        card("Failed Settlements", "failedSettlements", "number")
                )).option("compact", true).build(),
                actionFormPanel("recovery-actions", "Recovery Actions", routeUrl("recovery", "action"), List.of(
                        section("Release recovery item", "Delivers recovered auction contents back to the seller delivery queue.",
                                List.of(
                                        field("recoveryId", "Recovery ID", "text", "UUID"),
                                        field("releaseReason", "Reason", "text", "Optional release reason")
                                ),
                                List.of(action("Release Recovery", "ADMIN_RELEASE_RECOVERY", "primary", List.of("recoveryId"),
                                        map("recoveryId", "$recoveryId", "reason", "$releaseReason"),
                                        "Release this recovery entry?")))
                )),
                table("recovery-table", "Recovery Queue", "recoveryRows", List.of(
                        col("Recovery", "recoveryId", "id"),
                        col("Auction", "auctionId", "id"),
                        col("Seller", "sellerName"),
                        col("Item", "itemName"),
                        col("Items", "totalItemCount", "number"),
                        col("Active", "active", "boolean"),
                        col("Reason", "reason"),
                        col("Recovered", "recoveredAt")
                )),
                table("recovery-failed-table", "Failed Settlements", "failedSettlementsRows", auctionQueueColumns())
        );
    }

    private static DashboardPageDefinition auditPage() {
        return page("audit", "Audit", "Admin action trail and moderation events.", 50, routeUrl("audit", "snapshot"),
                DashboardComponents.kpiGroup("audit-kpis", "metrics", List.of(
                        card("Audit Events", "auditEvents", "number"),
                        card("Successful", "successfulAuditEvents", "number"),
                        card("Failed", "failedAuditEvents", "number")
                )).option("compact", true).build(),
                table("audit-table", "Audit Log", "auditRows", List.of(
                        col("When", "createdAt"),
                        col("Action", "action"),
                        col("Admin", "adminName"),
                        col("Target", "target"),
                        col("Success", "success", "boolean"),
                        col("Reason", "reason"),
                        col("Message", "message")
                ))
        );
    }

    private static DashboardPageDefinition page(String id,
                                                String title,
                                                String subtitle,
                                                int order,
                                                String dataUrl,
                                                DashboardComponentDefinition... components) {
        DashboardPageDefinition.Builder builder = DashboardPageDefinition.builder(id, title)
                .subtitle(subtitle)
                .order(order)
                .dataUrl(dataUrl)
                .routePattern("#/d/" + UltimateAuctionSystem.MODID + "/" + id);
        for (DashboardComponentDefinition component : components) {
            builder.component(component);
        }
        return builder.build();
    }

    private static DashboardResponse route(DashboardRequestContext context, String routePath, JsonObject body) {
        String method = context == null ? "" : context.method();
        String path = normalizeRoute(routePath);
        if ("POST".equalsIgnoreCase(method) || "action".equals(path)) {
            return handleAction(context, body == null ? new JsonObject() : body);
        }
        if (!"GET".equalsIgnoreCase(method)) {
            return DashboardResponse.methodNotAllowed("Use GET for UAS dashboard snapshots or POST for actions.");
        }
        return snapshotResponse(context);
    }

    private static DashboardResponse snapshotResponse(DashboardRequestContext context) {
        try {
            DashboardData data = dashboardData(context == null ? null : context.server());
            String panel = context == null ? "overview" : context.panelId();
            return DashboardResponse.ok(switch (panel) {
                case "auctions" -> auctionsPayload(data);
                case "players" -> playersPayload(data);
                case "moderation" -> moderationPayload(data);
                case "recovery" -> recoveryPayload(data);
                case "audit" -> auditPayload(data);
                default -> overviewPayload(data);
            });
        } catch (RuntimeException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS WebAdmin] Snapshot failed: {}", exception.getMessage());
            return DashboardResponse.serverError("UAS dashboard snapshot failed: " + exception.getMessage());
        }
    }

    private static DashboardResponse handleAction(DashboardRequestContext context, JsonObject body) {
        if (context == null || context.server() == null) {
            return DashboardResponse.serverError("Minecraft server is unavailable.");
        }
        AuctionHouse house = UltimateAuctionSystem.auctionHouse;
        if (house == null) {
            return DashboardResponse.serverError("Auction house is not initialized.");
        }
        AuctionAdminSavedData adminData = safeAdminData(context.server());
        if (adminData == null) {
            return DashboardResponse.serverError("Auction admin saved data is unavailable.");
        }
        AuctionDeliverySavedData deliveryData = safeDeliveryData(context.server());
        String action = string(body, "action").trim().toUpperCase(Locale.ROOT);
        AuctionActionResult result;
        try {
            result = switch (action) {
                case "ADMIN_FORCE_CANCEL" -> audited(
                        adminData,
                        "ADMIN_FORCE_CANCEL",
                        string(body, "auctionId"),
                        string(body, "reason"),
                        house.adminForceCancel(
                                WEB_ADMIN_ID,
                                WEB_ADMIN_NAME,
                                true,
                                uuid(body, "auctionId"),
                                deliveryData,
                                adminData,
                                bool(body, "recoverItems", false),
                                string(body, "reason")
                        )
                );
                case "ADMIN_RETRY_SETTLEMENT" -> audited(
                        adminData,
                        "ADMIN_RETRY_SETTLEMENT",
                        string(body, "auctionId"),
                        "UBS web admin settlement retry",
                        house.adminRetrySettlement(WEB_ADMIN_ID, WEB_ADMIN_NAME, true, uuid(body, "auctionId"), deliveryData)
                );
                case "ADMIN_RELEASE_RECOVERY" -> audited(
                        adminData,
                        "ADMIN_RELEASE_RECOVERY",
                        string(body, "recoveryId"),
                        string(body, "reason"),
                        house.adminReleaseRecovery(WEB_ADMIN_ID, WEB_ADMIN_NAME, true, uuid(body, "recoveryId"), deliveryData, adminData, string(body, "reason"))
                );
                case "APPLY_BAN" -> applyPlayerBan(context.server(), adminData, body);
                case "REVOKE_BAN" -> revokePlayerBan(adminData, body);
                case "ADD_BANNED_ENTRY" -> addBannedEntry(adminData, body);
                case "REMOVE_BANNED_ENTRY" -> removeBannedEntry(adminData, body);
                default -> AuctionActionResult.fail("Unknown UAS dashboard action: " + action);
            };
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            result = AuctionActionResult.fail(exception.getMessage());
        } catch (RuntimeException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS WebAdmin] Action {} failed: {}", action, exception.getMessage());
            result = AuctionActionResult.fail("UAS dashboard action failed: " + exception.getMessage());
        }
        return DashboardResponse.ok(actionPayload(result));
    }

    private static AuctionActionResult applyPlayerBan(MinecraftServer server, AuctionAdminSavedData adminData, JsonObject body) {
        UUID playerId = uuid(body, "playerId");
        if (playerId == null) {
            return AuctionActionResult.fail("Player UUID is required.");
        }
        boolean blockCreate = bool(body, "blockCreate", true);
        boolean blockBid = bool(body, "blockBid", true);
        boolean blockBuyout = bool(body, "blockBuyout", true);
        boolean blockWatch = bool(body, "blockWatch", false);
        if (!blockCreate && !blockBid && !blockBuyout && !blockWatch) {
            return AuctionActionResult.fail("Select at least one auction-house action to block.");
        }
        LocalDateTime expiresAt = dateTime(body, "expiresAt");
        String playerName = playerName(server, playerId, string(body, "playerName"));
        adminData.applyBan(
                playerId,
                playerName,
                blockCreate,
                blockBid,
                blockBuyout,
                blockWatch,
                string(body, "reason"),
                expiresAt,
                WEB_ADMIN_ID,
                WEB_ADMIN_NAME
        );
        return AuctionActionResult.ok("Auction-house ban updated for " + playerName + ".");
    }

    private static AuctionActionResult revokePlayerBan(AuctionAdminSavedData adminData, JsonObject body) {
        UUID playerId = uuid(body, "playerId");
        if (playerId == null) {
            return AuctionActionResult.fail("Player UUID is required.");
        }
        boolean revoked = adminData.revokeBan(playerId, WEB_ADMIN_ID, WEB_ADMIN_NAME, string(body, "reason"));
        return revoked ? AuctionActionResult.ok("Auction-house ban revoked.") : AuctionActionResult.fail("No auction-house ban exists for that player.");
    }

    private static AuctionActionResult addBannedEntry(AuctionAdminSavedData adminData, JsonObject body) {
        String entry = Config.normalizeAuctionRestriction(string(body, "bannedEntry"));
        if (!Config.isValidAuctionRestriction(entry)) {
            adminData.addAudit("BANNED_ENTRY_ADD", WEB_ADMIN_ID, WEB_ADMIN_NAME, entry, "UBS web admin banned-entry add", false, "Invalid banned auction entry.");
            return AuctionActionResult.fail("Invalid banned auction entry.");
        }
        List<String> entries = new ArrayList<>(Config.bannedAuctionEntries);
        if (!entries.contains(entry)) {
            entries.add(entry);
        }
        AuctionActionResult result = Config.replaceBannedAuctionEntries(entries);
        adminData.addAudit("BANNED_ENTRY_ADD", WEB_ADMIN_ID, WEB_ADMIN_NAME, entry, "UBS web admin banned-entry add", result.success(), result.message());
        return result;
    }

    private static AuctionActionResult removeBannedEntry(AuctionAdminSavedData adminData, JsonObject body) {
        String entry = Config.normalizeAuctionRestriction(string(body, "bannedEntry"));
        List<String> entries = new ArrayList<>(Config.bannedAuctionEntries);
        boolean removed = entries.removeIf(current -> entry.equals(Config.normalizeAuctionRestriction(current)));
        AuctionActionResult result = removed
                ? Config.replaceBannedAuctionEntries(entries)
                : AuctionActionResult.fail("Banned auction entry was not found.");
        adminData.addAudit("BANNED_ENTRY_REMOVE", WEB_ADMIN_ID, WEB_ADMIN_NAME, entry, "UBS web admin banned-entry remove", result.success(), result.message());
        return result;
    }

    private static AuctionActionResult audited(AuctionAdminSavedData adminData,
                                               String action,
                                               String target,
                                               String reason,
                                               AuctionActionResult result) {
        adminData.addAudit(action, WEB_ADMIN_ID, WEB_ADMIN_NAME, target, reason, result.success(), result.message());
        return result;
    }

    private static Map<String, Object> overviewPayload(DashboardData data) {
        Map<String, Object> payload = basePayload(data);
        payload.put("alerts", alerts(data));
        payload.put("charts", charts(data));
        payload.put("topSellers", economyRows(data.allReport().topSellers()));
        payload.put("topItems", economyRows(data.allReport().topItems()));
        payload.put("failedSettlementsRows", listingRows(data.admin.failedSettlements()));
        payload.put("restrictedRows", listingRows(data.admin.restrictedListings()));
        return payload;
    }

    private static Map<String, Object> auctionsPayload(DashboardData data) {
        Map<String, Object> payload = basePayload(data);
        payload.put("auctionRows", listingRows(data.listings));
        return payload;
    }

    private static Map<String, Object> playersPayload(DashboardData data) {
        Map<String, Object> payload = basePayload(data);
        payload.put("playerRows", data.admin.players().stream().map(UasDashboardBootstrap::playerRow).toList());
        payload.put("banRows", data.admin.bans().stream().map(UasDashboardBootstrap::banRow).toList());
        return payload;
    }

    private static Map<String, Object> moderationPayload(DashboardData data) {
        Map<String, Object> payload = basePayload(data);
        payload.put("bannedEntryRows", data.admin.bannedEntries().stream().map(UasDashboardBootstrap::bannedEntryRow).toList());
        payload.put("suspicionRows", data.admin.suspicionSignals().stream().map(UasDashboardBootstrap::suspicionRow).toList());
        return payload;
    }

    private static Map<String, Object> recoveryPayload(DashboardData data) {
        Map<String, Object> payload = basePayload(data);
        payload.put("recoveryRows", data.admin.recoveryEntries().stream().map(UasDashboardBootstrap::recoveryRow).toList());
        payload.put("failedSettlementsRows", listingRows(data.admin.failedSettlements()));
        return payload;
    }

    private static Map<String, Object> auditPayload(DashboardData data) {
        Map<String, Object> payload = basePayload(data);
        payload.put("auditRows", data.admin.auditLog().stream().map(UasDashboardBootstrap::auditRow).toList());
        return payload;
    }

    private static Map<String, Object> basePayload(DashboardData data) {
        Map<String, Object> payload = map(
                "ok", true,
                "generatedAt", data.admin.generatedAt(),
                "metrics", metrics(data)
        );
        return payload;
    }

    private static Map<String, Object> metrics(DashboardData data) {
        long active = data.listings.stream().filter(row -> row.state() == AuctionState.ACTIVE).count();
        long ended = data.listings.stream().filter(row -> row.state() == AuctionState.ENDED).count();
        long cancelled = data.listings.stream().filter(row -> row.state() == AuctionState.CANCELLED).count();
        long claimable = data.listings.stream().filter(row -> row.state() == AuctionState.CLAIMED).count();
        long failed = data.listings.stream().filter(row -> row.state() == AuctionState.FAILED_SETTLEMENT).count();
        long sealed = data.listings.stream().filter(row -> row.format() != null && row.format().name().equals("SEALED_BID")).count();
        long bundles = data.listings.stream().filter(AuctionListingSummary::bundle).count();
        long watched = data.listings.stream().filter(row -> row.notificationSubscriberCount() > 0).count();
        int bids = data.listings.stream().mapToInt(AuctionListingSummary::bidCount).sum();
        int activeBidders = (int) data.listings.stream()
                .flatMap(row -> row.bidHistory().stream())
                .filter(AuctionBidRecord::isAccepted)
                .map(AuctionBidRecord::getBidderId)
                .filter(id -> id != null)
                .distinct()
                .count();
        BigDecimal bidVolume = data.listings.stream()
                .flatMap(row -> row.bidHistory().stream())
                .filter(AuctionBidRecord::isAccepted)
                .map(AuctionBidRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal soldValue = data.listings.stream()
                .filter(row -> row.state() == AuctionState.CLAIMED && row.highestBidderId() != null)
                .map(AuctionListingSummary::currentBid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        AuctionAdminDashboardSnapshot.Stats allStats = allStats(data.admin);
        return map(
                "totalAuctions", data.listings.size(),
                "activeAuctions", active,
                "endedAuctions", ended,
                "cancelledAuctions", cancelled,
                "claimableAuctions", claimable,
                "failedSettlements", failed,
                "sealedAuctions", sealed,
                "bundleAuctions", bundles,
                "watchedAuctions", watched,
                "bidCount", bids,
                "bidVolume", bidVolume,
                "soldValue", soldValue,
                "estimatedListingFees", allStats == null ? "" : allStats.estimatedListingFees(),
                "estimatedSalesTax", allStats == null ? "" : allStats.estimatedSalesTax(),
                "averageSale", allStats == null ? "" : allStats.averageSale(),
                "trackedPlayers", data.admin.players().size(),
                "activeBans", data.admin.players().stream().filter(AuctionAdminDashboardSnapshot.Player::banActive).count(),
                "openDeliveries", data.admin.players().stream().mapToInt(AuctionAdminDashboardSnapshot.Player::deliveryCount).sum(),
                "activeSellers", data.listings.stream().filter(row -> row.state() == AuctionState.ACTIVE).map(AuctionListingSummary::sellerId).filter(id -> id != null).distinct().count(),
                "activeBidders", activeBidders,
                "bannedEntryCount", data.admin.bannedEntries().size(),
                "restrictedActiveListings", data.admin.restrictedListings().size(),
                "suspicionSignals", data.admin.suspicionSignals().size(),
                "activeRecoveries", data.admin.recoveryEntries().stream().filter(AuctionRecoveryEntry::active).count(),
                "releasedRecoveries", data.admin.recoveryEntries().stream().filter(entry -> !entry.active()).count(),
                "auditEvents", data.admin.auditLog().size(),
                "successfulAuditEvents", data.admin.auditLog().stream().filter(AuctionAdminAuditEntry::success).count(),
                "failedAuditEvents", data.admin.auditLog().stream().filter(entry -> !entry.success()).count()
        );
    }

    private static List<Map<String, Object>> alerts(DashboardData data) {
        ArrayList<Map<String, Object>> alerts = new ArrayList<>();
        if (!data.admin.failedSettlements().isEmpty()) {
            alerts.add(map("tone", "warn", "text", data.admin.failedSettlements().size() + " failed settlement(s) need review."));
        }
        if (!data.admin.restrictedListings().isEmpty()) {
            alerts.add(map("tone", "warn", "text", data.admin.restrictedListings().size() + " active listing(s) now match banned item rules."));
        }
        long activeRecoveries = data.admin.recoveryEntries().stream().filter(AuctionRecoveryEntry::active).count();
        if (activeRecoveries > 0) {
            alerts.add(map("tone", "warn", "text", activeRecoveries + " recovery entr" + (activeRecoveries == 1 ? "y is" : "ies are") + " awaiting release."));
        }
        if (!data.admin.suspicionSignals().isEmpty()) {
            alerts.add(map("tone", "warn", "text", data.admin.suspicionSignals().size() + " suspicious bidding signal(s) detected."));
        }
        if (alerts.isEmpty()) {
            alerts.add(map("tone", "ok", "text", "No auction-house alerts right now."));
        }
        return alerts;
    }

    private static Map<String, Object> charts(DashboardData data) {
        EnumMap<AuctionState, Integer> counts = new EnumMap<>(AuctionState.class);
        for (AuctionListingSummary row : data.listings) {
            counts.merge(row.state(), 1, Integer::sum);
        }
        List<Map<String, Object>> stateDistribution = counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> map("bankName", "Auction State", "type", label(entry.getKey().name()), "value", entry.getValue()))
                .toList();
        List<Map<String, Object>> economyWindows = data.admin.stats().stream()
                .map(stat -> map("bankName", "Window " + stat.label(), "type", stat.label() + " created", "value", stat.auctionsCreated()))
                .toList();
        return map("stateDistribution", stateDistribution, "economyWindows", economyWindows);
    }

    private static DashboardData dashboardData(MinecraftServer server) {
        if (server == null) {
            throw new IllegalStateException("Minecraft server is unavailable.");
        }
        AuctionHouse house = UltimateAuctionSystem.auctionHouse;
        if (house == null) {
            throw new IllegalStateException("Auction house is not initialized.");
        }
        AuctionDeliverySavedData deliveryData = safeDeliveryData(server);
        List<AuctionListingSummary> listings = house.buildAdminListingSummaries();
        AuctionAdminDashboardSnapshot admin = house.buildAdminDashboard(server, deliveryData);
        return new DashboardData(house, listings, admin);
    }

    private static AuctionAdminSavedData safeAdminData(MinecraftServer server) {
        try {
            return AuctionAdminSavedData.get(server);
        } catch (RuntimeException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS WebAdmin] Admin saved data unavailable: {}", exception.getMessage());
            return null;
        }
    }

    private static AuctionDeliverySavedData safeDeliveryData(MinecraftServer server) {
        try {
            return AuctionDeliverySavedData.get(server);
        } catch (RuntimeException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS WebAdmin] Delivery saved data unavailable: {}", exception.getMessage());
            return null;
        }
    }

    private static List<Map<String, Object>> listingRows(List<AuctionListingSummary> listings) {
        return (listings == null ? List.<AuctionListingSummary>of() : listings).stream()
                .sorted(Comparator.comparingInt(UasDashboardBootstrap::listingPriority).thenComparing(AuctionListingSummary::createdAt, Comparator.reverseOrder()))
                .limit(500)
                .map(UasDashboardBootstrap::listingRow)
                .toList();
    }

    private static int listingPriority(AuctionListingSummary listing) {
        if (listing.state() == AuctionState.FAILED_SETTLEMENT) {
            return 0;
        }
        if (listing.state() == AuctionState.ACTIVE) {
            return 1;
        }
        if (listing.state() == AuctionState.ENDED) {
            return 2;
        }
        return 3;
    }

    private static Map<String, Object> listingRow(AuctionListingSummary listing) {
        ItemStack stack = listing.item();
        return map(
                "auctionId", stringId(listing.auctionId()),
                "itemName", listing.itemName(),
                "itemId", itemId(stack),
                "modId", modId(stack),
                "description", limit(listing.description(), 180),
                "sellerName", listing.sellerName(),
                "sellerId", stringId(listing.sellerId()),
                "state", listing.state().name(),
                "format", label(listing.format().name()),
                "bundle", listing.bundle(),
                "stacks", listing.contents().size(),
                "items", listing.totalItemCount(),
                "startingBid", listing.startingBid(),
                "currentBid", listing.currentBid(),
                "buyout", positiveOrBlank(listing.buyoutPrice()),
                "reserve", positiveOrBlank(listing.reservePrice()),
                "bids", listing.bidCount(),
                "watchers", listing.notificationSubscriberCount(),
                "createdAt", text(listing.createdAt()),
                "endsAt", text(listing.endsAt()),
                "flags", listingFlags(listing)
        );
    }

    private static Map<String, Object> playerRow(AuctionAdminDashboardSnapshot.Player player) {
        return map(
                "playerId", stringId(player.playerId()),
                "playerName", player.playerName(),
                "activeListings", player.activeListings(),
                "maxActiveListings", player.maxActiveListings(),
                "bidCount", player.bidCount(),
                "soldCount", player.soldCount(),
                "boughtCount", player.boughtCount(),
                "cancelledCount", player.cancelledCount(),
                "bidVolume", player.bidVolume(),
                "soldValue", player.soldValue(),
                "deliveryCount", player.deliveryCount(),
                "deliveryPreview", player.deliveryPreview(),
                "banActive", player.banActive(),
                "blockedActions", blockedActions(player.blockCreate(), player.blockBid(), player.blockBuyout(), player.blockWatch()),
                "banReason", player.banReason(),
                "banExpiresAt", player.banExpiresAt()
        );
    }

    private static Map<String, Object> banRow(AuctionPlayerBan ban) {
        return map(
                "banId", stringId(ban.banId()),
                "playerId", stringId(ban.playerId()),
                "playerName", ban.playerName(),
                "active", ban.active(),
                "blockCreate", ban.blockCreate(),
                "blockBid", ban.blockBid(),
                "blockBuyout", ban.blockBuyout(),
                "blockWatch", ban.blockWatch(),
                "reason", ban.reason(),
                "createdBy", ban.createdByName(),
                "createdAt", text(ban.createdAt()),
                "expiresAt", ban.expiresAt().map(UasDashboardBootstrap::text).orElse("Never"),
                "revokedAt", ban.revokedAt().map(UasDashboardBootstrap::text).orElse("")
        );
    }

    private static Map<String, Object> bannedEntryRow(AuctionAdminDashboardSnapshot.BannedEntry entry) {
        return map(
                "entry", entry.entry(),
                "type", entry.type(),
                "label", entry.label(),
                "matchingActiveAuctions", entry.matchingActiveAuctions()
        );
    }

    private static Map<String, Object> suspicionRow(AuctionSuspicionSignal signal) {
        return map(
                "type", label(signal.type()),
                "auctionId", stringId(signal.auctionId()),
                "itemName", signal.itemName(),
                "primaryPlayerId", stringId(signal.primaryPlayerId()),
                "primaryPlayerName", signal.primaryPlayerName(),
                "secondaryPlayerId", stringId(signal.secondaryPlayerId()),
                "secondaryPlayerName", signal.secondaryPlayerName(),
                "evidenceCount", signal.evidenceCount(),
                "windowSeconds", signal.windowSeconds(),
                "startAmount", signal.startAmount(),
                "endAmount", signal.endAmount(),
                "observedAt", text(signal.observedAt())
        );
    }

    private static Map<String, Object> recoveryRow(AuctionRecoveryEntry entry) {
        return map(
                "recoveryId", stringId(entry.recoveryId()),
                "auctionId", stringId(entry.auctionId()),
                "sellerId", stringId(entry.sellerId()),
                "sellerName", entry.sellerName(),
                "itemName", entry.itemName(),
                "totalItemCount", entry.totalItemCount(),
                "active", entry.active(),
                "reason", entry.reason(),
                "adminName", entry.adminName(),
                "recoveredAt", text(entry.recoveredAt()),
                "releasedBy", entry.releasedByName(),
                "releaseReason", entry.releaseReason(),
                "releasedAt", entry.releasedAt().map(UasDashboardBootstrap::text).orElse("")
        );
    }

    private static Map<String, Object> auditRow(AuctionAdminAuditEntry entry) {
        return map(
                "auditId", stringId(entry.auditId()),
                "action", label(entry.action()),
                "adminId", stringId(entry.adminId()),
                "adminName", entry.adminName(),
                "target", entry.target(),
                "reason", entry.reason(),
                "success", entry.success(),
                "message", entry.message(),
                "createdAt", text(entry.createdAt())
        );
    }

    private static List<Map<String, Object>> economyRows(List<AuctionEconomyReport.Row> rows) {
        return (rows == null ? List.<AuctionEconomyReport.Row>of() : rows).stream()
                .map(row -> map("label", row.label(), "count", row.count(), "amount", row.amount()))
                .toList();
    }

    private static AuctionAdminDashboardSnapshot.Stats allStats(AuctionAdminDashboardSnapshot admin) {
        if (admin == null || admin.stats().isEmpty()) {
            return null;
        }
        return admin.stats().stream()
                .filter(stats -> "All".equalsIgnoreCase(stats.label()))
                .findFirst()
                .orElse(admin.stats().getLast());
    }

    private static AuctionEconomyReport allReport() {
        return new AuctionEconomyReport("", 0, 0, 0, "", "", "", List.of(), List.of(), List.of());
    }

    private static String blockedActions(boolean create, boolean bid, boolean buyout, boolean watch) {
        ArrayList<String> values = new ArrayList<>();
        if (create) {
            values.add("Create");
        }
        if (bid) {
            values.add("Bid");
        }
        if (buyout) {
            values.add("Buyout");
        }
        if (watch) {
            values.add("Watch");
        }
        return values.isEmpty() ? "None" : String.join(", ", values);
    }

    private static String listingFlags(AuctionListingSummary listing) {
        ArrayList<String> flags = new ArrayList<>();
        if (listing.bundle()) {
            flags.add("Bundle");
        }
        if (listing.format() != null && listing.format().name().equals("SEALED_BID")) {
            flags.add("Sealed");
        }
        if (listing.reserveActive()) {
            flags.add(listing.reserveMet() ? "Reserve met" : "Reserve unmet");
        }
        if (listing.notificationSubscriberCount() > 0) {
            flags.add(listing.notificationSubscriberCount() + " watcher(s)");
        }
        return flags.isEmpty() ? "None" : String.join(", ", flags);
    }

    private static Map<String, Object> actionPayload(AuctionActionResult result) {
        return map(
                "ok", result != null && result.success(),
                "message", result == null ? "Action failed." : result.message(),
                "code", result == null || result.code() == null ? "" : result.code().name(),
                "auctionId", result == null ? "" : stringId(result.auctionId()),
                "balanceAfter", result == null ? "" : result.balanceAfter(),
                "settlementReference", result == null ? "" : result.settlementReference()
        );
    }

    private static AuctionEconomyReport allReport(DashboardData data) {
        if (data.admin.economyReports().isEmpty()) {
            return allReport();
        }
        return data.admin.economyReports().stream()
                .filter(report -> "All".equalsIgnoreCase(report.label()))
                .findFirst()
                .orElse(data.admin.economyReports().getLast());
    }

    private static String playerName(MinecraftServer server, UUID playerId, String fallback) {
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        if (server != null && playerId != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                return player.getGameProfile().getName();
            }
        }
        return playerId == null ? "Unknown" : playerId.toString().substring(0, 8);
    }

    private static LocalDateTime dateTime(JsonObject body, String key) {
        String raw = string(body, key).trim();
        if (raw.isBlank() || "never".equalsIgnoreCase(raw)) {
            return null;
        }
        return LocalDateTime.parse(raw);
    }

    private static UUID uuid(JsonObject body, String key) {
        String raw = string(body, key).trim();
        if (raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw);
    }

    private static boolean bool(JsonObject body, String key, boolean fallback) {
        JsonElement element = element(body, key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        String raw = element.getAsString();
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private static String string(JsonObject body, String key) {
        JsonElement element = element(body, key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

    private static JsonElement element(JsonObject body, String key) {
        return body == null || key == null ? null : body.get(key);
    }

    private static String normalizeRoute(String routePath) {
        String path = routePath == null ? "" : routePath.trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        int slash = path.indexOf('/');
        return slash >= 0 ? path.substring(0, slash) : path;
    }

    private static String routeUrl(String panelId, String route) {
        return "/api/webadmin/addons/" + UltimateAuctionSystem.MODID + "/routes/" + panelId + "/" + DATA_WIDGET_ID + "/" + route;
    }

    private static DashboardComponentDefinition.Builder component(String id, String type) {
        return DashboardComponentDefinition.builder(id, type);
    }

    private static DashboardComponentDefinition table(String id, String title, String dataPath, List<?> columns) {
        return DashboardComponents.table(id, title, dataPath, columns).build();
    }

    private static DashboardComponentDefinition actionFormPanel(String id, String title, String endpoint, List<Map<String, Object>> sections) {
        return DashboardComponents.panel(id, title)
                .child(component(id + "-form", DashboardComponents.ACTION_FORM)
                        .option("endpoint", endpoint)
                        .option("sections", sections)
                        .build())
                .build();
    }

    private static List<Map<String, Object>> auctionColumns() {
        return List.of(
                col("Auction", "auctionId", "id"),
                col("Item", "itemName"),
                col("Seller", "sellerName"),
                col("State", "state"),
                col("Bid", "currentBid", "money"),
                col("Buyout", "buyout", "money"),
                col("Bids", "bids", "number"),
                col("Ends", "endsAt"),
                col("Flags", "flags")
        );
    }

    private static List<Map<String, Object>> auctionQueueColumns() {
        return List.of(
                col("Auction", "auctionId", "id"),
                col("Item", "itemName"),
                col("Seller", "sellerName"),
                col("State", "state"),
                col("Bid", "currentBid", "money"),
                col("Ends", "endsAt"),
                col("Flags", "flags")
        );
    }

    private static Map<String, Object> section(String title,
                                               String subtitle,
                                               List<Map<String, Object>> fields,
                                               List<Map<String, Object>> actions) {
        return map("title", title, "subtitle", subtitle, "fields", fields, "actions", actions);
    }

    private static Map<String, Object> field(String id, String label, String type, String placeholder) {
        return map("id", id, "label", label, "type", type, "placeholder", placeholder);
    }

    private static Map<String, Object> select(String id, String label, List<Map<String, Object>> options) {
        Object value = options == null || options.isEmpty() ? "" : options.getFirst().getOrDefault("value", "");
        return map("id", id, "label", label, "type", "select", "value", value, "options", options == null ? List.of() : options);
    }

    private static Map<String, Object> option(String value, String label) {
        return map("value", value, "label", label);
    }

    private static List<Map<String, Object>> yesNoOptions(boolean defaultYes) {
        List<Map<String, Object>> options = List.of(option("true", "Yes"), option("false", "No"));
        if (defaultYes) {
            return options;
        }
        return List.of(option("false", "No"), option("true", "Yes"));
    }

    private static Map<String, Object> action(String label,
                                              String action,
                                              String tone,
                                              List<String> required,
                                              Map<String, Object> payload,
                                              String confirm) {
        Map<String, Object> result = map("label", label, "action", action, "tone", tone, "required", required, "payload", payload);
        if (confirm != null && !confirm.isBlank()) {
            result.put("confirm", confirm);
        }
        return result;
    }

    private static Map<String, Object> card(String label, String path, String format) {
        return map("label", label, "path", path, "format", format);
    }

    private static Map<String, Object> col(String label, String path) {
        return map("label", label, "path", path);
    }

    private static Map<String, Object> col(String label, String path, String format) {
        return map("label", label, "path", path, "format", format);
    }

    private static String positiveOrBlank(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0 ? "" : value.toPlainString();
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static String modId(ItemStack stack) {
        String id = itemId(stack);
        int colon = id.indexOf(':');
        return colon <= 0 ? "" : id.substring(0, colon);
    }

    private static String label(String raw) {
        String source = raw == null ? "" : raw.trim().replace('-', '_');
        if (source.isBlank()) {
            return "";
        }
        return java.util.Arrays.stream(source.toLowerCase(Locale.ROOT).split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static String stringId(UUID id) {
        return id == null ? "" : id.toString();
    }

    private static String text(LocalDateTime time) {
        return time == null ? "" : time.toString();
    }

    private static String limit(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private record DashboardData(
            AuctionHouse house,
            List<AuctionListingSummary> listings,
            AuctionAdminDashboardSnapshot admin
    ) {
        private AuctionEconomyReport allReport() {
            return UasDashboardBootstrap.allReport(this);
        }
    }
}
