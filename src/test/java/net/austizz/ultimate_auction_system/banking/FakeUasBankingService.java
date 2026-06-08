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

    public record Transaction(UUID accountId, BigDecimal amount, String reference, String type, boolean success) {
    }

    public record CashMutation(UUID playerId, UasCashKind kind, int denomination, int count, String type, boolean success) {
    }

    public record Cheque(UUID sourceAccountId,
                         UUID recipientPlayerId,
                         long amountDollars,
                         UUID issuerPlayerId,
                         String issuerName,
                         String recipientName,
                         boolean success) {
    }

    private final Map<UUID, UUID> primaryAccounts = new HashMap<>();
    private final Map<UUID, UasAccountSnapshot> accounts = new HashMap<>();
    private final Map<UUID, Map<Integer, Integer>> cashBills = new HashMap<>();
    private final Map<UUID, Map<Integer, Integer>> cashCoins = new HashMap<>();
    private final List<Alert> alerts = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<CashMutation> cashMutations = new ArrayList<>();
    private final List<Cheque> cheques = new ArrayList<>();
    private String nextFailureReason;
    private String nextDepositFailureReason;
    private String nextWithdrawFailureReason;
    private String nextCashTakeFailureReason;
    private String nextCashCoinTakeFailureReason;
    private String nextCashGiveFailureReason;
    private String nextChequeFailureReason;

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

    public UUID createAccount(UUID playerId, BigDecimal balance, String accountType, String accountTypeLabel) {
        UUID accountId = UUID.randomUUID();
        accounts.put(accountId, new UasAccountSnapshot(
                accountId,
                playerId,
                UUID.randomUUID(),
                accountType,
                accountTypeLabel,
                balance,
                false,
                false,
                ""
        ));
        return accountId;
    }

    public void failNext(String reason) {
        this.nextFailureReason = reason == null || reason.isBlank() ? "Forced banking failure" : reason;
    }

    public void failNextDeposit(String reason) {
        this.nextDepositFailureReason = reason == null || reason.isBlank() ? "Forced deposit failure" : reason;
    }

    public void failNextWithdraw(String reason) {
        this.nextWithdrawFailureReason = reason == null || reason.isBlank() ? "Forced withdrawal failure" : reason;
    }

    public void failNextCashTake(String reason) {
        this.nextCashTakeFailureReason = reason == null || reason.isBlank() ? "Forced cash take failure" : reason;
    }

    public void failNextCashCoinTake(String reason) {
        this.nextCashCoinTakeFailureReason = reason == null || reason.isBlank() ? "Forced cash coin take failure" : reason;
    }

    public void failNextCashGive(String reason) {
        this.nextCashGiveFailureReason = reason == null || reason.isBlank() ? "Forced cash give failure" : reason;
    }

    public void failNextCheque(String reason) {
        this.nextChequeFailureReason = reason == null || reason.isBlank() ? "Forced cheque failure" : reason;
    }

    public void setCashBills(UUID playerId, int denomination, int count) {
        setCash(cashBills, playerId, denomination, count);
    }

    public void setCashCoins(UUID playerId, int denominationCents, int count) {
        setCash(cashCoins, playerId, denominationCents, count);
    }

    public List<Alert> alerts() {
        return List.copyOf(alerts);
    }

    public List<Transaction> transactions() {
        return List.copyOf(transactions);
    }

    public List<CashMutation> cashMutations() {
        return List.copyOf(cashMutations);
    }

    public List<Cheque> cheques() {
        return List.copyOf(cheques);
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
        return adjustBalance(accountId, amount, reference, "DEPOSIT", true);
    }

    @Override
    public UasBankingResult withdraw(UUID accountId, BigDecimal amount, String reference) {
        return adjustBalance(accountId, amount, reference, "WITHDRAW", false);
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
    public List<UasAccountSnapshot> getPlayerAccounts(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return accounts.values().stream()
                .filter(snapshot -> playerId.equals(snapshot.playerId()))
                .sorted((left, right) -> Boolean.compare(right.primary(), left.primary()))
                .toList();
    }

    @Override
    public Optional<UasAccountSnapshot> getAccountSnapshot(UUID accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    @Override
    public boolean playerOwnsAccount(UUID playerId, UUID accountId) {
        UasAccountSnapshot snapshot = accounts.get(accountId);
        return snapshot != null && playerId != null && playerId.equals(snapshot.playerId());
    }

    @Override
    public List<Integer> getSupportedCashBillDenominations() {
        return List.of(100, 50, 20, 10, 5, 1);
    }

    @Override
    public List<Integer> getSupportedCashCoinDenominations() {
        return List.of(100, 50, 25, 10, 5, 1);
    }

    @Override
    public int getCashBillCount(UUID playerId, int denomination) {
        return getCash(cashBills, playerId, denomination);
    }

    @Override
    public int getCashCoinCount(UUID playerId, int denominationCents) {
        return getCash(cashCoins, playerId, denominationCents);
    }

    @Override
    public UasCashResult giveCashBills(UUID playerId, int denomination, int billCount) {
        return adjustCash(cashBills, playerId, UasCashKind.BILL, denomination, billCount, "GIVE", true);
    }

    @Override
    public UasCashResult takeCashBills(UUID playerId, int denomination, int billCount) {
        return adjustCash(cashBills, playerId, UasCashKind.BILL, denomination, billCount, "TAKE", false);
    }

    @Override
    public UasCashResult giveCashCoins(UUID playerId, int denominationCents, int coinCount) {
        return adjustCash(cashCoins, playerId, UasCashKind.COIN, denominationCents, coinCount, "GIVE", true);
    }

    @Override
    public UasCashResult takeCashCoins(UUID playerId, int denominationCents, int coinCount) {
        return adjustCash(cashCoins, playerId, UasCashKind.COIN, denominationCents, coinCount, "TAKE", false);
    }

    @Override
    public UasItemResult issueCheque(UUID sourceAccountId,
                                     UUID recipientPlayerId,
                                     long amountDollars,
                                     UUID issuerPlayerId,
                                     String issuerName,
                                     String recipientName) {
        if (nextChequeFailureReason != null) {
            String reason = nextChequeFailureReason;
            nextChequeFailureReason = null;
            cheques.add(new Cheque(sourceAccountId, recipientPlayerId, amountDollars, issuerPlayerId, issuerName, recipientName, false));
            return UasItemResult.fail(reason);
        }
        if (sourceAccountId == null || recipientPlayerId == null || amountDollars <= 0) {
            cheques.add(new Cheque(sourceAccountId, recipientPlayerId, amountDollars, issuerPlayerId, issuerName, recipientName, false));
            return UasItemResult.fail("Invalid cheque request");
        }
        UasBankingResult debit = withdraw(sourceAccountId, BigDecimal.valueOf(amountDollars), "FAKE_CHEQUE");
        if (!debit.success()) {
            cheques.add(new Cheque(sourceAccountId, recipientPlayerId, amountDollars, issuerPlayerId, issuerName, recipientName, false));
            return UasItemResult.fail(debit.reason());
        }
        cheques.add(new Cheque(sourceAccountId, recipientPlayerId, amountDollars, issuerPlayerId, issuerName, recipientName, true));
        return UasItemResult.ok(null, "fake-cheque-" + cheques.size(), BigDecimal.valueOf(amountDollars));
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

    private UasBankingResult adjustBalance(UUID accountId, BigDecimal amount, String reference, String type, boolean deposit) {
        BigDecimal value = safeAmount(amount);
        String typedFailure = deposit ? nextDepositFailureReason : nextWithdrawFailureReason;
        if (typedFailure != null) {
            if (deposit) {
                nextDepositFailureReason = null;
            } else {
                nextWithdrawFailureReason = null;
            }
            transactions.add(new Transaction(accountId, value, reference, type, false));
            return UasBankingResult.fail(typedFailure, BigDecimal.ZERO);
        }
        if (nextFailureReason != null) {
            String reason = nextFailureReason;
            nextFailureReason = null;
            transactions.add(new Transaction(accountId, value, reference, type, false));
            return UasBankingResult.fail(reason, BigDecimal.ZERO);
        }

        UasAccountSnapshot current = accounts.get(accountId);
        if (current == null) {
            transactions.add(new Transaction(accountId, value, reference, type, false));
            return UasBankingResult.fail("Account not found", BigDecimal.ZERO);
        }

        BigDecimal nextBalance = deposit ? current.balance().add(value) : current.balance().subtract(value);
        if (nextBalance.compareTo(BigDecimal.ZERO) < 0) {
            transactions.add(new Transaction(accountId, value, reference, type, false));
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
        transactions.add(new Transaction(accountId, value, reference, type, true));
        return UasBankingResult.ok(nextBalance, UUID.randomUUID(), reference);
    }

    private UasAlertResult recordAlert(UUID playerId, String title, String message, String tone, int durationMs) {
        if (playerId == null) {
            return UasAlertResult.fail("Player is required", null);
        }
        alerts.add(new Alert(playerId, title, message, tone, durationMs));
        return UasAlertResult.ok(playerId);
    }

    private UasCashResult adjustCash(Map<UUID, Map<Integer, Integer>> storage,
                                     UUID playerId,
                                     UasCashKind kind,
                                     int denomination,
                                     int count,
                                     String type,
                                     boolean give) {
        if (playerId == null || denomination <= 0 || count <= 0) {
            cashMutations.add(new CashMutation(playerId, kind, denomination, count, type, false));
            return UasCashResult.fail("Invalid cash request", kind, denomination, count);
        }

        String forcedFailure = give ? nextCashGiveFailureReason : nextCashTakeFailureReason;
        if (!give && forcedFailure == null && kind == UasCashKind.COIN) {
            forcedFailure = nextCashCoinTakeFailureReason;
        }
        if (forcedFailure != null) {
            if (give) {
                nextCashGiveFailureReason = null;
            } else if (kind == UasCashKind.COIN && forcedFailure.equals(nextCashCoinTakeFailureReason)) {
                nextCashCoinTakeFailureReason = null;
            } else {
                nextCashTakeFailureReason = null;
            }
            cashMutations.add(new CashMutation(playerId, kind, denomination, count, type, false));
            return UasCashResult.fail(forcedFailure, kind, denomination, count);
        }

        int available = getCash(storage, playerId, denomination);
        if (!give && available < count) {
            cashMutations.add(new CashMutation(playerId, kind, denomination, count, type, false));
            return UasCashResult.fail("Not enough matching cash", kind, denomination, count);
        }

        setCash(storage, playerId, denomination, give ? available + count : available - count);
        cashMutations.add(new CashMutation(playerId, kind, denomination, count, type, true));
        return UasCashResult.ok(kind, denomination, count);
    }

    private void setCash(Map<UUID, Map<Integer, Integer>> storage, UUID playerId, int denomination, int count) {
        if (playerId == null || denomination <= 0) {
            return;
        }
        Map<Integer, Integer> cash = storage.computeIfAbsent(playerId, ignored -> new HashMap<>());
        if (count <= 0) {
            cash.remove(denomination);
        } else {
            cash.put(denomination, count);
        }
    }

    private int getCash(Map<UUID, Map<Integer, Integer>> storage, UUID playerId, int denomination) {
        if (playerId == null || denomination <= 0) {
            return 0;
        }
        return storage.getOrDefault(playerId, Map.of()).getOrDefault(denomination, 0);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
