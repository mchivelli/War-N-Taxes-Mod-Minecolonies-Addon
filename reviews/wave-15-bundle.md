## WAVE 15 — Step 3 Phase 2 (shared defender pool + last-kill-credit)

Three changes to close Step 3:
  A — BesiegeRaidData defender-pool sets (hostileCitizenIds/spawnedMercenaries/militiaSupport) changed from final to mutable. New isSecondaryRaider flag.
  B — launchRaid detects existing raids on the colony BEFORE inserting itself. If primary exists, secondary raid points its three pool sets at primary's by reference (no doubling), skips merc/militia spawn calls, computes defender counts from the shared pool for boss-bar display.
  C — cleanupRaid ref-counted: only restores citizen AI + despawns mercs/militia when this is the LAST raid on the colony (via COLONY_RAID_INDEX size check).
  D — tick() last-kill-credit: when allDefendersDead becomes true for a raid, that raid wins; all other concurrent besiegers on the same colony are notified 'you lost the race', get cleanup + cooldown, no spoils.

### DIFF: BesiegeManager.java
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
index 959ac36..61e2715 100644
--- a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
@@ -11,7 +11,12 @@ import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
 import com.minecolonies.api.colony.permissions.Action;
 import com.minecolonies.api.colony.permissions.IPermissions;
 import com.minecolonies.api.colony.permissions.Rank;
-import com.minecolonies.core.entity.mobs.EntityMercenary;
+// Intentionally do NOT import com.minecolonies.core.entity.mobs.EntityMercenary —
+// it lives in the INTERNAL core package, not the api/* surface. We work through the
+// API-typed EntityType<? extends PathfinderMob> ModEntities.MERCENARY and operate on
+// the result as a Mob, so a future class move/rename cannot brick this manager.
+import net.minecraft.world.entity.Mob;
+import net.minecraft.world.entity.PathfinderMob;
 import net.machiavelli.minecolonytax.FirstColonyTracker;
 import net.machiavelli.minecolonytax.TaxConfig;
 import net.machiavelli.minecolonytax.WarSystem;
@@ -68,6 +73,14 @@ public class BesiegeManager {
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
 
@@ -88,6 +101,7 @@ public class BesiegeManager {
             cleanupRaid(raid, false);
         }
         ACTIVE_RAIDS.clear();
+        COLONY_RAID_INDEX.clear();
         if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager shutdown complete");
     }
 
