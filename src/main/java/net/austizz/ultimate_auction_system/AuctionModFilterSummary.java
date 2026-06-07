package net.austizz.ultimate_auction_system;

public record AuctionModFilterSummary(
        String modId,
        String displayName,
        int activeAuctionCount
) {
}
