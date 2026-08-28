package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DisplayEditorPayload(BlockPos pos, float x, float y, float z, float pitch, float yaw, float roll,
                                   float scale, boolean spinning) implements CustomPacketPayload {
    public static final Type<DisplayEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateAuctionSystem.MODID, "display_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayEditorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.pos());
                ByteBufCodecs.FLOAT.encode(buf, payload.x()); ByteBufCodecs.FLOAT.encode(buf, payload.y());
                ByteBufCodecs.FLOAT.encode(buf, payload.z()); ByteBufCodecs.FLOAT.encode(buf, payload.pitch());
                ByteBufCodecs.FLOAT.encode(buf, payload.yaw()); ByteBufCodecs.FLOAT.encode(buf, payload.roll());
                ByteBufCodecs.FLOAT.encode(buf, payload.scale()); ByteBufCodecs.BOOL.encode(buf, payload.spinning());
            },
            buf -> new DisplayEditorPayload(BlockPos.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

    public static DisplayEditorPayload fromDisplay(BlockPos pos, net.austizz.ultimate_auction_system.display.AuctionDisplayBlockEntity display) {
        return new DisplayEditorPayload(pos, display.modelX(), display.modelY(), display.modelZ(), display.modelPitch(),
                display.modelYaw(), display.modelRoll(), display.modelScale(), display.spinning());
    }

    @Override
    public Type<DisplayEditorPayload> type() { return TYPE; }
}
