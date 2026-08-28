package net.austizz.ultimate_auction_system.api.remote;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record UasRemoteCommand(String idempotencyKey, UasRemoteCommandType type, UUID actorId, UUID auctionId,
                               UUID accountId, BigDecimal amount, Map<String, String> metadata) {
    public UasRemoteCommand {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        amount = amount == null ? BigDecimal.ZERO : amount;
    }
}
