package net.machiavelli.minecolonytax.permissions;

/**
 * Colony actions an owner can grant to or withhold from individual officers.
 *
 * <p>Every entry is enforced server-side at the action's own entry point; the Officers tab
 * only renders and toggles them. {@link #isDefaultAllowed()} is chosen so that a server
 * updating into this feature keeps behaving exactly as before until an owner changes
 * something — which is why DECLARE_WAR defaults to <em>false</em>: declaring war used to be
 * structurally owner-only ({@code WarSystem.findValidAttackerColony} filters on colony
 * ownership), so granting it is a new capability rather than a restriction being lifted.
 */
public enum ColonyPermission {

    /** Claim the colony's accumulated tax balance. */
    CLAIM_TAX("Claim Taxes", true, true),

    // Vassal tribute is deliberately absent: VassalManager keys the relation on the overlord's
    // player UUID and resolves the receiving colony via getPrimaryColonyOfPlayer(), so an
    // officer cannot claim it without reworking how tribute money is routed. Adding a toggle
    // that silently does nothing would be worse than not offering one.

    /** Withdraw funds from the colony treasury. */
    WITHDRAW_FUNDS("Withdraw Funds", true, false),

    /** Deploy spy missions paid for by the colony. */
    DEPLOY_SPY("Deploy Spies", true, false),

    /** Declare war on another colony on this colony's behalf. */
    DECLARE_WAR("Declare War", true, false);

    private final String displayName;
    private final boolean defaultAllowed;
    private final boolean blockedWhileBesieged;

    ColonyPermission(String displayName, boolean defaultAllowed, boolean blockedWhileBesieged) {
        this.displayName = displayName;
        this.defaultAllowed = defaultAllowed;
        this.blockedWhileBesieged = blockedWhileBesieged;
    }

    /** Short label for the GUI. Kept under 16 chars so it fits the book page. */
    public String getDisplayName() { return displayName; }

    /** Value used when neither an individual override nor a colony default is set. */
    public boolean isDefaultAllowed() { return defaultAllowed; }

    /**
     * Whether the besiege lock suppresses this permission. Only CLAIM_TAX is marked, which
     * preserves the pre-existing behaviour exactly — the besiege gate has never applied to
     * treasury withdrawals, spy deploys or war declarations, and silently extending it here
     * would be an unrequested gameplay change.
     */
    public boolean isBlockedWhileBesieged() { return blockedWhileBesieged; }

    /** Bounds-checked lookup for packet decoding; returns null for an out-of-range ordinal. */
    public static ColonyPermission byOrdinal(int ordinal) {
        ColonyPermission[] values = values();
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : null;
    }
}
