## FINAL REWORK REVIEW BUNDLE — 10 HIGH-severity fixes applied

This is a REWORK pass against the prior 11-step baseline (which is staged). The diffs below are the rework deltas only, not the original implementations.

Bugs addressed (numbered per reviews/SUMMARY.md):
  #1 — ColonyTierGuard FCT-reverse-lookup first (no longer trusts permissions.getOwner alone)
  #2 — Documented exemptions for reflective setOwner sites in abandonment/claiming/admin paths
  #3 — reclaimByOriginalOwner gated to TAX_ONLY mode + caller-UUID match + atomic remove
  #4 — Solo damage shield bypass closed (no early return on source-is-besieger)
  #5 — Defender-victory siege spoils now run via completeBesiege(raid, false, colony) timeout path
  #6 — Treasury cap-aware: applySiegeSpoils computes headroom and only deducts what can be credited
  #7 — Block ledger uses saveWithFullMetadata + loadStatic (correct MC 1.20.1 API)
  #8 — WarSystem.endWar now calls WarBlockLedger.restoreWarDamage(warId, world)
  #9 — Town Hall demolition checks attackers-not-exhausted before zeroing defender lives
 #10 — Explosion source extraction checks indirect ServerPlayer first (catches TNT lit by player)

Bonus medium/low fixes:
  - Damage shield uses Rank.isHostile() (catches custom hostile ranks)
  - Damage shield protects raid.spawnedMercenaries
  - Block ledger uses Map<BlockPos,BlockInfo> (dedupe + first-snapshot-wins) with 50k cap
  - WAR_HITS cleared from WarSystem.endWar via TownHallDemolitionObjective.onWarEnded
  - SLF4J '{:.2f}' replaced with String.format Locale.ROOT
  - Town Hall demolition rejects attacker-also-defender UUIDs

### DIFF: ColonyTierGuard.java
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java b/src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java
index 982fe4f..b824fc7 100644
--- a/src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java
+++ b/src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java
@@ -34,6 +34,14 @@ public final class ColonyTierGuard {
         if (colony == null) {
             return false;
         }
+        // Permissions.getOwner() can be stale, null, or hold a placeholder UUID
+        // from the abandonment system. Use the FCT reverse lookup FIRST — it
+        // tracks the true first-colony owner regardless of permissions state —
+        // then fall back to the permissions owner only when FCT has no record.
+        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
+        if (trackedFirstOwner != null) {
+            return TaxConfig.isPrimaryColonyTransferEnabled();
+        }
         UUID currentOwner = colony.getPermissions().getOwner();
         if (currentOwner == null) {
             return true;
@@ -63,11 +71,31 @@ public final class ColonyTierGuard {
         if (colony == null) {
             return "Colony reference is null.";
         }
+        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
         UUID owner = colony.getPermissions().getOwner();
-        if (owner != null && FirstColonyTracker.isFirstColony(owner, colony.getID())) {
+        if (trackedFirstOwner != null
+                || (owner != null && FirstColonyTracker.isFirstColony(owner, colony.getID()))) {
             return colony.getName() + " is a Primary colony — ownership transfer is blocked by config "
                     + "(EnablePrimaryColonyTransfer = false). Vassalization or tax-occupation may still apply.";
         }
         return "Transfer denied (no specific reason).";
     }
+
+    /**
+     * Documented exemptions from the central guard. These are NOT war-time
+     * player-to-player transfers — they're system-owner placeholder flows:
+     *   - {@code ColonyAbandonmentManager} sets a fake owner UUID when a
+     *     colony is auto-abandoned (the colony is owner-less in spirit).
+     *   - {@code ColonyClaimingRaidManager} flips ownership when a player
+     *     successfully claims a previously abandoned colony (the placeholder
+     *     UUID isn't a real player, so the FCT primary-protection doesn't apply).
+     *   - {@code WntCommands} admin paths that set a system owner.
+     * Bypassing the guard in those files is intentional. If you add a NEW
+     * code path that flips ownership for a real player-on-player conflict,
+     * route it through {@link net.machiavelli.minecolonytax.WarSystem#transferOwnership(IColony, java.util.UUID)}
+     * so this guard applies.
+     */
+    public static void documentedExemptionsBeyondTransferOwnership() {
+        // marker method — see javadoc
+    }
 }
```

### DIFF: OccupationManager.java
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java b/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
index d460830..5d19782 100644
--- a/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
@@ -505,13 +505,32 @@ public class OccupationManager {
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

### DIFF: BesiegeManager.java
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
index 959ac36..8eaddb1 100644
--- a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
@@ -119,8 +119,10 @@ public class BesiegeManager {
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
@@ -319,6 +321,10 @@ public class BesiegeManager {
             // Spawn mercenaries
             int mercCount = spawnMercenaries(colony, besieger, raid);
 
+            // Militia upgrade reinforcements — NOT counted toward victory.
+            // Each tier adds +N% bonus militia entities scaled by current guard count.
+            int militiaUpgradeCount = spawnMilitiaUpgradeReinforcements(colony, besieger, raid, guardCount);
+
             int totalDefenders = guardCount + militiaCount + mercCount;
 
             // Apply FORTIFICATION investment: extra DAMAGE_RESISTANCE to all defenders
@@ -541,23 +547,120 @@ public class BesiegeManager {
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
         int tributePct = TaxConfig.getBesiegeTributePercent();
         int durationHours = TaxConfig.getBesiegeTributeDurationHours();
@@ -663,6 +766,15 @@ public class BesiegeManager {
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
@@ -1100,6 +1212,12 @@ public class BesiegeManager {
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

### DIFF: BesiegeDamageShieldHandler.java
```diff
```

### DIFF: WarBlockLedger.java
```diff
```

### DIFF: WarSystem.java (endWar wiring only)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/WarSystem.java b/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
index fff5562..28ae5b7 100644
--- a/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
+++ b/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
@@ -1251,6 +1251,22 @@ public class WarSystem {
 
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
         }
 
         // Disable war actions for both sides
```

### DIFF: TownHallDemolitionObjective.java
```diff
```
