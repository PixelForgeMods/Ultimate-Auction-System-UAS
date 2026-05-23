package net.austizz.ultimate_auction_system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionSavedData extends SavedData {
    private static final String DATA_NAME = "ultimate_auction_system_auctions";
    private static final String AUCTIONS_TAG = "auctions";

    private final ConcurrentHashMap<UUID, AuctionItem> auctions;
    private final int skippedRecords;
    private final int repairedRecords;

    public AuctionSavedData() {
        this(new ConcurrentHashMap<>(), 0, 0);
    }

    private AuctionSavedData(ConcurrentHashMap<UUID, AuctionItem> auctions, int skippedRecords, int repairedRecords) {
        this.auctions = auctions;
        this.skippedRecords = skippedRecords;
        this.repairedRecords = repairedRecords;
    }

    public static SavedData.Factory<AuctionSavedData> factory() {
        return new SavedData.Factory<>(
                AuctionSavedData::new,
                AuctionSavedData::load,
                null
        );
    }

    public static AuctionSavedData get(MinecraftServer server) {
        if (server == null) {
            throw new IllegalStateException("Minecraft server is unavailable");
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld level is unavailable");
        }
        return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static AuctionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ConcurrentHashMap<UUID, AuctionItem> loaded = new ConcurrentHashMap<>();
        int skipped = 0;
        int repaired = 0;
        ListTag auctionTags = tag.getList(AUCTIONS_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : auctionTags) {
            if (!(raw instanceof CompoundTag auctionTag)) {
                skipped++;
                continue;
            }
            try {
                Optional<AuctionItem> loadedAuction = AuctionItem.load(auctionTag, registries);
                if (loadedAuction.isEmpty()) {
                    skipped++;
                    UltimateAuctionSystem.LOGGER.warn("[UAS] Skipped incomplete or invalid auction record during recovery.");
                    continue;
                }

                AuctionItem auction = loadedAuction.get();
                if (auction.repairLoadedBidState()) {
                    repaired++;
                    UltimateAuctionSystem.LOGGER.warn("[UAS] Repaired bid/highest-bid state for auction {} during recovery.", auction.getAuctionId());
                }

                if (loaded.putIfAbsent(auction.getAuctionId(), auction) != null) {
                    skipped++;
                    UltimateAuctionSystem.LOGGER.warn("[UAS] Skipped duplicate auction record {} during recovery.", auction.getAuctionId());
                }
            } catch (RuntimeException exception) {
                skipped++;
                UltimateAuctionSystem.LOGGER.warn("[UAS] Skipped invalid auction record during load: {}", exception.getMessage());
            }
        }
        AuctionSavedData savedData = new AuctionSavedData(loaded, skipped, repaired);
        if (repaired > 0) {
            savedData.markChanged();
        }
        return savedData;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag auctionTags = new ListTag();
        for (AuctionItem auction : auctions.values()) {
            if (auction == null || !auction.isPersistable()) {
                UltimateAuctionSystem.LOGGER.warn("[UAS] Skipped non-persistable auction record during save.");
                continue;
            }
            try {
                auctionTags.add(auction.save(registries));
            } catch (RuntimeException exception) {
                UltimateAuctionSystem.LOGGER.error(
                        "[UAS] Skipped auction {} during save because it could not be serialized.",
                        auction.getAuctionId(),
                        exception
                );
            }
        }
        tag.put(AUCTIONS_TAG, auctionTags);
        return tag;
    }

    public ConcurrentHashMap<UUID, AuctionItem> getAuctions() {
        return auctions;
    }

    public int getSkippedRecords() {
        return skippedRecords;
    }

    public int getRepairedRecords() {
        return repairedRecords;
    }

    public void markChanged() {
        setDirty();
    }
}
