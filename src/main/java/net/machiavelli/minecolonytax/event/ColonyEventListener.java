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

    // Track which colonies have received the Guard Tower tax boost
    private static final Map<Integer, Boolean> guardTowerBoostApplied = new HashMap<>();

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

            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                int currentLevel = building.getBuildingLevel();
                String buildingType = building.getBuildingDisplayName();

                // Count Guard Towers
                if ("Guard Tower".equalsIgnoreCase(buildingType)) {
                    guardTowerCount++;
                }

                // Check if the building is new or has been upgraded
                if (!buildingLevels.containsKey(building) || buildingLevels.get(building) < currentLevel) {
                    LOGGER.info("Detected new or upgraded building: {} at level {} in colony {}", buildingType, currentLevel, colony.getName());

                    // Calculate and update tax if below maximum limit
                    int maxTax = TaxConfig.getMaxTaxRevenue();
                    int currentTax = TaxManager.getStoredTaxForColony(colony);
                    if (currentTax < maxTax) {
                        double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
                        double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType) * currentLevel;
                        int generatedTax = (int) (baseTax + upgradeTax);

                        TaxManager.incrementTaxRevenue(colony, generatedTax);
                    } else {
                        LOGGER.info("Colony {} has reached the maximum tax revenue limit ({}).", colony.getName(), maxTax);
                    }

                    // Update the cached building level
                    buildingLevels.put(building, currentLevel);
                }
            }

            // Apply Tax Boost if 5+ Guard Towers Exist (Only Once Per Colony, and configurable)
            int requiredGuardTowers = TaxConfig.getRequiredGuardTowersForBoost();
            double boostPercentage = TaxConfig.getGuardTowerTaxBoostPercentage();

            // Continuously apply Tax Boost if Guard Towers meet the threshold
            if (guardTowerCount >= requiredGuardTowers) {
                for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                    String buildingType = building.getBuildingDisplayName();

                    // Skip maintenance buildings
                    if (buildingType.equalsIgnoreCase("Warehouse") || buildingType.equalsIgnoreCase("Builder Hut")) {
                        continue;
                    }

                    int currentTax = TaxManager.getStoredTaxForColony(colony);
                    int boostedTax = (int) ((TaxConfig.getBaseTaxForBuilding(buildingType) +
                            TaxConfig.getUpgradeTaxForBuilding(buildingType) * building.getBuildingLevel()) * boostPercentage);

                    TaxManager.incrementTaxRevenue(colony, boostedTax);
                }
                LOGGER.info("Continuously applying {}% tax boost to colony {} for having {}+ Guard Towers.", boostPercentage * 100, colony.getName(), requiredGuardTowers);
            }
            }
    }
}
