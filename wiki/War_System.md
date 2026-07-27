# War & PvP System

War 'N Taxes turns peaceful colony-building into high-stakes geopolitics. Players can declare war on each other's colonies, fight in real-time PvP battles with limited lives, and claim the spoils of victory — or suffer devastating economic consequences if they lose.

This page covers the full war lifecycle from declaration to aftermath.

> **Tip:** To contest a player's *secondary* colony for tax income without triggering a full server-wide war, use the **[Besiege System](Besiege_System)** instead. Besieging is a lighter single-player raid that grants tax vassalage. Primary colonies still require full war.

---

## 1. Declaring War

**Command:** `/wnt wagewar <colony>` (optionally `/wnt wagewar <colony> <extortion%>`)

Before a colony can declare war, it must meet several prerequisites:

- **Building Requirements** (`EnableWarBuildingRequirements`, default: true): The attacker must have specific military buildings at certain levels — for example, a Town Hall at level 2, multiple Guard Towers, and a Barracks. The exact requirements are set by `WarBuildingRequirements` in the config.
- **Guard Requirements** (fallback): If building requirements are disabled, the attacker needs at least `MinGuardsToWageWar` guards (default: 5).
- **Target Minimum Guards:** The target colony must also have at least `MinGuardsToWageWar` guards. You cannot declare war on a completely undefended colony.
- **Treasury** (`EnableTreasury`): If enabled, the attacker's treasury must hold at least `TreasuryMinPercentOfTarget` (default: 25%) of the target colony's tax balance.
- **Grace Period:** After declaring a war, the attacker is locked out from declaring another for `AttackerGracePeriodMinutes` (default: 120 = 2 hours).
- **Concurrent War Lock:** A colony that is already participating in an active war — as either attacker or defender — cannot start or be pulled into a second war until the current one ends. This prevents a colony from being engaged in two wars simultaneously.
- **Primary Colony as Attacker:** When you declare war, the mod selects which of your colonies is put at stake as the attacker. It always prefers your primary (first) colony when it meets the requirements, so your secondary outpost colonies are not wagered unintentionally.

**What happens after declaring:**

- If `WarAcceptanceRequired` is **true** (default): The defender receives a clickable accept/decline prompt. They have 30 seconds to respond. If they decline or the timer expires, the war is cancelled.
- If `WarAcceptanceRequired` is **false**: The war begins immediately with no option to refuse.

Once accepted, a **Join Phase** begins (`JoinPhaseDurationMinutes`), during which allies and team members can join the fight before combat starts.

> **Screenshot placeholder:** *Flow diagram showing the war lifecycle: declaration → acceptance prompt → join phase → active combat → victory condition check → outcome (occupation / transfer / vassalization)*

---

## 2. High Stakes War

The High Stakes system introduces two mechanics that make war risky for **both** sides: Outpost Vulnerability and the Colony Wager.

### Primary vs Secondary Colonies (Outpost Vulnerability)

**Config:** `EnableOutpostVulnerability` (default: true)

Every player's **first colony** is automatically designated as their **Primary Colony** (capital). Any additional colonies they create are **Secondary Colonies** (outposts). The mod tracks this via `FirstColonyTracker`, persisted in `config/warntax/firstColonyData.json`.

- **Primary Colony (Capital):** Can **only be attacked when the owner is online**. This protects a player's main base from offline griefing. A Primary colony can be tax-occupied (its taxes diverted to the victor) but its **ownership cannot be permanently taken** unless the server enables `EnablePrimaryColonyTransfer` (default off). Normally the owner reclaims a tax-occupied capital by winning a counter-besiege; a defeated Primary stays tax-occupied for `PrimaryColonyTaxOccupationDays` (default 7) before the occupation expires.
- **Secondary Colonies (Outposts):** Can be attacked **even when the owner is offline**. The colony's guards defend automatically, but no players will be present on the defending side. Unlike a capital, a Secondary colony can be **permanently claimed** after a decisive enough defeat.

