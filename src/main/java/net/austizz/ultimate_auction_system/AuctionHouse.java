package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.api.UasAuctionSnapshot;
import net.austizz.ultimate_auction_system.api.event.UasAuctionEvents;
import net.austizz.ultimate_auction_system.api.UasPermissionAction;
import net.austizz.ultimate_auction_system.api.UasPermissions;
import net.austizz.ultimate_auction_system.banking.UasAlertResult;
import net.austizz.ultimate_auction_system.banking.UasBankingResult;
import net.austizz.ultimate_auction_system.banking.UasBankingService;
import net.austizz.ultimate_auction_system.banking.UasItemResult;
import net.austizz.ultimate_auction_system.banking.UbsBankingService;
import net.austizz.ultimate_auction_system.banking.UasAccountSnapshot;
import net.austizz.ultimate_auction_system.banking.UasMoneyFormatter;
import net.austizz.ultimate_auction_system.i18n.UasTranslations;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

public class AuctionHouse {
    private static final String ALERT_TITLE = "Auction House";
    private static final int ALERT_DURATION_MS = 5000;
    private static final int AUTO_SETTLEMENT_LIMIT_PER_SCAN = 8;
    static final String EVENT_LISTING_FEE = "LISTING_FEE";
    static final String EVENT_LISTING_FEE_REFUND = "LISTING_FEE_REFUND";
    static final String EVENT_BID_ESCROW = "BID_ESCROW";
    static final String EVENT_BID_ESCROW_REFUND = "BID_ESCROW_REFUND";
    static final String EVENT_BUYOUT_ESCROW = "BUYOUT_ESCROW";
    static final String EVENT_BUYOUT_ESCROW_REFUND = "BUYOUT_ESCROW_REFUND";
    static final String EVENT_OUTBID_REFUND = "OUTBID_REFUND";
    static final String EVENT_RESERVE_REFUND = "RESERVE_REFUND";
    static final String EVENT_SEALED_BID_REFUND = "SEALED_BID_REFUND";
    static final String EVENT_SEALED_BID_REPLACE_REFUND = "SEALED_BID_REPLACE_REFUND";
    static final String EVENT_AUCTION_PAYOUT = "AUCTION_PAYOUT";
    static final String EVENT_SALES_TAX = "SALES_TAX";
    static final String EVENT_ADMIN_FORCE_CANCEL_REFUND = "ADMIN_FORCE_CANCEL_REFUND";
    static final String EVENT_CANCELLATION_FEE = "CANCELLATION_FEE";

    private record AuctionChatAction(String label,
                                     String command,
                                     String hover,
                                     ChatFormatting color,
                                     ClickEvent.Action clickAction) {
    }

    private final ConcurrentHashMap<UUID, AuctionItem> AuctionItems;
    private final ConcurrentHashMap<UUID, PendingAuctionListing> pendingListings = new ConcurrentHashMap<>();
    private final UasBankingService bankingService;
    private final AuctionSuspicionAnalyzer suspicionAnalyzer = new AuctionSuspicionAnalyzer();
    private final AuctionEconomyReporter economyReporter = new AuctionEconomyReporter();
    private final AuctionSavedData savedData;
    private final boolean mutationsBlocked;
    private volatile AuctionStorageHealth storageHealth = AuctionStorageHealth.inMemoryOnly();
    private final PriorityBlockingQueue<ExpiryEntry> auctionQueue = new PriorityBlockingQueue<>(
            11,
            Comparator.comparingLong(ExpiryEntry::expiresAt)
    );


    private record ExpiryEntry(UUID listingId, long expiresAt) {}

    private record SettlementResult(boolean success,
                                    String message,
                                    BigDecimal gross,
                                    BigDecimal salesTax,
                                    BigDecimal net) {
        static SettlementResult ok(String message, BigDecimal gross, BigDecimal salesTax, BigDecimal net) {
            return new SettlementResult(true, message == null ? "" : message, gross, salesTax, net);
        }

        static SettlementResult fail(String message, BigDecimal gross, BigDecimal salesTax, BigDecimal net) {
            return new SettlementResult(false, message == null ? "Auction settlement failed." : message, gross, salesTax, net);
        }
    }

    private record AccountSelection(boolean success,
                                    String message,
                                    UUID accountId,
                                    UasAccountSnapshot snapshot) {
        static AccountSelection ok(UasAccountSnapshot snapshot) {
            return new AccountSelection(true, "", snapshot.accountId(), snapshot);
        }

        static AccountSelection fail(String message) {
            return new AccountSelection(false, message == null ? "Selected UBS account is unavailable." : message, null, null);
        }
    }

    private static final class AdminPlayerAccumulator {
        private final UUID playerId;
        private String name;
        private int activeListings;
        private int bidCount;
        private int soldCount;
        private int boughtCount;
        private int cancelledCount;
        private BigDecimal bidVolume = BigDecimal.ZERO;
        private BigDecimal soldValue = BigDecimal.ZERO;

        private AdminPlayerAccumulator(UUID playerId, String name) {
            this.playerId = playerId;
            this.name = name == null || name.isBlank() ? playerId.toString().substring(0, 8) : name;
        }

        private int score() {
            return activeListings * 4 + soldCount * 3 + boughtCount * 3 + bidCount + cancelledCount;
        }
    }

    public AuctionHouse() {
        this(null, new UbsBankingService());
    }

    public AuctionHouse(UasBankingService bankingService) {
        this(null, bankingService);
    }

    private AuctionHouse(AuctionSavedData savedData, UasBankingService bankingService) {
        this.savedData = savedData;
        this.AuctionItems = savedData == null ? new ConcurrentHashMap<>() : savedData.getAuctions();
        this.bankingService = bankingService;
        this.mutationsBlocked = savedData != null && savedData.isMigrationFailed();
        this.AuctionItems.values().forEach(this::attachMutationTracking);
    }



    public static AuctionHouse load(MinecraftServer server) {
        AuctionSavedData data = AuctionSavedData.get(server);
        AuctionHouse house = new AuctionHouse(data, new UbsBankingService());
        if (data.isMigrationFailed()) {
            house.markStorageFailed("Auction storage migration failed: " + data.getMigrationMessage());
            return house;
        }
        String message = data.getSkippedRecords() == 0 && data.getRepairedRecords() == 0
                ? "Persistent auction storage schema " + data.getSchemaVersion()
                + " loaded with " + data.getAuctions().size() + " auction(s)."
                : "Persistent auction storage loaded with " + data.getAuctions().size()
                + " auction(s); skipped " + data.getSkippedRecords()
                + " invalid record(s), repaired " + data.getRepairedRecords() + " record(s).";
        house.markStorageLoaded(message);
        return house;
    }

    public void addAuctionItem(AuctionItem item) {
        if (item == null) {
            return;
        }
        if (item.isBundle()) {
            ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(item.getPlayerId());
            sendListingError(player, listingError("Bundled auctions must be created from the auction house inventory picker."));
            return;
        }
        if (mutationsBlocked) {
            ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(item.getPlayerId());
            sendListingError(player, listingError("Auction storage has a migration problem. New listings are blocked until an admin fixes the saved data."));
            return;
        }

        ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(item.getPlayerId());
        Optional<UUID> sellerAccountId = bankingService.getPrimaryAccountId(item.getPlayerId());
        if (sellerAccountId.isEmpty()) {
            sendListingError(player, listingError("UAS could not find your UBS primary account."));
            return;
        }
        UasBankingResult canReceive = bankingService.validateCanReceive(sellerAccountId.get());
        if (!canReceive.success()) {
            sendListingError(player, listingError("Your UBS primary account cannot receive auction payouts right now: ")
                    .append(Component.literal(canReceive.reason()).withStyle(ChatFormatting.RED)));
            return;
        }

        item.setSellerAccountId(sellerAccountId.get());
        Optional<String> validationError = item.validateForListingRequest();
        if (validationError.isPresent()) {
            sendListingError(player, listingError("Auction listing is incomplete: ")
                    .append(Component.literal(validationError.get()).withStyle(ChatFormatting.RED))
                    .append(Component.literal(".").withStyle(ChatFormatting.RED)));
            return;
        }
        if (player == null) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Listing {} rejected because seller {} is not online for escrow transfer.", item.getAuctionId(), item.getPlayerId());
            return;
        }

        ItemStack escrowStack = item.getItem();
        if (!takeEscrowFromSeller(player, escrowStack)) {
            sendListingError(player, listingError("The item in your main hand no longer matches this listing."));
            return;
        }

