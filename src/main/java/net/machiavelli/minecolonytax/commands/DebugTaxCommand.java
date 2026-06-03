package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
import net.machiavelli.minecolonytax.economy.WarExhaustionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * /debugtax [colony] — per-building tax and maintenance breakdown.
 *
 * Access model:
 *   Players (no OP): own colonies only (rank.isColonyManager())
 *   Admins (OP level 2): any colony by name or numeric ID
 */
public class DebugTaxCommand {

    public static int execute(CommandContext<CommandSourceStack> ctx, String colonyArg)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("You must be a player to run this command."));
            return 0;
        }

        boolean isAdmin = src.hasPermission(2);
        IColony colony = resolveColony(src, player, colonyArg, isAdmin);
        if (colony == null) {
            if (colonyArg != null) {
                src.sendFailure(Component.literal(isAdmin
                        ? "Colony not found: " + colonyArg
                        : "Colony not found or you don't manage it: " + colonyArg));
            } else {
                src.sendFailure(Component.literal("You are not a manager of any colony."));
            }
            return 0;
        }

        // Permission check: player must manage the colony, or be admin
        var rank = colony.getPermissions().getRank(player.getUUID());
        if (!isAdmin && (rank == null || !rank.isColonyManager())) {
            src.sendFailure(Component.literal("You must be a colony officer or admin to view this colony's tax breakdown."));
            return 0;
        }

        return printBreakdown(src, colony);
    }

    private static int printBreakdown(CommandSourceStack src, IColony colony) {
        double avgHappiness = TaxManager.calculateColonyAverageHappiness(colony);
        double happinessMult = TaxConfig.calculateHappinessTaxMultiplier(avgHappiness);
        boolean happinessEnabled = TaxConfig.isHappinessTaxModifierEnabled();
        double exhaustionMult = WarExhaustionManager.getTaxMultiplier(colony.getID());

        src.sendSuccess(() -> Component.literal("=== Tax Debug: " + colony.getName() + " (ID: " + colony.getID() + ") ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        int totalTax = 0, totalMaint = 0, skipped = 0;

        for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
            int level = building.getBuildingLevel();
            if (level == 0 || !building.isBuilt()) continue;
            String displayName = building.getBuildingDisplayName();
            int bTax = (int) (TaxConfig.getBaseTaxForBuilding(displayName)
                    + TaxConfig.getUpgradeTaxForBuilding(displayName) * level);
            int bMaint = (int) (TaxConfig.getBaseMaintenanceForBuilding(displayName)
                    + TaxConfig.getUpgradeMaintenanceForBuilding(displayName) * level);
            if (bTax == 0 && bMaint == 0) { skipped++; continue; }
            int bNet = bTax - bMaint;
            totalTax += bTax;
            totalMaint += bMaint;
            String name = displayName != null && displayName.length() > 22
                    ? displayName.substring(0, 21) + "\u2026" : (displayName != null ? displayName : "?");
            final String row = "  " + name + " lv" + level
                    + ":  tax " + bTax + "  maint " + bMaint + "  net " + (bNet >= 0 ? "+" : "") + bNet;
            final ChatFormatting rowColor = bNet >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
            src.sendSuccess(() -> Component.literal(row).withStyle(rowColor), false);
        }

        final int fTax = totalTax, fMaint = totalMaint, fNet = totalTax - totalMaint, fSkipped = skipped;
        src.sendSuccess(() -> Component.literal("  ------------------------------------------")
                .withStyle(ChatFormatting.GRAY), false);
        final String totalsRow = "  Totals:  tax " + fTax + "  maint " + fMaint
                + "  net " + (fNet >= 0 ? "+" : "") + fNet;
        src.sendSuccess(() -> Component.literal(totalsRow)
                .withStyle(fNet >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        if (fSkipped > 0) {
            src.sendSuccess(() -> Component.literal("  §7(" + fSkipped + " buildings with no tax config skipped)")
                    , false);
        }
        int storedBalance = TaxManager.getStoredTaxForColony(colony);
        src.sendSuccess(() -> Component.literal("  Stored balance: §e" + storedBalance)
                .withStyle(ChatFormatting.WHITE), false);
        if (happinessEnabled) {
            src.sendSuccess(() -> Component.literal(
                    "  Happiness: " + String.format("%.2f", happinessMult) + "x")
                    .withStyle(happinessMult >= 1.0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        }
        if (exhaustionMult != 1.0) {
            src.sendSuccess(() -> Component.literal(
                    "  War exhaustion: " + String.format("%.2f", exhaustionMult) + "x")
                    .withStyle(ChatFormatting.RED), false);
        }
        src.sendSuccess(() -> Component.literal("===========================")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        return 1;
    }

    private static IColony resolveColony(CommandSourceStack src, ServerPlayer player, String arg, boolean isAdmin) {
        IColonyManager mgr = IMinecoloniesAPI.getInstance().getColonyManager();

        if (arg != null) {
            // Try numeric ID
            try {
                int id = Integer.parseInt(arg);
                for (IColony c : mgr.getAllColonies()) {
                    if (c.getID() == id) return c;
                }
            } catch (NumberFormatException ignored) {}
            // Try by name — admins see all, players only see colonies they manage
            for (IColony c : mgr.getAllColonies()) {
                if (c.getName().equalsIgnoreCase(arg)) {
                    if (isAdmin) return c;
                    var rank = c.getPermissions().getRank(player.getUUID());
                    if (rank != null && rank.isColonyManager()) return c;
                }
            }
            return null;
        }

        // No arg: default to player's first managed colony
        Optional<IColony> own = mgr.getAllColonies().stream()
                .filter(c -> {
                    var rank = c.getPermissions().getRank(player.getUUID());
                    return rank != null && rank.isColonyManager();
                })
                .findFirst();
        return own.orElse(null);
    }
}
