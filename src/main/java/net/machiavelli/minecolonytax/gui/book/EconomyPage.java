package net.machiavelli.minecolonytax.gui.book;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import static net.machiavelli.minecolonytax.gui.book.BookRenderHelper.*;

/**
 * Economy tab: placeholder for future trade network / market features.
 */
public class EconomyPage extends BookPage {

    public EconomyPage(Screen screen, Font font) {
        super(screen, font);
    }

    @Override
    public void renderLeftPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawHeading(g, font, "Trade Network", leftX, leftY, leftW);
        int cy = leftY + leftH / 2 - 10;
        g.drawCenteredString(font, "$", leftX + leftW / 2, cy - 10, INK_GHOST);
        g.drawCenteredString(font, "Merchant routes", leftX + leftW / 2, cy + 4, INK_FAINT);
        g.drawCenteredString(font, "and global market", leftX + leftW / 2, cy + 14, INK_FAINT);
        g.drawCenteredString(font, "tariffs are currently", leftX + leftW / 2, cy + 24, INK_FAINT);
        g.drawCenteredString(font, "unavailable.", leftX + leftW / 2, cy + 34, INK_FAINT);
    }

    @Override
    public void renderRightPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cy = rightY + rightH / 2 - 10;
        g.drawCenteredString(font, "$", rightX + rightW / 2, cy - 10, INK_GHOST);
        g.drawCenteredString(font, "Coming in a", rightX + rightW / 2, cy + 4, INK_GHOST);
        g.drawCenteredString(font, "future update", rightX + rightW / 2, cy + 14, INK_GHOST);
    }
}
