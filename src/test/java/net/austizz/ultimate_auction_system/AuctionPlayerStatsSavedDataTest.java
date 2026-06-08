package net.austizz.ultimate_auction_system;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionPlayerStatsSavedDataTest {
    @Test
    void listingAndSaleStatsAreIdempotentPerAuctionId() {
        AuctionPlayerStatsSavedData statsData = new AuctionPlayerStatsSavedData();
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        assertTrue(statsData.recordListing(auctionId, sellerId, "Seller"));
        assertFalse(statsData.recordListing(auctionId, sellerId, "Seller"));
        assertTrue(statsData.recordSale(auctionId, sellerId, "Seller", buyerId, "Buyer", new BigDecimal("125")));
        assertFalse(statsData.recordSale(auctionId, sellerId, "Seller", buyerId, "Buyer", new BigDecimal("125")));

        AuctionPlayerStats seller = statsData.statsFor(sellerId, "Seller");
        AuctionPlayerStats buyer = statsData.statsFor(buyerId, "Buyer");

        assertEquals(1, seller.auctionsListed());
        assertEquals(new BigDecimal("125"), seller.grossSoldValue());
        assertEquals(1, buyer.auctionsWon());
        assertEquals(new BigDecimal("125"), buyer.grossSpentValue());
    }

    @Test
    void ranksUseStoredUuidIdentity() {
        AuctionPlayerStatsSavedData statsData = new AuctionPlayerStatsSavedData();
        UUID firstSeller = UUID.randomUUID();
        UUID secondSeller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        statsData.recordListing(UUID.randomUUID(), firstSeller, "First");
        statsData.recordListing(UUID.randomUUID(), secondSeller, "Second");
        statsData.recordSale(UUID.randomUUID(), firstSeller, "First", buyer, "Buyer", new BigDecimal("10"));
        statsData.recordSale(UUID.randomUUID(), secondSeller, "Second", buyer, "Buyer", new BigDecimal("50"));

        assertEquals(1, statsData.sellerRank(secondSeller));
        assertEquals(2, statsData.sellerRank(firstSeller));
        assertEquals(1, statsData.buyerRank(buyer));
        assertEquals(1, statsData.topSellers(1).size());
        assertEquals(secondSeller, statsData.topSellers(1).getFirst().playerId());
    }
}
