# Faction System - Complete Feature Reference

> **Last Updated**: 2025-12-14  
> **Status**: Implemented  
> **Branch**: `feature/faction-system`

## Overview


The Faction System allows MineColonies colony owners to form alliances with other colonies, share tax revenue through a pooled treasury, and establish diplomatic relations (ALLY, NEUTRAL, ENEMY). This system integrates with the existing War and Raid mechanics to **prevent hostile actions between allied factions**.

---

## Core Concepts

### What is a Faction?
A **Faction** is a group of colonies that share:
1.  **Diplomatic Relations**: Allies cannot attack each other.
2.  **Shared Tax Pool**: Members contribute a percentage of their tax generation to a common treasury.
3.  **Collective Identity**: A faction has a name, owner, and member list.

### Key Entities
| Entity | Description |
|--------|-------------|
| `FactionData` | Data model storing faction ID, name, owner colony, members, relations, and tax balance. |
| `FactionManager` | Static manager class handling all faction CRUD operations, relation logic, and persistence. |
| `FactionCommand` | Brigadier command handler for `/wnt faction` subcommands. |
| `FactionRelation` (Enum) | `ALLY`, `NEUTRAL`, `ENEMY` - defines inter-faction diplomacy. |

---

## Data Model: `FactionData.java`

Located at: `src/main/java/net/machiavelli/minecolonytax/faction/FactionData.java`

```java
public class FactionData {
    private UUID id;                          // Unique faction identifier
    private String name;                      // Faction display name
    private int ownerColonyId;                // Colony ID of the faction owner
    private Set<Integer> memberColonyIds;     // Set of member colony IDs
    private Map<UUID, FactionRelation> relations; // Relations to other factions (by faction UUID)
    private long taxBalance;                  // Shared tax pool balance
    private double taxRate;                   // Contribution rate (0.0 - 1.0)
    private Set<Integer> pendingInvites;      // Colony IDs with pending invites
}
```

### Key Methods
- `isMember(int colonyId)`: Check if a colony belongs to this faction.
- `addMember(int colonyId)` / `removeMember(int colonyId)`: Manage membership.
- `getRelation(UUID factionId)`: Get relation status with another faction.
- `setRelation(UUID factionId, FactionRelation relation)`: Set relation status.
- `addTax(long amount)` / `deductTax(long amount)`: Manage shared pool.

---

## Manager Logic: `FactionManager.java`

Located at: `src/main/java/net/machiavelli/minecolonytax/faction/FactionManager.java`

### Persistence
- **File**: `config/warntax/factions.json`
- **Format**: Gson-serialized `Map<UUID, FactionData>`
- **Load**: `init()` called on server start (`MineColonyTax.java`).
- **Save**: `saveData()` called after any mutation (create, join, leave, tax).

### Core Operations

#### Faction Creation
```java
public static FactionData createFaction(String name, int ownerColonyId)
```
1.  Check if faction system is enabled (`TaxConfig.isFactionSystemEnabled()`).
2.  Check if owner colony is already in a faction (`getFactionByColony()`).
3.  Generate UUID, create `FactionData`, add owner as first member.
4.  Initialize tax rate from config (`TaxConfig.getDefaultPoolContributionPercent()`).
5.  Persist and return.

#### Joining a Faction
```java
public static boolean joinFaction(int colonyId, UUID factionId)
```
1.  Check max members limit (`TaxConfig.getMaxFactionMembers()`).
2.  Check if colony is not already in a faction.
3.  Add to member set, clear pending invite.
4.  Persist.

#### Leaving a Faction
```java
public static void leaveFaction(int colonyId)
```
1.  If owner leaves with other members: transfer ownership to first remaining member.
2.  If owner leaves with no other members: disband faction.
3.  Otherwise: remove member from set.

### Relation Logic

#### Checking Alliance
```java
public static boolean areAllies(int colonyId1, int colonyId2)
```
1.  Get factions for both colonies.
2.  If either colony is not in a faction → `false`.
3.  If same faction → `true` (same faction = allies).
4.  Check `f1.getRelation(f2.getId()).isAlly()`.

