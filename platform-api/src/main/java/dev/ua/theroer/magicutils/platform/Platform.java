package dev.ua.theroer.magicutils.platform;

import java.nio.file.Path;
import java.util.Collection;

/**
 * Abstraction over runtime platform (Bukkit, Fabric, NeoForge, etc.).
 */
public interface Platform {
    /**
     * Base directory for configs/lang files.
     *
     * @return config path
     */
    Path configDir();

    /**
     * Primary logger for platform.
     *
     * @return platform logger
     */
    PlatformLogger logger();

    /**
     * Audience representing console/server.
     *
     * @return console audience
     */
    Audience console();

    /**
     * Snapshot of online player audiences, if applicable.
     *
     * @return online audiences collection (may be empty)
     */
    Collection<Audience> onlinePlayers();

    /**
     * Executes a task on the main thread.
     *
     * @param task work to run
     */
    void runOnMain(Runnable task);

    /**
     * Determine if the current thread is the platform main thread.
     *
     * @return true if current thread is main/platform thread.
     */
    boolean isMainThread();

    /**
     * Returns the current execution context for thread-sensitive operations.
     *
     * @return thread context classification
     */
    default ThreadContext threadContext() {
        return isMainThread() ? ThreadContext.MAIN : ThreadContext.WORKER;
    }

    /**
     * Returns the task scheduler for async work.
     *
     * @return task scheduler
     */
    default TaskScheduler scheduler() {
        return TaskSchedulers.shared();
    }

    /**
     * Subscribes to normalized player chat/command messages when the platform supports it.
     *
     * @param listener message listener
     * @return subscription handle, or a no-op subscription when unsupported
     */
    default ListenerSubscription subscribePlayerMessages(PlayerMessageListener listener) {
        return ListenerSubscription.noop();
    }

    /**
     * Subscribes to normalized player join/leave events when the platform supports it.
     *
     * @param listener lifecycle listener
     * @return subscription handle, or a no-op subscription when unsupported
     */
    default ListenerSubscription subscribePlayerLifecycle(PlayerLifecycleListener listener) {
        return ListenerSubscription.noop();
    }
}
