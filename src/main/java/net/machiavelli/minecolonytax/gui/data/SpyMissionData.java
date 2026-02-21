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

    public SpyMissionData(String missionId, String targetColonyName, int targetColonyId, int attackerColonyId,
            String missionType, String status, long startTime, long maxDurationMs, int cost, SpyIntelData intel) {
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
}
