package net.austizz.ultimate_auction_system;

import java.util.Locale;

public enum AuctionSellerFilter {
    ALL,
    ACTIVE,
    SOLD,
    CANCELLED,
    EXPIRED;

    public static AuctionSellerFilter fromToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        try {
            return AuctionSellerFilter.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ALL;
        }
    }

    public boolean matches(AuctionItem item) {
        if (item == null) {
            return false;
        }
        return switch (this) {
            case ALL -> true;
            case ACTIVE -> item.getState() == AuctionState.ACTIVE && !item.isExpired();
            case SOLD -> item.getHighestBidderId() != null
                    && (item.getState() == AuctionState.ENDED
                    || item.getState() == AuctionState.CLAIMED
                    || item.getState() == AuctionState.FAILED_SETTLEMENT);
            case CANCELLED -> item.getState() == AuctionState.CANCELLED;
            case EXPIRED -> item.getHighestBidderId() == null
                    && (item.getState() == AuctionState.ENDED
                    || (item.getState() == AuctionState.ACTIVE && item.isExpired()));
        };
    }
}
