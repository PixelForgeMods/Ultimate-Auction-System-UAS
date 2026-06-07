package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.AuctionDeliveryEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public record AuctionDeliverySummary(
        UUID deliveryId,
        UUID auctionId,
        ItemStack item,
        List<ItemStack> contents,
        boolean bundle,
        int totalItemCount,
        String reason,
        String createdAt
) {
    public AuctionDeliverySummary {
        contents = contents == null || contents.isEmpty()
                ? List.of(item == null ? ItemStack.EMPTY : item.copy())
                : contents.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
        totalItemCount = Math.max(totalItemCount, contents.stream().mapToInt(ItemStack::getCount).sum());
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionDeliverySummary> STREAM_CODEC = StreamCodec.of(
            (buf, summary) -> {
                UasNetworkCodecs.UUID_CODEC.encode(buf, summary.deliveryId());
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, summary.auctionId());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, summary.item());
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(18)).encode(buf, summary.contents());
                ByteBufCodecs.BOOL.encode(buf, summary.bundle());
                ByteBufCodecs.INT.encode(buf, summary.totalItemCount());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.reason());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.createdAt());
            },
            buf -> new AuctionDeliverySummary(
                    UasNetworkCodecs.UUID_CODEC.decode(buf),
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(18)).decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            )
    );

    public static AuctionDeliverySummary fromEntry(AuctionDeliveryEntry entry) {
        return new AuctionDeliverySummary(
                entry.deliveryId(),
                entry.auctionId(),
                entry.item(),
                entry.items(),
                entry.bundle(),
                entry.totalItemCount(),
                entry.reason(),
                entry.createdAt().toString()
        );
    }
}
