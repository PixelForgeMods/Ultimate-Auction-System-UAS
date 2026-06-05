package net.austizz.ultimate_auction_system;

public record AuctionActionResult(boolean success, String message) {
    public static AuctionActionResult ok(String message) {
        return new AuctionActionResult(true, message == null ? "" : message);
    }

    public static AuctionActionResult fail(String message) {
        return new AuctionActionResult(false, message == null ? "Auction action failed." : message);
    }
}
