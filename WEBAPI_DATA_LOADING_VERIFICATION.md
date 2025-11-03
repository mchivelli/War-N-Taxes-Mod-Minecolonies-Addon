# WebAPI Data Loading Verification Guide

## Critical Issue Found & Fixed

### The Problem with getOrCreate()

The original code used `PlayerWarDataCapability.getOrCreate()`:

```java
public static PlayerWarData getOrCreate(Player player) {
    return player.getCapability(CAPABILITY).orElseGet(PlayerWarData::new);
}
```

**This is DANGEROUS for reading data!**

When you call `orElseGet(PlayerWarData::new)`:
- ❌ Creates a NEW, EMPTY PlayerWarData instance if capability not present
- ❌ This new instance is NOT attached to the player
- ❌ Has all zeros: 0 wars won, 0 raids, 0 kills
- ❌ Is immediately discarded (not saved)
- ❌ API returns empty stats instead of real data

### The Fix

Now using `capability.resolve().orElse(null)`:

```java
var capability = player.getCapability(PlayerWarDataCapability.CAPABILITY);
PlayerWarData data = capability.resolve().orElse(null);

if (data != null) {
    // Use real data
} else {
    // Capability not loaded - log warning
}
```

**Benefits:**
- ✅ Returns null if capability not attached (we can detect this)
- ✅ Only reads REAL, ATTACHED capabilities
- ✅ Logs warnings when data isn't available
- ✅ No false empty stats

---

## How Data Loading Works

### 1. Capability Registration (Server Startup)
```
[INFO] REGISTERING PlayerWarData capability on MOD event bus
[INFO] PlayerWarData capability registration complete
```

### 2. Player Join Sequence

#### Step A: Entity Created
```
[DEBUG] Attaching PlayerWarData capability to player entity
```
*Capability is attached but EMPTY at this point*

#### Step B: Data Loaded from Disk
```
[INFO] Player 550e8400-e29b-41d4-a716-446655440000 logged in, ensuring war data is loaded
[INFO] War data loaded on player login: {playersKilledInWar:42,raidedColonies:8,...}
[INFO] War stats on login - PlayersKilled: 42, RaidedColonies: 8, AmountRaided: 5000, WarsWon: 15, WarStalemates: 2
```
*Now the capability has real data from the player's .dat file*

#### Step C: API Can Read Data
```
[DEBUG] Read stats for PlayerName: wars=15, raids=8, killed=42, amount=5000
```

---

## Verifying Data is Loading Correctly

### Test 1: Check Player Login Logs

When a player with stats logs in, you should see:

```log
[INFO] Player 550e8400-... logged in, ensuring war data is loaded
[INFO] War stats on login - PlayersKilled: 42, RaidedColonies: 8, AmountRaided: 5000, WarsWon: 15, WarStalemates: 2
```

**If you see:**
```log
[INFO] No war data found for player 550e8400-... on login
```
Then the player has never earned any stats (all zeros is correct).

### Test 2: Check API Reads Data

Enable debug logging and watch API requests:

```log
[DEBUG] Read stats for PlayerName: wars=15, raids=8, killed=42, amount=5000
```

**Warning Signs:**
```log
[WARN] Capability not loaded for player PlayerName
[WARN] Capability not attached for player PlayerName
```
These indicate the capability system isn't working properly.

### Test 3: Verify Stat Increments

When a player earns stats (wins war, raids colony, kills player):

```log
[INFO] 📊 STAT UPDATE: PlayerName warsWon: 14 -> 15
[DEBUG] Saving war stats - PlayersKilled: 42, RaidedColonies: 8, AmountRaided: 5000, WarsWon: 15, WarStalemates: 2
[DEBUG] PlayerWarData successfully saved to persistent storage for player 550e8400-...
```

### Test 4: Check Persistent Storage

After stats are earned, check the player's .dat file:

**Location:** `world/playerdata/<uuid>.dat`

Extract with NBT editor or check logs:
```
ForgeData:
  minecolonytax_war_data:
    playersKilledInWar: 42
    raidedColonies: 8
    amountRaided: 5000L
    warsWon: 15
    warStalemates: 2
```

---

## Testing the API Data Loading

### Manual Test: Add Stats and Verify

1. **Give a player some stats** (via raid/war)
2. **Check player logout** - data should save:
   ```log
   [INFO] War stats on logout - PlayersKilled: 1, RaidedColonies: 1, AmountRaided: 500, ...
   [INFO] War data saved on player logout: {playersKilledInWar:1,...}
   ```

3. **Player logs back in** - data should load:
   ```log
   [INFO] War stats on login - PlayersKilled: 1, RaidedColonies: 1, AmountRaided: 500, ...
   ```

4. **Call API** - should return the stats:
   ```bash
   curl -H "X-API-Key: test" http://localhost:8090/api/warstats/server
   ```
   
   Expected response:
   ```json
   {
     "totals": {
       "warsWon": 0,
       "coloniesRaided": 1,
       "playersKilled": 1,
       "amountRaided": 500
     }
   }
   ```

---

## Common Issues & Solutions

### Issue 1: All Stats Show Zero

**Symptoms:**
- API returns all zeros
- Logs show: `[WARN] Capability not loaded for player PlayerName`

