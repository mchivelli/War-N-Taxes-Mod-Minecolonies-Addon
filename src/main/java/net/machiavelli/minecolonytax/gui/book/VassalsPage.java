package net.machiavelli.minecolonytax.gui.book;

import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.packets.EndVassalizationPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static net.machiavelli.minecolonytax.gui.book.BookRenderHelper.*;

/**
 * Vassals tab: left page shows overlords + vassals, right page shows selected detail.
 */
public class VassalsPage extends BookPage {

    private final Supplier<List<VassalIncomeData>> vassalSupplier;
    private final Supplier<List<ColonyTaxData>> coloniesSupplier;
    private final Runnable refreshCallback;

    private int scrollOffset = 0;
    private int selectedVassalIndex = -1;
    private int selectedOverlordIndex = -1;

    public VassalsPage(Screen screen, Font font,
                       Supplier<List<VassalIncomeData>> vassalSupplier,
                       Supplier<List<ColonyTaxData>> coloniesSupplier,
                       Runnable refreshCallback) {
        super(screen, font);
        this.vassalSupplier = vassalSupplier;
        this.coloniesSupplier = coloniesSupplier;
        this.refreshCallback = refreshCallback;
    }

    @Override
    public void onActivated() {
        scrollOffset = 0;
        selectedVassalIndex = -1;
        selectedOverlordIndex = -1;
    }

    @Override
    public void renderLeftPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<VassalIncomeData> vassals = vassalSupplier.get();
        List<ColonyTaxData> overlords = coloniesSupplier.get().stream()
                .filter(ColonyTaxData::isVassal).collect(Collectors.toList());

        drawHeading(g, font, "Diplomacy", leftX, leftY, leftW);
        int y = leftY + 14;

        g.fill(leftX, y, leftX + leftW, y + 10, INK);
        g.drawCenteredString(font, "OVERLORDS", leftX + leftW / 2, y + 1, CARD_BORDER);
        y += 12;

        if (overlords.isEmpty()) {
            g.drawString(font, "Independent", leftX + 4, y, INK_GHOST, false);
            y += 12;
        } else {
            for (int i = 0; i < overlords.size() && i < 3; i++) {
                ColonyTaxData ol = overlords.get(i);
                boolean sel = selectedOverlordIndex == i;
                boolean hov = mouseX >= leftX && mouseX < leftX + leftW && mouseY >= y && mouseY < y + 18;
                if (sel) {
                    g.fill(leftX, y, leftX + leftW, y + 18, 0x182C1E0E);
                    g.fill(leftX, y, leftX + 2, y + 18, GOLD);
                } else if (hov) {
                    g.fill(leftX, y, leftX + leftW, y + 18, 0x082C1E0E);
                }
                g.fill(leftX, y + 17, leftX + leftW, y + 18, CARD_BORDER);
                g.drawString(font, truncate(font, ol.getColonyName(), leftW - 6), leftX + 4, y + 1, INK, false);
                g.drawString(font, "Tribute: " + ol.getVassalTributeRate() + "%", leftX + 4, y + 10, DANGER, false);
                y += 18;
            }
        }

        y += 2;
        g.fill(leftX, y, leftX + leftW, y + 10, GOLD_DARK);
        g.drawCenteredString(font, "VASSALS", leftX + leftW / 2, y + 1, CARD_BORDER);
        y += 12;

