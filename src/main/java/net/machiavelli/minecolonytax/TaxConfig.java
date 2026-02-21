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
        public static final ForgeConfigSpec.ConfigValue<String> CURRENCY_ITEM_NAME;
        public static final ForgeConfigSpec.IntValue DEBT_LIMIT;
        public static final ForgeConfigSpec.IntValue TAX_STEAL_PER_GUARD;
        public static final ForgeConfigSpec.IntValue MIN_GUARDS_TO_RAID;
        public static final ForgeConfigSpec.IntValue MAX_TAX_REVENUE;
        public static final ForgeConfigSpec.BooleanValue ENABLE_COLONY_TRANSFER;

        // Maps for storing building taxes and upgrade taxes
        public static final Map<String, ForgeConfigSpec.DoubleValue> BUILDING_TAXES = new HashMap<>();
        public static final Map<String, ForgeConfigSpec.DoubleValue> UPGRADE_TAXES = new HashMap<>();

        // Map to link full building class names to short config names
        private static final Map<String, String> CLASS_NAME_TO_SHORT_NAME = new HashMap<>();

        // Define the tax interval in minutes
        public static final ForgeConfigSpec.IntValue TAX_INTERVAL_MINUTES;

        public static final ForgeConfigSpec.IntValue ATTACKER_GRACE_PERIOD_MINUTES;
        public static final ForgeConfigSpec.IntValue RAID_GRACE_PERIOD_MINUTES;
        public static final ForgeConfigSpec.IntValue MAX_RAID_DURATION_MINUTES;
        public static final ForgeConfigSpec.IntValue RAID_TAX_INTERVAL_SECONDS;
        public static final ForgeConfigSpec.ConfigValue<List<Double>> RAID_TAX_PERCENTAGES;
        public static final ForgeConfigSpec.IntValue WAR_DURATION_MINUTES;
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

        // War Vassalization Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_VASSALIZATION;
        public static final ForgeConfigSpec.IntValue WAR_VASSALIZATION_DURATION_HOURS;
        public static final ForgeConfigSpec.IntValue WAR_VASSALIZATION_TRIBUTE_PERCENTAGE;
        public static final ForgeConfigSpec.IntValue JOIN_PHASE_DURATION_MINUTES;
        public static final ForgeConfigSpec.BooleanValue WAR_ACCEPTANCE_REQUIRED;
        public static final ForgeConfigSpec.BooleanValue KEEP_INVENTORY_ON_LAST_LIFE;

        public static final ForgeConfigSpec.IntValue REQUIRED_GUARD_TOWERS_FOR_BOOST;
        public static final ForgeConfigSpec.DoubleValue GUARD_TOWER_TAX_BOOST_PERCENTAGE;

        public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_ACTIONS;
        public static final ForgeConfigSpec.IntValue PLAYER_LIVES_IN_WAR; // New config

        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CONFIGURABLE_WAR_ACTIONS;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CONFIGURABLE_RAID_ACTIONS;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CONFIGURABLE_CLAIMING_ACTIONS;

        // Block Interaction Filter Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_INTERACTION_FILTER;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_INTERACTION_BLACKLIST;
        public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_INTERACTION_WHITELIST;
        public static final ForgeConfigSpec.BooleanValue BLOCK_FILTER_WARS;
        public static final ForgeConfigSpec.BooleanValue BLOCK_FILTER_RAIDS;

        // PvP Arena Settings
        public static final ForgeConfigSpec.BooleanValue PVP_COMMANDS_IN_BATTLE_ENABLED;
        public static final ForgeConfigSpec.IntValue CHALLENGE_COOLDOWN_SECONDS;
        public static final ForgeConfigSpec.IntValue TEAM_BATTLE_COOLDOWN_SECONDS;
        public static final ForgeConfigSpec.IntValue BATTLE_DURATION_SECONDS;
        public static final ForgeConfigSpec.IntValue TEAM_BATTLE_START_COUNTDOWN_SECONDS;
        public static final ForgeConfigSpec.IntValue BATTLE_END_COUNTDOWN_SECONDS;
        public static final ForgeConfigSpec.BooleanValue PVP_DISABLE_FRIENDLY_FIRE;

        // Logging configuration
        public static final ForgeConfigSpec.BooleanValue SHOW_TAX_GENERATION_LOGS;
        public static final ForgeConfigSpec.BooleanValue SHOW_COLONY_INITIALIZATION_LOGS;

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

        // Web API Configuration
        public static final ForgeConfigSpec.BooleanValue ENABLE_WEB_API;
        public static final ForgeConfigSpec.IntValue WEB_API_PORT;
        public static final ForgeConfigSpec.ConfigValue<String> WEB_API_KEY;
        public static final ForgeConfigSpec.IntValue WEB_API_RATE_LIMIT_REQUESTS_PER_MINUTE;
        public static final ForgeConfigSpec.BooleanValue WEB_API_REQUIRE_AUTHENTICATION;
        public static final ForgeConfigSpec.BooleanValue WEB_API_ENABLE_OFFLINE_PLAYERS;
        public static final ForgeConfigSpec.IntValue WEB_API_CACHE_REFRESH_MINUTES;

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

        // Spy Action Detection Chances
        public static final ForgeConfigSpec.DoubleValue SPY_SCOUT_DETECTION_CHANCE;
        public static final ForgeConfigSpec.DoubleValue SPY_SABOTAGE_DETECTION_CHANCE;
        public static final ForgeConfigSpec.DoubleValue SPY_BRIBE_GUARDS_DETECTION_CHANCE;
        public static final ForgeConfigSpec.DoubleValue SPY_STEAL_SECRETS_DETECTION_CHANCE;

        // ========== TAX EXPANSION: War Chest ==========
        public static final ForgeConfigSpec.BooleanValue ENABLE_WAR_CHEST;
        public static final ForgeConfigSpec.DoubleValue WAR_CHEST_MIN_PERCENT_OF_TARGET;
        public static final ForgeConfigSpec.IntValue WAR_CHEST_DRAIN_PER_MINUTE;
        public static final ForgeConfigSpec.IntValue WAR_CHEST_MAX_CAPACITY;
        public static final ForgeConfigSpec.BooleanValue WAR_CHEST_AUTO_SURRENDER_ENABLED;
        public static final ForgeConfigSpec.DoubleValue WAR_CHEST_AUTO_DEPOSIT_PERCENT;

        // Raid War Chest
        public static final ForgeConfigSpec.BooleanValue RAID_WAR_CHEST_ENABLED;
        public static final ForgeConfigSpec.DoubleValue RAID_WAR_CHEST_COST_PERCENT;

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

                // Define general settings
                BUILDER.push("General");
                TAX_INTERVAL_MINUTES = BUILDER.comment("Tax generation interval in minutes")
                                .defineInRange("TaxIntervalMinutes", 60, 1, 1440); // Default 60 minutes, min 1, max
                                                                                   // 1440 (1 day)

                MAX_TAX_REVENUE = BUILDER.comment(
                                "Maximum tax revenue a colony can store before it stops generating further taxes")
                                .defineInRange("MaxTaxRevenue", 5000, 1, Integer.MAX_VALUE);

                ENABLE_SDM_SHOP_CONVERSION = BUILDER
                                .comment("Enable SDMShop conversion (true = enable, false = disable).")
                                .define("EnableSDMShopConversion", true);

                CURRENCY_ITEM_NAME = BUILDER
                                .comment("The item name for the custom currency (e.g., 'minecraft:emerald').")
                                .define("CurrencyItemName", "minecraft:emerald");

                SHOW_TAX_GENERATION_LOGS = BUILDER.comment(
                                "Enable console logging of tax generation details (building upgrades, max warnings, etc.). "
                                                +
                                                "Set to false to reduce console spam during initialization.")
                                .define("ShowTaxGenerationLogs", true);

                SHOW_COLONY_INITIALIZATION_LOGS = BUILDER
                                .comment("Enable console logging during colony building initialization. " +
                                                "Set to false to reduce console spam during server startup. Colony initialization will still occur, just with less verbose logging.")
                                .define("ShowColonyInitializationLogs", true);

                BUILDER.pop();

                DEBT_LIMIT = BUILDER.comment("Optional debt limit for colony debt. " +
                                "If > 0, colony revenue will not deduct more once the debt reaches this limit (i.e. the tax value won't drop below -DebtLimit). Set to 0 to disable.")
                                .defineInRange("DebtLimit", 0, 0, Integer.MAX_VALUE);

                TAX_STEAL_PER_GUARD = BUILDER.comment(
                                "Amount of debt added to colony per guard killed when raiding colonies in debt. " +
                                                "Only applies when DebtLimit > 0. Raiders get this amount when killing guards in debt colonies. Default: 200.")
                                .defineInRange("TaxStealPerGuard", 200, 1, 10000);

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
                                "Percentage of losing players' balance awarded to each winning player. Set to 0.0 to only enable colony transfer (if enabled).\n"
                                                +
                                                "Uses SDMShop balance or colony funds based on what's configured.")
                                .defineInRange("WarVictoryPercentage", 0.25, 0.0, 1.0);

                WAR_DEFEAT_PERCENTAGE = BUILDER.comment(
                                "Percentage that each losing player loses from their balance when defeated in war.\n" +
                                                "Uses SDMShop balance or colony funds based on what's configured.")
                                .defineInRange("WarDefeatPercentage", 0.15, 0.0, 1.0);

                WAR_STALEMATE_PERCENTAGE = BUILDER.comment(
                                "Percentage that all war participants lose from their balance when a war ends in stalemate.\n"
                                                +
                                                "Uses SDMShop balance or colony funds based on what's configured.")
                                .defineInRange("WarStalematePercentage", 0.10, 0.0, 1.0);

                WAR_TAX_FREEZE_HOURS = BUILDER.comment(
                                "Duration (in hours) to freeze colony tax generation after a war loss or stalemate.\n" +
                                                "Set to 0 to disable tax freezing.")
                                .defineInRange("WarTaxFreezeHours", 0, 0, 168); // Max 1 week

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
                                                "This is the tribute rate enforced when a colony is vassalized through war.")
                                .defineInRange("WarVassalizationTributePercentage", 25, 1, 100); // Default 25%

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
                                .comment("Tax transfer percentages during raids (comma-separated decimals)")
                                .define("RaidTaxPercentages", List.of(0.1, 0.25, 0.5, 0.7));

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
                                "Maximum percentage of colony tax that can be stolen during a raid (0.0 - 1.0). This amount is distributed across all guards/militia. For example: 0.5 = 50% max tax stolen when all defenders are killed.")
                                .defineInRange("MaxRaidTaxPercentage", 0.5, 0.0, 1.0);

                APPLY_RESISTANCE_TO_CITIZENS = BUILDER.comment(
                                "If true, resistance effects during raids/wars will also be applied to all citizens, not just guards. This makes the entire colony more defensive.")
                                .define("ApplyResistanceToCitizens", false);

                BUILDER.pop();

                // ========== Colony Auto-Abandon Settings ==========
                BUILDER.push("Colony Auto-Abandon");

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

                // ========== Web API Settings ==========
                BUILDER.push("Web API");

                ENABLE_WEB_API = BUILDER.comment("Enable the Web API server for external data access. " +
                                "When enabled, war statistics and other data can be queried via HTTP REST endpoints. " +
                                "IMPORTANT: Only enable this if you understand the security implications. " +
                                "The API runs SERVER-SIDE ONLY and requires proper port forwarding if accessed from outside your network. "
                                +
                                "Default: false (disabled)")
                                .define("EnableWebAPI", false);

                WEB_API_PORT = BUILDER.comment("Port number for the Web API server. " +
                                "Make sure this port is not already in use by another application. " +
                                "Common ports: 8080, 8090, 9000. Avoid ports below 1024 (requires admin privileges). " +
                                "You may need to configure port forwarding on your router for external access. " +
                                "Default: 8090")
                                .defineInRange("WebAPIPort", 8090, 1024, 65535);

                WEB_API_KEY = BUILDER.comment("API authentication key for secure access. " +
                                "This key must be provided in the 'X-API-Key' header for all requests when authentication is enabled. "
                                +
                                "Generate a strong, random key (recommended: 32+ characters). " +
                                "Example: 'my-super-secret-api-key-12345-abcdef' " +
                                "SECURITY WARNING: Keep this key private! Anyone with this key can access your server data. "
                                +
                                "Default: empty (you must set this to enable authentication)")
                                .define("WebAPIKey", "");

                WEB_API_RATE_LIMIT_REQUESTS_PER_MINUTE = BUILDER
                                .comment("Maximum number of API requests allowed per IP address per minute. " +
                                                "Prevents abuse and excessive server load from a single source. " +
                                                "Set to 0 to disable rate limiting (not recommended for public servers). "
                                                +
                                                "Default: 60 (1 request per second average)")
                                .defineInRange("WebAPIRateLimitRequestsPerMinute", 60, 0, 1000);

                WEB_API_REQUIRE_AUTHENTICATION = BUILDER.comment("Require API key authentication for all requests. " +
                                "When enabled, requests without a valid 'X-API-Key' header will be rejected with 401 Unauthorized. "
                                +
                                "When disabled, anyone can query the API (use with caution!). " +
                                "SECURITY: Always enable this for public servers. Only disable for local testing. " +
                                "Default: true (authentication required)")
                                .define("WebAPIRequireAuthentication", true);

                WEB_API_ENABLE_OFFLINE_PLAYERS = BUILDER.comment("Enable offline player data in API responses. " +
                                "When enabled, the API can return statistics for offline players by scanning player data files. "
                                +
                                "This requires periodic file scanning and caching, which uses more memory and disk I/O. "
                                +
                                "Use the 'includeOffline=true' query parameter to request offline data. " +
                                "PERFORMANCE: Only enable if you need offline player stats on your website. " +
                                "Default: false (online players only)")
                                .define("WebAPIEnableOfflinePlayers", false);

                WEB_API_CACHE_REFRESH_MINUTES = BUILDER
                                .comment("How often to refresh the offline player data cache (in minutes). " +
                                                "The server will scan player data files at this interval to update offline player statistics. "
                                                +
                                                "Lower values = more current data but higher disk I/O. Higher values = less load but stale data. "
                                                +
                                                "Only used when 'WebAPIEnableOfflinePlayers' is true. " +
                                                "Recommended: 5-15 minutes for active servers, 30-60 minutes for larger servers. "
                                                +
                                                "Default: 10 minutes")
                                .defineInRange("WebAPICacheRefreshMinutes", 10, 1, 1440);

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
                                "Happiness modifier for LOW tax policy. Positive = happier citizens. Example: 0.20 = 20% faster happiness growth.")
                                .defineInRange("LowPolicyHappinessModifier", 0.20, 0.0, 1.0);

                TAX_POLICY_HIGH_REVENUE_MODIFIER = BUILDER.comment(
                                "Revenue modifier for HIGH tax policy. Positive = more tax. Example: 0.25 = 25% more revenue.")
                                .defineInRange("HighPolicyRevenueModifier", 0.25, 0.0, 1.0);

                TAX_POLICY_HIGH_HAPPINESS_MODIFIER = BUILDER.comment(
                                "Happiness modifier for HIGH tax policy. Negative = unhappier citizens. Example: -0.15 = 15% slower happiness.")
                                .defineInRange("HighPolicyHappinessModifier", -0.15, -1.0, 0.0);

                TAX_POLICY_WAR_REVENUE_MODIFIER = BUILDER.comment(
                                "Revenue modifier for WAR ECONOMY policy. High boost during wartime. Example: 0.50 = 50% more revenue.")
                                .defineInRange("WarPolicyRevenueModifier", 0.50, 0.0, 2.0);

                TAX_POLICY_WAR_HAPPINESS_MODIFIER = BUILDER.comment(
                                "Happiness modifier for WAR ECONOMY policy. Citizens dislike war economy. Example: -0.25 = 25% happiness penalty.")
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
                                .defineInRange("FactionCreationCost", 1000, 0, 100000);

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
                                .define("EnableTradeRoutes", true);

                TRADE_ROUTE_INCOME_PER_CHUNK = BUILDER.comment(
                                "Tax income generated per chunk of distance on a trade route (per tax cycle).")
                                .defineInRange("TradeRouteIncomePerChunk", 5, 1, 100);

                TRADE_ROUTE_MAX_DISTANCE_CHUNKS = BUILDER.comment(
                                "Maximum distance in chunks for a trade route. Longer routes = more income but higher maintenance.")
                                .defineInRange("TradeRouteMaxDistanceChunks", 1000, 10, 10000);

                TRADE_ROUTE_MAINTENANCE_COST = BUILDER.comment(
                                "Maintenance cost per trade route per tax cycle.")
                                .defineInRange("TradeRouteMaintenanceCost", 50, 0, 10000);

                MAX_TRADE_ROUTES_PER_COLONY = BUILDER.comment(
                                "Maximum number of trade routes a colony can have active.")
                                .defineInRange("MaxTradeRoutesPerColony", 3, 1, 20);
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
                                .defineInRange("SabotageCost", 500, 0, 100000);

                SPY_BRIBE_GUARDS_COST = BUILDER.comment(
                                "Tax cost for BRIBE GUARDS action (disables guards during next raid).")
                                .defineInRange("BribeGuardsCost", 1000, 0, 100000);

                SPY_STEAL_SECRETS_COST = BUILDER.comment(
                                "Tax cost for STEAL SECRETS action (copy building synergy bonuses temporarily).")
                                .defineInRange("StealSecretsCost", 750, 0, 100000);
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
                                "Additional detection chance for BRIBE GUARDS action (0.0-1.0). High risk.")
                                .defineInRange("BribeGuardsDetectionChance", 0.35, 0.0, 1.0);

                SPY_STEAL_SECRETS_DETECTION_CHANCE = BUILDER.comment(
                                "Additional detection chance for STEAL SECRETS action (0.0-1.0). Medium-high risk.")
                                .defineInRange("StealSecretsDetectionChance", 0.20, 0.0, 1.0);
                BUILDER.pop();

                BUILDER.pop(); // End Tax Expansion - Espionage

                // ============================================================
                // [WIP] TAX EXPANSION: War Mechanics (Feature Branch - Not Yet Merged)
                // ============================================================
                BUILDER.push("Tax Expansion - War Mechanics [WIP]");

                // --- War Chest ---
                BUILDER.push("War Chest");
                ENABLE_WAR_CHEST = BUILDER.comment(
                                "Enable war chest system. Colonies must have funds in war chest to declare war. " +
                                                "War chest drains during active war.")
                                .define("EnableWarChest", true);

                WAR_CHEST_MIN_PERCENT_OF_TARGET = BUILDER.comment(
                                "Minimum war chest balance required as percentage of target colony's tax balance (0.0-1.0). "
                                                +
                                                "Example: 0.25 = need 25% of target's balance to declare war.")
                                .defineInRange("WarChestMinPercentOfTarget", 0.25, 0.0, 2.0);

                WAR_CHEST_DRAIN_PER_MINUTE = BUILDER.comment(
                                "Amount drained from war chest per minute during active war.")
                                .defineInRange("WarChestDrainPerMinute", 100, 0, 10000);

                WAR_CHEST_MAX_CAPACITY = BUILDER.comment(
                                "Maximum capacity of the war chest. Prevents hoarding.")
                                .defineInRange("WarChestMaxCapacity", 50000, 1000, Integer.MAX_VALUE);

                WAR_CHEST_AUTO_SURRENDER_ENABLED = BUILDER.comment(
                                "If true, war automatically ends in surrender when war chest is depleted.")
                                .define("WarChestAutoSurrenderEnabled", true);

                WAR_CHEST_AUTO_DEPOSIT_PERCENT = BUILDER.comment(
                                "Percentage of tax revenue to automatically deposit into war chest each tax cycle (0.0-1.0). "
                                                +
                                                "Example: 0.10 = 10% of each tax collection auto-deposited.")
                                .defineInRange("WarChestAutoDepositPercent", 0.10, 0.0, 1.0);

                // --- Raid War Chest ---
                RAID_WAR_CHEST_ENABLED = BUILDER.comment(
                                "If true, raids require war chest funds. Cost is one-time payment based on target's tax generation.")
                                .define("RaidWarChestEnabled", true);

                RAID_WAR_CHEST_COST_PERCENT = BUILDER.comment(
                                "Raid cost as percentage of target colony's tax per interval (0.0-1.0). " +
                                                "Example: 0.10 = raiding costs 10% of what target earns per tax cycle.")
                                .defineInRange("RaidWarChestCostPercent", 0.10, 0.0, 2.0);

                // --- Raid Penalties ---
                RAID_PENALTY_TAX_REDUCTION_PERCENT = BUILDER.comment(
                                "Percentage reduction in tax generation for raided colony (0.0-1.0). " +
                                                "Example: 0.25 = 25% less tax after being raided.")
                                .defineInRange("RaidPenaltyTaxReductionPercent", 0.25, 0.0, 1.0);

                RAID_PENALTY_DURATION_HOURS = BUILDER.comment(
                                "Duration in hours that the raid tax penalty lasts.")
                                .defineInRange("RaidPenaltyDurationHours", 24, 1, 168);

                RAID_REPAIR_COST_PERCENT = BUILDER.comment(
                                "Cost to repair colony and remove raid penalty, as percentage of colony's total tax earnings (0.0-1.0). "
                                                +
                                                "Example: 0.50 = repairing costs 50% of colony's tax balance.")
                                .defineInRange("RaidRepairCostPercent", 0.50, 0.0, 2.0);
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
                                "Hours of immunity from raids after paying a ransom.")
                                .defineInRange("RansomImmunityAfterPaymentHours", 24, 0, 168);
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
                                                "Example: 0.9 = each purchase is 90% as effective as the previous.")
                                .defineInRange("InvestmentDiminishingReturnsFactor", 0.9, 0.1, 1.0);

                // --- Infrastructure Investment ---
                BUILDER.push("Infrastructure Investment");
                INVESTMENT_INFRASTRUCTURE_COST = BUILDER.comment(
                                "Tax cost for infrastructure investment (permanent tax bonus).")
                                .defineInRange("InfrastructureCost", 1000, 100, 1000000);

                INVESTMENT_INFRASTRUCTURE_BONUS = BUILDER.comment(
                                "Permanent tax generation bonus per infrastructure investment (0.0-1.0). " +
                                                "Example: 0.05 = 5% more tax generation.")
                                .defineInRange("InfrastructureBonus", 0.05, 0.01, 0.5);

                INVESTMENT_INFRASTRUCTURE_MAX_STACKS = BUILDER.comment(
                                "Maximum number of infrastructure investments a colony can have.")
                                .defineInRange("InfrastructureMaxStacks", 10, 1, 100);
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
                                "Happiness bonus during festival (0.0-1.0). Example: 0.50 = 50% faster happiness growth.")
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
                                                List.of("PLACE_BLOCKS", "BREAK_BLOCKS", "TOSS_ITEM", "PICKUP_ITEM",
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
                                                "  - Specific block: 'modid:blockname' (e.g., 'minecraft:chest', 'ironchest:iron_chest')\n"
                                                +
                                                "  - Entire mod: '#modid' (e.g., '#ironchest', '#sophisticatedstorage')\n"
                                                +
                                                "These blocks can be opened/used even during conflicts.\n" +
                                                "Common use: Allow looting chests and storage blocks.\n" +
                                                "Blacklist always takes priority over whitelist!")
                                .defineList("BlockInteractionWhitelist",
                                                List.of(
                                                                "minecraft:chest",
                                                                "minecraft:barrel",
                                                                "minecraft:furnace",
                                                                "minecraft:blast_furnace",
                                                                "minecraft:smoker",
                                                                "minecraft:dropper",
                                                                "minecraft:dispenser",
                                                                "minecraft:hopper"),
                                                obj -> obj instanceof String);

                BLOCK_FILTER_WARS = BUILDER.comment("Apply block interaction filter during wars.\n" +
                                "If enabled, blacklist/whitelist rules will be enforced during active wars.")
                                .define("BlockFilterWars", true);

                BLOCK_FILTER_RAIDS = BUILDER.comment("Apply block interaction filter during raids.\n" +
                                "If enabled, blacklist/whitelist rules will be enforced during active raids.")
                                .define("BlockFilterRaids", true);

                REQUIRED_GUARD_TOWERS_FOR_BOOST = BUILDER.comment(
                                "Number of Guard Towers required to activate a tax boost for all buildings in a colony.")
                                .defineInRange("RequiredGuardTowersForBoost", 5, 1, 100);

                GUARD_TOWER_TAX_BOOST_PERCENTAGE = BUILDER.comment(
                                "Percentage increase in total tax revenue when required Guard Towers are built. " +
                                                "This acts as a multiplier on the colony's total generated tax income. "
                                                +
                                                "For example: 0.5 = 50% increase, so 1000 tax becomes 1500 tax.")
                                .defineInRange("GuardTowerTaxBoostPercentage", 0.5, 0.0, 2.0);

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

                // Add base and upgrade taxes for all buildings

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

                // Additional buildings
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

                // Newly added buildings
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

                // Add mapping for full class names to short names used in config
                // Legacy mappings (just in case)
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

                // Correct new class name mappings (Standard MineColonies structure)
                String prefix = "com.minecolonies.core.colony.buildings.workerbuildings.";
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingBarracks", "barracks");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingGuardTower", "guardtower");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingArchery", "archery");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingBaker", "bakery"); // Note: BuildingBaker -> bakery
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
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingResidence", "residence"); // Note: BuildingResidence ->
                                                                                         // residence
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

                // New buildings from user list
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingAlchemist", "alchemist");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingConcreteMixer", "concretemixer"); // Assuming Name
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingFletcher", "fletcher");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingLumberjack", "lumberjack");
                CLASS_NAME_TO_SHORT_NAME.put(prefix + "BuildingRabbitHutch", "rabbithutch"); // Assuming Name
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

                CONFIG = BUILDER.build();
        }

        public static boolean isSDMShopConversionEnabled() {
                return ENABLE_SDM_SHOP_CONVERSION.get();
        }

        public static String getCurrencyItemName() {
                return CURRENCY_ITEM_NAME.get();
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

        public static boolean showTaxGenerationLogs() {
                return SHOW_TAX_GENERATION_LOGS.get();
        }

        // RaidGuardProtection Configuration
        public static int getMinGuardsToBeRaided() {
                return MIN_GUARDS_TO_BE_RAIDED.get();
        }

        public static int getMinGuardTowersToBeRaided() {
                return MIN_GUARD_TOWERS_TO_BE_RAIDED.get();
        }

        public static boolean isRaidGuardProtectionEnabled() {
                return ENABLE_RAID_GUARD_PROTECTION.get();
        }

        public static boolean showColonyInitializationLogs() {
                return SHOW_COLONY_INITIALIZATION_LOGS.get();
        }

        // Entity Raid Configuration Getters
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

        // Entity Raid Debug Configuration Getters
        public static boolean isEntityRaidDebugEnabled() {
                return ENABLE_ENTITY_RAID_DEBUG.get();
        }

        public static int getEntityRaidDebugLevel() {
                return ENTITY_RAID_DEBUG_LEVEL.get();
        }

        public static boolean shouldBypassAllianceChecks() {
                return BYPASS_ALLIANCE_CHECKS.get();
        }

        // General Colony Permissions Configuration Getters
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

        // Guard Resistance During Raids Configuration Getters
        public static boolean isGuardResistanceDuringRaidsEnabled() {
                return ENABLE_GUARD_RESISTANCE_DURING_RAIDS.get();
        }

        public static int getGuardResistanceLevel() {
                return GUARD_RESISTANCE_LEVEL.get();
        }

        // Happiness-Based Tax Configuration Getters
        public static boolean isHappinessTaxModifierEnabled() {
                return ENABLE_HAPPINESS_TAX_MODIFIER.get();
        }

        // Colony Inactivity Configuration Getters
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

        // Colony Auto-Abandon Configuration Getters
        public static boolean isColonyAutoAbandonEnabled() {
                return ENABLE_COLONY_AUTO_ABANDON.get();
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

        // Abandoned Colony Claiming Configuration Getters
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

        // Recipe Disabling Configuration Getters
        public static boolean isDisableHutRecipesEnabled() {
                return DISABLE_HUT_RECIPES.get();
        }

        // Web API Configuration Getters
        public static boolean isWebAPIEnabled() {
                return ENABLE_WEB_API.get();
        }

        public static int getWebAPIPort() {
                return WEB_API_PORT.get();
        }

        public static String getWebAPIKey() {
                return WEB_API_KEY.get();
        }

        public static int getWebAPIRateLimitRequestsPerMinute() {
                return WEB_API_RATE_LIMIT_REQUESTS_PER_MINUTE.get();
        }

        public static boolean isWebAPIAuthenticationRequired() {
                return WEB_API_REQUIRE_AUTHENTICATION.get();
        }

        public static boolean isWebAPIOfflinePlayersEnabled() {
                return WEB_API_ENABLE_OFFLINE_PLAYERS.get();
        }

        public static int getWebAPICacheRefreshMinutes() {
                return WEB_API_CACHE_REFRESH_MINUTES.get();
        }

        // Block Interaction Filter Configuration Getters
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

        // Spy Action Costs
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

        public static int getSpyScoutMaxDurationHours() {
                return SPY_SCOUT_MAX_DURATION_HOURS.get();
        }

        public static int getSpyMaxActivePerPlayer() {
                return SPY_MAX_ACTIVE_PER_PLAYER.get();
        }

        // Spy Action Effects
        public static double getSpySabotageTaxReductionPercent() {
                return SPY_SABOTAGE_TAX_REDUCTION_PERCENT.get();
        }

        // Spy Action Detection Chances
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

        // --- War Chest ---
        public static boolean isWarChestEnabled() {
                return ENABLE_WAR_CHEST.get();
        }

        public static double getWarChestMinPercentOfTarget() {
                return WAR_CHEST_MIN_PERCENT_OF_TARGET.get();
        }

        public static int getWarChestDrainPerMinute() {
                return WAR_CHEST_DRAIN_PER_MINUTE.get();
        }

        public static int getWarChestMaxCapacity() {
                return WAR_CHEST_MAX_CAPACITY.get();
        }

        public static boolean isWarChestAutoSurrenderEnabled() {
                return WAR_CHEST_AUTO_SURRENDER_ENABLED.get();
        }

        public static double getWarChestAutoDepositPercent() {
                return WAR_CHEST_AUTO_DEPOSIT_PERCENT.get();
        }

        // --- Raid War Chest ---
        public static boolean isRaidWarChestEnabled() {
                return RAID_WAR_CHEST_ENABLED.get();
        }

        public static double getRaidWarChestCostPercent() {
                return RAID_WAR_CHEST_COST_PERCENT.get();
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
}
