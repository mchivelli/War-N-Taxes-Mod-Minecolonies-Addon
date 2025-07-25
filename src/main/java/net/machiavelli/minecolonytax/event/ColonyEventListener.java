package net.machiavelli.minecolonytax.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.minecraftforge.event.TickEvent;
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

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // Increment the tick counter and run the check only at the defined interval.
        tickCounter++;
        if (tickCounter % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        List<IColony> colonies = IColonyManager.getInstance().getAllColonies();

        for (IColony colony : colonies) {
            int colonyId = colony.getID();
            Map<IBuilding, Integer> buildingLevels = colonyBuildingLevels.computeIfAbsent(colonyId, k -> new HashMap<>());

            int guardTowerCount = 0;
            int newOrUpgradedBuildingsCount = 0;
            int totalTaxGenerated = 0;
            boolean maxTaxReached = false;

            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                int currentLevel = building.getBuildingLevel();
                String buildingType = building.getBuildingDisplayName();

                // Count Guard Towers
                if (isGuardTower(building)) {
                    guardTowerCount++;
                }

                // Check if the building is new or has been upgraded
                if (!buildingLevels.containsKey(building) || buildingLevels.get(building) < currentLevel) {
                    newOrUpgradedBuildingsCount++;

                    // Calculate and update tax if below maximum limit
                    int maxTax = TaxConfig.getMaxTaxRevenue();
                    int currentTax = TaxManager.getStoredTaxForColony(colony);
                    if (currentTax < maxTax) {
                        double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
                        double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType) * currentLevel;
                        int generatedTax = (int) (baseTax + upgradeTax);

                        TaxManager.incrementTaxRevenue(colony, generatedTax);
                        totalTaxGenerated += generatedTax;
                        
                        // Check if we've now reached the max tax limit
                        if (TaxManager.getStoredTaxForColony(colony) >= maxTax) {
                            maxTaxReached = true;
                        }
                    }

                    // Update the cached building level
                    buildingLevels.put(building, currentLevel);
                }
            }

            // Condensed logging: Only log summary if buildings were processed and logging is enabled
            if (newOrUpgradedBuildingsCount > 0 && TaxConfig.showColonyInitializationLogs()) {
                LOGGER.info("Colony '{}': Processed {} buildings, generated {} tax (Guards: {}, Max Tax: {})", 
                           colony.getName(), newOrUpgradedBuildingsCount, totalTaxGenerated, guardTowerCount, 
                           maxTaxReached ? "REACHED" : "Not Reached");
            }
            
            // Guard tower boost is now properly implemented in TaxManager.generateTaxesForAllColonies()
            // This ensures it's applied consistently with the main tax generation cycle
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
}
