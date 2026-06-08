package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.AuctionListingPreview;
import net.austizz.ultimate_auction_system.banking.UasMoneyFormatter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDateTime;
import java.util.List;

public record AuctionPendingListingSummary(
        boolean present,
        ItemStack item,
        String itemName,
        int itemCount,
        String startingBid,
        String buyoutPrice,
        String reservePrice,
        String listingFee,
        String endsAt,
        String expiresAt,
        String description,
        String sourceLabel,
        List<ItemStack> contents,
        boolean bundle
) {
    public static final AuctionPendingListingSummary EMPTY = new AuctionPendingListingSummary(
            false,
            ItemStack.EMPTY,
            "",
            0,
            "0",
            "0",
            "0",
            "0",
            "",
            "",
            "",
            "",
            List.of(),
            false
    );

    public AuctionPendingListingSummary {
        contents = contents == null || contents.isEmpty()
                ? List.of(item == null ? ItemStack.EMPTY : item.copy())
                : contents.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionPendingListingSummary> STREAM_CODEC = StreamCodec.of(
            (buf, summary) -> {
                ByteBufCodecs.BOOL.encode(buf, summary.present());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, summary.item());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.itemName());
                ByteBufCodecs.INT.encode(buf, summary.itemCount());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.startingBid());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.buyoutPrice());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.reservePrice());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.listingFee());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.endsAt());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.expiresAt());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.description());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.sourceLabel());
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(18)).encode(buf, summary.contents());
                ByteBufCodecs.BOOL.encode(buf, summary.bundle());
            },
            buf -> new AuctionPendingListingSummary(
                    ByteBufCodecs.BOOL.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(18)).decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)
            )
    );

    public static AuctionPendingListingSummary fromPreview(AuctionListingPreview preview) {
        if (preview == null || !preview.present()) {
            return EMPTY;
        }
        return new AuctionPendingListingSummary(
                true,
                preview.item(),
                preview.itemName(),
                preview.itemCount(),
                UasMoneyFormatter.display(preview.startingBid()),
                UasMoneyFormatter.display(preview.buyoutPrice()),
                UasMoneyFormatter.display(preview.reservePrice()),
                UasMoneyFormatter.display(preview.listingFee()),
                time(preview.endsAt()),
                time(preview.expiresAt()),
                preview.description(),
                preview.sourceLabel(),
                preview.contents(),
                preview.bundle()
        );
    }

    private static String time(LocalDateTime time) {
        return time == null ? "" : time.toString();
    }
}
