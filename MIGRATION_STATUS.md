# NeoForge 1.21.1 Migration Status

## Current Branch: `neoforge-1.21.1`

## ✅ Completed Steps

### 1. Build System Migration
- [x] Updated `settings.gradle` to use NeoForge Maven repository
- [x] Updated `gradle.properties`:
  - Minecraft version: 1.20.1 → 1.21.1
  - Java version: 17 → 21
  - Forge → NeoForge (version 21.1.72)
  - Mod version bumped to 4.0.0
  - Parchment mappings updated to 2024.11.17-1.21.1
- [x] Updated `build.gradle`:
  - Replaced MinecraftForge Gradle plugin with NeoGradle 7.0.163
  - Updated all Forge properties to NeoForge equivalents
  - Changed dependency declarations
  - Updated processResources to use `neoforge.mods.toml`
- [x] Created `neoforge.mods.toml` with proper NeoForge metadata format

### 2. Core Mod Classes
- [x] `MineColonyTax.java`: Updated imports and API calls
  - `net.minecraftforge.*` → `net.neoforged.*`
  - `MinecraftForge.EVENT_BUS` → `NeoForge.EVENT_BUS`
- [x] `TaxConfig.java`: Partially migrated (imports updated, fields need completion)

### 3. Documentation
- [x] Created `NEOFORGE_MIGRATION_GUIDE.md` with comprehensive migration details
- [x] Created PowerShell migration script for bulk import replacements

## ⏳ In Progress

### Package Import Migration
The following types of imports need updating across ~59 Java files:

```
net.minecraftforge.fml.* → net.neoforged.fml.*
net.minecraftforge.common.* → net.neoforged.neoforge.common.*
net.minecraftforge.event.* → net.neoforged.neoforge.event.*
net.minecraftforge.eventbus.* → net.neoforged.bus.*
net.minecraftforge.network.* → net.neoforged.neoforge.network.*
ForgeConfigSpec → ModConfigSpec
```

### TaxConfig.java
- Needs all `ForgeConfigSpec` references replaced with `ModConfigSpec`
- ~153 field declarations need updating

## 🔄 Pending Steps

### 3. Capability System → Data Attachments
**CRITICAL CHANGE**: NeoForge 1.21.1 completely replaced the Capability system

**Files Affected:**
- `capability/PlayerWarDataCapability.java` - Complete rewrite needed
- All files accessing player war data capabilities

**Migration Strategy:**
```java
// OLD (Forge Capabilities)
Capability<PlayerWarData> CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});
player.getCapability(CAPABILITY).ifPresent(data -> {...});

// NEW (NeoForge Data Attachments)
AttachmentType<PlayerWarData> PLAYER_WAR_DATA = AttachmentType.builder(() -> new PlayerWarData()).build();
PlayerWarData data = player.getData(PLAYER_WAR_DATA);
```

**Action Items:**
1. Create new `PlayerWarDataAttachment.java` replacing capability system
2. Register attachment type in mod constructor
3. Update all capability access points to use attachment API
4. Remove capability event handlers
5. Test data persistence (attachments auto-serialize)

### 4. Networking System Migration
**Files Affected:**
- `network/NetworkHandler.java`
- All `network/packets/*.java` files (14 packet classes)

**Changes Required:**
```java
// OLD (Forge SimpleChannel)
CHANNEL.registerMessage(id, Packet.class, Packet::encode, Packet::decode, Packet::handle);

// NEW (NeoForge Payloads)
public record PacketName(data...) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PacketName> TYPE = new Type<>(ResourceLocation);
    public void write(FriendlyByteBuf buf) {...}
    public static PacketName read(FriendlyByteBuf buf) {...}
}
```

### 5. Event Handler Updates
**Files Affected** (~18 event handler classes):
- All event subscription annotations remain the same
- Event class paths need updating to `net.neoforged.neoforge.event.*`
- Some event methods have changed signatures in 1.21.1

**Key Files:**
- `event/WarEventHandler.java`
- `event/RaidKillTracker.java`
- `event/PvPEventHandler.java`
- `pvp/PvPEventHandler.java`
- etc.

### 6. Recipe System
**Files Affected:**
- `recipe/ModRecipeSerializers.java`
- Custom recipe types

**Changes:**
- Recipe serializers registration syntax slightly changed
- DeferredRegister still exists but with new generics

### 7. Command System
**Minimal changes expected** - Commands should work with minor tweaks:
- Some `CommandSourceStack.sendSuccess()` signatures changed in 1.21

### 8. Client-Side Code
**Files Affected:**
- `client/KeyBindings.java`
- `gui/*.java` (4 GUI classes)
- Client event handlers

**Changes:**
- Key binding registration updated
- GUI rendering may need updates for 1.21.1 changes

## 🚫 Blocked Items

### External Dependencies
All external mod dependencies are commented out until 1.21.1 NeoForge versions are available:

- **MineColonies** - Required, waiting for update
- **SDM Shop** - Required for economy features
- **FTB Teams** - Required for team functionality
- **Recruits** - Optional integration
- **BlockUI, Structurize, Domum Ornamentum** - MineColonies dependencies
- **JEI** - Optional integration

**Status:** Check CurseForge regularly for updates

## 🧪 Testing Checklist (Post-Migration)

Once compilation succeeds:

- [ ] Mod loads without crashes
- [ ] Tax generation works
- [ ] Tax claiming works
- [ ] War declaration and combat
- [ ] Raid system
- [ ] PvP arena
- [ ] Colony permissions
- [ ] GUI opens and functions
- [ ] Commands execute correctly
- [ ] Config loads and saves
- [ ] Player data persists (critical for war stats)
- [ ] Web API functions (if enabled)
- [ ] Vassalization system
- [ ] Guard resistance
- [ ] Citizen militia
- [ ] All economy integrations

## ⚠️ Known Issues

1. **Compilation Errors**: Expected until all imports are migrated and Gradle sync completes
2. **Data Attachment Migration**: Most complex part - requires careful testing of player data persistence
3. **Networking**: New packet system requires complete rewrite of packet classes
4. **API Changes**: Minecraft 1.21.1 has significant changes that may require code adjustments beyond just NeoForge migration

## 📝 Next Steps

1. **Run PowerShell migration script** to bulk-update imports:
   ```powershell
   .\migrate_imports.ps1
   ```

2. **Manually complete TaxConfig.java**: Replace all `ForgeConfigSpec` → `ModConfigSpec`

3. **Sync Gradle**: Download NeoForge dependencies
   ```
   ./gradlew --refresh-dependencies
   ```

4. **Migrate Capability System**: Complete rewrite of PlayerWarDataCapability

5. **Migrate Networking**: Update all packet classes

6. **Fix remaining compilation errors**

7. **Wait for dependency updates**

8. **Test extensively**

## 💡 Development Notes

- Keep Forge 1.20.1 version on `main` branch
- NeoForge 1.21.1 version on `neoforge-1.21.1` branch
- Consider maintaining both versions until ecosystem fully migrates
- Version 4.0.0 signifies major breaking changes

## 📚 References

- [NeoForge Documentation](https://docs.neoforged.net/)
- [NeoForge Migration Primer](https://docs.neoforged.net/docs/1.21.x/gettingstarted/modmigration/)
- [Minecraft 1.21 Changes](https://fabricmc.net/wiki/tutorial:1.21)
- [Parchment Mappings](https://parchmentmc.org/)

---

**Last Updated:** Current session  
**Migration Progress:** ~15% complete  
**Estimated Remaining Work:** 20-30 hours (excluding dependency wait time)
