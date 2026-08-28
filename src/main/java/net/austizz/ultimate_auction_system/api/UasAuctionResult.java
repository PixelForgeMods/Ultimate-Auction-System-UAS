package net.austizz.ultimate_auction_system.api;

import net.austizz.ultimate_auction_system.AuctionActionResult;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public record UasAuctionResult(
        boolean success,
        UasAuctionResultCode code,
        String reason,
        UUID auctionId,
        BigDecimal balanceAfter,
        String settlementReference,
        UasAuctionSnapshot auction
) {
    public UasAuctionResult {
        code = code == null ? UasAuctionResultCode.infer(success, reason) : code;
        reason = reason == null ? "" : reason;
        balanceAfter = balanceAfter == null ? BigDecimal.ZERO : balanceAfter;
        settlementReference = settlementReference == null ? "" : settlementReference;
    }

    public Optional<UasAuctionSnapshot> auctionSnapshot() {
        return Optional.ofNullable(auction);
    }

    public static UasAuctionResult ok(String reason, UUID auctionId, UasAuctionSnapshot auction) {
        return new UasAuctionResult(true, UasAuctionResultCode.SUCCESS, reason, auctionId, BigDecimal.ZERO, "", auction);
    }

    public static UasAuctionResult fail(UasAuctionResultCode code, String reason, UUID auctionId) {
        return new UasAuctionResult(false, code, reason, auctionId, BigDecimal.ZERO, "", null);
    }

    public static UasAuctionResult fromAction(AuctionActionResult action, UasAuctionSnapshot auction) {
        if (action == null) {
            return fail(UasAuctionResultCode.UNKNOWN_FAILURE, "Auction action failed.", null);
        }
        UasAuctionResultCode code = action.success()
                ? UasAuctionResultCode.SUCCESS
                : UasAuctionResultCode.infer(false, action.message());
        if (!action.success() && action.code() != null && action.code() != UasAuctionResultCode.SUCCESS) {
            code = action.code();
        }
        UUID auctionId = action.auctionId() == null && auction != null ? auction.auctionId() : action.auctionId();
        return new UasAuctionResult(
                action.success(),
                code,
                action.message(),
                auctionId,
                action.balanceAfter(),
                action.settlementReference(),
                auction
        );
    }
}
