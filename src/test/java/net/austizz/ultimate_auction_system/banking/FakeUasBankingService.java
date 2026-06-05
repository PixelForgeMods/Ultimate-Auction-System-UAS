package net.austizz.ultimate_auction_system.banking;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FakeUasBankingService implements UasBankingService {
    public record Alert(UUID playerId, String title, String message, String tone, int durationMs) {
    }

    private final Map<UUID, UUID> primaryAccounts = new HashMap<>();
    private final Map<UUID, UasAccountSnapshot> accounts = new HashMap<>();
    private final List<Alert> alerts = new ArrayList<>();
    private String nextFailureReason;

    public UUID createPrimaryAccount(UUID playerId, BigDecimal balance) {
        UUID accountId = UUID.randomUUID();
        primaryAccounts.put(playerId, accountId);
        accounts.put(accountId, new UasAccountSnapshot(
                accountId,
                playerId,
                UUID.randomUUID(),
                "CHECKING",
                "Checking",
                balance,
                true,
                false,
                ""
        ));
        return accountId;
    }

    public void failNext(String reason) {
        this.nextFailureReason = reason == null || reason.isBlank() ? "Forced banking failure" : reason;
    }

    public List<Alert> alerts() {
        return List.copyOf(alerts);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getApiVersion() {
        return "fake";
    }

    @Override
    public UasBankingResult getBalance(UUID accountId) {
        UasAccountSnapshot snapshot = accounts.get(accountId);
        return snapshot == null
                ? UasBankingResult.fail("Account not found", BigDecimal.ZERO)
                : UasBankingResult.ok(snapshot.balance());
    }

    @Override
    public UasBankingResult deposit(UUID accountId, BigDecimal amount, String reference) {
        return adjustBalance(accountId, amount, true);
    }

    @Override
    public UasBankingResult withdraw(UUID accountId, BigDecimal amount, String reference) {
        return adjustBalance(accountId, amount, false);
    }

    @Override
    public UasBankingResult transfer(UUID senderAccountId, UUID receiverAccountId, BigDecimal amount, String reference) {
        UasBankingResult debit = withdraw(senderAccountId, amount, reference);
        if (!debit.success()) {
            return debit;
        }
        return deposit(receiverAccountId, amount, reference);
    }

    @Override
    public UasBankingResult transferFromPrimary(UUID senderPlayerId, UUID receiverAccountId, BigDecimal amount, String reference) {
        return getPrimaryAccountId(senderPlayerId)
                .map(senderAccountId -> transfer(senderAccountId, receiverAccountId, amount, reference))
                .orElseGet(() -> UasBankingResult.fail("Sender primary account not found", BigDecimal.ZERO));
    }

    @Override
    public UasBankingResult transferToPrimary(UUID senderAccountId, UUID receiverPlayerId, BigDecimal amount, String reference) {
        return getPrimaryAccountId(receiverPlayerId)
                .map(receiverAccountId -> transfer(senderAccountId, receiverAccountId, amount, reference))
                .orElseGet(() -> UasBankingResult.fail("Receiver primary account not found", BigDecimal.ZERO));
    }

    @Override
    public UasBankingResult validateCanSend(UUID accountId, BigDecimal amount) {
        UasAccountSnapshot snapshot = accounts.get(accountId);
        if (snapshot == null) {
            return UasBankingResult.fail("Account not found", BigDecimal.ZERO);
        }
        BigDecimal value = safeAmount(amount);
        return snapshot.balance().compareTo(value) >= 0
                ? UasBankingResult.ok(snapshot.balance())
                : UasBankingResult.fail("Insufficient funds", snapshot.balance());
    }

    @Override
    public UasBankingResult validateCanReceive(UUID accountId) {
        return accounts.containsKey(accountId)
                ? UasBankingResult.ok(accounts.get(accountId).balance())
                : UasBankingResult.fail("Account not found", BigDecimal.ZERO);
    }

    @Override
    public boolean playerHasAnyAccount(UUID playerId) {
        return primaryAccounts.containsKey(playerId);
    }

    @Override
    public boolean playerHasPrimaryAccount(UUID playerId) {
        return primaryAccounts.containsKey(playerId);
    }

    @Override
    public boolean playerHasAvailableAccount(UUID playerId) {
        return getPrimaryAccountId(playerId).isPresent();
    }

    @Override
    public boolean playerHasAvailablePrimaryAccount(UUID playerId) {
        return getPrimaryAccountId(playerId).isPresent();
    }

    @Override
    public boolean playerHasFrozenAccount(UUID playerId) {
        return false;
    }

    @Override
    public boolean accountCanSend(UUID accountId, BigDecimal amount) {
        return validateCanSend(accountId, amount).success();
    }

    @Override
    public boolean accountCanReceive(UUID accountId) {
        return validateCanReceive(accountId).success();
    }

    @Override
    public boolean primaryAccountCanSend(UUID playerId, BigDecimal amount) {
        return getPrimaryAccountId(playerId)
                .map(accountId -> accountCanSend(accountId, amount))
                .orElse(false);
    }

    @Override
    public boolean primaryAccountCanReceive(UUID playerId) {
        return getPrimaryAccountId(playerId)
                .map(this::accountCanReceive)
                .orElse(false);
    }

    @Override
    public Optional<UUID> getPrimaryAccountId(UUID playerId) {
        return Optional.ofNullable(primaryAccounts.get(playerId));
    }

    @Override
    public Optional<UasAccountSnapshot> getAccountSnapshot(UUID accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    @Override
    public UasAlertResult sendSuccessAlert(UUID playerId, String title, String message, int durationMs) {
        return recordAlert(playerId, title, message, "SUCCESS", durationMs);
    }

    @Override
    public UasAlertResult sendErrorAlert(UUID playerId, String title, String message, int durationMs) {
        return recordAlert(playerId, title, message, "ERROR", durationMs);
    }

    @Override
    public UasAlertResult sendInfoAlert(UUID playerId, String title, String message, int durationMs) {
        return recordAlert(playerId, title, message, "INFO", durationMs);
    }

    @Override
    public UasAlertResult sendWarningAlert(UUID playerId, String title, String message, int durationMs) {
        return recordAlert(playerId, title, message, "WARNING", durationMs);
    }

    private UasBankingResult adjustBalance(UUID accountId, BigDecimal amount, boolean deposit) {
        if (nextFailureReason != null) {
            String reason = nextFailureReason;
            nextFailureReason = null;
            return UasBankingResult.fail(reason, BigDecimal.ZERO);
        }

        UasAccountSnapshot current = accounts.get(accountId);
        if (current == null) {
            return UasBankingResult.fail("Account not found", BigDecimal.ZERO);
        }

        BigDecimal value = safeAmount(amount);
        BigDecimal nextBalance = deposit ? current.balance().add(value) : current.balance().subtract(value);
        if (nextBalance.compareTo(BigDecimal.ZERO) < 0) {
            return UasBankingResult.fail("Insufficient funds", current.balance());
        }

        accounts.put(accountId, new UasAccountSnapshot(
                current.accountId(),
                current.playerId(),
                current.bankId(),
                current.accountType(),
                current.accountTypeLabel(),
                nextBalance,
                current.primary(),
                current.frozen(),
                current.frozenReason()
        ));
        return UasBankingResult.ok(nextBalance);
    }

    private UasAlertResult recordAlert(UUID playerId, String title, String message, String tone, int durationMs) {
        if (playerId == null) {
            return UasAlertResult.fail("Player is required", null);
        }
        alerts.add(new Alert(playerId, title, message, tone, durationMs));
        return UasAlertResult.ok(playerId);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
