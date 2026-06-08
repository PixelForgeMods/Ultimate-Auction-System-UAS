package net.austizz.ultimate_auction_system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AuctionRecoveryEntry {
    private final UUID recoveryId;
    private final UUID auctionId;
    private final UUID sellerId;
    private final String sellerName;
    private final UUID adminId;
    private final String adminName;
    private final String reason;
    private final LocalDateTime recoveredAt;
    private final List<ItemStack> contents;
    private UUID releasedBy;
    private String releasedByName;
    private String releaseReason;
    private LocalDateTime releasedAt;

    private AuctionRecoveryEntry(UUID recoveryId,
                                 UUID auctionId,
                                 UUID sellerId,
                                 String sellerName,
                                 UUID adminId,
                                 String adminName,
                                 String reason,
                                 LocalDateTime recoveredAt,
                                 List<ItemStack> contents,
                                 UUID releasedBy,
                                 String releasedByName,
                                 String releaseReason,
                                 LocalDateTime releasedAt) {
        this.recoveryId = recoveryId == null ? UUID.randomUUID() : recoveryId;
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.sellerName = blank(sellerName, fallbackName(sellerId));
        this.adminId = adminId;
        this.adminName = blank(adminName, "Unknown");
        this.reason = blank(reason, "No reason provided");
        this.recoveredAt = recoveredAt == null ? LocalDateTime.now() : recoveredAt;
        this.contents = sanitizeContents(contents);
        this.releasedBy = releasedBy;
        this.releasedByName = blank(releasedByName, "");
        this.releaseReason = blank(releaseReason, "");
        this.releasedAt = releasedAt;
    }

    public static AuctionRecoveryEntry create(UUID auctionId,
                                              UUID sellerId,
                                              String sellerName,
                                              UUID adminId,
                                              String adminName,
                                              String reason,
                                              List<ItemStack> contents) {
        return new AuctionRecoveryEntry(
                UUID.randomUUID(),
                auctionId,
                sellerId,
                sellerName,
                adminId,
                adminName,
                reason,
                LocalDateTime.now(),
                contents,
                null,
                "",
                "",
                null
        );
    }

    public UUID recoveryId() {
        return recoveryId;
    }

    public UUID auctionId() {
        return auctionId;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public String sellerName() {
        return sellerName;
    }

    public UUID adminId() {
        return adminId;
    }

    public String adminName() {
        return adminName;
    }

    public String reason() {
        return reason;
    }

    public LocalDateTime recoveredAt() {
        return recoveredAt;
    }

    public List<ItemStack> contents() {
        return contents.stream().map(ItemStack::copy).toList();
    }

    public Optional<UUID> releasedBy() {
        return Optional.ofNullable(releasedBy);
    }

    public String releasedByName() {
        return releasedByName;
    }

    public String releaseReason() {
        return releaseReason;
    }

    public Optional<LocalDateTime> releasedAt() {
        return Optional.ofNullable(releasedAt);
    }

    public boolean active() {
        return releasedAt == null;
    }

    public String itemName() {
        if (contents.isEmpty()) {
            return "Empty";
        }
        if (contents.size() == 1) {
            return contents.getFirst().getHoverName().getString();
        }
        return AuctionItem.generatedBundleTitle(contents);
    }

    public int totalItemCount() {
        return contents.stream().mapToInt(ItemStack::getCount).sum();
    }

    public boolean release(UUID adminId, String adminName, String reason) {
        if (!active()) {
            return false;
        }
        this.releasedBy = adminId;
        this.releasedByName = blank(adminName, "Unknown");
        this.releaseReason = blank(reason, "Admin recovery release");
        this.releasedAt = LocalDateTime.now();
        return true;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("recoveryId", recoveryId);
        if (auctionId != null) {
            tag.putUUID("auctionId", auctionId);
        }
        if (sellerId != null) {
            tag.putUUID("sellerId", sellerId);
        }
        tag.putString("sellerName", sellerName);
        if (adminId != null) {
            tag.putUUID("adminId", adminId);
        }
        tag.putString("adminName", adminName);
        tag.putString("reason", reason);
        tag.putString("recoveredAt", recoveredAt.toString());
        if (releasedBy != null) {
            tag.putUUID("releasedBy", releasedBy);
        }
        tag.putString("releasedByName", releasedByName);
        tag.putString("releaseReason", releaseReason);
        if (releasedAt != null) {
            tag.putString("releasedAt", releasedAt.toString());
        }
        ListTag contentTags = new ListTag();
        for (ItemStack stack : contents) {
            if (stack != null && !stack.isEmpty()) {
                CompoundTag contentTag = new CompoundTag();
                contentTag.put("item", UasItemStackNbt.saveOptional(stack, registries));
                contentTags.add(contentTag);
            }
        }
        tag.put("contents", contentTags);
        return tag;
    }

    public static Optional<AuctionRecoveryEntry> load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null || !tag.contains("recoveryId") || !tag.contains("recoveredAt")) {
            return Optional.empty();
        }
        try {
            List<ItemStack> contents = loadContents(tag, registries);
            return Optional.of(new AuctionRecoveryEntry(
                    tag.getUUID("recoveryId"),
                    tag.contains("auctionId") ? tag.getUUID("auctionId") : null,
                    tag.contains("sellerId") ? tag.getUUID("sellerId") : null,
                    tag.getString("sellerName"),
                    tag.contains("adminId") ? tag.getUUID("adminId") : null,
                    tag.getString("adminName"),
                    tag.getString("reason"),
                    LocalDateTime.parse(tag.getString("recoveredAt")),
                    contents,
                    tag.contains("releasedBy") ? tag.getUUID("releasedBy") : null,
                    tag.getString("releasedByName"),
                    tag.getString("releaseReason"),
                    tag.contains("releasedAt") ? LocalDateTime.parse(tag.getString("releasedAt")) : null
            ));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Skipped invalid admin recovery entry: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private static List<ItemStack> loadContents(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag contentTags = tag.getList("contents", Tag.TAG_COMPOUND);
        java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>();
        for (Tag raw : contentTags) {
            if (raw instanceof CompoundTag contentTag) {
                ItemStack stack = ItemStack.parseOptional(registries, contentTag.getCompound("item"));
                if (!stack.isEmpty()) {
                    stacks.add(stack.copy());
                }
            }
        }
        return sanitizeContents(stacks);
    }

    private static List<ItemStack> sanitizeContents(List<ItemStack> rawContents) {
        if (rawContents == null || rawContents.isEmpty()) {
            return List.of();
        }
        return rawContents.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .limit(AuctionItem.MAX_BUNDLE_CONTENTS)
                .map(ItemStack::copy)
                .toList();
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String fallbackName(UUID playerId) {
        return playerId == null ? "Unknown" : playerId.toString().substring(0, 8);
    }
}
