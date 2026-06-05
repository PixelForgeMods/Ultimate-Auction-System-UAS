package net.austizz.ultimate_auction_system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionDeliverySavedData extends SavedData {
    private static final String DATA_NAME = "ultimate_auction_system_deliveries";
    private static final String DELIVERIES_TAG = "deliveries";

    private final ConcurrentHashMap<UUID, List<AuctionDeliveryEntry>> deliveries;

    public AuctionDeliverySavedData() {
        this(new ConcurrentHashMap<>());
    }

    private AuctionDeliverySavedData(ConcurrentHashMap<UUID, List<AuctionDeliveryEntry>> deliveries) {
        this.deliveries = deliveries;
    }

    public static SavedData.Factory<AuctionDeliverySavedData> factory() {
        return new SavedData.Factory<>(
                AuctionDeliverySavedData::new,
                AuctionDeliverySavedData::load,
                null
        );
    }

    public static AuctionDeliverySavedData get(MinecraftServer server) {
        if (server == null) {
            throw new IllegalStateException("Minecraft server is unavailable");
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld level is unavailable");
        }
        return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    public synchronized List<AuctionDeliveryEntry> getDeliveries(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return deliveries.getOrDefault(playerId, List.of()).stream()
                .map(AuctionDeliveryEntry::copy)
                .toList();
    }

    public synchronized void addDelivery(UUID playerId, UUID auctionId, ItemStack stack, String reason) {
        if (playerId == null || stack == null || stack.isEmpty()) {
            return;
        }
        deliveries.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(new AuctionDeliveryEntry(
                UUID.randomUUID(),
                playerId,
                auctionId,
                stack.copy(),
                reason == null ? "" : reason,
                LocalDateTime.now()
        ));
        setDirty();
    }

    public synchronized Optional<AuctionDeliveryEntry> removeDelivery(UUID playerId, UUID deliveryId) {
        if (playerId == null || deliveryId == null) {
            return Optional.empty();
        }
        List<AuctionDeliveryEntry> playerDeliveries = deliveries.get(playerId);
        if (playerDeliveries == null || playerDeliveries.isEmpty()) {
            return Optional.empty();
        }
        for (int index = 0; index < playerDeliveries.size(); index++) {
            AuctionDeliveryEntry entry = playerDeliveries.get(index);
            if (deliveryId.equals(entry.deliveryId())) {
                playerDeliveries.remove(index);
                if (playerDeliveries.isEmpty()) {
                    deliveries.remove(playerId);
                }
                setDirty();
                return Optional.of(entry.copy());
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag deliveryTags = new ListTag();
        for (List<AuctionDeliveryEntry> playerDeliveries : deliveries.values()) {
            for (AuctionDeliveryEntry entry : playerDeliveries) {
                CompoundTag deliveryTag = new CompoundTag();
                deliveryTag.putUUID("deliveryId", entry.deliveryId());
                deliveryTag.putUUID("playerId", entry.playerId());
                if (entry.auctionId() != null) {
                    deliveryTag.putUUID("auctionId", entry.auctionId());
                }
                deliveryTag.putString("reason", entry.reason() == null ? "" : entry.reason());
                deliveryTag.putString("createdAt", entry.createdAt().toString());
                deliveryTag.put("item", saveItemStack(entry.item(), registries));
                deliveryTags.add(deliveryTag);
            }
        }
        tag.put(DELIVERIES_TAG, deliveryTags);
        return tag;
    }

    private static AuctionDeliverySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ConcurrentHashMap<UUID, List<AuctionDeliveryEntry>> loaded = new ConcurrentHashMap<>();
        if (tag == null) {
            return new AuctionDeliverySavedData(loaded);
        }
        ListTag deliveryTags = tag.getList(DELIVERIES_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : deliveryTags) {
            if (!(raw instanceof CompoundTag deliveryTag)) {
                continue;
            }
            try {
                UUID deliveryId = deliveryTag.getUUID("deliveryId");
                UUID playerId = deliveryTag.getUUID("playerId");
                UUID auctionId = deliveryTag.contains("auctionId") ? deliveryTag.getUUID("auctionId") : null;
                ItemStack stack = ItemStack.parseOptional(registries, deliveryTag.getCompound("item"));
                if (stack.isEmpty()) {
                    continue;
                }
                LocalDateTime createdAt = LocalDateTime.parse(deliveryTag.getString("createdAt"));
                loaded.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(new AuctionDeliveryEntry(
                        deliveryId,
                        playerId,
                        auctionId,
                        stack,
                        deliveryTag.getString("reason"),
                        createdAt
                ));
            } catch (DateTimeParseException | IllegalArgumentException exception) {
                UltimateAuctionSystem.LOGGER.warn("[UAS] Skipped invalid delivery entry during load: {}", exception.getMessage());
            }
        }
        return new AuctionDeliverySavedData(loaded);
    }

    private static CompoundTag saveItemStack(ItemStack stack, HolderLookup.Provider registries) {
        return UasItemStackNbt.saveOptional(stack, registries);
    }
}
