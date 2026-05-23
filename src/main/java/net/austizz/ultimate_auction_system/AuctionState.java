package net.austizz.ultimate_auction_system;

import java.util.Locale;

public enum AuctionState {
    DRAFT,
    ACTIVE,
    ENDED,
    CANCELLED,
    CLAIMED,
    FAILED_SETTLEMENT;

    public static AuctionState fromSerializedName(String raw) {
        if (raw == null || raw.isBlank()) {
            return ACTIVE;
        }
        try {
            return AuctionState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ACTIVE;
        }
    }
}
