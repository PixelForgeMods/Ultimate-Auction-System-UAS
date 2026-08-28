package net.austizz.ultimate_auction_system.api.remote;

public record UasRemoteItemSnapshot(String itemId, String displayName, int count) {
    public UasRemoteItemSnapshot {
        itemId = itemId == null ? "" : itemId;
        displayName = displayName == null ? "" : displayName;
    }
}
