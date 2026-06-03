# Defensive Audit 03 — Espionage / Spy System

Scope: every file under `src/main/java/net/machiavelli/minecolonytax/espionage/**`, the five spy network packets, `SpyDialogScreen` + `SpyMissionData`, and the JourneyMap compat shim. Static analysis only — no build, no edits.

## Summary

The spy subsystem is comprehensive, but several state-machine paths leak resources, allow players to bypass guards, or silently lose missions:

- DeploySpyPacket effectively limits deployment to colony OWNERS only (despite the `isColonyManager` check) because the upstream resolver is `getIColonyByOwner` — non-owner officers cannot deploy spies even though the chat error message implies they can.
- `PENDING_COSTS` is never refunded on RECALL or auto-cancellation paths, so a player who deploys then recalls a spy still loses the tax cost on the next cycle for no benefit.
- `SpyManager.loadData()` early-returns without clearing the static maps when the JSON file is missing, leaving stale missions in memory across server reloads in single-player.
- A spy entity in the world after `onMissionSuccess` only checks `isMissionActive` every 6000 ticks (5 min) and only if `tickCount > 100` — orphaned spies can persist for up to 5 minutes after their mission completes (and forever if chunks unload between despawn attempts).
- `SpyEntity.hurt()` makes the entity *immortal to all players and guards* — they can NEVER be killed by direct combat, only by flee-timeout, fall/lava/drowning, or admin `/kill`. This is intentional per comments but worth flagging.
- JourneyMap waypoint colours are 24-bit RGB (no alpha). Several JM API versions require 0xAARRGGBB; on some setups waypoints render fully transparent.
- The "auto-cancel when target colony deleted" branch (line 821) removes the mission silently without prune of `PENDING_COSTS`, no `saveData()`, no `pushSpyDataToPlayer`, and no entity-despawn confirmation (the despawn helper only despawns if `level.getEntity(spyUuid) instanceof SpyEntity`).

No `at-war` restriction exists in `DeploySpyPacket` or `SpyManager.deploySpyMission`. Per the task description, this may or may not be intentional, but it is flagged Medium.

---

## Critical

### C1. Officers cannot deploy spies — only owners. UI claims otherwise.
**File:** `src/main/java/net/machiavelli/minecolonytax/network/packets/DeploySpyPacket.java:43-52`

```java
IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getIColonyByOwner(player.level(), player);
if (colony == null) return;
if (!colony.getPermissions().getRank(player.getUUID()).isColonyManager()) {
    player.sendSystemMessage(Component.literal("Only officers can deploy spies.")...);
    return;
}
```

`getIColonyByOwner` (defined in `minecolonies/.../ColonyManager.java:511-520`) only returns the colony where the player is the **registered owner**. A player who is an OFFICER (manager-rank) of a colony they don't own will get `colony == null` from this lookup and the packet handler returns silently — no error message, no UI feedback. The `isColonyManager` rank check is essentially dead code: by the time it runs, the player is already the owner.

Impact: officer/co-leader gameplay is broken. The "Only officers can deploy spies" message will never be sent because the silent `return` at line 46 fires first for non-owners.

Fix direction: use a lookup that respects manager rank, e.g. iterate `getAllColonies()` and find one where this player is owner or manager, or accept the attacker colony ID from the packet payload and validate the player has manager rank on that specific colony.

---

### C2. `PENDING_COSTS` is never refunded on recall, auto-cancel, or mission-failure
**Files:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:286-287` (charge), `:309-348` (recall — no refund), `:817-826` (auto-cancel — no refund), `:441-475` (onSpyKilled — no refund)

```java
// deploy charges:
int currentPending = PENDING_COSTS.getOrDefault(attackerColonyId, 0);
PENDING_COSTS.put(attackerColonyId, currentPending + cost);
```

`PENDING_COSTS` is consumed only in `TaxManager.java:564-568` during the tax cycle, deducted from the colony's tax balance. Every "mission ends without reward" path (DEPLOYING-RECALL at line 322, target-colony-deleted at line 821, ACTIVE-RECALL also keeps charge) leaves the pending cost on the colony, so the player pays full price for a spy that never collected anything.

This is exploitable for griefing: an attacker repeatedly deploys+recalls high-tier missions on a co-owner's colony, draining their treasury via the shared `PENDING_COSTS` map (since the cost is attached to `attackerColonyId`, not the player).

---

### C3. SpyManager static state survives world unload when save file is absent
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:76-82`

