package net.austizz.ultimate_auction_system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

public class AuctionItem {
    private final UUID auctionId;
    private final UUID playerId;
    private final ItemStack item;
    private String description;
    private final LocalDateTime dateOfEnd;
    private final LocalDateTime dateOfStart;
    private BigDecimal startingBidPrice;
    private ConcurrentSkipListMap<UUID, BigDecimal> bids;
    private final AtomicReference<BigDecimal> highestBid = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<UUID> highestBidderId = new AtomicReference<>();
    private AuctionState state;
    private transient Runnable changeListener = () -> {
    };

    public AuctionItem(ItemStack item, String description, LocalDateTime dateOfEnd, LocalDateTime dateOfStart, BigDecimal startingBidPrice, UUID playerId) {
        this(UUID.randomUUID(), item, description, dateOfEnd, dateOfStart, startingBidPrice, playerId, AuctionState.ACTIVE, new ConcurrentSkipListMap<>(), startingBidPrice, null);
    }

    private AuctionItem(UUID auctionId,
                        ItemStack item,
                        String description,
                        LocalDateTime dateOfEnd,
                        LocalDateTime dateOfStart,
                        BigDecimal startingBidPrice,
                        UUID playerId,
                        AuctionState state,
                        ConcurrentSkipListMap<UUID, BigDecimal> bids,
                        BigDecimal highestBid,
                        UUID highestBidderId) {
        this.item = item.copy(); // CRITICAL: Always copy ItemStacks to prevent inventory reference bugs!
        this.description = description == null ? "" : description;
        this.dateOfEnd = dateOfEnd;
        this.dateOfStart = dateOfStart;
        this.startingBidPrice = startingBidPrice == null ? BigDecimal.ZERO : startingBidPrice;
        this.bids = bids == null ? new ConcurrentSkipListMap<>() : bids;
        this.auctionId = auctionId == null ? UUID.randomUUID() : auctionId;
        this.playerId = playerId;
        this.state = state == null ? AuctionState.ACTIVE : state;
        this.highestBid.set(highestBid == null ? this.startingBidPrice : highestBid);
        this.highestBidderId.set(highestBidderId);
    }

    public UUID getAuctionId() { return auctionId; }
    public ItemStack getItem() { return item; }
    public String getDescription() { return description; }
    public LocalDateTime getDateOfEnd() { return dateOfEnd; }
    public LocalDateTime getDateOfStart() { return dateOfStart; }
    public BigDecimal getStartingBidPrice() { return startingBidPrice; }
    public BigDecimal getHighestBid() { return highestBid.get(); }
    public UUID getHighestBidderId() { return highestBidderId.get(); }
    public UUID getPlayerId() { return playerId; }
    public ConcurrentSkipListMap<UUID, BigDecimal> getBids() { return new ConcurrentSkipListMap<>(bids); }
    public AuctionState getState() { return state; }

    public synchronized void setBids(ConcurrentSkipListMap<UUID, BigDecimal> bids) {
        this.bids = bids == null ? new ConcurrentSkipListMap<>() : new ConcurrentSkipListMap<>(bids);
        repairLoadedBidState();
        markChanged();
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
        markChanged();
    }

    public void setState(AuctionState state) {
        AuctionState nextState = state == null ? AuctionState.ACTIVE : state;
        if (this.state != nextState) {
            this.state = nextState;
            markChanged();
        }
    }

    public void setStartingBidPrice(BigDecimal startingBidPrice) {
        if (startingBidPrice != null && startingBidPrice.compareTo(BigDecimal.ZERO) > 0) {
            this.startingBidPrice = startingBidPrice;
            markChanged();
        }
    }

    /**
     * Thread-safely processes a player's bid.
     * @return true if the bid was successful, false if it was rejected.
     */
    public synchronized boolean addBid(UUID uuid, BigDecimal bid) {
        if (uuid == null || bid == null) {
            UltimateAuctionSystem.LOGGER.error("Bid rejected: Missing bidder or amount.");
            return false;
        }

        // 1. Validate if the auction period has already expired
        if (LocalDateTime.now().isAfter(dateOfEnd)) {
            UltimateAuctionSystem.LOGGER.error("Bid rejected: Auction already ended!");
            return false;
        }

        // 2. Validate that the new bid is explicitly higher than the current highest bid
        if (bid.compareTo(highestBid.get()) > 0) {
            highestBid.set(bid);
            highestBidderId.set(uuid);
            this.bids.put(uuid, bid);
            markChanged();
            UltimateAuctionSystem.LOGGER.info("New highest bid accepted for auction " + auctionId);
            return true;
        } else {
            UltimateAuctionSystem.LOGGER.error("New highest bid rejected: Too low!");
            return false;
        }
    }

