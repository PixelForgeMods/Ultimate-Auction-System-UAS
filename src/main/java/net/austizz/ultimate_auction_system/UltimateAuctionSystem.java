package net.austizz.ultimate_auction_system;

import com.mojang.logging.LogUtils;
import net.austizz.ultimate_auction_system.registry.UasBlocks;
import net.austizz.ultimate_auction_system.registry.UasEntities;
import net.austizz.ultimate_auction_system.webadmin.UasDashboardBootstrap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(UltimateAuctionSystem.MODID)
public class UltimateAuctionSystem {
    public static final String MODID = "ultimate_auction_system";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static AuctionHouse auctionHouse;
    private long ticksSinceAuctionAutosave;
    private long ticksSinceSettlementScan;

    public UltimateAuctionSystem(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        UasBlocks.register(modEventBus);
        UasEntities.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        UasDependencyDiagnostics.validateRequiredUbs(LOGGER);
        UasDashboardBootstrap.register();
        LOGGER.info(
                "UAS config loaded: listingFeeRate={}, salesTaxRate={}, minimumBidIncrement=${}, maxListings={}, maxDurationHours={}",
                Config.listingFeeRate,
                Config.salesTaxRate,
                Config.minimumBidIncrementDollars,
                Config.maxActiveListingsPerPlayer,
                Config.maxAuctionDurationHours
        );
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("UltimateAuctionSystem Starting");
        UasDependencyDiagnostics.logServerDiagnostics(LOGGER);

        try {
            auctionHouse = AuctionHouse.load(event.getServer());
            LOGGER.info("[UAS] {}", auctionHouse.getStorageHealth().message());
        } catch (RuntimeException exception) {
            LOGGER.error("[UAS] Failed to load persistent auction storage; using in-memory fallback.", exception);
            auctionHouse = new AuctionHouse();
            auctionHouse.markStorageFailed("Persistent auction storage failed to load: " + exception.getMessage());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        AuctionHouse house = auctionHouse;
        if (house != null) {
            house.saveNow(event.getServer(), "Server stopping; auction storage flushed.");
            LOGGER.info("[UAS] {}", house.getStorageHealth().message());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        AuctionHouse house = auctionHouse;
        if (house == null) {
            return;
        }

        house.pruneExpiredPendingListings();
        ticksSinceSettlementScan++;
        if (ticksSinceSettlementScan >= 20 && event.hasTime()) {
            ticksSinceSettlementScan = 0L;
            house.notifyEndingSoonWatchlists();
            if (Config.autoSettleExpiredAuctions) {
                house.settleExpiredAuctions();
            }
        }

        ticksSinceAuctionAutosave++;
        int autosaveInterval = Math.max(Config.autosaveIntervalTicks, Config.DEFAULT_AUTOSAVE_INTERVAL_TICKS);
        if (ticksSinceAuctionAutosave < autosaveInterval) {
            return;
        }
        if (!event.hasTime()) {
            return;
        }

        ticksSinceAuctionAutosave = 0L;
        if (!house.autosave(event.getServer())) {
            LOGGER.warn("[UAS] {}", house.getStorageHealth().message());
        }
    }

}