```java
private static void loadData() {
    File file = new File(STORAGE_FILE);
    if (!file.exists()) {
        if (TaxConfig.isNormalLogging()) LOGGER.info("No espionage data file found, starting fresh");
        return;          // <-- never clears maps
    }
    ...
}
```

The `ACTIVE_MISSIONS`, `COMPLETED_MISSIONS`, `COOLDOWNS`, `PENDING_COSTS`, `SABOTAGE_EFFECTS`, `BRIBED_GUARDS`, and `STOLEN_SECRETS_BUFF` static maps are only cleared inside the `loaded != null` branch. If the player leaves a single-player world and creates/loads a *different* world without the save file (or deletes `config/warntax/espionage.json` manually, or if the file is corrupt and Gson throws), every static map keeps its previous contents.

In multiplayer this is masked by JVM lifecycle, but in single-player every save-and-quit-to-menu → load-different-world cycle inherits the previous world's spy state.

Fix direction: clear all maps unconditionally at the top of `loadData()` (or in `initialize()` before `loadData()`).

---

## High

### H1. `SpyManager.shutdown()` writes asynchronously without awaiting completion
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:72-74, 121-142`

```java
public static void shutdown() {
    saveData();
}

private static void saveData() {
    ...
    net.machiavelli.minecolonytax.util.AsyncSaveExecutor.submit("espionage", () -> {
        ...
        try (FileWriter writer = new FileWriter(file)) { GSON.toJson(data, writer); }
        ...
    });
}
```

`saveData` submits to an async executor and returns immediately. If the JVM exits before the executor drains, the latest spy state (including the just-saved-after-tick deltas) is lost. The snapshot pattern protects against concurrent mutation but not against the executor being killed mid-write. Worth confirming `AsyncSaveExecutor` is flushed on `ServerStoppingEvent`.

### H2. Orphaned spy entities live for up to 5 minutes after their mission completes
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntity.java:120-127`

```java
private void tickInfiltrating() {
    if (this.tickCount > 100 && !missionId.isEmpty() && this.tickCount % 6000 == 0) {
        if (!SpyManager.isMissionActive(missionId)) {
            this.discard();
            return;
        }
    }
    ...
}
```

`despawnSpyEntity` (`SpyManager.java:1066-1080`) only despawns when the entity is loaded; it iterates `SERVER.getAllLevels()` and calls `level.getEntity(spyUuid)`, which returns `null` for entities in unloaded chunks. If the target colony chunk is unloaded (no players nearby), the entity never receives the despawn — and when the chunk reloads, the mission ID no longer matches anything in `ACTIVE_MISSIONS`, so the entity stays for up to 6000 ticks (5 min) before the self-check fires. After that the entity uses `discard()` without notifying anything; harmless but messy.

Also, the entity will only self-discard while `INFILTRATING`. If a stale entity wakes up in `FLEEING` state (e.g. after a restart while fleeing, mission already pruned), `tickFleeing()` has no equivalent self-check — the entity flees indefinitely until `FleeMaxSeconds` triggers a (now meaningless) `SpyManager.onSpyKilled(missionId)` for a mission that no longer exists.

Fix direction: add the same `isMissionActive` self-check to `tickFleeing()`.

