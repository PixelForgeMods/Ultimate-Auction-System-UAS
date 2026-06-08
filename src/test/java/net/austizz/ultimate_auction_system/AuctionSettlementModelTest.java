package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.FakeUasBankingService;
import net.austizz.ultimate_auction_system.banking.UasBankingResult;
import net.austizz.ultimate_auction_system.banking.UasItemResult;
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
        assertEquals("UAS_RESERVE_REFUND:" + auctionId, AuctionHouse.auctionReference(AuctionHouse.EVENT_RESERVE_REFUND, auctionId));
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
    void chequePayoutConfigRequiresEnabledWholeDollarPayouts() {
        boolean previousEnabled = Config.chequePayouts;
        long previousMinimum = Config.chequePayoutMinimumDollars;
        String previousSource = Config.chequePayoutSourceAccountUuid;
        String previousIssuer = Config.chequePayoutIssuerPlayerUuid;
        UUID sourceAccount = UUID.randomUUID();
        UUID issuer = UUID.randomUUID();
        try {
            Config.chequePayouts = false;
            Config.chequePayoutMinimumDollars = 100L;
            Config.chequePayoutSourceAccountUuid = sourceAccount.toString();
            Config.chequePayoutIssuerPlayerUuid = issuer.toString();
            assertTrue(Config.chequePayoutSourceAccountId().isPresent());
            assertEquals(issuer, Config.chequePayoutIssuerPlayerId().orElseThrow());
            assertTrue(!Config.chequePayoutApplies(new BigDecimal("150")));

            Config.chequePayouts = true;
            assertTrue(!Config.chequePayoutApplies(new BigDecimal("99")));
            assertTrue(!Config.chequePayoutApplies(new BigDecimal("150.50")));
            assertTrue(Config.chequePayoutApplies(new BigDecimal("150")));
        } finally {
            Config.chequePayouts = previousEnabled;
            Config.chequePayoutMinimumDollars = previousMinimum;
            Config.chequePayoutSourceAccountUuid = previousSource;
            Config.chequePayoutIssuerPlayerUuid = previousIssuer;
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

    @Test
    void fakeBankingServiceRecordsChequeIssuerRecipientAndDebitsSource() {
        FakeUasBankingService banking = new FakeUasBankingService();
        UUID issuerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID sourceAccountId = banking.createPrimaryAccount(issuerId, new BigDecimal("500"));

        UasItemResult cheque = banking.issueCheque(
                sourceAccountId,
                sellerId,
                125L,
                issuerId,
                "Auction Treasury",
                "Seller"
        );

        assertTrue(cheque.success());
        assertEquals(new BigDecimal("375"), banking.getBalance(sourceAccountId).balanceAfter());
        assertEquals(1, banking.cheques().size());
        assertEquals(sellerId, banking.cheques().getFirst().recipientPlayerId());
        assertEquals("Auction Treasury", banking.cheques().getFirst().issuerName());
        assertEquals("Seller", banking.cheques().getFirst().recipientName());
    }

}
