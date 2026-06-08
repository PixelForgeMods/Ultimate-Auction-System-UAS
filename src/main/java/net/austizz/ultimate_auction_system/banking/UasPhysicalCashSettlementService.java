package net.austizz.ultimate_auction_system.banking;

import net.austizz.ultimate_auction_system.Config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class UasPhysicalCashSettlementService {
    private final UasBankingService bankingService;

    public UasPhysicalCashSettlementService() {
        this(new UbsBankingService());
    }

    public UasPhysicalCashSettlementService(UasBankingService bankingService) {
        this.bankingService = bankingService;
    }

    public UasCashSettlementResult takeExactCash(UUID playerId,
                                                 BigDecimal expectedAmount,
                                                 UasCashBreakdown breakdown,
                                                 UasCashSettlementUse use) {
        UasCashSettlementResult validation = validate(playerId, expectedAmount, breakdown, use, true);
        if (!validation.success()) {
            return validation;
        }

        List<TakenCash> takenCash = new ArrayList<>();
        List<UasCashResult> mutations = new ArrayList<>();
        for (Map.Entry<Integer, Integer> bill : breakdown.dollarBills().entrySet()) {
            UasCashResult result = bankingService.takeCashBills(playerId, bill.getKey(), bill.getValue());
            mutations.add(result);
            if (!result.success()) {
                return compensateFailedTake(playerId, expectedAmount, breakdown, use, result.reason(), takenCash, mutations);
            }
            takenCash.add(new TakenCash(UasCashKind.BILL, bill.getKey(), bill.getValue()));
        }
        for (Map.Entry<Integer, Integer> coin : breakdown.coins().entrySet()) {
            UasCashResult result = bankingService.takeCashCoins(playerId, coin.getKey(), coin.getValue());
            mutations.add(result);
            if (!result.success()) {
                return compensateFailedTake(playerId, expectedAmount, breakdown, use, result.reason(), takenCash, mutations);
            }
            takenCash.add(new TakenCash(UasCashKind.COIN, coin.getKey(), coin.getValue()));
        }
        return UasCashSettlementResult.ok(use, playerId, expectedAmount, breakdown, mutations);
    }

    public UasCashSettlementResult giveExactCash(UUID playerId,
                                                 BigDecimal expectedAmount,
                                                 UasCashBreakdown breakdown,
                                                 UasCashSettlementUse use) {
        UasCashSettlementResult validation = validate(playerId, expectedAmount, breakdown, use, false);
        if (!validation.success()) {
            return validation;
        }

        List<UasCashResult> mutations = new ArrayList<>();
        for (Map.Entry<Integer, Integer> bill : breakdown.dollarBills().entrySet()) {
            UasCashResult result = bankingService.giveCashBills(playerId, bill.getKey(), bill.getValue());
            mutations.add(result);
            if (!result.success()) {
                return UasCashSettlementResult.fail("UBS could not give exact cash: " + result.reason(), use, playerId, expectedAmount, breakdown, mutations, false, false);
            }
        }
        for (Map.Entry<Integer, Integer> coin : breakdown.coins().entrySet()) {
            UasCashResult result = bankingService.giveCashCoins(playerId, coin.getKey(), coin.getValue());
            mutations.add(result);
            if (!result.success()) {
                return UasCashSettlementResult.fail("UBS could not give exact cash: " + result.reason(), use, playerId, expectedAmount, breakdown, mutations, false, false);
            }
        }
        return UasCashSettlementResult.ok(use, playerId, expectedAmount, breakdown, mutations);
    }

    private UasCashSettlementResult validate(UUID playerId,
                                             BigDecimal expectedAmount,
                                             UasCashBreakdown rawBreakdown,
                                             UasCashSettlementUse use,
                                             boolean requirePlayerCash) {
        UasCashBreakdown breakdown = rawBreakdown == null ? UasCashBreakdown.empty() : rawBreakdown;
        if (use == null) {
            return UasCashSettlementResult.fail("Cash settlement flow is required.", null, playerId, expectedAmount, breakdown);
        }
        if (!Config.isPhysicalCashSettlementEnabled(use)) {
            return UasCashSettlementResult.fail("Physical UBS cash settlement is disabled for " + flowName(use) + ".", use, playerId, expectedAmount, breakdown);
        }
        if (bankingService == null || !bankingService.isAvailable()) {
            return UasCashSettlementResult.fail("UBS API is unavailable.", use, playerId, expectedAmount, breakdown);
        }
        if (playerId == null) {
            return UasCashSettlementResult.fail("Player id is required for cash settlement.", use, null, expectedAmount, breakdown);
        }
        if (breakdown.isEmpty()) {
            return UasCashSettlementResult.fail("Cash settlement requires at least one UBS bill or coin.", use, playerId, expectedAmount, breakdown);
        }

        Long expectedCents = toCents(expectedAmount);
        if (expectedCents == null || expectedCents <= 0L) {
            return UasCashSettlementResult.fail("Cash settlement amount must be a positive dollars-and-cents value.", use, playerId, expectedAmount, breakdown);
        }
        long actualCents;
        try {
            actualCents = breakdown.totalCents();
        } catch (ArithmeticException exception) {
            return UasCashSettlementResult.fail("Cash denomination total is too large.", use, playerId, expectedAmount, breakdown);
        }
        if (actualCents != expectedCents) {
            return UasCashSettlementResult.fail("Cash denominations must exactly equal $" + BigDecimal.valueOf(expectedCents, 2).toPlainString() + ".", use, playerId, expectedAmount, breakdown);
        }

        Set<Integer> supportedBills = new HashSet<>(bankingService.getSupportedCashBillDenominations());
        Set<Integer> supportedCoins = new HashSet<>(bankingService.getSupportedCashCoinDenominations());
        for (Map.Entry<Integer, Integer> bill : breakdown.dollarBills().entrySet()) {
            if (!supportedBills.contains(bill.getKey())) {
                return UasCashSettlementResult.fail("Unsupported UBS bill denomination: $" + bill.getKey() + ".", use, playerId, expectedAmount, breakdown);
            }
            if (requirePlayerCash && bankingService.getCashBillCount(playerId, bill.getKey()) < bill.getValue()) {
                return UasCashSettlementResult.fail("Player does not have enough $" + bill.getKey() + " UBS bills.", use, playerId, expectedAmount, breakdown);
            }
        }
        for (Map.Entry<Integer, Integer> coin : breakdown.coins().entrySet()) {
            if (!supportedCoins.contains(coin.getKey())) {
                return UasCashSettlementResult.fail("Unsupported UBS coin denomination: " + coin.getKey() + " cents.", use, playerId, expectedAmount, breakdown);
            }
            if (requirePlayerCash && bankingService.getCashCoinCount(playerId, coin.getKey()) < coin.getValue()) {
                return UasCashSettlementResult.fail("Player does not have enough " + coin.getKey() + " cent UBS coins.", use, playerId, expectedAmount, breakdown);
            }
        }

        return UasCashSettlementResult.ok(use, playerId, expectedAmount, breakdown, List.of());
    }

    private UasCashSettlementResult compensateFailedTake(UUID playerId,
                                                         BigDecimal expectedAmount,
                                                         UasCashBreakdown breakdown,
                                                         UasCashSettlementUse use,
                                                         String failureReason,
                                                         List<TakenCash> takenCash,
                                                         List<UasCashResult> mutations) {
        boolean compensationSucceeded = true;
        for (int index = takenCash.size() - 1; index >= 0; index--) {
            TakenCash taken = takenCash.get(index);
            UasCashResult refund = taken.kind() == UasCashKind.BILL
                    ? bankingService.giveCashBills(playerId, taken.denomination(), taken.count())
                    : bankingService.giveCashCoins(playerId, taken.denomination(), taken.count());
            mutations.add(refund);
            compensationSucceeded &= refund.success();
        }

        String message = compensationSucceeded
                ? "UBS cash settlement failed and removed cash was returned: " + failureReason
                : "UBS cash settlement failed and some removed cash could not be returned: " + failureReason;
        return UasCashSettlementResult.fail(message, use, playerId, expectedAmount, breakdown, mutations, true, compensationSucceeded);
    }

    private Long toCents(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private String flowName(UasCashSettlementUse use) {
        return use == UasCashSettlementUse.BUYOUT ? "buyouts" : "listing fees";
    }

    private record TakenCash(UasCashKind kind, int denomination, int count) {
    }
}
