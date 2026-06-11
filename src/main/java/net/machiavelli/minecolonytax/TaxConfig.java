package net.machiavelli.minecolonytax;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber
public class TaxConfig {

        private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        public static ForgeConfigSpec CONFIG;

        public static final ForgeConfigSpec.BooleanValue ENABLE_SDM_SHOP_CONVERSION;
        public static final ForgeConfigSpec.ConfigValue<String> SDM_CURRENCY_NAME;
        public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_ITEM_NAME;
        public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_DENOMINATIONS;
        public static final ForgeConfigSpec.IntValue DEBT_LIMIT;
        public static final ForgeConfigSpec.IntValue TAX_STEAL_PER_GUARD;
        public static final ForgeConfigSpec.IntValue DEBT_EVENT_CYCLES;
        public static final ForgeConfigSpec.IntValue DEBT_ABANDONMENT_CYCLES;
        public static final ForgeConfigSpec.BooleanValue DEBT_BLOCKS_WAR;
        public static final ForgeConfigSpec.BooleanValue DEBT_BLOCKS_SPY;
        public static final ForgeConfigSpec.DoubleValue DEBT_HAPPINESS_FACTOR;
        public static final ForgeConfigSpec.IntValue MIN_GUARDS_TO_RAID;
        public static final ForgeConfigSpec.IntValue MAX_TAX_REVENUE;
        public static final ForgeConfigSpec.BooleanValue ENABLE_COLONY_TRANSFER;

        public static final Map<String, ForgeConfigSpec.DoubleValue> BUILDING_TAXES = new HashMap<>();
        public static final Map<String, ForgeConfigSpec.DoubleValue> UPGRADE_TAXES = new HashMap<>();

        private static final Map<String, String> CLASS_NAME_TO_SHORT_NAME = new HashMap<>();

        public static final ForgeConfigSpec.IntValue TAX_INTERVAL_MINUTES;

        public static final ForgeConfigSpec.IntValue ATTACKER_GRACE_PERIOD_MINUTES;
        public static final ForgeConfigSpec.IntValue RAID_GRACE_PERIOD_MINUTES;
        public static final ForgeConfigSpec.IntValue MAX_RAID_DURATION_MINUTES;
        public static final ForgeConfigSpec.IntValue RAID_TAX_INTERVAL_SECONDS;
        public static final ForgeConfigSpec.ConfigValue<List<Double>> RAID_TAX_PERCENTAGES;
        public static final ForgeConfigSpec.IntValue WAR_DURATION_MINUTES;
        public static final ForgeConfigSpec.IntValue PEACE_PROPOSAL_TIMEOUT_SECONDS;
        public static final ForgeConfigSpec.IntValue MIN_GUARDS_TO_WAGE_WAR;
        public static final ForgeConfigSpec.BooleanValue ENABLE_LP_GROUP_SWITCHING;
        public static final Map<String, ForgeConfigSpec.DoubleValue> BUILDING_MAINTENANCE = new HashMap<>();
        public static final Map<String, ForgeConfigSpec.DoubleValue> UPGRADE_MAINTENANCE = new HashMap<>();
        public static final ForgeConfigSpec.BooleanValue ALLOW_OFFLINE_RAIDS;
        public static final ForgeConfigSpec.DoubleValue RAID_PENALTY_PERCENTAGE;
        public static final ForgeConfigSpec.DoubleValue RAID_DEFENSE_REWARD_PERCENTAGE;
        public static final ForgeConfigSpec.DoubleValue WAR_VICTORY_PERCENTAGE;
        public static final ForgeConfigSpec.DoubleValue WAR_DEFEAT_PERCENTAGE;
        public static final ForgeConfigSpec.DoubleValue WAR_STALEMATE_PERCENTAGE;
        public static final ForgeConfigSpec.IntValue WAR_TAX_FREEZE_HOURS;
        public static final ForgeConfigSpec.IntValue WAR_TAX_FREEZE_CYCLES;
        public static final ForgeConfigSpec.DoubleValue MAX_COMBINED_WAR_PENALTY_PERCENT;
        public static final ForgeConfigSpec.DoubleValue MIN_TAX_GENERATION_PERCENT;

        // Colony Occupation Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_OCCUPATION_SYSTEM;
        public static final ForgeConfigSpec.IntValue OCCUPATION_DURATION_DAYS;
        public static final ForgeConfigSpec.DoubleValue OCCUPATION_TAX_PERCENTAGE;

        // High Stakes War Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_OUTPOST_VULNERABILITY;
        public static final ForgeConfigSpec.BooleanValue ENABLE_COLONY_WAGER;

        // War Vassalization Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_VASSALIZATION;
        public static final ForgeConfigSpec.IntValue WAR_VASSALIZATION_DURATION_HOURS;
        public static final ForgeConfigSpec.IntValue WAR_VASSALIZATION_TRIBUTE_PERCENTAGE;
        // One-time "huge money" grab applied when a war win vassalizes instead of transferring a colony.
        public static final ForgeConfigSpec.IntValue WAR_VASSALIZATION_TREASURY_GRAB_PERCENT;
        public static final ForgeConfigSpec.IntValue WAR_VASSALIZATION_PLAYER_BALANCE_GRAB_PERCENT;

        // Colony Tier Protection (Siege SMP ruleset)
        public static final ForgeConfigSpec.BooleanValue ENABLE_PRIMARY_COLONY_TRANSFER;
        public static final ForgeConfigSpec.IntValue PRIMARY_COLONY_TAX_OCCUPATION_DAYS;
        public static final ForgeConfigSpec.IntValue BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY;

        // Experimental Siege Objectives (step 11)
        public static final ForgeConfigSpec.BooleanValue ENABLE_EXPERIMENTAL_SIEGE_OBJECTIVES;
        public static final ForgeConfigSpec.IntValue TOWN_HALL_EXPLOSIVE_HITS_REQUIRED;
        public static final ForgeConfigSpec.IntValue TOWN_HALL_HIT_COOLDOWN_MINUTES;
        public static final ForgeConfigSpec.IntValue MAX_SIEGE_RADIUS;
        public static final ForgeConfigSpec.IntValue ATTACKER_GLOW_SECONDS;
        public static final ForgeConfigSpec.IntValue BANNER_CAPTURE_MINUTES;

        // Explosion't compat
        public static final ForgeConfigSpec.BooleanValue DEFER_RESTORATION_TO_EXPLOSIONT;
        public static final ForgeConfigSpec.IntValue JOIN_PHASE_DURATION_MINUTES;
        public static final ForgeConfigSpec.BooleanValue WAR_ACCEPTANCE_REQUIRED;
        public static final ForgeConfigSpec.BooleanValue KEEP_INVENTORY_ON_LAST_LIFE;

        public static final ForgeConfigSpec.IntValue REQUIRED_GUARD_TOWERS_FOR_BOOST;
        public static final ForgeConfigSpec.DoubleValue GUARD_TOWER_TAX_BOOST_PERCENTAGE;

        public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_ACTIONS;
        public static final ForgeConfigSpec.IntValue PLAYER_LIVES_IN_WAR;

        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CONFIGURABLE_WAR_ACTIONS;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CONFIGURABLE_RAID_ACTIONS;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CONFIGURABLE_CLAIMING_ACTIONS;

        // Block Interaction Filter Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_INTERACTION_FILTER;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_INTERACTION_BLACKLIST;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_INTERACTION_WHITELIST;
        public static final ForgeConfigSpec.BooleanValue BLOCK_FILTER_WARS;
        public static final ForgeConfigSpec.BooleanValue BLOCK_FILTER_RAIDS;
        public static final ForgeConfigSpec.BooleanValue SUPPRESS_COLONY_LEVITATION;

        // PvP Arena Settings
        public static final ForgeConfigSpec.BooleanValue PVP_COMMANDS_IN_BATTLE_ENABLED;
        public static final ForgeConfigSpec.IntValue CHALLENGE_COOLDOWN_SECONDS;
        public static final ForgeConfigSpec.IntValue TEAM_BATTLE_COOLDOWN_SECONDS;
        public static final ForgeConfigSpec.IntValue BATTLE_DURATION_SECONDS;
        public static final ForgeConfigSpec.IntValue TEAM_BATTLE_START_COUNTDOWN_SECONDS;
        public static final ForgeConfigSpec.IntValue BATTLE_END_COUNTDOWN_SECONDS;
        public static final ForgeConfigSpec.BooleanValue PVP_DISABLE_FRIENDLY_FIRE;

        // Logging configuration (0: MINIMAL, 1: NORMAL, 2: DEBUG)
        public static final ForgeConfigSpec.IntValue LOG_LEVEL;

        // RaidGuardProtection Configuration
        public static final ForgeConfigSpec.IntValue MIN_GUARDS_TO_BE_RAIDED;
        public static final ForgeConfigSpec.IntValue MIN_GUARD_TOWERS_TO_BE_RAIDED;
        public static final ForgeConfigSpec.BooleanValue ENABLE_RAID_GUARD_PROTECTION;

        // Entity Raid Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_ENTITY_RAIDS;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_RAID_WHITELIST;
        public static final ForgeConfigSpec.IntValue ENTITY_RAID_THRESHOLD;
        public static final ForgeConfigSpec.IntValue ENTITY_RAID_DETECTION_RADIUS;
        public static final ForgeConfigSpec.BooleanValue ENTITY_RAID_MESSAGE_ONLY;
        public static final ForgeConfigSpec.IntValue ENTITY_RAID_BOUNDARY_TIMER_SECONDS;

        public static final ForgeConfigSpec.IntValue ENTITY_RAID_CHECK_INTERVAL_TICKS;
        public static final ForgeConfigSpec.IntValue ENTITY_RAID_COOLDOWN_MINUTES;

        // Entity Raid Debug Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_ENTITY_RAID_DEBUG;
        public static final ForgeConfigSpec.IntValue ENTITY_RAID_DEBUG_LEVEL;
        public static final ForgeConfigSpec.BooleanValue BYPASS_ALLIANCE_CHECKS;

        // PvP Kill Economy Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_PVP_KILL_ECONOMY;
        public static final ForgeConfigSpec.DoubleValue PVP_KILL_REWARD_PERCENTAGE;

        // General Colony Permissions Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_GENERAL_ITEM_INTERACTIONS;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GENERAL_COLONY_ACTIONS;

        // Easy Factions Integration Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_EASY_FACTIONS_INTEGRATION;
        public static final ForgeConfigSpec.ConfigValue<String> EASY_FACTIONS_MEMBER_RANK;
        public static final ForgeConfigSpec.BooleanValue EASY_FACTIONS_PROMOTE_OFFICERS;
        public static final ForgeConfigSpec.IntValue EASY_FACTIONS_SYNC_INTERVAL_TICKS;

        // Guard Resistance During Raids Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_GUARD_RESISTANCE_DURING_RAIDS;
        public static final ForgeConfigSpec.IntValue GUARD_RESISTANCE_LEVEL;

        // Happiness-Based Tax Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_HAPPINESS_TAX_MODIFIER;
        public static final ForgeConfigSpec.DoubleValue HAPPINESS_TAX_MULTIPLIER_MIN;
        public static final ForgeConfigSpec.DoubleValue HAPPINESS_TAX_MULTIPLIER_MAX;

        // Colony Inactivity Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_COLONY_INACTIVITY_TAX_PAUSE;
        public static final ForgeConfigSpec.IntValue COLONY_INACTIVITY_HOURS_THRESHOLD;

        // Extortion System Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_EXTORTION_SYSTEM;
        public static final ForgeConfigSpec.DoubleValue DEFAULT_EXTORTION_PERCENTAGE;
        public static final ForgeConfigSpec.IntValue EXTORTION_RESPONSE_TIME_MINUTES;
        public static final ForgeConfigSpec.IntValue EXTORTION_IMMUNITY_HOURS;

        // Citizen Militia System Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_CITIZEN_MILITIA;
        public static final ForgeConfigSpec.DoubleValue MILITIA_CONVERSION_PERCENTAGE;
        public static final ForgeConfigSpec.IntValue MILITIA_MIN_CITIZEN_LEVEL;
        public static final ForgeConfigSpec.BooleanValue MILITIA_GUARDS_SEEK_RAIDERS;
        public static final ForgeConfigSpec.BooleanValue TAX_STEAL_PER_GUARD_KILLED;
        public static final ForgeConfigSpec.DoubleValue TAX_STEAL_PERCENTAGE_PER_GUARD;
        public static final ForgeConfigSpec.DoubleValue MAX_RAID_TAX_PERCENTAGE;
        public static final ForgeConfigSpec.BooleanValue APPLY_RESISTANCE_TO_CITIZENS;

        // Colony Auto-Abandon Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_COLONY_ABANDONMENT_SYSTEM;
        public static final ForgeConfigSpec.BooleanValue ENABLE_COLONY_AUTO_ABANDON;
        public static final ForgeConfigSpec.IntValue COLONY_AUTO_ABANDON_DAYS;
        public static final ForgeConfigSpec.BooleanValue NOTIFY_OWNERS_BEFORE_ABANDON;
        public static final ForgeConfigSpec.IntValue ABANDON_WARNING_DAYS;
        public static final ForgeConfigSpec.BooleanValue ENABLE_LIST_ABANDONED_FOR_ALL;
        public static final ForgeConfigSpec.BooleanValue RESET_TIMER_ON_OFFICER_LOGIN;

        // Abandoned Colony Claiming Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_ABANDONED_COLONY_CLAIMING;
        public static final ForgeConfigSpec.IntValue MIN_GUARDS_FOR_CLAIMING_RAID;
        public static final ForgeConfigSpec.IntValue CLAIMING_RAID_DURATION_MINUTES;
        public static final ForgeConfigSpec.IntValue CLAIMING_GRACE_PERIOD_HOURS;
        public static final ForgeConfigSpec.BooleanValue SPAWN_MERCENARIES_IF_LOW_DEFENDERS;
        public static final ForgeConfigSpec.ConfigValue<String> CLAIMING_BUILDING_REQUIREMENTS;

        // Raid Building Requirements Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_RAID_BUILDING_REQUIREMENTS;
        public static final ForgeConfigSpec.ConfigValue<String> RAID_BUILDING_REQUIREMENTS;

        // War Building Requirements Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_BUILDING_REQUIREMENTS;
        public static final ForgeConfigSpec.ConfigValue<String> WAR_BUILDING_REQUIREMENTS;

        // Recipe Disabling Configuration
        public static final ForgeConfigSpec.BooleanValue DISABLE_HUT_RECIPES;

        // Patchouli Book Configuration
        public static final ForgeConfigSpec.BooleanValue GIVE_PATCHOULI_BOOK_ON_JOIN;
        public static final ForgeConfigSpec.BooleanValue SHOW_ADMIN_PAGES_IN_BOOK;

        // Database Configuration
        public static final ForgeConfigSpec.BooleanValue DATABASE_ENABLED;
        public static final ForgeConfigSpec.ConfigValue<String> DATABASE_HOST;
        public static final ForgeConfigSpec.IntValue DATABASE_PORT;
        public static final ForgeConfigSpec.ConfigValue<String> DATABASE_NAME;
        public static final ForgeConfigSpec.ConfigValue<String> DATABASE_USERNAME;
        public static final ForgeConfigSpec.ConfigValue<String> DATABASE_PASSWORD;
        public static final ForgeConfigSpec.IntValue DATABASE_POOL_SIZE;
        public static final ForgeConfigSpec.IntValue DATABASE_SNAPSHOT_INTERVAL_SECONDS;

        // ========== TAX EXPANSION: Economy Settings ==========
        // Tax Policies
        public static final ForgeConfigSpec.BooleanValue ENABLE_TAX_POLICIES;
        public static final ForgeConfigSpec.DoubleValue TAX_POLICY_LOW_REVENUE_MODIFIER;
        public static final ForgeConfigSpec.DoubleValue TAX_POLICY_LOW_HAPPINESS_MODIFIER;
        public static final ForgeConfigSpec.DoubleValue TAX_POLICY_HIGH_REVENUE_MODIFIER;
        public static final ForgeConfigSpec.DoubleValue TAX_POLICY_HIGH_HAPPINESS_MODIFIER;
        public static final ForgeConfigSpec.DoubleValue TAX_POLICY_WAR_REVENUE_MODIFIER;
        public static final ForgeConfigSpec.DoubleValue TAX_POLICY_WAR_HAPPINESS_MODIFIER;

        // Tax Reports
        public static final ForgeConfigSpec.BooleanValue ENABLE_TAX_REPORTS;
        public static final ForgeConfigSpec.IntValue TAX_REPORT_AUTO_DELETE_HOURS;
        public static final ForgeConfigSpec.IntValue TAX_REPORT_MAX_HISTORY_DAYS;

        // Leaderboards
        public static final ForgeConfigSpec.BooleanValue ENABLE_LEADERBOARDS;
        public static final ForgeConfigSpec.IntValue LEADERBOARD_UPDATE_INTERVAL_MINUTES;
        public static final ForgeConfigSpec.IntValue LEADERBOARD_TOP_COUNT;

        // ========== TAX EXPANSION: Faction Settings ==========
        public static final ForgeConfigSpec.BooleanValue ENABLE_FACTION_SYSTEM;
        public static final ForgeConfigSpec.IntValue MAX_FACTION_MEMBERS;
        public static final ForgeConfigSpec.IntValue FACTION_CREATION_COST;
        public static final ForgeConfigSpec.IntValue FACTION_ALLIANCE_LIMIT;

        // Shared Tax Pool
        public static final ForgeConfigSpec.BooleanValue ENABLE_SHARED_TAX_POOL;
        public static final ForgeConfigSpec.IntValue DEFAULT_POOL_CONTRIBUTION_PERCENT;
        public static final ForgeConfigSpec.IntValue MAX_POOL_BALANCE;
        public static final ForgeConfigSpec.IntValue POOL_HISTORY_RETENTION_DAYS;

        // Trade Routes
        public static final ForgeConfigSpec.BooleanValue ENABLE_TRADE_ROUTES;
        public static final ForgeConfigSpec.IntValue TRADE_ROUTE_INCOME_PER_CHUNK;
        public static final ForgeConfigSpec.IntValue TRADE_ROUTE_MAX_DISTANCE_CHUNKS;
        public static final ForgeConfigSpec.IntValue TRADE_ROUTE_MAINTENANCE_COST;
        public static final ForgeConfigSpec.IntValue MAX_TRADE_ROUTES_PER_COLONY;

        // ========== TAX EXPANSION: Espionage Settings ==========
        public static final ForgeConfigSpec.BooleanValue ENABLE_SPY_SYSTEM;
        public static final ForgeConfigSpec.IntValue SPY_COOLDOWN_MINUTES;
        public static final ForgeConfigSpec.DoubleValue SPY_DETECTION_BASE_CHANCE;
        public static final ForgeConfigSpec.IntValue SPY_MAX_ACTIVE_PER_PLAYER;
        public static final ForgeConfigSpec.IntValue SPY_SCOUT_MAX_DURATION_HOURS;

        // Spy Action Costs
        public static final ForgeConfigSpec.IntValue SPY_SCOUT_COST;
        public static final ForgeConfigSpec.IntValue SPY_SABOTAGE_COST;
        public static final ForgeConfigSpec.IntValue SPY_BRIBE_GUARDS_COST;
        public static final ForgeConfigSpec.IntValue SPY_STEAL_SECRETS_COST;

        // Spy Action Effects
        public static final ForgeConfigSpec.DoubleValue SPY_SABOTAGE_TAX_REDUCTION_PERCENT;
        public static final ForgeConfigSpec.IntValue SPY_BRIBE_GUARDS_DISABLED_COUNT;
        public static final ForgeConfigSpec.IntValue SPY_STEAL_SECRETS_DURATION_HOURS;

        // Spy Action Min Field Times
        public static final ForgeConfigSpec.IntValue SPY_SABOTAGE_MIN_FIELD_MINUTES;

        // Spy Action Detection Chances
        public static final ForgeConfigSpec.DoubleValue SPY_SCOUT_DETECTION_CHANCE;
        public static final ForgeConfigSpec.DoubleValue SPY_SABOTAGE_DETECTION_CHANCE;
        public static final ForgeConfigSpec.DoubleValue SPY_BRIBE_GUARDS_DETECTION_CHANCE;
        public static final ForgeConfigSpec.DoubleValue SPY_STEAL_SECRETS_DETECTION_CHANCE;

        // Progressive Intel Thresholds
        public static final ForgeConfigSpec.IntValue INTEL_EARLY_THRESHOLD_MINUTES;
        public static final ForgeConfigSpec.IntValue INTEL_MID_THRESHOLD_MINUTES;
        public static final ForgeConfigSpec.IntValue INTEL_LATE_THRESHOLD_MINUTES;

        // Flee Behavior
        public static final ForgeConfigSpec.DoubleValue FLEE_SPEED_MULTIPLIER;
        public static final ForgeConfigSpec.IntValue FLEE_ESCAPE_DISTANCE;
        public static final ForgeConfigSpec.IntValue FLEE_MAX_SECONDS;

        // Spy Travel Phase
        public static final ForgeConfigSpec.IntValue SPY_TRAVEL_BLOCKS_PER_MINUTE;
        public static final ForgeConfigSpec.IntValue SPY_TRAVEL_MIN_MINUTES;
        public static final ForgeConfigSpec.IntValue SPY_TRAVEL_MAX_MINUTES;

        // Spy Map
        public static final ForgeConfigSpec.BooleanValue SPY_MAP_ENABLED;
        public static final ForgeConfigSpec.IntValue SPY_MAP_SCALE;

        // Colony Upgrades Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_COLONY_UPGRADES;
        public static final ForgeConfigSpec.IntValue UPGRADE_MAX_LEVEL;
        public static final ForgeConfigSpec.IntValue UPGRADE_MILITIA_COST_BASE;
        public static final ForgeConfigSpec.IntValue UPGRADE_SPY_CAPACITY_COST_BASE;
        public static final ForgeConfigSpec.IntValue UPGRADE_SPY_SPEED_COST_BASE;
        public static final ForgeConfigSpec.IntValue UPGRADE_SPY_EVASION_COST_BASE;
        public static final ForgeConfigSpec.IntValue UPGRADE_RAID_FORCE_COST_BASE;
        public static final ForgeConfigSpec.IntValue UPGRADE_DEFENSE_COST_BASE;
        public static final ForgeConfigSpec.DoubleValue UPGRADE_COST_SCALING_FACTOR;
        public static final ForgeConfigSpec.DoubleValue UPGRADE_MILITIA_MULTIPLIER_PER_LEVEL;
        public static final ForgeConfigSpec.IntValue UPGRADE_SPY_CAPACITY_PER_LEVEL;
        public static final ForgeConfigSpec.DoubleValue UPGRADE_SPY_SPEED_MULTIPLIER_PER_LEVEL;
        public static final ForgeConfigSpec.DoubleValue UPGRADE_DETECTION_REDUCTION_PER_LEVEL;
        public static final ForgeConfigSpec.IntValue UPGRADE_DEFENSE_BONUS_PER_LEVEL;
        public static final ForgeConfigSpec.IntValue UPGRADE_TREASURY_CAP_COST_BASE;
        public static final ForgeConfigSpec.IntValue UPGRADE_TAX_EFFICIENCY_COST_BASE;
        public static final ForgeConfigSpec.IntValue UPGRADE_FORTIFICATION_COST_BASE;
        public static final ForgeConfigSpec.IntValue UPGRADE_COUNTER_INTEL_COST_BASE;
        public static final ForgeConfigSpec.IntValue UPGRADE_TREASURY_CAP_FLAT_PER_LEVEL;
        public static final ForgeConfigSpec.DoubleValue UPGRADE_TAX_EFFICIENCY_PERCENT_PER_LEVEL;
        public static final ForgeConfigSpec.DoubleValue UPGRADE_FORTIFICATION_DAMAGE_REDUCTION_PER_LEVEL;
        public static final ForgeConfigSpec.DoubleValue UPGRADE_COUNTER_INTEL_DETECT_CHANCE_PER_LEVEL;
        public static final ForgeConfigSpec.DoubleValue UPGRADE_RAID_FORCE_MULTIPLIER_PER_LEVEL;

        // ========== TAX EXPANSION: Treasury ==========
        public static final ForgeConfigSpec.BooleanValue ENABLE_TREASURY;
        public static final ForgeConfigSpec.DoubleValue TREASURY_MIN_PERCENT_OF_TARGET;
        public static final ForgeConfigSpec.DoubleValue TREASURY_MIN_PERCENT_OF_OWN_TAX;
        public static final ForgeConfigSpec.IntValue TREASURY_DRAIN_PER_MINUTE;
        public static final ForgeConfigSpec.BooleanValue TREASURY_DRAIN_USE_PERCENT;
        public static final ForgeConfigSpec.DoubleValue TREASURY_DRAIN_PERCENT;
        public static final ForgeConfigSpec.IntValue TREASURY_MAX_CAPACITY;
        public static final ForgeConfigSpec.BooleanValue TREASURY_AUTO_SURRENDER_ENABLED;
        public static final ForgeConfigSpec.DoubleValue TREASURY_AUTO_DEPOSIT_PERCENT;
        public static final ForgeConfigSpec.DoubleValue WAR_DEFENDER_DRAIN_REDUCTION;

