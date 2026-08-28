package net.austizz.ultimate_auction_system.display;

import java.util.Locale;

public enum AuctionDisplayType {
    HIGHEST_BID,
    MOST_WATCHED,
    MANUAL,
    ENDING_SOON,
    RANDOM;

    public static AuctionDisplayType fromToken(String token) {
        if (token == null || token.isBlank()) {
            return HIGHEST_BID;
        }
        try {
            return valueOf(token.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return HIGHEST_BID;
        }
    }
}
