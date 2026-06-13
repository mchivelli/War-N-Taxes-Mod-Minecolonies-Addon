package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.UUID;

/**
 * Handles block interaction filtering during raids and wars.
 * 
 * SECURITY ARCHITECTURE:
 * - HIGHEST Priority: Runs before other protection handlers
 * - Blacklist: Blocks are completely protected (no interaction allowed)
 * - Whitelist: Blocks are explicitly allowed (overrides restrictions)
 * - Default: Falls through to existing protection systems
 * 
 * PRIORITY ORDER:
 * 1. Feature disabled check (pass through)
 * 2. Blacklist check (DENY if matched - highest priority)
 * 3. Whitelist check (ALLOW if matched)
 * 4. Fall through to existing protection systems
 */
@EventBusSubscriber
public class BlockInteractionFilterHandler {
    
    private static final Logger LOGGER = LogManager.getLogger(BlockInteractionFilterHandler.class);
    
    /**
     * Handle block breaking with filter enforcement.
     * Uses HIGHEST priority to override all other protection systems.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        
        FilterResult result = checkBlockInteraction(
            player,
            event.getPos(),
            (Level) event.getLevel(),
            event.getState().getBlock(),
            InteractionType.BREAK
        );
        
        applyFilterResult(result, event, player);
    }
    
    /**
     * Handle block placement with filter enforcement.
     * Uses HIGHEST priority to override all other protection systems.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        FilterResult result = checkBlockInteraction(
            player,
            event.getPos(),
            (Level) event.getLevel(),
            event.getPlacedBlock().getBlock(),
            InteractionType.PLACE
        );
        
        applyFilterResult(result, event, player);
    }
    
    /**
     * Handle block right-click (use) with filter enforcement.
     * Uses HIGHEST priority to override all other protection systems.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        
        FilterResult result = checkBlockInteraction(
            player,
            event.getPos(),
            (Level) event.getLevel(),
            block,
            InteractionType.USE
        );
        
        applyFilterResult(result, event, player);
    }
    
    /**
     * Core filtering logic that checks blacklist/whitelist during active raids/wars.
     * 
     * @param player The player attempting the interaction
     * @param pos The block position
     * @param level The world level
     * @param block The block being interacted with
     * @param type The type of interaction
     * @return FilterResult indicating whether to allow, deny, or pass through
     */
    private static FilterResult checkBlockInteraction(
            ServerPlayer player,
            BlockPos pos,
            Level level,
            Block block,
            InteractionType type) {
        
        // Step 1: Check if filter system is enabled
        if (!TaxConfig.isBlockInteractionFilterEnabled()) {
            return FilterResult.PASS_THROUGH;
        }
        
        // Step 2: Check if player is in a colony
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null) {
            return FilterResult.PASS_THROUGH; // No colony, no filtering
        }

        // Step 2b: Besiege-occupation (vassal phase) lockout of the FORMER OWNER — opt-in.
        // By default the original owner keeps access while vassalized (only tax tribute is
        // siphoned); set VassalLockOutFormerOwner=true to lock them out until they reclaim.
        if (TaxConfig.isVassalLockOutFormerOwnerEnabled()
                && TaxConfig.isBesiegeSystemEnabled()
                && net.machiavelli.minecolonytax.besiege.BesiegeManager.shouldBlockInteraction(
                        player.getUUID(), colony.getID())) {
            return FilterResult.deny(
                "This colony is under besiege occupation. Use /wnt besiege " + colony.getName() + " to reclaim it.",
                BuiltInRegistries.BLOCK.getKey(block).toString());
        }

        // Step 2c: Siege SMP — during an ACTIVE besiege, deny opening containers (combat-only,
        // no looting) unless BesiegeAllowChestAccess is enabled.
        if (type == InteractionType.USE && !TaxConfig.isBesiegeChestAccessAllowed()
                && isBesiegeActiveForPlayer(player, colony.getID())
                && isContainerBlock(block, level, pos)) {
            return FilterResult.deny("You cannot loot containers during a besiege!",
                BuiltInRegistries.BLOCK.getKey(block).toString());
        }

        // Step 3: Check if raid or war is active for this situation
        boolean isActiveRaidOrWar = isPlayerInActiveRaidOrWar(player, colony);
        if (!isActiveRaidOrWar) {
            return FilterResult.PASS_THROUGH; // No active conflict, no filtering
        }
        
        // Step 4: Get block ID for checking
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (blockId == null) {
            LOGGER.warn("Could not get registry key for block: {}", block);
            return FilterResult.PASS_THROUGH;
        }
        
        String blockIdString = blockId.toString();
        
        // Step 5: Check BLACKLIST (highest priority - always deny)
        Set<String> blacklist = TaxConfig.getBlockInteractionBlacklist();
        
        // Check exact block match
        if (blacklist.contains(blockIdString)) {
            LOGGER.debug("🚫 BLACKLIST DENIED: Player {} attempted {} on blacklisted block {} at {}", 
                player.getName().getString(), type, blockIdString, pos);
            return FilterResult.deny("This block is protected during conflicts!", blockIdString);
        }
        
        // Check mod-level match (entries starting with #)
        for (String entry : blacklist) {
            if (entry.startsWith("#")) {
                String modId = entry.substring(1); // Remove # prefix
                if (blockIdString.startsWith(modId + ":")) {
                    LOGGER.debug("🚫 BLACKLIST DENIED (MOD): Player {} attempted {} on block {} (mod {} is blacklisted) at {}", 
                        player.getName().getString(), type, blockIdString, modId, pos);
                    return FilterResult.deny("Blocks from this mod are protected during conflicts!", blockIdString);
                }
            }
        }
        
