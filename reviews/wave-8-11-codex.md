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
session id: 019e5d11-9ff3-7f40-8dbb-90323b78ae47
--------
user
Reviewing waves 8-11 of a Java Forge mod refactor (MC 1.20.1, MineColonies addon). Five additions: (W8) Explosion't opt-in compat shim with cached ModList check; (W9a) extended container-block detection with Container BlockEntity fallback for modded chests; (W9b) new EntityInteract handler denying villager/citizen interactions during besiege; (W10) BesiegeManager colony→raid secondary index for O(1) hot-path lookups; (W11) counter-besiege victory now triggers OccupationManager.reclaimByOriginalOwner before vassalization. Review for: (1) is the index maintained in lock-step with ACTIVE_RAIDS on ALL add/remove paths including error/timeout paths, (2) is the EntityInteract handler correctly cancelling — does setCanceled(true) + setCancellationResult work for Forge 1.20.1?, (3) does the Container fallback correctly handle null BlockEntity/dirty chunk reads, (4) is the Explosion't compat actually safe (what happens if mod is unloaded mid-session?), (5) does the counter-besiege wiring handle the case where the besieger ISN'T the original owner (no-op?). STATUS APPROVE/REWORK, up to 6 bullets, up to 3 fix suggestions. Max 350 words.

<stdin>
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
</stdin>
codex
I’ll review this as a code-review pass against the diff and the local tree where useful. I’ll also verify the Forge cancellation behavior from primary Forge/Minecraft source or docs rather than assuming it.
mcp: codex/list_mcp_resources started
mcp: codex/list_mcp_resources (completed)
codex
GitNexus MCP resources/tools aren’t available in this session, so I can’t run the project’s graph queries. I’m falling back to local source inspection and will call that limitation out if it affects confidence.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "class PlayerInteractEvent|setCancellationResult|EntityInteract" "$env:USERPROFILE'"\\.gradle\\caches\" -g \"*.java\" -g \"*.class\"" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "setCancellationResult|isCancelable|EntityInteract|RightClickBlock|applyFilterResult" src/main/java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n \"ACTIVE_RAIDS|COLONY_RAID_INDEX|cleanupRaid|completeBesiege\\(|isActiveRaidOnColony|getRaidsForColony|removeFromColonyIndex\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "class PlayerInteractEvent|setCancellationResult|EntityInteract" "$env:USERPROFILE'"\\.gradle\\caches\" -g \"*.java\" -g \"*.class\""` rejected: blocked by policy
2026-05-25T02:58:49.231043Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n "class PlayerInteractEvent|setCancellationResult|EntityInteract" "$env:USERPROFILE'"\\.gradle\\caches\" -g \"*.java\" -g \"*.class\""` rejected: blocked by policy
 succeeded in 325ms:
62:     * raid the same colony â€” look them up by colony with {@link #getRaidsForColony(int)}.
69:    private static final Map<UUID, BesiegeRaidData> ACTIVE_RAIDS = new ConcurrentHashMap<>();
73:     * Maintained in lock-step with ACTIVE_RAIDS so hot-path lookups
74:     * (isActiveRaidOnColony, getRaidsForColony, container deny checks)
77:    private static final Map<Integer, Set<UUID>> COLONY_RAID_INDEX = new ConcurrentHashMap<>();
95:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
96:            cleanupRaid(raid, false);
98:        ACTIVE_RAIDS.clear();
99:        COLONY_RAID_INDEX.clear();
104:        if (ACTIVE_RAIDS.isEmpty()) return;
106:        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
114:                    cleanupRaid(raid, false);
134:                    completeBesiege(raid, false, colony);
148:                        cleanupRaid(raid, false);
162:                    completeBesiege(raid, true, colony);
214:        if (ACTIVE_RAIDS.containsKey(besiegerUUID)) {
288:        if (ACTIVE_RAIDS.containsKey(playerUUID)) {
318:            ACTIVE_RAIDS.put(besiegerUUID, raid);
319:            COLONY_RAID_INDEX.computeIfAbsent(colonyId, k -> ConcurrentHashMap.newKeySet()).add(besiegerUUID);
392:            ACTIVE_RAIDS.remove(besiegerUUID);
393:            removeFromColonyIndex(colonyId, besiegerUUID);
399:    private static void removeFromColonyIndex(int colonyId, UUID besiegerUUID) {
400:        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
403:        if (set.isEmpty()) COLONY_RAID_INDEX.remove(colonyId, set);
611:    private static void completeBesiege(BesiegeRaidData raid, boolean attackerWon, IColony colony) {
612:        cleanupRaid(raid, true);
770:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
835:            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
836:            removeFromColonyIndex(raid.colonyId, raid.besiegingPlayerUUID);
904:    public static boolean isActiveRaidOnColony(int colonyId) {
905:        // Hot path â€” O(1) via COLONY_RAID_INDEX.
906:        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
911:    public static List<BesiegeRaidData> getRaidsForColony(int colonyId) {
912:        Set<UUID> besiegerUUIDs = COLONY_RAID_INDEX.get(colonyId);
916:            BesiegeRaidData raid = ACTIVE_RAIDS.get(uuid);
950:        List<BesiegeRaidData> raids = getRaidsForColony(colonyId);
976:     * {@link #getRaidsForColony(int)}; callers that need a specific besieger's
981:        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
989:        return ACTIVE_RAIDS.get(besiegerUUID);
994:        return Collections.unmodifiableMap(ACTIVE_RAIDS);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 340ms:
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeEntityInteractHandler.java:31:public class BesiegeEntityInteractHandler {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeEntityInteractHandler.java:33:    private static final Logger LOGGER = LogManager.getLogger(BesiegeEntityInteractHandler.class);
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeEntityInteractHandler.java:40:    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
src/main/java\net\machiavelli\minecolonytax\besiege\BesiegeEntityInteractHandler.java:74:        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
src/main/java\net\machiavelli\minecolonytax\event\AbandonedColonyProtectionHandler.java:58:    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
src/main/java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:48:        applyFilterResult(result, event, player);
src/main/java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:65:        applyFilterResult(result, event, player);
src/main/java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:69:    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
src/main/java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:84:        applyFilterResult(result, event, player);
src/main/java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:315:    private static void applyFilterResult(FilterResult result, Event event, ServerPlayer player) {
src/main/java\net\machiavelli\minecolonytax\event\BlockInteractionFilterHandler.java:319:                if (event.isCancelable()) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
The first pass shows the new index is updated on normal start, start failure, shutdown, and cleanup, but there are iterator-removal paths in `tick()` that need closer reading because those can bypass `removeFromColonyIndex`.
2026-05-25T02:59:01.908661Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java'; "'$lines=Get-Content -Path $p; for($i=260;$i -le 338;$i++){ '"'{0}:{1}' -f "'$i,$lines[$i-1] }'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java'; "'$lines=Get-Content -Path $p; for($i=760;$i -le 842;$i++){ '"'{0}:{1}' -f "'$i,$lines[$i-1] }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java'; "'$lines=Get-Content -Path $p; for($i=260;$i -le 338;$i++){ '"'{0}:{1}' -f "'$i,$lines[$i-1] }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java'; "'$lines=Get-Content -Path $p; for($i=260;$i -le 338;$i++){ '"'{0}:{1}' -f "'$i,$lines[$i-1] }'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java'; "'$lines=Get-Content -Path $p; for($i=35;$i -le 82;$i++){ '"'{0}:{1}' -f "'$i,$lines[$i-1] }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java'; "'$lines=Get-Content -Path $p; for($i=760;$i -le 842;$i++){ '"'{0}:{1}' -f "'$i,$lines[$i-1] }'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java'; "'$lines=Get-Content -Path $p; for($i=100;$i -le 170;$i++){ '"'{0}:{1}' -f "'$i,$lines[$i-1] }' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
2026-05-25T02:59:01.910244Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java'; "'$lines=Get-Content -Path $p; for($i=760;$i -le 842;$i++){ '"'{0}:{1}' -f "'$i,$lines[$i-1] }'` rejected: blocked by policy
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java'; "'$lines=Get-Content -Path $p; for($i=35;$i -le 82;$i++){ '"'{0}:{1}' -f "'$i,$lines[$i-1] }'` rejected: blocked by policy
 declined in 0ms:
