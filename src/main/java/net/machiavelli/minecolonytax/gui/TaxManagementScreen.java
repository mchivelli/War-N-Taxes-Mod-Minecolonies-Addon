package net.machiavelli.minecolonytax.gui;

import net.machiavelli.minecolonytax.gui.book.*;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.OfficerData;
import net.machiavelli.minecolonytax.gui.data.SpyMissionData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.machiavelli.minecolonytax.network.ClientPacketHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Book-style Tax Management GUI shell (NeoForge 1.21.1 port).
 *
 * <p>Renders the book background + icon tabs and delegates page content to the {@link BookPage}
 * subclasses in {@code gui/book/}. This replaces the old monolithic single-screen layout so the
 * port matches the redesigned book GUI of the 1.20.1 line. All public {@code updateX} methods keep
 * the signatures the network payload handlers ({@code MctClientNetHandlers}) call.
 */
public class TaxManagementScreen extends Screen {

    private static final ResourceLocation BOOK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecolonytax", "textures/gui/book_background.png");

    private static final int GUI_WIDTH = 360;
    private static final int GUI_HEIGHT = 280;
    // Book image is 300x256, stretched to the GUI size.
    private static final int IMG_W = 300;
    private static final int IMG_H = 256;

    // Page content areas (relative to guiLeft/guiTop) — calibrated to the book parchment panes.
    private static final int LEFT_X = 47, LEFT_Y = 43, LEFT_W = 135, LEFT_H = 179;
    private static final int RIGHT_X = 189, RIGHT_Y = 43, RIGHT_W = 130, RIGHT_H = 179;
    private static final int BOTTOM_Y = 224;

    // Icons are 32x32 PNGs rendered at 12x12 on screen.
    private static final int ICON_RENDER_SIZE = 12;
    private static final int ICON_TEX_SIZE = 32;

    // --- Data (shared across pages) ---
    private final List<ColonyTaxData> colonies = new ArrayList<>();
    private final List<VassalIncomeData> vassalData = new ArrayList<>();
    private final List<OfficerData> officerData = new ArrayList<>();
    private final List<SpyMissionData> spyMissions = new ArrayList<>();
    private ColonyTaxData selectedColony = null;

    /** Latest missions — readable by JourneyMap compat + network handlers. */
    private static volatile List<SpyMissionData> latestSpyMissions = new ArrayList<>();

    // --- Pages ---
    private final Map<BookTab, BookPage> pages = new EnumMap<>(BookTab.class);
    private BookTab activeTab = BookTab.COLONIES;
    private WarChestPage warChestPage;
    private InvestmentsPage investmentsPage;

    // --- Bottom bar widgets ---
    private Button refreshButton;
    private Button claimAllButton;

    // --- Layout ---
    private int guiLeft, guiTop;

    public TaxManagementScreen() {
        super(Component.translatable("gui.minecolonytax.tax_management.title"));
    }

    // ========== Public API (signatures consumed by MctClientNetHandlers / payloads) ==========

    public static List<SpyMissionData> getLatestSpyMissions() {
        return latestSpyMissions;
    }

    public static void updateLatestMissions(List<SpyMissionData> missions) {
        latestSpyMissions = new ArrayList<>(missions);
    }

    public void updateColonyData(List<ColonyTaxData> newData) {
        int prevSelectedId = selectedColony != null ? selectedColony.getColonyId() : -1;
        this.colonies.clear();
        this.colonies.addAll(newData);
        this.colonies.sort((a, b) -> {
            if (a.isOwner() && !b.isOwner()) return -1;
            if (!a.isOwner() && b.isOwner()) return 1;
            return a.getColonyName().compareToIgnoreCase(b.getColonyName());
        });
        if (prevSelectedId != -1) {
            selectedColony = this.colonies.stream()
                    .filter(c -> c.getColonyId() == prevSelectedId)
                    .findFirst().orElse(null);
        } else {
            selectedColony = null;
        }
    }

    public void updateVassalData(List<VassalIncomeData> newVassalData) {
        this.vassalData.clear();
        this.vassalData.addAll(newVassalData);
    }

    public void updateOfficerData(List<OfficerData> newOfficerData, int colonyId) {
        this.officerData.clear();
        this.officerData.addAll(newOfficerData);
    }

    public void updateSpyData(List<SpyMissionData> missions) {
        this.spyMissions.clear();
        this.spyMissions.addAll(missions);
        latestSpyMissions = new ArrayList<>(missions);
    }

