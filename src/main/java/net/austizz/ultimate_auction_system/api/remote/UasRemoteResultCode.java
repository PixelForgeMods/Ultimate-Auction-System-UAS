package net.austizz.ultimate_auction_system.api.remote;

import net.austizz.ultimate_auction_system.api.UasAuctionResultCode;

public enum UasRemoteResultCode {
    SUCCESS,
    INVALID_COMMAND,
    AUCTION_NOT_FOUND,
    PERMISSION_DENIED,
    STORAGE_UNAVAILABLE,
    SERVER_UNAVAILABLE,
    UBS_UNAVAILABLE,
    FAILED;

    public static UasRemoteResultCode from(UasAuctionResultCode code) {
        if (code == null) return FAILED;
        return switch (code) {
            case SUCCESS -> SUCCESS;
            case MISSING_AUCTION -> AUCTION_NOT_FOUND;
            case PERMISSION_DENIED -> PERMISSION_DENIED;
            case STORAGE_UNAVAILABLE -> STORAGE_UNAVAILABLE;
            default -> FAILED;
        };
    }
}
