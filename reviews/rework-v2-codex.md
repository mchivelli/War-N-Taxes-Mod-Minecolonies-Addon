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
session id: 019e5d03-de85-7c61-b3a0-4b1f432bf544
--------
user
Second rework pass — focused fix for the two findings from the previous rework review. Verify: (A) the new defendersWouldWin condition in triggerVictory matches WarSystem.checkForVictory's defender-win path exactly, (B) startOccupation's FCT-first classification matches ColonyTierGuard's logic. Respond: STATUS APPROVE/REWORK, VERIFIED/STILL-BROKEN for each, up to 2 new findings if any. Max 250 words.

<stdin>
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
</stdin>
codex
I’ll verify against the live repo rather than the pasted diff, then compare the two target code paths directly. Since this is a review-only pass, I’m not editing anything.
codex
The GitNexus tools referenced by `AGENTS.md` are not exposed in this session, so I’m falling back to direct repository inspection and will call out that limitation in the result.
mcp: codex/list_mcp_resources started
mcp: codex/list_mcp_resources (completed)
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "checkForVictory|triggerVictory|defendersWouldWin|startOccupation|class ColonyTierGuard|isFirstColony|getFirstColonyOwner" src/main/java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git status --short' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 403ms:
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:149:    public static boolean isFirstColony(UUID playerUUID, int colonyID) {
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:181:    public static UUID getFirstColonyOwner(int colonyId) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:668:    public static void checkForVictory(WarData war) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:768:                    net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1793:        checkForVictory(war);
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1824:                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1836:                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2762:        boolean isPrimaryColony = FirstColonyTracker.isFirstColony(targetOwnerUUID, targetColony.getID());
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:149:    public static void startOccupation(IColony colony, UUID occupierUUID, IColony attackerColony) {
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:151:            LOGGER.warn("startOccupation called with null colony or occupier");
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:177:        UUID trackedFirstOwner = net.machiavelli.minecolonytax.FirstColonyTracker.getFirstColonyOwner(colonyId);
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:182:                    && net.machiavelli.minecolonytax.FirstColonyTracker.isFirstColony(originalOwner, colonyId);
src/main/java\net\machiavelli\minecolonytax\commands\OfficerTrackingDebugCommand.java:117:        UUID fctOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:22:public final class ColonyTierGuard {
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:41:        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:49:        if (FirstColonyTracker.isFirstColony(currentOwner, colony.getID())) {
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:74:        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:77:                || (owner != null && FirstColonyTracker.isFirstColony(owner, colony.getID()))) {
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:69:            boolean isFirstColony = FirstColonyTracker.addColony(ownerUUID, colonyID);
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:71:            if (isFirstColony) {
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:156:            triggerVictory(war, attacker);
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:183:     * defender lives to zero so the existing checkForVictory + endWar pipeline
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:187:     * checkForVictory would resolve as DEFENDER victory the moment we zero
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:192:    private static void triggerVictory(WarData war, ServerPlayer winner) {
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:196:        // Mirror WarSystem.checkForVictory's defender-win condition exactly. If we
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:204:        boolean defendersWouldWin =
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:207:        if (defendersWouldWin) {
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:228:        // Zero defender lives + nudge checkForVictory to drive the existing flow.
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:235:            WarSystem.checkForVictory(war);
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:186:            WarSystem.checkForVictory(war);
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:200:            WarSystem.checkForVictory(war);
src/main/java\net\machiavelli\minecolonytax\event\WarEventHandler.java:356:            WarSystem.checkForVictory(war);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 757ms:
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
?? claimButtonX
?? reviews/
?? s.withColor(ChatFormatting.GREEN).withBold(true),+
?? src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java
?? src/main/java/net/machiavelli/minecolonytax/compat/EasyFactionsBridge.java
?? src/main/java/net/machiavelli/minecolonytax/compat/EasyFactionsPermissionSync.java
?? src/main/java/net/machiavelli/minecolonytax/espionage/SpyClientHandler.java
?? src/main/java/net/machiavelli/minecolonytax/siege/
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
 

codex
I found the relevant methods in the working tree. Next I’m reading the exact branches around `checkForVictory`, `triggerVictory`, `startOccupation`, and `ColonyTierGuard` to compare behavior line by line.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path 'src/main/java/net/machiavelli/minecolonytax/WarSystem.java' | Select-Object -Skip 650 -First 115" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path 'src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java' | Select-Object -Skip 180 -First 70" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path 'src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java' | Select-Object -Skip 0 -First 105" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path 'src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java' | Select-Object -Skip 135 -First 75" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 548ms:
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

        // Mirror WarSystem.checkForVictory's defender-win condition exactly. If we
        // proceed when defenders would win, the legacy resolver will flip the result
        // to defender victory the moment we zero defender lives (which is exactly
        // what triggers the resolver). Use the SAME logic so we never race.
        boolean hasAttackers = !war.getAttackerLives().isEmpty();
        boolean allAttackersDead = hasAttackers
                && war.getAttackerLives().values().stream().allMatch(v -> v <= 0);
        boolean allAttackerGuardsDead = war.getRemainingAttackerGuards() <= 0;
        boolean defendersWouldWin =
                (hasAttackers && allAttackersDead)
                || (!hasAttackers && allAttackerGuardsDead);
        if (defendersWouldWin) {
            LOGGER.info("Experimental victory refused: defender-win condition is already true for war {}; "
                    + "the legacy resolver will run.", war.getWarID());
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 636ms:
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 554ms:
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
 

 succeeded in 543ms:
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
        //
        // Use the same canonical tier check as ColonyTierGuard: FCT reverse
        // lookup first, then permissions-owner fallback. Otherwise stale/null/
        // placeholder owners (abandoned colonies, system-owned, etc.) can leak
        // a primary into the TRANSFER_PENDING flow and accidentally transfer
        // the deed at expiry.
        boolean isPrimary;
        UUID trackedFirstOwner = net.machiavelli.minecolonytax.FirstColonyTracker.getFirstColonyOwner(colonyId);
        if (trackedFirstOwner != null) {
            isPrimary = true;
        } else {
            isPrimary = originalOwner != null
                    && net.machiavelli.minecolonytax.FirstColonyTracker.isFirstColony(originalOwner, colonyId);
        }
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
The two requested comparisons both look semantically aligned so far. I’m doing one extra pass for direct call-site fallout from the signature shown in the diff, because that kind of mismatch can turn an otherwise correct logic fix into a compile break.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"reclaimByOriginalOwner\\(\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path 'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' | Select-Object -Skip 0 -First 260" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"canTransferOwnership\\(|canBesiegePermanentClaim\\(|transferOwnership\\(\" src/main/java/net/machiavelli/minecolonytax src/main/java/net/machiavelli/minecolonytax/occupation src/main/java/net/machiavelli/minecolonytax/permissions" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 412ms:
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:530:    public static boolean reclaimByOriginalOwner(int colonyId, UUID reclaimerUUID) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 416ms:
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:479:                boolean transferred = WarSystem.transferOwnership(colony, occupierUUID);
src/main/java/net/machiavelli/minecolonytax/permissions\ColonyTierGuard.java:13: * {@link #canTransferOwnership(IColony)}. Primary colonies (a player's first
src/main/java/net/machiavelli/minecolonytax/permissions\ColonyTierGuard.java:33:    public static boolean canTransferOwnership(IColony colony) {
src/main/java/net/machiavelli/minecolonytax/permissions\ColonyTierGuard.java:59:     * Same rules as {@link #canTransferOwnership(IColony)}; named separately so
src/main/java/net/machiavelli/minecolonytax/permissions\ColonyTierGuard.java:62:    public static boolean canBesiegePermanentClaim(IColony colony) {
src/main/java/net/machiavelli/minecolonytax/permissions\ColonyTierGuard.java:63:        return canTransferOwnership(colony);
src/main/java/net/machiavelli/minecolonytax/permissions\ColonyTierGuard.java:95:     * route it through {@link net.machiavelli.minecolonytax.WarSystem#transferOwnership(IColony, java.util.UUID)}
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:774:                    transferOwnership(war.getColony(), war.getAttacker());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1191:    public static boolean transferOwnership(IColony colony, UUID newOwnerUUID) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1202:        if (!net.machiavelli.minecolonytax.permissions.ColonyTierGuard.canTransferOwnership(colony)) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1829:                transferOwnership(war.getColony(), war.getAttacker());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1886:                transferOwnership(war.getAttackerColony(), war.getDefender());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:479:                boolean transferred = WarSystem.transferOwnership(colony, occupierUUID);
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:13: * {@link #canTransferOwnership(IColony)}. Primary colonies (a player's first
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:33:    public static boolean canTransferOwnership(IColony colony) {
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:59:     * Same rules as {@link #canTransferOwnership(IColony)}; named separately so
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:62:    public static boolean canBesiegePermanentClaim(IColony colony) {
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:63:        return canTransferOwnership(colony);
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:95:     * route it through {@link net.machiavelli.minecolonytax.WarSystem#transferOwnership(IColony, java.util.UUID)}
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 552ms:
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

    /** Persistent occupation records (colonyId -> occupation data). */
    private static final Map<Integer, BesiegeOccupationData> OCCUPATIONS = new ConcurrentHashMap<>();

    /** Per-player cooldown map (playerUUID -> timestamp when cooldown expires). */
    private static final Map<UUID, Long> PLAYER_COOLDOWNS = new ConcurrentHashMap<>();

    private static MinecraftServer SERVER;

    public static void initialize(MinecraftServer server) {
        SERVER = server;
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

        // 1. Must own at least one colony themselves
        IColony besiegerColony = getPrimaryColonyOfPlayer(besiegerUUID);
        if (besiegerColony == null) {
            besieger.sendSystemMessage(Component.literal("You must own a colony to besiege another.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 2. Cannot besiege own colony
        if (colony.getPermissions().getOwner() != null
                && colony.getPermissions().getOwner().equals(besiegerUUID)) {
            besieger.sendSystemMessage(Component.literal("You cannot besiege your own colony.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 3. Primary colonies CAN now be besieged. Outcome routes through
        // OccupationManager in TAX_ONLY mode (deed never moves; auto-reclaim after
        // PrimaryColonyTaxOccupationDays). Secondaries continue to use the legacy
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"completeReclaim|OccupationManager\\.startOccupation|startOccupation\\(\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java src/main/java/net/machiavelli/minecolonytax/WarSystem.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path 'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' | Select-Object -Skip 250 -First 220" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path 'src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java' | Select-Object -Skip 450 -First 110" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path 'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' | Select-Object -Skip 560 -First 220" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 330ms:
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:599:                completeReclaim(raid, colony);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:710:    private static void completeReclaim(BesiegeRaidData raid, IColony colony) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:768:                    net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:1824:                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:1836:                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 459ms:

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
                    .append(Component.literal("! ")
                            .withStyle(ChatFormatting.DARK_RED))
                    .append(Component.literal("[Defend it]")
                            .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                            net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
                                            "/tp " + colonyCenter.getX() + " " + colonyCenter.getY() + " " + colonyCenter.getZ()))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Teleport near the colony center to join the defense")))));
            notifyColonyDefenders(colony, callToArms);

            if (TaxConfig.isNormalLogging())
                LOGGER.info("Besiege {} started on colony {} ({}) by {} with {} total defenders ({}g {}m {}e)",
                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
                        besieger.getName().getString(), totalDefenders, guardCount, militiaCount, mercCount);

            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
            ACTIVE_RAIDS.remove(besiegerUUID);
            return false;
        }
    }


    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
        int count = 0;
        for (ICitizenData citizenData : colony.getCitizenManager().getCitizens()) {
            if (citizenData.getJob() == null || !citizenData.getJob().isGuard()) continue;
            Optional<AbstractEntityCitizen> entityOpt = citizenData.getEntity();
            if (entityOpt.isEmpty()) continue;
            AbstractEntityCitizen guard = entityOpt.get();

            try {
                applyDefenderAI(guard, besieger, raid);
                applyDefenderEffects(guard, TaxConfig.getBesiegeDurationMinutes());
                raid.hostileCitizenIds.add(citizenData.getId());
                count++;
            } catch (Exception e) {
                LOGGER.warn("Failed to make guard {} hostile during besiege", citizenData.getName(), e);
            }
        }
        return count;
    }

    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
        List<ICitizenData> eligible = new ArrayList<>();
        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            if (citizen.isChild()) continue;
            if (citizen.getEntity().isEmpty()) continue;
            if (citizen.getJob() != null && citizen.getJob().isGuard()) continue;
            if (citizen.getJob() != null) {
                String jobPath = citizen.getJob().getJobRegistryEntry().getKey().getPath();
                if (jobPath.equals("deliveryman")) continue;
            }
            eligible.add(citizen);
        }

        Collections.shuffle(eligible);
        int target = (int) Math.ceil(eligible.size() * TaxConfig.getBesiegeMilitiaPercent());
        int count = 0;

        for (ICitizenData citizen : eligible) {
            if (count >= target) break;
            Optional<AbstractEntityCitizen> entityOpt = citizen.getEntity();
            if (entityOpt.isEmpty()) continue;
            AbstractEntityCitizen entity = entityOpt.get();

            try {
                // Equip wooden sword
                entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
                applyDefenderAI(entity, besieger, raid);
                applyDefenderEffects(entity, TaxConfig.getBesiegeDurationMinutes());
                raid.hostileCitizenIds.add(citizen.getId());
                count++;
            } catch (Exception e) {
                LOGGER.warn("Failed to convert citizen {} to militia during besiege", citizen.getName(), e);
            }
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 453ms:
                // to the original owner. Friendly notification on both sides.
                if (TaxConfig.isNormalLogging()) {
                    LOGGER.info("Tax-only occupation expired for primary colony {} - auto-reclaiming", colony.getName());
                }

                ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
                if (originalOwner != null) {
                    originalOwner.sendSystemMessage(
                            Component.literal("Your Primary colony " + colony.getName()
                                            + " has been auto-reclaimed ƒ?" the besieger's hold has lapsed and your taxes now route to you again.")
                                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                }
                ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
                if (occupier != null) {
                    occupier.sendSystemMessage(
                            Component.literal("Your tax-occupation of " + colony.getName()
                                            + " has lapsed ƒ?" the owner reclaims it automatically (Primary colonies cannot be permanently claimed).")
                                    .withStyle(ChatFormatting.GOLD));
                }
            } else {
                // Standard TRANSFER_PENDING flow for secondaries. Only broadcast the
                // permanent-claim message if the transfer actually succeeded ƒ?"
                // ColonyTierGuard or other failures must not produce a misleading message.
                if (TaxConfig.isNormalLogging()) {
                    LOGGER.info("Occupation expired for colony {} - attempting full ownership transfer to {}",
                            colony.getName(), occupierUUID);
                }

                boolean transferred = WarSystem.transferOwnership(colony, occupierUUID);
                if (transferred) {
                    Component broadcastMsg = Component.literal(colony.getName() + " has been permanently claimed by its occupier!")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        p.sendSystemMessage(broadcastMsg);
                    }
                    ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
                    if (originalOwner != null) {
                        originalOwner.sendSystemMessage(
                                Component.literal("You failed to reclaim " + colony.getName()
                                                + " within the deadline. Ownership has been permanently transferred!")
                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    }
                } else {
                    // Transfer was denied (e.g. by ColonyTierGuard) or failed. Don't
                    // broadcast a permanent-claim message that isn't true; treat as a
                    // tax-only-style lapse and reclaim to the original owner.
                    LOGGER.info("Transfer for colony {} was denied or failed - lapsing occupation instead", colony.getName());
                    ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
                    if (originalOwner != null) {
                        originalOwner.sendSystemMessage(
                                Component.literal("The occupation of " + colony.getName()
                                                + " has lapsed ƒ?" the deed remains with you.")
                                        .withStyle(ChatFormatting.GREEN));
                    }
                }
            }

            ACTIVE_OCCUPATIONS.remove(colonyId);
        }

        if (!toTransfer.isEmpty()) {
            saveData();
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 452ms:

        double multiplier = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
                .getMilitiaMultiplier(colony.getID());
        int bonus = (int) Math.floor(guardCount * (multiplier - 1.0));
        if (bonus <= 0) return 0;

        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
        int spawned = 0;
        for (int i = 0; i < bonus; i++) {
            try {
                EntityMercenary militia = (EntityMercenary) com.minecolonies.api.entity.ModEntities.MERCENARY.create(world);
                if (militia == null) continue;
                BlockPos spawnPos = findSpawnPos(colony.getCenter(), world);
                militia.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                militia.setTarget(besieger);
                militia.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 0));
                world.addFreshEntity(militia);
                raid.militiaSupport.add(militia);
                spawned++;
            } catch (Exception e) {
                LOGGER.warn("Failed to spawn militia reinforcement {} for colony {}", i, colony.getName(), e);
            }
        }
        if (spawned > 0 && TaxConfig.isNormalLogging()) {
            LOGGER.info("Spawned {} militia-upgrade reinforcements for colony {} (multiplier {})",
                    spawned, colony.getName(),
                    String.format(java.util.Locale.ROOT, "%.2f", multiplier));
        }
        return spawned;
    }

    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
        cleanupRaid(raid, true);
        applyCooldown(raid.besiegingPlayerUUID);

        if (attackerWon) {
            applySiegeSpoils(raid, colony, true);
            if (raid.isReclaim) {
                completeReclaim(raid, colony);
            } else {
                completeBesiegeVictory(raid, colony);
            }
        } else {
            applySiegeSpoils(raid, colony, false);
            sendToPlayer(raid.besiegingPlayerUUID,
                    Component.literal("The besiege of " + colony.getName() + " failed.")
                            .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * One-shot percentage transfer between loser's and winner's primary colony treasuries.
     * Mirrors the war-spoil semantics; configured via BesiegeSpoilPercentOfLoserTreasury.
     *
     * Honours both sides' treasury caps: the actual credited amount is the lesser
     * of (computed spoil) and (winner's remaining headroom). The deduction matches
     * that credited amount, so coins are never lost to the cap.
     */
    private static void applySiegeSpoils(BesiegeRaidData raid, IColony defenderColony, boolean attackerWon) {
        int percent = TaxConfig.getBesiegeSpoilPercentOfLoserTreasury();
        if (percent <= 0) return;

        IColony besiegerColony = getPrimaryColonyOfPlayer(raid.besiegingPlayerUUID);
        if (besiegerColony == null) return;

        IColony loser = attackerWon ? defenderColony : besiegerColony;
        IColony winner = attackerWon ? besiegerColony : defenderColony;
        if (loser == null || winner == null) return;

        int loserBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(loser.getID());
        if (loserBalance <= 0) return;
        int requestedSpoil = (int) Math.floor(loserBalance * (percent / 100.0));
        if (requestedSpoil <= 0) return;

        // Compute the winner's available headroom so we don't deduct coins that
        // would be silently capped away on the credit side.
        int winnerBalance = net.machiavelli.minecolonytax.economy.TreasuryManager.getTreasuryBalance(winner.getID());
        int winnerCap = net.machiavelli.minecolonytax.economy.TreasuryManager.getEffectiveMaxCapacity(winner.getID());
        int headroom = Math.max(0, winnerCap - winnerBalance);
        int actualSpoil = Math.min(requestedSpoil, headroom);
        if (actualSpoil <= 0) return;

        net.machiavelli.minecolonytax.economy.TreasuryManager.deductFromTreasury(loser.getID(), actualSpoil);
        net.machiavelli.minecolonytax.economy.TreasuryManager.addToTreasury(winner.getID(), actualSpoil);

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Siege spoils ({}%): {} ƒÅ' {} = {} (requested {}, headroom {})",
                    percent, loser.getName(), winner.getName(), actualSpoil, requestedSpoil, headroom);
        }

        // Notify both sides with the actual transferred amount.
        UUID winnerOwner = winner.getPermissions().getOwner();
        UUID loserOwner = loser.getPermissions().getOwner();
        Component winMsg = Component.literal("Siege spoils: " + actualSpoil + " coins transferred from "
                + loser.getName() + " to " + winner.getName() + ".")
                .withStyle(ChatFormatting.GOLD);
        Component loseMsg = Component.literal("Siege fine: " + actualSpoil + " coins paid from "
                + loser.getName() + " to " + winner.getName() + ".")
                .withStyle(ChatFormatting.RED);
        if (winnerOwner != null) sendToPlayer(winnerOwner, winMsg);
        if (loserOwner != null && !loserOwner.equals(winnerOwner)) sendToPlayer(loserOwner, loseMsg);
    }

    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
        int tributePct = TaxConfig.getBesiegeTributePercent();
        int durationHours = TaxConfig.getBesiegeTributeDurationHours();

        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);

        if (vassalized) {
            // Store occupation record
            UUID ownerUUID = colony.getPermissions().getOwner();
            BesiegeOccupationData occ = new BesiegeOccupationData(
                    colony.getID(), colony.getName(),
                    raid.besiegingPlayerUUID, ownerUUID,
                    System.currentTimeMillis(), tributePct);
            OCCUPATIONS.put(colony.getID(), occ);
            saveData();

            // Notify former owner
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff -- src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff -- src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff -- src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 379ms:
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 396ms:
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java', LF will be replaced by CRLF the next time Git touches it

 succeeded in 381ms:
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
 
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java', LF will be replaced by CRLF the next time Git touches it

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"TownHallDemolitionObjective|onWarEnded\\(\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git status --short src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git ls-files src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git ls-files src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java'` rejected: blocked by policy
2026-05-25T02:45:02.330968Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git ls-files src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java'` rejected: blocked by policy
 succeeded in 470ms:
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1268:                net.machiavelli.minecolonytax.siege.TownHallDemolitionObjective.onWarEnded(warData.getWarID());
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:48:public final class TownHallDemolitionObjective {
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:50:    private static final Logger LOGGER = LogManager.getLogger(TownHallDemolitionObjective.class);
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:60:    private TownHallDemolitionObjective() {}
src/main/java\net\machiavelli\minecolonytax\siege\TownHallDemolitionObjective.java:248:    public static void onWarEnded(UUID warId) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 504ms:
?? src/main/java/net/machiavelli/minecolonytax/siege/TownHallDemolitionObjective.java
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-Content -Path 'src/main/java/net/machiavelli/minecolonytax/WarSystem.java' | Select-Object -Skip 1248 -First 32" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "git diff -- src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-String -Pattern 'TownHallDemolitionObjective|onWarEnded' -Context 3,3" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "git diff -- src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-String -Pattern 'TownHallDemolitionObjective|onWarEnded' -Context 3,3"` rejected: blocked by policy
2026-05-25T02:45:18.541458Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "git diff -- src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-String -Pattern 'TownHallDemolitionObjective|onWarEnded' -Context 3,3"` rejected: blocked by policy
 succeeded in 814ms:
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git status --short src/main/java/net/machiavelli/minecolonytax/siege' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 679ms:
?? src/main/java/net/machiavelli/minecolonytax/siege/
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-ChildItem -Path 'src/main/java/net/machiavelli/minecolonytax/siege'" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 2920ms:


    Verzeichnis: C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\siege


Mode                 LastWriteTime         Length Name                                                                 
----                 -------------         ------ ----                                                                 
-a----        25.05.2026     04:42          12107 TownHallDemolitionObjective.java                                     
-a----        25.05.2026     04:33           9792 WarBlockLedger.java                                                  


Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
STATUS REWORK

A VERIFIED — `triggerVictory` now matches `WarSystem.checkForVictory` defender-win semantics: `(hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead)`.

B VERIFIED — `startOccupation` now classifies primary colonies FCT reverse-lookup first, then permissions-owner fallback, matching `ColonyTierGuard`.

New finding:
1. `src/main/java/net/machiavelli/minecolonytax/siege/` is untracked, including `TownHallDemolitionObjective.java` and `WarBlockLedger.java`, while tracked `WarSystem.java` references them. If this patch is submitted as-is from tracked diff only, it will compile-break or omit the reviewed fix.

Note: GitNexus tools were not available in this session; verification was by direct source inspection. Build not run due read-only sandbox.
tokens used
95.830
STATUS REWORK

A VERIFIED — `triggerVictory` now matches `WarSystem.checkForVictory` defender-win semantics: `(hasAttackers && allAttackersDead) || (!hasAttackers && allAttackerGuardsDead)`.

B VERIFIED — `startOccupation` now classifies primary colonies FCT reverse-lookup first, then permissions-owner fallback, matching `ColonyTierGuard`.

New finding:
1. `src/main/java/net/machiavelli/minecolonytax/siege/` is untracked, including `TownHallDemolitionObjective.java` and `WarBlockLedger.java`, while tracked `WarSystem.java` references them. If this patch is submitted as-is from tracked diff only, it will compile-break or omit the reviewed fix.

Note: GitNexus tools were not available in this session; verification was by direct source inspection. Build not run due read-only sandbox.
