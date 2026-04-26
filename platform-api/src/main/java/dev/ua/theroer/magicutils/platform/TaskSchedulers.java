package dev.ua.theroer.magicutils.platform;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory utilities for {@link TaskScheduler}.
 */
public final class TaskSchedulers {
    private static final TaskScheduler SHARED = new DefaultTaskScheduler("MagicUtils-Shared");

    private TaskSchedulers() {
    }

    /**
     * Creates a single-thread {@link ScheduledExecutorService} with a named daemon thread.
     *
     * @param threadName name for the backing thread
     * @return new single-thread scheduled executor
     */
    public static ScheduledExecutorService newSingleThreadScheduler(String threadName) {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Creates a single-thread {@link ManagedScheduler} with a named daemon thread.
     * The returned scheduler implements {@link AutoCloseable}, making it compatible
     * with {@code MagicRuntime.manage()} for automatic shutdown on runtime close.
     *
     * @param threadName name for the backing thread
     * @return new managed single-thread scheduler
     */
    public static ManagedScheduler newManagedScheduler(String threadName) {
        return new ManagedScheduler(newSingleThreadScheduler(threadName));
    }

    /**
     * Creates a fixed-size thread pool with named daemon threads.
     *
     * @param threadPrefix prefix for thread names (threads are named prefix-1, prefix-2, etc.)
     * @param poolSize number of threads
     * @return new fixed thread pool
     */
    public static ExecutorService newFixedPool(String threadPrefix, int poolSize) {
        AtomicInteger counter = new AtomicInteger(1);
        return Executors.newFixedThreadPool(poolSize, r -> {
            Thread thread = new Thread(r, threadPrefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Creates a fixed-size {@link ManagedExecutor} with named daemon threads.
     * The returned executor implements {@link AutoCloseable}, making it compatible
     * with {@code MagicRuntime.manage()} for automatic shutdown on runtime close.
     *
     * @param threadPrefix prefix for thread names
     * @param poolSize number of threads
     * @return new managed fixed thread pool
     */
    public static ManagedExecutor newManagedPool(String threadPrefix, int poolSize) {
        return new ManagedExecutor(newFixedPool(threadPrefix, poolSize));
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
