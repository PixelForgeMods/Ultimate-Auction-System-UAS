package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.UasBankingResult;
import net.austizz.ultimate_auction_system.banking.UasBankingService;
import net.austizz.ultimate_auction_system.banking.UbsBankingService;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionHouse {

    private final ConcurrentHashMap<UUID, AuctionItem> AuctionItems;
    private final UasBankingService bankingService;

    public AuctionHouse() {
        this(new UbsBankingService());
    }

    public AuctionHouse(UasBankingService bankingService) {
        this.AuctionItems = new ConcurrentHashMap<>();
        this.bankingService = bankingService;
    }

    public void addAuctionItem(AuctionItem item) {
        this.AuctionItems.put(item.getAuctionId(), item);
    }

    public void removeAuctionItem(AuctionItem item) {
        this.AuctionItems.remove(item.getAuctionId());
    }

    public AuctionItem getAuctionItem(UUID id) {
        if  (this.AuctionItems.containsKey(id)) {
            return this.AuctionItems.get(id);
        }
        return null;
    }

    public ConcurrentHashMap<UUID, AuctionItem> getAuctionItems() {
        return this.AuctionItems;
    }

    public void payoutAuctionItem(UUID id) {
        AuctionItem item = getAuctionItem(id);
        if (item == null || !item.isExpired()) {
            return;
        }

        UUID winningBidderId = item.getHighestBidderId();
        if (winningBidderId == null) {
            UltimateAuctionSystem.LOGGER.info("Auction {} expired without bids; no UBS payout was created.", id);
            return;
        }

        if (!bankingService.isAvailable()) {
            UltimateAuctionSystem.LOGGER.warn("UBS is not available; cannot settle auction {}.", id);
            return;
        }

        UUID sellerAccountId = bankingService.getPrimaryAccountId(item.getPlayerId()).orElse(null);
        if (sellerAccountId == null) {
            UltimateAuctionSystem.LOGGER.warn("Seller {} has no primary UBS account; cannot settle auction {}.", item.getPlayerId(), id);
            return;
        }

        UasBankingResult result = bankingService.transferFromPrimary(
                winningBidderId,
                sellerAccountId,
                item.getHighestBid(),
                "UAS_AUCTION_PAYOUT:" + id
        );

        if (!result.success()) {
            UltimateAuctionSystem.LOGGER.warn("UBS auction settlement failed for {}: {}", id, result.reason());
            return;
        }

        removeAuctionItem(item);
    }
}
