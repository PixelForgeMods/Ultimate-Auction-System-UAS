package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record AuctionActionPayload(
        String action,
        UUID auctionId,
        UUID deliveryId,
        int slot,
        String amount,
        String startingBid,
        String buyoutPrice,
        int durationHours,
        String endDateTime,
        String description,
        String search,
        String category,
        String sort,
        String minimumPrice,
        String maximumPrice,
        long maximumHoursLeft
) implements CustomPacketPayload {
    public static final Type<AuctionActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateAuctionSystem.MODID, "auction_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.action());
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, payload.auctionId());
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, payload.deliveryId());
                ByteBufCodecs.INT.encode(buf, payload.slot());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.amount());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.startingBid());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.buyoutPrice());
                ByteBufCodecs.INT.encode(buf, payload.durationHours());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.endDateTime());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.description());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.search());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.category());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.sort());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.minimumPrice());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.maximumPrice());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.maximumHoursLeft());
            },
            buf -> new AuctionActionPayload(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf)
            )
    );

    public static AuctionActionPayload refresh(String search, String category, String sort, String min, String max, long hoursLeft) {
        return new AuctionActionPayload("REFRESH", null, null, -1, "", "", "", 0, "", "", search, category, sort, min, max, hoursLeft);
    }

    @Override
    public Type<AuctionActionPayload> type() {
        return TYPE;
    }
}