        try {
            item.markEscrowed("SELLER_MAIN_HAND");
            Optional<String> escrowValidationError = item.validateForActivation();
            if (escrowValidationError.isPresent()) {
                restoreEscrowToSeller(player, escrowStack);
                sendListingError(player, listingError("Auction escrow failed validation: ")
                        .append(Component.literal(escrowValidationError.get()).withStyle(ChatFormatting.RED))
                        .append(Component.literal(".").withStyle(ChatFormatting.RED)));
                return;
            }

            attachMutationTracking(item);
            this.AuctionItems.put(item.getAuctionId(), item);
            markChanged("Auction storage marked dirty after listing creation.");
            Component message = Component.empty()
                    .append(Component.literal("⚖ ").withStyle(ChatFormatting.GOLD))
                    .append(UasTranslations.literal("Auction: ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(UasTranslations.literal("Successfully listed ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(item.getItem().getCount() + "x " + item.getItem().getHoverName().getString()).withStyle(ChatFormatting.AQUA))
                    .append(UasTranslations.literal(" for ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(moneyLabel(item.getStartingBidPrice())).withStyle(ChatFormatting.GREEN))
                    .append(UasTranslations.literal(". Use /ah to view your listing!").withStyle(ChatFormatting.DARK_GRAY));
            player.sendSystemMessage(message, false);
        } catch (RuntimeException exception) {
            this.AuctionItems.remove(item.getAuctionId());
            restoreEscrowToSeller(player, escrowStack);
            markStorageFailed("Auction listing failed after escrow transfer: " + exception.getMessage());
            UltimateAuctionSystem.LOGGER.error("[UAS] Auction listing failed after escrow transfer; escrow was returned.", exception);
        }
    }

    public AuctionActionResult createAuctionFromMainHand(ServerPlayer player,
                                                         BigDecimal startingBidPrice,
                                                         BigDecimal buyoutPrice,
                                                         long durationHours,
                                                         String description) {
        return prepareAuctionFromMainHand(player, startingBidPrice, buyoutPrice, durationHours, description);
    }

    public AuctionActionResult prepareAuctionFromMainHand(ServerPlayer player,
                                                          BigDecimal startingBidPrice,
                                                          BigDecimal buyoutPrice,
                                                          long durationHours,
                                                          String description) {
        if (player == null) {
            return AuctionActionResult.fail("Only players can create auctions.");
        }
        AuctionActionResult permission = UasPermissions.check(player, UasPermissionAction.LIST);
        if (!permission.success()) {
            return permission;
        }
        ItemStack itemInHand = player.getMainHandItem();
        if (itemInHand.isEmpty()) {
            return AuctionActionResult.fail("Hold the item you want to auction in your main hand.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = now.plusHours(Math.max(1L, durationHours));
        AuctionActionResult validation = validateListingRequest(player, itemInHand, startingBidPrice, buyoutPrice, end);
        if (!validation.success()) {
            return validation;
        }

        PendingAuctionListing pending = new PendingAuctionListing(
                player.getUUID(),
                PendingAuctionListing.MAIN_HAND_SLOT,
                itemInHand,
                startingBidPrice,
                buyoutPrice,
                BigDecimal.ZERO,
                AuctionFormat.NORMAL,
                end,
                description,
                now,
                now.plusSeconds(Config.pendingListingConfirmationSeconds),
                "Main Hand"
        );
        pendingListings.put(player.getUUID(), pending);
        return AuctionActionResult.ok(pendingPreviewMessage(pending));
    }

    public AuctionActionResult createAuctionFromInventorySlot(ServerPlayer player,
                                                              int slot,
                                                              BigDecimal startingBidPrice,
                                                              BigDecimal buyoutPrice,
                                                              long durationHours,
                                                              String description) {
        return prepareAuctionFromInventorySlot(player, slot, startingBidPrice, buyoutPrice, durationHours, description);
    }

    public AuctionActionResult prepareAuctionFromInventorySlot(ServerPlayer player,
                                                               int slot,
                                                               BigDecimal startingBidPrice,
                                                               BigDecimal buyoutPrice,
                                                               long durationHours,
                                                               String description) {
        LocalDateTime now = LocalDateTime.now();
        return prepareAuctionFromInventorySlot(player, slot, startingBidPrice, buyoutPrice, now.plusHours(Math.max(1L, durationHours)), description);
    }

    public AuctionActionResult createAuctionFromInventorySlot(ServerPlayer player,
                                                              int slot,
                                                              BigDecimal startingBidPrice,
                                                              BigDecimal buyoutPrice,
                                                              LocalDateTime end,
                                                              String description) {
        return prepareAuctionFromInventorySlot(player, slot, startingBidPrice, buyoutPrice, end, description);
    }

    public AuctionActionResult prepareAuctionFromInventorySlot(ServerPlayer player,
                                                               int slot,
                                                               BigDecimal startingBidPrice,
                                                               BigDecimal buyoutPrice,
                                                               LocalDateTime end,
                                                               String description) {
        return prepareAuctionFromInventorySlots(player, List.of(slot), "", startingBidPrice, buyoutPrice, end, description);
    }

    public AuctionActionResult prepareAuctionFromInventorySlots(ServerPlayer player,
                                                                List<Integer> slots,
                                                                String title,
                                                                BigDecimal startingBidPrice,
                                                                BigDecimal buyoutPrice,
                                                                LocalDateTime end,
                                                                String description) {
        return prepareAuctionFromInventorySlots(player, slots, title, startingBidPrice, buyoutPrice, BigDecimal.ZERO, end, description, null);
    }

    public AuctionActionResult prepareAuctionFromInventorySlots(ServerPlayer player,
                                                                List<Integer> slots,
                                                                String title,
                                                                BigDecimal startingBidPrice,
                                                                BigDecimal buyoutPrice,
                                                                BigDecimal reservePrice,
                                                                LocalDateTime end,
                                                                String description,
                                                                UUID sellerAccountId) {
        return prepareAuctionFromInventorySlotsInternal(player, slots, title, startingBidPrice, buyoutPrice, reservePrice, AuctionFormat.NORMAL, end, description, sellerAccountId);
    }

    public AuctionActionResult prepareAuctionFromInventorySlots(ServerPlayer player,
                                                                List<Integer> slots,
                                                                String title,
                                                                BigDecimal startingBidPrice,
                                                                BigDecimal buyoutPrice,
                                                                BigDecimal reservePrice,
                                                                AuctionFormat format,
                                                                LocalDateTime end,
                                                                String description,
                                                                UUID sellerAccountId) {
        return prepareAuctionFromInventorySlotsInternal(player, slots, title, startingBidPrice, buyoutPrice, reservePrice, format, end, description, sellerAccountId);
    }

    public AuctionActionResult prepareAuctionFromInventorySlots(ServerPlayer player,
                                                                List<Integer> slots,
                                                                String title,
                                                                BigDecimal startingBidPrice,
                                                                BigDecimal buyoutPrice,
                                                                LocalDateTime end,
                                                                String description,
                                                                UUID sellerAccountId) {
        return prepareAuctionFromInventorySlotsInternal(player, slots, title, startingBidPrice, buyoutPrice, BigDecimal.ZERO, AuctionFormat.NORMAL, end, description, sellerAccountId);
    }

    private AuctionActionResult prepareAuctionFromInventorySlotsInternal(ServerPlayer player,
                                                                         List<Integer> slots,
                                                                         String title,
                                                                         BigDecimal startingBidPrice,
                                                                         BigDecimal buyoutPrice,
                                                                         BigDecimal reservePrice,
                                                                         AuctionFormat format,
                                                                         LocalDateTime end,
                                                                         String description,
                                                                         UUID sellerAccountId) {
        if (player == null) {
            return AuctionActionResult.fail("Only players can create auctions.");
        }
        AuctionActionResult permission = UasPermissions.check(player, UasPermissionAction.LIST);
        if (!permission.success()) {
            return permission;
        }
        List<Integer> safeSlots = sanitizeSelectedSlots(slots);
        if (safeSlots.isEmpty()) {
            return AuctionActionResult.fail("Select at least one inventory item.");
        }
        if (safeSlots.size() > AuctionItem.MAX_BUNDLE_CONTENTS) {
            return AuctionActionResult.fail("A bundled auction can include up to " + AuctionItem.MAX_BUNDLE_CONTENTS + " item stacks.");
        }

        ArrayList<ItemStack> snapshots = new ArrayList<>();
        for (int selectedSlot : safeSlots) {
            if (selectedSlot < 0 || selectedSlot >= player.getInventory().getContainerSize()) {
                return AuctionActionResult.fail("Select a valid inventory slot.");
            }
            ItemStack stack = player.getInventory().getItem(selectedSlot);
            if (stack.isEmpty()) {
                return AuctionActionResult.fail("One selected inventory slot is empty.");
            }
            snapshots.add(stack.copy());
        }

        LocalDateTime now = LocalDateTime.now();
        AuctionFormat safeFormat = format == null ? AuctionFormat.NORMAL : format;
        AuctionActionResult validation = validateListingRequest(player, snapshots, startingBidPrice, buyoutPrice, reservePrice, safeFormat, end, sellerAccountId);
        if (!validation.success()) {
            return validation;
        }

        PendingAuctionListing pending = new PendingAuctionListing(
                player.getUUID(),
                safeSlots,
                snapshots,
                snapshots.size() > 1 ? title : "",
                startingBidPrice,
                buyoutPrice,
                reservePrice,
                safeFormat,
                end,
                description,
                now,
                now.plusSeconds(Config.pendingListingConfirmationSeconds),
                snapshots.size() > 1 ? "Bundle (" + snapshots.size() + " items)" : "Inventory Slot " + (safeSlots.getFirst() + 1),
                sellerAccountId
        );
        pendingListings.put(player.getUUID(), pending);
        return AuctionActionResult.ok(pendingPreviewMessage(pending));
    }

    public AuctionActionResult confirmPendingAuction(ServerPlayer player) {
        return confirmPendingAuction(player, null);
    }

    public AuctionActionResult confirmPendingAuction(ServerPlayer player, UUID sellerAccountId) {
        if (player == null) {
            return AuctionActionResult.fail("Only players can create auctions.");
        }
        AuctionActionResult permission = UasPermissions.check(player, UasPermissionAction.LIST);
        if (!permission.success()) {
            return permission;
        }
        PendingAuctionListing pending = pendingListings.get(player.getUUID());
        if (pending == null) {
            return AuctionActionResult.fail("You do not have a pending auction listing to confirm.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (pending.isExpired(now)) {
            pendingListings.remove(player.getUUID());
            return AuctionActionResult.fail("Your pending auction listing expired. Start the listing again.");
        }
        if (!pending.stillMatches(player)) {
            pendingListings.remove(player.getUUID());
            return AuctionActionResult.fail("The item no longer matches the pending auction listing. Start the listing again.");
        }

        UUID accountId = sellerAccountId == null ? pending.sellerAccountId() : sellerAccountId;
        AuctionActionResult validation = validateListingRequest(player, pending.itemSnapshots(), pending.startingBid(), pending.buyoutPrice(), pending.reservePrice(), pending.format(), pending.endsAt(), accountId);
        if (!validation.success()) {
            return validation;
        }

        AccountSelection sellerAccount = validateSellerAccount(player.getUUID(), accountId, pending.startingBid());
        if (!sellerAccount.success()) {
            return AuctionActionResult.fail(sellerAccount.message());
        }

        UUID auctionId = UUID.randomUUID();
        String listingFeeReference = auctionReference(EVENT_LISTING_FEE, auctionId);
        UasBankingResult feeResult = chargeListingFee(sellerAccount.accountId(), pending.startingBid(), listingFeeReference);
        if (!feeResult.success()) {
            return AuctionActionResult.fail(feeFailureMessage("listing fee", Config.calculateListingFee(pending.startingBid()), feeResult));
        }

        List<ItemStack> escrowStacks = pending.itemSnapshots();
        if (!takePendingEscrowFromSeller(player, pending)) {
            refundListingFee(sellerAccount.accountId(), pending.startingBid(), auctionReference(EVENT_LISTING_FEE_REFUND, auctionId));
            pendingListings.remove(player.getUUID());
            return AuctionActionResult.fail(pending.isBundle()
                    ? "One selected inventory item no longer matches this bundle listing."
                    : "The selected inventory item no longer matches this listing.");
        }

        pendingListings.remove(player.getUUID());
        AuctionActionResult activation = activateAuction(
                auctionId,
                player,
                escrowStacks,
                pending.title(),
                pending.description(),
                pending.endsAt(),
                now,
                pending.startingBid(),
                pending.buyoutPrice(),
                pending.reservePrice(),
                pending.format(),
                sellerAccount.accountId(),
                pending.isMainHand() ? "SELLER_MAIN_HAND" : pending.isBundle() ? "SELLER_INVENTORY_BUNDLE" : "SELLER_INVENTORY_SLOT_" + pending.slot()
        );
        if (activation.success()) {
            AuctionItem item = getAuctionItem(auctionId);
            if (item != null && Config.calculateListingFee(pending.startingBid()).compareTo(BigDecimal.ZERO) > 0) {
                item.recordFinancialEvent(AuctionFinancialEvent.fromBanking(auctionId, EVENT_LISTING_FEE, Config.calculateListingFee(pending.startingBid()), listingFeeReference, feeResult));
            }
        }
        return activation;
    }

    public AuctionActionResult discardPendingAuction(ServerPlayer player) {
        if (player == null) {
            return AuctionActionResult.fail("Only players can discard auction listings.");
        }
        PendingAuctionListing removed = pendingListings.remove(player.getUUID());
        return removed == null
                ? AuctionActionResult.fail("You do not have a pending auction listing to discard.")
                : AuctionActionResult.ok("Pending auction listing discarded.");
    }

    public Optional<AuctionListingPreview> getPendingListingPreview(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        pruneExpiredPendingListings();
        PendingAuctionListing pending = pendingListings.get(playerId);
        return pending == null ? Optional.empty() : Optional.of(pending.toPreview());
    }

    public void pruneExpiredPendingListings() {
        LocalDateTime now = LocalDateTime.now();
        pendingListings.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isExpired(now));
    }

    public AuctionActionResult placeBidWithEscrow(ServerPlayer bidder, UUID auctionId, BigDecimal amount) {
        return placeBidWithEscrow(bidder, auctionId, amount, null);
    }

    public AuctionActionResult placeBidWithEscrow(ServerPlayer bidder, UUID auctionId, BigDecimal amount, UUID bidderAccountId) {
        if (bidder == null) {
            return AuctionActionResult.fail("Only players can place bids.");
        }
        AuctionActionResult permission = UasPermissions.check(bidder, UasPermissionAction.BID);
        if (!permission.success()) {
            return permission;
        }
        if (AuctionAdminSavedData.isBlocked(bidder.getServer(), bidder.getUUID(), AuctionBanAction.BID)) {
            return auctionBanFailure(AuctionBanAction.BID);
        }
        return placeBidWithEscrow(bidder.getUUID(), auctionId, amount, bidderAccountId, true, bidder.getServer());
    }

    AuctionActionResult placeBidWithEscrow(UUID bidderId, UUID auctionId, BigDecimal amount) {
        return placeBidWithEscrow(bidderId, auctionId, amount, null, true);
    }

    AuctionActionResult placeBidWithEscrow(UUID bidderId, UUID auctionId, BigDecimal amount, UUID bidderAccountId) {
        return placeBidWithEscrow(bidderId, auctionId, amount, bidderAccountId, true);
    }

    private AuctionActionResult placeBidWithEscrow(UUID bidderId, UUID auctionId, BigDecimal amount, UUID bidderAccountId, boolean emitBidAlerts) {
        return placeBidWithEscrow(bidderId, auctionId, amount, bidderAccountId, emitBidAlerts, ServerLifecycleHooks.getCurrentServer());
    }

    private AuctionActionResult placeBidWithEscrow(UUID bidderId,
                                                   UUID auctionId,
                                                   BigDecimal amount,
                                                   UUID requestedBidderAccountId,
                                                   boolean emitBidAlerts,
                                                   MinecraftServer auditServer) {
        if (bidderId == null) {
            return AuctionActionResult.fail("Only players can place bids.");
        }
        if (mutationsBlocked) {
            return AuctionActionResult.fail("Auction storage has a migration problem. Bidding is blocked until an admin fixes the saved data.");
        }
        AuctionItem item = getAuctionItem(auctionId);
        if (item == null) {
            return AuctionActionResult.fail("Auction not found.");
        }
        synchronized (item) {
            if (item.getState() != AuctionState.ACTIVE) {
                return AuctionActionResult.fail("Auction is not active.");
            }
            if (item.isExpired()) {
                item.transitionTo(AuctionState.ENDED, "bid rejected after auction end time");
                return AuctionActionResult.fail("Auction already ended.");
            }
            if (!Config.allowSellerSelfBid && bidderId.equals(item.getPlayerId())) {
                auditSellerSelfBidAttempt(item, bidderId, auditServer);
                return AuctionActionResult.fail("You cannot bid on your own auction.");
            }
            BigDecimal safeAmount = safeMoney(amount);
            if (hasFractionalDollars(safeAmount)) {
                return AuctionActionResult.fail("Bids must use whole dollars.");
            }
            if (item.isSealedBid()) {
                return placeSealedBidWithEscrow(item, bidderId, safeAmount, requestedBidderAccountId, emitBidAlerts, auditServer);
            }
            BigDecimal minimum = minimumAcceptedBid(item);
            if (safeAmount.compareTo(minimum) < 0) {
                return AuctionActionResult.fail("Bid must be at least " + moneyLabel(minimum) + ".");
            }

            AccountSelection bidderAccount = resolveBidderAccount(bidderId, requestedBidderAccountId);
            if (!bidderAccount.success()) {
                item.recordRejectedBid(bidderId, null, safeAmount, AuctionBidResult.REJECTED_NO_ACCOUNT, "Bidder has no UBS primary account.");
                return AuctionActionResult.fail(bidderAccount.message());
            }
            UUID bidderAccountId = bidderAccount.accountId();
            UasBankingResult canSend = bankingService.validateCanSend(bidderAccountId, safeAmount);
            if (!canSend.success()) {
                item.recordRejectedBid(bidderId, bidderAccountId, safeAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, canSend.reason());
                return AuctionActionResult.fail("Your selected UBS account cannot pay this bid: " + canSend.reason());
            }

            UUID previousBidderId = item.getHighestBidderId();
            BigDecimal previousAmount = item.getHighestBid();
            UUID previousAccountId = item.getWinningBidRecord()
                    .flatMap(AuctionBidRecord::getBidderAccountId)
                    .orElse(null);

            String holdReference = auctionReference(EVENT_BID_ESCROW, item.getAuctionId());
            UasBankingResult hold = bankingService.withdraw(bidderAccountId, safeAmount, holdReference);
            recordBankingEvent(item, EVENT_BID_ESCROW, safeAmount, holdReference, hold);
            if (!hold.success()) {
                item.recordRejectedBid(bidderId, bidderAccountId, safeAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, hold.reason());
                return AuctionActionResult.fail("Your selected UBS account could not reserve the bid: " + hold.reason());
            }

            if (previousBidderId != null && previousAccountId != null) {
                String refundReference = auctionReference(EVENT_OUTBID_REFUND, item.getAuctionId());
                UasBankingResult refund = bankingService.deposit(previousAccountId, previousAmount, refundReference);
                recordBankingEvent(item, EVENT_OUTBID_REFUND, previousAmount, refundReference, refund);
                if (!refund.success()) {
                    String holdRefundReference = auctionReference(EVENT_BID_ESCROW_REFUND, item.getAuctionId());
                    UasBankingResult holdRefund = bankingService.deposit(bidderAccountId, safeAmount, holdRefundReference);
                    recordBankingEvent(item, EVENT_BID_ESCROW_REFUND, safeAmount, holdRefundReference, holdRefund);
                    item.recordRejectedBid(bidderId, bidderAccountId, safeAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, "Outbid refund failed: " + refund.reason());
                    String message = "Outbid refund failed for auction " + item.getAuctionId() + ": " + refund.reason();
                    UltimateAuctionSystem.LOGGER.warn("[UAS] {}", message);
                    alertOnlineAdmins("Auction Refund Failed", "Outbid refund failed for auction {0}: {1}", "ERROR", item.getAuctionId(), refund.reason());
                    return AuctionActionResult.fail("Could not safely refund the previous highest bidder. Bid was not accepted.");
                }
            }

            AuctionBidRecord bidRecord = item.recordBid(bidderId, bidderAccountId, safeAmount);
            if (!bidRecord.isAccepted()) {
                String refundReference = auctionReference(EVENT_BID_ESCROW_REFUND, item.getAuctionId());
                UasBankingResult refund = bankingService.deposit(bidderAccountId, safeAmount, refundReference);
                recordBankingEvent(item, EVENT_BID_ESCROW_REFUND, safeAmount, refundReference, refund);
                if (!refund.success()) {
                    item.transitionTo(AuctionState.FAILED_SETTLEMENT, "bid escrow refund failed after rejected bid: " + refund.reason());
                    postSettlementFailedEvent(item, "Bid escrow refund failed after rejected bid: " + refund.reason());
                    alertOnlineAdmins("Auction Settlement Failed", "Bid escrow refund failed for auction {0}: {1}", "ERROR", item.getAuctionId(), refund.reason());
                }
                return AuctionActionResult.fail(bidRecord.getReason());
            }

            markChanged("Auction storage marked dirty after accepted bid.");
            auditSuspiciousBidSignals(item, auditServer);
            boolean soldByBid = item.getState() == AuctionState.ENDED
                    && item.getBuyoutPrice().isPresent()
                    && safeAmount.compareTo(item.getBuyoutPrice().get()) >= 0;
            postAuctionEvent(new UasAuctionEvents.BidAccepted(eventSnapshot(item), bidderId, safeAmount, previousBidderId, previousAmount, soldByBid));
            if (previousBidderId != null) {
                postAuctionEvent(new UasAuctionEvents.Outbid(eventSnapshot(item), previousBidderId, previousAmount, bidderId, safeAmount));
            }
            if (soldByBid) {
                SettlementResult settlement = settleHeldBid(item);
                if (!settlement.success()) {
                    Object[] sellerArgs = {item.getAuctionId(), itemName(item), settlement.message()};
                    Object[] bidderArgs = {item.getAuctionId(), itemName(item)};
                    sendAuctionAlert(item.getPlayerId(), "Auction Settlement Failed", "Auction {0}: {1} could not pay out to your account. {2}", "ERROR", sellerArgs);
                    sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} could not pay out to your account. {2}", ChatFormatting.RED, sellerArgs, openAhAction(), myAuctionsAction());
                    sendAuctionAlert(bidderId, "Auction Settlement Delayed", "Auction {0}: {1} is waiting for payment settlement.", "WARNING", bidderArgs);
                    sendAuctionChatMessage(bidderId, "Auction {0}: {1} is waiting for payment settlement.", ChatFormatting.YELLOW, bidderArgs, openAhAction());
                    return AuctionActionResult.fail(settlement.message());
                }
                postAuctionEvent(new UasAuctionEvents.BuyoutAccepted(eventSnapshot(item), bidderId, safeAmount));
            }
            if (emitBidAlerts) {
                if (soldByBid) {
                    notifyAuctionSold(item, bidderId, safeAmount);
                } else {
                    notifyBidPlaced(item, bidderId, previousBidderId, previousAmount, safeAmount);
                }
            }
            return AuctionActionResult.ok(soldByBid ? "Buyout accepted. Claim the item from My Bids." : "Bid placed.");
        }
    }

    private AuctionActionResult placeSealedBidWithEscrow(AuctionItem item,
                                                         UUID bidderId,
                                                         BigDecimal safeAmount,
                                                         UUID requestedBidderAccountId,
                                                         boolean emitBidAlerts,
                                                         MinecraftServer auditServer) {
        BigDecimal previousOwnAmount = item.getBids().getOrDefault(bidderId, BigDecimal.ZERO);
        BigDecimal minimum = AuctionItem.sealedBidMinimum(item.getStartingBidPrice(), previousOwnAmount);
        if (safeAmount.compareTo(minimum) < 0) {
            return AuctionActionResult.fail("Sealed bid must be at least " + moneyLabel(minimum) + ".");
        }

        AccountSelection bidderAccount = resolveBidderAccount(bidderId, requestedBidderAccountId);
        if (!bidderAccount.success()) {
            item.recordRejectedBid(bidderId, null, safeAmount, AuctionBidResult.REJECTED_NO_ACCOUNT, "Bidder has no UBS primary account.");
            return AuctionActionResult.fail(bidderAccount.message());
        }
        UUID bidderAccountId = bidderAccount.accountId();
        UasBankingResult canSend = bankingService.validateCanSend(bidderAccountId, safeAmount);
        if (!canSend.success()) {
            item.recordRejectedBid(bidderId, bidderAccountId, safeAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, canSend.reason());
            return AuctionActionResult.fail("Your selected UBS account cannot pay this sealed bid: " + canSend.reason());
        }

        UUID previousAccountId = item.getCurrentBidRecordForBidder(bidderId)
                .flatMap(AuctionBidRecord::getBidderAccountId)
                .orElse(null);
        String safeAmountToken = moneyReferenceToken(safeAmount);
        String holdReference = auctionReference(EVENT_BID_ESCROW, item.getAuctionId()) + ":sealed:" + bidderId + ":" + safeAmountToken;
        UasBankingResult hold = bankingService.withdraw(bidderAccountId, safeAmount, holdReference);
        recordBankingEvent(item, EVENT_BID_ESCROW, safeAmount, holdReference, hold);
        if (!hold.success()) {
            item.recordRejectedBid(bidderId, bidderAccountId, safeAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, hold.reason());
            return AuctionActionResult.fail("Your selected UBS account could not reserve the sealed bid: " + hold.reason());
        }

        if (previousOwnAmount.compareTo(BigDecimal.ZERO) > 0 && previousAccountId != null) {
            String replaceReference = auctionReference(EVENT_SEALED_BID_REPLACE_REFUND, item.getAuctionId()) + ":" + bidderId + ":" + moneyReferenceToken(previousOwnAmount);
            UasBankingResult replaceRefund = bankingService.deposit(previousAccountId, previousOwnAmount, replaceReference);
            recordBankingEvent(item, EVENT_SEALED_BID_REPLACE_REFUND, previousOwnAmount, replaceReference, replaceRefund);
            if (!replaceRefund.success()) {
                String holdRefundReference = auctionReference(EVENT_BID_ESCROW_REFUND, item.getAuctionId()) + ":sealed:" + bidderId + ":" + safeAmountToken;
                UasBankingResult holdRefund = bankingService.deposit(bidderAccountId, safeAmount, holdRefundReference);
                recordBankingEvent(item, EVENT_BID_ESCROW_REFUND, safeAmount, holdRefundReference, holdRefund);
                item.recordRejectedBid(bidderId, bidderAccountId, safeAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, "Previous sealed bid refund failed: " + replaceRefund.reason());
                alertOnlineAdmins("Auction Refund Failed", "Sealed bid replacement refund failed for auction {0}: {1}", "ERROR", item.getAuctionId(), replaceRefund.reason());
                return AuctionActionResult.fail("Could not safely refund your previous sealed bid. New bid was not accepted.");
            }
        }

        AuctionBidRecord bidRecord = item.recordSealedBid(bidderId, bidderAccountId, safeAmount);
        if (!bidRecord.isAccepted()) {
            String refundReference = auctionReference(EVENT_BID_ESCROW_REFUND, item.getAuctionId()) + ":sealed:" + bidderId + ":" + safeAmountToken;
            UasBankingResult refund = bankingService.deposit(bidderAccountId, safeAmount, refundReference);
            recordBankingEvent(item, EVENT_BID_ESCROW_REFUND, safeAmount, refundReference, refund);
            if (!refund.success()) {
                item.transitionTo(AuctionState.FAILED_SETTLEMENT, "sealed bid escrow refund failed after rejected bid: " + refund.reason());
                postSettlementFailedEvent(item, "Sealed bid escrow refund failed after rejected bid: " + refund.reason());
                alertOnlineAdmins("Auction Settlement Failed", "Sealed bid escrow refund failed for auction {0}: {1}", "ERROR", item.getAuctionId(), refund.reason());
            }
            return AuctionActionResult.fail(bidRecord.getReason());
        }

        markChanged("Auction storage marked dirty after accepted sealed bid.");
        auditSuspiciousBidSignals(item, auditServer);
        postAuctionEvent(new UasAuctionEvents.BidAccepted(eventSnapshot(item), bidderId, safeAmount, null, BigDecimal.ZERO, false));
        if (emitBidAlerts) {
            notifySealedBidPlaced(item, bidderId, safeAmount, previousOwnAmount);
        }
        return AuctionActionResult.ok("Sealed bid placed.");
    }

    public AuctionActionResult buyout(ServerPlayer bidder, UUID auctionId) {
        return buyout(bidder, auctionId, null);
    }

    public AuctionActionResult buyout(ServerPlayer bidder, UUID auctionId, UUID bidderAccountId) {
        if (bidder == null) {
            return AuctionActionResult.fail("Only players can buy out auctions.");
        }
        AuctionActionResult permission = UasPermissions.check(bidder, UasPermissionAction.BUYOUT);
        if (!permission.success()) {
            return permission;
        }
        if (AuctionAdminSavedData.isBlocked(bidder.getServer(), bidder.getUUID(), AuctionBanAction.BUYOUT)) {
            return auctionBanFailure(AuctionBanAction.BUYOUT);
        }
        return buyout(bidder.getUUID(), auctionId, bidderAccountId, bidder.getServer());
    }

    AuctionActionResult buyout(UUID bidderId, UUID auctionId) {
        return buyout(bidderId, auctionId, null, ServerLifecycleHooks.getCurrentServer());
    }

    AuctionActionResult buyout(UUID bidderId, UUID auctionId, UUID bidderAccountId) {
        return buyout(bidderId, auctionId, bidderAccountId, ServerLifecycleHooks.getCurrentServer());
    }

    private AuctionActionResult buyout(UUID bidderId, UUID auctionId, UUID requestedBidderAccountId, MinecraftServer auditServer) {
        if (bidderId == null) {
            return AuctionActionResult.fail("Only players can buy out auctions.");
        }
        if (mutationsBlocked) {
            return AuctionActionResult.fail("Auction storage has a migration problem. Buyout is blocked until an admin fixes the saved data.");
        }
        AuctionItem item = getAuctionItem(auctionId);
        if (item == null) {
            return AuctionActionResult.fail("Auction not found.");
        }
        synchronized (item) {
            if (item.getState() != AuctionState.ACTIVE) {
                return AuctionActionResult.fail("Auction is not active.");
            }
            if (item.isExpired()) {
                item.transitionTo(AuctionState.ENDED, "buyout rejected after auction end time");
                return AuctionActionResult.fail("Auction already ended.");
            }
            if (!Config.allowSellerSelfBid && bidderId.equals(item.getPlayerId())) {
                auditSellerSelfBidAttempt(item, bidderId, auditServer);
                return AuctionActionResult.fail("You cannot buy out your own auction.");
            }
            Optional<BigDecimal> buyout = item.getBuyoutPrice();
            if (buyout.isEmpty()) {
                return AuctionActionResult.fail("This auction has no buyout price.");
            }
            BigDecimal buyoutAmount = safeMoney(buyout.get());
            if (!item.isSealedBid() && item.getHighestBidderId() != null && item.getHighestBid().compareTo(buyoutAmount) >= 0) {
                return AuctionActionResult.fail("The current bid already reached the buyout price.");
            }

            AccountSelection bidderAccount = resolveBidderAccount(bidderId, requestedBidderAccountId);
            if (!bidderAccount.success()) {
                item.recordRejectedBid(bidderId, null, buyoutAmount, AuctionBidResult.REJECTED_NO_ACCOUNT, "Bidder has no UBS primary account.");
                return AuctionActionResult.fail(bidderAccount.message());
            }
            UUID bidderAccountId = bidderAccount.accountId();
            UasBankingResult canSend = bankingService.validateCanSend(bidderAccountId, buyoutAmount);
            if (!canSend.success()) {
                item.recordRejectedBid(bidderId, bidderAccountId, buyoutAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, canSend.reason());
                return AuctionActionResult.fail("Your selected UBS account cannot pay this buyout: " + canSend.reason());
            }

            UUID previousBidderId = item.getHighestBidderId();
            BigDecimal previousAmount = item.getHighestBid();
            UUID previousAccountId = item.getWinningBidRecord()
                    .flatMap(AuctionBidRecord::getBidderAccountId)
                    .orElse(null);

            String holdReference = auctionReference(EVENT_BUYOUT_ESCROW, item.getAuctionId());
            UasBankingResult hold = bankingService.withdraw(bidderAccountId, buyoutAmount, holdReference);
            recordBankingEvent(item, EVENT_BUYOUT_ESCROW, buyoutAmount, holdReference, hold);
            if (!hold.success()) {
                item.recordRejectedBid(bidderId, bidderAccountId, buyoutAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, hold.reason());
                return AuctionActionResult.fail("Your selected UBS account could not reserve the buyout: " + hold.reason());
            }

            if (item.isSealedBid()) {
                AuctionActionResult sealedRefunds = refundSealedBidsExcept(item, Set.of(), EVENT_SEALED_BID_REFUND, "sealed bids refunded after buyout");
                if (!sealedRefunds.success()) {
                    String holdRefundReference = auctionReference(EVENT_BUYOUT_ESCROW_REFUND, item.getAuctionId());
                    UasBankingResult holdRefund = bankingService.deposit(bidderAccountId, buyoutAmount, holdRefundReference);
                    recordBankingEvent(item, EVENT_BUYOUT_ESCROW_REFUND, buyoutAmount, holdRefundReference, holdRefund);
                    item.recordRejectedBid(bidderId, bidderAccountId, buyoutAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, sealedRefunds.message());
                    return AuctionActionResult.fail("Could not safely refund sealed bids. Buyout was not accepted.");
                }
                previousBidderId = null;
                previousAmount = BigDecimal.ZERO;
                previousAccountId = null;
                item.clearCurrentBidsAfterRefund();
            } else if (previousBidderId != null && previousAccountId != null) {
                String refundReference = auctionReference(EVENT_OUTBID_REFUND, item.getAuctionId());
                UasBankingResult refund = bankingService.deposit(previousAccountId, previousAmount, refundReference);
                recordBankingEvent(item, EVENT_OUTBID_REFUND, previousAmount, refundReference, refund);
                if (!refund.success()) {
                    String holdRefundReference = auctionReference(EVENT_BUYOUT_ESCROW_REFUND, item.getAuctionId());
                    UasBankingResult holdRefund = bankingService.deposit(bidderAccountId, buyoutAmount, holdRefundReference);
                    recordBankingEvent(item, EVENT_BUYOUT_ESCROW_REFUND, buyoutAmount, holdRefundReference, holdRefund);
                    item.recordRejectedBid(bidderId, bidderAccountId, buyoutAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, "Outbid refund failed: " + refund.reason());
                    String message = "Buyout refund of previous bidder failed for auction " + item.getAuctionId() + ": " + refund.reason();
                    UltimateAuctionSystem.LOGGER.warn("[UAS] {}", message);
                    alertOnlineAdmins("Auction Refund Failed", "Buyout refund of previous bidder failed for auction {0}: {1}", "ERROR", item.getAuctionId(), refund.reason());
                    return AuctionActionResult.fail("Could not safely refund the previous highest bidder. Buyout was not accepted.");
                }
            }

            AuctionBidRecord bidRecord = item.recordBid(bidderId, bidderAccountId, buyoutAmount);
            if (!bidRecord.isAccepted()) {
                String refundReference = auctionReference(EVENT_BUYOUT_ESCROW_REFUND, item.getAuctionId());
                UasBankingResult refund = bankingService.deposit(bidderAccountId, buyoutAmount, refundReference);
                recordBankingEvent(item, EVENT_BUYOUT_ESCROW_REFUND, buyoutAmount, refundReference, refund);
                if (!refund.success()) {
                    item.transitionTo(AuctionState.FAILED_SETTLEMENT, "buyout escrow refund failed after rejected buyout: " + refund.reason());
                    postSettlementFailedEvent(item, "Buyout escrow refund failed after rejected buyout: " + refund.reason());
                    alertOnlineAdmins("Auction Settlement Failed", "Buyout escrow refund failed for auction {0}: {1}", "ERROR", item.getAuctionId(), refund.reason());
                }
                return AuctionActionResult.fail(bidRecord.getReason());
            }

            item.transitionTo(AuctionState.ENDED, "buyout accepted");
            SettlementResult settlement = settleHeldBid(item);
            if (!settlement.success()) {
                Object[] sellerArgs = {item.getAuctionId(), itemName(item), settlement.message()};
                Object[] bidderArgs = {item.getAuctionId(), itemName(item)};
                sendAuctionAlert(item.getPlayerId(), "Auction Settlement Failed", "Auction {0}: {1} could not pay out to your account. {2}", "ERROR", sellerArgs);
                sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} could not pay out to your account. {2}", ChatFormatting.RED, sellerArgs, openAhAction(), myAuctionsAction());
                sendAuctionAlert(bidderId, "Auction Settlement Delayed", "Auction {0}: {1} is waiting for payment settlement.", "WARNING", bidderArgs);
                sendAuctionChatMessage(bidderId, "Auction {0}: {1} is waiting for payment settlement.", ChatFormatting.YELLOW, bidderArgs, openAhAction());
                return AuctionActionResult.fail(settlement.message());
            }
            markChanged("Auction storage marked dirty after accepted buyout.");
            auditSuspiciousBidSignals(item, auditServer);
            postAuctionEvent(new UasAuctionEvents.BidAccepted(eventSnapshot(item), bidderId, buyoutAmount, previousBidderId, previousAmount, true));
            if (previousBidderId != null) {
                postAuctionEvent(new UasAuctionEvents.Outbid(eventSnapshot(item), previousBidderId, previousAmount, bidderId, buyoutAmount));
            }
            postAuctionEvent(new UasAuctionEvents.BuyoutAccepted(eventSnapshot(item), bidderId, buyoutAmount));
            notifyAuctionSold(item, bidderId, buyoutAmount);
            return AuctionActionResult.ok("Buyout accepted. Seller was paid; claim the item from My Bids.");
        }
    }

    public AuctionActionResult cancelOwnAuction(ServerPlayer seller, UUID auctionId, AuctionDeliverySavedData deliveryData) {
        if (seller == null) {
            return AuctionActionResult.fail("Only players can cancel auctions.");
        }
        AuctionActionResult permission = UasPermissions.check(seller, UasPermissionAction.CANCEL_OWN);
        if (!permission.success()) {
            return permission;
        }
        AuctionItem item = getAuctionItem(auctionId);
        if (item == null) {
            return AuctionActionResult.fail("Auction not found.");
        }
        synchronized (item) {
            if (!seller.getUUID().equals(item.getPlayerId())) {
                return AuctionActionResult.fail("You can only cancel your own auctions.");
            }
            if (item.getState() != AuctionState.ACTIVE) {
                return AuctionActionResult.fail("Only active auctions can be cancelled.");
            }
            if (item.getHighestBidderId() != null) {
                return AuctionActionResult.fail("You cannot cancel an auction after bids are placed.");
            }
            AuctionActionResult cancellationFee = chargeCancellationFee(seller, item);
            if (!cancellationFee.success()) {
                return cancellationFee;
            }
            if (!item.transitionTo(AuctionState.CANCELLED, "seller cancelled auction with no bids")) {
                return AuctionActionResult.fail("Auction could not be cancelled.");
            }
            giveOrDeliver(seller, item.getContents(), deliveryData, auctionId, "Cancelled auction return");
            markChanged("Auction storage marked dirty after auction cancellation.");
            postAuctionEvent(new UasAuctionEvents.Cancelled(eventSnapshot(item), seller.getUUID(), "Seller cancelled auction with no bids", false));
            auditSuspiciousCancellationSignals(seller.getUUID(), seller.getServer());
            notifyAuctionCancelled(item, seller.getUUID());
            return AuctionActionResult.ok("Auction cancelled and item returned.");
        }
    }

    public AuctionActionResult adminForceCancel(ServerPlayer admin, UUID auctionId, AuctionDeliverySavedData deliveryData) {
        if (admin == null) {
            return AuctionActionResult.fail("Only admins can force-cancel auctions.");
        }
        return adminForceCancel(
                admin.getUUID(),
                admin.getGameProfile().getName(),
                UasPermissions.has(admin, UasPermissionAction.ADMIN),
                auctionId,
                deliveryData,
                admin.getServer() == null ? null : AuctionAdminSavedData.get(admin.getServer()),
                false,
                "Admin force cancel"
        );
    }

    public AuctionActionResult adminForceCancel(UUID adminId,
                                                String adminName,
                                                boolean permitted,
                                                UUID auctionId,
                                                AuctionDeliverySavedData deliveryData,
                                                AuctionAdminSavedData adminData,
                                                boolean recoverItems,
                                                String reason) {
        if (!permitted) {
            return AuctionActionResult.fail("You do not have permission to force-cancel auctions.");
        }
        String safeReason = reason == null ? "" : reason.trim();
        if (safeReason.isBlank()) {
            return AuctionActionResult.fail("Force-cancel reason is required.");
        }
        String safeAdminName = adminName == null || adminName.isBlank() ? "Console" : adminName;
        AuctionItem item = getAuctionItem(auctionId);
        if (item == null) {
            return AuctionActionResult.fail("Auction not found.");
        }
        synchronized (item) {
            if (item.getState() == AuctionState.CLAIMED || item.getState() == AuctionState.CANCELLED) {
                return AuctionActionResult.fail("Auction has already been claimed or cancelled.");
            }
            if (recoverItems && adminData == null) {
                return AuctionActionResult.fail("Admin recovery storage is unavailable.");
            }

            UUID winnerAccountId = item.getWinningBidRecord()
                    .flatMap(AuctionBidRecord::getBidderAccountId)
                    .orElse(null);
            UUID winnerId = item.getHighestBidderId();
            if (item.isSealedBid()) {
                AuctionActionResult sealedRefunds = refundSealedBidsExcept(item, Set.of(), EVENT_ADMIN_FORCE_CANCEL_REFUND, "admin force-cancel sealed bid refunds");
                if (!sealedRefunds.success()) {
                    return sealedRefunds;
                }
            } else if (winnerId != null && winnerAccountId != null && item.getHighestBid().compareTo(BigDecimal.ZERO) > 0) {
                String refundReference = auctionReference(EVENT_ADMIN_FORCE_CANCEL_REFUND, item.getAuctionId());
                UasBankingResult refund = bankingService.deposit(winnerAccountId, item.getHighestBid(), refundReference);
                recordBankingEvent(item, EVENT_ADMIN_FORCE_CANCEL_REFUND, item.getHighestBid(), refundReference, refund);
                if (!refund.success()) {
                    return AuctionActionResult.fail("Could not refund the highest bid before force-cancelling: " + refund.reason());
                }
            }

            if (!item.transitionTo(AuctionState.CANCELLED, "admin force-cancelled by " + safeAdminName + ": " + safeReason)) {
                return AuctionActionResult.fail("Auction could not be force-cancelled.");
            }
            if (recoverItems) {
                AuctionRecoveryEntry recoveryEntry = AuctionRecoveryEntry.create(
                        item.getAuctionId(),
                        item.getPlayerId(),
                        playerName(item.getPlayerId()),
                        adminId,
                        safeAdminName,
                        safeReason,
                        item.getContents()
                );
                adminData.addRecovery(recoveryEntry);
            } else {
                giveOrDeliver(item.getPlayerId(), item.getContents(), deliveryData, auctionId, "Admin force-cancel return: " + safeReason);
            }
            markChanged("Auction storage marked dirty after admin force-cancel.");
            postAuctionEvent(new UasAuctionEvents.Cancelled(eventSnapshot(item), adminId, safeReason, true));
            Object[] sellerArgs = {item.getAuctionId(), itemName(item), safeAdminName, safeReason};
            if (recoverItems) {
                sendAuctionAlert(item.getPlayerId(), "Auction Force-Cancelled", "Auction {0}: {1} was force-cancelled by admin {2}. Reason: {3}. Item moved to admin recovery.", "WARNING", sellerArgs);
                sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} was force-cancelled by admin {2}. Reason: {3}. Item moved to admin recovery.", ChatFormatting.YELLOW, sellerArgs, openAhAction(), myAuctionsAction());
            } else {
                sendAuctionAlert(item.getPlayerId(), "Auction Force-Cancelled", "Auction {0}: {1} was force-cancelled by admin {2}. Reason: {3}. Item returned.", "WARNING", sellerArgs);
                sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} was force-cancelled by admin {2}. Reason: {3}. Item returned.", ChatFormatting.YELLOW, sellerArgs, openAhAction(), myAuctionsAction());
            }
            if (winnerId != null) {
                Object[] winnerArgs = {item.getAuctionId(), itemName(item), safeReason};
                sendAuctionAlert(winnerId, "Auction Force-Cancelled", "Auction {0}: Your bid on {1} was refunded after an admin force-cancel. Reason: {2}", "WARNING", winnerArgs);
                sendAuctionChatMessage(winnerId, "Auction {0}: Your bid on {1} was refunded after an admin force-cancel. Reason: {2}", ChatFormatting.YELLOW, winnerArgs, openAhAction());
            }
            alertSubscribers(item, exclusions(item.getPlayerId(), winnerId), "Auction Force-Cancelled", "Auction {0}: {1} was force-cancelled by admin {2}. Reason: {3}", "WARNING", item.getAuctionId(), itemName(item), safeAdminName, safeReason);
            return AuctionActionResult.ok(recoverItems
                    ? "Auction force-cancelled, bidder refunded, and item moved to admin recovery."
                    : "Auction force-cancelled, bidder refunded, and item returned.");
        }
    }

