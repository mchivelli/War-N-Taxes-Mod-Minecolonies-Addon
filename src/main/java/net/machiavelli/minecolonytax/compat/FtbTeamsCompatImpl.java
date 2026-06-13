package net.machiavelli.minecolonytax.compat;

import dev.ftb.mods.ftbteams.FTBTeamsAPIImpl;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.data.PartyTeam;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Internal implementation class that actually touches FTB Teams types.
 *
 * <p>This class MUST NOT be referenced from anywhere except
 * {@link FtbTeamsCompat}. The JVM only loads it the first time one of its
 * static methods is called — by that time the outer wrapper has already
 * verified FTB Teams is installed.
 *
 * <p><b>NeoForge 1.21.1 port note:</b> the FTB Teams NeoForge 1.21.1 API uses
 * the SAME package paths as the Forge build
 * ({@code dev.ftb.mods.ftbteams.FTBTeamsAPIImpl},
 * {@code dev.ftb.mods.ftbteams.api.Team / .api.TeamManager},
 * {@code dev.ftb.mods.ftbteams.data.PartyTeam}). FTB Teams is a real compile
 * dependency in this repo's build.gradle (curse.maven:ftb-teams-forge-404468),
 * and {@code WarSystem} already imports these exact types — so this file ports
 * across unchanged.
 */
final class FtbTeamsCompatImpl {

    private FtbTeamsCompatImpl() {}

    private static TeamManager manager() {
        try {
            return FTBTeamsAPIImpl.INSTANCE.getManager();
        } catch (Throwable t) {
            // Some legacy/parallel ftbteams builds expose the manager differently.
            // Fall through to null — callers null-check.
            return null;
        }
    }

    static Optional<FtbTeamsCompat.TeamHandle> getTeamForPlayer(UUID playerId) {
        TeamManager m = manager();
        if (m == null) return Optional.empty();
        // getTeamForPlayerID and getPlayerTeamForPlayerID both exist in some
        // FTB Teams API versions; prefer the more common one but fall back if
        // it throws (e.g. AbstractMethodError on a slightly-mismatched build).
        Optional<Team> opt;
        try {
            opt = m.getTeamForPlayerID(playerId);
        } catch (Throwable t) {
            try {
                opt = m.getPlayerTeamForPlayerID(playerId);
            } catch (Throwable t2) {
                return Optional.empty();
            }
        }
        return opt.map(t -> new FtbTeamsCompat.TeamHandle(t));
    }

    static Optional<FtbTeamsCompat.TeamHandle> getTeamById(UUID teamId) {
        TeamManager m = manager();
        if (m == null) return Optional.empty();
        Optional<Team> opt = m.getTeamByID(teamId);
        return opt.map(t -> new FtbTeamsCompat.TeamHandle(t));
    }

    static UUID getTeamId(FtbTeamsCompat.TeamHandle handle) {
        return ((Team) handle.raw()).getId();
    }

    static Collection<UUID> getTeamMembers(FtbTeamsCompat.TeamHandle handle) {
        Team t = (Team) handle.raw();
        Collection<UUID> members = t.getMembers();
        return members != null ? members : Collections.emptyList();
    }

    static boolean isPartyTeam(FtbTeamsCompat.TeamHandle handle) {
        Team t = (Team) handle.raw();
        return t.isPartyTeam();
    }

    static Set<UUID> getPartyMembers(FtbTeamsCompat.TeamHandle handle) {
        Team t = (Team) handle.raw();
        if (!t.isPartyTeam()) return Collections.emptySet();
        Collection<UUID> members = ((PartyTeam) t).getMembers();
        if (members == null) return Collections.emptySet();
        return new java.util.HashSet<>(members);
    }
}
