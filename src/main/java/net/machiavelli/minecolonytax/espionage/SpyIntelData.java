package net.machiavelli.minecolonytax.espionage;

public class SpyIntelData {
    private int citizenCount;
    private int guardCount;
    private double happiness;
    private int taxBalance;
    private int buildingCount;
    private boolean isAtWar;
    private String targetColonyName;
    private long lastUpdated;

    public SpyIntelData() {
    }

    public SpyIntelData(int citizenCount, int guardCount, double happiness, int taxBalance, int buildingCount,
            boolean isAtWar, String targetColonyName, long lastUpdated) {
        this.citizenCount = citizenCount;
        this.guardCount = guardCount;
        this.happiness = happiness;
        this.taxBalance = taxBalance;
        this.buildingCount = buildingCount;
        this.isAtWar = isAtWar;
        this.targetColonyName = targetColonyName;
        this.lastUpdated = lastUpdated;
    }

    public int getCitizenCount() {
        return citizenCount;
    }

    public int getGuardCount() {
        return guardCount;
    }

    public double getHappiness() {
        return happiness;
    }

    public int getTaxBalance() {
        return taxBalance;
    }

    public int getBuildingCount() {
        return buildingCount;
    }

    public boolean isAtWar() {
        return isAtWar;
    }

    public String getTargetColonyName() {
        return targetColonyName;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void update(int citizenCount, int guardCount, double happiness, int taxBalance, int buildingCount,
            boolean isAtWar, String targetColonyName, long lastUpdated) {
        this.citizenCount = citizenCount;
        this.guardCount = guardCount;
        this.happiness = happiness;
        this.taxBalance = taxBalance;
        this.buildingCount = buildingCount;
        this.isAtWar = isAtWar;
        this.targetColonyName = targetColonyName;
        this.lastUpdated = lastUpdated;
    }
}
