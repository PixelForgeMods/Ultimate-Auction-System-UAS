package net.austizz.ultimate_auction_system.registry;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimate_auction_system.block.AuctionTerminalBlock;
import net.austizz.ultimate_auction_system.display.AuctionDisplayBlock;
import net.austizz.ultimate_auction_system.item.AuctionTellerSpawnEggItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class UasBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(UltimateAuctionSystem.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UltimateAuctionSystem.MODID);

    public static final DeferredBlock<Block> AUCTION_TERMINAL = registerBlock("auction_terminal",
            () -> new AuctionTerminalBlock(BlockBehaviour.Properties.of()
                    .strength(3.0f)
                    .sound(SoundType.METAL)
            ));
    public static final DeferredBlock<Block> AUCTION_DISPLAY = registerBlock("auction_display",
            () -> new AuctionDisplayBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(-1.0F, 3_600_000.0F)
                    .noLootTable()
            ));
    public static final DeferredItem<Item> AUCTION_TELLER_SPAWN_EGG =
            ITEMS.register("auction_teller_spawn_egg", AuctionTellerSpawnEggItem::new);

    private UasBlocks() {
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, java.util.function.Supplier<T> block) {
        DeferredBlock<T> registered = BLOCKS.register(name, block);
        ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
