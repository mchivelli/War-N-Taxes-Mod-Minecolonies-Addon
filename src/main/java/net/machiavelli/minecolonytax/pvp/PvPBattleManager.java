package net.machiavelli.minecolonytax.pvp;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.pvp.model.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PvPBattleManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private final PvPManager pvpManager = PvPManager.INSTANCE;

    public void handlePlayerDefeat(ServerPlayer player, ActiveBattle battle, net.minecraft.world.damagesource.DamageSource source) {
        UUID playerId = player.getUUID();
        
        // Prevent duplicate defeat handling
        if (pvpManager.defeatedPlayers.containsKey(playerId)) {
            return;
        }
        
        // Mark player as defeated
        pvpManager.defeatedPlayers.put(playerId, battle.getBattleId());
        
        // Track kill statistics
        if (source.getEntity() instanceof ServerPlayer killer) {
            if (battle.getEnemies(playerId).contains(killer.getUUID())) {
                updatePlayerKill(killer.getUUID());
            }
        }

        player.sendSystemMessage(Component.literal("You have been eliminated from the battle!").withStyle(ChatFormatting.RED));
        player.sendSystemMessage(Component.literal("You will be restored in 5 seconds...").withStyle(ChatFormatting.YELLOW));
        
        boolean isDuel = battle.getTeams().size() == 2 && battle.getTeams().get(0).size() == 1 && battle.getTeams().get(1).size() == 1;
        updatePlayerStats(playerId, false, true, !isDuel);
        
        // Schedule restoration after 5 seconds
        PvPManager.BATTLE_END_SCHEDULER.schedule(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> {
                    restoreDefeatedPlayer(player, battle);
                });
            }
        }, 5, java.util.concurrent.TimeUnit.SECONDS);
        
        checkForBattleEnd(battle);
    }
    
    private void restoreDefeatedPlayer(ServerPlayer player, ActiveBattle battle) {
        if (player == null) return;
        
        UUID playerId = player.getUUID();
        
        // Remove from defeated players tracking
        pvpManager.defeatedPlayers.remove(playerId);
        
        // Get original position and game mode
        GlobalPos originalPos = battle.getOriginalPositions().get(playerId);
        GameType originalGameMode = pvpManager.playerOriginalGameModes.getOrDefault(playerId, GameType.SURVIVAL);
        
        // Restore player to their original state
        restorePlayer(player, originalPos, originalGameMode);
        
        // Clean up
        pvpManager.playerOriginalGameModes.remove(playerId);
        
        player.sendSystemMessage(Component.literal("You have been restored to your original position.").withStyle(ChatFormatting.GREEN));
    }

    public void startTeamBattle(TeamBattle teamBattle) {
        if (!teamBattle.canStart()) {
            return;
        }

        String mapName = teamBattle.getMapName();
        PvPMap map = pvpManager.arenaMapsByName.get(mapName);

        if (map == null) {
            notifyTeamBattlePlayers(teamBattle, Component.literal("Map not found! Battle cancelled.").withStyle(ChatFormatting.RED));
            pvpManager.pendingTeamBattles.remove(teamBattle.getBattleId());
            return;
        }

        if (map.getSpawnPoints().size() < teamBattle.getTotalPlayers()) {
            notifyTeamBattlePlayers(teamBattle, Component.literal("Not enough spawn points on map for " + teamBattle.getTotalPlayers() + " players! Battle cancelled.").withStyle(ChatFormatting.RED));
            pvpManager.pendingTeamBattles.remove(teamBattle.getBattleId());
            return;
        }

        // Filter out null spawn points and validate
        List<GlobalPos> validSpawnPoints = map.getSpawnPoints().stream()
                .filter(pos -> pos != null)
                .toList();
        
        if (validSpawnPoints.size() < teamBattle.getTotalPlayers()) {
            notifyTeamBattlePlayers(teamBattle, Component.literal("Not enough valid spawn points on map for " + teamBattle.getTotalPlayers() + " players! Battle cancelled.").withStyle(ChatFormatting.RED));
            pvpManager.pendingTeamBattles.remove(teamBattle.getBattleId());
            return;
        }

        if (pvpManager.lockedMaps.contains(mapName)) {
            notifyTeamBattlePlayers(teamBattle, Component.literal("Map is currently in use! Battle cancelled.").withStyle(ChatFormatting.RED));
            pvpManager.pendingTeamBattles.remove(teamBattle.getBattleId());
            return;
        }

        lockMap(mapName);
        teamBattle.setState(TeamBattleState.IN_PROGRESS);

        List<List<UUID>> teams = List.of(
                new ArrayList<>(teamBattle.getTeam1()),
                new ArrayList<>(teamBattle.getTeam2())
        );

        String battleId = "ab_" + System.currentTimeMillis();
        ActiveBattle activeBattle = new ActiveBattle(battleId, teams, validSpawnPoints, mapName);
        pvpManager.activeBattles.put(battleId, activeBattle);
        pvpManager.pendingTeamBattles.remove(teamBattle.getBattleId());
        startBattle(activeBattle);
    }

    public void handleBattleTimerExpiry(String battleId) {
        ActiveBattle battle = pvpManager.activeBattles.get(battleId);
        if (battle == null) return;
        
        // Count remaining players per team
        Map<Integer, Integer> teamPlayerCounts = new HashMap<>();
        for (int teamIndex = 0; teamIndex < battle.getTeams().size(); teamIndex++) {
            int aliveCount = 0;
            for (UUID playerId : battle.getTeams().get(teamIndex)) {
                ServerPlayer player = getPlayerByUUID(playerId);
                if (player != null && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    aliveCount++;
                }
            }
            teamPlayerCounts.put(teamIndex, aliveCount);
        }
        
        // Find team with most players remaining
        int maxPlayers = teamPlayerCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Integer> teamsWithMaxPlayers = teamPlayerCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == maxPlayers)
                .map(Map.Entry::getKey)
                .toList();
        
        if (maxPlayers == 0) {
            // All players eliminated
            endBattleAsDraw(battle, "Time expired - all players eliminated!");
        } else if (teamsWithMaxPlayers.size() == 1) {
            // One team has more players
            endBattleWithWinner(battle, teamsWithMaxPlayers.get(0), "Time expired - victory by player count!");
        } else {
            // Tie - multiple teams have the same number of players
            endBattleAsDraw(battle, "Time expired - tie!");
        }
    }

    public void sendBattleTimerNotifications(String battleId, int remainingTicks) {
        ActiveBattle battle = pvpManager.activeBattles.get(battleId);
        if (battle == null) return;

        int secondsRemaining = remainingTicks / 20;
        int lastNotified = pvpManager.lastNotificationTime.getOrDefault(battleId, -1);

        if (secondsRemaining != lastNotified && (secondsRemaining % 60 == 0 || secondsRemaining <= 10)) {
            MutableComponent message;
            if (secondsRemaining > 60) {
                int minutes = secondsRemaining / 60;
                message = Component.literal("Battle time remaining: " + minutes + " minute" + (minutes > 1 ? "s" : "")).withStyle(ChatFormatting.YELLOW);
            } else if (secondsRemaining > 10) {
                message = Component.literal("Battle time remaining: " + secondsRemaining + " seconds").withStyle(ChatFormatting.YELLOW);
            } else {
                message = Component.literal("Battle time remaining: " + secondsRemaining + "!").withStyle(ChatFormatting.GOLD);
            }

            for (UUID playerId : battle.getAllPlayers()) {
                ServerPlayer player = getPlayerByUUID(playerId);
                if (player != null) {
                    player.sendSystemMessage(message);
                }
            }
            pvpManager.lastNotificationTime.put(battleId, secondsRemaining);
        }
    }

    public void handlePlayerDisconnect(ServerPlayer player) {
        UUID playerId = player.getUUID();
        
        pvpManager.pendingRequests.values().removeIf(request ->
                request.getChallengerId().equals(playerId) || request.getTargetPlayers().contains(playerId));

        // Clean up defeated player tracking
        pvpManager.defeatedPlayers.remove(playerId);
        
        ActiveBattle battle = pvpManager.getActiveBattle(player);
        if (battle != null) {
            cancelBattleDueToDisconnect(battle, player);
            return;
        }

        SpectatorData specData = pvpManager.spectatorData.get(playerId);
        if (specData != null) {
            player.setGameMode(specData.originalGameMode());
            pvpManager.spectatorData.remove(playerId);
            LOGGER.info("Player {} disconnected as a spectator, resetting gamemode to {}.", player.getName().getString(), specData.originalGameMode());
        }
    }
    
    public int handleAccept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BattleRequest request = pvpManager.pendingRequests.remove(player.getUUID());

        if (request == null) {
            context.getSource().sendFailure(Component.literal("No active battle requests!"));
            return 0;
        }

        List<UUID> allTargets = new ArrayList<>(request.getTargetPlayers());
        boolean allAccepted = allTargets.stream().noneMatch(pvpManager.pendingRequests::containsKey);

        if (allAccepted) {
            startChallengedBattle(request);
        } else {
            player.sendSystemMessage(Component.literal("Waiting for other players to accept...").withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }

    public int handleDecline(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BattleRequest request = pvpManager.pendingRequests.remove(player.getUUID());

        if (request == null) {
            context.getSource().sendFailure(Component.literal("No active battle requests!"));
            return 0;
        }

        for (UUID targetId : request.getTargetPlayers()) {
            pvpManager.pendingRequests.remove(targetId);
        }

        ServerPlayer challenger = getPlayerByUUID(request.getChallengerId());
        if (challenger != null) {
            challenger.sendSystemMessage(Component.literal(player.getName().getString() + " declined the battle! Challenge cancelled.").withStyle(ChatFormatting.RED));
        }
        return 1;
    }
    
    public int handleSpectateStart(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer spectator = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");

        ActiveBattle battle = pvpManager.getActiveBattle(target);
        if (battle == null) {
            context.getSource().sendFailure(Component.literal("Player is not in a battle!"));
            return 0;
        }

        PvPMap map = pvpManager.arenaMapsByName.get(battle.getMapName());
        if (map == null) {
            context.getSource().sendFailure(Component.literal("Battle map not found!"));
            return 0;
        }

        GlobalPos originalPos = GlobalPos.of(spectator.level().dimension(), spectator.blockPosition());
        pvpManager.spectatorData.put(spectator.getUUID(), new SpectatorData(originalPos, spectator.gameMode.getGameModeForPlayer()));

        if (!map.getSpawnPoints().isEmpty()) {
            BlockPos centerPos = calculateMapCenter(map);
            GlobalPos spectatorPos = GlobalPos.of(map.getDimension(), centerPos.offset(0, 10, 0));

            spectator.setGameMode(GameType.SPECTATOR);
            teleportTo(spectator, spectatorPos);

            pvpManager.activeSpectators.computeIfAbsent(battle.getBattleId(), k -> new ArrayList<>()).add(spectator.getUUID());

            spectator.sendSystemMessage(Component.literal("Now spectating battle on " + battle.getMapName())
                    .withStyle(ChatFormatting.GREEN)
                    .append("\n")
                    .append(createStopButton()));
        }
        return 1;
    }
    
    public int handleSpectateStop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer spectator = context.getSource().getPlayerOrException();
        stopSpectating(spectator, true);
        return 1;
    }

    public int handleDuel(CommandContext<CommandSourceStack> context, boolean hasAmount, boolean hasMap) throws CommandSyntaxException {
        ServerPlayer challenger = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "targetPlayer");
        int amount = hasAmount ? IntegerArgumentType.getInteger(context, "wager") : 0;
        String mapName;

        if (hasMap) {
            mapName = StringArgumentType.getString(context, "mapName");
            if (!pvpManager.arenaMapsByName.containsKey(mapName)) {
                challenger.sendSystemMessage(Component.literal("Arena map '" + mapName + "' does not exist!").withStyle(ChatFormatting.RED));
                return 0;
            }
        } else {
            if (pvpManager.defaultMapName != null && pvpManager.arenaMapsByName.containsKey(pvpManager.defaultMapName)) {
                mapName = pvpManager.defaultMapName;
            } else if (!pvpManager.arenaMapsByName.isEmpty()) {
                mapName = pvpManager.arenaMapsByName.keySet().iterator().next();
            } else {
                challenger.sendSystemMessage(Component.literal("No arena map available! Contact an admin.").withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        if (challenger.getUUID().equals(target.getUUID())) {
            challenger.sendSystemMessage(Component.literal("You can't challenge yourself!").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (pvpManager.isPlayerBusy(challenger.getUUID()) || pvpManager.isPlayerBusy(target.getUUID())) {
            challenger.sendSystemMessage(Component.literal("One of the players is already in a battle or has a pending request!").withStyle(ChatFormatting.RED));
            return 0;
        }

        BattleRequest request = new BattleRequest(challenger.getUUID(), List.of(target.getUUID()), amount, mapName);
        pvpManager.pendingRequests.put(target.getUUID(), request);

        challenger.sendSystemMessage(Component.literal("Duel request sent to " + target.getName().getString() +
                (amount > 0 ? " for " + amount + " coins" : "")).withStyle(ChatFormatting.GREEN));

        MutableComponent targetMessage = Component.literal(challenger.getName().getString() + " has challenged you to a duel" +
                (amount > 0 ? " for " + amount + " coins" : "") + "! ");
        targetMessage.append(Component.literal("[ACCEPT]")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to accept the duel")))));
        targetMessage.append(" ");
        targetMessage.append(Component.literal("[DECLINE]")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD, ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp decline"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to decline the duel")))));

        target.sendSystemMessage(targetMessage);

        return 1;
    }
    
    public int createTeamBattle(CommandContext<CommandSourceStack> context, String mapName) throws CommandSyntaxException {
        ServerPlayer organizer = context.getSource().getPlayerOrException();
        UUID organizerId = organizer.getUUID();

        if (pvpManager.isPlayerBusy(organizerId)) {
            organizer.sendSystemMessage(Component.literal("You are currently busy and cannot start a battle.").withStyle(ChatFormatting.RED));
            return 0;
        }

        Long lastBattleTime = pvpManager.teamBattleCooldown.get(organizerId);
        long cooldownMillis = TaxConfig.TEAM_BATTLE_COOLDOWN_SECONDS.get() * 1000L;
        if (lastBattleTime != null && System.currentTimeMillis() - lastBattleTime < cooldownMillis) {
            long remaining = (lastBattleTime + cooldownMillis) - System.currentTimeMillis();
            organizer.sendSystemMessage(Component.literal("You must wait " + (remaining / 1000) + " seconds before starting another team battle.").withStyle(ChatFormatting.RED));
            return 0;
        }

        PvPMap map = pvpManager.arenaMapsByName.get(mapName);
        if (map == null) {
            organizer.sendSystemMessage(Component.literal("The map '" + mapName + "' does not exist.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (map.getMaxPlayers() < 2) {
            organizer.sendSystemMessage(Component.literal("This map is not large enough for a team battle.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!isMapAvailable(mapName)) {
            organizer.sendSystemMessage(Component.literal("The map '" + mapName + "' is currently in use.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String battleId = "team_battle_" + UUID.randomUUID().toString().substring(0, 8);
        int maxTeamSize = map.getMaxPlayers() / 2;

        TeamBattle teamBattle = new TeamBattle(battleId, mapName, organizer.getUUID(), maxTeamSize);
        pvpManager.pendingTeamBattles.put(battleId, teamBattle);
        pvpManager.teamBattleCooldown.put(organizerId, System.currentTimeMillis());

        teamBattle.addPlayerToTeam(organizerId, 1);

        MutableComponent message = Component.literal(organizer.getName().getString() + " has started a team battle on map " + mapName + "!")
                .withStyle(ChatFormatting.GOLD);
        message.append(Component.literal("\n[Join Team 1]").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teampvp join " + battleId + " 1"))));
        message.append(Component.literal(" - "));
        message.append(Component.literal("[Join Team 2]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teampvp join " + battleId + " 2"))));

        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(message, false);

        MutableComponent startMessage = Component.literal("You have created a team battle! You can start it early with: ")
                .withStyle(ChatFormatting.GREEN);
        startMessage.append(Component.literal("[START EARLY]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teampvp start " + battleId))));
        organizer.sendSystemMessage(startMessage);

        displayTeamRosters(teamBattle);
        return 1;
    }

    public int joinTeamBattle(CommandContext<CommandSourceStack> context, String battleId, int teamNumber) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TeamBattle teamBattle = pvpManager.pendingTeamBattles.get(battleId);
        if (teamBattle == null || teamBattle.getState() == TeamBattleState.IN_PROGRESS) {
            player.sendSystemMessage(Component.literal("You can no longer join this battle.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (pvpManager.isPlayerBusy(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You are already in a battle or have a pending request!").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (teamBattle.addPlayerToTeam(player.getUUID(), teamNumber)) {
            displayTeamRosters(teamBattle);
            if (teamBattle.canStart() && teamBattle.getState() == TeamBattleState.RECRUITING) {
                teamBattle.startCountdown();
                int countdownSeconds = TaxConfig.TEAM_BATTLE_START_COUNTDOWN_SECONDS.get();
                notifyTeamBattlePlayers(teamBattle, Component.literal("The battle will begin in " + countdownSeconds + " seconds!").withStyle(ChatFormatting.YELLOW));
            }
        } else {
            player.sendSystemMessage(Component.literal("Failed to join team. It might be full, or you're already on a team.").withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }
    
    public int switchTeam(CommandContext<CommandSourceStack> context, String battleId, int newTeamNumber) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID playerId = player.getUUID();
        TeamBattle teamBattle = pvpManager.pendingTeamBattles.get(battleId);

        if (teamBattle == null || teamBattle.getState() == TeamBattleState.IN_PROGRESS) {
            player.sendSystemMessage(Component.literal("You cannot switch teams at this time.").withStyle(ChatFormatting.RED));
            return 0;
        }

        int currentTeam = 0;
        if (teamBattle.getTeam1().contains(playerId)) currentTeam = 1;
        else if (teamBattle.getTeam2().contains(playerId)) currentTeam = 2;

        if (currentTeam == newTeamNumber) {
            player.sendSystemMessage(Component.literal("You are already on that team.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        if ((newTeamNumber == 1 && teamBattle.getTeam1().size() >= teamBattle.getMaxTeamSize()) ||
                (newTeamNumber == 2 && teamBattle.getTeam2().size() >= teamBattle.getMaxTeamSize())) {
            player.sendSystemMessage(Component.literal("The target team is full.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (currentTeam != 0) {
            teamBattle.removePlayer(playerId);
        }

        if (teamBattle.addPlayerToTeam(playerId, newTeamNumber)) {
            displayTeamRosters(teamBattle);
        } else {
            player.sendSystemMessage(Component.literal("Failed to switch teams.").withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    public int startPendingTeamBattle(CommandContext<CommandSourceStack> context, String battleId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TeamBattle teamBattle = pvpManager.pendingTeamBattles.get(battleId);

        if (teamBattle == null) {
            player.sendSystemMessage(Component.literal("Team battle not found.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!teamBattle.getOrganizer().equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Only the organizer can start the battle.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (teamBattle.getState() == TeamBattleState.IN_PROGRESS) {
            player.sendSystemMessage(Component.literal("This battle is already in progress.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!teamBattle.canStart()) {
            player.sendSystemMessage(Component.literal("Both teams must have at least one player to start.").withStyle(ChatFormatting.RED));
            return 0;
        }

        teamBattle.setState(TeamBattleState.IN_PROGRESS);
        startTeamBattle(teamBattle);
        return 1;
    }

    private void startChallengedBattle(BattleRequest request) {
        List<UUID> allPlayers = new ArrayList<>();
        allPlayers.add(request.getChallengerId());
        allPlayers.addAll(request.getTargetPlayers());

        PvPMap map = pvpManager.arenaMapsByName.get(request.getMapName());
        if (map == null || map.getSpawnPoints().size() < allPlayers.size()) {
            allPlayers.forEach(id -> {
                ServerPlayer p = getPlayerByUUID(id);
                if (p != null) {
                    p.sendSystemMessage(Component.literal("Not enough spawn points available on map '" + request.getMapName() + "'! Battle cancelled.").withStyle(ChatFormatting.RED));
                }
            });
            return;
        }

        List<List<UUID>> teams = new ArrayList<>();
        if (allPlayers.size() == 2) {
            teams.add(Arrays.asList(allPlayers.get(0)));
            teams.add(Arrays.asList(allPlayers.get(1)));
        } else {
            for (UUID playerId : allPlayers) {
                teams.add(Arrays.asList(playerId));
            }
        }

        Map<UUID, GlobalPos> originalPositions = new HashMap<>();
        for (UUID playerId : allPlayers) {
            ServerPlayer player = getPlayerByUUID(playerId);
            if (player != null) {
                originalPositions.put(playerId, GlobalPos.of(player.level().dimension(), player.blockPosition()));
            }
        }

        String battleId = "challenge_" + System.currentTimeMillis();
        ActiveBattle battle = new ActiveBattle(battleId, teams, map.getSpawnPoints(), request.getMapName());
        pvpManager.activeBattles.put(battleId, battle);

        for (Map.Entry<UUID, GlobalPos> entry : originalPositions.entrySet()) {
            battle.getOriginalPositions().put(entry.getKey(), entry.getValue());
        }

        startBattle(battle);
    }
    
    private void checkForBattleEnd(ActiveBattle battle) {
        // Count remaining players per team
        Map<Integer, Integer> teamPlayerCounts = new HashMap<>();
        for (int teamIndex = 0; teamIndex < battle.getTeams().size(); teamIndex++) {
            int aliveCount = 0;
            for (UUID playerId : battle.getTeams().get(teamIndex)) {
                ServerPlayer player = getPlayerByUUID(playerId);
                if (player != null && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    aliveCount++;
                }
            }
            teamPlayerCounts.put(teamIndex, aliveCount);
        }
        
        // Check if any team is completely eliminated
        boolean anyTeamEliminated = teamPlayerCounts.values().stream().anyMatch(count -> count == 0);
        
        if (anyTeamEliminated) {
            // Find the team that still has players
            int winningTeam = teamPlayerCounts.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(-1);
            
            if (winningTeam >= 0) {
                endBattleWithWinner(battle, winningTeam, "Victory - enemy team eliminated!");
            } else {
                // This shouldn't happen, but handle it just in case
                endBattleAsDraw(battle, "All combatants were eliminated!");
            }
        }
        // If no team is completely eliminated, let the timer handle the end
    }

    private void endBattleWithWinner(ActiveBattle battle, int winningTeam, String reason) {
        List<UUID> winners = battle.getTeams().get(winningTeam);
        boolean isDuel = battle.getTeams().size() == 2 && battle.getTeams().get(0).size() == 1 && battle.getTeams().get(1).size() == 1;

        for (int i = 0; i < battle.getTeams().size(); i++) {
            boolean isWinningTeam = (i == winningTeam);
            for (UUID playerId : battle.getTeams().get(i)) {
                updatePlayerStats(playerId, isWinningTeam, false, !isDuel);
                ServerPlayer player = getPlayerByUUID(playerId);
                if (player != null) {
                    MutableComponent message = isWinningTeam ?
                            Component.literal("✧ Victory! ✧").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD) :
                            Component.literal("✧ Defeat! ✧").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                    player.sendSystemMessage(message);
                }
            }
        }
        processBattleRewards(battle, winners);
        scheduleBattleEnd(battle.getBattleId());
    }

    private void endBattleAsDraw(ActiveBattle battle, String reason) {
        boolean isDuel = battle.getTeams().size() == 2 && battle.getTeams().get(0).size() == 1 && battle.getTeams().get(1).size() == 1;
        for (List<UUID> team : battle.getTeams()) {
            for (UUID playerId : team) {
                updatePlayerStats(playerId, false, false, !isDuel);
            }
        }
        String resultTitle = isDuel ? "✧ Duel Result ✧" : "✧ Battle Result ✧";
        MutableComponent announcement = Component.literal(resultTitle + "\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(reason + " - DRAW!").withStyle(ChatFormatting.YELLOW));
        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(announcement, false);
        scheduleBattleEnd(battle.getBattleId());
    }

    private void scheduleBattleEnd(String battleId) {
        ActiveBattle battle = pvpManager.activeBattles.get(battleId);
        if (battle == null) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        int countdownSeconds = TaxConfig.BATTLE_END_COUNTDOWN_SECONDS.get();
        server.getPlayerList().broadcastSystemMessage(Component.literal("Returning to original positions in " + countdownSeconds + " seconds...").withStyle(ChatFormatting.YELLOW), false);
        PvPManager.BATTLE_END_SCHEDULER.schedule(() -> server.execute(() -> endBattle(battleId)), countdownSeconds, TimeUnit.SECONDS);
    }
    
    private void endBattle(String battleId) {
        ActiveBattle battle = pvpManager.activeBattles.remove(battleId);
        if (battle == null) return;

        unlockMap(battle.getMapName());
        pvpManager.battleTimers.remove(battleId);
        pvpManager.lastNotificationTime.remove(battleId);
        pvpManager.battleDamage.remove(battleId);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (UUID playerId : battle.getAllPlayers()) {
            // Skip players who are already defeated and being restored individually
            if (pvpManager.defeatedPlayers.containsKey(playerId)) {
                continue;
            }
            
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    GlobalPos originalPos = battle.getOriginalPositions().get(playerId);
                    GameType originalGameMode = pvpManager.playerOriginalGameModes.getOrDefault(playerId, GameType.SURVIVAL);
                    restorePlayer(player, originalPos, originalGameMode);
                    pvpManager.playerOriginalGameModes.remove(playerId);
                }
            });
        }
    }
    
    private void startBattle(ActiveBattle battle) {
        pvpManager.activeBattles.put(battle.getBattleId(), battle);
        lockMap(battle.getMapName());

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        int numTeams = battle.getTeams().size();
        for (int teamIndex = 0; teamIndex < numTeams; teamIndex++) {
            List<UUID> team = battle.getTeams().get(teamIndex);
            for (int playerIndex = 0; playerIndex < team.size(); playerIndex++) {
                UUID playerId = team.get(playerIndex);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    int spawnIndex = teamIndex + (playerIndex * numTeams);
                    if (spawnIndex >= battle.getSpawnPositions().size()) {
                        LOGGER.error("Not enough spawn points for battle {}. This should have been checked earlier.", battle.getBattleId());
                        // Early exit to prevent crash
                        return;
                    }
                    GlobalPos spawnPos = battle.getSpawnPositions().get(spawnIndex);
                    
                    // Validate spawn position
                    if (spawnPos == null) {
                        LOGGER.error("Spawn position at index {} is null for battle {}. Skipping player {}.", 
                                   spawnIndex, battle.getBattleId(), player.getName().getString());
                        continue;
                    }

                    battle.getOriginalPositions().put(playerId, GlobalPos.of(player.level().dimension(), player.blockPosition()));
                    pvpManager.playerOriginalGameModes.put(playerId, player.gameMode.getGameModeForPlayer());
                    // REMOVED: saveInventory(player); - Caused duplication glitch when items were moved to containers
                    teleportTo(player, spawnPos);
                    applyFreezeEffects(player);
                }
            }
        }
        int battleDurationTicks = TaxConfig.BATTLE_DURATION_SECONDS.get() * 20;
        pvpManager.battleTimers.put(battle.getBattleId(), battleDurationTicks);
        LOGGER.info("Battle {} started with {} seconds duration ({} ticks)", 
                   battle.getBattleId(), TaxConfig.BATTLE_DURATION_SECONDS.get(), battleDurationTicks);
        startBattleCountdown(battle);
    }

    private void startBattleCountdown(ActiveBattle battle) {
        new Thread(() -> {
            try {
                for (int i = 5; i > 0; i--) {
                    int finalI = i;
                    ServerLifecycleHooks.getCurrentServer().execute(() -> {
                        MutableComponent countdown = Component.literal("Battle starts in: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(finalI)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                        for (UUID playerId : battle.getAllPlayers()) {
                            ServerPlayer player = getPlayerByUUID(playerId);
                            if (player != null) player.sendSystemMessage(countdown);
                        }
                    });
                    Thread.sleep(1000);
                }
                ServerLifecycleHooks.getCurrentServer().execute(() -> {
                    MutableComponent fightMessage = Component.literal("FIGHT!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                    for (UUID playerId : battle.getAllPlayers()) {
                        ServerPlayer player = getPlayerByUUID(playerId);
                        if (player != null) {
                            player.sendSystemMessage(fightMessage);
                            removeFreezeEffects(player);
                        }
                    }
                });
            } catch (InterruptedException e) {
                LOGGER.error("Countdown error", e);
            }
        }).start();
    }
    
    private void cancelBattleDueToDisconnect(ActiveBattle battle, ServerPlayer disconnectedPlayer) {
        String battleId = battle.getBattleId();
        for (UUID playerId : battle.getAllPlayers()) {
            if (!playerId.equals(disconnectedPlayer.getUUID())) {
                ServerPlayer player = getPlayerByUUID(playerId);
                if (player != null) {
                    player.sendSystemMessage(Component.literal(disconnectedPlayer.getName().getString() + " disconnected. Battle cancelled - no penalties.").withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        endBattle(battleId);
    }

    private void updatePlayerStats(UUID playerId, boolean won, boolean died, boolean isTeamBattle) {
        PlayerPvPStats stats = pvpManager.playerStats.computeIfAbsent(playerId, k -> new PlayerPvPStats());
        if (won) stats.addWin(isTeamBattle);
        else stats.addLoss(isTeamBattle);
        if (died) stats.addDeath();
    }

    private void displayTeamRosters(TeamBattle teamBattle) {
        MutableComponent roster = Component.literal("--- Team Battle Rosters ---\n").withStyle(ChatFormatting.GOLD);
        roster.append(Component.literal("Team 1: ").withStyle(ChatFormatting.AQUA));
        appendTeamMembers(roster, teamBattle.getTeam1());
        roster.append(Component.literal("\nTeam 2: ").withStyle(ChatFormatting.RED));
        appendTeamMembers(roster, teamBattle.getTeam2());
        notifyTeamBattlePlayers(teamBattle, roster);
    }
    
    private void appendTeamMembers(MutableComponent component, List<UUID> team) {
        if (team.isEmpty()) {
            component.append(Component.literal("(empty)").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (int i = 0; i < team.size(); i++) {
            UUID memberId = team.get(i);
            ServerPlayer member = getPlayerByUUID(memberId);
            String name = member != null ? member.getName().getString() : "Offline Player";
            component.append(Component.literal(name).withStyle(ChatFormatting.WHITE));
            if (i < team.size() - 1) component.append(", ");
        }
    }
    
    public void notifyTeamBattlePlayers(TeamBattle teamBattle, Component message) {
        List<UUID> allPlayers = new ArrayList<>();
        allPlayers.addAll(teamBattle.getTeam1());
        allPlayers.addAll(teamBattle.getTeam2());
        for (UUID playerId : allPlayers) {
            ServerPlayer player = getPlayerByUUID(playerId);
            if (player != null) player.sendSystemMessage(message);
        }
    }
    
    private ServerPlayer getPlayerByUUID(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(uuid);
    }

    private BlockPos calculateMapCenter(PvPMap map) {
        if (map.getSpawnPoints().isEmpty()) return BlockPos.ZERO;
        int totalX = 0, totalY = 0, totalZ = 0;
        int count = 0;
        for (GlobalPos pos : map.getSpawnPoints()) {
            if (pos != null) {
                totalX += pos.pos().getX();
                totalY += pos.pos().getY();
                totalZ += pos.pos().getZ();
                count++;
            }
        }
        return count == 0 ? BlockPos.ZERO : new BlockPos(totalX / count, totalY / count, totalZ / count);
    }

    private void teleportTo(ServerPlayer player, GlobalPos pos) {
        // Add null checks to prevent crash
        if (player == null || pos == null) {
            LOGGER.error("Cannot teleport: player or position is null. Player: {}, Pos: {}", player, pos);
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;
        
        try {
            ServerLevel level = server.getLevel(pos.dimension());
            if (level == null) return;
            BlockPos blockPos = pos.pos();
            player.teleportTo(level, blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5, player.getYRot(), player.getXRot());
        } catch (Exception e) {
            LOGGER.error("Error teleporting player {} to position {}: {}", player.getName().getString(), pos, e.getMessage());
        }
    }

    private MutableComponent createStopButton() {
        return Component.literal("[Stop Spectating]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp spectate stop"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to stop spectating"))));
    }

    private void stopSpectating(ServerPlayer spectator, boolean sendMessage) {
        SpectatorData data = pvpManager.spectatorData.remove(spectator.getUUID());
        if(data != null) {
            spectator.setGameMode(data.originalGameMode());
            teleportTo(spectator, data.originalPos());
            pvpManager.activeSpectators.values().forEach(list -> list.remove(spectator.getUUID()));
            if(sendMessage) {
                spectator.sendSystemMessage(Component.literal("Stopped spectating").withStyle(ChatFormatting.GREEN));
            }
        }
    }
    
    private void restorePlayer(ServerPlayer player, GlobalPos originalPos, GameType originalGameMode) {
        if (player == null) return;
        if (originalPos != null) {
            teleportTo(player, originalPos);
        }
        // REMOVED: restoreInventory(player); - Players keep their actual inventory (fixes duplication glitch)
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.clearFire();
        player.setGameMode(originalGameMode);
        removeFreezeEffects(player);
        player.removeEffect(MobEffects.GLOWING);
    }

    // DEPRECATED: Inventory save/restore system removed to fix duplication glitch
    // Players now keep their actual inventory throughout the match
    @Deprecated
    @SuppressWarnings("unused")
    private void saveInventory(ServerPlayer player) {
        // NO-OP: This method is no longer used
        // Keeping for compatibility but functionality removed
    }

    // DEPRECATED: Inventory save/restore system removed to fix duplication glitch
    // Players now keep their actual inventory throughout the match
    @Deprecated
    @SuppressWarnings("unused")
    private void restoreInventory(ServerPlayer player) {
        // NO-OP: This method is no longer used
        // Clean up any legacy data that might exist
        UUID uuid = player.getUUID();
        pvpManager.playerInventories.remove(uuid);
        pvpManager.playerArmor.remove(uuid);
    }
    
    private void applyFreezeEffects(ServerPlayer player) {
        // Apply maximum freeze effects that last longer
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6000, 255, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, 6000, 250, false, false, false));
        // Add blindness to prevent seeing and confusion to prevent movement
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 6000, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 6000, 0, false, false, false));
    }

    private void removeFreezeEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.JUMP);
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.CONFUSION);
    }
    
    private void lockMap(String mapName) {
        pvpManager.lockedMaps.add(mapName);
        LOGGER.info("Locked map: " + mapName);
    }

    private void unlockMap(String mapName) {
        pvpManager.lockedMaps.remove(mapName);
        LOGGER.info("Unlocked map: " + mapName);
    }

    private boolean isMapAvailable(String mapName) {
        return !pvpManager.lockedMaps.contains(mapName);
    }

    private void updatePlayerKill(UUID killerId) {
        PlayerPvPStats stats = pvpManager.playerStats.computeIfAbsent(killerId, k -> new PlayerPvPStats());
        stats.addKill();
        // Rewards can be handled here later
    }

    private void processBattleRewards(ActiveBattle battle, List<UUID> winners) {
        // Rewards can be handled here later
    }
}