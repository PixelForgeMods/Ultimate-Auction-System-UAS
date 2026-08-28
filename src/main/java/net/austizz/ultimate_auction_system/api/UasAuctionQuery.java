package net.austizz.ultimate_auction_system.api;

import net.austizz.ultimate_auction_system.AuctionCategory;
import net.austizz.ultimate_auction_system.AuctionSort;
import net.austizz.ultimate_auction_system.AuctionUiQuery;

import java.math.BigDecimal;

public record UasAuctionQuery(
        String search,
        AuctionCategory category,
        BigDecimal minimumPrice,
        BigDecimal maximumPrice,
        long maximumHoursLeft,
        AuctionSort sort,
        String modId,
        int limit
) {
    public UasAuctionQuery {
        search = search == null ? "" : search;
        category = category == null ? AuctionCategory.ALL : category;
        minimumPrice = minimumPrice == null ? BigDecimal.ZERO : minimumPrice;
        maximumPrice = maximumPrice == null ? BigDecimal.ZERO : maximumPrice;
        sort = sort == null ? AuctionSort.ENDING_SOON : sort;
        modId = modId == null ? "" : modId;
        int requestedLimit = limit <= 0 ? 120 : limit;
        limit = Math.max(1, Math.min(120, requestedLimit));
    }

    public static UasAuctionQuery defaults() {
        return new UasAuctionQuery("", AuctionCategory.ALL, BigDecimal.ZERO, BigDecimal.ZERO, 0L, AuctionSort.ENDING_SOON, "", 120);
    }

    AuctionUiQuery toUiQuery() {
        return new AuctionUiQuery(search, category, minimumPrice, maximumPrice, maximumHoursLeft, sort, modId);
    }
}