`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java'; "'$lines=Get-Content -Path $p; for($i=100;$i -le 170;$i++){ '"'{0}:{1}' -f "'$i,$lines[$i-1] }'` rejected: blocked by policy
2026-05-25T02:59:01.913340Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java'; "'$lines=Get-Content -Path $p; for($i=35;$i -le 82;$i++){ '"'{0}:{1}' -f "'$i,$lines[$i-1] }'` rejected: blocked by policy
2026-05-25T02:59:01.914138Z ERROR codex_core::tools::router: error=`"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command '$p='"'src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java'; "'$lines=Get-Content -Path $p; for($i=100;$i -le 170;$i++){ '"'{0}:{1}' -f "'$i,$lines[$i-1] }'` rejected: blocked by policy
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 8 \"private static void cleanupRaid|removeFromMap|ACTIVE_RAIDS\\.remove|removeFromColonyIndex\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 8 "setCanceled|setCancellationResult|getTarget|getRaidForBesieger|AbstractVillager|AbstractEntityCitizen" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeEntityInteractHandler.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 8 \"cleanupRaid\\(raid, false\\)|completeBesiege\\(raid|it\\.remove\\(\\)\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 8 \"private static boolean isContainerBlock|level\\.getBlockEntity|applyFilterResult\" src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 303ms:
1-package net.machiavelli.minecolonytax.besiege;
2-
3:import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
4-import net.machiavelli.minecolonytax.MineColonyTax;
5-import net.machiavelli.minecolonytax.TaxConfig;
6-import net.minecraft.ChatFormatting;
7-import net.minecraft.network.chat.Component;
8-import net.minecraft.server.level.ServerPlayer;
9-import net.minecraft.world.entity.Entity;
10:import net.minecraft.world.entity.npc.AbstractVillager;
11-import net.minecraftforge.event.entity.player.PlayerInteractEvent;
12-import net.minecraftforge.eventbus.api.EventPriority;
13-import net.minecraftforge.eventbus.api.SubscribeEvent;
14-import net.minecraftforge.fml.common.Mod;
15-import org.apache.logging.log4j.LogManager;
16-import org.apache.logging.log4j.Logger;
17-
18-import java.util.HashMap;
--
37-    private static final long DENY_MESSAGE_COOLDOWN_MS = 3000;
38-
39-    @SubscribeEvent(priority = EventPriority.HIGH)
40-    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
41-        if (!TaxConfig.isBesiegeSystemEnabled()) return;
42-        if (event.getLevel().isClientSide()) return;
43-        if (!(event.getEntity() instanceof ServerPlayer player)) return;
44-
45:        Entity target = event.getTarget();
46:        boolean isVillager = target instanceof AbstractVillager;
47:        boolean isCitizen = target instanceof AbstractEntityCitizen;
48-        if (!isVillager && !isCitizen) return;
49-
50-        // Find any active besiege the player is involved in (either as besieger
51-        // or as a defender on the besieged colony).
52-        int besiegedColonyId = -1;
53-
54-        // Is the player a besieger?
55:        BesiegeManager.BesiegeRaidData own = BesiegeManager.getRaidForBesieger(player.getUUID());
56-        if (own != null) besiegedColonyId = own.colonyId;
57-
58-        // If not, are they inside a colony that's being besieged?
59-        if (besiegedColonyId < 0 && isCitizen) {
60:            AbstractEntityCitizen citizen = (AbstractEntityCitizen) target;
61-            try {
62-                var data = citizen.getCitizenData();
63-                if (data != null && data.getColony() != null) {
64-                    int cid = data.getColony().getID();
65-                    if (BesiegeManager.isActiveRaidOnColony(cid)) besiegedColonyId = cid;
66-                }
67-            } catch (Exception ignored) {}
68-        }
69-
70-        if (besiegedColonyId < 0) return;
71-
72-        // Deny â€” combat-only during besiege.
73:        event.setCanceled(true);
74:        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
75-
76-        long now = System.currentTimeMillis();
77-        Long last = LAST_DENY_MESSAGE.get(player.getUUID());
78-        if (last == null || now - last >= DENY_MESSAGE_COOLDOWN_MS) {
79-            LAST_DENY_MESSAGE.put(player.getUUID(), now);
80-            String label = isVillager ? "trade with this villager" : "interact with this citizen";
81-            player.sendSystemMessage(Component.literal(
82-                    "You cannot " + label + " during a besiege â€” combat only.")
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 284ms:
40-        FilterResult result = checkBlockInteraction(
41-            player,
42-            event.getPos(),
43-            (Level) event.getLevel(),
44-            event.getState().getBlock(),
45-            InteractionType.BREAK
46-        );
47-        
48:        applyFilterResult(result, event, player);
49-    }
50-    
51-    @SubscribeEvent(priority = EventPriority.HIGHEST)
52-    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
53-        if (event.getLevel().isClientSide()) return;
54-        
55-        if (!(event.getEntity() instanceof ServerPlayer player)) return;
56-        
57-        FilterResult result = checkBlockInteraction(
58-            player,
59-            event.getPos(),
60-            (Level) event.getLevel(),
61-            event.getPlacedBlock().getBlock(),
62-            InteractionType.PLACE
63-        );
64-        
65:        applyFilterResult(result, event, player);
66-    }
67-    
68-    @SubscribeEvent(priority = EventPriority.HIGHEST)
69-    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
70-        if (event.getLevel().isClientSide()) return;
71-
72-        if (!(event.getEntity() instanceof ServerPlayer player)) return;
73-
--
76-        FilterResult result = checkBlockInteraction(
77-            player,
78-            event.getPos(),
79-            (Level) event.getLevel(),
80-            block,
81-            InteractionType.USE
82-        );
83-
84:        applyFilterResult(result, event, player);
85-    }
86-
87-    // LeftClickBlock is NOT handled here â€” it fires every tick while holding left-click,
88-    // which would cause lag (colony lookups 20x/sec) and break normal mining.
89-    // MineColonies counts left-click denials toward levitation; the tick-based levitation
90-    // remover in WarEventHandler handles that instead.
91-    
92-    /**
--
285-     * dispensers, furnaces, brewing stands, AND any modded block whose BlockEntity
286-     * implements net.minecraft.world.Container.
287-     *
288-     * Two-tier check:
289-     *  1. Fast vanilla-class instanceof for the common cases
290-     *  2. Level-aware BlockEntity Container instanceof fallback for everything else
291-     *     (modded chests/storage that don't subclass vanilla blocks)
292-     */
293:    private static boolean isContainerBlock(Block block, Level level, BlockPos pos) {
294-        // Vanilla fast paths
295-        if (block instanceof net.minecraft.world.level.block.ChestBlock) return true;
296-        if (block instanceof net.minecraft.world.level.block.BarrelBlock) return true;
297-        if (block instanceof net.minecraft.world.level.block.ShulkerBoxBlock) return true;
298-        if (block instanceof net.minecraft.world.level.block.EnderChestBlock) return true;
299-        if (block instanceof net.minecraft.world.level.block.HopperBlock) return true;
300-        if (block instanceof net.minecraft.world.level.block.DispenserBlock) return true;
301-        if (block instanceof net.minecraft.world.level.block.ChiseledBookShelfBlock) return true;
302-        if (block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock) return true;
303-        if (block instanceof net.minecraft.world.level.block.BrewingStandBlock) return true;
304-        if (block instanceof net.minecraft.world.level.block.LecternBlock) return true;
305-
306-        // Catch-all: modded containers whose BlockEntity implements Container.
307-        // O(1) lookup â€” the entity is already cached on the chunk.
308-        try {
309:            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
310-            if (be instanceof net.minecraft.world.Container) return true;
311-        } catch (Exception ignored) {}
312-        return false;
313-    }
314-
315:    private static void applyFilterResult(FilterResult result, Event event, ServerPlayer player) {
316-        switch (result.action) {
317-            case DENY:
318-                event.setResult(Event.Result.DENY);
319-                if (event.isCancelable()) {
320-                    event.setCanceled(true);
321-                }
322-                player.sendSystemMessage(
323-                    Component.literal(result.message)
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 290ms:
88-        SERVER = server;
89-        loadData(server);
90-        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager initialized");
91-    }
92-
93-    public static void shutdown() {
94-        saveData();
95-        for (BesiegeRaidData raid : ACTIVE_RAIDS.values()) {
96:            cleanupRaid(raid, false);
97-        }
98-        ACTIVE_RAIDS.clear();
99-        COLONY_RAID_INDEX.clear();
100-        if (TaxConfig.isNormalLogging()) LOGGER.info("BesiegeManager shutdown complete");
101-    }
102-
103-    public static void tick() {
104-        if (ACTIVE_RAIDS.isEmpty()) return;
--
106-        for (Iterator<Map.Entry<UUID, BesiegeRaidData>> it = ACTIVE_RAIDS.entrySet().iterator(); it.hasNext(); ) {
107-            Map.Entry<UUID, BesiegeRaidData> entry = it.next();
108-            BesiegeRaidData raid = entry.getValue();
109-
110-            try {
111-                IColony colony = getColonyById(raid.colonyId);
112-                if (colony == null) {
113-                    LOGGER.warn("BesiegeManager.tick: colony {} not found, ending raid", raid.colonyId);
114:                    cleanupRaid(raid, false);
115:                    it.remove();
116-                    continue;
117-                }
118-
119-                ServerPlayer besieger = SERVER.getPlayerList().getPlayer(raid.besiegingPlayerUUID);
120-
121-                // --- Timer expired: defenders win ---
122-                if (System.currentTimeMillis() >= raid.endTime) {
123-                    if (TaxConfig.isNormalLogging())
--
126-                            Component.literal("The besiege of " + colony.getName() + " has failed â€” the defenders held out!")
127-                                    .withStyle(ChatFormatting.RED));
128-                    broadcastToNearbyPlayers(colony,
129-                            Component.literal(colony.getName() + " successfully repelled the besiege!")
130-                                    .withStyle(ChatFormatting.GREEN), 200);
131-                    // Route through completeBesiege so siege spoils + cooldown + cleanup all fire
132-                    // via a single path. Previously the timeout cleaned up directly, skipping
133-                    // defender-victory siege spoils entirely.
134:                    completeBesiege(raid, false, colony);
135:                    it.remove();
136-                    continue;
137-                }
138-
139-                // --- Besieger left the area ---
140-                if (besieger != null) {
141-                    BlockPos center = colony.getCenter();
142-                    double dist = besieger.distanceToSqr(center.getX(), center.getY(), center.getZ());
143-                    int maxRadius = TaxConfig.getBesiegePlayerStayRadius();
144-                    if (dist > (double) maxRadius * maxRadius) {
145-                        besieger.sendSystemMessage(Component.literal(
146-                                "You left the besiege area â€” the raid has been cancelled!")
147-                                .withStyle(ChatFormatting.RED));
148:                        cleanupRaid(raid, false);
149-                        applyCooldown(raid.besiegingPlayerUUID);
150:                        it.remove();
151-                        continue;
152-                    }
153-
154-                    // Track allies: anyone who recently damaged a defender
155-                    // (ally tracking is done in the kill/hurt event â€” see RaidKillTracker integration)
156-                }
157-
158-                // --- Victory: all defenders dead ---
159-                if (allDefendersDead(raid, colony)) {
160-                    if (TaxConfig.isNormalLogging())
161-                        LOGGER.info("Besiege raid on colony {} successful â€” besieger wins", colony.getName());
162:                    completeBesiege(raid, true, colony);
163:                    it.remove();
164-                    continue;
165-                }
166-
167-                // --- Update boss bar ---
168-                updateBossBar(raid, colony);
169-
170-            } catch (Exception e) {
171-                LOGGER.error("Error ticking besiege raid for colony {}", entry.getKey(), e);
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 321ms:
384-                LOGGER.info("Besiege {} started on colony {} ({}) by {} with {} total defenders ({}g {}m {}e)",
385-                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
386-                        besieger.getName().getString(), totalDefenders, guardCount, militiaCount, mercCount);
387-
388-            return true;
389-
390-        } catch (Exception e) {
391-            LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
392:            ACTIVE_RAIDS.remove(besiegerUUID);
393:            removeFromColonyIndex(colonyId, besiegerUUID);
394-            return false;
395-        }
396-    }
397-
398-    /** Remove a besieger from the colonyâ†’raid index; drop the colony entry when empty. */
399:    private static void removeFromColonyIndex(int colonyId, UUID besiegerUUID) {
400-        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
401-        if (set == null) return;
402-        set.remove(besiegerUUID);
403-        if (set.isEmpty()) COLONY_RAID_INDEX.remove(colonyId, set);
404-    }
405-
406-
407-    private static int makeGuardsHostile(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
--
762-                        .withStyle(ChatFormatting.GOLD), 300);
763-
764-        if (TaxConfig.isNormalLogging())
765-            LOGGER.info("Besiege reclaim: colony {} reclaimed by {}", colony.getName(),
766-                    getPlayerName(raid.besiegingPlayerUUID));
767-    }
768-
769-
770:    private static void cleanupRaid(BesiegeRaidData raid, boolean removeFromMap) {
771-        IColony colony = getColonyById(raid.colonyId);
772-        if (colony != null) {
773-            // Revoke combat permissions from the besieger (and any allies)
774-            revokeBesiegeCombatPermissions(colony, raid.besiegingPlayerUUID);
775-            for (UUID ally : raid.alliedPlayers) {
776-                revokeBesiegeCombatPermissions(colony, ally);
777-            }
778-
--
826-            try {
827-                raid.bossEvent.removeAllPlayers();
828-            } catch (Exception e) {
829-                LOGGER.warn("Failed to remove besiege boss bar", e);
830-            }
831-            raid.bossEvent = null;
832-        }
833-
834:        if (removeFromMap) {
835:            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
836:            removeFromColonyIndex(raid.colonyId, raid.besiegingPlayerUUID);
837-        }
838-    }
839-
840-
841-    private static void createBossBar(BesiegeRaidData raid, ServerPlayer besieger, IColony colony, int totalDefenders) {
842-        try {
843-            int minutes = TaxConfig.getBesiegeDurationMinutes();
844-            Component text = Component.literal(
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 8 \"catch \\(Exception e\\)|Error ticking besiege\" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 273ms:
162-                    completeBesiege(raid, true, colony);
163-                    it.remove();
164-                    continue;
165-                }
166-
167-                // --- Update boss bar ---
168-                updateBossBar(raid, colony);
169-
170:            } catch (Exception e) {
171:                LOGGER.error("Error ticking besiege raid for colony {}", entry.getKey(), e);
172-            }
173-        }
174-    }
175-
176-
177-    /**
178-     * Start a besiege raid. Validates all preconditions.
179-     * Returns true if the raid started successfully.
--
382-
383-            if (TaxConfig.isNormalLogging())
384-                LOGGER.info("Besiege {} started on colony {} ({}) by {} with {} total defenders ({}g {}m {}e)",
385-                        isReclaim ? "reclaim" : "raid", colony.getName(), colonyId,
386-                        besieger.getName().getString(), totalDefenders, guardCount, militiaCount, mercCount);
387-
388-            return true;
389-
390:        } catch (Exception e) {
391-            LOGGER.error("Failed to start besiege raid on colony {}", colony.getName(), e);
392-            ACTIVE_RAIDS.remove(besiegerUUID);
393-            removeFromColonyIndex(colonyId, besiegerUUID);
394-            return false;
395-        }
396-    }
397-
398-    /** Remove a besieger from the colonyâ†’raid index; drop the colony entry when empty. */
--
412-            if (entityOpt.isEmpty()) continue;
413-            AbstractEntityCitizen guard = entityOpt.get();
414-
415-            try {
416-                applyDefenderAI(guard, besieger, raid);
417-                applyDefenderEffects(guard, TaxConfig.getBesiegeDurationMinutes());
418-                raid.hostileCitizenIds.add(citizenData.getId());
419-                count++;
420:            } catch (Exception e) {
421-                LOGGER.warn("Failed to make guard {} hostile during besiege", citizenData.getName(), e);
422-            }
423-        }
424-        return count;
425-    }
426-
427-    private static int convertCitizensToMilitia(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
428-        List<ICitizenData> eligible = new ArrayList<>();
--
449-
450-            try {
451-                // Equip wooden sword
452-                entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
453-                applyDefenderAI(entity, besieger, raid);
454-                applyDefenderEffects(entity, TaxConfig.getBesiegeDurationMinutes());
455-                raid.hostileCitizenIds.add(citizen.getId());
456-                count++;
457:            } catch (Exception e) {
458-                LOGGER.warn("Failed to convert citizen {} to militia during besiege", citizen.getName(), e);
459-            }
460-        }
461-        return count;
462-    }
463-
464-    private static void applyDefenderAI(AbstractEntityCitizen entity, ServerPlayer besieger, BesiegeRaidData raid) {
465-        entity.goalSelector.removeAllGoals(g -> true);
--
527-    private static int spawnMercenaries(IColony colony, ServerPlayer besieger, BesiegeRaidData raid) {
528-        Level world = colony.getWorld();
529-        if (!(world instanceof ServerLevel)) return 0;
530-
531-        int buildingCount = 0;
532-        try {
533-            buildingCount = net.machiavelli.minecolonytax.compat.ColonyBuildingUtil
534-                    .getBuildings(colony).size();
535:        } catch (Exception e) {
536-            LOGGER.warn("Could not count buildings for mercenary calc in colony {}", colony.getName());
537-        }
538-
539-        int count = (int) (buildingCount * TaxConfig.getBesiegeExtraMercenariesPerBuilding());
540-        count = Math.min(count, TaxConfig.getBesiegeMaxMercenaries());
541-        if (count <= 0) return 0;
542-
543-        int durationTicks = TaxConfig.getBesiegeDurationMinutes() * 60 * 20;
--
553-                merc.setTarget(besieger);
554-
555-                merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1));
556-                merc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, durationTicks, 0));
557-
558-                world.addFreshEntity(merc);
559-                raid.spawnedMercenaries.add(merc);
560-                spawned++;
561:            } catch (Exception e) {
562-                LOGGER.warn("Failed to spawn mercenary {} for besiege on colony {}", i, colony.getName(), e);
563-            }
564-        }
565-        return spawned;
566-    }
567-
568-
569-    /**
--
591-                if (militia == null) continue;
592-                BlockPos spawnPos = findSpawnPos(colony.getCenter(), world);
593-                militia.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
594-                militia.setTarget(besieger);
595-                militia.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 0));
596-                world.addFreshEntity(militia);
597-                raid.militiaSupport.add(militia);
598-                spawned++;
599:            } catch (Exception e) {
600-                LOGGER.warn("Failed to spawn militia reinforcement {} for colony {}", i, colony.getName(), e);
601-            }
602-        }
603-        if (spawned > 0 && TaxConfig.isNormalLogging()) {
604-            LOGGER.info("Spawned {} militia-upgrade reinforcements for colony {} (multiplier {})",
605-                    spawned, colony.getName(),
606-                    String.format(java.util.Locale.ROOT, "%.2f", multiplier));
607-        }
--
689-        // colony, but we keep calling it for the notifications.
690-        try {
691-            boolean reclaimed = net.machiavelli.minecolonytax.occupation.OccupationManager
692-                    .reclaimByOriginalOwner(colony.getID(), raid.besiegingPlayerUUID);
693-            if (reclaimed && TaxConfig.isNormalLogging()) {
694-                LOGGER.info("Besiege victory doubled as counter-besiege reclaim: {} reclaimed TAX_ONLY occupation of {}",
695-                        raid.besiegingPlayerUUID, colony.getName());
696-            }
697:        } catch (Exception e) {
698-            LOGGER.warn("Counter-besiege reclaim handoff failed for colony {}", colony.getName(), e);
699-        }
700-
701-        int tributePct = TaxConfig.getBesiegeTributePercent();
702-        int durationHours = TaxConfig.getBesiegeTributeDurationHours();
703-
704-        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
705-
--
792-                        entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
793-                        entity.removeEffect(MobEffects.MOVEMENT_SPEED);
794-                        entity.removeEffect(MobEffects.DAMAGE_BOOST);
795-                        // Restore job AI
796-                        if (citizen.getJob() != null) {
797-                            entity.getCitizenJobHandler().onJobChanged(citizen.getJob());
798-                        }
799-                    }
800:                } catch (Exception e) {
801-                    LOGGER.warn("Failed to restore citizen {} AI after besiege", citizenId, e);
802-                }
803-            }
804-
805-            // Despawn militia-upgrade reinforcements (NOT victory-counted)
806-            for (Entity militia : raid.militiaSupport) {
807-                try {
808-                    if (militia.isAlive()) militia.remove(Entity.RemovalReason.DISCARDED);
809:                } catch (Exception e) {
810-                    LOGGER.warn("Failed to despawn militia reinforcement after besiege", e);
811-                }
812-            }
813-
814-            // Despawn mercenaries
815-            for (Entity merc : raid.spawnedMercenaries) {
816-                try {
817-                    if (merc.isAlive()) merc.remove(Entity.RemovalReason.DISCARDED);
818:                } catch (Exception e) {
819-                    LOGGER.warn("Failed to despawn mercenary after besiege", e);
820-                }
821-            }
822-        }
823-
824-        // Remove boss bar
825-        if (raid.bossEvent != null) {
826-            try {
827-                raid.bossEvent.removeAllPlayers();
828:            } catch (Exception e) {
829-                LOGGER.warn("Failed to remove besiege boss bar", e);
830-            }
831-            raid.bossEvent = null;
832-        }
833-
834-        if (removeFromMap) {
835-            ACTIVE_RAIDS.remove(raid.besiegingPlayerUUID);
836-            removeFromColonyIndex(raid.colonyId, raid.besiegingPlayerUUID);
--
858-                    if (!player.equals(besieger) && player.level() == world) {
859-                        double dist = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
860-                        if (dist <= 200.0 * 200.0) {
861-                            try { raid.bossEvent.addPlayer(player); } catch (Exception ignored) {}
862-                        }
863-                    }
864-                }
865-            }
866:        } catch (Exception e) {
867-            LOGGER.error("Failed to create boss bar for besiege on colony {}", colony.getName(), e);
868-        }
869-    }
870-
871-    private static void updateBossBar(BesiegeRaidData raid, IColony colony) {
872-        if (raid.bossEvent == null) return;
873-
874-        try {
--
886-                    : progress > 0.3f ? BossEvent.BossBarColor.YELLOW
887-                    : BossEvent.BossBarColor.RED;
888-
889-            raid.bossEvent.setColor(color);
890-            raid.bossEvent.setProgress(progress);
891-            raid.bossEvent.setName(Component.literal(
892-                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
893-                            colony.getName(), aliveDefenders, mm, ss)));
894:        } catch (Exception e) {
895-            LOGGER.warn("Failed to update besiege boss bar", e);
896-        }
897-    }
898-
899-
900-    public static boolean isColonyBesieged(int colonyId) {
901-        return OCCUPATIONS.containsKey(colonyId);
902-    }
--
1164-            // Enable combat actions on the hostile rank
1165-            for (Action a : TaxConfig.getWarActions()) {
1166-                perms.setPermission(hostile, a, true);
1167-            }
1168-
1169-            if (TaxConfig.isDebugLogging())
1170-                LOGGER.debug("Granted besiege combat permissions to {} on colony {}",
1171-                        playerUUID, colony.getName());
1172:        } catch (Exception e) {
1173-            LOGGER.error("Failed to grant besiege combat permissions for {} on colony {}",
1174-                    playerUUID, colony.getName(), e);
1175-        }
1176-    }
1177-
1178-    /**
1179-     * Revokes combat permissions and demotes the player from hostile back to neutral.
1180-     * Called during raid cleanup.
--
1195-            if (!playerUUID.equals(owner)) {
1196-                Rank neutral = perms.getRankNeutral();
1197-                perms.setPlayerRank(playerUUID, neutral, colony.getWorld());
1198-            }
1199-
1200-            if (TaxConfig.isDebugLogging())
1201-                LOGGER.debug("Revoked besiege combat permissions from {} on colony {}",
1202-                        playerUUID, colony.getName());
1203:        } catch (Exception e) {
1204-            LOGGER.error("Failed to revoke besiege combat permissions for {} on colony {}",
1205-                    playerUUID, colony.getName(), e);
1206-        }
1207-    }
1208-
1209-    private static void loadData(MinecraftServer server) {
1210-        File f = new File(server.getServerDirectory(), STORAGE_FILE);
1211-        if (!f.exists()) return;
--
1214-            List<BesiegeOccupationData> list = GSON.fromJson(r, type);
1215-            if (list != null) {
1216-                for (BesiegeOccupationData occ : list) {
1217-                    OCCUPATIONS.put(occ.colonyId, occ);
1218-                }
1219-            }
1220-            if (TaxConfig.isNormalLogging())
1221-                LOGGER.info("Loaded {} besiege occupation record(s)", OCCUPATIONS.size());
1222:        } catch (Exception e) {
1223-            LOGGER.error("Failed to load besiege occupation data", e);
1224-        }
1225-    }
1226-
1227-    private static void saveData() {
1228-        if (SERVER == null) return;
1229-        // Snapshot on the calling (server) thread.
1230-        final List<BesiegeOccupationData> list = new ArrayList<>(OCCUPATIONS.values());
1231-        final File f = new File(SERVER.getServerDirectory(), STORAGE_FILE);
1232-
1233-        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("besiege", () -> {
1234-            try {
1235-                f.getParentFile().mkdirs();
1236-                try (FileWriter w = new FileWriter(f)) {
1237-                    GSON.toJson(list, w);
1238-                }
1239:            } catch (Exception e) {
1240-                LOGGER.error("Failed to save besiege occupation data", e);
1241-            }
1242-        });
1243-    }
1244-
1245-
1246-    /** Transient raid state â€” not persisted. */
1247-    public static class BesiegeRaidData {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

web search: 
web search: Forge 1.20.1 PlayerInteractEvent EntityInteract setCancellationResult source ...
web search: 
web search: https://cocalc.com/github/MinecraftForge/MinecraftForge/blob/1.20.x/src/main/java/net/minecraftforge/event/entity/player/PlayerInteractEvent.java
web search: 
web search: https://cocalc.com/github/MinecraftForge/MinecraftForge/blob/1.20.x/src/main/java/net/minecraftforge/event/entity/player/PlayerInteractEvent.java
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 8 "reclaimByOriginalOwner|forceVassalize|completeBesiegeVictory" src/main/java/net/machiavelli/minecolonytax' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 5 "forceVassalize" src/main/java/net/machiavelli/minecolonytax' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 5 "class OccupationManager|enum.*Occupation|TAX_ONLY|originalOwner" src/main/java/net/machiavelli/minecolonytax/occupation src/main/java/net/machiavelli/minecolonytax' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 326ms:
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-699-        }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-700-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-701-        int tributePct = TaxConfig.getBesiegeTributePercent();
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-702-        int durationHours = TaxConfig.getBesiegeTributeDurationHours();
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-703-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:704:        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-705-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-706-        if (vassalized) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-707-            // Store occupation record
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-708-            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-709-            BesiegeOccupationData occ = new BesiegeOccupationData(
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-775-                }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-776-            } else if (TaxConfig.isWarVassalizationEnabled()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-777-                // Vassalize the losing colony instead of transferring ownership
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-778-                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-779-                int durationHours = TaxConfig.getWarVassalizationDurationHours();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:780:                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-781-                        war.getColony(),
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-782-                        war.getAttacker(),
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-783-                        tributePercent,
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-784-                        durationHours);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-785-                if (vassalized) {
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1204-                    colony.getID(),
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1205-                    net.machiavelli.minecolonytax.permissions.ColonyTierGuard.getTransferDenialReason(colony));
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1206-            if (TaxConfig.isWarVassalizationEnabled()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1207-                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1208-                int durationHours = TaxConfig.getWarVassalizationDurationHours();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1209:                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1210-                        colony, newOwnerUUID, tributePercent, durationHours);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1211-                if (vassalized) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1212-                    WARSYSTEM_LOGGER.info("Primary colony {} vassalized by {} instead of transferred ({}% tribute for {}h)",
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1213-                            colony.getName(), newOwnerUUID, tributePercent, durationHours);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1214-                    WarData war = ACTIVE_WARS.get(colony.getID());
--
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-286-
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-287-    /**
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-288-     * Forces a colony to become a vassal as a result of war victory, bypassing the
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-289-     * normal proposal/acceptance flow. durationHours=0 means permanent.
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-290-     */
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:291:    public static boolean forceVassalize(IColony vassalColony, UUID overlordUUID, int tributePercent,
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-292-            int durationHours) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-293-        if (vassalColony == null || overlordUUID == null) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:294:            LOGGER.warn("forceVassalize called with null colony or overlord");
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-295-            return false;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-296-        }
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-297-
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-298-        int colonyId = vassalColony.getID();
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-299-
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 328ms:
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-35- *   buildings or items.
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-36- * - The original owner has a configurable window (OccupationDurationDays) to
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-37- *   wage a reclamation war.  If they do not, full ownership transfers permanently
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-38- *   to the occupier when the occupation expires.
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-39- */
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:40:public class OccupationManager {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-41-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-42-    private static final Logger LOGGER = LogManager.getLogger(OccupationManager.class);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-43-    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-44-    private static final String STORAGE_FILE = "config/warntax/occupations.json";
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-45-
--
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-48-    private static MinecraftServer serverInstance;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-49-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-50-    /**
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-51-     * How an occupation resolves when its timer expires.
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-52-     *
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:53:     * Primary colonies always run in TAX_ONLY (deed never moves), unless
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-54-     * EnablePrimaryColonyTransfer is on. Secondaries run in TRANSFER_PENDING.
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-55-     */
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:56:    public enum OccupationMode {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-57-        /** Expiry transfers full ownership to the occupier. Default for secondaries. */
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-58-        TRANSFER_PENDING,
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-59-        /** Expiry auto-reclaims тАФ taxes route back to original owner, deed never moves. Primary colonies. */
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:60:        TAX_ONLY
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-61-    }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-62-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-63-    public static class OccupationData {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-64-        public final int colonyId;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-65-        public final String occupierUUID;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:66:        public final String originalOwnerUUID;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-67-        public final int occupierColonyId;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-68-        public final long startTime;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-69-        public final long expirationTime;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-70-        public final String colonyName;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-71-        public boolean reclamationAttempted;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-72-        public long lastTaxCollectionTime;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-73-        /** Null on save files written before the Siege SMP upgrade тАФ see {@link #getMode()}. */
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-74-        public OccupationMode mode;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-75-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:76:        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-77-                              int occupierColonyId, String colonyName,
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-78-                              long startTime, long expirationTime) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:79:            this(colonyId, occupierUUID, originalOwnerUUID, occupierColonyId, colonyName,
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-80-                    startTime, expirationTime, OccupationMode.TRANSFER_PENDING);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-81-        }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-82-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:83:        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-84-                              int occupierColonyId, String colonyName,
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-85-                              long startTime, long expirationTime, OccupationMode mode) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-86-            this.colonyId = colonyId;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-87-            this.occupierUUID = occupierUUID.toString();
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:88:            this.originalOwnerUUID = originalOwnerUUID.toString();
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-89-            this.occupierColonyId = occupierColonyId;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-90-            this.colonyName = colonyName;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-91-            this.startTime = startTime;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-92-            this.expirationTime = expirationTime;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-93-            this.reclamationAttempted = false;
--
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-121-        public UUID getOccupierUUID() {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-122-            return UUID.fromString(occupierUUID);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-123-        }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-124-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-125-        public UUID getOriginalOwnerUUID() {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:126:            return UUID.fromString(originalOwnerUUID);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-127-        }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-128-    }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-129-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-130-    private static class OccupationSaveData {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-131-        public List<OccupationData> occupations = new ArrayList<>();
--
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-158-        if (ACTIVE_OCCUPATIONS.containsKey(colonyId)) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-159-            LOGGER.warn("Colony {} is already occupied, cannot start new occupation", colony.getName());
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-160-            return;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-161-        }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-162-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:163:        UUID originalOwner = colony.getPermissions().getOwner();
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-164-        int occupierColonyId = attackerColony != null ? attackerColony.getID() : -1;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-165-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-166-        // Decide mode by colony tier. Primary colonies are tax-only by default;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-167-        // secondaries follow the legacy transfer-on-expiry flow.
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-168-        // EnablePrimaryColonyTransfer lets server owners opt back into the legacy
--
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-176-        boolean isPrimary;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-177-        UUID trackedFirstOwner = net.machiavelli.minecolonytax.FirstColonyTracker.getFirstColonyOwner(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-178-        if (trackedFirstOwner != null) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-179-            isPrimary = true;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-180-        } else {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:181:            isPrimary = originalOwner != null
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:182:                    && net.machiavelli.minecolonytax.FirstColonyTracker.isFirstColony(originalOwner, colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-183-        }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-184-        OccupationMode mode;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-185-        int durationDays;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-186-        if (isPrimary && !TaxConfig.isPrimaryColonyTransferEnabled()) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:187:            mode = OccupationMode.TAX_ONLY;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-188-            durationDays = TaxConfig.getPrimaryColonyTaxOccupationDays();
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-189-        } else {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-190-            mode = OccupationMode.TRANSFER_PENDING;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-191-            durationDays = TaxConfig.getOccupationDurationDays();
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-192-        }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-193-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-194-        long now = System.currentTimeMillis();
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-195-        long expirationTime = now + (durationDays * 24L * 60L * 60L * 1000L);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-196-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-197-        OccupationData data = new OccupationData(
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:198:                colonyId, occupierUUID, originalOwner,
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-199-                occupierColonyId, colony.getName(),
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-200-                now, expirationTime, mode
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-201-        );
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-202-        ACTIVE_OCCUPATIONS.put(colonyId, data);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-203-        saveData();
--
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-205-        if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-206-            LOGGER.info("Colony {} is now OCCUPIED by {} for {} days [mode={}]",
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-207-                    colony.getName(), occupierUUID, durationDays, mode);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-208-        }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-209-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:210:        final boolean isTaxOnly = mode == OccupationMode.TAX_ONLY;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-211-        final String expiryConsequence = isTaxOnly
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-212-                ? "If not reclaimed in time, the occupation lapses and taxes route back to the owner."
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-213-                : "If the original owner does not reclaim within " + durationDays + " days, full ownership transfers to you!";
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-214-        final String ownerStakes = isTaxOnly
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-215-                ? "This is your Primary colony тАФ the deed is safe. Reclaim within " + durationDays
--
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-237-                                .withStyle(ChatFormatting.AQUA));
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-238-                occupier.sendSystemMessage(occupierMsg);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-239-            }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-240-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-241-            // Notify original owner
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:242:            ServerPlayer owner = server.getPlayerList().getPlayer(originalOwner);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-243-            if (owner != null) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-244-                Component ownerMsg = Component.literal(isTaxOnly
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-245-                                ? "YOUR PRIMARY COLONY HAS BEEN TAX-OCCUPIED"
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-246-                                : "YOUR COLONY HAS BEEN OCCUPIED")
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-247-                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
--
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-444-            }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-445-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-446-            UUID occupierUUID = data.getOccupierUUID();
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-447-            OccupationMode mode = data.getMode();
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-448-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:449:            if (mode == OccupationMode.TAX_ONLY) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-450-                // Primary colony auto-reclaim тАФ deed never moves, taxes simply revert
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-451-                // to the original owner. Friendly notification on both sides.
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-452-                if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-453-                    LOGGER.info("Tax-only occupation expired for primary colony {} - auto-reclaiming", colony.getName());
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-454-                }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-455-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:456:                ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:457:                if (originalOwner != null) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:458:                    originalOwner.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-459-                            Component.literal("Your Primary colony " + colony.getName()
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-460-                                            + " has been auto-reclaimed тАФ the besieger's hold has lapsed and your taxes now route to you again.")
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-461-                                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-462-                }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-463-                ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
--
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-481-                    Component broadcastMsg = Component.literal(colony.getName() + " has been permanently claimed by its occupier!")
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-482-                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-483-                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-484-                        p.sendSystemMessage(broadcastMsg);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-485-                    }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:486:                    ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:487:                    if (originalOwner != null) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:488:                        originalOwner.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-489-                                Component.literal("You failed to reclaim " + colony.getName()
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-490-                                                + " within the deadline. Ownership has been permanently transferred!")
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-491-                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-492-                    }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-493-                } else {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-494-                    // Transfer was denied (e.g. by ColonyTierGuard) or failed. Don't
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-495-                    // broadcast a permanent-claim message that isn't true; treat as a
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-496-                    // tax-only-style lapse and reclaim to the original owner.
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-497-                    LOGGER.info("Transfer for colony {} was denied or failed - lapsing occupation instead", colony.getName());
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:498:                    ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:499:                    if (originalOwner != null) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:500:                        originalOwner.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-501-                                Component.literal("The occupation of " + colony.getName()
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-502-                                                + " has lapsed тАФ the deed remains with you.")
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-503-                                        .withStyle(ChatFormatting.GREEN));
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-504-                    }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-505-                }
--
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-516-    /**
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-517-     * Manually end a tax-only occupation early тАФ called when the owner successfully
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-518-     * mounts a counter-besiege. Restores everything to pre-occupation state.
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-519-     *
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-520-     * Strict guards:
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:521:     *  - Only TAX_ONLY occupations can be reclaimed this way. TRANSFER_PENDING
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-522-     *    occupations (secondary colonies) must follow the legacy reclaim flow
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-523-     *    or be ended by the standard expiry/cancel paths.
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-524-     *  - {@code reclaimerUUID} must match the recorded original owner. Prevents
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-525-     *    arbitrary players from cancelling another player's occupation.
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-526-     *  - Uses atomic remove so concurrent calls don't double-fire.
--
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-528-     * @return true if an occupation was ended, false otherwise
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-529-     */
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-530-    public static boolean reclaimByOriginalOwner(int colonyId, UUID reclaimerUUID) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-531-        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-532-        if (data == null) return false;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:533:        if (data.getMode() != OccupationMode.TAX_ONLY) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:534:            LOGGER.info("reclaimByOriginalOwner refused: occupation on colony {} is mode {} (not TAX_ONLY)",
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-535-                    colonyId, data.getMode());
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-536-            return false;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-537-        }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:538:        if (reclaimerUUID == null || !reclaimerUUID.toString().equals(data.originalOwnerUUID)) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-539-            LOGGER.info("reclaimByOriginalOwner refused: caller {} is not the recorded original owner {} of colony {}",
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:540:                    reclaimerUUID, data.originalOwnerUUID, colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-541-            return false;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-542-        }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-543-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-544-        // Atomic remove тАФ bail if a concurrent caller already cleared it.
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-545-        if (!ACTIVE_OCCUPATIONS.remove(colonyId, data)) return false;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-546-        saveData();
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-547-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-548-        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-549-        if (server != null) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:550:            ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:551:            if (originalOwner != null) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:552:                originalOwner.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-553-                        Component.literal("You have successfully reclaimed " + data.colonyName + "!")
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-554-                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-555-            }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-556-            ServerPlayer occupier = server.getPlayerList().getPlayer(data.getOccupierUUID());
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-557-            if (occupier != null) {
--
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-576-    }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-577-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-578-    public static boolean isOriginalOwner(UUID playerUUID, int colonyId) {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-579-        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-580-        if (data == null) return false;
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java:581:        return data.originalOwnerUUID.equals(playerUUID.toString());
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-582-    }
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-583-
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-584-    public static void saveData() {
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-585-        // Snapshot on the calling (server) thread; the worker only touches
src/main/java/net/machiavelli/minecolonytax/occupation\OccupationManager.java-586-        // a fresh ArrayList so it can't ConcurrentModification on the live map.
--
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-203-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-204-            return false;
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-205-        }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-206-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-207-        // 3. Primary colonies CAN now be besieged. Outcome routes through
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:208:        // OccupationManager in TAX_ONLY mode (deed never moves; auto-reclaim after
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-209-        // PrimaryColonyTaxOccupationDays). Secondaries continue to use the legacy
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-210-        // TRANSFER_PENDING flow. See ColonyTierGuard + OccupationManager.
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-211-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-212-        // 4. Solo rule: this besieger may not already have an active raid elsewhere.
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-213-        // Multiple besiegers attacking the SAME colony concurrently is allowed.
--
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-680-        if (loserOwner != null && !loserOwner.equals(winnerOwner)) sendToPlayer(loserOwner, loseMsg);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-681-    }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-682-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-683-    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-684-        // Counter-besiege reclaim handoff: if the besieger is the original owner
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:685:        // of a TAX_ONLY war-occupation on this colony, clear that occupation
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-686-        // first тАФ they've successfully reclaimed their primary via solo combat
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-687-        // (the Siege SMP "owner mounts counter-besiege" loop). The legacy
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-688-        // besiege vassalization flow then no-ops because they already own the
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-689-        // colony, but we keep calling it for the notifications.
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-690-        try {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-691-            boolean reclaimed = net.machiavelli.minecolonytax.occupation.OccupationManager
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-692-                    .reclaimByOriginalOwner(colony.getID(), raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-693-            if (reclaimed && TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:694:                LOGGER.info("Besiege victory doubled as counter-besiege reclaim: {} reclaimed TAX_ONLY occupation of {}",
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-695-                        raid.besiegingPlayerUUID, colony.getName());
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-696-            }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-697-        } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-698-            LOGGER.warn("Counter-besiege reclaim handoff failed for colony {}", colony.getName(), e);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-699-        }
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-35- *   buildings or items.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-36- * - The original owner has a configurable window (OccupationDurationDays) to
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-37- *   wage a reclamation war.  If they do not, full ownership transfers permanently
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-38- *   to the occupier when the occupation expires.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-39- */
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:40:public class OccupationManager {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-41-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-42-    private static final Logger LOGGER = LogManager.getLogger(OccupationManager.class);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-43-    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-44-    private static final String STORAGE_FILE = "config/warntax/occupations.json";
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-45-
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-48-    private static MinecraftServer serverInstance;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-49-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-50-    /**
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-51-     * How an occupation resolves when its timer expires.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-52-     *
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:53:     * Primary colonies always run in TAX_ONLY (deed never moves), unless
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-54-     * EnablePrimaryColonyTransfer is on. Secondaries run in TRANSFER_PENDING.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-55-     */
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:56:    public enum OccupationMode {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-57-        /** Expiry transfers full ownership to the occupier. Default for secondaries. */
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-58-        TRANSFER_PENDING,
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-59-        /** Expiry auto-reclaims тАФ taxes route back to original owner, deed never moves. Primary colonies. */
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:60:        TAX_ONLY
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-61-    }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-62-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-63-    public static class OccupationData {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-64-        public final int colonyId;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-65-        public final String occupierUUID;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:66:        public final String originalOwnerUUID;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-67-        public final int occupierColonyId;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-68-        public final long startTime;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-69-        public final long expirationTime;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-70-        public final String colonyName;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-71-        public boolean reclamationAttempted;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-72-        public long lastTaxCollectionTime;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-73-        /** Null on save files written before the Siege SMP upgrade тАФ see {@link #getMode()}. */
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-74-        public OccupationMode mode;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-75-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:76:        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-77-                              int occupierColonyId, String colonyName,
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-78-                              long startTime, long expirationTime) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:79:            this(colonyId, occupierUUID, originalOwnerUUID, occupierColonyId, colonyName,
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-80-                    startTime, expirationTime, OccupationMode.TRANSFER_PENDING);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-81-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-82-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:83:        public OccupationData(int colonyId, UUID occupierUUID, UUID originalOwnerUUID,
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-84-                              int occupierColonyId, String colonyName,
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-85-                              long startTime, long expirationTime, OccupationMode mode) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-86-            this.colonyId = colonyId;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-87-            this.occupierUUID = occupierUUID.toString();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:88:            this.originalOwnerUUID = originalOwnerUUID.toString();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-89-            this.occupierColonyId = occupierColonyId;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-90-            this.colonyName = colonyName;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-91-            this.startTime = startTime;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-92-            this.expirationTime = expirationTime;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-93-            this.reclamationAttempted = false;
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-121-        public UUID getOccupierUUID() {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-122-            return UUID.fromString(occupierUUID);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-123-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-124-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-125-        public UUID getOriginalOwnerUUID() {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:126:            return UUID.fromString(originalOwnerUUID);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-127-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-128-    }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-129-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-130-    private static class OccupationSaveData {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-131-        public List<OccupationData> occupations = new ArrayList<>();
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-158-        if (ACTIVE_OCCUPATIONS.containsKey(colonyId)) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-159-            LOGGER.warn("Colony {} is already occupied, cannot start new occupation", colony.getName());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-160-            return;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-161-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-162-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:163:        UUID originalOwner = colony.getPermissions().getOwner();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-164-        int occupierColonyId = attackerColony != null ? attackerColony.getID() : -1;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-165-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-166-        // Decide mode by colony tier. Primary colonies are tax-only by default;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-167-        // secondaries follow the legacy transfer-on-expiry flow.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-168-        // EnablePrimaryColonyTransfer lets server owners opt back into the legacy
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-176-        boolean isPrimary;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-177-        UUID trackedFirstOwner = net.machiavelli.minecolonytax.FirstColonyTracker.getFirstColonyOwner(colonyId);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-178-        if (trackedFirstOwner != null) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-179-            isPrimary = true;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-180-        } else {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:181:            isPrimary = originalOwner != null
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:182:                    && net.machiavelli.minecolonytax.FirstColonyTracker.isFirstColony(originalOwner, colonyId);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-183-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-184-        OccupationMode mode;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-185-        int durationDays;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-186-        if (isPrimary && !TaxConfig.isPrimaryColonyTransferEnabled()) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:187:            mode = OccupationMode.TAX_ONLY;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-188-            durationDays = TaxConfig.getPrimaryColonyTaxOccupationDays();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-189-        } else {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-190-            mode = OccupationMode.TRANSFER_PENDING;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-191-            durationDays = TaxConfig.getOccupationDurationDays();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-192-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-193-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-194-        long now = System.currentTimeMillis();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-195-        long expirationTime = now + (durationDays * 24L * 60L * 60L * 1000L);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-196-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-197-        OccupationData data = new OccupationData(
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:198:                colonyId, occupierUUID, originalOwner,
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-199-                occupierColonyId, colony.getName(),
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-200-                now, expirationTime, mode
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-201-        );
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-202-        ACTIVE_OCCUPATIONS.put(colonyId, data);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-203-        saveData();
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-205-        if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-206-            LOGGER.info("Colony {} is now OCCUPIED by {} for {} days [mode={}]",
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-207-                    colony.getName(), occupierUUID, durationDays, mode);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-208-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-209-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:210:        final boolean isTaxOnly = mode == OccupationMode.TAX_ONLY;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-211-        final String expiryConsequence = isTaxOnly
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-212-                ? "If not reclaimed in time, the occupation lapses and taxes route back to the owner."
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-213-                : "If the original owner does not reclaim within " + durationDays + " days, full ownership transfers to you!";
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-214-        final String ownerStakes = isTaxOnly
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-215-                ? "This is your Primary colony тАФ the deed is safe. Reclaim within " + durationDays
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-237-                                .withStyle(ChatFormatting.AQUA));
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-238-                occupier.sendSystemMessage(occupierMsg);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-239-            }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-240-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-241-            // Notify original owner
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:242:            ServerPlayer owner = server.getPlayerList().getPlayer(originalOwner);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-243-            if (owner != null) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-244-                Component ownerMsg = Component.literal(isTaxOnly
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-245-                                ? "YOUR PRIMARY COLONY HAS BEEN TAX-OCCUPIED"
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-246-                                : "YOUR COLONY HAS BEEN OCCUPIED")
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-247-                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-444-            }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-445-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-446-            UUID occupierUUID = data.getOccupierUUID();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-447-            OccupationMode mode = data.getMode();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-448-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:449:            if (mode == OccupationMode.TAX_ONLY) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-450-                // Primary colony auto-reclaim тАФ deed never moves, taxes simply revert
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-451-                // to the original owner. Friendly notification on both sides.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-452-                if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-453-                    LOGGER.info("Tax-only occupation expired for primary colony {} - auto-reclaiming", colony.getName());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-454-                }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-455-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:456:                ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:457:                if (originalOwner != null) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:458:                    originalOwner.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-459-                            Component.literal("Your Primary colony " + colony.getName()
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-460-                                            + " has been auto-reclaimed тАФ the besieger's hold has lapsed and your taxes now route to you again.")
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-461-                                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-462-                }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-463-                ServerPlayer occupier = server.getPlayerList().getPlayer(occupierUUID);
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-481-                    Component broadcastMsg = Component.literal(colony.getName() + " has been permanently claimed by its occupier!")
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-482-                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-483-                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-484-                        p.sendSystemMessage(broadcastMsg);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-485-                    }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:486:                    ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:487:                    if (originalOwner != null) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:488:                        originalOwner.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-489-                                Component.literal("You failed to reclaim " + colony.getName()
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-490-                                                + " within the deadline. Ownership has been permanently transferred!")
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-491-                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-492-                    }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-493-                } else {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-494-                    // Transfer was denied (e.g. by ColonyTierGuard) or failed. Don't
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-495-                    // broadcast a permanent-claim message that isn't true; treat as a
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-496-                    // tax-only-style lapse and reclaim to the original owner.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-497-                    LOGGER.info("Transfer for colony {} was denied or failed - lapsing occupation instead", colony.getName());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:498:                    ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:499:                    if (originalOwner != null) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:500:                        originalOwner.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-501-                                Component.literal("The occupation of " + colony.getName()
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-502-                                                + " has lapsed тАФ the deed remains with you.")
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-503-                                        .withStyle(ChatFormatting.GREEN));
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-504-                    }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-505-                }
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-516-    /**
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-517-     * Manually end a tax-only occupation early тАФ called when the owner successfully
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-518-     * mounts a counter-besiege. Restores everything to pre-occupation state.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-519-     *
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-520-     * Strict guards:
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:521:     *  - Only TAX_ONLY occupations can be reclaimed this way. TRANSFER_PENDING
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-522-     *    occupations (secondary colonies) must follow the legacy reclaim flow
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-523-     *    or be ended by the standard expiry/cancel paths.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-524-     *  - {@code reclaimerUUID} must match the recorded original owner. Prevents
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-525-     *    arbitrary players from cancelling another player's occupation.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-526-     *  - Uses atomic remove so concurrent calls don't double-fire.
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-528-     * @return true if an occupation was ended, false otherwise
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-529-     */
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-530-    public static boolean reclaimByOriginalOwner(int colonyId, UUID reclaimerUUID) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-531-        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-532-        if (data == null) return false;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:533:        if (data.getMode() != OccupationMode.TAX_ONLY) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:534:            LOGGER.info("reclaimByOriginalOwner refused: occupation on colony {} is mode {} (not TAX_ONLY)",
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-535-                    colonyId, data.getMode());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-536-            return false;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-537-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:538:        if (reclaimerUUID == null || !reclaimerUUID.toString().equals(data.originalOwnerUUID)) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-539-            LOGGER.info("reclaimByOriginalOwner refused: caller {} is not the recorded original owner {} of colony {}",
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:540:                    reclaimerUUID, data.originalOwnerUUID, colonyId);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-541-            return false;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-542-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-543-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-544-        // Atomic remove тАФ bail if a concurrent caller already cleared it.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-545-        if (!ACTIVE_OCCUPATIONS.remove(colonyId, data)) return false;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-546-        saveData();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-547-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-548-        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-549-        if (server != null) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:550:            ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:551:            if (originalOwner != null) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:552:                originalOwner.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-553-                        Component.literal("You have successfully reclaimed " + data.colonyName + "!")
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-554-                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-555-            }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-556-            ServerPlayer occupier = server.getPlayerList().getPlayer(data.getOccupierUUID());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-557-            if (occupier != null) {
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-576-    }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-577-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-578-    public static boolean isOriginalOwner(UUID playerUUID, int colonyId) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-579-        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-580-        if (data == null) return false;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:581:        return data.originalOwnerUUID.equals(playerUUID.toString());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-582-    }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-583-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-584-    public static void saveData() {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-585-        // Snapshot on the calling (server) thread; the worker only touches
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-586-        // a fresh ArrayList so it can't ConcurrentModification on the live map.
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fБr 
Kerntypen unterstБtzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

 succeeded in 338ms:
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-612-        cleanupRaid(raid, true);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-613-        applyCooldown(raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-614-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-615-        if (attackerWon) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-616-            applySiegeSpoils(raid, colony, true);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-617-            if (raid.isReclaim) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-618-                completeReclaim(raid, colony);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-619-            } else {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:620:                completeBesiegeVictory(raid, colony);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-621-            }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-622-        } else {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-623-            applySiegeSpoils(raid, colony, false);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-624-            sendToPlayer(raid.besiegingPlayerUUID,
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-625-                    Component.literal("The besiege of " + colony.getName() + " failed.")
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-626-                            .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-627-        }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-628-    }
--
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-675-                .withStyle(ChatFormatting.GOLD);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-676-        Component loseMsg = Component.literal("Siege fine: " + actualSpoil + " coins paid from "
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-677-                + loser.getName() + " to " + winner.getName() + ".")
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-678-                .withStyle(ChatFormatting.RED);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-679-        if (winnerOwner != null) sendToPlayer(winnerOwner, winMsg);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-680-        if (loserOwner != null && !loserOwner.equals(winnerOwner)) sendToPlayer(loserOwner, loseMsg);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-681-    }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-682-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:683:    private static void completeBesiegeVictory(BesiegeRaidData raid, IColony colony) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-684-        // Counter-besiege reclaim handoff: if the besieger is the original owner
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-685-        // of a TAX_ONLY war-occupation on this colony, clear that occupation
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-686-        // first â€” they've successfully reclaimed their primary via solo combat
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-687-        // (the Siege SMP "owner mounts counter-besiege" loop). The legacy
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-688-        // besiege vassalization flow then no-ops because they already own the
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-689-        // colony, but we keep calling it for the notifications.
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-690-        try {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-691-            boolean reclaimed = net.machiavelli.minecolonytax.occupation.OccupationManager
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:692:                    .reclaimByOriginalOwner(colony.getID(), raid.besiegingPlayerUUID);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-693-            if (reclaimed && TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-694-                LOGGER.info("Besiege victory doubled as counter-besiege reclaim: {} reclaimed TAX_ONLY occupation of {}",
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-695-                        raid.besiegingPlayerUUID, colony.getName());
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-696-            }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-697-        } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-698-            LOGGER.warn("Counter-besiege reclaim handoff failed for colony {}", colony.getName(), e);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-699-        }
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-700-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-701-        int tributePct = TaxConfig.getBesiegeTributePercent();
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-702-        int durationHours = TaxConfig.getBesiegeTributeDurationHours();
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-703-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java:704:        boolean vassalized = VassalManager.forceVassalize(colony, raid.besiegingPlayerUUID, tributePct, durationHours);
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-705-
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-706-        if (vassalized) {
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-707-            // Store occupation record
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-708-            UUID ownerUUID = colony.getPermissions().getOwner();
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-709-            BesiegeOccupationData occ = new BesiegeOccupationData(
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-710-                    colony.getID(), colony.getName(),
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-711-                    raid.besiegingPlayerUUID, ownerUUID,
src/main/java/net/machiavelli/minecolonytax\besiege\BesiegeManager.java-712-                    System.currentTimeMillis(), tributePct);
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-772-                } else {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-773-                    // Direct transfer (legacy behavior)
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-774-                    transferOwnership(war.getColony(), war.getAttacker());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-775-                }
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-776-            } else if (TaxConfig.isWarVassalizationEnabled()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-777-                // Vassalize the losing colony instead of transferring ownership
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-778-                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-779-                int durationHours = TaxConfig.getWarVassalizationDurationHours();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:780:                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-781-                        war.getColony(),
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-782-                        war.getAttacker(),
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-783-                        tributePercent,
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-784-                        durationHours);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-785-                if (vassalized) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-786-                    WARSYSTEM_LOGGER.info("Colony {} has been vassalized by {} for {} hours at {}% tribute",
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-787-                            war.getColony().getName(), war.getAttacker(), durationHours, tributePercent);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-788-                }
--
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1201-        // a meaningful consequence for the loser.
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1202-        if (!net.machiavelli.minecolonytax.permissions.ColonyTierGuard.canTransferOwnership(colony)) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1203-            WARSYSTEM_LOGGER.info("Ownership transfer denied for colony {}: {}",
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1204-                    colony.getID(),
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1205-                    net.machiavelli.minecolonytax.permissions.ColonyTierGuard.getTransferDenialReason(colony));
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1206-            if (TaxConfig.isWarVassalizationEnabled()) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1207-                int tributePercent = TaxConfig.getWarVassalizationTributePercentage();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1208-                int durationHours = TaxConfig.getWarVassalizationDurationHours();
src/main/java/net/machiavelli/minecolonytax\WarSystem.java:1209:                boolean vassalized = net.machiavelli.minecolonytax.vassalization.VassalManager.forceVassalize(
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1210-                        colony, newOwnerUUID, tributePercent, durationHours);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1211-                if (vassalized) {
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1212-                    WARSYSTEM_LOGGER.info("Primary colony {} vassalized by {} instead of transferred ({}% tribute for {}h)",
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1213-                            colony.getName(), newOwnerUUID, tributePercent, durationHours);
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1214-                    WarData war = ACTIVE_WARS.get(colony.getID());
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1215-                    Component msg = Component.literal(colony.getName()
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1216-                            + " is a Primary colony â€” vassalized instead of conquered.")
src/main/java/net/machiavelli/minecolonytax\WarSystem.java-1217-                            .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true));
--
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-283-        }
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-284-        return tribute;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-285-    }
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-286-
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-287-    /**
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-288-     * Forces a colony to become a vassal as a result of war victory, bypassing the
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-289-     * normal proposal/acceptance flow. durationHours=0 means permanent.
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-290-     */
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:291:    public static boolean forceVassalize(IColony vassalColony, UUID overlordUUID, int tributePercent,
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-292-            int durationHours) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-293-        if (vassalColony == null || overlordUUID == null) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java:294:            LOGGER.warn("forceVassalize called with null colony or overlord");
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-295-            return false;
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-296-        }
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-297-
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-298-        int colonyId = vassalColony.getID();
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-299-
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-300-        if (ACTIVE_VASSALS.containsKey(colonyId)) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-301-            if (TaxConfig.isDebugLogging()) {
src/main/java/net/machiavelli/minecolonytax\vassalization\VassalManager.java-302-                LOGGER.info("Colony {} is already a vassal, cannot force vassalize again", vassalColony.getName());
--
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-522-     *    occupations (secondary colonies) must follow the legacy reclaim flow
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-523-     *    or be ended by the standard expiry/cancel paths.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-524-     *  - {@code reclaimerUUID} must match the recorded original owner. Prevents
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-525-     *    arbitrary players from cancelling another player's occupation.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-526-     *  - Uses atomic remove so concurrent calls don't double-fire.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-527-     *
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-528-     * @return true if an occupation was ended, false otherwise
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-529-     */
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:530:    public static boolean reclaimByOriginalOwner(int colonyId, UUID reclaimerUUID) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-531-        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-532-        if (data == null) return false;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-533-        if (data.getMode() != OccupationMode.TAX_ONLY) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:534:            LOGGER.info("reclaimByOriginalOwner refused: occupation on colony {} is mode {} (not TAX_ONLY)",
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-535-                    colonyId, data.getMode());
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-536-            return false;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-537-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-538-        if (reclaimerUUID == null || !reclaimerUUID.toString().equals(data.originalOwnerUUID)) {
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java:539:            LOGGER.info("reclaimByOriginalOwner refused: caller {} is not the recorded original owner {} of colony {}",
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-540-                    reclaimerUUID, data.originalOwnerUUID, colonyId);
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-541-            return false;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-542-        }
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-543-
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-544-        // Atomic remove â€” bail if a concurrent caller already cleared it.
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-545-        if (!ACTIVE_OCCUPATIONS.remove(colonyId, data)) return false;
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-546-        saveData();
src/main/java/net/machiavelli/minecolonytax\occupation\OccupationManager.java-547-
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 12 "public static boolean forceVassalize|overlordUUID.equals|owner" src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 281ms:
161-
162-    public static int revokeRelation(ServerPlayer executor, String overlordNameOrTarget) {
163-        UUID executorId = executor.getUUID();
164-        boolean found = false;
165-        Iterator<Map.Entry<Integer, VassalRelation>> it = ACTIVE_VASSALS.entrySet().iterator();
166-        while (it.hasNext()) {
167-            Map.Entry<Integer, VassalRelation> e = it.next();
168-            VassalRelation rel = e.getValue();
169-            IColony colony = getColonyById(e.getKey());
170-            if (colony == null)
171-                continue;
172-            String overlordName = getPlayerName(rel.overlordUUID);
173:            if (rel.overlordUUID.equals(executorId) || (isPlayerManagerOfColony(executor, colony)
174-                    && overlordName.equalsIgnoreCase(overlordNameOrTarget))) {
175-                it.remove();
176-                saveData();
177-                ServerPlayer overlord = SERVER.getPlayerList().getPlayer(rel.overlordUUID);
178-                String msg = "Vassalization between colony " + colony.getName() + " and player " + overlordName
179-                        + " has been revoked.";
180-                executor.sendSystemMessage(Component.literal("You revoked the vassalization."));
181-                if (overlord != null) {
182-                    overlord.sendSystemMessage(Component.literal(msg));
183-                } else {
184-                    queueMessage(rel.overlordUUID, Component.literal(msg));
185-                }
--
213-                        Component.literal("Â§c[VASSAL STATUS] Last tribute: " + rel.lastTribute + " " + currencyName));
214-                break;
215-            }
216-        }
217-
218-        if (!isVassal) {
219-            player.sendSystemMessage(Component.literal("Â§a[VASSAL STATUS] Your colony is independent"));
220-        }
221-
222-        boolean hasVassals = false;
223-        player.sendSystemMessage(Component.literal("Â§e--- Your Vassals ---"));
224-        for (VassalRelation rel : ACTIVE_VASSALS.values()) {
225:            if (rel.overlordUUID.equals(id)) {
226-                IColony colony = getColonyById(rel.colonyId);
227-                if (colony != null) {
228-                    long minutes = (System.currentTimeMillis() - rel.lastPayment) / 60000;
229-                    player.sendSystemMessage(
230-                            Component.literal("Â§a- " + colony.getName() + ": " + rel.percent + "% tribute rate"));
231-                    player.sendSystemMessage(Component.literal("  Â§7Last payment: " + minutes + "m ago, Amount: "
232-                            + rel.lastTribute + " " + getCurrencyName()));
233-                    hasVassals = true;
234-                }
235-            }
236-        }
237-        if (!hasVassals) {
--
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
294-            LOGGER.warn("forceVassalize called with null colony or overlord");
295-            return false;
296-        }
297-
298-        int colonyId = vassalColony.getID();
299-
300-        if (ACTIVE_VASSALS.containsKey(colonyId)) {
301-            if (TaxConfig.isDebugLogging()) {
302-                LOGGER.info("Colony {} is already a vassal, cannot force vassalize again", vassalColony.getName());
303-            }
--
393-        return rank != null && rank.isColonyManager();
394-    }
395-
396-    private static void sendToColonyManagers(IColony colony, Component message) {
397-        if (colony == null)
398-            return;
399-        IPermissions perms = colony.getPermissions();
400-
401-        // Build robust recipient set with multiple fallbacks
402-        java.util.Set<java.util.UUID> recipients = new java.util.HashSet<>();
403-
404-        // Owner
405:        java.util.UUID ownerId = perms.getOwner();
406:        if (ownerId != null)
407:            recipients.add(ownerId);
408-
409-        // Officers (if rank exists)
410-        try {
411-            var officerRank = perms.getRankOfficer();
412-            if (officerRank != null) {
413-                for (ColonyPlayer cp : perms.getPlayersByRank(officerRank)) {
414-                    if (cp != null && cp.getID() != null)
415-                        recipients.add(cp.getID());
416-                }
417-            }
418-        } catch (Throwable ignored) {
419-        }
--
435-
436-        // Fallback: all known colony members from permissions map if still empty
437-        if (recipients.isEmpty()) {
438-            try {
439-                var playersMap = perms.getPlayers();
440-                if (playersMap != null) {
441-                    recipients.addAll(playersMap.keySet());
442-                }
443-            } catch (Throwable ignored) {
444-            }
445-        }
446-
447:        // FTB Teams extension: also notify members of the owner's FTB party (if
448-        // installed)
449-        try {
450:            if (ownerId != null && WarSystem.FTB_TEAMS_INSTALLED && WarSystem.FTB_TEAM_MANAGER != null) {
451:                var teamOpt = WarSystem.FTB_TEAM_MANAGER.getTeamForPlayerID(ownerId);
452-                if (teamOpt.isPresent()) {
453-                    var team = teamOpt.get();
454-                    // For party teams, notify direct members
455-                    try {
456-                        var members = team.getMembers();
457-                        if (members != null)
458-                            recipients.addAll(members);
459-                    } catch (Throwable ignoredInner) {
460-                    }
461-                }
462-            }
463-        } catch (Throwable ignored) {
--
617-        VassalRelation rel = ACTIVE_VASSALS.get(colonyId);
618-        return rel != null ? rel.overlordUUID : null;
619-    }
620-
621-    public static int getVassalTributeRate(int colonyId) {
622-        VassalRelation rel = ACTIVE_VASSALS.get(colonyId);
623-        return rel != null ? rel.percent : 0;
624-    }
625-
626-    public static int countVassalsForPlayer(UUID playerId) {
627-        int count = 0;
628-        for (VassalRelation rel : ACTIVE_VASSALS.values()) {
629:            if (rel.overlordUUID.equals(playerId)) {
630-                count++;
631-            }
632-        }
633-        return count;
634-    }
635-
636-    public static boolean removeVassalRelation(int vassalColonyId) {
637-        VassalRelation relation = ACTIVE_VASSALS.remove(vassalColonyId);
638-        if (relation != null) {
639-            saveData();
640-            if (TaxConfig.isNormalLogging()) {
641-                LOGGER.info("Vassalization ended for colony " + vassalColonyId);
--
679-        }
680-
681-        return success;
682-    }
683-
684-    public static void addOfflineMessage(UUID playerId, Component message) {
685-        OFFLINE_MESSAGES.computeIfAbsent(playerId, k -> new ArrayList<>()).add(message);
686-    }
687-
688-    public static List<VassalIncomeData> getVassalIncomeForPlayer(UUID overlordId) {
689-        List<VassalIncomeData> vassalIncomes = new ArrayList<>();
690-        for (VassalRelation rel : ACTIVE_VASSALS.values()) {
691:            if (rel.overlordUUID.equals(overlordId)) {
692-                IColony colony = getColonyById(rel.colonyId);
693-                if (colony != null) {
694-                    int currentTaxBalance = TaxManager.getStoredTaxForColony(colony);
695-                    int tributeOwed = (int) (currentTaxBalance * rel.percent / 100.0);
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
712-    public static int claimVassalTribute(UUID overlordId, int vassalColonyId) {
713-        VassalRelation rel = ACTIVE_VASSALS.get(vassalColonyId);
714:        if (rel == null || !rel.overlordUUID.equals(overlordId)) {
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
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command "rg -n -C 30 \"int colonyId = vassalColony.getID\\(\\);\" src/main/java/net/machiavelli/minecolonytax/vassalization/VassalManager.java" in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 285ms:
268-
269-        IColony overlordColony = getPrimaryColonyOfPlayer(rel.overlordUUID);
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
291-    public static boolean forceVassalize(IColony vassalColony, UUID overlordUUID, int tributePercent,
292-            int durationHours) {
293-        if (vassalColony == null || overlordUUID == null) {
294-            LOGGER.warn("forceVassalize called with null colony or overlord");
295-            return false;
296-        }
297-
298:        int colonyId = vassalColony.getID();
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
324-        IColony overlordColony = getPrimaryColonyOfPlayer(overlordUUID);
325-        String overlordColonyName = overlordColony != null ? overlordColony.getName() : "Unknown";
326-
327-        String durationMsg;
328-        if (durationHours <= 0) {
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur f〉 
Kerntypen unterst》zt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
The counter-besiege handoff is guarded correctly inside `OccupationManager`, but the caller’s follow-on behavior is riskier than the comment says: `forceVassalize` does not appear to reject self-vassalization.
exec
"C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe" -Command 'rg -n -C 10 "startBesiege|isColonyBesieged|ACTIVE_OCCUPATIONS|getActiveOccupations|isOriginalOwner|can.*besiege|occupied" src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java' in C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
 succeeded in 462ms:
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-171-                LOGGER.error("Error ticking besiege raid for colony {}", entry.getKey(), e);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-172-            }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-173-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-174-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-175-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-176-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-177-    /**
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-178-     * Start a besiege raid. Validates all preconditions.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-179-     * Returns true if the raid started successfully.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-180-     */
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:181:    public static boolean startBesiege(IColony colony, ServerPlayer besieger) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-182-        if (!TaxConfig.isBesiegeSystemEnabled()) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-183-            besieger.sendSystemMessage(Component.literal("The besiege system is disabled on this server.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-184-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-185-            return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-186-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-187-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-188-        UUID besiegerUUID = besieger.getUUID();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-189-        int colonyId = colony.getID();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-190-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-191-        // 1. Must own at least one colony themselves
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-192-        IColony besiegerColony = getPrimaryColonyOfPlayer(besiegerUUID);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-193-        if (besiegerColony == null) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-194-            besieger.sendSystemMessage(Component.literal("You must own a colony to besiege another.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-195-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-196-            return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-197-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-198-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-199-        // 2. Cannot besiege own colony
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-200-        if (colony.getPermissions().getOwner() != null
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-201-                && colony.getPermissions().getOwner().equals(besiegerUUID)) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:202:            besieger.sendSystemMessage(Component.literal("You cannot besiege your own colony.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-203-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-204-            return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-205-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-206-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-207-        // 3. Primary colonies CAN now be besieged. Outcome routes through
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-208-        // OccupationManager in TAX_ONLY mode (deed never moves; auto-reclaim after
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-209-        // PrimaryColonyTaxOccupationDays). Secondaries continue to use the legacy
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-210-        // TRANSFER_PENDING flow. See ColonyTierGuard + OccupationManager.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-211-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-212-        // 4. Solo rule: this besieger may not already have an active raid elsewhere.
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-272-        BesiegeOccupationData occ = OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-273-        if (occ == null) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-274-            reclaimingPlayer.sendSystemMessage(Component.literal("This colony is not under besiege occupation.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-275-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-276-            return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-277-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-278-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-279-        UUID playerUUID = reclaimingPlayer.getUUID();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-280-        if (!occ.formerOwnerUUID.equals(playerUUID)) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-281-            reclaimingPlayer.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:282:                    "Only the former owner can reclaim this colony via besiege.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-283-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-284-            return false;
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-285-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-286-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-287-        // Solo rule: this player may not already have an active raid.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-288-        if (ACTIVE_RAIDS.containsKey(playerUUID)) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-289-            reclaimingPlayer.sendSystemMessage(Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-290-                    "You already have an active besiege/reclaim raid.")
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-291-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-292-            return false;
--
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-890-            raid.bossEvent.setProgress(progress);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-891-            raid.bossEvent.setName(Component.literal(
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-892-                    String.format("Besiege: %s | Defenders: %d | %02d:%02d",
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-893-                            colony.getName(), aliveDefenders, mm, ss)));
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-894-        } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-895-            LOGGER.warn("Failed to update besiege boss bar", e);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-896-        }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-897-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-898-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-899-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java:900:    public static boolean isColonyBesieged(int colonyId) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-901-        return OCCUPATIONS.containsKey(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-902-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-903-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-904-    public static boolean isActiveRaidOnColony(int colonyId) {
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-905-        // Hot path â€” O(1) via COLONY_RAID_INDEX.
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-906-        Set<UUID> set = COLONY_RAID_INDEX.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-907-        return set != null && !set.isEmpty();
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-908-    }
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-909-
src/main/java/net/machiavelli/minecolonytax/besiege/BesiegeManager.java-910-    /** All currently active besiege raids targeting this colony. O(matches). */
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-24-import java.nio.file.Files;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-25-import java.nio.file.Path;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-26-import java.nio.file.Paths;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-27-import java.util.*;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-28-import java.util.concurrent.ConcurrentHashMap;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-29-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-30-/**
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-31- * Manages the Colony Occupation system.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-32- *
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-33- * Constraints:
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:34: * - The occupier may collect taxes but cannot interact with occupied colony
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-35- *   buildings or items.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-36- * - The original owner has a configurable window (OccupationDurationDays) to
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-37- *   wage a reclamation war.  If they do not, full ownership transfers permanently
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-38- *   to the occupier when the occupation expires.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-39- */
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-40-public class OccupationManager {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-41-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-42-    private static final Logger LOGGER = LogManager.getLogger(OccupationManager.class);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-43-    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-44-    private static final String STORAGE_FILE = "config/warntax/occupations.json";
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-45-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:46:    private static final Map<Integer, OccupationData> ACTIVE_OCCUPATIONS = new ConcurrentHashMap<>();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-47-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-48-    private static MinecraftServer serverInstance;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-49-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-50-    /**
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-51-     * How an occupation resolves when its timer expires.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-52-     *
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-53-     * Primary colonies always run in TAX_ONLY (deed never moves), unless
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-54-     * EnablePrimaryColonyTransfer is on. Secondaries run in TRANSFER_PENDING.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-55-     */
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-56-    public enum OccupationMode {
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-128-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-129-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-130-    private static class OccupationSaveData {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-131-        public List<OccupationData> occupations = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-132-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-133-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-134-    public static void initialize(MinecraftServer server) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-135-        serverInstance = server;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-136-        loadData();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-137-        if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:138:            LOGGER.info("OccupationManager initialized with {} active occupations", ACTIVE_OCCUPATIONS.size());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-139-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-140-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-141-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-142-    public static void shutdown() {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-143-        saveData();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-144-        if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-145-            LOGGER.info("OccupationManager shutdown complete");
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-146-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-147-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-148-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-149-    public static void startOccupation(IColony colony, UUID occupierUUID, IColony attackerColony) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-150-        if (colony == null || occupierUUID == null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-151-            LOGGER.warn("startOccupation called with null colony or occupier");
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-152-            return;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-153-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-154-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-155-        int colonyId = colony.getID();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-156-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-157-        // Don't double-occupy
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:158:        if (ACTIVE_OCCUPATIONS.containsKey(colonyId)) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:159:            LOGGER.warn("Colony {} is already occupied, cannot start new occupation", colony.getName());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-160-            return;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-161-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-162-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-163-        UUID originalOwner = colony.getPermissions().getOwner();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-164-        int occupierColonyId = attackerColony != null ? attackerColony.getID() : -1;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-165-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-166-        // Decide mode by colony tier. Primary colonies are tax-only by default;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-167-        // secondaries follow the legacy transfer-on-expiry flow.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-168-        // EnablePrimaryColonyTransfer lets server owners opt back into the legacy
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-169-        // behavior for primaries too.
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-192-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-193-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-194-        long now = System.currentTimeMillis();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-195-        long expirationTime = now + (durationDays * 24L * 60L * 60L * 1000L);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-196-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-197-        OccupationData data = new OccupationData(
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-198-                colonyId, occupierUUID, originalOwner,
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-199-                occupierColonyId, colony.getName(),
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-200-                now, expirationTime, mode
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-201-        );
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:202:        ACTIVE_OCCUPATIONS.put(colonyId, data);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-203-        saveData();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-204-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-205-        if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-206-            LOGGER.info("Colony {} is now OCCUPIED by {} for {} days [mode={}]",
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-207-                    colony.getName(), occupierUUID, durationDays, mode);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-208-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-209-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-210-        final boolean isTaxOnly = mode == OccupationMode.TAX_ONLY;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-211-        final String expiryConsequence = isTaxOnly
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-212-                ? "If not reclaimed in time, the occupation lapses and taxes route back to the owner."
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-239-            }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-240-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-241-            // Notify original owner
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-242-            ServerPlayer owner = server.getPlayerList().getPlayer(originalOwner);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-243-            if (owner != null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-244-                Component ownerMsg = Component.literal(isTaxOnly
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-245-                                ? "YOUR PRIMARY COLONY HAS BEEN TAX-OCCUPIED"
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-246-                                : "YOUR COLONY HAS BEEN OCCUPIED")
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-247-                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-248-                        .append(Component.literal("\n"))
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:249:                        .append(Component.literal("Your colony " + colony.getName() + " has been occupied!")
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-250-                                .withStyle(ChatFormatting.RED))
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-251-                        .append(Component.literal("\n"))
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-252-                        .append(Component.literal("The occupier will collect taxes from your colony.")
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-253-                                .withStyle(ChatFormatting.YELLOW))
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-254-                        .append(Component.literal("\n"))
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-255-                        .append(Component.literal("You have " + durationDays + " days to wage a reclamation war with /wnt wagewar " + colonyId)
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-256-                                .withStyle(ChatFormatting.GREEN))
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-257-                        .append(Component.literal("\n"))
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-258-                        .append(Component.literal(ownerStakes)
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-259-                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-260-                owner.sendSystemMessage(ownerMsg);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-261-            }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-262-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-263-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-264-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-265-    public static boolean isOccupied(int colonyId) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:266:        return ACTIVE_OCCUPATIONS.containsKey(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-267-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-268-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-269-    public static OccupationData getOccupation(int colonyId) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:270:        return ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-271-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-272-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:273:    public static Map<Integer, OccupationData> getActiveOccupations() {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:274:        return Collections.unmodifiableMap(ACTIVE_OCCUPATIONS);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-275-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-276-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-277-    public static boolean isOccupier(UUID playerUUID, int colonyId) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:278:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-279-        return data != null && data.occupierUUID.equals(playerUUID.toString());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-280-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-281-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-282-    public static List<OccupationData> getOccupiedByPlayer(UUID playerUUID) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-283-        List<OccupationData> result = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-284-        String uuid = playerUUID.toString();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:285:        for (OccupationData data : ACTIVE_OCCUPATIONS.values()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-286-            if (data.occupierUUID.equals(uuid)) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-287-                result.add(data);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-288-            }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-289-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-290-        return result;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-291-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-292-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-293-    public static int collectOccupationTax(int colonyId, ServerPlayer occupier) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:294:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-295-        if (data == null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:296:            occupier.sendSystemMessage(Component.literal("This colony is not occupied by you.")
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-297-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-298-            return 0;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-299-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-300-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-301-        if (!data.occupierUUID.equals(occupier.getUUID().toString())) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-302-            occupier.sendSystemMessage(Component.literal("You are not the occupier of this colony.")
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-303-                    .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-304-            return 0;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-305-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-306-
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-343-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-344-        if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-345-            LOGGER.info("Occupier {} collected {} occupation tax from colony {}",
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-346-                    occupier.getName().getString(), taxToCollect, colony.getName());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-347-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-348-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-349-        return taxToCollect;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-350-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-351-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-352-    public static int processAutomaticOccupationTax(int colonyId, int generatedTax) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:353:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-354-        if (data == null || generatedTax <= 0) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-355-            return 0;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-356-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-357-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-358-        double occupationTaxRate = TaxConfig.getOccupationTaxPercentage();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-359-        int diverted = (int) (generatedTax * occupationTaxRate);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-360-        if (diverted <= 0) return 0;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-361-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-362-        if (data.occupierColonyId > 0) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-363-            IColony occupierColony = findColonyById(data.occupierColonyId);
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-368-                            diverted, colonyId, data.occupierColonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-369-                }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-370-            }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-371-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-372-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-373-        return diverted;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-374-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-375-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-376-    /** Called when the original owner declares war against the occupier. */
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-377-    public static void markReclamationAttempted(int colonyId) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:378:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-379-        if (data != null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-380-            data.reclamationAttempted = true;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-381-            saveData();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-382-            if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:383:                LOGGER.info("Reclamation attempt recorded for occupied colony {}", colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-384-            }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-385-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-386-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-387-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-388-    public static void endOccupation(int colonyId, String reason) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:389:        OccupationData data = ACTIVE_OCCUPATIONS.remove(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-390-        if (data == null) return;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-391-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-392-        saveData();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-393-        if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-394-            LOGGER.info("Occupation ended for colony {} ({}): {}", data.colonyName, colonyId, reason);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-395-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-396-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-397-        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-398-        if (server != null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-399-            // Notify occupier
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-407-            ServerPlayer owner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-408-            if (owner != null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-409-                owner.sendSystemMessage(Component.literal("The occupation of " + data.colonyName + " has ended: " + reason)
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-410-                        .withStyle(ChatFormatting.GREEN));
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-411-            }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-412-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-413-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-414-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-415-    /** Periodic check â€” transfers full ownership to the occupier when the occupation expires. Called via TickScheduler. */
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-416-    public static void checkExpiredOccupations() {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:417:        if (ACTIVE_OCCUPATIONS.isEmpty()) return;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-418-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-419-        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-420-        if (server == null) return;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-421-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-422-        List<Integer> toTransfer = new ArrayList<>();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-423-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:424:        for (Map.Entry<Integer, OccupationData> entry : ACTIVE_OCCUPATIONS.entrySet()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-425-            OccupationData data = entry.getValue();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-426-            if (data.isExpired()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-427-                // Transfer in both cases:
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-428-                // - reclamationAttempted=false: deadline passed with no attempt
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-429-                // - reclamationAttempted=true:  owner tried to reclaim but failed (lost the war),
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-430-                //   occupation expired while still in effect â†’ occupier wins
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-431-                toTransfer.add(entry.getKey());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-432-            }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-433-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-434-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-435-        for (int colonyId : toTransfer) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:436:            OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-437-            if (data == null) continue;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-438-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-439-            IColony colony = findColonyById(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-440-            if (colony == null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-441-                LOGGER.warn("Occupied colony {} no longer exists, removing occupation", colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:442:                ACTIVE_OCCUPATIONS.remove(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-443-                continue;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-444-            }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-445-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-446-            UUID occupierUUID = data.getOccupierUUID();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-447-            OccupationMode mode = data.getMode();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-448-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-449-            if (mode == OccupationMode.TAX_ONLY) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-450-                // Primary colony auto-reclaim â€” deed never moves, taxes simply revert
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-451-                // to the original owner. Friendly notification on both sides.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-452-                if (TaxConfig.isNormalLogging()) {
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-498-                    ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-499-                    if (originalOwner != null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-500-                        originalOwner.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-501-                                Component.literal("The occupation of " + colony.getName()
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-502-                                                + " has lapsed â€” the deed remains with you.")
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-503-                                        .withStyle(ChatFormatting.GREEN));
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-504-                    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-505-                }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-506-            }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-507-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:508:            ACTIVE_OCCUPATIONS.remove(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-509-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-510-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-511-        if (!toTransfer.isEmpty()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-512-            saveData();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-513-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-514-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-515-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-516-    /**
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-517-     * Manually end a tax-only occupation early â€” called when the owner successfully
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-518-     * mounts a counter-besiege. Restores everything to pre-occupation state.
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-521-     *  - Only TAX_ONLY occupations can be reclaimed this way. TRANSFER_PENDING
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-522-     *    occupations (secondary colonies) must follow the legacy reclaim flow
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-523-     *    or be ended by the standard expiry/cancel paths.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-524-     *  - {@code reclaimerUUID} must match the recorded original owner. Prevents
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-525-     *    arbitrary players from cancelling another player's occupation.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-526-     *  - Uses atomic remove so concurrent calls don't double-fire.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-527-     *
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-528-     * @return true if an occupation was ended, false otherwise
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-529-     */
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-530-    public static boolean reclaimByOriginalOwner(int colonyId, UUID reclaimerUUID) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:531:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-532-        if (data == null) return false;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-533-        if (data.getMode() != OccupationMode.TAX_ONLY) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-534-            LOGGER.info("reclaimByOriginalOwner refused: occupation on colony {} is mode {} (not TAX_ONLY)",
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-535-                    colonyId, data.getMode());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-536-            return false;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-537-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-538-        if (reclaimerUUID == null || !reclaimerUUID.toString().equals(data.originalOwnerUUID)) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-539-            LOGGER.info("reclaimByOriginalOwner refused: caller {} is not the recorded original owner {} of colony {}",
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-540-                    reclaimerUUID, data.originalOwnerUUID, colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-541-            return false;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-542-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-543-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-544-        // Atomic remove â€” bail if a concurrent caller already cleared it.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:545:        if (!ACTIVE_OCCUPATIONS.remove(colonyId, data)) return false;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-546-        saveData();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-547-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-548-        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-549-        if (server != null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-550-            ServerPlayer originalOwner = server.getPlayerList().getPlayer(data.getOriginalOwnerUUID());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-551-            if (originalOwner != null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-552-                originalOwner.sendSystemMessage(
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-553-                        Component.literal("You have successfully reclaimed " + data.colonyName + "!")
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-554-                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-555-            }
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-559-                        Component.literal(data.colonyName + " has been reclaimed by its original owner â€” your hold has been broken.")
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-560-                                .withStyle(ChatFormatting.RED));
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-561-            }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-562-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-563-        if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-564-            LOGGER.info("Occupation reclaimed for colony {} ({}) by original owner", data.colonyName, colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-565-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-566-        return true;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-567-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-568-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:569:    /** Returns true if the occupier should be blocked from interacting with the occupied colony's buildings and items. */
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-570-    public static boolean shouldBlockInteraction(UUID playerUUID, int colonyId) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:571:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-572-        if (data == null) return false;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-573-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:574:        // Block the occupier from interacting with the occupied colony's items
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-575-        return data.occupierUUID.equals(playerUUID.toString());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-576-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-577-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:578:    public static boolean isOriginalOwner(UUID playerUUID, int colonyId) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:579:        OccupationData data = ACTIVE_OCCUPATIONS.get(colonyId);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-580-        if (data == null) return false;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-581-        return data.originalOwnerUUID.equals(playerUUID.toString());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-582-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-583-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-584-    public static void saveData() {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-585-        // Snapshot on the calling (server) thread; the worker only touches
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-586-        // a fresh ArrayList so it can't ConcurrentModification on the live map.
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-587-        final OccupationSaveData saveData = new OccupationSaveData();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:588:        saveData.occupations = new ArrayList<>(ACTIVE_OCCUPATIONS.values());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-589-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-590-        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("occupations", () -> {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-591-            try {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-592-                Path dir = Paths.get("config/warntax");
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-593-                if (!Files.exists(dir)) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-594-                    Files.createDirectories(dir);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-595-                }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-596-                try (Writer writer = new FileWriter(STORAGE_FILE)) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-597-                    GSON.toJson(saveData, writer);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-598-                }
--
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-603-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-604-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-605-    public static void loadData() {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-606-        try {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-607-            Path path = Paths.get(STORAGE_FILE);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-608-            if (!Files.exists(path)) return;
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-609-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-610-            try (Reader reader = new FileReader(STORAGE_FILE)) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-611-                OccupationSaveData saveData = GSON.fromJson(reader, OccupationSaveData.class);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-612-                if (saveData != null && saveData.occupations != null) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:613:                    ACTIVE_OCCUPATIONS.clear();
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-614-                    for (OccupationData data : saveData.occupations) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:615:                        ACTIVE_OCCUPATIONS.put(data.colonyId, data);
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-616-                    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-617-                    if (TaxConfig.isNormalLogging()) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java:618:                        LOGGER.info("Loaded {} occupations from disk", ACTIVE_OCCUPATIONS.size());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-619-                    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-620-                }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-621-            }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-622-        } catch (Exception e) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-623-            LOGGER.error("Failed to load occupation data: {}", e.getMessage());
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-624-        }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-625-    }
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-626-
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-627-    private static IColony findColonyById(int colonyId) {
src/main/java/net/machiavelli/minecolonytax/occupation/OccupationManager.java-628-        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
Die Eigenschaft kann nicht festgelegt werden. Das Festlegen der Eigenschaft wird in diesem Sprachmodus nur fr 
Kerntypen untersttzt.
In Zeile:1 Zeichen:1
+ [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : PropertySetterNotSupportedInConstrainedLanguage
 

codex
STATUS REWORK

- `BesiegeManager.tick`: the index is not lock-step. Colony-null and left-radius paths call `cleanupRaid(raid, false)` then `it.remove()` (`BesiegeManager.java:114-115`, `148-150`), bypassing `removeFromColonyIndex`, leaving stale colony entries. Timeout/victory/start-failure paths look okay.
- Entity cancellation: yes for `PlayerInteractEvent.EntityInteract`; Forge 1.20.x marks it cancelable and uses `cancellationResult` when canceled, and `setCancellationResult` applies to `EntityInteract`/`EntityInteractSpecific`. But this handler only covers `EntityInteract`; interactions consumed by `EntityInteractSpecific` can bypass it. Source: [Forge PlayerInteractEvent.java](https://cocalc.com/github/MinecraftForge/MinecraftForge/blob/1.20.x/src/main/java/net/minecraftforge/event/entity/player/PlayerInteractEvent.java).
- Container fallback is null-safe: `be instanceof Container` handles null, and block interaction positions should already be loaded server-side. It will not catch modded storage that exposes Forge item-handler capability without implementing `Container`.
- Explosion't compat is safe under normal Forge semantics: mods do not unload mid-session, so cached `ModList.isLoaded("explosiont")` is stable; config remains live. Removing the mod between restarts recomputes in a new JVM.
- Counter-besiege guard no-ops correctly when the besieger is not the recorded original owner, but successful reclaim still falls through to `VassalManager.forceVassalize`; local source shows no self-vassalization guard, so this can create a self-vassal relation.

Fixes: centralize raid removal and update both maps on every path; return early from `completeBesiegeVictory` when `reclaimed == true`; add a shared handler for `EntityInteractSpecific` and optionally `ForgeCapabilities.ITEM_HANDLER`.
tokens used
130.242
STATUS REWORK

- `BesiegeManager.tick`: the index is not lock-step. Colony-null and left-radius paths call `cleanupRaid(raid, false)` then `it.remove()` (`BesiegeManager.java:114-115`, `148-150`), bypassing `removeFromColonyIndex`, leaving stale colony entries. Timeout/victory/start-failure paths look okay.
- Entity cancellation: yes for `PlayerInteractEvent.EntityInteract`; Forge 1.20.x marks it cancelable and uses `cancellationResult` when canceled, and `setCancellationResult` applies to `EntityInteract`/`EntityInteractSpecific`. But this handler only covers `EntityInteract`; interactions consumed by `EntityInteractSpecific` can bypass it. Source: [Forge PlayerInteractEvent.java](https://cocalc.com/github/MinecraftForge/MinecraftForge/blob/1.20.x/src/main/java/net/minecraftforge/event/entity/player/PlayerInteractEvent.java).
- Container fallback is null-safe: `be instanceof Container` handles null, and block interaction positions should already be loaded server-side. It will not catch modded storage that exposes Forge item-handler capability without implementing `Container`.
- Explosion't compat is safe under normal Forge semantics: mods do not unload mid-session, so cached `ModList.isLoaded("explosiont")` is stable; config remains live. Removing the mod between restarts recomputes in a new JVM.
- Counter-besiege guard no-ops correctly when the besieger is not the recorded original owner, but successful reclaim still falls through to `VassalManager.forceVassalize`; local source shows no self-vassalization guard, so this can create a self-vassal relation.

Fixes: centralize raid removal and update both maps on every path; return early from `completeBesiegeVictory` when `reclaimed == true`; add a shared handler for `EntityInteractSpecific` and optionally `ForgeCapabilities.ITEM_HANDLER`.
