package net.austizz.ultimate_auction_system;

import java.util.List;

public record AuctionEconomyReport(
        String label,
        int activeListings,
        int completedSales,
        int failedSettlements,
        String grossVolume,
        String fees,
        String taxes,
        List<Row> topSellers,
        List<Row> topCategories,
        List<Row> topItems
) {
    public AuctionEconomyReport {
        label = label == null ? "" : label;
        grossVolume = grossVolume == null ? "" : grossVolume;
        fees = fees == null ? "" : fees;
        taxes = taxes == null ? "" : taxes;
        topSellers = topSellers == null ? List.of() : List.copyOf(topSellers);
        topCategories = topCategories == null ? List.of() : List.copyOf(topCategories);
        topItems = topItems == null ? List.of() : List.copyOf(topItems);
    }

    public record Row(String label, int count, String amount) {
        public Row {
            label = label == null ? "" : label;
            amount = amount == null ? "" : amount;
        }
    }
}
