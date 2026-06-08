package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.FakeUasBankingService;
import net.austizz.ultimate_auction_system.banking.UasCashBreakdown;
import net.austizz.ultimate_auction_system.banking.UasCashSettlementResult;
import net.austizz.ultimate_auction_system.banking.UasCashSettlementUse;
import net.austizz.ultimate_auction_system.banking.UasPhysicalCashSettlementService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionPhysicalCashSettlementTest {
    @Test
    void physicalCashSettlementIsDisabledByDefaultPerFlow() {
        boolean previousListing = Config.physicalCashListingFees;
        boolean previousBuyouts = Config.physicalCashBuyouts;
        try {
            Config.physicalCashListingFees = false;
            Config.physicalCashBuyouts = false;
            UUID playerId = UUID.randomUUID();
            FakeUasBankingService banking = new FakeUasBankingService();
            banking.setCashBills(playerId, 10, 1);
            UasPhysicalCashSettlementService service = new UasPhysicalCashSettlementService(banking);

            UasCashSettlementResult listing = service.takeExactCash(
                    playerId,
                    new BigDecimal("10.00"),
                    new UasCashBreakdown(Map.of(10, 1), Map.of()),
                    UasCashSettlementUse.LISTING_FEE
            );
            UasCashSettlementResult buyout = service.takeExactCash(
                    playerId,
                    new BigDecimal("10.00"),
                    new UasCashBreakdown(Map.of(10, 1), Map.of()),
                    UasCashSettlementUse.BUYOUT
            );

            assertFalse(listing.success());
            assertFalse(buyout.success());
            assertEquals(1, banking.getCashBillCount(playerId, 10));
            assertEquals(0, banking.cashMutations().size());
        } finally {
            Config.physicalCashListingFees = previousListing;
            Config.physicalCashBuyouts = previousBuyouts;
        }
    }

    @Test
    void exactCashSettlementTakesBillsAndCoinsOnlyWhenTotalsMatch() {
        boolean previousListing = Config.physicalCashListingFees;
        try {
            Config.physicalCashListingFees = true;
            UUID playerId = UUID.randomUUID();
            FakeUasBankingService banking = new FakeUasBankingService();
            banking.setCashBills(playerId, 5, 1);
            banking.setCashCoins(playerId, 25, 2);
            UasPhysicalCashSettlementService service = new UasPhysicalCashSettlementService(banking);

            UasCashSettlementResult result = service.takeExactCash(
                    playerId,
                    new BigDecimal("5.50"),
                    new UasCashBreakdown(Map.of(5, 1), Map.of(25, 2)),
                    UasCashSettlementUse.LISTING_FEE
            );

            assertTrue(result.success());
            assertEquals(0, banking.getCashBillCount(playerId, 5));
            assertEquals(0, banking.getCashCoinCount(playerId, 25));
            assertEquals(2, banking.cashMutations().size());
        } finally {
            Config.physicalCashListingFees = previousListing;
        }
    }

    @Test
    void exactCashSettlementRejectsIncorrectTotalsBeforeMutatingInventory() {
        boolean previousBuyouts = Config.physicalCashBuyouts;
        try {
            Config.physicalCashBuyouts = true;
            UUID playerId = UUID.randomUUID();
            FakeUasBankingService banking = new FakeUasBankingService();
            banking.setCashBills(playerId, 20, 1);
            UasPhysicalCashSettlementService service = new UasPhysicalCashSettlementService(banking);

            UasCashSettlementResult result = service.takeExactCash(
                    playerId,
                    new BigDecimal("19.99"),
                    new UasCashBreakdown(Map.of(20, 1), Map.of()),
                    UasCashSettlementUse.BUYOUT
            );

            assertFalse(result.success());
            assertEquals(1, banking.getCashBillCount(playerId, 20));
            assertEquals(0, banking.cashMutations().size());
        } finally {
            Config.physicalCashBuyouts = previousBuyouts;
        }
    }

    @Test
    void failedCashTakeAttemptsToReturnAlreadyRemovedDenominations() {
        boolean previousBuyouts = Config.physicalCashBuyouts;
        try {
            Config.physicalCashBuyouts = true;
            UUID playerId = UUID.randomUUID();
            FakeUasBankingService banking = new FakeUasBankingService();
            banking.setCashBills(playerId, 5, 1);
            banking.setCashCoins(playerId, 25, 4);
            UasPhysicalCashSettlementService service = new UasPhysicalCashSettlementService(banking);

            banking.failNextCashCoinTake("Coin inventory changed");
            UasCashSettlementResult result = service.takeExactCash(
                    playerId,
                    new BigDecimal("6.00"),
                    new UasCashBreakdown(Map.of(5, 1), Map.of(25, 4)),
                    UasCashSettlementUse.BUYOUT
            );

            assertFalse(result.success());
            assertEquals(1, banking.getCashBillCount(playerId, 5));
            assertEquals(4, banking.getCashCoinCount(playerId, 25));
            assertTrue(result.compensationAttempted());
            assertTrue(result.compensationSucceeded());
        } finally {
            Config.physicalCashBuyouts = previousBuyouts;
        }
    }
}
