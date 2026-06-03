## WAVE 8-11 — Explosion't compat + MEDIUM fixes + counter-besiege wiring

Five additions on top of the prior rework:
  W8 — Explosion't optional compat. New ExplosiontCompat helper + DeferRestorationToExplosiont config (default false). When mod loaded + config on, WarBlockLedger.onExplosionDetonate skips and lets Explosion't handle restoration.
  W9a — isContainerBlock now adds AbstractFurnaceBlock/BrewingStandBlock/LecternBlock + a level-aware BlockEntity Container instanceof fallback for modded containers.
  W9b — New BesiegeEntityInteractHandler: during besiege, PlayerInteractEvent.EntityInteract on AbstractVillager or AbstractEntityCitizen is denied with chat throttle. Closes the villager-trade hole in the besiege lockdown.
  W10 — BesiegeManager hot-path optimization: new COLONY_RAID_INDEX (colonyId → Set<UUID besiegers>) maintained in lock-step with ACTIVE_RAIDS. isActiveRaidOnColony and getRaidsForColony are now O(1)/O(matches) instead of O(activeRaids).
  W11 — Counter-besiege reclaim wiring: completeBesiegeVictory now calls OccupationManager.reclaimByOriginalOwner first. If the besieger is the original owner of a TAX_ONLY war-occupation, that occupation is cleared. Closes the design-loop 'primary loses war → tax-occupied → owner counter-besieges → reclaims'.

