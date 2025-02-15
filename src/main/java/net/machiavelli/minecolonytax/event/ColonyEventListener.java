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

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        List<IColony> colonies = IColonyManager.getInstance().getAllColonies();

        for (IColony colony : colonies) {
            int colonyId = colony.getID();
            Map<IBuilding, Integer> buildingLevels = colonyBuildingLevels.computeIfAbsent(colonyId, k -> new HashMap<>());

            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                int currentLevel = building.getBuildingLevel();
                String buildingType = building.getBuildingDisplayName();

                // Check if the building is new or upgraded
                if (!buildingLevels.containsKey(building) || buildingLevels.get(building) < currentLevel) {
                    LOGGER.info("Detected new or upgraded building: {} at level {} in colony {}", buildingType, currentLevel, colony.getName());

                    // Calculate tax only if below max limit
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

                    buildingLevels.put(building, currentLevel); // Update building level tracking
                }
            }
        }
    }
}
