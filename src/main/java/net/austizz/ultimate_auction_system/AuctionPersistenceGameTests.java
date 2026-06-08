package net.austizz.ultimate_auction_system;

import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@GameTestHolder(UltimateAuctionSystem.MODID)
@PrefixGameTestTemplate(false)
public final class AuctionPersistenceGameTests {
    private AuctionPersistenceGameTests() {
    }

    @GameTest(template = "empty")
    public static void activeAuctionSurvivesSavedDataReload(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        UUID sellerId = UUID.randomUUID();
        UUID sellerAccountId = UUID.randomUUID();
        UUID bidderId = UUID.randomUUID();
        UUID bidderAccountId = UUID.randomUUID();
        UUID subscriberId = UUID.randomUUID();

        AuctionItem auction = new AuctionItem(
                new ItemStack(Items.DIAMOND_HELMET),
                "Persistence reload coverage",
                LocalDateTime.now().plusHours(2),
                LocalDateTime.now().minusMinutes(1),
                new BigDecimal("100"),
                sellerId,
                sellerAccountId,
                new BigDecimal("500")
        );
        auction.markEscrowed("GAME_TEST_ESCROW");
        auction.recordBid(bidderId, bidderAccountId, new BigDecimal("150"));
        auction.toggleNotificationSubscriber(subscriberId);
        auction.markEndingSoonNotificationSent();

        AuctionItem sealedAuction = new AuctionItem(
                UUID.randomUUID(),
                java.util.List.of(new ItemStack(Items.DIAMOND)),
                "Sealed persistence",
                "Format reload coverage",
                LocalDateTime.now().plusHours(2),
                LocalDateTime.now().minusMinutes(1),
                new BigDecimal("100"),
                sellerId,
                sellerAccountId,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                AuctionFormat.SEALED_BID
        );
        sealedAuction.markEscrowed("GAME_TEST_SEALED_ESCROW");

        AuctionSavedData savedData = new AuctionSavedData();
        savedData.getAuctions().put(auction.getAuctionId(), auction);
        savedData.getAuctions().put(sealedAuction.getAuctionId(), sealedAuction);
        CompoundTag savedRoot = savedData.save(new CompoundTag(), registries);

        AuctionSavedData reloadedData = AuctionSavedData.load(savedRoot, registries);
        ConcurrentHashMap<UUID, AuctionItem> reloadedAuctions = reloadedData.getAuctions();
        AuctionItem reloadedAuction = reloadedAuctions.get(auction.getAuctionId());
        AuctionItem reloadedSealedAuction = reloadedAuctions.get(sealedAuction.getAuctionId());

        helper.assertTrue(reloadedAuction != null, "Reloaded data must contain the active auction");
        helper.assertTrue(reloadedSealedAuction != null, "Reloaded data must contain the sealed auction");
        helper.assertValueEqual(AuctionState.ACTIVE, reloadedAuction.getState(), "Auction state should survive reload");
        helper.assertValueEqual(AuctionFormat.NORMAL, reloadedAuction.getFormat(), "Default format should survive reload");
        helper.assertValueEqual(AuctionFormat.SEALED_BID, reloadedSealedAuction.getFormat(), "Sealed format should survive reload");
        helper.assertTrue(reloadedSealedAuction.isSealedBid(), "Sealed flag should survive reload");
        helper.assertValueEqual("Persistence reload coverage", reloadedAuction.getDescription(), "Description should survive reload");
        helper.assertValueEqual(new BigDecimal("150"), reloadedAuction.getHighestBid(), "Highest bid should survive reload");
        helper.assertValueEqual(bidderId, reloadedAuction.getHighestBidderId(), "Highest bidder should survive reload");
        helper.assertTrue(reloadedAuction.isEscrowed(), "Escrow flag should survive reload");
        helper.assertValueEqual("GAME_TEST_ESCROW", reloadedAuction.getEscrowSource(), "Escrow source should survive reload");
        helper.assertTrue(reloadedAuction.getBidRecords().size() == 1, "Bid history should survive reload");
        helper.assertTrue(reloadedAuction.isNotificationSubscriber(subscriberId), "Notification subscriber should survive reload");
        helper.assertTrue(reloadedAuction.isEndingSoonNotificationSent(), "Ending-soon notification state should survive reload");
        helper.assertTrue(reloadedData.getSkippedRecords() == 0, "Reload should not skip valid records");
        helper.assertFalse(reloadedData.isMigrationFailed(), "Reload should not fail migration");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sealedBidsSelectHighestBidWithEarliestTieBreaker(GameTestHelper helper) {
        AuctionItem auction = sealedAuction(new BigDecimal("100"));
        UUID firstBidder = UUID.randomUUID();
        UUID secondBidder = UUID.randomUUID();

        auction.recordSealedBid(firstBidder, UUID.randomUUID(), new BigDecimal("125"));
        auction.recordSealedBid(secondBidder, UUID.randomUUID(), new BigDecimal("125"));

        helper.assertValueEqual(AuctionFormat.SEALED_BID, auction.getFormat(), "Auction should be sealed");
        helper.assertTrue(auction.isSealedBid(), "Sealed flag should be true");
        helper.assertValueEqual(firstBidder, auction.getHighestBidderId(), "Earliest tied sealed bid should win");
        helper.assertValueEqual(new BigDecimal("125"), auction.getHighestBid(), "Highest sealed bid should be selected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sealedBidRaiseReplacesCurrentBidForThatBidder(GameTestHelper helper) {
        long previousIncrement = Config.minimumBidIncrementDollars;
        try {
            Config.minimumBidIncrementDollars = 5L;
            AuctionItem auction = sealedAuction(new BigDecimal("100"));
            UUID bidder = UUID.randomUUID();
            UUID bidderAccount = UUID.randomUUID();

            auction.recordSealedBid(bidder, bidderAccount, new BigDecimal("125"));
            auction.recordSealedBid(bidder, bidderAccount, new BigDecimal("130"));

            helper.assertValueEqual(new BigDecimal("130"), auction.getBids().get(bidder), "Current sealed bid should update");
            helper.assertValueEqual(new BigDecimal("130"), auction.getHighestBid(), "Raised sealed bid should become highest");
            helper.assertValueEqual(
                    new BigDecimal("130"),
                    auction.getCurrentBidRecordForBidder(bidder).orElseThrow().getAmount(),
                    "Current sealed bid record should point at latest accepted bid"
            );
            helper.assertValueEqual(2L, auction.getBidRecords().stream().filter(AuctionBidRecord::isAccepted).count(), "Bid history should keep both accepted sealed bids");
        } finally {
            Config.minimumBidIncrementDollars = previousIncrement;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sealedBuyoutCanReplaceHigherHiddenBidAfterRefunds(GameTestHelper helper) {
        AuctionItem auction = sealedAuction(new BigDecimal("100"));
        UUID sealedBidder = UUID.randomUUID();
        UUID buyoutBidder = UUID.randomUUID();
        UUID buyoutAccount = UUID.randomUUID();

        auction.recordSealedBid(sealedBidder, UUID.randomUUID(), new BigDecimal("200"));
        auction.clearCurrentBidsAfterRefund();
        AuctionBidRecord buyoutRecord = auction.recordBid(buyoutBidder, buyoutAccount, new BigDecimal("150"));

        helper.assertTrue(buyoutRecord.isAccepted(), "Buyout should be accepted after refunded sealed bids are cleared");
        helper.assertValueEqual(buyoutBidder, auction.getHighestBidderId(), "Buyout bidder should become winner");
        helper.assertValueEqual(new BigDecimal("150"), auction.getHighestBid(), "Buyout amount should become winning bid");
        helper.assertTrue(auction.getBids().size() == 1, "Only the buyout bidder should remain in current bids");
        helper.assertValueEqual(2, auction.getBidRecords().size(), "Audit history should keep the sealed bid and buyout records");
        helper.succeed();
    }

    private static AuctionItem sealedAuction(BigDecimal startingBid) {
        return new AuctionItem(
                UUID.randomUUID(),
                java.util.List.of(new ItemStack(Items.DIAMOND)),
                "Sealed diamond",
                "Hidden-bid test",
                LocalDateTime.now().plusHours(2),
                LocalDateTime.now().minusMinutes(1),
                startingBid,
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                AuctionFormat.SEALED_BID
        );
    }
}
