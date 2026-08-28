package net.austizz.ultimate_auction_system.registry;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimate_auction_system.entity.AuctionTellerEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = UltimateAuctionSystem.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class UasEntityEvents {
    private UasEntityEvents() {
    }

    @SubscribeEvent
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(UasEntities.AUCTION_TELLER.get(), AuctionTellerEntity.createAttributes().build());
    }
}
