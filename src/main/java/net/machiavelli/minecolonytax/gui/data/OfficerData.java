package net.machiavelli.minecolonytax.gui.data;

import net.machiavelli.minecolonytax.permissions.ColonyPermission;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * One row of the Officers tab. Built server-side and shipped to the client, because
 * every permission fact here lives in server-only state (TaxPermissionManager) that a
 * remote client cannot read.
 */
public class OfficerData {

    /** lastSeen sentinel: the server has no last-seen record for this player. */
    public static final long LAST_SEEN_UNKNOWN = 0L;

    private final UUID playerId;
    private final String playerName;
    private final String rank;
    /** True for the colony owner rank. */
    private final boolean isOwner;
    /** True for ranks that manage the colony (owner, officer, custom manager ranks). */
    private final boolean isManager;
    /** Effective right now, per action — mirrors the server-side gates. */
    private final Map<ColonyPermission, Boolean> effective;
    /** The raw grants the toggles control (individual override, else colony default). */
    private final Map<ColonyPermission, Boolean> granted;
    private final boolean isOnline;
    private final long lastSeen;

    public OfficerData(UUID playerId, String playerName, String rank,
                       boolean isOwner, boolean isManager,
                       Map<ColonyPermission, Boolean> effective,
                       Map<ColonyPermission, Boolean> granted,
                       boolean isOnline, long lastSeen) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.rank = rank;
        this.isOwner = isOwner;
        this.isManager = isManager;
        this.effective = new EnumMap<>(ColonyPermission.class);
        if (effective != null) this.effective.putAll(effective);
        this.granted = new EnumMap<>(ColonyPermission.class);
        if (granted != null) this.granted.putAll(granted);
        this.isOnline = isOnline;
        this.lastSeen = lastSeen;
    }

    /** Whether this player can perform the action right now (war/siege gates included). */
    public boolean can(ColonyPermission permission) {
        return Boolean.TRUE.equals(effective.get(permission));
    }

    /** Whether the owner has granted the action, ignoring temporary blocks. */
    public boolean isGranted(ColonyPermission permission) {
        Boolean value = granted.get(permission);
        return value != null ? value : permission.isDefaultAllowed();
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public String getRank() { return rank; }
    public boolean isOwner() { return isOwner; }
    public boolean isManager() { return isManager; }
    public boolean canClaimTax() { return can(ColonyPermission.CLAIM_TAX); }
    public boolean isClaimGranted() { return isGranted(ColonyPermission.CLAIM_TAX); }
    public boolean isOnline() { return isOnline; }
    public long getLastSeen() { return lastSeen; }

    /** Returns a human-readable "last seen" string, e.g. "2h ago". */
    public String getLastSeenText() {
        if (isOnline) {
            return "Online";
        }
        if (lastSeen == LAST_SEEN_UNKNOWN) {
            // No record. Previously this branch reported "Just now" for every offline
            // player because lastSeen was stamped with the request time server-side.
            return "unknown";
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
            return canClaimTax() ? 0x00FF00 : 0xFFFF00;
        } else {
            return 0x808080;
        }
    }

    public int getRankColor() {
        if (isOwner) return 0xFFD700; // gold — custom-named owner ranks stay gold
        switch (rank.toLowerCase()) {
            case "owner":   return 0xFFD700; // gold
            case "officer": return 0x00BFFF; // deep sky blue
            case "friend":  return 0x32CD32; // lime green
            case "neutral": return 0xC0C0C0; // silver
            default:        return isManager ? 0x00BFFF : 0xFFFFFF;
        }
    }
}
