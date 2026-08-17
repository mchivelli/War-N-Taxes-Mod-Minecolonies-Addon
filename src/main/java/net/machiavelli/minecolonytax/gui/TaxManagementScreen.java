package net.machiavelli.minecolonytax.gui;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.events.random.EventLogEntry;
import net.machiavelli.minecolonytax.gui.book.*;
import net.machiavelli.minecolonytax.gui.data.ColonySummary;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.OfficerData;
import net.machiavelli.minecolonytax.gui.data.SpyMissionData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.packets.ClaimTaxPacket;
import net.machiavelli.minecolonytax.network.packets.RequestColonyDataPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Book-style Tax Management GUI shell.
 * Renders the book background, tab icons, and delegates page content to BookPage subclasses.
 * All public data-update methods are preserved for network packet compatibility.
 */
public class TaxManagementScreen extends Screen {

    private static final ResourceLocation BOOK_TEXTURE = new ResourceLocation("minecolonytax",
            "textures/gui/book_background.png");
    private static final int GUI_WIDTH = 360;
    private static final int GUI_HEIGHT = 280;

    // Book image is 300x256, stretched to 360x280
    private static final int IMG_W = 300;
    private static final int IMG_H = 256;

    // Page layout constants (relative to guiLeft/guiTop)
    // Matches CSS: page areas extend to just before the bottom bar.
    // Parchment area is ~190px tall, but we stop at y=224 (bottom bar) minus 2px margin.
    private static final int LEFT_X = 47, LEFT_Y = 43, LEFT_W = 135, LEFT_H = 179;
    private static final int RIGHT_X = 189, RIGHT_Y = 43, RIGHT_W = 130, RIGHT_H = 179;
    private static final int BOTTOM_Y = 224;

    // Icon rendering: all icons are 32x32 PNGs rendered at 12x12 on screen
    private static final int ICON_RENDER_SIZE = 12;
    private static final int ICON_TEX_SIZE = 32;

    // --- Data (shared across pages) ---
    private final List<ColonyTaxData> colonies = new ArrayList<>();
    private final List<VassalIncomeData> vassalData = new ArrayList<>();
    private final List<OfficerData> officerData = new ArrayList<>();
    /** Colony the officer list belongs to; -2 = nothing loaded yet (distinguishes "loading" from "empty"). */
    private int officerDataColonyId = -2;
    /** Colony-wide default per action, as reported by the server. */
    private final java.util.Map<net.machiavelli.minecolonytax.permissions.ColonyPermission, Boolean>
            colonyPermissionDefaults = new java.util.EnumMap<>(
                    net.machiavelli.minecolonytax.permissions.ColonyPermission.class);
    private final List<SpyMissionData> spyMissions = new ArrayList<>();
    private final List<ColonySummary> allColonySummaries = new ArrayList<>();
    private final Map<Integer, List<EventLogEntry>> eventLogData = new HashMap<>();
    private ColonyTaxData selectedColony = null;

    /** Latest missions — readable by JourneyMap compat. */
    private static volatile List<SpyMissionData> latestSpyMissions = new ArrayList<>();

    // --- Pages ---
    private final Map<BookTab, BookPage> pages = new EnumMap<>(BookTab.class);
    private BookTab activeTab = BookTab.COLONIES;
    private TreasuryPage treasuryPage;
    private InvestmentsPage investmentsPage;

    // --- Bottom bar widgets ---
    private Button refreshButton;
    private Button claimAllButton;

    // --- Layout ---
    private int guiLeft, guiTop;

    public TaxManagementScreen() {
        super(Component.translatable("gui.minecolonytax.tax_management.title"));
    }

    // ========== Public API (unchanged signatures for packet handlers) ==========

    public static List<SpyMissionData> getLatestSpyMissions() {
        return latestSpyMissions;
    }

    public static void updateLatestMissions(List<SpyMissionData> missions) {
        latestSpyMissions = new ArrayList<>(missions);
    }

