package net.machiavelli.minecolonytax.events.random;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.events.random.deep.CitizenManipulator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages random events for colonies.
 *
 * Random events are probability-based occurrences that affect:
 * - Tax revenue (multiplicative modifier)
 * - Citizen happiness (additive modifier)
 *
 * Events have:
 * - Duration (tax cycles)
 * - Cooldowns (prevent rapid re-triggers)
 * - Conditions (prerequisites to trigger)
 * - Balance protections (new colony grace, simultaneous limits)
 *
 * Pattern: Follows RaidPenaltyManager.java structure
 */
public class RandomEventManager {

    private static final Logger LOGGER = LogManager.getLogger(RandomEventManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "config/warntax/random_events.json";

    // ==================== Data Storage ====================

    /** Map: colonyId → List of active events */
    private static final Map<Integer, List<ActiveEvent>> ACTIVE_EVENTS = new ConcurrentHashMap<>();

    /** Map: colonyId → Map of event type → cooldown expiry timestamp */
    private static final Map<Integer, Map<RandomEventType, Long>> EVENT_COOLDOWNS = new ConcurrentHashMap<>();

    /** Map: colonyId → global cooldown expiry timestamp */
    private static final Map<Integer, Long> GLOBAL_COOLDOWNS = new ConcurrentHashMap<>();

    /** Map: colonyId → recent event log, newest first, capped at MAX_LOG_ENTRIES */
    private static final Map<Integer, LinkedList<EventLogEntry>> EVENT_LOG = new ConcurrentHashMap<>();
    private static final int MAX_LOG_ENTRIES = 10;

    /**
     * Map: colonyId → epoch-ms when we first processed this colony.
     * Used by EventTriggerSystem to determine colony age for new-colony protection.
     * MineColonies' getLastContactInHours() tracks last player *visit*, not colony age —
     * for active colonies this is always 0, which would permanently block events.
     */
    static final Map<Integer, Long> COLONY_FIRST_SEEN_MS = new ConcurrentHashMap<>();

    private static MinecraftServer SERVER;
    private static final Random RANDOM = new Random();

    // ==================== Initialization ====================

    /**
     * Initialize the RandomEventManager.
     * Called from MineColonyTax.onServerStarted()
     *
     * @param server The Minecraft server instance
     */
    public static void initialize(MinecraftServer server) {
        SERVER = server;
        loadData();
        if (TaxConfig.isNormalLogging()) LOGGER.info("RandomEventManager initialized with {} active events across {} colonies", countTotalEvents(), ACTIVE_EVENTS.size());
    }

    /**
     * Shutdown and save data.
     * Called from MineColonyTax.onServerStopping()
     */
    public static void shutdown() {
        saveData();
        if (TaxConfig.isNormalLogging()) LOGGER.info("RandomEventManager shutdown complete");
    }

    // ==================== Tax Cycle Integration ====================

    /**
     * Called during tax generation for each colony.
     * Handles event triggering, updating, and expiration.
     *
     * Called from: TaxManager.generateTaxesForAllColonies() after saveTaxData()
     *
     * @param colony The colony to process events for
     */
    public static void onTaxCycle(IColony colony) {
        if (!TaxConfig.isRandomEventsEnabled()) {
            return;
        }

        int colonyId = colony.getID();

        // Track when we first see this colony (used for new-colony protection age check)
        COLONY_FIRST_SEEN_MS.putIfAbsent(colonyId, System.currentTimeMillis());

        // 1. Update active events (decrement cycles, check expiration)
        updateActiveEvents(colonyId, colony);

        // 2. Try to trigger new events (probability-based)
        checkForNewEvents(colony);

        // NOTE: do NOT saveData() here. This runs once per colony per tax cycle, so a
        // per-colony save serialized the full mod-wide state N times each cycle = O(N^2)
        // synchronous disk writes at scale (optimization audit C1). TaxManager now calls
        // persist() ONCE after the whole tax loop completes.
    }

    /**
     * Persist random-event state. Call this ONCE after the tax cycle finishes
     * (TaxManager.generateTaxesForAllColonies) rather than per colony.
     */
    public static void persist() {
        saveData();
    }

    /**
     * Get the combined tax multiplier from all active events for a colony.
     *
     * Called from: TaxManager.generateTaxesForAllColonies() line 483
     *
     * @param colonyId The colony ID
     * @return Multiplier value (e.g., 0.85 for -15%, 1.2 for +20%)
     */
    public static double getTaxMultiplier(int colonyId) {
        if (!TaxConfig.isRandomEventsEnabled()) {
            return 1.0;
        }

        List<ActiveEvent> events = ACTIVE_EVENTS.getOrDefault(colonyId, new ArrayList<>());

        double multiplier = 1.0;
        for (ActiveEvent event : events) {
            multiplier *= event.getType().getTaxMultiplier();
        }

        // Clamp to reasonable range (prevent extreme values)
        return Math.max(0.1, Math.min(2.0, multiplier));
    }

    /**
     * Get the combined happiness modifier from all active events for a colony.
     *
     * Called from: TaxManager.calculateColonyAverageHappiness() line 384
     *
     * @param colonyId The colony ID
     * @return Additive modifier (e.g., -0.3, +0.5)
     */
    public static double getHappinessModifier(int colonyId) {
        if (!TaxConfig.isRandomEventsEnabled()) {
            return 0.0;
        }

        List<ActiveEvent> events = ACTIVE_EVENTS.getOrDefault(colonyId, new ArrayList<>());

        double modifier = 0.0;
        for (ActiveEvent event : events) {
            modifier += event.getType().getHappinessModifier();
        }

        // Clamp to reasonable range
        return Math.max(-2.0, Math.min(2.0, modifier));
    }

    // ==================== Event Triggering ====================

    /**
     * Check if new events should trigger for a colony.
     *
     * @param colony The colony to check
     */
    private static void checkForNewEvents(IColony colony) {
        int colonyId = colony.getID();

        // Check global cooldown
        Long globalCooldown = GLOBAL_COOLDOWNS.get(colonyId);
        if (globalCooldown != null && System.currentTimeMillis() < globalCooldown) {
            return; // Still in global cooldown
        }

        // Check simultaneous event limit
        List<ActiveEvent> activeEvents = ACTIVE_EVENTS.getOrDefault(colonyId, new ArrayList<>());
        int maxSimultaneous = TaxConfig.getMaxSimultaneousEvents();
        if (activeEvents.size() >= maxSimultaneous) {
            return; // Already at max events
        }

        // Check new colony protection
        if (EventTriggerSystem.isColonyProtected(colony)) {
            return; // Colony too new for events
        }

        // Try to trigger each event type
        for (RandomEventType eventType : RandomEventType.values()) {
            // Check if event is enabled in config
            if (!isEventEnabled(eventType)) {
                continue;
            }

            // Check event-specific cooldown
            if (isOnCooldown(colonyId, eventType)) {
                continue;
            }

            // Check if conditions are met
            if (!eventType.meetsConditions(colony)) {
                continue;
            }

            // Calculate modified probability (policy, war, raid, size modifiers all applied)
            double probability = EventTriggerSystem.calculateEventProbability(colony, eventType);

            // Roll for trigger
            if (RANDOM.nextDouble() < probability) {
                triggerEvent(colony, eventType);
                return; // Only trigger one event per cycle
            }
        }
    }

    /**
     * Trigger a specific event for a colony.
     *
     * @param colony The colony to trigger event for
     * @param eventType The type of event to trigger
     */
    private static void triggerEvent(IColony colony, RandomEventType eventType) {
        int colonyId = colony.getID();

        // Create event instance
        ActiveEvent event = new ActiveEvent(colonyId, eventType);

        // Apply deep integration effects
        applyDeepIntegrationEffects(colony, event);

        // Add to active events
        ACTIVE_EVENTS.computeIfAbsent(colonyId, k -> new ArrayList<>()).add(event);

        // Record in event log
        addToEventLog(colonyId, new EventLogEntry(
            event.getEventId().toString(),
            eventType.getDisplayName(),
            eventType.getDescription(),
            buildCompactStat(eventType),
            getColorCode(eventType),
            true,
            eventType.getDurationCycles()
        ));

        // Set cooldowns
        long cooldownDuration = eventType.getCooldownCycles() * getTaxCycleDurationMs();
        EVENT_COOLDOWNS.computeIfAbsent(colonyId, k -> new ConcurrentHashMap<>())
            .put(eventType, System.currentTimeMillis() + cooldownDuration);

        long globalCooldownDuration = TaxConfig.getGlobalCooldownCycles() * getTaxCycleDurationMs();
        GLOBAL_COOLDOWNS.put(colonyId, System.currentTimeMillis() + globalCooldownDuration);

        // Notify players
        notifyColonyPlayers(colony, event, "started");

        LOGGER.info("Event {} triggered for colony {} ({})",
            eventType, colonyId, colony.getName());
        int _evtBal = net.machiavelli.minecolonytax.TaxManager.getStoredTaxForColonyId(colonyId);
        net.machiavelli.minecolonytax.data.HistoryManager.logWithBalance(colonyId, "EVENT",
                eventType.getDisplayName() + ": " + buildCompactStat(eventType),
                _evtBal, _evtBal);
    }

    /**
     * Apply deep integration effects when an event triggers.
     * Calls CitizenManipulator for events that directly affect citizens.
     *
     * @param colony The colony
     * @param event The event being triggered
     */
    private static void applyDeepIntegrationEffects(IColony colony, ActiveEvent event) {
        RandomEventType type = event.getType();
        List<Integer> affectedCitizens = new ArrayList<>();

        try {
            switch (type) {
                case LABOR_STRIKE:
                    // Set 30-50% of workers to STUCK status
                    double strikePercentage = 0.3 + (RANDOM.nextDouble() * 0.2); // 30-50%
                    affectedCitizens = CitizenManipulator.forceLaborStrike(colony, strikePercentage);
                    LOGGER.info("Labor strike: {} citizens affected in colony {}",
                        affectedCitizens.size(), colony.getName());
                    break;

                case PLAGUE_OUTBREAK:
                    // Infect 20-40% of citizens with random disease
                    double plaguePercentage = 0.2 + (RANDOM.nextDouble() * 0.2); // 20-40%
                    affectedCitizens = CitizenManipulator.infectWithPlague(colony, plaguePercentage);
                    LOGGER.info("Plague outbreak: {} citizens infected in colony {}",
                        affectedCitizens.size(), colony.getName());
                    break;

                case ROYAL_FEAST:
                    // Set all citizens' saturation to max (20.0)
                    int feastCount = CitizenManipulator.setSaturationForAll(colony, 20.0);
                    LOGGER.info("Royal feast: {} citizens fed in colony {}",
                        feastCount, colony.getName());
                    break;

                case CROP_BLIGHT:
                    // Set all citizens' saturation to near-starvation (3.0)
                    int blightCount = CitizenManipulator.setSaturationForAll(colony, 3.0);
                    LOGGER.info("Crop blight: {} citizens affected in colony {}",
                        blightCount, colony.getName());
                    break;

                case BOUNTIFUL_HARVEST:
                    // Colony is well-fed — full saturation for all citizens
                    int harvestCount = CitizenManipulator.setSaturationForAll(colony, 20.0);
                    LOGGER.info("Bountiful harvest: {} citizens fed in colony {}",
                        harvestCount, colony.getName());
                    break;

                case FOOD_SHORTAGE:
                    // Citizens are hungry — low but non-starving saturation
                    int shortageCount = CitizenManipulator.setSaturationForAll(colony, 6.0);
                    LOGGER.info("Food shortage: {} citizens affected in colony {}",
                        shortageCount, colony.getName());
                    break;

                case DISEASE_OUTBREAK:
                    // Infect 10-15% of citizens (lighter than PLAGUE_OUTBREAK's 20-40%)
                    double outbreakPercentage = 0.10 + (RANDOM.nextDouble() * 0.05);
                    affectedCitizens = CitizenManipulator.infectWithPlague(colony, outbreakPercentage);
                    LOGGER.info("Disease outbreak: {} citizens infected in colony {}",
                        affectedCitizens.size(), colony.getName());
                    break;

                case BANDIT_HARASSMENT:
                    // Spawn 2-4 Pillagers near the colony center
                    int banditCount = 2 + RANDOM.nextInt(3); // 2, 3, or 4
                    spawnBanditsNearColony(colony, banditCount);
                    break;

                case GUARD_DESERTION:
                    // Permanently remove the strongest guard (by total XP) from the colony
                    String desertedGuard = CitizenManipulator.desertStrongestGuard(colony);
                    if (desertedGuard != null) {
                        LOGGER.info("Guard desertion: {} permanently left colony {}", desertedGuard, colony.getName());
                        // Store name in affectedCitizens as a sentinel — id -1 means "used desertedName"
                        // We don't restore on expiry; desertion is permanent.
                    }
                    break;

                default:
                    // No deep integration for this event type
                    break;
            }

            // Store affected citizens in the event
            if (!affectedCitizens.isEmpty()) {
                event.setAffectedCitizens(affectedCitizens);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to apply deep integration effects for event {} in colony {}: {}",
                type, colony.getName(), e.getMessage());
        }
    }

    /**
     * Spawn pillager bandits near a colony's center block.
     * Bandits are scattered randomly within a 20-block radius at surface level.
     * They are natural hostile mobs — no cleanup needed, they despawn or are killed.
     *
     * @param colony Target colony
     * @param count  Number of pillagers to spawn (2-4 recommended)
     */
    private static void spawnBanditsNearColony(IColony colony, int count) {
        try {
            ServerLevel level = (ServerLevel) colony.getWorld();
            if (level == null) {
                LOGGER.warn("Bandit harassment: colony {} has null world, skipping spawn", colony.getName());
                return;
            }

            BlockPos center = colony.getCenter();
            int spawned = 0;

            for (int i = 0; i < count; i++) {
                // Scatter within ±20 blocks of colony center
                int offsetX = (RANDOM.nextInt(41)) - 20;
                int offsetZ = (RANDOM.nextInt(41)) - 20;
                BlockPos target = new BlockPos(center.getX() + offsetX, center.getY(), center.getZ() + offsetZ);

                // Find the surface height at this position
                BlockPos surfacePos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target);

                Pillager pillager = EntityType.PILLAGER.create(level);
                if (pillager != null) {
                    pillager.moveTo(surfacePos.getX() + 0.5, surfacePos.getY(), surfacePos.getZ() + 0.5, RANDOM.nextFloat() * 360f, 0f);
                    pillager.finalizeSpawn(level, level.getCurrentDifficultyAt(surfacePos), MobSpawnType.EVENT, null, null);
                    level.addFreshEntityWithPassengers(pillager);
                    spawned++;
                }
            }

            LOGGER.info("Bandit harassment: spawned {} pillagers near colony {} ({})", spawned, colony.getName(), colony.getID());
        } catch (Exception e) {
            LOGGER.warn("Bandit harassment: failed to spawn bandits near colony {}: {}", colony.getName(), e.getMessage());
        }
    }

