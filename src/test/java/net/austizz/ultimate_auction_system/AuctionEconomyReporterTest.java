package net.austizz.ultimate_auction_system;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuctionEconomyReporterTest {
    @Test
    void reportUsesPersistedFinancialEventsForSalesFeesTaxesAndFailures() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 8, 12, 0);
        UUID sellerId = UUID.randomUUID();
        UUID soldAuctionId = UUID.randomUUID();
        AuctionEconomyReporter.Source sold = new AuctionEconomyReporter.Source(
                sellerId,
                "Diamond Sword",
                AuctionCategory.WEAPONS.label(),
                AuctionState.CLAIMED,
                new BigDecimal("100"),
                now.minusDays(1),
                now.minusHours(2),
                List.of(
                        event(soldAuctionId, AuctionHouse.EVENT_LISTING_FEE, "5", true, now.minusHours(3)),
                        event(soldAuctionId, AuctionHouse.EVENT_SALES_TAX, "10", true, now.minusHours(2)),
                        event(soldAuctionId, AuctionHouse.EVENT_AUCTION_PAYOUT, "90", true, now.minusHours(2))
                )
        );

        UUID failedAuctionId = UUID.randomUUID();
        AuctionEconomyReporter.Source failed = new AuctionEconomyReporter.Source(
                sellerId,
                "Failed item",
                AuctionCategory.MISC.label(),
                AuctionState.FAILED_SETTLEMENT,
                new BigDecimal("20"),
                now.minusHours(4),
                now.minusHours(1),
                List.of(event(failedAuctionId, AuctionHouse.EVENT_AUCTION_PAYOUT, "20", false, now.minusHours(1)))
        );

        AuctionEconomyReporter.Source active = new AuctionEconomyReporter.Source(
                sellerId,
                "Active item",
                AuctionCategory.MISC.label(),
                AuctionState.ACTIVE,
                new BigDecimal("15"),
                now.minusMinutes(30),
                now.minusMinutes(30),
                List.of()
        );

        AuctionEconomyReport day = new AuctionEconomyReporter()
                .buildReportsFromSources(List.of(sold, failed, active), id -> id.equals(sellerId) ? "SellerOne" : "Other", now)
                .getFirst();

        assertEquals("24h", day.label());
        assertEquals(1, day.activeListings());
        assertEquals(1, day.completedSales());
        assertEquals(1, day.failedSettlements());
        assertEquals("$100", day.grossVolume());
        assertEquals("$5", day.fees());
        assertEquals("$10", day.taxes());
        assertFalse(day.topSellers().isEmpty());
        assertEquals("SellerOne", day.topSellers().getFirst().label());
        assertEquals("$100", day.topSellers().getFirst().amount());
    }

    private AuctionFinancialEvent event(UUID auctionId, String type, String amount, boolean success, LocalDateTime createdAt) {
        return new AuctionFinancialEvent(
                UUID.randomUUID(),
                auctionId,
                type,
                AuctionHouse.auctionReference(type, auctionId),
                new BigDecimal(amount),
                success,
                success ? UUID.randomUUID() : null,
                success ? "ok" : "failed",
                createdAt
        );
    }
}
