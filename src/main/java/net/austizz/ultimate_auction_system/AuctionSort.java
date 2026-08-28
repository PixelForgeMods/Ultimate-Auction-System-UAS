package net.austizz.ultimate_auction_system;

import java.util.Locale;

public enum AuctionSort {
    NEWEST("Newest"),
    ENDING_SOON("Ending Soon"),
    HIGHEST_BID("Highest Bid"),
    LOWEST_PRICE("Lowest Price"),
    BUYOUT_PRICE("Buyout Price");

    private final String label;

    AuctionSort(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static AuctionSort fromToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return ENDING_SOON;
        }
        try {
            return AuctionSort.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ENDING_SOON;
        }
    }
}
