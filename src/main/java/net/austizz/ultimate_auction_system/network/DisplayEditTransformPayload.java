package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DisplayEditTransformPayload(BlockPos pos, float x, float y, float z,
                                          float pitch, float yaw, float roll,
                                          float scale, boolean spinning) implements CustomPacketPayload {
    public static final Type<DisplayEditTransformPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateAuctionSystem.MODID, "display_edit_transform"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayEditTransformPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                ByteBufCodecs.FLOAT.encode(buf, payload.x()); ByteBufCodecs.FLOAT.encode(buf, payload.y());
                ByteBufCodecs.FLOAT.encode(buf, payload.z()); ByteBufCodecs.FLOAT.encode(buf, payload.pitch());
                ByteBufCodecs.FLOAT.encode(buf, payload.yaw()); ByteBufCodecs.FLOAT.encode(buf, payload.roll());
                ByteBufCodecs.FLOAT.encode(buf, payload.scale()); ByteBufCodecs.BOOL.encode(buf, payload.spinning());
            },
            buf -> new DisplayEditTransformPayload(BlockPos.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

    @Override
    public Type<DisplayEditTransformPayload> type() { return TYPE; }
}
