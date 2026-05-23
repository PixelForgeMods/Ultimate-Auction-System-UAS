package net.austizz.ultimate_auction_system;

public record AuctionStorageHealth(
        UasHealthLevel level,
        String message,
        long lastSaveEpochMillis
) {
    public AuctionStorageHealth {
        level = level == null ? UasHealthLevel.WARNING : level;
        message = message == null || message.isBlank() ? "No storage status available." : message;
    }

    public static AuctionStorageHealth inMemoryOnly() {
        return new AuctionStorageHealth(
                UasHealthLevel.WARNING,
                "In-memory auction storage is active; persistent save backend is not implemented yet.",
                -1L
        );
    }

    public static AuctionStorageHealth saved(String message) {
        return new AuctionStorageHealth(UasHealthLevel.HEALTHY, message, System.currentTimeMillis());
    }

    public static AuctionStorageHealth failed(String message) {
        return new AuctionStorageHealth(UasHealthLevel.ERROR, message, System.currentTimeMillis());
    }
}
