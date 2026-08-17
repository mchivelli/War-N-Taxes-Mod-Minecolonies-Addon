package net.machiavelli.minecolonytax.gui.book;

import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.OfficerData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.packets.RequestOfficerDataPayload;
import net.machiavelli.minecolonytax.network.packets.UpdatePlayerTaxPermissionPayload;
import net.machiavelli.minecolonytax.network.packets.UpdateTaxPermissionPayload;
import net.machiavelli.minecolonytax.permissions.ColonyPermission;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static net.machiavelli.minecolonytax.gui.book.BookRenderHelper.*;

/**
 * Officers tab. Left page lists the colony's officers; the right page is the permission
 * editor — colony-wide defaults when nothing is selected, and that officer's individual
 * overrides once one is picked.
 *
 * <p>Every permission value rendered here comes from the server via {@link OfficerData} /
 * the officer response packet. This page used to call TaxPermissionManager directly, but
 * that manager is server-only static state: on a dedicated server the client's copy is
 * always empty, so the tab reported "ON" for everyone regardless of the real setting, and
 * its toggles computed the value to send from that empty state.
 */
public class OfficersPage extends BookPage {

    private static final int ITEM_H = 18;
    private static final int ROW_H = 14;
    private static final int TOGGLE_W = 28, TOGGLE_H = 11;
    private static final ColonyPermission[] PERMISSIONS = ColonyPermission.values();

    private final Supplier<List<OfficerData>> officerSupplier;
    private final Supplier<ColonyTaxData> selectedColonySupplier;
    private final IntSupplier loadedColonyIdSupplier;
    private final Predicate<ColonyPermission> colonyDefaultSupplier;

    private int scrollOffset = 0;
    private int selectedOfficerIndex = -1;

    public OfficersPage(Screen screen, Font font,
                        Supplier<List<OfficerData>> officerSupplier,
                        Supplier<ColonyTaxData> selectedColonySupplier,
                        IntSupplier loadedColonyIdSupplier,
                        Predicate<ColonyPermission> colonyDefaultSupplier) {
        super(screen, font);
        this.officerSupplier = officerSupplier;
        this.selectedColonySupplier = selectedColonySupplier;
        this.loadedColonyIdSupplier = loadedColonyIdSupplier;
        this.colonyDefaultSupplier = colonyDefaultSupplier;
    }

    // --- Layout anchors: shared by render and hit-testing so the two cannot drift apart ---

    /** First list row: heading (14) + colony name (12). */
    private int listStartY() { return leftY + 26; }

    private int listVisibleRows() { return (leftH - (listStartY() - leftY)) / ITEM_H; }

    /** Permission row i on the right page. Same anchor in both right-page modes. */
    private int rowY(int index) { return rightY + 30 + index * ROW_H; }

    private int toggleX() { return rightX + rightW - TOGGLE_W - 2; }

    @Override
    public void onActivated() {
        scrollOffset = 0;
        selectedOfficerIndex = -1;
        requestOfficers();
    }

    private void requestOfficers() {
        ColonyTaxData colony = selectedColonySupplier.get();
        if (colony != null) {
            NetworkHandler.sendToServer(new RequestOfficerDataPayload(colony.getColonyId()));
        }
    }

    // ======================== Left page: officer list ========================

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

        String colName = truncate(font, colony.getColonyName(), leftW - 6);
        g.drawString(font, colName, leftX + 4, y, INK, false);
        y = listStartY();

        if (officers.isEmpty()) {
            // Distinguish "the reply hasn't arrived" from "this colony has no officers";
            // both used to render as a permanent "Loading...".
            boolean loaded = loadedColonyIdSupplier.getAsInt() == colony.getColonyId();
            g.drawString(font, loaded ? "No officers" : "Loading...", leftX + 4, y + 10, INK_GHOST, false);
            if (loaded) {
                g.drawString(font, "in this colony", leftX + 4, y + 22, INK_GHOST, false);
            }
            return;
        }

        int maxVisible = listVisibleRows();
        clampScroll(officers.size(), maxVisible);
        int visible = Math.min(maxVisible, officers.size() - scrollOffset);

