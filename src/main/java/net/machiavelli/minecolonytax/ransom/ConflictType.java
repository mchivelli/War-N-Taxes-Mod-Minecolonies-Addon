package net.machiavelli.minecolonytax.ransom;

/**
 * The kind of active conflict a ransom offer was created in.
 * Determines which percentage config applies and how the conflict is ended on accept.
 */
public enum ConflictType {
    RAID,
    BESIEGE
}