    /**
     * Helper to verify if the auction lifecycle has naturally concluded
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.dateOfEnd);
    }

    void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener == null ? () -> {
        } : changeListener;
    }

    boolean repairLoadedBidState() {
        BigDecimal safeStartingBid = startingBidPrice == null ? BigDecimal.ZERO : startingBidPrice;
        boolean repaired = false;

        if (highestBid.get() == null || highestBid.get().compareTo(safeStartingBid) < 0) {
            highestBid.set(safeStartingBid);
            repaired = true;
        }

        if (bids == null || bids.isEmpty()) {
            if (bids == null) {
                bids = new ConcurrentSkipListMap<>();
                repaired = true;
            }
            if (highestBidderId.get() != null) {
                highestBidderId.set(null);
                repaired = true;
            }
            if (highestBid.get().compareTo(safeStartingBid) != 0) {
                highestBid.set(safeStartingBid);
                repaired = true;
            }
            return repaired;
        }

        Map.Entry<UUID, BigDecimal> bestBid = null;
        for (Map.Entry<UUID, BigDecimal> entry : bids.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                repaired = true;
                continue;
            }
            if (bestBid == null || entry.getValue().compareTo(bestBid.getValue()) > 0) {
                bestBid = entry;
            }
        }

        if (bestBid == null) {
            bids.clear();
            highestBid.set(safeStartingBid);
            highestBidderId.set(null);
            return true;
        }

        BigDecimal expectedHighest = bestBid.getValue().max(safeStartingBid);
        UUID expectedBidder = bestBid.getKey();
        UUID currentHighestBidder = highestBidderId.get();
        BigDecimal bidderRecordedAmount = currentHighestBidder == null ? null : bids.get(currentHighestBidder);
        if (currentHighestBidder == null
                || bidderRecordedAmount == null
                || bidderRecordedAmount.compareTo(highestBid.get()) != 0
                || highestBid.get().compareTo(expectedHighest) != 0) {
            highestBid.set(expectedHighest);
            highestBidderId.set(expectedBidder);
            repaired = true;
        }

        return repaired;
    }

    boolean isPersistable() {
        return auctionId != null
                && playerId != null
                && item != null
                && !item.isEmpty()
                && dateOfStart != null
                && dateOfEnd != null
                && startingBidPrice != null
                && startingBidPrice.compareTo(BigDecimal.ZERO) >= 0
                && highestBid.get() != null
                && state != null;
    }

    private void markChanged() {
        changeListener.run();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("auctionId", this.auctionId);
        tag.putUUID("playerId", this.playerId);
        tag.putString("description", this.description == null ? "" : this.description);
        tag.putString("dateOfEnd", this.dateOfEnd.toString());
        tag.putString("dateOfStart", this.dateOfStart.toString());
        tag.putString("startingBidPrice", this.startingBidPrice.toPlainString());
        tag.putString("highestBid", this.highestBid.get().toPlainString());
        tag.putString("state", this.state.name());
        if (this.highestBidderId.get() != null) {
            tag.putUUID("highestBidderId", this.highestBidderId.get());
        }
        tag.put("item", saveItemStack(this.item, registries));

        ListTag bidList = new ListTag();
        for (var entry : this.bids.entrySet()) {
            CompoundTag bidTag = new CompoundTag();
            bidTag.putUUID("bidderId", entry.getKey());
            bidTag.putString("amount", entry.getValue().toPlainString());
            bidList.add(bidTag);
        }
        tag.put("bids", bidList);
        return tag;
    }

    public static Optional<AuctionItem> load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null
                || !tag.contains("auctionId")
                || !tag.contains("playerId")
                || !tag.contains("item", Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        ItemStack item = ItemStack.parseOptional(registries, tag.getCompound("item"));
        if (item.isEmpty()) {
            return Optional.empty();
        }

        try {
            UUID auctionId = tag.getUUID("auctionId");
            UUID playerId = tag.getUUID("playerId");
            LocalDateTime dateOfEnd = LocalDateTime.parse(tag.getString("dateOfEnd"));
            LocalDateTime dateOfStart = LocalDateTime.parse(tag.getString("dateOfStart"));
            BigDecimal startingBidPrice = new BigDecimal(tag.getString("startingBidPrice"));
            BigDecimal highestBid = tag.contains("highestBid")
                    ? new BigDecimal(tag.getString("highestBid"))
                    : startingBidPrice;
            UUID highestBidderId = tag.contains("highestBidderId") ? tag.getUUID("highestBidderId") : null;
            ConcurrentSkipListMap<UUID, BigDecimal> bids = loadBids(tag);

            return Optional.of(new AuctionItem(
                    auctionId,
                    item,
                    tag.getString("description"),
                    dateOfEnd,
                    dateOfStart,
                    startingBidPrice,
                    playerId,
                    AuctionState.fromSerializedName(tag.getString("state")),
                    bids,
                    highestBid,
                    highestBidderId
            ));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Invalid auction record: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static ConcurrentSkipListMap<UUID, BigDecimal> loadBids(CompoundTag tag) {
        ConcurrentSkipListMap<UUID, BigDecimal> loaded = new ConcurrentSkipListMap<>();
        ListTag bidList = tag.getList("bids", Tag.TAG_COMPOUND);
        for (Tag rawBid : bidList) {
            if (!(rawBid instanceof CompoundTag bidTag) || !bidTag.contains("bidderId")) {
                continue;
            }
            try {
                loaded.put(bidTag.getUUID("bidderId"), new BigDecimal(bidTag.getString("amount")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return loaded;
    }

    private static CompoundTag saveItemStack(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (stack != null && !stack.isEmpty()) {
            stack.save(registries, tag);
        }
        return tag;
    }
}
