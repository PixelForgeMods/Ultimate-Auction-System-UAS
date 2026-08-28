package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.client.AuctionHouseScreen;
import net.austizz.ultimate_auction_system.client.AuctionDisplayClientEvents;
import net.austizz.ultimate_auction_system.client.AuctionDisplayEditorScreen;
import net.austizz.ultimate_auction_system.display.AuctionDisplayBlockEntity;
import net.minecraft.client.Minecraft;

public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handleAuctionSnapshot(AuctionSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AuctionHouseScreen screen) {
            screen.refresh(payload);
            screen.openAuctionDetails(payload.openAuctionId());
        } else {
            AuctionHouseScreen screen = new AuctionHouseScreen(payload);
            minecraft.setScreen(screen);
            screen.openAuctionDetails(payload.openAuctionId());
        }
    }

    public static void handleDisplayEditMode(DisplayEditModePayload payload) {
        AuctionDisplayClientEvents.setEditMode(payload.enabled());
    }

    public static void openDisplayEditor(DisplayEditorPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getBlockEntity(payload.pos()) instanceof AuctionDisplayBlockEntity display) {
            minecraft.setScreen(new AuctionDisplayEditorScreen(payload, display.displayedItem()));
        }
    }
}
