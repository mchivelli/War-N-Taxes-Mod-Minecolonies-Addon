## STEP 6 — Besiege-specific container deny

BlockInteractionFilterHandler: during besiege only, deny right-click USE on container blocks (chest/barrel/shulker/ender/hopper/dispenser/chiseled-bookshelf). War keeps current permissive profile. Villager trade entity interaction is NOT yet wired (would need a separate PlayerInteractEvent.EntityInteract handler) — that piece is deferred.

```diff
diff --git a/src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java b/src/main/java/net/machiavelli/minecolonytax/event/BlockInteractionFilterHandler.java
index 99895c1..0fcdf8e 100644
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
@@ -133,6 +165,18 @@ public class BlockInteractionFilterHandler {
             return FilterResult.deny("Block breaking is not allowed during raids and wars!", null);
         }
 
+        // Siege SMP rule: during a besiege, deny right-click USE on container-style blocks
+        // (chests, barrels, shulkers, etc.). Combat-only — no looting. Doors/levers/buttons
+        // still pass through via the existing whitelist/blacklist below.
+        if (type == InteractionType.USE && isBesiegeActiveForPlayer(player, colony.getID())) {
+            if (isContainerBlock(block)) {
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
@@ -192,7 +236,7 @@ public class BlockInteractionFilterHandler {
         UUID playerUUID = player.getUUID();
         int colonyId = colony.getID();
 
-        if (TaxConfig.isBlockFilterRaidsEnabled()) {
+        if (TaxConfig.isBlockFilterRaidsEnabled() && !RaidManager.getActiveRaids().isEmpty()) {
             net.machiavelli.minecolonytax.raid.ActiveRaidData raid =
                 RaidManager.getActiveRaidForPlayer(playerUUID);
             if (raid != null && raid.getColony() != null && raid.getColony().getID() == colonyId) {
@@ -200,7 +244,7 @@ public class BlockInteractionFilterHandler {
             }
         }
 
-        if (TaxConfig.isBlockFilterWarsEnabled()) {
+        if (TaxConfig.isBlockFilterWarsEnabled() && !WarSystem.ACTIVE_WARS.isEmpty()) {
             for (WarData warData : WarSystem.ACTIVE_WARS.values()) {
                 boolean playerIsParticipant =
                     (warData.getAttackerLives() != null && warData.getAttackerLives().containsKey(playerUUID))
@@ -219,6 +263,35 @@ public class BlockInteractionFilterHandler {
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
+    /** True if this block exposes a Container — chests, barrels, shulkers, hoppers, dispensers, etc. */
+    private static boolean isContainerBlock(Block block) {
+        // Block-level instanceof is the broadest catch; covers vanilla and modded chest variants.
+        if (block instanceof net.minecraft.world.level.block.ChestBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.BarrelBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.ShulkerBoxBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.EnderChestBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.HopperBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.DispenserBlock) return true;
+        if (block instanceof net.minecraft.world.level.block.ChiseledBookShelfBlock) return true;
+        return false;
+    }
+
     private static void applyFilterResult(FilterResult result, Event event, ServerPlayer player) {
         switch (result.action) {
             case DENY:
```
