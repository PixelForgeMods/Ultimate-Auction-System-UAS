package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DisplayEditSelectPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<DisplayEditSelectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateAuctionSystem.MODID, "display_edit_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayEditSelectPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, DisplayEditSelectPayload::pos, DisplayEditSelectPayload::new);

    @Override
    public Type<DisplayEditSelectPayload> type() { return TYPE; }
}
