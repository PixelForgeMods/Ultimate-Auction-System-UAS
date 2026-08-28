package net.austizz.ultimate_auction_system.api;

import net.austizz.ultimate_auction_system.AuctionFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record UasCreateAuctionRequest(
        List<Integer> inventorySlots,
        String title,
        String description,
        BigDecimal startingBid,
        BigDecimal buyoutPrice,
        BigDecimal reservePrice,
        AuctionFormat format,
        LocalDateTime endsAt
) {
    public UasCreateAuctionRequest(List<Integer> inventorySlots,
                                   String title,
                                   String description,
                                   BigDecimal startingBid,
                                   BigDecimal buyoutPrice,
                                   LocalDateTime endsAt) {
        this(inventorySlots, title, description, startingBid, buyoutPrice, BigDecimal.ZERO, AuctionFormat.NORMAL, endsAt);
    }

    public UasCreateAuctionRequest(List<Integer> inventorySlots,
                                   String title,
                                   String description,
                                   BigDecimal startingBid,
                                   BigDecimal buyoutPrice,
                                   BigDecimal reservePrice,
                                   LocalDateTime endsAt) {
        this(inventorySlots, title, description, startingBid, buyoutPrice, reservePrice, AuctionFormat.NORMAL, endsAt);
    }

    public UasCreateAuctionRequest {
        inventorySlots = inventorySlots == null ? List.of() : inventorySlots.stream().filter(slot -> slot != null && slot >= 0).distinct().toList();
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        startingBid = startingBid == null ? BigDecimal.ZERO : startingBid;
        buyoutPrice = buyoutPrice == null ? BigDecimal.ZERO : buyoutPrice;
        reservePrice = reservePrice == null ? BigDecimal.ZERO : reservePrice;
        format = format == null ? AuctionFormat.NORMAL : format;
    }
}
