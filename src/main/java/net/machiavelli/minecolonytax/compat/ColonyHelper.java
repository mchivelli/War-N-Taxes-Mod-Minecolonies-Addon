package net.machiavelli.minecolonytax.compat;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import java.util.UUID;

public class ColonyHelper {

    public static IColony getPrimaryColony(UUID playerUUID) {
        if (playerUUID == null)
            return null;
        for (IColony c : IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()) {
            if (playerUUID.equals(c.getPermissions().getOwner()))
                return c;
        }
        return null;
    }

    public static int getPrimaryColonyId(UUID playerUUID) {
        IColony c = getPrimaryColony(playerUUID);
        return c != null ? c.getID() : -1;
    }
}
