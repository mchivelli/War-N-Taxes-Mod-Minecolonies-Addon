package net.machiavelli.minecolonytax.gui.book;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.packets.BuyInvestmentPayload;
import net.machiavelli.minecolonytax.network.packets.RequestInvestmentDataPayload;
import net.machiavelli.minecolonytax.upgrade.UpgradeType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static net.machiavelli.minecolonytax.gui.book.BookRenderHelper.*;

/**
 * Investments tab: left page lists upgrade types with their level, right page shows the
 * selected upgrade's detail + a Buy button. Mirrors the War Chest page pattern: on activation
 * it requests data from the server, and clicking Buy sends a {@link BuyInvestmentPayload}.
 */
public class InvestmentsPage extends BookPage {

    private static final UpgradeType[] TYPES = UpgradeType.values();
    private static final int ROW_H = 16;
    private static final int BUY_BTN_H = 14;
    private static final int LEVEL_BAR_H = 12;

    private final Supplier<ColonyTaxData> selectedColonySupplier;

    // Data from server
    private Map<String, Integer> levels = new LinkedHashMap<>();
    private Map<String, Integer> costs = new LinkedHashMap<>();
    private int treasuryBalance = 0;
    private int maxLevel = 5;

    // UI state
    private UpgradeType selectedType = null;
    private int buyButtonY = 0;

    public InvestmentsPage(Screen screen, Font font, Supplier<ColonyTaxData> selectedColonySupplier) {
        super(screen, font);
        this.selectedColonySupplier = selectedColonySupplier;
    }

    @Override
    public void onActivated() {
        ColonyTaxData colony = selectedColonySupplier.get();
        if (colony != null) {
            NetworkHandler.sendToServer(new RequestInvestmentDataPayload(colony.getColonyId()));
        }
    }

    /**
     * Called by the shell when investment data arrives from the server.
     */
    public void updateData(int colonyId, Map<String, Integer> levels, Map<String, Integer> costs,
                           int treasuryBalance, int maxCapacity, int maxLevel) {
        this.levels = new LinkedHashMap<>(levels);
        this.costs = new LinkedHashMap<>(costs);
        this.treasuryBalance = treasuryBalance;
        this.maxLevel = maxLevel;
    }

    // -- Left page: type list --------------------------------------------------

    @Override
    public void renderLeftPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawHeading(g, font, "Investments", leftX, leftY, leftW);
        int y = leftY + 14;

        ColonyTaxData colony = selectedColonySupplier.get();
        if (colony == null) {
            g.drawString(font, "Select a colony", leftX + 4, y + 10, INK_GHOST, false);
            g.drawString(font, "from Colonies tab", leftX + 4, y + 22, INK_GHOST, false);
            return;
        }

        // Colony name right-aligned on the same heading line -- no extra Y space consumed
        String colName = truncate(font, colony.getColonyName(), leftW - font.width("Investments") - 6);
        g.drawString(font, colName, leftX + leftW - font.width(colName) - 2, leftY + 2, INK_FAINT, false);

