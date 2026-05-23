package net.austizz.ultimate_auction_system.banking;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public record UasBankingResult(
        boolean success,
        String reason,
        BigDecimal balanceAfter,
        Optional<UUID> ubsTransactionId,
        Optional<String> ubsReference
) {
    public UasBankingResult {
        reason = reason == null ? "" : reason;
        balanceAfter = balanceAfter == null ? BigDecimal.ZERO : balanceAfter;
        ubsTransactionId = ubsTransactionId == null ? Optional.empty() : ubsTransactionId;
        ubsReference = ubsReference == null ? Optional.empty() : ubsReference;
    }

    public static UasBankingResult ok(BigDecimal balanceAfter) {
        return new UasBankingResult(true, "", balanceAfter, Optional.empty(), Optional.empty());
    }

    public static UasBankingResult ok(BigDecimal balanceAfter, UUID ubsTransactionId, String ubsReference) {
        return new UasBankingResult(
                true,
                "",
                balanceAfter,
                Optional.ofNullable(ubsTransactionId),
                Optional.ofNullable(blankToNull(ubsReference))
        );
    }

    public static UasBankingResult fail(String reason, BigDecimal balanceAfter) {
        return new UasBankingResult(
                false,
                reason == null || reason.isBlank() ? "Banking operation failed" : reason,
                balanceAfter,
                Optional.empty(),
                Optional.empty()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
