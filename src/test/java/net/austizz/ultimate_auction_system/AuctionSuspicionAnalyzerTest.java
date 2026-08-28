package net.austizz.ultimate_auction_system;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionSuspicionAnalyzerTest {
    private final AuctionSuspicionAnalyzer analyzer = new AuctionSuspicionAnalyzer();
    private final AuctionSuspicionAnalyzer.Rules rules = new AuctionSuspicionAnalyzer.Rules(
            true,
            true,
            300,
            4,
            3,
            24,
            3
    );

    @Test
    void detectsRapidBidEscalationAndRepeatedBidderPairs() {
        UUID auctionId = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID bidderA = UUID.randomUUID();
        UUID bidderB = UUID.randomUUID();
        List<AuctionBidRecord> records = List.of(
                AuctionBidRecord.accepted(auctionId, bidderA, UUID.randomUUID(), new BigDecimal("10")),
                AuctionBidRecord.accepted(auctionId, bidderB, UUID.randomUUID(), new BigDecimal("11")),
                AuctionBidRecord.accepted(auctionId, bidderA, UUID.randomUUID(), new BigDecimal("12")),
                AuctionBidRecord.accepted(auctionId, bidderB, UUID.randomUUID(), new BigDecimal("13"))
        );

        List<AuctionSuspicionSignal> signals = analyzer.analyzeBidRecords(auctionId, "Diamond", seller, records, rules, this::name);

        assertTrue(signals.stream().anyMatch(signal -> AuctionSuspicionSignal.RAPID_BID_ESCALATION.equals(signal.type())));
        assertTrue(signals.stream().anyMatch(signal -> AuctionSuspicionSignal.REPEATED_BIDDER_PAIR.equals(signal.type())));
    }

    @Test
    void detectsSellerSelfBidSignals() {
        UUID auctionId = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        List<AuctionBidRecord> records = List.of(AuctionBidRecord.accepted(auctionId, seller, UUID.randomUUID(), new BigDecimal("10")));

        List<AuctionSuspicionSignal> signals = analyzer.analyzeBidRecords(auctionId, "Diamond", seller, records, rules, this::name);

        assertTrue(signals.stream().anyMatch(signal -> AuctionSuspicionSignal.SELLER_SELF_BID.equals(signal.type())));
    }

    private String name(UUID playerId) {
        return playerId == null ? "" : playerId.toString().substring(0, 8);
    }
}
