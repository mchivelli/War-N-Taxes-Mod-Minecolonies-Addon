# Refactoring Plan for WarCommands.java

This document outlines the plan to refactor `src/main/java/net/machiavelli/minecolonytax/commands/WarCommands.java` to improve its structure, maintainability, and separation of concerns.

## Current Issues

`WarCommands.java` currently handles multiple responsibilities:
*   Command registration.
*   Raid logic and state management.
*   War declaration, joining, and state management.
*   Peace proposal logic and state management.
*   Various utility functions.

This makes the class overly large and complex.

## Proposed Refactoring Strategy

The core idea is to extract distinct responsibilities into new, focused classes (Managers/Services) and data objects. `WarCommands.java` will then primarily handle command registration and delegate actions to these new classes.

**New Classes and Their Responsibilities:**

*   **`net.machiavelli.minecolonytax.raid.RaidManager.java`**: Manages all aspects of raid functionality, including active raid state, grace periods, and command logic related to raids.
*   **`net.machiavelli.minecolonytax.raid.ActiveRaidData.java`**: Holds the runtime state for an active raid (moved from the inner class in `WarCommands.java`).
*   **`net.machiavelli.minecolonytax.peace.PeaceProposalManager.java`**: Manages all aspects of peace proposals, including their creation, acceptance/declination, and finalization.
*   **`net.machiavelli.minecolonytax.peace.PeaceProposal.java`**: Data class representing a peace proposal (moved from the inner class in `WarCommands.java`).
*   **`net.machiavelli.minecolonytax.WarSystem.java`**: Will take on more core war logic, including managing pending war requests and the war lifecycle previously handled directly in `WarCommands.java`.

**Mermaid Diagram of Proposed Architecture:**

```mermaid
graph TD
    subgraph Commands
        WarCommands_java[WarCommands.java]
    end

    subgraph Services
        WarSystem_java[WarSystem.java]
        RaidManager_java[RaidManager.java (New)]
        PeaceProposalManager_java[PeaceProposalManager.java (New)]
    end

    subgraph DataObjects
        WarData_java[data/WarData.java]
        RaidData_java[data/RaidData.java (Record)]
        ActiveRaidData_java[raid/ActiveRaidData.java (New)]
        PeaceProposal_data_java[peace/PeaceProposal.java (New)]
    end

    WarCommands_java -- delegates war commands --> WarSystem_java
    WarCommands_java -- delegates raid commands --> RaidManager_java
    WarCommands_java -- delegates peace commands --> PeaceProposalManager_java

    WarSystem_java -- manages --> WarData_java
    RaidManager_java -- manages --> ActiveRaidData_java
    RaidManager_java -- uses/logs to --> RaidData_java
    PeaceProposalManager_java -- interacts with --> WarData_java
    PeaceProposalManager_java -- uses --> PeaceProposal_data_java

    WarData_java -- references --> PeaceProposal_data_java
```

## Detailed Step-by-Step Plan

### Phase 1: Create New Data Classes

1.  **Create `net.machiavelli.minecolonytax.raid.ActiveRaidData.java`**:
    *   Copy the existing inner class `WarCommands.RaidData` (lines 1658-1710 from `WarCommands.java`) into this new file as a public class.
    *   Update imports and ensure constructors/methods are public.

2.  **Create `net.machiavelli.minecolonytax.peace.PeaceProposal.java`**:
    *   Copy the existing inner class `WarCommands.PeaceProposal` (lines 1712-1734 from `WarCommands.java`) into this new file as a public class.
    *   Update imports and ensure the `Type` enum and methods are public.

3.  **Update `net.machiavelli.minecolonytax.data.WarData.java`**:
    *   Change the type of the `activeProposal` field (line 46) to the new `net.machiavelli.minecolonytax.peace.PeaceProposal`.
    *   Update `setActiveProposal` (line 94) and `getActiveProposal` (line 123) methods.
    *   Adjust imports.

### Phase 2: Create New Manager/Service Classes

