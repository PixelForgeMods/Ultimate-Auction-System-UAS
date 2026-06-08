package net.austizz.ultimate_auction_system.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record UasCreateAuctionRequest(
        List<Integer> inventorySlots,
        String title,
        String description,
        BigDecimal startingBid,
        BigDecimal buyoutPrice,
        LocalDateTime endsAt
) {
    public UasCreateAuctionRequest {
        inventorySlots = inventorySlots == null ? List.of() : inventorySlots.stream().filter(slot -> slot != null && slot >= 0).distinct().toList();
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        startingBid = startingBid == null ? BigDecimal.ZERO : startingBid;
        buyoutPrice = buyoutPrice == null ? BigDecimal.ZERO : buyoutPrice;
    }
}
