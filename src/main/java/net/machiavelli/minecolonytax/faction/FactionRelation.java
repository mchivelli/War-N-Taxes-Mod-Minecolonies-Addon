package net.machiavelli.minecolonytax.faction;

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
