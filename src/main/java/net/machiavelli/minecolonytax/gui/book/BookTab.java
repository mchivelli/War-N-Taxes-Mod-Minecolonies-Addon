package net.machiavelli.minecolonytax.gui.book;

import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.resources.ResourceLocation;

/**
 * Enum defining the book tabs with their hitbox coordinates and icon paths.
 * Coordinates are relative to the 360x280 GUI.
 * Left-side tabs have x=0; right-side tabs have x at the right edge.
 */
public enum BookTab {
    // Tab hitbox positions relative to 360x280 GUI.
    // Calibrated from in-game measurements at GUI scale 2 (screen px / 2 = GUI px).
    // Icons are centered within hitbox by renderTabs().
    //                                                          iconDX iconDY
    COLONIES("Colonies", Side.LEFT, 14, 57, 28, 24,
            "textures/gui/icons/colonies_icon.png", true,    0,  0),
    VASSALS("Vassals", Side.LEFT, 14, 90, 28, 22,
            "textures/gui/icons/vassals_icon.png", true,     1, -2),
    OFFICERS("Officers", Side.LEFT, 14, 151, 28, 24,
            "textures/gui/icons/officers_icon.png", true,    0, -3),
    INVESTMENTS("Investments", Side.LEFT, 14, 186, 28, 22,
            "textures/gui/icons/economy_icon.png", false,    0, -3),
    TREASURY("Treasury", Side.RIGHT, 320, 102, 28, 24,
            "textures/gui/icons/warchest_icon.png", false,   2,  0),
    ESPIONAGE("Espionage", Side.RIGHT, 320, 133, 28, 22,
            "textures/gui/icons/espionage_icon.png", false,  2,  0);

    public enum Side { LEFT, RIGHT }

    public final String displayName;
    public final Side side;
    /** X position relative to GUI left edge */
    public final int x;
    /** Y position relative to GUI top edge */
    public final int y;
    public final int width;
    public final int height;
    public final ResourceLocation icon;
    private final boolean alwaysEnabled;
    /** Fine-tune the icon render position relative to its centered default (does not affect hitbox). */
    public final int iconOffsetX;
    public final int iconOffsetY;

    BookTab(String displayName, Side side, int x, int y, int w, int h, String iconPath, boolean alwaysEnabled,
            int iconOffsetX, int iconOffsetY) {
        this.displayName = displayName;
        this.side = side;
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
        this.icon = new ResourceLocation("minecolonytax", iconPath);
        this.alwaysEnabled = alwaysEnabled;
        this.iconOffsetX = iconOffsetX;
        this.iconOffsetY = iconOffsetY;
    }

    /**
     * Whether this tab should be visible given current config.
     */
    public boolean isEnabled() {
        if (alwaysEnabled) return true;
        return switch (this) {
            case TREASURY    -> TaxConfig.isTreasuryEnabled();
            case ESPIONAGE   -> TaxConfig.isSpySystemEnabled();
            case INVESTMENTS -> TaxConfig.isUpgradesEnabled();
            default -> true;
        };
    }

    /**
     * Returns the absolute hitbox X for a given guiLeft.
     * Right-side tabs are positioned relative to the right edge.
     */
    public int getAbsX(int guiLeft, int guiWidth) {
        if (side == Side.RIGHT) {
            return guiLeft + guiWidth - (guiWidth - x);
        }
        return guiLeft + x;
    }

    /**
     * Returns the absolute hitbox Y for a given guiTop.
     */
    public int getAbsY(int guiTop) {
        return guiTop + y;
    }

    /**
     * Tests if the mouse is over this tab's hitbox.
     */
    public boolean isMouseOver(double mouseX, double mouseY, int guiLeft, int guiTop, int guiWidth) {
        int ax = getAbsX(guiLeft, guiWidth);
        int ay = getAbsY(guiTop);
        return mouseX >= ax && mouseX < ax + width && mouseY >= ay && mouseY < ay + height;
    }
}
