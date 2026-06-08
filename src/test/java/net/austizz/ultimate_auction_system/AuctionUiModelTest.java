package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.FakeUasBankingService;
import net.austizz.ultimate_auction_system.network.AuctionActionPayload;
import net.austizz.ultimate_auction_system.network.AuctionModFilterSummaryPayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionUiModelTest {
    @Test
    void queryEnumsFallBackToSafeDefaults() {
        assertEquals(AuctionCategory.ALL, AuctionCategory.fromToken("unknown"));
        assertEquals(AuctionSort.ENDING_SOON, AuctionSort.fromToken("unknown"));
    }

    @Test
    void queryEnumsParseKnownTokensCaseInsensitively() {
        assertEquals(AuctionCategory.WEAPONS, AuctionCategory.fromToken("weapons"));
        assertEquals(AuctionSort.HIGHEST_BID, AuctionSort.fromToken("highest_bid"));
    }

    @Test
    void queryObjectProvidesSafeDefaultsForNullFields() {
        AuctionUiQuery query = new AuctionUiQuery(null, null, null, null, 0L, null, null);

        assertEquals("", query.safeSearch());
        assertEquals(AuctionCategory.ALL, query.safeCategory());
        assertEquals(AuctionSort.ENDING_SOON, query.safeSort());
        assertEquals("", query.safeModId());
    }

    @Test
    void queryObjectNormalizesModId() {
        AuctionUiQuery query = new AuctionUiQuery("", AuctionCategory.ALL, BigDecimal.ZERO, BigDecimal.ZERO, 0L, AuctionSort.ENDING_SOON, " MineCraft ");

        assertEquals("minecraft", query.safeModId());
    }

    @Test
    void listingFeeUsesConfiguredPercentageWithTwoDecimals() {
        double previousRate = Config.listingFeeRate;
        try {
            Config.listingFeeRate = 0.05D;

            assertEquals(new BigDecimal("6.05"), Config.calculateListingFee(new BigDecimal("121")));
            assertEquals(new BigDecimal("0.00"), Config.calculateListingFee(BigDecimal.ZERO));
        } finally {
            Config.listingFeeRate = previousRate;
        }
    }

    @Test
    void minimumAuctionDurationUsesMinutes() {
        int previousMinimum = Config.minAuctionDurationMinutes;
        try {
            Config.minAuctionDurationMinutes = 5;

            assertEquals(Duration.ofMinutes(5), Config.minimumAuctionDuration());
        } finally {
            Config.minAuctionDurationMinutes = previousMinimum;
        }
    }

    @Test
    void auctionRestrictionValidationSupportsItemsTagsAndMods() {
        assertTrue(Config.isValidAuctionRestriction("minecraft:bedrock"));
        assertTrue(Config.isValidAuctionRestriction("#minecraft:shulker_boxes"));
        assertTrue(Config.isValidAuctionRestriction("@minecraft"));
        assertEquals("@minecraft", Config.normalizeAuctionRestriction(" @Minecraft "));
        assertEquals("Mod", Config.auctionRestrictionType("@minecraft"));
        assertEquals("Tag", Config.auctionRestrictionType("#minecraft:shulker_boxes"));
        assertEquals("Item", Config.auctionRestrictionType("minecraft:bedrock"));
        assertFalse(Config.isValidAuctionRestriction("minecraft"));
        assertFalse(Config.isValidAuctionRestriction("@"));
    }

    @Test
    void playerBanBlocksOnlySelectedActiveActions() {
        UUID playerId = UUID.randomUUID();
        AuctionPlayerBan ban = AuctionPlayerBan.create(
                playerId,
                "Dev",
                true,
                false,
                true,
                false,
                "Testing",
                null,
                UUID.randomUUID(),
                "Admin"
        );

        assertTrue(ban.blocks(AuctionBanAction.CREATE));
        assertFalse(ban.blocks(AuctionBanAction.BID));
        assertTrue(ban.blocks(AuctionBanAction.BUYOUT));
        assertFalse(ban.blocks(AuctionBanAction.WATCH));

        ban.revoke(UUID.randomUUID(), "Admin", "Done");
        assertFalse(ban.blocks(AuctionBanAction.CREATE));
        assertFalse(ban.active());
    }

    @Test
    void claimedAuctionsAreNotClaimableEvenWhenExpired() {
        assertFalse(AuctionHouse.canViewerClaimAuction(AuctionState.CLAIMED, true, true, false, true));
        assertFalse(AuctionHouse.canViewerClaimAuction(AuctionState.CANCELLED, true, true, false, true));
        assertTrue(AuctionHouse.canViewerClaimAuction(AuctionState.ACTIVE, true, true, false, true));
    }

    @Test
    void snapshotCarriesListingFeeRateToClientModel() {
        AuctionHouseSnapshot snapshot = new AuctionHouseSnapshot(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                0.05D,
                "",
                true,
                false
        );

        assertEquals(0.05D, snapshot.listingFeeRate());
    }

    @Test
    void modFilterSummaryPayloadCarriesClientModelFields() {
        AuctionModFilterSummaryPayload payload = AuctionModFilterSummaryPayload.fromSummary(new AuctionModFilterSummary("minecraft", "Minecraft", 2));

        assertEquals("minecraft", payload.modId());
        assertEquals("Minecraft", payload.displayName());
        assertEquals(2, payload.activeAuctionCount());
    }

    @Test
    void priceRangeMatchesCurrentBidOrPositiveBuyout() {
        assertTrue(AuctionHouse.matchesPriceRange(
                new BigDecimal("25"),
                BigDecimal.ZERO,
                new BigDecimal("20"),
                new BigDecimal("30")
        ));

        assertTrue(AuctionHouse.matchesPriceRange(
                new BigDecimal("10"),
                new BigDecimal("75"),
                new BigDecimal("70"),
                new BigDecimal("80")
        ));

        assertFalse(AuctionHouse.matchesPriceRange(
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal("70"),
                new BigDecimal("80")
        ));
    }

    @Test
    void priceRangeSupportsOpenEndedBounds() {
        assertTrue(AuctionHouse.matchesPriceRange(
                new BigDecimal("50"),
                BigDecimal.ZERO,
                new BigDecimal("25"),
                BigDecimal.ZERO
        ));
        assertTrue(AuctionHouse.matchesPriceRange(
                new BigDecimal("50"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("75")
        ));
        assertFalse(AuctionHouse.matchesPriceRange(
                new BigDecimal("100"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("75")
        ));
    }

    @Test
    void rateLimiterReportsRemainingCooldownAndAllowsAfterWindow() {
        int previousBidCooldown = Config.bidCooldownSeconds;
        UUID playerId = UUID.randomUUID();
        try {
            Config.bidCooldownSeconds = 2;
            AuctionRateLimiter.clearForTesting();

            assertTrue(AuctionRateLimiter.checkAndMark(playerId, false, AuctionRateLimiter.Action.BID, 1_000L).success());
            AuctionActionResult rejected = AuctionRateLimiter.checkAndMark(playerId, false, AuctionRateLimiter.Action.BID, 1_500L);
            assertFalse(rejected.success());
            assertEquals("Rate limited. Try again in 2 seconds.", rejected.message());
            assertTrue(AuctionRateLimiter.checkAndMark(playerId, false, AuctionRateLimiter.Action.BID, 3_000L).success());
        } finally {
            Config.bidCooldownSeconds = previousBidCooldown;
            AuctionRateLimiter.clearForTesting();
        }
    }

    @Test
    void rateLimiterAllowsAdminBypass() {
        int previousCancelCooldown = Config.cancelCooldownSeconds;
        UUID playerId = UUID.randomUUID();
        try {
            Config.cancelCooldownSeconds = 60;
            AuctionRateLimiter.clearForTesting();

            assertTrue(AuctionRateLimiter.checkAndMark(playerId, false, AuctionRateLimiter.Action.CANCEL, 1_000L).success());
            assertTrue(AuctionRateLimiter.checkAndMark(playerId, true, AuctionRateLimiter.Action.CANCEL, 1_001L).success());
        } finally {
            Config.cancelCooldownSeconds = previousCancelCooldown;
            AuctionRateLimiter.clearForTesting();
        }
    }

    @Test
    void fakeBankingServiceRecordsUiAlerts() {
        FakeUasBankingService banking = new FakeUasBankingService();
        UUID playerId = UUID.randomUUID();

        assertTrue(banking.sendWarningAlert(playerId, "Auction House", "You were outbid.", 5000).success());

        assertEquals(1, banking.alerts().size());
        assertEquals(playerId, banking.alerts().getFirst().playerId());
        assertEquals("WARNING", banking.alerts().getFirst().tone());
        assertEquals("You were outbid.", banking.alerts().getFirst().message());
    }

    @Test
    void createPayloadUsesSlotFallbackWhenSlotListIsEmpty() {
        AuctionActionPayload payload = new AuctionActionPayload(
                "PREPARE_CREATE",
                null,
                null,
                4,
                List.of(),
                "",
                "",
                "10",
                "0",
                1,
                "",
                "",
                "",
                "ALL",
                "ENDING_SOON",
                "",
                "",
                0L,
                "",
                false
        );

        assertEquals(List.of(4), payload.selectedSlots());
    }

    @Test
    void createPayloadPrefersExplicitSlotListForBundles() {
        AuctionActionPayload payload = new AuctionActionPayload(
                "PREPARE_CREATE",
                null,
                null,
                4,
                List.of(1, 3, 5),
                "Starter bundle",
                "",
                "10",
                "0",
                1,
                "",
                "",
                "",
                "ALL",
                "ENDING_SOON",
                "",
                "",
                0L,
                "",
                false
        );

        assertEquals(List.of(1, 3, 5), payload.selectedSlots());
        assertEquals("Starter bundle", payload.title());
    }
}
