package net.machiavelli.minecolonytax.gui.book;

import net.machiavelli.minecolonytax.espionage.SpyIntelData;
import net.machiavelli.minecolonytax.gui.data.ColonySummary;
import net.machiavelli.minecolonytax.gui.data.SpyMissionData;
import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.network.packets.DeploySpyPacket;
import net.machiavelli.minecolonytax.network.packets.DismissSpyMissionPacket;
import net.machiavelli.minecolonytax.network.packets.RecallSpyPacket;
import net.machiavelli.minecolonytax.network.packets.RequestSpyDataPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static net.machiavelli.minecolonytax.gui.book.BookRenderHelper.*;

/**
 * Espionage tab: left page shows spy missions list, right page shows mission control + intel.
 */
public class EspionagePage extends BookPage {

    // Protocol names (sent to server)
    private static final String[] MISSION_TYPES = {"SCOUT", "SABOTAGE", "BRIBE", "STEAL"};
    // Display names (visible in UI buttons)
    private static final String[] MISSION_DISPLAY = {"Scout", "Sabotage", "Bribe", "Steal"};
    private static final int[] MISSION_COLORS = {BLUE, DANGER, GOLD_DARK, GREEN};
    private static final String[][] MISSION_DESCS = {
            {"Covert survey of a rival",
             "colony. Returns with",
             "intel: pop., guards &",
             "wealth. Lowest risk op."},
            {"Sabotages the target's",
             "tax economy. Cuts their",
             "next cycle by 25%. Must",
             "stay 30 min at target."},
            {"Bribes the target guards",
             "to stand down. Affected",
             "guards fight weakened in",
             "the next raid. High risk."},
            {"Steals economic secrets.",
             "Boosts your tax income",
             "by +20% for 24 hours.",
             "Higher detection risk."}
    };

    private final Supplier<List<SpyMissionData>> missionSupplier;
    private final Supplier<List<ColonySummary>> coloniesSupplier;
    private final Consumer<EditBox> addWidgetCallback;

    private int scrollOffset = 0;
    private int selectedMissionIndex = -1;
    private int targetColonyIndex = 0;
    private int missionTypeIndex = 0;

    private EditBox targetInput;

    public EspionagePage(Screen screen, Font font,
                         Supplier<List<SpyMissionData>> missionSupplier,
                         Supplier<List<ColonySummary>> coloniesSupplier,
                         Consumer<EditBox> addWidgetCallback) {
        super(screen, font);
        this.missionSupplier = missionSupplier;
        this.coloniesSupplier = coloniesSupplier;
        this.addWidgetCallback = addWidgetCallback;
    }

    @Override
    public void init() {
        targetInput = new EditBox(font, 0, 0, 80, 14, Component.literal("Target"));
        targetInput.setMaxLength(32);
        targetInput.setHint(Component.literal("Colony name..."));
        targetInput.setVisible(false);
        targetInput.setResponder(this::onTargetTextChanged);
        addWidgetCallback.accept(targetInput);
    }

    private void onTargetTextChanged(String text) {
        List<ColonySummary> colonies = coloniesSupplier.get();
        if (text.isEmpty() || colonies.isEmpty()) {
            targetColonyIndex = colonies.isEmpty() ? -1 : 0;
            return;
        }
        String lower = text.toLowerCase();
        // Try startsWith match first
        for (int i = 0; i < colonies.size(); i++) {
            if (colonies.get(i).getName().toLowerCase().startsWith(lower)) {
                targetColonyIndex = i;
                return;
            }
        }
        // Fall back to contains match
        for (int i = 0; i < colonies.size(); i++) {
            if (colonies.get(i).getName().toLowerCase().contains(lower)) {
                targetColonyIndex = i;
                return;
            }
        }
        targetColonyIndex = -1;
    }

    @Override
    public void setWidgetsVisible(boolean visible) {
        if (targetInput != null) {
            targetInput.setVisible(visible && selectedMissionIndex < 0);
        }
    }

