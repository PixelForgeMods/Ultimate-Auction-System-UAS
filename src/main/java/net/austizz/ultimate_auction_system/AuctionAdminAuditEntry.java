package net.austizz.ultimate_auction_system;

import net.minecraft.nbt.CompoundTag;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

public record AuctionAdminAuditEntry(
        UUID auditId,
        String action,
        UUID adminId,
        String adminName,
        String target,
        String reason,
        boolean success,
        String message,
        LocalDateTime createdAt
) {
    public static AuctionAdminAuditEntry create(String action,
                                                UUID adminId,
                                                String adminName,
                                                String target,
                                                String reason,
                                                boolean success,
                                                String message) {
        return new AuctionAdminAuditEntry(
                UUID.randomUUID(),
                blank(action, "ADMIN_ACTION"),
                adminId,
                blank(adminName, "Unknown"),
                blank(target, ""),
                blank(reason, ""),
                success,
                blank(message, ""),
                LocalDateTime.now()
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("auditId", auditId == null ? UUID.randomUUID() : auditId);
        tag.putString("action", blank(action, "ADMIN_ACTION"));
        if (adminId != null) {
            tag.putUUID("adminId", adminId);
        }
        tag.putString("adminName", blank(adminName, "Unknown"));
        tag.putString("target", blank(target, ""));
        tag.putString("reason", blank(reason, ""));
        tag.putBoolean("success", success);
        tag.putString("message", blank(message, ""));
        tag.putString("createdAt", (createdAt == null ? LocalDateTime.now() : createdAt).toString());
        return tag;
    }

    public static Optional<AuctionAdminAuditEntry> load(CompoundTag tag) {
        if (tag == null || !tag.contains("action") || !tag.contains("createdAt")) {
            return Optional.empty();
        }
        try {
            UUID auditId = tag.contains("auditId") ? tag.getUUID("auditId") : UUID.randomUUID();
            UUID adminId = tag.contains("adminId") ? tag.getUUID("adminId") : null;
            return Optional.of(new AuctionAdminAuditEntry(
                    auditId,
                    tag.getString("action"),
                    adminId,
                    tag.getString("adminName"),
                    tag.getString("target"),
                    tag.getString("reason"),
                    tag.getBoolean("success"),
                    tag.getString("message"),
                    LocalDateTime.parse(tag.getString("createdAt"))
            ));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Skipped invalid admin audit entry: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
