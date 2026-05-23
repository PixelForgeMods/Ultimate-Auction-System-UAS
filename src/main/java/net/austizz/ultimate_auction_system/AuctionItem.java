package net.austizz.ultimate_auction_system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

public class AuctionItem {
    private final UUID auctionId;
    private final UUID playerId;
    private UUID sellerAccountId;
    private final ItemStack item;
    private String description;
    private final LocalDateTime dateOfEnd;
    private final LocalDateTime dateOfStart;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BigDecimal startingBidPrice;
    private BigDecimal buyoutPrice;
    private ConcurrentSkipListMap<UUID, BigDecimal> bids;
    private final ArrayList<AuctionBidRecord> bidRecords;
    private final AtomicReference<BigDecimal> highestBid = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<UUID> highestBidderId = new AtomicReference<>();
    private AuctionState state;
    private transient Runnable changeListener = () -> {
    };

    public AuctionItem(ItemStack item, String description, LocalDateTime dateOfEnd, LocalDateTime dateOfStart, BigDecimal startingBidPrice, UUID playerId) {
        this(item, description, dateOfEnd, dateOfStart, startingBidPrice, playerId, null, null);
    }

    public AuctionItem(ItemStack item,
                       String description,
                       LocalDateTime dateOfEnd,
                       LocalDateTime dateOfStart,
                       BigDecimal startingBidPrice,
                       UUID playerId,
                       UUID sellerAccountId,
                       BigDecimal buyoutPrice) {
        this(
                UUID.randomUUID(),
                item,
                description,
                dateOfEnd,
                dateOfStart,
                LocalDateTime.now(),
                LocalDateTime.now(),
                startingBidPrice,
                buyoutPrice,
                playerId,
                sellerAccountId,
                AuctionState.ACTIVE,
                new ConcurrentSkipListMap<>(),
                new ArrayList<>(),
                startingBidPrice,
                null
        );
    }

