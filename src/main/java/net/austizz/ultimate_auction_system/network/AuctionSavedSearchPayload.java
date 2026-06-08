package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.AuctionSavedSearch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record AuctionSavedSearchPayload(
        UUID searchId,
        String name,
        String search,
        String category,
        String sort,
        String minimumPrice,
        String maximumPrice,
        long maximumHoursLeft,
        String modId
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionSavedSearchPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, payload.searchId());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.name());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.search());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.category());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.sort());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.minimumPrice());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.maximumPrice());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.maximumHoursLeft());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.modId());
            },
            buf -> new AuctionSavedSearchPayload(
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            )
    );

    public AuctionSavedSearchPayload {
        name = name == null ? "" : name;
        search = search == null ? "" : search;
        category = category == null ? "" : category;
        sort = sort == null ? "" : sort;
        minimumPrice = minimumPrice == null ? "" : minimumPrice;
        maximumPrice = maximumPrice == null ? "" : maximumPrice;
        modId = modId == null ? "" : modId;
        maximumHoursLeft = Math.max(0L, maximumHoursLeft);
    }

    public static AuctionSavedSearchPayload fromSearch(AuctionSavedSearch search) {
        if (search == null) {
            return new AuctionSavedSearchPayload(null, "", "", "", "", "", "", 0L, "");
        }
        return new AuctionSavedSearchPayload(
                search.searchId(),
                search.name(),
                search.search(),
                search.category(),
                search.sort(),
                search.minimumPrice(),
                search.maximumPrice(),
                search.maximumHoursLeft(),
                search.modId()
        );
    }
}
