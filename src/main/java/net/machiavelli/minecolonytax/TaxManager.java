package net.machiavelli.minecolonytax;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber
public class TaxManager {

    private static final Map<Integer, Integer> colonyTaxMap = new HashMap<>();
    private static final Logger LOGGER = LogManager.getLogger(TaxManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Integer, Integer> colonyTaxData = new HashMap<>();
    private static final String TAX_DATA_FILE = "config/colonyTaxData.json";
    private static MinecraftServer serverInstance;
    // Set of colony IDs for which tax claims are frozen
    private static final Set<Integer> FROZEN_COLONIES = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> DISABLED_GENERATION = ConcurrentHashMap.newKeySet();
    // Tick interval for generating taxes (1 hour)
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

        // If the colony's tax is frozen, do not allow claiming.
        if (FROZEN_COLONIES.contains(colonyId)) {
            LOGGER.info("Tax claims for colony {} are currently frozen.", colony.getName());
            return 0;
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

    public static void deductColonyTax(IColony colony, double percentage) {
        int currentTax = colonyTaxMap.getOrDefault(colony.getID(), 0);
        int deduction = (int)(currentTax * percentage);
        colonyTaxMap.put(colony.getID(), currentTax - deduction);
        LOGGER.info("Deducted {} tax as penalty from colony {}", deduction, colony.getName());
        saveTaxData();
    }

    // Generate taxes for all colonies
    public static void generateTaxesForAllColonies() {
        if (serverInstance != null) {
            serverInstance.getAllLevels().forEach(world -> {
                IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
                colonyManager.getColonies(world).forEach(colony -> {
                    int colonyId = colony.getID();
                    if (isGenerationDisabled(colonyId)) {
                        LOGGER.debug("Skipping tax generation for disabled colony {}", colonyId);
                        return;
                    }
                    int totalGeneratedTax = 0;
                    int totalMaintenance = 0;

                    for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                        if (building.getBuildingLevel() > 0 && building.isBuilt()) {
                            String buildingType = building.getBuildingDisplayName();
                            int buildingLevel = building.getBuildingLevel();

                            // Generate Tax Income
                            double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
                            double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType) * buildingLevel;
                            int generatedTax = (int) (baseTax + upgradeTax);
                            totalGeneratedTax += generatedTax;
                            incrementTaxRevenue(colony, generatedTax);

                            // Deduct Maintenance Cost
                            double baseMaintenance = TaxConfig.getBaseMaintenanceForBuilding(buildingType);
                            double upgradeMaintenance = TaxConfig.getUpgradeMaintenanceForBuilding(buildingType) * buildingLevel;
                            int totalMaintenanceForBuilding = (int) (baseMaintenance + upgradeMaintenance);
                            totalMaintenance += totalMaintenanceForBuilding;

                            if (totalMaintenanceForBuilding > 0) {
                                int currentTax = colonyTaxMap.getOrDefault(colonyId, 0);
                                int newTax = currentTax - totalMaintenanceForBuilding;
                                int debtLimit = TaxConfig.getDebtLimit();
                                if (debtLimit > 0 && newTax < -debtLimit) {
                                    newTax = -debtLimit;  // Do not allow tax to drop below negative debt limit
                                }
                                colonyTaxMap.put(colonyId, newTax);
                                LOGGER.info("Deducted {} maintenance for {} (level {}) in colony {}. New tax: {}",
                                        totalMaintenanceForBuilding, buildingType, buildingLevel, colony.getName(), newTax);
                            }


                        }
                    }

                    // Notify colony managers
                    IPermissions permissions = colony.getPermissions();
                    Set<ColonyPlayer> officers = permissions.getPlayersByRank(permissions.getRankOfficer());
                    UUID ownerId = permissions.getOwner();

                    Set<UUID> recipients = officers.stream()
                            .map(ColonyPlayer::getID)
                            .collect(Collectors.toSet());
                    recipients.add(ownerId);

                    for (UUID playerId : recipients) {
                        ServerPlayer player = serverInstance.getPlayerList().getPlayer(playerId);
                        if (player != null) {
                            player.sendSystemMessage(Component.translatable(
                                    "message.minecolonytax.tax_report",
                                    colony.getName(),
                                    totalGeneratedTax,
                                    totalMaintenance
                            ));
                        }

                    }

                    if (totalMaintenance > totalGeneratedTax) {
                        for (UUID playerId : recipients) {
                            ServerPlayer player = serverInstance.getPlayerList().getPlayer(playerId);
                            if (player != null) {
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.debt_warning",
                                        colony.getName(), totalMaintenance, totalGeneratedTax));
                            }
                        }
                    }
                });
            });
            saveTaxData();
        }
    }

    // Update tax when a new building is constructed or upgraded
    public static void updateTaxForBuilding(IColony colony, IBuilding building, int currentLevel) {
        if (currentLevel > 0 && building.isBuilt()) {
            String buildingType = building.getBuildingDisplayName();
            double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
            double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType) * currentLevel;
            int totalTax = (int) (baseTax + upgradeTax);
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
                    colonyTaxMap.putAll(loadedData);
                    LOGGER.info("Loaded tax data from file.");
                }
            } catch (IOException e) {
                LOGGER.error("Error loading tax data", e);
            }
        } else {
            LOGGER.info("No existing tax data file found at: {}", taxFile.getAbsolutePath());
        }
    }

    // --- New method added to freeze tax claims for a colony for a given number of hours ---
    public static void freezeColonyTax(int colonyId, int freezeHours) {
        FROZEN_COLONIES.add(colonyId);
        LOGGER.info("Colony {} tax is frozen for {} hours.", colonyId, freezeHours);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                FROZEN_COLONIES.remove(colonyId);
                LOGGER.info("Colony {} tax freeze expired.", colonyId);
            }
        }, TimeUnit.HOURS.toMillis(freezeHours));
    }

    /**
     * Applies a payment to reduce the colony's tax debt.
     * @param colony The colony to receive the payment.
     * @param amount The payment amount.
     * @return The effective payment applied (which may be lower than the requested amount if it would exceed the debt).
     */
    public static int payTaxDebt(IColony colony, int amount) {
        int colonyId = colony.getID();
        int currentTax = colonyTaxMap.getOrDefault(colonyId, 0);
        if (currentTax >= 0) {
            return 0; // No debt to pay.
        }
        int effectivePayment = Math.min(amount, -currentTax);
        colonyTaxMap.put(colonyId, currentTax + effectivePayment);
        LOGGER.info("Colony {} debt paid by {}. New tax value: {}", colony.getName(), effectivePayment, colonyTaxMap.get(colonyId));
        saveTaxData();
        return effectivePayment;
    }

    public static void disableTaxGeneration(int colonyId) {
        DISABLED_GENERATION.add(colonyId);
        LOGGER.info("Tax generation disabled for colony {}", colonyId);
    }

    /** Re‑enable tax generation for a colony **/
    public static void enableTaxGeneration(int colonyId) {
        DISABLED_GENERATION.remove(colonyId);
        LOGGER.info("Tax generation enabled for colony {}", colonyId);
    }

    /** Check if generation is disabled **/
    public static boolean isGenerationDisabled(int colonyId) {
        return DISABLED_GENERATION.contains(colonyId);
    }

}
