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
session id: 019e5cfe-7a3d-75c2-914b-52b5fb488036
--------
user
FINAL REWORK REVIEW. The prior pass shipped 11 design steps; codex flagged 10 HIGH-severity bugs across them. This bundle shows the REWORK deltas applied against the staged baseline (which has the original step implementations). Each diff is the change made to address the corresponding bug. Verify each numbered fix actually addresses the issue from the prior review:

#1 ColonyTierGuard now does FCT.getFirstColonyOwner reverse lookup FIRST
#2 Documented exemption block (no code routing change for system-owner reflective flows)
#3 reclaimByOriginalOwner gated to TAX_ONLY + caller match + atomic remove
#4 BesiegeDamageShieldHandler removed the sourceOwnRaid early-return bypass
#5 Timeout path in BesiegeManager.tick now routes through completeBesiege(raid, false, colony)
#6 applySiegeSpoils computes winner headroom and caps actualSpoil before deducting
#7 WarBlockLedger uses saveWithFullMetadata + loadStatic
#8 WarSystem.endWar now calls WarBlockLedger.restoreWarDamage
#9 Town Hall triggerVictory checks attackersExhausted before zeroing defender lives
#10 Source extraction now checks getIndirectSourceEntity for ServerPlayer first

Respond with: (1) STATUS APPROVE/REWORK/REJECT for the rework as a whole, (2) for EACH numbered bug: VERIFIED/PARTIAL/STILL-BROKEN with one line of justification, (3) up to 5 new findings if any (regressions or oversights introduced by the rework), (4) one-paragraph close. Max 500 words.

<stdin>
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
</stdin>
codex
I’ll review this as a code-review verification pass against the actual workspace where possible, then map the result back to the 10 numbered fixes. I’m checking the staged baseline plus working-tree deltas because several sections in the bundle are empty.
mcp: codex/list_mcp_resources started
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff --staged -- src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java src/main/java/net/machiavelli/minecolonytax/siege/WarBlockLedger.java src/main/java/net/machiavelli/minecolonytax/WarSystem.java src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff -- src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java src/main/java/net/machiavelli/minecolonytax/siege/WarBlockLedger.java src/main/java/net/machiavelli/minecolonytax/WarSystem.java src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
mcp: codex/list_mcp_resources (completed)
 succeeded in 366ms:
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
 
+            // Militia upgrade reinforcements â€” NOT counted toward victory.
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
diff --git a/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java b/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
index d460830..5d19782 100644
--- a/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
@@ -505,13 +505,32 @@ public class OccupationManager {
      * Manually end a tax-only occupation early â€” called when the owner successfully
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
+        // Atomic remove â€” bail if a concurrent caller already cleared it.
+        if (!ACTIVE_OCCUPATIONS.remove(colonyId, data)) return false;
         saveData();
 
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
diff --git a/src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java b/src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java
index 982fe4f..b824fc7 100644
--- a/src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java
+++ b/src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java
@@ -34,6 +34,14 @@ public final class ColonyTierGuard {
         if (colony == null) {
             return false;
         }
+        // Permissions.getOwner() can be stale, null, or hold a placeholder UUID
+        // from the abandonment system. Use the FCT reverse lookup FIRST â€” it
+        // tracks the true first-colony owner regardless of permissions state â€”
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
             return colony.getName() + " is a Primary colony â€” ownership transfer is blocked by config "
                     + "(EnablePrimaryColonyTransfer = false). Vassalization or tax-occupation may still apply.";
         }
         return "Transfer denied (no specific reason).";
     }
+
+    /**
+     * Documented exemptions from the central guard. These are NOT war-time
+     * player-to-player transfers â€” they're system-owner placeholder flows:
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
+        // marker method â€” see javadoc
+    }
 }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java', LF will be replaced by CRLF the next time Git touches it

 succeeded in 363ms:
diff --git a/src/main/java/net/machiavelli/minecolonytax/WarSystem.java b/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
index a6884a6..fff5562 100644
--- a/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
+++ b/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
@@ -2,7 +2,7 @@ package net.machiavelli.minecolonytax;
 
 import com.minecolonies.api.colony.IColony;
 import com.minecolonies.api.colony.IColonyManager;
-import com.minecolonies.api.colony.buildings.IBuilding; // Corrected import
+import com.minecolonies.api.colony.buildings.IBuilding;
 import com.minecolonies.api.colony.permissions.Action;
 import com.minecolonies.api.colony.permissions.IPermissions;
 import com.minecolonies.api.colony.permissions.Rank;
@@ -11,10 +11,12 @@ import dev.ftb.mods.ftbteams.FTBTeamsAPIImpl;
 import dev.ftb.mods.ftbteams.api.Team;
 import dev.ftb.mods.ftbteams.api.TeamManager;
 import dev.ftb.mods.ftbteams.data.PartyTeam;
+import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
 import net.machiavelli.minecolonytax.data.HistoryManager;
 import net.machiavelli.minecolonytax.data.PlayerWarDataManager;
 import net.machiavelli.minecolonytax.data.WarData;
 import net.machiavelli.minecolonytax.event.WarEconomyHandler;
+import net.machiavelli.minecolonytax.event.WarEventHandler;
 import net.machiavelli.minecolonytax.event.WarVictoryEvent;
 import net.minecraft.ChatFormatting;
 import net.minecraft.commands.CommandSourceStack;
@@ -29,17 +31,24 @@ import net.minecraft.world.level.GameType;
 import net.minecraft.world.level.Level;
 import net.minecraft.world.level.levelgen.Heightmap;
 import net.minecraft.world.scores.Scoreboard;
-import net.minecraft.world.level.Level;
 import net.minecraftforge.server.ServerLifecycleHooks;
-import net.machiavelli.minecolonytax.event.WarEventHandler;
 import net.machiavelli.minecolonytax.raid.GuardResistanceHandler;
+import net.machiavelli.minecolonytax.util.TickScheduler;
 import org.apache.logging.log4j.LogManager;
 import org.apache.logging.log4j.Logger;
 
-import java.time.Instant;
+import com.google.gson.Gson;
+import com.google.gson.GsonBuilder;
+import com.google.gson.reflect.TypeToken;
+import java.io.FileReader;
+import java.io.FileWriter;
+import java.io.Reader;
+import java.io.Writer;
+import java.lang.reflect.Type;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.nio.file.Paths;
 import java.util.*;
-import java.util.Timer;
-import java.util.TimerTask;
 import java.util.concurrent.ConcurrentHashMap;
 import java.util.concurrent.TimeUnit;
 
@@ -101,7 +110,7 @@ public class WarSystem {
                     .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                             Component.literal("Click to leave the war!").withStyle(ChatFormatting.AQUA))));
 
-    public static final long WAR_PHASE_DURATION_SECONDS = 60; // For debugging
+    public static final long WAR_PHASE_DURATION_SECONDS = 60;
 
     public static void initiateWar(ServerPlayer attacker, UUID defender, Team attackerTeam, Team defenderTeam,
             IColony colony, IColony attackerColony) {
@@ -122,17 +131,16 @@ public class WarSystem {
 
         int playerLives = TaxConfig.PLAYER_LIVES_IN_WAR.get(); // Use config
 
-        // Always start with the primary participants
         data.getAttackerLives().put(attacker.getUUID(), playerLives);
         data.getDefenderLives().put(colony.getPermissions().getOwner(), playerLives);
 
         // Assign hostile rank to the main attacker on defender's colony
         assignWarParticipantRanks(attacker.getUUID(), colony, attackerColony, true);
 
-        // Add attacker colony members (Officers and Friends) using Minecolonies API
         if (attackerColony != null) {
             IPermissions attackerPerms = attackerColony.getPermissions();
-            System.out.println("[DEBUG] Adding attacker colony members from " + attackerColony.getName());
+            if (TaxConfig.isDebugLogging())
+                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Adding attacker colony members from " + attackerColony.getName());
 
             attackerPerms.getPlayers().forEach((uuid, player) -> {
                 if (!uuid.equals(attacker.getUUID())) { // Don't add attacker twice
@@ -140,8 +148,9 @@ public class WarSystem {
                     if (rank != null && (rank.equals(attackerPerms.getRankOfficer())
                             || rank.equals(attackerPerms.getRankFriend()))) {
                         data.getAttackerLives().put(uuid, playerLives);
-                        System.out.println(
-                                "[DEBUG] Added attacker colony member " + uuid + " with rank " + rank.getName());
+                        if (TaxConfig.isDebugLogging())
+                            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
+                                    "[DEBUG] Added attacker colony member " + uuid + " with rank " + rank.getName());
 
                         // Assign hostile rank to this attacker on defender's colony
                         assignWarParticipantRanks(uuid, colony, attackerColony, true);
@@ -174,9 +183,9 @@ public class WarSystem {
             });
         }
 
-        // Add defender colony members (Officers and Friends) using Minecolonies API
         IPermissions defenderPerms = colony.getPermissions();
-        System.out.println("[DEBUG] Adding defender colony members from " + colony.getName());
+        if (TaxConfig.isDebugLogging())
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Adding defender colony members from " + colony.getName());
 
         defenderPerms.getPlayers().forEach((uuid, player) -> {
             if (!uuid.equals(colony.getPermissions().getOwner())) { // Don't add owner twice
@@ -184,7 +193,9 @@ public class WarSystem {
                 if (rank != null && (rank.equals(defenderPerms.getRankOfficer())
                         || rank.equals(defenderPerms.getRankFriend()))) {
                     data.getDefenderLives().put(uuid, playerLives);
-                    System.out.println("[DEBUG] Added defender colony member " + uuid + " with rank " + rank.getName());
+                    if (TaxConfig.isDebugLogging())
+                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
+                                "[DEBUG] Added defender colony member " + uuid + " with rank " + rank.getName());
 
                     // Assign hostile rank to this defender on attacker's colony (if it exists)
                     assignWarParticipantRanks(uuid, colony, attackerColony, false);
@@ -215,15 +226,16 @@ public class WarSystem {
             }
         });
 
-        // Optional: Add FTB Team members if FTB Teams is installed
         if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
-            System.out.println("[DEBUG] FTB Teams detected, adding team members as additional participants");
+            if (TaxConfig.isDebugLogging())
+                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] FTB Teams detected, adding team members as additional participants");
 
             if (attackerTeam != null) {
                 attackerTeam.getMembers().forEach(uuid -> {
                     if (!data.getAttackerLives().containsKey(uuid)) { // Don't add if already added via colony
                         data.getAttackerLives().put(uuid, playerLives);
-                        System.out.println("[DEBUG] Added FTB team member to attackers: " + uuid);
+                        if (TaxConfig.isDebugLogging())
+                            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Added FTB team member to attackers: " + uuid);
 
                         // Assign hostile rank to this attacker on defender's colony
                         assignWarParticipantRanks(uuid, colony, attackerColony, true);
@@ -253,7 +265,8 @@ public class WarSystem {
                 defenderTeam.getMembers().forEach(uuid -> {
                     if (!data.getDefenderLives().containsKey(uuid)) { // Don't add if already added via colony
                         data.getDefenderLives().put(uuid, playerLives);
-                        System.out.println("[DEBUG] Added FTB team member to defenders: " + uuid);
+                        if (TaxConfig.isDebugLogging())
+                            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Added FTB team member to defenders: " + uuid);
 
                         // Assign hostile rank to this defender on attacker's colony (if it exists)
                         assignWarParticipantRanks(uuid, colony, attackerColony, false);
@@ -287,16 +300,63 @@ public class WarSystem {
         data.initialDefenderTotalLives = data.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();
         ACTIVE_WARS.put(colony.getID(), data);
 
+        // Mark defender for home-field drain advantage
+        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(colony.getID());
+
+        // Schedule treasury drain every 60 seconds for both sides
+        scheduleTreasuryDrain(data, colony, attackerColony);
+
         // Apply War Exhaustion - both colonies generate less tax during war
         net.machiavelli.minecolonytax.economy.WarExhaustionManager.applyWarStatus(colony.getID());
         if (attackerColony != null) {
             net.machiavelli.minecolonytax.economy.WarExhaustionManager.applyWarStatus(attackerColony.getID());
         }
+
+        // Record war declaration in DB
+        net.machiavelli.minecolonytax.db.WarStatsDB.recordWarDeclared(
+                attacker.getUUID(), attacker.getName().getString(), defender);
+    }
+
+    /**
+     * Schedule a repeating treasury drain task for both attacker and defender.
+     * Drains every 60 seconds; also does a periodic save every 5 minutes.
+     */
+    private static void scheduleTreasuryDrain(WarData data, IColony defenderColony, IColony attackerColony) {
+        if (!TaxConfig.isTreasuryEnabled()) return;
+
+        final int defenderColonyId = defenderColony.getID();
+        final int attackerColonyId = attackerColony != null ? attackerColony.getID() : -1;
+        final long[] tickCount = {0}; // mutable counter for periodic save
+
+        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
+            tickCount[0]++;
+
+            int defenderResult = net.machiavelli.minecolonytax.economy.TreasuryManager.drainTreasury(defenderColonyId);
+            int attackerResult = attackerColonyId >= 0
+                    ? net.machiavelli.minecolonytax.economy.TreasuryManager.drainTreasury(attackerColonyId)
+                    : Integer.MAX_VALUE;
+
+            // Periodic save every 5 drain ticks (5 minutes)
+            if (tickCount[0] % 5 == 0) {
+                net.machiavelli.minecolonytax.economy.TreasuryManager.shutdown(); // calls saveData()
+            }
+
+            // Auto-surrender if either side is depleted
+            if (defenderResult == -1 || attackerResult == -1) {
+                IColony colony = defenderResult == -1 ? defenderColony : attackerColony;
+                if (colony != null) {
+                    endWar(colony);
+                }
+            }
+        }, 60_000, 60_000);
     }
 
     public static void setWarInteractionPermissions(IColony colony, boolean allowed) {
         if (!TaxConfig.ENABLE_WAR_ACTIONS.get())
             return;
+        if (allowed) {
+            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.snapshotBefore(colony);
+        }
         IPermissions perms = colony.getPermissions();
         Rank hostile = perms.getRankHostile();
         for (Action a : TaxConfig.getWarActions()) {
@@ -326,15 +386,18 @@ public class WarSystem {
                 // Attackers get hostile rank on defender colony
                 IPermissions defenderPerms = defenderColony.getPermissions();
                 defenderPerms.setPlayerRank(playerUUID, defenderPerms.getRankHostile(), defenderColony.getWorld());
-                System.out.println("[DEBUG] Assigned hostile rank to attacker " + playerUUID + " on defender colony "
-                        + defenderColony.getName());
+                if (TaxConfig.isDebugLogging())
+                    System.out
+                            .println("[DEBUG] Assigned hostile rank to attacker " + playerUUID + " on defender colony "
+                                    + defenderColony.getName());
             } else {
                 // Defenders get hostile rank on attacker colony (if it exists)
                 if (attackerColony != null) {
                     IPermissions attackerPerms = attackerColony.getPermissions();
                     attackerPerms.setPlayerRank(playerUUID, attackerPerms.getRankHostile(), attackerColony.getWorld());
-                    System.out.println("[DEBUG] Assigned hostile rank to defender " + playerUUID
-                            + " on attacker colony " + attackerColony.getName());
+                    if (TaxConfig.isDebugLogging())
+                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Assigned hostile rank to defender " + playerUUID
+                                + " on attacker colony " + attackerColony.getName());
                 }
             }
         } catch (Exception e) {
@@ -345,6 +408,9 @@ public class WarSystem {
     public static void setRaidInteractionPermissions(IColony colony, boolean allowed) {
         if (!TaxConfig.ENABLE_WAR_ACTIONS.get())
             return;
+        if (allowed) {
+            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.snapshotBefore(colony);
+        }
         IPermissions perms = colony.getPermissions();
         Rank hostile = perms.getRankHostile();
         for (Action a : TaxConfig.getRaidActions()) {
@@ -353,10 +419,14 @@ public class WarSystem {
     }
 
     /**
-     * Restore all colonies' war and raid permissions to their config defaults
+     * Restore all colonies' war, raid, and claiming permissions to their config defaults
      * (disabled).
      * Should be called on server startup to clean up any leftover permissions from
-     * crashes/restarts.
+     * crashes/restarts. This includes:
+     * - War actions on Hostile rank
+     * - Raid actions on Hostile rank
+     * - Claiming raid actions on Hostile rank (OPEN_CONTAINER etc.)
+     * - Claiming raid attack permissions on Neutral rank (HURT_CITIZEN, ATTACK_CITIZEN, etc.)
      */
     public static void restoreAllColonyPermissionsToDefaults() {
         try {
@@ -370,21 +440,77 @@ public class WarSystem {
             for (Level level : server.getAllLevels()) {
                 for (IColony colony : IColonyManager.getInstance().getColonies(level)) {
                     if (colony != null) {
-                        // Disable war actions (set to false)
+                        // Disable war actions on Hostile rank
                         setWarInteractionPermissions(colony, false);
-                        // Disable raid actions (set to false)
+                        // Disable raid actions on Hostile rank
                         setRaidInteractionPermissions(colony, false);
+                        // Disable claiming raid actions on Hostile rank + Neutral rank attack perms
+                        restoreClaimingPermissionsToDefaults(colony);
                         coloniesRestored++;
                     }
                 }
             }
 
-            WARSYSTEM_LOGGER.info("Restored war/raid permissions to config defaults for {} colonies", coloniesRestored);
+            WARSYSTEM_LOGGER.info("Restored war/raid/claiming permissions to config defaults for {} colonies", coloniesRestored);
         } catch (Exception e) {
             WARSYSTEM_LOGGER.error("Failed to restore colony permissions to defaults", e);
         }
     }
 
+    /**
+     * Revoke any leftover claiming-raid permissions from a colony.
+     * Called unconditionally on startup (does not check whether claiming system is enabled,
+     * because leftover permissions from a previous config must still be cleaned up).
+     */
+    private static void restoreClaimingPermissionsToDefaults(IColony colony) {
+        try {
+            IPermissions perms = colony.getPermissions();
+
+            // Clean up Hostile rank claiming actions (OPEN_CONTAINER is not in WarActions/RaidActions)
+            Rank hostile = perms.getRankHostile();
+            perms.setPermission(hostile, Action.OPEN_CONTAINER, false);
+
+            // Clean up Neutral rank attack permissions that claiming raids grant
+            Rank neutral = perms.getRankNeutral();
+            perms.setPermission(neutral, Action.HURT_CITIZEN, false);
+            perms.setPermission(neutral, Action.ATTACK_CITIZEN, false);
+            perms.setPermission(neutral, Action.HURT_VISITOR, false);
+            perms.setPermission(neutral, Action.ATTACK_ENTITY, false);
+            perms.setPermission(neutral, Action.SHOOT_ARROW, false);
+            perms.setPermission(neutral, Action.THROW_POTION, false);
+            perms.setPermission(neutral, Action.RIGHTCLICK_ENTITY, false);
+            perms.setPermission(neutral, Action.FILL_BUCKET, false);
+        } catch (Exception e) {
+            WARSYSTEM_LOGGER.debug("Error restoring claiming permissions for colony {}", colony.getID(), e);
+        }
+    }
+
+    /**
+     * Demote a set of players from the Hostile rank to Neutral on the given colony.
+     * Called at war end to clean up rank assignments made by assignWarParticipantRanks().
+     * Skips the colony owner and players who are not currently in the Hostile rank.
+     */
+    public static void demoteParticipantsFromHostile(IColony colony, java.util.Set<UUID> participants) {
+        if (colony == null || colony.getWorld() == null || participants == null) return;
+        IPermissions perms = colony.getPermissions();
+        Rank neutral = perms.getRankNeutral();
+        UUID colonyOwner = perms.getOwner();
+
+        for (UUID uuid : participants) {
+            if (uuid.equals(colonyOwner)) continue; // Never demote the colony owner
+            try {
+                Rank current = perms.getRank(uuid);
+                if (current != null && current.isHostile()) {
+                    perms.setPlayerRank(uuid, neutral, colony.getWorld());
+                    if (TaxConfig.isDebugLogging())
+                        WARSYSTEM_LOGGER.debug("Demoted {} from Hostile to Neutral on colony {} after war end.", uuid, colony.getName());
+                }
+            } catch (Exception e) {
+                WARSYSTEM_LOGGER.warn("Failed to demote player {} from hostile rank on colony {} at war end", uuid, colony.getName(), e);
+            }
+        }
+    }
+
     public static void updateBossBar(WarData war) {
         long now = System.currentTimeMillis();
         if (now < war.getJoinPhaseEndTime()) {
@@ -441,6 +567,7 @@ public class WarSystem {
                         .withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true));
                 broadcastToServer(ratioMsg);
             }
+            endWar(war.getColony()); // clean up â€” bare return would leak the war in ACTIVE_WARS
             return;
         }
 
@@ -570,12 +697,15 @@ public class WarSystem {
         if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
             return;
 
-        System.out.println(
-                "[DEBUG] War victory detected - Attackers win: " + attackersWin + ", Defenders win: " + defendersWin);
-        System.out.println(
-                "[DEBUG] All attackers dead: " + allAttackersDead + ", All defenders dead: " + allDefendersDead);
-        System.out.println("[DEBUG] Attacker guards: " + war.getRemainingAttackerGuards() + ", Defender guards: "
-                + war.getRemainingDefenderGuards());
+        if (TaxConfig.isDebugLogging()) {
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
+                    "[DEBUG] War victory detected - Attackers win: " + attackersWin + ", Defenders win: "
+                            + defendersWin);
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
+                    "[DEBUG] All attackers dead: " + allAttackersDead + ", All defenders dead: " + allDefendersDead);
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Attacker guards: " + war.getRemainingAttackerGuards() + ", Defender guards: "
+                    + war.getRemainingDefenderGuards());
+        }
 
         if (defendersWin) {
             String defenderColonyName = war.getColony().getName();
@@ -596,13 +726,14 @@ public class WarSystem {
                 if (defender != null) {
                 }
             }
-            applyWarEconomyTransfers(war, false);
-
-            // Record war loss for attacker (they lost when defenders won)
+            // Record war loss BEFORE economic transfers so immunity check uses pre-war
+            // balance
             if (war.getAttackerColony() != null) {
                 net.machiavelli.minecolonytax.economy.WarExhaustionManager
                         .recordWarLoss(war.getAttackerColony().getID());
             }
+            applyWarEconomyTransfers(war, false);
+
         } else if (attackersWin) {
             String defenderColonyName = war.getColony().getName();
             String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
@@ -625,14 +756,23 @@ public class WarSystem {
                     net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new WarVictoryEvent(attackerPlayer));
                 }
             }
-            // Apply victory/defeat balance transfers - attackers win, defenders pay
-            applyWarEconomyTransfers(war, true);
-
             // Record war loss for defender (they lost when attackers won)
             net.machiavelli.minecolonytax.economy.WarExhaustionManager.recordWarLoss(war.getColony().getID());
+            // Apply victory/defeat balance transfers - attackers win, defenders pay
+            applyWarEconomyTransfers(war, true);
 
             if (TaxConfig.ENABLE_COLONY_TRANSFER.get()) {
-                transferOwnership(war.getColony(), war.getAttacker());
+                if (TaxConfig.isOccupationSystemEnabled()) {
+                    // Occupation phase: occupier collects taxes but can't interact, original owner
+                    // has time to reclaim
+                    net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
+                            war.getColony(), war.getAttacker(), war.getAttackerColony());
+                    WARSYSTEM_LOGGER.info("Colony {} is now occupied by {} (occupation system active)",
+                            war.getColony().getName(), war.getAttacker());
+                } else {
+                    // Direct transfer (legacy behavior)
+                    transferOwnership(war.getColony(), war.getAttacker());
+                }
             } else if (TaxConfig.isWarVassalizationEnabled()) {
                 // Vassalize the losing colony instead of transferring ownership
                 int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
@@ -695,14 +835,14 @@ public class WarSystem {
             }
         }
 
-        int freezeHours = TaxConfig.getWarTaxFreezeHours();
-        if (freezeHours > 0) {
-            TaxManager.freezeColonyTax(war.getColony().getID(), freezeHours);
+        int freezeCycles = TaxConfig.getWarTaxFreezeCycles();
+        if (freezeCycles > 0) {
+            TaxManager.freezeColonyTax(war.getColony().getID(), freezeCycles);
             if (war.getAttackerColony() != null) {
-                TaxManager.freezeColonyTax(war.getAttackerColony().getID(), freezeHours);
+                TaxManager.freezeColonyTax(war.getAttackerColony().getID(), freezeCycles);
             }
             if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
-                String freezeMsg = "Tax generation frozen for " + freezeHours + " hours due to war stalemate!";
+                String freezeMsg = "Tax generation frozen for " + freezeCycles + " cycles due to war stalemate!";
                 Component notification = Component.literal(freezeMsg).withStyle(ChatFormatting.GOLD);
                 sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), notification);
             }
@@ -857,7 +997,7 @@ public class WarSystem {
                     int expectedTaxRevenue = 0;
 
                     // Calculate an expected tax based on the attacker's colony revenue potential
-                    for (IBuilding building : winnerColony.getBuildingManager().getBuildings().values()) {
+                    for (IBuilding building : ColonyBuildingUtil.getBuildings(winnerColony)) {
                         String buildingType = building.getBuildingDisplayName();
                         double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
                         double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType)
@@ -869,7 +1009,7 @@ public class WarSystem {
                     reparationsAmount = (int) (expectedTaxRevenue * transferPercentage);
 
                     // Ensure minimum reparations amount if any buildings exist
-                    if (reparationsAmount <= 0 && !winnerColony.getBuildingManager().getBuildings().isEmpty()) {
+                    if (reparationsAmount <= 0 && !ColonyBuildingUtil.getBuildings(winnerColony).isEmpty()) {
                         reparationsAmount = TaxConfig.getDebtLimit() / 10; // A minimum reparation amount
                     }
                 }
@@ -1039,20 +1179,62 @@ public class WarSystem {
         }
     }
 
-    public static void transferOwnership(IColony colony, UUID newOwnerUUID) {
-        if (colony.getWorld() == null || colony.getWorld().getServer() == null)
-            return;
+    /**
+     * Transfers a colony's deed to a new owner, OR routes the action through
+     * the appropriate fallback per the Siege SMP ruleset.
+     *
+     * @return true if the deed actually moved; false if the transfer was blocked
+     *         (e.g. primary colony protection), vassalized as fallback, or failed
+     *         for any other reason. Callers MUST inspect this so they don't
+     *         broadcast "permanently claimed" when the deed never moved.
+     */
+    public static boolean transferOwnership(IColony colony, UUID newOwnerUUID) {
+        if (colony == null) {
+            return false;
+        }
+        if (colony.getWorld() == null || colony.getWorld().getServer() == null) {
+            return false;
+        }
+
+        // Siege SMP ruleset: primary colonies are protected from ownership transfer
+        // by default. Fall back to vassalization if enabled, so the war still has
+        // a meaningful consequence for the loser.
+        if (!net.machiavelli.minecolonytax.permissions.ColonyTierGuard.canTransferOwnership(colony)) {
+            WARSYSTEM_LOGGER.info("Ownership transfer denied for colony {}: {}",
+                    colony.getID(),
+                    net.machiavelli.minecolonytax.permissions.ColonyTierGuard.getTransferDenialReason(colony));
+            if (TaxConfig.isWarVassalizationEnabled()) {
+                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
+                int durationHours = TaxConfig.getWarVassalizationDurationHours();
+                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
+                        colony, newOwnerUUID, tributePercent, durationHours);
+                if (vassalized) {
+                    WARSYSTEM_LOGGER.info("Primary colony {} vassalized by {} instead of transferred ({}% tribute for {}h)",
+                            colony.getName(), newOwnerUUID, tributePercent, durationHours);
+                    WarData war = ACTIVE_WARS.get(colony.getID());
+                    Component msg = Component.literal(colony.getName()
+                            + " is a Primary colony â€” vassalized instead of conquered.")
+                            .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true));
+                    sendNotificationToWarParticipants(colony, war != null ? war.getAttackerColony() : null, msg);
+                }
+            }
+            return false;
+        }
+
         ServerPlayer newOwner = colony.getWorld().getServer().getPlayerList().getPlayer(newOwnerUUID);
-        if (newOwner == null)
-            return;
+        if (newOwner == null) {
+            return false;
+        }
         if (colony.getPermissions().setOwner(newOwner)) {
             colony.markDirty();
             Component msg = Component.literal(colony.getName() + " conquered by " + newOwner.getName().getString())
                     .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED).withBold(true));
             WarData war = ACTIVE_WARS.get(colony.getID());
             sendNotificationToWarParticipants(colony, war != null ? war.getAttackerColony() : null, msg);
+            return true;
         } else {
             WARSYSTEM_LOGGER.error("Ownership transfer failed for colony {}", colony.getID());
+            return false;
         }
     }
 
@@ -1079,9 +1261,26 @@ public class WarSystem {
             setWarInteractionPermissions(warData.getAttackerColony(), false);
         }
 
+        // Demote war participants out of the Hostile rank on both colonies.
+        // Attackers were assigned hostile on the defender colony; defenders on the attacker colony.
+        if (warData != null) {
+            if (warData.getAttackerLives() != null) {
+                demoteParticipantsFromHostile(colony, warData.getAttackerLives().keySet());
+            }
+            if (warData.getAttackerColony() != null && warData.getDefenderLives() != null) {
+                demoteParticipantsFromHostile(warData.getAttackerColony(), warData.getDefenderLives().keySet());
+            }
+        }
+
         // Now remove from active wars
         warData = ACTIVE_WARS.remove(colony.getID());
 
+        // Restore Hostile rank to pre-war state now that the war is no longer active
+        net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(colony);
+        if (warData != null && warData.getAttackerColony() != null) {
+            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(warData.getAttackerColony());
+        }
+
         // Remove War Exhaustion status and start recovery period
         net.machiavelli.minecolonytax.economy.WarExhaustionManager.removeWarStatus(colony.getID());
         if (warData != null && warData.getAttackerColony() != null) {
@@ -1089,10 +1288,20 @@ public class WarSystem {
                     .removeWarStatus(warData.getAttackerColony().getID());
         }
 
+        // Clear war chest roles for both sides
+        net.machiavelli.minecolonytax.economy.TreasuryManager.clearColonyRole(colony.getID());
+        if (warData != null && warData.getAttackerColony() != null) {
+            net.machiavelli.minecolonytax.economy.TreasuryManager.clearColonyRole(warData.getAttackerColony().getID());
+        }
+
         if (warData != null) {
-            if (warData.timerTask != null) {
-                warData.timerTask.cancel();
-                warData.timerTask = null;
+            if (warData.countdownTaskId >= 0) {
+                TickScheduler.cancel(warData.countdownTaskId);
+                warData.countdownTaskId = -1;
+            }
+            if (warData.warChestDrainTaskId >= 0) {
+                TickScheduler.cancel(warData.warChestDrainTaskId);
+                warData.warChestDrainTaskId = -1;
             }
             if (warData.bossEvent != null) {
                 warData.bossEvent.removeAllPlayers();
@@ -1202,12 +1411,19 @@ public class WarSystem {
                 boolean isDefenderVictory = warData.getRemainingDefenderGuards() > 0;
                 Map<UUID, Integer> winnerLivesMap = isDefenderVictory ? warData.getDefenderLives()
                         : warData.getAttackerLives();
+                Map<UUID, Integer> loserLivesMap = isDefenderVictory ? warData.getAttackerLives()
+                        : warData.getDefenderLives();
                 if (colony.getWorld() != null && colony.getWorld().getServer() != null) {
                     for (UUID uuid : winnerLivesMap.keySet()) {
                         ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
                         if (player != null)
                             PlayerWarDataManager.incrementWarsWon(player);
                     }
+                    for (UUID uuid : loserLivesMap.keySet()) {
+                        ServerPlayer player = colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
+                        if (player != null)
+                            PlayerWarDataManager.incrementWarsLost(player);
+                    }
                 }
                 if (!TaxConfig.ENABLE_COLONY_TRANSFER.get()) {
                     IColony loserColonyActual = isDefenderVictory ? warData.getAttackerColony() : warData.getColony();
@@ -1231,16 +1447,38 @@ public class WarSystem {
                 outcome = warData.getPenaltyReport();
             }
 
+            // Record war outcome in DB
+            boolean ledToOccupation = warData.getPenaltyReport().contains("TOTAL VICTORY")
+                    && TaxConfig.ENABLE_COLONY_TRANSFER.get();
+            net.machiavelli.minecolonytax.db.WarStatsDB.recordWarEnd(
+                    warData,
+                    net.machiavelli.minecolonytax.db.WarStatsDB.determineOutcome(warData),
+                    amountTransferred,
+                    ledToOccupation);
+
             String attackerName = warData.getAttackerColony() != null ? warData.getAttackerColony().getName()
                     : "Unknown Attacker";
-            String eventString = String.format(
-                    "[WAR] Colony '%s' was attacked by '%s'. Outcome: %s. Amount Transferred: %d",
-                    colony.getName(),
-                    attackerName,
-                    outcome,
-                    amountTransferred);
-
-            HistoryManager.getColonyHistory(colony.getID()).addEvent(eventString);
+
+            // Determine outcome from each colony's perspective
+            boolean isStalemate = warData.getPenaltyReport().isEmpty()
+                    || warData.getPenaltyReport().toLowerCase().contains("stalemate");
+            boolean defenderWon = !isStalemate && warData.getPenaltyReport().contains("TOTAL VICTORY")
+                    && warData.getRemainingDefenderGuards() > 0;
+            String defenderOutcome = isStalemate ? "STALEMATE" : (defenderWon ? "VICTORY" : "DEFEAT");
+            String attackerOutcome = isStalemate ? "STALEMATE" : (defenderWon ? "DEFEAT" : "VICTORY");
+
+            // addWarEntry also writes a legacy string for WarHistoryCommand compatibility
+            int _defBefore = TaxManager.getStoredTaxForColonyId(colony.getID());
+            int _defAfter  = _defBefore - (int) amountTransferred;
+            int _atkBefore = warData.getAttackerColony() != null
+                    ? TaxManager.getStoredTaxForColonyId(warData.getAttackerColony().getID()) : 0;
+            int _atkAfter  = _atkBefore + (int) amountTransferred;
+            HistoryManager.getColonyHistory(colony.getID())
+                    .addWarEntry(attackerName, defenderOutcome, amountTransferred, _defBefore, _defAfter);
+            if (warData.getAttackerColony() != null) {
+                HistoryManager.getColonyHistory(warData.getAttackerColony().getID())
+                        .addWarEntry(colony.getName(), attackerOutcome, amountTransferred, _atkBefore, _atkAfter);
+            }
             HistoryManager.saveHistory();
             WARSYSTEM_LOGGER.info("War ended for colony {}", colony.getName());
         }
@@ -1339,6 +1577,11 @@ public class WarSystem {
                 if (p != null)
                     PlayerWarDataManager.incrementWarsWon(p);
             }
+            for (UUID atkUUID : war.getAttackerLives().keySet()) {
+                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(atkUUID);
+                if (p != null)
+                    PlayerWarDataManager.incrementWarsLost(p);
+            }
             handleVictoryRewards(war, true); // true for defender victory
             endWar(war.getColony());
             return;
@@ -1359,6 +1602,11 @@ public class WarSystem {
                 if (p != null)
                     PlayerWarDataManager.incrementWarsWon(p);
             }
+            for (UUID defUUID : war.getDefenderLives().keySet()) {
+                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defUUID);
+                if (p != null)
+                    PlayerWarDataManager.incrementWarsLost(p);
+            }
             handleVictoryRewards(war, false); // false for attacker victory
             endWar(war.getColony());
             return;
@@ -1439,6 +1687,11 @@ public class WarSystem {
                 if (p != null)
                     PlayerWarDataManager.incrementWarsWon(p);
             }
+            for (UUID atkUUID : war.getAttackerLives().keySet()) {
+                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(atkUUID);
+                if (p != null)
+                    PlayerWarDataManager.incrementWarsLost(p);
+            }
         } else if (defenderNormalizedStrength + epsilon < attackerNormalizedStrength) { // Defenders lost proportionally
                                                                                         // more
             reportOutcome = "Strategic Victory: Attackers win! Defenders lost proportionally more strength.";
@@ -1469,6 +1722,11 @@ public class WarSystem {
                 if (p != null)
                     PlayerWarDataManager.incrementWarsWon(p);
             }
+            for (UUID defUUID : war.getDefenderLives().keySet()) {
+                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defUUID);
+                if (p != null)
+                    PlayerWarDataManager.incrementWarsLost(p);
+            }
         } else { // Proportional losses are too close - stalemate
             reportOutcome = "Stalemate (Timeout - Proportional Losses): Both sides fought hard but neither gained a clear advantage. Penalties apply.";
             strategicMsg = Component.translatable("war.time.expired.title")
@@ -1544,9 +1802,74 @@ public class WarSystem {
         Map<UUID, Integer> winnerLives = defendersWon ? war.getDefenderLives() : war.getAttackerLives();
         IColony loserColony = defendersWon ? war.getAttackerColony() : war.getColony();
 
-        if (TaxConfig.ENABLE_COLONY_TRANSFER.get() && !defendersWon) { // Attackers win and transfer is on
-            transferOwnership(war.getColony(), war.getAttacker());
-            war.setPenaltyReport("TOTAL VICTORY - Colony transferred to attackers!");
+        if (TaxConfig.ENABLE_COLONY_TRANSFER.get() && !defendersWon) { // ATTACKER WINS - Target colony enters occupied
+                                                                       // state
+            if (TaxConfig.isOccupationSystemEnabled()) {
+                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
+                        war.getColony(), war.getAttacker(), war.getAttackerColony());
+                war.setPenaltyReport("TOTAL VICTORY - Colony is now OCCUPIED! Original owner has " +
+                        TaxConfig.getOccupationDurationDays() + " days to reclaim.");
+            } else {
+                transferOwnership(war.getColony(), war.getAttacker());
+                war.setPenaltyReport("TOTAL VICTORY - Colony transferred to attackers!");
+            }
+        } else if (TaxConfig.isColonyWagerEnabled() && defendersWon && war.getAttackerColony() != null) {
+            // DEFENDER WINS with COLONY WAGER enabled - Attacker's wagered colony enters
+            // occupied state!
+            if (TaxConfig.isOccupationSystemEnabled()) {
+                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
+                        war.getAttackerColony(), war.getDefender(), war.getColony());
+                war.setPenaltyReport("âš” COUNTER-CONQUEST! The attacker's colony is now OCCUPIED by the defenders! " +
+                        "Attacker has " + TaxConfig.getOccupationDurationDays() + " days to reclaim.");
+
+                // Notify both sides about the wager outcome
+                if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
+                    Component wagerLostMsg = Component.empty()
+                            .append(Component.literal("âš” WAGER LOST! âš”").withStyle(ChatFormatting.DARK_RED,
+                                    ChatFormatting.BOLD))
+                            .append(Component.literal("\nYou attacked and LOST! Your colony ")
+                                    .withStyle(ChatFormatting.RED))
+                            .append(Component.literal(war.getAttackerColony().getName()).withStyle(ChatFormatting.GOLD,
+                                    ChatFormatting.BOLD))
+                            .append(Component.literal(" is now OCCUPIED by the defenders!")
+                                    .withStyle(ChatFormatting.RED))
+                            .append(Component
+                                    .literal("\nYou have " + TaxConfig.getOccupationDurationDays()
+                                            + " days to wage a reclamation war.")
+                                    .withStyle(ChatFormatting.YELLOW));
+
+                    Component wagerWonMsg = Component.empty()
+                            .append(Component.literal("âš” COUNTER-CONQUEST! âš”").withStyle(ChatFormatting.GOLD,
+                                    ChatFormatting.BOLD))
+                            .append(Component.literal(
+                                    "\nYou successfully defended your colony and captured the attacker's wagered colony ")
+                                    .withStyle(ChatFormatting.GREEN))
+                            .append(Component.literal(war.getAttackerColony().getName()).withStyle(ChatFormatting.GOLD,
+                                    ChatFormatting.BOLD))
+                            .append(Component.literal("!").withStyle(ChatFormatting.GREEN))
+                            .append(Component.literal("\nYou can now collect "
+                                    + (int) (TaxConfig.getOccupationTaxPercentage() * 100) + "% of their taxes!")
+                                    .withStyle(ChatFormatting.YELLOW));
+
+                    // Notify attacker
+                    ServerPlayer attackerPlayer = war.getColony().getWorld().getServer().getPlayerList()
+                            .getPlayer(war.getAttacker());
+                    if (attackerPlayer != null) {
+                        attackerPlayer.sendSystemMessage(wagerLostMsg);
+                    }
+
+                    // Notify defender
+                    ServerPlayer defenderPlayer = war.getColony().getWorld().getServer().getPlayerList()
+                            .getPlayer(war.getDefender());
+                    if (defenderPlayer != null) {
+                        defenderPlayer.sendSystemMessage(wagerWonMsg);
+                    }
+                }
+            } else {
+                // No occupation system - direct transfer of attacker's colony to defender
+                transferOwnership(war.getAttackerColony(), war.getDefender());
+                war.setPenaltyReport("âš” COUNTER-CONQUEST! Attacker's colony transferred to the defenders!");
+            }
         } else {
             if (loserColony == null) {
                 war.setPenaltyReport("TOTAL VICTORY - Loser colony not found for economic penalties.");
@@ -1578,50 +1901,63 @@ public class WarSystem {
     }
 
     public static Map<UUID, Integer> getLivesForPlayer(WarData war, ServerPlayer player) {
-        System.out.println("[DEBUG] getLivesForPlayer called for player " + player.getName().getString() + " ("
-                + player.getUUID() + ")");
-        System.out.println("[DEBUG] FTB_TEAMS_INSTALLED: " + FTB_TEAMS_INSTALLED);
-        System.out.println("[DEBUG] Attacker lives: " + war.getAttackerLives());
-        System.out.println("[DEBUG] Defender lives: " + war.getDefenderLives());
+        if (TaxConfig.isDebugLogging()) {
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] getLivesForPlayer called for player " + player.getName().getString() + " ("
+                    + player.getUUID() + ")");
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] FTB_TEAMS_INSTALLED: " + FTB_TEAMS_INSTALLED);
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Attacker lives: " + war.getAttackerLives());
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Defender lives: " + war.getDefenderLives());
+        }
 
         // First check if player is directly in the lives maps
         UUID playerUUID = player.getUUID();
-        System.out.println("[DEBUG] Checking if attacker lives contains player UUID: "
-                + war.getAttackerLives().containsKey(playerUUID));
-        System.out.println("[DEBUG] Checking if defender lives contains player UUID: "
-                + war.getDefenderLives().containsKey(playerUUID));
+        if (TaxConfig.isDebugLogging()) {
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Checking if attacker lives contains player UUID: "
+                    + war.getAttackerLives().containsKey(playerUUID));
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Checking if defender lives contains player UUID: "
+                    + war.getDefenderLives().containsKey(playerUUID));
+        }
 
         if (war.getAttackerLives().containsKey(playerUUID)) {
-            System.out.println("[DEBUG] Player found in attacker lives, returning attacker lives");
+            if (TaxConfig.isDebugLogging())
+                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player found in attacker lives, returning attacker lives");
             return war.getAttackerLives();
         } else if (war.getDefenderLives().containsKey(playerUUID)) {
-            System.out.println("[DEBUG] Player found in defender lives, returning defender lives");
+            if (TaxConfig.isDebugLogging())
+                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player found in defender lives, returning defender lives");
             return war.getDefenderLives();
         }
 
         // Check if player is in attacker or defender allies
         if (war.getAttackerAllies().contains(playerUUID)) {
-            System.out.println("[DEBUG] Player found in attacker allies, returning attacker lives");
+            if (TaxConfig.isDebugLogging())
+                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player found in attacker allies, returning attacker lives");
             return war.getAttackerLives();
         } else if (war.getDefenderAllies().contains(playerUUID)) {
-            System.out.println("[DEBUG] Player found in defender allies, returning defender lives");
+            if (TaxConfig.isDebugLogging())
+                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player found in defender allies, returning defender lives");
             return war.getDefenderLives();
         }
 
         if (FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null) {
             Optional<Team> teamOpt = FTB_TEAM_MANAGER.getPlayerTeamForPlayerID(playerUUID);
-            System.out.println("[DEBUG] Player team found: " + teamOpt.isPresent());
+            if (TaxConfig.isDebugLogging())
+                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player team found: " + teamOpt.isPresent());
             if (teamOpt.isPresent()) {
                 Team team = teamOpt.get();
-                System.out.println("[DEBUG] Player team ID: " + team.getId());
-                System.out.println("[DEBUG] War attacker team ID: " + war.getAttackerTeamID());
-                System.out.println("[DEBUG] War defender team ID: " + war.getDefenderTeamID());
+                if (TaxConfig.isDebugLogging()) {
+                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player team ID: " + team.getId());
+                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] War attacker team ID: " + war.getAttackerTeamID());
+                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] War defender team ID: " + war.getDefenderTeamID());
+                }
 
                 if (team.getId().equals(war.getAttackerTeamID())) {
-                    System.out.println("[DEBUG] Player is on attacker team, returning attacker lives");
+                    if (TaxConfig.isDebugLogging())
+                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is on attacker team, returning attacker lives");
                     return war.getAttackerLives();
                 } else if (team.getId().equals(war.getDefenderTeamID())) {
-                    System.out.println("[DEBUG] Player is on defender team, returning defender lives");
+                    if (TaxConfig.isDebugLogging())
+                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is on defender team, returning defender lives");
                     return war.getDefenderLives();
                 }
 
@@ -1629,20 +1965,25 @@ public class WarSystem {
                 Team atkTeam = FTB_TEAM_MANAGER.getTeamByID(war.getAttackerTeamID()).orElse(null);
                 if (atkTeam != null && atkTeam.isPartyTeam()
                         && ((PartyTeam) atkTeam).getMembers().contains(playerUUID)) {
-                    System.out.println("[DEBUG] Player is allied to attacker team, returning attacker lives");
+                    if (TaxConfig.isDebugLogging())
+                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is allied to attacker team, returning attacker lives");
                     return war.getAttackerLives();
                 }
 
                 Team defTeam = FTB_TEAM_MANAGER.getTeamByID(war.getDefenderTeamID()).orElse(null);
                 if (defTeam != null && defTeam.isPartyTeam()
                         && ((PartyTeam) defTeam).getMembers().contains(playerUUID)) {
-                    System.out.println("[DEBUG] Player is allied to defender team, returning defender lives");
+                    if (TaxConfig.isDebugLogging())
+                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is allied to defender team, returning defender lives");
                     return war.getDefenderLives();
                 }
 
-                System.out.println("[DEBUG] Player team not participating in war, checking Minecolonies membership");
+                if (TaxConfig.isDebugLogging())
+                    System.out
+                            .println("[DEBUG] Player team not participating in war, checking Minecolonies membership");
             } else {
-                System.out.println("[DEBUG] Player has no FTB team, checking Minecolonies membership");
+                if (TaxConfig.isDebugLogging())
+                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player has no FTB team, checking Minecolonies membership");
             }
         }
 
@@ -1650,25 +1991,30 @@ public class WarSystem {
         IColony attackerColony = war.getAttackerColony();
         IColony defenderColony = war.getColony();
 
-        System.out.println("[DEBUG] Checking Minecolonies membership - Attacker colony: "
-                + (attackerColony != null ? attackerColony.getName() : "null"));
-        System.out.println("[DEBUG] Checking Minecolonies membership - Defender colony: "
-                + (defenderColony != null ? defenderColony.getName() : "null"));
+        if (TaxConfig.isDebugLogging()) {
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Checking Minecolonies membership - Attacker colony: "
+                    + (attackerColony != null ? attackerColony.getName() : "null"));
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Checking Minecolonies membership - Defender colony: "
+                    + (defenderColony != null ? defenderColony.getName() : "null"));
+        }
 
         // Check if player is in attacker colony (owner, officer, or friend)
         if (attackerColony != null) {
             IPermissions attackerPerms = attackerColony.getPermissions();
-            System.out.println("[DEBUG] Player in attacker colony players list: "
-                    + attackerPerms.getPlayers().containsKey(playerUUID));
+            if (TaxConfig.isDebugLogging())
+                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player in attacker colony players list: "
+                        + attackerPerms.getPlayers().containsKey(playerUUID));
             if (attackerPerms.getPlayers().containsKey(playerUUID)) {
                 Rank playerRank = attackerPerms.getRank(playerUUID);
-                System.out.println("[DEBUG] Player rank in attacker colony: "
-                        + (playerRank != null ? playerRank.getName() : "null"));
+                if (TaxConfig.isDebugLogging())
+                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player rank in attacker colony: "
+                            + (playerRank != null ? playerRank.getName() : "null"));
                 if (playerRank != null && (playerRank.equals(attackerPerms.getRankOwner()) ||
                         playerRank.equals(attackerPerms.getRankOfficer()) ||
                         playerRank.equals(attackerPerms.getRankFriend()))) {
-                    System.out.println("[DEBUG] Player is in attacker colony with rank " + playerRank.getName()
-                            + ", returning attacker lives");
+                    if (TaxConfig.isDebugLogging())
+                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is in attacker colony with rank " + playerRank.getName()
+                                + ", returning attacker lives");
                     return war.getAttackerLives();
                 }
             }
@@ -1677,23 +2023,27 @@ public class WarSystem {
         // Check if player is in defender colony (owner, officer, or friend)
         if (defenderColony != null) {
             IPermissions defenderPerms = defenderColony.getPermissions();
-            System.out.println("[DEBUG] Player in defender colony players list: "
-                    + defenderPerms.getPlayers().containsKey(playerUUID));
+            if (TaxConfig.isDebugLogging())
+                if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player in defender colony players list: "
+                        + defenderPerms.getPlayers().containsKey(playerUUID));
             if (defenderPerms.getPlayers().containsKey(playerUUID)) {
                 Rank playerRank = defenderPerms.getRank(playerUUID);
-                System.out.println("[DEBUG] Player rank in defender colony: "
-                        + (playerRank != null ? playerRank.getName() : "null"));
+                if (TaxConfig.isDebugLogging())
+                    if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player rank in defender colony: "
+                            + (playerRank != null ? playerRank.getName() : "null"));
                 if (playerRank != null && (playerRank.equals(defenderPerms.getRankOwner()) ||
                         playerRank.equals(defenderPerms.getRankOfficer()) ||
                         playerRank.equals(defenderPerms.getRankFriend()))) {
-                    System.out.println("[DEBUG] Player is in defender colony with rank " + playerRank.getName()
-                            + ", returning defender lives");
+                    if (TaxConfig.isDebugLogging())
+                        if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player is in defender colony with rank " + playerRank.getName()
+                                + ", returning defender lives");
                     return war.getDefenderLives();
                 }
             }
         }
 
-        System.out.println("[DEBUG] Player not participating in war, returning empty map");
+        if (TaxConfig.isDebugLogging())
+            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Player not participating in war, returning empty map");
         return new HashMap<>(); // Return mutable map instead of Collections.emptyMap()
     }
 
@@ -1769,47 +2119,40 @@ public class WarSystem {
     }
 
     public static void scheduleTimerWarnings(WarData war, long warDurationMillis) {
-        Timer warningTimer = new Timer();
         long quarter = warDurationMillis / 4;
         for (int i = 1; i <= 3; i++) {
             long delay = quarter * i;
             if (delay <= 0)
                 continue;
-            warningTimer.schedule(new TimerTask() {
-                @Override
-                public void run() {
-                    // Check if war still exists in active wars or if the colony world/server is
-                    // null
-                    if (!ACTIVE_WARS.containsKey(war.getColony().getID()) ||
-                            war.getColony().getWorld() == null ||
-                            war.getColony().getWorld().getServer() == null ||
-                            war.bossEvent == null) {
-                        this.cancel();
-                        return;
-                    }
+            TickScheduler.scheduleDelayed(() -> {
+                // Check if war still exists in active wars or if the colony world/server is
+                // null
+                if (!ACTIVE_WARS.containsKey(war.getColony().getID()) ||
+                        war.getColony().getWorld() == null ||
+                        war.getColony().getWorld().getServer() == null ||
+                        war.bossEvent == null) {
+                    return;
+                }
 
-                    // Check war status - don't process for ended wars
-                    if (war.getStatus() != WarData.WarStatus.INWAR) {
-                        this.cancel();
-                        return;
-                    }
+                // Check war status - don't process for ended wars
+                if (war.getStatus() != WarData.WarStatus.INWAR) {
+                    return;
+                }
 
-                    long elapsedSeconds = (System.currentTimeMillis() - war.warStartTime) / 1000;
-                    long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
-                    long remaining = Math.max(0, warDurationSeconds - elapsedSeconds);
-                    String bossText = String.format("War: Attacker Lives: %d | Defender Lives: %d | Time: %02d:%02d",
-                            war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum(),
-                            war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum(),
-                            remaining / 60, remaining % 60);
-                    Component newName = Component.literal(bossText);
-                    float newProgress = (float) remaining / warDurationSeconds;
-                    war.bossEvent.setName(newName);
-                    war.bossEvent.setProgress(newProgress);
-                    war.bossEvent.setVisible(true);
-                    if (remaining <= 0) {
-                        handleTimeExpiry(war);
-                        this.cancel();
-                    }
+                long elapsedSeconds = (System.currentTimeMillis() - war.warStartTime) / 1000;
+                long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
+                long remaining = Math.max(0, warDurationSeconds - elapsedSeconds);
+                String bossText = String.format("War: Attacker Lives: %d | Defender Lives: %d | Time: %02d:%02d",
+                        war.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum(),
+                        war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum(),
+                        remaining / 60, remaining % 60);
+                Component newName = Component.literal(bossText);
+                float newProgress = (float) remaining / warDurationSeconds;
+                war.bossEvent.setName(newName);
+                war.bossEvent.setProgress(newProgress);
+                war.bossEvent.setVisible(true);
+                if (remaining <= 0) {
+                    handleTimeExpiry(war);
                 }
             }, delay);
         }
@@ -1846,6 +2189,10 @@ public class WarSystem {
     public static void onPlayerKilledInWar(ServerPlayer killer, ServerPlayer killed, WarData war) {
         if (killer != null && killed != null && war != null) {
             PlayerWarDataManager.incrementPlayersKilledInWar(killer);
+            PlayerWarDataManager.incrementTimesKilledInWar(killed);
+            net.machiavelli.minecolonytax.db.WarStatsDB.recordWarKill(
+                    killer.getUUID(), killer.getName().getString(),
+                    killed.getUUID(), killed.getName().getString());
         }
     }
 
@@ -1870,10 +2217,13 @@ public class WarSystem {
         WarData war = getActiveWarForPlayer(owner);
 
         int configuredMinutes = TaxConfig.JOIN_PHASE_DURATION_MINUTES.get();
-        WARSYSTEM_LOGGER.info("[DEBUG] JOIN_PHASE_DURATION_MINUTES config value: {} minutes", configuredMinutes);
-        WARSYSTEM_LOGGER.info("[DEBUG] Config spec: {}", TaxConfig.CONFIG.getClass().getName());
-        WARSYSTEM_LOGGER.info("[DEBUG] Config default value: {}", TaxConfig.JOIN_PHASE_DURATION_MINUTES.getDefault());
-        WARSYSTEM_LOGGER.info("[DEBUG] Config is loaded: {}", TaxConfig.CONFIG.isLoaded());
+        if (TaxConfig.isDebugLogging()) {
+            WARSYSTEM_LOGGER.info("[DEBUG] JOIN_PHASE_DURATION_MINUTES config value: {} minutes", configuredMinutes);
+            WARSYSTEM_LOGGER.info("[DEBUG] Config spec: {}", TaxConfig.CONFIG.getClass().getName());
+            WARSYSTEM_LOGGER.info("[DEBUG] Config default value: {}",
+                    TaxConfig.JOIN_PHASE_DURATION_MINUTES.getDefault());
+            WARSYSTEM_LOGGER.info("[DEBUG] Config is loaded: {}", TaxConfig.CONFIG.isLoaded());
+        }
 
         if (ServerLifecycleHooks.getCurrentServer() != null) {
             // Get the time remaining in a readable format
@@ -1955,74 +2305,61 @@ public class WarSystem {
         // Add countdown sound timer for the last 6 seconds of join phase, but only if
         // join phase is at least 6 seconds long
         if (joinDurationMillis >= 6000) {
-            new Timer().schedule(new TimerTask() {
-                int secondsLeft = 6;
-
-                @Override
-                public void run() {
-                    try {
-                        if (war == null || war.getColony() == null || !war.isJoinPhaseActive()) {
-                            this.cancel();
-                            return;
-                        }
+            final int[] secondsLeft = { 6 };
+            TickScheduler.scheduleRepeating(() -> {
+                try {
+                    if (war == null || war.getColony() == null || !war.isJoinPhaseActive() || secondsLeft[0] < 0) {
+                        return; // Task will be cleaned up when war starts or ends
+                    }
 
-                        // Play countdown sound to all war participants
-                        Set<UUID> allParticipants = new HashSet<>();
-                        allParticipants.addAll(war.getAttackerLives().keySet());
-                        allParticipants.addAll(war.getDefenderLives().keySet());
-
-                        // Only play sound if there are participants
-                        if (!allParticipants.isEmpty()) {
-                            // Play countdown sound using Minecraft's bell sound
-                            for (UUID uuid : allParticipants) {
-                                ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList()
-                                        .getPlayer(uuid);
-                                if (player != null) {
-                                    player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.get(), 1.0F,
-                                            1.0F);
-                                }
+                    // Play countdown sound to all war participants
+                    Set<UUID> allParticipants = new HashSet<>();
+                    allParticipants.addAll(war.getAttackerLives().keySet());
+                    allParticipants.addAll(war.getDefenderLives().keySet());
+
+                    // Only play sound if there are participants
+                    if (!allParticipants.isEmpty()) {
+                        // Play countdown sound using Minecraft's bell sound
+                        for (UUID uuid : allParticipants) {
+                            ServerPlayer player = ServerLifecycleHooks.getCurrentServer() != null
+                                    ? ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid)
+                                    : null;
+                            if (player != null) {
+                                player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.get(), 1.0F,
+                                        1.0F);
                             }
                         }
+                    }
 
-                        // Notify remaining seconds
-                        notifyWarParticipants(war,
-                                Component
-                                        .literal("â± " + secondsLeft + (secondsLeft == 1 ? " second" : " seconds")
-                                                + " until war starts!")
-                                        .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)));
+                    // Notify remaining seconds
+                    notifyWarParticipants(war,
+                            Component
+                                    .literal("â± " + secondsLeft[0] + (secondsLeft[0] == 1 ? " second" : " seconds")
+                                            + " until war starts!")
+                                    .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)));
 
-                        secondsLeft--;
-                        if (secondsLeft < 0) {
-                            this.cancel();
-                        }
-                    } catch (Exception ex) {
-                        // Catch any exceptions to prevent timer from crashing
-                        WARSYSTEM_LOGGER.error("Error in countdown timer: " + ex.getMessage(), ex);
-                        this.cancel();
-                    }
+                    secondsLeft[0]--;
+                } catch (Exception ex) {
+                    WARSYSTEM_LOGGER.error("Error in countdown timer: " + ex.getMessage(), ex);
                 }
             }, Math.max(0, joinDurationMillis - 6000), 1000); // Start 6 seconds before join phase ends, repeat every 1
                                                               // second
         }
 
         // Main timer to start the war when join phase ends
-        new Timer().schedule(new TimerTask() {
-            @Override
-            public void run() {
-                if (war == null || war.getColony() == null) {
-                    this.cancel();
-                    return;
-                } // Null check for war
-                war.setStatus(WarData.WarStatus.INWAR);
-                war.warStartTime = System.currentTimeMillis();
-                finalizeWarStart(war);
-                // Enable war actions for both sides
-                setWarInteractionPermissions(war.getColony(), true);
-                if (war.getAttackerColony() != null) {
-                    setWarInteractionPermissions(war.getAttackerColony(), true);
-                }
-                startWarCountdown(war);
+        TickScheduler.scheduleDelayed(() -> {
+            if (war == null || war.getColony() == null) {
+                return;
+            }
+            war.setStatus(WarData.WarStatus.INWAR);
+            war.warStartTime = System.currentTimeMillis();
+            finalizeWarStart(war);
+            // Enable war actions for both sides
+            setWarInteractionPermissions(war.getColony(), true);
+            if (war.getAttackerColony() != null) {
+                setWarInteractionPermissions(war.getAttackerColony(), true);
             }
+            startWarCountdown(war);
         }, joinDurationMillis);
         war.setAccepted(true);
     }
@@ -2134,43 +2471,42 @@ public class WarSystem {
             return;
         }
         final long warDurationSeconds = TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
-        warData.timerTask = new TimerTask() {
-            @Override
-            public void run() {
-                // Check if war still exists in active wars or if the colony world/server is
-                // null
-                if (!ACTIVE_WARS.containsKey(warData.getColony().getID()) ||
-                        warData.getColony().getWorld() == null ||
-                        warData.getColony().getWorld().getServer() == null ||
-                        warData.bossEvent == null) {
-                    this.cancel();
-                    return;
-                }
+        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
+            // Check if war still exists in active wars or if the colony world/server is
+            // null
+            if (!ACTIVE_WARS.containsKey(warData.getColony().getID()) ||
+                    warData.getColony().getWorld() == null ||
+                    warData.getColony().getWorld().getServer() == null ||
+                    warData.bossEvent == null) {
+                TickScheduler.cancel(warData.countdownTaskId);
+                warData.countdownTaskId = -1;
+                return;
+            }
 
-                // Check war status - don't process for ended wars
-                if (warData.getStatus() != WarData.WarStatus.INWAR) {
-                    this.cancel();
-                    return;
-                }
+            // Check war status - don't process for ended wars
+            if (warData.getStatus() != WarData.WarStatus.INWAR) {
+                TickScheduler.cancel(warData.countdownTaskId);
+                warData.countdownTaskId = -1;
+                return;
+            }
 
-                long elapsedSeconds = (System.currentTimeMillis() - warData.warStartTime) / 1000;
-                long remaining = Math.max(0, warDurationSeconds - elapsedSeconds);
-                String bossText = String.format("War: Attacker Lives: %d | Defender Lives: %d | Time: %02d:%02d",
-                        warData.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum(),
-                        warData.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum(),
-                        remaining / 60, remaining % 60);
-                Component newName = Component.literal(bossText);
-                float newProgress = (float) remaining / warDurationSeconds;
-                warData.bossEvent.setName(newName);
-                warData.bossEvent.setProgress(newProgress);
-                warData.bossEvent.setVisible(true);
-                if (remaining <= 0) {
-                    handleTimeExpiry(warData);
-                    this.cancel();
-                }
+            long elapsedSeconds = (System.currentTimeMillis() - warData.warStartTime) / 1000;
+            long remaining = Math.max(0, warDurationSeconds - elapsedSeconds);
+            String bossText = String.format("War: Attacker Lives: %d | Defender Lives: %d | Time: %02d:%02d",
+                    warData.getAttackerLives().values().stream().mapToInt(Integer::intValue).sum(),
+                    warData.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum(),
+                    remaining / 60, remaining % 60);
+            Component newName = Component.literal(bossText);
+            float newProgress = (float) remaining / warDurationSeconds;
+            warData.bossEvent.setName(newName);
+            warData.bossEvent.setProgress(newProgress);
+            warData.bossEvent.setVisible(true);
+            if (remaining <= 0) {
+                handleTimeExpiry(warData);
+                TickScheduler.cancel(warData.countdownTaskId);
+                warData.countdownTaskId = -1;
             }
-        };
-        new Timer().scheduleAtFixedRate(warData.timerTask, 1000, 1000);
+        }, 1000, 1000);
     }
 
     public static void sendColonyMessage(IColony colony, Component message) {
@@ -2204,43 +2540,90 @@ public class WarSystem {
     /**
      * Finds a valid colony owned by the player that meets the requirements to
      * declare war on the target.
-     * Checks building requirements, guard counts, and war chest status.
+     * Checks building requirements, guard counts, and treasury status.
+     * 
+     * SPECIAL CASE: Occupied colonies can be used for RECLAMATION wars against the
+     * occupier's colony.
+     * This allows players whose only colony is occupied to still fight back!
      * 
      * @param player        The player attempting to declare war
      * @param targetColony  The target colony
-     * @param checkWarChest Whether to check war chest requirements (usually true)
+     * @param checkTreasury Whether to check treasury requirements (usually true)
      * @return The first valid IColony found, or null if none meet requirements
      */
-    public static IColony findValidAttackerColony(ServerPlayer player, IColony targetColony, boolean checkWarChest) {
+    public static IColony findValidAttackerColony(ServerPlayer player, IColony targetColony, boolean checkTreasury) {
         if (player == null || targetColony == null)
             return null;
 
+        UUID playerUUID = player.getUUID();
+        int primaryColonyId = FirstColonyTracker.getFirstColony(playerUUID);
         List<IColony> playerColonies = IColonyManager.getInstance().getColonies(player.level()).stream()
-                .filter(c -> c.getPermissions().getOwner().equals(player.getUUID()))
-                .toList();
+                .filter(c -> c.getPermissions().getOwner().equals(playerUUID))
+                .sorted((a, b) -> {
+                    if (a.getID() == primaryColonyId) return -1;
+                    if (b.getID() == primaryColonyId) return 1;
+                    return 0;
+                })
+                .collect(java.util.stream.Collectors.toList());
 
         for (IColony potentialAttacker : playerColonies) {
             // Cannot attack yourself
             if (potentialAttacker.getID() == targetColony.getID())
                 continue;
 
-            // Check building/guard requirements
-            if (TaxConfig.isWarBuildingRequirementsEnabled()) {
-                net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult reqs = net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager
-                        .checkWarRequirements(potentialAttacker);
-                if (!reqs.meetsRequirements)
-                    continue;
-            } else {
-                int guardCount = countGuards(potentialAttacker);
-                if (guardCount < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get())
-                    continue;
+            // RECLAMATION WAR EXCEPTION: Relax requirements when fighting to reclaim an
+            // occupied colony.
+            // Two scenarios are covered:
+            // A) Player targets their OWN occupied colony - any attacker colony gets relaxed reqs
+            // B) Player targets the OCCUPIER's colony using their occupied colony as attacker
+            boolean isReclamationWar = false;
+            if (TaxConfig.isOccupationSystemEnabled()) {
+                // Scenario B: This attacker colony is occupied - check if targeting the occupier
+                net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData attackerOccData = 
+                    net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(potentialAttacker.getID());
+                if (attackerOccData != null && attackerOccData.getOriginalOwnerUUID().equals(player.getUUID())) {
+                    UUID occupierUUID = attackerOccData.getOccupierUUID();
+                    if (targetColony.getPermissions().getOwner().equals(occupierUUID)) {
+                        isReclamationWar = true;
+                        WARSYSTEM_LOGGER.info(
+                                "Reclamation war (B): {} using occupied colony {} to attack occupier's colony {}",
+                                player.getName().getString(), potentialAttacker.getName(), targetColony.getName());
+                    }
+                }
+
+                // Scenario A: The TARGET colony is the player's own occupied colony
+                if (!isReclamationWar) {
+                    net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData targetOccData = 
+                        net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(targetColony.getID());
+                    if (targetOccData != null && targetOccData.getOriginalOwnerUUID().equals(player.getUUID())) {
+                        isReclamationWar = true;
+                        WARSYSTEM_LOGGER.info(
+                                "Reclamation war (A): {} using colony {} to reclaim their occupied colony {}",
+                                player.getName().getString(), potentialAttacker.getName(), targetColony.getName());
+                    }
+                }
             }
 
-            // Check War Chest
-            if (checkWarChest) {
-                if (!net.machiavelli.minecolonytax.economy.WarChestManager.canDeclareWar(potentialAttacker.getID(),
-                        targetColony.getID())) {
-                    continue;
+            // Check building/guard requirements (skip for reclamation wars - desperation
+            // allows it!)
+            if (!isReclamationWar) {
+                if (TaxConfig.isWarBuildingRequirementsEnabled()) {
+                    net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult reqs = net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager
+                            .checkWarRequirements(potentialAttacker);
+                    if (!reqs.meetsRequirements)
+                        continue;
+                } else {
+                    int guardCount = countGuards(potentialAttacker);
+                    if (guardCount < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get())
+                        continue;
+                }
+
+                // Check treasury (skip for reclamation wars - fighting for freedom!)
+                if (checkTreasury) {
+                    if (!net.machiavelli.minecolonytax.economy.TreasuryManager.canDeclareWar(potentialAttacker.getID(),
+                            targetColony.getID())) {
+                        continue;
+                    }
                 }
             }
 
@@ -2260,6 +2643,46 @@ public class WarSystem {
             return 0;
         }
 
+        // Check if this is a reclamation war (two scenarios):
+        // A) Player targets their OWN occupied colony directly
+        // B) Player targets the OCCUPIER's colony while their own colony is occupied
+        if (TaxConfig.isOccupationSystemEnabled()) {
+            // Scenario A: Target IS the player's occupied colony
+            net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData targetOccData =
+                    net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(targetColony.getID());
+            if (targetOccData != null && targetOccData.getOriginalOwnerUUID().equals(attacker.getUUID())) {
+                net.machiavelli.minecolonytax.occupation.OccupationManager
+                        .markReclamationAttempted(targetColony.getID());
+                attacker.sendSystemMessage(Component.literal(
+                        "\u2694 RECLAMATION WAR! You are fighting to reclaim your occupied colony!")
+                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
+                WARSYSTEM_LOGGER.info("Reclamation war (A) initiated by {} for occupied colony {}",
+                        attacker.getName().getString(), targetColony.getName());
+            } else {
+                // Scenario B: Player's colony is occupied and they're attacking the occupier
+                // Find if any of the attacker's colonies are occupied by the target colony's owner
+                UUID targetOwner = targetColony.getPermissions().getOwner();
+                List<IColony> attackerColonies = IColonyManager.getInstance().getColonies(source.getLevel()).stream()
+                        .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
+                        .toList();
+                for (IColony ac : attackerColonies) {
+                    net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData acOccData =
+                            net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(ac.getID());
+                    if (acOccData != null && acOccData.getOriginalOwnerUUID().equals(attacker.getUUID())
+                            && acOccData.getOccupierUUID().equals(targetOwner)) {
+                        net.machiavelli.minecolonytax.occupation.OccupationManager
+                                .markReclamationAttempted(ac.getID());
+                        attacker.sendSystemMessage(Component.literal(
+                                "\u2694 RECLAMATION WAR! You are attacking the occupier of your colony " + ac.getName() + "!")
+                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
+                        WARSYSTEM_LOGGER.info("Reclamation war (B) initiated by {} - attacking occupier's colony {}, reclaiming {}",
+                                attacker.getName().getString(), targetColony.getName(), ac.getName());
+                        break;
+                    }
+                }
+            }
+        }
+
         // Find a valid attacker colony using the new helper
         IColony attackerColony = findValidAttackerColony(attacker, targetColony, true);
 
@@ -2288,10 +2711,62 @@ public class WarSystem {
             return 0;
         }
 
+        // Block war if attacker colony is at maximum debt
+        if (TaxConfig.isDebtBlocksWar() && TaxConfig.getDebtLimit() > 0) {
+            int attackerBalance = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColony(attackerColony);
+            if (attackerBalance <= -TaxConfig.getDebtLimit()) {
+                source.sendFailure(Component.literal("Your colony (" + attackerColony.getName()
+                        + ") is bankrupt! Pay off your tax debt before declaring war.")
+                        .withStyle(ChatFormatting.RED));
+                return 0;
+            }
+        }
+
+        // Block if the chosen attacker colony is already in a war
+        if (ACTIVE_WARS.containsKey(attackerColony.getID())) {
+            source.sendFailure(Component.literal("Your colony " + attackerColony.getName()
+                    + " is already under attack â€” cannot start another war!")
+                    .withStyle(ChatFormatting.RED));
+            return 0;
+        }
+        boolean attackerAlreadyWaging = ACTIVE_WARS.values().stream()
+                .anyMatch(wd -> wd.getAttackerColony() != null
+                        && wd.getAttackerColony().getID() == attackerColony.getID());
+        if (attackerAlreadyWaging) {
+            source.sendFailure(Component.literal("Your colony " + attackerColony.getName()
+                    + " is already engaged in a war â€” end it before starting another!")
+                    .withStyle(ChatFormatting.RED));
+            return 0;
+        }
+
         ServerPlayer owner = level.getServer().getPlayerList().getPlayer(targetColony.getPermissions().getOwner());
+
+        // Check if this is a PRIMARY or SECONDARY colony for offline attack rules
+        UUID targetOwnerUUID = targetColony.getPermissions().getOwner();
+        boolean isPrimaryColony = FirstColonyTracker.isFirstColony(targetOwnerUUID, targetColony.getID());
+
         if (owner == null) {
-            source.sendFailure(Component.literal("Target colony owner is offline!"));
-            return 0;
+            // Owner is offline - check if we can still attack based on colony type
+            if (TaxConfig.isOutpostVulnerabilityEnabled() && !isPrimaryColony) {
+                // SECONDARY COLONY (Outpost) - Can be attacked while owner is offline
+                WARSYSTEM_LOGGER.info("Outpost attack initiated on {} (owner offline) by {}",
+                        targetColony.getName(), attacker.getName().getString());
+                source.sendSuccess(() -> Component.literal(
+                        "âš” OUTPOST ASSAULT! Target colony owner is offline, but this is a secondary colony (outpost). Attack proceeds!")
+                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
+                // Continue with auto-accept flow for offline outpost attacks
+                return processOfflineOutpostAttack(attacker, targetColony, attackerColony, source);
+            } else {
+                // PRIMARY COLONY - Owner must be online
+                if (isPrimaryColony) {
+                    source.sendFailure(Component.literal(
+                            "Target colony is a PRIMARY colony (capital). The owner must be online to defend!"));
+                } else {
+                    source.sendFailure(
+                            Component.literal("Target colony owner is offline! (Outpost vulnerability is disabled)"));
+                }
+                return 0;
+            }
         }
 
         if (!TaxConfig.WAR_ACCEPTANCE_REQUIRED.get()) {
@@ -2348,26 +2823,23 @@ public class WarSystem {
             }
         }
         pendingWarRequests.put(targetColony.getID(), new WarRequest(attacker.getUUID(), targetColony.getID()));
-        new Timer().schedule(new TimerTask() {
-            @Override
-            public void run() {
-                Object removedRequest = pendingWarRequests.remove(targetColony.getID());
-                if (removedRequest != null) {
-                    if (targetColony.getWorld() != null && targetColony.getWorld().getServer() != null) {
-                        ServerPlayer targetOwner = targetColony.getWorld().getServer().getPlayerList()
-                                .getPlayer(targetColony.getPermissions().getOwner());
-                        if (targetOwner != null) {
-                            targetOwner.sendSystemMessage(
-                                    Component.translatable("war.request.expired.defender")
-                                            .withStyle(style -> style.withColor(ChatFormatting.RED)));
-                        }
-                        ServerPlayer attackerPlayer = targetColony.getWorld().getServer().getPlayerList()
-                                .getPlayer(attacker.getUUID());
-                        if (attackerPlayer != null) {
-                            attackerPlayer.sendSystemMessage(
-                                    Component.translatable("war.request.expired.attacker", targetColony.getName())
-                                            .withStyle(style -> style.withColor(ChatFormatting.RED)));
-                        }
+        TickScheduler.scheduleDelayed(() -> {
+            Object removedRequest = pendingWarRequests.remove(targetColony.getID());
+            if (removedRequest != null) {
+                if (targetColony.getWorld() != null && targetColony.getWorld().getServer() != null) {
+                    ServerPlayer targetOwner = targetColony.getWorld().getServer().getPlayerList()
+                            .getPlayer(targetColony.getPermissions().getOwner());
+                    if (targetOwner != null) {
+                        targetOwner.sendSystemMessage(
+                                Component.translatable("war.request.expired.defender")
+                                        .withStyle(style -> style.withColor(ChatFormatting.RED)));
+                    }
+                    ServerPlayer attackerPlayer = targetColony.getWorld().getServer().getPlayerList()
+                            .getPlayer(attacker.getUUID());
+                    if (attackerPlayer != null) {
+                        attackerPlayer.sendSystemMessage(
+                                Component.translatable("war.request.expired.attacker", targetColony.getName())
+                                        .withStyle(style -> style.withColor(ChatFormatting.RED)));
                     }
                 }
             }
@@ -2406,6 +2878,138 @@ public class WarSystem {
         return 1;
     }
 
+    /**
+     * Handles attacks on secondary colonies (outposts) when the owner is offline.
+     * The war proceeds with auto-accept and the attacker fights against the
+     * colony's guards.
+     * The defender can still win if the attacker runs out of lives or time expires.
+     */
+    private static int processOfflineOutpostAttack(ServerPlayer attacker, IColony targetColony,
+            IColony attackerColony, CommandSourceStack source) {
+
+        if (ServerLifecycleHooks.getCurrentServer() != null) {
+            Component outpostAssaultMsg = Component.empty()
+                    .append(Component.literal("âš”ï¸ OUTPOST ASSAULT âš”ï¸").withStyle(ChatFormatting.GOLD,
+                            ChatFormatting.BOLD))
+                    .append(Component.literal("\n----------------------------------------")
+                            .withStyle(ChatFormatting.DARK_GRAY))
+                    .append(Component.literal("\n").withStyle(ChatFormatting.YELLOW))
+                    .append(Component.literal(attackerColony.getName()).withStyle(ChatFormatting.DARK_RED,
+                            ChatFormatting.BOLD))
+                    .append(Component.literal(" is assaulting the outpost ").withStyle(ChatFormatting.YELLOW))
+                    .append(Component.literal(targetColony.getName()).withStyle(ChatFormatting.BLUE,
+                            ChatFormatting.BOLD))
+                    .append(Component.literal("!").withStyle(ChatFormatting.YELLOW))
+                    .append(Component.literal("\nâš  Owner is OFFLINE - Guards will defend automatically!")
+                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
+                    .append(Component.literal("\n----------------------------------------")
+                            .withStyle(ChatFormatting.DARK_GRAY));
+            broadcastToServer(outpostAssaultMsg);
+        }
+
+        // Start the war immediately with the attacker - no join phase needed since
+        // owner is offline
+        Team attackerTeam = FTB_TEAMS_INSTALLED && FTB_TEAM_MANAGER != null
+                ? FTB_TEAM_MANAGER.getTeamForPlayerID(attacker.getUUID()).orElse(null)
+                : null;
+
+        // Initiate war without a defender player - guards will fight
+        initiateOfflineOutpostWar(attacker, targetColony, attackerColony, attackerTeam);
+
+        return 1;
+    }
+
+    /**
+     * Initiates a war against an outpost when the owner is offline.
+     * Special handling: No defender players, only guards defend.
+     */
+    private static void initiateOfflineOutpostWar(ServerPlayer attacker, IColony targetColony,
+            IColony attackerColony, Team attackerTeam) {
+
+        int attackerGuards = countGuards(attackerColony);
+        int defenderGuards = countGuards(targetColony);
+
+        WarData warData = new WarData(
+                attacker.getUUID(),
+                targetColony.getPermissions().getOwner(), // Defender owner UUID (offline)
+                attackerTeam != null ? attackerTeam.getId() : null,
+                null, // No defender team
+                System.currentTimeMillis(),
+                null, // No boss event yet - created below
+                targetColony,
+                attackerColony);
+
+        // Mark this as an offline outpost war
+        warData.setOfflineOutpostWar(true);
+
+        // IMPORTANT: Set attacker guards to 0 for offline outpost wars.
+        // The attacker's guards are at their HOME colony, not on the battlefield.
+        // Without this, the victory check (attackerLives==0 && attackerGuards==0)
+        // would never trigger - making the attacker invincible!
+        warData.remainingAttackerGuards = 0;
+
+        ACTIVE_WARS.put(targetColony.getID(), warData);
+        int _defStartBal = TaxManager.getStoredTaxForColonyId(targetColony.getID());
+        HistoryManager.logWithBalance(targetColony.getID(), "WAR",
+                "War started â€” attacked by " + attacker.getName().getString(),
+                _defStartBal, _defStartBal);
+        if (attackerColony != null) {
+            int _atkStartBal = TaxManager.getStoredTaxForColonyId(attackerColony.getID());
+            HistoryManager.logWithBalance(attackerColony.getID(), "WAR",
+                    "War started â€” attacking " + targetColony.getName(),
+                    _atkStartBal, _atkStartBal);
+        }
+
+        // Mark defender for home-field drain advantage + schedule drain
+        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(targetColony.getID());
+        scheduleTreasuryDrain(warData, targetColony, attackerColony);
+
+        // Add attacker to the war
+        warData.getAttackerLives().put(attacker.getUUID(), TaxConfig.PLAYER_LIVES_IN_WAR.get());
+
+        // Create boss bar for the attacker
+        warData.bossEvent = new ServerBossEvent(
+                Component.literal("Outpost Assault - " + targetColony.getName()),
+                BossEvent.BossBarColor.RED,
+                BossEvent.BossBarOverlay.PROGRESS);
+        warData.bossEvent.setProgress(1.0f);
+        warData.bossEvent.setVisible(true);
+        warData.bossEvent.addPlayer(attacker);
+
+        // Set war status to IN WAR immediately (no join phase for offline attacks)
+        warData.setStatus(WarData.WarStatus.INWAR);
+        warData.warStartTime = System.currentTimeMillis();
+
+        // Enable war interactions
+        setWarInteractionPermissions(targetColony, true);
+        setWarInteractionPermissions(attackerColony, true);
+
+        // Assign hostile rank to attacker in target colony
+        Rank hostileRank = targetColony.getPermissions().getRankHostile();
+        targetColony.getPermissions().addPlayer(attacker.getGameProfile(), hostileRank);
+
+        // Apply glow effects and resistance buffs
+        // TODO: Implement applyGlowEffect and applyGuardResistance for offline outpost
+        // wars
+        // applyGlowEffect(attacker, targetColony);
+        // applyGuardResistance(targetColony);
+
+        // Start the war countdown
+        startWarCountdown(warData);
+
+        // Notify the attacker
+        attacker.sendSystemMessage(
+                Component.literal("âš” ASSAULT BEGUN! Defeat the colony guards or hold the outpost until time expires!")
+                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
+        attacker.sendSystemMessage(Component
+                .literal("âš  You have " + TaxConfig.PLAYER_LIVES_IN_WAR.get()
+                        + " lives. Lose them all and the outpost's owner wins!")
+                .withStyle(ChatFormatting.RED));
+
+        WARSYSTEM_LOGGER.info("Offline outpost war started: {} vs {} (defender offline)",
+                attackerColony.getName(), targetColony.getName());
+    }
+
     public static int processWageWarRequestWithExtortion(ServerPlayer attacker, IColony targetColony,
             CommandSourceStack source, int extortionPercent) {
         Level level = source.getLevel();
@@ -2446,6 +3050,18 @@ public class WarSystem {
             source.sendFailure(Component.literal("Cannot declare war on your own colony!"));
             return 0;
         }
+
+        // Block extortion/war if attacker colony is at maximum debt
+        if (TaxConfig.isDebtBlocksWar() && TaxConfig.getDebtLimit() > 0) {
+            int attackerBalance = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColony(attackerColony);
+            if (attackerBalance <= -TaxConfig.getDebtLimit()) {
+                source.sendFailure(Component.literal("Your colony (" + attackerColony.getName()
+                        + ") is bankrupt! Pay off your tax debt before declaring war.")
+                        .withStyle(ChatFormatting.RED));
+                return 0;
+            }
+        }
+
         ServerPlayer owner = level.getServer().getPlayerList().getPlayer(targetColony.getPermissions().getOwner());
         if (owner == null) {
             source.sendFailure(Component.literal("Target colony owner is offline!"));
@@ -3091,9 +3707,9 @@ public class WarSystem {
      *         invalid
      */
     public static int countGuardTowers(IColony colony) {
-        if (colony == null || colony.getBuildingManager() == null)
+        if (colony == null)
             return 0;
-        return (int) colony.getBuildingManager().getBuildings().values().stream()
+        return (int) ColonyBuildingUtil.getBuildings(colony).stream()
                 .filter(WarSystem::isGuardTower)
                 .count();
     }
@@ -3105,10 +3721,26 @@ public class WarSystem {
      * @param building The building to check
      * @return true if the building is a guard tower, false otherwise
      */
+    // Cache: building Class -> whether it's a guard tower. There are only a
+    // handful of distinct building classes, so this stabilises after a few
+    // ticks and removes per-call string allocations from the hot path.
+    private static final java.util.Map<Class<?>, Boolean> GUARD_TOWER_CLASS_CACHE =
+            new java.util.concurrent.ConcurrentHashMap<>();
+
     public static boolean isGuardTower(IBuilding building) {
         if (building == null)
             return false;
 
+        Boolean cached = GUARD_TOWER_CLASS_CACHE.get(building.getClass());
+        if (cached != null) {
+            return cached;
+        }
+        boolean result = computeIsGuardTower(building);
+        GUARD_TOWER_CLASS_CACHE.put(building.getClass(), result);
+        return result;
+    }
+
+    private static boolean computeIsGuardTower(IBuilding building) {
         // Method 1: Check display name (current approach)
         String displayName = building.getBuildingDisplayName();
         if (displayName != null && "Guard Tower".equalsIgnoreCase(displayName)) {
@@ -3210,52 +3842,7 @@ public class WarSystem {
                 .append(Component.literal("\nâ° You have " + timeLimitMinutes + " minutes to decide!")
                         .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                 .append(Component.literal("\n\nChoose quickly:\n").withStyle(ChatFormatting.WHITE))
-                .append(createStartWarButton(targetColony))
-                .append("  ")
-                .append(createPayExtortionButton(targetColony, extortionPercent));
-
-        owner.sendSystemMessage(message);
-
-        // Start timer for auto-war start (daemon thread to avoid blocking shutdown)
-        new Timer(true).schedule(new TimerTask() {
-            @Override
-            public void run() {
-                Object pendingRequest = pendingWarRequests.remove(targetColony.getID());
-                if (pendingRequest instanceof WarRequestWithExtortion) {
-                    // Time expired, start war automatically
-                    WARSYSTEM_LOGGER.info("Extortion time limit expired for colony {}. Starting war automatically.",
-                            targetColony.getID());
-
-                    if (targetColony.getWorld() != null && targetColony.getWorld().getServer() != null) {
-                        ServerPlayer targetOwner = targetColony.getWorld().getServer().getPlayerList()
-                                .getPlayer(targetColony.getPermissions().getOwner());
-                        if (targetOwner != null) {
-                            targetOwner.sendSystemMessage(
-                                    Component.literal("â° Time expired! War begins automatically!")
-                                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
-                        }
-
-                        ServerPlayer attackerPlayer = targetColony.getWorld().getServer().getPlayerList()
-                                .getPlayer(attacker.getUUID());
-                        if (attackerPlayer != null) {
-                            attackerPlayer.sendSystemMessage(
-                                    Component
-                                            .literal("â° " + targetColony.getName()
-                                                    + " failed to respond in time. War begins!")
-                                            .withStyle(ChatFormatting.GOLD));
-                        }
-
-                        // Start the war join phase
-                        startJoinPhase(targetColony, attacker, targetOwner);
-                    }
-                }
-            }
-        }, timeLimitMs);
-
-        attacker.sendSystemMessage(Component
-                .literal("War declaration with " + extortionPercent + "% extortion demand sent to "
-                        + targetColony.getName() + ". They have " + timeLimitMinutes + " minutes to respond.")
-                .withStyle(ChatFormatting.YELLOW));
+                .append(createStartWarButton(targetColony));
     }
 
     /**
@@ -3297,4 +3884,345 @@ public class WarSystem {
                                 Component.literal("Click to pay " + extortionPercent + "% of your balance to avoid war")
                                         .withStyle(ChatFormatting.YELLOW))));
     }
+
+    // ==================== WAR PERSISTENCE ====================
+
+    private static final Gson WAR_GSON = new GsonBuilder().setPrettyPrinting().create();
+    private static final String WAR_STORAGE_FILE = "config/warntax/active_wars.json";
+
+    private static class WarSaveEntry {
+        String warID;
+        String attacker;
+        String defender;
+        String attackerTeamID;
+        String defenderTeamID;
+        int defenderColonyId;
+        int attackerColonyId;
+        long warStartTime;
+        long joinPhaseEndTime;
+        String status;
+        boolean accepted;
+        boolean stalemateTriggered;
+        Map<String, Integer> attackerLives;
+        Map<String, Integer> defenderLives;
+        List<Integer> defenderGuardIDs;
+        List<Integer> attackerGuardIDs;
+        List<String> attackerAllies;
+        List<String> defenderAllies;
+        List<String> spectators;
+        List<String> lastLifeInventoryPreservation;
+        int initialAttackerGuards;
+        int remainingAttackerGuards;
+        int initialDefenderGuards;
+        int remainingDefenderGuards;
+        int initialAttackerTotalLives;
+        int initialDefenderTotalLives;
+        String penaltyReport;
+    }
+
+    private static class WarSaveData {
+        List<WarSaveEntry> wars;
+    }
+
+    public static void saveActiveWars() {
+        try {
+            Path path = Paths.get(WAR_STORAGE_FILE);
+            Files.createDirectories(path.getParent());
+
+            WarSaveData saveData = new WarSaveData();
+            saveData.wars = new ArrayList<>();
+
+            for (Map.Entry<Integer, WarData> entry : ACTIVE_WARS.entrySet()) {
+                WarData war = entry.getValue();
+                WarSaveEntry e = new WarSaveEntry();
+                e.warID = war.getWarID().toString();
+                e.attacker = war.getAttacker().toString();
+                e.defender = war.getDefender().toString();
+                e.attackerTeamID = war.getAttackerTeamID().toString();
+                e.defenderTeamID = war.getDefenderTeamID().toString();
+                e.defenderColonyId = entry.getKey();
+                e.attackerColonyId = war.getAttackerColony() != null ? war.getAttackerColony().getID() : -1;
+                e.warStartTime = war.warStartTime;
+                e.joinPhaseEndTime = war.joinPhaseEndTime;
+                e.status = war.getStatus().name();
+                e.accepted = war.isAccepted();
+                e.stalemateTriggered = war.isStalemateTriggered();
+                e.penaltyReport = war.getPenaltyReport();
+                e.initialAttackerGuards = war.initialAttackerGuards;
+                e.remainingAttackerGuards = war.remainingAttackerGuards;
+                e.initialDefenderGuards = war.initialDefenderGuards;
+                e.remainingDefenderGuards = war.remainingDefenderGuards;
+                e.initialAttackerTotalLives = war.initialAttackerTotalLives;
+                e.initialDefenderTotalLives = war.initialDefenderTotalLives;
+
+                e.attackerLives = new HashMap<>();
+                war.getAttackerLives().forEach((uuid, lives) -> e.attackerLives.put(uuid.toString(), lives));
+                e.defenderLives = new HashMap<>();
+                war.getDefenderLives().forEach((uuid, lives) -> e.defenderLives.put(uuid.toString(), lives));
+
+                e.defenderGuardIDs = new ArrayList<>(war.getDefenderGuardIDs());
+                e.attackerGuardIDs = new ArrayList<>(war.getAttackerGuardIDs());
+                e.attackerAllies = new ArrayList<>();
+                war.getAttackerAllies().forEach(uuid -> e.attackerAllies.add(uuid.toString()));
+                e.defenderAllies = new ArrayList<>();
+                war.getDefenderAllies().forEach(uuid -> e.defenderAllies.add(uuid.toString()));
+                e.spectators = new ArrayList<>();
+                war.getSpectators().forEach(uuid -> e.spectators.add(uuid.toString()));
+                e.lastLifeInventoryPreservation = new ArrayList<>();
+                war.getLastLifeInventoryPreservation()
+                        .forEach(uuid -> e.lastLifeInventoryPreservation.add(uuid.toString()));
+
+                saveData.wars.add(e);
+            }
+
+            try (Writer writer = new FileWriter(path.toFile())) {
+                WAR_GSON.toJson(saveData, writer);
+            }
+
+            WARSYSTEM_LOGGER.info("Saved {} active wars to {}", saveData.wars.size(), WAR_STORAGE_FILE);
+        } catch (Exception ex) {
+            WARSYSTEM_LOGGER.error("Failed to save active wars", ex);
+        }
+    }
+
+    public static void loadAndResumeActiveWars() {
+        Path path = Paths.get(WAR_STORAGE_FILE);
+        if (!Files.exists(path)) {
+            WARSYSTEM_LOGGER.info("No saved wars file found, skipping war restoration");
+            return;
+        }
+
+        try (Reader reader = new FileReader(path.toFile())) {
+            WarSaveData saveData = WAR_GSON.fromJson(reader, WarSaveData.class);
+            if (saveData == null || saveData.wars == null || saveData.wars.isEmpty()) {
+                WARSYSTEM_LOGGER.info("No wars to restore from save file");
+                Files.deleteIfExists(path);
+                return;
+            }
+
+            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
+            if (server == null) {
+                WARSYSTEM_LOGGER.warn("Cannot restore wars: server not available");
+                return;
+            }
+
+            int restored = 0;
+            int skipped = 0;
+
+            for (WarSaveEntry e : saveData.wars) {
+                try {
+                    if (resumeWarFromSave(e, server)) {
+                        restored++;
+                    } else {
+                        skipped++;
+                    }
+                } catch (Exception ex) {
+                    WARSYSTEM_LOGGER.error("Failed to restore war {} for colony {}", e.warID, e.defenderColonyId, ex);
+                    skipped++;
+                }
+            }
+
+            WARSYSTEM_LOGGER.info("War restoration complete: {} restored, {} skipped", restored, skipped);
+            Files.deleteIfExists(path);
+
+        } catch (Exception ex) {
+            WARSYSTEM_LOGGER.error("Failed to load active wars from disk", ex);
+        }
+    }
+
+    private static boolean resumeWarFromSave(WarSaveEntry e, MinecraftServer server) {
+        IColony defenderColony = null;
+        IColony attackerColony = null;
+        for (Level level : server.getAllLevels()) {
+            if (defenderColony == null) {
+                defenderColony = IColonyManager.getInstance().getColonies(level).stream()
+                        .filter(c -> c.getID() == e.defenderColonyId).findFirst().orElse(null);
+            }
+            if (e.attackerColonyId > 0 && attackerColony == null) {
+                attackerColony = IColonyManager.getInstance().getColonies(level).stream()
+                        .filter(c -> c.getID() == e.attackerColonyId).findFirst().orElse(null);
+            }
+            if (defenderColony != null && (e.attackerColonyId <= 0 || attackerColony != null))
+                break;
+        }
+
+        if (defenderColony == null) {
+            WARSYSTEM_LOGGER.warn("Cannot restore war {}: defender colony {} no longer exists", e.warID,
+                    e.defenderColonyId);
+            return false;
+        }
+        if (e.attackerColonyId > 0 && attackerColony == null) {
+            WARSYSTEM_LOGGER.warn("Cannot restore war {}: attacker colony {} no longer exists", e.warID,
+                    e.attackerColonyId);
+            return false;
+        }
+
+        WarData.WarStatus status;
+        try {
+            status = WarData.WarStatus.valueOf(e.status);
+        } catch (IllegalArgumentException ex) {
+            WARSYSTEM_LOGGER.warn("Cannot restore war {}: invalid status '{}'", e.warID, e.status);
+            return false;
+        }
+
+        Map<UUID, Integer> attackerLives = new HashMap<>();
+        if (e.attackerLives != null) {
+            e.attackerLives.forEach((k, v) -> attackerLives.put(UUID.fromString(k), v));
+        }
+        Map<UUID, Integer> defenderLives = new HashMap<>();
+        if (e.defenderLives != null) {
+            e.defenderLives.forEach((k, v) -> defenderLives.put(UUID.fromString(k), v));
+        }
+        Set<Integer> defenderGuardIDSet = e.defenderGuardIDs != null ? new HashSet<>(e.defenderGuardIDs) : new HashSet<>();
+        Set<Integer> attackerGuardIDSet = e.attackerGuardIDs != null ? new HashSet<>(e.attackerGuardIDs) : new HashSet<>();
+        Set<UUID> attackerAlliesSet = parseUUIDList(e.attackerAllies);
+        Set<UUID> defenderAlliesSet = parseUUIDList(e.defenderAllies);
+        Set<UUID> spectatorsSet = parseUUIDList(e.spectators);
+        Set<UUID> lastLifeSet = parseUUIDList(e.lastLifeInventoryPreservation);
+
+        long now = System.currentTimeMillis();
+        if (status == WarData.WarStatus.INWAR) {
+            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
+            if (now >= e.warStartTime + warDurationMs) {
+                WARSYSTEM_LOGGER.info("War {} expired during server downtime, skipping restoration", e.warID);
+                return false;
+            }
+        } else if (status == WarData.WarStatus.JOINING) {
+            if (now >= e.joinPhaseEndTime) {
+                WARSYSTEM_LOGGER.info("War {} join phase expired during downtime, transitioning to INWAR", e.warID);
+                status = WarData.WarStatus.INWAR;
+                e.warStartTime = now;
+            }
+        }
+
+        ServerBossEvent bossEvent = new ServerBossEvent(
+                Component.literal("War for " + defenderColony.getName()),
+                BossEvent.BossBarColor.RED,
+                BossEvent.BossBarOverlay.PROGRESS);
+        bossEvent.setProgress(1.0f);
+        bossEvent.setVisible(true);
+
+        WarData warData = new WarData(
+                UUID.fromString(e.warID),
+                UUID.fromString(e.attacker),
+                UUID.fromString(e.defender),
+                UUID.fromString(e.attackerTeamID),
+                UUID.fromString(e.defenderTeamID),
+                e.warStartTime, e.joinPhaseEndTime,
+                bossEvent, defenderColony, attackerColony,
+                status, e.accepted,
+                e.initialAttackerGuards, e.remainingAttackerGuards,
+                e.initialDefenderGuards, e.remainingDefenderGuards,
+                e.initialAttackerTotalLives, e.initialDefenderTotalLives,
+                attackerLives, defenderLives,
+                defenderGuardIDSet, attackerGuardIDSet, attackerAlliesSet, defenderAlliesSet,
+                spectatorsSet, lastLifeSet,
+                e.penaltyReport, e.stalemateTriggered);
+
+        ACTIVE_WARS.put(e.defenderColonyId, warData);
+
+        // Restore defender tracking + drain scheduling
+        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(e.defenderColonyId);
+        scheduleTreasuryDrain(warData, defenderColony, attackerColony);
+
+        for (UUID uuid : warData.getAttackerLives().keySet()) {
+            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
+            if (p != null)
+                bossEvent.addPlayer(p);
+        }
+        for (UUID uuid : warData.getDefenderLives().keySet()) {
+            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
+            if (p != null)
+                bossEvent.addPlayer(p);
+        }
+
+        setWarInteractionPermissions(defenderColony, true);
+        if (attackerColony != null) {
+            setWarInteractionPermissions(attackerColony, true);
+        }
+
+        final IColony finalDefenderColony = defenderColony;
+
+        if (warData.getStatus() == WarData.WarStatus.INWAR) {
+            applyWarGlowToParticipants(warData);
+            applyGuardGlow(defenderColony);
+            if (attackerColony != null) {
+                applyGuardGlow(attackerColony);
+            }
+
+            GuardResistanceHandler.applyResistanceToGuardsForWar(defenderColony);
+            if (attackerColony != null) {
+                GuardResistanceHandler.applyResistanceToGuardsForWar(attackerColony);
+            }
+
+            startWarCountdown(warData);
+
+            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
+            long elapsed = now - warData.warStartTime;
+            long remaining = warDurationMs - elapsed;
+            if (remaining > 0) {
+                scheduleTimerWarnings(warData, remaining);
+            }
+
+            updateBossBar(warData);
+            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
+                    e.warID, defenderColony.getName(), remaining);
+        } else if (warData.getStatus() == WarData.WarStatus.JOINING) {
+            warData.alliesBossEvent = new ServerBossEvent(
+                    Component.literal("Joining War - " + defenderColony.getName()),
+                    BossEvent.BossBarColor.YELLOW,
+                    BossEvent.BossBarOverlay.PROGRESS);
+            warData.alliesBossEvent.setProgress(1.0f);
+            warData.alliesBossEvent.setVisible(true);
+
+            long remainingJoinMs = warData.getJoinPhaseEndTime() - now;
+            if (remainingJoinMs > 0) {
+                final int colonyId = e.defenderColonyId;
+                TickScheduler.scheduleDelayed(() -> {
+                    if (!ACTIVE_WARS.containsKey(colonyId))
+                        return;
+                    WarData w = ACTIVE_WARS.get(colonyId);
+                    if (w == null || w.getStatus() != WarData.WarStatus.JOINING)
+                        return;
+                    w.setStatus(WarData.WarStatus.INWAR);
+                    w.warStartTime = System.currentTimeMillis();
+                    finalizeWarStart(w);
+                    setWarInteractionPermissions(w.getColony(), true);
+                    if (w.getAttackerColony() != null) {
+                        setWarInteractionPermissions(w.getAttackerColony(), true);
+                    }
+                    startWarCountdown(w);
+                    long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
+                    scheduleTimerWarnings(w, warDurationMillis);
+                }, remainingJoinMs);
+            }
+
+            updateBossBar(warData);
+            WARSYSTEM_LOGGER.info("Restored JOINING war {} for colony {} ({} ms until war starts)",
+                    e.warID, defenderColony.getName(), remainingJoinMs);
+        }
+
+        Component restoreMsg = Component.literal("âš” War Restored: ")
+                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
+                .append(Component
+                        .literal("The war for " + finalDefenderColony.getName()
+                                + " has been resumed after server restart.")
+                        .withStyle(ChatFormatting.YELLOW));
+        broadcastToServer(restoreMsg);
+
+        return true;
+    }
+
+    private static Set<UUID> parseUUIDList(List<String> list) {
+        Set<UUID> result = new HashSet<>();
+        if (list != null) {
+            for (String s : list) {
+                try {
+                    result.add(UUID.fromString(s));
+                } catch (IllegalArgumentException ignored) {
+                }
+            }
+        }
+        return result;
+    }
 }
\ No newline at end of file
diff --git a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
new file mode 100644
index 0000000..959ac36
--- /dev/null
+++ b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
@@ -0,0 +1,1140 @@
+package net.machiavelli.minecolonytax.besiege;
+
+import com.google.gson.Gson;
+import com.google.gson.GsonBuilder;
+import com.google.gson.reflect.TypeToken;
+import com.minecolonies.api.IMinecoloniesAPI;
+import com.minecolonies.api.colony.ICitizenData;
+import com.minecolonies.api.colony.IColony;
+import com.minecolonies.api.colony.IColonyManager;
+import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
+import com.minecolonies.api.colony.permissions.Action;
+import com.minecolonies.api.colony.permissions.IPermissions;
+import com.minecolonies.api.colony.permissions.Rank;
+import com.minecolonies.core.entity.mobs.EntityMercenary;
+import net.machiavelli.minecolonytax.FirstColonyTracker;
+import net.machiavelli.minecolonytax.TaxConfig;
+import net.machiavelli.minecolonytax.WarSystem;
+import net.machiavelli.minecolonytax.militia.MilitiaAttackGoal;
+import net.machiavelli.minecolonytax.vassalization.VassalManager;
+import net.minecraft.ChatFormatting;
+import net.minecraft.core.BlockPos;
+import net.minecraft.network.chat.Component;
+import net.minecraft.server.MinecraftServer;
+import net.minecraft.server.level.ServerBossEvent;
+import net.minecraft.server.level.ServerLevel;
+import net.minecraft.server.level.ServerPlayer;
+import net.minecraft.world.BossEvent;
+import net.minecraft.world.effect.MobEffectInstance;
+import net.minecraft.world.effect.MobEffects;
+import net.minecraft.world.entity.Entity;
+import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
+import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
+import net.minecraft.world.entity.player.Player;
+import net.minecraft.world.item.ItemStack;
+import net.minecraft.world.item.Items;
+import net.minecraft.world.entity.EquipmentSlot;
+import net.minecraft.world.level.Level;
+import net.minecraft.world.level.levelgen.Heightmap;
+import org.apache.logging.log4j.LogManager;
+import org.apache.logging.log4j.Logger;
+
+import java.io.File;
+import java.io.FileReader;
+import java.io.FileWriter;
+import java.lang.reflect.Type;
+import java.util.*;
+import java.util.concurrent.ConcurrentHashMap;
+
+/**
+ * Manages the besiege system â€” single-player raids on active non-primary colonies
+ * that grant tax vassalage on victory, and the reclaim flow for the former owner.
+ */
+public class BesiegeManager {
+
+    private static final Logger LOGGER = LogManager.getLogger(BesiegeManager.class);
+    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
+    private static final String STORAGE_FILE = "config/warntax/besieged_colonies.json";
+
+    /**
+     * Active besiege raids, keyed by besieger UUID. Each besieger has at most one
+     * active raid at a time (solo combat rule). Multiple besiegers may concurrently
+     * raid the same colony â€” look them up by colony with {@link #getRaidsForColony(int)}.
+     *
+     * Phase 2 follow-up: defender pool is still per-raid, not shared across
+     * concurrent besiegers on the same colony. Each besieger currently spawns its
+     * own mercs and tracks its own hostile-citizen set; last-kill-credit semantics
+     * are not yet implemented.
+     */
+    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
+
+    /** Persistent occupation records (colonyId -> occupation data). */
+    private static final Map<Integer, BesiegeOccupationData> OCCUPATIONS = new ConcurrentHashMap<>();
+
+    /** Per-player cooldown map (playerUUID -> timestamp when cooldown expires). */
+    private static final Map<UUID, Long> PLAYER_COOLDOWNS = new ConcurrentHashMap<>();
+
+    private static MinecraftServer SERVER;
+
+    public static void initialize(MinecraftServer server) {
+        SERVER = server;
+        loadData(server);
+        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager initialized");
+    }
+
+    public static void shutdown() {
+        saveData();
+        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
+            cleanupRaid(raid, false);
+        }
+        ACTIVE_RAIDS.clear();
+        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager shutdown complete");
+    }
+
+    public static void tick() {
+        if (ACTIVE_RAIDS.isEmpty()) return;
+
+        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
+            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
+            BesiegeRaidData raid = entry.getValue();
+
+            try {
+                IColony colony = getColonyById(raid.colonyId);
+                if (colony == null) {
+                    LOGGER.warn("BesiegeManager.tick: colony {} not found, ending raid", raid.colonyId);
+                    cleanupRaid(raid, false);
+                    it.remove();
+                    continue;
+                }
+
+                ServerPlayer besieger = SERVER.getPlayerList().getPlayer(raid.besiegingPlayerUUID);
+
+                // --- Timer expired: defenders win ---
+                if (System.currentTimeMillis() >= raid.endTime) {
+                    if (TaxConfig.isNormalLogging())
+                        LOGGER.info("Besiege raid on colony {} timed out â€” defenders win", colony.getName());
+                    sendToPlayer(raid.besiegingPlayerUUID,
+                            Component.literal("The besiege of " + colony.getName() + " has failed â€” the defenders held out!")
+                                    .withStyle(ChatFormatting.RED));
+                    broadcastToNearbyPlayers(colony,
+                            Component.literal(colony.getName() + " successfully repelled the besiege!")
+                                    .withStyle(ChatFormatting.GREEN), 200);
+                    cleanupRaid(raid, false);
+                    applyCooldown(raid.besiegingPlayerUUID);
+                    it.remove();
+                    continue;
+                }
+
+                // --- Besieger left the area ---
+                if (besieger != null) {
+                    BlockPos center = colony.getCenter();
+                    double dist = besieger.distanceToSqr(center.getX(), center.getY(), center.getZ());
+                    int maxRadius = TaxConfig.getBesiegePlayerStayRadius();
+                    if (dist > (double) maxRadius * maxRadius) {
+                        besieger.sendSystemMessage(Component.literal(
+                                "You left the besiege area â€” the raid has been cancelled!")
+                                .withStyle(ChatFormatting.RED));
+                        cleanupRaid(raid, false);
+                        applyCooldown(raid.besiegingPlayerUUID);
+                        it.remove();
+                        continue;
+                    }
+
+                    // Track allies: anyone who recently damaged a defender
+                    // (ally tracking is done in the kill/hurt event â€” see RaidKillTracker integration)
+                }
+
+                // --- Victory: all defenders dead ---
+                if (allDefendersDead(raid, colony)) {
+                    if (TaxConfig.isNormalLogging())
+                        LOGGER.info("Besiege raid on colony {} successful â€” besieger wins", colony.getName());
+                    completeBesiege(raid, true, colony);
+                    it.remove();
+                    continue;
+                }
+
+                // --- Update boss bar ---
+                updateBossBar(raid, colony);
+
+            } catch (Exception e) {
+                LOGGER.error("Error ticking besiege raid for colony {}", entry.getKey(), e);
+            }
+        }
+    }
+
+
+    /**
+     * Start a besiege raid. Validates all preconditions.
+     * Returns true if the raid started successfully.
+     */
+    public static boolean startBesiege(IColony colony, ServerPlayer besieger) {
+        if (!TaxConfig.isBesiegeSystemEnabled()) {
+            besieger.sendSystemMessage(Component.literal("The besiege system is disabled on this server.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        UUID besiegerUUID = besieger.getUUID();
+        int colonyId = colony.getID();
+
+        // 1. Must own at least one colony themselves
+        IColony besiegerColony = getPrimaryColonyOfPlayer(besiegerUUID);
+        if (besiegerColony == null) {
+            besieger.sendSystemMessage(Component.literal("You must own a colony to besiege another.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        // 2. Cannot besiege own colony
+        if (colony.getPermissions().getOwner() != null
+                && colony.getPermissions().getOwner().equals(besiegerUUID)) {
+            besieger.sendSystemMessage(Component.literal("You cannot besiege your own colony.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        // 3. Primary colonies CAN now be besieged. Outcome routes through
+        // OccupationManager in TAX_ONLY mode (deed never moves; auto-reclaim after
+        // PrimaryColonyTaxOccupationDays). Secondaries continue to use the legacy
+        // TRANSFER_PENDING flow. See ColonyTierGuard + OccupationManager.
+
+        // 4. Solo rule: this besieger may not already have an active raid elsewhere.
+        // Multiple besiegers attacking the SAME colony concurrently is allowed.
+        if (ACTIVE_RAIDS.containsKey(besiegerUUID)) {
+            besieger.sendSystemMessage(Component.literal(
+                    "You already have an active besiege. Only one besiege at a time per player.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        // 5. Cooldown check
+        Long cooldownExpiry = PLAYER_COOLDOWNS.get(besiegerUUID);
+        if (cooldownExpiry != null && System.currentTimeMillis() < cooldownExpiry) {
+            long remaining = (cooldownExpiry - System.currentTimeMillis()) / 60000;
+            besieger.sendSystemMessage(Component.literal(
+                    "You must wait " + remaining + " more minute(s) before besieging again.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        // 6. Min colony size
+        int citizenCount = colony.getCitizenManager().getCitizens().size();
+        if (citizenCount < TaxConfig.getBesiegeMinColonySize()) {
+            besieger.sendSystemMessage(Component.literal(
+                    "Target colony is too small to besiege (needs at least "
+                            + TaxConfig.getBesiegeMinColonySize() + " citizens).")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        // 7. Cannot besiege a colony that is currently in an active war
+        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
+            besieger.sendSystemMessage(Component.literal(
+                    "This colony is already engaged in an active war. Besiege is not available during wartime.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        // 8. Cannot besiege a colony that is already a vassal of this player
+        if (VassalManager.isColonyVassal(colonyId)
+                && VassalManager.getVassalOverlordUUID(colonyId) != null
+                && VassalManager.getVassalOverlordUUID(colonyId).equals(besiegerUUID)) {
+            besieger.sendSystemMessage(Component.literal("This colony is already your vassal.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        return launchRaid(colony, besieger, false);
+    }
+
+    /**
+     * Start a reclaim raid â€” former owner/officer taking back their besieged colony.
+     */
+    public static boolean startReclaim(IColony colony, ServerPlayer reclaimingPlayer) {
+        if (!TaxConfig.isBesiegeSystemEnabled()) {
+            reclaimingPlayer.sendSystemMessage(Component.literal("The besiege system is disabled.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        int colonyId = colony.getID();
+        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
+        if (occ == null) {
+            reclaimingPlayer.sendSystemMessage(Component.literal("This colony is not under besiege occupation.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        UUID playerUUID = reclaimingPlayer.getUUID();
+        if (!occ.formerOwnerUUID.equals(playerUUID)) {
+            reclaimingPlayer.sendSystemMessage(Component.literal(
+                    "Only the former owner can reclaim this colony via besiege.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        // Solo rule: this player may not already have an active raid.
+        if (ACTIVE_RAIDS.containsKey(playerUUID)) {
+            reclaimingPlayer.sendSystemMessage(Component.literal(
+                    "You already have an active besiege/reclaim raid.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        // Cooldown check
+        Long cooldownExpiry = PLAYER_COOLDOWNS.get(playerUUID);
+        if (cooldownExpiry != null && System.currentTimeMillis() < cooldownExpiry) {
+            long remaining = (cooldownExpiry - System.currentTimeMillis()) / 60000;
+            reclaimingPlayer.sendSystemMessage(Component.literal(
+                    "You must wait " + remaining + " more minute(s) before attempting a reclaim.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        reclaimingPlayer.sendSystemMessage(Component.literal(
+                "Reclaim raid started! Kill all defenders to reclaim " + colony.getName() + ".")
+                .withStyle(ChatFormatting.GOLD));
+        return launchRaid(colony, reclaimingPlayer, true);
+    }
+
+
+    private static boolean launchRaid(IColony colony, ServerPlayer besieger, boolean isReclaim) {
+        int colonyId = colony.getID();
+        UUID besiegerUUID = besieger.getUUID();
+
+        try {
+            BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
+            ACTIVE_RAIDS.put(besiegerUUID, raid);
+
+            // Grant the besieger hostile rank + combat permissions on the colony
+            // so MineColonies allows the player to attack citizens.
+            grantBesiegeCombatPermissions(colony, besiegerUUID);
+
+            // Convert guards to hostile
+            int guardCount = makeGuardsHostile(colony, besieger, raid);
+
+            // Convert militia (non-guard eligible citizens)
+            int militiaCount = convertCitizensToMilitia(colony, besieger, raid);
+
+            // Spawn mercenaries
+            int mercCount = spawnMercenaries(colony, besieger, raid);
+
+            int totalDefenders = guardCount + militiaCount + mercCount;
+
+            // Apply FORTIFICATION investment: extra DAMAGE_RESISTANCE to all defenders
+            applyFortificationBonus(colony, raid);
+
+            // Create boss bar
+            createBossBar(raid, besieger, colony, totalDefenders);
+
+            String verb = isReclaim ? "RECLAIM RAID" : "BESIEGE";
+            besieger.sendSystemMessage(Component.literal(
+                    verb + " STARTED: " + colony.getName()
+                            + " | Defenders: " + totalDefenders
+                            + " | Time: " + TaxConfig.getBesiegeDurationMinutes() + "m")
+                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
+
+            broadcastToNearbyPlayers(colony,
+                    Component.literal("Nearby colony " + colony.getName()
+                            + " is under " + (isReclaim ? "reclaim attack" : "siege")
+                            + " by " + besieger.getName().getString() + "!")
+                            .withStyle(ChatFormatting.YELLOW), 200);
+
+            // Notify owner + officers + friends â€” the defender's call-to-arms.
+            // Friends are included per the Siege SMP defender-ally rule: defenders may
+            // mobilize allies even when the attacker must stand alone.
+            BlockPos colonyCenter = colony.getCenter();
+            net.minecraft.network.chat.Component callToArms = Component.literal("WARNING: ")
+                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
+                    .append(Component.literal(besieger.getName().getString())
+                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
+                    .append(Component.literal(" is besieging your colony ")
+                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
+                    .append(Component.literal(colony.getName())
+                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
+                    .append(Component.literal("! ")
+                            .withStyle(ChatFormatting.DARK_RED))
+                    .append(Component.literal("[Defend it]")
+                            .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
+                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
+                                            net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
+                                            "/tp " + colonyCenter.getX() + " " + colonyCenter.getY() + " " + colonyCenter.getZ()))
+                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
+                                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
+                                            Component.literal("Teleport near the colony center to join the defense")))));
+            notifyColonyDefenders(colony, callToArms);
+
+            if (TaxConfig.isNormalLogging())
+                LOGGER.info("Besiege {} started on colony {} ({}) by {} with {} total defenders ({}g {}m {}e)",
+                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
+                        besieger.getName().getString(), totalDefenders, guardCount, militiaCount, mercCount);
+
+            return true;
+
+        } catch (Exception e) {
+            LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
+            ACTIVE_RAIDS.remove(besiegerUUID);
+            return false;
+        }
+    }
+
+
+    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
+        int count = 0;
+        for (ICitizenData citizenData : colony.getCitizenManager().getCitizens()) {
+            if (citizenData.getJob() == null || !citizenData.getJob().isGuard()) continue;
+            Optional<AbstractEntityCitizen> entityOpt = citizenData.getEntity();
+            if (entityOpt.isEmpty()) continue;
+            AbstractEntityCitizen guard = entityOpt.get();
+
+            try {
+                applyDefenderAI(guard, besieger, raid);
+                applyDefenderEffects(guard, TaxConfig.getBesiegeDurationMinutes());
+                raid.hostileCitizenIds.add(citizenData.getId());
+                count++;
+            } catch (Exception e) {
+                LOGGER.warn("Failed to make guard {} hostile during besiege", citizenData.getName(), e);
+            }
+        }
+        return count;
+    }
+
+    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
+        List<ICitizenData> eligible = new ArrayList<>();
+        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
+            if (citizen.isChild()) continue;
+            if (citizen.getEntity().isEmpty()) continue;
+            if (citizen.getJob() != null && citizen.getJob().isGuard()) continue;
+            if (citizen.getJob() != null) {
+                String jobPath = citizen.getJob().getJobRegistryEntry().getKey().getPath();
+                if (jobPath.equals("deliveryman")) continue;
+            }
+            eligible.add(citizen);
+        }
+
+        Collections.shuffle(eligible);
+        int target = (int) Math.ceil(eligible.size() * TaxConfig.getBesiegeMilitiaPercent());
+        int count = 0;
+
+        for (ICitizenData citizen : eligible) {
+            if (count >= target) break;
+            Optional<AbstractEntityCitizen> entityOpt = citizen.getEntity();
+            if (entityOpt.isEmpty()) continue;
+            AbstractEntityCitizen entity = entityOpt.get();
+
+            try {
+                // Equip wooden sword
+                entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
+                applyDefenderAI(entity, besieger, raid);
+                applyDefenderEffects(entity, TaxConfig.getBesiegeDurationMinutes());
+                raid.hostileCitizenIds.add(citizen.getId());
+                count++;
+            } catch (Exception e) {
+                LOGGER.warn("Failed to convert citizen {} to militia during besiege", citizen.getName(), e);
+            }
+        }
+        return count;
+    }
+
+    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
+        entity.goalSelector.removeAllGoals(g -> true);
+        entity.targetSelector.removeAllGoals(g -> true);
+
+        // Use MilitiaAttackGoal instead of vanilla MeleeAttackGoal â€” non-guard citizens
+        // lack the ATTACK_DAMAGE attribute, which causes MeleeAttackGoal.doHurtTarget() to
+        // crash with IllegalArgumentException.
+        entity.goalSelector.addGoal(0, new MilitiaAttackGoal(entity, 1.2D));
+
+        // Retaliate against anyone who hits them (covers allies)
+        entity.targetSelector.addGoal(0, new HurtByTargetGoal(entity));
+
+        // Proactively hunt the besieger (and any allies)
+        entity.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(entity, Player.class,
+                20, true, false, (target) -> {
+                    if (!(target instanceof ServerPlayer sp)) return false;
+                    return sp.getUUID().equals(besieger.getUUID())
+                            || raid.alliedPlayers.contains(sp.getUUID());
+                }));
+    }
+
+    private static void applyDefenderEffects(AbstractEntityCitizen entity, int durationMinutes) {
+        int ticks = durationMinutes * 60 * 20;
+        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1));
+        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
+        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
+    }
+
+    /**
+     * Applies bonus DAMAGE_RESISTANCE to all besiege defenders based on the
+     * colony's FORTIFICATION investment level. Called once after all defenders
+     * are spawned in launchRaid().
+     * Each 20% damage reduction from the investment adds +1 resistance amplifier.
+     */
+    private static void applyFortificationBonus(IColony colony, BesiegeRaidData raid) {
+        if (!TaxConfig.isUpgradesEnabled()) return;
+        double dmgReduction = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
+                .getFortificationDamageReduction(colony.getID());
+        if (dmgReduction <= 0) return;
+        int extraAmplifier = (int) (dmgReduction / 0.20);
+        if (extraAmplifier <= 0) return;
+
+        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
+        // Re-apply DAMAGE_RESISTANCE to citizens with the boosted amplifier
+        for (int citizenId : raid.hostileCitizenIds) {
+            colony.getCitizenManager().getCitizens().stream()
+                    .filter(c -> c.getId() == citizenId)
+                    .findFirst()
+                    .flatMap(ICitizenData::getEntity)
+                    .ifPresent(entity -> entity.addEffect(
+                            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier)));
+        }
+        // Also boost mercenaries
+        for (Entity merc : raid.spawnedMercenaries) {
+            if (merc instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
+                living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier));
+            }
+        }
+        if (TaxConfig.isDebugLogging())
+            LOGGER.debug("FORTIFICATION bonus applied to colony {} defenders (extra resistance amplifier: {})",
+                    colony.getName(), extraAmplifier);
+    }
+
+    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
+        Level world = colony.getWorld();
+        if (!(world instanceof ServerLevel)) return 0;
+
+        int buildingCount = 0;
+        try {
+            buildingCount = net.machiavelli.minecolonytax.compat.ColonyBuildingUtil
+                    .getBuildings(colony).size();
+        } catch (Exception e) {
+            LOGGER.warn("Could not count buildings for mercenary calc in colony {}", colony.getName());
+        }
+
+        int count = (int) (buildingCount * TaxConfig.getBesiegeExtraMercenariesPerBuilding());
+        count = Math.min(count, TaxConfig.getBesiegeMaxMercenaries());
+        if (count <= 0) return 0;
+
+        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
+        int spawned = 0;
+
+        for (int i = 0; i < count; i++) {
+            try {
+                EntityMercenary merc = (EntityMercenary) com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
+                if (merc == null) continue;
+
+                BlockPos spawnPos = findSpawnPos(colony.getCenter(), world);
+                merc.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
+                merc.setTarget(besieger);
+
+                merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1));
+                merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, durationTicks, 0));
+
+                world.addFreshEntity(merc);
+                raid.spawnedMercenaries.add(merc);
+                spawned++;
+            } catch (Exception e) {
+                LOGGER.warn("Failed to spawn mercenary {} for besiege on colony {}", i, colony.getName(), e);
+            }
+        }
+        return spawned;
+    }
+
+
+    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
+        cleanupRaid(raid, true);
+        applyCooldown(raid.besiegingPlayerUUID);
+
+        if (attackerWon) {
+            if (raid.isReclaim) {
+                completeReclaim(raid, colony);
+            } else {
+                completeBesiegeVictory(raid, colony);
+            }
+        } else {
+            sendToPlayer(raid.besiegingPlayerUUID,
+                    Component.literal("The besiege of " + colony.getName() + " failed.")
+                            .withStyle(ChatFormatting.RED));
+        }
+    }
+
+    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
+        int tributePct = TaxConfig.getBesiegeTributePercent();
+        int durationHours = TaxConfig.getBesiegeTributeDurationHours();
+
+        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
+
+        if (vassalized) {
+            // Store occupation record
+            UUID ownerUUID = colony.getPermissions().getOwner();
+            BesiegeOccupationData occ = new BesiegeOccupationData(
+                    colony.getID(), colony.getName(),
+                    raid.besiegingPlayerUUID, ownerUUID,
+                    System.currentTimeMillis(), tributePct);
+            OCCUPATIONS.put(colony.getID(), occ);
+            saveData();
+
+            // Notify former owner
+            if (ownerUUID != null) {
+                String besiegerName = getPlayerName(raid.besiegingPlayerUUID);
+                sendToPlayer(ownerUUID, Component.literal(
+                        "Your colony " + colony.getName() + " has been besieged by " + besiegerName
+                                + "! Tax tribute (" + tributePct + "%) now flows to them. "
+                                + "Use /wnt besiege " + colony.getName() + " to reclaim it.")
+                        .withStyle(ChatFormatting.RED));
+            }
+
+            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
+                    "Besiege victory! " + colony.getName() + " now pays you " + tributePct
+                            + "% in tribute" + (durationHours > 0 ? " for " + durationHours + "h" : "") + ".")
+                    .withStyle(ChatFormatting.GREEN));
+
+            broadcastToNearbyPlayers(colony,
+                    Component.literal(colony.getName() + " has fallen under besiege occupation!")
+                            .withStyle(ChatFormatting.DARK_RED), 300);
+
+            if (TaxConfig.isNormalLogging())
+                LOGGER.info("Besiege victory: colony {} vassalized to {} at {}% tribute",
+                        colony.getName(), getPlayerName(raid.besiegingPlayerUUID), tributePct);
+        } else {
+            // Colony was already a vassal (edge case) â€” just notify
+            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
+                    "Besiege raid completed but vassalization could not be established (colony may already be a vassal).")
+                    .withStyle(ChatFormatting.YELLOW));
+        }
+    }
+
+    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
+        int colonyId = colony.getID();
+
+        // Remove vassalization
+        VassalManager.removeVassalRelation(colonyId);
+
+        // Remove occupation record
+        OCCUPATIONS.remove(colonyId);
+        saveData();
+
+        sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
+                "Reclaim successful! " + colony.getName() + " is free from occupation.")
+                .withStyle(ChatFormatting.GREEN));
+
+        broadcastToNearbyPlayers(colony,
+                Component.literal(colony.getName() + " has been reclaimed by its owner!")
+                        .withStyle(ChatFormatting.GOLD), 300);
+
+        if (TaxConfig.isNormalLogging())
+            LOGGER.info("Besiege reclaim: colony {} reclaimed by {}", colony.getName(),
+                    getPlayerName(raid.besiegingPlayerUUID));
+    }
+
+
+    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
+        IColony colony = getColonyById(raid.colonyId);
+        if (colony != null) {
+            // Revoke combat permissions from the besieger (and any allies)
+            revokeBesiegeCombatPermissions(colony, raid.besiegingPlayerUUID);
+            for (UUID ally : raid.alliedPlayers) {
+                revokeBesiegeCombatPermissions(colony, ally);
+            }
+
+            // Restore citizen AI
+            for (int citizenId : raid.hostileCitizenIds) {
+                try {
+                    ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
+                    if (citizen != null && citizen.getEntity().isPresent()) {
+                        AbstractEntityCitizen entity = citizen.getEntity().get();
+                        entity.goalSelector.removeAllGoals(g -> true);
+                        entity.targetSelector.removeAllGoals(g -> true);
+                        // Remove militia sword if present
+                        if (entity.getMainHandItem().getItem() == Items.WOODEN_SWORD) {
+                            entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
+                        }
+                        // Remove combat effects
+                        entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
+                        entity.removeEffect(MobEffects.MOVEMENT_SPEED);
+                        entity.removeEffect(MobEffects.DAMAGE_BOOST);
+                        // Restore job AI
+                        if (citizen.getJob() != null) {
+                            entity.getCitizenJobHandler().onJobChanged(citizen.getJob());
+                        }
+                    }
+                } catch (Exception e) {
+                    LOGGER.warn("Failed to restore citizen {} AI after besiege", citizenId, e);
+                }
+            }
+
+            // Despawn mercenaries
+            for (Entity merc : raid.spawnedMercenaries) {
+                try {
+                    if (merc.isAlive()) merc.remove(Entity.RemovalReason.DISCARDED);
+                } catch (Exception e) {
+                    LOGGER.warn("Failed to despawn mercenary after besiege", e);
+                }
+            }
+        }
+
+        // Remove boss bar
+        if (raid.bossEvent != null) {
+            try {
+                raid.bossEvent.removeAllPlayers();
+            } catch (Exception e) {
+                LOGGER.warn("Failed to remove besiege boss bar", e);
+            }
+            raid.bossEvent = null;
+        }
+
+        if (removeFromMap) {
+            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
+        }
+    }
+
+
+    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
+        try {
+            int minutes = TaxConfig.getBesiegeDurationMinutes();
+            Component text = Component.literal(
+                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
+                            colony.getName(), totalDefenders, minutes, 0))
+                    .withStyle(ChatFormatting.YELLOW);
+
+            raid.bossEvent = new ServerBossEvent(text, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
+            raid.bossEvent.setProgress(1.0f);
+            raid.bossEvent.addPlayer(besieger);
+
+            // Add nearby players
+            Level world = colony.getWorld();
+            if (world instanceof ServerLevel serverLevel) {
+                BlockPos center = colony.getCenter();
+                for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
+                    if (!player.equals(besieger) && player.level() == world) {
+                        double dist = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
+                        if (dist <= 200.0 * 200.0) {
+                            try { raid.bossEvent.addPlayer(player); } catch (Exception ignored) {}
+                        }
+                    }
+                }
+            }
+        } catch (Exception e) {
+            LOGGER.error("Failed to create boss bar for besiege on colony {}", colony.getName(), e);
+        }
+    }
+
+    private static void updateBossBar(BesiegeRaidData raid, IColony colony) {
+        if (raid.bossEvent == null) return;
+
+        try {
+            long remaining = Math.max(0, raid.endTime - System.currentTimeMillis());
+            float progress = (float) remaining / (float) (TaxConfig.getBesiegeDurationMinutes() * 60_000L);
+            progress = Math.max(0f, Math.min(1f, progress));
+
+            long seconds = remaining / 1000;
+            long mm = seconds / 60;
+            long ss = seconds % 60;
+
+            int aliveDefenders = countAliveDefenders(raid, colony);
+
+            BossEvent.BossBarColor color = progress > 0.6f ? BossEvent.BossBarColor.GREEN
+                    : progress > 0.3f ? BossEvent.BossBarColor.YELLOW
+                    : BossEvent.BossBarColor.RED;
+
+            raid.bossEvent.setColor(color);
+            raid.bossEvent.setProgress(progress);
+            raid.bossEvent.setName(Component.literal(
+                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
+                            colony.getName(), aliveDefenders, mm, ss)));
+        } catch (Exception e) {
+            LOGGER.warn("Failed to update besiege boss bar", e);
+        }
+    }
+
+
+    public static boolean isColonyBesieged(int colonyId) {
+        return OCCUPATIONS.containsKey(colonyId);
+    }
+
+    public static boolean isActiveRaidOnColony(int colonyId) {
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
+    }
+
+    /**
+     * Returns true if the player is locked out of the colony due to besiege occupation.
+     * The former owner is locked out; the besieging player is the new effective controller.
+     */
+    public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
+        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
+        if (occ == null) return false;
+        // Former owner is locked out
+        return occ.formerOwnerUUID != null && occ.formerOwnerUUID.equals(playerUUID);
+    }
+
+    public static boolean isBesiegingPlayer(UUID playerUUID, int colonyId) {
+        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
+        return occ != null && occ.besiegingPlayerUUID.equals(playerUUID);
+    }
+
+    /**
+     * Called from RaidKillTracker to register an allied player to ALL raids
+     * targeting this colony. With multi-besieger, several besiegers may target
+     * the same colony â€” registering the ally on each gives them combat rights
+     * regardless of which besieger they're supporting.
+     *
+     * Note: the new Siege SMP solo-besiege rule (step 5) blocks attacker-side
+     * allies via a damage shield. This method remains for any defender-ally
+     * tracking and for legacy callers; the besiege-allies config still gates it.
+     */
+    public static void registerAlly(int colonyId, UUID allyUUID) {
+        if (!TaxConfig.isBesiegeAlliesEnabled()) return;
+        List<BesiegeRaidData> raids = getRaidsForColony(colonyId);
+        if (raids.isEmpty()) return;
+        IColony colony = getColonyById(colonyId);
+        for (BesiegeRaidData raid : raids) {
+            if (raid.alliedPlayers.add(allyUUID) && colony != null) {
+                grantBesiegeCombatPermissions(colony, allyUUID);
+            }
+        }
+    }
+
+    /** Check whether a player is on cooldown. */
+    public static boolean isOnCooldown(UUID playerUUID) {
+        Long expiry = PLAYER_COOLDOWNS.get(playerUUID);
+        return expiry != null && System.currentTimeMillis() < expiry;
+    }
+
+    public static BesiegeOccupationData getOccupation(int colonyId) {
+        return OCCUPATIONS.get(colonyId);
+    }
+
+    /**
+     * Backward-compatible view of active raids keyed by colonyId.
+     *
+     * Since multi-besieger support landed, the internal storage is keyed by
+     * besieger UUID. This view returns at most ONE raid per colony (the first
+     * one encountered). Callers that need ALL raids for a colony must use
+     * {@link #getRaidsForColony(int)}; callers that need a specific besieger's
+     * raid should use {@link #getRaidForBesieger(UUID)}.
+     */
+    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
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
+        return Collections.unmodifiableMap(ACTIVE_RAIDS);
+    }
+
+    public static Map<Integer, BesiegeOccupationData> getAllOccupations() {
+        return Collections.unmodifiableMap(OCCUPATIONS);
+    }
+
+
+    private static boolean allDefendersDead(BesiegeRaidData raid, IColony colony) {
+        return countAliveDefenders(raid, colony) == 0;
+    }
+
+    private static int countAliveDefenders(BesiegeRaidData raid, IColony colony) {
+        int alive = 0;
+        // Citizens
+        for (int citizenId : raid.hostileCitizenIds) {
+            try {
+                ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
+                if (citizen != null && citizen.getEntity().isPresent()
+                        && citizen.getEntity().get().isAlive()) {
+                    alive++;
+                }
+            } catch (Exception ignored) {}
+        }
+        // Mercenaries
+        for (Entity merc : raid.spawnedMercenaries) {
+            if (merc.isAlive()) alive++;
+        }
+        return alive;
+    }
+
+    private static void applyCooldown(UUID playerUUID) {
+        long cooldownMs = TaxConfig.getBesiegeCooldownHours() * 3600_000L;
+        PLAYER_COOLDOWNS.put(playerUUID, System.currentTimeMillis() + cooldownMs);
+    }
+
+    private static BlockPos findSpawnPos(BlockPos center, Level world) {
+        Random rng = new Random();
+        for (int attempt = 0; attempt < 10; attempt++) {
+            int x = center.getX() + rng.nextInt(20) - 10;
+            int z = center.getZ() + rng.nextInt(20) - 10;
+            BlockPos surface = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
+            if (world.getBlockState(surface).isAir() && !world.getBlockState(surface.below()).isAir()) {
+                return surface;
+            }
+        }
+        return center;
+    }
+
+    private static void broadcastToNearbyPlayers(IColony colony, Component message, int radius) {
+        Level world = colony.getWorld();
+        if (!(world instanceof ServerLevel serverLevel)) return;
+        BlockPos center = colony.getCenter();
+        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
+            if (player.level() == world) {
+                double dist = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
+                if (dist <= (double) radius * radius) {
+                    player.sendSystemMessage(message);
+                }
+            }
+        }
+    }
+
+    private static void sendToPlayer(UUID uuid, Component message) {
+        if (SERVER == null) return;
+        ServerPlayer player = SERVER.getPlayerList().getPlayer(uuid);
+        if (player != null) {
+            player.sendSystemMessage(message);
+        }
+        // Offline messages are not queued for the besiege system (unlike VassalManager)
+        // because besiege outcomes are time-sensitive and stale messages would confuse players.
+    }
+
+    /**
+     * Sends a message to the colony owner and all online officers, regardless of distance.
+     * Used to ensure colonists responsible for defense are always informed of a besiege.
+     */
+    private static void notifyColonyOwnersAndOfficers(IColony colony, Component message) {
+        if (SERVER == null) return;
+        com.minecolonies.api.colony.permissions.IPermissions perms = colony.getPermissions();
+        // Owner
+        sendToPlayer(perms.getOwner(), message);
+        // Officers
+        try {
+            for (com.minecolonies.api.colony.permissions.ColonyPlayer cp
+                    : perms.getPlayersByRank(perms.getRankOfficer())) {
+                sendToPlayer(cp.getID(), message);
+            }
+        } catch (Exception ignored) {}
+    }
+
+    /**
+     * Defender call-to-arms: notifies owner, officers, AND friends.
+     * Per the Siege SMP rule, defender allies may answer the besiege; attacker
+     * allies are blocked by the solo-besiege damage shield (step 5).
+     */
+    private static void notifyColonyDefenders(IColony colony, Component message) {
+        if (SERVER == null) return;
+        com.minecolonies.api.colony.permissions.IPermissions perms = colony.getPermissions();
+        java.util.Set<UUID> notified = new java.util.HashSet<>();
+        UUID owner = perms.getOwner();
+        if (owner != null && notified.add(owner)) {
+            sendToPlayer(owner, message);
+        }
+        try {
+            for (com.minecolonies.api.colony.permissions.ColonyPlayer cp
+                    : perms.getPlayersByRank(perms.getRankOfficer())) {
+                if (cp != null && cp.getID() != null && notified.add(cp.getID())) {
+                    sendToPlayer(cp.getID(), message);
+                }
+            }
+        } catch (Exception ignored) {}
+        try {
+            for (com.minecolonies.api.colony.permissions.ColonyPlayer cp
+                    : perms.getPlayersByRank(perms.getRankFriend())) {
+                if (cp != null && cp.getID() != null && notified.add(cp.getID())) {
+                    sendToPlayer(cp.getID(), message);
+                }
+            }
+        } catch (Exception ignored) {}
+    }
+
+    private static String getPlayerName(UUID uuid) {
+        if (SERVER == null) return uuid.toString();
+        ServerPlayer p = SERVER.getPlayerList().getPlayer(uuid);
+        return p != null ? p.getName().getString() : uuid.toString();
+    }
+
+    private static IColony getColonyById(int colonyId) {
+        return IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
+                .filter(c -> c.getID() == colonyId)
+                .findFirst()
+                .orElse(null);
+    }
+
+    private static IColony getPrimaryColonyOfPlayer(UUID playerId) {
+        IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
+        // Prefer FCT: it tracks the true first colony regardless of permissions state
+        Integer firstColonyId = FirstColonyTracker.getFirstColony(playerId);
+        if (firstColonyId != null) {
+            IColony first = cm.getAllColonies().stream()
+                    .filter(c -> c.getID() == firstColonyId)
+                    .findFirst().orElse(null);
+            if (first != null) return first;
+        }
+        // Fallback: any colony where the player is listed as MC owner
+        for (IColony c : cm.getAllColonies()) {
+            UUID owner = c.getPermissions().getOwner();
+            if (owner != null && owner.equals(playerId)) return c;
+        }
+        return null;
+    }
+
+
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
+    private static void loadData(MinecraftServer server) {
+        File f = new File(server.getServerDirectory(), STORAGE_FILE);
+        if (!f.exists()) return;
+        try (FileReader r = new FileReader(f)) {
+            Type type = new TypeToken<List<BesiegeOccupationData>>() {}.getType();
+            List<BesiegeOccupationData> list = GSON.fromJson(r, type);
+            if (list != null) {
+                for (BesiegeOccupationData occ : list) {
+                    OCCUPATIONS.put(occ.colonyId, occ);
+                }
+            }
+            if (TaxConfig.isNormalLogging())
+                LOGGER.info("Loaded {} besiege occupation record(s)", OCCUPATIONS.size());
+        } catch (Exception e) {
+            LOGGER.error("Failed to load besiege occupation data", e);
+        }
+    }
+
+    private static void saveData() {
+        if (SERVER == null) return;
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
+            }
+        });
+    }
+
+
+    /** Transient raid state â€” not persisted. */
+    public static class BesiegeRaidData {
+        public final int colonyId;
+        public final UUID besiegingPlayerUUID;
+        public final long startTime;
+        public final long endTime;
+        public final Set<Integer> hostileCitizenIds = ConcurrentHashMap.newKeySet();
+        public final Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
+        public final Set<UUID> alliedPlayers = ConcurrentHashMap.newKeySet();
+        public final BlockPos colonyCenter;
+        public ServerBossEvent bossEvent;
+        public final boolean isReclaim;
+
+        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
+            this.colonyId = colonyId;
+            this.besiegingPlayerUUID = besiegingPlayerUUID;
+            this.colonyCenter = colonyCenter;
+            this.isReclaim = isReclaim;
+            this.startTime = System.currentTimeMillis();
+            this.endTime = startTime + (long) TaxConfig.getBesiegeDurationMinutes() * 60_000L;
+        }
+    }
+
+    /** Persisted occupation record â€” survives server restart. */
+    public static class BesiegeOccupationData {
+        public int colonyId;
+        public String colonyName;
+        public UUID besiegingPlayerUUID;
+        public UUID formerOwnerUUID;
+        public long besiegeTime;
+        public int tributePercent;
+
+        // For Gson deserialization
+        public BesiegeOccupationData() {}
+
+        public BesiegeOccupationData(int colonyId, String colonyName, UUID besiegingPlayerUUID,
+                UUID formerOwnerUUID, long besiegeTime, int tributePercent) {
+            this.colonyId = colonyId;
+            this.colonyName = colonyName;
+            this.besiegingPlayerUUID = besiegingPlayerUUID;
+            this.formerOwnerUUID = formerOwnerUUID;
+            this.besiegeTime = besiegeTime;
+            this.tributePercent = tributePercent;
+        }
+    }
+}
diff --git a/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java b/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
new file mode 100644
index 0000000..d460830
--- /dev/null
+++ b/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
@@ -0,0 +1,607 @@
+package net.machiavelli.minecolonytax.occupation;
+
+import com.google.gson.Gson;
+import com.google.gson.GsonBuilder;
+import com.minecolonies.api.colony.IColony;
+import com.minecolonies.api.colony.IColonyManager;
+import com.minecolonies.api.colony.permissions.Action;
+import net.machiavelli.minecolonytax.TaxConfig;
+import net.machiavelli.minecolonytax.TaxManager;
+import net.machiavelli.minecolonytax.WarSystem;
+import net.minecraft.ChatFormatting;
+import net.minecraft.network.chat.Component;
+import net.minecraft.server.MinecraftServer;
+import net.minecraft.server.level.ServerPlayer;
+import net.minecraft.world.level.Level;
+import net.minecraftforge.server.ServerLifecycleHooks;
+import org.apache.logging.log4j.LogManager;
+import org.apache.logging.log4j.Logger;
+
+import java.io.FileReader;
+import java.io.FileWriter;
+import java.io.Reader;
+import java.io.Writer;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.nio.file.Paths;
+import java.util.*;
+import java.util.concurrent.ConcurrentHashMap;
+
+/**
+ * Manages the Colony Occupation system.
+ *
+ * Constraints:
+ * - The occupier may collect taxes but cannot interact with occupied colony
+ *   buildings or items.
+ * - The original owner has a configurable window (OccupationDurationDays) to
+ *   wage a reclamation war.  If they do not, full ownership transfers permanently
+ *   to the occupier when the occupation expires.
+ */
+public class OccupationManager {
+
+    private static final Logger LOGGER = LogManager.getLogger(OccupationManager.class);
+    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
+    private static final String STORAGE_FILE = "config/warntax/occupations.json";
+
+    private static final Map<Integer, OccupationData> ACTIVE_OCCUPATIONS = new ConcurrentHashMap<>();
+
+    private static MinecraftServer serverInstance;
+
+    /**
+     * How an occupation resolves when its timer expires.
+     *
+     * Primary colonies always run in TAX_ONLY (deed never moves), unless
+     * EnablePrimaryColonyTransfer is on. Secondaries run in TRANSFER_PENDING.
+     */
+    public enum OccupationMode {
+        /** Expiry transfers full ownership to the occupier. Default for secondaries. */
+        TRANSFER_PENDING,
+        /** Expiry auto-reclaims â€” taxes route back to original owner, deed never moves. Primary colonies. */
+        TAX_ONLY
+    }
+
+    public static class OccupationData {
+        public final int colonyId;
+        public final String occupierUUID;
+        public final String originalOwnerUUID;
+        public final int occupierColonyId;
+        public final long startTime;
+        public final long expirationTime;
+        public final String colonyName;
+        public boolean reclamationAttempted;
+        public long lastTaxCollectionTime;
+        /** Null on save files written before the Siege SMP upgrade â€” see {@link #getMode()}. */
+        public OccupationMode mode;
+
+        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
+                              int occupierColonyId, String colonyName,
+                              long startTime, long expirationTime) {
+            this(colonyId, occupierUUID, originalOwnerUUID, occupierColonyId, colonyName,
+                    startTime, expirationTime, OccupationMode.TRANSFER_PENDING);
+        }
+
+        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
+                              int occupierColonyId, String colonyName,
+                              long startTime, long expirationTime, OccupationMode mode) {
+            this.colonyId = colonyId;
+            this.occupierUUID = occupierUUID.toString();
+            this.originalOwnerUUID = originalOwnerUUID.toString();
+            this.occupierColonyId = occupierColonyId;
+            this.colonyName = colonyName;
+            this.startTime = startTime;
+            this.expirationTime = expirationTime;
+            this.reclamationAttempted = false;
+            this.lastTaxCollectionTime = 0;
+            this.mode = mode;
+        }
+
+        /** Returns the mode, defaulting to TRANSFER_PENDING for legacy save files. */
+        public OccupationMode getMode() {
+            return mode != null ? mode : OccupationMode.TRANSFER_PENDING;
+        }
+
+        public boolean isExpired() {
+            return System.currentTimeMillis() >= expirationTime;
+        }
+
+        public long getRemainingTimeMs() {
+            return Math.max(0, expirationTime - System.currentTimeMillis());
+        }
+
+        public int getRemainingDays() {
+            long remainingMs = getRemainingTimeMs();
+            return (int) (remainingMs / (24L * 60L * 60L * 1000L));
+        }
+
+        public int getRemainingHours() {
+            long remainingMs = getRemainingTimeMs();
+            return (int) (remainingMs / (60L * 60L * 1000L));
+        }
+
+        public UUID getOccupierUUID() {
+            return UUID.fromString(occupierUUID);
+        }
+
+        public UUID getOriginalOwnerUUID() {
+            return UUID.fromString(originalOwnerUUID);
+        }
+    }
+
+    private static class OccupationSaveData {
+        public List<OccupationData> occupations = new ArrayList<>();
+    }
+
+    public static void initialize(MinecraftServer server) {
+        serverInstance = server;
+        loadData();
+        if (TaxConfig.isNormalLogging()) {
+            LOGGER.info("OccupationManager initialized with {} active occupations", ACTIVE_OCCUPATIONS.size());
+        }
+    }
+
+    public static void shutdown() {
+        saveData();
+        if (TaxConfig.isNormalLogging()) {
+            LOGGER.info("OccupationManager shutdown complete");
+        }
+    }
+
+    public static void startOccupation(IColony colony, UUID occupierUUID, IColony attackerColony) {
+        if (colony == null || occupierUUID == null) {
+            LOGGER.warn("startOccupation called with null colony or occupier");
+            return;
+        }
+
+        int colonyId = colony.getID();
+
+        // Don't double-occupy
+        if (ACTIVE_OCCUPATIONS.containsKey(colonyId)) {
+            LOGGER.warn("Colony {} is already occupied, cannot start new occupation", colony.getName());
+            return;
+        }
+
+        UUID originalOwner = colony.getPermissions().getOwner();
+        int occupierColonyId = attackerColony != null ? attackerColony.getID() : -1;
+
+        // Decide mode by colony tier. Primary colonies are tax-only by default;
+        // secondaries follow the legacy transfer-on-expiry flow.
+        // EnablePrimaryColonyTransfer lets server owners opt back into the legacy
+        // behavior for primaries too.
+        boolean isPrimary = originalOwner != null
+                && net.machiavelli.minecolonytax.FirstColonyTracker.isFirstColony(originalOwner, colonyId);
+        OccupationMode mode;
+        int durationDays;
+        if (isPrimary && !TaxConfig.isPrimaryColonyTransferEnabled()) {
+            mode = OccupationMode.TAX_ONLY;
+            durationDays = TaxConfig.getPrimaryColonyTaxOccupationDays();
+        } else {
+            mode = OccupationMode.TRANSFER_PENDING;
+            durationDays = TaxConfig.getOccupationDurationDays();
+        }
+
+        long now = System.currentTimeMillis();
+        long expirationTime = now + (durationDays * 24L * 60L * 60L * 1000L);
+
+        OccupationData data = new OccupationData(
+                colonyId, occupierUUID, originalOwner,
+                occupierColonyId, colony.getName(),
+                now, expirationTime, mode
+        );
+        ACTIVE_OCCUPATIONS.put(colonyId, data);
+        saveData();
+
+        if (TaxConfig.isNormalLogging()) {
+            LOGGER.info("Colony {} is now OCCUPIED by {} for {} days [mode={}]",
+                    colony.getName(), occupierUUID, durationDays, mode);
+        }
+
+        final boolean isTaxOnly = mode == OccupationMode.TAX_ONLY;
+        final String expiryConsequence = isTaxOnly
+                ? "If not reclaimed in time, the occupation lapses and taxes route back to the owner."
+                : "If the original owner does not reclaim within " + durationDays + " days, full ownership transfers to you!";
+        final String ownerStakes = isTaxOnly
+                ? "This is your Primary colony â€” the deed is safe. Reclaim within " + durationDays
+                        + " days or the occupation simply ends."
+                : "If you do not reclaim, ownership will permanently transfer!";
+
+        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
+        if (server != null) {
+            // Notify occupier
+            ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
+            if (occupier != null) {
+                Component occupierMsg = Component.literal(isTaxOnly ? "PRIMARY COLONY TAX-OCCUPIED" : "COLONY OCCUPIED")
+                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
+                        .append(Component.literal("\n"))
+                        .append(Component.literal("You now occupy " + colony.getName() + "!")
+                                .withStyle(ChatFormatting.YELLOW))
+                        .append(Component.literal("\n"))
+                        .append(Component.literal("You can collect taxes with /wnt collectoccupation " + colonyId)
+                                .withStyle(ChatFormatting.GREEN))
+                        .append(Component.literal("\n"))
+                        .append(Component.literal("You cannot interact with colony buildings or items.")
+                                .withStyle(ChatFormatting.RED))
+                        .append(Component.literal("\n"))
+                        .append(Component.literal(expiryConsequence)
+                                .withStyle(ChatFormatting.AQUA));
+                occupier.sendSystemMessage(occupierMsg);
+            }
+
+            // Notify original owner
+            ServerPlayer owner = server.getPlayerList().getPlayer(originalOwner);
+            if (owner != null) {
+                Component ownerMsg = Component.literal(isTaxOnly
+                                ? "YOUR PRIMARY COLONY HAS BEEN TAX-OCCUPIED"
+                                : "YOUR COLONY HAS BEEN OCCUPIED")
+                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
+                        .append(Component.literal("\n"))
+                        .append(Component.literal("Your colony " + colony.getName() + " has been occupied!")
+                                .withStyle(ChatFormatting.RED))
+                        .append(Component.literal("\n"))
+                        .append(Component.literal("The occupier will collect taxes from your colony.")
+                                .withStyle(ChatFormatting.YELLOW))
+                        .append(Component.literal("\n"))
+                        .append(Component.literal("You have " + durationDays + " days to wage a reclamation war with /wnt wagewar " + colonyId)
+                                .withStyle(ChatFormatting.GREEN))
+                        .append(Component.literal("\n"))
+                        .append(Component.literal(ownerStakes)
+                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
+                owner.sendSystemMessage(ownerMsg);
+            }
+        }
+    }
+
+    public static boolean isOccupied(int colonyId) {
+        return ACTIVE_OCCUPATIONS.containsKey(colonyId);
+    }
+
+    public static OccupationData getOccupation(int colonyId) {
+        return ACTIVE_OCCUPATIONS.get(colonyId);
+    }
+
+    public static Map<Integer, OccupationData> getActiveOccupations() {
+        return Collections.unmodifiableMap(ACTIVE_OCCUPATIONS);
+    }
+
+    public static boolean isOccupier(UUID playerUUID, int colonyId) {
+        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
+        return data != null && data.occupierUUID.equals(playerUUID.toString());
+    }
+
+    public static List<OccupationData> getOccupiedByPlayer(UUID playerUUID) {
+        List<OccupationData> result = new ArrayList<>();
+        String uuid = playerUUID.toString();
+        for (OccupationData data : ACTIVE_OCCUPATIONS.values()) {
+            if (data.occupierUUID.equals(uuid)) {
+                result.add(data);
+            }
+        }
+        return result;
+    }
+
+    public static int collectOccupationTax(int colonyId, ServerPlayer occupier) {
+        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
+        if (data == null) {
+            occupier.sendSystemMessage(Component.literal("This colony is not occupied by you.")
+                    .withStyle(ChatFormatting.RED));
+            return 0;
+        }
+
+        if (!data.occupierUUID.equals(occupier.getUUID().toString())) {
+            occupier.sendSystemMessage(Component.literal("You are not the occupier of this colony.")
+                    .withStyle(ChatFormatting.RED));
+            return 0;
+        }
+
+        IColony colony = findColonyById(colonyId);
+        if (colony == null) {
+            occupier.sendSystemMessage(Component.literal("Colony not found.")
+                    .withStyle(ChatFormatting.RED));
+            return 0;
+        }
+
+        int storedTax = TaxManager.getStoredTaxForColony(colony);
+        if (storedTax <= 0) {
+            occupier.sendSystemMessage(Component.literal("No tax available to collect from " + colony.getName() + ".")
+                    .withStyle(ChatFormatting.YELLOW));
+            return 0;
+        }
+
+        double occupationTaxRate = TaxConfig.getOccupationTaxPercentage();
+        int taxToCollect = (int) (storedTax * occupationTaxRate);
+        if (taxToCollect <= 0) {
+            occupier.sendSystemMessage(Component.literal("Tax amount too small to collect.")
+                    .withStyle(ChatFormatting.YELLOW));
+            return 0;
+        }
+
+        TaxManager.adjustTax(colony, -taxToCollect);
+
+        if (data.occupierColonyId > 0) {
+            IColony occupierColony = findColonyById(data.occupierColonyId);
+            if (occupierColony != null) {
+                TaxManager.incrementTaxRevenue(occupierColony, taxToCollect);
+            }
+        }
+
+        data.lastTaxCollectionTime = System.currentTimeMillis();
+        saveData();
+
+        occupier.sendSystemMessage(Component.literal("Collected " + taxToCollect + " occupation tax from " + colony.getName())
+                .withStyle(ChatFormatting.GOLD));
+
+        if (TaxConfig.isNormalLogging()) {
+            LOGGER.info("Occupier {} collected {} occupation tax from colony {}",
+                    occupier.getName().getString(), taxToCollect, colony.getName());
+        }
+
+        return taxToCollect;
+    }
+
+    public static int processAutomaticOccupationTax(int colonyId, int generatedTax) {
+        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
+        if (data == null || generatedTax <= 0) {
+            return 0;
+        }
+
+        double occupationTaxRate = TaxConfig.getOccupationTaxPercentage();
+        int diverted = (int) (generatedTax * occupationTaxRate);
+        if (diverted <= 0) return 0;
+
+        if (data.occupierColonyId > 0) {
+            IColony occupierColony = findColonyById(data.occupierColonyId);
+            if (occupierColony != null) {
+                TaxManager.incrementTaxRevenue(occupierColony, diverted);
+                if (TaxConfig.isDebugLogging()) {
+                    LOGGER.info("Auto-diverted {} occupation tax from colony {} to occupier colony {}",
+                            diverted, colonyId, data.occupierColonyId);
+                }
+            }
+        }
+
+        return diverted;
+    }
+
+    /** Called when the original owner declares war against the occupier. */
+    public static void markReclamationAttempted(int colonyId) {
+        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
+        if (data != null) {
+            data.reclamationAttempted = true;
+            saveData();
+            if (TaxConfig.isNormalLogging()) {
+                LOGGER.info("Reclamation attempt recorded for occupied colony {}", colonyId);
+            }
+        }
+    }
+
+    public static void endOccupation(int colonyId, String reason) {
+        OccupationData data = ACTIVE_OCCUPATIONS.remove(colonyId);
+        if (data == null) return;
+
+        saveData();
+        if (TaxConfig.isNormalLogging()) {
+            LOGGER.info("Occupation ended for colony {} ({}): {}", data.colonyName, colonyId, reason);
+        }
+
+        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
+        if (server != null) {
+            // Notify occupier
+            ServerPlayer occupier = server.getPlayerList().getPlayer(data.getOccupierUUID());
+            if (occupier != null) {
+                occupier.sendSystemMessage(Component.literal("Your occupation of " + data.colonyName + " has ended: " + reason)
+                        .withStyle(ChatFormatting.RED));
+            }
+
+            // Notify original owner
+            ServerPlayer owner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
+            if (owner != null) {
+                owner.sendSystemMessage(Component.literal("The occupation of " + data.colonyName + " has ended: " + reason)
+                        .withStyle(ChatFormatting.GREEN));
+            }
+        }
+    }
+
+    /** Periodic check â€” transfers full ownership to the occupier when the occupation expires. Called via TickScheduler. */
+    public static void checkExpiredOccupations() {
+        if (ACTIVE_OCCUPATIONS.isEmpty()) return;
+
+        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
+        if (server == null) return;
+
+        List<Integer> toTransfer = new ArrayList<>();
+
+        for (Map.Entry<Integer, OccupationData> entry : ACTIVE_OCCUPATIONS.entrySet()) {
+            OccupationData data = entry.getValue();
+            if (data.isExpired()) {
+                // Transfer in both cases:
+                // - reclamationAttempted=false: deadline passed with no attempt
+                // - reclamationAttempted=true:  owner tried to reclaim but failed (lost the war),
+                //   occupation expired while still in effect â†’ occupier wins
+                toTransfer.add(entry.getKey());
+            }
+        }
+
+        for (int colonyId : toTransfer) {
+            OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
+            if (data == null) continue;
+
+            IColony colony = findColonyById(colonyId);
+            if (colony == null) {
+                LOGGER.warn("Occupied colony {} no longer exists, removing occupation", colonyId);
+                ACTIVE_OCCUPATIONS.remove(colonyId);
+                continue;
+            }
+
+            UUID occupierUUID = data.getOccupierUUID();
+            OccupationMode mode = data.getMode();
+
+            if (mode == OccupationMode.TAX_ONLY) {
+                // Primary colony auto-reclaim â€” deed never moves, taxes simply revert
+                // to the original owner. Friendly notification on both sides.
+                if (TaxConfig.isNormalLogging()) {
+                    LOGGER.info("Tax-only occupation expired for primary colony {} - auto-reclaiming", colony.getName());
+                }
+
+                ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
+                if (originalOwner != null) {
+                    originalOwner.sendSystemMessage(
+                            Component.literal("Your Primary colony " + colony.getName()
+                                            + " has been auto-reclaimed â€” the besieger's hold has lapsed and your taxes now route to you again.")
+                                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
+                }
+                ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
+                if (occupier != null) {
+                    occupier.sendSystemMessage(
+                            Component.literal("Your tax-occupation of " + colony.getName()
+                                            + " has lapsed â€” the owner reclaims it automatically (Primary colonies cannot be permanently claimed).")
+                                    .withStyle(ChatFormatting.GOLD));
+                }
+            } else {
+                // Standard TRANSFER_PENDING flow for secondaries. Only broadcast the
+                // permanent-claim message if the transfer actually succeeded â€”
+                // ColonyTierGuard or other failures must not produce a misleading message.
+                if (TaxConfig.isNormalLogging()) {
+                    LOGGER.info("Occupation expired for colony {} - attempting full ownership transfer to {}",
+                            colony.getName(), occupierUUID);
+                }
+
+                boolean transferred = WarSystem.transferOwnership(colony, occupierUUID);
+                if (transferred) {
+                    Component broadcastMsg = Component.literal(colony.getName() + " has been permanently claimed by its occupier!")
+                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
+                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
+                        p.sendSystemMessage(broadcastMsg);
+                    }
+                    ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
+                    if (originalOwner != null) {
+                        originalOwner.sendSystemMessage(
+                                Component.literal("You failed to reclaim " + colony.getName()
+                                                + " within the deadline. Ownership has been permanently transferred!")
+                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
+                    }
+                } else {
+                    // Transfer was denied (e.g. by ColonyTierGuard) or failed. Don't
+                    // broadcast a permanent-claim message that isn't true; treat as a
+                    // tax-only-style lapse and reclaim to the original owner.
+                    LOGGER.info("Transfer for colony {} was denied or failed - lapsing occupation instead", colony.getName());
+                    ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
+                    if (originalOwner != null) {
+                        originalOwner.sendSystemMessage(
+                                Component.literal("The occupation of " + colony.getName()
+                                                + " has lapsed â€” the deed remains with you.")
+                                        .withStyle(ChatFormatting.GREEN));
+                    }
+                }
+            }
+
+            ACTIVE_OCCUPATIONS.remove(colonyId);
+        }
+
+        if (!toTransfer.isEmpty()) {
+            saveData();
+        }
+    }
+
+    /**
+     * Manually end a tax-only occupation early â€” called when the owner successfully
+     * mounts a counter-besiege. Restores everything to pre-occupation state.
+     *
+     * @return true if an occupation was ended, false if none was active
+     */
+    public static boolean reclaimByOriginalOwner(int colonyId) {
+        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
+        if (data == null) return false;
+
+        ACTIVE_OCCUPATIONS.remove(colonyId);
+        saveData();
+
+        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
+        if (server != null) {
+            ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
+            if (originalOwner != null) {
+                originalOwner.sendSystemMessage(
+                        Component.literal("You have successfully reclaimed " + data.colonyName + "!")
+                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
+            }
+            ServerPlayer occupier = server.getPlayerList().getPlayer(data.getOccupierUUID());
+            if (occupier != null) {
+                occupier.sendSystemMessage(
+                        Component.literal(data.colonyName + " has been reclaimed by its original owner â€” your hold has been broken.")
+                                .withStyle(ChatFormatting.RED));
+            }
+        }
+        if (TaxConfig.isNormalLogging()) {
+            LOGGER.info("Occupation reclaimed for colony {} ({}) by original owner", data.colonyName, colonyId);
+        }
+        return true;
+    }
+
+    /** Returns true if the occupier should be blocked from interacting with the occupied colony's buildings and items. */
+    public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
+        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
+        if (data == null) return false;
+
+        // Block the occupier from interacting with the occupied colony's items
+        return data.occupierUUID.equals(playerUUID.toString());
+    }
+
+    public static boolean isOriginalOwner(UUID playerUUID, int colonyId) {
+        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
+        if (data == null) return false;
+        return data.originalOwnerUUID.equals(playerUUID.toString());
+    }
+
+    public static void saveData() {
+        // Snapshot on the calling (server) thread; the worker only touches
+        // a fresh ArrayList so it can't ConcurrentModification on the live map.
+        final OccupationSaveData saveData = new OccupationSaveData();
+        saveData.occupations = new ArrayList<>(ACTIVE_OCCUPATIONS.values());
+
+        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("occupations", () -> {
+            try {
+                Path dir = Paths.get("config/warntax");
+                if (!Files.exists(dir)) {
+                    Files.createDirectories(dir);
+                }
+                try (Writer writer = new FileWriter(STORAGE_FILE)) {
+                    GSON.toJson(saveData, writer);
+                }
+            } catch (Exception e) {
+                LOGGER.error("Failed to save occupation data: {}", e.getMessage());
+            }
+        });
+    }
+
+    public static void loadData() {
+        try {
+            Path path = Paths.get(STORAGE_FILE);
+            if (!Files.exists(path)) return;
+
+            try (Reader reader = new FileReader(STORAGE_FILE)) {
+                OccupationSaveData saveData = GSON.fromJson(reader, OccupationSaveData.class);
+                if (saveData != null && saveData.occupations != null) {
+                    ACTIVE_OCCUPATIONS.clear();
+                    for (OccupationData data : saveData.occupations) {
+                        ACTIVE_OCCUPATIONS.put(data.colonyId, data);
+                    }
+                    if (TaxConfig.isNormalLogging()) {
+                        LOGGER.info("Loaded {} occupations from disk", ACTIVE_OCCUPATIONS.size());
+                    }
+                }
+            }
+        } catch (Exception e) {
+            LOGGER.error("Failed to load occupation data: {}", e.getMessage());
+        }
+    }
+
+    private static IColony findColonyById(int colonyId) {
+        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
+        if (server == null) return null;
+
+        for (Level level : server.getAllLevels()) {
+            for (IColony c : IColonyManager.getInstance().getColonies(level)) {
+                if (c.getID() == colonyId) return c;
+            }
+        }
+        return null;
+    }
+}
diff --git a/src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java b/src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java
new file mode 100644
index 0000000..982fe4f
--- /dev/null
+++ b/src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java
@@ -0,0 +1,73 @@
+package net.machiavelli.minecolonytax.permissions;
+
+import com.minecolonies.api.colony.IColony;
+import net.machiavelli.minecolonytax.FirstColonyTracker;
+import net.machiavelli.minecolonytax.TaxConfig;
+
+import java.util.UUID;
+
+/**
+ * Central guard for colony ownership-transfer decisions.
+ *
+ * Every code path that flips a colony's deed to a new player must route through
+ * {@link #canTransferOwnership(IColony)}. Primary colonies (a player's first
+ * colony, per {@link FirstColonyTracker}) are protected by default and only
+ * transferable when {@code EnablePrimaryColonyTransfer} is set to {@code true}
+ * in the config.
+ *
+ * Vassalage is intentionally NOT gated here â€” losing a war can still vassalize
+ * a primary colony (the loser pays tribute) without the deed moving. Only
+ * permanent ownership changes flow through this guard.
+ */
+public final class ColonyTierGuard {
+
+    private ColonyTierGuard() {}
+
+    /**
+     * Whether the colony's ownership may be transferred to a new player.
+     *
+     * @param colony the colony in question (may be null â€” returns false)
+     * @return true when transfer is permitted; false when the colony is a
+     *         primary and {@code EnablePrimaryColonyTransfer} is off
+     */
+    public static boolean canTransferOwnership(IColony colony) {
+        if (colony == null) {
+            return false;
+        }
+        UUID currentOwner = colony.getPermissions().getOwner();
+        if (currentOwner == null) {
+            return true;
+        }
+        if (FirstColonyTracker.isFirstColony(currentOwner, colony.getID())) {
+            return TaxConfig.isPrimaryColonyTransferEnabled();
+        }
+        return true;
+    }
+
+    /**
+     * Whether a besiege victory may convert into a permanent ownership claim
+     * (as opposed to ongoing tax-occupation).
+     *
+     * Same rules as {@link #canTransferOwnership(IColony)}; named separately so
+     * besiege code reads clearly.
+     */
+    public static boolean canBesiegePermanentClaim(IColony colony) {
+        return canTransferOwnership(colony);
+    }
+
+    /**
+     * Human-readable explanation for why a transfer was denied, suitable for
+     * logging or for messaging the player who attempted the action.
+     */
+    public static String getTransferDenialReason(IColony colony) {
+        if (colony == null) {
+            return "Colony reference is null.";
+        }
+        UUID owner = colony.getPermissions().getOwner();
+        if (owner != null && FirstColonyTracker.isFirstColony(owner, colony.getID())) {
+            return colony.getName() + " is a Primary colony â€” ownership transfer is blocked by config "
+                    + "(EnablePrimaryColonyTransfer = false). Vassalization or tax-occupation may still apply.";
+        }
+        return "Transfer denied (no specific reason).";
+    }
+}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
The working-tree diff only contains rework changes for four files, while the staged diff contains the larger baseline. I’m reading the final source around the missing bundle sections now, especially damage shielding, block ledger restore, Town Hall victory, and explosion attribution.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"reclaimByOriginalOwner|completeBesiege\\(|applySiegeSpoils|sourceOwnRaid|isHostile|spawnedMercenaries|militiaSupport|getIndirectSourceEntity|getDirectSourceEntity|saveWithFullMetadata|loadStatic|restoreWarDamage|triggerVictory|attackersExhausted|onWarEnded|WAR_HITS\" src/main/java/net/machiavelli/minecolonytax" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/siege/WarBlockLedger.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 373ms:
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:61:        public final Set<Entity> spawnedMercenaries;
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:72:            this.spawnedMercenaries = ConcurrentHashMap.newKeySet();
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:289:                raidData.spawnedMercenaries.add(mercenary);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:355:            int totalDefenders = raidData.hostileCitizens.size() + raidData.spawnedMercenaries.size();
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:506:            for (Entity mercenary : raidData.spawnedMercenaries) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:598:            for (Entity mercenary : raidData.spawnedMercenaries) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:613:                raidData.spawnedMercenaries.removeAll(deadMercenaries);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:619:                    raidData.hostileCitizens.size() + raidData.spawnedMercenaries.size(),
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:646:                    for (Entity mercenary : raidData.spawnedMercenaries) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:686:            for (Entity mercenary : raidData.spawnedMercenaries) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:854:            for (Entity mercenary : raidData.spawnedMercenaries) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:1117:            LOGGER.info("Spawned Mercenaries: {}", raidData.spawnedMercenaries.size());
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:1321:            // GUARDS_ATTACK removed from API - hostility now controlled by Rank.isHostile()
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:1332:            // GUARDS_ATTACK removed from API - hostility now controlled by Rank.isHostile()
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:125:                    completeBesiege(raid, false, colony);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:153:                    completeBesiege(raid, true, colony);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:498:        for (Entity merc : raid.spawnedMercenaries) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:540:                raid.spawnedMercenaries.add(merc);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:553:     * Tracked in raid.militiaSupport so they're despawned on raid end but NOT
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:578:                raid.militiaSupport.add(militia);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:592:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:597:            applySiegeSpoils(raid, colony, true);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:604:            applySiegeSpoils(raid, colony, false);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:619:    private static void applySiegeSpoils(BesiegeRaidData raid, IColony defenderColony, boolean attackerWon) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:770:            for (Entity militia : raid.militiaSupport) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:779:            for (Entity merc : raid.spawnedMercenaries) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:980:        for (Entity merc : raid.spawnedMercenaries) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:1214:        public final Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:1216:         * Militia upgrade reinforcements. Tracked separately from spawnedMercenaries
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:1220:        public final Set<Entity> militiaSupport = ConcurrentHashMap.newKeySet();
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java:237:            // NOTE: GUARDS_ATTACK removed from API - hostility now controlled by Rank.isHostile()
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java:76:     * shared colony. Uses {@link Rank#isHostile()} so custom hostile ranks are
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java:90:                if (sourceRank.isHostile()) continue;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java:91:                if (besiegerRank.isHostile()) continue;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java:108:        if (raid.spawnedMercenaries.contains(target)) return true;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeDamageShieldHandler.java:130:                if (targetRank.isHostile()) return false;
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java:233:        if (currentRank != null && currentRank.isHostile()) {
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java:312:                    || currentRank.isHostile()
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:79:        // isColonyManager checks for officers, and !rank.isHostile() includes any
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:81:        return rank.isColonyManager() || !rank.isHostile();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:503:                if (current != null && current.isHostile()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1259:                    net.machiavelli.minecolonytax.siege.WarBlockLedger.restoreWarDamage(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1268:                net.machiavelli.minecolonytax.siege.TownHallDemolitionObjective.onWarEnded(warData.getWarID());
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java:209:            if (colony.getPermissions().getRank(responder).isHostile()) return false;
src/main/java/net/machiavelli/minecolonytax\peace\PeaceProposalManager.java:215:            if (colony.getPermissions().getRank(responder).isHostile()) return false;
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:446:                if (current != null && current.isHostile()) {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1190:                if (current != null && current.isHostile()) {
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:122:                // Use saveWithFullMetadata so the BlockEntity round-trips cleanly via
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:123:                // loadStatic on restore (this is the world-save-compatible API for
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:128:                    try { nbt = be.saveWithFullMetadata(); } catch (Exception ignored) {}
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:147:    public static void restoreWarDamage(UUID warId, Level level) {
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:172:                        // Use loadStatic for the world-save-compatible reconstruction path;
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:173:                        // it pairs with saveWithFullMetadata used at capture time.
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:174:                        BlockEntity rebuilt = BlockEntity.loadStatic(info.pos, info.state, info.blockEntityNBT);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:518:    public static boolean reclaimByOriginalOwner(int colonyId, UUID reclaimerUUID) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:522:            LOGGER.info("reclaimByOriginalOwner refused: occupation on colony {} is mode {} (not TAX_ONLY)",
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:527:            LOGGER.info("reclaimByOriginalOwner refused: caller {} is not the recorded original owner {} of colony {}",
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java:169:            if (rank == null || !rank.isHostile()) continue;
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2951:                for (net.minecraft.world.entity.Entity mercenary : raidData.spawnedMercenaries) {
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:53:    private static final Map<UUID, Map<UUID, AttackerHitState>> WAR_HITS = new ConcurrentHashMap<>();
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:74:        Entity indirect = event.getExplosion().getIndirectSourceEntity();
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:78:            Entity direct = event.getExplosion().getDirectSourceEntity();
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:126:        Map<UUID, AttackerHitState> warMap = WAR_HITS.computeIfAbsent(war.getWarID(), k -> new ConcurrentHashMap<>());
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:156:            triggerVictory(war, attacker);
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:192:    private static void triggerVictory(WarData war, ServerPlayer winner) {
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:196:        boolean attackersExhausted =
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:200:        if (attackersExhausted) {
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:203:            WAR_HITS.remove(war.getWarID());
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:234:        WAR_HITS.remove(war.getWarID());
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:241:    public static void onWarEnded(UUID warId) {
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:242:        WAR_HITS.remove(warId);
src/main/java/net/machiavelli/minecolonytax\siege\TownHallDemolitionObjective.java:254:        WAR_HITS.clear();
src/main/java/net/machiavelli/minecolonytax\event\RaidKillTracker.java:450:            if (raidData.spawnedMercenaries.contains(mercenary)) {
src/main/java/net/machiavelli/minecolonytax\event\RaidKillTracker.java:451:                raidData.spawnedMercenaries.remove(mercenary);
src/main/java/net/machiavelli/minecolonytax\event\RaidKillTracker.java:488:        for (Entity mercenary : raidData.spawnedMercenaries) {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1894:                                                "Note: GUARDS_ATTACK was removed from Minecolonies API - hostility is now controlled by Rank.isHostile()")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1906:                                                "Note: GUARDS_ATTACK was removed from Minecolonies API - hostility is now controlled by Rank.isHostile()")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1917:                                                "Note: GUARDS_ATTACK was removed from Minecolonies API - hostility is now controlled by Rank.isHostile()")
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 399ms:
package net.machiavelli.minecolonytax.besiege;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces the Siege SMP "solo besiege" rule on the attacker side only:
 *
 * If the damage source is a player belonging to an active besieger's colony
 * (but is NOT the besieger themselves), cancel the damage. The besieger fights
 * alone ƒ?" their friends and officers can be present but cannot deal damage to
 * defenders.
 *
 * The defender side is asymmetric: defenders may rally allies freely (the
 * call-to-arms message from step 4 invites them). This shield does NOT block
 * defender damage to the attacker.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BesiegeDamageShieldHandler {

    // Throttle the chat message so spam-clicking attacks doesn't flood the chat
    private static final Map<UUID, Long> LAST_BLOCK_MESSAGE = new HashMap<>();
    private static final long BLOCK_MESSAGE_COOLDOWN_MS = 3000;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!TaxConfig.isBesiegeSystemEnabled()) return;
        if (BesiegeManager.getAllActiveRaidsByBesieger().isEmpty()) return;

        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof ServerPlayer source)) return;

        LivingEntity target = event.getEntity();
        UUID sourceUUID = source.getUUID();

        // For EACH active besiege, ask "is this source helping someone else's besiege?"
        // We do NOT short-circuit on the source being a besieger themselves ƒ?" that was
        // the bug. An active besieger can still be a colony-mate of ANOTHER besieger,
        // and damage they deal in the other besiege's target colony is also blocked.
        for (BesiegeManager.BesiegeRaidData raid : BesiegeManager.getAllActiveRaidsByBesieger().values()) {
            if (raid.besiegingPlayerUUID == null) continue;
            // Skip the source's own raid ƒ?" they're allowed to deal damage in their own besiege.
            if (raid.besiegingPlayerUUID.equals(sourceUUID)) continue;

            if (!areColonyMates(source, raid.besiegingPlayerUUID)) continue;

            // Confirm the target belongs to the besieged side: defender citizen,
            // defender-side player, or a mercenary spawned for this raid.
            if (!isDefenderSideTarget(target, raid)) continue;

            event.setCanceled(true);
            event.setAmount(0f);
            sendBlockedMessage(source);
            return;
        }
    }

    /**
     * True when both players hold a non-neutral, non-hostile rank in any single
     * shared colony. Uses {@link Rank#isHostile()} so custom hostile ranks are
     * caught (not just the default Hostile rank instance).
     */
    private static boolean areColonyMates(ServerPlayer source, UUID besiegerUUID) {
        if (source.level().getServer() == null) return false;
        try {
            for (IColony colony : com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies()) {
                Rank sourceRank = colony.getPermissions().getRank(source.getUUID());
                Rank besiegerRank = colony.getPermissions().getRank(besiegerUUID);
                if (sourceRank == null || besiegerRank == null) continue;
                // Skip if either party is neutral (not really a colony member).
                if (sourceRank.equals(colony.getPermissions().getRankNeutral())) continue;
                if (besiegerRank.equals(colony.getPermissions().getRankNeutral())) continue;
                // Skip if either party is hostile ƒ?" including custom hostile ranks.
                if (sourceRank.isHostile()) continue;
                if (besiegerRank.isHostile()) continue;
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * True if {@code target} is a defender-side combatant for the given raid:
     *  - Citizen of the besieged colony
     *  - Mercenary spawned for this raid (NOT militia-upgrade reinforcements ƒ?"
     *    those don't count as objectives, so the shield ignores them too to
     *    keep behavior consistent across the rules)
     *  - Player with a non-hostile rank on the besieged colony
     */
    private static boolean isDefenderSideTarget(LivingEntity target, BesiegeManager.BesiegeRaidData raid) {
        // Mercenary spawned for this raid?
        if (raid.spawnedMercenaries.contains(target)) return true;

        // Citizen of the besieged colony?
        if (target instanceof AbstractEntityCitizen citizen) {
            try {
                var data = citizen.getCitizenData();
                if (data != null && data.getColony() != null
                        && data.getColony().getID() == raid.colonyId) {
                    return true;
                }
            } catch (Exception ignored) {}
            return false;
        }
        // Player on defender side?
        if (target instanceof ServerPlayer player) {
            try {
                IColony besieged = com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies().stream()
                        .filter(c -> c.getID() == raid.colonyId)
                        .findFirst().orElse(null);
                if (besieged == null) return false;
                Rank targetRank = besieged.getPermissions().getRank(player.getUUID());
                if (targetRank == null) return false;
                if (targetRank.isHostile()) return false;
                if (targetRank.equals(besieged.getPermissions().getRankNeutral())) return false;
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static void sendBlockedMessage(ServerPlayer source) {
        long now = System.currentTimeMillis();
        Long last = LAST_BLOCK_MESSAGE.get(source.getUUID());
        if (last != null && now - last < BLOCK_MESSAGE_COOLDOWN_MS) return;
        LAST_BLOCK_MESSAGE.put(source.getUUID(), now);
        source.sendSystemMessage(Component.literal(
                "You cannot interfere in a solo besiege ƒ?" your colony-mate must fight alone.")
                .withStyle(ChatFormatting.RED));
    }
}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 398ms:
package net.machiavelli.minecolonytax.siege;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.util.TickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures and restores blocks damaged by explosions during active wars.
 *
 * Architecture mirrors the harmonised/explosiont mod's BlockInfo pattern:
 * snapshot state + BlockEntity NBT at explosion time, schedule restoration
 * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
 * claim area + a configurable padding radius so wider explosions aren't
 * undone.
 *
 * In-memory only for now ƒ?" JSON persistence is a Phase 2 follow-up. On
 * server crash mid-war, blocks stay broken (lost ledger) but the affected
 * colony continues to function.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WarBlockLedger {

    private static final Logger LOGGER = LogManager.getLogger(WarBlockLedger.class);

    /** Padding around the colony center within which explosions are ledgered. */
    private static final int LEDGER_RADIUS = 256;
    private static final int LEDGER_RADIUS_SQ = LEDGER_RADIUS * LEDGER_RADIUS;

    /** Snapshot of one damaged block, with enough state to fully restore it. */
    public static final class BlockInfo {
        public final BlockPos pos;
        public final BlockState state;
        public final CompoundTag blockEntityNBT;
        public final ResourceLocation dimResLoc;

        BlockInfo(BlockPos pos, BlockState state, CompoundTag blockEntityNBT, ResourceLocation dimResLoc) {
            this.pos = pos;
            this.state = state;
            this.blockEntityNBT = blockEntityNBT;
            this.dimResLoc = dimResLoc;
        }
    }

    /**
     * warId ƒÅ' (pos ƒÅ' first snapshot of that pos for this war).
     *
     * Map-by-pos dedupes repeated explosions on the same block, which both
     * caps memory and guarantees we restore the PRE-WAR state (not whatever
     * intermediate state a later TNT shot captured). First snapshot wins.
     */
    private static final Map<UUID, Map<BlockPos, BlockInfo>> LEDGERS = new ConcurrentHashMap<>();

    /** Hard per-war cap so a runaway explosion loop can't OOM the server. */
    private static final int MAX_ENTRIES_PER_WAR = 50_000;

    private WarBlockLedger() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (WarSystem.ACTIVE_WARS.isEmpty()) return;

        Level level = event.getLevel();
        if (level.isClientSide()) return;

        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;

        // Find the war (if any) whose defender colony bracket contains this explosion.
        // Cheap pre-filter on the FIRST affected block.
        BlockPos representative = affected.get(0);
        WarData war = findOwningWar(representative, level);
        if (war == null) return;

        ResourceLocation dimResLoc = level.dimension().location();
        Map<BlockPos, BlockInfo> ledger = LEDGERS.computeIfAbsent(war.getWarID(), k -> new ConcurrentHashMap<>());

        // Snapshot only ƒ?" we do NOT clear blocks. Vanilla/Forge explosion handling
        // clears the affected blocks; we simply rewrite them later from the snapshot.
        // First snapshot per pos wins so repeat explosions on the same spot don't
        // overwrite the pre-war state.
        boolean capWarningEmitted = false;
        for (BlockPos pos : affected) {
            try {
                BlockState state = level.getBlockState(pos);
                Block block = state.getBlock();
                if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) continue;
                if (block == Blocks.BEDROCK) continue; // never touch bedrock

                BlockPos immutable = pos.immutable();
                if (ledger.containsKey(immutable)) continue;

                if (ledger.size() >= MAX_ENTRIES_PER_WAR) {
                    if (!capWarningEmitted) {
                        LOGGER.warn("WarBlockLedger cap of {} entries hit for war {} ƒ?" further snapshots ignored",
                                MAX_ENTRIES_PER_WAR, war.getWarID());
                        capWarningEmitted = true;
                    }
                    break;
                }

                // Use saveWithFullMetadata so the BlockEntity round-trips cleanly via
                // loadStatic on restore (this is the world-save-compatible API for
                // MC 1.20.1; serializeNBT/deserializeNBT can drop position metadata).
                CompoundTag nbt = null;
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    try { nbt = be.saveWithFullMetadata(); } catch (Exception ignored) {}
                }
                ledger.put(immutable, new BlockInfo(immutable, state, nbt, dimResLoc));
            } catch (Exception e) {
                LOGGER.warn("WarBlockLedger snapshot failed at {}: {}", pos, e.getMessage());
            }
        }

        if (TaxConfig.isDebugLogging()) {
            LOGGER.debug("Ledgered explosion for war {} (total entries now {})",
                    war.getWarID(), ledger.size());
        }
    }

    /**
     * Restore all ledgered blocks for the given war back to their pre-explosion
     * state. Spreads work across ticks to avoid chunk-flicker and lag spikes.
     * Called from WarSystem.endWar() ƒ?" safe to call when no ledger exists.
     */
    public static void restoreWarDamage(UUID warId, Level level) {
        Map<BlockPos, BlockInfo> ledgerMap = LEDGERS.remove(warId);
        if (ledgerMap == null || ledgerMap.isEmpty()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Snapshot to an array once so the restore loop is index-based and
        // doesn't churn on the underlying map.
        final BlockInfo[] entries = ledgerMap.values().toArray(new BlockInfo[0]);
        final int batchSize = 50;
        final int totalBatches = (int) Math.ceil(entries.length / (double) batchSize);
        final int[] cursor = { 0 };
        final long[] taskRef = { -1L };

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("WarBlockLedger restoring {} blocks for war {} across ~{} ticks",
                    entries.length, warId, totalBatches);
        }

        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
            int end = Math.min(cursor[0] + batchSize, entries.length);
            for (int i = cursor[0]; i < end; i++) {
                BlockInfo info = entries[i];
                try {
                    serverLevel.setBlock(info.pos, info.state, Block.UPDATE_ALL);
                    if (info.blockEntityNBT != null) {
                        // Use loadStatic for the world-save-compatible reconstruction path;
                        // it pairs with saveWithFullMetadata used at capture time.
                        BlockEntity rebuilt = BlockEntity.loadStatic(info.pos, info.state, info.blockEntityNBT);
                        if (rebuilt != null) {
                            serverLevel.setBlockEntity(rebuilt);
                            rebuilt.setChanged();
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to restore block at {}: {}", info.pos, e.getMessage());
                }
            }
            cursor[0] = end;
            if (cursor[0] >= entries.length) {
                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
            }
        }, 50, 50); // 1 tick = 50ms
    }

    /** Number of pending ledger entries for a war (useful for diagnostics). */
    public static int getLedgerSize(UUID warId) {
        Map<BlockPos, BlockInfo> ledger = LEDGERS.get(warId);
        return ledger == null ? 0 : ledger.size();
    }

    /** Drop all ledger state ƒ?" for server shutdown / reset. */
    public static void clearAll() {
        LEDGERS.clear();
    }

    /**
     * Find the war (if any) whose defender colony center is within
     * {@link #LEDGER_RADIUS} of the explosion. Cheap iteration over the small
     * ACTIVE_WARS map.
     */
    private static WarData findOwningWar(BlockPos pos, Level level) {
        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            IColony defender = war.getColony();
            if (defender == null || defender.getWorld() != level) continue;
            BlockPos center = defender.getCenter();
            long dx = center.getX() - pos.getX();
            long dz = center.getZ() - pos.getZ();
            if ((dx * dx + dz * dz) <= LEDGER_RADIUS_SQ) {
                return war;
            }
        }
        return null;
    }
}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 411ms:
package net.machiavelli.minecolonytax.siege;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Experimental victory objective: attacker wins by landing N explosive hits on
 * the defender's Town Hall building (not just the central block ƒ?" anywhere in
 * the building's footprint, per IBuilding.isInBuilding).
 *
 * Each war tracks per-attacker hit counts and last-hit timestamps so a 5-minute
 * cooldown gates each counted hit. Attacker must be within MaxSiegeRadius of
 * the Town Hall center. On counted hit: GLOWING applied + coordinates broadcast
 * to all war participants. Reaching the threshold ends the war as attacker
 * victory.
 *
 * Behind EnableExperimentalSiegeObjectives. Runs IN PARALLEL with the legacy
 * lives+guards victory system ƒ?" first trigger wins.
 *
 * NOTE: Plant-the-Banner objective is not implemented in this step; that path
 * requires registering a custom item/block.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TownHallDemolitionObjective {

    private static final Logger LOGGER = LogManager.getLogger(TownHallDemolitionObjective.class);

    /** Per-war hit progress, keyed by warId then attacker UUID. */
    private static final Map<UUID, Map<UUID, AttackerHitState>> WAR_HITS = new ConcurrentHashMap<>();

    private static final class AttackerHitState {
        int hits = 0;
        long lastHitMs = 0L;
    }

    private TownHallDemolitionObjective() {}

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!TaxConfig.isExperimentalSiegeObjectivesEnabled()) return;
        if (WarSystem.ACTIVE_WARS.isEmpty()) return;

        Level level = event.getLevel();
        if (level.isClientSide()) return;

        // Resolve to the player owner of the explosion. For player-lit TNT/projectiles
        // the DIRECT source is the PrimedTnt/arrow entity and the INDIRECT source is
        // the player who lit/fired it. Prefer indirect-ServerPlayer first, then direct.
        ServerPlayer attacker = null;
        Entity indirect = event.getExplosion().getIndirectSourceEntity();
        if (indirect instanceof ServerPlayer sp) {
            attacker = sp;
        } else {
            Entity direct = event.getExplosion().getDirectSourceEntity();
            if (direct instanceof ServerPlayer sp2) attacker = sp2;
        }
        if (attacker == null) {
            // Unattributable explosion (creeper, dispenser TNT, etc.) ƒ?" don't count.
            return;
        }

        // Find the war where this player is an attacker.
        WarData war = findWarForAttacker(attacker.getUUID());
        if (war == null) return;
        // Hard-reject: if the player is somehow also a defender, refuse to count
        // the hit. Prevents accidental self-sabotage AND deliberate self-victory.
        if (war.getDefenderLives().containsKey(attacker.getUUID())) return;

        IColony defenderColony = war.getColony();
        if (defenderColony == null) return;
        // Per CLAUDE.md, never call getBuildingManager() directly ƒ?" route through ColonyBuildingUtil.
        ITownHall townHall = null;
        try {
            for (IBuilding b : net.machiavelli.minecolonytax.compat.ColonyBuildingUtil.getBuildings(defenderColony)) {
                if (b instanceof ITownHall th) { townHall = th; break; }
            }
        } catch (Exception ignored) {}
        if (townHall == null) return;

        // Did any block in the affected list actually fall inside the Town Hall building?
        boolean hitTownHall = false;
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (((IBuilding) townHall).isInBuilding(pos)) {
                hitTownHall = true;
                break;
            }
        }
        if (!hitTownHall) return;

        // Attacker must be within MaxSiegeRadius of the Town Hall center.
        BlockPos thCenter = townHall.getPosition();
        int maxRadius = TaxConfig.getMaxSiegeRadius();
        double distSq = attacker.distanceToSqr(thCenter.getX() + 0.5, thCenter.getY() + 0.5, thCenter.getZ() + 0.5);
        if (distSq > (double) maxRadius * maxRadius) {
            attacker.sendSystemMessage(Component.literal(
                    "Town Hall hit registered but you are outside the siege radius (" + maxRadius + " blocks).")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        // Per-attacker cooldown gate.
        Map<UUID, AttackerHitState> warMap = WAR_HITS.computeIfAbsent(war.getWarID(), k -> new ConcurrentHashMap<>());
        AttackerHitState state = warMap.computeIfAbsent(attacker.getUUID(), k -> new AttackerHitState());
        long now = System.currentTimeMillis();
        long cooldownMs = TaxConfig.getTownHallHitCooldownMinutes() * 60_000L;
        if (now - state.lastHitMs < cooldownMs) {
            long remainingSec = (cooldownMs - (now - state.lastHitMs)) / 1000L;
            attacker.sendSystemMessage(Component.literal(
                    "Town Hall hit registered but on cooldown ƒ?" " + remainingSec + "s remaining before it counts.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        state.hits++;
        state.lastHitMs = now;

        // Apply glow + broadcast coords.
        int glowSec = TaxConfig.getAttackerGlowSeconds();
        if (glowSec > 0) {
            attacker.addEffect(new MobEffectInstance(MobEffects.GLOWING, glowSec * 20, 0, false, true));
        }
        int required = TaxConfig.getTownHallExplosiveHitsRequired();
        broadcastHit(war, attacker, state.hits, required);

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Experimental: attacker {} hit Town Hall of colony {} ({}/{})",
                    attacker.getName().getString(), defenderColony.getName(), state.hits, required);
        }

        // Victory check.
        if (state.hits >= required) {
            triggerVictory(war, attacker);
        }
    }

    /** Broadcast counted-hit info to all war participants. */
    private static void broadcastHit(WarData war, ServerPlayer attacker, int hits, int required) {
        if (attacker.getServer() == null) return;
        BlockPos pos = attacker.blockPosition();
        Component msg = Component.literal("Town Hall struck! ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(attacker.getName().getString()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" at (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ") ƒ?" hits: ")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(hits + "/" + required)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        for (UUID uuid : war.getAttackerLives().keySet()) {
            ServerPlayer p = attacker.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(msg);
        }
        for (UUID uuid : war.getDefenderLives().keySet()) {
            ServerPlayer p = attacker.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(msg);
        }
    }

    /**
     * Trigger attacker victory via the legacy WarSystem.endWar flow. We force the
     * defender lives to zero so the existing checkForVictory + endWar pipeline
     * concludes naturally with the attacker as winner.
     *
     * Race guard (bug #9 fix): if attackers are already at 0 lives/guards,
     * checkForVictory would resolve as DEFENDER victory the moment we zero
     * the defender lives. We detect that case before mutating and refuse to
     * trigger ƒ?" the legacy resolution will play out on its own; demolishing
     * the Town Hall in the dying breath shouldn't flip the result.
     */
    private static void triggerVictory(WarData war, ServerPlayer winner) {
        IColony defenderColony = war.getColony();
        if (defenderColony == null) return;

        boolean attackersExhausted =
                !war.getAttackerLives().isEmpty()
                && war.getAttackerLives().values().stream().allMatch(v -> v <= 0)
                && war.getRemainingAttackerGuards() <= 0;
        if (attackersExhausted) {
            LOGGER.info("Experimental victory refused: attackers already exhausted for war {}; "
                    + "the legacy defender-win resolution will run.", war.getWarID());
            WAR_HITS.remove(war.getWarID());
            return;
        }

        // Broadcast the victory cause.
        Component victoryMsg = Component.literal("EXPERIMENTAL VICTORY ƒ?" Town Hall demolished!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (winner.getServer() != null) {
            for (UUID uuid : war.getAttackerLives().keySet()) {
                ServerPlayer p = winner.getServer().getPlayerList().getPlayer(uuid);
                if (p != null) p.sendSystemMessage(victoryMsg);
            }
            for (UUID uuid : war.getDefenderLives().keySet()) {
                ServerPlayer p = winner.getServer().getPlayerList().getPlayer(uuid);
                if (p != null) p.sendSystemMessage(victoryMsg);
            }
        }

        // Zero defender lives + nudge checkForVictory to drive the existing flow.
        for (Map.Entry<UUID, Integer> e : new HashMap<>(war.getDefenderLives()).entrySet()) {
            war.getDefenderLives().put(e.getKey(), 0);
        }
        war.remainingDefenderGuards = 0;

        try {
            WarSystem.checkForVictory(war);
        } catch (Exception e) {
            LOGGER.error("Failed to trigger experimental victory for war {}", war.getWarID(), e);
        }

        // Cleanup per-war state.
        WAR_HITS.remove(war.getWarID());
    }

    /**
     * Hook called from WarSystem.endWar so per-war hit state doesn't leak when
     * a war ends through normal (non-demolition) resolution.
     */
    public static void onWarEnded(UUID warId) {
        WAR_HITS.remove(warId);
    }

    private static WarData findWarForAttacker(UUID attackerUUID) {
        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            if (war.getAttackerLives().containsKey(attackerUUID)) return war;
        }
        return null;
    }

    /** Drop all state ƒ?" for server shutdown or war end. */
    public static void clearAll() {
        WAR_HITS.clear();
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
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -Skip 440 -First 90' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -Skip 720 -First 90' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -Skip 80 -First 100' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -Skip 930 -First 90' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"reclaimByOriginalOwner\\(|completeReclaim|deductFromTreasury|addToTreasury|getEffectiveMaxCapacity|getTreasuryBalance\" src/main/java/net/machiavelli/minecolonytax" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 490ms:
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:599:                completeReclaim(raid, colony);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:630:        int loserBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(loser.getID());
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:637:        int winnerBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(winner.getID());
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:638:        int winnerCap = net.machiavelli.minecolonytax.economy.TreasuryManager.getEffectiveMaxCapacity(winner.getID());
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:643:        net.machiavelli.minecolonytax.economy.TreasuryManager.deductFromTreasury(loser.getID(), actualSpoil);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:644:        net.machiavelli.minecolonytax.economy.TreasuryManager.addToTreasury(winner.getID(), actualSpoil);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:710:    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:89:    public static int getTreasuryBalance(int colonyId) {
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:147:        int currentBalance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:148:        int maxCapacity = getEffectiveMaxCapacity(colonyId);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:213:        int currentBalance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:263:        int balance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:264:        int maxCapacity = getEffectiveMaxCapacity(colonyId);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:319:        int attackerBalance = getTreasuryBalance(attackerColonyId);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:358:        int attackerBalance = getTreasuryBalance(attackerColonyId);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:386:            baseDrain = (int) Math.ceil(getEffectiveMaxCapacity(colonyId) * TaxConfig.getTreasuryDrainPercent());
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:412:        int currentBalance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:433:    public static int deductFromTreasury(int colonyId, int amount) {
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:434:        int currentBalance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:449:    public static int addToTreasury(int colonyId, int amount) {
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:450:        int currentBalance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:451:        int maxCapacity = getEffectiveMaxCapacity(colonyId);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:464:        return TaxConfig.isTreasuryEnabled() && getTreasuryBalance(colonyId) <= 0;
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:476:        int currentBalance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:522:    public static int getEffectiveMaxCapacity(int colonyId) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3773:                int treasuryBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3807:                player.sendSystemMessage(Component.literal("Treasury: " + net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(colonyId)).withStyle(ChatFormatting.AQUA));
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3857:                int balance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:670:                            TreasuryManager.addToTreasury(colonyId, depositAmount);
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:877:                                int treasuryBalance = TreasuryManager.getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:518:    public static boolean reclaimByOriginalOwner(int colonyId, UUID reclaimerUUID) {
src/main/java/net/machiavelli/minecolonytax\network\packets\BuyInvestmentPacket.java:76:            int treasuryBalance = TreasuryManager.getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\network\packets\BuyInvestmentPacket.java:77:            int maxCapacity = TreasuryManager.getEffectiveMaxCapacity(colonyId);
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:216:                int treasuryBalance = TreasuryManager.getTreasuryBalance(raiderColony.getID());
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:230:                TreasuryManager.deductFromTreasury(raiderColony.getID(), raidCost);
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:697:                            .getTreasuryBalance(raiderColonyForPenalty.getID())
src/main/java/net/machiavelli/minecolonytax\network\packets\InvestmentDataResponsePacket.java:81:    public int getTreasuryBalance()                { return treasuryBalance; }
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestInvestmentDataPacket.java:55:            int treasuryBalance = TreasuryManager.getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestInvestmentDataPacket.java:56:            int maxCapacity = TreasuryManager.getEffectiveMaxCapacity(colonyId);
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestTreasuryDataPacket.java:54:            int balance = TreasuryManager.getTreasuryBalance(colonyId);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 673ms:
                "Reclaim successful! " + colony.getName() + " is free from occupation.")
                .withStyle(ChatFormatting.GREEN));

        broadcastToNearbyPlayers(colony,
                Component.literal(colony.getName() + " has been reclaimed by its owner!")
                        .withStyle(ChatFormatting.GOLD), 300);

        if (TaxConfig.isNormalLogging())
            LOGGER.info("Besiege reclaim: colony {} reclaimed by {}", colony.getName(),
                    getPlayerName(raid.besiegingPlayerUUID));
    }


    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
        IColony colony = getColonyById(raid.colonyId);
        if (colony != null) {
            // Revoke combat permissions from the besieger (and any allies)
            revokeBesiegeCombatPermissions(colony, raid.besiegingPlayerUUID);
            for (UUID ally : raid.alliedPlayers) {
                revokeBesiegeCombatPermissions(colony, ally);
            }

            // Restore citizen AI
            for (int citizenId : raid.hostileCitizenIds) {
                try {
                    ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
                    if (citizen != null && citizen.getEntity().isPresent()) {
                        AbstractEntityCitizen entity = citizen.getEntity().get();
                        entity.goalSelector.removeAllGoals(g -> true);
                        entity.targetSelector.removeAllGoals(g -> true);
                        // Remove militia sword if present
                        if (entity.getMainHandItem().getItem() == Items.WOODEN_SWORD) {
                            entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                        }
                        // Remove combat effects
                        entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                        entity.removeEffect(MobEffects.MOVEMENT_SPEED);
                        entity.removeEffect(MobEffects.DAMAGE_BOOST);
                        // Restore job AI
                        if (citizen.getJob() != null) {
                            entity.getCitizenJobHandler().onJobChanged(citizen.getJob());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to restore citizen {} AI after besiege", citizenId, e);
                }
            }

            // Despawn militia-upgrade reinforcements (NOT victory-counted)
            for (Entity militia : raid.militiaSupport) {
                try {
                    if (militia.isAlive()) militia.remove(Entity.RemovalReason.DISCARDED);
                } catch (Exception e) {
                    LOGGER.warn("Failed to despawn militia reinforcement after besiege", e);
                }
            }

            // Despawn mercenaries
            for (Entity merc : raid.spawnedMercenaries) {
                try {
                    if (merc.isAlive()) merc.remove(Entity.RemovalReason.DISCARDED);
                } catch (Exception e) {
                    LOGGER.warn("Failed to despawn mercenary after besiege", e);
                }
            }
        }

        // Remove boss bar
        if (raid.bossEvent != null) {
            try {
                raid.bossEvent.removeAllPlayers();
            } catch (Exception e) {
                LOGGER.warn("Failed to remove besiege boss bar", e);
            }
            raid.bossEvent = null;
        }

        if (removeFromMap) {
            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
        }
    }


    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
        try {
            int minutes = TaxConfig.getBesiegeDurationMinutes();
            Component text = Component.literal(
                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
                            colony.getName(), totalDefenders, minutes, 0))
                    .withStyle(ChatFormatting.YELLOW);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 665ms:
    /**
     * Backward-compatible view of active raids keyed by colonyId.
     *
     * Since multi-besieger support landed, the internal storage is keyed by
     * besieger UUID. This view returns at most ONE raid per colony (the first
     * one encountered). Callers that need ALL raids for a colony must use
     * {@link #getRaidsForColony(int)}; callers that need a specific besieger's
     * raid should use {@link #getRaidForBesieger(UUID)}.
     */
    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
        Map<Integer, BesiegeRaidData> view = new HashMap<>();
        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
            view.putIfAbsent(raid.colonyId, raid);
        }
        return Collections.unmodifiableMap(view);
    }

    /** Direct lookup by besieger UUID. Null when this player has no active raid. */
    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
        return ACTIVE_RAIDS.get(besiegerUUID);
    }

    /** Read-only view of all active raids keyed by besieger UUID. */
    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
        return Collections.unmodifiableMap(ACTIVE_RAIDS);
    }

    public static Map<Integer, BesiegeOccupationData> getAllOccupations() {
        return Collections.unmodifiableMap(OCCUPATIONS);
    }


    private static boolean allDefendersDead(BesiegeRaidData raid, IColony colony) {
        return countAliveDefenders(raid, colony) == 0;
    }

    private static int countAliveDefenders(BesiegeRaidData raid, IColony colony) {
        int alive = 0;
        // Citizens
        for (int citizenId : raid.hostileCitizenIds) {
            try {
                ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
                if (citizen != null && citizen.getEntity().isPresent()
                        && citizen.getEntity().get().isAlive()) {
                    alive++;
                }
            } catch (Exception ignored) {}
        }
        // Mercenaries
        for (Entity merc : raid.spawnedMercenaries) {
            if (merc.isAlive()) alive++;
        }
        return alive;
    }

    private static void applyCooldown(UUID playerUUID) {
        long cooldownMs = TaxConfig.getBesiegeCooldownHours() * 3600_000L;
        PLAYER_COOLDOWNS.put(playerUUID, System.currentTimeMillis() + cooldownMs);
    }

    private static BlockPos findSpawnPos(BlockPos center, Level world) {
        Random rng = new Random();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = center.getX() + rng.nextInt(20) - 10;
            int z = center.getZ() + rng.nextInt(20) - 10;
            BlockPos surface = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
            if (world.getBlockState(surface).isAir() && !world.getBlockState(surface.below()).isAir()) {
                return surface;
            }
        }
        return center;
    }

    private static void broadcastToNearbyPlayers(IColony colony, Component message, int radius) {
        Level world = colony.getWorld();
        if (!(world instanceof ServerLevel serverLevel)) return;
        BlockPos center = colony.getCenter();
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (player.level() == world) {
                double dist = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
                if (dist <= (double) radius * radius) {
                    player.sendSystemMessage(message);
                }
            }
        }
    }

    private static void sendToPlayer(UUID uuid, Component message) {
        if (SERVER == null) return;
        ServerPlayer player = SERVER.getPlayerList().getPlayer(uuid);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 689ms:
        }
        return count;
    }

    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
        entity.goalSelector.removeAllGoals(g -> true);
        entity.targetSelector.removeAllGoals(g -> true);

        // Use MilitiaAttackGoal instead of vanilla MeleeAttackGoal ƒ?" non-guard citizens
        // lack the ATTACK_DAMAGE attribute, which causes MeleeAttackGoal.doHurtTarget() to
        // crash with IllegalArgumentException.
        entity.goalSelector.addGoal(0, new MilitiaAttackGoal(entity, 1.2D));

        // Retaliate against anyone who hits them (covers allies)
        entity.targetSelector.addGoal(0, new HurtByTargetGoal(entity));

        // Proactively hunt the besieger (and any allies)
        entity.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(entity, Player.class,
                20, true, false, (target) -> {
                    if (!(target instanceof ServerPlayer sp)) return false;
                    return sp.getUUID().equals(besieger.getUUID())
                            || raid.alliedPlayers.contains(sp.getUUID());
                }));
    }

    private static void applyDefenderEffects(AbstractEntityCitizen entity, int durationMinutes) {
        int ticks = durationMinutes * 60 * 20;
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1));
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
    }

    /**
     * Applies bonus DAMAGE_RESISTANCE to all besiege defenders based on the
     * colony's FORTIFICATION investment level. Called once after all defenders
     * are spawned in launchRaid().
     * Each 20% damage reduction from the investment adds +1 resistance amplifier.
     */
    private static void applyFortificationBonus(IColony colony, BesiegeRaidData raid) {
        if (!TaxConfig.isUpgradesEnabled()) return;
        double dmgReduction = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
                .getFortificationDamageReduction(colony.getID());
        if (dmgReduction <= 0) return;
        int extraAmplifier = (int) (dmgReduction / 0.20);
        if (extraAmplifier <= 0) return;

        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
        // Re-apply DAMAGE_RESISTANCE to citizens with the boosted amplifier
        for (int citizenId : raid.hostileCitizenIds) {
            colony.getCitizenManager().getCitizens().stream()
                    .filter(c -> c.getId() == citizenId)
                    .findFirst()
                    .flatMap(ICitizenData::getEntity)
                    .ifPresent(entity -> entity.addEffect(
                            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier)));
        }
        // Also boost mercenaries
        for (Entity merc : raid.spawnedMercenaries) {
            if (merc instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
                living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier));
            }
        }
        if (TaxConfig.isDebugLogging())
            LOGGER.debug("FORTIFICATION bonus applied to colony {} defenders (extra resistance amplifier: {})",
                    colony.getName(), extraAmplifier);
    }

    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
        Level world = colony.getWorld();
        if (!(world instanceof ServerLevel)) return 0;

        int buildingCount = 0;
        try {
            buildingCount = net.machiavelli.minecolonytax.compat.ColonyBuildingUtil
                    .getBuildings(colony).size();
        } catch (Exception e) {
            LOGGER.warn("Could not count buildings for mercenary calc in colony {}", colony.getName());
        }

        int count = (int) (buildingCount * TaxConfig.getBesiegeExtraMercenariesPerBuilding());
        count = Math.min(count, TaxConfig.getBesiegeMaxMercenaries());
        if (count <= 0) return 0;

        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
        int spawned = 0;

        for (int i = 0; i < count; i++) {
            try {
                EntityMercenary merc = (EntityMercenary) com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
                if (merc == null) continue;
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 692ms:
        loadData(server);
        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager initialized");
    }

    public static void shutdown() {
        saveData();
        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
            cleanupRaid(raid, false);
        }
        ACTIVE_RAIDS.clear();
        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager shutdown complete");
    }

    public static void tick() {
        if (ACTIVE_RAIDS.isEmpty()) return;

        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
            BesiegeRaidData raid = entry.getValue();

            try {
                IColony colony = getColonyById(raid.colonyId);
                if (colony == null) {
                    LOGGER.warn("BesiegeManager.tick: colony {} not found, ending raid", raid.colonyId);
                    cleanupRaid(raid, false);
                    it.remove();
                    continue;
                }

                ServerPlayer besieger = SERVER.getPlayerList().getPlayer(raid.besiegingPlayerUUID);

                // --- Timer expired: defenders win ---
                if (System.currentTimeMillis() >= raid.endTime) {
                    if (TaxConfig.isNormalLogging())
                        LOGGER.info("Besiege raid on colony {} timed out ƒ?" defenders win", colony.getName());
                    sendToPlayer(raid.besiegingPlayerUUID,
                            Component.literal("The besiege of " + colony.getName() + " has failed ƒ?" the defenders held out!")
                                    .withStyle(ChatFormatting.RED));
                    broadcastToNearbyPlayers(colony,
                            Component.literal(colony.getName() + " successfully repelled the besiege!")
                                    .withStyle(ChatFormatting.GREEN), 200);
                    // Route through completeBesiege so siege spoils + cooldown + cleanup all fire
                    // via a single path. Previously the timeout cleaned up directly, skipping
                    // defender-victory siege spoils entirely.
                    completeBesiege(raid, false, colony);
                    it.remove();
                    continue;
                }

                // --- Besieger left the area ---
                if (besieger != null) {
                    BlockPos center = colony.getCenter();
                    double dist = besieger.distanceToSqr(center.getX(), center.getY(), center.getZ());
                    int maxRadius = TaxConfig.getBesiegePlayerStayRadius();
                    if (dist > (double) maxRadius * maxRadius) {
                        besieger.sendSystemMessage(Component.literal(
                                "You left the besiege area ƒ?" the raid has been cancelled!")
                                .withStyle(ChatFormatting.RED));
                        cleanupRaid(raid, false);
                        applyCooldown(raid.besiegingPlayerUUID);
                        it.remove();
                        continue;
                    }

                    // Track allies: anyone who recently damaged a defender
                    // (ally tracking is done in the kill/hurt event ƒ?" see RaidKillTracker integration)
                }

                // --- Victory: all defenders dead ---
                if (allDefendersDead(raid, colony)) {
                    if (TaxConfig.isNormalLogging())
                        LOGGER.info("Besiege raid on colony {} successful ƒ?" besieger wins", colony.getName());
                    completeBesiege(raid, true, colony);
                    it.remove();
                    continue;
                }

                // --- Update boss bar ---
                updateBossBar(raid, colony);

            } catch (Exception e) {
                LOGGER.error("Error ticking besiege raid for colony {}", entry.getKey(), e);
            }
        }
    }


    /**
     * Start a besiege raid. Validates all preconditions.
     * Returns true if the raid started successfully.
     */
    public static boolean startBesiege(IColony colony, ServerPlayer besieger) {
        if (!TaxConfig.isBesiegeSystemEnabled()) {
            besieger.sendSystemMessage(Component.literal("The besiege system is disabled on this server.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        UUID besiegerUUID = besieger.getUUID();
        int colonyId = colony.getID();
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -Skip 680 -First 60' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java | Select-Object -Skip 420 -First 45' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"public static boolean transferOwnership|transferOwnership\\(\" src/main/java/net/machiavelli/minecolonytax/WarSystem.java src/main/java/net/machiavelli/minecolonytax/occupation src/main/java/net/machiavelli/minecolonytax/besiege" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-Object -Skip 1200 -First 100' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java | Select-Object -Skip 510 -First 30' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 546ms:
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:774:                    transferOwnership(war.getColony(), war.getAttacker());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:1191:    public static boolean transferOwnership(IColony colony, UUID newOwnerUUID) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:1829:                transferOwnership(war.getColony(), war.getAttacker());
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:1886:                transferOwnership(war.getAttackerColony(), war.getDefender());
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:467:                boolean transferred = WarSystem.transferOwnership(colony, occupierUUID);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 750ms:
        file.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(TREASURIES, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save treasury data: {}", e.getMessage());
        }
    }

    // ==================== Utility ====================

    public static int getEffectiveMaxCapacity(int colonyId) {
        int base = TaxConfig.getTreasuryMaxCapacity();
        if (!TaxConfig.isUpgradesEnabled()) return base;
        return base + net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager.getTreasuryCapBonus(colonyId);
    }

    public static IColony getColony(int colonyId) {
        if (SERVER == null)
            return null;
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        return colonyManager.getColonyByWorld(colonyId, SERVER.overworld());
    }
}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 801ms:
            if (ownerUUID != null) {
                String besiegerName = getPlayerName(raid.besiegingPlayerUUID);
                sendToPlayer(ownerUUID, Component.literal(
                        "Your colony " + colony.getName() + " has been besieged by " + besiegerName
                                + "! Tax tribute (" + tributePct + "%) now flows to them. "
                                + "Use /wnt besiege " + colony.getName() + " to reclaim it.")
                        .withStyle(ChatFormatting.RED));
            }

            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
                    "Besiege victory! " + colony.getName() + " now pays you " + tributePct
                            + "% in tribute" + (durationHours > 0 ? " for " + durationHours + "h" : "") + ".")
                    .withStyle(ChatFormatting.GREEN));

            broadcastToNearbyPlayers(colony,
                    Component.literal(colony.getName() + " has fallen under besiege occupation!")
                            .withStyle(ChatFormatting.DARK_RED), 300);

            if (TaxConfig.isNormalLogging())
                LOGGER.info("Besiege victory: colony {} vassalized to {} at {}% tribute",
                        colony.getName(), getPlayerName(raid.besiegingPlayerUUID), tributePct);
        } else {
            // Colony was already a vassal (edge case) ƒ?" just notify
            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
                    "Besiege raid completed but vassalization could not be established (colony may already be a vassal).")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
        int colonyId = colony.getID();

        // Remove vassalization
        VassalManager.removeVassalRelation(colonyId);

        // Remove occupation record
        OCCUPATIONS.remove(colonyId);
        saveData();

        sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
                "Reclaim successful! " + colony.getName() + " is free from occupation.")
                .withStyle(ChatFormatting.GREEN));

        broadcastToNearbyPlayers(colony,
                Component.literal(colony.getName() + " has been reclaimed by its owner!")
                        .withStyle(ChatFormatting.GOLD), 300);

        if (TaxConfig.isNormalLogging())
            LOGGER.info("Besiege reclaim: colony {} reclaimed by {}", colony.getName(),
                    getPlayerName(raid.besiegingPlayerUUID));
    }


    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
        IColony colony = getColonyById(raid.colonyId);
        if (colony != null) {
            // Revoke combat permissions from the besieger (and any allies)
            revokeBesiegeCombatPermissions(colony, raid.besiegingPlayerUUID);
            for (UUID ally : raid.alliedPlayers) {
                revokeBesiegeCombatPermissions(colony, ally);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 780ms:
        // a meaningful consequence for the loser.
        if (!net.machiavelli.minecolonytax.permissions.ColonyTierGuard.canTransferOwnership(colony)) {
            WARSYSTEM_LOGGER.info("Ownership transfer denied for colony {}: {}",
                    colony.getID(),
                    net.machiavelli.minecolonytax.permissions.ColonyTierGuard.getTransferDenialReason(colony));
            if (TaxConfig.isWarVassalizationEnabled()) {
                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
                int durationHours = TaxConfig.getWarVassalizationDurationHours();
                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
                        colony, newOwnerUUID, tributePercent, durationHours);
                if (vassalized) {
                    WARSYSTEM_LOGGER.info("Primary colony {} vassalized by {} instead of transferred ({}% tribute for {}h)",
                            colony.getName(), newOwnerUUID, tributePercent, durationHours);
                    WarData war = ACTIVE_WARS.get(colony.getID());
                    Component msg = Component.literal(colony.getName()
                            + " is a Primary colony ƒ?" vassalized instead of conquered.")
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true));
                    sendNotificationToWarParticipants(colony, war != null ? war.getAttackerColony() : null, msg);
                }
            }
            return false;
        }

        ServerPlayer newOwner = colony.getWorld().getServer().getPlayerList().getPlayer(newOwnerUUID);
        if (newOwner == null) {
            return false;
        }
        if (colony.getPermissions().setOwner(newOwner)) {
            colony.markDirty();
            Component msg = Component.literal(colony.getName() + " conquered by " + newOwner.getName().getString())
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED).withBold(true));
            WarData war = ACTIVE_WARS.get(colony.getID());
            sendNotificationToWarParticipants(colony, war != null ? war.getAttackerColony() : null, msg);
            return true;
        } else {
            WARSYSTEM_LOGGER.error("Ownership transfer failed for colony {}", colony.getID());
            return false;
        }
    }

    public static void endWar(IColony colony) {
        // Get war data before removing it from active wars
        WarData warData = ACTIVE_WARS.get(colony.getID());

        // Remove resistance effects from guards in both colonies
        if (warData != null) {
            GuardResistanceHandler.removeResistanceFromGuardsForWar(warData.getColony());
            if (warData.getAttackerColony() != null) {
                GuardResistanceHandler.removeResistanceFromGuardsForWar(warData.getAttackerColony());
            }

            // Clean up militia system for both colonies
            cleanupWarMilitiaSystem(warData);

            // Restore all explosion-damaged blocks ledgered for this war.
            // Bug #8 fix: previously the ledger only accumulated and never restored.
            try {
                if (warData.getColony() != null && warData.getColony().getWorld() != null) {
                    net.machiavelli.minecolonytax.siege.WarBlockLedger.restoreWarDamage(
                            warData.getWarID(), warData.getColony().getWorld());
                }
            } catch (Exception e) {
                WARSYSTEM_LOGGER.error("Failed to restore war block ledger for war {}", warData.getWarID(), e);
            }

            // Drop any Town Hall demolition hit state so it doesn't leak between wars.
            try {
                net.machiavelli.minecolonytax.siege.TownHallDemolitionObjective.onWarEnded(warData.getWarID());
            } catch (Exception ignored) {}
        }

        // Disable war actions for both sides
        setWarInteractionPermissions(colony, false);

        // Also disable for attacker colony if it exists
        if (warData != null && warData.getAttackerColony() != null) {
            setWarInteractionPermissions(warData.getAttackerColony(), false);
        }

        // Demote war participants out of the Hostile rank on both colonies.
        // Attackers were assigned hostile on the defender colony; defenders on the attacker colony.
        if (warData != null) {
            if (warData.getAttackerLives() != null) {
                demoteParticipantsFromHostile(colony, warData.getAttackerLives().keySet());
            }
            if (warData.getAttackerColony() != null && warData.getDefenderLives() != null) {
                demoteParticipantsFromHostile(warData.getAttackerColony(), warData.getDefenderLives().keySet());
            }
        }

        // Now remove from active wars
        warData = ACTIVE_WARS.remove(colony.getID());

        // Restore Hostile rank to pre-war state now that the war is no longer active
        net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(colony);
        if (warData != null && warData.getAttackerColony() != null) {
            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.restoreIfNoConflict(warData.getAttackerColony());
        }

        // Remove War Exhaustion status and start recovery period
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 804ms:
        }

        return newBalance;
    }

    /**
     * Deduct a specific amount from the treasury (for one-time raid costs).
     *
     * @param colonyId The colony ID
     * @param amount   Amount to deduct
     * @return new balance after deduction
     */
    public static int deductFromTreasury(int colonyId, int amount) {
        int currentBalance = getTreasuryBalance(colonyId);
        int newBalance = Math.max(0, currentBalance - amount);
        TREASURIES.put(colonyId, newBalance);
        // Periodic save handled by WarSystem drain loop
        if (TaxConfig.isNormalLogging()) LOGGER.info("Deducted {} from colony {} treasury. New balance: {}", amount, colonyId, newBalance);
        return newBalance;
    }

    /**
     * Add a specific amount to the treasury.
     *
     * @param colonyId The colony ID
     * @param amount   Amount to add
     * @return new balance after addition
     */
    public static int addToTreasury(int colonyId, int amount) {
        int currentBalance = getTreasuryBalance(colonyId);
        int maxCapacity = getEffectiveMaxCapacity(colonyId);
        int newBalance = Math.min(maxCapacity, currentBalance + amount);

        TREASURIES.put(colonyId, newBalance);
        saveData();
        if (TaxConfig.isNormalLogging()) LOGGER.info("Added {} to colony {} treasury. New balance: {}", amount, colonyId, newBalance);
        return newBalance;
    }

    /**
     * Check if the treasury is depleted (for auto-surrender).
     */
    public static boolean isTreasuryDepleted(int colonyId) {
        return TaxConfig.isTreasuryEnabled() && getTreasuryBalance(colonyId) <= 0;
    }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
The key fixes are present in final source, but I’m seeing one important behavioral gap around the reclaim path and one possible attribution/ledger limitation that may matter depending on the original bug wording. I’m checking whether these are regressions introduced by the rework or pre-existing baseline issues before I grade the bundle.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -Skip 1020 -First 230' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"class TickScheduler|scheduleRepeating|cancel\\(\" src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java src/main/java/net/machiavelli/minecolonytax" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -Skip 0 -First 70' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -Skip 200 -First 160' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"ACTIVE_RAIDS|OCCUPATIONS|public static boolean startReclaim|isReclaim|BesiegeRaidData\\(\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 357ms:
src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:26:public class TickScheduler {
src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:55:    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java:63:    public static void cancel(long taskId) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:331:        data.warChestDrainTaskId = TickScheduler.scheduleRepeating(() -> {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1315:                TickScheduler.cancel(warData.countdownTaskId);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1319:                TickScheduler.cancel(warData.warChestDrainTaskId);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2325:            TickScheduler.scheduleRepeating(() -> {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2490:        warData.countdownTaskId = TickScheduler.scheduleRepeating(() -> {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2497:                TickScheduler.cancel(warData.countdownTaskId);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2504:                TickScheduler.cancel(warData.countdownTaskId);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2522:                TickScheduler.cancel(warData.countdownTaskId);
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:261:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:273:        net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:286:                net.machiavelli.minecolonytax.util.TickScheduler.scheduleRepeating(() -> {
src/main/java/net/machiavelli/minecolonytax\commands\TradeRouteCommand.java:70:    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
src/main/java/net/machiavelli/minecolonytax\util\TickScheduler.java:26:public class TickScheduler {
src/main/java/net/machiavelli/minecolonytax\util\TickScheduler.java:55:    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
src/main/java/net/machiavelli/minecolonytax\util\TickScheduler.java:63:    public static void cancel(long taskId) {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:914:        raidData.setCountdownTaskId(TickScheduler.scheduleRepeating(() -> {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:918:                TickScheduler.cancel(raidData.getCountdownTaskId());
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:926:                TickScheduler.cancel(raidData.getCountdownTaskId());
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:937:                TickScheduler.cancel(raidData.getCountdownTaskId());
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:992:                TickScheduler.cancel(raidData.getCountdownTaskId());
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1005:                TickScheduler.cancel(raidData.getCountdownTaskId());
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1313:            TickScheduler.cancel(raidData.getCountdownTaskId());
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:165:        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
src/main/java/net/machiavelli/minecolonytax\siege\WarBlockLedger.java:186:                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 318ms:
69:    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
72:    private static final Map<Integer, BesiegeOccupationData> OCCUPATIONS = new ConcurrentHashMap<>();
87:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
90:        ACTIVE_RAIDS.clear();
95:        if (ACTIVE_RAIDS.isEmpty()) return;
97:        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
205:        if (ACTIVE_RAIDS.containsKey(besiegerUUID)) {
255:    public static boolean startReclaim(IColony colony, ServerPlayer reclaimingPlayer) {
263:        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
279:        if (ACTIVE_RAIDS.containsKey(playerUUID)) {
303:    private static boolean launchRaid(IColony colony, ServerPlayer besieger, boolean isReclaim) {
308:            BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
309:            ACTIVE_RAIDS.put(besiegerUUID, raid);
336:            String verb = isReclaim ? "RECLAIM RAID" : "BESIEGE";
345:                            + " is under " + (isReclaim ? "reclaim attack" : "siege")
375:                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
382:            ACTIVE_RAIDS.remove(besiegerUUID);
598:            if (raid.isReclaim) {
677:            OCCUPATIONS.put(colony.getID(), occ);
717:        OCCUPATIONS.remove(colonyId);
799:            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
864:        return OCCUPATIONS.containsKey(colonyId);
868:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
877:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
888:        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
895:        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
928:        return OCCUPATIONS.get(colonyId);
942:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
950:        return ACTIVE_RAIDS.get(besiegerUUID);
955:        return Collections.unmodifiableMap(ACTIVE_RAIDS);
959:        return Collections.unmodifiableMap(OCCUPATIONS);
1178:                    OCCUPATIONS.put(occ.colonyId, occ);
1182:                LOGGER.info("Loaded {} besiege occupation record(s)", OCCUPATIONS.size());
1191:        final List<BesiegeOccupationData> list = new ArrayList<>(OCCUPATIONS.values());
1224:        public final boolean isReclaim;
1226:        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
1230:            this.isReclaim = isReclaim;
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 436ms:
package net.machiavelli.minecolonytax.besiege;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.core.entity.mobs.EntityMercenary;
import net.machiavelli.minecolonytax.FirstColonyTracker;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.militia.MilitiaAttackGoal;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the besiege system ƒ?" single-player raids on active non-primary colonies
 * that grant tax vassalage on victory, and the reclaim flow for the former owner.
 */
public class BesiegeManager {

    private static final Logger LOGGER = LogManager.getLogger(BesiegeManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/besieged_colonies.json";

    /**
     * Active besiege raids, keyed by besieger UUID. Each besieger has at most one
     * active raid at a time (solo combat rule). Multiple besiegers may concurrently
     * raid the same colony ƒ?" look them up by colony with {@link #getRaidsForColony(int)}.
     *
     * Phase 2 follow-up: defender pool is still per-raid, not shared across
     * concurrent besiegers on the same colony. Each besieger currently spawns its
     * own mercs and tracks its own hostile-citizen set; last-kill-credit semantics
     * are not yet implemented.
     */
    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();

Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 466ms:
        if (player != null) {
            player.sendSystemMessage(message);
        }
        // Offline messages are not queued for the besiege system (unlike VassalManager)
        // because besiege outcomes are time-sensitive and stale messages would confuse players.
    }

    /**
     * Sends a message to the colony owner and all online officers, regardless of distance.
     * Used to ensure colonists responsible for defense are always informed of a besiege.
     */
    private static void notifyColonyOwnersAndOfficers(IColony colony, Component message) {
        if (SERVER == null) return;
        com.minecolonies.api.colony.permissions.IPermissions perms = colony.getPermissions();
        // Owner
        sendToPlayer(perms.getOwner(), message);
        // Officers
        try {
            for (com.minecolonies.api.colony.permissions.ColonyPlayer cp
                    : perms.getPlayersByRank(perms.getRankOfficer())) {
                sendToPlayer(cp.getID(), message);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Defender call-to-arms: notifies owner, officers, AND friends.
     * Per the Siege SMP rule, defender allies may answer the besiege; attacker
     * allies are blocked by the solo-besiege damage shield (step 5).
     */
    private static void notifyColonyDefenders(IColony colony, Component message) {
        if (SERVER == null) return;
        com.minecolonies.api.colony.permissions.IPermissions perms = colony.getPermissions();
        java.util.Set<UUID> notified = new java.util.HashSet<>();
        UUID owner = perms.getOwner();
        if (owner != null && notified.add(owner)) {
            sendToPlayer(owner, message);
        }
        try {
            for (com.minecolonies.api.colony.permissions.ColonyPlayer cp
                    : perms.getPlayersByRank(perms.getRankOfficer())) {
                if (cp != null && cp.getID() != null && notified.add(cp.getID())) {
                    sendToPlayer(cp.getID(), message);
                }
            }
        } catch (Exception ignored) {}
        try {
            for (com.minecolonies.api.colony.permissions.ColonyPlayer cp
                    : perms.getPlayersByRank(perms.getRankFriend())) {
                if (cp != null && cp.getID() != null && notified.add(cp.getID())) {
                    sendToPlayer(cp.getID(), message);
                }
            }
        } catch (Exception ignored) {}
    }

    private static String getPlayerName(UUID uuid) {
        if (SERVER == null) return uuid.toString();
        ServerPlayer p = SERVER.getPlayerList().getPlayer(uuid);
        return p != null ? p.getName().getString() : uuid.toString();
    }

    private static IColony getColonyById(int colonyId) {
        return IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
                .filter(c -> c.getID() == colonyId)
                .findFirst()
                .orElse(null);
    }

    private static IColony getPrimaryColonyOfPlayer(UUID playerId) {
        IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
        // Prefer FCT: it tracks the true first colony regardless of permissions state
        Integer firstColonyId = FirstColonyTracker.getFirstColony(playerId);
        if (firstColonyId != null) {
            IColony first = cm.getAllColonies().stream()
                    .filter(c -> c.getID() == firstColonyId)
                    .findFirst().orElse(null);
            if (first != null) return first;
        }
        // Fallback: any colony where the player is listed as MC owner
        for (IColony c : cm.getAllColonies()) {
            UUID owner = c.getPermissions().getOwner();
            if (owner != null && owner.equals(playerId)) return c;
        }
        return null;
    }


    /**
     * Grants the besieging player hostile rank and combat permissions on the target colony.
     * Without this, MineColonies blocks all player attacks on citizens with
     * "You do not have permission to do this in this colony!".
     */
    private static void grantBesiegeCombatPermissions(IColony colony, UUID playerUUID) {
        if (!TaxConfig.ENABLE_WAR_ACTIONS.get()) return;
        try {
            IPermissions perms = colony.getPermissions();
            // Snapshot before modifying (for restore on cleanup)
            net.machiavelli.minecolonytax.permissions.PermissionSnapshot.snapshotBefore(colony);

            // Assign the player to Hostile rank so guards treat them as enemy
            Rank hostile = perms.getRankHostile();
            perms.setPlayerRank(playerUUID, hostile, colony.getWorld());

            // Enable combat actions on the hostile rank
            for (Action a : TaxConfig.getWarActions()) {
                perms.setPermission(hostile, a, true);
            }

            if (TaxConfig.isDebugLogging())
                LOGGER.debug("Granted besiege combat permissions to {} on colony {}",
                        playerUUID, colony.getName());
        } catch (Exception e) {
            LOGGER.error("Failed to grant besiege combat permissions for {} on colony {}",
                    playerUUID, colony.getName(), e);
        }
    }

    /**
     * Revokes combat permissions and demotes the player from hostile back to neutral.
     * Called during raid cleanup.
     */
    private static void revokeBesiegeCombatPermissions(IColony colony, UUID playerUUID) {
        if (!TaxConfig.ENABLE_WAR_ACTIONS.get()) return;
        try {
            IPermissions perms = colony.getPermissions();

            // Disable combat actions on hostile rank
            Rank hostile = perms.getRankHostile();
            for (Action a : TaxConfig.getWarActions()) {
                perms.setPermission(hostile, a, false);
            }

            // Demote player back to neutral (skip if they are the colony owner)
            UUID owner = perms.getOwner();
            if (!playerUUID.equals(owner)) {
                Rank neutral = perms.getRankNeutral();
                perms.setPlayerRank(playerUUID, neutral, colony.getWorld());
            }

            if (TaxConfig.isDebugLogging())
                LOGGER.debug("Revoked besiege combat permissions from {} on colony {}",
                        playerUUID, colony.getName());
        } catch (Exception e) {
            LOGGER.error("Failed to revoke besiege combat permissions for {} on colony {}",
                    playerUUID, colony.getName(), e);
        }
    }

    private static void loadData(MinecraftServer server) {
        File f = new File(server.getServerDirectory(), STORAGE_FILE);
        if (!f.exists()) return;
        try (FileReader r = new FileReader(f)) {
            Type type = new TypeToken<List<BesiegeOccupationData>>() {}.getType();
            List<BesiegeOccupationData> list = GSON.fromJson(r, type);
            if (list != null) {
                for (BesiegeOccupationData occ : list) {
                    OCCUPATIONS.put(occ.colonyId, occ);
                }
            }
            if (TaxConfig.isNormalLogging())
                LOGGER.info("Loaded {} besiege occupation record(s)", OCCUPATIONS.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load besiege occupation data", e);
        }
    }

    private static void saveData() {
        if (SERVER == null) return;
        // Snapshot on the calling (server) thread.
        final List<BesiegeOccupationData> list = new ArrayList<>(OCCUPATIONS.values());
        final File f = new File(SERVER.getServerDirectory(), STORAGE_FILE);

        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("besiege", () -> {
            try {
                f.getParentFile().mkdirs();
                try (FileWriter w = new FileWriter(f)) {
                    GSON.toJson(list, w);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to save besiege occupation data", e);
            }
        });
    }


    /** Transient raid state ƒ?" not persisted. */
    public static class BesiegeRaidData {
        public final int colonyId;
        public final UUID besiegingPlayerUUID;
        public final long startTime;
        public final long endTime;
        public final Set<Integer> hostileCitizenIds = ConcurrentHashMap.newKeySet();
        public final Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
        /**
         * Militia upgrade reinforcements. Tracked separately from spawnedMercenaries
         * because by design they extend combat without counting toward victory.
         * allDefendersDead and countAliveDefenders intentionally ignore this set.
         */
        public final Set<Entity> militiaSupport = ConcurrentHashMap.newKeySet();
        public final Set<UUID> alliedPlayers = ConcurrentHashMap.newKeySet();
        public final BlockPos colonyCenter;
        public ServerBossEvent bossEvent;
        public final boolean isReclaim;

        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
            this.colonyId = colonyId;
            this.besiegingPlayerUUID = besiegingPlayerUUID;
            this.colonyCenter = colonyCenter;
            this.isReclaim = isReclaim;
            this.startTime = System.currentTimeMillis();
            this.endTime = startTime + (long) TaxConfig.getBesiegeDurationMinutes() * 60_000L;
        }
    }

    /** Persisted occupation record ƒ?" survives server restart. */
    public static class BesiegeOccupationData {
        public int colonyId;
        public String colonyName;
        public UUID besiegingPlayerUUID;
        public UUID formerOwnerUUID;
        public long besiegeTime;
        public int tributePercent;

        // For Gson deserialization
        public BesiegeOccupationData() {}

        public BesiegeOccupationData(int colonyId, String colonyName, UUID besiegingPlayerUUID,
                UUID formerOwnerUUID, long besiegeTime, int tributePercent) {
            this.colonyId = colonyId;
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 447ms:
        // TRANSFER_PENDING flow. See ColonyTierGuard + OccupationManager.

        // 4. Solo rule: this besieger may not already have an active raid elsewhere.
        // Multiple besiegers attacking the SAME colony concurrently is allowed.
        if (ACTIVE_RAIDS.containsKey(besiegerUUID)) {
            besieger.sendSystemMessage(Component.literal(
                    "You already have an active besiege. Only one besiege at a time per player.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 5. Cooldown check
        Long cooldownExpiry = PLAYER_COOLDOWNS.get(besiegerUUID);
        if (cooldownExpiry != null && System.currentTimeMillis() < cooldownExpiry) {
            long remaining = (cooldownExpiry - System.currentTimeMillis()) / 60000;
            besieger.sendSystemMessage(Component.literal(
                    "You must wait " + remaining + " more minute(s) before besieging again.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 6. Min colony size
        int citizenCount = colony.getCitizenManager().getCitizens().size();
        if (citizenCount < TaxConfig.getBesiegeMinColonySize()) {
            besieger.sendSystemMessage(Component.literal(
                    "Target colony is too small to besiege (needs at least "
                            + TaxConfig.getBesiegeMinColonySize() + " citizens).")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 7. Cannot besiege a colony that is currently in an active war
        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
            besieger.sendSystemMessage(Component.literal(
                    "This colony is already engaged in an active war. Besiege is not available during wartime.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 8. Cannot besiege a colony that is already a vassal of this player
        if (VassalManager.isColonyVassal(colonyId)
                && VassalManager.getVassalOverlordUUID(colonyId) != null
                && VassalManager.getVassalOverlordUUID(colonyId).equals(besiegerUUID)) {
            besieger.sendSystemMessage(Component.literal("This colony is already your vassal.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        return launchRaid(colony, besieger, false);
    }

    /**
     * Start a reclaim raid ƒ?" former owner/officer taking back their besieged colony.
     */
    public static boolean startReclaim(IColony colony, ServerPlayer reclaimingPlayer) {
        if (!TaxConfig.isBesiegeSystemEnabled()) {
            reclaimingPlayer.sendSystemMessage(Component.literal("The besiege system is disabled.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        int colonyId = colony.getID();
        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
        if (occ == null) {
            reclaimingPlayer.sendSystemMessage(Component.literal("This colony is not under besiege occupation.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        UUID playerUUID = reclaimingPlayer.getUUID();
        if (!occ.formerOwnerUUID.equals(playerUUID)) {
            reclaimingPlayer.sendSystemMessage(Component.literal(
                    "Only the former owner can reclaim this colony via besiege.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // Solo rule: this player may not already have an active raid.
        if (ACTIVE_RAIDS.containsKey(playerUUID)) {
            reclaimingPlayer.sendSystemMessage(Component.literal(
                    "You already have an active besiege/reclaim raid.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // Cooldown check
        Long cooldownExpiry = PLAYER_COOLDOWNS.get(playerUUID);
        if (cooldownExpiry != null && System.currentTimeMillis() < cooldownExpiry) {
            long remaining = (cooldownExpiry - System.currentTimeMillis()) / 60000;
            reclaimingPlayer.sendSystemMessage(Component.literal(
                    "You must wait " + remaining + " more minute(s) before attempting a reclaim.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        reclaimingPlayer.sendSystemMessage(Component.literal(
                "Reclaim raid started! Kill all defenders to reclaim " + colony.getName() + ".")
                .withStyle(ChatFormatting.GOLD));
        return launchRaid(colony, reclaimingPlayer, true);
    }


    private static boolean launchRaid(IColony colony, ServerPlayer besieger, boolean isReclaim) {
        int colonyId = colony.getID();
        UUID besiegerUUID = besieger.getUUID();

        try {
            BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
            ACTIVE_RAIDS.put(besiegerUUID, raid);

            // Grant the besieger hostile rank + combat permissions on the colony
            // so MineColonies allows the player to attack citizens.
            grantBesiegeCombatPermissions(colony, besiegerUUID);

            // Convert guards to hostile
            int guardCount = makeGuardsHostile(colony, besieger, raid);

            // Convert militia (non-guard eligible citizens)
            int militiaCount = convertCitizensToMilitia(colony, besieger, raid);

            // Spawn mercenaries
            int mercCount = spawnMercenaries(colony, besieger, raid);

            // Militia upgrade reinforcements ƒ?" NOT counted toward victory.
            // Each tier adds +N% bonus militia entities scaled by current guard count.
            int militiaUpgradeCount = spawnMilitiaUpgradeReinforcements(colony, besieger, raid, guardCount);

            int totalDefenders = guardCount + militiaCount + mercCount;

            // Apply FORTIFICATION investment: extra DAMAGE_RESISTANCE to all defenders
            applyFortificationBonus(colony, raid);

            // Create boss bar
            createBossBar(raid, besieger, colony, totalDefenders);

            String verb = isReclaim ? "RECLAIM RAID" : "BESIEGE";
            besieger.sendSystemMessage(Component.literal(
                    verb + " STARTED: " + colony.getName()
                            + " | Defenders: " + totalDefenders
                            + " | Time: " + TaxConfig.getBesiegeDurationMinutes() + "m")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

            broadcastToNearbyPlayers(colony,
                    Component.literal("Nearby colony " + colony.getName()
                            + " is under " + (isReclaim ? "reclaim attack" : "siege")
                            + " by " + besieger.getName().getString() + "!")
                            .withStyle(ChatFormatting.YELLOW), 200);

            // Notify owner + officers + friends ƒ?" the defender's call-to-arms.
            // Friends are included per the Siege SMP defender-ally rule: defenders may
            // mobilize allies even when the attacker must stand alone.
            BlockPos colonyCenter = colony.getCenter();
            net.minecraft.network.chat.Component callToArms = Component.literal("WARNING: ")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                    .append(Component.literal(besieger.getName().getString())
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal(" is besieging your colony ")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                    .append(Component.literal(colony.getName())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-Object -Skip 720 -First 90' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"getFirstColonyOwner|getFirstColony\\(|isFirstColony\" src/main/java/net/machiavelli/minecolonytax/FirstColonyTracker.java src/main/java/net/machiavelli/minecolonytax" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-Object -Skip 1800 -First 110' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 364ms:
package net.machiavelli.minecolonytax.util;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central tick-based scheduler that replaces all java.util.Timer/TimerTask usage.
 * All scheduled tasks run on the main server thread via Forge's ServerTickEvent,
 * eliminating cross-thread state mutation of war/raid data.
 *
 * <p>Key guarantees:
 * <ul>
 *   <li>All callbacks execute on the main server thread</li>
 *   <li>Deterministic tick-based timing (20 ticks = 1 second)</li>
 *   <li>Safe cancellation by task ID</li>
 *   <li>Automatic cleanup on server shutdown</li>
 * </ul>
 */
public class TickScheduler {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int TICKS_PER_SECOND = 20;

    private static final AtomicLong NEXT_TASK_ID = new AtomicLong(1);
    private static final Map<Long, ScheduledTask> TASKS = new ConcurrentHashMap<>();

    private static class ScheduledTask {
        final Runnable action;
        long ticksRemaining;       // ticks until first/next execution
        final long intervalTicks;  // 0 = one-shot, >0 = repeating
        volatile boolean cancelled;

        ScheduledTask(Runnable action, long delayTicks, long intervalTicks) {
            this.action = action;
            this.ticksRemaining = delayTicks;
            this.intervalTicks = intervalTicks;
            this.cancelled = false;
        }
    }

    public static long scheduleDelayed(Runnable action, long delayMs) {
        long ticks = msToTicks(delayMs);
        long id = NEXT_TASK_ID.getAndIncrement();
        TASKS.put(id, new ScheduledTask(action, ticks, 0));
        return id;
    }

    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
        long delayTicks = msToTicks(delayMs);
        long intervalTicks = Math.max(1, msToTicks(intervalMs));
        long id = NEXT_TASK_ID.getAndIncrement();
        TASKS.put(id, new ScheduledTask(action, delayTicks, intervalTicks));
        return id;
    }

    public static void cancel(long taskId) {
        ScheduledTask task = TASKS.remove(taskId);
        if (task != null) {
            task.cancelled = true;
        }
    }

    public static boolean isActive(long taskId) {
        return TASKS.containsKey(taskId);
    }

    public static void shutdown() {
        int count = TASKS.size();
        TASKS.values().forEach(t -> t.cancelled = true);
        TASKS.clear();
        if (count > 0 && net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
            LOGGER.info("TickScheduler shutdown - cleared {} pending tasks", count);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (TASKS.isEmpty()) return;

        Iterator<Map.Entry<Long, ScheduledTask>> it = TASKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ScheduledTask> entry = it.next();
            ScheduledTask task = entry.getValue();

            if (task.cancelled) {
                it.remove();
                continue;
            }

            task.ticksRemaining--;

            if (task.ticksRemaining <= 0) {
                try {
                    task.action.run();
                } catch (Exception e) {
                    LOGGER.error("TickScheduler task {} threw an exception", entry.getKey(), e);
                }

                if (task.cancelled) {
                    it.remove();
                } else if (task.intervalTicks > 0) {
                    task.ticksRemaining = task.intervalTicks;
                } else {
                    it.remove();
                }
            }
        }
    }

    private static long msToTicks(long ms) {
        return Math.max(1, ms / 50);
    }
}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 361ms:
src/main/java/net/machiavelli/minecolonytax/FirstColonyTracker.java:129:    public static Integer getFirstColony(UUID playerUUID) {
src/main/java/net/machiavelli/minecolonytax/FirstColonyTracker.java:149:    public static boolean isFirstColony(UUID playerUUID, int colonyID) {
src/main/java/net/machiavelli/minecolonytax/FirstColonyTracker.java:150:        Integer firstColony = getFirstColony(playerUUID);
src/main/java/net/machiavelli/minecolonytax/FirstColonyTracker.java:181:    public static UUID getFirstColonyOwner(int colonyId) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:1093:        Integer firstColonyId = FirstColonyTracker.getFirstColony(playerId);
src/main/java/net/machiavelli/minecolonytax\FirstColonyTracker.java:129:    public static Integer getFirstColony(UUID playerUUID) {
src/main/java/net/machiavelli/minecolonytax\FirstColonyTracker.java:149:    public static boolean isFirstColony(UUID playerUUID, int colonyID) {
src/main/java/net/machiavelli/minecolonytax\FirstColonyTracker.java:150:        Integer firstColony = getFirstColony(playerUUID);
src/main/java/net/machiavelli/minecolonytax\FirstColonyTracker.java:181:    public static UUID getFirstColonyOwner(int colonyId) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2575:        int primaryColonyId = FirstColonyTracker.getFirstColony(playerUUID);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2762:        boolean isPrimaryColony = FirstColonyTracker.isFirstColony(targetOwnerUUID, targetColony.getID());
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:64:            int primaryColonyId = net.machiavelli.minecolonytax.FirstColonyTracker.getFirstColony(raiderUUID);
src/main/java/net/machiavelli/minecolonytax\event\ColonyOwnershipHandler.java.disabled:69:            boolean isFirstColony = FirstColonyTracker.addColony(ownerUUID, colonyID);
src/main/java/net/machiavelli/minecolonytax\event\ColonyOwnershipHandler.java.disabled:71:            if (isFirstColony) {
src/main/java/net/machiavelli/minecolonytax\event\ColonyOwnershipHandler.java.disabled:91:        Integer firstColonyID = FirstColonyTracker.getFirstColony(playerUUID);
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:128:        int primaryColonyId = FirstColonyTracker.getFirstColony(attackerUUID);
src/main/java/net/machiavelli/minecolonytax\commands\OfficerTrackingDebugCommand.java:117:        UUID fctOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:171:                && net.machiavelli.minecolonytax.FirstColonyTracker.isFirstColony(originalOwner, colonyId);
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:41:        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:49:        if (FirstColonyTracker.isFirstColony(currentOwner, colony.getID())) {
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:74:        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:77:                || (owner != null && FirstColonyTracker.isFirstColony(owner, colony.getID()))) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 448ms:
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), victoryMsg);
            for (UUID defenderUUID : war.getDefenderLives().keySet()) {
                ServerPlayer defender = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defenderUUID);
                if (defender != null) {
                }
            }
            // Record war loss BEFORE economic transfers so immunity check uses pre-war
            // balance
            if (war.getAttackerColony() != null) {
                net.machiavelli.minecolonytax.economy.WarExhaustionManager
                        .recordWarLoss(war.getAttackerColony().getID());
            }
            applyWarEconomyTransfers(war, false);

        } else if (attackersWin) {
            String defenderColonyName = war.getColony().getName();
            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
                    : "The Attackers";
            Component conquestMsg = Component.empty()
                    .append(Component.translatable("war.attackers.win.title").withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.attackers.win.body", attackerColonyName, defenderColonyName)
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), conquestMsg);
            for (UUID attackerUUID : war.getAttackerLives().keySet()) {
                ServerPlayer attackerPlayer = war.getColony().getWorld().getServer().getPlayerList()
                        .getPlayer(attackerUUID);
                if (attackerPlayer != null) {
                    PlayerWarDataManager.incrementWarsWon(attackerPlayer);
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new WarVictoryEvent(attackerPlayer));
                }
            }
            // Record war loss for defender (they lost when attackers won)
            net.machiavelli.minecolonytax.economy.WarExhaustionManager.recordWarLoss(war.getColony().getID());
            // Apply victory/defeat balance transfers - attackers win, defenders pay
            applyWarEconomyTransfers(war, true);

            if (TaxConfig.ENABLE_COLONY_TRANSFER.get()) {
                if (TaxConfig.isOccupationSystemEnabled()) {
                    // Occupation phase: occupier collects taxes but can't interact, original owner
                    // has time to reclaim
                    net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
                            war.getColony(), war.getAttacker(), war.getAttackerColony());
                    WARSYSTEM_LOGGER.info("Colony {} is now occupied by {} (occupation system active)",
                            war.getColony().getName(), war.getAttacker());
                } else {
                    // Direct transfer (legacy behavior)
                    transferOwnership(war.getColony(), war.getAttacker());
                }
            } else if (TaxConfig.isWarVassalizationEnabled()) {
                // Vassalize the losing colony instead of transferring ownership
                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
                int durationHours = TaxConfig.getWarVassalizationDurationHours();
                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
                        war.getColony(),
                        war.getAttacker(),
                        tributePercent,
                        durationHours);
                if (vassalized) {
                    WARSYSTEM_LOGGER.info("Colony {} has been vassalized by {} for {} hours at {}% tribute",
                            war.getColony().getName(), war.getAttacker(), durationHours, tributePercent);
                }
            }
        }
        endWar(war.getColony());
    }

    /**
     * Applies economic penalties to both sides during a stalemate.
     * 
     * @param war The war data containing information about the conflict
     */
    private static void applyStalematePenalties(WarData war) {
        double stalematePenalty = TaxConfig.getWarStalematePercentage();
        if (stalematePenalty > 0) {
            // Apply stalemate penalties to both sides
            long attackerDeducted = 0;
            long defenderDeducted = 0;

            // Process attacker side
            if (war.getAttackerTeamID() != null) {
                attackerDeducted = WarEconomyHandler.deductTeamBalanceWithReport(war.getAttackerTeamID(),
                        stalematePenalty);
            } else {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 454ms:
            if (player != null)
                player.sendSystemMessage(notification);
        });
        war.getDefenderLives().keySet().forEach(uuid -> {
            ServerPlayer player = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
            if (player != null)
                player.sendSystemMessage(notification);
        });
    }

    // Keep the original method for backward compatibility
    private static void notifyWarParticipants(WarData war, String message, ChatFormatting color) {
        Component notification = Component.literal(message).withStyle(style -> style.withColor(color));
        notifyWarParticipants(war, notification);
    }

    private static void handleVictoryRewards(WarData war, boolean defendersWon) {
        Map<UUID, Integer> winnerLives = defendersWon ? war.getDefenderLives() : war.getAttackerLives();
        IColony loserColony = defendersWon ? war.getAttackerColony() : war.getColony();

        if (TaxConfig.ENABLE_COLONY_TRANSFER.get() && !defendersWon) { // ATTACKER WINS - Target colony enters occupied
                                                                       // state
            if (TaxConfig.isOccupationSystemEnabled()) {
                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
                        war.getColony(), war.getAttacker(), war.getAttackerColony());
                war.setPenaltyReport("TOTAL VICTORY - Colony is now OCCUPIED! Original owner has " +
                        TaxConfig.getOccupationDurationDays() + " days to reclaim.");
            } else {
                transferOwnership(war.getColony(), war.getAttacker());
                war.setPenaltyReport("TOTAL VICTORY - Colony transferred to attackers!");
            }
        } else if (TaxConfig.isColonyWagerEnabled() && defendersWon && war.getAttackerColony() != null) {
            // DEFENDER WINS with COLONY WAGER enabled - Attacker's wagered colony enters
            // occupied state!
            if (TaxConfig.isOccupationSystemEnabled()) {
                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
                        war.getAttackerColony(), war.getDefender(), war.getColony());
                war.setPenaltyReport("ピ" COUNTER-CONQUEST! The attacker's colony is now OCCUPIED by the defenders! " +
                        "Attacker has " + TaxConfig.getOccupationDurationDays() + " days to reclaim.");

                // Notify both sides about the wager outcome
                if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                    Component wagerLostMsg = Component.empty()
                            .append(Component.literal("ピ" WAGER LOST! ピ"").withStyle(ChatFormatting.DARK_RED,
                                    ChatFormatting.BOLD))
                            .append(Component.literal("\nYou attacked and LOST! Your colony ")
                                    .withStyle(ChatFormatting.RED))
                            .append(Component.literal(war.getAttackerColony().getName()).withStyle(ChatFormatting.GOLD,
                                    ChatFormatting.BOLD))
                            .append(Component.literal(" is now OCCUPIED by the defenders!")
                                    .withStyle(ChatFormatting.RED))
                            .append(Component
                                    .literal("\nYou have " + TaxConfig.getOccupationDurationDays()
                                            + " days to wage a reclamation war.")
                                    .withStyle(ChatFormatting.YELLOW));

                    Component wagerWonMsg = Component.empty()
                            .append(Component.literal("ピ" COUNTER-CONQUEST! ピ"").withStyle(ChatFormatting.GOLD,
                                    ChatFormatting.BOLD))
                            .append(Component.literal(
                                    "\nYou successfully defended your colony and captured the attacker's wagered colony ")
                                    .withStyle(ChatFormatting.GREEN))
                            .append(Component.literal(war.getAttackerColony().getName()).withStyle(ChatFormatting.GOLD,
                                    ChatFormatting.BOLD))
                            .append(Component.literal("!").withStyle(ChatFormatting.GREEN))
                            .append(Component.literal("\nYou can now collect "
                                    + (int) (TaxConfig.getOccupationTaxPercentage() * 100) + "% of their taxes!")
                                    .withStyle(ChatFormatting.YELLOW));

                    // Notify attacker
                    ServerPlayer attackerPlayer = war.getColony().getWorld().getServer().getPlayerList()
                            .getPlayer(war.getAttacker());
                    if (attackerPlayer != null) {
                        attackerPlayer.sendSystemMessage(wagerLostMsg);
                    }

                    // Notify defender
                    ServerPlayer defenderPlayer = war.getColony().getWorld().getServer().getPlayerList()
                            .getPlayer(war.getDefender());
                    if (defenderPlayer != null) {
                        defenderPlayer.sendSystemMessage(wagerWonMsg);
                    }
                }
            } else {
                // No occupation system - direct transfer of attacker's colony to defender
                transferOwnership(war.getAttackerColony(), war.getDefender());
                war.setPenaltyReport("ピ" COUNTER-CONQUEST! Attacker's colony transferred to the defenders!");
            }
        } else {
            if (loserColony == null) {
                war.setPenaltyReport("TOTAL VICTORY - Loser colony not found for economic penalties.");
                return;
            }
            int colonyBalance = TaxManager.getStoredTaxForColony(loserColony);
            double victoryPercentage = TaxConfig.WAR_VICTORY_PERCENTAGE.get();
            double defeatPercentage = TaxConfig.WAR_DEFEAT_PERCENTAGE.get();
            long transferAmount = Math.max(100, (long) (colonyBalance * victoryPercentage));
            TaxManager.deductColonyTax(loserColony, defeatPercentage);

            if (!winnerLives.isEmpty() && war.getColony().getWorld() != null
                    && war.getColony().getWorld().getServer() != null) {
                int sharePerPlayer = winnerLives.size() > 0 ? (int) (transferAmount / winnerLives.size()) : 0; // Avoid
                                                                                                               // division
                                                                                                               // by
                                                                                                               // zero
                for (UUID uuid : winnerLives.keySet()) {
                    ServerPlayer player = war.getColony().getWorld().getServer().getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("You received " + sharePerPlayer + " as war spoils!")
                                .withStyle(ChatFormatting.GOLD));
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java | Select-Object -Skip 130 -First 90' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java | Select-Object -Skip 500 -First 90' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/FirstColonyTracker.java | Select-Object -Skip 115 -First 85' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 359ms:
package net.machiavelli.minecolonytax.permissions;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.FirstColonyTracker;
import net.machiavelli.minecolonytax.TaxConfig;

import java.util.UUID;

/**
 * Central guard for colony ownership-transfer decisions.
 *
 * Every code path that flips a colony's deed to a new player must route through
 * {@link #canTransferOwnership(IColony)}. Primary colonies (a player's first
 * colony, per {@link FirstColonyTracker}) are protected by default and only
 * transferable when {@code EnablePrimaryColonyTransfer} is set to {@code true}
 * in the config.
 *
 * Vassalage is intentionally NOT gated here ƒ?" losing a war can still vassalize
 * a primary colony (the loser pays tribute) without the deed moving. Only
 * permanent ownership changes flow through this guard.
 */
public final class ColonyTierGuard {

    private ColonyTierGuard() {}

    /**
     * Whether the colony's ownership may be transferred to a new player.
     *
     * @param colony the colony in question (may be null ƒ?" returns false)
     * @return true when transfer is permitted; false when the colony is a
     *         primary and {@code EnablePrimaryColonyTransfer} is off
     */
    public static boolean canTransferOwnership(IColony colony) {
        if (colony == null) {
            return false;
        }
        // Permissions.getOwner() can be stale, null, or hold a placeholder UUID
        // from the abandonment system. Use the FCT reverse lookup FIRST ƒ?" it
        // tracks the true first-colony owner regardless of permissions state ƒ?"
        // then fall back to the permissions owner only when FCT has no record.
        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
        if (trackedFirstOwner != null) {
            return TaxConfig.isPrimaryColonyTransferEnabled();
        }
        UUID currentOwner = colony.getPermissions().getOwner();
        if (currentOwner == null) {
            return true;
        }
        if (FirstColonyTracker.isFirstColony(currentOwner, colony.getID())) {
            return TaxConfig.isPrimaryColonyTransferEnabled();
        }
        return true;
    }

    /**
     * Whether a besiege victory may convert into a permanent ownership claim
     * (as opposed to ongoing tax-occupation).
     *
     * Same rules as {@link #canTransferOwnership(IColony)}; named separately so
     * besiege code reads clearly.
     */
    public static boolean canBesiegePermanentClaim(IColony colony) {
        return canTransferOwnership(colony);
    }

    /**
     * Human-readable explanation for why a transfer was denied, suitable for
     * logging or for messaging the player who attempted the action.
     */
    public static String getTransferDenialReason(IColony colony) {
        if (colony == null) {
            return "Colony reference is null.";
        }
        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
        UUID owner = colony.getPermissions().getOwner();
        if (trackedFirstOwner != null
                || (owner != null && FirstColonyTracker.isFirstColony(owner, colony.getID()))) {
            return colony.getName() + " is a Primary colony ƒ?" ownership transfer is blocked by config "
                    + "(EnablePrimaryColonyTransfer = false). Vassalization or tax-occupation may still apply.";
        }
        return "Transfer denied (no specific reason).";
    }

    /**
     * Documented exemptions from the central guard. These are NOT war-time
     * player-to-player transfers ƒ?" they're system-owner placeholder flows:
     *   - {@code ColonyAbandonmentManager} sets a fake owner UUID when a
     *     colony is auto-abandoned (the colony is owner-less in spirit).
     *   - {@code ColonyClaimingRaidManager} flips ownership when a player
     *     successfully claims a previously abandoned colony (the placeholder
     *     UUID isn't a real player, so the FCT primary-protection doesn't apply).
     *   - {@code WntCommands} admin paths that set a system owner.
     * Bypassing the guard in those files is intentional. If you add a NEW
     * code path that flips ownership for a real player-on-player conflict,
     * route it through {@link net.machiavelli.minecolonytax.WarSystem#transferOwnership(IColony, java.util.UUID)}
     * so this guard applies.
     */
    public static void documentedExemptionsBeyondTransferOwnership() {
        // marker method ƒ?" see javadoc
    }
}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 443ms:
        }
    }

    /**
     * Manually end a tax-only occupation early ƒ?" called when the owner successfully
     * mounts a counter-besiege. Restores everything to pre-occupation state.
     *
     * Strict guards:
     *  - Only TAX_ONLY occupations can be reclaimed this way. TRANSFER_PENDING
     *    occupations (secondary colonies) must follow the legacy reclaim flow
     *    or be ended by the standard expiry/cancel paths.
     *  - {@code reclaimerUUID} must match the recorded original owner. Prevents
     *    arbitrary players from cancelling another player's occupation.
     *  - Uses atomic remove so concurrent calls don't double-fire.
     *
     * @return true if an occupation was ended, false otherwise
     */
    public static boolean reclaimByOriginalOwner(int colonyId, UUID reclaimerUUID) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data == null) return false;
        if (data.getMode() != OccupationMode.TAX_ONLY) {
            LOGGER.info("reclaimByOriginalOwner refused: occupation on colony {} is mode {} (not TAX_ONLY)",
                    colonyId, data.getMode());
            return false;
        }
        if (reclaimerUUID == null || !reclaimerUUID.toString().equals(data.originalOwnerUUID)) {
            LOGGER.info("reclaimByOriginalOwner refused: caller {} is not the recorded original owner {} of colony {}",
                    reclaimerUUID, data.originalOwnerUUID, colonyId);
            return false;
        }

        // Atomic remove ƒ?" bail if a concurrent caller already cleared it.
        if (!ACTIVE_OCCUPATIONS.remove(colonyId, data)) return false;
        saveData();

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
            if (originalOwner != null) {
                originalOwner.sendSystemMessage(
                        Component.literal("You have successfully reclaimed " + data.colonyName + "!")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            }
            ServerPlayer occupier = server.getPlayerList().getPlayer(data.getOccupierUUID());
            if (occupier != null) {
                occupier.sendSystemMessage(
                        Component.literal(data.colonyName + " has been reclaimed by its original owner ƒ?" your hold has been broken.")
                                .withStyle(ChatFormatting.RED));
            }
        }
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Occupation reclaimed for colony {} ({}) by original owner", data.colonyName, colonyId);
        }
        return true;
    }

    /** Returns true if the occupier should be blocked from interacting with the occupied colony's buildings and items. */
    public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data == null) return false;

        // Block the occupier from interacting with the occupied colony's items
        return data.occupierUUID.equals(playerUUID.toString());
    }

    public static boolean isOriginalOwner(UUID playerUUID, int colonyId) {
        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
        if (data == null) return false;
        return data.originalOwnerUUID.equals(playerUUID.toString());
    }

    public static void saveData() {
        // Snapshot on the calling (server) thread; the worker only touches
        // a fresh ArrayList so it can't ConcurrentModification on the live map.
        final OccupationSaveData saveData = new OccupationSaveData();
        saveData.occupations = new ArrayList<>(ACTIVE_OCCUPATIONS.values());

        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("occupations", () -> {
            try {
                Path dir = Paths.get("config/warntax");
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
                try (Writer writer = new FileWriter(STORAGE_FILE)) {
                    GSON.toJson(saveData, writer);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to save occupation data: {}", e.getMessage());
            }
        });
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 435ms:
            }
            return newFirstColony;
        }

        return null;
    }

    /**
     * Gets the first (primary) colony ID for a player.
     *
     * @param playerUUID The player's UUID
     * @return The first colony ID, or null if the player has no colonies
     */
    public static Integer getFirstColony(UUID playerUUID) {
        if (playerUUID == null) {
            return null;
        }

        List<Integer> colonies = playerColoniesMap.get(playerUUID);
        if (colonies == null || colonies.isEmpty()) {
            return null;
        }

        return colonies.get(0);
    }

    /**
     * Checks if a colony is a player's first (primary) colony.
     *
     * @param playerUUID The player's UUID
     * @param colonyID   The colony ID to check
     * @return true if this is the player's first colony
     */
    public static boolean isFirstColony(UUID playerUUID, int colonyID) {
        Integer firstColony = getFirstColony(playerUUID);
        return firstColony != null && firstColony == colonyID;
    }

    /**
     * Gets all colonies for a player in creation order.
     *
     * @param playerUUID The player's UUID
     * @return Unmodifiable list of colony IDs in creation order, or empty list if
     *         none
     */
    public static List<Integer> getPlayerColonies(UUID playerUUID) {
        if (playerUUID == null) {
            return Collections.emptyList();
        }

        List<Integer> colonies = playerColoniesMap.get(playerUUID);
        if (colonies == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(new ArrayList<>(colonies));
    }

    /**
     * Reverse lookup: finds which player UUID has the given colony as their first (primary) colony.
     * More reliable than calling getOwner() on a colony whose permissions may be corrupted.
     *
     * @param colonyId The colony ID to look up
     * @return The player UUID who registered this as their first colony, or null if not found
     */
    public static UUID getFirstColonyOwner(int colonyId) {
        for (Map.Entry<UUID, List<Integer>> entry : playerColoniesMap.entrySet()) {
            List<Integer> cols = entry.getValue();
            if (!cols.isEmpty() && cols.get(0).equals(colonyId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Gets the number of colonies a player has.
     *
     * @param playerUUID The player's UUID
     * @return The number of colonies
     */
    public static int getColonyCount(UUID playerUUID) {
        if (playerUUID == null) {
            return 0;
        }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 463ms:
        public List<OccupationData> occupations = new ArrayList<>();
    }

    public static void initialize(MinecraftServer server) {
        serverInstance = server;
        loadData();
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("OccupationManager initialized with {} active occupations", ACTIVE_OCCUPATIONS.size());
        }
    }

    public static void shutdown() {
        saveData();
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("OccupationManager shutdown complete");
        }
    }

    public static void startOccupation(IColony colony, UUID occupierUUID, IColony attackerColony) {
        if (colony == null || occupierUUID == null) {
            LOGGER.warn("startOccupation called with null colony or occupier");
            return;
        }

        int colonyId = colony.getID();

        // Don't double-occupy
        if (ACTIVE_OCCUPATIONS.containsKey(colonyId)) {
            LOGGER.warn("Colony {} is already occupied, cannot start new occupation", colony.getName());
            return;
        }

        UUID originalOwner = colony.getPermissions().getOwner();
        int occupierColonyId = attackerColony != null ? attackerColony.getID() : -1;

        // Decide mode by colony tier. Primary colonies are tax-only by default;
        // secondaries follow the legacy transfer-on-expiry flow.
        // EnablePrimaryColonyTransfer lets server owners opt back into the legacy
        // behavior for primaries too.
        boolean isPrimary = originalOwner != null
                && net.machiavelli.minecolonytax.FirstColonyTracker.isFirstColony(originalOwner, colonyId);
        OccupationMode mode;
        int durationDays;
        if (isPrimary && !TaxConfig.isPrimaryColonyTransferEnabled()) {
            mode = OccupationMode.TAX_ONLY;
            durationDays = TaxConfig.getPrimaryColonyTaxOccupationDays();
        } else {
            mode = OccupationMode.TRANSFER_PENDING;
            durationDays = TaxConfig.getOccupationDurationDays();
        }

        long now = System.currentTimeMillis();
        long expirationTime = now + (durationDays * 24L * 60L * 60L * 1000L);

        OccupationData data = new OccupationData(
                colonyId, occupierUUID, originalOwner,
                occupierColonyId, colony.getName(),
                now, expirationTime, mode
        );
        ACTIVE_OCCUPATIONS.put(colonyId, data);
        saveData();

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Colony {} is now OCCUPIED by {} for {} days [mode={}]",
                    colony.getName(), occupierUUID, durationDays, mode);
        }

        final boolean isTaxOnly = mode == OccupationMode.TAX_ONLY;
        final String expiryConsequence = isTaxOnly
                ? "If not reclaimed in time, the occupation lapses and taxes route back to the owner."
                : "If the original owner does not reclaim within " + durationDays + " days, full ownership transfers to you!";
        final String ownerStakes = isTaxOnly
                ? "This is your Primary colony ƒ?" the deed is safe. Reclaim within " + durationDays
                        + " days or the occupation simply ends."
                : "If you do not reclaim, ownership will permanently transfer!";

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Notify occupier
            ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
            if (occupier != null) {
                Component occupierMsg = Component.literal(isTaxOnly ? "PRIMARY COLONY TAX-OCCUPIED" : "COLONY OCCUPIED")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("\n"))
                        .append(Component.literal("You now occupy " + colony.getName() + "!")
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("\n"))
                        .append(Component.literal("You can collect taxes with /wnt collectoccupation " + colonyId)
                                .withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("\n"))
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"setOwner\\(|getDeclaredMethod\\(\\\"setOwner|Method.*setOwner|setAccessible\\(true\\)\" src/main/java/net/machiavelli/minecolonytax" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "OccupationMode|TAX_ONLY|TRANSFER_PENDING|isPrimary" src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "recordWarLoss|applyWarEconomyTransfers|handleVictoryRewards|checkForVictory|attackersWin|defendersWin" src/main/java/net/machiavelli/minecolonytax/WarSystem.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"setOwner\\(|getDeclaredMethod\\(\\\"setOwner|Method.*setOwner|setAccessible\\(true\\)\" src/main/java/net/machiavelli/minecolonytax"` rejected: blocked by policy
2026-05-25T02:39:21.605453Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"setOwner\\(|getDeclaredMethod\\(\\\"setOwner|Method.*setOwner|setAccessible\\(true\\)\" src/main/java/net/machiavelli/minecolonytax"` rejected: blocked by policy
 succeeded in 330ms:
668:    public static void checkForVictory(WarData war) {
689:        boolean attackersWin = (hasDefenders && allDefendersDead) || (!hasDefenders && allDefenderGuardsDead);
690:        boolean defendersWin = (hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead);
693:        if (!attackersWin && !defendersWin) {
702:                    "[DEBUG] War victory detected - Attackers win: " + attackersWin + ", Defenders win: "
703:                            + defendersWin);
710:        if (defendersWin) {
733:                        .recordWarLoss(war.getAttackerColony().getID());
735:            applyWarEconomyTransfers(war, false);
737:        } else if (attackersWin) {
760:            net.machiavelli.minecolonytax.economy.WarExhaustionManager.recordWarLoss(war.getColony().getID());
762:            applyWarEconomyTransfers(war, true);
859:    private static void applyWarEconomyTransfers(WarData war, boolean attackersWon) {
1601:            handleVictoryRewards(war, true); // true for defender victory
1626:            handleVictoryRewards(war, false); // false for attacker victory
1793:        checkForVictory(war);
1817:    private static void handleVictoryRewards(WarData war, boolean defendersWon) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 340ms:
53:     * Primary colonies always run in TAX_ONLY (deed never moves), unless
54:     * EnablePrimaryColonyTransfer is on. Secondaries run in TRANSFER_PENDING.
56:    public enum OccupationMode {
58:        TRANSFER_PENDING,
60:        TAX_ONLY
74:        public OccupationMode mode;
80:                    startTime, expirationTime, OccupationMode.TRANSFER_PENDING);
85:                              long startTime, long expirationTime, OccupationMode mode) {
98:        /** Returns the mode, defaulting to TRANSFER_PENDING for legacy save files. */
99:        public OccupationMode getMode() {
100:            return mode != null ? mode : OccupationMode.TRANSFER_PENDING;
170:        boolean isPrimary = originalOwner != null
172:        OccupationMode mode;
174:        if (isPrimary && !TaxConfig.isPrimaryColonyTransferEnabled()) {
175:            mode = OccupationMode.TAX_ONLY;
178:            mode = OccupationMode.TRANSFER_PENDING;
198:        final boolean isTaxOnly = mode == OccupationMode.TAX_ONLY;
435:            OccupationMode mode = data.getMode();
437:            if (mode == OccupationMode.TAX_ONLY) {
459:                // Standard TRANSFER_PENDING flow for secondaries. Only broadcast the
509:     *  - Only TAX_ONLY occupations can be reclaimed this way. TRANSFER_PENDING
521:        if (data.getMode() != OccupationMode.TAX_ONLY) {
522:            LOGGER.info("reclaimByOriginalOwner refused: occupation on colony {} is mode {} (not TAX_ONLY)",
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n 'setOwner\\(|getDeclaredMethod|Method.*setOwner|setAccessible\\(true\\)' src/main/java/net/machiavelli/minecolonytax" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-Object -Skip 650 -First 130' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-Object -Skip 850 -First 95' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-Object -Skip 1570 -First 75' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 397ms:
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:786:                java.lang.reflect.Method setOwnerMethod = permissions.getClass().getMethod("setOwner", UUID.class);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:793:                    for (java.lang.reflect.Method method : permissions.getClass().getDeclaredMethods()) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:795:                            method.setAccessible(true);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java:465:            // NOTE: We don't explicitly call setOwner() to avoid API compatibility issues
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java:736:                        java.lang.reflect.Method method = permissions.getClass().getDeclaredMethod("removePlayer", UUID.class);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java:737:                        method.setAccessible(true);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java:787:                        java.lang.reflect.Method setOwnerMethod = permissions.getClass().getMethod("setOwner", UUID.class);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java:794:                            for (java.lang.reflect.Method method : permissions.getClass().getDeclaredMethods()) {
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java:796:                                    method.setAccessible(true);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1228:        if (colony.getPermissions().setOwner(newOwner)) {
src/main/java/net/machiavelli/minecolonytax\event\ColonyOwnershipHandler.java.disabled:128:            firstColony.getPermissions().setOwner(player);
src/main/java/net/machiavelli/minecolonytax\event\ColonyOwnershipHandler.java.disabled:222:            colony.getPermissions().setOwner(player);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3310:                                        .getDeclaredMethod("cleanupAbandonedEntries",
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3312:                        cleanupMethod.setAccessible(true);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3417:                                                                java.lang.reflect.Method setOwnerMethod = permissions
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3418:                                                                                .getClass().getMethod("setOwner",
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3429:                                                                                        .getDeclaredMethods()) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3432:                                                                                        method.setAccessible(true);
src/main/java/net/machiavelli/minecolonytax\event\RecipeDisableClient.java:143:        recipesField.setAccessible(true);
src/main/java/net/machiavelli/minecolonytax\event\RecipeDisableClient.java:144:        byNameField.setAccessible(true);
src/main/java/net/machiavelli/minecolonytax\event\RecipeDisableRuntime.java:160:        recipesField.setAccessible(true);
src/main/java/net/machiavelli/minecolonytax\event\RecipeDisableRuntime.java:161:        byNameField.setAccessible(true);
src/main/java/net/machiavelli/minecolonytax\raid\EntityRaidManager.java:650:            diplomacyManagerField.setAccessible(true);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 543ms:
        int defenderTotalLives = war.getDefenderLives().values().stream().mapToInt(Integer::intValue).sum();
        String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
                : "The Attackers";
        String defenderColonyName = war.getColony().getName();

        MutableComponent timeExpiredMsgBase = Component.translatable("war.time.expired.title")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));

        if (attackerTotalLives == 0 && war.getRemainingAttackerGuards() == 0) {
            MutableComponent defenderVictoryMsg = Component.translatable("war.time.expired.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.defenders.part1").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.defenders.part2").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            broadcastComponent(war, defenderVictoryMsg);
            for (UUID defUUID : war.getDefenderLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defUUID);
                if (p != null)
                    PlayerWarDataManager.incrementWarsWon(p);
            }
            for (UUID atkUUID : war.getAttackerLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(atkUUID);
                if (p != null)
                    PlayerWarDataManager.incrementWarsLost(p);
            }
            handleVictoryRewards(war, true); // true for defender victory
            endWar(war.getColony());
            return;
        } else if (defenderTotalLives == 0 && war.getRemainingDefenderGuards() == 0) {
            MutableComponent attackerVictoryMsg = Component.translatable("war.time.expired.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.attackers.part1").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.attackers.part2").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            broadcastComponent(war, attackerVictoryMsg);
            for (UUID atkUUID : war.getAttackerLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(atkUUID);
                if (p != null)
                    PlayerWarDataManager.incrementWarsWon(p);
            }
            for (UUID defUUID : war.getDefenderLives().keySet()) {
                ServerPlayer p = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defUUID);
                if (p != null)
                    PlayerWarDataManager.incrementWarsLost(p);
            }
            handleVictoryRewards(war, false); // false for attacker victory
            endWar(war.getColony());
            return;
        }

        // Check for stalemate due to no losses on either side by timeout
        if (attackerTotalLives == war.initialAttackerTotalLives && // No player lives lost by attackers
                defenderTotalLives == war.initialDefenderTotalLives && // No player lives lost by defenders
                war.getRemainingAttackerGuards() == war.initialAttackerGuards && // No attacker guards lost
                war.getRemainingDefenderGuards() == war.initialDefenderGuards) { // No defender guards lost

            MutableComponent stalemateNoLossesMsg = Component.translatable("war.time.expired.title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.stalemate.timeout.part1").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(attackerColonyName).withStyle(ChatFormatting.DARK_RED,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.stalemate.timeout.part2").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(defenderColonyName).withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 563ms:

    /**
     * Handles economic transfers after a war is won or lost.
     * Transfers funds based on the configured percentages in TaxConfig.
     * 
     * @param war          The war data containing information about the conflict
     * @param attackersWon True if attackers won, false if defenders won
     */
    private static void applyWarEconomyTransfers(WarData war, boolean attackersWon) {
        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
            return;

        // Get the appropriate percentage based on who won
        double transferPercentage = attackersWon ? TaxConfig.getWarVictoryPercentage()
                : TaxConfig.getWarDefeatPercentage();

        if (transferPercentage <= 0) {
            // No economic penalties configured
            return;
        }

        // Identify winner and loser colonies
        IColony winnerColony = attackersWon ? war.getAttackerColony() : war.getColony();
        IColony loserColony = attackersWon ? war.getColony() : war.getAttackerColony();

        UUID winnerTeamID = attackersWon ? war.getAttackerTeamID() : war.getDefenderTeamID();
        UUID loserTeamID = attackersWon ? war.getDefenderTeamID() : war.getAttackerTeamID();

        long totalTransferred = 0;

        // Check if SDMShop is enabled
        if (TaxConfig.isSDMShopConversionEnabled()) {
            // === SDMShop ENABLED - Use SDMShop balance system ===
            if (winnerTeamID != null && loserTeamID != null) {
                // Team-based battle - Select SINGLE winner to receive ALL rewards

                // Determine winning participants and colony
                Map<UUID, Integer> winningParticipants = attackersWon ? war.getAttackerLives() : war.getDefenderLives();
                IColony winningColony = attackersWon ? war.getAttackerColony() : war.getColony();
                Map<UUID, Integer> losingParticipants = attackersWon ? war.getDefenderLives() : war.getAttackerLives();

                // Select single winner (prioritizes owner > officers > participants)
                UUID singleWinnerUUID = selectSingleWarWinner(winningColony, winningParticipants.keySet());
                ServerPlayer singleWinner = war.getColony().getWorld().getServer().getPlayerList()
                        .getPlayer(singleWinnerUUID);

                // Apply team economic penalties - transfer from ALL losers to SINGLE winner
                long totalCollected = 0;
                List<String> transactionDetails = new ArrayList<>();

                // Collect from all losing participants
                for (UUID loserUUID : losingParticipants.keySet()) {
                    ServerPlayer loser = war.getColony().getWorld().getServer().getPlayerList().getPlayer(loserUUID);
                    if (loser != null) {
                        long loserBalance = net.machiavelli.minecolonytax.integration.SDMShopIntegration
                                .getMoney(loser);
                        long transferAmount = Math.max(1, (long) (loserBalance * transferPercentage));

                        if (transferAmount > 0 && loserBalance >= transferAmount) {
                            net.machiavelli.minecolonytax.integration.SDMShopIntegration.setMoney(loser,
                                    loserBalance - transferAmount);
                            totalCollected += transferAmount;

                            // Notify losing participant
                            loser.sendSystemMessage(Component.literal("ƒs"‹÷? WAR DEFEAT PENALTY ƒs"‹÷?")
                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                    .append(Component.literal("\nYou lost $" + transferAmount + " due to war defeat!")
                                            .withStyle(ChatFormatting.RED)));

                            transactionDetails.add(loser.getName().getString() + " lost $" + transferAmount);
                        }
                    }
                }

                // Award all collected funds to single winner
                if (totalCollected > 0 && singleWinner != null) {
                    long currentBalance = net.machiavelli.minecolonytax.integration.SDMShopIntegration
                            .getMoney(singleWinner);
                    net.machiavelli.minecolonytax.integration.SDMShopIntegration.setMoney(singleWinner,
                            currentBalance + totalCollected);

                    // Notify winner
                    singleWinner.sendSystemMessage(Component.literal("ÐY?Å WAR VICTORY REWARD ÐY?Å")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(Component.literal("\nYou received $" + totalCollected + " as war reparations!")
                                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));

                    // Send transaction summary to all participants only
                    Component transactionSummary = Component.literal("ÐY'ø WAR ECONOMY TRANSACTIONS ÐY'ø")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                            .append(Component.literal("\n" + String.join("\n", transactionDetails))
                                    .withStyle(ChatFormatting.GRAY))
                            .append(Component
                                    .literal("\nTotal awarded to " + singleWinner.getName().getString() + ": $"
                                            + totalCollected)
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 586ms:
        if (player == null || player.getServer() == null)
            return;
        if (TaxConfig.ENABLE_LP_GROUP_SWITCHING.get()) {
            String command = "lp user " + player.getName().getString() + " parent set war";
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }
    }

    public static void resetWarGroup(ServerPlayer player) {
        if (player == null || player.getServer() == null)
            return;
        if (TaxConfig.ENABLE_LP_GROUP_SWITCHING.get()) {
            String command = "lp user " + player.getName().getString() + " parent set default";
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }
    }

    public static void checkForVictory(WarData war) {
        boolean allAttackersDead = war.getAttackerLives().values().stream().allMatch(lives -> lives <= 0);
        boolean allDefendersDead = war.getDefenderLives().values().stream().allMatch(lives -> lives <= 0);
        boolean allDefenderGuardsDead = war.getRemainingDefenderGuards() <= 0;
        boolean allAttackerGuardsDead = war.getRemainingAttackerGuards() <= 0;

        // Check if we have any participants at all
        boolean hasAttackers = !war.getAttackerLives().isEmpty();
        boolean hasDefenders = !war.getDefenderLives().isEmpty();

        // If no participants, don't end the war
        if (!hasAttackers && !hasDefenders) {
            return;
        }

        // Victory conditions:
        // - Attackers win if all defenders are dead (0 lives) OR if all defender guards
        // are dead
        // - Defenders win if all attackers are dead (0 lives) OR if all attacker guards
        // are dead
        // - Priority: Player deaths take precedence over guard deaths for ending wars
        boolean attackersWin = (hasDefenders && allDefendersDead) || (!hasDefenders && allDefenderGuardsDead);
        boolean defendersWin = (hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead);

        // Only proceed if there's a clear victory condition
        if (!attackersWin && !defendersWin) {
            return;
        }

        if (war.getColony().getWorld() == null || war.getColony().getWorld().getServer() == null)
            return;

        if (TaxConfig.isDebugLogging()) {
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
                    "[DEBUG] War victory detected - Attackers win: " + attackersWin + ", Defenders win: "
                            + defendersWin);
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug(
                    "[DEBUG] All attackers dead: " + allAttackersDead + ", All defenders dead: " + allDefendersDead);
            if (TaxConfig.isDebugLogging()) WARSYSTEM_LOGGER.debug("[DEBUG] Attacker guards: " + war.getRemainingAttackerGuards() + ", Defender guards: "
                    + war.getRemainingDefenderGuards());
        }

        if (defendersWin) {
            String defenderColonyName = war.getColony().getName();
            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
                    : "The Attackers";
            Component victoryMsg = Component.empty()
                    .append(Component.translatable("war.defenders.win.title").withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.defenders.win.body", defenderColonyName, attackerColonyName)
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), victoryMsg);
            for (UUID defenderUUID : war.getDefenderLives().keySet()) {
                ServerPlayer defender = war.getColony().getWorld().getServer().getPlayerList().getPlayer(defenderUUID);
                if (defender != null) {
                }
            }
            // Record war loss BEFORE economic transfers so immunity check uses pre-war
            // balance
            if (war.getAttackerColony() != null) {
                net.machiavelli.minecolonytax.economy.WarExhaustionManager
                        .recordWarLoss(war.getAttackerColony().getID());
            }
            applyWarEconomyTransfers(war, false);

        } else if (attackersWin) {
            String defenderColonyName = war.getColony().getName();
            String attackerColonyName = war.getAttackerColony() != null ? war.getAttackerColony().getName()
                    : "The Attackers";
            Component conquestMsg = Component.empty()
                    .append(Component.translatable("war.attackers.win.title").withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.attackers.win.body", attackerColonyName, defenderColonyName)
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("war.time.expired.separator").withStyle(ChatFormatting.DARK_GRAY));
            sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), conquestMsg);
            for (UUID attackerUUID : war.getAttackerLives().keySet()) {
                ServerPlayer attackerPlayer = war.getColony().getWorld().getServer().getPlayerList()
                        .getPlayer(attackerUUID);
                if (attackerPlayer != null) {
                    PlayerWarDataManager.incrementWarsWon(attackerPlayer);
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new WarVictoryEvent(attackerPlayer));
                }
            }
            // Record war loss for defender (they lost when attackers won)
            net.machiavelli.minecolonytax.economy.WarExhaustionManager.recordWarLoss(war.getColony().getID());
            // Apply victory/defeat balance transfers - attackers win, defenders pay
            applyWarEconomyTransfers(war, true);

            if (TaxConfig.ENABLE_COLONY_TRANSFER.get()) {
                if (TaxConfig.isOccupationSystemEnabled()) {
                    // Occupation phase: occupier collects taxes but can't interact, original owner
                    // has time to reclaim
                    net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
                            war.getColony(), war.getAttacker(), war.getAttackerColony());
                    WARSYSTEM_LOGGER.info("Colony {} is now occupied by {} (occupation system active)",
                            war.getColony().getName(), war.getAttacker());
                } else {
                    // Direct transfer (legacy behavior)
                    transferOwnership(war.getColony(), war.getAttacker());
                }
            } else if (TaxConfig.isWarVassalizationEnabled()) {
                // Vassalize the losing colony instead of transferring ownership
                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
                int durationHours = TaxConfig.getWarVassalizationDurationHours();
                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java | Select-Object -Skip 3380 -First 80' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java | Select-Object -Skip 760 -First 65' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java | Select-Object -Skip 760 -First 55' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "SourceCompatibility|JavaVersion|minecraft|forge|1.20.1" build.gradle gradle.properties settings.gradle' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 503ms:
settings.gradle:6:            url = 'https://maven.minecraftforge.net/'
gradle.properties:10:minecraft_version=1.20.1
gradle.properties:14:minecraft_version_range=[1.20.1,1.21)
gradle.properties:16:forge_version=47.3.10
gradle.properties:18:forge_version_range=[47,)
gradle.properties:38:mapping_version=2023.09.03-1.20.1
gradle.properties:66:sdm_shop_version_range=[1.20.1-7.2.0,)
gradle.properties:69:patchouli_version_range=[1.20.1-84.1-FORGE,)
build.gradle:5:    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
build.gradle:6:    id 'org.parchmentmc.librarian.forgegradle' version '1.+'
build.gradle:20:minecraft {
build.gradle:56:    // See https://docs.minecraftforge.net/en/latest/advanced/accesstransformers/ for more information.
build.gradle:71:            property 'forge.logging.markers', 'REGISTRIES'
build.gradle:76:            property 'forge.logging.console.level', 'debug'
build.gradle:89:            property 'forge.enabledGameTestNamespaces', mod_id
build.gradle:99:            property 'forge.enabledGameTestNamespaces', mod_id
build.gradle:110:            property 'forge.enabledGameTestNamespaces', mod_id
build.gradle:135:    maven { url = "https://maven.saps.dev/minecraft" }
build.gradle:155:    runtimeOnly fg.deobf("curse.maven:ftb-library-forge-404465:7296748")
build.gradle:160:    implementation fg.deobf("curse.maven:ftb-teams-forge-404468:5267190")
build.gradle:162:    implementation fg.deobf("vazkii.patchouli:Patchouli:1.20.1-84-FORGE")
build.gradle:168:    minecraft "net.minecraftforge:forge:1.20.1-47.3.10"
build.gradle:180:            minecraft_version: minecraft_version, minecraft_version_range: minecraft_version_range,
build.gradle:181:            forge_version: forge_version, forge_version_range: forge_version_range,
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 723ms:
                } catch (Exception e) {
                    LOGGER.error("Failed to remove system owner from colony {}: {}", colony.getName(), e.getMessage());
                }
            }
            
            // Determine rank and messaging based on whether this is a former owner/officer
            boolean isFormerMember = ColonyAbandonmentManager.wasFormerOwnerOrOfficer(colony.getID(), claimingPlayer.getUUID());
            Rank officerRank = permissions.getRankOfficer();
            
            // STEP 1: Set claimer as OWNER (they've earned it by completing the raid!)
            boolean wasAlreadyInColony = permissions.getPlayers().containsKey(claimingPlayer.getUUID());
            if (wasAlreadyInColony) {
                // Player was already in colony - promote to Owner
                permissions.setPlayerRank(claimingPlayer.getUUID(), permissions.getRankOwner(), colony.getWorld());
                if (TaxConfig.isNormalLogging()) LOGGER.info("CLAIMING SUCCESS: Promoted existing player {} to OWNER of colony {}",
                    claimingPlayer.getName().getString(), colony.getName());
            } else {
                // Player was not in colony - add them as Owner
                permissions.addPlayer(claimingPlayer.getUUID(), claimingPlayer.getName().getString(), permissions.getRankOwner());
                if (TaxConfig.isNormalLogging()) LOGGER.info("CLAIMING SUCCESS: Added new player {} as OWNER of colony {}",
                    claimingPlayer.getName().getString(), colony.getName());
            }
            
            // CRITICAL: Set the claiming player as the actual owner to prevent GUI crashes
            try {
                java.lang.reflect.Method setOwnerMethod = permissions.getClass().getMethod("setOwner", UUID.class);
                setOwnerMethod.invoke(permissions, claimingPlayer.getUUID());
                if (TaxConfig.isNormalLogging()) LOGGER.info("Claiming owner set: {} is now the actual owner of claimed colony {}",
                    claimingPlayer.getName().getString(), colony.getName());
            } catch (Exception e) {
                LOGGER.warn("Could not set claiming player as actual owner directly, trying alternative: {}", e.getMessage());
                try {
                    for (java.lang.reflect.Method method : permissions.getClass().getDeclaredMethods()) {
                        if (method.getName().equals("setOwner") && method.getParameterCount() == 1) {
                            method.setAccessible(true);
                            method.invoke(permissions, claimingPlayer.getUUID());
                            if (TaxConfig.isNormalLogging()) LOGGER.info("Claiming owner set (alt): {} is now the actual owner of claimed colony {}",
                                claimingPlayer.getName().getString(), colony.getName());
                            break;
                        }
                    }
                } catch (Exception e2) {
                    LOGGER.error("Failed to set claiming player as actual owner: {}", e2.getMessage());
                }
            }
            
            // STEP 2: Restore normal permissions for neutral players (they were restricted during abandonment)
            Rank neutralRank = permissions.getRankNeutral();
            
            if (TaxConfig.isDebugLogging()) LOGGER.info("Restoring normal neutral permissions for abandoned colony {}", colony.getName());
            
            // Restore basic interaction permissions for neutral players
            permissions.setPermission(neutralRank, Action.ACCESS_HUTS, true);
            permissions.setPermission(neutralRank, Action.RIGHTCLICK_BLOCK, true);
            permissions.setPermission(neutralRank, Action.OPEN_CONTAINER, true);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 734ms:

        boolean hasRealOfficers = colony.getPermissions().getPlayers().values().stream()
                .filter(player -> !isSystemOwner(player.getID()))
                .anyMatch(player -> player.getRank().isColonyManager());

        if (hasRealOfficers) {
            if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} reactivated - real officers/owners present, removing abandoned status", colony.getName());
            markColonyAsClaimed(colony.getID());
            cleanupSystemOwnerAndSetRealOwner(colony);
            restoreNormalPermissions(colony);
        }
    }
    
    private static void cleanupSystemOwnerAndSetRealOwner(IColony colony) {
        try {
            IPermissions permissions = colony.getPermissions();
            UUID systemOwnerUUID = createSystemOwner();

            if (permissions.getPlayers().containsKey(systemOwnerUUID)) {
                permissions.removePlayer(systemOwnerUUID);
                if (TaxConfig.isDebugLogging()) LOGGER.debug("Removed system owner placeholder from reactivated colony {}", colony.getName());
            }

            for (ColonyPlayer player : permissions.getPlayers().values()) {
                if (!isSystemOwner(player.getID()) && player.getRank().isColonyManager()) {
                    try {
                        java.lang.reflect.Method setOwnerMethod = permissions.getClass().getMethod("setOwner", UUID.class);
                        setOwnerMethod.invoke(permissions, player.getID());
                        if (TaxConfig.isNormalLogging()) LOGGER.info("Set {} as owner of reactivated colony {}", player.getName(), colony.getName());
                        break;
                    } catch (Exception e) {
                        LOGGER.warn("Could not set {} as owner directly, trying via reflection: {}", player.getName(), e.getMessage());
                        try {
                            for (java.lang.reflect.Method method : permissions.getClass().getDeclaredMethods()) {
                                if (method.getName().equals("setOwner") && method.getParameterCount() == 1) {
                                    method.setAccessible(true);
                                    method.invoke(permissions, player.getID());
                                    if (TaxConfig.isNormalLogging()) LOGGER.info("Set {} as owner of reactivated colony {} (via reflection)", player.getName(), colony.getName());
                                    break;
                                }
                            }
                        } catch (Exception e2) {
                            LOGGER.error("Failed to set {} as owner of colony {}: {}", player.getName(), colony.getName(), e2.getMessage());
                        }
                    }
                    break;
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("Error cleaning up system owner for colony {}: {}", colony.getName(), e.getMessage());
        }
    }
    
    /**
     * Returns a deterministic placeholder UUID used as a temporary colony owner
     * for abandoned colonies that have no real players. Consistent across restarts.
     */
    public static UUID createSystemOwner() {
        return UUID.nameUUIDFromBytes("MINECOLONY_TAX_SYSTEM_OWNER".getBytes());
    }

    public static boolean isSystemOwner(UUID uuid) {
        return uuid != null && uuid.equals(createSystemOwner());
    }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 745ms:
                                                issue[0] = "null owner";
                                        } else {
                                                // Check for problematic player entries
                                                for (ColonyPlayer player : permissions.getPlayers().values()) {
                                                        if (player.getName() != null &&
                                                                        (player.getName().contains("[abandoned]") ||
                                                                                        player.getName().toLowerCase()
                                                                                                        .contains("abandoned"))) {
                                                                needsFix = true;
                                                                issue[0] = "has [abandoned] entries";
                                                                break;
                                                        }
                                                }
                                        }

                                        if (needsFix) {
                                                final String colonyName = colony.getName();
                                                final String fixIssue = issue[0];
                                                source.sendSuccess(() -> Component
                                                                .literal("  Fixing " + colonyName + " - " + fixIssue)
                                                                .withStyle(ChatFormatting.AQUA), false);

                                                // Apply emergency fix using our new system
                                                ColonyAbandonmentManager.cleanupAbandonedEntries(permissions);

                                                // If still no valid owner, create system owner
                                                if (permissions.getOwner() == null ||
                                                                ColonyAbandonmentManager.isSystemOwner(
                                                                                permissions.getOwner())) {

                                                        UUID systemOwner = ColonyAbandonmentManager.createSystemOwner();
                                                        permissions.addPlayer(systemOwner, "[SYSTEM_ABANDONED]",
                                                                        permissions.getRankOwner());

                                                        // CRITICAL FIX: Set actual owner to prevent GUI crashes
                                                        try {
                                                                java.lang.reflect.Method setOwnerMethod = permissions
                                                                                .getClass().getMethod("setOwner",
                                                                                                UUID.class);
                                                                setOwnerMethod.invoke(permissions, systemOwner);
                                                                source.sendSuccess(() -> Component
                                                                                .literal("    Set system owner as actual owner to prevent GUI crashes")
                                                                                .withStyle(ChatFormatting.GREEN),
                                                                                false);
                                                        } catch (Exception e) {
                                                                try {
                                                                        for (java.lang.reflect.Method method : permissions
                                                                                        .getClass()
                                                                                        .getDeclaredMethods()) {
                                                                                if (method.getName().equals("setOwner")
                                                                                                && method.getParameterCount() == 1) {
                                                                                        method.setAccessible(true);
                                                                                        method.invoke(permissions,
                                                                                                        systemOwner);
                                                                                        source.sendSuccess(
                                                                                                        () -> Component
                                                                                                                        .literal("    Set system owner (alt method) to prevent GUI crashes")
                                                                                                                        .withStyle(ChatFormatting.GREEN),
                                                                                                        false);
                                                                                        break;
                                                                                }
                                                                        }
                                                                } catch (Exception e2) {
                                                                        source.sendFailure(Component
                                                                                        .literal("    WARNING: Could not set actual owner - GUI may crash!")
                                                                                        .withStyle(ChatFormatting.RED));
                                                                }
                                                        }

                                                        // Set all real players to neutral with no permissions
                                                        Rank neutralRank = permissions.getRankNeutral();
                                                        for (UUID playerId : permissions.getPlayers().keySet()) {
                                                                if (!ColonyAbandonmentManager.isSystemOwner(playerId)) {
                                                                        permissions.setPlayerRank(playerId, neutralRank,
                                                                                        colony.getWorld());
                                                                }
                                                        }

                                                        // Disable all griefing permissions for neutral players
                                                        permissions.setPermission(neutralRank,
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"markReclamationAttempted|isOriginalOwner|shouldBlockInteraction|collectOccupationTax|startOccupation|checkExpiredOccupations|endOccupation\\(\" src/main/java/net/machiavelli/minecolonytax" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "reclaim|reclamation|OccupationManager" src/main/java/net/machiavelli/minecolonytax/commands src/main/java/net/machiavelli/minecolonytax/WarSystem.java src/main/java/net/machiavelli/minecolonytax/besiege' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 exited 124 in 10166ms:
2026-05-25T02:40:40.353119Z ERROR codex_core::tools::router: error=Exit code: 124
Wall time: 10.2 seconds
Output:
command timed out after 10166 milliseconds

 exited 124 in 10187ms:
2026-05-25T02:40:40.363692Z ERROR codex_core::tools::router: error=Exit code: 124
Wall time: 10.2 seconds
Output:
command timed out after 10187 milliseconds

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "reclaim|reclamation" src/main/java/net/machiavelli/minecolonytax/commands src/main/java/net/machiavelli/minecolonytax/WarSystem.java src/main/java/net/machiavelli/minecolonytax/besiege' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"markReclamationAttempted|isOriginalOwner|shouldBlockInteraction|collectOccupationTax|checkExpiredOccupations|endOccupation\\(\" src/main/java/net/machiavelli/minecolonytax" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 353ms:
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:767:                    // has time to reclaim
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:1827:                        TaxConfig.getOccupationDurationDays() + " days to reclaim.");
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:1839:                        "Attacker has " + TaxConfig.getOccupationDurationDays() + " days to reclaim.");
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:1854:                                            + " days to wage a reclamation war.")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:2590:            // RECLAMATION WAR EXCEPTION: Relax requirements when fighting to reclaim an
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:2617:                                "Reclamation war (A): {} using colony {} to reclaim their occupied colony {}",
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:2623:            // Check building/guard requirements (skip for reclamation wars - desperation
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:2637:                // Check treasury (skip for reclamation wars - fighting for freedom!)
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:2662:        // Check if this is a reclamation war (two scenarios):
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:2673:                        "\u2694 RECLAMATION WAR! You are fighting to reclaim your occupied colony!")
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:2694:                        WARSYSTEM_LOGGER.info("Reclamation war (B) initiated by {} - attacking occupier's colony {}, reclaiming {}",
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:51: * that grant tax vassalage on victory, and the reclaim flow for the former owner.
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:199:        // OccupationManager in TAX_ONLY mode (deed never moves; auto-reclaim after
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:253:     * Start a reclaim raid â€” former owner/officer taking back their besieged colony.
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:255:    public static boolean startReclaim(IColony colony, ServerPlayer reclaimingPlayer) {
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:257:            reclaimingPlayer.sendSystemMessage(Component.literal("The besiege system is disabled.")
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:265:            reclaimingPlayer.sendSystemMessage(Component.literal("This colony is not under besiege occupation.")
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:270:        UUID playerUUID = reclaimingPlayer.getUUID();
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:272:            reclaimingPlayer.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:273:                    "Only the former owner can reclaim this colony via besiege.")
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:280:            reclaimingPlayer.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:281:                    "You already have an active besiege/reclaim raid.")
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:290:            reclaimingPlayer.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:291:                    "You must wait " + remaining + " more minute(s) before attempting a reclaim.")
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:296:        reclaimingPlayer.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:297:                "Reclaim raid started! Kill all defenders to reclaim " + colony.getName() + ".")
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:299:        return launchRaid(colony, reclaimingPlayer, true);
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:345:                            + " is under " + (isReclaim ? "reclaim attack" : "siege")
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:375:                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:686:                                + "Use /wnt besiege " + colony.getName() + " to reclaim it.")
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:725:                Component.literal(colony.getName() + " has been reclaimed by its owner!")
src/main/java/net/machiavelli/minecolonytax/besiege\BesiegeManager.java:729:            LOGGER.info("Besiege reclaim: colony {} reclaimed by {}", colony.getName(),
src/main/java/net/machiavelli/minecolonytax/commands\WntCommands.java:1395:                        // If the colony is already besieged AND the player is the former owner -> reclaim
src/main/java/net/machiavelli/minecolonytax/commands\WntCommands.java:1403:                                                "This colony is already under besiege occupation. Only the former owner can reclaim it.")
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 364ms:
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:887:    public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
src/main/java/net/machiavelli/minecolonytax\event\BlockInteractionFilterHandler.java:136:                && net.machiavelli.minecolonytax.occupation.OccupationManager.shouldBlockInteraction(
src/main/java/net/machiavelli/minecolonytax\event\BlockInteractionFilterHandler.java:145:                && net.machiavelli.minecolonytax.besiege.BesiegeManager.shouldBlockInteraction(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2671:                        .markReclamationAttempted(targetColony.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2690:                                .markReclamationAttempted(ac.getID());
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:275:                net.machiavelli.minecolonytax.occupation.OccupationManager.checkExpiredOccupations();
src/main/java/net/machiavelli/minecolonytax\permissions\TaxPermissionManager.java:26:                && net.machiavelli.minecolonytax.besiege.BesiegeManager.shouldBlockInteraction(playerId, colonyId)) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:281:    public static int collectOccupationTax(int colonyId, ServerPlayer occupier) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:365:    public static void markReclamationAttempted(int colonyId) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:376:    public static void endOccupation(int colonyId, String reason) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:404:    public static void checkExpiredOccupations() {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:558:    public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:566:    public static boolean isOriginalOwner(UUID playerUUID, int colonyId) {
src/main/java/net/machiavelli/minecolonytax\event\RaidLoginNotifier.java:146:                    boolean isOriginalOwner = occ.getOriginalOwnerUUID().equals(playerUUID);
src/main/java/net/machiavelli/minecolonytax\event\RaidLoginNotifier.java:148:                    if (!isOriginalOwner && !isOccupier) continue;
src/main/java/net/machiavelli/minecolonytax\event\RaidLoginNotifier.java:151:                    if (isOriginalOwner) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-Object -Skip 2540 -First 180' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java | Select-Object -Skip 1375 -First 55' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 527ms:

                        if (!TaxConfig.isBesiegeSystemEnabled()) {
                                player.sendSystemMessage(Component.literal("The besiege system is disabled on this server.")
                                                .withStyle(ChatFormatting.RED));
                                return 0;
                        }

                        // Resolve colony by name
                        IColony target = IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
                                        .filter(c -> c.getName().equalsIgnoreCase(colonyArg))
                                        .findFirst()
                                        .orElse(null);

                        if (target == null) {
                                player.sendSystemMessage(Component.literal("Colony not found: " + colonyArg)
                                                .withStyle(ChatFormatting.RED));
                                return 0;
                        }

                        // If the colony is already besieged AND the player is the former owner -> reclaim
                        if (BesiegeManager.isColonyBesieged(target.getID())) {
                                BesiegeManager.BesiegeOccupationData occ = BesiegeManager.getOccupation(target.getID());
                                if (occ != null && player.getUUID().equals(occ.formerOwnerUUID)) {
                                        boolean started = BesiegeManager.startReclaim(target, player);
                                        return started ? 1 : 0;
                                }
                                player.sendSystemMessage(Component.literal(
                                                "This colony is already under besiege occupation. Only the former owner can reclaim it.")
                                                .withStyle(ChatFormatting.RED));
                                return 0;
                        }

                        boolean started = BesiegeManager.startBesiege(target, player);
                        return started ? 1 : 0;

                } catch (CommandSyntaxException e) {
                        ctx.getSource().sendFailure(Component.literal("You must be a player to use this command."));
                        return 0;
                } catch (Exception e) {
                        LOGGER.error("Error handling besiege command", e);
                        return 0;
                }
        }

        private static int handleBesiegeStatus(CommandContext<CommandSourceStack> ctx) {
                CommandSourceStack src = ctx.getSource();
                if (src.hasPermission(2)) {
                        return showBesiegeStatusAdmin(src);
                }
                try {
                        return showBesiegeStatusPlayer(src, src.getPlayerOrException());
                } catch (CommandSyntaxException e) {
                        src.sendFailure(Component.literal("You must be a player to use this command."));
                        return 0;
                }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 548ms:
                    p.sendSystemMessage(message);
            }
        });
    }

    public static void sendMessageToTeam(Team team, Component msg) {
        if (team == null || ServerLifecycleHooks.getCurrentServer() == null)
            return;
        for (UUID member : team.getMembers()) {
            ServerPlayer sp = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(member);
            if (sp != null)
                sp.sendSystemMessage(msg);
        }
    }

    /**
     * Finds a valid colony owned by the player that meets the requirements to
     * declare war on the target.
     * Checks building requirements, guard counts, and treasury status.
     * 
     * SPECIAL CASE: Occupied colonies can be used for RECLAMATION wars against the
     * occupier's colony.
     * This allows players whose only colony is occupied to still fight back!
     * 
     * @param player        The player attempting to declare war
     * @param targetColony  The target colony
     * @param checkTreasury Whether to check treasury requirements (usually true)
     * @return The first valid IColony found, or null if none meet requirements
     */
    public static IColony findValidAttackerColony(ServerPlayer player, IColony targetColony, boolean checkTreasury) {
        if (player == null || targetColony == null)
            return null;

        UUID playerUUID = player.getUUID();
        int primaryColonyId = FirstColonyTracker.getFirstColony(playerUUID);
        List<IColony> playerColonies = IColonyManager.getInstance().getColonies(player.level()).stream()
                .filter(c -> c.getPermissions().getOwner().equals(playerUUID))
                .sorted((a, b) -> {
                    if (a.getID() == primaryColonyId) return -1;
                    if (b.getID() == primaryColonyId) return 1;
                    return 0;
                })
                .collect(java.util.stream.Collectors.toList());

        for (IColony potentialAttacker : playerColonies) {
            // Cannot attack yourself
            if (potentialAttacker.getID() == targetColony.getID())
                continue;

            // RECLAMATION WAR EXCEPTION: Relax requirements when fighting to reclaim an
            // occupied colony.
            // Two scenarios are covered:
            // A) Player targets their OWN occupied colony - any attacker colony gets relaxed reqs
            // B) Player targets the OCCUPIER's colony using their occupied colony as attacker
            boolean isReclamationWar = false;
            if (TaxConfig.isOccupationSystemEnabled()) {
                // Scenario B: This attacker colony is occupied - check if targeting the occupier
                net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData attackerOccData = 
                    net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(potentialAttacker.getID());
                if (attackerOccData != null && attackerOccData.getOriginalOwnerUUID().equals(player.getUUID())) {
                    UUID occupierUUID = attackerOccData.getOccupierUUID();
                    if (targetColony.getPermissions().getOwner().equals(occupierUUID)) {
                        isReclamationWar = true;
                        WARSYSTEM_LOGGER.info(
                                "Reclamation war (B): {} using occupied colony {} to attack occupier's colony {}",
                                player.getName().getString(), potentialAttacker.getName(), targetColony.getName());
                    }
                }

                // Scenario A: The TARGET colony is the player's own occupied colony
                if (!isReclamationWar) {
                    net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData targetOccData = 
                        net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(targetColony.getID());
                    if (targetOccData != null && targetOccData.getOriginalOwnerUUID().equals(player.getUUID())) {
                        isReclamationWar = true;
                        WARSYSTEM_LOGGER.info(
                                "Reclamation war (A): {} using colony {} to reclaim their occupied colony {}",
                                player.getName().getString(), potentialAttacker.getName(), targetColony.getName());
                    }
                }
            }

            // Check building/guard requirements (skip for reclamation wars - desperation
            // allows it!)
            if (!isReclamationWar) {
                if (TaxConfig.isWarBuildingRequirementsEnabled()) {
                    net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult reqs = net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager
                            .checkWarRequirements(potentialAttacker);
                    if (!reqs.meetsRequirements)
                        continue;
                } else {
                    int guardCount = countGuards(potentialAttacker);
                    if (guardCount < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get())
                        continue;
                }

                // Check treasury (skip for reclamation wars - fighting for freedom!)
                if (checkTreasury) {
                    if (!net.machiavelli.minecolonytax.economy.TreasuryManager.canDeclareWar(potentialAttacker.getID(),
                            targetColony.getID())) {
                        continue;
                    }
                }
            }

            return potentialAttacker;
        }

        return null;
    }

    public static int processWageWarRequest(ServerPlayer attacker, IColony targetColony, CommandSourceStack source) {
        Level level = source.getLevel();

        int targetGuards = countGuards(targetColony);
        if (targetGuards < TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get()) {
            source.sendFailure(Component.literal("Target colony must have at least "
                    + TaxConfig.MIN_GUARDS_TO_WAGE_WAR.get() + " guards! (Found: " + targetGuards + ")"));
            return 0;
        }

        // Check if this is a reclamation war (two scenarios):
        // A) Player targets their OWN occupied colony directly
        // B) Player targets the OCCUPIER's colony while their own colony is occupied
        if (TaxConfig.isOccupationSystemEnabled()) {
            // Scenario A: Target IS the player's occupied colony
            net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData targetOccData =
                    net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(targetColony.getID());
            if (targetOccData != null && targetOccData.getOriginalOwnerUUID().equals(attacker.getUUID())) {
                net.machiavelli.minecolonytax.occupation.OccupationManager
                        .markReclamationAttempted(targetColony.getID());
                attacker.sendSystemMessage(Component.literal(
                        "\u2694 RECLAMATION WAR! You are fighting to reclaim your occupied colony!")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                WARSYSTEM_LOGGER.info("Reclamation war (A) initiated by {} for occupied colony {}",
                        attacker.getName().getString(), targetColony.getName());
            } else {
                // Scenario B: Player's colony is occupied and they're attacking the occupier
                // Find if any of the attacker's colonies are occupied by the target colony's owner
                UUID targetOwner = targetColony.getPermissions().getOwner();
                List<IColony> attackerColonies = IColonyManager.getInstance().getColonies(source.getLevel()).stream()
                        .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
                        .toList();
                for (IColony ac : attackerColonies) {
                    net.machiavelli.minecolonytax.occupation.OccupationManager.OccupationData acOccData =
                            net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(ac.getID());
                    if (acOccData != null && acOccData.getOriginalOwnerUUID().equals(attacker.getUUID())
                            && acOccData.getOccupierUUID().equals(targetOwner)) {
                        net.machiavelli.minecolonytax.occupation.OccupationManager
                                .markReclamationAttempted(ac.getID());
                        attacker.sendSystemMessage(Component.literal(
                                "\u2694 RECLAMATION WAR! You are attacking the occupier of your colony " + ac.getName() + "!")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                        WARSYSTEM_LOGGER.info("Reclamation war (B) initiated by {} - attacking occupier's colony {}, reclaiming {}",
                                attacker.getName().getString(), targetColony.getName(), ac.getName());
                        break;
                    }
                }
            }
        }

        // Find a valid attacker colony using the new helper
        IColony attackerColony = findValidAttackerColony(attacker, targetColony, true);

        if (attackerColony == null) {
            // Retained specific error messaging logic for better user feedback if they have
            // at least one colony
            IColony anyColony = IColonyManager.getInstance().getColonies(level).stream()
                    .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
                    .findFirst().orElse(null);

            if (anyColony == null) {
                source.sendFailure(Component.literal("You must own a colony to declare war."));
            } else {
                // If they have colonies but none were valid, give a generic failure or try to
                // diagnose the first one
                if (TaxConfig.isWarBuildingRequirementsEnabled()) {
                    net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager.RequirementResult reqs = net.machiavelli.minecolonytax.requirements.BuildingRequirementsManager
                            .checkWarRequirements(anyColony);
                    source.sendFailure(Component.literal("None of your colonies meet the war requirements. Example ("
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java | Select-Object -Skip 40 -First 75' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "class OccupationData|originalOwnerUUID|getOriginalOwnerUUID|getOccupierUUID|getMode" src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 464ms:
63:    public static class OccupationData {
66:        public final String originalOwnerUUID;
73:        /** Null on save files written before the Siege SMP upgrade â€” see {@link #getMode()}. */
76:        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
79:            this(colonyId, occupierUUID, originalOwnerUUID, occupierColonyId, colonyName,
83:        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
88:            this.originalOwnerUUID = originalOwnerUUID.toString();
99:        public OccupationMode getMode() {
121:        public UUID getOccupierUUID() {
125:        public UUID getOriginalOwnerUUID() {
126:            return UUID.fromString(originalOwnerUUID);
388:            ServerPlayer occupier = server.getPlayerList().getPlayer(data.getOccupierUUID());
395:            ServerPlayer owner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
434:            UUID occupierUUID = data.getOccupierUUID();
435:            OccupationMode mode = data.getMode();
444:                ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
474:                    ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
486:                    ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
521:        if (data.getMode() != OccupationMode.TAX_ONLY) {
523:                    colonyId, data.getMode());
526:        if (reclaimerUUID == null || !reclaimerUUID.toString().equals(data.originalOwnerUUID)) {
528:                    reclaimerUUID, data.originalOwnerUUID, colonyId);
538:            ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
544:            ServerPlayer occupier = server.getPlayerList().getPlayer(data.getOccupierUUID());
569:        return data.originalOwnerUUID.equals(playerUUID.toString());
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 644ms:

    private static final Logger LOGGER = LogManager.getLogger(OccupationManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/occupations.json";

    private static final Map<Integer, OccupationData> ACTIVE_OCCUPATIONS = new ConcurrentHashMap<>();

    private static MinecraftServer serverInstance;

    /**
     * How an occupation resolves when its timer expires.
     *
     * Primary colonies always run in TAX_ONLY (deed never moves), unless
     * EnablePrimaryColonyTransfer is on. Secondaries run in TRANSFER_PENDING.
     */
    public enum OccupationMode {
        /** Expiry transfers full ownership to the occupier. Default for secondaries. */
        TRANSFER_PENDING,
        /** Expiry auto-reclaims ƒ?" taxes route back to original owner, deed never moves. Primary colonies. */
        TAX_ONLY
    }

    public static class OccupationData {
        public final int colonyId;
        public final String occupierUUID;
        public final String originalOwnerUUID;
        public final int occupierColonyId;
        public final long startTime;
        public final long expirationTime;
        public final String colonyName;
        public boolean reclamationAttempted;
        public long lastTaxCollectionTime;
        /** Null on save files written before the Siege SMP upgrade ƒ?" see {@link #getMode()}. */
        public OccupationMode mode;

        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
                              int occupierColonyId, String colonyName,
                              long startTime, long expirationTime) {
            this(colonyId, occupierUUID, originalOwnerUUID, occupierColonyId, colonyName,
                    startTime, expirationTime, OccupationMode.TRANSFER_PENDING);
        }

        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
                              int occupierColonyId, String colonyName,
                              long startTime, long expirationTime, OccupationMode mode) {
            this.colonyId = colonyId;
            this.occupierUUID = occupierUUID.toString();
            this.originalOwnerUUID = originalOwnerUUID.toString();
            this.occupierColonyId = occupierColonyId;
            this.colonyName = colonyName;
            this.startTime = startTime;
            this.expirationTime = expirationTime;
            this.reclamationAttempted = false;
            this.lastTaxCollectionTime = 0;
            this.mode = mode;
        }

        /** Returns the mode, defaulting to TRANSFER_PENDING for legacy save files. */
        public OccupationMode getMode() {
            return mode != null ? mode : OccupationMode.TRANSFER_PENDING;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }

        public long getRemainingTimeMs() {
            return Math.max(0, expirationTime - System.currentTimeMillis());
        }

        public int getRemainingDays() {
            long remainingMs = getRemainingTimeMs();
            return (int) (remainingMs / (24L * 60L * 60L * 1000L));
        }

Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff --check' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 696ms:
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java', LF will be replaced by CRLF the next time Git touches it

codex
STATUS: REWORK

1. VERIFIED — `ColonyTierGuard.canTransferOwnership` now checks `FirstColonyTracker.getFirstColonyOwner(colonyId)` before trusting `permissions.getOwner()`.

2. VERIFIED — the exemption marker/Javadoc exists and documents abandonment, claiming, and admin system-owner bypasses as intentional.

3. VERIFIED — `reclaimByOriginalOwner` is now TAX_ONLY-only, caller UUID matched, and uses `ConcurrentHashMap.remove(key, value)`.

4. VERIFIED — damage shield no longer globally returns when source is a besieger; it only skips that source’s own raid and continues checking others.

5. VERIFIED — timeout path now routes through `completeBesiege(raid, false, colony)`, so cleanup/cooldown/spoils use the shared path.

6. VERIFIED — `applySiegeSpoils` computes winner headroom, caps `actualSpoil`, and deducts only that amount.

7. VERIFIED — ledger capture/restore uses `saveWithFullMetadata` and `BlockEntity.loadStatic`.

8. VERIFIED — `WarSystem.endWar` calls `WarBlockLedger.restoreWarDamage(warId, world)` before active-war removal.

9. PARTIAL — `triggerVictory` checks attackers exhausted before zeroing defenders, but its condition requires attacker lives AND guards at zero; `WarSystem.checkForVictory` grants defender victory when attacker lives alone are zero, so the race still exists.

10. VERIFIED — Town Hall explosion attribution prefers `getIndirectSourceEntity()` when it is a `ServerPlayer`, then falls back to direct source.

New findings:
- HIGH: `TownHallDemolitionObjective.triggerVictory` must mirror `WarSystem.checkForVictory` defender-win logic before mutation; current guard misses “all attacker lives dead, guards remain”.
- MEDIUM: `OccupationManager.startOccupation` still classifies primaries using `permissions.getOwner()` plus `isFirstColony`, so the stale/null/placeholder-owner problem fixed in `ColonyTierGuard` can still misclassify occupation mode.
- MEDIUM: `OccupationManager.reclaimByOriginalOwner` is hardened but appears unused by current reclaim-war flow, so the real reclaim completion path may not benefit from the new guard.

Close: Most rework deltas are valid and directly address the reviewed bugs, but #9 remains incomplete against the actual victory resolver semantics. I would not approve this bundle until the Town Hall guard checks the same defender-win condition that `checkForVictory` will evaluate after defender lives are zeroed.
tokens used
125.876
STATUS: REWORK

1. VERIFIED — `ColonyTierGuard.canTransferOwnership` now checks `FirstColonyTracker.getFirstColonyOwner(colonyId)` before trusting `permissions.getOwner()`.

2. VERIFIED — the exemption marker/Javadoc exists and documents abandonment, claiming, and admin system-owner bypasses as intentional.

3. VERIFIED — `reclaimByOriginalOwner` is now TAX_ONLY-only, caller UUID matched, and uses `ConcurrentHashMap.remove(key, value)`.

4. VERIFIED — damage shield no longer globally returns when source is a besieger; it only skips that source’s own raid and continues checking others.

5. VERIFIED — timeout path now routes through `completeBesiege(raid, false, colony)`, so cleanup/cooldown/spoils use the shared path.

6. VERIFIED — `applySiegeSpoils` computes winner headroom, caps `actualSpoil`, and deducts only that amount.

7. VERIFIED — ledger capture/restore uses `saveWithFullMetadata` and `BlockEntity.loadStatic`.

8. VERIFIED — `WarSystem.endWar` calls `WarBlockLedger.restoreWarDamage(warId, world)` before active-war removal.

9. PARTIAL — `triggerVictory` checks attackers exhausted before zeroing defenders, but its condition requires attacker lives AND guards at zero; `WarSystem.checkForVictory` grants defender victory when attacker lives alone are zero, so the race still exists.

10. VERIFIED — Town Hall explosion attribution prefers `getIndirectSourceEntity()` when it is a `ServerPlayer`, then falls back to direct source.

New findings:
- HIGH: `TownHallDemolitionObjective.triggerVictory` must mirror `WarSystem.checkForVictory` defender-win logic before mutation; current guard misses “all attacker lives dead, guards remain”.
- MEDIUM: `OccupationManager.startOccupation` still classifies primaries using `permissions.getOwner()` plus `isFirstColony`, so the stale/null/placeholder-owner problem fixed in `ColonyTierGuard` can still misclassify occupation mode.
- MEDIUM: `OccupationManager.reclaimByOriginalOwner` is hardened but appears unused by current reclaim-war flow, so the real reclaim completion path may not benefit from the new guard.

Close: Most rework deltas are valid and directly address the reviewed bugs, but #9 remains incomplete against the actual victory resolver semantics. I would not approve this bundle until the Town Hall guard checks the same defender-win condition that `checkForVictory` will evaluate after defender lives are zeroed.
