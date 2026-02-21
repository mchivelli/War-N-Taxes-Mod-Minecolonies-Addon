package net.machiavelli.minecolonytax.gui;

import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.machiavelli.minecolonytax.gui.data.OfficerData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.packets.ClaimTaxPacket;
import net.machiavelli.minecolonytax.network.packets.RequestColonyDataPacket;
import net.machiavelli.minecolonytax.network.packets.PayTaxDebtPacket;
import net.machiavelli.minecolonytax.network.packets.EndVassalizationPacket;
import net.machiavelli.minecolonytax.network.packets.UpdateTaxPermissionPacket;
import net.machiavelli.minecolonytax.network.packets.UpdatePlayerTaxPermissionPacket;
import net.machiavelli.minecolonytax.network.packets.RequestOfficerDataPacket;
import net.machiavelli.minecolonytax.network.packets.RequestWarChestDataPacket;
import net.machiavelli.minecolonytax.network.packets.WarChestActionPacket;
import net.machiavelli.minecolonytax.network.packets.SetTaxPolicyPacket;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicy;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaxManagementScreen extends Screen {
    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation("minecolonytax",
            "textures/gui/backgroundmenu.png");
    private static final int GUI_WIDTH = 360;
    private static final int GUI_HEIGHT = 280;

    private List<ColonyTaxData> colonies = new ArrayList<>();
    private List<VassalIncomeData> vassalData = new ArrayList<>();
    private List<OfficerData> officerData = new ArrayList<>();
    private int scrollOffset = 0;
    private int vassalScrollOffset = 0;
    private final int maxVisibleColonies = 5; // Show colonies with compact GUI size
    private final int maxVisibleVassals = 5; // Show vassals with compact GUI size
    private ColonyTaxData selectedColony = null;
    private Button refreshButton;
    private Button claimAllButton;
    private Button vassalsTabButton;
    private Button coloniesTabButton;
    private Button permissionsTabButton;
    private Button claimSelectedButton;
    private Button payDebtButton;
    private Button endVassalButton;
    private Button warChestTabButton;
    private boolean showingVassals = false;
    private boolean showingPermissions = false;
    private boolean showingWarChest = false;

    // Espionage fields
    private boolean showingEspionage = false;
    private Button espionageTabButton;
    private List<net.machiavelli.minecolonytax.gui.data.SpyMissionData> spyMissions = new ArrayList<>();
    private int selectedTargetColonyIndex = 0;
    private int selectedMissionTypeIndex = 0;
    private static final String[] MISSION_TYPES = { "SCOUT", "SABOTAGE", "BRIBE", "STEAL" };

    // War Chest UI Elements
    private EditBox amountInput;
    private Button depositButton;
    private Button withdrawButton;

    // War Chest data (received from server)
    private int warChestBalance = 0;
    private int warChestMaxCapacity = 0;
    private int warChestDrainPerMin = 0;
    private int currentTaxBalance = 0;
    private boolean warChestAutoSurrender = false;
    private double warChestMinPercent = 0.0;

    // Permission GUI elements
    private Rectangle permissionToggleButton;
    private Map<Integer, Rectangle> officerToggleButtons;

    // Enhanced Colors for Beautiful Design
    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_LIGHT_GRAY = 0xE0E0E0;
    private static final int COLOR_GREEN = 0x4CAF50;
    private static final int COLOR_LIGHT_GREEN = 0x8BC34A;
    private static final int COLOR_RED = 0xF44336;
    private static final int COLOR_ORANGE = 0xFF9800;
    private static final int COLOR_YELLOW = 0xFFEB3B;
    private static final int COLOR_GOLD = 0xFFC107;
    private static final int COLOR_BLUE = 0x2196F3;
    private static final int COLOR_GRAY = 0x9E9E9E;
    private static final int COLOR_BACKGROUND = 0x2E2E2E;
    private static final int COLOR_BORDER = 0x5E5E5E;
    private static final int COLOR_HEADER = 0x1976D2;

    public TaxManagementScreen() {
        super(Component.translatable("gui.minecolonytax.tax_management.title"));
    }

    @Override
    protected void init() {
        super.init();

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        // Tab buttons - centered for better visual alignment with parchment background
        int tabButtonWidth = 70;
        int tabButtonHeight = 20;
        boolean warChestEnabled = TaxConfig.isWarChestEnabled();
        int numTabs = warChestEnabled ? 4 : 3; // 4 tabs if War Chest enabled, 3 otherwise
        int totalTabWidth = tabButtonWidth * numTabs + (numTabs - 1) * 5; // buttons + gaps
        int tabStartX = guiLeft + (GUI_WIDTH - totalTabWidth) / 2;

        this.coloniesTabButton = Button.builder(
                Component.literal("Colonies"),
                button -> switchToColonies())
                .bounds(tabStartX, guiTop + 25, tabButtonWidth, tabButtonHeight)
                .build();
        this.addRenderableWidget(coloniesTabButton);

        this.vassalsTabButton = Button.builder(
                Component.literal("Vassals"),
                button -> switchToVassals())
                .bounds(tabStartX + tabButtonWidth + 5, guiTop + 25, tabButtonWidth, tabButtonHeight)
                .build();
        this.addRenderableWidget(vassalsTabButton);

        this.permissionsTabButton = Button.builder(
                Component.literal("Officers"),
                button -> switchToPermissions())
                .bounds(tabStartX + (tabButtonWidth + 5) * 2, guiTop + 25, tabButtonWidth, tabButtonHeight)
                .build();
        this.addRenderableWidget(permissionsTabButton);

        // War Chest tab button - only show if feature is enabled
        if (warChestEnabled) {
            this.warChestTabButton = Button.builder(
                    Component.literal("War Chest"),
                    button -> switchToWarChest())
                    .bounds(tabStartX + (tabButtonWidth + 5) * 3, guiTop + 25, tabButtonWidth, tabButtonHeight)
                    .build();
            this.addRenderableWidget(warChestTabButton);
        }

        // Espionage tab button - only show if feature enabled
        if (TaxConfig.isSpySystemEnabled()) {
            int spyTabIndex = warChestEnabled ? 4 : 3;
            this.espionageTabButton = Button.builder(
                    Component.literal("Espionage"),
                    button -> switchToEspionage())
                    .bounds(tabStartX + (tabButtonWidth + 5) * spyTabIndex, guiTop + 25, tabButtonWidth,
                            tabButtonHeight)
                    .build();
            this.addRenderableWidget(espionageTabButton);
        }

        // Bottom action buttons - centered and properly spaced with proper margins
        int buttonHeight = 20;
        int buttonMargin = 30; // Larger margin to align with scroll area
        int totalButtonsWidth = 55 + 50 + 60 + 70 + 65 + 20; // Buttons + gaps
        int buttonAreaWidth = GUI_WIDTH - (buttonMargin * 2);
        int buttonSpacing = (buttonAreaWidth - totalButtonsWidth + 20) / 6; // Distribute remaining space
        int buttonStartX = guiLeft + buttonMargin;

        this.refreshButton = Button.builder(
                Component.literal("Refresh"),
                button -> requestColonyData())
                .bounds(buttonStartX, guiTop + GUI_HEIGHT - 28, 55, buttonHeight)
                .build();
        this.addRenderableWidget(refreshButton);

        this.claimSelectedButton = Button.builder(
                Component.literal("Claim"),
                button -> {
                    if (selectedColony != null && selectedColony.canClaimTax()) {
                        NetworkHandler.sendToServer(new ClaimTaxPacket(selectedColony.getColonyId(), -1));
                        selectedColony = null;
                        updateButtonVisibility();
                    }
                })
                .bounds(buttonStartX + 55 + buttonSpacing, guiTop + GUI_HEIGHT - 28, 50, buttonHeight)
                .build();
        this.claimSelectedButton.visible = false;
        this.addRenderableWidget(claimSelectedButton);

        this.payDebtButton = Button.builder(
                Component.literal("Pay Debt"),
                button -> {
                    if (selectedColony != null && selectedColony.getDebtAmount() > 0) {
                        NetworkHandler.sendToServer(new PayTaxDebtPacket(selectedColony.getColonyId()));
                        selectedColony = null;
                        updateButtonVisibility();
                    }
                })
                .bounds(buttonStartX + 55 + 50 + buttonSpacing * 2, guiTop + GUI_HEIGHT - 28, 60, buttonHeight)
                .build();
        this.payDebtButton.visible = false;
        this.addRenderableWidget(payDebtButton);

        this.endVassalButton = Button.builder(
                Component.literal("End Vassal"),
                button -> {
                    if (selectedColony != null && selectedColony.isVassal()) {
                        NetworkHandler.sendToServer(new EndVassalizationPacket(selectedColony.getColonyId()));
                        selectedColony = null;
                        updateButtonVisibility();
                    }
                })
                .bounds(buttonStartX + 55 + 50 + 60 + buttonSpacing * 3, guiTop + GUI_HEIGHT - 28, 70, buttonHeight)
                .build();
        this.endVassalButton.visible = false;
        this.addRenderableWidget(endVassalButton);

        this.claimAllButton = Button.builder(
                Component.literal("Claim All"),
                button -> claimAllTaxes())
                .bounds(guiLeft + GUI_WIDTH - buttonMargin - 65, guiTop + GUI_HEIGHT - 28, 65, buttonHeight)
                .build();
        this.addRenderableWidget(claimAllButton);

        // Request initial data
        requestColonyData();

        // War Chest Elements
        int contentMargin = 50;
        int inputWidth = 100;
        int inputX = guiLeft + (GUI_WIDTH - inputWidth) / 2;
        int inputY = guiTop + 200;

        this.amountInput = new EditBox(this.font, inputX, inputY, inputWidth, 20, Component.literal("Amount"));
        this.amountInput.setMaxLength(9);
        this.amountInput.setFilter(s -> s.matches("\\d*")); // Only allow digits
        this.amountInput.setVisible(false);
        this.addRenderableWidget(amountInput);

        this.depositButton = Button.builder(Component.literal("Deposit"), button -> {
            if (selectedColony == null)
                return;
            try {
                String val = amountInput.getValue();
                if (val.isEmpty())
                    return;
                int amount = Integer.parseInt(val);
                if (amount <= 0)
                    return;

                NetworkHandler.sendToServer(new WarChestActionPacket(
                        selectedColony.getColonyId(),
                        WarChestActionPacket.ActionType.DEPOSIT,
                        amount));
                amountInput.setValue(""); // Clear input
            } catch (NumberFormatException ignored) {
            }
        }).bounds(inputX - 60, inputY + 25, 60, 20).build();
        this.depositButton.visible = false;
        this.addRenderableWidget(depositButton);

        this.withdrawButton = Button.builder(Component.literal("Withdraw"), button -> {
            if (selectedColony == null)
                return;
            try {
                String val = amountInput.getValue();
                if (val.isEmpty())
                    return;
                int amount = Integer.parseInt(val);
                if (amount <= 0)
                    return;

                NetworkHandler.sendToServer(new WarChestActionPacket(
                        selectedColony.getColonyId(),
                        WarChestActionPacket.ActionType.WITHDRAW,
                        amount));
                amountInput.setValue(""); // Clear input
            } catch (NumberFormatException ignored) {
            }
        }).bounds(inputX + inputWidth, inputY + 25, 70, 20).build();
        this.withdrawButton.visible = false;
        this.addRenderableWidget(withdrawButton);
    }

    private void requestColonyData() {
        NetworkHandler.sendToServer(new RequestColonyDataPacket());
        // Also clear officer data when refreshing to ensure fresh data
        officerData.clear();
    }

    private void switchToColonies() {
        showingVassals = false;
        showingPermissions = false;
        showingWarChest = false;
        scrollOffset = 0;
        setWarChestElementsVisible(false);
    }

    private void switchToVassals() {
        showingVassals = true;
        showingPermissions = false;
        showingWarChest = false;
        vassalScrollOffset = 0;
        setWarChestElementsVisible(false);
    }

    private void switchToPermissions() {
        showingVassals = false;
        showingPermissions = true;
        showingWarChest = false;
        scrollOffset = 0;
        setWarChestElementsVisible(false);

        // Request officer data for currently selected colony
        if (selectedColony != null) {
            NetworkHandler.sendToServer(new RequestOfficerDataPacket(selectedColony.getColonyId()));
        }
    }

    private void claimAllTaxes() {
        for (ColonyTaxData colony : colonies) {
            if (colony.getTaxBalance() > 0 && colony.canClaimTax()) {
                NetworkHandler.CHANNEL.sendToServer(new ClaimTaxPacket(colony.getColonyId(), -1)); // -1 = claim all
            }
        }
        // Refresh data after claiming
        requestColonyData();
    }

    @Override
    public void render(@javax.annotation.Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Don't call renderBackground() as it interferes with our custom texture

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        // Draw backgroundmenu.png texture with proper scaling for large GUIs (726x484)
        try {
            final int TEXTURE_WIDTH = 256;
            final int TEXTURE_HEIGHT = 256;

            // For very large GUIs like 726x484, we need to use a different approach
            // to prevent width compression and maintain proper aspect ratio

            // For large GUIs like 726x484, always use the tiling approach to maintain
            // quality
            renderTiledBackground(guiGraphics, guiLeft, guiTop, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        } catch (Exception e) {
            // Fallback: draw a simple colored background if texture fails
            guiGraphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF2D2D30);
            guiGraphics.fill(guiLeft + 2, guiTop + 2, guiLeft + GUI_WIDTH - 2, guiTop + GUI_HEIGHT - 2, 0xFF1E1E1E);
        }

        // Draw title with enhanced styling
        Component title = Component.translatable("gui.minecolonytax.tax_management.title");
        guiGraphics.drawCenteredString(this.font, title, guiLeft + GUI_WIDTH / 2, guiTop + 6, COLOR_WHITE);

        // Highlight active tab - centered positioning
        int tabButtonWidth = 70;
        boolean warChestEnabled = TaxConfig.isWarChestEnabled();
        boolean spyEnabled = TaxConfig.isSpySystemEnabled();
        int numTabs = 3;
        if (warChestEnabled)
            numTabs++;
        if (spyEnabled)
            numTabs++;
        int totalTabWidth = tabButtonWidth * numTabs + (numTabs - 1) * 5;
        int tabStartX = guiLeft + (GUI_WIDTH - totalTabWidth) / 2;

        if (showingWarChest && warChestEnabled) {
            guiGraphics.fill(tabStartX + (tabButtonWidth + 5) * 3, guiTop + 47,
                    tabStartX + (tabButtonWidth + 5) * 3 + tabButtonWidth, guiTop + 49, COLOR_GOLD);
        } else if (showingPermissions) {
            guiGraphics.fill(tabStartX + (tabButtonWidth + 5) * 2, guiTop + 47,
                    tabStartX + (tabButtonWidth + 5) * 2 + tabButtonWidth, guiTop + 49, COLOR_GOLD);
        } else if (showingVassals) {
            guiGraphics.fill(tabStartX + tabButtonWidth + 5, guiTop + 47,
                    tabStartX + tabButtonWidth + 5 + tabButtonWidth, guiTop + 49, COLOR_GOLD);
        } else {
            guiGraphics.fill(tabStartX, guiTop + 47, tabStartX + tabButtonWidth, guiTop + 49, COLOR_GOLD);
        }

        // Draw appropriate content based on active tab
        if (showingWarChest) {
            renderWarChest(guiGraphics, guiLeft, guiTop, mouseX, mouseY);
        } else if (showingEspionage) {
            renderEspionage(guiGraphics, guiLeft, guiTop, mouseX, mouseY);
        } else if (showingPermissions) {
            renderPermissionsManagement(guiGraphics, guiLeft, guiTop, mouseX, mouseY);
        } else if (showingVassals) {
            renderVassalList(guiGraphics, guiLeft, guiTop, mouseX, mouseY);
            if (vassalData.size() > maxVisibleVassals) {
                renderVassalScrollIndicator(guiGraphics, guiLeft, guiTop);
            }
        } else {
            renderColonyList(guiGraphics, guiLeft, guiTop, mouseX, mouseY);
            if (colonies.size() > maxVisibleColonies) {
                renderScrollIndicator(guiGraphics, guiLeft, guiTop);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * Renders a tiled background for very large GUIs to prevent compression
     * artifacts.
     * This method tiles the texture across the GUI area while maintaining proper
     * borders.
     */
    private void renderTiledBackground(GuiGraphics guiGraphics, int guiLeft, int guiTop, int textureWidth,
            int textureHeight) {
        // Strategy: Use a hybrid approach for large GUIs
        // 1. Stretch the texture for the main area
        // 2. Use proper edge handling to prevent compression artifacts

        // For a 726x484 GUI with 256x256 texture, we'll use intelligent stretching
        // that maintains the texture's aspect ratio in key areas

        // Draw the stretched background
        guiGraphics.pose().pushPose();

        // Calculate scale factors
        float scaleX = (float) GUI_WIDTH / textureWidth;
        float scaleY = (float) GUI_HEIGHT / textureHeight;

        // Apply scaling and draw texture at original size
        guiGraphics.pose().translate(guiLeft, guiTop, 0);
        guiGraphics.pose().scale(scaleX, scaleY, 1.0f);

        // Draw the texture at 0,0 after scaling (it will be positioned correctly)
        guiGraphics.blit(BACKGROUND_TEXTURE, 0, 0, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);

        guiGraphics.pose().popPose();

        // Optional: Add subtle borders or overlays to enhance the appearance
        // Draw a subtle border to define the GUI boundaries
        int borderColor = 0x40000000; // Semi-transparent black
        guiGraphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + 1, borderColor); // Top
        guiGraphics.fill(guiLeft, guiTop + GUI_HEIGHT - 1, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, borderColor); // Bottom
        guiGraphics.fill(guiLeft, guiTop, guiLeft + 1, guiTop + GUI_HEIGHT, borderColor); // Left
        guiGraphics.fill(guiLeft + GUI_WIDTH - 1, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, borderColor); // Right
    }

    private void renderColonyList(GuiGraphics guiGraphics, int guiLeft, int guiTop, int mouseX, int mouseY) {
        Font font = this.font;
        int startY = guiTop + 50; // Start below tab buttons
        int entryHeight = 35; // Increased spacing between entries

        // Center the content area within the parchment scroll - align with inner scroll
        // area
        int contentMargin = 50; // Larger margin to align with scroll's inner area
        int contentX = guiLeft + contentMargin;
        int contentWidth = GUI_WIDTH - (contentMargin * 2);

        for (int i = 0; i < Math.min(maxVisibleColonies, colonies.size() - scrollOffset); i++) {
            ColonyTaxData colony = colonies.get(i + scrollOffset);
            int entryY = startY + i * entryHeight;

            // Background for entry with card-like styling - centered within content area
            boolean isHovered = mouseX >= contentX && mouseX < contentX + contentWidth &&
                    mouseY >= entryY && mouseY < entryY + entryHeight - 2;

            // Different colors for owned vs managed colonies with selection highlighting
            boolean isSelected = selectedColony != null && selectedColony.getColonyId() == colony.getColonyId();
            int bgColor, borderColor;

            if (isSelected) {
                // Selected colony gets bright highlighting
                bgColor = 0x5A4A3A;
                borderColor = COLOR_GOLD;
            } else if (colony.isOwner()) {
                // Owner colonies get golden styling
                bgColor = isHovered ? 0x4A3728 : (i % 2 == 0 ? 0x3A2B1E : 0x322517);
                borderColor = isHovered ? COLOR_GOLD : 0x8B6914;
            } else {
                // Manager colonies get standard styling
                bgColor = isHovered ? 0x404040 : (i % 2 == 0 ? 0x303030 : 0x2A2A2A);
                borderColor = isHovered ? COLOR_GOLD : 0x505050;
            }

            // Draw card background with border - centered
            guiGraphics.fill(contentX, entryY, contentX + contentWidth, entryY + entryHeight - 2, borderColor);
            guiGraphics.fill(contentX + 1, entryY + 1, contentX + contentWidth - 1, entryY + entryHeight - 3, bgColor);

            // Colony name with owner indicator and selection status
            String colonyName = colony.getColonyName();
            if (colony.isOwner()) {
                colonyName = "★ " + colonyName; // Add star for owned colonies
            }
            if (isSelected) {
                colonyName = "▶ " + colonyName; // Add arrow for selected colony
            }
            if (colonyName.length() > 18) {
                colonyName = colonyName.substring(0, 15) + "...";
            }

            // Status indicator
            int statusColor = getColonyStatusColor(colony);
            String statusText = getColonyStatusText(colony);
            if (isSelected) {
                statusText = "SELECTED - " + statusText;
                statusColor = COLOR_GOLD;
            }

            guiGraphics.drawString(font, colonyName, contentX + 5, entryY + 2, COLOR_WHITE);
            guiGraphics.drawString(font, statusText, contentX + 5, entryY + 11, statusColor);

            // Tax info (right side)
            String taxText;
            int taxColor;
            if (colony.hasDebt()) {
                taxText = "DEBT: " + colony.getDebtAmount();
                taxColor = COLOR_RED;
            } else {
                taxText = colony.getTaxBalance() + " / " + colony.getMaxTaxRevenue();
                taxColor = COLOR_GOLD;
            }
            String buildingText = "B:" + colony.getBuildingCount() + " G:" + colony.getGuardCount();
            String revenueText = "Approx. " + colony.getApproximateRevenuePerInterval() + " $/ Interval";

            int rightX = contentX + contentWidth - 5;
            guiGraphics.drawString(font, taxText, rightX - font.width(taxText), entryY + 2, taxColor);
            guiGraphics.drawString(font, buildingText, rightX - font.width(buildingText), entryY + 11, COLOR_GRAY);
            guiGraphics.drawString(font, revenueText, rightX - font.width(revenueText), entryY + 20, COLOR_LIGHT_GRAY);

            // Enhanced vassal indicator with modern styling
            if (colony.isVassal()) {
                String vassalText = "Vassal (" + colony.getVassalTributeRate() + "%)";
                int vassalTextWidth = font.width(vassalText);
                int vassalBgX = contentX + 100;
                int vassalBgY = entryY + 1;

                // Draw background badge for vassal status
                guiGraphics.fill(vassalBgX - 2, vassalBgY, vassalBgX + vassalTextWidth + 2, vassalBgY + 9,
                        COLOR_ORANGE);
                guiGraphics.drawString(font, vassalText, vassalBgX, vassalBgY + 1, COLOR_WHITE);
            } else if (colony.hasVassals()) {
                String overlordText = "Overlord (" + colony.getVassalCount() + ")";
                int overlordTextWidth = font.width(overlordText);
                int overlordBgX = contentX + 100;
                int overlordBgY = entryY + 1;
                guiGraphics.fill(overlordBgX - 2, overlordBgY, overlordBgX + overlordTextWidth + 2, overlordBgY + 9,
                        COLOR_GREEN);
                guiGraphics.drawString(font, overlordText, overlordBgX, overlordBgY + 1, COLOR_WHITE);
            }

            // Tax Policy indicator (below status text)
            if (TaxConfig.isTaxPoliciesEnabled()) {
                String policyName = colony.getTaxPolicy();
                TaxPolicy policy = TaxPolicy.fromString(policyName);
                if (policy != null && policy != TaxPolicy.NORMAL) {
                    String policyText = policy.getDisplayName();
                    guiGraphics.drawString(font, "Policy: " + policyText, contentX + 5, entryY + 20,
                            getPolicyColor(policy));
                }
            }
        }

        // No colonies message
        if (colonies.isEmpty()) {
            Component noColonies = Component.translatable("gui.minecolonytax.no_colonies");
            int textWidth = font.width(noColonies);
            guiGraphics.drawString(font, noColonies,
                    guiLeft + (GUI_WIDTH - textWidth) / 2,
                    guiTop + GUI_HEIGHT / 2, COLOR_GRAY);
        }

        // Tax Policy selector (when colony selected and feature enabled)
        if (selectedColony != null && TaxConfig.isTaxPoliciesEnabled() && selectedColony.isOwner()) {
            renderPolicySelector(guiGraphics, guiLeft, guiTop, mouseX, mouseY);
        }
    }

    private void renderPolicySelector(GuiGraphics guiGraphics, int guiLeft, int guiTop, int mouseX, int mouseY) {
        if (selectedColony == null)
            return;

        Font font = this.font;
        int contentMargin = 50;
        int contentX = guiLeft + contentMargin;
        int contentWidth = GUI_WIDTH - (contentMargin * 2);

        // Position below colony list
        int startY = guiTop + 50 + (maxVisibleColonies * 35) + 5;

        // Header
        guiGraphics.drawString(font, "Tax Policy:", contentX, startY, COLOR_LIGHT_GRAY);

        // Current policy
        String currentPolicyName = selectedColony.getTaxPolicy();
        TaxPolicy currentPolicy = TaxPolicy.fromString(currentPolicyName);
        if (currentPolicy == null)
            currentPolicy = TaxPolicy.NORMAL;

        String currentText = "Current: " + currentPolicy.getColorCode() + currentPolicy.getDisplayName();
        guiGraphics.drawString(font, currentText, contentX + 70, startY, COLOR_WHITE);

        // Policy buttons (4 compact buttons)
        int buttonY = startY + 12;
        int buttonWidth = 60;
        int buttonHeight = 12;
        int buttonSpacing = 5;

        TaxPolicy[] policies = TaxPolicy.values();
        for (int i = 0; i < policies.length; i++) {
            TaxPolicy policy = policies[i];
            int buttonX = contentX + (i * (buttonWidth + buttonSpacing));

            boolean isHovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth &&
                    mouseY >= buttonY && mouseY < buttonY + buttonHeight;
            boolean isCurrent = policy == currentPolicy;

            int bgColor = isCurrent ? 0xFF4A4A4A : (isHovered ? 0xFF3A3A3A : 0xFF2A2A2A);
            int borderColor = isCurrent ? COLOR_GOLD : (isHovered ? COLOR_LIGHT_GRAY : COLOR_GRAY);

            // Draw button
            guiGraphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, borderColor);
            guiGraphics.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, bgColor);

            // Button text
            String buttonText = policy.name().substring(0, Math.min(6, policy.name().length()));
            int textColor = getPolicyColor(policy);
            guiGraphics.drawCenteredString(font, buttonText, buttonX + buttonWidth / 2, buttonY + 2, textColor);

            // Store button bounds for click detection
            if (i == 0)
                selectedColony.setPermissionButtonBounds(buttonX, buttonY, buttonWidth, buttonHeight);
        }
    }

    private void renderScrollIndicator(GuiGraphics guiGraphics, int guiLeft, int guiTop) {
        int scrollBarX = guiLeft + GUI_WIDTH - 20; // Move scroll bar in slightly
        int scrollBarY = guiTop + 50;
        int scrollBarHeight = maxVisibleColonies * 28;

        // Enhanced scroll track with border
        guiGraphics.fill(scrollBarX - 1, scrollBarY - 1, scrollBarX + 8, scrollBarY + scrollBarHeight + 1,
                COLOR_BORDER);
        guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + 7, scrollBarY + scrollBarHeight, COLOR_BACKGROUND);

        // Enhanced scroll thumb with better styling
        int thumbHeight = Math.max(10, (maxVisibleColonies * scrollBarHeight) / colonies.size());
        int thumbY = scrollBarY
                + (scrollOffset * (scrollBarHeight - thumbHeight)) / Math.max(1, colonies.size() - maxVisibleColonies);

        // Draw thumb with gradient effect
        guiGraphics.fill(scrollBarX + 1, thumbY, scrollBarX + 6, thumbY + thumbHeight, COLOR_HEADER);
        guiGraphics.fill(scrollBarX + 2, thumbY + 1, scrollBarX + 5, thumbY + thumbHeight - 1, COLOR_BLUE);
    }

    private void renderVassalList(GuiGraphics guiGraphics, int guiLeft, int guiTop, int mouseX, int mouseY) {
        Font font = this.font;
        int startY = guiTop + 50; // Start below tab buttons
        int entryHeight = 30;

        // Center the content area within the parchment scroll - align with inner scroll
        // area
        int contentMargin = 50; // Larger margin to align with scroll's inner area
        int contentX = guiLeft + contentMargin;
        int contentWidth = GUI_WIDTH - (contentMargin * 2);

        // Section 1: Colonies you are vassalizing (your vassals)
        Component yourVassalsTitle = Component.literal("Your Vassals:");
        guiGraphics.drawString(font, yourVassalsTitle, contentX, startY, COLOR_BLUE);
        int currentY = startY + 15;

        if (vassalData.isEmpty()) {
            Component noVassals = Component.literal("No vassals");
            guiGraphics.drawString(font, noVassals, contentX, currentY, COLOR_GRAY);
            currentY += 15;
        } else {
            int visibleVassals = Math.min(maxVisibleVassals, vassalData.size() - vassalScrollOffset);
            for (int i = 0; i < visibleVassals; i++) {
                VassalIncomeData vassal = vassalData.get(i + vassalScrollOffset);
                int entryY = currentY + i * entryHeight;

                // Background for entry with card-like styling - centered
                boolean isHovered = mouseX >= contentX && mouseX < contentX + contentWidth &&
                        mouseY >= entryY && mouseY < entryY + entryHeight - 2;
                int bgColor = isHovered ? 0x404040 : (i % 2 == 0 ? 0x303030 : 0x2A2A2A);
                int borderColor = isHovered ? COLOR_GOLD : 0x505050;

                // Draw card background with border - centered
                guiGraphics.fill(contentX, entryY, contentX + contentWidth, entryY + entryHeight - 2, borderColor);
                guiGraphics.fill(contentX + 1, entryY + 1, contentX + contentWidth - 1, entryY + entryHeight - 3,
                        bgColor);

                // Vassal colony name
                String colonyName = vassal.getVassalColonyName();
                if (colonyName.length() > 16) {
                    colonyName = colonyName.substring(0, 13) + "...";
                }

                guiGraphics.drawString(font, colonyName, contentX + 5, entryY + 3, COLOR_WHITE);

                // Tribute info
                String tributeInfo = vassal.getTributeRate() + "% tribute";
                guiGraphics.drawString(font, tributeInfo, contentX + 5, entryY + 13, COLOR_ORANGE);

                // Tribute estimation info
                int estimatedNextTribute = (int) (vassal.getTributeOwed() * (vassal.getTributeRate() / 100.0));
                String estimationText = "Next Est: " + estimatedNextTribute;
                int estimationWidth = font.width(estimationText);
                guiGraphics.drawString(font, estimationText, contentX + contentWidth - 5 - estimationWidth, entryY + 2,
                        COLOR_YELLOW);

                // Last collected tribute amount and time
                String lastText = "Last: " + vassal.getLastTribute() + " (" + vassal.getFormattedLastPayment() + ")";
                int lastTextWidth = font.width(lastText);
                guiGraphics.drawString(font, lastText, contentX + contentWidth - 5 - lastTextWidth, entryY + 13,
                        COLOR_GRAY);

                // Show auto-collection status instead of manual button
                String autoText = "Auto-collected at tax intervals";
                int autoTextWidth = font.width(autoText);
                int autoX = contentX + contentWidth - 5 - autoTextWidth;
                guiGraphics.drawString(font, autoText, autoX, entryY + 22, COLOR_LIGHT_GREEN);
            }
            currentY += visibleVassals * entryHeight;
        }

        // Section 2: Colonies you are vassal of (your overlords)
        currentY += 20;
        Component vassalOfTitle = Component.literal("You are vassal of:");
        guiGraphics.drawString(font, vassalOfTitle, contentX, currentY, COLOR_RED);
        currentY += 15;

        // Find colonies where the current player is a vassal
        List<ColonyTaxData> playerVassalColonies = colonies.stream()
                .filter(ColonyTaxData::isVassal)
                .collect(java.util.stream.Collectors.toList());

        if (playerVassalColonies.isEmpty()) {
            Component noOverlords = Component.literal("You are independent");
            guiGraphics.drawString(font, noOverlords, contentX, currentY, COLOR_GRAY);
        } else {
            for (int i = 0; i < playerVassalColonies.size() && i < 3; i++) {
                ColonyTaxData vassalColony = playerVassalColonies.get(i);
                int entryY = currentY + i * 25;

                // Background
                boolean isSelected = selectedColony != null
                        && selectedColony.getColonyId() == vassalColony.getColonyId();
                boolean isHovered = mouseX >= contentX && mouseX < contentX + contentWidth &&
                        mouseY >= entryY && mouseY < entryY + 23;
                int bgColor = isSelected ? COLOR_GOLD : (isHovered ? 0x404040 : 0x303030);

                guiGraphics.fill(contentX, entryY, contentX + contentWidth, entryY + 23, bgColor);

                // Colony info
                String colonyText = vassalColony.getColonyName() + " (paying " + vassalColony.getVassalTributeRate()
                        + "%)";
                if (colonyText.length() > 25) {
                    colonyText = colonyText.substring(0, 22) + "...";
                }
                guiGraphics.drawString(font, colonyText, contentX + 5, entryY + 3, COLOR_WHITE);

                // End vassalage button
                if (isSelected) {
                    int buttonX = guiLeft + GUI_WIDTH - 70;
                    int buttonY = entryY + 10;
                    int buttonWidth = 60;
                    int buttonHeight = 10;

                    boolean isButtonHovered = mouseX >= buttonX && mouseX < buttonX + buttonWidth &&
                            mouseY >= buttonY && mouseY < buttonY + buttonHeight;

                    int buttonColor = isButtonHovered ? 0x800000 : 0x600000;
                    guiGraphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor);

                    String endText = "End Vassalage";
                    guiGraphics.drawString(font, endText,
                            buttonX + (buttonWidth - font.width(endText)) / 2,
                            buttonY + 1, COLOR_WHITE);

                    // Store button bounds
                    vassalColony.setClaimButtonBounds(buttonX, buttonY, buttonWidth, buttonHeight);
                }

                guiGraphics.drawString(font, "Click to select", contentX + 5, entryY + 13, COLOR_LIGHT_GRAY);
            }
        }
    }

    private void renderVassalScrollIndicator(GuiGraphics guiGraphics, int guiLeft, int guiTop) {
        int scrollBarX = guiLeft + GUI_WIDTH - 20; // Move scroll bar in slightly
        int scrollBarY = guiTop + 50;
        int scrollBarHeight = maxVisibleVassals * 30;

        // Enhanced scroll track with border
        guiGraphics.fill(scrollBarX - 1, scrollBarY - 1, scrollBarX + 8, scrollBarY + scrollBarHeight + 1,
                COLOR_BORDER);
        guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + 7, scrollBarY + scrollBarHeight, COLOR_BACKGROUND);

        // Enhanced scroll thumb with better styling
        int thumbHeight = Math.max(10, (maxVisibleVassals * scrollBarHeight) / vassalData.size());
        int thumbY = scrollBarY + (vassalScrollOffset * (scrollBarHeight - thumbHeight))
                / Math.max(1, vassalData.size() - maxVisibleVassals);

        // Draw thumb with gradient effect
        guiGraphics.fill(scrollBarX + 1, thumbY, scrollBarX + 6, thumbY + thumbHeight, COLOR_HEADER);
        guiGraphics.fill(scrollBarX + 2, thumbY + 1, scrollBarX + 5, thumbY + thumbHeight - 1, COLOR_BLUE);
    }

    private int getColonyStatusColor(ColonyTaxData colony) {
        if (!colony.canClaimTax()) {
            if (colony.isAtWar())
                return COLOR_RED;
            if (colony.isBeingRaided())
                return COLOR_RED;
            return COLOR_YELLOW; // Other restrictions
        }

        if (colony.getTaxBalance() < 0)
            return COLOR_RED; // In debt
        if (colony.getTaxBalance() >= colony.getMaxTaxRevenue() * 0.9)
            return COLOR_YELLOW; // Near max
        return COLOR_GREEN; // Healthy
    }

    private String getColonyStatusText(ColonyTaxData colony) {
        if (colony.isAtWar())
            return "At War";
        if (colony.isBeingRaided())
            return "Under Raid";
        if (!colony.canClaimTax())
            return "Restricted";
        if (colony.getTaxBalance() < 0)
            return "In Debt";
        return "Healthy";
    }

    private int getPolicyColor(TaxPolicy policy) {
        return switch (policy) {
            case LOW -> COLOR_GREEN;
            case HIGH -> COLOR_ORANGE;
            case WAR_ECONOMY -> COLOR_RED;
            default -> COLOR_GRAY;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Handle scrolling in Officers Tab
        if (showingPermissions && !officerData.isEmpty()) {
            int maxOfficersVisible = 8;
            int maxScrollOffset = Math.max(0, officerData.size() - maxOfficersVisible);

            if (delta > 0) {
                // Scroll up
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else if (delta < 0) {
                // Scroll down
                scrollOffset = Math.min(maxScrollOffset, scrollOffset + 1);
            }
            return true;
        }

        // Handle scrolling in Vassals Tab
        if (showingVassals) {
            if (vassalData.size() > maxVisibleVassals) {
                vassalScrollOffset = Mth.clamp(vassalScrollOffset - (int) delta, 0,
                        vassalData.size() - maxVisibleVassals);
                return true;
            }
        } else {
            if (colonies.size() > maxVisibleColonies) {
                scrollOffset = Mth.clamp(scrollOffset - (int) delta, 0, colonies.size() - maxVisibleColonies);
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            int guiLeft = (this.width - GUI_WIDTH) / 2;
            int guiTop = (this.height - GUI_HEIGHT) / 2;

            if (showingVassals) {
                // Note: Manual tribute collection removed - tributes are now auto-collected
                // during tax intervals

                // Handle clicks on colonies you are vassal of (for selection and end vassalage)
                List<ColonyTaxData> playerVassalColonies = colonies.stream()
                        .filter(ColonyTaxData::isVassal)
                        .collect(java.util.stream.Collectors.toList());

                int startY = guiTop + 50 + 15; // Account for "Your Vassals" section
                if (!vassalData.isEmpty()) {
                    startY += vassalData.size() * 30 + 20; // Account for vassal entries
                }
                startY += 35; // Account for "You are vassal of" title

                for (int i = 0; i < playerVassalColonies.size() && i < 3; i++) {
                    ColonyTaxData vassalColony = playerVassalColonies.get(i);
                    int entryY = startY + i * 25;

                    // Check for colony selection click - centered content
                    int contentMargin = 50;
                    int contentX = guiLeft + contentMargin;
                    int contentWidth = GUI_WIDTH - (contentMargin * 2);

                    if (mouseX >= contentX && mouseX < contentX + contentWidth &&
                            mouseY >= entryY && mouseY < entryY + 23) {

                        // Check if clicking the end vassalage button
                        boolean isSelected = selectedColony != null
                                && selectedColony.getColonyId() == vassalColony.getColonyId();
                        if (isSelected && vassalColony.isClaimButtonClicked(mouseX, mouseY)) {
                            // End vassalage
                            NetworkHandler.sendToServer(new EndVassalizationPacket(vassalColony.getColonyId()));
                            selectedColony = null;
                            updateButtonVisibility();
                            requestColonyData(); // Refresh data
                            return true;
                        } else {
                            // Select/deselect colony
                            if (isSelected) {
                                selectedColony = null;
                            } else {
                                selectedColony = vassalColony;
                            }
                            updateButtonVisibility();
                            return true;
                        }
                    }
                }
            } else if (showingPermissions) {
                // Permission toggle button clicks (if colony owner and colony selected)
                if (selectedColony != null && selectedColony.isOwner()) {
                    // Check default officer permission toggle
                    if (permissionToggleButton != null &&
                            mouseX >= permissionToggleButton.x
                            && mouseX < permissionToggleButton.x + permissionToggleButton.width &&
                            mouseY >= permissionToggleButton.y
                            && mouseY < permissionToggleButton.y + permissionToggleButton.height) {
                        // Toggle colony-wide permission and send to server
                        boolean newPermission = TaxPermissionManager
                                .toggleOfficerClaimPermission(selectedColony.getColonyId());
                        NetworkHandler.sendToServer(
                                new UpdateTaxPermissionPacket(selectedColony.getColonyId(), newPermission));
                        return true;
                    }

                    // Check individual officer permission toggles
                    if (officerToggleButtons != null) {
                        for (Map.Entry<Integer, Rectangle> entry : officerToggleButtons.entrySet()) {
                            Rectangle toggleButton = entry.getValue();
                            if (mouseX >= toggleButton.x && mouseX < toggleButton.x + toggleButton.width &&
                                    mouseY >= toggleButton.y && mouseY < toggleButton.y + toggleButton.height) {

                                int displayIndex = entry.getKey();
                                // Convert display index to actual officer index considering scroll offset
                                int actualOfficerIndex = displayIndex + scrollOffset;
                                if (actualOfficerIndex < officerData.size()) {
                                    OfficerData officer = officerData.get(actualOfficerIndex);
                                    // Toggle individual player permission
                                    boolean newPermission = TaxPermissionManager.togglePlayerClaimPermission(
                                            selectedColony.getColonyId(),
                                            officer.getPlayerId(),
                                            true // is officer
                                    );
                                    // Send individual player permission update to server
                                    NetworkHandler.sendToServer(new UpdatePlayerTaxPermissionPacket(
                                            selectedColony.getColonyId(),
                                            officer.getPlayerId(),
                                            newPermission));
                                    return true;
                                }
                            }
                        }
                    }
                }
            } else if (showingEspionage) {
                int contentMargin = 50;
                int contentX = guiLeft + contentMargin;
                int contentWidth = GUI_WIDTH - (contentMargin * 2);
                int currentY = guiTop + 50 + 15; // Start of active missions

                if (spyMissions.isEmpty()) {
                    currentY += 20;
                } else {
                    for (net.machiavelli.minecolonytax.gui.data.SpyMissionData mission : spyMissions) {
                        int btnX = contentX + contentWidth - 45;
                        if (mouseX >= btnX && mouseX <= btnX + 40 && mouseY >= currentY && mouseY <= currentY + 15) {
                            net.machiavelli.minecolonytax.network.NetworkHandler
                                    .sendToServer(new net.machiavelli.minecolonytax.network.packets.RecallSpyPacket(
                                            mission.getMissionId()));
                            return true;
                        }
                        currentY += 25;
                    }
                }

                currentY += 25; // Skip separator and "Deploy New Spy"

                // Target cycle button
                int tgtBtnX = contentX + contentWidth - 25;
                if (mouseX >= tgtBtnX && mouseX <= tgtBtnX + 20 && mouseY >= currentY && mouseY <= currentY + 10) {
                    if (!colonies.isEmpty()) {
                        selectedTargetColonyIndex = (selectedTargetColonyIndex + 1) % colonies.size();
                    }
                    return true;
                }
                currentY += 15;

                // Type cycle button
                int typBtnX = contentX + contentWidth - 25;
                if (mouseX >= typBtnX && mouseX <= typBtnX + 20 && mouseY >= currentY && mouseY <= currentY + 10) {
                    selectedMissionTypeIndex = (selectedMissionTypeIndex + 1) % MISSION_TYPES.length;
                    return true;
                }
                currentY += 15;

                // Deploy button
                int dBtnX = contentX + 10;
                if (mouseX >= dBtnX && mouseX <= dBtnX + 60 && mouseY >= currentY && mouseY <= currentY + 15) {
                    if (!colonies.isEmpty() && selectedTargetColonyIndex >= 0
                            && selectedTargetColonyIndex < colonies.size()) {
                        ColonyTaxData target = colonies.get(selectedTargetColonyIndex);
                        String type = MISSION_TYPES[selectedMissionTypeIndex];
                        net.machiavelli.minecolonytax.network.NetworkHandler.sendToServer(
                                new net.machiavelli.minecolonytax.network.packets.DeploySpyPacket(target.getColonyId(),
                                        type));
                    }
                    return true;
                }
            } else {
                // Colony selection and action buttons
                int startY = guiTop + 50;
                int entryHeight = 35;

                for (int i = 0; i < Math.min(maxVisibleColonies, colonies.size() - scrollOffset); i++) {
                    ColonyTaxData colony = colonies.get(i + scrollOffset);
                    int entryY = startY + i * entryHeight;

                    // Check if clicking on action buttons first
                    if (colony.isClaimButtonClicked(mouseX, mouseY)) {
                        if (colony.hasDebt()) {
                            // Pay debt button clicked - TODO: implement when packet available
                        } else if (colony.getTaxBalance() > 0 && colony.canClaimTax()) {
                            // Claim tax button clicked
                            NetworkHandler.CHANNEL.sendToServer(new ClaimTaxPacket(colony.getColonyId(), -1));
                        }
                        requestColonyData(); // Refresh after action
                        return true;
                    }
                }

                // Check for policy button clicks (if colony selected and feature enabled)
                if (selectedColony != null && TaxConfig.isTaxPoliciesEnabled() && selectedColony.isOwner()) {
                    int policyY = guiTop + 50 + (maxVisibleColonies * 35) + 5 + 12;
                    int buttonWidth = 60;
                    int buttonHeight = 12;
                    int buttonSpacing = 5;
                    int contentMargin = 50;
                    int contentX = guiLeft + contentMargin;

                    TaxPolicy[] policies = TaxPolicy.values();
                    for (int i = 0; i < policies.length; i++) {
                        int buttonX = contentX + (i * (buttonWidth + buttonSpacing));
                        if (mouseX >= buttonX && mouseX < buttonX + buttonWidth &&
                                mouseY >= policyY && mouseY < policyY + buttonHeight) {
                            // Send policy change to server
                            NetworkHandler.sendToServer(new SetTaxPolicyPacket(
                                    selectedColony.getColonyId(),
                                    policies[i].name()));
                            requestColonyData(); // Refresh to show new policy
                            return true;
                        }
                    }
                }

                // Check if clicking on colony row for selection - updated for centered content
                for (int i = 0; i < Math.min(maxVisibleColonies, colonies.size() - scrollOffset); i++) {
                    ColonyTaxData colony = colonies.get(i + scrollOffset);
                    int entryY = startY + i * entryHeight;

                    // Check clicking on colony row for selection - updated for centered content
                    int contentMargin = 50;
                    int contentX = guiLeft + contentMargin;
                    int contentWidth = GUI_WIDTH - (contentMargin * 2);

                    if (mouseX >= contentX && mouseX < contentX + contentWidth &&
                            mouseY >= entryY && mouseY < entryY + entryHeight - 2) {
                        // Toggle selection
                        if (selectedColony != null && selectedColony.getColonyId() == colony.getColonyId()) {
                            selectedColony = null;
                            // Clear officer data when deselecting colony
                            officerData.clear();
                        } else {
                            selectedColony = colony;
                            // Request officer data if Officers tab is currently shown
                            if (showingPermissions) {
                                NetworkHandler.sendToServer(new RequestOfficerDataPacket(selectedColony.getColonyId()));
                            }
                        }
                        updateButtonVisibility();
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void updateSpyData(List<net.machiavelli.minecolonytax.gui.data.SpyMissionData> missions) {
        this.spyMissions.clear();
        this.spyMissions.addAll(missions);
        // Reset selection if out of bounds or data changed
        if (this.spyMissions.isEmpty()) {
            this.selectedMissionTypeIndex = 0;
            this.selectedTargetColonyIndex = 0;
        }
    }

    public void updateColonyData(List<ColonyTaxData> newData) {
        this.colonies.clear();
        this.colonies.addAll(newData);

        // Sort colonies: owned colonies first, then managers
        this.colonies.sort((a, b) -> {
            if (a.isOwner() && !b.isOwner())
                return -1;
            if (!a.isOwner() && b.isOwner())
                return 1;
            return a.getColonyName().compareToIgnoreCase(b.getColonyName());
        });

        scrollOffset = 0; // Reset scroll when data updates
        selectedColony = null; // Clear selection
        updateButtonVisibility();
    }

    public void updateVassalData(List<VassalIncomeData> newVassalData) {
        this.vassalData.clear();
        this.vassalData.addAll(newVassalData);
        vassalScrollOffset = 0; // Reset scroll when data updates
    }

    public void updateOfficerData(List<OfficerData> newOfficerData, int colonyId) {
        this.officerData.clear();
        this.officerData.addAll(newOfficerData);
    }

    private void updateButtonVisibility() {
        if (claimSelectedButton != null && payDebtButton != null && endVassalButton != null) {
            boolean hasSelection = selectedColony != null;
            boolean isColonyView = !showingVassals && !showingPermissions;

            // Only show buttons on colony view
            claimSelectedButton.visible = isColonyView && hasSelection && selectedColony.canClaimTax()
                    && !selectedColony.isVassal();
            payDebtButton.visible = isColonyView && hasSelection && selectedColony.getDebtAmount() > 0;
            endVassalButton.visible = isColonyView && hasSelection && selectedColony.isVassal();

            // Hide all selection buttons on other tabs
            if (!isColonyView) {
                claimSelectedButton.visible = false;
                payDebtButton.visible = false;
                endVassalButton.visible = false;
            }
        }
    }

    private void setWarChestElementsVisible(boolean visible) {
        if (amountInput != null)
            amountInput.setVisible(visible);
        if (depositButton != null)
            depositButton.visible = visible;
        if (withdrawButton != null)
            withdrawButton.visible = visible;
    }

    private void renderPermissionsManagement(GuiGraphics guiGraphics, int guiLeft, int guiTop, int mouseX, int mouseY) {
        if (selectedColony == null) {
            guiGraphics.drawCenteredString(font, "No colony selected.", guiLeft + GUI_WIDTH / 2, guiTop + 60,
                    COLOR_RED);
            return;
        }

        // Center the content area within the parchment scroll - align with inner scroll
        // area
        int contentMargin = 50; // Same margin as other tabs for consistency
        int contentX = guiLeft + contentMargin;
        int contentWidth = GUI_WIDTH - (contentMargin * 2);

        // Start below tab buttons with safe clearance
        int currentY = guiTop + 50;

        // Colony info section - moved lower and made more compact
        String colonyName = selectedColony.getColonyName();
        String colonyInfo = String.format("%s (ID: %d)", colonyName, selectedColony.getColonyId());

        // Truncate colony name if too long to fit in available space
        int maxColonyWidth = contentWidth - 10;
        if (font.width(colonyInfo) > maxColonyWidth) {
            String shortName = colonyName;
            while (font.width(String.format("%s... (ID: %d)", shortName, selectedColony.getColonyId())) > maxColonyWidth
                    && shortName.length() > 3) {
                shortName = shortName.substring(0, shortName.length() - 1);
            }
            colonyInfo = String.format("%s... (ID: %d)", shortName, selectedColony.getColonyId());
        }

        guiGraphics.drawString(font, colonyInfo, contentX, currentY, COLOR_WHITE);
        currentY += 18;

        // Default permission section
        boolean isOwner = selectedColony.isOwner();
        boolean officersCanClaim = TaxPermissionManager.canOfficersClaim(selectedColony.getColonyId());
        String permText = "Default: " + (officersCanClaim ? "ALLOW" : "BLOCK");
        int permColor = officersCanClaim ? COLOR_GREEN : COLOR_RED;

        guiGraphics.drawString(font, permText, contentX, currentY, permColor);

        if (isOwner) {
            // Toggle button positioned within content area
            int buttonX = contentX + contentWidth - 45;
            int buttonY = currentY - 2;
            int buttonWidth = 35;
            int buttonHeight = 12;

            // Store button bounds for click detection
            permissionToggleButton = new Rectangle(buttonX, buttonY, buttonWidth, buttonHeight);

            // Draw button
            int buttonColor = officersCanClaim ? 0xFF006600 : 0xFF660000;
            guiGraphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonColor);
            guiGraphics.drawCenteredString(font, "EDIT", buttonX + buttonWidth / 2, buttonY + 2, COLOR_WHITE);
        } else {
            String ownerOnlyText = "(Owner only)";
            int textX = contentX + contentWidth - font.width(ownerOnlyText);
            guiGraphics.drawString(font, ownerOnlyText, textX, currentY, COLOR_GRAY);
        }
        currentY += 18;

        // Help text - only for owners and made shorter
        if (isOwner) {
            guiGraphics.drawString(font, "Individual settings override default", contentX, currentY, COLOR_GRAY);
            currentY += 15;
        }

        // Officers section header
        guiGraphics.drawString(font, "Officers:", contentX, currentY, COLOR_LIGHT_GRAY);
        currentY += 15;

        // Officers list with scrolling support
        if (officerData.isEmpty()) {
            String message = selectedColony != null ? "Loading officers..." : "No officers found";
            guiGraphics.drawString(font, message, contentX, currentY, COLOR_GRAY);
        } else {
            renderOfficersList(guiGraphics, contentX, currentY, mouseX, mouseY, isOwner, contentWidth);
        }
    }

    private void renderOfficersList(GuiGraphics guiGraphics, int contentX, int startY, int mouseX, int mouseY,
            boolean isOwner, int contentWidth) {
        int maxOfficersVisible = 8; // Increased from 6
        int officerHeight = 16;
        int totalOfficers = officerData.size();

        // Clear previous button mappings to avoid stale data
        if (officerToggleButtons != null) {
            officerToggleButtons.clear();
        }

        // Calculate visible range based on scroll offset
        int startIndex = Math.max(0, scrollOffset);
        int endIndex = Math.min(totalOfficers, startIndex + maxOfficersVisible);

        // Draw officers
        for (int i = startIndex; i < endIndex; i++) {
            OfficerData officer = officerData.get(i);
            int displayIndex = i - startIndex;
            int officerY = startY + (displayIndex * (officerHeight + 2));

            renderOfficerEntry(guiGraphics, contentX, officerY, mouseX, mouseY, officer, i, isOwner, contentWidth);
        }

        // Draw scroll indicators if needed
        if (totalOfficers > maxOfficersVisible) {
            drawScrollIndicators(guiGraphics, contentX, startY, maxOfficersVisible * (officerHeight + 2), totalOfficers,
                    maxOfficersVisible, contentWidth);
        }
    }

    private void renderOfficerEntry(GuiGraphics guiGraphics, int contentX, int officerY, int mouseX, int mouseY,
            OfficerData officer, int officerIndex, boolean isOwner, int contentWidth) {
        int entryHeight = 16;

        // Officer entry background
        boolean entryHovered = mouseX >= contentX && mouseX < contentX + contentWidth &&
                mouseY >= officerY - 1 && mouseY < officerY + entryHeight - 1;

        if (entryHovered) {
            guiGraphics.fill(contentX, officerY - 1, contentX + contentWidth, officerY + entryHeight - 1, 0x22333333);
        }

        // Officer name and rank - allow longer names since we have space
        String officerText = officer.getPlayerName() + " [" + officer.getRank() + "]";
        int maxTextWidth = contentWidth - 50; // Leave space for toggle button
        if (font.width(officerText) > maxTextWidth) {
            // Truncate more intelligently - keep more of the name
            String playerName = officer.getPlayerName();
            String rank = officer.getRank();
            while (font.width(playerName + " [" + rank + "]") > maxTextWidth && playerName.length() > 3) {
                playerName = playerName.substring(0, playerName.length() - 1);
            }
            officerText = playerName + "... [" + rank + "]";
        }
        guiGraphics.drawString(font, officerText, contentX + 5, officerY, officer.getRankColor());

        // Owner indicator - owners always can claim
        if (officer.getRank().equals("Owner")) {
            String ownerText = "OWNER";
            int ownerTextX = contentX + contentWidth - font.width(ownerText) - 5;
            guiGraphics.drawString(font, ownerText, ownerTextX, officerY, COLOR_GREEN);
        } else if (isOwner) {
            // Individual permission toggle button for officers (only shown to owners)
            boolean canClaim = TaxPermissionManager.canPlayerClaimTax(
                    selectedColony.getColonyId(),
                    officer.getPlayerId(),
                    false,
                    true // is officer
            );

            // Position toggle button within content bounds
            int toggleX = contentX + contentWidth - 40; // 40px from right edge of content
            int toggleY = officerY - 1;
            int toggleWidth = 35;
            int toggleHeight = 12;

            // Store button bounds for click detection - using display index as identifier
            if (officerToggleButtons == null) {
                officerToggleButtons = new java.util.HashMap<>();
            }
            // Use display index (i) instead of actual officer index for button mapping
            int displayIndex = officerIndex - scrollOffset;
            officerToggleButtons.put(displayIndex, new Rectangle(toggleX, toggleY, toggleWidth, toggleHeight));

            // Draw toggle button
            int toggleColor = canClaim ? 0xFF006600 : 0xFF660000;
            guiGraphics.fill(toggleX, toggleY, toggleX + toggleWidth, toggleY + toggleHeight, toggleColor);
            String toggleText = canClaim ? "ON" : "OFF";
            guiGraphics.drawCenteredString(font, toggleText, toggleX + toggleWidth / 2, toggleY + 2, COLOR_WHITE);
        } else {
            // Show current permission state for non-owners
            boolean canClaim = TaxPermissionManager.canPlayerClaimTax(
                    selectedColony.getColonyId(),
                    officer.getPlayerId(),
                    false,
                    true // is officer
            );
            String permissionText = canClaim ? "ON" : "OFF";
            int statusColor = canClaim ? COLOR_GREEN : COLOR_RED;
            int permTextX = contentX + contentWidth - font.width(permissionText) - 5;
            guiGraphics.drawString(font, permissionText, permTextX, officerY, statusColor);
        }
    }

    private void drawScrollIndicators(GuiGraphics guiGraphics, int contentX, int startY, int listHeight, int totalItems,
            int visibleItems, int contentWidth) {
        if (totalItems <= visibleItems)
            return;

        // Draw scroll bar background within content area
        int scrollBarX = contentX + contentWidth - 8;
        int scrollBarY = startY;
        int scrollBarHeight = listHeight;
        guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + 6, scrollBarY + scrollBarHeight, 0x44333333);

        // Calculate scroll thumb position and size
        float scrollPercentage = (float) scrollOffset / Math.max(1, totalItems - visibleItems);
        int thumbHeight = Math.max(10, (scrollBarHeight * visibleItems) / totalItems);
        int thumbY = scrollBarY + (int) ((scrollBarHeight - thumbHeight) * scrollPercentage);

        // Draw scroll thumb
        guiGraphics.fill(scrollBarX + 1, thumbY, scrollBarX + 5, thumbY + thumbHeight, 0x88AAAAAA);

        // Draw scroll arrows if needed
        if (scrollOffset > 0) {
            guiGraphics.drawString(font, "↑", scrollBarX, startY - 12, COLOR_WHITE);
        }
        if (scrollOffset < totalItems - visibleItems) {
            guiGraphics.drawString(font, "↓", scrollBarX, startY + listHeight + 2, COLOR_WHITE);
        }
    }

    private void switchToWarChest() {
        // Don't allow switching to War Chest if feature is disabled
        if (!TaxConfig.isWarChestEnabled()) {
            return;
        }

        showingVassals = false;
        showingPermissions = false;
        showingWarChest = true;
        scrollOffset = 0;
        setWarChestElementsVisible(true);

        // Request war chest data for currently selected colony
        if (selectedColony != null) {
            NetworkHandler.sendToServer(new RequestWarChestDataPacket(selectedColony.getColonyId()));
        }
    }

    /**
     * Called by WarChestDataResponsePacket to update war chest data from server.
     */
    public void updateWarChestData(int colonyId, int balance, int maxCapacity, int drainPerMinute,
            int taxBalance, boolean autoSurrender, double minPercentForWar) {
        this.warChestBalance = balance;
        this.warChestMaxCapacity = maxCapacity;
        this.warChestDrainPerMin = drainPerMinute;
        this.currentTaxBalance = taxBalance;
        this.warChestAutoSurrender = autoSurrender;
        this.warChestMinPercent = minPercentForWar;
    }

    private void renderWarChest(GuiGraphics guiGraphics, int guiLeft, int guiTop, int mouseX, int mouseY) {
        if (selectedColony == null) {
            guiGraphics.drawCenteredString(font, "Select a colony to view War Chest", guiLeft + GUI_WIDTH / 2,
                    guiTop + 80, COLOR_GRAY);
            guiGraphics.drawCenteredString(font, "Switch to 'Colonies' tab and select one", guiLeft + GUI_WIDTH / 2,
                    guiTop + 100, COLOR_GRAY);
            return;
        }

        int contentMargin = 50;
        int contentX = guiLeft + contentMargin;
        int contentWidth = GUI_WIDTH - (contentMargin * 2);
        int currentY = guiTop + 55;

        // Colony name header
        String colonyName = selectedColony.getColonyName();
        guiGraphics.drawCenteredString(font, colonyName + " - War Chest", guiLeft + GUI_WIDTH / 2, currentY,
                COLOR_GOLD);
        currentY += 25;

        // War Chest Balance - large display
        String balanceStr = String.format("%,d", warChestBalance);
        String maxStr = String.format("%,d", warChestMaxCapacity);
        guiGraphics.drawString(font, "Balance:", contentX, currentY, COLOR_LIGHT_GRAY);
        int balanceColor = warChestBalance > 0 ? COLOR_GREEN : COLOR_RED;
        guiGraphics.drawString(font, balanceStr + " / " + maxStr, contentX + 60, currentY, balanceColor);
        currentY += 18;

        // Fill bar
        int barWidth = contentWidth - 10;
        int barHeight = 12;
        float fillPercent = warChestMaxCapacity > 0 ? (float) warChestBalance / warChestMaxCapacity : 0;
        int fillWidth = (int) (barWidth * Math.min(1, fillPercent));

        guiGraphics.fill(contentX, currentY, contentX + barWidth, currentY + barHeight, 0xFF444444);
        if (fillWidth > 0) {
            int barColor = fillPercent > 0.5 ? 0xFF00AA00 : (fillPercent > 0.25 ? 0xFFAAAA00 : 0xFFAA0000);
            guiGraphics.fill(contentX + 1, currentY + 1, contentX + 1 + fillWidth, currentY + barHeight - 1, barColor);
        }
        guiGraphics.fill(contentX, currentY, contentX + barWidth, currentY + 1, 0xFF666666);
        guiGraphics.fill(contentX, currentY + barHeight - 1, contentX + barWidth, currentY + barHeight, 0xFF666666);
        currentY += 22;

        // Drain rate info
        guiGraphics.drawString(font, "Drain Rate:", contentX, currentY, COLOR_LIGHT_GRAY);
        String drainStr = warChestDrainPerMin + " " + getCurrencyName() + "/min during war";
        guiGraphics.drawString(font, drainStr, contentX + 75, currentY, COLOR_GRAY);
        currentY += 15;

        // Auto-surrender info
        String autoSurrenderStr = warChestAutoSurrender ? "YES (when empty)" : "NO";
        int autoColor = warChestAutoSurrender ? COLOR_RED : COLOR_GREEN;
        guiGraphics.drawString(font, "Auto Surrender:", contentX, currentY, COLOR_LIGHT_GRAY);
        guiGraphics.drawString(font, autoSurrenderStr, contentX + 100, currentY, autoColor);
        currentY += 25;

        // Separator
        guiGraphics.fill(contentX, currentY, contentX + contentWidth, currentY + 1, 0xFF555555);
        currentY += 10;

        // Interactive GUI replaces text instructions
        guiGraphics.drawCenteredString(font, "Enter amount:", guiLeft + GUI_WIDTH / 2, currentY, COLOR_GRAY);
        // Elements are rendered automatically by addRenderableWidget
    }

    /**
     * Gets the appropriate currency name based on config settings
     * 
     * @return the currency name to display
     */
    private String getCurrencyName() {
        if (TaxConfig.isSDMShopConversionEnabled()) {
            return "$";
        } else {
            String currencyName = TaxConfig.getCurrencyItemName();
            if (currencyName.contains(":")) {
                currencyName = currencyName.substring(currencyName.indexOf(":") + 1);
            }
            return currencyName;
        }
    }

    private void switchToEspionage() {
        showingVassals = false;
        showingPermissions = false;
        showingWarChest = false;
        showingEspionage = true;
        scrollOffset = 0;
        setWarChestElementsVisible(false);
        net.machiavelli.minecolonytax.network.NetworkHandler
                .sendToServer(new net.machiavelli.minecolonytax.network.packets.RequestSpyDataPacket());
    }

    private void renderEspionage(GuiGraphics guiGraphics, int guiLeft, int guiTop, int mouseX, int mouseY) {
        if (!TaxConfig.isSpySystemEnabled()) {
            guiGraphics.drawCenteredString(font, "Espionage is disabled.", guiLeft + GUI_WIDTH / 2, guiTop + 60,
                    COLOR_RED);
            return;
        }

        int contentMargin = 50;
        int contentX = guiLeft + contentMargin;
        int contentWidth = GUI_WIDTH - (contentMargin * 2);
        int currentY = guiTop + 50;

        guiGraphics.drawString(font, "Active Missions (" + spyMissions.size() + ")", contentX, currentY, COLOR_GOLD);
        currentY += 15;

        if (spyMissions.isEmpty()) {
            guiGraphics.drawString(font, "No active missions.", contentX + 10, currentY, COLOR_GRAY);
            currentY += 20;
        } else {
            for (net.machiavelli.minecolonytax.gui.data.SpyMissionData mission : spyMissions) {
                String desc = mission.getMissionType() + " -> " + mission.getTargetColonyName();
                guiGraphics.drawString(font, desc, contentX + 10, currentY, COLOR_WHITE);
                String status = "Status: " + mission.getStatus();
                guiGraphics.drawString(font, status, contentX + 10, currentY + 10, 0xFFAAAAAA);

                // Draw Recall Button
                int btnX = contentX + contentWidth - 45;
                guiGraphics.fill(btnX, currentY, btnX + 40, currentY + 15, 0xFF880000);
                guiGraphics.drawCenteredString(font, "Recall", btnX + 20, currentY + 4, COLOR_WHITE);
                currentY += 25;
            }
        }

        guiGraphics.fill(contentX, currentY, contentX + contentWidth, currentY + 1, 0xFF555555);
        currentY += 10;

        guiGraphics.drawString(font, "Deploy New Spy", contentX, currentY, COLOR_GOLD);
        currentY += 15;

        // Target Selector
        String curTarget = "None";
        if (!colonies.isEmpty() && selectedTargetColonyIndex >= 0 && selectedTargetColonyIndex < colonies.size()) {
            ColonyTaxData target = colonies.get(selectedTargetColonyIndex);
            curTarget = target.getColonyName();
        }
        guiGraphics.drawString(font, "Target: " + curTarget, contentX + 10, currentY, COLOR_WHITE);
        int tgtBtnX = contentX + contentWidth - 25;
        guiGraphics.fill(tgtBtnX, currentY, tgtBtnX + 20, currentY + 10, 0xFF444444);
        guiGraphics.drawCenteredString(font, ">", tgtBtnX + 10, currentY + 1, COLOR_WHITE);
        currentY += 15;

        // Type Selector
        String curType = MISSION_TYPES[selectedMissionTypeIndex];
        guiGraphics.drawString(font, "Mission: " + curType, contentX + 10, currentY, COLOR_WHITE);
        int typBtnX = contentX + contentWidth - 25;
        guiGraphics.fill(typBtnX, currentY, typBtnX + 20, currentY + 10, 0xFF444444);
        guiGraphics.drawCenteredString(font, ">", typBtnX + 10, currentY + 1, COLOR_WHITE);
        currentY += 15;

        // Deploy Button
        int dBtnX = contentX + 10;
        guiGraphics.fill(dBtnX, currentY, dBtnX + 60, currentY + 15, 0xFF006600);
        guiGraphics.drawCenteredString(font, "DEPLOY", dBtnX + 30, currentY + 4, COLOR_WHITE);
    }
}
