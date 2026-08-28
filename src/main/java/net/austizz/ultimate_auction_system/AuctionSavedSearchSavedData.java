package net.austizz.ultimate_auction_system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionSavedSearchSavedData extends SavedData {
    private static final String DATA_NAME = "ultimate_auction_system_saved_searches";
    private static final String SEARCHES_TAG = "searches";

    private final ConcurrentHashMap<UUID, List<AuctionSavedSearch>> searchesByPlayer;

    public AuctionSavedSearchSavedData() {
        this(new ConcurrentHashMap<>());
    }

    private AuctionSavedSearchSavedData(ConcurrentHashMap<UUID, List<AuctionSavedSearch>> searchesByPlayer) {
        this.searchesByPlayer = searchesByPlayer;
    }

    public static SavedData.Factory<AuctionSavedSearchSavedData> factory() {
        return new SavedData.Factory<>(
                AuctionSavedSearchSavedData::new,
                AuctionSavedSearchSavedData::load,
                null
        );
    }

    public static AuctionSavedSearchSavedData get(MinecraftServer server) {
        if (server == null) {
            throw new IllegalStateException("Minecraft server is unavailable");
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld level is unavailable");
        }
        return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    static AuctionSavedSearchSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ConcurrentHashMap<UUID, List<AuctionSavedSearch>> loaded = new ConcurrentHashMap<>();
        ListTag searchTags = tag.getList(SEARCHES_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : searchTags) {
            if (!(raw instanceof CompoundTag searchTag)) {
                continue;
            }
            AuctionSavedSearch search = AuctionSavedSearch.load(searchTag);
            if (search == null || search.playerId() == null) {
                continue;
            }
            loaded.computeIfAbsent(search.playerId(), ignored -> new ArrayList<>()).add(search);
        }
        loaded.replaceAll((playerId, searches) -> sortedLimited(searches));
        return new AuctionSavedSearchSavedData(loaded);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag searchTags = new ListTag();
        searchesByPlayer.values().forEach(searches -> searches.forEach(search -> searchTags.add(search.save())));
        tag.put(SEARCHES_TAG, searchTags);
        return tag;
    }

    public synchronized List<AuctionSavedSearch> list(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return List.copyOf(searchesByPlayer.getOrDefault(playerId, List.of()));
    }

    public synchronized AuctionActionResult saveSearch(UUID playerId, String rawName, AuctionUiQuery query) {
        if (playerId == null) {
            return AuctionActionResult.fail("Only players can save auction searches.");
        }
        String name = AuctionSavedSearch.sanitizeName(rawName);
        if (name.isBlank()) {
            return AuctionActionResult.fail("Saved search name is required.");
        }
        List<AuctionSavedSearch> searches = new ArrayList<>(searchesByPlayer.getOrDefault(playerId, List.of()));
        for (int index = 0; index < searches.size(); index++) {
            AuctionSavedSearch existing = searches.get(index);
            if (existing.name().equalsIgnoreCase(name)) {
                searches.set(index, existing.withQuery(name, query));
                searchesByPlayer.put(playerId, sortedLimited(searches));
                setDirty();
                return AuctionActionResult.ok("Search updated.");
            }
        }
        if (searches.size() >= Config.maxSavedSearchesPerPlayer) {
            return AuctionActionResult.fail("Saved search limit reached. Delete one first.");
        }
        searches.add(AuctionSavedSearch.create(playerId, name, query));
        searchesByPlayer.put(playerId, sortedLimited(searches));
        setDirty();
        return AuctionActionResult.ok("Search saved.");
    }

    public synchronized AuctionActionResult renameSearch(UUID playerId, UUID searchId, String rawName) {
        if (playerId == null) {
            return AuctionActionResult.fail("Only players can rename auction searches.");
        }
        String name = AuctionSavedSearch.sanitizeName(rawName);
        if (name.isBlank()) {
            return AuctionActionResult.fail("Saved search name is required.");
        }
        List<AuctionSavedSearch> searches = new ArrayList<>(searchesByPlayer.getOrDefault(playerId, List.of()));
        for (AuctionSavedSearch search : searches) {
            if (!search.searchId().equals(searchId) && search.name().equalsIgnoreCase(name)) {
                return AuctionActionResult.fail("A saved search already uses that name.");
            }
        }
        for (int index = 0; index < searches.size(); index++) {
            AuctionSavedSearch existing = searches.get(index);
            if (existing.searchId().equals(searchId)) {
                searches.set(index, existing.withName(name));
                searchesByPlayer.put(playerId, sortedLimited(searches));
                setDirty();
                return AuctionActionResult.ok("Saved search renamed.");
            }
        }
        return AuctionActionResult.fail("Saved search not found.");
    }

    public synchronized AuctionActionResult deleteSearch(UUID playerId, UUID searchId) {
        if (playerId == null) {
            return AuctionActionResult.fail("Only players can delete auction searches.");
        }
        List<AuctionSavedSearch> searches = new ArrayList<>(searchesByPlayer.getOrDefault(playerId, List.of()));
        boolean removed = searches.removeIf(search -> search.searchId().equals(searchId));
        if (!removed) {
            return AuctionActionResult.fail("Saved search not found.");
        }
        if (searches.isEmpty()) {
            searchesByPlayer.remove(playerId);
        } else {
            searchesByPlayer.put(playerId, sortedLimited(searches));
        }
        setDirty();
        return AuctionActionResult.ok("Saved search deleted.");
    }

    private static List<AuctionSavedSearch> sortedLimited(List<AuctionSavedSearch> searches) {
        if (searches == null || searches.isEmpty()) {
            return List.of();
        }
        return searches.stream()
                .filter(search -> search != null && search.searchId() != null && search.playerId() != null && !search.name().isBlank())
                .sorted(Comparator.comparing(AuctionSavedSearch::updatedAt).reversed())
                .limit(Math.max(1, Config.maxSavedSearchesPerPlayer))
                .toList();
    }
}
