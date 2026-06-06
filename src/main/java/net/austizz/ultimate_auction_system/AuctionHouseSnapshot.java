package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.UasAccountSnapshot;

import java.util.List;

public record AuctionHouseSnapshot(
        List<AuctionListingSummary> browseListings,
        List<AuctionListingSummary> myBids,
        List<AuctionListingSummary> myAuctions,
        List<AuctionDeliveryEntry> deliveries,
        UasAccountSnapshot primaryAccount,
        AuctionListingPreview pendingListing,
        double listingFeeRate,
        String message,
        boolean success,
        boolean adminMode
) {
}
