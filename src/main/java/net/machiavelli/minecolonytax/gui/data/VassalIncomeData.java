package net.machiavelli.minecolonytax.gui.data;

/**
 * Data container for vassal income information displayed in the GUI
 */
public class VassalIncomeData {
    private final int vassalColonyId;
    private final String vassalColonyName;
    private final int tributeRate;
    private final int tributeOwed;
    private final int lastTribute;
    private final long lastPayment;
    private final boolean canClaim;
    
    // UI state for claim button
    private int claimButtonX, claimButtonY, claimButtonWidth, claimButtonHeight;
    
    public VassalIncomeData(int vassalColonyId, String vassalColonyName, int tributeRate, 
                           int tributeOwed, int lastTribute, long lastPayment, boolean canClaim) {
        this.vassalColonyId = vassalColonyId;
        this.vassalColonyName = vassalColonyName;
        this.tributeRate = tributeRate;
        this.tributeOwed = tributeOwed;
        this.lastTribute = lastTribute;
        this.lastPayment = lastPayment;
        this.canClaim = canClaim;
    }
    
    // Getters
    public int getVassalColonyId() { return vassalColonyId; }
    public String getVassalColonyName() { return vassalColonyName; }
    public int getTributeRate() { return tributeRate; }
    public int getTributeOwed() { return tributeOwed; }
    public int getLastTribute() { return lastTribute; }
    public long getLastPayment() { return lastPayment; }
    public boolean canClaim() { return canClaim; }
    
    // Button bounds for UI interaction
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
    
    public String getFormattedLastPayment() {
        if (lastPayment == 0) return "Never";
        long minutes = (System.currentTimeMillis() - lastPayment) / 60000;
        if (minutes < 60) return minutes + "m ago";
        if (minutes < 1440) return (minutes / 60) + "h ago";
        return (minutes / 1440) + "d ago";
    }
}
