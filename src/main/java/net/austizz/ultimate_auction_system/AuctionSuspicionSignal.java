package net.austizz.ultimate_auction_system;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionSuspicionSignal(
        String type,
        UUID auctionId,
        String itemName,
        UUID primaryPlayerId,
        String primaryPlayerName,
        UUID secondaryPlayerId,
        String secondaryPlayerName,
        int evidenceCount,
        int windowSeconds,
        BigDecimal startAmount,
        BigDecimal endAmount,
        LocalDateTime observedAt
) {
    public static final String RAPID_BID_ESCALATION = "RAPID_BID_ESCALATION";
    public static final String REPEATED_BIDDER_PAIR = "REPEATED_BIDDER_PAIR";
    public static final String SELLER_SELF_BID = "SELLER_SELF_BID";
    public static final String REPEATED_CANCELLED_LISTINGS = "REPEATED_CANCELLED_LISTINGS";

    public AuctionSuspicionSignal {
        type = type == null || type.isBlank() ? "UNKNOWN" : type.trim().toUpperCase(java.util.Locale.ROOT);
        itemName = itemName == null ? "" : itemName;
        primaryPlayerName = primaryPlayerName == null ? "" : primaryPlayerName;
        secondaryPlayerName = secondaryPlayerName == null ? "" : secondaryPlayerName;
        evidenceCount = Math.max(0, evidenceCount);
        windowSeconds = Math.max(0, windowSeconds);
        startAmount = startAmount == null ? BigDecimal.ZERO : startAmount;
        endAmount = endAmount == null ? BigDecimal.ZERO : endAmount;
        observedAt = observedAt == null ? LocalDateTime.now() : observedAt;
    }

    public String auditTarget() {
        if (auctionId != null) {
            return auctionId.toString();
        }
        return primaryPlayerId == null ? "" : primaryPlayerId.toString();
    }

    public String auditMessage() {
        return type + " evidence=" + evidenceCount + " primary=" + primaryPlayerName + " secondary=" + secondaryPlayerName;
    }
}
