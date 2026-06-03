package net.machiavelli.minecolonytax.gui.book;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.integration.CurrencyService;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.packets.RequestTreasuryDataPacket;
import net.machiavelli.minecolonytax.network.packets.TreasuryActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static net.machiavelli.minecolonytax.gui.book.BookRenderHelper.*;

/**
 * Treasury tab: left page shows treasury summary, right page has source-selector and deposit/withdraw controls.
 *
 * The right page lets the player choose where funds come from (deposit) or go (withdraw):
 *   TAX       — colony's accumulated tax balance
 *   WALLET    — SDMShop / SDMEconomy balance (grayed when unavailable)
 *   INVENTORY — physical currency items in the player's inventory
 */
public class TreasuryPage extends BookPage {

    private final Supplier<ColonyTaxData> selectedColonySupplier;
    private final Consumer<EditBox> addWidgetCallback;
    private final Consumer<Button> addButtonCallback;

    // Minecraft widgets
    private EditBox amountInput;
    private Button depositButton;
    private Button withdrawButton;

    // Treasury data from server
    private int balance = 0;
    private int maxCapacity = 0;
    private int drainPerMin = 0;
    private int taxBalance = 0;
    private boolean autoSurrender = false;
    private double minPercent = 0.0;
    /** -1 = wallet not available on this server */
    private long walletBalance = -1L;

    // Selected source for deposit / destination for withdraw
    private CurrencyService.Source selectedSource = CurrencyService.Source.TAX_BALANCE;
    /** True once we've auto-switched to WALLET on first data arrival when SDMShop is available. */
    private boolean hasAutoSelectedWallet = false;

    // Tab button rects — computed in repositionWidgets, used in mouseClicked
    private int tabY, tabH;
    private int[] tabX = new int[3];
    private int[] tabW = new int[3];

    private static final CurrencyService.Source[] SOURCES = CurrencyService.Source.values();

    public TreasuryPage(Screen screen, Font font,
                        Supplier<ColonyTaxData> selectedColonySupplier,
                        Consumer<EditBox> addWidgetCallback,
                        Consumer<Button> addButtonCallback) {
        super(screen, font);
        this.selectedColonySupplier = selectedColonySupplier;
        this.addWidgetCallback = addWidgetCallback;
        this.addButtonCallback = addButtonCallback;
    }

    @Override
    public void init() {
        amountInput = new EditBox(font, 0, 0, 80, 14, Component.literal("Amount"));
        amountInput.setMaxLength(9);
        amountInput.setFilter(s -> s.matches("\\d*"));
        amountInput.setVisible(false);
        addWidgetCallback.accept(amountInput);

        depositButton = Button.builder(Component.literal("Deposit"), btn -> doAction(TreasuryActionPacket.ActionType.DEPOSIT))
                .bounds(0, 0, 38, 14).build();
        depositButton.visible = false;
        addButtonCallback.accept(depositButton);

        withdrawButton = Button.builder(Component.literal("Withdraw"), btn -> doAction(TreasuryActionPacket.ActionType.WITHDRAW))
                .bounds(0, 0, 44, 14).build();
        withdrawButton.visible = false;
        addButtonCallback.accept(withdrawButton);
    }

    @Override
    public void setWidgetsVisible(boolean visible) {
        if (amountInput != null) amountInput.setVisible(visible);
        if (depositButton != null) depositButton.visible = visible;
        if (withdrawButton != null) withdrawButton.visible = visible;
    }

    @Override
    public void onActivated() {
        hasAutoSelectedWallet = false; // re-evaluate default source on each page open
        selectedSource = CurrencyService.Source.TAX_BALANCE;
        ColonyTaxData colony = selectedColonySupplier.get();
        setWidgetsVisible(colony != null);
        repositionWidgets();
        if (colony != null) {
            NetworkHandler.sendToServer(new RequestTreasuryDataPacket(colony.getColonyId()));
        }
    }

    @Override
    public void onDeactivated() {
        setWidgetsVisible(false);
    }

