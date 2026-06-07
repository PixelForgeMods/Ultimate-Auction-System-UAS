package net.austizz.ultimate_auction_system;

import net.minecraft.world.item.ItemStack;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AuctionDeliveryEntry(
        UUID deliveryId,
        UUID playerId,
        UUID auctionId,
        List<ItemStack> items,
        String reason,
        LocalDateTime createdAt
) {
    public AuctionDeliveryEntry(UUID deliveryId,
                                UUID playerId,
                                UUID auctionId,
                                ItemStack item,
                                String reason,
                                LocalDateTime createdAt) {
        this(deliveryId, playerId, auctionId, List.of(item == null ? ItemStack.EMPTY : item.copy()), reason, createdAt);
    }

    public AuctionDeliveryEntry {
        items = items == null || items.isEmpty()
                ? List.of()
                : items.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .limit(AuctionItem.MAX_BUNDLE_CONTENTS)
                .map(ItemStack::copy)
                .toList();
        reason = reason == null ? "" : reason;
    }

    public ItemStack item() {
        return items.isEmpty() ? ItemStack.EMPTY : items.getFirst().copy();
    }

    public boolean bundle() {
        return items.size() > 1;
    }

    public int totalItemCount() {
        return items.stream().mapToInt(ItemStack::getCount).sum();
    }

    public AuctionDeliveryEntry copy() {
        return new AuctionDeliveryEntry(
                deliveryId,
                playerId,
                auctionId,
                items,
                reason,
                createdAt
        );
    }
}
