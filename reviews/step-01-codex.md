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
session id: 019e5cce-9269-71c3-919d-4dff0bc07f6c
--------
user
You are reviewing one step of an 11-step Java refactor for a Minecraft 1.20.1 Forge mod (War 'N Taxes, a MineColonies addon). Step 1 introduces a central ColonyTierGuard that protects 'primary' colonies (a player's first colony) from ownership transfer unless EnablePrimaryColonyTransfer config is on. The guard is wired into the existing WarSystem.transferOwnership() method as an early check with vassalization fallback. Review for: correctness, null-safety, thread-safety, edge cases, fallback completeness (does the vassalization fallback fire in ALL the right scenarios?), and whether the central-guard pattern is actually airtight or if there are other transfer code paths that bypass it. The bundle below is a NEW file plus two file diffs. Respond with: (1) STATUS: APPROVE / REWORK / REJECT, (2) up to 5 bullet findings ranked by severity, (3) up to 3 concrete file:line fix suggestions. Max 350 words total.

<stdin>
## STEP 1 REVIEW BUNDLE

### NEW FILE: src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java
```java
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
 * Vassalage is intentionally NOT gated here — losing a war can still vassalize
 * a primary colony (the loser pays tribute) without the deed moving. Only
 * permanent ownership changes flow through this guard.
 */
public final class ColonyTierGuard {

    private ColonyTierGuard() {}

    /**
     * Whether the colony's ownership may be transferred to a new player.
     *
     * @param colony the colony in question (may be null — returns false)
     * @return true when transfer is permitted; false when the colony is a
     *         primary and {@code EnablePrimaryColonyTransfer} is off
     */
    public static boolean canTransferOwnership(IColony colony) {
        if (colony == null) {
            return false;
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
        UUID owner = colony.getPermissions().getOwner();
        if (owner != null && FirstColonyTracker.isFirstColony(owner, colony.getID())) {
            return colony.getName() + " is a Primary colony — ownership transfer is blocked by config "
                    + "(EnablePrimaryColonyTransfer = false). Vassalization or tax-occupation may still apply.";
        }
        return "Transfer denied (no specific reason).";
    }
}
```

