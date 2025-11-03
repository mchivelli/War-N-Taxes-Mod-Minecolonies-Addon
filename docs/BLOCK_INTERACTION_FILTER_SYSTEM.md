# Block Interaction Filter System

## Overview

The Block Interaction Filter System provides fine-grained control over which blocks can and cannot be interacted with during raids and wars. This system **overrides all other protection systems** to enforce server-defined rules about block interactions during conflicts.

## Key Features

### 🛡️ Security Architecture

1. **Highest Priority Enforcement**: Runs before all other protection handlers
2. **Blacklist Protection**: Prevents interaction with critical blocks (highest priority)
3. **Whitelist Allowance**: Explicitly permits interaction with specific blocks
4. **Pass-Through Default**: Defers to existing systems for unlisted blocks

### 🎯 Use Cases

- **Prevent Griefing**: Blacklist critical infrastructure (town halls, command blocks, etc.)
- **Enable Looting**: Whitelist storage blocks for raid rewards (chests, barrels)
- **Protect Modded Blocks**: Add modded blocks to blacklist/whitelist as needed
- **Granular Control**: Different rules for raids vs. wars

## Configuration

All settings are in `config/minecolonytax-common.toml`:

### Enable/Disable

```toml
# Master switch for the entire filter system
EnableBlockInteractionFilter = true

# Apply filtering during wars
BlockFilterWars = true

# Apply filtering during raids
BlockFilterRaids = true
```

### Blacklist (Protection)

Blocks in the blacklist **CANNOT** be interacted with during active raids/wars:

```toml
BlockInteractionBlacklist = [
    "minecraft:bedrock",
    "minecraft:command_block",
    "minecraft:chain_command_block",
    "minecraft:repeating_command_block",
    "minecraft:structure_block",
    "minecraft:jigsaw",
    "minecolonies:blockhuttownhall"
]
```

**Default Blacklist Protects:**
- Bedrock (world boundaries)
- All command blocks (admin tools)
- Structure blocks (world edit tools)
- Jigsaw blocks (generation tools)
- MineColonies Town Hall (colony center)

### Whitelist (Explicit Access)

Blocks in the whitelist **CAN** be interacted with during active raids/wars:

```toml
BlockInteractionWhitelist = [
    "minecraft:chest",
    "minecraft:barrel",
    "minecraft:furnace",
    "minecraft:blast_furnace",
    "minecraft:smoker",
    "minecraft:dropper",
    "minecraft:dispenser",
    "minecraft:hopper"
]
```

**Default Whitelist Allows:**
- Chests (looting)
- Barrels (storage access)
- Furnaces (resource interaction)
- Hoppers/Droppers/Dispensers (automation blocks)

## How It Works

### Priority Order

1. **Feature Disabled**: If system is disabled, all blocks pass through
2. **No Colony**: If block is not in a colony, pass through
3. **No Active Conflict**: If no raid/war is active, pass through
4. **Blacklist Check**: If block matches blacklist → **DENY** (highest priority)
5. **Whitelist Check**: If block matches whitelist → **ALLOW**
6. **Pass Through**: Block not in either list → defer to existing protection systems

### Event Handling

The system intercepts THREE types of block interactions:
- **Block Breaking**: `BlockEvent.BreakEvent`
- **Block Placement**: `BlockEvent.EntityPlaceEvent`
- **Block Usage**: `PlayerInteractEvent.RightClickBlock` (right-click)

All events use `EventPriority.HIGHEST` to ensure filter runs first.

### Conflict Detection

The system detects active raids/wars by checking:

**For Raids:**
- Is the player actively raiding?
- Is the colony being raided?

**For Wars:**
- Is the colony in an active war?
- Is the player a war participant (attacker or defender)?

## Security Considerations

### ✅ Security Features

1. **Blacklist Priority**: Blacklist ALWAYS overrides whitelist
2. **Immutable Config**: Config values are copied to prevent runtime modification
3. **Early Enforcement**: HIGHEST priority prevents bypasses
4. **Comprehensive Coverage**: All interaction types (break/place/use) filtered
5. **Logging**: All denied interactions are logged for monitoring

### ⚠️ Important Notes

- **Admin Override**: Server admins (permission level 2+) may bypass protection in some contexts
- **Blacklist > Whitelist**: A block in BOTH lists will be denied (blacklist wins)
- **Default Behavior**: Unlisted blocks follow normal MineColonies protection rules
- **Performance**: Minimal overhead - checks only run during active conflicts

## Adding Modded Blocks

### Format

Block IDs use the format: `modid:blockname`

### Examples

**Protect Iron Chests Mod chests:**
```toml
BlockInteractionBlacklist = [
    # ... existing entries ...
    "ironchest:iron_chest",
    "ironchest:gold_chest",
    "ironchest:diamond_chest"
]
```

**Allow Applied Energistics storage:**
```toml
BlockInteractionWhitelist = [
    # ... existing entries ...
    "appliedenergistics2:chest",
    "appliedenergistics2:drive"
]
```

### Finding Block IDs

1. **F3+H Debug Mode**: Enable advanced tooltips in Minecraft
2. **Look at Block**: Hover over block in inventory to see ID
3. **Check Mod Docs**: Most mods document their block IDs

