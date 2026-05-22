package net.austizz.ultimate_auction_system;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class AuctionHouse {

    private ConcurrentHashMap<UUID, AuctionItem> AuctionItems;

    public AuctionHouse() {
        this.AuctionItems = new ConcurrentHashMap<>();
    }

    public void addAuctionItem(AuctionItem item) {
        this.AuctionItems.put(item.getAuctionId(), item);
    }
    public void removeAuctionItem(AuctionItem item) {
        this.AuctionItems.remove(item.getAuctionId());
    }
    public AuctionItem getAuctionItem(UUID id) {
        if  (this.AuctionItems.containsKey(id)) {
            return this.AuctionItems.get(id);
        }
        return null;
    }
    public ConcurrentHashMap<UUID, AuctionItem> getAuctionItems() {
        return this.AuctionItems;
    }



}
