package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.api.UasPermissionAction;
import net.austizz.ultimate_auction_system.api.UasPermissions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionPermissionModelTest {
    @Test
    void zeroPermissionLevelAllowsEveryoneAndHigherLevelsUseMinecraftSemantics() {
        assertTrue(UasPermissions.hasPermissionLevel(level -> false, 0));
        assertFalse(UasPermissions.hasPermissionLevel(level -> level <= 1, 2));
        assertTrue(UasPermissions.hasPermissionLevel(level -> level <= 2, 2));
    }

    @Test
    void permissionActionsReadConfiguredLevels() {
        int previousList = Config.listPermissionLevel;
        int previousBid = Config.bidPermissionLevel;
        int previousBuyout = Config.buyoutPermissionLevel;
        int previousCancel = Config.cancelOwnPermissionLevel;
        int previousClaim = Config.claimPermissionLevel;
        int previousAdmin = Config.adminStatusPermissionLevel;
        try {
            Config.listPermissionLevel = 1;
            Config.bidPermissionLevel = 2;
            Config.buyoutPermissionLevel = 3;
            Config.cancelOwnPermissionLevel = 4;
            Config.claimPermissionLevel = 1;
            Config.adminStatusPermissionLevel = 2;

            assertEquals(1, UasPermissionAction.LIST.requiredPermissionLevel());
            assertEquals(2, UasPermissionAction.BID.requiredPermissionLevel());
            assertEquals(3, UasPermissionAction.BUYOUT.requiredPermissionLevel());
            assertEquals(4, UasPermissionAction.CANCEL_OWN.requiredPermissionLevel());
            assertEquals(1, UasPermissionAction.CLAIM.requiredPermissionLevel());
            assertEquals(2, UasPermissionAction.ADMIN.requiredPermissionLevel());
        } finally {
            Config.listPermissionLevel = previousList;
            Config.bidPermissionLevel = previousBid;
            Config.buyoutPermissionLevel = previousBuyout;
            Config.cancelOwnPermissionLevel = previousCancel;
            Config.claimPermissionLevel = previousClaim;
            Config.adminStatusPermissionLevel = previousAdmin;
        }
    }

    @Test
    void permissionDenialMessagesAreActionSpecific() {
        assertEquals("You do not have permission to create auction listings.", UasPermissionAction.LIST.deniedMessage());
        assertEquals("You do not have permission to place bids.", UasPermissionAction.BID.deniedMessage());
        assertEquals("You do not have permission to buy out auctions.", UasPermissionAction.BUYOUT.deniedMessage());
        assertEquals("You do not have permission to cancel your own auctions.", UasPermissionAction.CANCEL_OWN.deniedMessage());
        assertEquals("You do not have permission to claim auction items.", UasPermissionAction.CLAIM.deniedMessage());
        assertEquals("You do not have permission to use auction admin tools.", UasPermissionAction.ADMIN.deniedMessage());
    }
}
