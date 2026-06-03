package net.machiavelli.minecolonytax.gui.book;

import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.OfficerData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.packets.RequestOfficerDataPayload;
import net.machiavelli.minecolonytax.network.packets.UpdatePlayerTaxPermissionPayload;
import net.machiavelli.minecolonytax.network.packets.UpdateTaxPermissionPayload;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.Supplier;

import static net.machiavelli.minecolonytax.gui.book.BookRenderHelper.*;

/**
 * Officers tab: left page shows officer list, right page shows permission toggles.
 */
public class OfficersPage extends BookPage {

    private final Supplier<List<OfficerData>> officerSupplier;
    private final Supplier<ColonyTaxData> selectedColonySupplier;

    private int scrollOffset = 0;
    private int selectedOfficerIndex = -1;

    public OfficersPage(Screen screen, Font font,
                        Supplier<List<OfficerData>> officerSupplier,
                        Supplier<ColonyTaxData> selectedColonySupplier) {
        super(screen, font);
        this.officerSupplier = officerSupplier;
        this.selectedColonySupplier = selectedColonySupplier;
    }

    @Override
    public void onActivated() {
        scrollOffset = 0;
        selectedOfficerIndex = -1;
        ColonyTaxData colony = selectedColonySupplier.get();
        if (colony != null) {
            NetworkHandler.sendToServer(new RequestOfficerDataPayload(colony.getColonyId()));
        }
    }

    @Override
    public void renderLeftPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ColonyTaxData colony = selectedColonySupplier.get();
        List<OfficerData> officers = officerSupplier.get();

        drawHeading(g, font, "Officers", leftX, leftY, leftW);
        int y = leftY + 14;

        if (colony == null) {
            g.drawString(font, "Select a colony", leftX + 4, y + 10, INK_GHOST, false);
            g.drawString(font, "from Colonies tab", leftX + 4, y + 22, INK_GHOST, false);
            return;
        }

        // Colony name header
        String colName = truncate(font, colony.getColonyName(), leftW - 6);
        g.drawString(font, colName, leftX + 4, y, INK, false);
        y += 12;

        if (officers.isEmpty()) {
            g.drawString(font, "Loading...", leftX + 4, y + 10, INK_GHOST, false);
            return;
        }

        int itemH = 18;
        int maxVisible = (leftH - (y - leftY)) / itemH;
        int visible = Math.min(maxVisible, officers.size() - scrollOffset);

        for (int i = 0; i < visible; i++) {
            OfficerData officer = officers.get(i + scrollOffset);
            int iy = y + i * itemH;
            boolean sel = selectedOfficerIndex == i + scrollOffset;
            boolean hov = mouseX >= leftX && mouseX < leftX + leftW && mouseY >= iy && mouseY < iy + itemH;

            if (sel) {
                g.fill(leftX, iy, leftX + leftW, iy + itemH, 0x182C1E0E);
                g.fill(leftX, iy, leftX + 2, iy + itemH, GOLD);
            } else if (hov) {
                g.fill(leftX, iy, leftX + leftW, iy + itemH, 0x082C1E0E);
            }
            g.fill(leftX, iy + itemH - 1, leftX + leftW, iy + itemH, CARD_BORDER);

            String name = truncate(font, officer.getPlayerName(), leftW - 6);
            g.drawString(font, name, leftX + 4, iy + 1, officer.getRankColor(), false);
            g.drawString(font, officer.getRank(), leftX + 4, iy + 10, INK_FAINT, false);
        }

