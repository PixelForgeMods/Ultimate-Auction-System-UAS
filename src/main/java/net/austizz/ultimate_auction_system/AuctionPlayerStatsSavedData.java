package net.austizz.ultimate_auction_system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionPlayerStatsSavedData extends SavedData {
    private static final String DATA_NAME = "ultimate_auction_system_player_stats";
    private static final String PLAYERS_TAG = "players";
    private static final String LISTED_AUCTIONS_TAG = "listedAuctions";
    private static final String SETTLED_AUCTIONS_TAG = "settledAuctions";

    private final ConcurrentHashMap<UUID, AuctionPlayerStats> statsByPlayer;
    private final Set<UUID> countedListingAuctionIds;
    private final Set<UUID> countedSettlementAuctionIds;

    public AuctionPlayerStatsSavedData() {
        this(new ConcurrentHashMap<>(), ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet());
    }

    private AuctionPlayerStatsSavedData(ConcurrentHashMap<UUID, AuctionPlayerStats> statsByPlayer,
                                        Set<UUID> countedListingAuctionIds,
                                        Set<UUID> countedSettlementAuctionIds) {
        this.statsByPlayer = statsByPlayer;
        this.countedListingAuctionIds = countedListingAuctionIds;
        this.countedSettlementAuctionIds = countedSettlementAuctionIds;
    }

    public static SavedData.Factory<AuctionPlayerStatsSavedData> factory() {
        return new SavedData.Factory<>(
                AuctionPlayerStatsSavedData::new,
                AuctionPlayerStatsSavedData::load,
                null
        );
    }

    public static AuctionPlayerStatsSavedData get(MinecraftServer server) {
        if (server == null) {
            throw new IllegalStateException("Minecraft server is unavailable");
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld level is unavailable");
        }
        return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    static AuctionPlayerStatsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ConcurrentHashMap<UUID, AuctionPlayerStats> players = new ConcurrentHashMap<>();
        Set<UUID> listedAuctions = ConcurrentHashMap.newKeySet();
        Set<UUID> settledAuctions = ConcurrentHashMap.newKeySet();
        if (tag == null) {
            return new AuctionPlayerStatsSavedData(players, listedAuctions, settledAuctions);
        }

        ListTag playerTags = tag.getList(PLAYERS_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : playerTags) {
            if (raw instanceof CompoundTag playerTag) {
                AuctionPlayerStats.load(playerTag).ifPresent(stats -> players.put(stats.playerId(), stats));
            }
        }
        loadUuidSet(tag.getList(LISTED_AUCTIONS_TAG, Tag.TAG_COMPOUND), listedAuctions);
        loadUuidSet(tag.getList(SETTLED_AUCTIONS_TAG, Tag.TAG_COMPOUND), settledAuctions);
        return new AuctionPlayerStatsSavedData(players, listedAuctions, settledAuctions);
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playerTags = new ListTag();
        statsByPlayer.values().stream()
                .sorted(Comparator.comparing(AuctionPlayerStats::playerName, String.CASE_INSENSITIVE_ORDER))
                .forEach(stats -> playerTags.add(stats.save()));
        tag.put(PLAYERS_TAG, playerTags);
        tag.put(LISTED_AUCTIONS_TAG, saveUuidSet(countedListingAuctionIds));
        tag.put(SETTLED_AUCTIONS_TAG, saveUuidSet(countedSettlementAuctionIds));
        return tag;
    }

    public synchronized AuctionPlayerStats statsFor(UUID playerId, String playerName) {
        if (playerId == null) {
            return AuctionPlayerStats.empty(null, playerName);
        }
        AuctionPlayerStats stats = statsByPlayer.get(playerId);
        return stats == null ? AuctionPlayerStats.empty(playerId, playerName) : stats.withName(playerName);
    }

    public synchronized boolean recordListing(UUID auctionId, UUID sellerId, String sellerName) {
        if (auctionId == null || sellerId == null || !countedListingAuctionIds.add(auctionId)) {
            return false;
        }
        AuctionPlayerStats current = statsByPlayer.getOrDefault(sellerId, AuctionPlayerStats.empty(sellerId, sellerName));
        statsByPlayer.put(sellerId, current.recordListing(sellerName));
        setDirty();
        return true;
    }

    public synchronized boolean recordSale(UUID auctionId,
                                           UUID sellerId,
                                           String sellerName,
                                           UUID buyerId,
                                           String buyerName,
                                           BigDecimal amount) {
        if (auctionId == null || sellerId == null || buyerId == null || !countedSettlementAuctionIds.add(auctionId)) {
            return false;
        }
        AuctionPlayerStats seller = statsByPlayer.getOrDefault(sellerId, AuctionPlayerStats.empty(sellerId, sellerName));
        statsByPlayer.put(sellerId, seller.recordSale(sellerName, amount));

        AuctionPlayerStats buyer = statsByPlayer.getOrDefault(buyerId, AuctionPlayerStats.empty(buyerId, buyerName));
        statsByPlayer.put(buyerId, buyer.recordWin(buyerName, amount));
        setDirty();
        return true;
    }

    public synchronized List<AuctionPlayerStats> topSellers(int limit) {
        return sorted(limit, sellerComparator());
    }

    public synchronized List<AuctionPlayerStats> topBuyers(int limit) {
        return sorted(limit, buyerComparator());
    }

    public synchronized int marketplaceRank(UUID playerId) {
        return rank(playerId, Comparator
                .comparing(AuctionPlayerStats::marketplaceVolume, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingInt((AuctionPlayerStats stats) -> stats.auctionsListed() + stats.auctionsWon()).reversed())
                .thenComparing(AuctionPlayerStats::playerName, String.CASE_INSENSITIVE_ORDER));
    }

    public synchronized int sellerRank(UUID playerId) {
        return rank(playerId, sellerComparator());
    }

    public synchronized int buyerRank(UUID playerId) {
        return rank(playerId, buyerComparator());
    }

    private static Comparator<AuctionPlayerStats> sellerComparator() {
        return Comparator
                .comparing(AuctionPlayerStats::grossSoldValue, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingInt(AuctionPlayerStats::auctionsListed).reversed())
                .thenComparing(AuctionPlayerStats::playerName, String.CASE_INSENSITIVE_ORDER);
    }

    private static Comparator<AuctionPlayerStats> buyerComparator() {
        return Comparator
                .comparing(AuctionPlayerStats::grossSpentValue, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingInt(AuctionPlayerStats::auctionsWon).reversed())
                .thenComparing(AuctionPlayerStats::playerName, String.CASE_INSENSITIVE_ORDER);
    }

    private List<AuctionPlayerStats> sorted(int limit, Comparator<AuctionPlayerStats> comparator) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        return statsByPlayer.values().stream()
                .filter(stats -> stats.marketplaceVolume().compareTo(BigDecimal.ZERO) > 0 || stats.auctionsListed() > 0 || stats.auctionsWon() > 0)
                .sorted(comparator)
                .limit(safeLimit)
                .toList();
    }

    private int rank(UUID playerId, Comparator<AuctionPlayerStats> comparator) {
        if (playerId == null) {
            return 0;
        }
        List<AuctionPlayerStats> ranked = statsByPlayer.values().stream()
                .filter(stats -> stats.marketplaceVolume().compareTo(BigDecimal.ZERO) > 0 || stats.auctionsListed() > 0 || stats.auctionsWon() > 0)
                .sorted(comparator)
                .toList();
        for (int index = 0; index < ranked.size(); index++) {
            if (playerId.equals(ranked.get(index).playerId())) {
                return index + 1;
            }
        }
        return 0;
    }

    private static ListTag saveUuidSet(Set<UUID> values) {
        ListTag tags = new ListTag();
        if (values == null) {
            return tags;
        }
        values.stream()
                .filter(value -> value != null)
                .sorted()
                .forEach(value -> {
                    CompoundTag tag = new CompoundTag();
                    tag.putUUID("id", value);
                    tags.add(tag);
                });
        return tags;
    }

    private static void loadUuidSet(ListTag tags, Set<UUID> target) {
        if (tags == null || target == null) {
            return;
        }
        for (Tag raw : tags) {
            if (raw instanceof CompoundTag tag && tag.hasUUID("id")) {
                try {
                    target.add(tag.getUUID("id"));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }
}
