package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.AuctionActionResult;
import net.austizz.ultimate_auction_system.AuctionCategory;
import net.austizz.ultimate_auction_system.AuctionDeliverySavedData;
import net.austizz.ultimate_auction_system.AuctionHouse;
import net.austizz.ultimate_auction_system.AuctionHouseSnapshot;
import net.austizz.ultimate_auction_system.AuctionSort;
import net.austizz.ultimate_auction_system.AuctionUiQuery;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@EventBusSubscriber(modid = UltimateAuctionSystem.MODID)
public final class UasPayloads {
    private UasPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(AuctionActionPayload.TYPE, AuctionActionPayload.STREAM_CODEC, UasPayloads::handleAuctionAction);
        registrar.playToClient(AuctionSnapshotPayload.TYPE, AuctionSnapshotPayload.STREAM_CODEC, UasPayloads::handleAuctionSnapshot);
    }

    public static void openAuctionHouse(ServerPlayer player) {
        sendSnapshot(player, AuctionUiQuery.defaults(), "", true);
    }

    private static void handleAuctionSnapshot(AuctionSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadInvoker.invoke("handleAuctionSnapshot", payload));
    }

    private static void handleAuctionAction(AuctionActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            AuctionUiQuery query = queryFrom(payload);
            AuctionHouse house = UltimateAuctionSystem.auctionHouse;
            if (house == null) {
                sendSnapshot(player, query, "Auction house is not initialized.", false);
                return;
            }
            AuctionDeliverySavedData deliveryData = AuctionDeliverySavedData.get(player.getServer());
            AuctionActionResult result = switch (safe(payload.action())) {
                case "CREATE" -> house.createAuctionFromInventorySlot(
                        player,
                        payload.slot(),
                        money(payload.startingBid()),
                        money(payload.buyoutPrice()),
                        endDateTime(payload),
                        payload.description()
                );
                case "BID" -> house.placeBidWithEscrow(player, payload.auctionId(), money(payload.amount()));
                case "BUYOUT" -> house.buyout(player, payload.auctionId());
                case "CANCEL" -> house.cancelOwnAuction(player, payload.auctionId(), deliveryData);
                case "CLAIM" -> house.claimAuction(player, payload.auctionId(), deliveryData);
                case "TOGGLE_NOTIFICATIONS" -> house.toggleNotifications(player, payload.auctionId());
                case "WITHDRAW_DELIVERY" -> house.withdrawDelivery(player, payload.deliveryId(), deliveryData);
                default -> AuctionActionResult.ok("");
            };
            house.sendActionAlert(player, result);
            sendSnapshot(player, query, result.message(), result.success());
        });
    }

    private static void sendSnapshot(ServerPlayer player, AuctionUiQuery query, String message, boolean success) {
        AuctionHouse house = UltimateAuctionSystem.auctionHouse;
        if (player == null || house == null || player.getServer() == null) {
            return;
        }
        AuctionDeliverySavedData deliveryData = AuctionDeliverySavedData.get(player.getServer());
        AuctionHouseSnapshot snapshot = house.buildSnapshot(player, deliveryData, query, message, success);
        PacketDistributor.sendToPlayer(player, AuctionSnapshotPayload.fromSnapshot(snapshot));
    }

    private static AuctionUiQuery queryFrom(AuctionActionPayload payload) {
        return new AuctionUiQuery(
                payload.search(),
                AuctionCategory.fromToken(payload.category()),
                money(payload.minimumPrice()),
                money(payload.maximumPrice()),
                Math.max(0L, payload.maximumHoursLeft()),
                AuctionSort.fromToken(payload.sort())
        );
    }

    private static BigDecimal money(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim().replace("$", "").replace(",", ""));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String safe(String action) {
        return action == null ? "" : action.trim().toUpperCase();
    }

    private static LocalDateTime endDateTime(AuctionActionPayload payload) {
        String raw = payload.endDateTime();
        if (raw != null && !raw.isBlank()) {
            try {
                return LocalDateTime.parse(raw.trim());
            } catch (DateTimeParseException ignored) {
            }
        }
        return LocalDateTime.now().plusHours(Math.max(1, payload.durationHours()));
    }
}
