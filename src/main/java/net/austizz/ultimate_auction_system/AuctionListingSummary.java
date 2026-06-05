package net.austizz.ultimate_auction_system;

import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuctionListingSummary(
        UUID auctionId,
        UUID sellerId,
        String sellerName,
        ItemStack item,
        String itemName,
        String description,
        AuctionCategory category,
        String rarity,
        AuctionState state,
        BigDecimal startingBid,
        BigDecimal currentBid,
        BigDecimal buyoutPrice,
        int bidCount,
        UUID highestBidderId,
        LocalDateTime createdAt,
        LocalDateTime endsAt,
        boolean viewerIsSeller,
        boolean viewerIsHighestBidder,
        boolean viewerHasBid,
        boolean viewerReceivesNotifications,
        int notificationSubscriberCount,
        boolean canBid,
        boolean canBuyout,
        boolean canCancel,
        boolean canClaim,
        List<AuctionBidRecord> bidHistory,
        Map<UUID, String> bidderNames
) {
}
