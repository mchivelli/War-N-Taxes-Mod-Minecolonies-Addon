package net.machiavelli.minecolonytax.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Off-thread file writer with key-based coalescing.
 *
 * <p>Managers that previously serialised + wrote JSON on the server thread
 * should now snapshot their state on the calling thread (cheap, safe) and
 * hand the resulting write Runnable here. The write itself runs on a single
 * background daemon thread, so {@link java.io.FileWriter} blocking and OS
 * fsync no longer stall server ticks.
 *
 * <p>If two writes for the same key are queued before either has started,
 * only the most recent wins. This prevents pile-up during rapid state
 * changes (e.g. spy mission churn during raids).
 *
 * <p>{@link #shutdownAndFlush()} MUST be called on ServerStoppingEvent so
 * any pending write reaches disk before the JVM exits. After shutdown, any
 * further submit() runs inline so no data is silently dropped.
 */
public final class AsyncSaveExecutor {

    private static final Logger LOGGER = LogManager.getLogger(AsyncSaveExecutor.class);

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WNT-AsyncSave");
        t.setDaemon(true);
        return t;
    });

    /** key -> latest pending write. Worker drains by removing under LOCK. */
    private static final Map<String, Runnable> PENDING = new HashMap<>();
    private static final Object LOCK = new Object();
    private static volatile boolean running = true;

    private AsyncSaveExecutor() {}

    /**
     * Queue a disk write. Only the latest write per {@code key} is kept;
     * earlier pending writes for the same key are discarded.
     */
    public static void submit(String key, Runnable writeJob) {
        if (!running) {
            // After shutdown, run inline so the caller's data isn't lost.
            try {
                writeJob.run();
            } catch (Throwable t) {
                LOGGER.error("Inline save failed for {}: {}", key, t.toString());
            }
            return;
        }

        boolean needSchedule;
        synchronized (LOCK) {
            needSchedule = !PENDING.containsKey(key);
            PENDING.put(key, writeJob);
        }
        if (needSchedule) {
            EXEC.execute(() -> drain(key));
        }
    }

    private static void drain(String key) {
        Runnable job;
        synchronized (LOCK) {
            job = PENDING.remove(key);
        }
        if (job == null) return;
        try {
            job.run();
        } catch (Throwable t) {
            LOGGER.error("Async save failed for {}: {}", key, t.toString());
        }
    }

    /**
     * Drain all pending writes synchronously, then shut the executor down.
     * Subsequent submit() calls run inline.
     */
    public static void shutdownAndFlush() {
        running = false;

        // Barrier FIRST: stop accepting new scheduled tasks and WAIT for the worker to
        // finish any in-flight write before this thread touches any file. Otherwise the
        // inline flush below could write the same file the worker is still writing,
        // racing/corrupting it (codex review). Already-queued drains run during the
        // orderly shutdown and drain PENDING as they go.
        EXEC.shutdown();
        boolean terminated = false;
        try {
            terminated = EXEC.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        if (!terminated) {
            // Worker is still alive after the timeout — do NOT inline-flush, or this thread
            // could write the same file the worker is still writing (codex review). Better to
            // leave a pending write unflushed than to corrupt a file; a stuck worker is itself
            // an error worth surfacing.
            LOGGER.warn("AsyncSaveExecutor did not terminate within 10s; skipping inline flush to avoid racing the worker");
            return;
        }

        // Worker is confirmed stopped; drain anything still pending inline (single-threaded).
        Map<String, Runnable> remaining;
        synchronized (LOCK) {
            remaining = new HashMap<>(PENDING);
            PENDING.clear();
        }
        for (Map.Entry<String, Runnable> e : remaining.entrySet()) {
            try {
                e.getValue().run();
            } catch (Throwable t) {
                LOGGER.error("Flush save failed for {}: {}", e.getKey(), t.toString());
            }
        }
    }
}
