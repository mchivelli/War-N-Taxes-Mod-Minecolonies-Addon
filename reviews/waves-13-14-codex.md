Reading additional input from stdin...
OpenAI Codex v0.133.0
--------
workdir: C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: xhigh
reasoning summaries: none
session id: 019e5f6d-a91b-7b22-b15e-438f9a792f3d
--------
user
Reviewing the partial-work completion pass: Step 8 (war-side militia wiring) and Step 9 (Vassals tab UI/packet wiring) finished. Five files changed plus one new MilitiaSpawner helper. Review for: (1) is the militia spawn in WarSystem.finalizeWarStart in a re-entrant safe location — could finalizeWarStart be called twice for the same war? (2) packet encode/decode symmetry — does the kind ordinal round-trip correctly and handle out-of-range values from old clients? (3) ColonyDataCollector occupation rows — does it correctly skip expired occupations, dedup against existing vassal rows for the same colony? (4) VassalsPage kind dispatch — fallback for null kind correct? Respond: STATUS APPROVE/REWORK, up to 5 bullets, up to 3 fix suggestions. Max 300 words.

<stdin>
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
</stdin>
codex
I’ll verify the touched paths and the surrounding call sites, then keep the review scoped to the four questions you listed.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff -- src/main/java/net/machiavelli/minecolonytax/WarSystem.java src/main/java/net/machiavelli/minecolonytax/data/WarData.java src/main/java/net/machiavelli/minecolonytax/network/packets/ColonyDataResponsePacket.java src/main/java/net/machiavelli/minecolonytax/server/ColonyDataCollector.java src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java src/main/java/net/machiavelli/minecolonytax/militia/MilitiaSpawner.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"finalizeWarStart|new VassalIncomeData|VassalKind|OccupationData|isExpired|getActiveOccupations|class ColonyDataResponsePacket|encode\\(|decode\\(\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 555ms:
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:80:        public boolean isExpired() {
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:463:            if (raidData.isExpired()) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:85:    private static final Map<Integer, BesiegeOccupationData> OCCUPATIONS = new ConcurrentHashMap<>();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:277:        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:696:            BesiegeOccupationData occ = new BesiegeOccupationData(
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:912:        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:919:        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:951:    public static BesiegeOccupationData getOccupation(int colonyId) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:982:    public static Map<Integer, BesiegeOccupationData> getAllOccupations() {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1198:            Type type = new TypeToken<List<BesiegeOccupationData>>() {}.getType();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1199:            List<BesiegeOccupationData> list = GSON.fromJson(r, type);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1201:                for (BesiegeOccupationData occ : list) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1215:        final List<BesiegeOccupationData> list = new ArrayList<>(OCCUPATIONS.values());
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1261:    public static class BesiegeOccupationData {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1270:        public BesiegeOccupationData() {}
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1272:        public BesiegeOccupationData(int colonyId, String colonyName, UUID besiegingPlayerUUID,
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:542:    public static void finalizeWarStart(WarData war) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2416:            finalizeWarStart(war);
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2656:                net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData attackerOccData = 
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2670:                    net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData targetOccData = 
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2725:            net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData targetOccData =
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2743:                    net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData acOccData =
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:4437:                    finalizeWarStart(w);
src/main/java\net\machiavelli\minecolonytax\vassalization\VassalManager.java:256:        if (rel.isExpired()) {
src/main/java\net\machiavelli\minecolonytax\vassalization\VassalManager.java:612:        public boolean isExpired() {
src/main/java\net\machiavelli\minecolonytax\vassalization\VassalManager.java:731:                    vassalIncomes.add(new VassalIncomeData(
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:46:    private static final Map<Integer, OccupationData> ACTIVE_OCCUPATIONS = new ConcurrentHashMap<>();
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:63:    public static class OccupationData {
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:76:        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:83:        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:103:        public boolean isExpired() {
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:131:        public List<OccupationData> occupations = new ArrayList<>();
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:165:        // owner. The OccupationData constructor does originalOwnerUUID.toString() and would
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:208:        OccupationData data = new OccupationData(
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:285:    public static OccupationData getOccupation(int colonyId) {
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:289:    public static Map<Integer, OccupationData> getActiveOccupations() {
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:294:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:298:    public static List<OccupationData> getOccupiedByPlayer(UUID playerUUID) {
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:299:        List<OccupationData> result = new ArrayList<>();
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:301:        for (OccupationData data : ACTIVE_OCCUPATIONS.values()) {
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:310:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:369:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:394:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:405:        OccupationData data = ACTIVE_OCCUPATIONS.remove(colonyId);
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:440:        for (Map.Entry<Integer, OccupationData> entry : ACTIVE_OCCUPATIONS.entrySet()) {
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:441:            OccupationData data = entry.getValue();
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:442:            if (data.isExpired()) {
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:452:            OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:547:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:587:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:595:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:645:                    for (OccupationData data : saveData.occupations) {
src/main/java\net\machiavelli\minecolonytax\db\WarStatsDB.java:455:                        OccupationManager.OccupationData occData = OccupationManager.getOccupation(colony.getID());
src/main/java\net\machiavelli\minecolonytax\network\EntityGlowPacket.java:19:    public static void encode(EntityGlowPacket msg, FriendlyByteBuf buf) {
src/main/java\net\machiavelli\minecolonytax\network\EntityGlowPacket.java:32:    public static EntityGlowPacket decode(FriendlyByteBuf buf) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1397:                                BesiegeManager.BesiegeOccupationData occ = BesiegeManager.getOccupation(target.getID());
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1435:                Map<Integer, BesiegeManager.BesiegeOccupationData> occupations = BesiegeManager.getAllOccupations();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1466:                        for (BesiegeManager.BesiegeOccupationData occ : occupations.values()) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1488:                Map<Integer, BesiegeManager.BesiegeOccupationData> occupations = BesiegeManager.getAllOccupations();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1502:                List<BesiegeManager.BesiegeOccupationData> myOccupations = occupations.values().stream()
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1541:                        for (BesiegeManager.BesiegeOccupationData occ : myOccupations) {
src/main/java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:102:                        .getActiveOccupations().isEmpty()) return true;
src/main/java\net\machiavelli\minecolonytax\util\ColonyActivityTracker.java:33:        public boolean isExpired() {
src/main/java\net\machiavelli\minecolonytax\util\ColonyActivityTracker.java:47:        if (cached != null && !cached.isExpired()) {
src/main/java\net\machiavelli\minecolonytax\util\ColonyActivityTracker.java:73:        if (cached != null && !cached.isExpired()) {
src/main/java\net\machiavelli\minecolonytax\util\ColonyActivityTracker.java:156:                if (entry.getValue().isExpired()) {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:140:            for (Map.Entry<Integer, OccupationManager.OccupationData> entry
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:141:                    : OccupationManager.getActiveOccupations().entrySet()) {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:143:                    OccupationManager.OccupationData occ = entry.getValue();
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:144:                    if (occ.isExpired()) continue;
src/main/java\net\machiavelli\minecolonytax\network\packets\ColonyDataResponsePacket.java:21:public class ColonyDataResponsePacket {
src/main/java\net\machiavelli\minecolonytax\network\packets\ColonyDataResponsePacket.java:112:            VassalIncomeData.VassalKind kind;
src/main/java\net\machiavelli\minecolonytax\network\packets\ColonyDataResponsePacket.java:114:                kind = VassalIncomeData.VassalKind.values()[kindOrdinal];
src/main/java\net\machiavelli\minecolonytax\network\packets\ColonyDataResponsePacket.java:116:                kind = VassalIncomeData.VassalKind.VASSAL;
src/main/java\net\machiavelli\minecolonytax\network\packets\ColonyDataResponsePacket.java:119:            this.vassalData.add(new VassalIncomeData(
src/main/java\net\machiavelli\minecolonytax\network\packets\PayDebtPacket.java:33:    public static void encode(PayDebtPacket packet, FriendlyByteBuf buffer) {
src/main/java\net\machiavelli\minecolonytax\network\packets\PayDebtPacket.java:38:    public static PayDebtPacket decode(FriendlyByteBuf buffer) {
src/main/java\net\machiavelli\minecolonytax\network\packets\OpenTaxGUIPacket.java:24:    public void encode(FriendlyByteBuf buf) {
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:186:                        OccupationManager.OccupationData occ = OccupationManager.getOccupation(colonyId);
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:187:                        if (occ != null && !occ.isExpired()) {
src/main/java\net\machiavelli\minecolonytax\gui\data\VassalIncomeData.java:12:    public enum VassalKind { VASSAL, TAX_OCCUPIED, PROVISIONAL }
src/main/java\net\machiavelli\minecolonytax\gui\data\VassalIncomeData.java:21:    private final VassalKind kind;
src/main/java\net\machiavelli\minecolonytax\gui\data\VassalIncomeData.java:28:        this(vassalColonyId, vassalColonyName, tributeRate, tributeOwed, lastTribute, lastPayment, canClaim, VassalKind.VASSAL);
src/main/java\net\machiavelli\minecolonytax\gui\data\VassalIncomeData.java:33:                           VassalKind kind) {
src/main/java\net\machiavelli\minecolonytax\gui\data\VassalIncomeData.java:41:        this.kind = kind != null ? kind : VassalKind.VASSAL;
src/main/java\net\machiavelli\minecolonytax\gui\data\VassalIncomeData.java:44:    public VassalKind getKind() {
src/main/java\net\machiavelli\minecolonytax\gui\book\VassalsPage.java:139:    private static String labelForKind(VassalIncomeData.VassalKind kind) {
src/main/java\net\machiavelli\minecolonytax\gui\book\VassalsPage.java:150:    private static int colorForKind(VassalIncomeData.VassalKind kind) {
src/main/java\net\machiavelli\minecolonytax\gui\book\VassalsPage.java:161:    private static String shortTagForKind(VassalIncomeData.VassalKind kind) {
src/main/java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:147:        if (proposal.isExpired(timeoutMillis)) {
src/main/java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:189:        if (proposal.isExpired(timeoutMillis)) {
src/main/java\net\machiavelli\minecolonytax\peace\PeaceProposal.java:35:    public boolean isExpired(long timeoutMillis) {
src/main/java\net\machiavelli\minecolonytax\trade\TradeRouteManager.java:100:        public boolean isExpired() {
src/main/java\net\machiavelli\minecolonytax\trade\TradeRouteManager.java:142:                    if (!prop.isExpired()) {
src/main/java\net\machiavelli\minecolonytax\trade\TradeRouteManager.java:250:        if (proposal.isExpired()) {
src/main/java\net\machiavelli\minecolonytax\trade\TradeRouteManager.java:365:                .filter(p -> p.targetColonyId == colonyId && !p.isExpired())
src/main/java\net\machiavelli\minecolonytax\trade\TradeRouteManager.java:423:        PENDING_PROPOSALS.entrySet().removeIf(e -> e.getValue().isExpired());
src/main/java\net\machiavelli\minecolonytax\pvp\model\BattleRequest.java:26:    public boolean isExpired() { return System.currentTimeMillis() > expiryTime; }
src/main/java\net\machiavelli\minecolonytax\pvp\PvPEventHandler.java:107:            pvpManager.pendingRequests.entrySet().removeIf(entry -> entry.getValue().isExpired());
src/main/java\net\machiavelli\minecolonytax\network\packets\UpdatePlayerTaxPermissionPacket.java:37:    public void encode(FriendlyByteBuf buffer) {
src/main/java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:75:                vassalIncomes.add(new VassalIncomeData(
src/main/java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:83:                        VassalIncomeData.VassalKind.VASSAL));
src/main/java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:96:            for (net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData occ
src/main/java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:97:                    : net.machiavelli.minecolonytax.occupation.OccupationManager.getActiveOccupations().values()) {
src/main/java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:98:                if (occ == null || occ.isExpired()) continue;
src/main/java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:102:                VassalIncomeData.VassalKind kind =
src/main/java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:104:                        ? VassalIncomeData.VassalKind.TAX_OCCUPIED
src/main/java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:105:                        : VassalIncomeData.VassalKind.PROVISIONAL;
src/main/java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:112:                vassalIncomes.add(new VassalIncomeData(
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 558ms:
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
+     * Legacy boolean flag тАФ preserved for external callers that read it directly.
+     * Routes through {@link FtbTeamsCompat#isInstalled()} which is classloader-safe.
+     * Do NOT add new typed FTB Teams statics here тАФ use {@link FtbTeamsCompat}.
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
 
+        // Militia upgrade reinforcements тАФ spawn on BOTH sides if either colony
+        // has the upgrade. Defender side primarily (per design), attacker side
+        // optionally so an upgraded attacker colony also gets the boost.
+        // Idempotent тАФ re-entry checks the existing set is empty first.
+        if (war.militiaSupport.isEmpty()) {
+            try {
+                // Defender militia
+                if (war.getColony() != null) {
+                    int defenderGuardCount = (int) war.getColony().getCitizenManager().getCitizens().stream()
+                            .filter(c -> c.getJob() != null && c.getJob().isGuard())
+                            .count();
+                    // No specific attacker-target тАФ let the militia find via vanilla aggro
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
+        // different code path), bail out тАФ re-running the rest of this method
+        // would double-fire demotions, history records, treasury cleanup, etc.
+        WarData warData = ACTIVE_WARS.remove(colony.getID());
+        if (warData == null) {
+            // Already ended тАФ nothing to do. (Previously this code re-ran all
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
+        // Finding 10 idempotency fix тАФ no further read/remove needed.)
 
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
+            // was "born in the future" тАФ almost certainly a backwards clock
+            // adjustment. Reset warStartTime to the current wall clock so the
+            // war doesn't appear to never expire (or instantly expire). This is
+            // a soft repair, not a monotonic rewrite тАФ sufficient to avoid
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
+        // on save/restore тАФ see WarData restoration constructor docstring.
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
+                // Finding 11 (audit CRIT тАФ CRASH-3b): defenderTeamID can be null for
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
+            // Finding 3: atomic write тАФ write to a tmp file, then atomic-move it
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
+            // for forensic recovery тАФ never silently drop unrestored entries.
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
+                WARSYSTEM_LOGGER.info("War {} expired during server downtime тАФ resolving via handleTimeExpiry", e.warID);
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
 
+        // Reconstruct restored fields (formerly silently dropped тАФ see WarData.java).
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
+                // PeaceProposal.createdTime defaults to "now" on construction тАФ close
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
+     * Militia-upgrade reinforcements spawned at INWAR transition. Transient тАФ
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
+     * <p><b>Parameter count: 34.</b> (was 28 тАФ increased 2026-05-25 to include the
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
+            case TAX_OCCUPIED: return DANGER;          // red тАФ primary under besiege
+            case PROVISIONAL:  return 0xFFE07B33;      // orange тАФ secondary mid-conversion
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
+            // This runs on the client side тАФ wrap to avoid class-loading client-only
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
+                // Map mode тЖТ badge kind.
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
 
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fБr 
Kerntypen unterstБtzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java', LF will be replaced by CRLF the next time Git touches it

codex
The key paths are in the working tree. I’m checking the transition callers and the data models now, because the re-entry and packet compatibility questions depend on surrounding invariants, not just the inserted lines.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; "'$i=0; Get-Content $p | ForEach-Object { $i++; if(($i -ge 520 -and $i -le 650) -or ($i -ge 2385 -and $i -le 2430) -or ($i -ge 4410 -and $i -le 4455)) { '"'{0,5}: {1}' -f "'$i,$_ } }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java'; "'$i=0; Get-Content $p | ForEach-Object { $i++; if(($i -ge 55 -and $i -le 115) -or ($i -ge 280 -and $i -le 310) -or ($i -ge 430 -and $i -le 450)) { '"'{0,5}: {1}' -f "'$i,$_ } }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/gui/data/VassalIncomeData.java'; "'$i=0; Get-Content $p | ForEach-Object { $i++; '"'{0,5}: {1}' -f "'$i,$_ }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; "'$i=0; Get-Content $p | ForEach-Object { $i++; if(($i -ge 520 -and $i -le 650) -or ($i -ge 2385 -and $i -le 2430) -or ($i -ge 4410 -and $i -le 4455)) { '"'{0,5}: {1}' -f "'$i,$_ } }'` rejected: blocked by policy
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java'; "'$i=0; Get-Content $p | ForEach-Object { $i++; if(($i -ge 55 -and $i -le 115) -or ($i -ge 280 -and $i -le 310) -or ($i -ge 430 -and $i -le 450)) { '"'{0,5}: {1}' -f "'$i,$_ } }'` rejected: blocked by policy
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/gui/data/VassalIncomeData.java'; "'$i=0; Get-Content $p | ForEach-Object { $i++; '"'{0,5}: {1}' -f "'$i,$_ }'` rejected: blocked by policy
2026-05-25T13:58:45.997058Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/gui/data/VassalIncomeData.java'; "'$i=0; Get-Content $p | ForEach-Object { $i++; '"'{0,5}: {1}' -f "'$i,$_ }'` rejected: blocked by policy
2026-05-25T13:58:45.997077Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java'; "'$i=0; Get-Content $p | ForEach-Object { $i++; if(($i -ge 55 -and $i -le 115) -or ($i -ge 280 -and $i -le 310) -or ($i -ge 430 -and $i -le 450)) { '"'{0,5}: {1}' -f "'$i,$_ } }'` rejected: blocked by policy
2026-05-25T13:58:45.997049Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/WarSystem.java'; "'$i=0; Get-Content $p | ForEach-Object { $i++; if(($i -ge 520 -and $i -le 650) -or ($i -ge 2385 -and $i -le 2430) -or ($i -ge 4410 -and $i -le 4455)) { '"'{0,5}: {1}' -f "'$i,$_ } }'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/network/packets/ColonyDataResponsePacket.java'; "'$i=0; Get-Content $p | ForEach-Object { $i++; if($i -ge 1 -and $i -le 230) { '"'{0,5}: {1}' -f "'$i,$_ } }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/network/packets/ColonyDataResponsePacket.java'; "'$i=0; Get-Content $p | ForEach-Object { $i++; if($i -ge 1 -and $i -le 230) { '"'{0,5}: {1}' -f "'$i,$_ } }'` rejected: blocked by policy
2026-05-25T13:58:46.003686Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/network/packets/ColonyDataResponsePacket.java'; "'$i=0; Get-Content $p | ForEach-Object { $i++; if($i -ge 1 -and $i -le 230) { '"'{0,5}: {1}' -f "'$i,$_ } }'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 35 \"public static void finalizeWarStart|finalizeWarStart\\(war\\)|finalizeWarStart\\(w\\)\" src/main/java/net/machiavelli/minecolonytax/WarSystem.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 8 \"kindOrdinal|writeInt\\(data\\.getKind|new VassalIncomeData|handle\\(\" src/main/java/net/machiavelli/minecolonytax/network/packets/ColonyDataResponsePacket.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 4 \"enum VassalKind|VassalIncomeData\\(|getKind\" src/main/java/net/machiavelli/minecolonytax/gui/data/VassalIncomeData.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 8 "class OccupationData|boolean isExpired|getActiveOccupations|getOccupiedByPlayer|removeExpired" src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 516ms:
507-
508-    public static void updateBossBar(WarData war) {
509-        long now = System.currentTimeMillis();
510-        if (now < war.getJoinPhaseEndTime()) {
511-            long remainingMillis = war.getJoinPhaseEndTime() - now;
512-            String timeStr = String.format("%02d:%02d", remainingMillis / 60000, (remainingMillis / 1000) % 60);
513-            String joinText = Component.translatable("war.siege.status", war.getColony().getName(), timeStr)
514-                    .getString();
515-            war.bossEvent.setName(Component.literal(joinText));
516-            long joinDuration = TaxConfig.JOIN_PHASE_DURATION_MINUTES.get() * 60 * 1000L;
517-            war.bossEvent.setProgress((float) remainingMillis / joinDuration);
518-            if (war.alliesBossEvent != null) {
519-                war.alliesBossEvent.setName(Component.literal(joinText));
520-                war.alliesBossEvent.setProgress((float) remainingMillis / joinDuration);
521-            }
522-        } else {
523-            long elapsedSeconds = (now - war.warStartTime) / 1000;
524-            long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
525-            long remainingSeconds = Math.max(0, warDurationSeconds - elapsedSeconds);
526-            int attackerLives = war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum();
527-            int defenderLives = war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();
528-            String timeStr = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
529-            String warText = "Â§6Â§lWar for " + war.getColony().getName() +
530-                    " - Time Remaining: " + timeStr +
531-                    " | Attackers: " + attackerLives +
532-                    " | Defenders: " + defenderLives;
533-            war.bossEvent.setName(Component.literal(warText));
534-            war.bossEvent.setProgress((float) remainingSeconds / warDurationSeconds);
535-            if (war.alliesBossEvent != null) {
536-                war.alliesBossEvent.removeAllPlayers();
537-                war.alliesBossEvent.setVisible(false);
538-            }
539-        }
540-    }
541-
542:    public static void finalizeWarStart(WarData war) {
543-        int attackerPlayerCount = war.getAttackerLives().size();
544-        int defenderPlayerCount = war.getDefenderLives().size();
545-
546-        if (attackerPlayerCount == 0 || defenderPlayerCount == 0) {
547-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
548-                Component cancelMsg = Component.literal("War cancelled due to lack of participants.")
549-                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
550-                broadcastToServer(cancelMsg);
551-            }
552-            endWar(war.getColony());
553-            return;
554-        }
555-
556-        if (Math.abs(attackerPlayerCount - defenderPlayerCount) > 1) {
557-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
558-                Component ratioMsg = Component.literal(
559-                        "Join phase ratio condition not met! Teams must be balanced (difference <= 1). Current: Attacker="
560-                                + attackerPlayerCount + ", Defender=" + defenderPlayerCount)
561-                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
562-                broadcastToServer(ratioMsg);
563-            }
564-            endWar(war.getColony()); // clean up â€” bare return would leak the war in ACTIVE_WARS
565-            return;
566-        }
567-
568-        war.bossEvent.removeAllPlayers();
569-        war.getAttackerLives().keySet().forEach(uuid -> {
570-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
571-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
572-                if (p != null)
573-                    war.bossEvent.addPlayer(p);
574-            }
575-        });
576-        war.getDefenderLives().keySet().forEach(uuid -> {
577-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
--
2381-                    if (!allParticipants.isEmpty()) {
2382-                        // Play countdown sound using Minecraft's bell sound
2383-                        for (UUID uuid : allParticipants) {
2384-                            ServerPlayer player = ServerLifecycleHooks.getCurrentServer() != null
2385-                                    ? ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid)
2386-                                    : null;
2387-                            if (player != null) {
2388-                                player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.get(), 1.0F,
2389-                                        1.0F);
2390-                            }
2391-                        }
2392-                    }
2393-
2394-                    // Notify remaining seconds
2395-                    notifyWarParticipants(war,
2396-                            Component
2397-                                    .literal("â± " + secondsLeft[0] + (secondsLeft[0] == 1 ? " second" : " seconds")
2398-                                            + " until war starts!")
2399-                                    .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)));
2400-
2401-                    secondsLeft[0]--;
2402-                } catch (Exception ex) {
2403-                    WARSYSTEM_LOGGER.error("Error in countdown timer: " + ex.getMessage(), ex);
2404-                }
2405-            }, Math.max(0, joinDurationMillis - 6000), 1000); // Start 6 seconds before join phase ends, repeat every 1
2406-                                                              // second
2407-        }
2408-
2409-        // Main timer to start the war when join phase ends
2410-        TickScheduler.scheduleDelayed(() -> {
2411-            if (war == null || war.getColony() == null) {
2412-                return;
2413-            }
2414-            war.setStatus(WarData.WarStatus.INWAR);
2415-            war.warStartTime = System.currentTimeMillis();
2416:            finalizeWarStart(war);
2417-            // Enable war actions for both sides
2418-            setWarInteractionPermissions(war.getColony(), true);
2419-            if (war.getAttackerColony() != null) {
2420-                setWarInteractionPermissions(war.getAttackerColony(), true);
2421-            }
2422-            startWarCountdown(war);
2423-        }, joinDurationMillis);
2424-        war.setAccepted(true);
2425-    }
2426-
2427-    private static void sendNotificationToColonyParticipants(IColony colony, Component message) {
2428-        if (colony == null || colony.getWorld() == null || colony.getWorld().getServer() == null) {
2429-            return;
2430-        }
2431-        colony.getPermissions().getPlayers().keySet().stream()
2432-                .filter(uuid -> isOfficerOrFriendly(colony, uuid))
2433-                .forEach(uuid -> {
2434-                    ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
2435-                    if (player != null) {
2436-                        player.sendSystemMessage(message);
2437-                    }
2438-                });
2439-    }
2440-
2441-    /**
2442-     * Sends a notification to all war participants from both colonies (attacker and
2443-     * defender),
2444-     * including officers, friends, and FTB team members if applicable.
2445-     */
2446-    // Helper to broadcast to entire server
2447-    private static void broadcastToServer(Component message) {
2448-        if (ServerLifecycleHooks.getCurrentServer() == null)
2449-            return;
2450-        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(message, false);
2451-    }
--
4402-            if (attackerColony != null) {
4403-                GuardResistanceHandler.applyResistanceToGuardsForWar(attackerColony);
4404-            }
4405-
4406-            startWarCountdown(warData);
4407-
4408-            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
4409-            long elapsed = now - warData.warStartTime;
4410-            long remaining = warDurationMs - elapsed;
4411-            if (remaining > 0) {
4412-                scheduleTimerWarnings(warData, remaining);
4413-            }
4414-
4415-            updateBossBar(warData);
4416-            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
4417-                    e.warID, defenderColony.getName(), remaining);
4418-        } else if (warData.getStatus() == WarData.WarStatus.JOINING) {
4419-            warData.alliesBossEvent = new ServerBossEvent(
4420-                    Component.literal("Joining War - " + defenderColony.getName()),
4421-                    BossEvent.BossBarColor.YELLOW,
4422-                    BossEvent.BossBarOverlay.PROGRESS);
4423-            warData.alliesBossEvent.setProgress(1.0f);
4424-            warData.alliesBossEvent.setVisible(true);
4425-
4426-            long remainingJoinMs = warData.getJoinPhaseEndTime() - now;
4427-            if (remainingJoinMs > 0) {
4428-                final int colonyId = e.defenderColonyId;
4429-                TickScheduler.scheduleDelayed(() -> {
4430-                    if (!ACTIVE_WARS.containsKey(colonyId))
4431-                        return;
4432-                    WarData w = ACTIVE_WARS.get(colonyId);
4433-                    if (w == null || w.getStatus() != WarData.WarStatus.JOINING)
4434-                        return;
4435-                    w.setStatus(WarData.WarStatus.INWAR);
4436-                    w.warStartTime = System.currentTimeMillis();
4437:                    finalizeWarStart(w);
4438-                    setWarInteractionPermissions(w.getColony(), true);
4439-                    if (w.getAttackerColony() != null) {
4440-                        setWarInteractionPermissions(w.getAttackerColony(), true);
4441-                    }
4442-                    startWarCountdown(w);
4443-                    long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
4444-                    scheduleTimerWarnings(w, warDurationMillis);
4445-                }, remainingJoinMs);
4446-            }
4447-
4448-            updateBossBar(warData);
4449-            WARSYSTEM_LOGGER.info("Restored JOINING war {} for colony {} ({} ms until war starts)",
4450-                    e.warID, defenderColony.getName(), remainingJoinMs);
4451-        }
4452-
4453-        Component restoreMsg = Component.literal("âš” War Restored: ")
4454-                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
4455-                .append(Component
4456-                        .literal("The war for " + finalDefenderColony.getName()
4457-                                + " has been resumed after server restart.")
4458-                        .withStyle(ChatFormatting.YELLOW));
4459-        broadcastToServer(restoreMsg);
4460-
4461-        return true;
4462-    }
4463-
4464-    private static Set<UUID> parseUUIDList(List<String> list) {
4465-        Set<UUID> result = new HashSet<>();
4466-        if (list != null) {
4467-            for (String s : list) {
4468-                try {
4469-                    result.add(UUID.fromString(s));
4470-                } catch (IllegalArgumentException ignored) {
4471-                }
4472-            }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 503ms:
8-     *  - VASSAL: classic war-vassalage (gold badge)
9-     *  - TAX_OCCUPIED: primary colony under besiege tax-occupation (red badge)
10-     *  - PROVISIONAL: secondary colony mid-conversion to permanent claim (orange badge)
11-     */
12:    public enum VassalKind { VASSAL, TAX_OCCUPIED, PROVISIONAL }
13-
14-    private final int vassalColonyId;
15-    private final String vassalColonyName;
16-    private final int tributeRate;
--
22-
23-    // UI state for claim button
24-    private int claimButtonX, claimButtonY, claimButtonWidth, claimButtonHeight;
25-
26:    public VassalIncomeData(int vassalColonyId, String vassalColonyName, int tributeRate,
27-                           int tributeOwed, int lastTribute, long lastPayment, boolean canClaim) {
28-        this(vassalColonyId, vassalColonyName, tributeRate, tributeOwed, lastTribute, lastPayment, canClaim, VassalKind.VASSAL);
29-    }
30-
31:    public VassalIncomeData(int vassalColonyId, String vassalColonyName, int tributeRate,
32-                           int tributeOwed, int lastTribute, long lastPayment, boolean canClaim,
33-                           VassalKind kind) {
34-        this.vassalColonyId = vassalColonyId;
35-        this.vassalColonyName = vassalColonyName;
--
40-        this.canClaim = canClaim;
41-        this.kind = kind != null ? kind : VassalKind.VASSAL;
42-    }
43-
44:    public VassalKind getKind() {
45-        return kind;
46-    }
47-    
48-    public int getVassalColonyId() { return vassalColonyId; }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 557ms:
103-        for (int i = 0; i < vassalSize; i++) {
104-            int vassalColonyId = buf.readInt();
105-            String vassalColonyName = buf.readUtf();
106-            int tributeRate = buf.readInt();
107-            int tributeOwed = buf.readInt();
108-            int lastTribute = buf.readInt();
109-            long lastPayment = buf.readLong();
110-            boolean canClaim = buf.readBoolean();
111:            int kindOrdinal = buf.readInt();
112-            VassalIncomeData.VassalKind kind;
113-            try {
114:                kind = VassalIncomeData.VassalKind.values()[kindOrdinal];
115-            } catch (Exception e) {
116-                kind = VassalIncomeData.VassalKind.VASSAL;
117-            }
118-
119:            this.vassalData.add(new VassalIncomeData(
120-                vassalColonyId, vassalColonyName, tributeRate,
121-                tributeOwed, lastTribute, lastPayment, canClaim, kind
122-            ));
123-        }
124-
125-        // Read event log data
126-        this.eventLogData = new HashMap<>();
127-        int logMapSize = buf.readInt();
--
188-        for (VassalIncomeData data : vassalData) {
189-            buf.writeInt(data.getVassalColonyId());
190-            buf.writeUtf(data.getVassalColonyName());
191-            buf.writeInt(data.getTributeRate());
192-            buf.writeInt(data.getTributeOwed());
193-            buf.writeInt(data.getLastTribute());
194-            buf.writeLong(data.getLastPayment());
195-            buf.writeBoolean(data.canClaim());
196:            buf.writeInt(data.getKind() != null ? data.getKind().ordinal() : 0);
197-        }
198-
199-        // Write event log data
200-        buf.writeInt(eventLogData.size());
201-        for (Map.Entry<Integer, List<EventLogEntry>> logEntry : eventLogData.entrySet()) {
202-            buf.writeInt(logEntry.getKey());
203-            List<EventLogEntry> entries = logEntry.getValue();
204-            buf.writeInt(entries.size());
--
216-        // Write all colony summaries (for spy target selection)
217-        buf.writeInt(allColonySummaries.size());
218-        for (ColonySummary s : allColonySummaries) {
219-            buf.writeInt(s.getId());
220-            buf.writeUtf(s.getName());
221-        }
222-    }
223-
224:    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
225-        NetworkEvent.Context context = supplier.get();
226-        context.enqueueWork(() -> {
227-            // This runs on the client side â€” wrap to avoid class-loading client-only
228-            // classes (Minecraft, TaxManagementScreen) on a dedicated server.
229-            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
230-                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
231-                if (mc.screen instanceof net.machiavelli.minecolonytax.gui.TaxManagementScreen) {
232-                    net.machiavelli.minecolonytax.gui.TaxManagementScreen screen =
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 506ms:
55-     */
56-    public enum OccupationMode {
57-        /** Expiry transfers full ownership to the occupier. Default for secondaries. */
58-        TRANSFER_PENDING,
59-        /** Expiry auto-reclaims â€” taxes route back to original owner, deed never moves. Primary colonies. */
60-        TAX_ONLY
61-    }
62-
63:    public static class OccupationData {
64-        public final int colonyId;
65-        public final String occupierUUID;
66-        public final String originalOwnerUUID;
67-        public final int occupierColonyId;
68-        public final long startTime;
69-        public final long expirationTime;
70-        public final String colonyName;
71-        public boolean reclamationAttempted;
--
95-            this.mode = mode;
96-        }
97-
98-        /** Returns the mode, defaulting to TRANSFER_PENDING for legacy save files. */
99-        public OccupationMode getMode() {
100-            return mode != null ? mode : OccupationMode.TRANSFER_PENDING;
101-        }
102-
103:        public boolean isExpired() {
104-            return System.currentTimeMillis() >= expirationTime;
105-        }
106-
107-        public long getRemainingTimeMs() {
108-            return Math.max(0, expirationTime - System.currentTimeMillis());
109-        }
110-
111-        public int getRemainingDays() {
--
281-    public static boolean isOccupied(int colonyId) {
282-        return ACTIVE_OCCUPATIONS.containsKey(colonyId);
283-    }
284-
285-    public static OccupationData getOccupation(int colonyId) {
286-        return ACTIVE_OCCUPATIONS.get(colonyId);
287-    }
288-
289:    public static Map<Integer, OccupationData> getActiveOccupations() {
290-        return Collections.unmodifiableMap(ACTIVE_OCCUPATIONS);
291-    }
292-
293-    public static boolean isOccupier(UUID playerUUID, int colonyId) {
294-        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
295-        return data != null && data.occupierUUID.equals(playerUUID.toString());
296-    }
297-
298:    public static List<OccupationData> getOccupiedByPlayer(UUID playerUUID) {
299-        List<OccupationData> result = new ArrayList<>();
300-        String uuid = playerUUID.toString();
301-        for (OccupationData data : ACTIVE_OCCUPATIONS.values()) {
302-            if (data.occupierUUID.equals(uuid)) {
303-                result.add(data);
304-            }
305-        }
306-        return result;
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 12 "getVassalIncomeData|vassalIncomes|data.getVassalColonyId|colonyId" src/main/java/net/machiavelli/minecolonytax/server/ColonyDataCollector.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 45 \"militiaSupport|setStatus\\(WarData\\.WarStatus\\.INWAR\\)|startWarCountdown\\(war\\)\" src/main/java/net/machiavelli/minecolonytax/WarSystem.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 8 "getOccupationTaxPercentage|collectoccupation|lastTaxCollectionTime|claimButton|canClaim" src/main/java/net/machiavelli/minecolonytax src/main/resources' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 10 "shortTagForKind|labelForKind|colorForKind|drawBadge|renderVassalDetail" src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 670ms:
34-            if (isPlayerManagerOfColony(player, colony)) {
35-                ColonyTaxData data = collectSingleColonyData(colony, playerId);
36-                if (data != null) {
37-                    colonyDataList.add(data);
38-                }
39-            }
40-        }
41-
42-        return colonyDataList;
43-    }
44-
45-    public static List<VassalIncomeData> collectVassalIncomeData(ServerPlayer player) {
46:        List<VassalIncomeData> vassalIncomes = new ArrayList<>();
47-        UUID playerId = player.getUUID();
48-
49-        try {
50-            List<VassalIncomeData> vassalData = net.machiavelli.minecolonytax.vassalization.VassalManager
51-                    .getVassalIncomeForPlayer(playerId);
52-
53-            for (VassalIncomeData data : vassalData) {
54-                boolean canClaim = true;
55-                try {
56-                    IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
57:                    IColony vassalColony = colonyManager.getColonyByWorld(data.getVassalColonyId(),
58-                            player.serverLevel());
59-                    if (vassalColony != null) {
60:                        WarData war = WarSystem.ACTIVE_WARS.get(data.getVassalColonyId());
61:                        boolean isBeingRaided = RaidManager.getActiveRaidForColony(data.getVassalColonyId()) != null;
62:                        boolean isBesieged = net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(data.getVassalColonyId());
63:                        boolean isOccupied = net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(data.getVassalColonyId()) != null;
64-                        canClaim = (war == null) && !isBeingRaided && !isBesieged && !isOccupied
65:                                && !TaxManager.isGenerationDisabled(data.getVassalColonyId());
66-                    }
67-                } catch (Exception e) {
68-                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
69-                        LOGGER.debug("Error checking claim status for vassal colony {}: {}", data.getVassalColonyName(),
70-                                e.getMessage());
71-                    }
72-                    canClaim = false;
73-                }
74-
75:                vassalIncomes.add(new VassalIncomeData(
76:                        data.getVassalColonyId(),
77-                        data.getVassalColonyName(),
78-                        data.getTributeRate(),
79-                        data.getTributeOwed(),
80-                        data.getLastTribute(),
81-                        data.getLastPayment(),
82-                        canClaim,
83-                        VassalIncomeData.VassalKind.VASSAL));
84-            }
85-
86-        } catch (Exception e) {
87-            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
88-                LOGGER.debug("Error collecting vassal income data for player {}: {}", player.getName().getString(),
--
100-
101-                // Map mode â†’ badge kind.
102-                VassalIncomeData.VassalKind kind =
103-                        occ.getMode() == net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationMode.TAX_ONLY
104-                        ? VassalIncomeData.VassalKind.TAX_OCCUPIED
105-                        : VassalIncomeData.VassalKind.PROVISIONAL;
106-
107-                // Use the configured occupation tax percentage as the displayed "rate"
108-                // so the row is honest about what's being collected.
109-                int tributeRate = (int) Math.round(
110-                        net.machiavelli.minecolonytax.TaxConfig.getOccupationTaxPercentage() * 100);
111-
112:                vassalIncomes.add(new VassalIncomeData(
113:                        occ.colonyId,
114-                        occ.colonyName,
115-                        tributeRate,
116-                        0,                              // no pending one-shot tribute model here
117-                        0,                              // last-tribute not tracked separately for occupations
118-                        occ.lastTaxCollectionTime,      // re-use for "last payment" label
119-                        true,                           // can collect via /wnt collectoccupation
120-                        kind));
121-            }
122-        } catch (Exception e) {
123-            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
124-                LOGGER.debug("Error collecting occupation rows for vassal feed: {}", e.getMessage());
125-            }
126-        }
127-
128:        return vassalIncomes;
129-    }
130-
131-    private static boolean isPlayerManagerOfColony(ServerPlayer player, IColony colony) {
132-        var rank = colony.getPermissions().getRank(player.getUUID());
133-        return rank != null && rank.isColonyManager();
134-    }
135-
136-    private static ColonyTaxData collectSingleColonyData(IColony colony, UUID playerId) {
137-        try {
138:            int colonyId = colony.getID();
139-            String colonyName = colony.getName();
140-
141-            int taxBalance = TaxManager.getStoredTaxForColony(colony);
142-            int maxTaxRevenue = TaxConfig.getMaxTaxRevenue();
143-
144-            int buildingCount = 0;
145-            int guardCount = 0;
146-            int guardTowerCount = 0;
147-
148-            for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
149-                if (building.getBuildingLevel() > 0 && building.isBuilt()) {
150-                    buildingCount++;
--
164-                        if (building.getAllAssignedCitizen() != null) {
165-                            guardCount += building.getAllAssignedCitizen().size();
166-                        }
167-                    } catch (Exception ignored) {
168-                        // not all building types support getAllAssignedCitizen
169-                    }
170-                }
171-            }
172-
173-            boolean isAtWar = false;
174-            boolean isBeingRaided = false;
175-
176:            WarData war = WarSystem.ACTIVE_WARS.get(colonyId);
177-            if (war == null) {
178-                // Also show at-war if this colony is the attacker
179-                for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
180:                    if (wd.getAttackerColony() != null && wd.getAttackerColony().getID() == colonyId) {
181-                        war = wd;
182-                        break;
183-                    }
184-                }
185-            }
186-            isAtWar = (war != null);
187:            isBeingRaided = RaidManager.getActiveRaidForColony(colonyId) != null;
188:            boolean isBesieged = net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(colonyId);
189:            boolean isOccupied = net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(colonyId) != null;
190:            boolean canClaimTax = !isAtWar && !isBeingRaided && !isBesieged && !isOccupied && !TaxManager.isGenerationDisabled(colonyId);
191-
192-            boolean isVassal = false;
193-            int vassalTributeRate = 0;
194-            boolean hasVassals = false;
195-            int vassalCount = 0;
196-
197-            try {
198:                isVassal = net.machiavelli.minecolonytax.vassalization.VassalManager.isColonyVassal(colonyId);
199-                if (isVassal) {
200-                    vassalTributeRate = net.machiavelli.minecolonytax.vassalization.VassalManager
201:                            .getVassalTributeRate(colonyId);
202-                }
203-
204-                vassalCount = net.machiavelli.minecolonytax.vassalization.VassalManager.countVassalsForPlayer(playerId);
205-                hasVassals = vassalCount > 0;
206-
207-                if (isVassal || hasVassals) {
208-                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
209-                        LOGGER.debug("Colony {} (Player: {}) - isVassal: {}, vassalRate: {}, hasVassals: {}, vassalCount: {}",
210:                                colonyId, playerId, isVassal, vassalTributeRate, hasVassals, vassalCount);
211-                    }
212-                }
213-
214-            } catch (Exception e) {
215-                if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
216-                    LOGGER.debug("Error checking vassal status for colony {}: {}", colonyName, e.getMessage());
217-                }
218-            }
219-
220-            long lastTaxGeneration = System.currentTimeMillis();
221-            int debtAmount = taxBalance < 0 ? Math.abs(taxBalance) : 0;
222-
223-            double colonyHappiness = TaxManager.calculateColonyAverageHappiness(colony);
224-            double happinessMultiplier = TaxConfig.calculateHappinessTaxMultiplier(colonyHappiness);
225-            int approximateRevenue = calculateApproximateRevenue(buildingCount, guardTowerCount, happinessMultiplier);
226-
227-            boolean isOwner = colony.getPermissions().getOwner().equals(playerId);
228:            String taxPolicy = TaxPolicyManager.getPolicy(colonyId).name();
229-
230-            return new ColonyTaxData(
231:                    colonyId, colonyName, taxBalance, maxTaxRevenue,
232-                    buildingCount, guardCount, guardTowerCount,
233-                    canClaimTax, isAtWar, isBeingRaided,
234-                    isVassal, vassalTributeRate, hasVassals, vassalCount,
235-                    lastTaxGeneration, debtAmount, approximateRevenue, isOwner,
236-                    taxPolicy, colonyHappiness, happinessMultiplier,
237-                    isBesieged, isOccupied);
238-
239-        } catch (Exception e) {
240-            if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
241-                LOGGER.error("Error collecting data for colony {}: {}", colony.getName(), e.getMessage());
242-            }
243-            return null;
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 666ms:
560-                                + attackerPlayerCount + ", Defender=" + defenderPlayerCount)
561-                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
562-                broadcastToServer(ratioMsg);
563-            }
564-            endWar(war.getColony()); // clean up â€” bare return would leak the war in ACTIVE_WARS
565-            return;
566-        }
567-
568-        war.bossEvent.removeAllPlayers();
569-        war.getAttackerLives().keySet().forEach(uuid -> {
570-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
571-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
572-                if (p != null)
573-                    war.bossEvent.addPlayer(p);
574-            }
575-        });
576-        war.getDefenderLives().keySet().forEach(uuid -> {
577-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
578-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
579-                if (p != null)
580-                    war.bossEvent.addPlayer(p);
581-            }
582-        });
583-
584-        if (war.alliesBossEvent != null) {
585-            war.alliesBossEvent.removeAllPlayers();
586-            war.alliesBossEvent.setVisible(false);
587-        }
588-
589-        war.getAttackerLives().keySet().forEach(uuid -> {
590-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
591-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
592-                if (p != null)
593-                    assignWarGroup(p);
594-            }
595-        });
596-        war.getDefenderLives().keySet().forEach(uuid -> {
597-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
598-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
599-                if (p != null)
600-                    assignWarGroup(p);
601-            }
602-        });
603-
604-        war.warStartTime = System.currentTimeMillis();
605:        war.setStatus(WarData.WarStatus.INWAR);
606-        updateBossBar(war);
607-        // Apply glow to both defender and attacker guards for clear visibility
608-        applyGuardGlow(war.getColony());
609-        if (war.getAttackerColony() != null) {
610-            applyGuardGlow(war.getAttackerColony());
611-        }
612-        applyWarGlowToParticipants(war);
613-
614-        // Militia upgrade reinforcements â€” spawn on BOTH sides if either colony
615-        // has the upgrade. Defender side primarily (per design), attacker side
616-        // optionally so an upgraded attacker colony also gets the boost.
617-        // Idempotent â€” re-entry checks the existing set is empty first.
618:        if (war.militiaSupport.isEmpty()) {
619-            try {
620-                // Defender militia
621-                if (war.getColony() != null) {
622-                    int defenderGuardCount = (int) war.getColony().getCitizenManager().getCitizens().stream()
623-                            .filter(c -> c.getJob() != null && c.getJob().isGuard())
624-                            .count();
625-                    // No specific attacker-target â€” let the militia find via vanilla aggro
626-                    net.machiavelli.minecolonytax.militia.MilitiaSpawner.spawnReinforcements(
627-                            war.getColony(), defenderGuardCount, null,
628:                            war.militiaSupport, TaxConfig.WAR_DURATION_MINUTES.get());
629-                }
630-                // Attacker militia (their colony also benefits from the upgrade)
631-                if (war.getAttackerColony() != null) {
632-                    int attackerGuardCount = (int) war.getAttackerColony().getCitizenManager().getCitizens().stream()
633-                            .filter(c -> c.getJob() != null && c.getJob().isGuard())
634-                            .count();
635-                    net.machiavelli.minecolonytax.militia.MilitiaSpawner.spawnReinforcements(
636-                            war.getAttackerColony(), attackerGuardCount, null,
637:                            war.militiaSupport, TaxConfig.WAR_DURATION_MINUTES.get());
638-                }
639-            } catch (Exception e) {
640-                WARSYSTEM_LOGGER.warn("Militia spawn during war start failed: {}", e.getMessage());
641-            }
642-        }
643-
644-        // Apply resistance effects to defending guards during war
645-        GuardResistanceHandler.applyResistanceToGuardsForWar(war.getColony());
646-        if (war.getAttackerColony() != null) {
647-            GuardResistanceHandler.applyResistanceToGuardsForWar(war.getAttackerColony());
648-        }
649-
650-        // Initialize militia system for guard tracking and citizen conversion in BOTH
651-        // colonies
652-        initializeWarMilitiaSystem(war);
653-        activateWarMilitia(war);
654-        if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
655-            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
656-                    : "Attacking Forces";
657-            String defenderColonyName = war.getColony().getName();
658-
659-            Component warBeginMsg = Component.empty()
660-                    .append(Component.translatable("war.begin.title").withStyle(ChatFormatting.GOLD,
661-                            ChatFormatting.BOLD))
662-                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
663-                    .append(Component.literal("\n"))
664-                    .append(Component.translatable("war.begin.body", attackerColonyName, defenderColonyName)
665-                            .withStyle(ChatFormatting.YELLOW))
666-                    .append(Component.literal("\n"))
667-                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
668-            broadcastToServer(warBeginMsg);
669-        }
670-        long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
671-        scheduleTimerWarnings(war, warDurationMillis);
672-    }
673-
674-    private static void assignWarGroup(ServerPlayer player) {
675-        if (player == null || player.getServer() == null)
676-            return;
677-        if (TaxConfig.ENABLE_LP_GROUP_SWITCHING.get()) {
678-            String command = "lp user " + player.getName().getString() + " parent set war";
679-            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
680-        }
681-    }
682-
--
1262-        }
1263-    }
1264-
1265-    public static void endWar(IColony colony) {
1266-        if (colony == null) return;
1267-        // Finding 10: make endWar idempotent. Atomically remove the WarData from
1268-        // ACTIVE_WARS; if it was already removed (a concurrent endWar call from a
1269-        // different code path), bail out â€” re-running the rest of this method
1270-        // would double-fire demotions, history records, treasury cleanup, etc.
1271-        WarData warData = ACTIVE_WARS.remove(colony.getID());
1272-        if (warData == null) {
1273-            // Already ended â€” nothing to do. (Previously this code re-ran all
1274-            // cleanup with warData == null, producing best-effort no-ops scattered
1275-            // with NPE risk.)
1276-            return;
1277-        }
1278-
1279-        // Remove resistance effects from guards in both colonies
1280-        if (warData != null) {
1281-            GuardResistanceHandler.removeResistanceFromGuardsForWar(warData.getColony());
1282-            if (warData.getAttackerColony() != null) {
1283-                GuardResistanceHandler.removeResistanceFromGuardsForWar(warData.getAttackerColony());
1284-            }
1285-
1286-            // Clean up militia system for both colonies
1287-            cleanupWarMilitiaSystem(warData);
1288-
1289-            // Restore all explosion-damaged blocks ledgered for this war.
1290-            // Bug #8 fix: previously the ledger only accumulated and never restored.
1291-            try {
1292-                if (warData.getColony() != null && warData.getColony().getWorld() != null) {
1293-                    net.machiavelli.minecolonytax.siege.WarBlockLedger.restoreWarDamage(
1294-                            warData.getWarID(), warData.getColony().getWorld());
1295-                }
1296-            } catch (Exception e) {
1297-                WARSYSTEM_LOGGER.error("Failed to restore war block ledger for war {}", warData.getWarID(), e);
1298-            }
1299-
1300-            // Drop any Town Hall demolition hit state so it doesn't leak between wars.
1301-            try {
1302-                net.machiavelli.minecolonytax.siege.TownHallDemolitionObjective.onWarEnded(warData.getWarID());
1303-            } catch (Exception ignored) {}
1304-
1305-            // Despawn militia-upgrade reinforcements (NOT victory-counted, just combat extenders).
1306-            try {
1307:                net.machiavelli.minecolonytax.militia.MilitiaSpawner.despawnAll(warData.militiaSupport);
1308-            } catch (Exception e) {
1309-                WARSYSTEM_LOGGER.warn("Failed to despawn war militia: {}", e.getMessage());
1310-            }
1311-        }
1312-
1313-        // Disable war actions for both sides
1314-        setWarInteractionPermissions(colony, false);
1315-
1316-        // Also disable for attacker colony if it exists
1317-        if (warData != null && warData.getAttackerColony() != null) {
1318-            setWarInteractionPermissions(warData.getAttackerColony(), false);
1319-        }
1320-
1321-        // Demote war participants out of the Hostile rank on both colonies.
1322-        // Attackers were assigned hostile on the defender colony; defenders on the attacker colony.
1323-        if (warData != null) {
1324-            if (warData.getAttackerLives() != null) {
1325-                demoteParticipantsFromHostile(colony, warData.getAttackerLives().keySet());
1326-            }
1327-            if (warData.getAttackerColony() != null && warData.getDefenderLives() != null) {
1328-                demoteParticipantsFromHostile(warData.getAttackerColony(), warData.getDefenderLives().keySet());
1329-            }
1330-        }
1331-
1332-        // (Removed from ACTIVE_WARS at the top of this method as part of the
1333-        // Finding 10 idempotency fix â€” no further read/remove needed.)
1334-
1335-        // Restore Hostile rank to pre-war state now that the war is no longer active
1336-        net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(colony);
1337-        if (warData != null && warData.getAttackerColony() != null) {
1338-            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(warData.getAttackerColony());
1339-        }
1340-
1341-        // Remove War Exhaustion status and start recovery period
1342-        net.machiavelli.minecolonytax.economy.WarExhaustionManager.removeWarStatus(colony.getID());
1343-        if (warData != null && warData.getAttackerColony() != null) {
1344-            net.machiavelli.minecolonytax.economy.WarExhaustionManager
1345-                    .removeWarStatus(warData.getAttackerColony().getID());
1346-        }
1347-
1348-        // Clear war chest roles for both sides
1349-        net.machiavelli.minecolonytax.economy.TreasuryManager.clearColonyRole(colony.getID());
1350-        if (warData != null && warData.getAttackerColony() != null) {
1351-            net.machiavelli.minecolonytax.economy.TreasuryManager.clearColonyRole(warData.getAttackerColony().getID());
1352-        }
--
2369-            TickScheduler.scheduleRepeating(() -> {
2370-                try {
2371-                    if (war == null || war.getColony() == null || !war.isJoinPhaseActive() || secondsLeft[0] < 0) {
2372-                        return; // Task will be cleaned up when war starts or ends
2373-                    }
2374-
2375-                    // Play countdown sound to all war participants
2376-                    Set<UUID> allParticipants = new HashSet<>();
2377-                    allParticipants.addAll(war.getAttackerLives().keySet());
2378-                    allParticipants.addAll(war.getDefenderLives().keySet());
2379-
2380-                    // Only play sound if there are participants
2381-                    if (!allParticipants.isEmpty()) {
2382-                        // Play countdown sound using Minecraft's bell sound
2383-                        for (UUID uuid : allParticipants) {
2384-                            ServerPlayer player = ServerLifecycleHooks.getCurrentServer() != null
2385-                                    ? ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid)
2386-                                    : null;
2387-                            if (player != null) {
2388-                                player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.get(), 1.0F,
2389-                                        1.0F);
2390-                            }
2391-                        }
2392-                    }
2393-
2394-                    // Notify remaining seconds
2395-                    notifyWarParticipants(war,
2396-                            Component
2397-                                    .literal("â± " + secondsLeft[0] + (secondsLeft[0] == 1 ? " second" : " seconds")
2398-                                            + " until war starts!")
2399-                                    .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)));
2400-
2401-                    secondsLeft[0]--;
2402-                } catch (Exception ex) {
2403-                    WARSYSTEM_LOGGER.error("Error in countdown timer: " + ex.getMessage(), ex);
2404-                }
2405-            }, Math.max(0, joinDurationMillis - 6000), 1000); // Start 6 seconds before join phase ends, repeat every 1
2406-                                                              // second
2407-        }
2408-
2409-        // Main timer to start the war when join phase ends
2410-        TickScheduler.scheduleDelayed(() -> {
2411-            if (war == null || war.getColony() == null) {
2412-                return;
2413-            }
2414:            war.setStatus(WarData.WarStatus.INWAR);
2415-            war.warStartTime = System.currentTimeMillis();
2416-            finalizeWarStart(war);
2417-            // Enable war actions for both sides
2418-            setWarInteractionPermissions(war.getColony(), true);
2419-            if (war.getAttackerColony() != null) {
2420-                setWarInteractionPermissions(war.getAttackerColony(), true);
2421-            }
2422:            startWarCountdown(war);
2423-        }, joinDurationMillis);
2424-        war.setAccepted(true);
2425-    }
2426-
2427-    private static void sendNotificationToColonyParticipants(IColony colony, Component message) {
2428-        if (colony == null || colony.getWorld() == null || colony.getWorld().getServer() == null) {
2429-            return;
2430-        }
2431-        colony.getPermissions().getPlayers().keySet().stream()
2432-                .filter(uuid -> isOfficerOrFriendly(colony, uuid))
2433-                .forEach(uuid -> {
2434-                    ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
2435-                    if (player != null) {
2436-                        player.sendSystemMessage(message);
2437-                    }
2438-                });
2439-    }
2440-
2441-    /**
2442-     * Sends a notification to all war participants from both colonies (attacker and
2443-     * defender),
2444-     * including officers, friends, and FTB team members if applicable.
2445-     */
2446-    // Helper to broadcast to entire server
2447-    private static void broadcastToServer(Component message) {
2448-        if (ServerLifecycleHooks.getCurrentServer() == null)
2449-            return;
2450-        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(message, false);
2451-    }
2452-
2453-    private static void sendNotificationToWarParticipants(IColony defenderColony, IColony attackerColony,
2454-            Component message) {
2455-        if (defenderColony == null || defenderColony.getWorld() == null
2456-                || defenderColony.getWorld().getServer() == null) {
2457-            return;
2458-        }
2459-
2460-        Set<UUID> notifiedPlayers = new HashSet<>();
2461-        MinecraftServer server = defenderColony.getWorld().getServer();
2462-
2463-        // Notify defender colony officers and friends
2464-        defenderColony.getPermissions().getPlayers().keySet().stream()
2465-                .filter(uuid -> isOfficerOrFriendly(defenderColony, uuid))
2466-                .forEach(uuid -> {
2467-                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
--
3009-                attackerTeam != null ? FtbTeamsCompat.getTeamId(attackerTeam) : null,
3010-                null, // No defender team
3011-                System.currentTimeMillis(),
3012-                null, // No boss event yet - created below
3013-                targetColony,
3014-                attackerColony);
3015-
3016-        // Mark this as an offline outpost war
3017-        warData.setOfflineOutpostWar(true);
3018-
3019-        // IMPORTANT: Set attacker guards to 0 for offline outpost wars.
3020-        // The attacker's guards are at their HOME colony, not on the battlefield.
3021-        // Without this, the victory check (attackerLives==0 && attackerGuards==0)
3022-        // would never trigger - making the attacker invincible!
3023-        warData.remainingAttackerGuards = 0;
3024-
3025-        ACTIVE_WARS.put(targetColony.getID(), warData);
3026-        int _defStartBal = TaxManager.getStoredTaxForColonyId(targetColony.getID());
3027-        HistoryManager.logWithBalance(targetColony.getID(), "WAR",
3028-                "War started â€” attacked by " + attacker.getName().getString(),
3029-                _defStartBal, _defStartBal);
3030-        if (attackerColony != null) {
3031-            int _atkStartBal = TaxManager.getStoredTaxForColonyId(attackerColony.getID());
3032-            HistoryManager.logWithBalance(attackerColony.getID(), "WAR",
3033-                    "War started â€” attacking " + targetColony.getName(),
3034-                    _atkStartBal, _atkStartBal);
3035-        }
3036-
3037-        // Mark defender for home-field drain advantage + schedule drain
3038-        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(targetColony.getID());
3039-        scheduleTreasuryDrain(warData, targetColony, attackerColony);
3040-
3041-        // Add attacker to the war
3042-        warData.getAttackerLives().put(attacker.getUUID(), TaxConfig.PLAYER_LIVES_IN_WAR.get());
3043-
3044-        // Create boss bar for the attacker
3045-        warData.bossEvent = new ServerBossEvent(
3046-                Component.literal("Outpost Assault - " + targetColony.getName()),
3047-                BossEvent.BossBarColor.RED,
3048-                BossEvent.BossBarOverlay.PROGRESS);
3049-        warData.bossEvent.setProgress(1.0f);
3050-        warData.bossEvent.setVisible(true);
3051-        warData.bossEvent.addPlayer(attacker);
3052-
3053-        // Set war status to IN WAR immediately (no join phase for offline attacks)
3054:        warData.setStatus(WarData.WarStatus.INWAR);
3055-        warData.warStartTime = System.currentTimeMillis();
3056-
3057-        // Enable war interactions
3058-        setWarInteractionPermissions(targetColony, true);
3059-        setWarInteractionPermissions(attackerColony, true);
3060-
3061-        // Assign hostile rank to attacker in target colony
3062-        Rank hostileRank = targetColony.getPermissions().getRankHostile();
3063-        targetColony.getPermissions().addPlayer(attacker.getGameProfile(), hostileRank);
3064-
3065-        // Apply glow effects and resistance buffs
3066-        // TODO: Implement applyGlowEffect and applyGuardResistance for offline outpost
3067-        // wars
3068-        // applyGlowEffect(attacker, targetColony);
3069-        // applyGuardResistance(targetColony);
3070-
3071-        // Start the war countdown
3072-        startWarCountdown(warData);
3073-
3074-        // Notify the attacker
3075-        attacker.sendSystemMessage(
3076-                Component.literal("âš” ASSAULT BEGUN! Defeat the colony guards or hold the outpost until time expires!")
3077-                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
3078-        attacker.sendSystemMessage(Component
3079-                .literal("âš  You have " + TaxConfig.PLAYER_LIVES_IN_WAR.get()
3080-                        + " lives. Lose them all and the outpost's owner wins!")
3081-                .withStyle(ChatFormatting.RED));
3082-
3083-        WARSYSTEM_LOGGER.info("Offline outpost war started: {} vs {} (defender offline)",
3084-                attackerColony.getName(), targetColony.getName());
3085-    }
3086-
3087-    public static int processWageWarRequestWithExtortion(ServerPlayer attacker, IColony targetColony,
3088-            CommandSourceStack source, int extortionPercent) {
3089-        Level level = source.getLevel();
3090-
3091-        int targetGuards = countGuards(targetColony);
3092-        if (targetGuards < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()) {
3093-            source.sendFailure(Component.literal("Target colony must have at least "
3094-                    + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get() + " guards! (Found: " + targetGuards + ")"));
3095-            return 0;
3096-        }
3097-
3098-        // Find a valid attacker colony using the new helper
3099-        IColony attackerColony = findValidAttackerColony(attacker, targetColony, true);
--
4390-        }
4391-
4392-        final IColony finalDefenderColony = defenderColony;
4393-
4394-        if (warData.getStatus() == WarData.WarStatus.INWAR) {
4395-            applyWarGlowToParticipants(warData);
4396-            applyGuardGlow(defenderColony);
4397-            if (attackerColony != null) {
4398-                applyGuardGlow(attackerColony);
4399-            }
4400-
4401-            GuardResistanceHandler.applyResistanceToGuardsForWar(defenderColony);
4402-            if (attackerColony != null) {
4403-                GuardResistanceHandler.applyResistanceToGuardsForWar(attackerColony);
4404-            }
4405-
4406-            startWarCountdown(warData);
4407-
4408-            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
4409-            long elapsed = now - warData.warStartTime;
4410-            long remaining = warDurationMs - elapsed;
4411-            if (remaining > 0) {
4412-                scheduleTimerWarnings(warData, remaining);
4413-            }
4414-
4415-            updateBossBar(warData);
4416-            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
4417-                    e.warID, defenderColony.getName(), remaining);
4418-        } else if (warData.getStatus() == WarData.WarStatus.JOINING) {
4419-            warData.alliesBossEvent = new ServerBossEvent(
4420-                    Component.literal("Joining War - " + defenderColony.getName()),
4421-                    BossEvent.BossBarColor.YELLOW,
4422-                    BossEvent.BossBarOverlay.PROGRESS);
4423-            warData.alliesBossEvent.setProgress(1.0f);
4424-            warData.alliesBossEvent.setVisible(true);
4425-
4426-            long remainingJoinMs = warData.getJoinPhaseEndTime() - now;
4427-            if (remainingJoinMs > 0) {
4428-                final int colonyId = e.defenderColonyId;
4429-                TickScheduler.scheduleDelayed(() -> {
4430-                    if (!ACTIVE_WARS.containsKey(colonyId))
4431-                        return;
4432-                    WarData w = ACTIVE_WARS.get(colonyId);
4433-                    if (w == null || w.getStatus() != WarData.WarStatus.JOINING)
4434-                        return;
4435:                    w.setStatus(WarData.WarStatus.INWAR);
4436-                    w.warStartTime = System.currentTimeMillis();
4437-                    finalizeWarStart(w);
4438-                    setWarInteractionPermissions(w.getColony(), true);
4439-                    if (w.getAttackerColony() != null) {
4440-                        setWarInteractionPermissions(w.getAttackerColony(), true);
4441-                    }
4442-                    startWarCountdown(w);
4443-                    long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
4444-                    scheduleTimerWarnings(w, warDurationMillis);
4445-                }, remainingJoinMs);
4446-            }
4447-
4448-            updateBossBar(warData);
4449-            WARSYSTEM_LOGGER.info("Restored JOINING war {} for colony {} ({} ms until war starts)",
4450-                    e.warID, defenderColony.getName(), remainingJoinMs);
4451-        }
4452-
4453-        Component restoreMsg = Component.literal("âš” War Restored: ")
4454-                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
4455-                .append(Component
4456-                        .literal("The war for " + finalDefenderColony.getName()
4457-                                + " has been resumed after server restart.")
4458-                        .withStyle(ChatFormatting.YELLOW));
4459-        broadcastToServer(restoreMsg);
4460-
4461-        return true;
4462-    }
4463-
4464-    private static Set<UUID> parseUUIDList(List<String> list) {
4465-        Set<UUID> result = new HashSet<>();
4466-        if (list != null) {
4467-            for (String s : list) {
4468-                try {
4469-                    result.add(UUID.fromString(s));
4470-                } catch (IllegalArgumentException ignored) {
4471-                }
4472-            }
4473-        }
4474-        return result;
4475-    }
4476-}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 666ms:
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-109-        if (activeClaimingRaids.containsKey(colony.getID())) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-110-            claimingPlayer.sendSystemMessage(Component.literal("A claiming raid is already in progress for this colony!")
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-111-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-112-            return false;
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-113-        }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-114-
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-115-        // Check claiming requirements (includes former owner/officer bypass)
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-116-        ClaimingRequirementResult requirementResult = checkClaimingRequirements(claimingPlayer, colony);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:117:        if (!requirementResult.canClaim) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-118-            claimingPlayer.sendSystemMessage(Component.literal("Cannot claim colony: " + requirementResult.message)
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-119-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-120-            return false;
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-121-        }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-122-        
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-123-        // Log if this is a former owner/officer claiming back their colony
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-124-        boolean isFormerMember = ColonyAbandonmentManager.wasFormerOwnerOrOfficer(colony.getID(), claimingPlayer.getUUID());
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-125-        if (isFormerMember) {
--
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1233-        return canPlayerClaimColony(player, null);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1234-    }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1235-    
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1236-    /**
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1237-     * Check if a player can claim a specific abandoned colony.
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1238-     */
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1239-    public static boolean canPlayerClaimColony(ServerPlayer player, IColony targetColony) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1240-        ClaimingRequirementResult result = checkClaimingRequirements(player, targetColony);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:1241:        return result.canClaim;
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1242-    }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1243-    
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1244-    /**
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1245-     * Check if a player meets the claiming requirements and return detailed info.
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1246-     */
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1247-    public static ClaimingRequirementResult checkClaimingRequirements(ServerPlayer player) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1248-        return checkClaimingRequirements(player, null);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1249-    }
--
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1441-            LOGGER.error("EMERGENCY CLEANUP: Error during cleanup of failed raids: {}", e.getMessage());
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1442-        }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1443-    }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1444-    
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1445-    /**
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1446-     * Result class for claiming requirement checks.
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1447-     */
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1448-    public static class ClaimingRequirementResult {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:1449:        public final boolean canClaim;
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1450-        public final String message;
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1451-        
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:1452:        public ClaimingRequirementResult(boolean canClaim, String message) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:1453:            this.canClaim = canClaim;
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1454-            this.message = message;
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1455-        }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1456-    }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-1457-}
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1900-                                    ChatFormatting.BOLD))
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1901-                            .append(Component.literal(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1902-                                    "\nYou successfully defended your colony and captured the attacker's wagered colony ")
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1903-                                    .withStyle(ChatFormatting.GREEN))
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1904-                            .append(Component.literal(war.getAttackerColony().getName()).withStyle(ChatFormatting.GOLD,
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1905-                                    ChatFormatting.BOLD))
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1906-                            .append(Component.literal("!").withStyle(ChatFormatting.GREEN))
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1907-                            .append(Component.literal("\nYou can now collect "
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1908:                                    + (int) (TaxConfig.getOccupationTaxPercentage() * 100) + "% of their taxes!")
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1909-                                    .withStyle(ChatFormatting.YELLOW));
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1910-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1911-                    // Notify attacker
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1912-                    ServerPlayer attackerPlayer = war.getColony().getWorld().getServer().getPlayerList()
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1913-                            .getPlayer(war.getAttacker());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1914-                    if (attackerPlayer != null) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1915-                        attackerPlayer.sendSystemMessage(wagerLostMsg);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1916-                    }
--
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-237-
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-238-    private void requestColonyData() {
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-239-        NetworkHandler.sendToServer(new RequestColonyDataPacket());
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-240-        officerData.clear();
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-241-    }
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-242-
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-243-    private void claimAllTaxes() {
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-244-        for (ColonyTaxData colony : colonies) {
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java:245:            if (colony.getTaxBalance() > 0 && colony.canClaimTax()) {
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-246-                NetworkHandler.CHANNEL.sendToServer(new ClaimTaxPacket(colony.getColonyId(), -1));
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-247-            }
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-248-        }
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-249-        requestColonyData();
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-250-    }
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-251-
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-252-    // ========== Tab switching ==========
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java-253-
--
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2721-                        player.sendSystemMessage(colonyInfo);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2722-                }
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2723-
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2724-                // Show player's eligibility status
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2725-                player.sendSystemMessage(Component.literal(""));
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2726-                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.ClaimingRequirementResult eligibility = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2727-                                .checkClaimingRequirements(player);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2728-
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2729:                if (eligibility.canClaim) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2730-                        player.sendSystemMessage(Component.literal("âœ“ You are eligible to claim colonies!")
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2731-                                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2732-                        player.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2733-                                        Component.literal("Use '/wnt claimcolony <colony>' to start a claiming raid!")
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2734-                                                        .withStyle(ChatFormatting.GREEN));
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2735-                } else {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2736-                        player.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2737-                                        Component.literal("âœ— You cannot claim colonies: " + eligibility.message)
--
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2768-                        return 0;
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2769-                }
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2770-
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2771-                // Check if player meets claiming requirements (pass target colony for former
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2772-                // owner/officer bypass)
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2773-                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.ClaimingRequirementResult requirementResult = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2774-                                .checkClaimingRequirements(player, targetColony);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2775-
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2776:                if (!requirementResult.canClaim) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2777-                        player.sendSystemMessage(Component.literal("Cannot claim colony: " + requirementResult.message)
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2778-                                        .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2779-                        return 0;
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2780-                }
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2781-
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2782-                // Start the claiming raid
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2783-                boolean success = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2784-                                .startClaimingRaid(targetColony, player);
--
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2869-
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2870-                // Check claiming requirements
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2871-                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.ClaimingRequirementResult eligibility = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2872-                                .checkClaimingRequirements(player);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2873-
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2874-                player.sendSystemMessage(Component.literal("=== Colony Claiming Status ===")
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2875-                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2876-
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2877:                if (eligibility.canClaim) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2878-                        player.sendSystemMessage(Component.literal("âœ“ You are eligible to claim colonies!")
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2879-                                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2880-                        player.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2881-                                        Component.literal("Use '/wnt claimcolony <colony>' to start a claiming raid!")
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2882-                                                        .withStyle(ChatFormatting.GREEN));
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2883-                } else {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2884-                        player.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-2885-                                        Component.literal("âœ— You cannot claim colonies: " + eligibility.message)
--
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2654-        public static boolean isOccupationSystemEnabled() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2655-                return ENABLE_OCCUPATION_SYSTEM.get();
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2656-        }
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2657-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2658-        public static int getOccupationDurationDays() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2659-                return OCCUPATION_DURATION_DAYS.get();
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2660-        }
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2661-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2662:        public static double getOccupationTaxPercentage() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2663-                return OCCUPATION_TAX_PERCENTAGE.get();
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2664-        }
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2665-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2666-        public static boolean isOutpostVulnerabilityEnabled() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2667-                return ENABLE_OUTPOST_VULNERABILITY.get();
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2668-        }
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2669-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2670-        public static boolean isColonyWagerEnabled() {
--
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-1-package net.machiavelli.minecolonytax.gui.data;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-2-
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-3-import java.util.UUID;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-4-
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-5-public class OfficerData {
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-6-    private final UUID playerId;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-7-    private final String playerName;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-8-    private final String rank;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java:9:    private final boolean canClaimTax;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-10-    private final boolean isOnline;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-11-    private final long lastSeen;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-12-    
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java:13:    public OfficerData(UUID playerId, String playerName, String rank, boolean canClaimTax, boolean isOnline, long lastSeen) {
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-14-        this.playerId = playerId;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-15-        this.playerName = playerName;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-16-        this.rank = rank;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java:17:        this.canClaimTax = canClaimTax;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-18-        this.isOnline = isOnline;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-19-        this.lastSeen = lastSeen;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-20-    }
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-21-    
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-22-    public UUID getPlayerId() { return playerId; }
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-23-    public String getPlayerName() { return playerName; }
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-24-    public String getRank() { return rank; }
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java:25:    public boolean canClaimTax() { return canClaimTax; }
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-26-    public boolean isOnline() { return isOnline; }
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-27-    public long getLastSeen() { return lastSeen; }
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-28-    
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-29-    /** Returns a human-readable "last seen" string, e.g. "2h ago". */
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-30-    public String getLastSeenText() {
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-31-        if (isOnline) {
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-32-            return "Online";
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-33-        }
--
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-46-        } else {
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-47-            return "Just now";
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-48-        }
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-49-    }
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-50-    
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-51-    /** Green = online + can claim; yellow = online but no claim; gray = offline. */
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-52-    public int getStatusColor() {
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-53-        if (isOnline) {
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java:54:            return canClaimTax ? 0x00FF00 : 0xFFFF00;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-55-        } else {
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-56-            return 0x808080;
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-57-        }
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-58-    }
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-59-
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-60-    public int getRankColor() {
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-61-        switch (rank.toLowerCase()) {
src/main/java/net/machiavelli/minecolonytax\gui\data\OfficerData.java-62-            case "owner":   return 0xFFD700; // gold
--
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-46-        List<VassalIncomeData> vassalIncomes = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-47-        UUID playerId = player.getUUID();
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-48-
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-49-        try {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-50-            List<VassalIncomeData> vassalData = net.machiavelli.minecolonytax.vassalization.VassalManager
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-51-                    .getVassalIncomeForPlayer(playerId);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-52-
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-53-            for (VassalIncomeData data : vassalData) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:54:                boolean canClaim = true;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-55-                try {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-56-                    IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-57-                    IColony vassalColony = colonyManager.getColonyByWorld(data.getVassalColonyId(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-58-                            player.serverLevel());
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-59-                    if (vassalColony != null) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-60-                        WarData war = WarSystem.ACTIVE_WARS.get(data.getVassalColonyId());
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-61-                        boolean isBeingRaided = RaidManager.getActiveRaidForColony(data.getVassalColonyId()) != null;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-62-                        boolean isBesieged = net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(data.getVassalColonyId());
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-63-                        boolean isOccupied = net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(data.getVassalColonyId()) != null;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:64:                        canClaim = (war == null) && !isBeingRaided && !isBesieged && !isOccupied
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-65-                                && !TaxManager.isGenerationDisabled(data.getVassalColonyId());
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-66-                    }
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-67-                } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-68-                    if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-69-                        LOGGER.debug("Error checking claim status for vassal colony {}: {}", data.getVassalColonyName(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-70-                                e.getMessage());
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-71-                    }
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:72:                    canClaim = false;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-73-                }
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-74-
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-75-                vassalIncomes.add(new VassalIncomeData(
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-76-                        data.getVassalColonyId(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-77-                        data.getVassalColonyName(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-78-                        data.getTributeRate(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-79-                        data.getTributeOwed(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-80-                        data.getLastTribute(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-81-                        data.getLastPayment(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:82:                        canClaim,
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-83-                        VassalIncomeData.VassalKind.VASSAL));
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-84-            }
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-85-
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-86-        } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-87-            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-88-                LOGGER.debug("Error collecting vassal income data for player {}: {}", player.getName().getString(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-89-                        e.getMessage());
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-90-            }
--
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-102-                VassalIncomeData.VassalKind kind =
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-103-                        occ.getMode() == net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationMode.TAX_ONLY
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-104-                        ? VassalIncomeData.VassalKind.TAX_OCCUPIED
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-105-                        : VassalIncomeData.VassalKind.PROVISIONAL;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-106-
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-107-                // Use the configured occupation tax percentage as the displayed "rate"
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-108-                // so the row is honest about what's being collected.
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-109-                int tributeRate = (int) Math.round(
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:110:                        net.machiavelli.minecolonytax.TaxConfig.getOccupationTaxPercentage() * 100);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-111-
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-112-                vassalIncomes.add(new VassalIncomeData(
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-113-                        occ.colonyId,
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-114-                        occ.colonyName,
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-115-                        tributeRate,
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-116-                        0,                              // no pending one-shot tribute model here
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-117-                        0,                              // last-tribute not tracked separately for occupations
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:118:                        occ.lastTaxCollectionTime,      // re-use for "last payment" label
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:119:                        true,                           // can collect via /wnt collectoccupation
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-120-                        kind));
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-121-            }
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-122-        } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-123-            if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-124-                LOGGER.debug("Error collecting occupation rows for vassal feed: {}", e.getMessage());
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-125-            }
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-126-        }
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-127-
--
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-182-                        break;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-183-                    }
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-184-                }
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-185-            }
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-186-            isAtWar = (war != null);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-187-            isBeingRaided = RaidManager.getActiveRaidForColony(colonyId) != null;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-188-            boolean isBesieged = net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(colonyId);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-189-            boolean isOccupied = net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(colonyId) != null;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:190:            boolean canClaimTax = !isAtWar && !isBeingRaided && !isBesieged && !isOccupied && !TaxManager.isGenerationDisabled(colonyId);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-191-
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-192-            boolean isVassal = false;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-193-            int vassalTributeRate = 0;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-194-            boolean hasVassals = false;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-195-            int vassalCount = 0;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-196-
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-197-            try {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-198-                isVassal = net.machiavelli.minecolonytax.vassalization.VassalManager.isColonyVassal(colonyId);
--
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-225-            int approximateRevenue = calculateApproximateRevenue(buildingCount, guardTowerCount, happinessMultiplier);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-226-
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-227-            boolean isOwner = colony.getPermissions().getOwner().equals(playerId);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-228-            String taxPolicy = TaxPolicyManager.getPolicy(colonyId).name();
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-229-
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-230-            return new ColonyTaxData(
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-231-                    colonyId, colonyName, taxBalance, maxTaxRevenue,
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-232-                    buildingCount, guardCount, guardTowerCount,
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:233:                    canClaimTax, isAtWar, isBeingRaided,
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-234-                    isVassal, vassalTributeRate, hasVassals, vassalCount,
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-235-                    lastTaxGeneration, debtAmount, approximateRevenue, isOwner,
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-236-                    taxPolicy, colonyHappiness, happinessMultiplier,
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-237-                    isBesieged, isOccupied);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-238-
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-239-        } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-240-            if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java-241-                LOGGER.error("Error collecting data for colony {}: {}", colony.getName(), e.getMessage());
--
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-12-    public enum VassalKind { VASSAL, TAX_OCCUPIED, PROVISIONAL }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-13-
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-14-    private final int vassalColonyId;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-15-    private final String vassalColonyName;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-16-    private final int tributeRate;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-17-    private final int tributeOwed;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-18-    private final int lastTribute;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-19-    private final long lastPayment;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:20:    private final boolean canClaim;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-21-    private final VassalKind kind;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-22-
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-23-    // UI state for claim button
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:24:    private int claimButtonX, claimButtonY, claimButtonWidth, claimButtonHeight;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-25-
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-26-    public VassalIncomeData(int vassalColonyId, String vassalColonyName, int tributeRate,
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:27:                           int tributeOwed, int lastTribute, long lastPayment, boolean canClaim) {
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:28:        this(vassalColonyId, vassalColonyName, tributeRate, tributeOwed, lastTribute, lastPayment, canClaim, VassalKind.VASSAL);
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-29-    }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-30-
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-31-    public VassalIncomeData(int vassalColonyId, String vassalColonyName, int tributeRate,
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:32:                           int tributeOwed, int lastTribute, long lastPayment, boolean canClaim,
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-33-                           VassalKind kind) {
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-34-        this.vassalColonyId = vassalColonyId;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-35-        this.vassalColonyName = vassalColonyName;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-36-        this.tributeRate = tributeRate;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-37-        this.tributeOwed = tributeOwed;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-38-        this.lastTribute = lastTribute;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-39-        this.lastPayment = lastPayment;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:40:        this.canClaim = canClaim;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-41-        this.kind = kind != null ? kind : VassalKind.VASSAL;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-42-    }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-43-
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-44-    public VassalKind getKind() {
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-45-        return kind;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-46-    }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-47-    
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-48-    public int getVassalColonyId() { return vassalColonyId; }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-49-    public String getVassalColonyName() { return vassalColonyName; }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-50-    public int getTributeRate() { return tributeRate; }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-51-    public int getTributeOwed() { return tributeOwed; }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-52-    public int getLastTribute() { return lastTribute; }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-53-    public long getLastPayment() { return lastPayment; }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:54:    public boolean canClaim() { return canClaim; }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-55-    
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-56-    public void setClaimButtonBounds(int x, int y, int width, int height) {
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:57:        this.claimButtonX = x;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:58:        this.claimButtonY = y;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:59:        this.claimButtonWidth = width;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:60:        this.claimButtonHeight = height;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-61-    }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-62-    
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-63-    public boolean isClaimButtonClicked(double mouseX, double mouseY) {
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:64:        return mouseX >= claimButtonX && mouseX < claimButtonX + claimButtonWidth &&
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:65:               mouseY >= claimButtonY && mouseY < claimButtonY + claimButtonHeight;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-66-    }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-67-    
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-68-    public String getFormattedLastPayment() {
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-69-        if (lastPayment == 0) return "Never";
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-70-        long minutes = (System.currentTimeMillis() - lastPayment) / 60000;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-71-        if (minutes < 60) return minutes + "m ago";
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-72-        if (minutes < 1440) return (minutes / 60) + "h ago";
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java-73-        return (minutes / 1440) + "d ago";
--
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-3-public class ColonyTaxData {
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-4-    private final int colonyId;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-5-    private final String colonyName;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-6-    private final int taxBalance;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-7-    private final int maxTaxRevenue;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-8-    private final int buildingCount;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-9-    private final int guardCount;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-10-    private final int guardTowerCount;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:11:    private final boolean canClaimTax;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-12-    private final boolean isAtWar;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-13-    private final boolean isBeingRaided;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-14-    private final boolean isBesieged;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-15-    private final boolean isOccupied;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-16-    private final boolean isVassal;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-17-    private final int vassalTributeRate;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-18-    private final boolean hasVassals;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-19-    private final int vassalCount;
--
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-21-    private final int debtAmount;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-22-    private final int approximateRevenuePerInterval;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-23-    private final boolean isOwner;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-24-    private final String taxPolicy;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-25-    private final double colonyHappiness;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-26-    private final double happinessMultiplier;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-27-
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-28-    // UI state
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:29:    private int claimButtonX, claimButtonY, claimButtonWidth, claimButtonHeight;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-30-    private int permissionButtonX, permissionButtonY, permissionButtonWidth, permissionButtonHeight;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-31-
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-32-    public ColonyTaxData(int colonyId, String colonyName, int taxBalance, int maxTaxRevenue,
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-33-                        int buildingCount, int guardCount, int guardTowerCount,
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:34:                        boolean canClaimTax, boolean isAtWar, boolean isBeingRaided,
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-35-                        boolean isVassal, int vassalTributeRate, boolean hasVassals, int vassalCount,
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-36-                        long lastTaxGeneration, int debtAmount, int approximateRevenuePerInterval, boolean isOwner,
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-37-                        String taxPolicy, double colonyHappiness, double happinessMultiplier,
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-38-                        boolean isBesieged, boolean isOccupied) {
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-39-        this.colonyId = colonyId;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-40-        this.colonyName = colonyName;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-41-        this.taxBalance = taxBalance;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-42-        this.maxTaxRevenue = maxTaxRevenue;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-43-        this.buildingCount = buildingCount;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-44-        this.guardCount = guardCount;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-45-        this.guardTowerCount = guardTowerCount;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:46:        this.canClaimTax = canClaimTax;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-47-        this.isAtWar = isAtWar;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-48-        this.isBeingRaided = isBeingRaided;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-49-        this.isBesieged = isBesieged;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-50-        this.isOccupied = isOccupied;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-51-        this.isVassal = isVassal;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-52-        this.vassalTributeRate = vassalTributeRate;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-53-        this.hasVassals = hasVassals;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-54-        this.vassalCount = vassalCount;
--
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-63-
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-64-    public int getColonyId() { return colonyId; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-65-    public String getColonyName() { return colonyName; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-66-    public int getTaxBalance() { return taxBalance; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-67-    public int getMaxTaxRevenue() { return maxTaxRevenue; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-68-    public int getBuildingCount() { return buildingCount; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-69-    public int getGuardCount() { return guardCount; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-70-    public int getGuardTowerCount() { return guardTowerCount; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:71:    public boolean canClaimTax() { return canClaimTax; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-72-    public boolean isAtWar() { return isAtWar; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-73-    public boolean isBeingRaided() { return isBeingRaided; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-74-    public boolean isBesieged() { return isBesieged; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-75-    public boolean isOccupied() { return isOccupied; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-76-    public boolean isVassal() { return isVassal; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-77-    public int getVassalTributeRate() { return vassalTributeRate; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-78-    public boolean hasVassals() { return hasVassals; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-79-    public int getVassalCount() { return vassalCount; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-80-    public long getLastTaxGeneration() { return lastTaxGeneration; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-81-
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-82-    public void setClaimButtonBounds(int x, int y, int width, int height) {
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:83:        this.claimButtonX = x;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:84:        this.claimButtonY = y;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:85:        this.claimButtonWidth = width;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:86:        this.claimButtonHeight = height;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-87-    }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-88-    
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-89-    public boolean isClaimButtonClicked(double mouseX, double mouseY) {
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:90:        return mouseX >= claimButtonX && mouseX < claimButtonX + claimButtonWidth &&
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:91:               mouseY >= claimButtonY && mouseY < claimButtonY + claimButtonHeight;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-92-    }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-93-    
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-94-    public void setPermissionButtonBounds(int x, int y, int width, int height) {
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-95-        this.permissionButtonX = x;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-96-        this.permissionButtonY = y;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-97-        this.permissionButtonWidth = width;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-98-        this.permissionButtonHeight = height;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java-99-    }
--
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-133-            y += 10;
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-134-            g.drawString(font, "Always has full", x + 4, y, INK_GHOST, false);
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-135-            y += 10;
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-136-            g.drawString(font, "permissions.", x + 4, y, INK_GHOST, false);
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-137-        } else {
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-138-            g.drawString(font, "Permissions:", x, y, INK_FAINT, false);
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-139-            y += 12;
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-140-
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java:141:            boolean canClaim = TaxPermissionManager.canPlayerClaimTax(
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-142-                    colony.getColonyId(), officer.getPlayerId(), false, true);
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-143-
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-144-            g.drawString(font, "Claim Taxes", x + 4, y + 1, INK, false);
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-145-            if (isOwner) {
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java:146:                drawToggle(g, font, canClaim, x + w - 30, y - 1, 28, 11, mouseX, mouseY);
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-147-            } else {
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java:148:                g.drawString(font, canClaim ? "ON" : "OFF", x + w - font.width(canClaim ? "ON" : "OFF") - 4,
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java:149:                        y + 1, canClaim ? GREEN : DANGER, false);
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-150-            }
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-151-            y += 14;
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-152-
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-153-            g.drawString(font, officer.isOnline() ? "Online" : "Last: " + officer.getLastSeenText(),
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-154-                    x + 4, y, officer.isOnline() ? GREEN : INK_GHOST, false);
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-155-        }
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-156-    }
src/main/java/net/machiavelli/minecolonytax\gui\book\OfficersPage.java-157-
--
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-199-        } else if (colony.hasVassals()) {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-200-            drawBadge(g, font, "Overlord (" + colony.getVassalCount() + ")", x, y, GREEN);
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-201-            y += 12;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-202-        }
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-203-
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-204-        int btnY = rightY + rightH - 12;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-205-        int btnW = (w - 4) / 3;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-206-
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java:207:        if (colony.canClaimTax() && colony.getTaxBalance() > 0 && !colony.isVassal()) {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-208-            drawButton(g, font, "Claim", x, btnY, btnW, 11, mouseX, mouseY, GREEN);
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-209-        }
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-210-
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-211-        if (colony.getDebtAmount() > 0) {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-212-            drawButton(g, font, "Pay Debt", x + btnW + 2, btnY, btnW, 11, mouseX, mouseY, DANGER);
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-213-        }
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-214-
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-215-        drawButton(g, font, "Events", x + (btnW + 2) * 2, btnY, btnW, 11, mouseX, mouseY, BLUE);
--
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-348-                            return true;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-349-                        }
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-350-                    }
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-351-                }
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-352-            } else {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-353-                int btnY = rightY + rightH - 12;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-354-                int btnW = (rightW - 4) / 3;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-355-
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java:356:                if (selected.canClaimTax() && selected.getTaxBalance() > 0 && !selected.isVassal()) {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-357-                    if (isInBounds(mouseX, mouseY, rightX, btnY, btnW, 11)) {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-358-                        NetworkHandler.sendToServer(new ClaimTaxPacket(selected.getColonyId(), -1));
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-359-                        refreshCallback.run();
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-360-                        return true;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-361-                    }
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-362-                }
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-363-
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-364-                if (selected.getDebtAmount() > 0) {
--
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-426-        return eventId.startsWith("raid_") || eventId.startsWith("war_");
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-427-    }
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-428-
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-429-    private String getStatusText(ColonyTaxData colony) {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-430-        if (colony.isAtWar()) return "At War";
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-431-        if (colony.isBesieged()) return "Besieged";
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-432-        if (colony.isOccupied()) return "Occupied";
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-433-        if (colony.isBeingRaided()) return "Raided";
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java:434:        if (!colony.canClaimTax()) return "Restricted";
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-435-        if (colony.hasDebt()) return "In Debt";
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-436-        return "Healthy";
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-437-    }
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-438-
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-439-    private int getStatusColor(ColonyTaxData colony) {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-440-        if (colony.isAtWar() || colony.isBesieged() || colony.isBeingRaided()) return DANGER;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-441-        if (colony.isOccupied()) return ORANGE;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java:442:        if (!colony.canClaimTax()) return GOLD_DARK;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-443-        if (colony.hasDebt()) return DANGER;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-444-        return GREEN;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-445-    }
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-446-
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-447-    private int getPolicyColor(TaxPolicy policy) {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-448-        return switch (policy) {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-449-            case LOW -> GREEN;
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java-450-            case HIGH -> ORANGE;
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-64-        public final int colonyId;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-65-        public final String occupierUUID;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-66-        public final String originalOwnerUUID;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-67-        public final int occupierColonyId;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-68-        public final long startTime;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-69-        public final long expirationTime;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-70-        public final String colonyName;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-71-        public boolean reclamationAttempted;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:72:        public long lastTaxCollectionTime;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-73-        /** Null on save files written before the Siege SMP upgrade â€” see {@link #getMode()}. */
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-74-        public OccupationMode mode;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-75-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-76-        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-77-                              int occupierColonyId, String colonyName,
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-78-                              long startTime, long expirationTime) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-79-            this(colonyId, occupierUUID, originalOwnerUUID, occupierColonyId, colonyName,
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-80-                    startTime, expirationTime, OccupationMode.TRANSFER_PENDING);
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-86-            this.colonyId = colonyId;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-87-            this.occupierUUID = occupierUUID.toString();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-88-            this.originalOwnerUUID = originalOwnerUUID.toString();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-89-            this.occupierColonyId = occupierColonyId;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-90-            this.colonyName = colonyName;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-91-            this.startTime = startTime;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-92-            this.expirationTime = expirationTime;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-93-            this.reclamationAttempted = false;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:94:            this.lastTaxCollectionTime = 0;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-95-            this.mode = mode;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-96-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-97-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-98-        /** Returns the mode, defaulting to TRANSFER_PENDING for legacy save files. */
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-99-        public OccupationMode getMode() {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-100-            return mode != null ? mode : OccupationMode.TRANSFER_PENDING;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-101-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-102-
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-227-                        + " days or the occupation simply ends."
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-228-                : "If you do not reclaim, ownership will permanently transfer!";
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-229-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-230-        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-231-        if (server != null) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-232-            // Notify occupier
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-233-            ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-234-            if (occupier != null) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:235:                // AUDIT FIX (defensive_04 C1): the previous "/wnt collectoccupation <id>" hint
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-236-                // pointed at a command that has never been registered. The automatic occupation
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-237-                // tax flow runs via TaxManager.processAutomaticOccupationTax and already routes
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-238-                // revenue to the occupier colony, so the manual path is unnecessary. Just tell
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-239-                // the player that revenue arrives automatically.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-240-                Component occupierMsg = Component.literal(isTaxOnly ? "PRIMARY COLONY TAX-OCCUPIED" : "COLONY OCCUPIED")
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-241-                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-242-                        .append(Component.literal("\n"))
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-243-                        .append(Component.literal("You now occupy " + colony.getName() + "!")
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-329-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-330-        int storedTax = TaxManager.getStoredTaxForColony(colony);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-331-        if (storedTax <= 0) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-332-            occupier.sendSystemMessage(Component.literal("No tax available to collect from " + colony.getName() + ".")
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-333-                    .withStyle(ChatFormatting.YELLOW));
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-334-            return 0;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-335-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-336-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:337:        double occupationTaxRate = TaxConfig.getOccupationTaxPercentage();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-338-        int taxToCollect = (int) (storedTax * occupationTaxRate);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-339-        if (taxToCollect <= 0) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-340-            occupier.sendSystemMessage(Component.literal("Tax amount too small to collect.")
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-341-                    .withStyle(ChatFormatting.YELLOW));
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-342-            return 0;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-343-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-344-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-345-        TaxManager.adjustTax(colony, -taxToCollect);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-346-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-347-        if (data.occupierColonyId > 0) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-348-            IColony occupierColony = findColonyById(data.occupierColonyId);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-349-            if (occupierColony != null) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-350-                TaxManager.incrementTaxRevenue(occupierColony, taxToCollect);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-351-            }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-352-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-353-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:354:        data.lastTaxCollectionTime = System.currentTimeMillis();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-355-        saveData();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-356-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-357-        occupier.sendSystemMessage(Component.literal("Collected " + taxToCollect + " occupation tax from " + colony.getName())
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-358-                .withStyle(ChatFormatting.GOLD));
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-359-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-360-        if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-361-            LOGGER.info("Occupier {} collected {} occupation tax from colony {}",
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-362-                    occupier.getName().getString(), taxToCollect, colony.getName());
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-366-    }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-367-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-368-    public static int processAutomaticOccupationTax(int colonyId, int generatedTax) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-369-        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-370-        if (data == null || generatedTax <= 0) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-371-            return 0;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-372-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-373-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:374:        double occupationTaxRate = TaxConfig.getOccupationTaxPercentage();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-375-        int diverted = (int) (generatedTax * occupationTaxRate);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-376-        if (diverted <= 0) return 0;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-377-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-378-        if (data.occupierColonyId > 0) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-379-            IColony occupierColony = findColonyById(data.occupierColonyId);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-380-            if (occupierColony != null) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-381-                TaxManager.incrementTaxRevenue(occupierColony, diverted);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-382-                if (TaxConfig.isDebugLogging()) {
--
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-390-                            ", Guards: " + net.machiavelli.minecolonytax.WarSystem.countGuards(nearbyColony))
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-391-                    .withStyle(ChatFormatting.WHITE);
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-392-
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-393-            // Send notifications
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-394-            player.sendSystemMessage(titleMessage);
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-395-            player.sendSystemMessage(colonyInfo);
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-396-            player.sendSystemMessage(citizenInfo);
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-397-
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java:398:            if (requirements.canClaim) {
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-399-                player.sendSystemMessage(Component.literal("âœ“ You can claim this colony!")
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-400-                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-401-                player.sendSystemMessage(Component
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-402-                        .literal("Use '/wnt claimcolony " + nearbyColony.getName() + "' to start a claiming raid!")
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-403-                        .withStyle(ChatFormatting.GREEN));
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-404-            } else {
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-405-                player.sendSystemMessage(Component.literal("âœ— You cannot claim this colony: " + requirements.message)
src/main/java/net/machiavelli/minecolonytax\pvp\PvPEventHandler.java-406-                        .withStyle(ChatFormatting.RED));
--
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-63-        for (int i = 0; i < size; i++) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-64-            int colonyId = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-65-            String colonyName = buf.readUtf();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-66-            int taxBalance = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-67-            int maxTaxRevenue = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-68-            int buildingCount = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-69-            int guardCount = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-70-            int guardTowerCount = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:71:            boolean canClaimTax = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-72-            boolean isAtWar = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-73-            boolean isBeingRaided = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-74-            boolean isBesieged = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-75-            boolean isOccupied = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-76-            boolean isVassal = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-77-            int vassalTributeRate = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-78-            boolean hasVassals = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-79-            int vassalCount = buf.readInt();
--
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-83-            boolean isOwner = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-84-            String taxPolicy = buf.readUtf();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-85-            double colonyHappiness = buf.readDouble();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-86-            double happinessMultiplier = buf.readDouble();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-87-
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-88-            this.colonyData.add(new ColonyTaxData(
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-89-                colonyId, colonyName, taxBalance, maxTaxRevenue,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-90-                buildingCount, guardCount, guardTowerCount,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:91:                canClaimTax, isAtWar, isBeingRaided,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-92-                isVassal, vassalTributeRate, hasVassals, vassalCount,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-93-                lastTaxGeneration, debtAmount, approximateRevenuePerInterval, isOwner, taxPolicy,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-94-                colonyHappiness, happinessMultiplier,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-95-                isBesieged, isOccupied
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-96-            ));
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-97-        }
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-98-        
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-99-        // Read vassal data
--
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-102-
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-103-        for (int i = 0; i < vassalSize; i++) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-104-            int vassalColonyId = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-105-            String vassalColonyName = buf.readUtf();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-106-            int tributeRate = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-107-            int tributeOwed = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-108-            int lastTribute = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-109-            long lastPayment = buf.readLong();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:110:            boolean canClaim = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-111-            int kindOrdinal = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-112-            VassalIncomeData.VassalKind kind;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-113-            try {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-114-                kind = VassalIncomeData.VassalKind.values()[kindOrdinal];
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-115-            } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-116-                kind = VassalIncomeData.VassalKind.VASSAL;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-117-            }
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-118-
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-119-            this.vassalData.add(new VassalIncomeData(
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-120-                vassalColonyId, vassalColonyName, tributeRate,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:121:                tributeOwed, lastTribute, lastPayment, canClaim, kind
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-122-            ));
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-123-        }
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-124-
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-125-        // Read event log data
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-126-        this.eventLogData = new HashMap<>();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-127-        int logMapSize = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-128-        for (int i = 0; i < logMapSize; i++) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-129-            int logColonyId = buf.readInt();
--
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-159-        for (ColonyTaxData data : colonyData) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-160-            buf.writeInt(data.getColonyId());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-161-            buf.writeUtf(data.getColonyName());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-162-            buf.writeInt(data.getTaxBalance());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-163-            buf.writeInt(data.getMaxTaxRevenue());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-164-            buf.writeInt(data.getBuildingCount());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-165-            buf.writeInt(data.getGuardCount());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-166-            buf.writeInt(data.getGuardTowerCount());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:167:            buf.writeBoolean(data.canClaimTax());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-168-            buf.writeBoolean(data.isAtWar());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-169-            buf.writeBoolean(data.isBeingRaided());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-170-            buf.writeBoolean(data.isBesieged());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-171-            buf.writeBoolean(data.isOccupied());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-172-            buf.writeBoolean(data.isVassal());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-173-            buf.writeInt(data.getVassalTributeRate());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-174-            buf.writeBoolean(data.hasVassals());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-175-            buf.writeInt(data.getVassalCount());
--
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-187-
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-188-        for (VassalIncomeData data : vassalData) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-189-            buf.writeInt(data.getVassalColonyId());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-190-            buf.writeUtf(data.getVassalColonyName());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-191-            buf.writeInt(data.getTributeRate());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-192-            buf.writeInt(data.getTributeOwed());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-193-            buf.writeInt(data.getLastTribute());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-194-            buf.writeLong(data.getLastPayment());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:195:            buf.writeBoolean(data.canClaim());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-196-            buf.writeInt(data.getKind() != null ? data.getKind().ordinal() : 0);
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-197-        }
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-198-
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-199-        // Write event log data
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-200-        buf.writeInt(eventLogData.size());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-201-        for (Map.Entry<Integer, List<EventLogEntry>> logEntry : eventLogData.entrySet()) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-202-            buf.writeInt(logEntry.getKey());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-203-            List<EventLogEntry> entries = logEntry.getValue();
--
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-24-        this.colonyId = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-25-        int count = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-26-        this.officers = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-27-        
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-28-        for (int i = 0; i < count; i++) {
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-29-            UUID playerId = buf.readUUID();
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-30-            String playerName = buf.readUtf();
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-31-            String rank = buf.readUtf();
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java:32:            boolean canClaimTax = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-33-            boolean isOnline = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-34-            long lastSeen = buf.readLong();
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-35-            
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java:36:            officers.add(new OfficerData(playerId, playerName, rank, canClaimTax, isOnline, lastSeen));
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-37-        }
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-38-    }
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-39-
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-40-    public void toBytes(FriendlyByteBuf buf) {
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-41-        buf.writeInt(colonyId);
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-42-        buf.writeInt(officers.size());
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-43-        
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-44-        for (OfficerData officer : officers) {
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-45-            buf.writeUUID(officer.getPlayerId());
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-46-            buf.writeUtf(officer.getPlayerName());
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-47-            buf.writeUtf(officer.getRank());
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java:48:            buf.writeBoolean(officer.canClaimTax());
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-49-            buf.writeBoolean(officer.isOnline());
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-50-            buf.writeLong(officer.getLastSeen());
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-51-        }
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-52-    }
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-53-
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-54-    public void handle(Supplier<NetworkEvent.Context> ctx) {
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-55-        ctx.get().enqueueWork(() -> {
src/main/java/net/machiavelli/minecolonytax\network\packets\OfficerDataResponsePacket.java-56-            // Update the GUI with officer data â€” wrap to avoid class-loading client-only
--
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-84-                continue;
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-85-            }
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-86-            
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-87-            String playerName = colonyPlayer.getName();
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-88-            if (playerName == null || playerName.isEmpty()) {
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-89-                playerName = "Unknown Player";
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-90-            }
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-91-            
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java:92:            boolean canClaimTax = rank.getId() >= 3; // Officers (rank 3) and above can claim
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-93-            boolean isOnline = false;
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-94-            long lastSeen = System.currentTimeMillis(); // Use current time as default
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-95-            
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-96-            // Safely check if player is online
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-97-            try {
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-98-                MinecraftServer server = colony.getWorld().getServer();
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-99-                if (server != null) {
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-100-                    isOnline = server.getPlayerList().getPlayer(playerId) != null;
--
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-103-                // If we can't determine online status, default to offline
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-104-                isOnline = false;
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-105-            }
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-106-            
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-107-            officers.add(new OfficerData(
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-108-                playerId,
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-109-                playerName,
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-110-                getRankName(rank.getId()),
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java:111:                canClaimTax,
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-112-                isOnline,
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-113-                lastSeen
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-114-            ));
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-115-        }
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-116-        
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-117-        return officers;
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-118-    }
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestOfficerDataPacket.java-119-    
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 690ms:
96-                if (sel) {
97-                    g.fill(leftX, iy, leftX + leftW, iy + 18, 0x182C1E0E);
98-                    g.fill(leftX, iy, leftX + 2, iy + 18, GOLD);
99-                } else if (hov) {
100-                    g.fill(leftX, iy, leftX + leftW, iy + 18, 0x082C1E0E);
101-                }
102-                g.fill(leftX, iy + 17, leftX + leftW, iy + 18, CARD_BORDER);
103-                g.drawString(font, truncate(font, v.getVassalColonyName(), leftW - 6), leftX + 4, iy + 1, INK, false);
104-                // Income label color tracks the relationship kind so tax-occupied
105-                // and provisional-claim rows visually stand apart from true vassals.
106:                int incomeColor = colorForKind(v.getKind());
107-                g.drawString(font, "Income: " + v.getTributeRate() + "%", leftX + 4, iy + 10, incomeColor, false);
108-                // Small kind-tag in the row's right margin (compact: just 1 char + dot)
109:                String tag = shortTagForKind(v.getKind());
110-                if (tag != null) {
111-                    int tagX = leftX + leftW - font.width(tag) - 6;
112-                    g.drawString(font, tag, tagX, iy + 1, incomeColor, false);
113-                }
114-            }
115-            drawScrollbar(g, leftX + leftW - 4, y, leftH - (y - leftY), vassals.size(), maxVisible, scrollOffset);
116-        }
117-    }
118-
119-    @Override
--
121-        List<VassalIncomeData> vassals = vassalSupplier.get();
122-        List<ColonyTaxData> overlords = coloniesSupplier.get().stream()
123-                .filter(ColonyTaxData::isVassal).collect(Collectors.toList());
124-
125-        if (selectedOverlordIndex >= 0 && selectedOverlordIndex < overlords.size()) {
126-            renderOverlordDetail(g, overlords.get(selectedOverlordIndex), mouseX, mouseY);
127-            return;
128-        }
129-
130-        if (selectedVassalIndex >= 0 && selectedVassalIndex < vassals.size()) {
131:            renderVassalDetail(g, vassals.get(selectedVassalIndex), mouseX, mouseY);
132-            return;
133-        }
134-
135-        g.drawString(font, "Select an entry", rightX + 10, rightY + rightH / 2 - 4, INK_GHOST, false);
136-    }
137-
138-    /** Display label for the Vassals tab row badge. */
139:    private static String labelForKind(VassalIncomeData.VassalKind kind) {
140-        if (kind == null) return "VASSAL";
141-        switch (kind) {
142-            case TAX_OCCUPIED: return "TAX-OCCUPIED";
143-            case PROVISIONAL:  return "PROVISIONAL";
144-            case VASSAL:
145-            default:           return "VASSAL";
146-        }
147-    }
148-
149-    /** Color for the Vassals tab row badge. Matches the design HTML's palette. */
150:    private static int colorForKind(VassalIncomeData.VassalKind kind) {
151-        if (kind == null) return GREEN;
152-        switch (kind) {
153-            case TAX_OCCUPIED: return DANGER;          // red â€” primary under besiege
154-            case PROVISIONAL:  return 0xFFE07B33;      // orange â€” secondary mid-conversion
155-            case VASSAL:
156-            default:           return GREEN;
157-        }
158-    }
159-
160-    /** One-letter row tag for the compact list view. */
161:    private static String shortTagForKind(VassalIncomeData.VassalKind kind) {
162-        if (kind == null) return null;
163-        switch (kind) {
164-            case TAX_OCCUPIED: return "T";
165-            case PROVISIONAL:  return "P";
166-            case VASSAL:       return null; // keep the legacy rows clean
167-            default:           return null;
168-        }
169-    }
170-
171-    private void renderOverlordDetail(GuiGraphics g, ColonyTaxData ol, int mouseX, int mouseY) {
172-        int x = rightX, y = rightY, w = rightW;
173-
174-        g.drawString(font, truncate(font, ol.getColonyName(), w - 50), x, y, INK, false);
175:        drawBadge(g, font, "OVERLORD", x + w - font.width("OVERLORD") - 4, y, DANGER);
176-        y += 14;
177-
178-        drawCard(g, x, y, w, 18, true);
179-        g.drawString(font, "TRIBUTE RATE", x + 3, y + 2, INK_FAINT, false);
180-        g.drawCenteredString(font, ol.getVassalTributeRate() + "%", x + w / 2, y + 10, INK);
181-        y += 20;
182-
183-        int btnY = rightY + rightH - 12;
184-        drawButton(g, font, "Break Vassalage", x, btnY, w, 11, mouseX, mouseY, DANGER);
185-    }
186-
187:    private void renderVassalDetail(GuiGraphics g, VassalIncomeData v, int mouseX, int mouseY) {
188-        int x = rightX, y = rightY, w = rightW;
189-
190-        g.drawString(font, truncate(font, v.getVassalColonyName(), w - 50), x, y, INK, false);
191-        // Kind-aware badge: VASSAL (green) / TAX-OCCUPIED (red) / PROVISIONAL (orange).
192:        String badgeLabel = labelForKind(v.getKind());
193:        int badgeColor = colorForKind(v.getKind());
194:        drawBadge(g, font, badgeLabel, x + w - font.width(badgeLabel) - 4, y, badgeColor);
195-        y += 14;
196-
197-        drawCard(g, x, y, w, 18, true);
198-        g.drawString(font, "TRIBUTE RATE", x + 3, y + 2, INK_FAINT, false);
199-        g.drawCenteredString(font, v.getTributeRate() + "%", x + w / 2, y + 10, INK);
200-        y += 20;
201-
202-        int halfW = (w - 2) / 2;
203-        drawCard(g, x, y, halfW, 18, false);
204-        g.drawString(font, "LAST PAYMENT", x + 3, y + 2, INK_FAINT, false);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 6 "Break Vassalage|Claim|ClaimTaxPacket|vassalSupplier|getKind|selectedVassalIndex|mouseClicked" src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 6 "NetworkRegistry|PROTOCOL|VERSION|simpleChannel|SimpleChannel|ColonyDataResponsePacket" src/main/java/net/machiavelli/minecolonytax/network src/main/java/net/machiavelli/minecolonytax' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 10 \"collectoccupation|lastTaxCollectionTime|set.*lastTax|OccupationData\\(\" src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 619ms:
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-62-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-63-    public static class OccupationData {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-64-        public final int colonyId;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-65-        public final String occupierUUID;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-66-        public final String originalOwnerUUID;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-67-        public final int occupierColonyId;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-68-        public final long startTime;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-69-        public final long expirationTime;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-70-        public final String colonyName;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-71-        public boolean reclamationAttempted;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:72:        public long lastTaxCollectionTime;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-73-        /** Null on save files written before the Siege SMP upgrade â€” see {@link #getMode()}. */
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-74-        public OccupationMode mode;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-75-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:76:        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-77-                              int occupierColonyId, String colonyName,
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-78-                              long startTime, long expirationTime) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-79-            this(colonyId, occupierUUID, originalOwnerUUID, occupierColonyId, colonyName,
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-80-                    startTime, expirationTime, OccupationMode.TRANSFER_PENDING);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-81-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-82-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:83:        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-84-                              int occupierColonyId, String colonyName,
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-85-                              long startTime, long expirationTime, OccupationMode mode) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-86-            this.colonyId = colonyId;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-87-            this.occupierUUID = occupierUUID.toString();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-88-            this.originalOwnerUUID = originalOwnerUUID.toString();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-89-            this.occupierColonyId = occupierColonyId;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-90-            this.colonyName = colonyName;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-91-            this.startTime = startTime;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-92-            this.expirationTime = expirationTime;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-93-            this.reclamationAttempted = false;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:94:            this.lastTaxCollectionTime = 0;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-95-            this.mode = mode;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-96-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-97-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-98-        /** Returns the mode, defaulting to TRANSFER_PENDING for legacy save files. */
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-99-        public OccupationMode getMode() {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-100-            return mode != null ? mode : OccupationMode.TRANSFER_PENDING;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-101-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-102-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-103-        public boolean isExpired() {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-104-            return System.currentTimeMillis() >= expirationTime;
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-198-            mode = OccupationMode.TAX_ONLY;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-199-            durationDays = TaxConfig.getPrimaryColonyTaxOccupationDays();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-200-        } else {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-201-            mode = OccupationMode.TRANSFER_PENDING;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-202-            durationDays = TaxConfig.getOccupationDurationDays();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-203-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-204-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-205-        long now = System.currentTimeMillis();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-206-        long expirationTime = now + (durationDays * 24L * 60L * 60L * 1000L);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-207-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:208:        OccupationData data = new OccupationData(
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-209-                colonyId, occupierUUID, originalOwner,
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-210-                occupierColonyId, colony.getName(),
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-211-                now, expirationTime, mode
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-212-        );
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-213-        ACTIVE_OCCUPATIONS.put(colonyId, data);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-214-        saveData();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-215-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-216-        if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-217-            LOGGER.info("Colony {} is now OCCUPIED by {} for {} days [mode={}]",
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-218-                    colony.getName(), occupierUUID, durationDays, mode);
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-225-        final String ownerStakes = isTaxOnly
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-226-                ? "This is your Primary colony â€” the deed is safe. Reclaim within " + durationDays
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-227-                        + " days or the occupation simply ends."
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-228-                : "If you do not reclaim, ownership will permanently transfer!";
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-229-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-230-        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-231-        if (server != null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-232-            // Notify occupier
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-233-            ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-234-            if (occupier != null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:235:                // AUDIT FIX (defensive_04 C1): the previous "/wnt collectoccupation <id>" hint
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-236-                // pointed at a command that has never been registered. The automatic occupation
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-237-                // tax flow runs via TaxManager.processAutomaticOccupationTax and already routes
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-238-                // revenue to the occupier colony, so the manual path is unnecessary. Just tell
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-239-                // the player that revenue arrives automatically.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-240-                Component occupierMsg = Component.literal(isTaxOnly ? "PRIMARY COLONY TAX-OCCUPIED" : "COLONY OCCUPIED")
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-241-                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-242-                        .append(Component.literal("\n"))
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-243-                        .append(Component.literal("You now occupy " + colony.getName() + "!")
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-244-                                .withStyle(ChatFormatting.YELLOW))
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-245-                        .append(Component.literal("\n"))
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-344-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-345-        TaxManager.adjustTax(colony, -taxToCollect);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-346-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-347-        if (data.occupierColonyId > 0) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-348-            IColony occupierColony = findColonyById(data.occupierColonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-349-            if (occupierColony != null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-350-                TaxManager.incrementTaxRevenue(occupierColony, taxToCollect);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-351-            }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-352-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-353-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:354:        data.lastTaxCollectionTime = System.currentTimeMillis();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-355-        saveData();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-356-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-357-        occupier.sendSystemMessage(Component.literal("Collected " + taxToCollect + " occupation tax from " + colony.getName())
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-358-                .withStyle(ChatFormatting.GOLD));
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-359-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-360-        if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-361-            LOGGER.info("Occupier {} collected {} occupation tax from colony {}",
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-362-                    occupier.getName().getString(), taxToCollect, colony.getName());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-363-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-364-
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 641ms:
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-6-import net.machiavelli.minecolonytax.gui.data.ColonySummary;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-7-import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-8-import net.machiavelli.minecolonytax.gui.data.OfficerData;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-9-import net.machiavelli.minecolonytax.gui.data.SpyMissionData;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-10-import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-11-import net.machiavelli.minecolonytax.network.NetworkHandler;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java:12:import net.machiavelli.minecolonytax.network.packets.ClaimTaxPacket;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-13-import net.machiavelli.minecolonytax.network.packets.RequestColonyDataPacket;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-14-import net.minecraft.client.gui.Font;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-15-import net.minecraft.client.gui.GuiGraphics;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-16-import net.minecraft.client.gui.components.Button;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-17-import net.minecraft.client.gui.components.EditBox;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-18-import net.minecraft.client.gui.screens.Screen;
--
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-209-        int barY = guiTop + BOTTOM_Y;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-210-
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-211-        refreshButton = Button.builder(Component.literal("Refresh"), btn -> requestColonyData())
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-212-                .bounds(barX, barY, 40, 11).build();
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-213-        this.addRenderableWidget(refreshButton);
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-214-
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java:215:        claimAllButton = Button.builder(Component.literal("Claim All"), btn -> claimAllTaxes())
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-216-                .bounds(guiLeft + RIGHT_X + RIGHT_W - 44, barY, 44, 11).build();
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-217-        this.addRenderableWidget(claimAllButton);
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-218-
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-219-        // Activate default tab and hide all other page widgets
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-220-        for (Map.Entry<BookTab, BookPage> entry : pages.entrySet()) {
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-221-            entry.getValue().setWidgetsVisible(entry.getKey() == activeTab);
--
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-239-        NetworkHandler.sendToServer(new RequestColonyDataPacket());
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-240-        officerData.clear();
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-241-    }
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-242-
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-243-    private void claimAllTaxes() {
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-244-        for (ColonyTaxData colony : colonies) {
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java:245:            if (colony.getTaxBalance() > 0 && colony.canClaimTax()) {
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java:246:                NetworkHandler.CHANNEL.sendToServer(new ClaimTaxPacket(colony.getColonyId(), -1));
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-247-            }
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-248-        }
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-249-        requestColonyData();
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-250-    }
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-251-
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-252-    // ========== Tab switching ==========
--
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-358-        }
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-359-    }
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-360-
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-361-    // ========== Input ==========
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-362-
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-363-    @Override
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java:364:    public boolean mouseClicked(double mouseX, double mouseY, int button) {
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-365-        if (button == 0) {
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-366-            // Check tab clicks first
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-367-            for (BookTab tab : BookTab.values()) {
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-368-                if (tab.isEnabled() && tab.isMouseOver(mouseX, mouseY, guiLeft, guiTop, GUI_WIDTH)) {
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-369-                    switchTab(tab);
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-370-                    return true;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-371-                }
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-372-            }
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-373-
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-374-            // Delegate to active page
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-375-            BookPage activePage = pages.get(activeTab);
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java:376:            if (activePage != null && activePage.mouseClicked(mouseX, mouseY, button)) {
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-377-                return true;
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-378-            }
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-379-        }
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-380-
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java:381:        return super.mouseClicked(mouseX, mouseY, button);
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-382-    }
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-383-
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-384-    @Override
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-385-    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-386-        BookPage activePage = pages.get(activeTab);
src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java-387-        if (activePage != null && activePage.mouseScrolled(mouseX, mouseY, delta)) {
--
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-16-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-17-/**
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-18- * Vassals tab: left page shows overlords + vassals, right page shows selected detail.
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-19- */
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-20-public class VassalsPage extends BookPage {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-21-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:22:    private final Supplier<List<VassalIncomeData>> vassalSupplier;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-23-    private final Supplier<List<ColonyTaxData>> coloniesSupplier;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-24-    private final Runnable refreshCallback;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-25-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-26-    private int scrollOffset = 0;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:27:    private int selectedVassalIndex = -1;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-28-    private int selectedOverlordIndex = -1;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-29-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-30-    public VassalsPage(Screen screen, Font font,
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:31:                       Supplier<List<VassalIncomeData>> vassalSupplier,
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-32-                       Supplier<List<ColonyTaxData>> coloniesSupplier,
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-33-                       Runnable refreshCallback) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-34-        super(screen, font);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:35:        this.vassalSupplier = vassalSupplier;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-36-        this.coloniesSupplier = coloniesSupplier;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-37-        this.refreshCallback = refreshCallback;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-38-    }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-39-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-40-    @Override
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-41-    public void onActivated() {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-42-        scrollOffset = 0;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:43:        selectedVassalIndex = -1;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-44-        selectedOverlordIndex = -1;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-45-    }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-46-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-47-    @Override
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-48-    public void renderLeftPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:49:        List<VassalIncomeData> vassals = vassalSupplier.get();
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-50-        List<ColonyTaxData> overlords = coloniesSupplier.get().stream()
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-51-                .filter(ColonyTaxData::isVassal).collect(Collectors.toList());
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-52-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-53-        drawHeading(g, font, "Diplomacy", leftX, leftY, leftW);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-54-        int y = leftY + 14;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-55-
--
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-88-        } else {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-89-            int maxVisible = Math.max(1, (leftH - (y - leftY)) / 18);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-90-            int visible = Math.min(maxVisible, vassals.size() - scrollOffset);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-91-            for (int i = 0; i < visible; i++) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-92-                VassalIncomeData v = vassals.get(i + scrollOffset);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-93-                int iy = y + i * 18;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:94:                boolean sel = selectedVassalIndex == i + scrollOffset;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-95-                boolean hov = mouseX >= leftX && mouseX < leftX + leftW && mouseY >= iy && mouseY < iy + 18;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-96-                if (sel) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-97-                    g.fill(leftX, iy, leftX + leftW, iy + 18, 0x182C1E0E);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-98-                    g.fill(leftX, iy, leftX + 2, iy + 18, GOLD);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-99-                } else if (hov) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-100-                    g.fill(leftX, iy, leftX + leftW, iy + 18, 0x082C1E0E);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-101-                }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-102-                g.fill(leftX, iy + 17, leftX + leftW, iy + 18, CARD_BORDER);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-103-                g.drawString(font, truncate(font, v.getVassalColonyName(), leftW - 6), leftX + 4, iy + 1, INK, false);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-104-                // Income label color tracks the relationship kind so tax-occupied
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-105-                // and provisional-claim rows visually stand apart from true vassals.
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:106:                int incomeColor = colorForKind(v.getKind());
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-107-                g.drawString(font, "Income: " + v.getTributeRate() + "%", leftX + 4, iy + 10, incomeColor, false);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-108-                // Small kind-tag in the row's right margin (compact: just 1 char + dot)
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:109:                String tag = shortTagForKind(v.getKind());
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-110-                if (tag != null) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-111-                    int tagX = leftX + leftW - font.width(tag) - 6;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-112-                    g.drawString(font, tag, tagX, iy + 1, incomeColor, false);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-113-                }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-114-            }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-115-            drawScrollbar(g, leftX + leftW - 4, y, leftH - (y - leftY), vassals.size(), maxVisible, scrollOffset);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-116-        }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-117-    }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-118-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-119-    @Override
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-120-    public void renderRightPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:121:        List<VassalIncomeData> vassals = vassalSupplier.get();
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-122-        List<ColonyTaxData> overlords = coloniesSupplier.get().stream()
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-123-                .filter(ColonyTaxData::isVassal).collect(Collectors.toList());
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-124-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-125-        if (selectedOverlordIndex >= 0 && selectedOverlordIndex < overlords.size()) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-126-            renderOverlordDetail(g, overlords.get(selectedOverlordIndex), mouseX, mouseY);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-127-            return;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-128-        }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-129-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:130:        if (selectedVassalIndex >= 0 && selectedVassalIndex < vassals.size()) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:131:            renderVassalDetail(g, vassals.get(selectedVassalIndex), mouseX, mouseY);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-132-            return;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-133-        }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-134-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-135-        g.drawString(font, "Select an entry", rightX + 10, rightY + rightH / 2 - 4, INK_GHOST, false);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-136-    }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-137-
--
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-178-        drawCard(g, x, y, w, 18, true);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-179-        g.drawString(font, "TRIBUTE RATE", x + 3, y + 2, INK_FAINT, false);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-180-        g.drawCenteredString(font, ol.getVassalTributeRate() + "%", x + w / 2, y + 10, INK);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-181-        y += 20;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-182-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-183-        int btnY = rightY + rightH - 12;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:184:        drawButton(g, font, "Break Vassalage", x, btnY, w, 11, mouseX, mouseY, DANGER);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-185-    }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-186-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-187-    private void renderVassalDetail(GuiGraphics g, VassalIncomeData v, int mouseX, int mouseY) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-188-        int x = rightX, y = rightY, w = rightW;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-189-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-190-        g.drawString(font, truncate(font, v.getVassalColonyName(), w - 50), x, y, INK, false);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-191-        // Kind-aware badge: VASSAL (green) / TAX-OCCUPIED (red) / PROVISIONAL (orange).
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:192:        String badgeLabel = labelForKind(v.getKind());
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:193:        int badgeColor = colorForKind(v.getKind());
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-194-        drawBadge(g, font, badgeLabel, x + w - font.width(badgeLabel) - 4, y, badgeColor);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-195-        y += 14;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-196-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-197-        drawCard(g, x, y, w, 18, true);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-198-        g.drawString(font, "TRIBUTE RATE", x + 3, y + 2, INK_FAINT, false);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-199-        g.drawCenteredString(font, v.getTributeRate() + "%", x + w / 2, y + 10, INK);
--
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-210-        y += 20;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-211-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-212-        g.drawString(font, "Auto-collected at tax intervals", x, y, INK_GHOST, false);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-213-    }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-214-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-215-    @Override
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:216:    public boolean mouseClicked(double mouseX, double mouseY, int button) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-217-        if (button != 0) return false;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:218:        List<VassalIncomeData> vassals = vassalSupplier.get();
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-219-        List<ColonyTaxData> overlords = coloniesSupplier.get().stream()
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-220-                .filter(ColonyTaxData::isVassal).collect(Collectors.toList());
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-221-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-222-        if (isInLeftPage(mouseX, mouseY)) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-223-            int y = leftY + 14;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-224-            y += 12; // section header
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-225-            if (!overlords.isEmpty()) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-226-                for (int i = 0; i < overlords.size() && i < 3; i++) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-227-                    if (mouseY >= y && mouseY < y + 18) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-228-                        selectedOverlordIndex = (selectedOverlordIndex == i) ? -1 : i;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:229:                        selectedVassalIndex = -1;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-230-                        return true;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-231-                    }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-232-                    y += 18;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-233-                }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-234-            } else {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-235-                y += 12;
--
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-239-                int maxVisible = Math.max(1, (leftH - (y - leftY)) / 18);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-240-                int visible = Math.min(maxVisible, vassals.size() - scrollOffset);
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-241-                for (int i = 0; i < visible; i++) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-242-                    int iy = y + i * 18;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-243-                    if (mouseY >= iy && mouseY < iy + 18) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-244-                        int idx = i + scrollOffset;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:245:                        selectedVassalIndex = (selectedVassalIndex == idx) ? -1 : idx;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-246-                        selectedOverlordIndex = -1;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-247-                        return true;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-248-                    }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-249-                }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-250-            }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-251-        }
--
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-265-        return false;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-266-    }
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-267-
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-268-    @Override
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-269-    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-270-        if (isInLeftPage(mouseX, mouseY)) {
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java:271:            List<VassalIncomeData> vassals = vassalSupplier.get();
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-272-            // Compute vassal list start Y to determine available space dynamically
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-273-            List<ColonyTaxData> overlords = coloniesSupplier.get().stream()
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-274-                    .filter(ColonyTaxData::isVassal).collect(Collectors.toList());
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-275-            int usedY = 14 + 12; // heading + overlords section header
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-276-            usedY += overlords.isEmpty() ? 12 : Math.min(overlords.size(), 3) * 18;
src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java-277-            usedY += 14; // gap + vassals section header
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 646ms:
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-340-        
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-341-        return center; // Fallback to colony center
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-342-    }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-343-    
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-344-    /**
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-345-     * Create a boss bar to track the claiming raid progress with timer and defender count.
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:346:     * ENHANCED VERSION: More robust boss bar creation with better error handling.
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-347-     */
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-348-    private static void createRaidBossBar(ClaimingRaidData raidData, ServerPlayer claimingPlayer) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-349-        if (raidData == null) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-350-            LOGGER.error("Cannot create boss bar - raid data is null");
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-351-            return;
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-352-        }
--
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-949-        } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-950-            return null;
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-951-        }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-952-    }
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-953-    
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-954-    /**
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:955:     * Get a colony by ID - FIXED VERSION that actually works.
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-956-     */
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-957-    private static IColony getColonyById(int colonyId) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-958-        try {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-959-            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-960-            
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java-961-            // CRITICAL FIX: Search through all worlds instead of using null
--
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-1-package net.machiavelli.minecolonytax.network;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-2-
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-3-import net.machiavelli.minecolonytax.MineColonyTax;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-4-import net.machiavelli.minecolonytax.network.packets.ClaimTaxPacket;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java:5:import net.machiavelli.minecolonytax.network.packets.ColonyDataResponsePacket;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-6-import net.machiavelli.minecolonytax.network.packets.PayTaxDebtPacket;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-7-import net.machiavelli.minecolonytax.network.packets.EndVassalizationPacket;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-8-import net.machiavelli.minecolonytax.network.packets.UpdateTaxPermissionPacket;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-9-import net.machiavelli.minecolonytax.network.packets.UpdatePlayerTaxPermissionPacket;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-10-import net.machiavelli.minecolonytax.network.packets.RequestOfficerDataPacket;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-11-import net.machiavelli.minecolonytax.network.packets.OfficerDataResponsePacket;
--
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-24-import net.machiavelli.minecolonytax.network.packets.RequestInvestmentDataPacket;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-25-import net.machiavelli.minecolonytax.network.packets.InvestmentDataResponsePacket;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-26-import net.machiavelli.minecolonytax.network.packets.BuyInvestmentPacket;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-27-import net.minecraft.resources.ResourceLocation;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-28-import net.minecraft.server.level.ServerPlayer;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-29-import net.minecraftforge.network.NetworkDirection;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java:30:import net.minecraftforge.network.NetworkRegistry;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-31-import net.minecraftforge.network.PacketDistributor;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java:32:import net.minecraftforge.network.simple.SimpleChannel;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-33-
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-34-public class NetworkHandler {
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java:35:        public static final String PROTOCOL_VERSION = "3";
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java:36:        public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-37-                        new ResourceLocation(MineColonyTax.MOD_ID, "main"),
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java:38:                        () -> PROTOCOL_VERSION,
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java:39:                        PROTOCOL_VERSION::equals,
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java:40:                        PROTOCOL_VERSION::equals);
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-41-
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-42-        private static int packetId = 0;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-43-
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-44-        private static int nextId() {
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-45-                return packetId++;
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-46-        }
--
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-58-                CHANNEL.messageBuilder(RequestColonyDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-59-                                .decoder(RequestColonyDataPacket::new)
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-60-                                .encoder(RequestColonyDataPacket::toBytes)
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-61-                                .consumerMainThread(RequestColonyDataPacket::handle)
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-62-                                .add();
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-63-
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java:64:                CHANNEL.messageBuilder(ColonyDataResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java:65:                                .decoder(ColonyDataResponsePacket::new)
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java:66:                                .encoder(ColonyDataResponsePacket::toBytes)
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java:67:                                .consumerMainThread(ColonyDataResponsePacket::handle)
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-68-                                .add();
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-69-
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-70-                CHANNEL.messageBuilder(ClaimTaxPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-71-                                .decoder(ClaimTaxPacket::new)
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-72-                                .encoder(ClaimTaxPacket::toBytes)
src/main/java/net/machiavelli/minecolonytax/network\NetworkHandler.java-73-                                .consumerMainThread(ClaimTaxPacket::handle)
--
src/main/java/net/machiavelli/minecolonytax/network\packets\RequestColonyDataPacket.java-219-                        : com.minecolonies.api.IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()) {
src/main/java/net/machiavelli/minecolonytax/network\packets\RequestColonyDataPacket.java-220-                    allColonySummaries.add(new ColonySummary(c.getID(), c.getName()));
src/main/java/net/machiavelli/minecolonytax/network\packets\RequestColonyDataPacket.java-221-                }
src/main/java/net/machiavelli/minecolonytax/network\packets\RequestColonyDataPacket.java-222-
src/main/java/net/machiavelli/minecolonytax/network\packets\RequestColonyDataPacket.java-223-                // Send response back to client
src/main/java/net/machiavelli/minecolonytax/network\packets\RequestColonyDataPacket.java-224-                NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
src/main/java/net/machiavelli/minecolonytax/network\packets\RequestColonyDataPacket.java:225:                    new ColonyDataResponsePacket(colonyData, vassalData, eventLogData, allColonySummaries));
src/main/java/net/machiavelli/minecolonytax/network\packets\RequestColonyDataPacket.java-226-            }
src/main/java/net/machiavelli/minecolonytax/network\packets\RequestColonyDataPacket.java-227-        });
src/main/java/net/machiavelli/minecolonytax/network\packets\RequestColonyDataPacket.java-228-        return true;
src/main/java/net/machiavelli/minecolonytax/network\packets\RequestColonyDataPacket.java-229-    }
src/main/java/net/machiavelli/minecolonytax/network\packets\RequestColonyDataPacket.java-230-}
--
src/main/java/net/machiavelli/minecolonytax/network\packets\DismissEventPacket.java-63-            Map<Integer, List<EventLogEntry>> eventLogData = new HashMap<>();
src/main/java/net/machiavelli/minecolonytax/network\packets\DismissEventPacket.java-64-            for (ColonyTaxData data : colonyData) {
src/main/java/net/machiavelli/minecolonytax/network\packets\DismissEventPacket.java-65-                eventLogData.put(data.getColonyId(), RandomEventManager.getEventLog(data.getColonyId()));
src/main/java/net/machiavelli/minecolonytax/network\packets\DismissEventPacket.java-66-            }
src/main/java/net/machiavelli/minecolonytax/network\packets\DismissEventPacket.java-67-            NetworkHandler.CHANNEL.send(
src/main/java/net/machiavelli/minecolonytax/network\packets\DismissEventPacket.java-68-                    PacketDistributor.PLAYER.with(() -> player),
src/main/java/net/machiavelli/minecolonytax/network\packets\DismissEventPacket.java:69:                    new ColonyDataResponsePacket(colonyData, vassalData, eventLogData));
src/main/java/net/machiavelli/minecolonytax/network\packets\DismissEventPacket.java-70-        });
src/main/java/net/machiavelli/minecolonytax/network\packets\DismissEventPacket.java-71-        return true;
src/main/java/net/machiavelli/minecolonytax/network\packets\DismissEventPacket.java-72-    }
src/main/java/net/machiavelli/minecolonytax/network\packets\DismissEventPacket.java-73-}
--
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-15-import java.util.Map;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-16-import java.util.function.Supplier;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-17-
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-18-/**
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-19- * Packet sent from server to client containing colony tax data for the GUI
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-20- */
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java:21:public class ColonyDataResponsePacket {
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-22-    private final List<ColonyTaxData> colonyData;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-23-    private final List<VassalIncomeData> vassalData;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-24-    private final Map<Integer, List<EventLogEntry>> eventLogData;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-25-    private final List<ColonySummary> allColonySummaries;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-26-
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java:27:    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData) {
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-28-        this.colonyData = colonyData;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-29-        this.vassalData = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-30-        this.eventLogData = new HashMap<>();
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-31-        this.allColonySummaries = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-32-    }
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-33-
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java:34:    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData) {
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-35-        this.colonyData = colonyData;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-36-        this.vassalData = vassalData;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-37-        this.eventLogData = new HashMap<>();
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-38-        this.allColonySummaries = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-39-    }
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-40-
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java:41:    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData,
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-42-                                    Map<Integer, List<EventLogEntry>> eventLogData) {
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-43-        this.colonyData = colonyData;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-44-        this.vassalData = vassalData;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-45-        this.eventLogData = eventLogData;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-46-        this.allColonySummaries = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-47-    }
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-48-
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java:49:    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData,
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-50-                                    Map<Integer, List<EventLogEntry>> eventLogData,
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-51-                                    List<ColonySummary> allColonySummaries) {
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-52-        this.colonyData = colonyData;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-53-        this.vassalData = vassalData;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-54-        this.eventLogData = eventLogData;
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-55-        this.allColonySummaries = allColonySummaries != null ? allColonySummaries : new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-56-    }
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-57-    
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java:58:    public ColonyDataResponsePacket(FriendlyByteBuf buf) {
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-59-        // Read colony data
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-60-        int size = buf.readInt();
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-61-        this.colonyData = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-62-        
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-63-        for (int i = 0; i < size; i++) {
src/main/java/net/machiavelli/minecolonytax/network\packets\ColonyDataResponsePacket.java-64-            int colonyId = buf.readInt();
--
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java-59-        deactivateMilitia(colony);
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java-60-
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java-61-        Set<Integer> militiaMembers = new HashSet<>();
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java-62-        List<ICitizenData> eligibleCitizens = getEligibleCitizens(colony);
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java-63-        
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java-64-        // Calculate how many to convert
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java:65:        int targetMilitiaCount = (int) Math.ceil(eligibleCitizens.size() * TaxConfig.MILITIA_CONVERSION_PERCENTAGE.get());
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java-66-        double upgradeMultiplier = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager.getMilitiaMultiplier(colonyId);
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java-67-        targetMilitiaCount = (int) Math.ceil(targetMilitiaCount * upgradeMultiplier);
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java-68-
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java-69-        Collections.shuffle(eligibleCitizens);
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java-70-        int converted = 0;
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java-71-
--
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-12-public class TaxConfig {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-13-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-14-        private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-15-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-16-        public static ForgeConfigSpec CONFIG;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-17-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:18:        public static final ForgeConfigSpec.BooleanValue ENABLE_SDM_SHOP_CONVERSION;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-19-        public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_ITEM_NAME;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-20-        public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_DENOMINATIONS;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-21-        public static final ForgeConfigSpec.IntValue DEBT_LIMIT;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-22-        public static final ForgeConfigSpec.IntValue TAX_STEAL_PER_GUARD;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-23-        public static final ForgeConfigSpec.IntValue DEBT_EVENT_CYCLES;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-24-        public static final ForgeConfigSpec.IntValue DEBT_ABANDONMENT_CYCLES;
--
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-173-        public static final ForgeConfigSpec.DoubleValue DEFAULT_EXTORTION_PERCENTAGE;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-174-        public static final ForgeConfigSpec.IntValue EXTORTION_RESPONSE_TIME_MINUTES;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-175-        public static final ForgeConfigSpec.IntValue EXTORTION_IMMUNITY_HOURS;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-176-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-177-        // Citizen Militia System Configuration
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-178-        public static final ForgeConfigSpec.BooleanValue ENABLE_CITIZEN_MILITIA;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:179:        public static final ForgeConfigSpec.DoubleValue MILITIA_CONVERSION_PERCENTAGE;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-180-        public static final ForgeConfigSpec.IntValue MILITIA_MIN_CITIZEN_LEVEL;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-181-        public static final ForgeConfigSpec.BooleanValue MILITIA_GUARDS_SEEK_RAIDERS;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-182-        public static final ForgeConfigSpec.BooleanValue TAX_STEAL_PER_GUARD_KILLED;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-183-        public static final ForgeConfigSpec.DoubleValue TAX_STEAL_PERCENTAGE_PER_GUARD;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-184-        public static final ForgeConfigSpec.DoubleValue MAX_RAID_TAX_PERCENTAGE;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-185-        public static final ForgeConfigSpec.BooleanValue APPLY_RESISTANCE_TO_CITIZENS;
--
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-456-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-457-                MAX_TAX_REVENUE = BUILDER.comment(
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-458-                                "Maximum tax revenue a colony can store before it stops generating further taxes. "
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-459-                                                + "Keep tight to encourage micromanagement and daily engagement.")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-460-                                .defineInRange("MaxTaxRevenue", 10000, 1, Integer.MAX_VALUE);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-461-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:462:                ENABLE_SDM_SHOP_CONVERSION = BUILDER
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-463-                                .comment("Enable SDMShop conversion (true = enable, false = disable).")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-464-                                .define("EnableSDMShopConversion", true);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-465-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-466-                CURRENCY_ITEM_NAME = BUILDER
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-467-                                .comment("The item name for the custom currency (e.g., 'minecraft:emerald').")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-468-                                .define("CurrencyItemName", "minecraft:emerald");
--
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-904-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-905-                // Citizen Militia System Configuration
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-906-                ENABLE_CITIZEN_MILITIA = BUILDER.comment(
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-907-                                "Enable citizen militia system during raids. When enabled, citizens will temporarily become guards to defend the colony. Set to false to use the old raid system.")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-908-                                .define("EnableCitizenMilitia", true);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-909-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:910:                MILITIA_CONVERSION_PERCENTAGE = BUILDER.comment(
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-911-                                "Percentage of eligible citizens to convert to militia guards during raids (0.0 - 1.0). For example: 0.3 = 30% of citizens become militia.")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-912-                                .defineInRange("MilitiaConversionPercentage", 0.3, 0.0, 1.0);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-913-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-914-                MILITIA_MIN_CITIZEN_LEVEL = BUILDER.comment(
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-915-                                "Minimum level required for a citizen to be eligible for militia conversion. Higher levels = more experienced citizens only.")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-916-                                .defineInRange("MilitiaMinCitizenLevel", 3, 1, 99);
--
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2532-                BUILDER.pop(); // End Besiege System
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2533-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2534-                CONFIG = BUILDER.build();
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2535-        }
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2536-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2537-        public static boolean isSDMShopConversionEnabled() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2538:                return ENABLE_SDM_SHOP_CONVERSION.get();
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2539-        }
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2540-
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2541-        public static String getCurrencyItemName() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2542-                return CURRENCY_ITEM_NAME.get();
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2543-        }
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java-2544-
--
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-1-package net.machiavelli.minecolonytax.network;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-2-
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-3-import net.machiavelli.minecolonytax.MineColonyTax;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-4-import net.machiavelli.minecolonytax.network.packets.ClaimTaxPacket;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:5:import net.machiavelli.minecolonytax.network.packets.ColonyDataResponsePacket;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-6-import net.machiavelli.minecolonytax.network.packets.PayTaxDebtPacket;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-7-import net.machiavelli.minecolonytax.network.packets.EndVassalizationPacket;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-8-import net.machiavelli.minecolonytax.network.packets.UpdateTaxPermissionPacket;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-9-import net.machiavelli.minecolonytax.network.packets.UpdatePlayerTaxPermissionPacket;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-10-import net.machiavelli.minecolonytax.network.packets.RequestOfficerDataPacket;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-11-import net.machiavelli.minecolonytax.network.packets.OfficerDataResponsePacket;
--
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-24-import net.machiavelli.minecolonytax.network.packets.RequestInvestmentDataPacket;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-25-import net.machiavelli.minecolonytax.network.packets.InvestmentDataResponsePacket;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-26-import net.machiavelli.minecolonytax.network.packets.BuyInvestmentPacket;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-27-import net.minecraft.resources.ResourceLocation;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-28-import net.minecraft.server.level.ServerPlayer;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-29-import net.minecraftforge.network.NetworkDirection;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:30:import net.minecraftforge.network.NetworkRegistry;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-31-import net.minecraftforge.network.PacketDistributor;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:32:import net.minecraftforge.network.simple.SimpleChannel;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-33-
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-34-public class NetworkHandler {
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:35:        public static final String PROTOCOL_VERSION = "3";
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:36:        public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-37-                        new ResourceLocation(MineColonyTax.MOD_ID, "main"),
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:38:                        () -> PROTOCOL_VERSION,
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:39:                        PROTOCOL_VERSION::equals,
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:40:                        PROTOCOL_VERSION::equals);
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-41-
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-42-        private static int packetId = 0;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-43-
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-44-        private static int nextId() {
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-45-                return packetId++;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-46-        }
--
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-58-                CHANNEL.messageBuilder(RequestColonyDataPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-59-                                .decoder(RequestColonyDataPacket::new)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-60-                                .encoder(RequestColonyDataPacket::toBytes)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-61-                                .consumerMainThread(RequestColonyDataPacket::handle)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-62-                                .add();
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-63-
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:64:                CHANNEL.messageBuilder(ColonyDataResponsePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:65:                                .decoder(ColonyDataResponsePacket::new)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:66:                                .encoder(ColonyDataResponsePacket::toBytes)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:67:                                .consumerMainThread(ColonyDataResponsePacket::handle)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-68-                                .add();
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-69-
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-70-                CHANNEL.messageBuilder(ClaimTaxPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-71-                                .decoder(ClaimTaxPacket::new)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-72-                                .encoder(ClaimTaxPacket::toBytes)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java-73-                                .consumerMainThread(ClaimTaxPacket::handle)
--
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-15-import java.util.Map;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-16-import java.util.function.Supplier;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-17-
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-18-/**
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-19- * Packet sent from server to client containing colony tax data for the GUI
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-20- */
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:21:public class ColonyDataResponsePacket {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-22-    private final List<ColonyTaxData> colonyData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-23-    private final List<VassalIncomeData> vassalData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-24-    private final Map<Integer, List<EventLogEntry>> eventLogData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-25-    private final List<ColonySummary> allColonySummaries;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-26-
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:27:    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-28-        this.colonyData = colonyData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-29-        this.vassalData = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-30-        this.eventLogData = new HashMap<>();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-31-        this.allColonySummaries = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-32-    }
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-33-
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:34:    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-35-        this.colonyData = colonyData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-36-        this.vassalData = vassalData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-37-        this.eventLogData = new HashMap<>();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-38-        this.allColonySummaries = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-39-    }
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-40-
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:41:    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-42-                                    Map<Integer, List<EventLogEntry>> eventLogData) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-43-        this.colonyData = colonyData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-44-        this.vassalData = vassalData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-45-        this.eventLogData = eventLogData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-46-        this.allColonySummaries = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-47-    }
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-48-
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:49:    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-50-                                    Map<Integer, List<EventLogEntry>> eventLogData,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-51-                                    List<ColonySummary> allColonySummaries) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-52-        this.colonyData = colonyData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-53-        this.vassalData = vassalData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-54-        this.eventLogData = eventLogData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-55-        this.allColonySummaries = allColonySummaries != null ? allColonySummaries : new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-56-    }
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-57-    
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:58:    public ColonyDataResponsePacket(FriendlyByteBuf buf) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-59-        // Read colony data
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-60-        int size = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-61-        this.colonyData = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-62-        
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-63-        for (int i = 0; i < size; i++) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java-64-            int colonyId = buf.readInt();
--
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java-63-            Map<Integer, List<EventLogEntry>> eventLogData = new HashMap<>();
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java-64-            for (ColonyTaxData data : colonyData) {
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java-65-                eventLogData.put(data.getColonyId(), RandomEventManager.getEventLog(data.getColonyId()));
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java-66-            }
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java-67-            NetworkHandler.CHANNEL.send(
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java-68-                    PacketDistributor.PLAYER.with(() -> player),
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java:69:                    new ColonyDataResponsePacket(colonyData, vassalData, eventLogData));
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java-70-        });
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java-71-        return true;
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java-72-    }
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java-73-}
--
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java-219-                        : com.minecolonies.api.IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()) {
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java-220-                    allColonySummaries.add(new ColonySummary(c.getID(), c.getName()));
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java-221-                }
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java-222-
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java-223-                // Send response back to client
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java-224-                NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java:225:                    new ColonyDataResponsePacket(colonyData, vassalData, eventLogData, allColonySummaries));
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java-226-            }
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java-227-        });
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java-228-        return true;
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java-229-    }
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java-230-}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 6 \"startJoinPhase\\(|setAccepted\\(|isAccepted\\(|JOINING|Leave War|leaveWar|accept\" src/main/java/net/machiavelli/minecolonytax/WarSystem.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 6 \"endWar\\(\" src/main/java/net/machiavelli/minecolonytax | rg -v \"public static void endWar\"" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 491ms:
91-                    .withBold(true)
92-                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt joinwar"))
93-                    .withHoverEvent(
94-                            new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to join the war!")
95-                                    .withStyle(style -> style.withColor(ChatFormatting.AQUA)))));
96-
97:    private static final Component LEAVE_MSG = Component.literal("[Leave War]")
98-            .withStyle(style -> style.withColor(ChatFormatting.RED)
99-                    .withBold(true)
100-                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wnt leavewar"))
101-                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
102-                            Component.literal("Click to leave the war!").withStyle(ChatFormatting.AQUA))));
103-
--
2253-            net.machiavelli.minecolonytax.db.WarStatsDB.recordWarKill(
2254-                    killer.getUUID(), killer.getName().getString(),
2255-                    killed.getUUID(), killed.getName().getString());
2256-        }
2257-    }
2258-
2259:    public static void startJoinPhase(IColony colony, ServerPlayer attacker, ServerPlayer owner) {
2260-        FtbTeamsCompat.TeamHandle attackerTeam = FTB_TEAMS_INSTALLED
2261-                ? FtbTeamsCompat.getTeamForPlayer(attacker.getUUID()).orElse(null)
2262-                : null;
2263-        FtbTeamsCompat.TeamHandle defenderTeam = FTB_TEAMS_INSTALLED
2264-                ? FtbTeamsCompat.getTeamForPlayer(owner.getUUID()).orElse(null)
2265-                : null;
--
2418-            setWarInteractionPermissions(war.getColony(), true);
2419-            if (war.getAttackerColony() != null) {
2420-                setWarInteractionPermissions(war.getAttackerColony(), true);
2421-            }
2422-            startWarCountdown(war);
2423-        }, joinDurationMillis);
2424:        war.setAccepted(true);
2425-    }
2426-
2427-    private static void sendNotificationToColonyParticipants(IColony colony, Component message) {
2428-        if (colony == null || colony.getWorld() == null || colony.getWorld().getServer() == null) {
2429-            return;
2430-        }
--
2825-                // SECONDARY COLONY (Outpost) - Can be attacked while owner is offline
2826-                WARSYSTEM_LOGGER.info("Outpost attack initiated on {} (owner offline) by {}",
2827-                        targetColony.getName(), attacker.getName().getString());
2828-                source.sendSuccess(() -> Component.literal(
2829-                        "âš” OUTPOST ASSAULT! Target colony owner is offline, but this is a secondary colony (outpost). Attack proceeds!")
2830-                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
2831:                // Continue with auto-accept flow for offline outpost attacks
2832-                return processOfflineOutpostAttack(attacker, targetColony, attackerColony, source);
2833-            } else {
2834-                // PRIMARY COLONY - Owner must be online
2835-                if (isPrimaryColony) {
2836-                    source.sendFailure(Component.literal(
2837-                            "Target colony is a PRIMARY colony (capital). The owner must be online to defend!"));
--
2860-                        .append(Component.literal("\nThe drums of war sound! Join phase starting immediately!")
2861-                                .withStyle(ChatFormatting.AQUA))
2862-                        .append(Component.literal("\n----------------------------------------")
2863-                                .withStyle(ChatFormatting.DARK_GRAY));
2864-                broadcastToServer(autoAcceptMsg);
2865-            }
2866:            startJoinPhase(targetColony, attacker, owner);
2867-            return 1;
2868-        }
2869-
2870-        WARSYSTEM_LOGGER.info("Adding pending war request for colony {} from attacker {}", targetColony.getID(),
2871-                attacker.getUUID());
2872-        if (ServerLifecycleHooks.getCurrentServer() != null) {
--
2935-                .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED).withBold(true))
2936-                .append(Component.literal("\n"))
2937-                .append(Component.literal(attacker.getName().getString())
2938-                        .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)))
2939-                .append(Component.literal(" seeks to wage war against your colony!")
2940-                        .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)))
2941:                .append(Component.literal("\n\nDo you accept this challenge?")
2942-                        .withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
2943-                .append(Component.literal("\n"))
2944-                .append(createAcceptButton(targetColony))
2945-                .append(" ")
2946-                .append(createDeclineButton(targetColony));
2947-        owner.sendSystemMessage(message);
--
2951-        WARSYSTEM_LOGGER.info("[War] Target Colony Owner: {}", targetColony.getPermissions().getOwner());
2952-        return 1;
2953-    }
2954-
2955-    /**
2956-     * Handles attacks on secondary colonies (outposts) when the owner is offline.
2957:     * The war proceeds with auto-accept and the attacker fights against the
2958-     * colony's guards.
2959-     * The defender can still win if the attacker runs out of lives or time expires.
2960-     */
2961-    private static int processOfflineOutpostAttack(ServerPlayer attacker, IColony targetColony,
2962-            IColony attackerColony, CommandSourceStack source) {
2963-
--
3155-            source.sendFailure(Component.literal("Colony " + targetColony.getName() + " has extortion immunity for "
3156-                    + hoursRemaining + " more hours. Use regular war declaration."));
3157-            return 0;
3158-        }
3159-
3160-        if (!TaxConfig.WAR_ACCEPTANCE_REQUIRED.get()) {
3161:            // Auto-accept is enabled, show extortion choice to defender with timer
3162-            showExtortionChoiceWithTimer(attacker, targetColony, owner, extortionPercent);
3163-            return 1;
3164-        } else {
3165:            // Manual acceptance is required, add extortion to pending request
3166-            WARSYSTEM_LOGGER.info("Adding pending war request with extortion for colony {} from attacker {}",
3167-                    targetColony.getID(), attacker.getUUID());
3168-            if (ServerLifecycleHooks.getCurrentServer() != null) {
3169-                String attackerColonyName = attackerColony != null ? attackerColony.getName()
3170-                        : attacker.getName().getString() + "'s forces";
3171-                Component warDeclarationMsg = Component.empty()
--
3211-                    "War declaration with " + extortionPercent + "% extortion demand sent to " + targetColony.getName())
3212-                    .withStyle(ChatFormatting.YELLOW));
3213-            return 1;
3214-        }
3215-    }
3216-
3217:    public static int processWarResponse(ServerPlayer executor, int colonyId, boolean accepted,
3218-            CommandSourceStack source) {
3219-        Object requestObj = pendingWarRequests.get(colonyId);
3220-        java.util.UUID attackerUUID = null;
3221-        if (requestObj instanceof WarRequest wr) {
3222-            attackerUUID = wr.attacker();
3223-        } else if (requestObj instanceof WarRequestWithExtortion wre) {
3224-            attackerUUID = wre.attacker();
3225-        }
3226-        if (attackerUUID == null) {
3227-            source.sendFailure(Component.literal("No active war request found for colony ID " + colonyId +
3228:                    ". Only an authorized officer or the colony owner may accept.")
3229-                    .withStyle(s -> s.withColor(ChatFormatting.RED)));
3230-            WARSYSTEM_LOGGER.warn(
3231-                    "No pending war or extortion war request found for colony ID {} when {} attempted to respond.",
3232-                    colonyId, executor.getName().getString());
3233-            return 0;
3234-        }
--
3244-        }
3245-
3246-        Rank executorRank = targetColony.getPermissions().getRank(executor.getUUID());
3247-        boolean isAuthorized = targetColony.getPermissions().getOwner().equals(executor.getUUID()) ||
3248-                (executorRank != null && executorRank.isColonyManager());
3249-        if (!isAuthorized) {
3250:            source.sendFailure(Component.literal("You are not authorized to accept/decline this war request.")
3251-                    .withStyle(s -> s.withColor(ChatFormatting.RED)));
3252-            WARSYSTEM_LOGGER.warn("{} is not authorized to respond to war request for colony {}.",
3253-                    executor.getName().getString(), targetColony.getName());
3254-            return 0;
3255-        }
3256-
--
3267-            WARSYSTEM_LOGGER.warn("Attacker {} is offline when {} tried to respond to war request for colony {}.",
3268-                    attackerUUID, executor.getName().getString(), targetColony.getName());
3269-            return 0;
3270-        }
3271-        pendingWarRequests.remove(colonyId);
3272-
3273:        if (accepted) {
3274:            WARSYSTEM_LOGGER.info("War request for colony {} accepted by {}.", targetColony.getID(),
3275-                    executor.getName().getString());
3276-            if (ServerLifecycleHooks.getCurrentServer() != null) {
3277-                IColony attackerColony = IColonyManager.getInstance().getColonies(attacker.level()).stream()
3278-                        .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
3279-                        .findFirst().orElse(null);
3280-                String attackerColonyName = attackerColony != null ? attackerColony.getName()
--
3286-                                .withStyle(ChatFormatting.DARK_GRAY))
3287-                        .append(Component.literal("\nThe colony of ").withStyle(ChatFormatting.YELLOW))
3288-                        .append(Component.literal(targetColony.getName()).withStyle(ChatFormatting.BLUE,
3289-                                ChatFormatting.BOLD))
3290-                        .append(Component.literal(" (led by ").withStyle(ChatFormatting.YELLOW))
3291-                        .append(Component.literal(executor.getName().getString()).withStyle(ChatFormatting.BLUE))
3292:                        .append(Component.literal(") has accepted the challenge! War against ")
3293-                                .withStyle(ChatFormatting.YELLOW))
3294-                        .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED,
3295-                                ChatFormatting.BOLD))
3296-                        .append(Component.literal(" will now proceed to the join phase!")
3297-                                .withStyle(ChatFormatting.AQUA))
3298-                        .append(Component.literal("\n----------------------------------------")
3299-                                .withStyle(ChatFormatting.DARK_GRAY));
3300-                broadcastToServer(warAcceptedMsg);
3301-            }
3302:            startJoinPhase(targetColony, attacker, executor);
3303-        } else {
3304-            WARSYSTEM_LOGGER.info("War request for colony {} declined by {}.", targetColony.getID(),
3305-                    executor.getName().getString());
3306-            executor.sendSystemMessage(Component.literal("âŒ War declaration declined!").withStyle(ChatFormatting.RED,
3307-                    ChatFormatting.BOLD));
3308-            attacker.sendSystemMessage(Component.literal("âŒ " + targetColony.getName() + " declined your war request!")
--
3329-    }
3330-
3331-    private static Component createAcceptButton(IColony colony) {
3332-        return Component.literal("[Accept]")
3333-                .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
3334-                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
3335:                                String.format("/wnt war accept %d", colony.getID()))));
3336-    }
3337-
3338-    private static Component createDeclineButton(IColony colony) {
3339-        return Component.literal("[Decline]")
3340-                .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)
3341-                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
--
3344-
3345-    private static Component createStartWarButton(IColony colony) {
3346-        return Component.literal("[âš”ï¸ START WAR NOW]")
3347-                .setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED)
3348-                        .withBold(true)
3349-                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
3350:                                String.format("/wnt war accept %d", colony.getID())))
3351-                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
3352-                                Component.literal("Click to start the war immediately")
3353-                                        .withStyle(ChatFormatting.RED))));
3354-    }
3355-
3356-    /**
--
3593-            source.sendFailure(Component.literal(
3594-                    "You are not eligible to join this war. Only colony owners, officers, and friends can participate."));
3595-            return 0;
3596-        }
3597-    }
3598-
3599:    // --- Logic moved from WarCommands.leaveWar ---
3600-    public static int processLeaveWar(ServerPlayer player, CommandSourceStack source) {
3601-        WarData war = getActiveWarForPlayer(player);
3602-        if (war == null) {
3603-            source.sendFailure(Component.literal("No active war to leave."));
3604-            return 0;
3605-        }
--
3910-                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
3911-                .append(Component
3912-                        .literal("\n\n" + attacker.getName().getString() + " has declared war but offers terms:")
3913-                        .withStyle(ChatFormatting.YELLOW))
3914-                .append(Component.literal("\nðŸ’° Pay " + extortionPercent + "% of your balance to avoid war")
3915-                        .withStyle(ChatFormatting.GOLD))
3916:                .append(Component.literal("\nâš”ï¸ Or let the war begin immediately (auto-accepted)")
3917-                        .withStyle(ChatFormatting.RED))
3918-                .append(Component.literal("\nâ° You have " + timeLimitMinutes + " minutes to decide!")
3919-                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
3920-                .append(Component.literal("\n\nChoose quickly:\n").withStyle(ChatFormatting.WHITE))
3921-                .append(createStartWarButton(targetColony));
3922-    }
--
3931-                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
3932-                .append(Component
3933-                        .literal("\n" + attacker.getName().getString() + " has declared war but offers terms:\n")
3934-                        .withStyle(ChatFormatting.YELLOW))
3935-                .append(Component.literal("ðŸ’° Pay " + extortionPercent + "% of your balance to avoid war\n")
3936-                        .withStyle(ChatFormatting.GOLD))
3937:                .append(Component.literal("âš”ï¸ Or accept the war and fight for your colony's honor\n")
3938-                        .withStyle(ChatFormatting.RED))
3939-                .append(Component.literal("\nChoose wisely:\n").withStyle(ChatFormatting.WHITE))
3940-                .append(createAcceptButton(targetColony))
3941-                .append(" ")
3942-                .append(createDeclineButton(targetColony))
3943-                .append(" ")
--
3974-        String defenderTeamID;
3975-        int defenderColonyId;
3976-        int attackerColonyId;
3977-        long warStartTime;
3978-        long joinPhaseEndTime;
3979-        String status;
3980:        boolean accepted;
3981-        boolean stalemateTriggered;
3982-        Map<String, Integer> attackerLives;
3983-        Map<String, Integer> defenderLives;
3984-        List<Integer> defenderGuardIDs;
3985-        List<Integer> attackerGuardIDs;
3986-        List<String> attackerAllies;
--
3995-        int initialDefenderTotalLives;
3996-        String penaltyReport;
3997-        // Added 2026-05-25 (audit fix): previously these fields were silently dropped
3998-        // on save/restore â€” see WarData restoration constructor docstring.
3999-        Map<String, Boolean> originalHostilePerms;            // Action.name() -> boolean
4000-        Map<String, Boolean> originalHostilePermsForAttacker;
4001:        List<String> acceptedAllies;
4002-        List<String> declinedAllies;
4003-        boolean offlineOutpostWar;
4004-        ProposalSaveEntry activeProposal; // null if no proposal in flight
4005-    }
4006-
4007-    private static class ProposalSaveEntry {
--
4053-                e.defenderTeamID = defTid.toString();
4054-                e.defenderColonyId = entry.getKey();
4055-                e.attackerColonyId = war.getAttackerColony() != null ? war.getAttackerColony().getID() : -1;
4056-                e.warStartTime = war.warStartTime;
4057-                e.joinPhaseEndTime = war.joinPhaseEndTime;
4058-                e.status = war.getStatus().name();
4059:                e.accepted = war.isAccepted();
4060-                e.stalemateTriggered = war.isStalemateTriggered();
4061-                e.penaltyReport = war.getPenaltyReport();
4062-                e.initialAttackerGuards = war.initialAttackerGuards;
4063-                e.remainingAttackerGuards = war.remainingAttackerGuards;
4064-                e.initialDefenderGuards = war.initialDefenderGuards;
4065-                e.remainingDefenderGuards = war.remainingDefenderGuards;
--
4090-                    war.originalHostilePerms.forEach((a, b) -> e.originalHostilePerms.put(a.name(), b));
4091-                }
4092-                if (war.originalHostilePermsForAttacker != null) {
4093-                    e.originalHostilePermsForAttacker = new HashMap<>();
4094-                    war.originalHostilePermsForAttacker.forEach((a, b) -> e.originalHostilePermsForAttacker.put(a.name(), b));
4095-                }
4096:                e.acceptedAllies = new ArrayList<>();
4097:                war.getAcceptedAllies().forEach(uuid -> e.acceptedAllies.add(uuid.toString()));
4098-                e.declinedAllies = new ArrayList<>();
4099-                war.getDeclinedAllies().forEach(uuid -> e.declinedAllies.add(uuid.toString()));
4100-                e.offlineOutpostWar = war.isOfflineOutpostWar();
4101-
4102-                net.machiavelli.minecolonytax.peace.PeaceProposal pp = war.getActiveProposal();
4103-                if (pp != null) {
--
4261-        if (status == WarData.WarStatus.INWAR) {
4262-            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
4263-            if (now >= e.warStartTime + warDurationMs) {
4264-                WARSYSTEM_LOGGER.info("War {} expired during server downtime â€” resolving via handleTimeExpiry", e.warID);
4265-                expiredDuringDowntime = true;
4266-            }
4267:        } else if (status == WarData.WarStatus.JOINING) {
4268-            if (now >= e.joinPhaseEndTime) {
4269-                WARSYSTEM_LOGGER.info("War {} join phase expired during downtime, transitioning to INWAR", e.warID);
4270-                status = WarData.WarStatus.INWAR;
4271-                e.warStartTime = now;
4272-            }
4273-        }
--
4307-            restoredHostilePermsAtk = new HashMap<>();
4308-            for (Map.Entry<String, Boolean> en : e.originalHostilePermsForAttacker.entrySet()) {
4309-                try { restoredHostilePermsAtk.put(Action.valueOf(en.getKey()), en.getValue()); }
4310-                catch (IllegalArgumentException ignored) {}
4311-            }
4312-        }
4313:        Set<UUID> acceptedAlliesSet = parseUUIDList(e.acceptedAllies);
4314-        Set<UUID> declinedAlliesSet = parseUUIDList(e.declinedAllies);
4315-        net.machiavelli.minecolonytax.peace.PeaceProposal restoredProposal = null;
4316-        if (e.activeProposal != null && e.activeProposal.type != null && e.activeProposal.proposer != null) {
4317-            try {
4318-                restoredProposal = new net.machiavelli.minecolonytax.peace.PeaceProposal(
4319-                        net.machiavelli.minecolonytax.peace.PeaceProposal.Type.valueOf(e.activeProposal.type),
--
4329-                UUID.fromString(e.attacker),
4330-                UUID.fromString(e.defender),
4331-                atkTid,
4332-                defTid,
4333-                e.warStartTime, e.joinPhaseEndTime,
4334-                bossEvent, defenderColony, attackerColony,
4335:                status, e.accepted,
4336-                e.initialAttackerGuards, e.remainingAttackerGuards,
4337-                e.initialDefenderGuards, e.remainingDefenderGuards,
4338-                e.initialAttackerTotalLives, e.initialDefenderTotalLives,
4339-                attackerLives, defenderLives,
4340-                defenderGuardIDSet, attackerGuardIDSet, attackerAlliesSet, defenderAlliesSet,
4341-                spectatorsSet, lastLifeSet,
4342-                e.penaltyReport, e.stalemateTriggered,
4343-                restoredHostilePerms, restoredHostilePermsAtk,
4344:                acceptedAlliesSet, declinedAlliesSet,
4345-                e.offlineOutpostWar,
4346-                restoredProposal);
4347-
4348-        ACTIVE_WARS.put(e.defenderColonyId, warData);
4349-
4350-        // Finding 5 cont'd: war ran past its clock while we were down. Register
--
4412-                scheduleTimerWarnings(warData, remaining);
4413-            }
4414-
4415-            updateBossBar(warData);
4416-            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
4417-                    e.warID, defenderColony.getName(), remaining);
4418:        } else if (warData.getStatus() == WarData.WarStatus.JOINING) {
4419-            warData.alliesBossEvent = new ServerBossEvent(
4420-                    Component.literal("Joining War - " + defenderColony.getName()),
4421-                    BossEvent.BossBarColor.YELLOW,
4422-                    BossEvent.BossBarOverlay.PROGRESS);
4423-            warData.alliesBossEvent.setProgress(1.0f);
4424-            warData.alliesBossEvent.setVisible(true);
--
4427-            if (remainingJoinMs > 0) {
4428-                final int colonyId = e.defenderColonyId;
4429-                TickScheduler.scheduleDelayed(() -> {
4430-                    if (!ACTIVE_WARS.containsKey(colonyId))
4431-                        return;
4432-                    WarData w = ACTIVE_WARS.get(colonyId);
4433:                    if (w == null || w.getStatus() != WarData.WarStatus.JOINING)
4434-                        return;
4435-                    w.setStatus(WarData.WarStatus.INWAR);
4436-                    w.warStartTime = System.currentTimeMillis();
4437-                    finalizeWarStart(w);
4438-                    setWarInteractionPermissions(w.getColony(), true);
4439-                    if (w.getAttackerColony() != null) {
--
4443-                    long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
4444-                    scheduleTimerWarnings(w, warDurationMillis);
4445-                }, remainingJoinMs);
4446-            }
4447-
4448-            updateBossBar(warData);
4449:            WARSYSTEM_LOGGER.info("Restored JOINING war {} for colony {} ({} ms until war starts)",
4450-                    e.warID, defenderColony.getName(), remainingJoinMs);
4451-        }
4452-
4453-        Component restoreMsg = Component.literal("âš” War Restored: ")
4454-                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
4455-                .append(Component
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 705ms:
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-330-    }
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-331-
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-332-    /**
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-333-     * Record a war ending. Writes one row to war_history and updates
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-334-     * wars_won / wars_lost / war_stalemates for all online participants.
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-335-     *
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java:336:     * @param warData        The WarData captured at endWar() time (still valid after remove).
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-337-     * @param outcome        "ATTACKER_WIN" | "DEFENDER_WIN" | "STALEMATE"
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-338-     * @param reparationsPaid Amount transferred as part of the outcome.
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-339-     * @param ledToOccupation Whether this war immediately led to an occupation.
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-340-     */
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-341-    public static void recordWarEnd(WarData warData, String outcome,
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-342-                                    long reparationsPaid, boolean ledToOccupation) {
--
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-567-            }
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-568-        }, "updateServerState");
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-569-    }
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-570-
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-571-    // ==================== Helpers ====================
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-572-
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java:573:    /** Determine the DB outcome string from a WarData at the moment endWar() is called. */
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-574-    public static String determineOutcome(WarData warData) {
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-575-        String report = warData.getPenaltyReport();
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-576-        if (report == null || report.isEmpty()) return "STALEMATE";
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-577-        if (report.contains("TOTAL VICTORY") || report.contains("COUNTER-CONQUEST")) {
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-578-            return warData.getRemainingDefenderGuards() > 0 ? "DEFENDER_WIN" : "ATTACKER_WIN";
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java-579-        }
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-336-            }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-337-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-338-            // Auto-surrender if either side is depleted
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-339-            if (defenderResult == -1 || attackerResult == -1) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-340-                IColony colony = defenderResult == -1 ? defenderColony : attackerColony;
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-341-                if (colony != null) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:342:                    endWar(colony);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-343-                }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-344-            }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-345-        }, 60_000, 60_000);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-346-    }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-347-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-348-    public static void setWarInteractionPermissions(IColony colony, boolean allowed) {
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-546-        if (attackerPlayerCount == 0 || defenderPlayerCount == 0) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-547-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-548-                Component cancelMsg = Component.literal("War cancelled due to lack of participants.")
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-549-                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-550-                broadcastToServer(cancelMsg);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-551-            }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:552:            endWar(war.getColony());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-553-            return;
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-554-        }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-555-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-556-        if (Math.abs(attackerPlayerCount - defenderPlayerCount) > 1) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-557-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-558-                Component ratioMsg = Component.literal(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-559-                        "Join phase ratio condition not met! Teams must be balanced (difference <= 1). Current: Attacker="
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-560-                                + attackerPlayerCount + ", Defender=" + defenderPlayerCount)
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-561-                        .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-562-                broadcastToServer(ratioMsg);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-563-            }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:564:            endWar(war.getColony()); // clean up ??? bare return would leak the war in ACTIVE_WARS
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-565-            return;
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-566-        }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-567-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-568-        war.bossEvent.removeAllPlayers();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-569-        war.getAttackerLives().keySet().forEach(uuid -> {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-570-            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-809-                if (vassalized) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-810-                    WARSYSTEM_LOGGER.info("Colony {} has been vassalized by {} for {} hours at {}% tribute",
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-811-                            war.getColony().getName(), war.getAttacker(), durationHours, tributePercent);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-812-                }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-813-            }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-814-        }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:815:        endWar(war.getColony());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-816-    }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-817-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-818-    /**
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-819-     * Applies economic penalties to both sides during a stalemate.
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-820-     * 
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-821-     * @param war The war data containing information about the conflict
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1259-        } else {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1260-            WARSYSTEM_LOGGER.error("Ownership transfer failed for colony {}", colony.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1261-            return false;
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1262-        }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1263-    }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1264-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1266-        if (colony == null) return;
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1267-        // Finding 10: make endWar idempotent. Atomically remove the WarData from
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1268-        // ACTIVE_WARS; if it was already removed (a concurrent endWar call from a
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1269-        // different code path), bail out ??? re-running the rest of this method
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1270-        // would double-fire demotions, history records, treasury cleanup, etc.
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1271-        WarData warData = ACTIVE_WARS.remove(colony.getID());
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1637-            for (UUID atkUUID : war.getAttackerLives().keySet()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1638-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(atkUUID);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1639-                if (p != null)
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1640-                    PlayerWarDataManager.incrementWarsLost(p);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1641-            }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1642-            handleVictoryRewards(war, true); // true for defender victory
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1643:            endWar(war.getColony());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1644-            return;
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1645-        } else if (defenderTotalLives == 0 && war.getRemainingDefenderGuards() == 0) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1646-            MutableComponent attackerVictoryMsg = Component.translatable("war.time.expired.title")
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1647-                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1648-                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1649-                    .append(Component.literal("\n"))
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1662-            for (UUID defUUID : war.getDefenderLives().keySet()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1663-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defUUID);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1664-                if (p != null)
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1665-                    PlayerWarDataManager.incrementWarsLost(p);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1666-            }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1667-            handleVictoryRewards(war, false); // false for attacker victory
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1668:            endWar(war.getColony());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1669-            return;
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1670-        }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1671-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1672-        // Check for stalemate due to no losses on either side by timeout
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1673-        if (attackerTotalLives == war.initialAttackerTotalLives && // No player lives lost by attackers
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1674-                defenderTotalLives == war.initialDefenderTotalLives && // No player lives lost by defenders
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1698-                TaxManager.deductColonyTax(war.getAttackerColony(), TaxConfig.getWarStalematePercentage()); // Attacker
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1699-                                                                                                            // colony
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1700-            war.setPenaltyReport(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1701-                    "Stalemate (Timeout - No Losses): Both sides lose " + (TaxConfig.getWarStalematePercentage() * 100)
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1702-                            + "% of their balances and colony revenue is reduced by "
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1703-                            + (TaxConfig.getWarStalematePercentage() * 100) + "%.");
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1704:            endWar(war.getColony());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1705-            return;
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1706-        }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1707-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1708-        // Strategic victory/loss based on proportional strength remaining
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1709-        double attackerNormalizedStrength = (double) (attackerTotalLives + war.getRemainingAttackerGuards())
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1710-                / (war.initialAttackerTotalLives + war.initialAttackerGuards);
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1808-            TaxManager.deductColonyTax(war.getColony(), TaxConfig.getWarStalematePercentage());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1809-            if (war.getAttackerColony() != null)
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1810-                TaxManager.deductColonyTax(war.getAttackerColony(), TaxConfig.getWarStalematePercentage());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1811-            broadcastComponent(war, strategicMsg);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1812-        }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1813-        war.setPenaltyReport(reportOutcome);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1814:        endWar(war.getColony());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1815-    }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1816-
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1817-    // Helper to broadcast war results to entire server
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1818-    private static void broadcastComponent(WarData war, Component message) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1819-        broadcastToServer(message);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1820-    }
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4357-        // demote this to a TickScheduler.scheduleDelayed(.., 100ms) call.
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4358-        if (expiredDuringDowntime) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4359-            try {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4360-                handleTimeExpiry(warData);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4361-            } catch (Throwable t) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4362-                WARSYSTEM_LOGGER.error("Failed to resolve downtime-expired war {} via handleTimeExpiry; falling back to endWar.", e.warID, t);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:4363:                try { endWar(defenderColony); } catch (Throwable t2) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4364-                    WARSYSTEM_LOGGER.error("Fallback endWar also failed for downtime-expired war {}; war removed without rewards.", e.warID, t2);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4365-                    ACTIVE_WARS.remove(e.defenderColonyId);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4366-                }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4367-            }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4368-            // True for the loader: we processed this war successfully.
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-4369-            return true;
--
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java-143-        }
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java-144-    }
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java-145-
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java-146-    /**
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java-147-     * Restore all ledgered blocks for the given war back to their pre-explosion
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java-148-     * state. Spreads work across ticks to avoid chunk-flicker and lag spikes.
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:149:     * Called from WarSystem.endWar() ??? safe to call when no ledger exists.
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java-150-     */
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java-151-    public static void restoreWarDamage(UUID warId, Level level) {
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java-152-        Map<BlockPos, BlockInfo> ledgerMap = LEDGERS.remove(warId);
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java-153-        if (ledgerMap == null || ledgerMap.isEmpty()) return;
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java-154-        if (!(level instanceof ServerLevel serverLevel)) return;
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java-155-
--
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-318-        }
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-319-        List<IColony> coloniesToStop = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-320-        for (WarData war : activeWars) {
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-321-            coloniesToStop.add(war.getColony());
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-322-        }
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-323-        for (IColony colony : coloniesToStop) {
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:324:            WarSystem.endWar(colony);
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-325-            WarSystem.sendColonyMessage(colony,
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-326-                    Component.literal("War has been stopped by an operator.").withStyle(ChatFormatting.GOLD));
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-327-        }
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-328-        ctx.getSource().sendSuccess(
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-329-                () -> Component.literal("All active wars have been stopped.").withStyle(ChatFormatting.GREEN), false);
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-330-        return 1;
--
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-341-        }
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-342-        if (!WarSystem.ACTIVE_WARS.containsKey(colony.getID())) {
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-343-            ctx.getSource().sendFailure(
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-344-                    Component.literal("No active war for colony: " + colonyName).withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-345-            return 0;
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-346-        }
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:347:        WarSystem.endWar(colony);
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-348-        WarSystem.sendColonyMessage(colony,
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-349-                Component.literal("War for " + colony.getName() + " has been stopped by an operator.")
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-350-                        .withStyle(ChatFormatting.GOLD));
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-351-        ctx.getSource().sendSuccess(
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-352-                () -> Component.literal("War stopped for " + colony.getName() + ".").withStyle(ChatFormatting.GREEN),
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java-353-                false);
--
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-155-             return 0;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-156-        }
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-157-
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-158-        // Finding 8: atomic check-and-clear BEFORE doing work. If two players
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-159-        // race to accept (chat click + command), only the first thread sees
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-160-        // the proposal non-null. The second sees null and bails. This avoids
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java:161:        // double reparations transfers and double-fires of endWar().
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-162-        synchronized (war) {
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-163-            PeaceProposal current = war.getActiveProposal();
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-164-            if (current == null || current != proposal) {
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-165-                ctx.getSource().sendFailure(Component.literal("Peace proposal already resolved."));
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-166-                return 0;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-167-            }
--
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-282-                responder.sendSystemMessage(Component.literal(acceptedMessageToResponder).append(whitePeaceMsg));
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-283-                // Broadcast to teams
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-284-                sendMessageToTeamFallback(war, true, whitePeaceMsg); // Attacker team
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-285-                sendMessageToTeamFallback(war, false, whitePeaceMsg); // Defender team
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-286-                // Set penalty report before endWar so war history logging captures peace outcome
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-287-                war.setPenaltyReport("White Peace: War ended by mutual agreement, no reparations");
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java:288:                WarSystem.endWar(war.getColony()); // Assumes WarSystem provides this
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-289-                break;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-290-            case REPARATIONS:
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-291-                UUID losingTeamId;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-292-                UUID winningPlayerId;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-293-
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-294-                // The proposer is the side offering to pay reparations; the responder's side receives.
--
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-336-                    sendMessageToTeamFallback(war, false, losingTeamMsg); // Defender team (lost)
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-337-                    sendMessageToTeamFallback(war, true, winningTeamMsg); // Attacker team (won)
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-338-                }
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-339-                // Set penalty report before endWar so war history logging captures peace outcome
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-340-                String payerSide = proposerWasAttacker ? "Attackers" : "Defenders";
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-341-                war.setPenaltyReport("Peace via Reparations: " + payerSide + " paid " + demandedAmount + " coins");
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java:342:                WarSystem.endWar(war.getColony());
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-343-                break;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-344-            case SURRENDER:
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-345-                // Surrender: proposer unconditionally surrenders, responder's side wins
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-346-                boolean surrendererWasAttacker = war.getAttackerLives().containsKey(proposal.getProposer());
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-347-                
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-348-                Component surrenderMsg = Component.literal("Surrender accepted! ").withStyle(ChatFormatting.GOLD);
--
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-367-                    net.machiavelli.minecolonytax.economy.WarExhaustionManager.recordWarLoss(war.getColony().getID());
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-368-                }
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-369-                
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-370-                // Set penalty report before endWar
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-371-                String surrenderingSide = surrendererWasAttacker ? "Attackers" : "Defenders";
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-372-                war.setPenaltyReport("Surrender: " + surrenderingSide + " surrendered unconditionally");
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java:373:                WarSystem.endWar(war.getColony());
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-374-                break;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-375-        }
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-376-        war.setActiveProposal(null);
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-377-    }
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-378-
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java-379-    private void sendMessageToTeamFallback(WarData war, boolean sendToAttacker, Component msg) {
--
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1758-                WarData war = WarSystem.ACTIVE_WARS.get(colony.getID());
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1759-                if (war == null) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1760-                        ctx.getSource().sendFailure(Component.literal("No active war found for colony: " + colonyName));
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1761-                        return 0;
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1762-                }
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1763-
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1764:                WarSystem.endWar(colony);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1765-                ctx.getSource().sendSuccess(() -> Component.literal("War stopped for colony: " + colonyName), false);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1766-                return 1;
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1767-        }
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1768-
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1769-        private static int stopAllWarsCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1770-                Collection<WarData> activeWars = WarSystem.ACTIVE_WARS.values();
--
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1777-                List<IColony> coloniesToStop = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1778-                for (WarData war : activeWars) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1779-                        coloniesToStop.add(war.getColony());
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1780-                }
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1781-
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1782-                for (IColony colony : coloniesToStop) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1783:                        WarSystem.endWar(colony);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1784-                }
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1785-
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1786-                ctx.getSource().sendSuccess(
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1787-                                () -> Component.literal("Stopped " + coloniesToStop.size() + " active wars."),
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1788-                                false);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java-1789-                return 1;
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
STATUS REWORK

- `finalizeWarStart` is not fully re-entrant safe. `militiaSupport.isEmpty()` prevents duplicate militia only while the same active `WarData` still holds spawned entities, but the live delayed join-phase callback does not check `ACTIVE_WARS` or prior status before transitioning. A late callback after `endWar` clears `militiaSupport` could re-spawn militia on stale war data.
- `ColonyDataResponsePacket` ordinal encode/decode is symmetric for the new format, and out-of-range ordinals fall back to `VASSAL`. But the packet layout changed without a protocol bump; old/new peers using `PROTOCOL_VERSION = "3"` can desync the stream.
- `ColonyDataCollector` correctly skips `occ == null` and `occ.isExpired()`, but it does not dedup against existing vassal rows. Same colony can appear twice if present in `VassalManager` and `OccupationManager`.
- `VassalsPage` null-kind fallback is correct: null renders as green `VASSAL` with no compact tag, and `VassalIncomeData` already normalizes null to `VASSAL`.

Fix suggestions:
1. Add an active-war/status guard before `finalizeWarStart`: `ACTIVE_WARS.get(colonyId) == war && war.getStatus() == JOINING`, then transition once.
2. Bump network `PROTOCOL_VERSION`, or version the vassal row payload explicitly.
3. Track seen colony IDs while adding vassal rows and skip/replace duplicate occupation rows deterministically.
tokens used
166.594
STATUS REWORK

- `finalizeWarStart` is not fully re-entrant safe. `militiaSupport.isEmpty()` prevents duplicate militia only while the same active `WarData` still holds spawned entities, but the live delayed join-phase callback does not check `ACTIVE_WARS` or prior status before transitioning. A late callback after `endWar` clears `militiaSupport` could re-spawn militia on stale war data.
- `ColonyDataResponsePacket` ordinal encode/decode is symmetric for the new format, and out-of-range ordinals fall back to `VASSAL`. But the packet layout changed without a protocol bump; old/new peers using `PROTOCOL_VERSION = "3"` can desync the stream.
- `ColonyDataCollector` correctly skips `occ == null` and `occ.isExpired()`, but it does not dedup against existing vassal rows. Same colony can appear twice if present in `VassalManager` and `OccupationManager`.
- `VassalsPage` null-kind fallback is correct: null renders as green `VASSAL` with no compact tag, and `VassalIncomeData` already normalizes null to `VASSAL`.

Fix suggestions:
1. Add an active-war/status guard before `finalizeWarStart`: `ACTIVE_WARS.get(colonyId) == war && war.getStatus() == JOINING`, then transition once.
2. Bump network `PROTOCOL_VERSION`, or version the vassal row payload explicitly.
3. Track seen colony IDs while adding vassal rows and skip/replace duplicate occupation rows deterministically.
