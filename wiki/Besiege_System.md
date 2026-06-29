# Besiege System

The Besiege system lets a player raid another player's colony and, on victory, extract tribute from it — without triggering a full server-wide war. Several players can besiege the same colony at once and share the spoils.

---

## When to Use It

Use **besiege** when you want to contest a player's colony for tax income. Besieging carries lower political costs than a full war: a winning besieger collects ongoing tax tribute from the colony and a one-time cut of its treasury.

**Colony tiers matter.** Every player's first colony is their **Primary** (capital); the rest are **Secondary** (outposts).

- A **Secondary** colony can be besieged and, after a long-enough campaign, permanently claimed.
- A **Primary** colony can be besieged and have its taxes occupied, but its ownership cannot be permanently taken unless the server enables `EnablePrimaryColonyTransfer` (default off). Normally the owner reclaims a tax-occupied capital by winning a counter-besiege.

**Abandoned colonies cannot be besieged** — use the colony claiming raid for those.

---

## Starting a Besiege

```
/wnt besiege <colony>
```

Requirements before the raid begins:

- You must own at least one colony yourself.
- The target must have at least 5 citizens (`BesiegeMinColonySize`).
- You must not be on cooldown from a recent attempt.
- The target colony must not already be your vassal.
- If `BesiegeRequireOnline` is on (default), you must stay online for the siege to count — see [Online and "Not Solo" Rules](#online-and-not-solo-rules) below.

A colony may already have an active besiege from another player — you can join in as an additional besieger rather than being blocked. See [Multiplayer Sieges](#multiplayer-sieges).

---

## The Raid

Once started:

1. **Guards** in the target colony turn hostile and hunt the besieger.
2. **Militia** — up to 60% of eligible non-guard citizens — receive wooden swords and fight alongside guards.
3. **Mercenaries** — approximately 1 per 3 colony buildings — are spawned as extra defenders.
4. A **boss bar** appears showing remaining defender count and time.

The besieger has a limited window (default 20 minutes) to kill every defender. Leaving the colony area (beyond 100 blocks of centre) cancels the raid.

**Allies:** Any player who attacks a defender is tracked as an ally and is also hunted by defenders. Allies can be disabled in the config.

---

> **Screenshot placeholder:** *The besiege in progress: boss bar at the top of the screen showing remaining defender count and timer, hostile guards charging the besieger*

## Victory Conditions

| Outcome | Result |
|---------|--------|
| All defenders killed within the timer | Attacker wins: vassal tribute established |
| Timer runs out | Defenders win: besieger on cooldown |
| Besieger leaves the area | Raid cancelled: besieger on cooldown |

---

## After Victory

On a successful besiege:

- The target colony is **force-vassalized** — it pays a percentage of its tax income (`BesiegeTributePercent`, default 30%) to the besieger each tax cycle.
- The winner also takes a **one-time cut of the loser's treasury** (`BesiegeSpoilPercentOfLoserTreasury`, default 25%). The transfer is cap-aware, so no coins are ever lost. If several players besieged the colony together, this lump sum is split among all participating besiegers (see [Multiplayer Sieges](#multiplayer-sieges)).
- The vassalage lasts for a configurable duration (`BesiegeTributeDurationHours`, default 72 hours), after which it expires automatically.

### The Former Owner Keeps Their Colony

By default, **the former owner is NOT locked out**. They keep full access to their own colony — chests, building GUIs, and block placement all still work. Only the **tax tribute** is siphoned away each cycle. This keeps a vassalized colony playable while still costing its owner income.

A server can switch to the old hard-lockout behaviour by setting `VassalLockOutFormerOwner` to `true`. With that on, the former owner cannot interact with their colony at all until they reclaim it.

### Looting During the Siege

While a siege is **in progress**, attackers cannot open the colony's chests or use its villagers by default — a siege is combat-only. A server that wants attackers to loot containers mid-siege can set `BesiegeAllowChestAccess` to `true`.

### Notifications

When a besiege starts:

- The **colony owner and all officers** receive a direct chat alert regardless of where they are on the server: "WARNING: [player] is besieging your colony [name]!"
- Players who happen to be physically **near the colony** see a separate area broadcast: "Nearby colony [name] is under siege by [player]!" — this is a local alert for bystanders, not a personal colony warning.
- If the owner or an officer is **offline** when the besiege starts or completes, they receive a chat message and on-screen title alert the next time they log in.

### Colony GUI Status

While the besiege is active, the **Events** tab in the colony management GUI shows a live entry at the top: **Under Siege by [player]** (red bar). Tax claiming is disabled for the colony during this time. The entry disappears automatically when the besiege ends.

---

## Multiplayer Sieges

Several players can besiege the same colony at the same time — each simply runs `/wnt besiege <colony>` on it. They fight as individual besiegers (a besieger's own colony-mates cannot damage the defenders), but they all contribute to bringing the colony down.

- **Shared spoils:** When `BesiegeShareSpoils` is on (default), the one-time treasury cut is split across **all** participating besiegers' colonies on victory, instead of going only to whoever happens to resolve the siege first. With it off, the first to break through takes the whole cut.
- **Tribute stream:** The ongoing tax tribute goes to the lead besieger (the one whose siege established the vassalage).

## Online and "Not Solo" Rules

Two settings govern how presence and numbers affect a siege:

- **Stay online (`BesiegeRequireOnline`, default on):** A siege cannot be run from the lobby. If a besieger logs off for longer than `BesiegeOfflineGraceMinutes` (default 5 minutes), their participation is cancelled. When multiple players are attacking, one of them logging off does not collapse the siege for the rest.
- **Minimum attackers (`BesiegeMinAttackers`, default 1):** This is the "not solo" gate. If fewer than this many distinct besiegers take part, the defenders can be wiped out but the colony is **not** captured. Leave it at 1 to allow solo sieges; set it to 2 or more to force players to band together before a colony can fall.

## Reclaiming Your Colony

If your colony has been besieged, you can take it back:

```
/wnt besiege <your besieged colony>
```

The same raid plays out — you must kill all defenders within the time limit. If you win, the vassalage ends, the lockout is lifted, and your colony is fully restored.

---

## Cooldowns

After any besiege attempt (win or loss), you must wait before attempting another (default 24 hours). This applies to both the initial besiege and reclaim attempts.

---

## Relationship to Other Systems

| System | Scope | Outcome |
|--------|-------|---------|
| **Raid** | Single colony, short | Tax theft per guard killed |
| **Besiege** | Single colony (solo or multiplayer) | Tax vassalage + one-time treasury cut; owner keeps colony access by default |
| **War** | Multiple colonies, server-wide | Occupation, vassalage (with a money grab), or colony transfer |
| **Claiming** | Abandoned colonies only | Full ownership transfer |
