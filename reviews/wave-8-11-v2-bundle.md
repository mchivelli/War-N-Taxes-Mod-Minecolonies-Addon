## WAVE 8-11 v2 — followups for codex's 3 findings

  A — cleanupRaid now always clears COLONY_RAID_INDEX (index ≠ source of truth, no point gating). Tick-side cleanups (colony-null, left-radius) are now lock-step automatically.
  B — Counter-besiege reclaim now returns EARLY when reclaimByOriginalOwner succeeds. Prevents self-vassalization.
  C — BesiegeEntityInteractHandler now also subscribes to PlayerInteractEvent.EntityInteractSpecific via a shared handler. Closes the event-ordering bypass.

### DIFF: BesiegeManager.java (lock-step cleanup + reclaim early-return)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
index 959ac36..c1bb867 100644
--- a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
@@ -68,6 +68,14 @@ public class BesiegeManager {
      */
     private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
 
+    /**
+     * Secondary index: colonyId → set of besiegerUUIDs currently raiding it.
+     * Maintained in lock-step with ACTIVE_RAIDS so hot-path lookups
+     * (isActiveRaidOnColony, getRaidsForColony, container deny checks)
+     * are O(1) instead of O(activeRaids).
+     */
+    private static final Map<Integer, Set<UUID>> COLONY_RAID_INDEX = new ConcurrentHashMap<>();
+
     /** Persistent occupation records (colonyId -> occupation data). */
     private static final Map<Integer, BesiegeOccupationData> OCCUPATIONS = new ConcurrentHashMap<>();
 
@@ -88,6 +96,7 @@ public class BesiegeManager {
             cleanupRaid(raid, false);
         }
         ACTIVE_RAIDS.clear();
+        COLONY_RAID_INDEX.clear();
         if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager shutdown complete");
     }
 
@@ -119,8 +128,10 @@ public class BesiegeManager {
                     broadcastToNearbyPlayers(colony,
                             Component.literal(colony.getName() + " successfully repelled the besiege!")
                                     .withStyle(ChatFormatting.GREEN), 200);
-                    cleanupRaid(raid, false);
-                    applyCooldown(raid.besiegingPlayerUUID);
+                    // Route through completeBesiege so siege spoils + cooldown + cleanup all fire
+                    // via a single path. Previously the timeout cleaned up directly, skipping
+                    // defender-victory siege spoils entirely.
+                    completeBesiege(raid, false, colony);
                     it.remove();
                     continue;
                 }
@@ -305,6 +316,7 @@ public class BesiegeManager {
         try {
             BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
             ACTIVE_RAIDS.put(besiegerUUID, raid);
+            COLONY_RAID_INDEX.computeIfAbsent(colonyId, k -> ConcurrentHashMap.newKeySet()).add(besiegerUUID);
 
             // Grant the besieger hostile rank + combat permissions on the colony
             // so MineColonies allows the player to attack citizens.
@@ -319,6 +331,10 @@ public class BesiegeManager {
             // Spawn mercenaries
             int mercCount = spawnMercenaries(colony, besieger, raid);
 
+            // Militia upgrade reinforcements — NOT counted toward victory.
+            // Each tier adds +N% bonus militia entities scaled by current guard count.
+            int militiaUpgradeCount = spawnMilitiaUpgradeReinforcements(colony, besieger, raid, guardCount);
+
             int totalDefenders = guardCount + militiaCount + mercCount;
 
             // Apply FORTIFICATION investment: extra DAMAGE_RESISTANCE to all defenders
@@ -374,10 +390,19 @@ public class BesiegeManager {
         } catch (Exception e) {
             LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
             ACTIVE_RAIDS.remove(besiegerUUID);
+            removeFromColonyIndex(colonyId, besiegerUUID);
             return false;
         }
     }
 
+    /** Remove a besieger from the colony→raid index; drop the colony entry when empty. */
+    private static void removeFromColonyIndex(int colonyId, UUID besiegerUUID) {
+        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
+        if (set == null) return;
+        set.remove(besiegerUUID);
+        if (set.isEmpty()) COLONY_RAID_INDEX.remove(colonyId, set);
+    }
+
 
     private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
         int count = 0;
@@ -541,24 +566,145 @@ public class BesiegeManager {
     }
 
 
+    /**
+     * Spawns militia-upgrade bonus defenders proportional to current guard count.
+     * Quantity = floor(guardCount * (getMilitiaMultiplier(colonyId) - 1.0)).
+     * Tracked in raid.militiaSupport so they're despawned on raid end but NOT
+     * counted toward victory (allDefendersDead skips this set by design).
+     */
+    private static int spawnMilitiaUpgradeReinforcements(IColony colony, ServerPlayer besieger,
+            BesiegeRaidData raid, int guardCount) {
+        Level world = colony.getWorld();
+        if (!(world instanceof ServerLevel)) return 0;
+        if (guardCount <= 0) return 0;
+
+        double multiplier = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
+                .getMilitiaMultiplier(colony.getID());
+        int bonus = (int) Math.floor(guardCount * (multiplier - 1.0));
+        if (bonus <= 0) return 0;
+
+        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
+        int spawned = 0;
+        for (int i = 0; i < bonus; i++) {
+            try {
+                EntityMercenary militia = (EntityMercenary) com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
+                if (militia == null) continue;
+                BlockPos spawnPos = findSpawnPos(colony.getCenter(), world);
+                militia.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
+                militia.setTarget(besieger);
+                militia.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 0));
+                world.addFreshEntity(militia);
+                raid.militiaSupport.add(militia);
+                spawned++;
+            } catch (Exception e) {
+                LOGGER.warn("Failed to spawn militia reinforcement {} for colony {}", i, colony.getName(), e);
+            }
+        }
+        if (spawned > 0 && TaxConfig.isNormalLogging()) {
+            LOGGER.info("Spawned {} militia-upgrade reinforcements for colony {} (multiplier {})",
+                    spawned, colony.getName(),
+                    String.format(java.util.Locale.ROOT, "%.2f", multiplier));
+        }
+        return spawned;
+    }
+
     private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
         cleanupRaid(raid, true);
         applyCooldown(raid.besiegingPlayerUUID);
 
         if (attackerWon) {
+            applySiegeSpoils(raid, colony, true);
             if (raid.isReclaim) {
                 completeReclaim(raid, colony);
             } else {
                 completeBesiegeVictory(raid, colony);
             }
         } else {
+            applySiegeSpoils(raid, colony, false);
             sendToPlayer(raid.besiegingPlayerUUID,
                     Component.literal("The besiege of " + colony.getName() + " failed.")
                             .withStyle(ChatFormatting.RED));
         }
     }
 
