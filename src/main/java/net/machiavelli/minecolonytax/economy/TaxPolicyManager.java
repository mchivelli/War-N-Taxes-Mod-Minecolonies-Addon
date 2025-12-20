package net.machiavelli.minecolonytax.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-colony tax rate policies with data persistence.
 * <p>
 * Tax Rates:
 * - LOW: Less revenue, happier citizens
 * - NORMAL: Default, balanced
 * - HIGH: More revenue, unhappier citizens
 * - WAR: Maximum revenue, significant happiness penalty (auto-activates during
 * war)
 */
public class TaxPolicyManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "data/warntax/tax_policies.json";

    private static MinecraftServer SERVER;

    /**
     * Available tax rate policies
     */
    public enum TaxRate {
        LOW, // -25% revenue, +20% happiness modifier
        NORMAL, // No modifier (default)
        HIGH, // +25% revenue, -15% happiness modifier
        WAR // +50% revenue, -25% happiness modifier (auto during war)
    }

    // Per-colony tax rate storage (colonyId -> TaxRate)
    private static final Map<Integer, TaxRate> COLONY_TAX_RATES = new ConcurrentHashMap<>();

    // ==================== Initialization ====================

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData();
        LOGGER.info("TaxPolicyManager initialized - {} colony policies loaded", COLONY_TAX_RATES.size());
    }

    public static void shutdown() {
        saveData();
        LOGGER.info("TaxPolicyManager shutdown - saved {} colony policies", COLONY_TAX_RATES.size());
    }

    // ==================== Tax Rate Management ====================

    /**
     * Get the effective tax rate for a colony, considering auto-WAR activation.
     * 
     * @param colonyId The colony ID
     * @return The effective tax rate (WAR if at war, otherwise stored/default)
     */
    public static TaxRate getEffectiveTaxRate(int colonyId) {
        if (!TaxConfig.isTaxPoliciesEnabled()) {
            return TaxRate.NORMAL;
        }

        // Auto-activate WAR policy when colony is at war
        if (WarSystem.ACTIVE_WARS.containsKey(colonyId)) {
            return TaxRate.WAR;
        }

        // Check if colony is in recovery from war (WarExhaustionManager)
        if (WarExhaustionManager.isInRecovery(colonyId) || WarExhaustionManager.isAtWar(colonyId)) {
            return TaxRate.WAR;
        }

        return COLONY_TAX_RATES.getOrDefault(colonyId, TaxRate.NORMAL);
    }

    /**
     * Get the stored tax rate (ignoring war status).
     */
    public static TaxRate getStoredTaxRate(int colonyId) {
        return COLONY_TAX_RATES.getOrDefault(colonyId, TaxRate.NORMAL);
    }

    /**
     * Set the tax rate for a colony.
     * Note: WAR rate cannot be manually set - it auto-activates during war.
     */
    public static boolean setTaxRate(int colonyId, TaxRate rate) {
        if (!TaxConfig.isTaxPoliciesEnabled()) {
            return false;
        }

        if (rate == TaxRate.WAR) {
            LOGGER.warn("Cannot manually set WAR tax rate - it auto-activates during war");
            return false;
        }

        TaxRate oldRate = COLONY_TAX_RATES.put(colonyId, rate);
        saveData();

        LOGGER.info("Colony {} tax policy changed: {} -> {}", colonyId,
                oldRate != null ? oldRate : "NORMAL", rate);
        return true;
    }

    /**
     * Reset a colony's tax rate to NORMAL.
     */
    public static void resetTaxRate(int colonyId) {
        COLONY_TAX_RATES.remove(colonyId);
        saveData();
    }

    // ==================== Modifier Calculations ====================

    /**
     * Get the revenue modifier for a colony based on its tax policy.
     * Applied as a multiplier adjustment: 1.0 + modifier
     * 
     * @return Value between -0.25 to +0.50
     */
    public static double getRevenueModifier(int colonyId) {
        TaxRate rate = getEffectiveTaxRate(colonyId);
        return switch (rate) {
            case LOW -> TaxConfig.getTaxPolicyLowRevenueModifier(); // -0.25
            case HIGH -> TaxConfig.getTaxPolicyHighRevenueModifier(); // +0.25
            case WAR -> TaxConfig.getTaxPolicyWarRevenueModifier(); // +0.50
            default -> 0.0; // NORMAL
        };
    }

    /**
     * Get the happiness modifier for a colony based on its tax policy.
     * This affects happiness growth rate, not tax multiplier.
     * 
     * @return Value between -0.25 to +0.20
     */
    public static double getHappinessModifier(int colonyId) {
        TaxRate rate = getEffectiveTaxRate(colonyId);
        return switch (rate) {
            case LOW -> TaxConfig.getTaxPolicyLowHappinessModifier(); // +0.20
            case HIGH -> TaxConfig.getTaxPolicyHighHappinessModifier(); // -0.15
            case WAR -> TaxConfig.getTaxPolicyWarHappinessModifier(); // -0.25
            default -> 0.0; // NORMAL
        };
    }

    /**
     * Get a human-readable description of the tax rate.
     */
    public static String getTaxRateDescription(TaxRate rate) {
        return switch (rate) {
            case LOW -> "Low Tax (-25% revenue, +20% happiness)";
            case NORMAL -> "Normal Tax (balanced)";
            case HIGH -> "High Tax (+25% revenue, -15% happiness)";
            case WAR -> "War Economy (+50% revenue, -25% happiness)";
        };
    }

    /**
     * Check if a colony is currently in WAR mode (auto-activated).
     */
    public static boolean isInWarMode(int colonyId) {
        return getEffectiveTaxRate(colonyId) == TaxRate.WAR;
    }

    // ==================== Data Persistence ====================

    private static void loadData() {
        Path path = Paths.get(DATA_FILE);
        if (!Files.exists(path)) {
            LOGGER.debug("No tax policy data file found at {}", DATA_FILE);
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Type type = new TypeToken<Map<Integer, String>>() {
            }.getType();
            Map<Integer, String> stringMap = GSON.fromJson(reader, type);

            if (stringMap != null) {
                COLONY_TAX_RATES.clear();
                stringMap.forEach((colonyId, rateName) -> {
                    try {
                        TaxRate rate = TaxRate.valueOf(rateName);
                        COLONY_TAX_RATES.put(colonyId, rate);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Invalid tax rate '{}' for colony {}, using NORMAL", rateName, colonyId);
                    }
                });
            }
            LOGGER.debug("Loaded {} colony tax policies", COLONY_TAX_RATES.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load tax policy data: {}", e.getMessage());
        }
    }

    private static void saveData() {
        Path path = Paths.get(DATA_FILE);
        try {
            Files.createDirectories(path.getParent());

            // Convert enum values to strings for JSON
            Map<Integer, String> stringMap = new ConcurrentHashMap<>();
            COLONY_TAX_RATES.forEach((colonyId, rate) -> stringMap.put(colonyId, rate.name()));

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(stringMap, writer);
            }
            LOGGER.debug("Saved {} colony tax policies", COLONY_TAX_RATES.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save tax policy data: {}", e.getMessage());
        }
    }

    // ==================== Cleanup ====================

    /**
     * Remove policy data for a deleted colony.
     */
    public static void onColonyDeleted(int colonyId) {
        if (COLONY_TAX_RATES.remove(colonyId) != null) {
            saveData();
            LOGGER.info("Removed tax policy data for deleted colony {}", colonyId);
        }
    }
}
