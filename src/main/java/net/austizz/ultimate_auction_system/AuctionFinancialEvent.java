package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.UasBankingResult;
import net.minecraft.nbt.CompoundTag;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record AuctionFinancialEvent(
        UUID eventId,
        UUID auctionId,
        String type,
        String reference,
        BigDecimal amount,
        boolean success,
        UUID transactionId,
        String result,
        LocalDateTime createdAt
) {
    public AuctionFinancialEvent {
        eventId = eventId == null ? UUID.randomUUID() : eventId;
        type = normalizeType(type);
        reference = reference == null ? "" : reference.trim();
        amount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
        result = result == null ? "" : result;
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public static AuctionFinancialEvent fromBanking(UUID auctionId,
                                                    String type,
                                                    BigDecimal amount,
                                                    String reference,
                                                    UasBankingResult result) {
        UUID transactionId = result == null ? null : result.ubsTransactionId().orElse(null);
        String message = result == null
                ? "UBS returned no result"
                : result.success() ? "ok" : result.reason();
        return new AuctionFinancialEvent(
                UUID.randomUUID(),
                auctionId,
                type,
                result == null ? reference : result.ubsReference().orElse(reference),
                amount,
                result != null && result.success(),
                transactionId,
                message,
                LocalDateTime.now()
        );
    }

    public boolean isValidForAuction(UUID expectedAuctionId) {
        return auctionId != null
                && auctionId.equals(expectedAuctionId)
                && !type.isBlank()
                && !reference.isBlank()
                && amount != null
                && amount.compareTo(BigDecimal.ZERO) >= 0
                && createdAt != null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("eventId", eventId == null ? UUID.randomUUID() : eventId);
        tag.putUUID("auctionId", auctionId);
        tag.putString("type", type);
        tag.putString("reference", reference);
        tag.putString("amount", amount.toPlainString());
        tag.putBoolean("success", success);
        if (transactionId != null) {
            tag.putUUID("transactionId", transactionId);
        }
        tag.putString("result", result);
        tag.putString("createdAt", createdAt.toString());
        return tag;
    }

    public static Optional<AuctionFinancialEvent> load(CompoundTag tag) {
        if (tag == null
                || !tag.contains("auctionId")
                || !tag.contains("type")
                || !tag.contains("reference")
                || !tag.contains("amount")
                || !tag.contains("createdAt")) {
            return Optional.empty();
        }

        try {
            AuctionFinancialEvent event = new AuctionFinancialEvent(
                    tag.contains("eventId") ? tag.getUUID("eventId") : UUID.randomUUID(),
                    tag.getUUID("auctionId"),
                    tag.getString("type"),
                    tag.getString("reference"),
                    new BigDecimal(tag.getString("amount")),
                    tag.getBoolean("success"),
                    tag.contains("transactionId") ? tag.getUUID("transactionId") : null,
                    tag.getString("result"),
                    LocalDateTime.parse(tag.getString("createdAt"))
            );
            return event.isValidForAuction(event.auctionId()) ? Optional.of(event) : Optional.empty();
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Invalid financial event: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static String normalizeType(String type) {
        return type == null || type.isBlank()
                ? "UNKNOWN"
                : type.trim().toUpperCase(Locale.ROOT);
    }
}