### H3. `SpyEntity.hurt()` returns `false` for all player/guard damage — entity is immortal in combat
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntity.java:208-216`

```java
public boolean hurt(DamageSource source, float amount) {
    if (level().isClientSide) return false;
    if (isPlayerOrGuardDamage(source)) {
        if (currentState != SpyState.FLEEING) enterFleeState();
        return false;          // never reduces HP, no knockback, no damage tick
    }
    return super.hurt(source, amount);
}
```

By design (comment says "flee timeout is the only death path"), but consequences:
- Players hitting the spy get no hit-confirmation: no damage tint, no knockback, no death drop. UX feels broken — "why is my axe doing nothing?".
- A defender cannot kill a spy that pathfinds into a wall corner during flee — they must wait `FleeMaxSeconds` (default 30s).
- Mob-platform damage (lava trap, drowning) DOES kill normally (falls through to `super.hurt`), so the spy's combat invulnerability is inconsistent with environmental death.

### H4. JourneyMap waypoint colours missing alpha channel
**File:** `src/main/java/net/machiavelli/minecolonytax/compat/JmImpl.java:129-137`

```java
private static int colorForStatus(String status) {
    return switch (status) {
        case "DEPLOYING" -> 0xFFAA00;
        case "ACTIVE"    -> 0x55FF55;
        case "FLEEING"   -> 0xFF5555;
        case "RETURNING" -> 0x55FFFF;
        default          -> 0xFFFFFF;
    };
}
```

JourneyMap's `Waypoint.setColor(int)` historically expects 24-bit RGB but on some builds interprets the int as `0xAARRGGBB`. With alpha=0 the icon renders invisible. Other code in the project uses `0xFFAA00` etc. for chat colours (which are 24-bit by convention), so the author may have copy-pasted. Worth double-checking against the JM API javadoc for the exact target version.

### H5. `onSpyEscaped` uses target colony name for "spyColonyName" in attacker log
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:419, 426-429`

```java
String spyColonyName = getColonyName(mission.getTargetColonyId());
...
net.machiavelli.minecolonytax.data.HistoryManager.logWithBalance(mission.getAttackerColonyId(), "SPY",
        String.format("%s spy escaped from %s with tier %d intel",
                mission.getMissionType(), spyColonyName, tier),
        _atkEscBal, _atkEscBal);
```

`spyColonyName` is `colonyName` (the target). The variable is misnamed; the log text is correct ("escaped from <target>"). Not a bug per se, but the redundant variable `colonyName` already exists at line 377 with the same value. Cosmetic.

---

## Medium

### M1. No "at-war" gating on spy deployment
**File:** `src/main/java/net/machiavelli/minecolonytax/network/packets/DeploySpyPacket.java` and `SpyManager.deploySpyMission`

Per the audit prompt: "DeploySpyPacket validates: ... not at war restriction?" There is no check anywhere in the spy deploy chain that requires the attacker and target colonies to be at war (or NOT at war). This may be intentional design — `SpyManager.java:616` reads `WarSystem.isColonyInWar(colony.getID())` as part of intel rather than as a gate. Worth confirming with design intent.

### M2. Mission-ID collision risk on race between two simultaneous packets
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:213`

```java
String missionId = UUID.randomUUID().toString();
```

Random UUIDs are safe in isolation, but the `alreadyTargeting` check at line 175-183 runs without a per-player lock. Two `DeploySpyPacket` invocations from the same player landing in the same tick — both enqueue on the main thread but a future change to defer work could break this — would each see `activeCount < maxSpies` and `alreadyTargeting == false`, then both succeed. Currently safe because `enqueueWork` serialises onto the main thread; but the comment in `deploySpyMission` does not state this assumption.

### M3. `getActiveMissionsForPlayer` uses `.toList()` but iteration is happening over a `ConcurrentHashMap`
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:993-997`

```java
public static List<SpyMission> getActiveMissionsForPlayer(String playerId) {
    return ACTIVE_MISSIONS.values().stream()
            .filter(m -> m.getAttackerPlayerId().equals(playerId))
            .toList();
}
```

`ConcurrentHashMap.values()` is weakly consistent — iteration during a concurrent put/remove is safe but may include the modified entry or not. Combined with `tick()` running on main thread and packets being `consumerMainThread`, this is currently safe. Just calling it out because a `ConcurrentHashMap` here is theatre — every access happens on the main server thread (`enqueueWork`, `TaxManager.TickEventHandler`), so a plain `HashMap` would do.

