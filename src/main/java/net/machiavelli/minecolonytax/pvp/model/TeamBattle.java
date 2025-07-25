package net.machiavelli.minecolonytax.pvp.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamBattle {
    private final String battleId;
    private final String mapName;
    private final UUID organizer;
    private final List<UUID> team1 = new ArrayList<>();
    private final List<UUID> team2 = new ArrayList<>();
    private final long createdTime;
    private TeamBattleState state = TeamBattleState.RECRUITING;
    private final int maxTeamSize;
    public long countdownStartTime;

    public TeamBattle(String battleId, String mapName, UUID organizer, int maxTeamSize) {
        this.battleId = battleId;
        this.mapName = mapName;
        this.organizer = organizer;
        this.createdTime = System.currentTimeMillis();
        this.maxTeamSize = maxTeamSize;
    }

    public String getBattleId() { return battleId; }
    public String getMapName() { return mapName; }
    public UUID getOrganizer() { return organizer; }
    public List<UUID> getTeam1() { return team1; }
    public List<UUID> getTeam2() { return team2; }
    public TeamBattleState getState() { return state; }
    public int getMaxTeamSize() { return maxTeamSize; }

    public void setState(TeamBattleState state) { this.state = state; }

    public void startCountdown() {
        if (this.state == TeamBattleState.RECRUITING) {
            this.state = TeamBattleState.COUNTDOWN;
            this.countdownStartTime = System.currentTimeMillis();
        }
    }

    public boolean addPlayerToTeam(UUID playerId, int teamNumber) {
        if (state == TeamBattleState.IN_PROGRESS || team1.contains(playerId) || team2.contains(playerId)) {
            return false;
        }
        if (teamNumber == 1 && team1.size() < maxTeamSize) {
            return team1.add(playerId);
        } else if (teamNumber == 2 && team2.size() < maxTeamSize) {
            return team2.add(playerId);
        }
        return false;
    }

    public void removePlayer(UUID playerId) {
        team1.remove(playerId);
        team2.remove(playerId);
    }

    public boolean canStart() {
        return !team1.isEmpty() && !team2.isEmpty();
    }

    public int getTotalPlayers() {
        return team1.size() + team2.size();
    }

    /**
     * Get the team name ("team1" or "team2") for a player
     *
     * @param playerId UUID of the player
     * @return Team name or null if player is not in any team
     */
    public String getTeamForPlayer(UUID playerId) {
        if (team1.contains(playerId)) {
            return "team1";
        } else if (team2.contains(playerId)) {
            return "team2";
        }
        return null;
    }

    /**
     * Checks if two players are on the same team
     *
     * @param player1 UUID of the first player
     * @param player2 UUID of the second player
     * @return true if both players are on the same team, false otherwise
     */
    public boolean arePlayersOnSameTeam(UUID player1, UUID player2) {
        String team1Name = getTeamForPlayer(player1);
        String team2Name = getTeamForPlayer(player2);
        return team1Name != null && team1Name.equals(team2Name);
    }
}