package net.machiavelli.minecolonytax.economy.policy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
 * Manages tax policies for colonies.
 *
 * Each colony can select a tax policy that affects:
 * - Tax revenue generation (multiplicative modifier)
 * - Citizen happiness (additive modifier to happiness growth)
 *
 * Policies:
 * - NORMAL: No modifiers (default)
 * - LOW: Less revenue, happier citizens
 * - HIGH: More revenue, unhappier citizens
 * - WAR_ECONOMY: Maximum revenue boost, significant happiness penalty
 */
public class TaxPolicyManager {

    private static final Logger LOGGER = LogManager.getLogger(TaxPolicyManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/tax_policies.json";

    /**
     * Map of colony ID to their active tax policy.
     * Colonies not in this map use NORMAL policy by default.
     */
    private static final Map<Integer, String> COLONY_POLICIES = new ConcurrentHashMap<>();

    private static MinecraftServer SERVER;

    // ==================== Initialization ====================

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData();
        LOGGER.info("TaxPolicyManager initialized with {} colony policies", COLONY_POLICIES.size());
    }

    public static void shutdown() {
        saveData();
        LOGGER.info("TaxPolicyManager shutdown complete");
    }

    // ==================== Helper Methods ====================

    /**
     * Get a colony by ID.
     */
    public static IColony getColony(int colonyId) {
        if (SERVER == null)
            return null;
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        return colonyManager.getColonyByWorld(colonyId, SERVER.overworld());
    }

    // ==================== Policy Operations ====================

    /**
     * Get the active policy for a colony.
     *
     * @param colonyId The colony ID
     * @return The active policy (defaults to NORMAL if not set)
     */
    public static TaxPolicy getPolicy(int colonyId) {
        if (!TaxConfig.isTaxPoliciesEnabled()) {
            return TaxPolicy.NORMAL;
        }
        String policyName = COLONY_POLICIES.get(colonyId);
        if (policyName == null) {
            return TaxPolicy.NORMAL;
        }
        TaxPolicy policy = TaxPolicy.fromString(policyName);
        return policy != null ? policy : TaxPolicy.NORMAL;
    }

    /**
     * Set the tax policy for a colony.
     *
     * @param colonyId The colony ID
     * @param policy   The policy to set
     */
    public static void setPolicy(int colonyId, TaxPolicy policy) {
        if (policy == TaxPolicy.NORMAL) {
            COLONY_POLICIES.remove(colonyId);
        } else {
            COLONY_POLICIES.put(colonyId, policy.name());
        }
        saveData();
        LOGGER.info("Colony {} set tax policy to {}", colonyId, policy.name());
    }

    /**
     * Get the revenue multiplier for a colony based on their policy.
     *
     * @param colonyId The colony ID
     * @return The revenue multiplier (e.g., 0.75, 1.0, 1.25)
     */
    public static double getRevenueMultiplier(int colonyId) {
        if (!TaxConfig.isTaxPoliciesEnabled()) {
            return 1.0;
        }
        return getPolicy(colonyId).getRevenueModifier();
    }

    /**
     * Get the happiness modifier for a colony based on their policy.
     * This is an additive modifier to happiness growth/calculation.
     *
     * @param colonyId The colony ID
     * @return The happiness modifier (e.g., -0.15, 0.0, 0.20)
     */
    public static double getHappinessModifier(int colonyId) {
        if (!TaxConfig.isTaxPoliciesEnabled()) {
            return 0.0;
        }
        return getPolicy(colonyId).getHappinessModifier();
    }

    // ==================== Command Methods ====================

    /**
     * Handle the set policy command.
     *
     * @param player     The player executing the command
     * @param policyName The policy name to set
     * @return Command result message
     */
    public static String setPolicyCommand(ServerPlayer player, String policyName) {
        if (!TaxConfig.isTaxPoliciesEnabled()) {
            return "§cTax policies are disabled on this server.";
        }

        // Find player's colony
        IColony colony = findPlayerColony(player);
        if (colony == null) {
            return "§cYou must own or manage a colony to set tax policy.";
        }

        // Check permission
        if (!colony.getPermissions().getRank(player.getUUID()).isColonyManager()) {
            return "§cYou must be a colony manager to change tax policy.";
        }

        // Parse policy
        TaxPolicy policy = TaxPolicy.fromString(policyName);
        if (policy == null) {
            return "§cInvalid policy. Available: NORMAL, LOW, HIGH, WAR_ECONOMY";
        }

        // Set the policy
        setPolicy(colony.getID(), policy);

        // Build confirmation message
        StringBuilder msg = new StringBuilder();
        msg.append("§a=== Tax Policy Changed ===\n");
        msg.append("§fColony: §e").append(colony.getName()).append("\n");
        msg.append("§fNew Policy: ").append(policy.getColorCode()).append(policy.getDisplayName()).append("\n");
        msg.append("§7").append(policy.getDescription()).append("\n");
        msg.append("\n§fEffects:\n");

        double revMod = policy.getRevenueModifier();
        if (revMod > 1.0) {
            msg.append("§a  +").append(String.format("%.0f", (revMod - 1) * 100)).append("% tax revenue\n");
        } else if (revMod < 1.0) {
            msg.append("§c  ").append(String.format("%.0f", (revMod - 1) * 100)).append("% tax revenue\n");
        } else {
            msg.append("§f  No revenue modifier\n");
        }

        double hapMod = policy.getHappinessModifier();
        if (hapMod > 0) {
            msg.append("§a  +").append(String.format("%.0f", hapMod * 100)).append("% happiness growth");
        } else if (hapMod < 0) {
            msg.append("§c  ").append(String.format("%.0f", hapMod * 100)).append("% happiness growth");
        } else {
            msg.append("§f  No happiness modifier");
        }

        return msg.toString();
    }

