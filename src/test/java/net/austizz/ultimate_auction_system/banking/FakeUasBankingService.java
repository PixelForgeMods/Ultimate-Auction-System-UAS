package net.austizz.ultimate_auction_system.banking;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FakeUasBankingService implements UasBankingService {
    private final Map<UUID, UUID> primaryAccounts = new HashMap<>();
    private final Map<UUID, UasAccountSnapshot> accounts = new HashMap<>();
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
    public Optional<UUID> getPrimaryAccountId(UUID playerId) {
        return Optional.ofNullable(primaryAccounts.get(playerId));
    }

    @Override
    public Optional<UasAccountSnapshot> getAccountSnapshot(UUID accountId) {
        return Optional.ofNullable(accounts.get(accountId));
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

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
