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
        BigDecimal reservePrice,
        AuctionFormat format,
        boolean sealedBidRevealed,
        BigDecimal viewerBid,
        boolean reserveActive,
        boolean reservePriceVisible,
        boolean reserveMet,
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
        Map<UUID, String> bidderNames,
        List<ItemStack> contents,
        boolean bundle,
        int totalItemCount
) {
    public AuctionListingSummary {
        reservePrice = reservePrice == null ? BigDecimal.ZERO : reservePrice;
        format = format == null ? AuctionFormat.NORMAL : format;
        viewerBid = viewerBid == null ? BigDecimal.ZERO : viewerBid;
        contents = contents == null || contents.isEmpty()
                ? List.of(item == null ? ItemStack.EMPTY : item.copy())
                : contents.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .limit(AuctionItem.MAX_BUNDLE_CONTENTS)
                .map(ItemStack::copy)
                .toList();
    }
}
