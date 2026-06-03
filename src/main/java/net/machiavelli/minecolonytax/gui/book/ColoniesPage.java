package net.machiavelli.minecolonytax.gui.book;

import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicy;
import net.machiavelli.minecolonytax.events.random.EventLogEntry;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.packets.ClaimTaxPacket;
import net.machiavelli.minecolonytax.network.packets.DismissEventPacket;
import net.machiavelli.minecolonytax.network.packets.PayTaxDebtPacket;
import net.machiavelli.minecolonytax.network.packets.SetTaxPolicyPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final Supplier<Map<Integer, List<EventLogEntry>>> eventLogSupplier;
    private final Runnable refreshCallback;

    private int scrollOffset = 0;
    private int eventScrollOffset = 0;
    private boolean showingEvents = false;
    /** Set during renderEventsView each frame; used by renderTooltips to draw hover tooltip. */
    private EventLogEntry hoveredEventEntry = null;

    public ColoniesPage(Screen screen, Font font,
                        Supplier<List<ColonyTaxData>> coloniesSupplier,
                        Supplier<ColonyTaxData> selectedSupplier,
                        Consumer<ColonyTaxData> selectCallback,
                        Supplier<Map<Integer, List<EventLogEntry>>> eventLogSupplier,
                        Runnable refreshCallback) {
        super(screen, font);
        this.coloniesSupplier = coloniesSupplier;
        this.selectedSupplier = selectedSupplier;
        this.selectCallback = selectCallback;
        this.eventLogSupplier = eventLogSupplier;
        this.refreshCallback = refreshCallback;
    }

    @Override
    public void onActivated() {
        scrollOffset = 0;
        eventScrollOffset = 0;
        showingEvents = false;
    }


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

            if (isSelected) {
                g.fill(leftX, iy, leftX + leftW, iy + itemH, 0x182C1E0E);
                g.fill(leftX, iy, leftX + 2, iy + itemH, GOLD);
            } else if (hovered) {
                g.fill(leftX, iy, leftX + leftW, iy + itemH, 0x082C1E0E);
            }

            g.fill(leftX, iy + itemH - 1, leftX + leftW, iy + itemH, CARD_BORDER);

            String name = truncate(font, colony.getColonyName(), leftW - 6);
            if (colony.isOwner()) name = "\u2605 " + name;
            g.drawString(font, name, leftX + 4, iy + 2, INK, false);

            String sub;
            if (colony.hasDebt()) {
                sub = "In Debt  -" + colony.getDebtAmount() + "$";
            } else {
                sub = getStatusText(colony) + "  " + colony.getTaxBalance() + "$";
            }
            g.drawString(font, sub, leftX + 4, iy + 12, getStatusColor(colony), false);
        }

        drawScrollbar(g, leftX + leftW - 4, y, leftH - 14, colonies.size(), maxVisible, scrollOffset);
    }


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

        // No status badge here — it was crowding the colony name.
        String name = truncate(font, colony.getColonyName(), w - 4);
        g.drawString(font, name, x, y, INK, false);
        drawSeparator(g, x, y + 10, w);
        y += 14;

        drawCard(g, x, y, w, 18, true);
        g.drawString(font, "TAX BALANCE", x + 3, y + 2, INK_FAINT, false);
        String balStr = colony.getTaxBalance() + " / " + colony.getMaxTaxRevenue() + " $";
        g.drawString(font, balStr, x + 3, y + 10, colony.hasDebt() ? DANGER : INK, false);
        y += 20;

        if (colony.hasDebt()) {
            drawCard(g, x, y, w, 14, false);
            g.drawString(font, "DEBT", x + 3, y + 2, INK_FAINT, false);
            String debtStr = String.valueOf(colony.getDebtAmount());
            g.drawString(font, debtStr, x + w - font.width(debtStr) - 3, y + 2, DANGER, false);
            y += 16;
        }

        drawCard(g, x, y, w, 18, false);
        g.drawString(font, "EST. TAX / INTERVAL", x + 3, y + 2, INK_FAINT, false);
        g.drawString(font, "~ " + colony.getApproximateRevenuePerInterval() + " $", x + 3, y + 10, INK, false);
        y += 20;

        drawCard(g, x, y, w, 28, false);
        g.drawString(font, "INFRASTRUCTURE", x + 3, y + 2, INK_FAINT, false);
        g.drawString(font, "Buildings", x + 5, y + 12, INK_FAINT, false);
        String bldgStr = String.valueOf(colony.getBuildingCount());
        g.drawString(font, bldgStr, x + w - font.width(bldgStr) - 5, y + 12, INK, false);
        g.drawString(font, "Military", x + 5, y + 21, INK_FAINT, false);
        String milStr = String.valueOf(colony.getGuardCount());
        g.drawString(font, milStr, x + w - font.width(milStr) - 5, y + 21, INK, false);
        y += 30;

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

        if (colony.isVassal()) {
            drawBadge(g, font, "Vassal " + colony.getVassalTributeRate() + "%", x, y, ORANGE);
            y += 12;
        } else if (colony.hasVassals()) {
            drawBadge(g, font, "Overlord (" + colony.getVassalCount() + ")", x, y, GREEN);
            y += 12;
        }

        int btnY = rightY + rightH - 12;
        int btnW = (w - 4) / 3;

        if (colony.canClaimTax() && colony.getTaxBalance() > 0 && !colony.isVassal()) {
            drawButton(g, font, "Claim", x, btnY, btnW, 11, mouseX, mouseY, GREEN);
        }

        if (colony.getDebtAmount() > 0) {
            drawButton(g, font, "Pay Debt", x + btnW + 2, btnY, btnW, 11, mouseX, mouseY, DANGER);
        }

        drawButton(g, font, "Events", x + (btnW + 2) * 2, btnY, btnW, 11, mouseX, mouseY, BLUE);
    }

    private static final int EVENT_ITEM_H = 14;

    private void renderEventsView(GuiGraphics g, ColonyTaxData colony, int mouseX, int mouseY) {
        int x = rightX, y = rightY, w = rightW;
        hoveredEventEntry = null; // reset each frame

        g.drawString(font, "Recent Events", x, y, INK, false);
        drawButton(g, font, "Back", x + w - 26, y - 1, 26, 11, mouseX, mouseY, INK_FAINT);
        y += 12;
        drawSeparator(g, x, y, w);
        y += 4; // events list starts here: rightY + 16

        Map<Integer, List<EventLogEntry>> allLogs = eventLogSupplier.get();
        List<EventLogEntry> events = allLogs.getOrDefault(colony.getColonyId(), Collections.emptyList());

        if (events.isEmpty()) {
            g.drawString(font, "No recent events.", x + 4, y + 10, INK_GHOST, false);
            return;
        }

        int available = rightH - 16;
        int maxVisible = available / EVENT_ITEM_H;
        int maxOffset = Math.max(0, events.size() - maxVisible);
        if (eventScrollOffset > maxOffset) eventScrollOffset = maxOffset;
        int visible = Math.min(maxVisible, events.size() - eventScrollOffset);

        for (int i = 0; i < visible; i++) {
            EventLogEntry entry = events.get(i + eventScrollOffset);
            int iy = y + i * EVENT_ITEM_H;

            boolean isHistory = isHistoryEntry(entry.getEventId());
            boolean isStateEvent = entry.getEventId().startsWith("active_");
            boolean hasDismiss = !isHistory && !isStateEvent;

            // Hover highlight + tooltip tracking
            boolean hovered = mouseX >= x && mouseX < x + w - 4
                    && mouseY >= iy && mouseY < iy + EVENT_ITEM_H;
            if (hovered && !entry.getDescription().isEmpty()) {
                g.fill(x, iy, x + w, iy + EVENT_ITEM_H - 1, 0x14000000);
                hoveredEventEntry = entry;
            }

            // Color bar on left
            int barColor = (isHistory || isStateEvent || entry.isActive()) ? entry.getColorCode() : INK_GHOST;
            g.fill(x, iy, x + 2, iy + EVENT_ITEM_H - 1, barColor);

            // Dismiss button (rightmost)
            if (hasDismiss) {
                drawButton(g, font, "x", x + w - 11, iy + 2, 10, 9, mouseX, mouseY, INK_FAINT);
            }

            // compactStat — right-aligned, just before dismiss button
            String stat = entry.getCompactStat();
            int dismissW = hasDismiss ? 12 : 0;
            int statW = 0;
            if (!stat.isEmpty()) {
                statW = font.width(stat) + 3;
                g.drawString(font, stat, x + w - dismissW - statW, iy + 3, barColor, false);
            }

            // Event name — truncated to fit remaining space
            int nameColor = (isHistory || isStateEvent || entry.isActive()) ? INK : INK_GHOST;
            int nameMaxW = w - 4 - dismissW - statW;
            String name = truncate(font, entry.getDisplayName(), nameMaxW);
            g.drawString(font, name, x + 4, iy + 3, nameColor, false);

            // Row separator
            g.fill(x, iy + EVENT_ITEM_H - 1, x + w, iy + EVENT_ITEM_H, CARD_BORDER);
        }

        drawScrollbar(g, x + w - 4, y, available, events.size(), maxVisible, eventScrollOffset);
    }

    @Override
    public void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (hoveredEventEntry != null && !hoveredEventEntry.getDescription().isEmpty()) {
            g.renderTooltip(font,
                    font.split(Component.literal(hoveredEventEntry.getDescription()), 160),
                    mouseX, mouseY);
        }
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        List<ColonyTaxData> colonies = coloniesSupplier.get();
        ColonyTaxData selected = selectedSupplier.get();

        if (isInLeftPage(mouseX, mouseY)) {
            int y = leftY + 14;
            int itemH = 22;
            int maxVisible = (leftH - 14) / itemH;
            int visible = Math.min(maxVisible, colonies.size() - scrollOffset);
            for (int i = 0; i < visible; i++) {
                int iy = y + i * itemH;
                if (mouseY >= iy && mouseY < iy + itemH) {
                    ColonyTaxData colony = colonies.get(i + scrollOffset);
                    // Only select a new colony; clicking the already-selected one does nothing
                    if (selected == null || selected.getColonyId() != colony.getColonyId()) {
                        selectCallback.accept(colony);
                        showingEvents = false;
                    }
                    return true;
                }
            }
        }

        if (isInRightPage(mouseX, mouseY) && selected != null) {
            if (showingEvents) {
                if (isInBounds(mouseX, mouseY, rightX + rightW - 26, rightY - 1, 26, 11)) {
                    showingEvents = false;
                    eventScrollOffset = 0;
                    return true;
                }
                Map<Integer, List<EventLogEntry>> allLogs = eventLogSupplier.get();
                List<EventLogEntry> events = allLogs.getOrDefault(selected.getColonyId(), Collections.emptyList());
                if (!events.isEmpty()) {
                    int ey = rightY + 16;
                    int maxVisible = (rightH - 16) / EVENT_ITEM_H;
                    int visible = Math.min(maxVisible, events.size() - eventScrollOffset);
                    for (int i = 0; i < visible; i++) {
                        EventLogEntry entry = events.get(i + eventScrollOffset);
                        int iy = ey + i * EVENT_ITEM_H;
                        if (!isHistoryEntry(entry.getEventId())
                                && !entry.getEventId().startsWith("active_")
                                && isInBounds(mouseX, mouseY, rightX + rightW - 11, iy + 2, 10, 9)) {
                            NetworkHandler.sendToServer(
                                    new DismissEventPacket(selected.getColonyId(), entry.getEventId()));
                            refreshCallback.run();
                            return true;
                        }
                    }
                }
            } else {
                int btnY = rightY + rightH - 12;
                int btnW = (rightW - 4) / 3;

                if (selected.canClaimTax() && selected.getTaxBalance() > 0 && !selected.isVassal()) {
                    if (isInBounds(mouseX, mouseY, rightX, btnY, btnW, 11)) {
                        NetworkHandler.sendToServer(new ClaimTaxPacket(selected.getColonyId(), -1));
                        refreshCallback.run();
                        return true;
                    }
                }

                if (selected.getDebtAmount() > 0) {
                    if (isInBounds(mouseX, mouseY, rightX + btnW + 2, btnY, btnW, 11)) {
                        NetworkHandler.sendToServer(new PayTaxDebtPacket(selected.getColonyId()));
                        refreshCallback.run();
                        return true;
                    }
                }

                if (isInBounds(mouseX, mouseY, rightX + (btnW + 2) * 2, btnY, btnW, 11)) {
                    showingEvents = true;
                    return true;
                }

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
                            NetworkHandler.sendToServer(new SetTaxPolicyPacket(
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
        if (isInRightPage(mouseX, mouseY) && showingEvents) {
            ColonyTaxData sel = selectedSupplier.get();
            if (sel != null) {
                Map<Integer, List<EventLogEntry>> allLogs = eventLogSupplier.get();
                List<EventLogEntry> events = allLogs.getOrDefault(sel.getColonyId(), Collections.emptyList());
                int maxVisible = (rightH - 16) / EVENT_ITEM_H;
                int maxOffset = Math.max(0, events.size() - maxVisible);
                eventScrollOffset = Math.max(0, Math.min(maxOffset, eventScrollOffset - (int) delta));
            }
            return true;
        }
        if (isInLeftPage(mouseX, mouseY)) {
            List<ColonyTaxData> colonies = coloniesSupplier.get();
            int maxVisible = (leftH - 14) / 22;
            int maxOffset = Math.max(0, colonies.size() - maxVisible);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) delta));
            return true;
        }
        return false;
    }


    private static boolean isHistoryEntry(String eventId) {
        return eventId.startsWith("raid_") || eventId.startsWith("war_");
    }

    private String getStatusText(ColonyTaxData colony) {
        if (colony.isAtWar()) return "At War";
        if (colony.isBesieged()) return "Besieged";
        if (colony.isOccupied()) return "Occupied";
        if (colony.isBeingRaided()) return "Raided";
        if (!colony.canClaimTax()) return "Restricted";
        if (colony.hasDebt()) return "In Debt";
        return "Healthy";
    }

    private int getStatusColor(ColonyTaxData colony) {
        if (colony.isAtWar() || colony.isBesieged() || colony.isBeingRaided()) return DANGER;
        if (colony.isOccupied()) return ORANGE;
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
