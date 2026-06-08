package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.AuctionActionResult;
import net.austizz.ultimate_auction_system.AuctionAdminSavedData;
import net.austizz.ultimate_auction_system.AuctionCategory;
import net.austizz.ultimate_auction_system.AuctionDeliverySavedData;
import net.austizz.ultimate_auction_system.AuctionHouse;
import net.austizz.ultimate_auction_system.AuctionHouseSnapshot;
import net.austizz.ultimate_auction_system.AuctionSavedSearchSavedData;
import net.austizz.ultimate_auction_system.AuctionSort;
import net.austizz.ultimate_auction_system.AuctionUiQuery;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimate_auction_system.api.UasPermissionAction;
import net.austizz.ultimate_auction_system.api.UasPermissions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = UltimateAuctionSystem.MODID)
public final class UasPayloads {
    private UasPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(AuctionActionPayload.TYPE, AuctionActionPayload.STREAM_CODEC, UasPayloads::handleAuctionAction);
        registrar.playToServer(AuctionAdminActionPayload.TYPE, AuctionAdminActionPayload.STREAM_CODEC, UasPayloads::handleAuctionAdminAction);
        registrar.playToClient(AuctionSnapshotPayload.TYPE, AuctionSnapshotPayload.STREAM_CODEC, UasPayloads::handleAuctionSnapshot);
    }

    public static void openAuctionHouse(ServerPlayer player) {
        sendSnapshot(player, AuctionUiQuery.defaults(), "", true, false);
    }

    public static void openAdminAuctionHouse(ServerPlayer player) {
        sendSnapshot(player, AuctionUiQuery.defaults(), "", true, true);
    }

    private static void handleAuctionSnapshot(AuctionSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleAuctionSnapshot", payload));
    }

    private static void handleAuctionAction(AuctionActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            AuctionUiQuery query = queryFrom(payload);
            AuctionHouse house = UltimateAuctionSystem.auctionHouse;
            if (house == null) {
                sendSnapshot(player, query, "Auction house is not initialized.", false, false);
                return;
            }
            AuctionDeliverySavedData deliveryData = AuctionDeliverySavedData.get(player.getServer());
            String action = safe(payload.action());
            boolean adminMode = payload.adminMode() && UasPermissions.has(player, UasPermissionAction.ADMIN);
            AuctionActionResult permission = permissionForAction(player, action);
            if (!permission.success()) {
                house.sendActionAlert(player, permission);
                sendSnapshot(player, query, permission.message(), false, adminMode);
                return;
            }
            AuctionActionResult rateLimit = rateLimit(player, action, adminMode);
            if (!rateLimit.success()) {
                house.sendActionAlert(player, rateLimit);
                sendSnapshot(player, query, rateLimit.message(), false, adminMode);
                return;
            }
            AuctionActionResult result = switch (action) {
                case "PREPARE_CREATE" -> house.prepareAuctionFromInventorySlots(
                        player,
                        payload.selectedSlots(),
                        payload.title(),
                        money(payload.startingBid()),
                        money(payload.buyoutPrice()),
                        money(payload.reservePrice()),
                        endDateTime(payload),
                        payload.description(),
                        payload.accountId()
                );
                case "CONFIRM_CREATE" -> house.confirmPendingAuction(player, payload.accountId());
                case "DISCARD_CREATE" -> house.discardPendingAuction(player);
                case "BID" -> house.placeBidWithEscrow(player, payload.auctionId(), money(payload.amount()), payload.accountId());
                case "BUYOUT" -> house.buyout(player, payload.auctionId(), payload.accountId());
                case "CANCEL" -> house.cancelOwnAuction(player, payload.auctionId(), deliveryData);
                case "ADMIN_FORCE_CANCEL" -> adminMode
                        ? AuctionActionResult.fail("Use the admin dashboard force-cancel action with a reason.")
                        : AuctionActionResult.fail("You do not have permission to force-cancel auctions.");
                case "CLAIM" -> house.claimAuction(player, payload.auctionId(), deliveryData);
                case "RELIST" -> house.relistAuction(
                        player,
                        payload.auctionId(),
                        payload.title(),
                        money(payload.startingBid()),
                        money(payload.buyoutPrice()),
                        money(payload.reservePrice()),
                        endDateTime(payload),
                        payload.description(),
                        payload.accountId()
                );
                case "TOGGLE_NOTIFICATIONS" -> house.toggleNotifications(player, payload.auctionId());
                case "WITHDRAW_DELIVERY" -> house.withdrawDelivery(player, payload.deliveryId(), deliveryData);
                case "SAVE_SEARCH" -> AuctionSavedSearchSavedData.get(player.getServer()).saveSearch(player.getUUID(), payload.title(), query);
                case "RENAME_SEARCH" -> AuctionSavedSearchSavedData.get(player.getServer()).renameSearch(player.getUUID(), payload.auctionId(), payload.title());
                case "DELETE_SEARCH" -> AuctionSavedSearchSavedData.get(player.getServer()).deleteSearch(player.getUUID(), payload.auctionId());
                default -> AuctionActionResult.ok("");
            };
            if (adminMode && "ADMIN_FORCE_CANCEL".equals(action)) {
                AuctionAdminSavedData.get(player.getServer()).addAudit(
                        "ADMIN_FORCE_CANCEL",
                        player.getUUID(),
                        player.getGameProfile().getName(),
                        String.valueOf(payload.auctionId()),
                        "Missing required admin reason",
                        result.success(),
                        result.message()
                );
            }
            house.sendActionAlert(player, result);
            sendSnapshot(player, query, result.message(), result.success(), adminMode);
        });
    }

    private static void handleAuctionAdminAction(AuctionAdminActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            AuctionHouse house = UltimateAuctionSystem.auctionHouse;
            if (house == null) {
                sendSnapshot(player, AuctionUiQuery.defaults(), "Auction house is not initialized.", false, false);
                return;
            }
            boolean adminMode = UasPermissions.has(player, UasPermissionAction.ADMIN);
            if (!adminMode) {
                sendSnapshot(player, AuctionUiQuery.defaults(), "You do not have permission to use auction admin tools.", false, false);
                return;
            }

            AuctionAdminSavedData adminData = AuctionAdminSavedData.get(player.getServer());
            AuctionDeliverySavedData deliveryData = AuctionDeliverySavedData.get(player.getServer());
            AuctionActionResult result = handleAdminAction(payload, player, house, adminData, deliveryData);
            house.sendActionAlert(player, result);
            sendSnapshot(player, AuctionUiQuery.defaults(), result.message(), result.success(), true);
        });
    }

    private static AuctionActionResult handleAdminAction(AuctionAdminActionPayload payload,
                                                        ServerPlayer admin,
                                                        AuctionHouse house,
                                                        AuctionAdminSavedData adminData,
                                                        AuctionDeliverySavedData deliveryData) {
        String action = safe(payload.action());
        return switch (action) {
            case "ADMIN_FORCE_CANCEL" -> auditedAdminAuctionAction(
                    adminData,
                    admin,
                    "ADMIN_FORCE_CANCEL",
                    String.valueOf(payload.auctionId()),
                    payload.reason(),
                    house.adminForceCancel(
                            admin.getUUID(),
                            admin.getGameProfile().getName(),
                            true,
                            payload.auctionId(),
                            deliveryData,
                            adminData,
                            "recover".equalsIgnoreCase(safe(payload.bannedEntry())),
                            payload.reason()
                    )
            );
            case "ADMIN_RELEASE_RECOVERY" -> auditedAdminAuctionAction(
                    adminData,
                    admin,
                    "ADMIN_RELEASE_RECOVERY",
                    String.valueOf(payload.auctionId()),
                    payload.reason(),
                    house.adminReleaseRecovery(admin, payload.auctionId(), deliveryData, adminData, payload.reason())
            );
            case "ADMIN_RETRY_SETTLEMENT" -> auditedAdminAuctionAction(
                    adminData,
                    admin,
                    "RETRY_SETTLEMENT",
                    String.valueOf(payload.auctionId()),
                    "Admin dashboard settlement retry",
                    house.adminRetrySettlement(admin, payload.auctionId(), deliveryData)
            );
            case "APPLY_BAN" -> applyPlayerBan(payload, admin, adminData);
            case "REVOKE_BAN" -> revokePlayerBan(payload, admin, adminData);
            case "ADD_BANNED_ENTRY" -> addBannedEntry(payload, admin, adminData);
            case "REMOVE_BANNED_ENTRY" -> removeBannedEntry(payload, admin, adminData);
            default -> AuctionActionResult.fail("Unknown admin action.");
        };
    }

    private static AuctionActionResult auditedAdminAuctionAction(AuctionAdminSavedData adminData,
                                                                ServerPlayer admin,
                                                                String action,
                                                                String target,
                                                                String reason,
                                                                AuctionActionResult result) {
        adminData.addAudit(action, admin.getUUID(), admin.getGameProfile().getName(), target, reason, result.success(), result.message());
        return result;
    }

    private static AuctionActionResult applyPlayerBan(AuctionAdminActionPayload payload,
                                                      ServerPlayer admin,
                                                      AuctionAdminSavedData adminData) {
        if (payload.playerId() == null) {
            return AuctionActionResult.fail("Select a player before applying a ban.");
        }
        if (!payload.blockCreate() && !payload.blockBid() && !payload.blockBuyout() && !payload.blockWatch()) {
            return AuctionActionResult.fail("Select at least one auction-house action to block.");
        }
        LocalDateTime expiresAt;
        try {
            expiresAt = parseAdminExpiry(payload.expiresAt());
        } catch (DateTimeParseException exception) {
            return AuctionActionResult.fail("Ban expiry must be blank or ISO date-time, for example 2026-06-06T18:30.");
        }
        String playerName = playerName(admin, payload.playerId(), payload.playerName());
        adminData.applyBan(
                payload.playerId(),
                playerName,
                payload.blockCreate(),
                payload.blockBid(),
                payload.blockBuyout(),
                payload.blockWatch(),
                payload.reason(),
                expiresAt,
                admin.getUUID(),
                admin.getGameProfile().getName()
        );
        return AuctionActionResult.ok("Auction-house ban updated for " + playerName + ".");
    }

    private static AuctionActionResult revokePlayerBan(AuctionAdminActionPayload payload,
                                                       ServerPlayer admin,
                                                       AuctionAdminSavedData adminData) {
        if (payload.playerId() == null) {
            return AuctionActionResult.fail("Select a player before revoking a ban.");
        }
        boolean revoked = adminData.revokeBan(payload.playerId(), admin.getUUID(), admin.getGameProfile().getName(), payload.reason());
        return revoked ? AuctionActionResult.ok("Auction-house ban revoked.") : AuctionActionResult.fail("No auction-house ban exists for that player.");
    }

    private static AuctionActionResult addBannedEntry(AuctionAdminActionPayload payload,
                                                     ServerPlayer admin,
                                                     AuctionAdminSavedData adminData) {
        String entry = net.austizz.ultimate_auction_system.Config.normalizeAuctionRestriction(payload.bannedEntry());
        if (!net.austizz.ultimate_auction_system.Config.isValidAuctionRestriction(entry)) {
            adminData.addAudit("BANNED_ENTRY_ADD", admin.getUUID(), admin.getGameProfile().getName(), entry, "Admin dashboard banned-entry add", false, "Invalid banned auction entry.");
            return AuctionActionResult.fail("Invalid banned auction entry.");
        }
        List<String> entries = new ArrayList<>(net.austizz.ultimate_auction_system.Config.bannedAuctionEntries);
        if (!entries.contains(entry)) {
            entries.add(entry);
        }
        AuctionActionResult result = net.austizz.ultimate_auction_system.Config.replaceBannedAuctionEntries(entries);
        adminData.addAudit("BANNED_ENTRY_ADD", admin.getUUID(), admin.getGameProfile().getName(), entry, "Admin dashboard banned-entry add", result.success(), result.message());
        return result;
    }

    private static AuctionActionResult removeBannedEntry(AuctionAdminActionPayload payload,
                                                        ServerPlayer admin,
                                                        AuctionAdminSavedData adminData) {
        String entry = net.austizz.ultimate_auction_system.Config.normalizeAuctionRestriction(payload.bannedEntry());
        List<String> entries = new ArrayList<>(net.austizz.ultimate_auction_system.Config.bannedAuctionEntries);
        boolean removed = entries.removeIf(current -> entry.equals(net.austizz.ultimate_auction_system.Config.normalizeAuctionRestriction(current)));
        AuctionActionResult result = removed
                ? net.austizz.ultimate_auction_system.Config.replaceBannedAuctionEntries(entries)
                : AuctionActionResult.fail("Banned auction entry was not found.");
        adminData.addAudit("BANNED_ENTRY_REMOVE", admin.getUUID(), admin.getGameProfile().getName(), entry, "Admin dashboard banned-entry remove", result.success(), result.message());
        return result;
    }

    private static void sendSnapshot(ServerPlayer player, AuctionUiQuery query, String message, boolean success) {
        sendSnapshot(player, query, message, success, false);
    }

    private static void sendSnapshot(ServerPlayer player, AuctionUiQuery query, String message, boolean success, boolean adminMode) {
        AuctionHouse house = UltimateAuctionSystem.auctionHouse;
        if (player == null || house == null || player.getServer() == null) {
            return;
        }
        AuctionDeliverySavedData deliveryData = AuctionDeliverySavedData.get(player.getServer());
        AuctionHouseSnapshot snapshot = house.buildSnapshot(player, deliveryData, query, message, success, adminMode);
        PacketDistributor.sendToPlayer(player, AuctionSnapshotPayload.fromSnapshot(snapshot));
    }

    private static LocalDateTime parseAdminExpiry(String raw) {
        if (raw == null || raw.isBlank() || "never".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        return LocalDateTime.parse(raw.trim());
    }

    private static String playerName(ServerPlayer admin, UUID playerId, String fallback) {
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        if (admin != null && admin.getServer() != null && playerId != null) {
            ServerPlayer online = admin.getServer().getPlayerList().getPlayer(playerId);
            if (online != null) {
                return online.getGameProfile().getName();
            }
        }
        return playerId == null ? "Unknown" : playerId.toString().substring(0, 8);
    }

    private static AuctionUiQuery queryFrom(AuctionActionPayload payload) {
        return new AuctionUiQuery(
                payload.search(),
                AuctionCategory.fromToken(payload.category()),
                money(payload.minimumPrice()),
                money(payload.maximumPrice()),
                Math.max(0L, payload.maximumHoursLeft()),
                AuctionSort.fromToken(payload.sort()),
                payload.modId()
        );
    }

    private static AuctionActionResult rateLimit(ServerPlayer player, String action, boolean adminMode) {
        if (adminMode) {
            return AuctionActionResult.ok("");
        }
        net.austizz.ultimate_auction_system.AuctionRateLimiter.Action limitAction = switch (action) {
            case "PREPARE_CREATE" -> net.austizz.ultimate_auction_system.AuctionRateLimiter.Action.CREATE;
            case "RELIST" -> net.austizz.ultimate_auction_system.AuctionRateLimiter.Action.CREATE;
            case "BID" -> net.austizz.ultimate_auction_system.AuctionRateLimiter.Action.BID;
            case "BUYOUT" -> net.austizz.ultimate_auction_system.AuctionRateLimiter.Action.BUYOUT;
            case "CANCEL" -> net.austizz.ultimate_auction_system.AuctionRateLimiter.Action.CANCEL;
            case "REFRESH" -> net.austizz.ultimate_auction_system.AuctionRateLimiter.Action.SEARCH;
            default -> null;
        };
        return limitAction == null
                ? AuctionActionResult.ok("")
                : net.austizz.ultimate_auction_system.AuctionRateLimiter.checkAndMark(player, limitAction);
    }

    private static AuctionActionResult permissionForAction(ServerPlayer player, String action) {
        UasPermissionAction permissionAction = switch (action) {
            case "PREPARE_CREATE", "CONFIRM_CREATE", "RELIST" -> UasPermissionAction.LIST;
            case "BID" -> UasPermissionAction.BID;
            case "BUYOUT" -> UasPermissionAction.BUYOUT;
            case "CANCEL" -> UasPermissionAction.CANCEL_OWN;
            case "CLAIM" -> UasPermissionAction.CLAIM;
            case "ADMIN_FORCE_CANCEL" -> UasPermissionAction.ADMIN;
            default -> null;
        };
        return permissionAction == null ? AuctionActionResult.ok("") : UasPermissions.check(player, permissionAction);
    }

    private static BigDecimal money(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim().replace("$", "").replace(",", ""));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String safe(String action) {
        return action == null ? "" : action.trim().toUpperCase();
    }

    private static LocalDateTime endDateTime(AuctionActionPayload payload) {
        String raw = payload.endDateTime();
        if (raw != null && !raw.isBlank()) {
            try {
                return LocalDateTime.parse(raw.trim());
            } catch (DateTimeParseException ignored) {
            }
        }
        return LocalDateTime.now().plusHours(Math.max(1, payload.durationHours()));
    }
}
