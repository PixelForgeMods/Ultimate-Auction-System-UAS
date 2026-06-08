package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.AuctionListingSummary;
import net.austizz.ultimate_auction_system.AuctionFormat;
import net.austizz.ultimate_auction_system.banking.UasMoneyFormatter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AuctionEntrySummary(
        UUID auctionId,
        UUID sellerId,
        String sellerName,
        ItemStack item,
        String itemName,
        String description,
        String category,
        String rarity,
        String state,
        String startingBid,
        String currentBid,
        String buyoutPrice,
        String reservePrice,
        String format,
        boolean sealedBid,
        boolean sealedBidRevealed,
        String viewerBid,
        boolean reserveActive,
        boolean reservePriceVisible,
        boolean reserveMet,
        int bidCount,
        String endsAt,
        boolean viewerIsSeller,
        boolean viewerIsHighestBidder,
        boolean viewerHasBid,
        boolean viewerReceivesNotifications,
        int notificationSubscriberCount,
        boolean canBid,
        boolean canBuyout,
        boolean canCancel,
        boolean canClaim,
        List<AuctionBidSummary> bidHistory,
        List<ItemStack> contents,
        boolean bundle,
        int totalItemCount
) {
    public AuctionEntrySummary {
        reservePrice = reservePrice == null ? "" : reservePrice;
        format = format == null || format.isBlank() ? "normal" : format;
        viewerBid = viewerBid == null ? "" : viewerBid;
        contents = contents == null || contents.isEmpty()
                ? List.of(item == null ? ItemStack.EMPTY : item.copy())
                : contents.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
        totalItemCount = Math.max(totalItemCount, contents.stream().mapToInt(ItemStack::getCount).sum());
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionEntrySummary> STREAM_CODEC = StreamCodec.of(
            (buf, summary) -> {
                UasNetworkCodecs.UUID_CODEC.encode(buf, summary.auctionId());
                UasNetworkCodecs.UUID_CODEC.encode(buf, summary.sellerId());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.sellerName());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, summary.item());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.itemName());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.description());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.category());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.rarity());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.state());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.startingBid());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.currentBid());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.buyoutPrice());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.reservePrice());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.format());
                ByteBufCodecs.BOOL.encode(buf, summary.sealedBid());
                ByteBufCodecs.BOOL.encode(buf, summary.sealedBidRevealed());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.viewerBid());
                ByteBufCodecs.BOOL.encode(buf, summary.reserveActive());
                ByteBufCodecs.BOOL.encode(buf, summary.reservePriceVisible());
                ByteBufCodecs.BOOL.encode(buf, summary.reserveMet());
                ByteBufCodecs.INT.encode(buf, summary.bidCount());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.endsAt());
                ByteBufCodecs.BOOL.encode(buf, summary.viewerIsSeller());
                ByteBufCodecs.BOOL.encode(buf, summary.viewerIsHighestBidder());
                ByteBufCodecs.BOOL.encode(buf, summary.viewerHasBid());
                ByteBufCodecs.BOOL.encode(buf, summary.viewerReceivesNotifications());
                ByteBufCodecs.INT.encode(buf, summary.notificationSubscriberCount());
                ByteBufCodecs.BOOL.encode(buf, summary.canBid());
                ByteBufCodecs.BOOL.encode(buf, summary.canBuyout());
                ByteBufCodecs.BOOL.encode(buf, summary.canCancel());
                ByteBufCodecs.BOOL.encode(buf, summary.canClaim());
                AuctionBidSummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, summary.bidHistory());
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(18)).encode(buf, summary.contents());
                ByteBufCodecs.BOOL.encode(buf, summary.bundle());
                ByteBufCodecs.INT.encode(buf, summary.totalItemCount());
            },
            buf -> new AuctionEntrySummary(
                    UasNetworkCodecs.UUID_CODEC.decode(buf),
                    UasNetworkCodecs.UUID_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    AuctionBidSummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(18)).decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.INT.decode(buf)
            )
    );

    public static AuctionEntrySummary fromListing(AuctionListingSummary summary) {
        return new AuctionEntrySummary(
                summary.auctionId(),
                summary.sellerId(),
                summary.sellerName(),
                summary.item(),
                summary.itemName(),
                summary.description(),
                summary.category().name(),
                summary.rarity(),
                summary.state().name(),
                UasMoneyFormatter.display(summary.startingBid()),
                currentBidDisplay(summary),
                UasMoneyFormatter.display(summary.buyoutPrice()),
                summary.reservePriceVisible() ? UasMoneyFormatter.display(summary.reservePrice()) : "",
                summary.format().serializedName(),
                summary.format() == AuctionFormat.SEALED_BID,
                summary.sealedBidRevealed(),
                viewerBidDisplay(summary),
                summary.reserveActive(),
                summary.reservePriceVisible(),
                summary.reserveMet(),
                summary.bidCount(),
                time(summary.endsAt()),
                summary.viewerIsSeller(),
                summary.viewerIsHighestBidder(),
                summary.viewerHasBid(),
                summary.viewerReceivesNotifications(),
                summary.notificationSubscriberCount(),
                summary.canBid(),
                summary.canBuyout(),
                summary.canCancel(),
                summary.canClaim(),
                summary.bidHistory().stream()
                        .map(record -> AuctionBidSummary.fromRecord(record, summary.bidderNames().get(record.getBidderId())))
                        .toList(),
                summary.contents(),
                summary.bundle(),
                summary.totalItemCount()
        );
    }

    private static String time(LocalDateTime time) {
        return time == null ? "" : time.toString();
    }

    private static String currentBidDisplay(AuctionListingSummary summary) {
        if (summary.format() == AuctionFormat.SEALED_BID && !summary.sealedBidRevealed()) {
            return "Sealed";
        }
        return UasMoneyFormatter.display(summary.currentBid());
    }

    private static String viewerBidDisplay(AuctionListingSummary summary) {
        if (summary.viewerBid().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return "";
        }
        return UasMoneyFormatter.display(summary.viewerBid());
    }
}
