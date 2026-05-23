package net.austizz.ultimate_auction_system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionSavedData extends SavedData {
    private static final String DATA_NAME = "ultimate_auction_system_auctions";
    private static final String AUCTIONS_TAG = "auctions";

    private final ConcurrentHashMap<UUID, AuctionItem> auctions;
    private int skippedRecords;

    public AuctionSavedData() {
        this(new ConcurrentHashMap<>(), 0);
    }

    private AuctionSavedData(ConcurrentHashMap<UUID, AuctionItem> auctions, int skippedRecords) {
        this.auctions = auctions;
        this.skippedRecords = skippedRecords;
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
        ListTag auctionTags = tag.getList(AUCTIONS_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : auctionTags) {
            if (!(raw instanceof CompoundTag auctionTag)) {
                skipped++;
                continue;
            }
            try {
                AuctionItem.load(auctionTag, registries).ifPresentOrElse(
                        auction -> loaded.put(auction.getAuctionId(), auction),
                        () -> {
                        }
                );
                if (!auctionTag.contains("auctionId") || !loaded.containsKey(auctionTag.getUUID("auctionId"))) {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                skipped++;
                UltimateAuctionSystem.LOGGER.warn("[UAS] Skipped invalid auction record during load: {}", exception.getMessage());
            }
        }
        return new AuctionSavedData(loaded, skipped);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag auctionTags = new ListTag();
        for (AuctionItem auction : auctions.values()) {
            if (auction != null) {
                auctionTags.add(auction.save(registries));
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

    public void markChanged() {
        setDirty();
    }
}