    private AuctionItem(UUID auctionId,
                        ItemStack item,
                        String description,
                        LocalDateTime dateOfEnd,
                        LocalDateTime dateOfStart,
                        LocalDateTime createdAt,
                        LocalDateTime updatedAt,
                        BigDecimal startingBidPrice,
                        BigDecimal buyoutPrice,
                        UUID playerId,
                        UUID sellerAccountId,
                        AuctionState state,
                        ConcurrentSkipListMap<UUID, BigDecimal> bids,
                        List<AuctionBidRecord> bidRecords,
                        BigDecimal highestBid,
                        UUID highestBidderId) {
        this.item = item.copy(); // CRITICAL: Always copy ItemStacks to prevent inventory reference bugs!
        this.description = description == null ? "" : description;
        this.dateOfEnd = dateOfEnd;
        this.dateOfStart = dateOfStart;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
        this.startingBidPrice = startingBidPrice == null ? BigDecimal.ZERO : startingBidPrice;
        this.buyoutPrice = normalizeOptionalPrice(buyoutPrice);
        this.bids = bids == null ? new ConcurrentSkipListMap<>() : bids;
        this.bidRecords = bidRecords == null ? new ArrayList<>() : new ArrayList<>(bidRecords);
        this.auctionId = auctionId == null ? UUID.randomUUID() : auctionId;
        this.playerId = playerId;
        this.sellerAccountId = sellerAccountId;
        this.state = state == null ? AuctionState.ACTIVE : state;
        addAcceptedBidRecordsToBidMap();
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
    public BigDecimal getCurrentPrice() { return highestBid.get(); }
    public UUID getHighestBidderId() { return highestBidderId.get(); }
    public UUID getPlayerId() { return playerId; }
    public UUID getSellerAccountId() { return sellerAccountId; }
    public int getItemQuantity() { return item == null ? 0 : item.getCount(); }
    public Optional<BigDecimal> getBuyoutPrice() { return Optional.ofNullable(buyoutPrice); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public ConcurrentSkipListMap<UUID, BigDecimal> getBids() { return new ConcurrentSkipListMap<>(bids); }
    public synchronized List<AuctionBidRecord> getBidRecords() { return List.copyOf(bidRecords); }
    public AuctionState getState() { return state; }

    public synchronized Optional<AuctionBidRecord> getWinningBidRecord() {
        for (int index = bidRecords.size() - 1; index >= 0; index--) {
            AuctionBidRecord record = bidRecords.get(index);
            if (record.isAccepted()
                    && Objects.equals(record.getBidderId(), highestBidderId.get())
                    && record.getAmount().compareTo(highestBid.get()) == 0) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public synchronized void setBids(ConcurrentSkipListMap<UUID, BigDecimal> bids) {
        this.bids = bids == null ? new ConcurrentSkipListMap<>() : new ConcurrentSkipListMap<>(bids);
        repairLoadedBidState();
        markChanged();
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
        markChanged();
    }

    public void setSellerAccountId(UUID sellerAccountId) {
        if (!Objects.equals(this.sellerAccountId, sellerAccountId)) {
            this.sellerAccountId = sellerAccountId;
            markChanged();
        }
    }

    public void setBuyoutPrice(BigDecimal buyoutPrice) {
        BigDecimal normalized = normalizeOptionalPrice(buyoutPrice);
        if (!Objects.equals(this.buyoutPrice, normalized)) {
            this.buyoutPrice = normalized;
            markChanged();
        }
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
        return recordBid(uuid, null, bid).isAccepted();
    }

    public synchronized AuctionBidRecord recordBid(UUID bidderId, UUID bidderAccountId, BigDecimal bid) {
        if (bidderId == null || bid == null || bid.compareTo(BigDecimal.ZERO) <= 0) {
            return recordRejectedBid(bidderId, bidderAccountId, bid, AuctionBidResult.REJECTED_INVALID, "Missing bidder or positive bid amount.");
        }
        if (bidderAccountId == null) {
            return recordRejectedBid(bidderId, null, bid, AuctionBidResult.REJECTED_NO_ACCOUNT, "Missing UBS bidder account ID.");
        }
        if (state != AuctionState.ACTIVE) {
            return recordRejectedBid(bidderId, bidderAccountId, bid, AuctionBidResult.REJECTED_AUCTION_NOT_ACTIVE, "Auction is not active.");
        }
        if (LocalDateTime.now().isAfter(dateOfEnd)) {
            state = AuctionState.ENDED;
            return recordRejectedBid(bidderId, bidderAccountId, bid, AuctionBidResult.REJECTED_AUCTION_ENDED, "Auction already ended.");
        }
        if (bid.compareTo(highestBid.get()) <= 0) {
            return recordRejectedBid(bidderId, bidderAccountId, bid, AuctionBidResult.REJECTED_TOO_LOW, "Bid must be higher than the current price.");
        }

        highestBid.set(bid);
        highestBidderId.set(bidderId);
        bids.put(bidderId, bid);
        if (buyoutPrice != null && bid.compareTo(buyoutPrice) >= 0) {
            state = AuctionState.ENDED;
        }

        AuctionBidRecord record = AuctionBidRecord.accepted(auctionId, bidderId, bidderAccountId, bid);
        bidRecords.add(record);
        markChanged();
        UltimateAuctionSystem.LOGGER.info("New highest bid accepted for auction {}", auctionId);
        return record;
    }

    public synchronized AuctionBidRecord recordRejectedBid(UUID bidderId,
                                                           UUID bidderAccountId,
                                                           BigDecimal bid,
                                                           AuctionBidResult result,
                                                           String reason) {
        AuctionBidRecord record = AuctionBidRecord.rejected(auctionId, bidderId, bidderAccountId, bid, result, reason);
        if (Config.auditRejectedBids && record.isValidForAuction(auctionId)) {
            bidRecords.add(record);
            markChanged();
        }
        UltimateAuctionSystem.LOGGER.debug("Bid rejected for auction {}: {}", auctionId, reason);
        return record;
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

    public synchronized void linkWinningBidToSettlement(String reference, net.austizz.ultimate_auction_system.banking.UasBankingResult result) {
        Optional<AuctionBidRecord> winningBidRecord = getWinningBidRecord();
        if (winningBidRecord.isPresent()) {
            winningBidRecord.get().linkSettlement(reference, result);
            markChanged();
        }
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
            if (repaired) {
                updatedAt = LocalDateTime.now();
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
            updatedAt = LocalDateTime.now();
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

        if (repaired) {
            updatedAt = LocalDateTime.now();
        }
        return repaired;
    }

    private void addAcceptedBidRecordsToBidMap() {
        for (AuctionBidRecord record : bidRecords) {
            if (!record.isAccepted() || !record.isValidForAuction(auctionId)) {
                continue;
            }
            bids.merge(record.getBidderId(), record.getAmount(), BigDecimal::max);
        }
    }

    boolean isPersistable() {
        return validateForPersistence().isEmpty();
    }

    Optional<String> validateForActivation() {
        Optional<String> validationError = validateForPersistence();
        if (validationError.isPresent()) {
            return validationError;
        }
        if (state != AuctionState.ACTIVE) {
            return Optional.of("auction must be ACTIVE before listing");
        }
        return Optional.empty();
    }

    Optional<String> validateForPersistence() {
        if (auctionId == null) {
            return Optional.of("missing auction ID");
        }
        if (playerId == null) {
            return Optional.of("missing seller player ID");
        }
        if (sellerAccountId == null) {
            return Optional.of("missing seller account ID");
        }
        if (item == null || item.isEmpty() || item.getCount() <= 0) {
            return Optional.of("missing item stack or quantity");
        }
        if (dateOfStart == null || dateOfEnd == null || !dateOfEnd.isAfter(dateOfStart)) {
            return Optional.of("invalid auction start/end time");
        }
        if (createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt)) {
            return Optional.of("invalid audit timestamps");
        }
        if (startingBidPrice == null || startingBidPrice.compareTo(BigDecimal.ZERO) < 0) {
            return Optional.of("invalid starting price");
        }
        if (highestBid.get() == null || highestBid.get().compareTo(startingBidPrice) < 0) {
            return Optional.of("invalid current price");
        }
        if (buyoutPrice != null && buyoutPrice.compareTo(startingBidPrice) < 0) {
            return Optional.of("buyout price is below starting price");
        }
        if (state == null) {
            return Optional.of("missing auction state");
        }
        for (AuctionBidRecord bidRecord : bidRecords) {
            if (!bidRecord.isValidForAuction(auctionId)) {
                return Optional.of("invalid bid record");
            }
        }
        return Optional.empty();
    }

    private void markChanged() {
        updatedAt = LocalDateTime.now();
        changeListener.run();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("auctionId", this.auctionId);
        tag.putUUID("playerId", this.playerId);
        tag.putUUID("sellerAccountId", this.sellerAccountId);
        tag.putString("description", this.description == null ? "" : this.description);
        tag.putInt("itemQuantity", getItemQuantity());
        tag.putString("dateOfEnd", this.dateOfEnd.toString());
        tag.putString("dateOfStart", this.dateOfStart.toString());
        tag.putString("createdAt", this.createdAt.toString());
        tag.putString("updatedAt", this.updatedAt.toString());
        tag.putString("startingBidPrice", this.startingBidPrice.toPlainString());
        tag.putString("currentPrice", this.highestBid.get().toPlainString());
        tag.putString("highestBid", this.highestBid.get().toPlainString());
        if (this.buyoutPrice != null) {
            tag.putString("buyoutPrice", this.buyoutPrice.toPlainString());
        }
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

        ListTag bidRecordList = new ListTag();
        for (AuctionBidRecord bidRecord : bidRecords) {
            if (bidRecord != null && bidRecord.isValidForAuction(auctionId)) {
                bidRecordList.add(bidRecord.save());
            }
        }
        tag.put("bidRecords", bidRecordList);
        return tag;
    }

    public static Optional<AuctionItem> load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null
                || !tag.contains("auctionId")
                || !tag.contains("playerId")
                || !tag.contains("sellerAccountId")
                || !tag.contains("itemQuantity", Tag.TAG_INT)
                || !tag.contains("currentPrice")
                || !tag.contains("createdAt")
                || !tag.contains("updatedAt")
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
            UUID sellerAccountId = tag.getUUID("sellerAccountId");
            int itemQuantity = tag.getInt("itemQuantity");
            if (itemQuantity <= 0 || item.getCount() != itemQuantity) {
                return Optional.empty();
            }
            LocalDateTime dateOfEnd = LocalDateTime.parse(tag.getString("dateOfEnd"));
            LocalDateTime dateOfStart = LocalDateTime.parse(tag.getString("dateOfStart"));
            LocalDateTime createdAt = LocalDateTime.parse(tag.getString("createdAt"));
            LocalDateTime updatedAt = LocalDateTime.parse(tag.getString("updatedAt"));
            BigDecimal startingBidPrice = new BigDecimal(tag.getString("startingBidPrice"));
            BigDecimal currentPrice = new BigDecimal(tag.getString("currentPrice"));
            BigDecimal buyoutPrice = tag.contains("buyoutPrice") ? new BigDecimal(tag.getString("buyoutPrice")) : null;
            UUID highestBidderId = tag.contains("highestBidderId") ? tag.getUUID("highestBidderId") : null;
            ConcurrentSkipListMap<UUID, BigDecimal> bids = loadBids(tag);
            List<AuctionBidRecord> bidRecords = loadBidRecords(tag, auctionId);

            AuctionItem auction = new AuctionItem(
                    auctionId,
                    item,
                    tag.getString("description"),
                    dateOfEnd,
                    dateOfStart,
                    createdAt,
                    updatedAt,
                    startingBidPrice,
                    buyoutPrice,
                    playerId,
                    sellerAccountId,
                    AuctionState.fromSerializedName(tag.getString("state")),
                    bids,
                    bidRecords,
                    currentPrice,
                    highestBidderId
            );
            if (auction.validateForPersistence().isPresent()) {
                return Optional.empty();
            }
            return Optional.of(auction);
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

    private static List<AuctionBidRecord> loadBidRecords(CompoundTag tag, UUID auctionId) {
        ArrayList<AuctionBidRecord> loaded = new ArrayList<>();
        ListTag recordList = tag.getList("bidRecords", Tag.TAG_COMPOUND);
        for (Tag rawRecord : recordList) {
            if (!(rawRecord instanceof CompoundTag recordTag)) {
                continue;
            }
            AuctionBidRecord.load(recordTag)
                    .filter(record -> record.isValidForAuction(auctionId))
                    .ifPresent(loaded::add);
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

    private static BigDecimal normalizeOptionalPrice(BigDecimal price) {
        return price == null || price.compareTo(BigDecimal.ZERO) <= 0 ? null : price;
    }
}
