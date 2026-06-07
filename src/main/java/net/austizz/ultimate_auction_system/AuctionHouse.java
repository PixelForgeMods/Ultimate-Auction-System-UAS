package net.austizz.ultimate_auction_system;

import com.google.common.eventbus.Subscribe;
import net.austizz.ultimate_auction_system.banking.UasAlertResult;
import net.austizz.ultimate_auction_system.banking.UasBankingResult;
import net.austizz.ultimate_auction_system.banking.UasBankingService;
import net.austizz.ultimate_auction_system.banking.UbsBankingService;
import net.austizz.ultimate_auction_system.banking.UasAccountSnapshot;
import net.austizz.ultimate_auction_system.banking.UasMoneyFormatter;
import net.austizz.ultimate_auction_system.i18n.UasTranslations;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
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
    static final String EVENT_AUCTION_PAYOUT = "AUCTION_PAYOUT";
    static final String EVENT_SALES_TAX = "SALES_TAX";
    static final String EVENT_ADMIN_FORCE_CANCEL_REFUND = "ADMIN_FORCE_CANCEL_REFUND";
    static final String EVENT_CANCELLATION_FEE = "CANCELLATION_FEE";

    private final ConcurrentHashMap<UUID, AuctionItem> AuctionItems;
    private final ConcurrentHashMap<UUID, PendingAuctionListing> pendingListings = new ConcurrentHashMap<>();
    private final UasBankingService bankingService;
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
        if (player == null) {
            return AuctionActionResult.fail("Only players can create auctions.");
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
        AuctionActionResult validation = validateListingRequest(player, snapshots, startingBidPrice, buyoutPrice, end);
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
                end,
                description,
                now,
                now.plusSeconds(Config.pendingListingConfirmationSeconds),
                snapshots.size() > 1 ? "Bundle (" + snapshots.size() + " items)" : "Inventory Slot " + (safeSlots.getFirst() + 1)
        );
        pendingListings.put(player.getUUID(), pending);
        return AuctionActionResult.ok(pendingPreviewMessage(pending));
    }

    public AuctionActionResult confirmPendingAuction(ServerPlayer player) {
        if (player == null) {
            return AuctionActionResult.fail("Only players can create auctions.");
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

        AuctionActionResult validation = validateListingRequest(player, pending.itemSnapshots(), pending.startingBid(), pending.buyoutPrice(), pending.endsAt());
        if (!validation.success()) {
            return validation;
        }

        Optional<UUID> sellerAccountId = bankingService.getPrimaryAccountId(player.getUUID());
        if (sellerAccountId.isEmpty()) {
            return AuctionActionResult.fail(accountSetupMessage(player.getUUID()));
        }

        UUID auctionId = UUID.randomUUID();
        String listingFeeReference = auctionReference(EVENT_LISTING_FEE, auctionId);
        UasBankingResult feeResult = chargeListingFee(sellerAccountId.get(), pending.startingBid(), listingFeeReference);
        if (!feeResult.success()) {
            return AuctionActionResult.fail(feeFailureMessage("listing fee", Config.calculateListingFee(pending.startingBid()), feeResult));
        }

        List<ItemStack> escrowStacks = pending.itemSnapshots();
        if (!takePendingEscrowFromSeller(player, pending)) {
            refundListingFee(sellerAccountId.get(), pending.startingBid(), auctionReference(EVENT_LISTING_FEE_REFUND, auctionId));
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
                sellerAccountId.get(),
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
        if (bidder == null) {
            return AuctionActionResult.fail("Only players can place bids.");
        }
        if (AuctionAdminSavedData.isBlocked(bidder.getServer(), bidder.getUUID(), AuctionBanAction.BID)) {
            return auctionBanFailure(AuctionBanAction.BID);
        }
        return placeBidWithEscrow(bidder.getUUID(), auctionId, amount, true);
    }

    AuctionActionResult placeBidWithEscrow(UUID bidderId, UUID auctionId, BigDecimal amount) {
        return placeBidWithEscrow(bidderId, auctionId, amount, true);
    }

    private AuctionActionResult placeBidWithEscrow(UUID bidderId, UUID auctionId, BigDecimal amount, boolean emitBidAlerts) {
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
                return AuctionActionResult.fail("You cannot bid on your own auction.");
            }
            BigDecimal safeAmount = safeMoney(amount);
            if (hasFractionalDollars(safeAmount)) {
                return AuctionActionResult.fail("Bids must use whole dollars.");
            }
            BigDecimal minimum = minimumAcceptedBid(item);
            if (safeAmount.compareTo(minimum) < 0) {
                return AuctionActionResult.fail("Bid must be at least " + moneyLabel(minimum) + ".");
            }

            Optional<UUID> bidderAccountId = bankingService.getPrimaryAccountId(bidderId);
            if (bidderAccountId.isEmpty()) {
                item.recordRejectedBid(bidderId, null, safeAmount, AuctionBidResult.REJECTED_NO_ACCOUNT, "Bidder has no UBS primary account.");
                return AuctionActionResult.fail("UAS could not find your UBS primary account.");
            }
            UasBankingResult canSend = bankingService.validateCanSend(bidderAccountId.get(), safeAmount);
            if (!canSend.success()) {
                item.recordRejectedBid(bidderId, bidderAccountId.get(), safeAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, canSend.reason());
                return AuctionActionResult.fail("Your UBS primary account cannot pay this bid: " + canSend.reason());
            }

            UUID previousBidderId = item.getHighestBidderId();
            BigDecimal previousAmount = item.getHighestBid();
            UUID previousAccountId = item.getWinningBidRecord()
                    .flatMap(AuctionBidRecord::getBidderAccountId)
                    .orElse(null);

            String holdReference = auctionReference(EVENT_BID_ESCROW, item.getAuctionId());
            UasBankingResult hold = bankingService.withdraw(bidderAccountId.get(), safeAmount, holdReference);
            recordBankingEvent(item, EVENT_BID_ESCROW, safeAmount, holdReference, hold);
            if (!hold.success()) {
                item.recordRejectedBid(bidderId, bidderAccountId.get(), safeAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, hold.reason());
                return AuctionActionResult.fail("Your UBS primary account could not reserve the bid: " + hold.reason());
            }

            if (previousBidderId != null && previousAccountId != null) {
                String refundReference = auctionReference(EVENT_OUTBID_REFUND, item.getAuctionId());
                UasBankingResult refund = bankingService.deposit(previousAccountId, previousAmount, refundReference);
                recordBankingEvent(item, EVENT_OUTBID_REFUND, previousAmount, refundReference, refund);
                if (!refund.success()) {
                    String holdRefundReference = auctionReference(EVENT_BID_ESCROW_REFUND, item.getAuctionId());
                    UasBankingResult holdRefund = bankingService.deposit(bidderAccountId.get(), safeAmount, holdRefundReference);
                    recordBankingEvent(item, EVENT_BID_ESCROW_REFUND, safeAmount, holdRefundReference, holdRefund);
                    item.recordRejectedBid(bidderId, bidderAccountId.get(), safeAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, "Outbid refund failed: " + refund.reason());
                    String message = "Outbid refund failed for auction " + item.getAuctionId() + ": " + refund.reason();
                    UltimateAuctionSystem.LOGGER.warn("[UAS] {}", message);
                    alertOnlineAdmins("Auction Refund Failed", message, "ERROR");
                    return AuctionActionResult.fail("Could not safely refund the previous highest bidder. Bid was not accepted.");
                }
            }

            AuctionBidRecord bidRecord = item.recordBid(bidderId, bidderAccountId.get(), safeAmount);
            if (!bidRecord.isAccepted()) {
                String refundReference = auctionReference(EVENT_BID_ESCROW_REFUND, item.getAuctionId());
                UasBankingResult refund = bankingService.deposit(bidderAccountId.get(), safeAmount, refundReference);
                recordBankingEvent(item, EVENT_BID_ESCROW_REFUND, safeAmount, refundReference, refund);
                if (!refund.success()) {
                    item.transitionTo(AuctionState.FAILED_SETTLEMENT, "bid escrow refund failed after rejected bid: " + refund.reason());
                    alertOnlineAdmins("Auction Settlement Failed", "Bid escrow refund failed for auction " + item.getAuctionId() + ": " + refund.reason(), "ERROR");
                }
                return AuctionActionResult.fail(bidRecord.getReason());
            }

            markChanged("Auction storage marked dirty after accepted bid.");
            boolean soldByBid = item.getState() == AuctionState.ENDED
                    && item.getBuyoutPrice().isPresent()
                    && safeAmount.compareTo(item.getBuyoutPrice().get()) >= 0;
            if (soldByBid) {
                SettlementResult settlement = settleHeldBid(item);
                if (!settlement.success()) {
                    sendAuctionAlert(item.getPlayerId(), "Auction Settlement Failed", itemName(item) + " could not pay out to your account.", "ERROR");
                    sendAuctionAlert(bidderId, "Auction Settlement Delayed", itemName(item) + " is waiting for payment settlement.", "WARNING");
                    return AuctionActionResult.fail(settlement.message());
                }
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

    public AuctionActionResult buyout(ServerPlayer bidder, UUID auctionId) {
        if (bidder == null) {
            return AuctionActionResult.fail("Only players can place bids.");
        }
        if (AuctionAdminSavedData.isBlocked(bidder.getServer(), bidder.getUUID(), AuctionBanAction.BUYOUT)) {
            return auctionBanFailure(AuctionBanAction.BUYOUT);
        }
        return buyout(bidder.getUUID(), auctionId);
    }

    AuctionActionResult buyout(UUID bidderId, UUID auctionId) {
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
                return AuctionActionResult.fail("You cannot buy out your own auction.");
            }
            Optional<BigDecimal> buyout = item.getBuyoutPrice();
            if (buyout.isEmpty()) {
                return AuctionActionResult.fail("This auction has no buyout price.");
            }
            BigDecimal buyoutAmount = safeMoney(buyout.get());
            if (item.getHighestBidderId() != null && item.getHighestBid().compareTo(buyoutAmount) >= 0) {
                return AuctionActionResult.fail("The current bid already reached the buyout price.");
            }

            Optional<UUID> bidderAccountId = bankingService.getPrimaryAccountId(bidderId);
            if (bidderAccountId.isEmpty()) {
                item.recordRejectedBid(bidderId, null, buyoutAmount, AuctionBidResult.REJECTED_NO_ACCOUNT, "Bidder has no UBS primary account.");
                return AuctionActionResult.fail("UAS could not find your UBS primary account.");
            }
            UasBankingResult canSend = bankingService.validateCanSend(bidderAccountId.get(), buyoutAmount);
            if (!canSend.success()) {
                item.recordRejectedBid(bidderId, bidderAccountId.get(), buyoutAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, canSend.reason());
                return AuctionActionResult.fail("Your UBS primary account cannot pay this buyout: " + canSend.reason());
            }

            UUID previousBidderId = item.getHighestBidderId();
            BigDecimal previousAmount = item.getHighestBid();
            UUID previousAccountId = item.getWinningBidRecord()
                    .flatMap(AuctionBidRecord::getBidderAccountId)
                    .orElse(null);

            String holdReference = auctionReference(EVENT_BUYOUT_ESCROW, item.getAuctionId());
            UasBankingResult hold = bankingService.withdraw(bidderAccountId.get(), buyoutAmount, holdReference);
            recordBankingEvent(item, EVENT_BUYOUT_ESCROW, buyoutAmount, holdReference, hold);
            if (!hold.success()) {
                item.recordRejectedBid(bidderId, bidderAccountId.get(), buyoutAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, hold.reason());
                return AuctionActionResult.fail("Your UBS primary account could not reserve the buyout: " + hold.reason());
            }

            if (previousBidderId != null && previousAccountId != null) {
                String refundReference = auctionReference(EVENT_OUTBID_REFUND, item.getAuctionId());
                UasBankingResult refund = bankingService.deposit(previousAccountId, previousAmount, refundReference);
                recordBankingEvent(item, EVENT_OUTBID_REFUND, previousAmount, refundReference, refund);
                if (!refund.success()) {
                    String holdRefundReference = auctionReference(EVENT_BUYOUT_ESCROW_REFUND, item.getAuctionId());
                    UasBankingResult holdRefund = bankingService.deposit(bidderAccountId.get(), buyoutAmount, holdRefundReference);
                    recordBankingEvent(item, EVENT_BUYOUT_ESCROW_REFUND, buyoutAmount, holdRefundReference, holdRefund);
                    item.recordRejectedBid(bidderId, bidderAccountId.get(), buyoutAmount, AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE, "Outbid refund failed: " + refund.reason());
                    String message = "Buyout refund of previous bidder failed for auction " + item.getAuctionId() + ": " + refund.reason();
                    UltimateAuctionSystem.LOGGER.warn("[UAS] {}", message);
                    alertOnlineAdmins("Auction Refund Failed", message, "ERROR");
                    return AuctionActionResult.fail("Could not safely refund the previous highest bidder. Buyout was not accepted.");
                }
            }

            AuctionBidRecord bidRecord = item.recordBid(bidderId, bidderAccountId.get(), buyoutAmount);
            if (!bidRecord.isAccepted()) {
                String refundReference = auctionReference(EVENT_BUYOUT_ESCROW_REFUND, item.getAuctionId());
                UasBankingResult refund = bankingService.deposit(bidderAccountId.get(), buyoutAmount, refundReference);
                recordBankingEvent(item, EVENT_BUYOUT_ESCROW_REFUND, buyoutAmount, refundReference, refund);
                if (!refund.success()) {
                    item.transitionTo(AuctionState.FAILED_SETTLEMENT, "buyout escrow refund failed after rejected buyout: " + refund.reason());
                    alertOnlineAdmins("Auction Settlement Failed", "Buyout escrow refund failed for auction " + item.getAuctionId() + ": " + refund.reason(), "ERROR");
                }
                return AuctionActionResult.fail(bidRecord.getReason());
            }

            item.transitionTo(AuctionState.ENDED, "buyout accepted");
            SettlementResult settlement = settleHeldBid(item);
            if (!settlement.success()) {
                sendAuctionAlert(item.getPlayerId(), "Auction Settlement Failed", itemName(item) + " could not pay out to your account.", "ERROR");
                sendAuctionAlert(bidderId, "Auction Settlement Delayed", itemName(item) + " is waiting for payment settlement.", "WARNING");
                return AuctionActionResult.fail(settlement.message());
            }
            markChanged("Auction storage marked dirty after accepted buyout.");
            notifyAuctionSold(item, bidderId, buyoutAmount);
            return AuctionActionResult.ok("Buyout accepted. Seller was paid; claim the item from My Bids.");
        }
    }

    public AuctionActionResult cancelOwnAuction(ServerPlayer seller, UUID auctionId, AuctionDeliverySavedData deliveryData) {
        if (seller == null) {
            return AuctionActionResult.fail("Only players can cancel auctions.");
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
            notifyAuctionCancelled(item, seller.getUUID());
            return AuctionActionResult.ok("Auction cancelled and item returned.");
        }
    }

    public AuctionActionResult adminForceCancel(ServerPlayer admin, UUID auctionId, AuctionDeliverySavedData deliveryData) {
        if (admin == null) {
            return AuctionActionResult.fail("Only admins can force-cancel auctions.");
        }
        if (!admin.hasPermissions(Config.adminStatusPermissionLevel)) {
            return AuctionActionResult.fail("You do not have permission to force-cancel auctions.");
        }
        AuctionItem item = getAuctionItem(auctionId);
        if (item == null) {
            return AuctionActionResult.fail("Auction not found.");
        }
        synchronized (item) {
            if (item.getState() == AuctionState.CLAIMED || item.getState() == AuctionState.CANCELLED) {
                return AuctionActionResult.fail("Auction has already been claimed or cancelled.");
            }

            UUID winnerAccountId = item.getWinningBidRecord()
                    .flatMap(AuctionBidRecord::getBidderAccountId)
                    .orElse(null);
            UUID winnerId = item.getHighestBidderId();
            if (winnerId != null && winnerAccountId != null && item.getHighestBid().compareTo(BigDecimal.ZERO) > 0) {
                String refundReference = auctionReference(EVENT_ADMIN_FORCE_CANCEL_REFUND, item.getAuctionId());
                UasBankingResult refund = bankingService.deposit(winnerAccountId, item.getHighestBid(), refundReference);
                recordBankingEvent(item, EVENT_ADMIN_FORCE_CANCEL_REFUND, item.getHighestBid(), refundReference, refund);
                if (!refund.success()) {
                    return AuctionActionResult.fail("Could not refund the highest bid before force-cancelling: " + refund.reason());
                }
            }

            if (!item.transitionTo(AuctionState.CANCELLED, "admin force-cancelled by " + admin.getGameProfile().getName())) {
                return AuctionActionResult.fail("Auction could not be force-cancelled.");
            }
            giveOrDeliver(item.getPlayerId(), item.getContents(), deliveryData, auctionId, "Admin force-cancel return");
            markChanged("Auction storage marked dirty after admin force-cancel.");
            sendAuctionAlert(item.getPlayerId(), "Auction Force-Cancelled", itemName(item) + " was force-cancelled by an admin and returned.", "WARNING");
            if (winnerId != null) {
                sendAuctionAlert(winnerId, "Auction Force-Cancelled", "Your bid on " + itemName(item) + " was refunded after an admin force-cancel.", "WARNING");
            }
            alertSubscribers(item, exclusions(item.getPlayerId(), winnerId), "Auction Force-Cancelled", itemName(item) + " was force-cancelled by an admin.", "WARNING");
            return AuctionActionResult.ok("Auction force-cancelled and item returned.");
        }
    }

    public AuctionActionResult adminRetrySettlement(ServerPlayer admin, UUID auctionId, AuctionDeliverySavedData deliveryData) {
        if (admin == null) {
            return AuctionActionResult.fail("Only admins can retry auction settlement.");
        }
        if (!admin.hasPermissions(Config.adminStatusPermissionLevel)) {
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
            UUID winnerId = item.getHighestBidderId();
            if (winnerId == null) {
                item.transitionTo(AuctionState.ENDED, "admin retry found no winning bidder");
                markChanged("Auction storage marked dirty after settlement retry recovery.");
                return AuctionActionResult.ok("Auction has no winner; moved back to ended state.");
            }
            SettlementResult settlement = settleHeldBid(item);
            if (!settlement.success()) {
                sendAuctionAlert(item.getPlayerId(), "Auction Settlement Retry Failed", itemName(item) + " still could not pay out.", "ERROR");
                sendAuctionAlert(winnerId, "Auction Settlement Retry Failed", itemName(item) + " still could not finish settlement.", "ERROR");
                return AuctionActionResult.fail(settlement.message());
            }
            giveOrDeliver(winnerId, item.getContents(), deliveryData, auctionId, "Won auction item");
            item.transitionTo(AuctionState.CLAIMED, "admin retried settlement and delivered item by " + (adminName == null || adminName.isBlank() ? "console" : adminName));
            markChanged("Auction storage marked dirty after admin settlement retry.");
            sendAuctionAlert(item.getPlayerId(), "Auction Settlement Recovered", itemName(item) + " was paid out after an admin retry.", "SUCCESS");
            sendAuctionAlert(winnerId, "Auction Won", itemName(item) + " was delivered after an admin settlement retry.", "SUCCESS");
            alertSubscribers(item, exclusions(item.getPlayerId(), winnerId), "Auction Settlement Recovered", itemName(item) + " was recovered by an admin.", "INFO");
            return AuctionActionResult.ok("Auction settlement retried, paid, and delivered.");
        }
    }

    public AuctionActionResult claimAuction(ServerPlayer player, UUID auctionId, AuctionDeliverySavedData deliveryData) {
        if (player == null) {
            return AuctionActionResult.fail("Only players can claim auction items.");
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

            UUID winnerId = item.getHighestBidderId();
            if (winnerId == null) {
                if (!player.getUUID().equals(item.getPlayerId())) {
                    return AuctionActionResult.fail("Only the seller can claim an unsold auction return.");
                }
                giveOrDeliver(player, item.getContents(), deliveryData, auctionId, "Expired unsold auction return");
                item.transitionTo(AuctionState.CLAIMED, "seller claimed unsold return");
                markChanged("Auction storage marked dirty after seller return claim.");
                notifyAuctionEndedUnsold(item, player.getUUID());
                return AuctionActionResult.ok("Unsold item returned.");
            }

            if (!player.getUUID().equals(winnerId)) {
                return AuctionActionResult.fail("Only the winning bidder can claim this auction.");
            }
            SettlementResult settlement = settleHeldBid(item);
            if (!settlement.success()) {
                sendAuctionAlert(item.getPlayerId(), "Auction Settlement Failed", itemName(item) + " could not pay out to your account.", "ERROR");
                return AuctionActionResult.fail(settlement.message());
            }
            if (item.getBuyoutPrice().isEmpty() || item.getHighestBid().compareTo(item.getBuyoutPrice().get()) < 0) {
                notifyAuctionSold(item, player.getUUID(), item.getHighestBid());
            }
            giveOrDeliver(player, item.getContents(), deliveryData, auctionId, "Won auction item");
            item.transitionTo(AuctionState.CLAIMED, "winner claimed auction item");
            markChanged("Auction storage marked dirty after winner claim.");
            return AuctionActionResult.ok("Auction item claimed.");
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
        boolean subscribed = item.toggleNotificationSubscriber(player.getUUID());
        markChanged("Auction storage marked dirty after notification subscription change.");
        return AuctionActionResult.ok(subscribed ? "Auction notifications enabled." : "Auction notifications disabled.");
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
            sendAuctionAlert(sellerId, "New Auction Bid", bidderName + " bid " + bidAmount + " on " + itemName + ".", "INFO");
        }
        if (previousBidderId != null && !previousBidderId.equals(bidderId)) {
            sendAuctionAlert(previousBidderId, "You Were Outbid", "You were outbid on " + itemName + ". Your previous bid of " + moneyLabel(previousAmount) + " was refunded. New bid: " + bidAmount + ".", "WARNING");
        }

        Set<UUID> excluded = exclusions(bidderId, sellerId, previousBidderId);
        alertSubscribers(item, excluded, "Auction Updated", bidderName + " bid " + bidAmount + " on " + itemName + ".", "INFO");
    }

    private void notifyAuctionSold(AuctionItem item, UUID buyerId, BigDecimal amount) {
        String itemName = itemName(item);
        String saleAmount = moneyLabel(amount);
        String buyerName = playerName(buyerId);
        UUID sellerId = item.getPlayerId();

        if (buyerId != null) {
            sendAuctionAlert(buyerId, "Auction Won", "You won " + itemName + " for " + saleAmount + ".", "SUCCESS");
        }
        if (sellerId != null && !sellerId.equals(buyerId)) {
            sendAuctionAlert(sellerId, "Auction Sold", itemName + " sold to " + buyerName + " for " + saleAmount + ".", "SUCCESS");
        }

        Set<UUID> losingBidders = new HashSet<>(item.getBids().keySet());
        losingBidders.remove(buyerId);
        for (UUID losingBidderId : losingBidders) {
            sendAuctionAlert(losingBidderId, "Auction Sold", itemName + " was sold to another player for " + saleAmount + ".", "WARNING");
        }

        Set<UUID> excluded = exclusions(buyerId, sellerId);
        excluded.addAll(losingBidders);
        alertSubscribers(item, excluded, "Auction Sold", itemName + " sold for " + saleAmount + ".", "INFO");
    }

    private void notifyAuctionCancelled(AuctionItem item, UUID sellerId) {
        alertSubscribers(item, exclusions(sellerId), "Auction Cancelled", itemName(item) + " was cancelled by the seller.", "WARNING");
    }

    private void notifyAuctionEndedUnsold(AuctionItem item, UUID sellerId) {
        alertSubscribers(item, exclusions(sellerId), "Auction Ended", itemName(item) + " ended without a buyer.", "INFO");
    }

    private void alertSubscribers(AuctionItem item, Set<UUID> excluded, String title, String message, String tone) {
        if (item == null) {
            return;
        }
        Set<UUID> safeExcluded = excluded == null ? Set.of() : excluded;
        for (UUID subscriberId : item.getNotificationSubscribers()) {
            if (subscriberId != null && !safeExcluded.contains(subscriberId)) {
                sendAuctionAlert(subscriberId, title, message, tone);
            }
        }
    }

    private void sendAuctionAlert(UUID playerId, String title, String message, String tone) {
        if (playerId == null || message == null || message.isBlank()) {
            return;
        }
        UasAlertResult result = switch (tone == null ? "" : tone) {
            case "SUCCESS" -> bankingService.sendSuccessAlert(playerId, title, message, ALERT_DURATION_MS);
            case "ERROR" -> bankingService.sendErrorAlert(playerId, title, message, ALERT_DURATION_MS);
            case "WARNING" -> bankingService.sendWarningAlert(playerId, title, message, ALERT_DURATION_MS);
            default -> bankingService.sendInfoAlert(playerId, title, message, ALERT_DURATION_MS);
        };
        if (result == null || !result.success()) {
            sendFallbackSystemMessage(playerId, message, toneColor(tone));
        }
    }

    private void sendFallbackSystemMessage(UUID playerId, String message, ChatFormatting color) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer player = server == null || playerId == null ? null : server.getPlayerList().getPlayer(playerId);
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

    private void alertOnlineAdmins(String title, String message, String tone) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || message == null || message.isBlank()) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Admin alert while no server is available: {}", message);
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && player.hasPermissions(Config.adminStatusPermissionLevel)) {
                sendAuctionAlert(player.getUUID(), title, message, tone);
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
        boolean resolvedAdminMode = adminMode && viewer != null && viewer.hasPermissions(Config.adminStatusPermissionLevel);
        AuctionUiQuery safeQuery = query == null ? AuctionUiQuery.defaults() : query;
        List<AuctionListingSummary> all = getAuctionItems().values().stream()
                .map(item -> toSummary(item, viewer))
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

        UasAccountSnapshot primaryAccount = null;
        if (viewerId != null) {
            primaryAccount = bankingService.getPrimaryAccountId(viewerId)
                    .flatMap(bankingService::getAccountSnapshot)
                    .orElse(null);
        }
        List<AuctionDeliveryEntry> deliveries = viewerId == null || deliveryData == null
                ? List.of()
                : deliveryData.getDeliveries(viewerId);
        AuctionListingPreview pendingListing = viewerId == null
                ? null
                : getPendingListingPreview(viewerId).orElse(null);
        AuctionAdminDashboardSnapshot adminDashboard = resolvedAdminMode
                ? buildAdminDashboard(all, adminSavedData(viewer))
                : AuctionAdminDashboardSnapshot.empty();
        return new AuctionHouseSnapshot(browse, myBids, myAuctions, deliveries, modFilters, primaryAccount, pendingListing, Config.listingFeeRate, message == null ? "" : message, success, resolvedAdminMode, adminDashboard);
    }

    private AuctionAdminSavedData adminSavedData(ServerPlayer viewer) {
        if (viewer == null || viewer.getServer() == null) {
            return null;
        }
        try {
            return AuctionAdminSavedData.get(viewer.getServer());
        } catch (RuntimeException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Admin dashboard saved data unavailable: {}", exception.getMessage());
            return null;
        }
    }

    private AuctionAdminDashboardSnapshot buildAdminDashboard(List<AuctionListingSummary> all,
                                                             AuctionAdminSavedData adminData) {
        List<AuctionListingSummary> safeAll = all == null ? List.of() : all;
        List<AuctionAdminDashboardSnapshot.Stats> stats = List.of(
                adminStats("24h", safeAll, LocalDateTime.now().minusHours(24)),
                adminStats("7d", safeAll, LocalDateTime.now().minusDays(7)),
                adminStats("All", safeAll, null)
        );
        List<AuctionPlayerBan> bans = adminData == null ? List.of() : adminData.getBans();
        List<AuctionAdminAuditEntry> audit = adminData == null ? List.of() : adminData.getAuditLog().stream().limit(80).toList();
        List<AuctionAdminDashboardSnapshot.Player> players = adminPlayers(safeAll, adminData, bans);
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
        return new AuctionAdminDashboardSnapshot(stats, players, bans, audit, bannedEntries, restrictedListings, failedSettlements, LocalDateTime.now().toString());
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
                                                                    List<AuctionPlayerBan> bans) {
        Map<UUID, AdminPlayerAccumulator> players = new HashMap<>();
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

        return players.values().stream()
                .sorted(Comparator.comparingInt(AdminPlayerAccumulator::score).reversed().thenComparing(accumulator -> accumulator.name.toLowerCase(Locale.ROOT)))
                .limit(80)
                .map(accumulator -> toAdminPlayer(accumulator, adminData))
                .toList();
    }

    private AuctionAdminDashboardSnapshot.Player toAdminPlayer(AdminPlayerAccumulator accumulator,
                                                               AuctionAdminSavedData adminData) {
        AuctionPlayerBan ban = adminData == null ? null : adminData.getBan(accumulator.playerId).orElse(null);
        boolean active = ban != null && ban.active();
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
                active && ban.blockCreate(),
                active && ban.blockBid(),
                active && ban.blockBuyout(),
                active && ban.blockWatch(),
                active ? ban.reason() : "",
                active ? ban.expiresAt().map(LocalDateTime::toString).orElse("Never") : "",
                active
        );
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
        if  (this.AuctionItems.containsKey(id)) {
            return this.AuctionItems.get(id);
        }
        return null;
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
                .append(Component.literal("Auction created: ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(item.getAuctionId().toString()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" "))
                .append(Component.literal("[Open /ah]").withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ah"))))
                .append(Component.literal(" "))
                .append(Component.literal("[My Auctions]").withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ah mine active"))));
        player.sendSystemMessage(message, false);
    }

    private String pendingPreviewMessage(PendingAuctionListing pending) {
        AuctionListingPreview preview = pending.toPreview();
        String buyout = preview.buyoutPrice().compareTo(BigDecimal.ZERO) > 0
                ? moneyLabel(preview.buyoutPrice())
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

    private String feeFailureMessage(String feeLabel, BigDecimal required, UasBankingResult result) {
        String current = result == null ? "unknown" : moneyLabel(result.balanceAfter());
        String reason = result == null ? "UBS returned no result" : result.reason();
        return "Your UBS primary account cannot pay the " + feeLabel
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

        UUID winningBidderId = item.getHighestBidderId();
        if (winningBidderId == null) {
            UltimateAuctionSystem.LOGGER.info("Auction {} expired without bids; no UBS payout was created.", id);
            item.transitionTo(AuctionState.ENDED, "auction ended without bids");
            sendAuctionAlert(item.getPlayerId(), "Auction Ended", itemName(item) + " ended without a buyer.", "INFO");
            notifyAuctionEndedUnsold(item, item.getPlayerId());
            return;
        }

        if (!bankingService.isAvailable()) {
            UltimateAuctionSystem.LOGGER.warn("UBS is not available; cannot settle auction {}.", id);
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "UBS unavailable during settlement");
            sendAuctionAlert(item.getPlayerId(), "Auction Settlement Delayed", itemName(item) + " sold but UBS is unavailable for payout.", "WARNING");
            sendAuctionAlert(winningBidderId, "Auction Settlement Delayed", itemName(item) + " is waiting for payment settlement.", "WARNING");
            return;
        }

        UUID sellerAccountId = item.getSellerAccountId();
        if (sellerAccountId == null) {
            UltimateAuctionSystem.LOGGER.warn("Auction {} has no stored seller account ID; cannot settle seller {}.", id, item.getPlayerId());
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "missing seller account during settlement");
            sendAuctionAlert(item.getPlayerId(), "Auction Settlement Failed", itemName(item) + " has no seller account for payout.", "ERROR");
            return;
        }

        Optional<AuctionBidRecord> winningBidRecord = item.getWinningBidRecord();
        UUID winningBidderAccountId = winningBidRecord.flatMap(AuctionBidRecord::getBidderAccountId).orElse(null);
        if (winningBidderAccountId == null) {
            UltimateAuctionSystem.LOGGER.warn("Auction {} has no auditable winning bid account; cannot settle.", id);
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "missing winning bidder account during settlement");
            sendAuctionAlert(winningBidderId, "Auction Settlement Failed", itemName(item) + " is missing your winning bid account.", "ERROR");
            return;
        }

        SettlementResult settlement = settleHeldBid(item);
        if (!settlement.success()) {
            UltimateAuctionSystem.LOGGER.warn("UBS auction settlement failed for {}: {}", id, settlement.message());
            sendAuctionAlert(item.getPlayerId(), "Auction Settlement Failed", itemName(item) + " could not pay out: " + settlement.message(), "ERROR");
            sendAuctionAlert(winningBidderId, "Auction Settlement Failed", itemName(item) + " could not finish payment settlement.", "ERROR");
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
                                                UUID sellerAccountId,
                                                String escrowSource) {
        return activateAuction(auctionId, player, List.of(escrowStack), "", description, end, start, startingBidPrice, buyoutPrice, sellerAccountId, escrowSource);
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
                                                UUID sellerAccountId,
                                                String escrowSource) {
        List<ItemStack> safeStacks = safeItemStacks(escrowStacks);
        AuctionItem item = new AuctionItem(auctionId, safeStacks, title, description, end, start, startingBidPrice, player.getUUID(), sellerAccountId, buyoutPrice);
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
        sendAuctionCreatedMessage(player, item);
        return AuctionActionResult.ok("Auction created: " + item.getAuctionId());
    }

    private AuctionActionResult validateListingRequest(ServerPlayer player,
                                                       ItemStack stack,
                                                       BigDecimal startingBidPrice,
                                                       BigDecimal buyoutPrice,
                                                       LocalDateTime end) {
        return validateListingRequest(player, stack == null || stack.isEmpty() ? List.of() : List.of(stack), startingBidPrice, buyoutPrice, end);
    }

    private AuctionActionResult validateListingRequest(ServerPlayer player,
                                                       List<ItemStack> stacks,
                                                       BigDecimal startingBidPrice,
                                                       BigDecimal buyoutPrice,
                                                       LocalDateTime end) {
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
        if (normalizedBuyout.compareTo(BigDecimal.ZERO) > 0 && normalizedBuyout.compareTo(startingBid) < 0) {
            return AuctionActionResult.fail("Buyout price must be at least the starting bid.");
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
        Optional<UUID> sellerAccountId = bankingService.getPrimaryAccountId(player.getUUID());
        if (Config.requireUbsForListing && sellerAccountId.isEmpty()) {
            return AuctionActionResult.fail(accountSetupMessage(player.getUUID()));
        }
        if (sellerAccountId.isPresent()) {
            Optional<UasAccountSnapshot> snapshot = bankingService.getAccountSnapshot(sellerAccountId.get());
            if (snapshot.isPresent() && snapshot.get().frozen()) {
                String reason = snapshot.get().frozenReason().isBlank() ? "no reason provided" : snapshot.get().frozenReason();
                return AuctionActionResult.fail("Your UBS primary account is frozen and cannot list auctions right now: " + reason);
            }
            UasBankingResult canReceive = bankingService.validateCanReceive(sellerAccountId.get());
            if (!canReceive.success()) {
                return AuctionActionResult.fail("Your UBS primary account cannot receive auction payouts right now: " + canReceive.reason());
            }
            BigDecimal listingFee = Config.calculateListingFee(startingBid);
            if (listingFee.compareTo(BigDecimal.ZERO) > 0) {
                UasBankingResult canPayFee = bankingService.validateCanSend(sellerAccountId.get(), listingFee);
                if (!canPayFee.success()) {
                    return AuctionActionResult.fail(feeFailureMessage("listing fee", listingFee, canPayFee));
                }
            }
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
            UasBankingResult failure = UasBankingResult.fail("Missing seller account", BigDecimal.ZERO);
            recordBankingEvent(item, EVENT_AUCTION_PAYOUT, net, payoutReference, failure);
            alertOnlineAdmins("Auction Settlement Failed", "Auction " + item.getAuctionId() + " is missing a seller account for payout.", "ERROR");
            return SettlementResult.fail("Auction settlement failed: missing seller account.", gross, salesTax, net);
        }
        UasBankingResult canReceive = bankingService.validateCanReceive(sellerAccountId);
        if (!canReceive.success()) {
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "seller account cannot receive payout: " + canReceive.reason());
            recordBankingEvent(item, EVENT_AUCTION_PAYOUT, net, payoutReference, canReceive);
            alertOnlineAdmins("Auction Settlement Failed", "Auction " + item.getAuctionId() + " seller account cannot receive payout: " + canReceive.reason(), "ERROR");
            return SettlementResult.fail("Auction settlement failed: seller account cannot receive payout: " + canReceive.reason(), gross, salesTax, net);
        }

        UasBankingResult deposit = net.compareTo(BigDecimal.ZERO) > 0
                ? bankingService.deposit(sellerAccountId, net, payoutReference)
                : UasBankingResult.ok(BigDecimal.ZERO, null, payoutReference);
        recordBankingEvent(item, EVENT_AUCTION_PAYOUT, net, payoutReference, deposit);
        item.getWinningBidRecord().ifPresent(record -> record.linkSettlement(payoutReference, deposit));
        if (!deposit.success()) {
            item.transitionTo(AuctionState.FAILED_SETTLEMENT, "UBS payout failed: " + deposit.reason());
            alertOnlineAdmins("Auction Settlement Failed", "Auction " + item.getAuctionId() + " seller payout failed: " + deposit.reason(), "ERROR");
            return SettlementResult.fail("Auction settlement failed: " + deposit.reason(), gross, salesTax, net);
        }

        if (salesTax.compareTo(BigDecimal.ZERO) > 0) {
            recordManualFinancialEvent(
                    item,
                    EVENT_SALES_TAX,
                    salesTax,
                    auctionReference(EVENT_SALES_TAX, item.getAuctionId()),
                    true,
                    "Deducted from seller payout"
            );
        }
        sendAuctionAlert(
                item.getPlayerId(),
                "Auction Payout",
                itemName(item) + " payout: gross " + moneyLabel(gross) + ", tax " + moneyLabel(salesTax) + ", net " + moneyLabel(net) + ". Auction " + item.getAuctionId() + ".",
                "SUCCESS"
        );
        return SettlementResult.ok("Auction payout settled.", gross, salesTax, net);
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

    private AuctionListingSummary toSummary(AuctionItem item, ServerPlayer viewer) {
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
        boolean viewerIsHighestBidder = viewerId != null && viewerId.equals(item.getHighestBidderId());
        boolean viewerHasBid = viewerId != null && item.getBids().containsKey(viewerId);
        boolean viewerReceivesNotifications = viewerId != null && item.isNotificationSubscriber(viewerId);
        int notificationSubscriberCount = item.getNotificationSubscribers().size();
        boolean active = item.getState() == AuctionState.ACTIVE && !item.isExpired();
        boolean buyoutAvailable = item.getBuyoutPrice()
                .map(price -> item.getHighestBidderId() == null || item.getHighestBid().compareTo(price) < 0)
                .orElse(false);
        boolean canClaim = viewerId != null && canViewerClaimAuction(
                item.getState(),
                item.isExpired(),
                viewerIsHighestBidder,
                viewerIsSeller,
                item.getHighestBidderId() != null
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
                item.getHighestBid(),
                item.getBuyoutPrice().orElse(BigDecimal.ZERO),
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
                bidRecords,
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
            String contentNames = summary.contents().stream()
                    .map(stack -> stack.getHoverName().getString())
                    .reduce("", (left, right) -> left + " " + right);
            String haystack = (summary.itemName() + " " + contentNames + " " + summary.sellerName() + " " + summary.description()).toLowerCase(Locale.ROOT);
            if (!haystack.contains(search)) {
                return false;
            }
        }
        if (query.minimumPrice() != null && query.minimumPrice().compareTo(BigDecimal.ZERO) > 0
                && summary.currentBid().compareTo(query.minimumPrice()) < 0) {
            return false;
        }
        if (query.maximumPrice() != null && query.maximumPrice().compareTo(BigDecimal.ZERO) > 0
                && summary.currentBid().compareTo(query.maximumPrice()) > 0) {
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

    private String itemModId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId == null ? "" : itemId.getNamespace().toLowerCase(Locale.ROOT);
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

    private BigDecimal safeMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private boolean hasFractionalDollars(BigDecimal amount) {
        return amount != null && amount.stripTrailingZeros().scale() > 0;
    }
}
