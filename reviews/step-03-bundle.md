## STEP 3 REVIEW BUNDLE — Multi-besieger refactor (partial)

Step 3 changes ACTIVE_RAIDS from Map<Integer, BesiegeRaidData> (colony-keyed, one raid per colony) to Map<UUID, BesiegeRaidData> (besieger-keyed). This unblocks multiple besiegers attacking the same colony concurrently AND allows besieging primary colonies (outcome routes via OccupationManager.TAX_ONLY from step 2). The legacy getActiveRaids() now returns a backward-compat colony→first-raid view. New getRaidsForColony() and getRaidForBesieger() helpers added.

EXPLICITLY DEFERRED for Phase 2 (please flag in findings):
- Shared defender pool: when two besiegers attack the same colony, each currently spawns its own mercenaries and tracks its own hostile-citizen set. Defenders are effectively doubled.
- Last-kill-credit semantics: no logic for 'whoever lands the killing blow wins; others get lost-the-race cooldown'.
- Each besieger's raid still ends independently when their per-raid defender count hits zero.

### DIFF: BesiegeManager.java
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
index e4d9cf9..1932358 100644
--- a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
@@ -8,10 +8,14 @@ import com.minecolonies.api.colony.ICitizenData;
 import com.minecolonies.api.colony.IColony;
 import com.minecolonies.api.colony.IColonyManager;
 import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
+import com.minecolonies.api.colony.permissions.Action;
+import com.minecolonies.api.colony.permissions.IPermissions;
+import com.minecolonies.api.colony.permissions.Rank;
 import com.minecolonies.core.entity.mobs.EntityMercenary;
 import net.machiavelli.minecolonytax.FirstColonyTracker;
 import net.machiavelli.minecolonytax.TaxConfig;
 import net.machiavelli.minecolonytax.WarSystem;
+import net.machiavelli.minecolonytax.militia.MilitiaAttackGoal;
 import net.machiavelli.minecolonytax.vassalization.VassalManager;
 import net.minecraft.ChatFormatting;
 import net.minecraft.core.BlockPos;
