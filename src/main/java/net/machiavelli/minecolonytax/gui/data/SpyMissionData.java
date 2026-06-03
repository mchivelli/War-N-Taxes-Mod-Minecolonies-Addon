package net.machiavelli.minecolonytax.gui.data;

import net.machiavelli.minecolonytax.espionage.SpyIntelData;

public class SpyMissionData {
    private final String missionId;
    private final String targetColonyName;
    private final int targetColonyId;
    private final int attackerColonyId;
    private final String missionType;
    private final String status;
    private final long startTime;
    private final long maxDurationMs;
    private final int cost;
    private final SpyIntelData intel;
    private final long elapsedTimeMs;
    private final int sourceX;
    private final int sourceZ;
    private final int destX;
    private final int destZ;
    private final long travelDurationMs;
    private final long recallStartTime;

    public SpyMissionData(String missionId, String targetColonyName, int targetColonyId, int attackerColonyId,
            String missionType, String status, long startTime, long maxDurationMs, int cost, SpyIntelData intel,
            int sourceX, int sourceZ, int destX, int destZ, long travelDurationMs, long recallStartTime) {
        this.missionId = missionId;
        this.targetColonyName = targetColonyName;
        this.targetColonyId = targetColonyId;
        this.attackerColonyId = attackerColonyId;
        this.missionType = missionType;
        this.status = status;
        this.startTime = startTime;
        this.maxDurationMs = maxDurationMs;
        this.cost = cost;
        this.intel = intel;
        this.elapsedTimeMs = startTime > 0 ? System.currentTimeMillis() - startTime : 0;
        this.sourceX = sourceX;
        this.sourceZ = sourceZ;
        this.destX = destX;
        this.destZ = destZ;
        this.travelDurationMs = travelDurationMs;
        this.recallStartTime = recallStartTime;
    }

    public String getMissionId() {
        return missionId;
    }

    public String getTargetColonyName() {
        return targetColonyName;
    }

    public int getTargetColonyId() {
        return targetColonyId;
    }

    public int getAttackerColonyId() {
        return attackerColonyId;
    }

    public String getMissionType() {
        return missionType;
    }

    public String getStatus() {
        return status;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getMaxDurationMs() {
        return maxDurationMs;
    }

    public int getCost() {
        return cost;
    }

    public SpyIntelData getIntel() {
        return intel;
    }

    public long getElapsedTimeMs() {
        return elapsedTimeMs;
    }

    public int getSourceX() { return sourceX; }
    public int getSourceZ() { return sourceZ; }
    public int getDestX() { return destX; }
    public int getDestZ() { return destZ; }
    public long getTravelDurationMs() { return travelDurationMs; }
    public long getRecallStartTime() { return recallStartTime; }
    /** Elapsed ms since recall was initiated (RETURNING phase). */
    public long getElapsedReturnMs() {
        return recallStartTime > 0 ? System.currentTimeMillis() - recallStartTime : 0;
    }
}