+    /**
+     * One-shot percentage transfer between loser's and winner's primary colony treasuries.
+     * Mirrors the war-spoil semantics; configured via BesiegeSpoilPercentOfLoserTreasury.
+     *
+     * Honours both sides' treasury caps: the actual credited amount is the lesser
+     * of (computed spoil) and (winner's remaining headroom). The deduction matches
+     * that credited amount, so coins are never lost to the cap.
+     */
+    private static void applySiegeSpoils(BesiegeRaidData raid, IColony defenderColony, boolean attackerWon) {
+        int percent = TaxConfig.getBesiegeSpoilPercentOfLoserTreasury();
+        if (percent <= 0) return;
+
+        IColony besiegerColony = getPrimaryColonyOfPlayer(raid.besiegingPlayerUUID);
+        if (besiegerColony == null) return;
+
+        IColony loser = attackerWon ? defenderColony : besiegerColony;
+        IColony winner = attackerWon ? besiegerColony : defenderColony;
+        if (loser == null || winner == null) return;
+
+        int loserBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(loser.getID());
+        if (loserBalance <= 0) return;
+        int requestedSpoil = (int) Math.floor(loserBalance * (percent / 100.0));
+        if (requestedSpoil <= 0) return;
+
+        // Compute the winner's available headroom so we don't deduct coins that
+        // would be silently capped away on the credit side.
+        int winnerBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(winner.getID());
+        int winnerCap = net.machiavelli.minecolonytax.economy.TreasuryManager.getEffectiveMaxCapacity(winner.getID());
+        int headroom = Math.max(0, winnerCap - winnerBalance);
+        int actualSpoil = Math.min(requestedSpoil, headroom);
+        if (actualSpoil <= 0) return;
+
+        net.machiavelli.minecolonytax.economy.TreasuryManager.deductFromTreasury(loser.getID(), actualSpoil);
+        net.machiavelli.minecolonytax.economy.TreasuryManager.addToTreasury(winner.getID(), actualSpoil);
+
+        if (TaxConfig.isNormalLogging()) {
+            LOGGER.info("Siege spoils ({}%): {} → {} = {} (requested {}, headroom {})",
+                    percent, loser.getName(), winner.getName(), actualSpoil, requestedSpoil, headroom);
+        }
+
+        // Notify both sides with the actual transferred amount.
+        UUID winnerOwner = winner.getPermissions().getOwner();
+        UUID loserOwner = loser.getPermissions().getOwner();
+        Component winMsg = Component.literal("Siege spoils: " + actualSpoil + " coins transferred from "
+                + loser.getName() + " to " + winner.getName() + ".")
+                .withStyle(ChatFormatting.GOLD);
+        Component loseMsg = Component.literal("Siege fine: " + actualSpoil + " coins paid from "
+                + loser.getName() + " to " + winner.getName() + ".")
+                .withStyle(ChatFormatting.RED);
+        if (winnerOwner != null) sendToPlayer(winnerOwner, winMsg);
+        if (loserOwner != null && !loserOwner.equals(winnerOwner)) sendToPlayer(loserOwner, loseMsg);
+    }
+
     private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