    public AuctionActionResult adminReleaseRecovery(ServerPlayer admin,
                                                    UUID recoveryId,
                                                    AuctionDeliverySavedData deliveryData,
                                                    AuctionAdminSavedData adminData,
                                                    String reason) {
        if (admin == null) {
            return AuctionActionResult.fail("Only admins can release recovery items.");
        }
        return adminReleaseRecovery(
                admin.getUUID(),
                admin.getGameProfile().getName(),
                UasPermissions.has(admin, UasPermissionAction.ADMIN),
                recoveryId,
                deliveryData,
                adminData,
                reason
        );
    }

    public AuctionActionResult adminReleaseRecovery(UUID adminId,
                                                    String adminName,
                                                    boolean permitted,
                                                    UUID recoveryId,
                                                    AuctionDeliverySavedData deliveryData,
                                                    AuctionAdminSavedData adminData,
                                                    String reason) {
        if (!permitted) {
            return AuctionActionResult.fail("You do not have permission to release recovery items.");
        }
        if (adminData == null) {
            return AuctionActionResult.fail("Admin recovery storage is unavailable.");
        }
        AuctionRecoveryEntry entry = adminData.getRecoveryEntry(recoveryId).orElse(null);
        if (entry == null) {
            return AuctionActionResult.fail("Recovery entry not found.");
        }
        if (!entry.active()) {
            return AuctionActionResult.fail("Recovery entry has already been released.");
        }
        String safeReason = reason == null || reason.isBlank() ? "Admin recovery release" : reason.trim();
        boolean released = adminData.releaseRecovery(recoveryId, adminId, adminName, safeReason);
        if (!released) {
            return AuctionActionResult.fail("Recovery entry has already been released.");
        }
        giveOrDeliver(entry.sellerId(), entry.contents(), deliveryData, entry.auctionId(), "Admin recovery release: " + safeReason);
        Object[] sellerArgs = {entry.auctionId(), entry.itemName(), safeReason};
        sendAuctionAlert(entry.sellerId(), "Auction Recovery Released", "Auction {0}: {1} was released from admin recovery. Reason: {2}", "INFO", sellerArgs);
        sendAuctionChatMessage(entry.sellerId(), "Auction {0}: {1} was released from admin recovery. Reason: {2}", ChatFormatting.GREEN, sellerArgs, openAhAction(), deliveryAction());
        return AuctionActionResult.ok("Recovery item released to seller delivery storage.");
    }

    public AuctionActionResult adminRetrySettlement(ServerPlayer admin, UUID auctionId, AuctionDeliverySavedData deliveryData) {
        if (admin == null) {
            return AuctionActionResult.fail("Only admins can retry auction settlement.");
        }
        AuctionActionResult permission = UasPermissions.check(admin, UasPermissionAction.ADMIN);
        if (!permission.success()) {
            return AuctionActionResult.fail("You do not have permission to retry auction settlement.");
        }
        return adminRetrySettlement(admin.getUUID(), admin.getGameProfile().getName(), true, auctionId, deliveryData);
    }

    public AuctionActionResult settlementRetryPreview(UUID auctionId) {
        AuctionItem item = getAuctionItem(auctionId);
        if (item == null) {
            return AuctionActionResult.fail("Auction not found.");
        }
        if (item.getState() != AuctionState.FAILED_SETTLEMENT) {
            return AuctionActionResult.fail("Only failed-settlement auctions can be retried.");
        }
        String previousFailure = item.latestFailedFinancialEvent()
                .map(event -> event.type() + " " + event.reference() + " failed: " + event.result())
                .orElse("No persisted financial failure is attached to this auction.");
        String action = item.getHighestBidderId() == null
                ? "Move auction back to ended with no winner."
                : "Retry seller payout, then deliver the escrowed item to the winning bidder.";
        return AuctionActionResult.ok("Previous failure: " + previousFailure + " Proposed action: " + action);
    }

