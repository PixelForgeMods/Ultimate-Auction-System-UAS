package net.austizz.ultimate_auction_system.banking;

import java.math.BigDecimal;
import java.util.List;
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

    boolean playerHasAnyAccount(UUID playerId);

    boolean playerHasPrimaryAccount(UUID playerId);

    boolean playerHasAvailableAccount(UUID playerId);

    boolean playerHasAvailablePrimaryAccount(UUID playerId);

    boolean playerHasFrozenAccount(UUID playerId);

    boolean accountCanSend(UUID accountId, BigDecimal amount);

    boolean accountCanReceive(UUID accountId);

    boolean primaryAccountCanSend(UUID playerId, BigDecimal amount);

    boolean primaryAccountCanReceive(UUID playerId);

    Optional<UUID> getPrimaryAccountId(UUID playerId);

    List<UasAccountSnapshot> getPlayerAccounts(UUID playerId);

    Optional<UasAccountSnapshot> getAccountSnapshot(UUID accountId);

    boolean playerOwnsAccount(UUID playerId, UUID accountId);

    UasAlertResult sendSuccessAlert(UUID playerId, String title, String message, int durationMs);

    UasAlertResult sendErrorAlert(UUID playerId, String title, String message, int durationMs);

    UasAlertResult sendInfoAlert(UUID playerId, String title, String message, int durationMs);

    UasAlertResult sendWarningAlert(UUID playerId, String title, String message, int durationMs);
}
