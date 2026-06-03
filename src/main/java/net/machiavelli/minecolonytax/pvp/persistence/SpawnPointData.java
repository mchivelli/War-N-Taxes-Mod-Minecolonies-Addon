package net.machiavelli.minecolonytax.pvp.persistence;

public class SpawnPointData {
    public int x, y, z;

    /** True when this entry was inserted as a placeholder (0,0,0) by gap-fill in addSpawnPoint. */
    public boolean isPlaceholder() {
        return x == 0 && y == 0 && z == 0;
    }

    /**
     * Loose validation for use before teleporting a player into the spawn:
     *  - y must be in [-64, 320] (vanilla 1.20.1 overworld height range; nether/end are also inside)
     *  - the spawn must not be the (0,0,0) placeholder
     *
     * Returns false for unsafe / unconfigured spawn points; callers should
     * refuse to teleport when this returns false.
     */
    public boolean isValid() {
        if (isPlaceholder()) return false;
        if (y < -64 || y > 320) return false;
        return true;
    }
}
