package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.FakeUasBankingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionUiModelTest {
    @Test
    void queryEnumsFallBackToSafeDefaults() {
        assertEquals(AuctionCategory.ALL, AuctionCategory.fromToken("unknown"));
        assertEquals(AuctionSort.ENDING_SOON, AuctionSort.fromToken("unknown"));
    }

    @Test
    void queryEnumsParseKnownTokensCaseInsensitively() {
        assertEquals(AuctionCategory.WEAPONS, AuctionCategory.fromToken("weapons"));
        assertEquals(AuctionSort.HIGHEST_BID, AuctionSort.fromToken("highest_bid"));
    }

    @Test
    void queryObjectProvidesSafeDefaultsForNullFields() {
        AuctionUiQuery query = new AuctionUiQuery(null, null, null, null, 0L, null);

        assertEquals("", query.safeSearch());
        assertEquals(AuctionCategory.ALL, query.safeCategory());
        assertEquals(AuctionSort.ENDING_SOON, query.safeSort());
    }

    @Test
    void listingFeeUsesConfiguredPercentageWithTwoDecimals() {
        double previousRate = Config.listingFeeRate;
        try {
            Config.listingFeeRate = 0.05D;

            assertEquals(new BigDecimal("6.05"), Config.calculateListingFee(new BigDecimal("121")));
            assertEquals(new BigDecimal("0.00"), Config.calculateListingFee(BigDecimal.ZERO));
        } finally {
            Config.listingFeeRate = previousRate;
        }
    }

    @Test
    void snapshotCarriesListingFeeRateToClientModel() {
        AuctionHouseSnapshot snapshot = new AuctionHouseSnapshot(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                0.05D,
                "",
                true,
                false
        );

        assertEquals(0.05D, snapshot.listingFeeRate());
    }

    @Test
    void fakeBankingServiceRecordsUiAlerts() {
        FakeUasBankingService banking = new FakeUasBankingService();
        UUID playerId = UUID.randomUUID();

        assertTrue(banking.sendWarningAlert(playerId, "Auction House", "You were outbid.", 5000).success());

        assertEquals(1, banking.alerts().size());
        assertEquals(playerId, banking.alerts().getFirst().playerId());
        assertEquals("WARNING", banking.alerts().getFirst().tone());
        assertEquals("You were outbid.", banking.alerts().getFirst().message());
    }
}
