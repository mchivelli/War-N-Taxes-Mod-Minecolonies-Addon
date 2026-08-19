# Configuration Guide

Below is a list of all server configurations for War 'N Taxes. All settings live in `config/warntax/minecolonytax.toml` on the server.

---

## General Settings

### EnableSDMShopConversion
- **Description**: Enable SDMShop economy integration. When true, tax claims and war payouts use SDMShop balances. When false, physical currency items are used instead.
- **Default Value**: true
- **Note**: If the shop economy is unavailable when you claim tax, the tax is now refunded to the colony (kept claimable) instead of being lost. Use `/wnt debug sdm status` to check why the economy is unavailable.

### SDMCurrencyName
- **Description**: The SDM-Economy currency id that claimed taxes are deposited into. This must match the currency id configured in SDM-Economy (not its display name). If your server uses a currency id other than the default, set it here, otherwise claimed taxes go into a currency you never see.
- **Default Value**: sdm_coin

### CurrencyItemName
- **Description**: The item used as physical currency when SDMShop conversion is disabled (e.g., 'minecraft:emerald').
- **Default Value**: minecraft:emerald

### LogLevel
- **Description**: Logging verbosity. 0 = MINIMAL (errors only), 1 = NORMAL (compact summaries), 2 = DEBUG (full verbose output for troubleshooting).
- **Default Value**: 0

### TaxIntervalMinutes
- **Description**: Tax generation interval in minutes.
- **Default Value**: 60

### MaxTaxRevenue
- **Description**: Maximum tax revenue a colony can store before it stops generating further taxes.
- **Default Value**: 10000

### ShowTaxGenerationLogs
- **Description**: Enable console logging of tax generation details (building upgrades, cap warnings, etc.). Set to false to reduce console spam. Controlled by the LogLevel setting above.
- **Default Value**: false