**Causes:**
1. Capability not registered (check for registration log at startup)
2. Capability not attached to players
3. Data not loading from persistent storage

**Solution:**
```bash
# Check if capability is registered at startup
grep "REGISTERING PlayerWarData" logs/latest.log

# Check if capabilities are being attached
grep "Attaching PlayerWarData capability" logs/latest.log

# Check if data is loading on login
grep "War stats on login" logs/latest.log
```

### Issue 2: Stats Don't Persist After Logout

**Symptoms:**
- Stats show correctly while online
- After logout/login, stats reset to zero

**Causes:**
1. Data not saving to persistent storage
2. Save events not firing
3. NBT serialization issues

**Solution:**
```bash
# Check if data is being saved
grep "War data saved on player logout" logs/latest.log
grep "PlayerWarData successfully saved" logs/latest.log

# Verify player .dat file contains the data
# Use NBT editor on world/playerdata/<uuid>.dat
```

### Issue 3: Capability Returns Null

**Symptoms:**
- Logs show: `[WARN] Capability not attached for player PlayerName`
- API returns no data for online players

**Causes:**
1. Capability registration failed
2. Event handler not subscribing correctly
3. Mod initialization order issue

**Solution:**
1. Check mod event bus registration:
   ```java
   @Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
   ```

2. Verify forge event bus registration:
   ```java
   @Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID)
   ```

3. Restart server completely (not just `/reload`)

---

## Debug Checklist

When troubleshooting API data loading:

- [ ] Capability registered at startup
- [ ] Capabilities attaching to players on join
- [ ] Data loading from persistent storage on login
- [ ] Stats incrementing when earned
- [ ] Data saving on logout
- [ ] Player .dat files contain war data
- [ ] API debug logs showing real stats
- [ ] No "Capability not loaded" warnings in logs

---

## Expected Log Flow (Happy Path)

### Server Start
```
[INFO] REGISTERING PlayerWarData capability on MOD event bus
[INFO] PlayerWarData capability registration complete
[INFO] Web API Server Started Successfully!
```

### Player Joins
```
[DEBUG] Attaching PlayerWarData capability to player entity
[INFO] Player 550e8400-... logged in, ensuring war data is loaded
[INFO] War stats on login - PlayersKilled: 42, RaidedColonies: 8, AmountRaided: 5000, WarsWon: 15, WarStalemates: 2
```

### Player Earns Stats
```
[INFO] 📊 STAT UPDATE: PlayerName raidedColonies: 8 -> 9
[INFO] 📊 STAT UPDATE: PlayerName amountRaided: 5000 -> 5500
```

### API Called
```
[DEBUG] Read stats for PlayerName: wars=15, raids=9, killed=42, amount=5500
```

### Player Leaves
```
[INFO] War stats on logout - PlayersKilled: 42, RaidedColonies: 9, AmountRaided: 5500, WarsWon: 15, WarStalemates: 2
[INFO] War data saved on player logout: {playersKilledInWar:42,raidedColonies:9,...}
[DEBUG] PlayerWarData successfully saved to persistent storage for player 550e8400-...
```

---

## Performance Considerations

### Why Not Use getOrCreate() Everywhere?

**For READING data:** ❌ Bad
- Creates fake empty instances
- Hides missing data
- Returns wrong information

**For WRITING data:** ✅ Good
- Ensures capability exists
- Safe to increment stats
- Proper for modification

### Current Implementation

**Reading (API):**
```java
var capability = player.getCapability(CAPABILITY);
PlayerWarData data = capability.resolve().orElse(null);
if (data != null) {
    // Use real data
}
```

**Writing (Stats Manager):**
```java
PlayerWarDataCapability.get(player).ifPresent(data -> {
    data.incrementWarsWon();
});
```

---

## Final Verification Commands

### Check Capability System
```bash
# At server startup
grep -A2 "REGISTERING PlayerWarData" logs/latest.log

# When player joins
grep "War stats on login" logs/latest.log | tail -n 10

# When API is called
grep "Read stats for" logs/latest.log | tail -n 10
```

### Test API Response
```bash
# Get server stats
curl -H "X-API-Key: your-key" http://localhost:8090/api/warstats/server | jq .

# Get leaderboard
curl -H "X-API-Key: your-key" \
  "http://localhost:8090/api/warstats/leaderboard?sort=warsWon&limit=10" | jq .

# Get specific player
curl -H "X-API-Key: your-key" \
  "http://localhost:8090/api/warstats/player/<uuid>" | jq .
```

### Check Player Data File
```bash
# On Linux/Mac
nbtdump world/playerdata/<uuid>.dat | grep -A10 minecolonytax_war_data

# Or use NBTExplorer GUI tool
```

---

## Summary

**Key Changes Made:**
1. ✅ Replaced `getOrCreate()` with `capability.resolve().orElse(null)` in API
2. ✅ Added null checks and warning logs
3. ✅ Added debug logging to track data reads
4. ✅ Prevents returning fake empty stats

**Now the API:**
- Returns real data or nothing
- Logs warnings when data unavailable
- Doesn't create temporary empty instances
- Properly reflects player statistics

**Test after restart and verify logs show real stats loading!**
