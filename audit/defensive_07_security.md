# Defensive Audit 07 — Permissions, Commands, Networking (SECURITY)

Static analysis only. No code modified.
Scope: `permissions/TaxPermissionManager.java`, `commands/**`, `network/**`, `network/packets/**`.

---

## Summary

War-N-Taxes uses Forge `SimpleChannel` with `consumerMainThread` (good — packets execute on server thread, no race conditions and no manual `enqueueWork` needed). However the layer has serious gaps:

- `UpdatePlayerTaxPermissionPacket` lets ANY client grant/revoke tax-claim permissions to ANY UUID in ANY colony — no authorization check.
- `TaxPermissionManager` state is entirely in-memory; nothing reloads it on server start (so officer-toggle state silently resets every restart while individual overrides are persisted only via the GUI snapshot — see `PermissionSnapshot`).
- `EntityGlowPacket` is registered with NO `NetworkDirection`, so a malicious client can send it to the server; the handler only runs the client branch, but ANY direction acceptance creates an inbound packet path with an unbounded `readVarInt` for duration.
- `BuyInvestmentPacket` accepts any officer (not owner) — bypassing the parallel `/wnt invest buy` command which restricts to colony owner.
- `payTaxDebt(...)` silently accepts payment with **no debt check inside TaxManager** — the colony balance is incremented by the full requested amount regardless of whether there is debt. The packet checks pre-emptively but `executeTaxDebt` (slash command) and `TaxDebtCommand.execute(...)` do NOT check that there is actually any debt before deducting from the player and giving the colony money. (Effectively a free deposit into someone else's colony — but only if you're already an officer.)
- `ClaimVassalTributePacket.java` exists as a dead packet (NOT registered in `NetworkHandler`) with NO permission check on the handler. Same for `PayDebtPacket.java`. Both are reachable only if a future change registers them — but if they do, they are exploitable. Recommend deletion.
- Multiple commands top-level: `wagewar`, `raid`, `joinwar`, `leavewar`, `warinfo`, `suepeace`, `choosewarside`, `taxgui`, `mct`, `claimtax`, `taxdebt`, `warhistory`, `traderoute` and `peace` are duplicated by `WarCommands.register` outside the `/wnt` namespace with no namespace guard — colliding with anything another mod might register and exposing flat-named top-level commands accessible to all players.
- Sender's `getRank(...)` is dereferenced without null checks in several handlers (DeploySpyPacket, TreasuryActionPacket, BuyInvestmentPacket, RequestTreasuryDataPacket, RequestInvestmentDataPacket) → NPE if a non-member sends the packet. Crash-grade DoS on the server thread.

---

## Critical

### C-1 — UpdatePlayerTaxPermissionPacket: NO AUTHORIZATION
- **File:** `src/main/java/net/machiavelli/minecolonytax/network/packets/UpdatePlayerTaxPermissionPacket.java:38-60`
- **Severity:** CRITICAL
- **Threat model:** Any connected player can craft this packet (client→server) to grant or revoke tax-claim permission for any UUID in any colony.
- **Reproduction:**
  1. Player joins server with modded client.
  2. Sends `UpdatePlayerTaxPermissionPacket(victimColonyId, victimOfficerUUID, false)`.
  3. Server calls `TaxPermissionManager.setPlayerClaimPermission(...)` with NO check that the sender owns the colony, is an officer, or has any relationship.
- **Impact:** Attacker can lock legitimate officers out of tax claiming, or (in concert with becoming an officer of another colony) grant themselves tax-claim rights they should not have. Combined with the bug at C-2 / H-1, this is a near-complete bypass of the WnT permission layer.
- **Note:** `UpdateTaxPermissionPacket` (the colony-wide toggle) DOES check `MANAGE_HUTS` (line 41); the per-player variant simply forgot.

