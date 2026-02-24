# War & PvP System

War 'N Taxes fundamentally redefines interactions on Minecolonies servers. Disagreements over land, resources, or pure conquest escalate into brutal, server-wide conflicts with massive economic consequences.

## 1. Declaring War (`/wnt wagewar <colony>`)
You cannot casually declare war on a brand new colony. There are strict prerequisites designed to balance risk and ensure fair fights.
- **Building Requirements:** By default, attackers must meet a hefty quota of military infrastructure. For example, ensuring they have high-tier Barracks and multiple Guard Towers.
- **Guard Requirements:** If `EnableWarBuildingRequirements` is off, the system falls back to requiring a default of at least **5 Guards** (`MinGuardsToWageWar`) to prove military competency.
- **Attacker Grace Period:** To prevent endless griefing, after initiating a war, a colony is locked out of declaring any further wars for **24 Hours** (`AttackerGracePeriodMinutes` = 1440).
- **Acceptance:** Depending on server rules (`WarAcceptanceRequired`), defenders may be forced into the war automatically, or they must explicitly accept the declaration via `/wnt war accept`.

## 2. The War Chest Economy
Wars are funded by your local treasury. Your colony relies on a **War Chest** (`EnableWarChest`).
- **Declaration Cost:** To even hit the wagewar command, your War Chest must contain a minimum of **25%** (`WarChestMinPercentOfTarget`) of the target colony's *entire* tax balance.
- **Active Drain:** During combat, both the attacker's and defender's War Chests constantly bleed money out of the economy. By default, this drain can be **5,000 currency per minute** (`WarChestDrainPerMinute`)!
- **Auto-Surrender:** If the attacker's War Chest runs completely dry, supply lines collapse, and the war results in an immediate, humiliating auto-surrender (`WarChestAutoSurrenderEnabled`).

## 3. Extortion & Prevention
If diplomacy fails, but bloodshed is avoidable, the attacker can leverage the Extortion mechanic.
- Before the war starts, the attacking player demands an **Extortion Percentage** of the defender's total wealth (e.g., 15%).
- The defender has a configured number of minutes (`ExtortionResponseTimeMinutes`) to respond. If they pay using `/wnt payextortion`, they earn guaranteed immunity for days (`ExtortionImmunityHours`), entirely dodging the war.

## 4. Active Combat, Victory & Defeat
Once war begins, participants are thrust into active PvP and sieges.
- **PvP Kill Economy:** Every time a player kills an opponent, a massive **25%** (`PvPKillRewardPercentage`) of the victim's personal bank balance is transferred directly into the killer's pockets. High stakes PvP!
- **War Duration & Lives:** Wars are not endless. They have a strict lifetime limit (`WarDurationMinutes`). Furthermore, players are usually restricted to a set number of respawns (`PlayerLivesInWar`). Running out of lives means you are permanently out of that specific conflict.

### To The Victor Go The Spoils
When a colony successfully forces a surrender or wipes the opponent:
1. **Colony Transfer:** If `EnableColonyTransfer` is true, the defending town’s entire ownership binding is permanently transferred to the victorious attacker.
2. **War Vassalization:** If Transfer is disabled, the victor "subjugates" the loser. The defeated colony is forced to pay a brutal **10%** (`WarVassalizationTributePercentage`) of their daily tax income as a tribute straight to the victor for a set duration.
3. **Victor's Cut:** Winning players split up to **25%** (`WarVictoryPercentage`) of the losing server's total economic balance. 

## 5. Defeat Penalties & War Reparations
Stalemates and Defeats absolutely crush a colony's progression.
- **Defeat Loss:** Losing a war instantly drains **25%** (`WarDefeatPercentage`) of your balance, while a Stalemate drains **10%** from ALL participants.
- **War Exhaustion (Tax Freezes):** Post-war, your colony suffers intense exhaustion. Tax generation is fully halted or severely penalized for **24 Hours** (`WarTaxFreezeHours` / `PostWarRecoveryHours`).
- **War Reparations:** Repeated failures trigger heavy sanctions. If a colony loses **3 wars** (`ReparationsTriggerLossesCount`) consecutively within a 7-day window, a crippling "War Reparations" debuff is placed. This permanently slashes their tax generation by **50%** (`ReparationsTaxPenaltyPercent`) until a massive fine is paid off or the timer expires.

## 6. Peace Treaties
At any point, leaders can swallow their pride to end the bloodshed:
*   `/wnt peace whitepeace` : End the war status-quo. No money shifts.
*   `/wnt peace reparations <amount>` : Demand a specific lump-sum payment of currency to call off the siege immediately.

---

### Command Reference
* `/wnt wagewar <colony>`
* `/wnt payextortion <colonyId> <amount>`
* `/wnt joinwar` & `/wnt leavewar`
* `/wnt peace`
