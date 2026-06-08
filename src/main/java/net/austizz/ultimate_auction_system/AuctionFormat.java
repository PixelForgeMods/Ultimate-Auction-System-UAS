package net.austizz.ultimate_auction_system;

import java.util.Locale;

public enum AuctionFormat {
    NORMAL("normal"),
    SEALED_BID("sealed_bid");

    private final String serializedName;

    AuctionFormat(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static AuctionFormat fromSerializedName(String raw) {
        if (raw == null || raw.isBlank()) {
            return NORMAL;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (AuctionFormat format : values()) {
            if (format.serializedName.equals(normalized) || format.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return format;
            }
        }
        return NORMAL;
    }
}
