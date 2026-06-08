package net.austizz.ultimate_auction_system;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PendingAuctionListing(
        UUID playerId,
        List<Integer> slots,
        List<ItemStack> itemSnapshots,
        String title,
        BigDecimal startingBid,
        BigDecimal buyoutPrice,
        LocalDateTime endsAt,
        String description,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        String sourceLabel,
        UUID sellerAccountId
) {
    public static final int MAIN_HAND_SLOT = -1;

    public PendingAuctionListing(UUID playerId,
                                 int slot,
                                 ItemStack itemSnapshot,
                                 BigDecimal startingBid,
                                 BigDecimal buyoutPrice,
                                 LocalDateTime endsAt,
                                 String description,
                                 LocalDateTime createdAt,
                                 LocalDateTime expiresAt,
                                 String sourceLabel) {
        this(
                playerId,
                List.of(slot),
                List.of(itemSnapshot == null ? ItemStack.EMPTY : itemSnapshot.copy()),
                "",
                startingBid,
                buyoutPrice,
                endsAt,
                description,
                createdAt,
                expiresAt,
                sourceLabel,
                null
        );
    }

    public PendingAuctionListing {
        slots = sanitizeSlots(slots);
        itemSnapshots = sanitizeStacks(itemSnapshots);
        if (itemSnapshots.size() > AuctionItem.MAX_BUNDLE_CONTENTS) {
            itemSnapshots = itemSnapshots.subList(0, AuctionItem.MAX_BUNDLE_CONTENTS);
        }
        if (slots.size() > itemSnapshots.size()) {
            slots = slots.subList(0, itemSnapshots.size());
        }
        title = title == null ? "" : title.trim();
        startingBid = startingBid == null ? BigDecimal.ZERO : startingBid;
        buyoutPrice = buyoutPrice == null ? BigDecimal.ZERO : buyoutPrice;
        description = description == null ? "" : description;
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        expiresAt = expiresAt == null ? createdAt.plusSeconds(Config.pendingListingConfirmationSeconds) : expiresAt;
        sourceLabel = sourceLabel == null ? "" : sourceLabel;
    }

    public int slot() {
        return slots.isEmpty() ? MAIN_HAND_SLOT : slots.getFirst();
    }

    public ItemStack itemSnapshot() {
        return itemSnapshots.isEmpty() ? ItemStack.EMPTY : itemSnapshots.getFirst().copy();
    }

    public boolean isBundle() {
        return itemSnapshots.size() > 1;
    }

    public boolean isMainHand() {
        return slots.size() == 1 && slot() == MAIN_HAND_SLOT;
    }

    public String displayTitle() {
        if (!isBundle()) {
            ItemStack first = itemSnapshot();
            return first.isEmpty() ? "" : first.getHoverName().getString();
        }
        return title.isBlank() ? AuctionItem.generatedBundleTitle(itemSnapshots) : title;
    }

    public int totalItemCount() {
        return itemSnapshots.stream().mapToInt(ItemStack::getCount).sum();
    }

    public boolean isExpired(LocalDateTime now) {
        return now == null || !expiresAt.isAfter(now);
    }

    public ItemStack currentStack(ServerPlayer player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        int firstSlot = slot();
        if (firstSlot == MAIN_HAND_SLOT) {
            return player.getMainHandItem();
        }
        if (firstSlot < 0 || firstSlot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(firstSlot);
    }

    public List<ItemStack> currentStacks(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        ArrayList<ItemStack> current = new ArrayList<>();
        for (int slot : slots) {
            ItemStack stack;
            if (slot == MAIN_HAND_SLOT) {
                stack = player.getMainHandItem();
            } else if (slot >= 0 && slot < player.getInventory().getContainerSize()) {
                stack = player.getInventory().getItem(slot);
            } else {
                stack = ItemStack.EMPTY;
            }
            current.add(stack.copy());
        }
        return current;
    }

    public boolean stillMatches(ServerPlayer player) {
        if (player == null || slots.size() != itemSnapshots.size() || itemSnapshots.isEmpty()) {
            return false;
        }
        for (int i = 0; i < itemSnapshots.size(); i++) {
            ItemStack snapshot = itemSnapshots.get(i);
            ItemStack current = stackAt(player, slots.get(i));
            if (snapshot.isEmpty()
                    || current.isEmpty()
                    || current.getCount() < snapshot.getCount()
                    || !ItemStack.isSameItemSameComponents(current, snapshot)) {
                return false;
            }
        }
        return true;
    }

    public AuctionListingPreview toPreview() {
        ItemStack primary = itemSnapshot();
        return new AuctionListingPreview(
                primary,
                displayTitle(),
                totalItemCount(),
                startingBid,
                buyoutPrice,
                Config.calculateListingFee(startingBid),
                endsAt,
                expiresAt,
                description,
                sourceLabel,
                itemSnapshots,
                isBundle()
        );
    }

    private static ItemStack stackAt(ServerPlayer player, int slot) {
        if (slot == MAIN_HAND_SLOT) {
            return player.getMainHandItem();
        }
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(slot);
    }

    private static List<Integer> sanitizeSlots(List<Integer> rawSlots) {
        if (rawSlots == null || rawSlots.isEmpty()) {
            return List.of();
        }
        return rawSlots.stream()
                .filter(slot -> slot != null)
                .limit(AuctionItem.MAX_BUNDLE_CONTENTS)
                .toList();
    }

    private static List<ItemStack> sanitizeStacks(List<ItemStack> rawStacks) {
        if (rawStacks == null || rawStacks.isEmpty()) {
            return List.of();
        }
        return rawStacks.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .limit(AuctionItem.MAX_BUNDLE_CONTENTS)
                .map(ItemStack::copy)
                .toList();
    }
}
