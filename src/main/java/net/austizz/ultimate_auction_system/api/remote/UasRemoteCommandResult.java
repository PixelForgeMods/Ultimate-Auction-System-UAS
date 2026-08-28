package net.austizz.ultimate_auction_system.api.remote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UasRemoteCommandResult(UasRemoteCommandStatus status, UasRemoteResultCode code, String messageKey,
                                     String idempotencyKey, UUID operationId, boolean duplicate, long revision,
                                     Instant completedAt, UUID auctionId, String auctionState, BigDecimal amount,
                                     BigDecimal balanceAfter, String settlementReference) {
    public UasRemoteCommandResult {
        status = status == null ? UasRemoteCommandStatus.FAILED : status;
        code = code == null ? UasRemoteResultCode.FAILED : code;
        messageKey = messageKey == null ? "" : messageKey;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey;
        operationId = operationId == null ? UUID.randomUUID() : operationId;
        completedAt = completedAt == null ? Instant.now() : completedAt;
        auctionState = auctionState == null ? "" : auctionState;
        amount = amount == null ? BigDecimal.ZERO : amount;
        balanceAfter = balanceAfter == null ? BigDecimal.ZERO : balanceAfter;
        settlementReference = settlementReference == null ? "" : settlementReference;
    }

    public boolean success() {
        return status == UasRemoteCommandStatus.SUCCEEDED && code == UasRemoteResultCode.SUCCESS;
    }

    public static UasRemoteCommandResult success(String key, UUID auctionId, BigDecimal balanceAfter, String reference) {
        return new UasRemoteCommandResult(UasRemoteCommandStatus.SUCCEEDED, UasRemoteResultCode.SUCCESS,
                "uas.remote.success", key, UUID.randomUUID(), false, 0L, Instant.now(), auctionId, "", BigDecimal.ZERO, balanceAfter, reference);
    }

    public static UasRemoteCommandResult failure(String key, UasRemoteResultCode code, String messageKey) {
        return new UasRemoteCommandResult(UasRemoteCommandStatus.FAILED, code, messageKey,
                key, UUID.randomUUID(), false, 0L, Instant.now(), null, "", BigDecimal.ZERO, BigDecimal.ZERO, "");
    }

    public UasRemoteCommandResult asDuplicate() {
        return new UasRemoteCommandResult(status, code, messageKey, idempotencyKey, operationId, true,
                revision, completedAt, auctionId, auctionState, amount, balanceAfter, settlementReference);
    }
}
