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
session id: 019e5d15-6b29-7863-a095-8f63f9d454ab
--------
user
Targeted follow-up review. Verify only these three fixes from the previous codex round: (A) cleanupRaid unconditionally removes from COLONY_RAID_INDEX so tick paths can't leave stale entries, (B) completeBesiegeVictory returns early when reclaimByOriginalOwner succeeds, preventing self-vassalization, (C) BesiegeEntityInteractHandler now covers both EntityInteract and EntityInteractSpecific via a shared private handler. Respond: STATUS APPROVE/REWORK, VERIFIED/STILL-BROKEN for each, up to 2 fix suggestions if any. Max 200 words.

<stdin>
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
</stdin>
codex
I’ll verify the checked-in files against the three claims, using GitNexus where available and plain reads for the exact handler/body details.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff -- src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "cleanupRaid|COLONY_RAID_INDEX|removeFromColonyIndex|completeBesiegeVictory|reclaimByOriginalOwner" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "EntityInteractSpecific|EntityInteract|shared|handle" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 310ms:
77:    private static final Map<Integer, Set<UUID>> COLONY_RAID_INDEX = new ConcurrentHashMap<>();
96:            cleanupRaid(raid, false);
99:        COLONY_RAID_INDEX.clear();
114:                    cleanupRaid(raid, false);
148:                        cleanupRaid(raid, false);
319:            COLONY_RAID_INDEX.computeIfAbsent(colonyId, k -> ConcurrentHashMap.newKeySet()).add(besiegerUUID);
393:            removeFromColonyIndex(colonyId, besiegerUUID);
399:    private static void removeFromColonyIndex(int colonyId, UUID besiegerUUID) {
400:        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
403:        if (set.isEmpty()) COLONY_RAID_INDEX.remove(colonyId, set);
612:        cleanupRaid(raid, true);
620:                completeBesiegeVictory(raid, colony);
683:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
690:                    .reclaimByOriginalOwner(colony.getID(), raid.besiegingPlayerUUID);
777:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
845:        removeFromColonyIndex(raid.colonyId, raid.besiegingPlayerUUID);
916:        // Hot path â€” O(1) via COLONY_RAID_INDEX.
917:        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
923:        Set<UUID> besiegerUUIDs = COLONY_RAID_INDEX.get(colonyId);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 318ms:
diff --git a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
index 959ac36..c1bb867 100644
--- a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
@@ -68,6 +68,14 @@ public class BesiegeManager {
      */
     private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
 
+    /**
+     * Secondary index: colonyId â†’ set of besiegerUUIDs currently raiding it.
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
 
+            // Militia upgrade reinforcements â€” NOT counted toward victory.
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
 
+    /** Remove a besieger from the colonyâ†’raid index; drop the colony entry when empty. */
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
+            LOGGER.info("Siege spoils ({}%): {} â†’ {} = {} (requested {}, headroom {})",
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
+        // and SHORT-CIRCUIT â€” the colony is already theirs, vassalizing it to
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
+                return; // No vassalization â€” the owner is whole again.
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
 
+        // Always clear the colony index â€” it's a secondary lookup, not the source
+        // of truth. ACTIVE_RAIDS removal is gated so tick callers that own the
+        // iterator can do it.remove() themselves; the index gets cleaned either
+        // way to avoid stale colonyâ†’besieger entries blocking subsequent raids.
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
+        // Hot path â€” O(1) via COLONY_RAID_INDEX.
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 317ms:
31:public class BesiegeEntityInteractHandler {
33:    private static final Logger LOGGER = LogManager.getLogger(BesiegeEntityInteractHandler.class);
40:    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
41:        handleEntityInteraction(event, event.getTarget(), event.getEntity());
45:     * EntityInteractSpecific fires before EntityInteract for some interactions
50:    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
51:        handleEntityInteraction(event, event.getTarget(), event.getEntity());
54:    private static void handleEntityInteraction(PlayerInteractEvent event, Entity target, Entity sourceEntity) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
GitNexus tools are not exposed in this session via tool discovery, so I’m checking the repository directly. So far, the BesiegeManager diff has the expected index removal and early return; I’m checking the surrounding control flow and the handler signatures now.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 14 "private static void completeBesiegeVictory|reclaimByOriginalOwner|createVassal|startVassal|vassal" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 8 \"cleanupRaid\\(raid|private static void cleanupRaid|removeFromColonyIndex\\(raid\\.colonyId|isActiveRaidOnColony|getRaidsForColony\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 287ms:
5-import com.google.gson.reflect.TypeToken;
6-import com.minecolonies.api.IMinecoloniesAPI;
7-import com.minecolonies.api.colony.ICitizenData;
8-import com.minecolonies.api.colony.IColony;
9-import com.minecolonies.api.colony.IColonyManager;
10-import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
11-import com.minecolonies.api.colony.permissions.Action;
12-import com.minecolonies.api.colony.permissions.IPermissions;
13-import com.minecolonies.api.colony.permissions.Rank;
14-import com.minecolonies.core.entity.mobs.EntityMercenary;
15-import net.machiavelli.minecolonytax.FirstColonyTracker;
16-import net.machiavelli.minecolonytax.TaxConfig;
17-import net.machiavelli.minecolonytax.WarSystem;
18-import net.machiavelli.minecolonytax.militia.MilitiaAttackGoal;
19:import net.machiavelli.minecolonytax.vassalization.VassalManager;
20-import net.minecraft.ChatFormatting;
21-import net.minecraft.core.BlockPos;
22-import net.minecraft.network.chat.Component;
23-import net.minecraft.server.MinecraftServer;
24-import net.minecraft.server.level.ServerBossEvent;
25-import net.minecraft.server.level.ServerLevel;
26-import net.minecraft.server.level.ServerPlayer;
27-import net.minecraft.world.BossEvent;
28-import net.minecraft.world.effect.MobEffectInstance;
29-import net.minecraft.world.effect.MobEffects;
30-import net.minecraft.world.entity.Entity;
31-import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
32-import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
33-import net.minecraft.world.entity.player.Player;
--
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
51: * that grant tax vassalage on victory, and the reclaim flow for the former owner.
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
--
235-                    "Target colony is too small to besiege (needs at least "
236-                            + TaxConfig.getBesiegeMinColonySize() + " citizens).")
237-                    .withStyle(ChatFormatting.RED));
238-            return false;
239-        }
240-
241-        // 7. Cannot besiege a colony that is currently in an active war
242-        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
243-            besieger.sendSystemMessage(Component.literal(
244-                    "This colony is already engaged in an active war. Besiege is not available during wartime.")
245-                    .withStyle(ChatFormatting.RED));
246-            return false;
247-        }
248-
249:        // 8. Cannot besiege a colony that is already a vassal of this player
250-        if (VassalManager.isColonyVassal(colonyId)
251-                && VassalManager.getVassalOverlordUUID(colonyId) != null
252-                && VassalManager.getVassalOverlordUUID(colonyId).equals(besiegerUUID)) {
253:            besieger.sendSystemMessage(Component.literal("This colony is already your vassal.")
254-                    .withStyle(ChatFormatting.RED));
255-            return false;
256-        }
257-
258-        return launchRaid(colony, besieger, false);
259-    }
260-
261-    /**
262-     * Start a reclaim raid â€” former owner/officer taking back their besieged colony.
263-     */
264-    public static boolean startReclaim(IColony colony, ServerPlayer reclaimingPlayer) {
265-        if (!TaxConfig.isBesiegeSystemEnabled()) {
266-            reclaimingPlayer.sendSystemMessage(Component.literal("The besiege system is disabled.")
267-                    .withStyle(ChatFormatting.RED));
--
669-
670-        // Notify both sides with the actual transferred amount.
671-        UUID winnerOwner = winner.getPermissions().getOwner();
672-        UUID loserOwner = loser.getPermissions().getOwner();
673-        Component winMsg = Component.literal("Siege spoils: " + actualSpoil + " coins transferred from "
674-                + loser.getName() + " to " + winner.getName() + ".")
675-                .withStyle(ChatFormatting.GOLD);
676-        Component loseMsg = Component.literal("Siege fine: " + actualSpoil + " coins paid from "
677-                + loser.getName() + " to " + winner.getName() + ".")
678-                .withStyle(ChatFormatting.RED);
679-        if (winnerOwner != null) sendToPlayer(winnerOwner, winMsg);
680-        if (loserOwner != null && !loserOwner.equals(winnerOwner)) sendToPlayer(loserOwner, loseMsg);
681-    }
682-
683:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
684-        // Counter-besiege reclaim handoff: if the besieger is the original owner
685-        // of a TAX_ONLY war-occupation on this colony, clear that occupation
686:        // and SHORT-CIRCUIT â€” the colony is already theirs, vassalizing it to
687:        // themselves would create a self-vassal nonsense state.
688-        try {
689-            boolean reclaimed = net.machiavelli.minecolonytax.occupation.OccupationManager
690:                    .reclaimByOriginalOwner(colony.getID(), raid.besiegingPlayerUUID);
691-            if (reclaimed) {
692-                if (TaxConfig.isNormalLogging()) {
693-                    LOGGER.info("Besiege victory was a counter-besiege reclaim: {} reclaimed TAX_ONLY occupation of {}",
694-                            raid.besiegingPlayerUUID, colony.getName());
695-                }
696-                sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
697-                        "You have successfully reclaimed " + colony.getName() + "!")
698-                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
699-                broadcastToNearbyPlayers(colony,
700-                        Component.literal(colony.getName() + " has been reclaimed by its original owner!")
701-                                .withStyle(ChatFormatting.GREEN), 300);
702:                return; // No vassalization â€” the owner is whole again.
703-            }
704-        } catch (Exception e) {
705-            LOGGER.warn("Counter-besiege reclaim handoff failed for colony {}", colony.getName(), e);
706-        }
707-
708-        int tributePct = TaxConfig.getBesiegeTributePercent();
709-        int durationHours = TaxConfig.getBesiegeTributeDurationHours();
710-
711:        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
712-
713:        if (vassalized) {
714-            // Store occupation record
715-            UUID ownerUUID = colony.getPermissions().getOwner();
716-            BesiegeOccupationData occ = new BesiegeOccupationData(
717-                    colony.getID(), colony.getName(),
718-                    raid.besiegingPlayerUUID, ownerUUID,
719-                    System.currentTimeMillis(), tributePct);
720-            OCCUPATIONS.put(colony.getID(), occ);
721-            saveData();
722-
723-            // Notify former owner
724-            if (ownerUUID != null) {
725-                String besiegerName = getPlayerName(raid.besiegingPlayerUUID);
726-                sendToPlayer(ownerUUID, Component.literal(
727-                        "Your colony " + colony.getName() + " has been besieged by " + besiegerName
--
729-                                + "Use /wnt besiege " + colony.getName() + " to reclaim it.")
730-                        .withStyle(ChatFormatting.RED));
731-            }
732-
733-            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
734-                    "Besiege victory! " + colony.getName() + " now pays you " + tributePct
735-                            + "% in tribute" + (durationHours > 0 ? " for " + durationHours + "h" : "") + ".")
736-                    .withStyle(ChatFormatting.GREEN));
737-
738-            broadcastToNearbyPlayers(colony,
739-                    Component.literal(colony.getName() + " has fallen under besiege occupation!")
740-                            .withStyle(ChatFormatting.DARK_RED), 300);
741-
742-            if (TaxConfig.isNormalLogging())
743:                LOGGER.info("Besiege victory: colony {} vassalized to {} at {}% tribute",
744-                        colony.getName(), getPlayerName(raid.besiegingPlayerUUID), tributePct);
745-        } else {
746:            // Colony was already a vassal (edge case) â€” just notify
747-            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
748:                    "Besiege raid completed but vassalization could not be established (colony may already be a vassal).")
749-                    .withStyle(ChatFormatting.YELLOW));
750-        }
751-    }
752-
753-    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
754-        int colonyId = colony.getID();
755-
756:        // Remove vassalization
757-        VassalManager.removeVassalRelation(colonyId);
758-
759-        // Remove occupation record
760-        OCCUPATIONS.remove(colonyId);
761-        saveData();
762-
763-        sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
764-                "Reclaim successful! " + colony.getName() + " is free from occupation.")
765-                .withStyle(ChatFormatting.GREEN));
766-
767-        broadcastToNearbyPlayers(colony,
768-                Component.literal(colony.getName() + " has been reclaimed by its owner!")
769-                        .withStyle(ChatFormatting.GOLD), 300);
770-
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 289ms:
54-
55-    private static final Logger LOGGER = LogManager.getLogger(BesiegeManager.class);
56-    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
57-    private static final String STORAGE_FILE = "config/warntax/besieged_colonies.json";
58-
59-    /**
60-     * Active besiege raids, keyed by besieger UUID. Each besieger has at most one
61-     * active raid at a time (solo combat rule). Multiple besiegers may concurrently
62:     * raid the same colony â€” look them up by colony with {@link #getRaidsForColony(int)}.
63-     *
64-     * Phase 2 follow-up: defender pool is still per-raid, not shared across
65-     * concurrent besiegers on the same colony. Each besieger currently spawns its
66-     * own mercs and tracks its own hostile-citizen set; last-kill-credit semantics
67-     * are not yet implemented.
68-     */
69-    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
70-
71-    /**
72-     * Secondary index: colonyId â†’ set of besiegerUUIDs currently raiding it.
73-     * Maintained in lock-step with ACTIVE_RAIDS so hot-path lookups
74:     * (isActiveRaidOnColony, getRaidsForColony, container deny checks)
75-     * are O(1) instead of O(activeRaids).
76-     */
77-    private static final Map<Integer, Set<UUID>> COLONY_RAID_INDEX = new ConcurrentHashMap<>();
78-
79-    /** Persistent occupation records (colonyId -> occupation data). */
80-    private static final Map<Integer, BesiegeOccupationData> OCCUPATIONS = new ConcurrentHashMap<>();
81-
82-    /** Per-player cooldown map (playerUUID -> timestamp when cooldown expires). */
--
88-        SERVER = server;
89-        loadData(server);
90-        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager initialized");
91-    }
92-
93-    public static void shutdown() {
94-        saveData();
95-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
96:            cleanupRaid(raid, false);
97-        }
98-        ACTIVE_RAIDS.clear();
99-        COLONY_RAID_INDEX.clear();
100-        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager shutdown complete");
101-    }
102-
103-    public static void tick() {
104-        if (ACTIVE_RAIDS.isEmpty()) return;
--
106-        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
107-            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
108-            BesiegeRaidData raid = entry.getValue();
109-
110-            try {
111-                IColony colony = getColonyById(raid.colonyId);
112-                if (colony == null) {
113-                    LOGGER.warn("BesiegeManager.tick: colony {} not found, ending raid", raid.colonyId);
114:                    cleanupRaid(raid, false);
115-                    it.remove();
116-                    continue;
117-                }
118-
119-                ServerPlayer besieger = SERVER.getPlayerList().getPlayer(raid.besiegingPlayerUUID);
120-
121-                // --- Timer expired: defenders win ---
122-                if (System.currentTimeMillis() >= raid.endTime) {
--
140-                if (besieger != null) {
141-                    BlockPos center = colony.getCenter();
142-                    double dist = besieger.distanceToSqr(center.getX(), center.getY(), center.getZ());
143-                    int maxRadius = TaxConfig.getBesiegePlayerStayRadius();
144-                    if (dist > (double) maxRadius * maxRadius) {
145-                        besieger.sendSystemMessage(Component.literal(
146-                                "You left the besiege area â€” the raid has been cancelled!")
147-                                .withStyle(ChatFormatting.RED));
148:                        cleanupRaid(raid, false);
149-                        applyCooldown(raid.besiegingPlayerUUID);
150-                        it.remove();
151-                        continue;
152-                    }
153-
154-                    // Track allies: anyone who recently damaged a defender
155-                    // (ally tracking is done in the kill/hurt event â€” see RaidKillTracker integration)
156-                }
--
604-            LOGGER.info("Spawned {} militia-upgrade reinforcements for colony {} (multiplier {})",
605-                    spawned, colony.getName(),
606-                    String.format(java.util.Locale.ROOT, "%.2f", multiplier));
607-        }
608-        return spawned;
609-    }
610-
611-    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
612:        cleanupRaid(raid, true);
613-        applyCooldown(raid.besiegingPlayerUUID);
614-
615-        if (attackerWon) {
616-            applySiegeSpoils(raid, colony, true);
617-            if (raid.isReclaim) {
618-                completeReclaim(raid, colony);
619-            } else {
620-                completeBesiegeVictory(raid, colony);
--
769-                        .withStyle(ChatFormatting.GOLD), 300);
770-
771-        if (TaxConfig.isNormalLogging())
772-            LOGGER.info("Besiege reclaim: colony {} reclaimed by {}", colony.getName(),
773-                    getPlayerName(raid.besiegingPlayerUUID));
774-    }
775-
776-
777:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
778-        IColony colony = getColonyById(raid.colonyId);
779-        if (colony != null) {
780-            // Revoke combat permissions from the besieger (and any allies)
781-            revokeBesiegeCombatPermissions(colony, raid.besiegingPlayerUUID);
782-            for (UUID ally : raid.alliedPlayers) {
783-                revokeBesiegeCombatPermissions(colony, ally);
784-            }
785-
--
837-            }
838-            raid.bossEvent = null;
839-        }
840-
841-        // Always clear the colony index â€” it's a secondary lookup, not the source
842-        // of truth. ACTIVE_RAIDS removal is gated so tick callers that own the
843-        // iterator can do it.remove() themselves; the index gets cleaned either
844-        // way to avoid stale colonyâ†’besieger entries blocking subsequent raids.
845:        removeFromColonyIndex(raid.colonyId, raid.besiegingPlayerUUID);
846-        if (removeFromMap) {
847-            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
848-        }
849-    }
850-
851-
852-    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
853-        try {
--
907-        }
908-    }
909-
910-
911-    public static boolean isColonyBesieged(int colonyId) {
912-        return OCCUPATIONS.containsKey(colonyId);
913-    }
914-
915:    public static boolean isActiveRaidOnColony(int colonyId) {
916-        // Hot path â€” O(1) via COLONY_RAID_INDEX.
917-        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
918-        return set != null && !set.isEmpty();
919-    }
920-
921-    /** All currently active besiege raids targeting this colony. O(matches). */
922:    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
923-        Set<UUID> besiegerUUIDs = COLONY_RAID_INDEX.get(colonyId);
924-        if (besiegerUUIDs == null || besiegerUUIDs.isEmpty()) return java.util.Collections.emptyList();
925-        List<BesiegeRaidData> matches = new ArrayList<>(besiegerUUIDs.size());
926-        for (UUID uuid : besiegerUUIDs) {
927-            BesiegeRaidData raid = ACTIVE_RAIDS.get(uuid);
928-            if (raid != null) matches.add(raid);
929-        }
930-        return matches;
--
953-     * regardless of which besieger they're supporting.
954-     *
955-     * Note: the new Siege SMP solo-besiege rule (step 5) blocks attacker-side
956-     * allies via a damage shield. This method remains for any defender-ally
957-     * tracking and for legacy callers; the besiege-allies config still gates it.
958-     */
959-    public static void registerAlly(int colonyId, UUID allyUUID) {
960-        if (!TaxConfig.isBesiegeAlliesEnabled()) return;
961:        List<BesiegeRaidData> raids = getRaidsForColony(colonyId);
962-        if (raids.isEmpty()) return;
963-        IColony colony = getColonyById(colonyId);
964-        for (BesiegeRaidData raid : raids) {
965-            if (raid.alliedPlayers.add(allyUUID) && colony != null) {
966-                grantBesiegeCombatPermissions(colony, allyUUID);
967-            }
968-        }
969-    }
--
979-    }
980-
981-    /**
982-     * Backward-compatible view of active raids keyed by colonyId.
983-     *
984-     * Since multi-besieger support landed, the internal storage is keyed by
985-     * besieger UUID. This view returns at most ONE raid per colony (the first
986-     * one encountered). Callers that need ALL raids for a colony must use
987:     * {@link #getRaidsForColony(int)}; callers that need a specific besieger's
988-     * raid should use {@link #getRaidForBesieger(UUID)}.
989-     */
990-    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
991-        Map<Integer, BesiegeRaidData> view = new HashMap<>();
992-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
993-            view.putIfAbsent(raid.colonyId, raid);
994-        }
995-        return Collections.unmodifiableMap(view);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 332ms:
package net.machiavelli.minecolonytax.besiege;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Completes the besiege interaction profile: during a besiege, right-clicking
 * a villager (trade) or a MineColonies citizen is denied for any player on the
 * besieged colony's combat radius. Combat-only ƒ?" no trading, no recruiting,
 * no information gathering through normal NPC interactions.
 *
 * Pairs with the container-block deny in BlockInteractionFilterHandler.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BesiegeEntityInteractHandler {

    private static final Logger LOGGER = LogManager.getLogger(BesiegeEntityInteractHandler.class);

    // Throttle the deny message so spam-clicking doesn't flood chat
    private static final Map<UUID, Long> LAST_DENY_MESSAGE = new HashMap<>();
    private static final long DENY_MESSAGE_COOLDOWN_MS = 3000;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handleEntityInteraction(event, event.getTarget(), event.getEntity());
    }

    /**
     * EntityInteractSpecific fires before EntityInteract for some interactions
     * (notably villager trade UI on some mod combos). Cover both events so the
     * besiege lockdown can't be bypassed by event-ordering quirks.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handleEntityInteraction(event, event.getTarget(), event.getEntity());
    }

    private static void handleEntityInteraction(PlayerInteractEvent event, Entity target, Entity sourceEntity) {
        if (!TaxConfig.isBesiegeSystemEnabled()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(sourceEntity instanceof ServerPlayer player)) return;

        boolean isVillager = target instanceof AbstractVillager;
        boolean isCitizen = target instanceof AbstractEntityCitizen;
        if (!isVillager && !isCitizen) return;

        // Find any active besiege the player is involved in (either as besieger
        // or as a defender on the besieged colony).
        int besiegedColonyId = -1;

        // Is the player a besieger?
        BesiegeManager.BesiegeRaidData own = BesiegeManager.getRaidForBesieger(player.getUUID());
        if (own != null) besiegedColonyId = own.colonyId;

        // If not, are they inside a colony that's being besieged?
        if (besiegedColonyId < 0 && isCitizen) {
            AbstractEntityCitizen citizen = (AbstractEntityCitizen) target;
            try {
                var data = citizen.getCitizenData();
                if (data != null && data.getColony() != null) {
                    int cid = data.getColony().getID();
                    if (BesiegeManager.isActiveRaidOnColony(cid)) besiegedColonyId = cid;
                }
            } catch (Exception ignored) {}
        }

        if (besiegedColonyId < 0) return;

        // Deny ƒ?" combat-only during besiege.
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);

        long now = System.currentTimeMillis();
        Long last = LAST_DENY_MESSAGE.get(player.getUUID());
        if (last == null || now - last >= DENY_MESSAGE_COOLDOWN_MS) {
            LAST_DENY_MESSAGE.put(player.getUUID(), now);
            String label = isVillager ? "trade with this villager" : "interact with this citizen";
            player.sendSystemMessage(Component.literal(
                    "You cannot " + label + " during a besiege ƒ?" combat only.")
                    .withStyle(ChatFormatting.RED));
        }
        if (TaxConfig.isDebugLogging()) {
            LOGGER.debug("BESIEGE DENIED (ENTITY INTERACT): {} blocked from interacting with {} on colony {}",
                    player.getName().getString(), target.getClass().getSimpleName(), besiegedColonyId);
        }
    }
}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"ACTIVE_RAIDS\\.(remove|clear|put)|COLONY_RAID_INDEX\\.(remove|clear|put|computeIfAbsent)|cleanupRaid\\(\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"completeBesiegeVictory\\(|completeBesiege\\(|completeReclaim\\(\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"EntityInteractSpecific|onEntityInteractSpecific|handleEntityInteraction\\(\" src/main/java/net/machiavelli/minecolonytax -g \"*.java\"" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 294ms:
96:            cleanupRaid(raid, false);
98:        ACTIVE_RAIDS.clear();
99:        COLONY_RAID_INDEX.clear();
114:                    cleanupRaid(raid, false);
148:                        cleanupRaid(raid, false);
318:            ACTIVE_RAIDS.put(besiegerUUID, raid);
319:            COLONY_RAID_INDEX.computeIfAbsent(colonyId, k -> ConcurrentHashMap.newKeySet()).add(besiegerUUID);
392:            ACTIVE_RAIDS.remove(besiegerUUID);
403:        if (set.isEmpty()) COLONY_RAID_INDEX.remove(colonyId, set);
612:        cleanupRaid(raid, true);
777:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
847:            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 286ms:
134:                    completeBesiege(raid, false, colony);
162:                    completeBesiege(raid, true, colony);
611:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
618:                completeReclaim(raid, colony);
620:                completeBesiegeVictory(raid, colony);
683:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
753:    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 307ms:
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeEntityInteractHandler.java:41:        handleEntityInteraction(event, event.getTarget(), event.getEntity());
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeEntityInteractHandler.java:45:     * EntityInteractSpecific fires before EntityInteract for some interactions
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeEntityInteractHandler.java:50:    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeEntityInteractHandler.java:51:        handleEntityInteraction(event, event.getTarget(), event.getEntity());
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeEntityInteractHandler.java:54:    private static void handleEntityInteraction(PlayerInteractEvent event, Entity target, Entity sourceEntity) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 6 \"allDefendersDead|completeBesiege\\(raid, true\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 302ms:
153-
154-                    // Track allies: anyone who recently damaged a defender
155-                    // (ally tracking is done in the kill/hurt event â€” see RaidKillTracker integration)
156-                }
157-
158-                // --- Victory: all defenders dead ---
159:                if (allDefendersDead(raid, colony)) {
160-                    if (TaxConfig.isNormalLogging())
161-                        LOGGER.info("Besiege raid on colony {} successful â€” besieger wins", colony.getName());
162:                    completeBesiege(raid, true, colony);
163-                    it.remove();
164-                    continue;
165-                }
166-
167-                // --- Update boss bar ---
168-                updateBossBar(raid, colony);
--
567-
568-
569-    /**
570-     * Spawns militia-upgrade bonus defenders proportional to current guard count.
571-     * Quantity = floor(guardCount * (getMilitiaMultiplier(colonyId) - 1.0)).
572-     * Tracked in raid.militiaSupport so they're despawned on raid end but NOT
573:     * counted toward victory (allDefendersDead skips this set by design).
574-     */
575-    private static int spawnMilitiaUpgradeReinforcements(IColony colony, ServerPlayer besieger,
576-            BesiegeRaidData raid, int guardCount) {
577-        Level world = colony.getWorld();
578-        if (!(world instanceof ServerLevel)) return 0;
579-        if (guardCount <= 0) return 0;
--
1007-
1008-    public static Map<Integer, BesiegeOccupationData> getAllOccupations() {
1009-        return Collections.unmodifiableMap(OCCUPATIONS);
1010-    }
1011-
1012-
1013:    private static boolean allDefendersDead(BesiegeRaidData raid, IColony colony) {
1014-        return countAliveDefenders(raid, colony) == 0;
1015-    }
1016-
1017-    private static int countAliveDefenders(BesiegeRaidData raid, IColony colony) {
1018-        int alive = 0;
1019-        // Citizens
--
1262-        public final long endTime;
1263-        public final Set<Integer> hostileCitizenIds = ConcurrentHashMap.newKeySet();
1264-        public final Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
1265-        /**
1266-         * Militia upgrade reinforcements. Tracked separately from spawnedMercenaries
1267-         * because by design they extend combat without counting toward victory.
1268:         * allDefendersDead and countAliveDefenders intentionally ignore this set.
1269-         */
1270-        public final Set<Entity> militiaSupport = ConcurrentHashMap.newKeySet();
1271-        public final Set<UUID> alliedPlayers = ConcurrentHashMap.newKeySet();
1272-        public final BlockPos colonyCenter;
1273-        public ServerBossEvent bossEvent;
1274-        public final boolean isReclaim;
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git status --short' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 665ms:
MM .gitignore
A  AGENTS.md
M  CHANGELOG.md
A  CLAUDE.md
D  Patchouli
D  TestMigration.java
M  build.gradle
 D build_final_revert.log
 D build_verification_fix.log
 D crash-2026-02-21_16.35.56-server.txt
M  gradle.properties
 D hs_err_pid20576.log
 D hs_err_pid38152.log
 D "latest (2).log"
M  minecolonies
D  run/banned-ips.json
D  run/banned-players.json
D  run/blueprints/dev/pack.json
D  run/blueprints/player1/pack.json
D  run/colony_info_log.txt
D  run/config/SDMShop/customization.json
D  run/config/SDMShop/sdmshop-common.snbt
D  run/config/SDMShop/sdmshop.snbt
D  run/config/fml.toml
D  run/config/forge-client.toml
D  run/config/jei/blacklist.cfg
D  run/config/jei/ingredient-list-mod-sort-order.ini
D  run/config/jei/ingredient-list-type-sort-order.ini
D  run/config/jei/jei-client.ini
D  run/config/jei/jei-colors.ini
D  run/config/jei/jei-debug.ini
D  run/config/jei/jei-mod-id-format.ini
D  run/config/jei/recipe-category-sort-order.ini
D  run/config/minecolonies-client.toml
D  run/config/minecolonies-common-1.toml.bak
D  run/config/minecolonies-common-2.toml.bak
D  run/config/minecolonies-common-3.toml.bak
D  run/config/minecolonies-common-4.toml.bak
D  run/config/minecolonies-common-5.toml.bak
D  run/config/minecolonies-common.toml
D  run/config/recruits-client.toml
D  run/config/sidebar_buttons.json
D  run/config/structurize-client-1.toml.bak
D  run/config/structurize-client-2.toml.bak
D  run/config/structurize-client.toml
D  run/config/warntax/colonyTaxData.json
D  run/config/warntax/colony_history.json
D  run/config/warntax/lastTaxGeneration.json
D  run/config/warntax/minecolonytax-1.toml.bak
D  run/config/warntax/minecolonytax-2.toml.bak
D  run/config/warntax/minecolonytax-3.toml.bak
D  run/config/warntax/minecolonytax-4.toml.bak
D  run/config/warntax/minecolonytax-5.toml.bak
D  run/config/warntax/minecolonytax.toml
D  run/config/warntax/pvp_arena_data.json
D  run/config/warntax/vassals.json
D  run/crash-reports/crash-2024-10-11_05.23.10-fml.txt
D  run/crash-reports/crash-2025-02-07_22.14.44-fml.txt
D  run/crash-reports/crash-2025-02-07_22.15.37-fml.txt
D  run/crash-reports/crash-2025-02-07_22.15.38-fml.txt
D  run/crash-reports/crash-2025-02-07_22.16.31-fml.txt
D  run/crash-reports/crash-2025-02-07_22.16.37-fml.txt
D  run/crash-reports/crash-2025-02-07_22.17.50-fml.txt
D  run/crash-reports/crash-2025-02-07_22.18.50-fml.txt
D  run/crash-reports/crash-2025-02-07_22.21.46-fml.txt
D  run/crash-reports/crash-2025-02-07_22.23.27-fml.txt
D  run/crash-reports/crash-2025-05-02_07.56.46-fml.txt
D  run/crash-reports/crash-2025-05-10_04.46.52-fml.txt
D  run/crash-reports/crash-2025-06-28_04.02.54-fml.txt
D  run/crash-reports/crash-2025-06-28_06.22.20-server.txt
D  run/crash-reports/crash-2025-06-29_16.50.46-fml.txt
D  run/crash-reports/crash-2025-08-10_11.43.43-server.txt
D  run/crash-reports/crash-2025-08-10_11.46.59-server.txt
D  run/crash-reports/crash-2025-09-05_14.27.18-fml.txt
D  run/defaultconfigs/ftblibrary/ftblibrary-client.snbt
D  run/defaultconfigs/sdmshop/sdmshop-common.snbt
D  run/eula.txt
D  run/hs_err_pid31556.log
D  run/hs_err_pid35296.log
D  run/hs_err_pid36324.log
D  run/hs_err_pid51196.log
D  run/hs_err_pid60888.log
D  run/hs_err_pid66380.log
D  run/hs_err_pid73508.log
D  run/hs_err_pid80328.log
D  run/local/ftblibrary-client.snbt
D  run/ops.json
D  run/options.txt
D  run/replay_pid31556.log
D  run/replay_pid66380.log
D  run/replay_pid73508.log
D  "run/saves/New World/DIM-1/data/capabilities.dat"
D  "run/saves/New World/DIM-1/data/raids.dat"
D  "run/saves/New World/DIM1/data/capabilities.dat"
D  "run/saves/New World/DIM1/data/raids_end.dat"
D  "run/saves/New World/SDMEconomy/380df991-f603-344c-a090-369bad2a924a.data"
D  "run/saves/New World/SDMEconomy/aed5efd4-551b-3965-bc28-ae21aa072a66.data"
D  "run/saves/New World/advancements/380df991-f603-344c-a090-369bad2a924a.json"
D  "run/saves/New World/advancements/aed5efd4-551b-3965-bc28-ae21aa072a66.json"
D  "run/saves/New World/data/capabilities.dat"
D  "run/saves/New World/data/diplomacy_data.dat"
D  "run/saves/New World/data/raids.dat"
D  "run/saves/New World/data/random_sequences.dat"
D  "run/saves/New World/data/recruit_player_unit_data.dat"
D  "run/saves/New World/data/recruitsTeamSaveData.dat"
D  "run/saves/New World/data/scoreboard.dat"
D  "run/saves/New World/entities/r.-1.-1.mca"
D  "run/saves/New World/entities/r.-1.-2.mca"
D  "run/saves/New World/entities/r.-1.0.mca"
D  "run/saves/New World/entities/r.-1.1.mca"
D  "run/saves/New World/entities/r.-2.-1.mca"
D  "run/saves/New World/entities/r.-2.0.mca"
D  "run/saves/New World/entities/r.-3.-1.mca"
D  "run/saves/New World/entities/r.-3.0.mca"
D  "run/saves/New World/entities/r.0.-1.mca"
D  "run/saves/New World/entities/r.0.-2.mca"
D  "run/saves/New World/entities/r.0.0.mca"
D  "run/saves/New World/entities/r.0.1.mca"
D  "run/saves/New World/entities/r.1.-1.mca"
D  "run/saves/New World/entities/r.1.0.mca"
D  "run/saves/New World/ftbteams/ftbteams.snbt"
D  "run/saves/New World/ftbteams/player/380df991-f603-344c-a090-369bad2a924a.snbt"
D  "run/saves/New World/ftbteams/player/aed5efd4-551b-3965-bc28-ae21aa072a66.snbt"
D  "run/saves/New World/icon.png"
D  "run/saves/New World/level.dat"
D  "run/saves/New World/level.dat_old"
D  "run/saves/New World/minecolonies/colonies.dat"
D  "run/saves/New World/minecolonies/minecraft/overworld/colony1.dat"
D  "run/saves/New World/minecolonies/minecraft/overworld/colony2.dat"
D  "run/saves/New World/playerdata/380df991-f603-344c-a090-369bad2a924a.dat"
D  "run/saves/New World/playerdata/380df991-f603-344c-a090-369bad2a924a.dat_old"
D  "run/saves/New World/playerdata/aed5efd4-551b-3965-bc28-ae21aa072a66-15586002512765606526.dat"
D  "run/saves/New World/playerdata/aed5efd4-551b-3965-bc28-ae21aa072a66.dat"
D  "run/saves/New World/playerdata/aed5efd4-551b-3965-bc28-ae21aa072a66.dat_old"
D  "run/saves/New World/poi/r.-1.-1.mca"
D  "run/saves/New World/poi/r.-1.-2.mca"
D  "run/saves/New World/poi/r.-1.0.mca"
D  "run/saves/New World/poi/r.-1.1.mca"
D  "run/saves/New World/poi/r.-2.-1.mca"
D  "run/saves/New World/poi/r.-2.-2.mca"
D  "run/saves/New World/poi/r.-2.0.mca"
D  "run/saves/New World/poi/r.-2.1.mca"
D  "run/saves/New World/poi/r.-3.-1.mca"
D  "run/saves/New World/poi/r.-3.0.mca"
D  "run/saves/New World/poi/r.-3.1.mca"
D  "run/saves/New World/poi/r.0.-1.mca"
D  "run/saves/New World/poi/r.0.-2.mca"
D  "run/saves/New World/poi/r.0.0.mca"
D  "run/saves/New World/poi/r.0.1.mca"
D  "run/saves/New World/poi/r.1.-1.mca"
D  "run/saves/New World/poi/r.1.-2.mca"
D  "run/saves/New World/poi/r.1.0.mca"
D  "run/saves/New World/poi/r.1.1.mca"
D  "run/saves/New World/region/r.-1.-1.mca"
D  "run/saves/New World/region/r.-1.-2.mca"
D  "run/saves/New World/region/r.-1.0.mca"
D  "run/saves/New World/region/r.-1.1.mca"
D  "run/saves/New World/region/r.-2.-1.mca"
D  "run/saves/New World/region/r.-2.-2.mca"
D  "run/saves/New World/region/r.-2.0.mca"
D  "run/saves/New World/region/r.-2.1.mca"
D  "run/saves/New World/region/r.-3.-1.mca"
D  "run/saves/New World/region/r.-3.-2.mca"
D  "run/saves/New World/region/r.-3.0.mca"
D  "run/saves/New World/region/r.-3.1.mca"
D  "run/saves/New World/region/r.0.-1.mca"
D  "run/saves/New World/region/r.0.-2.mca"
D  "run/saves/New World/region/r.0.0.mca"
D  "run/saves/New World/region/r.0.1.mca"
D  "run/saves/New World/region/r.1.-1.mca"
D  "run/saves/New World/region/r.1.-2.mca"
D  "run/saves/New World/region/r.1.0.mca"
D  "run/saves/New World/region/r.1.1.mca"
D  "run/saves/New World/serverconfig/forge-server.toml"
D  "run/saves/New World/serverconfig/jei-server.toml"
D  "run/saves/New World/serverconfig/minecolonies-server.toml"
D  "run/saves/New World/serverconfig/recruits-server.toml"
D  "run/saves/New World/serverconfig/structurize-server.toml"
D  "run/saves/New World/session.lock"
D  "run/saves/New World/stats/380df991-f603-344c-a090-369bad2a924a.json"
D  "run/saves/New World/stats/aed5efd4-551b-3965-bc28-ae21aa072a66.json"
D  run/saves/tsadasd/entities/r.0.-1.mca
D  run/saves/tsadasd/level.dat
D  run/saves/tsadasd/region/r.-1.-1.mca
D  run/saves/tsadasd/region/r.-1.-2.mca
D  run/saves/tsadasd/region/r.-1.0.mca
D  run/saves/tsadasd/region/r.0.-1.mca
D  run/saves/tsadasd/region/r.0.-2.mca
D  run/saves/tsadasd/region/r.0.0.mca
D  run/saves/tsadasd/region/r.1.-1.mca
D  run/saves/tsadasd/region/r.1.-2.mca
D  run/saves/tsadasd/region/r.1.0.mca
D  run/saves/tsadasd/serverconfig/forge-server.toml
D  run/saves/tsadasd/serverconfig/jei-server.toml
D  run/saves/tsadasd/serverconfig/minecolonies-server.toml
D  run/saves/tsadasd/serverconfig/structurize-server.toml
D  run/saves/tsadasd/session.lock
D  run/screenshots/2025-05-10_00.42.19.png
D  run/screenshots/2025-06-01_05.07.29.png
D  run/server.properties
D  run/servers.dat
D  run/servers.dat_old
D  run/taxData.json
D  run/usercache.json
D  run/usernamecache.json
D  run/whitelist.json
D  run/world/DIM-1/data/capabilities.dat
D  run/world/DIM-1/data/raids.dat
D  run/world/DIM1/data/capabilities.dat
D  run/world/DIM1/data/raids_end.dat
D  run/world/SDMEconomy/380df991-f603-344c-a090-369bad2a924a.data
D  run/world/SDMEconomy/681f539b-8bb8-3f85-85e5-a2945f6c6539.data
D  run/world/SDMEconomy/aed5efd4-551b-3965-bc28-ae21aa072a66.data
D  run/world/advancements/380df991-f603-344c-a090-369bad2a924a.json
D  run/world/advancements/aed5efd4-551b-3965-bc28-ae21aa072a66.json
D  run/world/data/capabilities.dat
D  run/world/data/diplomacy_data.dat
D  run/world/data/raids.dat
D  run/world/data/random_sequences.dat
D  run/world/data/recruit_player_unit_data.dat
D  run/world/data/recruitsTeamSaveData.dat
D  run/world/data/scoreboard.dat
D  run/world/entities/r.-1.-1.mca
D  run/world/entities/r.-1.0.mca
D  run/world/entities/r.0.-1.mca
D  run/world/entities/r.0.-2.mca
D  run/world/entities/r.0.0.mca
D  run/world/entities/r.1.-1.mca
D  run/world/entities/r.1.-2.mca
D  run/world/entities/r.1.0.mca
D  run/world/entities/r.2.-1.mca
D  run/world/entities/r.2.0.mca
D  run/world/ftbteams/ftbteams.snbt
D  run/world/ftbteams/player/380df991-f603-344c-a090-369bad2a924a.snbt
D  run/world/ftbteams/player/681f539b-8bb8-3f85-85e5-a2945f6c6539.snbt
D  run/world/ftbteams/player/aed5efd4-551b-3965-bc28-ae21aa072a66.snbt
D  run/world/level.dat
D  run/world/level.dat_old
D  run/world/minecolonies/colonies-2024-10-04_07.24.51.zip
D  run/world/minecolonies/colonies-2024-10-04_07.25.43.zip
D  run/world/minecolonies/colonies-2024-10-04_07.30.30.zip
D  run/world/minecolonies/colonies-2024-10-04_07.36.58.zip
D  run/world/minecolonies/colonies-2024-10-05_12.21.53.zip
D  run/world/minecolonies/colonies-2024-10-05_12.23.22.zip
D  run/world/minecolonies/colonies-2024-10-05_12.24.30.zip
D  run/world/minecolonies/colonies-2024-10-05_13.20.30.zip
D  run/world/minecolonies/colonies-2024-10-05_13.22.43.zip
D  run/world/minecolonies/colonies-2024-10-05_16.59.06.zip
D  run/world/minecolonies/colonies-2024-10-05_16.59.52.zip
D  run/world/minecolonies/colonies-2024-10-05_17.05.26.zip
D  run/world/minecolonies/colonies-2024-10-05_17.13.20.zip
D  run/world/minecolonies/colonies-2024-10-05_18.49.33.zip
D  run/world/minecolonies/colonies-2024-10-05_18.56.34.zip
D  run/world/minecolonies/colonies-2024-10-05_18.57.38.zip
D  run/world/minecolonies/colonies.dat
D  run/world/minecolonies/minecraft/overworld/colony1.dat
D  run/world/minecolonies/minecraft/overworld/colony2.dat
D  run/world/playerdata/380df991-f603-344c-a090-369bad2a924a.dat
D  run/world/playerdata/aed5efd4-551b-3965-bc28-ae21aa072a66.dat
D  run/world/playerdata/aed5efd4-551b-3965-bc28-ae21aa072a66.dat_old
D  run/world/poi/r.-1.-1.mca
D  run/world/poi/r.-1.-2.mca
D  run/world/poi/r.-1.0.mca
D  run/world/poi/r.0.-1.mca
D  run/world/poi/r.0.-2.mca
D  run/world/poi/r.0.0.mca
D  run/world/poi/r.0.1.mca
D  run/world/poi/r.1.-1.mca
D  run/world/poi/r.1.-2.mca
D  run/world/poi/r.1.0.mca
D  run/world/poi/r.1.1.mca
D  run/world/poi/r.2.-1.mca
D  run/world/poi/r.2.-2.mca
D  run/world/poi/r.2.0.mca
D  run/world/poi/r.2.1.mca
D  run/world/region/r.-1.-1.mca
D  run/world/region/r.-1.-2.mca
D  run/world/region/r.-1.0.mca
D  run/world/region/r.-2.-1.mca
D  run/world/region/r.-2.0.mca
D  run/world/region/r.0.-1.mca
D  run/world/region/r.0.-2.mca
D  run/world/region/r.0.0.mca
D  run/world/region/r.0.1.mca
D  run/world/region/r.1.-1.mca
D  run/world/region/r.1.-2.mca
D  run/world/region/r.1.0.mca
D  run/world/region/r.1.1.mca
D  run/world/region/r.2.-1.mca
D  run/world/region/r.2.-2.mca
D  run/world/region/r.2.0.mca
D  run/world/region/r.2.1.mca
D  run/world/region/r.3.-1.mca
D  run/world/region/r.3.0.mca
D  run/world/serverconfig/forge-server.toml
D  run/world/serverconfig/jei-server.toml
D  run/world/serverconfig/minecolonies-server.toml
D  run/world/serverconfig/recruits-server.toml
D  run/world/serverconfig/structurize-server.toml
D  run/world/session.lock
D  run/world/stats/380df991-f603-344c-a090-369bad2a924a.json
D  run/world/stats/aed5efd4-551b-3965-bc28-ae21aa072a66.json
D  run2/blueprints/Dev/pack.json
D  run2/blueprints/Dev/scans
D  run2/blueprints/Player1/pack.json
D  run2/blueprints/Player1/scans
D  run2/blueprints/player2/pack.json
D  run2/config/SDMShop/customization.json
D  run2/config/SDMShop/sdmshop-common.snbt
D  run2/config/SDMShop/sdmshop.snbt
D  run2/config/fml.toml
D  run2/config/forge-client.toml
D  run2/config/jei/blacklist.cfg
D  run2/config/jei/ingredient-list-mod-sort-order.ini
D  run2/config/jei/ingredient-list-type-sort-order.ini
D  run2/config/jei/jei-client.ini
D  run2/config/jei/jei-colors.ini
D  run2/config/jei/jei-debug.ini
D  run2/config/jei/jei-mod-id-format.ini
D  run2/config/jei/recipe-category-sort-order.ini
D  run2/config/minecolonies-client.toml
D  run2/config/minecolonies-common.toml
D  run2/config/recruits-client.toml
D  run2/config/sidebar_buttons.json
D  run2/config/structurize-client.toml
D  run2/config/warntax/colonyTaxData.json
D  run2/config/warntax/lastTaxGeneration.json
D  run2/config/warntax/minecolonytax-1.toml.bak
D  run2/config/warntax/minecolonytax-2.toml.bak
D  run2/config/warntax/minecolonytax-3.toml.bak
D  run2/config/warntax/minecolonytax-4.toml.bak
D  run2/config/warntax/minecolonytax-5.toml.bak
D  run2/config/warntax/minecolonytax.toml
D  run2/config/warntax/vassals.json
D  run2/crash-reports/crash-2025-02-07_22.14.46-fml.txt
D  run2/crash-reports/crash-2025-02-07_22.15.54-fml.txt
D  run2/crash-reports/crash-2025-05-02_07.57.05-fml.txt
D  run2/crash-reports/crash-2025-05-02_19.13.29-fml.txt
D  run2/crash-reports/crash-2025-06-28_04.02.35-fml.txt
D  run2/crash-reports/crash-2025-09-11_15.10.34-fml.txt
D  run2/defaultconfigs/ftblibrary/ftblibrary-client.snbt
D  run2/defaultconfigs/sdmshop/sdmshop-common.snbt
D  run2/hs_err_pid14172.log
D  run2/hs_err_pid79116.log
D  run2/local/ftblibrary-client.snbt
D  run2/logs/2025-02-06-2.log.gz
D  run2/logs/2025-02-06-3.log.gz
D  run2/logs/2025-02-06-4.log.gz
D  run2/logs/2025-02-07-1.log.gz
D  run2/logs/2025-02-07-10.log.gz
D  run2/logs/2025-02-07-2.log.gz
D  run2/logs/2025-02-07-3.log.gz
D  run2/logs/2025-02-07-4.log.gz
D  run2/logs/2025-02-07-5.log.gz
D  run2/logs/2025-02-07-6.log.gz
D  run2/logs/2025-02-07-7.log.gz
D  run2/logs/2025-02-07-8.log.gz
D  run2/logs/2025-02-07-9.log.gz
D  run2/logs/2025-02-08-2.log.gz
D  run2/logs/2025-02-08-3.log.gz
D  run2/logs/2025-02-08-4.log.gz
D  run2/logs/2025-02-08-5.log.gz
D  run2/logs/2025-02-08-6.log.gz
D  run2/logs/2025-02-08-7.log.gz
D  run2/logs/2025-02-08-8.log.gz
D  run2/logs/2025-03-17-2.log.gz
D  run2/logs/2025-03-17-3.log.gz
D  run2/logs/2025-03-17-4.log.gz
D  run2/logs/2025-03-18-2.log.gz
D  run2/logs/2025-03-18-3.log.gz
D  run2/logs/2025-03-18-4.log.gz
D  run2/logs/2025-03-18-5.log.gz
D  run2/logs/2025-03-18-6.log.gz
D  run2/logs/2025-03-18-7.log.gz
D  run2/logs/2025-04-20-2.log.gz
D  run2/logs/2025-04-20-3.log.gz
D  run2/logs/2025-04-20-4.log.gz
D  run2/logs/2025-04-20-5.log.gz
D  run2/logs/2025-04-24-2.log.gz
D  run2/logs/2025-04-24-3.log.gz
D  run2/logs/2025-04-24-4.log.gz
D  run2/logs/2025-04-24-5.log.gz
D  run2/logs/2025-04-24-6.log.gz
D  run2/logs/2025-04-24-7.log.gz
D  run2/logs/2025-04-25-2.log.gz
D  run2/logs/2025-04-25-3.log.gz
D  run2/logs/2025-04-25-4.log.gz
D  run2/logs/2025-04-25-5.log.gz
D  run2/logs/2025-04-25-6.log.gz
D  run2/logs/2025-05-01-2.log.gz
D  run2/logs/2025-05-01-3.log.gz
D  run2/logs/2025-05-01-4.log.gz
D  run2/logs/2025-05-01-5.log.gz
D  run2/logs/2025-05-02-10.log.gz
D  run2/logs/2025-05-02-11.log.gz
D  run2/logs/2025-05-02-12.log.gz
D  run2/logs/2025-05-02-13.log.gz
D  run2/logs/2025-05-02-14.log.gz
D  run2/logs/2025-05-02-15.log.gz
D  run2/logs/2025-05-02-16.log.gz
D  run2/logs/2025-05-02-17.log.gz
D  run2/logs/2025-05-02-18.log.gz
D  run2/logs/2025-05-02-19.log.gz
D  run2/logs/2025-05-02-2.log.gz
D  run2/logs/2025-05-02-20.log.gz
D  run2/logs/2025-05-02-21.log.gz
D  run2/logs/2025-05-02-22.log.gz
D  run2/logs/2025-05-02-3.log.gz
D  run2/logs/2025-05-02-4.log.gz
D  run2/logs/2025-05-02-5.log.gz
D  run2/logs/2025-05-02-6.log.gz
D  run2/logs/2025-05-02-7.log.gz
D  run2/logs/2025-05-02-8.log.gz
D  run2/logs/2025-05-02-9.log.gz
D  run2/logs/2025-05-03-2.log.gz
D  run2/logs/2025-05-03-3.log.gz
D  run2/logs/2025-05-03-4.log.gz
D  run2/logs/2025-05-03-5.log.gz
D  run2/logs/2025-05-05-2.log.gz
D  run2/logs/2025-05-05-3.log.gz
D  run2/logs/2025-05-05-4.log.gz
D  run2/logs/2025-05-06-2.log.gz
D  run2/logs/2025-05-06-3.log.gz
D  run2/logs/2025-05-06-4.log.gz
D  run2/logs/2025-05-06-5.log.gz
D  run2/logs/2025-05-06-6.log.gz
D  run2/logs/2025-05-06-7.log.gz
D  run2/logs/2025-05-06-8.log.gz
D  run2/logs/2025-05-06-9.log.gz
D  run2/logs/2025-05-07-10.log.gz
D  run2/logs/2025-05-07-11.log.gz
D  run2/logs/2025-05-07-2.log.gz
D  run2/logs/2025-05-07-3.log.gz
D  run2/logs/2025-05-07-4.log.gz
D  run2/logs/2025-05-07-5.log.gz
D  run2/logs/2025-05-07-6.log.gz
D  run2/logs/2025-05-07-7.log.gz
D  run2/logs/2025-05-07-8.log.gz
D  run2/logs/2025-05-07-9.log.gz
D  run2/logs/2025-05-08-2.log.gz
D  run2/logs/2025-05-08-3.log.gz
D  run2/logs/2025-05-09-2.log.gz
D  run2/logs/2025-05-09-3.log.gz
D  run2/logs/2025-05-10-10.log.gz
D  run2/logs/2025-05-10-2.log.gz
D  run2/logs/2025-05-10-3.log.gz
D  run2/logs/2025-05-10-4.log.gz
D  run2/logs/2025-05-10-5.log.gz
D  run2/logs/2025-05-10-6.log.gz
D  run2/logs/2025-05-10-7.log.gz
D  run2/logs/2025-05-10-8.log.gz
D  run2/logs/2025-05-10-9.log.gz
D  run2/logs/2025-05-11-2.log.gz
D  run2/logs/2025-05-11-3.log.gz
D  run2/logs/2025-05-11-4.log.gz
D  run2/logs/2025-05-30-2.log.gz
D  run2/logs/2025-05-30-3.log.gz
D  run2/logs/2025-05-30-4.log.gz
D  run2/logs/2025-06-01-2.log.gz
D  run2/logs/2025-06-01-3.log.gz
D  run2/logs/2025-06-01-4.log.gz
D  run2/logs/2025-06-01-5.log.gz
D  run2/logs/2025-06-01-6.log.gz
D  run2/logs/2025-06-01-7.log.gz
D  run2/logs/2025-06-04-2.log.gz
D  run2/logs/2025-06-04-3.log.gz
D  run2/logs/2025-06-12-2.log.gz
D  run2/logs/2025-06-13-2.log.gz
D  run2/logs/2025-06-21-2.log.gz
D  run2/logs/2025-06-21-3.log.gz
D  run2/logs/2025-06-23-2.log.gz
D  run2/logs/2025-06-27-2.log.gz
D  run2/logs/2025-06-27-3.log.gz
D  run2/logs/2025-06-28-10.log.gz
D  run2/logs/2025-06-28-11.log.gz
D  run2/logs/2025-06-28-12.log.gz
D  run2/logs/2025-06-28-13.log.gz
D  run2/logs/2025-06-28-14.log.gz
D  run2/logs/2025-06-28-15.log.gz
D  run2/logs/2025-06-28-16.log.gz
D  run2/logs/2025-06-28-17.log.gz
D  run2/logs/2025-06-28-18.log.gz
D  run2/logs/2025-06-28-2.log.gz
D  run2/logs/2025-06-28-3.log.gz
D  run2/logs/2025-06-28-4.log.gz
D  run2/logs/2025-06-28-5.log.gz
D  run2/logs/2025-06-28-6.log.gz
D  run2/logs/2025-06-28-7.log.gz
D  run2/logs/2025-06-28-8.log.gz
D  run2/logs/2025-06-28-9.log.gz
D  run2/logs/2025-06-29-2.log.gz
D  run2/logs/2025-06-29-3.log.gz
D  run2/logs/2025-06-29-4.log.gz
D  run2/logs/2025-07-16-2.log.gz
D  run2/logs/2025-07-17-10.log.gz
D  run2/logs/2025-07-17-2.log.gz
D  run2/logs/2025-07-17-3.log.gz
D  run2/logs/2025-07-17-4.log.gz
D  run2/logs/2025-07-17-5.log.gz
D  run2/logs/2025-07-17-6.log.gz
D  run2/logs/2025-07-17-7.log.gz
D  run2/logs/2025-07-17-8.log.gz
D  run2/logs/2025-07-17-9.log.gz
D  run2/logs/2025-07-18-2.log.gz
D  run2/logs/2025-08-05-2.log.gz
D  run2/logs/2025-08-07-2.log.gz
D  run2/logs/2025-08-07-3.log.gz
D  run2/logs/2025-08-07-4.log.gz
D  run2/logs/2025-08-07-5.log.gz
D  run2/logs/2025-08-07-6.log.gz
D  run2/logs/2025-08-09-2.log.gz
D  run2/logs/2025-08-09-3.log.gz
D  run2/logs/2025-08-09-4.log.gz
D  run2/logs/2025-08-09-5.log.gz
D  run2/logs/2025-08-09-6.log.gz
D  run2/logs/2025-08-09-7.log.gz
D  run2/logs/2025-08-09-8.log.gz
D  run2/logs/2025-08-10-10.log.gz
D  run2/logs/2025-08-10-2.log.gz
D  run2/logs/2025-08-10-3.log.gz
D  run2/logs/2025-08-10-4.log.gz
D  run2/logs/2025-08-10-5.log.gz
D  run2/logs/2025-08-10-6.log.gz
D  run2/logs/2025-08-10-7.log.gz
D  run2/logs/2025-08-10-8.log.gz
D  run2/logs/2025-08-10-9.log.gz
D  run2/logs/2025-08-11-2.log.gz
D  run2/logs/2025-08-12-2.log.gz
D  run2/logs/2025-08-12-3.log.gz
D  run2/logs/2025-08-12-4.log.gz
D  run2/logs/2025-09-06-2.log.gz
D  run2/logs/2025-09-06-3.log.gz
D  run2/logs/2025-09-06-4.log.gz
D  run2/logs/2025-09-07-10.log.gz
D  run2/logs/2025-09-07-11.log.gz
D  run2/logs/2025-09-07-12.log.gz
D  run2/logs/2025-09-07-13.log.gz
D  run2/logs/2025-09-07-14.log.gz
D  run2/logs/2025-09-07-15.log.gz
D  run2/logs/2025-09-07-16.log.gz
D  run2/logs/2025-09-07-17.log.gz
D  run2/logs/2025-09-07-18.log.gz
D  run2/logs/2025-09-07-2.log.gz
D  run2/logs/2025-09-07-3.log.gz
D  run2/logs/2025-09-07-4.log.gz
D  run2/logs/2025-09-07-5.log.gz
D  run2/logs/2025-09-07-6.log.gz
D  run2/logs/2025-09-07-7.log.gz
D  run2/logs/2025-09-07-8.log.gz
D  run2/logs/2025-09-07-9.log.gz
D  run2/logs/2025-09-10-2.log.gz
D  run2/logs/2025-09-11-2.log.gz
D  run2/logs/2025-09-11-3.log.gz
D  run2/logs/2025-09-11-4.log.gz
D  run2/logs/2025-09-11-5.log.gz
D  run2/logs/2025-09-11-6.log.gz
D  run2/logs/2025-09-11-7.log.gz
D  run2/logs/2025-09-12-10.log.gz
D  run2/logs/2025-09-12-11.log.gz
D  run2/logs/2025-09-12-12.log.gz
D  run2/logs/2025-09-12-13.log.gz
D  run2/logs/2025-09-12-14.log.gz
D  run2/logs/2025-09-12-15.log.gz
D  run2/logs/2025-09-12-16.log.gz
D  run2/logs/2025-09-12-17.log.gz
D  run2/logs/2025-09-12-18.log.gz
D  run2/logs/2025-09-12-2.log.gz
D  run2/logs/2025-09-12-3.log.gz
D  run2/logs/2025-09-12-4.log.gz
D  run2/logs/2025-09-12-5.log.gz
D  run2/logs/2025-09-12-6.log.gz
D  run2/logs/2025-09-12-7.log.gz
D  run2/logs/2025-09-12-8.log.gz
D  run2/logs/2025-09-12-9.log.gz
D  run2/logs/2025-09-13-10.log.gz
D  run2/logs/2025-09-13-11.log.gz
D  run2/logs/2025-09-13-12.log.gz
D  run2/logs/2025-09-13-13.log.gz
D  run2/logs/2025-09-13-14.log.gz
D  run2/logs/2025-09-13-15.log.gz
D  run2/logs/2025-09-13-16.log.gz
D  run2/logs/2025-09-13-17.log.gz
D  run2/logs/2025-09-13-2.log.gz
D  run2/logs/2025-09-13-3.log.gz
D  run2/logs/2025-09-13-4.log.gz
D  run2/logs/2025-09-13-5.log.gz
D  run2/logs/2025-09-13-6.log.gz
D  run2/logs/2025-09-13-7.log.gz
D  run2/logs/2025-09-13-8.log.gz
D  run2/logs/2025-09-13-9.log.gz
D  run2/logs/2025-09-14-2.log.gz
D  run2/logs/2025-09-14-3.log.gz
D  run2/logs/2025-09-28-2.log.gz
D  run2/logs/2025-09-28-3.log.gz
D  run2/logs/2025-09-28-4.log.gz
D  run2/logs/2025-09-29-1.log.gz
D  run2/logs/2025-09-29-10.log.gz
D  run2/logs/2025-09-29-2.log.gz
D  run2/logs/2025-09-29-3.log.gz
D  run2/logs/2025-09-29-4.log.gz
D  run2/logs/2025-09-29-5.log.gz
D  run2/logs/2025-09-29-6.log.gz
D  run2/logs/2025-09-29-7.log.gz
D  run2/logs/2025-09-29-8.log.gz
D  run2/logs/2025-09-29-9.log.gz
D  run2/logs/2025-11-12-2.log.gz
D  run2/logs/2025-11-29-2.log.gz
D  run2/logs/debug-1.log.gz
D  run2/logs/debug-2.log.gz
D  run2/logs/debug-3.log.gz
D  run2/logs/debug-4.log.gz
D  run2/logs/debug-5.log.gz
D  run2/logs/debug.log
D  run2/logs/latest.log
D  run2/options.txt
D  run2/replay_pid79116.log
D  "run2/saves/New World/DIM-1/data/capabilities.dat"
D  "run2/saves/New World/DIM-1/data/raids.dat"
D  "run2/saves/New World/DIM1/data/capabilities.dat"
D  "run2/saves/New World/DIM1/data/raids_end.dat"
D  "run2/saves/New World/SDMEconomy/380df991-f603-344c-a090-369bad2a924a.data"
D  "run2/saves/New World/SDMEconomy/aed5efd4-551b-3965-bc28-ae21aa072a66.data"
D  "run2/saves/New World/advancements/380df991-f603-344c-a090-369bad2a924a.json"
D  "run2/saves/New World/advancements/aed5efd4-551b-3965-bc28-ae21aa072a66.json"
D  "run2/saves/New World/data/capabilities.dat"
D  "run2/saves/New World/data/diplomacy_data.dat"
D  "run2/saves/New World/data/raids.dat"
D  "run2/saves/New World/data/random_sequences.dat"
D  "run2/saves/New World/data/recruit_player_unit_data.dat"
D  "run2/saves/New World/data/recruitsTeamSaveData.dat"
D  "run2/saves/New World/data/scoreboard.dat"
D  "run2/saves/New World/entities/r.-1.-1.mca"
D  "run2/saves/New World/entities/r.-1.0.mca"
D  "run2/saves/New World/entities/r.0.-1.mca"
D  "run2/saves/New World/entities/r.0.0.mca"
D  "run2/saves/New World/ftbteams/ftbteams.snbt"
D  "run2/saves/New World/ftbteams/player/380df991-f603-344c-a090-369bad2a924a.snbt"
D  "run2/saves/New World/ftbteams/player/aed5efd4-551b-3965-bc28-ae21aa072a66.snbt"
D  "run2/saves/New World/icon.png"
D  "run2/saves/New World/level.dat"
D  "run2/saves/New World/level.dat_old"
D  "run2/saves/New World/playerdata/380df991-f603-344c-a090-369bad2a924a.dat"
D  "run2/saves/New World/playerdata/aed5efd4-551b-3965-bc28-ae21aa072a66.dat"
D  "run2/saves/New World/playerdata/aed5efd4-551b-3965-bc28-ae21aa072a66.dat_old"
D  "run2/saves/New World/poi/r.-1.-1.mca"
D  "run2/saves/New World/poi/r.-1.0.mca"
D  "run2/saves/New World/poi/r.0.-1.mca"
D  "run2/saves/New World/poi/r.0.0.mca"
D  "run2/saves/New World/poi/r.1.-1.mca"
D  "run2/saves/New World/poi/r.1.0.mca"
D  "run2/saves/New World/region/r.-1.-1.mca"
D  "run2/saves/New World/region/r.-1.0.mca"
D  "run2/saves/New World/region/r.0.-1.mca"
D  "run2/saves/New World/region/r.0.0.mca"
D  "run2/saves/New World/region/r.1.-1.mca"
D  "run2/saves/New World/region/r.1.0.mca"
D  "run2/saves/New World/serverconfig/forge-server.toml"
D  "run2/saves/New World/serverconfig/jei-server.toml"
D  "run2/saves/New World/serverconfig/minecolonies-server.toml"
D  "run2/saves/New World/serverconfig/recruits-server.toml"
D  "run2/saves/New World/serverconfig/structurize-server.toml"
D  "run2/saves/New World/session.lock"
D  "run2/saves/New World/stats/380df991-f603-344c-a090-369bad2a924a.json"
D  "run2/saves/New World/stats/aed5efd4-551b-3965-bc28-ae21aa072a66.json"
D  run2/screenshots/2025-05-10_00.50.24.png
D  run2/screenshots/2025-05-10_06.14.27.png
D  run2/screenshots/2025-06-01_05.07.21.png
D  run2/screenshots/2025-06-01_05.08.15.png
D  run2/screenshots/2025-06-01_05.08.23.png
D  run2/screenshots/2025-06-01_05.13.34.png
D  run2/servers.dat
D  run2/servers.dat_old
D  run2/usercache.json
D  run2/usernamecache.json
M  src/main/java/net/machiavelli/minecolonytax/CrashLogger.java
M  src/main/java/net/machiavelli/minecolonytax/FirstColonyTracker.java
MM src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java
MM src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
M  src/main/java/net/machiavelli/minecolonytax/TaxManager.java
MM src/main/java/net/machiavelli/minecolonytax/WarSystem.java
M  src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java
M  src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java
A  src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java
AM src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
M  src/main/java/net/machiavelli/minecolonytax/capability/PlayerWarDataCapability.java
M  src/main/java/net/machiavelli/minecolonytax/commands/AbandonmentCheckCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/CheckTaxRevenueCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/ColonyActivityCommand.java
A  src/main/java/net/machiavelli/minecolonytax/commands/DebugTaxCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/EntityRaidCommands.java
M  src/main/java/net/machiavelli/minecolonytax/commands/FactionCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/GeneralPermissionsCommands.java
M  src/main/java/net/machiavelli/minecolonytax/commands/OfficerTrackingDebugCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/RaidHistoryCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/RaidRepairCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/RandomEventsCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/RecipeDisableTestCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/TaxDebtCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/TaxGUICommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/TaxPolicyCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/TradeRouteCommand.java
A  src/main/java/net/machiavelli/minecolonytax/commands/TreasuryCommand.java
D  src/main/java/net/machiavelli/minecolonytax/commands/WarChestCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/WarCommands.java
D  src/main/java/net/machiavelli/minecolonytax/commands/WarCommands.java.backup
M  src/main/java/net/machiavelli/minecolonytax/commands/WarHistoryCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/WarStatsCommand.java
M  src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java
A  src/main/java/net/machiavelli/minecolonytax/compat/ColonyBuildingUtil.java
A  src/main/java/net/machiavelli/minecolonytax/compat/ColonyHelper.java
A  src/main/java/net/machiavelli/minecolonytax/compat/JmImpl.java
A  src/main/java/net/machiavelli/minecolonytax/compat/SpyJourneyMapPlugin.java
M  src/main/java/net/machiavelli/minecolonytax/data/HistoryManager.java
M  src/main/java/net/machiavelli/minecolonytax/data/PlayerWarData.java
M  src/main/java/net/machiavelli/minecolonytax/data/PlayerWarDataManager.java
M  src/main/java/net/machiavelli/minecolonytax/data/WarData.java
M  src/main/java/net/machiavelli/minecolonytax/datagen/DisabledRecipeProvider.java
M  src/main/java/net/machiavelli/minecolonytax/datagen/ModDataGenerators.java
A  src/main/java/net/machiavelli/minecolonytax/db/WarStatsDB.java
M  src/main/java/net/machiavelli/minecolonytax/economy/RaidPenaltyManager.java
A  src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java
D  src/main/java/net/machiavelli/minecolonytax/economy/WarChestManager.java
M  src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java
M  src/main/java/net/machiavelli/minecolonytax/economy/policy/TaxPolicy.java
M  src/main/java/net/machiavelli/minecolonytax/economy/policy/TaxPolicyManager.java
M  src/main/java/net/machiavelli/minecolonytax/espionage/ModEntities.java
MM src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntity.java
M  src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntityRenderer.java
A  src/main/java/net/machiavelli/minecolonytax/espionage/SpyFleeGoal.java
A  src/main/java/net/machiavelli/minecolonytax/espionage/SpyIntelBookGenerator.java
M  src/main/java/net/machiavelli/minecolonytax/espionage/SpyIntelData.java
MM src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java
A  src/main/java/net/machiavelli/minecolonytax/espionage/SpyMapGenerator.java
M  src/main/java/net/machiavelli/minecolonytax/espionage/SpyMission.java
M  src/main/java/net/machiavelli/minecolonytax/event/AbandonedColonyProtectionHandler.java
MM src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java
MM src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java
M  src/main/java/net/machiavelli/minecolonytax/event/ColonyPermissionMonitor.java
M  src/main/java/net/machiavelli/minecolonytax/event/EntityRaidBossbarAttachHandler.java
M  src/main/java/net/machiavelli/minecolonytax/event/EntityRaidEventHandler.java
M  src/main/java/net/machiavelli/minecolonytax/event/OfficerColonyVisitTracker.java
M  src/main/java/net/machiavelli/minecolonytax/event/PatchouliBookHandler.java
M  src/main/java/net/machiavelli/minecolonytax/event/PvPKillEconomyHandler.java
M  src/main/java/net/machiavelli/minecolonytax/event/RaidEndEvent.java
M  src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java
M  src/main/java/net/machiavelli/minecolonytax/event/RaidLoginNotifier.java
M  src/main/java/net/machiavelli/minecolonytax/event/RecipeDisableClient.java
M  src/main/java/net/machiavelli/minecolonytax/event/RecipeDisableEventHandler.java
M  src/main/java/net/machiavelli/minecolonytax/event/RecipeDisableRuntime.java
M  src/main/java/net/machiavelli/minecolonytax/event/WarEconomyHandler.java
MM src/main/java/net/machiavelli/minecolonytax/event/WarEventHandler.java
M  src/main/java/net/machiavelli/minecolonytax/event/WarVictoryEvent.java
D  src/main/java/net/machiavelli/minecolonytax/events/random/EventHistory.java
A  src/main/java/net/machiavelli/minecolonytax/events/random/EventLogEntry.java
M  src/main/java/net/machiavelli/minecolonytax/events/random/EventTriggerSystem.java
M  src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java
M  src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventType.java
M  src/main/java/net/machiavelli/minecolonytax/events/random/deep/CitizenManipulator.java
M  src/main/java/net/machiavelli/minecolonytax/faction/FactionData.java
MM src/main/java/net/machiavelli/minecolonytax/faction/FactionManager.java
M  src/main/java/net/machiavelli/minecolonytax/faction/FactionRelation.java
M  src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java
A  src/main/java/net/machiavelli/minecolonytax/gui/book/BookPage.java
A  src/main/java/net/machiavelli/minecolonytax/gui/book/BookRenderHelper.java
A  src/main/java/net/machiavelli/minecolonytax/gui/book/BookTab.java
A  src/main/java/net/machiavelli/minecolonytax/gui/book/ColoniesPage.java
A  src/main/java/net/machiavelli/minecolonytax/gui/book/EconomyPage.java
A  src/main/java/net/machiavelli/minecolonytax/gui/book/EspionagePage.java
A  src/main/java/net/machiavelli/minecolonytax/gui/book/InvestmentsPage.java
A  src/main/java/net/machiavelli/minecolonytax/gui/book/OfficersPage.java
A  src/main/java/net/machiavelli/minecolonytax/gui/book/TreasuryPage.java
A  src/main/java/net/machiavelli/minecolonytax/gui/book/VassalsPage.java
A  src/main/java/net/machiavelli/minecolonytax/gui/data/ColonySummary.java
M  src/main/java/net/machiavelli/minecolonytax/gui/data/ColonyTaxData.java
M  src/main/java/net/machiavelli/minecolonytax/gui/data/OfficerData.java
M  src/main/java/net/machiavelli/minecolonytax/gui/data/SpyMissionData.java
MM src/main/java/net/machiavelli/minecolonytax/gui/data/VassalIncomeData.java
A  src/main/java/net/machiavelli/minecolonytax/happiness/ColonyHappinessModifierManager.java
A  src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java
M  src/main/java/net/machiavelli/minecolonytax/militia/CitizenMilitiaManager.java
M  src/main/java/net/machiavelli/minecolonytax/network/NetworkHandler.java
A  src/main/java/net/machiavelli/minecolonytax/network/packets/BuyInvestmentPacket.java
M  src/main/java/net/machiavelli/minecolonytax/network/packets/ColonyDataResponsePacket.java
M  src/main/java/net/machiavelli/minecolonytax/network/packets/DeploySpyPacket.java
A  src/main/java/net/machiavelli/minecolonytax/network/packets/DismissEventPacket.java
A  src/main/java/net/machiavelli/minecolonytax/network/packets/DismissSpyMissionPacket.java
A  src/main/java/net/machiavelli/minecolonytax/network/packets/InvestmentDataResponsePacket.java
M  src/main/java/net/machiavelli/minecolonytax/network/packets/RecallSpyPacket.java
M  src/main/java/net/machiavelli/minecolonytax/network/packets/RequestColonyDataPacket.java
A  src/main/java/net/machiavelli/minecolonytax/network/packets/RequestInvestmentDataPacket.java
M  src/main/java/net/machiavelli/minecolonytax/network/packets/RequestSpyDataPacket.java
R  src/main/java/net/machiavelli/minecolonytax/network/packets/RequestWarChestDataPacket.java -> src/main/java/net/machiavelli/minecolonytax/network/packets/RequestTreasuryDataPacket.java
M  src/main/java/net/machiavelli/minecolonytax/network/packets/SpyDataResponsePacket.java
R  src/main/java/net/machiavelli/minecolonytax/network/packets/WarChestActionPacket.java -> src/main/java/net/machiavelli/minecolonytax/network/packets/TreasuryActionPacket.java
R  src/main/java/net/machiavelli/minecolonytax/network/packets/WarChestDataResponsePacket.java -> src/main/java/net/machiavelli/minecolonytax/network/packets/TreasuryDataResponsePacket.java
M  src/main/java/net/machiavelli/minecolonytax/network/packets/UpdatePlayerTaxPermissionPacket.java
AM src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
M  src/main/java/net/machiavelli/minecolonytax/peace/PeaceProposal.java
M  src/main/java/net/machiavelli/minecolonytax/peace/PeaceProposalManager.java
AM src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java
M  src/main/java/net/machiavelli/minecolonytax/permissions/GeneralColonyPermissionsManager.java
A  src/main/java/net/machiavelli/minecolonytax/permissions/PermissionSnapshot.java
A  src/main/java/net/machiavelli/minecolonytax/permissions/PermissionsHealthCheck.java
M  src/main/java/net/machiavelli/minecolonytax/permissions/TaxPermissionManager.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/PvPArenaCommand.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/PvPBattleManager.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/PvPEventHandler.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/PvPManager.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/PvPMapManager.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/model/ActiveBattle.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/model/BattleRequest.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/model/PlayerPvPStats.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/model/PvPMap.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/model/SpectatorData.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/model/TeamBattle.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/model/TeamBattleState.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/persistence/ArenaDataCollection.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/persistence/ArenaMapData.java
M  src/main/java/net/machiavelli/minecolonytax/pvp/persistence/SpawnPointData.java
M  src/main/java/net/machiavelli/minecolonytax/raid/ActiveRaidData.java
M  src/main/java/net/machiavelli/minecolonytax/raid/EntityRaidDebugLogger.java
M  src/main/java/net/machiavelli/minecolonytax/raid/EntityRaidManager.java
M  src/main/java/net/machiavelli/minecolonytax/raid/GuardResistanceHandler.java
M  src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java
M  src/main/java/net/machiavelli/minecolonytax/raid/RecruitmentTracker.java
M  src/main/java/net/machiavelli/minecolonytax/raid/ReflectionCache.java
M  src/main/java/net/machiavelli/minecolonytax/recipe/DisabledRecipe.java
M  src/main/java/net/machiavelli/minecolonytax/recipe/DisabledRecipeSerializer.java
M  src/main/java/net/machiavelli/minecolonytax/recipe/ModRecipeSerializers.java
M  src/main/java/net/machiavelli/minecolonytax/requirements/BuildingRequirementsManager.java
M  src/main/java/net/machiavelli/minecolonytax/server/ColonyDataCollector.java
A  src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java
AM src/main/java/net/machiavelli/minecolonytax/siege/WarBlockLedger.java
M  src/main/java/net/machiavelli/minecolonytax/trade/TradeRouteManager.java
A  src/main/java/net/machiavelli/minecolonytax/upgrade/ColonyUpgradeData.java
A  src/main/java/net/machiavelli/minecolonytax/upgrade/ColonyUpgradeManager.java
A  src/main/java/net/machiavelli/minecolonytax/upgrade/UpgradeType.java
M  src/main/java/net/machiavelli/minecolonytax/util/ColonyActivityTracker.java
M  src/main/java/net/machiavelli/minecolonytax/util/ItemUtils.java
A  src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java
M  src/main/java/net/machiavelli/minecolonytax/util/TranslationUtil.java
MM src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java
D  src/main/java/net/machiavelli/minecolonytax/webapi/PlayerDataCache.java
D  src/main/java/net/machiavelli/minecolonytax/webapi/WarStatsAPIData.java
D  src/main/java/net/machiavelli/minecolonytax/webapi/WebAPIServer.java
M  src/main/resources/assets/minecolonytax/lang/en_us.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/categories/besiege.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/categories/espionage.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/categories/tax_policies.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/categories/war_economy.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/besiege/besiege_mechanics.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/besiege/besiege_occupation.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/besiege/besiege_overview.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/colony_management/investments.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/colony_management/treasury.json
M  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/commands/player_commands.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/diplomacy/vassalization.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/espionage/espionage_overview.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/espionage/spy_defense.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/espionage/spy_intel.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/espionage/spy_lifecycle.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/espionage/spy_missions.json
M  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/getting_started/welcome.json
M  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/random_events/managing_events.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/tax_policies/policy_overview.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/tax_policies/policy_types.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/war_economy/war_exhaustion.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/war_economy/war_reparations.json
A  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/war_economy/war_weariness.json
M  src/main/resources/assets/minecolonytax/patchouli_books/war_taxes_codex/en_us/entries/war_system/war_overview.json
A  src/main/resources/assets/minecolonytax/textures/entity/spy/spy.png
A  src/main/resources/assets/minecolonytax/textures/gui/book_background.png
A  src/main/resources/assets/minecolonytax/textures/gui/icons/colonies_icon.png
A  src/main/resources/assets/minecolonytax/textures/gui/icons/economy_icon.png
A  src/main/resources/assets/minecolonytax/textures/gui/icons/espionage_icon.png
A  src/main/resources/assets/minecolonytax/textures/gui/icons/officers_icon.png
A  src/main/resources/assets/minecolonytax/textures/gui/icons/vassals_icon.png
A  src/main/resources/assets/minecolonytax/textures/gui/icons/warchest_icon.png
A  src/main/resources/data/minecolonytax/recipes/war_taxes_codex.json
D  test_migration.java
A  wiki/Besiege_System.md
M  wiki/Colony_Abandonment.md
M  wiki/Commands_&_Permissions.md
M  wiki/Configuration_Guide.md
M  wiki/Espionage_System.md
M  wiki/Features_&_Mechanics.md
M  wiki/Home.md
A  wiki/Investments_System.md
A  wiki/Occupation_System.md
M  wiki/Random_Events.md
A  wiki/Tax_Policies.md
M  wiki/Taxation_System.md
A  wiki/War_Exhaustion.md
A  wiki/War_Persistence.md
M  wiki/War_System.md
MM wiki/_Sidebar.md
?? DENY_MESSAGE_COOLDOWN_MS)
?? claimButtonX
?? reviews/
?? s.withColor(ChatFormatting.GREEN).withBold(true),+
?? src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java
?? src/main/java/net/machiavelli/minecolonytax/compat/EasyFactionsBridge.java
?? src/main/java/net/machiavelli/minecolonytax/compat/EasyFactionsPermissionSync.java
?? src/main/java/net/machiavelli/minecolonytax/compat/ExplosiontCompat.java
?? src/main/java/net/machiavelli/minecolonytax/espionage/SpyClientHandler.java
?? src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java
?? vassalized
?? wiki/Easy_Factions_Integration.md
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git ls-files -- src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git ls-files -- src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java'` rejected: blocked by policy
2026-05-25T03:03:40.417221Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git ls-files -- src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git status --short src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 298ms:
?? src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git status --short src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 297ms:
AM src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
STATUS REWORK

A VERIFIED: `cleanupRaid` always calls `removeFromColonyIndex(...)`, including tick cleanup paths using `removeFromMap=false`.

B VERIFIED: `completeBesiegeVictory` returns immediately when `reclaimByOriginalOwner(...)` succeeds, before `VassalManager.forceVassalize(...)`.

C VERIFIED in working tree: both `EntityInteract` and `EntityInteractSpecific` subscribe and delegate to shared `handleEntityInteraction(...)`.

Fix suggestion: `BesiegeEntityInteractHandler.java` is currently untracked, and the supplied diff for it is empty. Add/stage/include that file, otherwise fix C may be omitted from the actual patch/commit.
tokens used
117.927
STATUS REWORK

A VERIFIED: `cleanupRaid` always calls `removeFromColonyIndex(...)`, including tick cleanup paths using `removeFromMap=false`.

B VERIFIED: `completeBesiegeVictory` returns immediately when `reclaimByOriginalOwner(...)` succeeds, before `VassalManager.forceVassalize(...)`.

C VERIFIED in working tree: both `EntityInteract` and `EntityInteractSpecific` subscribe and delegate to shared `handleEntityInteraction(...)`.

Fix suggestion: `BesiegeEntityInteractHandler.java` is currently untracked, and the supplied diff for it is empty. Add/stage/include that file, otherwise fix C may be omitted from the actual patch/commit.
