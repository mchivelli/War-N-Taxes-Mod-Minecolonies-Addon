package net.machiavelli.minecolonytax.util;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central tick-based scheduler that replaces all java.util.Timer/TimerTask usage.
 * All scheduled tasks run on the main server thread via NeoForge's ServerTickEvent,
 * eliminating cross-thread state mutation of war/raid data.
 *
 * <p>Key guarantees:
 * <ul>
 *   <li>All callbacks execute on the main server thread</li>
 *   <li>Deterministic tick-based timing (20 ticks = 1 second)</li>
 *   <li>Safe cancellation by task ID</li>
 *   <li>Automatic cleanup on server shutdown</li>
 * </ul>
 */
@EventBusSubscriber
public class TickScheduler {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final AtomicLong NEXT_TASK_ID = new AtomicLong(1);
    private static final Map<Long, ScheduledTask> TASKS = new ConcurrentHashMap<>();

    // ─── Task definition ───────────────────────────────────────────────

    private static class ScheduledTask {
        final Runnable action;
        long ticksRemaining;       // ticks until first/next execution
        final long intervalTicks;  // 0 = one-shot, >0 = repeating
        volatile boolean cancelled;

        ScheduledTask(Runnable action, long delayTicks, long intervalTicks) {
            this.action = action;
            this.ticksRemaining = delayTicks;
            this.intervalTicks = intervalTicks;
            this.cancelled = false;
        }
    }

    // ─── Public scheduling API ─────────────────────────────────────────

    /**
     * Schedule a one-shot task after a delay in milliseconds.
     *
     * @param action  the callback to run on the server thread
     * @param delayMs delay in milliseconds (converted to ticks, minimum 1 tick)
     * @return unique task ID for cancellation
     */
    public static long scheduleDelayed(Runnable action, long delayMs) {
        long ticks = msToTicks(delayMs);
        long id = NEXT_TASK_ID.getAndIncrement();
        TASKS.put(id, new ScheduledTask(action, ticks, 0));
        return id;
    }

    /**
     * Schedule a repeating task with an initial delay and fixed interval.
     *
     * @param action     the callback to run on the server thread each interval
     * @param delayMs    initial delay in milliseconds
     * @param intervalMs repeat interval in milliseconds (minimum 1 tick)
     * @return unique task ID for cancellation
     */
    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
        long delayTicks = msToTicks(delayMs);
        long intervalTicks = Math.max(1, msToTicks(intervalMs));
        long id = NEXT_TASK_ID.getAndIncrement();
        TASKS.put(id, new ScheduledTask(action, delayTicks, intervalTicks));
        return id;
    }

    /**
     * Cancel a scheduled task by its ID. Safe to call multiple times or with invalid IDs.
     *
     * @param taskId the task ID returned by a schedule method
     */
    public static void cancel(long taskId) {
        ScheduledTask task = TASKS.remove(taskId);
        if (task != null) {
            task.cancelled = true;
        }
    }

    /**
     * Check whether a task is still active (not cancelled/completed).
     */
    public static boolean isActive(long taskId) {
        return TASKS.containsKey(taskId);
    }

    /**
     * Remove all scheduled tasks. Called on server shutdown.
     */
    public static void shutdown() {
        int count = TASKS.size();
        TASKS.values().forEach(t -> t.cancelled = true);
        TASKS.clear();
        if (count > 0) {
            LOGGER.info("TickScheduler shutdown - cleared {} pending tasks", count);
        }
    }

    // ─── NeoForge tick hook ────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) return;

        Iterator<Map.Entry<Long, ScheduledTask>> it = TASKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ScheduledTask> entry = it.next();
            ScheduledTask task = entry.getValue();

            if (task.cancelled) {
                it.remove();
                continue;
            }

            task.ticksRemaining--;

            if (task.ticksRemaining <= 0) {
                try {
                    task.action.run();
                } catch (Exception e) {
                    LOGGER.error("TickScheduler task {} threw an exception", entry.getKey(), e);
                }

                if (task.cancelled) {
                    // Task cancelled itself during execution
                    it.remove();
                } else if (task.intervalTicks > 0) {
                    // Repeating – reset countdown
                    task.ticksRemaining = task.intervalTicks;
                } else {
                    // One-shot – remove
                    it.remove();
                }
            }
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    private static long msToTicks(long ms) {
        // 1 tick = 50ms, minimum 1 tick
        return Math.max(1, ms / 50);
    }
}