    public void updateColonyData(List<ColonyTaxData> newData) {
        // Remember the previously selected colony ID so we can restore it after refresh
        int prevSelectedId = selectedColony != null ? selectedColony.getColonyId() : -1;

        this.colonies.clear();
        this.colonies.addAll(newData);
        this.colonies.sort((a, b) -> {
            if (a.isOwner() && !b.isOwner()) return -1;
            if (!a.isOwner() && b.isOwner()) return 1;
            return a.getColonyName().compareToIgnoreCase(b.getColonyName());
        });

        // Restore selection if the colony is still present after refresh
        if (prevSelectedId != -1) {
            selectedColony = this.colonies.stream()
                    .filter(c -> c.getColonyId() == prevSelectedId)
                    .findFirst()
                    .orElse(null);
        } else {
            selectedColony = null;
        }
    }

    public void updateVassalData(List<VassalIncomeData> newVassalData) {
        this.vassalData.clear();
        this.vassalData.addAll(newVassalData);
    }

    public void updateAllColonySummaries(List<ColonySummary> summaries) {
        this.allColonySummaries.clear();
        if (summaries != null) {
            this.allColonySummaries.addAll(summaries);
            this.allColonySummaries.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        }
    }

    public void updateOfficerData(List<OfficerData> newOfficerData, int colonyId,
                                  java.util.Map<net.machiavelli.minecolonytax.permissions.ColonyPermission,
                                          Boolean> colonyDefaults) {
        // Drop responses for a colony that is no longer selected. The colonyId used to be
        // ignored, so a late reply for the previously selected colony overwrote the list the
        // player was actually looking at.
        if (colonyId != -1 && (selectedColony == null || selectedColony.getColonyId() != colonyId)) {
            return;
        }

        this.officerData.clear();
        this.officerData.addAll(newOfficerData);
        this.officerDataColonyId = colonyId;
        this.colonyPermissionDefaults.clear();
        if (colonyDefaults != null) this.colonyPermissionDefaults.putAll(colonyDefaults);
    }

    public void updateSpyData(List<SpyMissionData> missions) {
        this.spyMissions.clear();
        this.spyMissions.addAll(missions);
        latestSpyMissions = new ArrayList<>(missions);
    }

    public void updateEventData(Map<Integer, List<EventLogEntry>> data) {
        this.eventLogData.clear();
        this.eventLogData.putAll(data);
    }

    public void updateTreasuryData(int colonyId, int balance, int maxCapacity, int drainPerMinute,
                                    int taxBalance, boolean autoSurrender, double minPercentForWar,
                                    long walletBalance) {
        if (treasuryPage != null) {
            treasuryPage.updateTreasuryData(balance, maxCapacity, drainPerMinute,
                    taxBalance, autoSurrender, minPercentForWar, walletBalance);
        }
    }

    public void updateInvestmentData(int colonyId, java.util.Map<String, Integer> levels,
                                     java.util.Map<String, Integer> costs,
                                     int treasuryBalance, int maxCapacity, int maxLevel) {
        if (investmentsPage != null) {
            investmentsPage.updateData(colonyId, levels, costs, treasuryBalance, maxCapacity, maxLevel);
        }
    }

    // ========== Lifecycle ==========

