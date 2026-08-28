package net.austizz.ultimate_auction_system.banking;

public record UasCashResult(
        boolean success,
        String reason,
        UasCashKind kind,
        int denomination,
        int count,
        long totalCents
) {
    public UasCashResult {
        reason = reason == null ? "" : reason;
        kind = kind == null ? UasCashKind.BILL : kind;
        denomination = Math.max(0, denomination);
        count = Math.max(0, count);
        totalCents = Math.max(0L, totalCents);
    }

    public static UasCashResult ok(UasCashKind kind, int denomination, int count) {
        return new UasCashResult(true, "", kind, denomination, count, totalCents(kind, denomination, count));
    }

    public static UasCashResult fail(String reason, UasCashKind kind, int denomination, int count) {
        return new UasCashResult(
                false,
                reason == null || reason.isBlank() ? "UBS cash operation failed" : reason,
                kind,
                denomination,
                count,
                0L
        );
    }

    public static long totalCents(UasCashKind kind, int denomination, int count) {
        if (denomination <= 0 || count <= 0) {
            return 0L;
        }
        long units = (long) denomination * (long) count;
        return kind == UasCashKind.BILL ? units * 100L : units;
    }
}
