package net.machiavelli.minecolonytax.util;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central tick-based scheduler that replaces all java.util.Timer/TimerTask usage.
 * All scheduled tasks run on the main server thread via Forge's ServerTickEvent,
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
public class TickScheduler {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int TICKS_PER_SECOND = 20;

    private static final AtomicLong NEXT_TASK_ID = new AtomicLong(1);
    private static final Map<Long, ScheduledTask> TASKS = new ConcurrentHashMap<>();

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

    public static long scheduleDelayed(Runnable action, long delayMs) {
        long ticks = msToTicks(delayMs);
        long id = NEXT_TASK_ID.getAndIncrement();
        TASKS.put(id, new ScheduledTask(action, ticks, 0));
        return id;
    }

    public static long scheduleRepeating(Runnable action, long delayMs, long intervalMs) {
        long delayTicks = msToTicks(delayMs);
        long intervalTicks = Math.max(1, msToTicks(intervalMs));
        long id = NEXT_TASK_ID.getAndIncrement();
        TASKS.put(id, new ScheduledTask(action, delayTicks, intervalTicks));
        return id;
    }

    public static void cancel(long taskId) {
        ScheduledTask task = TASKS.remove(taskId);
        if (task != null) {
            task.cancelled = true;
        }
    }

    public static boolean isActive(long taskId) {
        return TASKS.containsKey(taskId);
    }

    public static void shutdown() {
        int count = TASKS.size();
        TASKS.values().forEach(t -> t.cancelled = true);
        TASKS.clear();
        if (count > 0 && net.machiavelli.minecolonytax.TaxConfig.isNormalLogging()) {
            LOGGER.info("TickScheduler shutdown - cleared {} pending tasks", count);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
                    it.remove();
                } else if (task.intervalTicks > 0) {
                    task.ticksRemaining = task.intervalTicks;
                } else {
                    it.remove();
                }
            }
        }
    }

    private static long msToTicks(long ms) {
        return Math.max(1, ms / 50);
    }
}
