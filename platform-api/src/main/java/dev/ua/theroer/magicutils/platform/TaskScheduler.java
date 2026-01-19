package dev.ua.theroer.magicutils.platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Provides executors for CPU-bound and IO-bound tasks.
 */
public interface TaskScheduler extends AutoCloseable {
    /**
     * Executor for CPU-heavy work.
     *
     * @return CPU executor
     */
    Executor cpu();

    /**
     * Executor for blocking IO work.
     *
     * @return IO executor
     */
    Executor io();

    /**
     * Scheduler for delayed tasks.
     *
     * @return scheduled executor
     */
    ScheduledExecutorService scheduler();

    /**
     * Default async executor (CPU by default).
     *
     * @return async executor
     */
    default Executor async() {
        return cpu();
    }

    /**
     * Run a task on the CPU executor.
     *
     * @param task task to run
     * @return future for completion
     */
    default CompletableFuture<Void> runCpu(Runnable task) {
        return CompletableFuture.runAsync(task, cpu());
    }

    /**
     * Run a task on the IO executor.
     *
     * @param task task to run
     * @return future for completion
     */
    default CompletableFuture<Void> runIo(Runnable task) {
        return CompletableFuture.runAsync(task, io());
    }

    /**
     * Run a task on the default async executor.
     *
     * @param task task to run
     * @return future for completion
     */
    default CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, async());
    }

    /**
     * Supply a value on the CPU executor.
     *
     * @param supplier value supplier
     * @param <T> value type
     * @return future for result
     */
    default <T> CompletableFuture<T> supplyCpu(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, cpu());
    }

    /**
     * Supply a value on the IO executor.
     *
     * @param supplier value supplier
     * @param <T> value type
     * @return future for result
     */
    default <T> CompletableFuture<T> supplyIo(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, io());
    }

    /**
     * Supply a value on the default async executor.
     *
     * @param supplier value supplier
     * @param <T> value type
     * @return future for result
     */
    default <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, async());
    }

    /**
     * Shutdown all executors.
     */
    void shutdown();

    @Override
    default void close() {
        shutdown();
    }
}
