package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.AuctionDeliveryEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public record AuctionDeliverySummary(
        UUID deliveryId,
        UUID auctionId,
        ItemStack item,
        String reason,
        String createdAt
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionDeliverySummary> STREAM_CODEC = StreamCodec.of(
            (buf, summary) -> {
                UasNetworkCodecs.UUID_CODEC.encode(buf, summary.deliveryId());
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, summary.auctionId());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, summary.item());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.reason());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.createdAt());
            },
            buf -> new AuctionDeliverySummary(
                    UasNetworkCodecs.UUID_CODEC.decode(buf),
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            )
    );

    public static AuctionDeliverySummary fromEntry(AuctionDeliveryEntry entry) {
        return new AuctionDeliverySummary(
                entry.deliveryId(),
                entry.auctionId(),
                entry.item(),
                entry.reason(),
                entry.createdAt().toString()
        );
    }
}
