# GUARDS_ATTACK Fix Implementation Plan

The goal is to fix a `java.lang.NoSuchFieldError` causing a server crash. The error is caused by the `GUARDS_ATTACK` permission being removed from the Minecolonies API, but still being referenced in the addon code.

## User Review Required

> [!IMPORTANT]
> This change removes the explicit disabling of `GUARDS_ATTACK` permission for neutral/force-abandoned colony ranks. Since this permission has been removed from the underlying API (presumably replaced by a hostile state check or removed entirely), this should be safe, but please be aware that this specific permission toggle is being removed.

## Proposed Changes

### Analysis of Minecolonies API Changes
The `GUARDS_ATTACK` permission has been removed from the `Action` enum. In the new API version:
- The concept of "Guards Attack" is no longer controlled by a granular permission flag (`Action`).
- It has been replaced by a "Hostile" property on the `Rank` class itself (`Rank.isHostile()`).
- Hostility is now a fundamental property of the rank (like Hostile Rank ID 4), rather than a toggleable permission.

Since the code in `ColonyAbandonmentManager.java` attempts to disable `GUARDS_ATTACK` for the "Neutral" rank (which is already non-hostile by default), removing this line is safe and correct. The code already correctly handles the "Hostile" rank separately where appropriate.

### Core Logic

#### [MODIFY] [ColonyAbandonmentManager.java](file:///c:/Dev/War-N-Taxes-Mod---Minecolonies-Addon/src/main/java/net/machiavelli/minecolonytax/abandon/ColonyAbandonmentManager.java)
- Remove line 258: `permissions.setPermission(colonyNeutralRank, com.minecolonies.api.colony.permissions.Action.GUARDS_ATTACK, false);`

## Verification Plan

### Automated Tests
- Run `./gradlew compileJava` to verify that the code compiles without the reference to the missing field.
- Run `./gradlew build` to ensure the mod builds successfully.

### Manual Verification
- The user will need to deploy the fixed jar and verify the server starts without the crash.
