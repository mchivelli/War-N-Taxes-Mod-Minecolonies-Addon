# Abandonment Permissions and Boss Bar Fixes

## Issues Fixed

### 1. **Colony Abandonment Permissions** ✅
**Problem**: Players could still break blocks and grief abandoned colonies because permissions weren't properly cleared.

**Root Cause**: The abandonment process only set former owners/officers as "hostile" but didn't remove all players or set proper neutral permissions.

**Fix**:
- **Complete Player Removal**: All players (owners, officers, members) are now removed from abandoned colonies
- **Restrictive Neutral Permissions**: Neutral players can no longer break blocks, place blocks, or grief abandoned colonies
- **Clean Slate Approach**: Abandoned colonies start with a clean permission slate

### 2. **Missing Boss Bar in Claiming Raids** ✅
**Problem**: The claiming raid boss bar wasn't appearing for the raiding player.

**Root Cause**: Player lookup issues and potential boss bar creation failures.

**Fix**:
- **Improved Player Lookup**: Enhanced `getPlayerById()` method with fallback mechanisms
- **Boss Bar Maintenance**: Automatic re-addition of claiming player to boss bar during updates
- **Debug Logging**: Added comprehensive logging to track boss bar creation and maintenance
- **Force Refresh**: Added admin command to manually refresh boss bars

## Technical Implementation

### Abandonment Permission Fixes

#### Before (Problematic):
```java
// Only set former owners as hostile - other players remained with permissions
permissions.setPlayerRank(playerId, permissions.getRankHostile(), colony.getWorld());
permissions.setOwnerAbandoned(); // Only removes owner
```

#### After (Fixed):
```java
// Remove ALL players from the colony
Map<UUID, ColonyPlayer> allPlayers = new HashMap<>(permissions.getPlayers());
for (UUID playerId : allPlayers.keySet()) {
    boolean removed = permissions.removePlayer(playerId);
}

// Set restrictive permissions for neutral players
Rank neutralRank = permissions.getRankNeutral();
permissions.setPermission(neutralRank, Action.BREAK_BLOCKS, false);
permissions.setPermission(neutralRank, Action.PLACE_BLOCKS, false);
permissions.setPermission(neutralRank, Action.RIGHTCLICK_BLOCK, false);
permissions.setPermission(neutralRank, Action.OPEN_CONTAINER, false);
permissions.setPermission(neutralRank, Action.TOSS_ITEM, false);
permissions.setPermission(neutralRank, Action.PICKUP_ITEM, false);
```

### Boss Bar Improvements

#### Enhanced Player Lookup:
```java
private static ServerPlayer getPlayerById(UUID playerId) {
    // Primary lookup from active raids
    for (ClaimingRaidData raidData : activeClaimingRaids.values()) {
        // ... lookup logic
    }
    
    // Fallback: try any available server
    for (IColony colony : colonyManager.getAllColonies()) {
        if (colony.getWorld() instanceof ServerLevel serverLevel) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) return player;
        }
    }
}
```

#### Boss Bar Maintenance:
```java
private static void updateRaidBossBar(ClaimingRaidData raidData) {
    // Ensure claiming player stays on boss bar
    ServerPlayer claimingPlayer = getPlayerById(raidData.claimingPlayerId);
    if (claimingPlayer != null && !raidData.bossEvent.getPlayers().contains(claimingPlayer)) {
        raidData.bossEvent.addPlayer(claimingPlayer);
    }
}
```

## New Features

### 1. **Force Boss Bar Refresh**
- **Command**: `/wnt claimraidstatus <colony>` now includes boss bar refresh
- **Method**: `ColonyClaimingRaidManager.forceRefreshBossBar(colonyId)`
- **Usage**: Admins can manually fix missing boss bars

### 2. **Enhanced Debug Logging**
- Boss bar creation success/failure logging
- Player lookup debugging
- Permission clearing verification
- Comprehensive abandonment process logging

### 3. **Improved Abandonment Process**
- Both regular and force abandonment use the same secure process
- Complete player removal prevents any residual permissions
- Restrictive neutral permissions prevent griefing

## Security Improvements

### Abandoned Colony Protection
- **No Block Breaking**: Neutral players cannot break blocks
- **No Block Placing**: Neutral players cannot place blocks  
- **No Container Access**: Neutral players cannot open chests/containers
- **No Item Interaction**: Neutral players cannot drop/pickup items
- **No Block Interaction**: Neutral players cannot use doors/buttons/etc.

### Permission Verification
- All players are removed during abandonment
- Former owners/officers lose all access
- Only successful claimers get permissions (Officer rank)
- Clean slate approach prevents permission inheritance issues

## Testing Instructions

### 1. Test Abandonment Permissions
```bash
# As colony owner
1. Let colony become abandoned (or use /wnt forceabandon)
2. Try to break blocks in the abandoned colony
3. Verify you cannot break/place blocks
4. Check that other players also cannot grief the colony
```

### 2. Test Boss Bar Display
```bash
# As player with claiming requirements
1. Start a claiming raid: /wnt claimcolony <colony>
2. Verify boss bar appears immediately
3. Check that boss bar shows defender count
4. Kill defenders and watch count update
5. Verify boss bar turns green when all defenders are dead
```

### 3. Test Boss Bar Recovery
```bash
# As admin, if boss bar is missing
1. Use: /wnt claimraidstatus <colony>
2. Check if boss bar reappears
3. Verify it shows correct information
```

## Configuration

No new configuration options required - all improvements work with existing settings.

## Compatibility

- **Fully Backward Compatible**: Works with existing colonies and raids
- **No Data Migration**: No save file changes required
- **Safe Deployment**: Can be deployed without server restart concerns

## Troubleshooting

### Boss Bar Not Appearing
1. Check if claiming raid is actually active: `/wnt claimraidstatus <colony>`
2. Use the command to force refresh the boss bar
3. Check server logs for boss bar creation errors

### Players Can Still Grief Abandoned Colonies
1. Verify colony is actually abandoned: `/wnt listabandoned`
2. Check if players have special permissions from other mods
3. Verify the abandonment process completed successfully in logs

### Permission Issues After Claiming
1. Successful claimers should have Officer rank
2. All former permissions should be cleared
3. Check logs for permission clearing verification

## Future Enhancements

1. **Configurable Neutral Permissions**: Allow admins to configure what neutral players can do in abandoned colonies
2. **Boss Bar Persistence**: Save boss bar state to survive server restarts
3. **Permission Backup**: Option to restore original permissions if claiming fails
4. **Visual Indicators**: Add particle effects or other visual cues for abandoned colonies