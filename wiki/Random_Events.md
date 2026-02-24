# Random Events

Running a colony shouldn't just be an AFK math equation. To prevent the economy from stagnating and to keep colony rulers on their toes, War 'N Taxes natively implements a **Random Event System**.

If `EnableRandomEvents` is true, the game loops through dynamic global cooldown cycles, calculating the chance of events firing upon active colonies based on their conditions (wealth, debt, happiness). 

## 1. Positive Events
Rulers are occasionally blessed by unforeseen fortune, completely circumventing normal mechanics and delivering immense bursts of progress.

*   **Merchant Caravans** (`ENABLE_MERCHANT_CARAVAN`)
    *   *Effect:* A trading company suddenly arrives at your borders. Delivers a massive, instantaneous tax injection (often **15%** of your standard revenue) and naturally boosts citizen happiness (e.g., **+30%** speed to happiness regeneration).
*   **Bountiful Harvests**
    *   *Effect:* Favorable weather triggers extreme crop growth. Prevents immediate food shortages and offers temporary tax production bonuses across your agricultural buildings.
*   **Cultural Festivals**
    *   *Effect:* Your citizens throw a spontaneous celebration. Triggers insane happiness spikes, immediately boosting your native tax generation multipliers into the upper quartiles for several cycles, allowing you to maximize Treasury collection. 

## 2. Negative Events
Prosperity invites disaster. A colony pushed too hard or mired in heavy debt invites chaos.

*   **Food Shortages & Plagues**
    *   *Effect:* Drastically destroys citizen happiness. Can reduce tax efficiencies to single digits until the crisis is managed, the players hunt down medicine, or the timer expires. 
*   **Guard Desertions**
    *   *Effect:* If the colony is broke or deeply indebted, the military will grow restless. A percentage of your town's guards will randomly desert their posts, abandoning their equipment and leaving you extremely vulnerable to immediate Raids and Wars from opportunist players. 
*   **Labor Strikes**
    *   *Effect:* Usually triggered when a ruler forces the oppressive `High Policy` or `War Economy` on citizens for far too long. Citizens will completely drop their tools and freeze all tax generation cycles entirely until their happiness demands or the strike timer are met.
*   **Corrupt Officials**
    *   *Effect:* Your treasury isn't safe from the inside. A corrupt governor or officer randomly embezzles a massive flat percentage of your entire claimed Treasury and vanishes into the void. This specifically hurts mega-colonies hoarding wealth at the `MaxTaxRevenue` cap.

## 3. Mitigation & Control
As a server owner, you have granular control over exactly what events can fire.
Every single negative and positive event can be individually disabled inside the primary `tax-config.toml` file (e.g., `ENABLE_MERCHANT_CARAVAN = true/false`).

As a player, this isn't purely RNG. Actively investing in infrastructure, maintaining low debt, maintaining high happiness, and managing defensive policies heavily skews the probability tables in your favor. A well-run colony ensures Plagues and Corrupt Officials seek out your weaker neighbors instead of your thriving metropolis!
