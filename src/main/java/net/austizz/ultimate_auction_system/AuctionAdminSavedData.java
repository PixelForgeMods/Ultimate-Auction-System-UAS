package net.austizz.ultimate_auction_system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionAdminSavedData extends SavedData {
    private static final String DATA_NAME = "ultimate_auction_system_admin";
    private static final String BANS_TAG = "playerBans";
    private static final String AUDIT_TAG = "auditLog";
    private static final String RECOVERY_TAG = "recoveryEntries";
    private static final int AUDIT_LIMIT = 500;
    private static final int SUSPICION_DEDUPE_MINUTES = 10;

    private final ConcurrentHashMap<UUID, AuctionPlayerBan> playerBans;
    private final ConcurrentHashMap<UUID, AuctionRecoveryEntry> recoveryEntries;
    private final List<AuctionAdminAuditEntry> auditLog;

    public AuctionAdminSavedData() {
        this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ArrayList<>());
    }

    private AuctionAdminSavedData(ConcurrentHashMap<UUID, AuctionPlayerBan> playerBans,
                                  ConcurrentHashMap<UUID, AuctionRecoveryEntry> recoveryEntries,
                                  List<AuctionAdminAuditEntry> auditLog) {
        this.playerBans = playerBans;
        this.recoveryEntries = recoveryEntries;
        this.auditLog = auditLog;
    }

    public static SavedData.Factory<AuctionAdminSavedData> factory() {
        return new SavedData.Factory<>(
                AuctionAdminSavedData::new,
                AuctionAdminSavedData::load,
                null
        );
    }

    public static AuctionAdminSavedData get(MinecraftServer server) {
        if (server == null) {
            throw new IllegalStateException("Minecraft server is unavailable");
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld level is unavailable");
        }
        return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public static boolean isBlocked(MinecraftServer server, UUID playerId, AuctionBanAction action) {
        if (server == null || playerId == null || action == null) {
            return false;
        }
        try {
            return get(server).isBlocked(playerId, action);
        } catch (RuntimeException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Could not check auction ban state: {}", exception.getMessage());
            return false;
        }
    }

    public synchronized boolean isBlocked(UUID playerId, AuctionBanAction action) {
        AuctionPlayerBan ban = playerBans.get(playerId);
        return ban != null && ban.blocks(action);
    }

    public synchronized Optional<AuctionPlayerBan> getBan(UUID playerId) {
        return Optional.ofNullable(playerBans.get(playerId));
    }

    public synchronized List<AuctionPlayerBan> getBans() {
        return playerBans.values().stream()
                .sorted(Comparator.comparing(AuctionPlayerBan::createdAt).reversed())
                .toList();
    }

    public synchronized List<AuctionAdminAuditEntry> getAuditLog() {
        return auditLog.stream()
                .sorted(Comparator.comparing(AuctionAdminAuditEntry::createdAt).reversed())
                .toList();
    }

    public synchronized List<AuctionRecoveryEntry> getRecoveryEntries() {
        return recoveryEntries.values().stream()
                .sorted(Comparator.<AuctionRecoveryEntry>comparingInt(entry -> entry.active() ? 0 : 1)
                        .thenComparing(AuctionRecoveryEntry::recoveredAt, Comparator.reverseOrder()))
                .toList();
    }

    public synchronized Optional<AuctionRecoveryEntry> getRecoveryEntry(UUID recoveryId) {
        return Optional.ofNullable(recoveryEntries.get(recoveryId));
    }

    public synchronized AuctionRecoveryEntry addRecovery(AuctionRecoveryEntry entry) {
        if (entry == null || entry.recoveryId() == null) {
            return entry;
        }
        recoveryEntries.put(entry.recoveryId(), entry);
        setDirty();
        return entry;
    }

    public synchronized boolean releaseRecovery(UUID recoveryId, UUID adminId, String adminName, String reason) {
        AuctionRecoveryEntry entry = recoveryEntries.get(recoveryId);
        if (entry == null) {
            return false;
        }
        boolean released = entry.release(adminId, adminName, reason);
        if (released) {
            setDirty();
        }
        return released;
    }

    public synchronized AuctionPlayerBan applyBan(UUID playerId,
                                                  String playerName,
                                                  boolean blockCreate,
                                                  boolean blockBid,
                                                  boolean blockBuyout,
                                                  boolean blockWatch,
                                                  String reason,
                                                  LocalDateTime expiresAt,
                                                  UUID adminId,
                                                  String adminName) {
        AuctionPlayerBan existing = playerBans.get(playerId);
        AuctionPlayerBan ban;
        if (existing == null) {
            ban = AuctionPlayerBan.create(playerId, playerName, blockCreate, blockBid, blockBuyout, blockWatch, reason, expiresAt, adminId, adminName);
            playerBans.put(playerId, ban);
        } else {
            existing.update(playerName, blockCreate, blockBid, blockBuyout, blockWatch, reason, expiresAt);
            ban = existing;
        }
        addAudit("PLAYER_BAN", adminId, adminName, String.valueOf(playerId), reason, true, "Auction-house ban updated.");
        setDirty();
        return ban;
    }

    public synchronized boolean revokeBan(UUID playerId, UUID adminId, String adminName, String reason) {
        AuctionPlayerBan ban = playerBans.get(playerId);
        if (ban == null) {
            addAudit("PLAYER_UNBAN", adminId, adminName, String.valueOf(playerId), reason, false, "No auction-house ban exists.");
            setDirty();
            return false;
        }
        ban.revoke(adminId, adminName, reason);
        addAudit("PLAYER_UNBAN", adminId, adminName, String.valueOf(playerId), reason, true, "Auction-house ban revoked.");
        setDirty();
        return true;
    }

    public synchronized void addAudit(String action,
                                      UUID adminId,
                                      String adminName,
                                      String target,
                                      String reason,
                                      boolean success,
                                      String message) {
        auditLog.add(AuctionAdminAuditEntry.create(action, adminId, adminName, target, reason, success, message));
        auditLog.sort(Comparator.comparing(AuctionAdminAuditEntry::createdAt).reversed());
        while (auditLog.size() > AUDIT_LIMIT) {
            auditLog.remove(auditLog.size() - 1);
        }
        setDirty();
    }

    public synchronized boolean addSuspicion(AuctionSuspicionSignal signal) {
        if (signal == null || !Config.auditSuspiciousBidPatterns) {
            return false;
        }
        String target = signal.auditTarget();
        if (target == null || target.isBlank()) {
            return false;
        }
        String reason = signal.type();
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(SUSPICION_DEDUPE_MINUTES);
        boolean duplicate = auditLog.stream().anyMatch(entry ->
                entry != null
                        && "SUSPICIOUS_BID_PATTERN".equalsIgnoreCase(entry.action())
                        && target.equals(entry.target())
                        && reason.equalsIgnoreCase(entry.reason())
                        && entry.createdAt() != null
                        && entry.createdAt().isAfter(cutoff));
        if (duplicate) {
            return false;
        }
        addAudit("SUSPICIOUS_BID_PATTERN", null, "UAS Audit", target, reason, true, signal.auditMessage());
        return true;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag banTags = new ListTag();
        for (AuctionPlayerBan ban : playerBans.values()) {
            if (ban != null && ban.playerId() != null) {
                banTags.add(ban.save());
            }
        }
        tag.put(BANS_TAG, banTags);

        ListTag auditTags = new ListTag();
        for (AuctionAdminAuditEntry entry : auditLog) {
            if (entry != null) {
                auditTags.add(entry.save());
            }
        }
        tag.put(AUDIT_TAG, auditTags);

        ListTag recoveryTags = new ListTag();
        for (AuctionRecoveryEntry entry : recoveryEntries.values()) {
            if (entry != null && entry.recoveryId() != null) {
                recoveryTags.add(entry.save(registries));
            }
        }
        tag.put(RECOVERY_TAG, recoveryTags);
        return tag;
    }

    private static AuctionAdminSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ConcurrentHashMap<UUID, AuctionPlayerBan> bans = new ConcurrentHashMap<>();
        ConcurrentHashMap<UUID, AuctionRecoveryEntry> recoveryEntries = new ConcurrentHashMap<>();
        List<AuctionAdminAuditEntry> audit = new ArrayList<>();
        if (tag == null) {
            return new AuctionAdminSavedData(bans, recoveryEntries, audit);
        }

        ListTag banTags = tag.getList(BANS_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : banTags) {
            if (raw instanceof CompoundTag banTag) {
                AuctionPlayerBan.load(banTag).ifPresent(ban -> bans.put(ban.playerId(), ban));
            }
        }

        ListTag auditTags = tag.getList(AUDIT_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : auditTags) {
            if (raw instanceof CompoundTag auditTag) {
                AuctionAdminAuditEntry.load(auditTag).ifPresent(audit::add);
            }
        }
        ListTag recoveryTags = tag.getList(RECOVERY_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : recoveryTags) {
            if (raw instanceof CompoundTag recoveryTag) {
                AuctionRecoveryEntry.load(recoveryTag, registries)
                        .ifPresent(entry -> recoveryEntries.put(entry.recoveryId(), entry));
            }
        }
        audit.sort(Comparator.comparing(AuctionAdminAuditEntry::createdAt).reversed());
        while (audit.size() > AUDIT_LIMIT) {
            audit.remove(audit.size() - 1);
        }
        return new AuctionAdminSavedData(bans, recoveryEntries, audit);
    }
}
