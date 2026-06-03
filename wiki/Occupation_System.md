# Colony Occupation System

**Added in:** v4.4.0

The Occupation System is the default outcome when a war is won. Rather than instantly transferring the defeated colony to the victor, the colony enters an intermediate **Occupied** state. The winner gains economic benefits while the original owner retains full control and has time to fight back.

---

## How It Works

> **Screenshot placeholder:** *The colony management GUI with the colony list on the left — one colony showing "Occupied" in orange text with the Events tab open on the right showing "Occupied by [player]" at the top*

### When Occupation Begins
Occupation starts automatically when a war ends in victory for one side. Both players are notified in chat. Note that when the [Colony Wager](War_System#colony-wager-system) is active, the **attacker's** colony can also be occupied if the defender wins.

- The **occupier** begins collecting a percentage of the occupied colony's tax revenue each cycle
- The **original owner** keeps full interaction rights: they can still build, manage citizens, and use all colony features normally
- The **occupier cannot interact** with the occupied colony's buildings, items, or citizens — only taxes flow to them

### The Reclamation Window
The original owner has a configurable number of real-time days (`OccupationDurationDays`, default: 7) to reclaim their colony.

- During this period, the original owner can declare a **Reclamation War** against the occupier
- Building requirements and treasury costs are waived for reclamation wars — the system recognises that a player fighting to reclaim their home may not have the resources for a standard war declaration
- If the reclamation war is won, the occupation ends immediately and the colony is fully restored to the original owner
- If the reclamation war is lost, ownership transfers to the occupier immediately (the reclamation window is forfeit)

### Automatic Transfer
If no reclamation war is attempted before the occupation window expires, **full ownership transfers automatically** to the occupier. No further action is required by either party.

### Persistence
Occupation state is saved to `config/warntax/occupations.json` and survives server restarts.

### Notifications

When occupation begins, both parties are notified immediately in chat:

- The **original owner** receives a direct chat alert and an on-screen title message informing them their colony is now occupied and by whom.
- The **occupier** receives a confirmation message noting which colony they now occupy and how long the reclamation window lasts.
- All **officers** of the occupied colony are also notified, regardless of their location on the server.

If any of these players are **offline** when the occupation starts, they receive the same chat message and title alert the next time they log in.

### Colony GUI Status

While a colony is occupied, the colony list in the management GUI shows **Occupied** in orange text next to the colony name. Tax claiming is disabled for the colony during this time — neither the original owner nor the occupier can manually claim taxes; the occupier's share is diverted automatically each cycle.

The **Events** tab for the occupied colony shows a non-dismissible entry at the top: **Occupied by [player]** in orange. This entry remains until the occupation ends, either through a successful reclamation war or automatic transfer when the window expires.

---

## Colony Wager Interaction

When `EnableColonyWager` is enabled (default: true), the attacker's colony is also wagered in every war. If the **defender wins**, the attacker's colony enters Occupation rather than the defender's. This means declaring war always carries real risk — the aggressor can end up occupied if they lose.

See [War & PvP System](War_System) for full details on the Colony Wager mechanic.

---

## Configuration

| Config Key | Default | Description |
|---|---|---|
| `EnableOccupationSystem` | true | Enables the occupation phase after a war victory. If disabled, colony transfer happens immediately when `EnableColonyTransfer` is true. |
| `OccupationDurationDays` | 7 | Number of real-time days the original owner has to attempt a reclamation war before ownership transfers automatically. Range: 1–90. |
| `OccupationTaxPercentage` | 0.5 | Fraction of the occupied colony's tax revenue diverted to the occupier each cycle (0.0 = none, 1.0 = all). |

---

## Summary

| Situation | Outcome |
|---|---|
| War won (occupation enabled) | Losing colony enters Occupation; winner collects partial taxes |
| Reclamation war won | Occupation ends; original owner fully restored |
| Reclamation war lost | Ownership transfers immediately to occupier |
| Occupation window expires with no reclamation | Ownership transfers automatically |
| Server restart during occupation | Occupation state is restored from disk; no data lost |
| War won (occupation disabled) | Colony transfers directly if `EnableColonyTransfer` is true; otherwise vassalization applies |
