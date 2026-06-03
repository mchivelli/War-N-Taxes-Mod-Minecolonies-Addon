package net.machiavelli.minecolonytax.compat;

import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Classloader-safe FTB Teams compatibility shim.
 *
 * <p>FTB Teams is an OPTIONAL dependency. Direct {@code dev.ftb.mods.ftbteams.*}
 * imports anywhere outside this package will fail class loading (via
 * {@link NoClassDefFoundError}) BEFORE any runtime guard can run when FTB
 * Teams is absent on the modpack. To prevent that, all FTB Teams API calls
 * go through this class.
 *
 * <p>The actual FTB Teams imports live ONLY in {@link FtbTeamsCompatImpl}.
 * That inner-package class is loaded lazily and is only ever referenced
 * through the methods in this class — and only after we check
 * {@code ModList.isLoaded("ftbteams")}. If loading still fails defensively
 * we catch {@link NoClassDefFoundError} / {@link Throwable} and return
 * sensible defaults (false, empty collections, null) so the rest of the mod
 * can proceed as if FTB Teams were absent.
 *
 * <p>This mirrors the {@code JmImpl}/{@code SpyJourneyMapPlugin} pattern
 * used for JourneyMap.
 */
public final class FtbTeamsCompat {

    private static final Logger LOGGER = LogManager.getLogger(FtbTeamsCompat.class);

    private static Boolean cachedPresent = null;

    private FtbTeamsCompat() {}

    /** True iff FTB Teams is loaded AND its API class is reachable. */
    public static boolean isInstalled() {
        if (cachedPresent != null) return cachedPresent;
        boolean modPresent;
        try {
            modPresent = ModList.get() != null && ModList.get().isLoaded("ftbteams");
        } catch (Throwable t) {
            modPresent = false;
        }
        if (!modPresent) {
            cachedPresent = false;
            return false;
        }
        try {
            Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
            cachedPresent = true;
        } catch (Throwable t) {
            cachedPresent = false;
        }
        return cachedPresent;
    }

    /** Opaque handle for an FTB Teams team. Outside callers must NOT cast. */
    public static final class TeamHandle {
        final Object delegate;
        TeamHandle(Object delegate) { this.delegate = delegate; }
        public Object raw() { return delegate; }
    }

    public static Optional<TeamHandle> getTeamForPlayer(UUID playerId) {
        if (!isInstalled() || playerId == null) return Optional.empty();
        try {
            return FtbTeamsCompatImpl.getTeamForPlayer(playerId);
        } catch (Throwable t) {
            cachedPresent = false;
            LOGGER.warn("FTB Teams API unavailable at runtime: {}", t.getMessage());
            return Optional.empty();
        }
    }

    public static Optional<TeamHandle> getTeamById(UUID teamId) {
        if (!isInstalled() || teamId == null) return Optional.empty();
        try {
            return FtbTeamsCompatImpl.getTeamById(teamId);
        } catch (Throwable t) {
            cachedPresent = false;
            return Optional.empty();
        }
    }

    public static UUID getTeamId(TeamHandle team) {
        if (team == null || !isInstalled()) return null;
        try {
            return FtbTeamsCompatImpl.getTeamId(team);
        } catch (Throwable t) {
            cachedPresent = false;
            return null;
        }
    }

    public static Collection<UUID> getTeamMembers(TeamHandle team) {
        if (team == null || !isInstalled()) return Collections.emptyList();
        try {
            return FtbTeamsCompatImpl.getTeamMembers(team);
        } catch (Throwable t) {
            cachedPresent = false;
            return Collections.emptyList();
        }
    }

    public static boolean isPartyTeam(TeamHandle team) {
        if (team == null || !isInstalled()) return false;
        try {
            return FtbTeamsCompatImpl.isPartyTeam(team);
        } catch (Throwable t) {
            cachedPresent = false;
            return false;
        }
    }

    /** Convenience: returns true iff the given player is a member of the given party-team handle. */
    public static boolean partyTeamContains(TeamHandle team, UUID playerId) {
        if (team == null || playerId == null || !isInstalled()) return false;
        try {
            if (!FtbTeamsCompatImpl.isPartyTeam(team)) return false;
            return FtbTeamsCompatImpl.getPartyMembers(team).contains(playerId);
        } catch (Throwable t) {
            cachedPresent = false;
            return false;
        }
    }

    /** Returns members of a party team, or empty if not a party team / not installed. */
    public static Set<UUID> getPartyMembers(TeamHandle team) {
        if (team == null || !isInstalled()) return Collections.emptySet();
        try {
            if (!FtbTeamsCompatImpl.isPartyTeam(team)) return Collections.emptySet();
            return FtbTeamsCompatImpl.getPartyMembers(team);
        } catch (Throwable t) {
            cachedPresent = false;
            return Collections.emptySet();
        }
    }
}