    public void updateWarChestData(int colonyId, int balance, int maxCapacity, int drainPerMinute,
                                   int taxBalance, boolean autoSurrender, double minPercentForWar) {
        if (warChestPage != null) {
            warChestPage.updateWarChestData(balance, maxCapacity, drainPerMinute,
                    taxBalance, autoSurrender, minPercentForWar);
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

        pages.clear();

        pages.put(BookTab.COLONIES, new ColoniesPage(this, this.font,
                () -> colonies, () -> selectedColony, c -> selectedColony = c));

        pages.put(BookTab.VASSALS, new VassalsPage(this, this.font,
                () -> vassalData, () -> colonies, this::requestColonyData));

        pages.put(BookTab.OFFICERS, new OfficersPage(this, this.font,
                () -> officerData, () -> selectedColony));

        warChestPage = new WarChestPage(this, this.font, () -> selectedColony,
                eb -> this.addRenderableWidget(eb),
                btn -> this.addRenderableWidget(btn));
        pages.put(BookTab.WAR_CHEST, warChestPage);

        investmentsPage = new InvestmentsPage(this, this.font, () -> selectedColony);
        pages.put(BookTab.INVESTMENTS, investmentsPage);

        pages.put(BookTab.ESPIONAGE, new EspionagePage(this, this.font,
                () -> spyMissions, () -> colonies,
                eb -> this.addRenderableWidget(eb)));

        pages.put(BookTab.ECONOMY, new EconomyPage(this, this.font));

        // Let pages create their widgets, then position every page.
        for (BookPage page : pages.values()) page.init();
        updatePageLayouts();

        int barY = guiTop + BOTTOM_Y;
        refreshButton = Button.builder(Component.literal("Refresh"), btn -> requestColonyData())
                .bounds(guiLeft + LEFT_X, barY, 40, 11).build();
        this.addRenderableWidget(refreshButton);

        claimAllButton = Button.builder(Component.literal("Claim All"), btn -> claimAllTaxes())
                .bounds(guiLeft + RIGHT_X + RIGHT_W - 44, barY, 44, 11).build();
        this.addRenderableWidget(claimAllButton);

        // Show only the active tab's widgets.
        for (Map.Entry<BookTab, BookPage> e : pages.entrySet()) {
            e.getValue().setWidgetsVisible(e.getKey() == activeTab);
        }
        BookPage active = pages.get(activeTab);
        if (active != null) active.onActivated();

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
        ClientPacketHelper.sendRequestColonyDataPacket(0);
        officerData.clear();
    }

    private void claimAllTaxes() {
        for (ColonyTaxData colony : colonies) {
            if (colony.getTaxBalance() > 0 && colony.canClaimTax()) {
                ClientPacketHelper.sendClaimTaxPacket(colony.getColonyId(), -1);
            }
        }
        requestColonyData();
    }

    // ========== Tab switching ==========

    private void switchTab(BookTab newTab) {
        if (newTab == activeTab || !newTab.isEnabled()) return;

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
        // Officers page needs per-colony data.
        if (newTab == BookTab.OFFICERS && selectedColony != null) {
            ClientPacketHelper.sendRequestOfficerDataPacket(selectedColony.getColonyId());
        }
    }

    // ========== Rendering ==========

    @Override
    public void renderBackground(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Intentionally a NO-OP. The book texture is our background. NeoForge 1.21 calls
        // renderBackground() from Screen.render(), and the default implementation applies the
        // menu-background blur (renderBlurredBackground). Overriding it empty kills that blur
        // and guarantees nothing paints a dim/blur sheet over the book.
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No dim, no blur — the book renders over the sharp world (matches the 1.20.1 look).

        // Book background: 300x256 image scaled to GUI_WIDTH x GUI_HEIGHT.
        g.pose().pushPose();
        g.pose().translate(guiLeft, guiTop, 0);
        g.pose().scale((float) GUI_WIDTH / IMG_W, (float) GUI_HEIGHT / IMG_H, 1.0f);
        g.blit(BOOK_TEXTURE, 0, 0, 0, 0, IMG_W, IMG_H, IMG_W, IMG_H);
        g.pose().popPose();

        // Title centered on the spine.
        g.drawCenteredString(this.font, this.title, guiLeft + GUI_WIDTH / 2, guiTop + 28, BookRenderHelper.INK);

        renderTabs(g, mouseX, mouseY);

        BookPage activePage = pages.get(activeTab);
        if (activePage != null) {
            g.enableScissor(guiLeft + LEFT_X, guiTop + LEFT_Y,
                    guiLeft + LEFT_X + LEFT_W, guiTop + LEFT_Y + LEFT_H);
            activePage.renderLeftPage(g, mouseX, mouseY, partialTick);
            g.disableScissor();

            g.enableScissor(guiLeft + RIGHT_X, guiTop + RIGHT_Y,
                    guiLeft + RIGHT_X + RIGHT_W, guiTop + RIGHT_Y + RIGHT_H);
            activePage.renderRightPage(g, mouseX, mouseY, partialTick);
            g.disableScissor();
        }

        // Widgets (buttons / edit boxes) on top.
        super.render(g, mouseX, mouseY, partialTick);

        renderTabTooltips(g, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (BookTab tab : BookTab.values()) {
            if (!tab.isEnabled()) continue;
            int tx = tab.getAbsX(guiLeft, GUI_WIDTH);
            int ty = tab.getAbsY(guiTop);
            int iconX = tx + (tab.width - ICON_RENDER_SIZE) / 2;
            int iconY = ty + (tab.height - ICON_RENDER_SIZE) / 2;

            if (tab == activeTab) {
                g.fill(iconX - 1, iconY - 1, iconX + ICON_RENDER_SIZE + 1, iconY + ICON_RENDER_SIZE + 1, 0x50000000);
            }

            // Scale the 32x32 icon down to 12x12 via the pose stack (uses the simple blit overload).
            g.pose().pushPose();
            g.pose().translate(iconX, iconY, 0);
            g.pose().scale((float) ICON_RENDER_SIZE / ICON_TEX_SIZE, (float) ICON_RENDER_SIZE / ICON_TEX_SIZE, 1.0f);
            g.blit(tab.icon, 0, 0, 0, 0, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE);
            g.pose().popPose();
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
            for (BookTab tab : BookTab.values()) {
                if (tab.isEnabled() && tab.isMouseOver(mouseX, mouseY, guiLeft, guiTop, GUI_WIDTH)) {
                    switchTab(tab);
                    return true;
                }
            }
            BookPage activePage = pages.get(activeTab);
            if (activePage != null && activePage.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        BookPage activePage = pages.get(activeTab);
        if (activePage != null && activePage.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
