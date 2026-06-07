package net.austizz.ultimate_auction_system;

import java.util.Locale;

public enum AuctionBanAction {
    CREATE,
    BID,
    BUYOUT,
    WATCH;

    public static AuctionBanAction fromSerializedName(String raw) {
        if (raw == null || raw.isBlank()) {
            return CREATE;
        }
        try {
            return AuctionBanAction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CREATE;
        }
    }
}