    // ==================== Event Management ====================

    /**
     * Update active events (decrement cycles, check expiration).
     *
     * @param colonyId The colony ID
     * @param colony The colony instance
     */
    private static void updateActiveEvents(int colonyId, IColony colony) {
        List<ActiveEvent> events = ACTIVE_EVENTS.get(colonyId);
        if (events == null || events.isEmpty()) {
            return;
        }

        List<ActiveEvent> expiredEvents = new ArrayList<>();

        for (ActiveEvent event : events) {
            // Decrement remaining cycles
            event.decrementCycle();

            // Keep log entry in sync
            syncLogEntry(colonyId, event.getEventId().toString(), event.getRemainingCycles());

            // Check if expired
            if (event.hasExpired()) {
                expiredEvents.add(event);
            }
        }

        // Remove expired events and restore citizens
        for (ActiveEvent expired : expiredEvents) {
            // Restore deep integration effects before removing event
            restoreDeepIntegrationEffects(colony, expired);

            events.remove(expired);
            markLogEntryExpired(colonyId, expired.getEventId().toString());
            notifyColonyPlayers(colony, expired, "expired");

            LOGGER.info("Event {} expired for colony {} ({})",
                expired.getType(), colonyId, colony.getName());
        }

        // Clean up empty list
        if (events.isEmpty()) {
            ACTIVE_EVENTS.remove(colonyId);
        }
    }

