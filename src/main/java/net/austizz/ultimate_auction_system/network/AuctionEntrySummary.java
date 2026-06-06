package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.AuctionListingSummary;
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
        List<AuctionBidSummary> bidHistory
) {
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
                    AuctionBidSummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf)
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
                UasMoneyFormatter.display(summary.currentBid()),
                UasMoneyFormatter.display(summary.buyoutPrice()),
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
                        .toList()
        );
    }

    private static String time(LocalDateTime time) {
        return time == null ? "" : time.toString();
    }
}