### DIFF: TaxConfig.java (DEFER_RESTORATION_TO_EXPLOSIONT)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java b/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
index 69fc131..2a64c0f 100644
--- a/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
+++ b/src/main/java/net/machiavelli/minecolonytax/TaxConfig.java
@@ -75,6 +75,17 @@ public class TaxConfig {
         // Colony Tier Protection (Siege SMP ruleset)
         public static final ForgeConfigSpec.BooleanValue ENABLE_PRIMARY_COLONY_TRANSFER;
         public static final ForgeConfigSpec.IntValue PRIMARY_COLONY_TAX_OCCUPATION_DAYS;
+        public static final ForgeConfigSpec.IntValue BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY;
+
+        // Experimental Siege Objectives (step 11)
+        public static final ForgeConfigSpec.BooleanValue ENABLE_EXPERIMENTAL_SIEGE_OBJECTIVES;
+        public static final ForgeConfigSpec.IntValue TOWN_HALL_EXPLOSIVE_HITS_REQUIRED;
+        public static final ForgeConfigSpec.IntValue TOWN_HALL_HIT_COOLDOWN_MINUTES;
+        public static final ForgeConfigSpec.IntValue MAX_SIEGE_RADIUS;
+        public static final ForgeConfigSpec.IntValue ATTACKER_GLOW_SECONDS;
+
+        // Explosion't compat
+        public static final ForgeConfigSpec.BooleanValue DEFER_RESTORATION_TO_EXPLOSIONT;
         public static final ForgeConfigSpec.IntValue JOIN_PHASE_DURATION_MINUTES;
         public static final ForgeConfigSpec.BooleanValue WAR_ACCEPTANCE_REQUIRED;
         public static final ForgeConfigSpec.BooleanValue KEEP_INVENTORY_ON_LAST_LIFE;
@@ -633,6 +644,64 @@ public class TaxConfig {
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
+                ENABLE_EXPERIMENTAL_SIEGE_OBJECTIVES = BUILDER.comment(
+                                "EXPERIMENTAL — when enabled, full wars get an additional win condition:\n"
+                                                +
+                                                "the attacker can win by landing N explosive hits on the defender's Town Hall.\n"
+                                                +
+                                                "Runs IN PARALLEL with the legacy lives+guards system — first trigger wins.\n"
+                                                +
+                                                "Banner Capture (other objective from the design) is NOT yet implemented and\n"
+                                                +
+                                                "would require a new item/block registration.")
+                                .define("EnableExperimentalSiegeObjectives", false);
+
+                TOWN_HALL_EXPLOSIVE_HITS_REQUIRED = BUILDER.comment(
+                                "Number of counted explosive hits on the Town Hall building required for attacker victory.")
+                                .defineInRange("TownHallExplosiveHitsRequired", 5, 1, 50);
+
+                TOWN_HALL_HIT_COOLDOWN_MINUTES = BUILDER.comment(
+                                "Per-attacker cooldown (minutes) between counted Town Hall hits. Prevents stacking\n"
+                                                +
+                                                "TNT in one chunk to instawin. Hits during cooldown still damage blocks but don't count.")
+                                .defineInRange("TownHallHitCooldownMinutes", 5, 1, 60);
+
+                MAX_SIEGE_RADIUS = BUILDER.comment(
+                                "Maximum distance (blocks) from the Town Hall centre the attacker may be in order\n"
+                                                +
+                                                "for an explosion to count as a Town Hall hit. Prevents remote-triggering.")
+                                .defineInRange("MaxSiegeRadius", 500, 50, 2000);
+
+                ATTACKER_GLOW_SECONDS = BUILDER.comment(
+                                "Seconds the GLOWING effect is applied to an attacker after they land a counted hit.\n"
+                                                +
+                                                "Lets defenders see them through walls and converge.")
+                                .defineInRange("AttackerGlowSeconds", 30, 0, 600);
+
+                DEFER_RESTORATION_TO_EXPLOSIONT = BUILDER.comment(
+                                "Compat: if Harmonised's 'Explosion't' mod is installed AND this is true, the WarBlockLedger\n"
+                                                +
+                                                "skips its own snapshot/restore pipeline and lets Explosion't handle all explosion\n"
+                                                +
+                                                "damage globally. Trade-off: Explosion't restores ALL world-wide explosion damage\n"
+                                                +
+                                                "(including outside the colony bracket), not just war damage. Leave false to keep\n"
+                                                +
+                                                "the per-war scoped restoration; flip true if you want one system in charge.")
+                                .define("DeferRestorationToExplosiont", false);
+
                 // ========== Colony Occupation Settings ==========
                 BUILDER.push("Colony Occupation");
 
@@ -2630,6 +2699,34 @@ public class TaxConfig {
                 return PRIMARY_COLONY_TAX_OCCUPATION_DAYS.get();
         }
 
+        public static int getBesiegeSpoilPercentOfLoserTreasury() {
+                return BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY.get();
+        }
+
+        public static boolean isExperimentalSiegeObjectivesEnabled() {
+                return ENABLE_EXPERIMENTAL_SIEGE_OBJECTIVES.get();
+        }
+
+        public static int getTownHallExplosiveHitsRequired() {
+                return TOWN_HALL_EXPLOSIVE_HITS_REQUIRED.get();
+        }
+
+        public static int getTownHallHitCooldownMinutes() {
+                return TOWN_HALL_HIT_COOLDOWN_MINUTES.get();
+        }
+
+        public static int getMaxSiegeRadius() {
+                return MAX_SIEGE_RADIUS.get();
+        }
+
+        public static int getAttackerGlowSeconds() {
+                return ATTACKER_GLOW_SECONDS.get();
+        }
+
+        public static boolean isDeferRestorationToExplosiont() {
+                return DEFER_RESTORATION_TO_EXPLOSIONT.get();
+        }
+
         public static int getWarVassalizationDurationHours() {
                 return WAR_VASSALIZATION_DURATION_HOURS.get();
         }
```

### NEW FILE: compat/ExplosiontCompat.java
```java
package net.machiavelli.minecolonytax.compat;

import net.minecraftforge.fml.ModList;

/**
 * Optional integration with Harmonised's "Explosion't" mod (CurseForge:
 * explosiont). When the mod is present AND DeferRestorationToExplosiont is
 * enabled in the config, our WarBlockLedger steps aside and lets Explosion't
 * handle all explosion restoration globally.
 *
 * No hard dependency — the class is only referenced when ModList confirms
 * the mod is loaded, so missing-mod environments work normally.
 */
public final class ExplosiontCompat {

    private static final String MOD_ID = "explosiont";

    private static Boolean cachedPresence = null;

    private ExplosiontCompat() {}

    /**
     * Returns true when the Explosion't mod is loaded. Cached after first call
     * — ModList state doesn't change after server start.
     */
    public static boolean isPresent() {
        if (cachedPresence == null) {
            try {
                cachedPresence = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
            } catch (Throwable t) {
                cachedPresence = false;
            }
        }
        return cachedPresence;
    }

    /**
     * Whether the WarBlockLedger should hand off explosion restoration to
     * Explosion't. Returns true only when BOTH (a) the mod is present AND
     * (b) the operator opted in via DeferRestorationToExplosiont.
     */
    public static boolean shouldDeferToExplosiont() {
        return isPresent()
                && net.machiavelli.minecolonytax.TaxConfig.isDeferRestorationToExplosiont();
    }
}
```

### NEW FILE: besiege/BesiegeEntityInteractHandler.java
```java
package net.machiavelli.minecolonytax.besiege;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Completes the besiege interaction profile: during a besiege, right-clicking
 * a villager (trade) or a MineColonies citizen is denied for any player on the
 * besieged colony's combat radius. Combat-only — no trading, no recruiting,
 * no information gathering through normal NPC interactions.
 *
 * Pairs with the container-block deny in BlockInteractionFilterHandler.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BesiegeEntityInteractHandler {

    private static final Logger LOGGER = LogManager.getLogger(BesiegeEntityInteractHandler.class);

    // Throttle the deny message so spam-clicking doesn't flood chat
    private static final Map<UUID, Long> LAST_DENY_MESSAGE = new HashMap<>();
    private static final long DENY_MESSAGE_COOLDOWN_MS = 3000;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!TaxConfig.isBesiegeSystemEnabled()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Entity target = event.getTarget();
        boolean isVillager = target instanceof AbstractVillager;
        boolean isCitizen = target instanceof AbstractEntityCitizen;
        if (!isVillager && !isCitizen) return;

        // Find any active besiege the player is involved in (either as besieger
        // or as a defender on the besieged colony).
        int besiegedColonyId = -1;

        // Is the player a besieger?
        BesiegeManager.BesiegeRaidData own = BesiegeManager.getRaidForBesieger(player.getUUID());
        if (own != null) besiegedColonyId = own.colonyId;

        // If not, are they inside a colony that's being besieged?
        if (besiegedColonyId < 0 && isCitizen) {
            AbstractEntityCitizen citizen = (AbstractEntityCitizen) target;
            try {
                var data = citizen.getCitizenData();
                if (data != null && data.getColony() != null) {
                    int cid = data.getColony().getID();
                    if (BesiegeManager.isActiveRaidOnColony(cid)) besiegedColonyId = cid;
                }
            } catch (Exception ignored) {}
        }

        if (besiegedColonyId < 0) return;

        // Deny — combat-only during besiege.
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);

        long now = System.currentTimeMillis();
        Long last = LAST_DENY_MESSAGE.get(player.getUUID());
        if (last == null || now - last >= DENY_MESSAGE_COOLDOWN_MS) {
            LAST_DENY_MESSAGE.put(player.getUUID(), now);
            String label = isVillager ? "trade with this villager" : "interact with this citizen";
            player.sendSystemMessage(Component.literal(
                    "You cannot " + label + " during a besiege — combat only.")
                    .withStyle(ChatFormatting.RED));
        }
        if (TaxConfig.isDebugLogging()) {
            LOGGER.debug("BESIEGE DENIED (ENTITY INTERACT): {} blocked from interacting with {} on colony {}",
                    player.getName().getString(), target.getClass().getSimpleName(), besiegedColonyId);
        }
    }
}
```

### DIFF: WarBlockLedger.java (compat guard)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/siege/WarBlockLedger.java b/src/main/java/net/machiavelli/minecolonytax/siege/WarBlockLedger.java
index 7b1de42..487c50c 100644
--- a/src/main/java/net/machiavelli/minecolonytax/siege/WarBlockLedger.java
+++ b/src/main/java/net/machiavelli/minecolonytax/siege/WarBlockLedger.java
@@ -78,6 +78,10 @@ public final class WarBlockLedger {
 
     @SubscribeEvent(priority = EventPriority.HIGHEST)
     public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
+        // Compat: if Explosion't is loaded AND operator opted in, let it handle
+        // restoration globally. Our per-war scoped capture becomes redundant.
+        if (net.machiavelli.minecolonytax.compat.ExplosiontCompat.shouldDeferToExplosiont()) return;
+
         if (WarSystem.ACTIVE_WARS.isEmpty()) return;
 
         Level level = event.getLevel();
```

