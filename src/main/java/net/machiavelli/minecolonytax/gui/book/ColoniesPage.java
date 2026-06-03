package net.machiavelli.minecolonytax.gui.book;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicy;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.packets.ClaimTaxPayload;
import net.machiavelli.minecolonytax.network.packets.PayTaxDebtPayload;
import net.machiavelli.minecolonytax.network.packets.SetTaxPolicyPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static net.machiavelli.minecolonytax.gui.book.BookRenderHelper.*;

/**
 * Colonies tab: left page shows colony list, right page shows selected colony detail or events.
 */
public class ColoniesPage extends BookPage {

    private final Supplier<List<ColonyTaxData>> coloniesSupplier;
    private final Supplier<ColonyTaxData> selectedSupplier;
    private final Consumer<ColonyTaxData> selectCallback;

    private int scrollOffset = 0;
    private boolean showingEvents = false;

    public ColoniesPage(Screen screen, Font font,
                        Supplier<List<ColonyTaxData>> coloniesSupplier,
                        Supplier<ColonyTaxData> selectedSupplier,
                        Consumer<ColonyTaxData> selectCallback) {
        super(screen, font);
        this.coloniesSupplier = coloniesSupplier;
        this.selectedSupplier = selectedSupplier;
        this.selectCallback = selectCallback;
    }

    @Override
    public void onActivated() {
        scrollOffset = 0;
        showingEvents = false;
    }

    // --- Left page: colony list ---

    @Override
    public void renderLeftPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<ColonyTaxData> colonies = coloniesSupplier.get();
        ColonyTaxData selected = selectedSupplier.get();

        drawHeading(g, font, "Your Colonies", leftX, leftY, leftW);
        int y = leftY + 14;

        if (colonies.isEmpty()) {
            g.drawString(font, "No colonies found.", leftX + 4, y + 20, INK_GHOST, false);
            return;
        }

        int itemH = 22;
        int maxVisible = (leftH - 14) / itemH;
        // Clamp scrollOffset if colony list shrank since last render
        int maxOffset = Math.max(0, colonies.size() - maxVisible);
        if (scrollOffset > maxOffset) scrollOffset = maxOffset;
        int visible = Math.min(maxVisible, colonies.size() - scrollOffset);

        for (int i = 0; i < visible; i++) {
            ColonyTaxData colony = colonies.get(i + scrollOffset);
            int iy = y + i * itemH;
            boolean isSelected = selected != null && selected.getColonyId() == colony.getColonyId();
            boolean hovered = mouseX >= leftX && mouseX < leftX + leftW
                    && mouseY >= iy && mouseY < iy + itemH;

            // Background
            if (isSelected) {
                g.fill(leftX, iy, leftX + leftW, iy + itemH, 0x182C1E0E);
                g.fill(leftX, iy, leftX + 2, iy + itemH, GOLD);
            } else if (hovered) {
                g.fill(leftX, iy, leftX + leftW, iy + itemH, 0x082C1E0E);
            }

            // Separator
            g.fill(leftX, iy + itemH - 1, leftX + leftW, iy + itemH, CARD_BORDER);

            // Name
            String name = truncate(font, colony.getColonyName(), leftW - 6);
            if (colony.isOwner()) name = "\u2605 " + name;
            g.drawString(font, name, leftX + 4, iy + 2, INK, false);

            // Sub line: status + balance with proper spacing
            String sub;
            if (colony.hasDebt()) {
                sub = "In Debt  -" + colony.getDebtAmount() + "$";
            } else {
                sub = getStatusText(colony) + "  " + colony.getTaxBalance() + "$";
            }
            g.drawString(font, sub, leftX + 4, iy + 12, getStatusColor(colony), false);
        }

