package net.austizz.ultimate_auction_system.api;

import net.austizz.ultimate_auction_system.AuctionItem;
import net.austizz.ultimate_auction_system.AuctionFormat;
import net.austizz.ultimate_auction_system.AuctionState;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UasAuctionSnapshot(
        UUID auctionId,
        UUID sellerId,
        String title,
        String description,
        AuctionState state,
        AuctionFormat format,
        BigDecimal startingBid,
        BigDecimal currentBid,
        BigDecimal buyoutPrice,
        BigDecimal reservePrice,
        boolean reserveMet,
        int acceptedBidCount,
        UUID highestBidderId,
        LocalDateTime createdAt,
        LocalDateTime endsAt,
        boolean bundle,
        int totalItemCount,
        List<ItemStack> contents
) {
    public UasAuctionSnapshot(UUID auctionId,
                              UUID sellerId,
                              String title,
                              String description,
                              AuctionState state,
                              BigDecimal startingBid,
                              BigDecimal currentBid,
                              BigDecimal buyoutPrice,
                              int acceptedBidCount,
                              UUID highestBidderId,
                              LocalDateTime createdAt,
                              LocalDateTime endsAt,
                              boolean bundle,
                              int totalItemCount,
                              List<ItemStack> contents) {
        this(
                auctionId,
                sellerId,
                title,
                description,
                state,
                AuctionFormat.NORMAL,
                startingBid,
                currentBid,
                buyoutPrice,
                BigDecimal.ZERO,
                true,
                acceptedBidCount,
                highestBidderId,
                createdAt,
                endsAt,
                bundle,
                totalItemCount,
                contents
        );
    }

    public UasAuctionSnapshot {
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        state = state == null ? AuctionState.ACTIVE : state;
        format = format == null ? AuctionFormat.NORMAL : format;
        startingBid = startingBid == null ? BigDecimal.ZERO : startingBid;
        currentBid = currentBid == null ? BigDecimal.ZERO : currentBid;
        buyoutPrice = buyoutPrice == null ? BigDecimal.ZERO : buyoutPrice;
        reservePrice = reservePrice == null ? BigDecimal.ZERO : reservePrice;
        contents = copyContents(contents);
    }

    @Override
    public List<ItemStack> contents() {
        return copyContents(contents);
    }

    public ItemStack displayItem() {
        return contents.isEmpty() ? ItemStack.EMPTY : contents.getFirst().copy();
    }

    public static UasAuctionSnapshot fromItem(AuctionItem item) {
        if (item == null) {
            return null;
        }
        return new UasAuctionSnapshot(
                item.getAuctionId(),
                item.getPlayerId(),
                item.getDisplayTitle(),
                item.getDescription(),
                item.isExpired() && item.getState() == AuctionState.ACTIVE ? AuctionState.ENDED : item.getState(),
                item.getFormat(),
                item.getStartingBidPrice(),
                item.getCurrentPrice(),
                item.getBuyoutPrice().orElse(BigDecimal.ZERO),
                item.getReservePrice().orElse(BigDecimal.ZERO),
                item.isReserveMet(),
                (int) item.getBidRecords().stream().filter(record -> record != null && record.isAccepted()).count(),
                item.getHighestBidderId(),
                item.getCreatedAt(),
                item.getDateOfEnd(),
                item.isBundle(),
                item.getTotalItemCount(),
                item.getContents()
        );
    }

    private static List<ItemStack> copyContents(List<ItemStack> rawContents) {
        if (rawContents == null || rawContents.isEmpty()) {
            return List.of();
        }
        return rawContents.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }
}
