package net.austizz.ultimate_auction_system.api.remote;

import java.time.Instant;
import java.util.List;

public record UasRemoteSnapshot(String apiVersion, long revision, Instant generatedAt,
                                List<UasRemoteAuctionSnapshot> auctions,
                                List<UasRemoteDeliverySnapshot> deliveries) {
    public UasRemoteSnapshot {
        auctions = auctions == null ? List.of() : List.copyOf(auctions);
        deliveries = deliveries == null ? List.of() : List.copyOf(deliveries);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
    }

    public static UasRemoteSnapshot empty(String apiVersion) {
        return new UasRemoteSnapshot(apiVersion, 0L, Instant.now(), List.of(), List.of());
    }
}
