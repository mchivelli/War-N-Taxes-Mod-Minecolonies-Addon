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
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class BlockInteractionFilterHandler {
    
    private static final Logger LOGGER = LogManager.getLogger(BlockInteractionFilterHandler.class);
    
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

    // LeftClickBlock is NOT handled here — it fires every tick while holding left-click,
    // which would cause lag (colony lookups 20x/sec) and break normal mining.
    // MineColonies counts left-click denials toward levitation; the tick-based levitation
    // remover in WarEventHandler handles that instead.
    
    /**
     * Returns true if at least one conflict system currently has active state.
     * All four checks are lock-free isEmpty() reads on ConcurrentHashMap or size==0
     * on HashMap — effectively free. This guard lets checkBlockInteraction skip the
     * relatively expensive getColonyByPosFromWorld call during normal play when no
     * wars, raids, occupations, or besieges are in progress.
     */
    private static boolean anyConflictSystemActive() {
        if (TaxConfig.isOccupationSystemEnabled()
                && !net.machiavelli.minecolonytax.occupation.OccupationManager
                        .getActiveOccupations().isEmpty()) return true;
        if (TaxConfig.isBesiegeSystemEnabled()
                && !net.machiavelli.minecolonytax.besiege.BesiegeManager
                        .getActiveRaids().isEmpty()) return true;
        if (TaxConfig.isBlockFilterRaidsEnabled()
                && !RaidManager.getActiveRaids().isEmpty()) return true;
        if (TaxConfig.isBlockFilterWarsEnabled()
                && !WarSystem.ACTIVE_WARS.isEmpty()) return true;
        return false;
    }

    private static FilterResult checkBlockInteraction(
            ServerPlayer player,
            BlockPos pos,
            Level level,
            Block block,
            InteractionType type) {

        if (!TaxConfig.isBlockInteractionFilterEnabled()) {
            return FilterResult.PASS_THROUGH;
        }

        // Fast path: skip the colony lookup entirely when no conflict system is active.
        // This is the common case on servers that are not in a war/raid/occupation/besiege.
        if (!anyConflictSystemActive()) {
            return FilterResult.PASS_THROUGH;
        }

        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        if (colony == null) {
            return FilterResult.PASS_THROUGH;
        }

        if (TaxConfig.isOccupationSystemEnabled()
                && net.machiavelli.minecolonytax.occupation.OccupationManager.shouldBlockInteraction(
                        player.getUUID(), colony.getID())) {
            LOGGER.debug("OCCUPATION DENIED: Occupier {} attempted {} in occupied colony {} at {}",
                player.getName().getString(), type, colony.getName(), pos);
            return FilterResult.deny("You are occupying this colony - you can collect taxes but cannot interact with colony items!",
                block.builtInRegistryHolder().key().location().toString());
        }

        if (TaxConfig.isBesiegeSystemEnabled()
                && net.machiavelli.minecolonytax.besiege.BesiegeManager.shouldBlockInteraction(
                        player.getUUID(), colony.getID())) {
            LOGGER.debug("BESIEGE LOCKOUT DENIED: Former owner {} attempted {} in besieged colony {} at {}",
                player.getName().getString(), type, colony.getName(), pos);
            return FilterResult.deny(
                "This colony is under besiege occupation. Use /wnt besiege " + colony.getName() + " to reclaim it.",
                block.builtInRegistryHolder().key().location().toString());
        }
        
        boolean isActiveRaidOrWar = isPlayerInActiveConflict(player, colony);
        if (!isActiveRaidOrWar) {
            return FilterResult.PASS_THROUGH;
        }

        // Block breaking by players is always denied during conflicts.
        // Only explosive damage (siege machines) may destroy blocks.
        // Denying here prevents MineColonies from applying its levitation punishment.
        if (type == InteractionType.BREAK) {
            LOGGER.debug("CONFLICT DENIED (BREAK): Player {} cannot break blocks during raids/wars at {}",
                player.getName().getString(), pos);
            return FilterResult.deny("Block breaking is not allowed during raids and wars!", null);
        }

        // Siege SMP rule: during a besiege, deny right-click USE on container-style blocks
        // (chests, barrels, shulkers, furnaces, etc., plus any modded BlockEntity Container).
        // Combat-only — no looting. Doors/levers/buttons still pass through via the
        // existing whitelist/blacklist below.
        if (type == InteractionType.USE && isBesiegeActiveForPlayer(player, colony.getID())) {
            if (isContainerBlock(block, level, pos)) {
                LOGGER.debug("BESIEGE DENIED (CONTAINER): Player {} cannot open containers during besiege at {}",
                    player.getName().getString(), pos);
                return FilterResult.deny("You cannot loot containers during a besiege!",
                    block.builtInRegistryHolder().key().location().toString());
            }
        }

        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        if (blockId == null) {
            LOGGER.warn("Could not get registry key for block: {}", block);
            return FilterResult.PASS_THROUGH;
        }

        String blockIdString = blockId.toString();

        Set<String> blacklist = TaxConfig.getBlockInteractionBlacklist();

        if (blacklist.contains(blockIdString)) {
            LOGGER.debug("BLACKLIST DENIED: Player {} attempted {} on blacklisted block {} at {}",
                player.getName().getString(), type, blockIdString, pos);
            return FilterResult.deny("This block is protected during conflicts!", blockIdString);
        }

        for (String entry : blacklist) {
            if (entry.startsWith("#")) {
                String modId = entry.substring(1);
                if (blockIdString.startsWith(modId + ":")) {
                    LOGGER.debug("BLACKLIST DENIED (MOD): Player {} attempted {} on block {} (mod {} is blacklisted) at {}",
                        player.getName().getString(), type, blockIdString, modId, pos);
                    return FilterResult.deny("Blocks from this mod are protected during conflicts!", blockIdString);
                }
            }
        }

        Set<String> whitelist = TaxConfig.getBlockInteractionWhitelist();

        if (whitelist.contains(blockIdString)) {
            LOGGER.debug("WHITELIST ALLOWED: Player {} {} whitelisted block {} at {}",
                player.getName().getString(), type, blockIdString, pos);
            return FilterResult.ALLOW;
        }

        for (String entry : whitelist) {
            if (entry.startsWith("#")) {
                String modId = entry.substring(1);
                if (blockIdString.startsWith(modId + ":")) {
                    LOGGER.debug("WHITELIST ALLOWED (MOD): Player {} {} block {} (mod {} is whitelisted) at {}",
                        player.getName().getString(), type, blockIdString, modId, pos);
                    return FilterResult.ALLOW;
                }
            }
        }

        return FilterResult.PASS_THROUGH;
    }
    
    /**
     * Returns true only when this specific player is an active participant in a conflict
     * that involves this specific colony. This keeps WNT's restrictions scoped to
     * actual combatants at their conflict site, so other protection mods (FTB Chunks,
     * Waystones land claims, etc.) remain unaffected for everyone else.
     */
    private static boolean isPlayerInActiveConflict(ServerPlayer player, IColony colony) {
        UUID playerUUID = player.getUUID();
        int colonyId = colony.getID();

        if (TaxConfig.isBlockFilterRaidsEnabled() && !RaidManager.getActiveRaids().isEmpty()) {
            net.machiavelli.minecolonytax.raid.ActiveRaidData raid =
                RaidManager.getActiveRaidForPlayer(playerUUID);
            if (raid != null && raid.getColony() != null && raid.getColony().getID() == colonyId) {
                return true;
            }
        }

        if (TaxConfig.isBlockFilterWarsEnabled() && !WarSystem.ACTIVE_WARS.isEmpty()) {
            for (WarData warData : WarSystem.ACTIVE_WARS.values()) {
                boolean playerIsParticipant =
                    (warData.getAttackerLives() != null && warData.getAttackerLives().containsKey(playerUUID))
                    || (warData.getDefenderLives() != null && warData.getDefenderLives().containsKey(playerUUID));
                if (!playerIsParticipant) continue;

                boolean colonyIsInWar =
                    (warData.getColony() != null && warData.getColony().getID() == colonyId)
                    || (warData.getAttackerColony() != null && warData.getAttackerColony().getID() == colonyId);
                if (colonyIsInWar) {
                    return true;
                }
            }
        }

        return false;
    }
    
    /** True if any besiege raid is active that involves this player and colony. */
    private static boolean isBesiegeActiveForPlayer(ServerPlayer player, int colonyId) {
        if (!TaxConfig.isBesiegeSystemEnabled()) return false;
        UUID playerUUID = player.getUUID();
        // The besieger themselves vs the besieged colony
        net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData own =
                net.machiavelli.minecolonytax.besiege.BesiegeManager.getRaidForBesieger(playerUUID);
        if (own != null && own.colonyId == colonyId) return true;
        // Defender side: any active raid is targeting this colony
        for (net.machiavelli.minecolonytax.besiege.BesiegeManager.BesiegeRaidData raid
                : net.machiavelli.minecolonytax.besiege.BesiegeManager.getAllActiveRaidsByBesieger().values()) {
            if (raid.colonyId == colonyId) return true;
        }
        return false;
    }

    /**
     * True if this block exposes a Container — chests, barrels, shulkers, hoppers,
     * dispensers, furnaces, brewing stands, AND any modded block whose BlockEntity
     * implements net.minecraft.world.Container.
     *
     * Two-tier check:
     *  1. Fast vanilla-class instanceof for the common cases
     *  2. Level-aware BlockEntity Container instanceof fallback for everything else
     *     (modded chests/storage that don't subclass vanilla blocks)
     */
    private static boolean isContainerBlock(Block block, Level level, BlockPos pos) {
        // Vanilla fast paths
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

        // Catch-all: modded containers whose BlockEntity implements Container.
        // O(1) lookup — the entity is already cached on the chunk.
        try {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof net.minecraft.world.Container) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private static void applyFilterResult(FilterResult result, Event event, ServerPlayer player) {
        switch (result.action) {
            case DENY:
                event.setResult(Event.Result.DENY);
                if (event.isCancelable()) {
                    event.setCanceled(true);
                }
                player.sendSystemMessage(
                    Component.literal(result.message)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                );
                LOGGER.debug("Filter DENIED interaction for {} on block {}",
                    player.getName().getString(), result.blockId);
                break;

            case ALLOW:
                event.setResult(Event.Result.ALLOW);
                LOGGER.debug("Filter ALLOWED interaction for {}", player.getName().getString());
                break;

            case PASS_THROUGH:
                break;
        }
    }
    
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
    
    private static class FilterResult {
        enum Action {
            ALLOW,
            DENY,
            PASS_THROUGH
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