@@ -52,8 +56,17 @@ public class BesiegeManager {
     private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
     private static final String STORAGE_FILE = "config/warntax/besieged_colonies.json";
 
-    /** Active besiege raids (colonyId -> raid data). */
-    private static final Map<Integer, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
+    /**
+     * Active besiege raids, keyed by besieger UUID. Each besieger has at most one
+     * active raid at a time (solo combat rule). Multiple besiegers may concurrently
+     * raid the same colony — look them up by colony with {@link #getRaidsForColony(int)}.
+     *
+     * Phase 2 follow-up: defender pool is still per-raid, not shared across
+     * concurrent besiegers on the same colony. Each besieger currently spawns its
+     * own mercs and tracks its own hostile-citizen set; last-kill-credit semantics
+     * are not yet implemented.
+     */
+    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
 
     /** Persistent occupation records (colonyId -> occupation data). */
     private static final Map<Integer, BesiegeOccupationData> OCCUPATIONS = new ConcurrentHashMap<>();
@@ -81,8 +94,8 @@ public class BesiegeManager {
     public static void tick() {
         if (ACTIVE_RAIDS.isEmpty()) return;
 
-        for (Iterator<Map.Entry<Integer, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
-            Map.Entry<Integer, BesiegeRaidData> entry = it.next();
+        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
+            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
             BesiegeRaidData raid = entry.getValue();
 
             try {
@@ -180,27 +193,16 @@ public class BesiegeManager {
             return false;
         }
 
-        // 3. Cannot besiege first (primary) colonies — those require full war.
-        // Use FCT reverse lookup first: it is immune to corrupted/placeholder owner UUIDs.
-        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colonyId);
-        if (trackedFirstOwner != null) {
-            besieger.sendSystemMessage(Component.literal(
-                    "This is the colony owner's primary colony. Declare full war to contest it.")
-                    .withStyle(ChatFormatting.RED));
-            return false;
-        }
-        // Fallback: check via permissions owner in case FCT data is missing for this colony
-        UUID ownerUUID = colony.getPermissions().getOwner();
-        if (ownerUUID != null && FirstColonyTracker.isFirstColony(ownerUUID, colonyId)) {
-            besieger.sendSystemMessage(Component.literal(
-                    "This is the colony owner's primary colony. Declare full war to contest it.")
-                    .withStyle(ChatFormatting.RED));
-            return false;
-        }
+        // 3. Primary colonies CAN now be besieged. Outcome routes through
+        // OccupationManager in TAX_ONLY mode (deed never moves; auto-reclaim after
+        // PrimaryColonyTaxOccupationDays). Secondaries continue to use the legacy
+        // TRANSFER_PENDING flow. See ColonyTierGuard + OccupationManager.
 
-        // 4. Cannot besiege an already-besieged colony (unless reclaiming — handled separately)
-        if (ACTIVE_RAIDS.containsKey(colonyId)) {
-            besieger.sendSystemMessage(Component.literal("A besiege raid is already in progress for this colony!")
+        // 4. Solo rule: this besieger may not already have an active raid elsewhere.
+        // Multiple besiegers attacking the SAME colony concurrently is allowed.
+        if (ACTIVE_RAIDS.containsKey(besiegerUUID)) {
+            besieger.sendSystemMessage(Component.literal(
+                    "You already have an active besiege. Only one besiege at a time per player.")
                     .withStyle(ChatFormatting.RED));
             return false;
         }
@@ -271,8 +273,10 @@ public class BesiegeManager {
             return false;
         }
 
-        if (ACTIVE_RAIDS.containsKey(colonyId)) {
-            reclaimingPlayer.sendSystemMessage(Component.literal("A besiege raid is already in progress for this colony!")
+        // Solo rule: this player may not already have an active raid.
+        if (ACTIVE_RAIDS.containsKey(playerUUID)) {
+            reclaimingPlayer.sendSystemMessage(Component.literal(
+                    "You already have an active besiege/reclaim raid.")
                     .withStyle(ChatFormatting.RED));
             return false;
         }
@@ -300,7 +304,11 @@ public class BesiegeManager {
 
         try {
             BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
-            ACTIVE_RAIDS.put(colonyId, raid);
+            ACTIVE_RAIDS.put(besiegerUUID, raid);
+
+            // Grant the besieger hostile rank + combat permissions on the colony
+            // so MineColonies allows the player to attack citizens.
+            grantBesiegeCombatPermissions(colony, besiegerUUID);
 
             // Convert guards to hostile
             int guardCount = makeGuardsHostile(colony, besieger, raid);
@@ -414,8 +422,10 @@ public class BesiegeManager {
         entity.goalSelector.removeAllGoals(g -> true);
         entity.targetSelector.removeAllGoals(g -> true);
 
-        entity.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(
-                entity, 1.2D, false));
+        // Use MilitiaAttackGoal instead of vanilla MeleeAttackGoal — non-guard citizens
+        // lack the ATTACK_DAMAGE attribute, which causes MeleeAttackGoal.doHurtTarget() to
+        // crash with IllegalArgumentException.
+        entity.goalSelector.addGoal(0, new MilitiaAttackGoal(entity, 1.2D));
 
         // Retaliate against anyone who hits them (covers allies)
         entity.targetSelector.addGoal(0, new HurtByTargetGoal(entity));
@@ -603,6 +613,12 @@ public class BesiegeManager {
     private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
         IColony colony = getColonyById(raid.colonyId);
         if (colony != null) {
+            // Revoke combat permissions from the besieger (and any allies)
+            revokeBesiegeCombatPermissions(colony, raid.besiegingPlayerUUID);
+            for (UUID ally : raid.alliedPlayers) {
+                revokeBesiegeCombatPermissions(colony, ally);
+            }
+
             // Restore citizen AI
             for (int citizenId : raid.hostileCitizenIds) {
                 try {
@@ -650,7 +666,7 @@ public class BesiegeManager {
         }
 
         if (removeFromMap) {
-            ACTIVE_RAIDS.remove(raid.colonyId);
+            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
         }
     }
 
@@ -719,7 +735,19 @@ public class BesiegeManager {
     }
 
     public static boolean isActiveRaidOnColony(int colonyId) {
-        return ACTIVE_RAIDS.containsKey(colonyId);
+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
+            if (raid.colonyId == colonyId) return true;
+        }
+        return false;
+    }
+
+    /** All currently active besiege raids targeting this colony. */
+    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
+        List<BesiegeRaidData> matches = new ArrayList<>();
+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
+            if (raid.colonyId == colonyId) matches.add(raid);
+        }
+        return matches;
     }
 
     /**
@@ -742,7 +770,13 @@ public class BesiegeManager {
     public static void registerAlly(int colonyId, UUID allyUUID) {
         BesiegeRaidData raid = ACTIVE_RAIDS.get(colonyId);
         if (raid != null && TaxConfig.isBesiegeAlliesEnabled()) {
-            raid.alliedPlayers.add(allyUUID);
+            if (raid.alliedPlayers.add(allyUUID)) {
+                // Grant combat permissions to the new ally
+                IColony colony = getColonyById(colonyId);
+                if (colony != null) {
+                    grantBesiegeCombatPermissions(colony, allyUUID);
+                }
+            }
         }
     }
 
@@ -756,7 +790,30 @@ public class BesiegeManager {
         return OCCUPATIONS.get(colonyId);
     }
 
+    /**
+     * Backward-compatible view of active raids keyed by colonyId.
+     *
+     * Since multi-besieger support landed, the internal storage is keyed by
+     * besieger UUID. This view returns at most ONE raid per colony (the first
+     * one encountered). Callers that need ALL raids for a colony must use
+     * {@link #getRaidsForColony(int)}; callers that need a specific besieger's
+     * raid should use {@link #getRaidForBesieger(UUID)}.
+     */
     public static Map<Integer, BesiegeRaidData> getActiveRaids() {
+        Map<Integer, BesiegeRaidData> view = new HashMap<>();
+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
+            view.putIfAbsent(raid.colonyId, raid);
+        }
+        return Collections.unmodifiableMap(view);
+    }
+
+    /** Direct lookup by besieger UUID. Null when this player has no active raid. */
+    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
+        return ACTIVE_RAIDS.get(besiegerUUID);
+    }
+
+    /** Read-only view of all active raids keyed by besieger UUID. */
+    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
         return Collections.unmodifiableMap(ACTIVE_RAIDS);
     }
 
@@ -880,6 +937,67 @@ public class BesiegeManager {
     }
 
 
+    /**
+     * Grants the besieging player hostile rank and combat permissions on the target colony.
+     * Without this, MineColonies blocks all player attacks on citizens with
+     * "You do not have permission to do this in this colony!".
+     */
+    private static void grantBesiegeCombatPermissions(IColony colony, UUID playerUUID) {
+        if (!TaxConfig.ENABLE_WAR_ACTIONS.get()) return;
+        try {
+            IPermissions perms = colony.getPermissions();
+            // Snapshot before modifying (for restore on cleanup)
+            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.snapshotBefore(colony);
+
+            // Assign the player to Hostile rank so guards treat them as enemy
+            Rank hostile = perms.getRankHostile();
+            perms.setPlayerRank(playerUUID, hostile, colony.getWorld());
+
+            // Enable combat actions on the hostile rank
+            for (Action a : TaxConfig.getWarActions()) {
+                perms.setPermission(hostile, a, true);
+            }
+
+            if (TaxConfig.isDebugLogging())
+                LOGGER.debug("Granted besiege combat permissions to {} on colony {}",
+                        playerUUID, colony.getName());
+        } catch (Exception e) {
+            LOGGER.error("Failed to grant besiege combat permissions for {} on colony {}",
+                    playerUUID, colony.getName(), e);
+        }
+    }
+
+    /**
+     * Revokes combat permissions and demotes the player from hostile back to neutral.
+     * Called during raid cleanup.
+     */
+    private static void revokeBesiegeCombatPermissions(IColony colony, UUID playerUUID) {
+        if (!TaxConfig.ENABLE_WAR_ACTIONS.get()) return;
+        try {
+            IPermissions perms = colony.getPermissions();
+
+            // Disable combat actions on hostile rank
+            Rank hostile = perms.getRankHostile();
+            for (Action a : TaxConfig.getWarActions()) {
+                perms.setPermission(hostile, a, false);
+            }
+
+            // Demote player back to neutral (skip if they are the colony owner)
+            UUID owner = perms.getOwner();
+            if (!playerUUID.equals(owner)) {
+                Rank neutral = perms.getRankNeutral();
+                perms.setPlayerRank(playerUUID, neutral, colony.getWorld());
+            }
+
+            if (TaxConfig.isDebugLogging())
+                LOGGER.debug("Revoked besiege combat permissions from {} on colony {}",
+                        playerUUID, colony.getName());
+        } catch (Exception e) {
+            LOGGER.error("Failed to revoke besiege combat permissions for {} on colony {}",
+                    playerUUID, colony.getName(), e);
+        }
+    }
+
     private static void loadData(MinecraftServer server) {
         File f = new File(server.getServerDirectory(), STORAGE_FILE);
         if (!f.exists()) return;
@@ -900,16 +1018,20 @@ public class BesiegeManager {
 
     private static void saveData() {
         if (SERVER == null) return;
-        File f = new File(SERVER.getServerDirectory(), STORAGE_FILE);
-        try {
-            f.getParentFile().mkdirs();
-            try (FileWriter w = new FileWriter(f)) {
-                List<BesiegeOccupationData> list = new ArrayList<>(OCCUPATIONS.values());
-                GSON.toJson(list, w);
+        // Snapshot on the calling (server) thread.
+        final List<BesiegeOccupationData> list = new ArrayList<>(OCCUPATIONS.values());
+        final File f = new File(SERVER.getServerDirectory(), STORAGE_FILE);
+
+        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("besiege", () -> {
+            try {
+                f.getParentFile().mkdirs();
+                try (FileWriter w = new FileWriter(f)) {
+                    GSON.toJson(list, w);
+                }
+            } catch (Exception e) {
+                LOGGER.error("Failed to save besiege occupation data", e);
             }
-        } catch (Exception e) {
-            LOGGER.error("Failed to save besiege occupation data", e);
-        }
+        });
     }
 
 
```
