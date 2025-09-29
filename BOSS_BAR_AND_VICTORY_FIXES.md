# Boss Bar and Victory Condition Fixes

## Issues Fixed

### 1. Missing Boss Bar Timer with Defender Count
**Problem**: Boss bar wasn't clearly showing how many defenders remained to be killed.

**Fix**:
- Enhanced boss bar text to show "KILL ALL DEFENDERS: X remaining" instead of generic text
- Added detailed breakdown showing militia vs mercenaries
- Boss bar turns GREEN when all defenders are eliminated
- Clear messaging emphasizes the victory condition

### 2. Victory Condition Not Triggering
**Problem**: Killing all defenders didn't end the claiming raid properly.

**Fix**:
- Added fallback cleanup mechanism in `checkRaidConditions()` that removes dead defenders from tracking
- Enhanced death tracking with double-check mechanism
- Added `forceCheckVictoryCondition()` method for manual victory checking
- Improved logging to track defender deaths and victory conditions
- Added cleanup of dead citizens and mercenaries during regular updates

## New Features

### 1. Enhanced Boss Bar Display
- **Before**: "Claiming ColonyName - Defenders: 5 - Time: 04:30"
- **After**: "KILL ALL DEFENDERS: 5 remaining (3 militia + 2 mercenaries) | Time: 04:30"
- **Victory**: "ALL DEFENDERS ELIMINATED! | Time: 04:30" (in GREEN)

### 2. Debug Command for Admins
- `/wnt claimraidstatus <colony>` - Admin-only command to check claiming raid status
- Shows detailed defender breakdown
- Forces victory condition check
- Useful for debugging stuck raids

### 3. Improved Death Tracking
- Fallback mechanism that cleans up dead defenders during regular updates
- Double-check system that calls both event-based and polling-based victory checks
- Enhanced logging for transparency

## Technical Implementation

### Boss Bar Improvements
```java
// Clear messaging about victory condition
if (totalAliveDefenders == 0) {
    defenderText.append("ALL DEFENDERS ELIMINATED!");
} else {
    defenderText.append("KILL ALL DEFENDERS: ").append(totalAliveDefenders).append(" remaining");
    // ... detailed breakdown
}

// Color coding: RED for active, GREEN for victory
Component newText = Component.literal(defenderText.toString() + " | Time: " + time)
    .withStyle(totalAliveDefenders == 0 ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD);
```

### Victory Detection Improvements
```java
// Fallback cleanup mechanism
Set<Integer> deadCitizens = new HashSet<>();
Set<Entity> deadMercenaries = new HashSet<>();

// Check each defender and mark dead ones for cleanup
for (Integer citizenId : raidData.hostileCitizens) {
    ICitizenData citizenData = colony.getCitizenManager().getCivilian(citizenId);
    if (citizenData == null || !citizenData.getEntity().isPresent() || !citizenData.getEntity().get().isAlive()) {
        deadCitizens.add(citizenId);
    }
}

// Clean up dead defenders
raidData.hostileCitizens.removeAll(deadCitizens);
raidData.spawnedMercenaries.removeAll(deadMercenaries);
```

### Double-Check System
```java
// In death event handler
checkClaimingRaidVictory(raidData, colony, killerName);
// Also force check via main manager (double-check)
ColonyClaimingRaidManager.forceCheckVictoryCondition(colony.getID());
```

## Testing Instructions

### 1. Test Boss Bar Display
1. Start a claiming raid
2. Verify boss bar shows "KILL ALL DEFENDERS: X remaining"
3. Kill defenders and watch the count decrease
4. Verify boss bar turns GREEN when all are dead

### 2. Test Victory Condition
1. Start a claiming raid with few defenders
2. Kill all defenders
3. Verify raid ends immediately with victory message
4. Check that permissions are properly set

### 3. Test Debug Command (Admin)
1. Use `/wnt claimraidstatus <colony>` during an active raid
2. Verify it shows accurate defender counts
3. Use it to force-check victory conditions if raid seems stuck

### 4. Test Fallback Mechanism
1. Start a claiming raid
2. Use creative mode or commands to kill defenders without direct combat
3. Wait for the next update cycle (1 second)
4. Verify dead defenders are cleaned up and victory is detected

## Key Improvements

1. **Clear Victory Messaging**: Boss bar explicitly states "KILL ALL DEFENDERS"
2. **Real-time Updates**: Defender count updates immediately as defenders die
3. **Fallback Safety**: Even if death events are missed, regular updates will clean up and check victory
4. **Visual Feedback**: Boss bar color changes to GREEN on victory
5. **Admin Tools**: Debug command for troubleshooting stuck raids
6. **Enhanced Logging**: Better tracking of defender deaths and victory conditions

## Configuration
No new configuration options needed - all improvements work with existing settings.

## Compatibility
- Fully backward compatible
- Works with existing claiming raids
- No database or save file changes required