@@ -119,8 +133,10 @@ public class BesiegeManager {
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
@@ -148,8 +164,34 @@ public class BesiegeManager {
                 if (allDefendersDead(raid, colony)) {
                     if (TaxConfig.isNormalLogging())
                         LOGGER.info("Besiege raid on colony {} successful — besieger wins", colony.getName());
+                    // Step 3 Phase 2 — last-kill-credit. Whoever's raid we're
+                    // processing right now in the tick wins. All other concurrent
+                    // besiegers on this colony lose the race: cooldown applied,
+                    // no spoils, "you lost the race" message.
+                    java.util.List<BesiegeRaidData> raceLosers = new java.util.ArrayList<>();
+                    for (UUID otherUUID : COLONY_RAID_INDEX.getOrDefault(raid.colonyId, java.util.Collections.emptySet())) {
+                        if (otherUUID.equals(raid.besiegingPlayerUUID)) continue;
+                        BesiegeRaidData other = ACTIVE_RAIDS.get(otherUUID);
+                        if (other != null) raceLosers.add(other);
+                    }
                     completeBesiege(raid, true, colony);
                     it.remove();
+                    for (BesiegeRaidData loser : raceLosers) {
+                        try {
+                            sendToPlayer(loser.besiegingPlayerUUID, Component.literal(
+                                    "You lost the race — " + getPlayerName(raid.besiegingPlayerUUID)
+                                            + " landed the killing blow on " + colony.getName() + ".")
+                                    .withStyle(ChatFormatting.RED));
+                            cleanupRaid(loser, true);
+                            applyCooldown(loser.besiegingPlayerUUID);
+                            if (TaxConfig.isNormalLogging())
+                                LOGGER.info("Race-loser besieger {} ended (no spoils, cooldown applied)",
+                                        loser.besiegingPlayerUUID);
+                        } catch (Exception ex) {
+                            LOGGER.warn("Failed to end race-loser raid for {}: {}",
+                                    loser.besiegingPlayerUUID, ex.getMessage());
+                        }
+                    }
                     continue;
                 }
 
@@ -304,27 +346,59 @@ public class BesiegeManager {
 
         try {
             BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
+
+            // Step 3 Phase 2 — multi-besieger shared defender pool.
+            // Find any already-active raid on this colony BEFORE inserting our own.
+            // If one exists, this besieger is a "secondary raider": share the
+            // primary's defender pool by reference so the colony's defenders aren't
+            // doubled, and skip the spawn calls that would otherwise create extra
+            // mercenaries/militia.
+            List<BesiegeRaidData> existingForColony = getRaidsForColony(colonyId);
+            BesiegeRaidData primary = existingForColony.isEmpty() ? null : existingForColony.get(0);
+
             ACTIVE_RAIDS.put(besiegerUUID, raid);
+            COLONY_RAID_INDEX.computeIfAbsent(colonyId, k -> ConcurrentHashMap.newKeySet()).add(besiegerUUID);
 
             // Grant the besieger hostile rank + combat permissions on the colony
             // so MineColonies allows the player to attack citizens.
             grantBesiegeCombatPermissions(colony, besiegerUUID);
 
-            // Convert guards to hostile
-            int guardCount = makeGuardsHostile(colony, besieger, raid);
-
-            // Convert militia (non-guard eligible citizens)
-            int militiaCount = convertCitizensToMilitia(colony, besieger, raid);
-
-            // Spawn mercenaries
-            int mercCount = spawnMercenaries(colony, besieger, raid);
+            int guardCount;
+            int militiaCount;
+            int mercCount;
+
+            if (primary != null) {
+                // Secondary raider — share defender pool by reference.
+                raid.isSecondaryRaider = true;
+                raid.hostileCitizenIds = primary.hostileCitizenIds;
+                raid.spawnedMercenaries = primary.spawnedMercenaries;
+                raid.militiaSupport = primary.militiaSupport;
+                // Defender counts come from the shared pool.
+                guardCount = (int) primary.hostileCitizenIds.stream()
+                        .filter(id -> {
+                            try {
+                                ICitizenData c = colony.getCitizenManager().getCivilian(id);
+                                return c != null && c.getJob() != null && c.getJob().isGuard();
+                            } catch (Exception e) { return false; }
+                        }).count();
+                militiaCount = primary.hostileCitizenIds.size() - guardCount;
+                mercCount = primary.spawnedMercenaries.size();
+                if (TaxConfig.isNormalLogging()) {
+                    LOGGER.info("Secondary besieger {} joining existing raid on colony {} — sharing defender pool ({}g {}m {}e)",
+                            besiegerUUID, colony.getName(), guardCount, militiaCount, mercCount);
+                }
+            } else {
+                // Primary raider — spawn the defender pool normally.
+                guardCount = makeGuardsHostile(colony, besieger, raid);
+                militiaCount = convertCitizensToMilitia(colony, besieger, raid);
+                mercCount = spawnMercenaries(colony, besieger, raid);
+                spawnMilitiaUpgradeReinforcements(colony, besieger, raid, guardCount);
+                applyFortificationBonus(colony, raid);
+            }
 
             int totalDefenders = guardCount + militiaCount + mercCount;
 
-            // Apply FORTIFICATION investment: extra DAMAGE_RESISTANCE to all defenders
-            applyFortificationBonus(colony, raid);
-
-            // Create boss bar
+            // Create boss bar (per-besieger; each gets their own)
             createBossBar(raid, besieger, colony, totalDefenders);
 
             String verb = isReclaim ? "RECLAIM RAID" : "BESIEGE";
@@ -374,10 +448,19 @@ public class BesiegeManager {
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
@@ -520,12 +603,14 @@ public class BesiegeManager {
 
         for (int i = 0; i < count; i++) {
             try {
-                EntityMercenary merc = (EntityMercenary) com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
+                // Use the API-typed superclass — never reference the internal
+                // EntityMercenary class. See import block for rationale.
+                PathfinderMob merc = com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
                 if (merc == null) continue;
 
                 BlockPos spawnPos = findSpawnPos(colony.getCenter(), world);
                 merc.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
-                merc.setTarget(besieger);
+                ((Mob) merc).setTarget(besieger);
 
                 merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1));
                 merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, durationTicks, 0));
@@ -533,7 +618,10 @@ public class BesiegeManager {
                 world.addFreshEntity(merc);
                 raid.spawnedMercenaries.add(merc);
                 spawned++;
-            } catch (Exception e) {
+            } catch (Throwable e) {
+                // Catch Throwable so a NoClassDefFoundError on the internal mercenary
+                // class (referenced transitively through the EntityType create() return)
+                // cannot propagate up the besiege tick path.
                 LOGGER.warn("Failed to spawn mercenary {} for besiege on colony {}", i, colony.getName(), e);
             }
         }
@@ -541,24 +629,115 @@ public class BesiegeManager {
     }
 
 
+    /**
+     * Spawns militia-upgrade bonus defenders for a besiege.
+     * Delegates to the shared {@link net.machiavelli.minecolonytax.militia.MilitiaSpawner}
+     * so besiege and full-war militia spawning share one implementation.
+     */
+    private static int spawnMilitiaUpgradeReinforcements(IColony colony, ServerPlayer besieger,
+            BesiegeRaidData raid, int guardCount) {
+        return net.machiavelli.minecolonytax.militia.MilitiaSpawner.spawnReinforcements(
+                colony, guardCount, besieger, raid.militiaSupport,
+                TaxConfig.getBesiegeDurationMinutes());
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
 
@@ -637,39 +816,60 @@ public class BesiegeManager {
                 revokeBesiegeCombatPermissions(colony, ally);
             }
 
-            // Restore citizen AI
-            for (int citizenId : raid.hostileCitizenIds) {
-                try {
-                    ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
-                    if (citizen != null && citizen.getEntity().isPresent()) {
-                        AbstractEntityCitizen entity = citizen.getEntity().get();
-                        entity.goalSelector.removeAllGoals(g -> true);
-                        entity.targetSelector.removeAllGoals(g -> true);
-                        // Remove militia sword if present
-                        if (entity.getMainHandItem().getItem() == Items.WOODEN_SWORD) {
-                            entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
-                        }
-                        // Remove combat effects
-                        entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
-                        entity.removeEffect(MobEffects.MOVEMENT_SPEED);
-                        entity.removeEffect(MobEffects.DAMAGE_BOOST);
-                        // Restore job AI
-                        if (citizen.getJob() != null) {
-                            entity.getCitizenJobHandler().onJobChanged(citizen.getJob());
+            // Step 3 Phase 2: only despawn shared defender entities + restore
+            // citizen AI if this is the LAST raid on the colony. Other concurrent
+            // raiders still reference the same hostileCitizenIds / spawnedMercenaries
+            // / militiaSupport sets and rely on those entities to continue existing
+            // until they too end.
+            //
+            // Count siblings BEFORE removing this raid from the index. We're "the
+            // last" if every entry in the index points back to this raid's besieger.
+            Set<UUID> siblingsOnColony = COLONY_RAID_INDEX.getOrDefault(raid.colonyId,
+                    java.util.Collections.emptySet());
+            boolean isLastRaidOnColony = siblingsOnColony.size() <= 1
+                    && siblingsOnColony.contains(raid.besiegingPlayerUUID);
+
+            if (isLastRaidOnColony) {
+                // Restore citizen AI
+                for (int citizenId : raid.hostileCitizenIds) {
+                    try {
+                        ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
+                        if (citizen != null && citizen.getEntity().isPresent()) {
+                            AbstractEntityCitizen entity = citizen.getEntity().get();
+                            entity.goalSelector.removeAllGoals(g -> true);
+                            entity.targetSelector.removeAllGoals(g -> true);
+                            // Remove militia sword if present
+                            if (entity.getMainHandItem().getItem() == Items.WOODEN_SWORD) {
+                                entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
+                            }
+                            // Remove combat effects
+                            entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
+                            entity.removeEffect(MobEffects.MOVEMENT_SPEED);
+                            entity.removeEffect(MobEffects.DAMAGE_BOOST);
+                            // Restore job AI
+                            if (citizen.getJob() != null) {
+                                entity.getCitizenJobHandler().onJobChanged(citizen.getJob());
+                            }
                         }
+                    } catch (Exception e) {
+                        LOGGER.warn("Failed to restore citizen {} AI after besiege", citizenId, e);
                     }
-                } catch (Exception e) {
-                    LOGGER.warn("Failed to restore citizen {} AI after besiege", citizenId, e);
                 }
-            }
 
-            // Despawn mercenaries
-            for (Entity merc : raid.spawnedMercenaries) {
-                try {
-                    if (merc.isAlive()) merc.remove(Entity.RemovalReason.DISCARDED);
-                } catch (Exception e) {
-                    LOGGER.warn("Failed to despawn mercenary after besiege", e);
+                // Despawn militia-upgrade reinforcements (NOT victory-counted)
+                net.machiavelli.minecolonytax.militia.MilitiaSpawner.despawnAll(raid.militiaSupport);
+
+                // Despawn mercenaries
+                for (Entity merc : raid.spawnedMercenaries) {
+                    try {
+                        if (merc.isAlive()) merc.remove(Entity.RemovalReason.DISCARDED);
+                    } catch (Exception e) {
+                        LOGGER.warn("Failed to despawn mercenary after besiege", e);
+                    }
                 }
+            } else if (TaxConfig.isNormalLogging()) {
+                LOGGER.info("Besiege cleanup for {} on colony {} — other raids still active, skipping defender despawn",
+                        raid.besiegingPlayerUUID, colony.getName());
             }
         }
 
@@ -683,6 +883,11 @@ public class BesiegeManager {
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
@@ -753,17 +958,19 @@ public class BesiegeManager {
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
@@ -1098,12 +1305,33 @@ public class BesiegeManager {
         public final UUID besiegingPlayerUUID;
         public final long startTime;
         public final long endTime;
-        public final Set<Integer> hostileCitizenIds = ConcurrentHashMap.newKeySet();
-        public final Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
+        /**
+         * Defender pool sets. NOT {@code final} — when a secondary besieger joins
+         * an already-besieged colony, these three references are reassigned to
+         * point at the primary raid's sets, so all concurrent raids see the SAME
+         * defender pool (no doubling). The primary raid keeps its original sets
+         * even after it ends, so secondary raids' references remain valid.
+         */
+        public Set<Integer> hostileCitizenIds = ConcurrentHashMap.newKeySet();
+        public Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
+        /**
+         * Militia upgrade reinforcements. Tracked separately from spawnedMercenaries
+         * because by design they extend combat without counting toward victory.
+         * allDefendersDead and countAliveDefenders intentionally ignore this set.
+         * Also shared by reference for secondary raiders.
+         */
+        public Set<Entity> militiaSupport = ConcurrentHashMap.newKeySet();
         public final Set<UUID> alliedPlayers = ConcurrentHashMap.newKeySet();
         public final BlockPos colonyCenter;
         public ServerBossEvent bossEvent;
         public final boolean isReclaim;
+        /**
+         * True when this raid is a "secondary" — joined after a primary was
+         * already underway on the same colony. Secondary raids share the defender
+         * pool by reference and skip merc/militia spawning. Used to gate cleanup
+         * so secondary cleanup doesn't despawn shared entities.
+         */
+        public boolean isSecondaryRaider = false;
 
         public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
             this.colonyId = colonyId;
```
