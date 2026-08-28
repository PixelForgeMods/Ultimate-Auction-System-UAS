package net.austizz.ultimate_auction_system.api.event;

import net.austizz.ultimate_auction_system.api.UasAuctionSnapshot;
import net.neoforged.bus.api.Event;

import java.math.BigDecimal;
import java.util.UUID;

public final class UasAuctionEvents {
    private UasAuctionEvents() {
    }

    public abstract static class AuctionEvent extends Event {
        private final UasAuctionSnapshot auction;

        protected AuctionEvent(UasAuctionSnapshot auction) {
            this.auction = auction;
        }

        public UasAuctionSnapshot auction() {
            return auction;
        }

        public UUID auctionId() {
            return auction == null ? null : auction.auctionId();
        }
    }

    public static final class ListingCreated extends AuctionEvent {
        private final UUID sellerId;

        public ListingCreated(UasAuctionSnapshot auction, UUID sellerId) {
            super(auction);
            this.sellerId = sellerId;
        }

        public UUID sellerId() {
            return sellerId;
        }
    }

    public static final class BidAccepted extends AuctionEvent {
        private final UUID bidderId;
        private final BigDecimal amount;
        private final UUID previousBidderId;
        private final BigDecimal previousAmount;
        private final boolean completedByBuyout;

        public BidAccepted(UasAuctionSnapshot auction,
                           UUID bidderId,
                           BigDecimal amount,
                           UUID previousBidderId,
                           BigDecimal previousAmount,
                           boolean completedByBuyout) {
            super(auction);
            this.bidderId = bidderId;
            this.amount = amount == null ? BigDecimal.ZERO : amount;
            this.previousBidderId = previousBidderId;
            this.previousAmount = previousAmount == null ? BigDecimal.ZERO : previousAmount;
            this.completedByBuyout = completedByBuyout;
        }

        public UUID bidderId() {
            return bidderId;
        }

        public BigDecimal amount() {
            return amount;
        }

        public UUID previousBidderId() {
            return previousBidderId;
        }

        public BigDecimal previousAmount() {
            return previousAmount;
        }

        public boolean completedByBuyout() {
            return completedByBuyout;
        }
    }

    public static final class Outbid extends AuctionEvent {
        private final UUID previousBidderId;
        private final BigDecimal previousAmount;
        private final UUID newBidderId;
        private final BigDecimal newAmount;

        public Outbid(UasAuctionSnapshot auction,
                      UUID previousBidderId,
                      BigDecimal previousAmount,
                      UUID newBidderId,
                      BigDecimal newAmount) {
            super(auction);
            this.previousBidderId = previousBidderId;
            this.previousAmount = previousAmount == null ? BigDecimal.ZERO : previousAmount;
            this.newBidderId = newBidderId;
            this.newAmount = newAmount == null ? BigDecimal.ZERO : newAmount;
        }

        public UUID previousBidderId() {
            return previousBidderId;
        }

        public BigDecimal previousAmount() {
            return previousAmount;
        }

        public UUID newBidderId() {
            return newBidderId;
        }

        public BigDecimal newAmount() {
            return newAmount;
        }
    }

    public static final class BuyoutAccepted extends AuctionEvent {
        private final UUID buyerId;
        private final BigDecimal amount;

        public BuyoutAccepted(UasAuctionSnapshot auction, UUID buyerId, BigDecimal amount) {
            super(auction);
            this.buyerId = buyerId;
            this.amount = amount == null ? BigDecimal.ZERO : amount;
        }

        public UUID buyerId() {
            return buyerId;
        }

        public BigDecimal amount() {
            return amount;
        }
    }

    public static final class Sold extends AuctionEvent {
        private final UUID buyerId;
        private final BigDecimal amount;

        public Sold(UasAuctionSnapshot auction, UUID buyerId, BigDecimal amount) {
            super(auction);
            this.buyerId = buyerId;
            this.amount = amount == null ? BigDecimal.ZERO : amount;
        }

        public UUID buyerId() {
            return buyerId;
        }

        public BigDecimal amount() {
            return amount;
        }
    }

    public static final class Cancelled extends AuctionEvent {
        private final UUID actorId;
        private final String reason;
        private final boolean adminAction;

        public Cancelled(UasAuctionSnapshot auction, UUID actorId, String reason, boolean adminAction) {
            super(auction);
            this.actorId = actorId;
            this.reason = reason == null ? "" : reason;
            this.adminAction = adminAction;
        }

        public UUID actorId() {
            return actorId;
        }

        public String reason() {
            return reason;
        }

        public boolean adminAction() {
            return adminAction;
        }
    }

    public static final class SettlementFailed extends AuctionEvent {
        private final String reason;

        public SettlementFailed(UasAuctionSnapshot auction, String reason) {
            super(auction);
            this.reason = reason == null ? "" : reason;
        }

        public String reason() {
            return reason;
        }
    }

    public static final class Claimed extends AuctionEvent {
        private final UUID claimerId;
        private final boolean sellerReturn;

        public Claimed(UasAuctionSnapshot auction, UUID claimerId, boolean sellerReturn) {
            super(auction);
            this.claimerId = claimerId;
            this.sellerReturn = sellerReturn;
        }

        public UUID claimerId() {
            return claimerId;
        }

        public boolean sellerReturn() {
            return sellerReturn;
        }
    }
}
