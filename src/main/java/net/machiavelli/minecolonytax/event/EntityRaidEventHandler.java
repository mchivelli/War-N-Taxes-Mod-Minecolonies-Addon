package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.raid.EntityRaidManager;
import net.machiavelli.minecolonytax.raid.EntityRaidDebugLogger;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CRITICAL EVENT HANDLER: Monitors entity spawn/join events to trigger entity raids
 * This was the missing piece causing entity raids to not work at runtime!
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityRaidEventHandler {

    private static final Logger LOGGER = LogManager.getLogger(EntityRaidEventHandler.class);
    
    /**
     * Monitor entities joining the level (world) to check for raid triggers
     * This is the PRIMARY event handler for entity raid detection
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        Level level = event.getLevel();
        
        // Only process on server side
        if (level.isClientSide()) {
            return;
        }
        
        // Check if entity raids are enabled in config
        if (!TaxConfig.ENABLE_ENTITY_RAIDS.get()) {
            return;
        }
        
        // Log join event for visibility (no early whitelist gating; filtering happens in threshold check)
        String registryId = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
        EntityRaidDebugLogger.logFilterStep(entity, null, "ENTITY_JOIN_LEVEL", true,
            "Entity joined level: " + registryId + " at " + entity.blockPosition());
        
        // Check nearby colonies for potential raid triggers
        checkNearbyColoniesForRaidTrigger(entity, level);
    }
    
    // NOTE: Removed MobSpawnEvent handler due to compilation issues
    // EntityJoinLevelEvent should be sufficient for detecting entity raids
    
    /**
     * Check colonies for potential entity raid triggers
     */
    private static void checkNearbyColoniesForRaidTrigger(Entity entity, Level level) {
        final int threshold = Math.max(1, TaxConfig.getEntityRaidThreshold());
        final long cooldownMs = Math.max(0, TaxConfig.getEntityRaidCooldownMinutes()) * 60_000L;
        IColonyManager.getInstance().getAllColonies().forEach(colony -> {
            if (colony.getWorld() != level) {
                return; // Different dimension
            }
            
            // Respect active raid and per-colony cooldown
            final int colonyId = colony.getID();
            if (EntityRaidManager.hasActiveEntityRaid(colonyId)) {
                return; // already in raid
            }
            long now = System.currentTimeMillis();
            if (EntityRaidManager.isOnCooldown(colonyId, cooldownMs)) {
                long last = EntityRaidManager.getLastRaidTime(colonyId);
                if (TaxConfig.isEntityRaidDebugEnabled()) {
                    LOGGER.info("[EntityRaid-Event] Colony '{}' on cooldown ({}s left)",
                        colony.getName(), (cooldownMs - (now - last)) / 1000);
                }
                return;
            }

            // Count eligible entities that are currently inside the colony boundary
            int eligibleCount = 0;
            Entity firstTrigger = null;
            ServerLevel serverLevel = (ServerLevel) level;
            for (Entity e : serverLevel.getEntities().getAll()) {
                try {
                    if (EntityRaidManager.shouldTriggerEntityRaid(e, colony)) {
                        boolean inside = false;
                        try {
                            inside = colony.isCoordInColony(colony.getWorld(), e.blockPosition());
                        } catch (Throwable t) {
                            inside = false;
                        }
                        if (!inside) {
                            continue;
                        }
                        eligibleCount++;
                        if (firstTrigger == null) firstTrigger = e;
                        if (eligibleCount >= threshold) {
                            break; // Enough entities inside; stop scanning further
                        }
                    }
                } catch (Exception ex) {
                    if (TaxConfig.isEntityRaidDebugEnabled()) {
                        LOGGER.warn("[EntityRaid-Event] Error evaluating entity for colony '{}': {}",
                            colony.getName(), ex.toString());
                    }
                }
            }

            if (eligibleCount >= threshold && firstTrigger != null) {
                if (TaxConfig.isEntityRaidDebugEnabled()) {
                    LOGGER.info("[EntityRaid-Event] Threshold met for colony '{}' (count={}, threshold={}). Starting raid.",
                        colony.getName(), eligibleCount, threshold);
                }
                EntityRaidManager.startEntityRaid(colony, firstTrigger);
            } else if (TaxConfig.isEntityRaidDebugEnabled() && TaxConfig.getEntityRaidDebugLevel() >= 3) {
                LOGGER.debug("[EntityRaid-Event] Colony '{}' count={} (threshold={}), no raid.",
                    colony.getName(), eligibleCount, threshold);
            }
        });
    }
}
