package net.austizz.ultimate_auction_system.item;

import net.austizz.ultimate_auction_system.entity.AuctionTellerEntity;
import net.austizz.ultimate_auction_system.i18n.UasTranslations;
import net.austizz.ultimate_auction_system.registry.UasEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class AuctionTellerSpawnEggItem extends Item {
    public AuctionTellerSpawnEggItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 spawnCenter = Vec3.atBottomCenterOf(spawnPos);
        float yaw = Mth.wrapDegrees(player.getYRot() + 180.0F);

        AuctionTellerEntity teller = UasEntities.AUCTION_TELLER.get().create(serverLevel);
        if (teller == null) {
            return InteractionResult.FAIL;
        }

        teller.moveTo(spawnCenter.x, spawnCenter.y, spawnCenter.z, yaw, 0.0F);
        teller.initializeFromSpawn(player);
        teller.alignBodyTo(yaw);

        if (!serverLevel.noCollision(teller)) {
            teller.discard();
            player.sendSystemMessage(UasTranslations.tr("Not enough space to place an Auction Teller here.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        serverLevel.addFreshEntity(teller);
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        player.sendSystemMessage(UasTranslations.tr("Auction Teller placed.").withStyle(ChatFormatting.GREEN));
        return InteractionResult.CONSUME;
    }
}
