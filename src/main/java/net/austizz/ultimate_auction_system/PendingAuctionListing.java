package net.austizz.ultimate_auction_system;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PendingAuctionListing(
        UUID playerId,
        int slot,
        ItemStack itemSnapshot,
        BigDecimal startingBid,
        BigDecimal buyoutPrice,
        LocalDateTime endsAt,
        String description,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        String sourceLabel
) {
    public static final int MAIN_HAND_SLOT = -1;

    public PendingAuctionListing {
        itemSnapshot = itemSnapshot == null ? ItemStack.EMPTY : itemSnapshot.copy();
        startingBid = startingBid == null ? BigDecimal.ZERO : startingBid;
        buyoutPrice = buyoutPrice == null ? BigDecimal.ZERO : buyoutPrice;
        description = description == null ? "" : description;
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        expiresAt = expiresAt == null ? createdAt.plusSeconds(Config.pendingListingConfirmationSeconds) : expiresAt;
        sourceLabel = sourceLabel == null ? "" : sourceLabel;
    }

    public boolean isExpired(LocalDateTime now) {
        return now == null || !expiresAt.isAfter(now);
    }

    public ItemStack currentStack(ServerPlayer player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        if (slot == MAIN_HAND_SLOT) {
            return player.getMainHandItem();
        }
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(slot);
    }

    public boolean stillMatches(ServerPlayer player) {
        ItemStack current = currentStack(player);
        return !itemSnapshot.isEmpty()
                && !current.isEmpty()
                && current.getCount() >= itemSnapshot.getCount()
                && ItemStack.isSameItemSameComponents(current, itemSnapshot);
    }

    public AuctionListingPreview toPreview() {
        return new AuctionListingPreview(
                itemSnapshot,
                itemSnapshot.getHoverName().getString(),
                itemSnapshot.getCount(),
                startingBid,
                buyoutPrice,
                Config.calculateListingFee(startingBid),
                endsAt,
                expiresAt,
                description,
                sourceLabel
        );
    }
}
