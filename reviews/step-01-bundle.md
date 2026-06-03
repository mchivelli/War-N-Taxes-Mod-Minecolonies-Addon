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
