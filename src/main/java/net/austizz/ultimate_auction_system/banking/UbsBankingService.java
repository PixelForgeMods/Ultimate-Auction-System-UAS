package net.austizz.ultimate_auction_system.banking;

import net.austizz.ultimatebankingsystem.api.ApiAccountSnapshot;
import net.austizz.ultimatebankingsystem.api.ApiResult;
import net.austizz.ultimatebankingsystem.api.ApiTransactionResult;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApi;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;

import java.math.BigDecimal;
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
    public Optional<UUID> getPrimaryAccountId(UUID playerId) {
        return api == null ? Optional.empty() : api.getPrimaryAccountId(playerId);
    }

    @Override
    public Optional<UasAccountSnapshot> getAccountSnapshot(UUID accountId) {
        if (api == null) {
            return Optional.empty();
        }
        return api.getAccountSnapshot(accountId).map(this::fromSnapshot);
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

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
