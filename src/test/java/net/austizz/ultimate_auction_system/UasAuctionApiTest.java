package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.api.UasAuctionApi;
import net.austizz.ultimate_auction_system.api.UasAuctionQuery;
import net.austizz.ultimate_auction_system.api.UasAuctionResult;
import net.austizz.ultimate_auction_system.api.UasAuctionResultCode;
import net.austizz.ultimate_auction_system.api.UasCreateAuctionRequest;
import net.austizz.ultimate_auction_system.banking.FakeUasBankingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UasAuctionApiTest {
    @Test
    void missingAuctionReturnsStableCodeInsteadOfThrowing() {
        UasAuctionApi api = new UasAuctionApi(new AuctionHouse(new FakeUasBankingService()));
        UUID auctionId = UUID.randomUUID();

        UasAuctionResult result = api.inspectStatus(auctionId);

        assertFalse(result.success());
        assertEquals(UasAuctionResultCode.MISSING_AUCTION, result.code());
        assertEquals(auctionId, result.auctionId());
    }

    @Test
    void queryActiveHandlesUnloadedAndEmptyAuctionHouses() {
        assertEquals(List.of(), new UasAuctionApi(null).queryActive(UasAuctionQuery.defaults()));
        assertEquals(List.of(), new UasAuctionApi(new AuctionHouse(new FakeUasBankingService())).queryActive(UasAuctionQuery.defaults()));
    }

    @Test
    void apiReportsVersionAndNormalizesPublicInputs() {
        UasAuctionApi api = new UasAuctionApi(new AuctionHouse(new FakeUasBankingService()));
        UasAuctionQuery query = new UasAuctionQuery(null, null, null, null, 0L, null, null, 500);
        UasCreateAuctionRequest request = new UasCreateAuctionRequest(List.of(3, 3, -1, 5), null, null, null, null, LocalDateTime.now());

        assertEquals("1", api.apiVersion());
        assertEquals(120, query.limit());
        assertEquals(List.of(3, 5), request.inventorySlots());
        assertEquals(BigDecimal.ZERO, request.startingBid());
        assertEquals(BigDecimal.ZERO, request.buyoutPrice());
    }
}
