# Colony Claiming Raid Fixes

## Issues Fixed

### 1. "Militia" Message Issue
**Problem**: Boss bar showed confusing "+ Militia" message even when militia system wasn't working properly.

**Fix**: 
- Updated `updateRaidBossBar()` method in `ColonyClaimingRaidManager.java`
- Now shows detailed breakdown: "Defenders: X (Y militia + Z mercenaries)" or just the specific type
- Clearer messaging that distinguishes between citizen militia and mercenaries

### 2. Missing Boss Bar
**Problem**: Boss bar wasn't appearing for some players during claiming raids.

**Fix**:
- Enhanced `createRaidBossBar()` method with better error handling
- Added logging to track boss bar creation success/failure
- Automatically adds nearby players (within 200 blocks) to the boss bar
- Added try-catch blocks to handle potential failures gracefully

### 3. Raid Doesn't End After Killing All Defenders
**Problem**: Victory condition wasn't being detected properly, allowing players to repeat the claim command.

**Fix**:
- Created specialized death tracking for claiming raids in `RaidKillTracker.java`
- Added `handleClaimingRaidDeath()` method that specifically tracks hostile citizens
- Added `handleMercenaryDeath()` method to track mercenary deaths
- Created `checkClaimingRaidVictory()` method for centralized victory checking
- Improved victory condition detection to immediately end raid when all defenders are eliminated
- Added proper cleanup to prevent duplicate processing

### 4. No Grace Period (24-Hour Cooldown)
**Problem**: Players could claim multiple colonies without any cooldown period.

**Fix**:
- Added `CLAIMING_GRACE_PERIOD_HOURS` configuration option (default: 24 hours)
- Implemented grace period tracking system using `claimingGracePeriods` map
- Updated `checkClaimingRequirements()` to enforce grace period for all players
- Added grace period cleanup to prevent memory leaks
- Created `/wnt claimstatus` command to check eligibility and remaining cooldown
- Updated help text to show the 24-hour cooldown requirement

### 5. Permissions Not Cleared on Claiming
**Problem**: When a colony was successfully claimed, former owners/officers retained some permissions.

**Fix**:
- Added complete permission clearing in `completeClaimingRaid()` method
- All existing players (including former owners set as hostile) are removed from colony permissions
- Successful claimer is added as Officer rank with clean permissions
- Updated success messages to indicate permission clearing
- Both former members and new claimers receive Officer rank (not Owner)

### 6. Normal Raid Issues
**Problem**: Similar defender tracking issues affected regular raids.

**Fix**:
- Enhanced death tracking system works for both claiming raids and regular raids
- Improved defender detection methods in `RaidKillTracker.java`
- Better handling of guard vs militia distinction
- More robust victory condition checking

## New Features Added

### 1. Claiming Status Command
- `/wnt claimstatus` - Shows player's eligibility and remaining cooldown time
- Displays detailed requirements and grace period information

### 2. Enhanced Configuration
- `ClaimingGracePeriodHours` - Configurable cooldown period (1-168 hours)
- Updated help system to show grace period information

### 3. Improved Logging
- Better debug logging for claiming raid progress
- Victory condition logging for transparency
- Grace period tracking logs

## Configuration Changes

### New Config Option
```toml
# Grace period in hours before a player can claim another colony
# Range: 1 ~ 168 (1 hour to 1 week)
ClaimingGracePeriodHours = 24
```

### Updated Help Text
- Added grace period information to `/wnt help claimcolony`
- New `/wnt claimstatus` command for checking eligibility

## Technical Implementation Details

### Grace Period System
- Uses UUID-based tracking in `claimingGracePeriods` ConcurrentHashMap
- Automatic cleanup of expired grace periods
- Applies to all players, including former owners/officers
- Persists until server restart (could be enhanced with file persistence if needed)

### Victory Detection
- Tracks both hostile citizens and spawned mercenaries separately
- Immediate victory detection when last defender dies
- Prevents duplicate victory processing
- Proper cleanup of raid data on victory

### Permission Management
- Complete permission clearing on successful claim using `removePlayer()` for all existing players
- Clean slate approach ensures no residual permissions from abandoned state
- Claimer receives Officer rank regardless of former status
- Clear messaging about permission reset

### Boss Bar Improvements
- Shows real-time defender count breakdown
- Includes nearby players automatically
- Better error handling and logging
- Clear progress indication

## Testing Recommendations

1. **Test Grace Period**: Claim a colony, then try to claim another immediately - should be blocked
2. **Test Victory Condition**: Kill all defenders and verify raid ends immediately
3. **Test Boss Bar**: Verify boss bar appears and updates correctly for all nearby players
4. **Test Status Command**: Use `/wnt claimstatus` to check cooldown and requirements
5. **Test Mercenary Deaths**: Verify mercenary kills count toward victory condition
6. **Test Multiple Players**: Ensure boss bar appears for multiple players in the area
7. **Test Permission Clearing**: Verify that after successful claiming, all former permissions are cleared and only the claimer has Officer rank
8. **Test Former Owner Claiming**: Ensure former owners get Officer rank (not Owner) when reclaiming their colony

## Future Enhancements

1. **Persistent Grace Periods**: Save grace periods to file for server restart persistence
2. **Configurable Boss Bar Range**: Make the 200-block range configurable
3. **Grace Period Exemptions**: Allow admins to bypass grace periods for specific players
4. **Enhanced Victory Notifications**: More detailed victory messages and effects