    @Override
    protected void init() {
        super.init();
        guiLeft = (this.width - GUI_WIDTH) / 2;
        guiTop = (this.height - GUI_HEIGHT) / 2;

        // Create pages
        pages.clear();

        ColoniesPage coloniesPage = new ColoniesPage(this, this.font,
                () -> colonies, () -> selectedColony, c -> { selectedColony = c; },
                () -> eventLogData,
                this::requestColonyData);
        pages.put(BookTab.COLONIES, coloniesPage);

        pages.put(BookTab.VASSALS, new VassalsPage(this, this.font,
                () -> vassalData, () -> colonies, this::requestColonyData));

        pages.put(BookTab.OFFICERS, new OfficersPage(this, this.font,
                () -> officerData, () -> selectedColony,
                () -> officerDataColonyId,
                perm -> colonyPermissionDefaults.getOrDefault(perm, perm.isDefaultAllowed())));

        treasuryPage = new TreasuryPage(this, this.font, () -> selectedColony,
                eb -> this.addRenderableWidget(eb),
                btn -> this.addRenderableWidget(btn));
        pages.put(BookTab.TREASURY, treasuryPage);

        pages.put(BookTab.ESPIONAGE, new EspionagePage(this, this.font,
                () -> spyMissions, () -> allColonySummaries,
                eb -> this.addRenderableWidget(eb)));

        investmentsPage = new InvestmentsPage(this, this.font, () -> selectedColony);
        pages.put(BookTab.INVESTMENTS, investmentsPage);

        // Init all pages (lets TreasuryPage create widgets)
        for (BookPage page : pages.values()) {
            page.init();
        }

        // Set layout for all pages
        updatePageLayouts();

        // Bottom bar buttons
        int barX = guiLeft + LEFT_X;
        int barY = guiTop + BOTTOM_Y;

        refreshButton = Button.builder(Component.literal("Refresh"), btn -> requestColonyData())
                .bounds(barX, barY, 40, 11).build();
        this.addRenderableWidget(refreshButton);

        claimAllButton = Button.builder(Component.literal("Claim All"), btn -> claimAllTaxes())
                .bounds(guiLeft + RIGHT_X + RIGHT_W - 44, barY, 44, 11).build();
        this.addRenderableWidget(claimAllButton);

        // Activate default tab and hide all other page widgets
        for (Map.Entry<BookTab, BookPage> entry : pages.entrySet()) {
            entry.getValue().setWidgetsVisible(entry.getKey() == activeTab);
        }
        BookPage activePage = pages.get(activeTab);
        if (activePage != null) activePage.onActivated();

        // Request initial data
        requestColonyData();
    }

    private void updatePageLayouts() {
        int lx = guiLeft + LEFT_X, ly = guiTop + LEFT_Y;
        int rx = guiLeft + RIGHT_X, ry = guiTop + RIGHT_Y;
        for (BookPage page : pages.values()) {
            page.setLayout(lx, ly, LEFT_W, LEFT_H, rx, ry, RIGHT_W, RIGHT_H);
        }
    }

    private void requestColonyData() {
        NetworkHandler.sendToServer(new RequestColonyDataPacket());
        officerData.clear();
        officerDataColonyId = -2;
        // Re-request instead of only clearing: hitting Refresh while the Officers tab was open
        // used to wipe the list and leave it on "Loading..." until the player switched tabs.
        if (selectedColony != null) {
            NetworkHandler.sendToServer(
                    new net.machiavelli.minecolonytax.network.packets.RequestOfficerDataPacket(
                            selectedColony.getColonyId()));
        }
    }

    private void claimAllTaxes() {
        for (ColonyTaxData colony : colonies) {
            if (colony.getTaxBalance() > 0 && colony.canClaimTax()) {
                NetworkHandler.CHANNEL.sendToServer(new ClaimTaxPacket(colony.getColonyId(), -1));
            }
        }
        requestColonyData();
    }

    // ========== Tab switching ==========

    private void switchTab(BookTab newTab) {
        if (newTab == activeTab) return;
        if (!newTab.isEnabled()) return;

        BookPage oldPage = pages.get(activeTab);
        if (oldPage != null) {
            oldPage.onDeactivated();
            oldPage.setWidgetsVisible(false);
        }

        activeTab = newTab;

        BookPage newPage = pages.get(activeTab);
        if (newPage != null) {
            newPage.setWidgetsVisible(true);
            newPage.onActivated();
        }
    }

    // ========== Rendering ==========

    @Override
    public void renderBackground(@Nonnull GuiGraphics guiGraphics) {
        // Override to prevent Screen.render() from drawing the default dim gradient
        // over our book. We draw our own dim in render() before the book.
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Dim game world behind the book (replaces default renderBackground)
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
        }

