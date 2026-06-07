package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record AuctionAdminActionPayload(
        String action,
        UUID auctionId,
        UUID playerId,
        String playerName,
        boolean blockCreate,
        boolean blockBid,
        boolean blockBuyout,
        boolean blockWatch,
        String reason,
        String expiresAt,
        String bannedEntry
) implements CustomPacketPayload {
    public static final Type<AuctionAdminActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateAuctionSystem.MODID, "auction_admin_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionAdminActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.action());
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, payload.auctionId());
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, payload.playerId());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.playerName());
                ByteBufCodecs.BOOL.encode(buf, payload.blockCreate());
                ByteBufCodecs.BOOL.encode(buf, payload.blockBid());
                ByteBufCodecs.BOOL.encode(buf, payload.blockBuyout());
                ByteBufCodecs.BOOL.encode(buf, payload.blockWatch());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.reason());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.expiresAt());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.bannedEntry());
            },
            buf -> new AuctionAdminActionPayload(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            )
    );

    @Override
    public Type<AuctionAdminActionPayload> type() {
        return TYPE;
    }
}
