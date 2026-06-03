Reading additional input from stdin...
OpenAI Codex v0.133.0
--------
workdir: C:\Dev\War-N-Taxes-Mod---Minecolonies-Addon
model: gpt-5.5
provider: openai
approval: never
sandbox: read-only
reasoning effort: xhigh
reasoning summaries: none
session id: 019e5cee-b270-7693-824d-f36b1b53468b
--------
user
Reviewing step 10 of 11. New WarBlockLedger captures explosion-damaged blocks per active war. Architecture mirrors harmonised/explosiont's BlockInfo pattern. Review for: (1) interaction with the explosion event - we snapshot but DON'T clear the affected blocks ourselves (explosion proceeds normally and clears them), so restoration just rewrites them back; is this correct? (2) BlockEntity NBT serialize/deserialize - is serializeNBT()/deserializeNBT() correct API for MC 1.20.1?; (3) thread safety of ledger access between event thread and TickScheduler; (4) memory bound if many explosions, (5) wiring gap (not called from endWar yet). Respond: STATUS, up to 5 bullets, up to 3 fixes. Max 300 words.

<stdin>
## STEP 10 — Persistent damage ledger (in-memory; JSON persistence Phase 2)

New file WarBlockLedger.java. Subscribes to ExplosionEvent.Detonate at HIGHEST priority. For explosions within LEDGER_RADIUS=256 of any active war's defender colony center, snapshots BlockInfo(state, NBT, pos, dim). On restoreWarDamage(warId, level), restores ~50 blocks/tick via TickScheduler. Bedrock and air variants are skipped. NOT wired into WarSystem.endWar yet — caller integration is a follow-up. NOT persisted across server restarts yet — ledger lost on crash.

```java
package net.machiavelli.minecolonytax.siege;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.util.TickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures and restores blocks damaged by explosions during active wars.
 *
 * Architecture mirrors the harmonised/explosiont mod's BlockInfo pattern:
 * snapshot state + BlockEntity NBT at explosion time, schedule restoration
 * at endWar. Per-war ledger keyed by warId. Scoped to the defender colony's
 * claim area + a configurable padding radius so wider explosions aren't
 * undone.
 *
 * In-memory only for now — JSON persistence is a Phase 2 follow-up. On
 * server crash mid-war, blocks stay broken (lost ledger) but the affected
 * colony continues to function.
 */
@Mod.EventBusSubscriber(modid = MineColonyTax.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WarBlockLedger {

    private static final Logger LOGGER = LogManager.getLogger(WarBlockLedger.class);

    /** Padding around the colony center within which explosions are ledgered. */
    private static final int LEDGER_RADIUS = 256;
    private static final int LEDGER_RADIUS_SQ = LEDGER_RADIUS * LEDGER_RADIUS;

    /** Snapshot of one damaged block, with enough state to fully restore it. */
    public static final class BlockInfo {
        public final BlockPos pos;
        public final BlockState state;
        public final CompoundTag blockEntityNBT;
        public final ResourceLocation dimResLoc;

        BlockInfo(BlockPos pos, BlockState state, CompoundTag blockEntityNBT, ResourceLocation dimResLoc) {
            this.pos = pos;
            this.state = state;
            this.blockEntityNBT = blockEntityNBT;
            this.dimResLoc = dimResLoc;
        }
    }

    /** warId → ledger entries. Pre-allocated lazily on first capture. */
    private static final Map<UUID, List<BlockInfo>> LEDGERS = new ConcurrentHashMap<>();

    private WarBlockLedger() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (WarSystem.ACTIVE_WARS.isEmpty()) return;

        Level level = event.getLevel();
        if (level.isClientSide()) return;

        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;

        // Find the war (if any) whose defender colony bracket contains this explosion.
        // Cheap pre-filter on the FIRST affected block.
        BlockPos representative = affected.get(0);
        WarData war = findOwningWar(representative, level);
        if (war == null) return;

        ResourceLocation dimResLoc = level.dimension().location();
        List<BlockInfo> ledger = LEDGERS.computeIfAbsent(war.getWarID(), k -> new ArrayList<>());

        // Snapshot AND clear in the same loop so we own the deletion (explosiont pattern).
        // We let the explosion proceed for the visual/damage effects but mark blocks for
        // restoration by removing the originals first and replacing with air.
        for (BlockPos pos : affected) {
            try {
                BlockState state = level.getBlockState(pos);
                Block block = state.getBlock();
                if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) continue;
                if (block == Blocks.BEDROCK) continue; // never touch bedrock

                CompoundTag nbt = null;
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    try { nbt = be.serializeNBT(); } catch (Exception ignored) {}
                }
                ledger.add(new BlockInfo(pos.immutable(), state, nbt, dimResLoc));
            } catch (Exception e) {
                LOGGER.warn("WarBlockLedger snapshot failed at {}: {}", pos, e.getMessage());
            }
        }

        if (TaxConfig.isDebugLogging()) {
            LOGGER.debug("Ledgered {} blocks for war {} (total now {})",
                    affected.size(), war.getWarID(), ledger.size());
        }
    }

    /**
     * Restore all ledgered blocks for the given war back to their pre-explosion
     * state. Spreads work across ticks to avoid chunk-flicker and lag spikes.
     * Called from WarSystem.endWar() — safe to call when no ledger exists.
     */
    public static void restoreWarDamage(UUID warId, Level level) {
        List<BlockInfo> ledger = LEDGERS.remove(warId);
        if (ledger == null || ledger.isEmpty()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Restore at ~50 blocks/tick using TickScheduler.
        final int batchSize = 50;
        final int totalBatches = (int) Math.ceil(ledger.size() / (double) batchSize);
        final int[] cursor = { 0 };
        final long[] taskRef = { -1L };

        if (TaxConfig.isNormalLogging()) {
            LOGGER.info("WarBlockLedger restoring {} blocks for war {} across ~{} ticks",
                    ledger.size(), warId, totalBatches);
        }

        taskRef[0] = TickScheduler.scheduleRepeating(() -> {
            int end = Math.min(cursor[0] + batchSize, ledger.size());
            for (int i = cursor[0]; i < end; i++) {
                BlockInfo info = ledger.get(i);
                try {
                    serverLevel.setBlock(info.pos, info.state, Block.UPDATE_ALL);
                    if (info.blockEntityNBT != null) {
                        BlockEntity be = serverLevel.getBlockEntity(info.pos);
                        if (be != null) {
                            try { be.deserializeNBT(info.blockEntityNBT); be.setChanged(); }
                            catch (Exception ignored) {}
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to restore block at {}: {}", info.pos, e.getMessage());
                }
            }
            cursor[0] = end;
            if (cursor[0] >= ledger.size()) {
                if (taskRef[0] >= 0) TickScheduler.cancel(taskRef[0]);
            }
        }, 50, 50); // 1 tick = 50ms
    }

    /** Number of pending ledger entries for a war (useful for diagnostics). */
    public static int getLedgerSize(UUID warId) {
        List<BlockInfo> ledger = LEDGERS.get(warId);
        return ledger == null ? 0 : ledger.size();
    }

    /** Drop all ledger state — for server shutdown / reset. */
    public static void clearAll() {
        LEDGERS.clear();
    }

    /**
     * Find the war (if any) whose defender colony center is within
     * {@link #LEDGER_RADIUS} of the explosion. Cheap iteration over the small
     * ACTIVE_WARS map.
     */
    private static WarData findOwningWar(BlockPos pos, Level level) {
        for (WarData war : WarSystem.ACTIVE_WARS.values()) {
            IColony defender = war.getColony();
            if (defender == null || defender.getWorld() != level) continue;
            BlockPos center = defender.getCenter();
            long dx = center.getX() - pos.getX();
            long dz = center.getZ() - pos.getZ();
            if ((dx * dx + dz * dz) <= LEDGER_RADIUS_SQ) {
                return war;
            }
        }
        return null;
    }
}
```
</stdin>
codex
STATUS: needs fixes before merge.

