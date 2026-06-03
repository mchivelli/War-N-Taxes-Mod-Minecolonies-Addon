# Features & Mechanics

War 'N Taxes fundamentally changes how players interact with their Minecolonies and with each other. Below is a detailed breakdown of all the core mechanics added by this mod.

---

## 1. Taxation System & Economy
At the heart of the mod is a dynamic taxation system that rewards active, well-developed colonies.
- **Tax Generation:** Colonies generate taxes passively in intervals (default 60 minutes) based on total building levels and citizen happiness. It is important to keep your colony thriving, as low happiness will incur a massive tax penalty, while high happiness introduces a tax multiplier.
- **Debt Limits:** A colony can go into debt. When a colony is in debt, raiding it might not be as profitable, or certain negative events could trigger. If the debt reaches a specified limit, the colony will stop generating revenue altogether.
- **Storage & Claiming:** Taxes accumulate over time up to a maximum Treasury limit. Colony Owners or Officers must manually use `/wnt claimtax` to extract this revenue into physical items or virtual balances depending on configuration.
- **Tax Policies:** Modify how taxes are extracted from your citizens. Each policy applies an additive happiness modifier to the colony average (0–10 scale). Aggressive policies boost revenue but directly reduce citizen happiness, which in turn reduces your tax multiplier.

---

## 2. The War & PvP System
Colonies can declare war on one another, changing interactions from peaceful to hostile. See [War & PvP](War_System) for the full guide.
- **Declaring War:** A colony must satisfy minimum defense requirements (building levels or guard counts) before declaring war, protecting newer colonies from being trivially attacked.
- **High Stakes Rules:** Your first colony (Capital) can only be attacked while you are online. Additional colonies (Outposts) can be attacked even while you are offline — guards defend automatically. When `EnableColonyWager` is on, your own colony is also at stake — lose the war and your colony becomes occupied by the defender.
- **Extortion:** Before a war starts, the attacker can demand a percentage of the defender's wealth as a one-time payment to cancel the declaration. Paying grants temporary immunity.
- **Occupation:** Winning a war does not instantly transfer the losing colony. Instead the defeated colony enters an **Occupation phase** where the winner collects a share of taxes while the original owner has a set number of real-time days to fight back via a reclamation war. Ownership only transfers if no reclamation war is won within that window. See [Colony Occupation](Occupation_System) for details.
- **Reclamation Wars:** Players whose colonies are occupied can declare war with waived building requirements and treasury costs — ensuring they are never permanently locked out of fighting back.
- **Vassalization:** If occupation and direct transfer are both disabled, the defeated colony instead pays a percentage of ongoing tax income as tribute to the victor.
- **Peace Treaties:** Players can propose white peace or demand reparations at any point during a war.
- **War Persistence:** Active wars survive server restarts and crashes. All war state is saved to `config/warntax/active_wars.json` automatically.

---

## 3. Raids & Citizen Militia
Want quick loot without the political baggage of a full-scale War? Initiating a Raid is your answer.
- **Requirement to Raid:** Similar to War, raids require specific guard or building thresholds. Protective rules are also placed so veteran towns cannot hopelessly raid infant towns.
- **Tax Stealing:** During a raid, every time you kill a Guard or Militia member, a percentage of the defending colony's accumulated Tax Treasury is stolen directly into the raider's pockets (default: 10% per kill, capped at 50% total).
- **Citizen Militia:** Unlike vanilla Minecolonies where citizens cower in their homes during attacks, War 'N Taxes arms them! A configurable percentage of citizens convert into temporary Militia who fight alongside Guards. Militia are equipped with wooden swords and use custom combat AI. They revert to civilians when the raid ends.
- **Guard Resistance:** During active raids, guards and militia receive a Damage Resistance buff that reduces incoming damage. The resistance level is configurable and applies for the full duration of the raid. Buildings like Guard Towers, Barracks, Combat Academies, and Archery ranges contribute to the resistance calculation.
- **Defensive Tactics:** Raiding isn't free. Upon death during a raid, an attacker loses a heavy portion of their personal balance as a penalty. Successful defenders receive a bonus (+25% of the attacker's penalty).

---

## 4. Espionage
Knowledge is power. The Espionage system lets you deploy real agents into rival colonies.
- Spies travel to the target colony in real time. Travel duration depends on distance.
- Once on site, spies gather **progressive intelligence** across three tiers over time: basic colony stats early on, guard counts and treasury mid-mission, and a full leadership roster and member list for long-running missions.
- Detected spies attempt to **flee** the colony rather than dying instantly. A successful escape preserves all gathered intel.
- **Four mission types:** Scout (gather intel), Sabotage (cut target's tax income), Bribe (weaken target guards for their next raid), and Steal (boost your own tax income).
- Completed missions remain visible for one hour, allowing you to review intel or confirm effects before the record expires.
- If JourneyMap is installed, active missions appear as map waypoints.

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
