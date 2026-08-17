package net.machiavelli.minecolonytax.permissions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.machiavelli.minecolonytax.TaxConfig;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-colony action permissions (see {@link ColonyPermission}).
 * Supports a colony-wide default per action plus per-player overrides.
 * Owners always bypass all restrictions.
 *
 * <p><b>Server-only state.</b> These maps are plain statics — on a dedicated server the
 * client's copy is always empty. GUI code must never read them directly; permission values
 * are pushed to the client inside the officer response packet.
 */
public class TaxPermissionManager {
    private static final Logger LOGGER = LogManager.getLogger(TaxPermissionManager.class);

    /** Colony-wide default per action. */
    private static final Map<Integer, Map<ColonyPermission, Boolean>> COLONY_DEFAULTS = new ConcurrentHashMap<>();
    /** Per-player override per action; beats the colony default. */
    private static final Map<Integer, Map<UUID, Map<ColonyPermission, Boolean>>> INDIVIDUAL = new ConcurrentHashMap<>();

    // ===================== Generic API =====================

    /**
     * Effective check: may this player perform this action for this colony right now?
     * Mirrors the shape of the old claim gate — owners always pass, non-managers never do.
     */
    public static boolean can(int colonyId, UUID playerId, ColonyPermission permission,
                              boolean isOwner, boolean isOfficer) {
        if (permission == null || playerId == null) {
            return false;
        }

        // Former owner is locked out while their colony is besieged; besieger collects via VassalManager.
        if (permission.isBlockedWhileBesieged()
                && TaxConfig.isBesiegeSystemEnabled()
                && net.machiavelli.minecolonytax.besiege.BesiegeManager.isColonyBesieged(colonyId)
                && net.machiavelli.minecolonytax.besiege.BesiegeManager.shouldBlockInteraction(playerId, colonyId)) {
            return false;
        }

        if (isOwner) {
            return true;
        }

        if (isOfficer) {
            return isGranted(colonyId, playerId, permission);
        }

        return false;
    }

    /**
     * The raw grant stored for a player: their individual override if one is set, otherwise
     * the colony default, otherwise the action's built-in default.
     *
     * <p>Unlike {@link #can} this ignores rank and the besiege lock, so it reports exactly
     * what the Officers-tab toggle controls. The GUI needs this because a besieged colony
     * would otherwise report every officer as "OFF" and the toggle could never be turned
     * back on.
     */
    public static boolean isGranted(int colonyId, UUID playerId, ColonyPermission permission) {
        Map<UUID, Map<ColonyPermission, Boolean>> colonyIndividual = INDIVIDUAL.get(colonyId);
        if (colonyIndividual != null) {
            Map<ColonyPermission, Boolean> playerPerms = colonyIndividual.get(playerId);
            if (playerPerms != null) {
                Boolean override = playerPerms.get(permission);
                if (override != null) return override;
            }
        }
        return getColonyDefault(colonyId, permission);
    }

    /** The colony-wide default for an action. */
    public static boolean getColonyDefault(int colonyId, ColonyPermission permission) {
        Map<ColonyPermission, Boolean> defaults = COLONY_DEFAULTS.get(colonyId);
        if (defaults != null) {
            Boolean value = defaults.get(permission);
            if (value != null) return value;
        }
        return permission.isDefaultAllowed();
    }

    public static void setColonyDefault(int colonyId, ColonyPermission permission, boolean allowed) {
        if (permission == null) return;
        COLONY_DEFAULTS.computeIfAbsent(colonyId, k -> new ConcurrentHashMap<>())
                .put(permission, allowed);
        if (TaxConfig.isNormalLogging())
            LOGGER.info("Colony {} default for {} set to: {}", colonyId, permission, allowed);
        save();
    }

    /** Toggles the colony-wide default and returns the new state. */
    public static boolean toggleColonyDefault(int colonyId, ColonyPermission permission) {
        boolean newState = !getColonyDefault(colonyId, permission);
        setColonyDefault(colonyId, permission, newState);
        return newState;
    }

