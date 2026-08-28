package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.AuctionBidRecord;
import net.austizz.ultimate_auction_system.banking.UasMoneyFormatter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionBidSummary(
        UUID bidderId,
        String bidderName,
        String amount,
        String timestamp,
        boolean accepted,
        String reason
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionBidSummary> STREAM_CODEC = StreamCodec.of(
            (buf, summary) -> {
                UasNetworkCodecs.UUID_CODEC.encode(buf, summary.bidderId());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.bidderName());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.amount());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.timestamp());
                ByteBufCodecs.BOOL.encode(buf, summary.accepted());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.reason());
            },
            buf -> new AuctionBidSummary(
                    UasNetworkCodecs.UUID_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            )
    );

    public static AuctionBidSummary fromRecord(AuctionBidRecord record) {
        return fromRecord(record, "");
    }

    public static AuctionBidSummary fromRecord(AuctionBidRecord record, String bidderName) {
        return new AuctionBidSummary(
                record.getBidderId(),
                nameOrFallback(bidderName, record.getBidderId()),
                UasMoneyFormatter.display(record.getAmount()),
                time(record.getTimestamp()),
                record.isAccepted(),
                record.getReason()
        );
    }

    private static String time(LocalDateTime time) {
        return time == null ? "" : time.toString();
    }

    private static String nameOrFallback(String name, UUID bidderId) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return bidderId == null ? "Unknown" : bidderId.toString().substring(0, 8);
    }
}
