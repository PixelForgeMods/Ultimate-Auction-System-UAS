package net.austizz.ultimate_auction_system.registry;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimate_auction_system.display.AuctionDisplayBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class UasBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UltimateAuctionSystem.MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AuctionDisplayBlockEntity>> AUCTION_DISPLAY =
            BLOCK_ENTITIES.register("auction_display", () ->
                    BlockEntityType.Builder.of(AuctionDisplayBlockEntity::new, UasBlocks.AUCTION_DISPLAY.get()).build(null));

    private UasBlockEntities() {
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
