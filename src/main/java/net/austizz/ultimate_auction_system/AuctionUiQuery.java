package net.austizz.ultimate_auction_system;

import java.math.BigDecimal;

public record AuctionUiQuery(
        String search,
        AuctionCategory category,
        BigDecimal minimumPrice,
        BigDecimal maximumPrice,
        long maximumHoursLeft,
        AuctionSort sort
) {
    public static AuctionUiQuery defaults() {
        return new AuctionUiQuery("", AuctionCategory.ALL, BigDecimal.ZERO, BigDecimal.ZERO, 0L, AuctionSort.ENDING_SOON);
    }

    public AuctionCategory safeCategory() {
        return category == null ? AuctionCategory.ALL : category;
    }

    public AuctionSort safeSort() {
        return sort == null ? AuctionSort.ENDING_SOON : sort;
    }

    public String safeSearch() {
        return search == null ? "" : search.trim();
    }
}
