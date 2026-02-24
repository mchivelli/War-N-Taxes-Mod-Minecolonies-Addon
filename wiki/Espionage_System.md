# Espionage System

War 'N Taxes allows players to employ covert mechanics via the **Spy System**, giving them alternative ways to hinder opponents besides brute force. The espionage system relies exclusively on the **Tax Economy** to function. All actions require a massive flat tax fee deducted directly from your treasury.

To engage in espionage, the server must have `EnableSpySystem` turned on.

## 1. Intelligence Operations (Spy Actions)
Players can perform several distinct spy actions against enemy colonies. Every action comes with a heavy tax cost and a strict `Detection Chance`.

*   **Scout:**
    *   **Description**: Uncovers vital statistics of the targeted colony, such as their current real-time tax balance, active Guard counts, and active policies. Excellent for deciding if a colony is worth raiding.
    *   **Risk**: Low. Has the lowest base detection modifier (`ScoutDetectionChance`).
*   **Sabotage:**
    *   **Description**: Deals a crushing blow to the target’s economy by drastically reducing their tax generation for the very next cycle. By default, a successful sabotage slashes their next tax interval by **25%** (`SabotageTaxReductionPercent`).
    *   **Risk**: Medium (`SabotageDetectionChance`). 
*   **Bribe Guards:**
    *   **Description**: Pays off specific guards in the target colony prior to an attack. Disables a configured number of guards (`BribeGuardsDisabledCount`) so that they will completely ignore the next raid or war engagement.
    *   **Risk**: High. Heavy detection penalty modifier (`BribeGuardsDetectionChance`).
*   **Steal Secrets:**
    *   **Description**: Covertly copies the enemy's active building synergy bonuses, applying them temporarily to your own colony for a configured number of hours (`StealSecretsDurationHours`).
    *   **Risk**: Medium-High.

## 2. Detection & Penalties
Spies are not invisible. Actions are checked against a probability matrix the moment you hit enter.
-   **Base Chance:** `SpyDetectionBaseChance` sets the global baseline chance of a spy being caught (usually **20%** native failure rate).
-   **Action Modifiers:** Each specific action stacks its own detection modifier onto the base chance. Bribing Guards is incredibly risky and will severely spike the probability of getting caught (e.g. jumping the failure rate to 60%+).
-   **Defense Research:** The defending colony isn't helpless against invisible attacks. They can actively invest in **Research** using `ResearchCost`. Having active Research massively bolsters their own spy defense bonus (often adding a **50%** buffer via `ResearchSpyDefenseBonus`), making any incoming agent almost guaranteed to be captured at the border.

### Consequences of Detection
If your agent fails their roll:
1. The requested action completely fails and provides zero intelligence.
2. The massive tax cost of the operation is still permanently consumed from your treasury.
3. The defending target is immediately notified in chat exactly *who* sent the spy, almost always leading to an immediate retaliation Raid or War Declaration.

## 3. Operations Management
You cannot spam intelligence actions infinitely against a single target to continuously bleed them dry.
-   **Operation Cooldowns:** Every colony is protected by `SpyCooldownMinutes`. Once an action is executed against them, there is a global grace period before *any* further espionage can occur against them from your colony.
-   **Max Spies Limit:** Players are capped at a specific `MaxActiveSpiesPerPlayer` limit. This restricts how many concurrent ops they can run simultaneously across the vast server map.

---

### Command Integration
Espionage actions are typically integrated directly into the broader colony management GUI menus via the Town Hall block, allowing officers to deploy covert assets and manage their concurrent intelligence operations securely.