        drawScrollbar(g, leftX + leftW - 4, y, leftH - (y - leftY), officers.size(), maxVisible, scrollOffset);
    }

    @Override
    public void renderRightPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ColonyTaxData colony = selectedColonySupplier.get();
        List<OfficerData> officers = officerSupplier.get();

        if (colony == null || selectedOfficerIndex < 0 || selectedOfficerIndex >= officers.size()) {
            String msg = colony == null ? "Select a colony first" : "Select an officer";
            g.drawString(font, msg, rightX + 10, rightY + rightH / 2 - 4, INK_GHOST, false);
            return;
        }

        OfficerData officer = officers.get(selectedOfficerIndex);
        boolean isOwner = colony.isOwner();
        int x = rightX, y = rightY, w = rightW;

        // Header
        g.drawString(font, truncate(font, officer.getPlayerName(), w - 50), x, y, INK, false);
        drawBadge(g, font, officer.getRank(), x + w - font.width(officer.getRank()) - 4, y, officer.getRankColor());
        y += 14;

        drawSeparator(g, x, y, w);
        y += 4;

        // Default permission toggle
        boolean officersCanClaim = TaxPermissionManager.canOfficersClaim(colony.getColonyId());
        g.drawString(font, "Default Policy:", x, y, INK_FAINT, false);
        y += 10;
        g.drawString(font, officersCanClaim ? "ALLOW" : "BLOCK", x + 4, y, officersCanClaim ? GREEN : DANGER, false);
        if (isOwner) {
            drawToggle(g, font, officersCanClaim, x + w - 30, y - 2, 28, 11, mouseX, mouseY);
        }
        y += 14;

        drawSeparator(g, x, y, w);
        y += 4;

        // Individual permissions
        if (officer.getRank().equals("Owner")) {
            g.drawString(font, "Colony Owner", x + 4, y, GREEN, false);
            y += 10;
            g.drawString(font, "Always has full", x + 4, y, INK_GHOST, false);
            y += 10;
            g.drawString(font, "permissions.", x + 4, y, INK_GHOST, false);
        } else {
            g.drawString(font, "Permissions:", x, y, INK_FAINT, false);
            y += 12;

            boolean canClaim = TaxPermissionManager.canPlayerClaimTax(
                    colony.getColonyId(), officer.getPlayerId(), false, true);

            // Claim Taxes
            g.drawString(font, "Claim Taxes", x + 4, y + 1, INK, false);
            if (isOwner) {
                drawToggle(g, font, canClaim, x + w - 30, y - 1, 28, 11, mouseX, mouseY);
            } else {
                g.drawString(font, canClaim ? "ON" : "OFF", x + w - font.width(canClaim ? "ON" : "OFF") - 4,
                        y + 1, canClaim ? GREEN : DANGER, false);
            }
            y += 14;

            // Status line
            g.drawString(font, officer.isOnline() ? "Online" : "Last: " + officer.getLastSeenText(),
                    x + 4, y, officer.isOnline() ? GREEN : INK_GHOST, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        ColonyTaxData colony = selectedColonySupplier.get();
        List<OfficerData> officers = officerSupplier.get();

        // Left page: officer selection
        if (isInLeftPage(mouseX, mouseY) && colony != null) {
            int y = leftY + 14 + 12; // after heading + colony name
            int itemH = 18;
            int maxVisible = (leftH - (y - leftY)) / itemH;
            int visible = Math.min(maxVisible, officers.size() - scrollOffset);
            for (int i = 0; i < visible; i++) {
                int iy = y + i * itemH;
                if (mouseY >= iy && mouseY < iy + itemH) {
                    int idx = i + scrollOffset;
                    selectedOfficerIndex = (selectedOfficerIndex == idx) ? -1 : idx;
                    return true;
                }
            }
        }

        // Right page permission toggles
        if (isInRightPage(mouseX, mouseY) && colony != null && colony.isOwner()
                && selectedOfficerIndex >= 0 && selectedOfficerIndex < officers.size()) {
            OfficerData officer = officers.get(selectedOfficerIndex);
            int x = rightX, w = rightW;

            // Default toggle: header(14) + sep(4) + label(10) + offset(-2) = rightY + 26
            int defaultToggleY = rightY + 26;
            if (isInBounds(mouseX, mouseY, x + w - 30, defaultToggleY, 28, 11)) {
                boolean newPerm = TaxPermissionManager.toggleOfficerClaimPermission(colony.getColonyId());
                NetworkHandler.sendToServer(new UpdateTaxPermissionPayload(colony.getColonyId(), newPerm));
                return true;
            }

            // Individual claim toggle
            if (!officer.getRank().equals("Owner")) {
                int claimToggleY = rightY + 14 + 4 + 10 + 14 + 4 + 12 - 1; // match render
                if (isInBounds(mouseX, mouseY, x + w - 30, claimToggleY, 28, 11)) {
                    boolean newPerm = TaxPermissionManager.togglePlayerClaimPermission(
                            colony.getColonyId(), officer.getPlayerId(), true);
                    NetworkHandler.sendToServer(new UpdatePlayerTaxPermissionPayload(
                            colony.getColonyId(), officer.getPlayerId(), newPerm));
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInLeftPage(mouseX, mouseY)) {
            List<OfficerData> officers = officerSupplier.get();
            int maxVisible = (leftH - 26) / 18;
            int maxOffset = Math.max(0, officers.size() - maxVisible);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) delta));
            return true;
        }
        return false;
    }
}