    /**
     * Handle the view policy command.
     *
     * @param player The player executing the command
     * @return Command result message
     */
    public static String viewPolicyCommand(ServerPlayer player) {
        if (!TaxConfig.isTaxPoliciesEnabled()) {
            return "§cTax policies are disabled on this server.";
        }

        // Find player's colony
        IColony colony = findPlayerColony(player);
        if (colony == null) {
            return "§cYou must be part of a colony to view tax policy.";
        }

        TaxPolicy policy = getPolicy(colony.getID());

        StringBuilder msg = new StringBuilder();
        msg.append("§6=== Tax Policy for ").append(colony.getName()).append(" ===\n");
        msg.append("§fCurrent Policy: ").append(policy.getColorCode()).append(policy.getDisplayName()).append("\n");
        msg.append("§7").append(policy.getDescription()).append("\n\n");

        double revMod = policy.getRevenueModifier();
        if (revMod > 1.0) {
            msg.append("§fRevenue: §a+").append(String.format("%.0f", (revMod - 1) * 100)).append("%\n");
        } else if (revMod < 1.0) {
            msg.append("§fRevenue: §c").append(String.format("%.0f", (revMod - 1) * 100)).append("%\n");
        } else {
            msg.append("§fRevenue: §fnormal\n");
        }

        double hapMod = policy.getHappinessModifier();
        if (hapMod > 0) {
            msg.append("§fHappiness: §a+").append(String.format("%.0f", hapMod * 100)).append("%");
        } else if (hapMod < 0) {
            msg.append("§fHappiness: §c").append(String.format("%.0f", hapMod * 100)).append("%");
        } else {
            msg.append("§fHappiness: §fnormal");
        }

        return msg.toString();
    }

    /**
     * List all available tax policies.
     *
     * @return Formatted list of policies
     */
    public static String listPoliciesCommand() {
        if (!TaxConfig.isTaxPoliciesEnabled()) {
            return "§cTax policies are disabled on this server.";
        }

        StringBuilder msg = new StringBuilder();
        msg.append("§6=== Available Tax Policies ===\n\n");

        for (TaxPolicy policy : TaxPolicy.values()) {
            msg.append(policy.getColorCode()).append("§l").append(policy.name()).append("§r");
            msg.append(" - ").append(policy.getDisplayName()).append("\n");
            msg.append("§7  ").append(policy.getDescription()).append("\n");

            double revMod = policy.getRevenueModifier();
            double hapMod = policy.getHappinessModifier();

            msg.append("§7  Revenue: ");
            if (revMod > 1.0) {
                msg.append("§a+").append(String.format("%.0f", (revMod - 1) * 100)).append("%");
            } else if (revMod < 1.0) {
                msg.append("§c").append(String.format("%.0f", (revMod - 1) * 100)).append("%");
            } else {
                msg.append("§fnormal");
            }

            msg.append("§7 | Happiness: ");
            if (hapMod > 0) {
                msg.append("§a+").append(String.format("%.0f", hapMod * 100)).append("%");
            } else if (hapMod < 0) {
                msg.append("§c").append(String.format("%.0f", hapMod * 100)).append("%");
            } else {
                msg.append("§fnormal");
            }
            msg.append("\n\n");
        }

        msg.append("§eUse: §f/wnt taxpolicy set <policy>");
        return msg.toString();
    }

    // ==================== Helper Methods ====================

    /**
     * Find a colony that the player owns or manages.
     */
    private static IColony findPlayerColony(ServerPlayer player) {
        if (SERVER == null) return null;

        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        for (var world : SERVER.getAllLevels()) {
            for (IColony colony : colonyManager.getColonies(world)) {
                // Check if player is owner
                if (colony.getPermissions().getOwner().equals(player.getUUID())) {
                    return colony;
                }
                // Check if player is colony manager
                if (colony.getPermissions().getRank(player.getUUID()).isColonyManager()) {
                    return colony;
                }
            }
        }
        return null;
    }

    // ==================== Persistence ====================

    private static void loadData() {
        Path path = Paths.get(STORAGE_FILE);
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = new FileReader(path.toFile())) {
            Type type = new TypeToken<Map<Integer, String>>() {}.getType();
            Map<Integer, String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                COLONY_POLICIES.clear();
                COLONY_POLICIES.putAll(loaded);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load tax policies data", e);
        }
    }

    private static void saveData() {
        Path path = Paths.get(STORAGE_FILE);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = new FileWriter(path.toFile())) {
                GSON.toJson(COLONY_POLICIES, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save tax policies data", e);
        }
    }
}
