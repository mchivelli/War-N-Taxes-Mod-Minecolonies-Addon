## STEP 8 — Militia upgrade reinforcements (besiege only; war wiring deferred)

Adds raid.militiaSupport Set<Entity>, spawnMilitiaUpgradeReinforcements() spawning floor(guardCount * (multiplier-1.0)) bonus EntityMercenary instances. Despawned on cleanup. Intentionally NOT in allDefendersDead / countAliveDefenders so they extend combat but don't count as victory objectives. War-side wiring (WarSystem.startWar) deferred.

```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
index 959ac36..cb159e1 100644
--- a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
@@ -319,6 +319,10 @@ public class BesiegeManager {
             // Spawn mercenaries
             int mercCount = spawnMercenaries(colony, besieger, raid);
 
+            // Militia upgrade reinforcements — NOT counted toward victory.
+            // Each tier adds +N% bonus militia entities scaled by current guard count.
+            int militiaUpgradeCount = spawnMilitiaUpgradeReinforcements(colony, besieger, raid, guardCount);
+
             int totalDefenders = guardCount + militiaCount + mercCount;
 
             // Apply FORTIFICATION investment: extra DAMAGE_RESISTANCE to all defenders
@@ -541,23 +545,106 @@ public class BesiegeManager {
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
+            LOGGER.info("Spawned {} militia-upgrade reinforcements for colony {} (multiplier {:.2f})",
+                    spawned, colony.getName(), multiplier);
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
+        int spoil = (int) Math.floor(loserBalance * (percent / 100.0));
+        if (spoil <= 0) return;
+
+        net.machiavelli.minecolonytax.economy.TreasuryManager.deductFromTreasury(loser.getID(), spoil);
+        net.machiavelli.minecolonytax.economy.TreasuryManager.addToTreasury(winner.getID(), spoil);
+
+        if (TaxConfig.isNormalLogging()) {
+            LOGGER.info("Siege spoils ({}%): {} → {} = {}", percent, loser.getName(), winner.getName(), spoil);
+        }
+
+        // Notify both sides
+        UUID winnerOwner = winner.getPermissions().getOwner();
+        UUID loserOwner = loser.getPermissions().getOwner();
+        Component winMsg = Component.literal("Siege spoils: " + spoil + " coins transferred from "
+                + loser.getName() + " to " + winner.getName() + ".")
+                .withStyle(ChatFormatting.GOLD);
+        Component loseMsg = Component.literal("Siege fine: " + spoil + " coins paid from "
+                + loser.getName() + " to " + winner.getName() + ".")
+                .withStyle(ChatFormatting.RED);
+        if (winnerOwner != null) sendToPlayer(winnerOwner, winMsg);
+        if (loserOwner != null && !loserOwner.equals(winnerOwner)) sendToPlayer(loserOwner, loseMsg);
+    }
+
     private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
         int tributePct = TaxConfig.getBesiegeTributePercent();
         int durationHours = TaxConfig.getBesiegeTributeDurationHours();
@@ -663,6 +750,15 @@ public class BesiegeManager {
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
@@ -1100,6 +1196,12 @@ public class BesiegeManager {
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