    /**
     * Called by the shell when treasury data arrives from server.
     */
    public void updateTreasuryData(int balance, int maxCapacity, int drainPerMinute,
                                    int taxBalance, boolean autoSurrender, double minPercentForWar,
                                    long walletBalance) {
        this.balance = balance;
        this.maxCapacity = maxCapacity;
        this.drainPerMin = drainPerMinute;
        this.taxBalance = taxBalance;
        this.autoSurrender = autoSurrender;
        this.minPercent = minPercentForWar;
        this.walletBalance = walletBalance;

        // Auto-select WALLET on first data arrival when SDMShop is available
        if (!hasAutoSelectedWallet && walletBalance >= 0) {
            selectedSource = CurrencyService.Source.WALLET;
            hasAutoSelectedWallet = true;
        }
        // If currently on WALLET and wallet just became unavailable, fall back to TAX
        if (selectedSource == CurrencyService.Source.WALLET && walletBalance < 0) {
            selectedSource = CurrencyService.Source.TAX_BALANCE;
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────

    private void repositionWidgets() {
        if (amountInput == null) return;

        int x = rightX + 2;
        int w = rightW - 4;

        // Tabs: 3 equal-width buttons with 2px gaps
        tabY = rightY + 28;
        tabH = 12;
        int totalGap = 4; // 2 gaps of 2px
        int eachW = (w - totalGap) / 3;
        int remainder = (w - totalGap) - eachW * 3;
        tabX[0] = x;           tabW[0] = eachW + (remainder > 0 ? 1 : 0);
        tabX[1] = tabX[0] + tabW[0] + 2; tabW[1] = eachW + (remainder > 1 ? 1 : 0);
        tabX[2] = tabX[1] + tabW[1] + 2; tabW[2] = eachW;

        // Amount input below tabs + balance card
        int inputY = tabY + tabH + 18; // tab + 2px gap + balance card (14px) + 2px gap
        amountInput.setX(x);
        amountInput.setY(inputY);
        amountInput.setWidth(w);

        int btnY = inputY + 16;
        int halfW = (w - 2) / 2;
        depositButton.setX(x);
        depositButton.setY(btnY);
        depositButton.setWidth(halfW);
        withdrawButton.setX(x + halfW + 2);
        withdrawButton.setY(btnY);
        withdrawButton.setWidth(halfW);
    }

    // ── Left page ─────────────────────────────────────────────────────────

    @Override
    public void renderLeftPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawHeading(g, font, "Treasury", leftX, leftY, leftW);
        int y = leftY + 14;

        ColonyTaxData colony = selectedColonySupplier.get();
        if (colony == null) {
            g.drawString(font, "Select a colony", leftX + 4, y + 10, INK_GHOST, false);
            g.drawString(font, "from Colonies tab", leftX + 4, y + 22, INK_GHOST, false);
            return;
        }

        String colName = truncate(font, colony.getColonyName(), leftW - 6);
        g.drawString(font, colName, leftX + 2, y, INK_FAINT, false);
        y += 10;

        // Total treasury card
        drawCard(g, leftX, y, leftW, 22, true);
        g.drawString(font, "TOTAL TREASURY", leftX + 3, y + 2, INK_FAINT, false);
        String balStr = String.format("%,d / %,d $", balance, maxCapacity);
        int balColor = balance > 0 ? INK : DANGER;
        g.drawString(font, balStr, leftX + leftW / 2 - font.width(balStr) / 2, y + 12, balColor, false);
        y += 24;

        // Fill bar
        int barW = leftW - 4;
        int barH = 6;
        float fillPct = maxCapacity > 0 ? (float) balance / maxCapacity : 0;
        g.fill(leftX + 2, y, leftX + 2 + barW, y + barH, CARD_BORDER);
        if (fillPct > 0) {
            int fillW = (int) (barW * Math.min(1, fillPct));
            int barColor = fillPct > 0.5 ? GREEN : (fillPct > 0.25 ? GOLD_DARK : DANGER);
            g.fill(leftX + 3, y + 1, leftX + 3 + fillW, y + barH - 1, barColor);
        }
        y += 10;

        // Auto-surrender + Drain rate
        int halfW = (leftW - 2) / 2;
        drawCard(g, leftX, y, halfW, 18, false);
        g.drawString(font, "AUTO-SURR", leftX + 3, y + 2, INK_FAINT, false);
        String autoStr = autoSurrender ? "@ " + (int)(minPercent * 100) + "%" : "OFF";
        g.drawString(font, autoStr, leftX + 3, y + 10, autoSurrender ? DANGER : GREEN, false);

        drawCard(g, leftX + halfW + 2, y, halfW, 18, false);
        g.drawString(font, "DRAIN", leftX + halfW + 5, y + 2, INK_FAINT, false);
        String drainStr = drainPerMin > 0 ? "-" + drainPerMin + "/min" : "None";
        g.drawString(font, drainStr, leftX + halfW + 5, y + 10, drainPerMin > 0 ? DANGER : INK_GHOST, false);
    }

    // ── Right page ────────────────────────────────────────────────────────

    @Override
    public void renderRightPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawHeading(g, font, "Fund Transfer", rightX, rightY, rightW);

        ColonyTaxData colony = selectedColonySupplier.get();
        if (colony == null) {
            g.drawString(font, "Select a colony", rightX + 4, rightY + 20, INK_GHOST, false);
            return;
        }

        setWidgetsVisible(true);
        repositionWidgets();

        // ── Source selector tabs ──
        renderSourceTabs(g, mouseX, mouseY);

        // ── Selected source balance card ──
        int cardY = tabY + tabH + 2;
        renderSelectedSourceBalance(g, cardY);
    }