**IMPORTANT**: This method is called by `WarCommands` and `RaidManager` to block hostile actions.

#### Checking Enmity
```java
public static boolean areEnemies(int colonyId1, int colonyId2)
```
- Same logic, but checks `isEnemy()`.

---

## Integration Points

### 1. War Declaration (`WarCommands.java`)

**File**: `src/main/java/net/machiavelli/minecolonytax/commands/WarCommands.java`
**Method**: `handleWageWarCommand()`

```java
// Faction Alliance Check
IColony attackerColony = IColonyManager.getInstance().getColonies(level).stream()
        .filter(c -> c.getPermissions().getOwner().equals(attacker.getUUID()))
        .findFirst()
        .orElseGet(() -> IColonyManager.getInstance().getColonies(level).stream()
                .filter(c -> c.getPermissions().getPlayers().containsKey(attacker.getUUID()))
                .findFirst()
                .orElse(null));

if (attackerColony != null) {
     if (FactionManager.areAllies(attackerColony.getID(), targetColony.getID())) {
         ctx.getSource().sendFailure(Component.literal("You cannot declare war on an allied faction!"));
         return 0;
     }
}
```

**Flow**:
1.  Player runs `/wnt wagewar <colonyName>`.
2.  Command finds target colony.
3.  **BEFORE** processing war request, check if attacker's colony and target colony are in allied factions.
4.  If allied → block with error message.

### 2. Raid Initiation (`RaidManager.java`)

**File**: `src/main/java/net/machiavelli/minecolonytax/raid/RaidManager.java`
**Method**: `handleRaid()`

```java
// Faction Alliance Check
if (FactionManager.areAllies(raiderColony.getID(), colony.getID())) {
    context.getSource().sendFailure(Component.literal("You cannot raid an allied faction!"));
    return 0;
}
```

**Flow**:
1.  Player runs `/wnt raid <colonyName>`.
2.  Command finds raider's colony and target colony.
3.  **AFTER** validating colony presence but **BEFORE** starting raid, check alliance.
4.  If allied → block with error message.

### 3. Tax System (`TaxManager.java`)

**File**: `src/main/java/net/machiavelli/minecolonytax/TaxManager.java`
**Method**: `generateTaxesForAllColonies()`

```java
if (TaxConfig.isFactionSystemEnabled() && TaxConfig.isSharedTaxPoolEnabled()) {
    double divertedAmount = FactionManager.processFactionTax(colonyId, totalGeneratedTax);
    if (divertedAmount > 0) {
         adjustTax(colony, -(int)divertedAmount);
         // Log contribution
    }
}
```

**Flow**:
1.  Tax is generated for a colony.
2.  If faction system and shared pool are enabled:
3.  Call `FactionManager.processFactionTax(colonyId, amount)`.
4.  This diverts `(amount * taxRate)` to the faction's `taxBalance`.
5.  Deduct from colony's individual tax.

---

## Commands Reference

All commands are registered under `/wnt faction`:

| Command | Permission | Description |
|---------|------------|-------------|
| `/wnt faction create <name>` | Player | Create a new faction (requires colony ownership) |
| `/wnt faction join <factionName>` | Player | Join a faction (must have pending invite) |
| `/wnt faction invite <playerName>` | Faction Owner | Invite a colony owner to join |
| `/wnt faction kick <playerName>` | Faction Owner | Remove a member |
| `/wnt faction leave` | Player | Leave current faction (owner cannot leave without transfer) |
| `/wnt faction info [name]` | Player | View faction details (members, relations, pool balance) |
| `/wnt faction list` | Player | List all factions on the server |
| `/wnt faction ally <factionName>` | Faction Owner | Set relation to ALLY |
| `/wnt faction enemy <factionName>` | Faction Owner | Set relation to ENEMY |
| `/wnt faction neutral <factionName>` | Faction Owner | Set relation to NEUTRAL |
| `/wnt faction tax <percent>` | Faction Owner | Set contribution percentage (0-100) |
| `/wnt faction withdraw <amount>` | Faction Owner | Withdraw from pool to colony's War Chest |

