package net.machiavelli.minecolonytax.faction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FactionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(FactionManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("warntax");
    private static final File FACTIONS_FILE = CONFIG_DIR.resolve("factions.json").toFile();

    private static Map<UUID, FactionData> FACTIONS = new HashMap<>();

    public static void init() {
        loadData();
    }

    private static void loadData() {
        if (!FACTIONS_FILE.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(FACTIONS_FILE)) {
            Type type = new TypeToken<Map<UUID, FactionData>>() {
            }.getType();
            Map<UUID, FactionData> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                FACTIONS = loaded;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load factions data", e);
        }
    }

    public static void saveData() {
        if (!CONFIG_DIR.toFile().exists()) {
            CONFIG_DIR.toFile().mkdirs();
        }

        try (FileWriter writer = new FileWriter(FACTIONS_FILE)) {
            GSON.toJson(FACTIONS, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save factions data", e);
        }
    }

    public static FactionData createFaction(String name, int ownerColonyId) {
        if (!TaxConfig.isFactionSystemEnabled())
            return null;

        // Check if colony is already in a faction
        if (getFactionByColony(ownerColonyId) != null) {
            return null;
        }

        UUID id = UUID.randomUUID();
        FactionData faction = new FactionData(id, name, ownerColonyId);
        FACTIONS.put(id, faction);

        // Initialize pool settings from config
        if (TaxConfig.isSharedTaxPoolEnabled()) {
            faction.setTaxRate(TaxConfig.getDefaultPoolContributionPercent() / 100.0);
        }

        saveData();
        return faction;
    }

    public static void disbandFaction(UUID factionId) {
        FACTIONS.remove(factionId);
        saveData();
    }

    public static FactionData getFaction(UUID factionId) {
        return FACTIONS.get(factionId);
    }

    public static FactionData getFactionByName(String name) {
        for (FactionData faction : FACTIONS.values()) {
            if (faction.getName().equalsIgnoreCase(name)) {
                return faction;
            }
        }
        return null;
    }

    public static FactionData getFactionByColony(int colonyId) {
        for (FactionData faction : FACTIONS.values()) {
            if (faction.isMember(colonyId)) {
                return faction;
            }
        }
        return null;
    }

    public static boolean hasInvite(int colonyId, UUID factionId) {
        FactionData faction = getFaction(factionId);
        return faction != null && faction.hasInvite(colonyId);
    }

    public static boolean joinFaction(int colonyId, UUID factionId) {
        FactionData faction = getFaction(factionId);
        if (faction == null)
            return false;

        // Check max members
        if (faction.getMemberColonyIds().size() >= TaxConfig.getMaxFactionMembers()) {
            return false;
        }

        // Check if already in a faction
        if (getFactionByColony(colonyId) != null) {
            return false;
        }

        faction.addMember(colonyId);
        faction.removeInvite(colonyId); // Clear invite upon joining
        saveData();
        return true;
    }

    public static void leaveFaction(int colonyId) {
        FactionData faction = getFactionByColony(colonyId);
        if (faction != null) {
            // If owner leaves, disband or transfer?
            // For now, if owner leaves, disband if they are the last one, or transfer to
            // next member?
            // Simple approach: Owner cannot leave without disbanding or transferring.
            // But for simple "leave", if owner -> disband.
            if (faction.getOwnerColonyId() == colonyId) {
                // If there are other members, transfer ownership to the first one found
                if (faction.getMemberColonyIds().size() > 1) {
                    faction.removeMember(colonyId);
                    int newOwner = faction.getMemberColonyIds().iterator().next();
                    faction.setOwnerColonyId(newOwner);
                } else {
                    disbandFaction(faction.getId());
                    return;
                }
            } else {
                faction.removeMember(colonyId);
            }
            saveData();
        }
    }

    public static List<FactionData> getAllFactions() {
        return new ArrayList<>(FACTIONS.values());
    }

    // Relation Logic

    public static boolean areAllies(int colonyId1, int colonyId2) {
        FactionData f1 = getFactionByColony(colonyId1);
        FactionData f2 = getFactionByColony(colonyId2);

        if (f1 == null || f2 == null)
            return false;

        // Same faction = allies
        if (f1.getId().equals(f2.getId()))
            return true;

        // Check inter-faction relation
        return f1.getRelation(f2.getId()).isAlly();
    }

    public static boolean areEnemies(int colonyId1, int colonyId2) {
        FactionData f1 = getFactionByColony(colonyId1);
        FactionData f2 = getFactionByColony(colonyId2);

        if (f1 == null || f2 == null)
            return false;
        if (f1.getId().equals(f2.getId()))
            return false; // Same faction cannot be enemies

        return f1.getRelation(f2.getId()).isEnemy();
    }

    // Shared Tax Logic
    // Returns the amount diverted to faction
    public static double processFactionTax(int colonyId, double taxAmount) {
        if (!TaxConfig.isSharedTaxPoolEnabled())
            return 0;

        FactionData faction = getFactionByColony(colonyId);
        if (faction == null)
            return 0;

        double rate = faction.getTaxRate();
        if (rate <= 0)
            return 0;

        double divertedAmount = taxAmount * rate;

        // Cap pool balance
        if (faction.getTaxBalance() + divertedAmount > TaxConfig.getMaxPoolBalance()) {
            divertedAmount = TaxConfig.getMaxPoolBalance() - faction.getTaxBalance();
            if (divertedAmount < 0)
                divertedAmount = 0;
        }

        faction.addTax((long) divertedAmount);
        saveData(); // Save frequently on tax updates

        return divertedAmount;
    }
}
