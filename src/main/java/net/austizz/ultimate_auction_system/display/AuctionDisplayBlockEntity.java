package net.austizz.ultimate_auction_system.display;

import net.austizz.ultimate_auction_system.AuctionHouse;
import net.austizz.ultimate_auction_system.AuctionItem;
import net.austizz.ultimate_auction_system.AuctionState;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimate_auction_system.i18n.UasTranslations;
import net.austizz.ultimate_auction_system.registry.UasBlockEntities;
import net.austizz.ultimate_auction_system.registry.UasBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class AuctionDisplayBlockEntity extends BlockEntity {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 16;
    private static final int REFRESH_TICKS = 20;
    private static final int ROTATION_TICKS = 60;

    private UUID owner;
    private AuctionDisplayType displayType = AuctionDisplayType.HIGHEST_BID;
    private UUID manualAuction;
    private int sizeX = 1;
    private int sizeY = 1;
    private int sizeZ = 1;
    private UUID selectedAuction;
    private ItemStack displayedItem = ItemStack.EMPTY;
    private String displayedName = "";
    private String displayedPrice = "0";
    private String displayedEnd = "";
    private int displayedCount;
    private boolean displayedBundle;
    private float modelX;
    private float modelY;
    private float modelZ;
    private float modelPitch;
    private float modelYaw;
    private float modelRoll;
    private float modelScale = 1.0F;
    private boolean spinning;
    private int rotationIndex;
    private long nextRefresh;
    private long nextRotation;
    private UUID pendingRemovePlayer;
    private long pendingRemoveUntil;

    public AuctionDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(UasBlockEntities.AUCTION_DISPLAY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AuctionDisplayBlockEntity display) {
        long time = level.getGameTime();
        if (time < display.nextRefresh) {
            return;
        }
        display.nextRefresh = time + REFRESH_TICKS;
        display.refresh((net.minecraft.server.level.ServerLevel) level, time >= display.nextRotation);
    }

    private void refresh(net.minecraft.server.level.ServerLevel level, boolean rotate) {
        AuctionHouse house = UltimateAuctionSystem.auctionHouse;
        if (house == null) return;
        List<AuctionItem> active = house.getAuctionItems().values().stream()
                .filter(item -> item != null && item.getState() == AuctionState.ACTIVE && !item.isExpired())
                .toList();
        AuctionItem selected = select(active, level.getGameTime());
        if (selected == null) {
            clearDisplay();
        } else {
            List<ItemStack> contents = selected.getContents();
            if (rotate && contents.size() > 1) {
                rotationIndex = (rotationIndex + 1) % contents.size();
                nextRotation = level.getGameTime() + ROTATION_TICKS;
            }
            ItemStack item = contents.get(Math.min(rotationIndex, contents.size() - 1));
            selectedAuction = selected.getAuctionId();
            displayedItem = item.copy();
            displayedName = selected.getDisplayTitle();
            displayedPrice = selected.getHighestBid().stripTrailingZeros().toPlainString();
            displayedEnd = selected.getDateOfEnd().toString();
            displayedBundle = selected.isBundle();
            displayedCount = displayedBundle ? selected.getContentStackCount() : item.getCount();
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private AuctionItem select(List<AuctionItem> active, long gameTime) {
        if (active.isEmpty()) {
            return null;
        }
        if (displayType == AuctionDisplayType.MANUAL) {
            return active.stream().filter(item -> item.getAuctionId().equals(manualAuction)).findFirst().orElse(null);
        }
        if (displayType == AuctionDisplayType.RANDOM) {
            return active.get((int) Math.floorMod(gameTime / ROTATION_TICKS, active.size()));
        }
        Comparator<AuctionItem> comparator = switch (displayType) {
            case MOST_WATCHED -> Comparator.comparingInt((AuctionItem item) -> item.getNotificationSubscribers().size()).reversed()
                    .thenComparing(AuctionItem::getDateOfEnd);
            case ENDING_SOON -> Comparator.comparing(AuctionItem::getDateOfEnd);
            case RANDOM -> Comparator.comparing(AuctionItem::getAuctionId);
            case HIGHEST_BID, MANUAL -> Comparator.comparing(AuctionItem::getHighestBid).reversed()
                    .thenComparing(AuctionItem::getDateOfEnd);
        };
        return active.stream().sorted(comparator).findFirst().orElse(null);
    }

    private void clearDisplay() {
        selectedAuction = null;
        displayedItem = ItemStack.EMPTY;
        displayedName = "";
        displayedPrice = "0";
        displayedEnd = "";
        displayedCount = 0;
        displayedBundle = false;
    }

    public void configure(ServerPlayer player, AuctionDisplayType type, int x, int y, int z, UUID auctionId) {
        owner = player.getUUID();
        displayType = type == null ? AuctionDisplayType.HIGHEST_BID : type;
        sizeX = clamp(x); sizeY = clamp(y); sizeZ = clamp(z);
        manualAuction = auctionId;
        setChanged();
    }

    public void loadItemConfiguration(ServerPlayer player, CompoundTag tag) {
        configure(player, AuctionDisplayType.fromToken(tag.getString("Type")), tag.getInt("SizeX"), tag.getInt("SizeY"), tag.getInt("SizeZ"),
                tag.hasUUID("ManualAuction") ? tag.getUUID("ManualAuction") : null);
    }

    private static int clamp(int size) { return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size)); }
    public UUID owner() { return owner; }
    public AuctionDisplayType displayType() { return displayType; }
    public UUID manualAuction() { return manualAuction; }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public UUID selectedAuction() { return selectedAuction; }
    public ItemStack displayedItem() { return displayedItem; }
    public String displayedName() { return displayedName; }
    public String displayedPrice() { return displayedPrice; }
    public String displayedEnd() { return displayedEnd; }
    public int displayedCount() { return displayedCount; }
    public boolean displayedBundle() { return displayedBundle; }
    public float modelX() { return modelX; }
    public float modelY() { return modelY; }
    public float modelZ() { return modelZ; }
    public float modelPitch() { return modelPitch; }
    public float modelYaw() { return modelYaw; }
    public float modelRoll() { return modelRoll; }
    public float modelScale() { return modelScale; }
    public boolean spinning() { return spinning; }

    public boolean applyTransform(ServerPlayer player, float x, float y, float z,
                                  float pitch, float yaw, float roll, float scale, boolean spinning) {
        if (!canRemove(player)) return false;
        modelX = clamp(x, -2.0F, 2.0F);
        modelY = clamp(y, -2.0F, 2.0F);
        modelZ = clamp(z, -2.0F, 2.0F);
        modelPitch = normalizeAngle(pitch);
        modelYaw = normalizeAngle(yaw);
        modelRoll = normalizeAngle(roll);
        modelScale = clamp(scale, 0.1F, 4.0F);
        this.spinning = spinning;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    private static float clamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }

    private static float normalizeAngle(float value) {
        if (!Float.isFinite(value)) return 0.0F;
        return ((value % 360.0F) + 360.0F) % 360.0F;
    }

    public void openAuction(ServerPlayer player) {
        if (selectedAuction == null) {
            player.sendSystemMessage(UasTranslations.tr("This auction display has no active auction.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        net.austizz.ultimate_auction_system.network.UasPayloads.openAuctionHouse(player, selectedAuction);
    }

    public void removeOrWarn(ServerPlayer player) {
        if (!canRemove(player)) {
            player.sendSystemMessage(UasTranslations.tr("Only the owner or an operator can remove this auction display.").withStyle(ChatFormatting.RED));
            return;
        }
        long now = level == null ? 0 : level.getGameTime();
        if (player.getUUID().equals(pendingRemovePlayer) && now <= pendingRemoveUntil) {
            ItemStack returned = new ItemStack(UasBlocks.AUCTION_DISPLAY.get());
            CompoundTag configuration = new CompoundTag();
            configuration.putString("id", UltimateAuctionSystem.MODID + ":auction_display");
            configuration.putString("Type", displayType.name());
            configuration.putInt("SizeX", sizeX); configuration.putInt("SizeY", sizeY); configuration.putInt("SizeZ", sizeZ);
            configuration.putFloat("ModelX", modelX); configuration.putFloat("ModelY", modelY); configuration.putFloat("ModelZ", modelZ);
            configuration.putFloat("ModelPitch", modelPitch); configuration.putFloat("ModelYaw", modelYaw); configuration.putFloat("ModelRoll", modelRoll);
            configuration.putFloat("ModelScale", modelScale); configuration.putBoolean("Spinning", spinning);
            if (manualAuction != null) configuration.putUUID("ManualAuction", manualAuction);
            returned.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(configuration));
            if (!player.getInventory().add(returned)) player.drop(returned, false);
            level.destroyBlock(worldPosition, false);
            return;
        }
        pendingRemovePlayer = player.getUUID();
        pendingRemoveUntil = now + 100;
        player.sendSystemMessage(UasTranslations.tr("Warning: shift-right-click again within 5 seconds to remove this auction display.").withStyle(ChatFormatting.YELLOW));
    }

    public boolean canRemove(ServerPlayer player) { return player.hasPermissions(2) || player.getUUID().equals(owner); }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        displayType = AuctionDisplayType.fromToken(tag.getString("Type"));
        if (tag.hasUUID("ManualAuction")) manualAuction = tag.getUUID("ManualAuction");
        sizeX = clamp(tag.getInt("SizeX")); sizeY = clamp(tag.getInt("SizeY")); sizeZ = clamp(tag.getInt("SizeZ"));
        if (tag.hasUUID("SelectedAuction")) selectedAuction = tag.getUUID("SelectedAuction");
        displayedName = tag.getString("Name"); displayedPrice = tag.getString("Price"); displayedEnd = tag.getString("End");
        displayedCount = tag.getInt("Count");
        displayedBundle = tag.getBoolean("Bundle");
        modelX = tag.getFloat("ModelX"); modelY = tag.getFloat("ModelY"); modelZ = tag.getFloat("ModelZ");
        modelPitch = tag.getFloat("ModelPitch"); modelYaw = tag.getFloat("ModelYaw"); modelRoll = tag.getFloat("ModelRoll");
        modelScale = tag.contains("ModelScale") ? clamp(tag.getFloat("ModelScale"), 0.1F, 4.0F) : 1.0F;
        spinning = tag.getBoolean("Spinning");
        if (tag.contains("Item")) displayedItem = ItemStack.parseOptional(registries, tag.getCompound("Item"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("Type", displayType.name()); tag.putInt("SizeX", sizeX); tag.putInt("SizeY", sizeY); tag.putInt("SizeZ", sizeZ);
        if (manualAuction != null) tag.putUUID("ManualAuction", manualAuction);
        if (selectedAuction != null) tag.putUUID("SelectedAuction", selectedAuction);
        tag.putString("Name", displayedName); tag.putString("Price", displayedPrice); tag.putString("End", displayedEnd); tag.putInt("Count", displayedCount);
        tag.putBoolean("Bundle", displayedBundle);
        tag.putFloat("ModelX", modelX); tag.putFloat("ModelY", modelY); tag.putFloat("ModelZ", modelZ);
        tag.putFloat("ModelPitch", modelPitch); tag.putFloat("ModelYaw", modelYaw); tag.putFloat("ModelRoll", modelRoll);
        tag.putFloat("ModelScale", modelScale); tag.putBoolean("Spinning", spinning);
        if (!displayedItem.isEmpty()) tag.put("Item", displayedItem.save(registries));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
