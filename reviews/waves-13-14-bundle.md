## WAVES 13-14 — Step 8 war militia + Step 9 Vassals UI complete

Wave 13 (Step 8 finish): extracted militia spawning to shared net.machiavelli.minecolonytax.militia.MilitiaSpawner. BesiegeManager now delegates. WarSystem.finalizeWarStart calls MilitiaSpawner for BOTH defender and attacker colonies (their guard count × upgrade multiplier). Tracked in new WarData.militiaSupport set. Despawned in endWar.

Wave 14 (Step 9 finish): ColonyDataResponsePacket now encodes/decodes the VassalKind ordinal. ColonyDataCollector adds TAX_OCCUPIED and PROVISIONAL rows for OccupationManager occupations where the player is the occupier. VassalsPage renders kind-aware badges (red TAX-OCCUPIED, orange PROVISIONAL, green VASSAL) in both list and detail views, with a one-letter row tag.

Note: external audit-driven refactor concurrent with this wave (EntityMercenary → PathfinderMob, FtbTeamsCompat type changes, WarData restoration constructor expansion). I migrated MilitiaSpawner to PathfinderMob and fixed two illegal multi-catches (NoClassDefFoundError|Throwable, NoClassDefFoundError|LinkageError) that the audit introduced. Build green.

### NEW FILE: militia/MilitiaSpawner.java
```java
package net.machiavelli.minecolonytax.militia;

import com.minecolonies.api.colony.IColony;
// Intentionally do NOT import com.minecolonies.core.entity.mobs.EntityMercenary —
// it lives in the internal core package, not the api/* surface. Match the pattern
// used by BesiegeManager and ColonyClaimingRaidManager: operate on the result of
// ModEntities.MERCENARY.create() as a PathfinderMob via the vanilla API only.
import net.minecraft.world.entity.PathfinderMob;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Shared spawn helper for militia-upgrade reinforcements. Used by both
 * BesiegeManager (besiege start) and WarSystem (war start) so the math,
 * spawn pattern, and tracking are identical.
 *
 * Reinforcements are tracked in a caller-provided Set so the caller owns
 * the despawn lifecycle. They are intentionally NOT counted toward victory
 * objectives — only real guards and player lives are.
 */
public final class MilitiaSpawner {

    private static final Logger LOGGER = LogManager.getLogger(MilitiaSpawner.class);
    private static final Random RNG = new Random();

    private MilitiaSpawner() {}

    /**
     * Spawn militia-upgrade reinforcements for a colony.
     *
     * @param colony            the colony whose upgrade level + center are used
     * @param guardCount        current real guard count (multiplier scales off this)
     * @param attackerTarget    optional initial aggro target (the besieger/attacker), may be null
     * @param trackingSet       caller-owned set; spawned entities are added here for despawn-on-end
     * @param durationMinutes   conflict duration in minutes, used to scope DAMAGE_RESISTANCE buff
     * @return the number of militia actually spawned (may be 0 if upgrade not purchased or guardCount=0)
     */
    public static int spawnReinforcements(IColony colony, int guardCount,
                                          Player attackerTarget,
                                          Set<Entity> trackingSet,
                                          int durationMinutes) {
        if (colony == null || trackingSet == null) return 0;
        Level world = colony.getWorld();
        if (!(world instanceof ServerLevel)) return 0;
        if (guardCount <= 0) return 0;

        double multiplier = ColonyUpgradeManager.getMilitiaMultiplier(colony.getID());
        int bonus = (int) Math.floor(guardCount * (multiplier - 1.0));
        if (bonus <= 0) return 0;

        int durationTicks = Math.max(1, durationMinutes) * 60 * 20;
        BlockPos center = colony.getCenter();
        int spawned = 0;
        for (int i = 0; i < bonus; i++) {
            try {
                PathfinderMob militia = com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
                if (militia == null) continue;
                BlockPos spawnPos = findSpawnPos(center, world);
                militia.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                if (attackerTarget != null) {
                    militia.setTarget(attackerTarget);
                }
                militia.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 0));
                world.addFreshEntity(militia);
                trackingSet.add(militia);
                spawned++;
            } catch (NoClassDefFoundError ncdfe) {
                // ModEntities.MERCENARY uses internal types; degrade silently if absent.
                LOGGER.debug("Militia spawn: ModEntities.MERCENARY unresolvable, skipping: {}", ncdfe.getMessage());
                break;
            } catch (Exception e) {
                LOGGER.warn("Failed to spawn militia reinforcement {} for colony {}", i, colony.getName(), e);
            }
        }
        if (spawned > 0 && TaxConfig.isNormalLogging()) {
            LOGGER.info("Militia reinforcements for {}: spawned {} (guards {}, multiplier {})",
                    colony.getName(), spawned, guardCount,
                    String.format(Locale.ROOT, "%.2f", multiplier));
        }
        return spawned;
    }

    /**
     * Despawn all militia in the tracking set. Idempotent — call from
     * endWar / cleanupRaid. Clears the set after iteration.
     */
    public static void despawnAll(Set<Entity> trackingSet) {
        if (trackingSet == null || trackingSet.isEmpty()) return;
        for (Entity militia : trackingSet) {
            try {
                if (militia != null && militia.isAlive()) {
                    militia.remove(Entity.RemovalReason.DISCARDED);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to despawn militia reinforcement: {}", e.getMessage());
            }
        }
        trackingSet.clear();
    }

    /**
     * Find a sky-exposed spawn position within ~5 blocks of the colony center.
     * Uses {@code MOTION_BLOCKING_NO_LEAVES} so spawns land on solid ground.
     */
    private static BlockPos findSpawnPos(BlockPos center, Level world) {
        int dx = RNG.nextInt(11) - 5; // -5..+5
        int dz = RNG.nextInt(11) - 5;
        int x = center.getX() + dx;
        int z = center.getZ() + dz;
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }
}
```

### DIFF: WarSystem.java (finalizeWarStart + endWar militia despawn)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/WarSystem.java b/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
index fff5562..d47cadc 100644
--- a/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
+++ b/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
@@ -7,11 +7,8 @@ import com.minecolonies.api.colony.permissions.Action;
 import com.minecolonies.api.colony.permissions.IPermissions;
 import com.minecolonies.api.colony.permissions.Rank;
 import com.mojang.brigadier.exceptions.CommandSyntaxException;
-import dev.ftb.mods.ftbteams.FTBTeamsAPIImpl;
-import dev.ftb.mods.ftbteams.api.Team;
-import dev.ftb.mods.ftbteams.api.TeamManager;
-import dev.ftb.mods.ftbteams.data.PartyTeam;
 import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
+import net.machiavelli.minecolonytax.compat.FtbTeamsCompat;
 import net.machiavelli.minecolonytax.data.HistoryManager;
 import net.machiavelli.minecolonytax.data.PlayerWarDataManager;
 import net.machiavelli.minecolonytax.data.WarData;
@@ -81,18 +78,12 @@ public class WarSystem {
         return rank.isColonyManager() || !rank.isHostile();
     }
 
