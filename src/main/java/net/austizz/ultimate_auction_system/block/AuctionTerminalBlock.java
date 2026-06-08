package net.austizz.ultimate_auction_system.block;

import net.austizz.ultimate_auction_system.Config;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimate_auction_system.api.UasPermissionAction;
import net.austizz.ultimate_auction_system.api.UasPermissions;
import net.austizz.ultimate_auction_system.i18n.UasTranslations;
import net.austizz.ultimate_auction_system.network.UasPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class AuctionTerminalBlock extends Block {
    public AuctionTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                              BlockState state,
                                              Level level,
                                              BlockPos pos,
                                              Player player,
                                              InteractionHand hand,
                                              BlockHitResult hitResult) {
        openTerminal(level, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hitResult) {
        openTerminal(level, player);
        return InteractionResult.SUCCESS;
    }

    private static void openTerminal(Level level, Player player) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!Config.auctionTerminalEnabled) {
            serverPlayer.sendSystemMessage(UasTranslations.tr("Auction terminals are disabled on this server.").withStyle(ChatFormatting.RED));
            return;
        }
        if (UltimateAuctionSystem.auctionHouse == null) {
            serverPlayer.sendSystemMessage(UasTranslations.tr("Auction house is not initialized.").withStyle(ChatFormatting.RED));
            return;
        }
        var permission = UasPermissions.check(serverPlayer, UasPermissionAction.TERMINAL);
        if (!permission.success()) {
            serverPlayer.sendSystemMessage(UasTranslations.tr(permission.message()).withStyle(ChatFormatting.RED));
            return;
        }
        UasPayloads.openAuctionHouse(serverPlayer);
    }
}
