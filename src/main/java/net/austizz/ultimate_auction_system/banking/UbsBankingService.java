package net.austizz.ultimate_auction_system.banking;

import net.austizz.ultimatebankingsystem.api.ApiAccountSnapshot;
import net.austizz.ultimatebankingsystem.api.ApiAlertResult;
import net.austizz.ultimatebankingsystem.api.ApiCashResult;
import net.austizz.ultimatebankingsystem.api.ApiItemResult;
import net.austizz.ultimatebankingsystem.api.ApiResult;
import net.austizz.ultimatebankingsystem.api.ApiTransactionResult;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApi;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class UbsBankingService implements UasBankingService {
    private final UltimateBankingApi api;

    public UbsBankingService() {
        this(UltimateBankingApiProvider.get());
    }

    public UbsBankingService(UltimateBankingApi api) {
        this.api = api;
    }

    @Override
    public boolean isAvailable() {
        return api != null && api.isServerAvailable();
    }

    @Override
    public String getApiVersion() {
        return api == null ? "unavailable" : api.getApiVersion();
    }

    @Override
    public UasBankingResult getBalance(UUID accountId) {
        if (api == null) {
            return UasBankingResult.fail("UBS API is unavailable", BigDecimal.ZERO);
        }
        return fromApiResult(api.getBalance(accountId));
    }

    @Override
    public UasBankingResult deposit(UUID accountId, BigDecimal amount, String reference) {
        if (api == null) {
            return UasBankingResult.fail("UBS API is unavailable", BigDecimal.ZERO);
        }
        return fromTransactionResult(api.deposit(accountId, safeAmount(amount), reference));
    }

    @Override
    public UasBankingResult withdraw(UUID accountId, BigDecimal amount, String reference) {
        if (api == null) {
            return UasBankingResult.fail("UBS API is unavailable", BigDecimal.ZERO);
        }
        return fromTransactionResult(api.withdraw(accountId, safeAmount(amount), reference));
    }

    @Override
    public UasBankingResult transfer(UUID senderAccountId, UUID receiverAccountId, BigDecimal amount, String reference) {
        if (api == null) {
            return UasBankingResult.fail("UBS API is unavailable", BigDecimal.ZERO);
        }
        return fromTransactionResult(api.transfer(senderAccountId, receiverAccountId, safeAmount(amount), reference));
    }

    @Override
    public UasBankingResult transferFromPrimary(UUID senderPlayerId, UUID receiverAccountId, BigDecimal amount, String reference) {
        if (api == null) {
            return UasBankingResult.fail("UBS API is unavailable", BigDecimal.ZERO);
        }
        return fromTransactionResult(api.transferFromPrimary(senderPlayerId, receiverAccountId, safeAmount(amount), reference));
    }

    @Override
    public UasBankingResult transferToPrimary(UUID senderAccountId, UUID receiverPlayerId, BigDecimal amount, String reference) {
        if (api == null) {
            return UasBankingResult.fail("UBS API is unavailable", BigDecimal.ZERO);
        }
        return fromTransactionResult(api.transferToPrimary(senderAccountId, receiverPlayerId, safeAmount(amount), reference));
    }

    @Override
    public UasBankingResult validateCanSend(UUID accountId, BigDecimal amount) {
        if (api == null) {
            return UasBankingResult.fail("UBS API is unavailable", BigDecimal.ZERO);
        }
        return fromApiResult(api.validateAccountCanSend(accountId, safeAmount(amount)));
    }

    @Override
    public UasBankingResult validateCanReceive(UUID accountId) {
        if (api == null) {
            return UasBankingResult.fail("UBS API is unavailable", BigDecimal.ZERO);
        }
        return fromApiResult(api.validateAccountCanReceive(accountId));
    }

    @Override
    public boolean playerHasAnyAccount(UUID playerId) {
        return api != null && api.playerHasAnyAccount(playerId);
    }

    @Override
    public boolean playerHasPrimaryAccount(UUID playerId) {
        return api != null && api.playerHasPrimaryAccount(playerId);
    }

    @Override
    public boolean playerHasAvailableAccount(UUID playerId) {
        return api != null && api.playerHasAvailableAccount(playerId);
    }

    @Override
    public boolean playerHasAvailablePrimaryAccount(UUID playerId) {
        return api != null && api.playerHasAvailablePrimaryAccount(playerId);
    }

    @Override
    public boolean playerHasFrozenAccount(UUID playerId) {
        return api != null && api.playerHasFrozenAccount(playerId);
    }

    @Override
    public boolean accountCanSend(UUID accountId, BigDecimal amount) {
        return api != null && api.accountCanSend(accountId, safeAmount(amount));
    }

    @Override
    public boolean accountCanReceive(UUID accountId) {
        return api != null && api.accountCanReceive(accountId);
    }

    @Override
    public boolean primaryAccountCanSend(UUID playerId, BigDecimal amount) {
        return api != null && api.primaryAccountCanSend(playerId, safeAmount(amount));
    }

    @Override
    public boolean primaryAccountCanReceive(UUID playerId) {
        return api != null && api.primaryAccountCanReceive(playerId);
    }

    @Override
    public Optional<UUID> getPrimaryAccountId(UUID playerId) {
        return api == null ? Optional.empty() : api.getPrimaryAccountId(playerId);
    }

    @Override
    public List<UasAccountSnapshot> getPlayerAccounts(UUID playerId) {
        if (api == null || playerId == null) {
            return List.of();
        }
        return api.getPlayerAccounts(playerId).stream()
                .map(this::fromSnapshot)
                .toList();
    }

    @Override
    public Optional<UasAccountSnapshot> getAccountSnapshot(UUID accountId) {
        if (api == null) {
            return Optional.empty();
        }
        return api.getAccountSnapshot(accountId).map(this::fromSnapshot);
    }

    @Override
    public boolean playerOwnsAccount(UUID playerId, UUID accountId) {
        return api != null && playerId != null && accountId != null && api.playerOwnsAccount(playerId, accountId);
    }

    @Override
    public List<Integer> getSupportedCashBillDenominations() {
        return api == null ? List.of() : api.getSupportedBillDenominations();
    }

    @Override
    public List<Integer> getSupportedCashCoinDenominations() {
        return api == null ? List.of() : api.getSupportedCoinDenominations();
    }

    @Override
    public int getCashBillCount(UUID playerId, int denomination) {
        return api == null || playerId == null ? 0 : api.getPlayerBillCount(playerId, denomination);
    }

    @Override
    public int getCashCoinCount(UUID playerId, int denominationCents) {
        return api == null || playerId == null ? 0 : api.getPlayerCoinCount(playerId, denominationCents);
    }

    @Override
    public UasCashResult giveCashBills(UUID playerId, int denomination, int billCount) {
        if (api == null) {
            return UasCashResult.fail("UBS API is unavailable", UasCashKind.BILL, denomination, billCount);
        }
        return fromCashResult(api.giveDollarBills(playerId, denomination, billCount), UasCashKind.BILL);
    }

    @Override
    public UasCashResult takeCashBills(UUID playerId, int denomination, int billCount) {
        if (api == null) {
            return UasCashResult.fail("UBS API is unavailable", UasCashKind.BILL, denomination, billCount);
        }
        return fromCashResult(api.takeDollarBills(playerId, denomination, billCount), UasCashKind.BILL);
    }

    @Override
    public UasCashResult giveCashCoins(UUID playerId, int denominationCents, int coinCount) {
        if (api == null) {
            return UasCashResult.fail("UBS API is unavailable", UasCashKind.COIN, denominationCents, coinCount);
        }
        return fromCashResult(api.giveCoins(playerId, denominationCents, coinCount), UasCashKind.COIN);
    }

    @Override
    public UasCashResult takeCashCoins(UUID playerId, int denominationCents, int coinCount) {
        if (api == null) {
            return UasCashResult.fail("UBS API is unavailable", UasCashKind.COIN, denominationCents, coinCount);
        }
        return fromCashResult(api.takeCoins(playerId, denominationCents, coinCount), UasCashKind.COIN);
    }

    @Override
    public UasItemResult issueCheque(UUID sourceAccountId,
                                     UUID recipientPlayerId,
                                     long amountDollars,
                                     UUID issuerPlayerId,
                                     String issuerName,
                                     String recipientName) {
        if (api == null) {
            return UasItemResult.fail("UBS API is unavailable");
        }
        return fromItemResult(api.issueCheque(
                sourceAccountId,
                recipientPlayerId,
                amountDollars,
                issuerPlayerId,
                issuerName,
                recipientName
        ));
    }

    @Override
    public UasAlertResult sendSuccessAlert(UUID playerId, String title, String message, int durationMs) {
        return sendAlert(playerId, title, message, durationMs, "SUCCESS");
    }

    @Override
    public UasAlertResult sendErrorAlert(UUID playerId, String title, String message, int durationMs) {
        return sendAlert(playerId, title, message, durationMs, "ERROR");
    }

    @Override
    public UasAlertResult sendInfoAlert(UUID playerId, String title, String message, int durationMs) {
        return sendAlert(playerId, title, message, durationMs, "INFO");
    }

    @Override
    public UasAlertResult sendWarningAlert(UUID playerId, String title, String message, int durationMs) {
        return sendAlert(playerId, title, message, durationMs, "WARNING");
    }

    private UasBankingResult fromApiResult(ApiResult result) {
        if (result == null) {
            return UasBankingResult.fail("UBS returned no result", BigDecimal.ZERO);
        }
        return result.success()
                ? UasBankingResult.ok(result.balanceAfter())
                : UasBankingResult.fail(result.reason(), result.balanceAfter());
    }

    private UasBankingResult fromTransactionResult(ApiTransactionResult result) {
        if (result == null) {
            return UasBankingResult.fail("UBS returned no transaction result", BigDecimal.ZERO);
        }
        return result.success()
                ? UasBankingResult.ok(result.balanceAfter(), result.transactionId(), result.description())
                : UasBankingResult.fail(result.reason(), result.balanceAfter());
    }

    private UasCashResult fromCashResult(ApiCashResult result, UasCashKind kind) {
        if (result == null) {
            return UasCashResult.fail("UBS returned no cash result", kind, 0, 0);
        }
        return result.success()
                ? UasCashResult.ok(kind, result.denomination(), result.billCount())
                : UasCashResult.fail(result.reason(), kind, result.denomination(), result.billCount());
    }

    private UasItemResult fromItemResult(ApiItemResult result) {
        if (result == null) {
            return UasItemResult.fail("UBS returned no item result");
        }
        return result.success()
                ? UasItemResult.ok(result.itemStack(), result.referenceId(), result.amount())
                : UasItemResult.fail(result.reason());
    }

    private UasAccountSnapshot fromSnapshot(ApiAccountSnapshot snapshot) {
        return new UasAccountSnapshot(
                snapshot.accountId(),
                snapshot.playerId(),
                snapshot.bankId(),
                snapshot.accountType(),
                snapshot.accountTypeLabel(),
                snapshot.balance(),
                snapshot.primary(),
                snapshot.frozen(),
                snapshot.frozenReason()
        );
    }

    private UasAlertResult sendAlert(UUID playerId, String title, String message, int durationMs, String tone) {
        if (api == null) {
            return UasAlertResult.fail("UBS API is unavailable", playerId);
        }
        String safeTitle = title == null || title.isBlank() ? "Auction House" : title;
        String safeMessage = message == null ? "" : message;
        ApiAlertResult result = switch (tone) {
            case "SUCCESS" -> api.sendSuccessUiAlert(playerId, safeTitle, safeMessage, durationMs);
            case "ERROR" -> api.sendErrorUiAlert(playerId, safeTitle, safeMessage, durationMs);
            case "WARNING" -> api.sendWarningUiAlert(playerId, safeTitle, safeMessage, durationMs);
            default -> api.sendInfoUiAlert(playerId, safeTitle, safeMessage, durationMs);
        };
        if (result == null) {
            return UasAlertResult.fail("UBS returned no alert result", playerId);
        }
        return result.success()
                ? UasAlertResult.ok(playerId)
                : UasAlertResult.fail(result.reason(), playerId);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
