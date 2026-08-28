package net.austizz.ultimate_auction_system.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class AuctionButton extends AbstractButton {
    public enum Style {
        GRAY,
        GREEN,
        RED,
        DARK,
        TAB_ACTIVE,
        CLAIMED
    }

    private final Consumer<AuctionButton> onPress;
    private Style style;

    public AuctionButton(int x, int y, int width, int height, Component message, Style style, Consumer<AuctionButton> onPress) {
        super(x, y, width, height, message);
        this.style = style == null ? Style.GRAY : style;
        this.onPress = onPress;
    }

    public AuctionButton setButtonStyle(Style style) {
        this.style = style == null ? Style.GRAY : style;
        return this;
    }

    @Override
    public void onPress() {
        if (onPress != null) {
            onPress.accept(this);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + width;
        int y2 = y1 + height;
        boolean hovered = isHoveredOrFocused();

        int border = active ? 0xFF000000 : 0xFF111111;
        int highlight = active && hovered ? 0xFFFFFFFF : 0xFFE6E6E6;
        int shadow = 0xFF202020;
        int fill = fillColor(hovered);

        graphics.fill(x1, y1, x2, y2, border);
        graphics.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
        graphics.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, highlight);
        graphics.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, shadow);

        Font font = Minecraft.getInstance().font;
        String label = trimToWidth(font, getMessage().getString(), width - 10);
        int color = active ? textColor() : disabledTextColor();
        Component renderedLabel = style == Style.GREEN || style == Style.CLAIMED
                ? Component.literal(label).withStyle(ChatFormatting.BOLD)
                : Component.literal(label);
        graphics.drawCenteredString(font, renderedLabel, x1 + width / 2, y1 + (height - 8) / 2, color);
    }

    private int fillColor(boolean hovered) {
        if (!active) {
            if (style == Style.CLAIMED) {
                return 0xFF314135;
            }
            return 0xFF2F2F2F;
        }
        return switch (style) {
            case GREEN -> hovered ? 0xFF55FF55 : 0xFF3EFF47;
            case RED -> hovered ? 0xFFFF6666 : 0xFFFF4545;
            case DARK -> hovered ? 0xFF4B4B4B : 0xFF2A2A2A;
            case TAB_ACTIVE -> hovered ? 0xFF777777 : 0xFF606060;
            case CLAIMED -> hovered ? 0xFF516755 : 0xFF455A49;
            case GRAY -> hovered ? 0xFF747474 : 0xFF5B5B5B;
        };
    }

    private int textColor() {
        return switch (style) {
            case GREEN -> 0xFFFFFFFF;
            case RED -> 0xFFFFFFFF;
            case CLAIMED -> 0xFFE8FFE8;
            default -> 0xFFFFFFFF;
        };
    }

    private int disabledTextColor() {
        return style == Style.CLAIMED ? 0xFF9DDBA2 : 0xFF8E8E8E;
    }

    private static String trimToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isBlank() || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) + font.width(ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