    private void repositionWidget() {
        if (targetInput == null) return;
        int x = rightX + 2;
        int y = rightY + 36; // heading(14) + "Deploy New Spy"(12) + "Target:"(10)
        targetInput.setX(x);
        targetInput.setY(y);
        targetInput.setWidth(rightW - 4);
    }

    @Override
    public void onActivated() {
        scrollOffset = 0;
        selectedMissionIndex = -1;
        repositionWidget();
        if (targetInput != null) targetInput.setVisible(true);
        NetworkHandler.sendToServer(new RequestSpyDataPacket());
    }

    @Override
    public void onDeactivated() {
        setWidgetsVisible(false);
    }

    @Override
    public void renderLeftPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<SpyMissionData> missions = missionSupplier.get();

        drawHeading(g, font, "Active Spies", leftX, leftY, leftW);
        int y = leftY + 14;

        if (missions.isEmpty()) {
            g.drawString(font, "No missions.", leftX + 4, y + 10, INK_GHOST, false);
            return;
        }

        int itemH = 22;
        int maxVisible = (leftH - 14) / itemH;
        int visible = Math.min(maxVisible, missions.size() - scrollOffset);

        for (int i = 0; i < visible; i++) {
            SpyMissionData m = missions.get(i + scrollOffset);
            int iy = y + i * itemH;
            boolean sel = selectedMissionIndex == i + scrollOffset;
            boolean hov = mouseX >= leftX && mouseX < leftX + leftW && mouseY >= iy && mouseY < iy + itemH;

            if (sel) {
                g.fill(leftX, iy, leftX + leftW, iy + itemH, 0x182C1E0E);
                g.fill(leftX, iy, leftX + 2, iy + itemH, GOLD);
            } else if (hov) {
                g.fill(leftX, iy, leftX + leftW, iy + itemH, 0x082C1E0E);
            }
            g.fill(leftX, iy + itemH - 1, leftX + leftW, iy + itemH, CARD_BORDER);

            String name = truncate(font, m.getMissionType() + " \u2192 " + m.getTargetColonyName(), leftW - 6);
            g.drawString(font, name, leftX + 4, iy + 1, INK, false);

            String statusText = getStatusDisplayText(m);
            int statusColor = getStatusDisplayColor(m);
            g.drawString(font, statusText, leftX + 4, iy + 11, statusColor, false);

            if ("ACTIVE".equals(m.getStatus())) {
                SpyIntelData mIntel = m.getIntel();
                int mTier = mIntel != null ? mIntel.getIntelTier() : 0;
                int segY = iy + 12;
                int segX = leftX + leftW - 20;
                for (int s = 0; s < 3; s++) {
                    int sx = segX + s * 6;
                    g.fill(sx, segY, sx + 4, segY + 4, s < mTier ? GOLD : CARD_BORDER);
                }
            }
        }

