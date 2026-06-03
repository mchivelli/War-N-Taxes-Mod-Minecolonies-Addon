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
session id: 019e5cda-3ffb-74c1-8f67-d9a3b9e8056c
--------
user
Reviewing step 3 of an 11-step Java refactor (Minecraft 1.20.1 Forge mod, War 'N Taxes / MineColonies addon). Step 3 partially implements multi-besieger support by re-keying ACTIVE_RAIDS from colonyId to besiegerUUID and removing the primary-colony block + the double-besiege block. Phase 2 follow-ups are intentionally deferred (see bundle preamble). Review for: correctness of the re-keying (do all internal call sites consistently use the new key?), backward-compat correctness of getActiveRaids() view (does putIfAbsent semantics matter?), race conditions in the view construction, whether cleanupRaid still removes the correct entry, whether the iterator-based tick loop is still safe with the new key, and any caller of ACTIVE_RAIDS or BesiegeRaidData that the refactor may have missed. The Phase 2 deferrals (shared defender pool, last-kill-credit) are KNOWN gaps — please flag them but don't count them against the status. Respond: (1) STATUS APPROVE/REWORK/REJECT, (2) up to 5 bullet findings ranked by severity, (3) up to 3 file:line fix suggestions. Max 350 words.

<stdin>
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
</stdin>
codex
I’ll verify the refactor against the indexed call graph first, then cross-check the actual source where needed so the review isn’t based only on the diff bundle.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -First 1100 | % { $script:i=0 }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"ACTIVE_RAIDS|getActiveRaids\\(|getRaidsForColony\\(|getRaidForBesieger\\(|registerAlly\\(|BesiegeRaidData\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -First 1100 | % { $script:i=0 }'` rejected: blocked by policy
2026-05-25T01:58:09.396178Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -First 1100 | % { $script:i=0 }'` rejected: blocked by policy
 succeeded in 347ms:
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:62:     * raid the same colony â€” look them up by colony with {@link #getRaidsForColony(int)}.
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:69:    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:87:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:90:        ACTIVE_RAIDS.clear();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:95:        if (ACTIVE_RAIDS.isEmpty()) return;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:97:        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:98:            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:99:            BesiegeRaidData raid = entry.getValue();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:203:        if (ACTIVE_RAIDS.containsKey(besiegerUUID)) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:277:        if (ACTIVE_RAIDS.containsKey(playerUUID)) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:306:            BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:307:            ACTIVE_RAIDS.put(besiegerUUID, raid);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:358:            ACTIVE_RAIDS.remove(colonyId);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:364:    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:384:    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:421:    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:455:    private static void applyFortificationBonus(IColony colony, BesiegeRaidData raid) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:484:    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:526:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:543:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:589:    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:613:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:669:            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:674:    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:704:    private static void updateBossBar(BesiegeRaidData raid, IColony colony) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:738:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:745:    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:746:        List<BesiegeRaidData> matches = new ArrayList<>();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:747:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:770:    public static void registerAlly(int colonyId, UUID allyUUID) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:771:        BesiegeRaidData raid = ACTIVE_RAIDS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:799:     * {@link #getRaidsForColony(int)}; callers that need a specific besieger's
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:800:     * raid should use {@link #getRaidForBesieger(UUID)}.
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:802:    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:803:        Map<Integer, BesiegeRaidData> view = new HashMap<>();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:804:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:811:    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:812:        return ACTIVE_RAIDS.get(besiegerUUID);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:816:    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:817:        return Collections.unmodifiableMap(ACTIVE_RAIDS);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:825:    private static boolean allDefendersDead(BesiegeRaidData raid, IColony colony) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:829:    private static int countAliveDefenders(BesiegeRaidData raid, IColony colony) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1039:    public static class BesiegeRaidData {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1051:        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1309:                if (!RaidManager.getActiveRaids().isEmpty()) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1344:                if (!RaidManager.getActiveRaids().isEmpty()) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1434:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1446:                        for (BesiegeManager.BesiegeRaidData raid : raids.values()) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1487:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1490:                List<BesiegeManager.BesiegeRaidData> myRaids = raids.values().stream()
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1518:                        for (BesiegeManager.BesiegeRaidData raid : myRaids) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1794:                var activeRaids = raidManager.getActiveRaids();
src/main/java\net\machiavelli\minecolonytax\commands\WarCommands.java:115:        boolean colonyBeingRaided = RaidManager.getActiveRaids().values().stream()
src/main/java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:105:                        .getActiveRaids().isEmpty()) return true;
src/main/java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:107:                && !RaidManager.getActiveRaids().isEmpty()) return true;
src/main/java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:227:        if (TaxConfig.isBlockFilterRaidsEnabled() && !RaidManager.getActiveRaids().isEmpty()) {
src/main/java\net\machiavelli\minecolonytax\permissions\PermissionSnapshot.java:179:        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
src/main/java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:227:        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
src/main/java\net\machiavelli\minecolonytax\raid\RaidManager.java:581:    public static Map<UUID, ActiveRaidData> getActiveRaids() {
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:52:        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:115:                    BesiegeManager.BesiegeRaidData raid = entry.getValue();
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:98:            for (Map.Entry<UUID, ActiveRaidData> entry : RaidManager.getActiveRaids().entrySet()) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:296:        for (ActiveRaidData raidData : RaidManager.getActiveRaids().values()) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:423:        if (!RaidManager.getActiveRaids().isEmpty()
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:434:        if (!net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().isEmpty()) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:435:            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:436:                    : net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().values()) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
The first pass found two suspicious internal sites already: one removal still passes `colonyId`, and `registerAlly` still does a direct map lookup by colony id. I’m checking surrounding control flow and external callers now to rank whether those are fatal or compatibility-only issues.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 10 \"ACTIVE_RAIDS\\.remove|registerAlly\\(|getActiveRaids\\(\\)\\.get\\(|BesiegeManager\\.getActiveRaids\\(|getRaidsForColony\\(|getRaidForBesieger\\(\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"BesiegeManager\\.registerAlly|registerAlly\\(\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"getAllActiveRaidsByBesieger|getRaidForBesieger|getRaidsForColony|getActiveRaids\\(\\)\\.get\\(\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 318ms:
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-52- */
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-53-public class BesiegeManager {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-54-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-55-    private static final Logger LOGGER = LogManager.getLogger(BesiegeManager.class);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-56-    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-57-    private static final String STORAGE_FILE = "config/warntax/besieged_colonies.json";
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-58-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-59-    /**
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-60-     * Active besiege raids, keyed by besieger UUID. Each besieger has at most one
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-61-     * active raid at a time (solo combat rule). Multiple besiegers may concurrently
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:62:     * raid the same colony â€” look them up by colony with {@link #getRaidsForColony(int)}.
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-63-     *
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-64-     * Phase 2 follow-up: defender pool is still per-raid, not shared across
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-65-     * concurrent besiegers on the same colony. Each besieger currently spawns its
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-66-     * own mercs and tracks its own hostile-citizen set; last-kill-credit semantics
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-67-     * are not yet implemented.
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-68-     */
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-69-    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-70-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-71-    /** Persistent occupation records (colonyId -> occupation data). */
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-72-    private static final Map<Integer, BesiegeOccupationData> OCCUPATIONS = new ConcurrentHashMap<>();
--
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-348-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-349-            if (TaxConfig.isNormalLogging())
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-350-                LOGGER.info("Besiege {} started on colony {} ({}) by {} with {} total defenders ({}g {}m {}e)",
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-351-                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-352-                        besieger.getName().getString(), totalDefenders, guardCount, militiaCount, mercCount);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-353-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-354-            return true;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-355-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-356-        } catch (Exception e) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-357-            LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:358:            ACTIVE_RAIDS.remove(colonyId);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-359-            return false;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-360-        }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-361-    }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-362-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-363-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-364-    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-365-        int count = 0;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-366-        for (ICitizenData citizenData : colony.getCitizenManager().getCitizens()) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-367-            if (citizenData.getJob() == null || !citizenData.getJob().isGuard()) continue;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-368-            Optional<AbstractEntityCitizen> entityOpt = citizenData.getEntity();
--
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-659-        if (raid.bossEvent != null) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-660-            try {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-661-                raid.bossEvent.removeAllPlayers();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-662-            } catch (Exception e) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-663-                LOGGER.warn("Failed to remove besiege boss bar", e);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-664-            }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-665-            raid.bossEvent = null;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-666-        }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-667-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-668-        if (removeFromMap) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:669:            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-670-        }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-671-    }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-672-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-673-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-674-    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-675-        try {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-676-            int minutes = TaxConfig.getBesiegeDurationMinutes();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-677-            Component text = Component.literal(
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-678-                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-679-                            colony.getName(), totalDefenders, minutes, 0))
--
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-735-    }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-736-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-737-    public static boolean isActiveRaidOnColony(int colonyId) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-738-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-739-            if (raid.colonyId == colonyId) return true;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-740-        }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-741-        return false;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-742-    }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-743-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-744-    /** All currently active besiege raids targeting this colony. */
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:745:    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-746-        List<BesiegeRaidData> matches = new ArrayList<>();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-747-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-748-            if (raid.colonyId == colonyId) matches.add(raid);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-749-        }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-750-        return matches;
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-751-    }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-752-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-753-    /**
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-754-     * Returns true if the player is locked out of the colony due to besiege occupation.
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-755-     * The former owner is locked out; the besieging player is the new effective controller.
--
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-760-        // Former owner is locked out
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-761-        return occ.formerOwnerUUID != null && occ.formerOwnerUUID.equals(playerUUID);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-762-    }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-763-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-764-    public static boolean isBesiegingPlayer(UUID playerUUID, int colonyId) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-765-        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-766-        return occ != null && occ.besiegingPlayerUUID.equals(playerUUID);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-767-    }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-768-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-769-    /** Called from RaidKillTracker to register an allied player. */
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:770:    public static void registerAlly(int colonyId, UUID allyUUID) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-771-        BesiegeRaidData raid = ACTIVE_RAIDS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-772-        if (raid != null && TaxConfig.isBesiegeAlliesEnabled()) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-773-            if (raid.alliedPlayers.add(allyUUID)) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-774-                // Grant combat permissions to the new ally
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-775-                IColony colony = getColonyById(colonyId);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-776-                if (colony != null) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-777-                    grantBesiegeCombatPermissions(colony, allyUUID);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-778-                }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-779-            }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-780-        }
--
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-789-    public static BesiegeOccupationData getOccupation(int colonyId) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-790-        return OCCUPATIONS.get(colonyId);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-791-    }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-792-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-793-    /**
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-794-     * Backward-compatible view of active raids keyed by colonyId.
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-795-     *
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-796-     * Since multi-besieger support landed, the internal storage is keyed by
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-797-     * besieger UUID. This view returns at most ONE raid per colony (the first
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-798-     * one encountered). Callers that need ALL raids for a colony must use
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:799:     * {@link #getRaidsForColony(int)}; callers that need a specific besieger's
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:800:     * raid should use {@link #getRaidForBesieger(UUID)}.
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-801-     */
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-802-    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-803-        Map<Integer, BesiegeRaidData> view = new HashMap<>();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-804-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-805-            view.putIfAbsent(raid.colonyId, raid);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-806-        }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-807-        return Collections.unmodifiableMap(view);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-808-    }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-809-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-810-    /** Direct lookup by besieger UUID. Null when this player has no active raid. */
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:811:    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-812-        return ACTIVE_RAIDS.get(besiegerUUID);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-813-    }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-814-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-815-    /** Read-only view of all active raids keyed by besieger UUID. */
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-816-    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-817-        return Collections.unmodifiableMap(ACTIVE_RAIDS);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-818-    }
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-819-
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-820-    public static Map<Integer, BesiegeOccupationData> getAllOccupations() {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java-821-        return Collections.unmodifiableMap(OCCUPATIONS);
--
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1424-                }
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1425-                try {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1426-                        return showBesiegeStatusPlayer(src, src.getPlayerOrException());
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1427-                } catch (CommandSyntaxException e) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1428-                        src.sendFailure(Component.literal("You must be a player to use this command."));
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1429-                        return 0;
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1430-                }
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1431-        }
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1432-
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1433-        private static int showBesiegeStatusAdmin(CommandSourceStack src) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1434:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1435-                Map<Integer, BesiegeManager.BesiegeOccupationData> occupations = BesiegeManager.getAllOccupations();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1436-                IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1437-
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1438-                src.sendSuccess(() -> Component.literal("=== Besiege Status (Admin) ===")
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1439-                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1440-
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1441-                src.sendSuccess(() -> Component.literal("Active Raids (" + raids.size() + "):")
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1442-                                .withStyle(ChatFormatting.YELLOW), false);
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1443-                if (raids.isEmpty()) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1444-                        src.sendSuccess(() -> Component.literal("  None").withStyle(ChatFormatting.GRAY), false);
--
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1477-                }
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1478-
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1479-                src.sendSuccess(() -> Component.literal("==============================")
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1480-                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1481-                return 1;
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1482-        }
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1483-
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1484-        private static int showBesiegeStatusPlayer(CommandSourceStack src, ServerPlayer player) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1485-                UUID playerUUID = player.getUUID();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1486-                IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1487:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1488-                Map<Integer, BesiegeManager.BesiegeOccupationData> occupations = BesiegeManager.getAllOccupations();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1489-
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1490-                List<BesiegeManager.BesiegeRaidData> myRaids = raids.values().stream()
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1491-                                .filter(r -> {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1492-                                        if (r.besiegingPlayerUUID.equals(playerUUID)) return true;
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1493-                                        return cm.getAllColonies().stream()
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1494-                                                        .filter(c -> c.getID() == r.colonyId)
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1495-                                                        .anyMatch(c -> {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1496-                                                                var rank = c.getPermissions().getRank(playerUUID);
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java-1497-                                                                return rank != null && rank.isColonyManager();
--
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-165-                        entries.add(new EventLogEntry(
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-166-                                "active_war_" + colonyId,
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-167-                                role + opponent,
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-168-                                isDefender ? "Under attack â€” tax claiming disabled" : "War ongoing â€” treasury draining",
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-169-                                "Active",
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-170-                                0xFFA03030, true, -1));
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-171-                    }
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-172-
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-173-                    if (TaxConfig.isBesiegeSystemEnabled()
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-174-                            && BesiegeManager.isColonyBesieged(colonyId)) {
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-176-                        String besiegerName = getPlayerDisplayName(brd != null ? brd.besiegingPlayerUUID : null, player);
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-177-                        entries.add(new EventLogEntry(
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-178-                                "active_besiege_" + colonyId,
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-179-                                "Under Siege by " + besiegerName,
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-180-                                "Colony besieged â€” tax claiming disabled",
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-181-                                "Besieged",
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-182-                                0xFFA03030, true, -1));
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-183-                    }
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-184-
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java-185-                    if (TaxConfig.isOccupationSystemEnabled()) {
--
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-102-                sendTitleNotification(player, "AT WAR!", defName + " vs " + atkName, "red");
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-103-                notifiedWars.add(warKey);
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-104-            } catch (Exception ignored) {}
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-105-        }
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-106-
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-107-        // --- Besiege notifications ---
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-108-        if (TaxConfig.isBesiegeSystemEnabled()) {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-109-            notifiedBesiegesByPlayer.computeIfAbsent(playerUUID, k -> new HashSet<>());
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-110-            Set<Integer> notifiedBesieges = notifiedBesiegesByPlayer.get(playerUUID);
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-111-
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-113-                try {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-114-                    int colonyId = entry.getKey();
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-115-                    BesiegeManager.BesiegeRaidData raid = entry.getValue();
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-116-
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-117-                    IColony besiegedColony = com.minecolonies.api.IMinecoloniesAPI.getInstance()
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-118-                            .getColonyManager().getAllColonies().stream()
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-119-                            .filter(c -> c.getID() == colonyId).findFirst().orElse(null);
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-120-                    if (besiegedColony == null) continue;
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-121-
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java-122-                    if (!isPlayerOfficerOrOwner(player, besiegedColony.getPermissions())) continue;
--
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-424-                && RaidManager.getActiveRaidForPlayer(playerId) != null) return true;
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-425-        // isEmpty guards avoid creating an iterator on empty maps
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-426-        if (!WarSystem.ACTIVE_WARS.isEmpty()) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-427-            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-428-                if ((war.getAttackerLives() != null && war.getAttackerLives().containsKey(playerId))
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-429-                        || (war.getDefenderLives() != null && war.getDefenderLives().containsKey(playerId))) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-430-                    return true;
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-431-                }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-432-            }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-433-        }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:434:        if (!net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().isEmpty()) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-435-            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:436:                    : net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().values()) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-437-                if (besiege.besiegingPlayerUUID != null && besiege.besiegingPlayerUUID.equals(playerId)) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-438-                    return true;
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-439-                }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-440-            }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-441-        }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-442-        return false;
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-443-    }
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-444-
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java-446-        for (UUID uuid : war.getAttackerLives().keySet()) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 326ms:
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:770:    public static void registerAlly(int colonyId, UUID allyUUID) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 325ms:
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:62:     * raid the same colony â€” look them up by colony with {@link #getRaidsForColony(int)}.
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:745:    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:799:     * {@link #getRaidsForColony(int)}; callers that need a specific besieger's
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:800:     * raid should use {@link #getRaidForBesieger(UUID)}.
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:811:    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:816:    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 65 \"public static void tick\\(\\)\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 35 "class BesiegeRaidData|hostileCitizenIds|alliedPlayers" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 70 "private static boolean startRaidInternal|BesiegeRaidData raid = new BesiegeRaidData" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 402ms:
29-import net.minecraft.world.effect.MobEffects;
30-import net.minecraft.world.entity.Entity;
31-import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
32-import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
33-import net.minecraft.world.entity.player.Player;
34-import net.minecraft.world.item.ItemStack;
35-import net.minecraft.world.item.Items;
36-import net.minecraft.world.entity.EquipmentSlot;
37-import net.minecraft.world.level.Level;
38-import net.minecraft.world.level.levelgen.Heightmap;
39-import org.apache.logging.log4j.LogManager;
40-import org.apache.logging.log4j.Logger;
41-
42-import java.io.File;
43-import java.io.FileReader;
44-import java.io.FileWriter;
45-import java.lang.reflect.Type;
46-import java.util.*;
47-import java.util.concurrent.ConcurrentHashMap;
48-
49-/**
50- * Manages the besiege system â€” single-player raids on active non-primary colonies
51- * that grant tax vassalage on victory, and the reclaim flow for the former owner.
52- */
53-public class BesiegeManager {
54-
55-    private static final Logger LOGGER = LogManager.getLogger(BesiegeManager.class);
56-    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
57-    private static final String STORAGE_FILE = "config/warntax/besieged_colonies.json";
58-
59-    /**
60-     * Active besiege raids, keyed by besieger UUID. Each besieger has at most one
61-     * active raid at a time (solo combat rule). Multiple besiegers may concurrently
62-     * raid the same colony â€” look them up by colony with {@link #getRaidsForColony(int)}.
63-     *
64-     * Phase 2 follow-up: defender pool is still per-raid, not shared across
65-     * concurrent besiegers on the same colony. Each besieger currently spawns its
66-     * own mercs and tracks its own hostile-citizen set; last-kill-credit semantics
67-     * are not yet implemented.
68-     */
69-    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
70-
71-    /** Persistent occupation records (colonyId -> occupation data). */
72-    private static final Map<Integer, BesiegeOccupationData> OCCUPATIONS = new ConcurrentHashMap<>();
73-
74-    /** Per-player cooldown map (playerUUID -> timestamp when cooldown expires). */
75-    private static final Map<UUID, Long> PLAYER_COOLDOWNS = new ConcurrentHashMap<>();
76-
77-    private static MinecraftServer SERVER;
78-
79-    public static void initialize(MinecraftServer server) {
80-        SERVER = server;
81-        loadData(server);
82-        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager initialized");
83-    }
84-
85-    public static void shutdown() {
86-        saveData();
87-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
88-            cleanupRaid(raid, false);
89-        }
90-        ACTIVE_RAIDS.clear();
91-        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager shutdown complete");
92-    }
93-
94:    public static void tick() {
95-        if (ACTIVE_RAIDS.isEmpty()) return;
96-
97-        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
98-            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
99-            BesiegeRaidData raid = entry.getValue();
100-
101-            try {
102-                IColony colony = getColonyById(raid.colonyId);
103-                if (colony == null) {
104-                    LOGGER.warn("BesiegeManager.tick: colony {} not found, ending raid", raid.colonyId);
105-                    cleanupRaid(raid, false);
106-                    it.remove();
107-                    continue;
108-                }
109-
110-                ServerPlayer besieger = SERVER.getPlayerList().getPlayer(raid.besiegingPlayerUUID);
111-
112-                // --- Timer expired: defenders win ---
113-                if (System.currentTimeMillis() >= raid.endTime) {
114-                    if (TaxConfig.isNormalLogging())
115-                        LOGGER.info("Besiege raid on colony {} timed out â€” defenders win", colony.getName());
116-                    sendToPlayer(raid.besiegingPlayerUUID,
117-                            Component.literal("The besiege of " + colony.getName() + " has failed â€” the defenders held out!")
118-                                    .withStyle(ChatFormatting.RED));
119-                    broadcastToNearbyPlayers(colony,
120-                            Component.literal(colony.getName() + " successfully repelled the besiege!")
121-                                    .withStyle(ChatFormatting.GREEN), 200);
122-                    cleanupRaid(raid, false);
123-                    applyCooldown(raid.besiegingPlayerUUID);
124-                    it.remove();
125-                    continue;
126-                }
127-
128-                // --- Besieger left the area ---
129-                if (besieger != null) {
130-                    BlockPos center = colony.getCenter();
131-                    double dist = besieger.distanceToSqr(center.getX(), center.getY(), center.getZ());
132-                    int maxRadius = TaxConfig.getBesiegePlayerStayRadius();
133-                    if (dist > (double) maxRadius * maxRadius) {
134-                        besieger.sendSystemMessage(Component.literal(
135-                                "You left the besiege area â€” the raid has been cancelled!")
136-                                .withStyle(ChatFormatting.RED));
137-                        cleanupRaid(raid, false);
138-                        applyCooldown(raid.besiegingPlayerUUID);
139-                        it.remove();
140-                        continue;
141-                    }
142-
143-                    // Track allies: anyone who recently damaged a defender
144-                    // (ally tracking is done in the kill/hurt event â€” see RaidKillTracker integration)
145-                }
146-
147-                // --- Victory: all defenders dead ---
148-                if (allDefendersDead(raid, colony)) {
149-                    if (TaxConfig.isNormalLogging())
150-                        LOGGER.info("Besiege raid on colony {} successful â€” besieger wins", colony.getName());
151-                    completeBesiege(raid, true, colony);
152-                    it.remove();
153-                    continue;
154-                }
155-
156-                // --- Update boss bar ---
157-                updateBossBar(raid, colony);
158-
159-            } catch (Exception e) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 382ms:
340-                            + " by " + besieger.getName().getString() + "!")
341-                            .withStyle(ChatFormatting.YELLOW), 200);
342-
343-            // Also notify owner + officers directly regardless of distance
344-            notifyColonyOwnersAndOfficers(colony,
345-                    Component.literal("WARNING: " + besieger.getName().getString()
346-                            + " is besieging your colony " + colony.getName() + "!")
347-                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
348-
349-            if (TaxConfig.isNormalLogging())
350-                LOGGER.info("Besiege {} started on colony {} ({}) by {} with {} total defenders ({}g {}m {}e)",
351-                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
352-                        besieger.getName().getString(), totalDefenders, guardCount, militiaCount, mercCount);
353-
354-            return true;
355-
356-        } catch (Exception e) {
357-            LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
358-            ACTIVE_RAIDS.remove(colonyId);
359-            return false;
360-        }
361-    }
362-
363-
364-    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
365-        int count = 0;
366-        for (ICitizenData citizenData : colony.getCitizenManager().getCitizens()) {
367-            if (citizenData.getJob() == null || !citizenData.getJob().isGuard()) continue;
368-            Optional<AbstractEntityCitizen> entityOpt = citizenData.getEntity();
369-            if (entityOpt.isEmpty()) continue;
370-            AbstractEntityCitizen guard = entityOpt.get();
371-
372-            try {
373-                applyDefenderAI(guard, besieger, raid);
374-                applyDefenderEffects(guard, TaxConfig.getBesiegeDurationMinutes());
375:                raid.hostileCitizenIds.add(citizenData.getId());
376-                count++;
377-            } catch (Exception e) {
378-                LOGGER.warn("Failed to make guard {} hostile during besiege", citizenData.getName(), e);
379-            }
380-        }
381-        return count;
382-    }
383-
384-    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
385-        List<ICitizenData> eligible = new ArrayList<>();
386-        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
387-            if (citizen.isChild()) continue;
388-            if (citizen.getEntity().isEmpty()) continue;
389-            if (citizen.getJob() != null && citizen.getJob().isGuard()) continue;
390-            if (citizen.getJob() != null) {
391-                String jobPath = citizen.getJob().getJobRegistryEntry().getKey().getPath();
392-                if (jobPath.equals("deliveryman")) continue;
393-            }
394-            eligible.add(citizen);
395-        }
396-
397-        Collections.shuffle(eligible);
398-        int target = (int) Math.ceil(eligible.size() * TaxConfig.getBesiegeMilitiaPercent());
399-        int count = 0;
400-
401-        for (ICitizenData citizen : eligible) {
402-            if (count >= target) break;
403-            Optional<AbstractEntityCitizen> entityOpt = citizen.getEntity();
404-            if (entityOpt.isEmpty()) continue;
405-            AbstractEntityCitizen entity = entityOpt.get();
406-
407-            try {
408-                // Equip wooden sword
409-                entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
410-                applyDefenderAI(entity, besieger, raid);
411-                applyDefenderEffects(entity, TaxConfig.getBesiegeDurationMinutes());
412:                raid.hostileCitizenIds.add(citizen.getId());
413-                count++;
414-            } catch (Exception e) {
415-                LOGGER.warn("Failed to convert citizen {} to militia during besiege", citizen.getName(), e);
416-            }
417-        }
418-        return count;
419-    }
420-
421-    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
422-        entity.goalSelector.removeAllGoals(g -> true);
423-        entity.targetSelector.removeAllGoals(g -> true);
424-
425-        // Use MilitiaAttackGoal instead of vanilla MeleeAttackGoal â€” non-guard citizens
426-        // lack the ATTACK_DAMAGE attribute, which causes MeleeAttackGoal.doHurtTarget() to
427-        // crash with IllegalArgumentException.
428-        entity.goalSelector.addGoal(0, new MilitiaAttackGoal(entity, 1.2D));
429-
430-        // Retaliate against anyone who hits them (covers allies)
431-        entity.targetSelector.addGoal(0, new HurtByTargetGoal(entity));
432-
433-        // Proactively hunt the besieger (and any allies)
434-        entity.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(entity, Player.class,
435-                20, true, false, (target) -> {
436-                    if (!(target instanceof ServerPlayer sp)) return false;
437-                    return sp.getUUID().equals(besieger.getUUID())
438:                            || raid.alliedPlayers.contains(sp.getUUID());
439-                }));
440-    }
441-
442-    private static void applyDefenderEffects(AbstractEntityCitizen entity, int durationMinutes) {
443-        int ticks = durationMinutes * 60 * 20;
444-        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1));
445-        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
446-        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
447-    }
448-
449-    /**
450-     * Applies bonus DAMAGE_RESISTANCE to all besiege defenders based on the
451-     * colony's FORTIFICATION investment level. Called once after all defenders
452-     * are spawned in launchRaid().
453-     * Each 20% damage reduction from the investment adds +1 resistance amplifier.
454-     */
455-    private static void applyFortificationBonus(IColony colony, BesiegeRaidData raid) {
456-        if (!TaxConfig.isUpgradesEnabled()) return;
457-        double dmgReduction = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
458-                .getFortificationDamageReduction(colony.getID());
459-        if (dmgReduction <= 0) return;
460-        int extraAmplifier = (int) (dmgReduction / 0.20);
461-        if (extraAmplifier <= 0) return;
462-
463-        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
464-        // Re-apply DAMAGE_RESISTANCE to citizens with the boosted amplifier
465:        for (int citizenId : raid.hostileCitizenIds) {
466-            colony.getCitizenManager().getCitizens().stream()
467-                    .filter(c -> c.getId() == citizenId)
468-                    .findFirst()
469-                    .flatMap(ICitizenData::getEntity)
470-                    .ifPresent(entity -> entity.addEffect(
471-                            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier)));
472-        }
473-        // Also boost mercenaries
474-        for (Entity merc : raid.spawnedMercenaries) {
475-            if (merc instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
476-                living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier));
477-            }
478-        }
479-        if (TaxConfig.isDebugLogging())
480-            LOGGER.debug("FORTIFICATION bonus applied to colony {} defenders (extra resistance amplifier: {})",
481-                    colony.getName(), extraAmplifier);
482-    }
483-
484-    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
485-        Level world = colony.getWorld();
486-        if (!(world instanceof ServerLevel)) return 0;
487-
488-        int buildingCount = 0;
489-        try {
490-            buildingCount = net.machiavelli.minecolonytax.compat.ColonyBuildingUtil
491-                    .getBuildings(colony).size();
492-        } catch (Exception e) {
493-            LOGGER.warn("Could not count buildings for mercenary calc in colony {}", colony.getName());
494-        }
495-
496-        int count = (int) (buildingCount * TaxConfig.getBesiegeExtraMercenariesPerBuilding());
497-        count = Math.min(count, TaxConfig.getBesiegeMaxMercenaries());
498-        if (count <= 0) return 0;
499-
500-        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
--
583-            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
584-                    "Besiege raid completed but vassalization could not be established (colony may already be a vassal).")
585-                    .withStyle(ChatFormatting.YELLOW));
586-        }
587-    }
588-
589-    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
590-        int colonyId = colony.getID();
591-
592-        // Remove vassalization
593-        VassalManager.removeVassalRelation(colonyId);
594-
595-        // Remove occupation record
596-        OCCUPATIONS.remove(colonyId);
597-        saveData();
598-
599-        sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
600-                "Reclaim successful! " + colony.getName() + " is free from occupation.")
601-                .withStyle(ChatFormatting.GREEN));
602-
603-        broadcastToNearbyPlayers(colony,
604-                Component.literal(colony.getName() + " has been reclaimed by its owner!")
605-                        .withStyle(ChatFormatting.GOLD), 300);
606-
607-        if (TaxConfig.isNormalLogging())
608-            LOGGER.info("Besiege reclaim: colony {} reclaimed by {}", colony.getName(),
609-                    getPlayerName(raid.besiegingPlayerUUID));
610-    }
611-
612-
613-    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
614-        IColony colony = getColonyById(raid.colonyId);
615-        if (colony != null) {
616-            // Revoke combat permissions from the besieger (and any allies)
617-            revokeBesiegeCombatPermissions(colony, raid.besiegingPlayerUUID);
618:            for (UUID ally : raid.alliedPlayers) {
619-                revokeBesiegeCombatPermissions(colony, ally);
620-            }
621-
622-            // Restore citizen AI
623:            for (int citizenId : raid.hostileCitizenIds) {
624-                try {
625-                    ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
626-                    if (citizen != null && citizen.getEntity().isPresent()) {
627-                        AbstractEntityCitizen entity = citizen.getEntity().get();
628-                        entity.goalSelector.removeAllGoals(g -> true);
629-                        entity.targetSelector.removeAllGoals(g -> true);
630-                        // Remove militia sword if present
631-                        if (entity.getMainHandItem().getItem() == Items.WOODEN_SWORD) {
632-                            entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
633-                        }
634-                        // Remove combat effects
635-                        entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
636-                        entity.removeEffect(MobEffects.MOVEMENT_SPEED);
637-                        entity.removeEffect(MobEffects.DAMAGE_BOOST);
638-                        // Restore job AI
639-                        if (citizen.getJob() != null) {
640-                            entity.getCitizenJobHandler().onJobChanged(citizen.getJob());
641-                        }
642-                    }
643-                } catch (Exception e) {
644-                    LOGGER.warn("Failed to restore citizen {} AI after besiege", citizenId, e);
645-                }
646-            }
647-
648-            // Despawn mercenaries
649-            for (Entity merc : raid.spawnedMercenaries) {
650-                try {
651-                    if (merc.isAlive()) merc.remove(Entity.RemovalReason.DISCARDED);
652-                } catch (Exception e) {
653-                    LOGGER.warn("Failed to despawn mercenary after besiege", e);
654-                }
655-            }
656-        }
657-
658-        // Remove boss bar
--
738-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
739-            if (raid.colonyId == colonyId) return true;
740-        }
741-        return false;
742-    }
743-
744-    /** All currently active besiege raids targeting this colony. */
745-    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
746-        List<BesiegeRaidData> matches = new ArrayList<>();
747-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
748-            if (raid.colonyId == colonyId) matches.add(raid);
749-        }
750-        return matches;
751-    }
752-
753-    /**
754-     * Returns true if the player is locked out of the colony due to besiege occupation.
755-     * The former owner is locked out; the besieging player is the new effective controller.
756-     */
757-    public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
758-        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
759-        if (occ == null) return false;
760-        // Former owner is locked out
761-        return occ.formerOwnerUUID != null && occ.formerOwnerUUID.equals(playerUUID);
762-    }
763-
764-    public static boolean isBesiegingPlayer(UUID playerUUID, int colonyId) {
765-        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
766-        return occ != null && occ.besiegingPlayerUUID.equals(playerUUID);
767-    }
768-
769-    /** Called from RaidKillTracker to register an allied player. */
770-    public static void registerAlly(int colonyId, UUID allyUUID) {
771-        BesiegeRaidData raid = ACTIVE_RAIDS.get(colonyId);
772-        if (raid != null && TaxConfig.isBesiegeAlliesEnabled()) {
773:            if (raid.alliedPlayers.add(allyUUID)) {
774-                // Grant combat permissions to the new ally
775-                IColony colony = getColonyById(colonyId);
776-                if (colony != null) {
777-                    grantBesiegeCombatPermissions(colony, allyUUID);
778-                }
779-            }
780-        }
781-    }
782-
783-    /** Check whether a player is on cooldown. */
784-    public static boolean isOnCooldown(UUID playerUUID) {
785-        Long expiry = PLAYER_COOLDOWNS.get(playerUUID);
786-        return expiry != null && System.currentTimeMillis() < expiry;
787-    }
788-
789-    public static BesiegeOccupationData getOccupation(int colonyId) {
790-        return OCCUPATIONS.get(colonyId);
791-    }
792-
793-    /**
794-     * Backward-compatible view of active raids keyed by colonyId.
795-     *
796-     * Since multi-besieger support landed, the internal storage is keyed by
797-     * besieger UUID. This view returns at most ONE raid per colony (the first
798-     * one encountered). Callers that need ALL raids for a colony must use
799-     * {@link #getRaidsForColony(int)}; callers that need a specific besieger's
800-     * raid should use {@link #getRaidForBesieger(UUID)}.
801-     */
802-    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
803-        Map<Integer, BesiegeRaidData> view = new HashMap<>();
804-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
805-            view.putIfAbsent(raid.colonyId, raid);
806-        }
807-        return Collections.unmodifiableMap(view);
808-    }
809-
810-    /** Direct lookup by besieger UUID. Null when this player has no active raid. */
811-    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
812-        return ACTIVE_RAIDS.get(besiegerUUID);
813-    }
814-
815-    /** Read-only view of all active raids keyed by besieger UUID. */
816-    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
817-        return Collections.unmodifiableMap(ACTIVE_RAIDS);
818-    }
819-
820-    public static Map<Integer, BesiegeOccupationData> getAllOccupations() {
821-        return Collections.unmodifiableMap(OCCUPATIONS);
822-    }
823-
824-
825-    private static boolean allDefendersDead(BesiegeRaidData raid, IColony colony) {
826-        return countAliveDefenders(raid, colony) == 0;
827-    }
828-
829-    private static int countAliveDefenders(BesiegeRaidData raid, IColony colony) {
830-        int alive = 0;
831-        // Citizens
832:        for (int citizenId : raid.hostileCitizenIds) {
833-            try {
834-                ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
835-                if (citizen != null && citizen.getEntity().isPresent()
836-                        && citizen.getEntity().get().isAlive()) {
837-                    alive++;
838-                }
839-            } catch (Exception ignored) {}
840-        }
841-        // Mercenaries
842-        for (Entity merc : raid.spawnedMercenaries) {
843-            if (merc.isAlive()) alive++;
844-        }
845-        return alive;
846-    }
847-
848-    private static void applyCooldown(UUID playerUUID) {
849-        long cooldownMs = TaxConfig.getBesiegeCooldownHours() * 3600_000L;
850-        PLAYER_COOLDOWNS.put(playerUUID, System.currentTimeMillis() + cooldownMs);
851-    }
852-
853-    private static BlockPos findSpawnPos(BlockPos center, Level world) {
854-        Random rng = new Random();
855-        for (int attempt = 0; attempt < 10; attempt++) {
856-            int x = center.getX() + rng.nextInt(20) - 10;
857-            int z = center.getZ() + rng.nextInt(20) - 10;
858-            BlockPos surface = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
859-            if (world.getBlockState(surface).isAir() && !world.getBlockState(surface.below()).isAir()) {
860-                return surface;
861-            }
862-        }
863-        return center;
864-    }
865-
866-    private static void broadcastToNearbyPlayers(IColony colony, Component message, int radius) {
867-        Level world = colony.getWorld();
--
1004-        try (FileReader r = new FileReader(f)) {
1005-            Type type = new TypeToken<List<BesiegeOccupationData>>() {}.getType();
1006-            List<BesiegeOccupationData> list = GSON.fromJson(r, type);
1007-            if (list != null) {
1008-                for (BesiegeOccupationData occ : list) {
1009-                    OCCUPATIONS.put(occ.colonyId, occ);
1010-                }
1011-            }
1012-            if (TaxConfig.isNormalLogging())
1013-                LOGGER.info("Loaded {} besiege occupation record(s)", OCCUPATIONS.size());
1014-        } catch (Exception e) {
1015-            LOGGER.error("Failed to load besiege occupation data", e);
1016-        }
1017-    }
1018-
1019-    private static void saveData() {
1020-        if (SERVER == null) return;
1021-        // Snapshot on the calling (server) thread.
1022-        final List<BesiegeOccupationData> list = new ArrayList<>(OCCUPATIONS.values());
1023-        final File f = new File(SERVER.getServerDirectory(), STORAGE_FILE);
1024-
1025-        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("besiege", () -> {
1026-            try {
1027-                f.getParentFile().mkdirs();
1028-                try (FileWriter w = new FileWriter(f)) {
1029-                    GSON.toJson(list, w);
1030-                }
1031-            } catch (Exception e) {
1032-                LOGGER.error("Failed to save besiege occupation data", e);
1033-            }
1034-        });
1035-    }
1036-
1037-
1038-    /** Transient raid state â€” not persisted. */
1039:    public static class BesiegeRaidData {
1040-        public final int colonyId;
1041-        public final UUID besiegingPlayerUUID;
1042-        public final long startTime;
1043-        public final long endTime;
1044:        public final Set<Integer> hostileCitizenIds = ConcurrentHashMap.newKeySet();
1045-        public final Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
1046:        public final Set<UUID> alliedPlayers = ConcurrentHashMap.newKeySet();
1047-        public final BlockPos colonyCenter;
1048-        public ServerBossEvent bossEvent;
1049-        public final boolean isReclaim;
1050-
1051-        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
1052-            this.colonyId = colonyId;
1053-            this.besiegingPlayerUUID = besiegingPlayerUUID;
1054-            this.colonyCenter = colonyCenter;
1055-            this.isReclaim = isReclaim;
1056-            this.startTime = System.currentTimeMillis();
1057-            this.endTime = startTime + (long) TaxConfig.getBesiegeDurationMinutes() * 60_000L;
1058-        }
1059-    }
1060-
1061-    /** Persisted occupation record â€” survives server restart. */
1062-    public static class BesiegeOccupationData {
1063-        public int colonyId;
1064-        public String colonyName;
1065-        public UUID besiegingPlayerUUID;
1066-        public UUID formerOwnerUUID;
1067-        public long besiegeTime;
1068-        public int tributePercent;
1069-
1070-        // For Gson deserialization
1071-        public BesiegeOccupationData() {}
1072-
1073-        public BesiegeOccupationData(int colonyId, String colonyName, UUID besiegingPlayerUUID,
1074-                UUID formerOwnerUUID, long besiegeTime, int tributePercent) {
1075-            this.colonyId = colonyId;
1076-            this.colonyName = colonyName;
1077-            this.besiegingPlayerUUID = besiegingPlayerUUID;
1078-            this.formerOwnerUUID = formerOwnerUUID;
1079-            this.besiegeTime = besiegeTime;
1080-            this.tributePercent = tributePercent;
1081-        }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 387ms:
236-        }
237-
238-        // 8. Cannot besiege a colony that is already a vassal of this player
239-        if (VassalManager.isColonyVassal(colonyId)
240-                && VassalManager.getVassalOverlordUUID(colonyId) != null
241-                && VassalManager.getVassalOverlordUUID(colonyId).equals(besiegerUUID)) {
242-            besieger.sendSystemMessage(Component.literal("This colony is already your vassal.")
243-                    .withStyle(ChatFormatting.RED));
244-            return false;
245-        }
246-
247-        return launchRaid(colony, besieger, false);
248-    }
249-
250-    /**
251-     * Start a reclaim raid â€” former owner/officer taking back their besieged colony.
252-     */
253-    public static boolean startReclaim(IColony colony, ServerPlayer reclaimingPlayer) {
254-        if (!TaxConfig.isBesiegeSystemEnabled()) {
255-            reclaimingPlayer.sendSystemMessage(Component.literal("The besiege system is disabled.")
256-                    .withStyle(ChatFormatting.RED));
257-            return false;
258-        }
259-
260-        int colonyId = colony.getID();
261-        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
262-        if (occ == null) {
263-            reclaimingPlayer.sendSystemMessage(Component.literal("This colony is not under besiege occupation.")
264-                    .withStyle(ChatFormatting.RED));
265-            return false;
266-        }
267-
268-        UUID playerUUID = reclaimingPlayer.getUUID();
269-        if (!occ.formerOwnerUUID.equals(playerUUID)) {
270-            reclaimingPlayer.sendSystemMessage(Component.literal(
271-                    "Only the former owner can reclaim this colony via besiege.")
272-                    .withStyle(ChatFormatting.RED));
273-            return false;
274-        }
275-
276-        // Solo rule: this player may not already have an active raid.
277-        if (ACTIVE_RAIDS.containsKey(playerUUID)) {
278-            reclaimingPlayer.sendSystemMessage(Component.literal(
279-                    "You already have an active besiege/reclaim raid.")
280-                    .withStyle(ChatFormatting.RED));
281-            return false;
282-        }
283-
284-        // Cooldown check
285-        Long cooldownExpiry = PLAYER_COOLDOWNS.get(playerUUID);
286-        if (cooldownExpiry != null && System.currentTimeMillis() < cooldownExpiry) {
287-            long remaining = (cooldownExpiry - System.currentTimeMillis()) / 60000;
288-            reclaimingPlayer.sendSystemMessage(Component.literal(
289-                    "You must wait " + remaining + " more minute(s) before attempting a reclaim.")
290-                    .withStyle(ChatFormatting.RED));
291-            return false;
292-        }
293-
294-        reclaimingPlayer.sendSystemMessage(Component.literal(
295-                "Reclaim raid started! Kill all defenders to reclaim " + colony.getName() + ".")
296-                .withStyle(ChatFormatting.GOLD));
297-        return launchRaid(colony, reclaimingPlayer, true);
298-    }
299-
300-
301-    private static boolean launchRaid(IColony colony, ServerPlayer besieger, boolean isReclaim) {
302-        int colonyId = colony.getID();
303-        UUID besiegerUUID = besieger.getUUID();
304-
305-        try {
306:            BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
307-            ACTIVE_RAIDS.put(besiegerUUID, raid);
308-
309-            // Grant the besieger hostile rank + combat permissions on the colony
310-            // so MineColonies allows the player to attack citizens.
311-            grantBesiegeCombatPermissions(colony, besiegerUUID);
312-
313-            // Convert guards to hostile
314-            int guardCount = makeGuardsHostile(colony, besieger, raid);
315-
316-            // Convert militia (non-guard eligible citizens)
317-            int militiaCount = convertCitizensToMilitia(colony, besieger, raid);
318-
319-            // Spawn mercenaries
320-            int mercCount = spawnMercenaries(colony, besieger, raid);
321-
322-            int totalDefenders = guardCount + militiaCount + mercCount;
323-
324-            // Apply FORTIFICATION investment: extra DAMAGE_RESISTANCE to all defenders
325-            applyFortificationBonus(colony, raid);
326-
327-            // Create boss bar
328-            createBossBar(raid, besieger, colony, totalDefenders);
329-
330-            String verb = isReclaim ? "RECLAIM RAID" : "BESIEGE";
331-            besieger.sendSystemMessage(Component.literal(
332-                    verb + " STARTED: " + colony.getName()
333-                            + " | Defenders: " + totalDefenders
334-                            + " | Time: " + TaxConfig.getBesiegeDurationMinutes() + "m")
335-                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
336-
337-            broadcastToNearbyPlayers(colony,
338-                    Component.literal("Nearby colony " + colony.getName()
339-                            + " is under " + (isReclaim ? "reclaim attack" : "siege")
340-                            + " by " + besieger.getName().getString() + "!")
341-                            .withStyle(ChatFormatting.YELLOW), 200);
342-
343-            // Also notify owner + officers directly regardless of distance
344-            notifyColonyOwnersAndOfficers(colony,
345-                    Component.literal("WARNING: " + besieger.getName().getString()
346-                            + " is besieging your colony " + colony.getName() + "!")
347-                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
348-
349-            if (TaxConfig.isNormalLogging())
350-                LOGGER.info("Besiege {} started on colony {} ({}) by {} with {} total defenders ({}g {}m {}e)",
351-                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
352-                        besieger.getName().getString(), totalDefenders, guardCount, militiaCount, mercCount);
353-
354-            return true;
355-
356-        } catch (Exception e) {
357-            LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
358-            ACTIVE_RAIDS.remove(colonyId);
359-            return false;
360-        }
361-    }
362-
363-
364-    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
365-        int count = 0;
366-        for (ICitizenData citizenData : colony.getCitizenManager().getCitizens()) {
367-            if (citizenData.getJob() == null || !citizenData.getJob().isGuard()) continue;
368-            Optional<AbstractEntityCitizen> entityOpt = citizenData.getEntity();
369-            if (entityOpt.isEmpty()) continue;
370-            AbstractEntityCitizen guard = entityOpt.get();
371-
372-            try {
373-                applyDefenderAI(guard, besieger, raid);
374-                applyDefenderEffects(guard, TaxConfig.getBesiegeDurationMinutes());
375-                raid.hostileCitizenIds.add(citizenData.getId());
376-                count++;
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 20 \"catch \\(Exception e\\) \\{\\s*"'$" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 296ms:
139-                        it.remove();
140-                        continue;
141-                    }
142-
143-                    // Track allies: anyone who recently damaged a defender
144-                    // (ally tracking is done in the kill/hurt event â€” see RaidKillTracker integration)
145-                }
146-
147-                // --- Victory: all defenders dead ---
148-                if (allDefendersDead(raid, colony)) {
149-                    if (TaxConfig.isNormalLogging())
150-                        LOGGER.info("Besiege raid on colony {} successful â€” besieger wins", colony.getName());
151-                    completeBesiege(raid, true, colony);
152-                    it.remove();
153-                    continue;
154-                }
155-
156-                // --- Update boss bar ---
157-                updateBossBar(raid, colony);
158-
159:            } catch (Exception e) {
160-                LOGGER.error("Error ticking besiege raid for colony {}", entry.getKey(), e);
161-            }
162-        }
163-    }
164-
165-
166-    /**
167-     * Start a besiege raid. Validates all preconditions.
168-     * Returns true if the raid started successfully.
169-     */
170-    public static boolean startBesiege(IColony colony, ServerPlayer besieger) {
171-        if (!TaxConfig.isBesiegeSystemEnabled()) {
172-            besieger.sendSystemMessage(Component.literal("The besiege system is disabled on this server.")
173-                    .withStyle(ChatFormatting.RED));
174-            return false;
175-        }
176-
177-        UUID besiegerUUID = besieger.getUUID();
178-        int colonyId = colony.getID();
179-
--
336-
337-            broadcastToNearbyPlayers(colony,
338-                    Component.literal("Nearby colony " + colony.getName()
339-                            + " is under " + (isReclaim ? "reclaim attack" : "siege")
340-                            + " by " + besieger.getName().getString() + "!")
341-                            .withStyle(ChatFormatting.YELLOW), 200);
342-
343-            // Also notify owner + officers directly regardless of distance
344-            notifyColonyOwnersAndOfficers(colony,
345-                    Component.literal("WARNING: " + besieger.getName().getString()
346-                            + " is besieging your colony " + colony.getName() + "!")
347-                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
348-
349-            if (TaxConfig.isNormalLogging())
350-                LOGGER.info("Besiege {} started on colony {} ({}) by {} with {} total defenders ({}g {}m {}e)",
351-                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
352-                        besieger.getName().getString(), totalDefenders, guardCount, militiaCount, mercCount);
353-
354-            return true;
355-
356:        } catch (Exception e) {
357-            LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
358-            ACTIVE_RAIDS.remove(colonyId);
359-            return false;
360-        }
361-    }
362-
363-
364-    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
365-        int count = 0;
366-        for (ICitizenData citizenData : colony.getCitizenManager().getCitizens()) {
367-            if (citizenData.getJob() == null || !citizenData.getJob().isGuard()) continue;
368-            Optional<AbstractEntityCitizen> entityOpt = citizenData.getEntity();
369-            if (entityOpt.isEmpty()) continue;
370-            AbstractEntityCitizen guard = entityOpt.get();
371-
372-            try {
373-                applyDefenderAI(guard, besieger, raid);
374-                applyDefenderEffects(guard, TaxConfig.getBesiegeDurationMinutes());
375-                raid.hostileCitizenIds.add(citizenData.getId());
376-                count++;
377:            } catch (Exception e) {
378-                LOGGER.warn("Failed to make guard {} hostile during besiege", citizenData.getName(), e);
379-            }
380-        }
381-        return count;
382-    }
383-
384-    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
385-        List<ICitizenData> eligible = new ArrayList<>();
386-        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
387-            if (citizen.isChild()) continue;
388-            if (citizen.getEntity().isEmpty()) continue;
389-            if (citizen.getJob() != null && citizen.getJob().isGuard()) continue;
390-            if (citizen.getJob() != null) {
391-                String jobPath = citizen.getJob().getJobRegistryEntry().getKey().getPath();
392-                if (jobPath.equals("deliveryman")) continue;
393-            }
394-            eligible.add(citizen);
395-        }
396-
397-        Collections.shuffle(eligible);
398-        int target = (int) Math.ceil(eligible.size() * TaxConfig.getBesiegeMilitiaPercent());
399-        int count = 0;
400-
401-        for (ICitizenData citizen : eligible) {
402-            if (count >= target) break;
403-            Optional<AbstractEntityCitizen> entityOpt = citizen.getEntity();
404-            if (entityOpt.isEmpty()) continue;
405-            AbstractEntityCitizen entity = entityOpt.get();
406-
407-            try {
408-                // Equip wooden sword
409-                entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
410-                applyDefenderAI(entity, besieger, raid);
411-                applyDefenderEffects(entity, TaxConfig.getBesiegeDurationMinutes());
412-                raid.hostileCitizenIds.add(citizen.getId());
413-                count++;
414:            } catch (Exception e) {
415-                LOGGER.warn("Failed to convert citizen {} to militia during besiege", citizen.getName(), e);
416-            }
417-        }
418-        return count;
419-    }
420-
421-    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
422-        entity.goalSelector.removeAllGoals(g -> true);
423-        entity.targetSelector.removeAllGoals(g -> true);
424-
425-        // Use MilitiaAttackGoal instead of vanilla MeleeAttackGoal â€” non-guard citizens
426-        // lack the ATTACK_DAMAGE attribute, which causes MeleeAttackGoal.doHurtTarget() to
427-        // crash with IllegalArgumentException.
428-        entity.goalSelector.addGoal(0, new MilitiaAttackGoal(entity, 1.2D));
429-
430-        // Retaliate against anyone who hits them (covers allies)
431-        entity.targetSelector.addGoal(0, new HurtByTargetGoal(entity));
432-
433-        // Proactively hunt the besieger (and any allies)
434-        entity.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(entity, Player.class,
--
472-        }
473-        // Also boost mercenaries
474-        for (Entity merc : raid.spawnedMercenaries) {
475-            if (merc instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
476-                living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier));
477-            }
478-        }
479-        if (TaxConfig.isDebugLogging())
480-            LOGGER.debug("FORTIFICATION bonus applied to colony {} defenders (extra resistance amplifier: {})",
481-                    colony.getName(), extraAmplifier);
482-    }
483-
484-    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
485-        Level world = colony.getWorld();
486-        if (!(world instanceof ServerLevel)) return 0;
487-
488-        int buildingCount = 0;
489-        try {
490-            buildingCount = net.machiavelli.minecolonytax.compat.ColonyBuildingUtil
491-                    .getBuildings(colony).size();
492:        } catch (Exception e) {
493-            LOGGER.warn("Could not count buildings for mercenary calc in colony {}", colony.getName());
494-        }
495-
496-        int count = (int) (buildingCount * TaxConfig.getBesiegeExtraMercenariesPerBuilding());
497-        count = Math.min(count, TaxConfig.getBesiegeMaxMercenaries());
498-        if (count <= 0) return 0;
499-
500-        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
501-        int spawned = 0;
502-
503-        for (int i = 0; i < count; i++) {
504-            try {
505-                EntityMercenary merc = (EntityMercenary) com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
506-                if (merc == null) continue;
507-
508-                BlockPos spawnPos = findSpawnPos(colony.getCenter(), world);
509-                merc.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
510-                merc.setTarget(besieger);
511-
512-                merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1));
513-                merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, durationTicks, 0));
514-
515-                world.addFreshEntity(merc);
516-                raid.spawnedMercenaries.add(merc);
517-                spawned++;
518:            } catch (Exception e) {
519-                LOGGER.warn("Failed to spawn mercenary {} for besiege on colony {}", i, colony.getName(), e);
520-            }
521-        }
522-        return spawned;
523-    }
524-
525-
526-    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
527-        cleanupRaid(raid, true);
528-        applyCooldown(raid.besiegingPlayerUUID);
529-
530-        if (attackerWon) {
531-            if (raid.isReclaim) {
532-                completeReclaim(raid, colony);
533-            } else {
534-                completeBesiegeVictory(raid, colony);
535-            }
536-        } else {
537-            sendToPlayer(raid.besiegingPlayerUUID,
538-                    Component.literal("The besiege of " + colony.getName() + " failed.")
--
623-            for (int citizenId : raid.hostileCitizenIds) {
624-                try {
625-                    ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
626-                    if (citizen != null && citizen.getEntity().isPresent()) {
627-                        AbstractEntityCitizen entity = citizen.getEntity().get();
628-                        entity.goalSelector.removeAllGoals(g -> true);
629-                        entity.targetSelector.removeAllGoals(g -> true);
630-                        // Remove militia sword if present
631-                        if (entity.getMainHandItem().getItem() == Items.WOODEN_SWORD) {
632-                            entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
633-                        }
634-                        // Remove combat effects
635-                        entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
636-                        entity.removeEffect(MobEffects.MOVEMENT_SPEED);
637-                        entity.removeEffect(MobEffects.DAMAGE_BOOST);
638-                        // Restore job AI
639-                        if (citizen.getJob() != null) {
640-                            entity.getCitizenJobHandler().onJobChanged(citizen.getJob());
641-                        }
642-                    }
643:                } catch (Exception e) {
644-                    LOGGER.warn("Failed to restore citizen {} AI after besiege", citizenId, e);
645-                }
646-            }
647-
648-            // Despawn mercenaries
649-            for (Entity merc : raid.spawnedMercenaries) {
650-                try {
651-                    if (merc.isAlive()) merc.remove(Entity.RemovalReason.DISCARDED);
652:                } catch (Exception e) {
653-                    LOGGER.warn("Failed to despawn mercenary after besiege", e);
654-                }
655-            }
656-        }
657-
658-        // Remove boss bar
659-        if (raid.bossEvent != null) {
660-            try {
661-                raid.bossEvent.removeAllPlayers();
662:            } catch (Exception e) {
663-                LOGGER.warn("Failed to remove besiege boss bar", e);
664-            }
665-            raid.bossEvent = null;
666-        }
667-
668-        if (removeFromMap) {
669-            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
670-        }
671-    }
672-
673-
674-    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
675-        try {
676-            int minutes = TaxConfig.getBesiegeDurationMinutes();
677-            Component text = Component.literal(
678-                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
679-                            colony.getName(), totalDefenders, minutes, 0))
680-                    .withStyle(ChatFormatting.YELLOW);
681-
682-            raid.bossEvent = new ServerBossEvent(text, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
683-            raid.bossEvent.setProgress(1.0f);
684-            raid.bossEvent.addPlayer(besieger);
685-
686-            // Add nearby players
687-            Level world = colony.getWorld();
688-            if (world instanceof ServerLevel serverLevel) {
689-                BlockPos center = colony.getCenter();
690-                for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
691-                    if (!player.equals(besieger) && player.level() == world) {
692-                        double dist = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
693-                        if (dist <= 200.0 * 200.0) {
694-                            try { raid.bossEvent.addPlayer(player); } catch (Exception ignored) {}
695-                        }
696-                    }
697-                }
698-            }
699:        } catch (Exception e) {
700-            LOGGER.error("Failed to create boss bar for besiege on colony {}", colony.getName(), e);
701-        }
702-    }
703-
704-    private static void updateBossBar(BesiegeRaidData raid, IColony colony) {
705-        if (raid.bossEvent == null) return;
706-
707-        try {
708-            long remaining = Math.max(0, raid.endTime - System.currentTimeMillis());
709-            float progress = (float) remaining / (float) (TaxConfig.getBesiegeDurationMinutes() * 60_000L);
710-            progress = Math.max(0f, Math.min(1f, progress));
711-
712-            long seconds = remaining / 1000;
713-            long mm = seconds / 60;
714-            long ss = seconds % 60;
715-
716-            int aliveDefenders = countAliveDefenders(raid, colony);
717-
718-            BossEvent.BossBarColor color = progress > 0.6f ? BossEvent.BossBarColor.GREEN
719-                    : progress > 0.3f ? BossEvent.BossBarColor.YELLOW
720-                    : BossEvent.BossBarColor.RED;
721-
722-            raid.bossEvent.setColor(color);
723-            raid.bossEvent.setProgress(progress);
724-            raid.bossEvent.setName(Component.literal(
725-                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
726-                            colony.getName(), aliveDefenders, mm, ss)));
727:        } catch (Exception e) {
728-            LOGGER.warn("Failed to update besiege boss bar", e);
729-        }
730-    }
731-
732-
733-    public static boolean isColonyBesieged(int colonyId) {
734-        return OCCUPATIONS.containsKey(colonyId);
735-    }
736-
737-    public static boolean isActiveRaidOnColony(int colonyId) {
738-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
739-            if (raid.colonyId == colonyId) return true;
740-        }
741-        return false;
742-    }
743-
744-    /** All currently active besiege raids targeting this colony. */
745-    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
746-        List<BesiegeRaidData> matches = new ArrayList<>();
747-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
--
944-     */
945-    private static void grantBesiegeCombatPermissions(IColony colony, UUID playerUUID) {
946-        if (!TaxConfig.ENABLE_WAR_ACTIONS.get()) return;
947-        try {
948-            IPermissions perms = colony.getPermissions();
949-            // Snapshot before modifying (for restore on cleanup)
950-            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.snapshotBefore(colony);
951-
952-            // Assign the player to Hostile rank so guards treat them as enemy
953-            Rank hostile = perms.getRankHostile();
954-            perms.setPlayerRank(playerUUID, hostile, colony.getWorld());
955-
956-            // Enable combat actions on the hostile rank
957-            for (Action a : TaxConfig.getWarActions()) {
958-                perms.setPermission(hostile, a, true);
959-            }
960-
961-            if (TaxConfig.isDebugLogging())
962-                LOGGER.debug("Granted besiege combat permissions to {} on colony {}",
963-                        playerUUID, colony.getName());
964:        } catch (Exception e) {
965-            LOGGER.error("Failed to grant besiege combat permissions for {} on colony {}",
966-                    playerUUID, colony.getName(), e);
967-        }
968-    }
969-
970-    /**
971-     * Revokes combat permissions and demotes the player from hostile back to neutral.
972-     * Called during raid cleanup.
973-     */
974-    private static void revokeBesiegeCombatPermissions(IColony colony, UUID playerUUID) {
975-        if (!TaxConfig.ENABLE_WAR_ACTIONS.get()) return;
976-        try {
977-            IPermissions perms = colony.getPermissions();
978-
979-            // Disable combat actions on hostile rank
980-            Rank hostile = perms.getRankHostile();
981-            for (Action a : TaxConfig.getWarActions()) {
982-                perms.setPermission(hostile, a, false);
983-            }
984-
985-            // Demote player back to neutral (skip if they are the colony owner)
986-            UUID owner = perms.getOwner();
987-            if (!playerUUID.equals(owner)) {
988-                Rank neutral = perms.getRankNeutral();
989-                perms.setPlayerRank(playerUUID, neutral, colony.getWorld());
990-            }
991-
992-            if (TaxConfig.isDebugLogging())
993-                LOGGER.debug("Revoked besiege combat permissions from {} on colony {}",
994-                        playerUUID, colony.getName());
995:        } catch (Exception e) {
996-            LOGGER.error("Failed to revoke besiege combat permissions for {} on colony {}",
997-                    playerUUID, colony.getName(), e);
998-        }
999-    }
1000-
1001-    private static void loadData(MinecraftServer server) {
1002-        File f = new File(server.getServerDirectory(), STORAGE_FILE);
1003-        if (!f.exists()) return;
1004-        try (FileReader r = new FileReader(f)) {
1005-            Type type = new TypeToken<List<BesiegeOccupationData>>() {}.getType();
1006-            List<BesiegeOccupationData> list = GSON.fromJson(r, type);
1007-            if (list != null) {
1008-                for (BesiegeOccupationData occ : list) {
1009-                    OCCUPATIONS.put(occ.colonyId, occ);
1010-                }
1011-            }
1012-            if (TaxConfig.isNormalLogging())
1013-                LOGGER.info("Loaded {} besiege occupation record(s)", OCCUPATIONS.size());
1014:        } catch (Exception e) {
1015-            LOGGER.error("Failed to load besiege occupation data", e);
1016-        }
1017-    }
1018-
1019-    private static void saveData() {
1020-        if (SERVER == null) return;
1021-        // Snapshot on the calling (server) thread.
1022-        final List<BesiegeOccupationData> list = new ArrayList<>(OCCUPATIONS.values());
1023-        final File f = new File(SERVER.getServerDirectory(), STORAGE_FILE);
1024-
1025-        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("besiege", () -> {
1026-            try {
1027-                f.getParentFile().mkdirs();
1028-                try (FileWriter w = new FileWriter(f)) {
1029-                    GSON.toJson(list, w);
1030-                }
1031:            } catch (Exception e) {
1032-                LOGGER.error("Failed to save besiege occupation data", e);
1033-            }
1034-        });
1035-    }
1036-
1037-
1038-    /** Transient raid state â€” not persisted. */
1039-    public static class BesiegeRaidData {
1040-        public final int colonyId;
1041-        public final UUID besiegingPlayerUUID;
1042-        public final long startTime;
1043-        public final long endTime;
1044-        public final Set<Integer> hostileCitizenIds = ConcurrentHashMap.newKeySet();
1045-        public final Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
1046-        public final Set<UUID> alliedPlayers = ConcurrentHashMap.newKeySet();
1047-        public final BlockPos colonyCenter;
1048-        public ServerBossEvent bossEvent;
1049-        public final boolean isReclaim;
1050-
1051-        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"isActiveRaidOnColony\\(|isColonyBesieged\\(|getActiveRaids\\(\\)\\.containsKey\\(|getActiveRaids\\(\\)\\.values\\(\\)|getActiveRaids\\(\\)\\.entrySet\\(\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"completeBesiege\\(|cleanupRaid\\(\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"BesiegeRaidData\" src/main/java --glob '"'!src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java'"'" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 366ms:
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:733:    public static boolean isColonyBesieged(int colonyId) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:737:    public static boolean isActiveRaidOnColony(int colonyId) {
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:162:                || BesiegeManager.isColonyBesieged(colonyId)) {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:52:        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:98:            for (Map.Entry<UUID, ActiveRaidData> entry : RaidManager.getActiveRaids().entrySet()) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:296:        for (ActiveRaidData raidData : RaidManager.getActiveRaids().values()) {
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:436:                    : net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().values()) {
src/main/java\net\machiavelli\minecolonytax\commands\WarCommands.java:115:        boolean colonyBeingRaided = RaidManager.getActiveRaids().values().stream()
src/main/java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:227:        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
src/main/java\net\machiavelli\minecolonytax\permissions\TaxPermissionManager.java:25:                && net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(colonyId)
src/main/java\net\machiavelli\minecolonytax\permissions\PermissionSnapshot.java:179:        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1396:                        if (BesiegeManager.isColonyBesieged(target.getID())) {
src/main/java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:62:                        boolean isBesieged = net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(data.getVassalColonyId());
src/main/java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:152:            boolean isBesieged = net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(colonyId);
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:174:                            && BesiegeManager.isColonyBesieged(colonyId)) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 387ms:
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:88:            cleanupRaid(raid, false);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:105:                    cleanupRaid(raid, false);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:122:                    cleanupRaid(raid, false);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:137:                        cleanupRaid(raid, false);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:151:                    completeBesiege(raid, true, colony);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:526:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:527:        cleanupRaid(raid, true);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:613:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:88:            cleanupRaid(raid, false);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:105:                    cleanupRaid(raid, false);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:122:                    cleanupRaid(raid, false);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:137:                        cleanupRaid(raid, false);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:151:                    completeBesiege(raid, true, colony);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:526:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:527:        cleanupRaid(raid, true);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:613:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 387ms:
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1434:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1446:                        for (BesiegeManager.BesiegeRaidData raid : raids.values()) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1487:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1490:                List<BesiegeManager.BesiegeRaidData> myRaids = raids.values().stream()
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1518:                        for (BesiegeManager.BesiegeRaidData raid : myRaids) {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:115:                    BesiegeManager.BesiegeRaidData raid = entry.getValue();
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:435:            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
The compatibility view is preserving the old `Map<Integer, BesiegeRaidData>` type, but several current readers treat it as the authoritative active-raid list. That means same-colony concurrent raids can disappear from UI/status logic even if the core storage is re-keyed correctly.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 35 "showBesiegeStatusAdmin|showBesiegeStatusPlayer|Active Raids|myRaids" src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 30 \"Besiege notifications|notifiedBesieges|BesiegeManager\\.getActiveRaids\" src/main/java/net/machiavelli/minecolonytax/event/RaidLoginNotifier.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 28 \"BesiegeManager\\.getActiveRaids\\(\\)|getActiveRaids\\(\\)\\.get\\(colonyId\\)\" src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 395ms:
1388-
1389-                        if (target == null) {
1390-                                player.sendSystemMessage(Component.literal("Colony not found: " + colonyArg)
1391-                                                .withStyle(ChatFormatting.RED));
1392-                                return 0;
1393-                        }
1394-
1395-                        // If the colony is already besieged AND the player is the former owner -> reclaim
1396-                        if (BesiegeManager.isColonyBesieged(target.getID())) {
1397-                                BesiegeManager.BesiegeOccupationData occ = BesiegeManager.getOccupation(target.getID());
1398-                                if (occ != null && player.getUUID().equals(occ.formerOwnerUUID)) {
1399-                                        boolean started = BesiegeManager.startReclaim(target, player);
1400-                                        return started ? 1 : 0;
1401-                                }
1402-                                player.sendSystemMessage(Component.literal(
1403-                                                "This colony is already under besiege occupation. Only the former owner can reclaim it.")
1404-                                                .withStyle(ChatFormatting.RED));
1405-                                return 0;
1406-                        }
1407-
1408-                        boolean started = BesiegeManager.startBesiege(target, player);
1409-                        return started ? 1 : 0;
1410-
1411-                } catch (CommandSyntaxException e) {
1412-                        ctx.getSource().sendFailure(Component.literal("You must be a player to use this command."));
1413-                        return 0;
1414-                } catch (Exception e) {
1415-                        LOGGER.error("Error handling besiege command", e);
1416-                        return 0;
1417-                }
1418-        }
1419-
1420-        private static int handleBesiegeStatus(CommandContext<CommandSourceStack> ctx) {
1421-                CommandSourceStack src = ctx.getSource();
1422-                if (src.hasPermission(2)) {
1423:                        return showBesiegeStatusAdmin(src);
1424-                }
1425-                try {
1426:                        return showBesiegeStatusPlayer(src, src.getPlayerOrException());
1427-                } catch (CommandSyntaxException e) {
1428-                        src.sendFailure(Component.literal("You must be a player to use this command."));
1429-                        return 0;
1430-                }
1431-        }
1432-
1433:        private static int showBesiegeStatusAdmin(CommandSourceStack src) {
1434-                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
1435-                Map<Integer, BesiegeManager.BesiegeOccupationData> occupations = BesiegeManager.getAllOccupations();
1436-                IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
1437-
1438-                src.sendSuccess(() -> Component.literal("=== Besiege Status (Admin) ===")
1439-                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
1440-
1441:                src.sendSuccess(() -> Component.literal("Active Raids (" + raids.size() + "):")
1442-                                .withStyle(ChatFormatting.YELLOW), false);
1443-                if (raids.isEmpty()) {
1444-                        src.sendSuccess(() -> Component.literal("  None").withStyle(ChatFormatting.GRAY), false);
1445-                } else {
1446-                        for (BesiegeManager.BesiegeRaidData raid : raids.values()) {
1447-                                String colonyName = cm.getAllColonies().stream()
1448-                                                .filter(c -> c.getID() == raid.colonyId)
1449-                                                .map(IColony::getName)
1450-                                                .findFirst().orElse("Colony#" + raid.colonyId);
1451-                                ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(raid.besiegingPlayerUUID);
1452-                                String besiegerName = besieger != null ? besieger.getName().getString() : raid.besiegingPlayerUUID.toString();
1453-                                long elapsedMin = (System.currentTimeMillis() - raid.startTime) / 60000;
1454-                                String label = raid.isReclaim ? " (RECLAIM)" : "";
1455-                                final String line = String.format("  \u2022 %s (ID: %d) \u2014 besieged by %s \u2014 %dm elapsed%s",
1456-                                                colonyName, raid.colonyId, besiegerName, elapsedMin, label);
1457-                                src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
1458-                        }
1459-                }
1460-
1461-                src.sendSuccess(() -> Component.literal("Occupations (" + occupations.size() + "):")
1462-                                .withStyle(ChatFormatting.YELLOW), false);
1463-                if (occupations.isEmpty()) {
1464-                        src.sendSuccess(() -> Component.literal("  None").withStyle(ChatFormatting.GRAY), false);
1465-                } else {
1466-                        for (BesiegeManager.BesiegeOccupationData occ : occupations.values()) {
1467-                                ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(occ.besiegingPlayerUUID);
1468-                                String besiegerName = besieger != null ? besieger.getName().getString() : occ.besiegingPlayerUUID.toString();
1469-                                ServerPlayer formerOwner = occ.formerOwnerUUID != null
1470-                                                ? src.getServer().getPlayerList().getPlayer(occ.formerOwnerUUID) : null;
1471-                                String formerOwnerName = formerOwner != null ? formerOwner.getName().getString()
1472-                                                : (occ.formerOwnerUUID != null ? occ.formerOwnerUUID.toString() : "unknown");
1473-                                final String line = String.format("  \u2022 %s (ID: %d) \u2014 besieged by %s, former owner: %s \u2014 tribute: %d%%",
1474-                                                occ.colonyName, occ.colonyId, besiegerName, formerOwnerName, occ.tributePercent);
1475-                                src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
1476-                        }
1477-                }
1478-
1479-                src.sendSuccess(() -> Component.literal("==============================")
1480-                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
1481-                return 1;
1482-        }
1483-
1484:        private static int showBesiegeStatusPlayer(CommandSourceStack src, ServerPlayer player) {
1485-                UUID playerUUID = player.getUUID();
1486-                IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
1487-                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
1488-                Map<Integer, BesiegeManager.BesiegeOccupationData> occupations = BesiegeManager.getAllOccupations();
1489-
1490:                List<BesiegeManager.BesiegeRaidData> myRaids = raids.values().stream()
1491-                                .filter(r -> {
1492-                                        if (r.besiegingPlayerUUID.equals(playerUUID)) return true;
1493-                                        return cm.getAllColonies().stream()
1494-                                                        .filter(c -> c.getID() == r.colonyId)
1495-                                                        .anyMatch(c -> {
1496-                                                                var rank = c.getPermissions().getRank(playerUUID);
1497-                                                                return rank != null && rank.isColonyManager();
1498-                                                        });
1499-                                })
1500-                                .collect(Collectors.toList());
1501-
1502-                List<BesiegeManager.BesiegeOccupationData> myOccupations = occupations.values().stream()
1503-                                .filter(o -> o.besiegingPlayerUUID.equals(playerUUID)
1504-                                                || (o.formerOwnerUUID != null && o.formerOwnerUUID.equals(playerUUID)))
1505-                                .collect(Collectors.toList());
1506-
1507:                if (myRaids.isEmpty() && myOccupations.isEmpty()) {
1508-                        src.sendSuccess(() -> Component.literal("No active besiegements for your colonies.")
1509-                                        .withStyle(ChatFormatting.GRAY), false);
1510-                        return 1;
1511-                }
1512-
1513-                src.sendSuccess(() -> Component.literal("=== Besiege Status ===")
1514-                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
1515-
1516:                if (!myRaids.isEmpty()) {
1517:                        src.sendSuccess(() -> Component.literal("Active Raids:").withStyle(ChatFormatting.YELLOW), false);
1518:                        for (BesiegeManager.BesiegeRaidData raid : myRaids) {
1519-                                String colonyName = cm.getAllColonies().stream()
1520-                                                .filter(c -> c.getID() == raid.colonyId)
1521-                                                .map(IColony::getName)
1522-                                                .findFirst().orElse("Colony#" + raid.colonyId);
1523-                                long elapsedMin = (System.currentTimeMillis() - raid.startTime) / 60000;
1524-                                long remainingMin = Math.max(0, raid.endTime - System.currentTimeMillis()) / 60000;
1525-                                final String line;
1526-                                if (raid.besiegingPlayerUUID.equals(playerUUID)) {
1527-                                        line = String.format("  \u2022 %s (ID: %d) \u2014 you are besieging \u2014 %dm elapsed, %dm remaining",
1528-                                                        colonyName, raid.colonyId, elapsedMin, remainingMin);
1529-                                } else {
1530-                                        ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(raid.besiegingPlayerUUID);
1531-                                        String besiegerName = besieger != null ? besieger.getName().getString() : raid.besiegingPlayerUUID.toString();
1532-                                        line = String.format("  \u2022 %s (ID: %d) \u2014 besieged by %s \u2014 %dm elapsed, %dm remaining",
1533-                                                        colonyName, raid.colonyId, besiegerName, elapsedMin, remainingMin);
1534-                                }
1535-                                src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
1536-                        }
1537-                }
1538-
1539-                if (!myOccupations.isEmpty()) {
1540-                        src.sendSuccess(() -> Component.literal("Occupied Colonies:").withStyle(ChatFormatting.YELLOW), false);
1541-                        for (BesiegeManager.BesiegeOccupationData occ : myOccupations) {
1542-                                final String line;
1543-                                if (occ.besiegingPlayerUUID.equals(playerUUID)) {
1544-                                        line = String.format("  \u2022 %s (ID: %d) \u2014 you occupy it \u2014 tribute: %d%%",
1545-                                                        occ.colonyName, occ.colonyId, occ.tributePercent);
1546-                                } else {
1547-                                        ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(occ.besiegingPlayerUUID);
1548-                                        String besiegerName = besieger != null ? besieger.getName().getString() : occ.besiegingPlayerUUID.toString();
1549-                                        line = String.format("  \u2022 %s (ID: %d) \u2014 occupied by %s \u2014 tribute: %d%%",
1550-                                                        occ.colonyName, occ.colonyId, besiegerName, occ.tributePercent);
1551-                                }
1552-                                src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
1553-                        }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 397ms:
5-import net.machiavelli.minecolonytax.WarSystem;
6-import net.machiavelli.minecolonytax.besiege.BesiegeManager;
7-import net.machiavelli.minecolonytax.data.WarData;
8-import net.machiavelli.minecolonytax.occupation.OccupationManager;
9-import net.machiavelli.minecolonytax.raid.ActiveRaidData;
10-import net.machiavelli.minecolonytax.raid.RaidManager;
11-import net.minecraft.ChatFormatting;
12-import net.minecraft.network.chat.Component;
13-import net.minecraft.server.level.ServerPlayer;
14-import net.minecraftforge.event.entity.player.PlayerEvent;
15-import net.minecraftforge.eventbus.api.SubscribeEvent;
16-import net.minecraftforge.fml.common.Mod;
17-import net.minecraftforge.server.ServerLifecycleHooks;
18-
19-import com.minecolonies.api.colony.IColony;
20-import com.minecolonies.api.colony.permissions.IPermissions;
21-import com.minecolonies.api.colony.permissions.ColonyPlayer;
22-
23-import java.util.*;
24-import java.util.stream.Collectors;
25-
26-@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
27-public class RaidLoginNotifier {
28-
29-    // Keep history of completed (including aborted) raids
30-    private static final List<ActiveRaidData> completedRaids = Collections.synchronizedList(new ArrayList<>());
31-
32-    // For each player, track which raids/wars/besieges/occupations they've already been notified about
33-    private static final Map<UUID, Set<UUID>> notifiedRaidsByPlayer = new HashMap<>();
34-    private static final Map<UUID, Set<Integer>> notifiedWarsByPlayer = new HashMap<>();
35:    private static final Map<UUID, Set<Integer>> notifiedBesiegesByPlayer = new HashMap<>();
36-    private static final Map<UUID, Set<Integer>> notifiedOccupationsByPlayer = new HashMap<>();
37-
38-    /** Call this from raid-ending logic to record completed raids for offline notification. */
39-    public static void recordCompletedRaid(ActiveRaidData raid) {
40-        completedRaids.add(raid);
41-    }
42-
43-    @SubscribeEvent
44-    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
45-        ServerPlayer player = (ServerPlayer) event.getEntity();
46-        UUID playerUUID = player.getUUID();
47-
48-        // --- Raid notifications ---
49-        notifiedRaidsByPlayer.computeIfAbsent(playerUUID, k -> new HashSet<>());
50-        Set<UUID> notifiedRaids = notifiedRaidsByPlayer.get(playerUUID);
51-
52-        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
53-            if (isPlayerOfficerOrOwner(player, raid.getColony().getPermissions())) {
54-                notifyRaid(player, raid, true);
55-                UUID identifier = UUID.nameUUIDFromBytes((raid.getColony().getID() + ":" + raid.getRaider()).getBytes());
56-                notifiedRaids.add(identifier);
57-            } else {
58-                notifyRaidIfRelevant(player, raid, notifiedRaids, true);
59-            }
60-        }
61-
62-        synchronized (completedRaids) {
63-            Iterator<ActiveRaidData> it = completedRaids.iterator();
64-            while (it.hasNext()) {
65-                ActiveRaidData raid = it.next();
--
77-        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
78-            try {
79-                IColony defColony = war.getColony();
80-                IColony atkColony = war.getAttackerColony();
81-
82-                boolean playerIsDefender = defColony != null
83-                        && isPlayerOfficerOrOwner(player, defColony.getPermissions());
84-                boolean playerIsAttacker = atkColony != null
85-                        && isPlayerOfficerOrOwner(player, atkColony.getPermissions());
86-
87-                if (!playerIsDefender && !playerIsAttacker) continue;
88-
89-                int warKey = playerIsDefender
90-                        ? (defColony != null ? defColony.getID() : -1)
91-                        : (atkColony != null ? atkColony.getID() : -1);
92-
93-                if (warKey < 0 || notifiedWars.contains(warKey)) continue;
94-
95-                String defName = defColony != null ? defColony.getName() : "unknown";
96-                String atkName = atkColony != null ? atkColony.getName() : "unknown";
97-                String msg = playerIsDefender
98-                        ? "Your colony " + defName + " is at war â€” attacker: " + atkName + "!"
99-                        : "Your colony " + atkName + " is waging war against " + defName + "!";
100-
101-                player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED));
102-                sendTitleNotification(player, "AT WAR!", defName + " vs " + atkName, "red");
103-                notifiedWars.add(warKey);
104-            } catch (Exception ignored) {}
105-        }
106-
107:        // --- Besiege notifications ---
108-        if (TaxConfig.isBesiegeSystemEnabled()) {
109:            notifiedBesiegesByPlayer.computeIfAbsent(playerUUID, k -> new HashSet<>());
110:            Set<Integer> notifiedBesieges = notifiedBesiegesByPlayer.get(playerUUID);
111-
112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
113-                try {
114-                    int colonyId = entry.getKey();
115-                    BesiegeManager.BesiegeRaidData raid = entry.getValue();
116-
117-                    IColony besiegedColony = com.minecolonies.api.IMinecoloniesAPI.getInstance()
118-                            .getColonyManager().getAllColonies().stream()
119-                            .filter(c -> c.getID() == colonyId).findFirst().orElse(null);
120-                    if (besiegedColony == null) continue;
121-
122-                    if (!isPlayerOfficerOrOwner(player, besiegedColony.getPermissions())) continue;
123:                    if (notifiedBesieges.contains(colonyId)) continue;
124-
125-                    String besiegerName = getPlayerName(raid.besiegingPlayerUUID);
126-                    player.sendSystemMessage(Component.literal(
127-                            "Your colony " + besiegedColony.getName() + " is being besieged by " + besiegerName + "!")
128-                            .withStyle(ChatFormatting.DARK_RED));
129-                    sendTitleNotification(player, "BESIEGED!", besiegedColony.getName(), "dark_red");
130:                    notifiedBesieges.add(colonyId);
131-                } catch (Exception ignored) {}
132-            }
133-        }
134-
135-        // --- Occupation notifications ---
136-        if (TaxConfig.isOccupationSystemEnabled()) {
137-            notifiedOccupationsByPlayer.computeIfAbsent(playerUUID, k -> new HashSet<>());
138-            Set<Integer> notifiedOccupations = notifiedOccupationsByPlayer.get(playerUUID);
139-
140-            for (Map.Entry<Integer, OccupationManager.OccupationData> entry
141-                    : OccupationManager.getActiveOccupations().entrySet()) {
142-                try {
143-                    OccupationManager.OccupationData occ = entry.getValue();
144-                    if (occ.isExpired()) continue;
145-
146-                    boolean isOriginalOwner = occ.getOriginalOwnerUUID().equals(playerUUID);
147-                    boolean isOccupier = occ.getOccupierUUID().equals(playerUUID);
148-                    if (!isOriginalOwner && !isOccupier) continue;
149-                    if (notifiedOccupations.contains(occ.colonyId)) continue;
150-
151-                    if (isOriginalOwner) {
152-                        player.sendSystemMessage(Component.literal(
153-                                "Your colony " + occ.colonyName + " is under occupation! Wage a reclamation war to take it back.")
154-                                .withStyle(ChatFormatting.RED));
155-                        sendTitleNotification(player, "OCCUPIED!", occ.colonyName, "red");
156-                    } else {
157-                        player.sendSystemMessage(Component.literal(
158-                                "You are occupying colony " + occ.colonyName + ".")
159-                                .withStyle(ChatFormatting.GOLD));
160-                    }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 397ms:
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-406-     * O(1) hasEffect check exits immediately when no levitation present.
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-407-     */
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-408-    @SubscribeEvent
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-409-    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-410-        if (event.phase != TickEvent.Phase.END) return;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-411-        Player player = event.player;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-412-        if (!player.hasEffect(MobEffects.LEVITATION)) return;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-413-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-414-        boolean shouldStrip = TaxConfig.isSuppressColonyLevitation()
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-415-                || (player instanceof ServerPlayer sp && isActiveConflictParticipant(sp.getUUID()));
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-416-        if (shouldStrip) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-417-            player.removeEffect(MobEffects.LEVITATION);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-418-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-419-    }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-420-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-421-    private static boolean isActiveConflictParticipant(UUID playerId) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-422-        // O(1) HashMap get â€” check raids first (most common conflict type)
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-423-        if (!RaidManager.getActiveRaids().isEmpty()
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-424-                && RaidManager.getActiveRaidForPlayer(playerId) != null) return true;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-425-        // isEmpty guards avoid creating an iterator on empty maps
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-426-        if (!WarSystem.ACTIVE_WARS.isEmpty()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-427-            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-428-                if ((war.getAttackerLives() != null && war.getAttackerLives().containsKey(playerId))
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-429-                        || (war.getDefenderLives() != null && war.getDefenderLives().containsKey(playerId))) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-430-                    return true;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-431-                }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-432-            }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-433-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java:434:        if (!net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().isEmpty()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-435-            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java:436:                    : net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().values()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-437-                if (besiege.besiegingPlayerUUID != null && besiege.besiegingPlayerUUID.equals(playerId)) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-438-                    return true;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-439-                }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-440-            }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-441-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-442-        return false;
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-443-    }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-444-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-445-    private static void sendWarParticipantsMessage(WarData war, Component message) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-446-        for (UUID uuid : war.getAttackerLives().keySet()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-447-            if (war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-448-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-449-                if (p != null) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-450-                    p.sendSystemMessage(message);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-451-                }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-452-            }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-453-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-454-        for (UUID uuid : war.getDefenderLives().keySet()) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-455-            if (war.getColony().getWorld().getServer() != null) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-456-                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-457-                if (p != null) {
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-458-                    p.sendSystemMessage(message);
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-459-                }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-460-            }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-461-        }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-462-    }
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-463-
src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java-464-    private static void sendColonyMessage(IColony colony, Component message) {
--
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-147-                        entries.add(warToLogEntry(war));
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-148-                    }
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-149-
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-150-                    // Active state events: war, besiege, occupation (always shown at top, not dismissible)
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-151-                    WarData activeWar = WarSystem.ACTIVE_WARS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-152-                    if (activeWar == null) {
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-153-                        for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-154-                            if (wd.getAttackerColony() != null && wd.getAttackerColony().getID() == colonyId) {
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-155-                                activeWar = wd; break;
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-156-                            }
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-157-                        }
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-158-                    }
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-159-                    if (activeWar != null) {
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-160-                        boolean isDefender = WarSystem.ACTIVE_WARS.containsKey(colonyId);
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-161-                        String opponent = isDefender
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-162-                                ? (activeWar.getAttackerColony() != null ? activeWar.getAttackerColony().getName() : "unknown")
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-163-                                : (activeWar.getColony() != null ? activeWar.getColony().getName() : "unknown");
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-164-                        String role = isDefender ? "Defending vs " : "Attacking ";
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-165-                        entries.add(new EventLogEntry(
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-166-                                "active_war_" + colonyId,
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-167-                                role + opponent,
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-168-                                isDefender ? "Under attack â€” tax claiming disabled" : "War ongoing â€” treasury draining",
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-169-                                "Active",
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-170-                                0xFFA03030, true, -1));
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-171-                    }
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-172-
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-173-                    if (TaxConfig.isBesiegeSystemEnabled()
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-174-                            && BesiegeManager.isColonyBesieged(colonyId)) {
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-176-                        String besiegerName = getPlayerDisplayName(brd != null ? brd.besiegingPlayerUUID : null, player);
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-177-                        entries.add(new EventLogEntry(
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-178-                                "active_besiege_" + colonyId,
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-179-                                "Under Siege by " + besiegerName,
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-180-                                "Colony besieged â€” tax claiming disabled",
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-181-                                "Besieged",
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-182-                                0xFFA03030, true, -1));
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-183-                    }
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-184-
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-185-                    if (TaxConfig.isOccupationSystemEnabled()) {
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-186-                        OccupationManager.OccupationData occ = OccupationManager.getOccupation(colonyId);
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-187-                        if (occ != null && !occ.isExpired()) {
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-188-                            String occupierName = getPlayerDisplayName(occ.getOccupierUUID(), player);
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-189-                            entries.add(new EventLogEntry(
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-190-                                    "active_occupation_" + colonyId,
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-191-                                    "Occupied by " + occupierName,
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-192-                                    "Taxes collected by occupier â€” wage reclamation war to retake it",
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-193-                                    "Occupied",
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-194-                                    0xFFFF9800, true, -1));
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-195-                        }
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-196-                    }
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-197-
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-198-                    // Sort: active state events first, then random events (active before inactive), then history newest-first
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-199-                    entries.sort((a, b) -> {
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-200-                        boolean aState = isActiveStateEntry(a.getEventId());
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-201-                        boolean bState = isActiveStateEntry(b.getEventId());
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-202-                        if (aState != bState) return aState ? -1 : 1;
src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java-203-                        boolean aHist = isHistoryEntry(a.getEventId());
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"registerAlly|alliedPlayers|hostileCitizenIds|spawnedMercenaries|besiegingPlayerUUID|isReclaim\" src/main/java --glob '"'!src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java'"'" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "Besiege|besiege|registerAlly|getRaidsForColony|getActiveRaids" src/main/java/net/machiavelli/minecolonytax -g "*Kill*" -g "*Combat*" -g "*Event*"' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"getActiveRaids\\(\\)\" src/main/java/net/machiavelli/minecolonytax --glob '"'!**/RaidManager.java'"'" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 396ms:
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:98:            for (Map.Entry<UUID, ActiveRaidData> entry : RaidManager.getActiveRaids().entrySet()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:296:        for (ActiveRaidData raidData : RaidManager.getActiveRaids().values()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:423:        if (!RaidManager.getActiveRaids().isEmpty()
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:434:        if (!net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().isEmpty()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:435:            for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData besiege
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:436:                    : net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().values()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:437:                if (besiege.besiegingPlayerUUID != null && besiege.besiegingPlayerUUID.equals(playerId)) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 397ms:
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:802:    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
src/main/java/net/machiavelli/minecolonytax\event\BlockInteractionFilterHandler.java:105:                        .getActiveRaids().isEmpty()) return true;
src/main/java/net/machiavelli/minecolonytax\event\BlockInteractionFilterHandler.java:107:                && !RaidManager.getActiveRaids().isEmpty()) return true;
src/main/java/net/machiavelli/minecolonytax\event\BlockInteractionFilterHandler.java:227:        if (TaxConfig.isBlockFilterRaidsEnabled() && !RaidManager.getActiveRaids().isEmpty()) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1309:                if (!RaidManager.getActiveRaids().isEmpty()) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1344:                if (!RaidManager.getActiveRaids().isEmpty()) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1434:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1487:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1794:                var activeRaids = raidManager.getActiveRaids();
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:115:        boolean colonyBeingRaided = RaidManager.getActiveRaids().values().stream()
src/main/java/net/machiavelli/minecolonytax\event\RaidLoginNotifier.java:52:        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
src/main/java/net/machiavelli/minecolonytax\event\RaidLoginNotifier.java:112:            for (Map.Entry<Integer, BesiegeManager.BesiegeRaidData> entry : BesiegeManager.getActiveRaids().entrySet()) {
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionSnapshot.java:179:        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java:227:        for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:98:            for (Map.Entry<UUID, ActiveRaidData> entry : RaidManager.getActiveRaids().entrySet()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:296:        for (ActiveRaidData raidData : RaidManager.getActiveRaids().values()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:423:        if (!RaidManager.getActiveRaids().isEmpty()
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:434:        if (!net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().isEmpty()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:436:                    : net.machiavelli.minecolonytax.besiege.BesiegeManager.getActiveRaids().values()) {
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java:175:                        BesiegeManager.BesiegeRaidData brd = BesiegeManager.getActiveRaids().get(colonyId);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 409ms:
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:61:        public final Set<Entity> spawnedMercenaries;
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:72:            this.spawnedMercenaries = ConcurrentHashMap.newKeySet();
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:289:                raidData.spawnedMercenaries.add(mercenary);
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:355:            int totalDefenders = raidData.hostileCitizens.size() + raidData.spawnedMercenaries.size();
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:506:            for (Entity mercenary : raidData.spawnedMercenaries) {
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:598:            for (Entity mercenary : raidData.spawnedMercenaries) {
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:613:                raidData.spawnedMercenaries.removeAll(deadMercenaries);
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:619:                    raidData.hostileCitizens.size() + raidData.spawnedMercenaries.size(),
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:646:                    for (Entity mercenary : raidData.spawnedMercenaries) {
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:686:            for (Entity mercenary : raidData.spawnedMercenaries) {
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:854:            for (Entity mercenary : raidData.spawnedMercenaries) {
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:1117:            LOGGER.info("Spawned Mercenaries: {}", raidData.spawnedMercenaries.size());
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1451:                                ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(raid.besiegingPlayerUUID);
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1452:                                String besiegerName = besieger != null ? besieger.getName().getString() : raid.besiegingPlayerUUID.toString();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1454:                                String label = raid.isReclaim ? " (RECLAIM)" : "";
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1467:                                ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(occ.besiegingPlayerUUID);
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1468:                                String besiegerName = besieger != null ? besieger.getName().getString() : occ.besiegingPlayerUUID.toString();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1492:                                        if (r.besiegingPlayerUUID.equals(playerUUID)) return true;
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1503:                                .filter(o -> o.besiegingPlayerUUID.equals(playerUUID)
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1526:                                if (raid.besiegingPlayerUUID.equals(playerUUID)) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1530:                                        ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(raid.besiegingPlayerUUID);
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1531:                                        String besiegerName = besieger != null ? besieger.getName().getString() : raid.besiegingPlayerUUID.toString();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1543:                                if (occ.besiegingPlayerUUID.equals(playerUUID)) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1547:                                        ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(occ.besiegingPlayerUUID);
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1548:                                        String besiegerName = besieger != null ? besieger.getName().getString() : occ.besiegingPlayerUUID.toString();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:2951:                for (net.minecraft.world.entity.Entity mercenary : raidData.spawnedMercenaries) {
src/main/java\net\machiavelli\minecolonytax\event\RaidKillTracker.java:450:            if (raidData.spawnedMercenaries.contains(mercenary)) {
src/main/java\net\machiavelli\minecolonytax\event\RaidKillTracker.java:451:                raidData.spawnedMercenaries.remove(mercenary);
src/main/java\net\machiavelli\minecolonytax\event\RaidKillTracker.java:488:        for (Entity mercenary : raidData.spawnedMercenaries) {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:125:                    String besiegerName = getPlayerName(raid.besiegingPlayerUUID);
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:437:                if (besiege.besiegingPlayerUUID != null && besiege.besiegingPlayerUUID.equals(playerId)) {
src/main/java\net\machiavelli\minecolonytax\network\packets\RequestColonyDataPacket.java:176:                        String besiegerName = getPlayerDisplayName(brd != null ? brd.besiegingPlayerUUID : null, player);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 30 "spawnedMercenaries|BesiegeRaidData" src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 45 "BesiegeManager|BesiegeRaidData|spawnedMercenaries|hostileCitizenIds|registerAlly" src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 376ms:
1404-                                                .withStyle(ChatFormatting.RED));
1405-                                return 0;
1406-                        }
1407-
1408-                        boolean started = BesiegeManager.startBesiege(target, player);
1409-                        return started ? 1 : 0;
1410-
1411-                } catch (CommandSyntaxException e) {
1412-                        ctx.getSource().sendFailure(Component.literal("You must be a player to use this command."));
1413-                        return 0;
1414-                } catch (Exception e) {
1415-                        LOGGER.error("Error handling besiege command", e);
1416-                        return 0;
1417-                }
1418-        }
1419-
1420-        private static int handleBesiegeStatus(CommandContext<CommandSourceStack> ctx) {
1421-                CommandSourceStack src = ctx.getSource();
1422-                if (src.hasPermission(2)) {
1423-                        return showBesiegeStatusAdmin(src);
1424-                }
1425-                try {
1426-                        return showBesiegeStatusPlayer(src, src.getPlayerOrException());
1427-                } catch (CommandSyntaxException e) {
1428-                        src.sendFailure(Component.literal("You must be a player to use this command."));
1429-                        return 0;
1430-                }
1431-        }
1432-
1433-        private static int showBesiegeStatusAdmin(CommandSourceStack src) {
1434:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
1435-                Map<Integer, BesiegeManager.BesiegeOccupationData> occupations = BesiegeManager.getAllOccupations();
1436-                IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
1437-
1438-                src.sendSuccess(() -> Component.literal("=== Besiege Status (Admin) ===")
1439-                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
1440-
1441-                src.sendSuccess(() -> Component.literal("Active Raids (" + raids.size() + "):")
1442-                                .withStyle(ChatFormatting.YELLOW), false);
1443-                if (raids.isEmpty()) {
1444-                        src.sendSuccess(() -> Component.literal("  None").withStyle(ChatFormatting.GRAY), false);
1445-                } else {
1446:                        for (BesiegeManager.BesiegeRaidData raid : raids.values()) {
1447-                                String colonyName = cm.getAllColonies().stream()
1448-                                                .filter(c -> c.getID() == raid.colonyId)
1449-                                                .map(IColony::getName)
1450-                                                .findFirst().orElse("Colony#" + raid.colonyId);
1451-                                ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(raid.besiegingPlayerUUID);
1452-                                String besiegerName = besieger != null ? besieger.getName().getString() : raid.besiegingPlayerUUID.toString();
1453-                                long elapsedMin = (System.currentTimeMillis() - raid.startTime) / 60000;
1454-                                String label = raid.isReclaim ? " (RECLAIM)" : "";
1455-                                final String line = String.format("  \u2022 %s (ID: %d) \u2014 besieged by %s \u2014 %dm elapsed%s",
1456-                                                colonyName, raid.colonyId, besiegerName, elapsedMin, label);
1457-                                src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
1458-                        }
1459-                }
1460-
1461-                src.sendSuccess(() -> Component.literal("Occupations (" + occupations.size() + "):")
1462-                                .withStyle(ChatFormatting.YELLOW), false);
1463-                if (occupations.isEmpty()) {
1464-                        src.sendSuccess(() -> Component.literal("  None").withStyle(ChatFormatting.GRAY), false);
1465-                } else {
1466-                        for (BesiegeManager.BesiegeOccupationData occ : occupations.values()) {
1467-                                ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(occ.besiegingPlayerUUID);
1468-                                String besiegerName = besieger != null ? besieger.getName().getString() : occ.besiegingPlayerUUID.toString();
1469-                                ServerPlayer formerOwner = occ.formerOwnerUUID != null
1470-                                                ? src.getServer().getPlayerList().getPlayer(occ.formerOwnerUUID) : null;
1471-                                String formerOwnerName = formerOwner != null ? formerOwner.getName().getString()
1472-                                                : (occ.formerOwnerUUID != null ? occ.formerOwnerUUID.toString() : "unknown");
1473-                                final String line = String.format("  \u2022 %s (ID: %d) \u2014 besieged by %s, former owner: %s \u2014 tribute: %d%%",
1474-                                                occ.colonyName, occ.colonyId, besiegerName, formerOwnerName, occ.tributePercent);
1475-                                src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
1476-                        }
1477-                }
1478-
1479-                src.sendSuccess(() -> Component.literal("==============================")
1480-                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
1481-                return 1;
1482-        }
1483-
1484-        private static int showBesiegeStatusPlayer(CommandSourceStack src, ServerPlayer player) {
1485-                UUID playerUUID = player.getUUID();
1486-                IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
1487:                Map<Integer, BesiegeManager.BesiegeRaidData> raids = BesiegeManager.getActiveRaids();
1488-                Map<Integer, BesiegeManager.BesiegeOccupationData> occupations = BesiegeManager.getAllOccupations();
1489-
1490:                List<BesiegeManager.BesiegeRaidData> myRaids = raids.values().stream()
1491-                                .filter(r -> {
1492-                                        if (r.besiegingPlayerUUID.equals(playerUUID)) return true;
1493-                                        return cm.getAllColonies().stream()
1494-                                                        .filter(c -> c.getID() == r.colonyId)
1495-                                                        .anyMatch(c -> {
1496-                                                                var rank = c.getPermissions().getRank(playerUUID);
1497-                                                                return rank != null && rank.isColonyManager();
1498-                                                        });
1499-                                })
1500-                                .collect(Collectors.toList());
1501-
1502-                List<BesiegeManager.BesiegeOccupationData> myOccupations = occupations.values().stream()
1503-                                .filter(o -> o.besiegingPlayerUUID.equals(playerUUID)
1504-                                                || (o.formerOwnerUUID != null && o.formerOwnerUUID.equals(playerUUID)))
1505-                                .collect(Collectors.toList());
1506-
1507-                if (myRaids.isEmpty() && myOccupations.isEmpty()) {
1508-                        src.sendSuccess(() -> Component.literal("No active besiegements for your colonies.")
1509-                                        .withStyle(ChatFormatting.GRAY), false);
1510-                        return 1;
1511-                }
1512-
1513-                src.sendSuccess(() -> Component.literal("=== Besiege Status ===")
1514-                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
1515-
1516-                if (!myRaids.isEmpty()) {
1517-                        src.sendSuccess(() -> Component.literal("Active Raids:").withStyle(ChatFormatting.YELLOW), false);
1518:                        for (BesiegeManager.BesiegeRaidData raid : myRaids) {
1519-                                String colonyName = cm.getAllColonies().stream()
1520-                                                .filter(c -> c.getID() == raid.colonyId)
1521-                                                .map(IColony::getName)
1522-                                                .findFirst().orElse("Colony#" + raid.colonyId);
1523-                                long elapsedMin = (System.currentTimeMillis() - raid.startTime) / 60000;
1524-                                long remainingMin = Math.max(0, raid.endTime - System.currentTimeMillis()) / 60000;
1525-                                final String line;
1526-                                if (raid.besiegingPlayerUUID.equals(playerUUID)) {
1527-                                        line = String.format("  \u2022 %s (ID: %d) \u2014 you are besieging \u2014 %dm elapsed, %dm remaining",
1528-                                                        colonyName, raid.colonyId, elapsedMin, remainingMin);
1529-                                } else {
1530-                                        ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(raid.besiegingPlayerUUID);
1531-                                        String besiegerName = besieger != null ? besieger.getName().getString() : raid.besiegingPlayerUUID.toString();
1532-                                        line = String.format("  \u2022 %s (ID: %d) \u2014 besieged by %s \u2014 %dm elapsed, %dm remaining",
1533-                                                        colonyName, raid.colonyId, besiegerName, elapsedMin, remainingMin);
1534-                                }
1535-                                src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
1536-                        }
1537-                }
1538-
1539-                if (!myOccupations.isEmpty()) {
1540-                        src.sendSuccess(() -> Component.literal("Occupied Colonies:").withStyle(ChatFormatting.YELLOW), false);
1541-                        for (BesiegeManager.BesiegeOccupationData occ : myOccupations) {
1542-                                final String line;
1543-                                if (occ.besiegingPlayerUUID.equals(playerUUID)) {
1544-                                        line = String.format("  \u2022 %s (ID: %d) \u2014 you occupy it \u2014 tribute: %d%%",
1545-                                                        occ.colonyName, occ.colonyId, occ.tributePercent);
1546-                                } else {
1547-                                        ServerPlayer besieger = src.getServer().getPlayerList().getPlayer(occ.besiegingPlayerUUID);
1548-                                        String besiegerName = besieger != null ? besieger.getName().getString() : occ.besiegingPlayerUUID.toString();
--
2921-
2922-                if (targetColony == null) {
2923-                        source.sendSuccess(() -> Component.literal("Colony '" + colonyName + "' not found!")
2924-                                        .withStyle(ChatFormatting.RED), false);
2925-                        return 0;
2926-                }
2927-
2928-                // Check if there's an active claiming raid
2929-                net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.ClaimingRaidData raidData = net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
2930-                                .getClaimingRaid(targetColony.getID());
2931-
2932-                if (raidData == null) {
2933-                        source.sendSuccess(() -> Component
2934-                                        .literal("No active claiming raid for colony " + targetColony.getName())
2935-                                        .withStyle(ChatFormatting.YELLOW), false);
2936-                        return 1;
2937-                }
2938-
2939-                // Count defenders
2940-                int aliveCitizensCount = 0;
2941-                int aliveMercenariesCount = 0;
2942-
2943-                for (Integer citizenId : raidData.hostileCitizens) {
2944-                        ICitizenData citizenData = targetColony.getCitizenManager().getCivilian(citizenId);
2945-                        if (citizenData != null && citizenData.getEntity().isPresent() &&
2946-                                        citizenData.getEntity().get().isAlive()) {
2947-                                aliveCitizensCount++;
2948-                        }
2949-                }
2950-
2951:                for (net.minecraft.world.entity.Entity mercenary : raidData.spawnedMercenaries) {
2952-                        if (mercenary.isAlive()) {
2953-                                aliveMercenariesCount++;
2954-                        }
2955-                }
2956-
2957-                final int aliveCitizens = aliveCitizensCount;
2958-                final int aliveMercenaries = aliveMercenariesCount;
2959-                final int totalDefenders = aliveCitizens + aliveMercenaries;
2960-
2961-                source.sendSuccess(() -> Component
2962-                                .literal("=== Claiming Raid Status for " + targetColony.getName() + " ===")
2963-                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
2964-                source.sendSuccess(() -> Component.literal("Claimer: " + raidData.claimingPlayerId)
2965-                                .withStyle(ChatFormatting.YELLOW), false);
2966-                source.sendSuccess(() -> Component.literal("Defenders remaining: " + totalDefenders)
2967-                                .withStyle(ChatFormatting.WHITE), false);
2968-                source.sendSuccess(() -> Component.literal("  - Citizens: " + aliveCitizens)
2969-                                .withStyle(ChatFormatting.GRAY), false);
2970-                source.sendSuccess(() -> Component.literal("  - Mercenaries: " + aliveMercenaries)
2971-                                .withStyle(ChatFormatting.GRAY), false);
2972-
2973-                long remaining = raidData.getRemainingTime();
2974-                int minutes = (int) (remaining / 60000);
2975-                int seconds = (int) ((remaining % 60000) / 1000);
2976-                source.sendSuccess(() -> Component
2977-                                .literal("Time remaining: " + String.format("%02d:%02d", minutes, seconds))
2978-                                .withStyle(ChatFormatting.AQUA), false);
2979-
2980-                // Force check victory condition
2981-                source.sendSuccess(() -> Component.literal("Forcing victory condition check...")
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 377ms:
405-                            net.minecraft.world.item.ItemStack itemStack = new net.minecraft.world.item.ItemStack(item,
406-                                    taxAwarded);
407-                            boolean added = killer.getInventory().add(itemStack);
408-                            if (!added) {
409-                                killer.drop(itemStack, false);
410-                            }
411-
412-                            Component taxMessage = Component.literal("TAX STOLEN: ")
413-                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
414-                                    .append(Component
415-                                            .literal("+" + taxAwarded + " " + item.getDescription().getString())
416-                                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
417-                                    .append(Component.literal(added ? " (added to inventory)" : " (dropped nearby)")
418-                                            .withStyle(ChatFormatting.GREEN));
419-                            killer.sendSystemMessage(taxMessage);
420-                        }
421-                    }
422-
423-                    if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
424-                        LOGGER.info("CLAIMING RAID TAX: {} stole {} tax from colony {} by killing {}",
425-                                killer.getName().getString(), taxAwarded, colony.getName(), citizenData.getName());
426-                    }
427-
428-                } catch (Exception e) {
429-                    LOGGER.error("Failed to award tax for claiming raid kill", e);
430-                }
431-            } else {
432-                Component noTaxMessage = Component.literal("No tax to steal from this colony!")
433-                        .withStyle(ChatFormatting.YELLOW);
434-                killer.sendSystemMessage(noTaxMessage);
435-            }
436-        }
437-    }
438-
439-    private static void handleMercenaryDeath(com.minecolonies.core.entity.mobs.EntityMercenary mercenary,
440-            DamageSource damageSource) {
441-        ServerPlayer killer = damageSource.getEntity() instanceof ServerPlayer player ? player : null;
442-        String killerName = killer != null ? killer.getName().getString() : "environmental damage";
443-
444-        for (ColonyClaimingRaidManager.ClaimingRaidData raidData : ColonyClaimingRaidManager.getActiveClaimingRaidIds()
445-                .stream()
446-                .map(ColonyClaimingRaidManager::getClaimingRaid)
447-                .filter(Objects::nonNull)
448-                .collect(java.util.stream.Collectors.toList())) {
449-
450:            if (raidData.spawnedMercenaries.contains(mercenary)) {
451:                raidData.spawnedMercenaries.remove(mercenary);
452-
453-                IColony colony = getColonyById(raidData.colonyId);
454-                if (colony != null) {
455-                    if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
456-                        LOGGER.info("CLAIMING RAID - Mercenary killed by {} in colony {}", killerName,
457-                                colony.getName());
458-                    }
459-
460-                    if (killer != null && killer.getUUID().equals(raidData.claimingPlayerId)) {
461-                        Component killMessage = Component.literal("MERCENARY ELIMINATED")
462-                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
463-                                .append(Component.literal("\nKilled: Mercenary").withStyle(ChatFormatting.YELLOW));
464-
465-                        killer.sendSystemMessage(killMessage);
466-                    }
467-
468-                    checkClaimingRaidVictory(raidData, colony, killerName);
469-                }
470-                break;
471-            }
472-        }
473-    }
474-
475-    private static void checkClaimingRaidVictory(ColonyClaimingRaidManager.ClaimingRaidData raidData, IColony colony,
476-            String killerName) {
477-        int remainingCitizens = 0;
478-        int remainingMercenaries = 0;
479-
480-        for (Integer citizenId : raidData.hostileCitizens) {
481-            ICitizenData remainingCitizen = colony.getCitizenManager().getCivilian(citizenId);
482-            if (remainingCitizen != null && remainingCitizen.getEntity().isPresent() &&
483-                    remainingCitizen.getEntity().get().isAlive()) {
484-                remainingCitizens++;
485-            }
486-        }
487-
488:        for (Entity mercenary : raidData.spawnedMercenaries) {
489-            if (mercenary.isAlive()) {
490-                remainingMercenaries++;
491-            }
492-        }
493-
494-        int totalRemaining = remainingCitizens + remainingMercenaries;
495-
496-        if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
497-            LOGGER.info("CLAIMING RAID PROGRESS - {} defenders remaining ({} citizens, {} mercenaries) in colony {}",
498-                    totalRemaining, remainingCitizens, remainingMercenaries, colony.getName());
499-        }
500-
501-        if (totalRemaining == 0) {
502-            if (net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
503-                LOGGER.info("CLAIMING RAID VICTORY - All defenders eliminated in colony {} by {}",
504-                        colony.getName(), killerName);
505-            }
506-
507-            ColonyClaimingRaidManager.completeClaimingRaid(raidData, true);
508-        }
509-    }
510-
511-    private static IColony getColonyById(int colonyId) {
512-        try {
513-            return com.minecolonies.api.IMinecoloniesAPI.getInstance().getColonyManager().getColonyByWorld(colonyId,
514-                    null);
515-        } catch (Exception e) {
516-            return null;
517-        }
518-    }
519-
520-    private static void handleClaimingRaidDeath(AbstractEntityCitizen citizen, IColony colony, ServerPlayer killer) {
521-        ICitizenData citizenData = citizen.getCitizenData();
522-        if (citizenData == null) {
523-            LOGGER.warn("Claiming raid death - citizen data is null");
524-            return;
525-        }
526-
527-        ColonyClaimingRaidManager.ClaimingRaidData raidData = ColonyClaimingRaidManager.getClaimingRaid(colony.getID());
528-        if (raidData == null) {
529-            LOGGER.warn("Claiming raid death detected but no raid data found for colony {}", colony.getID());
530-            return;
531-        }
532-
533-        if (net.machiavelli.minecolonytax.TaxConfig.isDebugLogging()) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "besiege|Besiege|hostileCitizenIds|spawnedMercenaries|registerAlly|active besiege" src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 348ms:
450:            if (raidData.spawnedMercenaries.contains(mercenary)) {
451:                raidData.spawnedMercenaries.remove(mercenary);
488:        for (Entity mercenary : raidData.spawnedMercenaries) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
(1) STATUS: REWORK

(2) Findings

- High: `BesiegeManager.java:358` still removes `ACTIVE_RAIDS.remove(colonyId)` after `ACTIVE_RAIDS.put(besiegerUUID, raid)`. Any launch failure after insertion leaves a stale active raid keyed by besieger UUID, blocking that player and possibly ticking partial state.

- High: `BesiegeManager.java:771` still does `ACTIVE_RAIDS.get(colonyId)`. `registerAlly` will never find raids after the re-key, so allies are not registered/granted combat permissions. With multiple raids on one colony, the method also lacks enough context to choose the intended besieger’s raid.

- Medium: `getActiveRaids()` uses `putIfAbsent`, so the returned colony-keyed view intentionally hides all but one raid per colony. That is okay only for legacy “is there a raid on this colony?” callers, but current callers treat it as the full active raid set: admin/player status in `WntCommands`, conflict-participant levitation checks in `WarEventHandler`, and login notifications can miss the second besieger.

- Low: the view construction over `ConcurrentHashMap.values()` is weakly consistent and “first” is nondeterministic. No CME risk, and probably fine on the server thread, but the selected raid per colony can vary under concurrent modification.

- Phase 2 known gaps, not counted against status: defender pools are still per-raid, last-kill-credit/race-loser cooldown semantics are not implemented, and each same-colony raid resolves independently.

(3) Fix Suggestions

- `BesiegeManager.java:358`: remove by `besiegerUUID`; preferably keep `raid` in outer scope and call `cleanupRaid(raid, false)` before removal on launch failure.

- `BesiegeManager.java:770`: change `registerAlly` to take `besiegerUUID` or explicitly apply to `getRaidsForColony(colonyId)` with clear semantics.

- `WntCommands.java:1434`, `WntCommands.java:1487`, `WarEventHandler.java:434`: use `getAllActiveRaidsByBesieger()` / `getRaidForBesieger(UUID)` where callers need all raids or a player-specific raid.
tokens used
106.629
(1) STATUS: REWORK

(2) Findings

- High: `BesiegeManager.java:358` still removes `ACTIVE_RAIDS.remove(colonyId)` after `ACTIVE_RAIDS.put(besiegerUUID, raid)`. Any launch failure after insertion leaves a stale active raid keyed by besieger UUID, blocking that player and possibly ticking partial state.

- High: `BesiegeManager.java:771` still does `ACTIVE_RAIDS.get(colonyId)`. `registerAlly` will never find raids after the re-key, so allies are not registered/granted combat permissions. With multiple raids on one colony, the method also lacks enough context to choose the intended besieger’s raid.

- Medium: `getActiveRaids()` uses `putIfAbsent`, so the returned colony-keyed view intentionally hides all but one raid per colony. That is okay only for legacy “is there a raid on this colony?” callers, but current callers treat it as the full active raid set: admin/player status in `WntCommands`, conflict-participant levitation checks in `WarEventHandler`, and login notifications can miss the second besieger.

- Low: the view construction over `ConcurrentHashMap.values()` is weakly consistent and “first” is nondeterministic. No CME risk, and probably fine on the server thread, but the selected raid per colony can vary under concurrent modification.

- Phase 2 known gaps, not counted against status: defender pools are still per-raid, last-kill-credit/race-loser cooldown semantics are not implemented, and each same-colony raid resolves independently.

(3) Fix Suggestions

- `BesiegeManager.java:358`: remove by `besiegerUUID`; preferably keep `raid` in outer scope and call `cleanupRaid(raid, false)` before removal on launch failure.

- `BesiegeManager.java:770`: change `registerAlly` to take `besiegerUUID` or explicitly apply to `getRaidsForColony(colonyId)` with clear semantics.

- `WntCommands.java:1434`, `WntCommands.java:1487`, `WarEventHandler.java:434`: use `getAllActiveRaidsByBesieger()` / `getRaidForBesieger(UUID)` where callers need all raids or a player-specific raid.