    private void renderSourceTabs(GuiGraphics g, int mouseX, int mouseY) {
        String[] labels = { "Tax", "Wallet", "Invent" };
        for (int i = 0; i < 3; i++) {
            CurrencyService.Source src = SOURCES[i];
            boolean selected = src == selectedSource;
            boolean unavailable = src == CurrencyService.Source.WALLET && walletBalance < 0;
            boolean hovered = !unavailable
                    && mouseX >= tabX[i] && mouseX < tabX[i] + tabW[i]
                    && mouseY >= tabY && mouseY < tabY + tabH;

            // Border and background
            int border, bg;
            if (selected) {
                border = GOLD;
                bg = GOLD_CARD_BG;
            } else if (unavailable) {
                border = CARD_BORDER;
                bg = 0x00000000;
            } else if (hovered) {
                border = INK_FAINT;
                bg = PARCHMENT_BG;
            } else {
                border = CARD_BORDER;
                bg = 0x00000000;
            }

            g.fill(tabX[i], tabY, tabX[i] + tabW[i], tabY + tabH, border);
            g.fill(tabX[i] + 1, tabY + 1, tabX[i] + tabW[i] - 1, tabY + tabH - 1, bg);

            int textColor = selected ? INK : (unavailable ? INK_GHOST : (hovered ? INK : INK_FAINT));
            int tw = font.width(labels[i]);
            g.drawString(font, labels[i],
                    tabX[i] + tabW[i] / 2 - tw / 2,
                    tabY + (tabH - 8) / 2,
                    textColor, false);
        }
    }

    private void renderSelectedSourceBalance(GuiGraphics g, int y) {
        drawCard(g, rightX + 2, y, rightW - 4, 14, false);

        switch (selectedSource) {
            case TAX_BALANCE -> {
                g.drawString(font, "Tax balance:", rightX + 4, y + 3, INK_FAINT, false);
                String val = String.format("%,d $", taxBalance);
                g.drawString(font, val, rightX + rightW - font.width(val) - 4, y + 3,
                        taxBalance > 0 ? INK : INK_GHOST, false);
            }
            case WALLET -> {
                g.drawString(font, "Wallet:", rightX + 4, y + 3, INK_FAINT, false);
                if (walletBalance < 0) {
                    g.drawString(font, "unavailable", rightX + rightW - font.width("unavailable") - 4,
                            y + 3, INK_GHOST, false);
                } else {
                    String val = String.format("%,d $", walletBalance);
                    g.drawString(font, val, rightX + rightW - font.width(val) - 4, y + 3,
                            walletBalance > 0 ? INK : INK_GHOST, false);
                }
            }
            case INVENTORY -> {
                g.drawString(font, "Inventory:", rightX + 4, y + 3, INK_FAINT, false);
                int count = countInventoryCurrency();
                String itemLabel = getShortItemName();
                String val = count + " " + itemLabel;
                g.drawString(font, val, rightX + rightW - font.width(val) - 4, y + 3,
                        count > 0 ? INK : INK_GHOST, false);
            }
        }
    }

    // ── Mouse interaction ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (selectedColonySupplier.get() == null) return false;

        for (int i = 0; i < 3; i++) {
            CurrencyService.Source src = SOURCES[i];
            boolean unavailable = src == CurrencyService.Source.WALLET && walletBalance < 0;
            if (!unavailable
                    && mouseX >= tabX[i] && mouseX < tabX[i] + tabW[i]
                    && mouseY >= tabY && mouseY < tabY + tabH) {
                selectedSource = src;
                return true;
            }
        }
        return false;
    }

    // ── Action dispatch ───────────────────────────────────────────────────

    private void doAction(TreasuryActionPacket.ActionType action) {
        ColonyTaxData colony = selectedColonySupplier.get();
        if (colony == null || amountInput == null) return;
        String val = amountInput.getValue();
        if (val.isEmpty()) return;
        try {
            int amount = Integer.parseInt(val);
            if (amount <= 0) return;
            NetworkHandler.sendToServer(
                    new TreasuryActionPacket(colony.getColonyId(), action, amount, selectedSource));
            amountInput.setValue("");
            NetworkHandler.sendToServer(new RequestTreasuryDataPacket(colony.getColonyId()));
        } catch (NumberFormatException ignored) {}
    }

    // ── Inventory helpers (client-side) ───────────────────────────────────

    private int countInventoryCurrency() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        return net.machiavelli.minecolonytax.util.ItemUtils.countInventoryValue(mc.player.getInventory());
    }

    /** Returns a compact label: "currency" in multi-denom mode, else the item path. */
    private String getShortItemName() {
        if (net.machiavelli.minecolonytax.util.ItemUtils.isMultiDenominationMode()) {
            return "currency";
        }
        String full = TaxConfig.getCurrencyItemName();
        int colon = full.indexOf(':');
        return colon >= 0 ? full.substring(colon + 1) : full;
    }
}
