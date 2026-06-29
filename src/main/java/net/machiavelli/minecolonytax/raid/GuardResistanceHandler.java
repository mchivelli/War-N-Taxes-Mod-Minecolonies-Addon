package net.machiavelli.minecolonytax.raid;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Handles applying and removing resistance effects to colony guards during
 * raids and wars
 */
public class GuardResistanceHandler {

    private static final Logger LOGGER = LogManager.getLogger(GuardResistanceHandler.class);

    // Raid and war effects are tracked separately so they can be removed independently.
    private static final Map<Integer, Set<UUID>> colonyGuardEffects = new HashMap<>();
    private static final Map<Integer, Set<UUID>> colonyWarGuardEffects = new HashMap<>();

    /**
     * Apply resistance effects to all guards in a colony when a raid starts
     * 
     * @param colony The colony under raid
     */
    @Deprecated
    public static void applyResistanceToGuards(IColony colony) {
        if (!TaxConfig.isGuardResistanceDuringRaidsEnabled()) {
            return;
        }

        int baseResistanceLevel = TaxConfig.getGuardResistanceLevel();
        int upgradeBonus = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager.getDefenseLevelBonus(colony.getID());
        int resistanceLevel = baseResistanceLevel + upgradeBonus;
        if (resistanceLevel <= 0) {
            return;
        }

        Integer colonyId = colony.getID();
        Set<UUID> affectedGuards = new HashSet<>();

        try {
            for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
                if (isGuardBuilding(building)) {
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

            colonyGuardEffects.put(colonyId, affectedGuards);

            if (!affectedGuards.isEmpty()) {
                String entityType = TaxConfig.APPLY_RESISTANCE_TO_CITIZENS.get() ? "guards and citizens" : "guards";
                if (TaxConfig.isDebugLogging()) {
                    LOGGER.info("Applied Resistance {} effect to {} {} in colony '{}' during raid",
                            resistanceLevel, affectedGuards.size(), entityType, colony.getName());
                }
            }

        } catch (Exception e) {
            LOGGER.error("Failed to apply resistance effects to guards in colony '{}': {}",
                    colony.getName(), e.getMessage());
        }
    }

    /**
     * Remove resistance effects from all guards in a colony when a raid ends
     *
     * @param colony The colony where the raid ended
     */
    @Deprecated
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
            for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
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
            colonyGuardEffects.remove(colonyId);

            if (removedCount > 0) {
                if (TaxConfig.isDebugLogging()) {
                    LOGGER.info("Removed Resistance effects from {} guards in colony '{}' after raid",
                            removedCount, colony.getName());
                }
            }

        } catch (Exception e) {
            LOGGER.error("Failed to remove resistance effects from guards in colony '{}': {}",
                    colony.getName(), e.getMessage());
        }
    }

    /**
     * Apply resistance effect to a specific guard
     *
     * @param guard The guard entity
     * @param level The resistance level (1-255)
     */
    private static void applyResistanceEffect(AbstractEntityCitizen guard, int level) {
        try {
            // Duration is 2 hours — long enough to outlast any raid or war.
            int durationTicks = 20 * 60 * 120;

            MobEffectInstance resistanceEffect = new MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE,
                    durationTicks,
                    level - 1, // Effect levels are 0-based, but config is 1-based
                    false, // ambient
                    true, // visible
                    true // show icon
            );

            guard.addEffect(resistanceEffect);

        } catch (Exception e) {
            LOGGER.warn("Failed to apply resistance effect to guard {}: {}",
                    guard.getUUID(), e.getMessage());
        }
    }

    /**
     * Remove resistance effect from a specific guard
     * 
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
     * 
     * @param building The building to check
     * @return true if it's a guard building (guard tower, barracks, etc.)
     */
    private static boolean isGuardBuilding(IBuilding building) {
        if (building == null)
            return false;

        String displayName = building.getBuildingDisplayName();
        if (displayName != null) {
            String lowerName = displayName.toLowerCase();
            if (lowerName.contains("guard") || lowerName.contains("barracks") ||
                    lowerName.contains("combat") || lowerName.contains("archery")) {
                return true;
            }
        }

        String className = building.getClass().getName().toLowerCase();
        return className.contains("guard") || className.contains("barracks") ||
                className.contains("combat") || className.contains("archery");
    }

    // ==== WAR-SPECIFIC METHODS ====

    /**
     * Apply resistance effects to all guards in a colony when a war starts
     * 
     * @param colony The colony involved in war
     */
    public static void applyResistanceToGuardsForWar(IColony colony) {
        if (!TaxConfig.isGuardResistanceDuringRaidsEnabled()) {
            return;
        }

        int baseResistanceLevel = TaxConfig.getGuardResistanceLevel();
        int upgradeBonus = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager.getDefenseLevelBonus(colony.getID());
        int resistanceLevel = baseResistanceLevel + upgradeBonus;
        if (resistanceLevel <= 0) {
            return;
        }

        Integer colonyId = colony.getID();
        Set<UUID> affectedGuards = new HashSet<>();

        try {
            for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
                if (isGuardBuilding(building)) {
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

            colonyWarGuardEffects.put(colonyId, affectedGuards);

            if (!affectedGuards.isEmpty()) {
                String entityType = TaxConfig.APPLY_RESISTANCE_TO_CITIZENS.get() ? "guards and citizens" : "guards";
                if (TaxConfig.isDebugLogging()) {
                    LOGGER.info("Applied Resistance {} effect to {} {} in colony '{}' during war",
                            resistanceLevel, affectedGuards.size(), entityType, colony.getName());
                }
            }

        } catch (Exception e) {
            LOGGER.error("Failed to apply resistance effects to guards in colony '{}' during war: {}",
                    colony.getName(), e.getMessage());
        }
    }

    /**
     * Remove resistance effects from all guards in a colony when a war ends
     *
     * @param colony The colony where the war ended
     */
    public static void removeResistanceFromGuardsForWar(IColony colony) {
        // AUDIT FIX (D2 M10): no config-flag gate here. Removal must run whenever the tracking
        // map has entries, otherwise toggling the feature off mid-war strands the buff forever.
        Integer colonyId = colony.getID();
        Set<UUID> affectedGuards = colonyWarGuardEffects.get(colonyId);

        if (affectedGuards == null || affectedGuards.isEmpty()) {
            return;
        }

        int removedCount = 0;

        try {
            for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
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
            colonyWarGuardEffects.remove(colonyId);

            if (removedCount > 0) {
                if (TaxConfig.isDebugLogging()) {
                    LOGGER.info("Removed Resistance effects from {} guards in colony '{}' after war",
                            removedCount, colony.getName());
                }
            }

        } catch (Exception e) {
            LOGGER.error("Failed to remove resistance effects from guards in colony '{}' after war: {}",
                    colony.getName(), e.getMessage());
        }
    }

    // ==== RAID-SPECIFIC METHODS (for backwards compatibility) ====

    /**
     * Apply resistance effects to all guards in a colony when a raid starts
     *
     * @param colony The colony under raid
     */
    public static void applyResistanceToGuardsForRaid(IColony colony) {
        if (!TaxConfig.isGuardResistanceDuringRaidsEnabled()) {
            return;
        }

        int baseResistanceLevel = TaxConfig.getGuardResistanceLevel();
        int upgradeBonus = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager.getDefenseLevelBonus(colony.getID());
        int resistanceLevel = baseResistanceLevel + upgradeBonus;
        if (resistanceLevel <= 0) {
            return;
        }

        Integer colonyId = colony.getID();
        Set<UUID> affectedGuards = new HashSet<>();

        try {
            for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
                if (isGuardBuilding(building)) {
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

            colonyGuardEffects.put(colonyId, affectedGuards);

            if (!affectedGuards.isEmpty()) {
                String entityType = TaxConfig.APPLY_RESISTANCE_TO_CITIZENS.get() ? "guards and citizens" : "guards";
                if (TaxConfig.isDebugLogging()) {
                    LOGGER.info("Applied Resistance {} effect to {} {} in colony '{}' during raid",
                            resistanceLevel, affectedGuards.size(), entityType, colony.getName());
                }
            }

        } catch (Exception e) {
            LOGGER.error("Failed to apply resistance effects to guards in colony '{}' during raid: {}",
                    colony.getName(), e.getMessage());
        }
    }

    /**
     * Remove resistance effects from all guards in a colony when a raid ends
     *
     * @param colony The colony where the raid ended
     */
    public static void removeResistanceFromGuardsForRaid(IColony colony) {
        // AUDIT FIX (D2 M10): no config-flag gate here. Removal must run whenever the tracking
        // map has entries, otherwise toggling the feature off mid-raid strands the buff forever.
        Integer colonyId = colony.getID();
        Set<UUID> affectedGuards = colonyGuardEffects.get(colonyId);

        if (affectedGuards == null || affectedGuards.isEmpty()) {
            return;
        }

        int removedCount = 0;

        try {
            for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
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
            colonyGuardEffects.remove(colonyId);

            if (removedCount > 0) {
                if (TaxConfig.isDebugLogging()) {
                    LOGGER.info("Removed Resistance effects from {} guards in colony '{}' after raid",
                            removedCount, colony.getName());
                }
            }

        } catch (Exception e) {
            LOGGER.error("Failed to remove resistance effects from guards in colony '{}' after raid: {}",
                    colony.getName(), e.getMessage());
        }
    }

    /**
     * Clear all in-memory resistance tracking. Call on server shutdown.
     */
    public static void emergencyCleanup() {
        colonyGuardEffects.clear();
        colonyWarGuardEffects.clear();
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Guard resistance effect tracking cleared");
        }
    }

    /**
     * Get the number of colonies currently tracked for guard resistance effects
     * 
     * @return Number of colonies with active guard resistance effects
     */
    public static int getTrackedColoniesCount() {
        return colonyGuardEffects.size() + colonyWarGuardEffects.size();
    }
}
