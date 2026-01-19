package dev.ua.theroer.magicutils.platform;

import org.jetbrains.annotations.Nullable;

/**
 * Factory utilities for {@link TaskScheduler}.
 */
public final class TaskSchedulers {
    private static final TaskScheduler SHARED = new DefaultTaskScheduler("MagicUtils-Shared");

    private TaskSchedulers() {
    }

    /**
     * Returns the shared scheduler instance.
     *
     * @return shared scheduler
     */
    public static TaskScheduler shared() {
        return SHARED;
    }

    /**
     * Creates a new scheduler and registers shutdown if possible.
     *
     * @param name name prefix for threads
     * @param shutdownRegistrar optional shutdown registrar
     * @return newly created scheduler
     */
    public static TaskScheduler create(String name, @Nullable ShutdownHookRegistrar shutdownRegistrar) {
        TaskScheduler scheduler = new DefaultTaskScheduler(name);
        if (shutdownRegistrar != null) {
            shutdownRegistrar.registerShutdownHook(scheduler::shutdown);
        }
        return scheduler;
    }
}
