package net.austizz.ultimate_auction_system;

public record AuctionStorageHealth(
        UasHealthLevel level,
        String message,
        long lastSaveEpochMillis,
        String lastFailureReason
) {
    public AuctionStorageHealth(UasHealthLevel level, String message, long lastSaveEpochMillis) {
        this(level, message, lastSaveEpochMillis, "");
    }

    public AuctionStorageHealth {
        level = level == null ? UasHealthLevel.WARNING : level;
        message = message == null || message.isBlank() ? "No storage status available." : message;
        lastFailureReason = lastFailureReason == null ? "" : lastFailureReason;
    }

    public static AuctionStorageHealth inMemoryOnly() {
        return new AuctionStorageHealth(
                UasHealthLevel.WARNING,
                "In-memory auction storage is active; persistent save backend is not implemented yet.",
                -1L,
                ""
        );
    }

    public static AuctionStorageHealth saved(String message) {
        return new AuctionStorageHealth(UasHealthLevel.HEALTHY, message, System.currentTimeMillis(), "");
    }

    public static AuctionStorageHealth loaded(String message) {
        return new AuctionStorageHealth(UasHealthLevel.HEALTHY, message, -1L, "");
    }

    public static AuctionStorageHealth dirty(AuctionStorageHealth previous, String message) {
        long lastSuccessfulSave = previous == null ? -1L : previous.lastSaveEpochMillis();
        String lastFailure = previous == null ? "" : previous.lastFailureReason();
        return new AuctionStorageHealth(UasHealthLevel.WARNING, message, lastSuccessfulSave, lastFailure);
    }

    public static AuctionStorageHealth failed(AuctionStorageHealth previous, String message) {
        long lastSuccessfulSave = previous == null ? -1L : previous.lastSaveEpochMillis();
        return new AuctionStorageHealth(UasHealthLevel.ERROR, message, lastSuccessfulSave, message);
    }
}
