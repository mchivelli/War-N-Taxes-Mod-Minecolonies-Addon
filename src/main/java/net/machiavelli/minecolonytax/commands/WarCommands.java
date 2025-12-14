package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.colony.IColony;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.peace.PeaceProposalManager;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.minecolonies.api.colony.IColonyManager;

public class WarCommands {

    // private static final Logger LOGGER = LogManager.getLogger(WarCommands.class);
    // // LOGGER is unused
    private static RaidManager raidManagerInstance; // Instance for non-static calls
    private static PeaceProposalManager peaceProposalManagerInstance; // Instance for non-static calls

    private static RaidManager getRaidManager() {
        if (raidManagerInstance == null) {
            raidManagerInstance = new RaidManager();
        }
        return raidManagerInstance;
    }

    private static PeaceProposalManager getPeaceProposalManager() {
        if (peaceProposalManagerInstance == null) {
            peaceProposalManagerInstance = new PeaceProposalManager();
        }
        return peaceProposalManagerInstance;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("raid")
                .then(Commands.argument("colony", StringArgumentType.string())
                        .executes(WarCommands::handleRaidCommand) // Changed to avoid direct static call if manager is
                                                                  // used
                ));
        dispatcher.register(Commands.literal("wagewar")
                .then(Commands.argument("colony", StringArgumentType.string())
                        .executes(WarCommands::handleWageWarCommand)));
        dispatcher.register(Commands.literal("suepeace")
                .then(Commands.literal("whitepeace")
                        .executes(WarCommands::suePeaceWhiteCommand))
                .then(Commands.literal("reparations")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> suePeaceReparationsCommand(ctx,
                                        IntegerArgumentType.getInteger(ctx, "amount"))))));
        dispatcher.register(Commands.literal("joinwar")
                .executes(WarCommands::joinWarCommand));
        dispatcher.register(Commands.literal("leavewar")
                .executes(WarCommands::leaveWarCommand));
        dispatcher.register(Commands.literal("war")
                .then(Commands.literal("accept")
                        .then(Commands.argument("colonyId", IntegerArgumentType.integer())
                                .executes(ctx -> handleWarResponseCommand(ctx, true))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("colonyId", IntegerArgumentType.integer())
                                .executes(ctx -> handleWarResponseCommand(ctx, false)))));
        dispatcher.register(Commands.literal("wardebug")
                .requires(src -> src.hasPermission(2))
                .executes(WarCommands::debugWarCommand));
        dispatcher.register(Commands.literal("warinfo")
                .executes(WarCommands::warInfoCommand));
        dispatcher.register(Commands.literal("choosewarside")
                .then(Commands.literal("attacker")
                        .executes(ctx -> chooseWarSideCommand(ctx, true)))
                .then(Commands.literal("defender")
                        .executes(ctx -> chooseWarSideCommand(ctx, false))));

        dispatcher.register(Commands.literal("warstop") // For stopping a specific war by colony name
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("colony", StringArgumentType.string())
                        .executes(WarCommands::stopWarCommand)));
        dispatcher.register(Commands.literal("warstopall") // New command to stop all wars
                .requires(src -> src.hasPermission(2))
                .executes(WarCommands::stopAllWarsCommand));
        dispatcher.register(Commands.literal("raidstop")
                .requires(src -> src.hasPermission(2))
                .executes(WarCommands::stopRaidCommand));
        dispatcher.register(
                Commands.literal("peace")
                        .then(Commands.literal("accept")
                                .executes(WarCommands::acceptPeaceCommand))
                        .then(Commands.literal("decline")
                                .executes(WarCommands::declinePeaceCommand)));
    }

    // --- COMMAND HANDLERS ---
    // These methods now primarily parse context and delegate to the respective
    // managers or WarSystem.

    private static int handleRaidCommand(CommandContext<CommandSourceStack> context) {
        return getRaidManager().handleRaid(context);
    }

    private static int handleWageWarCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer attacker = ctx.getSource().getPlayerOrException();
        String colonyName = StringArgumentType.getString(ctx, "colony");
        Level level = ctx.getSource().getLevel();
        IColony targetColony = WarSystem.findColonyByName(colonyName, level); // Use WarSystem's helper

        if (targetColony == null) {
            ctx.getSource().sendFailure(Component.literal("Target colony not found!"));
            return 0;
        }
        if (!RaidManager.getActiveRaids().isEmpty()) { // Check active raids via RaidManager
            ctx.getSource().sendFailure(Component.literal("A raid is currently active! You cannot declare war."));
            return 0;
        }
        if (WarSystem.ACTIVE_WARS.containsKey(targetColony.getID())) {
            ctx.getSource().sendFailure(Component.literal("A war is already active for this colony!"));
            return 0;
        }

        // Faction Alliance Check
        IColony attackerColony = IColonyManager.getInstance().getColonies(level).stream()
                .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
                .findFirst()
                .orElseGet(() -> IColonyManager.getInstance().getColonies(level).stream()
                        .filter(c -> c.getPermissions().getPlayers().containsKey(attacker.getUUID()))
                        .findFirst()
                        .orElse(null));

        if (attackerColony != null) {
            if (net.machiavelli.minecolonytax.faction.FactionManager.areAllies(attackerColony.getID(),
                    targetColony.getID())) {
                ctx.getSource().sendFailure(Component.literal("You cannot declare war on an allied faction!")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        // Delegate core logic to WarSystem
        return WarSystem.processWageWarRequest(attacker, targetColony, ctx.getSource());
    }

    private static int handleWarResponseCommand(CommandContext<CommandSourceStack> ctx, boolean accepted)
            throws CommandSyntaxException {
        ServerPlayer executor = ctx.getSource().getPlayerOrException();
        int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");
        // Delegate core logic to WarSystem
        return WarSystem.processWarResponse(executor, colonyId, accepted, ctx.getSource());
    }

    private static int joinWarCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war == null) {
            ctx.getSource()
                    .sendFailure(Component.translatable("command.joinwar.error.none").withStyle(ChatFormatting.RED));
            return 0;
        }
        return WarSystem.processJoinWar(player, ctx.getSource());
    }

    private static int leaveWarCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        WarData war = WarSystem.getActiveWarForPlayer(player);
        if (war == null) {
            ctx.getSource()
                    .sendFailure(Component.translatable("command.joinwar.error.none").withStyle(ChatFormatting.RED));
            return 0;
        }

        // Check if player is a colony owner
        if (player.getUUID().equals(war.getColony().getPermissions().getOwner()) ||
                player.getUUID().equals(war.getAttacker()) ||
                (war.getAttackerColony() != null
                        && player.getUUID().equals(war.getAttackerColony().getPermissions().getOwner()))) {
            ctx.getSource()
                    .sendFailure(Component.translatable("command.leavewar.error.owner").withStyle(ChatFormatting.RED));
            return 0;
        }

        return WarSystem.processLeaveWar(player, ctx.getSource());
    }

    private static int suePeaceWhiteCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return getPeaceProposalManager().suePeaceWhite(ctx);
    }

    private static int suePeaceReparationsCommand(CommandContext<CommandSourceStack> ctx, int amount)
            throws CommandSyntaxException {
        return getPeaceProposalManager().suePeaceReparations(ctx, amount);
    }

    private static int acceptPeaceCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return getPeaceProposalManager().acceptPeace(ctx);
    }

    private static int declinePeaceCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return getPeaceProposalManager().declinePeace(ctx);
    }

    private static int warInfoCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        WarData war = WarSystem.getActiveWarForPlayer(player);

        if (war == null) {
            ctx.getSource().sendFailure(
                    Component.literal("You are not currently in an active war!").withStyle(ChatFormatting.RED));
            return 0;
        }

        StringBuilder sb = new StringBuilder();
        String attackerColName = (war.getAttackerColony() != null) ? war.getAttackerColony().getName()
                : "UnknownAttackerColony";
        String defenderColName = (war.getColony() != null) ? war.getColony().getName() : "UnknownDefenderColony";
        String status = (war.getStatus() != null) ? war.getStatus().toString() : "UNKNOWN";

        long now = System.currentTimeMillis();
        long remaining;
        String remainingStr;

        if (war.isJoinPhaseActive()) {
            // During join phase, show time until join phase ends
            remaining = Math.max(0, (war.getJoinPhaseEndTime() - now) / 1000);
            remainingStr = String.format("%02d:%02d", remaining / 60, remaining % 60);
        } else {
            // During active war, show time until war ends
            long warDuration = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
            long elapsed = (now - war.warStartTime) / 1000;
            remaining = Math.max(0, warDuration - elapsed);
            remainingStr = String.format("%02d:%02d", remaining / 60, remaining % 60);
        }

        sb.append("§a§lWar Report: ").append(attackerColName).append(" vs ").append(defenderColName).append("\n");
        sb.append("§aWar ID: §f").append(war.getWarID()).append("\n");
        sb.append("§aStatus: §f").append(status).append("\n");
        sb.append("§aTime Remaining: §f").append(remainingStr).append("\n");
        sb.append("------------------------------------------\n");
        sb.append("§bAttacker Team:\n");
        war.getAttackerLives().forEach((uuid, lives) -> {
            ServerPlayer sp = ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
            String name = (sp != null) ? sp.getName().getString() : "OfflinePlayer";
            sb.append("  ").append(name).append(" - ").append(lives).append(" lives\n");
        });
        sb.append("§bDefender Team:\n");
        war.getDefenderLives().forEach((uuid, lives) -> {
            ServerPlayer sp = ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
            String name = (sp != null) ? sp.getName().getString() : "OfflinePlayer";
            sb.append("  ").append(name).append(" - ").append(lives).append(" lives\n");
        });
        sb.append("§aGuard Count:\n");
        sb.append(attackerColName).append(" = ").append(war.getRemainingAttackerGuards()).append("\n");
        sb.append(defenderColName).append(" = ").append(war.getRemainingDefenderGuards()).append("\n");
        sb.append("§6==========================\n");
        if (!war.getPenaltyReport().isEmpty()) {
            sb.append("§cPenalty Report: §f").append(war.getPenaltyReport()).append("\n");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int debugWarCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        // Ensure this command is executed by a player with appropriate permissions
        ctx.getSource().getPlayerOrException(); // This validates player existence and permissions
        if (WarSystem.ACTIVE_WARS.isEmpty()) {
            ctx.getSource()
                    .sendFailure(Component.literal("No active wars at the moment!").withStyle(ChatFormatting.RED));
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            String attackerColName = (war.getAttackerColony() != null) ? war.getAttackerColony().getName()
                    : "UnknownAttackerColony";
            String defenderColName = (war.getColony() != null) ? war.getColony().getName() : "UnknownDefenderColony";
            String status = (war.getStatus() != null) ? war.getStatus().toString() : "UNKNOWN";
            long now = System.currentTimeMillis();
            long remaining;
            String remainingStr;

            if (war.isJoinPhaseActive()) {
                // During join phase, show time until join phase ends
                remaining = Math.max(0, (war.getJoinPhaseEndTime() - now) / 1000);
                remainingStr = String.format("%02d:%02d", remaining / 60, remaining % 60);
            } else {
                // During active war, show time until war ends
                long warDuration = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
                long elapsed = (now - war.warStartTime) / 1000;
                remaining = Math.max(0, warDuration - elapsed);
                remainingStr = String.format("%02d:%02d", remaining / 60, remaining % 60);
            }

            sb.append("\n§e§lWar Report: ").append(attackerColName).append(" vs ").append(defenderColName).append("\n");
            sb.append("§eWar ID: §f").append(war.getWarID()).append("\n");
            sb.append("§eStatus: §f").append(status).append("\n");
            sb.append("§eTime Remaining: §f").append(remainingStr).append("\n");
            sb.append("------------------------------------------\n");
            sb.append("§bAttacker Team:\n");
            war.getAttackerLives().forEach((uuid, lives) -> {
                ServerPlayer sp = ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
                String name = (sp != null) ? sp.getName().getString() : "OfflinePlayer";
                sb.append("  ").append(name).append(" - ").append(lives).append(" lives\n");
            });
            sb.append("§bDefender Team:\n");
            war.getDefenderLives().forEach((uuid, lives) -> {
                ServerPlayer sp = ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
                String name = (sp != null) ? sp.getName().getString() : "OfflinePlayer";
                sb.append("  ").append(name).append(" - ").append(lives).append(" lives\n");
            });
            sb.append("§eGuard Count:\n");
            sb.append(attackerColName).append(" = ").append(war.getRemainingAttackerGuards()).append("\n");
            sb.append(defenderColName).append(" = ").append(war.getRemainingDefenderGuards()).append("\n");
            sb.append("§6==========================\n");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int stopAllWarsCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<WarData> activeWars = WarSystem.ACTIVE_WARS.values();
        if (activeWars.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No active wars to stop.").withStyle(ChatFormatting.RED));
            return 0;
        }
        List<IColony> coloniesToStop = new ArrayList<>();
        for (WarData war : activeWars) {
            coloniesToStop.add(war.getColony());
        }
        for (IColony colony : coloniesToStop) {
            WarSystem.endWar(colony);
            WarSystem.sendColonyMessage(colony,
                    Component.literal("War has been stopped by an operator.").withStyle(ChatFormatting.GOLD));
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("All active wars have been stopped.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int stopWarCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String colonyName = StringArgumentType.getString(ctx, "colony");
        IColony colony = WarSystem.findColonyByName(colonyName, ctx.getSource().getLevel());

        if (colony == null) {
            ctx.getSource()
                    .sendFailure(Component.literal("Colony not found: " + colonyName).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!WarSystem.ACTIVE_WARS.containsKey(colony.getID())) {
            ctx.getSource().sendFailure(
                    Component.literal("No active war for colony: " + colonyName).withStyle(ChatFormatting.RED));
            return 0;
        }
        WarSystem.endWar(colony);
        WarSystem.sendColonyMessage(colony,
                Component.literal("War for " + colony.getName() + " has been stopped by an operator.")
                        .withStyle(ChatFormatting.GOLD));
        ctx.getSource().sendSuccess(
                () -> Component.literal("War stopped for " + colony.getName() + ".").withStyle(ChatFormatting.GREEN),
                false);
        return 1;
    }

    private static int stopRaidCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        // This now delegates to RaidManager, which needs to handle how a raid is
        // targeted for stopping by an admin.
        // The original logic activeRaids.get(player.getUUID()) was for a player
        // stopping their *own* raid.
        // Admin command needs a different way to target, or stops all.
        // For now, RaidManager.stopRaidCommand will try to stop any active raid if
        // called by admin.
        return getRaidManager().stopRaidCommand(ctx);
    }

    /**
     * Handle the player's choice of which side to join in a war when they are
     * members of both teams.
     * 
     * @param ctx           Command context
     * @param joinAttackers True to join attackers, false to join defenders
     * @return Command success status
     */
    private static int chooseWarSideCommand(CommandContext<CommandSourceStack> ctx, boolean joinAttackers) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            WarData war = WarSystem.getActiveWarForPlayer(player);

            if (war == null) {
                ctx.getSource().sendFailure(Component.literal("No active war to join."));
                return 0;
            }

            if (!war.isJoinPhaseActive()) {
                ctx.getSource().sendFailure(Component.literal("Join phase is over."));
                return 0;
            }

            int playerLives = TaxConfig.PLAYER_LIVES_IN_WAR.get();

            if (joinAttackers) {
                // Join attacker side
                if (!war.getAttackerLives().containsKey(player.getUUID())) {
                    war.getAttackerLives().put(player.getUUID(), playerLives);
                    player.sendSystemMessage(Component.literal("You have chosen to join the attacking side.")
                            .withStyle(ChatFormatting.GREEN));
                    if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
                        war.alliesBossEvent.addPlayer(player);
                    } else {
                        war.bossEvent.addPlayer(player);
                    }
                    return 1;
                } else {
                    ctx.getSource().sendFailure(Component.literal("You are already registered on the attacking side."));
                    return 0;
                }
            } else {
                // Join defender side
                if (!war.getDefenderLives().containsKey(player.getUUID())) {
                    war.getDefenderLives().put(player.getUUID(), playerLives);
                    player.sendSystemMessage(Component.literal("You have chosen to join the defending side.")
                            .withStyle(ChatFormatting.GREEN));
                    if (war.alliesBossEvent != null && war.alliesBossEvent.isVisible()) {
                        war.alliesBossEvent.addPlayer(player);
                    } else {
                        war.bossEvent.addPlayer(player);
                    }
                    return 1;
                } else {
                    ctx.getSource().sendFailure(Component.literal("You are already registered on the defending side."));
                    return 0;
                }
            }
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("You must be a player to use this command."));
            return 0;
        }
    }
}
