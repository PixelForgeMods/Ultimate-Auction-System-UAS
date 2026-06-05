package net.austizz.ultimate_auction_system;

import net.minecraft.world.item.ItemStack;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionDeliveryEntry(
        UUID deliveryId,
        UUID playerId,
        UUID auctionId,
        ItemStack item,
        String reason,
        LocalDateTime createdAt
) {
    public AuctionDeliveryEntry copy() {
        return new AuctionDeliveryEntry(
                deliveryId,
                playerId,
                auctionId,
                item == null ? ItemStack.EMPTY : item.copy(),
                reason,
                createdAt
        );
    }
}
