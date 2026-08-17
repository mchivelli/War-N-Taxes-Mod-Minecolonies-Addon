package net.machiavelli.minecolonytax.util;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Single place for "give me the colony with this id".
 *
 * <p>Two wrong ways to do this were spread across the mod and each caused live bugs:
 *
 * <ul>
 *   <li>{@code getColonyByWorld(id, player.level())} reads the colony-manager capability of
 *       one specific level, so a player standing in the Nether could not see their overworld
 *       colony — the Officers tab, the treasury screen and the investment screen all came up
 *       empty, and permission toggles failed with "Colony not found".</li>
 *   <li>{@code getAllColonies().stream().filter(c -> c.getID() == id)} looks global but is not
 *       safe on its own: MineColonies keeps one {@code ColonyList} per level, each with its own
 *       id counter, so colony ids are unique <em>per dimension</em> only. On a server that
 *       allows colonies outside the overworld, two colonies can share an id and a bare filter
 *       silently picks whichever comes first.</li>
 * </ul>
 *
 * <p>This resolver prefers the requesting player's own dimension (which reproduces the old
 * behaviour exactly wherever it already worked), then falls back to a cross-dimension search,
 * and only ever guesses when an id really is ambiguous — and says so in the log when it does.
 */
public final class ColonyLookup {

    private static final Logger LOGGER = LogManager.getLogger(ColonyLookup.class);

    private ColonyLookup() {}

    /** Resolves a colony id in the context of the player who asked. Returns null if unknown. */
    public static IColony byId(int colonyId, ServerPlayer player) {
        IColonyManager manager = IMinecoloniesAPI.getInstance().getColonyManager();
        if (manager == null) return null;

        // The player's own dimension wins outright.
        if (player != null) {
            try {
                IColony local = manager.getColonyByWorld(colonyId, player.serverLevel());
                if (local != null) return local;
            } catch (Exception ignored) {
                // Fall through to the global search below.
            }
        }

        List<IColony> matches = collectMatches(manager, colonyId);
        if (matches.isEmpty()) return null;
        if (matches.size() == 1) return matches.get(0);

        // Ambiguous id: prefer a colony the player actually belongs to.
        if (player != null) {
            for (IColony candidate : matches) {
                if (candidate.getPermissions().getPlayers().containsKey(player.getUUID())) {
                    return candidate;
                }
            }
        }

        return disambiguate(matches, colonyId);
    }

    /** Resolves a colony id without a player context (server-side callers). */
    public static IColony byId(int colonyId) {
        IColonyManager manager = IMinecoloniesAPI.getInstance().getColonyManager();
        if (manager == null) return null;

        List<IColony> matches = collectMatches(manager, colonyId);
        if (matches.isEmpty()) return null;
        if (matches.size() == 1) return matches.get(0);
        return disambiguate(matches, colonyId);
    }

    private static List<IColony> collectMatches(IColonyManager manager, int colonyId) {
        List<IColony> matches = new ArrayList<>();
        for (IColony colony : manager.getAllColonies()) {
            if (colony != null && colony.getID() == colonyId) {
                matches.add(colony);
            }
        }
        return matches;
    }

    /**
     * Deterministic tie-break so the same id always resolves to the same colony rather than
     * depending on level iteration order, and a loud log line because an ambiguous id means
     * this server has colonies in more than one dimension.
     */
    private static IColony disambiguate(List<IColony> matches, int colonyId) {
        matches.sort(Comparator.comparing(c -> String.valueOf(c.getDimension().location())));
        IColony chosen = matches.get(0);
        LOGGER.warn("Colony id {} exists in {} dimensions (ids are only unique per dimension); "
                        + "resolved to the one in {}. Colony-id addressing is ambiguous on this server.",
                colonyId, matches.size(), chosen.getDimension().location());
        return chosen;
    }
}
