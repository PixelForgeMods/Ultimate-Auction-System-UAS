package net.austizz.ultimate_auction_system.api.remote;

import java.util.Optional;

public interface UasRemoteAuctionApi {
    String getApiVersion();

    UasRemoteSnapshot snapshot(UasRemoteSnapshotRequest request);

    Optional<UasRemoteCommandResult> findCommand(String idempotencyKey);

    UasRemoteCommandResult execute(UasRemoteCommand command);
}
