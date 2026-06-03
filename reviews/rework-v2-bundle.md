## REWORK V2 — addressing codex follow-up findings

Codex's first rework review (reviews/rework-codex.md) marked bug #9 as PARTIAL and flagged two new MEDIUMs. This second pass closes those:

  #9 (now full) — triggerVictory's defender-would-win guard now mirrors WarSystem.checkForVictory's exact logic ((hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead)) instead of requiring both.

  MEDIUM-1 — OccupationManager.startOccupation now uses the same FCT-reverse-lookup-first pattern as ColonyTierGuard, so stale/null/placeholder owners don't misclassify primaries into TRANSFER_PENDING.

  MEDIUM-2 (acknowledged, deferred) — reclaimByOriginalOwner remains unused by current BesiegeManager.completeReclaim because the 'original owner mounts counter-besiege to reclaim their own primary' design path isn't wired end-to-end yet. Documented as a Phase 2 wiring task, not a regression.

### DIFF: TownHallDemolitionObjective.java (defenderWouldWin guard)
```diff
```

### DIFF: OccupationManager.java (FCT-first tier classification)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java b/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
index d460830..ef6fba5 100644
--- a/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
@@ -167,8 +167,20 @@ public class OccupationManager {
         // secondaries follow the legacy transfer-on-expiry flow.
         // EnablePrimaryColonyTransfer lets server owners opt back into the legacy
         // behavior for primaries too.
-        boolean isPrimary = originalOwner != null
-                && net.machiavelli.minecolonytax.FirstColonyTracker.isFirstColony(originalOwner, colonyId);
+        //
+        // Use the same canonical tier check as ColonyTierGuard: FCT reverse
+        // lookup first, then permissions-owner fallback. Otherwise stale/null/
+        // placeholder owners (abandoned colonies, system-owned, etc.) can leak
+        // a primary into the TRANSFER_PENDING flow and accidentally transfer
+        // the deed at expiry.
+        boolean isPrimary;
+        UUID trackedFirstOwner = net.machiavelli.minecolonytax.FirstColonyTracker.getFirstColonyOwner(colonyId);
+        if (trackedFirstOwner != null) {
+            isPrimary = true;
+        } else {
+            isPrimary = originalOwner != null
+                    && net.machiavelli.minecolonytax.FirstColonyTracker.isFirstColony(originalOwner, colonyId);
+        }
         OccupationMode mode;
         int durationDays;
         if (isPrimary && !TaxConfig.isPrimaryColonyTransferEnabled()) {
@@ -505,13 +517,32 @@ public class OccupationManager {
      * Manually end a tax-only occupation early — called when the owner successfully
      * mounts a counter-besiege. Restores everything to pre-occupation state.
      *
-     * @return true if an occupation was ended, false if none was active
+     * Strict guards:
+     *  - Only TAX_ONLY occupations can be reclaimed this way. TRANSFER_PENDING
+     *    occupations (secondary colonies) must follow the legacy reclaim flow
+     *    or be ended by the standard expiry/cancel paths.
+     *  - {@code reclaimerUUID} must match the recorded original owner. Prevents
+     *    arbitrary players from cancelling another player's occupation.
+     *  - Uses atomic remove so concurrent calls don't double-fire.
+     *
+     * @return true if an occupation was ended, false otherwise
      */
-    public static boolean reclaimByOriginalOwner(int colonyId) {
+    public static boolean reclaimByOriginalOwner(int colonyId, UUID reclaimerUUID) {
         OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
         if (data == null) return false;
+        if (data.getMode() != OccupationMode.TAX_ONLY) {
+            LOGGER.info("reclaimByOriginalOwner refused: occupation on colony {} is mode {} (not TAX_ONLY)",
+                    colonyId, data.getMode());
+            return false;
+        }
+        if (reclaimerUUID == null || !reclaimerUUID.toString().equals(data.originalOwnerUUID)) {
+            LOGGER.info("reclaimByOriginalOwner refused: caller {} is not the recorded original owner {} of colony {}",
+                    reclaimerUUID, data.originalOwnerUUID, colonyId);
+            return false;
+        }
 
-        ACTIVE_OCCUPATIONS.remove(colonyId);
+        // Atomic remove — bail if a concurrent caller already cleared it.
+        if (!ACTIVE_OCCUPATIONS.remove(colonyId, data)) return false;
         saveData();
 
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
```
