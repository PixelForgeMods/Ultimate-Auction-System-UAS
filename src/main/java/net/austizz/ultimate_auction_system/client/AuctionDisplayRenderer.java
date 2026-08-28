package net.austizz.ultimate_auction_system.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austizz.ultimate_auction_system.display.AuctionDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemDisplayContext;

public final class AuctionDisplayRenderer implements BlockEntityRenderer<AuctionDisplayBlockEntity> {
    private final Minecraft minecraft = Minecraft.getInstance();

    public AuctionDisplayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(AuctionDisplayBlockEntity display) {
        return true;
    }

    @Override
    public void render(AuctionDisplayBlockEntity display, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        AuctionDisplayClientEvents.registerDisplay(display);
        if (display.displayedItem().isEmpty()) return;
        int largestDimension = Math.max(display.sizeX(), Math.max(display.sizeY(), display.sizeZ()));
        float scale = Math.min(2.8F, 0.55F + largestDimension * 0.28F);
        pose.pushPose();
        // Keep the item centred in the display volume, including non-cubic displays.
        pose.translate(display.sizeX() * 0.5D + display.modelX(), display.sizeY() * 0.5D + display.modelY(), display.sizeZ() * 0.5D + display.modelZ());
        pose.mulPose(Axis.XP.rotationDegrees(display.modelPitch()));
        pose.mulPose(Axis.YP.rotationDegrees(display.modelYaw()
                + (display.spinning() && display.getLevel() != null ? (display.getLevel().getGameTime() + partialTick) * 3.0F : 0.0F)));
        pose.mulPose(Axis.ZP.rotationDegrees(display.modelRoll()));
        pose.scale(display.modelScale(), display.modelScale(), display.modelScale());
        pose.scale(scale, scale, scale);
        minecraft.getItemRenderer().renderStatic(display.displayedItem(), ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY, pose, buffers, display.getLevel(), 0);
        pose.popPose();

    }
}