    public AuctionActionResult adminRetrySettlement(UUID adminId,
                                                    String adminName,
                                                    boolean permitted,
                                                    UUID auctionId,
                                                    AuctionDeliverySavedData deliveryData) {
        if (!permitted) {
            return AuctionActionResult.fail("You do not have permission to retry auction settlement.");
        }
        AuctionItem item = getAuctionItem(auctionId);
        if (item == null) {
            return AuctionActionResult.fail("Auction not found.");
        }
        synchronized (item) {
            if (item.getState() != AuctionState.FAILED_SETTLEMENT) {
                return AuctionActionResult.fail("Only failed-settlement auctions can be retried.");
            }
            if (item.isSealedBid()) {
                item.selectWinningSealedBid();
            }
            UUID winnerId = item.getHighestBidderId();
            if (winnerId == null) {
                item.transitionTo(AuctionState.ENDED, "admin retry found no winning bidder");
                markChanged("Auction storage marked dirty after settlement retry recovery.");
                return AuctionActionResult.ok("Auction has no winner; moved back to ended state.");
            }
            if (!item.isReserveMet()) {
                AuctionActionResult reserveRefund = refundBelowReserveBid(item, "admin retry reserve refund");
                return reserveRefund.success()
                        ? AuctionActionResult.ok("Reserve price was not met. Highest bid refunded and auction ended without a buyer.")
                        : reserveRefund;
            }
            AuctionActionResult sealedRefunds = refundLosingSealedBids(item, "sealed loser refunds before admin settlement retry");
            if (!sealedRefunds.success()) {
                return sealedRefunds;
            }
            SettlementResult settlement = settleHeldBid(item);
            if (!settlement.success()) {
                Object[] args = {item.getAuctionId(), itemName(item)};
                sendAuctionAlert(item.getPlayerId(), "Auction Settlement Retry Failed", "Auction {0}: {1} still could not pay out after retry.", "ERROR", args);
                sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} still could not pay out after retry.", ChatFormatting.RED, args, openAhAction(), myAuctionsAction());
                sendAuctionAlert(winnerId, "Auction Settlement Retry Failed", "Auction {0}: {1} still could not finish settlement.", "ERROR", args);
                sendAuctionChatMessage(winnerId, "Auction {0}: {1} still could not finish settlement.", ChatFormatting.RED, args, openAhAction());
                return AuctionActionResult.fail(settlement.message());
            }
            giveOrDeliver(winnerId, item.getContents(), deliveryData, auctionId, "Won auction item");
            item.transitionTo(AuctionState.CLAIMED, "admin retried settlement and delivered item by " + (adminName == null || adminName.isBlank() ? "console" : adminName));
            markChanged("Auction storage marked dirty after admin settlement retry.");
            postAuctionEvent(new UasAuctionEvents.Claimed(eventSnapshot(item), winnerId, false));
            Object[] args = {item.getAuctionId(), itemName(item)};
            sendAuctionAlert(item.getPlayerId(), "Auction Settlement Recovered", "Auction {0}: {1} was paid out after an admin retry.", "SUCCESS", args);
            sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} was paid out after an admin retry.", ChatFormatting.GREEN, args, openAhAction(), myAuctionsAction());
            sendAuctionAlert(winnerId, "Auction Won", "Auction {0}: {1} was delivered after an admin settlement retry.", "SUCCESS", args);
            sendAuctionChatMessage(winnerId, "Auction {0}: {1} was delivered after an admin settlement retry.", ChatFormatting.GREEN, args, openAhAction());
            alertSubscribers(item, exclusions(item.getPlayerId(), winnerId), "Auction Settlement Recovered", "Auction {0}: {1} was recovered by an admin.", "INFO", item.getAuctionId(), itemName(item));
            return AuctionActionResult.ok("Auction settlement retried, paid, and delivered.");
        }
    }

    public AuctionActionResult claimAuction(ServerPlayer player, UUID auctionId, AuctionDeliverySavedData deliveryData) {
        if (player == null) {
            return AuctionActionResult.fail("Only players can claim auction items.");
        }
        AuctionActionResult permission = UasPermissions.check(player, UasPermissionAction.CLAIM);
        if (!permission.success()) {
            return permission;
        }
        AuctionItem item = getAuctionItem(auctionId);
        if (item == null) {
            return AuctionActionResult.fail("Auction not found.");
        }
        synchronized (item) {
            if (item.getState() == AuctionState.CLAIMED || item.getState() == AuctionState.CANCELLED) {
                return AuctionActionResult.fail("Auction has already been claimed or cancelled.");
            }
            if (item.getState() == AuctionState.FAILED_SETTLEMENT) {
                return AuctionActionResult.fail("Auction settlement is pending admin recovery.");
            }
            if (item.getState() == AuctionState.ACTIVE && !item.isExpired()) {
                return AuctionActionResult.fail("Auction has not ended yet.");
            }
            if (item.getState() == AuctionState.ACTIVE) {
                item.transitionTo(AuctionState.ENDED, "auction expired before claim");
            }

            if (item.isSealedBid()) {
                item.selectWinningSealedBid();
            }
            UUID winnerId = item.getHighestBidderId();
            if (winnerId != null && !item.isReserveMet()) {
                AuctionActionResult reserveRefund = refundBelowReserveBid(item, "claim attempted below reserve");
                if (!reserveRefund.success()) {
                    return reserveRefund;
                }
                if (!player.getUUID().equals(item.getPlayerId())) {
                    return AuctionActionResult.ok("Reserve price was not met. Your bid was refunded.");
                }
                winnerId = null;
            }
            if (winnerId == null) {
                if (!player.getUUID().equals(item.getPlayerId())) {
                    return AuctionActionResult.fail("Only the seller can claim an unsold auction return.");
                }
                giveOrDeliver(player, item.getContents(), deliveryData, auctionId, "Expired unsold auction return");
                item.transitionTo(AuctionState.CLAIMED, "seller claimed unsold return");
                markChanged("Auction storage marked dirty after seller return claim.");
                postAuctionEvent(new UasAuctionEvents.Claimed(eventSnapshot(item), player.getUUID(), true));
                notifyAuctionEndedUnsold(item, player.getUUID());
                return AuctionActionResult.ok("Unsold item returned.");
            }

            if (!player.getUUID().equals(winnerId)) {
                return AuctionActionResult.fail("Only the winning bidder can claim this auction.");
            }
            AuctionActionResult sealedRefunds = refundLosingSealedBids(item, "sealed loser refunds before winner claim");
            if (!sealedRefunds.success()) {
                return sealedRefunds;
            }
            SettlementResult settlement = settleHeldBid(item);
            if (!settlement.success()) {
                Object[] args = {item.getAuctionId(), itemName(item), settlement.message()};
                sendAuctionAlert(item.getPlayerId(), "Auction Settlement Failed", "Auction {0}: {1} could not pay out to your account. {2}", "ERROR", args);
                sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} could not pay out to your account. {2}", ChatFormatting.RED, args, openAhAction(), myAuctionsAction());
                return AuctionActionResult.fail(settlement.message());
            }
            if (item.getBuyoutPrice().isEmpty() || item.getHighestBid().compareTo(item.getBuyoutPrice().get()) < 0) {
                notifyAuctionSold(item, player.getUUID(), item.getHighestBid());
            }
            giveOrDeliver(player, item.getContents(), deliveryData, auctionId, "Won auction item");
            item.transitionTo(AuctionState.CLAIMED, "winner claimed auction item");
            markChanged("Auction storage marked dirty after winner claim.");
            postAuctionEvent(new UasAuctionEvents.Claimed(eventSnapshot(item), player.getUUID(), false));
            return AuctionActionResult.ok("Auction item claimed.");
        }
    }

    public AuctionActionResult relistAuction(ServerPlayer seller,
                                             UUID sourceAuctionId,
                                             String title,
                                             BigDecimal startingBidPrice,
                                             BigDecimal buyoutPrice,
                                             LocalDateTime end,
                                             String description,
                                             UUID sellerAccountId) {
        return relistAuction(seller, sourceAuctionId, title, startingBidPrice, buyoutPrice, BigDecimal.ZERO, end, description, sellerAccountId);
    }

    public AuctionActionResult relistAuction(ServerPlayer seller,
                                             UUID sourceAuctionId,
                                             String title,
                                             BigDecimal startingBidPrice,
                                             BigDecimal buyoutPrice,
                                             BigDecimal reservePrice,
                                             LocalDateTime end,
                                             String description,
                                             UUID sellerAccountId) {
        return relistAuction(seller, sourceAuctionId, title, startingBidPrice, buyoutPrice, reservePrice, null, end, description, sellerAccountId);
    }

    public AuctionActionResult relistAuction(ServerPlayer seller,
                                             UUID sourceAuctionId,
                                             String title,
                                             BigDecimal startingBidPrice,
                                             BigDecimal buyoutPrice,
                                             BigDecimal reservePrice,
                                             AuctionFormat format,
                                             LocalDateTime end,
                                             String description,
                                             UUID sellerAccountId) {
        if (seller == null) {
            return AuctionActionResult.fail("Only players can relist auctions.");
        }
        AuctionActionResult permission = UasPermissions.check(seller, UasPermissionAction.LIST);
        if (!permission.success()) {
            return permission;
        }
        AuctionItem source = getAuctionItem(sourceAuctionId);
        if (source == null) {
            return AuctionActionResult.fail("Auction not found.");
        }
        synchronized (source) {
            if (!seller.getUUID().equals(source.getPlayerId())) {
                return AuctionActionResult.fail("Only the original seller can relist this auction.");
            }
            if (source.getHighestBidderId() != null) {
                return AuctionActionResult.fail("Only unsold auctions can be relisted.");
            }
            if (source.getState() == AuctionState.CLAIMED || source.getState() == AuctionState.CANCELLED) {
                return AuctionActionResult.fail("Auction has already been claimed or cancelled.");
            }
            if (source.getState() == AuctionState.FAILED_SETTLEMENT) {
                return AuctionActionResult.fail("Auction settlement is pending admin recovery.");
            }
            if (source.getState() == AuctionState.ACTIVE && !source.isExpired()) {
                return AuctionActionResult.fail("Only expired unsold auctions can be relisted.");
            }
            if (source.getState() == AuctionState.ACTIVE && !source.transitionTo(AuctionState.ENDED, "auction expired before relist")) {
                return AuctionActionResult.fail("Auction could not be prepared for relisting.");
            }
            if (source.getState() != AuctionState.ENDED) {
                return AuctionActionResult.fail("Only expired unsold auctions can be relisted.");
            }
            if (!source.isEscrowed()) {
                return AuctionActionResult.fail("Auction item is not available to relist.");
            }

            List<ItemStack> relistContents = source.getContents();
            BigDecimal relistStartingBid = safeMoney(startingBidPrice);
            BigDecimal relistBuyout = buyoutPrice == null ? BigDecimal.ZERO : buyoutPrice;
            BigDecimal relistReserve = reservePrice == null ? BigDecimal.ZERO : reservePrice;
            AuctionFormat relistFormat = format == null ? source.getFormat() : format;
            UUID accountId = sellerAccountId == null ? source.getSellerAccountId() : sellerAccountId;
            AuctionActionResult validation = validateListingRequest(seller, relistContents, relistStartingBid, relistBuyout, relistReserve, relistFormat, end, accountId);
            if (!validation.success()) {
                return validation;
            }
            AccountSelection sellerAccount = validateSellerAccount(seller.getUUID(), accountId, relistStartingBid);
            if (!sellerAccount.success()) {
                return AuctionActionResult.fail(sellerAccount.message());
            }

            UUID relistedAuctionId = UUID.randomUUID();
            while (AuctionItems.containsKey(relistedAuctionId)) {
                relistedAuctionId = UUID.randomUUID();
            }
            String listingFeeReference = auctionReference(EVENT_LISTING_FEE, relistedAuctionId);
            UasBankingResult feeResult = chargeListingFee(sellerAccount.accountId(), relistStartingBid, listingFeeReference);
            if (!feeResult.success()) {
                return AuctionActionResult.fail(feeFailureMessage("listing fee", Config.calculateListingFee(relistStartingBid), feeResult));
            }

            String relistTitle = title == null || title.isBlank() ? source.getTitle() : title.trim();
            String relistDescription = description == null ? source.getDescription() : description;
            AuctionItem relisted = new AuctionItem(
                    relistedAuctionId,
                    relistContents,
                    relistTitle,
                    relistDescription,
                    end,
                    LocalDateTime.now(),
                    relistStartingBid,
                    seller.getUUID(),
                    sellerAccount.accountId(),
                    relistBuyout,
                    relistReserve,
                    relistFormat
            );
            relisted.markEscrowed("RELISTED_FROM_" + source.getAuctionId());
            Optional<String> activationError = relisted.validateForActivation();
            if (activationError.isPresent()) {
                refundListingFee(sellerAccount.accountId(), relistStartingBid, auctionReference(EVENT_LISTING_FEE_REFUND, relistedAuctionId));
                return AuctionActionResult.fail("Auction escrow failed validation: " + activationError.get() + ".");
            }
            if (!source.transitionTo(AuctionState.CLAIMED, "seller relisted unsold auction as " + relistedAuctionId)) {
                refundListingFee(sellerAccount.accountId(), relistStartingBid, auctionReference(EVENT_LISTING_FEE_REFUND, relistedAuctionId));
                return AuctionActionResult.fail("Auction could not be relisted.");
            }

            attachMutationTracking(relisted);
            AuctionItems.put(relisted.getAuctionId(), relisted);
            if (Config.calculateListingFee(relistStartingBid).compareTo(BigDecimal.ZERO) > 0) {
                relisted.recordFinancialEvent(AuctionFinancialEvent.fromBanking(relistedAuctionId, EVENT_LISTING_FEE, Config.calculateListingFee(relistStartingBid), listingFeeReference, feeResult));
            }
            markChanged("Auction storage marked dirty after auction relist.");
            recordListingStats(relisted, seller.getGameProfile().getName(), seller.getServer());
            postAuctionEvent(new UasAuctionEvents.ListingCreated(eventSnapshot(relisted), seller.getUUID()));
            sendAuctionCreatedMessage(seller, relisted);
            return AuctionActionResult.ok("Auction relisted: " + relistedAuctionId, relistedAuctionId);
        }
    }

    public AuctionActionResult withdrawDelivery(ServerPlayer player, UUID deliveryId, AuctionDeliverySavedData deliveryData) {
        if (player == null) {
            return AuctionActionResult.fail("Only players can withdraw delivery items.");
        }
        if (deliveryData == null) {
            return AuctionActionResult.fail("Auction delivery storage is unavailable.");
        }
        Optional<AuctionDeliveryEntry> entry = deliveryData.removeDelivery(player.getUUID(), deliveryId);
        if (entry.isEmpty()) {
            return AuctionActionResult.fail("Delivery item not found.");
        }
        List<ItemStack> stacks = entry.get().items();
        if (!canInventoryFit(player, stacks)) {
            deliveryData.addDelivery(player.getUUID(), entry.get().auctionId(), stacks, entry.get().reason());
            return AuctionActionResult.fail("Your inventory is full.");
        }
        for (ItemStack stack : stacks) {
            player.getInventory().add(stack.copy());
        }
        player.getInventory().setChanged();
        return AuctionActionResult.ok(entry.get().bundle() ? "Delivery bundle withdrawn." : "Delivery item withdrawn.");
    }

    public AuctionActionResult toggleNotifications(ServerPlayer player, UUID auctionId) {
        if (player == null) {
            return AuctionActionResult.fail("Only players can change auction notifications.");
        }
        if (AuctionAdminSavedData.isBlocked(player.getServer(), player.getUUID(), AuctionBanAction.WATCH)) {
            return auctionBanFailure(AuctionBanAction.WATCH);
        }
        AuctionItem item = getAuctionItem(auctionId);
        if (item == null) {
            return AuctionActionResult.fail("Auction not found.");
        }
        boolean alreadySubscribed = item.isNotificationSubscriber(player.getUUID());
        if (!alreadySubscribed && item.getState() != AuctionState.ACTIVE) {
            return AuctionActionResult.fail("Auction is no longer active.");
        }
        if (!alreadySubscribed && watchedAuctionCount(player.getUUID()) >= Config.maxWatchedAuctionsPerPlayer) {
            return AuctionActionResult.fail("Watchlist limit reached. Unwatch another auction first.");
        }
        boolean subscribed = item.toggleNotificationSubscriber(player.getUUID());
        markChanged("Auction storage marked dirty after notification subscription change.");
        return AuctionActionResult.ok(subscribed ? "Auction notifications enabled." : "Auction notifications disabled.");
    }

    private long watchedAuctionCount(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        return AuctionItems.values().stream()
                .filter(item -> item != null
                        && item.getState() == AuctionState.ACTIVE
                        && item.isNotificationSubscriber(playerId))
                .count();
    }

    public void sendActionAlert(ServerPlayer player, AuctionActionResult result) {
        if (player == null || result == null || result.message().isBlank()) {
            return;
        }
        sendAuctionAlert(
                player.getUUID(),
                ALERT_TITLE,
                result.message(),
                result.success() ? "SUCCESS" : "ERROR"
        );
    }

    private void notifyBidPlaced(AuctionItem item, UUID bidderId, UUID previousBidderId, BigDecimal previousAmount, BigDecimal amount) {
        String itemName = itemName(item);
        String bidAmount = moneyLabel(amount);
        String bidderName = playerName(bidderId);
        UUID sellerId = item.getPlayerId();

        if (sellerId != null && !sellerId.equals(bidderId)) {
            Object[] args = {item.getAuctionId(), bidderName, bidAmount, itemName};
            sendAuctionAlert(sellerId, "New Auction Bid", "Auction {0}: {1} bid {2} on {3}.", "INFO", args);
            sendAuctionChatMessage(sellerId, "Auction {0}: {1} bid {2} on {3}.", ChatFormatting.AQUA, args, openAhAction(), myAuctionsAction());
        }
        if (previousBidderId != null && !previousBidderId.equals(bidderId)) {
            Object[] args = {item.getAuctionId(), itemName, moneyLabel(previousAmount), bidAmount};
            sendAuctionAlert(previousBidderId, "You Were Outbid", "Auction {0}: You were outbid on {1}. Refund succeeded for {2}. New bid: {3}.", "WARNING", args);
            sendAuctionChatMessage(previousBidderId, "Auction {0}: You were outbid on {1}. Refund succeeded for {2}. New bid: {3}.", ChatFormatting.YELLOW, args, bidSuggestAction(item), openAhAction());
        }

        Set<UUID> excluded = exclusions(bidderId, sellerId, previousBidderId);
        alertSubscribers(item, excluded, "Auction Updated", "Auction {0}: {1} bid {2} on {3}.", "INFO", item.getAuctionId(), bidderName, bidAmount, itemName);
    }

    private void notifySealedBidPlaced(AuctionItem item, UUID bidderId, BigDecimal amount, BigDecimal previousAmount) {
        String itemName = itemName(item);
        String bidAmount = moneyLabel(amount);
        UUID sellerId = item.getPlayerId();

        Object[] bidderArgs = {item.getAuctionId(), itemName, bidAmount};
        sendAuctionAlert(bidderId, "Sealed Bid Placed", "Auction {0}: Your sealed bid on {1} is {2}.", "SUCCESS", bidderArgs);
        sendAuctionChatMessage(bidderId, "Auction {0}: Your sealed bid on {1} is {2}.", ChatFormatting.GREEN, bidderArgs, openAhAction());

        if (sellerId != null && !sellerId.equals(bidderId)) {
            Object[] sellerArgs = {item.getAuctionId(), itemName};
            sendAuctionAlert(sellerId, "New Sealed Bid", "Auction {0}: A sealed bid was placed on {1}. Amount hidden until close.", "INFO", sellerArgs);
            sendAuctionChatMessage(sellerId, "Auction {0}: A sealed bid was placed on {1}. Amount hidden until close.", ChatFormatting.AQUA, sellerArgs, openAhAction(), myAuctionsAction());
        }

        alertSubscribers(item, exclusions(bidderId, sellerId), "Auction Updated", "Auction {0}: {1} received a sealed bid.", "INFO", item.getAuctionId(), itemName);
    }

    private void notifyAuctionSold(AuctionItem item, UUID buyerId, BigDecimal amount) {
        postAuctionEvent(new UasAuctionEvents.Sold(eventSnapshot(item), buyerId, amount));
        String itemName = itemName(item);
        String saleAmount = moneyLabel(amount);
        String buyerName = playerName(buyerId);
        UUID sellerId = item.getPlayerId();

        if (buyerId != null) {
            Object[] args = {item.getAuctionId(), itemName, saleAmount};
            sendAuctionAlert(buyerId, "Auction Won", "Auction {0}: You won {1} for {2}. Claim is available.", "SUCCESS", args);
            sendAuctionChatMessage(buyerId, "Auction {0}: You won {1} for {2}. Claim is available.", ChatFormatting.GREEN, args, claimAction(item), openAhAction());
        }
        if (sellerId != null && !sellerId.equals(buyerId)) {
            Object[] args = {item.getAuctionId(), itemName, buyerName, saleAmount};
            sendAuctionAlert(sellerId, "Auction Sold", "Auction {0}: {1} sold to {2} for {3}. Seller payout processed.", "SUCCESS", args);
            sendAuctionChatMessage(sellerId, "Auction {0}: {1} sold to {2} for {3}. Seller payout processed.", ChatFormatting.GREEN, args, openAhAction(), myAuctionsAction());
        }

        Set<UUID> losingBidders = new HashSet<>(item.getBids().keySet());
        losingBidders.remove(buyerId);
        for (UUID losingBidderId : losingBidders) {
            Object[] args = {item.getAuctionId(), itemName, saleAmount};
            sendAuctionAlert(losingBidderId, "Auction Sold", "Auction {0}: {1} sold to another player for {2}. You did not win.", "WARNING", args);
            sendAuctionChatMessage(losingBidderId, "Auction {0}: {1} sold to another player for {2}. You did not win.", ChatFormatting.YELLOW, args, openAhAction());
        }

        Set<UUID> excluded = exclusions(buyerId, sellerId);
        excluded.addAll(losingBidders);
        alertSubscribers(item, excluded, "Auction Sold", "Auction {0}: {1} sold for {2}.", "INFO", item.getAuctionId(), itemName, saleAmount);
        clearCompletedWatchers(item);
    }

    private void notifyAuctionCancelled(AuctionItem item, UUID sellerId) {
        alertSubscribers(item, exclusions(sellerId), "Auction Cancelled", "Auction {0}: {1} was cancelled by the seller.", "WARNING", item.getAuctionId(), itemName(item));
        clearCompletedWatchers(item);
    }

    private void notifyAuctionEndedUnsold(AuctionItem item, UUID sellerId) {
        alertSubscribers(item, exclusions(sellerId), "Auction Ended", "Auction {0}: {1} ended without a buyer.", "INFO", item.getAuctionId(), itemName(item));
        clearCompletedWatchers(item);
    }

    private void notifyReserveNotMet(AuctionItem item, UUID bidderId, BigDecimal refundedAmount) {
        String itemName = itemName(item);
        String amount = moneyLabel(refundedAmount);
        Object[] sellerArgs = {item.getAuctionId(), itemName, amount};
        sendAuctionAlert(item.getPlayerId(), "Reserve Not Met", "Auction {0}: {1} ended below reserve. Highest bid {2} was refunded; item can be claimed.", "INFO", sellerArgs);
        sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} ended below reserve. Highest bid {2} was refunded; item can be claimed.", ChatFormatting.AQUA, sellerArgs, openAhAction(), myAuctionsAction());
        if (bidderId != null) {
            Object[] bidderArgs = {item.getAuctionId(), itemName, amount};
            sendAuctionAlert(bidderId, "Reserve Not Met", "Auction {0}: Your bid of {2} on {1} was refunded because reserve was not met.", "INFO", bidderArgs);
            sendAuctionChatMessage(bidderId, "Auction {0}: Your bid of {2} on {1} was refunded because reserve was not met.", ChatFormatting.AQUA, bidderArgs, openAhAction());
        }
        alertSubscribers(item, exclusions(item.getPlayerId(), bidderId), "Reserve Not Met", "Auction {0}: {1} ended below reserve.", "INFO", item.getAuctionId(), itemName);
        clearCompletedWatchers(item);
    }

    private void notifySealedReserveNotMet(AuctionItem item, Set<UUID> bidders) {
        String itemName = itemName(item);
        Object[] sellerArgs = {item.getAuctionId(), itemName};
        sendAuctionAlert(item.getPlayerId(), "Reserve Not Met", "Auction {0}: {1} ended below reserve. Sealed bids were refunded; item can be claimed.", "INFO", sellerArgs);
        sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} ended below reserve. Sealed bids were refunded; item can be claimed.", ChatFormatting.AQUA, sellerArgs, openAhAction(), myAuctionsAction());
        Set<UUID> safeBidders = bidders == null ? Set.of() : bidders;
        for (UUID bidderId : safeBidders) {
            if (bidderId == null) {
                continue;
            }
            Object[] bidderArgs = {item.getAuctionId(), itemName};
            sendAuctionAlert(bidderId, "Reserve Not Met", "Auction {0}: Your sealed bid on {1} was refunded because reserve was not met.", "INFO", bidderArgs);
            sendAuctionChatMessage(bidderId, "Auction {0}: Your sealed bid on {1} was refunded because reserve was not met.", ChatFormatting.AQUA, bidderArgs, openAhAction());
        }
        Set<UUID> excluded = exclusions(item.getPlayerId());
        excluded.addAll(safeBidders);
        alertSubscribers(item, excluded, "Reserve Not Met", "Auction {0}: {1} ended below reserve.", "INFO", item.getAuctionId(), itemName);
        clearCompletedWatchers(item);
    }

    private void clearCompletedWatchers(AuctionItem item) {
        if (item != null && item.clearNotificationSubscribers()) {
            markChanged("Auction storage marked dirty after completed auction watchlist cleanup.");
        }
    }

    private void alertSubscribers(AuctionItem item, Set<UUID> excluded, String title, String message, String tone, Object... args) {
        if (item == null) {
            return;
        }
        Set<UUID> safeExcluded = excluded == null ? Set.of() : excluded;
        for (UUID subscriberId : item.getNotificationSubscribers()) {
            if (subscriberId != null && !safeExcluded.contains(subscriberId)) {
                sendAuctionAlert(subscriberId, title, message, tone, args);
                sendAuctionChatMessage(subscriberId, message, toneColor(tone), args, openAhAction());
            }
        }
    }

    private void sendAuctionAlert(UUID playerId, String title, String message, String tone, Object... args) {
        if (playerId == null || message == null || message.isBlank()) {
            return;
        }
        ServerPlayer player = onlinePlayer(playerId);
        String localizedTitle = UasTranslations.plain(player, title);
        String localizedMessage = UasTranslations.formatPlain(player, message, args);
        UasAlertResult result = switch (tone == null ? "" : tone) {
            case "SUCCESS" -> bankingService.sendSuccessAlert(playerId, localizedTitle, localizedMessage, ALERT_DURATION_MS);
            case "ERROR" -> bankingService.sendErrorAlert(playerId, localizedTitle, localizedMessage, ALERT_DURATION_MS);
            case "WARNING" -> bankingService.sendWarningAlert(playerId, localizedTitle, localizedMessage, ALERT_DURATION_MS);
            default -> bankingService.sendInfoAlert(playerId, localizedTitle, localizedMessage, ALERT_DURATION_MS);
        };
        if (result == null || !result.success()) {
            sendFallbackSystemMessage(playerId, localizedMessage, toneColor(tone));
        }
    }

    private void sendAuctionChatMessage(UUID playerId,
                                        String message,
                                        ChatFormatting color,
                                        AuctionChatAction... actions) {
        sendAuctionChatMessage(playerId, message, color, new Object[0], actions);
    }

    private void sendAuctionChatMessage(UUID playerId,
                                        String message,
                                        ChatFormatting color,
                                        Object[] args,
                                        AuctionChatAction... actions) {
        ServerPlayer player = onlinePlayer(playerId);
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        MutableComponent line = Component.literal("[AH] ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(UasTranslations.formatPlain(player, message, args)).withStyle(color));
        if (actions != null) {
            for (AuctionChatAction action : actions) {
                if (action == null || action.command() == null || action.command().isBlank()) {
                    continue;
                }
                line.append(Component.literal(" "))
                        .append(Component.literal(UasTranslations.plain(player, action.label())).withStyle(style -> style
                                .withColor(action.color())
                                .withClickEvent(new ClickEvent(action.clickAction(), action.command()))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(UasTranslations.plain(player, action.hover()))))));
            }
        }
        player.sendSystemMessage(line, false);
    }

    private AuctionChatAction openAhAction() {
        return new AuctionChatAction("[Open /ah]", "/ah", "Open the Auction House GUI.", ChatFormatting.YELLOW, ClickEvent.Action.RUN_COMMAND);
    }

    private AuctionChatAction myAuctionsAction() {
        return new AuctionChatAction("[My Auctions]", "/ah mine active", "Show your auction listings in chat.", ChatFormatting.GOLD, ClickEvent.Action.RUN_COMMAND);
    }

    private AuctionChatAction deliveryAction() {
        return new AuctionChatAction("[Deliveries]", "/ah", "Open the Auction House delivery storage.", ChatFormatting.GREEN, ClickEvent.Action.RUN_COMMAND);
    }

    private AuctionChatAction claimAction(AuctionItem item) {
        return new AuctionChatAction("[Claim]", "/ah claim " + item.getAuctionId(), "Claim this auction if it is available.", ChatFormatting.GREEN, ClickEvent.Action.RUN_COMMAND);
    }

    private AuctionChatAction bidSuggestAction(AuctionItem item) {
        return new AuctionChatAction("[Bid]", "/ah bid " + item.getAuctionId() + " ", "Suggest a bid command for this auction.", ChatFormatting.GREEN, ClickEvent.Action.SUGGEST_COMMAND);
    }

    private ServerPlayer onlinePlayer(UUID playerId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null || playerId == null ? null : server.getPlayerList().getPlayer(playerId);
    }

    private void sendFallbackSystemMessage(UUID playerId, String message, ChatFormatting color) {
        ServerPlayer player = onlinePlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(UasTranslations.literal(message).withStyle(color));
        }
    }

    private ChatFormatting toneColor(String tone) {
        return switch (tone == null ? "" : tone) {
            case "SUCCESS" -> ChatFormatting.GREEN;
            case "ERROR" -> ChatFormatting.RED;
            case "WARNING" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.AQUA;
        };
    }

    private Set<UUID> exclusions(UUID... playerIds) {
        Set<UUID> excluded = new HashSet<>();
        if (playerIds != null) {
            for (UUID playerId : playerIds) {
                if (playerId != null) {
                    excluded.add(playerId);
                }
            }
        }
        return excluded;
    }

    private String itemName(AuctionItem item) {
        if (item == null) {
            return "auction item";
        }
        String title = item.getDisplayTitle();
        return title == null || title.isBlank() ? "auction item" : title;
    }

    private String moneyLabel(BigDecimal amount) {
        return UasMoneyFormatter.display(safeMoney(amount));
    }

    static String auctionReference(String eventType, UUID auctionId) {
        String normalized = eventType == null || eventType.isBlank()
                ? "UNKNOWN"
                : eventType.trim().toUpperCase(Locale.ROOT);
        return "UAS_" + normalized + ":" + (auctionId == null ? "unknown" : auctionId);
    }

    private static String moneyReferenceToken(BigDecimal amount) {
        return safeMoney(amount).stripTrailingZeros().toPlainString().replace('.', '_');
    }

    private UasAuctionSnapshot eventSnapshot(AuctionItem item) {
        return UasAuctionSnapshot.fromItem(item);
    }

    private void postAuctionEvent(UasAuctionEvents.AuctionEvent event) {
        if (event != null) {
            NeoForge.EVENT_BUS.post(event);
        }
    }

    private void recordListingStats(AuctionItem item, String sellerName, MinecraftServer server) {
        if (item == null || item.getAuctionId() == null || item.getPlayerId() == null) {
            return;
        }
        try {
            AuctionPlayerStatsSavedData.get(server).recordListing(item.getAuctionId(), item.getPlayerId(), sellerName);
        } catch (RuntimeException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Could not record listing stats for auction {}: {}", item.getAuctionId(), exception.getMessage());
        }
    }

    private void recordSettlementStats(AuctionItem item, BigDecimal grossAmount) {
        if (item == null || item.getAuctionId() == null || item.getPlayerId() == null || item.getHighestBidderId() == null) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        try {
            AuctionPlayerStatsSavedData.get(server).recordSale(
                    item.getAuctionId(),
                    item.getPlayerId(),
                    playerName(item.getPlayerId()),
                    item.getHighestBidderId(),
                    playerName(item.getHighestBidderId()),
                    grossAmount
            );
        } catch (RuntimeException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Could not record settlement stats for auction {}: {}", item.getAuctionId(), exception.getMessage());
        }
    }

    private void postSettlementFailedEvent(AuctionItem item, String reason) {
        postAuctionEvent(new UasAuctionEvents.SettlementFailed(eventSnapshot(item), reason));
    }

    private AuctionFinancialEvent recordBankingEvent(AuctionItem item,
                                                     String eventType,
                                                     BigDecimal amount,
                                                     String reference,
                                                     UasBankingResult result) {
        AuctionFinancialEvent event = AuctionFinancialEvent.fromBanking(
                item == null ? null : item.getAuctionId(),
                eventType,
                amount,
                reference,
                result
        );
        if (item != null) {
            item.recordFinancialEvent(event);
        }
        return event;
    }

    private void recordManualFinancialEvent(AuctionItem item,
                                            String eventType,
                                            BigDecimal amount,
                                            String reference,
                                            boolean success,
                                            String result) {
        if (item == null) {
            return;
        }
        item.recordFinancialEvent(new AuctionFinancialEvent(
                UUID.randomUUID(),
                item.getAuctionId(),
                eventType,
                reference,
                amount,
                success,
                null,
                result,
                LocalDateTime.now()
        ));
    }

    private AuctionFinancialEvent recordItemEvent(AuctionItem item,
                                                  String eventType,
                                                  BigDecimal amount,
                                                  String reference,
                                                  UasItemResult result) {
        AuctionFinancialEvent event = new AuctionFinancialEvent(
                UUID.randomUUID(),
                item == null ? null : item.getAuctionId(),
                eventType,
                reference,
                amount,
                result != null && result.success(),
                null,
                itemResultMessage(result),
                LocalDateTime.now()
        );
        if (item != null) {
            item.recordFinancialEvent(event);
        }
        return event;
    }

    private String itemResultMessage(UasItemResult result) {
        if (result == null) {
            return "UBS returned no item result";
        }
        if (!result.success()) {
            return result.reason();
        }
        return result.referenceId().isBlank() ? "ok" : "ok:" + result.referenceId();
    }

    private void alertOnlineAdmins(String title, String message, String tone, Object... args) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || message == null || message.isBlank()) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Admin alert while no server is available: {}", message);
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && UasPermissions.has(player, UasPermissionAction.ADMIN)) {
                sendAuctionAlert(player.getUUID(), title, message, tone, args);
            }
        }
    }

    public AuctionHouseSnapshot buildSnapshot(ServerPlayer viewer,
                                               AuctionDeliverySavedData deliveryData,
                                               AuctionUiQuery query,
                                               String message,
                                               boolean success) {
        return buildSnapshot(viewer, deliveryData, query, message, success, false);
    }

    public AuctionHouseSnapshot buildSnapshot(ServerPlayer viewer,
                                               AuctionDeliverySavedData deliveryData,
                                               AuctionUiQuery query,
                                               String message,
                                               boolean success,
                                               boolean adminMode) {
        pruneExpiredPendingListings();
        UUID viewerId = viewer == null ? null : viewer.getUUID();
        boolean resolvedAdminMode = adminMode && viewer != null && UasPermissions.has(viewer, UasPermissionAction.ADMIN);
        AuctionUiQuery safeQuery = query == null ? AuctionUiQuery.defaults() : query;
        List<AuctionListingSummary> all = getAuctionItems().values().stream()
                .map(item -> toSummary(item, viewer, resolvedAdminMode))
                .toList();
        List<AuctionListingSummary> browseBase = all.stream()
                .filter(summary -> resolvedAdminMode || summary.state() == AuctionState.ACTIVE)
                .toList();
        List<AuctionModFilterSummary> modFilters = all.stream()
                .filter(summary -> summary.state() == AuctionState.ACTIVE)
                .collect(() -> new HashMap<String, Integer>(), (counts, summary) -> {
                    HashSet<String> listedMods = new HashSet<>();
                    for (ItemStack stack : summary.contents()) {
                        if (isBannedFromAuctions(stack)) {
                            continue;
                        }
                        String modId = itemModId(stack);
                        if (!modId.isBlank() && listedMods.add(modId)) {
                            counts.merge(modId, 1, Integer::sum);
                        }
                    }
                }, HashMap::putAll)
                .entrySet()
                .stream()
                .map(entry -> new AuctionModFilterSummary(entry.getKey(), modDisplayName(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(summary -> summary.displayName().toLowerCase(Locale.ROOT)))
                .toList();
        List<AuctionListingSummary> browse = browseBase.stream()
                .filter(summary -> matchesQuery(summary, safeQuery))
                .sorted(comparatorFor(safeQuery.safeSort()))
                .limit(120)
                .toList();
        List<AuctionListingSummary> myBids = all.stream()
                .filter(summary -> viewerId != null && summary.viewerHasBid())
                .sorted(comparatorFor(AuctionSort.ENDING_SOON))
                .toList();
        List<AuctionListingSummary> myAuctions = all.stream()
                .filter(summary -> viewerId != null && viewerId.equals(summary.sellerId()))
                .sorted(comparatorFor(AuctionSort.ENDING_SOON))
                .toList();
        List<AuctionListingSummary> dashboardListings = buildPersonalDashboard(all, viewerId);

        List<UasAccountSnapshot> accounts = List.of();
        UasAccountSnapshot primaryAccount = null;
        if (viewerId != null) {
            accounts = bankingService.getPlayerAccounts(viewerId);
            primaryAccount = accounts.stream()
                    .filter(UasAccountSnapshot::primary)
                    .findFirst()
                    .orElse(null);
            if (primaryAccount == null && !accounts.isEmpty()) {
                primaryAccount = accounts.getFirst();
            }
        }
        List<AuctionDeliveryEntry> deliveries = viewerId == null || deliveryData == null
                ? List.of()
                : deliveryData.getDeliveries(viewerId);
        List<AuctionSavedSearch> savedSearches = viewer == null || viewer.getServer() == null || viewerId == null
                ? List.of()
                : AuctionSavedSearchSavedData.get(viewer.getServer()).list(viewerId);
        AuctionListingPreview pendingListing = viewerId == null
                ? null
                : getPendingListingPreview(viewerId).orElse(null);
        AuctionAdminDashboardSnapshot adminDashboard = resolvedAdminMode
                ? buildAdminDashboard(all, adminSavedData(viewer), deliveryData)
                : AuctionAdminDashboardSnapshot.empty();
        return new AuctionHouseSnapshot(browse, myBids, myAuctions, dashboardListings, deliveries, modFilters, savedSearches, accounts, primaryAccount, pendingListing, Config.listingFeeRate, message == null ? "" : message, success, resolvedAdminMode, adminDashboard);
    }

    public List<AuctionListingSummary> buildAdminListingSummaries() {
        pruneExpiredPendingListings();
        return getAuctionItems().values().stream()
                .map(item -> toSummary(item, null, true))
                .toList();
    }

    public AuctionAdminDashboardSnapshot buildAdminDashboard(MinecraftServer server,
                                                             AuctionDeliverySavedData deliveryData) {
        return buildAdminDashboard(buildAdminListingSummaries(), adminSavedData(server), deliveryData);
    }

    private List<AuctionListingSummary> buildPersonalDashboard(List<AuctionListingSummary> all, UUID viewerId) {
        if (viewerId == null || all == null || all.isEmpty()) {
            return List.of();
        }
        return all.stream()
                .filter(summary -> personalDashboardMatch(summary, viewerId))
                .sorted(personalDashboardComparator(viewerId))
                .limit(160)
                .toList();
    }

    private boolean personalDashboardMatch(AuctionListingSummary summary, UUID viewerId) {
        if (summary == null || viewerId == null) {
            return false;
        }
        return viewerId.equals(summary.sellerId())
                || viewerId.equals(summary.highestBidderId())
                || summary.viewerHasBid()
                || summary.viewerReceivesNotifications()
                || summary.canClaim();
    }

    private Comparator<AuctionListingSummary> personalDashboardComparator(UUID viewerId) {
        return Comparator
                .comparingInt((AuctionListingSummary summary) -> dashboardPriority(summary, viewerId))
                .thenComparing(AuctionListingSummary::endsAt);
    }

    private int dashboardPriority(AuctionListingSummary summary, UUID viewerId) {
        if (summary.canClaim()) {
            return 0;
        }
        if (summary.state() == AuctionState.ACTIVE && endingSoon(summary)) {
            return 1;
        }
        if (summary.state() == AuctionState.ACTIVE && (summary.viewerHasBid() || viewerId.equals(summary.sellerId()) || summary.viewerReceivesNotifications())) {
            return 2;
        }
        return 3;
    }

    private boolean endingSoon(AuctionListingSummary summary) {
        return summary != null
                && summary.endsAt() != null
                && !summary.endsAt().isBefore(LocalDateTime.now())
                && Duration.between(LocalDateTime.now(), summary.endsAt()).toHours() <= 24L;
    }

    private AuctionAdminSavedData adminSavedData(ServerPlayer viewer) {
        if (viewer == null || viewer.getServer() == null) {
            return null;
        }
        return adminSavedData(viewer.getServer());
    }

    private AuctionAdminSavedData adminSavedData(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        try {
            return AuctionAdminSavedData.get(server);
        } catch (RuntimeException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Admin dashboard saved data unavailable: {}", exception.getMessage());
            return null;
        }
    }

    private AuctionAdminDashboardSnapshot buildAdminDashboard(List<AuctionListingSummary> all,
                                                             AuctionAdminSavedData adminData,
                                                             AuctionDeliverySavedData deliveryData) {
        List<AuctionListingSummary> safeAll = all == null ? List.of() : all;
        List<AuctionAdminDashboardSnapshot.Stats> stats = List.of(
                adminStats("24h", safeAll, LocalDateTime.now().minusHours(24)),
                adminStats("7d", safeAll, LocalDateTime.now().minusDays(7)),
                adminStats("All", safeAll, null)
        );
        List<AuctionEconomyReport> economyReports = buildEconomyReports();
        List<AuctionPlayerBan> bans = adminData == null ? List.of() : adminData.getBans();
        List<AuctionAdminAuditEntry> audit = adminData == null ? List.of() : adminData.getAuditLog().stream().limit(80).toList();
        List<AuctionRecoveryEntry> recoveryEntries = adminData == null ? List.of() : adminData.getRecoveryEntries().stream().limit(80).toList();
        List<AuctionSuspicionSignal> suspicionSignals = buildSuspicionSignals().stream().limit(80).toList();
        List<AuctionAdminDashboardSnapshot.Player> players = adminPlayers(safeAll, adminData, bans, deliveryData);
        List<AuctionAdminDashboardSnapshot.BannedEntry> bannedEntries = adminBannedEntries(safeAll);
        List<AuctionListingSummary> restrictedListings = safeAll.stream()
                .filter(summary -> summary.state() == AuctionState.ACTIVE)
                .filter(summary -> !summary.item().isEmpty())
                .filter(summary -> isBannedFromAuctions(summary.item()))
                .sorted(comparatorFor(AuctionSort.ENDING_SOON))
                .limit(80)
                .toList();
        List<AuctionListingSummary> failedSettlements = safeAll.stream()
                .filter(summary -> summary.state() == AuctionState.FAILED_SETTLEMENT)
                .sorted(Comparator.comparing(AuctionListingSummary::endsAt).reversed())
                .limit(80)
                .toList();
        return new AuctionAdminDashboardSnapshot(stats, economyReports, players, bans, audit, bannedEntries, suspicionSignals, recoveryEntries, restrictedListings, failedSettlements, LocalDateTime.now().toString());
    }

    public List<AuctionEconomyReport> buildEconomyReports() {
        return economyReporter.buildReports(new ArrayList<>(AuctionItems.values()), this::playerName);
    }

    public AuctionEconomyReport buildEconomyReport(String windowToken) {
        return economyReporter.reportForToken(new ArrayList<>(AuctionItems.values()), this::playerName, windowToken);
    }

    private List<AuctionSuspicionSignal> buildSuspicionSignals() {
        AuctionSuspicionAnalyzer.Rules rules = AuctionSuspicionAnalyzer.Rules.fromConfig();
        if (!rules.enabled()) {
            return List.of();
        }
        ArrayList<AuctionSuspicionSignal> signals = new ArrayList<>();
        for (AuctionItem item : AuctionItems.values()) {
            signals.addAll(suspicionAnalyzer.analyze(item, rules, this::playerName));
        }
        signals.addAll(suspicionAnalyzer.repeatedCancellationSignals(AuctionItems.values(), rules, this::playerName));
        return signals.stream()
                .sorted(Comparator.comparing(AuctionSuspicionSignal::observedAt).reversed())
                .toList();
    }

    private void auditSuspiciousBidSignals(AuctionItem item, MinecraftServer server) {
        AuctionSuspicionAnalyzer.Rules rules = AuctionSuspicionAnalyzer.Rules.fromConfig();
        if (item == null || !rules.enabled()) {
            return;
        }
        for (AuctionSuspicionSignal signal : suspicionAnalyzer.analyze(item, rules, this::playerName)) {
            auditSuspicionSignal(signal, server);
        }
    }

    private void auditSellerSelfBidAttempt(AuctionItem item, UUID bidderId, MinecraftServer server) {
        if (!Config.auditSuspiciousBidPatterns || !Config.auditSellerSelfBidSignals) {
            return;
        }
        auditSuspicionSignal(suspicionAnalyzer.sellerSelfBidAttempt(item, bidderId, this::playerName), server);
    }

    private void auditSuspiciousCancellationSignals(UUID sellerId, MinecraftServer server) {
        AuctionSuspicionAnalyzer.Rules rules = AuctionSuspicionAnalyzer.Rules.fromConfig();
        if (sellerId == null || !rules.enabled()) {
            return;
        }
        for (AuctionSuspicionSignal signal : suspicionAnalyzer.repeatedCancellationSignals(AuctionItems.values(), rules, this::playerName)) {
            if (sellerId.equals(signal.primaryPlayerId())) {
                auditSuspicionSignal(signal, server);
            }
        }
    }

    private void auditSuspicionSignal(AuctionSuspicionSignal signal, MinecraftServer server) {
        AuctionAdminSavedData adminData = adminSavedData(server);
        if (adminData == null || signal == null) {
            return;
        }
        if (adminData.addSuspicion(signal)) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Suspicious auction pattern {} target {}: {}", signal.type(), signal.auditTarget(), signal.auditMessage());
        }
    }

    private AuctionAdminDashboardSnapshot.Stats adminStats(String label,
                                                           List<AuctionListingSummary> all,
                                                           LocalDateTime cutoff) {
        BigDecimal bidVolume = BigDecimal.ZERO;
        BigDecimal soldValue = BigDecimal.ZERO;
        BigDecimal estimatedListingFees = BigDecimal.ZERO;
        BigDecimal estimatedSalesTax = BigDecimal.ZERO;
        Set<UUID> activeSellers = new HashSet<>();
        Set<UUID> activeBidders = new HashSet<>();
        int auctionsCreated = 0;
        int activeAuctions = 0;
        int soldAuctions = 0;
        int cancelledAuctions = 0;
        int failedSettlements = 0;

        for (AuctionListingSummary summary : all) {
            if (summary == null) {
                continue;
            }
            boolean createdInWindow = inWindow(summary.createdAt(), cutoff);
            if (createdInWindow) {
                auctionsCreated++;
                estimatedListingFees = estimatedListingFees.add(Config.calculateListingFee(summary.startingBid()));
            }
            if (summary.state() == AuctionState.ACTIVE) {
                activeAuctions++;
                if (summary.sellerId() != null) {
                    activeSellers.add(summary.sellerId());
                }
            }
            if (summary.state() == AuctionState.CANCELLED && createdInWindow) {
                cancelledAuctions++;
            }
            if (summary.state() == AuctionState.FAILED_SETTLEMENT && inWindow(summary.endsAt(), cutoff)) {
                failedSettlements++;
            }
            boolean sold = summary.highestBidderId() != null && summary.state() == AuctionState.CLAIMED;
            if (sold && inWindow(summary.endsAt(), cutoff)) {
                soldAuctions++;
                soldValue = soldValue.add(safeMoney(summary.currentBid()));
                estimatedSalesTax = estimatedSalesTax.add(Config.calculateSalesTax(summary.currentBid()));
            }
            for (AuctionBidRecord record : summary.bidHistory()) {
                if (record != null && record.isAccepted() && inWindow(record.getTimestamp(), cutoff)) {
                    bidVolume = bidVolume.add(record.getAmount());
                    if (record.getBidderId() != null) {
                        activeBidders.add(record.getBidderId());
                    }
                }
            }
        }

        BigDecimal averageSale = soldAuctions <= 0
                ? BigDecimal.ZERO
                : soldValue.divide(BigDecimal.valueOf(soldAuctions), 2, java.math.RoundingMode.HALF_UP);
        return new AuctionAdminDashboardSnapshot.Stats(
                label,
                auctionsCreated,
                activeAuctions,
                soldAuctions,
                cancelledAuctions,
                failedSettlements,
                activeSellers.size(),
                activeBidders.size(),
                moneyLabel(bidVolume),
                moneyLabel(soldValue),
                moneyLabel(estimatedListingFees),
                moneyLabel(estimatedSalesTax),
                moneyLabel(averageSale)
        );
    }

    private List<AuctionAdminDashboardSnapshot.Player> adminPlayers(List<AuctionListingSummary> all,
                                                                    AuctionAdminSavedData adminData,
                                                                    List<AuctionPlayerBan> bans,
                                                                    AuctionDeliverySavedData deliveryData) {
        Map<UUID, AdminPlayerAccumulator> players = new HashMap<>();
        Map<UUID, List<AuctionDeliveryEntry>> deliveriesByPlayer = deliveryData == null ? Map.of() : deliveryData.getAllDeliveries();
        for (AuctionListingSummary summary : all) {
            if (summary == null) {
                continue;
            }
            if (summary.sellerId() != null) {
                AdminPlayerAccumulator seller = players.computeIfAbsent(summary.sellerId(), id -> new AdminPlayerAccumulator(id, summary.sellerName()));
                seller.name = summary.sellerName();
                if (summary.state() == AuctionState.ACTIVE) {
                    seller.activeListings++;
                }
                if (summary.state() == AuctionState.CANCELLED) {
                    seller.cancelledCount++;
                }
                if (summary.highestBidderId() != null && summary.state() == AuctionState.CLAIMED) {
                    seller.soldCount++;
                    seller.soldValue = seller.soldValue.add(safeMoney(summary.currentBid()));
                }
            }
            if (summary.highestBidderId() != null && summary.state() == AuctionState.CLAIMED) {
                AdminPlayerAccumulator buyer = players.computeIfAbsent(summary.highestBidderId(), id -> new AdminPlayerAccumulator(id, playerName(id)));
                buyer.boughtCount++;
            }
            for (AuctionBidRecord record : summary.bidHistory()) {
                if (record != null && record.getBidderId() != null && record.isAccepted()) {
                    String knownName = summary.bidderNames().get(record.getBidderId());
                    AdminPlayerAccumulator bidder = players.computeIfAbsent(record.getBidderId(), id -> new AdminPlayerAccumulator(id, playerName(id)));
                    if (knownName != null && !knownName.isBlank()) {
                        bidder.name = knownName;
                    }
                    bidder.bidCount++;
                    bidder.bidVolume = bidder.bidVolume.add(record.getAmount());
                }
            }
        }
        for (AuctionPlayerBan ban : bans) {
            if (ban != null && ban.playerId() != null) {
                AdminPlayerAccumulator banned = players.computeIfAbsent(ban.playerId(), id -> new AdminPlayerAccumulator(id, ban.playerName()));
                banned.name = ban.playerName();
            }
        }
        for (UUID playerId : deliveriesByPlayer.keySet()) {
            if (playerId != null) {
                players.computeIfAbsent(playerId, id -> new AdminPlayerAccumulator(id, playerName(id)));
            }
        }

        return players.values().stream()
                .sorted(Comparator.comparingInt(AdminPlayerAccumulator::score).reversed().thenComparing(accumulator -> accumulator.name.toLowerCase(Locale.ROOT)))
                .limit(80)
                .map(accumulator -> toAdminPlayer(accumulator, adminData, deliveriesByPlayer.get(accumulator.playerId)))
                .toList();
    }

    private AuctionAdminDashboardSnapshot.Player toAdminPlayer(AdminPlayerAccumulator accumulator,
                                                               AuctionAdminSavedData adminData,
                                                               List<AuctionDeliveryEntry> deliveries) {
        AuctionPlayerBan ban = adminData == null ? null : adminData.getBan(accumulator.playerId).orElse(null);
        boolean active = ban != null && ban.active();
        List<AuctionDeliveryEntry> safeDeliveries = deliveries == null ? List.of() : deliveries;
        return new AuctionAdminDashboardSnapshot.Player(
                accumulator.playerId,
                accumulator.name,
                accumulator.activeListings,
                Config.maxActiveListingsPerPlayer,
                accumulator.bidCount,
                accumulator.soldCount,
                accumulator.boughtCount,
                accumulator.cancelledCount,
                moneyLabel(accumulator.bidVolume),
                moneyLabel(accumulator.soldValue),
                safeDeliveries.size(),
                deliveryPreview(safeDeliveries),
                active && ban.blockCreate(),
                active && ban.blockBid(),
                active && ban.blockBuyout(),
                active && ban.blockWatch(),
                active ? ban.reason() : "",
                active ? ban.expiresAt().map(LocalDateTime::toString).orElse("Never") : "",
                active
        );
    }

    private String deliveryPreview(List<AuctionDeliveryEntry> deliveries) {
        if (deliveries == null || deliveries.isEmpty()) {
            return "";
        }
        return deliveries.stream()
                .limit(3)
                .map(entry -> {
                    String item = deliveryItemLabel(entry);
                    String reason = entry.reason() == null || entry.reason().isBlank() ? "" : entry.reason();
                    return reason.isBlank() ? item : item + " (" + reason + ")";
                })
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
    }

    private String deliveryItemLabel(AuctionDeliveryEntry entry) {
        if (entry == null || entry.items().isEmpty()) {
            return "?";
        }
        ItemStack stack = entry.item();
        if (entry.bundle()) {
            String firstItem = stack.isEmpty() ? "?" : stack.getHoverName().getString();
            return firstItem + " +" + Math.max(0, entry.items().size() - 1);
        }
        if (stack.isEmpty()) {
            return "?";
        }
        return stack.getCount() + "x " + stack.getHoverName().getString();
    }

    private List<AuctionAdminDashboardSnapshot.BannedEntry> adminBannedEntries(List<AuctionListingSummary> all) {
        List<AuctionAdminDashboardSnapshot.BannedEntry> entries = new ArrayList<>();
        for (String raw : Config.bannedAuctionEntries) {
            String entry = Config.normalizeAuctionRestriction(raw);
            if (entry.isBlank()) {
                continue;
            }
            int matching = (int) all.stream()
                    .filter(summary -> summary.state() == AuctionState.ACTIVE)
                    .filter(summary -> bannedEntryMatches(entry, summary.item()))
                    .count();
            entries.add(new AuctionAdminDashboardSnapshot.BannedEntry(entry, Config.auctionRestrictionType(entry), bannedEntryLabel(entry), matching));
        }
        return entries;
    }

    private boolean inWindow(LocalDateTime time, LocalDateTime cutoff) {
        return cutoff == null || (time != null && !time.isBefore(cutoff));
    }

    private String bannedEntryLabel(String entry) {
        if (entry.startsWith("@")) {
            String modId = entry.substring(1);
            return modDisplayName(modId) + " (" + modId + ")";
        }
        return entry;
    }

    public List<AuctionItem> getSellerListings(UUID sellerId, AuctionSellerFilter filter) {
        AuctionSellerFilter safeFilter = filter == null ? AuctionSellerFilter.ALL : filter;
        if (sellerId == null) {
            return List.of();
        }
        return AuctionItems.values().stream()
                .filter(item -> item != null && sellerId.equals(item.getPlayerId()))
                .filter(safeFilter::matches)
                .sorted(Comparator.comparing(AuctionItem::getUpdatedAt).reversed())
                .toList();
    }

    public SellerAuctionStats getSellerStats(UUID sellerId) {
        if (sellerId == null) {
            return new SellerAuctionStats(null, 0, 0, 0, 0, 0, Config.maxActiveListingsPerPlayer);
        }
        int active = 0;
        int sold = 0;
        int cancelled = 0;
        int expired = 0;
        int total = 0;
        for (AuctionItem item : AuctionItems.values()) {
            if (item == null || !sellerId.equals(item.getPlayerId())) {
                continue;
            }
            total++;
            if (AuctionSellerFilter.ACTIVE.matches(item)) {
                active++;
            }
            if (AuctionSellerFilter.SOLD.matches(item)) {
                sold++;
            }
            if (AuctionSellerFilter.CANCELLED.matches(item)) {
                cancelled++;
            }
            if (AuctionSellerFilter.EXPIRED.matches(item)) {
                expired++;
            }
        }
        return new SellerAuctionStats(sellerId, active, sold, cancelled, expired, total, Config.maxActiveListingsPerPlayer);
    }

    public void removeAuctionItem(AuctionItem item) {
        if (mutationsBlocked) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Auction removal blocked because storage migration failed.");
            return;
        }
        this.AuctionItems.remove(item.getAuctionId());
        markChanged("Auction storage marked dirty after listing removal.");
    }

    public AuctionItem getAuctionItem(UUID id) {
        return id == null ? null : this.AuctionItems.get(id);
    }

    public List<AuctionSuspicionSignal> getSuspicionSignals(UUID auctionId) {
        AuctionItem item = getAuctionItem(auctionId);
        if (item == null) {
            return List.of();
        }
        return suspicionAnalyzer.analyze(item, AuctionSuspicionAnalyzer.Rules.fromConfig(), this::playerName);
    }

    public ConcurrentHashMap<UUID, AuctionItem> getAuctionItems() {
        return new ConcurrentHashMap<>(this.AuctionItems);
    }

    void addEscrowedAuctionForTesting(AuctionItem item) {
        if (item != null) {
            attachMutationTracking(item);
            this.AuctionItems.put(item.getAuctionId(), item);
        }
    }

    public AuctionStorageHealth getStorageHealth() {
        return storageHealth;
    }

    public void markStorageSaved(String message) {
        this.storageHealth = AuctionStorageHealth.saved(message);
    }

    public void markStorageLoaded(String message) {
        this.storageHealth = AuctionStorageHealth.loaded(message);
    }

    public void markStorageFailed(String message) {
        this.storageHealth = AuctionStorageHealth.failed(this.storageHealth, message);
    }

    public boolean placeBid(UUID auctionId, UUID bidderId, BigDecimal amount) {
        return placeBidWithEscrow(bidderId, auctionId, amount).success();
    }

    public boolean saveNow(MinecraftServer server, String reason) {
        if (mutationsBlocked) {
            markStorageFailed("Auction storage save blocked because migration failed: " + savedData.getMigrationMessage());
            return false;
        }
        if (savedData == null) {
            markStorageFailed("Auction storage save skipped because persistent SavedData is unavailable.");
            return false;
        }
        if (server == null) {
            markStorageFailed("Auction storage save failed because Minecraft server is unavailable.");
            return false;
        }

        refreshExpiredStates();
        savedData.markChanged();

        try {
            boolean saved = server.saveEverything(false, true, true);
            markStorageSaved(reason + " Saved " + AuctionItems.size() + " auction(s).");
            return saved;
        } catch (RuntimeException exception) {
            String message = "Auction storage save failed: " + exception.getMessage();
            markStorageFailed(message);
            UltimateAuctionSystem.LOGGER.error("[UAS] {}", message, exception);
            return false;
        }
    }

    public boolean autosave(MinecraftServer server) {
        if (mutationsBlocked) {
            markStorageFailed("Auction autosave blocked because migration failed: " + savedData.getMigrationMessage());
            return false;
        }
        if (savedData == null) {
            markStorageFailed("Auction autosave skipped because persistent SavedData is unavailable.");
            return false;
        }
        if (server == null) {
            markStorageFailed("Auction autosave failed because Minecraft server is unavailable.");
            return false;
        }

        refreshExpiredStates();
        savedData.markChanged();

        try {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld == null) {
                markStorageFailed("Auction autosave failed because overworld data storage is unavailable.");
                return false;
            }
            overworld.getDataStorage().save();
            markStorageSaved("Autosave queued for " + AuctionItems.size() + " auction(s).");
            return true;
        } catch (RuntimeException exception) {
            String message = "Auction autosave failed: " + exception.getMessage();
            markStorageFailed(message);
            UltimateAuctionSystem.LOGGER.error("[UAS] {}", message, exception);
            return false;
        }
    }

    private void markChanged(String message) {
        if (savedData != null) {
            savedData.markChanged();
            this.storageHealth = AuctionStorageHealth.dirty(this.storageHealth, message);
        }
    }

    private void attachMutationTracking(AuctionItem item) {
        if (item != null) {
            item.setChangeListener(() -> markChanged("Auction storage marked dirty after auction record mutation."));
        }
    }

    private void sendListingError(ServerPlayer player, MutableComponent message) {
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    private void sendAuctionCreatedMessage(ServerPlayer player, AuctionItem item) {
        if (player == null || item == null) {
            return;
        }
        MutableComponent message = Component.empty()
                .append(UasTranslations.literal("Auction created: ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(item.getAuctionId().toString()).withStyle(ChatFormatting.AQUA))
                .append(UasTranslations.literal(" "))
                .append(UasTranslations.literal("[Open /ah]").withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ah"))))
                .append(UasTranslations.literal(" "))
                .append(UasTranslations.literal("[My Auctions]").withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ah mine active"))));
        player.sendSystemMessage(message, false);
    }

    private String pendingPreviewMessage(PendingAuctionListing pending) {
        AuctionListingPreview preview = pending.toPreview();
        String buyout = preview.buyoutPrice().compareTo(BigDecimal.ZERO) > 0
                ? moneyLabel(preview.buyoutPrice())
                : "none";
        String reserve = preview.reservePrice().compareTo(BigDecimal.ZERO) > 0
                ? moneyLabel(preview.reservePrice())
                : "none";
        return "Pending auction created. Confirm within "
                + Config.pendingListingConfirmationSeconds
                + "s: "
                + preview.itemCount()
                + "x "
                + preview.itemName()
                + ", start "
                + moneyLabel(preview.startingBid())
                + ", buyout "
                + buyout
                + ", reserve "
                + reserve
                + ", duration "
                + preview.durationHours()
                + "h"
                + ", fee "
                + moneyLabel(preview.listingFee())
                + ".";
    }

    private MutableComponent listingError(String message) {
        return UasTranslations.literal(message).withStyle(ChatFormatting.RED);
    }

    private String accountSetupMessage(UUID playerId) {
        if (playerId != null && bankingService.playerHasFrozenAccount(playerId)) {
            return "Your UBS account is frozen. Open UBS or ask an admin to resolve the frozen account before creating auctions.";
        }
        if (playerId != null && bankingService.playerHasAnyAccount(playerId)) {
            return "UAS could not find a usable UBS primary account. Open UBS and select a primary account, then try again.";
        }
        return "UAS could not find your UBS primary account. Open UBS and create or select a usable primary account, then try again.";
    }

    private AccountSelection resolvePlayerAccount(UUID playerId, UUID requestedAccountId) {
        if (playerId == null) {
            return AccountSelection.fail("Only players can choose UBS accounts.");
        }
        UUID accountId = requestedAccountId;
        if (accountId == null) {
            Optional<UUID> primary = bankingService.getPrimaryAccountId(playerId);
            if (primary.isEmpty()) {
                return AccountSelection.fail(accountSetupMessage(playerId));
            }
            accountId = primary.get();
        }
        if (!bankingService.playerOwnsAccount(playerId, accountId)) {
            return AccountSelection.fail("Selected UBS account does not belong to you. Refresh UAS and choose one of your accounts.");
        }
        Optional<UasAccountSnapshot> snapshot = bankingService.getAccountSnapshot(accountId);
        if (snapshot.isEmpty()) {
            return AccountSelection.fail("Selected UBS account snapshot is unavailable. Refresh UAS or choose another account.");
        }
        if (!playerId.equals(snapshot.get().playerId())) {
            return AccountSelection.fail("Selected UBS account does not belong to you. Refresh UAS and choose one of your accounts.");
        }
        return AccountSelection.ok(snapshot.get());
    }

    private AccountSelection validateSellerAccount(UUID playerId, UUID requestedAccountId, BigDecimal startingBid) {
        AccountSelection selection = resolvePlayerAccount(playerId, requestedAccountId);
        if (!selection.success()) {
            return selection;
        }
        UasAccountSnapshot snapshot = selection.snapshot();
        if (snapshot.frozen()) {
            String reason = snapshot.frozenReason().isBlank() ? "no reason provided" : snapshot.frozenReason();
            return AccountSelection.fail("Your selected UBS account is frozen and cannot list auctions right now: " + reason);
        }
        UasBankingResult canReceive = bankingService.validateCanReceive(selection.accountId());
        if (!canReceive.success()) {
            return AccountSelection.fail("Your selected UBS account cannot receive auction payouts right now: " + canReceive.reason());
        }
        BigDecimal listingFee = Config.calculateListingFee(startingBid);
        if (listingFee.compareTo(BigDecimal.ZERO) > 0) {
            UasBankingResult canPayFee = bankingService.validateCanSend(selection.accountId(), listingFee);
            if (!canPayFee.success()) {
                return AccountSelection.fail(feeFailureMessage("listing fee", listingFee, canPayFee));
            }
        }
        return selection;
    }

    private AccountSelection resolveBidderAccount(UUID playerId, UUID requestedAccountId) {
        AccountSelection selection = resolvePlayerAccount(playerId, requestedAccountId);
        if (!selection.success()) {
            return selection;
        }
        UasAccountSnapshot snapshot = selection.snapshot();
        if (snapshot.frozen()) {
            String reason = snapshot.frozenReason().isBlank() ? "no reason provided" : snapshot.frozenReason();
            return AccountSelection.fail("Your selected UBS account is frozen and cannot pay auctions right now: " + reason);
        }
        return selection;
    }

    private String feeFailureMessage(String feeLabel, BigDecimal required, UasBankingResult result) {
        String current = result == null ? "unknown" : moneyLabel(result.balanceAfter());
        String reason = result == null ? "UBS returned no result" : result.reason();
        return "Your selected UBS account cannot pay the " + feeLabel
                + ". Required: " + moneyLabel(required)
                + ", available: " + current
                + ". UBS says: " + reason;
    }

    private boolean takeEscrowFromSeller(ServerPlayer player, ItemStack escrowStack) {
        ItemStack mainHand = player.getMainHandItem();
        if (escrowStack.isEmpty()
                || mainHand.isEmpty()
                || mainHand.getCount() < escrowStack.getCount()
                || !ItemStack.isSameItemSameComponents(mainHand, escrowStack)) {
            return false;
        }

        mainHand.shrink(escrowStack.getCount());
        player.getInventory().setChanged();
        return true;
    }

    private boolean takePendingEscrowFromSeller(ServerPlayer player, PendingAuctionListing pending) {
        if (player == null || pending == null || !pending.stillMatches(player)) {
            return false;
        }
        if (pending.isMainHand()) {
            return takeEscrowFromSeller(player, pending.itemSnapshot());
        }
        for (int slot : pending.slots()) {
            if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
                return false;
            }
        }
        for (int index = 0; index < pending.slots().size(); index++) {
            int slot = pending.slots().get(index);
            ItemStack current = player.getInventory().getItem(slot);
            int escrowCount = pending.itemSnapshots().get(index).getCount();
            if (current.getCount() <= escrowCount) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            } else {
                current.shrink(escrowCount);
            }
        }
        player.getInventory().setChanged();
        return true;
    }

    private void restoreEscrowToSeller(ServerPlayer player, ItemStack escrowStack) {
        if (player != null && escrowStack != null && !escrowStack.isEmpty()) {
            player.getInventory().add(escrowStack.copy());
            player.getInventory().setChanged();
        }
    }

    private void restoreEscrowToSeller(ServerPlayer player, List<ItemStack> escrowStacks) {
        if (player == null || escrowStacks == null || escrowStacks.isEmpty()) {
            return;
        }
        for (ItemStack stack : escrowStacks) {
            if (stack != null && !stack.isEmpty()) {
                player.getInventory().add(stack.copy());
            }
        }
        player.getInventory().setChanged();
    }

    private void refreshExpiredStates() {
        for (AuctionItem item : AuctionItems.values()) {
            if (item != null && item.getState() == AuctionState.ACTIVE && item.isExpired()) {
                item.transitionTo(AuctionState.ENDED, "auction expired during storage save");
            }
        }
    }

    public int notifyEndingSoonWatchlists() {
        if (mutationsBlocked || Config.watchEndingSoonThresholdMinutes <= 0) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int notified = 0;
        for (AuctionItem item : AuctionItems.values()) {
            if (item == null
                    || item.getState() != AuctionState.ACTIVE
                    || item.isEndingSoonNotificationSent()
                    || item.getNotificationSubscribers().isEmpty()
                    || item.getDateOfEnd() == null) {
                continue;
            }
            Duration remaining = Duration.between(now, item.getDateOfEnd());
            if (remaining.isNegative() || remaining.isZero() || remaining.toMinutes() > Config.watchEndingSoonThresholdMinutes) {
                continue;
            }
            alertSubscribers(
                    item,
                    Set.of(),
                    "Auction Ending Soon",
                    "Auction {0}: {1} ends within {2} minute(s).",
                    "WARNING",
                    item.getAuctionId(),
                    itemName(item),
                    Config.watchEndingSoonThresholdMinutes
            );
            if (item.markEndingSoonNotificationSent()) {
                markChanged("Auction storage marked dirty after ending-soon watch notification.");
                notified++;
            }
        }
        return notified;
    }

    public void payoutAuctionItem(UUID id) {
        if (mutationsBlocked) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Settlement blocked because auction storage migration failed.");
            return;
        }
        AuctionItem item = getAuctionItem(id);
        if (item == null || !item.isExpired()) {
            return;
        }
        if (item.getState() == AuctionState.ACTIVE) {
            item.transitionTo(AuctionState.ENDED, "auction expired before settlement");
        }
        if (item.getState() != AuctionState.ENDED && item.getState() != AuctionState.FAILED_SETTLEMENT) {
            UltimateAuctionSystem.LOGGER.warn("Auction {} is in state {}; settlement skipped.", id, item.getState());
            return;
        }

        if (item.isSealedBid()) {
            item.selectWinningSealedBid();
        }
        UUID winningBidderId = item.getHighestBidderId();
        if (winningBidderId == null) {
            UltimateAuctionSystem.LOGGER.info("Auction {} expired without bids; no UBS payout was created.", id);
            item.transitionTo(AuctionState.ENDED, "auction ended without bids");
            Object[] args = {item.getAuctionId(), itemName(item)};
            sendAuctionAlert(item.getPlayerId(), "Auction Ended", "Auction {0}: {1} ended without a buyer.", "INFO", args);
            sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} ended without a buyer.", ChatFormatting.AQUA, args, openAhAction(), myAuctionsAction());
            notifyAuctionEndedUnsold(item, item.getPlayerId());
            return;
        }
        if (!item.isReserveMet()) {
            AuctionActionResult reserveRefund = refundBelowReserveBid(item, "auction expired below reserve");
            if (!reserveRefund.success()) {
                UltimateAuctionSystem.LOGGER.warn("Auction {} reserve refund failed: {}", id, reserveRefund.message());
            }
            return;
        }

        if (!bankingService.isAvailable()) {
            UltimateAuctionSystem.LOGGER.warn("UBS is not available; cannot settle auction {}.", id);
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "UBS unavailable during settlement");
            postSettlementFailedEvent(item, "UBS unavailable during settlement");
            Object[] args = {item.getAuctionId(), itemName(item)};
            sendAuctionAlert(item.getPlayerId(), "Auction Settlement Delayed", "Auction {0}: {1} sold but UBS is unavailable for payout.", "WARNING", args);
            sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} sold but UBS is unavailable for payout.", ChatFormatting.YELLOW, args, openAhAction(), myAuctionsAction());
            sendAuctionAlert(winningBidderId, "Auction Settlement Delayed", "Auction {0}: {1} is waiting for payment settlement.", "WARNING", args);
            sendAuctionChatMessage(winningBidderId, "Auction {0}: {1} is waiting for payment settlement.", ChatFormatting.YELLOW, args, openAhAction());
            return;
        }

        AuctionActionResult sealedRefunds = refundLosingSealedBids(item, "sealed loser refunds before seller payout");
        if (!sealedRefunds.success()) {
            UltimateAuctionSystem.LOGGER.warn("Auction {} sealed loser refunds failed: {}", id, sealedRefunds.message());
            return;
        }

        UUID sellerAccountId = item.getSellerAccountId();
        if (sellerAccountId == null) {
            UltimateAuctionSystem.LOGGER.warn("Auction {} has no stored seller account ID; cannot settle seller {}.", id, item.getPlayerId());
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "missing seller account during settlement");
            postSettlementFailedEvent(item, "Missing seller account during settlement");
            Object[] args = {item.getAuctionId(), itemName(item)};
            sendAuctionAlert(item.getPlayerId(), "Auction Settlement Failed", "Auction {0}: {1} has no seller account for payout.", "ERROR", args);
            sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} has no seller account for payout.", ChatFormatting.RED, args, openAhAction(), myAuctionsAction());
            return;
        }

        Optional<AuctionBidRecord> winningBidRecord = item.getWinningBidRecord();
        UUID winningBidderAccountId = winningBidRecord.flatMap(AuctionBidRecord::getBidderAccountId).orElse(null);
        if (winningBidderAccountId == null) {
            UltimateAuctionSystem.LOGGER.warn("Auction {} has no auditable winning bid account; cannot settle.", id);
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "missing winning bidder account during settlement");
            postSettlementFailedEvent(item, "Missing winning bidder account during settlement");
            Object[] args = {item.getAuctionId(), itemName(item)};
            sendAuctionAlert(winningBidderId, "Auction Settlement Failed", "Auction {0}: {1} is missing your winning bid account.", "ERROR", args);
            sendAuctionChatMessage(winningBidderId, "Auction {0}: {1} is missing your winning bid account.", ChatFormatting.RED, args, openAhAction());
            return;
        }

        SettlementResult settlement = settleHeldBid(item);
        if (!settlement.success()) {
            UltimateAuctionSystem.LOGGER.warn("UBS auction settlement failed for {}: {}", id, settlement.message());
            Object[] sellerArgs = {item.getAuctionId(), itemName(item), settlement.message()};
            Object[] winnerArgs = {item.getAuctionId(), itemName(item)};
            sendAuctionAlert(item.getPlayerId(), "Auction Settlement Failed", "Auction {0}: {1} could not pay out: {2}", "ERROR", sellerArgs);
            sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} could not pay out: {2}", ChatFormatting.RED, sellerArgs, openAhAction(), myAuctionsAction());
            sendAuctionAlert(winningBidderId, "Auction Settlement Failed", "Auction {0}: {1} could not finish payment settlement.", "ERROR", winnerArgs);
            sendAuctionChatMessage(winningBidderId, "Auction {0}: {1} could not finish payment settlement.", ChatFormatting.RED, winnerArgs, openAhAction());
            return;
        }

        item.transitionTo(AuctionState.ENDED, "settlement payout completed; waiting for winner claim");
        markChanged("Auction storage marked dirty after automatic seller payout.");
        notifyAuctionSold(item, winningBidderId, item.getHighestBid());
    }

    public int settleExpiredAuctions() {
        return settleExpiredAuctions(AUTO_SETTLEMENT_LIMIT_PER_SCAN);
    }

    public int settleExpiredAuctions(int limit) {
        if (!Config.autoSettleExpiredAuctions || mutationsBlocked || limit <= 0) {
            return 0;
        }
        int processed = 0;
        for (AuctionItem item : AuctionItems.values()) {
            if (item == null || processed >= limit) {
                continue;
            }
            boolean shouldSettle = item.isExpired()
                    && (item.getState() == AuctionState.ACTIVE
                    || (item.getState() == AuctionState.ENDED
                    && item.getHighestBidderId() != null
                    && !item.hasSuccessfulFinancialEvent(EVENT_AUCTION_PAYOUT)));
            if (shouldSettle) {
                payoutAuctionItem(item.getAuctionId());
                processed++;
            }
        }
        return processed;
    }
    
    @SubscribeEvent
    public static void onServerTick (ServerTickEvent.Post event) {

    }

    private AuctionActionResult activateAuction(UUID auctionId,
                                                ServerPlayer player,
                                                ItemStack escrowStack,
                                                String description,
                                                LocalDateTime end,
                                                LocalDateTime start,
                                                 BigDecimal startingBidPrice,
                                                 BigDecimal buyoutPrice,
                                                 BigDecimal reservePrice,
                                                 AuctionFormat format,
                                                 UUID sellerAccountId,
                                                 String escrowSource) {
        return activateAuction(auctionId, player, List.of(escrowStack), "", description, end, start, startingBidPrice, buyoutPrice, reservePrice, format, sellerAccountId, escrowSource);
    }

    private AuctionActionResult activateAuction(UUID auctionId,
                                                ServerPlayer player,
                                                List<ItemStack> escrowStacks,
                                                String title,
                                                String description,
                                                LocalDateTime end,
                                                LocalDateTime start,
                                                 BigDecimal startingBidPrice,
                                                 BigDecimal buyoutPrice,
                                                 BigDecimal reservePrice,
                                                 AuctionFormat format,
                                                 UUID sellerAccountId,
                                                 String escrowSource) {
        List<ItemStack> safeStacks = safeItemStacks(escrowStacks);
        AuctionItem item = new AuctionItem(auctionId, safeStacks, title, description, end, start, startingBidPrice, player.getUUID(), sellerAccountId, buyoutPrice, reservePrice, format);
        item.markEscrowed(escrowSource);
        Optional<String> activationError = item.validateForActivation();
        if (activationError.isPresent()) {
            restoreEscrowToSeller(player, safeStacks);
            refundListingFee(sellerAccountId, startingBidPrice, auctionReference(EVENT_LISTING_FEE_REFUND, item.getAuctionId()));
            return AuctionActionResult.fail("Auction escrow failed validation: " + activationError.get() + ".");
        }
        attachMutationTracking(item);
        AuctionItems.put(item.getAuctionId(), item);
        markChanged("Auction storage marked dirty after listing creation.");
        recordListingStats(item, player.getGameProfile().getName(), player.getServer());
        postAuctionEvent(new UasAuctionEvents.ListingCreated(eventSnapshot(item), player.getUUID()));
        sendAuctionCreatedMessage(player, item);
        return AuctionActionResult.ok("Auction created: " + item.getAuctionId(), item.getAuctionId());
    }

    private AuctionActionResult validateListingRequest(ServerPlayer player,
                                                       ItemStack stack,
                                                       BigDecimal startingBidPrice,
                                                       BigDecimal buyoutPrice,
                                                       LocalDateTime end) {
        return validateListingRequest(player, stack == null || stack.isEmpty() ? List.of() : List.of(stack), startingBidPrice, buyoutPrice, BigDecimal.ZERO, AuctionFormat.NORMAL, end, null);
    }

    private AuctionActionResult validateListingRequest(ServerPlayer player,
                                                       List<ItemStack> stacks,
                                                       BigDecimal startingBidPrice,
                                                       BigDecimal buyoutPrice,
                                                       LocalDateTime end) {
        return validateListingRequest(player, stacks, startingBidPrice, buyoutPrice, BigDecimal.ZERO, AuctionFormat.NORMAL, end, null);
    }

    private AuctionActionResult validateListingRequest(ServerPlayer player,
                                                       List<ItemStack> stacks,
                                                       BigDecimal startingBidPrice,
                                                       BigDecimal buyoutPrice,
                                                       BigDecimal reservePrice,
                                                       AuctionFormat format,
                                                       LocalDateTime end,
                                                       UUID sellerAccountId) {
        if (mutationsBlocked) {
            return AuctionActionResult.fail("Auction storage has a migration problem. New listings are blocked until an admin fixes the saved data.");
        }
        List<ItemStack> safeStacks = safeItemStacks(stacks);
        if (safeStacks.isEmpty()) {
            return AuctionActionResult.fail("Select an item to auction.");
        }
        if (safeStacks.size() > AuctionItem.MAX_BUNDLE_CONTENTS) {
            return AuctionActionResult.fail("A bundled auction can include up to " + AuctionItem.MAX_BUNDLE_CONTENTS + " item stacks.");
        }
        if (AuctionAdminSavedData.isBlocked(player.getServer(), player.getUUID(), AuctionBanAction.CREATE)) {
            return auctionBanFailure(AuctionBanAction.CREATE);
        }
        BigDecimal startingBid = safeMoney(startingBidPrice);
        if (startingBid.compareTo(BigDecimal.ZERO) < 0) {
            return AuctionActionResult.fail("Starting bid must be zero or higher.");
        }
        if (hasFractionalDollars(startingBid)) {
            return AuctionActionResult.fail("Prices must use whole dollars.");
        }
        BigDecimal normalizedBuyout = buyoutPrice == null ? BigDecimal.ZERO : buyoutPrice;
        if (hasFractionalDollars(normalizedBuyout)) {
            return AuctionActionResult.fail("Prices must use whole dollars.");
        }
        BigDecimal normalizedReserve = reservePrice == null ? BigDecimal.ZERO : reservePrice;
        AuctionFormat safeFormat = format == null ? AuctionFormat.NORMAL : format;
        if (hasFractionalDollars(normalizedReserve)) {
            return AuctionActionResult.fail("Prices must use whole dollars.");
        }
        if (safeFormat == AuctionFormat.SEALED_BID && !Config.sealedBidAuctionsEnabled) {
            return AuctionActionResult.fail("Sealed-bid auctions are disabled on this server.");
        }
        if (normalizedReserve.compareTo(BigDecimal.ZERO) > 0 && !Config.reservePricesEnabled) {
            return AuctionActionResult.fail("Reserve-price auctions are disabled on this server.");
        }
        if (normalizedReserve.compareTo(BigDecimal.ZERO) > 0 && normalizedReserve.compareTo(startingBid) < 0) {
            return AuctionActionResult.fail("Reserve price must be at least the starting bid.");
        }
        if (normalizedBuyout.compareTo(BigDecimal.ZERO) > 0 && normalizedBuyout.compareTo(startingBid) < 0) {
            return AuctionActionResult.fail("Buyout price must be at least the starting bid.");
        }
        if (normalizedBuyout.compareTo(BigDecimal.ZERO) > 0 && normalizedReserve.compareTo(BigDecimal.ZERO) > 0 && normalizedBuyout.compareTo(normalizedReserve) < 0) {
            return AuctionActionResult.fail("Buyout price must be at least the reserve price.");
        }
        if (end == null || !end.isAfter(LocalDateTime.now())) {
            return AuctionActionResult.fail("Auction duration must be in the future.");
        }
        Duration requestedDuration = Duration.between(LocalDateTime.now(), end).plusSeconds(1);
        if (requestedDuration.compareTo(Config.minimumAuctionDuration()) < 0) {
            return AuctionActionResult.fail("Auction duration must be at least " + Config.minAuctionDurationMinutes + " minutes.");
        }
        if (requestedDuration.compareTo(Duration.ofHours(Config.maxAuctionDurationHours)) > 0) {
            return AuctionActionResult.fail("Auction duration cannot be longer than " + Config.maxAuctionDurationHours + " hours.");
        }
        long activeListings = AuctionItems.values().stream()
                .filter(item -> item != null && item.getState() == AuctionState.ACTIVE)
                .filter(item -> player.getUUID().equals(item.getPlayerId()))
                .count();
        if (activeListings >= Config.maxActiveListingsPerPlayer) {
            return AuctionActionResult.fail("You already have the maximum number of active listings.");
        }
        for (ItemStack stack : safeStacks) {
            if (isBannedFromAuctions(stack)) {
                return AuctionActionResult.fail("One selected item is restricted and cannot be auctioned.");
            }
        }
        AccountSelection account = validateSellerAccount(player.getUUID(), sellerAccountId, startingBid);
        if (!account.success()) {
            return AuctionActionResult.fail(account.message());
        }
        return AuctionActionResult.ok("");
    }

    private UasBankingResult chargeListingFee(UUID sellerAccountId, BigDecimal startingBidPrice, String reference) {
        BigDecimal listingFee = Config.calculateListingFee(startingBidPrice);
        if (listingFee.compareTo(BigDecimal.ZERO) <= 0) {
            return UasBankingResult.ok(BigDecimal.ZERO, null, reference);
        }
        return bankingService.withdraw(sellerAccountId, listingFee, reference);
    }

    private AuctionActionResult chargeCancellationFee(ServerPlayer seller, AuctionItem item) {
        BigDecimal cancellationFee = Config.calculateCancellationFee(item == null ? BigDecimal.ZERO : item.getStartingBidPrice());
        if (cancellationFee.compareTo(BigDecimal.ZERO) <= 0) {
            return AuctionActionResult.ok("");
        }
        if (item == null || item.getSellerAccountId() == null) {
            return AuctionActionResult.fail("Your UBS primary account could not be found for the cancellation fee.");
        }
        String reference = auctionReference(EVENT_CANCELLATION_FEE, item.getAuctionId());
        UasBankingResult result = bankingService.withdraw(item.getSellerAccountId(), cancellationFee, reference);
        recordBankingEvent(item, EVENT_CANCELLATION_FEE, cancellationFee, reference, result);
        return result.success()
                ? AuctionActionResult.ok("")
                : AuctionActionResult.fail(feeFailureMessage("cancellation fee", cancellationFee, result));
    }

    private void refundListingFee(UUID sellerAccountId, BigDecimal startingBidPrice, String reference) {
        BigDecimal listingFee = Config.calculateListingFee(startingBidPrice);
        if (sellerAccountId != null && listingFee.compareTo(BigDecimal.ZERO) > 0) {
            bankingService.deposit(sellerAccountId, listingFee, reference);
        }
    }

    private BigDecimal minimumAcceptedBid(AuctionItem item) {
        if (item.getHighestBidderId() == null) {
            return item.getStartingBidPrice();
        }
        return item.getHighestBid().add(Config.minimumBidIncrementAmount());
    }

    private AuctionActionResult auctionBanFailure(AuctionBanAction action) {
        return AuctionActionResult.fail("An admin has blocked your auction-house " + auctionBanLabel(action) + " access.");
    }

    private String auctionBanLabel(AuctionBanAction action) {
        return switch (action) {
            case CREATE -> "listing";
            case BID -> "bidding";
            case BUYOUT -> "buyout";
            case WATCH -> "notification";
        };
    }

    private AuctionActionResult refundBelowReserveBid(AuctionItem item, String reason) {
        if (item == null) {
            return AuctionActionResult.fail("Auction not found.");
        }
        if (item.isSealedBid()) {
            item.selectWinningSealedBid();
        }
        if (item.isReserveMet() || item.getHighestBidderId() == null) {
            return AuctionActionResult.ok("");
        }
        if (item.isSealedBid()) {
            Set<UUID> bidders = new HashSet<>(item.getBids().keySet());
            AuctionActionResult refund = refundSealedBidsExcept(item, Set.of(), EVENT_RESERVE_REFUND, "sealed reserve refund");
            if (!refund.success()) {
                return refund;
            }
            item.clearWinningBidAfterReserveRefund();
            item.transitionTo(AuctionState.ENDED, reason == null || reason.isBlank() ? "sealed reserve price not met; bids refunded" : reason);
            markChanged("Auction storage marked dirty after sealed reserve refund.");
            notifySealedReserveNotMet(item, bidders);
            return AuctionActionResult.ok("Reserve price was not met. Sealed bids refunded and auction ended without a buyer.");
        }
        UUID bidderId = item.getHighestBidderId();
        BigDecimal amount = safeMoney(item.getHighestBid());
        UUID bidderAccountId = item.getWinningBidRecord()
                .flatMap(AuctionBidRecord::getBidderAccountId)
                .orElse(null);
        if (bidderAccountId == null) {
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "missing bidder account during reserve refund");
            postSettlementFailedEvent(item, "Missing bidder account during reserve refund");
            alertOnlineAdmins("Auction Settlement Failed", "Auction {0} is missing a bidder account for reserve refund.", "ERROR", item.getAuctionId());
            return AuctionActionResult.fail("Auction settlement failed: missing bidder account for reserve refund.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            item.clearWinningBidAfterReserveRefund();
            item.transitionTo(AuctionState.ENDED, "reserve price not met; no positive bid to refund");
            markChanged("Auction storage marked dirty after below-reserve auction ended.");
            return AuctionActionResult.ok("Reserve price was not met. Auction ended without a buyer.");
        }

        String refundReference = auctionReference(EVENT_RESERVE_REFUND, item.getAuctionId());
        UasBankingResult refund = bankingService.deposit(bidderAccountId, amount, refundReference);
        recordBankingEvent(item, EVENT_RESERVE_REFUND, amount, refundReference, refund);
        if (!refund.success()) {
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "reserve refund failed: " + refund.reason());
            postSettlementFailedEvent(item, "Reserve refund failed: " + refund.reason());
            Object[] sellerArgs = {item.getAuctionId(), itemName(item), moneyLabel(amount), refund.reason()};
            Object[] bidderArgs = {item.getAuctionId(), itemName(item), moneyLabel(amount), refund.reason()};
            sendAuctionAlert(item.getPlayerId(), "Auction Settlement Failed", "Auction {0}: {1} ended below reserve, but refund {2} failed: {3}", "ERROR", sellerArgs);
            sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} ended below reserve, but refund {2} failed: {3}", ChatFormatting.RED, sellerArgs, openAhAction(), myAuctionsAction());
            sendAuctionAlert(bidderId, "Auction Settlement Failed", "Auction {0}: Your bid of {2} on {1} could not be refunded yet: {3}", "ERROR", bidderArgs);
            sendAuctionChatMessage(bidderId, "Auction {0}: Your bid of {2} on {1} could not be refunded yet: {3}", ChatFormatting.RED, bidderArgs, openAhAction());
            alertOnlineAdmins("Auction Settlement Failed", "Auction {0} reserve refund failed: {1}", "ERROR", item.getAuctionId(), refund.reason());
            return AuctionActionResult.fail("Auction reserve refund failed: " + refund.reason());
        }

        item.clearWinningBidAfterReserveRefund();
        item.transitionTo(AuctionState.ENDED, reason == null || reason.isBlank() ? "reserve price not met; bid refunded" : reason);
        markChanged("Auction storage marked dirty after reserve refund.");
        notifyReserveNotMet(item, bidderId, amount);
        return AuctionActionResult.ok("Reserve price was not met. Highest bid refunded and auction ended without a buyer.");
    }

    private AuctionActionResult refundLosingSealedBids(AuctionItem item, String reason) {
        if (item == null || !item.isSealedBid()) {
            return AuctionActionResult.ok("");
        }
        item.selectWinningSealedBid();
        UUID winnerId = item.getHighestBidderId();
        if (winnerId == null) {
            return AuctionActionResult.ok("");
        }
        return refundSealedBidsExcept(item, Set.of(winnerId), EVENT_SEALED_BID_REFUND, reason);
    }

    private AuctionActionResult refundSealedBidsExcept(AuctionItem item, Set<UUID> excludedBidders, String eventType, String reason) {
        if (item == null) {
            return AuctionActionResult.fail("Auction not found.");
        }
        Set<UUID> excluded = excludedBidders == null ? Set.of() : excludedBidders;
        String safeEventType = eventType == null || eventType.isBlank() ? EVENT_SEALED_BID_REFUND : eventType;
        for (Map.Entry<UUID, BigDecimal> entry : item.getBids().entrySet()) {
            UUID bidderId = entry.getKey();
            BigDecimal amount = safeMoney(entry.getValue());
            if (bidderId == null || excluded.contains(bidderId) || amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String refundReference = auctionReference(safeEventType, item.getAuctionId()) + ":" + bidderId;
            if (item.hasSuccessfulFinancialEvent(safeEventType, refundReference)) {
                continue;
            }
            UUID bidderAccountId = item.getCurrentBidRecordForBidder(bidderId)
                    .flatMap(AuctionBidRecord::getBidderAccountId)
                    .orElse(null);
            if (bidderAccountId == null) {
                item.transitionTo(AuctionState.FAILED_SETTLEMENT, "missing bidder account during sealed bid refund");
                postSettlementFailedEvent(item, "Missing bidder account during sealed bid refund");
                alertOnlineAdmins("Auction Settlement Failed", "Auction {0} is missing a bidder account for sealed bid refund.", "ERROR", item.getAuctionId());
                return AuctionActionResult.fail("Auction settlement failed: missing bidder account for sealed bid refund.");
            }
            UasBankingResult refund = bankingService.deposit(bidderAccountId, amount, refundReference);
            recordBankingEvent(item, safeEventType, amount, refundReference, refund);
            if (!refund.success()) {
                item.transitionTo(AuctionState.FAILED_SETTLEMENT, "sealed bid refund failed: " + refund.reason());
                postSettlementFailedEvent(item, "Sealed bid refund failed: " + refund.reason());
                Object[] args = {item.getAuctionId(), itemName(item), moneyLabel(amount), refund.reason()};
                sendAuctionAlert(bidderId, "Auction Settlement Failed", "Auction {0}: Your sealed bid of {2} on {1} could not be refunded yet: {3}", "ERROR", args);
                sendAuctionChatMessage(bidderId, "Auction {0}: Your sealed bid of {2} on {1} could not be refunded yet: {3}", ChatFormatting.RED, args, openAhAction());
                alertOnlineAdmins("Auction Settlement Failed", "Auction {0} sealed bid refund failed: {1}", "ERROR", item.getAuctionId(), refund.reason());
                return AuctionActionResult.fail("Auction sealed bid refund failed: " + refund.reason());
            }
            Object[] args = {item.getAuctionId(), itemName(item), moneyLabel(amount)};
            sendAuctionAlert(bidderId, "Sealed Bid Refunded", "Auction {0}: Your sealed bid of {2} on {1} was refunded.", "INFO", args);
            sendAuctionChatMessage(bidderId, "Auction {0}: Your sealed bid of {2} on {1} was refunded.", ChatFormatting.AQUA, args, openAhAction());
        }
        return AuctionActionResult.ok(reason == null || reason.isBlank() ? "Sealed bid refunds completed." : reason);
    }

    private SettlementResult settleHeldBid(AuctionItem item) {
        if (item == null) {
            return SettlementResult.fail("Auction not found.", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal gross = safeMoney(item.getHighestBid());
        BigDecimal salesTax = Config.calculateSalesTax(gross).min(gross).max(BigDecimal.ZERO);
        BigDecimal net = gross.subtract(salesTax).max(BigDecimal.ZERO);
        if (item.hasSuccessfulFinancialEvent(EVENT_AUCTION_PAYOUT)) {
            return SettlementResult.ok("Auction payout was already settled.", gross, salesTax, net);
        }

        String payoutReference = auctionReference(EVENT_AUCTION_PAYOUT, item.getAuctionId());
        UUID sellerAccountId = item.getSellerAccountId();
        if (sellerAccountId == null) {
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "missing seller account during claim");
            postSettlementFailedEvent(item, "Missing seller account during claim");
            UasBankingResult failure = UasBankingResult.fail("Missing seller account", BigDecimal.ZERO);
            recordBankingEvent(item, EVENT_AUCTION_PAYOUT, net, payoutReference, failure);
            alertOnlineAdmins("Auction Settlement Failed", "Auction {0} is missing a seller account for payout.", "ERROR", item.getAuctionId());
            return SettlementResult.fail("Auction settlement failed: missing seller account.", gross, salesTax, net);
        }
        UasBankingResult canReceive = bankingService.validateCanReceive(sellerAccountId);
        if (!canReceive.success()) {
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "seller account cannot receive payout: " + canReceive.reason());
            postSettlementFailedEvent(item, "Seller account cannot receive payout: " + canReceive.reason());
            recordBankingEvent(item, EVENT_AUCTION_PAYOUT, net, payoutReference, canReceive);
            alertOnlineAdmins("Auction Settlement Failed", "Auction {0} seller account cannot receive payout: {1}", "ERROR", item.getAuctionId(), canReceive.reason());
            return SettlementResult.fail("Auction settlement failed: seller account cannot receive payout: " + canReceive.reason(), gross, salesTax, net);
        }

        SettlementResult taxSettlement = settleSalesTax(item, salesTax, gross, net);
        if (!taxSettlement.success()) {
            return taxSettlement;
        }

        if (Config.chequePayoutApplies(net)) {
            return settleSellerChequePayout(item, net, payoutReference, gross, salesTax);
        }

        UasBankingResult deposit = net.compareTo(BigDecimal.ZERO) > 0
                ? bankingService.deposit(sellerAccountId, net, payoutReference)
                : UasBankingResult.ok(BigDecimal.ZERO, null, payoutReference);
        recordBankingEvent(item, EVENT_AUCTION_PAYOUT, net, payoutReference, deposit);
        item.getWinningBidRecord().ifPresent(record -> record.linkSettlement(payoutReference, deposit));
        if (!deposit.success()) {
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "UBS payout failed: " + deposit.reason());
            postSettlementFailedEvent(item, "UBS payout failed: " + deposit.reason());
            alertOnlineAdmins("Auction Settlement Failed", "Auction {0} seller payout failed: {1}", "ERROR", item.getAuctionId(), deposit.reason());
            return SettlementResult.fail("Auction settlement failed: " + deposit.reason(), gross, salesTax, net);
        }

        BigDecimal fees = successfulFinancialEventAmount(item, EVENT_LISTING_FEE)
                .add(successfulFinancialEventAmount(item, EVENT_CANCELLATION_FEE));
        recordSettlementStats(item, gross);
        Object[] args = {item.getAuctionId(), itemName(item), moneyLabel(gross), moneyLabel(salesTax), moneyLabel(fees), moneyLabel(net)};
        sendAuctionAlert(item.getPlayerId(), "Auction Payout", "Auction {0}: {1} payout: gross {2}, tax {3}, fees {4}, net {5}.", "SUCCESS", args);
        sendAuctionChatMessage(item.getPlayerId(), "Auction {0}: {1} payout: gross {2}, tax {3}, fees {4}, net {5}.", ChatFormatting.GREEN, args, openAhAction(), myAuctionsAction());
        return SettlementResult.ok("Auction payout settled.", gross, salesTax, net);
    }

    private SettlementResult settleSellerChequePayout(AuctionItem item,
                                                      BigDecimal net,
                                                      String payoutReference,
                                                      BigDecimal gross,
                                                      BigDecimal salesTax) {
        UUID auctionId = item.getAuctionId();
        Optional<UUID> sourceAccountId = Config.chequePayoutSourceAccountId();
        if (sourceAccountId.isEmpty()) {
            UasItemResult failure = UasItemResult.fail("Cheque payout source account is not configured");
            recordItemEvent(item, EVENT_AUCTION_PAYOUT, net, payoutReference, failure);
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "UBS cheque payout source account is not configured");
            postSettlementFailedEvent(item, "UBS cheque payout source account is not configured");
            alertOnlineAdmins("Auction Settlement Failed", "Auction {0} cheque payout failed: {1}", "ERROR", auctionId, failure.reason());
            return SettlementResult.fail("Auction settlement failed: " + failure.reason(), gross, salesTax, net);
        }

        Long wholeDollars = wholeDollars(net);
        if (wholeDollars == null || wholeDollars <= 0L) {
            UasItemResult failure = UasItemResult.fail("Cheque payout requires a positive whole-dollar net payout");
            recordItemEvent(item, EVENT_AUCTION_PAYOUT, net, payoutReference, failure);
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "UBS cheque payout requires a positive whole-dollar amount");
            postSettlementFailedEvent(item, "UBS cheque payout requires a positive whole-dollar amount");
            alertOnlineAdmins("Auction Settlement Failed", "Auction {0} cheque payout failed: {1}", "ERROR", auctionId, failure.reason());
            return SettlementResult.fail("Auction settlement failed: " + failure.reason(), gross, salesTax, net);
        }

        UUID sellerId = item.getPlayerId();
        String sellerName = playerName(sellerId);
        AuctionDeliverySavedData deliveryData;
        try {
            deliveryData = AuctionDeliverySavedData.get(ServerLifecycleHooks.getCurrentServer());
        } catch (RuntimeException exception) {
            UasItemResult failure = UasItemResult.fail("Cheque delivery storage unavailable: " + exception.getMessage());
            recordItemEvent(item, EVENT_AUCTION_PAYOUT, net, payoutReference, failure);
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "UBS cheque delivery storage unavailable: " + exception.getMessage());
            postSettlementFailedEvent(item, "UBS cheque delivery storage unavailable: " + exception.getMessage());
            alertOnlineAdmins("Auction Settlement Failed", "Auction {0} cheque delivery failed: {1}", "ERROR", auctionId, exception.getMessage());
            return SettlementResult.fail("Auction settlement failed: " + failure.reason(), gross, salesTax, net);
        }

        UasItemResult cheque = bankingService.issueCheque(
                sourceAccountId.get(),
                sellerId,
                wholeDollars,
                Config.chequePayoutIssuerPlayerId().orElse(null),
                Config.chequePayoutIssuerName,
                sellerName
        );
        ItemStack issuedCheque = cheque.itemStack();
        if (!cheque.success() || issuedCheque == null || issuedCheque.isEmpty()) {
            UasItemResult failure = cheque.success() && (issuedCheque == null || issuedCheque.isEmpty())
                    ? UasItemResult.fail("UBS returned an empty cheque item")
                    : cheque;
            recordItemEvent(item, EVENT_AUCTION_PAYOUT, net, payoutReference, failure);
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "UBS cheque payout failed: " + failure.reason());
            postSettlementFailedEvent(item, "UBS cheque payout failed: " + failure.reason());
            alertOnlineAdmins("Auction Settlement Failed", "Auction {0} cheque payout failed: {1}", "ERROR", auctionId, failure.reason());
            return SettlementResult.fail("Auction settlement failed: " + failure.reason(), gross, salesTax, net);
        }

        ItemStack chequeStack = annotateCheque(issuedCheque, auctionId, payoutReference, wholeDollars);
        try {
            giveOrDeliver(sellerId, chequeStack, deliveryData, auctionId, "UBS cheque payout");
        } catch (RuntimeException exception) {
            UasItemResult failure = UasItemResult.fail("Cheque delivery failed: " + exception.getMessage());
            recordItemEvent(item, EVENT_AUCTION_PAYOUT, net, payoutReference, failure);
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "UBS cheque delivery failed: " + exception.getMessage());
            postSettlementFailedEvent(item, "UBS cheque delivery failed: " + exception.getMessage());
            alertOnlineAdmins("Auction Settlement Failed", "Auction {0} cheque delivery failed: {1}", "ERROR", auctionId, exception.getMessage());
            return SettlementResult.fail("Auction settlement failed: " + failure.reason(), gross, salesTax, net);
        }

        recordItemEvent(item, EVENT_AUCTION_PAYOUT, net, payoutReference, cheque);
        item.getWinningBidRecord().ifPresent(record -> record.linkSettlement(
                payoutReference,
                UasBankingResult.ok(BigDecimal.ZERO, null, payoutReference)
        ));
        recordSettlementStats(item, gross);
        BigDecimal fees = successfulFinancialEventAmount(item, EVENT_LISTING_FEE)
                .add(successfulFinancialEventAmount(item, EVENT_CANCELLATION_FEE));
        Object[] args = {auctionId, itemName(item), moneyLabel(gross), moneyLabel(salesTax), moneyLabel(fees), cheque.referenceId().isBlank() ? payoutReference : cheque.referenceId()};
        sendAuctionAlert(sellerId, "Auction Payout", "Auction {0}: {1} cheque payout: gross {2}, tax {3}, fees {4}, cheque {5}.", "SUCCESS", args);
        sendAuctionChatMessage(sellerId, "Auction {0}: {1} cheque payout: gross {2}, tax {3}, fees {4}, cheque {5}.", ChatFormatting.GREEN, args, openAhAction(), deliveryAction());
        return SettlementResult.ok("Auction cheque payout settled.", gross, salesTax, net);
    }

    private SettlementResult settleSalesTax(AuctionItem item, BigDecimal salesTax, BigDecimal gross, BigDecimal net) {
        if (item == null || salesTax == null || salesTax.compareTo(BigDecimal.ZERO) <= 0 || item.hasSuccessfulFinancialEvent(EVENT_SALES_TAX)) {
            return SettlementResult.ok("", gross, salesTax, net);
        }

        String taxReference = auctionReference(EVENT_SALES_TAX, item.getAuctionId());
        Optional<UUID> destinationAccountId = Config.salesTaxDestinationAccountId();
        if (destinationAccountId.isEmpty()) {
            recordManualFinancialEvent(
                    item,
                    EVENT_SALES_TAX,
                    salesTax,
                    taxReference,
                    true,
                    "Deducted from seller payout as money sink"
            );
            return SettlementResult.ok("", gross, salesTax, net);
        }

        UasBankingResult taxDeposit = bankingService.deposit(destinationAccountId.get(), salesTax, taxReference);
        recordBankingEvent(item, EVENT_SALES_TAX, salesTax, taxReference, taxDeposit);
        if (!taxDeposit.success()) {
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "sales tax transfer failed: " + taxDeposit.reason());
            postSettlementFailedEvent(item, "Sales tax transfer failed: " + taxDeposit.reason());
            alertOnlineAdmins("Auction Settlement Failed", "Auction {0} sales tax transfer failed: {1}", "ERROR", item.getAuctionId(), taxDeposit.reason());
            return SettlementResult.fail("Auction settlement failed: sales tax transfer failed: " + taxDeposit.reason(), gross, salesTax, net);
        }
        return SettlementResult.ok("", gross, salesTax, net);
    }

    private BigDecimal successfulFinancialEventAmount(AuctionItem item, String type) {
        if (item == null || type == null || type.isBlank()) {
            return BigDecimal.ZERO;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        return item.getFinancialEvents().stream()
                .filter(event -> event != null && event.success() && normalized.equals(event.type()))
                .map(AuctionFinancialEvent::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Long wholeDollars(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        try {
            return amount.setScale(0, java.math.RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private ItemStack annotateCheque(ItemStack rawCheque, UUID auctionId, String payoutReference, long wholeDollars) {
        ItemStack cheque = rawCheque == null ? ItemStack.EMPTY : rawCheque.copy();
        if (cheque.isEmpty()) {
            return cheque;
        }
        CustomData existing = cheque.get(DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag tag = existing == null ? new net.minecraft.nbt.CompoundTag() : existing.copyTag();
        if (auctionId != null) {
            tag.putUUID("uas_auction_id", auctionId);
        }
        tag.putString("uas_auction_reference", payoutReference == null ? "" : payoutReference);
        tag.putLong("uas_cheque_payout_dollars", wholeDollars);
        cheque.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return cheque;
    }

    private void giveOrDeliver(ServerPlayer player,
                               ItemStack stack,
                               AuctionDeliverySavedData deliveryData,
                               UUID auctionId,
                               String reason) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        giveOrDeliver(player, List.of(stack), deliveryData, auctionId, reason);
    }

    private void giveOrDeliver(ServerPlayer player,
                               List<ItemStack> stacks,
                               AuctionDeliverySavedData deliveryData,
                               UUID auctionId,
                               String reason) {
        if (player == null) {
            return;
        }
        List<ItemStack> safeStacks = safeItemStacks(stacks);
        if (safeStacks.isEmpty()) {
            return;
        }
        if (canInventoryFit(player, safeStacks)) {
            for (ItemStack stack : safeStacks) {
                player.getInventory().add(stack.copy());
            }
        } else {
            if (deliveryData != null) {
                deliveryData.addDelivery(player.getUUID(), auctionId, safeStacks, reason);
            } else {
                for (ItemStack stack : safeStacks) {
                    player.drop(stack.copy(), false);
                }
            }
        }
        player.getInventory().setChanged();
    }

    private void giveOrDeliver(UUID playerId,
                               ItemStack stack,
                               AuctionDeliverySavedData deliveryData,
                               UUID auctionId,
                               String reason) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        giveOrDeliver(playerId, List.of(stack), deliveryData, auctionId, reason);
    }

    private void giveOrDeliver(UUID playerId,
                               List<ItemStack> stacks,
                               AuctionDeliverySavedData deliveryData,
                               UUID auctionId,
                               String reason) {
        if (playerId == null) {
            return;
        }
        List<ItemStack> safeStacks = safeItemStacks(stacks);
        if (safeStacks.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer onlinePlayer = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (onlinePlayer != null) {
            giveOrDeliver(onlinePlayer, safeStacks, deliveryData, auctionId, reason);
            return;
        }
        if (deliveryData != null) {
            deliveryData.addDelivery(playerId, auctionId, safeStacks, reason);
        }
    }

    private boolean canInventoryFit(ServerPlayer player, List<ItemStack> stacks) {
        if (player == null) {
            return false;
        }
        List<ItemStack> simulated = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            simulated.add(player.getInventory().getItem(slot).copy());
        }
        for (ItemStack stack : safeItemStacks(stacks)) {
            ItemStack remaining = stack.copy();
            for (ItemStack existing : simulated) {
                if (remaining.isEmpty()) {
                    break;
                }
                if (!existing.isEmpty()
                        && ItemStack.isSameItemSameComponents(existing, remaining)
                        && existing.getCount() < existing.getMaxStackSize()) {
                    int move = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                    existing.grow(move);
                    remaining.shrink(move);
                }
            }
            if (!remaining.isEmpty()) {
                for (int i = 0; i < simulated.size(); i++) {
                    ItemStack existing = simulated.get(i);
                    if (existing.isEmpty()) {
                        simulated.set(i, remaining.copy());
                        remaining.setCount(0);
                        break;
                    }
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isBannedFromAuctions(ItemStack stack) {
        for (String raw : Config.bannedAuctionEntries) {
            if (bannedEntryMatches(Config.normalizeAuctionRestriction(raw), stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean bannedEntryMatches(String entry, ItemStack stack) {
        if (entry == null || entry.isBlank() || stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return false;
        }
        if (entry.startsWith("@")) {
            return itemId.getNamespace().equals(entry.substring(1));
        }
        if (entry.startsWith("#")) {
            ResourceLocation tagId = parseResourceLocation(entry.substring(1));
            return tagId != null && stack.is(TagKey.create(Registries.ITEM, tagId));
        }
        return itemId.toString().equals(entry);
    }

    private AuctionListingSummary toSummary(AuctionItem item, ServerPlayer viewer, boolean adminMode) {
        UUID viewerId = viewer == null ? null : viewer.getUUID();
        ItemStack stack = item.getItem();
        List<ItemStack> contents = item.getContents();
        List<AuctionBidRecord> bidRecords = item.getBidRecords();
        Map<UUID, String> bidderNames = new HashMap<>();
        for (AuctionBidRecord record : bidRecords) {
            UUID bidderId = record.getBidderId();
            if (bidderId != null) {
                bidderNames.computeIfAbsent(bidderId, this::playerName);
            }
        }
        boolean viewerIsSeller = viewerId != null && viewerId.equals(item.getPlayerId());
        boolean sealedBid = item.isSealedBid();
        boolean sealedBidRevealed = !sealedBid || adminMode || item.getState() != AuctionState.ACTIVE || item.isExpired();
        boolean viewerIsHighestBidder = viewerId != null && viewerId.equals(item.getHighestBidderId()) && sealedBidRevealed;
        boolean viewerHasBid = viewerId != null && item.getBids().containsKey(viewerId);
        BigDecimal viewerBid = viewerId == null ? BigDecimal.ZERO : item.getBids().getOrDefault(viewerId, BigDecimal.ZERO);
        boolean reserveActive = item.hasReservePrice();
        boolean reserveMet = item.isReserveMet();
        boolean reserveVisible = reserveActive && (viewerIsSeller || adminMode);
        boolean hasWinningBidderForClaim = item.getHighestBidderId() != null && reserveMet;
        boolean viewerReceivesNotifications = viewerId != null && item.isNotificationSubscriber(viewerId);
        int notificationSubscriberCount = item.getNotificationSubscribers().size();
        boolean active = item.getState() == AuctionState.ACTIVE && !item.isExpired();
        boolean buyoutAvailable = item.getBuyoutPrice()
                .map(price -> sealedBid || item.getHighestBidderId() == null || item.getHighestBid().compareTo(price) < 0)
                .orElse(false);
        List<AuctionBidRecord> visibleBidRecords = sealedBid && !sealedBidRevealed ? List.of() : bidRecords;
        BigDecimal visibleCurrentBid = sealedBid && !sealedBidRevealed ? item.getStartingBidPrice() : item.getHighestBid();
        boolean canClaim = viewerId != null && canViewerClaimAuction(
                item.getState(),
                item.isExpired(),
                viewerIsHighestBidder && reserveMet,
                viewerIsSeller,
                hasWinningBidderForClaim
        );
        return new AuctionListingSummary(
                item.getAuctionId(),
                item.getPlayerId(),
                playerName(item.getPlayerId()),
                stack,
                item.getDisplayTitle(),
                item.getDescription(),
                AuctionCategory.categorize(stack),
                stack.getRarity().name().toLowerCase(Locale.ROOT),
                item.isExpired() && item.getState() == AuctionState.ACTIVE ? AuctionState.ENDED : item.getState(),
                item.getStartingBidPrice(),
                visibleCurrentBid,
                item.getBuyoutPrice().orElse(BigDecimal.ZERO),
                item.getReservePrice().orElse(BigDecimal.ZERO),
                item.getFormat(),
                sealedBidRevealed,
                viewerBid,
                reserveActive,
                reserveVisible,
                reserveMet,
                bidRecords.stream().filter(AuctionBidRecord::isAccepted).toList().size(),
                item.getHighestBidderId(),
                item.getCreatedAt(),
                item.getDateOfEnd(),
                viewerIsSeller,
                viewerIsHighestBidder,
                viewerHasBid,
                viewerReceivesNotifications,
                notificationSubscriberCount,
                active && (!viewerIsSeller || Config.allowSellerSelfBid),
                active && (!viewerIsSeller || Config.allowSellerSelfBid) && buyoutAvailable,
                active && viewerIsSeller && item.getHighestBidderId() == null,
                canClaim,
                visibleBidRecords,
                bidderNames,
                contents,
                item.isBundle(),
                item.getTotalItemCount()
        );
    }

    static boolean canViewerClaimAuction(AuctionState state,
                                         boolean expired,
                                         boolean viewerIsHighestBidder,
                                         boolean viewerIsSeller,
                                         boolean hasHighestBidder) {
        boolean viewerCanReceiveClaim = viewerIsHighestBidder || (viewerIsSeller && !hasHighestBidder);
        if (!viewerCanReceiveClaim) {
            return false;
        }
        return state == AuctionState.ENDED
                || (state == AuctionState.ACTIVE && expired);
    }

    private boolean matchesQuery(AuctionListingSummary summary, AuctionUiQuery query) {
        if (summary == null) {
            return false;
        }
        if (summary.contents().stream().noneMatch(stack -> query.safeCategory().matches(stack))) {
            return false;
        }
        String modId = query.safeModId();
        if (!modId.isEmpty() && summary.contents().stream().noneMatch(stack -> modId.equals(itemModId(stack)))) {
            return false;
        }
        String search = query.safeSearch().toLowerCase(Locale.ROOT);
        if (!search.isEmpty()) {
            String haystack = searchHaystack(summary).toLowerCase(Locale.ROOT);
            if (!haystack.contains(search)) {
                return false;
            }
        }
        if (!matchesPriceRange(summary.currentBid(), summary.buyoutPrice(), query.minimumPrice(), query.maximumPrice())) {
            return false;
        }
        if (query.maximumHoursLeft() > 0) {
            long hoursLeft = Math.max(0L, Duration.between(LocalDateTime.now(), summary.endsAt()).toHours());
            if (hoursLeft > query.maximumHoursLeft()) {
                return false;
            }
        }
        return true;
    }

    private String searchHaystack(AuctionListingSummary summary) {
        StringBuilder haystack = new StringBuilder()
                .append(safeSearchToken(summary.itemName())).append(' ')
                .append(safeSearchToken(summary.sellerName())).append(' ')
                .append(summary.sellerId() == null ? "" : summary.sellerId()).append(' ')
                .append(summary.auctionId() == null ? "" : summary.auctionId()).append(' ')
                .append(safeSearchToken(summary.description()));
        for (ItemStack stack : summary.contents()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            haystack.append(' ')
                    .append(safeSearchToken(stack.getHoverName().getString()))
                    .append(' ')
                    .append(itemRegistryId(stack));
        }
        return haystack.toString();
    }

    private String safeSearchToken(String value) {
        return value == null ? "" : value;
    }

    static boolean matchesPriceRange(BigDecimal currentBid,
                                     BigDecimal buyoutPrice,
                                     BigDecimal minimumPrice,
                                     BigDecimal maximumPrice) {
        BigDecimal min = minimumPrice == null ? BigDecimal.ZERO : minimumPrice;
        BigDecimal max = maximumPrice == null ? BigDecimal.ZERO : maximumPrice;
        if (min.compareTo(BigDecimal.ZERO) <= 0 && max.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        List<BigDecimal> candidates = new ArrayList<>();
        candidates.add(safeMoney(currentBid));
        BigDecimal safeBuyout = safeMoney(buyoutPrice);
        if (safeBuyout.compareTo(BigDecimal.ZERO) > 0) {
            candidates.add(safeBuyout);
        }
        for (BigDecimal candidate : candidates) {
            boolean aboveMin = min.compareTo(BigDecimal.ZERO) <= 0 || candidate.compareTo(min) >= 0;
            boolean belowMax = max.compareTo(BigDecimal.ZERO) <= 0 || candidate.compareTo(max) <= 0;
            if (aboveMin && belowMax) {
                return true;
            }
        }
        return false;
    }

    private String itemModId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId == null ? "" : itemId.getNamespace().toLowerCase(Locale.ROOT);
    }

    private String itemRegistryId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId == null ? "" : itemId.toString().toLowerCase(Locale.ROOT);
    }

    private String modDisplayName(String modId) {
        if (modId == null || modId.isBlank()) {
            return "";
        }
        if ("minecraft".equals(modId)) {
            return "Minecraft";
        }
        try {
            ModList modList = ModList.get();
            if (modList != null) {
                return modList.getModContainerById(modId)
                        .map(container -> container.getModInfo().getDisplayName())
                        .filter(name -> name != null && !name.isBlank())
                        .orElse(modId);
            }
        } catch (RuntimeException ignored) {
        }
        return modId;
    }

    private Comparator<AuctionListingSummary> comparatorFor(AuctionSort sort) {
        return switch (sort) {
            case NEWEST -> Comparator.comparing(AuctionListingSummary::createdAt).reversed();
            case HIGHEST_BID -> Comparator.comparing(AuctionListingSummary::currentBid).reversed();
            case LOWEST_PRICE -> Comparator.comparing(AuctionListingSummary::currentBid);
            case BUYOUT_PRICE -> Comparator.comparing(summary -> summary.buyoutPrice().compareTo(BigDecimal.ZERO) <= 0
                    ? new BigDecimal("999999999999")
                    : summary.buyoutPrice());
            case ENDING_SOON -> Comparator.comparing(AuctionListingSummary::endsAt);
        };
    }

    private String playerName(UUID playerId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && playerId != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                return player.getName().getString();
            }
        }
        return playerId == null ? "Unknown" : playerId.toString().substring(0, 8);
    }

    private ResourceLocation parseResourceLocation(String raw) {
        try {
            return ResourceLocation.parse(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private List<Integer> sanitizeSelectedSlots(List<Integer> slots) {
        ArrayList<Integer> safeSlots = new ArrayList<>();
        if (slots == null) {
            return safeSlots;
        }
        for (Integer slot : slots) {
            if (slot == null || safeSlots.contains(slot)) {
                continue;
            }
            safeSlots.add(slot);
            if (safeSlots.size() >= AuctionItem.MAX_BUNDLE_CONTENTS) {
                break;
            }
        }
        return safeSlots;
    }

    private List<ItemStack> safeItemStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }
        return stacks.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .limit(AuctionItem.MAX_BUNDLE_CONTENTS)
                .map(ItemStack::copy)
                .toList();
    }

    private static BigDecimal safeMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private boolean hasFractionalDollars(BigDecimal amount) {
        return amount != null && amount.stripTrailingZeros().scale() > 0;
    }
}