        // Raid Treasury
        public static final ForgeConfigSpec.BooleanValue RAID_TREASURY_ENABLED;
        public static final ForgeConfigSpec.DoubleValue RAID_TREASURY_COST_PERCENT;

        // Raid Penalties
        public static final ForgeConfigSpec.DoubleValue RAID_PENALTY_TAX_REDUCTION_PERCENT;
        public static final ForgeConfigSpec.IntValue RAID_PENALTY_DURATION_HOURS;
        public static final ForgeConfigSpec.DoubleValue RAID_REPAIR_COST_PERCENT;

        // ========== TAX EXPANSION: War Exhaustion ==========
        public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_EXHAUSTION;
        public static final ForgeConfigSpec.DoubleValue WAR_TAX_REDUCTION_PERCENT;
        public static final ForgeConfigSpec.IntValue POST_WAR_RECOVERY_HOURS;

        // War Reparations
        public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_REPARATIONS;
        public static final ForgeConfigSpec.DoubleValue REPARATIONS_TAX_PENALTY_PERCENT;
        public static final ForgeConfigSpec.IntValue REPARATIONS_DURATION_HOURS;
        public static final ForgeConfigSpec.IntValue REPARATIONS_TRIGGER_LOSSES_COUNT;

        // War Weariness Protection
        public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_WEARINESS;
        public static final ForgeConfigSpec.IntValue WAR_WEARINESS_THRESHOLD;
        public static final ForgeConfigSpec.IntValue WAR_WEARINESS_IMMUNITY_HOURS;

        // Vassalization Economy
        public static final ForgeConfigSpec.BooleanValue VASSALIZATION_REPLACES_REPARATIONS;

        // ========== TAX EXPANSION: Ransom System ==========
        public static final ForgeConfigSpec.BooleanValue ENABLE_RANSOM_SYSTEM;
        public static final ForgeConfigSpec.DoubleValue RANSOM_DEFAULT_PERCENT;
        public static final ForgeConfigSpec.IntValue RANSOM_MIN_AMOUNT;
        public static final ForgeConfigSpec.IntValue RANSOM_MAX_AMOUNT;
        public static final ForgeConfigSpec.IntValue RANSOM_TIMEOUT_SECONDS;
        public static final ForgeConfigSpec.IntValue RANSOM_COOLDOWN_MINUTES;
        public static final ForgeConfigSpec.IntValue RANSOM_IMMUNITY_AFTER_PAYMENT_HOURS;

        // ========== TAX EXPANSION: Money Sinks / Investments ==========
        public static final ForgeConfigSpec.BooleanValue ENABLE_INVESTMENTS;
        public static final ForgeConfigSpec.DoubleValue INVESTMENT_DIMINISHING_RETURNS_FACTOR;

        // Infrastructure Investment
        public static final ForgeConfigSpec.IntValue INVESTMENT_INFRASTRUCTURE_COST;
        public static final ForgeConfigSpec.DoubleValue INVESTMENT_INFRASTRUCTURE_BONUS;
        public static final ForgeConfigSpec.IntValue INVESTMENT_INFRASTRUCTURE_MAX_STACKS;

        // Guard Training Investment
        public static final ForgeConfigSpec.IntValue INVESTMENT_GUARD_TRAINING_COST;
        public static final ForgeConfigSpec.DoubleValue INVESTMENT_GUARD_TRAINING_DAMAGE_BONUS;
        public static final ForgeConfigSpec.IntValue INVESTMENT_GUARD_TRAINING_DURATION_HOURS;

        // Festival Investment
        public static final ForgeConfigSpec.IntValue INVESTMENT_FESTIVAL_COST;
        public static final ForgeConfigSpec.DoubleValue INVESTMENT_FESTIVAL_HAPPINESS_BONUS;
        public static final ForgeConfigSpec.IntValue INVESTMENT_FESTIVAL_DURATION_HOURS;

        // Research Investment
        public static final ForgeConfigSpec.IntValue INVESTMENT_RESEARCH_COST;
        public static final ForgeConfigSpec.DoubleValue INVESTMENT_RESEARCH_SPY_DEFENSE_BONUS;
        public static final ForgeConfigSpec.IntValue INVESTMENT_RESEARCH_DURATION_DAYS;

        // ==================== BESIEGE SYSTEM ====================
        public static final ForgeConfigSpec.BooleanValue ENABLE_BESIEGE_SYSTEM;
        public static final ForgeConfigSpec.IntValue BESIEGE_DURATION_MINUTES;
        public static final ForgeConfigSpec.IntValue BESIEGE_COOLDOWN_HOURS;
        public static final ForgeConfigSpec.DoubleValue BESIEGE_MILITIA_PERCENT;
        public static final ForgeConfigSpec.IntValue BESIEGE_TRIBUTE_PERCENT;
        public static final ForgeConfigSpec.IntValue BESIEGE_TRIBUTE_DURATION_HOURS;
        public static final ForgeConfigSpec.IntValue BESIEGE_MIN_COLONY_SIZE;
        public static final ForgeConfigSpec.BooleanValue BESIEGE_ALLIES_ENABLED;
        public static final ForgeConfigSpec.DoubleValue BESIEGE_EXTRA_MERCENARIES_PER_BUILDING;
        public static final ForgeConfigSpec.IntValue BESIEGE_MAX_MERCENARIES;
        public static final ForgeConfigSpec.IntValue BESIEGE_PLAYER_STAY_RADIUS;
        // Siege SMP follow-up — access controls: chest access + vassalized-owner lockout
        public static final ForgeConfigSpec.BooleanValue BESIEGE_ALLOW_CHEST_ACCESS;
        public static final ForgeConfigSpec.BooleanValue VASSAL_LOCK_OUT_FORMER_OWNER;
        // Siege SMP follow-up — multiplayer & online rules: min attackers, shared spoils, online requirement
        public static final ForgeConfigSpec.IntValue BESIEGE_MIN_ATTACKERS;
        public static final ForgeConfigSpec.BooleanValue BESIEGE_SHARE_SPOILS;
        public static final ForgeConfigSpec.BooleanValue BESIEGE_REQUIRE_ONLINE;
        public static final ForgeConfigSpec.IntValue BESIEGE_OFFLINE_GRACE_MINUTES;

        // ==================== RANDOM EVENTS SYSTEM ====================
        public static final ForgeConfigSpec.BooleanValue ENABLE_RANDOM_EVENTS;
        public static final ForgeConfigSpec.IntValue RANDOM_EVENT_CHECK_FREQUENCY;
        public static final ForgeConfigSpec.IntValue RANDOM_EVENT_GLOBAL_COOLDOWN_CYCLES;
        public static final ForgeConfigSpec.IntValue RANDOM_EVENT_MAX_SIMULTANEOUS;
        public static final ForgeConfigSpec.DoubleValue RANDOM_EVENT_BASE_CHANCE_MULTIPLIER;
        public static final ForgeConfigSpec.IntValue RANDOM_EVENT_PROTECT_NEW_COLONIES_HOURS;

        // Individual Event Toggles
        public static final ForgeConfigSpec.BooleanValue ENABLE_MERCHANT_CARAVAN;
        public static final ForgeConfigSpec.BooleanValue ENABLE_BOUNTIFUL_HARVEST;
        public static final ForgeConfigSpec.BooleanValue ENABLE_CULTURAL_FESTIVAL;
        public static final ForgeConfigSpec.BooleanValue ENABLE_SUCCESSFUL_RECRUITMENT;
        public static final ForgeConfigSpec.BooleanValue ENABLE_FOOD_SHORTAGE;
        public static final ForgeConfigSpec.BooleanValue ENABLE_DISEASE_OUTBREAK;
        public static final ForgeConfigSpec.BooleanValue ENABLE_BANDIT_HARASSMENT;
        public static final ForgeConfigSpec.BooleanValue ENABLE_CORRUPT_OFFICIAL;
        public static final ForgeConfigSpec.BooleanValue ENABLE_WANDERING_TRADER_OFFER;
        public static final ForgeConfigSpec.BooleanValue ENABLE_NEIGHBORING_ALLIANCE;
        public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_PROFITEERING;
        public static final ForgeConfigSpec.BooleanValue ENABLE_GUARD_DESERTION;
        public static final ForgeConfigSpec.BooleanValue ENABLE_LABOR_STRIKE;
        public static final ForgeConfigSpec.BooleanValue ENABLE_PLAGUE_OUTBREAK;
        public static final ForgeConfigSpec.BooleanValue ENABLE_ROYAL_FEAST;
        public static final ForgeConfigSpec.BooleanValue ENABLE_CROP_BLIGHT;

