package net.austizz.ultimate_auction_system.client;

import net.austizz.ultimate_auction_system.i18n.UasTranslations;
import net.austizz.ultimate_auction_system.network.DisplayEditTransformPayload;
import net.austizz.ultimate_auction_system.network.DisplayEditSpectatorPayload;
import net.austizz.ultimate_auction_system.network.DisplayEditorPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

public final class AuctionDisplayEditorScreen extends Screen {
    private final DisplayEditorPayload initial;
    private final ItemStack previewItem;
    private float x, y, z, pitch, yaw, roll, scale;
    private boolean spinning;
    private AuctionEditBox[] fields;
    private boolean orbiting;
    private boolean gizmoDragging;
    private int gizmoAxis;
    private float orbitYaw = 180.0F;
    private float orbitPitch = 12.0F;
    private Entity previousCameraEntity;
    private Entity editorCameraEntity;
    private boolean editorCameraActive;
    private ToolMode mode = ToolMode.MOVE;
    private Axis axis = Axis.X;
    private boolean draggingAxis;
    private double lastDragX;
    private double lastDragY;
    private AuctionButton moveButton;
    private AuctionButton rotateButton;
    private AuctionButton scaleButton;
    private AuctionButton axisXButton;
    private AuctionButton axisYButton;
    private AuctionButton axisZButton;

    public AuctionDisplayEditorScreen(DisplayEditorPayload payload, ItemStack previewItem) {
        super(UasTranslations.tr("Display model editor"));
        this.initial = payload;
        this.previewItem = previewItem.copy();
        x = payload.x(); y = payload.y(); z = payload.z(); pitch = payload.pitch(); yaw = payload.yaw();
        roll = payload.roll(); scale = payload.scale(); spinning = payload.spinning();
    }

    @Override protected void init() {
        clearWidgets();
        int panelLeft = (width - Math.min(780, Math.max(610, width - 24))) / 2;
        int panelTop = (height - Math.min(430, Math.max(350, height - 24))) / 2;
        int left = panelLeft + 14;
        int top = panelTop + 42;
        int fieldX = panelLeft + Math.min(560, Math.max(0, Math.min(780, Math.max(610, width - 24)) - 102));
        fields = new AuctionEditBox[7];
        Component[] labels = {Component.literal("X"), Component.literal("Y"), Component.literal("Z"),
                UasTranslations.tr("Pitch"), UasTranslations.tr("Yaw"), UasTranslations.tr("Roll"), UasTranslations.tr("Scale")};
        float[] values = {x, y, z, pitch, yaw, roll, scale};
        for (int i = 0; i < fields.length; i++) {
            int row = i < 3 ? i : i < 6 ? i + 1 : i + 2;
            AuctionEditBox box = new AuctionEditBox(font, fieldX, panelTop + 84 + row * 22, 88, 18, labels[i]);
            box.setValue(format(values[i]));
            box.setResponder(ignored -> updateFromFields());
            fields[i] = box;
            addRenderableWidget(box);
        }
        moveButton = addRenderableWidget(new AuctionButton(left, top, 86, 22, UasTranslations.tr("Move (W)"), AuctionButton.Style.GRAY, b -> setMode(ToolMode.MOVE)));
        rotateButton = addRenderableWidget(new AuctionButton(left + 92, top, 86, 22, UasTranslations.tr("Rotate (E)"), AuctionButton.Style.GRAY, b -> setMode(ToolMode.ROTATE)));
        scaleButton = addRenderableWidget(new AuctionButton(left + 184, top, 86, 22, UasTranslations.tr("Scale (R)"), AuctionButton.Style.GRAY, b -> setMode(ToolMode.SCALE)));
        axisXButton = addRenderableWidget(new AuctionButton(left + 300, top, 42, 22, Component.literal("X"), AuctionButton.Style.RED, b -> setAxis(Axis.X)));
        axisYButton = addRenderableWidget(new AuctionButton(left + 348, top, 42, 22, Component.literal("Y"), AuctionButton.Style.GREEN, b -> setAxis(Axis.Y)));
        axisZButton = addRenderableWidget(new AuctionButton(left + 396, top, 42, 22, Component.literal("Z"), AuctionButton.Style.GRAY, b -> setAxis(Axis.Z)));
        int actionX = panelLeft + Math.min(780, Math.max(610, width - 24)) - 172;
        addRenderableWidget(new AuctionButton(actionX, panelTop + 300, 80, 22, UasTranslations.tr("Apply"), AuctionButton.Style.GREEN, b -> updateFromFields()));
        addRenderableWidget(new AuctionButton(actionX + 88, panelTop + 300, 84, 22, UasTranslations.tr("Reset model"), AuctionButton.Style.GRAY, b -> reset()));
        addRenderableWidget(new AuctionButton(actionX, panelTop + 326, 172, 22, spinningLabel(), AuctionButton.Style.GRAY, button -> { spinning = !spinning; button.setMessage(spinningLabel()); send(); }));
        addRenderableWidget(new AuctionButton(width / 2 - 70, height - 32, 140, 22, UasTranslations.tr("Done"), AuctionButton.Style.GREEN, button -> onClose()));
        updateControlState();
        activateEditorCamera();
        updateEditorCamera();
        PacketDistributor.sendToServer(new DisplayEditSpectatorPayload(true));
    }

