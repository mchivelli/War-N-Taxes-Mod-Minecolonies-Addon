package net.machiavelli.minecolonytax.pvp;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.integration.CurrencyService;
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
import net.machiavelli.minecolonytax.util.TickScheduler;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PvPBattleManager {

    private static final Logger LOGGER = LogManager.getLogger();
    private final PvPManager pvpManager = PvPManager.INSTANCE;

    public void handlePlayerDefeat(ServerPlayer player, ActiveBattle battle,
            net.minecraft.world.damagesource.DamageSource source) {
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

        player.sendSystemMessage(
                Component.literal("You have been eliminated from the battle!").withStyle(ChatFormatting.RED));
        player.sendSystemMessage(
                Component.literal("You will be restored in 5 seconds...").withStyle(ChatFormatting.YELLOW));

        boolean isDuel = battle.getTeams().size() == 2 && battle.getTeams().get(0).size() == 1
                && battle.getTeams().get(1).size() == 1;
        updatePlayerStats(playerId, false, true, !isDuel);

        // Schedule restoration after 5 seconds via TickScheduler (main server thread).
        // Capture only the UUID so a player who reconnects within the 5 s window
        // is correctly re-looked-up by their fresh ServerPlayer instance.
        final UUID capturedId = playerId;
        TickScheduler.scheduleDelayed(() -> restoreDefeatedPlayer(capturedId, battle), 5_000L);

        checkForBattleEnd(battle);
    }

    private void restoreDefeatedPlayer(UUID playerId, ActiveBattle battle) {
        if (playerId == null) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer player = (server != null) ? server.getPlayerList().getPlayer(playerId) : null;

        // Remove from defeated players tracking regardless — battle has finished defeat
        pvpManager.defeatedPlayers.remove(playerId);

        if (player == null) {
            // Player is offline. Leave persisted gamemode + position in place so the
            // PlayerLoggedIn handler can restore them on next login (fix #4b).
            PvPStatsPersistence.save();
            return;
        }

        // Get original position and game mode
        GlobalPos originalPos = battle.getOriginalPositions().get(playerId);
        GameType originalGameMode = pvpManager.playerOriginalGameModes.getOrDefault(playerId, GameType.SURVIVAL);

        // Restore player to their original state
        restorePlayer(player, originalPos, originalGameMode);

        // Clean up
        pvpManager.playerOriginalGameModes.remove(playerId);
        pvpManager.playerOriginalPositions.remove(playerId);
        PvPStatsPersistence.save();

        player.sendSystemMessage(
                Component.literal("You have been restored to your original position.").withStyle(ChatFormatting.GREEN));
    }

    public void startTeamBattle(TeamBattle teamBattle) {
        if (!teamBattle.canStart()) {
            return;
        }

        String mapName = teamBattle.getMapName();
        PvPMap map = pvpManager.arenaMapsByName.get(mapName);

        if (map == null) {
            notifyTeamBattlePlayers(teamBattle,
                    Component.literal("Map not found! Battle cancelled.").withStyle(ChatFormatting.RED));
            pvpManager.pendingTeamBattles.remove(teamBattle.getBattleId());
            return;
        }

        if (map.getSpawnPoints().size() < teamBattle.getTotalPlayers()) {
            notifyTeamBattlePlayers(teamBattle, Component.literal("Not enough spawn points on map for "
                    + teamBattle.getTotalPlayers() + " players! Battle cancelled.").withStyle(ChatFormatting.RED));
            pvpManager.pendingTeamBattles.remove(teamBattle.getBattleId());
            return;
        }

        // Filter out null spawn points and validate
        List<GlobalPos> validSpawnPoints = map.getSpawnPoints().stream()
                .filter(pos -> pos != null)
                .toList();

        if (validSpawnPoints.size() < teamBattle.getTotalPlayers()) {
            notifyTeamBattlePlayers(teamBattle, Component.literal("Not enough valid spawn points on map for "
                    + teamBattle.getTotalPlayers() + " players! Battle cancelled.").withStyle(ChatFormatting.RED));
            pvpManager.pendingTeamBattles.remove(teamBattle.getBattleId());
            return;
        }

        if (pvpManager.lockedMaps.contains(mapName)) {
            notifyTeamBattlePlayers(teamBattle,
                    Component.literal("Map is currently in use! Battle cancelled.").withStyle(ChatFormatting.RED));
            pvpManager.pendingTeamBattles.remove(teamBattle.getBattleId());
            return;
        }

        lockMap(mapName);
        teamBattle.setState(TeamBattleState.IN_PROGRESS);

        List<List<UUID>> teams = List.of(
                new ArrayList<>(teamBattle.getTeam1()),
                new ArrayList<>(teamBattle.getTeam2()));

        String battleId = "ab_" + System.currentTimeMillis();
        ActiveBattle activeBattle = new ActiveBattle(battleId, teams, validSpawnPoints, mapName);
        pvpManager.activeBattles.put(battleId, activeBattle);
        pvpManager.pendingTeamBattles.remove(teamBattle.getBattleId());
        startBattle(activeBattle);
    }

    public void handleBattleTimerExpiry(String battleId) {
        ActiveBattle battle = pvpManager.activeBattles.get(battleId);
        if (battle == null)
            return;

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
        if (battle == null)
            return;

        int secondsRemaining = remainingTicks / 20;
        int lastNotified = pvpManager.lastNotificationTime.getOrDefault(battleId, -1);

        if (secondsRemaining != lastNotified && (secondsRemaining % 60 == 0 || secondsRemaining <= 10)) {
            MutableComponent message;
            if (secondsRemaining > 60) {
                int minutes = secondsRemaining / 60;
                message = Component.literal("Battle time remaining: " + minutes + " minute" + (minutes > 1 ? "s" : ""))
                        .withStyle(ChatFormatting.YELLOW);
            } else if (secondsRemaining > 10) {
                message = Component.literal("Battle time remaining: " + secondsRemaining + " seconds")
                        .withStyle(ChatFormatting.YELLOW);
            } else {
                message = Component.literal("Battle time remaining: " + secondsRemaining + "!")
                        .withStyle(ChatFormatting.GOLD);
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

        pvpManager.pendingRequests.values().removeIf(
                request -> request.getChallengerId().equals(playerId) || request.getTargetPlayers().contains(playerId));

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
            if (TaxConfig.isDebugLogging()) {
                LOGGER.debug("Player {} disconnected as a spectator, resetting gamemode to {}.",
                        player.getName().getString(), specData.originalGameMode());
            }
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
            player.sendSystemMessage(
                    Component.literal("Waiting for other players to accept...").withStyle(ChatFormatting.YELLOW));
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
            challenger.sendSystemMessage(
                    Component.literal(player.getName().getString() + " declined the battle! Challenge cancelled.")
                            .withStyle(ChatFormatting.RED));
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
        pvpManager.spectatorData.put(spectator.getUUID(),
                new SpectatorData(originalPos, spectator.gameMode.getGameModeForPlayer()));

        if (!map.getSpawnPoints().isEmpty()) {
            BlockPos centerPos = calculateMapCenter(map);
            GlobalPos spectatorPos = GlobalPos.of(map.getDimension(), centerPos.offset(0, 10, 0));

            spectator.setGameMode(GameType.SPECTATOR);
            teleportTo(spectator, spectatorPos);

            pvpManager.activeSpectators.computeIfAbsent(battle.getBattleId(), k -> new ArrayList<>())
                    .add(spectator.getUUID());

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

    public int handleDuel(CommandContext<CommandSourceStack> context, boolean hasAmount, boolean hasMap)
            throws CommandSyntaxException {
        ServerPlayer challenger = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "targetPlayer");
        int amount = hasAmount ? IntegerArgumentType.getInteger(context, "wager") : 0;
        String mapName;

        if (hasMap) {
            mapName = StringArgumentType.getString(context, "mapName");
            if (!pvpManager.arenaMapsByName.containsKey(mapName)) {
                challenger.sendSystemMessage(
                        Component.literal("Arena map '" + mapName + "' does not exist!").withStyle(ChatFormatting.RED));
                return 0;
            }
        } else {
            if (pvpManager.defaultMapName != null
                    && pvpManager.arenaMapsByName.containsKey(pvpManager.defaultMapName)) {
                mapName = pvpManager.defaultMapName;
            } else if (!pvpManager.arenaMapsByName.isEmpty()) {
                mapName = pvpManager.arenaMapsByName.keySet().iterator().next();
            } else {
                challenger.sendSystemMessage(
                        Component.literal("No arena map available! Contact an admin.").withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        if (challenger.getUUID().equals(target.getUUID())) {
            challenger.sendSystemMessage(
                    Component.literal("You can't challenge yourself!").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (pvpManager.isPlayerBusy(challenger.getUUID()) || pvpManager.isPlayerBusy(target.getUUID())) {
            challenger.sendSystemMessage(
                    Component.literal("One of the players is already in a battle or has a pending request!")
                            .withStyle(ChatFormatting.RED));
            return 0;
        }

        BattleRequest request = new BattleRequest(challenger.getUUID(), List.of(target.getUUID()), amount, mapName);
        pvpManager.pendingRequests.put(target.getUUID(), request);

        challenger.sendSystemMessage(Component.literal("Duel request sent to " + target.getName().getString() +
                (amount > 0 ? " for " + amount + " coins" : "")).withStyle(ChatFormatting.GREEN));

        MutableComponent targetMessage = Component
                .literal(challenger.getName().getString() + " has challenged you to a duel" +
                        (amount > 0 ? " for " + amount + " coins" : "") + "! ");
        targetMessage.append(Component.literal("[ACCEPT]")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to accept the duel")))));
        targetMessage.append(" ");
        targetMessage.append(Component.literal("[DECLINE]")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD, ChatFormatting.UNDERLINE)
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp decline"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to decline the duel")))));

        target.sendSystemMessage(targetMessage);

        return 1;
    }

    public int createTeamBattle(CommandContext<CommandSourceStack> context, String mapName)
            throws CommandSyntaxException {
        ServerPlayer organizer = context.getSource().getPlayerOrException();
        UUID organizerId = organizer.getUUID();

        if (pvpManager.isPlayerBusy(organizerId)) {
            organizer.sendSystemMessage(Component.literal("You are currently busy and cannot start a battle.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Long lastBattleTime = pvpManager.teamBattleCooldown.get(organizerId);
        long cooldownMillis = TaxConfig.TEAM_BATTLE_COOLDOWN_SECONDS.get() * 1000L;
        if (lastBattleTime != null && System.currentTimeMillis() - lastBattleTime < cooldownMillis) {
            long remaining = (lastBattleTime + cooldownMillis) - System.currentTimeMillis();
            organizer.sendSystemMessage(Component
                    .literal("You must wait " + (remaining / 1000) + " seconds before starting another team battle.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        PvPMap map = pvpManager.arenaMapsByName.get(mapName);
        if (map == null) {
            organizer.sendSystemMessage(
                    Component.literal("The map '" + mapName + "' does not exist.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (map.getMaxPlayers() < 2) {
            organizer.sendSystemMessage(
                    Component.literal("This map is not large enough for a team battle.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!isMapAvailable(mapName)) {
            organizer.sendSystemMessage(
                    Component.literal("The map '" + mapName + "' is currently in use.").withStyle(ChatFormatting.RED));
            return 0;
        }

        String battleId = "team_battle_" + UUID.randomUUID().toString().substring(0, 8);
        int maxTeamSize = map.getMaxPlayers() / 2;

        TeamBattle teamBattle = new TeamBattle(battleId, mapName, organizer.getUUID(), maxTeamSize);
        pvpManager.pendingTeamBattles.put(battleId, teamBattle);
        pvpManager.teamBattleCooldown.put(organizerId, System.currentTimeMillis());

        teamBattle.addPlayerToTeam(organizerId, 1);

        MutableComponent message = Component
                .literal(organizer.getName().getString() + " has started a team battle on map " + mapName + "!")
                .withStyle(ChatFormatting.GOLD);
        message.append(Component.literal("\n[Join Team 1]").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teampvp join " + battleId + " 1"))));
        message.append(Component.literal(" - "));
        message.append(Component.literal("[Join Team 2]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teampvp join " + battleId + " 2"))));

        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(message, false);

        MutableComponent startMessage = Component
                .literal("You have created a team battle! You can start it early with: ")
                .withStyle(ChatFormatting.GREEN);
        startMessage.append(Component.literal("[START EARLY]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teampvp start " + battleId))));
        organizer.sendSystemMessage(startMessage);

        displayTeamRosters(teamBattle);
        return 1;
    }

    public int joinTeamBattle(CommandContext<CommandSourceStack> context, String battleId, int teamNumber)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TeamBattle teamBattle = pvpManager.pendingTeamBattles.get(battleId);
        if (teamBattle == null || teamBattle.getState() == TeamBattleState.IN_PROGRESS) {
            player.sendSystemMessage(
                    Component.literal("You can no longer join this battle.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (pvpManager.isPlayerBusy(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You are already in a battle or have a pending request!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (teamBattle.addPlayerToTeam(player.getUUID(), teamNumber)) {
            displayTeamRosters(teamBattle);
            if (teamBattle.canStart() && teamBattle.getState() == TeamBattleState.RECRUITING) {
                teamBattle.startCountdown();
                int countdownSeconds = TaxConfig.TEAM_BATTLE_START_COUNTDOWN_SECONDS.get();
                notifyTeamBattlePlayers(teamBattle,
                        Component.literal("The battle will begin in " + countdownSeconds + " seconds!")
                                .withStyle(ChatFormatting.YELLOW));
            }
        } else {
            player.sendSystemMessage(
                    Component.literal("Failed to join team. It might be full, or you're already on a team.")
                            .withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    public int switchTeam(CommandContext<CommandSourceStack> context, String battleId, int newTeamNumber)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        UUID playerId = player.getUUID();
        TeamBattle teamBattle = pvpManager.pendingTeamBattles.get(battleId);

        if (teamBattle == null || teamBattle.getState() == TeamBattleState.IN_PROGRESS) {
            player.sendSystemMessage(
                    Component.literal("You cannot switch teams at this time.").withStyle(ChatFormatting.RED));
            return 0;
        }

        int currentTeam = 0;
        if (teamBattle.getTeam1().contains(playerId))
            currentTeam = 1;
        else if (teamBattle.getTeam2().contains(playerId))
            currentTeam = 2;

        if (currentTeam == newTeamNumber) {
            player.sendSystemMessage(
                    Component.literal("You are already on that team.").withStyle(ChatFormatting.YELLOW));
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

    public int startPendingTeamBattle(CommandContext<CommandSourceStack> context, String battleId)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TeamBattle teamBattle = pvpManager.pendingTeamBattles.get(battleId);

        if (teamBattle == null) {
            player.sendSystemMessage(Component.literal("Team battle not found.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!teamBattle.getOrganizer().equals(player.getUUID())) {
            player.sendSystemMessage(
                    Component.literal("Only the organizer can start the battle.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (teamBattle.getState() == TeamBattleState.IN_PROGRESS) {
            player.sendSystemMessage(
                    Component.literal("This battle is already in progress.").withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!teamBattle.canStart()) {
            player.sendSystemMessage(Component.literal("Both teams must have at least one player to start.")
                    .withStyle(ChatFormatting.RED));
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
                    p.sendSystemMessage(Component.literal("Not enough spawn points available on map '"
                            + request.getMapName() + "'! Battle cancelled.").withStyle(ChatFormatting.RED));
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

        // Escrow the wager from EVERY participant before the battle begins. If any
        // player cannot afford the stake, abort and refund everyone already debited
        // so escrowed coins are never lost. wager == 0 is a free duel (no-op).
        // Resolve the source ONCE up-front so escrow and settlement always agree (fix #5).
        int wager = request.getAmount();
        CurrencyService.Source source = wagerSource();
        if (wager > 0 && !escrowWager(allPlayers, wager, source)) {
            return;
        }

        String battleId = "challenge_" + System.currentTimeMillis();
        ActiveBattle battle = new ActiveBattle(battleId, teams, map.getSpawnPoints(), request.getMapName());
        battle.setWager(wager);
        // Remember the exact source escrow took from so refunds/payouts use the same
        // store, even if WALLET availability flips mid-battle (fix #5).
        battle.setWagerSource(wager > 0 ? source : null);
        pvpManager.activeBattles.put(battleId, battle);

        for (Map.Entry<UUID, GlobalPos> entry : originalPositions.entrySet()) {
            battle.getOriginalPositions().put(entry.getKey(), entry.getValue());
        }

        startBattle(battle);
    }

    /**
     * Returns the currency source used for duel wagers: the player's economy wallet
     * (SDMShop / SDM-Economy) when available, otherwise physical inventory currency.
     * Mirrors {@link CurrencyService#deliverClaimedTaxOrRefund} so wagers use the same
     * money the player sees for taxes.
     */
    private CurrencyService.Source wagerSource() {
        return CurrencyService.isAvailable(CurrencyService.Source.WALLET)
                ? CurrencyService.Source.WALLET
                : CurrencyService.Source.INVENTORY;
    }

    /**
     * Deduct {@code wager} from each participant via {@code source}. If anyone cannot pay,
     * refund all players already debited and abort. Wagers are not colony-bound, so colony
     * is null. The caller resolves the source once so escrow and settlement match (fix #5).
     *
     * @return true if every participant was successfully debited; false (with refunds done) otherwise
     */
    private boolean escrowWager(List<UUID> players, int wager, CurrencyService.Source source) {
        List<UUID> debited = new ArrayList<>();
        for (UUID id : players) {
            ServerPlayer player = getPlayerByUUID(id);
            int taken = (player != null) ? CurrencyService.takeFromPlayer(player, null, wager, source) : 0;
            if (taken < wager) {
                // This player could not pay — refund everyone already debited and abort.
                for (UUID refundId : debited) {
                    refundWager(refundId, wager, source);
                }
                String who = (player != null) ? player.getName().getString() : "A player";
                for (UUID notifyId : players) {
                    ServerPlayer p = getPlayerByUUID(notifyId);
                    if (p != null) {
                        p.sendSystemMessage(Component.literal(who + " cannot afford the "
                                + wager + " wager. Duel cancelled - all stakes refunded.")
                                .withStyle(ChatFormatting.RED));
                    }
                }
                return false;
            }
            debited.add(id);
        }
        return true;
    }

    /**
     * Deliver {@code amount} to an online player via {@code source}, honoring the
     * {@link CurrencyService#giveToPlayer} return value. If the primary source fails to
     * deliver the full amount, retry via the ALTERNATE source (WALLET&lt;-&gt;INVENTORY) so
     * escrowed coins are never silently dropped. Mirrors
     * {@link CurrencyService#deliverClaimedTaxOrRefund}'s {@code given <= 0} handling.
     *
     * @return true only if the full {@code amount} was actually delivered.
     */
    private boolean deliverWagerOrFallback(ServerPlayer player, int amount, CurrencyService.Source source,
            String purpose) {
        if (amount <= 0 || player == null) return false;
        int given = CurrencyService.giveToPlayer(player, null, amount, source);
        if (given >= amount) {
            return true;
        }
        // Primary source could not deliver — try the alternate store so coins are not lost.
        CurrencyService.Source alternate = (source == CurrencyService.Source.WALLET)
                ? CurrencyService.Source.INVENTORY
                : CurrencyService.Source.WALLET;
        LOGGER.error("Wager {} delivery to {} via {} returned {} (< {}); retrying via {}.",
                purpose, player.getName().getString(), CurrencyService.label(source), given, amount,
                CurrencyService.label(alternate));
        int givenAlt = CurrencyService.giveToPlayer(player, null, amount, alternate);
        if (givenAlt >= amount) {
            return true;
        }
        // Both stores failed — only now is the coin truly undeliverable. Log the loss.
        LOGGER.error("LOST {} coins: could not deliver wager {} to {} via {} ({}) or {} ({}).",
                amount, purpose, player.getName().getString(), CurrencyService.label(source), given,
                CurrencyService.label(alternate), givenAlt);
        return false;
    }

    /** Return an escrowed wager to a single player via the same source it was taken from. */
    private void refundWager(UUID playerId, int wager, CurrencyService.Source source) {
        if (wager <= 0) return;
        ServerPlayer player = getPlayerByUUID(playerId);
        if (player == null) {
            // Offline is the COMMON case here, not an edge case: refundAllWagers runs on
            // "draw/cancel/disconnect/abort", and a disconnect is exactly what makes this lookup
            // null. Returning here destroyed the stake silently, and the battle was then marked
            // settled so it could never be recovered. Owe it instead; paid out on next login.
            PendingWagerPayouts.queue(playerId, wager, "refund while offline");
            return;
        }
        // Only confirm the refund to the player when the coins were actually delivered (H#3).
        if (deliverWagerOrFallback(player, wager, source, "refund")) {
            player.sendSystemMessage(Component.literal("Your " + wager + " wager was refunded.")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            PendingWagerPayouts.queue(playerId, wager, "refund delivery failed");
        }
    }

    /** Refund the escrowed wager to every participant of a battle (draw/cancel/disconnect/abort). */
    public void refundAllWagers(ActiveBattle battle) {
        int wager = battle.getWager();
        if (wager <= 0) return;
        // Use the source escrow actually took from, not a freshly-recomputed one, so a
        // mid-battle WALLET availability flip can't refund from the wrong store (fix #5).
        CurrencyService.Source source = resolveBattleSource(battle);
        for (UUID playerId : battle.getAllPlayers()) {
            refundWager(playerId, wager, source);
        }
        battle.setWager(0); // mark settled so we never double-refund
    }

    /**
     * Resolve the currency source a battle's wager was escrowed from. Falls back to the
     * current {@link #wagerSource()} only for legacy battles created before the source was
     * stored (defensive; new battles always set it at escrow time).
     */
    private CurrencyService.Source resolveBattleSource(ActiveBattle battle) {
        Object stored = battle.getWagerSource();
        return (stored instanceof CurrencyService.Source s) ? s : wagerSource();
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
        boolean isDuel = battle.getTeams().size() == 2 && battle.getTeams().get(0).size() == 1
                && battle.getTeams().get(1).size() == 1;

        for (int i = 0; i < battle.getTeams().size(); i++) {
            boolean isWinningTeam = (i == winningTeam);
            for (UUID playerId : battle.getTeams().get(i)) {
                updatePlayerStats(playerId, isWinningTeam, false, !isDuel);
                ServerPlayer player = getPlayerByUUID(playerId);
                if (player != null) {
                    MutableComponent message = isWinningTeam
                            ? Component.literal("✧ Victory! ✧").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            : Component.literal("✧ Defeat! ✧").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                    player.sendSystemMessage(message);
                }
            }
        }
        processBattleRewards(battle, winners);
        scheduleBattleEnd(battle.getBattleId());
    }

    private void endBattleAsDraw(ActiveBattle battle, String reason) {
        // No winner: return every escrowed stake so wagered coins are never lost.
        refundAllWagers(battle);
        boolean isDuel = battle.getTeams().size() == 2 && battle.getTeams().get(0).size() == 1
                && battle.getTeams().get(1).size() == 1;
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
        if (battle == null)
            return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
        int countdownSeconds = TaxConfig.BATTLE_END_COUNTDOWN_SECONDS.get();
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("Returning to original positions in " + countdownSeconds + " seconds...")
                        .withStyle(ChatFormatting.YELLOW),
                false);
        // Use TickScheduler (main server thread) instead of forbidden ScheduledExecutorService.
        TickScheduler.scheduleDelayed(() -> endBattle(battleId), countdownSeconds * 1000L);
    }

    private void endBattle(String battleId) {
        ActiveBattle battle = pvpManager.activeBattles.remove(battleId);
        if (battle == null)
            return;

        unlockMap(battle.getMapName());
        pvpManager.battleTimers.remove(battleId);
        pvpManager.lastNotificationTime.remove(battleId);
        pvpManager.battleDamage.remove(battleId);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;

        for (UUID playerId : battle.getAllPlayers()) {
            // Skip players who are already defeated and being restored individually
            if (pvpManager.defeatedPlayers.containsKey(playerId)) {
                continue;
            }

            // TickScheduler already runs us on the main server thread; no nested server.execute.
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                GlobalPos originalPos = battle.getOriginalPositions().get(playerId);
                GameType originalGameMode = pvpManager.playerOriginalGameModes.getOrDefault(playerId,
                        GameType.SURVIVAL);
                restorePlayer(player, originalPos, originalGameMode);
                pvpManager.playerOriginalGameModes.remove(playerId);
                pvpManager.playerOriginalPositions.remove(playerId);
            }
            // If player is offline, leave entries in place so PlayerLoggedInEvent
            // can restore them on next login (fix #4b).
        }

        // M18: restore every spectator for this battle so none is stranded in
        // SPECTATOR mode at the arena after the fight ends. stopSpectating restores
        // their gamemode and teleports them back to their original position.
        List<UUID> spectators = pvpManager.activeSpectators.remove(battleId);
        if (spectators != null) {
            for (UUID spectatorId : new ArrayList<>(spectators)) {
                ServerPlayer spectator = server.getPlayerList().getPlayer(spectatorId);
                if (spectator != null) {
                    stopSpectating(spectator, true);
                }
                // Offline spectator: their persisted SpectatorData is restored on next
                // login by the disconnect/login handler, so nothing to do here.
            }
        }

        PvPStatsPersistence.save();
    }

    private void startBattle(ActiveBattle battle) {
        pvpManager.activeBattles.put(battle.getBattleId(), battle);
        lockMap(battle.getMapName());

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;

        int numTeams = battle.getTeams().size();
        for (int teamIndex = 0; teamIndex < numTeams; teamIndex++) {
            List<UUID> team = battle.getTeams().get(teamIndex);
            for (int playerIndex = 0; playerIndex < team.size(); playerIndex++) {
                UUID playerId = team.get(playerIndex);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    int spawnIndex = teamIndex + (playerIndex * numTeams);
                    if (spawnIndex >= battle.getSpawnPositions().size()) {
                        LOGGER.error("Not enough spawn points for battle {}. This should have been checked earlier.",
                                battle.getBattleId());
                        // Abort cleanly: refund any escrowed wager, free the map, and drop the
                        // half-started battle so escrowed coins are never stranded.
                        abortStartedBattle(battle, "Not enough spawn points - battle cancelled, stakes refunded.");
                        return;
                    }
                    GlobalPos spawnPos = battle.getSpawnPositions().get(spawnIndex);

                    // Validate spawn position
                    if (spawnPos == null) {
                        LOGGER.error("Spawn position at index {} is null for battle {}. Skipping player {}.",
                                spawnIndex, battle.getBattleId(), player.getName().getString());
                        continue;
                    }

                    GlobalPos origPos = GlobalPos.of(player.level().dimension(), player.blockPosition());
                    battle.getOriginalPositions().put(playerId, origPos);
                    pvpManager.playerOriginalGameModes.put(playerId, player.gameMode.getGameModeForPlayer());
                    pvpManager.playerOriginalPositions.put(playerId, origPos);
                    // REMOVED: saveInventory(player); - Caused duplication glitch when items were
                    // moved to containers
                    teleportTo(player, spawnPos);
                    applyFreezeEffects(player);
                }
            }
        }
        int battleDurationTicks = TaxConfig.BATTLE_DURATION_SECONDS.get() * 20;
        pvpManager.battleTimers.put(battle.getBattleId(), battleDurationTicks);
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Battle {} started with {} seconds duration ({} ticks)",
                    battle.getBattleId(), TaxConfig.BATTLE_DURATION_SECONDS.get(), battleDurationTicks);
        }
        startBattleCountdown(battle);
    }

    /**
     * Tear down a battle that failed during {@link #startBattle} (e.g. spawn-point
     * shortage) before it could properly run. Refunds escrowed wagers, frees the map,
     * removes battle tracking, and notifies any online participants.
     */
    private void abortStartedBattle(ActiveBattle battle, String reason) {
        refundAllWagers(battle);
        pvpManager.activeBattles.remove(battle.getBattleId());
        pvpManager.battleTimers.remove(battle.getBattleId());
        pvpManager.lastNotificationTime.remove(battle.getBattleId());
        pvpManager.battleDamage.remove(battle.getBattleId());
        unlockMap(battle.getMapName());
        for (UUID playerId : battle.getAllPlayers()) {
            ServerPlayer player = getPlayerByUUID(playerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.RED));
            }
        }
    }

    private void startBattleCountdown(ActiveBattle battle) {
        // Schedule countdown ticks 5..1 at 1-second intervals, then FIGHT at 5 seconds
        for (int i = 5; i > 0; i--) {
            final int count = i;
            long delayMs = (long) (5 - i + 1) * 1000L;
            TickScheduler.scheduleDelayed(() -> {
                MutableComponent countdown = Component.literal("Battle starts in: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.GOLD,
                                ChatFormatting.BOLD));
                for (UUID playerId : battle.getAllPlayers()) {
                    ServerPlayer player = getPlayerByUUID(playerId);
                    if (player != null)
                        player.sendSystemMessage(countdown);
                }
            }, delayMs);
        }
        TickScheduler.scheduleDelayed(() -> {
            MutableComponent fightMessage = Component.literal("FIGHT!").withStyle(ChatFormatting.RED,
                    ChatFormatting.BOLD);
            for (UUID playerId : battle.getAllPlayers()) {
                ServerPlayer player = getPlayerByUUID(playerId);
                if (player != null) {
                    player.sendSystemMessage(fightMessage);
                    removeFreezeEffects(player);
                }
            }
        }, 6000L);
    }

    private void cancelBattleDueToDisconnect(ActiveBattle battle, ServerPlayer disconnectedPlayer) {
        String battleId = battle.getBattleId();
        // Battle cancelled with no winner — return every escrowed stake.
        refundAllWagers(battle);
        for (UUID playerId : battle.getAllPlayers()) {
            if (!playerId.equals(disconnectedPlayer.getUUID())) {
                ServerPlayer player = getPlayerByUUID(playerId);
                if (player != null) {
                    player.sendSystemMessage(Component
                            .literal(disconnectedPlayer.getName().getString()
                                    + " disconnected. Battle cancelled - no penalties.")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        endBattle(battleId);
    }

    private void updatePlayerStats(UUID playerId, boolean won, boolean died, boolean isTeamBattle) {
        PlayerPvPStats stats = pvpManager.playerStats.computeIfAbsent(playerId, k -> new PlayerPvPStats());
        if (won)
            stats.addWin(isTeamBattle);
        else
            stats.addLoss(isTeamBattle);
        if (died)
            stats.addDeath();
        // Persist after every mutation so wins/losses survive crashes (fix #1).
        PvPStatsPersistence.save();
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
            if (i < team.size() - 1)
                component.append(", ");
        }
    }

    public void notifyTeamBattlePlayers(TeamBattle teamBattle, Component message) {
        List<UUID> allPlayers = new ArrayList<>();
        allPlayers.addAll(teamBattle.getTeam1());
        allPlayers.addAll(teamBattle.getTeam2());
        for (UUID playerId : allPlayers) {
            ServerPlayer player = getPlayerByUUID(playerId);
            if (player != null)
                player.sendSystemMessage(message);
        }
    }

    private ServerPlayer getPlayerByUUID(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(uuid);
    }

    private BlockPos calculateMapCenter(PvPMap map) {
        if (map.getSpawnPoints().isEmpty())
            return BlockPos.ZERO;
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
        if (server == null)
            return;

        try {
            BlockPos blockPos = pos.pos();
            // Fix #7: refuse to teleport to the (0,0,0) placeholder or to a y outside
            // the world height range. This stops players being void-dropped when an
            // arena map has gaps from addSpawnPoint(... spawnIndex=N) calls.
            if (blockPos.getX() == 0 && blockPos.getY() == 0 && blockPos.getZ() == 0) {
                LOGGER.error("Refusing to teleport {} to placeholder spawn point (0,0,0). Arena map is misconfigured.",
                        player.getName().getString());
                player.sendSystemMessage(Component.literal(
                        "Arena spawn point not configured properly. Contact an admin.").withStyle(ChatFormatting.RED));
                return;
            }
            if (blockPos.getY() < -64 || blockPos.getY() > 320) {
                LOGGER.error("Refusing to teleport {} to out-of-range spawn point {}. Arena map is misconfigured.",
                        player.getName().getString(), blockPos);
                player.sendSystemMessage(Component.literal(
                        "Arena spawn point Y out of range. Contact an admin.").withStyle(ChatFormatting.RED));
                return;
            }
            ServerLevel level = server.getLevel(pos.dimension());
            if (level == null)
                return;
            player.teleportTo(level, blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5, player.getYRot(),
                    player.getXRot());
        } catch (Exception e) {
            LOGGER.error("Error teleporting player {} to position {}: {}", player.getName().getString(), pos,
                    e.getMessage());
        }
    }

    private MutableComponent createStopButton() {
        return Component.literal("[Stop Spectating]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pvp spectate stop"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to stop spectating"))));
    }

    private void stopSpectating(ServerPlayer spectator, boolean sendMessage) {
        SpectatorData data = pvpManager.spectatorData.remove(spectator.getUUID());
        if (data != null) {
            spectator.setGameMode(data.originalGameMode());
            teleportTo(spectator, data.originalPos());
            pvpManager.activeSpectators.values().forEach(list -> list.remove(spectator.getUUID()));
            if (sendMessage) {
                spectator.sendSystemMessage(Component.literal("Stopped spectating").withStyle(ChatFormatting.GREEN));
            }
        }
    }

    private void restorePlayer(ServerPlayer player, GlobalPos originalPos, GameType originalGameMode) {
        if (player == null)
            return;
        if (originalPos != null) {
            teleportTo(player, originalPos);
        }
        // REMOVED: restoreInventory(player); - Players keep their actual inventory
        // (fixes duplication glitch)
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
        if (TaxConfig.isDebugLogging()) LOGGER.debug("Locked map: {}", mapName);
    }

    private void unlockMap(String mapName) {
        pvpManager.lockedMaps.remove(mapName);
        if (TaxConfig.isDebugLogging()) LOGGER.debug("Unlocked map: {}", mapName);
    }

    private boolean isMapAvailable(String mapName) {
        return !pvpManager.lockedMaps.contains(mapName);
    }

    private void updatePlayerKill(UUID killerId) {
        PlayerPvPStats stats = pvpManager.playerStats.computeIfAbsent(killerId, k -> new PlayerPvPStats());
        stats.addKill();
        // Persist kill count immediately (fix #1).
        PvPStatsPersistence.save();
        // Rewards can be handled here later
    }

    private void processBattleRewards(ActiveBattle battle, List<UUID> winners) {
        int wager = battle.getWager();
        if (wager <= 0 || winners == null || winners.isEmpty()) {
            battle.setWager(0); // nothing escrowed (or already settled) — no payout
            return;
        }

        // Settle using the source escrow actually took from (fix #5).
        CurrencyService.Source source = resolveBattleSource(battle);

        // Full pot = every participant's stake.
        int pot = wager * battle.getAllPlayers().size();
        battle.setWager(0); // mark settled BEFORE paying so an end-path refund can't double-spend

        // Pay only ONLINE winners; an offline winner cannot be delivered synchronously
        // and we must not burn their share. Determine the deliverable recipients first (#6).
        List<UUID> onlineWinners = new ArrayList<>();
        for (UUID winnerId : winners) {
            if (getPlayerByUUID(winnerId) != null) {
                onlineWinners.add(winnerId);
            }
        }

        if (onlineWinners.isEmpty()) {
            // No winner is online to receive the pot. Refunding every participant their
            // original stake conserves money instead of destroying the pot (#6). Losers'
            // escrow is returned via the stored source; idempotent re-credit.
            for (UUID playerId : battle.getAllPlayers()) {
                ServerPlayer p = getPlayerByUUID(playerId);
                // This branch runs BECAUSE people are offline, so skipping offline participants
                // here destroyed exactly the stakes it claims to be conserving.
                if (p == null) {
                    PendingWagerPayouts.queue(playerId, wager, "stake refund, no winner online");
                } else if (deliverWagerOrFallback(p, wager, source, "winner-offline-refund")) {
                    p.sendSystemMessage(Component.literal(
                            "No winner was online to collect the pot - your " + wager + " stake was refunded.")
                            .withStyle(ChatFormatting.YELLOW));
                } else {
                    PendingWagerPayouts.queue(playerId, wager, "stake refund delivery failed");
                }
            }
            return;
        }

        // Split the pot evenly among ONLINE winners; remainder to the first.
        int share = pot / onlineWinners.size();
        int remainder = pot - (share * onlineWinners.size());

        boolean first = true;
        for (UUID winnerId : onlineWinners) {
            int payout = share + (first ? remainder : 0);
            first = false;
            if (payout <= 0) continue;
            ServerPlayer winner = getPlayerByUUID(winnerId);
            // Only announce the win when the coins were actually delivered (H#3). A winner who
            // logged out between the online check above and this payout, or whose stores both
            // refuse, is owed the pot rather than losing it.
            if (deliverWagerOrFallback(winner, payout, source, "win")) {
                winner.sendSystemMessage(Component.literal("You won " + payout + " coins from the wager!")
                        .withStyle(ChatFormatting.GOLD));
            } else {
                PendingWagerPayouts.queue(winnerId, payout, "winnings delivery failed");
            }
        }
    }
}
