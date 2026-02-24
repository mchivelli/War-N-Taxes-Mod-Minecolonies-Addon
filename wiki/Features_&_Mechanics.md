# Features & Mechanics

War 'N Taxes fundamentally changes how players interact with their Minecolonies and with each other. Below is a detailed breakdown of all the core mechanics added by this mod.

---

## 1. Taxation System & Economy
At the heart of the mod is a dynamic taxation system that rewards active, well-developed colonies.
- **Tax Generation:** Colonies generate taxes passively in intervals (default 60 minutes) based on total building levels and citizen happiness. It is important to keep your colony thriving, as low happiness will incur a massive tax penalty, while high happiness introduces a tax multiplier.
- **Debt Limits:** A colony can go into debt. When a colony is in debt, raiding it might not be as profitable, or certain negative events could trigger. If the debt reaches a specified limit, the colony will stop generating revenue altogether.
- **Storage & Claiming:** Taxes accumulate over time up to a maximum Treasury limit. Colony Owners or Officers must manually use `/wnt claimtax` to extract this revenue into physical items or virtual balances depending on configuration.
- **Tax Policies:** Modify how taxes are extracted from your citizens. Implementing aggressive policies might boost short-term revenue but drastically lower citizen happiness.

---

## 2. The War & PvP System
Colonies can declare war on one another, changing interactions from peaceful to hostile.
- **Declaring War:** A colony must satisfy minimum defense requirements (either a specific number of Guards or specific levels of Military Buildings like Guard Towers) before declaring war. This ensures newer colonies cannot be trivially wiped out.
- **Extortion:** Before a war begins, the attacking colony can demand an *Extortion Percentage* of the defending colony's wealth. If the defender pays, the war declaration is cancelled, and the defender gains temporary immunity.
- **Victory & Defeat:** If war ensues, players participate in active PvP around their colonies. Killing players or guards drains the opponent's "War Chest" or balance.
- **Colony Transfer vs. Vassalization:** By default, winning a war can transfer the defeated colony to the victor. However, the server can configure *Vassalization* instead: the defeated colony becomes a vassal and pays a daily/weekly percentage of all its tax income as tribute to the conqueror.
- **Peace Treaties:** Players can negotiate peace before totally wiping out an opponent. Options include a simple "White Peace" (resetting relations to neutral) or exacting "Reparations" (a lump sum payment).

---

## 3. Raids & Citizen Militia
Want quick loot without the political baggage of a full-scale War? Initiating a Raid is your answer.
- **Requirement to Raid:** Similar to War, raids require specific guard or building thresholds. Protective rules are also placed so veteran towns cannot hopelessly raid infant towns.
- **Tax Stealing:** During a raid, every time you kill a Guard or Militia member, a percentage of the defending colony's accumulated Tax Treasury is stolen directly into the raider's pockets.
- **Citizen Militia:** Unlike vanilla Minecolonies where citizens cower in their homes during attacks, War 'N Taxes arms them! Based on citizen levels, a percentage of regular citizens will take up arms and convert into temporary Militia to defend their home alongside the standard Guards. 
- **Defensive Tactics:** Raiding isn't free. Upon death during a raid, an attacker loses a heavy portion of their personal balance as a penalty, rewarding the successful defenders.

---

## 4. Espionage
Knowledge is power. The Espionage system allows for covert interactions.
- Send spies to other colonies (with dedicated cooldowns and limits). 
- Actions include: 
  - **Scouting:** Discover the target colony's balance, guard count, and statistics.
  - **Sabotaging:** Momentarily decrease the tax generation or happiness of the target.
  - **Bribing Guards:** Temporarily disable a certain number of guards in the target colony, making a subsequent raid much easier.
  - **Stealing Secrets:** Temporarily lower their defense bonuses or discover weaknesses.
- Be careful! If a spy is caught (based on detection chances affected by the target's Research or Guard level), the spy's owner will be penalized.

---

## 5. Random Events
Ruling is unpredictable. The mod implements a Random Events system that triggers on global cooldown cycles. Events can drastically change the tide:
- **Positive Events:** *Merchant Caravans* (bonus trades), *Bountiful Harvests*, *Cultural Festivals* (insane happiness spikes), and *Neighboring Alliances*.
- **Negative Events:** *Food Shortages*, *Plague Outbreaks*, *Guard Desertions*, *Labor Strikes*, and *Corrupt Officials* stealing your treasury.

---

## 6. Abandonment & Claiming System
To prevent server clutter from inactive players, colonies can be set to "Auto-Abandon" after a configured number of days without Owner/Officer activity.
- Abandoned colonies become open for the taking using `/wnt claimcolony`.
- Claiming is not free! Initiating a claim triggers a massive *Claiming Raid* where all remaining citizens become hostile militia to defend their home. If the claimer survives the raid, the colony is theirs.
- Alternatively, admins can permanently protect specific colonies from ever decaying or being claimed.