    private Component spinningLabel() { return UasTranslations.tr(spinning ? "Spinning: ON" : "Spinning: OFF"); }

    private void updateFromFields() {
        if (fields == null) return;
        float[] values = new float[7];
        for (int i = 0; i < fields.length; i++) {
            try { values[i] = Float.parseFloat(fields[i].getValue()); } catch (NumberFormatException ignored) { return; }
        }
        x = values[0]; y = values[1]; z = values[2]; pitch = values[3]; yaw = values[4]; roll = values[5]; scale = values[6];
        send();
    }

    private void setMode(ToolMode next) {
        mode = next;
        updateControlState();
    }

    private void setAxis(Axis next) {
        axis = next;
        updateControlState();
    }

    private void updateControlState() {
        if (moveButton != null) moveButton.active = mode != ToolMode.MOVE;
        if (rotateButton != null) rotateButton.active = mode != ToolMode.ROTATE;
        if (scaleButton != null) scaleButton.active = mode != ToolMode.SCALE;
        if (axisXButton != null) axisXButton.active = axis != Axis.X;
        if (axisYButton != null) axisYButton.active = axis != Axis.Y;
        if (axisZButton != null) axisZButton.active = axis != Axis.Z;
    }

    private void reset() {
        x = y = z = pitch = yaw = roll = 0.0F; scale = 1.0F; spinning = false;
        syncFields();
        updateControlState();
        send();
    }

    private void send() {
        PacketDistributor.sendToServer(new DisplayEditTransformPayload(initial.pos(), x, y, z, pitch, yaw, roll, scale, spinning));
    }