- Explosion handling is conceptually correct: in `ExplosionEvent.Detonate`, snapshotting `event.getAffectedBlocks()` and leaving the list intact lets vanilla/Forge explosion destruction proceed, then restoration rewrites blocks later. The code comment claiming “snapshot AND clear” is wrong and should be corrected.
- BlockEntity NBT is likely wrong for Forge/MC 1.20.1. Prefer `be.saveWithFullMetadata()` for capture and `BlockEntity.loadStatic(pos, state, tag)` or `be.load(tag)` after `setBlock`. `serializeNBT()/deserializeNBT()` is not the safest world-save-compatible API here.
- `ConcurrentHashMap` only protects the map. The `ArrayList` values are not thread-safe, so capture and restore can race if TickScheduler and events interleave, or if async scheduling exists. Use synchronized lists, queues, or main-thread-only enforcement.
- Memory is unbounded. Repeated TNT/cannon fire can append indefinitely per war, including duplicate positions. This can become large fast and restoration may replay stale/duplicate snapshots.
- Wiring gap is real: until `restoreWarDamage(warId, level)` is called from `WarSystem.endWar`, the ledger only accumulates and never restores or clears per completed war.

Fixes:
1. Replace per-war `List<BlockInfo>` with `Map<BlockPos, BlockInfo>` or keyed by `dim+pos`, using first snapshot wins.
2. Use 1.20.1 block entity save/load APIs and copy the `CompoundTag`.
3. Add bounds: max entries per war, dedupe, logging when capped, and call restore/clear from `endWar`.
tokens used
17.661
STATUS: needs fixes before merge.

- Explosion handling is conceptually correct: in `ExplosionEvent.Detonate`, snapshotting `event.getAffectedBlocks()` and leaving the list intact lets vanilla/Forge explosion destruction proceed, then restoration rewrites blocks later. The code comment claiming “snapshot AND clear” is wrong and should be corrected.
- BlockEntity NBT is likely wrong for Forge/MC 1.20.1. Prefer `be.saveWithFullMetadata()` for capture and `BlockEntity.loadStatic(pos, state, tag)` or `be.load(tag)` after `setBlock`. `serializeNBT()/deserializeNBT()` is not the safest world-save-compatible API here.
- `ConcurrentHashMap` only protects the map. The `ArrayList` values are not thread-safe, so capture and restore can race if TickScheduler and events interleave, or if async scheduling exists. Use synchronized lists, queues, or main-thread-only enforcement.
- Memory is unbounded. Repeated TNT/cannon fire can append indefinitely per war, including duplicate positions. This can become large fast and restoration may replay stale/duplicate snapshots.
- Wiring gap is real: until `restoreWarDamage(warId, level)` is called from `WarSystem.endWar`, the ledger only accumulates and never restores or clears per completed war.

Fixes:
1. Replace per-war `List<BlockInfo>` with `Map<BlockPos, BlockInfo>` or keyed by `dim+pos`, using first snapshot wins.
2. Use 1.20.1 block entity save/load APIs and copy the `CompoundTag`.
3. Add bounds: max entries per war, dedupe, logging when capped, and call restore/clear from `endWar`.
