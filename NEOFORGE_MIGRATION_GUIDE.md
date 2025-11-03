# NeoForge 1.21.1 Migration Guide

## Overview
This document outlines the migration of War 'N Taxes Mod from Minecraft 1.20.1 Forge to Minecraft 1.21.1 NeoForge.

## Key Changes

### 1. Package Renames
| Forge (1.20.1) | NeoForge (1.21.1) |
|----------------|-------------------|
| `net.minecraftforge.*` | `net.neoforged.*` |
| `net.minecraftforge.fml.*` | `net.neoforged.fml.*` |
| `net.minecraftforge.common.*` | `net.neoforged.neoforge.common.*` |
| `net.minecraftforge.event.*` | `net.neoforged.neoforge.event.*` |
| `net.minecraftforge.network.*` | `net.neoforged.neoforge.network.*` |
| `net.minecraftforge.eventbus.*` | `net.neoforged.bus.*` |

### 2. Capability System → Data Attachments
NeoForge replaces the Capability system with Data Attachments:
- `Capability<T>` → `AttachmentType<T>`
- `CapabilityManager` → `AttachmentType.Builder`
- `ICapabilityProvider` → Data Attachments API
- `@CapabilityInject` → Direct field registration
- `AttachCapabilitiesEvent` → Attachments are automatic

### 3. Networking Changes
- `SimpleChannel` → `EventNetworkChannel` / `StreamCodec` based system
- New packet registration using `payload` system
- `NetworkDirection` removed, replaced with context-based handling
- Packets must implement `CustomPacketPayload`

### 4. Event Bus Changes
- Event subscription remains similar
- `@Mod.EventBusSubscriber` still works
- Bus types: `MOD` bus and `FORGE` bus (now `NeoForge.EVENT_BUS`)

### 5. Java Version
- Java 17 → Java 21 (required for 1.21.1)

### 6. Config Changes
- ForgeConfigSpec remains similar but in new package
- Config files location unchanged

### 7. Mod Metadata
- `mods.toml` → `neoforge.mods.toml`
- `modLoader="javafml"` stays the same
- Dependency format slightly changed

## Migration Steps

### Phase 1: Build Configuration ✓
- [x] Update `settings.gradle` for NeoForge repository
- [x] Update `gradle.properties` with MC 1.21.1 and NeoForge versions
- [x] Update `build.gradle` with NeoGradle plugin
- [x] Create `neoforge.mods.toml` metadata file

### Phase 2: Core Package Migrations
- [ ] Update `MineColonyTax.java` main mod class
- [ ] Migrate all package imports across codebase
- [ ] Update event handling code
- [ ] Update config system

### Phase 3: Capability → Data Attachments
- [ ] Replace `PlayerWarDataCapability.java` with Data Attachments
- [ ] Remove capability registration code
- [ ] Update all capability access points

### Phase 4: Networking System
- [ ] Migrate `NetworkHandler.java` to new packet system
- [ ] Update all packet classes to implement `CustomPacketPayload`
- [ ] Update packet encoding/decoding

### Phase 5: Dependency Updates
- [ ] Wait for MineColonies 1.21.1 NeoForge release
- [ ] Wait for SDM Shop 1.21.1 NeoForge release
- [ ] Wait for FTB Teams 1.21.1 NeoForge release
- [ ] Wait for Recruits 1.21.1 NeoForge release
- [ ] Update dependency version IDs in build.gradle

### Phase 6: Testing & Fixes
- [ ] Resolve all compilation errors
- [ ] Test core tax system
- [ ] Test war system
- [ ] Test raid system
- [ ] Test GUI functionality
- [ ] Test Web API
- [ ] Test all commands

## Breaking API Changes in MC 1.21.1

### Registry Changes
- Registry system has been overhauled
- DeferredRegister still exists but with new syntax

### Component System
- Item components replaced old NBT system for item data
- Need to update any item NBT handling

### Network Protocol
- Completely rewritten for 1.21+
- Must use new packet payload system

## Notes
- This migration requires waiting for dependency mods to update
- Some features may need reworking due to API changes
- Testing on a development server is essential before release
- Version bumped to 4.0.0 to reflect major changes

## Current Status
- Branch: `neoforge-1.21.1`
- Build files: Updated
- Source code: In progress
- Compilation: Not yet tested
- Dependencies: Awaiting updates
