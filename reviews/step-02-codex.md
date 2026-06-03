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
session id: 019e5cd5-dbe9-7061-8c4b-6e7717809885
--------
user
You are reviewing step 2 of an 11-step Java refactor for a Minecraft 1.20.1 Forge mod (War 'N Taxes, a MineColonies addon). Step 2 introduces an OccupationMode enum (TRANSFER_PENDING for secondaries, TAX_ONLY for primaries), 7-day TTL for TAX_ONLY, dual-mode expiry behavior, and changes WarSystem.transferOwnership to return boolean so callers know whether the deed actually moved. The OccupationManager.checkExpiredOccupations is now branched: TAX_ONLY auto-reclaims, TRANSFER_PENDING attempts transfer and falls back gracefully if the transfer was denied. A new reclaimByOriginalOwner method supports counter-besiege success. NOTE: TaxConfig.java and WarSystem.java diffs include step-1 changes too — please focus only on step 2 additions. Review for: correctness of mode branching, backward-compatibility of OccupationData JSON deserialization (the mode field is null for old save files), null-safety, edge cases (e.g. what if FirstColonyTracker has stale data, what if reclamationAttempted=true with TAX_ONLY mode, what if multiple expirations race), and whether the reclaimByOriginalOwner method needs guarding. Respond with: (1) STATUS: APPROVE / REWORK / REJECT, (2) up to 5 bullet findings ranked by severity, (3) up to 3 concrete file:line fix suggestions. Max 350 words total.

<stdin>
## STEP 2 REVIEW BUNDLE — Tax-occupation mode rework

Step 2 adds an OccupationMode enum (TRANSFER_PENDING / TAX_ONLY), branches the expiry handler by mode, makes WarSystem.transferOwnership return a boolean so callers know if the deed actually moved, adds PrimaryColonyTaxOccupationDays config (default 7), and adds a reclaimByOriginalOwner() method for counter-besiege success. Files 1 and 2 (TaxConfig, WarSystem) also include the step-1 hunks since those are still uncommitted; please focus on step 2 specifically.

### DIFF: TaxConfig.java (step 1 + step 2 hunks; step 2 = PRIMARY_COLONY_TAX_OCCUPATION_DAYS only)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java b/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
index f6f21ab..69fc131 100644
--- a/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
+++ b/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
@@ -71,6 +71,10 @@ public class TaxConfig {
         public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_VASSALIZATION;
         public static final ForgeConfigSpec.IntValue WAR_VASSALIZATION_DURATION_HOURS;
         public static final ForgeConfigSpec.IntValue WAR_VASSALIZATION_TRIBUTE_PERCENTAGE;
+
+        // Colony Tier Protection (Siege SMP ruleset)
+        public static final ForgeConfigSpec.BooleanValue ENABLE_PRIMARY_COLONY_TRANSFER;
+        public static final ForgeConfigSpec.IntValue PRIMARY_COLONY_TAX_OCCUPATION_DAYS;
         public static final ForgeConfigSpec.IntValue JOIN_PHASE_DURATION_MINUTES;
         public static final ForgeConfigSpec.BooleanValue WAR_ACCEPTANCE_REQUIRED;
         public static final ForgeConfigSpec.BooleanValue KEEP_INVENTORY_ON_LAST_LIFE;
@@ -134,6 +138,12 @@ public class TaxConfig {
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
@@ -597,6 +607,32 @@ public class TaxConfig {
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
+                PRIMARY_COLONY_TAX_OCCUPATION_DAYS = BUILDER.comment(
+                                "Duration (real-time days) a Primary colony stays tax-occupied after a successful besiege.\n"
+                                                +
+                                                "During this window the besieger collects 100% of the colony's taxes and the original\n"
+                                                +
+                                                "owner is locked out of GUI/permissions, but the deed never transfers. If the owner\n"
+                                                +
+                                                "does not mount a successful counter-besiege within this window, the occupation\n"
+                                                +
+                                                "auto-reclaims (taxes route back to the owner). Secondary colonies use the standard\n"
+                                                +
+                                                "OccupationDurationDays config and DO transfer on expiry.")
+                                .defineInRange("PrimaryColonyTaxOccupationDays", 7, 1, 90);
+
                 // ========== Colony Occupation Settings ==========
                 BUILDER.push("Colony Occupation");
 
@@ -832,6 +868,28 @@ public class TaxConfig {
 
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
 
@@ -1865,15 +1923,16 @@ public class TaxConfig {
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
@@ -2563,6 +2622,14 @@ public class TaxConfig {
                 return ENABLE_WAR_VASSALIZATION.get();
         }
 
+        public static boolean isPrimaryColonyTransferEnabled() {
+                return ENABLE_PRIMARY_COLONY_TRANSFER.get();
+        }
+
+        public static int getPrimaryColonyTaxOccupationDays() {
+                return PRIMARY_COLONY_TAX_OCCUPATION_DAYS.get();
+        }
+
         public static int getWarVassalizationDurationHours() {
                 return WAR_VASSALIZATION_DURATION_HOURS.get();
         }
@@ -2725,6 +2792,22 @@ public class TaxConfig {
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

### DIFF: WarSystem.java (step 1 + step 2 hunks; step 2 = boolean return + null guard)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/WarSystem.java b/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
index 9158174..fff5562 100644
--- a/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
+++ b/src/main/java/net/machiavelli/minecolonytax/WarSystem.java
@@ -1179,20 +1179,62 @@ public class WarSystem {
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
+                            + " is a Primary colony — vassalized instead of conquered.")
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
 
@@ -3679,10 +3721,26 @@ public class WarSystem {
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

### DIFF: OccupationManager.java (step 2 ONLY — enum, mode field, dual-mode startOccupation, dual-mode expiry, reclaim method)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java b/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
index e8d44fc..d460830 100644
--- a/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java
@@ -47,6 +47,19 @@ public class OccupationManager {
 
     private static MinecraftServer serverInstance;
 
+    /**
+     * How an occupation resolves when its timer expires.
+     *
+     * Primary colonies always run in TAX_ONLY (deed never moves), unless
+     * EnablePrimaryColonyTransfer is on. Secondaries run in TRANSFER_PENDING.
+     */
+    public enum OccupationMode {
+        /** Expiry transfers full ownership to the occupier. Default for secondaries. */
+        TRANSFER_PENDING,
+        /** Expiry auto-reclaims — taxes route back to original owner, deed never moves. Primary colonies. */
+        TAX_ONLY
+    }
+
     public static class OccupationData {
         public final int colonyId;
         public final String occupierUUID;
@@ -57,10 +70,19 @@ public class OccupationManager {
         public final String colonyName;
         public boolean reclamationAttempted;
         public long lastTaxCollectionTime;
+        /** Null on save files written before the Siege SMP upgrade — see {@link #getMode()}. */
+        public OccupationMode mode;
 
         public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
                               int occupierColonyId, String colonyName,
                               long startTime, long expirationTime) {
+            this(colonyId, occupierUUID, originalOwnerUUID, occupierColonyId, colonyName,
+                    startTime, expirationTime, OccupationMode.TRANSFER_PENDING);
+        }
+
+        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
+                              int occupierColonyId, String colonyName,
+                              long startTime, long expirationTime, OccupationMode mode) {
             this.colonyId = colonyId;
             this.occupierUUID = occupierUUID.toString();
             this.originalOwnerUUID = originalOwnerUUID.toString();
@@ -70,6 +92,12 @@ public class OccupationManager {
             this.expirationTime = expirationTime;
             this.reclamationAttempted = false;
             this.lastTaxCollectionTime = 0;
+            this.mode = mode;
+        }
+
+        /** Returns the mode, defaulting to TRANSFER_PENDING for legacy save files. */
+        public OccupationMode getMode() {
+            return mode != null ? mode : OccupationMode.TRANSFER_PENDING;
         }
 
         public boolean isExpired() {
@@ -135,29 +163,53 @@ public class OccupationManager {
         UUID originalOwner = colony.getPermissions().getOwner();
         int occupierColonyId = attackerColony != null ? attackerColony.getID() : -1;
 
-        int durationDays = TaxConfig.getOccupationDurationDays();
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
         long now = System.currentTimeMillis();
         long expirationTime = now + (durationDays * 24L * 60L * 60L * 1000L);
 
         OccupationData data = new OccupationData(
                 colonyId, occupierUUID, originalOwner,
                 occupierColonyId, colony.getName(),
-                now, expirationTime
+                now, expirationTime, mode
         );
         ACTIVE_OCCUPATIONS.put(colonyId, data);
         saveData();
 
         if (TaxConfig.isNormalLogging()) {
-            LOGGER.info("Colony {} is now OCCUPIED by {} for {} days",
-                    colony.getName(), occupierUUID, durationDays);
+            LOGGER.info("Colony {} is now OCCUPIED by {} for {} days [mode={}]",
+                    colony.getName(), occupierUUID, durationDays, mode);
         }
 
+        final boolean isTaxOnly = mode == OccupationMode.TAX_ONLY;
+        final String expiryConsequence = isTaxOnly
+                ? "If not reclaimed in time, the occupation lapses and taxes route back to the owner."
+                : "If the original owner does not reclaim within " + durationDays + " days, full ownership transfers to you!";
+        final String ownerStakes = isTaxOnly
+                ? "This is your Primary colony — the deed is safe. Reclaim within " + durationDays
+                        + " days or the occupation simply ends."
+                : "If you do not reclaim, ownership will permanently transfer!";
+
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server != null) {
             // Notify occupier
             ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
             if (occupier != null) {
-                Component occupierMsg = Component.literal("COLONY OCCUPIED")
+                Component occupierMsg = Component.literal(isTaxOnly ? "PRIMARY COLONY TAX-OCCUPIED" : "COLONY OCCUPIED")
                         .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                         .append(Component.literal("\n"))
                         .append(Component.literal("You now occupy " + colony.getName() + "!")
@@ -169,7 +221,7 @@ public class OccupationManager {
                         .append(Component.literal("You cannot interact with colony buildings or items.")
                                 .withStyle(ChatFormatting.RED))
                         .append(Component.literal("\n"))
-                        .append(Component.literal("If the original owner does not reclaim within " + durationDays + " days, full ownership transfers to you!")
+                        .append(Component.literal(expiryConsequence)
                                 .withStyle(ChatFormatting.AQUA));
                 occupier.sendSystemMessage(occupierMsg);
             }
@@ -177,7 +229,9 @@ public class OccupationManager {
             // Notify original owner
             ServerPlayer owner = server.getPlayerList().getPlayer(originalOwner);
             if (owner != null) {
-                Component ownerMsg = Component.literal("YOUR COLONY HAS BEEN OCCUPIED")
+                Component ownerMsg = Component.literal(isTaxOnly
+                                ? "YOUR PRIMARY COLONY HAS BEEN TAX-OCCUPIED"
+                                : "YOUR COLONY HAS BEEN OCCUPIED")
                         .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                         .append(Component.literal("\n"))
                         .append(Component.literal("Your colony " + colony.getName() + " has been occupied!")
@@ -189,7 +243,7 @@ public class OccupationManager {
                         .append(Component.literal("You have " + durationDays + " days to wage a reclamation war with /wnt wagewar " + colonyId)
                                 .withStyle(ChatFormatting.GREEN))
                         .append(Component.literal("\n"))
-                        .append(Component.literal("If you do not reclaim, ownership will permanently transfer!")
+                        .append(Component.literal(ownerStakes)
                                 .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                 owner.sendSystemMessage(ownerMsg);
             }
@@ -378,26 +432,65 @@ public class OccupationManager {
             }
 
             UUID occupierUUID = data.getOccupierUUID();
-            if (TaxConfig.isNormalLogging()) {
-                LOGGER.info("Occupation expired for colony {} - transferring full ownership to {}",
-                        colony.getName(), occupierUUID);
-            }
+            OccupationMode mode = data.getMode();
 
-            WarSystem.transferOwnership(colony, occupierUUID);
+            if (mode == OccupationMode.TAX_ONLY) {
+                // Primary colony auto-reclaim — deed never moves, taxes simply revert
+                // to the original owner. Friendly notification on both sides.
+                if (TaxConfig.isNormalLogging()) {
+                    LOGGER.info("Tax-only occupation expired for primary colony {} - auto-reclaiming", colony.getName());
+                }
 
-            // Broadcast the transfer
-            Component broadcastMsg = Component.literal(colony.getName() + " has been permanently claimed by its occupier!")
-                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
-            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
-                p.sendSystemMessage(broadcastMsg);
-            }
+                ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
+                if (originalOwner != null) {
+                    originalOwner.sendSystemMessage(
+                            Component.literal("Your Primary colony " + colony.getName()
+                                            + " has been auto-reclaimed — the besieger's hold has lapsed and your taxes now route to you again.")
+                                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
+                }
+                ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
+                if (occupier != null) {
+                    occupier.sendSystemMessage(
+                            Component.literal("Your tax-occupation of " + colony.getName()
+                                            + " has lapsed — the owner reclaims it automatically (Primary colonies cannot be permanently claimed).")
+                                    .withStyle(ChatFormatting.GOLD));
+                }
+            } else {
+                // Standard TRANSFER_PENDING flow for secondaries. Only broadcast the
+                // permanent-claim message if the transfer actually succeeded —
+                // ColonyTierGuard or other failures must not produce a misleading message.
+                if (TaxConfig.isNormalLogging()) {
+                    LOGGER.info("Occupation expired for colony {} - attempting full ownership transfer to {}",
+                            colony.getName(), occupierUUID);
+                }
 
-            // Notify original owner specifically
-            ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
-            if (originalOwner != null) {
-                originalOwner.sendSystemMessage(
-                        Component.literal("You failed to reclaim " + colony.getName() + " within the deadline. Ownership has been permanently transferred!")
-                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
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
+                                                + " has lapsed — the deed remains with you.")
+                                        .withStyle(ChatFormatting.GREEN));
+                    }
+                }
             }
 
             ACTIVE_OCCUPATIONS.remove(colonyId);
@@ -408,6 +501,40 @@ public class OccupationManager {
         }
     }
 
+    /**
+     * Manually end a tax-only occupation early — called when the owner successfully
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
+                        Component.literal(data.colonyName + " has been reclaimed by its original owner — your hold has been broken.")
+                                .withStyle(ChatFormatting.RED));
+            }
+        }
+        if (TaxConfig.isNormalLogging()) {
+            LOGGER.info("Occupation reclaimed for colony {} ({}) by original owner", data.colonyName, colonyId);
+        }
+        return true;
+    }
+
     /** Returns true if the occupier should be blocked from interacting with the occupied colony's buildings and items. */
     public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
         OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
@@ -424,21 +551,24 @@ public class OccupationManager {
     }
 
     public static void saveData() {
-        try {
-            Path dir = Paths.get("config/warntax");
-            if (!Files.exists(dir)) {
-                Files.createDirectories(dir);
-            }
-
-            OccupationSaveData saveData = new OccupationSaveData();
-            saveData.occupations = new ArrayList<>(ACTIVE_OCCUPATIONS.values());
-
-            try (Writer writer = new FileWriter(STORAGE_FILE)) {
-                GSON.toJson(saveData, writer);
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
             }
-        } catch (Exception e) {
-            LOGGER.error("Failed to save occupation data: {}", e.getMessage());
-        }
+        });
     }
 
     public static void loadData() {
```
</stdin>
codex
STATUS: REWORK

- **High:** `reclaimByOriginalOwner()` removes any occupation, including `TRANSFER_PENDING`. A counter-besiege helper meant for primary/tax-only occupations can currently cancel a secondary transfer-pending occupation without validating mode, original owner, colony existence, or caller authority.

- **Medium:** Primary/secondary mode selection depends directly on `FirstColonyTracker.isFirstColony(...)`. If tracker data is stale, a primary may get `TRANSFER_PENDING` messaging/duration until expiry fallback, or a secondary may become `TAX_ONLY` and never transfer. Use the same canonical transfer guard used by `WarSystem.transferOwnership`, or add a reconciliation path.

- **Medium:** `startOccupation()` still passes `originalOwner` into `OccupationData`, whose constructor calls `originalOwnerUUID.toString()`. If MineColonies returns null owner during edge states, this crashes before occupation save/notification.

- **Low:** `reclamationAttempted=true` has no special handling for `TAX_ONLY`. That may be intended, but stale legacy data or prior failed reclaim state will not affect auto-reclaim. Consider explicitly documenting or clearing it for TAX_ONLY to avoid misleading save state.

- **Low:** Expiry/removal is not atomic. If `checkExpiredOccupations()` and `reclaimByOriginalOwner()` can run close together, both can notify/save based on the same entry. Server-thread-only use may make this acceptable, but the async save change raises enough concurrency surface to justify `remove(colonyId, data)`.

Fix suggestions:

- `OccupationManager.java:501`: guard `reclaimByOriginalOwner` with `if (data.getMode() != OccupationMode.TAX_ONLY) return false;` and preferably validate the current colony owner matches `data.getOriginalOwnerUUID()`.

- `OccupationManager.java:163`: replace direct `FirstColonyTracker` classification with `!ColonyTierGuard.canTransferOwnership(colony)` or a shared helper so mode/duration match the transfer denial source.

- `OccupationManager.java:166/84`: if `originalOwner == null`, log and return before constructing `OccupationData`, or make the constructor reject null with a controlled failure path.
tokens used
22.855
STATUS: REWORK

- **High:** `reclaimByOriginalOwner()` removes any occupation, including `TRANSFER_PENDING`. A counter-besiege helper meant for primary/tax-only occupations can currently cancel a secondary transfer-pending occupation without validating mode, original owner, colony existence, or caller authority.

- **Medium:** Primary/secondary mode selection depends directly on `FirstColonyTracker.isFirstColony(...)`. If tracker data is stale, a primary may get `TRANSFER_PENDING` messaging/duration until expiry fallback, or a secondary may become `TAX_ONLY` and never transfer. Use the same canonical transfer guard used by `WarSystem.transferOwnership`, or add a reconciliation path.

- **Medium:** `startOccupation()` still passes `originalOwner` into `OccupationData`, whose constructor calls `originalOwnerUUID.toString()`. If MineColonies returns null owner during edge states, this crashes before occupation save/notification.

- **Low:** `reclamationAttempted=true` has no special handling for `TAX_ONLY`. That may be intended, but stale legacy data or prior failed reclaim state will not affect auto-reclaim. Consider explicitly documenting or clearing it for TAX_ONLY to avoid misleading save state.

- **Low:** Expiry/removal is not atomic. If `checkExpiredOccupations()` and `reclaimByOriginalOwner()` can run close together, both can notify/save based on the same entry. Server-thread-only use may make this acceptable, but the async save change raises enough concurrency surface to justify `remove(colonyId, data)`.

Fix suggestions:

- `OccupationManager.java:501`: guard `reclaimByOriginalOwner` with `if (data.getMode() != OccupationMode.TAX_ONLY) return false;` and preferably validate the current colony owner matches `data.getOriginalOwnerUUID()`.

- `OccupationManager.java:163`: replace direct `FirstColonyTracker` classification with `!ColonyTierGuard.canTransferOwnership(colony)` or a shared helper so mode/duration match the transfer denial source.

- `OccupationManager.java:166/84`: if `originalOwner == null`, log and return before constructing `OccupationData`, or make the constructor reject null with a controlled failure path.