    public static void setPlayerPermission(int colonyId, UUID playerId, ColonyPermission permission, boolean allowed) {
        if (permission == null || playerId == null) return;
        INDIVIDUAL.computeIfAbsent(colonyId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(permission, allowed);
        if (TaxConfig.isNormalLogging())
            LOGGER.info("Colony {} player {} {} set to: {}", colonyId, playerId, permission, allowed);
        save();
    }

    /** Toggles a player's override and returns the new state. */
    public static boolean togglePlayerPermission(int colonyId, UUID playerId, ColonyPermission permission) {
        boolean newState = !isGranted(colonyId, playerId, permission);
        setPlayerPermission(colonyId, playerId, permission, newState);
        return newState;
    }

    public static void removePlayerPermissions(int colonyId, UUID playerId) {
        Map<UUID, Map<ColonyPermission, Boolean>> colonyIndividual = INDIVIDUAL.get(colonyId);
        if (colonyIndividual != null) {
            colonyIndividual.remove(playerId);
            if (colonyIndividual.isEmpty()) {
                INDIVIDUAL.remove(colonyId);
            }
            if (TaxConfig.isNormalLogging())
                LOGGER.info("Removed individual permissions for player {} in colony {}", playerId, colonyId);
            save();
        }
    }

    // ===================== Tax-claim compatibility API =====================
    // Kept so existing call sites (ClaimTaxCommand, ClaimTaxPacket, PayTaxDebtPacket) keep
    // reading naturally; all of them delegate to the generic API above.

    public static boolean canPlayerClaimTax(int colonyId, UUID playerId, boolean isOwner, boolean isOfficer) {
        return can(colonyId, playerId, ColonyPermission.CLAIM_TAX, isOwner, isOfficer);
    }

    public static boolean canOfficersClaim(int colonyId) {
        return getColonyDefault(colonyId, ColonyPermission.CLAIM_TAX);
    }

    public static boolean isClaimGranted(int colonyId, UUID playerId) {
        return isGranted(colonyId, playerId, ColonyPermission.CLAIM_TAX);
    }

    public static void setPlayerClaimPermission(int colonyId, UUID playerId, boolean allowed) {
        setPlayerPermission(colonyId, playerId, ColonyPermission.CLAIM_TAX, allowed);
    }

    public static boolean togglePlayerClaimPermission(int colonyId, UUID playerId, boolean isOfficer) {
        return togglePlayerPermission(colonyId, playerId, ColonyPermission.CLAIM_TAX);
    }

    public static void setOfficerClaimPermission(int colonyId, boolean allowed) {
        setColonyDefault(colonyId, ColonyPermission.CLAIM_TAX, allowed);
    }

    public static boolean toggleOfficerClaimPermission(int colonyId) {
        return toggleColonyDefault(colonyId, ColonyPermission.CLAIM_TAX);
    }

    public static void removePlayerPermission(int colonyId, UUID playerId) {
        removePlayerPermissions(colonyId, playerId);
    }

    public static void clearAllPermissions() {
        COLONY_DEFAULTS.clear();
        INDIVIDUAL.clear();
        if (TaxConfig.isNormalLogging())
            LOGGER.info("All colony permissions cleared");
    }

    // ===================== Persistence =====================
    // Until 5.0.4 nothing ever called save/load: every permission an owner set in the
    // Officers tab was lost on the next server restart and the colony silently fell back
    // to the defaults.

    private static final String DATA_FILE = "config/warntax/tax_permissions.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FORMAT_VERSION = 2;

    /** On-disk shape. Enum names are used as keys so reordering the enum stays safe. */
    private static class PersistedPermissions {
        int version = FORMAT_VERSION;
        Map<Integer, Map<ColonyPermission, Boolean>> colonyDefaults = new HashMap<>();
        Map<Integer, Map<UUID, Map<ColonyPermission, Boolean>>> individual = new HashMap<>();

        // --- v1 fields, read-only. v1 only knew the tax-claim permission. ---
        Map<Integer, Boolean> officerClaim;
        Map<Integer, Map<UUID, Boolean>> individualClaim;
    }

    /** Writes all permission maps to disk. Atomic temp+move, matching the other managers. */
    public static void save() {
        try {
            PersistedPermissions data = new PersistedPermissions();
            // Plain HashMap copies on purpose: new EnumMap<>(map) throws IllegalArgumentException
            // when the source is a non-EnumMap that happens to be empty. save() catches broadly,
            // so that would silently stop persisting permissions instead of failing loudly — the
            // exact failure mode this file exists to prevent. Gson keys enums by name either way.
            COLONY_DEFAULTS.forEach((colonyId, perms) ->
                    data.colonyDefaults.put(colonyId, new HashMap<>(perms)));
            INDIVIDUAL.forEach((colonyId, players) -> {
                Map<UUID, Map<ColonyPermission, Boolean>> copy = new HashMap<>();
                players.forEach((playerId, perms) -> copy.put(playerId, new HashMap<>(perms)));
                data.individual.put(colonyId, copy);
            });

            File file = new File(DATA_FILE);
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();

            File tmp = new File(parent, file.getName() + ".tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException amnse) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            if (TaxConfig.isDebugLogging())
                LOGGER.debug("Saved colony permissions ({} colony defaults, {} colonies with overrides)",
                        data.colonyDefaults.size(), data.individual.size());
        } catch (Exception e) {
            LOGGER.error("Failed to save colony permissions", e);
        }
    }

    /** Reads all permission maps from disk. A missing or corrupt file degrades to defaults. */
    public static void load() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            LOGGER.debug("No colony permission file found, using defaults");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            PersistedPermissions data = GSON.fromJson(reader, PersistedPermissions.class);
            if (data == null) return;

            COLONY_DEFAULTS.clear();
            INDIVIDUAL.clear();

            if (data.colonyDefaults != null) {
                data.colonyDefaults.forEach((colonyId, perms) -> {
                    if (colonyId == null || perms == null) return;
                    Map<ColonyPermission, Boolean> target = new ConcurrentHashMap<>();
                    perms.forEach((perm, allowed) -> {
                        if (perm != null && allowed != null) target.put(perm, allowed);
                    });
                    if (!target.isEmpty()) COLONY_DEFAULTS.put(colonyId, target);
                });
            }

            if (data.individual != null) {
                data.individual.forEach((colonyId, players) -> {
                    if (colonyId == null || players == null) return;
                    Map<UUID, Map<ColonyPermission, Boolean>> targetPlayers = new ConcurrentHashMap<>();
                    players.forEach((playerId, perms) -> {
                        if (playerId == null || perms == null) return;
                        Map<ColonyPermission, Boolean> target = new ConcurrentHashMap<>();
                        perms.forEach((perm, allowed) -> {
                            if (perm != null && allowed != null) target.put(perm, allowed);
                        });
                        if (!target.isEmpty()) targetPlayers.put(playerId, target);
                    });
                    if (!targetPlayers.isEmpty()) INDIVIDUAL.put(colonyId, targetPlayers);
                });
            }

            migrateLegacy(data);

            if (TaxConfig.isNormalLogging())
                LOGGER.info("Loaded colony permissions ({} colony defaults, {} colonies with overrides)",
                        COLONY_DEFAULTS.size(), INDIVIDUAL.size());
        } catch (Exception e) {
            // Corrupt JSON throws a RuntimeException — degrade to defaults rather than
            // aborting server start.
            LOGGER.error("Failed to load colony permissions (using defaults)", e);
        }
    }

