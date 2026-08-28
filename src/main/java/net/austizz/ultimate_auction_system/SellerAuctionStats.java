package net.austizz.ultimate_auction_system;

import java.util.UUID;

public record SellerAuctionStats(
        UUID sellerId,
        int active,
        int sold,
        int cancelled,
        int expired,
        int total,
        int activeLimit
) {
}
