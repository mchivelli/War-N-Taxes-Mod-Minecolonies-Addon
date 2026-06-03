package net.machiavelli.minecolonytax.gui.book;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.packets.RequestWarChestDataPayload;
import net.machiavelli.minecolonytax.network.packets.WarChestActionPayload;
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
 * War Chest tab: left page shows war fund summary, right page has deposit/withdraw controls.
 * This is the only page that manages Minecraft widgets (EditBox, Buttons).
 */
public class WarChestPage extends BookPage {

    private final Supplier<ColonyTaxData> selectedColonySupplier;
    private final Consumer<EditBox> addWidgetCallback;
    private final Consumer<Button> addButtonCallback;

    // Minecraft widgets
    private EditBox amountInput;
    private Button depositButton;
    private Button withdrawButton;

    // War chest data from server
    private int balance = 0;
    private int maxCapacity = 0;
    private int drainPerMin = 0;
    private int taxBalance = 0;
    private boolean autoSurrender = false;
    private double minPercent = 0.0;

    public WarChestPage(Screen screen, Font font,
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
        // Create widgets at temporary positions -- repositioned in setLayout
        amountInput = new EditBox(font, 0, 0, 80, 14, Component.literal("Amount"));
        amountInput.setMaxLength(9);
        amountInput.setFilter(s -> s.matches("\\d*"));
        amountInput.setVisible(false);
        addWidgetCallback.accept(amountInput);

        depositButton = Button.builder(Component.literal("Deposit"), btn -> doDeposit())
                .bounds(0, 0, 38, 14).build();
        depositButton.visible = false;
        addButtonCallback.accept(depositButton);

        withdrawButton = Button.builder(Component.literal("Withdraw"), btn -> doWithdraw())
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
        ColonyTaxData colony = selectedColonySupplier.get();
        // Only show widgets when a colony is selected
        setWidgetsVisible(colony != null);
        repositionWidgets();
        if (colony != null) {
            NetworkHandler.sendToServer(new RequestWarChestDataPayload(colony.getColonyId()));
        }
    }

    @Override
    public void onDeactivated() {
        setWidgetsVisible(false);
    }

    /**
     * Repositions widgets to the right page area.
     */
    private void repositionWidgets() {
        if (amountInput == null) return;
        int x = rightX + 2;
        int y = rightY + 30;
        int w = rightW - 4;
        amountInput.setX(x);
        amountInput.setY(y);
        amountInput.setWidth(w);

        int btnY = y + 16;
        int halfW = (w - 2) / 2;
        depositButton.setX(x);
        depositButton.setY(btnY);
        depositButton.setWidth(halfW);
        withdrawButton.setX(x + halfW + 2);
        withdrawButton.setY(btnY);
        withdrawButton.setWidth(halfW);
    }

    /**
     * Called by the shell when war chest data arrives from server.
     */
    public void updateWarChestData(int balance, int maxCapacity, int drainPerMinute,
                                    int taxBalance, boolean autoSurrender, double minPercentForWar) {
        this.balance = balance;
        this.maxCapacity = maxCapacity;
        this.drainPerMin = drainPerMinute;
        this.taxBalance = taxBalance;
        this.autoSurrender = autoSurrender;
        this.minPercent = minPercentForWar;
    }

    @Override
    public void renderLeftPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawHeading(g, font, "War Chest", leftX, leftY, leftW);
        int y = leftY + 14;

        ColonyTaxData colony = selectedColonySupplier.get();
        if (colony == null) {
            g.drawString(font, "Select a colony", leftX + 4, y + 10, INK_GHOST, false);
            g.drawString(font, "from Colonies tab", leftX + 4, y + 22, INK_GHOST, false);
            return;
        }

        // Total War Funds card
        drawCard(g, leftX, y, leftW, 22, true);
        g.drawString(font, "TOTAL WAR FUNDS", leftX + 3, y + 2, INK_FAINT, false);
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

        // Auto-Surrender + Active Wars
        int halfW = (leftW - 2) / 2;
        drawCard(g, leftX, y, halfW, 18, false);
        g.drawString(font, "AUTO-SURR", leftX + 3, y + 2, INK_FAINT, false);
        String autoStr = autoSurrender ? "@ " + (int)(minPercent * 100) + "%" : "OFF";
        int autoColor = autoSurrender ? DANGER : GREEN;
        g.drawString(font, autoStr, leftX + 3, y + 10, autoColor, false);

        drawCard(g, leftX + halfW + 2, y, halfW, 18, false);
        g.drawString(font, "DRAIN", leftX + halfW + 5, y + 2, INK_FAINT, false);
        String drainStr = drainPerMin > 0 ? "-" + drainPerMin + " $/min" : "None";
        g.drawString(font, drainStr, leftX + halfW + 5, y + 10, drainPerMin > 0 ? DANGER : INK_GHOST, false);
    }

    @Override
    public void renderRightPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ColonyTaxData colony = selectedColonySupplier.get();

        drawHeading(g, font, "Fund Transfer", rightX, rightY, rightW);
        int y = rightY + 14;

        if (colony == null) {
            g.drawString(font, "Select a colony", rightX + 4, y + 10, INK_GHOST, false);
            return;
        }

        // Tax balance card
        drawCard(g, rightX, y, rightW, 14, true);
        g.drawString(font, "YOUR TAX BAL", rightX + 3, y + 2, INK_FAINT, false);
        g.drawString(font, taxBalance + " $", rightX + rightW - font.width(taxBalance + " $") - 3,
                y + 2, INK, false);
        // Widgets (amountInput, deposit, withdraw) are positioned automatically
        // and rendered by Minecraft's widget system via addRenderableWidget

        // Ensure widgets are visible now that colony is selected
        setWidgetsVisible(true);
        repositionWidgets();
    }

    private void doDeposit() {
        ColonyTaxData colony = selectedColonySupplier.get();
        if (colony == null || amountInput == null) return;
        try {
            String val = amountInput.getValue();
            if (val.isEmpty()) return;
            int amount = Integer.parseInt(val);
            if (amount <= 0) return;
            NetworkHandler.sendToServer(new WarChestActionPayload(
                    colony.getColonyId(), WarChestActionPayload.ActionType.DEPOSIT, amount));
            amountInput.setValue("");
        } catch (NumberFormatException ignored) {}
    }

    private void doWithdraw() {
        ColonyTaxData colony = selectedColonySupplier.get();
        if (colony == null || amountInput == null) return;
        try {
            String val = amountInput.getValue();
            if (val.isEmpty()) return;
            int amount = Integer.parseInt(val);
            if (amount <= 0) return;
            NetworkHandler.sendToServer(new WarChestActionPayload(
                    colony.getColonyId(), WarChestActionPayload.ActionType.WITHDRAW, amount));
            amountInput.setValue("");
        } catch (NumberFormatException ignored) {}
    }
}
