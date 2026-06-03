package net.machiavelli.minecolonytax.espionage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
import net.machiavelli.minecolonytax.compat.ColonyBuildingUtil;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.TaxManager;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.integration.SDMShopIntegration;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpyManager {

    private static final Logger LOGGER = LogManager.getLogger(SpyManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/espionage.json";

    private static final Map<String, SpyMission> ACTIVE_MISSIONS = new ConcurrentHashMap<>();
    // Completed/escaped/recalled missions — available for player viewing; cleaned after 1 hour
    private static final Map<String, SpyMission> COMPLETED_MISSIONS = new ConcurrentHashMap<>();
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> PENDING_COSTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Double> SABOTAGE_EFFECTS = new ConcurrentHashMap<>();
    // targetColonyId -> number of guards to debuff when next raided
    private static final Map<Integer, Integer> BRIBED_GUARDS = new ConcurrentHashMap<>();
    // attackerColonyId -> expiration timestamp (millis) for the tax bonus
    private static final Map<Integer, Long> STOLEN_SECRETS_BUFF = new ConcurrentHashMap<>();

    // playerUUID (string) -> list of mission IDs whose rewards (intel book / map) couldn't be delivered
    // because the player was offline at completion. Drained on next deliverRewards call.
    // See audit/defensive_03_espionage.md / CODEX_INDEPENDENT.md MED-5.
    private static final Map<String, List<String>> PENDING_REWARDS = new ConcurrentHashMap<>();

    // missionId -> "chunkX,chunkZ" encoded chunks that WnT force-loaded during spawn.
    // Only these chunks are released on despawn so we don't clobber force-loads owned by other systems.
    // See audit/CODEX_INDEPENDENT.md MED-6.
    private static final Map<String, String> FORCED_CHUNKS = new ConcurrentHashMap<>();

    private static final long COMPLETED_MISSION_TTL_MS = 3600_000L; // 1 hour

    private static MinecraftServer SERVER;

    private static class SpySaveData {
        Map<String, SpyMission> activeMissions = new HashMap<>();
        Map<String, SpyMission> completedMissions = new HashMap<>();
        Map<String, Long> cooldowns = new HashMap<>();
        Map<Integer, Integer> pendingCosts = new HashMap<>();
        Map<Integer, Double> sabotageEffects = new HashMap<>();
        Map<Integer, Integer> bribedGuards = new HashMap<>();
        Map<Integer, Long> stolenSecretsBuff = new HashMap<>();
    }

    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData();
        if (TaxConfig.isNormalLogging()) LOGGER.info("SpyManager initialized with {} active missions", ACTIVE_MISSIONS.size());
    }

    public static void shutdown() {
        saveData();
    }

    private static void loadData() {
        // Always clear static state up front so a fresh server / different singleplayer world never inherits
        // missions from the previous JVM lifetime. Audit/defensive_03_espionage.md C3 — the old code only
        // cleared the maps inside the loaded != null branch, so a missing/corrupt JSON would leak state.
        ACTIVE_MISSIONS.clear();
        COMPLETED_MISSIONS.clear();
        COOLDOWNS.clear();
        PENDING_COSTS.clear();
        SABOTAGE_EFFECTS.clear();
        BRIBED_GUARDS.clear();
        STOLEN_SECRETS_BUFF.clear();
        PENDING_REWARDS.clear();

        File file = new File(STORAGE_FILE);
        if (!file.exists()) {
            if (TaxConfig.isNormalLogging()) LOGGER.info("No espionage data file found, starting fresh");
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            SpySaveData loaded = GSON.fromJson(reader, SpySaveData.class);
            if (loaded != null) {
                if (loaded.activeMissions != null)
                    ACTIVE_MISSIONS.putAll(loaded.activeMissions);

                if (loaded.completedMissions != null)
                    COMPLETED_MISSIONS.putAll(loaded.completedMissions);

                if (loaded.cooldowns != null)
                    COOLDOWNS.putAll(loaded.cooldowns);

                if (loaded.pendingCosts != null)
                    PENDING_COSTS.putAll(loaded.pendingCosts);

                if (loaded.sabotageEffects != null)
                    SABOTAGE_EFFECTS.putAll(loaded.sabotageEffects);

                if (loaded.bribedGuards != null)
                    BRIBED_GUARDS.putAll(loaded.bribedGuards);

                if (loaded.stolenSecretsBuff != null)
                    STOLEN_SECRETS_BUFF.putAll(loaded.stolenSecretsBuff);
            }
            if (TaxConfig.isNormalLogging()) LOGGER.info("Loaded {} active spy missions, {} completed missions",
                    ACTIVE_MISSIONS.size(), COMPLETED_MISSIONS.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load espionage data: {}", e.getMessage());
        }
    }

    private static void saveData() {
        // Snapshot under fresh containers on the calling (server) thread so
        // the worker can never race with main-thread map mutations.
        final SpySaveData data = new SpySaveData();
        data.activeMissions.putAll(ACTIVE_MISSIONS);
        data.completedMissions.putAll(COMPLETED_MISSIONS);
        data.cooldowns.putAll(COOLDOWNS);
        data.pendingCosts.putAll(PENDING_COSTS);
        data.sabotageEffects.putAll(SABOTAGE_EFFECTS);
        data.bribedGuards.putAll(BRIBED_GUARDS);
        data.stolenSecretsBuff.putAll(STOLEN_SECRETS_BUFF);

        final File file = new File(STORAGE_FILE);
        net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("espionage", () -> {
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(data, writer);
            } catch (Exception e) {
                LOGGER.error("Failed to save espionage data: {}", e.getMessage());
            }
        });
    }

    // ======================== DEPLOY ========================

    public static void deploySpyMission(ServerPlayer player, int attackerColonyId, int targetColonyId,
            String missionType) {
        if (!TaxConfig.isSpySystemEnabled())
            return;

        if (attackerColonyId == targetColonyId) {
            player.sendSystemMessage(
                    Component.literal("You cannot spy on your own colony.").withStyle(ChatFormatting.RED));
            return;
        }

        String playerId = player.getUUID().toString();
        int maxSpies = TaxConfig.getSpyMaxActivePerPlayer();
        try {
            maxSpies += net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager.getSpyCapacityBonus(attackerColonyId);
        } catch (Exception e) {
            // ColonyUpgradeManager unavailable — use config value as-is
        }
        long activeCount = ACTIVE_MISSIONS.values().stream()
                .filter(m -> m.getAttackerPlayerId().equals(playerId))
                .count();
        if (activeCount >= maxSpies) {
            player.sendSystemMessage(
                    Component.literal("You have reached the maximum number of active spy missions ("
                            + maxSpies + ").")
                            .withStyle(ChatFormatting.RED));
            return;
        }

        boolean alreadyTargeting = ACTIVE_MISSIONS.values().stream()
                .anyMatch(m -> m.getAttackerPlayerId().equals(playerId)
                        && m.getTargetColonyId() == targetColonyId);
        if (alreadyTargeting) {
            player.sendSystemMessage(
                    Component.literal("You already have an active mission targeting this colony.")
                            .withStyle(ChatFormatting.RED));
            return;
        }

        if (isCooldownActive(attackerColonyId, targetColonyId)) {
            player.sendSystemMessage(
                    Component.literal("Espionage is on cooldown for this target.").withStyle(ChatFormatting.RED));
            return;
        }

        // Block spy deployment if attacker colony is at maximum debt
        if (TaxConfig.isDebtBlocksSpy() && TaxConfig.getDebtLimit() > 0) {
            int attackerBalance = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColonyId(attackerColonyId);
            if (attackerBalance <= -TaxConfig.getDebtLimit()) {
                player.sendSystemMessage(Component.literal(
                        "Your colony is bankrupt! Pay off your tax debt before deploying spies.")
                        .withStyle(ChatFormatting.RED));
                return;
            }
        }

        int cost = switch (missionType) {
            case "SCOUT" -> TaxConfig.getSpyScoutCost();
            case "SABOTAGE" -> TaxConfig.getSpySabotageCost();
            case "BRIBE" -> TaxConfig.getSpyBribeGuardsCost();
            case "STEAL" -> TaxConfig.getSpyStealSecretsCost();
            default -> 100;
        };

        long maxDurationHours = TaxConfig.getSpyScoutMaxDurationHours();
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

        if ("SCOUT".equals(missionType)) {
            mission.setMissionIntel(new SpyIntelData());
        }

        // Validate target colony exists before committing resources
        if (SERVER != null) {
            IColony targetCheck = IMinecoloniesAPI.getInstance()
                    .getColonyManager().getColonyByWorld(targetColonyId, SERVER.overworld());
            if (targetCheck == null) {
                player.sendSystemMessage(
                        Component.literal("Target colony does not exist.").withStyle(ChatFormatting.RED));
                return;
            }
            // Prevent spying on a colony where the player is an owner or officer
            if (net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.isOwnerOrOfficer(targetCheck, player.getUUID())) {
                player.sendSystemMessage(
                        Component.literal("You cannot spy on a colony you are an officer of.").withStyle(ChatFormatting.RED));
                return;
            }
            mission.setTargetColonyName(targetCheck.getName());
        }

        int srcX = 0, srcZ = 0, dstX = 0, dstZ = 0;
        long travelDurationMs = TaxConfig.getSpyTravelMinMinutes() * 60000L;
        if (SERVER != null) {
            ServerLevel level = SERVER.overworld();
            IColony attackerColony = IMinecoloniesAPI.getInstance()
                    .getColonyManager().getColonyByWorld(attackerColonyId, level);
            IColony targetColony = IMinecoloniesAPI.getInstance()
                    .getColonyManager().getColonyByWorld(targetColonyId, level);
            if (attackerColony != null && targetColony != null) {
                net.minecraft.core.BlockPos src = attackerColony.getCenter();
                net.minecraft.core.BlockPos dst = targetColony.getCenter();
                srcX = src.getX(); srcZ = src.getZ();
                dstX = dst.getX(); dstZ = dst.getZ();
                double dx = dstX - srcX;
                double dz = dstZ - srcZ;
                double distance = Math.sqrt(dx * dx + dz * dz);
                int blocksPerMinute = TaxConfig.getSpyTravelBlocksPerMinute();
                double speedMultiplier = 1.0;
                try {
                    speedMultiplier += net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager.getSpySpeedMultiplier(attackerColonyId);
                } catch (Exception e) {
                    // ColonyUpgradeManager unavailable — no speed bonus
                }
                double travelMinutes = distance / (blocksPerMinute * speedMultiplier);
                long minMs = TaxConfig.getSpyTravelMinMinutes() * 60000L;
                long maxMs = TaxConfig.getSpyTravelMaxMinutes() * 60000L;
                travelDurationMs = Math.max(minMs, Math.min(maxMs, (long) (travelMinutes * 60000)));
            } else if (targetColony != null) {
                net.minecraft.core.BlockPos dst = targetColony.getCenter();
                dstX = dst.getX(); dstZ = dst.getZ();
            }
        }

        mission.setSourceX(srcX);
        mission.setSourceZ(srcZ);
        mission.setDestX(dstX);
        mission.setDestZ(dstZ);
        mission.setTravelDurationMs(travelDurationMs);

        int currentPending = PENDING_COSTS.getOrDefault(attackerColonyId, 0);
        PENDING_COSTS.put(attackerColonyId, currentPending + cost);

        // Entity spawns after travel completes — do NOT spawn now
        ACTIVE_MISSIONS.put(missionId, mission);
        String _logTargetName = mission.getTargetColonyName() != null ? mission.getTargetColonyName() : "colony#" + targetColonyId;
        int _atkBal = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColonyId(attackerColonyId);
        int _tgtBal = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColonyId(targetColonyId);
        net.machiavelli.minecolonytax.data.HistoryManager.logWithBalance(attackerColonyId, "SPY",
                String.format("Deployed %s spy to %s (ETA %dm)", missionType,
                        _logTargetName, travelDurationMs / 60000L), _atkBal, _atkBal);
        net.machiavelli.minecolonytax.data.HistoryManager.logWithBalance(targetColonyId, "SPY",
                "Incoming spy detected in transit (type unknown)", _tgtBal, _tgtBal);
        saveData();

        long etaMinutes = travelDurationMs / 60000L;
        player.sendSystemMessage(
                Component.literal("Spy deployed! Traveling to target colony... ETA: ~" + etaMinutes + " minute(s).")
                        .withStyle(ChatFormatting.GREEN));
    }

    // ======================== RECALL ========================

    public static void recallSpy(String missionId) {
        SpyMission mission = ACTIVE_MISSIONS.get(missionId);
        if (mission == null) return;

        int _atkRecBal = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColonyId(mission.getAttackerColonyId());
        int _tgtRecBal = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColonyId(mission.getTargetColonyId());
        if ("DEPLOYING".equals(mission.getStatus())) {
            // Spy is still en route — cancel immediately, no return travel.
            // Refund the queued cost: the spy never reached the field, so the colony pays nothing.
            refundPendingCost(mission);
            mission.setStatus("RECALLED");
            net.machiavelli.minecolonytax.data.HistoryManager.logWithBalance(mission.getAttackerColonyId(), "SPY",
                    String.format("%s spy recalled en route to %s",
                            mission.getMissionType(), mission.getTargetColonyName()),
                    _atkRecBal, _atkRecBal);
            ACTIVE_MISSIONS.remove(missionId);
            COMPLETED_MISSIONS.put(missionId, mission);
            saveData();
            giveIntelBook(mission);
            pushSpyDataToPlayer(mission.getAttackerPlayerId());
            notifyPlayer(mission.getAttackerPlayerId(),
                    Component.literal("Spy recalled before reaching target.").withStyle(ChatFormatting.YELLOW));
        } else {
            // Spy is in the field — despawn entity and begin return journey.
            // Refund the queued cost: a recalled mission is voluntarily cancelled by the attacker.
            // Per audit/defensive_03_espionage.md C2, leaving the cost queued is exploitable for self-griefing.
            refundPendingCost(mission);
            despawnSpyEntity(mission);
            mission.setStatus("RETURNING");
            mission.setRecallStartTime(System.currentTimeMillis());
            int tier = mission.getMissionIntel() != null ? mission.getMissionIntel().getIntelTier() : 0;
            net.machiavelli.minecolonytax.data.HistoryManager.logWithBalance(mission.getAttackerColonyId(), "SPY",
                    String.format("%s spy recalled from %s (tier %d intel)",
                            mission.getMissionType(), mission.getTargetColonyName(), tier),
                    _atkRecBal, _atkRecBal);
            net.machiavelli.minecolonytax.data.HistoryManager.logWithBalance(mission.getTargetColonyId(), "SPY",
                    "Enemy spy recalled (left colony)", _tgtRecBal, _tgtRecBal);
            saveData();
            pushSpyDataToPlayer(mission.getAttackerPlayerId());
            long etaMinutes = mission.getTravelDurationMs() / 60000L;
            notifyPlayer(mission.getAttackerPlayerId(),
                    Component.literal("Spy recalled — returning home. ETA: ~" + etaMinutes + " minute(s).")
                            .withStyle(ChatFormatting.YELLOW));
        }
    }

    // ======================== DETECTION & FLEE ========================

    /**
     * Called when a spy entity enters flee state (detected by guard or player damage).
     * Sets mission status to FLEEING and notifies defenders.
     * Does NOT kill the spy.
     */
    public static void onSpyDetected(String missionId) {
        SpyMission mission = ACTIVE_MISSIONS.get(missionId);
        if (mission != null) {
            mission.setStatus("FLEEING");
            notifyColonyOfficers(mission.getTargetColonyId(),
                    Component.literal("Your guards detected an enemy spy — they are trying to flee!")
                            .withStyle(ChatFormatting.YELLOW));
            saveData();
        }
    }

    /**
     * Called when a spy successfully escapes (exits colony + buffer).
     * Preserves gathered intel; moves mission to COMPLETED_MISSIONS.
     */
    public static void onSpyEscaped(String missionId) {
        SpyMission mission = ACTIVE_MISSIONS.get(missionId);
        if (mission != null) {
            mission.setStatus("ESCAPED");

            String colonyName = getColonyName(mission.getTargetColonyId());

            // SABOTAGE: apply effect only if spy had been active long enough before detection
            if ("SABOTAGE".equals(mission.getMissionType())) {
                long fieldStartTime = mission.getStartTime() + mission.getTravelDurationMs();
                long fieldTimeElapsedMs = System.currentTimeMillis() - fieldStartTime;
                long minFieldTimeMs = TaxConfig.getSpySabotageMinFieldMinutes() * 60_000L;
                if (fieldTimeElapsedMs >= minFieldTimeMs) {
                    double reduction = TaxConfig.getSpySabotageTaxReductionPercent();
                    applySabotageEffect(mission.getTargetColonyId(), reduction);
                    notifyPlayer(mission.getAttackerPlayerId(),
                            Component.literal("Your spy escaped! Sabotage planted — " + colonyName
                                    + "'s next tax cycle reduced by " + (int) (reduction * 100) + "%.")
                                    .withStyle(ChatFormatting.GREEN));
                    notifyColonyOfficers(mission.getTargetColonyId(),
                            Component.literal("An enemy spy escaped — sabotage may have been planted!")
                                    .withStyle(ChatFormatting.RED));
                } else {
                    notifyPlayer(mission.getAttackerPlayerId(),
                            Component.literal("Your spy escaped from " + colonyName
                                    + " but was detected too early — no sabotage planted.")
                                    .withStyle(ChatFormatting.YELLOW));
                    notifyColonyOfficers(mission.getTargetColonyId(),
                            Component.literal("An enemy spy escaped from your colony!")
                                    .withStyle(ChatFormatting.RED));
                }
            } else {
                String intelMsg = mission.getMissionIntel() != null && mission.getMissionIntel().getIntelTier() > 0
                        ? "Your spy escaped from " + colonyName + " with Tier " +
                          mission.getMissionIntel().getIntelTier() + " intel!"
                        : "Your spy escaped from " + colonyName + " but gathered no intel.";
                notifyPlayer(mission.getAttackerPlayerId(),
                        Component.literal(intelMsg).withStyle(ChatFormatting.GREEN));

                notifyColonyOfficers(mission.getTargetColonyId(),
                        Component.literal("An enemy spy escaped from your colony!")
                                .withStyle(ChatFormatting.RED));
            }

            String cooldownKey = mission.getAttackerColonyId() + ":" + mission.getTargetColonyId();
            COOLDOWNS.put(cooldownKey, System.currentTimeMillis() + (TaxConfig.getSpyCooldownMinutes() * 60000L));

            String spyColonyName = getColonyName(mission.getTargetColonyId());
            int tier = mission.getMissionIntel() != null ? mission.getMissionIntel().getIntelTier() : 0;
            int _tgtEscBal = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColonyId(mission.getTargetColonyId());
            int _atkEscBal = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColonyId(mission.getAttackerColonyId());
            net.machiavelli.minecolonytax.data.HistoryManager.logWithBalance(mission.getTargetColonyId(), "SPY",
                    String.format("Enemy %s spy escaped (tier %d intel)", mission.getMissionType(), tier),
                    _tgtEscBal, _tgtEscBal);
            net.machiavelli.minecolonytax.data.HistoryManager.logWithBalance(mission.getAttackerColonyId(), "SPY",
                    String.format("%s spy escaped from %s with tier %d intel",
                            mission.getMissionType(), spyColonyName, tier),
                    _atkEscBal, _atkEscBal);
            ACTIVE_MISSIONS.remove(missionId);
            COMPLETED_MISSIONS.put(missionId, mission);
            saveData();
            giveIntelBook(mission);
            giveSpyMap(mission);
            pushSpyDataToPlayer(mission.getAttackerPlayerId());
        }
    }

    // ======================== KILLED ========================

    public static void onSpyKilled(String missionId) {
        SpyMission mission = ACTIVE_MISSIONS.get(missionId);
        if (mission != null) {
            // Refund queued cost: a killed spy delivered nothing of value to the attacker colony.
            // See audit/defensive_03_espionage.md C2.
            refundPendingCost(mission);

            mission.setStatus("KILLED");

            // Intel is destroyed when the spy is caught — prevents the attacker from benefiting
            mission.setMissionIntel(null);

            String cooldownKey = mission.getAttackerColonyId() + ":" + mission.getTargetColonyId();
            COOLDOWNS.put(cooldownKey, System.currentTimeMillis() + (TaxConfig.getSpyCooldownMinutes() * 60000L));

            int _tgtKilBal = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColonyId(mission.getTargetColonyId());
            int _atkKilBal = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColonyId(mission.getAttackerColonyId());
            net.machiavelli.minecolonytax.data.HistoryManager.logWithBalance(mission.getTargetColonyId(), "SPY",
                    String.format("Enemy %s spy killed (intel destroyed)", mission.getMissionType()),
                    _tgtKilBal, _tgtKilBal);
            net.machiavelli.minecolonytax.data.HistoryManager.logWithBalance(mission.getAttackerColonyId(), "SPY",
                    String.format("%s spy killed in %s (intel lost)", mission.getMissionType(),
                            getColonyName(mission.getTargetColonyId())),
                    _atkKilBal, _atkKilBal);
            ACTIVE_MISSIONS.remove(missionId);
            // Killed missions are not stored in COMPLETED_MISSIONS — nothing to view

            notifyPlayer(mission.getAttackerPlayerId(),
                    Component.literal("Your spy in " + getColonyName(mission.getTargetColonyId())
                            + " has been eliminated!").withStyle(ChatFormatting.RED));

            notifyColonyOfficers(mission.getTargetColonyId(),
                    Component.literal("Your guards caught and eliminated an enemy spy!")
                            .withStyle(ChatFormatting.GREEN));

            saveData();
            pushSpyDataToPlayer(mission.getAttackerPlayerId());
        }
    }

    // ======================== PROGRESSIVE INTEL ========================

    /**
     * Called every ~60 seconds from SpyEntity while INFILTRATING.
     * Advances intel tier based on elapsed mission time vs config thresholds.
     * Sends chat notification on tier transitions.
     */
    public static void progressIntel(String missionId) {
        SpyMission mission = ACTIVE_MISSIONS.get(missionId);
        if (mission == null || SERVER == null) return;

        SpyIntelData intel = mission.getMissionIntel();
        if (intel == null) {
            intel = new SpyIntelData();
            mission.setMissionIntel(intel);
        }

        // Count intel time from arrival, not deploy — spy can't gather intel while traveling
        long elapsedMs = System.currentTimeMillis() - (mission.getStartTime() + mission.getTravelDurationMs());
        if (elapsedMs < 0) return; // Still traveling
        long elapsedMinutes = elapsedMs / 60000L;

        int previousTier = intel.getIntelTier();
        int earlyMin = TaxConfig.getIntelEarlyThresholdMinutes();
        int midMin = TaxConfig.getIntelMidThresholdMinutes();
        int lateMin = TaxConfig.getIntelLateThresholdMinutes();

        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager()
                .getColonyByWorld(mission.getTargetColonyId(), SERVER.overworld());
        if (colony == null) return;

        // Tier 1 — early intel
        if (elapsedMinutes >= earlyMin && previousTier < 1) {
            gatherTier1Intel(intel, colony);
            intel.advanceToTier(1);
            notifyPlayer(mission.getAttackerPlayerId(),
                    Component.literal("Your spy in " + colony.getName() +
                            " has gathered early intelligence (Tier 1).")
                            .withStyle(ChatFormatting.YELLOW));
            LOGGER.debug("Mission {} advanced to Tier 1 intel", missionId);
        }

        // Tier 2 — mid intel
        if (elapsedMinutes >= midMin && previousTier < 2) {
            gatherTier2Intel(intel, colony);
            intel.advanceToTier(2);
            notifyPlayer(mission.getAttackerPlayerId(),
                    Component.literal("Your spy in " + colony.getName() +
                            " has gathered tactical intelligence (Tier 2).")
                            .withStyle(ChatFormatting.YELLOW));
            LOGGER.debug("Mission {} advanced to Tier 2 intel", missionId);
        }

        // Tier 3 — deep intel
        if (elapsedMinutes >= lateMin && previousTier < 3) {
            gatherTier3Intel(intel, colony, mission.getAttackerPlayerId());
            intel.advanceToTier(3);
            notifyPlayer(mission.getAttackerPlayerId(),
                    Component.literal("Your spy in " + colony.getName() +
                            " has gathered deep intelligence (Tier 3) including financial data!")
                            .withStyle(ChatFormatting.GOLD));
            LOGGER.debug("Mission {} advanced to Tier 3 intel", missionId);
        }

        if (intel.getIntelTier() > previousTier) {
            saveData();
            pushSpyDataToPlayer(mission.getAttackerPlayerId());
        }
    }

    /**
     * Generates and gives a Written Book intel report to the mission's attacker.
     * Drops to ground if inventory is full. No-op for KILLED missions.
     * If the player is offline, queues the mission ID in PENDING_REWARDS so the book can be re-issued
     * on the next deliverPendingRewards() call (login or GUI open). See audit MED-5.
     */
    private static void giveIntelBook(SpyMission mission) {
        if (SERVER == null) return;
        try {
            ServerPlayer player = SERVER.getPlayerList().getPlayer(UUID.fromString(mission.getAttackerPlayerId()));
            if (player == null) {
                queuePendingReward(mission);
                return;
            }
            String colonyName = getColonyName(mission.getTargetColonyId());
            ItemStack book = SpyIntelBookGenerator.createIntelReport(mission, colonyName);
            if (book != null) {
                if (!player.getInventory().add(book)) {
                    player.drop(book, false);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to give intel book to player {}: {}", mission.getAttackerPlayerId(), e.getMessage());
        }
    }

    /**
     * Queues a mission ID as a pending reward for an offline attacker.
     * Deduplicated so repeated callbacks (e.g. recall preserves intel; future re-delivery) don't stack copies.
     */
    private static void queuePendingReward(SpyMission mission) {
        if (mission == null || mission.getAttackerPlayerId() == null) return;
        PENDING_REWARDS.compute(mission.getAttackerPlayerId(), (k, list) -> {
            if (list == null) list = new ArrayList<>();
            if (!list.contains(mission.getMissionId())) list.add(mission.getMissionId());
            return list;
        });
    }

    /**
     * Delivers any queued spy rewards (intel book / map) to a player who has come back online.
     * Safe to call repeatedly — drains the per-player queue. Call from PlayerLoggedInEvent or GUI-open hook.
     */
    public static void deliverPendingRewards(ServerPlayer player) {
        if (player == null) return;
        List<String> queued = PENDING_REWARDS.remove(player.getUUID().toString());
        if (queued == null || queued.isEmpty()) return;
        for (String mid : queued) {
            SpyMission mission = COMPLETED_MISSIONS.get(mid);
            if (mission == null) continue;
            try {
                String colonyName = getColonyName(mission.getTargetColonyId());
                ItemStack book = SpyIntelBookGenerator.createIntelReport(mission, colonyName);
                if (book != null) {
                    if (!player.getInventory().add(book)) player.drop(book, false);
                }
                if (TaxConfig.isSpyMapEnabled() && SERVER != null) {
                    ServerLevel level = SERVER.overworld();
                    ItemStack map = SpyMapGenerator.generateColonyMap(level, mission.getDestX(),
                            mission.getDestZ(), colonyName);
                    if (map != null) {
                        if (!player.getInventory().add(map)) player.drop(map, false);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to deliver pending reward for mission {}: {}", mid, e.getMessage());
            }
        }
        saveData();
    }

    /**
     * Generates and gives a pre-filled map of the target colony area to the mission's attacker.
     * Only called when the spy physically reached the target (ACTIVE, FLEEING, RETURNING phases) —
     * never called for recalls before the spy arrived (DEPLOYING → RECALLED).
     * Drops to ground if inventory is full. No-op when map generation is disabled.
     */
    private static void giveSpyMap(SpyMission mission) {
        if (!TaxConfig.isSpyMapEnabled()) return;
        if (SERVER == null) return;
        try {
            ServerPlayer player = SERVER.getPlayerList().getPlayer(UUID.fromString(mission.getAttackerPlayerId()));
            if (player == null) {
                // Player offline — queue so map can be re-issued on next login alongside the intel book.
                // See audit/CODEX_INDEPENDENT.md MED-5.
                queuePendingReward(mission);
                return;
            }
            ServerLevel level = SERVER.overworld();
            String colonyName = getColonyName(mission.getTargetColonyId());
            ItemStack map = SpyMapGenerator.generateColonyMap(level, mission.getDestX(), mission.getDestZ(), colonyName);
            if (map != null) {
                if (!player.getInventory().add(map)) {
                    player.drop(map, false);
                }
                if (TaxConfig.isNormalLogging())
                    LOGGER.info("Gave spy map of {} to player {}", colonyName, mission.getAttackerPlayerId());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to give spy map to player {}: {}", mission.getAttackerPlayerId(), e.getMessage());
        }
    }

    /**
     * Push updated spy data to a player's GUI (if they have TaxManagementScreen open).
     */
    private static void pushSpyDataToPlayer(String playerUuid) {
        if (SERVER == null) return;
        try {
            ServerPlayer player = SERVER.getPlayerList().getPlayer(UUID.fromString(playerUuid));
            if (player != null) {
                List<SpyMission> missions = getActiveMissionsForPlayer(playerUuid);
                net.machiavelli.minecolonytax.network.NetworkHandler.sendToPlayer(player,
                        new net.machiavelli.minecolonytax.network.packets.SpyDataResponsePacket(missions));
            }
        } catch (Exception e) {
            LOGGER.debug("Could not push spy data to player {}: {}", playerUuid, e.getMessage());
        }
    }

    private static void gatherTier1Intel(SpyIntelData intel, IColony colony) {
        intel.setTargetColonyName(colony.getName());
        intel.setCitizenCount(colony.getCitizenManager().getCitizens().size());
        intel.setBuildingCount(ColonyBuildingUtil.getBuildings(colony).size());
        intel.setAtWar(WarSystem.isColonyInWar(colony.getID()));
        intel.setLastUpdated(System.currentTimeMillis());
    }

    private static void gatherTier2Intel(SpyIntelData intel, IColony colony) {
        int guardCount = (int) colony.getCitizenManager().getCitizens().stream()
                .filter(c -> c.getJob() != null
                        && c.getJob().getJobRegistryEntry().getKey().getPath().contains("guard"))
                .count();
        intel.setGuardCount(guardCount);
        intel.setHappiness(colony.getOverallHappiness());
        intel.setTaxBalance(TaxManager.getStoredTaxForColony(colony));
        intel.setLastUpdated(System.currentTimeMillis());
    }

    private static void gatherTier3Intel(SpyIntelData intel, IColony colony, String attackerPlayerId) {
        IPermissions perms = colony.getPermissions();

        UUID ownerUuid = perms.getOwner();
        if (ownerUuid != null) {
            ServerPlayer ownerPlayer = SERVER.getPlayerList().getPlayer(ownerUuid);
            if (ownerPlayer != null) {
                intel.setOwnerName(ownerPlayer.getName().getString());
                intel.setOwnerSdmBalance(SDMShopIntegration.getMoney(ownerPlayer));
            } else {
                // Owner is offline — try to get name from profile cache
                com.mojang.authlib.GameProfile profile = SERVER.getProfileCache() != null
                        ? SERVER.getProfileCache().get(ownerUuid).map(p -> p).orElse(null)
                        : null;
                intel.setOwnerName(profile != null ? profile.getName() : ownerUuid.toString().substring(0, 8));
                intel.setOwnerSdmBalance(-1); // offline
            }
        }

        List<SpyIntelData.OfficerFinanceInfo> officerList = new ArrayList<>();
        for (ColonyPlayer cp : perms.getPlayersByRank(perms.getRankOfficer())) {
            if (officerList.size() >= 10) break;
            ServerPlayer officerPlayer = SERVER.getPlayerList().getPlayer(cp.getID());
            String name;
            long balance;
            if (officerPlayer != null) {
                name = officerPlayer.getName().getString();
                balance = SDMShopIntegration.getMoney(officerPlayer);
            } else {
                com.mojang.authlib.GameProfile profile = SERVER.getProfileCache() != null
                        ? SERVER.getProfileCache().get(cp.getID()).map(p -> p).orElse(null)
                        : null;
                name = profile != null ? profile.getName() : cp.getID().toString().substring(0, 8);
                balance = -1;
            }
            officerList.add(new SpyIntelData.OfficerFinanceInfo(name, balance));
        }
        intel.setOfficerFinances(officerList);

        // Owner first, then officers, then remaining members — up to 20 total
        List<SpyIntelData.ColonyMemberInfo> memberList = new ArrayList<>();
        Set<UUID> added = new HashSet<>();

        // Add owner
        if (ownerUuid != null && !added.contains(ownerUuid)) {
            String name = intel.getOwnerName() != null ? intel.getOwnerName() : ownerUuid.toString().substring(0, 8);
            boolean online = SERVER.getPlayerList().getPlayer(ownerUuid) != null;
            memberList.add(new SpyIntelData.ColonyMemberInfo(name, "Owner", online));
            added.add(ownerUuid);
        }

        // Add officers
        for (ColonyPlayer cp : perms.getPlayersByRank(perms.getRankOfficer())) {
            if (memberList.size() >= 20) break;
            if (added.contains(cp.getID())) continue;
            boolean online = SERVER.getPlayerList().getPlayer(cp.getID()) != null;
            String name;
            com.mojang.authlib.GameProfile profile = SERVER.getProfileCache() != null
                    ? SERVER.getProfileCache().get(cp.getID()).map(p -> p).orElse(null)
                    : null;
            name = profile != null ? profile.getName() : cp.getID().toString().substring(0, 8);
            memberList.add(new SpyIntelData.ColonyMemberInfo(name, "Officer", online));
            added.add(cp.getID());
        }

        // Add remaining members (Friend rank and below, up to 20 total)
        Map<UUID, ColonyPlayer> allPlayers = perms.getPlayers();
        if (allPlayers != null) {
            List<Map.Entry<UUID, ColonyPlayer>> sorted = new ArrayList<>(allPlayers.entrySet());
            // Sort by rank priority — rank ID lower = higher rank; alphabetically within rank
            sorted.sort((a, b) -> {
                int ra = a.getValue().getRank().getId();
                int rb = b.getValue().getRank().getId();
                return ra != rb ? Integer.compare(ra, rb) : 0;
            });
            for (Map.Entry<UUID, ColonyPlayer> entry : sorted) {
                if (memberList.size() >= 20) break;
                if (added.contains(entry.getKey())) continue;
                boolean online = SERVER.getPlayerList().getPlayer(entry.getKey()) != null;
                com.mojang.authlib.GameProfile profile = SERVER.getProfileCache() != null
                        ? SERVER.getProfileCache().get(entry.getKey()).map(p -> p).orElse(null)
                        : null;
                String name = profile != null ? profile.getName() : entry.getKey().toString().substring(0, 8);
                String rankName = entry.getValue().getRank().getName();
                memberList.add(new SpyIntelData.ColonyMemberInfo(name, rankName, online));
                added.add(entry.getKey());
            }
        }

        intel.setColonyMembers(memberList);
        intel.setLastUpdated(System.currentTimeMillis());
    }

    // ======================== TICK & AUTO-COMPLETION ========================

    /**
     * Called once per second from TaxManager.TickEventHandler.
     */
    public static void tick() {
        long now = System.currentTimeMillis();
        boolean anyProcessed = false;

        if (!ACTIVE_MISSIONS.isEmpty()) {
            List<String> missionIds = new ArrayList<>(ACTIVE_MISSIONS.keySet());

            for (String missionId : missionIds) {
                SpyMission mission = ACTIVE_MISSIONS.get(missionId);
                if (mission == null) continue;

                if ("DEPLOYING".equals(mission.getStatus())) {
                    if (now - mission.getStartTime() >= mission.getTravelDurationMs()) {
                        spawnSpyEntityForMission(mission);
                        mission.setStatus("ACTIVE");
                        String colonyName = getColonyName(mission.getTargetColonyId());
                        notifyPlayer(mission.getAttackerPlayerId(),
                                Component.literal("Your spy has arrived at " + colonyName + "!")
                                        .withStyle(ChatFormatting.GREEN));
                        pushSpyDataToPlayer(mission.getAttackerPlayerId());
                        // Counter-intel: defender's investment may auto-detect on arrival
                        if (TaxConfig.isUpgradesEnabled()) {
                            double detectChance = net.machiavelli.minecolonytax.upgrade.ColonyUpgradeManager
                                    .getCounterIntelDetectChance(mission.getTargetColonyId());
                            if (detectChance > 0 && Math.random() < detectChance) {
                                if (TaxConfig.isNormalLogging())
                                    LOGGER.info("Counter-intel detected spy {} at colony {} on arrival", missionId, mission.getTargetColonyId());
                                onSpyDetected(missionId);
                                anyProcessed = true;
                                continue;
                            }
                        }
                        anyProcessed = true;
                    }
                    continue;
                }

                if ("RETURNING".equals(mission.getStatus())) {
                    if (mission.getRecallStartTime() > 0
                            && now - mission.getRecallStartTime() >= mission.getTravelDurationMs()) {
                        mission.setStatus("RECALLED");
                        String colonyName = getColonyName(mission.getTargetColonyId());
                        notifyPlayer(mission.getAttackerPlayerId(),
                                Component.literal("Your spy has returned from " + colonyName + "!")
                                        .withStyle(ChatFormatting.GREEN));
                        ACTIVE_MISSIONS.remove(missionId);
                        COMPLETED_MISSIONS.put(missionId, mission);
                        giveIntelBook(mission);
                        giveSpyMap(mission);
                        pushSpyDataToPlayer(mission.getAttackerPlayerId());
                        anyProcessed = true;
                    }
                    continue;
                }

                // FLEEING missions are handled by the entity; only tick ACTIVE here
                if (!"ACTIVE".equals(mission.getStatus())) continue;

                // Drive intel from the server side so it advances even when the target chunk is unloaded
                if ("SCOUT".equals(mission.getMissionType())) {
                    progressIntel(mission.getMissionId());

                    // Auto-complete when all intel tiers gathered (no need to wait for maxDuration)
                    if (mission.getMissionIntel() != null && mission.getMissionIntel().getIntelTier() >= 3) {
                        if (TaxConfig.isNormalLogging()) LOGGER.info("Mission {} has gathered all intel (Tier 3) — auto-completing",
                                mission.getMissionId());
                        onMissionSuccess(mission);
                        anyProcessed = true;
                        continue;
                    }
                }

                if ("SABOTAGE".equals(mission.getMissionType())) {
                    long fieldStartTime = mission.getStartTime() + mission.getTravelDurationMs();
                    long fieldTimeElapsedMs = now - fieldStartTime;
                    long minFieldTimeMs = TaxConfig.getSpySabotageMinFieldMinutes() * 60_000L;
                    if (fieldTimeElapsedMs >= minFieldTimeMs) {
                        if (TaxConfig.isNormalLogging()) LOGGER.info("Mission {} SABOTAGE planted after {} min — auto-completing",
                                mission.getMissionId(), fieldTimeElapsedMs / 60_000L);
                        onMissionSuccess(mission);
                        anyProcessed = true;
                        continue;
                    }
                }

                if (SERVER != null) {
                    IColony targetColony = IMinecoloniesAPI.getInstance().getColonyManager()
                            .getColonyByWorld(mission.getTargetColonyId(), SERVER.overworld());
                    if (targetColony == null) {
                        if (TaxConfig.isNormalLogging()) LOGGER.info("Mission {} target colony {} no longer exists — auto-cancelling",
                                missionId, mission.getTargetColonyId());
                        // Refund queued cost: the target is gone, mission auto-cancels with no benefit.
                        // See audit/defensive_03_espionage.md C2.
                        refundPendingCost(mission);
                        despawnSpyEntity(mission);
                        ACTIVE_MISSIONS.remove(missionId);
                        notifyPlayer(mission.getAttackerPlayerId(),
                                Component.literal("Your spy mission was cancelled — target colony no longer exists.")
                                        .withStyle(ChatFormatting.YELLOW));
                        anyProcessed = true;
                        continue;
                    }
                }

                // Field time counts from arrival, not from deploy
                long fieldStartTime = mission.getStartTime() + mission.getTravelDurationMs();
                if (now - fieldStartTime >= mission.getMaxDurationMs()) {
                    onMissionSuccess(mission);
                    anyProcessed = true;
                }
            }
        }

        COMPLETED_MISSIONS.entrySet().removeIf(e -> {
            long missionEnd = e.getValue().getStartTime() + e.getValue().getMaxDurationMs();
            return (now - missionEnd) > COMPLETED_MISSION_TTL_MS;
        });

        clearExpiredStolenSecrets();

        if (anyProcessed) {
            saveData();
        }
    }

    // ======================== MISSION SUCCESS ========================

    private static void onMissionSuccess(SpyMission mission) {
        mission.setStatus("COMPLETED");
        despawnSpyEntity(mission);

        // Set cooldown
        String cooldownKey = mission.getAttackerColonyId() + ":" + mission.getTargetColonyId();
        COOLDOWNS.put(cooldownKey, System.currentTimeMillis() + (TaxConfig.getSpyCooldownMinutes() * 60000L));

        String colonyName = getColonyName(mission.getTargetColonyId());

        switch (mission.getMissionType()) {
            case "SCOUT" -> {
                int tier = mission.getMissionIntel() != null ? mission.getMissionIntel().getIntelTier() : 0;
                notifyPlayer(mission.getAttackerPlayerId(),
                        Component.literal("Your Scout has returned from " + colonyName + " with Tier " + tier + " intel.")
                                .withStyle(ChatFormatting.GREEN));
            }
            case "SABOTAGE" -> {
                double reduction = TaxConfig.getSpySabotageTaxReductionPercent();
                applySabotageEffect(mission.getTargetColonyId(), reduction);
                notifyPlayer(mission.getAttackerPlayerId(),
                        Component.literal("Sabotage successful! " + colonyName
                                + "'s next tax cycle will be reduced by " + (int) (reduction * 100) + "%.")
                                .withStyle(ChatFormatting.GREEN));
                notifyColonyOfficers(mission.getTargetColonyId(),
                        Component.literal("Your colony's economy has been sabotaged! Next tax cycle will be reduced.")
                                .withStyle(ChatFormatting.RED));
            }
            case "BRIBE" -> {
                int guardsToDisable = TaxConfig.getSpyBribeGuardsDisabledCount();
                int current = BRIBED_GUARDS.getOrDefault(mission.getTargetColonyId(), 0);
                BRIBED_GUARDS.put(mission.getTargetColonyId(), current + guardsToDisable);
                notifyPlayer(mission.getAttackerPlayerId(),
                        Component.literal("Bribe successful! " + guardsToDisable + " guards in " + colonyName
                                + " will be weakened during the next raid against them.")
                                .withStyle(ChatFormatting.GREEN));
            }
            case "STEAL" -> {
                long durationMs = TaxConfig.getSpyStealSecretsDurationHours() * 3600000L;
                long expiration = System.currentTimeMillis() + durationMs;
                STOLEN_SECRETS_BUFF.put(mission.getAttackerColonyId(), expiration);
                notifyPlayer(mission.getAttackerPlayerId(),
                        Component.literal("Secrets stolen from " + colonyName
                                + "! Your colony gains +20% tax bonus for "
                                + TaxConfig.getSpyStealSecretsDurationHours() + " hours.")
                                .withStyle(ChatFormatting.GREEN));
                notifyColonyOfficers(mission.getTargetColonyId(),
                        Component.literal("An enemy spy has stolen trade secrets from your colony!")
                                .withStyle(ChatFormatting.RED));
            }
            default -> LOGGER.warn("Unknown mission type on success: {}", mission.getMissionType());
        }

        ACTIVE_MISSIONS.remove(mission.getMissionId());
        COMPLETED_MISSIONS.put(mission.getMissionId(), mission);
        giveIntelBook(mission);
        giveSpyMap(mission);
        pushSpyDataToPlayer(mission.getAttackerPlayerId());
        if (TaxConfig.isNormalLogging()) LOGGER.info("Mission {} ({}) completed successfully against colony {}",
                mission.getMissionId(), mission.getMissionType(), mission.getTargetColonyId());
    }

    // ======================== BRIBED GUARDS ACCESSORS ========================

    public static int consumeBribedGuards(int targetColonyId) {
        Integer count = BRIBED_GUARDS.remove(targetColonyId);
        if (count != null && count > 0) {
            saveData();
            return count;
        }
        return 0;
    }

    public static boolean hasBribedGuards(int targetColonyId) {
        return BRIBED_GUARDS.getOrDefault(targetColonyId, 0) > 0;
    }

    // ======================== STOLEN SECRETS ACCESSORS ========================

    public static boolean hasActiveStolenSecretsBuff(int attackerColonyId) {
        Long expiration = STOLEN_SECRETS_BUFF.get(attackerColonyId);
        if (expiration == null) return false;
        if (System.currentTimeMillis() > expiration) {
            STOLEN_SECRETS_BUFF.remove(attackerColonyId);
            return false;
        }
        return true;
    }

    private static void clearExpiredStolenSecrets() {
        long now = System.currentTimeMillis();
        STOLEN_SECRETS_BUFF.entrySet().removeIf(e -> now > e.getValue());
    }

    // ======================== SABOTAGE ACCESSORS ========================

    public static double getSabotageReduction(int colonyId) {
        return SABOTAGE_EFFECTS.getOrDefault(colonyId, 0.0);
    }

    public static void clearSabotageEffect(int colonyId) {
        SABOTAGE_EFFECTS.remove(colonyId);
    }

    public static void applySabotageEffect(int colonyId, double reductionPercent) {
        double existing = SABOTAGE_EFFECTS.getOrDefault(colonyId, 0.0);
        SABOTAGE_EFFECTS.put(colonyId, Math.min(1.0, existing + reductionPercent));
        saveData();
    }

    // ======================== PENDING COSTS ========================

    public static int consumePendingCost(int colonyId) {
        int cost = PENDING_COSTS.getOrDefault(colonyId, 0);
        PENDING_COSTS.remove(colonyId);
        return cost;
    }

    /**
     * Refunds an already-queued pending spy cost from the attacker colony.
     * Called from recall / auto-cancel / killed paths so the colony isn't billed for a mission that
     * never delivered intel or effects. See audit/defensive_03_espionage.md C2.
     */
    private static void refundPendingCost(SpyMission mission) {
        if (mission == null || mission.getCost() <= 0) return;
        int attackerColonyId = mission.getAttackerColonyId();
        Integer newVal = PENDING_COSTS.merge(attackerColonyId, -mission.getCost(), Integer::sum);
        if (newVal != null && newVal <= 0) {
            PENDING_COSTS.remove(attackerColonyId);
        }
    }

    // ======================== MISSION QUERY ========================

    public static boolean isMissionActive(String missionId) {
        return ACTIVE_MISSIONS.containsKey(missionId);
    }

    public static int getAttackerColonyIdForMission(String missionId) {
        SpyMission mission = ACTIVE_MISSIONS.get(missionId);
        return mission != null ? mission.getAttackerColonyId() : -1;
    }

    public static boolean isCooldownActive(int attackerColonyId, int targetColonyId) {
        String key = attackerColonyId + ":" + targetColonyId;
        Long cooldownEnd = COOLDOWNS.get(key);
        if (cooldownEnd == null) return false;
        if (System.currentTimeMillis() > cooldownEnd) {
            COOLDOWNS.remove(key);
            return false;
        }
        return true;
    }

    public static List<SpyMission> getActiveMissionsForPlayer(String playerId) {
        return ACTIVE_MISSIONS.values().stream()
                .filter(m -> m.getAttackerPlayerId().equals(playerId))
                .toList();
    }

    /**
     * Removes a completed/terminal mission from the player's history.
     * Only removes the mission if it belongs to the given player and is in COMPLETED_MISSIONS.
     */
    public static void dismissMission(String missionId, String playerId) {
        SpyMission mission = COMPLETED_MISSIONS.get(missionId);
        if (mission != null && mission.getAttackerPlayerId().equals(playerId)) {
            COMPLETED_MISSIONS.remove(missionId);
            saveData();
            if (TaxConfig.isNormalLogging())
                LOGGER.info("Player {} dismissed mission {}", playerId, missionId);
        }
    }

    public static List<SpyMission> getMissionsForPlayer(String playerId) {
        List<SpyMission> result = new ArrayList<>();
        ACTIVE_MISSIONS.values().stream()
                .filter(m -> m.getAttackerPlayerId().equals(playerId))
                .forEach(result::add);
        COMPLETED_MISSIONS.values().stream()
                .filter(m -> m.getAttackerPlayerId().equals(playerId))
                .forEach(result::add);
        return result;
    }

    // ======================== HELPERS ========================

    private static void spawnSpyEntityForMission(SpyMission mission) {
        if (SERVER == null) return;
        ServerLevel level = SERVER.overworld();
        IColony targetColony = IMinecoloniesAPI.getInstance().getColonyManager()
                .getColonyByWorld(mission.getTargetColonyId(), level);
        if (targetColony == null) {
            LOGGER.warn("Target colony {} not found when spawning spy for mission {}",
                    mission.getTargetColonyId(), mission.getMissionId());
            return;
        }
        net.minecraft.core.BlockPos center = targetColony.getCenter();

        // Force-load the chunk so the entity actually spawns even if no player is nearby.
        // Wrap in try/finally so a spawn-time exception can't leak force-loaded chunks; the chunk is
        // released immediately after spawn since the entity is then persisted in the world.
        // See audit/CODEX_INDEPENDENT.md MED-6.
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        boolean wePushedForce = false;
        try {
            level.setChunkForced(chunkX, chunkZ, true);
            wePushedForce = true;
            FORCED_CHUNKS.put(mission.getMissionId(), chunkX + "," + chunkZ);

            net.minecraft.core.BlockPos spawnPos = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, center);
            // If heightmap returns garbage for an unloaded / below-world position, fall back to above center
            if (spawnPos.getY() <= level.getMinBuildHeight() + 5) {
                spawnPos = center.above();
            }
            SpyEntity spy = ModEntities.SPY.get().create(level);
            if (spy != null) {
                spy.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                UUID attackerUuid = UUID.fromString(mission.getAttackerPlayerId());
                spy.setMissionData(mission.getMissionId(), attackerUuid, mission.getTargetColonyId(),
                        mission.getMissionType());
                level.addFreshEntity(spy);
                mission.setSpyEntityUUID(spy.getUUID().toString());
                if (TaxConfig.isNormalLogging()) LOGGER.info("Spawned spy entity {} at {},{},{} for mission {}",
                        spy.getUUID(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), mission.getMissionId());
            } else {
                LOGGER.error("Failed to create spy entity (EntityType.create returned null) for mission {}", mission.getMissionId());
            }
        } finally {
            // Only release chunks WE forced. Tracked via FORCED_CHUNKS so an unrelated force-load by
            // another system survives our cleanup.
            if (wePushedForce) {
                try {
                    level.setChunkForced(chunkX, chunkZ, false);
                } catch (Exception e) {
                    LOGGER.warn("Failed to release forced chunk {},{} for mission {}: {}",
                            chunkX, chunkZ, mission.getMissionId(), e.getMessage());
                }
                FORCED_CHUNKS.remove(mission.getMissionId());
            }
        }
    }

    private static void despawnSpyEntity(SpyMission mission) {
        if (SERVER == null || mission.getSpyEntityUUID() == null) return;
        try {
            UUID spyUuid = UUID.fromString(mission.getSpyEntityUUID());
            for (ServerLevel level : SERVER.getAllLevels()) {
                net.minecraft.world.entity.Entity entity = level.getEntity(spyUuid);
                if (entity instanceof SpyEntity) {
                    entity.discard();
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to despawn spy entity {}: {}", mission.getSpyEntityUUID(), e.getMessage());
        }
    }

    private static void notifyPlayer(String playerUuid, Component message) {
        if (SERVER == null) return;
        try {
            ServerPlayer player = SERVER.getPlayerList().getPlayer(UUID.fromString(playerUuid));
            if (player != null) {
                player.sendSystemMessage(message);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not notify player {}: {}", playerUuid, e.getMessage());
        }
    }

    private static void notifyColonyOfficers(int colonyId, Component message) {
        if (SERVER == null) return;
        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager()
                .getColonyByWorld(colonyId, SERVER.overworld());
        if (colony == null) return;

        Set<UUID> recipients = new HashSet<>();
        IPermissions perms = colony.getPermissions();
        recipients.add(perms.getOwner());
        for (ColonyPlayer cp : perms.getPlayersByRank(perms.getRankOfficer())) {
            recipients.add(cp.getID());
        }

        for (UUID id : recipients) {
            ServerPlayer player = SERVER.getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static String getColonyName(int colonyId) {
        if (SERVER == null) return "Colony " + colonyId;
        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager()
                .getColonyByWorld(colonyId, SERVER.overworld());
        return colony != null ? colony.getName() : "Colony " + colonyId;
    }
}
