package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.UasBankingResult;
import net.minecraft.nbt.CompoundTag;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

public final class AuctionBidRecord {
    private final UUID auctionId;
    private final UUID bidderId;
    private final UUID bidderAccountId;
    private final BigDecimal amount;
    private final LocalDateTime timestamp;
    private final AuctionBidResult result;
    private final String reason;
    private String settlementReference;
    private UUID settlementTransactionId;
    private String settlementResult;

    private AuctionBidRecord(UUID auctionId,
                             UUID bidderId,
                             UUID bidderAccountId,
                             BigDecimal amount,
                             LocalDateTime timestamp,
                             AuctionBidResult result,
                             String reason,
                             String settlementReference,
                             UUID settlementTransactionId,
                             String settlementResult) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.bidderAccountId = bidderAccountId;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
        this.timestamp = timestamp == null ? LocalDateTime.now() : timestamp;
        this.result = result == null ? AuctionBidResult.REJECTED_INVALID : result;
        this.reason = reason == null ? "" : reason;
        this.settlementReference = blankToNull(settlementReference);
        this.settlementTransactionId = settlementTransactionId;
        this.settlementResult = blankToNull(settlementResult);
    }

    public static AuctionBidRecord accepted(UUID auctionId, UUID bidderId, UUID bidderAccountId, BigDecimal amount) {
        return new AuctionBidRecord(
                auctionId,
                bidderId,
                bidderAccountId,
                amount,
                LocalDateTime.now(),
                AuctionBidResult.ACCEPTED,
                "accepted",
                null,
                null,
                null
        );
    }

    public static AuctionBidRecord rejected(UUID auctionId,
                                            UUID bidderId,
                                            UUID bidderAccountId,
                                            BigDecimal amount,
                                            AuctionBidResult result,
                                            String reason) {
        return new AuctionBidRecord(
                auctionId,
                bidderId,
                bidderAccountId,
                amount,
                LocalDateTime.now(),
                result == AuctionBidResult.ACCEPTED ? AuctionBidResult.REJECTED_INVALID : result,
                reason,
                null,
                null,
                null
        );
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public UUID getBidderId() {
        return bidderId;
    }

    public Optional<UUID> getBidderAccountId() {
        return Optional.ofNullable(bidderAccountId);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public AuctionBidResult getResult() {
        return result;
    }

    public String getReason() {
        return reason;
    }

    public Optional<String> getSettlementReference() {
        return Optional.ofNullable(settlementReference);
    }

    public Optional<UUID> getSettlementTransactionId() {
        return Optional.ofNullable(settlementTransactionId);
    }

    public Optional<String> getSettlementResult() {
        return Optional.ofNullable(settlementResult);
    }

    public boolean isAccepted() {
        return result == AuctionBidResult.ACCEPTED;
    }

    public void linkSettlement(String reference, UasBankingResult result) {
        this.settlementReference = blankToNull(reference);
        this.settlementTransactionId = result == null ? null : result.ubsTransactionId().orElse(null);
        if (result == null) {
            this.settlementResult = "UBS returned no settlement result";
        } else {
            this.settlementResult = result.success() ? "settled" : result.reason();
        }
    }

    public boolean isValidForAuction(UUID expectedAuctionId) {
        if (auctionId == null || !auctionId.equals(expectedAuctionId)) {
            return false;
        }
        if (bidderId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || timestamp == null || result == null) {
            return false;
        }
        return !isAccepted() || bidderAccountId != null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("auctionId", auctionId);
        tag.putUUID("bidderId", bidderId);
        if (bidderAccountId != null) {
            tag.putUUID("bidderAccountId", bidderAccountId);
        }
        tag.putString("amount", amount.toPlainString());
        tag.putString("timestamp", timestamp.toString());
        tag.putString("result", result.name());
        tag.putString("reason", reason);
        if (settlementReference != null) {
            tag.putString("settlementReference", settlementReference);
        }
        if (settlementTransactionId != null) {
            tag.putUUID("settlementTransactionId", settlementTransactionId);
        }
        if (settlementResult != null) {
            tag.putString("settlementResult", settlementResult);
        }
        return tag;
    }

    public static Optional<AuctionBidRecord> load(CompoundTag tag) {
        if (tag == null
                || !tag.contains("auctionId")
                || !tag.contains("bidderId")
                || !tag.contains("amount")
                || !tag.contains("timestamp")
                || !tag.contains("result")) {
            return Optional.empty();
        }

        try {
            UUID auctionId = tag.getUUID("auctionId");
            UUID bidderId = tag.getUUID("bidderId");
            UUID bidderAccountId = tag.contains("bidderAccountId") ? tag.getUUID("bidderAccountId") : null;
            BigDecimal amount = new BigDecimal(tag.getString("amount"));
            LocalDateTime timestamp = LocalDateTime.parse(tag.getString("timestamp"));
            AuctionBidResult result = AuctionBidResult.fromSerializedName(tag.getString("result"));
            AuctionBidRecord record = new AuctionBidRecord(
                    auctionId,
                    bidderId,
                    bidderAccountId,
                    amount,
                    timestamp,
                    result,
                    tag.getString("reason"),
                    tag.contains("settlementReference") ? tag.getString("settlementReference") : null,
                    tag.contains("settlementTransactionId") ? tag.getUUID("settlementTransactionId") : null,
                    tag.contains("settlementResult") ? tag.getString("settlementResult") : null
            );
            return record.isValidForAuction(auctionId) ? Optional.of(record) : Optional.empty();
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Invalid bid record: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
