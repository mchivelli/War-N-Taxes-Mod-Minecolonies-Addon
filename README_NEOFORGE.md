# War 'N Taxes Mod - NeoForge 1.21.1 Branch

⚠️ **WORK IN PROGRESS** ⚠️

This branch contains the NeoForge 1.21.1 port of the War 'N Taxes mod. **This version is NOT yet functional** and is under active development.

## Branch Status

- **Branch Name:** `neoforge-1.21.1`
- **Target Minecraft:** 1.21.1
- **Target NeoForge:** 21.1.72
- **Java Version:** 21
- **Status:** Migration in progress (~15% complete)
- **Compilation:** ❌ Currently failing (expected)
- **Runnable:** ❌ No

## What's Been Done

### ✅ Completed
- Build system configuration (Gradle, settings, properties)
- NeoForge metadata file (`neoforge.mods.toml`)
- Main mod class imports (`MineColonyTax.java`)
- Partial config migration (`TaxConfig.java`)
- Migration documentation and guides

### ⏳ In Progress
- Package import migration across all Java files
- Config specification updates

### 🔜 To Do
- **Critical:** Capability System → Data Attachments migration
- **Critical:** Networking system rewrite
- Event handler updates
- Recipe system updates
- GUI code updates
- Fix all compilation errors

## Why This Branch Exists

Minecraft 1.21+ uses NeoForge (a fork of MinecraftForge), which requires significant code changes. This branch is preparing for:

1. Minecraft 1.21.1 support
2. NeoForge API migration
3. Dependency mod updates (MineColonies, SDMShop, etc.)

## Building (Currently Won't Work)

This won't compile yet, but when ready:

```bash
./gradlew build
```

## For Developers

### Required Tools
- Java 21 JDK
- Gradle 8.8+
- Git

### Key Migration Documents
1. `NEOFORGE_MIGRATION_GUIDE.md` - Detailed technical migration guide
2. `MIGRATION_STATUS.md` - Current progress and next steps
3. `migrate_imports.ps1` - PowerShell script for bulk import updates

### Major API Changes

#### 1. Capability System → Data Attachments
```java
// Before (Forge)
player.getCapability(CAPABILITY).ifPresent(data -> {});

// After (NeoForge)
PlayerWarData data = player.getData(PLAYER_WAR_DATA);
```

#### 2. Networking
```java
// Before (Forge SimpleChannel)
CHANNEL.registerMessage(id, Packet.class, ...);

// After (NeoForge CustomPacketPayload)  
public record MyPacket(...) implements CustomPacketPayload {
    // New packet format
}
```

#### 3. Package Names
- `net.minecraftforge.*` → `net.neoforged.*`
- `ForgeConfigSpec` → `ModConfigSpec`
- `MinecraftForge.EVENT_BUS` → `NeoForge.EVENT_BUS`

## Blocked By Dependencies

This mod requires the following mods to be updated to 1.21.1 NeoForge:

- **MineColonies** (core dependency)
- **SDM Shop** (economy system)
- **FTB Teams** (team functionality)
- **Recruits** (optional)

## Testing Plan

Once compilation succeeds, extensive testing is required:

- [ ] Server starts without crashes
- [ ] Tax generation and claiming
- [ ] War system
- [ ] Raid system  
- [ ] PvP battles
- [ ] Colony permissions
- [ ] All GUIs
- [ ] Player data persistence
- [ ] Web API
- [ ] All commands

## Contributing

If you want to help with the migration:

1. Check `MIGRATION_STATUS.md` for current status
2. Pick a pending task
3. Follow the migration patterns in `NEOFORGE_MIGRATION_GUIDE.md`
4. Test your changes thoroughly
5. Submit a PR to the `neoforge-1.21.1` branch

## Questions?

This is a complex migration involving:
- 59+ Java files
- Complete capability system rewrite
- Full networking rewrite
- Minecraft 1.21 API changes

Please be patient as we work through this systematically!

## For Users

**DO NOT use this branch for gameplay yet!** 

Use the main branch with Minecraft 1.20.1 Forge until this migration is complete.

---

**For the stable 1.20.1 Forge version, switch to the `main` branch.**
