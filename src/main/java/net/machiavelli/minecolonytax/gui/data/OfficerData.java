package net.machiavelli.minecolonytax.gui.data;

import java.util.UUID;

public class OfficerData {
    private final UUID playerId;
    private final String playerName;
    private final String rank;
    private final boolean canClaimTax;
    private final boolean isOnline;
    private final long lastSeen;
    
    public OfficerData(UUID playerId, String playerName, String rank, boolean canClaimTax, boolean isOnline, long lastSeen) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.rank = rank;
        this.canClaimTax = canClaimTax;
        this.isOnline = isOnline;
        this.lastSeen = lastSeen;
    }
    
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public String getRank() { return rank; }
    public boolean canClaimTax() { return canClaimTax; }
    public boolean isOnline() { return isOnline; }
    public long getLastSeen() { return lastSeen; }
    
    /** Returns a human-readable "last seen" string, e.g. "2h ago". */
    public String getLastSeenText() {
        if (isOnline) {
            return "Online";
        }
        
        long diff = System.currentTimeMillis() - lastSeen;
        long minutes = diff / (1000 * 60);
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + "d ago";
        } else if (hours > 0) {
            return hours + "h ago";
        } else if (minutes > 0) {
            return minutes + "m ago";
        } else {
            return "Just now";
        }
    }
    
    /** Green = online + can claim; yellow = online but no claim; gray = offline. */
    public int getStatusColor() {
        if (isOnline) {
            return canClaimTax ? 0x00FF00 : 0xFFFF00;
        } else {
            return 0x808080;
        }
    }

    public int getRankColor() {
        switch (rank.toLowerCase()) {
            case "owner":   return 0xFFD700; // gold
            case "officer": return 0x00BFFF; // deep sky blue
            case "friend":  return 0x32CD32; // lime green
            case "neutral": return 0xC0C0C0; // silver
            default:        return 0xFFFFFF;
        }
    }
}
