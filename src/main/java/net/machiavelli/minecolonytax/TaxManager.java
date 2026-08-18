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
import com.minecolonies.api.entity.citizen.happiness.ExpirationBasedHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.common.MinecraftForge;
import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
import net.machiavelli.minecolonytax.raid.RaidManager;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.economy.RaidPenaltyManager;
import net.machiavelli.minecolonytax.economy.TreasuryManager;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicyManager;
import net.machiavelli.minecolonytax.economy.policy.TaxPolicy;
import net.machiavelli.minecolonytax.events.random.RandomEventManager;
import net.machiavelli.minecolonytax.util.TickScheduler;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
    // Tracks how many consecutive tax cycles a colony has been in debt
    private static final Map<Integer, Integer> CONSECUTIVE_DEBT_CYCLES = new ConcurrentHashMap<>();
    // Single instance of the tick event handler to prevent multiple registrations
    private static TickEventHandler tickEventHandler = null;
    // Last tax generation timestamp (persistent across server restarts)
    private static long lastTaxGenerationTime = 0L;

    public static void initialize(MinecraftServer server) {
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Initializing Tax Manager...");
        }
        serverInstance = server;

        loadTaxData(server);

        loadLastTaxGenerationTime();

        if (tickEventHandler != null) {
            MinecraftForge.EVENT_BUS.unregister(tickEventHandler);
        }

        tickEventHandler = new TickEventHandler();
        MinecraftForge.EVENT_BUS.register(tickEventHandler);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping. Saving tax data and timestamp...");
        saveTaxData(); // Save tax data when server stops
        saveLastTaxGenerationTime(); // CRITICAL: Save timestamp on shutdown

        try {
            net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.endAllClaimingRaids();
        } catch (Exception e) {
            LOGGER.error("Error ending claiming raids on shutdown", e);
        }
    }

    public static class TickEventHandler {
        private int tickCount = 0; // Check every 20 ticks (1 second) for performance
        private int abandonmentTickCount = 0; // Check abandonment every hour (72000 ticks)
        private int cleanupTickCount = 0; // Check [abandoned] cleanup every 30 minutes (36000 ticks)
        private int nullOwnerCheckCount = 0;

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                tickCount++;
                abandonmentTickCount++;
                cleanupTickCount++;
                nullOwnerCheckCount++;

                // Null-owner safety net. The real null-owner repair runs at startup
                // (MineColonyTax.onServerStarting + deferred passes); this is only a
                // periodic catch for owners that go null at runtime (rare). Throttled
                // to every 5 minutes — a per-5s all-colony permission scan is a needless
                // steady-state cost at hundreds of colonies (optimization audit C2).
                if (nullOwnerCheckCount >= 6000) { // 6000 ticks = 5 minutes
                    nullOwnerCheckCount = 0;
                    // Gated by the master abandonment switch (4.x world-brick fix): when the
                    // abandonment system is off the mod performs NO automatic writes to
                    // MineColonies owner/permission state.
                    if (TaxConfig.isColonyAbandonmentSystemEnabled()) {
                        try {
                            net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager.emergencyFixAllNullOwners();
                        } catch (Exception e) {
                            LOGGER.error("Failed automatic null owner fix", e);
                        }
                    }
                }

                if (tickCount >= 20) { // 20 ticks = 1 second
                    tickCount = 0;
                    checkForTaxGeneration();

                    // Espionage tick — check for mission completions (runs on main server thread)
                    if (TaxConfig.isSpySystemEnabled()) {
                        try {
                            net.machiavelli.minecolonytax.espionage.SpyManager.tick();
                        } catch (Exception e) {
                            LOGGER.error("Error during SpyManager tick", e);
                        }
                    }
                }

                // Check colony abandonment every hour (72000 ticks = 1 hour)
                if (abandonmentTickCount >= 72000) {
                    abandonmentTickCount = 0;
                    checkColonyAbandonment();
                }

                // Run proactive [abandoned] cleanup every 30 minutes (36000 ticks = 30 minutes)
                if (cleanupTickCount >= 36000) {
                    cleanupTickCount = 0;
                    if (TaxConfig.isColonyAbandonmentSystemEnabled()) {
                        runPeriodicAbandonedCleanup();
                    }
                }

                // Update claiming raids every second
                if (tickCount == 0) {
                    net.machiavelli.minecolonytax.abandon.ColonyClaimingRaidManager.updateClaimingRaids();
                }

                // Check for officer changes in abandoned colonies every 5 minutes (6000 ticks)
                // This detects admin commands that add officers/owners to abandoned colonies
                if (abandonmentTickCount % 6000 == 0 && TaxConfig.isColonyAbandonmentSystemEnabled()) {
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
                if (TaxConfig.isNormalLogging()) {
                    LOGGER.warn(
                            "Last tax generation timestamp is in the future! Clock may have changed. Resetting timestamp.");
                }
                lastTaxGenerationTime = currentTime - intervalMs; // Force immediate generation
                saveLastTaxGenerationTime();
            }

            // If this is the first time or enough time has passed, generate taxes
            if (lastTaxGenerationTime == 0L || (currentTime - lastTaxGenerationTime) >= intervalMs) {
                if (TaxConfig.isNormalLogging()) {
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

        if (TaxConfig.isDebugLogging()) {
            LOGGER.info("[TAX DEBUG] Colony {}: Stored tax = {}, Requested amount = {}", colony.getName(), storedTax,
                    amount);
        }

        if (storedTax <= 0) {
            if (TaxConfig.isDebugLogging()) {
                LOGGER.info("[TAX DEBUG] No tax available to claim for colony {} (stored: {})", colony.getName(),
                        storedTax);
            }
            return 0; // No tax to claim
        }

        // If the colony's tax is frozen, do not allow claiming.
        if (FROZEN_COLONIES.contains(colonyId)) {
            if (TaxConfig.isDebugLogging()) {
                LOGGER.info("Tax claims for colony {} are currently frozen.", colony.getName());
            }
            return 0;
        }

        // Check if the colony is currently being raided - if so, block tax claiming
        if (RaidManager.getActiveRaidForColony(colonyId) != null) {
            if (TaxConfig.isDebugLogging()) {
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
            if (TaxConfig.isDebugLogging()) {
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
            // SECURITY (audit Codex CRIT-1): the previous code did Math.min(amount, storedTax) which, for a
            // negative attacker-supplied amount, would return the negative amount and then compute
            // storedTax - (negative) = storedTax + |amount|, inflating the ledger. Defensively clamp to
            // [0, storedTax]. Sentinel values (-1, -2) are handled by the packet layer and the branch above.
            int clampedRequest = (amount > 0) ? amount : 0;
            claimedAmount = Math.max(0, Math.min(clampedRequest, storedTax));
            colonyTaxMap.put(colonyId, storedTax - claimedAmount); // Deduct the claimed amount
        }

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Claimed {} tax for colony {}", claimedAmount, colony.getName());
        }
        saveTaxData(true); // Log save for important operations like claiming tax

        return claimedAmount;
    }

    public static int claimTax(IColony colony) {
        return claimTax(colony, -1); // Claim all tax by default
    }

    public static int getStoredTaxForColony(IColony colony) {
        return colonyTaxMap.getOrDefault(colony.getID(), 0);
    }

    public static int getStoredTaxForColonyId(int colonyId) {
        return colonyTaxMap.getOrDefault(colonyId, 0);
    }

    public static int getConsecutiveDebtCycles(int colonyId) {
        return CONSECUTIVE_DEBT_CYCLES.getOrDefault(colonyId, 0);
    }

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
        // A percentage penalty only ever takes from positive holdings. With a negative balance
        // (debt) the product is negative and "currentTax - deduction" would RAISE the balance,
        // so a stalemate/defeat penalty used to pay down the loser's debt instead of hurting.
        int deduction = (int) (Math.max(0, currentTax) * Math.max(0.0, percentage));
        colonyTaxMap.put(colony.getID(), currentTax - deduction);
        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("Deducted {} tax as penalty from colony {}", deduction, colony.getName());
        }
        saveTaxData();
    }

    /**
     * Deduct up to {@code amount} from a colony's tax balance WITHOUT driving it past the configured
     * debt limit, and return how much was actually taken. The raid-steal path already respects
     * {@code DebtLimit} (never below {@code -DebtLimit}, or below 0 when the limit is 0 = debt
     * disabled); the raid penalty and defense-reward deductions used raw {@link #adjustTax}, which had
     * no floor — so repeated raider deaths could drive the raider colony arbitrarily negative. Callers
     * should credit downstream rewards with the RETURNED amount so the transfer conserves currency.
     */
    public static int deductRespectingDebtLimit(IColony colony, int amount) {
        if (colony == null || amount <= 0) return 0;
        int id = colony.getID();
        int current = colonyTaxMap.getOrDefault(id, 0);
        int debtLimit = TaxConfig.getDebtLimit();
        int floor = debtLimit > 0 ? -debtLimit : 0; // lowest balance allowed
        int deductible = Math.max(0, current - floor);
        int actual = Math.min(amount, deductible);
        colonyTaxMap.put(id, current - actual);
        return actual;
    }

    public static void adjustTax(IColony colony, int delta) {
        int id = colony.getID();
        int current = colonyTaxMap.getOrDefault(id, 0);
        // SECURITY (audit HIGH): defend against signed-int overflow when the colony's tax ledger is repeatedly
        // adjusted by large positive or negative deltas (e.g. via repeated vassal tribute or war reparations).
        // Use long-widened arithmetic then clamp to int range before storing.
        long widened = (long) current + (long) delta;
        int clamped;
        if (widened > Integer.MAX_VALUE) {
            clamped = Integer.MAX_VALUE;
        } else if (widened < Integer.MIN_VALUE) {
            clamped = Integer.MIN_VALUE;
        } else {
            clamped = (int) widened;
        }
        colonyTaxMap.put(id, clamped);
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
                        // Access happiness handler through the citizen data interface (no cast needed)
                        com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenHappinessHandler happinessHandler = citizen
                                .getCitizenHappinessHandler();

                        if (happinessHandler != null) {
                            double happiness = happinessHandler.getHappiness(colony, citizen);
                            totalHappiness += happiness;
                            adultCitizenCount++;
                        }
                    } catch (Exception e) {
                        // If we can't get happiness for this citizen, skip them
                        if (TaxConfig.isDebugLogging()) {
                            LOGGER.debug("Could not get happiness for citizen {} in colony {}: {}",
                                    citizen.getName(), colony.getName(), e.getMessage());
                        }
                    }
                }
            }

            if (adultCitizenCount > 0) {
                double baseHappiness = totalHappiness / adultCitizenCount;
                double eventModifier = RandomEventManager.getHappinessModifier(colony.getID());
                double policyModifier = TaxPolicyManager.getHappinessModifier(colony.getID());
                return Math.max(0.0, Math.min(10.0, baseHappiness + eventModifier + policyModifier));
            } else {
                return 5.0; // Default to neutral happiness if no adult citizens
            }
        } catch (Exception e) {
            if (TaxConfig.isDebugLogging()) {
                LOGGER.warn("Error calculating colony happiness for {}: {}", colony.getName(), e.getMessage());
            }
            return 5.0; // Default to neutral happiness on error
        }
    }

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
                            if (TaxConfig.isDebugLogging()) {
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
                    int treasuryDeposit = 0;
                    int factionPoolContribution = 0;
                    int sabotageReductionAmount = 0;

                    // Calculate colony happiness for tax modifier
                    double colonyAvgHappiness = calculateColonyAverageHappiness(colony);

                    double happinessMultiplier = TaxConfig.calculateHappinessTaxMultiplier(colonyAvgHappiness);

                    // Apply Raid Penalty Multiplier
                    double raidPenaltyMultiplier = RaidPenaltyManager.getTaxMultiplier(colonyId);
                    if (raidPenaltyMultiplier < 1.0 && TaxConfig.isDebugLogging()) {
                        // Log4j2 kennt nur {} — Prozentwert vorformatieren (sonst bleibt {:.0f} literal)
                        LOGGER.info("Colony {} has active raid penalty. Tax reduced by {}%",
                                colony.getName(), Math.round((1.0 - raidPenaltyMultiplier) * 100));
                    }

                    // Apply War Exhaustion Multiplier (includes at-war, recovery, and reparations)
                    double warExhaustionMultiplier = net.machiavelli.minecolonytax.economy.WarExhaustionManager
                            .getTaxMultiplier(colonyId);
                    if (warExhaustionMultiplier < 1.0 && TaxConfig.isDebugLogging()) {
                        LOGGER.info("Colony {} has war exhaustion/reparations. Tax reduced by {}%",
                                colony.getName(), Math.round((1.0 - warExhaustionMultiplier) * 100));
                    }

                    // Apply Tax Policy Multiplier
                    double taxPolicyMultiplier = TaxPolicyManager.getRevenueMultiplier(colonyId);
                    TaxPolicy activePolicy = TaxPolicyManager.getPolicy(colonyId);
                    if (taxPolicyMultiplier != 1.0 && TaxConfig.isDebugLogging()) {
                        String policyEffect = taxPolicyMultiplier > 1.0 ? "increased" : "reduced";
                        LOGGER.info("Colony {} has {} tax policy. Tax {} by {}%",
                                colony.getName(), activePolicy.name(), policyEffect,
                                Math.round(Math.abs(1.0 - taxPolicyMultiplier) * 100));
                    }

                    for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
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

                            // Combine all penalties/bonuses and ensure it doesn't drop below the minimum
                            // tax floor
                            double combinedMultiplier = raidPenaltyMultiplier * warExhaustionMultiplier
                                    * taxPolicyMultiplier * randomEventMultiplier;
                            double minMultiplier = TaxConfig.getMinTaxGenerationPercent();
                            if (combinedMultiplier < minMultiplier) {
                                combinedMultiplier = minMultiplier;
                            }

                            int generatedTax = (int) (taxWithHappiness * combinedMultiplier);

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

                    // --- Investment: Tax Efficiency bonus ---
                    if (TaxConfig.isUpgradesEnabled() && totalGeneratedTax > 0) {
                        double efficiencyBonus = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
                                .getTaxEfficiencyBonus(colonyId);
                        if (efficiencyBonus > 0) {
                            int bonusAmount = (int) (totalGeneratedTax * efficiencyBonus);
                            if (bonusAmount > 0) {
                                adjustTax(colony, bonusAmount);
                                totalGeneratedTax += bonusAmount;
                                finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);
                                if (TaxConfig.isDebugLogging())
                                    LOGGER.info("Colony {} tax efficiency bonus: +{}", colonyId, bonusAmount);
                            }
                        }
                    }

                    // --- Espionage: Deduct pending spy costs ---
                    if (TaxConfig.isSpySystemEnabled()) {
                        int pendingCost = net.machiavelli.minecolonytax.espionage.SpyManager
                                .consumePendingCost(colonyId);
                        if (pendingCost > 0) {
                            adjustTax(colony, -pendingCost);
                            finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);

                            if (TaxConfig.isDebugLogging()) {
                                LOGGER.info("Deducted {} pending spy cost from colony {}", pendingCost, colonyId);
                            }
                        }

                        // Apply sabotage reduction if active
                        double sabotageReduction = net.machiavelli.minecolonytax.espionage.SpyManager
                                .getSabotageReduction(colonyId);
                        if (sabotageReduction > 0) {
                            // Cap at net cycle gain so sabotage can't push the colony into debt
                            int netCycleGain = finalTaxBalance - startingBalance;
                            int rawReduction = (int) (totalGeneratedTax * sabotageReduction);
                            int reduction = Math.max(0, Math.min(rawReduction, netCycleGain));
                            adjustTax(colony, -reduction);
                            finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);
                            sabotageReductionAmount = reduction;
                            net.machiavelli.minecolonytax.espionage.SpyManager.clearSabotageEffect(colonyId); // One-time effect
                        }

                        // Apply stolen secrets tax bonus if active (+20%)
                        if (net.machiavelli.minecolonytax.espionage.SpyManager
                                .hasActiveStolenSecretsBuff(colonyId)) {
                            int bonusAmount = (int) (totalGeneratedTax * 0.20);
                            if (bonusAmount > 0) {
                                adjustTax(colony, bonusAmount);
                                finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);

                                if (TaxConfig.isDebugLogging()) {
                                    LOGGER.info("Stolen secrets bonus: +{} tax for colony {} (+20%)",
                                            bonusAmount, colonyId);
                                }
                            }
                        }
                    }

                    // --- Guard Tower Tax Boost Processing ---
                    // Use the cached WarSystem.isGuardTower (identical matching logic) instead of
                    // re-doing building.toString().toLowerCase() per building — that allocation
                    // ran for every building of every colony each tax cycle (audit H8).
                    for (IBuilding building : ColonyBuildingUtil.getBuildings(colony)) {
                        if (building.getBuildingLevel() > 0 && building.isBuilt()
                                && WarSystem.isGuardTower(building)) {
                            guardTowerCount++;
                        }
                    }

                    // Apply guard tower boost linearly per tower, capped at 80%
                    if (guardTowerCount >= requiredGuardTowers) {
                        double perTowerBoost = TaxConfig.getGuardTowerTaxBoostPercentage();

                        // Apply boost ONLY for towers ABOVE the minimum required, OR if required is 1,
                        // apply for all.
                        int effectiveTowers = guardTowerCount;
                        if (requiredGuardTowers > 1) {
                            effectiveTowers = (guardTowerCount - requiredGuardTowers) + 1;
                        }

                        double totalBoostPercentage = effectiveTowers * perTowerBoost;

                        // Cap the maximum boost at 80% to prevent excessive scaling
                        if (totalBoostPercentage > 0.80) {
                            totalBoostPercentage = 0.80;
                        }

                        int boostAmount = (int) (totalGeneratedTax * totalBoostPercentage);

                        if (boostAmount > 0) {
                            incrementTaxRevenue(colony, boostAmount);
                            finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);

                            if (TaxConfig.isDebugLogging()) {
                                LOGGER.info(
                                        "Applied guard tower tax boost to colony {}: {} tax ({} guard towers, {}% boost)",
                                        colony.getName(), boostAmount, guardTowerCount,
                                        (int) (totalBoostPercentage * 100));
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

                    // --- Treasury Auto-Deposit ---
                    if (TaxConfig.isTreasuryEnabled() && TaxConfig.getTreasuryAutoDepositPercent() > 0) {
                        double autoDepositPercent = TaxConfig.getTreasuryAutoDepositPercent();
                        int depositAmount = (int) (totalGeneratedTax * autoDepositPercent);

                        if (depositAmount > 0) {
                            adjustTax(colony, -depositAmount);
                            TreasuryManager.addToTreasury(colonyId, depositAmount);
                            treasuryDeposit = depositAmount;

                            finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);

                            if (TaxConfig.isDebugLogging()) {
                                LOGGER.info("Auto-deposited {} to treasury for colony {} ({}%)",
                                        depositAmount, colony.getName(), (int) (autoDepositPercent * 100));
                            }
                        }
                    }

                    // --- Faction Shared Tax Pool ---
                    if (TaxConfig.isFactionSystemEnabled() && TaxConfig.isSharedTaxPoolEnabled()) {
                        double divertedAmount = net.machiavelli.minecolonytax.faction.FactionManager
                                .processFactionTax(colonyId, totalGeneratedTax);
                        if (divertedAmount > 0) {
                            // MONEY CONSERVATION (release fix, faction-pool branch):
                            // FactionManager.processFactionTax now clamps the diversion to this
                            // colony's available ledger balance BEFORE crediting the pool and returns
                            // that clamped (integer) value, so the pool credit == the debit below and
                            // the colony can never go negative. The min() here is therefore a harmless
                            // defensive floor (it can never reduce the debit below the credited amount).
                            int available = Math.max(0, colonyTaxMap.getOrDefault(colonyId, 0));
                            int debit = Math.min((int) divertedAmount, available);
                            adjustTax(colony, -debit);
                            factionPoolContribution = debit; // Track for reporting (actual debit)
                            finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);

                            if (TaxConfig.isDebugLogging()) {
                                LOGGER.info("Contributed {} to Faction Shared Pool for colony {}", debit,
                                        colony.getName());
                            }
                        }
                    }

                    // --- Occupation Tax Diversion ---
                    if (TaxConfig.isOccupationSystemEnabled()
                            && net.machiavelli.minecolonytax.occupation.OccupationManager.isOccupied(colonyId)) {
                        int diverted = net.machiavelli.minecolonytax.occupation.OccupationManager
                                .processAutomaticOccupationTax(colonyId, totalGeneratedTax);
                        if (diverted > 0) {
                            // MONEY CONSERVATION (release fix C#2): processAutomaticOccupationTax now
                            // clamps the diversion to the occupied colony's available balance BEFORE
                            // crediting the occupier and returns that clamped value, so the debit below
                            // equals the occupier credit exactly. We keep a defensive floor-at-0 clamp
                            // here as a harmless no-op safety net (vassal tribute above could in theory
                            // shrink the balance between calls); it never raises the debit above the
                            // amount already credited.
                            int available = Math.max(0, colonyTaxMap.getOrDefault(colonyId, 0));
                            diverted = Math.min(diverted, available);
                        }
                        if (diverted > 0) {
                            adjustTax(colony, -diverted);
                            finalTaxBalance = colonyTaxMap.getOrDefault(colonyId, 0);
                            if (TaxConfig.isDebugLogging()) {
                                LOGGER.info("Diverted {} occupation tax from colony {} to occupier",
                                        diverted, colony.getName());
                            }
                        }
                    }

                    // Colony activity log entry (always, regardless of log level)
                    {
                        int netChangeThisCycle = finalTaxBalance - startingBalance;
                        StringBuilder taxLogMsg = new StringBuilder();
                        taxLogMsg.append(String.format("Net %+d (%d blds", netChangeThisCycle, buildingCount));
                        if (totalMaintenance > 0)
                            taxLogMsg.append(String.format(", maint -%d", totalMaintenance));
                        if (sabotageReductionAmount > 0)
                            taxLogMsg.append(String.format(", sabotage -%d", sabotageReductionAmount));
                        taxLogMsg.append(")");
                        net.machiavelli.minecolonytax.data.HistoryManager.logWithBalance(
                                colonyId, "TAX", taxLogMsg.toString(), startingBalance, finalTaxBalance);
                    }

                    // Consolidated logging per colony (NORMAL for summary, DEBUG for details)
                    if (TaxConfig.isNormalLogging()) {
                        int netChangeThisCycle = finalTaxBalance - startingBalance;

                        // Keep a compact summary at NORMAL level
                        if (sabotageReductionAmount > 0) {
                            LOGGER.info(
                                    "Tax cycle: Colony {} - Net: {}, Balance: {} ({} blds) [SABOTAGED: -{} coins]",
                                    colony.getName(),
                                    (netChangeThisCycle > 0 ? "+" + netChangeThisCycle : netChangeThisCycle),
                                    finalTaxBalance, buildingCount, sabotageReductionAmount);
                        } else {
                            LOGGER.info(
                                    "Tax cycle: Colony {} - Net: {}, Balance: {} ({} blds)",
                                    colony.getName(),
                                    (netChangeThisCycle > 0 ? "+" + netChangeThisCycle : netChangeThisCycle),
                                    finalTaxBalance, buildingCount);
                        }

                        // Move detailed calculation breakdown to DEBUG level
                        if (TaxConfig.isDebugLogging()) {
                            LOGGER.info(
                                    "   Detailed Breakdown - Buildings: {}, Base: {}, Generated: {}, Maintenance: {}, Treasury: {}, Starting: {}, Net Change: {}, Final: {}",
                                    buildingCount, totalBaseTax, totalGeneratedTax, totalMaintenance,
                                    treasuryDeposit, startingBalance, netChangeThisCycle, finalTaxBalance);

                            // Log happiness impact if enabled
                            if (TaxConfig.isHappinessTaxModifierEnabled()) {
                                int happinessTaxImpact = totalGeneratedTax - totalBaseTax;
                                String impactType = happinessTaxImpact > 0 ? "BONUS"
                                        : (happinessTaxImpact < 0 ? "PENALTY" : "NEUTRAL");
                                LOGGER.info("   Happiness: {}/10.0, Tax Impact: {} coins ({})",
                                        String.format("%.1f", colonyAvgHappiness), happinessTaxImpact,
                                        impactType);
                            }

                            // Log calculation breakdown for clarity
                            LOGGER.info(
                                    "   Calculation: {} (starting) + {} (base) + {} (happiness) - {} (maintenance) - {} (treasury) = {} (final)",
                                    startingBalance, totalBaseTax, (totalGeneratedTax - totalBaseTax),
                                    totalMaintenance, treasuryDeposit, finalTaxBalance);

                            if (maxLimitHits > 0) {
                                LOGGER.info(
                                        "   Reached tax revenue maximum limit on {} building calculations (Max: {})",
                                        maxLimitHits, TaxConfig.getMaxTaxRevenue());
                            }

                            if (hasDebt) {
                                LOGGER.info("   Hit debt limit during maintenance deductions");
                            }
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
                            if (treasuryDeposit > 0) {
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_war_chest",
                                        treasuryDeposit).withStyle(net.minecraft.ChatFormatting.GRAY));
                            }

                            // Show current treasury balance
                            if (TaxConfig.isTreasuryEnabled()) {
                                int treasuryBalance = TreasuryManager.getTreasuryBalance(colonyId);
                                int treasuryMax = TaxConfig.getTreasuryMaxCapacity();
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_war_chest_balance",
                                        treasuryBalance, treasuryMax).withStyle(net.minecraft.ChatFormatting.GRAY));
                            }

                            // Show faction pool contribution if applicable
                            if (factionPoolContribution > 0) {
                                player.sendSystemMessage(Component.translatable(
                                        "message.minecolonytax.tax_report_faction_pool",
                                        factionPoolContribution).withStyle(net.minecraft.ChatFormatting.GRAY));
                            }

                            // Show sabotage deduction if it hit this cycle
                            if (sabotageReductionAmount > 0) {
                                player.sendSystemMessage(Component.literal(
                                        "  Sabotage: -" + sabotageReductionAmount + " coins")
                                        .withStyle(net.minecraft.ChatFormatting.DARK_RED));
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

                    // Apply debt consequences (happiness penalty, events, abandonment, blocking)
                    processDebtConsequences(colony, finalTaxBalance, recipients);

                    // Trigger random events after tax cycle. Catch per-colony so one bad
                    // colony can't abort the whole forEach — that would skip the post-loop
                    // saveTaxData() and RandomEventManager.persist() and lose the cycle's
                    // saves (codex C1 follow-up).
                    if (TaxConfig.isRandomEventsEnabled()) {
                        try {
                            RandomEventManager.onTaxCycle(colony);
                        } catch (Exception ex) {
                            LOGGER.error("Random event tick failed for colony {}", colony.getID(), ex);
                        }
                    }
                });
            });
            // Only log save operation once per full tax cycle
            saveTaxData();
            // Persist random-event state ONCE for the whole cycle (not per colony —
            // see optimization audit C1 / RandomEventManager.persist()).
            if (TaxConfig.isRandomEventsEnabled()) {
                RandomEventManager.persist();
            }
            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("Tax generation cycle completed for all colonies");
            }
        }
    }

    /**
     * Applies escalating consequences to a colony that is in debt.
     * Called once per tax cycle per colony.
     */
    private static void processDebtConsequences(IColony colony, int finalTaxBalance, Set<UUID> recipients) {
        int colonyId = colony.getID();
        int debtLimit = TaxConfig.getDebtLimit();

        if (finalTaxBalance < 0) {
            int cycles = CONSECUTIVE_DEBT_CYCLES.merge(colonyId, 1, Integer::sum);

            // 1. Happiness penalty — applied every cycle while in debt
            double happinessFactor = TaxConfig.getDebtHappinessFactor();
            if (happinessFactor < 1.0) {
                try {
                    colony.getCitizenManager().injectModifier(
                        new ExpirationBasedHappinessModifier(
                            "wnt_debt_misery",
                            1.5,
                            new StaticHappinessSupplier(happinessFactor),
                            5 // lasts 5 in-game days before expiring
                        )
                    );
                } catch (Exception e) {
                    LOGGER.debug("Could not inject debt happiness modifier for colony {}: {}", colony.getName(), e.getMessage());
                }
            }

            // 2. Debt events — trigger bandit harassment + guard desertion
            int debtEventCycles = TaxConfig.getDebtEventCycles();
            if (debtEventCycles > 0 && cycles >= debtEventCycles && TaxConfig.isRandomEventsEnabled()) {
                if ((cycles - debtEventCycles) % debtEventCycles == 0) { // fire every N cycles, not every cycle
                    try {
                        // forceTriggerEvent bypasses the per-event config switches (it exists
                        // for the admin command), so honour them here: an admin who disabled
                        // guard desertion — a PERMANENT guard loss — must not get it through the
                        // debt path anyway. Bandits are the same, just less destructive.
                        boolean fired = false;
                        if (TaxConfig.isBanditHarassmentEnabled()) {
                            net.machiavelli.minecolonytax.events.random.RandomEventManager.forceTriggerEvent(
                                colony, net.machiavelli.minecolonytax.events.random.RandomEventType.BANDIT_HARASSMENT);
                            fired = true;
                        }
                        if (TaxConfig.isGuardDesertionEnabled()) {
                            net.machiavelli.minecolonytax.events.random.RandomEventManager.forceTriggerEvent(
                                colony, net.machiavelli.minecolonytax.events.random.RandomEventType.GUARD_DESERTION);
                            fired = true;
                        }
                        if (fired && TaxConfig.isNormalLogging())
                            LOGGER.info("Debt consequence events triggered for colony {} (cycle {})", colony.getName(), cycles);
                    } catch (Exception e) {
                        LOGGER.warn("Failed to trigger debt events for colony {}: {}", colony.getName(), e.getMessage());
                    }
                }
            }

            // 3. Abandonment — after N consecutive max-debt cycles.
            // Gated by the abandonment master switch (4.x world-brick fix): debt bankruptcy
            // rewrites colony owner/permission state, so when the abandonment system is off the
            // mod must not perform it automatically either.
            int abandonCycles = TaxConfig.getDebtAbandonmentCycles();
            if (TaxConfig.isColonyAbandonmentSystemEnabled()
                    && abandonCycles > 0 && debtLimit > 0 && finalTaxBalance <= -debtLimit && cycles >= abandonCycles
                    && serverInstance != null) {
                LOGGER.warn("Colony {} has been in max debt for {} consecutive cycles — triggering debt abandonment",
                    colony.getName(), cycles);
                try {
                    net.machiavelli.minecolonytax.abandon.ColonyAbandonmentManager
                        .abandonColonyForDebt(colony, serverInstance);
                    CONSECUTIVE_DEBT_CYCLES.remove(colonyId); // reset after abandonment
                } catch (Exception e) {
                    LOGGER.error("Failed to abandon colony {} for debt: {}", colony.getName(), e.getMessage());
                }
            }

            // Escalating warning messages to online players
            if (!recipients.isEmpty() && serverInstance != null) {
                String warn;
                int abandonCyclesLeft = abandonCycles > 0 ? Math.max(0, abandonCycles - cycles) : -1;
                if (abandonCyclesLeft == 0) {
                    warn = "BANKRUPTCY IMMINENT: Colony " + colony.getName() + " is at maximum debt and will be abandoned this cycle!";
                } else if (abandonCyclesLeft > 0) {
                    warn = "DEBT CRISIS: Colony " + colony.getName() + " has been in debt for " + cycles
                        + " consecutive cycles. Bankruptcy in " + abandonCyclesLeft + " more cycles if unresolved!";
                } else if (cycles >= (debtEventCycles > 0 ? debtEventCycles : Integer.MAX_VALUE)) {
                    warn = "DEBT ESCALATION: Colony " + colony.getName() + " debt unrest is causing bandit raids and guard desertion (cycle " + cycles + ").";
                } else {
                    warn = "DEBT WARNING: Colony " + colony.getName() + " has been in debt for " + cycles
                        + " consecutive tax cycles. Pay debt to avoid unrest!";
                }
                for (UUID playerId : recipients) {
                    ServerPlayer player = serverInstance.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal(warn)
                                .withStyle(cycles >= 5
                                    ? net.minecraft.ChatFormatting.DARK_RED
                                    : net.minecraft.ChatFormatting.RED));
                    }
                }
            }

        } else {
            // Colony recovered from debt — reset counter
            int prev = CONSECUTIVE_DEBT_CYCLES.getOrDefault(colonyId, 0);
            if (prev > 0) {
                CONSECUTIVE_DEBT_CYCLES.remove(colonyId);
                if (TaxConfig.isNormalLogging())
                    LOGGER.info("Colony {} recovered from debt after {} consecutive cycles", colony.getName(), prev);
                if (!recipients.isEmpty() && serverInstance != null) {
                    for (UUID playerId : recipients) {
                        ServerPlayer player = serverInstance.getPlayerList().getPlayer(playerId);
                        if (player != null) {
                            player.sendSystemMessage(
                                net.minecraft.network.chat.Component.literal(
                                    "Colony " + colony.getName() + " has recovered from its debt after " + prev + " cycles. Citizens are relieved.")
                                    .withStyle(net.minecraft.ChatFormatting.GREEN));
                        }
                    }
                }
            }
        }
    }

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

    private static void saveTaxData() {
        saveTaxData(false); // Default to not log
    }

    private static void saveTaxData(boolean logSave) {
        // Snapshot on the calling (main) thread, then write off-thread + coalesced so
        // per-cycle and per-claim/debt-payment saves no longer block ticks (audit H4).
        // Atomic temp+move added so a crash mid-write can't truncate colonyTaxData.json.
        final Map<Integer, Integer> snapshot = new HashMap<>(colonyTaxMap);
        final boolean log = logSave;
        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("tax_data", () -> {
            File file = new File(TAX_DATA_FILE);
            file.getParentFile().mkdirs();
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            try {
                try (FileWriter writer = new FileWriter(tmp)) {
                    GSON.toJson(snapshot, writer);
                }
                try {
                    java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                if (log && TaxConfig.showTaxGenerationLogs()) {
                    LOGGER.info("Saved tax data to file.");
                }
            } catch (Exception e) {
                LOGGER.error("Error saving tax data", e);
                if (tmp.exists()) {
                    try { tmp.delete(); } catch (Exception ignored) { /* nothing else to do */ }
                }
            }
        });
    }

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
            } catch (Exception e) {
                // Broadened from IOException: a corrupt/truncated file throws
                // JsonSyntaxException (a RuntimeException), which must not propagate
                // out of onServerStarting (fatal to world load). Degrade to start-fresh.
                LOGGER.error("Error loading tax data (starting fresh)", e);
            }
        } else {
            LOGGER.info("No existing tax data file found at: {}", taxFile.getAbsolutePath());
        }
    }

    public static void freezeColonyTax(int colonyId, int freezeCycles) {
        FROZEN_COLONIES.add(colonyId);
        long freezeMinutes = (long) freezeCycles * TaxConfig.TAX_INTERVAL_MINUTES.get();
        if (TaxConfig.showTaxGenerationLogs()) {
            LOGGER.info("Colony {} tax is frozen for {} cycles ({} minutes).", colonyId, freezeCycles, freezeMinutes);
        }
        // Schedule unfreeze on the main server thread via TickScheduler
        TickScheduler.scheduleDelayed(() -> {
            FROZEN_COLONIES.remove(colonyId);
            if (TaxConfig.showTaxGenerationLogs()) {
                LOGGER.info("Colony {} tax freeze expired.", colonyId);
            }
        }, TimeUnit.MINUTES.toMillis(freezeMinutes));
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

        // SECURITY (audit HIGH): defend against signed-int overflow on currentTax + amount. Widen to long
        // and clamp to int range before storing. Keeps existing semantics for both positive (pay-down) and
        // negative (deduct) callers — this method is reused by WarSystem reparations, RaidKillTracker, and
        // RaidManager as a general "adjust tax with logging+save", so we cannot restrict the sign here.
        // The "must be in debt" pre-check belongs in the user-facing packet/command callers, which
        // already enforce currentTax < 0 before calling this method.
        long widened = (long) currentTax + (long) amount;
        int newBalance;
        if (widened > Integer.MAX_VALUE) {
            newBalance = Integer.MAX_VALUE;
        } else if (widened < Integer.MIN_VALUE) {
            newBalance = Integer.MIN_VALUE;
        } else {
            newBalance = (int) widened;
        }
        colonyTaxMap.put(colonyId, newBalance);

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
