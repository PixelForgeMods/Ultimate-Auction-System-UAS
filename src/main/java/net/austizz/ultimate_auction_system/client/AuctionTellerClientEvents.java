package net.austizz.ultimate_auction_system.client;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimate_auction_system.registry.UasEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = UltimateAuctionSystem.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AuctionTellerClientEvents {
    private AuctionTellerClientEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(UasEntities.AUCTION_TELLER.get(), AuctionTellerRenderer::new);
    }
}
