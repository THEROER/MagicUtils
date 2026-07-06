package dev.ua.theroer.magicutils.platform;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

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
     * Finds an online player audience by exact, case-insensitive name.
     *
     * <p>The default implementation scans {@link #onlinePlayers()}; platforms
     * with a direct registry lookup may override for efficiency.</p>
     *
     * @param name player name (case-insensitive)
     * @return the matching audience, or {@code null} if no such player is online
     */
    default @Nullable Audience playerByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (Audience audience : onlinePlayers()) {
            if (audience != null && name.equalsIgnoreCase(audience.name())) {
                return audience;
            }
        }
        return null;
    }

    /**
     * Finds an online player audience by UUID.
     *
     * <p>The default implementation scans {@link #onlinePlayers()}; platforms
     * with a direct registry lookup may override for efficiency.</p>
     *
     * @param id player UUID
     * @return the matching audience, or {@code null} if no such player is online
     */
    default @Nullable Audience playerById(UUID id) {
        if (id == null) {
            return null;
        }
        for (Audience audience : onlinePlayers()) {
            if (audience != null && id.equals(audience.id())) {
                return audience;
            }
        }
        return null;
    }

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
     * Executes a task in the context of a specific audience (e.g., entity region on Folia).
     * On platforms without regional threading, this delegates to {@link #runOnMain(Runnable)}.
     *
     * @param audience target audience for region resolution
     * @param task work to run
     */
    default void runForAudience(Audience audience, Runnable task) {
        runOnMain(task);
    }

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

    /**
     * Subscribes to normalized player locale updates when the platform supports it.
     *
     * @param listener locale listener
     * @return subscription handle, or a no-op subscription when unsupported
     */
    default ListenerSubscription subscribePlayerLocales(PlayerLocaleListener listener) {
        return ListenerSubscription.noop();
    }
}
