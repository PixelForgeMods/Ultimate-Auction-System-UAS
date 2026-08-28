package net.austizz.ultimate_auction_system.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;

public record DisplayEditSpectatorPayload(boolean entering) implements CustomPacketPayload {
    public static final Type<DisplayEditSpectatorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateAuctionSystem.MODID, "display_edit_spectator"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayEditSpectatorPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, DisplayEditSpectatorPayload::entering, DisplayEditSpectatorPayload::new);

    @Override
    public Type<DisplayEditSpectatorPayload> type() {
        return TYPE;
    }
}
