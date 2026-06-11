OpenAI Codex v0.133.0
--------
workdir: C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, $TMPDIR]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e95f5-8a39-7ad1-8b5f-2face38be509
--------
user
current changes
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git status --short' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 22999ms:
 M AGENTS.md
 M CHANGELOG.md
 M CLAUDE.md
 M build.gradle
 M gradle.properties
 M src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java
 M src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
 M src/main/java/net/machiavelli/minecolonytax/TaxManager.java
 M src/main/java/net/machiavelli/minecolonytax/WarSystem.java
 M src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java
 M src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java
 M src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java
 M src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
 M src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java
 M src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java
 M src/main/java/net/machiavelli/minecolonytax/data/WarData.java
 M src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java
 M src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java
 M src/main/java/net/machiavelli/minecolonytax/event/AbandonedColonyProtectionHandler.java
 M src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java
 M src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java
 M src/main/java/net/machiavelli/minecolonytax/event/ColonyPermissionMonitor.java
 M src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java
 M src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java
 M src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java
 M src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java
 M src/main/java/net/machiavelli/minecolonytax/mixin/WorldTickHandlerMixin.java
 M src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java
 M src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java
 M src/main/resources/META-INF/mods.toml
 M wiki/Commands_&_Permissions.md
 M wiki/Configuration_Guide.md
 M wiki/War_Persistence.md
?? $(basename
?? $null
?? 'argument
?? 0
?? 0)
?? DEPENDENCY_COMPATIBILITY.md
?? HEAD
?? INWAR
?? JAR
?? OPTIMIZATION_AUDIT.md
?? PORTING_NOTES.md
?? col.getID()
?? dimension
?? raid.getColony().getID()
?? return)
?? reviews/codex-townhall-sdm-review.md
?? reviews/fix-setowner-v2.diff
?? reviews/fix-setowner.diff
?? reviews/opt-batch1-critical.diff
?? reviews/opt-batch23.diff
?? reviews/opt-batch4.diff
?? reviews/opt-final-consolidated.diff
?? reviews/opt-final-fixes.diff
?? reviews/opt-fixc-final.diff
?? reviews/opt-warsystem-final.diff
?? setOwner(online)
?? {,+
?? {,-
?? {}
warning: unable to unlink 'C:/Dev/War-N-Taxes-Mod---Minecolonies-Addon/minecolonies/.git/index.lock': Invalid argument

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "git diff --stat; Write-Output '---CACHED---'; git diff --cached --stat; Write-Output '---UNTRACKED TOP---'; Get-ChildItem -Force | Where-Object { -not "'$_.PSIsContainer } | Select-Object Name,Length,LastWriteTime | Format-Table -AutoSize' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 682ms:
 AGENTS.md                                          |   2 +-
 CHANGELOG.md                                       |  77 +++++-
 CLAUDE.md                                          |   2 +-
 build.gradle                                       |  24 +-
 gradle.properties                                  |   4 +-
 .../machiavelli/minecolonytax/MineColonyTax.java   |  38 +--
 .../net/machiavelli/minecolonytax/TaxConfig.java   |  36 ++-
 .../net/machiavelli/minecolonytax/TaxManager.java  | 108 +++++---
 .../net/machiavelli/minecolonytax/WarSystem.java   |  73 ++++--
 .../abandon/ColonyAbandonmentManager.java          | 292 ++++++++++++++-------
 .../abandon/ColonyClaimingRaidManager.java         |  39 +--
 .../besiege/BesiegeDamageShieldHandler.java        |  29 +-
 .../minecolonytax/besiege/BesiegeManager.java      |  27 ++
 .../minecolonytax/commands/ClaimTaxCommand.java    |  31 ++-
 .../minecolonytax/commands/WntCommands.java        | 197 +++++++-------
 .../machiavelli/minecolonytax/data/WarData.java    |   6 +
 .../minecolonytax/economy/TreasuryManager.java     |  19 +-
 .../economy/WarExhaustionManager.java              |  41 +--
 .../event/AbandonedColonyProtectionHandler.java    |   6 +
 .../event/BlockInteractionFilterHandler.java       |   4 +-
 .../minecolonytax/event/ColonyEventListener.java   |  10 +-
 .../event/ColonyPermissionMonitor.java             |   8 +
 .../minecolonytax/event/RaidKillTracker.java       |  10 +
 .../events/random/RandomEventManager.java          |  12 +-
 .../minecolonytax/integration/CurrencyService.java |  49 ++++
 .../integration/SDMShopIntegration.java            |  62 ++++-
 .../minecolonytax/mixin/WorldTickHandlerMixin.java |   7 +
 .../network/packets/ClaimTaxPacket.java            |  53 +---
 .../minecolonytax/util/AsyncSaveExecutor.java      |  32 ++-
 src/main/resources/META-INF/mods.toml              |   2 +-
 wiki/Commands_&_Permissions.md                     |  13 +-
 wiki/Configuration_Guide.md                        |  58 +++-
 wiki/War_Persistence.md                            |  40 +++
 33 files changed, 1002 insertions(+), 409 deletions(-)
---CACHED---
---UNTRACKED TOP---

Name                          Length LastWriteTime      
----                          ------ -------------      
$(basename                         0 03.06.2026 15:53:41
$null                            135 05.06.2026 04:45:11
'argument                          0 05.06.2026 01:40:53
.gitattributes                   261 12.11.2025 23:29:09
.gitignore                       846 25.05.2026 04:09:02
.mcp.json                        525 01.04.2026 17:52:36
.windsurfrules                     0 12.11.2025 23:29:09
0                                  0 05.06.2026 04:33:42
0)                                 0 05.06.2026 04:37:41
AGENTS.md                       5648 03.06.2026 15:34:42
build.gradle                   11806 05.06.2026 04:52:59
build.log                       2622 01.03.2026 23:28:14
CHANGELOG.md                  112179 05.06.2026 04:51:17
CLAUDE.md                      10543 03.06.2026 15:34:42
col.getID()                        0 03.06.2026 19:26:40
DEPENDENCY_COMPATIBILITY.md     4990 03.06.2026 15:55:33
dimension                          0 03.06.2026 19:26:19
events                             0 03.04.2026 19:43:48
gradle.properties               3887 05.06.2026 04:49:17
gradlew                         8955 08.02.2026 22:53:07
gradlew.bat                     2918 08.02.2026 22:53:07
HEAD                               0 03.06.2026 16:15:05
INWAR                              0 05.06.2026 01:04:47
JAR                                0 03.06.2026 15:51:41
MOD_DESCRIPTION.md              8127 07.01.2026 21:26:24
OFFICER_FIX_SUMMARY.md          5501 14.12.2025 04:38:43
OPTIMIZATION_AUDIT.md          12718 03.06.2026 20:25:12
PORTING_NOTES.md                6726 05.06.2026 01:49:44
raid.getColony().getID()           0 03.06.2026 18:47:04
return)                            0 05.06.2026 01:07:09
setOwner(online)                   0 05.06.2026 01:48:41
settings.gradle                  363 01.03.2026 23:45:02
Siege_SMP_Design_Changes.html  63525 03.06.2026 10:36:14
TESTING_GUIDE.md               17324 03.06.2026 12:27:17
War_System_Audit.html          29624 14.05.2026 11:51:53
War_System_Explained.html      29915 27.04.2026 19:16:14
Whats_New_Since_v2.html        28598 03.06.2026 15:41:01
WNT_UI_BookMainComponent.png    4258 20.03.2026 12:32:29
{,+                                0 03.06.2026 18:45:21
{,-                                0 05.06.2026 04:30:40
{}                                 0 05.06.2026 04:31:33


warning: in the working copy of 'AGENTS.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'CLAUDE.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/mixin/WorldTickHandlerMixin.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'wiki/Configuration_Guide.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'wiki/War_Persistence.md', LF will be replaced by CRLF the next time Git touches it

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff -- src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java src/main/java/net/machiavelli/minecolonytax/TaxConfig.java src/main/java/net/machiavelli/minecolonytax/TaxManager.java src/main/java/net/machiavelli/minecolonytax/WarSystem.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 319ms:
diff --git a/src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java b/src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java
index 34241e1..484efd6 100644
--- a/src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java
+++ b/src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java
@@ -152,27 +152,33 @@ public class MineColonyTax {
 
         WarSystem.restoreAllColonyPermissionsToDefaults();
 
-        try {
-            net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
-        } catch (Exception e) {
-            LOGGER.error("Immediate null owner fix failed", e);
+        // SAFETY (4.x world-brick fix): the legacy code called emergencyFixAllNullOwners()
+        // IMMEDIATELY here, at ServerStartingEvent — BEFORE MineColonies finishes loading
+        // colonies. A colony that was only transiently owner-null mid-load would then get a
+        // synthetic '[AUTO_OWNER]' placeholder written into its permissions and be flagged
+        // abandoned, corrupting its saved data and bricking the world on the next load.
+        // That immediate pass is removed. Automatic owner-repair / abandoned-entry cleanup
+        // now runs ONLY when the abandonment system is explicitly enabled, and ONLY on a
+        // deferred pass that lets colonies finish loading first.
+        if (TaxConfig.isColonyAbandonmentSystemEnabled()) {
+            net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
+                try {
+                    net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
+                    net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.cleanupAllColoniesAbandonedEntries();
+                } catch (Exception e) {
+                    LOGGER.error("Deferred null-owner repair failed", e);
+                }
+            }, 3000);
         }
 
-        // Deferred safety passes let MineColonies finish loading colonies before the fix runs
-        net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
-            try {
-                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
-                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.cleanupAllColoniesAbandonedEntries();
-            } catch (Exception e) {
-                LOGGER.error("Delayed null owner fix failed", e);
-            }
-        }, 1000);
-
+        // Always-on, removal-ONLY migration: heal worlds that older versions corrupted with
+        // synthetic '[AUTO_OWNER]'/system-owner placeholder entries. This never injects owners
+        // and never flags colonies abandoned. Deferred so colonies are fully loaded first.
         net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
             try {
-                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
+                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.repairLegacySyntheticOwners();
             } catch (Exception e) {
-                LOGGER.error("Final null owner fix failed", e);
+                LOGGER.error("Legacy synthetic-owner repair failed", e);
             }
         }, 3000);
 
diff --git a/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java b/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
index 7b93296..a5b0978 100644
--- a/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
+++ b/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
@@ -16,6 +16,7 @@ public class TaxConfig {
         public static ForgeConfigSpec CONFIG;
 
         public static final ForgeConfigSpec.BooleanValue ENABLE_SDM_SHOP_CONVERSION;
+        public static final ForgeConfigSpec.ConfigValue<String> SDM_CURRENCY_NAME;
         public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_ITEM_NAME;
         public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_DENOMINATIONS;
         public static final ForgeConfigSpec.IntValue DEBT_LIMIT;
@@ -186,6 +187,7 @@ public class TaxConfig {
         public static final ForgeConfigSpec.BooleanValue APPLY_RESISTANCE_TO_CITIZENS;
 
         // Colony Auto-Abandon Configuration
+        public static final ForgeConfigSpec.BooleanValue ENABLE_COLONY_ABANDONMENT_SYSTEM;
         public static final ForgeConfigSpec.BooleanValue ENABLE_COLONY_AUTO_ABANDON;
         public static final ForgeConfigSpec.IntValue COLONY_AUTO_ABANDON_DAYS;
         public static final ForgeConfigSpec.BooleanValue NOTIFY_OWNERS_BEFORE_ABANDON;
@@ -464,6 +466,13 @@ public class TaxConfig {
                                 .comment("Enable SDMShop conversion (true = enable, false = disable).")
                                 .define("EnableSDMShopConversion", true);
 
+                SDM_CURRENCY_NAME = BUILDER
+                                .comment("The SDM-Economy currency id that claimed taxes are deposited into.",
+                                                "Must match the currency id configured in SDM-Economy (NOT the display name).",
+                                                "Default 'sdm_coin'. If your server uses a different currency id, set it here,",
+                                                "otherwise claimed taxes are deposited into a currency you never see.")
+                                .define("SDMCurrencyName", "sdm_coin");
+
                 CURRENCY_ITEM_NAME = BUILDER
                                 .comment("The item name for the custom currency (e.g., 'minecraft:emerald').")
                                 .define("CurrencyItemName", "minecraft:emerald");
@@ -971,6 +980,17 @@ public class TaxConfig {
                 // ========== Colony Auto-Abandon Settings ==========
                 BUILDER.push("Colony Auto-Abandon");
 
+                ENABLE_COLONY_ABANDONMENT_SYSTEM = BUILDER.comment(
+                                "MASTER SWITCH for the entire automatic colony abandonment / owner-repair system.",
+                                "When FALSE (default): the mod NEVER automatically rewrites MineColonies colony owner",
+                                "or permission data. This is the safe default and prevents the rare colony-data",
+                                "corruption that can occur when owner-repair runs while colonies are still loading.",
+                                "When TRUE: the inactivity auto-abandon, null-owner repair, and abandoned-entry cleanup",
+                                "passes run (each still has its own sub-toggle below).",
+                                "NOTE: a one-time, removal-only repair of legacy '[AUTO_OWNER]' placeholder entries",
+                                "always runs regardless of this switch, to heal worlds affected by older versions.")
+                                .define("EnableColonyAbandonmentSystem", false);
+
                 ENABLE_COLONY_AUTO_ABANDON = BUILDER.comment(
                                 "Enable automatic colony abandonment when owners/officers haven't visited for the configured time. "
                                                 +
@@ -2547,6 +2567,10 @@ public class TaxConfig {
                 return ENABLE_SDM_SHOP_CONVERSION.get();
         }
 
+        public static String getSDMCurrencyName() {
+                return SDM_CURRENCY_NAME.get();
+        }
+
         public static String getCurrencyItemName() {
                 return CURRENCY_ITEM_NAME.get();
         }
@@ -2970,8 +2994,18 @@ public class TaxConfig {
                 return minMultiplier + (normalizedHappiness * (maxMultiplier - minMultiplier));
         }
 
+        /**
+         * Master switch for the automatic abandonment / owner-repair machinery.
+         * When false the mod performs NO automatic writes to MineColonies owner/permission
+         * state. The inactivity auto-abandon toggle ({@link #isColonyAutoAbandonEnabled()})
+         * is a sub-feature and only takes effect when this master switch is also enabled.
+         */
+        public static boolean isColonyAbandonmentSystemEnabled() {
+                return ENABLE_COLONY_ABANDONMENT_SYSTEM.get();
+        }
+
         public static boolean isColonyAutoAbandonEnabled() {
-                return ENABLE_COLONY_AUTO_ABANDON.get();
+                return ENABLE_COLONY_ABANDONMENT_SYSTEM.get() && ENABLE_COLONY_AUTO_ABANDON.get();
         }
 
         public static int getColonyAutoAbandonDays() {
diff --git a/src/main/java/net/machiavelli/minecolonytax/TaxManager.java b/src/main/java/net/machiavelli/minecolonytax/TaxManager.java
index 5f3b154..938bff6 100644
--- a/src/main/java/net/machiavelli/minecolonytax/TaxManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/TaxManager.java
@@ -108,13 +108,22 @@ public class TaxManager {
                 cleanupTickCount++;
                 nullOwnerCheckCount++;
 
-                // Check for null owners every 5 seconds
-                if (nullOwnerCheckCount >= 100) { // 100 ticks = 5 seconds
+                // Null-owner safety net. The real null-owner repair runs at startup
+                // (MineColonyTax.onServerStarting + deferred passes); this is only a
+                // periodic catch for owners that go null at runtime (rare). Throttled
+                // to every 5 minutes — a per-5s all-colony permission scan is a needless
+                // steady-state cost at hundreds of colonies (optimization audit C2).
+                if (nullOwnerCheckCount >= 6000) { // 6000 ticks = 5 minutes
                     nullOwnerCheckCount = 0;
-                    try {
-                        net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
-                    } catch (Exception e) {
-                        LOGGER.error("Failed automatic null owner fix", e);
+                    // Gated by the master abandonment switch (4.x world-brick fix): when the
+                    // abandonment system is off the mod performs NO automatic writes to
+                    // MineColonies owner/permission state.
+                    if (TaxConfig.isColonyAbandonmentSystemEnabled()) {
+                        try {
+                            net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
+                        } catch (Exception e) {
+                            LOGGER.error("Failed automatic null owner fix", e);
+                        }
                     }
                 }
 
@@ -141,7 +150,9 @@ public class TaxManager {
                 // Run proactive [abandoned] cleanup every 30 minutes (36000 ticks = 30 minutes)
                 if (cleanupTickCount >= 36000) {
                     cleanupTickCount = 0;
-                    runPeriodicAbandonedCleanup();
+                    if (TaxConfig.isColonyAbandonmentSystemEnabled()) {
+                        runPeriodicAbandonedCleanup();
+                    }
                 }
 
                 // Update claiming raids every second
@@ -151,7 +162,7 @@ public class TaxManager {
 
                 // Check for officer changes in abandoned colonies every 5 minutes (6000 ticks)
                 // This detects admin commands that add officers/owners to abandoned colonies
-                if (abandonmentTickCount % 6000 == 0) {
+                if (abandonmentTickCount % 6000 == 0 && TaxConfig.isColonyAbandonmentSystemEnabled()) {
                     checkForOfficerChangesInAbandonedColonies();
                 }
             }
@@ -619,19 +630,13 @@ public class TaxManager {
                     }
 
                     // --- Guard Tower Tax Boost Processing ---
+                    // Use the cached WarSystem.isGuardTower (identical matching logic) instead of
+                    // re-doing building.toString().toLowerCase() per building — that allocation
+                    // ran for every building of every colony each tax cycle (audit H8).
                     for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
-                        if (building.getBuildingLevel() > 0 && building.isBuilt()) {
-                            // Count guard towers using the same logic as WarSystem
-                            String displayName = building.getBuildingDisplayName();
-                            String className = building.getClass().getName().toLowerCase();
-                            String toString = building.toString().toLowerCase();
-
-                            if ((displayName != null && "Guard Tower".equalsIgnoreCase(displayName)) ||
-                                    className.contains("guardtower") ||
-                                    toString.contains("guardtower") ||
-                                    toString.contains("guard_tower")) {
-                                guardTowerCount++;
-                            }
+                        if (building.getBuildingLevel() > 0 && building.isBuilt()
+                                && WarSystem.isGuardTower(building)) {
+                            guardTowerCount++;
                         }
                     }
 
@@ -997,14 +1002,26 @@ public class TaxManager {
                     // Apply debt consequences (happiness penalty, events, abandonment, blocking)
                     processDebtConsequences(colony, finalTaxBalance, recipients);
 
-                    // Trigger random events after tax cycle
+                    // Trigger random events after tax cycle. Catch per-colony so one bad
+                    // colony can't abort the whole forEach — that would skip the post-loop
+                    // saveTaxData() and RandomEventManager.persist() and lose the cycle's
+                    // saves (codex C1 follow-up).
                     if (TaxConfig.isRandomEventsEnabled()) {
-                        RandomEventManager.onTaxCycle(colony);
+                        try {
+                            RandomEventManager.onTaxCycle(colony);
+                        } catch (Exception ex) {
+                            LOGGER.error("Random event tick failed for colony {}", colony.getID(), ex);
+                        }
                     }
                 });
             });
             // Only log save operation once per full tax cycle
             saveTaxData();
+            // Persist random-event state ONCE for the whole cycle (not per colony —
+            // see optimization audit C1 / RandomEventManager.persist()).
+            if (TaxConfig.isRandomEventsEnabled()) {
+                RandomEventManager.persist();
+            }
             if (TaxConfig.isNormalLogging()) {
                 LOGGER.info("Tax generation cycle completed for all colonies");
             }
@@ -1056,9 +1073,13 @@ public class TaxManager {
                 }
             }
 
-            // 3. Abandonment — after N consecutive max-debt cycles
+            // 3. Abandonment — after N consecutive max-debt cycles.
+            // Gated by the abandonment master switch (4.x world-brick fix): debt bankruptcy
+            // rewrites colony owner/permission state, so when the abandonment system is off the
+            // mod must not perform it automatically either.
             int abandonCycles = TaxConfig.getDebtAbandonmentCycles();
-            if (abandonCycles > 0 && debtLimit > 0 && finalTaxBalance <= -debtLimit && cycles >= abandonCycles
+            if (TaxConfig.isColonyAbandonmentSystemEnabled()
+                    && abandonCycles > 0 && debtLimit > 0 && finalTaxBalance <= -debtLimit && cycles >= abandonCycles
                     && serverInstance != null) {
                 LOGGER.warn("Colony {} has been in max debt for {} consecutive cycles — triggering debt abandonment",
                     colony.getName(), cycles);
@@ -1139,16 +1160,37 @@ public class TaxManager {
     }
 
     private static void saveTaxData(boolean logSave) {
-        File file = new File(TAX_DATA_FILE);
-        file.getParentFile().mkdirs(); // Ensure the directory exists
-        try (FileWriter writer = new FileWriter(file)) {
-            GSON.toJson(colonyTaxMap, writer);
-            if (logSave && TaxConfig.showTaxGenerationLogs()) {
-                LOGGER.info("Saved tax data to file.");
+        // Snapshot on the calling (main) thread, then write off-thread + coalesced so
+        // per-cycle and per-claim/debt-payment saves no longer block ticks (audit H4).
+        // Atomic temp+move added so a crash mid-write can't truncate colonyTaxData.json.
+        final Map<Integer, Integer> snapshot = new HashMap<>(colonyTaxMap);
+        final boolean log = logSave;
+        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("tax_data", () -> {
+            File file = new File(TAX_DATA_FILE);
+            file.getParentFile().mkdirs();
+            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
+            try {
+                try (FileWriter writer = new FileWriter(tmp)) {
+                    GSON.toJson(snapshot, writer);
+                }
+                try {
+                    java.nio.file.Files.move(tmp.toPath(), file.toPath(),
+                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
+                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
+                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
+                    java.nio.file.Files.move(tmp.toPath(), file.toPath(),
+                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
+                }
+                if (log && TaxConfig.showTaxGenerationLogs()) {
+                    LOGGER.info("Saved tax data to file.");
+                }
+            } catch (Exception e) {
+                LOGGER.error("Error saving tax data", e);
+                if (tmp.exists()) {
+                    try { tmp.delete(); } catch (Exception ignored) { /* nothing else to do */ }
+                }
             }
-        } catch (IOException e) {
-            LOGGER.error("Error saving tax data", e);
-        }
+        });
     }
 
     private static void loadTaxData(MinecraftServer server) {
diff --git a/src/main/java/net/machiavelli/minecolonytax/WarSystem.java b/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
index 5e7ab58..c371a46 100644
--- a/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
+++ b/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
@@ -330,9 +330,10 @@ public class WarSystem {
                     ? net.machiavelli.minecolonytax.economy.TreasuryManager.drainTreasury(attackerColonyId)
                     : Integer.MAX_VALUE;
 
-            // Periodic save every 5 drain ticks (5 minutes)
+            // Periodic save every 5 drain ticks (5 minutes) — async + coalesced now,
+            // so concurrent wars no longer each fire a synchronous treasury write (audit H2).
             if (tickCount[0] % 5 == 0) {
-                net.machiavelli.minecolonytax.economy.TreasuryManager.shutdown(); // calls saveData()
+                net.machiavelli.minecolonytax.economy.TreasuryManager.save();
             }
 
             // Auto-surrender if either side is depleted
@@ -1386,6 +1387,14 @@ public class WarSystem {
                 TickScheduler.cancel(warData.warChestDrainTaskId);
                 warData.warChestDrainTaskId = -1;
             }
+            if (warData.joinCountdownTaskId >= 0) {
+                TickScheduler.cancel(warData.joinCountdownTaskId);
+                warData.joinCountdownTaskId = -1;
+            }
+            if (warData.joinStartTaskId >= 0) {
+                TickScheduler.cancel(warData.joinStartTaskId);
+                warData.joinStartTaskId = -1;
+            }
             if (warData.bossEvent != null) {
                 warData.bossEvent.removeAllPlayers();
                 warData.bossEvent.setVisible(false);
@@ -2392,10 +2401,16 @@ public class WarSystem {
         // join phase is at least 6 seconds long
         if (joinDurationMillis >= 6000) {
             final int[] secondsLeft = { 6 };
-            TickScheduler.scheduleRepeating(() -> {
+            war.joinCountdownTaskId = TickScheduler.scheduleRepeating(() -> {
                 try {
                     if (war == null || war.getColony() == null || !war.isJoinPhaseActive() || secondsLeft[0] < 0) {
-                        return; // Task will be cleaned up when war starts or ends
+                        // Self-cancel: without this the repeating task re-arms forever,
+                        // leaking the task + retaining the whole WarData (audit C4).
+                        if (war != null && war.joinCountdownTaskId >= 0) {
+                            TickScheduler.cancel(war.joinCountdownTaskId);
+                            war.joinCountdownTaskId = -1;
+                        }
+                        return;
                     }
 
                     // Play countdown sound to all war participants
@@ -2433,13 +2448,27 @@ public class WarSystem {
         }
 
         // Main timer to start the war when join phase ends
-        TickScheduler.scheduleDelayed(() -> {
+        war.joinStartTaskId = TickScheduler.scheduleDelayed(() -> {
             if (war == null || war.getColony() == null) {
                 return;
             }
+            // Guard: the war may have ended during the JOINING phase (operator /warstop,
+            // finalize abort, etc.). endWar() removes it from ACTIVE_WARS, so if this
+            // delayed task is no longer the active war for its colony, do NOT start it —
+            // otherwise it would resurrect an ended war and re-enable war permissions
+            // (codex HIGH).
+            if (ACTIVE_WARS.get(war.getColony().getID()) != war) {
+                return;
+            }
             war.setStatus(WarData.WarStatus.INWAR);
             war.warStartTime = System.currentTimeMillis();
             finalizeWarStart(war);
+            // finalizeWarStart() itself ends the war (calls endWar) when there are no valid
+            // participants / a bad ratio. If it did, the war is gone from ACTIVE_WARS — do
+            // NOT enable war permissions or start the countdown on an ended war (codex HIGH).
+            if (ACTIVE_WARS.get(war.getColony().getID()) != war) {
+                return;
+            }
             // Enable war actions for both sides
             setWarInteractionPermissions(war.getColony(), true);
             if (war.getAttackerColony() != null) {
@@ -3833,32 +3862,38 @@ public class WarSystem {
         if (building == null)
             return false;
 
+        // Display name can be a PER-INSTANCE custom name (a player can rename a building),
+        // so it must be evaluated per building — never cached by class. It's a cheap getter.
+        String displayName = building.getBuildingDisplayName();
+        if (displayName != null && "Guard Tower".equalsIgnoreCase(displayName)) {
+            return true;
+        }
+
+        // Class name and the schematic/structure name (from toString) are CLASS-stable —
+        // cache that determination by class so the expensive building.toString() runs at
+        // most once per building TYPE, not once per building each tax cycle (audit H8 +
+        // codex correctness follow-up: the previous code cached the per-instance displayName
+        // result by class, which could mis-count custom-named buildings).
         Boolean cached = GUARD_TOWER_CLASS_CACHE.get(building.getClass());
         if (cached != null) {
             return cached;
         }
-        boolean result = computeIsGuardTower(building);
+        boolean result = computeClassIsGuardTower(building);
         GUARD_TOWER_CLASS_CACHE.put(building.getClass(), result);
         return result;
     }
 
-    private static boolean computeIsGuardTower(IBuilding building) {
-        // Method 1: Check display name (current approach)
-        String displayName = building.getBuildingDisplayName();
-        if (displayName != null && "Guard Tower".equalsIgnoreCase(displayName)) {
-            return true;
-        }
-
+    private static boolean computeClassIsGuardTower(IBuilding building) {
         // Method 2: Check if class name contains "guardtower"
         String className = building.getClass().getName().toLowerCase();
         if (className.contains("guardtower")) {
             return true;
         }
 
-        // Method 3: Check if the building has guard-related functionality
-        // This is a fallback in case the building class structure changes
+        // Method 3: Fallback on the schematic name in case the class structure changes.
+        // The "guardtower" substring comes from the structure type (class-level), so the
+        // match is class-stable even though toString may also contain instance data.
         try {
-            // Try to get the schematic name if available
             String toString = building.toString().toLowerCase();
             if (toString.contains("guardtower") || toString.contains("guard_tower")) {
                 return true;
@@ -4461,6 +4496,12 @@ public class WarSystem {
                     w.setStatus(WarData.WarStatus.INWAR);
                     w.warStartTime = System.currentTimeMillis();
                     finalizeWarStart(w);
+                    // finalizeWarStart() can end the war (no valid participants / bad ratio),
+                    // removing it from ACTIVE_WARS. Don't enable permissions/countdown on an
+                    // ended war (codex HIGH — same guard as the live join-start path).
+                    if (ACTIVE_WARS.get(colonyId) != w) {
+                        return;
+                    }
                     setWarInteractionPermissions(w.getColony(), true);
                     if (w.getAttackerColony() != null) {
                         setWarInteractionPermissions(w.getAttackerColony(), true);

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff -- src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 267ms:
diff --git a/src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java b/src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java
index 07c95c1..c495005 100644
--- a/src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java
@@ -293,7 +293,7 @@ public class ColonyAbandonmentManager {
                     permissions.addPlayer(newOwner, "[AUTO_OWNER]", permissions.getRankOwner());
                     LOGGER.warn("No players found in colony {} during abandonment - created system owner placeholder", colony.getName());
                 }
-                permissions.setPlayerRank(newOwner, permissions.getRankOwner(), colony.getWorld());
+                safeSetPlayerRank(colony, permissions, newOwner, permissions.getRankOwner());
                 if (TaxConfig.isDebugLogging()) LOGGER.debug("Assigned {} as owner placeholder for abandoned colony {}", newOwner, colony.getName());
                 colonyOwner = newOwner;
             } else {
@@ -306,7 +306,7 @@ public class ColonyAbandonmentManager {
                 if (!playerId.equals(colonyOwner)) {
                     ColonyPlayer player = allPlayers.get(playerId);
                     if (!player.getRank().equals(colonyNeutralRank)) {
-                        boolean rankSet = permissions.setPlayerRank(playerId, colonyNeutralRank, colony.getWorld());
+                        boolean rankSet = safeSetPlayerRank(colony, permissions, playerId, colonyNeutralRank);
                         if (TaxConfig.isDebugLogging()) LOGGER.debug("Set non-owner player {} to neutral rank: {}", playerId, rankSet);
                     }
                 }
@@ -461,52 +461,79 @@ public class ColonyAbandonmentManager {
         }
     }
     
+    /** "Are there any abandoned colonies at all?" — for hot-path early-outs (audit H7).
+     *  Calls the lazy one-shot loadData() (guarded by a LOADED flag, so cheap after the
+     *  first call) so a fresh server start sees persisted abandonment state before the
+     *  first block event — matching isColonyAbandoned(). */
+    public static boolean hasAbandonedColonies() {
+        loadData();
+        return !abandonedColonies.isEmpty();
+    }
+
+    /**
+     * Pure read — returns whether the colony is currently flagged abandoned.
+     *
+     * 4.x world-brick fix: this used to MUTATE colony state as a side effect of a status
+     * read — it injected a synthetic '[AUTO_EMERGENCY_OWNER]' into any colony that momentarily
+     * reported a null owner (e.g. while still loading from disk), which corrupted the colony's
+     * saved permission data and could brick the world on the next load. A status check must
+     * never write. Any null-owner repair now lives exclusively in the gated, deferred
+     * {@link #emergencyFixAllNullOwners()} pass.
+     */
     public static boolean isColonyAbandoned(IColony colony) {
         if (colony == null || colony.getPermissions() == null) {
             return false;
         }
         loadData(); // lazy one-shot load — see LOADED flag
+        return abandonedColonies.contains(colony.getID());
+    }
 
-        UUID owner = colony.getPermissions().getOwner();
-        if (owner == null) {
-            LOGGER.warn("Colony {} has null owner - attempting fix", colony.getName());
-            fixNullOwnerColony(colony);
-            owner = colony.getPermissions().getOwner();
-            if (owner == null) {
-                LOGGER.warn("Failed to fix null owner for colony {} - creating emergency placeholder", colony.getName());
-                try {
-                    UUID systemOwner = createSystemOwner();
-                    colony.getPermissions().addPlayer(systemOwner, "[AUTO_EMERGENCY_OWNER]", colony.getPermissions().getRankOwner());
-                    colony.getPermissions().setPlayerRank(systemOwner, colony.getPermissions().getRankOwner(), colony.getWorld());
-                } catch (Exception e) {
-                    LOGGER.warn("Emergency system owner creation failed for colony {}: {}", colony.getName(), e.getMessage());
-                }
-            }
+    /**
+     * Null-world-safe wrapper around {@link IPermissions#setPlayerRank}. Skips the operation
+     * (returns false) when the colony world is null, so we never pass a null Level into
+     * MineColonies' permission system (4.x world-brick hardening).
+     */
+    private static boolean safeSetPlayerRank(IColony colony, IPermissions permissions, UUID playerId, Rank rank) {
+        Level world = colony.getWorld();
+        if (world == null) {
+            LOGGER.warn("Skipping setPlayerRank for colony {} — world is null", colony.getName());
+            return false;
         }
-        
-        return abandonedColonies.contains(colony.getID());
+        return permissions.setPlayerRank(playerId, rank, world);
     }
     
+    /**
+     * Best-effort repair for a colony whose cached owner is null.
+     *
+     * 4.x world-brick fix: the old implementation wrote a placeholder owner via setPlayerRank
+     * (which does NOT update the cached ownerUUID, so it didn't even fix getOwner()) AND
+     * flagged the colony abandoned as a side effect — turning a transient null-owner blip into
+     * a permanently-corrupted, falsely-abandoned colony. The new behavior:
+     *   - promote an existing REAL colony-manager player to owner via setOwner (the only call
+     *     that actually updates the cached owner) when one is currently online;
+     *   - otherwise leave the colony completely untouched (no synthetic placeholder, no
+     *     abandoned flag). It will be repaired naturally when a manager next logs in.
+     */
     private static void fixNullOwnerColony(IColony colony) {
         try {
             IPermissions permissions = colony.getPermissions();
-            UUID newOwner = null;
+            Level world = colony.getWorld();
+            if (world == null || world.getServer() == null) {
+                return; // cannot safely resolve online players without a server context
+            }
             for (ColonyPlayer player : permissions.getPlayers().values()) {
-                if (player.getID() != null) {
-                    newOwner = player.getID();
-                    break;
+                if (player.getID() == null || isSystemOwner(player.getID())) continue;
+                if (player.getRank() == null || !player.getRank().isColonyManager()) continue;
+                ServerPlayer online = world.getServer().getPlayerList().getPlayer(player.getID());
+                if (online != null) {
+                    permissions.setOwner(online);
+                    if (TaxConfig.isNormalLogging()) LOGGER.info("Restored {} as owner of null-owner colony {}", player.getName(), colony.getName());
+                    return;
                 }
             }
-            if (newOwner != null) {
-                permissions.setPlayerRank(newOwner, permissions.getRankOwner(), colony.getWorld());
-                abandonedColonies.add(colony.getID());
-                saveData(); // AUDIT FIX (defensive_04 M2): persist abandoned-flag for null-owner repair
-                if (TaxConfig.isNormalLogging()) LOGGER.info("Assigned {} as owner placeholder for null-owner colony {} and marked abandoned", newOwner, colony.getName());
-            } else {
-                LOGGER.warn("No players found in null-owner colony {} - cannot fix", colony.getName());
-            }
+            if (TaxConfig.isDebugLogging()) LOGGER.debug("Null-owner colony {} has no online manager to promote; leaving untouched", colony.getName());
         } catch (Exception e) {
-            LOGGER.error("Failed to fix null owner colony {}: {}", colony.getName(), e.getMessage());
+            LOGGER.error("Failed to repair null-owner colony {}: {}", colony.getName(), e.getMessage());
         }
     }
     
@@ -514,47 +541,122 @@ public class ColonyAbandonmentManager {
      * Scans all colonies and assigns a placeholder owner to any that have null owners.
      * Idempotent — safe to call multiple times. Logs only when repairs are needed.
      */
+    /**
+     * Scans all colonies and attempts to restore a real owner for any whose cached owner is
+     * null. Idempotent and gated by the abandonment master switch at its call sites.
+     *
+     * 4.x world-brick fix: this NO LONGER injects a synthetic '[AUTO_OWNER]' placeholder into
+     * colonies with no players, and NO LONGER flags any colony abandoned. Both behaviors
+     * previously wrote corrupt/fake data into MineColonies colony state. Repair is now delegated
+     * to {@link #fixNullOwnerColony(IColony)} which only ever promotes a real online manager.
+     */
     public static void emergencyFixAllNullOwners() {
         try {
             IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
-            int fixedColonies = 0;
+            int repaired = 0;
+
+            for (IColony colony : colonyManager.getAllColonies()) {
+                try {
+                    if (colony.getPermissions().getOwner() != null) continue;
+                    fixNullOwnerColony(colony);
+                    if (colony.getPermissions().getOwner() != null) repaired++;
+                } catch (Exception e) {
+                    LOGGER.error("Error repairing null owner in colony {}: {}", colony.getName(), e.getMessage());
+                }
+            }
+
+            if (repaired > 0 && TaxConfig.isNormalLogging()) {
+                LOGGER.info("Null-owner repair complete: {} colonies restored to a real owner", repaired);
+            }
+        } catch (Exception e) {
+            LOGGER.warn("Error during null-owner scan: {}", e.getMessage());
+        }
+    }
+
+    /**
+     * One-time, REMOVAL-ONLY migration that heals colonies corrupted by older versions which
+     * injected synthetic '[AUTO_OWNER]' / '[AUTO_EMERGENCY_OWNER]' / '[SYSTEM_ABANDONED]' /
+     * system-UUID placeholder entries into MineColonies permissions.
+     *
+     * Runs regardless of the abandonment master switch because it only ever REMOVES the mod's
+     * own synthetic data — it never adds owners and never flags colonies abandoned. Safety
+     * rule: never leave a colony ownerless. If a synthetic entry is the current owner, it is
+     * only removed after a real online colony-manager is promoted to owner; if none is
+     * available, the synthetic owner is left in place (an ownerless colony is worse).
+     */
+    public static void repairLegacySyntheticOwners() {
+        try {
+            IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
+            int coloniesHealed = 0;
 
             for (IColony colony : colonyManager.getAllColonies()) {
                 try {
                     IPermissions permissions = colony.getPermissions();
-                    if (permissions.getOwner() != null) continue;
+                    UUID owner = permissions.getOwner();
 
-                    UUID emergencyOwner = null;
-                    for (ColonyPlayer player : permissions.getPlayers().values()) {
-                        if (player.getID() != null) {
-                            emergencyOwner = player.getID();
-                            break;
-                        }
+                    List<UUID> synthetic = new ArrayList<>();
+                    for (ColonyPlayer p : permissions.getPlayers().values()) {
+                        UUID id = p.getID();
+                        if (id == null) continue;
+                        String name = p.getName();
+                        boolean isSynthetic = isSystemOwner(id)
+                                || (name != null && (name.equals("[AUTO_OWNER]")
+                                        || name.equals("[AUTO_EMERGENCY_OWNER]")
+                                        || name.equals("[SYSTEM_ABANDONED]")
+                                        || name.contains("[abandoned]")));
+                        if (isSynthetic) synthetic.add(id);
                     }
-                    if (emergencyOwner == null) {
-                        emergencyOwner = createSystemOwner();
-                        permissions.addPlayer(emergencyOwner, "[AUTO_OWNER]", permissions.getRankOwner());
-                        LOGGER.warn("No players in colony {} during null-owner fix - created system owner placeholder", colony.getName());
+                    if (synthetic.isEmpty()) continue;
+
+                    if (owner != null && synthetic.contains(owner)) {
+                        ServerPlayer replacement = findOnlineRealManager(colony, synthetic);
+                        if (replacement != null) {
+                            permissions.setOwner(replacement);
+                            if (TaxConfig.isNormalLogging()) LOGGER.info("Legacy repair: handed ownership of colony {} from a synthetic placeholder to {}",
+                                    colony.getName(), replacement.getName().getString());
+                        } else {
+                            // No safe online replacement — keep the synthetic owner for now and
+                            // only strip the non-owner synthetic entries below.
+                            synthetic.remove(owner);
+                        }
                     }
-                    permissions.setPlayerRank(emergencyOwner, permissions.getRankOwner(), colony.getWorld());
-                    abandonedColonies.add(colony.getID());
-                    fixedColonies++;
-                    if (TaxConfig.isNormalLogging()) LOGGER.info("Null-owner fix: colony {} assigned owner {} and marked abandoned", colony.getName(), emergencyOwner);
 
+                    int removed = 0;
+                    for (UUID id : synthetic) {
+                        try {
+                            permissions.removePlayer(id);
+                            removed++;
+                        } catch (Exception ignored) {}
+                    }
+                    if (removed > 0) {
+                        coloniesHealed++;
+                        if (TaxConfig.isNormalLogging()) LOGGER.info("Legacy repair: removed {} synthetic placeholder entr{} from colony {}",
+                                removed, removed == 1 ? "y" : "ies", colony.getName());
+                    }
                 } catch (Exception e) {
-                    LOGGER.error("Error fixing null owner in colony {}: {}", colony.getName(), e.getMessage());
+                    LOGGER.error("Legacy synthetic-owner repair failed for colony {}: {}", colony.getName(), e.getMessage());
                 }
             }
-
-            if (fixedColonies > 0 && TaxConfig.isNormalLogging()) {
-                LOGGER.info("Null-owner fix complete: {} colonies repaired", fixedColonies);
-            }
-            if (fixedColonies > 0) {
-                saveData(); // AUDIT FIX (defensive_04 M2): persist any newly-flagged abandonments
+            if (coloniesHealed > 0 && TaxConfig.isNormalLogging()) {
+                LOGGER.info("Legacy synthetic-owner repair complete: {} colonies healed", coloniesHealed);
             }
         } catch (Exception e) {
-            LOGGER.warn("Error during null-owner scan: {}", e.getMessage());
+            LOGGER.warn("Error during legacy synthetic-owner repair: {}", e.getMessage());
+        }
+    }
+
+    /** Returns an online colony-manager player who is NOT one of the given synthetic UUIDs, or null. */
+    private static ServerPlayer findOnlineRealManager(IColony colony, java.util.Collection<UUID> exclude) {
+        Level world = colony.getWorld();
+        if (world == null || world.getServer() == null) return null;
+        for (ColonyPlayer p : colony.getPermissions().getPlayers().values()) {
+            UUID id = p.getID();
+            if (id == null || exclude.contains(id) || isSystemOwner(id)) continue;
+            if (p.getRank() == null || !p.getRank().isColonyManager()) continue;
+            ServerPlayer online = world.getServer().getPlayerList().getPlayer(id);
+            if (online != null) return online;
         }
+        return null;
     }
     
     /**
@@ -730,7 +832,7 @@ public class ColonyAbandonmentManager {
             for (UUID playerId : allPlayers.keySet()) {
                 ColonyPlayer player = allPlayers.get(playerId);
                 if (!player.getRank().equals(neutralRank)) {
-                    permissions.setPlayerRank(playerId, neutralRank, colony.getWorld());
+                    safeSetPlayerRank(colony, permissions, playerId, neutralRank);
                 }
             }
 
@@ -795,40 +897,31 @@ public class ColonyAbandonmentManager {
 
                 boolean isProblematic = false;
                 String reason = "";
-                
+
+                // 4.x world-brick fix: match ONLY the exact synthetic markers the mod itself
+                // ever wrote. The old heuristics — ANY name containing "abandoned", names
+                // starting with ~ or #, empty/null names, and a bogus UUID-length check —
+                // risked deleting a legitimate (possibly just-added or name-unresolved) player
+                // and leaving the colony ownerless, which crashes the town hall GUI.
                 if (playerId == null) {
                     isProblematic = true;
                     reason = "null UUID";
                 } else if (player == null) {
-                    isProblematic = true; 
-                    reason = "null player object";
-                } else if (player.getName() == null) {
-                    isProblematic = true;
-                    reason = "null player name";
-                } else if (player.getName().equals("")) {
                     isProblematic = true;
-                    reason = "empty player name";
-                } else if (player.getName().contains("[abandoned]")) {
-                    isProblematic = true;
-                    reason = "contains [abandoned]";
-                } else if (player.getName().toLowerCase().contains("abandoned")) {
+                    reason = "null player object";
+                } else if (isSystemOwner(playerId)) {
                     isProblematic = true;
-                    reason = "contains 'abandoned'";
-                } else if (player.getName().equals("[SYSTEM_ABANDONED]") || isSystemOwner(playerId)) {
+                    reason = "synthetic system-owner UUID";
+                } else if (playerId.equals(new UUID(0L, 0L))) {
                     isProblematic = true;
-                    reason = "old system owner entry";
-                } else if (player.getName().startsWith("~") || player.getName().startsWith("#")) {
+                    reason = "zero UUID";
+                } else if (player.getName() != null
+                        && (player.getName().equals("[AUTO_OWNER]")
+                                || player.getName().equals("[AUTO_EMERGENCY_OWNER]")
+                                || player.getName().equals("[SYSTEM_ABANDONED]")
+                                || player.getName().contains("[abandoned]"))) {
                     isProblematic = true;
-                    reason = "suspicious name prefix";
-                } else {
-                    // Check for invalid UUID patterns that might indicate corruption
-                    String uuidStr = playerId.toString();
-                    if (uuidStr.equals("00000000-0000-0000-0000-000000000000") || 
-                        uuidStr.contains("abandoned") || 
-                        uuidStr.length() != 36) {
-                        isProblematic = true;
-                        reason = "invalid UUID pattern";
-                    }
+                    reason = "synthetic placeholder entry (" + player.getName() + ")";
                 }
                 
                 if (isProblematic) {
@@ -898,24 +991,25 @@ public class ColonyAbandonmentManager {
             for (ColonyPlayer player : permissions.getPlayers().values()) {
                 if (!isSystemOwner(player.getID()) && player.getRank().isColonyManager()) {
                     try {
-                        java.lang.reflect.Method setOwnerMethod = permissions.getClass().getMethod("setOwner", UUID.class);
-                        setOwnerMethod.invoke(permissions, player.getID());
+                        // MineColonies changed IPermissions.setOwner(UUID) -> setOwner(Player) in
+                        // 1.1.1237, which broke the old reflection ("argument type mismatch") and
+                        // left colonies ownerless. setOwner(Player) updates the cached ownerUUID
+                        // that getOwner() returns; setPlayerRank does NOT. Prefer setOwner when the
+                        // target manager is online; fall back to a best-effort rank assignment when
+                        // offline (the new API cannot set an offline player as the cached owner).
+                        // [1.21-PORT] same limitation on NeoForge/1.21 — see PORTING_NOTES.md.
+                        net.minecraft.server.level.ServerPlayer online =
+                                (colony.getWorld() != null && colony.getWorld().getServer() != null)
+                                        ? colony.getWorld().getServer().getPlayerList().getPlayer(player.getID())
+                                        : null;
+                        if (online != null) {
+                            permissions.setOwner(online);
+                        } else {
+                            safeSetPlayerRank(colony, permissions, player.getID(), permissions.getRankOwner());
+                        }
                         if (TaxConfig.isNormalLogging()) LOGGER.info("Set {} as owner of reactivated colony {}", player.getName(), colony.getName());
-                        break;
                     } catch (Exception e) {
-                        LOGGER.warn("Could not set {} as owner directly, trying via reflection: {}", player.getName(), e.getMessage());
-                        try {
-                            for (java.lang.reflect.Method method : permissions.getClass().getDeclaredMethods()) {
-                                if (method.getName().equals("setOwner") && method.getParameterCount() == 1) {
-                                    method.setAccessible(true);
-                                    method.invoke(permissions, player.getID());
-                                    if (TaxConfig.isNormalLogging()) LOGGER.info("Set {} as owner of reactivated colony {} (via reflection)", player.getName(), colony.getName());
-                                    break;
-                                }
-                            }
-                        } catch (Exception e2) {
-                            LOGGER.error("Failed to set {} as owner of colony {}: {}", player.getName(), colony.getName(), e2.getMessage());
-                        }
+                        LOGGER.error("Failed to set {} as owner of colony {}: {}", player.getName(), colony.getName(), e.getMessage());
                     }
                     break;
                 }

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff -- src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 274ms:
diff --git a/src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java b/src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java
index 97df1c2..05ba32f 100644
--- a/src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java
@@ -793,8 +793,13 @@ public class ColonyClaimingRaidManager {
             // STEP 1: Set claimer as OWNER (they've earned it by completing the raid!)
             boolean wasAlreadyInColony = permissions.getPlayers().containsKey(claimingPlayer.getUUID());
             if (wasAlreadyInColony) {
-                // Player was already in colony - promote to Owner
-                permissions.setPlayerRank(claimingPlayer.getUUID(), permissions.getRankOwner(), colony.getWorld());
+                // Player was already in colony - promote to Owner.
+                // Null-world guard (4.x world-brick hardening): never pass a null Level into
+                // MineColonies' permission system. setOwner(claimingPlayer) below is the
+                // authoritative owner update regardless.
+                if (colony.getWorld() != null) {
+                    permissions.setPlayerRank(claimingPlayer.getUUID(), permissions.getRankOwner(), colony.getWorld());
+                }
                 if (TaxConfig.isNormalLogging()) LOGGER.info("CLAIMING SUCCESS: Promoted existing player {} to OWNER of colony {}",
                     claimingPlayer.getName().getString(), colony.getName());
             } else {
@@ -804,27 +809,18 @@ public class ColonyClaimingRaidManager {
                     claimingPlayer.getName().getString(), colony.getName());
             }
             
-            // CRITICAL: Set the claiming player as the actual owner to prevent GUI crashes
+            // CRITICAL: Set the claiming player as the actual owner to prevent GUI crashes.
+            // MineColonies changed IPermissions.setOwner(UUID) -> setOwner(Player) in 1.1.1237,
+            // which broke the old reflection ("argument type mismatch") and left the colony
+            // ownerless. setOwner(Player) updates the cached ownerUUID that getOwner() returns
+            // (setPlayerRank does NOT). claimingPlayer is an online ServerPlayer, so this is the
+            // correct, complete fix. [1.21-PORT] re-verify the owner API — see PORTING_NOTES.md.
             try {
-                java.lang.reflect.Method setOwnerMethod = permissions.getClass().getMethod("setOwner", UUID.class);
-                setOwnerMethod.invoke(permissions, claimingPlayer.getUUID());
+                permissions.setOwner(claimingPlayer);
                 if (TaxConfig.isNormalLogging()) LOGGER.info("Claiming owner set: {} is now the actual owner of claimed colony {}",
                     claimingPlayer.getName().getString(), colony.getName());
             } catch (Exception e) {
-                LOGGER.warn("Could not set claiming player as actual owner directly, trying alternative: {}", e.getMessage());
-                try {
-                    for (java.lang.reflect.Method method : permissions.getClass().getDeclaredMethods()) {
-                        if (method.getName().equals("setOwner") && method.getParameterCount() == 1) {
-                            method.setAccessible(true);
-                            method.invoke(permissions, claimingPlayer.getUUID());
-                            if (TaxConfig.isNormalLogging()) LOGGER.info("Claiming owner set (alt): {} is now the actual owner of claimed colony {}",
-                                claimingPlayer.getName().getString(), colony.getName());
-                            break;
-                        }
-                    }
-                } catch (Exception e2) {
-                    LOGGER.error("Failed to set claiming player as actual owner: {}", e2.getMessage());
-                }
+                LOGGER.error("Failed to set claiming player as actual owner: {}", e.getMessage());
             }
             
             // STEP 2: Restore normal permissions for neutral players (they were restricted during abandonment)
@@ -1048,6 +1044,11 @@ public class ColonyClaimingRaidManager {
     public static boolean isColonyUnderClaimingRaid(int colonyId) {
         return activeClaimingRaids.containsKey(colonyId);
     }
+
+    /** O(1) "is any claiming raid active?" — for hot-path early-outs (audit H5). */
+    public static boolean hasActiveClaimingRaids() {
+        return !activeClaimingRaids.isEmpty();
+    }
     
     /**
      * Get the claiming raid data for a colony.
diff --git a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java
index c397ca0..84080a5 100644
--- a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java
+++ b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java
@@ -38,6 +38,10 @@ public class BesiegeDamageShieldHandler {
     private static final Map<UUID, Long> LAST_BLOCK_MESSAGE = new HashMap<>();
     private static final long BLOCK_MESSAGE_COOLDOWN_MS = 3000;
 
+    // How long a cached colony-mate determination stays valid. Short enough to pick
+    // up mid-besiege rank changes, long enough to collapse a combat hit-burst.
+    private static final long COLONY_MATE_CACHE_TTL_MS = 5000;
+
     @SubscribeEvent(priority = EventPriority.HIGH)
     public static void onLivingHurt(LivingHurtEvent event) {
         if (!TaxConfig.isBesiegeSystemEnabled()) return;
@@ -58,7 +62,7 @@ public class BesiegeDamageShieldHandler {
             // Skip the source's own raid — they're allowed to deal damage in their own besiege.
             if (raid.besiegingPlayerUUID.equals(sourceUUID)) continue;
 
-            if (!areColonyMates(source, raid.besiegingPlayerUUID)) continue;
+            if (!areColonyMates(source, raid)) continue;
 
             // Confirm the target belongs to the besieged side: defender citizen,
             // defender-side player, or a mercenary spawned for this raid.
@@ -76,7 +80,28 @@ public class BesiegeDamageShieldHandler {
      * shared colony. Uses {@link Rank#isHostile()} so custom hostile ranks are
      * caught (not just the default Hostile rank instance).
      */
-    private static boolean areColonyMates(ServerPlayer source, UUID besiegerUUID) {
+    private static boolean areColonyMates(ServerPlayer source, BesiegeManager.BesiegeRaidData raid) {
+        UUID besiegerUUID = raid.besiegingPlayerUUID;
+        if (besiegerUUID == null) return false;
+        // Short-TTL cache per source — LivingHurtEvent is extremely high-frequency and
+        // the colony scan below is O(allColonies). The TTL collapses a combat hit-burst
+        // to one scan per few seconds per attacker while still re-checking often enough
+        // to catch a mid-besiege rank change (audit C3 + codex follow-up).
+        long now = System.currentTimeMillis();
+        long[] entry = raid.colonyMateCache.get(source.getUUID());
+        if (entry != null && now < entry[1]) return entry[0] != 0;
+        boolean result = computeColonyMates(source, besiegerUUID);
+        raid.colonyMateCache.put(source.getUUID(), new long[]{ result ? 1 : 0, now + COLONY_MATE_CACHE_TTL_MS });
+        return result;
+    }
+
+    /**
+     * True when both players hold a non-neutral, non-hostile rank in any single
+     * shared colony. Uses {@link Rank#isHostile()} so custom hostile ranks are
+     * caught (not just the default Hostile rank instance). O(allColonies) — only
+     * called once per (raid, source) thanks to {@link #areColonyMates} caching.
+     */
+    private static boolean computeColonyMates(ServerPlayer source, UUID besiegerUUID) {
         if (source.level().getServer() == null) return false;
         try {
             for (IColony colony : com.minecolonies.api.colony.IColonyManager.getInstance().getAllColonies()) {
diff --git a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
index 8874afe..ad5b81b 100644
--- a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
@@ -366,6 +366,7 @@ public class BesiegeManager {
 
         try {
             BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
+            try { raid.dimension = colony.getDimension(); } catch (Exception ignored) { /* fallback handled in mixin */ }
 
             // Step 3 Phase 2 — multi-besieger shared defender pool.
             // Find any already-active raid on this colony BEFORE inserting our own.
@@ -1068,6 +1069,15 @@ public class BesiegeManager {
         return Collections.unmodifiableMap(view);
     }
 
+    /**
+     * O(1), allocation-free "is any besiege active?" check. Use this on hot guard
+     * paths (e.g. the block-interaction filter) instead of {@code getActiveRaids().isEmpty()},
+     * which builds and discards a HashMap on every call (audit H6).
+     */
+    public static boolean hasActiveRaids() {
+        return !ACTIVE_RAIDS.isEmpty();
+    }
+
     /** Direct lookup by besieger UUID. Null when this player has no active raid. */
     public static BesiegeRaidData getRaidForBesieger(UUID besiegerUUID) {
         return ACTIVE_RAIDS.get(besiegerUUID);
@@ -1361,6 +1371,23 @@ public class BesiegeManager {
          */
         public boolean isSecondaryRaider = false;
 
+        /**
+         * Short-TTL cache of "is this player a colony-mate of the besieger?" keyed
+         * by the damage source's UUID. Value = {@code long[]{result(0/1), expiryMs}}.
+         * LivingHurtEvent fires constantly, so without this the damage shield scanned
+         * ALL colonies on every hit; the TTL collapses per-hit bursts to one scan per
+         * few seconds per attacker while still catching mid-besiege rank changes
+         * (audit C3 + codex follow-up).
+         */
+        public final Map<UUID, long[]> colonyMateCache = new ConcurrentHashMap<>();
+
+        /**
+         * Dimension the besieged colony lives in, cached at launch so the Explosion't
+         * war-aware mixin can match the ticking level in O(1) instead of scanning all
+         * colonies every level tick (audit H12). May be null (e.g. a restored raid).
+         */
+        public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
+
         public BesiegeRaidData(int colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
             this.colonyId = colonyId;
             this.besiegingPlayerUUID = besiegingPlayerUUID;
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java', LF will be replaced by CRLF the next time Git touches it

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff -- src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 350ms:
diff --git a/src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java b/src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java
index 7afe85a..ff1dd85 100644
--- a/src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java
+++ b/src/main/java/net/machiavelli/minecolonytax/commands/ClaimTaxCommand.java
@@ -137,24 +137,23 @@ public class ClaimTaxCommand {
                 int totalClaimed = TaxManager.claimTax(colony, amount);
 
                 if (totalClaimed > 0) {
-                    try {
-                        net.minecraft.advancements.Advancement adv = player.getServer().getAdvancements()
-                                .getAdvancement(
-                                        new net.minecraft.resources.ResourceLocation("minecolonytax:codex/claim_tax"));
-                        if (adv != null) {
-                            player.getAdvancements().award(adv, "check");
+                    // Deliver the claimed tax, refunding the colony ledger if delivery fails so
+                    // taxes are never silently lost (4.x "coins never appear" fix). The helper
+                    // emits its own failure/refund message; only confirm + award on success.
+                    if (net.machiavelli.minecolonytax.integration.CurrencyService
+                            .deliverClaimedTaxOrRefund(player, colony, totalClaimed)) {
+                        try {
+                            net.minecraft.advancements.Advancement adv = player.getServer().getAdvancements()
+                                    .getAdvancement(
+                                            new net.minecraft.resources.ResourceLocation("minecolonytax:codex/claim_tax"));
+                            if (adv != null) {
+                                player.getAdvancements().award(adv, "check");
+                            }
+                        } catch (Exception e) {
                         }
-                    } catch (Exception e) {
-                    }
-
-                    player.sendSystemMessage(Component.translatable("command.claimtax.success",
-                            colony.getName(), totalClaimed));
 
-                    if (TaxConfig.isSDMShopConversionEnabled() && SDMShopIntegration.isAvailable()) {
-                        long currentBalance = SDMShopIntegration.getMoney(player);
-                        SDMShopIntegration.setMoney(player, currentBalance + totalClaimed);
-                    } else {
-                        net.machiavelli.minecolonytax.util.ItemUtils.giveCurrencyToPlayer(player, totalClaimed);
+                        player.sendSystemMessage(Component.translatable("command.claimtax.success",
+                                colony.getName(), totalClaimed));
                     }
                 } else {
                     player.sendSystemMessage(Component.translatable("command.claimtax.no_tax", colony.getName()));
diff --git a/src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java b/src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java
index 704e534..98ced43 100644
--- a/src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java
+++ b/src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java
@@ -420,6 +420,33 @@ public class WntCommands {
                                                                                 }))))
 
                                 .then(Commands.literal("debug")
+                                                .then(Commands.literal("sdm")
+                                                                .then(Commands.literal("status")
+                                                                                .executes(WntCommands::showSdmStatus)))
+                                                .then(Commands.literal("emergencyfix")
+                                                                .requires(source -> source.hasPermission(2))
+                                                                .executes(WntCommands::handleEmergencyFix))
+                                                .then(Commands.literal("fixnullowners")
+                                                                .requires(source -> source.hasPermission(2))
+                                                                .executes(WntCommands::handleFixNullOwners))
+                                                .then(Commands.literal("cleanupabandonedentries")
+                                                                .requires(source -> source.hasPermission(2))
+                                                                .executes(WntCommands::handleCleanupAbandonedEntries))
+                                                .then(Commands.literal("forcecleanupcolony")
+                                                                .requires(source -> source.hasPermission(2))
+                                                                .then(Commands.argument("colony", StringArgumentType.string())
+                                                                                .suggests(COLONY_SUGGESTIONS)
+                                                                                .executes(WntCommands::handleForceCleanupColony)))
+                                                .then(Commands.literal("bossbar")
+                                                                .requires(source -> source.hasPermission(2))
+                                                                .then(Commands.argument("colony", StringArgumentType.string())
+                                                                                .suggests(COLONY_SUGGESTIONS)
+                                                                                .executes(WntCommands::handleDebugBossBar)))
+                                                .then(Commands.literal("claimraidstatus")
+                                                                .requires(source -> source.hasPermission(2))
+                                                                .then(Commands.argument("colony", StringArgumentType.string())
+                                                                                .suggests(ABANDONED_COLONY_SUGGESTIONS)
+                                                                                .executes(WntCommands::checkClaimingRaidStatus)))
                                                 .then(Commands.literal("war")
                                                                 .requires(src -> src.hasPermission(2))
                                                                 .executes(WntCommands::debugWarCommand))
@@ -523,11 +550,6 @@ public class WntCommands {
                                                 .executes(WntCommands::listAbandonedColonies))
                                 .then(Commands.literal("claimstatus")
                                                 .executes(WntCommands::checkClaimingStatus))
-                                .then(Commands.literal("claimraidstatus")
-                                                .requires(source -> source.hasPermission(2))
-                                                .then(Commands.argument("colony", StringArgumentType.string())
-                                                                .suggests(ABANDONED_COLONY_SUGGESTIONS)
-                                                                .executes(WntCommands::checkClaimingRaidStatus)))
                                 .then(Commands.literal("protectcolony")
                                                 .requires(source -> source.hasPermission(2))
                                                 .then(Commands.argument("colony", StringArgumentType.string())
@@ -539,29 +561,6 @@ public class WntCommands {
                                                                 .suggests(COLONY_SUGGESTIONS)
                                                                 .executes(WntCommands::unprotectColonyFromClaiming)))
 
-                                .then(Commands.literal("cleanupabandonedentries")
-                                                .requires(source -> source.hasPermission(2))
-                                                .executes(WntCommands::handleCleanupAbandonedEntries))
-
-                                .then(Commands.literal("debugbossbar")
-                                                .requires(source -> source.hasPermission(2))
-                                                .then(Commands.argument("colony", StringArgumentType.string())
-                                                                .suggests(COLONY_SUGGESTIONS)
-                                                                .executes(WntCommands::handleDebugBossBar)))
-
-                                .then(Commands.literal("forcecleanupcolony")
-                                                .requires(source -> source.hasPermission(2))
-                                                .then(Commands.argument("colony", StringArgumentType.string())
-                                                                .suggests(COLONY_SUGGESTIONS)
-                                                                .executes(WntCommands::handleForceCleanupColony)))
-
-                                .then(Commands.literal("emergencyfix")
-                                                .requires(source -> source.hasPermission(2))
-                                                .executes(WntCommands::handleEmergencyFix))
-
-                                .then(Commands.literal("fixnullowners")
-                                                .requires(source -> source.hasPermission(2))
-                                                .executes(WntCommands::handleFixNullOwners))
                                 .then(Commands.literal("listprotected")
                                                 .requires(source -> source.hasPermission(2))
                                                 .executes(WntCommands::listProtectedColonies))
@@ -1840,54 +1839,14 @@ public class WntCommands {
 
                                 int claimedAmount = net.machiavelli.minecolonytax.TaxManager.claimTax(colony, amount);
                                 if (claimedAmount > 0) {
-                                        player.sendSystemMessage(
-                                                        Component.translatable("command.claimtax.success",
-                                                                        claimedAmount, colony.getName()));
-
-                                        // Update player's funds using SDMShop API if enabled
-                                        if (TaxConfig.isSDMShopConversionEnabled()
-                                                        && net.machiavelli.minecolonytax.integration.SDMShopIntegration
-                                                                        .isAvailable()) {
-                                                long currentBalance = net.machiavelli.minecolonytax.integration.SDMShopIntegration
-                                                                .getMoney(player);
-                                                net.machiavelli.minecolonytax.integration.SDMShopIntegration
-                                                                .setMoney(player, currentBalance + claimedAmount);
-                                        } else {
-                                                // Use direct inventory manipulation instead of give command for modded
-                                                // items
-                                                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
-                                                                .getValue(
-                                                                                new net.minecraft.resources.ResourceLocation(
-                                                                                                TaxConfig.getCurrencyItemName()));
-                                                if (item != null) {
-                                                        net.minecraft.world.item.ItemStack itemStack = new net.minecraft.world.item.ItemStack(
-                                                                        item,
-                                                                        claimedAmount);
-                                                        boolean added = player.getInventory().add(itemStack);
-                                                        if (!added) {
-                                                                // If inventory is full, drop items near player
-                                                                player.drop(itemStack, false);
-                                                                player.sendSystemMessage(Component.translatable(
-                                                                                "taxmanager.inventory_full",
-                                                                                claimedAmount,
-                                                                                TaxConfig.getCurrencyItemName()));
-                                                        } else {
-                                                                player.sendSystemMessage(Component.translatable(
-                                                                                "taxmanager.currency_received",
-                                                                                claimedAmount,
-                                                                                TaxConfig.getCurrencyItemName()));
-                                                        }
-                                                } else {
-                                                        // Fallback to give command if item not found in registry
-                                                        String itemName = TaxConfig.getCurrencyItemName();
-                                                        String giveCommand = String.format("give %s %s %d",
-                                                                        player.getName().getString(), itemName,
-                                                                        claimedAmount);
-                                                        source.getServer().getCommands()
-                                                                        .performPrefixedCommand(source.getServer()
-                                                                                        .createCommandSourceStack(),
-                                                                                        giveCommand);
-                                                }
+                                        // Deliver the claimed tax, refunding the colony ledger if delivery fails so
+                                        // taxes are never silently lost (4.x "coins never appear" fix). The helper
+                                        // emits its own failure/refund message; only confirm on success.
+                                        if (net.machiavelli.minecolonytax.integration.CurrencyService
+                                                        .deliverClaimedTaxOrRefund(player, colony, claimedAmount)) {
+                                                player.sendSystemMessage(
+                                                                Component.translatable("command.claimtax.success",
+                                                                                claimedAmount, colony.getName()));
                                         }
                                 } else {
                                         player.sendSystemMessage(Component.translatable("command.claimtax.no_tax",
@@ -2266,6 +2225,54 @@ public class WntCommands {
                 }
         }
 
+        /** Diagnostic for the "claimed tax coins never appear" issue — shows the live state of
+         *  the SDMShop/SDM-Economy integration and the caller's wallet balance. */
+        private static int showSdmStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
+                ServerPlayer player = context.getSource().getPlayerOrException();
+
+                boolean conversionEnabled = TaxConfig.isSDMShopConversionEnabled();
+                boolean modPresent = net.machiavelli.minecolonytax.integration.SDMShopIntegration.isModPresent();
+                String mode = net.machiavelli.minecolonytax.integration.SDMShopIntegration.getIntegrationMode();
+                boolean serverReady = net.machiavelli.minecolonytax.integration.SDMShopIntegration.isServerInstanceReady();
+                boolean available = net.machiavelli.minecolonytax.integration.SDMShopIntegration.isAvailable();
+                String currency = net.machiavelli.minecolonytax.integration.SDMShopIntegration.getCurrencyName();
+                long balance = available
+                                ? net.machiavelli.minecolonytax.integration.SDMShopIntegration.getMoney(player)
+                                : -1L;
+
+                net.minecraft.network.chat.MutableComponent msg = Component
+                                .literal("SDMShop / SDM-Economy Integration Status")
+                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
+                msg.append(sdmStatusLine("Conversion enabled (EnableSDMShopConversion)", conversionEnabled));
+                msg.append(sdmStatusLine("SDMShop/SDM-Economy mod loaded", modPresent));
+                msg.append(Component.literal("\n- Integration mode: ").withStyle(ChatFormatting.YELLOW)
+                                .append(Component.literal(mode).withStyle(ChatFormatting.WHITE)));
+                msg.append(sdmStatusLine("Economy server instance ready", serverReady));
+                msg.append(sdmStatusLine("Available for payouts", available));
+                msg.append(Component.literal("\n- Currency id (SDMCurrencyName): ").withStyle(ChatFormatting.YELLOW)
+                                .append(Component.literal(currency).withStyle(ChatFormatting.WHITE)));
+                msg.append(Component.literal("\n- Your balance: ").withStyle(ChatFormatting.YELLOW)
+                                .append(Component.literal(balance >= 0 ? String.valueOf(balance) : "n/a")
+                                                .withStyle(ChatFormatting.WHITE)));
+
+                if (conversionEnabled && !available) {
+                        msg.append(Component.literal(
+                                        "\nConversion is ON but the economy is unavailable — claimed taxes are refunded (not lost). "
+                                      + "Check that SDMShop/SDM-Economy is installed, that the currency id above matches your "
+                                      + "SDM-Economy config, and (single-player) that you have fully loaded into the world.")
+                                        .withStyle(ChatFormatting.RED));
+                }
+
+                player.sendSystemMessage(msg);
+                return 1;
+        }
+
+        private static net.minecraft.network.chat.MutableComponent sdmStatusLine(String label, boolean ok) {
+                return Component.literal("\n- " + label + ": ").withStyle(ChatFormatting.YELLOW)
+                                .append(Component.literal(ok ? "YES" : "NO")
+                                                .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
+        }
+
         private static int showWarStats(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                 ServerPlayer player = context.getSource().getPlayerOrException();
 
@@ -3412,39 +3419,23 @@ public class WntCommands {
                                                         permissions.addPlayer(systemOwner, "[SYSTEM_ABANDONED]",
                                                                         permissions.getRankOwner());
 
-                                                        // CRITICAL FIX: Set actual owner to prevent GUI crashes
+                                                        // CRITICAL FIX: ensure the system owner holds the OWNER rank so
+                                                        // getOwner() resolves (prevents GUI crashes). Version-stable
+                                                        // setPlayerRank, not reflected setOwner — MineColonies changed
+                                                        // setOwner(UUID) -> setOwner(Player) in 1.1.1237, breaking the
+                                                        // old reflection. [1.21-PORT] re-verify — see PORTING_NOTES.md.
                                                         try {
-                                                                java.lang.reflect.Method setOwnerMethod = permissions
-                                                                                .getClass().getMethod("setOwner",
-                                                                                                UUID.class);
-                                                                setOwnerMethod.invoke(permissions, systemOwner);
+                                                                permissions.setPlayerRank(systemOwner,
+                                                                                permissions.getRankOwner(),
+                                                                                colony.getWorld());
                                                                 source.sendSuccess(() -> Component
                                                                                 .literal("    Set system owner as actual owner to prevent GUI crashes")
                                                                                 .withStyle(ChatFormatting.GREEN),
                                                                                 false);
                                                         } catch (Exception e) {
-                                                                try {
-                                                                        for (java.lang.reflect.Method method : permissions
-                                                                                        .getClass()
-                                                                                        .getDeclaredMethods()) {
-                                                                                if (method.getName().equals("setOwner")
-                                                                                                && method.getParameterCount() == 1) {
-                                                                                        method.setAccessible(true);
-                                                                                        method.invoke(permissions,
-                                                                                                        systemOwner);
-                                                                                        source.sendSuccess(
-                                                                                                        () -> Component
-                                                                                                                        .literal("    Set system owner (alt method) to prevent GUI crashes")
-                                                                                                                        .withStyle(ChatFormatting.GREEN),
-                                                                                                        false);
-                                                                                        break;
-                                                                                }
-                                                                        }
-                                                                } catch (Exception e2) {
-                                                                        source.sendFailure(Component
-                                                                                        .literal("    WARNING: Could not set actual owner - GUI may crash!")
-                                                                                        .withStyle(ChatFormatting.RED));
-                                                                }
+                                                                source.sendFailure(Component
+                                                                                .literal("    WARNING: Could not set actual owner - GUI may crash!")
+                                                                                .withStyle(ChatFormatting.RED));
                                                         }
 
                                                         // Set all real players to neutral with no permissions
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java', LF will be replaced by CRLF the next time Git touches it

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Get-ChildItem -Recurse -Path src/main/resources -Filter *.json | Select-String -Pattern 'command.claimtax.success|taxmanager.currency_received|claimtax.success' -Context 0,0" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 386ms:

src\main\resources\assets\minecolonytax\lang\de_de.json:5:  "command.claimtax.success": "Du hast %d Steuereinnahmen 
von der Kolonie %s beansprucht.",
src\main\resources\assets\minecolonytax\lang\de_de.json:8:  "taxmanager.currency_received": "Du hast %d %s erhalten!",
src\main\resources\assets\minecolonytax\lang\en_us.json:13:  "command.claimtax.success": "You have claimed %d in tax 
revenue from colony %s.",
src\main\resources\assets\minecolonytax\lang\en_us.json:16:  "taxmanager.currency_received": "You have received %d 
%s!",
src\main\resources\assets\minecolonytax\lang\es_es.json:5:  "command.claimtax.success": "Has reclamado %d ingresos 
fiscales de la colonia %s.",
src\main\resources\assets\minecolonytax\lang\es_es.json:8:  "taxmanager.currency_received": "¡Has recibido %d %s!",
src\main\resources\assets\minecolonytax\lang\fr_fr.json:5:  "command.claimtax.success": "Vous avez réclamé %d de 
revenus fiscaux de la colonie %s.",
src\main\resources\assets\minecolonytax\lang\fr_fr.json:8:  "taxmanager.currency_received": "Vous avez reçu %d %s !",
src\main\resources\assets\minecolonytax\lang\ru_ru.json:5:  "command.claimtax.success": "Вы получили %d налогового 
дохода от колонии %s.",
src\main\resources\assets\minecolonytax\lang\ru_ru.json:8:  "taxmanager.currency_received": "Вы получили %d %s!",
src\main\resources\assets\minecolonytax\lang\zh_cn.json:5:  "command.claimtax.success": "您已从殖民地 %s 领取了 %d 税收。",
src\main\resources\assets\minecolonytax\lang\zh_cn.json:8:  "taxmanager.currency_received": "您已收到 %d %s！",
src\main\resources\data\minecolonytax\example_datapack\data\minecolonytax\lang\de_de.json:5:  
"command.claimtax.success": "Sie haben %d Steuereinnahmen aus der Kolonie %s erhalten.",
src\main\resources\data\minecolonytax\example_datapack\data\minecolonytax\lang\de_de.json:8:  
"taxmanager.currency_received": "Sie haben %d %s erhalten!",
src\main\resources\data\minecolonytax\example_datapack\data\minecolonytax\lang\en_us.json:5:  
"command.claimtax.success": "You have claimed %d in tax revenue from colony %s.",
src\main\resources\data\minecolonytax\example_datapack\data\minecolonytax\lang\en_us.json:8:  
"taxmanager.currency_received": "You have received %d %s!",
src\main\resources\data\minecolonytax\example_datapack\data\minecolonytax\lang\es_es.json:5:  
"command.claimtax.success": "Has reclamado %d en ingresos fiscales de la colonia %s.",
src\main\resources\data\minecolonytax\example_datapack\data\minecolonytax\lang\es_es.json:8:  
"taxmanager.currency_received": "¡Has recibido %d %s!",
src\main\resources\data\minecolonytax\example_datapack\data\minecolonytax\lang\fr_fr.json:5:  
"command.claimtax.success": "Vous avez reçu %d taxes de la colonie %s.",
src\main\resources\data\minecolonytax\example_datapack\data\minecolonytax\lang\fr_fr.json:8:  
"taxmanager.currency_received": "Vous avez reçu %d %s !",
src\main\resources\data\minecolonytax\example_datapack\data\minecolonytax\lang\ru_ru.json:5:  
"command.claimtax.success": "Вы получили %d налогового дохода от колонии %s.",
src\main\resources\data\minecolonytax\example_datapack\data\minecolonytax\lang\ru_ru.json:8:  
"taxmanager.currency_received": "Вы получили %d %s!",
src\main\resources\data\minecolonytax\example_datapack\data\minecolonytax\lang\zh_cn.json:5:  
"command.claimtax.success": "您已从殖民地 %s 领取了 %d 税收。",
src\main\resources\data\minecolonytax\example_datapack\data\minecolonytax\lang\zh_cn.json:8:  
"taxmanager.currency_received": "您已收到 %d %s！",



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/**/*.java -Pattern 'setOwner\\(|setPlayerRank\\(' | Select-Object Path,LineNumber,Line | Format-Table -AutoSize" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 447ms:

Path                                                                                                                   
----                                                                                                                   
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentMan...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentMan...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentMan...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentMan...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentMan...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentMan...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentMan...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentMan...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentMan...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentMan...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentMan...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentMan...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidMa...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidMa...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidMa...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidMa...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidMa...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java    
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java    
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java      
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java      
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java      
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissio...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissio...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealt...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealt...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealt...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealt...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\permissions\PermissionsHealt...
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java          
C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.java          



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java -Pattern 'setOwner|setPlayerRank' -Context 3,3" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 357ms:

  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3421:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3422:                                          
              // CRITICAL FIX: ensure the system owner holds the OWNER rank so
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3423:                                          
              // getOwner() resolves (prevents GUI crashes). Version-stable
> src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3424:                                          
              // setPlayerRank, not reflected setOwner — MineColonies changed
> src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3425:                                          
              // setOwner(UUID) -> setOwner(Player) in 1.1.1237, breaking the
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3426:                                          
              // old reflection. [1.21-PORT] re-verify — see PORTING_NOTES.md.
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3427:                                          
              try {
> src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3428:                                          
                      permissions.setPlayerRank(systemOwner,
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3429:                                          
                                      permissions.getRankOwner(),
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3430:                                          
                                      colony.getWorld());
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3431:                                          
                      source.sendSuccess(() -> Component
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3442:                                          
              Rank neutralRank = permissions.getRankNeutral();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3443:                                          
              for (UUID playerId : permissions.getPlayers().keySet()) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3444:                                          
                      if (!ColonyAbandonmentManager.isSystemOwner(playerId)) {
> src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3445:                                          
                              permissions.setPlayerRank(playerId, neutralRank,
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3446:                                          
                                              colony.getWorld());
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3447:                                          
                      }
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3448:                                          
              }



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff -- src/main/java/net/machiavelli/minecolonytax/data/WarData.java src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java src/main/java/net/machiavelli/minecolonytax/event/AbandonedColonyProtectionHandler.java src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java src/main/java/net/machiavelli/minecolonytax/event/ColonyPermissionMonitor.java src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 371ms:
diff --git a/src/main/java/net/machiavelli/minecolonytax/data/WarData.java b/src/main/java/net/machiavelli/minecolonytax/data/WarData.java
index dd7404d..a2687f3 100644
--- a/src/main/java/net/machiavelli/minecolonytax/data/WarData.java
+++ b/src/main/java/net/machiavelli/minecolonytax/data/WarData.java
@@ -41,6 +41,12 @@ public class WarData {
 
     public long countdownTaskId = -1;
     public long warChestDrainTaskId = -1;
+    /** Join-phase countdown-sound timer. Captured so it can be cancelled when the
+     *  join phase ends — otherwise the repeating task leaks forever (audit C4). */
+    public long joinCountdownTaskId = -1;
+    /** Main delayed JOINING->INWAR start timer. Captured so endWar() can cancel it and
+     *  so it can't resurrect a war that ended during the join phase (audit C4 follow-up). */
+    public long joinStartTaskId = -1;
     public ServerBossEvent bossEvent;
     public ServerBossEvent alliesBossEvent;
     private String penaltyReport = "";
diff --git a/src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java b/src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java
index f66c40f..145ac55 100644
--- a/src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java
@@ -65,6 +65,16 @@ public class TreasuryManager {
     }
 
     public static void shutdown() {
+        // Route the final save through the async executor too, so warchests.json is only
+        // ever written by the single async thread — never concurrently from the main
+        // thread (which could race an in-flight async write of the same .tmp). The
+        // ServerStopping hook calls AsyncSaveExecutor.shutdownAndFlush() afterwards,
+        // which barriers on the worker then flushes this pending write to disk.
+        save();
+    }
+
+    /** Public runtime save trigger — async + coalesced (audit H1/H2). */
+    public static void save() {
         saveData();
     }
 
@@ -532,6 +542,13 @@ public class TreasuryManager {
     }
 
     private static void saveData() {
+        // Snapshot on the calling (main) thread, write off-thread + coalesced so a
+        // deposit/withdraw/purchase storm no longer blocks ticks on disk I/O (audit H1).
+        final Map<Integer, Integer> snapshot = new java.util.HashMap<>(TREASURIES);
+        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("treasury", () -> writeData(snapshot));
+    }
+
+    private static void writeData(Map<Integer, Integer> data) {
         File file = new File(STORAGE_FILE);
         file.getParentFile().mkdirs();
 
@@ -542,7 +559,7 @@ public class TreasuryManager {
         File tmpFile = new File(file.getParentFile(), file.getName() + ".tmp");
         try {
             try (FileWriter writer = new FileWriter(tmpFile)) {
-                GSON.toJson(TREASURIES, writer);
+                GSON.toJson(data, writer);
             }
             Path tmpPath = tmpFile.toPath();
             Path finalPath = file.toPath();
diff --git a/src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java b/src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java
index e0cd2b0..333189a 100644
--- a/src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java
@@ -430,23 +430,32 @@ public class WarExhaustionManager {
     }
 
     private static void saveData() {
-        try {
-            Path path = Paths.get(STORAGE_FILE);
-            Files.createDirectories(path.getParent());
-
-            ExhaustionSaveData data = new ExhaustionSaveData();
-            data.coloniesAtWar = new ConcurrentHashMap<>(COLONIES_AT_WAR);
-            data.recoveryStatus = new ConcurrentHashMap<>(RECOVERY_STATUS);
-            data.warLosses = new ConcurrentHashMap<>(WAR_LOSSES);
-            data.reparations = new ConcurrentHashMap<>(REPARATIONS);
-            data.warImmunity = new ConcurrentHashMap<>(WAR_IMMUNITY);
-
-            try (Writer writer = new FileWriter(path.toFile())) {
-                GSON.toJson(data, writer);
-            }
-        } catch (Exception e) {
-            LOGGER.error("Failed to save war exhaustion data", e);
+        // Snapshot on the calling (main) thread, write off-thread + coalesced so the
+        // many war-state transitions no longer each block a tick on disk I/O (audit H3).
+        final ExhaustionSaveData data = new ExhaustionSaveData();
+        data.coloniesAtWar = new java.util.HashMap<>(COLONIES_AT_WAR);
+        data.recoveryStatus = new java.util.HashMap<>(RECOVERY_STATUS);
+        // warLosses values are mutable Lists — deep-copy so the async writer never
+        // serializes a list while the main thread mutates it.
+        Map<Integer, List<Long>> lossesCopy = new java.util.HashMap<>();
+        for (Map.Entry<Integer, List<Long>> e : WAR_LOSSES.entrySet()) {
+            lossesCopy.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
         }
+        data.warLosses = lossesCopy;
+        data.reparations = new java.util.HashMap<>(REPARATIONS);
+        data.warImmunity = new java.util.HashMap<>(WAR_IMMUNITY);
+
+        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("war_exhaustion", () -> {
+            try {
+                Path path = Paths.get(STORAGE_FILE);
+                Files.createDirectories(path.getParent());
+                try (Writer writer = new FileWriter(path.toFile())) {
+                    GSON.toJson(data, writer);
+                }
+            } catch (Exception e) {
+                LOGGER.error("Failed to save war exhaustion data", e);
+            }
+        });
     }
 
     /**
diff --git a/src/main/java/net/machiavelli/minecolonytax/event/AbandonedColonyProtectionHandler.java b/src/main/java/net/machiavelli/minecolonytax/event/AbandonedColonyProtectionHandler.java
index 79e0f4b..3647209 100644
--- a/src/main/java/net/machiavelli/minecolonytax/event/AbandonedColonyProtectionHandler.java
+++ b/src/main/java/net/machiavelli/minecolonytax/event/AbandonedColonyProtectionHandler.java
@@ -74,6 +74,12 @@ public class AbandonedColonyProtectionHandler {
     }
     
     private static boolean isBlockInAbandonedColony(BlockPos pos, Level level, ServerPlayer player) {
+        // Fast path: if NO colony is abandoned, skip the per-block-event colony-by-position
+        // lookup entirely. This guard covers all three handlers (break/place/right-click)
+        // since each calls this first (audit H7).
+        if (!ColonyAbandonmentManager.hasAbandonedColonies()) {
+            return false;
+        }
         try {
             IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
             if (colony == null) {
diff --git a/src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java b/src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java
index e0c4ca5..dffe00b 100644
--- a/src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java
+++ b/src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java
@@ -101,8 +101,8 @@ public class BlockInteractionFilterHandler {
                 && !net.machiavelli.minecolonytax.occupation.OccupationManager
                         .getActiveOccupations().isEmpty()) return true;
         if (TaxConfig.isBesiegeSystemEnabled()
-                && !net.machiavelli.minecolonytax.besiege.BesiegeManager
-                        .getActiveRaids().isEmpty()) return true;
+                && net.machiavelli.minecolonytax.besiege.BesiegeManager
+                        .hasActiveRaids()) return true;
         if (TaxConfig.isBlockFilterRaidsEnabled()
                 && !RaidManager.getActiveRaids().isEmpty()) return true;
         if (TaxConfig.isBlockFilterWarsEnabled()
diff --git a/src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java b/src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java
index d38ba0e..fbc882e 100644
--- a/src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java
+++ b/src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java
@@ -59,7 +59,13 @@ public class ColonyEventListener {
         }
         tickCounter = 0;
 
-        boolean logUpgrades = TaxConfig.showColonyInitializationLogs();
+        // This building-delta scan exists ONLY to log new/upgraded buildings. When that
+        // logging is off (production default) the whole O(colonies x buildings) per-second
+        // scan is pure waste — skip it entirely (audit H11). This also keeps
+        // colonyBuildingLevels empty in production, so it can't leak there.
+        if (!TaxConfig.showColonyInitializationLogs()) {
+            return;
+        }
         List<IColony> colonies = IColonyManager.getInstance().getAllColonies();
 
         for (IColony colony : colonies) {
@@ -84,7 +90,7 @@ public class ColonyEventListener {
                     }
                 }
 
-                if (newOrUpgradedBuildingsCount > 0 && logUpgrades) {
+                if (newOrUpgradedBuildingsCount > 0) {
                     LOGGER.info("Colony '{}': Detected {} new/upgraded buildings (Guards: {})",
                                colony.getName(), newOrUpgradedBuildingsCount, guardTowerCount);
                 }
diff --git a/src/main/java/net/machiavelli/minecolonytax/event/ColonyPermissionMonitor.java b/src/main/java/net/machiavelli/minecolonytax/event/ColonyPermissionMonitor.java
index bb5ca69..10f2f9d 100644
--- a/src/main/java/net/machiavelli/minecolonytax/event/ColonyPermissionMonitor.java
+++ b/src/main/java/net/machiavelli/minecolonytax/event/ColonyPermissionMonitor.java
@@ -31,6 +31,14 @@ public class ColonyPermissionMonitor {
             return;
         }
 
+        // Gated by the master abandonment switch (4.x world-brick fix): this monitor exists
+        // solely to auto-reactivate abandoned colonies (a permission-mutating action). When the
+        // abandonment system is off it must not run, and skipping it also avoids a periodic
+        // all-colony scan.
+        if (!net.machiavelli.minecolonytax.TaxConfig.isColonyAbandonmentSystemEnabled()) {
+            return;
+        }
+
         tickCounter++;
         if (tickCounter < CHECK_INTERVAL) {
             return;
diff --git a/src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java b/src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java
index 6990321..79f14b7 100644
--- a/src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java
+++ b/src/main/java/net/machiavelli/minecolonytax/event/RaidKillTracker.java
@@ -55,6 +55,16 @@ public class RaidKillTracker {
     }
 
     private static void handleEntityDeath(AbstractEntityCitizen citizen, DamageSource damageSource) {
+        // Fast path: citizen deaths are extremely frequent across hundreds of colonies.
+        // This handler only acts on regular raids, claiming raids, or wars — if none are
+        // active anywhere, bail before the colony lookup (audit H5). Behavior-preserving:
+        // with no conflict, the flags below stay false and the method returns anyway.
+        if (RaidManager.getActiveRaids().isEmpty()
+                && net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.isEmpty()
+                && !ColonyClaimingRaidManager.hasActiveClaimingRaids()) {
+            return;
+        }
+
         ServerPlayer killer = damageSource.getEntity() instanceof ServerPlayer player ? player : null;
         IColony colony = IColonyManager.getInstance().getColonyByWorld(citizen.getCitizenColonyHandler().getColonyId(),
                 citizen.level());
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java', LF will be replaced by CRLF the next time Git touches it

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/event/AbandonedColonyProtectionHandler.java -TotalCount 180' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 343ms:
package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber
public class AbandonedColonyProtectionHandler {
    
    private static final Logger LOGGER = LogManager.getLogger(AbandonedColonyProtectionHandler.class);
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        
        Player player = event.getPlayer();
        if (player == null || !(player instanceof ServerPlayer)) return;
        ServerPlayer serverPlayer = (ServerPlayer) player;
        if (isBlockInAbandonedColony(event.getPos(), (Level) event.getLevel(), serverPlayer)) {
            event.setCanceled(true);
            serverPlayer.sendSystemMessage(Component.literal("You cannot break blocks in abandoned colonies!")
                    .withStyle(ChatFormatting.RED));
            LOGGER.debug("Blocked block breaking by {} in abandoned colony at {}", 
                player.getName().getString(), event.getPos());
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) event.getEntity();
            if (isBlockInAbandonedColony(event.getPos(), (Level) event.getLevel(), serverPlayer)) {
                event.setCanceled(true);
                serverPlayer.sendSystemMessage(Component.literal("You cannot place blocks in abandoned colonies!")
                        .withStyle(ChatFormatting.RED));
                LOGGER.debug("Blocked block placing by {} in abandoned colony at {}", 
                    serverPlayer.getName().getString(), event.getPos());
            }
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            if (isBlockInAbandonedColony(event.getPos(), (Level) event.getLevel(), serverPlayer)) {
                if (isPlayerInActiveClaimingRaid(serverPlayer,
                    IColonyManager.getInstance().getColonyByPosFromWorld((Level) event.getLevel(), event.getPos()))) {
                    return;
                }
                event.setCanceled(true);
                serverPlayer.sendSystemMessage(Component.literal("You cannot interact with blocks in abandoned colonies!")
                        .withStyle(ChatFormatting.RED));
                LOGGER.debug("Blocked block interaction by {} in abandoned colony at {}", 
                    serverPlayer.getName().getString(), event.getPos());
            }
        }
    }
    
    private static boolean isBlockInAbandonedColony(BlockPos pos, Level level, ServerPlayer player) {
        // Fast path: if NO colony is abandoned, skip the per-block-event colony-by-position
        // lookup entirely. This guard covers all three handlers (break/place/right-click)
        // since each calls this first (audit H7).
        if (!ColonyAbandonmentManager.hasAbandonedColonies()) {
            return false;
        }
        try {
            IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
            if (colony == null) {
                return false;
            }
            if (!ColonyAbandonmentManager.isColonyAbandoned(colony)) {
                return false;
            }
            // Admins bypass
            if (player.hasPermissions(2)) {
                return false;
            }
            // The claiming player must be able to interact during their raid
            if (isPlayerInActiveClaimingRaid(player, colony)) {
                return false;
            }
            LOGGER.debug("Blocking {} from modifying blocks in abandoned colony {} at {}",
                player.getName().getString(), colony.getName(), pos);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Error checking abandoned colony protection for position {}", pos, e);
            return false; // Don't block on error
        }
    }
    
    private static boolean isPlayerInActiveClaimingRaid(ServerPlayer player, IColony colony) {
        try {
            return net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
                    .isPlayerInClaimingRaid(player.getUUID(), colony.getID());
        } catch (Exception e) {
            LOGGER.debug("Error checking claiming raid status", e);
            return false;
        }
    }
}

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff -- src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java src/main/java/net/machiavelli/minecolonytax/mixin/WorldTickHandlerMixin.java src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 326ms:
diff --git a/src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java b/src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java
index 10b5b0d..a16c6ac 100644
--- a/src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/events/random/RandomEventManager.java
@@ -122,7 +122,17 @@ public class RandomEventManager {
         // 2. Try to trigger new events (probability-based)
         checkForNewEvents(colony);
 
-        // 3. Save data after changes
+        // NOTE: do NOT saveData() here. This runs once per colony per tax cycle, so a
+        // per-colony save serialized the full mod-wide state N times each cycle = O(N^2)
+        // synchronous disk writes at scale (optimization audit C1). TaxManager now calls
+        // persist() ONCE after the whole tax loop completes.
+    }
+
+    /**
+     * Persist random-event state. Call this ONCE after the tax cycle finishes
+     * (TaxManager.generateTaxesForAllColonies) rather than per colony.
+     */
+    public static void persist() {
         saveData();
     }
 
diff --git a/src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java b/src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java
index f862c76..39f1534 100644
--- a/src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java
+++ b/src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java
@@ -4,6 +4,8 @@ import com.minecolonies.api.colony.IColony;
 import net.machiavelli.minecolonytax.TaxConfig;
 import net.machiavelli.minecolonytax.TaxManager;
 import net.machiavelli.minecolonytax.util.ItemUtils;
+import net.minecraft.ChatFormatting;
+import net.minecraft.network.chat.Component;
 import net.minecraft.server.level.ServerPlayer;
 import org.apache.logging.log4j.LogManager;
 import org.apache.logging.log4j.Logger;
@@ -85,6 +87,53 @@ public class CurrencyService {
         }
     }
 
+    /**
+     * Deliver tax that was ALREADY claimed (deducted from the colony ledger by
+     * {@link TaxManager#claimTax}) to the player, honoring the SDMShop-conversion config, and
+     * REFUND the colony tax ledger if delivery fails so taxes can never be silently lost.
+     *
+     * <p>This fixes the 4.x "claimed coins never appear" bug: the claim paths used to deduct
+     * the tax first and then attempt SDM delivery with no refund, so any unavailable-economy
+     * or failed-deposit left the player with nothing and the tax gone.</p>
+     *
+     * <p>Behavior when {@code EnableSDMShopConversion=true} but the shop economy is unavailable:
+     * the tax is refunded (kept claimable) and the player is told to check {@code /wnt sdm status},
+     * rather than silently dropping surprise currency items.</p>
+     *
+     * @param claimedAmount amount already removed from the colony ledger; must be &gt; 0
+     * @return true if the player actually received the funds; false if delivery failed (and the
+     *         ledger was refunded)
+     */
+    public static boolean deliverClaimedTaxOrRefund(ServerPlayer player, IColony colony, int claimedAmount) {
+        if (claimedAmount <= 0) {
+            return false;
+        }
+
+        boolean useWallet = TaxConfig.isSDMShopConversionEnabled();
+
+        if (useWallet && !SDMShopIntegration.isAvailable()) {
+            SDMShopIntegration.warnUnavailableOnce();
+            TaxManager.adjustTax(colony, claimedAmount); // refund — never lose taxes
+            player.sendSystemMessage(Component.literal(
+                    "§c✗ Shop economy (SDMShop/SDM-Economy) is not available right now — your "
+                  + claimedAmount + " in taxes was NOT lost and is still claimable. Run /wnt sdm status for details.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+
+        Source destination = useWallet ? Source.WALLET : Source.INVENTORY;
+        int given = giveToPlayer(player, colony, claimedAmount, destination);
+        if (given <= 0) {
+            TaxManager.adjustTax(colony, claimedAmount); // refund — never lose taxes
+            player.sendSystemMessage(Component.literal(
+                    "§c✗ Failed to deliver " + claimedAmount + " (" + label(destination)
+                  + ") — your taxes were refunded and remain claimable.")
+                    .withStyle(ChatFormatting.RED));
+            return false;
+        }
+        return true;
+    }
+
     /**
      * Returns how much currency the player has available from the given source.
      * Used for balance checks and status display.
diff --git a/src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java b/src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java
index efa5a11..34231e4 100644
--- a/src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java
+++ b/src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java
@@ -1,5 +1,6 @@
 package net.machiavelli.minecolonytax.integration;
 
+import net.machiavelli.minecolonytax.TaxConfig;
 import net.minecraft.server.level.ServerPlayer;
 import net.minecraft.world.entity.player.Player;
 import net.minecraftforge.fml.ModList;
@@ -8,6 +9,7 @@ import org.apache.logging.log4j.Logger;
 
 import java.lang.reflect.Field;
 import java.lang.reflect.Method;
+import java.util.concurrent.atomic.AtomicBoolean;
 
 /**
  * Integration wrapper for SDMShop/SDM-Economy to avoid compilation errors when
@@ -24,9 +26,27 @@ import java.lang.reflect.Method;
 public class SDMShopIntegration {
     private static final Logger LOGGER = LogManager.getLogger(SDMShopIntegration.class);
 
-    // Default currency name for SDM-Economy multi-currency system
+    // Fallback currency id used only if the configured value is blank. The authoritative value
+    // is TaxConfig.getSDMCurrencyName() (config key 'SDMCurrencyName') so servers whose
+    // SDM-Economy currency id is not literally "sdm_coin" can point taxes at the right currency.
     private static final String DEFAULT_CURRENCY_NAME = "sdm_coin";
 
+    /** One-time WARN latch so we don't spam logs when conversion is enabled but unavailable. */
+    private static final AtomicBoolean WARNED_UNAVAILABLE = new AtomicBoolean(false);
+
+    /** The SDM-Economy currency id taxes are deposited into (config 'SDMCurrencyName'). */
+    private static String currencyName() {
+        try {
+            String configured = TaxConfig.getSDMCurrencyName();
+            if (configured != null && !configured.trim().isEmpty()) {
+                return configured.trim();
+            }
+        } catch (Throwable ignored) {
+            // config not ready (very early classload) — fall back to default
+        }
+        return DEFAULT_CURRENCY_NAME;
+    }
+
     // Integration mode enum
     private enum IntegrationMode {
         NONE, // Not available
@@ -340,7 +360,7 @@ public class SDMShopIntegration {
         }
 
         // Returns ErrorCodeStruct<Double>
-        Object result = currencyDataGetBalance.invoke(currencyPlayerDataServer, player, DEFAULT_CURRENCY_NAME);
+        Object result = currencyDataGetBalance.invoke(currencyPlayerDataServer, player, currencyName());
 
         // Get the 'value' field from ErrorCodeStruct
         Field valueField = result.getClass().getField("value");
@@ -427,7 +447,7 @@ public class SDMShopIntegration {
         }
 
         // Returns ErrorCodes enum
-        Object result = currencyDataSetValue.invoke(currencyPlayerDataServer, player, DEFAULT_CURRENCY_NAME,
+        Object result = currencyDataSetValue.invoke(currencyPlayerDataServer, player, currencyName(),
                 (double) amount);
 
         // Check if result is SUCCESS
@@ -494,7 +514,7 @@ public class SDMShopIntegration {
 
             if (mode == IntegrationMode.CURRENCY_DATA && currencyPlayerDataServer != null
                     && currencyDataAddValue != null) {
-                Object result = currencyDataAddValue.invoke(currencyPlayerDataServer, player, DEFAULT_CURRENCY_NAME,
+                Object result = currencyDataAddValue.invoke(currencyPlayerDataServer, player, currencyName(),
                         (double) amount);
                 boolean success = result.toString().equals("SUCCESS");
 
@@ -566,4 +586,38 @@ public class SDMShopIntegration {
     public static String getIntegrationMode() {
         return mode.toString();
     }
+
+    /** True if the SDMShop/SDM-Economy mod was detected as loaded at init. */
+    public static boolean isModPresent() {
+        return modPresent;
+    }
+
+    /** The currency id taxes are deposited into (config 'SDMCurrencyName'). */
+    public static String getCurrencyName() {
+        return currencyName();
+    }
+
+    /**
+     * True when, for the active integration mode, the backing economy server instance is ready.
+     * For CURRENCY_DATA mode this is what's commonly null in single-player until SDM-Economy
+     * finishes its own SERVER_STARTED init — the usual cause of "coins never appeared".
+     */
+    public static boolean isServerInstanceReady() {
+        if (!modPresent) return false;
+        refreshServerInstance();
+        return mode != IntegrationMode.NONE
+                && (mode != IntegrationMode.CURRENCY_DATA || currencyPlayerDataServer != null);
+    }
+
+    /**
+     * Logs a single WARN the first time tax conversion is requested while the integration is
+     * unavailable, so server admins can see the cause without enabling debug logging.
+     */
+    public static void warnUnavailableOnce() {
+        if (WARNED_UNAVAILABLE.compareAndSet(false, true)) {
+            LOGGER.warn("EnableSDMShopConversion=true but the SDMShop/SDM-Economy integration is not available "
+                    + "(modPresent={}, mode={}, serverReady={}). Claimed taxes are being refunded instead of "
+                    + "converted. Use /wnt sdm status to diagnose.", modPresent, mode, isServerInstanceReady());
+        }
+    }
 }
diff --git a/src/main/java/net/machiavelli/minecolonytax/mixin/WorldTickHandlerMixin.java b/src/main/java/net/machiavelli/minecolonytax/mixin/WorldTickHandlerMixin.java
index fdb0fdc..3aae662 100644
--- a/src/main/java/net/machiavelli/minecolonytax/mixin/WorldTickHandlerMixin.java
+++ b/src/main/java/net/machiavelli/minecolonytax/mixin/WorldTickHandlerMixin.java
@@ -84,7 +84,14 @@ public abstract class WorldTickHandlerMixin {
         }
 
         if (anyBesiege) {
+            net.minecraft.resources.ResourceKey<Level> dimKey = eventLevel.dimension();
             for (BesiegeManager.BesiegeRaidData raid : BesiegeManager.getAllActiveRaidsByBesieger().values()) {
+                // Fast path: compare cached dimension in O(1) (audit H12).
+                if (raid.dimension != null) {
+                    if (raid.dimension.equals(dimKey)) { ci.cancel(); return; }
+                    continue;
+                }
+                // Fallback for a raid with no cached dimension (e.g. restored): resolve by id.
                 IColony c = com.minecolonies.api.IMinecoloniesAPI.getInstance().getColonyManager()
                         .getAllColonies().stream()
                         .filter(col -> col.getID() == raid.colonyId)
diff --git a/src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java b/src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java
index 324e512..057600c 100644
--- a/src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java
+++ b/src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java
@@ -128,50 +128,19 @@ public class ClaimTaxPacket {
             }
             
             if (claimedAmount > 0) {
-                // Give the player the claimed tax
-                boolean paymentSuccessful = false;
-                
-                if (TaxConfig.isSDMShopConversionEnabled()) {
-                    // Use SDMShop integration
-                    if (SDMShopIntegration.isAvailable()) {
-                        long currentBalance = SDMShopIntegration.getMoney(player);
-                        
-                        paymentSuccessful = SDMShopIntegration.addMoney(player, claimedAmount);
-                        
-                        if (paymentSuccessful) {
-                            long newBalance = SDMShopIntegration.getMoney(player);
-                            player.sendSystemMessage(Component.literal("§a✓ Successfully added " + claimedAmount + " to your balance!"));
-                            player.sendSystemMessage(Component.literal("§a  Balance: " + currentBalance + " → " + newBalance));
-                        } else {
-                            player.sendSystemMessage(Component.literal("§c✗ Failed to add money to SDMShop balance!"));
-                        }
-                    } else {
-                        player.sendSystemMessage(Component.literal("§c✗ SDMShop integration is not available!"));
-                        player.sendSystemMessage(Component.literal("§eCheck that SDMShop mod is installed and working"));
-                    }
-                } else {
-                    // Use item-based currency
-                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(TaxConfig.getCurrencyItemName()));
-                    if (item != null) {
-                        ItemStack itemStack = new ItemStack(item, claimedAmount);
-                        boolean added = player.getInventory().add(itemStack);
-                        if (!added) {
-                            // If inventory is full, drop items near player
-                            player.drop(itemStack, false);
-                            player.sendSystemMessage(Component.translatable("taxmanager.inventory_full", claimedAmount, TaxConfig.getCurrencyItemName()));
-                        } else {
-                            player.sendSystemMessage(Component.translatable("taxmanager.currency_received", claimedAmount, TaxConfig.getCurrencyItemName()));
-                        }
-                        paymentSuccessful = true;
-                    } else {
-                        player.sendSystemMessage(Component.literal("§c✗ Currency item not found: " + TaxConfig.getCurrencyItemName()));
+                // Deliver the claimed tax, refunding the colony ledger if delivery fails so
+                // taxes are never silently lost (4.x "coins never appear" fix). The helper
+                // emits its own failure/refund message; we only confirm on success.
+                long before = TaxConfig.isSDMShopConversionEnabled() && SDMShopIntegration.isAvailable()
+                        ? SDMShopIntegration.getMoney(player) : -1L;
+                if (net.machiavelli.minecolonytax.integration.CurrencyService
+                        .deliverClaimedTaxOrRefund(player, colony, claimedAmount)) {
+                    if (before >= 0L) {
+                        long after = SDMShopIntegration.getMoney(player);
+                        player.sendSystemMessage(Component.literal("§a✓ Added " + claimedAmount
+                                + " to your balance (" + before + " → " + after + ")"));
                     }
-                }
-                
-                if (paymentSuccessful) {
                     player.sendSystemMessage(Component.translatable("command.claimtax.success", claimedAmount, colony.getName()));
-                } else {
-                    player.sendSystemMessage(Component.literal("§c✗ Failed to claim tax - payment system error!"));
                 }
             } else {
                 player.sendSystemMessage(Component.translatable("command.claimtax.no_tax", colony.getName()));
diff --git a/src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java b/src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java
index 03da58b..5703115 100644
--- a/src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java
+++ b/src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java
@@ -88,6 +88,29 @@ public final class AsyncSaveExecutor {
     public static void shutdownAndFlush() {
         running = false;
 
+        // Barrier FIRST: stop accepting new scheduled tasks and WAIT for the worker to
+        // finish any in-flight write before this thread touches any file. Otherwise the
+        // inline flush below could write the same file the worker is still writing,
+        // racing/corrupting it (codex review). Already-queued drains run during the
+        // orderly shutdown and drain PENDING as they go.
+        EXEC.shutdown();
+        boolean terminated = false;
+        try {
+            terminated = EXEC.awaitTermination(10, TimeUnit.SECONDS);
+        } catch (InterruptedException ie) {
+            Thread.currentThread().interrupt();
+        }
+
+        if (!terminated) {
+            // Worker is still alive after the timeout — do NOT inline-flush, or this thread
+            // could write the same file the worker is still writing (codex review). Better to
+            // leave a pending write unflushed than to corrupt a file; a stuck worker is itself
+            // an error worth surfacing.
+            LOGGER.warn("AsyncSaveExecutor did not terminate within 10s; skipping inline flush to avoid racing the worker");
+            return;
+        }
+
+        // Worker is confirmed stopped; drain anything still pending inline (single-threaded).
         Map<String, Runnable> remaining;
         synchronized (LOCK) {
             remaining = new HashMap<>(PENDING);
@@ -100,14 +123,5 @@ public final class AsyncSaveExecutor {
                 LOGGER.error("Flush save failed for {}: {}", e.getKey(), t.toString());
             }
         }
-
-        EXEC.shutdown();
-        try {
-            if (!EXEC.awaitTermination(10, TimeUnit.SECONDS)) {
-                LOGGER.warn("AsyncSaveExecutor did not terminate within 10s");
-            }
-        } catch (InterruptedException ie) {
-            Thread.currentThread().interrupt();
-        }
     }
 }
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/mixin/WorldTickHandlerMixin.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java', LF will be replaced by CRLF the next time Git touches it

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 317ms:
package net.machiavelli.minecolonytax.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Off-thread file writer with key-based coalescing.
 *
 * <p>Managers that previously serialised + wrote JSON on the server thread
 * should now snapshot their state on the calling thread (cheap, safe) and
 * hand the resulting write Runnable here. The write itself runs on a single
 * background daemon thread, so {@link java.io.FileWriter} blocking and OS
 * fsync no longer stall server ticks.
 *
 * <p>If two writes for the same key are queued before either has started,
 * only the most recent wins. This prevents pile-up during rapid state
 * changes (e.g. spy mission churn during raids).
 *
 * <p>{@link #shutdownAndFlush()} MUST be called on ServerStoppingEvent so
 * any pending write reaches disk before the JVM exits. After shutdown, any
 * further submit() runs inline so no data is silently dropped.
 */
public final class AsyncSaveExecutor {

    private static final Logger LOGGER = LogManager.getLogger(AsyncSaveExecutor.class);

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WNT-AsyncSave");
        t.setDaemon(true);
        return t;
    });

    /** key -> latest pending write. Worker drains by removing under LOCK. */
    private static final Map<String, Runnable> PENDING = new HashMap<>();
    private static final Object LOCK = new Object();
    private static volatile boolean running = true;

    private AsyncSaveExecutor() {}

    /**
     * Queue a disk write. Only the latest write per {@code key} is kept;
     * earlier pending writes for the same key are discarded.
     */
    public static void submit(String key, Runnable writeJob) {
        if (!running) {
            // After shutdown, run inline so the caller's data isn't lost.
            try {
                writeJob.run();
            } catch (Throwable t) {
                LOGGER.error("Inline save failed for {}: {}", key, t.toString());
            }
            return;
        }

        boolean needSchedule;
        synchronized (LOCK) {
            needSchedule = !PENDING.containsKey(key);
            PENDING.put(key, writeJob);
        }
        if (needSchedule) {
            EXEC.execute(() -> drain(key));
        }
    }

    private static void drain(String key) {
        Runnable job;
        synchronized (LOCK) {
            job = PENDING.remove(key);
        }
        if (job == null) return;
        try {
            job.run();
        } catch (Throwable t) {
            LOGGER.error("Async save failed for {}: {}", key, t.toString());
        }
    }

    /**
     * Drain all pending writes synchronously, then shut the executor down.
     * Subsequent submit() calls run inline.
     */
    public static void shutdownAndFlush() {
        running = false;

        // Barrier FIRST: stop accepting new scheduled tasks and WAIT for the worker to
        // finish any in-flight write before this thread touches any file. Otherwise the
        // inline flush below could write the same file the worker is still writing,
        // racing/corrupting it (codex review). Already-queued drains run during the
        // orderly shutdown and drain PENDING as they go.
        EXEC.shutdown();
        boolean terminated = false;
        try {
            terminated = EXEC.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        if (!terminated) {
            // Worker is still alive after the timeout â€” do NOT inline-flush, or this thread
            // could write the same file the worker is still writing (codex review). Better to
            // leave a pending write unflushed than to corrupt a file; a stuck worker is itself
            // an error worth surfacing.
            LOGGER.warn("AsyncSaveExecutor did not terminate within 10s; skipping inline flush to avoid racing the worker");
            return;
        }

        // Worker is confirmed stopped; drain anything still pending inline (single-threaded).
        Map<String, Runnable> remaining;
        synchronized (LOCK) {
            remaining = new HashMap<>(PENDING);
            PENDING.clear();
        }
        for (Map.Entry<String, Runnable> e : remaining.entrySet()) {
            try {
                e.getValue().run();
            } catch (Throwable t) {
                LOGGER.error("Flush save failed for {}: {}", e.getKey(), t.toString());
            }
        }
    }
}

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 304ms:
package net.machiavelli.minecolonytax.integration;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.util.ItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Central dispatch for all player-facing currency operations (take / give).
 *
 * Supported sources and destinations:
 *   TAX_BALANCE  â€” colony's accumulated tax balance (TaxManager server ledger)
 *   WALLET       â€” player's economy wallet (SDMShop / SDMEconomy)
 *   INVENTORY    â€” physical currency items in the player's Minecraft inventory
 *
 * To add support for a future economy mod, add a new Source constant and
 * handle it in takeFromPlayer() and giveToPlayer(). No other files need
 * to change for new currency types.
 */
public class CurrencyService {

    private static final Logger LOGGER = LogManager.getLogger(CurrencyService.class);

    public enum Source {
        /** Colony's accumulated tax ledger balance. Default for treasury operations. */
        TAX_BALANCE,
        /** Player's SDMShop / SDMEconomy wallet balance. */
        WALLET,
        /** Physical currency items in the player's inventory slots. */
        INVENTORY
    }

    // â”€â”€ Public API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Remove {@code amount} of currency from {@code source} on behalf of the player.
     *
     * @return the amount actually removed, or 0 if funds are insufficient or
     *         the source is not available.
     */
    public static int takeFromPlayer(ServerPlayer player, IColony colony, int amount, Source source) {
        switch (source) {
            case TAX_BALANCE: {
                int stored = colony != null ? TaxManager.getStoredTaxForColony(colony) : 0;
                if (stored < amount) return 0;
                TaxManager.adjustTax(colony, -amount);
                return amount;
            }
            case WALLET: {
                if (!SDMShopIntegration.isAvailable()) return 0;
                return SDMShopIntegration.deductPlayerBalance(player, amount);
            }
            case INVENTORY: {
                return takeCurrencyFromInventory(player, amount);
            }
            default:
                return 0;
        }
    }

    /**
     * Give {@code amount} of currency to the player via the specified destination.
     *
     * @return the amount actually given, or 0 on failure.
     */
    public static int giveToPlayer(ServerPlayer player, IColony colony, int amount, Source destination) {
        switch (destination) {
            case TAX_BALANCE: {
                if (colony == null) return 0;
                TaxManager.adjustTax(colony, amount);
                return amount;
            }
            case WALLET: {
                if (!SDMShopIntegration.isAvailable()) return 0;
                return SDMShopIntegration.addMoney(player, amount) ? amount : 0;
            }
            case INVENTORY: {
                return ItemUtils.giveCurrencyToPlayer(player, amount) ? amount : 0;
            }
            default:
                return 0;
        }
    }

    /**
     * Deliver tax that was ALREADY claimed (deducted from the colony ledger by
     * {@link TaxManager#claimTax}) to the player, honoring the SDMShop-conversion config, and
     * REFUND the colony tax ledger if delivery fails so taxes can never be silently lost.
     *
     * <p>This fixes the 4.x "claimed coins never appear" bug: the claim paths used to deduct
     * the tax first and then attempt SDM delivery with no refund, so any unavailable-economy
     * or failed-deposit left the player with nothing and the tax gone.</p>
     *
     * <p>Behavior when {@code EnableSDMShopConversion=true} but the shop economy is unavailable:
     * the tax is refunded (kept claimable) and the player is told to check {@code /wnt sdm status},
     * rather than silently dropping surprise currency items.</p>
     *
     * @param claimedAmount amount already removed from the colony ledger; must be &gt; 0
     * @return true if the player actually received the funds; false if delivery failed (and the
     *         ledger was refunded)
     */
    public static boolean deliverClaimedTaxOrRefund(ServerPlayer player, IColony colony, int claimedAmount) {
        if (claimedAmount <= 0) {
            return false;
        }

        boolean useWallet = TaxConfig.isSDMShopConversionEnabled();

        if (useWallet && !SDMShopIntegration.isAvailable()) {
            SDMShopIntegration.warnUnavailableOnce();
            TaxManager.adjustTax(colony, claimedAmount); // refund â€” never lose taxes
            player.sendSystemMessage(Component.literal(
                    "Â§câœ— Shop economy (SDMShop/SDM-Economy) is not available right now â€” your "
                  + claimedAmount + " in taxes was NOT lost and is still claimable. Run /wnt sdm status for details.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        Source destination = useWallet ? Source.WALLET : Source.INVENTORY;
        int given = giveToPlayer(player, colony, claimedAmount, destination);
        if (given <= 0) {
            TaxManager.adjustTax(colony, claimedAmount); // refund â€” never lose taxes
            player.sendSystemMessage(Component.literal(
                    "Â§câœ— Failed to deliver " + claimedAmount + " (" + label(destination)
                  + ") â€” your taxes were refunded and remain claimable.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    /**
     * Returns how much currency the player has available from the given source.
     * Used for balance checks and status display.
     */
    public static long getAvailableBalance(ServerPlayer player, IColony colony, Source source) {
        switch (source) {
            case TAX_BALANCE:
                return colony != null ? TaxManager.getStoredTaxForColony(colony) : 0;
            case WALLET:
                return SDMShopIntegration.isAvailable() ? SDMShopIntegration.getMoney(player) : -1;
            case INVENTORY:
                return countCurrencyInInventory(player);
            default:
                return 0;
        }
    }

    /**
     * Returns true if this source/destination is usable in the current environment.
     * WALLET requires SDMShop / SDMEconomy to be installed and initialised.
     */
    public static boolean isAvailable(Source source) {
        switch (source) {
            case TAX_BALANCE: return true;
            case WALLET:      return SDMShopIntegration.isAvailable();
            case INVENTORY:   return true;
            default:          return false;
        }
    }

    /**
     * Human-readable label for a source, used in player-facing messages.
     */
    public static String label(Source source) {
        switch (source) {
            case TAX_BALANCE: return "tax balance";
            case WALLET:      return "wallet";
            case INVENTORY:   return "inventory";
            default:          return source.name().toLowerCase();
        }
    }

    // â”€â”€ Inventory helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Count the total monetary value of currency items in the player's inventory.
     * In multi-denomination mode each item type contributes its configured value.
     */
    public static int countCurrencyInInventory(ServerPlayer player) {
        return ItemUtils.countInventoryValue(player.getInventory());
    }

    /**
     * Remove exactly {@code amount} of currency value from the player's inventory.
     * Uses largest denominations first; requires exact change to be possible.
     *
     * @return amount removed, or 0 if insufficient funds or exact change not possible.
     */
    private static int takeCurrencyFromInventory(ServerPlayer player, int amount) {
        return ItemUtils.takeCurrencyFromInventory(player, amount);
    }
}

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/TaxManager.java -Pattern 'static .*adjustTax|claimTax' -Context 3,12" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 344ms:

  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:252:        }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:253:    }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:254:
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:255:    public static int claimTax(IColony colony, int 
amount) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:256:
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:257:        int colonyId = colony.getID();
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:258:        int storedTax = 
colonyTaxMap.getOrDefault(colonyId, 0);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:259:
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:260:        if (TaxConfig.isDebugLogging()) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:261:            LOGGER.info("[TAX DEBUG] Colony {}: 
Stored tax = {}, Requested amount = {}", colony.getName(), storedTax,
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:262:                    amount);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:263:        }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:264:
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:265:        if (storedTax <= 0) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:266:            if (TaxConfig.isDebugLogging()) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:267:                LOGGER.info("[TAX DEBUG] No tax 
available to claim for colony {} (stored: {})", colony.getName(),
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:327:        return claimedAmount;
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:328:    }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:329:
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:330:    public static int claimTax(IColony colony) {
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:331:        return claimTax(colony, -1); // Claim all 
tax by default
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:332:    }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:333:
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:334:    public static int getStoredTaxForColony(IColony 
colony) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:335:        return 
colonyTaxMap.getOrDefault(colony.getID(), 0);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:336:    }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:337:
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:338:    public static int getStoredTaxForColonyId(int 
colonyId) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:339:        return colonyTaxMap.getOrDefault(colonyId, 
0);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:340:    }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:341:
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:342:    public static int getConsecutiveDebtCycles(int 
colonyId) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:343:        return 
CONSECUTIVE_DEBT_CYCLES.getOrDefault(colonyId, 0);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:368:        saveTaxData();
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:369:    }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:370:
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:371:    public static void adjustTax(IColony colony, int 
delta) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:372:        int id = colony.getID();
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:373:        int current = colonyTaxMap.getOrDefault(id, 
0);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:374:        // SECURITY (audit HIGH): defend against 
signed-int overflow when the colony's tax ledger is repeatedly
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:375:        // adjusted by large positive or negative 
deltas (e.g. via repeated vassal tribute or war reparations).
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:376:        // Use long-widened arithmetic then clamp to 
int range before storing.
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:377:        long widened = (long) current + (long) delta;
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:378:        int clamped;
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:379:        if (widened > Integer.MAX_VALUE) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:380:            clamped = Integer.MAX_VALUE;
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:381:        } else if (widened < Integer.MIN_VALUE) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:382:            clamped = Integer.MIN_VALUE;
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:383:        } else {



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/TaxManager.java | Select-Object -Index 255..390' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
2026-06-05T04:08:01.097584Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 0.4 seconds
Output:
Select-Object : Der Parameter "Index" kann nicht gebunden werden. Der Wert "255..390" kann nicht in den Typ 
"System.Int32" konvertiert werden. Fehler: "Die Eingabezeichenfolge hat das falsche Format."
In Zeile:2 Zeichen:102
+ ... iavelli/minecolonytax/TaxManager.java | Select-Object -Index 255..390
+                                                                  ~~~~~~~~
    + CategoryInfo          : InvalidArgument: (:) [Select-Object], ParameterBindingException
    + FullyQualifiedErrorId : CannotConvertArgumentNoMessage,Microsoft.PowerShell.Commands.SelectObjectCommand
 

 exited 1 in 431ms:
Select-Object : Der Parameter "Index" kann nicht gebunden werden. Der Wert "255..390" kann nicht in den Typ 
"System.Int32" konvertiert werden. Fehler: "Die Eingabezeichenfolge hat das falsche Format."
In Zeile:2 Zeichen:102
+ ... iavelli/minecolonytax/TaxManager.java | Select-Object -Index 255..390
+                                                                  ~~~~~~~~
    + CategoryInfo          : InvalidArgument: (:) [Select-Object], ParameterBindingException
    + FullyQualifiedErrorId : CannotConvertArgumentNoMessage,Microsoft.PowerShell.Commands.SelectObjectCommand
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/TaxManager.java | Select-Object -Index (254..390)' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 346ms:
    public static int claimTax(IColony colony, int amount) {

        int colonyId = colony.getID();
        int storedTax = colonyTaxMap.getOrDefault(colonyId, 0);

        if (TaxConfig.isDebugLogging()) {
            LOGGER.info("[TAX DEBUG] Colony {}: Stored tax = {}, Requested amount = {}", colony.getName(), storedTax,
                    amount);
        }

        if (storedTax <= 0) {
            if (TaxConfig.isDebugLogging()) {
                LOGGER.info("[TAX DEBUG] No tax available to claim for colony {} (stored: {})", colony.getName(),
                        storedTax);
            }
            return 0; // No tax to claim
        }

        // If the colony's tax is frozen, do not allow claiming.
        if (FROZEN_COLONIES.contains(colonyId)) {
            if (TaxConfig.isDebugLogging()) {
                LOGGER.info("Tax claims for colony {} are currently frozen.", colony.getName());
            }
            return 0;
        }

        // Check if the colony is currently being raided - if so, block tax claiming
        if (RaidManager.getActiveRaidForColony(colonyId) != null) {
            if (TaxConfig.isDebugLogging()) {
                LOGGER.info("Tax claims blocked for colony {} - colony is currently being raided.", colony.getName());
            }
            return 0;
        }

        // Check if the colony is currently in a war (either as defender or attacker) -
        // if so, block tax claiming
        WarData activeWar = WarSystem.ACTIVE_WARS.get(colonyId);
        if (activeWar == null) {
            for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
                if (wd.getAttackerColony() != null && wd.getAttackerColony().getID() == colonyId) {
                    activeWar = wd;
                    break;
                }
            }
        }
        if (activeWar != null) {
            if (TaxConfig.isDebugLogging()) {
                LOGGER.info("Tax claims blocked for colony {} - colony is currently at war.", colony.getName());
            }
            return 0;
        }

        int claimedAmount;
        if (amount == -1) {
            // Claim all tax
            claimedAmount = storedTax;
            colonyTaxMap.put(colonyId, 0); // Reset tax to zero
        } else {
            // SECURITY (audit Codex CRIT-1): the previous code did Math.min(amount, storedTax) which, for a
            // negative attacker-supplied amount, would return the negative amount and then compute
            // storedTax - (negative) = storedTax + |amount|, inflating the ledger. Defensively clamp to
            // [0, storedTax]. Sentinel values (-1, -2) are handled by the packet layer and the branch above.
            int clampedRequest = (amount > 0) ? amount : 0;
            claimedAmount = Math.max(0, Math.min(clampedRequest, storedTax));
            colonyTaxMap.put(colonyId, storedTax - claimedAmount); // Deduct the claimed amount
        }

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Claimed {} tax for colony {}", claimedAmount, colony.getName());
        }
        saveTaxData(true); // Log save for important operations like claiming tax

        return claimedAmount;
    }

    public static int claimTax(IColony colony) {
        return claimTax(colony, -1); // Claim all tax by default
    }

    public static int getStoredTaxForColony(IColony colony) {
        return colonyTaxMap.getOrDefault(colony.getID(), 0);
    }

    public static int getStoredTaxForColonyId(int colonyId) {
        return colonyTaxMap.getOrDefault(colonyId, 0);
    }

    public static int getConsecutiveDebtCycles(int colonyId) {
        return CONSECUTIVE_DEBT_CYCLES.getOrDefault(colonyId, 0);
    }

    public static void incrementTaxRevenue(IColony colony, int taxAmount) {
        int currentTax = colonyTaxMap.getOrDefault(colony.getID(), 0);
        int maxTax = TaxConfig.getMaxTaxRevenue();

        if (currentTax < maxTax) {
            int newTax = Math.min(currentTax + taxAmount, maxTax);
            colonyTaxMap.put(colony.getID(), newTax);
            // Removed per-building logging - will be aggregated in
            // generateTaxesForAllColonies
        } else {
            // Only log max limit reached once per colony per iteration
            // This will be handled in generateTaxesForAllColonies method
        }
    }

    public static void deductColonyTax(IColony colony, double percentage) {
        int currentTax = colonyTaxMap.getOrDefault(colony.getID(), 0);
        int deduction = (int) (currentTax * percentage);
        colonyTaxMap.put(colony.getID(), currentTax - deduction);
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Deducted {} tax as penalty from colony {}", deduction, colony.getName());
        }
        saveTaxData();
    }

    public static void adjustTax(IColony colony, int delta) {
        int id = colony.getID();
        int current = colonyTaxMap.getOrDefault(id, 0);
        // SECURITY (audit HIGH): defend against signed-int overflow when the colony's tax ledger is repeatedly
        // adjusted by large positive or negative deltas (e.g. via repeated vassal tribute or war reparations).
        // Use long-widened arithmetic then clamp to int range before storing.
        long widened = (long) current + (long) delta;
        int clamped;
        if (widened > Integer.MAX_VALUE) {
            clamped = Integer.MAX_VALUE;
        } else if (widened < Integer.MIN_VALUE) {
            clamped = Integer.MIN_VALUE;
        } else {
            clamped = (int) widened;
        }
        colonyTaxMap.put(id, clamped);
    }

    /**
     * Calculate the average happiness of adult citizens in a colony.
     * 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/**/*.java,src/main/java/net/machiavelli/minecolonytax/*.java -Pattern 'saveTaxData|adjustTax\\(' | Select-Object Path,LineNumber,Line | Format-List" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 496ms:


Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\commands\WntComman
             ds.java
LineNumber : 2621
Line       :                         TaxManager.adjustTax(targetColony, -takenFromColony);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\commands\WntComman
             ds.java
LineNumber : 2633
Line       :                                 TaxManager.adjustTax(attackerColony, takenFromColony);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\Curren
             cyService.java
LineNumber : 51
Line       :                 TaxManager.adjustTax(colony, -amount);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\Curren
             cyService.java
LineNumber : 75
Line       :                 TaxManager.adjustTax(colony, amount);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\Curren
             cyService.java
LineNumber : 116
Line       :             TaxManager.adjustTax(colony, claimedAmount); // refund — never lose taxes

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\Curren
             cyService.java
LineNumber : 127
Line       :             TaxManager.adjustTax(colony, claimedAmount); // refund — never lose taxes

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\occupation\Occupat
             ionManager.java
LineNumber : 345
Line       :         TaxManager.adjustTax(colony, -taxToCollect);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.j
             ava
LineNumber : 734
Line       :                 TaxManager.adjustTax(raiderColony, -raidPenalty);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.j
             ava
LineNumber : 809
Line       :                         TaxManager.adjustTax(raiderColony, -calculatedDefenseReward);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\raid\RaidManager.j
             ava
LineNumber : 823
Line       :                         TaxManager.adjustTax(raiderColony, -calculatedDefenseReward);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\vassalization\Vass
             alManager.java
LineNumber : 274
Line       :         TaxManager.adjustTax(colony, -tribute);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\vassalization\Vass
             alManager.java
LineNumber : 278
Line       :             TaxManager.adjustTax(overlordColony, tribute);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\vassalization\Vass
             alManager.java
LineNumber : 762
Line       :         TaxManager.adjustTax(vassalColony, -tributeOwed);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\vassalization\Vass
             alManager.java
LineNumber : 766
Line       :             TaxManager.adjustTax(overlordColony, tributeOwed);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 87
Line       :         saveTaxData(); // Save tax data when server stops

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 325
Line       :         saveTaxData(true); // Log save for important operations like claiming tax

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 368
Line       :         saveTaxData();

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 371
Line       :     public static void adjustTax(IColony colony, int delta) {

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 580
Line       :                                 adjustTax(colony, bonusAmount);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 594
Line       :                             adjustTax(colony, -pendingCost);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 610
Line       :                             adjustTax(colony, -reduction);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 621
Line       :                                 adjustTax(colony, bonusAmount);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 690
Line       :                             adjustTax(colony, -depositAmount);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 708
Line       :                             adjustTax(colony, -(int) divertedAmount);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 725
Line       :                             adjustTax(colony, -diverted);

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 1007
Line       :                     // saveTaxData() and RandomEventManager.persist() and lose the cycle's

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 1019
Line       :             saveTaxData();

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 1158
Line       :     private static void saveTaxData() {

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 1159
Line       :         saveTaxData(false); // Default to not log

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 1162
Line       :     private static void saveTaxData(boolean logSave) {

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\TaxManager.java
LineNumber : 1262
Line       :         saveTaxData(true);




exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/TaxManager.java | Select-Object -Index (60..100)' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 357ms:
    // Single instance of the tick event handler to prevent multiple registrations
    private static TickEventHandler tickEventHandler = null;
    // Last tax generation timestamp (persistent across server restarts)
    private static long lastTaxGenerationTime = 0L;

    public static void initialize(MinecraftServer server) {
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Initializing Tax Manager...");
        }
        serverInstance = server;

        loadTaxData(server);

        loadLastTaxGenerationTime();

        if (tickEventHandler != null) {
            MinecraftForge.EVENT_BUS.unregister(tickEventHandler);
        }

        tickEventHandler = new TickEventHandler();
        MinecraftForge.EVENT_BUS.register(tickEventHandler);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping. Saving tax data and timestamp...");
        saveTaxData(); // Save tax data when server stops
        saveLastTaxGenerationTime(); // CRITICAL: Save timestamp on shutdown

        try {
            net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.endAllClaimingRaids();
        } catch (Exception e) {
            LOGGER.error("Error ending claiming raids on shutdown", e);
        }
    }

    public static class TickEventHandler {
        private int tickCount = 0; // Check every 20 ticks (1 second) for performance
        private int abandonmentTickCount = 0; // Check abandonment every hour (72000 ticks)
        private int cleanupTickCount = 0; // Check [abandoned] cleanup every 30 minutes (36000 ticks)
        private int nullOwnerCheckCount = 0;

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java -Pattern 'ServerStopping|shutdownAndFlush|save' -Context 3,6" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 408ms:

  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:16:import net.machiavelli.minecolonytax.db.WarStatsDB;
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:17:import 
net.machiavelli.minecolonytax.faction.FactionManager;
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:18:import 
net.minecraftforge.event.server.ServerStartingEvent;
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:19:import 
net.minecraftforge.event.server.ServerStoppingEvent;
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:20:import 
net.minecraftforge.eventbus.api.SubscribeEvent;
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:21:import net.minecraftforge.fml.ModList;
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:22:import net.minecraftforge.fml.ModLoadingContext;
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:23:import net.minecraftforge.fml.common.Mod;
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:24:import net.minecraftforge.fml.config.ModConfig;
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:25:import 
net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:156:        // IMMEDIATELY here, at 
ServerStartingEvent — BEFORE MineColonies finishes loading
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:157:        // colonies. A colony that was only 
transiently owner-null mid-load would then get a
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:158:        // synthetic '[AUTO_OWNER]' placeholder 
written into its permissions and be flagged
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:159:        // abandoned, corrupting its saved data 
and bricking the world on the next load.
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:160:        // That immediate pass is removed. 
Automatic owner-repair / abandoned-entry cleanup
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:161:        // now runs ONLY when the abandonment 
system is explicitly enabled, and ONLY on a
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:162:        // deferred pass that lets colonies 
finish loading first.
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:163:        if 
(TaxConfig.isColonyAbandonmentSystemEnabled()) {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:164:            
net.machiavelli.minecolonytax.util.TickScheduler.scheduleDelayed(() -> {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:165:                try {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:342:    }
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:343:
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:344:    @SubscribeEvent
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:345:    public void 
onServerStopping(ServerStoppingEvent event) {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:346:        try {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:347:            WarStatsDB.shutdown();
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:348:            if (TaxConfig.isNormalLogging()) 
LOGGER.info("WarStatsDB shutdown complete");
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:349:        } catch (Throwable t) {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:350:            LOGGER.warn("Error during WarStatsDB 
shutdown: {}", t.toString());
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:351:        }
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:372:        }
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:373:
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:374:        try {
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:375:            FactionManager.saveData();
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:376:            if (TaxConfig.isNormalLogging()) 
LOGGER.info("FactionManager data saved");
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:377:        } catch (Throwable t) {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:378:            LOGGER.warn("Error saving 
FactionManager data: {}", t.toString());
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:379:        }
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:380:
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:381:        try {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:382:            TaxPolicyManager.shutdown();
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:393:        }
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:394:
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:395:        try {
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:396:            HistoryManager.saveHistory();
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:397:            if (TaxConfig.isNormalLogging()) 
LOGGER.info("HistoryManager saved");
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:398:        } catch (Throwable t) {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:399:            LOGGER.warn("Error saving 
HistoryManager: {}", t.toString());
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:400:        }
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:401:
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:402:        try {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:403:            
net.machiavelli.minecolonytax.espionage.SpyManager.shutdown();
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:406:            LOGGER.warn("Error during SpyManager 
shutdown: {}", t.toString());
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:407:        }
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:408:
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:409:        // Save active wars before TickScheduler 
shutdown — task IDs are still needed for cleanup
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:410:        try {
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:411:            WarSystem.saveActiveWars();
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:412:            if (TaxConfig.isNormalLogging()) 
LOGGER.info("Active wars saved to disk");
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:413:        } catch (Throwable t) {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:414:            LOGGER.warn("Error saving active 
wars: {}", t.toString());
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:415:        }
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:416:
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:417:        // Finish any in-progress block 
restoration synchronously (before TickScheduler
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:418:        // shutdown cancels the batched task), 
then persist the remaining active-war
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:420:        // (mirrors active_wars.json).
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:421:        try {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:422:            
net.machiavelli.minecolonytax.siege.WarBlockLedger.flushPendingRestores();
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:423:            
net.machiavelli.minecolonytax.siege.WarBlockLedger.saveToDisk();
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:424:            if (TaxConfig.isNormalLogging()) 
LOGGER.info("WarBlockLedger flushed and saved to disk");
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:425:        } catch (Throwable t) {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:426:            LOGGER.warn("Error saving 
WarBlockLedger: {}", t.toString());
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:427:        }
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:428:
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:429:        try {
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:430:            
net.machiavelli.minecolonytax.occupation.OccupationManager.shutdown();
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:454:            LOGGER.warn("Error during 
TickScheduler shutdown: {}", t.toString());
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:455:        }
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:456:
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:457:        // Must run AFTER all manager shutdowns 
above so any final saveData()
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:458:        // they queued is flushed to disk before 
the JVM exits.
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:459:        try {
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:460:            
net.machiavelli.minecolonytax.util.AsyncSaveExecutor.shutdownAndFlush();
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:461:            if (TaxConfig.isNormalLogging()) 
LOGGER.info("AsyncSaveExecutor flushed");
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:462:        } catch (Throwable t) {
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:463:            LOGGER.warn("Error flushing 
AsyncSaveExecutor: {}", t.toString());
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:464:        }
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:465:    }
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:466:}



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command './gradlew compileJava --no-daemon' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
2026-06-05T04:08:39.239658Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 1 seconds
Output:
Exception in thread "main" java.io.FileNotFoundException: C:\Users\paulm\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8-bin.zip.lck (Zugriff verweigert)
	at java.base/java.io.RandomAccessFile.open0(Native Method)
	at java.base/java.io.RandomAccessFile.open(RandomAccessFile.java:356)
	at java.base/java.io.RandomAccessFile.<init>(RandomAccessFile.java:273)
	at java.base/java.io.RandomAccessFile.<init>(RandomAccessFile.java:223)
	at org.gradle.wrapper.GradleWrapperMain.main(SourceFile:67)

 exited 1 in 968ms:
Exception in thread "main" java.io.FileNotFoundException: C:\Users\paulm\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8-bin.zip.lck (Zugriff verweigert)
	at java.base/java.io.RandomAccessFile.open0(Native Method)
	at java.base/java.io.RandomAccessFile.open(RandomAccessFile.java:356)
	at java.base/java.io.RandomAccessFile.<init>(RandomAccessFile.java:273)
	at java.base/java.io.RandomAccessFile.<init>(RandomAccessFile.java:223)
	at org.gradle.wrapper.GradleWrapperMain.main(SourceFile:67)

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "git diff -- build.gradle gradle.properties src/main/resources/META-INF/mods.toml CHANGELOG.md AGENTS.md CLAUDE.md wiki/Commands_\\&_Permissions.md wiki/Configuration_Guide.md wiki/War_Persistence.md" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
2026-06-05T04:08:44.407569Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 0.3 seconds
Output:
In Zeile:2 Zeichen:130
+ ... F/mods.toml CHANGELOG.md AGENTS.md CLAUDE.md wiki/Commands_\&_Permiss ...
+                                                                 ~
Das kaufmännische Und-Zeichen (&) ist nicht zulässig. Der &-Operator ist für eine zukünftige Verwendung reserviert. 
Verwenden Sie das kaufmännische Und-Zeichen in doppelten Anführungszeichen ("&"), um es als Teil einer Zeichenfolge zu 
übergeben.
    + CategoryInfo          : ParserError: (:) [], ParentContainsErrorRecordException
    + FullyQualifiedErrorId : AmpersandNotAllowed
 

 exited 1 in 334ms:
In Zeile:2 Zeichen:130
+ ... F/mods.toml CHANGELOG.md AGENTS.md CLAUDE.md wiki/Commands_\&_Permiss ...
+                                                                 ~
Das kaufmännische Und-Zeichen (&) ist nicht zulässig. Der &-Operator ist für eine zukünftige Verwendung reserviert. 
Verwenden Sie das kaufmännische Und-Zeichen in doppelten Anführungszeichen ("&"), um es als Teil einer Zeichenfolge zu 
übergeben.
    + CategoryInfo          : ParserError: (:) [], ParentContainsErrorRecordException
    + FullyQualifiedErrorId : AmpersandNotAllowed
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "git diff -- build.gradle gradle.properties src/main/resources/META-INF/mods.toml CHANGELOG.md AGENTS.md CLAUDE.md 'wiki/Commands_&_Permissions.md' wiki/Configuration_Guide.md wiki/War_Persistence.md" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 293ms:
diff --git a/AGENTS.md b/AGENTS.md
index e38a6d1..61a3ca7 100644
--- a/AGENTS.md
+++ b/AGENTS.md
@@ -1,7 +1,7 @@
 <!-- gitnexus:start -->
 # GitNexus — Code Intelligence
 
-This project is indexed by GitNexus as **War-N-Taxes-Mod---Minecolonies-Addon** (52572 symbols, 141396 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.
+This project is indexed by GitNexus as **War-N-Taxes-Mod---Minecolonies-Addon** (47956 symbols, 131375 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.
 
 > If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.
 
diff --git a/CHANGELOG.md b/CHANGELOG.md
index ebc052e..a6ed04f 100644
--- a/CHANGELOG.md
+++ b/CHANGELOG.md
@@ -1,11 +1,82 @@
 # Changelog
 
-All notable changes to the War N Tax mod will be documented in this file.
+All notable changes to WarNTaxes (the War 'N Taxes MineColonies addon) will be documented in this file.
 
 The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
 and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
 
-## [Unreleased]
+## [5.0.0] - 2026-06-05
+
+This is the **WarNTaxes 5.0** release: the mod is rebranded to **WarNTaxes** and gains a
+full **Siege SMP** ruleset, persistent siege damage, a dependency-stack update to the latest
+MineColonies, and a server-stability/performance pass — on top of the Occupation, War
+Persistence, and Espionage systems consolidated from earlier 5.0 development below.
+
+### Added - Siege SMP: Colony Tiers & Conquest
+
+- Colonies now have tiers. Your **first** colony is a **Primary** (capital); the rest are **Secondary** (outposts). A Primary can be besieged and have its taxes occupied, but its ownership cannot be permanently claimed unless the server enables `EnablePrimaryColonyTransfer` (default off) — the owner reclaims a tax-occupied capital by winning a counter-besiege. Secondary colonies can be permanently claimed after a long-enough besiege.
+- **Multi-besieger sieges**: a colony can be besieged by several attackers at once; the first to break through wins the spoils ("resolved first", no double awards). Besiegers fight **solo** (their colony-mates cannot damage the defenders), but defenders may rally **allies**, who receive a clickable call-to-arms notification.
+- The besiege winner takes a configurable share of the loser's treasury (`BesiegeSpoilPercentOfLoserTreasury`, cap-aware so no coins are lost).
+- During a besiege the colony's **chests and villagers cannot be used** by attackers.
+- Besieged and occupied colonies now appear in the **Vassals tab** with colour-coded status badges (vassal / tax-occupied / provisional).
+- During an active war, blocks can no longer be broken by hand — only **explosive damage** destroys them; chests, doors and combat still work.
+
+### Added - Siege Victory Objectives (experimental)
+
+Gated behind `EnableExperimentalSiegeObjectives` (default off):
+
+- **Plant the Banner** — attackers are given a Siege Banner; planting it inside the town-hall borders starts a war-scoped capture timer shown as a boss bar to participants only. Hold it for `BannerCaptureMinutes` to win; defenders can break the banner to stop the capture.
+- **Demolish the Town Hall** — destroy the town-hall *building* with explosives (ideally siege weaponry). Each valid hit (within `MaxSiegeRadius`) makes the attacker **glow** and broadcasts their coordinates to the defenders; after `TownHallExplosiveHitsRequired` hits the colony falls. A per-attacker cooldown (`TownHallHitCooldownMinutes`) sits between counted hits.
+
+### Added - Militia Investment & Persistent Siege Damage
+
+- New **Militia** investment spawns extra defenders scaled to a colony's guard count (applies to wars, besieges, and raids). Militia extend a fight but **never count as victory objectives** — only guards and player lives do — and despawn when the conflict ends.
+- Explosion damage during a war is now recorded and **fully restored when the war ends**, blocks and block-entity contents (chests, signs) intact, so siege warfare no longer permanently scars the map. The damage ledger **persists across server restarts** (saved on stop, reloaded and pruned on start).
+- **Explosion't integration**: when the Explosion't mod is installed, a war-aware mixin pauses its regeneration during any active war/raid/besiege and resumes only after the conflict ends (`DeferRestorationToExplosiont`).
+
+### Added - New Configuration (War Settings)
+
+- `EnablePrimaryColonyTransfer`, `PrimaryColonyTaxOccupationDays`, `BesiegeSpoilPercentOfLoserTreasury`, `EnableExperimentalSiegeObjectives`, `TownHallExplosiveHitsRequired`, `TownHallHitCooldownMinutes`, `MaxSiegeRadius`, `AttackerGlowSeconds`, `BannerCaptureMinutes`, `DeferRestorationToExplosiont`.
+
+### Changed - Dependencies (latest MineColonies stack)
+
+- Updated to **MineColonies 1.20.1-1.1.1237** and the matching coherent dependency stack: Structurize 1.0.816, BlockUI 1.0.194, Domum Ornamentum 1.0.301, Multi-Piston 0.0.47, JEI 15.20.0.130, FTB Teams 2001.3.2, Recruits 1.15.0, SDM Shop 7.2.2, SDM Engine Core 2001.4.0. Verified the mod compiles against the new API and the whole stack loads coherently (MineColonies mandates minimum Structurize/BlockUI/Domum versions). See `DEPENDENCY_COMPATIBILITY.md`.
+
+### Performance - Server Stability at Scale
+
+A server-stability pass for high player counts and hundreds of colonies (see `OPTIMIZATION_AUDIT.md`):
+
+- Removed an O(N²) per-tax-cycle disk write in the random-events system (now one coalesced save per cycle).
+- Moved treasury, war-exhaustion, and tax-data saves onto the off-thread async writer — no synchronous disk I/O on the server tick or on player actions.
+- Throttled a perpetual all-colony permission scan from every 5 seconds to every 5 minutes.
+- Added cheap early-outs to the highest-frequency event handlers (citizen-death, block break/place/interact, abandoned-colony protection) and an allocation-free besiege-active check on the block-interaction filter.
+- Fixed a repeating-task leak on every war declaration and hardened the join-phase start so an ended war can no longer be resurrected.
+
+### Fixed - MineColonies 1.1.1237 Compatibility
+
+- MineColonies changed `IPermissions.setOwner` from taking a player UUID to taking an online player entity, and `getOwner()` now returns a cached owner id updated only by `setOwner`. Colony-ownership assignment in the abandonment, claiming-raid, and admin-fix paths was rewritten accordingly (the old reflection failed with "argument type mismatch", leaving colonies ownerless). See `PORTING_NOTES.md`.
+
+### Fixed - World corruption when claiming/using a town hall (CRITICAL)
+
+- Root cause: the automatic colony owner-repair ran **immediately at `ServerStartingEvent`**, before MineColonies finished loading colonies. A colony that momentarily reported a null owner mid-load had a synthetic `[AUTO_OWNER]` placeholder UUID written into its permissions and was flagged abandoned, corrupting its saved data and bricking the world on the next load. This ran unconditionally in every version (matching reports that downgrading did not help).
+- Added master switch `EnableColonyAbandonmentSystem` (default **false**). Out of the box the mod now performs **no automatic writes** to MineColonies owner/permission state. The inactivity auto-abandon, debt-bankruptcy abandonment, null-owner repair, and abandoned-entry cleanup all require this switch to be enabled.
+- Removed the immediate `ServerStartingEvent` owner-fix pass; any owner-repair now runs only on a deferred pass after colonies have finished loading, and only when the system is enabled.
+- `emergencyFixAllNullOwners()` / `fixNullOwnerColony()` no longer inject synthetic placeholder owners or flag colonies abandoned — they only promote a real online colony-manager to owner via `setOwner`, otherwise they leave the colony untouched.
+- `isColonyAbandoned()` is now a pure read with no side effects (it previously mutated colony state during a status check).
+- `cleanupAbandonedEntries()` now matches only the exact synthetic markers the mod itself wrote; the old broad heuristics (any name containing "abandoned", `~`/`#` prefixes, a bogus UUID-length check) could delete a real player and leave a colony ownerless.
+- Added an always-on, **removal-only** migration that heals worlds already corrupted by older versions: it strips legacy synthetic placeholder entries, promoting a real manager to owner first so a colony is never left ownerless.
+- Added null-world guards to all permission rank writes so a null Level is never passed into MineColonies.
+
+### Fixed - Claimed tax coins never appearing in shop balance
+
+- All three tax-claim paths (claim GUI packet, `/claimtax`, `/wnt claimtax`) deducted the tax from the colony ledger **before** delivery and never refunded on failure — so if the shop economy was unavailable or the deposit failed, the tax was lost. Delivery now refunds the colony ledger (`TaxManager.adjustTax`) on any failure; taxes are never silently lost.
+- When `EnableSDMShopConversion=true` but the economy is unavailable, the claim is refunded with a clear message instead of silently dropping surprise currency items.
+- Added `SDMCurrencyName` config (default `sdm_coin`) so servers whose SDM-Economy currency id differs from the default can point taxes at the correct currency (previously hardcoded, causing deposits into an invisible currency).
+- Added a one-time WARN when conversion is enabled but unavailable, and the `/wnt debug sdm status` command to diagnose integration state, currency id, and wallet balance (covers the single-player `CurrencyPlayerData.SERVER`-not-ready timing case).
+
+### Changed - Debug commands consolidated under `/wnt debug`
+
+- Moved `emergencyfix`, `fixnullowners`, `cleanupabandonedentries`, `forcecleanupcolony`, `debugbossbar` (now `bossbar`), and `claimraidstatus` under the `/wnt debug` prefix, alongside the new `/wnt debug sdm status`.
 
 ### Fixed - Colony Ownership Tracking
 
@@ -44,8 +115,6 @@ and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0
 - Besieged and occupied colony status is now displayed in the colony list GUI ("Besieged" in red, "Occupied" in orange)
 - Players now receive login notifications for active wars, besieges, and occupations on their colonies, including when events occurred while they were offline
 
-## [5.0.0] - 2026-03-13
-
 ### Added - Colony Occupation System
 
 - Winning a war no longer immediately transfers a colony. Instead the losing colony enters an **Occupation phase**
diff --git a/CLAUDE.md b/CLAUDE.md
index e9f8d40..4dd809a 100644
--- a/CLAUDE.md
+++ b/CLAUDE.md
@@ -114,7 +114,7 @@ Key pages:
 <!-- gitnexus:start -->
 # GitNexus — Code Intelligence
 
-This project is indexed by GitNexus as **War-N-Taxes-Mod---Minecolonies-Addon** (52572 symbols, 141396 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.
+This project is indexed by GitNexus as **War-N-Taxes-Mod---Minecolonies-Addon** (47956 symbols, 131375 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.
 
 > If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.
 
diff --git a/build.gradle b/build.gradle
index dff0069..f82fb52 100644
--- a/build.gradle
+++ b/build.gradle
@@ -10,7 +10,9 @@ version = mod_version
 group = mod_group_id
 
 base {
-    archivesName = mod_id
+    // Release artifact name (filename only). The in-jar MOD_ID stays "minecolonytax"
+    // (gradle.properties mod_id) so existing worlds/saves are unaffected by the rebrand.
+    archivesName = "WarNTaxes"
 }
 
 // Mojang ships Java 17 to end users in 1.18+, so your mod should target Java 17.
@@ -145,20 +147,20 @@ dependencies {
         jarJar.ranged(it, '[8.0.0,9.0.0)')
     }
 
-    implementation fg.deobf("curse.maven:minecolonies-245506:7629580")
-    runtimeOnly fg.deobf("curse.maven:blockui-522992:7541343")
-    runtimeOnly fg.deobf("curse.maven:multi-piston-303278:5204918")
-    runtimeOnly fg.deobf("curse.maven:domum-ornamentum-527361:7585567")
-    runtimeOnly fg.deobf("curse.maven:structurize-298744:7532330")
-    runtimeOnly fg.deobf("curse.maven:jei-238222:5739402")
-    implementation fg.deobf("curse.maven:sdm-shop-948942:7395729")
+    implementation fg.deobf("curse.maven:minecolonies-245506:8186502")
+    runtimeOnly fg.deobf("curse.maven:blockui-522992:7606230")
+    runtimeOnly fg.deobf("curse.maven:multi-piston-303278:7097889")
+    runtimeOnly fg.deobf("curse.maven:domum-ornamentum-527361:8179147")
+    runtimeOnly fg.deobf("curse.maven:structurize-298744:8138301")
+    runtimeOnly fg.deobf("curse.maven:jei-238222:7920915")
+    implementation fg.deobf("curse.maven:sdm-shop-948942:7935087")
     runtimeOnly fg.deobf("curse.maven:ftb-library-forge-404465:7296748")
     runtimeOnly fg.deobf("curse.maven:sdm-economy-1102542:6689080")
-    runtimeOnly fg.deobf("curse.maven:sdm-engine-core-964997:5761090")
+    runtimeOnly fg.deobf("curse.maven:sdm-engine-core-964997:7090174")
     runtimeOnly fg.deobf("curse.maven:sdm-ui-lib-1095061:6086204")
     runtimeOnly fg.deobf("curse.maven:architectury-api-419699:5137938")
-    implementation fg.deobf("curse.maven:ftb-teams-forge-404468:5267190")
-    implementation fg.deobf("curse.maven:recruits-523860:6500292")
+    implementation fg.deobf("curse.maven:ftb-teams-forge-404468:7499810")
+    implementation fg.deobf("curse.maven:recruits-523860:7906232")
     implementation fg.deobf("vazkii.patchouli:Patchouli:1.20.1-84-FORGE")
     // easy-factions removed: ServerConfig.onLoad bug crashes dev env (no code depends on it)
     // runtimeOnly fg.deobf("curse.maven:easy-factions-1419148:7678446")
diff --git a/gradle.properties b/gradle.properties
index 97503e4..6986a64 100644
--- a/gradle.properties
+++ b/gradle.properties
@@ -44,7 +44,7 @@ mapping_version=2023.09.03-1.20.1
 # Must match the String constant located in the main mod class annotated with @Mod.
 mod_id=minecolonytax
 # The human-readable display name for the mod.
-mod_name=War 'N Taxes Mod
+mod_name=WarNTaxes
 # The license of the mod. Review your options at https://choosealicense.com/. All Rights Reserved is the default.
 mod_license=MIT
 # The mod version. See https://semver.org/
@@ -56,7 +56,7 @@ mod_group_id=net.machiavelli.minecolonytax
 # The authors of the mod. This is a simple text string that is used for display purposes in the mod list.
 mod_authors=mchivelli
 # The description of the mod. This is a simple multiline text string that is used for display purposes in the mod list.
-mod_description=Minecolonies: War 'N Taxes Addon by Machiavelli
+mod_description=WarNTaxes - war, taxation, espionage, siege and colony-management addon for MineColonies, by Machiavelli
 # Dependency Version Ranges
 minecolonies_version_range=[1.1.1170,)
 blockui_version_range=[1.0.194,)
diff --git a/src/main/resources/META-INF/mods.toml b/src/main/resources/META-INF/mods.toml
index dc2c902..75e209b 100644
--- a/src/main/resources/META-INF/mods.toml
+++ b/src/main/resources/META-INF/mods.toml
@@ -19,7 +19,7 @@ modId="minecolonytax" #mandatory
 # The version number of the mod
 version="${mod_version}" #mandatory
 # A display name for the mod
-displayName="War N' Taxes: Minecolonies Addon" #mandatory
+displayName="WarNTaxes" #mandatory
 # A URL to query for updates for this mod. See the JSON update specification https://docs.minecraftforge.net/en/latest/misc/updatechecker/
 #updateJSONURL="https://change.me.example.invalid/updates.json" #optional
 # A URL for the "homepage" for this mod, displayed in the mod UI
diff --git a/wiki/Commands_&_Permissions.md b/wiki/Commands_&_Permissions.md
index 65efb52..b05fd48 100644
--- a/wiki/Commands_&_Permissions.md
+++ b/wiki/Commands_&_Permissions.md
@@ -76,15 +76,16 @@ These commands require OP Permission Level 2 or higher and are meant for server
 | `/wnt protectcolony <colony>` | Protect an abandoned colony from being claimed by other players. |
 | `/wnt unprotectcolony <colony>` | Remove claiming protection from an abandoned colony. |
 | `/wnt listprotected` | List all abandoned colonies that are protected from claiming. |
-| `/wnt claimraidstatus <colony>` | Check the status of a claiming raid on a colony. |
 | `/wnt taxgen disable <colonyId>` | Completely halt tax generation for a specific colony. |
 | `/wnt taxgen enable <colonyId>` | Resume tax generation for a specific colony. |
-| `/wnt cleanupabandonedentries` | Remove stale abandonment tracking records from memory. |
-| `/wnt debugbossbar <colony>` | Debug the boss bar state for the war involving a colony. |
-| `/wnt forcecleanupcolony <colony>` | Force a full cleanup of a colony's war/raid state. |
-| `/wnt emergencyfix` | Emergency fix for broken war state on the server. |
-| `/wnt fixnullowners` | Scan and fix colonies with null owner records. |
 | **Debug Commands (Admin)** | |
+| `/wnt debug sdm status` | Show SDMShop/SDM-Economy integration state and your wallet balance (diagnose "tax coins not appearing"). |
+| `/wnt debug claimraidstatus <colony>` | Check the status of a claiming raid on a colony. |
+| `/wnt debug cleanupabandonedentries` | Remove stale/synthetic abandonment tracking entries from colony permissions. |
+| `/wnt debug bossbar <colony>` | Debug the boss bar state for the war involving a colony. |
+| `/wnt debug forcecleanupcolony <colony>` | Force a full cleanup of a colony's war/raid state. |
+| `/wnt debug emergencyfix` | Emergency repair pass for null-owner colonies (abandonment system must be enabled). |
+| `/wnt debug fixnullowners` | Scan and repair colonies with null owner records (abandonment system must be enabled). |
 | `/wnt debug war` | Show deeply technical debug information for all active wars. |
 | `/wnt debug guards [colony]` | Debug the guard and guard tower counting mechanisms for a given colony. |
 | `/wnt debug tax [colony]` | Show a per-building tax and maintenance breakdown for a colony. |
diff --git a/wiki/Configuration_Guide.md b/wiki/Configuration_Guide.md
index b939413..aae02ba 100644
--- a/wiki/Configuration_Guide.md
+++ b/wiki/Configuration_Guide.md
@@ -9,6 +9,11 @@ Below is a list of all server configurations for War 'N Taxes. All settings live
 ### EnableSDMShopConversion
 - **Description**: Enable SDMShop economy integration. When true, tax claims and war payouts use SDMShop balances. When false, physical currency items are used instead.
 - **Default Value**: true
+- **Note**: If the shop economy is unavailable when you claim tax, the tax is now refunded to the colony (kept claimable) instead of being lost. Use `/wnt debug sdm status` to check why the economy is unavailable.
+
+### SDMCurrencyName
+- **Description**: The SDM-Economy currency id that claimed taxes are deposited into. This must match the currency id configured in SDM-Economy (not its display name). If your server uses a currency id other than the default, set it here, otherwise claimed taxes go into a currency you never see.
+- **Default Value**: sdm_coin
 
 ### CurrencyItemName
 - **Description**: The item used as physical currency when SDMShop conversion is disabled (e.g., 'minecraft:emerald').
@@ -282,8 +287,13 @@ Below is a list of all server configurations for War 'N Taxes. All settings live
 - **Description**: If true, the resistance effect during raids and wars is also applied to all citizens, not only guards.
 - **Default Value**: false
 
+### EnableColonyAbandonmentSystem
+- **Description**: Master switch for the entire automatic colony abandonment and owner-repair system. When false (the default), the mod never automatically rewrites a colony's owner or permissions. This is the safe default and prevents the colony-data corruption that could occur when owner-repair ran while colonies were still loading. When true, the inactivity auto-abandon, debt-bankruptcy abandonment, null-owner repair, and abandoned-entry cleanup all become active (each still has its own sub-toggle).
+- **Default Value**: false
+- **Note**: A one-time, removal-only cleanup of legacy `[AUTO_OWNER]` placeholder entries written by older versions always runs on startup regardless of this switch, so existing worlds are healed automatically. EnableColonyAutoAbandon below has no effect unless this master switch is also true.
+
 ### EnableColonyAutoAbandon
-- **Description**: When enabled, colonies whose owners and officers have not physically visited for the configured number of days are automatically abandoned.
+- **Description**: When enabled, colonies whose owners and officers have not physically visited for the configured number of days are automatically abandoned. Requires EnableColonyAbandonmentSystem to also be true.
 - **Default Value**: true
 
 ### ColonyAutoAbandonDays
@@ -1312,3 +1322,49 @@ See [WarDefenderDrainReduction](#wardefenderdrainreduction) in the Treasury sect
 ### BesiegePlayerStayRadius
 - **Description**: Maximum distance (blocks) from the colony centre the besieger may stray. Exceeding this cancels the raid.
 - **Default Value**: 100
+
+---
+
+## Siege SMP — Colony Tiers, Spoils & Victory Objectives
+
+These settings live under the `[War Settings]` section of `minecolonytax.toml` and control the Siege SMP rules added after the v2 update.
+
+### EnablePrimaryColonyTransfer
+- **Description**: If true, a player's first (Primary) colony can have its ownership permanently transferred when it loses a war. If false (default), a Primary colony can be tax-occupied and besieged but never claimed — the owner can always reclaim it. Secondary colonies are always claimable regardless of this setting.
+- **Default Value**: false
+
+### PrimaryColonyTaxOccupationDays
+- **Description**: How many in-game days a defeated Primary colony stays tax-occupied (its taxes diverted to the victor) before the occupation expires and the original owner regains their taxes. The owner can also end it early by winning a counter-besiege.
+- **Default Value**: 7
+
+### BesiegeSpoilPercentOfLoserTreasury
+- **Description**: Percentage of the loser colony's Treasury paid to the winner when a besiege resolves. The transfer is cap-aware — only the winner's available headroom is deposited, so no coins are lost.
+- **Default Value**: 25
+
+### EnableExperimentalSiegeObjectives
+- **Description**: Master switch for the experimental victory objectives (Plant the Banner and Town Hall Demolition). When false (default), wars are won only by the classic life/guard conditions. **Must be true to test or use the banner and demolition objectives below.**
+- **Default Value**: false
+
+### TownHallExplosiveHitsRequired
+- **Description**: Number of valid explosive hits on the defender's Town Hall *building* required for the attacker to win via demolition. Only counts when EnableExperimentalSiegeObjectives is true.
+- **Default Value**: 5
+
+### TownHallHitCooldownMinutes
+- **Description**: Cooldown (minutes) between counted explosive hits on the Town Hall, per attacker. Hits landed during the cooldown do not advance the demolition objective.
+- **Default Value**: 5
+
+### MaxSiegeRadius
+- **Description**: Maximum distance (blocks) from the Town Hall an attacker may be for an explosive hit to count toward the demolition objective.
+- **Default Value**: 500
+
+### AttackerGlowSeconds
+- **Description**: How long (seconds) an attacker is made to glow after landing a Town Hall hit. Their coordinates are also broadcast to the defenders. Set to 0 to disable the glow.
+- **Default Value**: 30
+
+### BannerCaptureMinutes
+- **Description**: How long (minutes) the Siege Banner must remain planted inside the Town Hall borders for the attacker to win via the Plant the Banner objective. A war-scoped boss bar (visible only to war participants) counts down this timer.
+- **Default Value**: 10
+
+### DeferRestorationToExplosiont
+- **Description**: If true and the Explosion't mod is installed, explosion-damage restoration is handed off to Explosion't (whose heal is paused during conflict by a war-aware mixin and resumes after). If false (default), the built-in WarBlockLedger captures and restores war-damaged blocks itself. See [War Persistence](War_Persistence.md#explosion-damage-restoration-warblockledger).
+- **Default Value**: false
diff --git a/wiki/War_Persistence.md b/wiki/War_Persistence.md
index 6799f51..c55d2aa 100644
--- a/wiki/War_Persistence.md
+++ b/wiki/War_Persistence.md
@@ -224,3 +224,43 @@ These runtime-only objects are **recreated** on load, not serialized:
 - **Active `TickScheduler` timers** — Rescheduled based on remaining time calculations
 - **Glow effects** — Reapplied to online participants
 - **Guard resistance buffs** — Reapplied via `GuardResistanceHandler`
+
+---
+
+## Explosion Damage Restoration (WarBlockLedger)
+
+**Added after the v2 update.**
+
+War state is not the only thing that must survive a restart. During a war, explosions damage blocks; those blocks are restored when the war ends. If wars persist across a restart but the *damage ledger* does not, blocks broken before the restart would never be restored when the war later ends. `WarBlockLedger` closes that gap.
+
+### What it does
+
+- On `ExplosionEvent.Detonate` (HIGHEST priority) during an active war, it snapshots every affected block — its `BlockState`, position, and full block-entity NBT (chest contents, sign text, etc.) — into a per-war ledger keyed by the war's UUID. First snapshot per position wins, so repeat blasts on the same spot still restore the original pre-war state.
+- Scope is limited to a padding radius around the defender colony, and a hard 50,000-entry-per-war cap guards against a runaway explosion loop.
+- On `endWar()`, the ledgered blocks are restored to the world at ~50 blocks per tick to avoid chunk flicker. Block entities round-trip via `saveWithFullMetadata()` (capture) and `BlockEntity.loadStatic()` (restore).
+
+### Persistence
+
+The ledger is saved to **`config/warntax/war_block_ledger.nbt`** and follows the same lifecycle as `active_wars.json`:
+
+| When | Action |
+|---|---|
+| Server stop (`ServerStoppingEvent`) | `flushPendingRestores()` synchronously finishes any in-progress restoration, then `saveToDisk()` writes the remaining active-war ledgers. Both run **before** `TickScheduler.shutdown()`. |
+| Server start (`ServerStartingEvent`) | `loadFromDisk()` runs **before** war resume (so a war that ends on resume can still restore), then `pruneOrphans()` drops any ledger whose war did not come back. |
+
+NBT (not Gson/JSON) is used here specifically because `BlockState` and block-entity snapshots are NBT-native and round-trip cleanly — unlike the war-state files, which stay JSON for human-readability.
+
+### Edge cases handled
+
+| Scenario | Behavior |
+|---|---|
+| Restart mid-war, war ends later | Blocks broken before the restart are restored correctly from the reloaded ledger. |
+| Server stops *during* a restoration | Remaining blocks are flushed synchronously and saved with the world — none are stranded. |
+| War failed to resume (orphan ledger) | Pruned at startup; cannot resurrect blocks at an unrelated war's end. |
+| Partial war-restore (`active_wars.json.failed-<ts>`) | The ledger file is **not** deleted, so a manual recovery still has the damage data. |
+| Corrupt `war_block_ledger.nbt` | Preserved as `war_block_ledger.nbt.failed-<ts>`; server still boots. |
+| Double-restore after a crash | Not possible — `restoreWarDamage` removes the ledger first, prune drops non-active wars, and `active_wars.json` is deleted on load + co-written at shutdown, so a "war active and already restored" state cannot occur. |
+
+### Explosion't integration
+
+If the **Explosion't** mod is installed and `DeferRestorationToExplosiont = true`, the built-in ledger steps aside and a war-aware mixin (`WorldTickHandlerMixin`) pauses Explosion't's heal countdown while any war, raid, or besiege is active in that level — so destruction only regenerates **after** the conflict. When Explosion't is absent the mixin no-ops silently and the WarBlockLedger is the standalone fallback. `ExplosiontCompat.shouldDeferToExplosiont()` selects the active path.
warning: in the working copy of 'AGENTS.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'CLAUDE.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'wiki/Configuration_Guide.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'wiki/War_Persistence.md', LF will be replaced by CRLF the next time Git touches it

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Write-Output '--- "'$null ---'"'; Get-Content -LiteralPath '"'$null'"' -Raw; Write-Output '--- reviews list ---'; Get-ChildItem -Path reviews -Force | Select-Object Name,Length,LastWriteTime | Format-Table -AutoSize; Write-Output '--- DEP ---'; Get-Content -Path DEPENDENCY_COMPATIBILITY.md -TotalCount 40; Write-Output '--- PORT ---'; Get-Content -Path PORTING_NOTES.md -TotalCount 40" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 431ms:
--- $null ---
Der Befehl "{"session_id":"76167641-43bd-410a-ad9c-4fd04b65d66c"" ist entweder falsch geschrieben oder
konnte nicht gefunden werden.

--- reviews list ---

Name                                    Length LastWriteTime      
----                                    ------ -------------      
codex-townhall-sdm-review.md            196296 05.06.2026 06:08:39
fix-setowner-v2.diff                     13707 05.06.2026 01:45:26
fix-setowner.diff                        13243 05.06.2026 01:37:22
opt-batch1-critical.diff                  9616 03.06.2026 19:01:14
opt-batch23.diff                         16145 03.06.2026 19:14:23
opt-batch4.diff                          11560 03.06.2026 19:27:28
opt-final-consolidated.diff              34856 05.06.2026 00:46:52
opt-final-fixes.diff                     16777 05.06.2026 00:58:44
opt-fixc-final.diff                       6960 05.06.2026 01:03:33
opt-warsystem-final.diff                  7786 05.06.2026 01:06:01
rework-bundle.md                         18264 25.05.2026 04:36:53
rework-codex.md                         493943 25.05.2026 04:41:34
rework-v2-bundle.md                       4738 25.05.2026 04:43:13
rework-v2-codex.md                      147783 25.05.2026 04:45:47
REWORK_RESULTS.md                         9988 25.05.2026 16:02:43
step-01-bundle.md                        15805 25.05.2026 03:44:43
step-01-codex.md                        171084 25.05.2026 03:47:07
step-02-bundle.md                        33619 25.05.2026 03:52:44
step-02-codex.md                         39587 25.05.2026 03:53:24
step-03-bundle.md                        15895 25.05.2026 03:57:46
step-03-codex.md                        214348 25.05.2026 03:59:48
step-04-bundle.md                        20753 25.05.2026 04:03:27
step-04-codex.md                         24021 25.05.2026 04:03:49
step-05-bundle.md                         7249 25.05.2026 04:05:27
step-05-codex.md                        372480 25.05.2026 04:08:00
step-06-bundle.md                         8131 25.05.2026 04:09:29
step-06-codex.md                         11295 25.05.2026 04:10:20
step-07-bundle.md                         6528 25.05.2026 04:12:08
step-07-codex.md                        180755 25.05.2026 04:13:51
step-08-bundle.md                         8103 25.05.2026 04:16:20
step-08-codex.md                         11823 25.05.2026 04:16:52
step-09-bundle.md                         2977 25.05.2026 04:18:16
step-09-codex.md                          4332 25.05.2026 04:18:33
step-10-bundle.md                         8729 25.05.2026 04:20:06
step-10-codex.md                         12868 25.05.2026 04:20:30
step-11-bundle.md                        12930 25.05.2026 04:24:27
step-11-codex.md                        107499 25.05.2026 04:25:43
SUMMARY.md                                9328 25.05.2026 04:27:13
wave-12-bundle.md                         9956 25.05.2026 14:43:04
wave-12-codex.md                       1201038 25.05.2026 14:46:25
wave-12-v2-bundle.md                       602 25.05.2026 14:48:09
wave-12-v2-codex.md                    1205565 25.05.2026 14:50:04
wave-15-bundle.md                        28172 25.05.2026 17:37:25
wave-15-codex.md                         33759 25.05.2026 17:37:57
wave-16-bundle.md                        21426 25.05.2026 17:45:55
wave-16-codex.md                        547859 25.05.2026 17:48:09
wave-17-ledger-persistence-bundle.diff   17019 03.06.2026 10:07:57
wave-17-ledger-persistence-codex.md       2964 03.06.2026 10:36:35
wave-17-ledger-persistence-v2.diff       24834 03.06.2026 10:17:34
wave-8-11-bundle.md                      39288 25.05.2026 04:57:53
wave-8-11-codex.md                      251115 25.05.2026 05:00:54
wave-8-11-v2-bundle.md                   15915 25.05.2026 05:02:24
wave-8-11-v2-codex.md                   121135 25.05.2026 05:04:03
waves-13-14-bundle.md                    65063 25.05.2026 15:58:00
waves-13-14-codex.md                    471493 25.05.2026 16:00:14


--- DEP ---
# Dependency Compatibility â€” Latest 1.20.1 Forge Stack

**Checked:** 2026-06-03 against MineColonies **1.20.1-1.1.1237-snapshot** (CurseForge file `8186502`, released 2026-06-02) and the latest 1.20.1-Forge build of every other dependency.

## Verdict

âœ… **War 'N Taxes is compatible with the latest MineColonies + the full latest dependency stack** â€” *provided the whole MineColonies stack is upgraded together.*

The one hard break found: our pinned **Structurize was `1.0.800`**, but MineColonies 1.1.1237 **mandates `structurize >= 1.0.806`**. Bumping MineColonies alone would crash at load (`Missing or unsupported mandatory dependencies`). Fixed by upgrading the whole coherent stack (below).

### How this was verified
1. Our code **compiles** against MineColonies 1.1.1237 (`./gradlew compileJava` â€” BUILD SUCCESSFUL, only pre-existing deprecation warnings). Covers every compile-time MineColonies API we use (`ModEntities.MERCENARY`, `IBuilding.isInBuilding`, `ITownHall`, `Rank.isHostile`, eventbus events, `IColony*`).
2. The full **runtime classpath resolves** cleanly with all updated deps (`./gradlew dependencies --configuration runtimeClasspath` â€” BUILD SUCCESSFUL, no unresolved).
3. **FML-style range check**: every mandatory `versionRange` in each mod's own `mods.toml` is satisfied by the chosen set (verified by extracting `META-INF/mods.toml` from each jar). Chains checked: MineColonies â†’ Structurize/BlockUI/Domum/Multi-Piston; FTB Teams â†’ FTB Library/Architectury; SDM Shop â†’ SDM Engine Core/Economy/UI-Lib/FTB Library/Architectury.
4. The two reflection-based paths (`ColonyBuildingUtil`, mercenary spawn in `MilitiaSpawner`) are version-agnostic by design (try-both-method-names / `NoClassDefFoundError` guard).

> Final empirical step (human): a `./gradlew runServer` / `runClient` smoke test to confirm in-game load. All deterministic checks above pass.

## The verified coherent set (in `build.gradle`)

| Mod | Old file | Old ver | New file | New ver | Action |
|-----|----------|---------|----------|---------|--------|
| **MineColonies** (245506) | 7629580 | 1.1.x | **8186502** | 1.1.1237-snapshot | â¬† bump (trigger) |
| **Structurize** (298744) | 7532330 | 1.0.800 | **8138301** | 1.0.816 | â¬† **bump â€” MANDATORY** (MC needs â‰¥1.0.806; 1.0.800 too old) |
| **BlockUI** (522992) | 7541343 | 1.0.193 | **7606230** | 1.0.194-snapshot | â¬† bump (old already â‰¥1.0.190) |
| **Domum Ornamentum** (527361) | 7585567 | 1.0.296 | **8179147** | 1.0.301-snapshot | â¬† bump (old already â‰¥1.0.288) |
| **Multi-Piston** (303278) | 5204918 | 1.2.43-RELEASE | **7097889** | 0.0.47-snapshot | â¬† bump (Structurize hard dep; re-versioned 1.2.xâ†’0.0.x) |
| **JEI** (238222) | 5739402 | 15.x | **7920915** | 15.20.0.130 | â¬† bump |
| **FTB Teams** (404468) | 5267190 | 2001.x | **7499810** | 2001.3.2 | â¬† bump (needs ftblib â‰¥2001.2.0 âœ“) |
| **Recruits** (523860) | 6500292 | 1.x | **7906232** | 1.15.0 | â¬† bump |
| **SDM Shop** (948942) | 7395729 | 7.1.13 | **7935087** | 7.2.2 | â¬† bump (needs ui-lib 1.8.3, econ 2.2.0, ftblib â‰¥2001.2.9, arch â‰¥9.2.14 â€” all âœ“) |
| **SDM Engine Core** (964997) | 5761090 | 2.1.1 | **7090174** | 2001.4.0 | â¬† bump (needs arch â‰¥9.2.14 âœ“) |
| Architectury (419699) | 5137938 | 9.2.14 | 5137938 | 9.2.14 | âœ” keep â€” **last 1.20.1 build** |
| FTB Library (404465) | 7296748 | 2001.2.12 | 7296748 | 2001.2.12 | âœ” keep â€” **last 1.20.1 build** |
| JourneyMap (32274) | 5789363 | 5.10.3 | 5789363 | 5.10.3 | âœ” keep â€” **last 1.20.1 build** |
| Explosion't (388909) | 4848559 | 2.4.8 | 4848559 | 2.4.8 | âœ” keep â€” **last 1.20.1 build** (Nov 2023; none newer) |
| SDM Economy (1102542) | 6689080 | 2.2.0 | 6689080 | 2.2.0 | âœ” keep â€” **last 1.20.1 build** |
| SDM UI Lib (1095061) | 6086204 | 1.8.3 | 6086204 | 1.8.3 | âœ” keep â€” **last 1.20.1 build** |

## Caveats / ceilings for the 1.20.1 line
--- PORT ---
# Porting Notes â€” for the 1.21 / NeoForge Port Agent

**Temporary handoff doc.** Bugs and MineColonies-API compatibility findings discovered while validating the 1.20.1 mod against **MineColonies 1.1.1237** (CurseForge file 8186502) on 2026-06-05. The 1.21/NeoForge port (`C:\Dev\MinecolonyTaxAddon-Dev-1.21 Neoforge`) targets a newer MineColonies still, so re-verify each of these against the port's MineColonies build.

Search the 1.20.1 source for the marker `[1.21-PORT]` â€” every spot below is tagged there in code.

---

## 1. CRITICAL â€” `IPermissions.setOwner` signature changed (UUID â†’ Player) + cached `ownerUUID`

**What broke (1.20.1, found via dedicated-server boot test):** MineColonies changed
`IPermissions.setOwner(UUID)` â†’ **`boolean setOwner(net.minecraft.world.entity.player.Player)`**.
Three sites reflected `setOwner(UUID)` and, on failure, invoked any 1-arg `setOwner` with a UUID â†’
`java.lang.IllegalArgumentException: argument type mismatch` at runtime, leaving colonies **ownerless**
(GUI crashes, abandonment/claiming ownership transfer silently failing).

**Crucial MineColonies internals (verified in `core/colony/permissions/Permissions.java`):**
- `getOwner()` returns a **cached `ownerUUID` field**. It only recomputes from the OWNER-ranked
  player (`getOwnerEntry()`) when `ownerUUID == null`.
- **`setOwner(Player)` is the ONLY public method that updates `ownerUUID`** (also `setOwnerAbandoned()`,
  which assigns a *random* uuid â€” used for the "[abandoned]" owner the mod deliberately avoids).
- **`setPlayerRank(uuid, OWNER, level)` does NOT update `ownerUUID`** â€” it only changes the rank map.
  So if `ownerUUID` is already non-null, setting OWNER rank will NOT change `getOwner()`.
- **`removePlayer(uuid)` refuses to remove an OWNER-ranked entry** (so you can't drop the current owner that way).

**Net API limitation:** in the new MineColonies API you **cannot set an OFFLINE or SYNTHETIC player
as the colony owner** â€” `setOwner` needs an online `Player`. The old `setOwner(UUID)` allowed it.

**How it was fixed in 1.20.1 (mirror this in the port):**
| Site | Fix |
|------|-----|
| `ColonyClaimingRaidManager` (~L807) | `permissions.setOwner(claimingPlayer)` â€” claimer is an online `ServerPlayer`; complete fix. |
| `ColonyAbandonmentManager.cleanupSystemOwnerAndSetRealOwner` | resolve online `ServerPlayer`; `setOwner(online)` if online, else best-effort `setPlayerRank(uuid, OWNER, level)`. |
| `WntCommands` (system-owner emergency fix) | best-effort `setPlayerRank(systemOwner, OWNER, level)` â€” synthetic uuid can never be online, so `setOwner(Player)` is impossible. |

**For the port:** confirm the port's MineColonies has `setOwner(Player)` (it will). The offline/synthetic
limitation persists â€” if the port needs true offline-owner reassignment, it must find a MineColonies-supported
path (there isn't a clean public one as of 1.1.1237) or accept best-effort. `WarSystem.transferOwnership`
(1.20.1 ~L1274) already calls `setOwner(Player)` directly â€” that path is fine and is the reference pattern.


exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java -TotalCount 80' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 263ms:
package net.machiavelli.minecolonytax.abandon;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages automatic colony abandonment based on owner/officer inactivity.
 * This system tracks when owners and officers last visited their colonies
 * and automatically abandons colonies that have been inactive for too long.
 */
public class ColonyAbandonmentManager {

    private static final Logger LOGGER = LogManager.getLogger(ColonyAbandonmentManager.class);

    // AUDIT FIX (defensive_04 M2 / Codex MED-11): persist abandoned-state across restarts.
    // Without this, server restart wipes abandoned-flag and former-member tracking; reclaim
    // bypass for former owners stops working and isColonyAbandoned() returns false until
    // the periodic abandonment scan re-derives state.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/abandonment.json";
    /**
     * Lazy one-shot load. We don't have a hook in MineColonyTax for this manager (it has no
     * initialize() in the lifecycle wiring), so we trigger load on first read/write of any
     * persisted collection. Subsequent calls are no-ops.
     */
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private static final Map<Integer, Long> warnedColonies = new ConcurrentHashMap<>();
    private static final Set<Integer> abandonedColonies = ConcurrentHashMap.newKeySet();
    // Former owners/officers are tracked so they can bypass claiming requirements for their own colony.
    private static final Map<Integer, Set<UUID>> formerColonyMembers = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Component>> pendingNotifications = new ConcurrentHashMap<>();

    /** Serializable snapshot of the persisted state. */
    private static class AbandonmentSaveData {
        Set<Integer> abandonedColonies = new HashSet<>();
        Map<Integer, Set<UUID>> formerColonyMembers = new HashMap<>();
    }

    /** Load abandonment state from disk. Idempotent â€” safe to call multiple times. */
    public static void loadData() {
        if (!LOADED.compareAndSet(false, true)) return;
        try {
            Path path = Path.of(STORAGE_FILE);
            if (!Files.exists(path)) return;
            try (FileReader r = new FileReader(path.toFile())) {
                AbandonmentSaveData data = GSON.fromJson(r, AbandonmentSaveData.class);
                if (data != null) {
                    if (data.abandonedColonies != null) {
                        abandonedColonies.addAll(data.abandonedColonies);
                    }
                    if (data.formerColonyMembers != null) {
                        for (Map.Entry<Integer, Set<UUID>> e : data.formerColonyMembers.entrySet()) {
                            if (e.getKey() != null && e.getValue() != null) {
                                formerColonyMembers.put(e.getKey(), ConcurrentHashMap.newKeySet());
                                formerColonyMembers.get(e.getKey()).addAll(e.getValue());

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/economy/WarExhaustionManager.java -Pattern 'class ExhaustionSaveData|Map<|ConcurrentHashMap' -Context 0,3" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 325ms:

> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:23:import 
java.util.concurrent.ConcurrentHashMap;
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:24:
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:25:/**
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:26: * Manages War Exhaustion and War 
Reparations for colonies.
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:47:    private static final 
Map<Integer, Long> COLONIES_AT_WAR = new ConcurrentHashMap<>();
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:48:
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:49:    /**
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:50:     * Tracks colonies in post-war 
recovery (colonyId -> war end time)
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:52:    private static final 
Map<Integer, Long> RECOVERY_STATUS = new ConcurrentHashMap<>();
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:53:
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:54:    /**
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:55:     * Tracks war losses for 
reparations (colonyId -> list of loss timestamps)
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:57:    private static final 
Map<Integer, List<Long>> WAR_LOSSES = new ConcurrentHashMap<>();
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:58:
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:59:    /**
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:60:     * Tracks colonies under 
reparations (colonyId -> expiry time)
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:62:    private static final 
Map<Integer, Long> REPARATIONS = new ConcurrentHashMap<>();
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:63:
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:64:    /**
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:65:     * Tracks colonies with war 
immunity (colonyId -> immunity expiry time).
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:69:    private static final 
Map<Integer, Long> WAR_IMMUNITY = new ConcurrentHashMap<>();
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:70:
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:71:    private static MinecraftServer 
SERVER;
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:72:
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:436:        data.coloniesAtWar = new 
java.util.HashMap<>(COLONIES_AT_WAR);
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:437:        data.recoveryStatus = new 
java.util.HashMap<>(RECOVERY_STATUS);
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:438:        // warLosses values are 
mutable Lists — deep-copy so the async writer never
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:439:        // serializes a list while 
the main thread mutates it.
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:440:        Map<Integer, List<Long>> 
lossesCopy = new java.util.HashMap<>();
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:441:        for (Map.Entry<Integer, 
List<Long>> e : WAR_LOSSES.entrySet()) {
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:442:            
lossesCopy.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:443:        }
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:445:        data.reparations = new 
java.util.HashMap<>(REPARATIONS);
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:446:        data.warImmunity = new 
java.util.HashMap<>(WAR_IMMUNITY);
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:447:
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:448:        
net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("war_exhaustion", () -> {
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:449:            try {
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:464:    private static class 
ExhaustionSaveData {
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:465:        Map<Integer, Long> 
coloniesAtWar;
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:466:        Map<Integer, Long> 
recoveryStatus;
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:467:        Map<Integer, List<Long>> 
warLosses;
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:468:        Map<Integer, Long> 
reparations;
> src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:469:        Map<Integer, Long> 
warImmunity;
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:470:    }
  src\main\java\net\machiavelli\minecolonytax\economy\WarExhaustionManager.java:471:}



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java -TotalCount 80' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 276ms:
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
// Intentionally do NOT import com.minecolonies.core.entity.mobs.EntityMercenary â€”
// it lives in the INTERNAL core package, not the api/* surface. We work through the
// API-typed EntityType<? extends PathfinderMob> ModEntities.MERCENARY and operate on
// the result as a Mob, so a future class move/rename cannot brick this manager.
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
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
 * Manages the besiege system â€” single-player raids on active non-primary colonies
 * that grant tax vassalage on victory, and the reclaim flow for the former owner.
 */
public class BesiegeManager {

    private static final Logger LOGGER = LogManager.getLogger(BesiegeManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/besieged_colonies.json";

    /**
     * Active besiege raids, keyed by besieger UUID. Each besieger has at most one
     * active raid at a time (solo combat rule). Multiple besiegers may concurrently
     * raid the same colony â€” look them up by colony with {@link #getRaidsForColony(int)}.
     *
     * Phase 2 follow-up: defender pool is still per-raid, not shared across
     * concurrent besiegers on the same colony. Each besieger currently spawns its
     * own mercs and tracks its own hostile-citizen set; last-kill-credit semantics
     * are not yet implemented.
     */
    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();

    /**
     * Secondary index: colonyId â†’ set of besiegerUUIDs currently raiding it.
     * Maintained in lock-step with ACTIVE_RAIDS so hot-path lookups
     * (isActiveRaidOnColony, getRaidsForColony, container deny checks)
     * are O(1) instead of O(activeRaids).

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/mixin/WorldTickHandlerMixin.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 313ms:
package net.machiavelli.minecolonytax.mixin;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.besiege.BesiegeManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the harmonised/Explosion't restoration system war-and-raid-aware.
 *
 * Explosion't's {@code WorldTickHandler.handleLevelTick} decrements every
 * pending block's {@code ticksLeft} every tick and heals when it hits zero,
 * with no native concept of wars or raids. Without intervention, blocks blown
 * up during a siege would heal mid-fight (e.g. a 30-second heal delay
 * restores your wall in the middle of the assault).
 *
 * This mixin injects at HEAD of that tick handler and CANCELS the tick when
 * ANY of the following is active in this level:
 *   - WarSystem.ACTIVE_WARS â€” full war (defender or attacker colony in level)
 *   - RaidManager.getActiveRaids() â€” MineColonies raid on a colony in level
 *   - BesiegeManager â€” solo besiege on a colony in level
 *
 * Side-effect analysis of HEAD cancel (per inspection of explosiont
 * 1.20.1-2.4.8 source): the upstream method does idempotent
 * {@code computeIfAbsent} initialization for {@code dimForceHeal},
 * {@code dimWasDay}, and {@code ChunkDataHandler.toHealDimMap}, then loops
 * the heal map, then calls {@code dimWasDay.replace(...)} at the end. We
 * skip all of those; the initializations will recreate-as-needed on the
 * next non-cancelled tick (computeIfAbsent), and the {@code dimWasDay}
 * staleness is only consulted inside the loop we're already skipping. No
 * persistent side effects are lost.
 *
 * Optional-mod safety:
 *   - {@code @Pseudo} + {@code @Mixin(targets = "...")} + {@code remap=false}
 *     means the mixin processor does NOT require the target class to exist
 *     at compile time and silently no-ops if Explosion't isn't installed.
 *   - mixins.json sets {@code required: false} + {@code defaultRequire: 0}
 *     so the whole config gracefully no-ops absent the target.
 *
 * Coarse-grained on purpose: pausing the entire level tick is simpler and
 * race-free vs. a per-block @ModifyVariable hack. The cost is that non-war
 * explosion damage in the same level also waits for war/raid-end, which is
 * acceptable behavior for a siege SMP.
 */
@Pseudo
@Mixin(targets = "harmonised.explosiont.events.WorldTickHandler", remap = false)
public abstract class WorldTickHandlerMixin {

    @Inject(method = "handleLevelTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void warntax$pauseDuringActiveConflict(TickEvent.LevelTickEvent event, CallbackInfo ci) {
        if (event == null || event.level == null || event.level.isClientSide()) return;

        // Fast exits when nothing is active.
        boolean anyWar = !WarSystem.ACTIVE_WARS.isEmpty();
        boolean anyRaid = !RaidManager.getActiveRaids().isEmpty();
        boolean anyBesiege = !BesiegeManager.getAllActiveRaidsByBesieger().isEmpty();
        if (!anyWar && !anyRaid && !anyBesiege) return;

        Level eventLevel = event.level;

        if (anyWar) {
            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
                IColony defender = war.getColony();
                if (defender != null && defender.getWorld() == eventLevel) { ci.cancel(); return; }
                IColony attacker = war.getAttackerColony();
                if (attacker != null && attacker.getWorld() == eventLevel) { ci.cancel(); return; }
            }
        }

        if (anyRaid) {
            for (ActiveRaidData raid : RaidManager.getActiveRaids().values()) {
                IColony c = raid.getColony();
                if (c != null && c.getWorld() == eventLevel) { ci.cancel(); return; }
            }
        }

        if (anyBesiege) {
            net.minecraft.resources.ResourceKey<Level> dimKey = eventLevel.dimension();
            for (BesiegeManager.BesiegeRaidData raid : BesiegeManager.getAllActiveRaidsByBesieger().values()) {
                // Fast path: compare cached dimension in O(1) (audit H12).
                if (raid.dimension != null) {
                    if (raid.dimension.equals(dimKey)) { ci.cancel(); return; }
                    continue;
                }
                // Fallback for a raid with no cached dimension (e.g. restored): resolve by id.
                IColony c = com.minecolonies.api.IMinecoloniesAPI.getInstance().getColonyManager()
                        .getAllColonies().stream()
                        .filter(col -> col.getID() == raid.colonyId)
                        .findFirst().orElse(null);
                if (c != null && c.getWorld() == eventLevel) { ci.cancel(); return; }
            }
        }
    }
}

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java -Pattern 'save|load|GSON|fromJson|toJson|BesiegeRaidData' -Context 2,4 | Select-Object -First 80" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 499ms:

  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1:package 
net.machiavelli.minecolonytax.besiege;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:2:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:3:import com.google.gson.Gson;
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:4:import com.google.gson.GsonBuilder;
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:5:import com.google.gson.reflect.TypeToken;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:6:import 
com.minecolonies.api.IMinecoloniesAPI;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:7:import 
com.minecolonies.api.colony.ICitizenData;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:8:import com.minecolonies.api.colony.IColony;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:9:import 
com.minecolonies.api.colony.IColonyManager;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:59:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:60:    private static final Logger LOGGER = 
LogManager.getLogger(BesiegeManager.class);
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:61:    private static final Gson GSON = new 
GsonBuilder().setPrettyPrinting().create();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:62:    private static final String 
STORAGE_FILE = "config/warntax/besieged_colonies.json";
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:63:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:64:    /**
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:65:     * Active besiege raids, keyed by 
besieger UUID. Each besieger has at most one
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:72:     * are not yet implemented.
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:73:     */
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:74:    private static final Map<UUID, 
BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:75:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:76:    /**
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:77:     * Secondary index: colonyId → set of 
besiegerUUIDs currently raiding it.
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:78:     * Maintained in lock-step with 
ACTIVE_RAIDS so hot-path lookups
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:92:    public static void 
initialize(MinecraftServer server) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:93:        SERVER = server;
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:94:        loadData(server);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:95:        if (TaxConfig.isNormalLogging()) 
LOGGER.info("BesiegeManager initialized");
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:96:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:97:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:98:    public static void shutdown() {
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:99:        saveData();
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:100:        for (BesiegeRaidData raid : 
ACTIVE_RAIDS.values()) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:101:            cleanupRaid(raid, false);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:102:        }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:103:        ACTIVE_RAIDS.clear();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:104:        COLONY_RAID_INDEX.clear();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:109:        if (ACTIVE_RAIDS.isEmpty()) 
return;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:110:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:111:        for (Iterator<Map.Entry<UUID, 
BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:112:            Map.Entry<UUID, 
BesiegeRaidData> entry = it.next();
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:113:            BesiegeRaidData raid = 
entry.getValue();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:114:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:115:            try {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:116:                IColony colony = 
getColonyById(raid.colonyId);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:117:                if (colony == null) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:172:                    // shared defender 
pool. Otherwise the ordering of cleanup calls
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:173:                    // controls which 
one sees siblings.size() == 1 and that's fragile.
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:174:                    
java.util.List<BesiegeRaidData> raceLosers = new java.util.ArrayList<>();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:175:                    for (UUID otherUUID 
: new java.util.HashSet<>(
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:176:                            
COLONY_RAID_INDEX.getOrDefault(raid.colonyId, java.util.Collections.emptySet()))) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:177:                        if 
(otherUUID.equals(raid.besiegingPlayerUUID)) continue;
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:178:                        BesiegeRaidData 
other = ACTIVE_RAIDS.get(otherUUID);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:179:                        if (other != 
null) raceLosers.add(other);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:180:                    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:181:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:182:                    // Drop ALL 
concurrent raids on this colony from the index first.
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:190:                    it.remove();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:191:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:192:                    for (BesiegeRaidData 
loser : raceLosers) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:193:                        try {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:194:                            // Worded 
honestly — codex wave-15 finding #3: not a true
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:195:                            // "killing 
blow" claim, since the resolver picked whichever
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:196:                            // raid the 
tick happened to process first.
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:366:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:367:        try {
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:368:            BesiegeRaidData raid = new 
BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:369:            try { raid.dimension = 
colony.getDimension(); } catch (Exception ignored) { /* fallback handled in mixin */ }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:370:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:371:            // Step 3 Phase 2 — 
multi-besieger shared defender pool.
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:372:            // Find any already-active 
raid on this colony BEFORE inserting our own.
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:380:            // so that a concurrent 
tick/reader can never see the new raid with
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:381:            // empty private sets and 
act on it as if it had no defenders.
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:382:            List<BesiegeRaidData> 
existingForColony = getRaidsForColony(colonyId);
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:383:            BesiegeRaidData primary = 
existingForColony.isEmpty() ? null : existingForColony.get(0);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:384:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:385:            if (primary != null) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:386:                raid.isSecondaryRaider = 
true;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:387:                raid.hostileCitizenIds = 
primary.hostileCitizenIds;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:492:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:493:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:494:    private static int 
makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:495:        int count = 0;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:496:        for (ICitizenData citizenData : 
colony.getCitizenManager().getCitizens()) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:497:            if (citizenData.getJob() == 
null || !citizenData.getJob().isGuard()) continue;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:498:            
Optional<AbstractEntityCitizen> entityOpt = citizenData.getEntity();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:512:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:513:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:514:    private static int 
convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:515:        List<ICitizenData> eligible = 
new ArrayList<>();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:516:        for (ICitizenData citizen : 
colony.getCitizenManager().getCitizens()) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:517:            if (citizen.isChild()) 
continue;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:518:            if 
(citizen.getEntity().isEmpty()) continue;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:549:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:550:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:551:    private static void 
applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:552:        
entity.goalSelector.removeAllGoals(g -> true);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:553:        
entity.targetSelector.removeAllGoals(g -> true);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:554:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:555:        // Use MilitiaAttackGoal instead 
of vanilla MeleeAttackGoal — non-guard citizens
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:583:     * Each 20% damage reduction from 
the investment adds +1 resistance amplifier.
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:584:     */
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:585:    private static void 
applyFortificationBonus(IColony colony, BesiegeRaidData raid) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:586:        if 
(!TaxConfig.isUpgradesEnabled()) return;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:587:        double dmgReduction = 
net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:588:                
.getFortificationDamageReduction(colony.getID());
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:589:        if (dmgReduction <= 0) return;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:612:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:613:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:614:    private static int 
spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:615:        Level world = colony.getWorld();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:616:        if (!(world instanceof 
ServerLevel)) return 0;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:617:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:618:        int buildingCount = 0;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:665:     */
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:666:    private static int 
spawnMilitiaUpgradeReinforcements(IColony colony, ServerPlayer besieger,
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:667:            BesiegeRaidData raid, int 
guardCount) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:668:        return 
net.machiavelli.minecolonytax.militia.MilitiaSpawner.spawnReinforcements(
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:669:                colony, guardCount, 
besieger, raid.militiaSupport,
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:670:                
TaxConfig.getBesiegeDurationMinutes());
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:671:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:672:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:673:    private static void 
completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:674:        cleanupRaid(raid, true);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:675:        
applyCooldown(raid.besiegingPlayerUUID);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:676:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:677:        if (attackerWon) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:698:     * that credited amount, so coins 
are never lost to the cap.
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:699:     */
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:700:    private static void 
applySiegeSpoils(BesiegeRaidData raid, IColony defenderColony, boolean attackerWon) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:701:        int percent = 
TaxConfig.getBesiegeSpoilPercentOfLoserTreasury();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:702:        if (percent <= 0) return;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:703:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:704:        IColony besiegerColony = 
getPrimaryColonyOfPlayer(raid.besiegingPlayerUUID);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:743:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:744:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:745:    private static void 
completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:746:        // Counter-besiege reclaim 
handoff: if the besieger is the original owner
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:747:        // of a TAX_ONLY war-occupation 
on this colony, clear that occupation
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:748:        // and SHORT-CIRCUIT — the 
colony is already theirs, vassalizing it to
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:749:        // themselves would create a 
self-vassal nonsense state.
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:781:                    
System.currentTimeMillis(), tributePct);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:782:            
OCCUPATIONS.put(colony.getID(), occ);
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:783:            saveData();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:784:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:785:            // Notify former owner
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:786:            if (ownerUUID != null) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:787:                String besiegerName = 
getPlayerName(raid.besiegingPlayerUUID);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:813:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:814:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:815:    private static void 
completeReclaim(BesiegeRaidData raid, IColony colony) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:816:        int colonyId = colony.getID();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:817:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:818:        // Remove vassalization
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:819:        
VassalManager.removeVassalRelation(colonyId);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:821:        // Remove occupation record
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:822:        OCCUPATIONS.remove(colonyId);
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:823:        saveData();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:824:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:825:        
sendToPlayer(raid.besiegingPlayerUUID, Component.literal(
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:826:                "Reclaim successful! " + 
colony.getName() + " is free from occupation.")
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:827:                
.withStyle(ChatFormatting.GREEN));
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:837:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:838:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:839:    private static void 
cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:840:        IColony colony = 
getColonyById(raid.colonyId);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:841:        if (colony != null) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:842:            // Revoke combat permissions 
from the besieger (and any allies)
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:843:            
revokeBesiegeCombatPermissions(colony, raid.besiegingPlayerUUID);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:854:            // Count siblings BEFORE 
removing this raid from the index. We're "the
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:855:            // last" if every entry in 
the index points back to this raid's besieger.
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:856:            Set<UUID> siblingsOnColony = 
COLONY_RAID_INDEX.getOrDefault(raid.colonyId,
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:857:                    
java.util.Collections.emptySet());
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:858:            boolean isLastRaidOnColony = 
siblingsOnColony.size() <= 1
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:859:                    && 
siblingsOnColony.contains(raid.besiegingPlayerUUID);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:860:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:861:            if (isLastRaidOnColony) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:862:                // Restore citizen AI
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:863:                for (int citizenId : 
raid.hostileCitizenIds) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:924:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:925:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:926:    private static void 
createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:927:        try {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:928:            int minutes = 
TaxConfig.getBesiegeDurationMinutes();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:929:            Component text = 
Component.literal(
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:930:                    
String.format("Besiege: %s | Defenders: %d | %02d:%02d",
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:954:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:955:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:956:    private static void 
updateBossBar(BesiegeRaidData raid, IColony colony) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:957:        if (raid.bossEvent == null) 
return;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:958:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:959:        try {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:960:            long remaining = Math.max(0, 
raid.endTime - System.currentTimeMillis());
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:994:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:995:    /** All currently active besiege 
raids targeting this colony. O(matches). */
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:996:    public static List<BesiegeRaidData> 
getRaidsForColony(int colonyId) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:997:        Set<UUID> besiegerUUIDs = 
COLONY_RAID_INDEX.get(colonyId);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:998:        if (besiegerUUIDs == null || 
besiegerUUIDs.isEmpty()) return java.util.Collections.emptyList();
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:999:        List<BesiegeRaidData> matches = 
new ArrayList<>(besiegerUUIDs.size());
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1000:        for (UUID uuid : besiegerUUIDs) 
{
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1001:            BesiegeRaidData raid = 
ACTIVE_RAIDS.get(uuid);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1002:            if (raid != null) 
matches.add(raid);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1003:        }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1004:        return matches;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1005:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1033:    public static void registerAlly(int 
colonyId, UUID allyUUID) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1034:        if 
(!TaxConfig.isBesiegeAlliesEnabled()) return;
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1035:        List<BesiegeRaidData> raids = 
getRaidsForColony(colonyId);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1036:        if (raids.isEmpty()) return;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1037:        IColony colony = 
getColonyById(colonyId);
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1038:        for (BesiegeRaidData raid : 
raids) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1039:            if 
(raid.alliedPlayers.add(allyUUID) && colony != null) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1040:                
grantBesiegeCombatPermissions(colony, allyUUID);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1041:            }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1042:        }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1062:     * raid should use {@link 
#getRaidForBesieger(UUID)}.
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1063:     */
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1064:    public static Map<Integer, 
BesiegeRaidData> getActiveRaids() {
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1065:        Map<Integer, BesiegeRaidData> 
view = new HashMap<>();
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1066:        for (BesiegeRaidData raid : 
ACTIVE_RAIDS.values()) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1067:            
view.putIfAbsent(raid.colonyId, raid);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1068:        }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1069:        return 
Collections.unmodifiableMap(view);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1070:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1080:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1081:    /** Direct lookup by besieger UUID. 
Null when this player has no active raid. */
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1082:    public static BesiegeRaidData 
getRaidForBesieger(UUID besiegerUUID) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1083:        return 
ACTIVE_RAIDS.get(besiegerUUID);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1084:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1085:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1086:    /** Read-only view of all active 
raids keyed by besieger UUID. */
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1087:    public static Map<UUID, 
BesiegeRaidData> getAllActiveRaidsByBesieger() {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1088:        return 
Collections.unmodifiableMap(ACTIVE_RAIDS);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1089:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1090:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1091:    public static Map<Integer, 
BesiegeOccupationData> getAllOccupations() {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1094:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1095:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1096:    private static boolean 
allDefendersDead(BesiegeRaidData raid, IColony colony) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1097:        return 
countAliveDefenders(raid, colony) == 0;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1098:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1099:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1100:    private static int 
countAliveDefenders(BesiegeRaidData raid, IColony colony) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1101:        int alive = 0;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1102:        // Citizens
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1103:        for (int citizenId : 
raid.hostileCitizenIds) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1104:            try {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1301:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1302:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1303:    private static void 
loadData(MinecraftServer server) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1304:        File f = new 
File(server.getServerDirectory(), STORAGE_FILE);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1305:        if (!f.exists()) return;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1306:        try (FileReader r = new 
FileReader(f)) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1307:            Type type = new 
TypeToken<List<BesiegeOccupationData>>() {}.getType();
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1308:            List<BesiegeOccupationData> 
list = GSON.fromJson(r, type);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1309:            if (list != null) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1310:                for 
(BesiegeOccupationData occ : list) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1311:                    
OCCUPATIONS.put(occ.colonyId, occ);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1312:                }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1313:            }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1314:            if 
(TaxConfig.isNormalLogging())
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1315:                LOGGER.info("Loaded {} 
besiege occupation record(s)", OCCUPATIONS.size());
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1316:        } catch (Exception e) {
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1317:            LOGGER.error("Failed to 
load besiege occupation data", e);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1318:        }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1319:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1320:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1321:    private static void saveData() {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1322:        if (SERVER == null) return;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1323:        // Snapshot on the calling 
(server) thread.
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1324:        final 
List<BesiegeOccupationData> list = new ArrayList<>(OCCUPATIONS.values());
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1325:        final File f = new 
File(SERVER.getServerDirectory(), STORAGE_FILE);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1326:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1327:        
net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("besiege", () -> {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1328:            try {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1329:                
f.getParentFile().mkdirs();
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1330:                try (FileWriter w = new 
FileWriter(f)) {
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1331:                    GSON.toJson(list, 
w);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1332:                }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1333:            } catch (Exception e) {
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1334:                LOGGER.error("Failed to 
save besiege occupation data", e);
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1335:            }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1336:        });
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1337:    }
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1338:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1339:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1340:    /** Transient raid state — not 
persisted. */
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1341:    public static class BesiegeRaidData 
{
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1342:        public final int colonyId;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1343:        public final UUID 
besiegingPlayerUUID;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1344:        public final long startTime;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1345:        public final long endTime;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1389:        public 
net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1390:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1391:        public BesiegeRaidData(int 
colonyId, UUID besiegingPlayerUUID, BlockPos colonyCenter, boolean isReclaim) {
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1392:            this.colonyId = colonyId;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1393:            this.besiegingPlayerUUID = 
besiegingPlayerUUID;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1394:            this.colonyCenter = 
colonyCenter;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1395:            this.isReclaim = isReclaim;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1408:        public int tributePercent;
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1409:
> src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1410:        // For Gson deserialization
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1411:        public BesiegeOccupationData() 
{}
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1412:
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1413:        public 
BesiegeOccupationData(int colonyId, String colonyName, UUID besiegingPlayerUUID,
  src\main\java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:1414:                UUID formerOwnerUUID, 
long besiegeTime, int tributePercent) {



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java | Select-Object -Index (250..590)' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 374ms:
                        try {
                            legacyGetMoney = legacyClass.getMethod(methodName, paramType);
                            LOGGER.debug("âœ“ Found legacy getMoney: {}({})", methodName, paramType.getSimpleName());
                            break;
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if (legacyGetMoney != null)
                        break;
                }

                // Try to find setMoney method
                String[] setMoneyNames = { "setMoney", "setBalance", "setPlayerMoney", "setPlayerBalance" };
                Class<?>[][] setParamTypes = {
                        { Player.class, long.class }, { Player.class, double.class }, { Player.class, int.class },
                        { ServerPlayer.class, long.class }, { ServerPlayer.class, double.class },
                        { ServerPlayer.class, int.class },
                        { java.util.UUID.class, long.class }, { java.util.UUID.class, double.class },
                        { String.class, long.class }, { String.class, double.class }
                };

                for (String methodName : setMoneyNames) {
                    for (Class<?>[] params : setParamTypes) {
                        try {
                            legacySetMoney = legacyClass.getMethod(methodName, params);
                            LOGGER.debug("âœ“ Found legacy setMoney: {}({}, {})", methodName,
                                    params[0].getSimpleName(), params[1].getSimpleName());
                            break;
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if (legacySetMoney != null)
                        break;
                }

                if (legacyGetMoney != null && legacySetMoney != null) {
                    LOGGER.info("âœ“ Successfully initialized legacy API from {}", className);
                    return true;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }

        LOGGER.debug("Legacy API not available");
        return false;
    }

    /**
     * Refresh the SERVER instance if it was null during initialization
     */
    private static void refreshServerInstance() {
        if (mode == IntegrationMode.CURRENCY_DATA && currencyPlayerDataServer == null) {
            try {
                Field serverField = currencyPlayerDataClass.getField("SERVER");
                currencyPlayerDataServer = serverField.get(null);
            } catch (Exception e) {
                LOGGER.debug("Could not refresh SERVER instance: {}", e.getMessage());
            }
        }
    }

    /**
     * Check if SDMShop/SDM-Economy integration is available
     */
    public static boolean isAvailable() {
        if (!modPresent) return false;
        refreshServerInstance();
        return mode != IntegrationMode.NONE &&
                (mode != IntegrationMode.CURRENCY_DATA || currencyPlayerDataServer != null);
    }

    /**
     * Get money from a player using SDMShop/SDM-Economy API
     */
    public static long getMoney(ServerPlayer player) {
        if (!modPresent) return 0;
        if (player == null) {
            LOGGER.debug("Player is null");
            return 0;
        }

        refreshServerInstance();

        try {
            switch (mode) {
                case SHOP_UTILS:
                    return getMoneyViaShopUtils(player);
                case CURRENCY_DATA:
                    return getMoneyViaCurrencyData(player);
                case LEGACY:
                    return getMoneyViaLegacy(player);
                default:
                    return 0;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to get money for player {}: {}", player.getName().getString(), e.getMessage());
            return 0;
        }
    }

    private static long getMoneyViaShopUtils(ServerPlayer player) throws Exception {
        Object result = shopUtilsGetMoney.invoke(null, player);
        return Math.round((Double) result);
    }

    private static long getMoneyViaCurrencyData(ServerPlayer player) throws Exception {
        if (currencyPlayerDataServer == null) {
            LOGGER.debug("CurrencyPlayerData.SERVER is null");
            return 0;
        }

        // Returns ErrorCodeStruct<Double>
        Object result = currencyDataGetBalance.invoke(currencyPlayerDataServer, player, currencyName());

        // Get the 'value' field from ErrorCodeStruct
        Field valueField = result.getClass().getField("value");
        Object value = valueField.get(result);

        if (value instanceof Double) {
            return Math.round((Double) value);
        }
        return 0;
    }

    private static long getMoneyViaLegacy(ServerPlayer player) throws Exception {
        Class<?> paramType = legacyGetMoney.getParameterTypes()[0];
        Object param;

        if (paramType == Player.class || paramType == ServerPlayer.class) {
            param = player;
        } else if (paramType == java.util.UUID.class) {
            param = player.getUUID();
        } else if (paramType == String.class) {
            param = player.getName().getString();
        } else {
            return 0;
        }

        Object result = legacyGetMoney.invoke(null, param);

        if (result instanceof Long)
            return (Long) result;
        if (result instanceof Double)
            return Math.round((Double) result);
        if (result instanceof Integer)
            return (Integer) result;
        return 0;
    }

    /**
     * Set money for a player using SDMShop/SDM-Economy API
     */
    public static boolean setMoney(ServerPlayer player, long amount) {
        if (!modPresent) return false;
        if (player == null) {
            LOGGER.debug("Player is null");
            return false;
        }

        refreshServerInstance();

        try {
            boolean success;
            switch (mode) {
                case SHOP_UTILS:
                    success = setMoneyViaShopUtils(player, amount);
                    break;
                case CURRENCY_DATA:
                    success = setMoneyViaCurrencyData(player, amount);
                    break;
                case LEGACY:
                    success = setMoneyViaLegacy(player, amount);
                    break;
                default:
                    return false;
            }

            if (success) {
                LOGGER.debug("Successfully set money for {}: {}", player.getName().getString(), amount);
            }
            return success;
        } catch (Exception e) {
            LOGGER.error("Failed to set money for player {} to {}: {}",
                    player.getName().getString(), amount, e.getMessage());
            return false;
        }
    }

    private static boolean setMoneyViaShopUtils(ServerPlayer player, long amount) throws Exception {
        Object result = shopUtilsSetMoney.invoke(null, player, (double) amount);
        return result instanceof Boolean && (Boolean) result;
    }

    private static boolean setMoneyViaCurrencyData(ServerPlayer player, long amount) throws Exception {
        if (currencyPlayerDataServer == null) {
            return false;
        }

        // Returns ErrorCodes enum
        Object result = currencyDataSetValue.invoke(currencyPlayerDataServer, player, currencyName(),
                (double) amount);

        // Check if result is SUCCESS
        boolean success = result.toString().equals("SUCCESS");

        // Sync player if available
        if (success && syncPlayerMethod != null) {
            try {
                syncPlayerMethod.invoke(null, player);
            } catch (Exception e) {
                LOGGER.debug("Could not sync player: {}", e.getMessage());
            }
        }

        return success;
    }

    private static boolean setMoneyViaLegacy(ServerPlayer player, long amount) throws Exception {
        Class<?>[] paramTypes = legacySetMoney.getParameterTypes();
        Object firstParam;
        Object secondParam;

        if (paramTypes[0] == Player.class || paramTypes[0] == ServerPlayer.class) {
            firstParam = player;
        } else if (paramTypes[0] == java.util.UUID.class) {
            firstParam = player.getUUID();
        } else if (paramTypes[0] == String.class) {
            firstParam = player.getName().getString();
        } else {
            return false;
        }

        if (paramTypes[1] == long.class || paramTypes[1] == Long.class) {
            secondParam = amount;
        } else if (paramTypes[1] == double.class || paramTypes[1] == Double.class) {
            secondParam = (double) amount;
        } else if (paramTypes[1] == int.class || paramTypes[1] == Integer.class) {
            secondParam = (int) amount;
        } else {
            return false;
        }

        legacySetMoney.invoke(null, firstParam, secondParam);
        return true;
    }

    /**
     * Add money to a player using SDMShop/SDM-Economy API
     */
    public static boolean addMoney(ServerPlayer player, long amount) {
        if (!modPresent) return false;
        if (player == null) {
            return false;
        }

        refreshServerInstance();

        try {
            // Try direct addMoney if available (more efficient)
            if (mode == IntegrationMode.SHOP_UTILS && shopUtilsAddMoney != null) {
                Object result = shopUtilsAddMoney.invoke(null, player, (double) amount);
                return result instanceof Boolean && (Boolean) result;
            }

            if (mode == IntegrationMode.CURRENCY_DATA && currencyPlayerDataServer != null
                    && currencyDataAddValue != null) {
                Object result = currencyDataAddValue.invoke(currencyPlayerDataServer, player, currencyName(),
                        (double) amount);
                boolean success = result.toString().equals("SUCCESS");

                if (success && syncPlayerMethod != null) {
                    try {
                        syncPlayerMethod.invoke(null, player);
                    } catch (Exception e) {
                        LOGGER.debug("Could not sync player: {}", e.getMessage());
                    }
                }
                return success;
            }

            // Fallback: get current balance and set new value
            long currentBalance = getMoney(player);
            return setMoney(player, currentBalance + amount);
        } catch (Exception e) {
            LOGGER.error("Failed to add money {} to player {}: {}",
                    amount, player.getName().getString(), e.getMessage());
            return false;
        }
    }

    /**
     * Remove money from a player using SDMShop/SDM-Economy API
     */
    public static boolean removeMoney(ServerPlayer player, long amount) {
        if (player == null) {
            return false;
        }

        try {
            long currentBalance = getMoney(player);
            if (currentBalance < amount) {
                return false; // Not enough money
            }
            return setMoney(player, currentBalance - amount);
        } catch (Exception e) {
            LOGGER.error("Failed to remove money {} from player {}: {}",
                    amount, player.getName().getString(), e.getMessage());
            return false;
        }
    }

    /**
     * Deduct player balance and return the amount actually deducted
     * Returns 0 if player doesn't have enough funds or if SDMShop is not available
     */
    public static int deductPlayerBalance(ServerPlayer player, int amount) {
        if (player == null || amount <= 0 || !isAvailable()) {
            return 0;
        }

        long currentBalance = getMoney(player);
        if (currentBalance < amount) {
            return 0; // Not enough funds
        }

        if (removeMoney(player, amount)) {
            return amount;
        }

        return 0;
    }

    /**
     * Get the current integration mode (for debugging)
     */
    public static String getIntegrationMode() {
        return mode.toString();
    }

    /** True if the SDMShop/SDM-Economy mod was detected as loaded at init. */
    public static boolean isModPresent() {

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/network/packets/ClaimTaxPacket.java -TotalCount 60' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 312ms:
package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Rank;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.machiavelli.minecolonytax.permissions.TaxPermissionManager;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * Packet sent from client to server to claim tax from a colony
 */
public class ClaimTaxPacket {
    private final int colonyId;
    private final int amount; // -1 for all
    
    public ClaimTaxPacket(int colonyId, int amount) {
        this.colonyId = colonyId;
        this.amount = amount;
    }
    
    public ClaimTaxPacket(FriendlyByteBuf buf) {
        this.colonyId = buf.readInt();
        int rawAmount = buf.readInt();
        // SECURITY: reject malicious/negative amounts. Only -1 (claim all), -2 (vassal claim), and strictly positive
        // values are valid. Any other negative value (including Integer.MIN_VALUE) is clamped to 0 so the handler
        // returns a no-op instead of letting TaxManager.claimTax compute storedTax - (negative) and inflate the
        // ledger. See audit/CODEX_INDEPENDENT.md CRIT-1.
        if (rawAmount != -1 && rawAmount != -2 && rawAmount <= 0) {
            rawAmount = 0;
        }
        this.amount = rawAmount;
    }
    
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(colonyId);
        buf.writeInt(amount);
    }
    
    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;


exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java -Pattern 'handleEmergencyFix|handleFixNullOwners|handleCleanupAbandonedEntries' -Context 0,60" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 507ms:

> src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:428:                                           
                     .executes(WntCommands::handleEmergencyFix))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:429:                                           
     .then(Commands.literal("fixnullowners")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:430:                                           
                     .requires(source -> source.hasPermission(2))
> src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:431:                                           
                     .executes(WntCommands::handleFixNullOwners))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:432:                                           
     .then(Commands.literal("cleanupabandonedentries")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:433:                                           
                     .requires(source -> source.hasPermission(2))
> src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:434:                                           
                     .executes(WntCommands::handleCleanupAbandonedEntries))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:435:                                           
     .then(Commands.literal("forcecleanupcolony")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:436:                                           
                     .requires(source -> source.hasPermission(2))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:437:                                           
                     .then(Commands.argument("colony", StringArgumentType.string())
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:438:                                           
                                     .suggests(COLONY_SUGGESTIONS)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:439:                                           
                                     .executes(WntCommands::handleForceCleanupColony)))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:440:                                           
     .then(Commands.literal("bossbar")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:441:                                           
                     .requires(source -> source.hasPermission(2))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:442:                                           
                     .then(Commands.argument("colony", StringArgumentType.string())
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:443:                                           
                                     .suggests(COLONY_SUGGESTIONS)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:444:                                           
                                     .executes(WntCommands::handleDebugBossBar)))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:445:                                           
     .then(Commands.literal("claimraidstatus")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:446:                                           
                     .requires(source -> source.hasPermission(2))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:447:                                           
                     .then(Commands.argument("colony", StringArgumentType.string())
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:448:                                           
                                     .suggests(ABANDONED_COLONY_SUGGESTIONS)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:449:                                           
                                     .executes(WntCommands::checkClaimingRaidStatus)))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:450:                                           
     .then(Commands.literal("war")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:451:                                           
                     .requires(src -> src.hasPermission(2))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:452:                                           
                     .executes(WntCommands::debugWarCommand))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:453:                                           
     .then(Commands.literal("guards")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:454:                                           
                     .requires(src -> src.hasPermission(2))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:455:                                           
                     .executes(WntCommands::debugGuardCounts)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:456:                                           
                     .then(Commands.argument("colony",
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:457:                                           
                                     StringArgumentType.greedyString())
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:458:                                           
                                     .suggests(COLONY_SUGGESTIONS)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:459:                                           
                                     .executes(WntCommands::debugGuardCountsForColony)))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:460:                                           
     .then(Commands.literal("tax")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:461:                                           
                     .executes(ctx -> DebugTaxCommand.execute(ctx, null))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:462:                                           
                     .then(Commands.argument("colony",
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:463:                                           
                                     StringArgumentType.string())
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:464:                                           
                                     .suggests(COLONY_SUGGESTIONS)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:465:                                           
                                     .executes(ctx -> DebugTaxCommand.execute(
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:466:                                           
                                                     ctx,
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:467:                                           
                                                     extractColonyName(StringArgumentType
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:468:                                           
                                                                     .getString(ctx, "colony"))))))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:469:                                           
     .then(Commands.literal("officertracking")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:470:                                           
                     .executes(OfficerTrackingDebugCommand::checkCurrentColony)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:471:                                           
                     .then(Commands.argument("colonyId",
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:472:                                           
                                     IntegerArgumentType.integer())
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:473:                                           
                                     .executes(OfficerTrackingDebugCommand::checkSpecificColony)))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:474:                                           
     .then(Commands.literal("besiege")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:475:                                           
                     .executes(WntCommands::handleBesiegeStatus))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:476:                                           
     .then(Commands.literal("entityraid")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:477:                                           
                     .requires(src -> src.hasPermission(2))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:478:                                           
                     .then(Commands.literal("status")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:479:                                           
                                     .executes(ctx -> EntityRaidCommands
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:480:                                           
                                                     .showEntityRaidStatus(ctx)))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:481:                                           
                     .then(Commands.literal("config")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:482:                                           
                                     .executes(ctx -> EntityRaidCommands
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:483:                                           
                                                     .showEntityRaidConfig(ctx)))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:484:                                           
                     .then(Commands.literal("end")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:485:                                           
                                     .then(Commands.argument("colonyId",
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:486:                                           
                                                     IntegerArgumentType.integer())
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:487:                                           
                                                     .executes(ctx -> EntityRaidCommands
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:488:                                           
                                                                     .endEntityRaid(ctx))))
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:489:                                           
                     .then(Commands.literal("test")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:490:                                           
                                     .then(Commands.argument("colonyName",
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:491:                                           
                                                     StringArgumentType.string())
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:492:                                           
                                                     .suggests(COLONY_SUGGESTIONS)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:493:                                           
                                                     .executes(ctx -> EntityRaidCommands
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:494:                                           
                                                                     .testEntityRaid(ctx))))
> src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3222:        private static int 
handleCleanupAbandonedEntries(CommandContext<CommandSourceStack> context)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3223:                        throws 
CommandSyntaxException {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3224:                CommandSourceStack source 
= context.getSource();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3225:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3226:                source.sendSuccess(() -> 
Component
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3227:                                
.literal("Starting cleanup of [abandoned] entries across all colonies...")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3228:                                
.withStyle(ChatFormatting.YELLOW), false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3229:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3230:                try {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3231:                        // Run the cleanup
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3232:                        
net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3233:                                        
.cleanupAllColoniesAbandonedEntries();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3234:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3235:                        
source.sendSuccess(() -> Component
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3236:                                        
.literal("Cleanup completed successfully! Check server logs for details.")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3237:                                        
.withStyle(ChatFormatting.GREEN), false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3238:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3239:                } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3240:                        
source.sendFailure(Component.literal("Cleanup failed: " + e.getMessage())
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3241:                                        
.withStyle(ChatFormatting.RED));
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3242:                        return 0;
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3243:                }
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3244:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3245:                return 1;
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3246:        }
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3247:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3248:        /**
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3249:         * Handle debug boss bar command.
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3250:         */
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3251:        private static int 
handleDebugBossBar(CommandContext<CommandSourceStack> context)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3252:                        throws 
CommandSyntaxException {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3253:                CommandSourceStack source 
= context.getSource();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3254:                String colonyName = 
extractColonyName(StringArgumentType.getString(context, "colony"));
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3255:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3256:                // Find the target colony
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3257:                Level level = 
source.getLevel();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3258:                IColony targetColony = 
WarSystem.findColonyByName(colonyName, level);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3259:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3260:                if (targetColony == null) 
{
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3261:                        
source.sendFailure(Component.literal("Colony '" + colonyName + "' not found!")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3262:                                        
.withStyle(ChatFormatting.RED));
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3263:                        return 0;
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3264:                }
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3265:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3266:                // Debug claiming raid 
status
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3267:                
net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.debugClaimingRaid(targetColony.getID());
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3268:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3269:                // Try to force refresh 
boss bar if there's an active raid
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3270:                if 
(net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3271:                                
.isColonyUnderClaimingRaid(targetColony.getID())) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3272:                        
net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3273:                                        
.forceRefreshBossBar(targetColony.getID());
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3274:                        
source.sendSuccess(() -> Component
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3275:                                        
.literal("Forced boss bar refresh for colony '" + colonyName
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3276:                                          
              + "'. Check server logs for debug info.")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3277:                                        
.withStyle(ChatFormatting.GREEN), false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3278:                } else {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3279:                        
source.sendSuccess(() -> Component
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3280:                                        
.literal("No active claiming raid for colony '" + colonyName
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3281:                                          
              + "'. Debug info logged.")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3282:                                        
.withStyle(ChatFormatting.YELLOW), false);
> src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3351:        private static int 
handleEmergencyFix(CommandContext<CommandSourceStack> context)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3352:                        throws 
CommandSyntaxException {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3353:                CommandSourceStack source 
= context.getSource();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3354:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3355:                source.sendSuccess(() -> 
Component.literal("Emergency fix started...")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3356:                                
.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3357:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3358:                try {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3359:                        
source.sendSuccess(() -> Component.literal("Step 0: Fixing null owners...")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3360:                                        
.withStyle(ChatFormatting.YELLOW), false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3361:                        
ColonyAbandonmentManager.emergencyFixAllNullOwners();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3362:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3363:                        // STEP 1: Force 
cleanup all [abandoned] entries AND fix null owners
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3364:                        
source.sendSuccess(
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3365:                                        
() -> Component.literal(
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3366:                                          
              "Step 1: Cleaning up [abandoned] entries AND fixing null owners...")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3367:                                          
              .withStyle(ChatFormatting.YELLOW),
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3368:                                        
false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3369:                        
ColonyAbandonmentManager.cleanupAllColoniesAbandonedEntries();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3370:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3371:                        // STEP 2: Fix 
all abandoned colonies with system owners
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3372:                        
source.sendSuccess(() -> Component.literal("Step 2: Fixing abandoned colonies...")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3373:                                        
.withStyle(ChatFormatting.YELLOW), false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3374:                        IColonyManager 
colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3375:                        final int[] 
fixedColonies = { 0 }; // Use array to allow modification in lambda
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3376:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3377:                        for (IColony 
colony : colonyManager.getAllColonies()) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3378:                                try {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3379:                                        
IPermissions permissions = colony.getPermissions();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3380:                                        
UUID owner = permissions.getOwner();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3381:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3382:                                        
// Check if colony has [abandoned] issues or no owner
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3383:                                        
boolean needsFix = false;
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3384:                                        
final String[] issue = { "" };
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3385:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3386:                                        
if (owner == null) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3387:                                          
      needsFix = true;
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3388:                                          
      issue[0] = "null owner";
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3389:                                        } 
else {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3390:                                          
      // Check for problematic player entries
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3391:                                          
      for (ColonyPlayer player : permissions.getPlayers().values()) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3392:                                          
              if (player.getName() != null &&
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3393:                                          
                              (player.getName().contains("[abandoned]") ||
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3394:                                          
                                              player.getName().toLowerCase()
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3395:                                          
                                                              .contains("abandoned"))) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3396:                                          
                      needsFix = true;
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3397:                                          
                      issue[0] = "has [abandoned] entries";
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3398:                                          
                      break;
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3399:                                          
              }
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3400:                                          
      }
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3401:                                        }
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3402:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3403:                                        
if (needsFix) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3404:                                          
      final String colonyName = colony.getName();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3405:                                          
      final String fixIssue = issue[0];
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3406:                                          
      source.sendSuccess(() -> Component
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3407:                                          
                      .literal("  Fixing " + colonyName + " - " + fixIssue)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3408:                                          
                      .withStyle(ChatFormatting.AQUA), false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3409:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3410:                                          
      // Apply emergency fix using our new system
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3411:                                          
      ColonyAbandonmentManager.cleanupAbandonedEntries(permissions);
> src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3496:        private static int 
handleFixNullOwners(CommandContext<CommandSourceStack> context) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3497:                CommandSourceStack source 
= context.getSource();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3498:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3499:                source.sendSuccess(() -> 
Component.literal("Fixing null colony owners...")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3500:                                
.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3501:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3502:                try {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3503:                        
source.sendSuccess(() -> Component.literal("Scanning all colonies for null owners...")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3504:                                        
.withStyle(ChatFormatting.YELLOW), false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3505:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3506:                        
ColonyAbandonmentManager.emergencyFixAllNullOwners();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3507:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3508:                        
source.sendSuccess(() -> Component.literal("Null owner fix completed.")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3509:                                        
.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3510:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3511:                        
source.sendSuccess(
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3512:                                        
() -> Component.literal(
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3513:                                          
              "All colonies now have valid owners - GUI crashes should be prevented!")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3514:                                          
              .withStyle(ChatFormatting.GREEN),
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3515:                                        
false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3516:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3517:                } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3518:                        
source.sendFailure(Component.literal("Error during null owner fix: " + e.getMessage())
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3519:                                        
.withStyle(ChatFormatting.RED));
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3520:                        
LOGGER.error("Error during /wnt fixnullowners command", e);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3521:                }
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3522:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3523:                return 1;
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3524:        }
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3525:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3526:        /**
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3527:         * Debug command to show detailed 
tax breakdown for a colony
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3528:         */
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3529:        private static int 
debugTaxBreakdown(CommandContext<CommandSourceStack> context, String colonyName)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3530:                        throws 
CommandSyntaxException {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3531:                ServerPlayer player = 
context.getSource().getPlayerOrException();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3532:                CommandSourceStack source 
= context.getSource();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3533:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3534:                // Find colony by name
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3535:                IColonyManager 
colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3536:                IColony tempColony = null;
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3537:                for (IColony c : 
colonyManager.getAllColonies()) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3538:                        if 
(c.getName().equalsIgnoreCase(colonyName)) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3539:                                
tempColony = c;
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3540:                                break;
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3541:                        }
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3542:                }
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3543:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3544:                if (tempColony == null) {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3545:                        
source.sendFailure(Component.literal("Colony not found: " + colonyName)
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3546:                                        
.withStyle(ChatFormatting.RED));
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3547:                        return 0;
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3548:                }
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3549:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3550:                final IColony colony = 
tempColony; // Make final for lambda capture
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3551:
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3552:                try {
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3553:                        
source.sendSuccess(() -> Component.literal("═══════════════════════════════════════")
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3554:                                        
.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3555:                        
source.sendSuccess(() -> Component.literal("📊 TAX DEBUG BREAKDOWN: " + colony.getName())
  src\main\java\net\machiavelli\minecolonytax\commands\WntCommands.java:3556:                                        
.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java | Select-Object -Index (3370..3465)' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 329ms:
                        // STEP 2: Fix all abandoned colonies with system owners
                        source.sendSuccess(() -> Component.literal("Step 2: Fixing abandoned colonies...")
                                        .withStyle(ChatFormatting.YELLOW), false);
                        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                        final int[] fixedColonies = { 0 }; // Use array to allow modification in lambda

                        for (IColony colony : colonyManager.getAllColonies()) {
                                try {
                                        IPermissions permissions = colony.getPermissions();
                                        UUID owner = permissions.getOwner();

                                        // Check if colony has [abandoned] issues or no owner
                                        boolean needsFix = false;
                                        final String[] issue = { "" };

                                        if (owner == null) {
                                                needsFix = true;
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

                                                        // CRITICAL FIX: ensure the system owner holds the OWNER rank so
                                                        // getOwner() resolves (prevents GUI crashes). Version-stable
                                                        // setPlayerRank, not reflected setOwner â€” MineColonies changed
                                                        // setOwner(UUID) -> setOwner(Player) in 1.1.1237, breaking the
                                                        // old reflection. [1.21-PORT] re-verify â€” see PORTING_NOTES.md.
                                                        try {
                                                                permissions.setPlayerRank(systemOwner,
                                                                                permissions.getRankOwner(),
                                                                                colony.getWorld());
                                                                source.sendSuccess(() -> Component
                                                                                .literal("    Set system owner as actual owner to prevent GUI crashes")
                                                                                .withStyle(ChatFormatting.GREEN),
                                                                                false);
                                                        } catch (Exception e) {
                                                                source.sendFailure(Component
                                                                                .literal("    WARNING: Could not set actual owner - GUI may crash!")
                                                                                .withStyle(ChatFormatting.RED));
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
                                                                        com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS,
                                                                        false);
                                                        permissions.setPermission(neutralRank,
                                                                        com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS,
                                                                        false);
                                                        permissions.setPermission(neutralRank,
                                                                        com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK,
                                                                        false);
                                                        permissions.setPermission(neutralRank,
                                                                        com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER,
                                                                        false);
                                                }

                                                fixedColonies[0]++;
                                        }

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java -Pattern 'cleanupAbandonedEntries' -Context 0,120" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 403ms:

> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:280:            
cleanupAbandonedEntries(permissions);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:281:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:282:            // Keep a valid 
owner in the colony to prevent GUI crashes in MineColonies.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:283:            UUID colonyOwner = 
permissions.getOwner();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:284:            if (colonyOwner == 
null) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:285:                
LOGGER.warn("Null owner detected during abandonment of colony {} - assigning placeholder", colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:286:                UUID newOwner 
= null;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:287:                if 
(!removedPlayers.isEmpty()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:288:                    newOwner = 
removedPlayers.get(0);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:289:                } else if 
(!allPlayers.isEmpty()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:290:                    newOwner = 
allPlayers.keySet().iterator().next();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:291:                } else {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:292:                    newOwner = 
createSystemOwner();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:293:                    
permissions.addPlayer(newOwner, "[AUTO_OWNER]", permissions.getRankOwner());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:294:                    
LOGGER.warn("No players found in colony {} during abandonment - created system owner placeholder", colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:295:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:296:                
safeSetPlayerRank(colony, permissions, newOwner, permissions.getRankOwner());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:297:                if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Assigned {} as owner placeholder for abandoned colony {}", newOwner, 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:298:                colonyOwner = 
newOwner;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:299:            } else {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:300:                if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Keeping existing owner {} to prevent GUI crashes in colony {}", 
colonyOwner, colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:301:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:302:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:303:            // Keep the owner 
at Owner rank; demote all other players to neutral.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:304:            Rank 
colonyNeutralRank = permissions.getRankNeutral();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:305:            for (UUID playerId 
: allPlayers.keySet()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:306:                if 
(!playerId.equals(colonyOwner)) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:307:                    
ColonyPlayer player = allPlayers.get(playerId);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:308:                    if 
(!player.getRank().equals(colonyNeutralRank)) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:309:                        
boolean rankSet = safeSetPlayerRank(colony, permissions, playerId, colonyNeutralRank);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:310:                        if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Set non-owner player {} to neutral rank: {}", playerId, rankSet);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:311:                    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:312:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:313:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:314:            if 
(TaxConfig.isNormalLogging()) LOGGER.info("Colony {} abandoned: all non-owner players set to neutral rank", 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:315:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:316:            // We do not call 
setOwnerAbandoned() — it creates problematic [abandoned]
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:317:            // player entries. 
Instead we rely on the abandonedColonies set.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:318:            try {
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:319:                
cleanupAbandonedEntries(permissions);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:320:            } catch (Exception 
e) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:321:                
LOGGER.error("Error during safe abandonment for colony {}", colony.getName(), e);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:322:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:323:            if 
(TaxConfig.isNormalLogging()) LOGGER.info("Successfully abandoned colony {} - set {} players to neutral rank", 
colony.getName(), allPlayers.size());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:324:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:325:            if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Setting restrictive neutral permissions for abandoned colony {}", 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:326:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:327:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:328:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:329:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:330:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.TOSS_ITEM, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:331:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.PICKUP_ITEM, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:332:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:333:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.HURT_CITIZEN, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:334:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.HURT_VISITOR, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:335:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.TELEPORT_TO_COLONY, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:336:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.RECEIVE_MESSAGES, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:337:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.USE_SCAN_TOOL, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:338:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.THROW_POTION, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:339:            
permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.SHOOT_ARROW, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:340:            // NOTE: 
GUARDS_ATTACK removed from API - hostility now controlled by Rank.isHostile()
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:341:            // NOTE: 
USE_FLY_STICK not available in this API version
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:342:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:343:            Rank hostileRank = 
permissions.getRankHostile();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:344:            
permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:345:            
permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:346:            
permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:347:            
permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:348:            
permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.TOSS_ITEM, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:349:            
permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.PICKUP_ITEM, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:350:            
permissions.setPermission(hostileRank, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:351:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:352:            if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Colony {} - all players set to neutral/hostile with zero permissions", 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:353:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:354:            // Mark colony as 
claimable
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:355:            
abandonedColonies.add(colony.getID());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:356:            saveData(); // 
AUDIT FIX (defensive_04 M2): persist abandonment + former-member state
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:357:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:358:            // Notify removed 
players when they next log in
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:359:            
scheduleAbandonmentNotifications(removedPlayers, colony, server);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:360:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:361:            // Broadcast 
abandonment to server
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:362:            Component 
broadcastMessage = Component.literal("Colony ")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:363:                    
.withStyle(ChatFormatting.YELLOW)
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:364:                    
.append(Component.literal(colony.getName()).withStyle(ChatFormatting.GOLD))
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:365:                    
.append(Component.literal(" has been abandoned due to inactivity and can now be claimed!")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:366:                           
.withStyle(ChatFormatting.YELLOW));
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:367:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:368:            
server.getPlayerList().broadcastSystemMessage(broadcastMessage, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:369:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:370:            // Remove from 
warned list
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:371:            
warnedColonies.remove(colony.getID());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:372:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:373:            return true;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:374:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:375:        } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:376:            
LOGGER.error("Failed to abandon colony {} ({})", colony.getName(), colony.getID(), e);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:377:            return false;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:378:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:379:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:380:    
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:381:    /**
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:382:     * Warn colony owners and 
officers about upcoming abandonment.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:383:     */
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:384:    private static boolean 
warnColonyOwnersAndOfficers(IColony colony, MinecraftServer server) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:385:        int colonyId = 
colony.getID();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:386:        long currentTime = 
System.currentTimeMillis();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:387:        
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:388:        // Check if we've 
warned recently (don't spam)
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:389:        Long lastWarned = 
warnedColonies.get(colonyId);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:390:        if (lastWarned != null 
&& (currentTime - lastWarned) < 24 * 60 * 60 * 1000) { // 24 hours
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:391:            return false;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:392:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:393:        
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:394:        try {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:395:            // Use WnT timer 
for accurate days until abandonment
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:396:            long 
officerVisitHours = 
net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.getHoursSinceOfficerVisit(colony.getID());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:397:            int 
actualInactivityHours = (officerVisitHours >= 0) ? (int) officerVisitHours : colony.getLastContactInHours();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:398:            int 
daysUntilAbandon = TaxConfig.getColonyAutoAbandonDays() - (actualInactivityHours / 24);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:399:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:400:            Component 
warningMessage = Component.literal("WARNING: Your colony ")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:401:                    
.withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:402:                    
.append(Component.literal(colony.getName()).withStyle(ChatFormatting.GOLD))
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:403:                    
.append(Component.literal(" will be abandoned in " + daysUntilAbandon + " days due to inactivity!")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:404:                           
.withStyle(ChatFormatting.RED))
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:405:                    
.append(Component.literal("\nVisit your colony to prevent abandonment.")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:406:                           
.withStyle(ChatFormatting.YELLOW));
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:407:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:408:            // Send warning to 
online owners/officers and queue for offline ones
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:409:            boolean 
sentWarning = false;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:410:            for (ColonyPlayer 
colonyPlayer : colony.getPermissions().getPlayers().values()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:411:                if 
(colonyPlayer.getRank().isColonyManager()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:412:                    
ServerPlayer player = server.getPlayerList().getPlayer(colonyPlayer.getID());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:413:                    if (player 
!= null) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:414:                        // 
Player is online - send immediately
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:415:                        
player.sendSystemMessage(warningMessage);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:416:                        
sentWarning = true;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:417:                    } else {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:418:                        // 
Player is offline - queue notification
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:419:                        
queueOfflineNotification(colonyPlayer.getID(), warningMessage);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:420:                        
sentWarning = true;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:421:                    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:422:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:423:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:424:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:425:            if (sentWarning) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:426:                
warnedColonies.put(colonyId, currentTime);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:427:                if 
(TaxConfig.isNormalLogging()) LOGGER.info("Warned owners/officers of colony {} ({}) about upcoming abandonment",
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:428:                          
colony.getName(), colony.getID());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:429:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:430:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:431:            return sentWarning;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:432:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:433:        } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:434:            
LOGGER.error("Failed to warn about colony abandonment for {} ({})", 
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:435:                        
colony.getName(), colony.getID(), e);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:436:            return false;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:437:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:438:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:439:    
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:840:                
cleanupAbandonedEntries(permissions);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:841:            } catch (Exception 
e) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:842:                
LOGGER.error("Error during safe abandonment for colony {}", colony.getName(), e);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:843:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:844:            if 
(TaxConfig.isNormalLogging()) LOGGER.info("Successfully force abandoned colony {} - set {} players to neutral rank", 
colony.getName(), allPlayers.size());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:845:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:846:            if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Setting restrictive neutral permissions for force abandoned colony {}", 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:847:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:848:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:849:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:850:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:851:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.TOSS_ITEM, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:852:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.PICKUP_ITEM, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:853:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:854:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:855:            if 
(TaxConfig.isNormalLogging()) LOGGER.info("Force abandonment completed for colony {} - all players set to neutral with 
restrictive permissions", colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:856:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:857:            
abandonedColonies.add(colony.getID());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:858:            saveData(); // 
AUDIT FIX (defensive_04 M2): persist abandonment + former-member state
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:859:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:860:            Component 
forceAbandonMessage = Component.literal("ADMIN ACTION: Your colony ")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:861:                    
.withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:862:                    
.append(Component.literal(colony.getName()).withStyle(ChatFormatting.GOLD))
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:863:                    
.append(Component.literal(" has been force abandoned by admin " + adminName + ".")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:864:                           
.withStyle(ChatFormatting.RED))
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:865:                    
.append(Component.literal("\nIt can now be claimed by other players using /wnt claimcolony.")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:866:                           
.withStyle(ChatFormatting.YELLOW));
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:867:            for (UUID playerId 
: managersToNotify) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:868:                ServerPlayer 
player = server.getPlayerList().getPlayer(playerId);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:869:                if (player != 
null) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:870:                    
player.sendSystemMessage(forceAbandonMessage);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:871:                } else {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:872:                    
queueOfflineNotification(playerId, forceAbandonMessage);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:873:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:874:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:875:            if 
(TaxConfig.isNormalLogging()) LOGGER.info("Colony {} ({}) force abandoned by admin {}", colony.getName(), 
colony.getID(), adminName);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:876:            return true;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:877:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:878:        } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:879:            
LOGGER.error("Failed to force abandon colony {} ({})", colony.getName(), colony.getID(), e);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:880:            return false;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:881:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:882:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:883:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:884:    /**
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:885:     * Removes any corrupt 
player entries from colony permissions (null UUIDs,
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:886:     * [abandoned] placeholder 
names, invalid UUID patterns). Called after
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:887:     * abandonment to avoid 
the stuck [abandoned] player bug.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:888:     */
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:889:    public static void 
cleanupAbandonedEntries(IPermissions permissions) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:890:        try {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:891:            Map<UUID, 
ColonyPlayer> players = new HashMap<>(permissions.getPlayers());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:892:            List<UUID> 
toRemove = new ArrayList<>();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:893:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:894:            for 
(Map.Entry<UUID, ColonyPlayer> entry : players.entrySet()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:895:                ColonyPlayer 
player = entry.getValue();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:896:                UUID playerId 
= entry.getKey();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:897:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:898:                boolean 
isProblematic = false;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:899:                String reason 
= "";
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:900:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:901:                // 4.x 
world-brick fix: match ONLY the exact synthetic markers the mod itself
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:902:                // ever wrote. 
The old heuristics — ANY name containing "abandoned", names
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:903:                // starting 
with ~ or #, empty/null names, and a bogus UUID-length check —
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:904:                // risked 
deleting a legitimate (possibly just-added or name-unresolved) player
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:905:                // and leaving 
the colony ownerless, which crashes the town hall GUI.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:906:                if (playerId 
== null) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:907:                    
isProblematic = true;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:908:                    reason = 
"null UUID";
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:909:                } else if 
(player == null) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:910:                    
isProblematic = true;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:911:                    reason = 
"null player object";
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:912:                } else if 
(isSystemOwner(playerId)) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:913:                    
isProblematic = true;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:914:                    reason = 
"synthetic system-owner UUID";
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:915:                } else if 
(playerId.equals(new UUID(0L, 0L))) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:916:                    
isProblematic = true;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:917:                    reason = 
"zero UUID";
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:918:                } else if 
(player.getName() != null
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:919:                        && 
(player.getName().equals("[AUTO_OWNER]")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:920:                               
 || player.getName().equals("[AUTO_EMERGENCY_OWNER]")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:921:                               
 || player.getName().equals("[SYSTEM_ABANDONED]")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:922:                               
 || player.getName().contains("[abandoned]"))) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:923:                    
isProblematic = true;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:924:                    reason = 
"synthetic placeholder entry (" + player.getName() + ")";
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:925:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:926:                
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:927:                if 
(isProblematic) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:928:                    
LOGGER.warn("CLEANUP: Found problematic player entry - UUID: {}, Name: '{}', Reason: {}", 
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:929:                        
playerId, player != null ? player.getName() : "null", reason);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:930:                    
toRemove.add(playerId);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:931:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:932:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:933:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:934:            // Remove all 
problematic entries
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:935:            for (UUID playerId 
: toRemove) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:936:                try {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:937:                    
permissions.removePlayer(playerId);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:938:                    if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Removed corrupt permissions entry: {}", playerId);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:939:                } catch 
(Exception e) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:940:                    
LOGGER.warn("Could not remove corrupt entry {}: {}", playerId, e.getMessage());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:941:                    // 
Fallback: try via reflection
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:942:                    try {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:943:                        
java.lang.reflect.Method method = permissions.getClass().getDeclaredMethod("removePlayer", UUID.class);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:944:                        
method.setAccessible(true);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:945:                        
method.invoke(permissions, playerId);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:946:                        if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Force-removed corrupt entry via reflection: {}", playerId);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:947:                    } catch 
(Exception reflectionEx) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:948:                        
LOGGER.error("Reflection removal also failed for {}: {}", playerId, reflectionEx.getMessage());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:949:                    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:950:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:951:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:952:            if 
(!toRemove.isEmpty() && TaxConfig.isDebugLogging()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:953:                
LOGGER.debug("Removed {} corrupt permissions entries", toRemove.size());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:954:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:955:        } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:956:            
LOGGER.error("Error during abandoned entries cleanup", e);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:957:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:958:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:959:    
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:960:    /**
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:961:     * Detects when a real 
officer or owner has been added back to an abandoned colony
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:962:     * and removes the 
abandoned state, restoring normal permissions.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:963:     */
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:964:    public static void 
checkForNewOfficers(IColony colony) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:965:        if (colony == null || 
!isColonyAbandoned(colony)) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:966:            return;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:967:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:968:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:969:        boolean 
hasRealOfficers = colony.getPermissions().getPlayers().values().stream()
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:970:                .filter(player 
-> !isSystemOwner(player.getID()))
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:971:                
.anyMatch(player -> player.getRank().isColonyManager());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:972:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:973:        if (hasRealOfficers) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:974:            if 
(TaxConfig.isNormalLogging()) LOGGER.info("Colony {} reactivated - real officers/owners present, removing abandoned 
status", colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:975:            
markColonyAsClaimed(colony.getID());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:976:            
cleanupSystemOwnerAndSetRealOwner(colony);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:977:            
restoreNormalPermissions(colony);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:978:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:979:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:980:    
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:981:    private static void 
cleanupSystemOwnerAndSetRealOwner(IColony colony) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:982:        try {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:983:            IPermissions 
permissions = colony.getPermissions();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:984:            UUID 
systemOwnerUUID = createSystemOwner();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:985:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:986:            if 
(permissions.getPlayers().containsKey(systemOwnerUUID)) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:987:                
permissions.removePlayer(systemOwnerUUID);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:988:                if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Removed system owner placeholder from reactivated colony {}", 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:989:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:990:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:991:            for (ColonyPlayer 
player : permissions.getPlayers().values()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:992:                if 
(!isSystemOwner(player.getID()) && player.getRank().isColonyManager()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:993:                    try {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:994:                        // 
MineColonies changed IPermissions.setOwner(UUID) -> setOwner(Player) in
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:995:                        // 
1.1.1237, which broke the old reflection ("argument type mismatch") and
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:996:                        // 
left colonies ownerless. setOwner(Player) updates the cached ownerUUID
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:997:                        // 
that getOwner() returns; setPlayerRank does NOT. Prefer setOwner when the
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:998:                        // 
target manager is online; fall back to a best-effort rank assignment when
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:999:                        // 
offline (the new API cannot set an offline player as the cached owner).
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1000:                        // 
[1.21-PORT] same limitation on NeoForge/1.21 — see PORTING_NOTES.md.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1001:                        
net.minecraft.server.level.ServerPlayer online =
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1002:                              
  (colony.getWorld() != null && colony.getWorld().getServer() != null)
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1003:                              
          ? colony.getWorld().getServer().getPlayerList().getPlayer(player.getID())
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1004:                              
          : null;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1005:                        if 
(online != null) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1006:                            
permissions.setOwner(online);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1007:                        } 
else {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1008:                            
safeSetPlayerRank(colony, permissions, player.getID(), permissions.getRankOwner());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1009:                        }
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1056:                    
cleanupAbandonedEntries(permissions);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1057:                    Map<UUID, 
ColonyPlayer> playersAfter = new HashMap<>(permissions.getPlayers());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1058:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1059:                    int 
removed = playersBefore.size() - playersAfter.size();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1060:                    if 
(removed > 0) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1061:                        
coloniesCleaned++;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1062:                        
entriesRemoved += removed;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1063:                        if 
(TaxConfig.isNormalLogging()) LOGGER.info("Cleaned {} corrupt entries from colony {} ({})",
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1064:                              
  removed, colony.getName(), colony.getID());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1065:                    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1066:                } catch 
(Exception e) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1067:                    
LOGGER.error("Error cleaning colony {} ({}): {}", colony.getName(), colony.getID(), e.getMessage());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1068:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1069:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1070:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1071:            if 
((coloniesCleaned > 0 || nullOwnersFixed > 0) && TaxConfig.isNormalLogging()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1072:                
LOGGER.info("Startup colony cleanup: {} null owners fixed, {} colonies cleaned, {} entries removed",
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1073:                        
nullOwnersFixed, coloniesCleaned, entriesRemoved);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1074:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1075:        } catch (Exception e) 
{
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1076:            
LOGGER.error("Error during global colony cleanup", e);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1077:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1078:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1079:    
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1080:    private static void 
restoreNormalPermissions(IColony colony) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1081:        try {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1082:            IPermissions 
permissions = colony.getPermissions();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1083:            Rank neutralRank 
= permissions.getRankNeutral();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1084:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1085:            if 
(TaxConfig.isNormalLogging()) LOGGER.info("Restoring normal permissions for colony {} (no longer abandoned)", 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1086:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1087:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS, true);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1088:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.RIGHTCLICK_BLOCK, true);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1089:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.OPEN_CONTAINER, true);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1090:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.PICKUP_ITEM, true);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1091:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.TOSS_ITEM, true);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1092:            // block/place 
remain false for security
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1093:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.BREAK_BLOCKS, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1094:            
permissions.setPermission(neutralRank, com.minecolonies.api.colony.permissions.Action.PLACE_BLOCKS, false);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1095:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1096:            if 
(TaxConfig.isNormalLogging()) LOGGER.info("Successfully restored normal permissions for colony {}", colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1097:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1098:        } catch (Exception e) 
{
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1099:            
LOGGER.error("Error restoring normal permissions for colony {}", colony.getID(), e);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1100:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1101:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1102:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1103:    public enum 
AbandonmentStatus {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1104:        ACTIVE,
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1105:        SHOULD_WARN,
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1106:        SHOULD_ABANDON
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1107:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1108:}



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java | Select-Object -Index (888..956)' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 319ms:
    public static void cleanupAbandonedEntries(IPermissions permissions) {
        try {
            Map<UUID, ColonyPlayer> players = new HashMap<>(permissions.getPlayers());
            List<UUID> toRemove = new ArrayList<>();

            for (Map.Entry<UUID, ColonyPlayer> entry : players.entrySet()) {
                ColonyPlayer player = entry.getValue();
                UUID playerId = entry.getKey();

                boolean isProblematic = false;
                String reason = "";

                // 4.x world-brick fix: match ONLY the exact synthetic markers the mod itself
                // ever wrote. The old heuristics â€” ANY name containing "abandoned", names
                // starting with ~ or #, empty/null names, and a bogus UUID-length check â€”
                // risked deleting a legitimate (possibly just-added or name-unresolved) player
                // and leaving the colony ownerless, which crashes the town hall GUI.
                if (playerId == null) {
                    isProblematic = true;
                    reason = "null UUID";
                } else if (player == null) {
                    isProblematic = true;
                    reason = "null player object";
                } else if (isSystemOwner(playerId)) {
                    isProblematic = true;
                    reason = "synthetic system-owner UUID";
                } else if (playerId.equals(new UUID(0L, 0L))) {
                    isProblematic = true;
                    reason = "zero UUID";
                } else if (player.getName() != null
                        && (player.getName().equals("[AUTO_OWNER]")
                                || player.getName().equals("[AUTO_EMERGENCY_OWNER]")
                                || player.getName().equals("[SYSTEM_ABANDONED]")
                                || player.getName().contains("[abandoned]"))) {
                    isProblematic = true;
                    reason = "synthetic placeholder entry (" + player.getName() + ")";
                }
                
                if (isProblematic) {
                    LOGGER.warn("CLEANUP: Found problematic player entry - UUID: {}, Name: '{}', Reason: {}", 
                        playerId, player != null ? player.getName() : "null", reason);
                    toRemove.add(playerId);
                }
            }
            
            // Remove all problematic entries
            for (UUID playerId : toRemove) {
                try {
                    permissions.removePlayer(playerId);
                    if (TaxConfig.isDebugLogging()) LOGGER.debug("Removed corrupt permissions entry: {}", playerId);
                } catch (Exception e) {
                    LOGGER.warn("Could not remove corrupt entry {}: {}", playerId, e.getMessage());
                    // Fallback: try via reflection
                    try {
                        java.lang.reflect.Method method = permissions.getClass().getDeclaredMethod("removePlayer", UUID.class);
                        method.setAccessible(true);
                        method.invoke(permissions, playerId);
                        if (TaxConfig.isDebugLogging()) LOGGER.debug("Force-removed corrupt entry via reflection: {}", playerId);
                    } catch (Exception reflectionEx) {
                        LOGGER.error("Reflection removal also failed for {}: {}", playerId, reflectionEx.getMessage());
                    }
                }
            }
            if (!toRemove.isEmpty() && TaxConfig.isDebugLogging()) {
                LOGGER.debug("Removed {} corrupt permissions entries", toRemove.size());
            }
        } catch (Exception e) {
            LOGGER.error("Error during abandoned entries cleanup", e);
        }

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/**/*.java,src/main/java/net/machiavelli/minecolonytax/*.java -Pattern 'RandomEventManager\\.onTaxCycle|RandomEventManager\\.persist'" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 393ms:

src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1007:                    // saveTaxData() and 
RandomEventManager.persist() and lose the cycle's
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1011:                            
RandomEventManager.onTaxCycle(colony);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1021:            // see optimization audit C1 / 
RandomEventManager.persist()).
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1023:                RandomEventManager.persist();



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java -TotalCount 80' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 276ms:
package net.machiavelli.minecolonytax;

import net.machiavelli.minecolonytax.network.NetworkHandler;
import net.machiavelli.minecolonytax.vassalization.VassalManager;
import net.machiavelli.minecolonytax.recipe.ModRecipeSerializers;
import net.machiavelli.minecolonytax.commands.RecipeDisableTestCommand;
import net.machiavelli.minecolonytax.commands.TreasuryCommand;
import net.machiavelli.minecolonytax.commands.RaidRepairCommand;
import net.machiavelli.minecolonytax.commands.FactionCommand;
import net.machiavelli.minecolonytax.commands.TaxPolicyCommand;
import net.machiavelli.minecolonytax.commands.RandomEventsCommand;
import net.machiavelli.minecolonytax.economy.TreasuryManager;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.machiavelli.minecolonytax.data.HistoryManager;
import net.machiavelli.minecolonytax.raid.GuardResistanceHandler;
import net.machiavelli.minecolonytax.db.WarStatsDB;
import net.machiavelli.minecolonytax.faction.FactionManager;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

@Mod(MineColonyTax.MOD_ID)
public class MineColonyTax {
    public static final String MOD_ID = "minecolonytax";
    public static final Logger LOGGER = LogManager.getLogger();

    /**
     * Guards against registering FCT event handlers more than once.
     * DefaultEventBus is a JVM-lifetime singleton â€” in single-player, onServerStarting
     * fires on every world load without clearing the handler list. Without this flag,
     * each world load would add another duplicate handler.
     */
    private static boolean fctEventBusSubscribed = false;

    public MineColonyTax() {
        // COMMON config type writes to /config/warntax/ only, not world/serverconfig/
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TaxConfig.CONFIG, "warntax/minecolonytax.toml");

        ModRecipeSerializers.RECIPE_SERIALIZERS.register(FMLJavaModLoadingContext.get().getModEventBus());

        net.machiavelli.minecolonytax.espionage.ModEntities.ENTITIES
                .register(FMLJavaModLoadingContext.get().getModEventBus());

        // Step 11 â€” siege banner block + item registration. Block is always
        // registered; the Plant-the-Banner objective gates behaviour at runtime
        // via EnableExperimentalSiegeObjectives.
        net.machiavelli.minecolonytax.siege.ModSiegeBlocks.register(
                FMLJavaModLoadingContext.get().getModEventBus());

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);

        MinecraftForge.EVENT_BUS.register(net.machiavelli.minecolonytax.event.RaidKillTracker.class);
        MinecraftForge.EVENT_BUS.register(net.machiavelli.minecolonytax.util.TickScheduler.class);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkHandler.register();
            LOGGER.info("MineColonyTax setup complete");
        });
    }

    private void clientSetup(final net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        // Set Patchouli config flag if admin pages are enabled
        if (ModList.get().isLoaded("patchouli") && TaxConfig.SHOW_ADMIN_PAGES_IN_BOOK.get()) {
            try {

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java,src/main/java/net/machiavelli/minecolonytax/**/*.java -Pattern 'TaxManager\\.initialize|EVENT_BUS.register\\(.*TaxManager' -Context 2,2" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 397ms:

  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:106:        if (TaxConfig.isNormalLogging()) 
LOGGER.info("Server starting - initializing TaxManager with configured interval of {} minutes",
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:107:                
TaxConfig.getTaxIntervalInMinutes());
> src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:108:        TaxManager.initialize(event.getServer());
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:109:
  src\main\java\net\machiavelli\minecolonytax\MineColonyTax.java:110:        FirstColonyTracker.loadData();



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/TaxManager.java -TotalCount 40' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 274ms:
package net.machiavelli.minecolonytax;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.minecolonies.api.entity.citizen.happiness.ExpirationBasedHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.common.MinecraftForge;
import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.economy.RaidPenaltyManager;
import net.machiavelli.minecolonytax.economy.TreasuryManager;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicy;
import net.machiavelli.minecolonytax.events.random.RandomEventManager;
import net.machiavelli.minecolonytax.util.TickScheduler;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/TaxManager.java -Pattern '@Mod|class TaxManager' -Context 0,1" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 310ms:

> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:47:@Mod.EventBusSubscriber
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:48:public class TaxManager {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:49:



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/TaxManager.java -Pattern 'colonyTaxMap' -Context 0,0" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 337ms:

src\main\java\net\machiavelli\minecolonytax\TaxManager.java:50:    private static final Map<Integer, Integer> 
colonyTaxMap = new HashMap<>();
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:258:        int storedTax = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:311:            colonyTaxMap.put(colonyId, 0); // Reset 
tax to zero
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:319:            colonyTaxMap.put(colonyId, storedTax - 
claimedAmount); // Deduct the claimed amount
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:335:        return 
colonyTaxMap.getOrDefault(colony.getID(), 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:339:        return colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:347:        int currentTax = 
colonyTaxMap.getOrDefault(colony.getID(), 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:352:            colonyTaxMap.put(colony.getID(), newTax);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:362:        int currentTax = 
colonyTaxMap.getOrDefault(colony.getID(), 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:364:        colonyTaxMap.put(colony.getID(), currentTax - 
deduction);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:373:        int current = colonyTaxMap.getOrDefault(id, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:386:        colonyTaxMap.put(id, clamped);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:466:                    int startingBalance = 
colonyTaxMap.getOrDefault(colonyId, 0); // Balance BEFORE this cycle
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:542:                            int currentTax = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:558:                                currentTax = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:565:                                
colonyTaxMap.put(colonyId, newTax);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:571:                    finalTaxBalance = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:582:                                finalTaxBalance = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:595:                            finalTaxBalance = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:611:                            finalTaxBalance = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:622:                                finalTaxBalance = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:665:                            finalTaxBalance = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:681:                        finalTaxBalance = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:694:                            finalTaxBalance = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:710:                            finalTaxBalance = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:726:                            finalTaxBalance = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1166:        final Map<Integer, Integer> snapshot = new 
HashMap<>(colonyTaxMap);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1204:                    colonyTaxMap.putAll(loadedData);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1239:        int currentTax = 
colonyTaxMap.getOrDefault(colonyId, 0);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1256:        colonyTaxMap.put(colonyId, newBalance);
src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1260:                    colonyTaxMap.get(colonyId));



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/event/ColonyEventListener.java,src/main/java/net/machiavelli/minecolonytax/**/*.java -Pattern 'colonyBuildingLevels|getBuildingLevels|showColonyInitializationLogs'" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 392ms:

src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:33:    private static final Map<Integer, 
Map<IBuilding, Integer>> colonyBuildingLevels = new HashMap<>();
src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:65:        // colonyBuildingLevels empty in 
production, so it can't leak there.
src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:66:        if 
(!TaxConfig.showColonyInitializationLogs()) {
src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:74:                Map<IBuilding, Integer> 
buildingLevels = colonyBuildingLevels.computeIfAbsent(colonyId, k -> new HashMap<>());
src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:33:    private static final Map<Integer, 
Map<IBuilding, Integer>> colonyBuildingLevels = new HashMap<>();
src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:65:        // colonyBuildingLevels empty in 
production, so it can't leak there.
src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:66:        if 
(!TaxConfig.showColonyInitializationLogs()) {
src\main\java\net\machiavelli\minecolonytax\event\ColonyEventListener.java:74:                Map<IBuilding, Integer> 
buildingLevels = colonyBuildingLevels.computeIfAbsent(colonyId, k -> new HashMap<>());



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/util/ItemUtils.java -Pattern 'giveCurrencyToPlayer|boolean' -Context 0,80" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 354ms:

> src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:103:    public static boolean 
isMultiDenominationMode() {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:104:        String config = 
TaxConfig.getCurrencyDenominations();
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:105:        return config != null && 
!config.isBlank();
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:106:    }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:107:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:108:    // ── Inventory counting 
────────────────────────────────────────────────
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:109:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:110:    /**
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:111:     * Count the total monetary value of all 
currency denominations in the inventory.
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:112:     * Works on both client and server. In 
single-denomination mode this equals
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:113:     * the raw item count; in multi-denomination 
mode each item contributes its value.
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:114:     */
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:115:    public static int 
countInventoryValue(Inventory inv) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:116:        List<Denomination> denoms = 
getDenominations();
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:117:        int total = 0;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:118:        for (int i = 0; i < 
inv.getContainerSize(); i++) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:119:            ItemStack stack = inv.getItem(i);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:120:            if (stack.isEmpty()) continue;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:121:            for (Denomination d : denoms) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:122:                if (stack.getItem() == d.item) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:123:                    total += stack.getCount() * 
d.value;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:124:                    break;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:125:                }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:126:            }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:127:        }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:128:        return total;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:129:    }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:130:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:131:    // ── Giving items 
──────────────────────────────────────────────────────
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:132:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:133:    /**
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:134:     * Give the player items worth exactly 
{@code amount} monetary units.
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:135:     *
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:136:     * In single-denomination mode: gives {@code 
amount} of CurrencyItemName.
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:137:     * In multi-denomination mode: greedily 
assigns largest denominations first.
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:138:     * Any sub-smallest-denomination remainder 
is rounded up to one extra coin of
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:139:     * the smallest denomination so the player 
is never shorted.
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:140:     */
> src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:141:    public static boolean 
giveCurrencyToPlayer(ServerPlayer player, int amount) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:142:        if (player == null || amount <= 0) 
return false;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:143:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:144:        List<Denomination> denoms = 
getDenominations();
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:145:        if (denoms.isEmpty()) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:146:            LOGGER.warn("No valid currency 
denominations configured; cannot give {} currency", amount);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:147:            return false;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:148:        }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:149:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:150:        // Single-denomination fast path
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:151:        if (denoms.size() == 1 && 
denoms.get(0).value == 1) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:152:            return giveItemsToPlayer(player, 
denoms.get(0).itemId, amount);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:153:        }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:154:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:155:        int remaining = amount;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:156:        for (Denomination d : denoms) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:157:            if (remaining <= 0) break;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:158:            int count = remaining / d.value;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:159:            if (count <= 0) continue;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:160:            remaining -= count * d.value;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:161:            // Give in stacks (player inventory 
handles splitting)
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:162:            ItemStack stack = new 
ItemStack(d.item, count);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:163:            if 
(!player.getInventory().add(stack)) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:164:                player.drop(stack, false);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:165:            }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:166:        }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:167:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:168:        // Remainder can't be expressed in whole 
denominations — round up to 1 of smallest
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:169:        if (remaining > 0) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:170:            Denomination smallest = 
denoms.get(denoms.size() - 1);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:171:            ItemStack extra = new 
ItemStack(smallest.item, 1);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:172:            if 
(!player.getInventory().add(extra)) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:173:                player.drop(extra, false);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:174:            }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:175:        }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:176:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:177:        return true;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:178:    }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:179:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:180:    // ── Taking items 
──────────────────────────────────────────────────────
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:181:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:182:    /**
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:183:     * Remove items worth exactly {@code amount} 
from the player's inventory.
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:184:     * Uses largest denominations first to 
preserve small change.
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:185:     *
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:186:     * @return the amount actually removed, or 0 
if exact change is not possible.
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:187:     */
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:188:    public static int 
takeCurrencyFromInventory(ServerPlayer player, int amount) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:189:        if (player == null || amount <= 0) 
return 0;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:190:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:191:        List<Denomination> denoms = 
getDenominations();
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:192:        if (denoms.isEmpty()) return 0;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:193:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:194:        // Check total available value first
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:195:        int totalAvailable = 
countInventoryValue(player.getInventory());
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:196:        if (totalAvailable < amount) return 0;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:197:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:198:        // Verify exact change is possible with 
a separate pass before modifying inventory
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:199:        if 
(!canMakeExactChange(player.getInventory(), denoms, amount)) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:200:            LOGGER.warn("Player {} cannot make 
exact change for {} — request denied", player.getName().getString(), amount);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:201:            
player.sendSystemMessage(Component.literal(
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:202:                    "Cannot pay exactly " + 
amount + ". Please break larger coins into smaller ones.")
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:203:                    
.withStyle(net.minecraft.ChatFormatting.YELLOW));
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:204:            return 0;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:205:        }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:206:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:207:        // Now actually remove items
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:208:        int remaining = amount;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:209:        for (Denomination d : denoms) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:210:            if (remaining <= 0) break;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:211:            int needed = remaining / d.value;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:212:            if (needed <= 0) continue;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:213:            for (int i = 0; i < 
player.getInventory().getContainerSize() && needed > 0; i++) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:214:                ItemStack stack = 
player.getInventory().getItem(i);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:215:                if (!stack.isEmpty() && 
stack.getItem() == d.item) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:216:                    int take = Math.min(needed, 
stack.getCount());
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:217:                    stack.shrink(take);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:218:                    needed -= take;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:219:                    remaining -= take * d.value;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:220:                }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:221:            }
> src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:230:    private static boolean 
canMakeExactChange(Inventory inv, List<Denomination> denoms, int amount) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:231:        // Count available items per denomination
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:232:        int[] available = new int[denoms.size()];
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:233:        for (int i = 0; i < 
inv.getContainerSize(); i++) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:234:            ItemStack stack = inv.getItem(i);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:235:            if (stack.isEmpty()) continue;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:236:            for (int d = 0; d < denoms.size(); 
d++) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:237:                if (stack.getItem() == 
denoms.get(d).item) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:238:                    available[d] += 
stack.getCount();
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:239:                    break;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:240:                }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:241:            }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:242:        }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:243:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:244:        int remaining = amount;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:245:        for (int d = 0; d < denoms.size(); d++) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:246:            if (remaining <= 0) break;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:247:            int use = Math.min(remaining / 
denoms.get(d).value, available[d]);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:248:            remaining -= use * 
denoms.get(d).value;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:249:        }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:250:        return remaining == 0;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:251:    }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:252:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:253:    // ── Legacy helpers 
────────────────────────────────────────────────────
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:254:
> src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:255:    public static boolean 
giveItemsToPlayer(ServerPlayer player, String itemName, int amount) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:256:        if (player == null || amount <= 0) 
return false;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:257:
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:258:        Item item = 
ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName));
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:259:        if (item != null) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:260:            ItemStack itemStack = new 
ItemStack(item, amount);
> src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:261:            boolean added = 
player.getInventory().add(itemStack);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:262:            if (!added) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:263:                player.drop(itemStack, false);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:264:                
player.sendSystemMessage(Component.translatable("taxmanager.inventory_full", amount, itemName));
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:265:                LOGGER.debug("Player's inventory 
was full, dropped {} {} near them", amount, itemName);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:266:            } else {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:267:                
player.sendSystemMessage(Component.translatable("taxmanager.currency_received", amount, itemName));
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:268:                LOGGER.debug("Successfully gave 
{} {} to player {}", amount, itemName, player.getName().getString());
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:269:            }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:270:            return true;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:271:        } else {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:272:            LOGGER.warn("Item {} not found in 
registry, falling back to /give command", itemName);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:273:            String giveCommand = 
String.format("give %s %s %d", player.getName().getString(), itemName, amount);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:274:            try {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:275:                
player.getServer().getCommands().performPrefixedCommand(
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:276:                        
player.getServer().createCommandSourceStack(),
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:277:                        giveCommand);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:278:                LOGGER.debug("Executed fallback 
give command: {}", giveCommand);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:279:                return true;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:280:            } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:281:                LOGGER.error("Failed to execute 
give command: {}", giveCommand, e);
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:282:                return false;
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:283:            }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:284:        }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:285:    }
  src\main\java\net\machiavelli\minecolonytax\util\ItemUtils.java:286:}



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/WarSystem.java -Pattern 'saveActiveWars|loadActiveWars|GSON|transient|countdownTaskId|joinStartTaskId' -Context 2,8" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 402ms:

  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:35:import org.apache.logging.log4j.Logger;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:36:
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:37:import com.google.gson.Gson;
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:38:import com.google.gson.GsonBuilder;
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:39:import com.google.gson.reflect.TypeToken;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:40:import java.io.FileReader;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:41:import java.io.FileWriter;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:42:import java.io.Reader;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:43:import java.io.Writer;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:44:import java.lang.reflect.Type;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:45:import java.nio.file.Files;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:46:import java.nio.file.Path;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:47:import java.nio.file.Paths;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1380:
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1381:        if (warData != null) {
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1382:            if (warData.countdownTaskId >= 0) {
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1383:                
TickScheduler.cancel(warData.countdownTaskId);
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1384:                warData.countdownTaskId = -1;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1385:            }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1386:            if (warData.warChestDrainTaskId >= 0) {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1387:                
TickScheduler.cancel(warData.warChestDrainTaskId);
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1388:                warData.warChestDrainTaskId = -1;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1389:            }
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1390:            if (warData.joinCountdownTaskId >= 0) {
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1391:                
TickScheduler.cancel(warData.joinCountdownTaskId);
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1392:                warData.joinCountdownTaskId = -1;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1393:            }
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1394:            if (warData.joinStartTaskId >= 0) {
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1395:                
TickScheduler.cancel(warData.joinStartTaskId);
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1396:                warData.joinStartTaskId = -1;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1397:            }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1398:            if (warData.bossEvent != null) {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1399:                warData.bossEvent.removeAllPlayers();
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1400:                warData.bossEvent.setVisible(false);
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1401:            }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1402:            if (colony.getWorld() != null && 
colony.getWorld().getServer() != null) {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1403:                
colony.getPermissions().getPlayers().forEach((uuid, pdata) -> {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:1404:                    ServerPlayer p = 
colony.getWorld().getServer().getPlayerList().getPlayer(uuid);
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2402:        if (joinDurationMillis >= 6000) {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2403:            final int[] secondsLeft = { 6 };
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2404:            war.joinCountdownTaskId = 
TickScheduler.scheduleRepeating(() -> {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2405:                try {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2406:                    if (war == null || 
war.getColony() == null || !war.isJoinPhaseActive() || secondsLeft[0] < 0) {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2407:                        // Self-cancel: without this 
the repeating task re-arms forever,
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2408:                        // leaking the task + 
retaining the whole WarData (audit C4).
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2409:                        if (war != null && 
war.joinCountdownTaskId >= 0) {
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2410:                            
TickScheduler.cancel(war.joinCountdownTaskId);
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2411:                            war.joinCountdownTaskId 
= -1;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2412:                        }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2413:                        return;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2414:                    }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2415:
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2416:                    // Play countdown sound to all 
war participants
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2417:                    Set<UUID> allParticipants = new 
HashSet<>();
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2418:                    
allParticipants.addAll(war.getAttackerLives().keySet());
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2419:                    
allParticipants.addAll(war.getDefenderLives().keySet());
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2449:
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2450:        // Main timer to start the war when join 
phase ends
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2451:        war.joinStartTaskId = 
TickScheduler.scheduleDelayed(() -> {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2452:            if (war == null || war.getColony() == 
null) {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2453:                return;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2454:            }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2455:            // Guard: the war may have ended during 
the JOINING phase (operator /warstop,
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2456:            // finalize abort, etc.). endWar() 
removes it from ACTIVE_WARS, so if this
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2457:            // delayed task is no longer the active 
war for its colony, do NOT start it —
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2458:            // otherwise it would resurrect an ended 
war and re-enable war permissions
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2459:            // (codex HIGH).
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2587:        }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2588:        final long warDurationSeconds = 
TaxConfig.WAR_DURATION_MINUTES.get() * 60L;
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2589:        warData.countdownTaskId = 
TickScheduler.scheduleRepeating(() -> {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2590:            // Check if war still exists in active 
wars or if the colony world/server is
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2591:            // null
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2592:            if 
(!ACTIVE_WARS.containsKey(warData.getColony().getID()) ||
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2593:                    warData.getColony().getWorld() 
== null ||
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2594:                    
warData.getColony().getWorld().getServer() == null ||
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2595:                    warData.bossEvent == null) {
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2596:                
TickScheduler.cancel(warData.countdownTaskId);
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2597:                warData.countdownTaskId = -1;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2598:                return;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2599:            }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2600:
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2601:            // Check war status - don't process for 
ended wars
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2602:            if (warData.getStatus() != 
WarData.WarStatus.INWAR) {
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2603:                
TickScheduler.cancel(warData.countdownTaskId);
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2604:                warData.countdownTaskId = -1;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2605:                return;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2606:            }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2607:
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2608:            // Finding 9: defensive guard against 
wall-clock skew (NTP, manual
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2609:            // clock change, container restart). If 
now < warStartTime the war
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2610:            // was "born in the future" — almost 
certainly a backwards clock
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2611:            // adjustment. Reset warStartTime to the 
current wall clock so the
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2612:            // war doesn't appear to never expire 
(or instantly expire). This is
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2633:            if (remaining <= 0) {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2634:                handleTimeExpiry(warData);
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2635:                
TickScheduler.cancel(warData.countdownTaskId);
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2636:                warData.countdownTaskId = -1;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2637:            }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2638:        }, 1000, 1000);
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2639:    }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2640:
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2641:    public static void sendColonyMessage(IColony 
colony, Component message) {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2642:        if (colony == null || colony.getWorld() == 
null)
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2643:            return;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:2644:        IPermissions perms = colony.getPermissions();
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4025:    // ==================== WAR PERSISTENCE 
====================
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4026:
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4027:    private static final Gson WAR_GSON = new 
GsonBuilder().setPrettyPrinting().create();
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4028:    private static final String WAR_STORAGE_FILE = 
"config/warntax/active_wars.json";
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4029:
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4030:    private static class WarSaveEntry {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4031:        String warID;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4032:        String attacker;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4033:        String defender;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4034:        String attackerTeamID;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4035:        String defenderTeamID;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4077:    }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4078:
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4079:    public static void saveActiveWars() {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4080:        try {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4081:            Path path = Paths.get(WAR_STORAGE_FILE);
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4082:            
Files.createDirectories(path.getParent());
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4083:
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4084:            WarSaveData saveData = new WarSaveData();
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4085:            saveData.wars = new ArrayList<>();
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4086:
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4087:            for (Map.Entry<Integer, WarData> entry : 
ACTIVE_WARS.entrySet()) {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4179:            Path tmp = 
path.resolveSibling(path.getFileName() + ".tmp");
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4180:            try (Writer writer = new 
FileWriter(tmp.toFile())) {
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4181:                WAR_GSON.toJson(saveData, writer);
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4182:            }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4183:            try {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4184:                Files.move(tmp, path,
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4185:                        
java.nio.file.StandardCopyOption.ATOMIC_MOVE,
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4186:                        
java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4187:            } catch 
(java.nio.file.AtomicMoveNotSupportedException windowsFallback) {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4188:                Files.move(tmp, path, 
java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4189:            }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4206:
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4207:        try (Reader reader = new 
FileReader(path.toFile())) {
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4208:            WarSaveData saveData = 
WAR_GSON.fromJson(reader, WarSaveData.class);
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4209:            if (saveData == null || saveData.wars == 
null || saveData.wars.isEmpty()) {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4210:                WARSYSTEM_LOGGER.info("No wars to 
restore from save file");
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4211:                Files.deleteIfExists(path);
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4212:                return;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4213:            }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4214:
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4215:            MinecraftServer server = 
ServerLifecycleHooks.getCurrentServer();
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4216:            if (server == null) {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4334:        }
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4335:
> src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4336:        // Convert sentinel team IDs (written by 
saveActiveWars for wars whose
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4337:        // team IDs were null at save time) back 
into null so callers see the
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4338:        // same invariant they had before the save.
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4339:        UUID atkTid;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4340:        UUID defTid;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4341:        try {
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4342:            atkTid = 
UUID.fromString(e.attackerTeamID);
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4343:            if 
(NULL_TEAM_ID_SENTINEL.equals(atkTid)) atkTid = null;
  src\main\java\net\machiavelli\minecolonytax\WarSystem.java:4344:        } catch (IllegalArgumentException iae) { 
atkTid = null; }



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-Object -Index (2398..2445)' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 363ms:

        // Add countdown sound timer for the last 6 seconds of join phase, but only if
        // join phase is at least 6 seconds long
        if (joinDurationMillis >= 6000) {
            final int[] secondsLeft = { 6 };
            war.joinCountdownTaskId = TickScheduler.scheduleRepeating(() -> {
                try {
                    if (war == null || war.getColony() == null || !war.isJoinPhaseActive() || secondsLeft[0] < 0) {
                        // Self-cancel: without this the repeating task re-arms forever,
                        // leaking the task + retaining the whole WarData (audit C4).
                        if (war != null && war.joinCountdownTaskId >= 0) {
                            TickScheduler.cancel(war.joinCountdownTaskId);
                            war.joinCountdownTaskId = -1;
                        }
                        return;
                    }

                    // Play countdown sound to all war participants
                    Set<UUID> allParticipants = new HashSet<>();
                    allParticipants.addAll(war.getAttackerLives().keySet());
                    allParticipants.addAll(war.getDefenderLives().keySet());

                    // Only play sound if there are participants
                    if (!allParticipants.isEmpty()) {
                        // Play countdown sound using Minecraft's bell sound
                        for (UUID uuid : allParticipants) {
                            ServerPlayer player = ServerLifecycleHooks.getCurrentServer() != null
                                    ? ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(uuid)
                                    : null;
                            if (player != null) {
                                player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.get(), 1.0F,
                                        1.0F);
                            }
                        }
                    }

                    // Notify remaining seconds
                    notifyWarParticipants(war,
                            Component
                                    .literal("â± " + secondsLeft[0] + (secondsLeft[0] == 1 ? " second" : " seconds")
                                            + " until war starts!")
                                    .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)));

                    secondsLeft[0]--;
                } catch (Exception ex) {
                    WARSYSTEM_LOGGER.error("Error in countdown timer: " + ex.getMessage(), ex);
                }
            }, Math.max(0, joinDurationMillis - 6000), 1000); // Start 6 seconds before join phase ends, repeat every 1

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-Object -Index (4440..4515)' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 372ms:
        }
        for (UUID uuid : warData.getDefenderLives().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null)
                bossEvent.addPlayer(p);
        }

        setWarInteractionPermissions(defenderColony, true);
        if (attackerColony != null) {
            setWarInteractionPermissions(attackerColony, true);
        }

        final IColony finalDefenderColony = defenderColony;

        if (warData.getStatus() == WarData.WarStatus.INWAR) {
            applyWarGlowToParticipants(warData);
            applyGuardGlow(defenderColony);
            if (attackerColony != null) {
                applyGuardGlow(attackerColony);
            }

            GuardResistanceHandler.applyResistanceToGuardsForWar(defenderColony);
            if (attackerColony != null) {
                GuardResistanceHandler.applyResistanceToGuardsForWar(attackerColony);
            }

            startWarCountdown(warData);

            long warDurationMs = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
            long elapsed = now - warData.warStartTime;
            long remaining = warDurationMs - elapsed;
            if (remaining > 0) {
                scheduleTimerWarnings(warData, remaining);
            }

            updateBossBar(warData);
            WARSYSTEM_LOGGER.info("Restored INWAR war {} for colony {} ({} ms remaining)",
                    e.warID, defenderColony.getName(), remaining);
        } else if (warData.getStatus() == WarData.WarStatus.JOINING) {
            warData.alliesBossEvent = new ServerBossEvent(
                    Component.literal("Joining War - " + defenderColony.getName()),
                    BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS);
            warData.alliesBossEvent.setProgress(1.0f);
            warData.alliesBossEvent.setVisible(true);

            long remainingJoinMs = warData.getJoinPhaseEndTime() - now;
            if (remainingJoinMs > 0) {
                final int colonyId = e.defenderColonyId;
                TickScheduler.scheduleDelayed(() -> {
                    if (!ACTIVE_WARS.containsKey(colonyId))
                        return;
                    WarData w = ACTIVE_WARS.get(colonyId);
                    if (w == null || w.getStatus() != WarData.WarStatus.JOINING)
                        return;
                    w.setStatus(WarData.WarStatus.INWAR);
                    w.warStartTime = System.currentTimeMillis();
                    finalizeWarStart(w);
                    // finalizeWarStart() can end the war (no valid participants / bad ratio),
                    // removing it from ACTIVE_WARS. Don't enable permissions/countdown on an
                    // ended war (codex HIGH â€” same guard as the live join-start path).
                    if (ACTIVE_WARS.get(colonyId) != w) {
                        return;
                    }
                    setWarInteractionPermissions(w.getColony(), true);
                    if (w.getAttackerColony() != null) {
                        setWarInteractionPermissions(w.getAttackerColony(), true);
                    }
                    startWarCountdown(w);
                    long warDurationMillis = TaxConfig.WAR_DURATION_MINUTES.get() * 60 * 1000L;
                    scheduleTimerWarnings(w, warDurationMillis);
                }, remainingJoinMs);
            }

            updateBossBar(warData);
            WARSYSTEM_LOGGER.info("Restored JOINING war {} for colony {} ({} ms until war starts)",

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/java/net/machiavelli/minecolonytax/util/TickScheduler.java -TotalCount 220' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 277ms:
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

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java -Pattern 'TREASURIES|saveData\\(' -Context 0,2" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 338ms:

> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:49:    private static final Map<Integer, 
Integer> TREASURIES = new ConcurrentHashMap<>();
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:50:
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:51:    /**
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:64:        if (TaxConfig.isNormalLogging()) 
LOGGER.info("TreasuryManager initialized with {} treasury records", TREASURIES.size());
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:65:    }
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:66:
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:78:        saveData();
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:79:    }
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:80:
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:104:        return 
TREASURIES.getOrDefault(colonyId, 0);
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:105:    }
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:106:
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:193:        TREASURIES.put(colonyId, 
currentBalance + taken);
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:194:        saveData();
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:195:
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:196:        
player.sendSystemMessage(Component.literal(
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:256:        TREASURIES.put(colonyId, 
currentBalance - amount);
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:257:
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:258:        IColony colony = 
getColony(colonyId);
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:263:            TREASURIES.put(colonyId, 
currentBalance);
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:264:            player.sendSystemMessage(
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:265:                    
Component.literal("Failed to deliver funds to " + CurrencyService.label(destination)
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:271:        saveData();
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:272:
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:273:        
player.sendSystemMessage(Component.literal(
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:443:        TREASURIES.put(colonyId, 
newBalance);
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:444:        // Periodic save handled by 
WarSystem drain loop
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:445:
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:464:        TREASURIES.put(colonyId, 
newBalance);
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:465:        // Periodic save handled by 
WarSystem drain loop
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:466:        if 
(TaxConfig.isNormalLogging()) LOGGER.info("Deducted {} from colony {} treasury. New balance: {}", amount, colonyId, 
newBalance);
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:482:        TREASURIES.put(colonyId, 
newBalance);
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:483:        saveData();
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:484:        if 
(TaxConfig.isNormalLogging()) LOGGER.info("Added {} to colony {} treasury. New balance: {}", amount, colonyId, 
newBalance);
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:485:        return newBalance;
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:515:        TREASURIES.put(colonyId, 
currentBalance - cost);
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:516:        saveData();
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:517:        if 
(TaxConfig.isNormalLogging()) LOGGER.info("Colony {} purchased upgrade for {} from treasury. New balance: {}", 
colonyId, cost, currentBalance - cost);
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:518:        return true;
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:535:                TREASURIES.clear();
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:536:                
TREASURIES.putAll(loaded);
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:537:            }
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:538:            if 
(TaxConfig.isNormalLogging()) LOGGER.info("Loaded {} treasury records", TREASURIES.size());
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:539:        } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:540:            LOGGER.error("Failed to 
load treasury data: {}", e.getMessage());
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:544:    private static void saveData() {
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:545:        // Snapshot on the calling 
(main) thread, write off-thread + coalesced so a
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:546:        // deposit/withdraw/purchase 
storm no longer blocks ticks on disk I/O (audit H1).
> src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:547:        final Map<Integer, Integer> 
snapshot = new java.util.HashMap<>(TREASURIES);
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:548:        
net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("treasury", () -> writeData(snapshot));
  src\main\java\net\machiavelli\minecolonytax\economy\TreasuryManager.java:549:    }



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'git diff --check' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 589ms:
warning: in the working copy of 'AGENTS.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'CLAUDE.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeDamageShieldHandler.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/economy/TreasuryManager.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/mixin/WorldTickHandlerMixin.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/util/AsyncSaveExecutor.java', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'wiki/Configuration_Guide.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'wiki/War_Persistence.md', LF will be replaced by CRLF the next time Git touches it

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/**/*.java,src/main/java/net/machiavelli/minecolonytax/*.java -Pattern '/wnt sdm status|debug sdm status' | Select-Object Path,LineNumber,Line | Format-List" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 479ms:


Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\Curren
             cyService.java
LineNumber : 100
Line       :      * the tax is refunded (kept claimable) and the player is told to check {@code /wnt sdm status},

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\Curren
             cyService.java
LineNumber : 119
Line       :                   + claimedAmount + " in taxes was NOT lost and is still claimable. Run /wnt sdm status 
             for details.")

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\SDMSho
             pIntegration.java
LineNumber : 620
Line       :                     + "converted. Use /wnt sdm status to diagnose.", modPresent, mode, 
             isServerInstanceReady());




exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-ChildItem -Path $env:USERPROFILE'"\\.gradle\\caches\\modules-2\\files-2.1 -Recurse -Filter '*minecolonies*8186502*' -ErrorAction SilentlyContinue | Select-Object -First 20 FullName" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 1485ms:

FullName                                                                                                               
--------                                                                                                               
C:\Users\paulm\.gradle\caches\modules-2\files-2.1\curse.maven\minecolonies-245506\8186502\526de9939b70b03cfa6de5ca99...
C:\Users\paulm\.gradle\caches\modules-2\files-2.1\curse.maven\minecolonies-245506\8186502\ee7594a5025f8c21327a7d0d3c...



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-ChildItem -Path $env:USERPROFILE'"\\.gradle\\caches\\modules-2\\files-2.1\\curse.maven\\minecolonies-245506\\8186502 -Recurse -File | Select-Object FullName,Length | Format-List" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 337ms:


FullName : C:\Users\paulm\.gradle\caches\modules-2\files-2.1\curse.maven\minecolonies-245506\8186502\526de9939b70b03cfa
           6de5ca99cc3ded99cdb625\minecolonies-245506-8186502.jar
Length   : 77801209

FullName : C:\Users\paulm\.gradle\caches\modules-2\files-2.1\curse.maven\minecolonies-245506\8186502\ee7594a5025f8c2132
           7a7d0d3cfab2b47475478a\minecolonies-245506-8186502.pom
Length   : 432




exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'jar tf "$env:USERPROFILE'"\\.gradle\\caches\\modules-2\\files-2.1\\curse.maven\\minecolonies-245506\\8186502\\526de9939b70b03cfa6de5ca99cc3ded99cdb625\\minecolonies-245506-8186502.jar\" | Select-String -Pattern 'Building.*Guard|guardtower|GuardTower' | Select-Object -First 50" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
2026-06-05T04:12:41.687886Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 0.6 seconds
Output:

assets/minecolonies/blockstates/blockhutguardtower.json
assets/minecolonies/models/block/blockhutguardtower.json
assets/minecolonies/models/item/blockhutguardtower.json
blueprints/minecolonies/acacia/military/guardtower1.blueprint
blueprints/minecolonies/acacia/military/guardtower2.blueprint
blueprints/minecolonies/acacia/military/guardtower3.blueprint
blueprints/minecolonies/acacia/military/guardtower4.blueprint
blueprints/minecolonies/acacia/military/guardtower5.blueprint
blueprints/minecolonies/acacia/military/wallguardtower1.blueprint
blueprints/minecolonies/acacia/military/wallguardtower2.blueprint
blueprints/minecolonies/acacia/military/wallguardtower3.blueprint
blueprints/minecolonies/acacia/military/wallguardtower4.blueprint
blueprints/minecolonies/acacia/military/wallguardtower5.blueprint
blueprints/minecolonies/ancientathens/military/guardtower1.blueprint
blueprints/minecolonies/ancientathens/military/guardtower2.blueprint
blueprints/minecolonies/ancientathens/military/guardtower3.blueprint
blueprints/minecolonies/ancientathens/military/guardtower4.blueprint
blueprints/minecolonies/ancientathens/military/guardtower5.blueprint
blueprints/minecolonies/birch/military/guardtower1.blueprint
blueprints/minecolonies/birch/military/guardtower2.blueprint
blueprints/minecolonies/birch/military/guardtower3.blueprint
blueprints/minecolonies/birch/military/guardtower4.blueprint
blueprints/minecolonies/birch/military/guardtower5.blueprint
blueprints/minecolonies/birch/military/guardtoweralt1.blueprint
blueprints/minecolonies/birch/military/guardtoweralt2.blueprint
blueprints/minecolonies/birch/military/guardtoweralt3.blueprint
blueprints/minecolonies/birch/military/guardtoweralt4.blueprint
blueprints/minecolonies/birch/military/guardtoweralt5.blueprint
blueprints/minecolonies/caledonia/military/altguardtower1.blueprint
blueprints/minecolonies/caledonia/military/altguardtower2.blueprint
blueprints/minecolonies/caledonia/military/altguardtower3.blueprint
blueprints/minecolonies/caledonia/military/altguardtower4.blueprint
blueprints/minecolonies/caledonia/military/altguardtower5.blueprint
blueprints/minecolonies/caledonia/military/guardtower1.blueprint
blueprints/minecolonies/caledonia/military/guardtower2.blueprint
blueprints/minecolonies/caledonia/military/guardtower3.blueprint
blueprints/minecolonies/caledonia/military/guardtower4.blueprint
blueprints/minecolonies/caledonia/military/guardtower5.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower1.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower2.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower3.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower4.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower5.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower1.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower2.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower3.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower4.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower5.blueprint
blueprints/minecolonies/cavern/military/default/guardtower1.blueprint
blueprints/minecolonies/cavern/military/default/guardtower2.blueprint



 exited 1 in 601ms:

assets/minecolonies/blockstates/blockhutguardtower.json
assets/minecolonies/models/block/blockhutguardtower.json
assets/minecolonies/models/item/blockhutguardtower.json
blueprints/minecolonies/acacia/military/guardtower1.blueprint
blueprints/minecolonies/acacia/military/guardtower2.blueprint
blueprints/minecolonies/acacia/military/guardtower3.blueprint
blueprints/minecolonies/acacia/military/guardtower4.blueprint
blueprints/minecolonies/acacia/military/guardtower5.blueprint
blueprints/minecolonies/acacia/military/wallguardtower1.blueprint
blueprints/minecolonies/acacia/military/wallguardtower2.blueprint
blueprints/minecolonies/acacia/military/wallguardtower3.blueprint
blueprints/minecolonies/acacia/military/wallguardtower4.blueprint
blueprints/minecolonies/acacia/military/wallguardtower5.blueprint
blueprints/minecolonies/ancientathens/military/guardtower1.blueprint
blueprints/minecolonies/ancientathens/military/guardtower2.blueprint
blueprints/minecolonies/ancientathens/military/guardtower3.blueprint
blueprints/minecolonies/ancientathens/military/guardtower4.blueprint
blueprints/minecolonies/ancientathens/military/guardtower5.blueprint
blueprints/minecolonies/birch/military/guardtower1.blueprint
blueprints/minecolonies/birch/military/guardtower2.blueprint
blueprints/minecolonies/birch/military/guardtower3.blueprint
blueprints/minecolonies/birch/military/guardtower4.blueprint
blueprints/minecolonies/birch/military/guardtower5.blueprint
blueprints/minecolonies/birch/military/guardtoweralt1.blueprint
blueprints/minecolonies/birch/military/guardtoweralt2.blueprint
blueprints/minecolonies/birch/military/guardtoweralt3.blueprint
blueprints/minecolonies/birch/military/guardtoweralt4.blueprint
blueprints/minecolonies/birch/military/guardtoweralt5.blueprint
blueprints/minecolonies/caledonia/military/altguardtower1.blueprint
blueprints/minecolonies/caledonia/military/altguardtower2.blueprint
blueprints/minecolonies/caledonia/military/altguardtower3.blueprint
blueprints/minecolonies/caledonia/military/altguardtower4.blueprint
blueprints/minecolonies/caledonia/military/altguardtower5.blueprint
blueprints/minecolonies/caledonia/military/guardtower1.blueprint
blueprints/minecolonies/caledonia/military/guardtower2.blueprint
blueprints/minecolonies/caledonia/military/guardtower3.blueprint
blueprints/minecolonies/caledonia/military/guardtower4.blueprint
blueprints/minecolonies/caledonia/military/guardtower5.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower1.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower2.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower3.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower4.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower5.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower1.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower2.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower3.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower4.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower5.blueprint
blueprints/minecolonies/cavern/military/default/guardtower1.blueprint
blueprints/minecolonies/cavern/military/default/guardtower2.blueprint



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'jar tf "$env:USERPROFILE'"\\.gradle\\caches\\modules-2\\files-2.1\\curse.maven\\minecolonies-245506\\8186502\\526de9939b70b03cfa6de5ca99cc3ded99cdb625\\minecolonies-245506-8186502.jar\" | Select-String -Pattern 'com/minecolonies/core/colony/buildings/.*Guard|guardtower|GuardTower' | Select-Object -First 100" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
2026-06-05T04:12:49.527311Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 0.7 seconds
Output:

assets/minecolonies/blockstates/blockhutguardtower.json
assets/minecolonies/models/block/blockhutguardtower.json
assets/minecolonies/models/item/blockhutguardtower.json
blueprints/minecolonies/acacia/military/guardtower1.blueprint
blueprints/minecolonies/acacia/military/guardtower2.blueprint
blueprints/minecolonies/acacia/military/guardtower3.blueprint
blueprints/minecolonies/acacia/military/guardtower4.blueprint
blueprints/minecolonies/acacia/military/guardtower5.blueprint
blueprints/minecolonies/acacia/military/wallguardtower1.blueprint
blueprints/minecolonies/acacia/military/wallguardtower2.blueprint
blueprints/minecolonies/acacia/military/wallguardtower3.blueprint
blueprints/minecolonies/acacia/military/wallguardtower4.blueprint
blueprints/minecolonies/acacia/military/wallguardtower5.blueprint
blueprints/minecolonies/ancientathens/military/guardtower1.blueprint
blueprints/minecolonies/ancientathens/military/guardtower2.blueprint
blueprints/minecolonies/ancientathens/military/guardtower3.blueprint
blueprints/minecolonies/ancientathens/military/guardtower4.blueprint
blueprints/minecolonies/ancientathens/military/guardtower5.blueprint
blueprints/minecolonies/birch/military/guardtower1.blueprint
blueprints/minecolonies/birch/military/guardtower2.blueprint
blueprints/minecolonies/birch/military/guardtower3.blueprint
blueprints/minecolonies/birch/military/guardtower4.blueprint
blueprints/minecolonies/birch/military/guardtower5.blueprint
blueprints/minecolonies/birch/military/guardtoweralt1.blueprint
blueprints/minecolonies/birch/military/guardtoweralt2.blueprint
blueprints/minecolonies/birch/military/guardtoweralt3.blueprint
blueprints/minecolonies/birch/military/guardtoweralt4.blueprint
blueprints/minecolonies/birch/military/guardtoweralt5.blueprint
blueprints/minecolonies/caledonia/military/altguardtower1.blueprint
blueprints/minecolonies/caledonia/military/altguardtower2.blueprint
blueprints/minecolonies/caledonia/military/altguardtower3.blueprint
blueprints/minecolonies/caledonia/military/altguardtower4.blueprint
blueprints/minecolonies/caledonia/military/altguardtower5.blueprint
blueprints/minecolonies/caledonia/military/guardtower1.blueprint
blueprints/minecolonies/caledonia/military/guardtower2.blueprint
blueprints/minecolonies/caledonia/military/guardtower3.blueprint
blueprints/minecolonies/caledonia/military/guardtower4.blueprint
blueprints/minecolonies/caledonia/military/guardtower5.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower1.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower2.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower3.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower4.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower5.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower1.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower2.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower3.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower4.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower5.blueprint
blueprints/minecolonies/cavern/military/default/guardtower1.blueprint
blueprints/minecolonies/cavern/military/default/guardtower2.blueprint
blueprints/minecolonies/cavern/military/default/guardtower3.blueprint
blueprints/minecolonies/cavern/military/default/guardtower4.blueprint
blueprints/minecolonies/cavern/military/default/guardtower5.blueprint
blueprints/minecolonies/colonial/military/altguardtower1.blueprint
blueprints/minecolonies/colonial/military/altguardtower2.blueprint
blueprints/minecolonies/colonial/military/altguardtower3.blueprint
blueprints/minecolonies/colonial/military/altguardtower4.blueprint
blueprints/minecolonies/colonial/military/altguardtower5.blueprint
blueprints/minecolonies/colonial/military/guardtower1.blueprint
blueprints/minecolonies/colonial/military/guardtower2.blueprint
blueprints/minecolonies/colonial/military/guardtower3.blueprint
blueprints/minecolonies/colonial/military/guardtower4.blueprint
blueprints/minecolonies/colonial/military/guardtower5.blueprint
blueprints/minecolonies/darkoak/military/guardtower1.blueprint
blueprints/minecolonies/darkoak/military/guardtower2.blueprint
blueprints/minecolonies/darkoak/military/guardtower3.blueprint
blueprints/minecolonies/darkoak/military/guardtower4.blueprint
blueprints/minecolonies/darkoak/military/guardtower5.blueprint
blueprints/minecolonies/fortress/military/altguardtower1.blueprint
blueprints/minecolonies/fortress/military/altguardtower2.blueprint
blueprints/minecolonies/fortress/military/altguardtower3.blueprint
blueprints/minecolonies/fortress/military/altguardtower4.blueprint
blueprints/minecolonies/fortress/military/altguardtower5.blueprint
blueprints/minecolonies/fortress/military/guardtower1.blueprint
blueprints/minecolonies/fortress/military/guardtower2.blueprint
blueprints/minecolonies/fortress/military/guardtower3.blueprint
blueprints/minecolonies/fortress/military/guardtower4.blueprint
blueprints/minecolonies/fortress/military/guardtower5.blueprint
blueprints/minecolonies/incan/military/guardtower1.blueprint
blueprints/minecolonies/incan/military/guardtower2.blueprint
blueprints/minecolonies/incan/military/guardtower3.blueprint
blueprints/minecolonies/incan/military/guardtower4.blueprint
blueprints/minecolonies/incan/military/guardtower5.blueprint
blueprints/minecolonies/incan/military/guardtoweralt1.blueprint
blueprints/minecolonies/incan/military/guardtoweralt2.blueprint
blueprints/minecolonies/incan/military/guardtoweralt3.blueprint
blueprints/minecolonies/incan/military/guardtoweralt4.blueprint
blueprints/minecolonies/incan/military/guardtoweralt5.blueprint
blueprints/minecolonies/jungle/military/guardtower1.blueprint
blueprints/minecolonies/jungle/military/guardtower2.blueprint
blueprints/minecolonies/jungle/military/guardtower3.blueprint
blueprints/minecolonies/jungle/military/guardtower4.blueprint
blueprints/minecolonies/jungle/military/guardtower5.blueprint
blueprints/minecolonies/lostcity/military/guardtower1.blueprint
blueprints/minecolonies/lostcity/military/guardtower2.blueprint
blueprints/minecolonies/lostcity/military/guardtower3.blueprint
blueprints/minecolonies/lostcity/military/guardtower4.blueprint
blueprints/minecolonies/lostcity/military/guardtower5.blueprint
blueprints/minecolonies/medievalbirch/military/altguardtower1.blueprint
blueprints/minecolonies/medievalbirch/military/altguardtower2.blueprint



 exited 1 in 695ms:

assets/minecolonies/blockstates/blockhutguardtower.json
assets/minecolonies/models/block/blockhutguardtower.json
assets/minecolonies/models/item/blockhutguardtower.json
blueprints/minecolonies/acacia/military/guardtower1.blueprint
blueprints/minecolonies/acacia/military/guardtower2.blueprint
blueprints/minecolonies/acacia/military/guardtower3.blueprint
blueprints/minecolonies/acacia/military/guardtower4.blueprint
blueprints/minecolonies/acacia/military/guardtower5.blueprint
blueprints/minecolonies/acacia/military/wallguardtower1.blueprint
blueprints/minecolonies/acacia/military/wallguardtower2.blueprint
blueprints/minecolonies/acacia/military/wallguardtower3.blueprint
blueprints/minecolonies/acacia/military/wallguardtower4.blueprint
blueprints/minecolonies/acacia/military/wallguardtower5.blueprint
blueprints/minecolonies/ancientathens/military/guardtower1.blueprint
blueprints/minecolonies/ancientathens/military/guardtower2.blueprint
blueprints/minecolonies/ancientathens/military/guardtower3.blueprint
blueprints/minecolonies/ancientathens/military/guardtower4.blueprint
blueprints/minecolonies/ancientathens/military/guardtower5.blueprint
blueprints/minecolonies/birch/military/guardtower1.blueprint
blueprints/minecolonies/birch/military/guardtower2.blueprint
blueprints/minecolonies/birch/military/guardtower3.blueprint
blueprints/minecolonies/birch/military/guardtower4.blueprint
blueprints/minecolonies/birch/military/guardtower5.blueprint
blueprints/minecolonies/birch/military/guardtoweralt1.blueprint
blueprints/minecolonies/birch/military/guardtoweralt2.blueprint
blueprints/minecolonies/birch/military/guardtoweralt3.blueprint
blueprints/minecolonies/birch/military/guardtoweralt4.blueprint
blueprints/minecolonies/birch/military/guardtoweralt5.blueprint
blueprints/minecolonies/caledonia/military/altguardtower1.blueprint
blueprints/minecolonies/caledonia/military/altguardtower2.blueprint
blueprints/minecolonies/caledonia/military/altguardtower3.blueprint
blueprints/minecolonies/caledonia/military/altguardtower4.blueprint
blueprints/minecolonies/caledonia/military/altguardtower5.blueprint
blueprints/minecolonies/caledonia/military/guardtower1.blueprint
blueprints/minecolonies/caledonia/military/guardtower2.blueprint
blueprints/minecolonies/caledonia/military/guardtower3.blueprint
blueprints/minecolonies/caledonia/military/guardtower4.blueprint
blueprints/minecolonies/caledonia/military/guardtower5.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower1.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower2.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower3.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower4.blueprint
blueprints/minecolonies/cavern/military/alternative/barracksguardtower5.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower1.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower2.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower3.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower4.blueprint
blueprints/minecolonies/cavern/military/alternative/guardtower5.blueprint
blueprints/minecolonies/cavern/military/default/guardtower1.blueprint
blueprints/minecolonies/cavern/military/default/guardtower2.blueprint
blueprints/minecolonies/cavern/military/default/guardtower3.blueprint
blueprints/minecolonies/cavern/military/default/guardtower4.blueprint
blueprints/minecolonies/cavern/military/default/guardtower5.blueprint
blueprints/minecolonies/colonial/military/altguardtower1.blueprint
blueprints/minecolonies/colonial/military/altguardtower2.blueprint
blueprints/minecolonies/colonial/military/altguardtower3.blueprint
blueprints/minecolonies/colonial/military/altguardtower4.blueprint
blueprints/minecolonies/colonial/military/altguardtower5.blueprint
blueprints/minecolonies/colonial/military/guardtower1.blueprint
blueprints/minecolonies/colonial/military/guardtower2.blueprint
blueprints/minecolonies/colonial/military/guardtower3.blueprint
blueprints/minecolonies/colonial/military/guardtower4.blueprint
blueprints/minecolonies/colonial/military/guardtower5.blueprint
blueprints/minecolonies/darkoak/military/guardtower1.blueprint
blueprints/minecolonies/darkoak/military/guardtower2.blueprint
blueprints/minecolonies/darkoak/military/guardtower3.blueprint
blueprints/minecolonies/darkoak/military/guardtower4.blueprint
blueprints/minecolonies/darkoak/military/guardtower5.blueprint
blueprints/minecolonies/fortress/military/altguardtower1.blueprint
blueprints/minecolonies/fortress/military/altguardtower2.blueprint
blueprints/minecolonies/fortress/military/altguardtower3.blueprint
blueprints/minecolonies/fortress/military/altguardtower4.blueprint
blueprints/minecolonies/fortress/military/altguardtower5.blueprint
blueprints/minecolonies/fortress/military/guardtower1.blueprint
blueprints/minecolonies/fortress/military/guardtower2.blueprint
blueprints/minecolonies/fortress/military/guardtower3.blueprint
blueprints/minecolonies/fortress/military/guardtower4.blueprint
blueprints/minecolonies/fortress/military/guardtower5.blueprint
blueprints/minecolonies/incan/military/guardtower1.blueprint
blueprints/minecolonies/incan/military/guardtower2.blueprint
blueprints/minecolonies/incan/military/guardtower3.blueprint
blueprints/minecolonies/incan/military/guardtower4.blueprint
blueprints/minecolonies/incan/military/guardtower5.blueprint
blueprints/minecolonies/incan/military/guardtoweralt1.blueprint
blueprints/minecolonies/incan/military/guardtoweralt2.blueprint
blueprints/minecolonies/incan/military/guardtoweralt3.blueprint
blueprints/minecolonies/incan/military/guardtoweralt4.blueprint
blueprints/minecolonies/incan/military/guardtoweralt5.blueprint
blueprints/minecolonies/jungle/military/guardtower1.blueprint
blueprints/minecolonies/jungle/military/guardtower2.blueprint
blueprints/minecolonies/jungle/military/guardtower3.blueprint
blueprints/minecolonies/jungle/military/guardtower4.blueprint
blueprints/minecolonies/jungle/military/guardtower5.blueprint
blueprints/minecolonies/lostcity/military/guardtower1.blueprint
blueprints/minecolonies/lostcity/military/guardtower2.blueprint
blueprints/minecolonies/lostcity/military/guardtower3.blueprint
blueprints/minecolonies/lostcity/military/guardtower4.blueprint
blueprints/minecolonies/lostcity/military/guardtower5.blueprint
blueprints/minecolonies/medievalbirch/military/altguardtower1.blueprint
blueprints/minecolonies/medievalbirch/military/altguardtower2.blueprint



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'jar tf "$env:USERPROFILE'"\\.gradle\\caches\\modules-2\\files-2.1\\curse.maven\\minecolonies-245506\\8186502\\526de9939b70b03cfa6de5ca99cc3ded99cdb625\\minecolonies-245506-8186502.jar\" | Select-String -Pattern 'core/colony/buildings' | Select-Object -First 30" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
2026-06-05T04:12:56.951500Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 0.6 seconds
Output:

com/minecolonies/core/colony/buildings/
com/minecolonies/core/colony/buildings/AbstractBuilding.class
com/minecolonies/core/colony/buildings/AbstractBuildingContainer.class
com/minecolonies/core/colony/buildings/AbstractBuildingGuards$View.class
com/minecolonies/core/colony/buildings/AbstractBuildingGuards.class
com/minecolonies/core/colony/buildings/AbstractBuildingStructureBuilder.class
com/minecolonies/core/colony/buildings/AbstractSchematicProvider.class
com/minecolonies/core/colony/buildings/BuildingMysticalSite$View.class
com/minecolonies/core/colony/buildings/BuildingMysticalSite.class
com/minecolonies/core/colony/buildings/DefaultBuildingInstance.class
com/minecolonies/core/colony/buildings/modules/
com/minecolonies/core/colony/buildings/modules/AbstractAssignedCitizenModule.class
com/minecolonies/core/colony/buildings/modules/AbstractCraftingBuildingModule$Brewing.class
com/minecolonies/core/colony/buildings/modules/AbstractCraftingBuildingModule$Crafting.class
com/minecolonies/core/colony/buildings/modules/AbstractCraftingBuildingModule$Custom.class
com/minecolonies/core/colony/buildings/modules/AbstractCraftingBuildingModule$Smelting.class
com/minecolonies/core/colony/buildings/modules/AbstractCraftingBuildingModule.class
com/minecolonies/core/colony/buildings/modules/AbstractDOCraftingBuildingModule.class
com/minecolonies/core/colony/buildings/modules/AnimalHerdingModule.class
com/minecolonies/core/colony/buildings/modules/BarracksStatisticsModule.class
com/minecolonies/core/colony/buildings/modules/BedHandlingModule.class
com/minecolonies/core/colony/buildings/modules/BuildingExtensionsModule.class
com/minecolonies/core/colony/buildings/modules/BuildingModules.class
com/minecolonies/core/colony/buildings/modules/BuildingResourcesModule.class
com/minecolonies/core/colony/buildings/modules/BuildingStatisticsModule.class
com/minecolonies/core/colony/buildings/modules/ChildrenBuildingModule.class
com/minecolonies/core/colony/buildings/modules/ColonyConnectionModule.class
com/minecolonies/core/colony/buildings/modules/CourierAssignmentModule.class
com/minecolonies/core/colony/buildings/modules/CraftingWorkerBuildingModule.class
com/minecolonies/core/colony/buildings/modules/DeliverymanAssignmentModule.class



 exited 1 in 606ms:

com/minecolonies/core/colony/buildings/
com/minecolonies/core/colony/buildings/AbstractBuilding.class
com/minecolonies/core/colony/buildings/AbstractBuildingContainer.class
com/minecolonies/core/colony/buildings/AbstractBuildingGuards$View.class
com/minecolonies/core/colony/buildings/AbstractBuildingGuards.class
com/minecolonies/core/colony/buildings/AbstractBuildingStructureBuilder.class
com/minecolonies/core/colony/buildings/AbstractSchematicProvider.class
com/minecolonies/core/colony/buildings/BuildingMysticalSite$View.class
com/minecolonies/core/colony/buildings/BuildingMysticalSite.class
com/minecolonies/core/colony/buildings/DefaultBuildingInstance.class
com/minecolonies/core/colony/buildings/modules/
com/minecolonies/core/colony/buildings/modules/AbstractAssignedCitizenModule.class
com/minecolonies/core/colony/buildings/modules/AbstractCraftingBuildingModule$Brewing.class
com/minecolonies/core/colony/buildings/modules/AbstractCraftingBuildingModule$Crafting.class
com/minecolonies/core/colony/buildings/modules/AbstractCraftingBuildingModule$Custom.class
com/minecolonies/core/colony/buildings/modules/AbstractCraftingBuildingModule$Smelting.class
com/minecolonies/core/colony/buildings/modules/AbstractCraftingBuildingModule.class
com/minecolonies/core/colony/buildings/modules/AbstractDOCraftingBuildingModule.class
com/minecolonies/core/colony/buildings/modules/AnimalHerdingModule.class
com/minecolonies/core/colony/buildings/modules/BarracksStatisticsModule.class
com/minecolonies/core/colony/buildings/modules/BedHandlingModule.class
com/minecolonies/core/colony/buildings/modules/BuildingExtensionsModule.class
com/minecolonies/core/colony/buildings/modules/BuildingModules.class
com/minecolonies/core/colony/buildings/modules/BuildingResourcesModule.class
com/minecolonies/core/colony/buildings/modules/BuildingStatisticsModule.class
com/minecolonies/core/colony/buildings/modules/ChildrenBuildingModule.class
com/minecolonies/core/colony/buildings/modules/ColonyConnectionModule.class
com/minecolonies/core/colony/buildings/modules/CourierAssignmentModule.class
com/minecolonies/core/colony/buildings/modules/CraftingWorkerBuildingModule.class
com/minecolonies/core/colony/buildings/modules/DeliverymanAssignmentModule.class



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'jar tf "$env:USERPROFILE'"\\.gradle\\caches\\modules-2\\files-2.1\\curse.maven\\minecolonies-245506\\8186502\\526de9939b70b03cfa6de5ca99cc3ded99cdb625\\minecolonies-245506-8186502.jar\" | Select-String -Pattern 'core/colony/buildings/.*Guard|core/colony/buildings/workerbuildings/.*Guard' | Select-Object -First 50" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 641ms:

com/minecolonies/core/colony/buildings/AbstractBuildingGuards$View.class
com/minecolonies/core/colony/buildings/AbstractBuildingGuards.class
com/minecolonies/core/colony/buildings/modules/GuardBuildingModule.class
com/minecolonies/core/colony/buildings/modules/settings/GuardFollowModeSetting.class
com/minecolonies/core/colony/buildings/modules/settings/GuardPatrolModeSetting.class
com/minecolonies/core/colony/buildings/modules/settings/GuardTaskSetting.class
com/minecolonies/core/colony/buildings/modules/settings/SettingsFactories$GuardFollowModeSettingFactory.class
com/minecolonies/core/colony/buildings/modules/settings/SettingsFactories$GuardPatrolModeSettingFactory.class
com/minecolonies/core/colony/buildings/modules/settings/SettingsFactories$GuardTaskSettingFactory.class
com/minecolonies/core/colony/buildings/moduleviews/MinerGuardAssignModuleView.class
com/minecolonies/core/colony/buildings/registry/GuardTypeDataManager.class
com/minecolonies/core/colony/buildings/workerbuildings/BuildingGuardTower$View.class
com/minecolonies/core/colony/buildings/workerbuildings/BuildingGuardTower.class



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content -Path src/main/resources/META-INF/mods.toml' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 319ms:
# This is an example mods.toml file. It contains the data relating to the loading mods.
# There are several mandatory fields (#mandatory), and many more that are optional (#optional).
# The overall format is standard TOML format, v0.5.0.
# Note that there are a couple of TOML lists in this file.
# Find more information on toml format here:  https://github.com/toml-lang/toml
# The name of the mod loader type to load - for regular FML @Mod mods it should be javafml
modLoader="javafml" #mandatory
# A version range to match for said mod loader - for regular FML @Mod it will be the forge version
loaderVersion="1.20.1" #mandatory This is typically bumped every Minecraft version by Forge. See our download page for lists of versions.
# The license for you mod. This is mandatory metadata and allows for easier comprehension of your redistributive properties.
# Review your options at https://choosealicense.com/. All rights reserved is the default copyright stance, and is thus the default here.
license="MIT"
# A URL to refer people to when problems occur with this mod
#issueTrackerURL="https://change.me.to.your.issue.tracker.example.invalid/" #optional
# A list of mods - how many allowed here is determined by the individual mod loader
[[mods]] #mandatory
# The modid of the mod
modId="minecolonytax" #mandatory
# The version number of the mod
version="${mod_version}" #mandatory
# A display name for the mod
displayName="WarNTaxes" #mandatory
# A URL to query for updates for this mod. See the JSON update specification https://docs.minecraftforge.net/en/latest/misc/updatechecker/
#updateJSONURL="https://change.me.example.invalid/updates.json" #optional
# A URL for the "homepage" for this mod, displayed in the mod UI
#displayURL="https://change.me.to.your.mods.homepage.example.invalid/" #optional
# A file name (in the root of the mod JAR) containing a logo for display
#logoFile="examplemod.png" #optional
# A text field displayed in the mod UI
#credits="" #optional
# A text field displayed in the mod UI
authors="${mod_authors}" #optional
# Display Test controls the display for your mod in the server connection screen
# MATCH_VERSION means that your mod will cause a red X if the versions on client and server differ. This is the default behaviour and should be what you choose if you have server and client elements to your mod.
# IGNORE_SERVER_VERSION means that your mod will not cause a red X if it's present on the server but not on the client. This is what you should use if you're a server only mod.
# IGNORE_ALL_VERSION means that your mod will not cause a red X if it's present on the client or the server. This is a special case and should only be used if your mod has no server component.
# NONE means that no display test is set on your mod. You need to do this yourself, see IExtensionPoint.DisplayTest for more information. You can define any scheme you wish with this value.
# IMPORTANT NOTE: this is NOT an instruction as to which environments (CLIENT or DEDICATED SERVER) your mod loads on. Your mod should load (and maybe do nothing!) whereever it finds itself.
#displayTest="MATCH_VERSION" # MATCH_VERSION is the default if nothing is specified (#optional)

# The description text for the mod (multi line!) (#mandatory)
description='''${mod_description}'''
# A dependency - use the . to indicate dependency for a specific modid. Dependencies are optional.
[[dependencies.${mod_id}]] #optional
    # the modid of the dependency
    modId="forge" #mandatory
    # Does this dependency have to exist - if not, ordering below must be specified
    mandatory=true #mandatory
    # The version range of the dependency
    versionRange="${forge_version_range}" #mandatory
    # An ordering relationship for the dependency - BEFORE or AFTER required if the dependency is not mandatory
    # BEFORE - This mod is loaded BEFORE the dependency
    # AFTER - This mod is loaded AFTER the dependency
    ordering="NONE"
    # Side this dependency is applied on - BOTH, CLIENT, or SERVER
    side="BOTH"
# Here's another dependency
[[dependencies.${mod_id}]]
    modId="minecraft"
    mandatory=true
    # This version range declares a minimum of the current minecraft version up to but not including the next major version
    versionRange="${minecraft_version_range}"
    ordering="NONE"
    side="BOTH"

# MineColonies is a HARD requirement â€” the mod is an addon to it and does not
# function without it (all colony/treasury/war/spy systems route through the
# MineColonies API surface). Version range is broad to allow patch updates of
# MineColonies on the 1.20.1 line.
[[dependencies.${mod_id}]]
    modId="minecolonies"
    mandatory=true
    versionRange="[1.1.700,)"
    ordering="AFTER"
    side="BOTH"

# Optional integrations â€” present only when the user installs them.
# Hard runtime imports of these mods are gated by ModList.isLoaded() in the code,
# but declaring them here lets Forge surface accurate "dependency not found"
# errors when the user mis-installs an integration.
[[dependencies.${mod_id}]]
    modId="ftbteams"
    mandatory=false
    versionRange="[1900,)"
    ordering="AFTER"
    side="BOTH"

[[dependencies.${mod_id}]]
    modId="journeymap"
    mandatory=false
    versionRange="[5.9,)"
    ordering="AFTER"
    side="BOTH"

[[dependencies.${mod_id}]]
    modId="sdmshop"
    mandatory=false
    versionRange="[1.0,)"
    ordering="AFTER"
    side="BOTH"

[[dependencies.${mod_id}]]
    modId="patchouli"
    mandatory=false
    versionRange="[1.20.1,)"
    ordering="AFTER"
    side="BOTH"

# Features are specific properties of the game environment, that you may want to declare you require. This example declares
# that your mod requires GL version 3.2 or higher. Other features will be added. They are side aware so declaring this won't
# stop your mod loading on the server for example.
#[features.${mod_id}]
#openGLVersion="[3.2,)"

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/TaxManager.java -Pattern 'generateTaxesForAllColonies|try \\{' -Context 0,5 | Select-Object -First 50" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 447ms:

> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:90:        try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:91:            
net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.endAllClaimingRaids();
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:92:        } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:93:            LOGGER.error("Error ending claiming raids 
on shutdown", e);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:94:        }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:95:    }
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:122:                        try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:123:                            
net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:124:                        } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:125:                            LOGGER.error("Failed 
automatic null owner fix", e);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:126:                        }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:127:                    }
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:136:                        try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:137:                            
net.machiavelli.minecolonytax.espionage.SpyManager.tick();
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:138:                        } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:139:                            LOGGER.error("Error 
during SpyManager tick", e);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:140:                        }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:141:                    }
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:175:            try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:176:                LOGGER.debug("Running periodic 
[abandoned] entries cleanup...");
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:177:                
net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.cleanupAllColoniesAbandonedEntries();
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:178:            } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:179:                LOGGER.error("Error during periodic 
[abandoned] cleanup", e);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:180:            }
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:188:            try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:189:                if (serverInstance == null)
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:190:                    return;
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:191:
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:192:                // Get all colonies and check if any 
abandoned ones have new officers
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:193:                for 
(com.minecolonies.api.colony.IColony colony : com.minecolonies.api.IMinecoloniesAPI.getInstance()
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:195:                    try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:196:                        // Only check colonies that 
are currently marked as abandoned
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:197:                        if 
(net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.isColonyAbandoned(colony)) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:198:                            
net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.checkForNewOfficers(colony);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:199:                        }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:200:                    } catch (Exception e) {
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:239:                
TaxManager.generateTaxesForAllColonies();
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:240:            }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:241:        }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:242:
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:243:        private void checkColonyAbandonment() {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:244:            if (serverInstance != null) {
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:245:                try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:246:                    
net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:247:                            
.checkColoniesForAbandonment(serverInstance);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:248:                } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:249:                    LOGGER.error("Error during 
colony abandonment check", e);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:250:                }
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:354:            // generateTaxesForAllColonies
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:355:        } else {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:356:            // Only log max limit reached once per 
colony per iteration
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:357:            // This will be handled in 
generateTaxesForAllColonies method
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:358:        }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:359:    }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:360:
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:361:    public static void deductColonyTax(IColony 
colony, double percentage) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:362:        int currentTax = 
colonyTaxMap.getOrDefault(colony.getID(), 0);
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:397:        try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:398:            double totalHappiness = 0.0;
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:399:            int adultCitizenCount = 0;
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:400:
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:401:            for 
(com.minecolonies.api.colony.ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:402:                if (citizen != null && 
!citizen.isChild()) {
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:403:                    try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:404:                        // Access happiness handler 
through the citizen data interface (no cast needed)
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:405:                        
com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenHappinessHandler happinessHandler = citizen
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:406:                                
.getCitizenHappinessHandler();
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:407:
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:408:                        if (happinessHandler != 
null) {
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:439:    public static void generateTaxesForAllColonies() 
{
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:440:        if (serverInstance != null) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:441:            
serverInstance.getAllLevels().forEach(world -> {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:442:                IColonyManager colonyManager = 
IMinecoloniesAPI.getInstance().getColonyManager();
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:443:                
colonyManager.getColonies(world).forEach(colony -> {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:444:                    int colonyId = colony.getID();
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1010:                        try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1011:                            
RandomEventManager.onTaxCycle(colony);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1012:                        } catch (Exception ex) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1013:                            LOGGER.error("Random 
event tick failed for colony {}", colony.getID(), ex);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1014:                        }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1015:                    }
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1045:                try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1046:                    
colony.getCitizenManager().injectModifier(
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1047:                        new 
ExpirationBasedHappinessModifier(
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1048:                            "wnt_debt_misery",
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1049:                            1.5,
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1050:                            new 
StaticHappinessSupplier(happinessFactor),
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1063:                    try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1064:                        
net.machiavelli.minecolonytax.events.random.RandomEventManager.forceTriggerEvent(
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1065:                            colony, 
net.machiavelli.minecolonytax.events.random.RandomEventType.BANDIT_HARASSMENT);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1066:                        
net.machiavelli.minecolonytax.events.random.RandomEventManager.forceTriggerEvent(
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1067:                            colony, 
net.machiavelli.minecolonytax.events.random.RandomEventType.GUARD_DESERTION);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1068:                        if 
(TaxConfig.isNormalLogging())
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1086:                try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1087:                    
net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1088:                        
.abandonColonyForDebt(colony, serverInstance);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1089:                    
CONSECUTIVE_DEBT_CYCLES.remove(colonyId); // reset after abandonment
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1090:                } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1091:                    LOGGER.error("Failed to abandon 
colony {} for debt: {}", colony.getName(), e.getMessage());
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1172:            try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1173:                try (FileWriter writer = new 
FileWriter(tmp)) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1174:                    GSON.toJson(snapshot, writer);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1175:                }
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1176:                try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1177:                    
java.nio.file.Files.move(tmp.toPath(), file.toPath(),
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1178:                            
java.nio.file.StandardCopyOption.ATOMIC_MOVE,
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1179:                            
java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1180:                } catch 
(java.nio.file.AtomicMoveNotSupportedException ex) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1181:                    
java.nio.file.Files.move(tmp.toPath(), file.toPath(),
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1190:                    try { tmp.delete(); } catch 
(Exception ignored) { /* nothing else to do */ }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1191:                }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1192:            }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1193:        });
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1194:    }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1195:
> src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1321:                try {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1322:                    timestampFile.delete();
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1323:                } catch (Exception ex) {
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1324:                    LOGGER.debug("Could not delete 
corrupted timestamp file: {}", ex.getMessage());
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1325:                }
  src\main\java\net\machiavelli\minecolonytax\TaxManager.java:1326:            }



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'javap -classpath "$env:USERPROFILE'"\\.gradle\\caches\\modules-2\\files-2.1\\curse.maven\\minecolonies-245506\\8186502\\526de9939b70b03cfa6de5ca99cc3ded99cdb625\\minecolonies-245506-8186502.jar\" -c com.minecolonies.core.colony.permissions.Permissions | Select-String -Pattern 'removePlayer|setOwner|getOwner' -Context 0,40" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 932ms:

>        1: invokevirtual #449                // Method getOwnerEntry:()Ljava/util/Map$Entry;
         4: astore_1
         5: aload_1
         6: ifnonnull     89
         9: aload_0
        10: getfield      #106                // Field ownerUUID:Ljava/util/UUID;
        13: ifnull        89
        16: invokestatic  #393                // Method 
net/minecraftforge/server/ServerLifecycleHooks.getCurrentServer:()Lnet/minecraft/server/MinecraftServer;
        19: invokevirtual #399                // Method 
net/minecraft/server/MinecraftServer.m_129927_:()Lnet/minecraft/server/players/GameProfileCache;
        22: aload_0
        23: getfield      #106                // Field ownerUUID:Ljava/util/UUID;
        26: invokevirtual #405                // Method 
net/minecraft/server/players/GameProfileCache.m_11002_:(Ljava/util/UUID;)Ljava/util/Optional;
        29: aconst_null
        30: invokevirtual #410                // Method 
java/util/Optional.orElse:(Ljava/lang/Object;)Ljava/lang/Object;
        33: checkcast     #412                // class com/mojang/authlib/GameProfile
        36: astore_2
        37: aload_2
        38: ifnull        86
        41: aload_0
        42: getfield      #98                 // Field players:Ljava/util/Map;
        45: aload_0
        46: getfield      #106                // Field ownerUUID:Ljava/util/UUID;
        49: new           #414                // class com/minecolonies/api/colony/permissions/ColonyPlayer
        52: dup
        53: aload_0
        54: getfield      #106                // Field ownerUUID:Ljava/util/UUID;
        57: aload_2
        58: invokevirtual #417                // Method com/mojang/authlib/GameProfile.getName:()Ljava/lang/String;
        61: aload_0
        62: getfield      #93                 // Field ranks:Ljava/util/Map;
        65: iconst_0
        66: invokestatic  #185                // Method java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
        69: invokeinterface #372,  2          // InterfaceMethod 
java/util/Map.get:(Ljava/lang/Object;)Ljava/lang/Object;
        74: checkcast     #169                // class com/minecolonies/api/colony/permissions/Rank
        77: invokespecial #420                // Method com/minecolonies/api/colony/permissions/ColonyPlayer."<init>":(
Ljava/util/UUID;Ljava/lang/String;Lcom/minecolonies/api/colony/permissions/Rank;)V
        80: invokeinterface #189,  3          // InterfaceMethod 
java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        85: pop
        86: goto          97
        89: aload_1
        90: ifnonnull     97
        93: aload_0
>       94: invokevirtual #452                // Method setOwnerAbandoned:()V
        97: return
  
>   public java.util.Map$Entry<java.util.UUID, com.minecolonies.api.colony.permissions.ColonyPlayer> getOwnerEntry();
      Code:
         0: aload_0
         1: getfield      #98                 // Field players:Ljava/util/Map;
         4: invokeinterface #460,  1          // InterfaceMethod java/util/Map.entrySet:()Ljava/util/Set;
         9: invokeinterface #466,  1          // InterfaceMethod java/util/Set.iterator:()Ljava/util/Iterator;
        14: astore_1
        15: aload_1
        16: invokeinterface #471,  1          // InterfaceMethod java/util/Iterator.hasNext:()Z
        21: ifeq          57
        24: aload_1
        25: invokeinterface #475,  1          // InterfaceMethod java/util/Iterator.next:()Ljava/lang/Object;
        30: checkcast     #11                 // class java/util/Map$Entry
        33: astore_2
        34: aload_2
        35: invokeinterface #478,  1          // InterfaceMethod java/util/Map$Entry.getValue:()Ljava/lang/Object;
        40: checkcast     #414                // class com/minecolonies/api/colony/permissions/ColonyPlayer
        43: invokevirtual #481                // Method 
com/minecolonies/api/colony/permissions/ColonyPlayer.getRank:()Lcom/minecolonies/api/colony/permissions/Rank;
        46: invokevirtual #179                // Method com/minecolonies/api/colony/permissions/Rank.getId:()I
        49: ifne          54
        52: aload_2
        53: areturn
        54: goto          15
        57: aconst_null
        58: areturn
  
>   public boolean setOwner(net.minecraft.world.entity.player.Player);
      Code:
         0: aload_0
         1: getfield      #98                 // Field players:Ljava/util/Map;
         4: aload_0
>        5: invokevirtual #488                // Method getOwner:()Ljava/util/UUID;
         8: invokeinterface #491,  2          // InterfaceMethod 
java/util/Map.remove:(Ljava/lang/Object;)Ljava/lang/Object;
        13: pop
        14: aload_0
        15: aload_1
        16: invokevirtual #497                // Method 
net/minecraft/world/entity/player/Player.m_7755_:()Lnet/minecraft/network/chat/Component;
        19: invokeinterface #502,  1          // InterfaceMethod 
net/minecraft/network/chat/Component.getString:()Ljava/lang/String;
        24: putfield      #104                // Field ownerName:Ljava/lang/String;
        27: aload_0
        28: aload_1
        29: invokevirtual #505                // Method 
net/minecraft/world/entity/player/Player.m_20148_:()Ljava/util/UUID;
        32: putfield      #106                // Field ownerUUID:Ljava/util/UUID;
        35: aload_0
        36: getfield      #98                 // Field players:Ljava/util/Map;
        39: aload_0
        40: getfield      #106                // Field ownerUUID:Ljava/util/UUID;
        43: new           #414                // class com/minecolonies/api/colony/permissions/ColonyPlayer
        46: dup
        47: aload_0
        48: getfield      #106                // Field ownerUUID:Ljava/util/UUID;
        51: aload_1
        52: invokevirtual #497                // Method 
net/minecraft/world/entity/player/Player.m_7755_:()Lnet/minecraft/network/chat/Component;
        55: invokeinterface #502,  1          // InterfaceMethod 
net/minecraft/network/chat/Component.getString:()Ljava/lang/String;
        60: aload_0
        61: getfield      #93                 // Field ranks:Ljava/util/Map;
        64: iconst_0
        65: invokestatic  #185                // Method java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
        68: invokeinterface #372,  2          // InterfaceMethod 
java/util/Map.get:(Ljava/lang/Object;)Ljava/lang/Object;
        73: checkcast     #169                // class com/minecolonies/api/colony/permissions/Rank
        76: invokespecial #420                // Method com/minecolonies/api/colony/permissions/ColonyPlayer."<init>":(
Ljava/util/UUID;Ljava/lang/String;Lcom/minecolonies/api/colony/permissions/Rank;)V
        79: invokeinterface #189,  3          // InterfaceMethod 
java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        84: pop
        85: aload_0
        86: iconst_0
        87: putfield      #108                // Field fullyAbandoned:Z
        90: aload_0
        91: invokevirtual #311                // Method markDirty:()V
        94: iconst_1
        95: ireturn
  
>   public void setOwnerAbandoned();
      Code:
         0: aload_0
         1: getfield      #98                 // Field players:Ljava/util/Map;
         4: aload_0
         5: getfield      #106                // Field ownerUUID:Ljava/util/UUID;
         8: invokeinterface #491,  2          // InterfaceMethod 
java/util/Map.remove:(Ljava/lang/Object;)Ljava/lang/Object;
        13: pop
        14: aload_0
        15: ldc_w         #508                // String [abandoned]
        18: putfield      #104                // Field ownerName:Ljava/lang/String;
        21: aload_0
        22: invokestatic  #511                // Method java/util/UUID.randomUUID:()Ljava/util/UUID;
        25: putfield      #106                // Field ownerUUID:Ljava/util/UUID;
        28: aload_0
        29: getfield      #98                 // Field players:Ljava/util/Map;
        32: aload_0
        33: getfield      #106                // Field ownerUUID:Ljava/util/UUID;
        36: new           #414                // class com/minecolonies/api/colony/permissions/ColonyPlayer
        39: dup
        40: aload_0
        41: getfield      #106                // Field ownerUUID:Ljava/util/UUID;
        44: aload_0
        45: getfield      #104                // Field ownerName:Ljava/lang/String;
        48: aload_0
        49: getfield      #93                 // Field ranks:Ljava/util/Map;
        52: iconst_0
        53: invokestatic  #185                // Method java/lang/Integer.valueOf:(I)Ljava/lang/Integer;
        56: invokeinterface #372,  2          // InterfaceMethod 
java/util/Map.get:(Ljava/lang/Object;)Ljava/lang/Object;
        61: checkcast     #169                // class com/minecolonies/api/colony/permissions/Rank
        64: invokespecial #420                // Method com/minecolonies/api/colony/permissions/ColonyPlayer."<init>":(
Ljava/util/UUID;Ljava/lang/String;Lcom/minecolonies/api/colony/permissions/Rank;)V
        67: invokeinterface #189,  3          // InterfaceMethod 
java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
        72: pop
        73: aload_0
        74: invokevirtual #426                // Method checkFullyAbandoned:()V
        77: aload_0
        78: invokevirtual #311                // Method markDirty:()V
        81: return
  
>   public java.util.UUID getOwner();
      Code:
         0: aload_0
         1: getfield      #106                // Field ownerUUID:Ljava/util/UUID;
         4: ifnonnull     36
         7: aload_0
>        8: invokevirtual #449                // Method getOwnerEntry:()Ljava/util/Map$Entry;
        11: astore_1
        12: aload_1
        13: ifnull        32
        16: aload_0
        17: aload_1
        18: invokeinterface #514,  1          // InterfaceMethod java/util/Map$Entry.getKey:()Ljava/lang/Object;
        23: checkcast     #380                // class java/util/UUID
        26: putfield      #106                // Field ownerUUID:Ljava/util/UUID;
        29: goto          36
        32: aload_0
        33: invokevirtual #429                // Method restoreOwnerIfNull:()V
        36: aload_0
        37: getfield      #106                // Field ownerUUID:Ljava/util/UUID;
        40: areturn
  
    public void savePermissions(net.minecraft.nbt.CompoundTag);
      Code:
         0: new           #348                // class net/minecraft/nbt/ListTag
         3: dup
         4: invokespecial #516                // Method net/minecraft/nbt/ListTag."<init>":()V
         7: astore_2
         8: aload_0
         9: getfield      #93                 // Field ranks:Ljava/util/Map;
        12: invokeinterface #519,  1          // InterfaceMethod java/util/Map.values:()Ljava/util/Collection;
        17: invokeinterface #522,  1          // InterfaceMethod java/util/Collection.iterator:()Ljava/util/Iterator;
        22: astore_3
        23: aload_3
        24: invokeinterface #471,  1          // InterfaceMethod java/util/Iterator.hasNext:()Z
        29: ifeq          123
        32: aload_3
        33: invokeinterface #475,  1          // InterfaceMethod java/util/Iterator.next:()Ljava/lang/Object;
        38: checkcast     #169                // class com/minecolonies/api/colony/permissions/Rank
        41: astore        4
        43: new           #334                // class net/minecraft/nbt/CompoundTag
        46: dup
        47: invokespecial #523                // Method net/minecraft/nbt/CompoundTag."<init>":()V
        50: astore        5
        52: aload         5
        54: ldc           #31                 // String id
        56: aload         4
>   public boolean removePlayer(java.util.UUID);
      Code:
         0: aload_0
         1: getfield      #98                 // Field players:Ljava/util/Map;
         4: aload_1
         5: invokeinterface #372,  2          // InterfaceMethod 
java/util/Map.get:(Ljava/lang/Object;)Ljava/lang/Object;
        10: checkcast     #414                // class com/minecolonies/api/colony/permissions/ColonyPlayer
        13: astore_2
        14: aload_2
        15: ifnull        51
        18: aload_2
        19: invokevirtual #481                // Method 
com/minecolonies/api/colony/permissions/ColonyPlayer.getRank:()Lcom/minecolonies/api/colony/permissions/Rank;
        22: invokevirtual #179                // Method com/minecolonies/api/colony/permissions/Rank.getId:()I
        25: ifeq          51
        28: aload_0
        29: getfield      #98                 // Field players:Ljava/util/Map;
        32: aload_1
        33: invokeinterface #491,  2          // InterfaceMethod 
java/util/Map.remove:(Ljava/lang/Object;)Ljava/lang/Object;
        38: ifnull        51
        41: aload_0
        42: invokevirtual #426                // Method checkFullyAbandoned:()V
        45: aload_0
        46: invokevirtual #311                // Method markDirty:()V
        49: iconst_1
        50: ireturn
        51: iconst_0
        52: ireturn
  
>   public java.lang.String getOwnerName();
      Code:
         0: aload_0
         1: getfield      #104                // Field ownerName:Ljava/lang/String;
         4: invokevirtual #423                // Method java/lang/String.isEmpty:()Z
         7: ifeq          35
        10: aload_0
>       11: invokevirtual #449                // Method getOwnerEntry:()Ljava/util/Map$Entry;
        14: astore_1
        15: aload_1
        16: ifnull        35
        19: aload_0
        20: aload_1
        21: invokeinterface #478,  1          // InterfaceMethod java/util/Map$Entry.getValue:()Ljava/lang/Object;
        26: checkcast     #414                // class com/minecolonies/api/colony/permissions/ColonyPlayer
        29: invokevirtual #553                // Method 
com/minecolonies/api/colony/permissions/ColonyPlayer.getName:()Ljava/lang/String;
        32: putfield      #104                // Field ownerName:Ljava/lang/String;
        35: aload_0
        36: getfield      #104                // Field ownerName:Ljava/lang/String;
        39: areturn
  
    public boolean isDirty();
      Code:
         0: aload_0
         1: getfield      #100                // Field dirty:Z
         4: ireturn
  
    public void clearDirty();
      Code:
         0: aload_0
         1: iconst_0
         2: putfield      #100                // Field dirty:Z
         5: return
  
    public void serializeViewNetworkData(net.minecraft.network.FriendlyByteBuf, 
com.minecolonies.api.colony.permissions.Rank);
      Code:
         0: aload_1
         1: aload_0
         2: getfield      #93                 // Field ranks:Ljava/util/Map;
         5: invokeinterface #838,  1          // InterfaceMethod java/util/Map.size:()I
        10: invokevirtual #844                // Method 
net/minecraft/network/FriendlyByteBuf.m_130130_:(I)Lnet/minecraft/network/FriendlyByteBuf;
        13: pop
        14: aload_0
        15: getfield      #93                 // Field ranks:Ljava/util/Map;
        18: invokeinterface #519,  1          // InterfaceMethod java/util/Map.values:()Ljava/util/Collection;
        23: invokeinterface #522,  1          // InterfaceMethod java/util/Collection.iterator:()Ljava/util/Iterator;
        28: astore_3
        29: aload_3



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java -Pattern 'createSystemOwner|isSystemOwner' -Context 0,20" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 400ms:

> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:292:                    newOwner = 
createSystemOwner();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:293:                    
permissions.addPlayer(newOwner, "[AUTO_OWNER]", permissions.getRankOwner());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:294:                    
LOGGER.warn("No players found in colony {} during abandonment - created system owner placeholder", colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:295:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:296:                
safeSetPlayerRank(colony, permissions, newOwner, permissions.getRankOwner());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:297:                if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Assigned {} as owner placeholder for abandoned colony {}", newOwner, 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:298:                colonyOwner = 
newOwner;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:299:            } else {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:300:                if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Keeping existing owner {} to prevent GUI crashes in colony {}", 
colonyOwner, colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:301:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:302:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:303:            // Keep the owner 
at Owner rank; demote all other players to neutral.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:304:            Rank 
colonyNeutralRank = permissions.getRankNeutral();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:305:            for (UUID playerId 
: allPlayers.keySet()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:306:                if 
(!playerId.equals(colonyOwner)) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:307:                    
ColonyPlayer player = allPlayers.get(playerId);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:308:                    if 
(!player.getRank().equals(colonyNeutralRank)) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:309:                        
boolean rankSet = safeSetPlayerRank(colony, permissions, playerId, colonyNeutralRank);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:310:                        if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Set non-owner player {} to neutral rank: {}", playerId, rankSet);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:311:                    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:312:                }
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:525:                if 
(player.getID() == null || isSystemOwner(player.getID())) continue;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:526:                if 
(player.getRank() == null || !player.getRank().isColonyManager()) continue;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:527:                ServerPlayer 
online = world.getServer().getPlayerList().getPlayer(player.getID());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:528:                if (online != 
null) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:529:                    
permissions.setOwner(online);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:530:                    if 
(TaxConfig.isNormalLogging()) LOGGER.info("Restored {} as owner of null-owner colony {}", player.getName(), 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:531:                    return;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:532:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:533:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:534:            if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Null-owner colony {} has no online manager to promote; leaving untouched", 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:535:        } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:536:            
LOGGER.error("Failed to repair null-owner colony {}: {}", colony.getName(), e.getMessage());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:537:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:538:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:539:    
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:540:    /**
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:541:     * Scans all colonies and 
assigns a placeholder owner to any that have null owners.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:542:     * Idempotent — safe to 
call multiple times. Logs only when repairs are needed.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:543:     */
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:544:    /**
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:545:     * Scans all colonies and 
attempts to restore a real owner for any whose cached owner is
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:602:                        
boolean isSynthetic = isSystemOwner(id)
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:603:                               
 || (name != null && (name.equals("[AUTO_OWNER]")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:604:                               
         || name.equals("[AUTO_EMERGENCY_OWNER]")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:605:                               
         || name.equals("[SYSTEM_ABANDONED]")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:606:                               
         || name.contains("[abandoned]")));
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:607:                        if 
(isSynthetic) synthetic.add(id);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:608:                    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:609:                    if 
(synthetic.isEmpty()) continue;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:610:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:611:                    if (owner 
!= null && synthetic.contains(owner)) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:612:                        
ServerPlayer replacement = findOnlineRealManager(colony, synthetic);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:613:                        if 
(replacement != null) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:614:                            
permissions.setOwner(replacement);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:615:                            if 
(TaxConfig.isNormalLogging()) LOGGER.info("Legacy repair: handed ownership of colony {} from a synthetic placeholder 
to {}",
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:616:                               
     colony.getName(), replacement.getName().getString());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:617:                        } else 
{
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:618:                            // 
No safe online replacement — keep the synthetic owner for now and
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:619:                            // 
only strip the non-owner synthetic entries below.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:620:                            
synthetic.remove(owner);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:621:                        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:622:                    }
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:654:            if (id == null || 
exclude.contains(id) || isSystemOwner(id)) continue;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:655:            if (p.getRank() == 
null || !p.getRank().isColonyManager()) continue;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:656:            ServerPlayer 
online = world.getServer().getPlayerList().getPlayer(id);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:657:            if (online != 
null) return online;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:658:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:659:        return null;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:660:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:661:    
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:662:    /**
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:663:     * Clean up system owner 
when a colony becomes active again.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:664:     */
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:665:    private static void 
cleanupSystemOwner(IColony colony) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:666:        try {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:667:            IPermissions 
permissions = colony.getPermissions();
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:668:            UUID systemOwner = 
createSystemOwner();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:669:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:670:            if 
(permissions.getPlayers().containsKey(systemOwner)) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:671:                
permissions.removePlayer(systemOwner);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:672:                if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Removed system owner placeholder from reactivated colony {}", 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:673:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:674:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:675:            // The first real 
officer will automatically become the effective owner
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:676:            // NOTE: We don't 
explicitly call setOwner() to avoid API compatibility issues
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:677:            for (ColonyPlayer 
player : permissions.getPlayers().values()) {
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:678:                if 
(!isSystemOwner(player.getID()) && player.getRank().isColonyManager()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:679:                    if 
(TaxConfig.isDebugLogging()) LOGGER.info("CLEANUP: {} will be the effective owner of reactivated colony {}", 
player.getName(), colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:680:                    break;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:681:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:682:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:683:            
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:684:        } catch (Exception e) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:685:            
LOGGER.error("Error cleaning up system owner for colony {}: {}", colony.getName(), e.getMessage());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:686:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:687:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:688:    
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:689:    /**
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:690:     * Mark a colony as 
claimed (remove from abandoned list).
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:691:     * Also clears officer 
visit tracking as the new owner will establish fresh activity.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:692:     */
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:693:    public static void 
markColonyAsClaimed(int colonyId) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:694:        
abandonedColonies.remove(colonyId);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:695:        
warnedColonies.remove(colonyId);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:696:        
formerColonyMembers.remove(colonyId); // Clear former members tracking
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:697:        saveData(); // AUDIT 
FIX (defensive_04 M2): persist removal of abandoned state
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:698:
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:912:                } else if 
(isSystemOwner(playerId)) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:913:                    
isProblematic = true;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:914:                    reason = 
"synthetic system-owner UUID";
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:915:                } else if 
(playerId.equals(new UUID(0L, 0L))) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:916:                    
isProblematic = true;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:917:                    reason = 
"zero UUID";
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:918:                } else if 
(player.getName() != null
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:919:                        && 
(player.getName().equals("[AUTO_OWNER]")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:920:                               
 || player.getName().equals("[AUTO_EMERGENCY_OWNER]")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:921:                               
 || player.getName().equals("[SYSTEM_ABANDONED]")
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:922:                               
 || player.getName().contains("[abandoned]"))) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:923:                    
isProblematic = true;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:924:                    reason = 
"synthetic placeholder entry (" + player.getName() + ")";
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:925:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:926:                
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:927:                if 
(isProblematic) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:928:                    
LOGGER.warn("CLEANUP: Found problematic player entry - UUID: {}, Name: '{}', Reason: {}", 
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:929:                        
playerId, player != null ? player.getName() : "null", reason);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:930:                    
toRemove.add(playerId);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:931:                }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:932:            }
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:970:                .filter(player 
-> !isSystemOwner(player.getID()))
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:971:                
.anyMatch(player -> player.getRank().isColonyManager());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:972:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:973:        if (hasRealOfficers) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:974:            if 
(TaxConfig.isNormalLogging()) LOGGER.info("Colony {} reactivated - real officers/owners present, removing abandoned 
status", colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:975:            
markColonyAsClaimed(colony.getID());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:976:            
cleanupSystemOwnerAndSetRealOwner(colony);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:977:            
restoreNormalPermissions(colony);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:978:        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:979:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:980:    
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:981:    private static void 
cleanupSystemOwnerAndSetRealOwner(IColony colony) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:982:        try {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:983:            IPermissions 
permissions = colony.getPermissions();
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:984:            UUID 
systemOwnerUUID = createSystemOwner();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:985:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:986:            if 
(permissions.getPlayers().containsKey(systemOwnerUUID)) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:987:                
permissions.removePlayer(systemOwnerUUID);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:988:                if 
(TaxConfig.isDebugLogging()) LOGGER.debug("Removed system owner placeholder from reactivated colony {}", 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:989:            }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:990:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:991:            for (ColonyPlayer 
player : permissions.getPlayers().values()) {
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:992:                if 
(!isSystemOwner(player.getID()) && player.getRank().isColonyManager()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:993:                    try {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:994:                        // 
MineColonies changed IPermissions.setOwner(UUID) -> setOwner(Player) in
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:995:                        // 
1.1.1237, which broke the old reflection ("argument type mismatch") and
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:996:                        // 
left colonies ownerless. setOwner(Player) updates the cached ownerUUID
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:997:                        // 
that getOwner() returns; setPlayerRank does NOT. Prefer setOwner when the
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:998:                        // 
target manager is online; fall back to a best-effort rank assignment when
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:999:                        // 
offline (the new API cannot set an offline player as the cached owner).
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1000:                        // 
[1.21-PORT] same limitation on NeoForge/1.21 — see PORTING_NOTES.md.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1001:                        
net.minecraft.server.level.ServerPlayer online =
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1002:                              
  (colony.getWorld() != null && colony.getWorld().getServer() != null)
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1003:                              
          ? colony.getWorld().getServer().getPlayerList().getPlayer(player.getID())
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1004:                              
          : null;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1005:                        if 
(online != null) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1006:                            
permissions.setOwner(online);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1007:                        } 
else {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1008:                            
safeSetPlayerRank(colony, permissions, player.getID(), permissions.getRankOwner());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1009:                        }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1010:                        if 
(TaxConfig.isNormalLogging()) LOGGER.info("Set {} as owner of reactivated colony {}", player.getName(), 
colony.getName());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1011:                    } catch 
(Exception e) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1012:                        
LOGGER.error("Failed to set {} as owner of colony {}: {}", player.getName(), colony.getName(), e.getMessage());
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1027:    public static UUID 
createSystemOwner() {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1028:        return 
UUID.nameUUIDFromBytes("MINECOLONY_TAX_SYSTEM_OWNER".getBytes());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1029:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1030:
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1031:    public static boolean 
isSystemOwner(UUID uuid) {
> src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1032:        return uuid != null 
&& uuid.equals(createSystemOwner());
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1033:    }
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1034:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1035:    /**
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1036:     * Scans all colonies and 
removes corrupt [abandoned] permission entries, then
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1037:     * fixes any null owners. 
Safe to call on startup and periodically.
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1038:     */
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1039:    public static void 
cleanupAllColoniesAbandonedEntries() {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1040:        try {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1041:            IColonyManager 
colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1042:            int 
coloniesCleaned = 0;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1043:            int 
entriesRemoved = 0;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1044:            int 
nullOwnersFixed = 0;
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1045:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1046:            for (IColony 
colony : colonyManager.getAllColonies()) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1047:                try {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1048:                    
IPermissions permissions = colony.getPermissions();
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1049:
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1050:                    if 
(permissions.getOwner() == null) {
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1051:                        
fixNullOwnerColony(colony);
  src\main\java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:1052:                        
nullOwnersFixed++;



exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$i=0; Get-Content -Path src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java | ForEach-Object { $i++; if($i -ge 108 -and $i -le 130){ '"'{0,4}: {1}' -f "'$i, $_ } }; $i=0; Get-Content -Path src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java | ForEach-Object { $i++; if($i -ge 420 -and $i -le 426){ '"'{0,4}: {1}' -f "'$i, $_ } }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 314ms:
 108:         if (claimedAmount <= 0) {
 109:             return false;
 110:         }
 111: 
 112:         boolean useWallet = TaxConfig.isSDMShopConversionEnabled();
 113: 
 114:         if (useWallet && !SDMShopIntegration.isAvailable()) {
 115:             SDMShopIntegration.warnUnavailableOnce();
 116:             TaxManager.adjustTax(colony, claimedAmount); // refund â€” never lose taxes
 117:             player.sendSystemMessage(Component.literal(
 118:                     "Shop economy (SDMShop/SDM-Economy) is not available right now - your "
 119:                   + claimedAmount + " in taxes was NOT lost and is still claimable. Run /wnt debug sdm status for details.")
 120:                     .withStyle(ChatFormatting.RED));
 121:             return false;
 122:         }
 123: 
 124:         Source destination = useWallet ? Source.WALLET : Source.INVENTORY;
 125:         int given = giveToPlayer(player, colony, claimedAmount, destination);
 126:         if (given <= 0) {
 127:             TaxManager.adjustTax(colony, claimedAmount); // refund â€” never lose taxes
 128:             player.sendSystemMessage(Component.literal(
 129:                     "Failed to deliver " + claimedAmount + " (" + label(destination)
 130:                   + ") - your taxes were refunded and remain claimable.")
 420:                                                                                 }))))
 421: 
 422:                                 .then(Commands.literal("debug")
 423:                                                 .then(Commands.literal("sdm")
 424:                                                                 .then(Commands.literal("status")
 425:                                                                                 .executes(WntCommands::showSdmStatus)))
 426:                                                 .then(Commands.literal("emergencyfix")

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "Select-String -Path src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java,src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java -Pattern '/wnt sdm status|/wnt debug sdm status' | Select-Object Path,LineNumber,Line | Format-List" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 331ms:


Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\Curren
             cyService.java
LineNumber : 100
Line       :      * the tax is refunded (kept claimable) and the player is told to check {@code /wnt sdm status},

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\Curren
             cyService.java
LineNumber : 119
Line       :                   + claimedAmount + " in taxes was NOT lost and is still claimable. Run /wnt debug sdm 
             status for details.")

Path       : C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\SDMSho
             pIntegration.java
LineNumber : 620
Line       :                     + "converted. Use /wnt sdm status to diagnose.", modPresent, mode, 
             isServerInstanceReady());




exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "git diff -- src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java | Select-String -Pattern 'wnt|adjustTax|Run|Use' -Context 1,1" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 362ms:

  +     *
> +     * <p>This fixes the 4.x "claimed coins never appear" bug: the claim paths used to deduct
  +     * the tax first and then attempt SDM delivery with no refund, so any unavailable-economy
  +     * <p>Behavior when {@code EnableSDMShopConversion=true} but the shop economy is unavailable:
> +     * the tax is refunded (kept claimable) and the player is told to check {@code /wnt sdm status},
  +     * rather than silently dropping surprise currency items.</p>
  +
> +        boolean useWallet = TaxConfig.isSDMShopConversionEnabled();
  +
> +        if (useWallet && !SDMShopIntegration.isAvailable()) {
  +            SDMShopIntegration.warnUnavailableOnce();
> +            TaxManager.adjustTax(colony, claimedAmount); // refund — never lose taxes
  +            player.sendSystemMessage(Component.literal(
  +                    "Shop economy (SDMShop/SDM-Economy) is not available right now - your "
> +                  + claimedAmount + " in taxes was NOT lost and is still claimable. Run /wnt debug sdm status for 
details.")
  +                    .withStyle(ChatFormatting.RED));
  +
> +        Source destination = useWallet ? Source.WALLET : Source.INVENTORY;
  +        int given = giveToPlayer(player, colony, claimedAmount, destination);
  +        if (given <= 0) {
> +            TaxManager.adjustTax(colony, claimedAmount); // refund — never lose taxes
  +            player.sendSystemMessage(Component.literal(
        * Returns how much currency the player has available from the given source.
>       * Used for balance checks and status display.
  diff --git a/src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java 
b/src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java
  -    // Default currency name for SDM-Economy multi-currency system
> +    // Fallback currency id used only if the configured value is blank. The authoritative value
  +    // is TaxConfig.getSDMCurrencyName() (config key 'SDMCurrencyName') so servers whose
  +     * For CURRENCY_DATA mode this is what's commonly null in single-player until SDM-Economy
> +     * finishes its own SERVER_STARTED init — the usual cause of "coins never appeared".
  +     */
  +     * Logs a single WARN the first time tax conversion is requested while the integration is
> +     * unavailable, so server admins can see the cause without enabling debug logging.
  +     */
  +                    + "(modPresent={}, mode={}, serverReady={}). Claimed taxes are being refunded instead of "
> +                    + "converted. Use /wnt sdm status to diagnose.", modPresent, mode, isServerInstanceReady());
  +        }


warning: in the working copy of 'src/main/java/net/machiavelli/minecolonytax/integration/CurrencyService.java', LF will be replaced by CRLF the next time Git touches it

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$i=0; Get-Content -Path src/main/java/net/machiavelli/minecolonytax/integration/SDMShopIntegration.java | ForEach-Object { $i++; if($i -ge 614 -and $i -le 622){ '"'{0,4}: {1}' -f "'$i, $_ } }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 288ms:
 614:      * unavailable, so server admins can see the cause without enabling debug logging.
 615:      */
 616:     public static void warnUnavailableOnce() {
 617:         if (WARNED_UNAVAILABLE.compareAndSet(false, true)) {
 618:             LOGGER.warn("EnableSDMShopConversion=true but the SDMShop/SDM-Economy integration is not available "
 619:                     + "(modPresent={}, mode={}, serverReady={}). Claimed taxes are being refunded instead of "
 620:                     + "converted. Use /wnt sdm status to diagnose.", modPresent, mode, isServerInstanceReady());
 621:         }
 622:     }

codex
The failed-delivery refund is not durably saved, so the patch can still lose claimed taxes in the failure scenario it is meant to fix. There is also a smaller diagnostic command mismatch.

Full review comments:

- [P2] Persist refunded tax after failed delivery — C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\CurrencyService.java:116-116
  When SDM conversion is enabled but unavailable (or delivery fails), this refunds only the in-memory ledger. `claimTax()` has already queued an async save of the deducted balance, so a crash before the next tax save can persist the stale post-claim amount and lose the tax despite telling the player it remains claimable; the refund path needs to enqueue/persist a tax save too.

- [P3] Point admins to the registered SDM debug command — C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\SDMShopIntegration.java:620-620
  The command registered in `WntCommands` is `/wnt debug sdm status`, so when the integration is unavailable this warning sends admins to a non-existent `/wnt sdm status` command and defeats the new diagnostic hint.
The failed-delivery refund is not durably saved, so the patch can still lose claimed taxes in the failure scenario it is meant to fix. There is also a smaller diagnostic command mismatch.

Full review comments:

- [P2] Persist refunded tax after failed delivery — C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\CurrencyService.java:116-116
  When SDM conversion is enabled but unavailable (or delivery fails), this refunds only the in-memory ledger. `claimTax()` has already queued an async save of the deducted balance, so a crash before the next tax save can persist the stale post-claim amount and lose the tax despite telling the player it remains claimable; the refund path needs to enqueue/persist a tax save too.

- [P3] Point admins to the registered SDM debug command — C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon\src\main\java\net\machiavelli\minecolonytax\integration\SDMShopIntegration.java:620-620
  The command registered in `WntCommands` is `/wnt debug sdm status`, so when the integration is unavailable this warning sends admins to a non-existent `/wnt sdm status` command and defeats the new diagnostic hint.
