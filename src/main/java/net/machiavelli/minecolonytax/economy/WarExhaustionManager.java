package net.machiavelli.minecolonytax.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages War Exhaustion and War Reparations for colonies.
 * 
 * War Exhaustion:
 * - Colonies at war generate reduced taxes (configurable %)
 * - After war ends, there's a recovery period before full tax generation
 * resumes
 * 
 * War Reparations:
 * - Colonies losing multiple wars (configurable count) within 7 days
 * receive an additional tax penalty for a configurable duration
 */
public class WarExhaustionManager {

    private static final Logger LOGGER = LogManager.getLogger(WarExhaustionManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/war_exhaustion.json";
    private static final long SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L;

    /**
     * Tracks colonies currently at war (colonyId -> war start time)
     */
    private static final Map<Integer, Long> COLONIES_AT_WAR = new ConcurrentHashMap<>();

    /**
     * Tracks colonies in post-war recovery (colonyId -> war end time)
     */
    private static final Map<Integer, Long> RECOVERY_STATUS = new ConcurrentHashMap<>();

    /**
     * Tracks war losses for reparations (colonyId -> list of loss timestamps)
     */
    private static final Map<Integer, List<Long>> WAR_LOSSES = new ConcurrentHashMap<>();

    /**
     * Tracks colonies under reparations (colonyId -> expiry time)
     */
    private static final Map<Integer, Long> REPARATIONS = new ConcurrentHashMap<>();

    /**
     * Tracks colonies with war immunity (colonyId -> immunity expiry time).
     * Colonies below the weariness threshold receive temporary protection
     * from war declarations after a loss (comeback mechanic).
     */
    private static final Map<Integer, Long> WAR_IMMUNITY = new ConcurrentHashMap<>();

    private static MinecraftServer SERVER;

    // ==================== Initialization ====================

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData();
        cleanupExpiredData();
        if (TaxConfig.isNormalLogging()) LOGGER.info("WarExhaustionManager initialized - {} at war, {} in recovery, {} under reparations",
                COLONIES_AT_WAR.size(), RECOVERY_STATUS.size(), REPARATIONS.size());
    }

    public static void shutdown() {
        saveData();
        if (TaxConfig.isNormalLogging()) LOGGER.info("WarExhaustionManager shutdown complete");
    }

    // ==================== War Status ====================

