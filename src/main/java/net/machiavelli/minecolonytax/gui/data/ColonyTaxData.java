package net.machiavelli.minecolonytax.gui.data;

/**
 * Data container for colony tax information displayed in the GUI
 */
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
    private final boolean isVassal;
    private final int vassalTributeRate;
    private final boolean hasVassals;
    private final int vassalCount;
    private final long lastTaxGeneration;
    private final int debtAmount;
    private final int approximateRevenuePerInterval;
    private final boolean isOwner;
    private final String taxPolicy; // Tax policy name (NORMAL, LOW, HIGH, WAR_ECONOMY)

    // UI state
    private int claimButtonX, claimButtonY, claimButtonWidth, claimButtonHeight;
    private int permissionButtonX, permissionButtonY, permissionButtonWidth, permissionButtonHeight;

    public ColonyTaxData(int colonyId, String colonyName, int taxBalance, int maxTaxRevenue,
                        int buildingCount, int guardCount, int guardTowerCount,
                        boolean canClaimTax, boolean isAtWar, boolean isBeingRaided,
                        boolean isVassal, int vassalTributeRate, boolean hasVassals, int vassalCount,
                        long lastTaxGeneration, int debtAmount, int approximateRevenuePerInterval, boolean isOwner,
                        String taxPolicy) {
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
        this.isVassal = isVassal;
        this.vassalTributeRate = vassalTributeRate;
        this.hasVassals = hasVassals;
        this.vassalCount = vassalCount;
        this.lastTaxGeneration = lastTaxGeneration;
        this.debtAmount = debtAmount;
        this.approximateRevenuePerInterval = approximateRevenuePerInterval;
        this.isOwner = isOwner;
        this.taxPolicy = taxPolicy;
    }

    // Getters
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
    public boolean isVassal() { return isVassal; }
    public int getVassalTributeRate() { return vassalTributeRate; }
    public boolean hasVassals() { return hasVassals; }
    public int getVassalCount() { return vassalCount; }
    public long getLastTaxGeneration() { return lastTaxGeneration; }
    
    // UI state methods
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
    
    /**
     * Gets the tax fill percentage (0.0 to 1.0)
     */
    public double getTaxFillPercentage() {
        if (maxTaxRevenue <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, (double) taxBalance / maxTaxRevenue));
    }
    
    /**
     * Gets minutes since last tax generation
     */
    public long getMinutesSinceLastGeneration() {
        return (System.currentTimeMillis() - lastTaxGeneration) / 60000;
    }
    
    /**
     * Checks if colony has enough guard towers for tax boost
     */
    public boolean hasGuardTowerBoost(int requiredTowers) {
        return guardTowerCount >= requiredTowers;
    }
    
    public int getDebtAmount() { return debtAmount; }
    public int getApproximateRevenuePerInterval() { return approximateRevenuePerInterval; }
    public boolean isOwner() { return isOwner; }
    public String getTaxPolicy() { return taxPolicy; }

    /**
     * Checks if colony has debt (negative tax balance)
     */
    public boolean hasDebt() {
        return taxBalance < 0;
    }
}