        if (vassals.isEmpty()) {
            g.drawString(font, "No vassals", leftX + 4, y, INK_GHOST, false);
        } else {
            int maxVisible = Math.max(1, (leftH - (y - leftY)) / 18);
            int visible = Math.min(maxVisible, vassals.size() - scrollOffset);
            for (int i = 0; i < visible; i++) {
                VassalIncomeData v = vassals.get(i + scrollOffset);
                int iy = y + i * 18;
                boolean sel = selectedVassalIndex == i + scrollOffset;
                boolean hov = mouseX >= leftX && mouseX < leftX + leftW && mouseY >= iy && mouseY < iy + 18;
                if (sel) {
                    g.fill(leftX, iy, leftX + leftW, iy + 18, 0x182C1E0E);
                    g.fill(leftX, iy, leftX + 2, iy + 18, GOLD);
                } else if (hov) {
                    g.fill(leftX, iy, leftX + leftW, iy + 18, 0x082C1E0E);
                }
                g.fill(leftX, iy + 17, leftX + leftW, iy + 18, CARD_BORDER);
                g.drawString(font, truncate(font, v.getVassalColonyName(), leftW - 6), leftX + 4, iy + 1, INK, false);
                // Income label color tracks the relationship kind so tax-occupied
                // and provisional-claim rows visually stand apart from true vassals.
                int incomeColor = colorForKind(v.getKind());
                g.drawString(font, "Income: " + v.getTributeRate() + "%", leftX + 4, iy + 10, incomeColor, false);
                // Small kind-tag in the row's right margin (compact: just 1 char + dot)
                String tag = shortTagForKind(v.getKind());
                if (tag != null) {
                    int tagX = leftX + leftW - font.width(tag) - 6;
                    g.drawString(font, tag, tagX, iy + 1, incomeColor, false);
                }
            }
            drawScrollbar(g, leftX + leftW - 4, y, leftH - (y - leftY), vassals.size(), maxVisible, scrollOffset);
        }
    }

    @Override
    public void renderRightPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<VassalIncomeData> vassals = vassalSupplier.get();
        List<ColonyTaxData> overlords = coloniesSupplier.get().stream()
                .filter(ColonyTaxData::isVassal).collect(Collectors.toList());

        if (selectedOverlordIndex >= 0 && selectedOverlordIndex < overlords.size()) {
            renderOverlordDetail(g, overlords.get(selectedOverlordIndex), mouseX, mouseY);
            return;
        }

        if (selectedVassalIndex >= 0 && selectedVassalIndex < vassals.size()) {
            renderVassalDetail(g, vassals.get(selectedVassalIndex), mouseX, mouseY);
            return;
        }

        g.drawString(font, "Select an entry", rightX + 10, rightY + rightH / 2 - 4, INK_GHOST, false);
    }

    /** Display label for the Vassals tab row badge. */
    private static String labelForKind(VassalIncomeData.VassalKind kind) {
        if (kind == null) return "VASSAL";
        switch (kind) {
            case TAX_OCCUPIED: return "TAX-OCCUPIED";
            case PROVISIONAL:  return "PROVISIONAL";
            case VASSAL:
            default:           return "VASSAL";
        }
    }

    /** Color for the Vassals tab row badge. Matches the design HTML's palette. */
    private static int colorForKind(VassalIncomeData.VassalKind kind) {
        if (kind == null) return GREEN;
        switch (kind) {
            case TAX_OCCUPIED: return DANGER;          // red — primary under besiege
            case PROVISIONAL:  return 0xFFE07B33;      // orange — secondary mid-conversion
            case VASSAL:
            default:           return GREEN;
        }
    }

    /** One-letter row tag for the compact list view. */
    private static String shortTagForKind(VassalIncomeData.VassalKind kind) {
        if (kind == null) return null;
        switch (kind) {
            case TAX_OCCUPIED: return "T";
            case PROVISIONAL:  return "P";
            case VASSAL:       return null; // keep the legacy rows clean
            default:           return null;
        }
    }

    private void renderOverlordDetail(GuiGraphics g, ColonyTaxData ol, int mouseX, int mouseY) {
        int x = rightX, y = rightY, w = rightW;

        g.drawString(font, truncate(font, ol.getColonyName(), w - 50), x, y, INK, false);
        drawBadge(g, font, "OVERLORD", x + w - font.width("OVERLORD") - 4, y, DANGER);
        y += 14;

        drawCard(g, x, y, w, 18, true);
        g.drawString(font, "TRIBUTE RATE", x + 3, y + 2, INK_FAINT, false);
        g.drawCenteredString(font, ol.getVassalTributeRate() + "%", x + w / 2, y + 10, INK);
        y += 20;

        int btnY = rightY + rightH - 12;
        drawButton(g, font, "Break Vassalage", x, btnY, w, 11, mouseX, mouseY, DANGER);
    }

    private void renderVassalDetail(GuiGraphics g, VassalIncomeData v, int mouseX, int mouseY) {
        int x = rightX, y = rightY, w = rightW;

        g.drawString(font, truncate(font, v.getVassalColonyName(), w - 50), x, y, INK, false);
        // Kind-aware badge: VASSAL (green) / TAX-OCCUPIED (red) / PROVISIONAL (orange).
        String badgeLabel = labelForKind(v.getKind());
        int badgeColor = colorForKind(v.getKind());
        drawBadge(g, font, badgeLabel, x + w - font.width(badgeLabel) - 4, y, badgeColor);
        y += 14;

        drawCard(g, x, y, w, 18, true);
        g.drawString(font, "TRIBUTE RATE", x + 3, y + 2, INK_FAINT, false);
        g.drawCenteredString(font, v.getTributeRate() + "%", x + w / 2, y + 10, INK);
        y += 20;

        int halfW = (w - 2) / 2;
        drawCard(g, x, y, halfW, 18, false);
        g.drawString(font, "LAST PAYMENT", x + 3, y + 2, INK_FAINT, false);
        g.drawString(font, v.getFormattedLastPayment(), x + 3, y + 10, INK, false);

        drawCard(g, x + halfW + 2, y, halfW, 18, false);
        g.drawString(font, "LAST TRIBUTE", x + halfW + 5, y + 2, INK_FAINT, false);
        g.drawString(font, v.getLastTribute() + " $", x + halfW + 5, y + 10, INK, false);
        y += 20;

        g.drawString(font, "Auto-collected at tax intervals", x, y, INK_GHOST, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        List<VassalIncomeData> vassals = vassalSupplier.get();
        List<ColonyTaxData> overlords = coloniesSupplier.get().stream()
                .filter(ColonyTaxData::isVassal).collect(Collectors.toList());

        if (isInLeftPage(mouseX, mouseY)) {
            int y = leftY + 14;
            y += 12; // section header
            if (!overlords.isEmpty()) {
                for (int i = 0; i < overlords.size() && i < 3; i++) {
                    if (mouseY >= y && mouseY < y + 18) {
                        selectedOverlordIndex = (selectedOverlordIndex == i) ? -1 : i;
                        selectedVassalIndex = -1;
                        return true;
                    }
                    y += 18;
                }
            } else {
                y += 12;
            }
            y += 14; // gap + section header
            if (!vassals.isEmpty()) {
                int maxVisible = Math.max(1, (leftH - (y - leftY)) / 18);
                int visible = Math.min(maxVisible, vassals.size() - scrollOffset);
                for (int i = 0; i < visible; i++) {
                    int iy = y + i * 18;
                    if (mouseY >= iy && mouseY < iy + 18) {
                        int idx = i + scrollOffset;
                        selectedVassalIndex = (selectedVassalIndex == idx) ? -1 : idx;
                        selectedOverlordIndex = -1;
                        return true;
                    }
                }
            }
        }

        if (isInRightPage(mouseX, mouseY) && selectedOverlordIndex >= 0
                && selectedOverlordIndex < overlords.size()) {
            int btnY = rightY + rightH - 12;
            if (isInBounds(mouseX, mouseY, rightX, btnY, rightW, 11)) {
                ColonyTaxData ol = overlords.get(selectedOverlordIndex);
                NetworkHandler.sendToServer(new EndVassalizationPacket(ol.getColonyId()));
                selectedOverlordIndex = -1;
                refreshCallback.run();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInLeftPage(mouseX, mouseY)) {
            List<VassalIncomeData> vassals = vassalSupplier.get();
            // Compute vassal list start Y to determine available space dynamically
            List<ColonyTaxData> overlords = coloniesSupplier.get().stream()
                    .filter(ColonyTaxData::isVassal).collect(Collectors.toList());
            int usedY = 14 + 12; // heading + overlords section header
            usedY += overlords.isEmpty() ? 12 : Math.min(overlords.size(), 3) * 18;
            usedY += 14; // gap + vassals section header
            int maxVisible = Math.max(1, (leftH - usedY) / 18);
            int maxOffset = Math.max(0, vassals.size() - maxVisible);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) delta));
            return true;
        }
        return false;
    }
}