### DebtLimit
- **Description**: Optional debt limit for colony debt. If > 0, colony revenue will not deduct more once the debt reaches this limit (i.e. the tax value won't drop below -DebtLimit). Set to 0 to disable.
- **Default Value**: 2000

### TaxStealPerGuard
- **Description**: Amount added to colony debt per guard killed when raiding colonies already in debt. Only applies when DebtLimit > 0.
- **Default Value**: 200

### EnableColonyTransfer
- **Description**: Enable colony ownership transfer when a war is won.
- **Default Value**: true

### EnableCorpseRetrievalInColonies
- **Description**: Allows players to interact with their **own** corpse (Corpse mod) inside foreign colonies even when colony permissions would block the interaction. Only the corpse owner is exempted; other players' corpses remain protected. Has no effect if the Corpse mod is not installed.
- **Default**: `true`

### EnableWarActions
- **Description**: If false, war will not toggle any colony interaction permissions.
- **Default Value**: true

### WarAcceptanceRequired
- **Description**: If true, war declarations must be manually accepted by the defender. If false, wars start immediately with no option to refuse.
- **Default Value**: true

### AttackerGracePeriodMinutes
- **Description**: Grace period between declaring wars (minutes). After declaring a war, the attacker cannot declare another for this duration.
- **Default Value**: 120

### RaidGracePeriodMinutes
- **Description**: Grace period between raids (minutes). After a raid, this player cannot raid again for this duration.
- **Default Value**: 120

### MaxRaidDurationMinutes
- **Description**: Maximum raid duration in minutes. The raid ends automatically when this time expires.
- **Default Value**: 5

### RaidPenaltyPercentage
- **Description**: Penalty percentage applied to a raider's balance when killed by a defender during a raid (0.0-1.0).
- **Default Value**: 0.25

### RaidDefenseRewardPercentage
- **Description**: Percentage of the killed raider's balance transferred to the defending colony owner or officers to claim (0.0-1.0).
- **Default Value**: 0.15

### WarVictoryPercentage
- **Description**: Percentage of losing players' balance awarded to each winning player. Set to 0.0 to only enable colony transfer (if enabled).
- **Default Value**: 0.20

### WarDefeatPercentage
- **Description**: Percentage each losing player loses from their balance when defeated in war. Should match WarVictoryPercentage to prevent money creation.
- **Default Value**: 0.20

### WarStalematePercentage
- **Description**: Percentage all war participants lose from their balance when a war ends in a draw.
- **Default Value**: 0.10

### WarTaxFreezeHours
- **Description**: (Deprecated — use WarTaxFreezeCycles instead.) Duration in hours to freeze colony tax generation after a war loss or stalemate. Set to 0 to disable. Ignored if WarTaxFreezeCycles is greater than 0.
- **Default Value**: 0

### WarTaxFreezeCycles
- **Description**: Number of tax cycles to skip after a war loss or stalemate. Cycle-based freezing ensures consistent behavior regardless of TaxIntervalMinutes. At default 60-minute intervals, 3 cycles = 3 hours. Takes precedence over WarTaxFreezeHours.
- **Default Value**: 3

### MaxCombinedWarPenaltyPercent
- **Description**: Maximum combined penalty from war exhaustion and active-war tax reduction (0.0-1.0). Prevents excessive stacking. Example: 0.5 = 50% maximum total penalty.
- **Default Value**: 0.5

### MinTaxGenerationPercent
- **Description**: Minimum tax generation floor as a fraction of base generation (0.0-1.0). After all penalties stack (raid, war exhaustion, reparations, events), tax income will never drop below this fraction. Prevents permanent economic collapse.
- **Default Value**: 0.30

### AllowOfflineRaids
- **Description**: Allow players to raid colonies when the colony owner is offline.
- **Default Value**: true

### PeaceProposalTimeoutSeconds
- **Description**: Seconds before an unanswered peace proposal auto-expires.
- **Default Value**: 300

### EnableOutpostVulnerability
- **Description**: When enabled, secondary colonies (outposts) can be attacked even when the owner is offline. Guards defend automatically. Primary colonies (the player's first colony) can only be attacked when the owner is online.
- **Default Value**: true

### EnableColonyWager
- **Description**: When enabled, the attacker's colony becomes a WAGER in every war. If the defender wins, the attacker's colony enters an occupied state controlled by the defender. This creates high-risk warfare where aggressors cannot safely bully weaker players.
- **Default Value**: true

### EnableWarVassalization
- **Description**: When enabled and EnableColonyTransfer is disabled, winning a war will vassalize the losing colony instead of transferring ownership. The losing colony pays a percentage of their tax income to the winner for a set duration.
- **Default Value**: true

### WarVassalizationTributePercentage
- **Description**: Percentage of the vassal colony's tax income paid to the victor as tribute each tax cycle (1-100).
- **Default Value**: 15

### WarVassalizationDurationHours
- **Description**: Duration in hours that a war vassalization lasts. After this time the vassalization ends automatically. Set to 0 for permanent vassalization until manually revoked.
- **Default Value**: 168

### WarVassalizationTreasuryGrabPercent
- **Description**: One-time "spoils of war" grab applied to the losing colony's Treasury when a war win vassalizes the colony (the vassalize-only path, used when colony deed transfer is disabled). This percentage is moved from the loser's treasury to the victor's primary colony treasury immediately on victory. This is the colony-side half of the "take the huge money" rule. Set 0 to disable.
- **Default Value**: 50

### WarVassalizationPlayerBalanceGrabPercent
- **Description**: One-time grab applied to the losing player's personal wallet balance when a war win vassalizes their colony. Requires an economy mod (SDMShop/SDMEconomy). This is the player-side half of the "take the huge money from the player" rule. Set 0 to disable. The matching access toggle, `VassalLockOutFormerOwner`, lives in the Besiege System section.
- **Default Value**: 25

### MinGuardsToRaid
- **Description**: Minimum number of guards required to initiate a raid. Only used when EnableRaidBuildingRequirements is disabled. If building requirements are enabled, they take priority.
- **Default Value**: 3

### EnableRaidGuardProtection
- **Description**: When enabled, colonies must meet minimum defense requirements to be eligible as raid targets. Protects smaller or newer colonies from being overwhelmed.
- **Default Value**: true

### MinGuardsToBeRaided
- **Description**: Minimum number of guards a colony must have before it can be raided. Set to 0 to disable.
- **Default Value**: 2

### MinGuardTowersToBeRaided
- **Description**: Minimum number of guard towers a colony must have before it can be raided. Set to 0 to disable.
- **Default Value**: 1

### RaidTaxIntervalSeconds
- **Description**: Interval in seconds between automatic tax transfers during a raid.
- **Default Value**: 60

### EnableEntityRaids
- **Description**: Enable entity-triggered raids. When disabled, hostile entity groups near a colony will not trigger a raid even if they meet the criteria. Disabled by default.
- **Default Value**: false

### EntityRaidWhitelist
- **Description**: List of entity types that can trigger raids. Use Minecraft entity resource IDs (e.g., 'minecraft:pillager'). Default: only Pillagers.
- **Default Value**: ["minecraft:pillager"]

### EntityRaidThreshold
- **Description**: Number of whitelisted entities near a colony required to trigger an entity raid.
- **Default Value**: 1

### EntityRaidDetectionRadius
- **Description**: Radius in blocks to scan around colonies for entity raid detection.
- **Default Value**: 50

### EntityRaidMessageOnly
- **Description**: If true, entity raids only send alert messages to the colony without triggering actual raid mechanics.
- **Default Value**: false

### EntityRaidBoundaryTimerSeconds
- **Description**: Seconds entities have to return to colony boundaries after leaving during an active entity raid.
- **Default Value**: 5

### EntityRaidCheckIntervalTicks
- **Description**: How often (in ticks) to scan for entities near colonies. 20 ticks = 1 second. Lower values increase check frequency at the cost of performance.
- **Default Value**: 20

### EntityRaidCooldownMinutes
- **Description**: Cooldown in minutes between entity raids for the same colony.
- **Default Value**: 30

### EnableEntityRaidDebug
- **Description**: Enable detailed debug logging for the entity raid system.
- **Default Value**: false

### EntityRaidDebugLevel
- **Description**: Verbosity of entity raid debug logging. 1 = basic, 2 = detailed, 3 = verbose.
- **Default Value**: 1

### BypassAllianceChecks
- **Description**: Testing only. Bypass alliance checks to allow your own recruits to trigger raids. Keep false in production.
- **Default Value**: false

### EnablePvPKillEconomy
- **Description**: When enabled, killing a player in war transfers a percentage of their balance to the killer. Compatible with SDMShop and SDMEconomy. Disabled by default.
- **Default Value**: false

### PvPKillRewardPercentage
- **Description**: Percentage of victim's balance transferred to the killer on a PvP kill (0.0-1.0). Example: 0.1 = 10%.
- **Default Value**: 0.10

### EnableGeneralItemInteractions
- **Description**: When enabled, non-colony members may toss and pick up items within colony boundaries.
- **Default Value**: true

### GeneralColonyActions
- **Description**: Colony permission actions allowed for all players regardless of rank. See the MineColonies Action API for valid values.
- **Default Value**: ["TOSS_ITEM", "PICKUP_ITEM"]

### EnableGuardResistanceDuringRaids
- **Description**: When enabled, guards receive a resistance effect during active raids to help defend the colony.
- **Default Value**: true

### GuardResistanceLevel
- **Description**: Level of resistance applied to guards during raids (0-255). Higher = more protection. Set to 0 to disable the effect while keeping the feature enabled.
- **Default Value**: 2

### EnableHappinessTaxModifier
- **Description**: When enabled, colony citizen happiness affects tax generation rates. Low happiness penalizes revenue; high happiness provides a bonus.
- **Default Value**: true

### HappinessTaxMultiplierMin
- **Description**: Minimum tax multiplier for very unhappy colonies (0.1-1.0).
- **Default Value**: 0.5

### HappinessTaxMultiplierMax
- **Description**: Maximum tax multiplier for very happy colonies (1.0-2.0).
- **Default Value**: 1.5

### EnableColonyInactivityTaxPause
- **Description**: When enabled, colonies whose owner and officers have not visited for the configured duration stop generating taxes.
- **Default Value**: true

### ColonyInactivityHoursThreshold
- **Description**: Hours of inactivity after which a colony stops generating taxes. Uses MineColonies built-in visit tracking.
- **Default Value**: 168

### EnableExtortionSystem
- **Description**: Enable extortion. When declaring war, the attacker can demand a percentage of the defender's balance as payment to call off the war.
- **Default Value**: true

### DefaultExtortionPercentage
- **Description**: Default extortion demand as a fraction of the defender's balance when the attacker does not specify a custom amount (0.0-1.0).
- **Default Value**: 0.15

### ExtortionResponseTimeMinutes
- **Description**: Minutes the defender has to respond to an extortion demand before the war starts automatically.
- **Default Value**: 5

### ExtortionImmunityHours
- **Description**: Hours of immunity from new extortion attempts granted to the defender after they successfully pay.
- **Default Value**: 24

### EnableCitizenMilitia
- **Description**: When enabled, a configurable portion of eligible citizens convert into temporary militia guards during raids.
- **Default Value**: true

### MilitiaConversionPercentage
- **Description**: Fraction of eligible citizens converted into militia during raids (0.0-1.0). Example: 0.3 = 30%.
- **Default Value**: 0.30

### MilitiaMinCitizenLevel
- **Description**: Minimum citizen job level required to be eligible for militia conversion.
- **Default Value**: 3

### MilitiaGuardsSeekRaiders
- **Description**: If true, militia and guards actively chase raiders instead of defending only their assigned posts.
- **Default Value**: true

### TaxStealPerGuardKilled
- **Description**: If true, tax theft is calculated per guard or militia killed (the recommended system). If false, uses the older time-based theft system.
- **Default Value**: true

### TaxStealPercentagePerGuard
- **Description**: Percentage of colony tax stolen per guard or militia killed during a raid (0.0-1.0).
- **Default Value**: 0.10

### MaxRaidTaxPercentage
- **Description**: Maximum total percentage of colony tax that can be stolen in a single raid (0.0-1.0). Example: 0.25 = 25% max when all defenders are killed.
- **Default Value**: 0.25

### ApplyResistanceToCitizens
- **Description**: If true, the resistance effect during raids and wars is also applied to all citizens, not only guards.
- **Default Value**: false

### EnableColonyAbandonmentSystem
- **Description**: Master switch for the entire automatic colony abandonment and owner-repair system. When false (the default), the mod never automatically rewrites a colony's owner or permissions. This is the safe default and prevents the colony-data corruption that could occur when owner-repair ran while colonies were still loading. When true, the inactivity auto-abandon, debt-bankruptcy abandonment, null-owner repair, and abandoned-entry cleanup all become active (each still has its own sub-toggle).
- **Default Value**: false
- **Note**: A one-time, removal-only cleanup of legacy `[AUTO_OWNER]` placeholder entries written by older versions always runs on startup regardless of this switch, so existing worlds are healed automatically. EnableColonyAutoAbandon below has no effect unless this master switch is also true.

### EnableColonyAutoAbandon
- **Description**: When enabled, colonies whose owners and officers have not physically visited for the configured number of days are automatically abandoned. Requires EnableColonyAbandonmentSystem to also be true.
- **Default Value**: true

### ColonyAutoAbandonDays
- **Description**: Days of inactivity (no owner or officer visit) before a colony is automatically abandoned.
- **Default Value**: 14

### NotifyOwnersBeforeAbandon
- **Description**: If true, owners and officers receive warnings when they log in during the pre-abandonment window.
- **Default Value**: true

### AbandonWarningDays
- **Description**: Days before the abandonment deadline during which warning notifications are sent on login.
- **Default Value**: 3

### EnableListAbandonedForAll
- **Description**: When false (default), only server operators can use /wnt listabandoned. When true, all players can see the list of abandoned colonies.
- **Default Value**: false

### ResetTimerOnOfficerLogin
- **Description**: Experimental. When false (recommended), the abandonment timer only resets when an officer physically visits the colony. When true, simply logging into the server resets the timer for all colonies that officer manages.
- **Default Value**: false

### EnableAbandonedColonyClaiming
- **Description**: When enabled, players can claim abandoned colonies using /wnt claimcolony, triggering a raid where all citizens become hostile.
- **Default Value**: true

### MinGuardsForClaimingRaid
- **Description**: Minimum number of guards the claiming player's colony must have to be eligible to claim an abandoned colony.
- **Default Value**: 3

### ClaimingRaidDurationMinutes
- **Description**: Duration of the claiming raid in minutes. The claimer must survive this long and eliminate all defenders to succeed.
- **Default Value**: 5

### ClaimingGracePeriodHours
- **Description**: Hours a player must wait after claiming or attempting to claim a colony before they can claim another.
- **Default Value**: 24

### SpawnMercenariesIfLowDefenders
- **Description**: If true and the abandoned colony has fewer than 5 citizens or guards, mercenaries spawn to ensure the claimer still faces a meaningful challenge.
- **Default Value**: true

### ClaimingBuildingRequirements
- **Description**: Building requirements the claiming player must meet. Format: 'building:level'. Leave empty to disable building requirements.
- **Default Value**: townhall:2

### EnableRaidBuildingRequirements
- **Description**: When enabled, building requirements take priority over MinGuardsToRaid for determining raid eligibility.
- **Default Value**: true

### RaidBuildingRequirements
- **Description**: Building requirements to initiate a raid. Format: 'building:level:amount'. Example: 'townhall:1:1,guardtower:1:3'. Leave empty to disable.
- **Default Value**: townhall:1:1,guardtower:1:3

### EnableWarBuildingRequirements
- **Description**: When enabled, building requirements take priority over MinGuardsToWageWar for determining war eligibility.
- **Default Value**: true

### WarBuildingRequirements
- **Description**: Building requirements to declare war. Format: 'building:level:amount'. Example: 'townhall:2:1,guardtower:1:3,buildershut:1:1,house:1:1'. Leave empty to disable.
- **Default Value**: townhall:2:1,guardtower:1:3,buildershut:1:1,house:1:1

### DisableHutRecipes
- **Description**: Disable all MineColonies building hut crafting recipes. When enabled, players must obtain building huts through SDMShop or admin shop. Disabled by default.
- **Default Value**: false

### DatabaseEnabled
- **Description**: Enable the MySQL/MariaDB statistics database. When enabled, war results, raid history, kills, and colony state are written to a shared database that your website container can also query. Both the game server and the website connect to the same database via TCP — no file sharing required. Get credentials from the Pterodactyl panel under server > Databases. Default: false
- **Default Value**: false

### DatabaseHost
- **Description**: Hostname or IP of the MySQL/MariaDB server. On Pterodactyl, copy this from the Databases tab (usually something like `db.pterodactyl.example.com` or an internal Docker network address). Default: localhost
- **Default Value**: localhost

### DatabasePort
- **Description**: Port of the MySQL/MariaDB server. The standard MySQL port is 3306. Change only if your database panel specifies a different port. Default: 3306
- **Default Value**: 3306

### DatabaseName
- **Description**: Name of the database to write to. Copy from the Pterodactyl Databases tab. The mod creates all required tables automatically on first startup. Default: warntax
- **Default Value**: warntax

### DatabaseUsername
- **Description**: Database username. Copy from the Pterodactyl Databases tab. Default: warntax
- **Default Value**: warntax

### DatabasePassword
- **Description**: Database password. Copy from the Pterodactyl Databases tab. Keep this out of screenshots and public configs. Default: (empty — you must set this)
- **Default Value**: (empty)

### DatabaseSnapshotIntervalSeconds
- **Description**: How often (in seconds) the mod writes a full colony-state snapshot to the database. Lower values give the website fresher data but increase database writes. Default: 300 (5 minutes)
- **Default Value**: 300

### GivePatchouliBookOnJoin
- **Description**: Give players the War and Taxes Codex (Patchouli guide book) when they first join the server. Requires Patchouli to be installed. The book is only given once per player.
- **Default Value**: true

### ShowAdminPagesInBook
- **Description**: Show admin and configuration pages in the Patchouli guide book. When disabled, the Configuration category is hidden from normal players.
- **Default Value**: false

### EnableTaxPolicies
- **Description**: Enable tax policy system. Colonies can choose policies that affect tax generation and citizen happiness.
- **Default Value**: true

### LowPolicyRevenueModifier
- **Description**: Revenue modifier for the LOW tax policy. Negative = less tax. Example: -0.25 = 25% less revenue.
- **Default Value**: -0.25

### LowPolicyHappinessModifier
- **Description**: Happiness modifier for the LOW tax policy. Additive to colony average happiness (0-10 scale). Example: 0.20 = +0.2 happiness.
- **Default Value**: 0.20

### HighPolicyRevenueModifier
- **Description**: Revenue modifier for the HIGH tax policy. Positive = more tax. Example: 0.25 = 25% more revenue.
- **Default Value**: 0.25

### HighPolicyHappinessModifier
- **Description**: Happiness modifier for the HIGH tax policy. Additive to colony average happiness. Example: -0.15 = -0.15 happiness.
- **Default Value**: -0.15

### WarPolicyRevenueModifier
- **Description**: Revenue modifier for the WAR ECONOMY tax policy. High boost during wartime. Example: 0.50 = 50% more revenue.
- **Default Value**: 0.50

### WarPolicyHappinessModifier
- **Description**: Happiness modifier for the WAR ECONOMY tax policy. Example: -0.25 = -0.25 happiness.
- **Default Value**: -0.25

### EnableTaxReports
- **Description**: Enable tax report generation as written books.
- **Default Value**: true

### TaxReportAutoDeleteHours
- **Description**: Hours before tax report books auto-delete from inventory. Set to 0 to never auto-delete.
- **Default Value**: 0

### TaxReportMaxHistoryDays
- **Description**: Maximum days of tax history to track for reports.
- **Default Value**: 30

### EnableLeaderboards
- **Description**: Enable server-wide leaderboards for tax earnings, wars won, etc.
- **Default Value**: true

### LeaderboardUpdateIntervalMinutes
- **Description**: How often leaderboards are recalculated in minutes.
- **Default Value**: 60

### LeaderboardTopCount
- **Description**: Number of entries to display in leaderboard commands.
- **Default Value**: 10

### EnableFactionSystem
- **Description**: Enable the faction system. Colonies can form factions with shared tax pools and trade routes.
- **Default Value**: true

### MaxFactionMembers
- **Description**: Maximum number of colonies that can belong to a single faction.
- **Default Value**: 10

### FactionCreationCost
- **Description**: Tax cost to create a new faction.
- **Default Value**: 5000

### FactionAllianceLimit
- **Description**: Maximum number of allied factions a faction can have.
- **Default Value**: 3

### EnableSharedTaxPool
- **Description**: Enable shared tax pool for factions. Member colonies contribute a configurable percentage to the pool.
- **Default Value**: true

### DefaultPoolContributionPercent
- **Description**: Default percentage of tax income that faction members contribute to the shared pool.
- **Default Value**: 10

### MaxPoolBalance
- **Description**: Maximum balance the shared tax pool can hold.
- **Default Value**: 100000

### PoolHistoryRetentionDays
- **Description**: Days to retain pool transaction history for reporting.
- **Default Value**: 30

### EnableTradeRoutes
- **Description**: Enable trade routes between allied colonies that generate passive income based on distance. Disabled by default.
- **Default Value**: false

### TradeRouteIncomePerChunk
- **Description**: Tax income generated per chunk of distance on a trade route each tax cycle.
- **Default Value**: 1

### TradeRouteMaxDistanceChunks
- **Description**: Maximum distance in chunks for a trade route.
- **Default Value**: 100

### TradeRouteMaintenanceCost
- **Description**: Maintenance cost per trade route per tax cycle.
- **Default Value**: 25

### MaxTradeRoutesPerColony
- **Description**: Maximum number of active trade routes a colony can have.
- **Default Value**: 2

### EnableSpySystem
- **Description**: Enable the spy system. Colonies can deploy spies to gather intelligence and perform covert actions on rivals.
- **Default Value**: true

### SpyCooldownMinutes
- **Description**: Cooldown in minutes between spy deployments against the same target colony.
- **Default Value**: 60

### SpyDetectionBaseChance
- **Description**: Base detection chance (0.0-1.0) applied to all spy actions. Each action type adds its own additional modifier.
- **Default Value**: 0.05

### MaxActiveSpiesPerPlayer
- **Description**: Maximum number of concurrent active spy missions per player.
- **Default Value**: 3

### ScoutMaxDurationHours
- **Description**: Maximum hours a SCOUT mission can run before the spy auto-recalls.
- **Default Value**: 24

### ScoutCost
- **Description**: Treasury cost to deploy a SCOUT spy (reveals colony stats and member list over time).
- **Default Value**: 100

### SabotageCost
- **Description**: Treasury cost to deploy a SABOTAGE spy (reduces target's next tax cycle).
- **Default Value**: 1500

### BribeGuardsCost
- **Description**: Treasury cost to deploy a BRIBE GUARDS spy (disables a number of guards before the target's next raid defense).
- **Default Value**: 2500

### StealSecretsCost
- **Description**: Treasury cost to deploy a STEAL SECRETS spy (temporarily boosts your own tax income).
- **Default Value**: 1000

### SabotageTaxReductionPercent
- **Description**: Fraction of the target's tax cycle reduced when a SABOTAGE mission succeeds (0.0-1.0).
- **Default Value**: 0.25

### SabotageMinFieldMinutes
- **Description**: Minutes a SABOTAGE spy must remain active before the sabotage takes effect. Spies detected before this threshold apply no effect on escape.
- **Default Value**: 30

### BribeGuardsDisabledCount
- **Description**: Number of guards disabled when a BRIBE GUARDS mission succeeds.
- **Default Value**: 2

### StealSecretsDurationHours
- **Description**: Duration in hours that the STEAL SECRETS income bonus lasts.
- **Default Value**: 24

### ScoutDetectionChance
- **Description**: Additional detection chance for SCOUT missions (0.0-1.0). Low risk.
- **Default Value**: 0.05

### SabotageDetectionChance
- **Description**: Additional detection chance for SABOTAGE missions (0.0-1.0). Medium risk.
- **Default Value**: 0.25

### BribeGuardsDetectionChance
- **Description**: Additional detection chance for BRIBE GUARDS missions (0.0-1.0). High risk.
- **Default Value**: 0.45

### StealSecretsDetectionChance
- **Description**: Additional detection chance for STEAL SECRETS missions (0.0-1.0). Medium-high risk.
- **Default Value**: 0.30

### IntelEarlyThresholdMinutes
- **Description**: Minutes a spy must be active before Tier 1 intel is gathered (colony name, citizen count, building count, war status).
- **Default Value**: 2

### IntelMidThresholdMinutes
- **Description**: Minutes a spy must be active before Tier 2 intel is gathered (guard count, happiness, tax balance).
- **Default Value**: 10

### IntelLateThresholdMinutes
- **Description**: Minutes a spy must be active before Tier 3 intel is gathered (owner balance, officer list, full member list).
- **Default Value**: 30

### FleeSpeedMultiplier
- **Description**: Speed multiplier applied to a fleeing spy (1.0 = normal speed).
- **Default Value**: 1.5

### FleeEscapeDistance
- **Description**: Blocks past the colony border a spy must reach to be considered successfully escaped.
- **Default Value**: 5

### FleeMaxSeconds
- **Description**: Maximum seconds a spy has to flee before being caught.
- **Default Value**: 30

### SpyTravelBlocksPerMinute
- **Description**: Spy travel speed in blocks per minute. Determines travel time from your colony to the target.
- **Default Value**: 200

### SpyTravelMinMinutes
- **Description**: Minimum travel time in minutes regardless of distance.
- **Default Value**: 1

### SpyTravelMaxMinutes
- **Description**: Maximum travel time cap in minutes regardless of distance.
- **Default Value**: 15

### SpyMapEnabled
- **Description**: When true, a filled map of the target colony area is given to the player when their spy mission completes successfully (completed, escaped, or recalled).
- **Default Value**: true

### SpyMapScale
- **Description**: Scale of the returned spy map. 0 = 128 blocks, 1 = 256 blocks, 2 = 512 blocks, 3 = 1024 blocks, 4 = 2048 blocks.
- **Default Value**: 1

### EnableTreasury
- **Description**: Enable the treasury system. Colonies must have funds in the treasury to declare war. The treasury drains during active wars.
- **Default Value**: true

### TreasuryMinPercentOfTarget
- **Description**: Minimum treasury balance required as a percentage of the target colony's tax balance (0.0-1.0). Example: 0.25 = need 25% of target's balance to declare war.
- **Default Value**: 0.25

### TreasuryMinPercentOfOwnTax
- **Description**: Also requires this fraction of the attacker's own tax balance in the treasury (0.0-1.0). Prevents wealthy colonies from cheaply bullying poor ones. Example: 0.10 = also need 10% of your own balance.
- **Default Value**: 0.10

### TreasuryDrainPerMinute
- **Description**: Flat amount drained from treasury per minute during active war. Only used when TreasuryDrainUsePercent is false. At default (10/min on a 25k treasury), a war lasts approximately 4 hours.
- **Default Value**: 10

### TreasuryDrainUsePercent
- **Description**: If true, treasury drains by a percentage of max capacity per minute (proportional burn). If false, uses the flat TreasuryDrainPerMinute value.
- **Default Value**: true

### TreasuryDrainPercent
- **Description**: Percentage of treasury max capacity drained per minute during active war (0.0-1.0). Only used when TreasuryDrainUsePercent is true. Example: 0.004 = 0.4% of max capacity per minute.
- **Default Value**: 0.004

### TreasuryMaxCapacity
- **Description**: Maximum capacity of the treasury. Prevents excessive stockpiling.
- **Default Value**: 25000

### TreasuryAutoSurrenderEnabled
- **Description**: If true, war automatically ends in surrender when the treasury is depleted.
- **Default Value**: true

### TreasuryAutoDepositPercent
- **Description**: Percentage of tax revenue to automatically deposit into the treasury each tax cycle (0.0-1.0). Example: 0.05 = 5% of each tax collection auto-deposited.
- **Default Value**: 0.05

### WarDefenderDrainReduction
- **Description**: Fraction by which the defending colony's treasury drain is reduced during war (0.0-1.0). Example: 0.5 = defender drains at half the normal rate (home field advantage).
- **Default Value**: 0.5

### RaidTreasuryEnabled
- **Description**: If true, raids require treasury funds. Cost is a one-time payment based on the target's tax generation.
- **Default Value**: true

### RaidTreasuryCostPercent
- **Description**: Raid cost as a percentage of the target colony's tax per interval (0.0-1.0). Example: 0.10 = raiding costs 10% of what target earns per tax cycle.
- **Default Value**: 0.10

### RaidPenaltyTaxReductionPercent
- **Description**: Percentage reduction in tax generation for a raided colony (0.0-1.0). Example: 0.25 = 25% less tax after being raided.
- **Default Value**: 0.25

### RaidPenaltyDurationHours
- **Description**: Duration in hours that the post-raid tax penalty lasts.
- **Default Value**: 24

### RaidRepairCostPercent
- **Description**: Cost to repair colony and remove raid penalty early, as a percentage of colony's total tax earnings (0.0-1.0). Example: 0.10 = repairing costs 10% of colony's tax balance.
- **Default Value**: 0.10

### EnableWarExhaustion
- **Description**: Enable war exhaustion. Colonies generate less tax during active wars and need recovery time afterward.
- **Default Value**: true

### WarTaxReductionPercent
- **Description**: Percentage reduction in tax generation during an active war (0.0-1.0). Example: 0.30 = 30% less tax.
- **Default Value**: 0.30

### PostWarRecoveryHours
- **Description**: Hours after a war ends for colony tax generation to fully recover to normal.
- **Default Value**: 48

### EnableWarReparations
- **Description**: Enable the war reparations debuff for colonies that lose wars frequently within a short window.
- **Default Value**: true

### ReparationsTaxPenaltyPercent
- **Description**: Tax generation penalty while under reparations (0.0-1.0). Example: 0.20 = 20% less tax.
- **Default Value**: 0.20

### ReparationsDurationHours
- **Description**: Duration in hours that the war reparations debuff lasts.
- **Default Value**: 72

### ReparationsTriggerLossesCount
- **Description**: Number of wars lost within 7 days to trigger the reparations debuff.
- **Default Value**: 3

### EnableWarWeariness
- **Description**: Enable war weariness protection. Colonies with very low tax balance that lost a war receive temporary war immunity to allow economic recovery (comeback mechanic).
- **Default Value**: true

### WarWearinessThreshold
- **Description**: Tax balance at or below which a colony qualifies for war weariness immunity after losing a war.
- **Default Value**: 500

### WarWearinessImmunityHours
- **Description**: Hours of war declaration immunity granted to a colony below the weariness threshold after losing a war.
- **Default Value**: 24

### VassalizationReplacesReparations
- **Description**: If true, when a colony is vassalized after a war loss, the ongoing tribute replaces the one-time reparations payment, preventing double-punishment.
- **Default Value**: true

### EnableRansomSystem
- **Description**: Enable ransom system. When the colony owner dies during a raid, the attacker can demand a ransom payment to end the raid early.
- **Default Value**: true

### RansomDefaultPercent
- **Description**: Default ransom amount as a percentage of the victim's tax balance (0.0-1.0). Example: 0.15 = 15%.
- **Default Value**: 0.15

### RansomMinAmount
- **Description**: Minimum ransom amount regardless of percentage calculation.
- **Default Value**: 100

### RansomMaxAmount
- **Description**: Maximum ransom amount cap regardless of percentage calculation.
- **Default Value**: 10000

### RansomTimeoutSeconds
- **Description**: Seconds the victim has to respond to a ransom offer before it expires.
- **Default Value**: 60

### RansomCooldownMinutes
- **Description**: Cooldown in minutes before the same attacker can offer ransom to the same victim again.
- **Default Value**: 30

### RansomImmunityAfterPaymentHours
- **Description**: Hours of immunity from raids granted to a player after they pay a ransom.
- **Default Value**: 4

### EnableInvestments
- **Description**: Enable the temporary investment system (Infrastructure, Guard Training, Festivals, Research). Separate from the permanent Colony Upgrades system.
- **Default Value**: true

### InvestmentDiminishingReturnsFactor
- **Description**: Factor applied to each subsequent investment purchase of the same type (0.0-1.0). Example: 0.80 = each purchase is 80% as effective as the previous.
- **Default Value**: 0.80

### InfrastructureCost
- **Description**: Tax cost for each Infrastructure investment stack (permanent tax generation bonus).
- **Default Value**: 1500

### InfrastructureBonus
- **Description**: Permanent tax generation bonus per Infrastructure investment (0.0-1.0). Example: 0.05 = +5%.
- **Default Value**: 0.05

### InfrastructureMaxStacks
- **Description**: Maximum number of Infrastructure investment stacks a colony can purchase.
- **Default Value**: 5

### GuardTrainingCost
- **Description**: Tax cost for a Guard Training investment (temporary damage bonus for guards).
- **Default Value**: 500

### GuardTrainingDamageBonus
- **Description**: Guard damage bonus while training is active (0.0-1.0). Example: 0.10 = +10% damage.
- **Default Value**: 0.10

### GuardTrainingDurationHours
- **Description**: Duration in hours that the guard training damage bonus lasts.
- **Default Value**: 24

### FestivalCost
- **Description**: Tax cost to host a Festival (temporary happiness bonus for citizens).
- **Default Value**: 2000

### FestivalHappinessBonus
- **Description**: Happiness bonus during the festival. Additive to colony average happiness (0-10 scale). Example: 0.50 = +0.50.
- **Default Value**: 0.50

### FestivalDurationHours
- **Description**: Duration in hours that the festival happiness bonus lasts.
- **Default Value**: 48

### ResearchCost
- **Description**: Tax cost for a Research investment (temporary spy detection bonus).
- **Default Value**: 1500

### ResearchSpyDefenseBonus
- **Description**: Bonus to spy detection chance while research is active (0.0-1.0). Example: 0.50 = 50% higher detection chance.
- **Default Value**: 0.50

### ResearchDurationDays
- **Description**: Duration in days that research spy defense bonus lasts.
- **Default Value**: 7

---

## Colony Upgrades

Colonies can spend treasury funds to permanently improve their capabilities. Upgrades are purchased via `/wnt invest buy <type>` and persist across server restarts in `config/warntax/colony_upgrades.json`.

### EnableColonyUpgrades
- **Description**: Enable the colony upgrade investment system.
- **Default Value**: true

### UpgradeMaxLevel
- **Description**: Maximum level for each upgrade type.
- **Default Value**: 5

### UpgradeCostScalingFactor
- **Description**: Cost multiplier per upgrade level. Example: 2.0 means each level costs double the previous.
- **Default Value**: 2.0

### UpgradeMilitiaCostBase
- **Description**: Base treasury cost for the first level of the Militia upgrade.
- **Default Value**: 500

### UpgradeMilitiaMultiplierPerLevel
- **Description**: Additional fraction of citizens mobilized as militia per upgrade level (0.1 = +10% per level).
- **Default Value**: 0.10

### UpgradeSpyCapacityCostBase
- **Description**: Base treasury cost for the first level of the Spy Capacity upgrade.
- **Default Value**: 300

### UpgradeSpyCapacityPerLevel
- **Description**: Additional concurrent spy missions per upgrade level.
- **Default Value**: 1

### UpgradeSpySpeedCostBase
- **Description**: Base treasury cost for the first level of the Spy Speed upgrade.
- **Default Value**: 200

### UpgradeSpySpeedMultiplierPerLevel
- **Description**: Fraction by which spy travel time is reduced per upgrade level (0.20 = 20% faster per level).
- **Default Value**: 0.20

### UpgradeSpyEvasionCostBase
- **Description**: Base treasury cost for the first level of the Spy Evasion upgrade.
- **Default Value**: 400

### UpgradeDetectionReductionPerLevel
- **Description**: Reduction in enemy spy detection chance per evasion upgrade level (0.05 = -5% per level).
- **Default Value**: 0.05

### UpgradeRaidForceCostBase
- **Description**: Base treasury cost for the first level of the Raid Force upgrade.
- **Default Value**: 600

### UpgradeRaidForceMultiplierPerLevel
- **Description**: Raid attacker effectiveness bonus per upgrade level (0.10 = +10% per level).
- **Default Value**: 0.10

### UpgradeDefenseCostBase
- **Description**: Base treasury cost for the first level of the Defense upgrade.
- **Default Value**: 350

### UpgradeDefenseBonusPerLevel
- **Description**: Additional guard resistance level per Defense upgrade level.
- **Default Value**: 1

### UpgradeTreasuryCapCostBase
- **Description**: Base treasury cost for the first level of the Treasury Cap upgrade.
- **Default Value**: 3000

### UpgradeTreasuryCapFlatPerLevel
- **Description**: Flat treasury capacity increase per Treasury Cap upgrade level.
- **Default Value**: 2000

### UpgradeTaxEfficiencyCostBase
- **Description**: Base treasury cost for the first level of the Tax Efficiency upgrade.
- **Default Value**: 4000

### UpgradeTaxEfficiencyPercentPerLevel
- **Description**: Tax generation bonus per Tax Efficiency upgrade level (fraction, e.g. 0.05 = +5%).
- **Default Value**: 0.05

### UpgradeFortificationCostBase
- **Description**: Base treasury cost for the first level of the Fortification upgrade.
- **Default Value**: 3500

### UpgradeFortificationDamageReductionPerLevel
- **Description**: Fraction of incoming raid damage reduced per Fortification upgrade level (0.05 = 5%). Capped at 75% total.
- **Default Value**: 0.05

### UpgradeCounterIntelCostBase
- **Description**: Base treasury cost for the first level of the Counter Intelligence upgrade.
- **Default Value**: 5000

### UpgradeCounterIntelDetectChancePerLevel
- **Description**: Passive chance per Counter Intelligence upgrade level to auto-detect an incoming spy on arrival (0.10 = 10%). Capped at 90% total.
- **Default Value**: 0.10

---

## Besiege System

### EnableBesiegeSystem
- **Description**: Enable the besiege system. Allows players to raid active non-primary colonies and establish tax vassalage on victory.
- **Default Value**: true

### BesiegeDurationMinutes
- **Description**: Duration of a besiege raid in minutes. Defenders must survive this long to repel the attack.
- **Default Value**: 20

### BesiegeCooldownHours
- **Description**: Hours a player must wait after a besiege attempt (win or loss) before besieging again.
- **Default Value**: 24

### BesiegeMilitiaPercent
- **Description**: Fraction of eligible citizens converted to militia defenders during a besiege (0.0-1.0).
- **Default Value**: 0.6

### BesiegeTributePercent
- **Description**: Percentage of tax income siphoned from the besieged colony to the victor each tax cycle.
- **Default Value**: 30

### BesiegeTributeDurationHours
- **Description**: How long (hours) the besiege vassalage lasts. Set to 0 for permanent until the colony is reclaimed.
- **Default Value**: 72

### BesiegeMinColonySize
- **Description**: Minimum citizen count required in the target colony before it can be besieged.
- **Default Value**: 5

### BesiegeAlliesEnabled
- **Description**: Allow other players to assist the besieger by attacking defenders. They are tracked as allies and also hunted.
- **Default Value**: true

### BesiegeExtraMercenariesPerBuilding
- **Description**: Mercenaries spawned per colony building. Example: 0.33 = approximately 1 mercenary per 3 buildings.
- **Default Value**: 0.33

### BesiegeMaxMercenaries
- **Description**: Maximum number of mercenaries that can be spawned during a single besiege raid.
- **Default Value**: 10

### BesiegePlayerStayRadius
- **Description**: Maximum distance in blocks the besieging player may stray from the colony center. Exceeding this cancels the raid.
- **Default Value**: 100

---

## Random Events

### EnableRandomEvents
- **Description**: Enable the random events system. When enabled, colonies can experience random positive and negative events each tax cycle.
- **Default Value**: true

### RandomEventCheckFrequency
- **Description**: How often to check for events (1 = every tax cycle, 2 = every other cycle, etc.).
- **Default Value**: 1

### RandomEventGlobalCooldownCycles
- **Description**: Minimum tax cycles between any events for a single colony.
- **Default Value**: 2

### RandomEventMaxSimultaneous
- **Description**: Maximum number of events that can be active simultaneously per colony.
- **Default Value**: 2

### RandomEventBaseChanceMultiplier
- **Description**: Global multiplier applied to all event probabilities (0.5-2.0). Lower values make events rarer overall.
- **Default Value**: 1.0

### RandomEventProtectNewColoniesHours
- **Description**: Prevent random events for colonies younger than this many hours.
- **Default Value**: 24

### Individual Event Toggles

Each event can be independently disabled in the config file. The config key names are listed below alongside the event they control.

| Config Key | Event | Default |
|---|---|---|
| `EnableMerchantCaravan` | Merchant Caravan (+15% tax, +0.3 happiness) | true |
| `EnableBountifulHarvest` | Bountiful Harvest (+20% tax, +0.4 happiness) | true |
| `EnableCulturalFestival` | Cultural Festival (-5% tax, +0.5 happiness) | true |
| `EnableSuccessfulRecruitment` | Successful Recruitment (+10% tax, +0.2 happiness) | true |
| `EnableRoyalFeast` | Royal Feast (+10% tax, +0.6 happiness) | true |
| `EnableWanderingTraderOffer` | Wandering Trader Offer (+20% tax, +0.1 happiness) | true |
| `EnableNeighboringAlliance` | Neighboring Alliance (+5% tax, +0.3 happiness) | true |
| `EnableFoodShortage` | Food Shortage (-15% tax, -0.3 happiness) | true |
| `EnableDiseaseOutbreak` | Disease Outbreak (-20% tax, -0.4 happiness) | true |
| `EnableBanditHarassment` | Bandit Harassment (-10% tax, -0.2 happiness) | true |
| `EnableCorruptOfficial` | Corrupt Official (-25% tax, -0.1 happiness) | true |
| `EnableGuardDesertion` | Guard Desertion (-20% tax, -0.4 happiness) | true |
| `EnableLaborStrike` | Labor Strike (-30% tax, -0.5 happiness) | true |
| `EnablePlagueOutbreak` | Plague Outbreak (-25% tax, -0.6 happiness) | true |
| `EnableCropBlight` | Crop Blight (-25% tax, -0.5 happiness) | true |
| `EnableWarProfiteering` | War Profiteering (+35% tax, -0.5 happiness) | true |

---

### EnableMerchantCaravan
- **Description**: Enable the Merchant Caravan event (+15% tax, +0.3 happiness for 2 cycles). Requires 3 or more restaurants or taverns and no active war.
- **Default Value**: true

### WarDurationMinutes
- **Description**: War duration in minutes. The war ends automatically if neither side is eliminated before this time.
- **Default Value**: 120

### MinGuardsToWageWar
- **Description**: Minimum guards required to declare war. Only used when EnableWarBuildingRequirements is disabled.
- **Default Value**: 5

### EnableLPGroupSwitching
- **Description**: When enabled, war participants are moved to the 'war' LuckPerms permission group during active wars. Requires LuckPerms installed and a 'war' group configured.
- **Default Value**: false

### JoinPhaseDurationMinutes
- **Description**: Duration of the join phase in minutes. During this window, allies can join the war before active combat begins.
- **Default Value**: 5

### KeepInventoryOnLastLife
- **Description**: When enabled, players keep their inventory when they die on their last life during a war, preventing permanent gear loss in colony transfer scenarios.
- **Default Value**: true

### PlayerLivesInWar
- **Description**: Number of lives each player has during a war. Losing all lives switches the player to spectator mode for the remainder of the war.
- **Default Value**: 5

### WarActions
- **Description**: Colony permission actions that are granted to enemy players during an active war. See the MineColonies Action API for valid values.
- **Default Value**: PLACE_BLOCKS, BREAK_BLOCKS, TOSS_ITEM, PICKUP_ITEM, ATTACK_CITIZEN, FILL_BUCKET, SHOOT_ARROW, RIGHTCLICK_BLOCK, RIGHTCLICK_ENTITY, ATTACK_ENTITY, EXPLODE, HURT_CITIZEN, HURT_VISITOR, THROW_POTION

### RaidActions
- **Description**: Colony permission actions that are granted to raiders during an active raid.
- **Default Value**: TOSS_ITEM, PICKUP_ITEM, ATTACK_CITIZEN, FILL_BUCKET, SHOOT_ARROW, RIGHTCLICK_BLOCK, RIGHTCLICK_ENTITY, ATTACK_ENTITY, EXPLODE, HURT_CITIZEN, HURT_VISITOR, THROW_POTION

### ClaimingActions
- **Description**: Colony permission actions granted to the claiming player during an abandoned colony claiming raid.
- **Default Value**: PLACE_BLOCKS, BREAK_BLOCKS, TOSS_ITEM, PICKUP_ITEM, ATTACK_CITIZEN, FILL_BUCKET, SHOOT_ARROW, RIGHTCLICK_BLOCK, RIGHTCLICK_ENTITY, ATTACK_ENTITY, EXPLODE, HURT_CITIZEN, HURT_VISITOR, THROW_POTION, OPEN_CONTAINER

### EnableBlockInteractionFilter
- **Description**: Enable the block interaction filter system during raids and wars. Blacklisted blocks cannot be interacted with at all; whitelisted blocks can be opened even when other permissions would deny it.
- **Default Value**: true

### BlockInteractionBlacklist
- **Description**: List of block IDs that cannot be interacted with during raids and wars (highest priority). Format: 'modid:blockname' for a specific block, or '#modid' to block an entire mod. Blacklist overrides whitelist.
- **Default Value**: minecraft:bedrock, minecraft:command_block, minecolonies:blockhuttownhall (and other command/structure blocks)

### BlockInteractionWhitelist
- **Description**: List of block IDs that can be interacted with during raids and wars even when other permissions would deny it. Format: 'modid:blockname' or '#modid' for an entire mod. Blacklist always takes priority over whitelist.
- **Default Value**: minecraft:chest, minecraft:barrel, minecraft:furnace, and other vanilla storage blocks

### BlockFilterWars
- **Description**: Apply block interaction filter rules during active wars.
- **Default Value**: true

### BlockFilterRaids
- **Description**: Apply block interaction filter rules during active raids.
- **Default Value**: true

### RequiredGuardTowersForBoost
- **Description**: Minimum number of Guard Towers before the per-tower tax boost starts applying. Set to 1 so the boost scales linearly from the very first tower.
- **Default Value**: 1

### GuardTowerTaxBoostPercentage
- **Description**: Tax boost percentage added per Guard Tower above the minimum threshold. Example: 0.08 = +8% per tower, so 5 towers = +40% (capped at 80% total).
- **Default Value**: 0.08

## Military Building Maintenance Costs

Military buildings drain the colony treasury passively each hour. The config key names follow the pattern `<building>Maintenance` (base cost) and `<building>MaintenanceUpgrade` (additional cost per level).

| Building | Config Key | Base Cost/hr | Per Level |
|---|---|---|---|
| Barracks | `barracksMaintenance` / `barracksMaintenanceUpgrade` | 15.0 | 5.0 |
| Guard Tower | `guardtowerMaintenance` / `guardtowerMaintenanceUpgrade` | 10.0 | 3.0 |
| Barracks Tower | `barrackstowerMaintenance` / `barrackstowerMaintenanceUpgrade` | 14.0 | 6.0 |
| Archery | `archeryMaintenance` / `archeryMaintenanceUpgrade` | 12.0 | 6.0 |
| Combat Academy | `combatacademyMaintenance` / `combatacademyMaintenanceUpgrade` | 14.0 | 6.0 |

---

## Building Tax Values

Each building type generates a base tax amount per tax cycle, plus an additional amount per level above 1. The config key names follow the pattern `<building>` (base) and `<building>Upgrade` (per level). Note that some building types are commented out in the config (marked "disabled") and do not generate taxes by default.

| Building | Base Tax | Per Level |
|---|---|---|
| Alchemist | 12.0 | 5.0 |
| Bakery | 10.0 | 4.0 |
| Beekeeper | 9.0 | 3.0 |
| Blacksmith | 18.0 | 8.0 |
| Builder | 8.0 | 4.0 |
| Chicken Herder | 9.0 | 3.0 |
| Composter | 6.0 | 2.0 |
| Concrete Mixer | 10.0 | 4.0 |
| Cook | 12.0 | 5.0 |
| Cowboy | 9.0 | 4.0 |
| Crusher | 13.0 | 6.0 |
| Deliveryman | 12.0 | 5.0 |
| Dyer | 9.0 | 3.0 |
| Enchanter | 15.0 | 5.0 |
| Farmer | 11.0 | 5.0 |
| Fisherman | 10.0 | 4.0 |
| Fletcher | 9.0 | 3.0 |
| Florist | 8.0 | 2.0 |
| Gatehouse | 10.0 | 3.0 |
| Glassblower | 10.0 | 3.0 |
| Graveyard | 7.0 | 2.0 |
| Hospital | 20.0 | 8.0 |
| Kitchen | 12.0 | 5.0 |
| Large Quarry | 25.0 | 9.0 |
| Library | 13.0 | 6.0 |
| Lumberjack | 11.0 | 5.0 |
| Mechanic | 11.0 | 4.0 |
| Medium Quarry | 20.0 | 7.0 |
| Miner | 11.0 | 5.0 |
| Mystical Site | 20.0 | 10.0 |
| Nether Worker | 15.0 | 6.0 |
| Plantation | 12.0 | 4.0 |
| Postbox | 8.0 | 2.0 |
| Rabbit Hutch | 8.0 | 2.0 |
| Residence | 5.0 | 2.0 |
| Sawmill | 10.0 | 3.0 |
| School | 15.0 | 5.0 |
| Shepherd | 9.0 | 3.0 |
| Sifter | 10.0 | 4.0 |
| Simple Quarry | 15.0 | 5.0 |
| Smeltery | 15.0 | 6.0 |
| Stash | 5.0 | 1.0 |
| Stonemason | 12.0 | 4.0 |
| Stone Smeltery | 12.0 | 5.0 |
| Swine Herder | 10.0 | 4.0 |
| Tavern | 14.0 | 6.0 |
| Town Hall | 20.0 | 8.0 |
| University | 20.0 | 10.0 |
| Warehouse | 10.0 | 4.0 |
| Warehouse Deliveryman | 12.0 | 5.0 |

**Disabled by default (generate no tax):** Archery, Barracks, Barracks Tower, Combat Academy, Guard Tower. These buildings have maintenance costs only.

---

## Logging

### LogLevel
- **Description**: Controls how much the mod prints to the server console. `0` = Errors and warnings only. `1` = Normal informational messages. `2` = Full debug output including detailed calculations.
- **Default Value**: 0

---

## Colony Occupation

### EnableOccupationSystem
- **Description**: When true, winning a war puts the defeated colony into an Occupation phase rather than transferring it immediately. The occupier collects a share of taxes while the original owner has a window to reclaim their colony.
- **Default Value**: true

### OccupationDurationDays
- **Description**: Number of real-time days the original owner has to attempt a Reclamation War before ownership transfers automatically to the occupier. Range: 1–90.
- **Default Value**: 7

### OccupationTaxPercentage
- **Description**: Fraction of the occupied colony's tax revenue diverted to the occupier each tax cycle. 0.0 = none, 1.0 = all taxes.
- **Default Value**: 0.5

---

## High Stakes War

### EnableOutpostVulnerability
- **Description**: When true, a player's first colony (Capital) can only be attacked while the owner is online. All additional colonies (Outposts) can be attacked even when the owner is offline, with guards defending automatically.
- **Default Value**: true

### EnableColonyWager
- **Description**: When true, the attacker's colony is wagered in every war declaration. If the defender wins, the attacker's colony enters Occupation instead of the defender's.
- **Default Value**: true

---

## War Economy

### WarTaxFreezeCycles
- **Description**: Number of tax cycles that tax generation is frozen or penalised after a war loss. Replaces the older hours-based freeze setting.
- **Default Value**: 3

### MaxCombinedWarPenaltyPercent
- **Description**: Maximum combined percentage penalty that can be applied to a colony's tax generation from stacked war debuffs (exhaustion, reparations, etc.). Prevents death spirals.
- **Default Value**: 0.5

### MinTaxGenerationPercent
- **Description**: Minimum fraction of normal tax generation a colony can ever fall to, regardless of penalties. 0.30 means taxes never drop below 30% of their base value.
- **Default Value**: 0.30

### PeaceProposalTimeoutSeconds
- **Description**: How long (in seconds) a peace proposal remains valid before it expires. Range: 30–3600.
- **Default Value**: 300

### TreasuryDrainUsePercent (duplicate reference)
See [TreasuryDrainUsePercent](#treasurydrainusepercent) in the Treasury section above.

### TreasuryDrainPercent (duplicate reference)
See [TreasuryDrainPercent](#treasurydrainpercent) in the Treasury section above.

### WarDefenderDrainReduction (duplicate reference)
See [WarDefenderDrainReduction](#wardefenderdrainreduction) in the Treasury section above.

---

## War Weariness

### EnableWarWeariness
- **Description**: When true, players who have been targeted for war too many times recently gain temporary immunity from further war declarations.
- **Default Value**: true

### WarWearinessThreshold
- **Description**: Number of war-related actions (hits against a colony) that must accumulate before war weariness immunity is granted.
- **Default Value**: 500

### WarWearinessImmunityHours
- **Description**: How many hours of war declaration immunity a colony receives once the weariness threshold is reached. Range: 1–168.
- **Default Value**: 24

---

## Espionage - Travel

### SpyTravelBlocksPerMinute
- **Description**: How fast a spy travels to the target colony, measured in blocks per minute. Higher values mean faster arrival.
- **Default Value**: 200

### SpyTravelMinMinutes
- **Description**: Minimum travel time for any spy mission, regardless of distance.
- **Default Value**: 1

### SpyTravelMaxMinutes
- **Description**: Maximum travel time cap. Very distant colonies are capped at this value.
- **Default Value**: 15

---

## Espionage - Intelligence Gathering

### SpyMaxActivePerPlayer
- **Description**: Maximum number of active spy missions a single player can have running at once.
- **Default Value**: 3

### IntelEarlyThresholdMinutes
- **Description**: Minutes after arrival before Tier 1 intel (basic colony stats) is unlocked.
- **Default Value**: 2

### IntelMidThresholdMinutes
- **Description**: Minutes after arrival before Tier 2 intel (guards, happiness, treasury) is unlocked.
- **Default Value**: 10

### IntelLateThresholdMinutes
- **Description**: Minutes after arrival before Tier 3 intel (full roster, owner balance) is unlocked.
- **Default Value**: 30

---

## Espionage - Flee Behaviour

### FleeSpeedMultiplier
- **Description**: Movement speed multiplier applied to a spy when they are fleeing after detection. 1.5 means 50% faster than normal. Range: 1.0–3.0.
- **Default Value**: 1.5

### FleeEscapeDistance
- **Description**: Number of blocks outside the colony border the spy must reach to confirm a successful escape. Range: 1–50.
- **Default Value**: 5

### FleeMaxSeconds
- **Description**: Maximum time (in seconds) a spy has to escape before being considered caught, regardless of distance. Range: 10–120.
- **Default Value**: 30



---

## Besiege System

### EnableBesiegeSystem
- **Description**: Master toggle for the besiege system. When disabled, `/wnt besiege` is rejected.
- **Default Value**: true

### BesiegeDurationMinutes
- **Description**: How long (minutes) the besieger has to kill all defenders. If the timer runs out, defenders win.
- **Default Value**: 20

### BesiegeCooldownHours
- **Description**: How long (hours) a player must wait after any besiege attempt before they can besiege again.
- **Default Value**: 24

### BesiegeMilitiaPercent
- **Description**: Fraction of eligible non-guard citizens (excluding deliverymen) converted into militia defenders. 0.6 = 60%.
- **Default Value**: 0.6

### BesiegeTributePercent
- **Description**: Percentage of the besieged colony's tax income siphoned to the victor each tax cycle.
- **Default Value**: 30

### BesiegeTributeDurationHours
- **Description**: How many hours the besiege vassalage lasts before it expires automatically. Set to 0 for permanent until reclaimed.
- **Default Value**: 72

### BesiegeMinColonySize
- **Description**: Minimum number of citizens a target colony must have before it can be besieged.
- **Default Value**: 5

### BesiegeAlliesEnabled
- **Description**: When true, any player who attacks a defender is registered as an ally and becomes a defender target.
- **Default Value**: true

### BesiegeExtraMercenariesPerBuilding
- **Description**: Mercenaries spawned per colony building. E.g. 0.33 means 1 mercenary per 3 buildings.
- **Default Value**: 0.33

### BesiegeMaxMercenaries
- **Description**: Maximum total mercenaries that can be spawned in a single besiege raid.
- **Default Value**: 10

### BesiegePlayerStayRadius
- **Description**: Maximum distance (blocks) from the colony centre the besieger may stray. Exceeding this cancels the raid.
- **Default Value**: 100

---

## Siege SMP — Colony Tiers, Spoils & Victory Objectives

These settings live under the `[War Settings]` section of `minecolonytax.toml` and control the Siege SMP rules added after the v2 update.

### EnablePrimaryColonyTransfer
- **Description**: If true, a player's first (Primary) colony can have its ownership permanently transferred when it loses a war. If false (default), a Primary colony can be tax-occupied and besieged but never claimed — the owner can always reclaim it. Secondary colonies are always claimable regardless of this setting.
- **Default Value**: false

### PrimaryColonyTaxOccupationDays
- **Description**: How many in-game days a defeated Primary colony stays tax-occupied (its taxes diverted to the victor) before the occupation expires and the original owner regains their taxes. The owner can also end it early by winning a counter-besiege.
- **Default Value**: 7

### BesiegeSpoilPercentOfLoserTreasury
- **Description**: Percentage of the loser colony's Treasury paid to the winner when a besiege resolves. The transfer is cap-aware — only the winner's available headroom is deposited, so no coins are lost. When multiple players besieged the colony and BesiegeShareSpoils is on, this total is split among all participating besiegers' colonies.
- **Default Value**: 25

_The following keys are grouped in the config under access controls and multiplayer/online rules._

#### Siege access controls

### BesiegeAllowChestAccess
- **Description**: Whether besiegers may open chests and other containers in the colony during an active siege. When false (default), sieges are combat-only and looting is blocked. Set true to let attackers raid containers mid-siege.
- **Default Value**: false

### VassalLockOutFormerOwner
- **Description**: After a siege ends and the colony is vassalized (besiege occupation), whether its original owner is locked out of colony interactions (chests, buildings) until they reclaim it. When false (default), the owner keeps access to their own colony while vassalized — only tax tribute is siphoned. When true, the owner is locked out until a successful reclaim raid.
- **Default Value**: false

#### Multiplayer & online rules

### BesiegeMinAttackers
- **Description**: The "not solo" rule. Minimum number of distinct besiegers that must be participating in a siege of a colony for it to actually be captured. With the default of 1, solo sieges work as before. Set to 2 or more to force players to band together — if fewer than this take part, the defenders can be wiped but the colony is not captured.
- **Default Value**: 1

### BesiegeShareSpoils
- **Description**: When several players besiege the same colony at once (each runs `/wnt besiege` on it), split the treasury spoils (BesiegeSpoilPercentOfLoserTreasury) among all participating besiegers' primary colonies on victory, instead of awarding it only to the first to resolve. The lead besieger still receives the ongoing tax-tribute stream.
- **Default Value**: true

### BesiegeRequireOnline
- **Description**: Require besieging players to stay online during a siege. When true, a besieger who logs off for longer than BesiegeOfflineGraceMinutes has their participation cancelled (a siege cannot be run from the lobby). With multiple attackers, one logging off does not end the siege for the others.
- **Default Value**: true

### BesiegeOfflineGraceMinutes
- **Description**: How long (minutes) a besieger may stay offline before their siege participation is cancelled, when BesiegeRequireOnline is true.
- **Default Value**: 5

### EnableExperimentalSiegeObjectives
- **Description**: Master switch for the experimental victory objectives (Plant the Banner and Town Hall Demolition). When false (default), wars are won only by the classic life/guard conditions. **Must be true to test or use the banner and demolition objectives below.**
- **Default Value**: false

### TownHallExplosiveHitsRequired
- **Description**: Number of valid explosive hits on the defender's Town Hall *building* required for the attacker to win via demolition. Only counts when EnableExperimentalSiegeObjectives is true.
- **Default Value**: 5

### TownHallHitCooldownMinutes
- **Description**: Cooldown (minutes) between counted explosive hits on the Town Hall, per attacker. Hits landed during the cooldown do not advance the demolition objective.
- **Default Value**: 5

### MaxSiegeRadius
- **Description**: Maximum distance (blocks) from the Town Hall an attacker may be for an explosive hit to count toward the demolition objective.
- **Default Value**: 500

### AttackerGlowSeconds
- **Description**: How long (seconds) an attacker is made to glow after landing a Town Hall hit. Their coordinates are also broadcast to the defenders. Set to 0 to disable the glow.
- **Default Value**: 30

### BannerCaptureMinutes
- **Description**: How long (minutes) the Siege Banner must remain planted inside the Town Hall borders for the attacker to win via the Plant the Banner objective. A war-scoped boss bar (visible only to war participants) counts down this timer.
- **Default Value**: 10

### BannerMaxReplants
- **Description**: How many times an attacker may re-plant the Siege Banner after defenders break it during the same war. Only applies when EnableExperimentalSiegeObjectives is true. Set to 0 to allow no re-plants (one chance only).
- **Default Value**: 1

### DeferRestorationToExplosiont
- **Description**: If true and the Explosion't mod is installed, explosion-damage restoration is handed off to Explosion't (whose heal is paused during conflict by a war-aware mixin and resumes after). If false (default), the built-in WarBlockLedger captures and restores war-damaged blocks itself. See [War Persistence](War_Persistence.md#explosion-damage-restoration-warblockledger).
- **Default Value**: false
