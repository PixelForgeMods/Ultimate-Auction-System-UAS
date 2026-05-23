package net.austizz.ultimate_auction_system;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionSavedDataMigrationTest {
    @Test
    void migratesUnversionedRootToCurrentSchema() {
        CompoundTag legacyRoot = new CompoundTag();
        legacyRoot.put("auctions", new ListTag());

        AuctionSavedDataMigration.MigrationResult result = AuctionSavedDataMigration.migrateRoot(legacyRoot);

        assertFalse(result.failed());
        assertTrue(result.migrated());
        assertEquals(1, result.fromVersion());
        assertEquals(AuctionSavedDataMigration.CURRENT_SCHEMA_VERSION, result.toVersion());
        assertEquals(AuctionSavedDataMigration.CURRENT_SCHEMA_VERSION, result.tag().getInt("schemaVersion"));
        assertEquals(1, result.tag().getInt("migratedFromSchemaVersion"));
    }

    @Test
    void rejectsNewerUnsupportedSchema() {
        CompoundTag futureRoot = new CompoundTag();
        futureRoot.putInt("schemaVersion", AuctionSavedDataMigration.CURRENT_SCHEMA_VERSION + 1);

        AuctionSavedDataMigration.MigrationResult result = AuctionSavedDataMigration.migrateRoot(futureRoot);

        assertTrue(result.failed());
        assertFalse(result.migrated());
    }
}
