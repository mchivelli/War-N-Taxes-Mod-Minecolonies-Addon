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
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.economy.RaidPenaltyManager;
import net.machiavelli.minecolonytax.economy.WarChestManager;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicy;
import net.machiavelli.minecolonytax.events.random.RandomEventManager;

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
    private static final String TAX_DATA_FILE = "config/warntax/colonyTaxData.json";
    private static final String TAX_TIMESTAMP_FILE = "config/warntax/lastTaxGeneration.json";
    private static MinecraftServer serverInstance;
    // Set of colony IDs for which tax claims are frozen
    private static final Set<Integer> FROZEN_COLONIES = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> DISABLED_GENERATION = ConcurrentHashMap.newKeySet();
    // Single instance of the tick event handler to prevent multiple registrations
    private static TickEventHandler tickEventHandler = null;
    // Last tax generation timestamp (persistent across server restarts)
    private static long lastTaxGenerationTime = 0L;

    // Initialize Tax Manager
    public static void initialize(MinecraftServer server) {
        if (TaxConfig.showTaxGenerationLogs()) {
            LOGGER.info("Initializing Tax Manager...");
        }
        serverInstance = server;

        // Load tax data on server start
        loadTaxData(server);

        // Load last tax generation timestamp
        loadLastTaxGenerationTime();

        // Unregister any existing handler to prevent multiple registrations
        if (tickEventHandler != null) {
            MinecraftForge.EVENT_BUS.unregister(tickEventHandler);
        }

        // Register to handle ticks for generating tax (now timestamp-based)
        tickEventHandler = new TickEventHandler();
        MinecraftForge.EVENT_BUS.register(tickEventHandler);
    }

    // Save tax data before the server stops
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping. Saving tax data and timestamp...");
        saveTaxData(); // Save tax data when server stops
        saveLastTaxGenerationTime(); // CRITICAL: Save timestamp on shutdown

        // End all claiming raids
        try {
            net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.endAllClaimingRaids();
        } catch (Exception e) {
            LOGGER.error("Error ending claiming raids on shutdown", e);
        }
    }

    // Inner class for handling tick events (now timestamp-based)
    public static class TickEventHandler {
        private int tickCount = 0; // Check every 20 ticks (1 second) for performance
        private int abandonmentTickCount = 0; // Check abandonment every hour (72000 ticks)
        private int cleanupTickCount = 0; // Check [abandoned] cleanup every 30 minutes (36000 ticks)
        private int nullOwnerCheckCount = 0; // 🚨 Check null owners every 5 seconds (100 ticks) - AGGRESSIVE!

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                tickCount++;
                abandonmentTickCount++;
                cleanupTickCount++;
                nullOwnerCheckCount++;

                // 🚨 AUTOMATIC: Check for null owners every 5 seconds - AGGRESSIVE PROTECTION!
                if (nullOwnerCheckCount >= 100) { // 100 ticks = 5 seconds - MUCH MORE FREQUENT!
                    nullOwnerCheckCount = 0;
                    try {
                        net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
                    } catch (Exception e) {
                        LOGGER.error("Failed automatic null owner fix", e);
                    }
                }

                // Check tax generation every second instead of every tick (performance
                // optimization)
                if (tickCount >= 20) { // 20 ticks = 1 second
                    tickCount = 0;
                    checkForTaxGeneration();
                }

                // Check colony abandonment every hour (72000 ticks = 1 hour)
                if (abandonmentTickCount >= 72000) {
                    abandonmentTickCount = 0;
                    checkColonyAbandonment();
                }

                // Run proactive [abandoned] cleanup every 30 minutes (36000 ticks = 30 minutes)
                if (cleanupTickCount >= 36000) {
                    cleanupTickCount = 0;
                    runPeriodicAbandonedCleanup();
                }

                // Update claiming raids every second
                if (tickCount == 0) {
                    net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.updateClaimingRaids();
                }

                // Check for officer changes in abandoned colonies every 5 minutes (6000 ticks)
                // This detects admin commands that add officers/owners to abandoned colonies
                if (abandonmentTickCount % 6000 == 0) {
                    checkForOfficerChangesInAbandonedColonies();
                }
            }
        }

        /**
         * Run periodic cleanup of [abandoned] entries to prevent corruption.
         */
        private void runPeriodicAbandonedCleanup() {
            try {
                LOGGER.debug("Running periodic [abandoned] entries cleanup...");
                net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.cleanupAllColoniesAbandonedEntries();
            } catch (Exception e) {
                LOGGER.error("Error during periodic [abandoned] cleanup", e);
            }
        }

        /**
         * Check all abandoned colonies for new officers/owners (detects admin
         * commands).
         */
        private void checkForOfficerChangesInAbandonedColonies() {
            try {
                if (serverInstance == null)
                    return;

                // Get all colonies and check if any abandoned ones have new officers
                for (com.minecolonies.api.colony.IColony colony : com.minecolonies.api.IMinecoloniesAPI.getInstance()
                        .getColonyManager().getAllColonies()) {
                    try {
                        // Only check colonies that are currently marked as abandoned
                        if (net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.isColonyAbandoned(colony)) {
                            net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.checkForNewOfficers(colony);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error checking colony {} for officer changes: {}", colony.getName(),
                                e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error during officer change check", e);
            }
        }

        private void checkForTaxGeneration() {
            long currentTime = System.currentTimeMillis();
            long intervalMs = Math.max(60000L, TaxConfig.getTaxIntervalInMinutes() * 60L * 1000L); // Minimum 1 minute

            // Handle clock changes/future timestamps (system clock moved backward)
            if (lastTaxGenerationTime > currentTime + 60000L) { // More than 1 minute in future
                if (TaxConfig.showTaxGenerationLogs()) {
                    LOGGER.warn(
                            "Last tax generation timestamp is in the future! Clock may have changed. Resetting timestamp.");
                }
                lastTaxGenerationTime = currentTime - intervalMs; // Force immediate generation
                saveLastTaxGenerationTime();
            }

            // If this is the first time or enough time has passed, generate taxes
            if (lastTaxGenerationTime == 0L || (currentTime - lastTaxGenerationTime) >= intervalMs) {
                if (TaxConfig.showTaxGenerationLogs()) {
                    if (lastTaxGenerationTime == 0L) {
                        LOGGER.info("First tax generation triggered (interval: {} minutes)",
                                TaxConfig.getTaxIntervalInMinutes());
                    } else {
                        long elapsedMinutes = (currentTime - lastTaxGenerationTime) / (60L * 1000L);
                        LOGGER.info("Tax generation triggered after {} minutes elapsed (interval: {} minutes)",
                                elapsedMinutes, TaxConfig.getTaxIntervalInMinutes());
                    }
                }

                lastTaxGenerationTime = currentTime;
                saveLastTaxGenerationTime(); // Persist timestamp immediately
                TaxManager.generateTaxesForAllColonies();
            }
        }

        private void checkColonyAbandonment() {
            if (serverInstance != null) {
                try {
                    net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager
                            .checkColoniesForAbandonment(serverInstance);
                } catch (Exception e) {
                    LOGGER.error("Error during colony abandonment check", e);
                }
            }
        }
    }

    public static int claimTax(IColony colony, int amount) {

        int colonyId = colony.getID();
        int storedTax = colonyTaxMap.getOrDefault(colonyId, 0);

        LOGGER.info("[TAX DEBUG] Colony {}: Stored tax = {}, Requested amount = {}", colony.getName(), storedTax,
                amount);

        if (storedTax <= 0) {
            LOGGER.info("[TAX DEBUG] No tax available to claim for colony {} (stored: {})", colony.getName(),
                    storedTax);
            return 0; // No tax to claim
        }

        // If the colony's tax is frozen, do not allow claiming.
        if (FROZEN_COLONIES.contains(colonyId)) {
            if (TaxConfig.showTaxGenerationLogs()) {
                LOGGER.info("Tax claims for colony {} are currently frozen.", colony.getName());
            }
            return 0;
        }

        // Check if the colony is currently being raided - if so, block tax claiming
        if (RaidManager.getActiveRaidForColony(colonyId) != null) {
            if (TaxConfig.showTaxGenerationLogs()) {
                LOGGER.info("Tax claims blocked for colony {} - colony is currently being raided.", colony.getName());
            }
            return 0;
        }

        // Check if the colony is currently in a war (either as defender or attacker) -
        // if so, block tax claiming
        WarData activeWar = WarSystem.ACTIVE_WARS.get(colonyId);
        if (activeWar == null) {
            for (WarData wd : WarSystem.ACTIVE_WARS.values()) {
                if (wd.getAttackerColony() != null && wd.getAttackerColony().getID() == colonyId) {
                    activeWar = wd;
                    break;
                }
            }
        }
        if (activeWar != null) {
            if (TaxConfig.showTaxGenerationLogs()) {
                LOGGER.info("Tax claims blocked for colony {} - colony is currently at war.", colony.getName());
            }
            return 0;
        }

        int claimedAmount;
        if (amount == -1) {
            // Claim all tax
            claimedAmount = storedTax;
            colonyTaxMap.put(colonyId, 0); // Reset tax to zero
        } else {
            // Claim a specific amount
            claimedAmount = Math.min(amount, storedTax); // Ensure the claimed amount does not exceed the stored tax
            colonyTaxMap.put(colonyId, storedTax - claimedAmount); // Deduct the claimed amount
        }

        if (TaxConfig.showTaxGenerationLogs()) {
            LOGGER.info("Claimed {} tax for colony {}", claimedAmount, colony.getName());
        }
        saveTaxData(true); // Log save for important operations like claiming tax

        return claimedAmount;
    }

    // Overload for backward compatibility
    public static int claimTax(IColony colony) {
        return claimTax(colony, -1); // Claim all tax by default
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
            // Removed per-building logging - will be aggregated in
            // generateTaxesForAllColonies
        } else {
            // Only log max limit reached once per colony per iteration
            // This will be handled in generateTaxesForAllColonies method
        }
    }

    public static void deductColonyTax(IColony colony, double percentage) {
        int currentTax = colonyTaxMap.getOrDefault(colony.getID(), 0);
        int deduction = (int) (currentTax * percentage);
        colonyTaxMap.put(colony.getID(), currentTax - deduction);
        if (TaxConfig.showTaxGenerationLogs()) {
            LOGGER.info("Deducted {} tax as penalty from colony {}", deduction, colony.getName());
        }
        saveTaxData();
    }

    // Helper method to adjust tax delta
    public static void adjustTax(IColony colony, int delta) {
        int id = colony.getID();
        int current = colonyTaxMap.getOrDefault(id, 0);
        colonyTaxMap.put(id, current + delta);
    }

    /**
     * Calculate the average happiness of adult citizens in a colony.
     * 
     * @param colony The colony to calculate happiness for
     * @return Average happiness (0.0 - 10.0), or 5.0 if no adult citizens or
     *         happiness unavailable
     */
    public static double calculateColonyAverageHappiness(IColony colony) {
        try {
            double totalHappiness = 0.0;
            int adultCitizenCount = 0;

            for (com.minecolonies.api.colony.ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
                if (citizen != null && !citizen.isChild()) {
                    try {
                        // Access happiness handler through the citizen data
                        com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenHappinessHandler happinessHandler = ((com.minecolonies.core.colony.CitizenData) citizen)
                                .getCitizenHappinessHandler();

                        if (happinessHandler != null) {
                            double happiness = happinessHandler.getHappiness(colony, citizen);
                            totalHappiness += happiness;
                            adultCitizenCount++;
                        }
                    } catch (Exception e) {
                        // If we can't get happiness for this citizen, skip them
                        if (TaxConfig.showTaxGenerationLogs()) {
                            LOGGER.debug("Could not get happiness for citizen {} in colony {}: {}",
                                    citizen.getName(), colony.getName(), e.getMessage());
                        }
                    }
                }
            }

            if (adultCitizenCount > 0) {
                double baseHappiness = totalHappiness / adultCitizenCount;
                double eventModifier = RandomEventManager.getHappinessModifier(colony.getID());
                return Math.max(0.0, Math.min(10.0, baseHappiness + eventModifier));
            } else {
                return 5.0; // Default to neutral happiness if no adult citizens
            }
        } catch (Exception e) {
            if (TaxConfig.showTaxGenerationLogs()) {
                LOGGER.warn("Error calculating colony happiness for {}: {}", colony.getName(), e.getMessage());
            }
            return 5.0; // Default to neutral happiness on error
        }
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

                    // Check colony inactivity
                    if (TaxConfig.isColonyInactivityTaxPauseEnabled()) {
                        int lastContactHours = colony.getLastContactInHours();
                        int inactivityThreshold = TaxConfig.getColonyInactivityHoursThreshold();

                        if (lastContactHours >= inactivityThreshold) {
                            if (TaxConfig.showTaxGenerationLogs()) {
                                LOGGER.info(
                                        "Skipping tax generation for inactive colony {} - Last contact: {} hours ago (threshold: {} hours)",
                                        colony.getName(), lastContactHours, inactivityThreshold);
                            }
                            return;
                        }
                    }

                    // Track colony-level statistics
                    int startingBalance = colonyTaxMap.getOrDefault(colonyId, 0); // Balance BEFORE this cycle
                    int totalGeneratedTax = 0;
                    int totalBaseTax = 0; // Tax before happiness modifier
                    int totalMaintenance = 0;
                    int buildingCount = 0;
                    int maxLimitHits = 0;
                    boolean hasDebt = false;
                    int finalTaxBalance;
                    int guardTowerCount = 0;
                    int requiredGuardTowers = TaxConfig.getRequiredGuardTowersForBoost();
                    int warChestDeposit = 0; // Track war chest auto-deposit
                    int factionPoolContribution = 0; // Track faction pool contribution

                    // Calculate colony happiness for tax modifier
                    double colonyAvgHappiness = calculateColonyAverageHappiness(colony);

                    double happinessMultiplier = TaxConfig.calculateHappinessTaxMultiplier(colonyAvgHappiness);

                    // Apply Raid Penalty Multiplier
                    double raidPenaltyMultiplier = RaidPenaltyManager.getTaxMultiplier(colonyId);
                    if (raidPenaltyMultiplier < 1.0 && TaxConfig.showTaxGenerationLogs()) {
                        LOGGER.info("Colony {} has active raid penalty. Tax reduced by {:.0f}%",
                                colony.getName(), (1.0 - raidPenaltyMultiplier) * 100);
                    }

                    // Apply War Exhaustion Multiplier (includes at-war, recovery, and reparations)
                    double warExhaustionMultiplier = net.machiavelli.minecolonytax.economy.WarExhaustionManager
                            .getTaxMultiplier(colonyId);
                    if (warExhaustionMultiplier < 1.0 && TaxConfig.showTaxGenerationLogs()) {
                        LOGGER.info("Colony {} has war exhaustion/reparations. Tax reduced by {:.0f}%",
                                colony.getName(), (1.0 - warExhaustionMultiplier) * 100);
                    }

                    // Apply Tax Policy Multiplier
                    double taxPolicyMultiplier = TaxPolicyManager.getRevenueMultiplier(colonyId);
                    TaxPolicy activePolicy = TaxPolicyManager.getPolicy(colonyId);
                    if (taxPolicyMultiplier != 1.0 && TaxConfig.showTaxGenerationLogs()) {
                        String policyEffect = taxPolicyMultiplier > 1.0 ? "increased" : "reduced";
                        LOGGER.info("Colony {} has {} tax policy. Tax {} by {:.0f}%",
                                colony.getName(), activePolicy.name(), policyEffect,
                                Math.abs(1.0 - taxPolicyMultiplier) * 100);
                    }

                    for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                        if (building.getBuildingLevel() > 0 && building.isBuilt()) {
                            buildingCount++;
                            String buildingType = building.getClass().getName();
                            int buildingLevel = building.getBuildingLevel();

                            // Generate Tax Income
                            double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
                            double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType) * buildingLevel;
                            double rawTax = baseTax + upgradeTax;

                            // Apply happiness modifier to tax generation
                            double taxWithHappiness = rawTax * happinessMultiplier;

                            // Apply raid penalty, war exhaustion, tax policy, and random event modifiers
                            double randomEventMultiplier = RandomEventManager.getTaxMultiplier(colonyId);
                            int generatedTax = (int) (taxWithHappiness * raidPenaltyMultiplier
                                    * warExhaustionMultiplier * taxPolicyMultiplier
                                    * randomEventMultiplier);

                            totalBaseTax += (int) rawTax; // Track base tax before happiness modifier
                            totalGeneratedTax += generatedTax; // Track actual modified tax for reporting

                            // Check if we hit max limit for this building's tax
                            int currentTax = colonyTaxMap.getOrDefault(colonyId, 0);
                            int maxTax = TaxConfig.getMaxTaxRevenue();
                            if (currentTax >= maxTax) {
                                maxLimitHits++;
                            } else {
                                incrementTaxRevenue(colony, generatedTax);
                            }

                            // Deduct Maintenance Cost
                            double baseMaintenance = TaxConfig.getBaseMaintenanceForBuilding(buildingType);
                            double upgradeMaintenance = TaxConfig.getUpgradeMaintenanceForBuilding(buildingType)
                                    * buildingLevel;
                            int totalMaintenanceForBuilding = (int) (baseMaintenance + upgradeMaintenance);
                            totalMaintenance += totalMaintenanceForBuilding;

                            if (totalMaintenanceForBuilding > 0) {
                                currentTax = colonyTaxMap.getOrDefault(colonyId, 0);
                                int newTax = currentTax - totalMaintenanceForBuilding;
                                int debtLimit = TaxConfig.getDebtLimit();
                                if (debtLimit > 0 && newTax < -debtLimit) {
                                    newTax = -debtLimit; // Do not allow tax to drop below negative debt limit
                                    hasDebt = true;
                                }
                                colonyTaxMap.put(colonyId, newTax);
                                // Removed per-building maintenance logging
                            }
                        }
                    }

                    finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);

                    // --- Espionage: Deduct pending spy costs ---
                    if (TaxConfig.isSpySystemEnabled()) {
                        int pendingCost = net.machiavelli.minecolonytax.espionage.SpyManager
                                .consumePendingCost(colonyId);
                        if (pendingCost > 0) {
                            adjustTax(colony, -pendingCost);
                            finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);

                            if (TaxConfig.showTaxGenerationLogs()) {
                                LOGGER.info("Deducted {} pending spy cost from colony {}", pendingCost, colonyId);
                            }
                        }

                        // Apply sabotage reduction if active
                        double sabotageReduction = net.machiavelli.minecolonytax.espionage.SpyManager
                                .getSabotageReduction(colonyId);
                        if (sabotageReduction > 0) {
                            int reduction = (int) (totalGeneratedTax * sabotageReduction);
                            adjustTax(colony, -reduction);
                            finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);
                            net.machiavelli.minecolonytax.espionage.SpyManager.clearSabotageEffect(colonyId); // One-time
                                                                                                              // effect

                            if (TaxConfig.showTaxGenerationLogs()) {
                                LOGGER.info("Sabotage reduced tax by {} for colony {}", reduction, colonyId);
                            }
                        }
                    }

                    // --- Guard Tower Tax Boost Processing ---
                    for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                        if (building.getBuildingLevel() > 0 && building.isBuilt()) {
                            // Count guard towers using the same logic as WarSystem
                            String displayName = building.getBuildingDisplayName();
                            String className = building.getClass().getName().toLowerCase();
                            String toString = building.toString().toLowerCase();

                            if ((displayName != null && "Guard Tower".equalsIgnoreCase(displayName)) ||
                                    className.contains("guardtower") ||
                                    toString.contains("guardtower") ||
                                    toString.contains("guard_tower")) {
                                guardTowerCount++;
                            }
                        }
                    }

                    // Apply guard tower boost if requirements are met
                    if (guardTowerCount >= requiredGuardTowers) {
                        double boostPercentage = TaxConfig.getGuardTowerTaxBoostPercentage();
                        int boostAmount = (int) (totalGeneratedTax * boostPercentage);

                        if (boostAmount > 0) {
                            incrementTaxRevenue(colony, boostAmount);
                            finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);

                            if (TaxConfig.showTaxGenerationLogs()) {
                                LOGGER.info(
                                        "Applied guard tower tax boost to colony {}: {} tax ({} guard towers, {}% boost)",
                                        colony.getName(), boostAmount, guardTowerCount, (int) (boostPercentage * 100));
                            }
                        }
                    }

                    // --- Vassal tribute processing ---
                    int tributePaid = net.machiavelli.minecolonytax.vassalization.VassalManager.handleTaxIncome(colony,
                            totalGeneratedTax);
                    if (tributePaid > 0) {
                        // Recalculate final balance after tribute deduction
                        finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);
                    }

                    // --- War Chest Auto-Deposit ---
                    if (TaxConfig.isWarChestEnabled() && TaxConfig.getWarChestAutoDepositPercent() > 0) {
                        double autoDepositPercent = TaxConfig.getWarChestAutoDepositPercent();
                        int depositAmount = (int) (totalGeneratedTax * autoDepositPercent);

                        if (depositAmount > 0) {
                            // Deduct from tax balance and add to war chest
                            // Use adjustTax with negative amount to deduct without triggering debt logging
                            // improperly here
                            adjustTax(colony, -depositAmount);
                            WarChestManager.addToWarChest(colonyId, depositAmount);
                            warChestDeposit = depositAmount; // Track for reporting

                            finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0); // Update final balance for
                                                                                      // logging

                            if (TaxConfig.showTaxGenerationLogs()) {
                                LOGGER.info("Auto-deposited {} to War Chest for colony {} ({}%)",
                                        depositAmount, colony.getName(), (int) (autoDepositPercent * 100));
                            }
                        }
                    }

                    // --- Faction Shared Tax Pool ---
                    if (TaxConfig.isFactionSystemEnabled() && TaxConfig.isSharedTaxPoolEnabled()) {
                        double divertedAmount = net.machiavelli.minecolonytax.faction.FactionManager
                                .processFactionTax(colonyId, totalGeneratedTax);
                        if (divertedAmount > 0) {
                            adjustTax(colony, -(int) divertedAmount);
                            factionPoolContribution = (int) divertedAmount; // Track for reporting
                            finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);

                            if (TaxConfig.showTaxGenerationLogs()) {
                                LOGGER.info("Contributed {} to Faction Shared Pool for colony {}", (int) divertedAmount,
                                        colony.getName());
                            }
                        }
                    }

                    // Consolidated logging per colony
                    if (TaxConfig.showTaxGenerationLogs()) {
                        int netChangeThisCycle = finalTaxBalance - startingBalance;
                        LOGGER.info(
                                "Tax cycle completed for colony {} - Buildings: {}, Base: {}, Generated: {}, Maintenance: {}, WarChest: {}, Starting: {}, Net Change: {}, Final: {}",
                                colony.getName(), buildingCount, totalBaseTax, totalGeneratedTax, totalMaintenance,
                                warChestDeposit, startingBalance, netChangeThisCycle, finalTaxBalance);

                        // Log happiness impact if enabled
                        if (TaxConfig.isHappinessTaxModifierEnabled()) {
                            int happinessTaxImpact = totalGeneratedTax - totalBaseTax;
                            String impactType = happinessTaxImpact > 0 ? "BONUS"
                                    : (happinessTaxImpact < 0 ? "PENALTY" : "NEUTRAL");
                            LOGGER.info("Colony {} - Happiness: {}/10.0, Tax Impact: {} coins ({})",
                                    colonyId, String.format("%.1f", colonyAvgHappiness), happinessTaxImpact,
                                    impactType);
                        }

                        // Log calculation breakdown for clarity
                        LOGGER.info(
                                "Colony {} - Calculation: {} (starting) + {} (base) + {} (happiness) - {} (maintenance) - {} (war chest) = {} (final)",
                                colony.getName(), startingBalance, totalBaseTax, (totalGeneratedTax - totalBaseTax),
                                totalMaintenance, warChestDeposit, finalTaxBalance);

                        if (maxLimitHits > 0) {
                            LOGGER.info(
                                    "Colony {} reached tax revenue maximum limit on {} building calculations (Max: {})",
                                    colony.getName(), maxLimitHits, TaxConfig.getMaxTaxRevenue());
                        }

                        if (hasDebt) {
                            LOGGER.info("Colony {} hit debt limit during maintenance deductions", colony.getName());
                        }
                    }

                    // Notify colony managers with enhanced tax report
                    IPermissions permissions = colony.getPermissions();
                    Set<ColonyPlayer> officers = permissions.getPlayersByRank(permissions.getRankOfficer());
                    UUID ownerId = permissions.getOwner();

                    Set<UUID> recipients = officers.stream()
                            .map(ColonyPlayer::getID)
                            .collect(Collectors.toSet());
                    recipients.add(ownerId);

                    // Send enhanced tax report to players
                    for (UUID playerId : recipients) {
                        ServerPlayer player = serverInstance.getPlayerList().getPlayer(playerId);
                        if (player != null) {
                            // Send main tax report header with box drawing
                            player.sendSystemMessage(Component.literal("╔═══════════════════════════════════╗")
                                    .withStyle(net.minecraft.ChatFormatting.GRAY));
                            player.sendSystemMessage(Component.literal("║")
                                    .append(Component
                                            .translatable("message.minecolonytax.tax_report_header", colony.getName())
                                            .withStyle(net.minecraft.ChatFormatting.GOLD))
                                    .append(Component.literal("║").withStyle(net.minecraft.ChatFormatting.GRAY)));
                            player.sendSystemMessage(Component.literal("╚═══════════════════════════════════╝")
                                    .withStyle(net.minecraft.ChatFormatting.GRAY));
                            player.sendSystemMessage(Component.literal(""));

                            // Income Section Header
                            player.sendSystemMessage(Component.translatable(
                                    "message.minecolonytax.tax_report_income_header")
                                    .withStyle(net.minecraft.ChatFormatting.WHITE));

                            // Show base revenue (before happiness modifier)
                            if (TaxConfig.isHappinessTaxModifierEnabled()) {
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_base_revenue",
                                        buildingCount,
                                        totalBaseTax).withStyle(net.minecraft.ChatFormatting.GRAY));

                                // Show happiness modifier
                                int happinessTaxImpact = totalGeneratedTax - totalBaseTax;
                                net.minecraft.ChatFormatting happinessColor = happinessTaxImpact > 0
                                        ? net.minecraft.ChatFormatting.GREEN
                                        : (happinessTaxImpact < 0 ? net.minecraft.ChatFormatting.RED
                                                : net.minecraft.ChatFormatting.YELLOW);
                                String impactSign = happinessTaxImpact > 0 ? "+" : "";
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_happiness_modifier",
                                        impactSign + happinessTaxImpact).withStyle(happinessColor));

                                // Show subseparator before total
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_subseparator")
                                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));

                                // Show total after happiness
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_total_revenue",
                                        totalGeneratedTax).withStyle(net.minecraft.ChatFormatting.WHITE));
                            } else {
                                // If happiness is disabled, just show total
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_generation",
                                        buildingCount,
                                        totalGeneratedTax).withStyle(net.minecraft.ChatFormatting.GREEN));
                            }

                            player.sendSystemMessage(Component.literal(""));

                            // Expenses Section Header
                            player.sendSystemMessage(Component.translatable(
                                    "message.minecolonytax.tax_report_expenses_header")
                                    .withStyle(net.minecraft.ChatFormatting.WHITE));

                            // Show maintenance info
                            if (totalMaintenance > 0) {
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_maintenance",
                                        totalMaintenance).withStyle(net.minecraft.ChatFormatting.GRAY));
                            }

                            // Show tribute paid if applicable
                            if (tributePaid > 0) {
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_tribute",
                                        tributePaid).withStyle(net.minecraft.ChatFormatting.GRAY));
                            }

                            // Show war chest auto-deposit if applicable
                            if (warChestDeposit > 0) {
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_war_chest",
                                        warChestDeposit).withStyle(net.minecraft.ChatFormatting.GRAY));
                            }

                            // Show faction pool contribution if applicable
                            if (factionPoolContribution > 0) {
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_faction_pool",
                                        factionPoolContribution).withStyle(net.minecraft.ChatFormatting.GRAY));
                            }

                            // Show tax policy if not NORMAL (optional info, not in expenses)
                            if (TaxConfig.isTaxPoliciesEnabled() && activePolicy != TaxPolicy.NORMAL) {
                                net.minecraft.ChatFormatting policyColor = taxPolicyMultiplier > 1.0
                                        ? net.minecraft.ChatFormatting.GREEN
                                        : net.minecraft.ChatFormatting.RED;
                                String policyImpactSign = taxPolicyMultiplier > 1.0 ? "+" : "";
                                int policyPercentage = (int) ((taxPolicyMultiplier - 1.0) * 100);
                                player.sendSystemMessage(Component.literal(
                                        "§7Tax Policy: " + activePolicy.getColorCode() + activePolicy.getDisplayName() +
                                                " §7(" + policyImpactSign + policyPercentage + "% revenue)")
                                        .withStyle(policyColor));
                            }

                            // Show guard tower boost if applicable (shown as note, not expense)
                            if (guardTowerCount >= requiredGuardTowers) {
                                double boostPercentage = TaxConfig.getGuardTowerTaxBoostPercentage();
                                int boostAmount = (int) (totalGeneratedTax * boostPercentage);
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_guard_boost",
                                        boostAmount).withStyle(net.minecraft.ChatFormatting.AQUA));
                            }

                            // Send separator line
                            player.sendSystemMessage(Component.literal(""));
                            player.sendSystemMessage(Component.translatable(
                                    "message.minecolonytax.tax_report_separator")
                                    .withStyle(net.minecraft.ChatFormatting.GRAY));

                            // Calculate net change this cycle
                            int netChangeThisCycle = finalTaxBalance - startingBalance;

                            // Show net change for this cycle
                            net.minecraft.ChatFormatting netChangeColor = netChangeThisCycle >= 0
                                    ? net.minecraft.ChatFormatting.GREEN
                                    : net.minecraft.ChatFormatting.RED;
                            String netChangeSign = netChangeThisCycle >= 0 ? "+" : "";
                            player.sendSystemMessage(Component.translatable(
                                    "message.minecolonytax.tax_report_net_change",
                                    netChangeSign + netChangeThisCycle).withStyle(netChangeColor));

                            // Send current total balance
                            net.minecraft.ChatFormatting balanceColor;
                            String statusKey;
                            if (finalTaxBalance < 0) {
                                balanceColor = net.minecraft.ChatFormatting.RED;
                                statusKey = "message.minecolonytax.tax_report_status_debt";
                            } else if (finalTaxBalance >= TaxConfig.getMaxTaxRevenue() * 0.9) {
                                balanceColor = net.minecraft.ChatFormatting.YELLOW;
                                statusKey = "message.minecolonytax.tax_report_status_near_max";
                            } else {
                                balanceColor = net.minecraft.ChatFormatting.WHITE;
                                statusKey = "message.minecolonytax.tax_report_status_healthy";
                            }

                            player.sendSystemMessage(Component.translatable(
                                    "message.minecolonytax.tax_report_total_stored",
                                    finalTaxBalance).withStyle(net.minecraft.ChatFormatting.WHITE));

                            player.sendSystemMessage(Component.translatable(
                                    statusKey,
                                    finalTaxBalance < 0 ? Math.abs(finalTaxBalance) : TaxConfig.getMaxTaxRevenue())
                                    .withStyle(balanceColor));

                            // Send footer
                            player.sendSystemMessage(Component.translatable(
                                    "message.minecolonytax.tax_report_separator")
                                    .withStyle(net.minecraft.ChatFormatting.GRAY));
                        }
                    }

                    // Send debt warning if applicable (simplified)
                    if (totalMaintenance > totalGeneratedTax) {
                        for (UUID playerId : recipients) {
                            ServerPlayer player = serverInstance.getPlayerList().getPlayer(playerId);
                            if (player != null) {
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.debt_warning",
                                        totalMaintenance, totalGeneratedTax)
                                        .withStyle(net.minecraft.ChatFormatting.RED));
                            }
                        }
                    }

                    // Trigger random events after tax cycle
                    if (TaxConfig.isRandomEventsEnabled()) {
                        RandomEventManager.onTaxCycle(colony);
                    }
                });
            });
            // Only log save operation once per full tax cycle
            saveTaxData();
            if (TaxConfig.showTaxGenerationLogs()) {
                LOGGER.info("Tax generation cycle completed for all colonies");
            }
        }
    }

    // Update tax when a new building is constructed or upgraded
    public static void updateTaxForBuilding(IColony colony, IBuilding building, int currentLevel) {
        if (currentLevel > 0 && building.isBuilt()) {
            String buildingType = building.getClass().getName();
            double baseTax = TaxConfig.getBaseTaxForBuilding(buildingType);
            double upgradeTax = TaxConfig.getUpgradeTaxForBuilding(buildingType) * currentLevel;
            int totalTax = (int) (baseTax + upgradeTax);
            incrementTaxRevenue(colony, totalTax);
            if (totalTax > 0) {
                LOGGER.debug("Generated {} tax for building {} (level {}) in colony {}", totalTax, buildingType,
                        currentLevel, colony.getName());
            }
        }
    }

    // Save tax data to a JSON file
    private static void saveTaxData() {
        saveTaxData(false); // Default to not log
    }

    // Overloaded method with logging control
    private static void saveTaxData(boolean logSave) {
        File file = new File(TAX_DATA_FILE);
        file.getParentFile().mkdirs(); // Ensure the directory exists
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(colonyTaxMap, writer);
            if (logSave && TaxConfig.showTaxGenerationLogs()) {
                LOGGER.info("Saved tax data to file.");
            }
        } catch (IOException e) {
            LOGGER.error("Error saving tax data", e);
        }
    }

    // Load tax data from a JSON file
    private static void loadTaxData(MinecraftServer server) {
        File taxFile = new File(TAX_DATA_FILE);
        if (taxFile.exists()) {
            try (FileReader reader = new FileReader(taxFile)) {
                Type taxDataType = new TypeToken<Map<Integer, Integer>>() {
                }.getType();
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

    // --- New method added to freeze tax claims for a colony for a given number of
    // hours ---
    public static void freezeColonyTax(int colonyId, int freezeHours) {
        FROZEN_COLONIES.add(colonyId);
        if (TaxConfig.showTaxGenerationLogs()) {
            LOGGER.info("Colony {} tax is frozen for {} hours.", colonyId, freezeHours);
        }
        // Use a daemon timer so it won't prevent server shutdown
        new Timer(true).schedule(new TimerTask() {
            @Override
            public void run() {
                FROZEN_COLONIES.remove(colonyId);
                if (TaxConfig.showTaxGenerationLogs()) {
                    LOGGER.info("Colony {} tax freeze expired.", colonyId);
                }
            }
        }, TimeUnit.HOURS.toMillis(freezeHours));
    }

    /**
     * Applies a payment to reduce the colony's tax debt or add to its balance.
     * 
     * @param colony The colony to receive the payment.
     * @param amount The payment amount.
     * @return The effective payment applied (full amount is always used).
     */
    public static int payTaxDebt(IColony colony, int amount) {
        int colonyId = colony.getID();
        int currentTax = colonyTaxMap.getOrDefault(colonyId, 0);
        // Apply the full amount regardless of current balance
        colonyTaxMap.put(colonyId, currentTax + amount);
        if (TaxConfig.showTaxGenerationLogs()) {
            LOGGER.info("Colony {} tax payment of {}. New tax value: {}", colony.getName(), amount,
                    colonyTaxMap.get(colonyId));
        }
        saveTaxData(true);
        return amount;
    }

    public static void disableTaxGeneration(int colonyId) {
        DISABLED_GENERATION.add(colonyId);
        if (TaxConfig.showTaxGenerationLogs()) {
            LOGGER.info("Tax generation disabled for colony {}", colonyId);
        }
    }

    /** Re‑enable tax generation for a colony **/
    public static void enableTaxGeneration(int colonyId) {
        DISABLED_GENERATION.remove(colonyId);
        if (TaxConfig.showTaxGenerationLogs()) {
            LOGGER.info("Tax generation enabled for colony {}", colonyId);
        }
    }

    /** Check if generation is disabled **/
    public static boolean isGenerationDisabled(int colonyId) {
        return DISABLED_GENERATION.contains(colonyId);
    }

    // Load last tax generation timestamp from persistent file
    private static void loadLastTaxGenerationTime() {
        File timestampFile = new File(TAX_TIMESTAMP_FILE);
        if (timestampFile.exists()) {
            try (FileReader reader = new FileReader(timestampFile)) {
                Type timestampType = new TypeToken<Map<String, Long>>() {
                }.getType();
                Map<String, Long> timestampData = GSON.fromJson(reader, timestampType);
                if (timestampData != null && timestampData.containsKey("lastTaxGeneration")) {
                    long loadedTimestamp = timestampData.get("lastTaxGeneration");

                    // Validate timestamp isn't corrupted or unrealistic
                    long currentTime = System.currentTimeMillis();
                    long oneYearAgo = currentTime - (365L * 24L * 60L * 60L * 1000L); // 1 year ago
                    long oneYearFromNow = currentTime + (365L * 24L * 60L * 60L * 1000L); // 1 year in future

                    if (loadedTimestamp < oneYearAgo || loadedTimestamp > oneYearFromNow) {
                        LOGGER.warn("Loaded timestamp appears corrupted ({}), starting fresh",
                                new java.util.Date(loadedTimestamp));
                        lastTaxGenerationTime = 0L;
                    } else {
                        lastTaxGenerationTime = loadedTimestamp;
                        if (TaxConfig.showTaxGenerationLogs()) {
                            long minutesAgo = (currentTime - lastTaxGenerationTime) / (60L * 1000L);
                            LOGGER.info("Loaded last tax generation timestamp: {} minutes ago ({})",
                                    minutesAgo, new java.util.Date(lastTaxGenerationTime));
                        }
                    }
                } else {
                    LOGGER.warn("Timestamp file exists but is malformed, starting fresh");
                    lastTaxGenerationTime = 0L;
                }
            } catch (Exception e) {
                LOGGER.warn("Error loading tax generation timestamp, will start fresh: {}", e.getMessage());
                lastTaxGenerationTime = 0L;
                // Delete corrupted file
                try {
                    timestampFile.delete();
                } catch (Exception ex) {
                    LOGGER.debug("Could not delete corrupted timestamp file: {}", ex.getMessage());
                }
            }
        } else {
            if (TaxConfig.showTaxGenerationLogs()) {
                LOGGER.info("No existing tax generation timestamp found, starting fresh");
            }
            lastTaxGenerationTime = 0L;
        }
    }

    // Save last tax generation timestamp to persistent file
    private static void saveLastTaxGenerationTime() {
        // Skip saving if timestamp is invalid
        if (lastTaxGenerationTime <= 0) {
            if (TaxConfig.showTaxGenerationLogs()) {
                LOGGER.debug("Skipping save of invalid timestamp: {}", lastTaxGenerationTime);
            }
            return;
        }

        File timestampFile = new File(TAX_TIMESTAMP_FILE);
        timestampFile.getParentFile().mkdirs(); // Ensure directory exists

        try (FileWriter writer = new FileWriter(timestampFile)) {
            Map<String, Long> timestampData = new HashMap<>();
            timestampData.put("lastTaxGeneration", lastTaxGenerationTime);
            timestampData.put("version", 1L); // Version for future compatibility
            GSON.toJson(timestampData, writer);

            if (TaxConfig.showTaxGenerationLogs()) {
                LOGGER.debug("Saved tax generation timestamp: {} ({})", lastTaxGenerationTime,
                        new java.util.Date(lastTaxGenerationTime));
            }
        } catch (IOException e) {
            LOGGER.error(
                    "CRITICAL: Failed to save tax generation timestamp! Tax intervals may be affected after restart: {}",
                    e.getMessage());
        }
    }

}
