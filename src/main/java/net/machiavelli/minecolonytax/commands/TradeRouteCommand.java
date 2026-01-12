package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.IMinecoloniesAPI;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.trade.TradeRouteManager;
import net.machiavelli.minecolonytax.trade.TradeRouteManager.TradeRouteData;
import net.machiavelli.minecolonytax.trade.TradeRouteManager.TradeRouteProposal;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Commands for managing trade routes between colonies
 */
public class TradeRouteCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("traderoute")
                .then(Commands.literal("propose")
                        .then(Commands.argument("colony_id", IntegerArgumentType.integer(1))
                                .executes(TradeRouteCommand::propose)))
                .then(Commands.literal("accept")
                        .then(Commands.argument("colony_id", IntegerArgumentType.integer(1))
                                .executes(TradeRouteCommand::accept)))
                .then(Commands.literal("deny")
                        .then(Commands.argument("colony_id", IntegerArgumentType.integer(1))
                                .executes(TradeRouteCommand::deny)))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("colony_id", IntegerArgumentType.integer(1))
                                .executes(TradeRouteCommand::cancel)))
                .then(Commands.literal("list")
                        .executes(TradeRouteCommand::list));
    }

    private static int propose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int targetId = IntegerArgumentType.getInteger(ctx, "colony_id");
        String result = TradeRouteManager.proposeTradeRoute(player, targetId);
        player.sendSystemMessage(Component.literal(result));
        return Command.SINGLE_SUCCESS;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int proposerId = IntegerArgumentType.getInteger(ctx, "colony_id");
        String result = TradeRouteManager.acceptTradeRoute(player, proposerId);
        player.sendSystemMessage(Component.literal(result));
        return Command.SINGLE_SUCCESS;
    }

    private static int deny(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int proposerId = IntegerArgumentType.getInteger(ctx, "colony_id");
        String result = TradeRouteManager.denyTradeRoute(player, proposerId);
        player.sendSystemMessage(Component.literal(result));
        return Command.SINGLE_SUCCESS;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int partnerId = IntegerArgumentType.getInteger(ctx, "colony_id");
        String result = TradeRouteManager.cancelTradeRoute(player, partnerId);
        player.sendSystemMessage(Component.literal(result));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        // Get player's colony
        IColony myColony = null;
        for (var world : player.getServer().getAllLevels()) {
            for (IColony col : IMinecoloniesAPI.getInstance().getColonyManager().getColonies(world)) {
                if (col.getPermissions().getOwner().equals(player.getUUID())) {
                    myColony = col;
                    break;
                }
            }
            if (myColony != null)
                break;
        }

        if (myColony == null) {
            player.sendSystemMessage(Component.literal("§cYou must own a colony."));
            return 0;
        }

        int myId = myColony.getID();
        player.sendSystemMessage(Component.literal("§6§l=== Trade Routes for " + myColony.getName() + " ==="));

        // Active routes
        List<TradeRouteData> routes = TradeRouteManager.getRoutesForColony(myId);
        if (routes.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7No active trade routes."));
        } else {
            player.sendSystemMessage(Component.literal("§e§lActive Routes:"));
            for (TradeRouteData route : routes) {
                int partnerId = route.getPartner(myId);
                IColony partner = TradeRouteManager.getColonyById(partnerId);
                String name = partner != null ? partner.getName() : "#" + partnerId;
                int income = route.distanceChunks * TaxConfig.getTradeRouteIncomePerChunk();
                String status = route.active ? "§aActive" : "§cInactive";
                player.sendSystemMessage(Component.literal(
                        "§e- " + name + " §7(" + route.distanceChunks + " chunks, +" + income + "/cycle) " + status));
            }
        }

        // Pending proposals
        List<TradeRouteProposal> proposals = TradeRouteManager.getProposalsForColony(myId);
        if (!proposals.isEmpty()) {
            player.sendSystemMessage(Component.literal("§6§lPending Proposals:"));
            for (TradeRouteProposal prop : proposals) {
                IColony proposer = TradeRouteManager.getColonyById(prop.proposerColonyId);
                String name = proposer != null ? proposer.getName() : "#" + prop.proposerColonyId;
                long minsLeft = (prop.expirationTime - System.currentTimeMillis()) / 60000;
                player.sendSystemMessage(Component.literal(
                        "§6- From " + name + " §7(expires in " + minsLeft + "m)"));
            }
        }

        int count = TradeRouteManager.getActiveRouteCount(myId);
        int max = TaxConfig.getMaxTradeRoutesPerColony();
        player.sendSystemMessage(Component.literal("§7Routes: " + count + "/" + max));

        return Command.SINGLE_SUCCESS;
    }
}