During an offline outpost attack:
1. The war starts immediately — no acceptance prompt, no join phase
2. The attacker fights only against the colony's guards (no defender player lives)
3. The attacker still has limited lives (`PlayerLivesInWar`) — losing all lives means the defender wins
4. If the war timer expires without a clear winner, the defender wins by default

> **Strategic implication:** Expanding your empire by building outpost colonies creates vulnerability. Invest in high-level guard towers and many guards at your outposts, or risk losing them while you sleep.

### Colony Wager System

**Config:** `EnableColonyWager` (default: true)

When the Colony Wager is enabled, the attacker's colony is put at stake in every war:

- **Attacker wins:** The target colony enters the [Occupation](Occupation_System) phase — the attacker collects a share of the defender's taxes.
- **Defender wins:** The **attacker's** colony enters Occupation instead! The defender now collects taxes from the attacker's colony.

Both sides receive prominent in-game notifications when a wager is won or lost.

> **Strategic implication:** You cannot safely bully weaker players. If you attack and lose, your own colony becomes occupied. Think carefully before declaring war.

### Reclamation Wars

If your colony has been occupied (either from losing a war or from the wager system), you have special privileges to fight back:

- You can declare war using your **occupied colony** as the attacker — even if that colony no longer meets the normal building/guard requirements
- **Building requirements and treasury costs are waived** for reclamation wars
- You can target either the occupier's colony directly, or attack your own occupied colony to reclaim it
- Winning a reclamation war **ends the occupation immediately** and restores full ownership

These exceptions ensure that a player who lost their only colony is never permanently locked out of fighting back.

---

## 3. Treasury Economy

**Config:** `EnableTreasury` (default: true)

Wars are funded through each colony's Treasury — a separate fund that accumulates automatically.

- **Auto-Deposit:** Each tax cycle, a percentage of tax revenue is deposited into the treasury (`TreasuryAutoDepositPercent`, default: 5%).
- **Declaration Cost:** Your treasury must hold at least `TreasuryMinPercentOfTarget` (default: 25%) of the target colony's tax balance to declare war. A secondary check requires `TreasuryMinPercentOfOwnTax` (default: 10%) of your own balance, preventing wealthy colonies from cheaply bullying poor ones.
- **Active Drain:** During combat, both attacker and defender treasuries drain every minute. The drain mode is set by `TreasuryDrainUsePercent` (default: true). When using percent mode, the drain is `TreasuryDrainPercent` (default: 0.4% of max capacity per minute). When using flat mode, the drain is `TreasuryDrainPerMinute` (default: 10 per minute). At default settings with a full 25,000 treasury, a war can be sustained for approximately 4 hours.
- **Defender Advantage:** The defending colony's drain rate is reduced by `WarDefenderDrainReduction` (default: 50%), giving defenders a home-field economic advantage.
- **Auto-Surrender:** If `TreasuryAutoSurrenderEnabled` is true, the war ends in surrender when either side's treasury is fully depleted.
- **Max Capacity:** The treasury has a maximum balance set by `TreasuryMaxCapacity` (default: 25,000).
- **Tax Report:** Each tax cycle, the current treasury balance is shown in the colony ledger alongside the auto-deposit line.

**Commands:** All commands accept an optional `[colonyId]` to target a specific colony. When omitted, the mod resolves your colony by your current position first, then falls back to ownership.

| Command | Description |
|---|---|
| `/wnt treasury status [colonyId]` | View treasury balance, drain rate, and sustain time |
| `/wnt treasury deposit <amount> [colonyId]` | Transfer funds from tax balance into the treasury |
| `/wnt treasury withdraw <amount> [colonyId]` | Transfer funds from treasury back to tax balance |

---

## 4. Extortion

**Config:** `EnableExtortionSystem`

Before a war starts, the attacker can demand a one-time payment from the defender to cancel the war:

- **Command:** `/wnt wagewar <colony> <percent>` — declares war with an extortion demand attached
- The demand is a percentage of the defender's balance (default: `DefaultExtortionPercentage`, typically 20%)
- The defender has `ExtortionResponseTimeMinutes` to respond
- **Paying:** The defender uses `/wnt payextortion <colonyId>` to pay. This cancels the war and grants the defender temporary immunity (`ExtortionImmunityHours`, default: 4 hours)
- **Refusing:** If the defender does not pay in time, the war proceeds normally

---

## 5. Active Combat

Once the join phase ends, the war enters active combat. Both sides fight with limited lives and a ticking clock.

### Lives and Duration

- **Player Lives:** Each participant has `PlayerLivesInWar` lives (default: 5). Dying costs one life. At zero lives, you enter **Spectator Mode** — you can watch the battle unfold but cannot fight or interact.
- **Spectator Mode:** Eliminated players receive a glowing outline visible to all and are switched to spectator. You remain in the war zone until it concludes but have no further influence on the outcome.
- **War Duration:** Wars last `WarDurationMinutes` (default: 120). If no side is eliminated before the timer runs out, the outcome depends on remaining lives and guards.
- **Keep Inventory:** If `KeepInventoryOnLastLife` is enabled, players retain their gear when they die on their final life (to prevent permanent gear loss in colony transfer scenarios).

### PvP Kill Economy

If `EnablePvPKillEconomy` is true, killing an enemy player during war transfers `PvPKillRewardPercentage` (default: 25%) of the victim's personal balance directly into the killer's account.

### Boss Bar

A persistent boss bar displays the current war state for all participants: attacker lives, defender lives, and remaining time.

### Victory Conditions

The war ends when:
1. **All attacker lives AND attacker guards reach zero** — Defender wins
2. **All defender lives AND defender guards reach zero** — Attacker wins
3. **Timer expires** — The side with more total remaining lives + guards wins. If tied, the defender wins (home advantage).

---

## 6. Victory Outcomes

What happens when the war ends depends on configuration. The outcomes are checked in priority order:

### Outcome 1: Colony Occupation (default)

**Config:** `EnableOccupationSystem` (default: true)

The losing colony enters an **Occupied** state rather than changing ownership immediately. See the [Occupation System](Occupation_System) page for full details. In brief:

- The occupier collects `OccupationTaxPercentage` (default: 50%) of the occupied colony's tax revenue each cycle
- The occupier **cannot** interact with any buildings, items, or citizens — only taxes flow to them
- The original owner keeps full control and has `OccupationDurationDays` (default: 7) real-time days to wage a reclamation war
- If no reclamation is attempted, ownership transfers automatically when the timer expires
- If the Colony Wager is active and the **defender** won, the **attacker's** colony is occupied instead

### Outcome 2: Direct Colony Transfer

**Config:** `EnableColonyTransfer` (default: true), `EnableOccupationSystem` = false

If the occupation system is disabled but colony transfer is enabled, the losing colony is transferred to the winner immediately:

- Full ownership change — the winner becomes the colony owner
- All buildings, citizens, and resources transfer intact
- The previous owner loses all access

### Outcome 3: Vassalization

**Config:** `EnableWarVassalization` (default: true), both occupation and transfer disabled

If neither occupation nor transfer is enabled, the loser becomes a vassal:

- The losing colony pays `WarVassalizationTributePercentage` (default: 15%) of their tax income as tribute for `WarVassalizationDurationHours` (default: 168 hours = 1 week)

**The "huge money" war grab.** Because this path keeps the colony in the loser's hands (no deed transfer), the victor instead takes a one-time pile of money on top of the ongoing tribute:

- **Treasury cut:** `WarVassalizationTreasuryGrabPercent` (default: 50%) of the losing colony's Treasury is moved straight into the victor's primary colony Treasury. The transfer is cap-aware, so no coins are lost.
- **Wallet cut:** `WarVassalizationPlayerBalanceGrabPercent` (default: 25%) of the losing player's personal wallet balance is taken as well (requires an economy mod such as SDMShop / SDMEconomy).

