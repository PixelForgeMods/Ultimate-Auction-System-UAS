package net.austizz.ultimate_auction_system.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimate_auction_system.entity.AuctionTellerEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class AuctionTellerRenderer extends HumanoidMobRenderer<AuctionTellerEntity, PlayerModel<AuctionTellerEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UltimateAuctionSystem.MODID, "textures/entity/auction_teller.png");

    public AuctionTellerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(AuctionTellerEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(AuctionTellerEntity entity,
                       float entityYaw,
                       float partialTicks,
                       PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected void scale(AuctionTellerEntity entity, PoseStack poseStack, float partialTickTime) {
        super.scale(entity, poseStack, partialTickTime);
        poseStack.scale(0.98F, 0.98F, 0.98F);
    }
}
