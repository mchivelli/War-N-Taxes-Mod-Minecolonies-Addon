package net.machiavelli.minecolonytax.pvp.model;

public class PlayerPvPStats {
    private int duelsWon = 0;
    private int duelsLost = 0;
    private int teamBattlesWon = 0;
    private int teamBattlesLost = 0;
    private int arenaKills = 0;
    private int arenaDeaths = 0;

    // Getters
    public int getDuelsWon() { return duelsWon; }
    public int getDuelsLost() { return duelsLost; }
    public int getTeamBattlesWon() { return teamBattlesWon; }
    public int getTeamBattlesLost() { return teamBattlesLost; }
    public int getArenaKills() { return arenaKills; }
    public int getArenaDeaths() { return arenaDeaths; }

    public double getWinLossRatio() {
        int totalLosses = duelsLost + teamBattlesLost;
        if (totalLosses == 0) return duelsWon + teamBattlesWon; // Prevent division by zero
        return (double)(duelsWon + teamBattlesWon) / totalLosses;
    }

    public double getKillDeathRatio() {
        if (arenaDeaths == 0) return arenaKills; // Prevent division by zero
        return (double)arenaKills / arenaDeaths;
    }

    // Methods to update stats
    public void addWin(boolean isTeamBattle) {
        if (isTeamBattle) teamBattlesWon++;
        else duelsWon++;
    }

    public void addLoss(boolean isTeamBattle) {
        if (isTeamBattle) teamBattlesLost++;
        else duelsLost++;
    }

    public void addKill() { arenaKills++; }
    public void addDeath() { arenaDeaths++; }
} 