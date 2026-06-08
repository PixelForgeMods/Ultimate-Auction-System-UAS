package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.api.UasPermissionAction;
import net.austizz.ultimate_auction_system.api.UasPermissions;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionRateLimiter {
    private static final ConcurrentHashMap<UUID, EnumMap<Action, Long>> LAST_ACTIONS = new ConcurrentHashMap<>();

    private AuctionRateLimiter() {
    }

    public enum Action {
        CREATE,
        BID,
        BUYOUT,
        CANCEL,
        SEARCH
    }

    public static AuctionActionResult checkAndMark(ServerPlayer player, Action action) {
        if (player == null) {
            return AuctionActionResult.ok("");
        }
        boolean bypass = UasPermissions.has(player, UasPermissionAction.ADMIN);
        return checkAndMark(player.getUUID(), bypass, action, System.currentTimeMillis());
    }

    static AuctionActionResult checkAndMark(UUID playerId, boolean bypass, Action action, long nowMillis) {
        if (playerId == null || action == null || bypass) {
            return AuctionActionResult.ok("");
        }

        int cooldownSeconds = cooldownSeconds(action);
        if (cooldownSeconds <= 0) {
            return AuctionActionResult.ok("");
        }

        long cooldownMillis = cooldownSeconds * 1_000L;
        EnumMap<Action, Long> playerActions = LAST_ACTIONS.computeIfAbsent(playerId, ignored -> new EnumMap<>(Action.class));
        synchronized (playerActions) {
            long last = playerActions.getOrDefault(action, Long.MIN_VALUE);
            long elapsed = nowMillis - last;
            if (last != Long.MIN_VALUE && elapsed >= 0L && elapsed < cooldownMillis) {
                long remainingSeconds = Math.max(1L, (cooldownMillis - elapsed + 999L) / 1_000L);
                return AuctionActionResult.fail("Rate limited. Try again in " + remainingSeconds + " seconds.");
            }
            playerActions.put(action, nowMillis);
            return AuctionActionResult.ok("");
        }
    }

    static void clearForTesting() {
        LAST_ACTIONS.clear();
    }

    private static int cooldownSeconds(Action action) {
        return switch (action) {
            case CREATE -> Config.createCooldownSeconds;
            case BID -> Config.bidCooldownSeconds;
            case BUYOUT -> Config.buyoutCooldownSeconds;
            case CANCEL -> Config.cancelCooldownSeconds;
            case SEARCH -> Config.searchCooldownSeconds;
        };
    }
}
