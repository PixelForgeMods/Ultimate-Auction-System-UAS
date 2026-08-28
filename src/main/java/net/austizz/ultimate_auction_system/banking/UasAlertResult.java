package net.austizz.ultimate_auction_system.banking;

import java.util.UUID;

public record UasAlertResult(
        boolean success,
        String reason,
        UUID playerId
) {
    public UasAlertResult {
        reason = reason == null ? "" : reason;
    }

    public static UasAlertResult ok(UUID playerId) {
        return new UasAlertResult(true, "", playerId);
    }

    public static UasAlertResult fail(String reason, UUID playerId) {
        return new UasAlertResult(
                false,
                reason == null || reason.isBlank() ? "Alert failed" : reason,
                playerId
        );
    }
}
