package net.austizz.ultimate_auction_system.banking;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface UasBankingService {
    boolean isAvailable();

    String getApiVersion();

    UasBankingResult getBalance(UUID accountId);

    UasBankingResult deposit(UUID accountId, BigDecimal amount, String reference);

    UasBankingResult withdraw(UUID accountId, BigDecimal amount, String reference);

    UasBankingResult transfer(UUID senderAccountId, UUID receiverAccountId, BigDecimal amount, String reference);

    UasBankingResult transferFromPrimary(UUID senderPlayerId, UUID receiverAccountId, BigDecimal amount, String reference);

    UasBankingResult transferToPrimary(UUID senderAccountId, UUID receiverPlayerId, BigDecimal amount, String reference);

    UasBankingResult validateCanSend(UUID accountId, BigDecimal amount);

    UasBankingResult validateCanReceive(UUID accountId);

    Optional<UUID> getPrimaryAccountId(UUID playerId);

    Optional<UasAccountSnapshot> getAccountSnapshot(UUID accountId);
}
