package net.machiavelli.minecolonytax.compat;

import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pure-reflection bridge to Easy Factions (modid: {@code easy_factions}).
 *
 * <p>This class contains zero compile-time references to Easy Factions classes,
 * so WNT builds and runs cleanly whether or not Easy Factions is installed.
 * All lookups are guarded by {@link ModList#isLoaded(String)} and a one-time
 * reflective resolution. Any failure (missing class, signature drift, etc.)
 * permanently disables the bridge for the current JVM lifetime so we degrade
 * silently instead of spamming logs.
 */
public final class EasyFactionsBridge {

    private static final Logger LOGGER = LogManager.getLogger(EasyFactionsBridge.class);

    private static final String MOD_ID = "easy_factions";
    private static final String FSM_CLASS = "com.jpreiss.easy_factions.server.faction.FactionStateManager";
    private static final String FACTION_CLASS = "com.jpreiss.easy_factions.server.faction.Faction";

    private static volatile State state = State.UNRESOLVED;
    // All Method handles are volatile so that a reader observing state==READY also
    // observes fully-published Method references (broken DCL otherwise).
    private static volatile Method fsmGet;
    private static volatile Method fsmGetFactionByPlayer;
    private static volatile Method factionGetMembers;
    private static volatile Method factionGetOfficers;
    private static volatile Method factionGetOwner;
    private static volatile Method factionGetName;

    private enum State { UNRESOLVED, READY, ABSENT }

    private EasyFactionsBridge() {}

    public static boolean isAvailable() {
        if (state == State.READY) return true;
        if (state == State.ABSENT) return false;
        return resolve();
    }

    private static synchronized boolean resolve() {
        if (state != State.UNRESOLVED) return state == State.READY;

        if (!ModList.get().isLoaded(MOD_ID)) {
            state = State.ABSENT;
            return false;
        }

        try {
            Class<?> fsm = Class.forName(FSM_CLASS);
            Class<?> faction = Class.forName(FACTION_CLASS);

            fsmGet = fsm.getMethod("get", MinecraftServer.class);
            fsmGetFactionByPlayer = fsm.getMethod("getFactionByPlayer", UUID.class);
            factionGetMembers = faction.getMethod("getMembers");
            factionGetOfficers = faction.getMethod("getOfficers");
            factionGetOwner = faction.getMethod("getOwner");
            factionGetName = faction.getMethod("getName");

            state = State.READY;
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Easy Factions detected - faction/rank synchronization enabled.");
            }
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Easy Factions present but its API could not be resolved ({}). Integration disabled.", t.toString());
            state = State.ABSENT;
            return false;
        }
    }

    /**
     * Looks up the Easy Factions faction the given player belongs to.
     *
     * @return immutable snapshot, or {@code null} if the player is not in a faction
     *         or the bridge is unavailable.
     */
    public static FactionView lookupForPlayer(MinecraftServer server, UUID playerUUID) {
        if (server == null || playerUUID == null || !isAvailable()) return null;
        try {
            Object manager = fsmGet.invoke(null, server);
            if (manager == null) return null;
            Object faction = fsmGetFactionByPlayer.invoke(manager, playerUUID);
            if (faction == null) return null;

            String name = (String) factionGetName.invoke(faction);
            UUID owner = (UUID) factionGetOwner.invoke(faction);

            @SuppressWarnings("unchecked")
            Set<UUID> members = new HashSet<>((Set<UUID>) factionGetMembers.invoke(faction));
            @SuppressWarnings("unchecked")
            Set<UUID> officers = new HashSet<>((Set<UUID>) factionGetOfficers.invoke(faction));

            return new FactionView(name, owner,
                    Collections.unmodifiableSet(members),
                    Collections.unmodifiableSet(officers));
        } catch (ReflectiveOperationException | ClassCastException | LinkageError t) {
            // Signature drift or class-shape change in Easy Factions. Permanently disable.
            LOGGER.warn("Easy Factions API shape no longer matches; integration disabled: {}", t.toString());
            state = State.ABSENT;
            return null;
        } catch (Throwable t) {
            // Transient failure (e.g. unexpected runtime exception inside EF). Log and skip
            // this call, but don't permanently kill the bridge.
            LOGGER.warn("Easy Factions lookup failed for {}: {}", playerUUID, t.toString());
            return null;
        }
    }

    /** Immutable snapshot of an Easy Factions {@code Faction}. */
    public static final class FactionView {
        public final String name;
        public final UUID owner;
        public final Set<UUID> members;
        public final Set<UUID> officers;

        FactionView(String name, UUID owner, Set<UUID> members, Set<UUID> officers) {
            this.name = name;
            this.owner = owner;
            this.members = members;
            this.officers = officers;
        }

        /** True if this player has officer-level standing (owner or explicit officer). */
        public boolean isOfficer(UUID uuid) {
            return uuid != null && (uuid.equals(owner) || officers.contains(uuid));
        }
    }
}
