package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.AuctionModFilterSummary;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record AuctionModFilterSummaryPayload(
        String modId,
        String displayName,
        int activeAuctionCount
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionModFilterSummaryPayload> STREAM_CODEC = StreamCodec.of(
            (buf, summary) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.modId());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.displayName());
                ByteBufCodecs.INT.encode(buf, summary.activeAuctionCount());
            },
            buf -> new AuctionModFilterSummaryPayload(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.INT.decode(buf)
            )
    );

    public static AuctionModFilterSummaryPayload fromSummary(AuctionModFilterSummary summary) {
        if (summary == null) {
            return new AuctionModFilterSummaryPayload("", "", 0);
        }
        return new AuctionModFilterSummaryPayload(summary.modId(), summary.displayName(), summary.activeAuctionCount());
    }
}
