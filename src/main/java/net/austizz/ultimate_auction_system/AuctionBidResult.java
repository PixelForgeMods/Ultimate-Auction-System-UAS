package net.austizz.ultimate_auction_system;

import java.util.Locale;

public enum AuctionBidResult {
    ACCEPTED,
    REJECTED_INVALID,
    REJECTED_AUCTION_NOT_ACTIVE,
    REJECTED_AUCTION_ENDED,
    REJECTED_TOO_LOW,
    REJECTED_NO_ACCOUNT,
    REJECTED_ACCOUNT_UNAVAILABLE;

    public static AuctionBidResult fromSerializedName(String raw) {
        if (raw == null || raw.isBlank()) {
            return REJECTED_INVALID;
        }
        try {
            return AuctionBidResult.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return REJECTED_INVALID;
        }
    }
}