        // Scrollbar
        drawScrollbar(g, leftX + leftW - 4, y, leftH - 14, colonies.size(), maxVisible, scrollOffset);
    }

    // --- Right page: detail or events ---

    @Override
    public void renderRightPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ColonyTaxData selected = selectedSupplier.get();

        if (selected == null) {
            g.drawString(font, "Select a colony", rightX + 10, rightY + rightH / 2 - 10, INK_GHOST, false);
            g.drawString(font, "from the left page...", rightX + 4, rightY + rightH / 2 + 2, INK_GHOST, false);
            return;
        }

        if (showingEvents) {
            renderEventsView(g, selected, mouseX, mouseY);
        } else {
            renderDetailView(g, selected, mouseX, mouseY);
        }
    }

    private void renderDetailView(GuiGraphics g, ColonyTaxData colony, int mouseX, int mouseY) {
        int y = rightY;
        int w = rightW;
        int x = rightX;

        // Header: colony name (full width, no badge -- badge was eating into name space)
        String name = truncate(font, colony.getColonyName(), w - 4);
        g.drawString(font, name, x, y, INK, false);
        drawSeparator(g, x, y + 10, w);
        y += 14;

        // Tax Balance card
        drawCard(g, x, y, w, 18, true);
        g.drawString(font, "TAX BALANCE", x + 3, y + 2, INK_FAINT, false);
        String balStr = colony.getTaxBalance() + " / " + colony.getMaxTaxRevenue() + " $";
        g.drawString(font, balStr, x + 3, y + 10, colony.hasDebt() ? DANGER : INK, false);
        y += 20;

        // Debt card (if applicable)
        if (colony.hasDebt()) {
            drawCard(g, x, y, w, 14, false);
            g.drawString(font, "DEBT", x + 3, y + 2, INK_FAINT, false);
            String debtStr = String.valueOf(colony.getDebtAmount());
            g.drawString(font, debtStr, x + w - font.width(debtStr) - 3, y + 2, DANGER, false);
            y += 16;
        }

        // Est. Tax per Interval card (full-width)
        drawCard(g, x, y, w, 18, false);
        g.drawString(font, "EST. TAX / INTERVAL", x + 3, y + 2, INK_FAINT, false);
        g.drawString(font, "~ " + colony.getApproximateRevenuePerInterval() + " $", x + 3, y + 10, INK, false);
        y += 20;

        // Infrastructure card (full-width, two labeled rows)
        drawCard(g, x, y, w, 28, false);
        g.drawString(font, "INFRASTRUCTURE", x + 3, y + 2, INK_FAINT, false);
        g.drawString(font, "Buildings", x + 5, y + 12, INK_FAINT, false);
        String bldgStr = String.valueOf(colony.getBuildingCount());
        g.drawString(font, bldgStr, x + w - font.width(bldgStr) - 5, y + 12, INK, false);
        g.drawString(font, "Military", x + 5, y + 21, INK_FAINT, false);
        String milStr = String.valueOf(colony.getGuardCount());
        g.drawString(font, milStr, x + w - font.width(milStr) - 5, y + 21, INK, false);
        y += 30;

        // Tax Policy selector
        if (TaxConfig.isTaxPoliciesEnabled() && colony.isOwner()) {
            g.drawString(font, "Tax Policy", x, y, INK_FAINT, false);
            y += 10;
            TaxPolicy currentPolicy = TaxPolicy.fromString(colony.getTaxPolicy());
            if (currentPolicy == null) currentPolicy = TaxPolicy.NORMAL;
            TaxPolicy[] policies = TaxPolicy.values();
            int btnW = (w - (policies.length - 1)) / policies.length;
            for (int i = 0; i < policies.length; i++) {
                TaxPolicy p = policies[i];
                int bx = x + i * (btnW + 1);
                boolean active = p == currentPolicy;
                boolean hov = mouseX >= bx && mouseX < bx + btnW && mouseY >= y && mouseY < y + 11;
                int bg = active ? INK : (hov ? 0x302C1E0E : PARCHMENT_BG);
                int fg = active ? GOLD : INK_FAINT;
                g.fill(bx, y, bx + btnW, y + 11, active ? GOLD_DARK : INK_GHOST);
                g.fill(bx + 1, y + 1, bx + btnW - 1, y + 10, bg);
                String label = p.name().substring(0, Math.min(4, p.name().length()));
                g.drawCenteredString(font, label, bx + btnW / 2, y + 2, fg);
            }
            y += 14;
        }

        // Vassal info
        if (colony.isVassal()) {
            drawBadge(g, font, "Vassal " + colony.getVassalTributeRate() + "%", x, y, ORANGE);
            y += 12;
        } else if (colony.hasVassals()) {
            drawBadge(g, font, "Overlord (" + colony.getVassalCount() + ")", x, y, GREEN);
            y += 12;
        }

        // Action buttons at bottom
        int btnY = rightY + rightH - 12;
        int btnW = (w - 4) / 3;

        // Claim button
        if (colony.canClaimTax() && colony.getTaxBalance() > 0 && !colony.isVassal()) {
            drawButton(g, font, "Claim", x, btnY, btnW, 11, mouseX, mouseY, GREEN);
        }

        // Pay Debt button
        if (colony.getDebtAmount() > 0) {
            drawButton(g, font, "Pay Debt", x + btnW + 2, btnY, btnW, 11, mouseX, mouseY, DANGER);
        }

        // Events button
        drawButton(g, font, "Events", x + (btnW + 2) * 2, btnY, btnW, 11, mouseX, mouseY, BLUE);
    }

    private void renderEventsView(GuiGraphics g, ColonyTaxData colony, int mouseX, int mouseY) {
        int x = rightX;
        int y = rightY;
        int w = rightW;

        g.drawString(font, "Recent Events", x, y, INK, false);
        // Back button
        drawButton(g, font, "Back", x + w - 26, y - 1, 26, 11, mouseX, mouseY, INK_FAINT);
        y += 14;

        drawSeparator(g, x, y - 2, w);

        // Placeholder - events data would come from server
        g.drawString(font, "No recent events.", x + 4, y + 10, INK_GHOST, false);
    }

    // --- Mouse handling ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        List<ColonyTaxData> colonies = coloniesSupplier.get();
        ColonyTaxData selected = selectedSupplier.get();

        // Left page: colony selection
        if (isInLeftPage(mouseX, mouseY)) {
            int y = leftY + 14;
            int itemH = 22;
            int maxVisible = (leftH - 14) / itemH;
            int visible = Math.min(maxVisible, colonies.size() - scrollOffset);
            for (int i = 0; i < visible; i++) {
                int iy = y + i * itemH;
                if (mouseY >= iy && mouseY < iy + itemH) {
                    ColonyTaxData colony = colonies.get(i + scrollOffset);
                    if (selected != null && selected.getColonyId() == colony.getColonyId()) {
                        selectCallback.accept(null);
                    } else {
                        selectCallback.accept(colony);
                    }
                    showingEvents = false;
                    return true;
                }
            }
        }

        // Right page clicks
        if (isInRightPage(mouseX, mouseY) && selected != null) {
            if (showingEvents) {
                // Back button
                if (mouseX >= rightX + rightW - 26 && mouseX < rightX + rightW
                        && mouseY >= rightY - 1 && mouseY < rightY + 10) {
                    showingEvents = false;
                    return true;
                }
            } else {
                // Action buttons at bottom
                int btnY = rightY + rightH - 12;
                int btnW = (rightW - 4) / 3;

                // Claim
                if (selected.canClaimTax() && selected.getTaxBalance() > 0 && !selected.isVassal()) {
                    if (isInBounds(mouseX, mouseY, rightX, btnY, btnW, 11)) {
                        NetworkHandler.sendToServer(new ClaimTaxPayload(selected.getColonyId(), -1));
                        return true;
                    }
                }

                // Pay Debt
                if (selected.getDebtAmount() > 0) {
                    if (isInBounds(mouseX, mouseY, rightX + btnW + 2, btnY, btnW, 11)) {
                        NetworkHandler.sendToServer(new PayTaxDebtPayload(selected.getColonyId()));
                        return true;
                    }
                }

                // Events
                if (isInBounds(mouseX, mouseY, rightX + (btnW + 2) * 2, btnY, btnW, 11)) {
                    showingEvents = true;
                    return true;
                }

                // Policy buttons
                if (TaxConfig.isTaxPoliciesEnabled() && selected.isOwner()) {
                    // Calculate policy button Y position to match rendering
                    int py = rightY + 14 + 20; // after header + balance card
                    if (selected.hasDebt()) py += 16;
                    py += 20; // revenue card
                    py += 30; // infrastructure card
                    py += 10; // "Tax Policy" label
                    TaxPolicy[] policies = TaxPolicy.values();
                    int pBtnW = (rightW - (policies.length - 1)) / policies.length;
                    for (int i = 0; i < policies.length; i++) {
                        int bx = rightX + i * (pBtnW + 1);
                        if (isInBounds(mouseX, mouseY, bx, py, pBtnW, 11)) {
                            NetworkHandler.sendToServer(new SetTaxPolicyPayload(
                                    selected.getColonyId(), policies[i].name()));
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInLeftPage(mouseX, mouseY)) {
            List<ColonyTaxData> colonies = coloniesSupplier.get();
            int maxVisible = (leftH - 14) / 22;
            int maxOffset = Math.max(0, colonies.size() - maxVisible);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) delta));
            return true;
        }
        return false;
    }

    // --- Helpers ---

    private String getStatusText(ColonyTaxData colony) {
        if (colony.isAtWar()) return "At War";
        if (colony.isBeingRaided()) return "Raided";
        if (!colony.canClaimTax()) return "Restricted";
        if (colony.hasDebt()) return "In Debt";
        return "Healthy";
    }

    private int getStatusColor(ColonyTaxData colony) {
        if (colony.isAtWar() || colony.isBeingRaided()) return DANGER;
        if (!colony.canClaimTax()) return GOLD_DARK;
        if (colony.hasDebt()) return DANGER;
        return GREEN;
    }

    private int getPolicyColor(TaxPolicy policy) {
        return switch (policy) {
            case LOW -> GREEN;
            case HIGH -> ORANGE;
            case WAR_ECONOMY -> DANGER;
            default -> INK_FAINT;
        };
    }
}
