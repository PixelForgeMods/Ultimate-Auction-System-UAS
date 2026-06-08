package net.austizz.ultimate_auction_system.banking;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public record UasCashBreakdown(
        Map<Integer, Integer> dollarBills,
        Map<Integer, Integer> coins
) {
    public UasCashBreakdown {
        dollarBills = normalize(dollarBills);
        coins = normalize(coins);
    }

    public static UasCashBreakdown empty() {
        return new UasCashBreakdown(Map.of(), Map.of());
    }

    public boolean isEmpty() {
        return dollarBills.isEmpty() && coins.isEmpty();
    }

    public long totalCents() {
        long total = 0L;
        for (Map.Entry<Integer, Integer> entry : dollarBills.entrySet()) {
            total = Math.addExact(total, Math.multiplyExact(Math.multiplyExact((long) entry.getKey(), 100L), entry.getValue()));
        }
        for (Map.Entry<Integer, Integer> entry : coins.entrySet()) {
            total = Math.addExact(total, Math.multiplyExact((long) entry.getKey(), entry.getValue()));
        }
        return total;
    }

    public BigDecimal totalDollars() {
        return BigDecimal.valueOf(totalCents(), 2);
    }

    private static Map<Integer, Integer> normalize(Map<Integer, Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<Integer, Integer> normalized = new LinkedHashMap<>();
        raw.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .filter(entry -> entry.getKey() > 0 && entry.getValue() > 0)
                .sorted(Map.Entry.<Integer, Integer>comparingByKey(Comparator.reverseOrder()))
                .forEach(entry -> normalized.merge(entry.getKey(), entry.getValue(), Integer::sum));
        return Map.copyOf(normalized);
    }
}
