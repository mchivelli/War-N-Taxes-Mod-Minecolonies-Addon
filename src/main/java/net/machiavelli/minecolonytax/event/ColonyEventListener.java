package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.raid.EntityRaidManager;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.raid.ActiveRaidData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ColonyEventListener {

    private static final Logger LOGGER = LogManager.getLogger(ColonyEventListener.class);

    // Map to track building levels in each colony
    private static final Map<Integer, Map<IBuilding, Integer>> colonyBuildingLevels = new HashMap<>();
    
    // New tick counter and interval (20 ticks = ~1 second)
    private static int tickCounter = 0;
    private static final int CHECK_INTERVAL_TICKS = 20;

    // Entity raid scan scheduler
    private static int entityScanTickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // Always advance entity raid lifecycle each server tick when enabled
        if (TaxConfig.ENABLE_ENTITY_RAIDS.get()) {
            EntityRaidManager.tick();
        }

        // Periodically scan for whitelisted entities near colonies to meet threshold
        // Reduced frequency to avoid spam - scan every 10 seconds instead of every second
        if (TaxConfig.ENABLE_ENTITY_RAIDS.get()) {
            entityScanTickCounter++;
            if (entityScanTickCounter >= 200) { // Every 10 seconds (200 ticks)
                entityScanTickCounter = 0;
                scanColoniesForEntityRaids();
            }
        }

        List<IColony> colonies = IColonyManager.getInstance().getAllColonies();

        for (IColony colony : colonies) {
            int colonyId = colony.getID();
            Map<IBuilding, Integer> buildingLevels = colonyBuildingLevels.computeIfAbsent(colonyId, k -> new HashMap<>());

            int guardTowerCount = 0;
            int newOrUpgradedBuildingsCount = 0;

            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                int currentLevel = building.getBuildingLevel();

                // Count Guard Towers
                if (isGuardTower(building)) {
                    guardTowerCount++;
                }

                            // Check if the building is new or has been upgraded (tracking only, no tax generation)
            if (!buildingLevels.containsKey(building) || buildingLevels.get(building) < currentLevel) {
                newOrUpgradedBuildingsCount++;
                
                // Update the cached building level (for tracking purposes only)
                buildingLevels.put(building, currentLevel);
            }
            }

            // Condensed logging: Only log summary if buildings were processed and logging is enabled
            if (newOrUpgradedBuildingsCount > 0 && TaxConfig.showColonyInitializationLogs()) {
                LOGGER.info("Colony '{}': Detected {} new/upgraded buildings (Guards: {})", 
                           colony.getName(), newOrUpgradedBuildingsCount, guardTowerCount);
            }
            
            // Guard tower boost is now properly implemented in TaxManager.generateTaxesForAllColonies()
            // This ensures it's applied consistently with the main tax generation cycle
        }
    }

    /**
     * Periodically scans all colonies and triggers an entity raid when the
     * number of eligible whitelisted entities currently inside the colony boundary
     * meets or exceeds the configured threshold.
     * Uses EntityRaidManager.shouldTriggerEntityRaid for per-entity filtering
     * (whitelist, cooldown, alliance), and respects a per-colony cooldown.
     */
    private static void scanColoniesForEntityRaids() {
        try {
            final int threshold = Math.max(1, TaxConfig.getEntityRaidThreshold());
            final long cooldownMs = Math.max(0, TaxConfig.getEntityRaidCooldownMinutes()) * 60_000L;

            List<IColony> colonies = IColonyManager.getInstance().getAllColonies();
            for (IColony colony : colonies) {
                // Skip if different dimension type or world not ready
                if (colony == null || colony.getWorld() == null || !(colony.getWorld() instanceof ServerLevel)) {
                    continue;
                }

                final int colonyId = colony.getID();

                // Skip if a raid is already active
                if (EntityRaidManager.hasActiveEntityRaid(colonyId)) {
                    continue;
                }

                // Per-colony cooldown
                long now = System.currentTimeMillis();
                if (EntityRaidManager.isOnCooldown(colonyId, cooldownMs)) {
                    long last = EntityRaidManager.getLastRaidTime(colonyId);
                    if (TaxConfig.isEntityRaidDebugEnabled()) {
                        LOGGER.info("[EntityRaid-SCAN] Colony '{}' on cooldown ({}s left)",
                                colony.getName(), (cooldownMs - (now - last)) / 1000);
                    }
                    continue;
                }

                // Scan entities currently inside colony boundary
                ServerLevel level = (ServerLevel) colony.getWorld();

                int eligibleCount = 0;
                Entity firstTrigger = null;

                // Only log scanning at high debug levels to reduce spam
                if (TaxConfig.isEntityRaidDebugEnabled() && TaxConfig.getEntityRaidDebugLevel() >= 2) {
                    LOGGER.info("[EntityRaid-SCAN] 🔍 Scanning colony '{}' for whitelisted entities currently INSIDE boundary", 
                        colony.getName());
                }

                for (Entity e : level.getEntities().getAll()) {
                    // Check if this entity qualifies (whitelist, not allied, not in grace) AND is inside the colony
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
                            // Only log individual entities at maximum debug level to avoid spam
                            if (TaxConfig.isEntityRaidDebugEnabled() && TaxConfig.getEntityRaidDebugLevel() >= 3) {
                                LOGGER.debug("[EntityRaid-SCAN] ✅ Entity {} is INSIDE boundary and qualifies (count: {})",
                                    e.getType().getDescriptionId(), eligibleCount);
                            }
                            // Early exit once threshold reached
                            if (eligibleCount >= threshold) {
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        // Robustness: never let one bad entity break the scan
                        if (TaxConfig.isEntityRaidDebugEnabled()) {
                            LOGGER.warn("[EntityRaid-SCAN] Error evaluating entity near colony '{}': {}",
                                    colony.getName(), ex.toString());
                        }
                    }
                }

                if (eligibleCount >= threshold && firstTrigger != null) {
                    if (TaxConfig.isEntityRaidDebugEnabled()) {
                        LOGGER.info("[EntityRaid-SCAN] Threshold met for colony '{}' (count={}, threshold={}) — starting raid.",
                                colony.getName(), eligibleCount, threshold);
                    }
                    EntityRaidManager.startEntityRaid(colony, firstTrigger);
                } else if (TaxConfig.isEntityRaidDebugEnabled() && TaxConfig.getEntityRaidDebugLevel() >= 2) {
                    LOGGER.info("[EntityRaid-SCAN] Colony '{}' count={} (threshold={}), no raid.",
                            colony.getName(), eligibleCount, threshold);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[EntityRaid-SCAN] Unexpected error during scan:", e);
        }
    }

    /**
     * Determines if a building is a guard tower using multiple identification methods.
     * @param building The building to check
     * @return true if the building is a guard tower, false otherwise
     */
    private static boolean isGuardTower(IBuilding building) {
        if (building == null) return false;
        
        // Method 1: Check display name (current approach)
        String displayName = building.getBuildingDisplayName();
        if (displayName != null && "Guard Tower".equalsIgnoreCase(displayName)) {
            return true;
        }
        
        // Method 2: Check if class name contains "guardtower"
        String className = building.getClass().getName().toLowerCase();
        if (className.contains("guardtower")) {
            return true;
        }
        
        // Method 3: Check if the building has guard-related functionality
        // This is a fallback in case the building class structure changes
        try {
            // Try to get the schematic name if available
            String toString = building.toString().toLowerCase();
            if (toString.contains("guardtower") || toString.contains("guard_tower")) {
                return true;
            }
        } catch (Exception e) {
            // Ignore any reflection exceptions
        }
        
        return false;
    }
    
    @SubscribeEvent
    public static void onGuardDeath(LivingDeathEvent event) {
        // Check if this is a guard citizen killed during a raid
        if (!(event.getEntity() instanceof AbstractEntityCitizen citizen)) return;
        
        var citizenData = citizen.getCitizenData();
        if (citizenData == null || citizenData.getJob() == null) return;
        
        // Check if this citizen is a guard
        if (!citizenData.getJob().isGuard()) return;
        
        IColony colony = citizenData.getColony();
        if (colony == null) return;
        
        // Check if there's an active raid on this colony
        ActiveRaidData raidData = RaidManager.getActiveRaidByColony(colony.getID());
        if (raidData == null) return;
        
        // Increment guard kills counter
        raidData.incrementGuardsKilled();
        
        LOGGER.info("Guard killed during raid! Colony: {}, Guards killed: {}/{}", 
            colony.getName(), raidData.getGuardsKilled(), raidData.getTotalGuards());
        
        // Update boss bar to show guard kill progress
        if (raidData.getBossEvent() != null) {
            double killPercentage = raidData.getGuardKillPercentage();
            String progressText = String.format("Raid Progress: %d/%d Guards Defeated (%.1f%%)", 
                raidData.getGuardsKilled(), raidData.getTotalGuards(), killPercentage * 100);
            
            raidData.getBossEvent().setName(net.minecraft.network.chat.Component.literal(progressText));
        }
    }
}
