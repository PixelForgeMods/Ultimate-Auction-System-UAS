package net.austizz.ultimate_auction_system.banking;

import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class UasMoneyFormatter {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000L);
    private static final String[] SCALE_SUFFIXES = {"", "K", "M", "B", "T"};
    private static final BigDecimal[] SCALE_DIVISORS = {
            BigDecimal.ONE,
            BigDecimal.valueOf(1_000L),
            BigDecimal.valueOf(1_000_000L),
            BigDecimal.valueOf(1_000_000_000L),
            BigDecimal.valueOf(1_000_000_000_000L)
    };

    private UasMoneyFormatter() {
    }

    public static String display(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        String apiFormatted = formatThroughUbsApi(safeAmount);
        return apiFormatted == null ? "$" + abbreviateRounded(safeAmount) : apiFormatted;
    }

    private static String formatThroughUbsApi(BigDecimal amount) {
        try {
            Object api = UltimateBankingApiProvider.get();
            if (api == null) {
                return null;
            }
            for (Class<?> contract : api.getClass().getInterfaces()) {
                if (!"net.austizz.ultimatebankingsystem.api.UltimateBankingApi".equals(contract.getName())) {
                    continue;
                }
                Method method = contract.getMethod("formatMoneyRounded", BigDecimal.class);
                Object formatted = method.invoke(api, amount);
                return formatted instanceof String text && !text.isBlank() ? text : null;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static String abbreviateRounded(BigDecimal amount) {
        BigDecimal abs = amount.abs();
        int scaleIndex = 0;
        for (int i = SCALE_DIVISORS.length - 1; i >= 1; i--) {
            if (abs.compareTo(SCALE_DIVISORS[i]) >= 0) {
                scaleIndex = i;
                break;
            }
        }

        BigDecimal shortened = amount.divide(SCALE_DIVISORS[scaleIndex], 2, RoundingMode.HALF_UP);
        if (scaleIndex < SCALE_SUFFIXES.length - 1 && shortened.abs().compareTo(THOUSAND) >= 0) {
            scaleIndex++;
            shortened = amount.divide(SCALE_DIVISORS[scaleIndex], 2, RoundingMode.HALF_UP);
        }
        return shortened.stripTrailingZeros().toPlainString() + SCALE_SUFFIXES[scaleIndex];
    }
}