        drawScrollbar(g, leftX + leftW - 4, y, leftH - 14, missions.size(), maxVisible, scrollOffset);
    }

    @Override
    public void renderRightPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<SpyMissionData> missions = missionSupplier.get();

        drawHeading(g, font, "Mission Control", rightX, rightY, rightW);
        int y = rightY + 14;
        int x = rightX;
        int w = rightW;

        boolean showDeployView = selectedMissionIndex < 0 || selectedMissionIndex >= missions.size();
        if (targetInput != null) targetInput.setVisible(showDeployView);

        if (!showDeployView) {
            SpyMissionData mission = missions.get(selectedMissionIndex);

            y = renderMissionDetail(g, mission, x, y, w, mouseX, mouseY);

            // Pinned-to-bottom action buttons: Recall/Delete on top row, Back below
            String status = mission.getStatus();
            boolean canRecall = "ACTIVE".equals(status) || "FLEEING".equals(status) || "DEPLOYING".equals(status);
            boolean canDismiss = isFinished(status);
            int backBtnY = rightY + rightH - 11;
            int actionBtnY = backBtnY - 13;

            if (canRecall) {
                drawButton(g, font, "Recall", x, actionBtnY, w / 2, 11, mouseX, mouseY, BLUE);
            }
            if (canDismiss) {
                drawButton(g, font, "Delete", x + w / 2 + 2, actionBtnY, w / 2 - 2, 11, mouseX, mouseY, DANGER);
            }
            drawButton(g, font, "\u2190 Back", x, backBtnY, w, 11, mouseX, mouseY, INK_FAINT);
            return;
        }

        g.drawString(font, "Deploy New Spy", x, y, INK, false);
        y += 12;

        g.drawString(font, "Target:", x, y, INK_FAINT, false);
        y += 10;
        repositionWidget();
        // EditBox rendered by Minecraft widget system (14px tall)
        y += 16;

        List<ColonySummary> colonies = coloniesSupplier.get();
        if (targetColonyIndex >= 0 && targetColonyIndex < colonies.size()) {
            String match = truncate(font, "\u2192 " + colonies.get(targetColonyIndex).getName(), w - 4);
            g.drawString(font, match, x + 2, y, GREEN, false);
        } else if (!colonies.isEmpty()) {
            g.drawString(font, "No match", x + 2, y, DANGER, false);
        } else {
            g.drawString(font, "No colonies", x + 2, y, INK_GHOST, false);
        }
        y += 12;

        g.drawString(font, "Type:", x, y, INK_FAINT, false);
        y += 10;
        int btnW = (w - 3) / 2;
        for (int i = 0; i < MISSION_TYPES.length; i++) {
            int bx = x + (i % 2) * (btnW + 2);
            int by = y + (i / 2) * 13;
            boolean active = i == missionTypeIndex;
            boolean hov = mouseX >= bx && mouseX < bx + btnW && mouseY >= by && mouseY < by + 11;
            int typeColor = MISSION_COLORS[i];
            int bg = active ? INK : (hov ? 0x302C1E0E : PARCHMENT_BG);
            int fg = active ? WHITE : typeColor;
            g.fill(bx, by, bx + btnW, by + 11, active ? typeColor : INK_GHOST);
            g.fill(bx + 1, by + 1, bx + btnW - 1, by + 10, bg);
            g.drawCenteredString(font, MISSION_DISPLAY[i], bx + btnW / 2, by + 2, fg);
        }
        y += 28;

        drawButton(g, font, "Deploy Spy", x, y, w, 12, mouseX, mouseY, GREEN);
        y += 16;

        drawSeparator(g, x, y, w);
        y += 4;
        g.drawString(font, MISSION_DISPLAY[missionTypeIndex], x, y, MISSION_COLORS[missionTypeIndex], false);
        y += 10;
        String[] desc = MISSION_DESCS[missionTypeIndex];
        for (String line : desc) {
            g.drawString(font, line, x + 2, y, INK_FAINT, false);
            y += 9;
        }
    }

    private int renderMissionDetail(GuiGraphics g, SpyMissionData mission, int x, int y, int w,
                                     int mouseX, int mouseY) {
        String status = mission.getStatus();
        SpyIntelData intel = mission.getIntel();

        g.drawString(font, truncate(font, mission.getTargetColonyName(), w - 4), x, y, INK, false);
        y += 10;
        drawBadge(g, font, status, x, y, getStatusDisplayColor(mission));
        y += 12;

        if ("DEPLOYING".equals(status)) {
            long travelMs = mission.getTravelDurationMs();
            long elapsed = mission.getElapsedTimeMs();
            int pct = travelMs > 0 ? (int) Math.min(99, elapsed * 100 / travelMs) : 0;
            g.drawString(font, "Traveling... " + pct + "%", x + 2, y, GOLD_DARK, false);
            y += 10;
            long remaining = Math.max(0, travelMs - elapsed);
            g.drawString(font, "ETA: " + formatElapsed(remaining), x + 2, y, INK_GHOST, false);
            return y + 10;
        }

        if ("RETURNING".equals(status)) {
            long travelMs = mission.getTravelDurationMs();
            long returnedMs = mission.getElapsedReturnMs();
            int pct = travelMs > 0 ? (int) Math.min(99, returnedMs * 100 / travelMs) : 0;
            g.drawString(font, "Returning... " + pct + "%", x + 2, y, BLUE, false);
            return y + 10;
        }

        if ("ACTIVE".equals(status)) {
            long fieldMs = Math.max(0, mission.getElapsedTimeMs() - mission.getTravelDurationMs());
            g.drawString(font, "Afield: " + formatElapsed(fieldMs), x + 2, y, INK_FAINT, false);
            y += 10;

            int aTier = intel != null ? intel.getIntelTier() : 0;
            String[] tierLabels = {"No intel yet", "Basic intel", "Tactical intel", "Full dossier"};
            g.drawString(font, tierLabels[Math.min(aTier, 3)], x + 2, y, GOLD_DARK, false);
            y += 10;

            // Each visual segment represents one intel tier.
            int barW = w - 4;
            int segSpacing = 2;
            int nSegs = 3;
            int segW = (barW - (nSegs - 1) * segSpacing) / nSegs;
            for (int s = 0; s < nSegs; s++) {
                int sx = x + 2 + s * (segW + segSpacing);
                g.fill(sx, y, sx + segW, y + 5, CARD_BORDER);
                if (aTier > s) {
                    g.fill(sx + 1, y + 1, sx + segW - 1, y + 4, GOLD);
                } else if (aTier == s && aTier < 3) {
                    g.fill(sx + 1, y + 1, sx + segW - 1, y + 4, GOLD_DARK);
                }
            }
            y += 8;

            if ("SABOTAGE".equals(mission.getMissionType())) {
                g.drawString(font, "Saboteur at work.", x + 2, y, INK_GHOST, false);
            } else {
                g.drawString(font, "Details on return.", x + 2, y, INK_GHOST, false);
            }
            return y + 10;
        }

        if ("FLEEING".equals(status)) {
            g.drawString(font, "Spy detected! Fleeing...", x + 2, y, ORANGE, false);
            y += 10;
            g.drawString(font, "Outcome pending.", x + 2, y, INK_GHOST, false);
            return y + 10;
        }

        if ("KILLED".equals(status)) {
            g.drawString(font, "Mission Failed", x + 2, y, DANGER, false);
            g.drawString(font, "Spy eliminated — no intel.", x + 2, y + 10, INK_GHOST, false);
            return y + 20;
        }

        if (intel == null) {
            g.drawString(font, "Mission complete.", x + 2, y, INK_FAINT, false);
            g.drawString(font, "Effect applied.", x + 2, y + 10, INK_GHOST, false);
            return y + 20;
        }

        if (intel.getIntelTier() == 0) {
            g.drawString(font, "No intel gathered.", x + 2, y, INK_GHOST, false);
            return y + 10;
        }

        long fieldMs = mission.getElapsedTimeMs() - mission.getTravelDurationMs();
        if (fieldMs > 0) {
            g.drawString(font, "Afield: " + formatElapsed(Math.max(0, fieldMs)), x + 2, y, INK_FAINT, false);
            y += 10;
        }

        if (intel.isEarlyAvailable()) {
            drawSeparator(g, x, y, w); y += 4;
            if (intel.getCitizenCount() >= 0 && intel.getBuildingCount() >= 0) {
                g.drawString(font, intel.getCitizenCount() + " souls, " + intel.getBuildingCount() + " buildings",
                        x + 2, y, INK, false);
                y += 9;
            } else if (intel.getCitizenCount() >= 0) {
                g.drawString(font, intel.getCitizenCount() + " citizens", x + 2, y, INK, false);
                y += 9;
            }
            if (intel.isAtWar()) {
                g.drawString(font, "At war", x + 2, y, DANGER, false);
            } else {
                g.drawString(font, "At peace", x + 2, y, GREEN, false);
            }
            y += 9;
        }

        if (intel.isMidAvailable()) {
            drawSeparator(g, x, y, w); y += 4;

            if (intel.getGuardCount() >= 0) {
                int guardColor = INK;
                String guardLabel = "Guards: " + intel.getGuardCount();
                if (intel.getCitizenCount() > 0) {
                    double ratio = (double) intel.getGuardCount() / intel.getCitizenCount() * 100;
                    if (ratio >= 30)      { guardLabel += " (heavy)";    guardColor = DANGER; }
                    else if (ratio >= 15) { guardLabel += " (moderate)"; guardColor = GOLD_DARK; }
                    else                  { guardLabel += " (light)";    guardColor = GREEN; }
                }
                g.drawString(font, guardLabel, x + 2, y, guardColor, false);
                y += 9;
            }

            if (intel.getHappiness() >= 0) {
                double h = intel.getHappiness();
                int moraleColor = h >= 6 ? GREEN : h >= 3 ? GOLD_DARK : DANGER;
                String mood = h >= 8 ? "Thriving" : h >= 5 ? "Content" : h >= 3 ? "Unhappy" : "Revolting";
                g.drawString(font, "Morale: " + String.format("%.1f", h) + "/10 " + mood,
                        x + 2, y, moraleColor, false);
                y += 9;
            }

            if (intel.getTaxBalance() >= 0) {
                int t = intel.getTaxBalance();
                int coinColor = t >= 5000 ? GOLD : t >= 1000 ? INK : DANGER;
                String coinLabel = t >= 10000 ? "Overflowing" : t >= 5000 ? "Prosperous"
                        : t >= 1000 ? "Adequate" : t > 0 ? "Strained" : "Bare";
                g.drawString(font, "Treasury: " + t + " (" + coinLabel + ")", x + 2, y, coinColor, false);
                y += 9;
            }
        }

        if (intel.isLateAvailable()) {
            drawSeparator(g, x, y, w); y += 4;
            if (intel.getOwnerName() != null) {
                g.drawString(font, "Lord: " + intel.getOwnerName(), x + 2, y, INK, false);
                y += 9;
            }
            if (intel.getOwnerSdmBalance() >= 0) {
                g.drawString(font, "Coin: " + intel.getOwnerSdmBalance(), x + 2, y, GOLD_DARK, false);
                y += 9;
            }
            if (intel.getColonyMembers() != null) {
                long online = intel.getColonyMembers().stream()
                        .filter(m -> m.isOnline).count();
                int total = intel.getColonyMembers().size();
                String roster = total + " members";
                if (online > 0) roster += ", " + online + " online";
                g.drawString(font, roster, x + 2, y, INK_FAINT, false);
                y += 9;
            }
        }

        return y;
    }

    private int drawIntelField(GuiGraphics g, int x, int y, String label, String value,
                                boolean unlocked, boolean gathering) {
        int color;
        if (unlocked) color = INK;
        else if (gathering) color = GOLD_DARK;
        else color = INK_GHOST;
        g.drawString(font, label + value, x + 2, y, color, false);
        return y + 9;
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        List<SpyMissionData> missions = missionSupplier.get();
        List<ColonySummary> colonies = coloniesSupplier.get();

        if (isInLeftPage(mouseX, mouseY)) {
            int y = leftY + 14;
            int itemH = 22;
            int maxVisible = (leftH - 14) / itemH;
            int visible = Math.min(maxVisible, missions.size() - scrollOffset);
            for (int i = 0; i < visible; i++) {
                int iy = y + i * itemH;
                if (mouseY >= iy && mouseY < iy + itemH) {
                    int idx = i + scrollOffset;
                    selectedMissionIndex = (selectedMissionIndex == idx) ? -1 : idx;
                    if (targetInput != null) targetInput.setVisible(selectedMissionIndex < 0);
                    return true;
                }
            }
        }

        if (isInRightPage(mouseX, mouseY)) {
            if (selectedMissionIndex >= 0 && selectedMissionIndex < missions.size()) {
                SpyMissionData mission = missions.get(selectedMissionIndex);
                String status = mission.getStatus();

                // Button layout matches renderRightPage: Back at bottom, Recall/Delete above it
                int backBtnY = rightY + rightH - 11;
                int actionBtnY = backBtnY - 13;

                if (isInBounds(mouseX, mouseY, rightX, backBtnY, rightW, 11)) {
                    selectedMissionIndex = -1;
                    if (targetInput != null) targetInput.setVisible(true);
                    return true;
                }

                boolean canRecall = "ACTIVE".equals(status) || "FLEEING".equals(status) || "DEPLOYING".equals(status);
                if (canRecall && isInBounds(mouseX, mouseY, rightX, actionBtnY, rightW / 2, 11)) {
                    NetworkHandler.sendToServer(new RecallSpyPacket(mission.getMissionId()));
                    return true;
                }

                boolean canDismiss = isFinished(status);
                if (canDismiss && isInBounds(mouseX, mouseY, rightX + rightW / 2 + 2, actionBtnY, rightW / 2 - 2, 11)) {
                    NetworkHandler.sendToServer(new DismissSpyMissionPacket(mission.getMissionId()));
                    selectedMissionIndex = -1;
                    if (targetInput != null) targetInput.setVisible(true);
                    return true;
                }

                return false;
            }

            // Deploy controls — y offsets match renderRightPage layout
            int w = rightW;

            // Mission type buttons at y = rightY + 74
            int typeY = rightY + 74;
            int btnW = (w - 3) / 2;
            for (int i = 0; i < MISSION_TYPES.length; i++) {
                int bx = rightX + (i % 2) * (btnW + 2);
                int by = typeY + (i / 2) * 13;
                if (isInBounds(mouseX, mouseY, bx, by, btnW, 11)) {
                    missionTypeIndex = i;
                    return true;
                }
            }

            // Deploy button at y = rightY + 102
            int deployY = rightY + 102;
            if (isInBounds(mouseX, mouseY, rightX, deployY, w, 12)) {
                if (targetColonyIndex >= 0 && targetColonyIndex < colonies.size()) {
                    ColonySummary target = colonies.get(targetColonyIndex);
                    NetworkHandler.sendToServer(new DeploySpyPacket(target.getId(),
                            MISSION_TYPES[missionTypeIndex]));
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInLeftPage(mouseX, mouseY)) {
            List<SpyMissionData> missions = missionSupplier.get();
            int maxVisible = (leftH - 14) / 22;
            int maxOffset = Math.max(0, missions.size() - maxVisible);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) delta));
            return true;
        }
        return false;
    }


    private String getStatusDisplayText(SpyMissionData m) {
        String status = m.getStatus();
        if ("DEPLOYING".equals(status)) {
            long travelMs = m.getTravelDurationMs();
            long elapsed = m.getElapsedTimeMs();
            int pct = travelMs > 0 ? (int) Math.min(99, elapsed * 100 / travelMs) : 0;
            return "Traveling... " + pct + "%";
        }
        if ("RETURNING".equals(status)) {
            long travelMs = m.getTravelDurationMs();
            long returnedMs = m.getElapsedReturnMs();
            int pct = travelMs > 0 ? (int) Math.min(99, returnedMs * 100 / travelMs) : 0;
            return "Returning... " + pct + "%";
        }
        if ("ACTIVE".equals(status)) return "Infiltrating";
        if ("FLEEING".equals(status)) return "Fleeing!";
        return status;
    }

    private int getStatusDisplayColor(SpyMissionData m) {
        return switch (m.getStatus()) {
            case "ACTIVE" -> GREEN_BRIGHT;
            case "DEPLOYING" -> GOLD_DARK;
            case "FLEEING" -> ORANGE;
            case "RETURNING" -> BLUE;
            case "KILLED" -> DANGER;
            case "ESCAPED", "RECALLED", "COMPLETED" -> INK_FAINT;
            default -> INK_GHOST;
        };
    }

    private boolean isFinished(String status) {
        return "ESCAPED".equals(status) || "RECALLED".equals(status) || "COMPLETED".equals(status);
    }

    private String formatElapsed(long ms) {
        if (ms <= 0) return "0m";
        long minutes = ms / 60000L;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        long rem = minutes % 60;
        return hours + "h " + rem + "m";
    }
}
