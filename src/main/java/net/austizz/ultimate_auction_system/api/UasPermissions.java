package net.austizz.ultimate_auction_system.api;

import net.austizz.ultimate_auction_system.AuctionActionResult;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class UasPermissions {
    private static final AtomicReference<UasPermissionHook> HOOK = new AtomicReference<>();

    private UasPermissions() {
    }

    public static void setHook(UasPermissionHook hook) {
        HOOK.set(hook);
    }

    public static void clearHook() {
        HOOK.set(null);
    }

    public static AuctionActionResult check(ServerPlayer player, UasPermissionAction action) {
        if (has(player, action)) {
            return AuctionActionResult.ok("");
        }
        UasPermissionAction safeAction = action == null ? UasPermissionAction.LIST : action;
        return AuctionActionResult.fail(safeAction.deniedMessage());
    }

    public static boolean has(ServerPlayer player, UasPermissionAction action) {
        if (player == null || action == null) {
            return false;
        }
        UasPermissionHook hook = HOOK.get();
        if (hook != null) {
            try {
                Optional<Boolean> hookResult = hook.hasPermission(player, action);
                if (hookResult.isPresent()) {
                    return hookResult.get();
                }
            } catch (RuntimeException exception) {
                UltimateAuctionSystem.LOGGER.warn("[UAS] Permission hook failed for {} and action {}; falling back to permission level.",
                        player.getUUID(), action, exception);
            }
        }
        return hasPermissionLevel(player, action.requiredPermissionLevel());
    }

    public static AuctionActionResult checkCommandSource(CommandSourceStack source, UasPermissionAction action) {
        if (has(source, action)) {
            return AuctionActionResult.ok("");
        }
        UasPermissionAction safeAction = action == null ? UasPermissionAction.ADMIN : action;
        return AuctionActionResult.fail(safeAction.deniedMessage());
    }

    public static boolean has(CommandSourceStack source, UasPermissionAction action) {
        if (source == null || action == null) {
            return false;
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            return has(player, action);
        }
        return hasPermissionLevel(source, action.requiredPermissionLevel());
    }

    public static boolean hasPermissionLevel(ServerPlayer player, int requiredLevel) {
        return player != null && hasPermissionLevel(player::hasPermissions, requiredLevel);
    }

    public static boolean hasPermissionLevel(CommandSourceStack source, int requiredLevel) {
        return source != null && hasPermissionLevel(source::hasPermission, requiredLevel);
    }

    public static boolean hasPermissionLevel(PermissionLevelCheck check, int requiredLevel) {
        int level = Math.max(0, requiredLevel);
        return level == 0 || check.hasPermission(level);
    }

    @FunctionalInterface
    public interface PermissionLevelCheck {
        boolean hasPermission(int level);
    }
}
