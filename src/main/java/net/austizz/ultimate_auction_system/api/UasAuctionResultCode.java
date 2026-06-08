package net.austizz.ultimate_auction_system.api;

import java.util.Locale;

/**
 * Stable machine-readable result codes for integrations using the UAS Java API.
 */
public enum UasAuctionResultCode {
    SUCCESS,
    VALIDATION_FAILED,
    INVALID_STATE,
    MISSING_AUCTION,
    PERMISSION_DENIED,
    UBS_UNAVAILABLE,
    UBS_ACCOUNT_MISSING,
    INSUFFICIENT_FUNDS,
    STORAGE_UNAVAILABLE,
    RATE_LIMITED,
    SETTLEMENT_FAILED,
    ESCROW_FAILED,
    UNKNOWN_FAILURE;

    public static UasAuctionResultCode infer(boolean success, String reason) {
        if (success) {
            return SUCCESS;
        }
        String normalized = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (normalized.contains("not found")) {
            return MISSING_AUCTION;
        }
        if (normalized.contains("permission") || normalized.contains("can only")) {
            return PERMISSION_DENIED;
        }
        if (normalized.contains("not active")
                || normalized.contains("already ended")
                || normalized.contains("expired")
                || normalized.contains("cancelled")
                || normalized.contains("claimed")) {
            return INVALID_STATE;
        }
        if (normalized.contains("ubs is unavailable") || normalized.contains("banking service")) {
            return UBS_UNAVAILABLE;
        }
        if (normalized.contains("primary account") || normalized.contains("account not found")) {
            return UBS_ACCOUNT_MISSING;
        }
        if (normalized.contains("insufficient funds") || normalized.contains("cannot pay")) {
            return INSUFFICIENT_FUNDS;
        }
        if (normalized.contains("storage") || normalized.contains("migration")) {
            return STORAGE_UNAVAILABLE;
        }
        if (normalized.contains("rate limited")) {
            return RATE_LIMITED;
        }
        if (normalized.contains("settlement") || normalized.contains("payout") || normalized.contains("refund")) {
            return SETTLEMENT_FAILED;
        }
        if (normalized.contains("escrow") || normalized.contains("no longer matches")) {
            return ESCROW_FAILED;
        }
        return VALIDATION_FAILED;
    }
}