    private static String format(float value) { return String.format(Locale.ROOT, "%.2f", value); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Math.min(780, Math.max(610, width - 24));
        int panelHeight = Math.min(430, Math.max(350, height - 24));
        int panelLeft = (width - panelWidth) / 2, panelTop = (height - panelHeight) / 2;
        int panelRight = panelLeft + panelWidth, panelBottom = panelTop + panelHeight;
        graphics.fill(panelLeft + 10, panelTop + 10, panelRight - 10, panelTop + 44, 0xA8565656);
        graphics.drawString(font, UasTranslations.tr("DISPLAY MODEL EDITOR"), panelLeft + 18, panelTop + 18, 0xFFFFAA00, false);
        graphics.drawString(font, UasTranslations.tr("North-facing preview"), panelRight - 150, panelTop + 18, 0xFFBDBDBD, false);
        renderPreview(graphics, width / 2, height / 2 + 20);
        int rightText = panelRight - 102;
        graphics.drawString(font, UasTranslations.tr("Transform"), rightText, panelTop + 54, 0xFFFFAA00, false);
        graphics.drawString(font, UasTranslations.tr("Position"), rightText, panelTop + 76, 0xFF8FFFB4, false);
        graphics.drawString(font, UasTranslations.tr("Rotation"), rightText, panelTop + 164, 0xFFFFD966, false);
        graphics.drawString(font, UasTranslations.tr("Scale"), rightText, panelTop + 230, 0xFF8FB8E5, false);
        graphics.drawString(font, UasTranslations.tr("Middle-drag rotates camera around display"), width / 2 - 170, panelBottom - 24, 0xFFBDBDBD, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPreview(GuiGraphics graphics, int cx, int cy) {
        drawCircle(graphics, cx, cy, 78, 0x448FB8E5);
        drawLine(graphics, cx, cy, cx + 72, cy, 0xFFED7B7B);
        drawLine(graphics, cx, cy, cx, cy - 72, 0xFF88E2A2);
        drawLine(graphics, cx, cy, cx - 52, cy + 52, 0xFF7DB2FF);
        graphics.fill(cx - 4, cy - 4, cx + 4, cy + 4, 0xFFFFFFFF);
        drawHandle(graphics, cx + 72, cy, 0xFFED7B7B);
        drawHandle(graphics, cx, cy - 72, 0xFF88E2A2);
        drawHandle(graphics, cx - 52, cy + 52, 0xFF7DB2FF);
        graphics.drawString(font, Component.literal("Y"), cx + 5, cy - 82, 0xFFFF5555, false);
        graphics.drawString(font, Component.literal("X"), cx + 74, cy - 6, 0xFF55FF55, false);
        graphics.drawString(font, Component.literal("Z"), cx + 58, cy + 28, 0xFF5599FF, false);
    }

    private static void drawHandle(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x - 5, y - 5, x + 5, y + 5, 0xFFF2F4F7);
        graphics.fill(x - 3, y - 3, x + 3, y + 3, color);
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx - dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 2, y0 + 2, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = err * 2;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    private static void drawCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int i = 0; i < 360; i += 2) {
            double angle = Math.toRadians(i);
            int x = cx + (int) (Math.cos(angle) * radius);
            int y = cy + (int) (Math.sin(angle) * radius);
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 2) { orbiting = true; return true; }
        if (button == 0) {
            int cx = width / 2;
            int cy = height / 2 + 20;
            int[][] handles = {{cx + 72, cy}, {cx, cy - 72}, {cx - 52, cy + 52}};
            for (int i = 0; i < handles.length; i++) {
                if (Math.abs(mouseX - handles[i][0]) <= 10 && Math.abs(mouseY - handles[i][1]) <= 10) {
                    gizmoDragging = true;
                    gizmoAxis = i;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 2) { orbiting = false; return true; }
        if (button == 0 && gizmoDragging) { gizmoDragging = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (orbiting && button == 2) {
            orbitYaw += (float) dragX;
            orbitPitch = Mth.clamp(orbitPitch - (float) dragY * 0.5F, -55.0F, 55.0F);
            return true;
        }
        if (gizmoDragging && button == 0) {
            float fine = hasShiftDown() ? 0.25F : 1.0F;
            if (mode == ToolMode.MOVE) {
                float delta = (float) (dragX - dragY) * 0.0025F * fine;
                if (gizmoAxis == 0) x += (float) dragX * 0.0025F * fine;
                else if (gizmoAxis == 1) y -= (float) dragY * 0.0025F * fine;
                else z += delta;
            } else if (mode == ToolMode.ROTATE) {
                float delta = (float) (dragX - dragY) * 0.72F * fine;
                if (gizmoAxis == 0) pitch += delta;
                else if (gizmoAxis == 1) yaw += delta;
                else roll += delta;
            } else {
                float delta = (float) (dragX - dragY) * 0.004F * fine;
                scale = Math.max(0.1F, scale + delta);
            }
            syncFields();
            send();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void syncFields() {
        if (fields == null) return;
        float[] values = {x, y, z, pitch, yaw, roll, scale};
        for (int i = 0; i < fields.length; i++) fields[i].setValue(format(values[i]));
    }

    @Override public void tick() {
        super.tick();
        updateEditorCamera();
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the real display and the north-facing world view visible behind the editor.
    }

    private void activateEditorCamera() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        previousCameraEntity = minecraft.getCameraEntity();
        editorCameraEntity = EntityType.MARKER.create(minecraft.level);
        if (editorCameraEntity != null) {
            editorCameraEntity.setInvisible(true);
            editorCameraEntity.noPhysics = true;
            minecraft.setCameraEntity(editorCameraEntity);
            editorCameraActive = true;
        }
    }

    private void updateEditorCamera() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        if (!editorCameraActive || editorCameraEntity == null || minecraft.level == null) return;
        BlockPos pos = initial.pos();
        Vec3 target = Vec3.atCenterOf(pos).add(x, 0.5D + y, z);
        double yaw = Math.toRadians(orbitYaw);
        double pitchAngle = Math.toRadians(orbitPitch);
        double distance = 3.8D;
        Vec3 camera = target.add(-Math.sin(yaw) * Math.cos(pitchAngle) * distance,
                Math.sin(pitchAngle) * distance,
                Math.cos(yaw) * Math.cos(pitchAngle) * distance);
        Vec3 look = target.subtract(camera);
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        editorCameraEntity.setPos(camera);
        editorCameraEntity.setYRot((float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0D));
        editorCameraEntity.setXRot((float) -Math.toDegrees(Math.atan2(look.y, horizontal)));
        editorCameraEntity.setOldPosAndRot();
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 87) { setMode(ToolMode.MOVE); return true; }
        if (keyCode == 69) { setMode(ToolMode.ROTATE); return true; }
        if (keyCode == 82) { setMode(ToolMode.SCALE); return true; }
        if (keyCode == 88) { setAxis(Axis.X); return true; }
        if (keyCode == 89) { setAxis(Axis.Y); return true; }
        if (keyCode == 90) { setAxis(Axis.Z); return true; }
        if (keyCode == 257 || keyCode == 335) { updateFromFields(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void removed() {
        if (editorCameraActive) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (previousCameraEntity != null && !previousCameraEntity.isRemoved()) minecraft.setCameraEntity(previousCameraEntity);
            editorCameraActive = false;
            editorCameraEntity = null;
            previousCameraEntity = null;
        }
        PacketDistributor.sendToServer(new DisplayEditSpectatorPayload(false));
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }

    private enum ToolMode { MOVE, ROTATE, SCALE }
    private enum Axis { X, Y, Z }
}