Either percentage can be set to `0` to disable that half of the grab. By default the former owner keeps access to their vassalized colony; a server can lock them out instead with `VassalLockOutFormerOwner` (see the [Besiege System](Besiege_System) page).

### Outcome 4: Economic Penalties Only

If all territory systems are disabled, the war resolves purely through economic penalties (see section 7).

### Victor's Cut

Regardless of the territory outcome, winning players split `WarVictoryPercentage` (default: 20%) of the losing colony's total tax balance.

---

## 7. Defeat Penalties & War Reparations

Losing a war has severe economic consequences:

- **Defeat Loss:** The losing colony immediately loses `WarDefeatPercentage` (default: 20%) of its tax balance.
- **Stalemate Loss:** If the war ends in a draw, **all** participants lose `WarStalematePercentage` (default: 10%).
- **War Exhaustion:** After any war, the colony's tax generation is reduced by `WarTaxReductionPercent` (default: 30%) for `WarTaxFreezeCycles` (default: 3 tax cycles) and then recovers fully over `PostWarRecoveryHours` (default: 48 hours).
- **War Immunity:** After losing a war, the defeated colony enters a temporary immunity window. During this period, the colony cannot declare war and cannot be declared upon. This prevents repeated attacks on weakened colonies and gives them time to recover.
- **War Reparations:** If a colony loses `ReparationsTriggerLossesCount` (default: 3) wars within a 7-day window, a War Reparations debuff is applied. This slashes tax generation by `ReparationsTaxPenaltyPercent` (default: 20%) for `ReparationsDurationHours` (default: 72 hours). After reparations trigger, the colony enters an additional immunity cooldown to prevent an infinite penalty loop.

---

## 8. War Persistence

**Wars survive server restarts and crashes.** All war state — player lives, timers, boss bars, and permissions — is automatically saved to `config/warntax/active_wars.json` on server shutdown and restored on the next startup.

- Remaining war duration is recalculated so the timer resumes where it left off
- Boss bars are recreated and shown to online participants
- Glow effects and guard resistance buffs are reapplied
- If a war's timer fully expired during downtime, it is not restored (the war ends naturally)
- You **cannot** escape a losing war by restarting the server
- When you log in, both the colony **owner and all officers** receive a chat message and on-screen title alert for any active war involving their colonies. This happens whether the war started while you were online or offline.

> See [War Persistence](War_Persistence) for the full technical implementation guide.

### Active War in the Colony GUI

While a war is ongoing, opening the colony management GUI and selecting the affected colony shows a live status entry at the top of the **Events** tab:

- Defender side: **Defending vs [Colony Name]** — red bar, note that tax claiming is disabled
- Attacker side: **Attacking [Colony Name]** — red bar, note that the treasury is actively draining

This entry cannot be dismissed. It disappears automatically when the war ends.

---

## 9. Peace Treaties

Either side can propose peace at any time during an active war. There are three proposal types:

| Command | Effect |
|---|---|
| `/wnt peace whitepeace` | Propose ending the war with no reparations — the status quo is restored |
| `/wnt peace reparations <amount>` | Propose peace in exchange for a lump-sum payment from the other side |
| `/wnt peace surrender` | Unconditionally surrender — the war ends with you as the loser, applying all defeat penalties |
| `/wnt peace accept` | Accept an incoming peace proposal |
| `/wnt peace decline` | Decline an incoming peace proposal |

### Alliance & Team Wars

Wars can involve teams. If players use FTB Teams or similar team systems, allies can participate in wars alongside the primary combatants.

- When war is declared, a **Join Phase** allows team members and allies to accept or decline participation
- Accepted allies contribute lives to their side and share in victory rewards or defeat penalties
- Declined allies are excluded from the war entirely
- Both attacker and defender sides can have allies join during the join phase

