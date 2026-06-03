package net.machiavelli.minecolonytax.gui.book;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Static rendering utilities and color constants for the book-style GUI.
 * Colors are derived from the parchment prototype palette.
 */
public final class BookRenderHelper {

    // Parchment-themed color palette
    public static final int INK         = 0xFF2C1E0E;
    public static final int INK_FAINT   = 0xFF8B7355;
    public static final int INK_GHOST   = 0xFFB5A48A;
    public static final int GOLD        = 0xFFC8A430;
    public static final int GOLD_DARK   = 0xFF8A6F1A;
    public static final int GREEN        = 0xFF2A6E2A;
    public static final int GREEN_BRIGHT = 0xFF4CAF50;
    public static final int DANGER      = 0xFFA03030;
    public static final int BLUE        = 0xFF1A4A7A;
    public static final int PARCHMENT_BG = 0x10000000;
    public static final int CARD_BORDER  = 0xFFD4C4A8;
    public static final int GOLD_CARD_BG = 0x18C8A430;
    public static final int WHITE       = 0xFFFFFFFF;
    public static final int ORANGE      = 0xFFFF9800;
    public static final int YELLOW      = 0xFFFFEB3B;
    public static final int GRAY        = 0xFF9E9E9E;

    private BookRenderHelper() {}

    /**
     * Draws a card with border and optional gold highlight.
     */
    public static void drawCard(GuiGraphics g, int x, int y, int w, int h, boolean gold) {
        int border = gold ? GOLD : CARD_BORDER;
        int bg = gold ? GOLD_CARD_BG : PARCHMENT_BG;
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
    }

    /**
     * Draws a small badge (colored border + text).
     */
    public static void drawBadge(GuiGraphics g, Font font, String text, int x, int y, int color) {
        int tw = font.width(text);
        g.fill(x - 1, y - 1, x + tw + 3, y + 9, color);
        g.fill(x, y, x + tw + 2, y + 8, PARCHMENT_BG);
        g.drawString(font, text, x + 1, y, color, false);
    }

    /**
     * Draws a horizontal separator line.
     */
    public static void drawSeparator(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, INK_GHOST);
    }

    /**
     * Draws a parchment-style button region. Returns true if hovered.
     */
    public static boolean drawButton(GuiGraphics g, Font font, String text, int x, int y, int w, int h,
                                     int mouseX, int mouseY, int color) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int border = hovered ? color : INK_GHOST;
        int bg = hovered ? (color & 0x00FFFFFF) | 0x30000000 : PARCHMENT_BG;
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, hovered ? WHITE : color);
        return hovered;
    }

    /**
     * Draws a toggle button (ON/OFF style).
     */
    public static boolean drawToggle(GuiGraphics g, Font font, boolean on, int x, int y, int w, int h,
                                     int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int color = on ? GREEN : DANGER;
        int bg = on ? 0x30006600 : 0x30660000;
        if (hovered) bg = on ? 0x50006600 : 0x50660000;
        g.fill(x, y, x + w, y + h, color);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.drawCenteredString(font, on ? "ON" : "OFF", x + w / 2, y + (h - 8) / 2, WHITE);
        return hovered;
    }

    /**
     * Draws a section heading with underline.
     */
    public static void drawHeading(GuiGraphics g, Font font, String text, int x, int y, int w) {
        int tw = font.width(text);
        g.drawString(font, text, x + w / 2 - tw / 2, y, INK, false);
        g.fill(x, y + 10, x + w, y + 11, INK_GHOST);
    }

    /**
     * Draws a scrollbar track and thumb.
     */
    public static void drawScrollbar(GuiGraphics g, int x, int y, int height,
                                     int totalItems, int visibleItems, int scrollOffset) {
        if (totalItems <= visibleItems) return;
        // Track
        g.fill(x, y, x + 3, y + height, PARCHMENT_BG);
        // Thumb
        int thumbH = Math.max(6, (height * visibleItems) / totalItems);
        int maxScroll = Math.max(1, totalItems - visibleItems);
        int thumbY = y + (int) ((height - thumbH) * ((float) scrollOffset / maxScroll));
        g.fill(x, thumbY, x + 3, thumbY + thumbH, INK_GHOST);
    }

    /**
     * Checks if a point is within a rectangular region.
     */
    public static boolean isInBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * Truncates text to fit within maxWidth, appending "..." if needed.
     */
    public static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        while (text.length() > 3 && font.width(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }
}