    /**
     * Restore deep integration effects when an event expires.
     * Restores citizens to normal state.
     *
     * @param colony The colony
     * @param event The expired event
     */
    private static void restoreDeepIntegrationEffects(IColony colony, ActiveEvent event) {
        RandomEventType type = event.getType();
        List<Integer> affectedCitizens = event.getAffectedCitizens();

        if (affectedCitizens.isEmpty()) {
            return; // No citizens to restore
        }

        try {
            switch (type) {
                case LABOR_STRIKE:
                    // Restore citizens from STUCK to WORKING status
                    int restoredCount = CitizenManipulator.restoreCitizens(colony, affectedCitizens);
                    LOGGER.info("Labor strike ended: {} citizens restored to work in colony {}",
                        restoredCount, colony.getName());
                    break;

                case PLAGUE_OUTBREAK:
                case DISEASE_OUTBREAK:
                    // Cure all sick citizens when either disease event expires
                    int curedCount = CitizenManipulator.cureAllCitizens(colony);
                    LOGGER.info("{} ended: {} citizens cured in colony {}",
                        type.getDisplayName(), curedCount, colony.getName());
                    break;

                default:
                    // Saturation-based events (ROYAL_FEAST, CROP_BLIGHT, BOUNTIFUL_HARVEST, FOOD_SHORTAGE)
                    // are one-time changes — saturation normalizes naturally as citizens eat.
                    // BANDIT_HARASSMENT, GUARD_DESERTION, CORRUPT_OFFICIAL etc. have no restoration.
                    break;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to restore deep integration effects for event {} in colony {}: {}",
                type, colony.getName(), e.getMessage());
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Check if an event type is on cooldown for a colony.
     *
     * @param colonyId The colony ID
     * @param eventType The event type
     * @return true if on cooldown
     */
    private static boolean isOnCooldown(int colonyId, RandomEventType eventType) {
        Map<RandomEventType, Long> cooldowns = EVENT_COOLDOWNS.get(colonyId);
        if (cooldowns == null) {
            return false;
        }

        Long cooldownExpiry = cooldowns.get(eventType);
        if (cooldownExpiry == null) {
            return false;
        }

        boolean onCooldown = System.currentTimeMillis() < cooldownExpiry;

        // Clean up expired cooldowns
        if (!onCooldown) {
            cooldowns.remove(eventType);
            if (cooldowns.isEmpty()) {
                EVENT_COOLDOWNS.remove(colonyId);
            }
        }

        return onCooldown;
    }

    /**
     * Get the tax cycle duration in milliseconds.
     *
     * @return Duration in milliseconds
     */
    private static long getTaxCycleDurationMs() {
        int minutesBetweenTaxes = TaxConfig.getTaxIntervalInMinutes();
        return minutesBetweenTaxes * 60L * 1000L;
    }

    /**
     * Count total active events across all colonies.
     *
     * @return Total event count
     */
    private static int countTotalEvents() {
        return ACTIVE_EVENTS.values().stream()
            .mapToInt(List::size)
            .sum();
    }

    // ==================== Event Log Helpers ====================

    private static void addToEventLog(int colonyId, EventLogEntry entry) {
        LinkedList<EventLogEntry> log = EVENT_LOG.computeIfAbsent(colonyId, k -> new LinkedList<>());
        log.addFirst(entry);
        while (log.size() > MAX_LOG_ENTRIES) {
            log.removeLast();
        }
    }

    private static void syncLogEntry(int colonyId, String eventId, int remainingCycles) {
        LinkedList<EventLogEntry> log = EVENT_LOG.get(colonyId);
        if (log == null) return;
        for (EventLogEntry e : log) {
            if (e.getEventId().equals(eventId)) {
                e.setRemainingCycles(remainingCycles);
                return;
            }
        }
    }

    private static void markLogEntryExpired(int colonyId, String eventId) {
        LinkedList<EventLogEntry> log = EVENT_LOG.get(colonyId);
        if (log == null) return;
        for (EventLogEntry e : log) {
            if (e.getEventId().equals(eventId)) {
                e.setActive(false);
                e.setRemainingCycles(0);
                return;
            }
        }
    }

    private static int getColorCode(RandomEventType type) {
        Integer chatColor = type.getColor().getColor();
        return chatColor != null ? (0xFF000000 | chatColor) : 0xFF2C1E0E;
    }

    /** Builds a short inline stat string like "+15% tax  -0.3 hap" for the GUI event row. */
    private static String buildCompactStat(RandomEventType type) {
        StringBuilder sb = new StringBuilder();
        double tax = type.getTaxMultiplier();
        double hap = type.getHappinessModifier();
        if (tax != 1.0) {
            int pct = (int) Math.round((tax - 1.0) * 100);
            sb.append(pct > 0 ? "+" : "").append(pct).append("% tax");
        }
        if (hap != 0.0) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(hap > 0 ? "+" : "").append(String.format("%.1f", hap)).append(" hap");
        }
        return sb.toString();
    }

    /**
     * Check if a specific event type is enabled in the configuration.
     *
     * @param eventType The event type to check
     * @return true if the event is enabled
     */
    private static boolean isEventEnabled(RandomEventType eventType) {
        switch (eventType) {
            case MERCHANT_CARAVAN:
                return TaxConfig.isMerchantCaravanEnabled();
            case BOUNTIFUL_HARVEST:
                return TaxConfig.isBountifulHarvestEnabled();
            case CULTURAL_FESTIVAL:
                return TaxConfig.isCulturalFestivalEnabled();
            case SUCCESSFUL_RECRUITMENT:
                return TaxConfig.isSuccessfulRecruitmentEnabled();
            case FOOD_SHORTAGE:
                return TaxConfig.isFoodShortageEnabled();
            case DISEASE_OUTBREAK:
                return TaxConfig.isDiseaseOutbreakEnabled();
            case BANDIT_HARASSMENT:
                return TaxConfig.isBanditHarassmentEnabled();
            case CORRUPT_OFFICIAL:
                return TaxConfig.isCorruptOfficialEnabled();
            case WANDERING_TRADER_OFFER:
                return TaxConfig.isWanderingTraderOfferEnabled();
            case NEIGHBORING_ALLIANCE:
                return TaxConfig.isNeighboringAllianceEnabled();
            case WAR_PROFITEERING:
                return TaxConfig.isWarProfiteeringEnabled();
            case GUARD_DESERTION:
                return TaxConfig.isGuardDesertionEnabled();
            case LABOR_STRIKE:
                return TaxConfig.isLaborStrikeEnabled();
            case PLAGUE_OUTBREAK:
                return TaxConfig.isPlagueOutbreakEnabled();
            case ROYAL_FEAST:
                return TaxConfig.isRoyalFeastEnabled();
            case CROP_BLIGHT:
                return TaxConfig.isCropBlightEnabled();
            default:
                return true; // Default to enabled for unknown events
        }
    }

    /**
     * Get a colony by ID.
     *
     * @param colonyId The colony ID
     * @return The colony, or null if not found
     */
    private static IColony getColony(int colonyId) {
        if (SERVER == null) return null;
        IColonyManager colonyManager = IMinecoloniesAPI.getInstance().getColonyManager();
        return colonyManager.getColonyByWorld(colonyId, SERVER.overworld());
    }

    /**
     * Notify colony players about an event.
     *
     * @param colony The colony
     * @param event The event
     * @param action "started" or "expired"
     */
    private static void notifyColonyPlayers(IColony colony, ActiveEvent event, String action) {
        try {
            String message;
            if ("started".equals(action)) {
                message = String.format("§%c[Random Event] %s: %s",
                    event.getType().getColor().getChar(),
                    event.getType().getDisplayName(),
                    event.getType().getDescription());
            } else {
                message = String.format("§7[Random Event] %s has ended",
                    event.getType().getDisplayName());
            }

            Component component = Component.literal(message);

            // Send to all colony officers and owner
            for (ServerPlayer player : SERVER.getPlayerList().getPlayers()) {
                if (colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS)) {
                    player.sendSystemMessage(component);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to notify players about event: {}", e.getMessage());
        }
    }

    // ==================== Persistence ====================

    /**
     * Load data from JSON file.
     */
    private static void loadData() {
        Path path = Paths.get(STORAGE_FILE);
        if (!Files.exists(path)) {
            LOGGER.debug("No existing random events data found, starting fresh");
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            // Load active events
            if (root.has("activeEvents")) {
                JsonObject activeEventsJson = root.getAsJsonObject("activeEvents");
                for (String colonyIdStr : activeEventsJson.keySet()) {
                    int colonyId = Integer.parseInt(colonyIdStr);
                    JsonArray eventsArray = activeEventsJson.getAsJsonArray(colonyIdStr);

                    List<ActiveEvent> events = new ArrayList<>();
                    for (JsonElement element : eventsArray) {
                        JsonObject eventJson = element.getAsJsonObject();

                        UUID eventId = UUID.fromString(eventJson.get("eventId").getAsString());
                        RandomEventType type = RandomEventType.valueOf(eventJson.get("type").getAsString());
                        long startTime = eventJson.get("startTime").getAsLong();
                        int remainingCycles = eventJson.get("remainingCycles").getAsInt();

                        // Load affected citizens if present (for deep integration events)
                        List<Integer> affectedCitizens = new ArrayList<>();
                        if (eventJson.has("affectedCitizens")) {
                            JsonArray citizensArray = eventJson.getAsJsonArray("affectedCitizens");
                            for (JsonElement citizenElement : citizensArray) {
                                affectedCitizens.add(citizenElement.getAsInt());
                            }
                        }

                        ActiveEvent event = new ActiveEvent(eventId, colonyId, type, startTime, remainingCycles, affectedCitizens);
                        events.add(event);
                    }

                    ACTIVE_EVENTS.put(colonyId, events);
                }
            }

            // Load cooldowns
            if (root.has("eventCooldowns")) {
                JsonObject cooldownsJson = root.getAsJsonObject("eventCooldowns");
                for (String colonyIdStr : cooldownsJson.keySet()) {
                    int colonyId = Integer.parseInt(colonyIdStr);
                    JsonObject colonyCooldownsJson = cooldownsJson.getAsJsonObject(colonyIdStr);

                    Map<RandomEventType, Long> cooldowns = new ConcurrentHashMap<>();
                    for (String eventTypeStr : colonyCooldownsJson.keySet()) {
                        try {
                            if (!"globalCooldown".equals(eventTypeStr)) {
                                RandomEventType type = RandomEventType.valueOf(eventTypeStr);
                                long expiry = colonyCooldownsJson.get(eventTypeStr).getAsLong();
                                cooldowns.put(type, expiry);
                            } else {
                                long globalExpiry = colonyCooldownsJson.get("globalCooldown").getAsLong();
                                GLOBAL_COOLDOWNS.put(colonyId, globalExpiry);
                            }
                        } catch (IllegalArgumentException e) {
                            LOGGER.warn("Unknown event type in cooldowns: {}", eventTypeStr);
                        }
                    }

                    if (!cooldowns.isEmpty()) {
                        EVENT_COOLDOWNS.put(colonyId, cooldowns);
                    }
                }
            }

            // Load event log
            if (root.has("eventLog")) {
                JsonObject logJson = root.getAsJsonObject("eventLog");
                for (String colonyIdStr : logJson.keySet()) {
                    int colonyId = Integer.parseInt(colonyIdStr);
                    JsonArray logArray = logJson.getAsJsonArray(colonyIdStr);
                    LinkedList<EventLogEntry> log = new LinkedList<>();
                    for (JsonElement element : logArray) {
                        JsonObject entryJson = element.getAsJsonObject();
                        String compactStat = entryJson.has("compactStat")
                                ? entryJson.get("compactStat").getAsString() : "";
                        log.add(new EventLogEntry(
                            entryJson.get("eventId").getAsString(),
                            entryJson.get("displayName").getAsString(),
                            entryJson.get("description").getAsString(),
                            compactStat,
                            entryJson.get("colorCode").getAsInt(),
                            entryJson.get("isActive").getAsBoolean(),
                            entryJson.get("remainingCycles").getAsInt()
                        ));
                    }
                    EVENT_LOG.put(colonyId, log);
                }
            }

            // Load colony first-seen timestamps
            if (root.has("colonyFirstSeen")) {
                JsonObject firstSeenJson = root.getAsJsonObject("colonyFirstSeen");
                for (String colonyIdStr : firstSeenJson.keySet()) {
                    int colonyId = Integer.parseInt(colonyIdStr);
                    COLONY_FIRST_SEEN_MS.put(colonyId, firstSeenJson.get(colonyIdStr).getAsLong());
                }
            }

            LOGGER.info("Loaded random events data from {}", STORAGE_FILE);
        } catch (Exception e) {
            LOGGER.error("Failed to load random events data: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save data to JSON file.
     */
    private static void saveData() {
        try {
            // Ensure directory exists
            Path path = Paths.get(STORAGE_FILE);
            Files.createDirectories(path.getParent());

            JsonObject root = new JsonObject();

            // Save active events
            JsonObject activeEventsJson = new JsonObject();
            for (Map.Entry<Integer, List<ActiveEvent>> entry : ACTIVE_EVENTS.entrySet()) {
                JsonArray eventsArray = new JsonArray();
                for (ActiveEvent event : entry.getValue()) {
                    JsonObject eventJson = new JsonObject();
                    eventJson.addProperty("eventId", event.getEventId().toString());
                    eventJson.addProperty("type", event.getType().name());
                    eventJson.addProperty("startTime", event.getStartTime());
                    eventJson.addProperty("remainingCycles", event.getRemainingCycles());

                    // Save affected citizens (for deep integration events)
                    if (!event.getAffectedCitizens().isEmpty()) {
                        JsonArray citizensArray = new JsonArray();
                        for (Integer citizenId : event.getAffectedCitizens()) {
                            citizensArray.add(citizenId);
                        }
                        eventJson.add("affectedCitizens", citizensArray);
                    }

                    eventsArray.add(eventJson);
                }
                activeEventsJson.add(entry.getKey().toString(), eventsArray);
            }
            root.add("activeEvents", activeEventsJson);

            // Save cooldowns
            JsonObject cooldownsJson = new JsonObject();
            for (Map.Entry<Integer, Map<RandomEventType, Long>> entry : EVENT_COOLDOWNS.entrySet()) {
                JsonObject colonyCooldownsJson = new JsonObject();
                for (Map.Entry<RandomEventType, Long> cooldownEntry : entry.getValue().entrySet()) {
                    colonyCooldownsJson.addProperty(cooldownEntry.getKey().name(), cooldownEntry.getValue());
                }

                // Add global cooldown if exists
                Long globalCooldown = GLOBAL_COOLDOWNS.get(entry.getKey());
                if (globalCooldown != null) {
                    colonyCooldownsJson.addProperty("globalCooldown", globalCooldown);
                }

                cooldownsJson.add(entry.getKey().toString(), colonyCooldownsJson);
            }
            root.add("eventCooldowns", cooldownsJson);

            // Save event log
            JsonObject logJson = new JsonObject();
            for (Map.Entry<Integer, LinkedList<EventLogEntry>> logEntry : EVENT_LOG.entrySet()) {
                JsonArray logArray = new JsonArray();
                for (EventLogEntry entry : logEntry.getValue()) {
                    JsonObject entryJson = new JsonObject();
                    entryJson.addProperty("eventId", entry.getEventId());
                    entryJson.addProperty("displayName", entry.getDisplayName());
                    entryJson.addProperty("description", entry.getDescription());
                    entryJson.addProperty("compactStat", entry.getCompactStat());
                    entryJson.addProperty("colorCode", entry.getColorCode());
                    entryJson.addProperty("isActive", entry.isActive());
                    entryJson.addProperty("remainingCycles", entry.getRemainingCycles());
                    logArray.add(entryJson);
                }
                logJson.add(logEntry.getKey().toString(), logArray);
            }
            root.add("eventLog", logJson);

            // Save colony first-seen timestamps (for new-colony protection)
            JsonObject firstSeenJson = new JsonObject();
            for (Map.Entry<Integer, Long> entry : COLONY_FIRST_SEEN_MS.entrySet()) {
                firstSeenJson.addProperty(entry.getKey().toString(), entry.getValue());
            }
            root.add("colonyFirstSeen", firstSeenJson);

            // Write to file
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }

            LOGGER.debug("Saved random events data to {}", STORAGE_FILE);
        } catch (Exception e) {
            LOGGER.error("Failed to save random events data: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== Public Queries ====================

    /**
     * Get all active events for a colony.
     * Called by commands.
     *
     * @param colonyId The colony ID
     * @return List of active events
     */
    public static List<ActiveEvent> getActiveEvents(int colonyId) {
        return new ArrayList<>(ACTIVE_EVENTS.getOrDefault(colonyId, new ArrayList<>()));
    }

    /**
     * Get recent event log for a colony, newest first (for GUI display).
     *
     * @param colonyId The colony ID
     * @return Snapshot list of log entries
     */
    public static List<EventLogEntry> getEventLog(int colonyId) {
        LinkedList<EventLogEntry> log = EVENT_LOG.get(colonyId);
        return log != null ? new ArrayList<>(log) : new ArrayList<>();
    }

    /**
     * Remove a specific entry from the event log (player-initiated dismiss in GUI).
     *
     * @param colonyId The colony ID
     * @param eventId  The event UUID string to remove
     */
    public static void dismissFromEventLog(int colonyId, String eventId) {
        LinkedList<EventLogEntry> log = EVENT_LOG.get(colonyId);
        if (log == null) return;
        log.removeIf(e -> e.getEventId().equals(eventId));
        if (log.isEmpty()) EVENT_LOG.remove(colonyId);
        saveData();
    }

    /**
     * Check if events system is enabled.
     *
     * @return true if enabled
     */
    public static boolean isEnabled() {
        return TaxConfig.isRandomEventsEnabled();
    }

    /**
     * Clear a specific event type from a colony (for testing/admin).
     *
     * @param colonyId The colony ID
     * @param eventType The event type to clear
     */
    public static void clearEventType(int colonyId, RandomEventType eventType) {
        List<ActiveEvent> events = ACTIVE_EVENTS.get(colonyId);
        if (events == null) {
            return;
        }

        events.removeIf(event -> event.getType() == eventType);

        if (events.isEmpty()) {
            ACTIVE_EVENTS.remove(colonyId);
        }

        saveData();
        LOGGER.info("Cleared event type {} for colony {}", eventType, colonyId);
    }

    /**
     * Force trigger an event (for testing/admin).
     *
     * @param colony The colony
     * @param eventType The event type to trigger
     */
    public static void forceTriggerEvent(IColony colony, RandomEventType eventType) {
        triggerEvent(colony, eventType);
        LOGGER.info("Force-triggered event {} for colony {}", eventType, colony.getID());
    }

    /**
     * Reset all event state for a colony so it behaves as if freshly set up.
     * Clears protection (sets first-seen to 25h ago), all cooldowns, and active events.
     * Admin/testing use only — wired to /wnt events reset.
     *
     * @param colonyId The colony ID
     */
    public static void resetColonyEventState(int colonyId) {
        // Set first-seen to 25 hours ago so new-colony protection is expired
        COLONY_FIRST_SEEN_MS.put(colonyId, System.currentTimeMillis() - 25 * 3_600_000L);

        // Clear per-event and global cooldowns
        EVENT_COOLDOWNS.remove(colonyId);
        GLOBAL_COOLDOWNS.remove(colonyId);

        // Clear any active events (without restoring effects — this is an admin reset)
        ACTIVE_EVENTS.remove(colonyId);

        saveData();
        LOGGER.info("Reset all event state for colony {}", colonyId);
    }
}
