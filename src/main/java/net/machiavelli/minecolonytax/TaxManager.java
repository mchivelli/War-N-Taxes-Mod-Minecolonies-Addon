package net.machiavelli.minecolonytax;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.common.MinecraftForge;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber
public class TaxManager {

    private static final Map<Integer, Integer> colonyTaxMap = new HashMap<>();
    private static final Logger LOGGER = LogManager.getLogger(TaxManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Integer, Integer> colonyTaxData = new HashMap<>();
    private static final String TAX_DATA_FILE = "config/colonyTaxData.json";
    private static MinecraftServer serverInstance;

    // Tick interval for generating taxes (default 1 hour)
    private static long ticksPerInterval = 72000L;

    // Initialize Tax Manager
    public static void initialize(MinecraftServer server) {
        LOGGER.info("Initializing Tax Manager...");
        serverInstance = server;

        // Load tax data on server start
        loadTaxData(server);

        // Register to handle ticks for generating tax
        ticksPerInterval = TaxConfig.getTaxIntervalInMinutes() * 1200L; // Calculate interval based on config
        MinecraftForge.EVENT_BUS.register(new TickEventHandler());
    }

    // Save tax data before the server stops
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping. Saving tax data...");
        saveTaxData();  // Save tax data when server stops
    }

    // Inner class for handling tick events
    public static class TickEventHandler {
        private static int tickCount = 0;  // Keep track of ticks

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                tickCount++;
                if (tickCount >= ticksPerInterval) {
                    TaxManager.generateTaxesForAllColonies();
                    tickCount = 0;  // Reset the tick counter
                }
            }
        }
    }

    public static int claimTax(IColony colony, int amount) {
        int colonyId = colony.getID();
        int storedTax = colonyTaxMap.getOrDefault(colonyId, 0);

        if (storedTax <= 0) {
            LOGGER.debug("No tax available to claim for colony {}", colony.getName());
            return 0;  // No tax to claim
        }

        int claimedAmount;
        if (amount == -1) {
            // Claim all tax
            claimedAmount = storedTax;
            colonyTaxMap.put(colonyId, 0);  // Reset tax to zero
        } else {
            // Claim a specific amount
            claimedAmount = Math.min(amount, storedTax);  // Ensure the claimed amount does not exceed the stored tax
            colonyTaxMap.put(colonyId, storedTax - claimedAmount);  // Deduct the claimed amount
        }

        LOGGER.info("Claimed {} tax for colony {}", claimedAmount, colony.getName());
        saveTaxData();  // Save changes to file

        return claimedAmount;
    }

    // Overload for backward compatibility
    public static int claimTax(IColony colony) {
        return claimTax(colony, -1);  // Claim all tax by default
    }


    // Method to get stored tax for a colony
    public static int getStoredTaxForColony(IColony colony) {
        return colonyTaxMap.getOrDefault(colony.getID(), 0);
    }

    // Method to increment tax revenue for a colony
    public static void incrementTaxRevenue(IColony colony, int taxAmount) {
        int currentTax = colonyTaxMap.getOrDefault(colony.getID(), 0);
        int maxTax = TaxConfig.getMaxTaxRevenue();

        if (currentTax < maxTax) {
            int newTax = Math.min(currentTax + taxAmount, maxTax);
            colonyTaxMap.put(colony.getID(), newTax);

            LOGGER.info("Updated tax revenue for colony {} to {} (Max: {}).", colony.getName(), newTax, maxTax);
        } else {
            LOGGER.info("Tax revenue for colony {} has reached the maximum limit ({}).", colony.getName(), maxTax);
        }
    }


    // Generate taxes for all colonies
    public static void generateTaxesForAllColonies() {
        if (serverInstance != null) {
            serverInstance.getAllLevels().forEach(world -> {
                IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                colonyManager.getColonies(world).forEach(colony -> {
                    for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                        if (building.getBuildingLevel() > 0 && building.isBuilt()) {
                            String buildingType = building.getBuildingDisplayName();
                            int buildingLevel = building.getBuildingLevel();

                            // Generate Tax Income
                            double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
                            double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType) * buildingLevel;
                            int generatedTax = (int) (baseTax + upgradeTax);
                            incrementTaxRevenue(colony, generatedTax);

                            // Deduct Maintenance Cost
                            double baseMaintenance = TaxConfig.getBaseMaintenanceForBuilding(buildingType);
                            double upgradeMaintenance = TaxConfig.getUpgradeMaintenanceForBuilding(buildingType) * buildingLevel;
                            int totalMaintenance = (int) (baseMaintenance + upgradeMaintenance);

                            if (totalMaintenance > 0) {
                                int colonyId = colony.getID();
                                int currentTax = colonyTaxMap.getOrDefault(colonyId, 0);
                                int newTax = currentTax - totalMaintenance;
                                colonyTaxMap.put(colonyId, newTax);
                                LOGGER.info("Deducted {} maintenance for {} (level {}) in colony {}. New tax: {}",
                                        totalMaintenance, buildingType, buildingLevel, colony.getName(), newTax);
                            }
                        }
                    }
                });
            });

            saveTaxData();
        }
    }

    // Method to update the tax when a new building is constructed or upgraded
    public static void updateTaxForBuilding(IColony colony, IBuilding building, int currentLevel) {
        if (currentLevel > 0 && building.isBuilt()) {
            String buildingType = building.getBuildingDisplayName(); // Get the display name of the building

            double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
            double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType) * currentLevel;

            int totalTax = (int) (baseTax + upgradeTax);

            // Update tax for the colony
            incrementTaxRevenue(colony, totalTax);

            if (totalTax > 0) {
                LOGGER.debug("Generated {} tax for building {} (level {}) in colony {}", totalTax, buildingType, currentLevel, colony.getName());
            }
        }
    }

    // Save tax data to a JSON file
    private static void saveTaxData() {
        try (FileWriter writer = new FileWriter(TAX_DATA_FILE)) {
            GSON.toJson(colonyTaxMap, writer);
            LOGGER.info("Saved tax data to file.");
        } catch (IOException e) {
            LOGGER.error("Error saving tax data", e);
        }
    }

    // Load tax data from a JSON file
    private static void loadTaxData(MinecraftServer server) {
        File taxFile = new File(server.getServerDirectory(), TAX_DATA_FILE);
        if (taxFile.exists()) {
            try (FileReader reader = new FileReader(taxFile)) {
                Type taxDataType = new TypeToken<Map<Integer, Integer>>() {}.getType();
                Map<Integer, Integer> loadedData = GSON.fromJson(reader, taxDataType);
                if (loadedData != null) {
                    colonyTaxMap.putAll(loadedData); // Load saved tax data
                    LOGGER.info("Loaded tax data from file.");
                }
            } catch (IOException e) {
                LOGGER.error("Error loading tax data", e);
            }
        } else {
            LOGGER.info("No existing tax data file found at: {}", taxFile.getAbsolutePath());
        }
    }
}