4.  **Create `net.machiavelli.minecolonytax.raid.RaidManager.java`**:
    *   **Fields**:
        *   `private static final Map<UUID, ActiveRaidData> activeRaids = new HashMap<>();`
        *   `private static final Map<UUID, Long> RAID_GRACE_PERIODS = new HashMap<>();`
        *   `private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger(RaidManager.class);`
    *   **Methods (Moved from `WarCommands.java`)**:
        *   `public int handleRaid(CommandContext<CommandSourceStack> context)`
        *   `public static ActiveRaidData getActiveRaidForPlayer(UUID playerId)`
        *   `public static void endActiveRaid(ActiveRaidData raidData, String reason)`
        *   `public static Map<UUID, ActiveRaidData> getActiveRaids()`
        *   `public void handleRaiderKilled(ActiveRaidData raidData, ServerPlayer killer)`
        *   `private void startRaidCountdown(ActiveRaidData raidData)`
        *   `private void updateRaidBossBar(ActiveRaidData raidData)`
        *   `private boolean isRaiderInColony(ServerPlayer raider, IColony colony)`
        *   `private void endRaid(ActiveRaidData raidData, String reason)`
        *   `private int getMaxRaidDurationSeconds()`
        *   `private int getTaxInterval()`
        *   `private double[] getTaxPercentages()`
        *   `private long getRaidGraceDurationMs()`
        *   Relevant helper methods.
    *   **New Methods**: Method for `WarCommands.stopRaid()` delegation.
    *   Update internal references and imports. Dependencies: `TaxConfig`, `TaxManager`, `PlayerWarDataManager`, `HistoryManager`, `RaidLoginNotifier`.

5.  **Create `net.machiavelli.minecolonytax.peace.PeaceProposalManager.java`**:
    *   **Fields**:
        *   `private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger(PeaceProposalManager.class);`
    *   **Methods (Moved from `WarCommands.java`)**:
        *   `public int suePeaceWhite(CommandContext<CommandSourceStack> ctx)`
        *   `public int suePeaceReparations(CommandContext<CommandSourceStack> ctx, int amount)`
        *   `private int handleSuePeaceProposal(CommandContext<CommandSourceStack> ctx, PeaceProposal.Type type, int amount)`
        *   `public int acceptPeace(CommandContext<CommandSourceStack> ctx)`
        *   `public int declinePeace(CommandContext<CommandSourceStack> ctx)`
        *   `private void finalizePeaceProposal(WarData war, boolean accepted)`
        *   Relevant helper methods.
    *   Will interact with `WarData` via `WarSystem`. Dependencies: `WarSystem`, `WarData`, `WarEconomyHandler`, `TaxConfig`.

### Phase 3: Refactor `WarSystem.java`

6.  **Modify `net.machiavelli.minecolonytax.WarSystem.java`**:
    *   **Fields**:
        *   Add `private static final Map<Integer, WarRequest> pendingWarRequests = new ConcurrentHashMap<>();`
        *   Add `public record WarRequest(UUID attacker, int colonyId) { }`
    *   **Methods (Moved/New Logic)**:
        *   Logic from `WarCommands.handleWarResponse()` (managing `pendingWarRequests`).
        *   Logic from `WarCommands.handleWageWar()` (creating/managing `pendingWarRequests`, calling `startJoinPhase`).
        *   `public void startJoinPhase(IColony colony, ServerPlayer attacker, ServerPlayer owner)` (moved from `WarCommands.java`).
        *   Core logic of `WarCommands.joinWar()` and `WarCommands.leaveWar()`.
        *   `private void startWarCountdown(WarData warData)` (moved from `WarCommands.java`).

### Phase 4: Refactor `WarCommands.java`

7.  **Modify `net.machiavelli.minecolonytax.commands.WarCommands.java`**:
    *   **Remove**: Inner classes `RaidData`, `PeaceProposal`. Fields and methods moved to new managers or `WarSystem`.
    *   **Update `register()`**: Delegate `executes()` calls to `RaidManager`, `PeaceProposalManager`, or `WarSystem` as appropriate.
    *   **Update Command Handler Methods**:
        *   `handleRaid()`: Calls `RaidManager`.
        *   `handleWageWar()`: Extracts args, calls `WarSystem`.
        *   `handleWarResponse()`: Extracts args, calls `WarSystem`.
        *   Peace commands: Call `PeaceProposalManager`.
        *   `joinWar()`, `leaveWar()`: Extract player, call `WarSystem`.
        *   `stopRaid()`: Calls `RaidManager`.
        *   `warInfo()`, `debugWar()`, `stopAllWars()`, `stopWar()`: Get `WarData` from `WarSystem`.
    *   **Utility Methods**: Evaluate and move `createAcceptButton`, `createDeclineButton`, `getTargetColony`, `findColonyByName`, `countGuardsDebug`, etc., to appropriate new classes or a utility class.

### Phase 5: Review and Testing (Conceptual)

*   Thoroughly review all changes for correctness.
*   Ensure all imports are correct.
*   Verify functionality preservation.
*   Confirm correct class interactions.

This plan aims to create a more modular and maintainable codebase for the war and raid features.