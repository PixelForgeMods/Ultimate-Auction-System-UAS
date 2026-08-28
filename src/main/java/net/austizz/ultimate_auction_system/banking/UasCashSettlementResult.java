package net.austizz.ultimate_auction_system.banking;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UasCashSettlementResult(
        boolean success,
        String reason,
        UasCashSettlementUse use,
        UUID playerId,
        BigDecimal expectedAmount,
        UasCashBreakdown breakdown,
        List<UasCashResult> mutations,
        boolean compensationAttempted,
        boolean compensationSucceeded
) {
    public UasCashSettlementResult {
        reason = reason == null ? "" : reason;
        expectedAmount = expectedAmount == null ? BigDecimal.ZERO : expectedAmount;
        breakdown = breakdown == null ? UasCashBreakdown.empty() : breakdown;
        mutations = mutations == null ? List.of() : List.copyOf(mutations);
    }

    public static UasCashSettlementResult ok(UasCashSettlementUse use,
                                             UUID playerId,
                                             BigDecimal expectedAmount,
                                             UasCashBreakdown breakdown,
                                             List<UasCashResult> mutations) {
        return new UasCashSettlementResult(true, "", use, playerId, expectedAmount, breakdown, mutations, false, true);
    }

    public static UasCashSettlementResult fail(String reason,
                                               UasCashSettlementUse use,
                                               UUID playerId,
                                               BigDecimal expectedAmount,
                                               UasCashBreakdown breakdown) {
        return fail(reason, use, playerId, expectedAmount, breakdown, List.of(), false, false);
    }

    public static UasCashSettlementResult fail(String reason,
                                               UasCashSettlementUse use,
                                               UUID playerId,
                                               BigDecimal expectedAmount,
                                               UasCashBreakdown breakdown,
                                               List<UasCashResult> mutations,
                                               boolean compensationAttempted,
                                               boolean compensationSucceeded) {
        return new UasCashSettlementResult(
                false,
                reason == null || reason.isBlank() ? "Cash settlement failed" : reason,
                use,
                playerId,
                expectedAmount,
                breakdown,
                mutations,
                compensationAttempted,
                compensationSucceeded
        );
    }
}
