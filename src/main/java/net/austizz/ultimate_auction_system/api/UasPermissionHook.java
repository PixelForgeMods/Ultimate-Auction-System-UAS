package net.austizz.ultimate_auction_system.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

@FunctionalInterface
public interface UasPermissionHook {
    Optional<Boolean> hasPermission(ServerPlayer player, UasPermissionAction action);
}
