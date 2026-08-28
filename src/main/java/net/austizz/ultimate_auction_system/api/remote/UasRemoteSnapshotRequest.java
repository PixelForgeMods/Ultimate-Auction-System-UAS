package net.austizz.ultimate_auction_system.api.remote;

public record UasRemoteSnapshotRequest(boolean includeAuctions, boolean includeDeliveries, int limit) {
    public static UasRemoteSnapshotRequest reconciliation() {
        return new UasRemoteSnapshotRequest(true, true, 0);
    }

    public UasRemoteSnapshotRequest {
        limit = Math.max(0, limit);
    }
}
