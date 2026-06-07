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
}
