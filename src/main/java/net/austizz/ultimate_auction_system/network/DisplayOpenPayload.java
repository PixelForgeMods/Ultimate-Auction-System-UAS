package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DisplayOpenPayload(BlockPos pos, boolean remove) implements CustomPacketPayload {
    public static final Type<DisplayOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateAuctionSystem.MODID, "display_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayOpenPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, DisplayOpenPayload::pos,
                    ByteBufCodecs.BOOL, DisplayOpenPayload::remove, DisplayOpenPayload::new);

    @Override
    public Type<DisplayOpenPayload> type() { return TYPE; }
}
