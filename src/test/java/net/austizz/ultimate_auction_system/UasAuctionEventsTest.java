package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.api.UasAuctionSnapshot;
import net.austizz.ultimate_auction_system.api.event.UasAuctionEvents;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UasAuctionEventsTest {
    @Test
    void eventsExposeSnapshotAndStableActorFields() {
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID bidderId = UUID.randomUUID();
        UasAuctionSnapshot snapshot = new UasAuctionSnapshot(
                auctionId,
                sellerId,
                "Bundle",
                "Test auction",
                AuctionState.ACTIVE,
                BigDecimal.TEN,
                new BigDecimal("15"),
                BigDecimal.ZERO,
                1,
                bidderId,
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(2),
                true,
                2,
                List.of()
        );

        UasAuctionEvents.BidAccepted event = new UasAuctionEvents.BidAccepted(
                snapshot,
                bidderId,
                new BigDecimal("15"),
                null,
                BigDecimal.ZERO,
                false
        );

        assertEquals(auctionId, event.auctionId());
        assertEquals(snapshot, event.auction());
        assertEquals(bidderId, event.bidderId());
        assertEquals(new BigDecimal("15"), event.amount());
        assertFalse(event.completedByBuyout());
    }
}
