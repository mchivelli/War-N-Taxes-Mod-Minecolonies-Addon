# Configuration Guide

Below is a list of all server configurations for War 'N Taxes.

### TaxIntervalMinutes
- **Description**: Tax generation interval in minutes
- **Default Value**: $defVal 

### MaxTaxRevenue
- **Description**:  Maximum tax revenue a colony can store before it stops generating further taxes
- **Default Value**: $defVal 

### ShowTaxGenerationLogs
- **Description**:  Enable console logging of tax generation details (building upgrades, max warnings, etc.).  Set to false to reduce console spam during initialization.
- **Default Value**: $defVal 

### DebtLimit
- **Description**: Optional debt limit for colony debt. If > 0, colony revenue will not deduct more once the debt reaches this limit (i.e. the tax value won't drop below -DebtLimit). Set to 0 to disable.
- **Default Value**: $defVal 

### TaxStealPerGuard
- **Description**:  Amount of debt added to colony per guard killed when raiding colonies in debt. Only applies when DebtLimit > 0. Raiders get this amount when killing guards in debt colonies. Default: 200.
- **Default Value**: $defVal 

### EnableColonyTransfer
- **Description**:  Enable colony ownership transfer when a war is won (true = enable, false = disable).
- **Default Value**: $defVal 

### EnableWarActions
- **Description**: If false, war will not toggle any interaction permissions
- **Default Value**: $defVal 

### WarAcceptanceRequired
- **Description**:  If true, war requests must be manually accepted; if false, wars requests will automatically accept.
- **Default Value**: $defVal 

### AttackerGracePeriodMinutes
- **Description**: Grace period between declaring wars (minutes)
- **Default Value**: $defVal 

### RaidGracePeriodMinutes
- **Description**: Grace period between raids (minutes)
- **Default Value**: $defVal 

### MaxRaidDurationMinutes
- **Description**: Maximum raid duration (minutes)
- **Default Value**: $defVal 

### RaidPenaltyPercentage
- **Description**:  Penalty percentage applied when a raider is killed by a defender during a raid (0.0 - 1.0)
- **Default Value**: $defVal 

### RaidDefenseRewardPercentage
- **Description**:  Percentage of killed raider's balance transferred to defending colony for owner/officers to claim (0.0 - 1.0)
- **Default Value**: $defVal 

### WarVictoryPercentage
- **Description**:  Percentage of losing players' balance awarded to each winning player. Set to 0.0 to only enable colony transfer (if enabled).\n  Uses SDMShop balance or colony funds based on what's configured.
- **Default Value**: $defVal 

### WarDefeatPercentage
- **Description**:  Percentage that each losing player loses from their balance when defeated in war.\n Uses SDMShop balance or colony funds based on what's configured.
- **Default Value**: $defVal 

### WarStalematePercentage
- **Description**:  Percentage that all war participants lose from their balance when a war ends in stalemate.\n  Uses SDMShop balance or colony funds based on what's configured.
- **Default Value**: $defVal 

### WarTaxFreezeHours
- **Description**:  Duration (in hours) to freeze colony tax generation after a war loss or stalemate.\n Set to 0 to disable tax freezing.
- **Default Value**: $defVal 

### EnableWarVassalization
- **Description**:  When enabled and ENABLE_COLONY_TRANSFER is disabled, winning a war will vassalize the losing colony instead of transferring ownership.\n  The losing colony will pay a percentage of their tax income to the winner for a set duration.
- **Default Value**: $defVal 

### WarVassalizationTributePercentage
- **Description**:  Percentage of the vassal colony's tax income paid to the victor as tribute (1-100).\n  This is the tribute rate enforced when a colony is vassalized through war.
- **Default Value**: $defVal 

### MinGuardsToRaid
- **Description**: Minimum number of guards required to initiate a raid. NOTE: This is only used when 'EnableRaidBuildingRequirements' is disabled. If building requirements are enabled, they take priority over this setting.
- **Default Value**: $defVal 

### EnableRaidGuardProtection
- **Description**:  Enable raid guard protection system. When enabled, colonies must meet minimum defense requirements to be eligible for raids, protecting smaller/newer colonies from being overwhelmed.
- **Default Value**: $defVal 

### MinGuardsToBeRaided
- **Description**:  Minimum number of guards a colony must have to be eligible for raids. Set to 0 to disable guard requirement. Default: 2 guards minimum.
- **Default Value**: $defVal 

### MinGuardTowersToBeRaided
- **Description**:  Minimum number of guard towers a colony must have to be eligible for raids. Set to 0 to disable guard tower requirement. Default: 1 guard tower minimum.
- **Default Value**: $defVal 

### RaidTaxIntervalSeconds
- **Description**: Interval between tax transfers during raids (seconds)
- **Default Value**: $defVal 

### EnableEntityRaids
- **Description**:  Enable entity-triggered raids. When disabled, entities will not trigger raids even if they meet the criteria. Admin-only feature.
- **Default Value**: $defVal 

### EntityRaidWhitelist
- **Description**:  List of entity types that can trigger raids. Use Minecraft entity resource IDs (e.g., 'minecraft:pillager'). Default: only Pillagers.
- **Default Value**: $defVal 

### EntityRaidDetectionRadius
- **Description**:  Radius in blocks to detect entities around colonies for raid triggering (default: 50 blocks)
- **Default Value**: $defVal 

### EntityRaidMessageOnly
- **Description**:  If true, entity raids will only send messages to colony and allies without triggering actual raid mechanics. Set to false for full raid functionality.
- **Default Value**: $defVal 

### EntityRaidBoundaryTimerSeconds
- **Description**:  Time in seconds entities have to return to colony boundaries after leaving during an entity raid
- **Default Value**: $defVal 

### EntityRaidCheckIntervalTicks
- **Description**:  How often (in ticks) to check for entities near colonies. Lower values = more frequent checks but higher performance cost. 20 ticks = 1 second
- **Default Value**: $defVal 

### EnableEntityRaidDebug
- **Description**: Enable detailed debug logging for EntityRaid system. When enabled, provides comprehensive logging of entity detection, filtering, and raid triggering processes.
- **Default Value**: $defVal 

### EntityRaidDebugLevel
- **Description**: Debug logging verbosity level for EntityRaid system:\n 1 = Basic (raid triggers, major events)\n 2 = Detailed (entity filtering, alliance checks)\n 3 = Verbose (all detection steps, performance metrics)
- **Default Value**: $defVal 

### BypassAllianceChecks
- **Description**:  TESTING ONLY: Bypass alliance checks to allow your own recruits to trigger raids (set to false for production)
- **Default Value**: $defVal 

### EnablePvPKillEconomy
- **Description**:  Enable PvP kill economy system. When enabled, killing a player transfers a percentage of their balance to the killer.  Compatible with SDMShop and SDMEconomy. Disabled by default.
- **Default Value**: $defVal 

### PvPKillRewardPercentage
- **Description**:  Percentage of victim's balance transferred to killer on PvP kill (0.0 - 1.0). For example: 0.1 = 10% of victim's money goes to killer. Uses SDMShop balance or colony funds based on configuration.
- **Default Value**: $defVal 

### EnableGeneralItemInteractions
- **Description**:  Enable general item interactions for all players in colonies. When enabled, allows non-allies to toss items and pickup items within colony boundaries.
- **Default Value**: $defVal 

### GeneralColonyActions
- **Description**:  Actions allowed for all players in colonies when general interactions are enabled. See https://ldtteam.github.io/MineColoniesAPI/com/minecolonies/api/colony/permissions/Action.html for a list of possible actions.
- **Default Value**: $defVal 

### EnableGuardResistanceDuringRaids
- **Description**:  Enable resistance effect for colony guards during raids. When enabled, guards will receive a resistance effect to help defend the colony.
- **Default Value**: $defVal 

### GuardResistanceLevel
- **Description**:  Level of resistance effect applied to guards during raids (1-255). Higher levels provide better protection. Set to 0 to disable even if the feature is enabled.
- **Default Value**: $defVal 

### EnableHappinessTaxModifier
- **Description**:  Enable happiness-based tax modifiers. When enabled, colony citizen happiness affects tax generation rates.
- **Default Value**: $defVal 

### HappinessTaxMultiplierMin
- **Description**:  Minimum tax multiplier for unhappy colonies (0.1 - 1.0). Lower values mean unhappy colonies generate less tax.
- **Default Value**: $defVal 

### HappinessTaxMultiplierMax
- **Description**:  Maximum tax multiplier for very happy colonies (1.0 - 2.0). Higher values mean happy colonies generate more tax.
- **Default Value**: $defVal 

### EnableColonyInactivityTaxPause
- **Description**:  Enable colony inactivity tax pause system. When enabled, colonies that haven't been visited by owners/officers for the specified time will stop generating taxes.
- **Default Value**: $defVal 

### ColonyInactivityHoursThreshold
- **Description**:  Hours of inactivity after which a colony will stop generating taxes. This uses MineColonies' built-in player interaction tracking.
- **Default Value**: $defVal 

### EnableExtortionSystem
- **Description**:  Enable extortion system for wars. When enabled, defenders can choose to pay extortion instead of fighting when WarAcceptanceRequired is false.
- **Default Value**: $defVal 

### DefaultExtortionPercentage
- **Description**:  Default extortion percentage when not specified by attacker (0.0 - 1.0). For example: 0.15 = 15% of defender's balance.
- **Default Value**: $defVal 

### ExtortionResponseTimeMinutes
- **Description**:  Time limit in minutes for defenders to respond to extortion offers before war automatically starts.
- **Default Value**: $defVal 

### ExtortionImmunityHours
- **Description**:  Hours of immunity from new extortion attempts after successfully paying extortion.
- **Default Value**: $defVal 

### EnableCitizenMilitia
- **Description**:  Enable citizen militia system during raids. When enabled, citizens will temporarily become guards to defend the colony. Set to false to use the old raid system.
- **Default Value**: $defVal 

### MilitiaConversionPercentage
- **Description**:  Percentage of eligible citizens to convert to militia guards during raids (0.0 - 1.0). For example: 0.3 = 30% of citizens become militia.
- **Default Value**: $defVal 

### MilitiaMinCitizenLevel
- **Description**:  Minimum level required for a citizen to be eligible for militia conversion. Higher levels = more experienced citizens only.
- **Default Value**: $defVal 

### MilitiaGuardsSeekRaiders
- **Description**:  If true, militia guards and regular guards will actively seek out and engage raiders instead of just defending their posts.
- **Default Value**: $defVal 

### TaxStealPerGuardKilled
- **Description**:  If true, raiders steal tax based on guards killed. If false, uses the old time-based tax stealing system.
- **Default Value**: $defVal 

### TaxStealPercentagePerGuard
- **Description**:  Percentage of colony tax stolen per guard/militia killed during a raid (0.0 - 1.0). For example: 0.1 = 10% tax stolen per guard killed.
- **Default Value**: $defVal 

### MaxRaidTaxPercentage
- **Description**:  Maximum percentage of colony tax that can be stolen during a raid (0.0 - 1.0). This amount is distributed across all guards/militia. For example: 0.5 = 50% max tax stolen when all defenders are killed.
- **Default Value**: $defVal 

### ApplyResistanceToCitizens
- **Description**:  If true, resistance effects during raids/wars will also be applied to all citizens, not just guards. This makes the entire colony more defensive.
- **Default Value**: $defVal 

### EnableColonyAutoAbandon
- **Description**:  Enable automatic colony abandonment when owners/officers haven't visited for the configured time.  When enabled, colonies will be automatically abandoned and can be claimed by other players.
- **Default Value**: $defVal 

### NotifyOwnersBeforeAbandon
- **Description**:  If true, owners and officers will be notified before their colony is abandoned. Requires warning days to be configured.
- **Default Value**: $defVal 

### AbandonWarningDays
- **Description**: Days before abandonment to warn owners and officers. Warnings are sent when they log in during this period.
- **Default Value**: $defVal 

### EnableListAbandonedForAll
- **Description**:  Allow ALL players to use /wnt listabandoned command.\n When FALSE (default): Only OPs (permission level 2) can view abandoned colonies.\n  When TRUE: Any player can view the list of abandoned colonies available for claiming.
- **Default Value**: $defVal 

### ResetTimerOnOfficerLogin
- **Description**:  EXPERIMENTAL: Reset abandonment timer when officers/owners log into the server.\n When FALSE (default): Timer only resets when officers PHYSICALLY VISIT the colony (chunk-based detection).\n  When TRUE: Timer resets for ALL colonies an officer manages just by logging in (easier but defeats the purpose).\n  \n RECOMMENDED: Keep this FALSE to ensure officers actually visit their colonies to prevent abandonment.\n  Setting this to TRUE will prevent colonies from ever being abandoned if officers log in regularly.
- **Default Value**: $defVal 

### ClaimingRaidDurationMinutes
- **Description**:  Duration in minutes for the claiming raid when taking over an abandoned colony. During this time, all citizens will be hostile and attack the claiming player.
- **Default Value**: $defVal 

### SpawnMercenariesIfLowDefenders
- **Description**:  If true, spawn mercenaries to defend the colony during claiming if there are fewer than 5 citizens/guards.  This ensures abandoned colonies still put up a fight when being claimed.
- **Default Value**: $defVal 

### ClaimingBuildingRequirements
- **Description**:  Building requirements to claim abandoned colonies. Format: 'building:level,building:level'.  Example: 'townhall:2,guardtower:1' means player needs townhall level 2 and at least one guard tower to claim.  Leave empty to disable building requirements.
- **Default Value**: $defVal 

### RaidBuildingRequirements
- **Description**:  Building requirements to initiate raids. Format: 'building:level:amount,building:level:amount'.  Example: 'townhall:1:1,guardtower:1:3' means player needs 1 townhall level 1 and 3 guard towers level 1 to raid.  NOTE: When this is enabled, it replaces the 'MinGuardsToRaid' requirement entirely.  Leave empty to disable building requirements.
- **Default Value**: $defVal 

### EnableWarBuildingRequirements
- **Description**: Enable building requirements for declaring wars. When enabled, these requirements take PRIORITY over 'MinGuardsToWageWar' setting. Disable this to use the legacy guard count system instead.
- **Default Value**: $defVal 

### WarBuildingRequirements
- **Description**:  Building requirements to declare wars. Format: 'building:level:amount,building:level:amount'.  Example: 'townhall:2:1,guardtower:1:3,buildershut:1:1,house:1:1' means player needs 1 townhall level 2, 3 guard towers level 1, 1 builders hut level 1, and 1 residential building level 1 to declare war.  NOTE: When this is enabled, it replaces the 'MinGuardsToWageWar' requirement entirely.  Leave empty to disable building requirements.
- **Default Value**: $defVal 

### DisableHutRecipes
- **Description**:  Disable all Minecolonies building hut recipes. When enabled, players must obtain building huts through SDMShop or Admin Shop instead of crafting them.  This affects all buildings that accumulate taxes or maintenance costs. Disabled by default.
- **Default Value**: $defVal 

### EnableWebAPI
- **Description**: Enable the Web API server for external data access. When enabled, war statistics and other data can be queried via HTTP REST endpoints. IMPORTANT: Only enable this if you understand the security implications. The API runs SERVER-SIDE ONLY and requires proper port forwarding if accessed from outside your network.  Default: false (disabled)
- **Default Value**: $defVal 

### WebAPIPort
- **Description**: Port number for the Web API server. Make sure this port is not already in use by another application. Common ports: 8080, 8090, 9000. Avoid ports below 1024 (requires admin privileges). You may need to configure port forwarding on your router for external access. Default: 8090
- **Default Value**: $defVal 

### WebAPIKey
- **Description**: API authentication key for secure access. This key must be provided in the 'X-API-Key' header for all requests when authentication is enabled.  Generate a strong, random key (recommended: 32 characters). Example: 'my-super-secret-api-key-12345-abcdef' SECURITY WARNING: Keep this key private! Anyone with this key can access your server data.  Default: empty (you must set this to enable authentication)
- **Default Value**: $defVal 

### WebAPIRequireAuthentication
- **Description**: Require API key authentication for all requests. When enabled, requests without a valid 'X-API-Key' header will be rejected with 401 Unauthorized.  When disabled, anyone can query the API (use with caution!). SECURITY: Always enable this for public servers. Only disable for local testing. Default: true (authentication required)
- **Default Value**: $defVal 

### WebAPIEnableOfflinePlayers
- **Description**: Enable offline player data in API responses. When enabled, the API can return statistics for offline players by scanning player data files.  This requires periodic file scanning and caching, which uses more memory and disk I/O.  Use the 'includeOffline=true' query parameter to request offline data. PERFORMANCE: Only enable if you need offline player stats on your website. Default: false (online players only)
- **Default Value**: $defVal 

### GivePatchouliBookOnJoin
- **Description**:  Give players the War & Taxes Codex (Patchouli guide book) when they first join the server.\n  Requires Patchouli mod to be installed. Book is only given once per player.
- **Default Value**: $defVal 

### EnableTaxPolicies
- **Description**:  Enable tax policy system. Colonies can choose policies that affect tax generation and citizen happiness.
- **Default Value**: $defVal 

### LowPolicyRevenueModifier
- **Description**:  Revenue modifier for LOW tax policy. Negative = less tax. Example: -0.25 = 25% less revenue.
- **Default Value**: $defVal 

### LowPolicyHappinessModifier
- **Description**:  Happiness modifier for LOW tax policy. Positive = happier citizens. Example: 0.20 = 20% faster happiness growth.
- **Default Value**: $defVal 

### HighPolicyRevenueModifier
- **Description**:  Revenue modifier for HIGH tax policy. Positive = more tax. Example: 0.25 = 25% more revenue.
- **Default Value**: $defVal 

### HighPolicyHappinessModifier
- **Description**:  Happiness modifier for HIGH tax policy. Negative = unhappier citizens. Example: -0.15 = 15% slower happiness.
- **Default Value**: $defVal 

### WarPolicyRevenueModifier
- **Description**:  Revenue modifier for WAR ECONOMY policy. High boost during wartime. Example: 0.50 = 50% more revenue.
- **Default Value**: $defVal 

### WarPolicyHappinessModifier
- **Description**:  Happiness modifier for WAR ECONOMY policy. Citizens dislike war economy. Example: -0.25 = 25% happiness penalty.
- **Default Value**: $defVal 

### EnableTaxReports
- **Description**:  Enable tax report generation as written books. Use /wnt taxreport to generate.
- **Default Value**: $defVal 

### TaxReportAutoDeleteHours
- **Description**:  Hours before tax report books auto-delete from inventory. Set to 0 to never auto-delete.
- **Default Value**: $defVal 

### TaxReportMaxHistoryDays
- **Description**:  Maximum days of tax history to track for reports.
- **Default Value**: $defVal 

### EnableLeaderboards
- **Description**:  Enable server-wide leaderboards for tax earnings, wars won, etc.
- **Default Value**: $defVal 

### LeaderboardUpdateIntervalMinutes
- **Description**:  How often leaderboards are recalculated (in minutes).
- **Default Value**: $defVal 

### LeaderboardTopCount
- **Description**:  Number of entries to show in leaderboard commands.
- **Default Value**: $defVal 

### EnableFactionSystem
- **Description**:  Enable faction system. Colonies can form factions with shared tax pools and trade routes.
- **Default Value**: $defVal 

### MaxFactionMembers
- **Description**:  Maximum number of colonies that can join a single faction.
- **Default Value**: $defVal 

### FactionCreationCost
- **Description**:  Tax cost to create a new faction.
- **Default Value**: $defVal 

### FactionAllianceLimit
- **Description**:  Maximum number of allied factions a faction can have.
- **Default Value**: $defVal 

### EnableSharedTaxPool
- **Description**:  Enable shared tax pool for factions. Member colonies contribute a percentage to the pool.
- **Default Value**: $defVal 

### DefaultPoolContributionPercent
- **Description**:  Default percentage of tax income that faction members contribute to the shared pool.
- **Default Value**: $defVal 

### MaxPoolBalance
- **Description**:  Maximum balance the shared tax pool can hold.
- **Default Value**: $defVal 

### PoolHistoryRetentionDays
- **Description**:  Days to retain pool transaction history for reporting.
- **Default Value**: $defVal 

### EnableTradeRoutes
- **Description**:  Enable trade routes between allied colonies. Trade routes generate passive income based on distance.
- **Default Value**: $defVal 

### TradeRouteIncomePerChunk
- **Description**:  Tax income generated per chunk of distance on a trade route (per tax cycle).
- **Default Value**: $defVal 

### TradeRouteMaxDistanceChunks
- **Description**:  Maximum distance in chunks for a trade route. Longer routes = more income but higher maintenance.
- **Default Value**: $defVal 

### TradeRouteMaintenanceCost
- **Description**:  Maintenance cost per trade route per tax cycle.
- **Default Value**: $defVal 

### MaxTradeRoutesPerColony
- **Description**:  Maximum number of trade routes a colony can have active.
- **Default Value**: $defVal 

### EnableSpySystem
- **Description**:  Enable the spy/sabotage system. Colonies can spend tax to perform espionage on rivals.
- **Default Value**: $defVal 

### SpyCooldownMinutes
- **Description**:  Cooldown in minutes between spy actions against the same colony.
- **Default Value**: $defVal 

### SpyDetectionBaseChance
- **Description**:  Base chance (0.0-1.0) of being detected when performing spy actions. Each action adds its own detection modifier.
- **Default Value**: $defVal 

### MaxActiveSpiesPerPlayer
- **Description**: Maximum number of active spy missions per player.
- **Default Value**: $defVal 

### ScoutCost
- **Description**:  Tax cost for SCOUT action (reveals enemy colony's current tax balance).
- **Default Value**: $defVal 

### SabotageCost
- **Description**:  Tax cost for SABOTAGE action (reduces target's next tax cycle).
- **Default Value**: $defVal 

### BribeGuardsCost
- **Description**:  Tax cost for BRIBE GUARDS action (disables guards during next raid).
- **Default Value**: $defVal 

### StealSecretsCost
- **Description**:  Tax cost for STEAL SECRETS action (copy building synergy bonuses temporarily).
- **Default Value**: $defVal 

### SabotageTaxReductionPercent
- **Description**:  Percentage reduction to target's tax for the next cycle when sabotaged (0.0-1.0). Example: 0.25 = 25% less tax.
- **Default Value**: $defVal 

### BribeGuardsDisabledCount
- **Description**:  Number of guards disabled when BRIBE GUARDS action succeeds.
- **Default Value**: $defVal 

### StealSecretsDurationHours
- **Description**:  Duration in hours that stolen building synergy bonuses last.
- **Default Value**: $defVal 

### ScoutDetectionChance
- **Description**:  Additional detection chance for SCOUT action (0.0-1.0). Low risk action.
- **Default Value**: $defVal 

### SabotageDetectionChance
- **Description**:  Additional detection chance for SABOTAGE action (0.0-1.0). Medium risk.
- **Default Value**: $defVal 

### BribeGuardsDetectionChance
- **Description**:  Additional detection chance for BRIBE GUARDS action (0.0-1.0). High risk.
- **Default Value**: $defVal 

### StealSecretsDetectionChance
- **Description**:  Additional detection chance for STEAL SECRETS action (0.0-1.0). Medium-high risk.
- **Default Value**: $defVal 

### EnableWarChest
- **Description**:  Enable war chest system. Colonies must have funds in war chest to declare war. War chest drains during active war.
- **Default Value**: $defVal 

### WarChestMinPercentOfTarget
- **Description**:  Minimum war chest balance required as percentage of target colony's tax balance (0.0-1.0).  Example: 0.25 = need 25% of target's balance to declare war.
- **Default Value**: $defVal 

### WarChestDrainPerMinute
- **Description**:  Amount drained from war chest per minute during active war.
- **Default Value**: $defVal 

### WarChestMaxCapacity
- **Description**:  Maximum capacity of the war chest. Prevents hoarding.
- **Default Value**: $defVal 

### WarChestAutoSurrenderEnabled
- **Description**:  If true, war automatically ends in surrender when war chest is depleted.
- **Default Value**: $defVal 

### WarChestAutoDepositPercent
- **Description**:  Percentage of tax revenue to automatically deposit into war chest each tax cycle (0.0-1.0).  Example: 0.10 = 10% of each tax collection auto-deposited.
- **Default Value**: $defVal 

### RaidWarChestEnabled
- **Description**:  If true, raids require war chest funds. Cost is one-time payment based on target's tax generation.
- **Default Value**: $defVal 

### RaidWarChestCostPercent
- **Description**:  Raid cost as percentage of target colony's tax per interval (0.0-1.0). Example: 0.10 = raiding costs 10% of what target earns per tax cycle.
- **Default Value**: $defVal 

### RaidPenaltyTaxReductionPercent
- **Description**:  Percentage reduction in tax generation for raided colony (0.0-1.0). Example: 0.25 = 25% less tax after being raided.
- **Default Value**: $defVal 

### RaidPenaltyDurationHours
- **Description**:  Duration in hours that the raid tax penalty lasts.
- **Default Value**: $defVal 

### RaidRepairCostPercent
- **Description**:  Cost to repair colony and remove raid penalty, as percentage of colony's total tax earnings (0.0-1.0).  Example: 0.50 = repairing costs 50% of colony's tax balance.
- **Default Value**: $defVal 

### EnableWarExhaustion
- **Description**:  Enable war exhaustion. Colonies generate less tax during active wars and need recovery time after.
- **Default Value**: $defVal 

### WarTaxReductionPercent
- **Description**:  Percentage reduction in tax generation during active war (0.0-1.0). Example: 0.30 = 30% less tax during war.
- **Default Value**: $defVal 

### PostWarRecoveryHours
- **Description**:  Hours after war ends for colony tax generation to fully recover.
- **Default Value**: $defVal 

### EnableWarReparations
- **Description**:  Enable war reparations debuff for colonies that lose wars frequently.
- **Default Value**: $defVal 

### ReparationsTaxPenaltyPercent
- **Description**:  Percentage penalty to tax generation while under reparations (0.0-1.0). Example: 0.20 = 20% less tax.
- **Default Value**: $defVal 

### ReparationsDurationHours
- **Description**:  Duration in hours that war reparations debuff lasts.
- **Default Value**: $defVal 

### ReparationsTriggerLossesCount
- **Description**:  Number of wars lost within 7 days to trigger reparations debuff.
- **Default Value**: $defVal 

### EnableRansomSystem
- **Description**:  Enable ransom system. When colony owner dies during raid, attacker can demand ransom to end raid.
- **Default Value**: $defVal 

### RansomDefaultPercent
- **Description**:  Default ransom amount as percentage of victim's tax balance (0.0-1.0). Example: 0.15 = 15% of their balance.
- **Default Value**: $defVal 

### RansomMinAmount
- **Description**:  Minimum ransom amount (floors the percentage calculation).
- **Default Value**: $defVal 

### RansomMaxAmount
- **Description**:  Maximum ransom amount (caps the percentage calculation).
- **Default Value**: $defVal 

### RansomTimeoutSeconds
- **Description**:  Seconds victim has to respond to ransom offer before it expires.
- **Default Value**: $defVal 

### RansomCooldownMinutes
- **Description**:  Cooldown in minutes before same attacker can offer ransom to same victim.
- **Default Value**: $defVal 

### RansomImmunityAfterPaymentHours
- **Description**:  Hours of immunity from raids after paying a ransom.
- **Default Value**: $defVal 

### EnableInvestments
- **Description**:  Enable investment system. Colonies can spend tax for permanent or temporary bonuses.
- **Default Value**: $defVal 

### InvestmentDiminishingReturnsFactor
- **Description**:  Factor applied to each subsequent investment purchase (0.0-1.0). Example: 0.9 = each purchase is 90% as effective as the previous.
- **Default Value**: $defVal 

### InfrastructureCost
- **Description**:  Tax cost for infrastructure investment (permanent tax bonus).
- **Default Value**: $defVal 

### InfrastructureBonus
- **Description**:  Permanent tax generation bonus per infrastructure investment (0.0-1.0). Example: 0.05 = 5% more tax generation.
- **Default Value**: $defVal 

### InfrastructureMaxStacks
- **Description**:  Maximum number of infrastructure investments a colony can have.
- **Default Value**: $defVal 

### GuardTrainingCost
- **Description**:  Tax cost for guard training investment (temporary damage bonus).
- **Default Value**: $defVal 

### GuardTrainingDamageBonus
- **Description**:  Damage bonus for guards after training (0.0-1.0). Example: 0.10 = 10% more damage.
- **Default Value**: $defVal 

### GuardTrainingDurationHours
- **Description**:  Duration in hours that guard training bonus lasts.
- **Default Value**: $defVal 

### FestivalCost
- **Description**:  Tax cost to host a festival (temporary happiness bonus).
- **Default Value**: $defVal 

### FestivalHappinessBonus
- **Description**:  Happiness bonus during festival (0.0-1.0). Example: 0.50 = 50% faster happiness growth.
- **Default Value**: $defVal 

### FestivalDurationHours
- **Description**:  Duration in hours that festival happiness bonus lasts.
- **Default Value**: $defVal 

### ResearchCost
- **Description**:  Tax cost for research investment (spy defense bonus).
- **Default Value**: $defVal 

### ResearchSpyDefenseBonus
- **Description**:  Bonus to spy detection chance while research is active (0.0-1.0). Example: 0.50 = 50% higher chance to detect enemy spies.
- **Default Value**: $defVal 

### ResearchDurationDays
- **Description**:  Duration in days that research spy defense bonus lasts.
- **Default Value**: $defVal 

### EnableMerchantCaravan
- **Description**: Individual event toggles - disable specific events you don't want);  ENABLE_MERCHANT_CARAVAN = BUILDER .comment(Enable Merchant Caravan event (15% tax, 0.3 happiness)
- **Default Value**: $defVal 

### WarDurationMinutes
- **Description**: War duration (minutes)
- **Default Value**: $defVal 

### MinGuardsToWageWar
- **Description**: Minimum guards required to declare war. NOTE: This is only used when 'EnableWarBuildingRequirements' is disabled. If building requirements are enabled, they take priority over this setting.
- **Default Value**: $defVal 

### EnableLPGroupSwitching
- **Description**:  If enabled, war participants will be switched to the 'war' LP permission group during wars.\n  This requires LuckPerms to be installed and the 'war' group to be properly set up.\n  The command used is: /lp user <Player> parent set war
- **Default Value**: $defVal 

### JoinPhaseDurationMinutes
- **Description**: Duration of the join phase in minutes
- **Default Value**: $defVal 

### KeepInventoryOnLastLife
- **Description**:  If enabled, players will keep their inventory on their last life when they die in war.\n  This allows them to continue fighting without losing their gear, and especially important when colony transfer is enabled.
- **Default Value**: $defVal 

### PlayerLivesInWar
- **Description**: Number of lives each player has during a war.
- **Default Value**: $defVal 

### WarActions
- **Description**:  Actions permitted during a War. See https://ldtteam.github.io/MineColoniesAPI/com/minecolonies/api/colony/permissions/Action.html for a list of possible actions.\n  Note: GUARDS_ATTACK was removed from Minecolonies API - hostility is now controlled by Rank.isHostile()
- **Default Value**: $defVal 

### RaidActions
- **Description**:  Actions permitted during a Raid. See https://ldtteam.github.io/MineColoniesAPI/com/minecolonies/api/colony/permissions/Action.html for a list of possible actions.\n  Note: GUARDS_ATTACK was removed from Minecolonies API - hostility is now controlled by Rank.isHostile()
- **Default Value**: $defVal 

### ClaimingActions
- **Description**:  Actions permitted during Abandoned Colony Claiming raids. See https://ldtteam.github.io/MineColoniesAPI/com/minecolonies/api/colony/permissions/Action.html for a list of possible actions.\n  Note: GUARDS_ATTACK was removed from Minecolonies API - hostility is now controlled by Rank.isHostile()
- **Default Value**: $defVal 

### BlockInteractionBlacklist
- **Description**:  List of block IDs that CANNOT be interacted with during raids/wars (highest priority).\n  Format:\n - Specific block: 'modid:blockname' (e.g., 'minecraft:bedrock', 'minecolonies:blockhuttownhall')\n  - Entire mod: '#modid' (e.g., '#refinedstorage', '#mekanism')\n These blocks are completely protected and override any whitelist or permission settings.\n  Default blacklist prevents interaction with critical infrastructure blocks.
- **Default Value**: $defVal 

### BlockFilterWars
- **Description**: Apply block interaction filter during wars.\n If enabled, blacklist/whitelist rules will be enforced during active wars.
- **Default Value**: $defVal 

### BlockFilterRaids
- **Description**: Apply block interaction filter during raids.\n If enabled, blacklist/whitelist rules will be enforced during active raids.
- **Default Value**: $defVal 

### RequiredGuardTowersForBoost
- **Description**:  Number of Guard Towers required to activate a tax boost for all buildings in a colony.
- **Default Value**: $defVal 

### GuardTowerTaxBoostPercentage
- **Description**:  Percentage increase in total tax revenue when required Guard Towers are built. This acts as a multiplier on the colony's total generated tax income.  For example: 0.5 = 50% increase, so 1000 tax becomes 1500 tax.
- **Default Value**: $defVal 

### barracksMaintenance
- **Description**: Base maintenance cost per hour for Barracks (Range: 0-10000)
- **Default Value**: $defVal 

### barracksMaintenanceUpgrade
- **Description**: Additional maintenance per level for Barracks (Range: 0-1000)
- **Default Value**: $defVal 

### guardtowerMaintenance
- **Description**: Base maintenance cost per hour for Guard Tower (Range: 0-10000)
- **Default Value**: $defVal 

### guardtowerMaintenanceUpgrade
- **Description**: Additional maintenance per level for Guard Tower (Range: 0-1000)
- **Default Value**: $defVal 

### barrackstowerMaintenance
- **Description**: Base maintenance cost per hour for Barracks Tower (Range: 0-10000)
- **Default Value**: $defVal 

### barrackstowerMaintenanceUpgrade
- **Description**: Additional maintenance per level for Barracks Tower (Range: 0-1000)
- **Default Value**: $defVal 

### archeryMaintenance
- **Description**: Base maintenance cost per hour for Archery (Range: 0-10000)
- **Default Value**: $defVal 

### archeryMaintenanceUpgrade
- **Description**: Additional maintenance per level for Archery (Range: 0-1000)
- **Default Value**: $defVal 

### combatacademyMaintenance
- **Description**: Base maintenance cost per hour for Combat Academy (Range: 0-10000)
- **Default Value**: $defVal 

### combatacademyMaintenanceUpgrade
- **Description**: Additional maintenance per level for Combat Academy (Range: 0-1000)
- **Default Value**: $defVal 

### archery
- **Description**: Base tax for Archery
- **Default Value**: $defVal 

### archeryUpgrade
- **Description**: Tax increase per level for // Archery
- **Default Value**: $defVal 

### alchemist
- **Description**: Base tax for Alchemist
- **Default Value**: $defVal 

### alchemistUpgrade
- **Description**: Tax increase per level for Alchemist
- **Default Value**: $defVal 

### concretemixer
- **Description**: Base tax for Concrete Mixer
- **Default Value**: $defVal 

### concretemixerUpgrade
- **Description**: Tax increase per level for Concrete Mixer
- **Default Value**: $defVal 

### fletcher
- **Description**: Base tax for Fletcher
- **Default Value**: $defVal 

### fletcherUpgrade
- **Description**: Tax increase per level for Fletcher
- **Default Value**: $defVal 

### lumberjack
- **Description**: Base tax for Lumberjack
- **Default Value**: $defVal 

### lumberjackUpgrade
- **Description**: Tax increase per level for Lumberjack
- **Default Value**: $defVal 

### rabbithutch
- **Description**: Base tax for Rabbit Hutch
- **Default Value**: $defVal 

### rabbithutchUpgrade
- **Description**: Tax increase per level for Rabbit Hutch
- **Default Value**: $defVal 

### shepherd
- **Description**: Base tax for Shepherd
- **Default Value**: $defVal 

### shepherdUpgrade
- **Description**: Tax increase per level for Shepherd
- **Default Value**: $defVal 

### smeltery
- **Description**: Base tax for Smeltery
- **Default Value**: $defVal 

### smelteryUpgrade
- **Description**: Tax increase per level for Smeltery
- **Default Value**: $defVal 

### swineherder
- **Description**: Base tax for Swine Herder
- **Default Value**: $defVal 

### swineherderUpgrade
- **Description**: Tax increase per level for Swine Herder
- **Default Value**: $defVal 

### townhall
- **Description**: Base tax for Town Hall
- **Default Value**: $defVal 

### townhallUpgrade
- **Description**: Tax increase per level for Town Hall
- **Default Value**: $defVal 

### warehousedeliveryman
- **Description**: Base tax for Warehouse Deliveryman
- **Default Value**: $defVal 

### warehousedeliverymanUpgrade
- **Description**: Tax increase per level for Warehouse Deliveryman
- **Default Value**: $defVal 

### bakery
- **Description**: Base tax for Bakery
- **Default Value**: $defVal 

### bakeryUpgrade
- **Description**: Tax increase per level for Bakery
- **Default Value**: $defVal 

### barracks
- **Description**: Base tax for Barracks
- **Default Value**: $defVal 

### barracksUpgrade
- **Description**: Tax increase per level for // Barracks
- **Default Value**: $defVal 

### barrackstower
- **Description**: Base tax for Barracks // Tower
- **Default Value**: $defVal 

### barrackstowerUpgrade
- **Description**: Tax increase per level // for Barracks Tower
- **Default Value**: $defVal 

### blacksmith
- **Description**: Base tax for Blacksmith
- **Default Value**: $defVal 

### blacksmithUpgrade
- **Description**: Tax increase per level for Blacksmith
- **Default Value**: $defVal 

### builder
- **Description**: Base tax for Builder
- **Default Value**: $defVal 

### builderUpgrade
- **Description**: Tax increase per level for Builder
- **Default Value**: $defVal 

### chickenherder
- **Description**: Base tax for Chicken Herder
- **Default Value**: $defVal 

### chickenherderUpgrade
- **Description**: Tax increase per level for Chicken Herder
- **Default Value**: $defVal 

### combatacademy
- **Description**: Base tax for Combat // Academy
- **Default Value**: $defVal 

### combatacademyUpgrade
- **Description**: Tax increase per level // for Combat Academy
- **Default Value**: $defVal 

### composter
- **Description**: Base tax for Composter
- **Default Value**: $defVal 

### composterUpgrade
- **Description**: Tax increase per level for Composter
- **Default Value**: $defVal 

### cook
- **Description**: Base tax for Cook
- **Default Value**: $defVal 

### cookUpgrade
- **Description**: Tax increase per level for Cook
- **Default Value**: $defVal 

### cowboy
- **Description**: Base tax for Cowboy
- **Default Value**: $defVal 

### cowboyUpgrade
- **Description**: Tax increase per level for Cowboy
- **Default Value**: $defVal 

### crusher
- **Description**: Base tax for Crusher
- **Default Value**: $defVal 

### crusherUpgrade
- **Description**: Tax increase per level for Crusher
- **Default Value**: $defVal 

### deliveryman
- **Description**: Base tax for Deliveryman
- **Default Value**: $defVal 

### deliverymanUpgrade
- **Description**: Tax increase per level for Deliveryman
- **Default Value**: $defVal 

### farmer
- **Description**: Base tax for Farmer
- **Default Value**: $defVal 

### farmerUpgrade
- **Description**: Tax increase per level for Farmer
- **Default Value**: $defVal 

### fisherman
- **Description**: Base tax for Fisherman
- **Default Value**: $defVal 

### fishermanUpgrade
- **Description**: Tax increase per level for Fisherman
- **Default Value**: $defVal 

### guardtower
- **Description**: Base tax for Guard Tower
- **Default Value**: $defVal 

### guardtowerUpgrade
- **Description**: Tax increase per level for // Guard Tower
- **Default Value**: $defVal 

### home
- **Description**: Base tax for Residence
- **Default Value**: $defVal 

### homeUpgrade
- **Description**: Tax increase per level for Residence
- **Default Value**: $defVal 

### library
- **Description**: Base tax for Library
- **Default Value**: $defVal 

### libraryUpgrade
- **Description**: Tax increase per level for Library
- **Default Value**: $defVal 

### university
- **Description**: Base tax for University
- **Default Value**: $defVal 

### universityUpgrade
- **Description**: Tax increase per level for University
- **Default Value**: $defVal 

### warehouse
- **Description**: Base tax for Warehouse
- **Default Value**: $defVal 

### warehouseUpgrade
- **Description**: Tax increase per level for Warehouse
- **Default Value**: $defVal 

### tavern
- **Description**: Base tax for Tavern
- **Default Value**: $defVal 

### tavernUpgrade
- **Description**: Tax increase per level for Tavern
- **Default Value**: $defVal 

### miner
- **Description**: Base tax for Miner
- **Default Value**: $defVal 

### minerUpgrade
- **Description**: Tax increase per level for Miner
- **Default Value**: $defVal 

### sawmill
- **Description**: Base tax for Sawmill
- **Default Value**: $defVal 

### sawmillUpgrade
- **Description**: Tax increase per level for Sawmill
- **Default Value**: $defVal 

### stonemason
- **Description**: Base tax for Stonemason
- **Default Value**: $defVal 

### stonemasonUpgrade
- **Description**: Tax increase per level for Stonemason
- **Default Value**: $defVal 

### florist
- **Description**: Base tax for Florist
- **Default Value**: $defVal 

### floristUpgrade
- **Description**: Tax increase per level for Florist
- **Default Value**: $defVal 

### enchanter
- **Description**: Base tax for Enchanter
- **Default Value**: $defVal 

### enchanterUpgrade
- **Description**: Tax increase per level for Enchanter
- **Default Value**: $defVal 

### hospital
- **Description**: Base tax for Hospital
- **Default Value**: $defVal 

### hospitalUpgrade
- **Description**: Tax increase per level for Hospital
- **Default Value**: $defVal 

### glassblower
- **Description**: Base tax for Glassblower
- **Default Value**: $defVal 

### glassblowerUpgrade
- **Description**: Tax increase per level for Glassblower
- **Default Value**: $defVal 

### dyer
- **Description**: Base tax for Dyer
- **Default Value**: $defVal 

### dyerUpgrade
- **Description**: Tax increase per level for Dyer
- **Default Value**: $defVal 

### mechanic
- **Description**: Base tax for Mechanic
- **Default Value**: $defVal 

### mechanicUpgrade
- **Description**: Tax increase per level for Mechanic
- **Default Value**: $defVal 

### plantation
- **Description**: Base tax for Plantation
- **Default Value**: $defVal 

### plantationUpgrade
- **Description**: Tax increase per level for Plantation
- **Default Value**: $defVal 

### graveyard
- **Description**: Base tax for Graveyard
- **Default Value**: $defVal 

### graveyardUpgrade
- **Description**: Tax increase per level for Graveyard
- **Default Value**: $defVal 

### beekeeper
- **Description**: Base tax for Beekeeper
- **Default Value**: $defVal 

### beekeeperUpgrade
- **Description**: Tax increase per level for Beekeeper
- **Default Value**: $defVal 

### netherworker
- **Description**: Base tax for Nether Worker
- **Default Value**: $defVal 

### netherworkerUpgrade
- **Description**: Tax increase per level for Nether Worker
- **Default Value**: $defVal 

### stonesmeltery
- **Description**: Base tax for Stone Smeltery
- **Default Value**: $defVal 

### stonesmelteryUpgrade
- **Description**: Tax increase per level for Stone Smeltery
- **Default Value**: $defVal 

### sifter
- **Description**: Base tax for Sifter
- **Default Value**: $defVal 

### sifterUpgrade
- **Description**: Tax increase per level for Sifter
- **Default Value**: $defVal 

### postbox
- **Description**: Base tax for Postbox
- **Default Value**: $defVal 

### postboxUpgrade
- **Description**: Tax increase per level for Postbox
- **Default Value**: $defVal 

### stash
- **Description**: Base tax for Stash
- **Default Value**: $defVal 

### stashUpgrade
- **Description**: Tax increase per level for Stash
- **Default Value**: $defVal 

### school
- **Description**: Base tax for School
- **Default Value**: $defVal 

### schoolUpgrade
- **Description**: Tax increase per level for School
- **Default Value**: $defVal 

### mysticalsite
- **Description**: Base tax for Mystical Site
- **Default Value**: $defVal 

### mysticalsiteUpgrade
- **Description**: Tax increase per level for Mystical Site
- **Default Value**: $defVal 

### simplequarry
- **Description**: Base tax for Simple Quarry
- **Default Value**: $defVal 

### simplequarryUpgrade
- **Description**: Tax increase per level for Simple Quarry
- **Default Value**: $defVal 

### mediumquarry
- **Description**: Base tax for Medium Quarry
- **Default Value**: $defVal 

### mediumquarryUpgrade
- **Description**: Tax increase per level for Medium Quarry
- **Default Value**: $defVal 

### largequarry
- **Description**: Base tax for Large Quarry
- **Default Value**: $defVal 

### largequarryUpgrade
- **Description**: Tax increase per level for Large Quarry
- **Default Value**: $defVal 

### kitchen
- **Description**: Base tax for Kitchen
- **Default Value**: $defVal 

### kitchenUpgrade
- **Description**: Tax increase per level for Kitchen
- **Default Value**: $defVal 

### gatehouse
- **Description**: Base tax for Gatehouse
- **Default Value**: $defVal 

### gatehouseUpgrade
- **Description**: Tax increase per level for Gatehouse
- **Default Value**: $defVal 