        // Draw book background: 300x256 image stretched to 360x280
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(guiLeft, guiTop, 0);
        guiGraphics.pose().scale((float) GUI_WIDTH / IMG_W, (float) GUI_HEIGHT / IMG_H, 1.0f);
        guiGraphics.blit(BOOK_TEXTURE, 0, 0, 0, 0, IMG_W, IMG_H, IMG_W, IMG_H);
        guiGraphics.pose().popPose();

        // Title centered on spine
        guiGraphics.drawCenteredString(this.font, this.title, guiLeft + GUI_WIDTH / 2, guiTop + 28,
                BookRenderHelper.INK);

        // Render tab icons over the colored bookmark markers
        renderTabs(guiGraphics, mouseX, mouseY);

        // Render active page content with scissor clipping to prevent parchment overflow
        BookPage activePage = pages.get(activeTab);
        if (activePage != null) {
            // Left page
            guiGraphics.enableScissor(guiLeft + LEFT_X, guiTop + LEFT_Y,
                    guiLeft + LEFT_X + LEFT_W, guiTop + LEFT_Y + LEFT_H);
            activePage.renderLeftPage(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.disableScissor();

            // Right page
            guiGraphics.enableScissor(guiLeft + RIGHT_X, guiTop + RIGHT_Y,
                    guiLeft + RIGHT_X + RIGHT_W, guiTop + RIGHT_Y + RIGHT_H);
            activePage.renderRightPage(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.disableScissor();
        }

        // Render Minecraft widgets (buttons, editbox) on top — super.render() calls
        // renderBackground() (now a no-op) then iterates renderables
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Page hover tooltips — rendered after scissor regions and widgets so they appear on top
        if (activePage != null) activePage.renderTooltips(guiGraphics, mouseX, mouseY);

        // Tab tooltips (must be after super.render so they appear on top of widgets)
        renderTabTooltips(guiGraphics, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (BookTab tab : BookTab.values()) {
            if (!tab.isEnabled()) continue;
            int tx = tab.getAbsX(guiLeft, GUI_WIDTH);
            int ty = tab.getAbsY(guiTop);

            // Icon centered within the tab's hitbox area, plus per-tab fine-tune offset
            int iconX = tx + (tab.width - ICON_RENDER_SIZE) / 2 + tab.iconOffsetX;
            int iconY = ty + (tab.height - ICON_RENDER_SIZE) / 2 + tab.iconOffsetY;

            // Active tab: draw greyish background behind icon to indicate selection
            if (tab == activeTab) {
                g.fill(iconX - 1, iconY - 1, iconX + ICON_RENDER_SIZE + 1,
                        iconY + ICON_RENDER_SIZE + 1, 0x50000000);
            }

            // blit(texture, screenX, screenY, screenW, screenH, u, v, uW, vH, texW, texH)
            g.blit(tab.icon, iconX, iconY, ICON_RENDER_SIZE, ICON_RENDER_SIZE,
                    0.0f, 0.0f, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE);
        }
    }

    private void renderTabTooltips(GuiGraphics g, int mouseX, int mouseY) {
        for (BookTab tab : BookTab.values()) {
            if (!tab.isEnabled()) continue;
            if (tab.isMouseOver(mouseX, mouseY, guiLeft, guiTop, GUI_WIDTH)) {
                g.renderTooltip(this.font, Component.literal(tab.displayName), mouseX, mouseY);
                break;
            }
        }
    }

    // ========== Input ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check tab clicks first
            for (BookTab tab : BookTab.values()) {
                if (tab.isEnabled() && tab.isMouseOver(mouseX, mouseY, guiLeft, guiTop, GUI_WIDTH)) {
                    switchTab(tab);
                    return true;
                }
            }

            // Delegate to active page
            BookPage activePage = pages.get(activeTab);
            if (activePage != null && activePage.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        BookPage activePage = pages.get(activeTab);
        if (activePage != null && activePage.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