### M4. `progressIntel` re-creates intel data when `SCOUT` mission has it stripped
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:485-492`

```java
SpyIntelData intel = mission.getMissionIntel();
if (intel == null) {
    intel = new SpyIntelData();
    mission.setMissionIntel(intel);
}
```

This silently restores intel for KILLED missions if `progressIntel` is called after `onSpyKilled` set intel to `null` (line 447). However the only call site that survives mission removal is the entity's `tickInfiltrating()`. By the time `onSpyKilled` removes the mission from `ACTIVE_MISSIONS`, `progressIntel` early-returns at line 486 (`mission == null`), so this is not a live exploit. But the `if (intel == null) new SpyIntelData()` reset is brittle if anyone calls `progressIntel` from a new code path on a "killed-but-still-in-map" mission.

### M5. `SpyDataResponsePacket` 32 767 byte length cap is small for full tier-3 reports
**File:** `src/main/java/net/machiavelli/minecolonytax/network/packets/SpyDataResponsePacket.java:68, 72`

```java
this.jsonPayload = buf.readUtf(32767);
buf.writeUtf(jsonPayload, 32767);
```

A player with many missions (default cap 5, plus completed-1h-TTL retention) could exceed 32 KB JSON, especially with 20 colony members per intel × multiple missions. Hard cap will throw and disconnect the player. The `SpyMissionData` constructor at line 24-44 takes 16 fields including `SpyIntelData` with nested lists — easy to push past 32 KB with 5 active + 10 completed missions each carrying tier-3 intel.

### M6. Static `latestSpyMissions` accessor — stale data after screen close
**File:** `src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java:66, 87-92, 139`

`latestSpyMissions` is a `volatile static` field that lives forever. After the player closes the `TaxManagementScreen`, the cache remains populated, and `SpyJourneyMapPlugin.syncWaypoints` is only called from the packet handler (line 83 of `SpyDataResponsePacket`). If the player never opens the screen, no `RequestSpyDataPacket` is sent, but server-pushed `pushSpyDataToPlayer` notifications still arrive. Race: the GUI screen reads `latestSpyMissions` in its render loop after a server push — fine because the field is volatile; but `updateSpyData` (line 136) also writes both `this.spyMissions` and `latestSpyMissions`, so the static is updated twice. Cosmetic.

### M7. Map generator uses `centerX / scaleFactor` with negative coordinates
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyMapGenerator.java:43-44`

```java
int mapStartX = (centerX / scaleFactor - 64) * scaleFactor;
int mapStartZ = (centerZ / scaleFactor - 64) * scaleFactor;
```

Integer division in Java truncates toward zero, so for negative `centerX` (colonies in the western world), `mapStartX` is offset by one `scaleFactor` block from vanilla's `Math.floorDiv` behaviour. The visible result is the map being shifted half a cell relative to where the colony actually sits when the colony is at negative X/Z. Vanilla uses `Math.floorDiv` here; replacing the operator would match vanilla rendering.

### M8. `OfficerColonyVisitTracker.isOwnerOrOfficer` only checks the target colony
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:239-243`

```java
if (net.machiavelli.minecolonytax.event.OfficerColonyVisitTracker.isOwnerOrOfficer(targetCheck, player.getUUID())) {
    player.sendSystemMessage(Component.literal("You cannot spy on a colony you are an officer of.")...);
    return;
}
```

Correct check but only the obvious case — a player who is officer of colony A spying on B is fine even if they're owner of a third colony C. The duplicate-mission check at line 175 uses `attackerPlayerId.equals(playerId)`, so a player can stack up to `SpyMaxActivePerPlayer` missions split across multiple of their own colonies. Likely intended; flagged for design review.

---

## Low

### L1. `SpyEntity.die()` calls `onSpyKilled` after `discard()` paths already did
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntity.java:356-364`

The `if (SpyManager.isMissionActive(missionId))` guard at line 360 is the only protection against double-firing. The discard paths (lines 124, 164, 352) all bypass `die()` — `discard()` does not call `die()` — so this works. But if a future change makes any path call `kill()` or `hurt(damageSource, Float.MAX_VALUE)` instead of `discard()`, both paths could fire. The guard relies on map ordering: `onSpyKilled` removes from `ACTIVE_MISSIONS` before `die` can re-enter. Safe today, brittle for the future.