### DIFF: src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java b/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
index f6f21ab..9eafed0 100644
--- a/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
+++ b/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
@@ -71,6 +71,9 @@ public class TaxConfig {
         public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_VASSALIZATION;
         public static final ForgeConfigSpec.IntValue WAR_VASSALIZATION_DURATION_HOURS;
         public static final ForgeConfigSpec.IntValue WAR_VASSALIZATION_TRIBUTE_PERCENTAGE;
+
+        // Colony Tier Protection (Siege SMP ruleset)
+        public static final ForgeConfigSpec.BooleanValue ENABLE_PRIMARY_COLONY_TRANSFER;
         public static final ForgeConfigSpec.IntValue JOIN_PHASE_DURATION_MINUTES;
         public static final ForgeConfigSpec.BooleanValue WAR_ACCEPTANCE_REQUIRED;
         public static final ForgeConfigSpec.BooleanValue KEEP_INVENTORY_ON_LAST_LIFE;
@@ -134,6 +137,12 @@ public class TaxConfig {
         public static final ForgeConfigSpec.BooleanValue ENABLE_GENERAL_ITEM_INTERACTIONS;
         public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GENERAL_COLONY_ACTIONS;
 
+        // Easy Factions Integration Configuration
+        public static final ForgeConfigSpec.BooleanValue ENABLE_EASY_FACTIONS_INTEGRATION;
+        public static final ForgeConfigSpec.ConfigValue<String> EASY_FACTIONS_MEMBER_RANK;
+        public static final ForgeConfigSpec.BooleanValue EASY_FACTIONS_PROMOTE_OFFICERS;
+        public static final ForgeConfigSpec.IntValue EASY_FACTIONS_SYNC_INTERVAL_TICKS;
+
         // Guard Resistance During Raids Configuration
         public static final ForgeConfigSpec.BooleanValue ENABLE_GUARD_RESISTANCE_DURING_RAIDS;
         public static final ForgeConfigSpec.IntValue GUARD_RESISTANCE_LEVEL;
@@ -597,6 +606,18 @@ public class TaxConfig {
                                                 "Set VassalizationReplacesReparations=true to make tribute the only penalty.")
                                 .defineInRange("WarVassalizationTributePercentage", 15, 1, 100); // Default 15%
 
+                ENABLE_PRIMARY_COLONY_TRANSFER = BUILDER.comment(
+                                "If false (default), a player's first colony (Primary) is protected from ownership transfer.\n"
+                                                +
+                                                "Primary colonies can still be tax-occupied via besiege or vassalized via full war,\n"
+                                                +
+                                                "but the deed never moves to a new owner. Secondary colonies are always transferable\n"
+                                                +
+                                                "when ENABLE_COLONY_TRANSFER is enabled. Flip this to true for a no-mercy SMP\n"
+                                                +
+                                                "where even home bases can be permanently lost.")
+                                .define("EnablePrimaryColonyTransfer", false);
+
                 // ========== Colony Occupation Settings ==========
                 BUILDER.push("Colony Occupation");
 
@@ -832,6 +853,28 @@ public class TaxConfig {
 
                 BUILDER.pop();
 
+                // ========== Easy Factions Integration ==========
+                BUILDER.push("Easy Factions Integration");
+
+                ENABLE_EASY_FACTIONS_INTEGRATION = BUILDER.comment(
+                                "Enable integration with the Easy Factions mod (modid: easy_factions). When enabled, faction members are automatically granted MineColonies permissions on each other's colonies. Has no effect if Easy Factions is not installed.")
+                                .define("EnableEasyFactionsIntegration", true);
+
+                EASY_FACTIONS_MEMBER_RANK = BUILDER.comment(
+                                "MineColonies rank assigned to regular faction members on each other's colonies. Valid values: friend, officer. Officer grants more interaction permissions but also allows tax claiming under default settings.")
+                                .define("EasyFactionsMemberRank", "friend",
+                                                o -> o instanceof String s && (s.equalsIgnoreCase("friend") || s.equalsIgnoreCase("officer")));
+
+                EASY_FACTIONS_PROMOTE_OFFICERS = BUILDER.comment(
+                                "If true, Easy Factions officers and the faction owner are promoted to MineColonies Officer rank on every faction member's colony (overrides EasyFactionsMemberRank for them). WARNING: MineColonies Officer rank grants tax-claim rights by default - enabling this lets faction officers drain other members' treasuries via /wnt claimtax. Default is false; opt in only for tightly-trusted factions.")
+                                .define("EasyFactionsPromoteOfficers", false);
+
+                EASY_FACTIONS_SYNC_INTERVAL_TICKS = BUILDER.comment(
+                                "How often (in server ticks) to reconcile faction membership with colony permissions. 20 ticks = 1 second. Default 200 = every 10 seconds. Lower values are more responsive but slightly more expensive.")
+                                .defineInRange("EasyFactionsSyncIntervalTicks", 200, 20, 12000);
+
+                BUILDER.pop();
+
                 // ========== Colony Auto-Abandon Settings ==========
                 BUILDER.push("Colony Auto-Abandon");
 
@@ -1865,15 +1908,16 @@ public class TaxConfig {
                                 .define("BlockFilterRaids", true);
 
                 SUPPRESS_COLONY_LEVITATION = BUILDER.comment(
-                                "Globally suppress the levitation effect that MineColonies applies as a\n" +
-                                "trespassing punishment when a player triggers too many denied permission\n" +
-                                "checks inside a colony. MineColonies does not expose a built-in toggle\n" +
-                                "for this behaviour, so WNT provides one here.\n" +
-                                "When true: no player will receive levitation from MineColonies colony\n" +
-                                "protection, regardless of whether a war or raid is active.\n" +
-                                "When false (default): only conflict participants at their conflict site\n" +
-                                "are exempt; levitation still fires normally everywhere else.")
-                                .define("SuppressColonyLevitation", false);
+                                "Controls the MineColonies levitation trespassing-punishment.\n" +
+                                "MineColonies does not expose a built-in toggle for this, so WNT provides one.\n" +
+                                "\n" +
+                                "true (default): levitation is suppressed for ALL players inside ANY colony at\n" +
+                                "  ALL times. Players in active wars, raids, or besiegements are always exempt.\n" +
+                                "\n" +
+                                "false: levitation fires normally for everyone, EXCEPT players who are active\n" +
+                                "  participants in a war, raid, or besiegement — they remain exempt so the\n" +
+                                "  conflict mechanic is not disrupted.")
+                                .define("SuppressColonyLevitation", true);
 
                 REQUIRED_GUARD_TOWERS_FOR_BOOST = BUILDER.comment(
                                 "Minimum number of Guard Towers to start receiving tax boost. " +
@@ -2563,6 +2607,10 @@ public class TaxConfig {
                 return ENABLE_WAR_VASSALIZATION.get();
         }
 
+        public static boolean isPrimaryColonyTransferEnabled() {
+                return ENABLE_PRIMARY_COLONY_TRANSFER.get();
+        }
+
         public static int getWarVassalizationDurationHours() {
                 return WAR_VASSALIZATION_DURATION_HOURS.get();
         }
@@ -2725,6 +2773,22 @@ public class TaxConfig {
                                 .collect(java.util.stream.Collectors.toSet());
         }
 
+        public static boolean isEasyFactionsIntegrationEnabled() {
+                return ENABLE_EASY_FACTIONS_INTEGRATION.get();
+        }
+
+        public static String getEasyFactionsMemberRank() {
+                return EASY_FACTIONS_MEMBER_RANK.get();
+        }
+
+        public static boolean shouldPromoteEasyFactionsOfficers() {
+                return EASY_FACTIONS_PROMOTE_OFFICERS.get();
+        }
+
+        public static int getEasyFactionsSyncIntervalTicks() {
+                return EASY_FACTIONS_SYNC_INTERVAL_TICKS.get();
+        }
+
         public static boolean isGuardResistanceDuringRaidsEnabled() {
                 return ENABLE_GUARD_RESISTANCE_DURING_RAIDS.get();
         }
```

### DIFF: src/main/java/net/machiavelli/minecolonytax/WarSystem.java
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/WarSystem.java b/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
index 9158174..bc098a3 100644
--- a/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
+++ b/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
@@ -1182,6 +1182,32 @@ public class WarSystem {
     public static void transferOwnership(IColony colony, UUID newOwnerUUID) {
         if (colony.getWorld() == null || colony.getWorld().getServer() == null)
             return;
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
+                            + " is a Primary colony — vassalized instead of conquered.")
+                            .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true));
+                    sendNotificationToWarParticipants(colony, war != null ? war.getAttackerColony() : null, msg);
+                }
+            }
+            return;
+        }
+
         ServerPlayer newOwner = colony.getWorld().getServer().getPlayerList().getPlayer(newOwnerUUID);
         if (newOwner == null)
             return;
@@ -3679,10 +3705,26 @@ public class WarSystem {
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
```
</stdin>
codex
I’ll verify the ownership-transfer surface in the repo, since the key review question is whether this guard covers all deed-moving paths. I’m only reading files, not editing.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"getPermissions\\(\\)\\.set|getPermissions\\(\\).*owner|getOwner\\(|setOwner|restore.*owner|change.*owner|transfer.*owner\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"transferOwnership\\(|ColonyTierGuard|canTransferOwnership|setOwner\\(|setOwner\\b|setOwnerId|ownerUUID|forceVassalize\\(\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "class WarSystem|public static void transferOwnership|sendNotificationToWarParticipants|class VassalManager|forceVassalize|ACTIVE_WARS|ENABLE_COLONY_TRANSFER|isColonyTransferEnabled" src/main/java/net/machiavelli/minecolonytax' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 395ms:
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:197:        UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:198:        if (ownerUUID != null && FirstColonyTracker.isFirstColony(ownerUUID, colonyId)) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:547:        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:551:            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:554:                    raid.besiegingPlayerUUID, ownerUUID,
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:560:            if (ownerUUID != null) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:562:                sendToPlayer(ownerUUID, Component.literal(
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:465:            // NOTE: We don't explicitly call setOwner() to avoid API compatibility issues
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:787:                        java.lang.reflect.Method setOwnerMethod = permissions.getClass().getMethod("setOwner", UUID.class);
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:795:                                if (method.getName().equals("setOwner") && method.getParameterCount() == 1) {
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:786:                java.lang.reflect.Method setOwnerMethod = permissions.getClass().getMethod("setOwner", UUID.class);
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:794:                        if (method.getName().equals("setOwner") && method.getParameterCount() == 1) {
src/main/java\net\machiavelli\minecolonytax\db\WarStatsDB.java:447:                        UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\db\WarStatsDB.java:469:                                ownerUUID != null ? ownerUUID.toString() : "",
src/main/java\net\machiavelli\minecolonytax\db\WarStatsDB.java:515:                    ps.setString(3,  snap.ownerUUID);
src/main/java\net\machiavelli\minecolonytax\db\WarStatsDB.java:653:            int colonyId, String colonyName, String ownerUUID,
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:167:        UUID ownerUUID = perms.getOwner();
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:168:        if (ownerUUID == null) return;
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:170:        EasyFactionsBridge.FactionView faction = EasyFactionsBridge.lookupForPlayer(server, ownerUUID);
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:177:                if (memberUUID.equals(ownerUUID)) continue;
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:342:        UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:344:                && ownerUUID != null
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:345:                && currentFaction.members.contains(ownerUUID)) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:774:                    transferOwnership(war.getColony(), war.getAttacker());
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:780:                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1182:    public static void transferOwnership(IColony colony, UUID newOwnerUUID) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1189:        if (!net.machiavelli.minecolonytax.permissions.ColonyTierGuard.canTransferOwnership(colony)) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1192:                    net.machiavelli.minecolonytax.permissions.ColonyTierGuard.getTransferDenialReason(colony));
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1196:                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1214:        if (colony.getPermissions().setOwner(newOwner)) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1797:                transferOwnership(war.getColony(), war.getAttacker());
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1854:                transferOwnership(war.getAttackerColony(), war.getDefender());
src/main/java\net\machiavelli\minecolonytax\vassalization\VassalManager.java:291:    public static boolean forceVassalize(IColony vassalColony, UUID overlordUUID, int tributePercent,
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:230:            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:231:            if (ownerUUID == null) continue;
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:234:            ColonyPlayer ownerEntry = colony.getPermissions().getPlayers().get(ownerUUID);
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:246:            ownerToColonyIds.computeIfAbsent(ownerUUID, k -> new ArrayList<>()).add(colony.getID());
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:251:            UUID ownerUUID = entry.getKey();
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:257:            List<Integer> alreadyTracked = playerColoniesMap.getOrDefault(ownerUUID, Collections.emptyList());
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:262:                playerColoniesMap.put(ownerUUID, newList);
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:266:                            newList.size(), ownerUUID, newList.get(0));
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:270:                List<Integer> tracked = playerColoniesMap.get(ownerUUID);
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:277:                                    colonyId, ownerUUID);
src/main/java\net\machiavelli\minecolonytax\MineColonyTax.java:113:                        UUID ownerUUID = e.getColony().getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\MineColonyTax.java:114:                        if (ownerUUID != null) {
src/main/java\net\machiavelli\minecolonytax\MineColonyTax.java:115:                            FirstColonyTracker.addColony(ownerUUID, e.getColony().getID());
src/main/java\net\machiavelli\minecolonytax\MineColonyTax.java:121:                        UUID ownerUUID = e.getColony().getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\MineColonyTax.java:122:                        if (ownerUUID != null) {
src/main/java\net\machiavelli\minecolonytax\MineColonyTax.java:123:                            FirstColonyTracker.removeColony(ownerUUID, e.getColony().getID());
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:386:            WarSystem.transferOwnership(colony, occupierUUID);
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:13: * {@link #canTransferOwnership(IColony)}. Primary colonies (a player's first
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:22:public final class ColonyTierGuard {
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:24:    private ColonyTierGuard() {}
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:33:    public static boolean canTransferOwnership(IColony colony) {
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:51:     * Same rules as {@link #canTransferOwnership(IColony)}; named separately so
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:55:        return canTransferOwnership(colony);
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:3418:                                                                                .getClass().getMethod("setOwner",
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:3430:                                                                                if (method.getName().equals("setOwner")
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:60:            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:61:            if (ownerUUID == null) {
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:69:            boolean isFirstColony = FirstColonyTracker.addColony(ownerUUID, colonyID);
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:74:                    colonyID, ownerUUID);
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:77:                handleSecondaryColonyCreation(colony, ownerUUID);
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:128:            firstColony.getPermissions().setOwner(player);
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:166:            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:167:            if (ownerUUID == null) {
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:175:            Integer newFirstColonyID = FirstColonyTracker.removeColony(ownerUUID, colonyID);
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:180:                    colonyID, ownerUUID, newFirstColonyID);
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:183:                promoteToFirstColony(newFirstColonyID, ownerUUID);
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:186:                    colonyID, ownerUUID);
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:222:            colony.getPermissions().setOwner(player);
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidDebugLogger.java:292:        UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidDebugLogger.java:293:        if (ownerUUID == null) {
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidDebugLogger.java:298:            ServerPlayer owner = colony.getWorld().getServer().getPlayerList().getPlayer(ownerUUID);
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidDebugLogger.java:304:        return ownerUUID.toString().substring(0, 8) + "...";
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:189:            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:190:            if (ownerUUID != null) {
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:191:                ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:913:                UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:914:                if (ownerUUID != null) {
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:915:                    ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:918:                        notifiedPlayers.add(ownerUUID);
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:949:                UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:950:                if (ownerUUID != null) {
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:951:                    ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:955:                        notifiedPlayers.add(ownerUUID);
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:993:                UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:994:                if (ownerUUID != null) {
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:995:                    ServerPlayer owner = server.getPlayerList().getPlayer(ownerUUID);
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:1028:                UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:1029:                boolean isOwner = ownerUUID != null && ownerUUID.equals(player.getUUID());
src/main/java\net\machiavelli\minecolonytax\raid\ReflectionCache.java:545:        UUID ownerUUID = getOwnerUUID(entity);
src/main/java\net\machiavelli\minecolonytax\raid\ReflectionCache.java:546:        if (ownerUUID != null) {
src/main/java\net\machiavelli\minecolonytax\raid\ReflectionCache.java:709:        UUID ownerUUID = getOwnerUUID(entity);
src/main/java\net\machiavelli\minecolonytax\raid\ReflectionCache.java:710:        if (ownerUUID != null) {
src/main/java\net\machiavelli\minecolonytax\raid\ReflectionCache.java:712:                    "Recruit owner UUID retrieved: " + ownerUUID.toString());
src/main/java\net\machiavelli\minecolonytax\raid\ReflectionCache.java:718:        return ownerUUID;
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 398ms:
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:786:                java.lang.reflect.Method setOwnerMethod = permissions.getClass().getMethod("setOwner", UUID.class);
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:787:                setOwnerMethod.invoke(permissions, claimingPlayer.getUUID());
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyClaimingRaidManager.java:794:                        if (method.getName().equals("setOwner") && method.getParameterCount() == 1) {
src/main/java\net\machiavelli\minecolonytax\db\WarStatsDB.java:447:                        UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\compat\ColonyHelper.java:13:            if (playerUUID.equals(c.getPermissions().getOwner()))
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:138:            if (!faction.members.contains(colony.getPermissions().getOwner())) {
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:167:        UUID ownerUUID = perms.getOwner();
src/main/java\net\machiavelli\minecolonytax\compat\EasyFactionsPermissionSync.java:342:        UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:180:        if (colony.getPermissions().getOwner() != null
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:181:                && colony.getPermissions().getOwner().equals(besiegerUUID)) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:197:        UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:551:            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:863:        sendToPlayer(perms.getOwner(), message);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:898:            UUID owner = c.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:951:            UUID owner = perms.getOwner();
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:102:        UUID owner = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:158:            UUID currentOwner = permissions.getOwner();
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:180:            UUID colonyOwner = permissions.getOwner();
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:213:            // We do not call setOwnerAbandoned() â€” it creates problematic [abandoned]
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:365:        UUID owner = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:369:            owner = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:375:                    colony.getPermissions().setPlayerRank(systemOwner, colony.getPermissions().getRankOwner(), colony.getWorld());
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:419:                    if (permissions.getOwner() != null) continue;
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:465:            // NOTE: We don't explicitly call setOwner() to avoid API compatibility issues
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:603:            UUID currentOwner = permissions.getOwner();
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:787:                        java.lang.reflect.Method setOwnerMethod = permissions.getClass().getMethod("setOwner", UUID.class);
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:788:                        setOwnerMethod.invoke(permissions, player.getID());
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:795:                                if (method.getName().equals("setOwner") && method.getParameterCount() == 1) {
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:842:                    if (permissions.getOwner() == null) {
src/main/java\net\machiavelli\minecolonytax\commands\AbandonmentCheckCommand.java:84:        UUID owner = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:119:                : colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:135:        data.getDefenderLives().put(colony.getPermissions().getOwner(), playerLives);
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:191:            if (!uuid.equals(colony.getPermissions().getOwner())) { // Don't add owner twice
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:497:        UUID colonyOwner = perms.getOwner();
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:777:                // Vassalize the losing colony instead of transferring ownership
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:956:                UUID loserUUID = attackersWon ? war.getColony().getPermissions().getOwner() : war.getAttacker();
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1140:        UUID owner = winningColony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1214:        if (colony.getPermissions().setOwner(newOwner)) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1369:            UUID winnerUuid = colony.getPermissions().getOwner(); // This might not always be the "winner"
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2192:                .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2238:                    .getPlayer(war.getColony().getPermissions().getOwner());
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2545:                .filter(c -> c.getPermissions().getOwner().equals(playerUUID))
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2570:                    if (targetColony.getPermissions().getOwner().equals(occupierUUID)) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2648:                UUID targetOwner = targetColony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2650:                        .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2677:                    .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2726:        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(targetColony.getPermissions().getOwner());
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2729:        UUID targetOwnerUUID = targetColony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2815:                            .getPlayer(targetColony.getPermissions().getOwner());
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2837:            targetColony.getPermissions().setPlayerRank(attacker.getUUID(),
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2861:        WARSYSTEM_LOGGER.info("[War] Target Colony Owner: {}", targetColony.getPermissions().getOwner());
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2918:                targetColony.getPermissions().getOwner(), // Defender owner UUID (offline)
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3014:                    .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3049:        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(targetColony.getPermissions().getOwner());
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3157:        boolean isAuthorized = targetColony.getPermissions().getOwner().equals(executor.getUUID()) ||
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3188:                        .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3272:        if (player.getUUID().equals(war.getColony().getPermissions().getOwner()) ||
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3275:                        && player.getUUID().equals(war.getAttackerColony().getPermissions().getOwner()))) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3353:        if (player.getUUID().equals(war.getColony().getPermissions().getOwner()) ||
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3356:                        && player.getUUID().equals(war.getAttackerColony().getPermissions().getOwner()))) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3515:        if (player.getUUID().equals(war.getColony().getPermissions().getOwner()) ||
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:3518:                        && player.getUUID().equals(war.getAttackerColony().getPermissions().getOwner()))) {
src/main/java\net\machiavelli\minecolonytax\commands\TradeRouteCommand.java:85:                if (col.getPermissions().getOwner().equals(player.getUUID())) {
src/main/java\net\machiavelli\minecolonytax\vassalization\VassalManager.java:405:        java.util.UUID ownerId = perms.getOwner();
src/main/java\net\machiavelli\minecolonytax\vassalization\VassalManager.java:492:            if (c.getPermissions().getOwner().equals(playerId))
src/main/java\net\machiavelli\minecolonytax\commands\OfficerTrackingDebugCommand.java:64:        UUID owner = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\commands\OfficerTrackingDebugCommand.java:103:                ColonyPlayer ownerEntry = colony.getPermissions().getPlayers().get(owner);
src/main/java\net\machiavelli\minecolonytax\faction\FactionManager.java:158:                // If there are other members, transfer ownership to the first one found
src/main/java\net\machiavelli\minecolonytax\faction\FactionManager.java:162:                    faction.setOwnerColonyId(newOwner);
src/main/java\net\machiavelli\minecolonytax\commands\WarCommands.java:130:                .filter(c -> c.getPermissions().getOwner().equals(attackerUUID))
src/main/java\net\machiavelli\minecolonytax\commands\WarCommands.java:173:        if (player.getUUID().equals(war.getColony().getPermissions().getOwner()) ||
src/main/java\net\machiavelli\minecolonytax\commands\WarCommands.java:176:                        && player.getUUID().equals(war.getAttackerColony().getPermissions().getOwner()))) {
src/main/java\net\machiavelli\minecolonytax\espionage\SpyManager.java:634:        UUID ownerUuid = perms.getOwner();
src/main/java\net\machiavelli\minecolonytax\espionage\SpyManager.java:638:                intel.setOwnerName(ownerPlayer.getName().getString());
src/main/java\net\machiavelli\minecolonytax\espionage\SpyManager.java:639:                intel.setOwnerSdmBalance(SDMShopIntegration.getMoney(ownerPlayer));
src/main/java\net\machiavelli\minecolonytax\espionage\SpyManager.java:645:                intel.setOwnerName(profile != null ? profile.getName() : ownerUuid.toString().substring(0, 8));
src/main/java\net\machiavelli\minecolonytax\espionage\SpyManager.java:646:                intel.setOwnerSdmBalance(-1); // offline
src/main/java\net\machiavelli\minecolonytax\espionage\SpyManager.java:1102:        recipients.add(perms.getOwner());
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1583:                if (player.getUUID().equals(war.getColony().getPermissions().getOwner()) ||
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:1587:                                                                war.getAttackerColony().getPermissions().getOwner()))) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:2490:                boolean isAuthorized = targetColony.getPermissions().getOwner().equals(player.getUUID()) ||
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:2623:                                        .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:3373:                                        UUID owner = permissions.getOwner();
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:3407:                                                if (permissions.getOwner() == null ||
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:3409:                                                                                permissions.getOwner())) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:3417:                                                                java.lang.reflect.Method setOwnerMethod = permissions
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:3418:                                                                                .getClass().getMethod("setOwner",
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:3420:                                                                setOwnerMethod.invoke(permissions, systemOwner);
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:3430:                                                                                if (method.getName().equals("setOwner")
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:3833:                if (!colony.getPermissions().getOwner().equals(player.getUUID())) {
src/main/java\net\machiavelli\minecolonytax\commands\WntCommands.java:3878:                                if (colony.getPermissions().getOwner().equals(player.getUUID())) {
src/main/java\net\machiavelli\minecolonytax\espionage\SpyIntelData.java:153:    public void setOwnerSdmBalance(long ownerSdmBalance) {
src/main/java\net\machiavelli\minecolonytax\espionage\SpyIntelData.java:161:    public void setOwnerName(String ownerName) {
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:60:            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:76:                // Secondary colony - demote player to officer and restore first colony ownership
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:124:        UUID firstColonyOwner = firstColony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:128:            firstColony.getPermissions().setOwner(player);
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:143:            newColony.getPermissions().setPlayerRank(playerUUID, officerRank, newColony.getWorld());
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:166:            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:218:        UUID currentOwner = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:222:            colony.getPermissions().setOwner(player);
src/main/java\net\machiavelli\minecolonytax\faction\FactionData.java:55:    public void setOwnerColonyId(int ownerColonyId) {
src/main/java\net\machiavelli\minecolonytax\economy\policy\TaxPolicyManager.java:339:                if (colony.getPermissions().getOwner().equals(playerUUID)
src/main/java\net\machiavelli\minecolonytax\MineColonyTax.java:113:                        UUID ownerUUID = e.getColony().getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\MineColonyTax.java:121:                        UUID ownerUUID = e.getColony().getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\event\OfficerColonyVisitTracker.java:263:        UUID owner = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:67:                if (wasRelevant && raid.getColony().getPermissions().getOwner().equals(playerUUID)) {
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:169:        boolean isOwner = perms.getOwner().equals(playerUUID);
src/main/java\net\machiavelli\minecolonytax\event\RaidLoginNotifier.java:199:        notifySet.add(perms.getOwner());
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:176:     * More reliable than calling getOwner() on a colony whose permissions may be corrupted.
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:230:            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\FirstColonyTracker.java:234:            ColonyPlayer ownerEntry = colony.getPermissions().getPlayers().get(ownerUUID);
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:135:        UUID originalOwner = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:349:    /** Periodic check â€” transfers full ownership to the occupier when the occupation expires. Called via TickScheduler. */
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:382:                LOGGER.info("Occupation expired for colony {} - transferring full ownership to {}",
src/main/java\net\machiavelli\minecolonytax\trade\TradeRouteManager.java:395:                if (colony.getPermissions().getOwner().equals(player.getUUID())) {
src/main/java\net\machiavelli\minecolonytax\trade\TradeRouteManager.java:414:        UUID ownerId = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\trade\TradeRouteManager.java:419:        return colony.getPermissions().getOwner().equals(player.getUUID());
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:37:        UUID currentOwner = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:66:        UUID owner = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:208:            if (colony.getPermissions().getOwner().equals(responderId)) return true;
src/main/java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:214:            if (colony.getPermissions().getOwner().equals(responderId)) return true;
src/main/java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:261:                    winningPlayerId = war.getColony().getPermissions().getOwner(); // Defender colony owner
src/main/java\net\machiavelli\minecolonytax\peace\PeaceProposalManager.java:264:                    winningPlayerId = war.getAttackerColony().getPermissions().getOwner(); // Attacker colony owner
src/main/java\net\machiavelli\minecolonytax\TaxManager.java:784:                    UUID ownerId = permissions.getOwner();
src/main/java\net\machiavelli\minecolonytax\permissions\PermissionsHealthCheck.java:156:        UUID colonyOwner = perms.getOwner();
src/main/java\net\machiavelli\minecolonytax\server\ColonyDataCollector.java:191:            boolean isOwner = colony.getPermissions().getOwner().equals(playerId);
src/main/java\net\machiavelli\minecolonytax\TaxConfig.java:536:                                "Percentage of killed raider's balance transferred to defending colony for owner/officers to claim (0.0 - 1.0)")
src/main/java\net\machiavelli\minecolonytax\TaxConfig.java:590:                                "When enabled and ENABLE_COLONY_TRANSFER is disabled, winning a war will vassalize the losing colony instead of transferring ownership.\n"
src/main/java\net\machiavelli\minecolonytax\TaxConfig.java:626:                                                + "instead of immediately transferring ownership. The occupier can collect taxes but cannot\n"
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidDebugLogger.java:292:        UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:189:            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:356:            UUID colonyOwnerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:509:        UUID colonyOwnerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:555:        UUID colonyOwnerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:913:                UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:949:                UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:993:                UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\EntityRaidManager.java:1028:                UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java\net\machiavelli\minecolonytax\raid\RaidManager.java:66:                    .filter(c -> c.getPermissions().getOwner().equals(raiderUUID))
src/main/java\net\machiavelli\minecolonytax\raid\RaidManager.java:159:                        .getPlayer(colony.getPermissions().getOwner());
src/main/java\net\machiavelli\minecolonytax\raid\RaidManager.java:269:            if (colony.getPermissions().getOwner().equals(raiderUUID)) {
src/main/java\net\machiavelli\minecolonytax\raid\RaidManager.java:274:            colony.getPermissions().setPlayerRank(raiderUUID, colony.getPermissions().getRankHostile(), level);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 401ms:
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:233:        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:547:        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java:160:        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)
src/main/java/net/machiavelli/minecolonytax\compat\EasyFactionsPermissionSync.java:204:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\commands\ClaimTaxCommand.java:119:                WarData war = WarSystem.ACTIVE_WARS.get(colony.getID());
src/main/java/net/machiavelli/minecolonytax\commands\ClaimTaxCommand.java:121:                    for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:55:public class WarSystem {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:96:    public static final Map<Integer, WarData> ACTIVE_WARS = new ConcurrentHashMap<>();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:301:        ACTIVE_WARS.put(colony.getID(), data);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:570:            endWar(war.getColony()); // clean up â€” bare return would leak the war in ACTIVE_WARS
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:723:            sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), victoryMsg);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:750:            sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), conquestMsg);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:764:            if (TaxConfig.ENABLE_COLONY_TRANSFER.get()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:780:                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:834:                sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), ecoMsg);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:847:                sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), notification);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1182:    public static void transferOwnership(IColony colony, UUID newOwnerUUID) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1196:                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1201:                    WarData war = ACTIVE_WARS.get(colony.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1205:                    sendNotificationToWarParticipants(colony, war != null ? war.getAttackerColony() : null, msg);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1218:            WarData war = ACTIVE_WARS.get(colony.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1219:            sendNotificationToWarParticipants(colony, war != null ? war.getAttackerColony() : null, msg);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1227:        WarData warData = ACTIVE_WARS.get(colony.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1260:        warData = ACTIVE_WARS.remove(colony.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1412:                if (!TaxConfig.ENABLE_COLONY_TRANSFER.get()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1436:                    && TaxConfig.ENABLE_COLONY_TRANSFER.get();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1789:        if (TaxConfig.ENABLE_COLONY_TRANSFER.get() && !defendersWon) { // ATTACKER WINS - Target colony enters occupied
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2035:        for (WarData war : ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2114:                if (!ACTIVE_WARS.containsKey(war.getColony().getID()) ||
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2218:            sendNotificationToWarParticipants(colony, attackerColony, joinPhaseMsg);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2377:    private static void sendNotificationToWarParticipants(IColony defenderColony, IColony attackerColony,
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2415:            WarData war = ACTIVE_WARS.get(defenderColony.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2461:            if (!ACTIVE_WARS.containsKey(warData.getColony().getID()) ||
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2710:        if (ACTIVE_WARS.containsKey(attackerColony.getID())) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2716:        boolean attackerAlreadyWaging = ACTIVE_WARS.values().stream()
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2935:        ACTIVE_WARS.put(targetColony.getID(), warData);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:3341:        for (WarData activeWar : ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:3759:        for (WarData warData : ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:3919:            for (Map.Entry<Integer, WarData> entry : ACTIVE_WARS.entrySet()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:4106:        ACTIVE_WARS.put(e.defenderColonyId, warData);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:4166:                    if (!ACTIVE_WARS.containsKey(colonyId))
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:4168:                    WarData w = ACTIVE_WARS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\db\WarStatsDB.java:545:        final int activeWars    = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.size();
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:35:public class VassalManager {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:291:    public static boolean forceVassalize(IColony vassalColony, UUID overlordUUID, int tributePercent,
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:294:            LOGGER.warn("forceVassalize called with null colony or overlord");
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:122:        if (WarSystem.ACTIVE_WARS.containsKey(targetColony.getID())) {
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:262:        if (WarSystem.ACTIVE_WARS.isEmpty()) {
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:268:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:314:        Collection<WarData> activeWars = WarSystem.ACTIVE_WARS.values();
src/main/java/net/machiavelli/minecolonytax\commands\WarCommands.java:342:        if (!WarSystem.ACTIVE_WARS.containsKey(colony.getID())) {
src/main/java/net/machiavelli/minecolonytax\events\random\EventTriggerSystem.java:189:            if (net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
src/main/java/net/machiavelli/minecolonytax\events\random\EventTriggerSystem.java:194:            for (net.machiavelli.minecolonytax.data.WarData war : net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\event\BlockInteractionFilterHandler.java:109:                && !WarSystem.ACTIVE_WARS.isEmpty()) return true;
src/main/java/net/machiavelli/minecolonytax\event\BlockInteractionFilterHandler.java:235:        if (TaxConfig.isBlockFilterWarsEnabled() && !WarSystem.ACTIVE_WARS.isEmpty()) {
src/main/java/net/machiavelli/minecolonytax\event\BlockInteractionFilterHandler.java:236:            for (WarData warData : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:192:                                                                                        for (WarData war : WarSystem.ACTIVE_WARS
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:211:                                                                                        for (WarData war : WarSystem.ACTIVE_WARS
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1314:                if (WarSystem.ACTIVE_WARS.containsKey(targetColony.getID())) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1349:                if (WarSystem.ACTIVE_WARS.containsKey(targetColony.getID())) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1686:                if (WarSystem.ACTIVE_WARS.isEmpty()) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1692:                                () -> Component.literal("=== War Debug (" + WarSystem.ACTIVE_WARS.size() + " active) ===")
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1695:                for (WarData war : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1758:                WarData war = WarSystem.ACTIVE_WARS.get(colony.getID());
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:1770:                Collection<WarData> activeWars = WarSystem.ACTIVE_WARS.values();
src/main/java/net/machiavelli/minecolonytax\network\packets\ClaimTaxPacket.java:83:                if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java:151:                    WarData activeWar = WarSystem.ACTIVE_WARS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java:153:                        for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\network\packets\RequestColonyDataPacket.java:160:                        boolean isDefender = WarSystem.ACTIVE_WARS.containsKey(colonyId);
src/main/java/net/machiavelli/minecolonytax\event\RaidLoginNotifier.java:77:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\event\RaidKillTracker.java:62:            net.machiavelli.minecolonytax.data.WarData warData = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS
src/main/java/net/machiavelli/minecolonytax\event\RaidKillTracker.java:93:            warData = net.machiavelli.minecolonytax.WarSystem.ACTIVE_WARS.get(colony.getID());
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:280:        WarData activeWar = WarSystem.ACTIVE_WARS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\TaxManager.java:282:            for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEconomyHandler.java:41:            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEconomyHandler.java:231:            for (WarData w : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEconomyHandler.java:251:                WarData war = WarSystem.ACTIVE_WARS.get(losingColony.getID());
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:30:        public static final ForgeConfigSpec.BooleanValue ENABLE_COLONY_TRANSFER;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:507:                ENABLE_COLONY_TRANSFER = BUILDER.comment(
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:590:                                "When enabled and ENABLE_COLONY_TRANSFER is disabled, winning a war will vassalize the losing colony instead of transferring ownership.\n"
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:616:                                                "when ENABLE_COLONY_TRANSFER is enabled. Flip this to true for a no-mercy SMP\n"
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2566:        public static boolean isColonyTransferEnabled() {
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2567:                return ENABLE_COLONY_TRANSFER.get();
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:213:            if (TaxConfig.ENABLE_COLONY_TRANSFER.get() && TaxConfig.KEEP_INVENTORY_ON_LAST_LIFE.get()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:269:                for (WarData war : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:314:        WarData war = WarSystem.ACTIVE_WARS.get(citizenColony.getID());
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:426:        if (!WarSystem.ACTIVE_WARS.isEmpty()) {
src/main/java/net/machiavelli/minecolonytax\event\WarEventHandler.java:427:            for (WarData war : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java:211:        WarData war = WarSystem.ACTIVE_WARS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java:218:        for (WarData activeWar : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionsHealthCheck.java:264:        boolean warOrRaidActive = WarSystem.ACTIVE_WARS.containsKey(colonyId)
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionSnapshot.java:38: *      (i.e., after ACTIVE_WARS.remove() or activeRaids.remove()).
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionSnapshot.java:88:     * (after ACTIVE_WARS.remove() or activeRaids.remove()), otherwise the conflict
src/main/java/net/machiavelli/minecolonytax\permissions\PermissionSnapshot.java:174:        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:60:                        WarData war = WarSystem.ACTIVE_WARS.get(data.getVassalColonyId());
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:140:            WarData war = WarSystem.ACTIVE_WARS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\server\ColonyDataCollector.java:143:                for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
src/main/java/net/machiavelli/minecolonytax\raid\RaidManager.java:142:            if (WarSystem.ACTIVE_WARS.containsKey(colony.getID())) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
The initial search shows `WarSystem.transferOwnership` callers, plus several reflective `setOwner` sites outside that method. I’m reading those regions now to separate legitimate abandoned-colony claims from bypasses of the new primary-colony rule.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-Object -Skip 730 -First 110' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-Object -Skip 1170 -First 80' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java | Select-Object -Skip 120 -First 60' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -Skip 520 -First 70' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/WarSystem.java | Select-Object -Skip 1770 -First 100' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java | Select-Object -Skip 160 -First 90' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 545ms:
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

        int durationDays = TaxConfig.getOccupationDurationDays();
        long now = System.currentTimeMillis();
        long expirationTime = now + (durationDays * 24L * 60L * 60L * 1000L);

        OccupationData data = new OccupationData(
                colonyId, occupierUUID, originalOwner,
                occupierColonyId, colony.getName(),
                now, expirationTime
        );
        ACTIVE_OCCUPATIONS.put(colonyId, data);
        saveData();

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Colony {} is now OCCUPIED by {} for {} days",
                    colony.getName(), occupierUUID, durationDays);
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Notify occupier
            ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
            if (occupier != null) {
                Component occupierMsg = Component.literal("COLONY OCCUPIED")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("\n"))
                        .append(Component.literal("You now occupy " + colony.getName() + "!")
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("\n"))
                        .append(Component.literal("You can collect taxes with /wnt collectoccupation " + colonyId)
                                .withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("\n"))
                        .append(Component.literal("You cannot interact with colony buildings or items.")
                                .withStyle(ChatFormatting.RED))
                        .append(Component.literal("\n"))
                        .append(Component.literal("If the original owner does not reclaim within " + durationDays + " days, full ownership transfers to you!")
                                .withStyle(ChatFormatting.AQUA));
                occupier.sendSystemMessage(occupierMsg);
            }

            // Notify original owner
            ServerPlayer owner = server.getPlayerList().getPlayer(originalOwner);
            if (owner != null) {
                Component ownerMsg = Component.literal("YOUR COLONY HAS BEEN OCCUPIED")
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 551ms:
        allParticipants.addAll(war.getDefenderLives().keySet());

        for (UUID participantUUID : allParticipants) {
            ServerPlayer participant = war.getColony().getWorld().getServer().getPlayerList()
                    .getPlayer(participantUUID);
            if (participant != null) {
                participant.sendSystemMessage(message);
            }
        }
    }

    public static void transferOwnership(IColony colony, UUID newOwnerUUID) {
        if (colony.getWorld() == null || colony.getWorld().getServer() == null)
            return;

        // Siege SMP ruleset: primary colonies are protected from ownership transfer
        // by default. Fall back to vassalization if enabled, so the war still has
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
            return;
        }

        ServerPlayer newOwner = colony.getWorld().getServer().getPlayerList().getPlayer(newOwnerUUID);
        if (newOwner == null)
            return;
        if (colony.getPermissions().setOwner(newOwner)) {
            colony.markDirty();
            Component msg = Component.literal(colony.getName() + " conquered by " + newOwner.getName().getString())
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED).withBold(true));
            WarData war = ACTIVE_WARS.get(colony.getID());
            sendNotificationToWarParticipants(colony, war != null ? war.getAttackerColony() : null, msg);
        } else {
            WARSYSTEM_LOGGER.error("Ownership transfer failed for colony {}", colony.getID());
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 559ms:
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
                for (UUID uuid : war.getAttackerLives().keySet()) {
                    attackerDeducted += WarEconomyHandler.deductTeamBalanceWithReport(uuid, stalematePenalty);
                }
            }

            // Process defender side
            if (war.getDefenderTeamID() != null) {
                defenderDeducted = WarEconomyHandler.deductTeamBalanceWithReport(war.getDefenderTeamID(),
                        stalematePenalty);
            } else {
                for (UUID uuid : war.getDefenderLives().keySet()) {
                    defenderDeducted += WarEconomyHandler.deductTeamBalanceWithReport(uuid, stalematePenalty);
                }
            }

            war.setPenaltyReport("Stalemate penalties applied: " + (stalematePenalty * 100)
                    + "% deducted from all participants (Attackers lost: "
                    + attackerDeducted + ", Defenders lost: " + defenderDeducted + ")");

            // Send message to all participants about the economic penalties
            if (war.getColony().getWorld() != null && war.getColony().getWorld().getServer() != null) {
                Component ecoMsg = Component.literal("War Stalemate: Both sides have been penalized economically!")
                        .withStyle(ChatFormatting.GOLD);
                sendNotificationToWarParticipants(war.getColony(), war.getAttackerColony(), ecoMsg);
            }
        }

        int freezeCycles = TaxConfig.getWarTaxFreezeCycles();
        if (freezeCycles > 0) {
            TaxManager.freezeColonyTax(war.getColony().getID(), freezeCycles);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 556ms:
        }
        return spawned;
    }


    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
        cleanupRaid(raid, true);
        applyCooldown(raid.besiegingPlayerUUID);

        if (attackerWon) {
            if (raid.isReclaim) {
                completeReclaim(raid, colony);
            } else {
                completeBesiegeVictory(raid, colony);
            }
        } else {
            sendToPlayer(raid.besiegingPlayerUUID,
                    Component.literal("The besiege of " + colony.getName() + " failed.")
                            .withStyle(ChatFormatting.RED));
        }
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 557ms:
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 553ms:
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

        // 3. Cannot besiege first (primary) colonies ƒ?" those require full war.
        // Use FCT reverse lookup first: it is immune to corrupted/placeholder owner UUIDs.
        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colonyId);
        if (trackedFirstOwner != null) {
            besieger.sendSystemMessage(Component.literal(
                    "This is the colony owner's primary colony. Declare full war to contest it.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        // Fallback: check via permissions owner in case FCT data is missing for this colony
        UUID ownerUUID = colony.getPermissions().getOwner();
        if (ownerUUID != null && FirstColonyTracker.isFirstColony(ownerUUID, colonyId)) {
            besieger.sendSystemMessage(Component.literal(
                    "This is the colony owner's primary colony. Declare full war to contest it.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        // 4. Cannot besiege an already-besieged colony (unless reclaiming ƒ?" handled separately)
        if (ACTIVE_RAIDS.containsKey(colonyId)) {
            besieger.sendSystemMessage(Component.literal("A besiege raid is already in progress for this colony!")
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java | Select-Object -Skip 340 -First 70' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java | Select-Object -Skip 760 -First 70' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/commands/WntCommands.java | Select-Object -Skip 3380 -First 70' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/TaxConfig.java | Select-Object -Skip 480 -First 150' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/abandon/ColonyClaimingRaidManager.java | Select-Object -Skip 760 -First 70' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java | Select-Object -Skip 280 -First 80' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 536ms:
            ServerPlayer owner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
            if (owner != null) {
                owner.sendSystemMessage(Component.literal("The occupation of " + data.colonyName + " has ended: " + reason)
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    /** Periodic check ƒ?" transfers full ownership to the occupier when the occupation expires. Called via TickScheduler. */
    public static void checkExpiredOccupations() {
        if (ACTIVE_OCCUPATIONS.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        List<Integer> toTransfer = new ArrayList<>();

        for (Map.Entry<Integer, OccupationData> entry : ACTIVE_OCCUPATIONS.entrySet()) {
            OccupationData data = entry.getValue();
            if (data.isExpired()) {
                // Transfer in both cases:
                // - reclamationAttempted=false: deadline passed with no attempt
                // - reclamationAttempted=true:  owner tried to reclaim but failed (lost the war),
                //   occupation expired while still in effect ƒÅ' occupier wins
                toTransfer.add(entry.getKey());
            }
        }

        for (int colonyId : toTransfer) {
            OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
            if (data == null) continue;

            IColony colony = findColonyById(colonyId);
            if (colony == null) {
                LOGGER.warn("Occupied colony {} no longer exists, removing occupation", colonyId);
                ACTIVE_OCCUPATIONS.remove(colonyId);
                continue;
            }

            UUID occupierUUID = data.getOccupierUUID();
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Occupation expired for colony {} - transferring full ownership to {}",
                        colony.getName(), occupierUUID);
            }

            WarSystem.transferOwnership(colony, occupierUUID);

            // Broadcast the transfer
            Component broadcastMsg = Component.literal(colony.getName() + " has been permanently claimed by its occupier!")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(broadcastMsg);
            }

            // Notify original owner specifically
            ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
            if (originalOwner != null) {
                originalOwner.sendSystemMessage(
                        Component.literal("You failed to reclaim " + colony.getName() + " within the deadline. Ownership has been permanently transferred!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }

            ACTIVE_OCCUPATIONS.remove(colonyId);
        }

        if (!toTransfer.isEmpty()) {
            saveData();
        }
    }

Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 550ms:

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

    /**
     * Scans all colonies and removes corrupt [abandoned] permission entries, then
     * fixes any null owners. Safe to call on startup and periodically.
     */
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 514ms:
        } else {
            queueMessage(rel.overlordUUID, msg);
        }
        return tribute;
    }

    /**
     * Forces a colony to become a vassal as a result of war victory, bypassing the
     * normal proposal/acceptance flow. durationHours=0 means permanent.
     */
    public static boolean forceVassalize(IColony vassalColony, UUID overlordUUID, int tributePercent,
            int durationHours) {
        if (vassalColony == null || overlordUUID == null) {
            LOGGER.warn("forceVassalize called with null colony or overlord");
            return false;
        }

        int colonyId = vassalColony.getID();

        if (ACTIVE_VASSALS.containsKey(colonyId)) {
            if (TaxConfig.isDebugLogging()) {
                LOGGER.info("Colony {} is already a vassal, cannot force vassalize again", vassalColony.getName());
            }
            return false;
        }

        long expirationTime = 0;
        if (durationHours > 0) {
            expirationTime = System.currentTimeMillis() + (durationHours * 60L * 60L * 1000L);
        }

        VassalRelation rel = new VassalRelation(
                colonyId,
                overlordUUID,
                tributePercent,
                System.currentTimeMillis(),
                expirationTime,
                true // isWarVassalization
        );
        ACTIVE_VASSALS.put(colonyId, rel);
        saveData();

        String overlordName = getPlayerName(overlordUUID);
        IColony overlordColony = getPrimaryColonyOfPlayer(overlordUUID);
        String overlordColonyName = overlordColony != null ? overlordColony.getName() : "Unknown";

        String durationMsg;
        if (durationHours <= 0) {
            durationMsg = "permanently";
        } else if (durationHours < 24) {
            durationMsg = "for " + durationHours + " hours";
        } else {
            int days = durationHours / 24;
            int remainingHours = durationHours % 24;
            if (remainingHours > 0) {
                durationMsg = "for " + days + " days and " + remainingHours + " hours";
            } else {
                durationMsg = "for " + days + " days";
            }
        }

        Component vassalNotification = Component
                .literal("¶õcƒs" WAR DEFEAT: Your colony has been vassalized by " + overlordName +
                        " (" + overlordColonyName + ") " + durationMsg + "!")
                .withStyle(ChatFormatting.RED);
        Component vassalDetails = Component
                .literal("¶õ7You will pay " + tributePercent + "% of your tax income as tribute.");
        sendToColonyManagers(vassalColony, vassalNotification);
        sendToColonyManagers(vassalColony, vassalDetails);

        Component overlordNotification = Component.literal("¶õaƒs" WAR VICTORY: Colony '" + vassalColony.getName() +
                "' is now your vassal " + durationMsg + "!")
                .withStyle(ChatFormatting.GREEN);
        Component overlordDetails = Component
                .literal("¶õ7They will pay you " + tributePercent + "% of their tax income as tribute.");
        sendOrQueue(overlordUUID, overlordNotification);
        sendOrQueue(overlordUUID, overlordDetails);

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("War vassalization created: Colony {} is now vassal to {} with {}% tribute {}",
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 548ms:
                DEBT_EVENT_CYCLES = BUILDER.comment(
                                "Number of consecutive debt cycles before triggering bandit harassment and guard desertion events on the colony. " +
                                                "0 = disabled. Default: 3.")
                                .defineInRange("DebtEventCycles", 3, 0, 100);

                DEBT_ABANDONMENT_CYCLES = BUILDER.comment(
                                "Number of consecutive max-debt cycles before the colony is forcibly abandoned due to tax bankruptcy. " +
                                                "Only triggers when the colony is at the DebtLimit cap. 0 = disabled. Default: 10.")
                                .defineInRange("DebtAbandonmentCycles", 10, 0, 200);

                DEBT_BLOCKS_WAR = BUILDER.comment(
                                "If true, a colony at maximum debt (DebtLimit) cannot declare war until the debt is cleared. Default: true.")
                                .define("DebtBlocksWar", true);

                DEBT_BLOCKS_SPY = BUILDER.comment(
                                "If true, a colony at maximum debt (DebtLimit) cannot deploy spy missions until the debt is cleared. Default: true.")
                                .define("DebtBlocksSpy", true);

                DEBT_HAPPINESS_FACTOR = BUILDER.comment(
                                "Happiness modifier factor applied to all citizens each cycle the colony is in debt (0.0-1.0 = penalty, values below 1 reduce happiness). " +
                                                "Applied via MineColonies happiness system. Default: 0.6.")
                                .defineInRange("DebtHappinessFactor", 0.6, 0.0, 2.0);

                // ========== War Settings ==========
                BUILDER.push("War Settings");

                ENABLE_COLONY_TRANSFER = BUILDER.comment(
                                "Enable colony ownership transfer when a war is won (true = enable, false = disable).")
                                .define("EnableColonyTransfer", true);

                ENABLE_WAR_ACTIONS = BUILDER.comment("If false, war will not toggle any interaction permissions")
                                .define("EnableWarActions", true);

                WAR_ACCEPTANCE_REQUIRED = BUILDER.comment(
                                "If true, war requests must be manually accepted; if false, wars requests will automatically accept.")
                                .define("WarAcceptanceRequired", true);

                ATTACKER_GRACE_PERIOD_MINUTES = BUILDER.comment("Grace period between declaring wars (minutes)")
                                .defineInRange("AttackerGracePeriodMinutes", 120, 1, 1440); // Default 2 hours

                RAID_GRACE_PERIOD_MINUTES = BUILDER.comment("Grace period between raids (minutes)")
                                .defineInRange("RaidGracePeriodMinutes", 120, 1, 1440); // Default 2h

                MAX_RAID_DURATION_MINUTES = BUILDER.comment("Maximum raid duration (minutes)")
                                .defineInRange("MaxRaidDurationMinutes", 5, 1, 1440);

                ALLOW_OFFLINE_RAIDS = BUILDER
                                .comment("Allow players to raid colonies even if the colony owner is offline.")
                                .define("AllowOfflineRaids", true);

                RAID_PENALTY_PERCENTAGE = BUILDER.comment(
                                "Penalty percentage applied when a raider is killed by a defender during a raid (0.0 - 1.0)")
                                .defineInRange("RaidPenaltyPercentage", 0.25, 0.0, 1.0);

                RAID_DEFENSE_REWARD_PERCENTAGE = BUILDER.comment(
                                "Percentage of killed raider's balance transferred to defending colony for owner/officers to claim (0.0 - 1.0)")
                                .defineInRange("RaidDefenseRewardPercentage", 0.15, 0.0, 1.0);

                WAR_VICTORY_PERCENTAGE = BUILDER.comment(
                                "Percentage of losing players' balance awarded to winning side. " +
                                                "Set equal to WarDefeatPercentage to prevent inflation (pool-based: winners get what losers lose). "
                                                +
                                                "Set to 0.0 to only enable colony transfer (if enabled).\n"
                                                +
                                                "Uses SDMShop balance or colony funds based on what's configured.")
                                .defineInRange("WarVictoryPercentage", 0.20, 0.0, 1.0);

                WAR_DEFEAT_PERCENTAGE = BUILDER.comment(
                                "Percentage that each losing player loses from their balance when defeated in war. " +
                                                "Should match WarVictoryPercentage to prevent money creation/destruction.\n"
                                                +
                                                "Uses SDMShop balance or colony funds based on what's configured.")
                                .defineInRange("WarDefeatPercentage", 0.20, 0.0, 1.0);

                WAR_STALEMATE_PERCENTAGE = BUILDER.comment(
                                "Percentage that all war participants lose from their balance when a war ends in stalemate.\n"
                                                +
                                                "Uses SDMShop balance or colony funds based on what's configured.")
                                .defineInRange("WarStalematePercentage", 0.10, 0.0, 1.0);

                WAR_TAX_FREEZE_HOURS = BUILDER.comment(
                                "[DEPRECATED] Use WarTaxFreezeCycles instead. Duration (in hours) to freeze colony tax generation after a war loss or stalemate.\n"
                                                +
                                                "Set to 0 to disable tax freezing. Ignored if WarTaxFreezeCycles > 0.")
                                .defineInRange("WarTaxFreezeHours", 0, 0, 168); // Max 1 week

                WAR_TAX_FREEZE_CYCLES = BUILDER.comment(
                                "Number of tax cycles to skip after a war loss or stalemate. " +
                                                "Cycle-based freezing ensures consistent behavior regardless of TaxIntervalMinutes. "
                                                +
                                                "At default 60min intervals, 3 cycles = 3 hours. Takes precedence over WarTaxFreezeHours.")
                                .defineInRange("WarTaxFreezeCycles", 3, 0, 20);

                MAX_COMBINED_WAR_PENALTY_PERCENT = BUILDER.comment(
                                "Maximum combined penalty from war exhaustion + active war tax multiplier (0.0-1.0).\n"
                                                +
                                                "Prevents excessive penalty stacking. 0.5 = 50% max total penalty.")
                                .defineInRange("MaxCombinedWarPenaltyPercent", 0.5, 0.0, 1.0);

                MIN_TAX_GENERATION_PERCENT = BUILDER.comment(
                                "Minimum tax generation floor as a percentage of base generation (0.0-1.0). " +
                                                "After all penalties stack (raid, war exhaustion, reparations, events), "
                                                +
                                                "tax income will never drop below this fraction. " +
                                                "Prevents death spirals where colonies can never recover. " +
                                                "0.30 = colonies always earn at least 30% of their base income.")
                                .defineInRange("MinTaxGenerationPercent", 0.30, 0.0, 1.0);

                ENABLE_WAR_VASSALIZATION = BUILDER.comment(
                                "When enabled and ENABLE_COLONY_TRANSFER is disabled, winning a war will vassalize the losing colony instead of transferring ownership.\n"
                                                +
                                                "The losing colony will pay a percentage of their tax income to the winner for a set duration.")
                                .define("EnableWarVassalization", true);

                WAR_VASSALIZATION_DURATION_HOURS = BUILDER
                                .comment("Duration (in hours) that a war vassalization lasts.\n" +
                                                "After this time, the vassalization automatically ends. Set to 0 for permanent vassalization until manually revoked.")
                                .defineInRange("WarVassalizationDurationHours", 168, 0, 8760); // Default 1 week, max 1
                                                                                               // year

                WAR_VASSALIZATION_TRIBUTE_PERCENTAGE = BUILDER.comment(
                                "Percentage of the vassal colony's tax income paid to the victor as tribute (1-100).\n"
                                                +
                                                "Lowered default to 15% to avoid stacking too harshly with other war penalties. "
                                                +
                                                "Set VassalizationReplacesReparations=true to make tribute the only penalty.")
                                .defineInRange("WarVassalizationTributePercentage", 15, 1, 100); // Default 15%

                ENABLE_PRIMARY_COLONY_TRANSFER = BUILDER.comment(
                                "If false (default), a player's first colony (Primary) is protected from ownership transfer.\n"
                                                +
                                                "Primary colonies can still be tax-occupied via besiege or vassalized via full war,\n"
                                                +
                                                "but the deed never moves to a new owner. Secondary colonies are always transferable\n"
                                                +
                                                "when ENABLE_COLONY_TRANSFER is enabled. Flip this to true for a no-mercy SMP\n"
                                                +
                                                "where even home bases can be permanently lost.")
                                .define("EnablePrimaryColonyTransfer", false);

                // ========== Colony Occupation Settings ==========
                BUILDER.push("Colony Occupation");

                ENABLE_OCCUPATION_SYSTEM = BUILDER.comment(
                                "When enabled and colony transfer is enabled, winning a war puts the colony into an OCCUPIED state\n"
                                                + "instead of immediately transferring ownership. The occupier can collect taxes but cannot\n"
                                                + "interact with colony buildings or items. The original owner has a configurable number of days\n"
                                                + "to wage a reclamation war. If no reclamation is attempted, full ownership transfers automatically.")
                                .define("EnableOccupationSystem", true);

Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 537ms:
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
            permissions.setPermission(neutralRank, Action.PICKUP_ITEM, true);
            permissions.setPermission(neutralRank, Action.TOSS_ITEM, true);
            
            // Keep building restrictions to prevent griefing
            permissions.setPermission(neutralRank, Action.BREAK_BLOCKS, false);
            permissions.setPermission(neutralRank, Action.PLACE_BLOCKS, false);
            
            if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} permissions safely updated - claimer is Officer, all players preserved", colony.getName());
            
            if (isFormerMember) {
                if (TaxConfig.isNormalLogging()) LOGGER.info("RECLAIMED: Former owner/officer {} has reclaimed colony {} and set as Officer",
                    claimingPlayer.getName().getString(), colony.getName());
            } else {
                if (TaxConfig.isNormalLogging()) LOGGER.info("CLAIMED: New claimer {} has claimed colony {} and set as Officer",
                    claimingPlayer.getName().getString(), colony.getName());
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 568ms:
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "playerColoniesMap|isFirstColony|getFirstColonyOwner|HashMap|Concurrent|synchronized" src/main/java/net/machiavelli/minecolonytax/FirstColonyTracker.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"transferOwnership\\(|setOwner\\(|getMethod\\(\\\"setOwner\\\"|canBesiegePermanentClaim|FirstColonyTracker\\.isFirstColony|FirstColonyTracker\\.getFirstColonyOwner\" src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/FirstColonyTracker.java | Select-Object -Skip 1 -First 140' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 379ms:
18:import java.util.concurrent.ConcurrentHashMap;
38:    private static final Map<UUID, List<Integer>> playerColoniesMap = new ConcurrentHashMap<>();
54:        List<Integer> colonies = playerColoniesMap.computeIfAbsent(playerUUID, k -> new ArrayList<>());
87:        List<Integer> colonies = playerColoniesMap.get(playerUUID);
100:            playerColoniesMap.remove(playerUUID);
134:        List<Integer> colonies = playerColoniesMap.get(playerUUID);
149:    public static boolean isFirstColony(UUID playerUUID, int colonyID) {
166:        List<Integer> colonies = playerColoniesMap.get(playerUUID);
181:    public static UUID getFirstColonyOwner(int colonyId) {
182:        for (Map.Entry<UUID, List<Integer>> entry : playerColoniesMap.entrySet()) {
202:        List<Integer> colonies = playerColoniesMap.get(playerUUID);
227:        Map<UUID, List<Integer>> ownerToColonyIds = new HashMap<>();
257:            List<Integer> alreadyTracked = playerColoniesMap.getOrDefault(ownerUUID, Collections.emptyList());
262:                playerColoniesMap.put(ownerUUID, newList);
270:                List<Integer> tracked = playerColoniesMap.get(ownerUUID);
303:                GSON.toJson(playerColoniesMap, writer);
323:            Type type = new TypeToken<ConcurrentHashMap<UUID, List<Integer>>>() {
328:                playerColoniesMap.clear();
329:                playerColoniesMap.putAll(loadedData);
330:                LOGGER.info("Loaded first colony data for {} players", playerColoniesMap.size());
341:        playerColoniesMap.clear();
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 exited 1 in 387ms:
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 
rg: regex parse error:
    (?:transferOwnership\(|setOwner\(|getMethod\(\)
    ^
error: unclosed group

2026-05-25T01:46:10.312626Z ERROR codex_core::tools::router: error=Exit code: 1
Wall time: 0.4 seconds
Output:
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 
rg: regex parse error:
    (?:transferOwnership\(|setOwner\(|getMethod\(\)
    ^
error: unclosed group

 succeeded in 529ms:

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.machiavelli.minecolonytax.TaxConfig;

/**
 * Tracks the first (primary) colony for each player and the creation order of
 * their colonies.
 * When a player creates multiple colonies, their first colony remains their
 * primary one where they are owner.
 * In subsequent colonies, they are set to officer rank.
 *
 * If the first colony is deleted, the next-oldest colony automatically becomes
 * the new primary colony.
 */
public class FirstColonyTracker {

    private static final Logger LOGGER = LogManager.getLogger(FirstColonyTracker.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "config/warntax/firstColonyData.json";

    // Maps player UUID -> list of colony IDs in creation order (oldest first)
    private static final Map<UUID, List<Integer>> playerColoniesMap = new ConcurrentHashMap<>();

    /**
     * Adds a colony to a player's tracked colonies.
     * If this is their first colony, it becomes their primary colony.
     *
     * @param playerUUID The player's UUID
     * @param colonyID   The colony ID to add
     * @return true if this is the player's first colony, false otherwise
     */
    public static boolean addColony(UUID playerUUID, int colonyID) {
        if (playerUUID == null) {
            LOGGER.warn("Attempted to add colony with null player UUID");
            return false;
        }

        List<Integer> colonies = playerColoniesMap.computeIfAbsent(playerUUID, k -> new ArrayList<>());

        // Prevent duplicate entries
        if (colonies.contains(colonyID)) {
            LOGGER.debug("Colony {} already tracked for player {}", colonyID, playerUUID);
            return colonies.size() == 1 && colonies.get(0) == colonyID;
        }

        colonies.add(colonyID);
        boolean isFirst = colonies.size() == 1;

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Added colony {} for player {} ({})",
                    colonyID, playerUUID, isFirst ? "PRIMARY" : "SECONDARY #" + colonies.size());
        }

        saveData();
        return isFirst;
    }

    /**
     * Removes a colony from tracking.
     * If this was the first colony, the next-oldest colony becomes the new first.
     *
     * @param playerUUID The player's UUID
     * @param colonyID   The colony ID to remove
     * @return The new first colony ID if it changed, or null if no change
     */
    public static Integer removeColony(UUID playerUUID, int colonyID) {
        if (playerUUID == null) {
            return null;
        }

        List<Integer> colonies = playerColoniesMap.get(playerUUID);
        if (colonies == null || colonies.isEmpty()) {
            return null;
        }

        boolean wasFirst = !colonies.isEmpty() && colonies.get(0) == colonyID;
        colonies.remove(Integer.valueOf(colonyID));

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Removed colony {} from player {} tracking", colonyID, playerUUID);
        }

        if (colonies.isEmpty()) {
            playerColoniesMap.remove(playerUUID);
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Player {} has no more tracked colonies", playerUUID);
            }
            saveData();
            return null;
        }

        saveData();

        // If the removed colony was the first, return the new first colony ID
        if (wasFirst && !colonies.isEmpty()) {
            Integer newFirstColony = colonies.get(0);
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Colony {} is now the PRIMARY colony for player {} (promoted after deletion)",
                        newFirstColony, playerUUID);
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

Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n 'transferOwnership\\(|setOwner\\(|getMethod\\(\"setOwner\"|canBesiegePermanentClaim|FirstColonyTracker\\.isFirstColony|FirstColonyTracker\\.getFirstColonyOwner' src/main/java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'Get-Content src/main/java/net/machiavelli/minecolonytax/FirstColonyTracker.java | Select-Object -Skip 140 -First 70' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 295ms:
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:189:        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colonyId);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeManager.java:198:        if (ownerUUID != null && FirstColonyTracker.isFirstColony(ownerUUID, colonyId)) {
src/main/java\net\machiavelli\minecolonytax\abandon\ColonyAbandonmentManager.java:465:            // NOTE: We don't explicitly call setOwner() to avoid API compatibility issues
src/main/java\net\machiavelli\minecolonytax\commands\OfficerTrackingDebugCommand.java:117:        UUID fctOwner = FirstColonyTracker.getFirstColonyOwner(colony.getID());
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:774:                    transferOwnership(war.getColony(), war.getAttacker());
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1182:    public static void transferOwnership(IColony colony, UUID newOwnerUUID) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1214:        if (colony.getPermissions().setOwner(newOwner)) {
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1797:                transferOwnership(war.getColony(), war.getAttacker());
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:1854:                transferOwnership(war.getAttackerColony(), war.getDefender());
src/main/java\net\machiavelli\minecolonytax\WarSystem.java:2730:        boolean isPrimaryColony = FirstColonyTracker.isFirstColony(targetOwnerUUID, targetColony.getID());
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:128:            firstColony.getPermissions().setOwner(player);
src/main/java\net\machiavelli\minecolonytax\event\ColonyOwnershipHandler.java.disabled:222:            colony.getPermissions().setOwner(player);
src/main/java\net\machiavelli\minecolonytax\occupation\OccupationManager.java:386:            WarSystem.transferOwnership(colony, occupierUUID);
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:41:        if (FirstColonyTracker.isFirstColony(currentOwner, colony.getID())) {
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:54:    public static boolean canBesiegePermanentClaim(IColony colony) {
src/main/java\net\machiavelli\minecolonytax\permissions\ColonyTierGuard.java:67:        if (owner != null && FirstColonyTracker.isFirstColony(owner, colony.getID())) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 389ms:

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

        List<Integer> colonies = playerColoniesMap.get(playerUUID);
        return colonies == null ? 0 : colonies.size();
    }

    /**
     * Bootstraps tracking data from an already-running server's colonies.
     *
     * Called once after MineColonies finishes loading on first install (or when
     * firstColonyData.json is absent/incomplete). Uses colony ID as a proxy for
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"public static boolean canTransferOwnership|UUID currentOwner|FirstColonyTracker\\.isFirstColony|public static void transferOwnership|forceVassalize|ServerPlayer newOwner|WarSystem\\.transferOwnership|startOccupation|completeBesiegeVictory|setOwnerMethod\\.invoke|ENABLE_PRIMARY_COLONY_TRANSFER|SUPPRESS_COLONY_LEVITATION\" src/main/java/net/machiavelli/minecolonytax src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "getFirstColonyOwner|isFirstColony" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java src/main/java/net/machiavelli/minecolonytax/WarSystem.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n 'getMethod\\(\"setOwner\"|method\\.getName\\(\\)\\.equals\\(\"setOwner\"|setOwner\\(' src/main/java/net/machiavelli/minecolonytax/abandon src/main/java/net/machiavelli/minecolonytax/commands" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 408ms:
src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java:41:        if (FirstColonyTracker.isFirstColony(currentOwner, colony.getID())) {
src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java:67:        if (owner != null && FirstColonyTracker.isFirstColony(owner, colony.getID())) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:189:        UUID trackedFirstOwner = FirstColonyTracker.getFirstColonyOwner(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:198:        if (ownerUUID != null && FirstColonyTracker.isFirstColony(ownerUUID, colonyId)) {
src/main/java/net/machiavelli/minecolonytax/WarSystem.java:2730:        boolean isPrimaryColony = FirstColonyTracker.isFirstColony(targetOwnerUUID, targetColony.getID());
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 409ms:
src/main/java/net/machiavelli/minecolonytax/abandon\ColonyAbandonmentManager.java:465:            // NOTE: We don't explicitly call setOwner() to avoid API compatibility issues
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 422ms:
src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java:33:    public static boolean canTransferOwnership(IColony colony) {
src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java:37:        UUID currentOwner = colony.getPermissions().getOwner();
src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java:41:        if (FirstColonyTracker.isFirstColony(currentOwner, colony.getID())) {
src/main/java/net/machiavelli/minecolonytax/permissions/ColonyTierGuard.java:67:        if (owner != null && FirstColonyTracker.isFirstColony(owner, colony.getID())) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:198:        if (ownerUUID != null && FirstColonyTracker.isFirstColony(ownerUUID, colonyId)) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:534:                completeBesiegeVictory(raid, colony);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:543:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:547:        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyClaimingRaidManager.java:787:                setOwnerMethod.invoke(permissions, claimingPlayer.getUUID());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:121:    public static void startOccupation(IColony colony, UUID occupierUUID, IColony attackerColony) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:123:            LOGGER.warn("startOccupation called with null colony or occupier");
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:386:            WarSystem.transferOwnership(colony, occupierUUID);
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java:158:            UUID currentOwner = permissions.getOwner();
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java:603:            UUID currentOwner = permissions.getOwner();
src/main/java/net/machiavelli/minecolonytax\abandon\ColonyAbandonmentManager.java:788:                        setOwnerMethod.invoke(permissions, player.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:768:                    net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:780:                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1182:    public static void transferOwnership(IColony colony, UUID newOwnerUUID) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1196:                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1211:        ServerPlayer newOwner = colony.getWorld().getServer().getPlayerList().getPlayer(newOwnerUUID);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1792:                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1804:                net.machiavelli.minecolonytax.occupation.OccupationManager.startOccupation(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:2730:        boolean isPrimaryColony = FirstColonyTracker.isFirstColony(targetOwnerUUID, targetColony.getID());
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:291:    public static boolean forceVassalize(IColony vassalColony, UUID overlordUUID, int tributePercent,
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:294:            LOGGER.warn("forceVassalize called with null colony or overlord");
src/main/java/net/machiavelli/minecolonytax\event\ColonyOwnershipHandler.java.disabled:218:        UUID currentOwner = colony.getPermissions().getOwner();
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:33:    public static boolean canTransferOwnership(IColony colony) {
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:37:        UUID currentOwner = colony.getPermissions().getOwner();
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:41:        if (FirstColonyTracker.isFirstColony(currentOwner, colony.getID())) {
src/main/java/net/machiavelli/minecolonytax\permissions\ColonyTierGuard.java:67:        if (owner != null && FirstColonyTracker.isFirstColony(owner, colony.getID())) {
src/main/java/net/machiavelli/minecolonytax\commands\WntCommands.java:3420:                                                                setOwnerMethod.invoke(permissions, systemOwner);
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:76:        public static final ForgeConfigSpec.BooleanValue ENABLE_PRIMARY_COLONY_TRANSFER;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:97:        public static final ForgeConfigSpec.BooleanValue SUPPRESS_COLONY_LEVITATION;
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:609:                ENABLE_PRIMARY_COLONY_TRANSFER = BUILDER.comment(
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:1910:                SUPPRESS_COLONY_LEVITATION = BUILDER.comment(
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2611:                return ENABLE_PRIMARY_COLONY_TRANSFER.get();
src/main/java/net/machiavelli/minecolonytax\TaxConfig.java:2961:                return SUPPRESS_COLONY_LEVITATION.get();
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
STATUS: REWORK

- High: `transferOwnership` is still `void`, but it now has three outcomes: transferred, vassalized, or denied. Existing callers continue as if transfer succeeded. `OccupationManager.checkExpiredOccupations` broadcasts “permanently claimed” and removes occupation even when the guard blocked deed transfer; `handleVictoryRewards` also records “Colony transferred” after a guarded fallback.

- High: The guard is not airtight for “primary” detection. It only checks `FirstColonyTracker.isFirstColony(currentOwner, colonyId)`, while existing besiege code uses `getFirstColonyOwner(colonyId)` first because permissions owner can be corrupted/placeholder/null. A primary colony with stale/null/system owner can slip through.

- Medium: Other ownership mutation paths bypass the central guard: reflective `setOwner` calls in abandoned/claiming repair flows, especially `ColonyClaimingRaidManager`. Some may be intentionally non-war, but the “every deed flip routes through guard” claim is currently false.

- Medium: Vassalization fallback does not fire at full-war victory when occupation mode is enabled; it only fires later when occupation expires. If the intended rule is “primary loses full war => vassalized, not occupied-to-transfer,” this branch bypasses the fallback.

- Low: `transferOwnership(null, …)` still NPEs before reaching the null-safe guard, and the TaxConfig diff includes unrelated Easy Factions config plus a levitation default change, increasing regression risk for a Step 1 guard change.

Concrete fixes:
- `WarSystem.java:1182`: make `transferOwnership` return an enum/result (`TRANSFERRED`, `VASSALIZED`, `DENIED`) and update callers at `WarSystem.java:1797`, `WarSystem.java:1854`, and `OccupationManager.java:386` to message/remove state based on the actual result.
- `ColonyTierGuard.java:37-41`: check `FirstColonyTracker.getFirstColonyOwner(colony.getID())` before permissions owner, then fall back to owner-based lookup.
- `ColonyClaimingRaidManager.java:787` and `ColonyAbandonmentManager.java:788`: either route real owner changes through the guard or explicitly document/exempt abandoned/system-owner repair paths.
tokens used
78.750
STATUS: REWORK

- High: `transferOwnership` is still `void`, but it now has three outcomes: transferred, vassalized, or denied. Existing callers continue as if transfer succeeded. `OccupationManager.checkExpiredOccupations` broadcasts “permanently claimed” and removes occupation even when the guard blocked deed transfer; `handleVictoryRewards` also records “Colony transferred” after a guarded fallback.

- High: The guard is not airtight for “primary” detection. It only checks `FirstColonyTracker.isFirstColony(currentOwner, colonyId)`, while existing besiege code uses `getFirstColonyOwner(colonyId)` first because permissions owner can be corrupted/placeholder/null. A primary colony with stale/null/system owner can slip through.

- Medium: Other ownership mutation paths bypass the central guard: reflective `setOwner` calls in abandoned/claiming repair flows, especially `ColonyClaimingRaidManager`. Some may be intentionally non-war, but the “every deed flip routes through guard” claim is currently false.

- Medium: Vassalization fallback does not fire at full-war victory when occupation mode is enabled; it only fires later when occupation expires. If the intended rule is “primary loses full war => vassalized, not occupied-to-transfer,” this branch bypasses the fallback.

- Low: `transferOwnership(null, …)` still NPEs before reaching the null-safe guard, and the TaxConfig diff includes unrelated Easy Factions config plus a levitation default change, increasing regression risk for a Step 1 guard change.

Concrete fixes:
- `WarSystem.java:1182`: make `transferOwnership` return an enum/result (`TRANSFERRED`, `VASSALIZED`, `DENIED`) and update callers at `WarSystem.java:1797`, `WarSystem.java:1854`, and `OccupationManager.java:386` to message/remove state based on the actual result.
- `ColonyTierGuard.java:37-41`: check `FirstColonyTracker.getFirstColonyOwner(colony.getID())` before permissions owner, then fall back to owner-based lookup.
- `ColonyClaimingRaidManager.java:787` and `ColonyAbandonmentManager.java:788`: either route real owner changes through the guard or explicitly document/exempt abandoned/system-owner repair paths.
