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
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

public class AuctionItem {
    public static final int MAX_BUNDLE_CONTENTS = 18;
    private static final String CONTENTS_TAG = "contents";

    private final UUID auctionId;
    private final UUID playerId;
    private UUID sellerAccountId;
    private final ItemStack item;
    private final List<ItemStack> contents;
    private final String title;
    private String description;
    private final LocalDateTime dateOfEnd;
    private final LocalDateTime dateOfStart;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean escrowed;
    private LocalDateTime escrowedAt;
    private String escrowSource;
    private BigDecimal startingBidPrice;
    private BigDecimal buyoutPrice;
    private BigDecimal reservePrice;
    private AuctionFormat format;
    private ConcurrentSkipListMap<UUID, BigDecimal> bids;
    private final ArrayList<AuctionBidRecord> bidRecords;
    private final ArrayList<AuctionFinancialEvent> financialEvents;
    private final ConcurrentSkipListSet<UUID> notificationSubscribers;
    private boolean endingSoonNotificationSent;
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
        this(UUID.randomUUID(), item, description, dateOfEnd, dateOfStart, startingBidPrice, playerId, sellerAccountId, buyoutPrice, null);
    }

    public AuctionItem(ItemStack item,
                       String description,
                       LocalDateTime dateOfEnd,
                       LocalDateTime dateOfStart,
                       BigDecimal startingBidPrice,
                       UUID playerId,
                       UUID sellerAccountId,
                       BigDecimal buyoutPrice,
                       BigDecimal reservePrice) {
        this(UUID.randomUUID(), item, description, dateOfEnd, dateOfStart, startingBidPrice, playerId, sellerAccountId, buyoutPrice, reservePrice);
    }

    AuctionItem(UUID auctionId,
                ItemStack item,
                String description,
                LocalDateTime dateOfEnd,
                LocalDateTime dateOfStart,
                BigDecimal startingBidPrice,
                UUID playerId,
                UUID sellerAccountId,
                BigDecimal buyoutPrice) {
        this(auctionId, List.of(item), "", description, dateOfEnd, dateOfStart, startingBidPrice, playerId, sellerAccountId, buyoutPrice, null);
    }

    AuctionItem(UUID auctionId,
                ItemStack item,
                String description,
                LocalDateTime dateOfEnd,
                LocalDateTime dateOfStart,
                BigDecimal startingBidPrice,
                UUID playerId,
                UUID sellerAccountId,
                BigDecimal buyoutPrice,
                BigDecimal reservePrice) {
        this(auctionId, List.of(item), "", description, dateOfEnd, dateOfStart, startingBidPrice, playerId, sellerAccountId, buyoutPrice, reservePrice);
    }

    AuctionItem(UUID auctionId,
                List<ItemStack> contents,
                String title,
                String description,
                LocalDateTime dateOfEnd,
                LocalDateTime dateOfStart,
                BigDecimal startingBidPrice,
                UUID playerId,
                UUID sellerAccountId,
                BigDecimal buyoutPrice) {
        this(auctionId, contents, title, description, dateOfEnd, dateOfStart, startingBidPrice, playerId, sellerAccountId, buyoutPrice, null);
    }

    AuctionItem(UUID auctionId,
                List<ItemStack> contents,
                String title,
                String description,
                LocalDateTime dateOfEnd,
                LocalDateTime dateOfStart,
                BigDecimal startingBidPrice,
                UUID playerId,
                UUID sellerAccountId,
                BigDecimal buyoutPrice,
                BigDecimal reservePrice) {
        this(auctionId, contents, title, description, dateOfEnd, dateOfStart, startingBidPrice, playerId, sellerAccountId, buyoutPrice, reservePrice, AuctionFormat.NORMAL);
    }

    AuctionItem(UUID auctionId,
                List<ItemStack> contents,
                String title,
                String description,
                LocalDateTime dateOfEnd,
                LocalDateTime dateOfStart,
                BigDecimal startingBidPrice,
                UUID playerId,
                UUID sellerAccountId,
                BigDecimal buyoutPrice,
                BigDecimal reservePrice,
                AuctionFormat format) {
        this(
                auctionId,
                contents,
                title,
                description,
                dateOfEnd,
                dateOfStart,
                LocalDateTime.now(),
                LocalDateTime.now(),
                false,
                null,
                "",
                startingBidPrice,
                buyoutPrice,
                reservePrice,
                format,
                playerId,
                sellerAccountId,
                AuctionState.ACTIVE,
                new ConcurrentSkipListMap<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                startingBidPrice,
                null,
                new ConcurrentSkipListSet<>(),
                false
        );
    }

    private AuctionItem(UUID auctionId,
                        List<ItemStack> contents,
                        String title,
                        String description,
                        LocalDateTime dateOfEnd,
                        LocalDateTime dateOfStart,
                        LocalDateTime createdAt,
                        LocalDateTime updatedAt,
                        boolean escrowed,
                        LocalDateTime escrowedAt,
                        String escrowSource,
                        BigDecimal startingBidPrice,
                        BigDecimal buyoutPrice,
                        BigDecimal reservePrice,
                        AuctionFormat format,
                        UUID playerId,
                        UUID sellerAccountId,
                        AuctionState state,
                        ConcurrentSkipListMap<UUID, BigDecimal> bids,
                        List<AuctionBidRecord> bidRecords,
                        List<AuctionFinancialEvent> financialEvents,
                        BigDecimal highestBid,
                        UUID highestBidderId,
                        ConcurrentSkipListSet<UUID> notificationSubscribers,
                        boolean endingSoonNotificationSent) {
        this.contents = sanitizeContents(contents);
        this.item = this.contents.isEmpty() ? ItemStack.EMPTY : this.contents.getFirst().copy();
        this.title = title == null ? "" : title.trim();
        this.description = description == null ? "" : description;
        this.dateOfEnd = dateOfEnd;
        this.dateOfStart = dateOfStart;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
        this.escrowed = escrowed;
        this.escrowedAt = escrowedAt;
        this.escrowSource = escrowSource == null ? "" : escrowSource;
        this.startingBidPrice = startingBidPrice == null ? BigDecimal.ZERO : startingBidPrice;
        this.buyoutPrice = normalizeOptionalPrice(buyoutPrice);
        this.reservePrice = normalizeOptionalPrice(reservePrice);
        this.format = format == null ? AuctionFormat.NORMAL : format;
        this.bids = bids == null ? new ConcurrentSkipListMap<>() : bids;
        this.bidRecords = bidRecords == null ? new ArrayList<>() : new ArrayList<>(bidRecords);
        this.financialEvents = financialEvents == null ? new ArrayList<>() : new ArrayList<>(financialEvents);
        this.notificationSubscribers = notificationSubscribers == null ? new ConcurrentSkipListSet<>() : new ConcurrentSkipListSet<>(notificationSubscribers);
        this.endingSoonNotificationSent = endingSoonNotificationSent;
        this.auctionId = auctionId == null ? UUID.randomUUID() : auctionId;
        this.playerId = playerId;
        this.sellerAccountId = sellerAccountId;
        this.state = state == null ? AuctionState.ACTIVE : state;
        addAcceptedBidRecordsToBidMap();
        this.highestBid.set(highestBid == null ? this.startingBidPrice : highestBid);
        this.highestBidderId.set(highestBidderId);
    }

    public UUID getAuctionId() { return auctionId; }
    public ItemStack getItem() { return item.copy(); }
    public List<ItemStack> getContents() { return contents.stream().map(ItemStack::copy).toList(); }
    public boolean isBundle() { return contents.size() > 1; }
    public int getContentStackCount() { return contents.size(); }
    public int getTotalItemCount() { return contents.stream().mapToInt(ItemStack::getCount).sum(); }
    public String getTitle() { return title; }
    public String getDisplayTitle() {
        if (isBundle()) {
            return title.isBlank() ? generatedBundleTitle(contents) : title;
        }
        return item.isEmpty() ? "" : item.getHoverName().getString();
    }
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
    public Optional<BigDecimal> getReservePrice() { return Optional.ofNullable(reservePrice); }
    public AuctionFormat getFormat() { return format; }
    public boolean isSealedBid() { return format == AuctionFormat.SEALED_BID; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public boolean isEscrowed() { return escrowed; }
    public Optional<LocalDateTime> getEscrowedAt() { return Optional.ofNullable(escrowedAt); }
    public String getEscrowSource() { return escrowSource; }
    public ConcurrentSkipListMap<UUID, BigDecimal> getBids() { return new ConcurrentSkipListMap<>(bids); }
    public synchronized List<AuctionBidRecord> getBidRecords() { return List.copyOf(bidRecords); }
    public synchronized List<AuctionFinancialEvent> getFinancialEvents() { return List.copyOf(financialEvents); }
    public List<UUID> getNotificationSubscribers() { return List.copyOf(notificationSubscribers); }
    public boolean isEndingSoonNotificationSent() { return endingSoonNotificationSent; }
    public AuctionState getState() { return state; }

    public boolean hasReservePrice() {
        return reservePrice != null && reservePrice.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isReserveMet() {
        return !hasReservePrice()
                || (highestBidderId.get() != null && highestBid.get().compareTo(reservePrice) >= 0);
    }

    public boolean isNotificationSubscriber(UUID playerId) {
        return playerId != null && notificationSubscribers.contains(playerId);
    }

    public synchronized boolean toggleNotificationSubscriber(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        boolean subscribed;
        if (notificationSubscribers.contains(playerId)) {
            notificationSubscribers.remove(playerId);
            subscribed = false;
        } else {
            notificationSubscribers.add(playerId);
            subscribed = true;
        }
        markChanged();
        return subscribed;
    }

    public synchronized boolean markEndingSoonNotificationSent() {
        if (endingSoonNotificationSent) {
            return false;
        }
        endingSoonNotificationSent = true;
        markChanged();
        return true;
    }

    public synchronized boolean clearNotificationSubscribers() {
        if (notificationSubscribers.isEmpty()) {
            return false;
        }
        notificationSubscribers.clear();
        markChanged();
        return true;
    }

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

    public void setReservePrice(BigDecimal reservePrice) {
        BigDecimal normalized = normalizeOptionalPrice(reservePrice);
        if (!Objects.equals(this.reservePrice, normalized)) {
            this.reservePrice = normalized;
            markChanged();
        }
    }

    public synchronized void clearWinningBidAfterReserveRefund() {
        clearCurrentBidsAfterRefund();
    }

    public synchronized void clearCurrentBidsAfterRefund() {
        bids.clear();
        highestBid.set(startingBidPrice == null ? BigDecimal.ZERO : startingBidPrice);
        highestBidderId.set(null);
        markChanged();
    }

    public void markEscrowed(String source) {
        if (!escrowed) {
            escrowed = true;
            escrowedAt = LocalDateTime.now();
            escrowSource = source == null || source.isBlank() ? "UNKNOWN" : source;
            markChanged();
        }
    }

    public void setState(AuctionState state) {
        transitionTo(state, "direct state update");
    }

    public synchronized boolean transitionTo(AuctionState state, String reason) {
        AuctionState nextState = state == null ? AuctionState.ACTIVE : state;
        if (this.state == nextState) {
            return true;
        }
        if (!this.state.canTransitionTo(nextState)) {
            if (Config.auditStateTransitions) {
                UltimateAuctionSystem.LOGGER.warn(
                        "[UAS] Rejected auction {} state transition {} -> {}: {}",
                        auctionId,
                        this.state,
                        nextState,
                        reason == null ? "no reason supplied" : reason
                );
            }
            return false;
        }

        AuctionState previousState = this.state;
        this.state = nextState;
        if (Config.auditStateTransitions) {
            UltimateAuctionSystem.LOGGER.info(
                    "[UAS] Auction {} state transition {} -> {}: {}",
                    auctionId,
                    previousState,
                    nextState,
                    reason == null ? "no reason supplied" : reason
            );
        }
        markChanged();
        return true;
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
            transitionTo(AuctionState.ENDED, "bid rejected after auction end time");
            return recordRejectedBid(bidderId, bidderAccountId, bid, AuctionBidResult.REJECTED_AUCTION_ENDED, "Auction already ended.");
        }
        boolean firstAcceptedBid = highestBidderId.get() == null;
        if ((!firstAcceptedBid && bid.compareTo(highestBid.get()) <= 0)
                || (firstAcceptedBid && bid.compareTo(startingBidPrice) < 0)) {
            return recordRejectedBid(bidderId, bidderAccountId, bid, AuctionBidResult.REJECTED_TOO_LOW, "Bid must be higher than the current price.");
        }

        highestBid.set(bid);
        highestBidderId.set(bidderId);
        bids.put(bidderId, bid);
        if (buyoutPrice != null && bid.compareTo(buyoutPrice) >= 0) {
            transitionTo(AuctionState.ENDED, "buyout price met by accepted bid");
        }

        AuctionBidRecord record = AuctionBidRecord.accepted(auctionId, bidderId, bidderAccountId, bid);
        bidRecords.add(record);
        markChanged();
        UltimateAuctionSystem.LOGGER.info("New highest bid accepted for auction {}", auctionId);
        return record;
    }

    public synchronized AuctionBidRecord recordSealedBid(UUID bidderId, UUID bidderAccountId, BigDecimal bid) {
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
            transitionTo(AuctionState.ENDED, "sealed bid rejected after auction end time");
            return recordRejectedBid(bidderId, bidderAccountId, bid, AuctionBidResult.REJECTED_AUCTION_ENDED, "Auction already ended.");
        }
        BigDecimal minimum = sealedBidMinimum(startingBidPrice, bids.get(bidderId));
        if (bid.compareTo(minimum) < 0) {
            return recordRejectedBid(bidderId, bidderAccountId, bid, AuctionBidResult.REJECTED_TOO_LOW, "Sealed bid must be at least " + minimum.stripTrailingZeros().toPlainString() + ".");
        }

        bids.put(bidderId, bid);
        AuctionBidRecord record = AuctionBidRecord.accepted(auctionId, bidderId, bidderAccountId, bid);
        bidRecords.add(record);
        selectWinningSealedBid();
        markChanged();
        UltimateAuctionSystem.LOGGER.info("Sealed bid accepted for auction {}", auctionId);
        return record;
    }

    public synchronized Optional<AuctionBidRecord> getCurrentBidRecordForBidder(UUID bidderId) {
        if (bidderId == null) {
            return Optional.empty();
        }
        BigDecimal currentAmount = bids.get(bidderId);
        if (currentAmount == null) {
            return Optional.empty();
        }
        for (int index = bidRecords.size() - 1; index >= 0; index--) {
            AuctionBidRecord record = bidRecords.get(index);
            if (record.isAccepted()
                    && Objects.equals(record.getBidderId(), bidderId)
                    && record.getAmount().compareTo(currentAmount) == 0) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    static BigDecimal sealedBidMinimum(BigDecimal startingBidPrice, BigDecimal ignoredPreviousOwnAmount) {
        return startingBidPrice == null ? BigDecimal.ZERO : startingBidPrice.max(BigDecimal.ZERO);
    }

    public synchronized Optional<AuctionBidRecord> selectWinningSealedBid() {
        AuctionBidRecord best = null;
        for (AuctionBidRecord record : bidRecords) {
            if (!record.isAccepted() || !record.isValidForAuction(auctionId)) {
                continue;
            }
            BigDecimal currentAmount = bids.get(record.getBidderId());
            if (currentAmount == null || currentAmount.compareTo(record.getAmount()) != 0) {
                continue;
            }
            if (best == null || compareSealedWinner(record, best) < 0) {
                best = record;
            }
        }
        if (best == null) {
            highestBid.set(startingBidPrice == null ? BigDecimal.ZERO : startingBidPrice);
            highestBidderId.set(null);
            markChanged();
            return Optional.empty();
        }
        highestBid.set(best.getAmount());
        highestBidderId.set(best.getBidderId());
        markChanged();
        return Optional.of(best);
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

    public synchronized void recordFinancialEvent(AuctionFinancialEvent event) {
        if (event != null && event.isValidForAuction(auctionId)) {
            financialEvents.add(event);
            markChanged();
        }
    }

    public synchronized boolean hasSuccessfulFinancialEvent(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(java.util.Locale.ROOT);
        return financialEvents.stream()
                .anyMatch(event -> event != null && event.success() && normalized.equals(event.type()));
    }

    public synchronized boolean hasSuccessfulFinancialEvent(String type, String reference) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase(java.util.Locale.ROOT);
        String normalizedReference = reference == null ? "" : reference.trim();
        return financialEvents.stream()
                .anyMatch(event -> event != null
                        && event.success()
                        && normalizedType.equals(event.type())
                        && normalizedReference.equals(event.reference()));
    }

    public synchronized Optional<AuctionFinancialEvent> latestFailedFinancialEvent() {
        for (int index = financialEvents.size() - 1; index >= 0; index--) {
            AuctionFinancialEvent event = financialEvents.get(index);
            if (event != null && !event.success()) {
                return Optional.of(event);
            }
        }
        return Optional.empty();
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
            bids.put(record.getBidderId(), record.getAmount());
        }
    }

    boolean isPersistable() {
        return validateForPersistence().isEmpty();
    }

    Optional<String> validateForActivation() {
        Optional<String> validationError = validateRecord(true);
        if (validationError.isPresent()) {
            return validationError;
        }
        if (state != AuctionState.ACTIVE) {
            return Optional.of("auction must be ACTIVE before listing");
        }
        return Optional.empty();
    }

    Optional<String> validateForPersistence() {
        return validateRecord(true);
    }

    Optional<String> validateForListingRequest() {
        Optional<String> validationError = validateRecord(false);
        if (validationError.isPresent()) {
            return validationError;
        }
        if (state != AuctionState.ACTIVE) {
            return Optional.of("auction must be ACTIVE before listing");
        }
        return Optional.empty();
    }

    private Optional<String> validateRecord(boolean requireEscrow) {
        if (auctionId == null) {
            return Optional.of("missing auction ID");
        }
        if (playerId == null) {
            return Optional.of("missing seller player ID");
        }
        if (sellerAccountId == null) {
            return Optional.of("missing seller account ID");
        }
        if (contents.isEmpty() || item == null || item.isEmpty() || item.getCount() <= 0) {
            return Optional.of("missing item stack or quantity");
        }
        if (contents.size() > MAX_BUNDLE_CONTENTS) {
            return Optional.of("bundle contains too many item stacks");
        }
        for (ItemStack content : contents) {
            if (content == null || content.isEmpty() || content.getCount() <= 0) {
                return Optional.of("bundle contains an invalid item stack");
            }
        }
        if (requireEscrow && state != AuctionState.DRAFT && !escrowed) {
            return Optional.of("auction item is not held in server escrow");
        }
        if (escrowed && (escrowedAt == null || escrowSource == null || escrowSource.isBlank())) {
            return Optional.of("missing escrow metadata");
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
        if (reservePrice != null && reservePrice.compareTo(startingBidPrice) < 0) {
            return Optional.of("reserve price is below starting price");
        }
        if (buyoutPrice != null && reservePrice != null && buyoutPrice.compareTo(reservePrice) < 0) {
            return Optional.of("buyout price is below reserve price");
        }
        if (state == null) {
            return Optional.of("missing auction state");
        }
        for (AuctionBidRecord bidRecord : bidRecords) {
            if (!bidRecord.isValidForAuction(auctionId)) {
                return Optional.of("invalid bid record");
            }
        }
        for (AuctionFinancialEvent event : financialEvents) {
            if (event == null || !event.isValidForAuction(auctionId)) {
                return Optional.of("invalid financial event");
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
        tag.putBoolean("escrowed", this.escrowed);
        if (this.escrowedAt != null) {
            tag.putString("escrowedAt", this.escrowedAt.toString());
        }
        tag.putString("escrowSource", this.escrowSource == null ? "" : this.escrowSource);
        tag.putString("startingBidPrice", this.startingBidPrice.toPlainString());
        tag.putString("currentPrice", this.highestBid.get().toPlainString());
        tag.putString("highestBid", this.highestBid.get().toPlainString());
        if (this.buyoutPrice != null) {
            tag.putString("buyoutPrice", this.buyoutPrice.toPlainString());
        }
        if (this.reservePrice != null) {
            tag.putString("reservePrice", this.reservePrice.toPlainString());
        }
        tag.putString("format", this.format == null ? AuctionFormat.NORMAL.serializedName() : this.format.serializedName());
        if (this.title != null && !this.title.isBlank()) {
            tag.putString("title", this.title);
        }
        tag.putString("state", this.state.name());
        if (this.highestBidderId.get() != null) {
            tag.putUUID("highestBidderId", this.highestBidderId.get());
        }
        tag.put("item", saveItemStack(this.item, registries));
        ListTag contentTags = new ListTag();
        for (ItemStack content : contents) {
            if (content == null || content.isEmpty()) {
                continue;
            }
            CompoundTag contentTag = new CompoundTag();
            contentTag.put("item", saveItemStack(content, registries));
            contentTags.add(contentTag);
        }
        tag.put(CONTENTS_TAG, contentTags);

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

        ListTag financialEventList = new ListTag();
        for (AuctionFinancialEvent event : financialEvents) {
            if (event != null && event.isValidForAuction(auctionId)) {
                financialEventList.add(event.save());
            }
        }
        tag.put("financialEvents", financialEventList);

        ListTag subscriberList = new ListTag();
        for (UUID subscriberId : notificationSubscribers) {
            if (subscriberId == null) {
                continue;
            }
            CompoundTag subscriberTag = new CompoundTag();
            subscriberTag.putUUID("playerId", subscriberId);
            subscriberList.add(subscriberTag);
        }
        tag.put("notificationSubscribers", subscriberList);
        tag.putBoolean("endingSoonNotificationSent", endingSoonNotificationSent);
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
                || !tag.contains("escrowed")
                || !tag.contains("escrowSource")
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
            boolean escrowed = tag.getBoolean("escrowed");
            LocalDateTime escrowedAt = tag.contains("escrowedAt") ? LocalDateTime.parse(tag.getString("escrowedAt")) : null;
            BigDecimal startingBidPrice = new BigDecimal(tag.getString("startingBidPrice"));
            BigDecimal currentPrice = new BigDecimal(tag.getString("currentPrice"));
            BigDecimal buyoutPrice = tag.contains("buyoutPrice") ? new BigDecimal(tag.getString("buyoutPrice")) : null;
            BigDecimal reservePrice = tag.contains("reservePrice") ? new BigDecimal(tag.getString("reservePrice")) : null;
            AuctionFormat format = AuctionFormat.fromSerializedName(tag.getString("format"));
            UUID highestBidderId = tag.contains("highestBidderId") ? tag.getUUID("highestBidderId") : null;
            ConcurrentSkipListMap<UUID, BigDecimal> bids = loadBids(tag);
            List<AuctionBidRecord> bidRecords = loadBidRecords(tag, auctionId);
            List<AuctionFinancialEvent> financialEvents = loadFinancialEvents(tag, auctionId);
            ConcurrentSkipListSet<UUID> notificationSubscribers = loadNotificationSubscribers(tag);
            boolean endingSoonNotificationSent = tag.getBoolean("endingSoonNotificationSent");
            List<ItemStack> contents = loadContents(tag, registries, item);

            AuctionItem auction = new AuctionItem(
                    auctionId,
                    contents,
                    tag.getString("title"),
                    tag.getString("description"),
                    dateOfEnd,
                    dateOfStart,
                    createdAt,
                    updatedAt,
                    escrowed,
                    escrowedAt,
                    tag.getString("escrowSource"),
                    startingBidPrice,
                    buyoutPrice,
                    reservePrice,
                    format,
                    playerId,
                    sellerAccountId,
                    AuctionState.fromSerializedName(tag.getString("state")),
                    bids,
                    bidRecords,
                    financialEvents,
                    currentPrice,
                    highestBidderId,
                    notificationSubscribers,
                    endingSoonNotificationSent
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

    private static List<AuctionFinancialEvent> loadFinancialEvents(CompoundTag tag, UUID auctionId) {
        ArrayList<AuctionFinancialEvent> loaded = new ArrayList<>();
        ListTag eventList = tag.getList("financialEvents", Tag.TAG_COMPOUND);
        for (Tag rawEvent : eventList) {
            if (!(rawEvent instanceof CompoundTag eventTag)) {
                continue;
            }
            AuctionFinancialEvent.load(eventTag)
                    .filter(event -> event.isValidForAuction(auctionId))
                    .ifPresent(loaded::add);
        }
        return loaded;
    }

    private static ConcurrentSkipListSet<UUID> loadNotificationSubscribers(CompoundTag tag) {
        ConcurrentSkipListSet<UUID> loaded = new ConcurrentSkipListSet<>();
        ListTag subscriberList = tag.getList("notificationSubscribers", Tag.TAG_COMPOUND);
        for (Tag rawSubscriber : subscriberList) {
            if (!(rawSubscriber instanceof CompoundTag subscriberTag) || !subscriberTag.contains("playerId")) {
                continue;
            }
            try {
                loaded.add(subscriberTag.getUUID("playerId"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return loaded;
    }

    private static List<ItemStack> loadContents(CompoundTag tag, HolderLookup.Provider registries, ItemStack fallback) {
        ArrayList<ItemStack> loaded = new ArrayList<>();
        ListTag contentTags = tag.getList(CONTENTS_TAG, Tag.TAG_COMPOUND);
        for (Tag rawContent : contentTags) {
            if (!(rawContent instanceof CompoundTag contentTag)) {
                continue;
            }
            ItemStack stack = ItemStack.parseOptional(registries, contentTag.getCompound("item"));
            if (!stack.isEmpty()) {
                loaded.add(stack.copy());
            }
        }
        if (loaded.isEmpty() && fallback != null && !fallback.isEmpty()) {
            loaded.add(fallback.copy());
        }
        return sanitizeContents(loaded);
    }

    private static List<ItemStack> sanitizeContents(List<ItemStack> rawContents) {
        if (rawContents == null || rawContents.isEmpty()) {
            return List.of();
        }
        return rawContents.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .limit(MAX_BUNDLE_CONTENTS)
                .map(ItemStack::copy)
                .toList();
    }

    private static int compareSealedWinner(AuctionBidRecord left, AuctionBidRecord right) {
        int amount = right.getAmount().compareTo(left.getAmount());
        if (amount != 0) {
            return amount;
        }
        int time = left.getTimestamp().compareTo(right.getTimestamp());
        if (time != 0) {
            return time;
        }
        return 0;
    }

    public static String generatedBundleTitle(List<ItemStack> contents) {
        List<ItemStack> safeContents = sanitizeContents(contents);
        if (safeContents.isEmpty()) {
            return "Bundle";
        }
        ItemStack first = safeContents.getFirst();
        int remaining = safeContents.size() - 1;
        if (remaining <= 0) {
            return first.getHoverName().getString();
        }
        return "Bundle: " + first.getHoverName().getString() + " + " + remaining + " more";
    }

    private static CompoundTag saveItemStack(ItemStack stack, HolderLookup.Provider registries) {
        return UasItemStackNbt.saveOptional(stack, registries);
    }

    private static BigDecimal normalizeOptionalPrice(BigDecimal price) {
        return price == null || price.compareTo(BigDecimal.ZERO) <= 0 ? null : price;
    }
}
