package net.austizz.ultimate_auction_system.entity;

import net.austizz.ultimate_auction_system.Config;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimate_auction_system.api.UasPermissionAction;
import net.austizz.ultimate_auction_system.api.UasPermissions;
import net.austizz.ultimate_auction_system.i18n.UasTranslations;
import net.austizz.ultimate_auction_system.network.UasPayloads;
import net.austizz.ultimate_auction_system.registry.UasBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public class AuctionTellerEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(AuctionTellerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> FIXED_YAW =
            SynchedEntityData.defineId(AuctionTellerEntity.class, EntityDataSerializers.FLOAT);

    private UUID pendingRemovePlayer;
    private long pendingRemoveUntilTick;

    public AuctionTellerEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
        this.setNoAi(true);
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(FIXED_YAW, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoAi(true);
        this.setInvulnerable(true);
        applyBodyRotation(this.entityData.get(FIXED_YAW));
        if (this.getHealth() < this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    @Override
    public void checkDespawn() {
        // Auction tellers are server-placed access points and should never despawn naturally.
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
        // No-op to keep the teller stationary.
    }

    @Override
    public void push(double x, double y, double z) {
        // No-op to keep the teller stationary.
    }

    @Override
    public void travel(Vec3 travelVector) {
        // No-op to keep the teller stationary.
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (serverPlayer.isShiftKeyDown()) {
            return handleRemovalClick(serverPlayer);
        }
        openAuctionHouse(serverPlayer);
        return InteractionResult.CONSUME;
    }

    private void openAuctionHouse(ServerPlayer player) {
        if (!Config.auctionTerminalEnabled) {
            player.sendSystemMessage(UasTranslations.tr("Auction tellers are disabled on this server.").withStyle(ChatFormatting.RED));
            return;
        }
        if (UltimateAuctionSystem.auctionHouse == null) {
            player.sendSystemMessage(UasTranslations.tr("Auction house is not initialized.").withStyle(ChatFormatting.RED));
            return;
        }
        var permission = UasPermissions.check(player, UasPermissionAction.TERMINAL);
        if (!permission.success()) {
            player.sendSystemMessage(UasTranslations.tr("You do not have permission to use auction tellers.").withStyle(ChatFormatting.RED));
            return;
        }
        UasPayloads.openAuctionHouse(player);
    }

    private InteractionResult handleRemovalClick(ServerPlayer player) {
        if (!canRemove(player)) {
            player.sendSystemMessage(UasTranslations.tr("Only the owner or an operator can remove this auction teller.").withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }

        long now = this.level().getGameTime();
        if (player.getUUID().equals(pendingRemovePlayer) && now <= pendingRemoveUntilTick) {
            ItemStack egg = new ItemStack(UasBlocks.AUCTION_TELLER_SPAWN_EGG.get());
            if (!player.getInventory().add(egg)) {
                player.drop(egg, false);
            }
            this.discard();
            player.sendSystemMessage(UasTranslations.tr("Auction Teller removed and spawn egg returned.").withStyle(ChatFormatting.GREEN));
            return InteractionResult.CONSUME;
        }

        pendingRemovePlayer = player.getUUID();
        pendingRemoveUntilTick = now + 100L;
        player.sendSystemMessage(UasTranslations.tr("Warning: shift-right-click again within 5 seconds to remove this auction teller.").withStyle(ChatFormatting.YELLOW));
        return InteractionResult.CONSUME;
    }

    private boolean canRemove(ServerPlayer player) {
        UUID owner = getOwnerUUID();
        return (owner != null && owner.equals(player.getUUID())) || player.hasPermissions(2);
    }

    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    public void setOwnerUUID(UUID owner) {
        this.entityData.set(OWNER_UUID, Optional.ofNullable(owner));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putFloat("FixedYaw", this.entityData.get(FIXED_YAW));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            setOwnerUUID(tag.getUUID("Owner"));
        }
        if (tag.contains("FixedYaw")) {
            this.entityData.set(FIXED_YAW, Mth.wrapDegrees(tag.getFloat("FixedYaw")));
        } else {
            this.entityData.set(FIXED_YAW, Mth.wrapDegrees(this.getYRot()));
        }
        this.setNoAi(true);
        this.setInvulnerable(true);
        applyBodyRotation(this.entityData.get(FIXED_YAW));
        updateDisplayName();
    }

    public void initializeFromSpawn(ServerPlayer ownerPlayer) {
        this.setOwnerUUID(ownerPlayer.getUUID());
        updateDisplayName();
        this.setNoAi(true);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    public void alignBodyTo(float yaw) {
        float normalized = Mth.wrapDegrees(yaw);
        this.entityData.set(FIXED_YAW, normalized);
        applyBodyRotation(normalized);
    }

    private void applyBodyRotation(float yaw) {
        float normalized = Mth.wrapDegrees(yaw);
        this.setYRot(normalized);
        this.yRotO = normalized;
        this.setYBodyRot(normalized);
        this.yBodyRotO = normalized;
        this.setYHeadRot(normalized);
        this.yHeadRotO = normalized;
        this.setXRot(0.0F);
        this.xRotO = 0.0F;
    }

    private void updateDisplayName() {
        this.setCustomName(UasTranslations.tr("Auction Teller").withStyle(ChatFormatting.GOLD));
        this.setCustomNameVisible(true);
    }

    public static AuctionTellerEntity spawn(ServerLevel level, Vec3 position, ServerPlayer owner, float yaw) {
        AuctionTellerEntity entity = new AuctionTellerEntity(
                net.austizz.ultimate_auction_system.registry.UasEntities.AUCTION_TELLER.get(),
                level
        );
        entity.moveTo(position.x, position.y, position.z, yaw, 0.0F);
        entity.initializeFromSpawn(owner);
        entity.alignBodyTo(yaw);
        return entity;
    }
}
