package net.machiavelli.minecolonytax.espionage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpyManager {

    private static final Logger LOGGER = LogManager.getLogger(SpyManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/espionage.json";

    private static final Map<String, SpyMission> ACTIVE_MISSIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, SpyIntelData> INTEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> PENDING_COSTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Double> SABOTAGE_EFFECTS = new ConcurrentHashMap<>();

    private static MinecraftServer SERVER;

    // Data container for serialization
    private static class SpySaveData {
        Map<String, SpyMission> activeMissions = new ConcurrentHashMap<>();
        Map<Integer, SpyIntelData> intelCache = new ConcurrentHashMap<>();
        Map<String, Long> cooldowns = new ConcurrentHashMap<>();
        Map<Integer, Integer> pendingCosts = new ConcurrentHashMap<>();
        Map<Integer, Double> sabotageEffects = new ConcurrentHashMap<>();
    }

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData();
        LOGGER.info("SpyManager initialized with {} active missions", ACTIVE_MISSIONS.size());
    }

    public static void shutdown() {
        saveData();
    }

    private static void loadData() {
        File file = new File(STORAGE_FILE);
        if (!file.exists()) {
            LOGGER.info("No espionage data file found, starting fresh");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            SpySaveData loaded = GSON.fromJson(reader, SpySaveData.class);
            if (loaded != null) {
                ACTIVE_MISSIONS.clear();
                if (loaded.activeMissions != null)
                    ACTIVE_MISSIONS.putAll(loaded.activeMissions);

                INTEL_CACHE.clear();
                if (loaded.intelCache != null)
                    INTEL_CACHE.putAll(loaded.intelCache);

                COOLDOWNS.clear();
                if (loaded.cooldowns != null)
                    COOLDOWNS.putAll(loaded.cooldowns);

                PENDING_COSTS.clear();
                if (loaded.pendingCosts != null)
                    PENDING_COSTS.putAll(loaded.pendingCosts);

                SABOTAGE_EFFECTS.clear();
                if (loaded.sabotageEffects != null)
                    SABOTAGE_EFFECTS.putAll(loaded.sabotageEffects);
            }
            LOGGER.info("Loaded {} active spy missions", ACTIVE_MISSIONS.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load espionage data: {}", e.getMessage());
        }
    }

    private static void saveData() {
        File file = new File(STORAGE_FILE);
        file.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(file)) {
            SpySaveData data = new SpySaveData();
            data.activeMissions.putAll(ACTIVE_MISSIONS);
            data.intelCache.putAll(INTEL_CACHE);
            data.cooldowns.putAll(COOLDOWNS);
            data.pendingCosts.putAll(PENDING_COSTS);
            data.sabotageEffects.putAll(SABOTAGE_EFFECTS);

            GSON.toJson(data, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save espionage data: {}", e.getMessage());
        }
    }

    public static void deploySpyMission(ServerPlayer player, int attackerColonyId, int targetColonyId,
            String missionType) {
        if (!TaxConfig.isSpySystemEnabled())
            return;

        // Check cooldown
        String cooldownKey = attackerColonyId + ":" + targetColonyId;
        if (isCooldownActive(attackerColonyId, targetColonyId)) {
            player.sendSystemMessage(
                    Component.literal("Espionage is on cooldown for this target.").withStyle(ChatFormatting.RED));
            return;
        }

        int cost = switch (missionType) {
            case "SCOUT" -> TaxConfig.getSpyScoutCost();
            case "SABOTAGE" -> TaxConfig.getSpySabotageCost();
            case "BRIBE" -> TaxConfig.getSpyBribeGuardsCost();
            case "STEAL" -> TaxConfig.getSpyStealSecretsCost();
            default -> 100;
        };

        long maxDurationHours = switch (missionType) {
            case "SCOUT" -> TaxConfig.getSpyScoutMaxDurationHours();
            case "STEAL" -> TaxConfig.getSpyStealSecretsDurationHours();
            default -> 24;
        };
        long maxDurationMs = maxDurationHours * 3600000L;

        String missionId = UUID.randomUUID().toString();
        SpyMission mission = new SpyMission(
                missionId,
                player.getUUID().toString(),
                attackerColonyId,
                targetColonyId,
                missionType,
                System.currentTimeMillis(),
                maxDurationMs,
                "DEPLOYING",
                cost);

        // Add pending cost to tax
        int currentPending = PENDING_COSTS.getOrDefault(attackerColonyId, 0);
        PENDING_COSTS.put(attackerColonyId, currentPending + cost);

        // Phase 4 - Spawn SpyEntity here
        if (SERVER != null) {
            net.minecraft.server.level.ServerLevel level = SERVER.overworld();
            com.minecolonies.api.colony.IColony targetColony = com.minecolonies.api.IMinecoloniesAPI.getInstance()
                    .getColonyManager().getColonyByWorld(targetColonyId, level);
            if (targetColony != null) {
                net.minecraft.core.BlockPos center = targetColony.getCenter();
                // Simple spawn offset towards edge of colony roughly
                net.minecraft.core.BlockPos spawnPos = center.offset(20, 0, 0);
                SpyEntity spy = ModEntities.SPY.get().create(level);
                if (spy != null) {
                    spy.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                    spy.setMissionData(missionId, player.getUUID(), targetColonyId, missionType);
                    level.addFreshEntity(spy);
                    mission.setSpyEntityUUID(spy.getUUID().toString());
                }
            }
        }

        // Set to ACTIVE for now, as if successfully deployed
        mission.setStatus("ACTIVE");

        ACTIVE_MISSIONS.put(missionId, mission);
        saveData();

        player.sendSystemMessage(
                Component.literal("Spy deployed to target colony! Cost will be deducted at next tax generation.")
                        .withStyle(ChatFormatting.GREEN));
    }

    public static void recallSpy(String missionId) {
        SpyMission mission = ACTIVE_MISSIONS.get(missionId);
        if (mission != null) {
            mission.setStatus("COMPLETED");

            // Phase 4 - Despawn SpyEntity here
            if (SERVER != null && mission.getSpyEntityUUID() != null) {
                try {
                    java.util.UUID spyUuid = java.util.UUID.fromString(mission.getSpyEntityUUID());
                    net.minecraft.server.level.ServerLevel level = SERVER.overworld();
                    net.minecraft.world.entity.Entity entity = level.getEntity(spyUuid);
                    if (entity instanceof SpyEntity) {
                        entity.discard();
                    }
                } catch (IllegalArgumentException e) {
                    LOGGER.error("Invalid Spy UUID: {}", mission.getSpyEntityUUID());
                }
            }

            ACTIVE_MISSIONS.remove(missionId);
            saveData();
        }
    }

    public static void onSpyKilled(String missionId) {
        SpyMission mission = ACTIVE_MISSIONS.get(missionId);
        if (mission != null) {
            mission.setStatus("KILLED");

            String cooldownKey = mission.getAttackerColonyId() + ":" + mission.getTargetColonyId();
            COOLDOWNS.put(cooldownKey, System.currentTimeMillis() + (TaxConfig.getSpyCooldownMinutes() * 60000L));

            ACTIVE_MISSIONS.remove(missionId);

            if (SERVER != null) {
                ServerPlayer player = SERVER.getPlayerList().getPlayer(UUID.fromString(mission.getAttackerPlayerId()));
                if (player != null) {
                    player.sendSystemMessage(Component
                            .literal("Your spy in colony " + mission.getTargetColonyId() + " has been eliminated!")
                            .withStyle(ChatFormatting.RED));
                }
            }

            saveData();
        }
    }

    public static void onSpyDetected(String missionId) {
        // Handled in Phase 6 - for now just killed
        onSpyKilled(missionId);
    }

    public static void gatherIntel(int targetColonyId) {
        if (SERVER == null)
            return;

        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByWorld(targetColonyId,
                SERVER.overworld());
        if (colony == null)
            return;

        int guardCount = (int) colony.getCitizenManager().getCitizens().stream()
                .filter(c -> c.getJob() != null
                        && c.getJob().getJobRegistryEntry().getKey().getPath().contains("guard"))
                .count();

        SpyIntelData intel = new SpyIntelData(
                colony.getCitizenManager().getCitizens().size(),
                guardCount,
                colony.getOverallHappiness(),
                TaxManager.getStoredTaxForColony(colony),
                colony.getBuildingManager().getBuildings().size(),
                WarSystem.isColonyInWar(colony.getID()),
                colony.getName(),
                System.currentTimeMillis());

        INTEL_CACHE.put(targetColonyId, intel);
    }

    public static SpyIntelData getIntel(int targetColonyId) {
        return INTEL_CACHE.get(targetColonyId);
    }

    public static List<SpyMission> getActiveMissionsForPlayer(String playerId) {
        return ACTIVE_MISSIONS.values().stream()
                .filter(m -> m.getAttackerPlayerId().equals(playerId))
                .toList();
    }

    public static int consumePendingCost(int colonyId) {
        int cost = PENDING_COSTS.getOrDefault(colonyId, 0);
        PENDING_COSTS.remove(colonyId);
        // Save handled by callers or periodically
        return cost;
    }

    public static boolean isCooldownActive(int attackerColonyId, int targetColonyId) {
        String key = attackerColonyId + ":" + targetColonyId;
        Long cooldownEnd = COOLDOWNS.get(key);
        if (cooldownEnd == null)
            return false;

        if (System.currentTimeMillis() > cooldownEnd) {
            COOLDOWNS.remove(key);
            return false;
        }
        return true;
    }

    public static double getSabotageReduction(int colonyId) {
        return SABOTAGE_EFFECTS.getOrDefault(colonyId, 0.0);
    }

    public static void clearSabotageEffect(int colonyId) {
        SABOTAGE_EFFECTS.remove(colonyId);
    }

    public static void applySabotageEffect(int colonyId, double reductionPercent) {
        SABOTAGE_EFFECTS.put(colonyId, reductionPercent);
        saveData();
    }
}
