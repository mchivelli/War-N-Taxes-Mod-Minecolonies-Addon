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
    private String status; // "DEPLOYING", "ACTIVE", "FLEEING", "ESCAPED", "COMPLETED", "KILLED", "RECALLED"
    private int cost;
    private SpyIntelData missionIntel;

    // Travel phase fields
    private int sourceX;
    private int sourceZ;
    private int destX;
    private int destZ;
    private long travelDurationMs;
    // Return travel (RETURNING status)
    private long recallStartTime;

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

    public SpyIntelData getMissionIntel() {
        return missionIntel;
    }

    public void setMissionIntel(SpyIntelData missionIntel) {
        this.missionIntel = missionIntel;
    }

    public int getSourceX() { return sourceX; }
    public void setSourceX(int sourceX) { this.sourceX = sourceX; }

    public int getSourceZ() { return sourceZ; }
    public void setSourceZ(int sourceZ) { this.sourceZ = sourceZ; }

    public int getDestX() { return destX; }
    public void setDestX(int destX) { this.destX = destX; }

    public int getDestZ() { return destZ; }
    public void setDestZ(int destZ) { this.destZ = destZ; }

    public long getTravelDurationMs() { return travelDurationMs; }
    public void setTravelDurationMs(long travelDurationMs) { this.travelDurationMs = travelDurationMs; }

    public long getRecallStartTime() { return recallStartTime; }
    public void setRecallStartTime(long recallStartTime) { this.recallStartTime = recallStartTime; }
}