## Usage Examples

### Example 1: Protect All Colony Buildings

```toml
BlockInteractionBlacklist = [
    "minecolonies:blockhuttownhall",
    "minecolonies:blockhutbarracks",
    "minecolonies:blockhutguardtower",
    "minecolonies:blockhutwarehouse"
]
```

### Example 2: Allow Looting But Protect Infrastructure

```toml
# Prevent breaking critical blocks
BlockInteractionBlacklist = [
    "minecraft:bedrock",
    "minecolonies:blockhuttownhall"
]

# Allow opening storage for rewards
BlockInteractionWhitelist = [
    "minecraft:chest",
    "minecraft:barrel",
    "minecraft:shulker_box",
    "ironchest:iron_chest"
]
```

### Example 3: War-Only Restrictions

```toml
# Enable filtering only during wars, not raids
BlockFilterWars = true
BlockFilterRaids = false
```

## Troubleshooting

### Players Can Still Break Protected Blocks

**Check:**
1. Is `EnableBlockInteractionFilter = true`?
2. Is the correct filter enabled (`BlockFilterWars` or `BlockFilterRaids`)?
3. Is the block ID spelled correctly in the blacklist?
4. Is a raid/war actually active?

### Players Cannot Open Whitelisted Chests

**Check:**
1. Is the block in the blacklist? (Blacklist overrides whitelist)
2. Is the chest owned by a colony with other protection systems?
3. Check logs for `WHITELIST ALLOWED` messages

### How to Find Block IDs

**Enable Debug Mode:**
1. Press F3+H in Minecraft
2. Hover over blocks in inventory
3. The tooltip will show the block ID

**Check Logs:**
The filter logs all denials with the block ID:
```
🚫 BLACKLIST DENIED: Player Steve attempted break on blacklisted block minecraft:bedrock at [100, 64, 200]
```

## Technical Details

### File Locations

- **Configuration**: `config/minecolonytax-common.toml`
- **Event Handler**: `net.machiavelli.minecolonytax.event.BlockInteractionFilterHandler`
- **Config Getters**: `net.machiavelli.minecolonytax.TaxConfig`

### Integration Points

The filter integrates with:
- **RaidManager**: Detects active raids via `getActiveRaidForPlayer()` and `getActiveRaidForColony()`
- **WarSystem**: Detects active wars via `ACTIVE_WARS` map
- **MineColonies API**: Uses `IColonyManager` to identify colonies

### Performance

- **Event Priority**: HIGHEST (runs first)
- **Check Overhead**: ~0.1ms per interaction during conflicts
- **Memory**: ~1KB per 100 blacklist/whitelist entries
- **Optimization**: Early exits when feature disabled or no conflict active

## Best Practices

### 1. Start with Default Configuration

The default blacklist/whitelist is designed for balanced gameplay. Test before making changes.

### 2. Add Critical Blocks to Blacklist

Always blacklist:
- Admin/creative blocks (command blocks, structure blocks)
- Colony core buildings (town hall)
- Important infrastructure

### 3. Whitelist for Raid Rewards

Allow looting of:
- Storage blocks (chests, barrels)
- Resource blocks (furnaces)
- But NOT critical infrastructure

### 4. Test in a Safe Environment

Before deploying changes to production:
1. Test on a dev server
2. Verify blacklist blocks cannot be broken
3. Verify whitelist blocks can be accessed
4. Check logs for unexpected behavior

### 5. Document Custom Rules

If you add custom blocks, document why:
```toml
# Custom Protection Rules
# Blacklist: Protect custom vault mod blocks
BlockInteractionBlacklist = [
    # ... defaults ...
    "custommodvaults:vault_block"  # Added: Protect player vaults during raids
]
```

## FAQ

**Q: Does this replace existing MineColonies protection?**
A: No, it supplements it. This system provides an override layer for specific blocks during conflicts.

**Q: Can admins bypass these restrictions?**
A: The filter itself does not check admin status, but underlying protection systems may allow admin overrides.

**Q: Will this break my existing raids/wars?**
A: No, it's backward compatible. With default settings, it only affects specific protected blocks.

**Q: Can I disable this for specific colonies?**
A: Currently, filtering is global. Per-colony filtering may be added in a future update.

**Q: What happens if a block is in BOTH blacklist and whitelist?**
A: The blacklist takes priority - the block will be protected.

## Related Systems

- **War Actions**: `WarActions` config controls overall war permissions
- **Raid Actions**: `RaidActions` config controls overall raid permissions
- **Claiming Actions**: `ClaimingActions` config for abandoned colony claiming
- **Colony Permissions**: MineColonies native permission system

## Changelog

### Version 1.0 (Initial Release)
- ✅ Blacklist/whitelist system implementation
- ✅ HIGHEST priority event handling
- ✅ War and raid detection
- ✅ Comprehensive logging
- ✅ Security-first design
- ✅ Default protection for critical blocks
- ✅ Default whitelist for storage blocks

## Support

For issues or questions:
1. Check server logs for filter messages
2. Verify configuration syntax
3. Test with minimal blacklist/whitelist first
4. Report bugs with full logs and config
