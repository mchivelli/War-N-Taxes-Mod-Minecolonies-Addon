package net.machiavelli.minecolonytax.compat;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import com.mojang.authlib.GameProfile;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager;
import net.machiavelli.minecolonytax.besiege.BesiegeManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges Easy Factions membership onto MineColonies per-colony permissions.
 *
 * <p>The manager grants {@code Friend} (or {@code Officer}) rank to every faction
 * member on every colony owned by another faction member, and reverts those grants
 * when membership changes. Reconciliation is periodic (configurable interval) plus
 * event-driven on player login. All Easy Factions access goes through
 * {@link EasyFactionsBridge} so this manager compiles and runs cleanly without the
 * mod installed.
 *
 * <p><b>Safety rails:</b>
 * <ul>
 *   <li>Skips abandoned colonies — {@link ColonyAbandonmentManager} owns their permissions.</li>
 *   <li>Never overwrites a player at Hostile rank — wars and raids own that state.</li>
 *   <li>Never touches the colony owner.</li>
 *   <li>Only reverts grants this manager itself made (tracked in {@link #GRANTS}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EasyFactionsPermissionSync {

    private static final Logger LOGGER = LogManager.getLogger(EasyFactionsPermissionSync.class);

    /**
     * colonyId -> (playerUUID -> rank tag this manager granted).
     * Server thread is the only writer today; ConcurrentHashMap is defensive in case a
     * future event handler fires on the netty thread.
     */
    private static final Map<Integer, Map<UUID, GrantKind>> GRANTS = new ConcurrentHashMap<>();

    private static int scanTicker = 0;

    private enum GrantKind { FRIEND, OFFICER }

    private EasyFactionsPermissionSync() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        int interval = TaxConfig.getEasyFactionsSyncIntervalTicks();
        if (++scanTicker < interval) return;
        scanTicker = 0;

        if (!TaxConfig.isEasyFactionsIntegrationEnabled()) {
            if (!GRANTS.isEmpty()) {
                MinecraftServer server = event.getServer();
                revertAllGrants(server);
            }
            return;
        }

        if (!EasyFactionsBridge.isAvailable()) return;

        try {
            reconcileAll(event.getServer());
        } catch (Throwable t) {
            LOGGER.error("Easy Factions permission sync failed", t);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!TaxConfig.isEasyFactionsIntegrationEnabled()) return;
        if (!EasyFactionsBridge.isAvailable()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        try {
            reconcileForPlayer(sp.getServer(), sp.getUUID());
        } catch (Throwable t) {
            LOGGER.warn("Easy Factions sync on login failed for {}: {}", sp.getUUID(), t.toString());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GRANTS.clear();
        scanTicker = 0;
    }

    /**
     * Walk every colony, find its owner's faction (if any), grant proper ranks to
     * all faction members, and revert any stale grants we previously made.
     */
    private static void reconcileAll(MinecraftServer server) {
        if (server == null) return;
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            reconcileColony(server, colony);
        }
    }

    /**
     * Login-side reconciliation: sync this player's standing across every colony
     * owned by anyone in the player's faction. Cheaper than full scan.
     */
    private static void reconcileForPlayer(MinecraftServer server, UUID playerUUID) {
        if (server == null || playerUUID == null) return;
        EasyFactionsBridge.FactionView faction = EasyFactionsBridge.lookupForPlayer(server, playerUUID);
        if (faction == null) {
            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
                revertGrantIfStale(server, colony, playerUUID, null);
            }
            return;
        }

        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (!faction.members.contains(colony.getPermissions().getOwner())) {
                revertGrantIfStale(server, colony, playerUUID, faction);
                continue;
            }
            reconcileColony(server, colony);
        }
    }

    private static void reconcileColony(MinecraftServer server, IColony colony) {
        if (colony == null || colony.getPermissions() == null) return;
        int colonyId = colony.getID();

        if (ColonyAbandonmentManager.isColonyAbandoned(colony)) {
            // Revert any ranks we previously granted before abandoning tracking — otherwise
            // the abandonment manager inherits stale Friend/Officer entries it doesn't know about.
            revertColonyGrants(colony);
            return;
        }

        // Skip colonies whose rank state is currently owned by war/raid/besiege systems.
        // Otherwise we race their cleanup: WarSystem demotes participants to Neutral at war end,
        // and the very next tick we'd re-grant Friend — undoing the intended demotion window.
        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)
                || RaidManager.isColonyUnderRaid(colonyId)
                || BesiegeManager.isColonyBesieged(colonyId)) {
            return;
        }

        IPermissions perms = colony.getPermissions();
        UUID ownerUUID = perms.getOwner();
        if (ownerUUID == null) return;

        EasyFactionsBridge.FactionView faction = EasyFactionsBridge.lookupForPlayer(server, ownerUUID);

        Map<UUID, GrantKind> existing = GRANTS.getOrDefault(colonyId, java.util.Collections.emptyMap());
        Set<UUID> desired = new HashSet<>();

        if (faction != null) {
            for (UUID memberUUID : faction.members) {
                if (memberUUID.equals(ownerUUID)) continue;
                // Don't grant to someone who's currently a participant in any active war —
                // they may be in the post-Hostile cleanup window and we shouldn't re-promote them.
                if (isWarParticipant(memberUUID)) continue;
                GrantKind kind = resolveGrantKind(faction, memberUUID);
                if (applyGrant(server, colony, memberUUID, kind)) {
                    desired.add(memberUUID);
                }
            }
        }

        // Revert anything we previously granted that's no longer desired.
        if (!existing.isEmpty()) {
            for (UUID prev : new HashSet<>(existing.keySet())) {
                if (!desired.contains(prev)) {
                    revertGrant(colony, prev);
                }
            }
        }

        if (desired.isEmpty()) {
            GRANTS.remove(colonyId);
        }
    }

    /** True if the player is currently tracked as a participant in any active war. */
    private static boolean isWarParticipant(UUID playerUUID) {
        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            if (war == null) continue;
            if (war.getAttackerLives().containsKey(playerUUID)) return true;
            if (war.getDefenderLives().containsKey(playerUUID)) return true;
        }
        return false;
    }

    private static GrantKind resolveGrantKind(EasyFactionsBridge.FactionView faction, UUID memberUUID) {
        if (TaxConfig.shouldPromoteEasyFactionsOfficers() && faction.isOfficer(memberUUID)) {
            return GrantKind.OFFICER;
        }
        return "officer".equalsIgnoreCase(TaxConfig.getEasyFactionsMemberRank())
                ? GrantKind.OFFICER
                : GrantKind.FRIEND;
    }

    /**
     * Ensure the given player holds the proper rank on this colony. Returns true if
     * the player should be tracked as one of our grants for this colony.
     */
    private static boolean applyGrant(MinecraftServer server, IColony colony, UUID memberUUID, GrantKind kind) {
        IPermissions perms = colony.getPermissions();
        Level world = colony.getWorld();
        if (world == null) return false;

        Rank currentRank = perms.getRank(memberUUID);

        // Never override hostile rank — owned by WarSystem / RaidManager.
        if (currentRank != null && currentRank.isHostile()) {
            return false;
        }

        // Owner rank: don't touch.
        if (currentRank != null && currentRank.equals(perms.getRankOwner())) {
            return false;
        }

        Rank target = (kind == GrantKind.OFFICER) ? perms.getRankOfficer() : perms.getRankFriend();
        int colonyId = colony.getID();
        Map<UUID, GrantKind> ours = GRANTS.computeIfAbsent(colonyId, k -> new ConcurrentHashMap<>());
        GrantKind prevOurs = ours.get(memberUUID);

        if (currentRank == null) {
            // Player not in perms — add them.
            GameProfile profile = (server.getProfileCache() != null)
                    ? server.getProfileCache().get(memberUUID).orElse(null)
                    : null;
            boolean added;
            if (profile != null) {
                added = perms.addPlayer(profile, target);
            } else {
                // Fallback: addPlayer(UUID, name, rank) — synthesize a placeholder name; MC will
                // refresh it on next login. Skip silently if even this signature fails.
                added = perms.addPlayer(memberUUID, memberUUID.toString().substring(0, 8), target);
            }
            if (added) {
                ours.put(memberUUID, kind);
                return true;
            }
            return false;
        }

        // Player already at the right rank.
        if (currentRank.equals(target)) {
            if (prevOurs != kind) ours.put(memberUUID, kind);
            return true;
        }

        // Player has a rank that doesn't match `target`. Only adjust if their current rank still
        // matches what we last granted them — i.e. nobody (admin, another system) has moved them.
        // If currentRank diverges from prevOurs, the owner has manually re-ranked them; abandon
        // ownership of the grant so admin choice sticks.
        if (prevOurs != null) {
            Rank previouslyGrantedRank = (prevOurs == GrantKind.OFFICER)
                    ? perms.getRankOfficer()
                    : perms.getRankFriend();
            if (currentRank.equals(previouslyGrantedRank)) {
                if (perms.setPlayerRank(memberUUID, target, world)) {
                    ours.put(memberUUID, kind);
                    return true;
                }
                return false;
            }
            // Admin moved them off our rank — release tracking, leave them alone.
            ours.remove(memberUUID);
            return false;
        }

        // Player has a rank we don't own (admin manually configured).
        // Don't downgrade them, don't claim ownership.
        return false;
    }

    /** Demote a previously-granted player back to Neutral, respecting safety rails. */
    private static void revertGrant(IColony colony, UUID memberUUID) {
        IPermissions perms = colony.getPermissions();
        Level world = colony.getWorld();
        if (world == null) return;
        int colonyId = colony.getID();
        Map<UUID, GrantKind> ours = GRANTS.get(colonyId);
        if (ours == null) return;
        GrantKind prevOurs = ours.get(memberUUID);

        try {
            Rank currentRank = perms.getRank(memberUUID);
            // Don't touch hostile/owner — that's not ours to revert.
            if (currentRank == null
                    || currentRank.isHostile()
                    || currentRank.equals(perms.getRankOwner())) {
                return;
            }
            // Only revert if their current rank is still what we granted. If admin moved them
            // to a different rank, they own it now — leave it alone.
            if (prevOurs != null) {
                Rank previouslyGrantedRank = (prevOurs == GrantKind.OFFICER)
                        ? perms.getRankOfficer()
                        : perms.getRankFriend();
                if (!currentRank.equals(previouslyGrantedRank)) {
                    return;
                }
            }
            perms.setPlayerRank(memberUUID, perms.getRankNeutral(), world);
        } catch (Throwable t) {
            LOGGER.debug("Failed to revert EF grant for {} on colony {}: {}", memberUUID, colonyId, t.toString());
        } finally {
            ours.remove(memberUUID);
            if (ours.isEmpty()) GRANTS.remove(colonyId);
        }
    }

    private static void revertGrantIfStale(MinecraftServer server, IColony colony, UUID playerUUID,
                                           EasyFactionsBridge.FactionView currentFaction) {
        int colonyId = colony.getID();
        Map<UUID, GrantKind> ours = GRANTS.get(colonyId);
        if (ours == null || !ours.containsKey(playerUUID)) return;

        // If the player is still a faction member of this colony's owner, leave the grant.
        UUID ownerUUID = colony.getPermissions().getOwner();
        if (currentFaction != null
                && ownerUUID != null
                && currentFaction.members.contains(ownerUUID)) {
            return;
        }
        revertGrant(colony, playerUUID);
    }

    private static void revertAllGrants(MinecraftServer server) {
        if (server == null) return;
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            revertColonyGrants(colony);
        }
        GRANTS.clear();
    }

    /** Revert every grant this manager made on the given colony, then drop the colony's tracking. */
    private static void revertColonyGrants(IColony colony) {
        Map<UUID, GrantKind> ours = GRANTS.get(colony.getID());
        if (ours == null || ours.isEmpty()) {
            GRANTS.remove(colony.getID());
            return;
        }
        for (UUID uuid : new HashSet<>(ours.keySet())) {
            revertGrant(colony, uuid);
        }
    }
}