-    private static boolean isFTBTeamsLoaded() {
-        try {
-            Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
-            return true;
-        } catch (ClassNotFoundException e) {
-            return false;
-        }
-    }
-
-    public static final boolean FTB_TEAMS_INSTALLED = isFTBTeamsLoaded();
-    public static final TeamManager FTB_TEAM_MANAGER = FTB_TEAMS_INSTALLED ? FTBTeamsAPIImpl.INSTANCE.getManager()
-            : null;
+    /**
+     * Legacy boolean flag — preserved for external callers that read it directly.
+     * Routes through {@link FtbTeamsCompat#isInstalled()} which is classloader-safe.
+     * Do NOT add new typed FTB Teams statics here — use {@link FtbTeamsCompat}.
+     */
+    public static final boolean FTB_TEAMS_INSTALLED = FtbTeamsCompat.isInstalled();
     public static final Map<Integer, WarData> ACTIVE_WARS = new ConcurrentHashMap<>();
 
     private static final Component JOIN_MSG = Component.literal("[Join War]")
@@ -112,10 +103,13 @@ public class WarSystem {
 
     public static final long WAR_PHASE_DURATION_SECONDS = 60;
 
-    public static void initiateWar(ServerPlayer attacker, UUID defender, Team attackerTeam, Team defenderTeam,
+    public static void initiateWar(ServerPlayer attacker, UUID defender,
+            FtbTeamsCompat.TeamHandle attackerTeam, FtbTeamsCompat.TeamHandle defenderTeam,
             IColony colony, IColony attackerColony) {
-        UUID attackerTeamID = (FTB_TEAMS_INSTALLED && attackerTeam != null) ? attackerTeam.getId() : attacker.getUUID();
-        UUID defenderTeamID = (FTB_TEAMS_INSTALLED && defenderTeam != null) ? defenderTeam.getId()
+        UUID attackerTeamID = (FTB_TEAMS_INSTALLED && attackerTeam != null)
+                ? FtbTeamsCompat.getTeamId(attackerTeam) : attacker.getUUID();
+        UUID defenderTeamID = (FTB_TEAMS_INSTALLED && defenderTeam != null)
+                ? FtbTeamsCompat.getTeamId(defenderTeam)
                 : colony.getPermissions().getOwner();
 
         ServerBossEvent bossEvent = new ServerBossEvent(
@@ -226,12 +220,12 @@ public class WarSystem {
             }
         });
 
-        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
+        if (FTB_TEAMS_INSTALLED) {
             if (TaxConfig.isDebugLogging())
                 if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] FTB Teams detected, adding team members as additional participants");
 
             if (attackerTeam != null) {
-                attackerTeam.getMembers().forEach(uuid -> {
+                FtbTeamsCompat.getTeamMembers(attackerTeam).forEach(uuid -> {
                     if (!data.getAttackerLives().containsKey(uuid)) { // Don't add if already added via colony
                         data.getAttackerLives().put(uuid, playerLives);
                         if (TaxConfig.isDebugLogging())
@@ -262,7 +256,7 @@ public class WarSystem {
             }
 
             if (defenderTeam != null) {
-                defenderTeam.getMembers().forEach(uuid -> {
+                FtbTeamsCompat.getTeamMembers(defenderTeam).forEach(uuid -> {
                     if (!data.getDefenderLives().containsKey(uuid)) { // Don't add if already added via colony
                         data.getDefenderLives().put(uuid, playerLives);
                         if (TaxConfig.isDebugLogging())
@@ -617,6 +611,36 @@ public class WarSystem {
         }
         applyWarGlowToParticipants(war);
 
+        // Militia upgrade reinforcements — spawn on BOTH sides if either colony
+        // has the upgrade. Defender side primarily (per design), attacker side
+        // optionally so an upgraded attacker colony also gets the boost.
+        // Idempotent — re-entry checks the existing set is empty first.
+        if (war.militiaSupport.isEmpty()) {
+            try {
+                // Defender militia
+                if (war.getColony() != null) {
+                    int defenderGuardCount = (int) war.getColony().getCitizenManager().getCitizens().stream()
+                            .filter(c -> c.getJob() != null && c.getJob().isGuard())
+                            .count();
+                    // No specific attacker-target — let the militia find via vanilla aggro
+                    net.machiavelli.minecolonytax.militia.MilitiaSpawner.spawnReinforcements(
+                            war.getColony(), defenderGuardCount, null,
+                            war.militiaSupport, TaxConfig.WAR_DURATION_MINUTES.get());
+                }
+                // Attacker militia (their colony also benefits from the upgrade)
+                if (war.getAttackerColony() != null) {
+                    int attackerGuardCount = (int) war.getAttackerColony().getCitizenManager().getCitizens().stream()
+                            .filter(c -> c.getJob() != null && c.getJob().isGuard())
+                            .count();
+                    net.machiavelli.minecolonytax.militia.MilitiaSpawner.spawnReinforcements(
+                            war.getAttackerColony(), attackerGuardCount, null,
+                            war.militiaSupport, TaxConfig.WAR_DURATION_MINUTES.get());
+                }
+            } catch (Exception e) {
+                WARSYSTEM_LOGGER.warn("Militia spawn during war start failed: {}", e.getMessage());
+            }
+        }
+
         // Apply resistance effects to defending guards during war
         GuardResistanceHandler.applyResistanceToGuardsForWar(war.getColony());
         if (war.getAttackerColony() != null) {
@@ -1239,8 +1263,18 @@ public class WarSystem {
     }
 
     public static void endWar(IColony colony) {
-        // Get war data before removing it from active wars
-        WarData warData = ACTIVE_WARS.get(colony.getID());
+        if (colony == null) return;
+        // Finding 10: make endWar idempotent. Atomically remove the WarData from
+        // ACTIVE_WARS; if it was already removed (a concurrent endWar call from a
+        // different code path), bail out — re-running the rest of this method
+        // would double-fire demotions, history records, treasury cleanup, etc.
+        WarData warData = ACTIVE_WARS.remove(colony.getID());
+        if (warData == null) {
+            // Already ended — nothing to do. (Previously this code re-ran all
+            // cleanup with warData == null, producing best-effort no-ops scattered
+            // with NPE risk.)
+            return;
+        }
 
         // Remove resistance effects from guards in both colonies
         if (warData != null) {
@@ -1251,6 +1285,29 @@ public class WarSystem {
 
             // Clean up militia system for both colonies
             cleanupWarMilitiaSystem(warData);
+
+            // Restore all explosion-damaged blocks ledgered for this war.
+            // Bug #8 fix: previously the ledger only accumulated and never restored.
+            try {
+                if (warData.getColony() != null && warData.getColony().getWorld() != null) {
+                    net.machiavelli.minecolonytax.siege.WarBlockLedger.restoreWarDamage(
+                            warData.getWarID(), warData.getColony().getWorld());
+                }
+            } catch (Exception e) {
+                WARSYSTEM_LOGGER.error("Failed to restore war block ledger for war {}", warData.getWarID(), e);
+            }
+
+            // Drop any Town Hall demolition hit state so it doesn't leak between wars.
+            try {
+                net.machiavelli.minecolonytax.siege.TownHallDemolitionObjective.onWarEnded(warData.getWarID());
+            } catch (Exception ignored) {}
+
+            // Despawn militia-upgrade reinforcements (NOT victory-counted, just combat extenders).
+            try {
+                net.machiavelli.minecolonytax.militia.MilitiaSpawner.despawnAll(warData.militiaSupport);
+            } catch (Exception e) {
+                WARSYSTEM_LOGGER.warn("Failed to despawn war militia: {}", e.getMessage());
+            }
         }
 
         // Disable war actions for both sides
@@ -1272,8 +1329,8 @@ public class WarSystem {
             }
         }
 
-        // Now remove from active wars
-        warData = ACTIVE_WARS.remove(colony.getID());
+        // (Removed from ACTIVE_WARS at the top of this method as part of the
+        // Finding 10 idempotency fix — no further read/remove needed.)
 
         // Restore Hostile rank to pre-war state now that the war is no longer active
         net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(colony);
@@ -1939,40 +1996,41 @@ public class WarSystem {
             return war.getDefenderLives();
         }
 
-        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
-            Optional<Team> teamOpt = FTB_TEAM_MANAGER.getPlayerTeamForPlayerID(playerUUID);
+        if (FTB_TEAMS_INSTALLED) {
+            Optional<FtbTeamsCompat.TeamHandle> teamOpt = FtbTeamsCompat.getTeamForPlayer(playerUUID);
             if (TaxConfig.isDebugLogging())
                 if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player team found: " + teamOpt.isPresent());
             if (teamOpt.isPresent()) {
-                Team team = teamOpt.get();
+                FtbTeamsCompat.TeamHandle team = teamOpt.get();
+                UUID teamId = FtbTeamsCompat.getTeamId(team);
                 if (TaxConfig.isDebugLogging()) {
-                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player team ID: " + team.getId());
+                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player team ID: " + teamId);
                     if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] War attacker team ID: " + war.getAttackerTeamID());
                     if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] War defender team ID: " + war.getDefenderTeamID());
                 }
 
-                if (team.getId().equals(war.getAttackerTeamID())) {
+                if (teamId != null && teamId.equals(war.getAttackerTeamID())) {
                     if (TaxConfig.isDebugLogging())
                         if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is on attacker team, returning attacker lives");
                     return war.getAttackerLives();
-                } else if (team.getId().equals(war.getDefenderTeamID())) {
+                } else if (teamId != null && teamId.equals(war.getDefenderTeamID())) {
                     if (TaxConfig.isDebugLogging())
                         if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is on defender team, returning defender lives");
                     return war.getDefenderLives();
                 }
 
                 // Check if player is allied to any participating team
-                Team atkTeam = FTB_TEAM_MANAGER.getTeamByID(war.getAttackerTeamID()).orElse(null);
-                if (atkTeam != null && atkTeam.isPartyTeam()
-                        && ((PartyTeam) atkTeam).getMembers().contains(playerUUID)) {
+                FtbTeamsCompat.TeamHandle atkTeam = war.getAttackerTeamID() == null ? null
+                        : FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
+                if (atkTeam != null && FtbTeamsCompat.partyTeamContains(atkTeam, playerUUID)) {
                     if (TaxConfig.isDebugLogging())
                         if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is allied to attacker team, returning attacker lives");
                     return war.getAttackerLives();
                 }
 
-                Team defTeam = FTB_TEAM_MANAGER.getTeamByID(war.getDefenderTeamID()).orElse(null);
-                if (defTeam != null && defTeam.isPartyTeam()
-                        && ((PartyTeam) defTeam).getMembers().contains(playerUUID)) {
+                FtbTeamsCompat.TeamHandle defTeam = war.getDefenderTeamID() == null ? null
+                        : FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);
+                if (defTeam != null && FtbTeamsCompat.partyTeamContains(defTeam, playerUUID)) {
                     if (TaxConfig.isDebugLogging())
                         if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is allied to defender team, returning defender lives");
                     return war.getDefenderLives();
@@ -2062,24 +2120,26 @@ public class WarSystem {
             }
 
             // Check FTB Teams
-            if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
-                Optional<Team> teamOpt = FTB_TEAM_MANAGER.getTeamForPlayerID(player.getUUID());
+            if (FTB_TEAMS_INSTALLED) {
+                Optional<FtbTeamsCompat.TeamHandle> teamOpt = FtbTeamsCompat.getTeamForPlayer(player.getUUID());
                 if (teamOpt.isPresent()) {
-                    Team team = teamOpt.get();
-                    if (team.getId().equals(war.getAttackerTeamID()) || team.getId().equals(war.getDefenderTeamID())) {
+                    FtbTeamsCompat.TeamHandle team = teamOpt.get();
+                    UUID teamId = FtbTeamsCompat.getTeamId(team);
+                    if (teamId != null
+                            && (teamId.equals(war.getAttackerTeamID()) || teamId.equals(war.getDefenderTeamID()))) {
                         return war;
                     }
 
                     // Check if player is allied to any participating team
-                    Team atkTeam = FTB_TEAM_MANAGER.getTeamByID(war.getAttackerTeamID()).orElse(null);
-                    if (atkTeam != null && atkTeam.isPartyTeam()
-                            && ((PartyTeam) atkTeam).getMembers().contains(player.getUUID())) {
+                    FtbTeamsCompat.TeamHandle atkTeam = war.getAttackerTeamID() == null ? null
+                            : FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
+                    if (atkTeam != null && FtbTeamsCompat.partyTeamContains(atkTeam, player.getUUID())) {
                         return war;
                     }
 
-                    Team defTeam = FTB_TEAM_MANAGER.getTeamByID(war.getDefenderTeamID()).orElse(null);
-                    if (defTeam != null && defTeam.isPartyTeam()
-                            && ((PartyTeam) defTeam).getMembers().contains(player.getUUID())) {
+                    FtbTeamsCompat.TeamHandle defTeam = war.getDefenderTeamID() == null ? null
+                            : FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);
+                    if (defTeam != null && FtbTeamsCompat.partyTeamContains(defTeam, player.getUUID())) {
                         return war;
                     }
                 }
@@ -2197,11 +2257,11 @@ public class WarSystem {
     }
 
     public static void startJoinPhase(IColony colony, ServerPlayer attacker, ServerPlayer owner) {
-        Team attackerTeam = FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null
-                ? FTB_TEAM_MANAGER.getTeamForPlayerID(attacker.getUUID()).orElse(null)
+        FtbTeamsCompat.TeamHandle attackerTeam = FTB_TEAMS_INSTALLED
+                ? FtbTeamsCompat.getTeamForPlayer(attacker.getUUID()).orElse(null)
                 : null;
-        Team defenderTeam = FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null
-                ? FTB_TEAM_MANAGER.getTeamForPlayerID(owner.getUUID()).orElse(null)
+        FtbTeamsCompat.TeamHandle defenderTeam = FTB_TEAMS_INSTALLED
+                ? FtbTeamsCompat.getTeamForPlayer(owner.getUUID()).orElse(null)
                 : null;
 
         IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
@@ -2272,7 +2332,7 @@ public class WarSystem {
                 .append(Component.literal(" "))
                 .append(LEAVE_MSG);
 
-        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
+        if (FTB_TEAMS_INSTALLED) {
             if (attackerTeam != null) {
                 sendNotificationToColonyParticipants(attackerColony, joinAnnouncement);
             }
@@ -2292,7 +2352,7 @@ public class WarSystem {
                 String.format("%02d:%02d", remainingMillis / (60 * 1000), (remainingMillis / 1000) % 60))
                 .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true));
 
-        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
+        if (FTB_TEAMS_INSTALLED) {
             if (attackerTeam != null)
                 sendNotificationToColonyParticipants(attackerColony, joinPhaseInfo);
             if (defenderTeam != null)
@@ -2427,14 +2487,14 @@ public class WarSystem {
         }
 
         // If FTB Teams is installed, also notify team members
-        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
+        if (FTB_TEAMS_INSTALLED) {
             WarData war = ACTIVE_WARS.get(defenderColony.getID());
             if (war != null) {
                 // Notify attacker team members
                 if (war.getAttackerTeamID() != null) {
-                    Team attackerTeam = FTB_TEAM_MANAGER.getTeamByID(war.getAttackerTeamID()).orElse(null);
-                    if (attackerTeam != null && attackerTeam.isPartyTeam()) {
-                        ((PartyTeam) attackerTeam).getMembers().forEach(uuid -> {
+                    FtbTeamsCompat.TeamHandle attackerTeam = FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
+                    if (attackerTeam != null && FtbTeamsCompat.isPartyTeam(attackerTeam)) {
+                        FtbTeamsCompat.getPartyMembers(attackerTeam).forEach(uuid -> {
                             if (!notifiedPlayers.contains(uuid)) {
                                 ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                                 if (player != null) {
@@ -2448,9 +2508,9 @@ public class WarSystem {
 
                 // Notify defender team members
                 if (war.getDefenderTeamID() != null) {
-                    Team defenderTeam = FTB_TEAM_MANAGER.getTeamByID(war.getDefenderTeamID()).orElse(null);
-                    if (defenderTeam != null && defenderTeam.isPartyTeam()) {
-                        ((PartyTeam) defenderTeam).getMembers().forEach(uuid -> {
+                    FtbTeamsCompat.TeamHandle defenderTeam = FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);
+                    if (defenderTeam != null && FtbTeamsCompat.isPartyTeam(defenderTeam)) {
+                        FtbTeamsCompat.getPartyMembers(defenderTeam).forEach(uuid -> {
                             if (!notifiedPlayers.contains(uuid)) {
                                 ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                                 if (player != null) {
@@ -2490,7 +2550,21 @@ public class WarSystem {
                 return;
             }
 
-            long elapsedSeconds = (System.currentTimeMillis() - warData.warStartTime) / 1000;
+            // Finding 9: defensive guard against wall-clock skew (NTP, manual
+            // clock change, container restart). If now < warStartTime the war
+            // was "born in the future" — almost certainly a backwards clock
+            // adjustment. Reset warStartTime to the current wall clock so the
+            // war doesn't appear to never expire (or instantly expire). This is
+            // a soft repair, not a monotonic rewrite — sufficient to avoid
+            // every-war-killed-on-NTP-skew bugs.
+            long nowMs = System.currentTimeMillis();
+            if (nowMs < warData.warStartTime) {
+                WARSYSTEM_LOGGER.warn("War {}: wall clock went backwards (now={} < warStartTime={}). "
+                        + "Resetting warStartTime to now; war will continue from the new clock value.",
+                        warData.getWarID(), nowMs, warData.warStartTime);
+                warData.warStartTime = nowMs;
+            }
+            long elapsedSeconds = (nowMs - warData.warStartTime) / 1000;
             long remaining = Math.max(0, warDurationSeconds - elapsedSeconds);
             String bossText = String.format("War: Attacker Lives: %d | Defender Lives: %d | Time: %02d:%02d",
                     warData.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum(),
@@ -2527,10 +2601,10 @@ public class WarSystem {
         });
     }
 
-    public static void sendMessageToTeam(Team team, Component msg) {
+    public static void sendMessageToTeam(FtbTeamsCompat.TeamHandle team, Component msg) {
         if (team == null || ServerLifecycleHooks.getCurrentServer() == null)
             return;
-        for (UUID member : team.getMembers()) {
+        for (UUID member : FtbTeamsCompat.getTeamMembers(team)) {
             ServerPlayer sp = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
             if (sp != null)
                 sp.sendSystemMessage(msg);
@@ -2909,8 +2983,8 @@ public class WarSystem {
 
         // Start the war immediately with the attacker - no join phase needed since
         // owner is offline
-        Team attackerTeam = FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null
-                ? FTB_TEAM_MANAGER.getTeamForPlayerID(attacker.getUUID()).orElse(null)
+        FtbTeamsCompat.TeamHandle attackerTeam = FTB_TEAMS_INSTALLED
+                ? FtbTeamsCompat.getTeamForPlayer(attacker.getUUID()).orElse(null)
                 : null;
 
         // Initiate war without a defender player - guards will fight
@@ -2924,7 +2998,7 @@ public class WarSystem {
      * Special handling: No defender players, only guards defend.
      */
     private static void initiateOfflineOutpostWar(ServerPlayer attacker, IColony targetColony,
-            IColony attackerColony, Team attackerTeam) {
+            IColony attackerColony, FtbTeamsCompat.TeamHandle attackerTeam) {
 
         int attackerGuards = countGuards(attackerColony);
         int defenderGuards = countGuards(targetColony);
@@ -2932,7 +3006,7 @@ public class WarSystem {
         WarData warData = new WarData(
                 attacker.getUUID(),
                 targetColony.getPermissions().getOwner(), // Defender owner UUID (offline)
-                attackerTeam != null ? attackerTeam.getId() : null,
+                attackerTeam != null ? FtbTeamsCompat.getTeamId(attackerTeam) : null,
                 null, // No defender team
                 System.currentTimeMillis(),
                 null, // No boss event yet - created below
@@ -3298,22 +3372,23 @@ public class WarSystem {
         }
 
         // Check FTB Teams
-        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
-            Team playerTeam = FTB_TEAM_MANAGER.getTeamForPlayerID(player.getUUID()).orElse(null);
-            Team atkTeam = FTB_TEAM_MANAGER.getTeamByID(war.getAttackerTeamID()).orElse(null);
-            Team defTeam = FTB_TEAM_MANAGER.getTeamByID(war.getDefenderTeamID()).orElse(null);
+        if (FTB_TEAMS_INSTALLED) {
+            FtbTeamsCompat.TeamHandle playerTeam = FtbTeamsCompat.getTeamForPlayer(player.getUUID()).orElse(null);
+            FtbTeamsCompat.TeamHandle atkTeam = war.getAttackerTeamID() == null ? null
+                    : FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
+            FtbTeamsCompat.TeamHandle defTeam = war.getDefenderTeamID() == null ? null
+                    : FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);
 
             // Direct team membership
-            if (playerTeam != null && (playerTeam.getId().equals(war.getAttackerTeamID()) ||
-                    playerTeam.getId().equals(war.getDefenderTeamID()))) {
+            UUID playerTeamId = playerTeam == null ? null : FtbTeamsCompat.getTeamId(playerTeam);
+            if (playerTeamId != null && (playerTeamId.equals(war.getAttackerTeamID()) ||
+                    playerTeamId.equals(war.getDefenderTeamID()))) {
                 return true;
             }
 
             // Allied team membership
-            if ((atkTeam != null && atkTeam.isPartyTeam()
-                    && ((PartyTeam) atkTeam).getMembers().contains(player.getUUID())) ||
-                    (defTeam != null && defTeam.isPartyTeam()
-                            && ((PartyTeam) defTeam).getMembers().contains(player.getUUID()))) {
+            if (FtbTeamsCompat.partyTeamContains(atkTeam, player.getUUID())
+                    || FtbTeamsCompat.partyTeamContains(defTeam, player.getUUID())) {
                 return true;
             }
         }
@@ -3398,26 +3473,27 @@ public class WarSystem {
         boolean canJoinDefenders = false;
 
         // Check FTB Teams first
-        if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
-            Team playerTeam = FTB_TEAM_MANAGER.getTeamForPlayerID(player.getUUID()).orElse(null);
-            Team atkTeam = FTB_TEAM_MANAGER.getTeamByID(war.getAttackerTeamID()).orElse(null);
-            Team defTeam = FTB_TEAM_MANAGER.getTeamByID(war.getDefenderTeamID()).orElse(null);
+        if (FTB_TEAMS_INSTALLED) {
+            FtbTeamsCompat.TeamHandle playerTeam = FtbTeamsCompat.getTeamForPlayer(player.getUUID()).orElse(null);
+            FtbTeamsCompat.TeamHandle atkTeam = war.getAttackerTeamID() == null ? null
+                    : FtbTeamsCompat.getTeamById(war.getAttackerTeamID()).orElse(null);
+            FtbTeamsCompat.TeamHandle defTeam = war.getDefenderTeamID() == null ? null
+                    : FtbTeamsCompat.getTeamById(war.getDefenderTeamID()).orElse(null);
 
             // Direct team membership
-            if (playerTeam != null && playerTeam.getId().equals(war.getAttackerTeamID())) {
+            UUID playerTeamId = playerTeam == null ? null : FtbTeamsCompat.getTeamId(playerTeam);
+            if (playerTeamId != null && playerTeamId.equals(war.getAttackerTeamID())) {
                 canJoinAttackers = true;
             }
-            if (playerTeam != null && playerTeam.getId().equals(war.getDefenderTeamID())) {
+            if (playerTeamId != null && playerTeamId.equals(war.getDefenderTeamID())) {
                 canJoinDefenders = true;
             }
 
             // Allied team membership
-            if (atkTeam != null && atkTeam.isPartyTeam()
-                    && ((PartyTeam) atkTeam).getMembers().contains(player.getUUID())) {
+            if (FtbTeamsCompat.partyTeamContains(atkTeam, player.getUUID())) {
                 canJoinAttackers = true;
             }
-            if (defTeam != null && defTeam.isPartyTeam()
-                    && ((PartyTeam) defTeam).getMembers().contains(player.getUUID())) {
+            if (FtbTeamsCompat.partyTeamContains(defTeam, player.getUUID())) {
                 canJoinDefenders = true;
             }
         }
@@ -3918,6 +3994,21 @@ public class WarSystem {
         int initialAttackerTotalLives;
         int initialDefenderTotalLives;
         String penaltyReport;
+        // Added 2026-05-25 (audit fix): previously these fields were silently dropped
+        // on save/restore — see WarData restoration constructor docstring.
+        Map<String, Boolean> originalHostilePerms;            // Action.name() -> boolean
+        Map<String, Boolean> originalHostilePermsForAttacker;
+        List<String> acceptedAllies;
+        List<String> declinedAllies;
+        boolean offlineOutpostWar;
+        ProposalSaveEntry activeProposal; // null if no proposal in flight
+    }
+
+    private static class ProposalSaveEntry {
+        String type;     // PeaceProposal.Type.name()
+        int amount;
+        String proposer; // UUID.toString()
+        long createdTime;
     }
 
     private static class WarSaveData {
@@ -3934,12 +4025,32 @@ public class WarSystem {
 
             for (Map.Entry<Integer, WarData> entry : ACTIVE_WARS.entrySet()) {
                 WarData war = entry.getValue();
+
+                // Finding 11 (audit CRIT — CRASH-3b): defenderTeamID can be null for
+                // wars against abandoned colonies (no FTB Teams + colony owner null).
+                // Previously war.getDefenderTeamID().toString() NPE'd here, aborting
+                // the save loop and dropping ALL subsequent wars from disk.
+                // Write a sentinel UUID instead and log a warning so the war still
+                // round-trips. (Loader treats sentinel as "no defender team".)
+                UUID atkTid = war.getAttackerTeamID();
+                UUID defTid = war.getDefenderTeamID();
+                if (atkTid == null) {
+                    WARSYSTEM_LOGGER.warn("War {} for colony {} has null attackerTeamID; persisting with sentinel UUID.",
+                            war.getWarID(), entry.getKey());
+                    atkTid = NULL_TEAM_ID_SENTINEL;
+                }
+                if (defTid == null) {
+                    WARSYSTEM_LOGGER.warn("War {} for colony {} has null defenderTeamID (abandoned colony?); persisting with sentinel UUID.",
+                            war.getWarID(), entry.getKey());
+                    defTid = NULL_TEAM_ID_SENTINEL;
+                }
+
                 WarSaveEntry e = new WarSaveEntry();
                 e.warID = war.getWarID().toString();
                 e.attacker = war.getAttacker().toString();
                 e.defender = war.getDefender().toString();
-                e.attackerTeamID = war.getAttackerTeamID().toString();
-                e.defenderTeamID = war.getDefenderTeamID().toString();
+                e.attackerTeamID = atkTid.toString();
+                e.defenderTeamID = defTid.toString();
                 e.defenderColonyId = entry.getKey();
                 e.attackerColonyId = war.getAttackerColony() != null ? war.getAttackerColony().getID() : -1;
                 e.warStartTime = war.warStartTime;
@@ -3972,12 +4083,49 @@ public class WarSystem {
                 war.getLastLifeInventoryPreservation()
                         .forEach(uuid -> e.lastLifeInventoryPreservation.add(uuid.toString()));
 
+                // Previously-dropped fields. Stored as Action.name() -> Boolean so the
+                // serialized form is forward/backward-compat with Action enum changes.
+                if (war.originalHostilePerms != null) {
+                    e.originalHostilePerms = new HashMap<>();
+                    war.originalHostilePerms.forEach((a, b) -> e.originalHostilePerms.put(a.name(), b));
+                }
+                if (war.originalHostilePermsForAttacker != null) {
+                    e.originalHostilePermsForAttacker = new HashMap<>();
+                    war.originalHostilePermsForAttacker.forEach((a, b) -> e.originalHostilePermsForAttacker.put(a.name(), b));
+                }
+                e.acceptedAllies = new ArrayList<>();
+                war.getAcceptedAllies().forEach(uuid -> e.acceptedAllies.add(uuid.toString()));
+                e.declinedAllies = new ArrayList<>();
+                war.getDeclinedAllies().forEach(uuid -> e.declinedAllies.add(uuid.toString()));
+                e.offlineOutpostWar = war.isOfflineOutpostWar();
+
+                net.machiavelli.minecolonytax.peace.PeaceProposal pp = war.getActiveProposal();
+                if (pp != null) {
+                    ProposalSaveEntry pe = new ProposalSaveEntry();
+                    pe.type = pp.getType().name();
+                    pe.amount = pp.getAmount();
+                    pe.proposer = pp.getProposer() != null ? pp.getProposer().toString() : null;
+                    pe.createdTime = pp.getCreatedTime();
+                    e.activeProposal = pe;
+                }
+
                 saveData.wars.add(e);
             }
 
-            try (Writer writer = new FileWriter(path.toFile())) {
+            // Finding 3: atomic write — write to a tmp file, then atomic-move it
+            // over the live file. Falls back to a plain replace on Windows builds
+            // that lack ATOMIC_MOVE support (catches AtomicMoveNotSupportedException).
+            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
+            try (Writer writer = new FileWriter(tmp.toFile())) {
                 WAR_GSON.toJson(saveData, writer);
             }
+            try {
+                Files.move(tmp, path,
+                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
+                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
+            } catch (java.nio.file.AtomicMoveNotSupportedException windowsFallback) {
+                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
+            }
 
             WARSYSTEM_LOGGER.info("Saved {} active wars to {}", saveData.wars.size(), WAR_STORAGE_FILE);
         } catch (Exception ex) {
@@ -3985,6 +4133,9 @@ public class WarSystem {
         }
     }
 
+    /** Sentinel UUID used in serialized form when an attacker/defender team ID was null at save time. */
+    private static final UUID NULL_TEAM_ID_SENTINEL = new UUID(0L, 0L);
+
     public static void loadAndResumeActiveWars() {
         Path path = Paths.get(WAR_STORAGE_FILE);
         if (!Files.exists(path)) {
@@ -4008,6 +4159,7 @@ public class WarSystem {
 
             int restored = 0;
             int skipped = 0;
+            int total = saveData.wars.size();
 
             for (WarSaveEntry e : saveData.wars) {
                 try {
@@ -4023,7 +4175,24 @@ public class WarSystem {
             }
 
             WARSYSTEM_LOGGER.info("War restoration complete: {} restored, {} skipped", restored, skipped);
-            Files.deleteIfExists(path);
+
+            // Finding 4: only delete the source file when EVERY war was successfully
+            // restored. On partial failure, rename the file to active_wars.json.failed-<ts>
+            // for forensic recovery — never silently drop unrestored entries.
+            if (skipped == 0) {
+                Files.deleteIfExists(path);
+            } else {
+                Path failedPath = path.resolveSibling(
+                        path.getFileName() + ".failed-" + System.currentTimeMillis());
+                try {
+                    Files.move(path, failedPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
+                    WARSYSTEM_LOGGER.warn("Partial war restore ({} of {} skipped). Original save preserved at {}",
+                            skipped, total, failedPath);
+                } catch (Exception moveEx) {
+                    WARSYSTEM_LOGGER.error("Could not move active_wars.json to .failed-<ts>; leaving it in place at {}",
+                            path, moveEx);
+                }
+            }
 
         } catch (Exception ex) {
             WARSYSTEM_LOGGER.error("Failed to load active wars from disk", ex);
@@ -4081,11 +4250,19 @@ public class WarSystem {
         Set<UUID> lastLifeSet = parseUUIDList(e.lastLifeInventoryPreservation);
 
         long now = System.currentTimeMillis();
+        // Finding 5: wars that ran out their clock while the server was down
+        // previously logged "expired during server downtime, skipping" and gave
+        // the victor zero rewards / no occupation / no rank cleanup. Instead,
+        // construct the WarData, register it in ACTIVE_WARS, then immediately
+        // run the normal end-of-war handler so reparations, ranks, occupation
+        // hooks, and history all fire. handleTimeExpiry() is the canonical
+        // end-of-time-elapsed entry point used by the countdown tick.
+        boolean expiredDuringDowntime = false;
         if (status == WarData.WarStatus.INWAR) {
             long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
             if (now >= e.warStartTime + warDurationMs) {
-                WARSYSTEM_LOGGER.info("War {} expired during server downtime, skipping restoration", e.warID);
-                return false;
+                WARSYSTEM_LOGGER.info("War {} expired during server downtime — resolving via handleTimeExpiry", e.warID);
+                expiredDuringDowntime = true;
             }
         } else if (status == WarData.WarStatus.JOINING) {
             if (now >= e.joinPhaseEndTime) {
@@ -4095,6 +4272,20 @@ public class WarSystem {
             }
         }
 
+        // Convert sentinel team IDs (written by saveActiveWars for wars whose
+        // team IDs were null at save time) back into null so callers see the
+        // same invariant they had before the save.
+        UUID atkTid;
+        UUID defTid;
+        try {
+            atkTid = UUID.fromString(e.attackerTeamID);
+            if (NULL_TEAM_ID_SENTINEL.equals(atkTid)) atkTid = null;
+        } catch (IllegalArgumentException iae) { atkTid = null; }
+        try {
+            defTid = UUID.fromString(e.defenderTeamID);
+            if (NULL_TEAM_ID_SENTINEL.equals(defTid)) defTid = null;
+        } catch (IllegalArgumentException iae) { defTid = null; }
+
         ServerBossEvent bossEvent = new ServerBossEvent(
                 Component.literal("War for " + defenderColony.getName()),
                 BossEvent.BossBarColor.RED,
@@ -4102,12 +4293,43 @@ public class WarSystem {
         bossEvent.setProgress(1.0f);
         bossEvent.setVisible(true);
 
+        // Reconstruct restored fields (formerly silently dropped — see WarData.java).
+        Map<Action, Boolean> restoredHostilePerms = null;
+        if (e.originalHostilePerms != null) {
+            restoredHostilePerms = new HashMap<>();
+            for (Map.Entry<String, Boolean> en : e.originalHostilePerms.entrySet()) {
+                try { restoredHostilePerms.put(Action.valueOf(en.getKey()), en.getValue()); }
+                catch (IllegalArgumentException ignored) {} // forward-compat: skip unknown Action names
+            }
+        }
+        Map<Action, Boolean> restoredHostilePermsAtk = null;
+        if (e.originalHostilePermsForAttacker != null) {
+            restoredHostilePermsAtk = new HashMap<>();
+            for (Map.Entry<String, Boolean> en : e.originalHostilePermsForAttacker.entrySet()) {
+                try { restoredHostilePermsAtk.put(Action.valueOf(en.getKey()), en.getValue()); }
+                catch (IllegalArgumentException ignored) {}
+            }
+        }
+        Set<UUID> acceptedAlliesSet = parseUUIDList(e.acceptedAllies);
+        Set<UUID> declinedAlliesSet = parseUUIDList(e.declinedAllies);
+        net.machiavelli.minecolonytax.peace.PeaceProposal restoredProposal = null;
+        if (e.activeProposal != null && e.activeProposal.type != null && e.activeProposal.proposer != null) {
+            try {
+                restoredProposal = new net.machiavelli.minecolonytax.peace.PeaceProposal(
+                        net.machiavelli.minecolonytax.peace.PeaceProposal.Type.valueOf(e.activeProposal.type),
+                        e.activeProposal.amount,
+                        UUID.fromString(e.activeProposal.proposer));
+                // PeaceProposal.createdTime defaults to "now" on construction — close
+                // enough for restored proposals; the timeout check is a relative delta.
+            } catch (IllegalArgumentException ignored) {}
+        }
+
         WarData warData = new WarData(
                 UUID.fromString(e.warID),
                 UUID.fromString(e.attacker),
                 UUID.fromString(e.defender),
-                UUID.fromString(e.attackerTeamID),
-                UUID.fromString(e.defenderTeamID),
+                atkTid,
+                defTid,
                 e.warStartTime, e.joinPhaseEndTime,
                 bossEvent, defenderColony, attackerColony,
                 status, e.accepted,
@@ -4117,10 +4339,36 @@ public class WarSystem {
                 attackerLives, defenderLives,
                 defenderGuardIDSet, attackerGuardIDSet, attackerAlliesSet, defenderAlliesSet,
                 spectatorsSet, lastLifeSet,
-                e.penaltyReport, e.stalemateTriggered);
+                e.penaltyReport, e.stalemateTriggered,
+                restoredHostilePerms, restoredHostilePermsAtk,
+                acceptedAlliesSet, declinedAlliesSet,
+                e.offlineOutpostWar,
+                restoredProposal);
 
         ACTIVE_WARS.put(e.defenderColonyId, warData);
 
+        // Finding 5 cont'd: war ran past its clock while we were down. Register
+        // the WarData (so handleTimeExpiry/endWar can find it via ACTIVE_WARS),
+        // then immediately resolve. handleTimeExpiry takes care of victor logic,
+        // reparations, ranks, etc. If the canonical end-of-time path lives at
+        // a different entrypoint in this codebase, this still has the WarData
+        // in ACTIVE_WARS so handleTimeExpiry / endWar must be wired correctly.
+        // TODO: if handleTimeExpiry isn't safe at boot (e.g. needs world ticks),
+        // demote this to a TickScheduler.scheduleDelayed(.., 100ms) call.
+        if (expiredDuringDowntime) {
+            try {
+                handleTimeExpiry(warData);
+            } catch (Throwable t) {
+                WARSYSTEM_LOGGER.error("Failed to resolve downtime-expired war {} via handleTimeExpiry; falling back to endWar.", e.warID, t);
+                try { endWar(defenderColony); } catch (Throwable t2) {
+                    WARSYSTEM_LOGGER.error("Fallback endWar also failed for downtime-expired war {}; war removed without rewards.", e.warID, t2);
+                    ACTIVE_WARS.remove(e.defenderColonyId);
+                }
+            }
+            // True for the loader: we processed this war successfully.
+            return true;
+        }
+
         // Restore defender tracking + drain scheduling
         net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(e.defenderColonyId);
         scheduleTreasuryDrain(warData, defenderColony, attackerColony);
```

### DIFF: data/WarData.java (militiaSupport field)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/data/WarData.java b/src/main/java/net/machiavelli/minecolonytax/data/WarData.java
index 3c9af05..dd7404d 100644
--- a/src/main/java/net/machiavelli/minecolonytax/data/WarData.java
+++ b/src/main/java/net/machiavelli/minecolonytax/data/WarData.java
@@ -10,8 +10,18 @@ import java.util.*;
 import java.util.concurrent.ConcurrentHashMap;
 import java.util.concurrent.TimeUnit;
 import net.machiavelli.minecolonytax.util.TickScheduler;
+import net.minecraft.world.entity.Entity;
 
 public class WarData {
+
+    /**
+     * Militia-upgrade reinforcements spawned at INWAR transition. Transient —
+     * not persisted across server restart (entities can't be either; a restored
+     * war is on the operator to manually re-spawn if desired). Tracked here so
+     * they can be cleanly despawned in {@code WarSystem.endWar}. By design
+     * these do NOT count toward victory; only real guards + player lives do.
+     */
+    public final Set<Entity> militiaSupport = ConcurrentHashMap.newKeySet();
     private final UUID warID;
     private final UUID attacker;
     private final UUID defender;
@@ -94,6 +104,15 @@ public class WarData {
     /**
      * Restoration constructor - used when loading saved wars from disk after a server restart.
      * Does NOT recalculate guards or generate a new warID; all values come from the save file.
+     *
+     * <p><b>Parameter count: 34.</b> (was 28 — increased 2026-05-25 to include the
+     * previously-dropped originalHostilePerms / originalHostilePermsForAttacker /
+     * acceptedAllies / declinedAllies / offlineOutpostWar / activeProposal fields.)
+     *
+     * <p>When modifying WarData, also update this constructor AND the matching
+     * WarSaveEntry serialization in WarSystem so persisted wars round-trip correctly.
+     * TODO: add a CI test that builds a WarData, serializes it, deserializes it,
+     * and asserts equality (no field silently dropped).
      */
     public WarData(UUID warID, UUID attacker, UUID defender, UUID attackerTeamID, UUID defenderTeamID,
                    long warStartTime, long joinPhaseEndTime, ServerBossEvent bossEvent,
@@ -105,7 +124,12 @@ public class WarData {
                    Set<Integer> defenderGuardIDsData, Set<Integer> attackerGuardIDsData,
                    Set<UUID> attackerAlliesData, Set<UUID> defenderAlliesData,
                    Set<UUID> spectatorsData, Set<UUID> lastLifeData,
-                   String penaltyReport, boolean stalemateTriggered) {
+                   String penaltyReport, boolean stalemateTriggered,
+                   Map<Action, Boolean> originalHostilePerms,
+                   Map<Action, Boolean> originalHostilePermsForAttacker,
+                   Set<UUID> acceptedAlliesData, Set<UUID> declinedAlliesData,
+                   boolean offlineOutpostWar,
+                   PeaceProposal activeProposal) {
         this.warID = warID;
         this.attacker = attacker;
         this.defender = defender;
@@ -134,6 +158,12 @@ public class WarData {
         if (defenderAlliesData != null) this.defenderAllies.addAll(defenderAlliesData);
         if (spectatorsData != null) this.spectators.addAll(spectatorsData);
         if (lastLifeData != null) this.lastLifeInventoryPreservation.addAll(lastLifeData);
+        if (originalHostilePerms != null) this.originalHostilePerms = new java.util.HashMap<>(originalHostilePerms);
+        if (originalHostilePermsForAttacker != null) this.originalHostilePermsForAttacker = new java.util.HashMap<>(originalHostilePermsForAttacker);
+        if (acceptedAlliesData != null) this.acceptedAllies.addAll(acceptedAlliesData);
+        if (declinedAlliesData != null) this.declinedAllies.addAll(declinedAlliesData);
+        this.offlineOutpostWar = offlineOutpostWar;
+        this.activeProposal = activeProposal;
     }
 
     private void initializeGuards(IColony colony, Set<Integer> targetSet) {
```

### DIFF: ColonyDataResponsePacket.java (kind on wire)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/network/packets/ColonyDataResponsePacket.java b/src/main/java/net/machiavelli/minecolonytax/network/packets/ColonyDataResponsePacket.java
index 1799eb1..ccb15c4 100644
--- a/src/main/java/net/machiavelli/minecolonytax/network/packets/ColonyDataResponsePacket.java
+++ b/src/main/java/net/machiavelli/minecolonytax/network/packets/ColonyDataResponsePacket.java
@@ -1,12 +1,12 @@
 package net.machiavelli.minecolonytax.network.packets;
 
 import net.machiavelli.minecolonytax.events.random.EventLogEntry;
-import net.machiavelli.minecolonytax.gui.TaxManagementScreen;
 import net.machiavelli.minecolonytax.gui.data.ColonySummary;
 import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
 import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
-import net.minecraft.client.Minecraft;
 import net.minecraft.network.FriendlyByteBuf;
+import net.minecraftforge.api.distmarker.Dist;
+import net.minecraftforge.fml.DistExecutor;
 import net.minecraftforge.network.NetworkEvent;
 
 import java.util.ArrayList;
@@ -108,10 +108,17 @@ public class ColonyDataResponsePacket {
             int lastTribute = buf.readInt();
             long lastPayment = buf.readLong();
             boolean canClaim = buf.readBoolean();
+            int kindOrdinal = buf.readInt();
+            VassalIncomeData.VassalKind kind;
+            try {
+                kind = VassalIncomeData.VassalKind.values()[kindOrdinal];
+            } catch (Exception e) {
+                kind = VassalIncomeData.VassalKind.VASSAL;
+            }
 
             this.vassalData.add(new VassalIncomeData(
                 vassalColonyId, vassalColonyName, tributeRate,
-                tributeOwed, lastTribute, lastPayment, canClaim
+                tributeOwed, lastTribute, lastPayment, canClaim, kind
             ));
         }
 
@@ -186,6 +193,7 @@ public class ColonyDataResponsePacket {
             buf.writeInt(data.getLastTribute());
             buf.writeLong(data.getLastPayment());
             buf.writeBoolean(data.canClaim());
+            buf.writeInt(data.getKind() != null ? data.getKind().ordinal() : 0);
         }
 
         // Write event log data
@@ -216,15 +224,19 @@ public class ColonyDataResponsePacket {
     public boolean handle(Supplier<NetworkEvent.Context> supplier) {
         NetworkEvent.Context context = supplier.get();
         context.enqueueWork(() -> {
-            // This runs on the client side
-            Minecraft mc = Minecraft.getInstance();
-            if (mc.screen instanceof TaxManagementScreen) {
-                TaxManagementScreen screen = (TaxManagementScreen) mc.screen;
-                screen.updateColonyData(colonyData);
-                screen.updateVassalData(vassalData);
-                screen.updateEventData(eventLogData);
-                screen.updateAllColonySummaries(allColonySummaries);
-            }
+            // This runs on the client side — wrap to avoid class-loading client-only
+            // classes (Minecraft, TaxManagementScreen) on a dedicated server.
+            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
+                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
+                if (mc.screen instanceof net.machiavelli.minecolonytax.gui.TaxManagementScreen) {
+                    net.machiavelli.minecolonytax.gui.TaxManagementScreen screen =
+                            (net.machiavelli.minecolonytax.gui.TaxManagementScreen) mc.screen;
+                    screen.updateColonyData(colonyData);
+                    screen.updateVassalData(vassalData);
+                    screen.updateEventData(eventLogData);
+                    screen.updateAllColonySummaries(allColonySummaries);
+                }
+            });
         });
         return true;
     }
```

### DIFF: ColonyDataCollector.java (occupation → VassalIncomeData rows)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/server/ColonyDataCollector.java b/src/main/java/net/machiavelli/minecolonytax/server/ColonyDataCollector.java
index a78f871..a88dfcb 100644
--- a/src/main/java/net/machiavelli/minecolonytax/server/ColonyDataCollector.java
+++ b/src/main/java/net/machiavelli/minecolonytax/server/ColonyDataCollector.java
@@ -79,7 +79,8 @@ public class ColonyDataCollector {
                         data.getTributeOwed(),
                         data.getLastTribute(),
                         data.getLastPayment(),
-                        canClaim));
+                        canClaim,
+                        VassalIncomeData.VassalKind.VASSAL));
             }
 
         } catch (Exception e) {
@@ -89,6 +90,41 @@ public class ColonyDataCollector {
             }
         }
 
+        // Step 9: surface tax-occupied (Primary) and provisionally-claimed (Secondary)
+        // colonies in the Vassals tab where this player is the occupier.
+        try {
+            for (net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData occ
+                    : net.machiavelli.minecolonytax.occupation.OccupationManager.getActiveOccupations().values()) {
+                if (occ == null || occ.isExpired()) continue;
+                if (!playerId.equals(occ.getOccupierUUID())) continue;
+
+                // Map mode → badge kind.
+                VassalIncomeData.VassalKind kind =
+                        occ.getMode() == net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationMode.TAX_ONLY
+                        ? VassalIncomeData.VassalKind.TAX_OCCUPIED
+                        : VassalIncomeData.VassalKind.PROVISIONAL;
+
+                // Use the configured occupation tax percentage as the displayed "rate"
+                // so the row is honest about what's being collected.
+                int tributeRate = (int) Math.round(
+                        net.machiavelli.minecolonytax.TaxConfig.getOccupationTaxPercentage() * 100);
+
+                vassalIncomes.add(new VassalIncomeData(
+                        occ.colonyId,
+                        occ.colonyName,
+                        tributeRate,
+                        0,                              // no pending one-shot tribute model here
+                        0,                              // last-tribute not tracked separately for occupations
+                        occ.lastTaxCollectionTime,      // re-use for "last payment" label
+                        true,                           // can collect via /wnt collectoccupation
+                        kind));
+            }
+        } catch (Exception e) {
+            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
+                LOGGER.debug("Error collecting occupation rows for vassal feed: {}", e.getMessage());
+            }
+        }
+
         return vassalIncomes;
     }
 
```

### DIFF: VassalsPage.java (kind-aware rendering)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java b/src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java
index 9c36a9c..ea958b9 100644
--- a/src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java
+++ b/src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java
@@ -101,7 +101,16 @@ public class VassalsPage extends BookPage {
                 }
                 g.fill(leftX, iy + 17, leftX + leftW, iy + 18, CARD_BORDER);
                 g.drawString(font, truncate(font, v.getVassalColonyName(), leftW - 6), leftX + 4, iy + 1, INK, false);
-                g.drawString(font, "Income: " + v.getTributeRate() + "%", leftX + 4, iy + 10, GREEN, false);
+                // Income label color tracks the relationship kind so tax-occupied
+                // and provisional-claim rows visually stand apart from true vassals.
+                int incomeColor = colorForKind(v.getKind());
+                g.drawString(font, "Income: " + v.getTributeRate() + "%", leftX + 4, iy + 10, incomeColor, false);
+                // Small kind-tag in the row's right margin (compact: just 1 char + dot)
+                String tag = shortTagForKind(v.getKind());
+                if (tag != null) {
+                    int tagX = leftX + leftW - font.width(tag) - 6;
+                    g.drawString(font, tag, tagX, iy + 1, incomeColor, false);
+                }
             }
             drawScrollbar(g, leftX + leftW - 4, y, leftH - (y - leftY), vassals.size(), maxVisible, scrollOffset);
         }
@@ -126,6 +135,39 @@ public class VassalsPage extends BookPage {
         g.drawString(font, "Select an entry", rightX + 10, rightY + rightH / 2 - 4, INK_GHOST, false);
     }
 
+    /** Display label for the Vassals tab row badge. */
+    private static String labelForKind(VassalIncomeData.VassalKind kind) {
+        if (kind == null) return "VASSAL";
+        switch (kind) {
+            case TAX_OCCUPIED: return "TAX-OCCUPIED";
+            case PROVISIONAL:  return "PROVISIONAL";
+            case VASSAL:
+            default:           return "VASSAL";
+        }
+    }
+
+    /** Color for the Vassals tab row badge. Matches the design HTML's palette. */
+    private static int colorForKind(VassalIncomeData.VassalKind kind) {
+        if (kind == null) return GREEN;
+        switch (kind) {
+            case TAX_OCCUPIED: return DANGER;          // red — primary under besiege
+            case PROVISIONAL:  return 0xFFE07B33;      // orange — secondary mid-conversion
+            case VASSAL:
+            default:           return GREEN;
+        }
+    }
+
+    /** One-letter row tag for the compact list view. */
+    private static String shortTagForKind(VassalIncomeData.VassalKind kind) {
+        if (kind == null) return null;
+        switch (kind) {
+            case TAX_OCCUPIED: return "T";
+            case PROVISIONAL:  return "P";
+            case VASSAL:       return null; // keep the legacy rows clean
+            default:           return null;
+        }
+    }
+
     private void renderOverlordDetail(GuiGraphics g, ColonyTaxData ol, int mouseX, int mouseY) {
         int x = rightX, y = rightY, w = rightW;
 
@@ -146,7 +188,10 @@ public class VassalsPage extends BookPage {
         int x = rightX, y = rightY, w = rightW;
 
         g.drawString(font, truncate(font, v.getVassalColonyName(), w - 50), x, y, INK, false);
-        drawBadge(g, font, "VASSAL", x + w - font.width("VASSAL") - 4, y, GREEN);
+        // Kind-aware badge: VASSAL (green) / TAX-OCCUPIED (red) / PROVISIONAL (orange).
+        String badgeLabel = labelForKind(v.getKind());
+        int badgeColor = colorForKind(v.getKind());
+        drawBadge(g, font, badgeLabel, x + w - font.width(badgeLabel) - 4, y, badgeColor);
         y += 14;
 
         drawCard(g, x, y, w, 18, true);
```
