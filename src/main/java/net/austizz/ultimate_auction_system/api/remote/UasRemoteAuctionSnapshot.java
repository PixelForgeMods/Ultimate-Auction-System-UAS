package net.austizz.ultimate_auction_system.api.remote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UasRemoteAuctionSnapshot(UUID auctionId, UUID sellerId, String title, String description,
                                       String state, String format, BigDecimal startingBid, BigDecimal currentBid,
                                       BigDecimal buyoutPrice, BigDecimal reservePrice, boolean hasReserve,
                                       boolean reservePriceHidden, boolean reserveMet, int acceptedBidCount,
                                       UUID highestBidderId, Instant createdAt, Instant updatedAt, Instant endsAt,
                                       boolean bundle, int totalItemCount, List<UasRemoteItemSnapshot> contents,
                                       String itemEscrowReference, UUID claimPlayerId, boolean claimRequiredInGame) {
    public UasRemoteAuctionSnapshot {
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        state = state == null ? "" : state;
        format = format == null ? "" : format;
        startingBid = startingBid == null ? BigDecimal.ZERO : startingBid;
        currentBid = currentBid == null ? BigDecimal.ZERO : currentBid;
        buyoutPrice = buyoutPrice == null ? BigDecimal.ZERO : buyoutPrice;
        reservePrice = reservePrice == null ? BigDecimal.ZERO : reservePrice;
        contents = contents == null ? List.of() : List.copyOf(contents);
        itemEscrowReference = itemEscrowReference == null ? "" : itemEscrowReference;
    }
}
