package net.austizz.ultimate_auction_system;

import java.util.Locale;

public enum AuctionState {
    DRAFT,
    ACTIVE,
    ENDED,
    CANCELLED,
    CLAIMED,
    FAILED_SETTLEMENT;

    public boolean canTransitionTo(AuctionState nextState) {
        if (nextState == null || this == nextState) {
            return true;
        }
        return switch (this) {
            case DRAFT -> nextState == ACTIVE || nextState == CANCELLED;
            case ACTIVE -> nextState == ENDED || nextState == CANCELLED || nextState == FAILED_SETTLEMENT;
            case ENDED -> nextState == CLAIMED || nextState == CANCELLED || nextState == FAILED_SETTLEMENT;
            case FAILED_SETTLEMENT -> nextState == CLAIMED || nextState == CANCELLED || nextState == ENDED;
            case CANCELLED, CLAIMED -> false;
        };
    }

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
