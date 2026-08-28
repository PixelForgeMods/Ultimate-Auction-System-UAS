package net.austizz.ultimate_auction_system.banking;

import java.math.BigDecimal;
import java.util.UUID;

public record UasAccountSnapshot(
        UUID accountId,
        UUID playerId,
        UUID bankId,
        String accountType,
        String accountTypeLabel,
        BigDecimal balance,
        boolean primary,
        boolean frozen,
        String frozenReason
) {
    public UasAccountSnapshot {
        balance = balance == null ? BigDecimal.ZERO : balance;
        accountType = accountType == null ? "" : accountType;
        accountTypeLabel = accountTypeLabel == null ? "" : accountTypeLabel;
        frozenReason = frozenReason == null ? "" : frozenReason;
    }
}