        static {

                BUILDER.push("General");
                TAX_INTERVAL_MINUTES = BUILDER.comment("Tax generation interval in minutes")
                                .defineInRange("TaxIntervalMinutes", 60, 1, 1440); // Default 60 minutes, min 1, max
                                                                                   // 1440 (1 day)

                MAX_TAX_REVENUE = BUILDER.comment(
                                "Maximum tax revenue a colony can store before it stops generating further taxes. "
                                                + "Keep tight to encourage micromanagement and daily engagement.")
                                .defineInRange("MaxTaxRevenue", 10000, 1, Integer.MAX_VALUE);

                ENABLE_SDM_SHOP_CONVERSION = BUILDER
                                .comment("Enable SDMShop conversion (true = enable, false = disable).")
                                .define("EnableSDMShopConversion", true);

                SDM_CURRENCY_NAME = BUILDER
                                .comment("The SDM-Economy currency id that claimed taxes are deposited into.",
                                                "Must match the currency id configured in SDM-Economy (NOT the display name).",
                                                "Default 'sdm_coin'. If your server uses a different currency id, set it here,",
                                                "otherwise claimed taxes are deposited into a currency you never see.")
                                .define("SDMCurrencyName", "sdm_coin");

                CURRENCY_ITEM_NAME = BUILDER
                                .comment("The item name for the custom currency (e.g., 'minecraft:emerald').")
                                .define("CurrencyItemName", "minecraft:emerald");

                CURRENCY_DENOMINATIONS = BUILDER
                                .comment(
                                    "Optional multi-denomination currency. Format: 'namespace:item:value,...' ordered highest to lowest.",
                                    "Example: 'minecolonytax:gold_coin:1000,minecolonytax:silver_coin:100,minecraft:emerald:1'",
                                    "Leave empty (default) to use only the single CurrencyItemName above.")
                                .define("CurrencyDenominations", "");
                LOG_LEVEL = BUILDER.comment(
                                "Logging verbosity level. 0 = MINIMAL (only errors/warnings/critical), 1 = NORMAL (compact summaries), 2 = DEBUG (full verbose output for troubleshooting).")
                                .defineInRange("LogLevel", 0, 0, 2);

                BUILDER.pop();

                DEBT_LIMIT = BUILDER.comment("Debt limit for colony debt. " +
                                "If > 0, colony revenue will not deduct more once the debt reaches this limit (i.e. the tax value won't drop below -DebtLimit). "
                                +
                                "Default 2000 to give military maintenance real consequences.")
                                .defineInRange("DebtLimit", 2000, 0, Integer.MAX_VALUE);

                TAX_STEAL_PER_GUARD = BUILDER.comment(
                                "Amount of debt added to colony per guard killed when raiding colonies in debt. " +
                                                "Only applies when DebtLimit > 0. Raiders get this amount when killing guards in debt colonies. Default: 200.")
                                .defineInRange("TaxStealPerGuard", 200, 1, 10000);

                DEBT_EVENT_CYCLES = BUILDER.comment(
                                "Number of consecutive debt cycles before triggering bandit harassment and guard desertion events on the colony. " +
                                                "0 = disabled. Default: 3.")
                                .defineInRange("DebtEventCycles", 3, 0, 100);

                DEBT_ABANDONMENT_CYCLES = BUILDER.comment(
                                "Number of consecutive max-debt cycles before the colony is forcibly abandoned due to tax bankruptcy. " +
                                                "Only triggers when the colony is at the DebtLimit cap. 0 = disabled. Default: 10.")
                                .defineInRange("DebtAbandonmentCycles", 10, 0, 200);

                DEBT_BLOCKS_WAR = BUILDER.comment(
                                "If true, a colony at maximum debt (DebtLimit) cannot declare war until the debt is cleared. Default: true.")
                                .define("DebtBlocksWar", true);

                DEBT_BLOCKS_SPY = BUILDER.comment(
                                "If true, a colony at maximum debt (DebtLimit) cannot deploy spy missions until the debt is cleared. Default: true.")
                                .define("DebtBlocksSpy", true);

                DEBT_HAPPINESS_FACTOR = BUILDER.comment(
                                "Happiness modifier factor applied to all citizens each cycle the colony is in debt (0.0-1.0 = penalty, values below 1 reduce happiness). " +
                                                "Applied via MineColonies happiness system. Default: 0.6.")
                                .defineInRange("DebtHappinessFactor", 0.6, 0.0, 2.0);

                // ========== War Settings ==========
                BUILDER.push("War Settings");

                ENABLE_COLONY_TRANSFER = BUILDER.comment(
                                "Enable colony ownership transfer when a war is won (true = enable, false = disable).")
                                .define("EnableColonyTransfer", true);

                ENABLE_WAR_ACTIONS = BUILDER.comment("If false, war will not toggle any interaction permissions")
                                .define("EnableWarActions", true);

                WAR_ACCEPTANCE_REQUIRED = BUILDER.comment(
                                "If true, war requests must be manually accepted; if false, wars requests will automatically accept.")
                                .define("WarAcceptanceRequired", true);

                ATTACKER_GRACE_PERIOD_MINUTES = BUILDER.comment("Grace period between declaring wars (minutes)")
                                .defineInRange("AttackerGracePeriodMinutes", 120, 1, 1440); // Default 2 hours

                RAID_GRACE_PERIOD_MINUTES = BUILDER.comment("Grace period between raids (minutes)")
                                .defineInRange("RaidGracePeriodMinutes", 120, 1, 1440); // Default 2h

                MAX_RAID_DURATION_MINUTES = BUILDER.comment("Maximum raid duration (minutes)")
                                .defineInRange("MaxRaidDurationMinutes", 5, 1, 1440);

                ALLOW_OFFLINE_RAIDS = BUILDER
                                .comment("Allow players to raid colonies even if the colony owner is offline.")
                                .define("AllowOfflineRaids", true);

                RAID_PENALTY_PERCENTAGE = BUILDER.comment(
                                "Penalty percentage applied when a raider is killed by a defender during a raid (0.0 - 1.0)")
                                .defineInRange("RaidPenaltyPercentage", 0.25, 0.0, 1.0);

                RAID_DEFENSE_REWARD_PERCENTAGE = BUILDER.comment(
                                "Percentage of killed raider's balance transferred to defending colony for owner/officers to claim (0.0 - 1.0)")
                                .defineInRange("RaidDefenseRewardPercentage", 0.15, 0.0, 1.0);

                WAR_VICTORY_PERCENTAGE = BUILDER.comment(
                                "Percentage of losing players' balance awarded to winning side. " +
                                                "Set equal to WarDefeatPercentage to prevent inflation (pool-based: winners get what losers lose). "
                                                +
                                                "Set to 0.0 to only enable colony transfer (if enabled).\n"
                                                +
                                                "Uses SDMShop balance or colony funds based on what's configured.")
                                .defineInRange("WarVictoryPercentage", 0.20, 0.0, 1.0);

                WAR_DEFEAT_PERCENTAGE = BUILDER.comment(
                                "Percentage that each losing player loses from their balance when defeated in war. " +
                                                "Should match WarVictoryPercentage to prevent money creation/destruction.\n"
                                                +
                                                "Uses SDMShop balance or colony funds based on what's configured.")
                                .defineInRange("WarDefeatPercentage", 0.20, 0.0, 1.0);

                WAR_STALEMATE_PERCENTAGE = BUILDER.comment(
                                "Percentage that all war participants lose from their balance when a war ends in stalemate.\n"
                                                +
                                                "Uses SDMShop balance or colony funds based on what's configured.")
                                .defineInRange("WarStalematePercentage", 0.10, 0.0, 1.0);

                WAR_TAX_FREEZE_HOURS = BUILDER.comment(
                                "[DEPRECATED] Use WarTaxFreezeCycles instead. Duration (in hours) to freeze colony tax generation after a war loss or stalemate.\n"
                                                +
                                                "Set to 0 to disable tax freezing. Ignored if WarTaxFreezeCycles > 0.")
                                .defineInRange("WarTaxFreezeHours", 0, 0, 168); // Max 1 week

                WAR_TAX_FREEZE_CYCLES = BUILDER.comment(
                                "Number of tax cycles to skip after a war loss or stalemate. " +
                                                "Cycle-based freezing ensures consistent behavior regardless of TaxIntervalMinutes. "
                                                +
                                                "At default 60min intervals, 3 cycles = 3 hours. Takes precedence over WarTaxFreezeHours.")
                                .defineInRange("WarTaxFreezeCycles", 3, 0, 20);

                MAX_COMBINED_WAR_PENALTY_PERCENT = BUILDER.comment(
                                "Maximum combined penalty from war exhaustion + active war tax multiplier (0.0-1.0).\n"
                                                +
                                                "Prevents excessive penalty stacking. 0.5 = 50% max total penalty.")
                                .defineInRange("MaxCombinedWarPenaltyPercent", 0.5, 0.0, 1.0);

                MIN_TAX_GENERATION_PERCENT = BUILDER.comment(
                                "Minimum tax generation floor as a percentage of base generation (0.0-1.0). " +
                                                "After all penalties stack (raid, war exhaustion, reparations, events), "
                                                +
                                                "tax income will never drop below this fraction. " +
                                                "Prevents death spirals where colonies can never recover. " +
                                                "0.30 = colonies always earn at least 30% of their base income.")
                                .defineInRange("MinTaxGenerationPercent", 0.30, 0.0, 1.0);

                ENABLE_WAR_VASSALIZATION = BUILDER.comment(
                                "When enabled and ENABLE_COLONY_TRANSFER is disabled, winning a war will vassalize the losing colony instead of transferring ownership.\n"
                                                +
                                                "The losing colony will pay a percentage of their tax income to the winner for a set duration.")
                                .define("EnableWarVassalization", true);

                WAR_VASSALIZATION_DURATION_HOURS = BUILDER
                                .comment("Duration (in hours) that a war vassalization lasts.\n" +
                                                "After this time, the vassalization automatically ends. Set to 0 for permanent vassalization until manually revoked.")
                                .defineInRange("WarVassalizationDurationHours", 168, 0, 8760); // Default 1 week, max 1
                                                                                               // year

                WAR_VASSALIZATION_TRIBUTE_PERCENTAGE = BUILDER.comment(
                                "Percentage of the vassal colony's tax income paid to the victor as tribute (1-100).\n"
                                                +
                                                "Lowered default to 15% to avoid stacking too harshly with other war penalties. "
                                                +
                                                "Set VassalizationReplacesReparations=true to make tribute the only penalty.")
                                .defineInRange("WarVassalizationTributePercentage", 15, 1, 100); // Default 15%

                // One-time "huge money" grab for the vassalize-only war outcome (deed transfer disabled).
                // The matching access toggle, VassalLockOutFormerOwner, lives in the [Besiege System] section.
                WAR_VASSALIZATION_TREASURY_GRAB_PERCENT = BUILDER.comment(
                                "[Spoils] One-time grab applied to the LOSING colony's treasury when a war win results in\n"
                                                +
                                                "vassalization (the vassalize-only path, used when EnableColonyTransfer is disabled).\n"
                                                +
                                                "This percentage moves from the loser's colony treasury to the victor's primary colony\n"
                                                +
                                                "treasury immediately on victory. Colony-side half of the 'take the huge money' rule.\n"
                                                +
                                                "Set 0 to disable.")
                                .defineInRange("WarVassalizationTreasuryGrabPercent", 50, 0, 100);

                WAR_VASSALIZATION_PLAYER_BALANCE_GRAB_PERCENT = BUILDER.comment(
                                "[Spoils] One-time grab applied to the LOSING player's personal wallet when a war win results\n"
                                                +
                                                "in vassalization. Percentage of their wallet taken and paid to the victor. Player-side half\n"
                                                +
                                                "of the 'take the huge money from the player' rule.\n"
                                                +
                                                "Requires an economy mod (SDMShop/SDMEconomy). Set 0 to disable.")
                                .defineInRange("WarVassalizationPlayerBalanceGrabPercent", 25, 0, 100);

                ENABLE_PRIMARY_COLONY_TRANSFER = BUILDER.comment(
                                "If false (default), a player's first colony (Primary) is protected from ownership transfer.\n"
                                                +
                                                "Primary colonies can still be tax-occupied via besiege or vassalized via full war,\n"
                                                +
                                                "but the deed never moves to a new owner. Secondary colonies are always transferable\n"
                                                +
                                                "when ENABLE_COLONY_TRANSFER is enabled. Flip this to true for a no-mercy SMP\n"
                                                +
                                                "where even home bases can be permanently lost.")
                                .define("EnablePrimaryColonyTransfer", false);

                PRIMARY_COLONY_TAX_OCCUPATION_DAYS = BUILDER.comment(
                                "Duration (real-time days) a Primary colony stays tax-occupied after a successful besiege.\n"
                                                +
                                                "During this window the besieger collects 100% of the colony's taxes and the original\n"
                                                +
                                                "owner is locked out of GUI/permissions, but the deed never transfers. If the owner\n"
                                                +
                                                "does not mount a successful counter-besiege within this window, the occupation\n"
                                                +
                                                "auto-reclaims (taxes route back to the owner). Secondary colonies use the standard\n"
                                                +
                                                "OccupationDurationDays config and DO transfer on expiry.")
                                .defineInRange("PrimaryColonyTaxOccupationDays", 7, 1, 90);

                BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY = BUILDER.comment(
                                "One-shot percentage of the loser's treasury transferred to the winner on besiege resolution.\n"
                                                +
                                                "Applied IN ADDITION to ongoing tax-occupation tribute. On attacker victory: extracted\n"
                                                +
                                                "from the besieged colony's treasury into the besieger's primary colony treasury.\n"
                                                +
                                                "On defender victory: extracted from the besieger's primary colony treasury into the\n"
                                                +
                                                "defending colony's treasury. 0 disables siege spoils entirely.")
                                .defineInRange("BesiegeSpoilPercentOfLoserTreasury", 25, 0, 100);

                ENABLE_EXPERIMENTAL_SIEGE_OBJECTIVES = BUILDER.comment(
                                "EXPERIMENTAL — when enabled, full wars get an additional win condition:\n"
                                                +
                                                "the attacker can win by landing N explosive hits on the defender's Town Hall.\n"
                                                +
                                                "Runs IN PARALLEL with the legacy lives+guards system — first trigger wins.\n"
                                                +
                                                "Banner Capture (other objective from the design) is NOT yet implemented and\n"
                                                +
                                                "would require a new item/block registration.")
                                .define("EnableExperimentalSiegeObjectives", false);

                TOWN_HALL_EXPLOSIVE_HITS_REQUIRED = BUILDER.comment(
                                "Number of counted explosive hits on the Town Hall building required for attacker victory.")
                                .defineInRange("TownHallExplosiveHitsRequired", 5, 1, 50);

                TOWN_HALL_HIT_COOLDOWN_MINUTES = BUILDER.comment(
                                "Per-attacker cooldown (minutes) between counted Town Hall hits. Prevents stacking\n"
                                                +
                                                "TNT in one chunk to instawin. Hits during cooldown still damage blocks but don't count.")
                                .defineInRange("TownHallHitCooldownMinutes", 5, 1, 60);

                MAX_SIEGE_RADIUS = BUILDER.comment(
                                "Maximum distance (blocks) from the Town Hall centre the attacker may be in order\n"
                                                +
                                                "for an explosion to count as a Town Hall hit. Prevents remote-triggering.")
                                .defineInRange("MaxSiegeRadius", 500, 50, 2000);

                ATTACKER_GLOW_SECONDS = BUILDER.comment(
                                "Seconds the GLOWING effect is applied to an attacker after they land a counted hit.\n"
                                                +
                                                "Lets defenders see them through walls and converge.")
                                .defineInRange("AttackerGlowSeconds", 30, 0, 600);

                BANNER_CAPTURE_MINUTES = BUILDER.comment(
                                "Minutes the attacker must hold a planted Siege Banner inside the Town Hall building\n"
                                                +
                                                "for the capture to complete. Defenders can break the banner to cancel — the\n"
                                                +
                                                "attacker may re-plant once. Behind EnableExperimentalSiegeObjectives.")
                                .defineInRange("BannerCaptureMinutes", 10, 1, 120);

                DEFER_RESTORATION_TO_EXPLOSIONT = BUILDER.comment(
                                "Compat: if Harmonised's 'Explosion't' mod is installed AND this is true, the WarBlockLedger\n"
                                                +
                                                "skips its own snapshot/restore pipeline and lets Explosion't handle all explosion\n"
                                                +
                                                "damage globally. Trade-off: Explosion't restores ALL world-wide explosion damage\n"
                                                +
                                                "(including outside the colony bracket), not just war damage. Leave false to keep\n"
                                                +
                                                "the per-war scoped restoration; flip true if you want one system in charge.")
                                .define("DeferRestorationToExplosiont", false);

                // ========== Colony Occupation Settings ==========
                BUILDER.push("Colony Occupation");

                ENABLE_OCCUPATION_SYSTEM = BUILDER.comment(
                                "When enabled and colony transfer is enabled, winning a war puts the colony into an OCCUPIED state\n"
                                                + "instead of immediately transferring ownership. The occupier can collect taxes but cannot\n"
                                                + "interact with colony buildings or items. The original owner has a configurable number of days\n"
                                                + "to wage a reclamation war. If no reclamation is attempted, full ownership transfers automatically.")
                                .define("EnableOccupationSystem", true);

                OCCUPATION_DURATION_DAYS = BUILDER.comment(
                                "Number of real-time days the original owner has to reclaim an occupied colony.\n"
                                                + "After this period expires without a reclamation war, full ownership permanently transfers to the occupier.")
                                .defineInRange("OccupationDurationDays", 7, 1, 90); // Default 7 days, max 90 days

                OCCUPATION_TAX_PERCENTAGE = BUILDER.comment(
                                "Percentage of the occupied colony's generated tax that is diverted to the occupier (0.0 - 1.0).\n"
                                                + "For example, 0.5 means 50% of the colony's tax income goes to the occupier.")
                                .defineInRange("OccupationTaxPercentage", 0.50, 0.0, 1.0);

                BUILDER.pop(); // Colony Occupation

                // ========== High Stakes War Settings ==========
                BUILDER.push("High Stakes War");

                ENABLE_OUTPOST_VULNERABILITY = BUILDER.comment(
                                "When enabled, SECONDARY colonies (outposts) can be attacked even if the owner is offline.\n"
                                                + "PRIMARY colonies (a player's first colony) still require the owner to be online.\n"
                                                + "This creates risk for empire expansion - outposts need strong passive defenses!")
                                .define("EnableOutpostVulnerability", true);

                ENABLE_COLONY_WAGER = BUILDER.comment(
                                "When enabled, the attacker's colony becomes a WAGER in the war.\n"
                                                + "If the attacker WINS: Target colony enters OCCUPIED state (attacker collects taxes).\n"
                                                + "If the DEFENDER WINS: Attacker's wagered colony enters OCCUPIED state (defender collects taxes)!\n"
                                                + "This creates high-risk, high-reward warfare where attackers cannot safely grief weaker players.")
                                .define("EnableColonyWager", true);

                BUILDER.pop(); // High Stakes War

                MIN_GUARDS_TO_RAID = BUILDER.comment("Minimum number of guards required to initiate a raid. " +
                                "NOTE: This is only used when 'EnableRaidBuildingRequirements' is disabled. " +
                                "If building requirements are enabled, they take priority over this setting.")
                                .defineInRange("MinGuardsToRaid", 3, 1, 100);

                // RaidGuardProtection Configuration - Protects smaller colonies from being
                // raided
                ENABLE_RAID_GUARD_PROTECTION = BUILDER.comment(
                                "Enable raid guard protection system. When enabled, colonies must meet minimum defense requirements to be eligible for raids, protecting smaller/newer colonies from being overwhelmed.")
                                .define("EnableRaidGuardProtection", true);

                MIN_GUARDS_TO_BE_RAIDED = BUILDER.comment(
                                "Minimum number of guards a colony must have to be eligible for raids. Set to 0 to disable guard requirement. Default: 2 guards minimum.")
                                .defineInRange("MinGuardsToBeRaided", 2, 0, 100);

                MIN_GUARD_TOWERS_TO_BE_RAIDED = BUILDER.comment(
                                "Minimum number of guard towers a colony must have to be eligible for raids. Set to 0 to disable guard tower requirement. Default: 1 guard tower minimum.")
                                .defineInRange("MinGuardTowersToBeRaided", 1, 0, 100);

                RAID_TAX_INTERVAL_SECONDS = BUILDER.comment("Interval between tax transfers during raids (seconds)")
                                .defineInRange("RaidTaxIntervalSeconds", 60, 5, 3600);

                RAID_TAX_PERCENTAGES = BUILDER
                                .comment("Tax transfer percentages during raids (comma-separated decimals). " +
                                                "Each tier applies at the RaidTaxIntervalSeconds interval. " +
                                                "Keep values consistent with MaxRaidTaxPercentage.")
                                .define("RaidTaxPercentages", List.of(0.05, 0.10, 0.15, 0.25));

                // ========== Entity Raid Settings ==========
                BUILDER.push("Entity Raid Settings");

                ENABLE_ENTITY_RAIDS = BUILDER.comment(
                                "Enable entity-triggered raids. When disabled, entities will not trigger raids even if they meet the criteria. Admin-only feature.")
                                .define("EnableEntityRaids", false);

                ENTITY_RAID_WHITELIST = BUILDER.comment(
                                "List of entity types that can trigger raids. Use Minecraft entity resource IDs (e.g., 'minecraft:pillager'). Default: only Pillagers.")
                                .defineList("EntityRaidWhitelist",
                                                List.of("minecraft:pillager"),
                                                obj -> obj instanceof String);

                ENTITY_RAID_THRESHOLD = BUILDER
                                .comment("Number of whitelisted entities near a colony required to trigger a raid")
                                .defineInRange("EntityRaidThreshold", 1, 1, 50);

                ENTITY_RAID_DETECTION_RADIUS = BUILDER.comment(
                                "Radius in blocks to detect entities around colonies for raid triggering (default: 50 blocks)")
                                .defineInRange("EntityRaidDetectionRadius", 50, 10, 500);

                ENTITY_RAID_MESSAGE_ONLY = BUILDER.comment(
                                "If true, entity raids will only send messages to colony and allies without triggering actual raid mechanics. Set to false for full raid functionality.")
                                .define("EntityRaidMessageOnly", false);

                ENTITY_RAID_BOUNDARY_TIMER_SECONDS = BUILDER.comment(
                                "Time in seconds entities have to return to colony boundaries after leaving during an entity raid")
                                .defineInRange("EntityRaidBoundaryTimerSeconds", 5, 1, 60);

                ENTITY_RAID_CHECK_INTERVAL_TICKS = BUILDER.comment(
                                "How often (in ticks) to check for entities near colonies. Lower values = more frequent checks but higher performance cost. 20 ticks = 1 second")
                                .defineInRange("EntityRaidCheckIntervalTicks", 20, 10, 200);

                ENTITY_RAID_COOLDOWN_MINUTES = BUILDER
                                .comment("Cooldown period between entity raids for the same colony (minutes)")
                                .defineInRange("EntityRaidCooldownMinutes", 30, 1, 1440);

                // Debug Configuration
                ENABLE_ENTITY_RAID_DEBUG = BUILDER.comment("Enable detailed debug logging for EntityRaid system. " +
                                "When enabled, provides comprehensive logging of entity detection, filtering, and raid triggering processes.")
                                .define("EnableEntityRaidDebug", false);

                ENTITY_RAID_DEBUG_LEVEL = BUILDER.comment("Debug logging verbosity level for EntityRaid system:\n" +
                                "1 = Basic (raid triggers, major events)\n" +
                                "2 = Detailed (entity filtering, alliance checks)\n" +
                                "3 = Verbose (all detection steps, performance metrics)")
                                .defineInRange("EntityRaidDebugLevel", 1, 1, 3);

                BYPASS_ALLIANCE_CHECKS = BUILDER.comment(
                                "TESTING ONLY: Bypass alliance checks to allow your own recruits to trigger raids (set to false for production)")
                                .define("BypassAllianceChecks", false);

                BUILDER.pop();

                // ========== PvP Kill Economy Settings ==========
                BUILDER.push("PvP Kill Economy");

                ENABLE_PVP_KILL_ECONOMY = BUILDER.comment(
                                "Enable PvP kill economy system. When enabled, killing a player transfers a percentage of their balance to the killer. "
                                                +
                                                "Compatible with SDMShop and SDMEconomy. Disabled by default.")
                                .define("EnablePvPKillEconomy", false);

                PVP_KILL_REWARD_PERCENTAGE = BUILDER.comment(
                                "Percentage of victim's balance transferred to killer on PvP kill (0.0 - 1.0). " +
                                                "For example: 0.1 = 10% of victim's money goes to killer. Uses SDMShop balance or colony funds based on configuration.")
                                .defineInRange("PvPKillRewardPercentage", 0.1, 0.0, 1.0);

                BUILDER.pop();

                // ========== General Colony Permissions ==========
                BUILDER.push("General Colony Permissions");

                ENABLE_GENERAL_ITEM_INTERACTIONS = BUILDER.comment(
                                "Enable general item interactions for all players in colonies. When enabled, allows non-allies to toss items and pickup items within colony boundaries.")
                                .define("EnableGeneralItemInteractions", true);

                GENERAL_COLONY_ACTIONS = BUILDER.comment(
                                "Actions allowed for all players in colonies when general interactions are enabled. See https://ldtteam.github.io/MineColoniesAPI/com/minecolonies/api/colony/permissions/Action.html for a list of possible actions.")
                                .defineList("GeneralColonyActions",
                                                List.of("TOSS_ITEM", "PICKUP_ITEM"),
                                                obj -> obj instanceof String);

                // Guard Resistance During Raids Configuration
                ENABLE_GUARD_RESISTANCE_DURING_RAIDS = BUILDER.comment(
                                "Enable resistance effect for colony guards during raids. When enabled, guards will receive a resistance effect to help defend the colony.")
                                .define("EnableGuardResistanceDuringRaids", true);

                GUARD_RESISTANCE_LEVEL = BUILDER.comment(
                                "Level of resistance effect applied to guards during raids (1-255). Higher levels provide better protection. Set to 0 to disable even if the feature is enabled.")
                                .defineInRange("GuardResistanceLevel", 2, 0, 255);

                // Happiness-Based Tax Configuration
                ENABLE_HAPPINESS_TAX_MODIFIER = BUILDER.comment(
                                "Enable happiness-based tax modifiers. When enabled, colony citizen happiness affects tax generation rates.")
                                .define("EnableHappinessTaxModifier", true);

                HAPPINESS_TAX_MULTIPLIER_MIN = BUILDER.comment(
                                "Minimum tax multiplier for unhappy colonies (0.1 - 1.0). Lower values mean unhappy colonies generate less tax.")
                                .defineInRange("HappinessTaxMultiplierMin", 0.5, 0.1, 1.0);

                HAPPINESS_TAX_MULTIPLIER_MAX = BUILDER.comment(
                                "Maximum tax multiplier for very happy colonies (1.0 - 2.0). Higher values mean happy colonies generate more tax.")
                                .defineInRange("HappinessTaxMultiplierMax", 1.5, 1.0, 2.0);

                // Colony Inactivity Configuration
                ENABLE_COLONY_INACTIVITY_TAX_PAUSE = BUILDER.comment(
                                "Enable colony inactivity tax pause system. When enabled, colonies that haven't been visited by owners/officers for the specified time will stop generating taxes.")
                                .define("EnableColonyInactivityTaxPause", true);

                COLONY_INACTIVITY_HOURS_THRESHOLD = BUILDER.comment(
                                "Hours of inactivity after which a colony will stop generating taxes. This uses MineColonies' built-in player interaction tracking.")
                                .defineInRange("ColonyInactivityHoursThreshold", 168, 1, 8760); // Default: 1 week (168
                                                                                                // hours), max: 1 year

                // Extortion System Configuration
                ENABLE_EXTORTION_SYSTEM = BUILDER.comment(
                                "Enable extortion system for wars. When enabled, defenders can choose to pay extortion instead of fighting when WarAcceptanceRequired is false.")
                                .define("EnableExtortionSystem", true);

                DEFAULT_EXTORTION_PERCENTAGE = BUILDER.comment(
                                "Default extortion percentage when not specified by attacker (0.0 - 1.0). For example: 0.15 = 15% of defender's balance.")
                                .defineInRange("DefaultExtortionPercentage", 0.15, 0.0, 1.0);

                EXTORTION_RESPONSE_TIME_MINUTES = BUILDER.comment(
                                "Time limit in minutes for defenders to respond to extortion offers before war automatically starts.")
                                .defineInRange("ExtortionResponseTimeMinutes", 5, 1, 30);

                EXTORTION_IMMUNITY_HOURS = BUILDER.comment(
                                "Hours of immunity from new extortion attempts after successfully paying extortion.")
                                .defineInRange("ExtortionImmunityHours", 24, 1, 168);

                // Citizen Militia System Configuration
                ENABLE_CITIZEN_MILITIA = BUILDER.comment(
                                "Enable citizen militia system during raids. When enabled, citizens will temporarily become guards to defend the colony. Set to false to use the old raid system.")
                                .define("EnableCitizenMilitia", true);

                MILITIA_CONVERSION_PERCENTAGE = BUILDER.comment(
                                "Percentage of eligible citizens to convert to militia guards during raids (0.0 - 1.0). For example: 0.3 = 30% of citizens become militia.")
                                .defineInRange("MilitiaConversionPercentage", 0.3, 0.0, 1.0);

                MILITIA_MIN_CITIZEN_LEVEL = BUILDER.comment(
                                "Minimum level required for a citizen to be eligible for militia conversion. Higher levels = more experienced citizens only.")
                                .defineInRange("MilitiaMinCitizenLevel", 3, 1, 99);

                MILITIA_GUARDS_SEEK_RAIDERS = BUILDER.comment(
                                "If true, militia guards and regular guards will actively seek out and engage raiders instead of just defending their posts.")
                                .define("MilitiaGuardsSeekRaiders", true);

                TAX_STEAL_PER_GUARD_KILLED = BUILDER.comment(
                                "If true, raiders steal tax based on guards killed. If false, uses the old time-based tax stealing system.")
                                .define("TaxStealPerGuardKilled", true);

                TAX_STEAL_PERCENTAGE_PER_GUARD = BUILDER.comment(
                                "Percentage of colony tax stolen per guard/militia killed during a raid (0.0 - 1.0). For example: 0.1 = 10% tax stolen per guard killed.")
                                .defineInRange("TaxStealPercentagePerGuard", 0.1, 0.0, 1.0);

                MAX_RAID_TAX_PERCENTAGE = BUILDER.comment(
                                "Maximum percentage of colony tax that can be stolen during a raid (0.0 - 1.0). This amount is distributed across all guards/militia. For example: 0.25 = 25% max tax stolen when all defenders are killed.")
                                .defineInRange("MaxRaidTaxPercentage", 0.25, 0.0, 1.0);

                APPLY_RESISTANCE_TO_CITIZENS = BUILDER.comment(
                                "If true, resistance effects during raids/wars will also be applied to all citizens, not just guards. This makes the entire colony more defensive.")
                                .define("ApplyResistanceToCitizens", false);

                BUILDER.pop();

                // ========== Easy Factions Integration ==========
                BUILDER.push("Easy Factions Integration");

                ENABLE_EASY_FACTIONS_INTEGRATION = BUILDER.comment(
                                "Enable integration with the Easy Factions mod (modid: easy_factions). When enabled, faction members are automatically granted MineColonies permissions on each other's colonies. Has no effect if Easy Factions is not installed.")
                                .define("EnableEasyFactionsIntegration", true);

                EASY_FACTIONS_MEMBER_RANK = BUILDER.comment(
                                "MineColonies rank assigned to regular faction members on each other's colonies. Valid values: friend, officer. Officer grants more interaction permissions but also allows tax claiming under default settings.")
                                .define("EasyFactionsMemberRank", "friend",
                                                o -> o instanceof String s && (s.equalsIgnoreCase("friend") || s.equalsIgnoreCase("officer")));

                EASY_FACTIONS_PROMOTE_OFFICERS = BUILDER.comment(
                                "If true, Easy Factions officers and the faction owner are promoted to MineColonies Officer rank on every faction member's colony (overrides EasyFactionsMemberRank for them). WARNING: MineColonies Officer rank grants tax-claim rights by default - enabling this lets faction officers drain other members' treasuries via /wnt claimtax. Default is false; opt in only for tightly-trusted factions.")
                                .define("EasyFactionsPromoteOfficers", false);

                EASY_FACTIONS_SYNC_INTERVAL_TICKS = BUILDER.comment(
                                "How often (in server ticks) to reconcile faction membership with colony permissions. 20 ticks = 1 second. Default 200 = every 10 seconds. Lower values are more responsive but slightly more expensive.")
                                .defineInRange("EasyFactionsSyncIntervalTicks", 200, 20, 12000);

                BUILDER.pop();

                // ========== Colony Auto-Abandon Settings ==========
                BUILDER.push("Colony Auto-Abandon");

                ENABLE_COLONY_ABANDONMENT_SYSTEM = BUILDER.comment(
                                "MASTER SWITCH for the entire automatic colony abandonment / owner-repair system.",
                                "When FALSE (default): the mod NEVER automatically rewrites MineColonies colony owner",
                                "or permission data. This is the safe default and prevents the rare colony-data",
                                "corruption that can occur when owner-repair runs while colonies are still loading.",
                                "When TRUE: the inactivity auto-abandon, null-owner repair, and abandoned-entry cleanup",
                                "passes run (each still has its own sub-toggle below).",
                                "NOTE: a one-time, removal-only repair of legacy '[AUTO_OWNER]' placeholder entries",
                                "always runs regardless of this switch, to heal worlds affected by older versions.")
                                .define("EnableColonyAbandonmentSystem", false);

                ENABLE_COLONY_AUTO_ABANDON = BUILDER.comment(
                                "Enable automatic colony abandonment when owners/officers haven't visited for the configured time. "
                                                +
                                                "When enabled, colonies will be automatically abandoned and can be claimed by other players.")
                                .define("EnableColonyAutoAbandon", true);

                COLONY_AUTO_ABANDON_DAYS = BUILDER
                                .comment("Days of inactivity after which a colony will be automatically abandoned. " +
                                                "This is measured by the last time any owner or officer visited the colony. Default: 14 days (2 weeks)")
                                .defineInRange("ColonyAutoAbandonDays", 14, 1, 365);

                NOTIFY_OWNERS_BEFORE_ABANDON = BUILDER.comment(
                                "If true, owners and officers will be notified before their colony is abandoned. " +
                                                "Requires warning days to be configured.")
                                .define("NotifyOwnersBeforeAbandon", true);

                ABANDON_WARNING_DAYS = BUILDER.comment("Days before abandonment to warn owners and officers. " +
                                "Warnings are sent when they log in during this period.")
                                .defineInRange("AbandonWarningDays", 3, 1, 30);

                ENABLE_LIST_ABANDONED_FOR_ALL = BUILDER.comment(
                                "Allow ALL players to use /wnt listabandoned command.\n" +
                                                "When FALSE (default): Only OPs (permission level 2+) can view abandoned colonies.\n"
                                                +
                                                "When TRUE: Any player can view the list of abandoned colonies available for claiming.")
                                .define("EnableListAbandonedForAll", false);

                RESET_TIMER_ON_OFFICER_LOGIN = BUILDER.comment(
                                "EXPERIMENTAL: Reset abandonment timer when officers/owners log into the server.\n" +
                                                "When FALSE (default): Timer only resets when officers PHYSICALLY VISIT the colony (chunk-based detection).\n"
                                                +
                                                "When TRUE: Timer resets for ALL colonies an officer manages just by logging in (easier but defeats the purpose).\n"
                                                +
                                                "\n" +
                                                "RECOMMENDED: Keep this FALSE to ensure officers actually visit their colonies to prevent abandonment.\n"
                                                +
                                                "Setting this to TRUE will prevent colonies from ever being abandoned if officers log in regularly.")
                                .define("ResetTimerOnOfficerLogin", false);

                BUILDER.pop();

                // ========== Abandoned Colony Claiming Settings ==========
                BUILDER.push("Abandoned Colony Claiming");

                ENABLE_ABANDONED_COLONY_CLAIMING = BUILDER
                                .comment("Enable claiming of abandoned colonies using the /wnt claimcolony command. " +
                                                "When enabled, players can claim abandoned colonies, triggering a raid where all citizens become hostile militia.")
                                .define("EnableAbandonedColonyClaiming", true);

                MIN_GUARDS_FOR_CLAIMING_RAID = BUILDER
                                .comment("Minimum number of guards required to claim an abandoned colony. " +
                                                "This ensures only established colonies can claim others.")
                                .defineInRange("MinGuardsForClaimingRaid", 3, 1, 50);

                CLAIMING_RAID_DURATION_MINUTES = BUILDER.comment(
                                "Duration in minutes for the claiming raid when taking over an abandoned colony. " +
                                                "During this time, all citizens will be hostile and attack the claiming player.")
                                .defineInRange("ClaimingRaidDurationMinutes", 5, 1, 60);

                CLAIMING_GRACE_PERIOD_HOURS = BUILDER
                                .comment("Grace period in hours before a player can claim another colony. " +
                                                "This prevents players from claiming multiple colonies in quick succession.")
                                .defineInRange("ClaimingGracePeriodHours", 24, 1, 168); // Default 24 hours, max 1 week

                SPAWN_MERCENARIES_IF_LOW_DEFENDERS = BUILDER.comment(
                                "If true, spawn mercenaries to defend the colony during claiming if there are fewer than 5 citizens/guards. "
                                                +
                                                "This ensures abandoned colonies still put up a fight when being claimed.")
                                .define("SpawnMercenariesIfLowDefenders", true);

                CLAIMING_BUILDING_REQUIREMENTS = BUILDER.comment(
                                "Building requirements to claim abandoned colonies. Format: 'building:level,building:level'. "
                                                +
                                                "Example: 'townhall:2,guardtower:1' means player needs townhall level 2+ and at least one guard tower to claim. "
                                                +
                                                "Leave empty to disable building requirements.")
                                .define("ClaimingBuildingRequirements", "townhall:2");

                BUILDER.pop();

                // ========== Raid Building Requirements ==========
                BUILDER.push("Raid Building Requirements");

                ENABLE_RAID_BUILDING_REQUIREMENTS = BUILDER
                                .comment("Enable building requirements for initiating raids. " +
                                                "When enabled, these requirements take PRIORITY over 'MinGuardsToRaid' setting. "
                                                +
                                                "Disable this to use the legacy guard count system instead.")
                                .define("EnableRaidBuildingRequirements", true);

                RAID_BUILDING_REQUIREMENTS = BUILDER.comment(
                                "Building requirements to initiate raids. Format: 'building:level:amount,building:level:amount'. "
                                                +
                                                "Example: 'townhall:1:1,guardtower:1:3' means player needs 1 townhall level 1+ and 3 guard towers level 1+ to raid. "
                                                +
                                                "NOTE: When this is enabled, it replaces the 'MinGuardsToRaid' requirement entirely. "
                                                +
                                                "Leave empty to disable building requirements.")
                                .define("RaidBuildingRequirements", "townhall:1:1,guardtower:1:3");

                BUILDER.pop();

                // ========== War Building Requirements ==========
                BUILDER.push("War Building Requirements");

                ENABLE_WAR_BUILDING_REQUIREMENTS = BUILDER.comment("Enable building requirements for declaring wars. " +
                                "When enabled, these requirements take PRIORITY over 'MinGuardsToWageWar' setting. " +
                                "Disable this to use the legacy guard count system instead.")
                                .define("EnableWarBuildingRequirements", true);

                WAR_BUILDING_REQUIREMENTS = BUILDER.comment(
                                "Building requirements to declare wars. Format: 'building:level:amount,building:level:amount'. "
                                                +
                                                "Example: 'townhall:2:1,guardtower:1:3,buildershut:1:1,house:1:1' means player needs 1 townhall level 2+, 3 guard towers level 1+, 1 builders hut level 1+, and 1 residential building level 1+ to declare war. "
                                                +
                                                "NOTE: When this is enabled, it replaces the 'MinGuardsToWageWar' requirement entirely. "
                                                +
                                                "Leave empty to disable building requirements.")
                                .define("WarBuildingRequirements",
                                                "townhall:2:1,guardtower:1:3,buildershut:1:1,house:1:1");

                BUILDER.pop();

                // ========== Recipe Disabling Settings ==========
                BUILDER.push("Recipe Disabling");

                DISABLE_HUT_RECIPES = BUILDER.comment(
                                "Disable all Minecolonies building hut recipes. When enabled, players must obtain building huts through SDMShop or Admin Shop instead of crafting them. "
                                                +
                                                "This affects all buildings that accumulate taxes or maintenance costs. Disabled by default.")
                                .define("DisableHutRecipes", false);

                BUILDER.pop();

                // ========== Database Settings ==========
                // Get these values from the Pterodactyl panel -> your server -> Databases tab.
                // Both the Minecraft server container and your website container connect to
                // the same MySQL/MariaDB server using these credentials.
                BUILDER.push("Database");

                DATABASE_ENABLED = BUILDER.comment(
                                "Enable MySQL/MariaDB database integration for war statistics. " +
                                "When enabled, war stats are written to the database in real time and can be " +
                                "queried by an external website. Requires a running MySQL/MariaDB server. " +
                                "On Pterodactyl: create a database under your server's 'Databases' tab and copy the credentials below. " +
                                "Default: false")
                                .define("Enabled", false);

                DATABASE_HOST = BUILDER.comment(
                                "MySQL/MariaDB server host. " +
                                "On Pterodactyl this is shown in the Databases tab (e.g. '127.0.0.1' or an internal hostname). " +
                                "Default: 127.0.0.1")
                                .define("Host", "127.0.0.1");

                DATABASE_PORT = BUILDER.comment(
                                "MySQL/MariaDB server port. Default: 3306")
                                .defineInRange("Port", 3306, 1, 65535);

                DATABASE_NAME = BUILDER.comment(
                                "Database name. On Pterodactyl this is generated automatically (e.g. 's1_warntax'). " +
                                "Default: warntax")
                                .define("Database", "warntax");

                DATABASE_USERNAME = BUILDER.comment(
                                "Database username. On Pterodactyl this is shown in the Databases tab. " +
                                "Default: warntax")
                                .define("Username", "warntax");

                DATABASE_PASSWORD = BUILDER.comment(
                                "Database password. On Pterodactyl this is shown in the Databases tab. " +
                                "Default: empty (set this)")
                                .define("Password", "");

                DATABASE_POOL_SIZE = BUILDER.comment(
                                "Number of database connections to keep open. " +
                                "2 is sufficient for a game server; increase only if you see connection wait errors. " +
                                "Default: 2")
                                .defineInRange("PoolSize", 2, 1, 10);

                DATABASE_SNAPSHOT_INTERVAL_SECONDS = BUILDER.comment(
                                "How often (in seconds) to snapshot colony state to the database. " +
                                "This keeps the website's colony status board current. " +
                                "Lower values = more current data but slightly more DB load. " +
                                "Default: 300 (5 minutes)")
                                .defineInRange("SnapshotIntervalSeconds", 300, 30, 3600);

                BUILDER.pop();

                // ========== Patchouli Book Settings ==========
                BUILDER.push("Patchouli Book");

                GIVE_PATCHOULI_BOOK_ON_JOIN = BUILDER.comment(
                                "Give players the War & Taxes Codex (Patchouli guide book) when they first join the server.\n"
                                                +
                                                "Requires Patchouli mod to be installed. Book is only given once per player.")
                                .define("GivePatchouliBookOnJoin", true);

                SHOW_ADMIN_PAGES_IN_BOOK = BUILDER
                                .comment("Show admin/configuration pages in the Patchouli guide book.\n" +
                                                "When disabled, the Configuration category with admin commands and settings is hidden from players.")
                                .define("ShowAdminPagesInBook", false);

                BUILDER.pop();

                // ============================================================
                // [WIP] TAX EXPANSION: Economy Settings (Feature Branch - Not Yet Merged)
                // ============================================================
                BUILDER.push("Tax Expansion - Economy [WIP]");

                // --- Tax Policies ---
                BUILDER.push("Tax Policies");
                ENABLE_TAX_POLICIES = BUILDER.comment(
                                "Enable tax policy system. Colonies can choose policies that affect tax generation and citizen happiness.")
                                .define("EnableTaxPolicies", true);

                TAX_POLICY_LOW_REVENUE_MODIFIER = BUILDER.comment(
                                "Revenue modifier for LOW tax policy. Negative = less tax. Example: -0.25 = 25% less revenue.")
                                .defineInRange("LowPolicyRevenueModifier", -0.25, -1.0, 0.0);

                TAX_POLICY_LOW_HAPPINESS_MODIFIER = BUILDER.comment(
                                "Happiness modifier for LOW tax policy. Additive to colony avg happiness (0-10 scale). Example: 0.20 = +0.2 happiness.")
                                .defineInRange("LowPolicyHappinessModifier", 0.20, 0.0, 1.0);

                TAX_POLICY_HIGH_REVENUE_MODIFIER = BUILDER.comment(
                                "Revenue modifier for HIGH tax policy. Positive = more tax. Example: 0.25 = 25% more revenue.")
                                .defineInRange("HighPolicyRevenueModifier", 0.25, 0.0, 1.0);

                TAX_POLICY_HIGH_HAPPINESS_MODIFIER = BUILDER.comment(
                                "Happiness modifier for HIGH tax policy. Additive to colony avg happiness (0-10 scale). Example: -0.15 = -0.15 happiness.")
                                .defineInRange("HighPolicyHappinessModifier", -0.15, -1.0, 0.0);

                TAX_POLICY_WAR_REVENUE_MODIFIER = BUILDER.comment(
                                "Revenue modifier for WAR ECONOMY policy. High boost during wartime. Example: 0.50 = 50% more revenue.")
                                .defineInRange("WarPolicyRevenueModifier", 0.50, 0.0, 2.0);

                TAX_POLICY_WAR_HAPPINESS_MODIFIER = BUILDER.comment(
                                "Happiness modifier for WAR ECONOMY policy. Additive to colony avg happiness (0-10 scale). Example: -0.25 = -0.25 happiness.")
                                .defineInRange("WarPolicyHappinessModifier", -0.25, -1.0, 0.0);
                BUILDER.pop();

                // --- Tax Reports ---
                BUILDER.push("Tax Reports");
                ENABLE_TAX_REPORTS = BUILDER.comment(
                                "Enable tax report generation as written books. Use /wnt taxreport to generate.")
                                .define("EnableTaxReports", true);

                TAX_REPORT_AUTO_DELETE_HOURS = BUILDER.comment(
                                "Hours before tax report books auto-delete from inventory. Set to 0 to never auto-delete.")
                                .defineInRange("TaxReportAutoDeleteHours", 0, 0, 720);

                TAX_REPORT_MAX_HISTORY_DAYS = BUILDER.comment(
                                "Maximum days of tax history to track for reports.")
                                .defineInRange("TaxReportMaxHistoryDays", 30, 1, 365);
                BUILDER.pop();

                // --- Leaderboards ---
                BUILDER.push("Leaderboards");
                ENABLE_LEADERBOARDS = BUILDER.comment(
                                "Enable server-wide leaderboards for tax earnings, wars won, etc.")
                                .define("EnableLeaderboards", true);

                LEADERBOARD_UPDATE_INTERVAL_MINUTES = BUILDER.comment(
                                "How often leaderboards are recalculated (in minutes).")
                                .defineInRange("LeaderboardUpdateIntervalMinutes", 60, 5, 1440);

                LEADERBOARD_TOP_COUNT = BUILDER.comment(
                                "Number of entries to show in leaderboard commands.")
                                .defineInRange("LeaderboardTopCount", 10, 5, 50);
                BUILDER.pop();

                BUILDER.pop(); // End Tax Expansion - Economy

                // ============================================================
                // [WIP] TAX EXPANSION: Faction Settings (Feature Branch - Not Yet Merged)
                // ============================================================
                BUILDER.push("Tax Expansion - Factions [WIP]");

                ENABLE_FACTION_SYSTEM = BUILDER.comment(
                                "Enable faction system. Colonies can form factions with shared tax pools and trade routes.")
                                .define("EnableFactionSystem", true);

                MAX_FACTION_MEMBERS = BUILDER.comment(
                                "Maximum number of colonies that can join a single faction.")
                                .defineInRange("MaxFactionMembers", 10, 2, 50);

                FACTION_CREATION_COST = BUILDER.comment(
                                "Tax cost to create a new faction.")
                                .defineInRange("FactionCreationCost", 5000, 0, 100000);

                FACTION_ALLIANCE_LIMIT = BUILDER.comment(
                                "Maximum number of allied factions a faction can have.")
                                .defineInRange("FactionAllianceLimit", 3, 0, 20);

                // --- Shared Tax Pool ---
                BUILDER.push("Shared Tax Pool");
                ENABLE_SHARED_TAX_POOL = BUILDER.comment(
                                "Enable shared tax pool for factions. Member colonies contribute a percentage to the pool.")
                                .define("EnableSharedTaxPool", true);

                DEFAULT_POOL_CONTRIBUTION_PERCENT = BUILDER.comment(
                                "Default percentage of tax income that faction members contribute to the shared pool.")
                                .defineInRange("DefaultPoolContributionPercent", 10, 0, 100);

                MAX_POOL_BALANCE = BUILDER.comment(
                                "Maximum balance the shared tax pool can hold.")
                                .defineInRange("MaxPoolBalance", 100000, 1000, Integer.MAX_VALUE);

                POOL_HISTORY_RETENTION_DAYS = BUILDER.comment(
                                "Days to retain pool transaction history for reporting.")
                                .defineInRange("PoolHistoryRetentionDays", 30, 1, 365);
                BUILDER.pop();

                // --- Trade Routes ---
                BUILDER.push("Trade Routes");
                ENABLE_TRADE_ROUTES = BUILDER.comment(
                                "Enable trade routes between allied colonies. Trade routes generate passive income based on distance.")
                                .define("EnableTradeRoutes", false);

                TRADE_ROUTE_INCOME_PER_CHUNK = BUILDER.comment(
                                "Tax income generated per chunk of distance on a trade route (per tax cycle). " +
                                                "Kept low to make trade routes a modest bonus, not a primary income source.")
                                .defineInRange("TradeRouteIncomePerChunk", 1, 1, 100);

                TRADE_ROUTE_MAX_DISTANCE_CHUNKS = BUILDER.comment(
                                "Maximum distance in chunks for a trade route. Capped to prevent infinite money. " +
                                                "At 100 chunks × 1/chunk = max 100 income/route.")
                                .defineInRange("TradeRouteMaxDistanceChunks", 100, 10, 10000);

                TRADE_ROUTE_MAINTENANCE_COST = BUILDER.comment(
                                "Maintenance cost per trade route per tax cycle.")
                                .defineInRange("TradeRouteMaintenanceCost", 25, 0, 10000);

                MAX_TRADE_ROUTES_PER_COLONY = BUILDER.comment(
                                "Maximum number of trade routes a colony can have active.")
                                .defineInRange("MaxTradeRoutesPerColony", 2, 1, 20);
                BUILDER.pop();

                BUILDER.pop(); // End Tax Expansion - Factions

                // ============================================================
                // [WIP] TAX EXPANSION: Espionage Settings (Feature Branch - Not Yet Merged)
                // ============================================================
                BUILDER.push("Tax Expansion - Espionage [WIP]");

                ENABLE_SPY_SYSTEM = BUILDER.comment(
                                "Enable the spy/sabotage system. Colonies can spend tax to perform espionage on rivals.")
                                .define("EnableSpySystem", true);

                SPY_COOLDOWN_MINUTES = BUILDER.comment(
                                "Cooldown in minutes between spy actions against the same colony.")
                                .defineInRange("SpyCooldownMinutes", 60, 1, 1440);

                SPY_DETECTION_BASE_CHANCE = BUILDER.comment(
                                "Base chance (0.0-1.0) of being detected when performing spy actions. " +
                                                "Each action adds its own detection modifier.")
                                .defineInRange("SpyDetectionBaseChance", 0.05, 0.0, 1.0);

                SPY_MAX_ACTIVE_PER_PLAYER = BUILDER.comment("Maximum number of active spy missions per player.")
                                .defineInRange("MaxActiveSpiesPerPlayer", 3, 1, 10);
                SPY_SCOUT_MAX_DURATION_HOURS = BUILDER
                                .comment("Maximum duration in hours for a SCOUT mission before spy auto-recalls.")
                                .defineInRange("ScoutMaxDurationHours", 24, 1, 168);

                // --- Spy Action Costs ---
                BUILDER.push("Action Costs");
                SPY_SCOUT_COST = BUILDER.comment(
                                "Tax cost for SCOUT action (reveals enemy colony's current tax balance).")
                                .defineInRange("ScoutCost", 100, 0, 100000);

                SPY_SABOTAGE_COST = BUILDER.comment(
                                "Tax cost for SABOTAGE action (reduces target's next tax cycle).")
                                .defineInRange("SabotageCost", 1500, 0, 100000);

                SPY_BRIBE_GUARDS_COST = BUILDER.comment(
                                "Tax cost for BRIBE GUARDS action (disables guards during next raid). " +
                                                "High cost makes this a gamble given ~50% detection chance.")
                                .defineInRange("BribeGuardsCost", 2500, 0, 100000);

                SPY_STEAL_SECRETS_COST = BUILDER.comment(
                                "Tax cost for STEAL SECRETS action (copy building synergy bonuses temporarily).")
                                .defineInRange("StealSecretsCost", 1000, 0, 100000);
                BUILDER.pop();

                // --- Spy Action Effects ---
                BUILDER.push("Action Effects");
                SPY_SABOTAGE_TAX_REDUCTION_PERCENT = BUILDER.comment(
                                "Percentage reduction to target's tax for the next cycle when sabotaged (0.0-1.0). " +
                                                "Example: 0.25 = 25% less tax.")
                                .defineInRange("SabotageTaxReductionPercent", 0.25, 0.0, 1.0);

                SPY_BRIBE_GUARDS_DISABLED_COUNT = BUILDER.comment(
                                "Number of guards disabled when BRIBE GUARDS action succeeds.")
                                .defineInRange("BribeGuardsDisabledCount", 2, 1, 20);

                SPY_STEAL_SECRETS_DURATION_HOURS = BUILDER.comment(
                                "Duration in hours that stolen building synergy bonuses last.")
                                .defineInRange("StealSecretsDurationHours", 24, 1, 168);

                SPY_SABOTAGE_MIN_FIELD_MINUTES = BUILDER.comment(
                                "Minutes a SABOTAGE spy must remain active at the target colony before the " +
                                "sabotage is considered planted and the mission auto-completes. " +
                                "Spies detected before this threshold apply no effect on escape.")
                                .defineInRange("SabotageMinFieldMinutes", 30, 1, 240);
                BUILDER.pop();

                // --- Spy Action Detection Chances ---
                BUILDER.push("Detection Chances");
                SPY_SCOUT_DETECTION_CHANCE = BUILDER.comment(
                                "Additional detection chance for SCOUT action (0.0-1.0). Low risk action.")
                                .defineInRange("ScoutDetectionChance", 0.05, 0.0, 1.0);

                SPY_SABOTAGE_DETECTION_CHANCE = BUILDER.comment(
                                "Additional detection chance for SABOTAGE action (0.0-1.0). Medium risk.")
                                .defineInRange("SabotageDetectionChance", 0.25, 0.0, 1.0);

                SPY_BRIBE_GUARDS_DETECTION_CHANCE = BUILDER.comment(
                                "Additional detection chance for BRIBE GUARDS action (0.0-1.0). High risk - combined with base chance ~50% detection.")
                                .defineInRange("BribeGuardsDetectionChance", 0.45, 0.0, 1.0);

                SPY_STEAL_SECRETS_DETECTION_CHANCE = BUILDER.comment(
                                "Additional detection chance for STEAL SECRETS action (0.0-1.0). Medium-high risk.")
                                .defineInRange("StealSecretsDetectionChance", 0.30, 0.0, 1.0);
                BUILDER.pop();

                // --- Progressive Intel Thresholds ---
                BUILDER.push("Progressive Intel");
                INTEL_EARLY_THRESHOLD_MINUTES = BUILDER.comment(
                                "Minutes a spy must be active before Tier 1 intel is gathered " +
                                                "(colony name, citizen count, building count, war status).")
                                .defineInRange("IntelEarlyThresholdMinutes", 2, 0, 60);

                INTEL_MID_THRESHOLD_MINUTES = BUILDER.comment(
                                "Minutes a spy must be active before Tier 2 intel is gathered " +
                                                "(guard count, happiness, tax balance).")
                                .defineInRange("IntelMidThresholdMinutes", 10, 1, 120);

                INTEL_LATE_THRESHOLD_MINUTES = BUILDER.comment(
                                "Minutes a spy must be active before Tier 3 intel is gathered " +
                                                "(owner SDM balance, officer balances, colony member list).")
                                .defineInRange("IntelLateThresholdMinutes", 30, 5, 240);
                BUILDER.pop();

                // --- Flee Behavior ---
                BUILDER.push("Flee Behavior");
                FLEE_SPEED_MULTIPLIER = BUILDER.comment(
                                "Speed multiplier applied to a spy when fleeing detection (1.0 = normal speed).")
                                .defineInRange("FleeSpeedMultiplier", 1.5, 1.0, 3.0);

                FLEE_ESCAPE_DISTANCE = BUILDER.comment(
                                "How many blocks past the colony border a spy must reach to escape.")
                                .defineInRange("FleeEscapeDistance", 5, 1, 50);

                FLEE_MAX_SECONDS = BUILDER.comment(
                                "Maximum seconds a spy has to flee before being caught.")
                                .defineInRange("FleeMaxSeconds", 30, 10, 120);
                BUILDER.pop();

                // --- Spy Travel Phase ---
                BUILDER.push("Travel Phase");
                SPY_TRAVEL_BLOCKS_PER_MINUTE = BUILDER.comment(
                                "Spy travel speed in blocks per minute. Determines how long it takes to reach the target colony.")
                                .defineInRange("SpyTravelBlocksPerMinute", 200, 50, 2000);

                SPY_TRAVEL_MIN_MINUTES = BUILDER.comment(
                                "Minimum travel time in minutes, regardless of distance.")
                                .defineInRange("SpyTravelMinMinutes", 1, 0, 30);

                SPY_TRAVEL_MAX_MINUTES = BUILDER.comment(
                                "Maximum travel time cap in minutes, regardless of distance.")
                                .defineInRange("SpyTravelMaxMinutes", 15, 1, 60);
                BUILDER.pop();

                // --- Spy Map ---
                BUILDER.push("Spy Map");
                SPY_MAP_ENABLED = BUILDER.comment(
                                "When true, a pre-filled map of the target colony area is given to the player " +
                                "whenever their spy returns successfully (completed, escaped, or recalled).")
                                .define("SpyMapEnabled", true);
                SPY_MAP_SCALE = BUILDER.comment(
                                "Scale of the returned spy map. 0 = 128 blocks, 1 = 256 blocks, " +
                                "2 = 512 blocks, 3 = 1024 blocks, 4 = 2048 blocks. " +
                                "Higher values cover more area at the cost of per-block detail.")
                                .defineInRange("SpyMapScale", 1, 0, 4);
                BUILDER.pop();

                BUILDER.pop(); // End Tax Expansion - Espionage

                // ============================================================
                // Colony Upgrades
                // ============================================================
                BUILDER.comment("Colony Upgrades — invest Treasury funds to permanently improve colony capabilities").push("ColonyUpgrades");
                ENABLE_COLONY_UPGRADES = BUILDER.comment("Enable the colony upgrade investment system").define("EnableColonyUpgrades", true);
                UPGRADE_MAX_LEVEL = BUILDER.comment("Maximum level for each upgrade type").defineInRange("UpgradeMaxLevel", 5, 1, 20);
                UPGRADE_MILITIA_COST_BASE = BUILDER.comment("Base Treasury cost for first militia upgrade level").defineInRange("UpgradeMilitiaCostBase", 500, 1, 100000);
                UPGRADE_SPY_CAPACITY_COST_BASE = BUILDER.comment("Base Treasury cost for first spy capacity upgrade level").defineInRange("UpgradeSpyCapacityCostBase", 300, 1, 100000);
                UPGRADE_SPY_SPEED_COST_BASE = BUILDER.comment("Base Treasury cost for first spy speed upgrade level").defineInRange("UpgradeSpySpeedCostBase", 200, 1, 100000);
                UPGRADE_SPY_EVASION_COST_BASE = BUILDER.comment("Base Treasury cost for first spy evasion upgrade level").defineInRange("UpgradeSpyEvasionCostBase", 400, 1, 100000);
                UPGRADE_RAID_FORCE_COST_BASE = BUILDER.comment("Base Treasury cost for first raid force upgrade level").defineInRange("UpgradeRaidForceCostBase", 600, 1, 100000);
                UPGRADE_DEFENSE_COST_BASE = BUILDER.comment("Base Treasury cost for first defense upgrade level").defineInRange("UpgradeDefenseCostBase", 350, 1, 100000);
                UPGRADE_COST_SCALING_FACTOR = BUILDER.comment("Cost multiplier per upgrade level (e.g. 2.0 means each level costs double the previous)").defineInRange("UpgradeCostScalingFactor", 2.0, 1.0, 10.0);
                UPGRADE_MILITIA_MULTIPLIER_PER_LEVEL = BUILDER.comment("Militia size multiplier added per upgrade level (0.1 = +10% per level)").defineInRange("UpgradeMilitiaMultiplierPerLevel", 0.10, 0.01, 1.0);
                UPGRADE_SPY_CAPACITY_PER_LEVEL = BUILDER.comment("Additional concurrent spies per upgrade level").defineInRange("UpgradeSpyCapacityPerLevel", 1, 1, 10);
                UPGRADE_SPY_SPEED_MULTIPLIER_PER_LEVEL = BUILDER.comment("Spy travel speed multiplier per upgrade level (0.2 = +20% faster per level)").defineInRange("UpgradeSpySpeedMultiplierPerLevel", 0.20, 0.01, 1.0);
                UPGRADE_DETECTION_REDUCTION_PER_LEVEL = BUILDER.comment("Detection chance reduction per evasion upgrade level (0.05 = -5% per level)").defineInRange("UpgradeDetectionReductionPerLevel", 0.05, 0.01, 0.5);
                UPGRADE_DEFENSE_BONUS_PER_LEVEL = BUILDER.comment("Additional guard resistance level per defense upgrade level").defineInRange("UpgradeDefenseBonusPerLevel", 1, 1, 5);
                UPGRADE_TREASURY_CAP_COST_BASE = BUILDER.comment("Base treasury cost to purchase one level of Treasury Cap investment.")
                                .defineInRange("UpgradeTreasuryCapCostBase", 3000, 1, Integer.MAX_VALUE);
                UPGRADE_TAX_EFFICIENCY_COST_BASE = BUILDER.comment("Base treasury cost to purchase one level of Tax Efficiency investment.")
                                .defineInRange("UpgradeTaxEfficiencyCostBase", 4000, 1, Integer.MAX_VALUE);
                UPGRADE_FORTIFICATION_COST_BASE = BUILDER.comment("Base treasury cost to purchase one level of Fortification investment.")
                                .defineInRange("UpgradeFortificationCostBase", 3500, 1, Integer.MAX_VALUE);
                UPGRADE_COUNTER_INTEL_COST_BASE = BUILDER.comment("Base treasury cost to purchase one level of Counter Intelligence investment.")
                                .defineInRange("UpgradeCounterIntelCostBase", 5000, 1, Integer.MAX_VALUE);
                UPGRADE_TREASURY_CAP_FLAT_PER_LEVEL = BUILDER.comment("Flat treasury capacity increase per Treasury Cap level.")
                                .defineInRange("UpgradeTreasuryCapFlatPerLevel", 2000, 1, Integer.MAX_VALUE);
                UPGRADE_TAX_EFFICIENCY_PERCENT_PER_LEVEL = BUILDER.comment("Tax generation bonus per Tax Efficiency level (fraction, e.g. 0.05 = +5%).")
                                .defineInRange("UpgradeTaxEfficiencyPercentPerLevel", 0.05, 0.0, 1.0);
                UPGRADE_FORTIFICATION_DAMAGE_REDUCTION_PER_LEVEL = BUILDER.comment("Fraction of incoming siege/raid damage reduced per Fortification level (e.g. 0.05 = 5%). Capped at 75%.")
                                .defineInRange("UpgradeFortificationDamageReductionPerLevel", 0.05, 0.0, 0.5);
                UPGRADE_COUNTER_INTEL_DETECT_CHANCE_PER_LEVEL = BUILDER.comment("Chance per Counter Intelligence level to auto-detect an incoming spy on arrival (e.g. 0.10 = 10%). Capped at 90%.")
                                .defineInRange("UpgradeCounterIntelDetectChancePerLevel", 0.10, 0.0, 1.0);
                UPGRADE_RAID_FORCE_MULTIPLIER_PER_LEVEL = BUILDER.comment("Multiplier bonus per Raid Force level applied to attacker effectiveness (e.g. 0.10 = +10% per level).")
                                .defineInRange("UpgradeRaidForceMultiplierPerLevel", 0.10, 0.0, 1.0);
                BUILDER.pop();

                // ============================================================
                // [WIP] TAX EXPANSION: War Mechanics (Feature Branch - Not Yet Merged)
                // ============================================================
                BUILDER.push("Tax Expansion - War Mechanics [WIP]");

                // --- Treasury ---
                BUILDER.push("Treasury");
                ENABLE_TREASURY = BUILDER.comment(
                                "Enable treasury system. Colonies must have funds in treasury to declare war. " +
                                                "Treasury drains during active war.")
                                .define("EnableTreasury", true);

                TREASURY_MIN_PERCENT_OF_TARGET = BUILDER.comment(
                                "Minimum treasury balance required as percentage of target colony's tax balance (0.0-1.0). "
                                                +
                                                "Example: 0.25 = need 25% of target's balance to declare war.")
                                .defineInRange("TreasuryMinPercentOfTarget", 0.25, 0.0, 2.0);

                TREASURY_MIN_PERCENT_OF_OWN_TAX = BUILDER.comment(
                                "Minimum treasury balance also required as percentage of the attacker's OWN tax balance (0.0-1.0). "
                                                +
                                                "The higher of this value and TreasuryMinPercentOfTarget is used. "
                                                +
                                                "Prevents wealthy colonies from cheaply bullying poor ones. "
                                                +
                                                "Example: 0.10 = also need 10% of your own tax balance in the treasury.")
                                .defineInRange("TreasuryMinPercentOfOwnTax", 0.10, 0.0, 2.0);

                TREASURY_DRAIN_PER_MINUTE = BUILDER.comment(
                                "Flat amount drained from treasury per minute during active war. "
                                                + "Full 250min at default (10/min on 25k treasury, ~4h to deplete). "
                                                +
                                                "Only used when TreasuryDrainUsePercent is false.")
                                .defineInRange("TreasuryDrainPerMinute", 10, 0, 10000);

                TREASURY_DRAIN_USE_PERCENT = BUILDER.comment(
                                "If true, treasury drains by a percentage of max capacity per minute (proportional burn). "
                                                +
                                                "If false, uses the flat TreasuryDrainPerMinute value.")
                                .define("TreasuryDrainUsePercent", true);

                TREASURY_DRAIN_PERCENT = BUILDER.comment(
                                "Percentage of treasury max capacity drained per minute during active war (0.0-1.0). "
                                                +
                                                "Only used when TreasuryDrainUsePercent is true. "
                                                +
                                                "Example: 0.004 = 0.4% of max capacity per minute (100/min on a 25k treasury, ~4h to deplete).")
                                .defineInRange("TreasuryDrainPercent", 0.004, 0.0, 1.0);

                TREASURY_MAX_CAPACITY = BUILDER.comment(
                                "Maximum capacity of the treasury. Lowered to prevent excessive stockpiling.")
                                .defineInRange("TreasuryMaxCapacity", 25000, 1000, Integer.MAX_VALUE);

                TREASURY_AUTO_SURRENDER_ENABLED = BUILDER.comment(
                                "If true, war automatically ends in surrender when treasury is depleted.")
                                .define("TreasuryAutoSurrenderEnabled", true);

                TREASURY_AUTO_DEPOSIT_PERCENT = BUILDER.comment(
                                "Percentage of tax revenue to automatically deposit into treasury each tax cycle (0.0-1.0). "
                                                + "Kept low (5%) to reward manual deposits over passive accumulation. "
                                                +
                                                "Example: 0.05 = 5% of each tax collection auto-deposited.")
                                .defineInRange("TreasuryAutoDepositPercent", 0.05, 0.0, 1.0);

                WAR_DEFENDER_DRAIN_REDUCTION = BUILDER.comment(
                                "Fraction by which the defending colony's treasury drain is reduced during war (0.0-1.0). "
                                                +
                                                "Example: 0.5 = defender drains at half the normal rate (home field advantage). "
                                                +
                                                "Set to 0.0 to disable and drain both sides equally.")
                                .defineInRange("WarDefenderDrainReduction", 0.5, 0.0, 1.0);

                // --- Raid Treasury ---
                RAID_TREASURY_ENABLED = BUILDER.comment(
                                "If true, raids require treasury funds. Cost is a one-time payment based on the target's tax generation.")
                                .define("RaidTreasuryEnabled", true);

                RAID_TREASURY_COST_PERCENT = BUILDER.comment(
                                "Raid cost as a percentage of the target colony's tax per interval (0.0-1.0). " +
                                                "Example: 0.10 = raiding costs 10% of what the target earns per tax cycle.")
                                .defineInRange("RaidTreasuryCostPercent", 0.10, 0.0, 2.0);

                // --- Raid Penalties ---
                RAID_PENALTY_TAX_REDUCTION_PERCENT = BUILDER.comment(
                                "Percentage reduction in tax generation for raided colony (0.0-1.0). " +
                                                "Example: 0.25 = 25% less tax after being raided.")
                                .defineInRange("RaidPenaltyTaxReductionPercent", 0.25, 0.0, 1.0);

                RAID_PENALTY_DURATION_HOURS = BUILDER.comment(
                                "Duration in hours that the raid tax penalty lasts.")
                                .defineInRange("RaidPenaltyDurationHours", 24, 1, 168);

                RAID_REPAIR_COST_PERCENT = BUILDER.comment(
                                "Cost to repair colony and remove raid penalty early, as percentage of colony's total tax earnings (0.0-1.0). "
                                                +
                                                "Set low (e.g. 0.10) so victims are not triple-punished: theft + penalty + repair. "
                                                +
                                                "Example: 0.10 = repairing costs 10% of colony's tax balance.")
                                .defineInRange("RaidRepairCostPercent", 0.10, 0.0, 2.0);
                BUILDER.pop();

                // --- War Exhaustion ---
                BUILDER.push("War Exhaustion");
                ENABLE_WAR_EXHAUSTION = BUILDER.comment(
                                "Enable war exhaustion. Colonies generate less tax during active wars and need recovery time after.")
                                .define("EnableWarExhaustion", true);

                WAR_TAX_REDUCTION_PERCENT = BUILDER.comment(
                                "Percentage reduction in tax generation during active war (0.0-1.0). " +
                                                "Example: 0.30 = 30% less tax during war.")
                                .defineInRange("WarTaxReductionPercent", 0.30, 0.0, 1.0);

                POST_WAR_RECOVERY_HOURS = BUILDER.comment(
                                "Hours after war ends for colony tax generation to fully recover.")
                                .defineInRange("PostWarRecoveryHours", 48, 0, 720);
                BUILDER.pop();

                // --- War Reparations ---
                BUILDER.push("War Reparations");
                ENABLE_WAR_REPARATIONS = BUILDER.comment(
                                "Enable war reparations debuff for colonies that lose wars frequently.")
                                .define("EnableWarReparations", true);

                REPARATIONS_TAX_PENALTY_PERCENT = BUILDER.comment(
                                "Percentage penalty to tax generation while under reparations (0.0-1.0). " +
                                                "Example: 0.20 = 20% less tax.")
                                .defineInRange("ReparationsTaxPenaltyPercent", 0.20, 0.0, 1.0);

                REPARATIONS_DURATION_HOURS = BUILDER.comment(
                                "Duration in hours that war reparations debuff lasts.")
                                .defineInRange("ReparationsDurationHours", 72, 1, 720);

                REPARATIONS_TRIGGER_LOSSES_COUNT = BUILDER.comment(
                                "Number of wars lost within 7 days to trigger reparations debuff.")
                                .defineInRange("ReparationsTriggerLossesCount", 3, 1, 20);
                BUILDER.pop();

                // --- War Weariness Protection ---
                BUILDER.push("War Weariness");
                ENABLE_WAR_WEARINESS = BUILDER.comment(
                                "Enable war weariness protection. Colonies with very low tax balance that lost a war "
                                                +
                                                "receive temporary war immunity to allow economic recovery (comeback mechanic).")
                                .define("EnableWarWeariness", true);

                WAR_WEARINESS_THRESHOLD = BUILDER.comment(
                                "Tax balance threshold below which a colony qualifies for war weariness immunity. "
                                                +
                                                "If a colony's tax balance is at or below this value after losing a war, "
                                                +
                                                "they receive temporary protection from further wars.")
                                .defineInRange("WarWearinessThreshold", 500, 0, Integer.MAX_VALUE);

                WAR_WEARINESS_IMMUNITY_HOURS = BUILDER.comment(
                                "Hours of war declaration immunity granted to colonies below the weariness threshold after losing a war.")
                                .defineInRange("WarWearinessImmunityHours", 24, 1, 168);
                BUILDER.pop();

                // --- Vassalization Economy ---
                BUILDER.push("Vassalization Economy");
                VASSALIZATION_REPLACES_REPARATIONS = BUILDER.comment(
                                "If true, when a colony is vassalized after a war loss, the ongoing tribute replaces "
                                                +
                                                "the one-time colony tax reparations payment. "
                                                +
                                                "This prevents stacking of reparations + tribute for the losing colony.")
                                .define("VassalizationReplacesReparations", true);
                BUILDER.pop();

                // --- Ransom System ---
                BUILDER.push("Ransom System");
                ENABLE_RANSOM_SYSTEM = BUILDER.comment(
                                "Enable ransom system. When colony owner dies during raid, attacker can demand ransom to end raid.")
                                .define("EnableRansomSystem", true);

                RANSOM_DEFAULT_PERCENT = BUILDER.comment(
                                "Default ransom amount as percentage of victim's tax balance (0.0-1.0). " +
                                                "Example: 0.15 = 15% of their balance.")
                                .defineInRange("RansomDefaultPercent", 0.15, 0.0, 1.0);

                RANSOM_MIN_AMOUNT = BUILDER.comment(
                                "Minimum ransom amount (floors the percentage calculation).")
                                .defineInRange("RansomMinAmount", 100, 0, 100000);

                RANSOM_MAX_AMOUNT = BUILDER.comment(
                                "Maximum ransom amount (caps the percentage calculation).")
                                .defineInRange("RansomMaxAmount", 10000, 100, Integer.MAX_VALUE);

                RANSOM_TIMEOUT_SECONDS = BUILDER.comment(
                                "Seconds victim has to respond to ransom offer before it expires.")
                                .defineInRange("RansomTimeoutSeconds", 60, 10, 300);

                RANSOM_COOLDOWN_MINUTES = BUILDER.comment(
                                "Cooldown in minutes before same attacker can offer ransom to same victim.")
                                .defineInRange("RansomCooldownMinutes", 30, 1, 1440);

                RANSOM_IMMUNITY_AFTER_PAYMENT_HOURS = BUILDER.comment(
                                "Hours of immunity from raids after paying a ransom. " +
                                                "Reduced from 24h to prevent ransom as a cheap 'invulnerability shield'.")
                                .defineInRange("RansomImmunityAfterPaymentHours", 4, 0, 168);
                BUILDER.pop();

                BUILDER.pop(); // End Tax Expansion - War Mechanics

                // ============================================================
                // [WIP] TAX EXPANSION: Money Sinks / Investments (Feature Branch - Not Yet
                // Merged)
                // ============================================================
                BUILDER.push("Tax Expansion - Money Sinks [WIP]");

                ENABLE_INVESTMENTS = BUILDER.comment(
                                "Enable investment system. Colonies can spend tax for permanent or temporary bonuses.")
                                .define("EnableInvestments", true);

                INVESTMENT_DIMINISHING_RETURNS_FACTOR = BUILDER.comment(
                                "Factor applied to each subsequent investment purchase (0.0-1.0). " +
                                                "Harsher diminishing returns (0.80) prevent infinite scaling. " +
                                                "Example: 0.80 = each purchase is 80% as effective as the previous.")
                                .defineInRange("InvestmentDiminishingReturnsFactor", 0.80, 0.1, 1.0);

                // --- Infrastructure Investment ---
                BUILDER.push("Infrastructure Investment");
                INVESTMENT_INFRASTRUCTURE_COST = BUILDER.comment(
                                "Tax cost for infrastructure investment (permanent tax bonus). " +
                                                "At 1500 cost with 10k max tax, represents 15% of max savings per stack.")
                                .defineInRange("InfrastructureCost", 1500, 100, 1000000);

                INVESTMENT_INFRASTRUCTURE_BONUS = BUILDER.comment(
                                "Permanent tax generation bonus per infrastructure investment (0.0-1.0). " +
                                                "Example: 0.05 = 5% more tax generation.")
                                .defineInRange("InfrastructureBonus", 0.05, 0.01, 0.5);

                INVESTMENT_INFRASTRUCTURE_MAX_STACKS = BUILDER.comment(
                                "Maximum number of infrastructure investments a colony can have. " +
                                                "Reduced to 5 to limit infinite scaling (+16.8% total with diminishing returns).")
                                .defineInRange("InfrastructureMaxStacks", 5, 1, 100);
                BUILDER.pop();

                // --- Guard Training Investment ---
                BUILDER.push("Guard Training Investment");
                INVESTMENT_GUARD_TRAINING_COST = BUILDER.comment(
                                "Tax cost for guard training investment (temporary damage bonus).")
                                .defineInRange("GuardTrainingCost", 500, 50, 100000);

                INVESTMENT_GUARD_TRAINING_DAMAGE_BONUS = BUILDER.comment(
                                "Damage bonus for guards after training (0.0-1.0). Example: 0.10 = 10% more damage.")
                                .defineInRange("GuardTrainingDamageBonus", 0.10, 0.01, 1.0);

                INVESTMENT_GUARD_TRAINING_DURATION_HOURS = BUILDER.comment(
                                "Duration in hours that guard training bonus lasts.")
                                .defineInRange("GuardTrainingDurationHours", 24, 1, 168);
                BUILDER.pop();

                // --- Festival Investment ---
                BUILDER.push("Festival Investment");
                INVESTMENT_FESTIVAL_COST = BUILDER.comment(
                                "Tax cost to host a festival (temporary happiness bonus).")
                                .defineInRange("FestivalCost", 2000, 100, 1000000);

                INVESTMENT_FESTIVAL_HAPPINESS_BONUS = BUILDER.comment(
                                "Happiness bonus during festival. Additive to colony avg happiness (0-10 scale). Example: 0.50 = +0.50 happiness.")
                                .defineInRange("FestivalHappinessBonus", 0.50, 0.1, 2.0);

                INVESTMENT_FESTIVAL_DURATION_HOURS = BUILDER.comment(
                                "Duration in hours that festival happiness bonus lasts.")
                                .defineInRange("FestivalDurationHours", 48, 1, 168);
                BUILDER.pop();

                // --- Research Investment ---
                BUILDER.push("Research Investment");
                INVESTMENT_RESEARCH_COST = BUILDER.comment(
                                "Tax cost for research investment (spy defense bonus).")
                                .defineInRange("ResearchCost", 1500, 100, 1000000);

                INVESTMENT_RESEARCH_SPY_DEFENSE_BONUS = BUILDER.comment(
                                "Bonus to spy detection chance while research is active (0.0-1.0). " +
                                                "Example: 0.50 = 50% higher chance to detect enemy spies.")
                                .defineInRange("ResearchSpyDefenseBonus", 0.50, 0.1, 2.0);

                INVESTMENT_RESEARCH_DURATION_DAYS = BUILDER.comment(
                                "Duration in days that research spy defense bonus lasts.")
                                .defineInRange("ResearchDurationDays", 7, 1, 30);
                BUILDER.pop();

                BUILDER.pop(); // End Tax Expansion - Money Sinks

                // ============================================================
                // Random Events System
                // ============================================================
                BUILDER.push("Random Events");

                ENABLE_RANDOM_EVENTS = BUILDER
                                .comment("Enable the random events system for dynamic colony events")
                                .define("EnableRandomEvents", true);

                RANDOM_EVENT_CHECK_FREQUENCY = BUILDER
                                .comment("How often to check for events (1 = every tax cycle, 2 = every other)")
                                .defineInRange("RandomEventCheckFrequency", 1, 1, 10);

                RANDOM_EVENT_GLOBAL_COOLDOWN_CYCLES = BUILDER
                                .comment("Minimum tax cycles between any events for a colony")
                                .defineInRange("RandomEventGlobalCooldownCycles", 2, 0, 10);

                RANDOM_EVENT_MAX_SIMULTANEOUS = BUILDER
                                .comment("Maximum events active simultaneously per colony")
                                .defineInRange("RandomEventMaxSimultaneous", 2, 1, 5);

                RANDOM_EVENT_BASE_CHANCE_MULTIPLIER = BUILDER
                                .comment("Global multiplier for all event probabilities")
                                .defineInRange("RandomEventBaseChanceMultiplier", 1.0, 0.0, 5.0);

                RANDOM_EVENT_PROTECT_NEW_COLONIES_HOURS = BUILDER
                                .comment("Prevent events for colonies younger than X hours")
                                .defineInRange("RandomEventProtectNewColoniesHours", 24, 0, 168);

                // Individual Event Toggles
                BUILDER.comment("Individual event toggles - disable specific events you don't want");

                ENABLE_MERCHANT_CARAVAN = BUILDER
                                .comment("Enable Merchant Caravan event (+15% tax, +0.3 happiness)")
                                .define("EnableMerchantCaravan", true);

                ENABLE_BOUNTIFUL_HARVEST = BUILDER
                                .comment("Enable Bountiful Harvest event (+20% tax, +0.4 happiness)")
                                .define("EnableBountifulHarvest", true);

                ENABLE_CULTURAL_FESTIVAL = BUILDER
                                .comment("Enable Cultural Festival event (-5% tax, +0.5 happiness)")
                                .define("EnableCulturalFestival", true);

                ENABLE_SUCCESSFUL_RECRUITMENT = BUILDER
                                .comment("Enable Successful Recruitment event (+10% tax, +0.2 happiness)")
                                .define("EnableSuccessfulRecruitment", true);

                ENABLE_FOOD_SHORTAGE = BUILDER
                                .comment("Enable Food Shortage event (-15% tax, -0.3 happiness)")
                                .define("EnableFoodShortage", true);

                ENABLE_DISEASE_OUTBREAK = BUILDER
                                .comment("Enable Disease Outbreak event (-20% tax, -0.4 happiness)")
                                .define("EnableDiseaseOutbreak", true);

                ENABLE_BANDIT_HARASSMENT = BUILDER
                                .comment("Enable Bandit Harassment event (-10% tax, -0.2 happiness)")
                                .define("EnableBanditHarassment", true);

                ENABLE_CORRUPT_OFFICIAL = BUILDER
                                .comment("Enable Corrupt Official event (-25% tax, -0.1 happiness)")
                                .define("EnableCorruptOfficial", true);

                ENABLE_WANDERING_TRADER_OFFER = BUILDER
                                .comment("Enable Wandering Trader Offer event (choice-based)")
                                .define("EnableWanderingTraderOffer", true);

                ENABLE_NEIGHBORING_ALLIANCE = BUILDER
                                .comment("Enable Neighboring Alliance event (choice-based)")
                                .define("EnableNeighboringAlliance", true);

                ENABLE_WAR_PROFITEERING = BUILDER
                                .comment("Enable War Profiteering event (+35% tax, -0.5 happiness)")
                                .define("EnableWarProfiteering", true);

                ENABLE_GUARD_DESERTION = BUILDER
                                .comment("Enable Guard Desertion event (-30% tax, -0.6 happiness)")
                                .define("EnableGuardDesertion", true);

                ENABLE_LABOR_STRIKE = BUILDER
                                .comment("Enable Labor Strike event (-40% tax, -0.7 happiness, citizens stop working)")
                                .define("EnableLaborStrike", true);

                ENABLE_PLAGUE_OUTBREAK = BUILDER
                                .comment("Enable Plague Outbreak event (-35% tax, -0.8 happiness, citizens get diseased)")
                                .define("EnablePlagueOutbreak", true);

                ENABLE_ROYAL_FEAST = BUILDER
                                .comment("Enable Royal Feast event (+10% tax, +0.6 happiness, citizens fed)")
                                .define("EnableRoyalFeast", true);

                ENABLE_CROP_BLIGHT = BUILDER
                                .comment("Enable Crop Blight event (-25% tax, -0.5 happiness, citizens hungry)")
                                .define("EnableCropBlight", true);

                BUILDER.pop(); // End Random Events

                WAR_DURATION_MINUTES = BUILDER.comment("War duration (minutes)")
                                .defineInRange("WarDurationMinutes", 120, 1, 1440);

                PEACE_PROPOSAL_TIMEOUT_SECONDS = BUILDER.comment(
                                "Peace proposal timeout duration (seconds). Proposals auto-expire if not accepted/declined.")
                                .defineInRange("PeaceProposalTimeoutSeconds", 300, 30, 3600);

                MIN_GUARDS_TO_WAGE_WAR = BUILDER.comment("Minimum guards required to declare war. " +
                                "NOTE: This is only used when 'EnableWarBuildingRequirements' is disabled. " +
                                "If building requirements are enabled, they take priority over this setting.")
                                .defineInRange("MinGuardsToWageWar", 5, 1, 100);

                ENABLE_LP_GROUP_SWITCHING = BUILDER.comment(
                                "If enabled, war participants will be switched to the 'war' LP permission group during wars.\n"
                                                +
                                                "This requires LuckPerms to be installed and the 'war' group to be properly set up.\n"
                                                +
                                                "The command used is: /lp user <Player> parent set war")
                                .define("EnableLPGroupSwitching", false);

                JOIN_PHASE_DURATION_MINUTES = BUILDER.comment("Duration of the join phase in minutes")
                                .defineInRange("JoinPhaseDurationMinutes", 5, 1, 30);

                KEEP_INVENTORY_ON_LAST_LIFE = BUILDER.comment(
                                "If enabled, players will keep their inventory on their last life when they die in war.\n"
                                                +
                                                "This allows them to continue fighting without losing their gear, and especially important when colony transfer is enabled.")
                                .define("KeepInventoryOnLastLife", true);

                PLAYER_LIVES_IN_WAR = BUILDER.comment("Number of lives each player has during a war.")
                                .defineInRange("PlayerLivesInWar", 5, 1, 100); // Default 5 lives

                CONFIGURABLE_WAR_ACTIONS = BUILDER.comment(
                                "Actions permitted during a War. See https://ldtteam.github.io/MineColoniesAPI/com/minecolonies/api/colony/permissions/Action.html for a list of possible actions.\n"
                                                +
                                                "Note: GUARDS_ATTACK was removed from Minecolonies API - hostility is now controlled by Rank.isHostile()")
                                .defineList("WarActions",
                                                List.of("PLACE_BLOCKS", "TOSS_ITEM", "PICKUP_ITEM",
                                                                "ATTACK_CITIZEN", "FILL_BUCKET",
                                                                "SHOOT_ARROW", "RIGHTCLICK_BLOCK", "RIGHTCLICK_ENTITY",
                                                                "ATTACK_ENTITY", "EXPLODE", "HURT_CITIZEN",
                                                                "HURT_VISITOR", "THROW_POTION"),
                                                obj -> obj instanceof String);

                CONFIGURABLE_RAID_ACTIONS = BUILDER.comment(
                                "Actions permitted during a Raid. See https://ldtteam.github.io/MineColoniesAPI/com/minecolonies/api/colony/permissions/Action.html for a list of possible actions.\n"
                                                +
                                                "Note: GUARDS_ATTACK was removed from Minecolonies API - hostility is now controlled by Rank.isHostile()")
                                .defineList("RaidActions",
                                                List.of("TOSS_ITEM", "PICKUP_ITEM", "ATTACK_CITIZEN",
                                                                "FILL_BUCKET", "SHOOT_ARROW", "RIGHTCLICK_BLOCK",
                                                                "RIGHTCLICK_ENTITY", "ATTACK_ENTITY", "EXPLODE",
                                                                "HURT_CITIZEN", "HURT_VISITOR", "THROW_POTION"),
                                                obj -> obj instanceof String);

                CONFIGURABLE_CLAIMING_ACTIONS = BUILDER.comment(
                                "Actions permitted during Abandoned Colony Claiming raids. See https://ldtteam.github.io/MineColoniesAPI/com/minecolonies/api/colony/permissions/Action.html for a list of possible actions.\n"
                                                +
                                                "Note: GUARDS_ATTACK was removed from Minecolonies API - hostility is now controlled by Rank.isHostile()")
                                .defineList("ClaimingActions",
                                                List.of("PLACE_BLOCKS", "BREAK_BLOCKS", "TOSS_ITEM", "PICKUP_ITEM",
                                                                "ATTACK_CITIZEN", "FILL_BUCKET",
                                                                "SHOOT_ARROW", "RIGHTCLICK_BLOCK", "RIGHTCLICK_ENTITY",
                                                                "ATTACK_ENTITY", "EXPLODE", "HURT_CITIZEN",
                                                                "HURT_VISITOR", "THROW_POTION", "OPEN_CONTAINER"),
                                                obj -> obj instanceof String);

                ENABLE_BLOCK_INTERACTION_FILTER = BUILDER
                                .comment("Enable the block interaction filter system during raids and wars.\n" +
                                                "When enabled, this system overrides ALL other protection systems to enforce blacklist/whitelist rules.\n"
                                                +
                                                "BLACKLIST blocks CANNOT be interacted with (highest priority - prevents griefing).\n"
                                                +
                                                "WHITELIST blocks CAN be interacted with (overrides normal restrictions).")
                                .define("EnableBlockInteractionFilter", true);

                BLOCK_INTERACTION_BLACKLIST = BUILDER.comment(
                                "List of block IDs that CANNOT be interacted with during raids/wars (highest priority).\n"
                                                +
                                                "Format:\n" +
                                                "  - Specific block: 'modid:blockname' (e.g., 'minecraft:bedrock', 'minecolonies:blockhuttownhall')\n"
                                                +
                                                "  - Entire mod: '#modid' (e.g., '#refinedstorage', '#mekanism')\n" +
                                                "These blocks are completely protected and override any whitelist or permission settings.\n"
                                                +
                                                "Default blacklist prevents interaction with critical infrastructure blocks.")
                                .defineList("BlockInteractionBlacklist",
                                                List.of(
                                                                "minecraft:bedrock",
                                                                "minecraft:command_block",
                                                                "minecraft:chain_command_block",
                                                                "minecraft:repeating_command_block",
                                                                "minecraft:structure_block",
                                                                "minecraft:jigsaw",
                                                                "minecolonies:blockhuttownhall"),
                                                obj -> obj instanceof String);

                BLOCK_INTERACTION_WHITELIST = BUILDER
                                .comment("List of block IDs that CAN be interacted with during raids/wars.\n" +
                                                "Format:\n" +
                                                "  - Specific block: 'modid:blockname' (e.g., 'minecraft:lever', 'minecraft:crafting_table')\n"
                                                +
                                                "  - Entire mod: '#modid'\n"
                                                +
                                                "Note: Container blocks (chests, barrels, etc.) require OPEN_CONTAINER permission on the\n" +
                                                "Hostile rank to be accessible — simply whitelisting them here is NOT sufficient.\n" +
                                                "By default this list is empty: raid/war participants cannot loot enemy storage.\n" +
                                                "Blacklist always takes priority over whitelist!")
                                .defineList("BlockInteractionWhitelist",
                                                List.of(),
                                                obj -> obj instanceof String);

                BLOCK_FILTER_WARS = BUILDER.comment("Apply block interaction filter during wars.\n" +
                                "If enabled, blacklist/whitelist rules will be enforced during active wars.")
                                .define("BlockFilterWars", true);

                BLOCK_FILTER_RAIDS = BUILDER.comment("Apply block interaction filter during raids.\n" +
                                "If enabled, blacklist/whitelist rules will be enforced during active raids.")
                                .define("BlockFilterRaids", true);

                SUPPRESS_COLONY_LEVITATION = BUILDER.comment(
                                "Controls the MineColonies levitation trespassing-punishment.\n" +
                                "MineColonies does not expose a built-in toggle for this, so WNT provides one.\n" +
                                "\n" +
                                "true (default): levitation is suppressed for ALL players inside ANY colony at\n" +
                                "  ALL times. Players in active wars, raids, or besiegements are always exempt.\n" +
                                "\n" +
                                "false: levitation fires normally for everyone, EXCEPT players who are active\n" +
                                "  participants in a war, raid, or besiegement — they remain exempt so the\n" +
                                "  conflict mechanic is not disrupted.")
                                .define("SuppressColonyLevitation", true);

                REQUIRED_GUARD_TOWERS_FOR_BOOST = BUILDER.comment(
                                "Minimum number of Guard Towers to start receiving tax boost. " +
                                                "Set to 1 so boost scales linearly from first tower (per-tower bonus).")
                                .defineInRange("RequiredGuardTowersForBoost", 1, 1, 100);

                GUARD_TOWER_TAX_BOOST_PERCENTAGE = BUILDER.comment(
                                "Tax boost PERCENTAGE PER Guard Tower above the minimum threshold. " +
                                                "Applied per-tower for linear scaling (not a flat bonus). " +
                                                "Example: 0.08 = +8% per tower, so 5 towers = +40%, 10 towers = +80% (capped at 80%).")
                                .defineInRange("GuardTowerTaxBoostPercentage", 0.08, 0.0, 2.0);

                BUILDER.pop();

                BUILDER.push("PvP Arena Settings");

                PVP_COMMANDS_IN_BATTLE_ENABLED = BUILDER
                                .comment("Allow players to use commands while in a PvP battle. It's recommended to keep this false to prevent exploits.")
                                .define("allowCommandsInBattle", false);

                CHALLENGE_COOLDOWN_SECONDS = BUILDER
                                .comment("Cooldown in seconds before a player can send another duel challenge.")
                                .defineInRange("challengeCooldownSeconds", 5, 1, 300);

                TEAM_BATTLE_COOLDOWN_SECONDS = BUILDER
                                .comment("Cooldown in seconds before a player can organize another team battle.")
                                .defineInRange("teamBattleCooldownSeconds", 30, 1, 600);

                BATTLE_DURATION_SECONDS = BUILDER
                                .comment("Default duration of a battle in seconds before it ends in a draw.")
                                .defineInRange("battleDurationSeconds", 300, 60, 3600);

                TEAM_BATTLE_START_COUNTDOWN_SECONDS = BUILDER
                                .comment("The countdown in seconds before a team battle starts after enough players have joined.")
                                .defineInRange("teamBattleStartCountdownSeconds", 20, 5, 120);

                BATTLE_END_COUNTDOWN_SECONDS = BUILDER
                                .comment("Time in seconds before battle ends after victory is decided.")
                                .defineInRange("BattleEndCountdownSeconds", 10, 1, 60);

                PVP_DISABLE_FRIENDLY_FIRE = BUILDER
                                .comment("Prevents players from damaging teammates in team PvP battles.")
                                .define("DisableFriendlyFire", true);

                BUILDER.pop();

                BUILDER.push("Military Maintenance Costs");

                BUILDING_MAINTENANCE.put("barracks",
                                BUILDER.comment("Base maintenance cost per hour for Barracks (Range: 0-10000)")
                                                .defineInRange("barracksMaintenance", 15.0, 0.0, 10000.0));
                UPGRADE_MAINTENANCE.put("barracks",
                                BUILDER.comment("Additional maintenance per level for Barracks (Range: 0-1000)")
                                                .defineInRange("barracksMaintenanceUpgrade", 5.0, 0.0, 1000.0));

                BUILDING_MAINTENANCE.put("guardtower",
                                BUILDER.comment("Base maintenance cost per hour for Guard Tower (Range: 0-10000)")
                                                .defineInRange("guardtowerMaintenance", 10.0, 0.0, 10000.0));
                UPGRADE_MAINTENANCE.put("guardtower",
                                BUILDER.comment("Additional maintenance per level for Guard Tower (Range: 0-1000)")
                                                .defineInRange("guardtowerMaintenanceUpgrade", 3.0, 0.0,
                                                                1000.0));

                BUILDING_MAINTENANCE.put("barrackstower",
                                BUILDER.comment("Base maintenance cost per hour for Barracks Tower (Range: 0-10000)")
                                                .defineInRange("barrackstowerMaintenance", 14.0, 0.0, 10000.0));
                UPGRADE_MAINTENANCE.put("barrackstower",
                                BUILDER.comment("Additional maintenance per level for Barracks Tower (Range: 0-1000)")
                                                .defineInRange("barrackstowerMaintenanceUpgrade", 6.0, 0.0,
                                                                1000.0));

                BUILDING_MAINTENANCE.put("archery",
                                BUILDER.comment("Base maintenance cost per hour for Archery (Range: 0-10000)")
                                                .defineInRange("archeryMaintenance", 12.0, 0.0, 10000.0));
                UPGRADE_MAINTENANCE.put("archery",
                                BUILDER.comment("Additional maintenance per level for Archery (Range: 0-1000)")
                                                .defineInRange("archeryMaintenanceUpgrade", 6.0, 0.0, 1000.0));

                BUILDING_MAINTENANCE.put("combatacademy",
                                BUILDER.comment("Base maintenance cost per hour for Combat Academy (Range: 0-10000)")
                                                .defineInRange("combatacademyMaintenance", 14.0, 0.0, 10000.0));
                UPGRADE_MAINTENANCE.put("combatacademy",
                                BUILDER.comment("Additional maintenance per level for Combat Academy (Range: 0-1000)")
                                                .defineInRange("combatacademyMaintenanceUpgrade", 6.0, 0.0,
                                                                1000.0));

                BUILDER.pop();

                // ========== Building Taxes ========== //
                BUILDER.push("Building Taxes");

                // BUILDING_TAXES.put("archery", BUILDER.comment("Base tax for Archery")
                // .defineInRange("archery", 12.0, 0.0, 10000.0));
                // UPGRADE_TAXES.put("archery", BUILDER.comment("Tax increase per level for
                // Archery")
                // .defineInRange("archeryUpgrade", 6.0, 0.0, 10000.0));

                BUILDING_TAXES.put("alchemist", BUILDER.comment("Base tax for Alchemist")
                                .defineInRange("alchemist", 12.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("alchemist", BUILDER.comment("Tax increase per level for Alchemist")
                                .defineInRange("alchemistUpgrade", 5.0, 0.0, 10000.0));

                BUILDING_TAXES.put("concretemixer", BUILDER.comment("Base tax for Concrete Mixer")
                                .defineInRange("concretemixer", 10.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("concretemixer", BUILDER.comment("Tax increase per level for Concrete Mixer")
                                .defineInRange("concretemixerUpgrade", 4.0, 0.0, 10000.0));

                BUILDING_TAXES.put("fletcher", BUILDER.comment("Base tax for Fletcher")
                                .defineInRange("fletcher", 9.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("fletcher", BUILDER.comment("Tax increase per level for Fletcher")
                                .defineInRange("fletcherUpgrade", 3.0, 0.0, 10000.0));

                BUILDING_TAXES.put("lumberjack", BUILDER.comment("Base tax for Lumberjack")
                                .defineInRange("lumberjack", 11.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("lumberjack", BUILDER.comment("Tax increase per level for Lumberjack")
                                .defineInRange("lumberjackUpgrade", 5.0, 0.0, 10000.0));

                BUILDING_TAXES.put("rabbithutch", BUILDER.comment("Base tax for Rabbit Hutch")
                                .defineInRange("rabbithutch", 8.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("rabbithutch", BUILDER.comment("Tax increase per level for Rabbit Hutch")
                                .defineInRange("rabbithutchUpgrade", 2.0, 0.0, 10000.0));

                BUILDING_TAXES.put("shepherd", BUILDER.comment("Base tax for Shepherd")
                                .defineInRange("shepherd", 9.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("shepherd", BUILDER.comment("Tax increase per level for Shepherd")
                                .defineInRange("shepherdUpgrade", 3.0, 0.0, 10000.0));

                BUILDING_TAXES.put("smeltery", BUILDER.comment("Base tax for Smeltery")
                                .defineInRange("smeltery", 15.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("smeltery", BUILDER.comment("Tax increase per level for Smeltery")
                                .defineInRange("smelteryUpgrade", 6.0, 0.0, 10000.0));

                BUILDING_TAXES.put("swineherder", BUILDER.comment("Base tax for Swine Herder")
                                .defineInRange("swineherder", 10.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("swineherder", BUILDER.comment("Tax increase per level for Swine Herder")
                                .defineInRange("swineherderUpgrade", 4.0, 0.0, 10000.0));

                BUILDING_TAXES.put("townhall", BUILDER.comment("Base tax for Town Hall")
                                .defineInRange("townhall", 20.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("townhall", BUILDER.comment("Tax increase per level for Town Hall")
                                .defineInRange("townhallUpgrade", 8.0, 0.0, 10000.0));

                BUILDING_TAXES.put("warehousedeliveryman", BUILDER.comment("Base tax for Warehouse Deliveryman")
                                .defineInRange("warehousedeliveryman", 12.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("warehousedeliveryman",
                                BUILDER.comment("Tax increase per level for Warehouse Deliveryman")
                                                .defineInRange("warehousedeliverymanUpgrade", 5.0, 0.0,
                                                                10000.0));

                BUILDING_TAXES.put("bakery", BUILDER.comment("Base tax for Bakery")
                                .defineInRange("bakery", 10.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("bakery", BUILDER.comment("Tax increase per level for Bakery")
                                .defineInRange("bakeryUpgrade", 4.0, 0.0, 10000.0));

                // BUILDING_TAXES.put("barracks", BUILDER.comment("Base tax for Barracks")
                // .defineInRange("barracks", 15.0, 0.0, 10000.0));
                // UPGRADE_TAXES.put("barracks", BUILDER.comment("Tax increase per level for
                // Barracks")
                // .defineInRange("barracksUpgrade", 7.0, 0.0, 10000.0));
                //
                // BUILDING_TAXES.put("barrackstower", BUILDER.comment("Base tax for Barracks
                // Tower")
                // .defineInRange("barrackstower", 14.0, 0.0, 10000.0));
                // UPGRADE_TAXES.put("barrackstower", BUILDER.comment("Tax increase per level
                // for Barracks Tower")
                // .defineInRange("barrackstowerUpgrade", 6.0, 0.0, 10000.0));

                BUILDING_TAXES.put("blacksmith", BUILDER.comment("Base tax for Blacksmith")
                                .defineInRange("blacksmith", 18.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("blacksmith", BUILDER.comment("Tax increase per level for Blacksmith")
                                .defineInRange("blacksmithUpgrade", 8.0, 0.0, 10000.0));

                BUILDING_TAXES.put("builder", BUILDER.comment("Base tax for Builder")
                                .defineInRange("builder", 8.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("builder", BUILDER.comment("Tax increase per level for Builder")
                                .defineInRange("builderUpgrade", 4.0, 0.0, 10000.0));

                BUILDING_TAXES.put("chickenherder", BUILDER.comment("Base tax for Chicken Herder")
                                .defineInRange("chickenherder", 9.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("chickenherder", BUILDER.comment("Tax increase per level for Chicken Herder")
                                .defineInRange("chickenherderUpgrade", 3.0, 0.0, 10000.0));

                // BUILDING_TAXES.put("combatacademy", BUILDER.comment("Base tax for Combat
                // Academy")
                // .defineInRange("combatacademy", 14.0, 0.0, 10000.0));
                // UPGRADE_TAXES.put("combatacademy", BUILDER.comment("Tax increase per level
                // for Combat Academy")
                // .defineInRange("combatacademyUpgrade", 6.0, 0.0, 10000.0));

                BUILDING_TAXES.put("composter", BUILDER.comment("Base tax for Composter")
                                .defineInRange("composter", 6.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("composter", BUILDER.comment("Tax increase per level for Composter")
                                .defineInRange("composterUpgrade", 2.0, 0.0, 10000.0));

                BUILDING_TAXES.put("cook", BUILDER.comment("Base tax for Cook")
                                .defineInRange("cook", 12.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("cook", BUILDER.comment("Tax increase per level for Cook")
                                .defineInRange("cookUpgrade", 5.0, 0.0, 10000.0));

                BUILDING_TAXES.put("cowboy", BUILDER.comment("Base tax for Cowboy")
                                .defineInRange("cowboy", 9.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("cowboy", BUILDER.comment("Tax increase per level for Cowboy")
                                .defineInRange("cowboyUpgrade", 4.0, 0.0, 10000.0));

                BUILDING_TAXES.put("crusher", BUILDER.comment("Base tax for Crusher")
                                .defineInRange("crusher", 13.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("crusher", BUILDER.comment("Tax increase per level for Crusher")
                                .defineInRange("crusherUpgrade", 6.0, 0.0, 10000.0));

                BUILDING_TAXES.put("deliveryman", BUILDER.comment("Base tax for Deliveryman")
                                .defineInRange("deliveryman", 12.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("deliveryman", BUILDER.comment("Tax increase per level for Deliveryman")
                                .defineInRange("deliverymanUpgrade", 5.0, 0.0, 10000.0));

                BUILDING_TAXES.put("farmer", BUILDER.comment("Base tax for Farmer")
                                .defineInRange("farmer", 11.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("farmer", BUILDER.comment("Tax increase per level for Farmer")
                                .defineInRange("farmerUpgrade", 5.0, 0.0, 10000.0));

                BUILDING_TAXES.put("fisherman", BUILDER.comment("Base tax for Fisherman")
                                .defineInRange("fisherman", 10.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("fisherman", BUILDER.comment("Tax increase per level for Fisherman")
                                .defineInRange("fishermanUpgrade", 4.0, 0.0, 10000.0));

                // BUILDING_TAXES.put("guardtower", BUILDER.comment("Base tax for Guard Tower")
                // .defineInRange("guardtower", 10.0, 0.0, 10000.0));
                // UPGRADE_TAXES.put("guardtower", BUILDER.comment("Tax increase per level for
                // Guard Tower")
                // .defineInRange("guardtowerUpgrade", 5.0, 0.0, 10000.0));

                BUILDING_TAXES.put("home", BUILDER.comment("Base tax for Residence")
                                .defineInRange("home", 5.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("home", BUILDER.comment("Tax increase per level for Residence")
                                .defineInRange("homeUpgrade", 2.0, 0.0, 10000.0));

                BUILDING_TAXES.put("library", BUILDER.comment("Base tax for Library")
                                .defineInRange("library", 13.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("library", BUILDER.comment("Tax increase per level for Library")
                                .defineInRange("libraryUpgrade", 6.0, 0.0, 10000.0));

                BUILDING_TAXES.put("university", BUILDER.comment("Base tax for University")
                                .defineInRange("university", 20.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("university", BUILDER.comment("Tax increase per level for University")
                                .defineInRange("universityUpgrade", 10.0, 0.0, 10000.0));

                BUILDING_TAXES.put("warehouse", BUILDER.comment("Base tax for Warehouse")
                                .defineInRange("warehouse", 10.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("warehouse", BUILDER.comment("Tax increase per level for Warehouse")
                                .defineInRange("warehouseUpgrade", 4.0, 0.0, 10000.0));

                BUILDING_TAXES.put("tavern", BUILDER.comment("Base tax for Tavern")
                                .defineInRange("tavern", 14.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("tavern", BUILDER.comment("Tax increase per level for Tavern")
                                .defineInRange("tavernUpgrade", 6.0, 0.0, 10000.0));

                BUILDING_TAXES.put("miner", BUILDER.comment("Base tax for Miner")
                                .defineInRange("miner", 11.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("miner", BUILDER.comment("Tax increase per level for Miner")
                                .defineInRange("minerUpgrade", 5.0, 0.0, 10000.0));

                BUILDING_TAXES.put("sawmill", BUILDER.comment("Base tax for Sawmill")
                                .defineInRange("sawmill", 10.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("sawmill", BUILDER.comment("Tax increase per level for Sawmill")
                                .defineInRange("sawmillUpgrade", 3.0, 0.0, 10000.0));

                BUILDING_TAXES.put("stonemason", BUILDER.comment("Base tax for Stonemason")
                                .defineInRange("stonemason", 12.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("stonemason", BUILDER.comment("Tax increase per level for Stonemason")
                                .defineInRange("stonemasonUpgrade", 4.0, 0.0, 10000.0));

                BUILDING_TAXES.put("florist", BUILDER.comment("Base tax for Florist")
                                .defineInRange("florist", 8.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("florist", BUILDER.comment("Tax increase per level for Florist")
                                .defineInRange("floristUpgrade", 2.0, 0.0, 10000.0));

                BUILDING_TAXES.put("enchanter", BUILDER.comment("Base tax for Enchanter")
                                .defineInRange("enchanter", 15.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("enchanter", BUILDER.comment("Tax increase per level for Enchanter")
                                .defineInRange("enchanterUpgrade", 5.0, 0.0, 10000.0));

                BUILDING_TAXES.put("hospital", BUILDER.comment("Base tax for Hospital")
                                .defineInRange("hospital", 20.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("hospital", BUILDER.comment("Tax increase per level for Hospital")
                                .defineInRange("hospitalUpgrade", 8.0, 0.0, 10000.0));

                BUILDING_TAXES.put("glassblower", BUILDER.comment("Base tax for Glassblower")
                                .defineInRange("glassblower", 10.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("glassblower", BUILDER.comment("Tax increase per level for Glassblower")
                                .defineInRange("glassblowerUpgrade", 3.0, 0.0, 10000.0));

                BUILDING_TAXES.put("dyer", BUILDER.comment("Base tax for Dyer")
                                .defineInRange("dyer", 9.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("dyer", BUILDER.comment("Tax increase per level for Dyer")
                                .defineInRange("dyerUpgrade", 3.0, 0.0, 10000.0));

                BUILDING_TAXES.put("mechanic", BUILDER.comment("Base tax for Mechanic")
                                .defineInRange("mechanic", 11.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("mechanic", BUILDER.comment("Tax increase per level for Mechanic")
                                .defineInRange("mechanicUpgrade", 4.0, 0.0, 10000.0));

                BUILDING_TAXES.put("plantation", BUILDER.comment("Base tax for Plantation")
                                .defineInRange("plantation", 12.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("plantation", BUILDER.comment("Tax increase per level for Plantation")
                                .defineInRange("plantationUpgrade", 4.0, 0.0, 10000.0));

                BUILDING_TAXES.put("graveyard", BUILDER.comment("Base tax for Graveyard")
                                .defineInRange("graveyard", 7.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("graveyard", BUILDER.comment("Tax increase per level for Graveyard")
                                .defineInRange("graveyardUpgrade", 2.0, 0.0, 10000.0));

                BUILDING_TAXES.put("beekeeper", BUILDER.comment("Base tax for Beekeeper")
                                .defineInRange("beekeeper", 9.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("beekeeper", BUILDER.comment("Tax increase per level for Beekeeper")
                                .defineInRange("beekeeperUpgrade", 3.0, 0.0, 10000.0));

                BUILDING_TAXES.put("netherworker", BUILDER.comment("Base tax for Nether Worker")
                                .defineInRange("netherworker", 15.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("netherworker", BUILDER.comment("Tax increase per level for Nether Worker")
                                .defineInRange("netherworkerUpgrade", 6.0, 0.0, 10000.0));

                BUILDING_TAXES.put("stonesmeltery", BUILDER.comment("Base tax for Stone Smeltery")
                                .defineInRange("stonesmeltery", 12.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("stonesmeltery", BUILDER.comment("Tax increase per level for Stone Smeltery")
                                .defineInRange("stonesmelteryUpgrade", 5.0, 0.0, 10000.0));

                BUILDING_TAXES.put("sifter", BUILDER.comment("Base tax for Sifter")
                                .defineInRange("sifter", 10.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("sifter", BUILDER.comment("Tax increase per level for Sifter")
                                .defineInRange("sifterUpgrade", 4.0, 0.0, 10000.0));

                BUILDING_TAXES.put("postbox", BUILDER.comment("Base tax for Postbox")
                                .defineInRange("postbox", 8.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("postbox", BUILDER.comment("Tax increase per level for Postbox")
                                .defineInRange("postboxUpgrade", 2.0, 0.0, 10000.0));

                BUILDING_TAXES.put("stash", BUILDER.comment("Base tax for Stash")
                                .defineInRange("stash", 5.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("stash", BUILDER.comment("Tax increase per level for Stash")
                                .defineInRange("stashUpgrade", 1.0, 0.0, 10000.0));

                BUILDING_TAXES.put("school", BUILDER.comment("Base tax for School")
                                .defineInRange("school", 15.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("school", BUILDER.comment("Tax increase per level for School")
                                .defineInRange("schoolUpgrade", 5.0, 0.0, 10000.0));

                BUILDING_TAXES.put("mysticalsite", BUILDER.comment("Base tax for Mystical Site")
                                .defineInRange("mysticalsite", 20.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("mysticalsite", BUILDER.comment("Tax increase per level for Mystical Site")
                                .defineInRange("mysticalsiteUpgrade", 10.0, 0.0, 10000.0));

                BUILDING_TAXES.put("simplequarry", BUILDER.comment("Base tax for Simple Quarry")
                                .defineInRange("simplequarry", 15.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("simplequarry", BUILDER.comment("Tax increase per level for Simple Quarry")
                                .defineInRange("simplequarryUpgrade", 5.0, 0.0, 10000.0));

                BUILDING_TAXES.put("mediumquarry", BUILDER.comment("Base tax for Medium Quarry")
                                .defineInRange("mediumquarry", 20.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("mediumquarry", BUILDER.comment("Tax increase per level for Medium Quarry")
                                .defineInRange("mediumquarryUpgrade", 7.0, 0.0, 10000.0));

                BUILDING_TAXES.put("largequarry", BUILDER.comment("Base tax for Large Quarry")
                                .defineInRange("largequarry", 25.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("largequarry", BUILDER.comment("Tax increase per level for Large Quarry")
                                .defineInRange("largequarryUpgrade", 9.0, 0.0, 10000.0));

                BUILDING_TAXES.put("kitchen", BUILDER.comment("Base tax for Kitchen")
                                .defineInRange("kitchen", 12.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("kitchen", BUILDER.comment("Tax increase per level for Kitchen")
                                .defineInRange("kitchenUpgrade", 5.0, 0.0, 10000.0));

                BUILDING_TAXES.put("gatehouse", BUILDER.comment("Base tax for Gatehouse")
                                .defineInRange("gatehouse", 10.0, 0.0, 10000.0));
                UPGRADE_TAXES.put("gatehouse", BUILDER.comment("Tax increase per level for Gatehouse")
                                .defineInRange("gatehouseUpgrade", 3.0, 0.0, 10000.0));

                // Legacy class name mappings
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.barracks", "barracks");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.guardtower", "guardtower");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.archery", "archery");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.bakery", "bakery");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.blacksmith", "blacksmith");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.builder", "builder");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.chickenherder", "chickenherder");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.combatacademy", "combatacademy");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.composter", "composter");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.cook", "cook");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.cowboy", "cowboy");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.crusher", "crusher");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.deliveryman", "deliveryman");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.farmer", "farmer");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.fisherman", "fisherman");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.home", "residence");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.library", "library");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.university", "university");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.warehouse", "warehouse");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.tavern", "tavern");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.miner", "miner");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.sawmill", "sawmill");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.stonemason", "stonemason");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.florist", "florist");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.enchanter", "enchanter");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.hospital", "hospital");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.glassblower", "glassblower");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.dyer", "dyer");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.mechanic", "mechanic");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.plantation", "plantation");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.graveyard", "graveyard");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.beekeeper", "beekeeper");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.netherworker", "netherworker");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.alchemist", "alchemist");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.concretemixer", "concretemixer");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.fletcher", "fletcher");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.lumberjack", "lumberjack");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.rabbithutch", "rabbithutch");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.shepherd", "shepherd");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.smeltery", "smeltery");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.swineherder", "swineherder");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.townhall", "townhall");
                CLASS_NAME_TO_SHORT_NAME.put("com.minecolonies.building.warehousedeliveryman", "warehousedeliveryman");

                String prefix = "com.minecolonies.core.colony.buildings.workerbuildings.";
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingBarracks", "barracks");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingGuardTower", "guardtower");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingArchery", "archery");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingBaker", "bakery");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingBlacksmith", "blacksmith");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingBuilder", "builder");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingChickenHerder", "chickenherder");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingCombatAcademy", "combatacademy");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingComposter", "composter");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingCook", "cook");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingCowboy", "cowboy");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingCrusher", "crusher");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingDeliveryman", "deliveryman");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingFarmer", "farmer");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingFisherman", "fisherman");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingResidence", "residence");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingLibrary", "library");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingUniversity", "university");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingWarehouse", "warehouse");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingTavern", "tavern");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingMiner", "miner");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingSawmill", "sawmill");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingStonemason", "stonemason");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingFlorist", "florist");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingEnchanter", "enchanter");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingHospital", "hospital");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingGlassblower", "glassblower");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingDyer", "dyer");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingMechanic", "mechanic");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingPlantation", "plantation");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingGraveyard", "graveyard");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingBeekeeper", "beekeeper");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingNetherWorker", "netherworker");

                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingAlchemist", "alchemist");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingConcreteMixer", "concretemixer");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingFletcher", "fletcher");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingLumberjack", "lumberjack");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingRabbitHutch", "rabbithutch");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingShepherd", "shepherd");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingSmeltery", "smeltery");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingSwineHerder", "swineherder");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingTownHall", "townhall");
                // CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingWarehouseDeliveryman",
                // "warehousedeliveryman"); // Unlikely to need explicit class map if it's just
                // a deliveryman variant, but kept for consistency if it exists

                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingStoneSmeltery", "stonesmeltery");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingSifter", "sifter");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingPostbox", "postbox");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingStash", "stash");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingSchool", "school");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingMysticalSite", "mysticalsite");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingSimpleQuarry", "simplequarry");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingMediumQuarry", "mediumquarry");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingLargeQuarry", "largequarry");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingKitchen", "kitchen");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingGateHouse", "gatehouse");

                // ========== Besiege System ==========
                BUILDER.push("Besiege System");

                ENABLE_BESIEGE_SYSTEM = BUILDER
                                .comment("Enable the besiege system. Allows players to raid active non-primary colonies for tax vassalage.")
                                .define("EnableBesiegeSystem", true);

                BESIEGE_DURATION_MINUTES = BUILDER
                                .comment("Duration of a besiege raid in minutes. Defenders must survive this long to repel the attack.")
                                .defineInRange("BesiegeDurationMinutes", 20, 1, 120);

                BESIEGE_COOLDOWN_HOURS = BUILDER
                                .comment("Hours a player must wait after a besiege attempt (win or loss) before besieging again.")
                                .defineInRange("BesiegeCooldownHours", 24, 0, 168);

                BESIEGE_MILITIA_PERCENT = BUILDER
                                .comment("Fraction of eligible citizens (non-guard, non-deliveryman) converted to militia defenders during a besiege.")
                                .defineInRange("BesiegeMilitiaPercent", 0.6, 0.0, 1.0);

                BESIEGE_TRIBUTE_PERCENT = BUILDER
                                .comment("Percentage of tax income siphoned from the besieged colony to the victor.")
                                .defineInRange("BesiegeTributePercent", 30, 1, 100);

                BESIEGE_TRIBUTE_DURATION_HOURS = BUILDER
                                .comment("How long (hours) the besiege vassalage lasts. Set to 0 for permanent until reclaimed.")
                                .defineInRange("BesiegeTributeDurationHours", 72, 0, 8760);

                BESIEGE_MIN_COLONY_SIZE = BUILDER
                                .comment("Minimum citizen count required in the target colony before it can be besieged.")
                                .defineInRange("BesiegeMinColonySize", 5, 1, 100);

                BESIEGE_ALLIES_ENABLED = BUILDER
                                .comment("Allow other players to assist the besieger by attacking defenders. They are tracked as allies.")
                                .define("BesiegeAlliesEnabled", true);

                BESIEGE_EXTRA_MERCENARIES_PER_BUILDING = BUILDER
                                .comment("Mercenaries spawned per colony building. E.g. 0.33 = 1 mercenary per 3 buildings.")
                                .defineInRange("BesiegeExtraMercenariesPerBuilding", 0.33, 0.0, 5.0);

                BESIEGE_MAX_MERCENARIES = BUILDER
                                .comment("Maximum number of mercenaries that can be spawned during a single besiege raid.")
                                .defineInRange("BesiegeMaxMercenaries", 10, 0, 50);

                BESIEGE_PLAYER_STAY_RADIUS = BUILDER
                                .comment("Maximum distance (blocks) the besieging player may stray from the colony center. Exceeding this cancels the raid.")
                                .defineInRange("BesiegePlayerStayRadius", 100, 20, 500);

                // ----- Siege access controls: who may interact with a besieged/vassalized colony -----
                BESIEGE_ALLOW_CHEST_ACCESS = BUILDER
                                .comment("[Access] Allow besiegers to open chests/containers in the colony during an ACTIVE siege.",
                                                "false (default): combat-only, no looting. true: attackers may open containers.")
                                .define("BesiegeAllowChestAccess", false);

                VASSAL_LOCK_OUT_FORMER_OWNER = BUILDER
                                .comment("[Access] After a siege ends and the colony is vassalized (besiege occupation), should the",
                                                "ORIGINAL OWNER be locked out of their own colony until they reclaim it?",
                                                "false (default): the owner keeps access and only pays tax tribute.",
                                                "true: the owner is locked out (chests/buildings) until a successful reclaim raid.")
                                .define("VassalLockOutFormerOwner", false);

                // ----- Multiplayer & online rules: how many attackers, and staying logged in -----
                BESIEGE_MIN_ATTACKERS = BUILDER
                                .comment("[Multiplayer] Minimum number of distinct besiegers that must take part for a siege to",
                                                "CAPTURE the colony (the 'not solo' rule). 1 = solo sieges allowed (default).",
                                                "Higher values force players to band together: if fewer than this are present when",
                                                "all defenders fall, the defenders are wiped but the colony is NOT captured.")
                                .defineInRange("BesiegeMinAttackers", 1, 1, 20);

                BESIEGE_SHARE_SPOILS = BUILDER
                                .comment("[Multiplayer] When several players besiege the SAME colony (each runs /wnt besiege on it),",
                                                "split the treasury spoils (BesiegeSpoilPercentOfLoserTreasury) among all participating",
                                                "besiegers' primary colonies on victory, instead of only the first to resolve.",
                                                "The lead besieger still receives the ongoing tax-tribute stream.")
                                .define("BesiegeShareSpoils", true);

                BESIEGE_REQUIRE_ONLINE = BUILDER
                                .comment("[Online] Require besieging player(s) to stay online during a siege. If true, a besieger who",
                                                "logs off for longer than BesiegeOfflineGraceMinutes has their participation cancelled",
                                                "(a siege cannot be run from the lobby).")
                                .define("BesiegeRequireOnline", true);

                BESIEGE_OFFLINE_GRACE_MINUTES = BUILDER
                                .comment("[Online] Minutes a besieger may stay offline before their siege participation is cancelled",
                                                "(only applies when BesiegeRequireOnline is true).")
                                .defineInRange("BesiegeOfflineGraceMinutes", 5, 0, 120);

                BUILDER.pop(); // End Besiege System

                CONFIG = BUILDER.build();
        }

        public static boolean isSDMShopConversionEnabled() {
                return ENABLE_SDM_SHOP_CONVERSION.get();
        }

        public static String getSDMCurrencyName() {
                return SDM_CURRENCY_NAME.get();
        }

        public static String getCurrencyItemName() {
                return CURRENCY_ITEM_NAME.get();
        }

        public static String getCurrencyDenominations() {
                return CURRENCY_DENOMINATIONS.get();
        }

        /**
         * Retrieves the base tax for a given building type using its full class name.
         *
         * @param fullClassName The full class name of the building type.
         * @return The base tax amount.
         */
        public static double getBaseTaxForBuilding(String fullClassName) {
                String shortName = getShortBuildingName(fullClassName);
                ForgeConfigSpec.DoubleValue taxValue = BUILDING_TAXES.get(shortName);
                return (taxValue != null) ? taxValue.get() : 0.0;
        }

        public static int getMaxTaxRevenue() {
                return MAX_TAX_REVENUE.get();
        }

        public static int getMinGuardsToRaid() {
                return MIN_GUARDS_TO_RAID.get();
        }

        public static int getDebtLimit() {
                return DEBT_LIMIT.get();
        }

        public static int getDebtEventCycles() {
                return DEBT_EVENT_CYCLES.get();
        }

        public static int getDebtAbandonmentCycles() {
                return DEBT_ABANDONMENT_CYCLES.get();
        }

        public static boolean isDebtBlocksWar() {
                return DEBT_BLOCKS_WAR.get();
        }

        public static boolean isDebtBlocksSpy() {
                return DEBT_BLOCKS_SPY.get();
        }

        public static double getDebtHappinessFactor() {
                return DEBT_HAPPINESS_FACTOR.get();
        }

        public static int getTaxStealPerGuard() {
                return TAX_STEAL_PER_GUARD.get();
        }

        /**
         * Retrieves the upgrade tax for a given building type using its full class
         * name.
         *
         * @param fullClassName The full class name of the building type.
         * @return The upgrade tax amount per level.
         */
        public static double getUpgradeTaxForBuilding(String fullClassName) {
                String shortName = getShortBuildingName(fullClassName);
                ForgeConfigSpec.DoubleValue upgradeValue = UPGRADE_TAXES.get(shortName);
                return (upgradeValue != null) ? upgradeValue.get() : 0.0;
        }

        public static double getBaseMaintenanceForBuilding(String fullClassName) {
                String shortName = getShortBuildingName(fullClassName);
                ForgeConfigSpec.DoubleValue maintenanceValue = BUILDING_MAINTENANCE.get(shortName);
                return (maintenanceValue != null) ? maintenanceValue.get() : 0.0;
        }

        public static double getUpgradeMaintenanceForBuilding(String fullClassName) {
                String shortName = getShortBuildingName(fullClassName);
                ForgeConfigSpec.DoubleValue upgradeValue = UPGRADE_MAINTENANCE.get(shortName);
                return (upgradeValue != null) ? upgradeValue.get() : 0.0;
        }

        public static int getRequiredGuardTowersForBoost() {
                return REQUIRED_GUARD_TOWERS_FOR_BOOST.get();
        }

        public static double getGuardTowerTaxBoostPercentage() {
                return GUARD_TOWER_TAX_BOOST_PERCENTAGE.get();
        }

        /**
         * Retrieves the tax interval in minutes.
         *
         * @return The tax interval in minutes.
         */
        public static int getTaxIntervalInMinutes() {
                return TAX_INTERVAL_MINUTES.get();
        }

        /**
         * Helper method to convert full class name to short config name.
         *
         * @param fullClassName Full class name of the building (e.g.,
         *                      com.minecolonies.building.barracks).
         * @return The corresponding short name (e.g., barracks).
         */
        private static String getShortBuildingName(String fullClassName) {
                return CLASS_NAME_TO_SHORT_NAME.getOrDefault(fullClassName, "unknown");
        }

        public static boolean isColonyTransferEnabled() {
                return ENABLE_COLONY_TRANSFER.get();
        }

        public static boolean isOccupationSystemEnabled() {
                return ENABLE_OCCUPATION_SYSTEM.get();
        }

        public static int getOccupationDurationDays() {
                return OCCUPATION_DURATION_DAYS.get();
        }

        public static double getOccupationTaxPercentage() {
                return OCCUPATION_TAX_PERCENTAGE.get();
        }

        public static boolean isOutpostVulnerabilityEnabled() {
                return ENABLE_OUTPOST_VULNERABILITY.get();
        }

        public static boolean isColonyWagerEnabled() {
                return ENABLE_COLONY_WAGER.get();
        }

        public static double getWarVictoryPercentage() {
                return WAR_VICTORY_PERCENTAGE.get();
        }

        public static double getWarDefeatPercentage() {
                return WAR_DEFEAT_PERCENTAGE.get();
        }

        public static double getWarStalematePercentage() {
                return WAR_STALEMATE_PERCENTAGE.get();
        }

        public static int getWarTaxFreezeHours() {
                return WAR_TAX_FREEZE_HOURS.get();
        }

        public static boolean isWarVassalizationEnabled() {
                return ENABLE_WAR_VASSALIZATION.get();
        }

        public static boolean isPrimaryColonyTransferEnabled() {
                return ENABLE_PRIMARY_COLONY_TRANSFER.get();
        }

        public static int getPrimaryColonyTaxOccupationDays() {
                return PRIMARY_COLONY_TAX_OCCUPATION_DAYS.get();
        }

        public static int getBesiegeSpoilPercentOfLoserTreasury() {
                return BESIEGE_SPOIL_PERCENT_OF_LOSER_TREASURY.get();
        }

        public static boolean isBesiegeChestAccessAllowed() {
                return BESIEGE_ALLOW_CHEST_ACCESS.get();
        }

        public static int getBesiegeMinAttackers() {
                return BESIEGE_MIN_ATTACKERS.get();
        }

        public static boolean isBesiegeRequireOnline() {
                return BESIEGE_REQUIRE_ONLINE.get();
        }

        public static int getBesiegeOfflineGraceMinutes() {
                return BESIEGE_OFFLINE_GRACE_MINUTES.get();
        }

        public static boolean isBesiegeShareSpoilsEnabled() {
                return BESIEGE_SHARE_SPOILS.get();
        }

        public static int getWarVassalizationTreasuryGrabPercent() {
                return WAR_VASSALIZATION_TREASURY_GRAB_PERCENT.get();
        }

        public static int getWarVassalizationPlayerBalanceGrabPercent() {
                return WAR_VASSALIZATION_PLAYER_BALANCE_GRAB_PERCENT.get();
        }

        public static boolean isVassalLockOutFormerOwnerEnabled() {
                return VASSAL_LOCK_OUT_FORMER_OWNER.get();
        }

        public static boolean isExperimentalSiegeObjectivesEnabled() {
                return ENABLE_EXPERIMENTAL_SIEGE_OBJECTIVES.get();
        }

        public static int getTownHallExplosiveHitsRequired() {
                return TOWN_HALL_EXPLOSIVE_HITS_REQUIRED.get();
        }

        public static int getTownHallHitCooldownMinutes() {
                return TOWN_HALL_HIT_COOLDOWN_MINUTES.get();
        }

        public static int getMaxSiegeRadius() {
                return MAX_SIEGE_RADIUS.get();
        }

        public static int getAttackerGlowSeconds() {
                return ATTACKER_GLOW_SECONDS.get();
        }

        public static int getBannerCaptureMinutesOrDefault() {
                return BANNER_CAPTURE_MINUTES.get();
        }

        public static boolean isDeferRestorationToExplosiont() {
                return DEFER_RESTORATION_TO_EXPLOSIONT.get();
        }

        public static int getWarVassalizationDurationHours() {
                return WAR_VASSALIZATION_DURATION_HOURS.get();
        }

        public static int getWarVassalizationTributePercentage() {
                return WAR_VASSALIZATION_TRIBUTE_PERCENTAGE.get();
        }

        public static Set<com.minecolonies.api.colony.permissions.Action> getWarActions() {
                List<? extends String> actionsStr = CONFIGURABLE_WAR_ACTIONS.get();
                return actionsStr.stream()
                                .map(s -> {
                                        try {
                                                return com.minecolonies.api.colony.permissions.Action
                                                                .valueOf(s.toUpperCase());
                                        } catch (IllegalArgumentException e) {
                                                // Log error or handle invalid action string
                                                System.err.println("Invalid war action in config: " + s);
                                                return null;
                                        }
                                })
                                .filter(java.util.Objects::nonNull)
                                .collect(java.util.stream.Collectors.toSet());
        }

        public static Set<com.minecolonies.api.colony.permissions.Action> getRaidActions() {
                List<? extends String> actionsStr = CONFIGURABLE_RAID_ACTIONS.get();
                return actionsStr.stream()
                                .map(s -> {
                                        try {
                                                return com.minecolonies.api.colony.permissions.Action
                                                                .valueOf(s.toUpperCase());
                                        } catch (IllegalArgumentException e) {
                                                // Log error or handle invalid action string
                                                System.err.println("Invalid raid action in config: " + s);
                                                return null;
                                        }
                                })
                                .filter(java.util.Objects::nonNull)
                                .collect(java.util.stream.Collectors.toSet());
        }

        public static Set<com.minecolonies.api.colony.permissions.Action> getClaimingActions() {
                List<? extends String> actionsStr = CONFIGURABLE_CLAIMING_ACTIONS.get();
                return actionsStr.stream()
                                .map(s -> {
                                        try {
                                                return com.minecolonies.api.colony.permissions.Action
                                                                .valueOf(s.toUpperCase());
                                        } catch (IllegalArgumentException e) {
                                                // Log error or handle invalid claiming action string
                                                System.err.println("Invalid claiming action in config: " + s);
                                                return null;
                                        }
                                })
                                .filter(java.util.Objects::nonNull)
                                .collect(java.util.stream.Collectors.toSet());
        }

        public static int getLogLevel() {
                return LOG_LEVEL.get();
        }

        public static boolean isNormalLogging() {
                return getLogLevel() >= 1;
        }

        public static boolean isDebugLogging() {
                return getLogLevel() >= 2;
        }

        // Backward compatibility
        public static boolean showTaxGenerationLogs() {
                return isNormalLogging();
        }

        // Backward compatibility
        public static boolean showColonyInitializationLogs() {
                return isNormalLogging();
        }

        public static int getMinGuardsToBeRaided() {
                return MIN_GUARDS_TO_BE_RAIDED.get();
        }

        public static int getMinGuardTowersToBeRaided() {
                return MIN_GUARD_TOWERS_TO_BE_RAIDED.get();
        }

        public static boolean isRaidGuardProtectionEnabled() {
                return ENABLE_RAID_GUARD_PROTECTION.get();
        }

        public static boolean isEntityRaidsEnabled() {
                return ENABLE_ENTITY_RAIDS.get();
        }

        public static List<? extends String> getEntityRaidWhitelist() {
                return ENTITY_RAID_WHITELIST.get();
        }

        public static int getEntityRaidThreshold() {
                return ENTITY_RAID_THRESHOLD.get();
        }

        public static int getEntityRaidDetectionRadius() {
                return ENTITY_RAID_DETECTION_RADIUS.get();
        }

        public static boolean isEntityRaidMessageOnly() {
                return ENTITY_RAID_MESSAGE_ONLY.get();
        }

        public static int getEntityRaidBoundaryTimerSeconds() {
                return ENTITY_RAID_BOUNDARY_TIMER_SECONDS.get();
        }

        public static int getEntityRaidCheckIntervalTicks() {
                return ENTITY_RAID_CHECK_INTERVAL_TICKS.get();
        }

        public static int getEntityRaidCooldownMinutes() {
                return ENTITY_RAID_COOLDOWN_MINUTES.get();
        }

        public static boolean isEntityRaidDebugEnabled() {
                return ENABLE_ENTITY_RAID_DEBUG.get();
        }

        public static int getEntityRaidDebugLevel() {
                return ENTITY_RAID_DEBUG_LEVEL.get();
        }

        public static boolean shouldBypassAllianceChecks() {
                return BYPASS_ALLIANCE_CHECKS.get();
        }

        public static boolean isGeneralItemInteractionsEnabled() {
                return ENABLE_GENERAL_ITEM_INTERACTIONS.get();
        }

        public static List<? extends String> getGeneralColonyActions() {
                return GENERAL_COLONY_ACTIONS.get();
        }

        public static Set<com.minecolonies.api.colony.permissions.Action> getGeneralColonyActionSet() {
                List<? extends String> actionsStr = GENERAL_COLONY_ACTIONS.get();
                return actionsStr.stream()
                                .map(s -> {
                                        try {
                                                return com.minecolonies.api.colony.permissions.Action
                                                                .valueOf(s.toUpperCase());
                                        } catch (IllegalArgumentException e) {
                                                // Log error or handle invalid action string
                                                System.err.println("Invalid general colony action in config: " + s);
                                                return null;
                                        }
                                })
                                .filter(java.util.Objects::nonNull)
                                .collect(java.util.stream.Collectors.toSet());
        }

        public static boolean isEasyFactionsIntegrationEnabled() {
                return ENABLE_EASY_FACTIONS_INTEGRATION.get();
        }

        public static String getEasyFactionsMemberRank() {
                return EASY_FACTIONS_MEMBER_RANK.get();
        }

        public static boolean shouldPromoteEasyFactionsOfficers() {
                return EASY_FACTIONS_PROMOTE_OFFICERS.get();
        }

        public static int getEasyFactionsSyncIntervalTicks() {
                return EASY_FACTIONS_SYNC_INTERVAL_TICKS.get();
        }

        public static boolean isGuardResistanceDuringRaidsEnabled() {
                return ENABLE_GUARD_RESISTANCE_DURING_RAIDS.get();
        }

        public static int getGuardResistanceLevel() {
                return GUARD_RESISTANCE_LEVEL.get();
        }

        public static boolean isHappinessTaxModifierEnabled() {
                return ENABLE_HAPPINESS_TAX_MODIFIER.get();
        }

        public static boolean isColonyInactivityTaxPauseEnabled() {
                return ENABLE_COLONY_INACTIVITY_TAX_PAUSE.get();
        }

        public static int getColonyInactivityHoursThreshold() {
                return COLONY_INACTIVITY_HOURS_THRESHOLD.get();
        }

        public static double getHappinessTaxMultiplierMin() {
                return HAPPINESS_TAX_MULTIPLIER_MIN.get();
        }

        public static double getHappinessTaxMultiplierMax() {
                return HAPPINESS_TAX_MULTIPLIER_MAX.get();
        }

        /**
         * Calculate happiness-based tax multiplier for a colony.
         * 
         * @param avgHappiness Average happiness of adult citizens (0.0 - 10.0)
         * @return Tax multiplier (between min and max configured values)
         */
        public static double calculateHappinessTaxMultiplier(double avgHappiness) {
                if (!isHappinessTaxModifierEnabled()) {
                        return 1.0; // No modifier if feature disabled
                }

                // Clamp happiness to valid range
                avgHappiness = Math.max(0.0, Math.min(10.0, avgHappiness));

                double minMultiplier = getHappinessTaxMultiplierMin();
                double maxMultiplier = getHappinessTaxMultiplierMax();

                // Normalize happiness (0-10) to (0-1)
                double normalizedHappiness = avgHappiness / 10.0;

                // Linear interpolation between min and max multipliers
                return minMultiplier + (normalizedHappiness * (maxMultiplier - minMultiplier));
        }

        /**
         * Master switch for the automatic abandonment / owner-repair machinery.
         * When false the mod performs NO automatic writes to MineColonies owner/permission
         * state. The inactivity auto-abandon toggle ({@link #isColonyAutoAbandonEnabled()})
         * is a sub-feature and only takes effect when this master switch is also enabled.
         */
        public static boolean isColonyAbandonmentSystemEnabled() {
                return ENABLE_COLONY_ABANDONMENT_SYSTEM.get();
        }

        public static boolean isColonyAutoAbandonEnabled() {
                return ENABLE_COLONY_ABANDONMENT_SYSTEM.get() && ENABLE_COLONY_AUTO_ABANDON.get();
        }

        public static int getColonyAutoAbandonDays() {
                return COLONY_AUTO_ABANDON_DAYS.get();
        }

        public static boolean shouldNotifyOwnersBeforeAbandon() {
                return NOTIFY_OWNERS_BEFORE_ABANDON.get();
        }

        public static int getAbandonWarningDays() {
                return ABANDON_WARNING_DAYS.get();
        }

        public static boolean shouldResetTimerOnOfficerLogin() {
                return RESET_TIMER_ON_OFFICER_LOGIN.get();
        }

        public static boolean isAbandonedColonyClaimingEnabled() {
                return ENABLE_ABANDONED_COLONY_CLAIMING.get();
        }

        public static int getMinGuardsForClaimingRaid() {
                return MIN_GUARDS_FOR_CLAIMING_RAID.get();
        }

        public static int getClaimingRaidDurationMinutes() {
                return CLAIMING_RAID_DURATION_MINUTES.get();
        }

        public static int getClaimingGracePeriodHours() {
                return CLAIMING_GRACE_PERIOD_HOURS.get();
        }

        public static boolean shouldSpawnMercenariesIfLowDefenders() {
                return SPAWN_MERCENARIES_IF_LOW_DEFENDERS.get();
        }

        public static String getClaimingBuildingRequirements() {
                return CLAIMING_BUILDING_REQUIREMENTS.get();
        }

        public static boolean isRaidBuildingRequirementsEnabled() {
                return ENABLE_RAID_BUILDING_REQUIREMENTS.get();
        }

        public static String getRaidBuildingRequirements() {
                return RAID_BUILDING_REQUIREMENTS.get();
        }

        public static boolean isWarBuildingRequirementsEnabled() {
                return ENABLE_WAR_BUILDING_REQUIREMENTS.get();
        }

        public static String getWarBuildingRequirements() {
                return WAR_BUILDING_REQUIREMENTS.get();
        }

        public static boolean isDisableHutRecipesEnabled() {
                return DISABLE_HUT_RECIPES.get();
        }

        public static boolean isDatabaseEnabled() {
                return DATABASE_ENABLED.get();
        }

        public static String getDatabaseHost() {
                return DATABASE_HOST.get();
        }

        public static int getDatabasePort() {
                return DATABASE_PORT.get();
        }

        public static String getDatabaseName() {
                return DATABASE_NAME.get();
        }

        public static String getDatabaseUsername() {
                return DATABASE_USERNAME.get();
        }

        public static String getDatabasePassword() {
                return DATABASE_PASSWORD.get();
        }

        public static int getDatabasePoolSize() {
                return DATABASE_POOL_SIZE.get();
        }

        public static int getDatabaseSnapshotIntervalSeconds() {
                return DATABASE_SNAPSHOT_INTERVAL_SECONDS.get();
        }

        public static boolean isBlockInteractionFilterEnabled() {
                return ENABLE_BLOCK_INTERACTION_FILTER.get();
        }

        public static Set<String> getBlockInteractionBlacklist() {
                return Set.copyOf(BLOCK_INTERACTION_BLACKLIST.get());
        }

        public static Set<String> getBlockInteractionWhitelist() {
                return Set.copyOf(BLOCK_INTERACTION_WHITELIST.get());
        }

        public static boolean isBlockFilterWarsEnabled() {
                return BLOCK_FILTER_WARS.get();
        }

        public static boolean isBlockFilterRaidsEnabled() {
                return BLOCK_FILTER_RAIDS.get();
        }

        public static boolean isSuppressColonyLevitation() {
                return SUPPRESS_COLONY_LEVITATION.get();
        }

        // ============================================================
        // TAX EXPANSION: Getters
        // ============================================================

        // --- Tax Policies ---
        public static boolean isTaxPoliciesEnabled() {
                return ENABLE_TAX_POLICIES.get();
        }

        public static double getTaxPolicyLowRevenueModifier() {
                return TAX_POLICY_LOW_REVENUE_MODIFIER.get();
        }

        public static double getTaxPolicyLowHappinessModifier() {
                return TAX_POLICY_LOW_HAPPINESS_MODIFIER.get();
        }

        public static double getTaxPolicyHighRevenueModifier() {
                return TAX_POLICY_HIGH_REVENUE_MODIFIER.get();
        }

        public static double getTaxPolicyHighHappinessModifier() {
                return TAX_POLICY_HIGH_HAPPINESS_MODIFIER.get();
        }

        public static double getTaxPolicyWarRevenueModifier() {
                return TAX_POLICY_WAR_REVENUE_MODIFIER.get();
        }

        public static double getTaxPolicyWarHappinessModifier() {
                return TAX_POLICY_WAR_HAPPINESS_MODIFIER.get();
        }

        // --- Tax Reports ---
        public static boolean isTaxReportsEnabled() {
                return ENABLE_TAX_REPORTS.get();
        }

        public static int getTaxReportAutoDeleteHours() {
                return TAX_REPORT_AUTO_DELETE_HOURS.get();
        }

        public static int getTaxReportMaxHistoryDays() {
                return TAX_REPORT_MAX_HISTORY_DAYS.get();
        }

        // --- Leaderboards ---
        public static boolean isLeaderboardsEnabled() {
                return ENABLE_LEADERBOARDS.get();
        }

        public static int getLeaderboardUpdateIntervalMinutes() {
                return LEADERBOARD_UPDATE_INTERVAL_MINUTES.get();
        }

        public static int getLeaderboardTopCount() {
                return LEADERBOARD_TOP_COUNT.get();
        }

        // --- Faction System ---
        public static boolean isFactionSystemEnabled() {
                return ENABLE_FACTION_SYSTEM.get();
        }

        public static int getMaxFactionMembers() {
                return MAX_FACTION_MEMBERS.get();
        }

        public static int getFactionCreationCost() {
                return FACTION_CREATION_COST.get();
        }

        public static int getFactionAllianceLimit() {
                return FACTION_ALLIANCE_LIMIT.get();
        }

        // --- Shared Tax Pool ---
        public static boolean isSharedTaxPoolEnabled() {
                return ENABLE_SHARED_TAX_POOL.get();
        }

        public static int getDefaultPoolContributionPercent() {
                return DEFAULT_POOL_CONTRIBUTION_PERCENT.get();
        }

        public static int getMaxPoolBalance() {
                return MAX_POOL_BALANCE.get();
        }

        public static int getPoolHistoryRetentionDays() {
                return POOL_HISTORY_RETENTION_DAYS.get();
        }

        // --- Trade Routes ---
        public static boolean isTradeRoutesEnabled() {
                return ENABLE_TRADE_ROUTES.get();
        }

        public static int getTradeRouteIncomePerChunk() {
                return TRADE_ROUTE_INCOME_PER_CHUNK.get();
        }

        public static int getTradeRouteMaxDistanceChunks() {
                return TRADE_ROUTE_MAX_DISTANCE_CHUNKS.get();
        }

        public static int getTradeRouteMaintenanceCost() {
                return TRADE_ROUTE_MAINTENANCE_COST.get();
        }

        public static int getMaxTradeRoutesPerColony() {
                return MAX_TRADE_ROUTES_PER_COLONY.get();
        }

        // --- Spy System ---
        public static boolean isSpySystemEnabled() {
                return ENABLE_SPY_SYSTEM.get();
        }

        public static int getSpyCooldownMinutes() {
                return SPY_COOLDOWN_MINUTES.get();
        }

        public static double getSpyDetectionBaseChance() {
                return SPY_DETECTION_BASE_CHANCE.get();
        }

        public static int getSpyScoutCost() {
                return SPY_SCOUT_COST.get();
        }

        public static int getSpySabotageCost() {
                return SPY_SABOTAGE_COST.get();
        }

        public static int getSpyBribeGuardsCost() {
                return SPY_BRIBE_GUARDS_COST.get();
        }

        public static int getSpyStealSecretsCost() {
                return SPY_STEAL_SECRETS_COST.get();
        }

        public static double getSpySabotageTaxReduction() {
                return SPY_SABOTAGE_TAX_REDUCTION_PERCENT.get();
        }

        public static int getSpyBribeGuardsDisabledCount() {
                return SPY_BRIBE_GUARDS_DISABLED_COUNT.get();
        }

        public static int getSpyStealSecretsDurationHours() {
                return SPY_STEAL_SECRETS_DURATION_HOURS.get();
        }

        public static int getSpySabotageMinFieldMinutes() {
                return SPY_SABOTAGE_MIN_FIELD_MINUTES.get();
        }

        public static int getSpyScoutMaxDurationHours() {
                return SPY_SCOUT_MAX_DURATION_HOURS.get();
        }

        public static double getMinTaxGenerationPercent() {
                return MIN_TAX_GENERATION_PERCENT.get();
        }

        public static int getWarTaxFreezeCycles() {
                return WAR_TAX_FREEZE_CYCLES.get();
        }

        public static int getSpyMaxActivePerPlayer() {
                return SPY_MAX_ACTIVE_PER_PLAYER.get();
        }

        public static double getSpySabotageTaxReductionPercent() {
                return SPY_SABOTAGE_TAX_REDUCTION_PERCENT.get();
        }

        public static double getSpyScoutDetectionChance() {
                return SPY_SCOUT_DETECTION_CHANCE.get();
        }

        public static double getSpySabotageDetectionChance() {
                return SPY_SABOTAGE_DETECTION_CHANCE.get();
        }

        public static double getSpyBribeGuardsDetectionChance() {
                return SPY_BRIBE_GUARDS_DETECTION_CHANCE.get();
        }

        public static double getSpyStealSecretsDetectionChance() {
                return SPY_STEAL_SECRETS_DETECTION_CHANCE.get();
        }

        // Progressive Intel Thresholds
        public static int getIntelEarlyThresholdMinutes() {
                return INTEL_EARLY_THRESHOLD_MINUTES.get();
        }

        public static int getIntelMidThresholdMinutes() {
                return INTEL_MID_THRESHOLD_MINUTES.get();
        }

        public static int getIntelLateThresholdMinutes() {
                return INTEL_LATE_THRESHOLD_MINUTES.get();
        }

        // Flee Behavior
        public static double getFleeSpeedMultiplier() {
                return FLEE_SPEED_MULTIPLIER.get();
        }

        public static int getFleeEscapeDistance() {
                return FLEE_ESCAPE_DISTANCE.get();
        }

        public static int getFleeMaxSeconds() {
                return FLEE_MAX_SECONDS.get();
        }

        // Spy Travel Phase
        public static int getSpyTravelBlocksPerMinute() {
                return SPY_TRAVEL_BLOCKS_PER_MINUTE.get();
        }

        public static int getSpyTravelMinMinutes() {
                return SPY_TRAVEL_MIN_MINUTES.get();
        }

        public static int getSpyTravelMaxMinutes() {
                return SPY_TRAVEL_MAX_MINUTES.get();
        }

        // Spy Map
        public static boolean isSpyMapEnabled() {
                return SPY_MAP_ENABLED.get();
        }

        public static int getSpyMapScale() {
                return SPY_MAP_SCALE.get();
        }

        // --- Treasury ---
        public static boolean isTreasuryEnabled() {
                return ENABLE_TREASURY.get();
        }

        public static double getTreasuryMinPercentOfTarget() {
                return TREASURY_MIN_PERCENT_OF_TARGET.get();
        }

        public static double getTreasuryMinPercentOfOwnTax() {
                return TREASURY_MIN_PERCENT_OF_OWN_TAX.get();
        }

        public static int getTreasuryDrainPerMinute() {
                return TREASURY_DRAIN_PER_MINUTE.get();
        }

        public static boolean isTreasuryDrainUsePercent() {
                return TREASURY_DRAIN_USE_PERCENT.get();
        }

        public static double getTreasuryDrainPercent() {
                return TREASURY_DRAIN_PERCENT.get();
        }

        public static int getTreasuryMaxCapacity() {
                return TREASURY_MAX_CAPACITY.get();
        }

        public static boolean isTreasuryAutoSurrenderEnabled() {
                return TREASURY_AUTO_SURRENDER_ENABLED.get();
        }

        public static double getTreasuryAutoDepositPercent() {
                return TREASURY_AUTO_DEPOSIT_PERCENT.get();
        }

        public static double getWarDefenderDrainReduction() {
                return WAR_DEFENDER_DRAIN_REDUCTION.get();
        }

        public static boolean isRaidTreasuryEnabled() {
                return RAID_TREASURY_ENABLED.get();
        }

        public static double getRaidTreasuryCostPercent() {
                return RAID_TREASURY_COST_PERCENT.get();
        }

        // --- Raid Penalties ---
        public static double getRaidPenaltyTaxReductionPercent() {
                return RAID_PENALTY_TAX_REDUCTION_PERCENT.get();
        }

        public static int getRaidPenaltyDurationHours() {
                return RAID_PENALTY_DURATION_HOURS.get();
        }

        public static double getRaidRepairCostPercent() {
                return RAID_REPAIR_COST_PERCENT.get();
        }

        // --- War Exhaustion ---
        public static boolean isWarExhaustionEnabled() {
                return ENABLE_WAR_EXHAUSTION.get();
        }

        public static double getWarTaxReductionPercent() {
                return WAR_TAX_REDUCTION_PERCENT.get();
        }

        public static int getPostWarRecoveryHours() {
                return POST_WAR_RECOVERY_HOURS.get();
        }

        // --- War Reparations ---
        public static boolean isWarReparationsEnabled() {
                return ENABLE_WAR_REPARATIONS.get();
        }

        public static double getReparationsTaxPenaltyPercent() {
                return REPARATIONS_TAX_PENALTY_PERCENT.get();
        }

        public static int getReparationsDurationHours() {
                return REPARATIONS_DURATION_HOURS.get();
        }

        public static int getReparationsTriggerLossesCount() {
                return REPARATIONS_TRIGGER_LOSSES_COUNT.get();
        }

        // --- War Weariness ---
        public static boolean isWarWearinessEnabled() {
                return ENABLE_WAR_WEARINESS.get();
        }

        public static int getWarWearinessThreshold() {
                return WAR_WEARINESS_THRESHOLD.get();
        }

        public static int getWarWearinessImmunityHours() {
                return WAR_WEARINESS_IMMUNITY_HOURS.get();
        }

        // --- Vassalization Economy ---
        public static boolean isVassalizationReplacesReparations() {
                return VASSALIZATION_REPLACES_REPARATIONS.get();
        }

        // --- Ransom System ---
        public static boolean isRansomSystemEnabled() {
                return ENABLE_RANSOM_SYSTEM.get();
        }

        public static double getRansomDefaultPercent() {
                return RANSOM_DEFAULT_PERCENT.get();
        }

        public static int getRansomMinAmount() {
                return RANSOM_MIN_AMOUNT.get();
        }

        public static int getRansomMaxAmount() {
                return RANSOM_MAX_AMOUNT.get();
        }

        public static int getRansomTimeoutSeconds() {
                return RANSOM_TIMEOUT_SECONDS.get();
        }

        public static int getRansomCooldownMinutes() {
                return RANSOM_COOLDOWN_MINUTES.get();
        }

        public static int getRansomImmunityAfterPaymentHours() {
                return RANSOM_IMMUNITY_AFTER_PAYMENT_HOURS.get();
        }

        // --- Investment System ---
        public static boolean isInvestmentsEnabled() {
                return ENABLE_INVESTMENTS.get();
        }

        public static double getInvestmentDiminishingReturnsFactor() {
                return INVESTMENT_DIMINISHING_RETURNS_FACTOR.get();
        }

        // Infrastructure
        public static int getInvestmentInfrastructureCost() {
                return INVESTMENT_INFRASTRUCTURE_COST.get();
        }

        public static double getInvestmentInfrastructureBonus() {
                return INVESTMENT_INFRASTRUCTURE_BONUS.get();
        }

        public static int getInvestmentInfrastructureMaxStacks() {
                return INVESTMENT_INFRASTRUCTURE_MAX_STACKS.get();
        }

        // Guard Training
        public static int getInvestmentGuardTrainingCost() {
                return INVESTMENT_GUARD_TRAINING_COST.get();
        }

        public static double getInvestmentGuardTrainingDamageBonus() {
                return INVESTMENT_GUARD_TRAINING_DAMAGE_BONUS.get();
        }

        public static int getInvestmentGuardTrainingDurationHours() {
                return INVESTMENT_GUARD_TRAINING_DURATION_HOURS.get();
        }

        // Festival
        public static int getInvestmentFestivalCost() {
                return INVESTMENT_FESTIVAL_COST.get();
        }

        public static double getInvestmentFestivalHappinessBonus() {
                return INVESTMENT_FESTIVAL_HAPPINESS_BONUS.get();
        }

        public static int getInvestmentFestivalDurationHours() {
                return INVESTMENT_FESTIVAL_DURATION_HOURS.get();
        }

        // Research
        public static int getInvestmentResearchCost() {
                return INVESTMENT_RESEARCH_COST.get();
        }

        public static double getInvestmentResearchSpyDefenseBonus() {
                return INVESTMENT_RESEARCH_SPY_DEFENSE_BONUS.get();
        }

        public static int getInvestmentResearchDurationDays() {
                return INVESTMENT_RESEARCH_DURATION_DAYS.get();
        }

        public static boolean isListAbandonedForAllEnabled() {
                return ENABLE_LIST_ABANDONED_FOR_ALL.get();
        }

        // ==================== RANDOM EVENTS GETTERS ====================

        public static boolean isRandomEventsEnabled() {
                return ENABLE_RANDOM_EVENTS.get();
        }

        public static int getEventCheckFrequency() {
                return RANDOM_EVENT_CHECK_FREQUENCY.get();
        }

        public static int getGlobalCooldownCycles() {
                return RANDOM_EVENT_GLOBAL_COOLDOWN_CYCLES.get();
        }

        public static int getMaxSimultaneousEvents() {
                return RANDOM_EVENT_MAX_SIMULTANEOUS.get();
        }

        public static double getBaseChanceMultiplier() {
                return RANDOM_EVENT_BASE_CHANCE_MULTIPLIER.get();
        }

        public static int getNewColonyProtectionHours() {
                return RANDOM_EVENT_PROTECT_NEW_COLONIES_HOURS.get();
        }

        // Individual Event Toggles
        public static boolean isMerchantCaravanEnabled() {
                return ENABLE_MERCHANT_CARAVAN.get();
        }

        public static boolean isBountifulHarvestEnabled() {
                return ENABLE_BOUNTIFUL_HARVEST.get();
        }

        public static boolean isCulturalFestivalEnabled() {
                return ENABLE_CULTURAL_FESTIVAL.get();
        }

        public static boolean isSuccessfulRecruitmentEnabled() {
                return ENABLE_SUCCESSFUL_RECRUITMENT.get();
        }

        public static boolean isFoodShortageEnabled() {
                return ENABLE_FOOD_SHORTAGE.get();
        }

        public static boolean isDiseaseOutbreakEnabled() {
                return ENABLE_DISEASE_OUTBREAK.get();
        }

        public static boolean isBanditHarassmentEnabled() {
                return ENABLE_BANDIT_HARASSMENT.get();
        }

        public static boolean isCorruptOfficialEnabled() {
                return ENABLE_CORRUPT_OFFICIAL.get();
        }

        public static boolean isWanderingTraderOfferEnabled() {
                return ENABLE_WANDERING_TRADER_OFFER.get();
        }

        public static boolean isNeighboringAllianceEnabled() {
                return ENABLE_NEIGHBORING_ALLIANCE.get();
        }

        public static boolean isWarProfiteeringEnabled() {
                return ENABLE_WAR_PROFITEERING.get();
        }

        public static boolean isGuardDesertionEnabled() {
                return ENABLE_GUARD_DESERTION.get();
        }

        public static boolean isLaborStrikeEnabled() {
                return ENABLE_LABOR_STRIKE.get();
        }

        public static boolean isPlagueOutbreakEnabled() {
                return ENABLE_PLAGUE_OUTBREAK.get();
        }

        public static boolean isRoyalFeastEnabled() {
                return ENABLE_ROYAL_FEAST.get();
        }

        public static boolean isCropBlightEnabled() {
                return ENABLE_CROP_BLIGHT.get();
        }

        // Besiege System Getters
        public static boolean isBesiegeSystemEnabled() {
                return ENABLE_BESIEGE_SYSTEM.get();
        }

        public static int getBesiegeDurationMinutes() {
                return BESIEGE_DURATION_MINUTES.get();
        }

        public static int getBesiegeCooldownHours() {
                return BESIEGE_COOLDOWN_HOURS.get();
        }

        public static double getBesiegeMilitiaPercent() {
                return BESIEGE_MILITIA_PERCENT.get();
        }

        public static int getBesiegeTributePercent() {
                return BESIEGE_TRIBUTE_PERCENT.get();
        }

        public static int getBesiegeTributeDurationHours() {
                return BESIEGE_TRIBUTE_DURATION_HOURS.get();
        }

        public static int getBesiegeMinColonySize() {
                return BESIEGE_MIN_COLONY_SIZE.get();
        }

        public static boolean isBesiegeAlliesEnabled() {
                return BESIEGE_ALLIES_ENABLED.get();
        }

        public static double getBesiegeExtraMercenariesPerBuilding() {
                return BESIEGE_EXTRA_MERCENARIES_PER_BUILDING.get();
        }

        public static int getBesiegeMaxMercenaries() {
                return BESIEGE_MAX_MERCENARIES.get();
        }

        public static int getBesiegePlayerStayRadius() {
                return BESIEGE_PLAYER_STAY_RADIUS.get();
        }

        // Colony Upgrades
        public static boolean isUpgradesEnabled() { return ENABLE_COLONY_UPGRADES.get(); }
        public static int getUpgradeMaxLevel() { return UPGRADE_MAX_LEVEL.get(); }
        public static int getUpgradeMilitiaCostBase() { return UPGRADE_MILITIA_COST_BASE.get(); }
        public static int getUpgradeSpyCapacityCostBase() { return UPGRADE_SPY_CAPACITY_COST_BASE.get(); }
        public static int getUpgradeSpySpeedCostBase() { return UPGRADE_SPY_SPEED_COST_BASE.get(); }
        public static int getUpgradeSpyEvasionCostBase() { return UPGRADE_SPY_EVASION_COST_BASE.get(); }
        public static int getUpgradeRaidForceCostBase() { return UPGRADE_RAID_FORCE_COST_BASE.get(); }
        public static int getUpgradeDefenseCostBase() { return UPGRADE_DEFENSE_COST_BASE.get(); }
        public static double getUpgradeCostScalingFactor() { return UPGRADE_COST_SCALING_FACTOR.get(); }
        public static double getUpgradeMilitiaMultiplierPerLevel() { return UPGRADE_MILITIA_MULTIPLIER_PER_LEVEL.get(); }
        public static int getUpgradeSpyCapacityPerLevel() { return UPGRADE_SPY_CAPACITY_PER_LEVEL.get(); }
        public static double getUpgradeSpySpeedMultiplierPerLevel() { return UPGRADE_SPY_SPEED_MULTIPLIER_PER_LEVEL.get(); }
        public static double getUpgradeDetectionReductionPerLevel() { return UPGRADE_DETECTION_REDUCTION_PER_LEVEL.get(); }
        public static int getUpgradeDefenseBonusPerLevel() { return UPGRADE_DEFENSE_BONUS_PER_LEVEL.get(); }
        public static int getUpgradeTreasuryCapCostBase() { return UPGRADE_TREASURY_CAP_COST_BASE.get(); }
        public static int getUpgradeTaxEfficiencyCostBase() { return UPGRADE_TAX_EFFICIENCY_COST_BASE.get(); }
        public static int getUpgradeFortificationCostBase() { return UPGRADE_FORTIFICATION_COST_BASE.get(); }
        public static int getUpgradeCounterIntelCostBase() { return UPGRADE_COUNTER_INTEL_COST_BASE.get(); }
        public static int getUpgradeTreasuryCapFlatPerLevel() { return UPGRADE_TREASURY_CAP_FLAT_PER_LEVEL.get(); }
        public static double getUpgradeTaxEfficiencyPercentPerLevel() { return UPGRADE_TAX_EFFICIENCY_PERCENT_PER_LEVEL.get(); }
        public static double getUpgradeFortificationDamageReductionPerLevel() { return UPGRADE_FORTIFICATION_DAMAGE_REDUCTION_PER_LEVEL.get(); }
        public static double getUpgradeCounterIntelDetectChancePerLevel() { return UPGRADE_COUNTER_INTEL_DETECT_CHANCE_PER_LEVEL.get(); }
        public static double getUpgradeRaidForceMultiplierPerLevel() { return UPGRADE_RAID_FORCE_MULTIPLIER_PER_LEVEL.get(); }
}
