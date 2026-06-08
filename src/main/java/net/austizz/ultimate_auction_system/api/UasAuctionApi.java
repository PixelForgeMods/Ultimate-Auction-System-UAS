package net.austizz.ultimate_auction_system.api;

import net.austizz.ultimate_auction_system.AuctionActionResult;
import net.austizz.ultimate_auction_system.AuctionDeliverySavedData;
import net.austizz.ultimate_auction_system.AuctionHouse;
import net.austizz.ultimate_auction_system.AuctionHouseSnapshot;
import net.austizz.ultimate_auction_system.AuctionItem;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class UasAuctionApi {
    public static final String API_VERSION = "1";

    private final AuctionHouse house;

    public UasAuctionApi(AuctionHouse house) {
        this.house = house;
    }

    public static UasAuctionApi get() {
        return new UasAuctionApi(UltimateAuctionSystem.auctionHouse);
    }

    public String apiVersion() {
        return API_VERSION;
    }

    public List<UasAuctionSnapshot> queryActive(UasAuctionQuery query) {
        if (house == null) {
            return List.of();
        }
        UasAuctionQuery safeQuery = query == null ? UasAuctionQuery.defaults() : query;
        AuctionHouseSnapshot snapshot = house.buildSnapshot(null, null, safeQuery.toUiQuery(), "", true, false);
        return snapshot.browseListings().stream()
                .limit(safeQuery.limit())
                .map(summary -> house.getAuctionItem(summary.auctionId()))
                .filter(item -> item != null)
                .map(UasAuctionSnapshot::fromItem)
                .toList();
    }

    public Optional<UasAuctionSnapshot> getAuctionSnapshot(UUID auctionId) {
        if (house == null || auctionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(UasAuctionSnapshot.fromItem(house.getAuctionItem(auctionId)));
    }

    public UasAuctionResult inspectStatus(UUID auctionId) {
        if (house == null) {
            return UasAuctionResult.fail(UasAuctionResultCode.STORAGE_UNAVAILABLE, "Auction house is not loaded.", auctionId);
        }
        if (auctionId == null) {
            return UasAuctionResult.fail(UasAuctionResultCode.MISSING_AUCTION, "Auction ID is required.", null);
        }
        Optional<UasAuctionSnapshot> snapshot = getAuctionSnapshot(auctionId);
        return snapshot
                .map(value -> UasAuctionResult.ok("Auction status loaded.", value.auctionId(), value))
                .orElseGet(() -> UasAuctionResult.fail(UasAuctionResultCode.MISSING_AUCTION, "Auction not found.", auctionId));
    }

    public UasAuctionResult createListing(ServerPlayer seller, UasCreateAuctionRequest request) {
        if (house == null) {
            return UasAuctionResult.fail(UasAuctionResultCode.STORAGE_UNAVAILABLE, "Auction house is not loaded.", null);
        }
        if (seller == null) {
            return UasAuctionResult.fail(UasAuctionResultCode.PERMISSION_DENIED, "Only players can create auctions.", null);
        }
        if (request == null) {
            return UasAuctionResult.fail(UasAuctionResultCode.VALIDATION_FAILED, "Create auction request is required.", null);
        }

        AuctionActionResult prepared = house.prepareAuctionFromInventorySlots(
                seller,
                request.inventorySlots(),
                request.title(),
                request.startingBid(),
                request.buyoutPrice(),
                request.reservePrice(),
                request.endsAt(),
                request.description(),
                null
        );
        if (!prepared.success()) {
            return UasAuctionResult.fromAction(prepared, null);
        }

        AuctionActionResult confirmed = house.confirmPendingAuction(seller);
        UasAuctionSnapshot created = getAuctionSnapshot(confirmed.auctionId()).orElse(null);
        return UasAuctionResult.fromAction(confirmed, created);
    }

    public UasAuctionResult cancelListing(ServerPlayer seller, UUID auctionId) {
        if (house == null) {
            return UasAuctionResult.fail(UasAuctionResultCode.STORAGE_UNAVAILABLE, "Auction house is not loaded.", auctionId);
        }
        if (seller == null) {
            return UasAuctionResult.fail(UasAuctionResultCode.PERMISSION_DENIED, "Only players can cancel auctions.", auctionId);
        }
        AuctionItem item = house.getAuctionItem(auctionId);
        if (item == null) {
            return UasAuctionResult.fail(UasAuctionResultCode.MISSING_AUCTION, "Auction not found.", auctionId);
        }
        try {
            AuctionActionResult result = house.cancelOwnAuction(seller, auctionId, AuctionDeliverySavedData.get(seller.getServer()));
            return UasAuctionResult.fromAction(result.withAuctionId(auctionId), getAuctionSnapshot(auctionId).orElse(null));
        } catch (RuntimeException exception) {
            return UasAuctionResult.fail(UasAuctionResultCode.STORAGE_UNAVAILABLE, "Auction delivery storage is unavailable: " + exception.getMessage(), auctionId);
        }
    }
}
