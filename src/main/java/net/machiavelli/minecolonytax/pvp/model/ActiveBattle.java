package net.machiavelli.minecolonytax.pvp.model;

import net.minecraft.core.GlobalPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ActiveBattle {
    private final String battleId;
    private final List<List<UUID>> teams;
    private final List<GlobalPos> spawnPositions;
    private final String mapName;
    private final long startTime;
    private final Map<UUID, Integer> originalScores;
    private final Map<UUID, GlobalPos> originalPositions;

    public ActiveBattle(String battleId, List<List<UUID>> teams, List<GlobalPos> spawnPositions, String mapName) {
        this.battleId = battleId;
        this.teams = teams;
        this.spawnPositions = spawnPositions;
        this.mapName = mapName;
        this.startTime = System.currentTimeMillis();
        this.originalScores = new HashMap<>();
        this.originalPositions = new HashMap<>();
    }

    // Getters
    public String getBattleId() { return battleId; }
    public List<List<UUID>> getTeams() { return teams; }
    public List<GlobalPos> getSpawnPositions() { return spawnPositions; }
    public String getMapName() { return mapName; }
    public long getStartTime() { return startTime; }
    public Map<UUID, Integer> getOriginalScores() { return originalScores; }
    public Map<UUID, GlobalPos> getOriginalPositions() { return originalPositions; }

    public List<UUID> getAllPlayers() {
        List<UUID> allPlayers = new ArrayList<>();
        for (List<UUID> team : teams) {
            allPlayers.addAll(team);
        }
        return allPlayers;
    }

    public int getTeamIndex(UUID playerId) {
        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).contains(playerId)) {
                return i;
            }
        }
        return -1;
    }

    public List<UUID> getTeammates(UUID playerId) {
        int teamIndex = getTeamIndex(playerId);
        return teamIndex >= 0 ? new ArrayList<>(teams.get(teamIndex)) : new ArrayList<>();
    }

    public List<UUID> getEnemies(UUID playerId) {
        List<UUID> enemies = new ArrayList<>();
        int playerTeam = getTeamIndex(playerId);
        for (int i = 0; i < teams.size(); i++) {
            if (i != playerTeam) {
                enemies.addAll(teams.get(i));
            }
        }
        return enemies;
    }

    public boolean isTeamEliminated(int teamIndex) {
        if (teamIndex < 0 || teamIndex >= teams.size()) return true;
        return teams.get(teamIndex).stream()
                .allMatch(playerId -> originalScores.getOrDefault(playerId, 0) <= 0);
    }

    public List<Integer> getRemainingTeams() {
        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            if (!isTeamEliminated(i)) {
                remaining.add(i);
            }
        }
        return remaining;
    }
} 