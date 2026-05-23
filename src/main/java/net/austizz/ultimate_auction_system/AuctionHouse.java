package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.UasBankingResult;
import net.austizz.ultimate_auction_system.banking.UasBankingService;
import net.austizz.ultimate_auction_system.banking.UbsBankingService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionHouse {

    private final ConcurrentHashMap<UUID, AuctionItem> AuctionItems;
    private final UasBankingService bankingService;
    private volatile AuctionStorageHealth storageHealth = AuctionStorageHealth.inMemoryOnly();

    public AuctionHouse() {
        this(new UbsBankingService());
    }

    public AuctionHouse(UasBankingService bankingService) {
        this.AuctionItems = new ConcurrentHashMap<>();
        this.bankingService = bankingService;
    }

    public void addAuctionItem(AuctionItem item) {
        ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(item.getPlayerId());
        if (Config.requireUbsForListing && !bankingService.playerHasAvailablePrimaryAccount(item.getPlayerId())) {
            if (player != null) {
                String errorMessage = String.format("Error: Player %s does not have an available primary banking account. Please set up a UBS primary account or contact the server administrator for assistance.", player.getName().getString());
                player.sendSystemMessage(Component.literal(errorMessage).withStyle(ChatFormatting.RED));
                return;
            }
            return;
        }

        this.AuctionItems.put(item.getAuctionId(), item);
        if (player != null) {
            Component message = Component.literal("")
                    .append(Component.literal("⚖ ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("Auction: ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(Component.literal("Successfully listed ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(item.getItem().getCount() + "x " + item.getItem().getHoverName().getString()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("$" + item.getStartingBidPrice()).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(". -> Type /ah to view your listing!").withStyle(ChatFormatting.DARK_GRAY));
            player.sendSystemMessage(message, false);
        }
    }

    public void removeAuctionItem(AuctionItem item) {
        this.AuctionItems.remove(item.getAuctionId());
    }

    public AuctionItem getAuctionItem(UUID id) {
        if  (this.AuctionItems.containsKey(id)) {
            return this.AuctionItems.get(id);
        }
        return null;
    }

    public ConcurrentHashMap<UUID, AuctionItem> getAuctionItems() {
        return this.AuctionItems;
    }

    public AuctionStorageHealth getStorageHealth() {
        return storageHealth;
    }

    public void markStorageSaved(String message) {
        this.storageHealth = AuctionStorageHealth.saved(message);
    }

    public void markStorageFailed(String message) {
        this.storageHealth = AuctionStorageHealth.failed(message);
    }

    public void payoutAuctionItem(UUID id) {
        AuctionItem item = getAuctionItem(id);
        if (item == null || !item.isExpired()) {
            return;
        }

        UUID winningBidderId = item.getHighestBidderId();
        if (winningBidderId == null) {
            UltimateAuctionSystem.LOGGER.info("Auction {} expired without bids; no UBS payout was created.", id);
            return;
        }

        if (!bankingService.isAvailable()) {
            UltimateAuctionSystem.LOGGER.warn("UBS is not available; cannot settle auction {}.", id);
            return;
        }

        UUID sellerAccountId = bankingService.getPrimaryAccountId(item.getPlayerId()).orElse(null);
        if (sellerAccountId == null) {
            UltimateAuctionSystem.LOGGER.warn("Seller {} has no primary UBS account; cannot settle auction {}.", item.getPlayerId(), id);
            return;
        }

        UasBankingResult result = bankingService.transferFromPrimary(
                winningBidderId,
                sellerAccountId,
                item.getHighestBid(),
                "UAS_AUCTION_PAYOUT:" + id
        );

        if (!result.success()) {
            UltimateAuctionSystem.LOGGER.warn("UBS auction settlement failed for {}: {}", id, result.reason());
            return;
        }

        removeAuctionItem(item);
    }
}