### C-2 — EntityGlowPacket: no `NetworkDirection`, unbounded duration
- **File:** `src/main/java/net/machiavelli/minecolonytax/network/NetworkHandler.java:48-54` (registration) and `network/EntityGlowPacket.java:25-39`
- **Severity:** CRITICAL (DoS), MEDIUM (logic)
- **Threat model:** The packet is registered via `registerMessage(int, Class, encoder, decoder, handler)` with no `NetworkDirection` argument. Forge accepts it in BOTH directions. The decoder is `buf.readVarInt()` for duration with no upper bound and no sender side check.
- **Reproduction:**
  1. Malicious client sends `EntityGlowPacket(anyEntityId, true, Integer.MAX_VALUE)` to the server.
  2. The handler checks `ctx.getDirection().getReceptionSide().isClient()` and short-circuits the body — so on the server side the body is skipped, but the packet was still accepted, decoded, and dispatched on the netty thread (the `enqueueWork` call is guarded by the client-side check; on the server the only work is `setPacketHandled(true)`).
  3. The server still pays for decode + dispatch on every spammed packet — useful for cheap network DoS, plus future code that drops the direction check would suddenly mutate server entity state.
- **Impact:** Direct: low-grade DoS and a footgun for future maintainers. Indirect: if the direction check is ever removed during refactor, ANY client can force ANY entity (mob, player, item frame) to glow forever.
- **Fix shape:** Register with explicit `NetworkDirection.PLAY_TO_CLIENT`, bound `durationTicks` to a sane max (e.g. 20*60*60).

### C-3 — BuyInvestmentPacket: officer can purchase, command requires owner — inconsistent and exploitable
- **File:** `src/main/java/net/machiavelli/minecolonytax/network/packets/BuyInvestmentPacket.java:48`
- **Severity:** CRITICAL (economy bypass)
- **Threat model:** The packet handler accepts any officer (`isColonyManager()`), but the slash command `WntCommands.handleInvestBuy` requires the colony **owner** (`getOwner().equals(player.getUUID())` — line 3833). An officer can spend the colony's treasury via the GUI even if the owner did not intend to grant that capability.
- **Impact:** An untrusted officer can drain the treasury by purchasing upgrades the owner did not approve. There is also no rate-limit — repeated clicks can in principle race the level check (though `ColonyUpgradeManager.purchase` is presumably idempotent server-side).

### C-4 — Sender rank dereferenced without null check (NPE / DoS)
- **Files:**
  - `network/packets/DeploySpyPacket.java:48` — `colony.getPermissions().getRank(player.getUUID()).isColonyManager()`
  - `network/packets/TreasuryActionPacket.java:64` — same pattern
  - `network/packets/BuyInvestmentPacket.java:48` — same pattern
  - `network/packets/RequestTreasuryDataPacket.java:50` — same pattern
  - `network/packets/RequestInvestmentDataPacket.java:43` — same pattern
