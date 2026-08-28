package net.austizz.ultimate_auction_system;

import net.minecraft.nbt.CompoundTag;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

public final class AuctionPlayerBan {
    private final UUID banId;
    private final UUID playerId;
    private String playerName;
    private boolean blockCreate;
    private boolean blockBid;
    private boolean blockBuyout;
    private boolean blockWatch;
    private String reason;
    private final UUID createdBy;
    private final String createdByName;
    private final LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private UUID revokedBy;
    private String revokedByName;
    private String revokedReason;
    private LocalDateTime revokedAt;

    private AuctionPlayerBan(UUID banId,
                             UUID playerId,
                             String playerName,
                             boolean blockCreate,
                             boolean blockBid,
                             boolean blockBuyout,
                             boolean blockWatch,
                             String reason,
                             UUID createdBy,
                             String createdByName,
                             LocalDateTime createdAt,
                             LocalDateTime expiresAt,
                             UUID revokedBy,
                             String revokedByName,
                             String revokedReason,
                             LocalDateTime revokedAt) {
        this.banId = banId == null ? UUID.randomUUID() : banId;
        this.playerId = playerId;
        this.playerName = blank(playerName, fallbackName(playerId));
        this.blockCreate = blockCreate;
        this.blockBid = blockBid;
        this.blockBuyout = blockBuyout;
        this.blockWatch = blockWatch;
        this.reason = blank(reason, "No reason provided");
        this.createdBy = createdBy;
        this.createdByName = blank(createdByName, "Unknown");
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.expiresAt = expiresAt;
        this.revokedBy = revokedBy;
        this.revokedByName = blank(revokedByName, "");
        this.revokedReason = blank(revokedReason, "");
        this.revokedAt = revokedAt;
    }

    public static AuctionPlayerBan create(UUID playerId,
                                          String playerName,
                                          boolean blockCreate,
                                          boolean blockBid,
                                          boolean blockBuyout,
                                          boolean blockWatch,
                                          String reason,
                                          LocalDateTime expiresAt,
                                          UUID adminId,
                                          String adminName) {
        return new AuctionPlayerBan(
                UUID.randomUUID(),
                playerId,
                playerName,
                blockCreate,
                blockBid,
                blockBuyout,
                blockWatch,
                reason,
                adminId,
                adminName,
                LocalDateTime.now(),
                expiresAt,
                null,
                "",
                "",
                null
        );
    }

    public UUID banId() {
        return banId;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public boolean blockCreate() {
        return blockCreate;
    }

    public boolean blockBid() {
        return blockBid;
    }

    public boolean blockBuyout() {
        return blockBuyout;
    }

    public boolean blockWatch() {
        return blockWatch;
    }

    public String reason() {
        return reason;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public String createdByName() {
        return createdByName;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public Optional<LocalDateTime> expiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    public Optional<LocalDateTime> revokedAt() {
        return Optional.ofNullable(revokedAt);
    }

    public String revokedReason() {
        return revokedReason;
    }

    public boolean active() {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }

    public boolean blocks(AuctionBanAction action) {
        if (!active()) {
            return false;
        }
        return switch (action) {
            case CREATE -> blockCreate;
            case BID -> blockBid;
            case BUYOUT -> blockBuyout;
            case WATCH -> blockWatch;
        };
    }

    public void update(String playerName,
                       boolean blockCreate,
                       boolean blockBid,
                       boolean blockBuyout,
                       boolean blockWatch,
                       String reason,
                       LocalDateTime expiresAt) {
        this.playerName = blank(playerName, this.playerName);
        this.blockCreate = blockCreate;
        this.blockBid = blockBid;
        this.blockBuyout = blockBuyout;
        this.blockWatch = blockWatch;
        this.reason = blank(reason, this.reason);
        this.expiresAt = expiresAt;
        this.revokedBy = null;
        this.revokedByName = "";
        this.revokedReason = "";
        this.revokedAt = null;
    }

    public void revoke(UUID adminId, String adminName, String reason) {
        this.revokedBy = adminId;
        this.revokedByName = blank(adminName, "Unknown");
        this.revokedReason = blank(reason, "Manual unban");
        this.revokedAt = LocalDateTime.now();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("banId", banId);
        tag.putUUID("playerId", playerId);
        tag.putString("playerName", blank(playerName, fallbackName(playerId)));
        tag.putBoolean("blockCreate", blockCreate);
        tag.putBoolean("blockBid", blockBid);
        tag.putBoolean("blockBuyout", blockBuyout);
        tag.putBoolean("blockWatch", blockWatch);
        tag.putString("reason", blank(reason, "No reason provided"));
        if (createdBy != null) {
            tag.putUUID("createdBy", createdBy);
        }
        tag.putString("createdByName", blank(createdByName, "Unknown"));
        tag.putString("createdAt", createdAt.toString());
        if (expiresAt != null) {
            tag.putString("expiresAt", expiresAt.toString());
        }
        if (revokedBy != null) {
            tag.putUUID("revokedBy", revokedBy);
        }
        tag.putString("revokedByName", blank(revokedByName, ""));
        tag.putString("revokedReason", blank(revokedReason, ""));
        if (revokedAt != null) {
            tag.putString("revokedAt", revokedAt.toString());
        }
        return tag;
    }

    public static Optional<AuctionPlayerBan> load(CompoundTag tag) {
        if (tag == null || !tag.contains("playerId") || !tag.contains("createdAt")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new AuctionPlayerBan(
                    tag.contains("banId") ? tag.getUUID("banId") : UUID.randomUUID(),
                    tag.getUUID("playerId"),
                    tag.getString("playerName"),
                    tag.getBoolean("blockCreate"),
                    tag.getBoolean("blockBid"),
                    tag.getBoolean("blockBuyout"),
                    tag.getBoolean("blockWatch"),
                    tag.getString("reason"),
                    tag.contains("createdBy") ? tag.getUUID("createdBy") : null,
                    tag.getString("createdByName"),
                    LocalDateTime.parse(tag.getString("createdAt")),
                    tag.contains("expiresAt") ? LocalDateTime.parse(tag.getString("expiresAt")) : null,
                    tag.contains("revokedBy") ? tag.getUUID("revokedBy") : null,
                    tag.getString("revokedByName"),
                    tag.getString("revokedReason"),
                    tag.contains("revokedAt") ? LocalDateTime.parse(tag.getString("revokedAt")) : null
            ));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Skipped invalid auction player ban: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String fallbackName(UUID playerId) {
        return playerId == null ? "Unknown" : playerId.toString().substring(0, 8);
    }
}
