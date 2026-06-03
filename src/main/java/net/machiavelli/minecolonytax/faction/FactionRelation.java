package net.machiavelli.minecolonytax.faction;

/**
 * @deprecated Will be replaced by an external faction/diplomacy mod. Code retained for reference.
 */
@Deprecated
public enum FactionRelation {
    ALLY,
    NEUTRAL,
    ENEMY;

    public boolean isAlly() {
        return this == ALLY;
    }

    public boolean isEnemy() {
        return this == ENEMY;
    }
}