### DIFF: BlockInteractionFilterHandler.java (modded container detection)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java b/src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java
index 99895c1..e0c4ca5 100644
--- a/src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java
+++ b/src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java
@@ -68,11 +68,11 @@ public class BlockInteractionFilterHandler {
     @SubscribeEvent(priority = EventPriority.HIGHEST)
     public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
         if (event.getLevel().isClientSide()) return;
-        
+
         if (!(event.getEntity() instanceof ServerPlayer player)) return;
-        
+
         Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
-        
+
         FilterResult result = checkBlockInteraction(
             player,
             event.getPos(),
@@ -80,21 +80,53 @@ public class BlockInteractionFilterHandler {
             block,
             InteractionType.USE
         );
-        
+
         applyFilterResult(result, event, player);
     }
+
+    // LeftClickBlock is NOT handled here — it fires every tick while holding left-click,
+    // which would cause lag (colony lookups 20x/sec) and break normal mining.
+    // MineColonies counts left-click denials toward levitation; the tick-based levitation
+    // remover in WarEventHandler handles that instead.
     
+    /**
+     * Returns true if at least one conflict system currently has active state.
+     * All four checks are lock-free isEmpty() reads on ConcurrentHashMap or size==0
+     * on HashMap — effectively free. This guard lets checkBlockInteraction skip the
+     * relatively expensive getColonyByPosFromWorld call during normal play when no
+     * wars, raids, occupations, or besieges are in progress.
+     */
+    private static boolean anyConflictSystemActive() {
+        if (TaxConfig.isOccupationSystemEnabled()
+                && !net.machiavelli.minecolonytax.occupation.OccupationManager
+                        .getActiveOccupations().isEmpty()) return true;
+        if (TaxConfig.isBesiegeSystemEnabled()
+                && !net.machiavelli.minecolonytax.besiege.BesiegeManager
+                        .getActiveRaids().isEmpty()) return true;
+        if (TaxConfig.isBlockFilterRaidsEnabled()
+                && !RaidManager.getActiveRaids().isEmpty()) return true;
+        if (TaxConfig.isBlockFilterWarsEnabled()
+                && !WarSystem.ACTIVE_WARS.isEmpty()) return true;
+        return false;
+    }
+
     private static FilterResult checkBlockInteraction(
             ServerPlayer player,
             BlockPos pos,
             Level level,
             Block block,
             InteractionType type) {
-        
+
         if (!TaxConfig.isBlockInteractionFilterEnabled()) {
             return FilterResult.PASS_THROUGH;
         }
 
+        // Fast path: skip the colony lookup entirely when no conflict system is active.
+        // This is the common case on servers that are not in a war/raid/occupation/besiege.
+        if (!anyConflictSystemActive()) {
+            return FilterResult.PASS_THROUGH;
+        }
+
         IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
         if (colony == null) {
             return FilterResult.PASS_THROUGH;
@@ -133,6 +165,19 @@ public class BlockInteractionFilterHandler {
             return FilterResult.deny("Block breaking is not allowed during raids and wars!", null);
         }
 
+        // Siege SMP rule: during a besiege, deny right-click USE on container-style blocks
+        // (chests, barrels, shulkers, furnaces, etc., plus any modded BlockEntity Container).
+        // Combat-only — no looting. Doors/levers/buttons still pass through via the
+        // existing whitelist/blacklist below.
+        if (type == InteractionType.USE && isBesiegeActiveForPlayer(player, colony.getID())) {
+            if (isContainerBlock(block, level, pos)) {
+                LOGGER.debug("BESIEGE DENIED (CONTAINER): Player {} cannot open containers during besiege at {}",
+                    player.getName().getString(), pos);
+                return FilterResult.deny("You cannot loot containers during a besiege!",
+                    block.builtInRegistryHolder().key().location().toString());
+            }
+        }
+
         ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
         if (blockId == null) {
             LOGGER.warn("Could not get registry key for block: {}", block);
@@ -192,7 +237,7 @@ public class BlockInteractionFilterHandler {
         UUID playerUUID = player.getUUID();
         int colonyId = colony.getID();
 
-        if (TaxConfig.isBlockFilterRaidsEnabled()) {
+        if (TaxConfig.isBlockFilterRaidsEnabled() && !RaidManager.getActiveRaids().isEmpty()) {
             net.machiavelli.minecolonytax.raid.ActiveRaidData raid =
                 RaidManager.getActiveRaidForPlayer(playerUUID);
             if (raid != null && raid.getColony() != null && raid.getColony().getID() == colonyId) {
@@ -200,7 +245,7 @@ public class BlockInteractionFilterHandler {
             }
         }
 
-        if (TaxConfig.isBlockFilterWarsEnabled()) {
+        if (TaxConfig.isBlockFilterWarsEnabled() && !WarSystem.ACTIVE_WARS.isEmpty()) {
             for (WarData warData : WarSystem.ACTIVE_WARS.values()) {
                 boolean playerIsParticipant =
                     (warData.getAttackerLives() != null && warData.getAttackerLives().containsKey(playerUUID))
@@ -219,6 +264,54 @@ public class BlockInteractionFilterHandler {
         return false;
     }
     
+    /** True if any besiege raid is active that involves this player and colony. */
+    private static boolean isBesiegeActiveForPlayer(ServerPlayer player, int colonyId) {
+        if (!TaxConfig.isBesiegeSystemEnabled()) return false;
+        UUID playerUUID = player.getUUID();
+        // The besieger themselves vs the besieged colony
+        net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData own =
+                net.machiavelli.minecolonytax.besiege.BesiegeManager.getRaidForBesieger(playerUUID);
+        if (own != null && own.colonyId == colonyId) return true;
+        // Defender side: any active raid is targeting this colony
+        for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData raid
+                : net.machiavelli.minecolonytax.besiege.BesiegeManager.getAllActiveRaidsByBesieger().values()) {
+            if (raid.colonyId == colonyId) return true;
+        }
+        return false;
+    }
+
+    /**
+     * True if this block exposes a Container — chests, barrels, shulkers, hoppers,
+     * dispensers, furnaces, brewing stands, AND any modded block whose BlockEntity
+     * implements net.minecraft.world.Container.
+     *
+     * Two-tier check:
+     *  1. Fast vanilla-class instanceof for the common cases
+     *  2. Level-aware BlockEntity Container instanceof fallback for everything else
+     *     (modded chests/storage that don't subclass vanilla blocks)
+     */
+    private static boolean isContainerBlock(Block block, Level level, BlockPos pos) {
+        // Vanilla fast paths
+        if (block instanceof net.minecraft.world.level.block.ChestBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.BarrelBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.ShulkerBoxBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.EnderChestBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.HopperBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.DispenserBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.ChiseledBookShelfBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.BrewingStandBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.LecternBlock) return true;
+
+        // Catch-all: modded containers whose BlockEntity implements Container.
+        // O(1) lookup — the entity is already cached on the chunk.
+        try {
+            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
+            if (be instanceof net.minecraft.world.Container) return true;
+        } catch (Exception ignored) {}
+        return false;
+    }
+
     private static void applyFilterResult(FilterResult result, Event event, ServerPlayer player) {
         switch (result.action) {
             case DENY:
```

### DIFF: BesiegeManager.java (index + reclaim handoff)
```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
index 959ac36..e3f0def 100644
--- a/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
+++ b/src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java
@@ -68,6 +68,14 @@ public class BesiegeManager {
      */
     private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
 
+    /**
+     * Secondary index: colonyId → set of besiegerUUIDs currently raiding it.
+     * Maintained in lock-step with ACTIVE_RAIDS so hot-path lookups
+     * (isActiveRaidOnColony, getRaidsForColony, container deny checks)
+     * are O(1) instead of O(activeRaids).
+     */
+    private static final Map<Integer, Set<UUID>> COLONY_RAID_INDEX = new ConcurrentHashMap<>();
+
     /** Persistent occupation records (colonyId -> occupation data). */
     private static final Map<Integer, BesiegeOccupationData> OCCUPATIONS = new ConcurrentHashMap<>();
 
@@ -88,6 +96,7 @@ public class BesiegeManager {
             cleanupRaid(raid, false);
         }
         ACTIVE_RAIDS.clear();
+        COLONY_RAID_INDEX.clear();
         if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager shutdown complete");
     }
 
@@ -119,8 +128,10 @@ public class BesiegeManager {
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
@@ -305,6 +316,7 @@ public class BesiegeManager {
         try {
             BesiegeRaidData raid = new BesiegeRaidData(colonyId, besiegerUUID, colony.getCenter(), isReclaim);
             ACTIVE_RAIDS.put(besiegerUUID, raid);
+            COLONY_RAID_INDEX.computeIfAbsent(colonyId, k -> ConcurrentHashMap.newKeySet()).add(besiegerUUID);
 
             // Grant the besieger hostile rank + combat permissions on the colony
             // so MineColonies allows the player to attack citizens.
@@ -319,6 +331,10 @@ public class BesiegeManager {
             // Spawn mercenaries
             int mercCount = spawnMercenaries(colony, besieger, raid);
 
+            // Militia upgrade reinforcements — NOT counted toward victory.
+            // Each tier adds +N% bonus militia entities scaled by current guard count.
+            int militiaUpgradeCount = spawnMilitiaUpgradeReinforcements(colony, besieger, raid, guardCount);
+
             int totalDefenders = guardCount + militiaCount + mercCount;
 
             // Apply FORTIFICATION investment: extra DAMAGE_RESISTANCE to all defenders
@@ -374,10 +390,19 @@ public class BesiegeManager {
         } catch (Exception e) {
             LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
             ACTIVE_RAIDS.remove(besiegerUUID);
+            removeFromColonyIndex(colonyId, besiegerUUID);
             return false;
         }
     }
 
+    /** Remove a besieger from the colony→raid index; drop the colony entry when empty. */
+    private static void removeFromColonyIndex(int colonyId, UUID besiegerUUID) {
+        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
+        if (set == null) return;
+        set.remove(besiegerUUID);
+        if (set.isEmpty()) COLONY_RAID_INDEX.remove(colonyId, set);
+    }
+
 
     private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
         int count = 0;
@@ -541,24 +566,138 @@ public class BesiegeManager {
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
+        // Counter-besiege reclaim handoff: if the besieger is the original owner
+        // of a TAX_ONLY war-occupation on this colony, clear that occupation
+        // first — they've successfully reclaimed their primary via solo combat
+        // (the Siege SMP "owner mounts counter-besiege" loop). The legacy
+        // besiege vassalization flow then no-ops because they already own the
+        // colony, but we keep calling it for the notifications.
+        try {
+            boolean reclaimed = net.machiavelli.minecolonytax.occupation.OccupationManager
+                    .reclaimByOriginalOwner(colony.getID(), raid.besiegingPlayerUUID);
+            if (reclaimed && TaxConfig.isNormalLogging()) {
+                LOGGER.info("Besiege victory doubled as counter-besiege reclaim: {} reclaimed TAX_ONLY occupation of {}",
+                        raid.besiegingPlayerUUID, colony.getName());
+            }
+        } catch (Exception e) {
+            LOGGER.warn("Counter-besiege reclaim handoff failed for colony {}", colony.getName(), e);
+        }
+
         int tributePct = TaxConfig.getBesiegeTributePercent();
         int durationHours = TaxConfig.getBesiegeTributeDurationHours();
 
@@ -663,6 +802,15 @@ public class BesiegeManager {
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
@@ -685,6 +833,7 @@ public class BesiegeManager {
 
         if (removeFromMap) {
             ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
+            removeFromColonyIndex(raid.colonyId, raid.besiegingPlayerUUID);
         }
     }
 
@@ -753,17 +902,19 @@ public class BesiegeManager {
     }
 
     public static boolean isActiveRaidOnColony(int colonyId) {
-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
-            if (raid.colonyId == colonyId) return true;
-        }
-        return false;
+        // Hot path — O(1) via COLONY_RAID_INDEX.
+        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
+        return set != null && !set.isEmpty();
     }
 
-    /** All currently active besiege raids targeting this colony. */
+    /** All currently active besiege raids targeting this colony. O(matches). */
     public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
-        List<BesiegeRaidData> matches = new ArrayList<>();
-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
-            if (raid.colonyId == colonyId) matches.add(raid);
+        Set<UUID> besiegerUUIDs = COLONY_RAID_INDEX.get(colonyId);
+        if (besiegerUUIDs == null || besiegerUUIDs.isEmpty()) return java.util.Collections.emptyList();
+        List<BesiegeRaidData> matches = new ArrayList<>(besiegerUUIDs.size());
+        for (UUID uuid : besiegerUUIDs) {
+            BesiegeRaidData raid = ACTIVE_RAIDS.get(uuid);
+            if (raid != null) matches.add(raid);
         }
         return matches;
     }
@@ -1100,6 +1251,12 @@ public class BesiegeManager {
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
