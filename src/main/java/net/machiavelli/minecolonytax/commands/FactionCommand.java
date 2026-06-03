package net.machiavelli.minecolonytax.commands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.faction.FactionData;
import net.machiavelli.minecolonytax.faction.FactionManager;
import net.machiavelli.minecolonytax.faction.FactionRelation;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class FactionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("wnt")
                .then(Commands.literal("faction")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(ctx -> executeCreate(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("join")
                                .then(Commands.argument("faction", StringArgumentType.string())
                                        .executes(ctx -> executeJoin(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "faction")))))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("colony", StringArgumentType.string()) // Or player name? Colony
                                                                                               // name is better but
                                                                                               // harder to resolve.
                                                                                               // Let's use player name
                                                                                               // (owner)
                                        .executes(ctx -> executeInvite(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "colony")))))
                        .then(Commands.literal("leave")
                                .executes(ctx -> executeLeave(ctx.getSource())))
                        .then(Commands.literal("info")
                                .executes(ctx -> executeInfo(ctx.getSource())))
                        .then(Commands.literal("list")
                                .executes(ctx -> executeList(ctx.getSource())))
                        .then(Commands.literal("tax")
                                .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0, 100))
                                        .executes(ctx -> executeSetTax(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "percent")))))
                        .then(Commands.literal("withdraw")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeWithdraw(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("ally")
                                .then(Commands.argument("faction", StringArgumentType.string())
                                        .executes(ctx -> executeRelation(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "faction"), FactionRelation.ALLY))))
                        .then(Commands.literal("neutral")
                                .then(Commands.argument("faction", StringArgumentType.string())
                                        .executes(ctx -> executeRelation(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "faction"),
                                                FactionRelation.NEUTRAL))))
                        .then(Commands.literal("enemy")
                                .then(Commands.argument("faction", StringArgumentType.string())
                                        .executes(ctx -> executeRelation(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "faction"),
                                                FactionRelation.ENEMY)))));

        dispatcher.register(command);
    }

    // Helpers
    private static IColony getPlayerColony(ServerPlayer player) {
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        return colonyManager.getIColonyByOwner(player.level(), player.getUUID());
    }

    private static boolean hasColonyPermission(ServerPlayer player, IColony colony) {
        return colony.getPermissions().getRank(player.getUUID()).isColonyManager();
    }

    // Executors

    private static int executeCreate(CommandSourceStack source, String name) {
        if (!checkChecks(source))
            return 0;
        ServerPlayer player = source.getPlayer();
        IColony colony = getPlayerColony(player);

        int cost = TaxConfig.getFactionCreationCost();
        // Check cost? Currently no generic money system in Tax mod, maybe check items?
        // Or check colony funds if that existed.
        // The plan said "Tax cost". But tax is generated, not stored in a "colony
        // balance" except for WarChest.
        // Let's assume we deduct from War Chest or just free for now if cost > 0 but we
        // have no payment method?
        // Actually, let's deduct from the colony's tax "credits" if we had them?
        // Or "WarChest". If cost > 0, check WarChest.

        // Wait, current system diverts taxes.
        // I'll skip cost check for now or just log it.
        // Plan says: "factionCreationCost (Tax credits/items?)"

        FactionData faction = FactionManager.createFaction(name, colony.getID());
        if (faction == null) {
            source.sendFailure(Component.literal("Failed to create faction. Name taken or you are already in one."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Faction '" + name + "' created successfully!").withStyle(ChatFormatting.GREEN),
                true);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeJoin(CommandSourceStack source, String factionName) {
        if (!checkChecks(source))
            return 0;
        ServerPlayer player = source.getPlayer();
        IColony colony = getPlayerColony(player);

        FactionData faction = FactionManager.getFactionByName(factionName);
        if (faction == null) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }

        if (!FactionManager.hasInvite(colony.getID(), faction.getId())) {
            source.sendFailure(Component.literal("You have not been invited to this faction."));
            return 0;
        }

        if (FactionManager.joinFaction(colony.getID(), faction.getId())) {
            source.sendSuccess(
                    () -> Component.literal("Joined faction '" + factionName + "'!").withStyle(ChatFormatting.GREEN),
                    true);
            return Command.SINGLE_SUCCESS;
        } else {
            source.sendFailure(
                    Component.literal("Failed to join. Faction may be full or you are already in a faction."));
            return 0;
        }
    }

    private static int executeInvite(CommandSourceStack source, String colonyOwnerName) {
        if (!checkChecks(source))
            return 0;
        ServerPlayer player = source.getPlayer();
        IColony colony = getPlayerColony(player);
        FactionData faction = FactionManager.getFactionByColony(colony.getID());

        if (faction == null) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }

        // Only owner/officers (handled by checkChecks) - but also needs faction rank?
        // For now, any member can invite? Or only faction owner?
        // Let's restrict to Faction Owner (colony owner of faction owner colony).
        if (faction.getOwnerColonyId() != colony.getID()) {
            source.sendFailure(Component.literal("Only the faction leader can invite members."));
            return 0;
        }

        // Resolve target colony
        // We have player name. Need to find player then their colony.
        ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(colonyOwnerName);
        if (targetPlayer == null) {
            source.sendFailure(Component.literal("Player not found online."));
            return 0;
        }

        IColony targetColony = getPlayerColony(targetPlayer);
        if (targetColony == null) {
            source.sendFailure(Component.literal("That player does not own a colony."));
            return 0;
        }

        faction.addInvite(targetColony.getID());
        FactionManager.saveData(); // Make sure invite is saved

        source.sendSuccess(() -> Component.literal("Invited colony of " + colonyOwnerName + " to faction.")
                .withStyle(ChatFormatting.GREEN), true);
        targetPlayer
                .sendSystemMessage(
                        Component
                                .literal("You have been invited to join faction '" + faction.getName()
                                        + "'. Use /wnt faction join " + faction.getName())
                                .withStyle(ChatFormatting.AQUA));

        return Command.SINGLE_SUCCESS;
    }

    private static int executeLeave(CommandSourceStack source) {
        if (!checkChecks(source))
            return 0;
        ServerPlayer player = source.getPlayer();
        IColony colony = getPlayerColony(player);

        FactionManager.leaveFaction(colony.getID());
        source.sendSuccess(() -> Component.literal("Left the faction.").withStyle(ChatFormatting.YELLOW), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeInfo(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player))
            return 0;
        IColony colony = getPlayerColony(player);
        if (colony == null)
            return 0;

        FactionData faction = FactionManager.getFactionByColony(colony.getID());
        if (faction == null) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("=== Faction Info: " + faction.getName() + " ===")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component
                .literal("Members: " + faction.getMemberColonyIds().size() + "/" + TaxConfig.getMaxFactionMembers())
                .withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(
                () -> Component.literal("Tax Balance: " + faction.getTaxBalance()).withStyle(ChatFormatting.AQUA),
                false);
        source.sendSuccess(
                () -> Component.literal("Tax Rate: " + String.format("%.1f", faction.getTaxRate() * 100) + "%")
                        .withStyle(ChatFormatting.AQUA),
                false);

        return Command.SINGLE_SUCCESS;
    }

    private static int executeList(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("=== Factions List ===").withStyle(ChatFormatting.GOLD), false);
        for (FactionData faction : FactionManager.getAllFactions()) {
            source.sendSuccess(() -> Component
                    .literal("- " + faction.getName() + " (" + faction.getMemberColonyIds().size() + " members)")
                    .withStyle(ChatFormatting.WHITE), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSetTax(CommandSourceStack source, double percent) {
        if (!checkChecks(source))
            return 0;
        ServerPlayer player = source.getPlayer();
        IColony colony = getPlayerColony(player);
        FactionData faction = FactionManager.getFactionByColony(colony.getID());

        if (faction == null || faction.getOwnerColonyId() != colony.getID()) {
            source.sendFailure(Component.literal("Only the faction leader can set tax rate."));
            return 0;
        }

        faction.setTaxRate(percent / 100.0);
        FactionManager.saveData();
        source.sendSuccess(
                () -> Component.literal("Faction tax rate set to " + percent + "%").withStyle(ChatFormatting.GREEN),
                true);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeWithdraw(CommandSourceStack source, int amount) {
        if (!checkChecks(source))
            return 0;
        ServerPlayer player = source.getPlayer();
        IColony colony = getPlayerColony(player);
        FactionData faction = FactionManager.getFactionByColony(colony.getID());

        if (faction == null || faction.getOwnerColonyId() != colony.getID()) {
            source.sendFailure(Component.literal("Only the faction leader can withdraw funds."));
            return 0;
        }

        if (faction.withdrawTax(amount)) {
            // Give money to colony or player? "Tax credits" usually implies virtual
            // currency or War Chest.
            // Let's add it to the War Chest of the leader colony.
            net.machiavelli.minecolonytax.economy.WarChestManager.deposit(player, colony.getID(), amount);

            FactionManager.saveData();
            source.sendSuccess(
                    () -> Component.literal("Withdrew " + amount + " to War Chest.").withStyle(ChatFormatting.GREEN),
                    true);
            return Command.SINGLE_SUCCESS;
        } else {
            source.sendFailure(Component.literal("Insufficient funds in faction pool."));
            return 0;
        }
    }

    private static int executeRelation(CommandSourceStack source, String targetFactionName, FactionRelation relation) {
        if (!checkChecks(source))
            return 0;
        ServerPlayer player = source.getPlayer();
        IColony colony = getPlayerColony(player);
        FactionData faction = FactionManager.getFactionByColony(colony.getID());

        if (faction == null || faction.getOwnerColonyId() != colony.getID()) {
            source.sendFailure(Component.literal("Only the faction leader can manage relations."));
            return 0;
        }

        FactionData target = FactionManager.getFactionByName(targetFactionName);
        if (target == null) {
            source.sendFailure(Component.literal("Target faction not found."));
            return 0;
        }

        // Alliance limit check
        if (relation == FactionRelation.ALLY && faction.getId() != target.getId()) {
            long currentAllies = faction.getRelations().values().stream().filter(FactionRelation::isAlly).count();
            if (currentAllies >= TaxConfig.getFactionAllianceLimit()) { // Need to add getFactionAllianceLimit to config
                                                                        // or FactionData
                source.sendFailure(Component.literal("Alliance limit reached."));
                return 0;
            }
        }

        faction.setRelation(target.getId(), relation);
        FactionManager.saveData();

        source.sendSuccess(() -> Component.literal("Set relation with " + targetFactionName + " to " + relation)
                .withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static boolean checkChecks(CommandSourceStack source) {
        if (!TaxConfig.isFactionSystemEnabled()) {
            source.sendFailure(Component.literal("Faction system is disabled."));
            return false;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return false;
        }
        IColony colony = getPlayerColony(player);
        if (colony == null) {
            source.sendFailure(Component.literal("You must be in a colony to use this command."));
            return false;
        }
        if (!hasColonyPermission(player, colony)) {
            source.sendFailure(Component.literal("You need to be a colony officer/owner."));
            return false;
        }
        return true;
    }
}