    /** Folds a v1 file (tax-claim only) into the generic maps, then rewrites it as v2. */
    private static void migrateLegacy(PersistedPermissions data) {
        boolean migrated = false;

        if (data.officerClaim != null) {
            for (Map.Entry<Integer, Boolean> entry : data.officerClaim.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                COLONY_DEFAULTS.computeIfAbsent(entry.getKey(), k -> new ConcurrentHashMap<>())
                        .putIfAbsent(ColonyPermission.CLAIM_TAX, entry.getValue());
                migrated = true;
            }
        }

        if (data.individualClaim != null) {
            for (Map.Entry<Integer, Map<UUID, Boolean>> colonyEntry : data.individualClaim.entrySet()) {
                if (colonyEntry.getKey() == null || colonyEntry.getValue() == null) continue;
                for (Map.Entry<UUID, Boolean> playerEntry : colonyEntry.getValue().entrySet()) {
                    if (playerEntry.getKey() == null || playerEntry.getValue() == null) continue;
                    INDIVIDUAL.computeIfAbsent(colonyEntry.getKey(), k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(playerEntry.getKey(), k -> new ConcurrentHashMap<>())
                            .putIfAbsent(ColonyPermission.CLAIM_TAX, playerEntry.getValue());
                    migrated = true;
                }
            }
        }

        if (migrated) {
            LOGGER.info("Migrated tax_permissions.json from v1 (tax-claim only) to v{}", FORMAT_VERSION);
            save();
        }
    }
}
