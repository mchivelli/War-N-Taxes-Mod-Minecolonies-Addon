package net.machiavelli.minecolonytax.events.random;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.events.random.deep.CitizenManipulator;
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
        LOGGER.info("RandomEventManager initialized with {} active events across {} colonies",
            countTotalEvents(), ACTIVE_EVENTS.size());
    }

    /**
     * Shutdown and save data.
     * Called from MineColonyTax.onServerStopping()
     */
    public static void shutdown() {
        saveData();
        LOGGER.info("RandomEventManager shutdown complete");
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

        // 1. Update active events (decrement cycles, check expiration)
        updateActiveEvents(colonyId, colony);

        // 2. Try to trigger new events (probability-based)
        checkForNewEvents(colony);

        // 3. Save data after changes
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
        if (isNewColony(colony)) {
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

            // Calculate modified probability
            double probability = calculateProbability(colony, eventType);

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
     * Calculate the probability for an event to trigger.
     * Applies modifiers based on colony size and configuration.
     *
     * @param colony The colony
     * @param eventType The event type
     * @return Modified probability (0.0-1.0)
     */
    private static double calculateProbability(IColony colony, RandomEventType eventType) {
        double baseProbability = eventType.getBaseProbability();

        // Apply config multiplier
        double configMultiplier = TaxConfig.getBaseChanceMultiplier();
        double probability = baseProbability * configMultiplier;

        // Apply colony size modifiers
        int citizenCount = colony.getCitizenManager().getCitizens().size();
        if (citizenCount < 20) {
            probability *= 0.5; // 50% reduced for small colonies
        } else if (citizenCount >= 100) {
            probability *= 1.3; // 30% increased for large colonies
        } else if (citizenCount >= 50) {
            probability *= 1.2; // 20% increased for medium colonies
        }

        return Math.max(0.0, Math.min(1.0, probability));
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
                    // Cure all sick citizens
                    int curedCount = CitizenManipulator.cureAllCitizens(colony);
                    LOGGER.info("Plague ended: {} citizens cured in colony {}",
                        curedCount, colony.getName());
                    break;

                default:
                    // ROYAL_FEAST and CROP_BLIGHT are one-time saturation changes
                    // No restoration needed - saturation returns to normal naturally
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
     * Check if a colony is too new for events.
     *
     * @param colony The colony
     * @return true if protected by new colony grace period
     */
    private static boolean isNewColony(IColony colony) {
        int protectionHours = TaxConfig.getNewColonyProtectionHours();
        if (protectionHours <= 0) {
            return false;
        }

        // TODO: Need colony creation timestamp from MineColonies API
        // For now, assume no protection (will implement in Phase 2)
        return false;
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
}
