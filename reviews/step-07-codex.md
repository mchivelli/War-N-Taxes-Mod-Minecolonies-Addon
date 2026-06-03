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
session id: 019e5ce7-66e2-73c0-9e0f-319433959eb8
--------
user
Reviewing step 7 of 11. New applySiegeSpoils method does one-shot percentage transfer between treasuries. Bundle includes earlier diffs — focus on step 7 (applySiegeSpoils + config). Review for: integer rounding edge cases, what happens if besieger has no primary colony, double-counting risk vs existing vassalization tribute, correctness of attacker/defender colony selection, notification correctness. STATUS + up to 4 bullets + up to 2 fixes. Max 250 words.

<stdin>
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
</stdin>
codex
I’ll check whether the GitNexus tools are available for this review context, then I’ll keep the findings scoped to the step 7 diff.
codex
GitNexus tools aren’t exposed in this session, so I’m using the local code as the next-best source. I’m checking the nearby besiege flow and treasury APIs to avoid reviewing the diff in isolation.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "getPrimaryColonyOfPlayer|primary colony|PrimaryColony" src/main/java/net/machiavelli/minecolonytax -S' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "completeBesiege|completeBesiegeVictory|completeReclaim|applySiege|BesiegeTribute|Vassal|tribute|TreasuryManager|getPrimaryColonyOfPlayer" src/main/java/net/machiavelli/minecolonytax -S' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "class TreasuryManager|deductFromTreasury|addToTreasury|getTreasuryBalance" src/main/java/net/machiavelli/minecolonytax/economy -S' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 429ms:
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:38:public class TreasuryManager {
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:89:    public static int getTreasuryBalance(int colonyId) {
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:147:        int currentBalance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:213:        int currentBalance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:263:        int balance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:319:        int attackerBalance = getTreasuryBalance(attackerColonyId);
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:358:        int attackerBalance = getTreasuryBalance(attackerColonyId);
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:412:        int currentBalance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:433:    public static int deductFromTreasury(int colonyId, int amount) {
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:434:        int currentBalance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:449:    public static int addToTreasury(int colonyId, int amount) {
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:450:        int currentBalance = getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:464:        return TaxConfig.isTreasuryEnabled() && getTreasuryBalance(colonyId) <= 0;
src/main/java/net/machiavelli/minecolonytax/economy\TreasuryManager.java:476:        int currentBalance = getTreasuryBalance(colonyId);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 437ms:
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:19:import net.machiavelli.minecolonytax.vassalization.VassalManager;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:151:                    completeBesiege(raid, true, colony);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:181:        IColony besiegerColony = getPrimaryColonyOfPlayer(besiegerUUID);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:239:        if (VassalManager.isColonyVassal(colonyId)
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:240:                && VassalManager.getVassalOverlordUUID(colonyId) != null
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:241:                && VassalManager.getVassalOverlordUUID(colonyId).equals(besiegerUUID)) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:444:        // lack the ATTACK_DAMAGE attribute, which causes MeleeAttackGoal.doHurtTarget() to
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:544:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:549:            applySiegeSpoils(raid, colony, true);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:551:                completeReclaim(raid, colony);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:553:                completeBesiegeVictory(raid, colony);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:556:            applySiegeSpoils(raid, colony, false);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:567:    private static void applySiegeSpoils(BesiegeRaidData raid, IColony defenderColony, boolean attackerWon) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:571:        IColony besiegerColony = getPrimaryColonyOfPlayer(raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:578:        int loserBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(loser.getID());
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:583:        net.machiavelli.minecolonytax.economy.TreasuryManager.deductFromTreasury(loser.getID(), spoil);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:584:        net.machiavelli.minecolonytax.economy.TreasuryManager.addToTreasury(winner.getID(), spoil);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:603:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:604:        int tributePct = TaxConfig.getBesiegeTributePercent();
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:605:        int durationHours = TaxConfig.getBesiegeTributeDurationHours();
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:607:        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:615:                    System.currentTimeMillis(), tributePct);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:624:                                + "! Tax tribute (" + tributePct + "%) now flows to them. "
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:630:                    "Besiege victory! " + colony.getName() + " now pays you " + tributePct
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:631:                            + "% in tribute" + (durationHours > 0 ? " for " + durationHours + "h" : "") + ".")
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:639:                LOGGER.info("Besiege victory: colony {} vassalized to {} at {}% tribute",
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:640:                        colony.getName(), getPlayerName(raid.besiegingPlayerUUID), tributePct);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:649:    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:653:        VassalManager.removeVassalRelation(colonyId);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:954:        // Offline messages are not queued for the besiege system (unlike VassalManager)
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:1020:    private static IColony getPrimaryColonyOfPlayer(UUID playerId) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:1167:        public int tributePercent;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:1173:                UUID formerOwnerUUID, long besiegeTime, int tributePercent) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:1179:            this.tributePercent = tributePercent;
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:304:        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(colony.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:334:            int defenderResult = net.machiavelli.minecolonytax.economy.TreasuryManager.drainTreasury(defenderColonyId);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:336:                    ? net.machiavelli.minecolonytax.economy.TreasuryManager.drainTreasury(attackerColonyId)
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:341:                net.machiavelli.minecolonytax.economy.TreasuryManager.shutdown(); // calls saveData()
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:776:            } else if (TaxConfig.isWarVassalizationEnabled()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:777:                // Vassalize the losing colony instead of transferring ownership
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:778:                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:779:                int durationHours = TaxConfig.getWarVassalizationDurationHours();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:780:                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:783:                        tributePercent,
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:786:                    WARSYSTEM_LOGGER.info("Colony {} has been vassalized by {} for {} hours at {}% tribute",
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:787:                            war.getColony().getName(), war.getAttacker(), durationHours, tributePercent);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1206:            if (TaxConfig.isWarVassalizationEnabled()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1207:                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1208:                int durationHours = TaxConfig.getWarVassalizationDurationHours();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1209:                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1210:                        colony, newOwnerUUID, tributePercent, durationHours);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1212:                    WARSYSTEM_LOGGER.info("Primary colony {} vassalized by {} instead of transferred ({}% tribute for {}h)",
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1213:                            colony.getName(), newOwnerUUID, tributePercent, durationHours);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1292:        net.machiavelli.minecolonytax.economy.TreasuryManager.clearColonyRole(colony.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1294:            net.machiavelli.minecolonytax.economy.TreasuryManager.clearColonyRole(warData.getAttackerColony().getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2623:                    if (!net.machiavelli.minecolonytax.economy.TreasuryManager.canDeclareWar(potentialAttacker.getID(),
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2964:        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(targetColony.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:4125:        net.machiavelli.minecolonytax.economy.TreasuryManager.setColonyAsDefender(e.defenderColonyId);
src/main/java/net/machiavelli/minecolonytax\espionage\ModEntities.java:9:import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
src/main/java/net/machiavelli/minecolonytax\espionage\ModEntities.java:28:        public static void onAttributeCreation(EntityAttributeCreationEvent event) {
src/main/java/net/machiavelli/minecolonytax\espionage\ModEntities.java:29:            event.put(SPY.get(), SpyEntity.createAttributes().build());
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:15:import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:35:public class VassalManager {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:37:    private static final Logger LOGGER = LogManager.getLogger(VassalManager.class);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:41:    private static final Map<Integer, VassalRelation> ACTIVE_VASSALS = new ConcurrentHashMap<>();
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:42:    private static final Map<Integer, VassalProposal> PENDING_PROPOSALS = new ConcurrentHashMap<>();
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:51:            LOGGER.info("VassalManager initialized");
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:59:    public static int requestVassalization(ServerPlayer overlord, IColony targetColony, int percent) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:69:        VassalProposal proposal = new VassalProposal(targetColony.getID(), overlord.getUUID(), percent);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:87:            overlord.sendSystemMessage(Component.literal("Vassalization proposal sent."));
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:101:        VassalProposal prop = PENDING_PROPOSALS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:116:        VassalRelation rel = new VassalRelation(colonyId, prop.overlordUUID, prop.percent, System.currentTimeMillis());
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:135:        VassalProposal prop = PENDING_PROPOSALS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:165:        Iterator<Map.Entry<Integer, VassalRelation>> it = ACTIVE_VASSALS.entrySet().iterator();
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:167:            Map.Entry<Integer, VassalRelation> e = it.next();
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:168:            VassalRelation rel = e.getValue();
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:178:                String msg = "Vassalization between colony " + colony.getName() + " and player " + overlordName
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:196:    public static int listVassals(ServerPlayer player) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:198:        player.sendSystemMessage(Component.literal("Â§6Â§l=== Vassalization Status ==="));
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:200:        boolean isVassal = false;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:201:        for (VassalRelation rel : ACTIVE_VASSALS.values()) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:202:            IColony playerColony = getPrimaryColonyOfPlayer(id);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:204:                isVassal = true;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:211:                                "'s colony (" + rel.percent + "% tribute rate)"));
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:213:                        Component.literal("Â§c[VASSAL STATUS] Last tribute: " + rel.lastTribute + " " + currencyName));
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:218:        if (!isVassal) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:222:        boolean hasVassals = false;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:223:        player.sendSystemMessage(Component.literal("Â§e--- Your Vassals ---"));
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:224:        for (VassalRelation rel : ACTIVE_VASSALS.values()) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:230:                            Component.literal("Â§a- " + colony.getName() + ": " + rel.percent + "% tribute rate"));
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:233:                    hasVassals = true;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:237:        if (!hasVassals) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:245:        VassalRelation rel = ACTIVE_VASSALS.get(colony.getID());
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:257:                    "Â§6âš” War Vassalization Ended: The vassalization of colony '" + colony.getName() + "' has expired.");
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:263:        int tribute = (int) (generatedTax * rel.percent / 100.0);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:264:        if (tribute <= 0)
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:267:        TaxManager.adjustTax(colony, -tribute);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:269:        IColony overlordColony = getPrimaryColonyOfPlayer(rel.overlordUUID);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:271:            TaxManager.adjustTax(overlordColony, tribute);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:274:        rel.lastTribute = tribute;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:278:                .literal("Received tribute of " + tribute + " coins from vassal colony " + colony.getName());
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:284:        return tribute;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:291:    public static boolean forceVassalize(IColony vassalColony, UUID overlordUUID, int tributePercent,
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:294:            LOGGER.warn("forceVassalize called with null colony or overlord");
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:312:        VassalRelation rel = new VassalRelation(
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:315:                tributePercent,
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:318:                true // isWarVassalization
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:324:        IColony overlordColony = getPrimaryColonyOfPlayer(overlordUUID);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:347:                .literal("Â§7You will pay " + tributePercent + "% of your tax income as tribute.");
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:355:                .literal("Â§7They will pay you " + tributePercent + "% of their tax income as tribute.");
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:360:            LOGGER.info("War vassalization created: Colony {} is now vassal to {} with {}% tribute {}",
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:361:                    vassalColony.getName(), overlordName, tributePercent, durationMsg);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:367:    public static int getRemainingVassalizationHours(int colonyId) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:368:        VassalRelation rel = ACTIVE_VASSALS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:379:    public static boolean isWarVassalization(int colonyId) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:380:        VassalRelation rel = ACTIVE_VASSALS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:381:        return rel != null && rel.isWarVassalization;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:385:        VassalRelation rel = ACTIVE_VASSALS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:489:    private static IColony getPrimaryColonyOfPlayer(UUID playerId) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:510:            Type type = new TypeToken<List<VassalRelation>>() {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:512:            List<VassalRelation> list = GSON.fromJson(r, type);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:514:                for (VassalRelation rel : list) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:527:        final List<VassalRelation> list = new ArrayList<>(ACTIVE_VASSALS.values());
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:548:    private static class VassalRelation {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:555:        boolean isWarVassalization;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:557:        public VassalRelation(int colonyId, UUID overlordUUID, int percent, long lastPayment) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:564:            this.isWarVassalization = false;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:567:        public VassalRelation(int colonyId, UUID overlordUUID, int percent, long lastPayment, long expirationTime,
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:568:                boolean isWarVassalization) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:575:            this.isWarVassalization = isWarVassalization;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:583:    private static class VassalProposal {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:588:        public VassalProposal(int colonyId, UUID overlordUUID, int percent) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:612:    public static boolean isColonyVassal(int colonyId) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:616:    public static UUID getVassalOverlordUUID(int colonyId) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:617:        VassalRelation rel = ACTIVE_VASSALS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:621:    public static int getVassalTributeRate(int colonyId) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:622:        VassalRelation rel = ACTIVE_VASSALS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:626:    public static int countVassalsForPlayer(UUID playerId) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:628:        for (VassalRelation rel : ACTIVE_VASSALS.values()) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:636:    public static boolean removeVassalRelation(int vassalColonyId) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:637:        VassalRelation relation = ACTIVE_VASSALS.remove(vassalColonyId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:641:                LOGGER.info("Vassalization ended for colony " + vassalColonyId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:648:    public static boolean endVassalizationWithNotification(int vassalColonyId, ServerPlayer player, IColony colony) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:649:        VassalRelation relation = ACTIVE_VASSALS.get(vassalColonyId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:658:        boolean success = removeVassalRelation(vassalColonyId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:662:            Component notificationMsg = Component.literal("Â§câš  Vassalization Ended: Colony '" + colonyName +
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:668:                overlord.sendSystemMessage(Component.literal("Â§7You will no longer receive tribute from this colony."));
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:672:                        Component.literal("Â§7You will no longer receive tribute from this colony."));
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:676:                LOGGER.info("Vassalization ended for colony {} with notification sent to overlord {}",
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:688:    public static List<VassalIncomeData> getVassalIncomeForPlayer(UUID overlordId) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:689:        List<VassalIncomeData> vassalIncomes = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:690:        for (VassalRelation rel : ACTIVE_VASSALS.values()) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:695:                    int tributeOwed = (int) (currentTaxBalance * rel.percent / 100.0);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:697:                    vassalIncomes.add(new VassalIncomeData(
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:701:                            tributeOwed,
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:712:    public static int claimVassalTribute(UUID overlordId, int vassalColonyId) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:713:        VassalRelation rel = ACTIVE_VASSALS.get(vassalColonyId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:723:        int tributeOwed = (int) (currentTaxBalance * rel.percent / 100.0);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:725:        if (tributeOwed <= 0)
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:728:        TaxManager.adjustTax(vassalColony, -tributeOwed);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:730:        IColony overlordColony = getPrimaryColonyOfPlayer(overlordId);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:732:            TaxManager.adjustTax(overlordColony, tributeOwed);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:736:        rel.lastTribute = tributeOwed;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:739:        return tributeOwed;
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:17:import net.minecraft.world.entity.ai.attributes.AttributeModifier;
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:18:import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:19:import net.minecraft.world.entity.ai.attributes.Attributes;
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:67:    public static AttributeSupplier.Builder createAttributes() {
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:68:        return Mob.createMobAttributes()
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:69:                .add(Attributes.MAX_HEALTH, 20.0D)
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:70:                .add(Attributes.MOVEMENT_SPEED, 0.25D);
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:76:        this.goalSelector.addGoal(1, new SpyFleeGoal(this, 1.0)); // Speed boost applied via attribute modifier in enterFleeState()
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:236:        // so the boost stacks correctly with any future attribute changes.
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:237:        var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:241:            speedAttr.addTransientModifier(new AttributeModifier(
src/main/java/net/machiavelli/minecolonytax\espionage\SpyEntity.java:245:                    AttributeModifier.Operation.ADDITION));
src/main/java/net/machiavelli/minecolonytax\militia\MilitiaAttackGoal.java:16: * Custom attack goal for militia citizens that doesn't require ATTACK_DAMAGE attribute.
src/main/java/net/machiavelli/minecolonytax\commands\FactionCommand.java:262:            net.machiavelli.minecolonytax.economy.TreasuryManager.deposit(player, colony.getID(), amount);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:38:public class TreasuryManager {
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:40:    private static final Logger LOGGER = LogManager.getLogger(TreasuryManager.class);
src/main/java/net/machiavelli/minecolonytax\economy\TreasuryManager.java:60:        if (TaxConfig.isNormalLogging()) LOGGER.info("TreasuryManager initialized with {} treasury records", TREASURIES.size());
src/main/java/net/machiavelli/minecolonytax\militia\CitizenMilitiaManager.java:375:     * This distributes the max tax percentage across all defenders (guards + militia).
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:70:        // War Vassalization Configuration
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:365:        // Vassalization Economy
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:595:                                .define("EnableWarVassalization", true);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:600:                                .defineInRange("WarVassalizationDurationHours", 168, 0, 8760); // Default 1 week, max 1
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:604:                                "Percentage of the vassal colony's tax income paid to the victor as tribute (1-100).\n"
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:608:                                                "Set VassalizationReplacesReparations=true to make tribute the only penalty.")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:609:                                .defineInRange("WarVassalizationTributePercentage", 15, 1, 100); // Default 15%
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:640:                                                "Applied IN ADDITION to ongoing tax-occupation tribute. On attacker victory: extracted\n"
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:875:                                "Maximum percentage of colony tax that can be stolen during a raid (0.0 - 1.0). This amount is distributed across all guards/militia. For example: 0.25 = 25% max tax stolen when all defenders are killed.")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1205:                                "Enable shared tax pool for factions. Member colonies contribute a percentage to the pool.")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1209:                                "Default percentage of tax income that faction members contribute to the shared pool.")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1592:                // --- Vassalization Economy ---
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1593:                BUILDER.push("Vassalization Economy");
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1595:                                "If true, when a colony is vassalized after a war loss, the ongoing tribute replaces "
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1599:                                                "This prevents stacking of reparations + tribute for the losing colony.")
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1600:                                .define("VassalizationReplacesReparations", true);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2450:                                .defineInRange("BesiegeTributePercent", 30, 1, 100);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2454:                                .defineInRange("BesiegeTributeDurationHours", 72, 0, 8760);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2634:        public static boolean isWarVassalizationEnabled() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2650:        public static int getWarVassalizationDurationHours() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2654:        public static int getWarVassalizationTributePercentage() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:3348:        // --- Vassalization Economy ---
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:3349:        public static boolean isVassalizationReplacesReparations() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:3555:        public static int getBesiegeTributePercent() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:3559:        public static int getBesiegeTributeDurationHours() {
src/main/java/net/machiavelli/minecolonytax\commands\TreasuryCommand.java:13:import net.machiavelli.minecolonytax.economy.TreasuryManager;
src/main/java/net/machiavelli/minecolonytax\commands\TreasuryCommand.java:150:        TreasuryManager.sendStatus(player, colony.getID());
src/main/java/net/machiavelli/minecolonytax\commands\TreasuryCommand.java:172:        return TreasuryManager.deposit(player, colony.getID(), amount, src) ? Command.SINGLE_SUCCESS : 0;
src/main/java/net/machiavelli/minecolonytax\commands\TreasuryCommand.java:193:        return TreasuryManager.withdraw(player, colony.getID(), amount, dest) ? Command.SINGLE_SUCCESS : 0;
src/main/java/net/machiavelli/minecolonytax\util\ItemUtils.java:113:     * the raw item count; in multi-denomination mode each item contributes its value.
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java:10:import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java:58:    private final List<VassalIncomeData> vassalData = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java:118:    public void updateVassalData(List<VassalIncomeData> newVassalData) {
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java:120:        this.vassalData.addAll(newVassalData);
src/main/java/net/machiavelli/minecolonytax\gui\TaxManagementScreen.java:181:        pages.put(BookTab.VASSALS, new VassalsPage(this, this.font,
src/main/java/net/machiavelli/minecolonytax\integration\CurrencyService.java:134:     * In multi-denomination mode each item type contributes its configured value.
src/main/java/net/machiavelli/minecolonytax\permissions\TaxPermissionManager.java:23:        // Former owner is locked out while their colony is besieged; besieger collects via VassalManager.
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:363:                                                                                .executes(ctx -> handleVassalize(ctx,
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:371:                                                                .executes(ctx -> handleVassalAccept(ctx,
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:376:                                                                .executes(ctx -> handleVassalDecline(ctx,
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:381:                                                                .executes(ctx -> handleVassalRevoke(ctx,
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:385:                                                .executes(WntCommands::handleVassalList))
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:639:                source.sendSuccess(() -> Component.literal("Â§6Vassal Commands:"), false);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:975:                        // Vassal commands
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1473:                                final String line = String.format("  \u2022 %s (ID: %d) \u2014 besieged by %s, former owner: %s \u2014 tribute: %d%%",
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1474:                                                occ.colonyName, occ.colonyId, besiegerName, formerOwnerName, occ.tributePercent);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1544:                                        line = String.format("  \u2022 %s (ID: %d) \u2014 you occupy it \u2014 tribute: %d%%",
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1545:                                                        occ.colonyName, occ.colonyId, occ.tributePercent);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1549:                                        line = String.format("  \u2022 %s (ID: %d) \u2014 occupied by %s \u2014 tribute: %d%%",
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1550:                                                        occ.colonyName, occ.colonyId, besiegerName, occ.tributePercent);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2334:        // Vassal command handlers
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2335:        private static int handleVassalize(CommandContext<CommandSourceStack> ctx, int percent, String colonyName)
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2357:                return net.machiavelli.minecolonytax.vassalization.VassalManager.requestVassalization(player, target,
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2361:        private static int handleVassalAccept(CommandContext<CommandSourceStack> ctx, int colonyId)
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2364:                return net.machiavelli.minecolonytax.vassalization.VassalManager.acceptProposal(player, colonyId);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2367:        private static int handleVassalDecline(CommandContext<CommandSourceStack> ctx, int colonyId)
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2370:                return net.machiavelli.minecolonytax.vassalization.VassalManager.declineProposal(player, colonyId);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2373:        private static int handleVassalRevoke(CommandContext<CommandSourceStack> ctx, String playerName)
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2376:                return net.machiavelli.minecolonytax.vassalization.VassalManager.revokeRelation(player, playerName);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2379:        private static int handleVassalList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:2381:                return net.machiavelli.minecolonytax.vassalization.VassalManager.listVassals(player);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3773:                int treasuryBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3807:                player.sendSystemMessage(Component.literal("Treasury: " + net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(colonyId)).withStyle(ChatFormatting.AQUA));
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3857:                int balance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:13:import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:45:    public static List<VassalIncomeData> collectVassalIncomeData(ServerPlayer player) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:46:        List<VassalIncomeData> vassalIncomes = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:50:            List<VassalIncomeData> vassalData = net.machiavelli.minecolonytax.vassalization.VassalManager
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:51:                    .getVassalIncomeForPlayer(playerId);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:53:            for (VassalIncomeData data : vassalData) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:57:                    IColony vassalColony = colonyManager.getColonyByWorld(data.getVassalColonyId(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:60:                        WarData war = WarSystem.ACTIVE_WARS.get(data.getVassalColonyId());
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:61:                        boolean isBeingRaided = RaidManager.getActiveRaidForColony(data.getVassalColonyId()) != null;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:62:                        boolean isBesieged = net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(data.getVassalColonyId());
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:63:                        boolean isOccupied = net.machiavelli.minecolonytax.occupation.OccupationManager.getOccupation(data.getVassalColonyId()) != null;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:65:                                && !TaxManager.isGenerationDisabled(data.getVassalColonyId());
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:69:                        LOGGER.debug("Error checking claim status for vassal colony {}: {}", data.getVassalColonyName(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:75:                vassalIncomes.add(new VassalIncomeData(
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:76:                        data.getVassalColonyId(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:77:                        data.getVassalColonyName(),
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:156:            boolean isVassal = false;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:158:            boolean hasVassals = false;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:162:                isVassal = net.machiavelli.minecolonytax.vassalization.VassalManager.isColonyVassal(colonyId);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:163:                if (isVassal) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:164:                    vassalTributeRate = net.machiavelli.minecolonytax.vassalization.VassalManager
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:165:                            .getVassalTributeRate(colonyId);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:168:                vassalCount = net.machiavelli.minecolonytax.vassalization.VassalManager.countVassalsForPlayer(playerId);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:169:                hasVassals = vassalCount > 0;
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:171:                if (isVassal || hasVassals) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:173:                        LOGGER.debug("Colony {} (Player: {}) - isVassal: {}, vassalRate: {}, hasVassals: {}, vassalCount: {}",
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:174:                                colonyId, playerId, isVassal, vassalTributeRate, hasVassals, vassalCount);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:198:                    isVassal, vassalTributeRate, hasVassals, vassalCount,
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:3:public class VassalIncomeData {
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:6:    private final int tributeRate;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:7:    private final int tributeOwed;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:15:    public VassalIncomeData(int vassalColonyId, String vassalColonyName, int tributeRate, 
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:16:                           int tributeOwed, int lastTribute, long lastPayment, boolean canClaim) {
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:19:        this.tributeRate = tributeRate;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:20:        this.tributeOwed = tributeOwed;
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:26:    public int getVassalColonyId() { return vassalColonyId; }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:27:    public String getVassalColonyName() { return vassalColonyName; }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:28:    public int getTributeRate() { return tributeRate; }
src/main/java/net/machiavelli/minecolonytax\gui\data\VassalIncomeData.java:29:    public int getTributeOwed() { return tributeOwed; }
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:18: * Vassalage is intentionally NOT gated here â€” losing a war can still vassalize
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:19: * a primary colony (the loser pays tribute) without the deed moving. Only
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:69:                    + "(EnablePrimaryColonyTransfer = false). Vassalization or tax-occupation may still apply.";
src/main/java/net/machiavelli/minecolonytax\upgrade\ColonyUpgradeManager.java:7:import net.machiavelli.minecolonytax.economy.TreasuryManager;
src/main/java/net/machiavelli/minecolonytax\upgrade\ColonyUpgradeManager.java:70:        if (!TreasuryManager.purchase(colonyId, cost, server)) return false;
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:28:import net.machiavelli.minecolonytax.economy.TreasuryManager;
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:655:                    // --- Vassal tribute processing ---
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:656:                    int tributePaid = net.machiavelli.minecolonytax.vassalization.VassalManager.handleTaxIncome(colony,
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:658:                    if (tributePaid > 0) {
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:659:                        // Recalculate final balance after tribute deduction
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:670:                            TreasuryManager.addToTreasury(colonyId, depositAmount);
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:692:                                LOGGER.info("Contributed {} to Faction Shared Pool for colony {}", (int) divertedAmount,
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:861:                            // Show tribute paid if applicable
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:862:                            if (tributePaid > 0) {
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:864:                                        "message.minecolonytax.tax_report_tribute",
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:865:                                        tributePaid).withStyle(net.minecraft.ChatFormatting.GRAY));
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:877:                                int treasuryBalance = TreasuryManager.getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:16:    private final boolean isVassal;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:18:    private final boolean hasVassals;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:35:                        boolean isVassal, int vassalTributeRate, boolean hasVassals, int vassalCount,
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:51:        this.isVassal = isVassal;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:53:        this.hasVassals = hasVassals;
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:76:    public boolean isVassal() { return isVassal; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:77:    public int getVassalTributeRate() { return vassalTributeRate; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:78:    public boolean hasVassals() { return hasVassals; }
src/main/java/net/machiavelli/minecolonytax\gui\data\ColonyTaxData.java:79:    public int getVassalCount() { return vassalCount; }
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:4:import net.machiavelli.minecolonytax.vassalization.VassalManager;
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:12:import net.machiavelli.minecolonytax.economy.TreasuryManager;
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:173:        VassalManager.initialize(event.getServer());
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:175:        TreasuryManager.initialize(event.getServer());
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:176:        if (TaxConfig.isNormalLogging()) LOGGER.info("TreasuryManager initialized");
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:312:            VassalManager.shutdown();
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:313:            if (TaxConfig.isNormalLogging()) LOGGER.info("VassalManager shutdown complete");
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:315:            LOGGER.warn("Error during VassalManager shutdown: {}", t.toString());
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:319:            TreasuryManager.shutdown();
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:320:            if (TaxConfig.isNormalLogging()) LOGGER.info("TreasuryManager shutdown complete");
src/main/java/net/machiavelli/minecolonytax\MineColonyTax.java:322:            LOGGER.warn("Error during TreasuryManager shutdown: {}", t.toString());
src/main/java/net/machiavelli/minecolonytax\gui\book\BookTab.java:18:    VASSALS("Vassals", Side.LEFT, 14, 90, 28, 22,
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java:196:        if (colony.isVassal()) {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java:197:            drawBadge(g, font, "Vassal " + colony.getVassalTributeRate() + "%", x, y, ORANGE);
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java:199:        } else if (colony.hasVassals()) {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java:200:            drawBadge(g, font, "Overlord (" + colony.getVassalCount() + ")", x, y, GREEN);
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java:207:        if (colony.canClaimTax() && colony.getTaxBalance() > 0 && !colony.isVassal()) {
src/main/java/net/machiavelli/minecolonytax\gui\book\ColoniesPage.java:356:                if (selected.canClaimTax() && selected.getTaxBalance() > 0 && !selected.isVassal()) {
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:7:import net.machiavelli.minecolonytax.network.packets.EndVassalizationPacket;
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:80:                CHANNEL.messageBuilder(EndVassalizationPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:81:                                .decoder(EndVassalizationPacket::new)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:82:                                .encoder(EndVassalizationPacket::toBytes)
src/main/java/net/machiavelli/minecolonytax\network\NetworkHandler.java:83:                                .consumerMainThread(EndVassalizationPacket::handle)
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:17:import net.machiavelli.minecolonytax.economy.TreasuryManager;
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:216:                int treasuryBalance = TreasuryManager.getTreasuryBalance(raiderColony.getID());
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:230:                TreasuryManager.deductFromTreasury(raiderColony.getID(), raidCost);
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:696:                    ? net.machiavelli.minecolonytax.economy.TreasuryManager
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:1496:            // New system: Tax stolen based on guards/militia killed, distributed from max
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:4:import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:6:import net.machiavelli.minecolonytax.network.packets.EndVassalizationPacket;
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:18: * Vassals tab: left page shows overlords + vassals, right page shows selected detail.
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:20:public class VassalsPage extends BookPage {
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:22:    private final Supplier<List<VassalIncomeData>> vassalSupplier;
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:27:    private int selectedVassalIndex = -1;
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:30:    public VassalsPage(Screen screen, Font font,
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:31:                       Supplier<List<VassalIncomeData>> vassalSupplier,
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:43:        selectedVassalIndex = -1;
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:49:        List<VassalIncomeData> vassals = vassalSupplier.get();
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:51:                .filter(ColonyTaxData::isVassal).collect(Collectors.toList());
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:76:                g.drawString(font, "Tribute: " + ol.getVassalTributeRate() + "%", leftX + 4, y + 10, DANGER, false);
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:92:                VassalIncomeData v = vassals.get(i + scrollOffset);
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:94:                boolean sel = selectedVassalIndex == i + scrollOffset;
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:103:                g.drawString(font, truncate(font, v.getVassalColonyName(), leftW - 6), leftX + 4, iy + 1, INK, false);
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:112:        List<VassalIncomeData> vassals = vassalSupplier.get();
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:114:                .filter(ColonyTaxData::isVassal).collect(Collectors.toList());
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:121:        if (selectedVassalIndex >= 0 && selectedVassalIndex < vassals.size()) {
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:122:            renderVassalDetail(g, vassals.get(selectedVassalIndex), mouseX, mouseY);
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:138:        g.drawCenteredString(font, ol.getVassalTributeRate() + "%", x + w / 2, y + 10, INK);
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:142:        drawButton(g, font, "Break Vassalage", x, btnY, w, 11, mouseX, mouseY, DANGER);
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:145:    private void renderVassalDetail(GuiGraphics g, VassalIncomeData v, int mouseX, int mouseY) {
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:148:        g.drawString(font, truncate(font, v.getVassalColonyName(), w - 50), x, y, INK, false);
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:173:        List<VassalIncomeData> vassals = vassalSupplier.get();
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:175:                .filter(ColonyTaxData::isVassal).collect(Collectors.toList());
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:184:                        selectedVassalIndex = -1;
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:200:                        selectedVassalIndex = (selectedVassalIndex == idx) ? -1 : idx;
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:213:                NetworkHandler.sendToServer(new EndVassalizationPacket(ol.getColonyId()));
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:226:            List<VassalIncomeData> vassals = vassalSupplier.get();
src/main/java/net/machiavelli/minecolonytax\gui\book\VassalsPage.java:229:                    .filter(ColonyTaxData::isVassal).collect(Collectors.toList());
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimVassalTributePacket.java:5:import net.machiavelli.minecolonytax.vassalization.VassalManager;
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimVassalTributePacket.java:14: * Packet for claiming vassal tribute from the GUI
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimVassalTributePacket.java:16:public class ClaimVassalTributePacket {
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimVassalTributePacket.java:19:    public ClaimVassalTributePacket(int vassalColonyId) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimVassalTributePacket.java:23:    public ClaimVassalTributePacket(FriendlyByteBuf buf) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimVassalTributePacket.java:45:            int tributeAmount = VassalManager.claimVassalTribute(player.getUUID(), vassalColonyId);
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimVassalTributePacket.java:47:            if (tributeAmount > 0) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimVassalTributePacket.java:52:                    SDMShopIntegration.setMoney(player, currentBalance + tributeAmount);
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimVassalTributePacket.java:53:                    player.sendSystemMessage(Component.literal("Â§a[Vassal Tribute] Claimed " + tributeAmount + " coins from vassal colony!"));
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimVassalTributePacket.java:58:                    player.sendSystemMessage(Component.literal("Â§a[Vassal Tribute] Claimed " + tributeAmount + " " + currencyItemName + " from vassal colony!"));
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimVassalTributePacket.java:61:                player.sendSystemMessage(Component.literal("Â§c[Vassal Tribute] No tribute available to claim from this vassal."));
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimVassalTributePacket.java:65:            player.sendSystemMessage(Component.literal("Â§c[Error] Failed to claim vassal tribute: " + e.getMessage()));
src/main/java/net/machiavelli/minecolonytax\network\packets\BuyInvestmentPacket.java:7:import net.machiavelli.minecolonytax.economy.TreasuryManager;
src/main/java/net/machiavelli/minecolonytax\network\packets\BuyInvestmentPacket.java:76:            int treasuryBalance = TreasuryManager.getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\network\packets\BuyInvestmentPacket.java:77:            int maxCapacity = TreasuryManager.getEffectiveMaxCapacity(colonyId);
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java:9:import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
src/main/java/net/machiavelli/minecolonytax\network\packets\DismissEventPacket.java:62:            List<VassalIncomeData> vassalData = ColonyDataCollector.collectVassalIncomeData(player);
src/main/java/net/machiavelli/minecolonytax\network\packets\EndVassalizationPacket.java:7:import net.machiavelli.minecolonytax.vassalization.VassalManager;
src/main/java/net/machiavelli/minecolonytax\network\packets\EndVassalizationPacket.java:18:public class EndVassalizationPacket {
src/main/java/net/machiavelli/minecolonytax\network\packets\EndVassalizationPacket.java:21:    public EndVassalizationPacket(int colonyId) {
src/main/java/net/machiavelli/minecolonytax\network\packets\EndVassalizationPacket.java:25:    public EndVassalizationPacket(FriendlyByteBuf buf) {
src/main/java/net/machiavelli/minecolonytax\network\packets\EndVassalizationPacket.java:59:            if (!VassalManager.isColonyVassal(colonyId)) {
src/main/java/net/machiavelli/minecolonytax\network\packets\EndVassalizationPacket.java:65:            boolean success = VassalManager.endVassalizationWithNotification(colonyId, player, colony);
src/main/java/net/machiavelli/minecolonytax\network\packets\EndVassalizationPacket.java:68:                player.sendSystemMessage(Component.literal("Â§aVassalization ended for " + colony.getName()));
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:7:import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:23:    private final List<VassalIncomeData> vassalData;
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:34:    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:41:    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:49:    public ColonyDataResponsePacket(List<ColonyTaxData> colonyData, List<VassalIncomeData> vassalData,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:76:            boolean isVassal = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:78:            boolean hasVassals = buf.readBoolean();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:92:                isVassal, vassalTributeRate, hasVassals, vassalCount,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:106:            int tributeRate = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:107:            int tributeOwed = buf.readInt();
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:112:            this.vassalData.add(new VassalIncomeData(
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:113:                vassalColonyId, vassalColonyName, tributeRate,
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:114:                tributeOwed, lastTribute, lastPayment, canClaim
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:165:            buf.writeBoolean(data.isVassal());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:166:            buf.writeInt(data.getVassalTributeRate());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:167:            buf.writeBoolean(data.hasVassals());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:168:            buf.writeInt(data.getVassalCount());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:181:        for (VassalIncomeData data : vassalData) {
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:182:            buf.writeInt(data.getVassalColonyId());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:183:            buf.writeUtf(data.getVassalColonyName());
src/main/java/net/machiavelli/minecolonytax\network\packets\ColonyDataResponsePacket.java:224:                screen.updateVassalData(vassalData);
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimTaxPacket.java:10:import net.machiavelli.minecolonytax.vassalization.VassalManager;
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimTaxPacket.java:68:                // Vassal tribute claim - check if player owns this vassal
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimTaxPacket.java:69:                claimedAmount = VassalManager.claimVassalTribute(player.getUUID(), colonyId);
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimTaxPacket.java:71:                    player.sendSystemMessage(Component.literal("No tribute available from this vassal colony."));
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java:12:import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java:129:                List<VassalIncomeData> vassalData = ColonyDataCollector.collectVassalIncomeData(player);
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestInvestmentDataPacket.java:7:import net.machiavelli.minecolonytax.economy.TreasuryManager;
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestInvestmentDataPacket.java:55:            int treasuryBalance = TreasuryManager.getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestInvestmentDataPacket.java:56:            int maxCapacity = TreasuryManager.getEffectiveMaxCapacity(colonyId);
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestTreasuryDataPacket.java:15:import net.machiavelli.minecolonytax.economy.TreasuryManager;
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestTreasuryDataPacket.java:54:            int balance = TreasuryManager.getTreasuryBalance(colonyId);
src/main/java/net/machiavelli/minecolonytax\network\packets\TreasuryActionPacket.java:6:import net.machiavelli.minecolonytax.economy.TreasuryManager;
src/main/java/net/machiavelli/minecolonytax\network\packets\TreasuryActionPacket.java:67:                case DEPOSIT  -> TreasuryManager.deposit(player, colonyId, amount, source);
src/main/java/net/machiavelli/minecolonytax\network\packets\TreasuryActionPacket.java:68:                case WITHDRAW -> TreasuryManager.withdraw(player, colonyId, amount, source);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 442ms:
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:181:        IColony besiegerColony = getPrimaryColonyOfPlayer(besiegerUUID);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:198:        // PrimaryColonyTaxOccupationDays). Secondaries continue to use the legacy
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:564:     * One-shot percentage transfer between loser's and winner's primary colony treasuries.
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:571:        IColony besiegerColony = getPrimaryColonyOfPlayer(raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:1020:    private static IColony getPrimaryColonyOfPlayer(UUID playerId) {
src/main/java/net/machiavelli/minecolonytax\FirstColonyTracker.java:29: * the new primary colony.
src/main/java/net/machiavelli/minecolonytax\FirstColonyTracker.java:42:     * If this is their first colony, it becomes their primary colony.
src/main/java/net/machiavelli/minecolonytax\FirstColonyTracker.java:212:     * player's primary colony.
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1187:     *         (e.g. primary colony protection), vassalized as fallback, or failed
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2746:        boolean isPrimaryColony = FirstColonyTracker.isFirstColony(targetOwnerUUID, targetColony.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2750:            if (TaxConfig.isOutpostVulnerabilityEnabled() && !isPrimaryColony) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2761:                if (isPrimaryColony) {
src/main/java/net/machiavelli/minecolonytax\commands\OfficerTrackingDebugCommand.java:130:                    .append(Component.literal("Not tracked as anyone's primary colony")
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:202:            IColony playerColony = getPrimaryColonyOfPlayer(id);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:269:        IColony overlordColony = getPrimaryColonyOfPlayer(rel.overlordUUID);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:324:        IColony overlordColony = getPrimaryColonyOfPlayer(overlordUUID);
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:489:    private static IColony getPrimaryColonyOfPlayer(UUID playerId) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:730:        IColony overlordColony = getPrimaryColonyOfPlayer(overlordId);
src/main/java/net/machiavelli/minecolonytax\compat\ColonyHelper.java:9:    public static IColony getPrimaryColony(UUID playerUUID) {
src/main/java/net/machiavelli/minecolonytax\compat\ColonyHelper.java:19:    public static int getPrimaryColonyId(UUID playerUUID) {
src/main/java/net/machiavelli/minecolonytax\compat\ColonyHelper.java:20:        IColony c = getPrimaryColony(playerUUID);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:621:                                .define("EnablePrimaryColonyTransfer", false);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:635:                                .defineInRange("PrimaryColonyTaxOccupationDays", 7, 1, 90);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:642:                                                "from the besieged colony's treasury into the besieger's primary colony treasury.\n"
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:644:                                                "On defender victory: extracted from the besieger's primary colony treasury into the\n"
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2638:        public static boolean isPrimaryColonyTransferEnabled() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2642:        public static int getPrimaryColonyTaxOccupationDays() {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:54:     * EnablePrimaryColonyTransfer is on. Secondaries run in TRANSFER_PENDING.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:168:        // EnablePrimaryColonyTransfer lets server owners opt back into the legacy
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:174:        if (isPrimary && !TaxConfig.isPrimaryColonyTransferEnabled()) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:176:            durationDays = TaxConfig.getPrimaryColonyTaxOccupationDays();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:441:                    LOGGER.info("Tax-only occupation expired for primary colony {} - auto-reclaiming", colony.getName());
src/main/java/net/machiavelli/minecolonytax\trade\TradeRouteManager.java:175:        IColony myColony = getPlayerPrimaryColony(player);
src/main/java/net/machiavelli/minecolonytax\trade\TradeRouteManager.java:234:        IColony myColony = getPlayerPrimaryColony(player);
src/main/java/net/machiavelli/minecolonytax\trade\TradeRouteManager.java:280:        IColony myColony = getPlayerPrimaryColony(player);
src/main/java/net/machiavelli/minecolonytax\trade\TradeRouteManager.java:293:        IColony myColony = getPlayerPrimaryColony(player);
src/main/java/net/machiavelli/minecolonytax\trade\TradeRouteManager.java:391:    private static IColony getPlayerPrimaryColony(ServerPlayer player) {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:63:            // Collect all colonies this player owns, FCT-primary colony first
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:15: * transferable when {@code EnablePrimaryColonyTransfer} is set to {@code true}
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:19: * a primary colony (the loser pays tribute) without the deed moving. Only
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:31:     *         primary and {@code EnablePrimaryColonyTransfer} is off
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:42:            return TaxConfig.isPrimaryColonyTransferEnabled();
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:69:                    + "(EnablePrimaryColonyTransfer = false). Vassalization or tax-occupation may still apply.";
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 12 "private static void completeBesiege|private static void applySiegeSpoils|private static void completeBesiegeVictory|private static void completeReclaim|private static IColony getPrimaryColonyOfPlayer" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 8 "public static int deductFromTreasury|public static int addToTreasury|public static int getTreasuryBalance" src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 16 "claimVassalTribute|forceVassalize|getPrimaryColonyOfPlayer" src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 8 "BesiegeSpoilPercentOfLoserTreasury|BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY|BesiegeTribute" src/main/java/net/machiavelli/minecolonytax/TaxConfig.java src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 341ms:
81-        DEFENDER_COLONIES.remove(colonyId);
82-    }
83-
84-    // ==================== Treasury Operations ====================
85-
86-    /**
87-     * Get the current treasury balance for a colony.
88-     */
89:    public static int getTreasuryBalance(int colonyId) {
90-        return TREASURIES.getOrDefault(colonyId, 0);
91-    }
92-
93-    /**
94-     * Deposit funds into the treasury from the colony's tax balance (default source).
95-     * Kept for backwards-compatible callers (e.g. FactionCommand).
96-     */
97-    public static boolean deposit(ServerPlayer player, int colonyId, int amount) {
--
425-
426-    /**
427-     * Deduct a specific amount from the treasury (for one-time raid costs).
428-     *
429-     * @param colonyId The colony ID
430-     * @param amount   Amount to deduct
431-     * @return new balance after deduction
432-     */
433:    public static int deductFromTreasury(int colonyId, int amount) {
434-        int currentBalance = getTreasuryBalance(colonyId);
435-        int newBalance = Math.max(0, currentBalance - amount);
436-        TREASURIES.put(colonyId, newBalance);
437-        // Periodic save handled by WarSystem drain loop
438-        if (TaxConfig.isNormalLogging()) LOGGER.info("Deducted {} from colony {} treasury. New balance: {}", amount, colonyId, newBalance);
439-        return newBalance;
440-    }
441-
442-    /**
443-     * Add a specific amount to the treasury.
444-     *
445-     * @param colonyId The colony ID
446-     * @param amount   Amount to add
447-     * @return new balance after addition
448-     */
449:    public static int addToTreasury(int colonyId, int amount) {
450-        int currentBalance = getTreasuryBalance(colonyId);
451-        int maxCapacity = getEffectiveMaxCapacity(colonyId);
452-        int newBalance = Math.min(maxCapacity, currentBalance + amount);
453-
454-        TREASURIES.put(colonyId, newBalance);
455-        saveData();
456-        if (TaxConfig.isNormalLogging()) LOGGER.info("Added {} to colony {} treasury. New balance: {}", amount, colonyId, newBalance);
457-        return newBalance;
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 346ms:
532-
533-                world.addFreshEntity(merc);
534-                raid.spawnedMercenaries.add(merc);
535-                spawned++;
536-            } catch (Exception e) {
537-                LOGGER.warn("Failed to spawn mercenary {} for besiege on colony {}", i, colony.getName(), e);
538-            }
539-        }
540-        return spawned;
541-    }
542-
543-
544:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
545-        cleanupRaid(raid, true);
546-        applyCooldown(raid.besiegingPlayerUUID);
547-
548-        if (attackerWon) {
549-            applySiegeSpoils(raid, colony, true);
550-            if (raid.isReclaim) {
551-                completeReclaim(raid, colony);
552-            } else {
553-                completeBesiegeVictory(raid, colony);
554-            }
555-        } else {
556-            applySiegeSpoils(raid, colony, false);
557-            sendToPlayer(raid.besiegingPlayerUUID,
558-                    Component.literal("The besiege of " + colony.getName() + " failed.")
559-                            .withStyle(ChatFormatting.RED));
560-        }
561-    }
562-
563-    /**
564-     * One-shot percentage transfer between loser's and winner's primary colony treasuries.
565-     * Mirrors the war-spoil semantics; configured via BesiegeSpoilPercentOfLoserTreasury.
566-     */
567:    private static void applySiegeSpoils(BesiegeRaidData raid, IColony defenderColony, boolean attackerWon) {
568-        int percent = TaxConfig.getBesiegeSpoilPercentOfLoserTreasury();
569-        if (percent <= 0) return;
570-
571-        IColony besiegerColony = getPrimaryColonyOfPlayer(raid.besiegingPlayerUUID);
572-        if (besiegerColony == null) return;
573-
574-        IColony loser = attackerWon ? defenderColony : besiegerColony;
575-        IColony winner = attackerWon ? besiegerColony : defenderColony;
576-        if (loser == null || winner == null) return;
577-
578-        int loserBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(loser.getID());
579-        if (loserBalance <= 0) return;
--
591-        UUID winnerOwner = winner.getPermissions().getOwner();
592-        UUID loserOwner = loser.getPermissions().getOwner();
593-        Component winMsg = Component.literal("Siege spoils: " + spoil + " coins transferred from "
594-                + loser.getName() + " to " + winner.getName() + ".")
595-                .withStyle(ChatFormatting.GOLD);
596-        Component loseMsg = Component.literal("Siege fine: " + spoil + " coins paid from "
597-                + loser.getName() + " to " + winner.getName() + ".")
598-                .withStyle(ChatFormatting.RED);
599-        if (winnerOwner != null) sendToPlayer(winnerOwner, winMsg);
600-        if (loserOwner != null && !loserOwner.equals(winnerOwner)) sendToPlayer(loserOwner, loseMsg);
601-    }
602-
603:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
604-        int tributePct = TaxConfig.getBesiegeTributePercent();
605-        int durationHours = TaxConfig.getBesiegeTributeDurationHours();
606-
607-        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
608-
609-        if (vassalized) {
610-            // Store occupation record
611-            UUID ownerUUID = colony.getPermissions().getOwner();
612-            BesiegeOccupationData occ = new BesiegeOccupationData(
613-                    colony.getID(), colony.getName(),
614-                    raid.besiegingPlayerUUID, ownerUUID,
615-                    System.currentTimeMillis(), tributePct);
--
637-
638-            if (TaxConfig.isNormalLogging())
639-                LOGGER.info("Besiege victory: colony {} vassalized to {} at {}% tribute",
640-                        colony.getName(), getPlayerName(raid.besiegingPlayerUUID), tributePct);
641-        } else {
642-            // Colony was already a vassal (edge case) â€” just notify
643-            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
644-                    "Besiege raid completed but vassalization could not be established (colony may already be a vassal).")
645-                    .withStyle(ChatFormatting.YELLOW));
646-        }
647-    }
648-
649:    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
650-        int colonyId = colony.getID();
651-
652-        // Remove vassalization
653-        VassalManager.removeVassalRelation(colonyId);
654-
655-        // Remove occupation record
656-        OCCUPATIONS.remove(colonyId);
657-        saveData();
658-
659-        sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
660-                "Reclaim successful! " + colony.getName() + " is free from occupation.")
661-                .withStyle(ChatFormatting.GREEN));
--
1008-        if (SERVER == null) return uuid.toString();
1009-        ServerPlayer p = SERVER.getPlayerList().getPlayer(uuid);
1010-        return p != null ? p.getName().getString() : uuid.toString();
1011-    }
1012-
1013-    private static IColony getColonyById(int colonyId) {
1014-        return IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
1015-                .filter(c -> c.getID() == colonyId)
1016-                .findFirst()
1017-                .orElse(null);
1018-    }
1019-
1020:    private static IColony getPrimaryColonyOfPlayer(UUID playerId) {
1021-        IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
1022-        // Prefer FCT: it tracks the true first colony regardless of permissions state
1023-        Integer firstColonyId = FirstColonyTracker.getFirstColony(playerId);
1024-        if (firstColonyId != null) {
1025-            IColony first = cm.getAllColonies().stream()
1026-                    .filter(c -> c.getID() == firstColonyId)
1027-                    .findFirst().orElse(null);
1028-            if (first != null) return first;
1029-        }
1030-        // Fallback: any colony where the player is listed as MC owner
1031-        for (IColony c : cm.getAllColonies()) {
1032-            UUID owner = c.getPermissions().getOwner();
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 340ms:
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-557-            sendToPlayer(raid.besiegingPlayerUUID,
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-558-                    Component.literal("The besiege of " + colony.getName() + " failed.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-559-                            .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-560-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-561-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-562-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-563-    /**
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-564-     * One-shot percentage transfer between loser's and winner's primary colony treasuries.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:565:     * Mirrors the war-spoil semantics; configured via BesiegeSpoilPercentOfLoserTreasury.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-566-     */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-567-    private static void applySiegeSpoils(BesiegeRaidData raid, IColony defenderColony, boolean attackerWon) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:568:        int percent = TaxConfig.getBesiegeSpoilPercentOfLoserTreasury();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-569-        if (percent <= 0) return;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-570-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-571-        IColony besiegerColony = getPrimaryColonyOfPlayer(raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-572-        if (besiegerColony == null) return;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-573-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-574-        IColony loser = attackerWon ? defenderColony : besiegerColony;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-575-        IColony winner = attackerWon ? besiegerColony : defenderColony;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-576-        if (loser == null || winner == null) return;
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-596-        Component loseMsg = Component.literal("Siege fine: " + spoil + " coins paid from "
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-597-                + loser.getName() + " to " + winner.getName() + ".")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-598-                .withStyle(ChatFormatting.RED);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-599-        if (winnerOwner != null) sendToPlayer(winnerOwner, winMsg);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-600-        if (loserOwner != null && !loserOwner.equals(winnerOwner)) sendToPlayer(loserOwner, loseMsg);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-601-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-602-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-603-    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:604:        int tributePct = TaxConfig.getBesiegeTributePercent();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:605:        int durationHours = TaxConfig.getBesiegeTributeDurationHours();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-606-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-607-        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-608-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-609-        if (vassalized) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-610-            // Store occupation record
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-611-            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-612-            BesiegeOccupationData occ = new BesiegeOccupationData(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-613-                    colony.getID(), colony.getName(),
--
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-70-        // War Vassalization Configuration
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-71-        public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_VASSALIZATION;
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-72-        public static final ForgeConfigSpec.IntValue WAR_VASSALIZATION_DURATION_HOURS;
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-73-        public static final ForgeConfigSpec.IntValue WAR_VASSALIZATION_TRIBUTE_PERCENTAGE;
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-74-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-75-        // Colony Tier Protection (Siege SMP ruleset)
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-76-        public static final ForgeConfigSpec.BooleanValue ENABLE_PRIMARY_COLONY_TRANSFER;
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-77-        public static final ForgeConfigSpec.IntValue PRIMARY_COLONY_TAX_OCCUPATION_DAYS;
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java:78:        public static final ForgeConfigSpec.IntValue BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY;
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-79-        public static final ForgeConfigSpec.IntValue JOIN_PHASE_DURATION_MINUTES;
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-80-        public static final ForgeConfigSpec.BooleanValue WAR_ACCEPTANCE_REQUIRED;
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-81-        public static final ForgeConfigSpec.BooleanValue KEEP_INVENTORY_ON_LAST_LIFE;
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-82-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-83-        public static final ForgeConfigSpec.IntValue REQUIRED_GUARD_TOWERS_FOR_BOOST;
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-84-        public static final ForgeConfigSpec.DoubleValue GUARD_TOWER_TAX_BOOST_PERCENTAGE;
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-85-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-86-        public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_ACTIONS;
--
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-629-                                                +
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-630-                                                "does not mount a successful counter-besiege within this window, the occupation\n"
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-631-                                                +
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-632-                                                "auto-reclaims (taxes route back to the owner). Secondary colonies use the standard\n"
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-633-                                                +
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-634-                                                "OccupationDurationDays config and DO transfer on expiry.")
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-635-                                .defineInRange("PrimaryColonyTaxOccupationDays", 7, 1, 90);
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-636-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java:637:                BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY = BUILDER.comment(
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-638-                                "One-shot percentage of the loser's treasury transferred to the winner on besiege resolution.\n"
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-639-                                                +
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-640-                                                "Applied IN ADDITION to ongoing tax-occupation tribute. On attacker victory: extracted\n"
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-641-                                                +
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-642-                                                "from the besieged colony's treasury into the besieger's primary colony treasury.\n"
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-643-                                                +
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-644-                                                "On defender victory: extracted from the besieger's primary colony treasury into the\n"
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-645-                                                +
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-646-                                                "defending colony's treasury. 0 disables siege spoils entirely.")
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java:647:                                .defineInRange("BesiegeSpoilPercentOfLoserTreasury", 25, 0, 100);
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-648-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-649-                // ========== Colony Occupation Settings ==========
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-650-                BUILDER.push("Colony Occupation");
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-651-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-652-                ENABLE_OCCUPATION_SYSTEM = BUILDER.comment(
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-653-                                "When enabled and colony transfer is enabled, winning a war puts the colony into an OCCUPIED state\n"
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-654-                                                + "instead of immediately transferring ownership. The occupier can collect taxes but cannot\n"
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-655-                                                + "interact with colony buildings or items. The original owner has a configurable number of days\n"
--
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2442-                                .defineInRange("BesiegeCooldownHours", 24, 0, 168);
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2443-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2444-                BESIEGE_MILITIA_PERCENT = BUILDER
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2445-                                .comment("Fraction of eligible citizens (non-guard, non-deliveryman) converted to militia defenders during a besiege.")
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2446-                                .defineInRange("BesiegeMilitiaPercent", 0.6, 0.0, 1.0);
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2447-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2448-                BESIEGE_TRIBUTE_PERCENT = BUILDER
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2449-                                .comment("Percentage of tax income siphoned from the besieged colony to the victor.")
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java:2450:                                .defineInRange("BesiegeTributePercent", 30, 1, 100);
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2451-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2452-                BESIEGE_TRIBUTE_DURATION_HOURS = BUILDER
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2453-                                .comment("How long (hours) the besiege vassalage lasts. Set to 0 for permanent until reclaimed.")
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java:2454:                                .defineInRange("BesiegeTributeDurationHours", 72, 0, 8760);
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2455-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2456-                BESIEGE_MIN_COLONY_SIZE = BUILDER
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2457-                                .comment("Minimum citizen count required in the target colony before it can be besieged.")
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2458-                                .defineInRange("BesiegeMinColonySize", 5, 1, 100);
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2459-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2460-                BESIEGE_ALLIES_ENABLED = BUILDER
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2461-                                .comment("Allow other players to assist the besieger by attacking defenders. They are tracked as allies.")
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2462-                                .define("BesiegeAlliesEnabled", true);
--
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2638-        public static boolean isPrimaryColonyTransferEnabled() {
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2639-                return ENABLE_PRIMARY_COLONY_TRANSFER.get();
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2640-        }
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2641-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2642-        public static int getPrimaryColonyTaxOccupationDays() {
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2643-                return PRIMARY_COLONY_TAX_OCCUPATION_DAYS.get();
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2644-        }
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2645-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java:2646:        public static int getBesiegeSpoilPercentOfLoserTreasury() {
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java:2647:                return BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY.get();
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2648-        }
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2649-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2650-        public static int getWarVassalizationDurationHours() {
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2651-                return WAR_VASSALIZATION_DURATION_HOURS.get();
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2652-        }
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2653-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2654-        public static int getWarVassalizationTributePercentage() {
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-2655-                return WAR_VASSALIZATION_TRIBUTE_PERCENTAGE.get();
--
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3547-        public static int getBesiegeCooldownHours() {
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3548-                return BESIEGE_COOLDOWN_HOURS.get();
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3549-        }
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3550-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3551-        public static double getBesiegeMilitiaPercent() {
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3552-                return BESIEGE_MILITIA_PERCENT.get();
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3553-        }
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3554-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java:3555:        public static int getBesiegeTributePercent() {
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3556-                return BESIEGE_TRIBUTE_PERCENT.get();
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3557-        }
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3558-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java:3559:        public static int getBesiegeTributeDurationHours() {
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3560-                return BESIEGE_TRIBUTE_DURATION_HOURS.get();
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3561-        }
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3562-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3563-        public static int getBesiegeMinColonySize() {
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3564-                return BESIEGE_MIN_COLONY_SIZE.get();
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3565-        }
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3566-
src/main/java/net/machiavelli/minecolonytax/TaxConfig.java-3567-        public static boolean isBesiegeAlliesEnabled() {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 346ms:
186-                found = true;
187-            }
188-        }
189-        if (!found) {
190-            executor.sendSystemMessage(Component.literal("No matching vassalization found."));
191-            return 0;
192-        }
193-        return 1;
194-    }
195-
196-    public static int listVassals(ServerPlayer player) {
197-        UUID id = player.getUUID();
198-        player.sendSystemMessage(Component.literal("Â§6Â§l=== Vassalization Status ==="));
199-
200-        boolean isVassal = false;
201-        for (VassalRelation rel : ACTIVE_VASSALS.values()) {
202:            IColony playerColony = getPrimaryColonyOfPlayer(id);
203-            if (playerColony != null && rel.colonyId == playerColony.getID()) {
204-                isVassal = true;
205-                ServerPlayer overlord = SERVER.getPlayerList().getPlayer(rel.overlordUUID);
206-                String overlordName = overlord != null ? overlord.getGameProfile().getName() : "Unknown";
207-
208-                String currencyName = getCurrencyName();
209-                player.sendSystemMessage(
210-                        Component.literal("Â§c[VASSAL STATUS] Your colony is a vassal to " + overlordName +
211-                                "'s colony (" + rel.percent + "% tribute rate)"));
212-                player.sendSystemMessage(
213-                        Component.literal("Â§c[VASSAL STATUS] Last tribute: " + rel.lastTribute + " " + currencyName));
214-                break;
215-            }
216-        }
217-
218-        if (!isVassal) {
--
253-            ACTIVE_VASSALS.remove(colony.getID());
254-            saveData();
255-
256-            Component expirationMsg = Component.literal(
257-                    "Â§6âš” War Vassalization Ended: The vassalization of colony '" + colony.getName() + "' has expired.");
258-            sendToColonyManagers(colony, expirationMsg);
259-            sendOrQueue(rel.overlordUUID, expirationMsg);
260-            return 0;
261-        }
262-
263-        int tribute = (int) (generatedTax * rel.percent / 100.0);
264-        if (tribute <= 0)
265-            return 0;
266-
267-        TaxManager.adjustTax(colony, -tribute);
268-
269:        IColony overlordColony = getPrimaryColonyOfPlayer(rel.overlordUUID);
270-        if (overlordColony != null) {
271-            TaxManager.adjustTax(overlordColony, tribute);
272-        }
273-        rel.lastPayment = System.currentTimeMillis();
274-        rel.lastTribute = tribute;
275-
276-        ServerPlayer overlordPlayer = SERVER.getPlayerList().getPlayer(rel.overlordUUID);
277-        Component msg = Component
278-                .literal("Received tribute of " + tribute + " coins from vassal colony " + colony.getName());
279-        if (overlordPlayer != null) {
280-            overlordPlayer.sendSystemMessage(msg);
281-        } else {
282-            queueMessage(rel.overlordUUID, msg);
283-        }
284-        return tribute;
285-    }
286-
287-    /**
288-     * Forces a colony to become a vassal as a result of war victory, bypassing the
289-     * normal proposal/acceptance flow. durationHours=0 means permanent.
290-     */
291:    public static boolean forceVassalize(IColony vassalColony, UUID overlordUUID, int tributePercent,
292-            int durationHours) {
293-        if (vassalColony == null || overlordUUID == null) {
294:            LOGGER.warn("forceVassalize called with null colony or overlord");
295-            return false;
296-        }
297-
298-        int colonyId = vassalColony.getID();
299-
300-        if (ACTIVE_VASSALS.containsKey(colonyId)) {
301-            if (TaxConfig.isDebugLogging()) {
302-                LOGGER.info("Colony {} is already a vassal, cannot force vassalize again", vassalColony.getName());
303-            }
304-            return false;
305-        }
306-
307-        long expirationTime = 0;
308-        if (durationHours > 0) {
309-            expirationTime = System.currentTimeMillis() + (durationHours * 60L * 60L * 1000L);
310-        }
311-
312-        VassalRelation rel = new VassalRelation(
313-                colonyId,
314-                overlordUUID,
315-                tributePercent,
316-                System.currentTimeMillis(),
317-                expirationTime,
318-                true // isWarVassalization
319-        );
320-        ACTIVE_VASSALS.put(colonyId, rel);
321-        saveData();
322-
323-        String overlordName = getPlayerName(overlordUUID);
324:        IColony overlordColony = getPrimaryColonyOfPlayer(overlordUUID);
325-        String overlordColonyName = overlordColony != null ? overlordColony.getName() : "Unknown";
326-
327-        String durationMsg;
328-        if (durationHours <= 0) {
329-            durationMsg = "permanently";
330-        } else if (durationHours < 24) {
331-            durationMsg = "for " + durationHours + " hours";
332-        } else {
333-            int days = durationHours / 24;
334-            int remainingHours = durationHours % 24;
335-            if (remainingHours > 0) {
336-                durationMsg = "for " + days + " days and " + remainingHours + " hours";
337-            } else {
338-                durationMsg = "for " + days + " days";
339-            }
340-        }
--
473-        if (player != null) {
474-            player.sendSystemMessage(msg);
475-        } else {
476-            queueMessage(playerId, msg);
477-        }
478-    }
479-
480-    private static void queueMessage(UUID playerId, Component msg) {
481-        OFFLINE_MESSAGES.computeIfAbsent(playerId, k -> new ArrayList<>()).add(msg);
482-    }
483-
484-    private static String getPlayerName(UUID uuid) {
485-        ServerPlayer p = SERVER.getPlayerList().getPlayer(uuid);
486-        return p != null ? p.getName().getString() : uuid.toString();
487-    }
488-
489:    private static IColony getPrimaryColonyOfPlayer(UUID playerId) {
490-        IColonyManager cm = IMinecoloniesAPI.getInstance().getColonyManager();
491-        for (IColony c : cm.getAllColonies()) {
492-            if (c.getPermissions().getOwner().equals(playerId))
493-                return c;
494-        }
495-        return null;
496-    }
497-
498-    private static IColony getColonyById(int colonyId) {
499-        return IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies().stream()
500-                .filter(c -> c.getID() == colonyId)
501-                .findFirst()
502-                .orElse(null);
503-    }
504-
505-    private static void loadData(MinecraftServer server) {
--
696-
697-                    vassalIncomes.add(new VassalIncomeData(
698-                            rel.colonyId,
699-                            colony.getName(),
700-                            rel.percent,
701-                            tributeOwed,
702-                            rel.lastTribute,
703-                            rel.lastPayment,
704-                            false
705-                    ));
706-                }
707-            }
708-        }
709-        return vassalIncomes;
710-    }
711-
712:    public static int claimVassalTribute(UUID overlordId, int vassalColonyId) {
713-        VassalRelation rel = ACTIVE_VASSALS.get(vassalColonyId);
714-        if (rel == null || !rel.overlordUUID.equals(overlordId)) {
715-            return 0;
716-        }
717-
718-        IColony vassalColony = getColonyById(vassalColonyId);
719-        if (vassalColony == null)
720-            return 0;
721-
722-        int currentTaxBalance = TaxManager.getStoredTaxForColony(vassalColony);
723-        int tributeOwed = (int) (currentTaxBalance * rel.percent / 100.0);
724-
725-        if (tributeOwed <= 0)
726-            return 0;
727-
728-        TaxManager.adjustTax(vassalColony, -tributeOwed);
729-
730:        IColony overlordColony = getPrimaryColonyOfPlayer(overlordId);
731-        if (overlordColony != null) {
732-            TaxManager.adjustTax(overlordColony, tributeOwed);
733-        }
734-
735-        rel.lastPayment = System.currentTimeMillis();
736-        rel.lastTribute = tributeOwed;
737-        saveData();
738-
739-        return tributeOwed;
740-    }
741-
742-}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 18 \"IColony besiegerColony = getPrimaryColonyOfPlayer\\(besiegerUUID\\)|No primary colony|besiegerColony == null|canStartBesiege|startBesiege\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 280ms:
152-                    it.remove();
153-                    continue;
154-                }
155-
156-                // --- Update boss bar ---
157-                updateBossBar(raid, colony);
158-
159-            } catch (Exception e) {
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
170:    public static boolean startBesiege(IColony colony, ServerPlayer besieger) {
171-        if (!TaxConfig.isBesiegeSystemEnabled()) {
172-            besieger.sendSystemMessage(Component.literal("The besiege system is disabled on this server.")
173-                    .withStyle(ChatFormatting.RED));
174-            return false;
175-        }
176-
177-        UUID besiegerUUID = besieger.getUUID();
178-        int colonyId = colony.getID();
179-
180-        // 1. Must own at least one colony themselves
181:        IColony besiegerColony = getPrimaryColonyOfPlayer(besiegerUUID);
182:        if (besiegerColony == null) {
183-            besieger.sendSystemMessage(Component.literal("You must own a colony to besiege another.")
184-                    .withStyle(ChatFormatting.RED));
185-            return false;
186-        }
187-
188-        // 2. Cannot besiege own colony
189-        if (colony.getPermissions().getOwner() != null
190-                && colony.getPermissions().getOwner().equals(besiegerUUID)) {
191-            besieger.sendSystemMessage(Component.literal("You cannot besiege your own colony.")
192-                    .withStyle(ChatFormatting.RED));
193-            return false;
194-        }
195-
196-        // 3. Primary colonies CAN now be besieged. Outcome routes through
197-        // OccupationManager in TAX_ONLY mode (deed never moves; auto-reclaim after
198-        // PrimaryColonyTaxOccupationDays). Secondaries continue to use the legacy
199-        // TRANSFER_PENDING flow. See ColonyTierGuard + OccupationManager.
200-
--
554-            }
555-        } else {
556-            applySiegeSpoils(raid, colony, false);
557-            sendToPlayer(raid.besiegingPlayerUUID,
558-                    Component.literal("The besiege of " + colony.getName() + " failed.")
559-                            .withStyle(ChatFormatting.RED));
560-        }
561-    }
562-
563-    /**
564-     * One-shot percentage transfer between loser's and winner's primary colony treasuries.
565-     * Mirrors the war-spoil semantics; configured via BesiegeSpoilPercentOfLoserTreasury.
566-     */
567-    private static void applySiegeSpoils(BesiegeRaidData raid, IColony defenderColony, boolean attackerWon) {
568-        int percent = TaxConfig.getBesiegeSpoilPercentOfLoserTreasury();
569-        if (percent <= 0) return;
570-
571-        IColony besiegerColony = getPrimaryColonyOfPlayer(raid.besiegingPlayerUUID);
572:        if (besiegerColony == null) return;
573-
574-        IColony loser = attackerWon ? defenderColony : besiegerColony;
575-        IColony winner = attackerWon ? besiegerColony : defenderColony;
576-        if (loser == null || winner == null) return;
577-
578-        int loserBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(loser.getID());
579-        if (loserBalance <= 0) return;
580-        int spoil = (int) Math.floor(loserBalance * (percent / 100.0));
581-        if (spoil <= 0) return;
582-
583-        net.machiavelli.minecolonytax.economy.TreasuryManager.deductFromTreasury(loser.getID(), spoil);
584-        net.machiavelli.minecolonytax.economy.TreasuryManager.addToTreasury(winner.getID(), spoil);
585-
586-        if (TaxConfig.isNormalLogging()) {
587-            LOGGER.info("Siege spoils ({}%): {} â†’ {} = {}", percent, loser.getName(), winner.getName(), spoil);
588-        }
589-
590-        // Notify both sides
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 28 "isReclaim|reclaim|VassalManager.isColonyVassal|BesiegeRaidData" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 288ms:
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
50- * Manages the besiege system тАФ single-player raids on active non-primary colonies
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
62-     * raid the same colony тАФ look them up by colony with {@link #getRaidsForColony(int)}.
63-     *
64-     * Phase 2 follow-up: defender pool is still per-raid, not shared across
65-     * concurrent besiegers on the same colony. Each besieger currently spawns its
66-     * own mercs and tracks its own hostile-citizen set; last-kill-credit semantics
67-     * are not yet implemented.
68-     */
69:    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
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
87:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
88-            cleanupRaid(raid, false);
89-        }
90-        ACTIVE_RAIDS.clear();
91-        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager shutdown complete");
92-    }
93-
94-    public static void tick() {
95-        if (ACTIVE_RAIDS.isEmpty()) return;
96-
97:        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
98:            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
99:            BesiegeRaidData raid = entry.getValue();
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
115-                        LOGGER.info("Besiege raid on colony {} timed out тАФ defenders win", colony.getName());
116-                    sendToPlayer(raid.besiegingPlayerUUID,
117-                            Component.literal("The besiege of " + colony.getName() + " has failed тАФ the defenders held out!")
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
--
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
180-        // 1. Must own at least one colony themselves
181-        IColony besiegerColony = getPrimaryColonyOfPlayer(besiegerUUID);
182-        if (besiegerColony == null) {
183-            besieger.sendSystemMessage(Component.literal("You must own a colony to besiege another.")
184-                    .withStyle(ChatFormatting.RED));
185-            return false;
186-        }
187-
188-        // 2. Cannot besiege own colony
189-        if (colony.getPermissions().getOwner() != null
190-                && colony.getPermissions().getOwner().equals(besiegerUUID)) {
191-            besieger.sendSystemMessage(Component.literal("You cannot besiege your own colony.")
192-                    .withStyle(ChatFormatting.RED));
193-            return false;
194-        }
195-
196-        // 3. Primary colonies CAN now be besieged. Outcome routes through
197:        // OccupationManager in TAX_ONLY mode (deed never moves; auto-reclaim after
198-        // PrimaryColonyTaxOccupationDays). Secondaries continue to use the legacy
199-        // TRANSFER_PENDING flow. See ColonyTierGuard + OccupationManager.
200-
201-        // 4. Solo rule: this besieger may not already have an active raid elsewhere.
202-        // Multiple besiegers attacking the SAME colony concurrently is allowed.
203-        if (ACTIVE_RAIDS.containsKey(besiegerUUID)) {
204-            besieger.sendSystemMessage(Component.literal(
205-                    "You already have an active besiege. Only one besiege at a time per player.")
206-                    .withStyle(ChatFormatting.RED));
207-            return false;
208-        }
209-
210-        // 5. Cooldown check
211-        Long cooldownExpiry = PLAYER_COOLDOWNS.get(besiegerUUID);
212-        if (cooldownExpiry != null && System.currentTimeMillis() < cooldownExpiry) {
213-            long remaining = (cooldownExpiry - System.currentTimeMillis()) / 60000;
214-            besieger.sendSystemMessage(Component.literal(
215-                    "You must wait " + remaining + " more minute(s) before besieging again.")
216-                    .withStyle(ChatFormatting.RED));
217-            return false;
218-        }
219-
220-        // 6. Min colony size
221-        int citizenCount = colony.getCitizenManager().getCitizens().size();
222-        if (citizenCount < TaxConfig.getBesiegeMinColonySize()) {
223-            besieger.sendSystemMessage(Component.literal(
224-                    "Target colony is too small to besiege (needs at least "
225-                            + TaxConfig.getBesiegeMinColonySize() + " citizens).")
226-                    .withStyle(ChatFormatting.RED));
227-            return false;
228-        }
229-
230-        // 7. Cannot besiege a colony that is currently in an active war
231-        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
232-            besieger.sendSystemMessage(Component.literal(
233-                    "This colony is already engaged in an active war. Besiege is not available during wartime.")
234-                    .withStyle(ChatFormatting.RED));
235-            return false;
236-        }
237-
238-        // 8. Cannot besiege a colony that is already a vassal of this player
239:        if (VassalManager.isColonyVassal(colonyId)
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
251:     * Start a reclaim raid тАФ former owner/officer taking back their besieged colony.
252-     */
253:    public static boolean startReclaim(IColony colony, ServerPlayer reclaimingPlayer) {
254-        if (!TaxConfig.isBesiegeSystemEnabled()) {
255:            reclaimingPlayer.sendSystemMessage(Component.literal("The besiege system is disabled.")
256-                    .withStyle(ChatFormatting.RED));
257-            return false;
258-        }
259-
260-        int colonyId = colony.getID();
261-        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
262-        if (occ == null) {
263:            reclaimingPlayer.sendSystemMessage(Component.literal("This colony is not under besiege occupation.")
264-                    .withStyle(ChatFormatting.RED));
265-            return false;
266-        }
267-
268:        UUID playerUUID = reclaimingPlayer.getUUID();
269-        if (!occ.formerOwnerUUID.equals(playerUUID)) {
270:            reclaimingPlayer.sendSystemMessage(Component.literal(
271:                    "Only the former owner can reclaim this colony via besiege.")
272-                    .withStyle(ChatFormatting.RED));
273-            return false;
274-        }
275-
276-        // Solo rule: this player may not already have an active raid.
277-        if (ACTIVE_RAIDS.containsKey(playerUUID)) {
278:            reclaimingPlayer.sendSystemMessage(Component.literal(
279:                    "You already have an active besiege/reclaim raid.")
280-                    .withStyle(ChatFormatting.RED));
281-            return false;
282-        }
283-
284-        // Cooldown check
285-        Long cooldownExpiry = PLAYER_COOLDOWNS.get(playerUUID);
286-        if (cooldownExpiry != null && System.currentTimeMillis() < cooldownExpiry) {
287-            long remaining = (cooldownExpiry - System.currentTimeMillis()) / 60000;
288:            reclaimingPlayer.sendSystemMessage(Component.literal(
289:                    "You must wait " + remaining + " more minute(s) before attempting a reclaim.")
290-                    .withStyle(ChatFormatting.RED));
291-            return false;
292-        }
293-
294:        reclaimingPlayer.sendSystemMessage(Component.literal(
295:                "Reclaim raid started! Kill all defenders to reclaim " + colony.getName() + ".")
296-                .withStyle(ChatFormatting.GOLD));
297:        return launchRaid(colony, reclaimingPlayer, true);
298-    }
299-
300-
301:    private static boolean launchRaid(IColony colony, ServerPlayer besieger, boolean isReclaim) {
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
330:            String verb = isReclaim ? "RECLAIM RAID" : "BESIEGE";
331-            besieger.sendSystemMessage(Component.literal(
332-                    verb + " STARTED: " + colony.getName()
333-                            + " | Defenders: " + totalDefenders
334-                            + " | Time: " + TaxConfig.getBesiegeDurationMinutes() + "m")
335-                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
336-
337-            broadcastToNearbyPlayers(colony,
338-                    Component.literal("Nearby colony " + colony.getName()
339:                            + " is under " + (isReclaim ? "reclaim attack" : "siege")
340-                            + " by " + besieger.getName().getString() + "!")
341-                            .withStyle(ChatFormatting.YELLOW), 200);
342-
343-            // Notify owner + officers + friends тАФ the defender's call-to-arms.
344-            // Friends are included per the Siege SMP defender-ally rule: defenders may
345-            // mobilize allies even when the attacker must stand alone.
346-            BlockPos colonyCenter = colony.getCenter();
347-            net.minecraft.network.chat.Component callToArms = Component.literal("WARNING: ")
348-                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
349-                    .append(Component.literal(besieger.getName().getString())
350-                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
351-                    .append(Component.literal(" is besieging your colony ")
352-                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
353-                    .append(Component.literal(colony.getName())
354-                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
355-                    .append(Component.literal("! ")
356-                            .withStyle(ChatFormatting.DARK_RED))
357-                    .append(Component.literal("[Defend it]")
358-                            .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
359-                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
360-                                            net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
361-                                            "/tp " + colonyCenter.getX() + " " + colonyCenter.getY() + " " + colonyCenter.getZ()))
362-                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
363-                                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
364-                                            Component.literal("Teleport near the colony center to join the defense")))));
365-            notifyColonyDefenders(colony, callToArms);
366-
367-            if (TaxConfig.isNormalLogging())
368-                LOGGER.info("Besiege {} started on colony {} ({}) by {} with {} total defenders ({}g {}m {}e)",
369:                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
370-                        besieger.getName().getString(), totalDefenders, guardCount, militiaCount, mercCount);
371-
372-            return true;
373-
374-        } catch (Exception e) {
375-            LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
376-            ACTIVE_RAIDS.remove(besiegerUUID);
377-            return false;
378-        }
379-    }
380-
381-
382:    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
383-        int count = 0;
384-        for (ICitizenData citizenData : colony.getCitizenManager().getCitizens()) {
385-            if (citizenData.getJob() == null || !citizenData.getJob().isGuard()) continue;
386-            Optional<AbstractEntityCitizen> entityOpt = citizenData.getEntity();
387-            if (entityOpt.isEmpty()) continue;
388-            AbstractEntityCitizen guard = entityOpt.get();
389-
390-            try {
391-                applyDefenderAI(guard, besieger, raid);
392-                applyDefenderEffects(guard, TaxConfig.getBesiegeDurationMinutes());
393-                raid.hostileCitizenIds.add(citizenData.getId());
394-                count++;
395-            } catch (Exception e) {
396-                LOGGER.warn("Failed to make guard {} hostile during besiege", citizenData.getName(), e);
397-            }
398-        }
399-        return count;
400-    }
401-
402:    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
403-        List<ICitizenData> eligible = new ArrayList<>();
404-        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
405-            if (citizen.isChild()) continue;
406-            if (citizen.getEntity().isEmpty()) continue;
407-            if (citizen.getJob() != null && citizen.getJob().isGuard()) continue;
408-            if (citizen.getJob() != null) {
409-                String jobPath = citizen.getJob().getJobRegistryEntry().getKey().getPath();
410-                if (jobPath.equals("deliveryman")) continue;
411-            }
412-            eligible.add(citizen);
413-        }
414-
415-        Collections.shuffle(eligible);
416-        int target = (int) Math.ceil(eligible.size() * TaxConfig.getBesiegeMilitiaPercent());
417-        int count = 0;
418-
419-        for (ICitizenData citizen : eligible) {
420-            if (count >= target) break;
421-            Optional<AbstractEntityCitizen> entityOpt = citizen.getEntity();
422-            if (entityOpt.isEmpty()) continue;
423-            AbstractEntityCitizen entity = entityOpt.get();
424-
425-            try {
426-                // Equip wooden sword
427-                entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
428-                applyDefenderAI(entity, besieger, raid);
429-                applyDefenderEffects(entity, TaxConfig.getBesiegeDurationMinutes());
430-                raid.hostileCitizenIds.add(citizen.getId());
431-                count++;
432-            } catch (Exception e) {
433-                LOGGER.warn("Failed to convert citizen {} to militia during besiege", citizen.getName(), e);
434-            }
435-        }
436-        return count;
437-    }
438-
439:    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
440-        entity.goalSelector.removeAllGoals(g -> true);
441-        entity.targetSelector.removeAllGoals(g -> true);
442-
443-        // Use MilitiaAttackGoal instead of vanilla MeleeAttackGoal тАФ non-guard citizens
444-        // lack the ATTACK_DAMAGE attribute, which causes MeleeAttackGoal.doHurtTarget() to
445-        // crash with IllegalArgumentException.
446-        entity.goalSelector.addGoal(0, new MilitiaAttackGoal(entity, 1.2D));
447-
448-        // Retaliate against anyone who hits them (covers allies)
449-        entity.targetSelector.addGoal(0, new HurtByTargetGoal(entity));
450-
451-        // Proactively hunt the besieger (and any allies)
452-        entity.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(entity, Player.class,
453-                20, true, false, (target) -> {
454-                    if (!(target instanceof ServerPlayer sp)) return false;
455-                    return sp.getUUID().equals(besieger.getUUID())
456-                            || raid.alliedPlayers.contains(sp.getUUID());
457-                }));
458-    }
459-
460-    private static void applyDefenderEffects(AbstractEntityCitizen entity, int durationMinutes) {
461-        int ticks = durationMinutes * 60 * 20;
462-        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1));
463-        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
464-        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
465-    }
466-
467-    /**
468-     * Applies bonus DAMAGE_RESISTANCE to all besiege defenders based on the
469-     * colony's FORTIFICATION investment level. Called once after all defenders
470-     * are spawned in launchRaid().
471-     * Each 20% damage reduction from the investment adds +1 resistance amplifier.
472-     */
473:    private static void applyFortificationBonus(IColony colony, BesiegeRaidData raid) {
474-        if (!TaxConfig.isUpgradesEnabled()) return;
475-        double dmgReduction = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
476-                .getFortificationDamageReduction(colony.getID());
477-        if (dmgReduction <= 0) return;
478-        int extraAmplifier = (int) (dmgReduction / 0.20);
479-        if (extraAmplifier <= 0) return;
480-
481-        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
482-        // Re-apply DAMAGE_RESISTANCE to citizens with the boosted amplifier
483-        for (int citizenId : raid.hostileCitizenIds) {
484-            colony.getCitizenManager().getCitizens().stream()
485-                    .filter(c -> c.getId() == citizenId)
486-                    .findFirst()
487-                    .flatMap(ICitizenData::getEntity)
488-                    .ifPresent(entity -> entity.addEffect(
489-                            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier)));
490-        }
491-        // Also boost mercenaries
492-        for (Entity merc : raid.spawnedMercenaries) {
493-            if (merc instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
494-                living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1 + extraAmplifier));
495-            }
496-        }
497-        if (TaxConfig.isDebugLogging())
498-            LOGGER.debug("FORTIFICATION bonus applied to colony {} defenders (extra resistance amplifier: {})",
499-                    colony.getName(), extraAmplifier);
500-    }
501-
502:    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
503-        Level world = colony.getWorld();
504-        if (!(world instanceof ServerLevel)) return 0;
505-
506-        int buildingCount = 0;
507-        try {
508-            buildingCount = net.machiavelli.minecolonytax.compat.ColonyBuildingUtil
509-                    .getBuildings(colony).size();
510-        } catch (Exception e) {
511-            LOGGER.warn("Could not count buildings for mercenary calc in colony {}", colony.getName());
512-        }
513-
514-        int count = (int) (buildingCount * TaxConfig.getBesiegeExtraMercenariesPerBuilding());
515-        count = Math.min(count, TaxConfig.getBesiegeMaxMercenaries());
516-        if (count <= 0) return 0;
517-
518-        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
519-        int spawned = 0;
520-
521-        for (int i = 0; i < count; i++) {
522-            try {
523-                EntityMercenary merc = (EntityMercenary) com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
524-                if (merc == null) continue;
525-
526-                BlockPos spawnPos = findSpawnPos(colony.getCenter(), world);
527-                merc.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
528-                merc.setTarget(besieger);
529-
530-                merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1));
531-                merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, durationTicks, 0));
532-
533-                world.addFreshEntity(merc);
534-                raid.spawnedMercenaries.add(merc);
535-                spawned++;
536-            } catch (Exception e) {
537-                LOGGER.warn("Failed to spawn mercenary {} for besiege on colony {}", i, colony.getName(), e);
538-            }
539-        }
540-        return spawned;
541-    }
542-
543-
544:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
545-        cleanupRaid(raid, true);
546-        applyCooldown(raid.besiegingPlayerUUID);
547-
548-        if (attackerWon) {
549-            applySiegeSpoils(raid, colony, true);
550:            if (raid.isReclaim) {
551-                completeReclaim(raid, colony);
552-            } else {
553-                completeBesiegeVictory(raid, colony);
554-            }
555-        } else {
556-            applySiegeSpoils(raid, colony, false);
557-            sendToPlayer(raid.besiegingPlayerUUID,
558-                    Component.literal("The besiege of " + colony.getName() + " failed.")
559-                            .withStyle(ChatFormatting.RED));
560-        }
561-    }
562-
563-    /**
564-     * One-shot percentage transfer between loser's and winner's primary colony treasuries.
565-     * Mirrors the war-spoil semantics; configured via BesiegeSpoilPercentOfLoserTreasury.
566-     */
567:    private static void applySiegeSpoils(BesiegeRaidData raid, IColony defenderColony, boolean attackerWon) {
568-        int percent = TaxConfig.getBesiegeSpoilPercentOfLoserTreasury();
569-        if (percent <= 0) return;
570-
571-        IColony besiegerColony = getPrimaryColonyOfPlayer(raid.besiegingPlayerUUID);
572-        if (besiegerColony == null) return;
573-
574-        IColony loser = attackerWon ? defenderColony : besiegerColony;
575-        IColony winner = attackerWon ? besiegerColony : defenderColony;
576-        if (loser == null || winner == null) return;
577-
578-        int loserBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(loser.getID());
579-        if (loserBalance <= 0) return;
580-        int spoil = (int) Math.floor(loserBalance * (percent / 100.0));
581-        if (spoil <= 0) return;
582-
583-        net.machiavelli.minecolonytax.economy.TreasuryManager.deductFromTreasury(loser.getID(), spoil);
584-        net.machiavelli.minecolonytax.economy.TreasuryManager.addToTreasury(winner.getID(), spoil);
585-
586-        if (TaxConfig.isNormalLogging()) {
587-            LOGGER.info("Siege spoils ({}%): {} тЖТ {} = {}", percent, loser.getName(), winner.getName(), spoil);
588-        }
589-
590-        // Notify both sides
591-        UUID winnerOwner = winner.getPermissions().getOwner();
592-        UUID loserOwner = loser.getPermissions().getOwner();
593-        Component winMsg = Component.literal("Siege spoils: " + spoil + " coins transferred from "
594-                + loser.getName() + " to " + winner.getName() + ".")
595-                .withStyle(ChatFormatting.GOLD);
596-        Component loseMsg = Component.literal("Siege fine: " + spoil + " coins paid from "
597-                + loser.getName() + " to " + winner.getName() + ".")
598-                .withStyle(ChatFormatting.RED);
599-        if (winnerOwner != null) sendToPlayer(winnerOwner, winMsg);
600-        if (loserOwner != null && !loserOwner.equals(winnerOwner)) sendToPlayer(loserOwner, loseMsg);
601-    }
602-
603:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
604-        int tributePct = TaxConfig.getBesiegeTributePercent();
605-        int durationHours = TaxConfig.getBesiegeTributeDurationHours();
606-
607-        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
608-
609-        if (vassalized) {
610-            // Store occupation record
611-            UUID ownerUUID = colony.getPermissions().getOwner();
612-            BesiegeOccupationData occ = new BesiegeOccupationData(
613-                    colony.getID(), colony.getName(),
614-                    raid.besiegingPlayerUUID, ownerUUID,
615-                    System.currentTimeMillis(), tributePct);
616-            OCCUPATIONS.put(colony.getID(), occ);
617-            saveData();
618-
619-            // Notify former owner
620-            if (ownerUUID != null) {
621-                String besiegerName = getPlayerName(raid.besiegingPlayerUUID);
622-                sendToPlayer(ownerUUID, Component.literal(
623-                        "Your colony " + colony.getName() + " has been besieged by " + besiegerName
624-                                + "! Tax tribute (" + tributePct + "%) now flows to them. "
625:                                + "Use /wnt besiege " + colony.getName() + " to reclaim it.")
626-                        .withStyle(ChatFormatting.RED));
627-            }
628-
629-            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
630-                    "Besiege victory! " + colony.getName() + " now pays you " + tributePct
631-                            + "% in tribute" + (durationHours > 0 ? " for " + durationHours + "h" : "") + ".")
632-                    .withStyle(ChatFormatting.GREEN));
633-
634-            broadcastToNearbyPlayers(colony,
635-                    Component.literal(colony.getName() + " has fallen under besiege occupation!")
636-                            .withStyle(ChatFormatting.DARK_RED), 300);
637-
638-            if (TaxConfig.isNormalLogging())
639-                LOGGER.info("Besiege victory: colony {} vassalized to {} at {}% tribute",
640-                        colony.getName(), getPlayerName(raid.besiegingPlayerUUID), tributePct);
641-        } else {
642-            // Colony was already a vassal (edge case) тАФ just notify
643-            sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
644-                    "Besiege raid completed but vassalization could not be established (colony may already be a vassal).")
645-                    .withStyle(ChatFormatting.YELLOW));
646-        }
647-    }
648-
649:    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
650-        int colonyId = colony.getID();
651-
652-        // Remove vassalization
653-        VassalManager.removeVassalRelation(colonyId);
654-
655-        // Remove occupation record
656-        OCCUPATIONS.remove(colonyId);
657-        saveData();
658-
659-        sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
660-                "Reclaim successful! " + colony.getName() + " is free from occupation.")
661-                .withStyle(ChatFormatting.GREEN));
662-
663-        broadcastToNearbyPlayers(colony,
664:                Component.literal(colony.getName() + " has been reclaimed by its owner!")
665-                        .withStyle(ChatFormatting.GOLD), 300);
666-
667-        if (TaxConfig.isNormalLogging())
668:            LOGGER.info("Besiege reclaim: colony {} reclaimed by {}", colony.getName(),
669-                    getPlayerName(raid.besiegingPlayerUUID));
670-    }
671-
672-
673:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
674-        IColony colony = getColonyById(raid.colonyId);
675-        if (colony != null) {
676-            // Revoke combat permissions from the besieger (and any allies)
677-            revokeBesiegeCombatPermissions(colony, raid.besiegingPlayerUUID);
678-            for (UUID ally : raid.alliedPlayers) {
679-                revokeBesiegeCombatPermissions(colony, ally);
680-            }
681-
682-            // Restore citizen AI
683-            for (int citizenId : raid.hostileCitizenIds) {
684-                try {
685-                    ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
686-                    if (citizen != null && citizen.getEntity().isPresent()) {
687-                        AbstractEntityCitizen entity = citizen.getEntity().get();
688-                        entity.goalSelector.removeAllGoals(g -> true);
689-                        entity.targetSelector.removeAllGoals(g -> true);
690-                        // Remove militia sword if present
691-                        if (entity.getMainHandItem().getItem() == Items.WOODEN_SWORD) {
692-                            entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
693-                        }
694-                        // Remove combat effects
695-                        entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
696-                        entity.removeEffect(MobEffects.MOVEMENT_SPEED);
697-                        entity.removeEffect(MobEffects.DAMAGE_BOOST);
698-                        // Restore job AI
699-                        if (citizen.getJob() != null) {
700-                            entity.getCitizenJobHandler().onJobChanged(citizen.getJob());
701-                        }
--
706-            }
707-
708-            // Despawn mercenaries
709-            for (Entity merc : raid.spawnedMercenaries) {
710-                try {
711-                    if (merc.isAlive()) merc.remove(Entity.RemovalReason.DISCARDED);
712-                } catch (Exception e) {
713-                    LOGGER.warn("Failed to despawn mercenary after besiege", e);
714-                }
715-            }
716-        }
717-
718-        // Remove boss bar
719-        if (raid.bossEvent != null) {
720-            try {
721-                raid.bossEvent.removeAllPlayers();
722-            } catch (Exception e) {
723-                LOGGER.warn("Failed to remove besiege boss bar", e);
724-            }
725-            raid.bossEvent = null;
726-        }
727-
728-        if (removeFromMap) {
729-            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
730-        }
731-    }
732-
733-
734:    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
735-        try {
736-            int minutes = TaxConfig.getBesiegeDurationMinutes();
737-            Component text = Component.literal(
738-                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
739-                            colony.getName(), totalDefenders, minutes, 0))
740-                    .withStyle(ChatFormatting.YELLOW);
741-
742-            raid.bossEvent = new ServerBossEvent(text, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
743-            raid.bossEvent.setProgress(1.0f);
744-            raid.bossEvent.addPlayer(besieger);
745-
746-            // Add nearby players
747-            Level world = colony.getWorld();
748-            if (world instanceof ServerLevel serverLevel) {
749-                BlockPos center = colony.getCenter();
750-                for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
751-                    if (!player.equals(besieger) && player.level() == world) {
752-                        double dist = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
753-                        if (dist <= 200.0 * 200.0) {
754-                            try { raid.bossEvent.addPlayer(player); } catch (Exception ignored) {}
755-                        }
756-                    }
757-                }
758-            }
759-        } catch (Exception e) {
760-            LOGGER.error("Failed to create boss bar for besiege on colony {}", colony.getName(), e);
761-        }
762-    }
763-
764:    private static void updateBossBar(BesiegeRaidData raid, IColony colony) {
765-        if (raid.bossEvent == null) return;
766-
767-        try {
768-            long remaining = Math.max(0, raid.endTime - System.currentTimeMillis());
769-            float progress = (float) remaining / (float) (TaxConfig.getBesiegeDurationMinutes() * 60_000L);
770-            progress = Math.max(0f, Math.min(1f, progress));
771-
772-            long seconds = remaining / 1000;
773-            long mm = seconds / 60;
774-            long ss = seconds % 60;
775-
776-            int aliveDefenders = countAliveDefenders(raid, colony);
777-
778-            BossEvent.BossBarColor color = progress > 0.6f ? BossEvent.BossBarColor.GREEN
779-                    : progress > 0.3f ? BossEvent.BossBarColor.YELLOW
780-                    : BossEvent.BossBarColor.RED;
781-
782-            raid.bossEvent.setColor(color);
783-            raid.bossEvent.setProgress(progress);
784-            raid.bossEvent.setName(Component.literal(
785-                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
786-                            colony.getName(), aliveDefenders, mm, ss)));
787-        } catch (Exception e) {
788-            LOGGER.warn("Failed to update besiege boss bar", e);
789-        }
790-    }
791-
792-
793-    public static boolean isColonyBesieged(int colonyId) {
794-        return OCCUPATIONS.containsKey(colonyId);
795-    }
796-
797-    public static boolean isActiveRaidOnColony(int colonyId) {
798:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
799-            if (raid.colonyId == colonyId) return true;
800-        }
801-        return false;
802-    }
803-
804-    /** All currently active besiege raids targeting this colony. */
805:    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
806:        List<BesiegeRaidData> matches = new ArrayList<>();
807:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
808-            if (raid.colonyId == colonyId) matches.add(raid);
809-        }
810-        return matches;
811-    }
812-
813-    /**
814-     * Returns true if the player is locked out of the colony due to besiege occupation.
815-     * The former owner is locked out; the besieging player is the new effective controller.
816-     */
817-    public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
818-        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
819-        if (occ == null) return false;
820-        // Former owner is locked out
821-        return occ.formerOwnerUUID != null && occ.formerOwnerUUID.equals(playerUUID);
822-    }
823-
824-    public static boolean isBesiegingPlayer(UUID playerUUID, int colonyId) {
825-        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
826-        return occ != null && occ.besiegingPlayerUUID.equals(playerUUID);
827-    }
828-
829-    /**
830-     * Called from RaidKillTracker to register an allied player to ALL raids
831-     * targeting this colony. With multi-besieger, several besiegers may target
832-     * the same colony тАФ registering the ally on each gives them combat rights
833-     * regardless of which besieger they're supporting.
834-     *
835-     * Note: the new Siege SMP solo-besiege rule (step 5) blocks attacker-side
836-     * allies via a damage shield. This method remains for any defender-ally
837-     * tracking and for legacy callers; the besiege-allies config still gates it.
838-     */
839-    public static void registerAlly(int colonyId, UUID allyUUID) {
840-        if (!TaxConfig.isBesiegeAlliesEnabled()) return;
841:        List<BesiegeRaidData> raids = getRaidsForColony(colonyId);
842-        if (raids.isEmpty()) return;
843-        IColony colony = getColonyById(colonyId);
844:        for (BesiegeRaidData raid : raids) {
845-            if (raid.alliedPlayers.add(allyUUID) && colony != null) {
846-                grantBesiegeCombatPermissions(colony, allyUUID);
847-            }
848-        }
849-    }
850-
851-    /** Check whether a player is on cooldown. */
852-    public static boolean isOnCooldown(UUID playerUUID) {
853-        Long expiry = PLAYER_COOLDOWNS.get(playerUUID);
854-        return expiry != null && System.currentTimeMillis() < expiry;
855-    }
856-
857-    public static BesiegeOccupationData getOccupation(int colonyId) {
858-        return OCCUPATIONS.get(colonyId);
859-    }
860-
861-    /**
862-     * Backward-compatible view of active raids keyed by colonyId.
863-     *
864-     * Since multi-besieger support landed, the internal storage is keyed by
865-     * besieger UUID. This view returns at most ONE raid per colony (the first
866-     * one encountered). Callers that need ALL raids for a colony must use
867-     * {@link #getRaidsForColony(int)}; callers that need a specific besieger's
868-     * raid should use {@link #getRaidForBesieger(UUID)}.
869-     */
870:    public static Map<Integer, BesiegeRaidData> getActiveRaids() {
871:        Map<Integer, BesiegeRaidData> view = new HashMap<>();
872:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
873-            view.putIfAbsent(raid.colonyId, raid);
874-        }
875-        return Collections.unmodifiableMap(view);
876-    }
877-
878-    /** Direct lookup by besieger UUID. Null when this player has no active raid. */
879:    public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
880-        return ACTIVE_RAIDS.get(besiegerUUID);
881-    }
882-
883-    /** Read-only view of all active raids keyed by besieger UUID. */
884:    public static Map<UUID, BesiegeRaidData> getAllActiveRaidsByBesieger() {
885-        return Collections.unmodifiableMap(ACTIVE_RAIDS);
886-    }
887-
888-    public static Map<Integer, BesiegeOccupationData> getAllOccupations() {
889-        return Collections.unmodifiableMap(OCCUPATIONS);
890-    }
891-
892-
893:    private static boolean allDefendersDead(BesiegeRaidData raid, IColony colony) {
894-        return countAliveDefenders(raid, colony) == 0;
895-    }
896-
897:    private static int countAliveDefenders(BesiegeRaidData raid, IColony colony) {
898-        int alive = 0;
899-        // Citizens
900-        for (int citizenId : raid.hostileCitizenIds) {
901-            try {
902-                ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
903-                if (citizen != null && citizen.getEntity().isPresent()
904-                        && citizen.getEntity().get().isAlive()) {
905-                    alive++;
906-                }
907-            } catch (Exception ignored) {}
908-        }
909-        // Mercenaries
910-        for (Entity merc : raid.spawnedMercenaries) {
911-            if (merc.isAlive()) alive++;
912-        }
913-        return alive;
914-    }
915-
916-    private static void applyCooldown(UUID playerUUID) {
917-        long cooldownMs = TaxConfig.getBesiegeCooldownHours() * 3600_000L;
918-        PLAYER_COOLDOWNS.put(playerUUID, System.currentTimeMillis() + cooldownMs);
919-    }
920-
921-    private static BlockPos findSpawnPos(BlockPos center, Level world) {
922-        Random rng = new Random();
923-        for (int attempt = 0; attempt < 10; attempt++) {
924-            int x = center.getX() + rng.nextInt(20) - 10;
925-            int z = center.getZ() + rng.nextInt(20) - 10;
--
1110-            }
1111-            if (TaxConfig.isNormalLogging())
1112-                LOGGER.info("Loaded {} besiege occupation record(s)", OCCUPATIONS.size());
1113-        } catch (Exception e) {
1114-            LOGGER.error("Failed to load besiege occupation data", e);
1115-        }
1116-    }
1117-
1118-    private static void saveData() {
1119-        if (SERVER == null) return;
1120-        // Snapshot on the calling (server) thread.
1121-        final List<BesiegeOccupationData> list = new ArrayList<>(OCCUPATIONS.values());
1122-        final File f = new File(SERVER.getServerDirectory(), STORAGE_FILE);
1123-
1124-        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("besiege", () -> {
1125-            try {
1126-                f.getParentFile().mkdirs();
1127-                try (FileWriter w = new FileWriter(f)) {
1128-                    GSON.toJson(list, w);
1129-                }
1130-            } catch (Exception e) {
1131-                LOGGER.error("Failed to save besiege occupation data", e);
1132-            }
1133-        });
1134-    }
1135-
1136-
1137-    /** Transient raid state тАФ not persisted. */
1138:    public static class BesiegeRaidData {
1139-        public final int colonyId;
1140-        public final UUID besiegingPlayerUUID;
1141-        public final long startTime;
1142-        public final long endTime;
1143-        public final Set<Integer> hostileCitizenIds = ConcurrentHashMap.newKeySet();
1144-        public final Set<Entity> spawnedMercenaries = ConcurrentHashMap.newKeySet();
1145-        public final Set<UUID> alliedPlayers = ConcurrentHashMap.newKeySet();
1146-        public final BlockPos colonyCenter;
1147-        public ServerBossEvent bossEvent;
1148:        public final boolean isReclaim;
1149-
1150:        public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
1151-            this.colonyId = colonyId;
1152-            this.besiegingPlayerUUID = besiegingPlayerUUID;
1153-            this.colonyCenter = colonyCenter;
1154:            this.isReclaim = isReclaim;
1155-            this.startTime = System.currentTimeMillis();
1156-            this.endTime = startTime + (long) TaxConfig.getBesiegeDurationMinutes() * 60_000L;
1157-        }
1158-    }
1159-
1160-    /** Persisted occupation record тАФ survives server restart. */
1161-    public static class BesiegeOccupationData {
1162-        public int colonyId;
1163-        public String colonyName;
1164-        public UUID besiegingPlayerUUID;
1165-        public UUID formerOwnerUUID;
1166-        public long besiegeTime;
1167-        public int tributePercent;
1168-
1169-        // For Gson deserialization
1170-        public BesiegeOccupationData() {}
1171-
1172-        public BesiegeOccupationData(int colonyId, String colonyName, UUID besiegingPlayerUUID,
1173-                UUID formerOwnerUUID, long besiegeTime, int tributePercent) {
1174-            this.colonyId = colonyId;
1175-            this.colonyName = colonyName;
1176-            this.besiegingPlayerUUID = besiegingPlayerUUID;
1177-            this.formerOwnerUUID = formerOwnerUUID;
1178-            this.besiegeTime = besiegeTime;
1179-            this.tributePercent = tributePercent;
1180-        }
1181-    }
1182-}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fБr 
Kerntypen unterstБtzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 10 \"completeBesiege\\(\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 277ms:
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
151:                    completeBesiege(raid, true, colony);
152-                    it.remove();
153-                    continue;
154-                }
155-
156-                // --- Update boss bar ---
157-                updateBossBar(raid, colony);
158-
159-            } catch (Exception e) {
160-                LOGGER.error("Error ticking besiege raid for colony {}", entry.getKey(), e);
161-            }
--
534-                raid.spawnedMercenaries.add(merc);
535-                spawned++;
536-            } catch (Exception e) {
537-                LOGGER.warn("Failed to spawn mercenary {} for besiege on colony {}", i, colony.getName(), e);
538-            }
539-        }
540-        return spawned;
541-    }
542-
543-
544:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
545-        cleanupRaid(raid, true);
546-        applyCooldown(raid.besiegingPlayerUUID);
547-
548-        if (attackerWon) {
549-            applySiegeSpoils(raid, colony, true);
550-            if (raid.isReclaim) {
551-                completeReclaim(raid, colony);
552-            } else {
553-                completeBesiegeVictory(raid, colony);
554-            }
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
STATUS: CHANGES REQUESTED

- Defender victory spoils likely never run: timeout path in `tick()` cleans up directly and does not call `completeBesiege(..., false)`, so the reversed transfer and notifications are skipped on the normal defender-win path.
- Rounding uses floor, so small balances can produce zero spoils. That is defensible, but `addToTreasury` caps at treasury capacity while `deductFromTreasury` already removed the full amount, so coins can disappear and the notification can overstate the transfer.
- Normal attacker/defender selection looks correct for plain besiege. Reclaim is suspect: a successful reclaim treats the occupied colony as loser and the former owner’s primary as winner, which can be the same colony or the wrong losing side if the intended loser is the occupier.
- No-primary behavior is silent skip. Normal besiege start blocks this, but reclaim does not; if the primary disappears mid-raid, spoils just do not happen.

Fixes:
1. Route timeout through `completeBesiege(raid, false, colony)` or explicitly call `applySiegeSpoils(..., false)` there.
2. Compute actual credited amount before deduction, respecting winner capacity; deduct and notify only that amount.
tokens used
58.844
STATUS: CHANGES REQUESTED

- Defender victory spoils likely never run: timeout path in `tick()` cleans up directly and does not call `completeBesiege(..., false)`, so the reversed transfer and notifications are skipped on the normal defender-win path.
- Rounding uses floor, so small balances can produce zero spoils. That is defensible, but `addToTreasury` caps at treasury capacity while `deductFromTreasury` already removed the full amount, so coins can disappear and the notification can overstate the transfer.
- Normal attacker/defender selection looks correct for plain besiege. Reclaim is suspect: a successful reclaim treats the occupied colony as loser and the former owner’s primary as winner, which can be the same colony or the wrong losing side if the intended loser is the occupier.
- No-primary behavior is silent skip. Normal besiege start blocks this, but reclaim does not; if the primary disappears mid-raid, spoils just do not happen.

Fixes:
1. Route timeout through `completeBesiege(raid, false, colony)` or explicitly call `applySiegeSpoils(..., false)` there.
2. Compute actual credited amount before deduction, respecting winner capacity; deduct and notify only that amount.
