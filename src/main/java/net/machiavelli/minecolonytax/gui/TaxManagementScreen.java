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
import net.machiavelli.minecolonytax.network.packets.RequestOfficerDataPacket;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class TaxManagementScreen extends Screen {
    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation("minecolonytax", "textures/gui/backgroundmenu.png");
    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 240;
    
    private List<ColonyTaxData> colonies = new ArrayList<>();
    private List<VassalIncomeData> vassalData = new ArrayList<>();
    private List<OfficerData> officerData = new ArrayList<>();
    private int scrollOffset = 0;
    private int vassalScrollOffset = 0;
    private final int maxVisibleColonies = 4; // Show max 4 colonies at a time
    private final int maxVisibleVassals = 4;
    private ColonyTaxData selectedColony = null;
    private Button refreshButton;
    private Button claimAllButton;
    private Button vassalsTabButton;
    private Button coloniesTabButton;
    private Button permissionsTabButton;
    private Button claimSelectedButton;
    private Button payDebtButton;
    private Button endVassalButton;
    private boolean showingVassals = false;
    private boolean showingPermissions = false;
    
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
        
        // Tab buttons
        this.coloniesTabButton = Button.builder(
            Component.literal("Colonies"),
            button -> switchToColonies()
        )
        .bounds(guiLeft + 10, guiTop + 25, 60, 20)
        .build();
        this.addRenderableWidget(coloniesTabButton);
        
        this.vassalsTabButton = Button.builder(
            Component.literal("Vassals"),
            button -> switchToVassals()
        )
        .bounds(guiLeft + 75, guiTop + 25, 50, 20)
        .build();
        this.addRenderableWidget(vassalsTabButton);
        
        this.permissionsTabButton = Button.builder(
            Component.literal("Officers"),
            button -> switchToPermissions()
        )
        .bounds(guiLeft + 130, guiTop + 25, 50, 20)
        .build();
        this.addRenderableWidget(permissionsTabButton);
        
        // Main action buttons - repositioned
        this.refreshButton = Button.builder(
            Component.literal("Refresh"),
            button -> requestColonyData()
        )
        .bounds(guiLeft + 10, guiTop + GUI_HEIGHT - 25, 50, 20)
        .build();
        this.addRenderableWidget(refreshButton);
        
        // Selected colony action buttons (positioned between Refresh and Claim All)
        this.claimSelectedButton = Button.builder(
            Component.literal("Claim"),
            button -> {
                if (selectedColony != null && selectedColony.canClaimTax()) {
                    NetworkHandler.sendToServer(new ClaimTaxPacket(selectedColony.getColonyId(), -1));
                    selectedColony = null;
                    updateButtonVisibility();
                }
            }
        )
        .bounds(guiLeft + 65, guiTop + GUI_HEIGHT - 25, 45, 20)
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
            }
        )
        .bounds(guiLeft + 115, guiTop + GUI_HEIGHT - 25, 55, 20)
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
            }
        )
        .bounds(guiLeft + 115, guiTop + GUI_HEIGHT - 25, 60, 20)
        .build();
        this.endVassalButton.visible = false;
        this.addRenderableWidget(endVassalButton);
        
        this.claimAllButton = Button.builder(
            Component.literal("Claim All"),
            button -> claimAllTaxes()
        )
        .bounds(guiLeft + GUI_WIDTH - 70, guiTop + GUI_HEIGHT - 25, 60, 20)
        .build();
        this.addRenderableWidget(claimAllButton);
        
        // Request initial data
        requestColonyData();
    }
    
    private void requestColonyData() {
        NetworkHandler.sendToServer(new RequestColonyDataPacket());
        // Also clear officer data when refreshing to ensure fresh data
        officerData.clear();
    }
    
    private void switchToColonies() {
        showingVassals = false;
        showingPermissions = false;
        scrollOffset = 0;
    }
    
    private void switchToVassals() {
        showingVassals = true;
        showingPermissions = false;
        vassalScrollOffset = 0;
    }
    
    private void switchToPermissions() {
        showingVassals = false;
        showingPermissions = true;
        scrollOffset = 0;
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Don't call renderBackground() as it interferes with our custom texture
        
        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        
        // Draw backgroundmenu.png texture with fallback
        try {
            // Use the full texture dimensions - backgroundmenu.png is typically 256x256
            guiGraphics.blit(BACKGROUND_TEXTURE, guiLeft, guiTop, 0, 0, GUI_WIDTH, GUI_HEIGHT, 256, 256);
        } catch (Exception e) {
            // Fallback: draw a simple colored background if texture fails
            guiGraphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF2D2D30);
            guiGraphics.fill(guiLeft + 2, guiTop + 2, guiLeft + GUI_WIDTH - 2, guiTop + GUI_HEIGHT - 2, 0xFF1E1E1E);
        }
        
        // Draw title with enhanced styling
        Component title = Component.translatable("gui.minecolonytax.tax_management.title");
        guiGraphics.drawCenteredString(this.font, title, guiLeft + GUI_WIDTH / 2, guiTop + 6, COLOR_WHITE);
        
        // Highlight active tab
        if (showingPermissions) {
            guiGraphics.fill(guiLeft + 130, guiTop + 45, guiLeft + 180, guiTop + 47, COLOR_GOLD);
        } else if (showingVassals) {
            guiGraphics.fill(guiLeft + 75, guiTop + 45, guiLeft + 125, guiTop + 47, COLOR_GOLD);
        } else {
            guiGraphics.fill(guiLeft + 10, guiTop + 45, guiLeft + 70, guiTop + 47, COLOR_GOLD);
        }
        
        // Draw appropriate content based on active tab
        if (showingPermissions) {
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
    
    private void renderColonyList(GuiGraphics guiGraphics, int guiLeft, int guiTop, int mouseX, int mouseY) {
        Font font = this.font;
        int startY = guiTop + 50;
        int entryHeight = 35; // Increased spacing between entries
        
        for (int i = 0; i < Math.min(maxVisibleColonies, colonies.size() - scrollOffset); i++) {
            ColonyTaxData colony = colonies.get(i + scrollOffset);
            int entryY = startY + i * entryHeight;
            
            // Background for entry with card-like styling
            boolean isHovered = mouseX >= guiLeft + 7 && mouseX < guiLeft + GUI_WIDTH - 17 && 
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
            
            // Draw card background with border
            guiGraphics.fill(guiLeft + 7, entryY, guiLeft + GUI_WIDTH - 17, entryY + entryHeight - 2, borderColor);
            guiGraphics.fill(guiLeft + 8, entryY + 1, guiLeft + GUI_WIDTH - 18, entryY + entryHeight - 3, bgColor);
            
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
            
            guiGraphics.drawString(font, colonyName, guiLeft + 8, entryY + 2, COLOR_WHITE);
            guiGraphics.drawString(font, statusText, guiLeft + 8, entryY + 11, statusColor);
            
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
            
            int rightX = guiLeft + GUI_WIDTH - 8;
            guiGraphics.drawString(font, taxText, rightX - font.width(taxText), entryY + 2, taxColor);
            guiGraphics.drawString(font, buildingText, rightX - font.width(buildingText), entryY + 11, COLOR_GRAY);
            guiGraphics.drawString(font, revenueText, rightX - font.width(revenueText), entryY + 20, COLOR_LIGHT_GRAY);
            
            // Enhanced vassal indicator with modern styling
            if (colony.isVassal()) {
                String vassalText = "Vassal (" + colony.getVassalTributeRate() + "%)";
                int vassalTextWidth = font.width(vassalText);
                int vassalBgX = guiLeft + 120;
                int vassalBgY = entryY + 1;
                
                // Draw background badge for vassal status
                guiGraphics.fill(vassalBgX - 2, vassalBgY, vassalBgX + vassalTextWidth + 2, vassalBgY + 9, COLOR_ORANGE);
                guiGraphics.drawString(font, vassalText, vassalBgX, vassalBgY + 1, COLOR_WHITE);
            } else if (colony.hasVassals()) {
                String overlordText = "Overlord (" + colony.getVassalCount() + ")";
                int overlordTextWidth = font.width(overlordText);
                int overlordBgX = guiLeft + 120;
                int overlordBgY = entryY + 1;
                guiGraphics.fill(overlordBgX - 2, overlordBgY, overlordBgX + overlordTextWidth + 2, overlordBgY + 9, COLOR_GREEN);
                guiGraphics.drawString(font, overlordText, overlordBgX, overlordBgY + 1, COLOR_WHITE);
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
    }
    
    private void renderScrollIndicator(GuiGraphics guiGraphics, int guiLeft, int guiTop) {
        int scrollBarX = guiLeft + GUI_WIDTH - 12;
        int scrollBarY = guiTop + 50;
        int scrollBarHeight = maxVisibleColonies * 28;
        
        // Enhanced scroll track with border
        guiGraphics.fill(scrollBarX - 1, scrollBarY - 1, scrollBarX + 8, scrollBarY + scrollBarHeight + 1, COLOR_BORDER);
        guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + 7, scrollBarY + scrollBarHeight, COLOR_BACKGROUND);
        
        // Enhanced scroll thumb with better styling
        int thumbHeight = Math.max(10, (maxVisibleColonies * scrollBarHeight) / colonies.size());
        int thumbY = scrollBarY + (scrollOffset * (scrollBarHeight - thumbHeight)) / Math.max(1, colonies.size() - maxVisibleColonies);
        
        // Draw thumb with gradient effect
        guiGraphics.fill(scrollBarX + 1, thumbY, scrollBarX + 6, thumbY + thumbHeight, COLOR_HEADER);
        guiGraphics.fill(scrollBarX + 2, thumbY + 1, scrollBarX + 5, thumbY + thumbHeight - 1, COLOR_BLUE);
    }
    
    private void renderVassalList(GuiGraphics guiGraphics, int guiLeft, int guiTop, int mouseX, int mouseY) {
        Font font = this.font;
        int startY = guiTop + 50;
        int entryHeight = 30;
        
        // Section 1: Colonies you are vassalizing (your vassals)
        Component yourVassalsTitle = Component.literal("Your Vassals:");
        guiGraphics.drawString(font, yourVassalsTitle, guiLeft + 10, startY, COLOR_BLUE);
        int currentY = startY + 15;
        
        if (vassalData.isEmpty()) {
            Component noVassals = Component.literal("No vassals");
            guiGraphics.drawString(font, noVassals, guiLeft + 10, currentY, COLOR_GRAY);
            currentY += 15;
        } else {
            int visibleVassals = Math.min(maxVisibleVassals, vassalData.size() - vassalScrollOffset);
            for (int i = 0; i < visibleVassals; i++) {
                VassalIncomeData vassal = vassalData.get(i + vassalScrollOffset);
                int entryY = currentY + i * entryHeight;
                
                // Background for entry with card-like styling
                boolean isHovered = mouseX >= guiLeft + 7 && mouseX < guiLeft + GUI_WIDTH - 17 && 
                                  mouseY >= entryY && mouseY < entryY + entryHeight - 2;
                int bgColor = isHovered ? 0x404040 : (i % 2 == 0 ? 0x303030 : 0x2A2A2A);
                int borderColor = isHovered ? COLOR_GOLD : 0x505050;
                
                // Draw card background with border
                guiGraphics.fill(guiLeft + 7, entryY, guiLeft + GUI_WIDTH - 17, entryY + entryHeight - 2, borderColor);
                guiGraphics.fill(guiLeft + 8, entryY + 1, guiLeft + GUI_WIDTH - 18, entryY + entryHeight - 3, bgColor);
                
                // Vassal colony name
                String colonyName = vassal.getVassalColonyName();
                if (colonyName.length() > 16) {
                    colonyName = colonyName.substring(0, 13) + "...";
                }
                
                guiGraphics.drawString(font, colonyName, guiLeft + 10, entryY + 3, COLOR_WHITE);
                
                // Tribute info
                String tributeInfo = vassal.getTributeRate() + "% tribute";
                guiGraphics.drawString(font, tributeInfo, guiLeft + 10, entryY + 13, COLOR_ORANGE);
            
            // Tribute estimation info
            int estimatedNextTribute = (int)(vassal.getTributeOwed() * (vassal.getTributeRate() / 100.0));
            String estimationText = "Next Est: " + estimatedNextTribute;
            int estimationWidth = font.width(estimationText);
            guiGraphics.drawString(font, estimationText, guiLeft + GUI_WIDTH - 17 - estimationWidth, entryY + 2, COLOR_YELLOW);
            
            // Last collected tribute amount and time
            String lastText = "Last: " + vassal.getLastTribute() + " (" + vassal.getFormattedLastPayment() + ")";
            int lastTextWidth = font.width(lastText);
            guiGraphics.drawString(font, lastText, guiLeft + GUI_WIDTH - 18 - lastTextWidth, entryY + 13, COLOR_GRAY);
            
            // Show auto-collection status instead of manual button
            String autoText = "Auto-collected at tax intervals";
            int autoTextWidth = font.width(autoText);
            int autoX = guiLeft + GUI_WIDTH - 17 - autoTextWidth;
            guiGraphics.drawString(font, autoText, autoX, entryY + 22, COLOR_LIGHT_GREEN);
            }
            currentY += visibleVassals * entryHeight;
        }
        
        // Section 2: Colonies you are vassal of (your overlords)
        currentY += 20;
        Component vassalOfTitle = Component.literal("You are vassal of:");
        guiGraphics.drawString(font, vassalOfTitle, guiLeft + 10, currentY, COLOR_RED);
        currentY += 15;
        
        // Find colonies where the current player is a vassal
        List<ColonyTaxData> playerVassalColonies = colonies.stream()
            .filter(ColonyTaxData::isVassal)
            .collect(java.util.stream.Collectors.toList());
            
        if (playerVassalColonies.isEmpty()) {
            Component noOverlords = Component.literal("You are independent");
            guiGraphics.drawString(font, noOverlords, guiLeft + 10, currentY, COLOR_GRAY);
        } else {
            for (int i = 0; i < playerVassalColonies.size() && i < 3; i++) {
                ColonyTaxData vassalColony = playerVassalColonies.get(i);
                int entryY = currentY + i * 25;
                
                // Background
                boolean isSelected = selectedColony != null && selectedColony.getColonyId() == vassalColony.getColonyId();
                boolean isHovered = mouseX >= guiLeft + 7 && mouseX < guiLeft + GUI_WIDTH - 17 && 
                                  mouseY >= entryY && mouseY < entryY + 23;
                int bgColor = isSelected ? COLOR_GOLD : (isHovered ? 0x404040 : 0x303030);
                
                guiGraphics.fill(guiLeft + 7, entryY, guiLeft + GUI_WIDTH - 17, entryY + 23, bgColor);
                
                // Colony info
                String colonyText = vassalColony.getColonyName() + " (paying " + vassalColony.getVassalTributeRate() + "%)";
                if (colonyText.length() > 25) {
                    colonyText = colonyText.substring(0, 22) + "...";
                }
                guiGraphics.drawString(font, colonyText, guiLeft + 10, entryY + 3, COLOR_WHITE);
                
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
                
                guiGraphics.drawString(font, "Click to select", guiLeft + 10, entryY + 13, COLOR_LIGHT_GRAY);
            }
        }
    }
    
    private void renderVassalScrollIndicator(GuiGraphics guiGraphics, int guiLeft, int guiTop) {
        int scrollBarX = guiLeft + GUI_WIDTH - 12;
        int scrollBarY = guiTop + 50;
        int scrollBarHeight = maxVisibleVassals * 30;
        
        // Enhanced scroll track with border
        guiGraphics.fill(scrollBarX - 1, scrollBarY - 1, scrollBarX + 8, scrollBarY + scrollBarHeight + 1, COLOR_BORDER);
        guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + 7, scrollBarY + scrollBarHeight, COLOR_BACKGROUND);
        
        // Enhanced scroll thumb with better styling
        int thumbHeight = Math.max(10, (maxVisibleVassals * scrollBarHeight) / vassalData.size());
        int thumbY = scrollBarY + (vassalScrollOffset * (scrollBarHeight - thumbHeight)) / Math.max(1, vassalData.size() - maxVisibleVassals);
        
        // Draw thumb with gradient effect
        guiGraphics.fill(scrollBarX + 1, thumbY, scrollBarX + 6, thumbY + thumbHeight, COLOR_HEADER);
        guiGraphics.fill(scrollBarX + 2, thumbY + 1, scrollBarX + 5, thumbY + thumbHeight - 1, COLOR_BLUE);
    }
    
    private int getColonyStatusColor(ColonyTaxData colony) {
        if (!colony.canClaimTax()) {
            if (colony.isAtWar()) return COLOR_RED;
            if (colony.isBeingRaided()) return COLOR_RED;
            return COLOR_YELLOW; // Other restrictions
        }
        
        if (colony.getTaxBalance() < 0) return COLOR_RED; // In debt
        if (colony.getTaxBalance() >= colony.getMaxTaxRevenue() * 0.9) return COLOR_YELLOW; // Near max
        return COLOR_GREEN; // Healthy
    }
    
    private String getColonyStatusText(ColonyTaxData colony) {
        if (colony.isAtWar()) return "At War";
        if (colony.isBeingRaided()) return "Under Raid";
        if (!colony.canClaimTax()) return "Restricted";
        if (colony.getTaxBalance() < 0) return "In Debt";
        return "Healthy";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            int guiLeft = (this.width - GUI_WIDTH) / 2;
            int guiTop = (this.height - GUI_HEIGHT) / 2;
            
            if (showingVassals) {
                // Note: Manual tribute collection removed - tributes are now auto-collected during tax intervals
                
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
                    
                    // Check for colony selection click
                    if (mouseX >= guiLeft + 7 && mouseX < guiLeft + GUI_WIDTH - 17 && 
                        mouseY >= entryY && mouseY < entryY + 23) {
                        
                        // Check if clicking the end vassalage button
                        boolean isSelected = selectedColony != null && selectedColony.getColonyId() == vassalColony.getColonyId();
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
                    if (selectedColony.isClaimButtonClicked(mouseX, mouseY)) {
                        // Toggle permission and send to server
                        boolean newPermission = TaxPermissionManager.toggleOfficerClaimPermission(selectedColony.getColonyId());
                        NetworkHandler.sendToServer(new UpdateTaxPermissionPacket(selectedColony.getColonyId(), newPermission));
                        return true;
                    }
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
                    
                    // Check if clicking on colony row for selection
                    if (mouseX >= guiLeft + 7 && mouseX < guiLeft + GUI_WIDTH - 17 && 
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (showingVassals) {
            if (vassalData.size() > maxVisibleVassals) {
                vassalScrollOffset = Mth.clamp(vassalScrollOffset - (int) delta, 0, vassalData.size() - maxVisibleVassals);
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
    
    public void updateColonyData(List<ColonyTaxData> newData) {
        this.colonies.clear();
        this.colonies.addAll(newData);
        
        // Sort colonies: owned colonies first, then managers
        this.colonies.sort((a, b) -> {
            if (a.isOwner() && !b.isOwner()) return -1;
            if (!a.isOwner() && b.isOwner()) return 1;
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
            claimSelectedButton.visible = isColonyView && hasSelection && selectedColony.canClaimTax() && !selectedColony.isVassal();
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
    
    
    private void renderPermissionsManagement(GuiGraphics guiGraphics, int guiLeft, int guiTop, int mouseX, int mouseY) {
        Font font = this.font;
        int startY = guiTop + 50;
        
        // Title for officers section
        Component officersTitle = Component.literal("Colony Officers & Permissions");
        guiGraphics.drawCenteredString(font, officersTitle, guiLeft + GUI_WIDTH / 2, startY, COLOR_WHITE);
        
        // Check if a colony is selected from the main colonies view
        if (selectedColony == null) {
            String noSelection = "No colony selected.";
            String instruction = "Go to Colonies tab and select a colony first.";
            guiGraphics.drawCenteredString(font, noSelection, guiLeft + GUI_WIDTH / 2, startY + 40, COLOR_RED);
            guiGraphics.drawCenteredString(font, instruction, guiLeft + GUI_WIDTH / 2, startY + 55, COLOR_LIGHT_GRAY);
            return;
        }
        
        // Request officer data if we don't have it for this colony
        if (officerData.isEmpty()) {
            NetworkHandler.sendToServer(new RequestOfficerDataPacket(selectedColony.getColonyId()));
            String loading = "Loading officers...";
            guiGraphics.drawCenteredString(font, loading, guiLeft + GUI_WIDTH / 2, startY + 40, COLOR_YELLOW);
            return;
        }
        
        // Display selected colony info
        int infoY = startY + 20;
        String colonyInfo = "Colony: " + selectedColony.getColonyName();
        guiGraphics.drawString(font, colonyInfo, guiLeft + 10, infoY, COLOR_WHITE);
        
        // Permission toggle for selected colony (if owner)
        int officersStartY = infoY + 20;
        if (selectedColony.isOwner()) {
            boolean officersCanClaim = TaxPermissionManager.canOfficersClaim(selectedColony.getColonyId());
            String permText = "Tax Claiming: " + (officersCanClaim ? "ALLOWED" : "BLOCKED");
            int permColor = officersCanClaim ? COLOR_LIGHT_GREEN : COLOR_RED;
            guiGraphics.drawString(font, permText, guiLeft + 10, officersStartY, permColor);
            
            // Toggle button
            int toggleX = guiLeft + GUI_WIDTH - 60;
            int toggleY = officersStartY - 2;
            boolean toggleHovered = mouseX >= toggleX && mouseX < toggleX + 50 &&
                                   mouseY >= toggleY && mouseY < toggleY + 12;
            
            int toggleBg = officersCanClaim ? 
                (toggleHovered ? COLOR_LIGHT_GREEN : COLOR_GREEN) :
                (toggleHovered ? 0x800000 : 0x600000);
            
            guiGraphics.fill(toggleX, toggleY, toggleX + 50, toggleY + 12, toggleBg);
            String toggleText = officersCanClaim ? "ALLOW" : "BLOCK";
            guiGraphics.drawString(font, toggleText, toggleX + 8, toggleY + 2, COLOR_WHITE);
            
            // Store toggle button bounds
            selectedColony.setClaimButtonBounds(toggleX, toggleY, 50, 12);
            
            officersStartY += 25;
        } else {
            // Show read-only permission status for non-owners
            boolean officersCanClaim = TaxPermissionManager.canOfficersClaim(selectedColony.getColonyId());
            String permText = "Tax Claiming: " + (officersCanClaim ? "ALLOWED" : "BLOCKED") + " (Read-only)";
            int permColor = COLOR_LIGHT_GRAY;
            guiGraphics.drawString(font, permText, guiLeft + 10, officersStartY, permColor);
            officersStartY += 20;
        }
        
        // Officers header
        guiGraphics.drawString(font, "Officers & Members:", guiLeft + 10, officersStartY, COLOR_LIGHT_GRAY);
        officersStartY += 15;
        
        // Officers list
        if (officerData.isEmpty()) {
            // Show loading message if no data yet
            String message = selectedColony != null ? "Loading officers..." : "No officers found for this colony";
            guiGraphics.drawString(font, message, guiLeft + 10, officersStartY, COLOR_GRAY);
        } else {
            int officerY = officersStartY;
            for (int i = 0; i < Math.min(7, officerData.size()); i++) {
                OfficerData officer = officerData.get(i);
                
                // Officer entry background
                int entryHeight = 12;
                boolean entryHovered = mouseX >= guiLeft + 7 && mouseX < guiLeft + GUI_WIDTH - 17 &&
                                      mouseY >= officerY - 1 && mouseY < officerY + entryHeight - 1;
                
                if (entryHovered) {
                    guiGraphics.fill(guiLeft + 7, officerY - 1, guiLeft + GUI_WIDTH - 17, officerY + entryHeight - 1, 0x333333);
                }
                
                // Officer name and rank
                String officerText = officer.getPlayerName() + " [" + officer.getRank() + "]";
                if (officerText.length() > 25) {
                    officerText = officerText.substring(0, 22) + "...";
                }
                
                guiGraphics.drawString(font, officerText, guiLeft + 10, officerY, officer.getRankColor());
                
                // Status (online/offline, can claim)
                String statusText = officer.getLastSeenText();
                if (officer.canClaimTax()) {
                    statusText += " ✓";
                }
                
                int rightX = guiLeft + GUI_WIDTH - 8;
                guiGraphics.drawString(font, statusText, rightX - font.width(statusText), officerY, officer.getStatusColor());
                
                officerY += entryHeight + 2;
            }
        }
        
    }
}
