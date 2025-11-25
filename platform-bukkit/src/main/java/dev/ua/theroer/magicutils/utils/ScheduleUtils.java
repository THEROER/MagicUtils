package dev.ua.theroer.magicutils.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Utility class for scheduling tasks and countdowns in Bukkit/Spigot.
 */
public final class ScheduleUtils {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private ScheduleUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    /**
     * Universal repeater: will execute action count times with period periodTicks,
     * then call onComplete.
     * 
     * @param count       the number of times to execute the action
     * @param periodTicks the period in ticks
     * @param action      the action to execute
     * @param onComplete  the action to execute when the task is complete
     * @param plugin      the plugin to run the task on
     * @return the runnable task
     */
    public static BukkitRunnable repeat(int count, long periodTicks, Runnable action, Runnable onComplete,
            Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (count <= 0)
            count = 1;
        final int total = count;
        BukkitRunnable runnable = new BukkitRunnable() {
            int done = 0;

            @Override
            public void run() {
                try {
                    if (done >= total) {
                        cancel();
                        if (onComplete != null)
                            onComplete.run();
                        return;
                    }
                    if (action != null)
                        action.run();
                    done++;
                } catch (Throwable t) {
                    cancel();
                    t.printStackTrace();
                }
            }
        };
        runnable.runTaskTimer(plugin, 0L, Math.max(1L, periodTicks));
        return runnable;
    }

    /**
     * Countdown in seconds (every second). Returns a task that can be cancelled.
     * 
     * @param seconds    the number of seconds to count down
     * @param onTick     the action to execute on each tick
     * @param onComplete the action to execute when the task is complete
     * @return the countdown task
     */
    public static CountdownTask countdown(int seconds, Consumer<Integer> onTick, Runnable onComplete) {
        Plugin plugin = Bukkit.getPluginManager().getPlugins()[0];
        int total = Math.max(1, seconds);
        BukkitRunnable runnable = new BukkitRunnable() {
            int left = total;

            @Override
            public void run() {
                try {
                    if (left <= 0) {
                        cancel();
                        if (onComplete != null)
                            onComplete.run();
                        return;
                    }
                    if (onTick != null)
                        onTick.accept(left);
                    left--;
                } catch (Throwable t) {
                    cancel();
                    t.printStackTrace();
                }
            }
        };
        runnable.runTaskTimer(plugin, 0L, 20L);
        return new CountdownTask(runnable);
    }

    /**
     * A task that counts down from a given number of seconds.
     */
    public static final class CountdownTask {
        private final BukkitRunnable delegate;

        /**
         * Constructs a new CountdownTask.
         * 
         * @param delegate the BukkitRunnable to delegate to
         */
        private CountdownTask(BukkitRunnable delegate) {
            this.delegate = delegate;
        }

        /**
         * Cancels the task.
         */
        public void cancel() {
            delegate.cancel();
        }

        /**
         * Checks if the task is cancelled.
         * 
         * @return true if the task is cancelled, false otherwise
         */
        public boolean isCancelled() {
            return delegate.isCancelled();
        }
    }
}