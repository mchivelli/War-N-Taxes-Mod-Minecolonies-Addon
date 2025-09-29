# Colony Protection System

## Overview

The Colony Protection System allows administrators to mark specific colonies as "protected" from claiming, even when they are abandoned. This is useful for:

- **Spawn Towns**: Prevent players from claiming the main spawn area
- **Admin Colonies**: Protect administrative or server-managed colonies
- **Special Areas**: Preserve important community buildings or landmarks
- **Event Locations**: Protect colonies used for server events

## Admin Commands

### Protect a Colony
```
/wnt protectcolony <colony>
```
- **Permission**: Admin (level 2)
- **Description**: Marks a colony as protected from claiming
- **Effect**: Colony cannot be claimed even when abandoned
- **Tracking**: Records which admin protected the colony

### Remove Protection
```
/wnt unprotectcolony <colony>
```
- **Permission**: Admin (level 2)
- **Description**: Removes protection from a colony
- **Effect**: Colony becomes claimable again when abandoned

### List Protected Colonies
```
/wnt listprotected
```
- **Permission**: Admin (level 2)
- **Description**: Shows all protected colonies
- **Information Displayed**:
  - Colony name and ID
  - Current status (Active/Abandoned)
  - Admin who protected it

## How It Works

### Protection Check
When a player tries to claim a colony, the system checks:
1. Is the colony abandoned? ✓
2. Does the player meet requirements? ✓
3. **Is the colony protected?** ← New check
4. Is the player on cooldown? ✓

If protected, the claiming attempt is blocked with a clear message.

### Protection Status Display
- **`/wnt listabandoned`**: Shows protection status for each colony
  - Protected colonies show: "Status: Protected (by AdminName)"
  - Claimable colonies show: "Status: Claimable"

### Persistence
- Protection status persists until server restart
- Can be enhanced with file persistence if needed
- Protection survives colony abandonment/reclaiming cycles

## Technical Implementation

### Data Storage
```java
// Track protected colonies (colony ID -> admin name)
private static final Map<Integer, String> protectedColonies = new ConcurrentHashMap<>();
```

### Protection Methods
```java
// Protect a colony
ColonyClaimingRaidManager.protectColony(colonyId, adminName);

// Remove protection
ColonyClaimingRaidManager.unprotectColony(colonyId);

// Check if protected
boolean isProtected = ColonyClaimingRaidManager.isColonyProtected(colonyId);

// Get protecting admin
String admin = ColonyClaimingRaidManager.getProtectedBy(colonyId);
```

### Integration with Claiming System
```java
// Added to checkClaimingRequirements()
if (targetColony != null && isColonyProtected(targetColony.getID())) {
    String protectedBy = getProtectedBy(targetColony.getID());
    return new ClaimingRequirementResult(false, 
        "This colony is protected from claiming by admin " + protectedBy + 
        ". Contact an administrator for assistance.");
}
```

## Usage Examples

### Protecting a Spawn Town
```bash
# Admin protects the spawn colony
/wnt protectcolony "Spawn Town"
> Colony Spawn Town is now protected from claiming!
> This colony cannot be claimed even when abandoned.

# Players see protection status
/wnt listabandoned
> Colony: Spawn Town
>   Status: Protected (by AdminName)
```

### Removing Protection
```bash
# Admin removes protection
/wnt unprotectcolony "Spawn Town"
> Colony Spawn Town protection removed!
> This colony can now be claimed when abandoned.
```

### Viewing All Protected Colonies
```bash
/wnt listprotected
> === Protected Colonies ===
> Colony: Spawn Town (ID: 1)
>   Status: Active
>   Protected by: AdminName
> 
> Colony: Event Arena (ID: 15)
>   Status: Abandoned
>   Protected by: EventAdmin
```

## Player Experience

### Attempting to Claim Protected Colony
```bash
/wnt claimcolony "Spawn Town"
> Cannot claim colony: This colony is protected from claiming by AdminName. 
> Contact an administrator for assistance.
```

### Checking Claiming Status
```bash
/wnt claimstatus
> ✗ You cannot claim colonies: This colony is protected from claiming by AdminName. 
> Contact an administrator for assistance.
```

### Viewing Abandoned Colonies
```bash
/wnt listabandoned
> Colony: Normal Colony
>   Status: Claimable
> 
> Colony: Protected Colony
>   Status: Protected (by AdminName)
```

## Configuration

No additional configuration required - the system works with existing settings.

## Best Practices

### When to Protect Colonies
1. **Before Abandonment**: Protect important colonies before they become abandoned
2. **Spawn Areas**: Always protect spawn towns and tutorial areas
3. **Community Buildings**: Protect shared facilities like markets or arenas
4. **Event Locations**: Protect colonies used for server events

### Admin Workflow
1. Identify colonies that should never be claimed
2. Use `/wnt protectcolony <colony>` to protect them
3. Use `/wnt listprotected` to audit protected colonies
4. Remove protection with `/wnt unprotectcolony <colony>` when appropriate

### Communication
- Inform players about protected colonies through server announcements
- Use clear naming conventions for protected colonies
- Document protection reasons for other admins

## Troubleshooting

### Colony Not Found
- Ensure colony name is spelled correctly
- Use quotes for colony names with spaces: `"My Colony"`
- Check if colony still exists with `/wnt listabandoned`

### Permission Denied
- Protection commands require admin permissions (level 2)
- Regular players cannot protect/unprotect colonies

### Protection Not Working
- Verify protection with `/wnt listprotected`
- Check if colony is actually abandoned
- Ensure no typos in colony names

## Future Enhancements

1. **File Persistence**: Save protection status to file for server restart persistence
2. **Reason Tracking**: Allow admins to specify why a colony is protected
3. **Temporary Protection**: Add time-based protection that expires automatically
4. **Bulk Operations**: Protect multiple colonies at once
5. **Permission Levels**: Different protection levels for different admin ranks