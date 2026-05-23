package net.austizz.ultimate_auction_system;

import net.minecraft.world.item.ItemStack;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    public AuctionItem(ItemStack item, String description, LocalDateTime dateOfEnd, LocalDateTime dateOfStart, BigDecimal startingBidPrice, UUID playerId) {
        this.item = item.copy(); // CRITICAL: Always copy ItemStacks to prevent inventory reference bugs!
        this.description = description;
        this.dateOfEnd = dateOfEnd;
        this.dateOfStart = dateOfStart;
        this.startingBidPrice = startingBidPrice;
        this.bids = new ConcurrentSkipListMap<>();
        this.auctionId = UUID.randomUUID();
        this.playerId = playerId;
        this.highestBid.set(startingBidPrice);
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
    public ConcurrentSkipListMap<UUID, BigDecimal> getBids() { return bids; }

    public void setBids(ConcurrentSkipListMap<UUID, BigDecimal> bids) { this.bids = bids; }
    public void setDescription(String description) { this.description = description; }

    public void setStartingBidPrice(BigDecimal startingBidPrice) {
        if (startingBidPrice.compareTo(BigDecimal.ZERO) > 0) {
            this.startingBidPrice = startingBidPrice;
        }
    }

    /**
     * Thread-safely processes a player's bid.
     * @return true if the bid was successful, false if it was rejected.
     */
    public synchronized boolean addBid(UUID uuid, BigDecimal bid) {
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
}