### L2. `SpyClientHandler.openSpyDialog` no-op on non-client
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyClientHandler.java` and `SpyEntity.java:367-373`

```java
public InteractionResult mobInteract(Player player, InteractionHand hand) {
    if (this.level().isClientSide) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SpyClientHandler.openSpyDialog());
        return InteractionResult.sidedSuccess(true);
    }
    return InteractionResult.sidedSuccess(false);
}
```

`SpyClientHandler` is `@OnlyIn(Dist.CLIENT)`. The `DistExecutor.unsafeRunWhenOn` wrap is correct for class-loading safety, but server-side returns `sidedSuccess(false)` which is the convention for "didn't actually do anything but stop further processing". Note the dialog is purely flavor — no server-side action is triggered by right-click. A player on a dedicated server could *only* right-click for the dialog; they cannot interact in any meaningful way.

### L3. Hard-coded constants ignored by config
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntity.java:42, 58, 67-71`

```java
private static final int ESCAPE_CONFIRM_TICKS = 60;
private static final int INTEL_CHECK_INTERVAL = 1200;
...
return Mob.createMobAttributes()
        .add(Attributes.MAX_HEALTH, 20.0D)
        .add(Attributes.MOVEMENT_SPEED, 0.25D);
```

Hard-coded values that other parts of the system would benefit from tuning (e.g. spy health, base movement speed, escape-confirm window). All other spy magic numbers are in `TaxConfig`; these aren't.

### L4. `STOLEN_SECRETS_BUFF` cleanup duplicated and inconsistent
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:932-945`

```java
public static boolean hasActiveStolenSecretsBuff(int attackerColonyId) {
    Long expiration = STOLEN_SECRETS_BUFF.get(attackerColonyId);
    if (expiration == null) return false;
    if (System.currentTimeMillis() > expiration) {
        STOLEN_SECRETS_BUFF.remove(attackerColonyId);
        return false;
    }
    return true;
}

private static void clearExpiredStolenSecrets() {
    long now = System.currentTimeMillis();
    STOLEN_SECRETS_BUFF.entrySet().removeIf(e -> now > e.getValue());
}
```

Two cleanup paths, neither calls `saveData()`. Stale entries can persist in the JSON until something else writes the file. Inconsistent with `consumeBribedGuards` (line 920) which does call `saveData()`.

### L5. `SpyIntelBookGenerator.createIntelReport` silently returns null for KILLED status, no caller handles `null` consistently
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyIntelBookGenerator.java:20-22` and `SpyManager.java:557-563`

```java
ItemStack book = SpyIntelBookGenerator.createIntelReport(mission, colonyName);
if (book != null) {
    if (!player.getInventory().add(book)) {
        player.drop(book, false);
    }
}
```

OK in `giveIntelBook`, but `KILLED` missions also never reach `giveIntelBook` (not added to `COMPLETED_MISSIONS`). The defensive `null` return is dead code with current callers; remove or document.

### L6. `SpyMission.SpyMission()` no-args constructor used only by Gson
The no-args constructor at line 26 of `SpyMission.java` leaves every primitive/String at default (`status=null`, etc.). If a malformed JSON enters `SpySaveData`, the resulting mission with `status=null` will pass through `tick()` (`!"ACTIVE".equals(null)` is `true`, but `"DEPLOYING".equals(null)` is `false`, etc.) and become permanently stuck — never auto-completes, never times out, blocks the player's `maxSpies` slot. No defensive validation after load.

### L7. `SpyMapGenerator` does not check `mapData.colors` length
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyMapGenerator.java:60`

```java
mapData.colors[px + pz * 128] = (byte) (mapColor.id * 4 + shade);
```

Assumes the array is 128*128. Always true for vanilla, but if Forge or a mod ever changes the map size, this throws. Cheap to guard.

### L8. `getCardinalDirection` returns wrong cardinals
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyIntelBookGenerator.java:306-317`

