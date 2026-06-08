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

        AuctionSavedData savedData = new AuctionSavedData();
        savedData.getAuctions().put(auction.getAuctionId(), auction);
        CompoundTag savedRoot = savedData.save(new CompoundTag(), registries);

        AuctionSavedData reloadedData = AuctionSavedData.load(savedRoot, registries);
        ConcurrentHashMap<UUID, AuctionItem> reloadedAuctions = reloadedData.getAuctions();
        AuctionItem reloadedAuction = reloadedAuctions.get(auction.getAuctionId());

        helper.assertTrue(reloadedAuction != null, "Reloaded data must contain the active auction");
        helper.assertValueEqual(AuctionState.ACTIVE, reloadedAuction.getState(), "Auction state should survive reload");
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
}
