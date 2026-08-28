package net.austizz.ultimate_auction_system.client;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimate_auction_system.display.AuctionDisplayBlockEntity;
import net.austizz.ultimate_auction_system.i18n.UasTranslations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

@EventBusSubscriber(modid = UltimateAuctionSystem.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class AuctionDisplayClientEvents {
    private static boolean editMode;
    private static boolean shiftDown;
    private static int lastShiftTick = -100;
    private static int clientTick;
    private static final Map<net.minecraft.core.BlockPos, AuctionDisplayBlockEntity> visibleDisplays = new HashMap<>();
    private AuctionDisplayClientEvents() {
    }

    public static void setEditMode(boolean enabled) { editMode = enabled; }

    public static void registerDisplay(AuctionDisplayBlockEntity display) {
        if (display != null && display.getBlockPos() != null) visibleDisplays.put(display.getBlockPos(), display);
    }

    @SubscribeEvent
    public static void handleInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || event.getKeyMapping() != minecraft.options.keyUse) return;
        AuctionDisplayBlockEntity display = hoveredDisplay(minecraft);
        if (display != null) {
            if (editMode) {
                PacketDistributor.sendToServer(new net.austizz.ultimate_auction_system.network.DisplayEditSelectPayload(display.getBlockPos()));
            } else {
                PacketDistributor.sendToServer(new net.austizz.ultimate_auction_system.network.DisplayOpenPayload(
                        display.getBlockPos(), minecraft.options.keyShift.isDown()));
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void handleShiftExit(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        clientTick++;
        boolean down = minecraft.options.keyShift.isDown();
        if (down && !shiftDown && editMode) {
            if (clientTick - lastShiftTick <= 20) {
                editMode = false;
                if (minecraft.player != null) minecraft.player.sendSystemMessage(UasTranslations.tr("Display edit mode disabled.").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            lastShiftTick = clientTick;
        }
        shiftDown = down;
    }

    @SubscribeEvent
    public static void renderHover(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.hitResult == null || minecraft.screen != null) return;
        if (editMode) {
            GuiGraphics banner = event.getGuiGraphics();
            int bannerWidth = 330;
            int bannerX = (minecraft.getWindow().getGuiScaledWidth() - bannerWidth) / 2;
            banner.fill(bannerX, 12, bannerX + bannerWidth, 42, 0xEE1E1E1E);
            banner.fill(bannerX + 2, 14, bannerX + bannerWidth - 2, 40, 0xFF565656);
            banner.drawString(minecraft.font, UasTranslations.tr("DISPLAY EDIT MODE"), bannerX + 12, 18, 0xFFFFAA00, false);
            banner.drawString(minecraft.font, UasTranslations.tr("Right-click a display • Shift twice to exit"), bannerX + 12, 29, 0xFFE0E0E0, false);
        }
        AuctionDisplayBlockEntity display = hoveredDisplay(minecraft);
        if (display == null || display.selectedAuction() == null) return;
        GuiGraphics graphics = event.getGuiGraphics();
        List<Component> itemDetails = new ArrayList<>();
        List<Component> rows = new ArrayList<>();
        rows.add(UasTranslations.tr("Items: {0}", display.displayedCount()));
        rows.add(UasTranslations.tr("Current highest bid: {0}", display.displayedPrice()));
        rows.add(UasTranslations.tr("Ends: {0}", display.displayedEnd().replace('T', ' ')));
        if (display.displayedBundle()) {
            if (minecraft.options.keyShift.isDown()) {
                Item.TooltipContext context = Item.TooltipContext.of(minecraft.level);
                itemDetails.addAll(display.displayedItem().getTooltipLines(context, minecraft.player, TooltipFlag.Default.NORMAL));
            } else {
                itemDetails.add(UasTranslations.tr("Hold Shift for item details"));
            }
        }
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        // GUI coordinates shrink as the Minecraft UI scale increases, so keep this card proportional
        // to the available GUI width instead of using a fixed width that can consume half the screen.
        int width = Math.min(330, Math.max(150, screenWidth * 28 / 100));
        // The frame ends after the auction-detail rows. The open-auction hint is intentionally rendered below it.
        int height = 76 + rows.size() * 14 + 62 + itemDetails.size() * 10;
        int x = 16;
        int y = Math.max(12, (minecraft.getWindow().getGuiScaledHeight() - height) / 2);
        // Match the auction menu's layered frame, dark cards, gold headings and colored metrics.
        graphics.fill(x, y, x + width, y + height, 0xF01E1E1E);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF6B6B6B);
        graphics.fill(x + 5, y + 5, x + width - 5, y + height - 5, 0xFF3E3E3E);
        graphics.fill(x + 9, y + 9, x + width - 9, y + 33, 0xFF565656);
        graphics.drawString(minecraft.font, UasTranslations.tr("AUCTION DISPLAY"), x + 16, y + 14, 0xFFFFAA00, false);
        String title = minecraft.font.plainSubstrByWidth(display.displayedName(), width - 120);
        graphics.drawString(minecraft.font, Component.literal(title), x + width - 16 - minecraft.font.width(title), y + 14, 0xFFFFFFFF, false);
        graphics.fill(x + 9, y + 34, x + width - 9, y + 35, 0xFFFFAA00);

        int cursor = y + 44;
        graphics.drawString(minecraft.font, UasTranslations.tr("CURRENT ITEM"), x + 16, cursor, 0xFF55FF55, false);
        cursor += 14;
        graphics.fill(x + 16, cursor - 2, x + 46, cursor + 26, 0xFF191919);
        graphics.renderItem(display.displayedItem(), x + 23, cursor + 4);
        String itemName = minecraft.font.plainSubstrByWidth(display.displayedItem().getHoverName().getString(), width - 78);
        graphics.drawString(minecraft.font, Component.literal(itemName), x + 56, cursor + 2, 0xFFFFFFFF, false);
        graphics.drawString(minecraft.font, UasTranslations.tr("Quantity: {0}", display.displayedItem().getCount()), x + 56, cursor + 15, 0xFFBDBDBD, false);
        cursor += 32;
        for (Component detail : itemDetails) {
            String detailText = minecraft.font.plainSubstrByWidth(detail.getString(), width - 78);
            graphics.drawString(minecraft.font, Component.literal(detailText), x + 56, cursor, 0xFFBDBDBD, false);
            cursor += 10;
        }
        cursor += 4;
        graphics.drawString(minecraft.font, UasTranslations.tr("AUCTION DETAILS"), x + 16, cursor, 0xFFFFAA00, false);
        cursor += 14;
        for (int i = 0; i < rows.size(); i++) {
            int rowY = cursor + i * 14;
            graphics.fill(x + 14, rowY - 3, x + width - 14, rowY + 11, 0xFF191919);
            String rowText = minecraft.font.plainSubstrByWidth(rows.get(i).getString(), width - 44);
            graphics.drawString(minecraft.font, Component.literal(rowText), x + 22, rowY, i == 1 ? 0xFFFFD966 : i == 2 ? 0xFFA5D6A7 : 0xFFE0E0E0, false);
        }
        cursor += rows.size() * 14 + 4;
        graphics.fill(x + 9, cursor, x + width - 9, cursor + 1, 0xFF6B6B6B);
        graphics.drawString(minecraft.font, UasTranslations.tr("OPEN AUCTION"), x + 16, cursor + 10, 0xFFFFAA00, false);
        graphics.drawString(minecraft.font, UasTranslations.tr("Right-click to open auction"), x + 16, cursor + 25, 0xFFE0E0E0, false);
    }

    @SubscribeEvent
    public static void renderDisplayOutline(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        renderDisplayHolograms(event, minecraft);
        AuctionDisplayBlockEntity display = hoveredDisplay(minecraft);
        if (display == null) return;
        Vec3 camera = event.getCamera().getPosition();
        AABB box = displayBounds(display);
        LevelRenderer.renderLineBox(event.getPoseStack(), minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines()),
                box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z,
                box.maxX - camera.x, box.maxY - camera.y, box.maxZ - camera.z,
                0.0F, 0.0F, 0.0F, 1.0F);
    }

    private static void renderDisplayHolograms(RenderLevelStageEvent event, Minecraft minecraft) {
        if (minecraft.options.hideGui || minecraft.font == null) return;
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        for (AuctionDisplayBlockEntity display : visibleDisplays.values()) {
            if (display == null || display.isRemoved() || display.displayedItem().isEmpty()) continue;
            BlockPos pos = display.getBlockPos();
            Vec3 anchor = new Vec3(pos.getX() + display.sizeX() * 0.5D,
                    pos.getY() + display.sizeY() + 0.35D,
                    pos.getZ() + display.sizeZ() * 0.5D);
            if (camera.distanceToSqr(anchor) > 64.0D * 64.0D) continue;
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);
            pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-0.018F, -0.018F, 0.018F);
            Component title = Component.literal(display.displayedBundle() ? display.displayedName()
                    : (display.displayedCount() > 1 ? display.displayedCount() + "x " + display.displayedName() : display.displayedName()));
            drawHologramLine(minecraft, buffers, pose, title, 0xFFFFFF, 0);
            drawHologramLine(minecraft, buffers, pose, UasTranslations.tr("Highest bid: {0}", display.displayedPrice()), 0x70E0A0, 12);
            drawHologramLine(minecraft, buffers, pose, UasTranslations.tr("Ends: {0}", display.displayedEnd().replace('T', ' ')), 0xFFD36A, 24);
            pose.popPose();
        }
    }

    private static void drawHologramLine(Minecraft minecraft, MultiBufferSource buffers, PoseStack pose,
                                         Component text, int color, int y) {
        minecraft.font.drawInBatch(text, -minecraft.font.width(text) / 2.0F, y, color, true,
                pose.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
    }

    @SubscribeEvent
    public static void suppressNativeDisplayOutline(RenderHighlightEvent.Block event) {
        if (event.getTarget() != null && Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(event.getTarget().getBlockPos()) instanceof AuctionDisplayBlockEntity) {
            event.setCanceled(true);
        }
    }

    private static AuctionDisplayBlockEntity hoveredDisplay(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.gameRenderer == null) return null;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 from = camera.getPosition();
        var look = camera.getLookVector();
        Vec3 to = from.add(look.x() * 64.0D, look.y() * 64.0D, look.z() * 64.0D);
        AuctionDisplayBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        visibleDisplays.entrySet().removeIf(entry -> entry.getValue() == null
                || entry.getValue().isRemoved() || entry.getValue().getLevel() != minecraft.level);
        for (AuctionDisplayBlockEntity display : visibleDisplays.values()) {
            Optional<Vec3> hit = displayBounds(display).clip(from, to);
            if (hit.isPresent()) {
                double distance = from.distanceToSqr(hit.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = display;
                }
            }
        }
        if (best != null) return best;
        if (minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && minecraft.level.getBlockEntity(hit.getBlockPos()) instanceof AuctionDisplayBlockEntity display) {
            registerDisplay(display);
            return display;
        }
        return null;
    }

    private static AABB displayBounds(AuctionDisplayBlockEntity display) {
        net.minecraft.core.BlockPos pos = display.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + display.sizeX(), pos.getY() + display.sizeY(), pos.getZ() + display.sizeZ());
    }
}
