package net.austizz.ultimate_auction_system;

import java.util.List;
import java.util.UUID;

public record AuctionAdminDashboardSnapshot(
        List<Stats> stats,
        List<AuctionEconomyReport> economyReports,
        List<Player> players,
        List<AuctionPlayerBan> bans,
        List<AuctionAdminAuditEntry> auditLog,
        List<BannedEntry> bannedEntries,
        List<AuctionSuspicionSignal> suspicionSignals,
        List<AuctionRecoveryEntry> recoveryEntries,
        List<AuctionListingSummary> restrictedListings,
        List<AuctionListingSummary> failedSettlements,
        String generatedAt
) {
    public static AuctionAdminDashboardSnapshot empty() {
        return new AuctionAdminDashboardSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "");
    }

    public record Stats(
            String label,
            int auctionsCreated,
            int activeAuctions,
            int soldAuctions,
            int cancelledAuctions,
            int failedSettlements,
            int activeSellers,
            int activeBidders,
            String bidVolume,
            String soldValue,
            String estimatedListingFees,
            String estimatedSalesTax,
            String averageSale
    ) {
    }

    public record Player(
            UUID playerId,
            String playerName,
            int activeListings,
            int maxActiveListings,
            int bidCount,
            int soldCount,
            int boughtCount,
            int cancelledCount,
            String bidVolume,
            String soldValue,
            int deliveryCount,
            String deliveryPreview,
            boolean blockCreate,
            boolean blockBid,
            boolean blockBuyout,
            boolean blockWatch,
            String banReason,
            String banExpiresAt,
            boolean banActive
    ) {
    }

    public record BannedEntry(
            String entry,
            String type,
            String label,
            int matchingActiveAuctions
    ) {
    }
}
