package net.austizz.ultimate_auction_system;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionSavedSearchSavedDataTest {
    private final int originalLimit = Config.maxSavedSearchesPerPlayer;

    @AfterEach
    void restoreConfig() {
        Config.maxSavedSearchesPerPlayer = originalLimit;
    }

    @Test
    void savesRenamesDeletesAndLimitsPlayerSearches() {
        Config.maxSavedSearchesPerPlayer = 2;
        UUID playerId = UUID.randomUUID();
        AuctionSavedSearchSavedData data = new AuctionSavedSearchSavedData();
        AuctionUiQuery tools = new AuctionUiQuery("pickaxe", AuctionCategory.TOOLS, BigDecimal.ONE, new BigDecimal("50"), 24L, AuctionSort.LOWEST_PRICE, "minecraft");

        assertTrue(data.saveSearch(playerId, "Tools", tools).success());
        assertEquals(1, data.list(playerId).size());
        assertEquals("TOOLS", data.list(playerId).getFirst().category());
        assertEquals("minecraft", data.list(playerId).getFirst().modId());

        assertTrue(data.saveSearch(playerId, "tools", AuctionUiQuery.defaults()).success());
        assertEquals(1, data.list(playerId).size(), "saving the same name should update instead of duplicating");
        assertEquals("ALL", data.list(playerId).getFirst().category());

        UUID firstId = data.list(playerId).getFirst().searchId();
        assertTrue(data.renameSearch(playerId, firstId, "Basics").success());
        assertEquals("Basics", data.list(playerId).getFirst().name());

        assertTrue(data.saveSearch(playerId, "Blocks", AuctionUiQuery.defaults()).success());
        assertFalse(data.saveSearch(playerId, "Weapons", AuctionUiQuery.defaults()).success());
        assertFalse(data.renameSearch(playerId, firstId, "Blocks").success());

        assertTrue(data.deleteSearch(playerId, firstId).success());
        assertEquals(1, data.list(playerId).size());
    }
}
