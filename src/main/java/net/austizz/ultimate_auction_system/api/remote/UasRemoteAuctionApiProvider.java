package net.austizz.ultimate_auction_system.api.remote;

import net.austizz.ultimate_auction_system.AuctionDeliveryEntry;
import net.austizz.ultimate_auction_system.AuctionDeliverySavedData;
import net.austizz.ultimate_auction_system.AuctionHouse;
import net.austizz.ultimate_auction_system.AuctionItem;
import net.austizz.ultimate_auction_system.AuctionState;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UasRemoteAuctionApiProvider {
    private static final String API_VERSION = "1.1.0";
    private static final Map<String, UasRemoteCommandResult> COMMANDS = new ConcurrentHashMap<>();
    private static final UasRemoteAuctionApi API = new DefaultApi();

    private UasRemoteAuctionApiProvider() {
    }

    public static UasRemoteAuctionApi get() {
        return API;
    }

    private static final class DefaultApi implements UasRemoteAuctionApi {
        @Override
        public String getApiVersion() {
            return API_VERSION;
        }

        @Override
        public UasRemoteSnapshot snapshot(UasRemoteSnapshotRequest request) {
            AuctionHouse house = UltimateAuctionSystem.auctionHouse;
            if (house == null) {
                return UasRemoteSnapshot.empty(API_VERSION);
            }
            List<UasRemoteAuctionSnapshot> auctions = request.includeAuctions()
                    ? house.getAuctionItems().values().stream().map(DefaultApi::auction).toList()
                    : List.of();
            List<UasRemoteDeliverySnapshot> deliveries = request.includeDeliveries()
                    ? deliveries()
                    : List.of();
            return new UasRemoteSnapshot(API_VERSION, revision(house), Instant.now(), auctions, deliveries);
        }

        @Override
        public Optional<UasRemoteCommandResult> findCommand(String idempotencyKey) {
            return Optional.ofNullable(idempotencyKey == null ? null : COMMANDS.get(idempotencyKey));
        }

        @Override
        public UasRemoteCommandResult execute(UasRemoteCommand command) {
            if (command == null || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
                return UasRemoteCommandResult.failure("", UasRemoteResultCode.INVALID_COMMAND, "uas.remote.invalid_command");
            }
            UasRemoteCommandResult previous = COMMANDS.get(command.idempotencyKey());
            if (previous != null) {
                return previous.asDuplicate();
            }
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            AuctionHouse house = UltimateAuctionSystem.auctionHouse;
            ServerPlayer actor = server == null || command.actorId() == null
                    ? null
                    : server.getPlayerList().getPlayer(command.actorId());
            if (house == null || actor == null) {
                return remember(command, UasRemoteCommandResult.failure(command.idempotencyKey(), UasRemoteResultCode.SERVER_UNAVAILABLE, "uas.remote.server_unavailable"));
            }
            var action = switch (command.type()) {
                case BID -> house.placeBidWithEscrow(actor, command.auctionId(), command.amount(), command.accountId());
                case BUYOUT -> house.buyout(actor, command.auctionId(), command.accountId());
                case CANCEL -> house.cancelOwnAuction(actor, command.auctionId(), AuctionDeliverySavedData.get(server));
                default -> null;
            };
            if (action == null) {
                return remember(command, UasRemoteCommandResult.failure(command.idempotencyKey(), UasRemoteResultCode.INVALID_COMMAND, "uas.remote.invalid_command"));
            }
            UasRemoteCommandResult result = action.success()
                    ? UasRemoteCommandResult.success(command.idempotencyKey(), action.auctionId() == null ? command.auctionId() : action.auctionId(), action.balanceAfter(), action.settlementReference())
                    : UasRemoteCommandResult.failure(command.idempotencyKey(), UasRemoteResultCode.from(action.code()), "uas.remote.action_failed");
            return remember(command, result);
        }

        private static UasRemoteCommandResult remember(UasRemoteCommand command, UasRemoteCommandResult result) {
            COMMANDS.put(command.idempotencyKey(), result);
            return result;
        }

        private static UasRemoteAuctionSnapshot auction(AuctionItem item) {
            boolean hasReserve = item.getReservePrice().isPresent();
            return new UasRemoteAuctionSnapshot(
                    item.getAuctionId(), item.getPlayerId(), item.getDisplayTitle(), item.getDescription(),
                    item.getState().name(), item.getFormat().name(), item.getStartingBidPrice(), item.getCurrentPrice(),
                    item.getBuyoutPrice().orElse(BigDecimal.ZERO), item.getReservePrice().orElse(BigDecimal.ZERO),
                    hasReserve, !hasReserve, item.isReserveMet(),
                    (int) item.getBidRecords().stream().filter(record -> record != null && record.isAccepted()).count(),
                    item.getHighestBidderId(), toInstant(item.getCreatedAt()), toInstant(item.getUpdatedAt()),
                    toInstant(item.getDateOfEnd()), item.isBundle(), item.getTotalItemCount(),
                    item.getContents().stream().map(DefaultApi::item).toList(), item.isEscrowed() ? item.getEscrowSource() : "",
                    item.getHighestBidderId(), item.getState() == AuctionState.ENDED
            );
        }

        private static UasRemoteItemSnapshot item(net.minecraft.world.item.ItemStack stack) {
            return new UasRemoteItemSnapshot(stack.getItem().toString(), stack.getHoverName().getString(), stack.getCount());
        }

        private static List<UasRemoteDeliverySnapshot> deliveries() {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return List.of();
            return AuctionDeliverySavedData.get(server).getAllDeliveries().values().stream()
                    .flatMap(List::stream)
                    .map(entry -> new UasRemoteDeliverySnapshot(entry.deliveryId(), entry.playerId(), entry.auctionId(), entry.items().stream().map(DefaultApi::item).toList(), entry.createdAt().toInstant(ZoneOffset.UTC)))
                    .toList();
        }

        private static long revision(AuctionHouse house) {
            return house.getAuctionItems().values().stream().mapToLong(item -> item.getUpdatedAt() == null ? 0L : item.getUpdatedAt().toEpochSecond(ZoneOffset.UTC)).sum();
        }

        private static Instant toInstant(java.time.LocalDateTime value) {
            return value == null ? null : value.toInstant(ZoneOffset.UTC);
        }
    }
}
