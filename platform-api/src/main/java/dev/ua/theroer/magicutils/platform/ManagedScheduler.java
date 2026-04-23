package dev.ua.theroer.magicutils.platform;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A thin {@link AutoCloseable} wrapper around a {@link ScheduledExecutorService}.
 *
 * <p>Extends {@link ManagedExecutor} with scheduling capabilities. Can be registered
 * with {@code MagicRuntime.manage()} for automatic shutdown when the runtime closes.</p>
 */
public final class ManagedScheduler extends ManagedExecutor {
    private final ScheduledExecutorService scheduledDelegate;

    ManagedScheduler(ScheduledExecutorService delegate) {
        super(delegate);
        this.scheduledDelegate = delegate;
    }

    /**
     * Returns the underlying scheduled executor for direct use.
     *
     * @return backing scheduled executor
     */
    @Override
    public ScheduledExecutorService executor() {
        return scheduledDelegate;
    }

    /**
     * Schedules a task at a fixed rate.
     *
     * @param task task to run
     * @param delay initial delay
     * @param period period between runs
     * @param unit time unit
     * @return scheduled future handle
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long delay, long period, TimeUnit unit) {
        return scheduledDelegate.scheduleAtFixedRate(task, delay, period, unit);
    }

    /**
     * Schedules a task with a fixed delay between completion and next start.
     *
     * @param task task to run
     * @param initialDelay initial delay
     * @param delay delay between runs
     * @param unit time unit
     * @return scheduled future handle
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return scheduledDelegate.scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }

    /**
     * Schedules a one-shot task.
     *
     * @param task task to run
     * @param delay delay before execution
     * @param unit time unit
     * @return scheduled future handle
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return scheduledDelegate.schedule(task, delay, unit);
    }
}
