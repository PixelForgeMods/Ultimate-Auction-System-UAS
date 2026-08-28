package net.austizz.ultimate_auction_system.display;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = UltimateAuctionSystem.MODID)
public final class AuctionDisplayEvents {
    private AuctionDisplayEvents() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && event.getState().getBlock() == net.austizz.ultimate_auction_system.registry.UasBlocks.AUCTION_DISPLAY.get()
                && event.getLevel().getBlockEntity(event.getPos()) instanceof AuctionDisplayBlockEntity display
                && !display.canRemove(player)) {
            event.setCanceled(true);
        }
    }
}
