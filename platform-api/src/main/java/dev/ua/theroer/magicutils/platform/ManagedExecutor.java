package dev.ua.theroer.magicutils.platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A thin {@link AutoCloseable} wrapper around an {@link ExecutorService}.
 *
 * <p>This allows thread pools to be registered with {@code MagicRuntime.manage()}
 * for automatic shutdown when the runtime closes.</p>
 */
public class ManagedExecutor implements AutoCloseable {
    private final ExecutorService delegate;

    ManagedExecutor(ExecutorService delegate) {
        this.delegate = delegate;
    }

    /**
     * Returns the underlying executor service.
     *
     * @return backing executor
     */
    public ExecutorService executor() {
        return delegate;
    }

    /**
     * Submits a task for asynchronous execution.
     *
     * @param task task to run
     */
    public void execute(Runnable task) {
        delegate.execute(task);
    }

    /**
     * Runs a task asynchronously and returns a future.
     *
     * @param task task to run
     * @return future for completion
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, delegate);
    }

    /**
     * Supplies a value asynchronously and returns a future.
     *
     * @param supplier value supplier
     * @param <T> value type
     * @return future for result
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, delegate);
    }

    /**
     * Returns true if the executor has been shut down.
     *
     * @return true if shut down
     */
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    /**
     * Shuts down the backing executor, attempting orderly termination first.
     */
    public void shutdown() {
        delegate.shutdown();
        try {
            if (!delegate.awaitTermination(3, TimeUnit.SECONDS)) {
                delegate.shutdownNow();
            }
        } catch (InterruptedException e) {
            delegate.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Immediately shuts down the backing executor.
     */
    public void shutdownNow() {
        delegate.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }
}
