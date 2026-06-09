package net.austizz.ultimate_auction_system.registry;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimate_auction_system.entity.AuctionTellerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class UasEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, UltimateAuctionSystem.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<AuctionTellerEntity>> AUCTION_TELLER =
            ENTITY_TYPES.register("auction_teller", () ->
                    EntityType.Builder.of(AuctionTellerEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build(UltimateAuctionSystem.MODID + ":auction_teller"));

    private UasEntities() {
    }

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
