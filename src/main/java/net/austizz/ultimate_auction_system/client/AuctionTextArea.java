package net.austizz.ultimate_auction_system.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AuctionTextArea extends AbstractWidget {
    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 11;

    private record WrappedLine(String text, int start, int end) {
    }

    private final Font font;
    private String value = "";
    private Component hint = Component.empty();
    private Consumer<String> responder = ignored -> {
    };
    private int cursor;
    private int scrollLine;
    private int maxLength = 500;

    public AuctionTextArea(Font font, int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
        this.font = font;
    }

    public void setHint(Component hint) {
        this.hint = hint == null ? Component.empty() : hint;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        setValue(value);
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = responder == null ? ignored -> {
        } : responder;
    }

    public void setValue(String value) {
        this.value = clamp(value == null ? "" : value);
        this.cursor = Math.min(cursor, this.value.length());
        ensureCursorVisible();
    }

    public String getValue() {
        return value;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + width;
        int y2 = y1 + height;
        int border = isFocused() ? 0xFFFFFFFF : 0xFFBDBDBD;

        graphics.fill(x1, y1, x2, y2, border);
        graphics.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0xFF000000);

        int textX = x1 + PADDING;
        int textY = y1 + PADDING;
        int innerW = innerWidth();
        int visibleLines = visibleLineCount();
        List<WrappedLine> lines = wrappedLines();
        scrollLine = clampInt(scrollLine, 0, Math.max(0, lines.size() - visibleLines));

        if (value.isBlank() && !hint.getString().isBlank() && !isFocused()) {
            graphics.drawString(font, Component.literal(trimToWidth(hint.getString(), innerW)), textX, textY, 0xFF7F7F7F, false);
            return;
        }

        int endLine = Math.min(lines.size(), scrollLine + visibleLines);
        for (int i = scrollLine; i < endLine; i++) {
            WrappedLine line = lines.get(i);
            graphics.drawString(font, Component.literal(line.text()), textX, textY + (i - scrollLine) * LINE_HEIGHT, 0xFFFFFFFF, false);
        }

        if (isFocused() && (Util.getMillis() / 300L) % 2L == 0L) {
            CursorPosition position = cursorPosition(lines);
            if (position.line() >= scrollLine && position.line() < scrollLine + visibleLines) {
                int cursorX = textX + position.x();
                int cursorY = textY + (position.line() - scrollLine) * LINE_HEIGHT - 1;
                graphics.fill(cursorX, cursorY, cursorX + 1, cursorY + 10, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !active || button != 0 || !isMouseOver(mouseX, mouseY)) {
            setFocused(false);
            return false;
        }
        setFocused(true);
        cursor = cursorAt(mouseX, mouseY);
        ensureCursorVisible();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible || !active || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int maxScroll = Math.max(0, wrappedLines().size() - visibleLineCount());
        if (maxScroll <= 0) {
            return false;
        }
        scrollLine = clampInt(scrollLine - (int) Math.signum(scrollY), 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused() || !active) {
            return false;
        }
        if (Screen.hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_A) {
                cursor = value.length();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_C) {
                Minecraft.getInstance().keyboardHandler.setClipboard(value);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_X) {
                Minecraft.getInstance().keyboardHandler.setClipboard(value);
                updateValue("");
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V) {
                insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) {
                    updateValue(value.substring(0, cursor - 1) + value.substring(cursor));
                    cursor--;
                    ensureCursorVisible();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < value.length()) {
                    updateValue(value.substring(0, cursor) + value.substring(cursor + 1));
                    ensureCursorVisible();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = Math.max(0, cursor - 1);
                ensureCursorVisible();
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = Math.min(value.length(), cursor + 1);
                ensureCursorVisible();
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                ensureCursorVisible();
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = value.length();
                ensureCursorVisible();
                yield true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                insert("\n");
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isFocused() || !active || Character.isISOControl(codePoint)) {
            return false;
        }
        insert(String.valueOf(codePoint));
        return true;
    }

    private void insert(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        int room = Math.max(0, maxLength - value.length());
        if (room <= 0) {
            return;
        }
        String inserted = normalized.length() > room ? normalized.substring(0, room) : normalized;
        updateValue(value.substring(0, cursor) + inserted + value.substring(cursor));
        cursor += inserted.length();
        ensureCursorVisible();
    }

    private void updateValue(String value) {
        this.value = clamp(value);
        cursor = Math.min(cursor, this.value.length());
        responder.accept(this.value);
    }

    private String clamp(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private int cursorAt(double mouseX, double mouseY) {
        List<WrappedLine> lines = wrappedLines();
        int lineIndex = clampInt(scrollLine + (int) ((mouseY - getY() - PADDING) / LINE_HEIGHT), 0, Math.max(0, lines.size() - 1));
        WrappedLine line = lines.get(lineIndex);
        int relativeX = Math.max(0, (int) mouseX - getX() - PADDING);
        String text = line.text();
        for (int i = 0; i <= text.length(); i++) {
            if (font.width(text.substring(0, i)) >= relativeX) {
                return Math.min(value.length(), line.start() + i);
            }
        }
        return Math.min(value.length(), line.end());
    }

    private void ensureCursorVisible() {
        List<WrappedLine> lines = wrappedLines();
        CursorPosition position = cursorPosition(lines);
        int visibleLines = visibleLineCount();
        if (position.line() < scrollLine) {
            scrollLine = position.line();
        } else if (position.line() >= scrollLine + visibleLines) {
            scrollLine = position.line() - visibleLines + 1;
        }
        scrollLine = clampInt(scrollLine, 0, Math.max(0, lines.size() - visibleLines));
    }

    private CursorPosition cursorPosition(List<WrappedLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            WrappedLine line = lines.get(i);
            if (cursor >= line.start() && cursor <= line.end()) {
                int relative = Math.max(0, Math.min(cursor - line.start(), line.text().length()));
                return new CursorPosition(i, font.width(line.text().substring(0, relative)));
            }
        }
        WrappedLine last = lines.get(lines.size() - 1);
        return new CursorPosition(lines.size() - 1, font.width(last.text()));
    }

    private List<WrappedLine> wrappedLines() {
        List<WrappedLine> lines = new ArrayList<>();
        int innerW = innerWidth();
        if (value.isEmpty()) {
            lines.add(new WrappedLine("", 0, 0));
            return lines;
        }

        int paragraphStart = 0;
        while (paragraphStart <= value.length()) {
            int newline = value.indexOf('\n', paragraphStart);
            int paragraphEnd = newline >= 0 ? newline : value.length();
            wrapParagraph(lines, paragraphStart, paragraphEnd, innerW);
            if (newline < 0) {
                break;
            }
            paragraphStart = newline + 1;
            if (paragraphStart == value.length()) {
                lines.add(new WrappedLine("", paragraphStart, paragraphStart));
                break;
            }
        }
        return lines.isEmpty() ? List.of(new WrappedLine("", 0, 0)) : lines;
    }

    private void wrapParagraph(List<WrappedLine> lines, int start, int end, int maxWidth) {
        if (start == end) {
            lines.add(new WrappedLine("", start, end));
            return;
        }
        int lineStart = start;
        while (lineStart < end) {
            String remaining = value.substring(lineStart, end);
            String fitting = font.plainSubstrByWidth(remaining, Math.max(1, maxWidth));
            int length = fitting.length();
            if (length <= 0) {
                length = 1;
            } else if (lineStart + length < end) {
                int lastSpace = fitting.lastIndexOf(' ');
                if (lastSpace > 0) {
                    length = lastSpace;
                }
            }
            int lineEnd = Math.min(end, lineStart + length);
            String text = value.substring(lineStart, lineEnd).stripLeading();
            int leadingTrim = value.substring(lineStart, lineEnd).length() - text.length();
            lines.add(new WrappedLine(text, lineStart + leadingTrim, lineEnd));
            lineStart = lineEnd;
            while (lineStart < end && value.charAt(lineStart) == ' ') {
                lineStart++;
            }
        }
    }

    private int innerWidth() {
        return Math.max(1, width - PADDING * 2);
    }

    private int visibleLineCount() {
        return Math.max(1, (height - PADDING * 2) / LINE_HEIGHT);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (text == null || text.isBlank() || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CursorPosition(int line, int x) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
