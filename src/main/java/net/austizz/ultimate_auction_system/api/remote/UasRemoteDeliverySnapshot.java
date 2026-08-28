package net.austizz.ultimate_auction_system.api.remote;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UasRemoteDeliverySnapshot(UUID deliveryId, UUID playerId, UUID auctionId, List<UasRemoteItemSnapshot> items, Instant createdAt) {
    public UasRemoteDeliverySnapshot {
        items = items == null ? List.of() : List.copyOf(items);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public int itemCount() {
        return items.size();
    }
}
