package net.machiavelli.minecolonytax.espionage;

public class SpyMission {
    private String missionId;
    private String attackerPlayerId;
    private int attackerColonyId;
    private int targetColonyId;
    private String missionType; // "SCOUT", "SABOTAGE", "BRIBE", "STEAL"
    private String spyEntityUUID;
    private long startTime;
    private long maxDurationMs;
    private String status; // "DEPLOYING", "ACTIVE", "COMPLETED", "KILLED"
    private int cost;

    // For serialization
    public SpyMission() {
    }

    public SpyMission(String missionId, String attackerPlayerId, int attackerColonyId, int targetColonyId,
            String missionType, long startTime, long maxDurationMs, String status, int cost) {
        this.missionId = missionId;
        this.attackerPlayerId = attackerPlayerId;
        this.attackerColonyId = attackerColonyId;
        this.targetColonyId = targetColonyId;
        this.missionType = missionType;
        this.startTime = startTime;
        this.maxDurationMs = maxDurationMs;
        this.status = status;
        this.cost = cost;
    }

    public String getMissionId() {
        return missionId;
    }

    public String getAttackerPlayerId() {
        return attackerPlayerId;
    }

    public int getAttackerColonyId() {
        return attackerColonyId;
    }

    public int getTargetColonyId() {
        return targetColonyId;
    }

    public String getMissionType() {
        return missionType;
    }

    public String getSpyEntityUUID() {
        return spyEntityUUID;
    }

    public void setSpyEntityUUID(String spyEntityUUID) {
        this.spyEntityUUID = spyEntityUUID;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getMaxDurationMs() {
        return maxDurationMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCost() {
        return cost;
    }
}
