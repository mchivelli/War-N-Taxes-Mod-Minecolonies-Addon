package net.machiavelli.minecolonytax.raid;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Handles applying and removing resistance effects to colony guards during raids and wars
 */
public class GuardResistanceHandler {
    
    private static final Logger LOGGER = LogManager.getLogger(GuardResistanceHandler.class);
    
    // Track which guards have resistance effects applied during raids and wars
    private static final Map<Integer, Set<UUID>> colonyGuardEffects = new HashMap<>();
    
    // Track which guards have resistance effects applied during wars (separate tracking)
    private static final Map<Integer, Set<UUID>> colonyWarGuardEffects = new HashMap<>();
    
    /**
     * Apply resistance effects to all guards in a colony when a raid starts
     * @param colony The colony under raid
     */
    @Deprecated
    // Use applyResistanceToGuardsForRaid for clarity
    public static void applyResistanceToGuards(IColony colony) {
        if (!TaxConfig.isGuardResistanceDuringRaidsEnabled()) {
            return;
        }
        
        int resistanceLevel = TaxConfig.getGuardResistanceLevel();
        if (resistanceLevel <= 0) {
            return;
        }
        
        Integer colonyId = colony.getID();
        Set<UUID> affectedGuards = new HashSet<>();
        
        try {
            // Find all guard buildings in the colony
            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                if (isGuardBuilding(building)) {
                    // Apply resistance to all citizens in guard buildings
                    building.getAllAssignedCitizen().forEach(citizenData -> {
                        if (citizenData != null && citizenData.getEntity().isPresent()) {
                            AbstractEntityCitizen guard = citizenData.getEntity().get();
                            if (guard != null && guard.isAlive()) {
                                applyResistanceEffect(guard, resistanceLevel);
                                affectedGuards.add(guard.getUUID());
                            }
                        }
                    });
                }
            }
            
            // Apply resistance to all citizens if configured
            if (TaxConfig.APPLY_RESISTANCE_TO_CITIZENS.get()) {
                colony.getCitizenManager().getCitizens().forEach(citizenData -> {
                    if (citizenData != null && citizenData.getEntity().isPresent()) {
                        AbstractEntityCitizen citizen = citizenData.getEntity().get();
                        if (citizen != null && citizen.isAlive() && !affectedGuards.contains(citizen.getUUID())) {
                            applyResistanceEffect(citizen, resistanceLevel);
                            affectedGuards.add(citizen.getUUID());
                        }
                    }
                });
            }
            
            // Store the affected guards for cleanup later
            colonyGuardEffects.put(colonyId, affectedGuards);
            
            if (!affectedGuards.isEmpty()) {
                String entityType = TaxConfig.APPLY_RESISTANCE_TO_CITIZENS.get() ? "guards and citizens" : "guards";
                LOGGER.info("Applied Resistance {} effect to {} {} in colony '{}' during raid", 
                    resistanceLevel, affectedGuards.size(), entityType, colony.getName());
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to apply resistance effects to guards in colony '{}': {}", 
                colony.getName(), e.getMessage());
        }
    }
    
    /**
     * Remove resistance effects from all guards in a colony when a raid ends
     * @param colony The colony where the raid ended
     */
    @Deprecated
    // Use removeResistanceFromGuardsForRaid for clarity
    public static void removeResistanceFromGuards(IColony colony) {
        if (!TaxConfig.isGuardResistanceDuringRaidsEnabled()) {
            return;
        }
        
        Integer colonyId = colony.getID();
        Set<UUID> affectedGuards = colonyGuardEffects.get(colonyId);
        
        if (affectedGuards == null || affectedGuards.isEmpty()) {
            return;
        }
        
        int removedCount = 0;
        
        try {
            // Find all guard buildings and remove resistance effects
            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                if (isGuardBuilding(building)) {
                    building.getAllAssignedCitizen().forEach(citizenData -> {
                        if (citizenData != null && citizenData.getEntity().isPresent()) {
                            AbstractEntityCitizen guard = citizenData.getEntity().get();
                            if (guard != null && affectedGuards.contains(guard.getUUID())) {
                                removeResistanceEffect(guard);
                            }
                        }
                    });
                }
            }
            
            removedCount = affectedGuards.size();
            
            // Clean up tracking
            colonyGuardEffects.remove(colonyId);
            
            if (removedCount > 0) {
                LOGGER.info("Removed Resistance effects from {} guards in colony '{}' after raid", 
                    removedCount, colony.getName());
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to remove resistance effects from guards in colony '{}': {}", 
                colony.getName(), e.getMessage());
        }
    }
    
    /**
     * Apply resistance effect to a specific guard
     * @param guard The guard entity
     * @param level The resistance level (1-255)
     */
    private static void applyResistanceEffect(AbstractEntityCitizen guard, int level) {
        try {
            // Create a resistance effect that lasts for a long duration (2 hours in ticks)
            // This should be longer than any possible raid duration
            int durationTicks = 20 * 60 * 120; // 20 ticks/sec * 60 sec/min * 120 min = 2 hours
            
            MobEffectInstance resistanceEffect = new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE, 
                durationTicks, 
                level - 1, // Effect levels are 0-based, but config is 1-based
                false, // ambient
                true,  // visible
                true   // show icon
            );
            
            guard.addEffect(resistanceEffect);
            
        } catch (Exception e) {
            LOGGER.warn("Failed to apply resistance effect to guard {}: {}", 
                guard.getUUID(), e.getMessage());
        }
    }
    
    /**
     * Remove resistance effect from a specific guard
     * @param guard The guard entity
     */
    private static void removeResistanceEffect(AbstractEntityCitizen guard) {
        try {
            guard.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        } catch (Exception e) {
            LOGGER.warn("Failed to remove resistance effect from guard {}: {}", 
                guard.getUUID(), e.getMessage());
        }
    }
    
    /**
     * Check if a building is a guard-related building
     * @param building The building to check
     * @return true if it's a guard building (guard tower, barracks, etc.)
     */
    private static boolean isGuardBuilding(IBuilding building) {
        if (building == null) return false;
        
        // Check display name
        String displayName = building.getBuildingDisplayName();
        if (displayName != null) {
            String lowerName = displayName.toLowerCase();
            if (lowerName.contains("guard") || lowerName.contains("barracks") || 
                lowerName.contains("combat") || lowerName.contains("archery")) {
                return true;
            }
        }
        
        // Check class name
        String className = building.getClass().getName().toLowerCase();
        if (className.contains("guard") || className.contains("barracks") || 
            className.contains("combat") || className.contains("archery")) {
            return true;
        }
        
        return false;
    }
    
    
    // ==== WAR-SPECIFIC METHODS ====
    
    /**
     * Apply resistance effects to all guards in a colony when a war starts
     * @param colony The colony involved in war
     */
    public static void applyResistanceToGuardsForWar(IColony colony) {
        if (!TaxConfig.isGuardResistanceDuringRaidsEnabled()) {
            return;
        }
        
        int resistanceLevel = TaxConfig.getGuardResistanceLevel();
        if (resistanceLevel <= 0) {
            return;
        }
        
        Integer colonyId = colony.getID();
        Set<UUID> affectedGuards = new HashSet<>();
        
        try {
            // Find all guard buildings in the colony
            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                if (isGuardBuilding(building)) {
                    // Apply resistance to all citizens in guard buildings
                    building.getAllAssignedCitizen().forEach(citizenData -> {
                        if (citizenData != null && citizenData.getEntity().isPresent()) {
                            AbstractEntityCitizen guard = citizenData.getEntity().get();
                            if (guard != null && guard.isAlive()) {
                                applyResistanceEffect(guard, resistanceLevel);
                                affectedGuards.add(guard.getUUID());
                            }
                        }
                    });
                }
            }
            
            // Apply resistance to all citizens if configured
            if (TaxConfig.APPLY_RESISTANCE_TO_CITIZENS.get()) {
                colony.getCitizenManager().getCitizens().forEach(citizenData -> {
                    if (citizenData != null && citizenData.getEntity().isPresent()) {
                        AbstractEntityCitizen citizen = citizenData.getEntity().get();
                        if (citizen != null && citizen.isAlive() && !affectedGuards.contains(citizen.getUUID())) {
                            applyResistanceEffect(citizen, resistanceLevel);
                            affectedGuards.add(citizen.getUUID());
                        }
                    }
                });
            }
            
            // Store the affected guards for cleanup later (use war-specific tracking)
            colonyWarGuardEffects.put(colonyId, affectedGuards);
            
            if (!affectedGuards.isEmpty()) {
                String entityType = TaxConfig.APPLY_RESISTANCE_TO_CITIZENS.get() ? "guards and citizens" : "guards";
                LOGGER.info("Applied Resistance {} effect to {} {} in colony '{}' during war", 
                    resistanceLevel, affectedGuards.size(), entityType, colony.getName());
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to apply resistance effects to guards in colony '{}' during war: {}", 
                colony.getName(), e.getMessage());
        }
    }
    
    /**
     * Remove resistance effects from all guards in a colony when a war ends
     * @param colony The colony where the war ended
     */
    public static void removeResistanceFromGuardsForWar(IColony colony) {
        if (!TaxConfig.isGuardResistanceDuringRaidsEnabled()) {
            return;
        }
        
        Integer colonyId = colony.getID();
        Set<UUID> affectedGuards = colonyWarGuardEffects.get(colonyId);
        
        if (affectedGuards == null || affectedGuards.isEmpty()) {
            return;
        }
        
        int removedCount = 0;
        
        try {
            // Find all guard buildings and remove resistance effects
            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                if (isGuardBuilding(building)) {
                    building.getAllAssignedCitizen().forEach(citizenData -> {
                        if (citizenData != null && citizenData.getEntity().isPresent()) {
                            AbstractEntityCitizen guard = citizenData.getEntity().get();
                            if (guard != null && affectedGuards.contains(guard.getUUID())) {
                                removeResistanceEffect(guard);
                            }
                        }
                    });
                }
            }
            
            removedCount = affectedGuards.size();
            
            // Clean up war-specific tracking
            colonyWarGuardEffects.remove(colonyId);
            
            if (removedCount > 0) {
                LOGGER.info("Removed Resistance effects from {} guards in colony '{}' after war", 
                    removedCount, colony.getName());
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to remove resistance effects from guards in colony '{}' after war: {}", 
                colony.getName(), e.getMessage());
        }
    }
    
    // ==== RAID-SPECIFIC METHODS (for backwards compatibility) ====
    
    /**
     * Apply resistance effects to all guards in a colony when a raid starts
     * @param colony The colony under raid
     */
    public static void applyResistanceToGuardsForRaid(IColony colony) {
        if (!TaxConfig.isGuardResistanceDuringRaidsEnabled()) {
            return;
        }
        
        int resistanceLevel = TaxConfig.getGuardResistanceLevel();
        if (resistanceLevel <= 0) {
            return;
        }
        
        Integer colonyId = colony.getID();
        Set<UUID> affectedGuards = new HashSet<>();
        
        try {
            // Find all guard buildings in the colony
            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                if (isGuardBuilding(building)) {
                    // Apply resistance to all citizens in guard buildings
                    building.getAllAssignedCitizen().forEach(citizenData -> {
                        if (citizenData != null && citizenData.getEntity().isPresent()) {
                            AbstractEntityCitizen guard = citizenData.getEntity().get();
                            if (guard != null && guard.isAlive()) {
                                applyResistanceEffect(guard, resistanceLevel);
                                affectedGuards.add(guard.getUUID());
                            }
                        }
                    });
                }
            }
            
            // Apply resistance to all citizens if configured
            if (TaxConfig.APPLY_RESISTANCE_TO_CITIZENS.get()) {
                colony.getCitizenManager().getCitizens().forEach(citizenData -> {
                    if (citizenData != null && citizenData.getEntity().isPresent()) {
                        AbstractEntityCitizen citizen = citizenData.getEntity().get();
                        if (citizen != null && citizen.isAlive() && !affectedGuards.contains(citizen.getUUID())) {
                            applyResistanceEffect(citizen, resistanceLevel);
                            affectedGuards.add(citizen.getUUID());
                        }
                    }
                });
            }
            
            // Store the affected guards for cleanup later (use raid-specific tracking)
            colonyGuardEffects.put(colonyId, affectedGuards);
            
            if (!affectedGuards.isEmpty()) {
                String entityType = TaxConfig.APPLY_RESISTANCE_TO_CITIZENS.get() ? "guards and citizens" : "guards";
                LOGGER.info("Applied Resistance {} effect to {} {} in colony '{}' during raid", 
                    resistanceLevel, affectedGuards.size(), entityType, colony.getName());
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to apply resistance effects to guards in colony '{}' during raid: {}", 
                colony.getName(), e.getMessage());
        }
    }
    
    /**
     * Remove resistance effects from all guards in a colony when a raid ends
     * @param colony The colony where the raid ended
     */
    public static void removeResistanceFromGuardsForRaid(IColony colony) {
        if (!TaxConfig.isGuardResistanceDuringRaidsEnabled()) {
            return;
        }
        
        Integer colonyId = colony.getID();
        Set<UUID> affectedGuards = colonyGuardEffects.get(colonyId);
        
        if (affectedGuards == null || affectedGuards.isEmpty()) {
            return;
        }
        
        int removedCount = 0;
        
        try {
            // Find all guard buildings and remove resistance effects
            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                if (isGuardBuilding(building)) {
                    building.getAllAssignedCitizen().forEach(citizenData -> {
                        if (citizenData != null && citizenData.getEntity().isPresent()) {
                            AbstractEntityCitizen guard = citizenData.getEntity().get();
                            if (guard != null && affectedGuards.contains(guard.getUUID())) {
                                removeResistanceEffect(guard);
                            }
                        }
                    });
                }
            }
            
            removedCount = affectedGuards.size();
            
            // Clean up raid-specific tracking
            colonyGuardEffects.remove(colonyId);
            
            if (removedCount > 0) {
                LOGGER.info("Removed Resistance effects from {} guards in colony '{}' after raid", 
                    removedCount, colony.getName());
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to remove resistance effects from guards in colony '{}' after raid: {}", 
                colony.getName(), e.getMessage());
        }
    }

    /**
     * Emergency cleanup method to remove all tracked resistance effects
     * Should be called on server shutdown or in case of errors
     */
    public static void emergencyCleanup() {
        colonyGuardEffects.clear();
        colonyWarGuardEffects.clear();
        LOGGER.info("Emergency cleanup of guard resistance effects completed");
    }
    
    /**
     * Get the number of colonies currently tracked for guard resistance effects
     * @return Number of colonies with active guard resistance effects
     */
    public static int getTrackedColoniesCount() {
        return colonyGuardEffects.size() + colonyWarGuardEffects.size();
    }
}
