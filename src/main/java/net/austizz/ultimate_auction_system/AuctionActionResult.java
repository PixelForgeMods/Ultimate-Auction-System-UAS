package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.api.UasAuctionResultCode;

import java.math.BigDecimal;
import java.util.UUID;

public record AuctionActionResult(
        boolean success,
        String message,
        UasAuctionResultCode code,
        UUID auctionId,
        BigDecimal balanceAfter,
        String settlementReference
) {
    public AuctionActionResult(boolean success, String message) {
        this(success, message, success ? UasAuctionResultCode.SUCCESS : UasAuctionResultCode.VALIDATION_FAILED, null, null, "");
    }

    public AuctionActionResult {
        message = message == null ? (success ? "" : "Auction action failed.") : message;
        code = code == null ? (success ? UasAuctionResultCode.SUCCESS : UasAuctionResultCode.VALIDATION_FAILED) : code;
        settlementReference = settlementReference == null ? "" : settlementReference;
    }

    public static AuctionActionResult ok(String message) {
        return new AuctionActionResult(true, message, UasAuctionResultCode.SUCCESS, null, null, "");
    }

    public static AuctionActionResult ok(String message, UUID auctionId) {
        return new AuctionActionResult(true, message, UasAuctionResultCode.SUCCESS, auctionId, null, "");
    }

    public static AuctionActionResult fail(String message) {
        return new AuctionActionResult(false, message, UasAuctionResultCode.VALIDATION_FAILED, null, null, "");
    }

    public static AuctionActionResult fail(UasAuctionResultCode code, String message) {
        return new AuctionActionResult(false, message, code, null, null, "");
    }

    public AuctionActionResult withAuctionId(UUID auctionId) {
        return new AuctionActionResult(success, message, code, auctionId, balanceAfter, settlementReference);
    }

    public AuctionActionResult withBalanceAfter(BigDecimal balanceAfter) {
        return new AuctionActionResult(success, message, code, auctionId, balanceAfter, settlementReference);
    }

    public AuctionActionResult withSettlementReference(String settlementReference) {
        return new AuctionActionResult(success, message, code, auctionId, balanceAfter, settlementReference);
    }
}