```java
double angle = Math.toDegrees(Math.atan2(dz, dx));
...
if (angle < 22.5 || angle >= 337.5) return "(E)";
if (angle < 67.5)  return "(SE)";
if (angle < 112.5) return "(S)";
...
```

`atan2(dz, dx)` where +X is east and +Z is south matches Minecraft's coordinate convention, so 0° == east is correct. Looks right on second pass — withdrawn as a finding. Leaving the note for future readers.

### L9. `EntityType.Builder` for SPY uses defaults for tracking
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/ModEntities.java:19-22`

No `.clientTrackingRange()` or `.updateInterval()` specified. Defaults are 5 chunks / every 3 ticks for `CREATURE`. Acceptable but worth a comment, since a spy entity is unique (one-of, mission-critical) and players may want to follow it from farther away.

### L10. `notifyColonyOfficers` deduplicates via a `Set<UUID>` but iterates with `getPlayersByRank(getRankOfficer())` only
**File:** `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java:1094-1113`

Player notifications go to owner + officers. Friends/managers above the configured "officer" rank but with a different rank ID don't receive alerts. Documenting the intent in a comment would help.

### L11. `SpyMission` `status` is `String` not enum
Fragile typo surface; "DEPLOYING" vs "DEPLOY" vs "Deploying" all silently fall through. Refactoring to an enum would catch this at compile time but is a large change.

### L12. `SpyMission.cost` is `int` but tax balances are computed in many places as `int` and may overflow
Not specific to spy; cost is small but `STOLEN_SECRETS_BUFF` expiration uses `long` while tax math uses `int`. Pre-existing project-wide concern.

---

## Files Audited

- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyManager.java` (1121 lines)
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntity.java` (429 lines)
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyFleeGoal.java` (63 lines)
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyIntelData.java` (211 lines)
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyMission.java` (118 lines)
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyEntityRenderer.java` (21 lines)
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyIntelBookGenerator.java` (318 lines)
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyMapGenerator.java` (109 lines)
- `src/main/java/net/machiavelli/minecolonytax/espionage/SpyClientHandler.java` (15 lines)
- `src/main/java/net/machiavelli/minecolonytax/espionage/ModEntities.java` (42 lines)
- `src/main/java/net/machiavelli/minecolonytax/network/packets/DeploySpyPacket.java` (64 lines)
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RecallSpyPacket.java` (49 lines)
- `src/main/java/net/machiavelli/minecolonytax/network/packets/RequestSpyDataPacket.java` (39 lines)
- `src/main/java/net/machiavelli/minecolonytax/network/packets/DismissSpyMissionPacket.java` (48 lines)
- `src/main/java/net/machiavelli/minecolonytax/network/packets/SpyDataResponsePacket.java` (92 lines)
- `src/main/java/net/machiavelli/minecolonytax/gui/SpyDialogScreen.java` (76 lines)
- `src/main/java/net/machiavelli/minecolonytax/gui/data/SpyMissionData.java` (100 lines)
- `src/main/java/net/machiavelli/minecolonytax/compat/SpyJourneyMapPlugin.java` (41 lines)
- `src/main/java/net/machiavelli/minecolonytax/compat/JmImpl.java` (138 lines)

Cross-referenced (read-only, not in scope):
- `src/main/java/net/machiavelli/minecolonytax/network/NetworkHandler.java:140-167` — packet registration
- `src/main/java/net/machiavelli/minecolonytax/gui/TaxManagementScreen.java:66-92, 136-140` — static cache
- `src/main/java/net/machiavelli/minecolonytax/TaxManager.java:126-132, 562-571` — tick wiring + cost deduction
- `src/main/java/net/machiavelli/minecolonytax/event/OfficerColonyVisitTracker.java:258-274` — officer check helper
- `src/main/java/net/machiavelli/minecolonytax/MineColonyTax.java:52-53, 190-198, 361` — entity register + init/shutdown
- `minecolonies/src/main/java/com/minecolonies/core/colony/ColonyManager.java:509-521` — `getIColonyByOwner` semantics