+        // Counter-besiege reclaim handoff: if the besieger is the original owner
+        // of a TAX_ONLY war-occupation on this colony, clear that occupation
+        // and SHORT-CIRCUIT — the colony is already theirs, vassalizing it to
+        // themselves would create a self-vassal nonsense state.
+        try {
+            boolean reclaimed = net.machiavelli.minecolonytax.occupation.OccupationManager
+                    .reclaimByOriginalOwner(colony.getID(), raid.besiegingPlayerUUID);
+            if (reclaimed) {
+                if (TaxConfig.isNormalLogging()) {
+                    LOGGER.info("Besiege victory was a counter-besiege reclaim: {} reclaimed TAX_ONLY occupation of {}",
+                            raid.besiegingPlayerUUID, colony.getName());
+                }
+                sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
+                        "You have successfully reclaimed " + colony.getName() + "!")
+                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
+                broadcastToNearbyPlayers(colony,
+                        Component.literal(colony.getName() + " has been reclaimed by its original owner!")
+                                .withStyle(ChatFormatting.GREEN), 300);
+                return; // No vassalization — the owner is whole again.
+            }
+        } catch (Exception e) {
+            LOGGER.warn("Counter-besiege reclaim handoff failed for colony {}", colony.getName(), e);
+        }
+
         int tributePct = TaxConfig.getBesiegeTributePercent();
         int durationHours = TaxConfig.getBesiegeTributeDurationHours();
 
@@ -663,6 +809,15 @@ public class BesiegeManager {
                 }
             }
 
+            // Despawn militia-upgrade reinforcements (NOT victory-counted)
+            for (Entity militia : raid.militiaSupport) {
+                try {
+                    if (militia.isAlive()) militia.remove(Entity.RemovalReason.DISCARDED);
+                } catch (Exception e) {
+                    LOGGER.warn("Failed to despawn militia reinforcement after besiege", e);
+                }
+            }
+
             // Despawn mercenaries
             for (Entity merc : raid.spawnedMercenaries) {
                 try {
@@ -683,6 +838,11 @@ public class BesiegeManager {
             raid.bossEvent = null;
         }
 
+        // Always clear the colony index — it's a secondary lookup, not the source
+        // of truth. ACTIVE_RAIDS removal is gated so tick callers that own the
+        // iterator can do it.remove() themselves; the index gets cleaned either
+        // way to avoid stale colony→besieger entries blocking subsequent raids.
+        removeFromColonyIndex(raid.colonyId, raid.besiegingPlayerUUID);
         if (removeFromMap) {
             ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
         }
@@ -753,17 +913,19 @@ public class BesiegeManager {
     }
 
     public static boolean isActiveRaidOnColony(int colonyId) {
-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
-            if (raid.colonyId == colonyId) return true;
-        }
-        return false;
+        // Hot path — O(1) via COLONY_RAID_INDEX.
+        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
+        return set != null && !set.isEmpty();
     }
 
-    /** All currently active besiege raids targeting this colony. */
+    /** All currently active besiege raids targeting this colony. O(matches). */
     public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
-        List<BesiegeRaidData> matches = new ArrayList<>();
-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
-            if (raid.colonyId == colonyId) matches.add(raid);
+        Set<UUID> besiegerUUIDs = COLONY_RAID_INDEX.get(colonyId);
+        if (besiegerUUIDs == null || besiegerUUIDs.isEmpty()) return java.util.Collections.emptyList();
+        List<BesiegeRaidData> matches = new ArrayList<>(besiegerUUIDs.size());
+        for (UUID uuid : besiegerUUIDs) {
+            BesiegeRaidData raid = ACTIVE_RAIDS.get(uuid);
+            if (raid != null) matches.add(raid);
         }
         return matches;
     }
@@ -1100,6 +1262,12 @@ public class BesiegeManager {
         public final long endTime;
         public final Set<Integer> hostileCitizenIds = ConcurrentHashMap.newKeySet();
         public final Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
+        /**
+         * Militia upgrade reinforcements. Tracked separately from spawnedMercenaries
+         * because by design they extend combat without counting toward victory.
+         * allDefendersDead and countAliveDefenders intentionally ignore this set.
+         */
+        public final Set<Entity> militiaSupport = ConcurrentHashMap.newKeySet();
         public final Set<UUID> alliedPlayers = ConcurrentHashMap.newKeySet();
         public final BlockPos colonyCenter;
         public ServerBossEvent bossEvent;
```

### DIFF: BesiegeEntityInteractHandler.java (EntityInteractSpecific too)
```diff
```
