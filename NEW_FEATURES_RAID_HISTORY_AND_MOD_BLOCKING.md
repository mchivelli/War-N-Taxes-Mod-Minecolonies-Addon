# New Features: Raid History Tracking & Mod-Level Block Filtering

## 1. Enhanced Raid History System

### Overview
The raid history system has been enhanced to track structured raid data including raider UUID, player name, amount stolen, timestamp, and success/failure status.

### Features

#### Structured Raid Tracking
- **Raider UUID**: Track who raided your colony by UUID (persists across name changes)
- **Raider Name**: Human-readable player name at time of raid
- **Amount Stolen**: Exact amount of currency stolen during successful raids
- **Timestamp**: Date and time of raid (formatted as `yyyy-MM-dd HH:mm:ss`)
- **Success Status**: Whether the raid was successful or failed
- **Failure Reason**: If raid failed, stores the reason (e.g., "left colony boundaries", "failed to kill any guards")

#### Query Methods
New methods available in `HistoryManager.ColonyHistory`:
- `getStructuredRaids()` - Get all raid entries with full details
- `getRaidsByPlayer(UUID)` - Filter raids by specific player
- `getTotalAmountStolen()` - Get total amount stolen across all successful raids
- `getSuccessfulRaidCount()` - Count of successful raids
- `getFailedRaidCount()` - Count of failed raids

#### Backward Compatibility
- Legacy string-based raid events (`getRaidEvents()`) still work
- New structured data automatically generates legacy format entries
- Existing raid history commands continue to function

### Usage

#### Via `/raidhistory` Command
```
/raidhistory                    # Show history for your colony
/raidhistory ColonyName        # Show history for specific colony
/raidhistory 123               # Show history for colony ID 123
```

**Output Format:**
- Successful raids: `§c[Raid] §7[2025-10-24 23:15:32] §fSUCCESS §7- §a1500 §7stolen by §fPlayerName`
- Failed raids: `§e[Raid] §7[2025-10-24 23:20:15] §fFAILED §7- §cleft colony boundaries §7by §fPlayerName`

#### Programmatic Access
```java
// Get colony history
ColonyHistory history = HistoryManager.getColonyHistory(colonyId);

// Get all raids
List<RaidEntry> allRaids = history.getStructuredRaids();

// Get raids by specific player
UUID playerUUID = player.getUUID();
List<RaidEntry> playerRaids = history.getRaidsByPlayer(playerUUID);

// Get statistics
int totalStolen = history.getTotalAmountStolen();
int successCount = history.getSuccessfulRaidCount();
int failCount = history.getFailedRaidCount();

// Access individual raid data
for (RaidEntry raid : allRaids) {
    String raiderUUID = raid.getRaiderUUID();
    String raiderName = raid.getRaiderName();
    int amount = raid.getAmountStolen();
    boolean success = raid.isSuccessful();
    String failReason = raid.getFailureReason(); // null if successful
    String timestamp = raid.getFormattedTimestamp();
}
```

### Storage
- Data stored in: `config/warntax/colony_history.json`
- Automatically saved after each raid
- JSON format for easy external parsing
- Keeps last 100 raids per colony

---

## 2. Mod-Level Block Filtering

### Overview
You can now block or allow entire mods at once using the `#` prefix in block interaction filters during raids and wars.

### Syntax

#### Blacklist (Blocks Interaction)
Use `#modid` to block ALL blocks from a specific mod:
```
#refinedstorage   # Blocks all Refined Storage blocks
#mekanism         # Blocks all Mekanism blocks
#ae2              # Blocks all Applied Energistics 2 blocks
```

#### Whitelist (Allows Interaction)
Use `#modid` to allow ALL blocks from a specific mod:
```
#ironchest                 # Allow all Iron Chests blocks
#sophisticatedstorage      # Allow all Sophisticated Storage blocks
#metalbarrels              # Allow all Metal Barrels blocks
```

### Configuration

#### File Location
`config/warntax-common.toml`

#### Example Configuration
```toml
# Block Interaction Blacklist (HIGHEST PRIORITY - ALWAYS BLOCKS)
BlockInteractionBlacklist = [
    # Specific blocks
    "minecraft:bedrock",
    "minecraft:command_block",
    "minecolonies:blockhuttownhall",
    
    # Entire mods (using # prefix)
    "#refinedstorage",     # Block all Refined Storage
    "#mekanism",           # Block all Mekanism
    "#ae2",                # Block all AE2
]

# Block Interaction Whitelist (EXPLICITLY ALLOWS)
BlockInteractionWhitelist = [
    # Specific blocks
    "minecraft:chest",
    "minecraft:barrel",
    "minecraft:furnace",
    
    # Entire mods (using # prefix)
    "#ironchest",          # Allow all Iron Chests
    "#sophisticatedstorage" # Allow all Sophisticated Storage
]
```

### How It Works