---

## Configuration (`TaxConfig.java`)

| Config Key | Type | Default | Description |
|------------|------|---------|-------------|
| `ENABLE_FACTION_SYSTEM` | Boolean | `true` | Master toggle for faction features |
| `MAX_FACTION_MEMBERS` | Int | `10` | Max colonies per faction |
| `FACTION_CREATION_COST` | Int | `1000` | Cost in tax currency to create a faction |
| `FACTION_ALLIANCE_LIMIT` | Int | `3` | Max factions that can be set to ALLY |
| `ENABLE_SHARED_TAX_POOL` | Boolean | `true` | Enable pooled treasury |
| `DEFAULT_POOL_CONTRIBUTION_PERCENT` | Int | `10` | Default tax diversion rate |
| `MAX_POOL_BALANCE` | Long | `100000` | Cap on faction treasury |
| `POOL_HISTORY_RETENTION_DAYS` | Int | `30` | Days to keep transaction logs |

---

## Multiplayer Implications

### Gameplay Impact
1.  **Alliance Protection**: Players that set each other as ALLIES cannot raid or attack each other. This encourages diplomacy and coalition-building.
2.  **Shared Economy**: Factions pool resources, enabling smaller colonies to benefit from larger ones' tax generation.
3.  **Strategic Depth**: Players must balance alliance benefits (protection, shared pool) against independence (full tax retention, freedom to attack).

### Server Administration
1.  **Faction Data Persistence**: Data is stored in `config/warntax/factions.json`. Backup this file during server backups.
2.  **Orphan Cleanup**: If a colony is deleted (via MineColonies), the faction manager does NOT automatically clean up references. Consider a scheduled cleanup task.
3.  **Abuse Prevention**: The `areAllies` check only uses `f1.getRelation(f2.getId()).isAlly()`. This is **one-directional** by design; F1 must set F2 as ally, AND F2's relation to F1 must also be ALLY for mutual protection. (Currently, only the attacker's stance is checked, so if A considers B an ally, A cannot attack B, even if B has NOT set A as ally.)

### Potential Exploits / Edge Cases
- **Faction Hopping**: A player could create a colony, raid a target, then join a faction with the target to avoid retaliation. **Mitigation**: Consider adding cooldowns after leaving/joining factions.
- **Pool Draining**: A faction owner could withdraw all pool funds and leave. **Mitigation**: Consider adding withdrawal limits or requiring member votes.

---

## File Locations Summary

| File | Purpose |
|------|---------|
| `faction/FactionData.java` | Data model |
| `faction/FactionManager.java` | Core logic & persistence |
| `faction/FactionRelation.java` | Enum for relation types |
| `commands/FactionCommand.java` | Command registration |
| `commands/WarCommands.java` | Alliance check for war |
| `raid/RaidManager.java` | Alliance check for raids |
| `TaxManager.java` | Pool contribution logic |
| `TaxConfig.java` | Configuration entries |
| `MineColonyTax.java` | Lifecycle hooks (init, save) |
| `config/warntax/factions.json` | Persisted data |

---

## For AI Agents: Key Editing Points

When modifying faction behavior:
1.  **Add new relations**: Extend `FactionRelation` enum and update `FactionData.getRelation()`.
2.  **New commands**: Add subcommands in `FactionCommand.java`, follow existing pattern.
3.  **New integrations**: Check `FactionManager.areAllies()` or `areEnemies()` before allowing hostile/friendly actions.
4.  **Config changes**: Add entries to `TaxConfig.java`, use `TaxConfig.get...()` accessors.
5.  **Persistence format**: If changing `FactionData` fields, update Gson serialization (may need custom TypeAdapters for complex types).
