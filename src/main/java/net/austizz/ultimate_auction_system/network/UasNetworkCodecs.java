package net.austizz.ultimate_auction_system.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

final class UasNetworkCodecs {
    static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = StreamCodec.of(
            (buf, uuid) -> {
                buf.writeLong(uuid.getMostSignificantBits());
                buf.writeLong(uuid.getLeastSignificantBits());
            },
            buf -> new UUID(buf.readLong(), buf.readLong())
    );

    static final StreamCodec<RegistryFriendlyByteBuf, UUID> OPTIONAL_UUID_CODEC = StreamCodec.of(
            (buf, uuid) -> {
                ByteBufCodecs.BOOL.encode(buf, uuid != null);
                if (uuid != null) {
                    UUID_CODEC.encode(buf, uuid);
                }
            },
            buf -> ByteBufCodecs.BOOL.decode(buf) ? UUID_CODEC.decode(buf) : null
    );

    private UasNetworkCodecs() {
    }
}