#### Priority Order
1. **Feature Disabled Check** - If filter is disabled, passes through
2. **Blacklist Check** - If matched (specific block or mod), ALWAYS DENY
3. **Whitelist Check** - If matched (specific block or mod), ALLOW
4. **Fall Through** - Pass to existing MineColonies protection systems

#### Matching Logic
- **Specific Block**: Exact match required (e.g., `minecraft:chest`)
- **Mod-Level**: Matches any block starting with `modid:` (e.g., `#refinedstorage` matches `refinedstorage:controller`, `refinedstorage:grid`, etc.)

### Use Cases

#### Example 1: Protect All Storage Mods
```toml
BlockInteractionBlacklist = [
    "#refinedstorage",
    "#ae2",
    "#storagedrawers",
    "#sophisticatedbackpacks",
]
```

#### Example 2: Allow Looting Specific Storage Mods
```toml
BlockInteractionWhitelist = [
    "#ironchest",
    "#metallicbarrels",
]
```

#### Example 3: Mixed Configuration
```toml
# Block all Refined Storage except the controller
BlockInteractionBlacklist = [
    "#refinedstorage"
]

BlockInteractionWhitelist = [
    "refinedstorage:controller"  # Specific block override
]
# Note: Blacklist takes priority, so this won't work!
# To allow specific blocks, don't use mod-level blacklist
```

### Configuration Options

#### Enable/Disable Filter System
```toml
EnableBlockInteractionFilter = true
```

#### Enable for Wars
```toml
BlockFilterWars = true
```

#### Enable for Raids
```toml
BlockFilterRaids = true
```

### Debug Logging

Enable debug logging to see filter decisions in real-time:
```
LOGGER.debug("🚫 BLACKLIST DENIED (MOD): Player attempted break on block refinedstorage:controller (mod refinedstorage is blacklisted)")
LOGGER.debug("✅ WHITELIST ALLOWED (MOD): Player use block ironchest:iron_chest (mod ironchest is whitelisted)")
```

### Player Feedback

When a block is blocked, players see:
```
🚫 Blocks from this mod are protected during conflicts!
```

---

## Technical Implementation

### Files Modified

1. **HistoryManager.java**
   - Added `RaidEntry` class with structured raid data
   - Added query methods for filtering and statistics
   - Backward compatible with legacy string events

2. **RaidManager.java**
   - Updated to use structured raid tracking
   - Calls `addRaidEntry()` instead of `addRaidEvent()`
   - Tracks success/failure with reasons

3. **BlockInteractionFilterHandler.java**
   - Added mod-level matching logic for `#` prefix
   - Checks both exact matches and mod-level matches
   - Supports both blacklist and whitelist

4. **TaxConfig.java**
   - Updated config comments to document `#` prefix syntax
   - No code changes required (already supported lists of strings)

### Testing Recommendations

#### Raid History Testing
1. Perform a successful raid and check `/raidhistory`
2. Fail a raid (leave boundaries) and verify failure is logged
3. Check that UUID and timestamp are correct
4. Verify JSON file in `config/warntax/colony_history.json`

#### Mod-Level Block Filter Testing
1. Add `#refinedstorage` to blacklist
2. Start a raid and try to break a Refined Storage block
3. Verify interaction is blocked with message
4. Check debug logs for filter decisions
5. Test with whitelist entries

---

## Migration Notes

### For Server Admins
- **No breaking changes** - Existing configurations continue to work
- **New JSON data** - Old raid history remains in legacy format
- **Config update recommended** - Add mod-level entries to blacklist/whitelist as desired

### For Developers
- **Use structured API** - Prefer `getStructuredRaids()` over `getRaidEvents()`
- **Query methods available** - Use `getRaidsByPlayer()`, `getTotalAmountStolen()`, etc.
- **Backward compatible** - Legacy methods still work

---

## Examples

### Example 1: Find All Raids by Specific Player
```java
UUID targetPlayer = UUID.fromString("...");
ColonyHistory history = HistoryManager.getColonyHistory(colonyId);
List<RaidEntry> playerRaids = history.getRaidsByPlayer(targetPlayer);

for (RaidEntry raid : playerRaids) {
    if (raid.isSuccessful()) {
        System.out.println("Successful raid: " + raid.getAmountStolen() + " stolen");
    } else {
        System.out.println("Failed raid: " + raid.getFailureReason());
    }
}
```

### Example 2: Protect All Tech Mods During Wars
```toml
[BlockInteractionBlacklist]
BlockInteractionBlacklist = [
    "#refinedstorage",
    "#mekanism",
    "#ae2",
    "#thermal",
    "#immersiveengineering",
    "#create"
]

BlockFilterWars = true
BlockFilterRaids = true
```

### Example 3: Allow Raiding Storage But Not Tech
```toml
[BlockInteractionBlacklist]
BlockInteractionBlacklist = [
    "#refinedstorage",
    "#ae2",
    "#mekanism"
]

[BlockInteractionWhitelist]
BlockInteractionWhitelist = [
    "#ironchest",
    "#metalbarrels",
    "minecraft:chest",
    "minecraft:barrel"
]
```