- **Severity:** CRITICAL (server crash on main thread)
- **Threat model:** If the sender is not in the colony's permissions list, MineColonies may return `null` from `getRank`. The unchecked `.isColonyManager()` will throw NPE on the **main server thread** (because of `consumerMainThread`). Forge will log the exception and may close the connection or — worse — propagate the NPE up the tick handler stack.
- **Reproduction:** Send a `DeploySpyPacket` while standing outside any colony or as a player who has never interacted with the resolved attacker colony (note: `getIColonyByOwner` resolves the attacker colony but the rank lookup is then performed on the same colony where the sender's UUID must already be the owner — usually safe — but the same pattern in `TreasuryActionPacket` and the request packets uses `getColonyByWorld` with a CLIENT-CONTROLLED `colonyId`, where the player may not be present).
- **Impact:** Any client can crash the server thread by sending `TreasuryActionPacket(colonyIdTheyDoNotBelongTo, DEPOSIT, 0, ...)`. Even a single NPE per second is significant tick-time pressure.

### C-5 — `WarCommands.register` registers root-level commands without permission gates
- **File:** `src/main/java/net/machiavelli/minecolonytax/commands/WarCommands.java:47-99`
- **Severity:** CRITICAL (command surface / collision)
- **Threat model:** `WarCommands.register` adds top-level `/raid`, `/wagewar`, `/suepeace`, `/joinwar`, `/leavewar`, `/war`, `/warinfo`, `/choosewarside`, `/peace`, `/wardebug` outside the `/wnt` namespace. None of them have a `.requires(...)` gate (except `wardebug`, `warstop`, `warstopall`, `raidstop`). The same commands are also present under `/wnt …`. Any player can issue them.
  - Per the project README / wiki, war declaration is a player action, so the open access may be intended.
  - However: `/raid <colony>` and `/wagewar <colony>` are reachable as bare top-level commands so other mods that register `raid` or `wagewar` will silently shadow or be shadowed. The lack of a namespace prefix is a vulnerability in itself because admins typing `/op` followed by `/raid` for a different mod will hit War-N-Taxes.
- **Impact:** Permission-bypass via command shadowing + a worse player-UX (commands are not discoverable as belonging to WnT). Also: `TaxGUICommand.register` registers `/mct` and `/taxgui` at root which any player can issue (just opens the GUI — low harm) but again no `wnt:` prefix.

---

## High

### H-1 — `TaxPermissionManager` officer-toggle state is not persisted across restarts
- **File:** `src/main/java/net/machiavelli/minecolonytax/permissions/TaxPermissionManager.java`
- **Severity:** HIGH (security regression after restart)
- **Threat model:** `OFFICER_CLAIM_PERMISSIONS` and `INDIVIDUAL_CLAIM_PERMISSIONS` are pure in-memory `ConcurrentHashMap`s. The class has `loadPermissions` / `loadIndividualPermissions` methods, but a grep across the codebase shows no auto-restore on server start (only the GUI snapshot path in `PermissionSnapshot.java` populates them at runtime when the GUI is opened). After a server restart, every colony defaults to "officers can claim" (line 47, `getOrDefault(...,true)`), even if the owner had toggled it off. Sensitive permission decisions silently revert.
- **Impact:** Owners cannot trust the toggle they set. An attacker who waits for a restart re-acquires permissions previously revoked.

### H-2 — `TaxDebtCommand` and `WntCommands.executeTaxDebt` accept payment even when no debt exists
- **File:** `commands/TaxDebtCommand.java:81-96` and `commands/WntCommands.java:2006-2041`
- **Severity:** HIGH (economy)
- **Threat model:** `TaxManager.payTaxDebt(colony, amount)` always adds `amount` to the colony balance (line 1183: `colonyTaxMap.put(colonyId, currentTax + amount)`), with NO check that the colony is actually in debt. The slash commands deduct currency from the player up-front, then call `payTaxDebt` for the full amount. Net effect: an officer can convert their personal wallet into colony tax balance arbitrarily, including overshooting any "debt repayment" amount.
  - Only the `PayTaxDebtPacket` GUI path correctly checks `currentTax < 0` before deducting. The CLI paths skip this gate.
- **Impact:** Quiet way to "donate" personal coin to a colony's tax balance, then immediately claim it back via `/wnt claimtax`. If there is any tax→coin conversion ratio mismatch (e.g., different sources in `CurrencyService.Source`), this is an exploitable money-printer.

### H-3 — `RecallSpyPacket` does not validate mission status before recall
- **File:** `network/packets/RecallSpyPacket.java:38-44`
- **Severity:** HIGH (state machine bypass)
- **Threat model:** The handler checks ownership (`ownsMission`) but not status. `SpyManager.recallSpy` itself does branch on `"DEPLOYING"`. A malicious client can spam `RecallSpyPacket` to repeatedly transition a mission, potentially racing the tick-driven status mutations. Since `consumerMainThread` serializes the work, the actual race is bounded — but the lack of a status check allows `RecallSpyPacket` to be called on a FLEEING mission, causing `despawnSpyEntity` to fire even after the entity is gone. Not a security hole per se but creates spurious history entries / log spam, possibly used as a denial-of-tracking attack.

### H-4 — Permission-gated subcommands silently allow non-OP execution via duplicated `wnt` literal
- **File:** `commands/RandomEventsCommand.java:36-66`, `commands/EntityRaidCommands.java:32-48`
- **Severity:** HIGH (Brigadier merge semantics)
- **Threat model:** Several command classes call `dispatcher.register(Commands.literal("wnt").then(...))` multiple times with overlapping subtrees. Brigadier MERGES literal nodes. The MERGED node retains the union of `.requires(...)` predicates only if both branches declare them. If one branch under `/wnt events` has `.requires(hasPermission(2))` and another branch under `/wnt events <int>` does not, the merged node's `requires` may degrade depending on registration order.
  - In `RandomEventsCommand.java`: `viewActiveEvents` is registered WITHOUT a `requires`; `triggerEvent` and `resetColonyEvents` are registered WITH `requires(hasPermission(2))`. Brigadier WILL keep each subcommand's `requires` because each `Commands.literal("triggerevent")` and `Commands.literal("reset")` is a leaf; the gating is on the leaf, not on the root. So this specific case is OK.
  - However, the **same `/wnt` literal is registered ~7 times across `WntCommands`, `RandomEventsCommand`, `EntityRaidCommands`, `GeneralPermissionsCommands`, `AbandonmentCheckCommand`, `TaxPolicyCommand`, `TaxGUICommand`, `TreasuryCommand`, `FactionCommand`, plus the duplicates inside `WntCommands` for `permissions`, `entityraid`, `events`** — relying on Brigadier's merge semantics for security. ONE refactor that moves a `.requires(hasPermission(2))` onto the wrong node will silently expose admin-only subcommands.
- **Impact:** Latent vulnerability — current state passes, but the architecture is fragile.

### H-5 — `Rank.getRank()` results trusted by ordinal in `OfficerDataResponsePacket` / `RequestOfficerDataPacket`
- **File:** `network/packets/RequestOfficerDataPacket.java:83, 92` and `OfficerDataResponsePacket.java`
- **Severity:** HIGH (info leak)
- **Threat model:** `RequestOfficerDataPacket` skips ranks where `rank.getId() <= 1`, marks `canClaimTax = rank.getId() >= 3`. MineColonies rank IDs are NOT a stable API contract — they depend on the order of rank creation in the colony. A colony with custom ranks may have id 3 mean "Visitor" not "Officer". The response also marks `canClaimTax` purely on rank id without consulting `TaxPermissionManager` — so the OFFICER list shown in the GUI may not match the actual claim authorization.
  - More importantly, the packet returns officer data for any colony the requester has `ACCESS_HUTS` (line 70), which in MineColonies is a relatively low permission. A neutral guest with `ACCESS_HUTS` can enumerate all officers' UUIDs, names, online-status and last-seen timestamps. This is a privacy / reconnaissance issue for "find-the-target" attacks.
- **Impact:** Information disclosure useful for griefing and PvP targeting.

### H-6 — `SpyDataResponsePacket` uses 32 KiB cap for JSON, parsed with Gson on client thread
- **File:** `network/packets/SpyDataResponsePacket.java:67, 79`
- **Severity:** HIGH (client-side DoS if server compromised)
- **Threat model:** Decoder is `buf.readUtf(32767)` then `GSON.fromJson(jsonPayload, listType)` runs inside `enqueueWork`. A malicious server (or a packet forwarded by a man-in-the-middle to vanilla clients) can send malformed JSON that throws inside Gson and propagates to the client tick loop, crashing the GUI screen. There is no `try/catch` around the parse.
- **Impact:** Client crash via crafted server payload. Not exploitable by another player directly, but breaks the trust boundary if a server admin's modded server is malicious. The same pattern (no try/catch on decoder/handler) recurs in most response packets.

### H-7 — `handleForceCleanupColony` uses reflection to invoke a private method (no perm validation on what the method does)
- **File:** `commands/WntCommands.java:3309-3315`
- **Severity:** HIGH (admin-only, but reflective bypass)
- **Threat model:** The `/wnt forcecleanupcolony` admin command uses reflection (`setAccessible(true)`) to call the private `ColonyAbandonmentManager#cleanupAbandonedEntries(IPermissions)` method. This bypasses any future encapsulation. Currently gated by `.requires(hasPermission(2))` so impact is bounded, but the same pattern in `handleEmergencyFix` (lines 3417-3447) writes through reflection to `permissions.setOwner(...)` — a potential corruption vector if abused or invoked at the wrong moment (during a colony transaction). Not exploitable by non-admins but worth a refactor.

---

## Medium

### M-1 — Dead packets present in source tree
- **Files:** `network/packets/ClaimVassalTributePacket.java`, `network/packets/PayDebtPacket.java`
- **Severity:** MEDIUM (footgun)
- **Threat model:** Both classes are NOT registered in `NetworkHandler.register()` but compile and are reachable from anyone who calls `NetworkHandler.sendToServer(new ClaimVassalTributePacket(...))`. `ClaimVassalTributePacket.handlePacket(...)` has NO permission check — it calls `VassalManager.claimVassalTribute(player.getUUID(), vassalColonyId)`. The Vassal manager only checks `rel.overlordUUID.equals(playerId)`, so it is internally safe — but if anyone restores the registration without re-reading the handler, the attacker can be the overlord-of-record and harvest tribute without confirmation. `PayDebtPacket` does check the `isColonyManager` rank but bypasses the SDMShop balance check. Recommend deletion.

### M-2 — `RequestColonyDataPacket` leaks all colony summaries to every requester
- **File:** `network/packets/RequestColonyDataPacket.java:217-221`
- **Severity:** MEDIUM (information disclosure)
- **Threat model:** The packet returns `ColonySummary(id, name)` for **every colony in the world** to every requester. This is required for the spy target dropdown, but it also leaks the full list of colonies — including PRIVATE colonies the player has never visited. For PvP servers that rely on obscurity for hidden bases, this is a permanent leak.
- **Recommendation:** Apply a "visible colonies" filter (e.g. ones the player has touched, or ones in a public registry).

### M-3 — `SetTaxPolicyPacket` accepts any string for `policyName`
- **File:** `network/packets/SetTaxPolicyPacket.java:30, 64`
- **Severity:** MEDIUM (input validation / log injection)
- **Threat model:** Decoder reads `buf.readUtf()` (default 32767 cap). The handler validates `policyName` via `TaxPolicy.fromString(...)`, but the unvalidated string is echoed back in chat ("Invalid policy: " + policyName). An attacker can include `§` color codes or 32 KiB of garbage to spam chat. Low impact but a clear input-trust violation.

### M-4 — `DeploySpyPacket` does not bound `missionType` to the allowed enum
- **File:** `network/packets/DeploySpyPacket.java:28, 54`
- **Severity:** MEDIUM (cost bypass / logic)
- **Threat model:** Decoder reads `buf.readUtf()`. The string is passed straight into `SpyManager.deploySpyMission`. The switch inside `deploySpyMission` has a `default -> 100` cost branch. A client passing a `missionType` of `"SCOUT" + random-garbage` would hit the default 100-coin cost branch even if the intended mission deserved a higher cost — but the mission's `setMissionIntel(new SpyIntelData())` is only called for exact "SCOUT" match. Effect: attacker pays the cheaper default cost while still getting a "spy" deployed; later code that branches on `missionType` may misbehave.
- **Impact:** Cost bypass for spy deployment. The cost is also not actually deducted at deploy time in `deploySpyMission` (only `PENDING_COSTS` is updated — the actual deduction happens elsewhere).

### M-5 — `TreasuryCommand.executeStatus` only checks permission inside `resolveColony` for explicit IDs
- **File:** `commands/TreasuryCommand.java:138-152, 203-237`
- **Severity:** MEDIUM (information disclosure)
- **Threat model:** When `colonyId > 0`, `resolveColony` enforces `hasColonyPermission`. When `colonyId == -1`, position-based resolution checks permission, but the ownership fallback at line 228 returns the colony WITHOUT calling `hasColonyPermission` (relies on `getIColonyByOwner` to return only owned colonies — usually safe, but if the player owns a colony AND is standing inside another colony where they don't have perms, the position-based check returns null and the fallback gives info about a different colony than expected). Edge-case, but the API surface invites confusion.

### M-6 — `executeClaimTax` (the slash command path) does NOT check `TaxPermissionManager.canPlayerClaimTax`
- **File:** `commands/WntCommands.java:1820-1909`
- **Severity:** MEDIUM (parallel-permission bypass)
- **Threat model:** The slash command checks `playerRank.isColonyManager()` but does NOT call `TaxPermissionManager.canPlayerClaimTax(...)` — only `ClaimTaxCommand.execute` (line 104) and `ClaimTaxPacket` (via the `playerRank.isColonyManager()` plus war/raid restriction) consult the TaxPermissionManager. So the `/wnt claimtax` path totally ignores the per-player overrides that exist in `TaxPermissionManager`.
- **Impact:** Officers explicitly revoked of tax-claim permission via the GUI can still use `/wnt claimtax` to claim tax. Permission revocation is effectively cosmetic.

### M-7 — `claimtax -1` for vassal tribute is invoked by ClaimTaxPacket with `amount == -2` — no upper bound on vassal claims
- **File:** `network/packets/ClaimTaxPacket.java:67-72`
- **Severity:** MEDIUM (logic / abuse)
- **Threat model:** `VassalManager.claimVassalTribute(player.getUUID(), colonyId)` only checks `rel.overlordUUID.equals(overlordId)` — so any overlord can spam this packet, and each call drains the vassal's `currentTaxBalance * percent / 100`. There is NO time-based cooldown enforced inside `claimVassalTribute` (it sets `rel.lastPayment = System.currentTimeMillis()` but does NOT consult the previous value). A vassal can be drained over the course of a single second on every tax-generation tick.
- **Impact:** Tribute drain race-condition / replay.

### M-8 — `PayTaxDebtPacket` and `PayDebtPacket` permission gates differ
- **File:** `network/packets/PayTaxDebtPacket.java:52-60` vs `network/packets/PayDebtPacket.java:60-64`
- **Severity:** MEDIUM (inconsistency)
- **Threat model:** `PayTaxDebtPacket` allows ANY officer (rank id > 0 + `TaxPermissionManager.canOfficersClaim`) to pay debt; `PayDebtPacket` (dead) requires `isColonyManager()`. The discrepancy is a tell that the access-control story is unfinished. The `canOfficersClaim` gate is the wrong API to use for debt payment — it's meant for claim, not pay.

---

## Low

### L-1 — Inconsistent `setPacketHandled(true)` calls
- Most packet handlers call `setPacketHandled(true)`; some do not (e.g. `ClaimTaxPacket.handle` returns `true` without calling it, `EndVassalizationPacket` sets it once outside the lambda). Forge will log warnings when the handled flag is not set. Cosmetic but a smell.

### L-2 — `executeClaimTax` falls back to `give` command via reflection-shaped string interpolation when item lookup fails
- **File:** `commands/WntCommands.java:1882-1890`
- **Severity:** LOW (command injection — but `getName().getString()` is safe and `claimedAmount` is an int, currency item is config-controlled)
- **Threat model:** A server admin who sets `currencyItemName` to a value containing spaces or shell-special characters could in principle inject into the `give` command string. The string IS passed via `performPrefixedCommand` so Brigadier parses it, not a shell — escaping is by Brigadier rules. Low risk but a brittle pattern.

### L-3 — `OfficerDataResponsePacket` decoder reads UTF strings without bound parameter
- **File:** `network/packets/OfficerDataResponsePacket.java:30-31`
- **Severity:** LOW (32767 default cap)
- **Threat model:** `buf.readUtf()` without a length argument defaults to 32767 chars. The packet is registered with `PLAY_TO_CLIENT` so only the server sends it — but a malicious server could send a payload containing N\*32767 bytes of officer name. Vanilla packet size limit (~2 MiB) caps this. Cosmetic concern.

### L-4 — `claimtax` slash command in `WntCommands` falls into a loop over ALL colonies and tries each
- **File:** `commands/WntCommands.java:1830-1897`
- **Severity:** LOW
- **Threat model:** If colony name is null, the command iterates EVERY colony in the world and claims for each one where the player is a manager — without any rate limiting. On a large server with many colonies, a single `/wnt claimtax` triggers N save operations (`saveTaxData(true)` per colony) on the main thread. Operational DoS surface.

### L-5 — `WarCommands` registers `wagewar` with `StringArgumentType.string()` — only quoted-string parsing
- **File:** `commands/WarCommands.java:51-53`
- **Severity:** LOW (UX, not security)
- Pure naming inconsistency: top-level `wagewar` uses `string` (allows quoted), but `wnt wagewar` accepts the same. Just noted for cleanup.

### L-6 — `OpenTaxGUIPacket` has no validation that the target screen exists, no encoder check
- **File:** `network/packets/OpenTaxGUIPacket.java`
- **Severity:** LOW
- **Threat model:** Server-to-client only. `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` correctly isolates. No abuse vector unless a server is hostile.

---

## Files Audited

### Permissions
- `src/main/java/net/machiavelli/minecolonytax/permissions/TaxPermissionManager.java`

### Commands (every file scanned for `requires` / `hasPermission` / `getRank` checks)
- `WntCommands.java` (3,885 lines — full read)
- `WarCommands.java`
- `TaxGUICommand.java`
- `ClaimTaxCommand.java`
- `TaxDebtCommand.java`
- `TreasuryCommand.java`
- `TaxPolicyCommand.java`
- `CheckTaxRevenueCommand.java`
- `WarHistoryCommand.java`
- `RandomEventsCommand.java`
- `EntityRaidCommands.java`
- `GeneralPermissionsCommands.java`
- `AbandonmentCheckCommand.java`
- `FactionCommand.java` (deprecated)
- `TradeRouteCommand.java` (deprecated)
- Confirmed register entries (not deeply audited): `AdminTaxGenCommand`, `ColonyActivityCommand`, `DebugTaxCommand`, `OfficerTrackingDebugCommand`, `RaidHistoryCommand`, `RaidRepairCommand`, `RecipeDisableTestCommand`, `WarStatsCommand` — all have at least `hasPermission(0)` or `hasPermission(2)` gates per grep.

### Network (every file)
- `network/NetworkHandler.java`
- `network/EntityGlowPacket.java`
- `network/GlowClientHandler.java`
- `network/packets/ClaimTaxPacket.java`
- `network/packets/PayTaxDebtPacket.java`
- `network/packets/PayDebtPacket.java` (DEAD — not registered)
- `network/packets/EndVassalizationPacket.java`
- `network/packets/ClaimVassalTributePacket.java` (DEAD — not registered)
- `network/packets/SetTaxPolicyPacket.java`
- `network/packets/UpdateTaxPermissionPacket.java`
- `network/packets/UpdatePlayerTaxPermissionPacket.java`
- `network/packets/RequestOfficerDataPacket.java`
- `network/packets/OfficerDataResponsePacket.java`
- `network/packets/OpenTaxGUIPacket.java`
- `network/packets/DeploySpyPacket.java`
- `network/packets/RecallSpyPacket.java`
- `network/packets/DismissEventPacket.java`
- `network/packets/DismissSpyMissionPacket.java`
- `network/packets/RequestSpyDataPacket.java`
- `network/packets/SpyDataResponsePacket.java`
- `network/packets/RequestColonyDataPacket.java`
- `network/packets/ColonyDataResponsePacket.java`
- `network/packets/RequestTreasuryDataPacket.java`
- `network/packets/TreasuryDataResponsePacket.java`
- `network/packets/TreasuryActionPacket.java`
- `network/packets/RequestInvestmentDataPacket.java`
- `network/packets/InvestmentDataResponsePacket.java`
- `network/packets/BuyInvestmentPacket.java`

### Supporting handlers cross-referenced (not deeply audited)
- `TaxManager.claimTax / payTaxDebt / disableTaxGeneration` (TaxManager.java)
- `VassalManager.claimVassalTribute / acceptProposal / revokeRelation`
- `SpyManager.deploySpyMission / recallSpy / dismissMission`
- `TreasuryManager.deposit / withdraw / purchase`
- `ColonyUpgradeManager.purchase`
- `RandomEventManager.dismissFromEventLog / resetColonyEventState`

