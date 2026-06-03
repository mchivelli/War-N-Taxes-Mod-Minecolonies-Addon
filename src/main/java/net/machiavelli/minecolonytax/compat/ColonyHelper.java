package net.machiavelli.minecolonytax.compat;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import java.util.UUID;

/**
 * Public utility for colony lookups used by compat classes.
 * Safe to call from any class that knows the server is running.
 */
public class ColonyHelper {

    /**
     * Returns the IColony owned by the given player UUID, or null if none.
     * "Primary" = the colony where this UUID is the registered owner.
     */
    public static IColony getPrimaryColony(UUID playerUUID) {
        if (playerUUID == null)
            return null;
        for (IColony c : IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()) {
            if (playerUUID.equals(c.getPermissions().getOwner()))
                return c;
        }
        return null;
    }

    /**
     * Returns the colony ID for the given player, or -1 if they own no colony.
     */
    public static int getPrimaryColonyId(UUID playerUUID) {
        IColony c = getPrimaryColony(playerUUID);
        return c != null ? c.getID() : -1;
    }
}
