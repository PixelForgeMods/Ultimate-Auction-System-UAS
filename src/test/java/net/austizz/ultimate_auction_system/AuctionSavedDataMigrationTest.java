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

    @Test
    void migratesSchemaThreeAuctionEscrowMetadata() {
        CompoundTag schemaThreeRoot = new CompoundTag();
        schemaThreeRoot.putInt("schemaVersion", 3);
        CompoundTag auction = new CompoundTag();
        auction.putString("updatedAt", "2026-05-23T12:00");
        ListTag auctions = new ListTag();
        auctions.add(auction);
        schemaThreeRoot.put("auctions", auctions);

        AuctionSavedDataMigration.MigrationResult result = AuctionSavedDataMigration.migrateRoot(schemaThreeRoot);
        CompoundTag migratedAuction = result.tag().getList("auctions", 10).getCompound(0);

        assertFalse(result.failed());
        assertTrue(result.migrated());
        assertTrue(migratedAuction.getBoolean("escrowed"));
        assertEquals("MIGRATED_SCHEMA_3", migratedAuction.getString("escrowSource"));
        assertEquals("2026-05-23T12:00", migratedAuction.getString("escrowedAt"));
    }

    @Test
    void itemStackNbtHelperKeepsReturnedEncodedCompound() {
        CompoundTag encoded = new CompoundTag();
        encoded.putString("id", "minecraft:diamond_helmet");

        CompoundTag result = UasItemStackNbt.asCompound(encoded);

        assertEquals("minecraft:diamond_helmet", result.getString("id"));
        assertTrue(UasItemStackNbt.asCompound(null).isEmpty());
    }
}
