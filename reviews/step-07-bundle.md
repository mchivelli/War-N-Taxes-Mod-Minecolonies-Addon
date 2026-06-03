## STEP 7 — Siege spoils (one-shot percentage transfer on besiege resolution)

Config: BesiegeSpoilPercentOfLoserTreasury (default 25). On attacker victory: percent transferred from defender colony treasury to besieger's primary colony treasury. On defender victory: same direction reversed. Wired into completeBesiege before completeReclaim/completeBesiegeVictory split.

```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java b/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
index 69fc131..d6b0bdd 100644
--- a/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
+++ b/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
@@ -75,6 +75,7 @@ public class TaxConfig {
         // Colony Tier Protection (Siege SMP ruleset)
         public static final ForgeConfigSpec.BooleanValue ENABLE_PRIMARY_COLONY_TRANSFER;
         public static final ForgeConfigSpec.IntValue PRIMARY_COLONY_TAX_OCCUPATION_DAYS;
+        public static final ForgeConfigSpec.IntValue BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY;
         public static final ForgeConfigSpec.IntValue JOIN_PHASE_DURATION_MINUTES;
         public static final ForgeConfigSpec.BooleanValue WAR_ACCEPTANCE_REQUIRED;
         public static final ForgeConfigSpec.BooleanValue KEEP_INVENTORY_ON_LAST_LIFE;
@@ -633,6 +634,18 @@ public class TaxConfig {
                                                 "OccupationDurationDays config and DO transfer on expiry.")
                                 .defineInRange("PrimaryColonyTaxOccupationDays", 7, 1, 90);
 
+                BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY = BUILDER.comment(
+                                "One-shot percentage of the loser's treasury transferred to the winner on besiege resolution.\n"
+                                                +
+                                                "Applied IN ADDITION to ongoing tax-occupation tribute. On attacker victory: extracted\n"
+                                                +
+                                                "from the besieged colony's treasury into the besieger's primary colony treasury.\n"
+                                                +
+                                                "On defender victory: extracted from the besieger's primary colony treasury into the\n"
+                                                +
+                                                "defending colony's treasury. 0 disables siege spoils entirely.")
+                                .defineInRange("BesiegeSpoilPercentOfLoserTreasury", 25, 0, 100);
+
                 // ========== Colony Occupation Settings ==========
                 BUILDER.push("Colony Occupation");
 
@@ -2630,6 +2643,10 @@ public class TaxConfig {
                 return PRIMARY_COLONY_TAX_OCCUPATION_DAYS.get();
         }
 
+        public static int getBesiegeSpoilPercentOfLoserTreasury() {
+                return BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY.get();
+        }
+
         public static int getWarVassalizationDurationHours() {
                 return WAR_VASSALIZATION_DURATION_HOURS.get();
         }
diff --git a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
index 959ac36..48f1307 100644
--- a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
@@ -546,18 +546,60 @@ public class BesiegeManager {
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
```
