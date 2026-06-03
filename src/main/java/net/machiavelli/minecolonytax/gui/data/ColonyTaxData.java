package net.machiavelli.minecolonytax.gui.data;

public class ColonyTaxData {
    private final int colonyId;
    private final String colonyName;
    private final int taxBalance;
    private final int maxTaxRevenue;
    private final int buildingCount;
    private final int guardCount;
    private final int guardTowerCount;
    private final boolean canClaimTax;
    private final boolean isAtWar;
    private final boolean isBeingRaided;
    private final boolean isBesieged;
    private final boolean isOccupied;
    private final boolean isVassal;
    private final int vassalTributeRate;
    private final boolean hasVassals;
    private final int vassalCount;
    private final long lastTaxGeneration;
    private final int debtAmount;
    private final int approximateRevenuePerInterval;
    private final boolean isOwner;
    private final String taxPolicy;
    private final double colonyHappiness;
    private final double happinessMultiplier;

    // UI state
    private int claimButtonX, claimButtonY, claimButtonWidth, claimButtonHeight;
    private int permissionButtonX, permissionButtonY, permissionButtonWidth, permissionButtonHeight;

    public ColonyTaxData(int colonyId, String colonyName, int taxBalance, int maxTaxRevenue,
                        int buildingCount, int guardCount, int guardTowerCount,
                        boolean canClaimTax, boolean isAtWar, boolean isBeingRaided,
                        boolean isVassal, int vassalTributeRate, boolean hasVassals, int vassalCount,
                        long lastTaxGeneration, int debtAmount, int approximateRevenuePerInterval, boolean isOwner,
                        String taxPolicy, double colonyHappiness, double happinessMultiplier,
                        boolean isBesieged, boolean isOccupied) {
        this.colonyId = colonyId;
        this.colonyName = colonyName;
        this.taxBalance = taxBalance;
        this.maxTaxRevenue = maxTaxRevenue;
        this.buildingCount = buildingCount;
        this.guardCount = guardCount;
        this.guardTowerCount = guardTowerCount;
        this.canClaimTax = canClaimTax;
        this.isAtWar = isAtWar;
        this.isBeingRaided = isBeingRaided;
        this.isBesieged = isBesieged;
        this.isOccupied = isOccupied;
        this.isVassal = isVassal;
        this.vassalTributeRate = vassalTributeRate;
        this.hasVassals = hasVassals;
        this.vassalCount = vassalCount;
        this.lastTaxGeneration = lastTaxGeneration;
        this.debtAmount = debtAmount;
        this.approximateRevenuePerInterval = approximateRevenuePerInterval;
        this.isOwner = isOwner;
        this.taxPolicy = taxPolicy;
        this.colonyHappiness = colonyHappiness;
        this.happinessMultiplier = happinessMultiplier;
    }

    public int getColonyId() { return colonyId; }
    public String getColonyName() { return colonyName; }
    public int getTaxBalance() { return taxBalance; }
    public int getMaxTaxRevenue() { return maxTaxRevenue; }
    public int getBuildingCount() { return buildingCount; }
    public int getGuardCount() { return guardCount; }
    public int getGuardTowerCount() { return guardTowerCount; }
    public boolean canClaimTax() { return canClaimTax; }
    public boolean isAtWar() { return isAtWar; }
    public boolean isBeingRaided() { return isBeingRaided; }
    public boolean isBesieged() { return isBesieged; }
    public boolean isOccupied() { return isOccupied; }
    public boolean isVassal() { return isVassal; }
    public int getVassalTributeRate() { return vassalTributeRate; }
    public boolean hasVassals() { return hasVassals; }
    public int getVassalCount() { return vassalCount; }
    public long getLastTaxGeneration() { return lastTaxGeneration; }

    public void setClaimButtonBounds(int x, int y, int width, int height) {
        this.claimButtonX = x;
        this.claimButtonY = y;
        this.claimButtonWidth = width;
        this.claimButtonHeight = height;
    }
    
    public boolean isClaimButtonClicked(double mouseX, double mouseY) {
        return mouseX >= claimButtonX && mouseX < claimButtonX + claimButtonWidth &&
               mouseY >= claimButtonY && mouseY < claimButtonY + claimButtonHeight;
    }
    
    public void setPermissionButtonBounds(int x, int y, int width, int height) {
        this.permissionButtonX = x;
        this.permissionButtonY = y;
        this.permissionButtonWidth = width;
        this.permissionButtonHeight = height;
    }
    
    public boolean isPermissionButtonClicked(double mouseX, double mouseY) {
        return mouseX >= permissionButtonX && mouseX < permissionButtonX + permissionButtonWidth &&
               mouseY >= permissionButtonY && mouseY < permissionButtonY + permissionButtonHeight;
    }
    
    /** Tax fill percentage in [0.0, 1.0]. */
    public double getTaxFillPercentage() {
        if (maxTaxRevenue <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, (double) taxBalance / maxTaxRevenue));
    }
    
    public long getMinutesSinceLastGeneration() {
        return (System.currentTimeMillis() - lastTaxGeneration) / 60000;
    }
    
    public boolean hasGuardTowerBoost(int requiredTowers) {
        return guardTowerCount >= requiredTowers;
    }
    
    public int getDebtAmount() { return debtAmount; }
    public int getApproximateRevenuePerInterval() { return approximateRevenuePerInterval; }
    public boolean isOwner() { return isOwner; }
    public String getTaxPolicy() { return taxPolicy; }
    public double getColonyHappiness() { return colonyHappiness; }
    public double getHappinessMultiplier() { return happinessMultiplier; }

    public boolean hasDebt() {
        return taxBalance < 0;
    }
}
