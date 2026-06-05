package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.client.AuctionHouseScreen;
import net.minecraft.client.Minecraft;

public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handleAuctionSnapshot(AuctionSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AuctionHouseScreen screen) {
            screen.refresh(payload);
        } else {
            minecraft.setScreen(new AuctionHouseScreen(payload));
        }
    }
}