        // Step 6: Check WHITELIST (explicit allow - overrides restrictions)
        Set<String> whitelist = TaxConfig.getBlockInteractionWhitelist();
        
        // Check exact block match
        if (whitelist.contains(blockIdString)) {
            LOGGER.debug("✅ WHITELIST ALLOWED: Player {} {} whitelisted block {} at {}", 
                player.getName().getString(), type, blockIdString, pos);
            return FilterResult.ALLOW;
        }
        
        // Check mod-level match (entries starting with #)
        for (String entry : whitelist) {
            if (entry.startsWith("#")) {
                String modId = entry.substring(1); // Remove # prefix
                if (blockIdString.startsWith(modId + ":")) {
                    LOGGER.debug("✅ WHITELIST ALLOWED (MOD): Player {} {} block {} (mod {} is whitelisted) at {}", 
                        player.getName().getString(), type, blockIdString, modId, pos);
                    return FilterResult.ALLOW;
                }
            }
        }
        
        // Step 7: Not in blacklist or whitelist - pass through to existing protection systems
        return FilterResult.PASS_THROUGH;
    }
    
    /**
     * Check if player is in an active raid or war that should trigger filtering.
     * 
     * @param player The player to check
     * @param colony The colony where the interaction is happening
     * @return true if filtering should be active
     */
    private static boolean isPlayerInActiveRaidOrWar(ServerPlayer player, IColony colony) {
        UUID playerUUID = player.getUUID();
        
        // Check if raid filtering is enabled and player is in a raid
        if (TaxConfig.isBlockFilterRaidsEnabled()) {
            // Check if player is raiding this colony
            if (RaidManager.getActiveRaidForPlayer(playerUUID) != null) {
                return true;
            }
            
            // Check if this colony is being raided
            if (RaidManager.getActiveRaidForColony(colony.getID()) != null) {
                return true;
            }
        }
        
        // Check if war filtering is enabled and player is in a war
        if (TaxConfig.isBlockFilterWarsEnabled()) {
            // Check if this colony is in an active war
            if (WarSystem.ACTIVE_WARS.containsKey(colony.getID())) {
                return true;
            }
            
            // Check if player is involved in any war
            for (WarData warData : WarSystem.ACTIVE_WARS.values()) {
                if (warData.getAttackerLives().containsKey(playerUUID) || 
                    warData.getDefenderLives().containsKey(playerUUID)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /** True when this player is a combatant in an active besiege at this colony (attacker or defender side). */
    private static boolean isBesiegeActiveForPlayer(ServerPlayer player, int colonyId) {
        if (!TaxConfig.isBesiegeSystemEnabled()) return false;
        UUID playerUUID = player.getUUID();
        net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData own =
                net.machiavelli.minecolonytax.besiege.BesiegeManager.getRaidForBesieger(playerUUID);
        if (own != null && own.colonyId == colonyId) return true;
        for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData raid
                : net.machiavelli.minecolonytax.besiege.BesiegeManager.getAllActiveRaidsByBesieger().values()) {
            if (raid.colonyId == colonyId) return true;
        }
        return false;
    }

    /** True when the block is a container (vanilla fast paths + any modded Container BlockEntity). */
    private static boolean isContainerBlock(Block block, Level level, BlockPos pos) {
        if (block instanceof net.minecraft.world.level.block.ChestBlock) return true;
        if (block instanceof net.minecraft.world.level.block.BarrelBlock) return true;
        if (block instanceof net.minecraft.world.level.block.ShulkerBoxBlock) return true;
        if (block instanceof net.minecraft.world.level.block.EnderChestBlock) return true;
        if (block instanceof net.minecraft.world.level.block.HopperBlock) return true;
        if (block instanceof net.minecraft.world.level.block.DispenserBlock) return true;
        if (block instanceof net.minecraft.world.level.block.ChiseledBookShelfBlock) return true;
        if (block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock) return true;
        if (block instanceof net.minecraft.world.level.block.BrewingStandBlock) return true;
        if (block instanceof net.minecraft.world.level.block.LecternBlock) return true;
        try {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof net.minecraft.world.Container) return true;
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Apply the filter result to the event.
     *
     * @param result The filter result
     * @param event The event to modify
     * @param player The player for messaging
     */
    private static void applyFilterResult(FilterResult result, Event event, ServerPlayer player) {
        switch (result.action) {
            case DENY:
                // Cancel whichever interaction event this is (right-click, break, or place).
                if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
                    cancellable.setCanceled(true);
                }

                // Send feedback to player
                player.sendSystemMessage(
                    Component.literal("🚫 " + result.message)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                );
                
                break;
            case ALLOW:
                // Event is not blocked
                break;
            default:
                break;
        }
    }
    
    /**
     * Types of block interactions.
     */
    private enum InteractionType {
        BREAK("break"),
        PLACE("place"),
        USE("use");
        
        private final String name;
        
        InteractionType(String name) {
            this.name = name;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    /**
     * Result of filtering check.
     */
    private static class FilterResult {
        enum Action {
            ALLOW,      // Explicitly allow this interaction
            DENY,       // Explicitly deny this interaction
            PASS_THROUGH // Let other handlers decide
        }
        
        final Action action;
        final String message;
        final String blockId;
        
        private FilterResult(Action action, String message, String blockId) {
            this.action = action;
            this.message = message;
            this.blockId = blockId;
        }
        
        static final FilterResult ALLOW = new FilterResult(Action.ALLOW, null, null);
        static final FilterResult PASS_THROUGH = new FilterResult(Action.PASS_THROUGH, null, null);
        
        static FilterResult deny(String message, String blockId) {
            return new FilterResult(Action.DENY, message, blockId);
        }
    }
}
