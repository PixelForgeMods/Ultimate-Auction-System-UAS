package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.banking.UasAccountSnapshot;
import net.austizz.ultimate_auction_system.banking.UasMoneyFormatter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record AuctionAccountSummary(
        UUID accountId,
        UUID bankId,
        String accountTypeLabel,
        String balance,
        boolean primary,
        boolean frozen,
        String frozenReason
) {
    public static final AuctionAccountSummary EMPTY = new AuctionAccountSummary(null, null, "", "0", false, false, "");

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionAccountSummary> STREAM_CODEC = StreamCodec.of(
            (buf, summary) -> {
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, summary.accountId());
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, summary.bankId());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.accountTypeLabel());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.balance());
                ByteBufCodecs.BOOL.encode(buf, summary.primary());
                ByteBufCodecs.BOOL.encode(buf, summary.frozen());
                ByteBufCodecs.STRING_UTF8.encode(buf, summary.frozenReason());
            },
            buf -> new AuctionAccountSummary(
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            )
    );

    public static AuctionAccountSummary fromSnapshot(UasAccountSnapshot snapshot) {
        if (snapshot == null) {
            return EMPTY;
        }
        return new AuctionAccountSummary(
                snapshot.accountId(),
                snapshot.bankId(),
                snapshot.accountTypeLabel(),
                UasMoneyFormatter.display(snapshot.balance()),
                snapshot.primary(),
                snapshot.frozen(),
                snapshot.frozenReason()
        );
    }

    public boolean present() {
        return accountId != null;
    }

    public String shortAccountId() {
        return shortId(accountId);
    }

    public String bankIdLabel() {
        String id = shortId(bankId);
        return id.isBlank() ? "" : id;
    }

    private static String shortId(UUID id) {
        return id == null ? "" : id.toString().substring(0, 8);
    }
}