        for (UpgradeType type : TYPES) {
            boolean selected = type == selectedType;
            boolean hovered = isInBounds(mouseX, mouseY, leftX, y, leftW, ROW_H);

            if (selected) {
                g.fill(leftX, y, leftX + leftW, y + ROW_H, 0x20C8A430);
            } else if (hovered) {
                g.fill(leftX, y, leftX + leftW, y + ROW_H, 0x10000000);
            }

            int level = levels.getOrDefault(type.name(), 0);
            boolean atMax = level >= maxLevel;

            String name = truncate(font, getDisplayName(type), leftW - 30);
            g.drawString(font, name, leftX + 3, y + 4, selected ? GOLD : INK, false);

            String levelStr = atMax ? "MAX" : "L" + level;
            int levelColor = atMax ? GREEN : (level > 0 ? GOLD : INK_GHOST);
            g.drawString(font, levelStr, leftX + leftW - font.width(levelStr) - 3, y + 4, levelColor, false);

            g.fill(leftX + 2, y + ROW_H - 1, leftX + leftW - 2, y + ROW_H, CARD_BORDER);
            y += ROW_H;
        }
    }

    // -- Right page: selected type detail --------------------------------------

    @Override
    public void renderRightPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ColonyTaxData colony = selectedColonySupplier.get();

        if (colony == null) {
            drawHeading(g, font, "Investment", rightX, rightY, rightW);
            g.drawString(font, "Select a colony", rightX + 4, rightY + 20, INK_GHOST, false);
            return;
        }

        if (selectedType == null) {
            drawHeading(g, font, "Investment", rightX, rightY, rightW);
            g.drawString(font, "Select an investment", rightX + 4, rightY + 20, INK_GHOST, false);
            g.drawString(font, "on the left", rightX + 4, rightY + 32, INK_GHOST, false);
            return;
        }

        int level = levels.getOrDefault(selectedType.name(), 0);
        boolean atMax = level >= maxLevel;
        int cost = costs.getOrDefault(selectedType.name(), 0);
        boolean canAfford = !atMax && treasuryBalance >= cost && cost > 0;

        drawHeading(g, font, getDisplayName(selectedType), rightX, rightY, rightW);
        int y = rightY + 14;

        // -- Level bar: solid background so white text is always visible --------
        int levelBg = atMax ? 0xFF2A6E2A : (level > 0 ? 0xFF8A6F1A : 0xFF555555);
        g.fill(rightX, y, rightX + rightW, y + LEVEL_BAR_H, levelBg);
        String levelStr = atMax ? "MAX LEVEL" : "Level " + level + " / " + maxLevel;
        int lx = rightX + (rightW - font.width(levelStr)) / 2;
        g.drawString(font, levelStr, lx, y + 2, WHITE, false);
        y += LEVEL_BAR_H + 4;

        // -- Current and next effect values ------------------------------------
        String nowStr = "Now:  " + getEffectAtLevel(selectedType, level);
        g.drawString(font, nowStr, rightX + 3, y, level > 0 ? GREEN : INK_GHOST, false);
        y += 9;

        if (!atMax) {
            String nextStr = "Next: " + getNextLevelGain(selectedType);
            g.drawString(font, nextStr, rightX + 3, y, GOLD, false);
        }
        y += 10;

        // -- War chest balance -------------------------------------------------
        drawSeparator(g, rightX, y, rightW);
        y += 5;
        drawCard(g, rightX, y, rightW, 14, false);
        g.drawString(font, "War Chest:", rightX + 3, y + 3, INK_FAINT, false);
        String balStr = String.format("%,d $", treasuryBalance);
        g.drawString(font, balStr, rightX + rightW - font.width(balStr) - 3, y + 3,
                treasuryBalance > 0 ? INK : DANGER, false);
        y += 18;

        // -- Cost + buy button (or max badge) ----------------------------------
        if (atMax) {
            int maxBg = 0xFF2A6E2A;
            g.fill(rightX, y, rightX + rightW, y + LEVEL_BAR_H, maxBg);
            String maxLabel = "Fully Upgraded";
            g.drawString(font, maxLabel, rightX + (rightW - font.width(maxLabel)) / 2, y + 2, WHITE, false);
            y += LEVEL_BAR_H + 5;
        } else {
            String costStr = String.format("Cost: %,d $", cost);
            g.drawString(font, costStr, rightX + 3, y, canAfford ? INK : DANGER, false);
            y += 11;

            buyButtonY = y;
            if (canAfford) {
                drawButton(g, font, "Buy Investment", rightX, y, rightW, BUY_BTN_H, mouseX, mouseY, GOLD);
            } else {
                g.fill(rightX, y, rightX + rightW, y + BUY_BTN_H, CARD_BORDER);
                g.fill(rightX + 1, y + 1, rightX + rightW - 1, y + BUY_BTN_H - 1, PARCHMENT_BG);
                String label = "Buy Investment";
                g.drawString(font, label, rightX + (rightW - font.width(label)) / 2,
                        y + (BUY_BTN_H - 8) / 2, INK_GHOST, false);
            }
            y += BUY_BTN_H + 5;
        }

        // -- Full description below the action area ----------------------------
        drawSeparator(g, rightX, y, rightW);
        y += 5;
        for (String line : getFullDescription(selectedType)) {
            g.drawString(font, line, rightX + 3, y, INK_FAINT, false);
            y += 9;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        ColonyTaxData colony = selectedColonySupplier.get();
        if (colony == null) return false;

        // Left page: row selection
        int y = leftY + 14;
        for (UpgradeType type : TYPES) {
            if (isInBounds(mouseX, mouseY, leftX, y, leftW, ROW_H)) {
                selectedType = type;
                NetworkHandler.sendToServer(new RequestInvestmentDataPayload(colony.getColonyId()));
                return true;
            }
            y += ROW_H;
        }

        // Right page: buy button
        if (selectedType != null) {
            int level = levels.getOrDefault(selectedType.name(), 0);
            boolean atMax = level >= maxLevel;
            int cost = costs.getOrDefault(selectedType.name(), 0);
            boolean canAfford = !atMax && treasuryBalance >= cost && cost > 0;

            if (canAfford && isInBounds(mouseX, mouseY, rightX, buyButtonY, rightW, BUY_BTN_H)) {
                NetworkHandler.sendToServer(new BuyInvestmentPayload(colony.getColonyId(), selectedType.name()));
                // Refresh investment data so levels and war chest balance update live.
                NetworkHandler.sendToServer(new RequestInvestmentDataPayload(colony.getColonyId()));
                return true;
            }
        }

        return false;
    }

    // -- Display helpers --------------------------------------------------------

    private static String getDisplayName(UpgradeType type) {
        return switch (type) {
            case MILITIA        -> "Militia Force";
            case SPY_CAPACITY   -> "Spy Network";
            case SPY_SPEED      -> "Courier Routes";
            case SPY_EVASION    -> "Spy Evasion";
            case RAID_FORCE     -> "Raid Force";
            case DEFENSE        -> "Fortified Walls";
            case TREASURY_CAP   -> "Treasury Vault";
            case TAX_EFFICIENCY -> "Tax Office";
            case FORTIFICATION  -> "Battlements";
            case COUNTER_INTEL  -> "Counter-Intel";
        };
    }

    /** Cumulative effect value at the given level. Returns "None" at level 0. */
    private static String getEffectAtLevel(UpgradeType type, int level) {
        if (level == 0) return "None";
        return switch (type) {
            case MILITIA ->
                String.format("+%.0f%% militia", level * TaxConfig.getUpgradeMilitiaMultiplierPerLevel() * 100);
            case SPY_CAPACITY ->
                "+" + (level * TaxConfig.getUpgradeSpyCapacityPerLevel()) + " spy slots";
            case SPY_SPEED ->
                String.format("+%.0f%% travel spd", level * TaxConfig.getUpgradeSpySpeedMultiplierPerLevel() * 100);
            case SPY_EVASION ->
                String.format("-%.0f%% detection", level * TaxConfig.getUpgradeDetectionReductionPerLevel() * 100);
            case RAID_FORCE ->
                String.format("+%.0f%% raid loot", level * TaxConfig.getUpgradeRaidForceMultiplierPerLevel() * 100);
            case DEFENSE ->
                "+" + (level * TaxConfig.getUpgradeDefenseBonusPerLevel()) + " def levels";
            case TREASURY_CAP ->
                String.format("+%,d$ cap", level * TaxConfig.getUpgradeTreasuryCapFlatPerLevel());
            case TAX_EFFICIENCY ->
                String.format("+%.0f%% tax eff.", level * TaxConfig.getUpgradeTaxEfficiencyPercentPerLevel() * 100);
            case FORTIFICATION ->
                String.format("-%.0f%% siege dmg", Math.min(75,
                        level * TaxConfig.getUpgradeFortificationDamageReductionPerLevel() * 100));
            case COUNTER_INTEL ->
                String.format("+%.0f%% spy detect", Math.min(90,
                        level * TaxConfig.getUpgradeCounterIntelDetectChancePerLevel() * 100));
        };
    }

    /** Per-level incremental gain shown as "Next" value. */
    private static String getNextLevelGain(UpgradeType type) {
        return switch (type) {
            case MILITIA ->
                String.format("+%.0f%% militia", TaxConfig.getUpgradeMilitiaMultiplierPerLevel() * 100);
            case SPY_CAPACITY ->
                "+" + TaxConfig.getUpgradeSpyCapacityPerLevel() + " spy slots";
            case SPY_SPEED ->
                String.format("+%.0f%% travel spd", TaxConfig.getUpgradeSpySpeedMultiplierPerLevel() * 100);
            case SPY_EVASION ->
                String.format("-%.0f%% detection", TaxConfig.getUpgradeDetectionReductionPerLevel() * 100);
            case RAID_FORCE ->
                String.format("+%.0f%% raid loot", TaxConfig.getUpgradeRaidForceMultiplierPerLevel() * 100);
            case DEFENSE ->
                "+" + TaxConfig.getUpgradeDefenseBonusPerLevel() + " def levels";
            case TREASURY_CAP ->
                String.format("+%,d$ cap", TaxConfig.getUpgradeTreasuryCapFlatPerLevel());
            case TAX_EFFICIENCY ->
                String.format("+%.0f%% tax eff.", TaxConfig.getUpgradeTaxEfficiencyPercentPerLevel() * 100);
            case FORTIFICATION ->
                String.format("+%.0f%% siege red.", TaxConfig.getUpgradeFortificationDamageReductionPerLevel() * 100);
            case COUNTER_INTEL ->
                String.format("+%.0f%% spy detect", TaxConfig.getUpgradeCounterIntelDetectChancePerLevel() * 100);
        };
    }

    /** Multi-line description displayed below the Buy button. */
    private static String[] getFullDescription(UpgradeType type) {
        return switch (type) {
            case MILITIA ->
                new String[]{"Increases the number of", "militia your colony can", "field during a war."};
            case SPY_CAPACITY ->
                new String[]{"Allows more concurrent", "spy missions to run from", "this colony at once."};
            case SPY_SPEED ->
                new String[]{"Cuts travel time for", "spies en route to and", "from target colonies."};
            case SPY_EVASION ->
                new String[]{"Lowers the chance that", "your spies are caught", "when they arrive."};
            case RAID_FORCE ->
                new String[]{"Your raids extract more", "tax from the enemy", "treasury on victory."};
            case DEFENSE ->
                new String[]{"Raises the effective", "guard defense level,", "reducing damage taken."};
            case TREASURY_CAP ->
                new String[]{"Raises the maximum funds", "the colony treasury can", "hold at one time."};
            case TAX_EFFICIENCY ->
                new String[]{"Adds bonus income to", "the tax collected each", "tax cycle."};
            case FORTIFICATION ->
                new String[]{"Reduces the damage dealt", "to your colony during", "siege battles."};
            case COUNTER_INTEL ->
                new String[]{"Detects and disrupts", "incoming enemy spies", "when they arrive."};
        };
    }
}
