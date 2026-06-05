package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.banking.UasAccountSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.math.BigDecimal;
import java.util.UUID;

public record AuctionAccountSummary(
        UUID accountId,
        String accountTypeLabel,
        String balance,
        boolean primary,
        boolean frozen
) {
    public static final AuctionAccountSummary EMPTY = new AuctionAccountSummary(null, "", "0", false, false);

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionAccountSummary> STREAM_CODEC = StreamCodec.of(
            (buf, summary) -> {
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, summary.accountId());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.accountTypeLabel());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.balance());
                ByteBufCodecs.BOOL.encode(buf, summary.primary());
                ByteBufCodecs.BOOL.encode(buf, summary.frozen());
            },
            buf -> new AuctionAccountSummary(
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)
            )
    );

    public static AuctionAccountSummary fromSnapshot(UasAccountSnapshot snapshot) {
        if (snapshot == null) {
            return EMPTY;
        }
        return new AuctionAccountSummary(
                snapshot.accountId(),
                snapshot.accountTypeLabel(),
                money(snapshot.balance()),
                snapshot.primary(),
                snapshot.frozen()
        );
    }

    public boolean present() {
        return accountId != null;
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "0" : amount.stripTrailingZeros().toPlainString();
    }
}
