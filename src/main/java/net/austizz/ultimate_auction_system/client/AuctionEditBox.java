package net.austizz.ultimate_auction_system.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class AuctionEditBox extends EditBox {
    private static final int PADDING = 5;

    public AuctionEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        setBordered(false);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + width;
        int y2 = y1 + height;
        int border = isFocused() ? 0xFFFFFFFF : 0xFFBDBDBD;

        graphics.fill(x1, y1, x2, y2, border);
        graphics.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0xFF000000);

        int originalX = getX();
        int originalY = getY();
        int originalWidth = width;
        setX(originalX + PADDING);
        setY(originalY + Math.max(0, (height - 8) / 2));
        width = Math.max(1, originalWidth - PADDING - 2);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        setX(originalX);
        setY(originalY);
        width = originalWidth;
    }
}