---

## 10. Experimental Siege Objectives

Gated behind `EnableExperimentalSiegeObjectives` (default **off**). When this is off, wars are won only by the classic lives-and-guards conditions in section 5. When on, attackers gain two extra ways to force a colony to fall:

### Plant the Banner

- Attackers are given a **Siege Banner**. Planting it inside the town-hall borders starts a capture timer, shown as a boss bar to war participants only.
- Hold the banner for `BannerCaptureMinutes` (default 10) to win the war.
- Defenders can **break the banner** to stop the capture. An attacker may re-plant it up to `BannerMaxReplants` times (default 1).

### Demolish the Town Hall

- Destroy the town-hall **building** with explosives — ideally siege weaponry.
- Each valid hit (landed within `MaxSiegeRadius` blocks, default 500, of the town hall) makes the attacker **glow** for `AttackerGlowSeconds` (default 30) and broadcasts their coordinates to the defenders, so attacking is never invisible.
- After `TownHallExplosiveHitsRequired` valid hits (default 5) the colony falls. A per-attacker cooldown of `TownHallHitCooldownMinutes` (default 5) sits between counted hits.

> Both objectives respect the same outcome rules as a normal war victory (occupation, transfer, or vassalization with the money grab).

---

## 11. Militia, Siege Damage, and Battlefield Cleanup

- **Militia investment:** A colony can invest in extra **Militia** who spawn as additional defenders, scaled to its guard count, during wars, besieges, and raids. Militia extend a fight but **never count as a victory objective** — only guards and player lives decide a war — and they despawn when the conflict ends. See the [Colony Investments](Investments_System) page.
- **Persistent siege damage restoration:** Explosion damage caused during a war is recorded and **fully restored when the war ends** — blocks and the contents of chests, signs, and other block entities are put back intact, so siege warfare does not permanently scar the map. This damage ledger survives server restarts. See [War Persistence](War_Persistence) for details, including the optional `DeferRestorationToExplosiont` hand-off to the Explosion't mod.
- **Hand-breaking blocked during war:** While a war is active, blocks can no longer be broken by hand near the conflict — only **explosive damage** destroys them. Chests, doors, and combat still work as normal.
- **Hundred Years' War troops:** If the Hundred Years' War (HYW) mod is installed, your commanded HYW armies can march to war alongside your colony without harming your own town — see the [Hundred Years' War Integration](HYW_Integration) page.

---

## Command Reference

| Command | Description |
|---|---|
| `/wnt wagewar <colony>` | Declare war on a colony |
| `/wnt wagewar <colony> <extortion%>` | Declare war with an extortion demand |
| `/wnt war accept <colonyId>` | Accept a war declaration |
| `/wnt war decline <colonyId>` | Decline a war declaration |
| `/wnt joinwar` | Join an active war on your side |
| `/wnt leavewar` | Leave an active war |
| `/wnt warinfo` | View details about your current war |
| `/wnt payextortion <colonyId>` | Pay the extortion demand to cancel the war |
| `/wnt peace whitepeace` | Propose a white peace |
| `/wnt peace reparations <amount>` | Propose peace with reparations |
| `/wnt peace accept` | Accept a peace proposal |
| `/wnt peace decline` | Decline a peace proposal |
| `/wnt treasury status [colonyId]` | View treasury balance and drain info |
| `/wnt treasury deposit <amount> [colonyId]` | Deposit tax into treasury |
| `/wnt treasury withdraw <amount> [colonyId]` | Withdraw from treasury |
| `/wnt warhistory [colony]` | View war history |
| `/wnt warstats` | View your personal war statistics |

---

## Further Reading

The defeat penalties in section 7 — tax freeze cycles, post-war recovery, reparations, and weariness immunity — are covered in full detail on the [War Exhaustion](War_Exhaustion) page, including how the penalties interact when multiple systems are active at once and the config keys that control each threshold.
