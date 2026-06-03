package net.machiavelli.minecolonytax.gui.book;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Abstract base class for book pages. Each page renders into a left and right pane
 * within the book layout. All coordinates passed to render methods are absolute screen coords.
 */
public abstract class BookPage {

    protected final Screen screen;
    protected final Font font;

    // Absolute screen coordinates -- set by setLayout()
    protected int leftX, leftY, leftW, leftH;
    protected int rightX, rightY, rightW, rightH;

    protected BookPage(Screen screen, Font font) {
        this.screen = screen;
        this.font = font;
    }

    /**
     * Called by the shell to set absolute page area coordinates.
     */
    public void setLayout(int leftX, int leftY, int leftW, int leftH,
                          int rightX, int rightY, int rightW, int rightH) {
        this.leftX = leftX;
        this.leftY = leftY;
        this.leftW = leftW;
        this.leftH = leftH;
        this.rightX = rightX;
        this.rightY = rightY;
        this.rightW = rightW;
        this.rightH = rightH;
    }

    /**
     * Render the left page content. Scissor is already applied.
     */
    public abstract void renderLeftPage(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /**
     * Render the right page content. Scissor is already applied.
     */
    public abstract void renderRightPage(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /**
     * Handle mouse click. Return true if consumed.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Handle mouse scroll. Return true if consumed.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    /**
     * Called when this page becomes the active tab.
     */
    public void onActivated() {}

    /**
     * Called when switching away from this page.
     */
    public void onDeactivated() {}

    /**
     * Show/hide any Minecraft widgets owned by this page.
     * Default: no-op (most pages have no widgets).
     */
    public void setWidgetsVisible(boolean visible) {}

    /**
     * Called during init() to let pages add widgets.
     * Only WarChestPage needs this.
     */
    public void init() {}

    /**
     * Helper: check if mouse is in left page bounds.
     */
    protected boolean isInLeftPage(double mx, double my) {
        return mx >= leftX && mx < leftX + leftW && my >= leftY && my < leftY + leftH;
    }

    /**
     * Helper: check if mouse is in right page bounds.
     */
    protected boolean isInRightPage(double mx, double my) {
        return mx >= rightX && mx < rightX + rightW && my >= rightY && my < rightY + rightH;
    }
}
