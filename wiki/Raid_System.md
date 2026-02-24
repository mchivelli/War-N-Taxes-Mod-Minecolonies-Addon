# Raids & Combat

While full-scale Wars center around territory control and long-term vassalization, **Raids** are rapid incursions strictly focused on plundering the defending colony's treasury.

## 1. Initiating a Raid (`/wnt raid <colony>`)
Just like Wars, Raiding relies on server configuration to strike a balance between Risk and Reward. 
- **Building Dependencies:** Attackers must meet a defined quota of military infrastructure (like level 2 Guard Towers) to trigger the `/wnt raid` command (`RaidBuildingRequirements`).
- **Defending Limitations:** `EnableRaidGuardProtection` prevents veteran cities from easily wiping out infant towns. Target colonies usually require a minimum count of specific buildings (e.g. **1 Guard Tower** `MinGuardTowersToBeRaided`) or active guards (e.g. **2 Guards** `MinGuardsToBeRaided`) before they are eligible to be raided.

## 2. Plundering the Treasury
During an active raid, the primary objective is not to capture a flag or kill the owner. Your goal is to bleed their Treasury dry by slaying defenders.
- **Tax Stealing:** Every single time an attacking player kills a defending Guard or Militia member, **10%** (`TaxStealPercentagePerGuard`) of the target colony's total amassed Treasury is instantly siphoned directly into the attacker's inventory.
- **Raid Caps:** The system prevents complete wipeouts. A raid will forcefully cap out after stealing a maximum threshold, typically **50%** (`MaxRaidTaxPercentage`) of the colony’s total tax. Once you hit that cap, further guard kills yield no money.

## 3. The Citizen Militia
Unlike standard Minecolonies where citizens flee into buildings during night-time raids, War 'N Taxes empowers them!
- When a raid initiates, a flat **30%** (`MilitiaConversionPercentage`) of the targeted colony's standard civilian population immediately converts into heavily-armed temporary guards.
- To prevent defenseless level 1 children from being mass-sacrificed, there is a strict `MilitiaMinCitizenLevel` requirement. 
- The Militia behaves exactly as standard guards. Because they count as guards, **killing a Militia member also steals tax!** This forces defenders to strategically manage their population. 
- Once the raid reaches its `MaxRaidDurationMinutes`, any surviving Militia immediately revert back to their peaceful civilian duties.

## 4. Defending Against Raids
Defending is highly lucrative and severely punishes careless attackers.
- **Attacker Penalties:** If the defending Owner, Officer, Guard, or Militia member successfully kills an attacker during the raid window, the attacker suffers a massive financial penalty, usually dropping **25%** (`RaidPenaltyPercentage`) of their personal balance.
- **Defender Reward:** The server then takes that stripped wealth and actively rewards the successful defenders (`RaidDefenseRewardPercentage`), directly injecting the attacker's drained cash into the colony.
- **Guard Resistance:** Guards and Militia are often granted extreme buffs. Configuration values like `EnableGuardResistanceDuringRaids` provide base damage resistance effects (levels 1-255) to defenders operating inside their colony boundaries to tip the scales against fully-enchanted PvP attackers.

## 5. Post-Raid Penalties & Ransom
If an attacker successfully raids a colony and escapes with the loot, the colony's economy crumbles.
- **Tax Reduction:** The raided colony natively takes a massive hit to all tax regeneration cycles for the next several hours due to panic and stolen logistics (typically a **25%** reduction via `RaidPenaltyTaxReductionPercent`).
- **Owner Ransoms (`EnableRansomSystem`):** A brutal mechanic. If the defending Owner or Officer is slain by the attacker during the raid, the attacker can demand a **Ransom**. They freeze the raid timer and demand a massive percentage of the defender’s balance (default **15%** `RansomDefaultPercent`). The defender has mere seconds (`RansomTimeoutSeconds`) to respond by typing a command, or hostilities immediately resume.

## 6. Entity Raids (PvE System)
Not all Raids involve players! War 'N Taxes integrates completely with Minecraft's PvE mechanics.
- If enabled (`EnableEntityRaids`), natural hostile spawns like Pillager patrols wandering within **50 blocks** of your borders (`EntityRaidDetectionRadius`) can dynamically trigger massive NPC raid sequences directly on your colony.
- These mobs will attempt to slaughter your guards just like a player would, meaning they can actively ruin your tax generation if left unchecked.
- *Admins use `/wnt entityraid` commands to toggle whitelists, check cooldowns, or forcefully summon PvE raids for testing.*
