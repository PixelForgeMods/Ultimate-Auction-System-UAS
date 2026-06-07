package net.austizz.ultimate_auction_system;

import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record AuctionListingPreview(
        ItemStack item,
        String itemName,
        int itemCount,
        BigDecimal startingBid,
        BigDecimal buyoutPrice,
        BigDecimal listingFee,
        LocalDateTime endsAt,
        LocalDateTime expiresAt,
        String description,
        String sourceLabel,
        List<ItemStack> contents,
        boolean bundle
) {
    public AuctionListingPreview {
        item = item == null ? ItemStack.EMPTY : item.copy();
        itemName = itemName == null ? "" : itemName;
        startingBid = startingBid == null ? BigDecimal.ZERO : startingBid;
        buyoutPrice = buyoutPrice == null ? BigDecimal.ZERO : buyoutPrice;
        listingFee = listingFee == null ? BigDecimal.ZERO : listingFee;
        description = description == null ? "" : description;
        sourceLabel = sourceLabel == null ? "" : sourceLabel;
        contents = contents == null || contents.isEmpty()
                ? List.of(item)
                : contents.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .limit(AuctionItem.MAX_BUNDLE_CONTENTS)
                .map(ItemStack::copy)
                .toList();
    }

    public long durationHours() {
        if (endsAt == null) {
            return 0L;
        }
        return Math.max(1L, Duration.between(LocalDateTime.now(), endsAt).toHours());
    }

    public boolean present() {
        return !item.isEmpty();
    }
}
