# Officer Visit Tracking System

## Overview

The Officer Visit Tracking system ensures that colonies are not abandoned when officers (not just owners) actively manage them. This fixes the issue where colonies would be marked as abandoned even though officers were regularly visiting and interacting with the colony.

## Problem Statement

Previously, the colony abandonment system only tracked when the **owner** visited the colony, using MineColonies' built-in `getLastContactInHours()` method. This meant that:

- Officers could visit and manage the colony daily
- The colony would still be marked as "inactive" and eventually abandoned
- Only the owner's visits would reset the abandonment timer

## Solution

The new `OfficerColonyVisitTracker` system:

1. **Tracks Officer Visits**: Monitors when any officer or owner enters their colony
2. **Resets Abandonment Timer**: Automatically resets the colony's last contact time when officers visit
3. **Performance Optimized**: Only checks every 5 seconds per player to avoid lag
4. **Fallback Tracking**: Maintains its own tracking system in case reflection fails

## How It Works

### 1. Player Position Monitoring

Every 5 seconds (100 ticks), the system checks each online player's position:

```java
@SubscribeEvent
public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
    // Check if player is in a colony
    // If yes, check if they're an officer/owner
    // If yes, reset the abandonment timer
}
```

### 2. Officer Detection

The system checks if a player has colony manager rank:

```java
private static boolean isOwnerOrOfficer(IColony colony, UUID playerId) {
    // Check if player is the owner
    UUID owner = colony.getPermissions().getOwner();
    if (owner != null && owner.equals(playerId)) {
        return true;
    }
    
    // Check if player is an officer (has colony manager rank)
    ColonyPlayer colonyPlayer = colony.getPermissions().getPlayers().get(playerId);
    if (colonyPlayer != null && colonyPlayer.getRank().isColonyManager()) {
        return true;
    }
    
    return false;
}
```

### 3. Timer Reset

When an officer visits, the system:

1. Uses reflection to set the colony's `lastContactInHours` field to 0
2. Marks the colony as dirty to ensure changes are saved
3. Maintains a backup tracking map in case reflection fails

### 4. Integration with Abandonment System

The `ColonyAbandonmentManager` now checks both:

- MineColonies' built-in owner visit tracking
- Our custom officer visit tracking

It uses whichever is more recent:

```java
// Get the base last contact hours from MineColonies (tracks owner visits)
int lastContactHours = colony.getLastContactInHours();

// Check if officers have visited recently
long officerVisitHours = OfficerColonyVisitTracker.getHoursSinceOfficerVisit(colony.getID());
if (officerVisitHours >= 0 && officerVisitHours < lastContactHours) {
    // Officers visited more recently - use that time
    lastContactHours = (int) officerVisitHours;
}
```

## Performance Considerations

### Optimizations

1. **Throttled Checks**: Only checks each player every 5 seconds (100 ticks)
2. **Efficient Lookup**: Uses MineColonies' `getColonyByPosFromWorld()` for O(1) colony lookup
3. **Cached Reflection**: Reflection methods/fields are cached after first use
4. **Memory Cleanup**: Player tracking data is removed on logout

### Performance Impact

- **CPU**: Minimal - only processes online players every 5 seconds
- **Memory**: ~16 bytes per online player + ~16 bytes per colony with officer visits
- **Network**: Zero - all processing is server-side

## Configuration

The system automatically activates when colony auto-abandonment is enabled:

```properties
# In minecolonytax-server.toml
[Colony Auto-Abandon]
    EnableColonyAutoAbandon = true
    ColonyAutoAbandonDays = 14
```

No additional configuration is required.

## Logging

The system provides debug logging to help diagnose issues:

```
[INFO] 🔧 Officer colony visit tracking initialized successfully
[DEBUG] ✅ Reset abandonment timer for colony 'MyColony' - Officer/Owner PlayerName visited
[DEBUG] Colony MyColony - Using officer visit time: 2 hours (owner: 48 hours)
```

## Troubleshooting

### Officers Still Not Recognized

1. **Check Officer Rank**: Ensure the player has "Officer" or higher rank in the colony
2. **Check Logs**: Look for "Reset abandonment timer" messages in the server log
3. **Verify Config**: Ensure `EnableColonyAutoAbandon` is set to `true`

### Reflection Warnings

If you see warnings about reflection failing:

```
⚠️ Could not find lastContactInHours field - trying alternative names
```

The system will fall back to its own tracking mechanism. This is not critical - the system will still work, but changes may not be visible in MineColonies' own UI.

## Technical Details

### Reflection Usage

The system uses reflection to access MineColonies' internal `Colony` class:

```java
Class<?> colonyClass = Class.forName("com.minecolonies.core.colony.Colony");
Field lastContactInHoursField = colonyClass.getDeclaredField("lastContactInHours");
lastContactInHoursField.setAccessible(true);
```

This is necessary because MineColonies doesn't provide a public API to reset the last contact time.

### Fallback Mechanism

If reflection fails, the system maintains its own tracking:

```java
// Track last visit time for each colony
private static final Map<Integer, Long> lastOfficerVisit = new ConcurrentHashMap<>();
```

The abandonment system checks this map and uses it if officers visited more recently than the owner.

## Future Improvements

Potential enhancements:

1. **Configurable Check Interval**: Allow server admins to adjust the 5-second check interval
2. **Per-Colony Tracking**: Track which specific officers visited each colony
3. **Visit History**: Maintain a history of officer visits for auditing
4. **GUI Integration**: Display officer visit times in the colony GUI

## Related Systems

- **Colony Abandonment Manager**: Uses officer visit data to determine if colonies should be abandoned
- **Colony Activity Tracker**: May be enhanced to use officer visit data for tax generation
- **Colony Permission Monitor**: Could be integrated to track permission changes

## Credits

Implemented to fix the issue where colonies were being abandoned despite active officer management.