    /**
     * Mark a colony as being at war. Called when war starts.
     * 
     * @param colonyId The colony entering war
     */
    public static void applyWarStatus(int colonyId) {
        if (!TaxConfig.isWarExhaustionEnabled())
            return;

        COLONIES_AT_WAR.put(colonyId, System.currentTimeMillis());
        RECOVERY_STATUS.remove(colonyId);
        saveData();

        if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} is now AT WAR - tax generation reduced by {}%",
                colonyId, (int) (TaxConfig.getWarTaxReductionPercent() * 100));
    }

    /**
     * Remove war status from a colony. Called when war ends.
     * Starts the recovery period.
     * 
     * @param colonyId The colony exiting war
     */
    public static void removeWarStatus(int colonyId) {
        if (!TaxConfig.isWarExhaustionEnabled())
            return;

        COLONIES_AT_WAR.remove(colonyId);
        RECOVERY_STATUS.put(colonyId, System.currentTimeMillis());
        saveData();

        if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} war ended - entering {} hour recovery period",
                colonyId, TaxConfig.getPostWarRecoveryHours());
    }

    /**
     * Check if a colony is currently at war.
     */
    public static boolean isAtWar(int colonyId) {
        return COLONIES_AT_WAR.containsKey(colonyId);
    }

    /**
     * Check if a colony is in post-war recovery period.
     */
    public static boolean isInRecovery(int colonyId) {
        Long warEndTime = RECOVERY_STATUS.get(colonyId);
        if (warEndTime == null)
            return false;

        long recoveryDurationMs = TaxConfig.getPostWarRecoveryHours() * 60L * 60L * 1000L;
        if (System.currentTimeMillis() >= warEndTime + recoveryDurationMs) {
            // Recovery complete
            RECOVERY_STATUS.remove(colonyId);
            saveData();
            return false;
        }
        return true;
    }

    // ==================== War Losses & Reparations ====================

    /**
     * Record a war loss for a colony. Checks if reparations should be triggered.
     * 
     * @param colonyId The colony that lost the war
     */
    public static void recordWarLoss(int colonyId) {
        long now = System.currentTimeMillis();

        // Grant war weariness immunity if colony tax is below threshold
        if (TaxConfig.isWarWearinessEnabled()) {
            int threshold = TaxConfig.getWarWearinessThreshold();
            int taxBalance = getColonyTaxBalance(colonyId);
            if (taxBalance <= threshold) {
                long immunityMs = TaxConfig.getWarWearinessImmunityHours() * 60L * 60L * 1000L;
                WAR_IMMUNITY.put(colonyId, now + immunityMs);
                if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} granted {}h war immunity (tax {} <= threshold {})",
                        colonyId, TaxConfig.getWarWearinessImmunityHours(), taxBalance, threshold);
            }
        }

        if (!TaxConfig.isWarReparationsEnabled()) {
            saveData();
            return;
        }

        List<Long> losses = WAR_LOSSES.computeIfAbsent(colonyId, k -> new ArrayList<>());
        losses.removeIf(timestamp -> now - timestamp > SEVEN_DAYS_MS);
        losses.add(now);

        if (TaxConfig.isNormalLogging()) LOGGER.info("Colony {} lost a war - {} losses in last 7 days", colonyId, losses.size());

        int triggerCount = TaxConfig.getReparationsTriggerLossesCount();
        if (losses.size() >= triggerCount) {
            applyReparations(colonyId);
        }

        saveData();
    }

    /**
     * Check if a colony has active war immunity (comeback protection).
     */
    public static boolean hasWarImmunity(int colonyId) {
        Long expiryTime = WAR_IMMUNITY.get(colonyId);
        if (expiryTime == null)
            return false;

        if (System.currentTimeMillis() >= expiryTime) {
            WAR_IMMUNITY.remove(colonyId);
            saveData();
            return false;
        }
        return true;
    }

    /**
     * Get hours remaining on war immunity, or 0 if none.
     */
    public static int getRemainingImmunityHours(int colonyId) {
        Long expiryTime = WAR_IMMUNITY.get(colonyId);
        if (expiryTime == null || System.currentTimeMillis() >= expiryTime)
            return 0;

        long remaining = expiryTime - System.currentTimeMillis();
        return Math.max(1, (int) (remaining / (60L * 60L * 1000L)));
    }

    /**
     * Helper: get a colony's stored tax balance using the colony manager.
     */
    private static int getColonyTaxBalance(int colonyId) {
        if (SERVER == null)
            return 0;
        try {
            IColonyManager manager = IMinecoloniesAPI.getInstance().getColonyManager();
            IColony colony = null;
            for (net.minecraft.world.level.Level level : SERVER.getAllLevels()) {
                colony = manager.getColonyByWorld(colonyId, level);
                if (colony != null) break;
            }
            if (colony == null)
                return 0;
            return net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColony(colony);
        } catch (Exception e) {
            LOGGER.warn("Could not retrieve tax balance for colony {} in weariness check", colonyId);
            return 0;
        }
    }

    /**
     * Apply war reparations penalty to a colony.
     */
    private static void applyReparations(int colonyId) {
        long durationMs = TaxConfig.getReparationsDurationHours() * 60L * 60L * 1000L;
        long expiryTime = System.currentTimeMillis() + durationMs;

        REPARATIONS.put(colonyId, expiryTime);

        WAR_LOSSES.remove(colonyId);

        LOGGER.warn("Colony {} is now under WAR REPARATIONS for {} hours ({} recent losses)",
                colonyId, TaxConfig.getReparationsDurationHours(), TaxConfig.getReparationsTriggerLossesCount());
    }

    /**
     * Check if a colony is under reparations.
     */
    public static boolean hasReparations(int colonyId) {
        Long expiryTime = REPARATIONS.get(colonyId);
        if (expiryTime == null)
            return false;

        if (System.currentTimeMillis() >= expiryTime) {
            REPARATIONS.remove(colonyId);
            saveData();
            return false;
        }
        return true;
    }

    // ==================== Tax Multiplier ====================

    /**
     * Get the combined tax multiplier for a colony considering all war penalties.
     * 
     * Priority:
     * 1. At War: WAR_TAX_REDUCTION_PERCENT reduction
     * 2. In Recovery: Gradual recovery curve
     * 3. Under Reparations: REPARATIONS_TAX_PENALTY_PERCENT reduction
     * 4. Normal: 1.0 (no penalty)
     * 
     * @param colonyId The colony ID
     * @return Tax multiplier (1.0 = full tax, 0.7 = 30% reduction)
     */
    public static double getTaxMultiplier(int colonyId) {
        if (!TaxConfig.isWarExhaustionEnabled()) {
            // Check reparations even if exhaustion disabled
            if (TaxConfig.isWarReparationsEnabled() && hasReparations(colonyId)) {
                return 1.0 - TaxConfig.getReparationsTaxPenaltyPercent();
            }
            return 1.0;
        }

        double multiplier = 1.0;

        // 1. At War - full reduction
        if (isAtWar(colonyId)) {
            multiplier = 1.0 - TaxConfig.getWarTaxReductionPercent();
        }
        // 2. In Recovery - gradual recovery
        else if (isInRecovery(colonyId)) {
            multiplier = calculateRecoveryMultiplier(colonyId);
        }

        // 3. Reparations stack on top as additional penalty
        if (TaxConfig.isWarReparationsEnabled() && hasReparations(colonyId)) {
            double reparationsPenalty = TaxConfig.getReparationsTaxPenaltyPercent();
            multiplier = multiplier * (1.0 - reparationsPenalty);
        }

        // 4. Apply combined penalty cap to prevent excessive stacking
        double maxPenalty = TaxConfig.MAX_COMBINED_WAR_PENALTY_PERCENT.get();
        double minMultiplier = 1.0 - maxPenalty;
        multiplier = Math.max(minMultiplier, multiplier);

        return Math.max(0.1, multiplier); // Minimum 10% tax generation
    }

    /**
     * Calculate the recovery multiplier using a gradual curve.
     * Starts at war-level reduction and linearly recovers to 1.0.
     */
    private static double calculateRecoveryMultiplier(int colonyId) {
        Long warEndTime = RECOVERY_STATUS.get(colonyId);
        if (warEndTime == null)
            return 1.0;

        long now = System.currentTimeMillis();
        long recoveryDurationMs = TaxConfig.getPostWarRecoveryHours() * 60L * 60L * 1000L;
        long elapsed = now - warEndTime;

        if (elapsed >= recoveryDurationMs) {
            return 1.0; // Fully recovered
        }

        // Linear interpolation from war reduction to 1.0
        double warMultiplier = 1.0 - TaxConfig.getWarTaxReductionPercent();
        double progress = (double) elapsed / recoveryDurationMs;

        return warMultiplier + (progress * (1.0 - warMultiplier));
    }

    // ==================== Status Info ====================

    /**
     * Get hours remaining in recovery period.
     */
    public static int getRemainingRecoveryHours(int colonyId) {
        Long warEndTime = RECOVERY_STATUS.get(colonyId);
        if (warEndTime == null)
            return 0;

        long recoveryDurationMs = TaxConfig.getPostWarRecoveryHours() * 60L * 60L * 1000L;
        long remaining = (warEndTime + recoveryDurationMs) - System.currentTimeMillis();

        return Math.max(0, (int) (remaining / (60L * 60L * 1000L)));
    }

    /**
     * Get hours remaining under reparations.
     */
    public static int getRemainingReparationsHours(int colonyId) {
        Long expiryTime = REPARATIONS.get(colonyId);
        if (expiryTime == null)
            return 0;

        long remaining = expiryTime - System.currentTimeMillis();
        return Math.max(0, (int) (remaining / (60L * 60L * 1000L)));
    }

    /**
     * Get number of recent war losses (within 7 days).
     */
    public static int getRecentLossCount(int colonyId) {
        List<Long> losses = WAR_LOSSES.get(colonyId);
        if (losses == null)
            return 0;

        long now = System.currentTimeMillis();
        return (int) losses.stream()
                .filter(timestamp -> now - timestamp <= SEVEN_DAYS_MS)
                .count();
    }

    // ==================== Persistence ====================

    private static void cleanupExpiredData() {
        long now = System.currentTimeMillis();

        int recoveryHours = TaxConfig.getPostWarRecoveryHours();
        long recoveryDurationMs = recoveryHours * 60L * 60L * 1000L;
        RECOVERY_STATUS.entrySet().removeIf(e -> now >= e.getValue() + recoveryDurationMs);
        REPARATIONS.entrySet().removeIf(e -> now >= e.getValue());
        WAR_IMMUNITY.entrySet().removeIf(e -> now >= e.getValue());

        for (List<Long> losses : WAR_LOSSES.values()) {
            losses.removeIf(timestamp -> now - timestamp > SEVEN_DAYS_MS);
        }
        WAR_LOSSES.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private static void loadData() {
        Path path = Paths.get(STORAGE_FILE);
        if (!Files.exists(path))
            return;

        try (Reader reader = new FileReader(path.toFile())) {
            Type type = new TypeToken<ExhaustionSaveData>() {
            }.getType();
            ExhaustionSaveData data = GSON.fromJson(reader, type);

            if (data != null) {
                if (data.coloniesAtWar != null)
                    COLONIES_AT_WAR.putAll(data.coloniesAtWar);
                if (data.recoveryStatus != null)
                    RECOVERY_STATUS.putAll(data.recoveryStatus);
                if (data.warLosses != null)
                    WAR_LOSSES.putAll(data.warLosses);
                if (data.reparations != null)
                    REPARATIONS.putAll(data.reparations);
                if (data.warImmunity != null)
                    WAR_IMMUNITY.putAll(data.warImmunity);
            }

            LOGGER.debug("Loaded war exhaustion data from {}", STORAGE_FILE);
        } catch (Exception e) {
            LOGGER.error("Failed to load war exhaustion data", e);
        }
    }

    private static void saveData() {
        // Snapshot on the calling (main) thread, write off-thread + coalesced so the
        // many war-state transitions no longer each block a tick on disk I/O (audit H3).
        final ExhaustionSaveData data = new ExhaustionSaveData();
        data.coloniesAtWar = new java.util.HashMap<>(COLONIES_AT_WAR);
        data.recoveryStatus = new java.util.HashMap<>(RECOVERY_STATUS);
        // warLosses values are mutable Lists — deep-copy so the async writer never
        // serializes a list while the main thread mutates it.
        Map<Integer, List<Long>> lossesCopy = new java.util.HashMap<>();
        for (Map.Entry<Integer, List<Long>> e : WAR_LOSSES.entrySet()) {
            lossesCopy.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
        }
        data.warLosses = lossesCopy;
        data.reparations = new java.util.HashMap<>(REPARATIONS);
        data.warImmunity = new java.util.HashMap<>(WAR_IMMUNITY);

        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("war_exhaustion", () -> {
            try {
                Path path = Paths.get(STORAGE_FILE);
                Files.createDirectories(path.getParent());
                try (Writer writer = new FileWriter(path.toFile())) {
                    GSON.toJson(data, writer);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to save war exhaustion data", e);
            }
        });
    }

    /**
     * Data class for JSON persistence
     */
    private static class ExhaustionSaveData {
        Map<Integer, Long> coloniesAtWar;
        Map<Integer, Long> recoveryStatus;
        Map<Integer, List<Long>> warLosses;
        Map<Integer, Long> reparations;
        Map<Integer, Long> warImmunity;
    }
}
