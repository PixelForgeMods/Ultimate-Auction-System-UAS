package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.UasBankingResult;
import net.austizz.ultimate_auction_system.banking.UasBankingService;
import net.austizz.ultimate_auction_system.banking.UbsBankingService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionHouse {

    private final ConcurrentHashMap<UUID, AuctionItem> AuctionItems;
    private final UasBankingService bankingService;
    private final AuctionSavedData savedData;
    private volatile AuctionStorageHealth storageHealth = AuctionStorageHealth.inMemoryOnly();

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
        this.AuctionItems.values().forEach(this::attachMutationTracking);
    }

    public static AuctionHouse load(MinecraftServer server) {
        AuctionSavedData data = AuctionSavedData.get(server);
        AuctionHouse house = new AuctionHouse(data, new UbsBankingService());
        String message = data.getSkippedRecords() == 0 && data.getRepairedRecords() == 0
                ? "Persistent auction storage loaded with " + data.getAuctions().size() + " auction(s)."
                : "Persistent auction storage loaded with " + data.getAuctions().size()
                + " auction(s); skipped " + data.getSkippedRecords()
                + " invalid record(s), repaired " + data.getRepairedRecords() + " record(s).";
        house.markStorageSaved(message);
        return house;
    }

    public void addAuctionItem(AuctionItem item) {
        if (item == null) {
            return;
        }

        ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(item.getPlayerId());
        Optional<UUID> sellerAccountId = bankingService.getPrimaryAccountId(item.getPlayerId());
        if (sellerAccountId.isEmpty()) {
            sendListingError(player, "Error: UAS could not resolve your UBS primary account ID, so the auction record cannot be audited safely.");
            return;
        }
        UasBankingResult canReceive = bankingService.validateCanReceive(sellerAccountId.get());
        if (!canReceive.success()) {
            sendListingError(player, "Error: Your UBS primary account cannot receive auction payouts right now: " + canReceive.reason());
            return;
        }

        item.setSellerAccountId(sellerAccountId.get());
        Optional<String> validationError = item.validateForActivation();
        if (validationError.isPresent()) {
            sendListingError(player, "Error: Auction listing is incomplete: " + validationError.get() + ".");
            return;
        }

        attachMutationTracking(item);
        this.AuctionItems.put(item.getAuctionId(), item);
        markChanged("Auction storage marked dirty after listing creation.");
        if (player != null) {
            Component message = Component.literal("")
                    .append(Component.literal("⚖ ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("Auction: ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(Component.literal("Successfully listed ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(item.getItem().getCount() + "x " + item.getItem().getHoverName().getString()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("$" + item.getStartingBidPrice()).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(". -> Type /ah to view your listing!").withStyle(ChatFormatting.DARK_GRAY));
            player.sendSystemMessage(message, false);
        }
    }

    public void removeAuctionItem(AuctionItem item) {
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

    public AuctionStorageHealth getStorageHealth() {
        return storageHealth;
    }

    public void markStorageSaved(String message) {
        this.storageHealth = AuctionStorageHealth.saved(message);
    }

    public void markStorageFailed(String message) {
        this.storageHealth = AuctionStorageHealth.failed(message);
    }

    public boolean placeBid(UUID auctionId, UUID bidderId, BigDecimal amount) {
        AuctionItem item = getAuctionItem(auctionId);
        if (item == null) {
            return false;
        }
        Optional<UUID> bidderAccountId = bankingService.getPrimaryAccountId(bidderId);
        if (bidderAccountId.isEmpty()) {
            return item.recordRejectedBid(
                    bidderId,
                    null,
                    amount,
                    AuctionBidResult.REJECTED_NO_ACCOUNT,
                    "Bidder has no UBS primary account."
            ).isAccepted();
        }

        UasBankingResult canSend = bankingService.validateCanSend(bidderAccountId.get(), amount);
        if (!canSend.success()) {
            return item.recordRejectedBid(
                    bidderId,
                    bidderAccountId.get(),
                    amount,
                    AuctionBidResult.REJECTED_ACCOUNT_UNAVAILABLE,
                    canSend.reason()
            ).isAccepted();
        }

        return item.recordBid(bidderId, bidderAccountId.get(), amount).isAccepted();
    }

    public boolean saveNow(MinecraftServer server, String reason) {
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

    private void markChanged(String message) {
        if (savedData != null) {
            savedData.markChanged();
            markStorageSaved(message);
        }
    }

    private void attachMutationTracking(AuctionItem item) {
        if (item != null) {
            item.setChangeListener(() -> markChanged("Auction storage marked dirty after auction record mutation."));
        }
    }

    private void sendListingError(ServerPlayer player, String message) {
        if (player != null) {
            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
        }
    }

    private void refreshExpiredStates() {
        for (AuctionItem item : AuctionItems.values()) {
            if (item != null && item.getState() == AuctionState.ACTIVE && item.isExpired()) {
                item.setState(AuctionState.ENDED);
            }
        }
    }

    public void payoutAuctionItem(UUID id) {
        AuctionItem item = getAuctionItem(id);
        if (item == null || !item.isExpired()) {
            return;
        }

        UUID winningBidderId = item.getHighestBidderId();
        if (winningBidderId == null) {
            UltimateAuctionSystem.LOGGER.info("Auction {} expired without bids; no UBS payout was created.", id);
            item.setState(AuctionState.ENDED);
            return;
        }

        if (!bankingService.isAvailable()) {
            UltimateAuctionSystem.LOGGER.warn("UBS is not available; cannot settle auction {}.", id);
            item.setState(AuctionState.FAILED_SETTLEMENT);
            return;
        }

        UUID sellerAccountId = item.getSellerAccountId();
        if (sellerAccountId == null) {
            UltimateAuctionSystem.LOGGER.warn("Auction {} has no stored seller account ID; cannot settle seller {}.", id, item.getPlayerId());
            item.setState(AuctionState.FAILED_SETTLEMENT);
            return;
        }

        Optional<AuctionBidRecord> winningBidRecord = item.getWinningBidRecord();
        UUID winningBidderAccountId = winningBidRecord.flatMap(AuctionBidRecord::getBidderAccountId).orElse(null);
        if (winningBidderAccountId == null) {
            UltimateAuctionSystem.LOGGER.warn("Auction {} has no auditable winning bid account; cannot settle.", id);
            item.setState(AuctionState.FAILED_SETTLEMENT);
            return;
        }

        String settlementReference = "UAS_AUCTION_PAYOUT:" + id;
        UasBankingResult result = bankingService.transfer(
                winningBidderAccountId,
                sellerAccountId,
                item.getHighestBid(),
                settlementReference
        );
        item.linkWinningBidToSettlement(settlementReference, result);

        if (!result.success()) {
            UltimateAuctionSystem.LOGGER.warn("UBS auction settlement failed for {}: {}", id, result.reason());
            item.setState(AuctionState.FAILED_SETTLEMENT);
            return;
        }

        item.setState(AuctionState.CLAIMED);
        removeAuctionItem(item);
    }
}
