package net.austizz.ultimate_auction_system;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

final class AuctionSavedDataMigration {
    static final int CURRENT_SCHEMA_VERSION = 4;
    static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;
    private static final String SCHEMA_VERSION_TAG = "schemaVersion";
    private static final String MIGRATED_FROM_TAG = "migratedFromSchemaVersion";
    private static final String AUCTIONS_TAG = "auctions";

    private AuctionSavedDataMigration() {
    }

    static MigrationResult migrateRoot(CompoundTag original) {
        CompoundTag migrated = original == null ? new CompoundTag() : original.copy();
        int originalVersion = migrated.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)
                ? migrated.getInt(SCHEMA_VERSION_TAG)
                : MIN_SUPPORTED_SCHEMA_VERSION;

        if (originalVersion < MIN_SUPPORTED_SCHEMA_VERSION) {
            return MigrationResult.failed(
                    migrated,
                    originalVersion,
                    "Unsupported auction data schema " + originalVersion
                            + "; minimum supported schema is " + MIN_SUPPORTED_SCHEMA_VERSION + "."
            );
        }
        if (originalVersion > CURRENT_SCHEMA_VERSION) {
            return MigrationResult.failed(
                    migrated,
                    originalVersion,
                    "Auction data schema " + originalVersion
                            + " is newer than this UAS build supports (" + CURRENT_SCHEMA_VERSION + ")."
            );
        }

        boolean migratedVersion = originalVersion < CURRENT_SCHEMA_VERSION;
        if (migratedVersion) {
            if (originalVersion < 4) {
                migrateEscrowMetadata(migrated, originalVersion);
            }
            migrated.putInt(MIGRATED_FROM_TAG, originalVersion);
            migrated.putInt(SCHEMA_VERSION_TAG, CURRENT_SCHEMA_VERSION);
        } else if (!migrated.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)) {
            migrated.putInt(SCHEMA_VERSION_TAG, CURRENT_SCHEMA_VERSION);
        }

        return MigrationResult.ok(migrated, originalVersion, CURRENT_SCHEMA_VERSION, migratedVersion);
    }

    private static void migrateEscrowMetadata(CompoundTag root, int originalVersion) {
        for (Tag rawAuction : root.getList(AUCTIONS_TAG, Tag.TAG_COMPOUND)) {
            if (!(rawAuction instanceof CompoundTag auctionTag)) {
                continue;
            }
            if (!auctionTag.contains("escrowed")) {
                auctionTag.putBoolean("escrowed", true);
            }
            if (!auctionTag.contains("escrowSource")) {
                auctionTag.putString("escrowSource", "MIGRATED_SCHEMA_" + originalVersion);
            }
            if (!auctionTag.contains("escrowedAt")) {
                auctionTag.putString("escrowedAt", firstNonBlank(
                        auctionTag.getString("updatedAt"),
                        auctionTag.getString("createdAt"),
                        auctionTag.getString("dateOfStart"),
                        "1970-01-01T00:00"
                ));
            }
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "1970-01-01T00:00";
    }

    record MigrationResult(
            CompoundTag tag,
            int fromVersion,
            int toVersion,
            boolean migrated,
            boolean failed,
            String message
    ) {
        static MigrationResult ok(CompoundTag tag, int fromVersion, int toVersion, boolean migrated) {
            String message = migrated
                    ? "Migrated auction data schema from " + fromVersion + " to " + toVersion + "."
                    : "Auction data schema " + toVersion + " is current.";
            return new MigrationResult(tag, fromVersion, toVersion, migrated, false, message);
        }

        static MigrationResult failed(CompoundTag tag, int fromVersion, String message) {
            return new MigrationResult(tag, fromVersion, CURRENT_SCHEMA_VERSION, false, true, message);
        }
    }
}
