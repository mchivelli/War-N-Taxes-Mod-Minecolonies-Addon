package net.machiavelli.minecolonytax.pvp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

public class PvPArenaCommand {

    public void register(CommandDispatcher<CommandSourceStack> dispatcher, PvPBattleManager battleManager, PvPMapManager mapManager) {

        final SuggestionProvider<CommandSourceStack> MAP_SUGGESTIONS = (context, builder) ->
                SharedSuggestionProvider.suggest(PvPManager.INSTANCE.arenaMapsByName.keySet(), builder);

        dispatcher.register(Commands.literal("pvparena")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("mapName", StringArgumentType.word())
                                .executes(context -> mapManager.createMap(context, StringArgumentType.getString(context, "mapName")))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("mapName", StringArgumentType.word())
                                .suggests(MAP_SUGGESTIONS)
                                .executes(context -> mapManager.deleteMap(context, StringArgumentType.getString(context, "mapName")))))
                .then(Commands.literal("addspawn")
                        .then(Commands.argument("mapName", StringArgumentType.word())
                                .suggests(MAP_SUGGESTIONS)
                                .then(Commands.argument("spawnIndex", IntegerArgumentType.integer(1))
                                        .executes(context -> mapManager.addSpawnPoint(context,
                                                StringArgumentType.getString(context, "mapName"),
                                                IntegerArgumentType.getInteger(context, "spawnIndex"))))))
                .then(Commands.literal("setdefault")
                        .then(Commands.argument("mapName", StringArgumentType.word())
                                .suggests(MAP_SUGGESTIONS)
                                .executes(context -> mapManager.setDefaultMap(context, StringArgumentType.getString(context, "mapName")))))
                .then(Commands.literal("list")
                        .executes(mapManager::listMaps))
                .then(Commands.literal("info")
                        .then(Commands.argument("mapName", StringArgumentType.word())
                                .suggests(MAP_SUGGESTIONS)
                                .executes(context -> mapManager.showMapInfo(context, StringArgumentType.getString(context, "mapName"))))));

        dispatcher.register(Commands.literal("pvp")
                .then(Commands.literal("accept")
                        .executes(battleManager::handleAccept))
                .then(Commands.literal("decline")
                        .executes(battleManager::handleDecline))
                .then(Commands.literal("spectate")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(battleManager::handleSpectateStart))
                        .then(Commands.literal("stop")
                                .executes(battleManager::handleSpectateStop)))
                .then(Commands.argument("targetPlayer", EntityArgument.player())
                        .executes(context -> battleManager.handleDuel(context, false, false))
                        .then(Commands.argument("mapName", StringArgumentType.word())
                                .suggests(MAP_SUGGESTIONS)
                                .executes(context -> battleManager.handleDuel(context, false, true))
                                .then(Commands.argument("wager", IntegerArgumentType.integer(0))
                                        .executes(context -> battleManager.handleDuel(context, true, true)))))
                .executes(context -> {
                    context.getSource().sendFailure(Component.literal("Usage: /pvp <player> [map] [wager]"));
                    return 0;
                }));

        dispatcher.register(Commands.literal("teampvp")
                .then(Commands.literal("create")
                        .then(Commands.argument("mapName", StringArgumentType.word())
                                .suggests(MAP_SUGGESTIONS)
                                .executes(context -> battleManager.createTeamBattle(context, StringArgumentType.getString(context, "mapName")))))
                .then(Commands.literal("join")
                        .then(Commands.argument("battleId", StringArgumentType.string())
                                .then(Commands.argument("teamNumber", IntegerArgumentType.integer(1, 2))
                                        .executes(context -> battleManager.joinTeamBattle(context,
                                                StringArgumentType.getString(context, "battleId"),
                                                IntegerArgumentType.getInteger(context, "teamNumber"))))))
                .then(Commands.literal("switch")
                        .then(Commands.argument("battleId", StringArgumentType.string())
                                .then(Commands.argument("teamNumber", IntegerArgumentType.integer(1, 2))
                                        .executes(context -> battleManager.switchTeam(context,
                                                StringArgumentType.getString(context, "battleId"),
                                                IntegerArgumentType.getInteger(context, "teamNumber"))))))
                .then(Commands.literal("start")
                        .then(Commands.argument("battleId", StringArgumentType.string())
                                .executes(context -> battleManager.startPendingTeamBattle(context, StringArgumentType.getString(context, "battleId")))))
                .executes(context -> {
                    context.getSource().sendFailure(Component.literal("Usage: /teampvp <create|join|switch|start>"));
                    return 0;
                }));
    }
} 