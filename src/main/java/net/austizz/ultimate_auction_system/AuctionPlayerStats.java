package net.austizz.ultimate_auction_system;

import net.minecraft.nbt.CompoundTag;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

public record AuctionPlayerStats(
        UUID playerId,
        String playerName,
        int auctionsListed,
        int auctionsWon,
        BigDecimal grossSoldValue,
        BigDecimal grossSpentValue,
        LocalDateTime updatedAt
) {
    public AuctionPlayerStats {
        playerName = playerName == null || playerName.isBlank() ? "Unknown" : playerName.trim();
        auctionsListed = Math.max(0, auctionsListed);
        auctionsWon = Math.max(0, auctionsWon);
        grossSoldValue = safeMoney(grossSoldValue);
        grossSpentValue = safeMoney(grossSpentValue);
        updatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
    }

    public static AuctionPlayerStats empty(UUID playerId, String playerName) {
        return new AuctionPlayerStats(playerId, playerName, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, LocalDateTime.now());
    }

    public AuctionPlayerStats withName(String name) {
        String safeName = name == null || name.isBlank() ? playerName : name.trim();
        if (safeName.equals(playerName)) {
            return this;
        }
        return new AuctionPlayerStats(playerId, safeName, auctionsListed, auctionsWon, grossSoldValue, grossSpentValue, LocalDateTime.now());
    }

    public AuctionPlayerStats recordListing(String name) {
        return new AuctionPlayerStats(
                playerId,
                cleanName(name, playerName),
                auctionsListed + 1,
                auctionsWon,
                grossSoldValue,
                grossSpentValue,
                LocalDateTime.now()
        );
    }

    public AuctionPlayerStats recordSale(String name, BigDecimal amount) {
        return new AuctionPlayerStats(
                playerId,
                cleanName(name, playerName),
                auctionsListed,
                auctionsWon,
                grossSoldValue.add(safeMoney(amount)),
                grossSpentValue,
                LocalDateTime.now()
        );
    }

    public AuctionPlayerStats recordWin(String name, BigDecimal amount) {
        return new AuctionPlayerStats(
                playerId,
                cleanName(name, playerName),
                auctionsListed,
                auctionsWon + 1,
                grossSoldValue,
                grossSpentValue.add(safeMoney(amount)),
                LocalDateTime.now()
        );
    }

    public BigDecimal marketplaceVolume() {
        return grossSoldValue.add(grossSpentValue);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("playerId", playerId);
        tag.putString("playerName", playerName);
        tag.putInt("auctionsListed", auctionsListed);
        tag.putInt("auctionsWon", auctionsWon);
        tag.putString("grossSoldValue", grossSoldValue.toPlainString());
        tag.putString("grossSpentValue", grossSpentValue.toPlainString());
        tag.putString("updatedAt", updatedAt.toString());
        return tag;
    }

    public static Optional<AuctionPlayerStats> load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("playerId")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new AuctionPlayerStats(
                    tag.getUUID("playerId"),
                    tag.getString("playerName"),
                    tag.getInt("auctionsListed"),
                    tag.getInt("auctionsWon"),
                    money(tag.getString("grossSoldValue")),
                    money(tag.getString("grossSpentValue")),
                    parseTime(tag.getString("updatedAt"))
            ));
        } catch (IllegalArgumentException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Skipped invalid player stats entry during load: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static String cleanName(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name.trim();
    }

    private static BigDecimal safeMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
    }

    private static BigDecimal money(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim()).max(BigDecimal.ZERO);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private static LocalDateTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException exception) {
            return LocalDateTime.now();
        }
    }
}
