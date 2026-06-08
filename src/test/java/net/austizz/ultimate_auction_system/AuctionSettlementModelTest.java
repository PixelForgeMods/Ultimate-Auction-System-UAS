package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.FakeUasBankingService;
import net.austizz.ultimate_auction_system.banking.UasBankingResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionSettlementModelTest {
    @Test
    void referencesIncludeAuctionIdAndEventType() {
        UUID auctionId = UUID.randomUUID();

        assertEquals("UAS_AUCTION_PAYOUT:" + auctionId, AuctionHouse.auctionReference(AuctionHouse.EVENT_AUCTION_PAYOUT, auctionId));
        assertEquals("UAS_SALES_TAX:" + auctionId, AuctionHouse.auctionReference(AuctionHouse.EVENT_SALES_TAX, auctionId));
        assertEquals("UAS_BID_ESCROW:" + auctionId, AuctionHouse.auctionReference(AuctionHouse.EVENT_BID_ESCROW, auctionId));
    }

    @Test
    void salesTaxUsesConfiguredRateAndTwoDecimals() {
        double previousRate = Config.salesTaxRate;
        try {
            Config.salesTaxRate = 0.05D;

            assertEquals(new BigDecimal("6.05"), Config.calculateSalesTax(new BigDecimal("121")));
        } finally {
            Config.salesTaxRate = previousRate;
        }
    }

    @Test
    void salesTaxDestinationAccountIdParsesOptionalUuid() {
        String previousDestination = Config.salesTaxDestinationAccountUuid;
        UUID destination = UUID.randomUUID();
        try {
            Config.salesTaxDestinationAccountUuid = "";
            assertTrue(Config.salesTaxDestinationAccountId().isEmpty());

            Config.salesTaxDestinationAccountUuid = destination.toString();
            assertEquals(destination, Config.salesTaxDestinationAccountId().orElseThrow());

            Config.salesTaxDestinationAccountUuid = "not-a-uuid";
            assertTrue(Config.salesTaxDestinationAccountId().isEmpty());
        } finally {
            Config.salesTaxDestinationAccountUuid = previousDestination;
        }
    }

    @Test
    void financialEventUsesUbsReferenceAndTransactionId() {
        UUID auctionId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        String reference = AuctionHouse.auctionReference(AuctionHouse.EVENT_AUCTION_PAYOUT, auctionId);

        AuctionFinancialEvent event = AuctionFinancialEvent.fromBanking(
                auctionId,
                AuctionHouse.EVENT_AUCTION_PAYOUT,
                new BigDecimal("50"),
                reference,
                UasBankingResult.ok(new BigDecimal("150"), transactionId, reference)
        );

        assertTrue(event.success());
        assertEquals(reference, event.reference());
        assertEquals(transactionId, event.transactionId());
        assertTrue(event.isValidForAuction(auctionId));
    }

    @Test
    void fakeBankingServiceRecordsReferencesForTests() {
        FakeUasBankingService banking = new FakeUasBankingService();
        UUID playerId = UUID.randomUUID();
        UUID accountId = banking.createPrimaryAccount(playerId, new BigDecimal("100"));
        String reference = "UAS_BID_ESCROW:" + UUID.randomUUID();

        banking.withdraw(accountId, new BigDecimal("25"), reference);

        assertEquals(1, banking.transactions().size());
        assertEquals(reference, banking.transactions().getFirst().reference());
        assertEquals(new BigDecimal("75"), banking.getBalance(accountId).balanceAfter());
    }

    @Test
    void fakeBankingServiceExposesPlayerAccountsAndOwnership() {
        FakeUasBankingService banking = new FakeUasBankingService();
        UUID playerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();
        UUID primary = banking.createPrimaryAccount(playerId, new BigDecimal("100"));
        UUID savings = banking.createAccount(playerId, new BigDecimal("250"), "SAVINGS", "Savings");
        UUID other = banking.createPrimaryAccount(otherPlayerId, new BigDecimal("50"));

        assertEquals(2, banking.getPlayerAccounts(playerId).size());
        assertTrue(banking.playerOwnsAccount(playerId, primary));
        assertTrue(banking.playerOwnsAccount(playerId, savings));
        assertTrue(!banking.playerOwnsAccount(playerId, other));
    }
}
