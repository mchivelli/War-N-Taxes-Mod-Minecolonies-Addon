package net.machiavelli.minecolonytax.espionage;

import java.util.ArrayList;
import java.util.List;

public class SpyIntelData {

    // ---- Tier tracking ----
    // 0 = nothing gathered yet
    // 1 = early (citizenCount, buildingCount, isAtWar, targetColonyName)
    // 2 = mid   (guardCount, happiness, taxBalance)
    // 3 = late  (owner/officer SDM balances, colony member list)
    private int intelTier = 0;

    // ---- Tier 1 fields ----
    private int citizenCount = -1;
    private int buildingCount = -1;
    private boolean isAtWar = false;
    private String targetColonyName = null;

    // ---- Tier 2 fields ----
    private int guardCount = -1;
    private double happiness = -1.0;
    private int taxBalance = -1;

    // ---- Tier 3 fields ----
    private long ownerSdmBalance = -1;
    private String ownerName = null;
    private List<OfficerFinanceInfo> officerFinances = null;
    private List<ColonyMemberInfo> colonyMembers = null;

    private long lastUpdated;

    // ---- Inner classes ----

    public static class ColonyMemberInfo {
        public String playerName;
        public String rankName;
        public boolean isOnline;

        public ColonyMemberInfo() {
        }

        public ColonyMemberInfo(String playerName, String rankName, boolean isOnline) {
            this.playerName = playerName;
            this.rankName = rankName;
            this.isOnline = isOnline;
        }
    }

    public static class OfficerFinanceInfo {
        public String playerName;
        public long sdmBalance;

        public OfficerFinanceInfo() {
        }

        public OfficerFinanceInfo(String playerName, long sdmBalance) {
            this.playerName = playerName;
            this.sdmBalance = sdmBalance;
        }
    }

    // ---- Constructors ----

    public SpyIntelData() {
        this.lastUpdated = System.currentTimeMillis();
    }

    // Legacy constructor kept for backward compat — creates a Tier 2 snapshot
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
        this.intelTier = 2;
    }

    // ---- Tier getters ----

    public int getIntelTier() {
        return intelTier;
    }

    /** True if Tier 1 data has been gathered (early intel). */
    public boolean isEarlyAvailable() {
        return intelTier >= 1;
    }

    /** True if Tier 2 data has been gathered (mid intel). */
    public boolean isMidAvailable() {
        return intelTier >= 2;
    }

    /** True if Tier 3 data has been gathered (late/deep intel). */
    public boolean isLateAvailable() {
        return intelTier >= 3;
    }

    // ---- Tier 1 getters/setters ----

    public int getCitizenCount() {
        return citizenCount;
    }

    public void setCitizenCount(int citizenCount) {
        this.citizenCount = citizenCount;
    }

    public int getBuildingCount() {
        return buildingCount;
    }

    public void setBuildingCount(int buildingCount) {
        this.buildingCount = buildingCount;
    }

    public boolean isAtWar() {
        return isAtWar;
    }

    public void setAtWar(boolean atWar) {
        this.isAtWar = atWar;
    }

    public String getTargetColonyName() {
        return targetColonyName;
    }

    public void setTargetColonyName(String targetColonyName) {
        this.targetColonyName = targetColonyName;
    }

    // ---- Tier 2 getters/setters ----

    public int getGuardCount() {
        return guardCount;
    }

    public void setGuardCount(int guardCount) {
        this.guardCount = guardCount;
    }

    public double getHappiness() {
        return happiness;
    }

    public void setHappiness(double happiness) {
        this.happiness = happiness;
    }

    public int getTaxBalance() {
        return taxBalance;
    }

    public void setTaxBalance(int taxBalance) {
        this.taxBalance = taxBalance;
    }

    // ---- Tier 3 getters/setters ----

    public long getOwnerSdmBalance() {
        return ownerSdmBalance;
    }

    public void setOwnerSdmBalance(long ownerSdmBalance) {
        this.ownerSdmBalance = ownerSdmBalance;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public List<OfficerFinanceInfo> getOfficerFinances() {
        return officerFinances;
    }

    public void setOfficerFinances(List<OfficerFinanceInfo> officerFinances) {
        this.officerFinances = officerFinances;
    }

    public List<ColonyMemberInfo> getColonyMembers() {
        return colonyMembers;
    }

    public void setColonyMembers(List<ColonyMemberInfo> colonyMembers) {
        this.colonyMembers = colonyMembers;
    }

    // ---- Tier advancement ----

    public void advanceToTier(int tier) {
        if (tier > this.intelTier) {
            this.intelTier = tier;
        }
        this.lastUpdated = System.currentTimeMillis();
    }

    // ---- Common ----

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /** Legacy update — overwrites all tier-1 and tier-2 data. */
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
        if (this.intelTier < 2) {
            this.intelTier = 2;
        }
    }
}