        for (int i = 0; i < visible; i++) {
            OfficerData officer = officers.get(i + scrollOffset);
            int iy = y + i * ITEM_H;
            boolean sel = selectedOfficerIndex == i + scrollOffset;
            boolean hov = mouseX >= leftX && mouseX < leftX + leftW && mouseY >= iy && mouseY < iy + ITEM_H;

            if (sel) {
                g.fill(leftX, iy, leftX + leftW, iy + ITEM_H, 0x182C1E0E);
                g.fill(leftX, iy, leftX + 2, iy + ITEM_H, GOLD);
            } else if (hov) {
                g.fill(leftX, iy, leftX + leftW, iy + ITEM_H, 0x082C1E0E);
            }
            g.fill(leftX, iy + ITEM_H - 1, leftX + leftW, iy + ITEM_H, CARD_BORDER);

            String name = truncate(font, officer.getPlayerName(), leftW - 6);
            g.drawString(font, name, leftX + 4, iy + 1, officer.getRankColor(), false);
            g.drawString(font, officer.getRank(), leftX + 4, iy + 10, INK_FAINT, false);
        }

        drawScrollbar(g, leftX + leftW - 4, y, leftH - (y - leftY), officers.size(), maxVisible, scrollOffset);
    }

    // ======================== Right page: permission editor ========================

    @Override
    public void renderRightPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ColonyTaxData colony = selectedColonySupplier.get();
        List<OfficerData> officers = officerSupplier.get();

        if (colony == null) {
            g.drawString(font, "Select a colony first", rightX + 10, rightY + rightH / 2 - 4, INK_GHOST, false);
            return;
        }

        if (selectedOfficerIndex < 0 || selectedOfficerIndex >= officers.size()) {
            renderColonyDefaults(g, colony, mouseX, mouseY);
            return;
        }

        renderOfficerPermissions(g, colony, officers.get(selectedOfficerIndex), mouseX, mouseY);
    }

    /** Shown while no officer is selected: the colony-wide baseline for every action. */
    private void renderColonyDefaults(GuiGraphics g, ColonyTaxData colony, int mouseX, int mouseY) {
        boolean isOwnerViewing = colony.isOwner();
        int x = rightX, w = rightW;

        g.drawString(font, "Colony Defaults", x, rightY, INK, false);
        g.drawString(font, "Applies to all officers", x, rightY + 12, INK_GHOST, false);
        drawSeparator(g, x, rightY + 24, w);

        for (int i = 0; i < PERMISSIONS.length; i++) {
            ColonyPermission permission = PERMISSIONS[i];
            boolean allowed = colonyDefaultSupplier.test(permission);
            int y = rowY(i);

            g.drawString(font, truncate(font, permission.getDisplayName(), w - 40), x + 2, y + 2, INK, false);
            if (isOwnerViewing) {
                drawToggle(g, font, allowed, toggleX(), y, TOGGLE_W, TOGGLE_H, mouseX, mouseY);
            } else {
                String label = allowed ? "ON" : "OFF";
                g.drawString(font, label, x + w - font.width(label) - 4, y + 2,
                        allowed ? GREEN : DANGER, false);
            }
        }

        int footerY = rowY(PERMISSIONS.length) + 2;
        drawSeparator(g, x, footerY, w);
        g.drawString(font, isOwnerViewing ? "Pick an officer to" : "Owner-only settings",
                x + 2, footerY + 6, INK_GHOST, false);
        if (isOwnerViewing) {
            g.drawString(font, "override individually", x + 2, footerY + 16, INK_GHOST, false);
        }
    }

    /** Shown for the selected officer: their effective rights and individual overrides. */
    private void renderOfficerPermissions(GuiGraphics g, ColonyTaxData colony, OfficerData officer,
                                          int mouseX, int mouseY) {
        boolean isOwnerViewing = colony.isOwner();
        int x = rightX, w = rightW;

        g.drawString(font, truncate(font, officer.getPlayerName(), w - 50), x, rightY, INK, false);
        drawBadge(g, font, officer.getRank(), x + w - font.width(officer.getRank()) - 4, rightY,
                officer.getRankColor());
        drawSeparator(g, x, rightY + 14, w);

        if (officer.isOwner()) {
            g.drawString(font, "Colony Owner", x + 2, rightY + 20, GREEN, false);
            g.drawString(font, "Always has full", x + 2, rightY + 32, INK_GHOST, false);
            g.drawString(font, "permissions.", x + 2, rightY + 42, INK_GHOST, false);
            drawStatusLine(g, officer, x, rightY + 58);
            return;
        }

        if (!officer.isManager()) {
            // Colony actions are gated on manager rank server-side, so offering toggles here
            // would be controls that do nothing.
            g.drawString(font, "No colony rights", x + 2, rightY + 20, INK_FAINT, false);
            g.drawString(font, "Needs officer rank", x + 2, rightY + 32, INK_GHOST, false);
            g.drawString(font, "in MineColonies.", x + 2, rightY + 42, INK_GHOST, false);
            drawStatusLine(g, officer, x, rightY + 58);
            return;
        }

        g.drawString(font, "Permissions:", x, rightY + 18, INK_FAINT, false);

        for (int i = 0; i < PERMISSIONS.length; i++) {
            ColonyPermission permission = PERMISSIONS[i];
            boolean granted = officer.isGranted(permission);
            int y = rowY(i);

            // Granted but currently unusable (siege lock) — flag it rather than let the
            // toggle look broken.
            boolean blocked = granted && !officer.can(permission);
            int labelColor = blocked ? DANGER : INK;

            g.drawString(font, truncate(font, permission.getDisplayName(), w - 40), x + 2, y + 2,
                    labelColor, false);
            if (isOwnerViewing) {
                drawToggle(g, font, granted, toggleX(), y, TOGGLE_W, TOGGLE_H, mouseX, mouseY);
            } else {
                String label = granted ? "ON" : "OFF";
                g.drawString(font, label, x + w - font.width(label) - 4, y + 2,
                        granted ? GREEN : DANGER, false);
            }
        }

        int footerY = rowY(PERMISSIONS.length) + 2;
        drawSeparator(g, x, footerY, w);
        if (anyBlocked(officer)) {
            g.drawString(font, "Red = blocked now", x + 2, footerY + 6, DANGER, false);
            drawStatusLine(g, officer, x, footerY + 18);
        } else {
            drawStatusLine(g, officer, x, footerY + 6);
        }
    }

    private boolean anyBlocked(OfficerData officer) {
        for (ColonyPermission permission : PERMISSIONS) {
            if (officer.isGranted(permission) && !officer.can(permission)) return true;
        }
        return false;
    }

    private void drawStatusLine(GuiGraphics g, OfficerData officer, int x, int y) {
        g.drawString(font, officer.isOnline() ? "Online" : "Last: " + officer.getLastSeenText(),
                x + 2, y, officer.isOnline() ? GREEN : INK_GHOST, false);
    }

    private void clampScroll(int total, int maxVisible) {
        int maxOffset = Math.max(0, total - maxVisible);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));
    }

    // ======================== Input ========================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        ColonyTaxData colony = selectedColonySupplier.get();
        if (colony == null) return false;
        List<OfficerData> officers = officerSupplier.get();

        if (isInLeftPage(mouseX, mouseY)) {
            int y = listStartY();
            int maxVisible = listVisibleRows();
            int visible = Math.min(maxVisible, officers.size() - scrollOffset);
            for (int i = 0; i < visible; i++) {
                int iy = y + i * ITEM_H;
                if (mouseY >= iy && mouseY < iy + ITEM_H) {
                    int idx = i + scrollOffset;
                    selectedOfficerIndex = (selectedOfficerIndex == idx) ? -1 : idx;
                    return true;
                }
            }
            return false;
        }

        // Only the owner may change anything; everyone else gets a read-only view.
        if (!isInRightPage(mouseX, mouseY) || !colony.isOwner()) return false;

        int hitRow = hitPermissionRow(mouseX, mouseY);
        if (hitRow < 0) return false;
        ColonyPermission permission = PERMISSIONS[hitRow];

        boolean editingOfficer = selectedOfficerIndex >= 0 && selectedOfficerIndex < officers.size();
        if (!editingOfficer) {
            // Colony-wide default. Derive the new state from the SERVER's value, not from a
            // client-side TaxPermissionManager mutation — the client's map is empty on a
            // dedicated server, so the old code always sent "false" on the first click.
            boolean newPerm = !colonyDefaultSupplier.test(permission);
            NetworkHandler.sendToServer(
                    new UpdateTaxPermissionPayload(colony.getColonyId(), permission, newPerm));
            requestOfficers();
            return true;
        }

        OfficerData officer = officers.get(selectedOfficerIndex);
        if (officer.isOwner() || !officer.isManager()) return false;

        boolean newPerm = !officer.isGranted(permission);
        NetworkHandler.sendToServer(new UpdatePlayerTaxPermissionPayload(
                colony.getColonyId(), officer.getPlayerId(), permission, newPerm));
        requestOfficers();
        return true;
    }

    /** Index of the permission row under the cursor, or -1. */
    private int hitPermissionRow(double mouseX, double mouseY) {
        for (int i = 0; i < PERMISSIONS.length; i++) {
            if (isInBounds(mouseX, mouseY, toggleX(), rowY(i), TOGGLE_W, TOGGLE_H)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInLeftPage(mouseX, mouseY)) {
            List<OfficerData> officers = officerSupplier.get();
            int maxVisible = listVisibleRows();
            int maxOffset = Math.max(0, officers.size() - maxVisible);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) delta));
            return true;
        }
        return false;
    }
}
