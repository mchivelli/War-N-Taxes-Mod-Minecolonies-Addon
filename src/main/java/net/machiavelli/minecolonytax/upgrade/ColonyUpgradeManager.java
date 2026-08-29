package net.machiavelli.minecolonytax.upgrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.economy.TreasuryManager;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ColonyUpgradeManager {

    private static final Logger LOGGER = LogManager.getLogger(ColonyUpgradeManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/colony_upgrades.json";

    private static final Map<Integer, ColonyUpgradeData> UPGRADES = new ConcurrentHashMap<>();
    /** Per-colony monitor objects so concurrent purchases against the same colony serialize. */
    private static final Map<Integer, Object> PURCHASE_LOCKS = new ConcurrentHashMap<>();
    private static MinecraftServer SERVER;

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData();
        if (TaxConfig.isNormalLogging()) LOGGER.info("ColonyUpgradeManager initialized with {} colony records", UPGRADES.size());
    }

    public static void shutdown() {
        saveData();
    }

    private static ColonyUpgradeData getOrCreate(int colonyId) {
        return UPGRADES.computeIfAbsent(colonyId, ColonyUpgradeData::new);
    }

    public static int getLevel(int colonyId, UpgradeType type) {
        if (!TaxConfig.isUpgradesEnabled()) return 0;
        return getOrCreate(colonyId).getLevel(type);
    }

    public static int getUpgradeCost(int colonyId, UpgradeType type) {
        int level = getLevel(colonyId, type);
        int baseCost = switch (type) {
            case MILITIA          -> TaxConfig.getUpgradeMilitiaCostBase();
            case SPY_CAPACITY     -> TaxConfig.getUpgradeSpyCapacityCostBase();
            case SPY_SPEED        -> TaxConfig.getUpgradeSpySpeedCostBase();
            case SPY_EVASION      -> TaxConfig.getUpgradeSpyEvasionCostBase();
            case RAID_FORCE       -> TaxConfig.getUpgradeRaidForceCostBase();
            case DEFENSE          -> TaxConfig.getUpgradeDefenseCostBase();
            case TREASURY_CAP     -> TaxConfig.getUpgradeTreasuryCapCostBase();
            case TAX_EFFICIENCY   -> TaxConfig.getUpgradeTaxEfficiencyCostBase();
            case FORTIFICATION    -> TaxConfig.getUpgradeFortificationCostBase();
            case COUNTER_INTEL    -> TaxConfig.getUpgradeCounterIntelCostBase();
        };
        // Compute in long-space to avoid silent int overflow when (baseCost * scaling^level)
        // exceeds Integer.MAX_VALUE — a negative int cost flows into TreasuryManager.purchase()
        // which would *credit* the colony (free upgrade + treasury inflation).
        double scaled = (double) baseCost * Math.pow(TaxConfig.getUpgradeCostScalingFactor(), level);
        if (!Double.isFinite(scaled) || scaled >= (double) Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        long costLong = (long) scaled;
        if (costLong < 0L) return Integer.MAX_VALUE;
        if (costLong > (long) Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) costLong;
    }

    public static boolean purchase(int colonyId, UpgradeType type, MinecraftServer server) {
        if (!TaxConfig.isUpgradesEnabled()) return false;
        // Serialize the whole read-check-deduct-write sequence per colony so two
        // near-simultaneous BuyInvestmentPacket calls cannot double-charge or
        // skip a level. ConcurrentHashMap.computeIfAbsent gives a stable monitor.
        Object lock = PURCHASE_LOCKS.computeIfAbsent(colonyId, k -> new Object());
        synchronized (lock) {
            ColonyUpgradeData data = getOrCreate(colonyId);
            int currentLevel = data.getLevel(type);
            if (currentLevel >= TaxConfig.getUpgradeMaxLevel()) return false;
            int cost = getUpgradeCost(colonyId, type);
            if (!TreasuryManager.purchase(colonyId, cost, server)) return false;
            data.setLevel(type, currentLevel + 1);
            saveData();
            if (TaxConfig.isNormalLogging())
                LOGGER.info("Colony {} purchased {} investment to level {}", colonyId, type, currentLevel + 1);
            return true;
        }
    }

    // ── Effect getters — called by game systems ──────────────────────────────

    public static double getMilitiaMultiplier(int colonyId) {
        int level = getLevel(colonyId, UpgradeType.MILITIA);
        return 1.0 + (level * TaxConfig.getUpgradeMilitiaMultiplierPerLevel());
    }

    public static int getSpyCapacityBonus(int colonyId) {
        return getLevel(colonyId, UpgradeType.SPY_CAPACITY) * TaxConfig.getUpgradeSpyCapacityPerLevel();
    }

    public static double getSpySpeedMultiplier(int colonyId) {
        int level = getLevel(colonyId, UpgradeType.SPY_SPEED);
        return level * TaxConfig.getUpgradeSpySpeedMultiplierPerLevel();
    }

    public static double getDetectionReductionChance(int colonyId) {
        int level = getLevel(colonyId, UpgradeType.SPY_EVASION);
        return level * TaxConfig.getUpgradeDetectionReductionPerLevel();
    }

    public static int getDefenseLevelBonus(int colonyId) {
        return getLevel(colonyId, UpgradeType.DEFENSE) * TaxConfig.getUpgradeDefenseBonusPerLevel();
    }

    /**
     * Flat treasury capacity bonus from the TREASURY_CAP investment. Returns {@code long}: the
     * per-level flat amount is admin-configurable up to Integer.MAX_VALUE, so {@code level * flat}
     * can exceed the int range. Computing in long keeps the value exact for the single caller
     * (TreasuryManager.getEffectiveMaxCapacity), which does its own [0, Integer.MAX_VALUE] clamp.
     */
    public static long getTreasuryCapBonus(int colonyId) {
        return (long) getLevel(colonyId, UpgradeType.TREASURY_CAP) * TaxConfig.getUpgradeTreasuryCapFlatPerLevel();
    }

    public static double getTaxEfficiencyBonus(int colonyId) {
        int level = getLevel(colonyId, UpgradeType.TAX_EFFICIENCY);
        return level * TaxConfig.getUpgradeTaxEfficiencyPercentPerLevel();
    }

    public static double getFortificationDamageReduction(int colonyId) {
        int level = getLevel(colonyId, UpgradeType.FORTIFICATION);
        return Math.min(0.75, level * TaxConfig.getUpgradeFortificationDamageReductionPerLevel());
    }

    public static double getCounterIntelDetectChance(int colonyId) {
        int level = getLevel(colonyId, UpgradeType.COUNTER_INTEL);
        return Math.min(0.90, level * TaxConfig.getUpgradeCounterIntelDetectChancePerLevel());
    }

    public static double getRaidForceMultiplier(int colonyId) {
        int level = getLevel(colonyId, UpgradeType.RAID_FORCE);
        return 1.0 + (level * TaxConfig.getUpgradeRaidForceMultiplierPerLevel());
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private static void loadData() {
        File file = new File(STORAGE_FILE);
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<Integer, ColonyUpgradeData>>() {}.getType();
            Map<Integer, ColonyUpgradeData> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                UPGRADES.clear();
                UPGRADES.putAll(loaded);
            }
            // If parse returned null on a non-empty file, treat the file as corrupt and
            // keep whatever is already in memory — do NOT wipe paid investments.
        } catch (Exception e) {
            // Corrupt/truncated JSON (Gson throws JsonSyntaxException) degrades to keeping
            // the prior in-memory UPGRADES rather than crashing world load or losing data.
            // Also back the broken file up: the shutdown save would otherwise overwrite the
            // ONLY copy of everyone's paid investments with the empty in-memory state.
            try {
                java.nio.file.Files.copy(file.toPath(),
                        file.toPath().resolveSibling(file.getName() + ".corrupt-" + System.currentTimeMillis()),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                LOGGER.error("Failed to load colony investment data: {} - the unreadable file was backed up next to it", e.getMessage());
            } catch (Exception backupEx) {
                LOGGER.error("Failed to load colony investment data: {} (backup of the unreadable file also failed: {})",
                        e.getMessage(), backupEx.getMessage());
            }
        }
    }

    private static void saveData() {
        // Snapshot on the calling (main/server) thread, write off-thread + coalesced so a
        // purchase storm no longer blocks ticks on disk I/O (matches TreasuryManager).
        final Map<Integer, ColonyUpgradeData> snapshot = new HashMap<>(UPGRADES);
        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("colony_upgrades", () -> writeData(snapshot));
    }

    private static void writeData(Map<Integer, ColonyUpgradeData> data) {
        File file = new File(STORAGE_FILE);
        file.getParentFile().mkdirs();

        // Atomic write: serialize to a sibling .tmp file, then rename over the final path.
        // Prevents a crash mid-write from leaving a truncated colony_upgrades.json (which
        // would silently zero every colony's paid investments on the next load).
        File tmpFile = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            try (FileWriter writer = new FileWriter(tmpFile)) {
                GSON.toJson(data, writer);
            }
            Path tmpPath = tmpFile.toPath();
            Path finalPath = file.toPath();
            try {
                Files.move(tmpPath, finalPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException atomicEx) {
                // Some filesystems (notably across drive letters on Windows) cannot
                // ATOMIC_MOVE — fall back to a regular replace.
                Files.move(tmpPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save colony investment data: {}", e.getMessage());
            // Best-effort cleanup of the temp file so it doesn't accumulate.
            if (tmpFile.exists()) {
                try { tmpFile.delete(); } catch (Exception ignored) { /* nothing else to do */ }
            }
        }
    }
}
