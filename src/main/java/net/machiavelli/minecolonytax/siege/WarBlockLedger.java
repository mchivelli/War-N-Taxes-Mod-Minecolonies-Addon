package net.machiavelli.minecolonytax.siege;

import com.minecolonies.api.colony.IColony;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.machiavelli.minecolonytax.WarSystem;
import net.machiavelli.minecolonytax.data.WarData;
import net.machiavelli.minecolonytax.util.TickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * Persistence: the ledger is saved to {@code config/warntax/war_block_ledger.nbt}
 * on server stop and reloaded on server start, mirroring the war save lifecycle
 * ({@code active_wars.json}). Wars survive a restart, so their damage ledger must
 * too — otherwise blocks broken before the restart would never be restored when
 * the war later ends. NBT (not Gson) is used here because BlockState and
 * BlockEntity snapshots are NBT-native and round-trip cleanly. Load is
 * idempotent (in-memory state is cleared first) and the on-disk file is owned by
 * {@link #saveToDisk()}, which rewrites it (or removes it when empty) on the next
 * shutdown — so a partial war-restore never strands recoverable ledger data.
 * Orphan ledgers (whose war did not restore) are pruned via
 * {@link #pruneOrphans(Set)}; in-progress restorations are flushed synchronously
 * on stop via {@link #flushPendingRestores()}.
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

    /**
     * warId → (pos → first snapshot of that pos for this war).
     *
     * Map-by-pos dedupes repeated explosions on the same block, which both
     * caps memory and guarantees we restore the PRE-WAR state (not whatever
     * intermediate state a later TNT shot captured). First snapshot wins.
     */
    private static final Map<UUID, Map<BlockPos, BlockInfo>> LEDGERS = new ConcurrentHashMap<>();

    /** Hard per-war cap so a runaway explosion loop can't OOM the server. */
    private static final int MAX_ENTRIES_PER_WAR = 50_000;

    private WarBlockLedger() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        // Compat: if Explosion't is loaded AND operator opted in, let it handle
        // restoration globally. Our per-war scoped capture becomes redundant.
        if (net.machiavelli.minecolonytax.compat.ExplosiontCompat.shouldDeferToExplosiont()) return;

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
        Map<BlockPos, BlockInfo> ledger = LEDGERS.computeIfAbsent(war.getWarID(), k -> new ConcurrentHashMap<>());

        // Snapshot only — we do NOT clear blocks. Vanilla/Forge explosion handling
        // clears the affected blocks; we simply rewrite them later from the snapshot.
        // First snapshot per pos wins so repeat explosions on the same spot don't
        // overwrite the pre-war state.
        boolean capWarningEmitted = false;
        for (BlockPos pos : affected) {
            try {
                BlockState state = level.getBlockState(pos);
                Block block = state.getBlock();
                if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) continue;
                if (block == Blocks.BEDROCK) continue; // never touch bedrock

                BlockPos immutable = pos.immutable();
                if (ledger.containsKey(immutable)) continue;

                if (ledger.size() >= MAX_ENTRIES_PER_WAR) {
                    if (!capWarningEmitted) {
                        LOGGER.warn("WarBlockLedger cap of {} entries hit for war {} — further snapshots ignored",
                                MAX_ENTRIES_PER_WAR, war.getWarID());
                        capWarningEmitted = true;
                    }
                    break;
                }

                // Use saveWithFullMetadata so the BlockEntity round-trips cleanly via
                // loadStatic on restore (this is the world-save-compatible API for
                // MC 1.20.1; serializeNBT/deserializeNBT can drop position metadata).
                CompoundTag nbt = null;
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    try { nbt = be.saveWithFullMetadata(); } catch (Exception ignored) {}
                }
                ledger.put(immutable, new BlockInfo(immutable, state, nbt, dimResLoc));
            } catch (Exception e) {
                LOGGER.warn("WarBlockLedger snapshot failed at {}: {}", pos, e.getMessage());
            }
        }

        if (TaxConfig.isDebugLogging()) {
            LOGGER.debug("Ledgered explosion for war {} (total entries now {})",
                    war.getWarID(), ledger.size());
        }
    }

    /** Blocks restored per tick during batched restoration. */
    private static final int RESTORE_BATCH_SIZE = 50;

    /**
     * One in-progress batched restoration. Tracked in {@link #IN_FLIGHT} so a
     * server stop mid-restore can flush the remaining blocks synchronously
     * instead of stranding them (the ledger is already removed from LEDGERS
     * once restoration starts, so it would otherwise not be re-persisted).
     */
    private static final class RestoreJob {
        final BlockInfo[] entries;
        final ServerLevel level;
        int cursor = 0;
        long taskId = -1L;
        RestoreJob(BlockInfo[] entries, ServerLevel level) {
            this.entries = entries;
            this.level = level;
        }
    }

    /** warId → active batched restoration job. */
    private static final Map<UUID, RestoreJob> IN_FLIGHT = new ConcurrentHashMap<>();

    /**
     * Restore all ledgered blocks for the given war back to their pre-explosion
     * state. Spreads work across ticks to avoid chunk-flicker and lag spikes.
     * Called from WarSystem.endWar() — safe to call when no ledger exists.
     *
     * Idempotent / double-restore safe by construction:
     *  - The {@code LEDGERS.remove(warId)} below is the first action, so a repeated
     *    endWar(warId) within the same session finds null and returns — the
     *    snapshot can only be applied once per session.
     *  - Across a restart the only re-population of a war's ledger is
     *    {@link #loadFromDisk()}, after which {@link #pruneOrphans(Set)} drops any
     *    ledger whose war is not active. A war whose blocks were already restored
     *    is no longer active, and {@code active_wars.json} is deleted on load and
     *    co-written with this ledger at graceful shutdown — so a "war still active
     *    AND already restored" state cannot occur, and the reloaded ledger is
     *    pruned rather than re-applied.
     */
    public static void restoreWarDamage(UUID warId, Level level) {
        Map<BlockPos, BlockInfo> ledgerMap = LEDGERS.remove(warId);
        if (ledgerMap == null || ledgerMap.isEmpty()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // Snapshot to an array once so the restore loop is index-based and
        // doesn't churn on the underlying map.
        final RestoreJob job = new RestoreJob(ledgerMap.values().toArray(new BlockInfo[0]), serverLevel);
        IN_FLIGHT.put(warId, job);

        if (TaxConfig.isNormalLogging()) {
            int totalBatches = (int) Math.ceil(job.entries.length / (double) RESTORE_BATCH_SIZE);
            LOGGER.info("WarBlockLedger restoring {} blocks for war {} across ~{} ticks",
                    job.entries.length, warId, totalBatches);
        }

        job.taskId = TickScheduler.scheduleRepeating(() -> {
            int end = Math.min(job.cursor + RESTORE_BATCH_SIZE, job.entries.length);
            for (int i = job.cursor; i < end; i++) {
                applyRestore(job.level, job.entries[i]);
            }
            job.cursor = end;
            if (job.cursor >= job.entries.length) {
                if (job.taskId >= 0) TickScheduler.cancel(job.taskId);
                IN_FLIGHT.remove(warId);
            }
        }, 50, 50); // 1 tick = 50ms
    }

    /** Write a single snapshotted block (and its BlockEntity, if any) back to the world. */
    private static void applyRestore(ServerLevel level, BlockInfo info) {
        try {
            level.setBlock(info.pos, info.state, Block.UPDATE_ALL);
            if (info.blockEntityNBT != null) {
                // loadStatic is the world-save-compatible reconstruction path;
                // it pairs with saveWithFullMetadata used at capture time.
                BlockEntity rebuilt = BlockEntity.loadStatic(info.pos, info.state, info.blockEntityNBT);
                if (rebuilt != null) {
                    level.setBlockEntity(rebuilt);
                    rebuilt.setChanged();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to restore block at {}: {}", info.pos, e.getMessage());
        }
    }

    /**
     * Synchronously finish every in-progress batched restoration. Called on
     * server stop (before TickScheduler shutdown) so a stop mid-restore writes
     * the remaining blocks to the still-loaded levels — they are then saved with
     * the world. Without this, blocks past the restore cursor would be lost:
     * their ledger was already removed from LEDGERS when restoration started.
     */
    public static void flushPendingRestores() {
        if (IN_FLIGHT.isEmpty()) return;
        int flushed = 0;
        for (RestoreJob job : IN_FLIGHT.values()) {
            if (job.taskId >= 0) TickScheduler.cancel(job.taskId);
            for (int i = job.cursor; i < job.entries.length; i++) {
                applyRestore(job.level, job.entries[i]);
                flushed++;
            }
            job.cursor = job.entries.length;
        }
        IN_FLIGHT.clear();
        if (flushed > 0 && TaxConfig.isNormalLogging()) {
            LOGGER.info("WarBlockLedger flushed {} pending block restorations on shutdown", flushed);
        }
    }

    /** Number of pending ledger entries for a war (useful for diagnostics). */
    public static int getLedgerSize(UUID warId) {
        Map<BlockPos, BlockInfo> ledger = LEDGERS.get(warId);
        return ledger == null ? 0 : ledger.size();
    }

    /** Drop all ledger state — for server shutdown / reset. */
    public static void clearAll() {
        LEDGERS.clear();
        IN_FLIGHT.clear();
    }

    // ==================== PERSISTENCE ====================

    private static final String LEDGER_STORAGE_FILE = "config/warntax/war_block_ledger.nbt";

    /**
     * Persist all pending ledgers to disk so they survive a server restart.
     * Called on ServerStoppingEvent, alongside {@code WarSystem.saveActiveWars()}.
     * If there is nothing to persist, any stale file is removed.
     */
    public static void saveToDisk() {
        try {
            Path path = Paths.get(LEDGER_STORAGE_FILE);
            if (LEDGERS.isEmpty()) {
                Files.deleteIfExists(path);
                return;
            }
            Files.createDirectories(path.getParent());

            CompoundTag root = new CompoundTag();
            ListTag warsTag = new ListTag();
            int totalBlocks = 0;

            for (Map.Entry<UUID, Map<BlockPos, BlockInfo>> warEntry : LEDGERS.entrySet()) {
                Map<BlockPos, BlockInfo> ledger = warEntry.getValue();
                if (ledger == null || ledger.isEmpty()) continue;

                CompoundTag warTag = new CompoundTag();
                warTag.putString("warId", warEntry.getKey().toString());

                ListTag blocksTag = new ListTag();
                for (BlockInfo info : ledger.values()) {
                    try {
                        CompoundTag b = new CompoundTag();
                        b.putInt("x", info.pos.getX());
                        b.putInt("y", info.pos.getY());
                        b.putInt("z", info.pos.getZ());
                        b.put("state", NbtUtils.writeBlockState(info.state));
                        if (info.dimResLoc != null) b.putString("dim", info.dimResLoc.toString());
                        if (info.blockEntityNBT != null) b.put("be", info.blockEntityNBT);
                        blocksTag.add(b);
                        totalBlocks++;
                    } catch (Exception perBlock) {
                        LOGGER.warn("WarBlockLedger save skipped a block in war {}: {}",
                                warEntry.getKey(), perBlock.getMessage());
                    }
                }
                if (blocksTag.isEmpty()) continue;
                warTag.put("blocks", blocksTag);
                warsTag.add(warTag);
            }

            root.put("wars", warsTag);

            // Atomic write: temp file then move over the live file.
            File tmp = path.resolveSibling(path.getFileName() + ".tmp").toFile();
            NbtIo.writeCompressed(root, tmp);
            try {
                Files.move(tmp.toPath(), path,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException windowsFallback) {
                Files.move(tmp.toPath(), path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("WarBlockLedger saved {} blocks across {} wars to {}",
                        totalBlocks, warsTag.size(), LEDGER_STORAGE_FILE);
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to save WarBlockLedger to disk", ex);
        }
    }

    /**
     * Reload persisted ledgers from disk into memory. Called on server start,
     * after {@code WarSystem.loadAndResumeActiveWars()} so orphan ledgers can be
     * pruned against the restored war set. The file is deleted after a successful
     * load to prevent double-restoration (mirrors the war save file lifecycle).
     */
    public static void loadFromDisk() {
        // Establish disk as the source of truth: drop any in-memory state left over
        // from a previous world in the same JVM (single-player relog) before loading.
        LEDGERS.clear();
        IN_FLIGHT.clear();

        Path path = Paths.get(LEDGER_STORAGE_FILE);
        try {
            if (!Files.exists(path)) return;

            // If the operator now defers to Explosion't, our stale ledger would
            // double-restore on top of Explosion't's heal. Drop it instead.
            if (net.machiavelli.minecolonytax.compat.ExplosiontCompat.shouldDeferToExplosiont()) {
                Files.deleteIfExists(path);
                LOGGER.info("WarBlockLedger deferring to Explosion't — discarded persisted ledger");
                return;
            }

            HolderGetter<Block> blockLookup = BuiltInRegistries.BLOCK.asLookup();
            CompoundTag root = NbtIo.readCompressed(path.toFile());
            if (root == null || !root.contains("wars")) {
                Files.deleteIfExists(path);
                return;
            }

            ListTag warsTag = root.getList("wars", Tag.TAG_COMPOUND);
            int loadedWars = 0;
            int loadedBlocks = 0;

            for (int w = 0; w < warsTag.size(); w++) {
                CompoundTag warTag = warsTag.getCompound(w);
                UUID warId;
                try {
                    warId = UUID.fromString(warTag.getString("warId"));
                } catch (IllegalArgumentException badId) {
                    LOGGER.warn("WarBlockLedger load skipped war with invalid warId '{}'", warTag.getString("warId"));
                    continue;
                }

                ListTag blocksTag = warTag.getList("blocks", Tag.TAG_COMPOUND);
                Map<BlockPos, BlockInfo> ledger = new ConcurrentHashMap<>();
                for (int i = 0; i < blocksTag.size(); i++) {
                    try {
                        CompoundTag b = blocksTag.getCompound(i);
                        BlockPos pos = new BlockPos(b.getInt("x"), b.getInt("y"), b.getInt("z"));
                        BlockState state = NbtUtils.readBlockState(blockLookup, b.getCompound("state"));
                        ResourceLocation dim = b.contains("dim")
                                ? ResourceLocation.tryParse(b.getString("dim")) : null;
                        CompoundTag beNbt = b.contains("be") ? b.getCompound("be") : null;
                        ledger.put(pos, new BlockInfo(pos, state, beNbt, dim));
                        loadedBlocks++;
                    } catch (Exception perBlock) {
                        LOGGER.warn("WarBlockLedger load skipped a block in war {}: {}", warId, perBlock.getMessage());
                    }
                }
                if (!ledger.isEmpty()) {
                    LEDGERS.put(warId, ledger);
                    loadedWars++;
                }
            }

            // Do NOT delete the file here. saveToDisk() owns the file lifecycle and
            // rewrites (or removes, when empty) the ledger on the next shutdown after
            // pruneOrphans() has run. Deleting now would lose recoverable ledgers if
            // WarSystem.loadAndResumeActiveWars() partially fails and preserves its
            // wars to active_wars.json.failed-<ts> for manual recovery. Reload is
            // idempotent (LEDGERS is cleared at the top) and restoration only fires
            // for wars still present in LEDGERS at endWar, so keeping the file cannot
            // cause a double-restore.

            if (TaxConfig.isNormalLogging()) {
                LOGGER.info("WarBlockLedger loaded {} blocks across {} wars from disk",
                        loadedBlocks, loadedWars);
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to load WarBlockLedger from disk", ex);
            // Preserve the unreadable file for forensics instead of deleting it
            // (mirrors active_wars.json.failed-<ts> behavior) so restoration data
            // is never silently lost on a transient/parse failure.
            try {
                Path failed = path.resolveSibling(path.getFileName() + ".failed-" + System.currentTimeMillis());
                Files.move(path, failed, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warn("Preserved unreadable WarBlockLedger at {}", failed);
            } catch (Exception moveEx) {
                LOGGER.error("Could not preserve unreadable WarBlockLedger at {}; leaving in place", path, moveEx);
            }
        }
    }

    /**
     * Drop any ledger whose war is not in the supplied active-war set. Called after
     * war restoration so a ledger for a war that failed to restore (or was already
     * concluded) does not linger and resurrect blocks at a later, unrelated endWar.
     */
    public static void pruneOrphans(Set<UUID> activeWarIds) {
        if (activeWarIds == null) return;
        int before = LEDGERS.size();
        LEDGERS.keySet().retainAll(activeWarIds);
        int pruned = before - LEDGERS.size();
        if (pruned > 0 && TaxConfig.isNormalLogging()) {
            LOGGER.info("WarBlockLedger pruned {} orphan ledger(s) with no matching active war", pruned);
        }
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
