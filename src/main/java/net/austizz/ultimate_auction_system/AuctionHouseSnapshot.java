package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.UasAccountSnapshot;

import java.util.List;

public record AuctionHouseSnapshot(
        List<AuctionListingSummary> browseListings,
        List<AuctionListingSummary> myBids,
        List<AuctionListingSummary> myAuctions,
        List<AuctionDeliveryEntry> deliveries,
        List<AuctionModFilterSummary> modFilters,
        UasAccountSnapshot primaryAccount,
        AuctionListingPreview pendingListing,
        double listingFeeRate,
        String message,
        boolean success,
        boolean adminMode,
        AuctionAdminDashboardSnapshot adminDashboard
) {
    public AuctionHouseSnapshot {
        adminDashboard = adminDashboard == null ? AuctionAdminDashboardSnapshot.empty() : adminDashboard;
    }

    public AuctionHouseSnapshot(List<AuctionListingSummary> browseListings,
                                List<AuctionListingSummary> myBids,
                                List<AuctionListingSummary> myAuctions,
                                List<AuctionDeliveryEntry> deliveries,
                                List<AuctionModFilterSummary> modFilters,
                                UasAccountSnapshot primaryAccount,
                                AuctionListingPreview pendingListing,
                                double listingFeeRate,
                                String message,
                                boolean success,
                                boolean adminMode) {
        this(
                browseListings,
                myBids,
                myAuctions,
                deliveries,
                modFilters,
                primaryAccount,
                pendingListing,
                listingFeeRate,
                message,
                success,
                adminMode,
                AuctionAdminDashboardSnapshot.empty()
        );
    }
}
