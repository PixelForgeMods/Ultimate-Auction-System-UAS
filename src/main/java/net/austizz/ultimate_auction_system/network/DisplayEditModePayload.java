package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DisplayEditModePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<DisplayEditModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateAuctionSystem.MODID, "display_edit_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayEditModePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, DisplayEditModePayload::enabled, DisplayEditModePayload::new);

    @Override
    public Type<DisplayEditModePayload> type() { return TYPE; }
}
