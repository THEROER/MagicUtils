package dev.ua.theroer.magicutils.platform;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Convenience helpers for scheduling tasks with {@link Platform}.
 */
public final class Tasks {
    private Tasks() {
    }

    /**
     * Resolves the scheduler for a platform (shared fallback when null).
     *
     * @param platform platform instance
     * @return scheduler instance
     */
    public static TaskScheduler scheduler(@Nullable Platform platform) {
        return platform != null ? platform.scheduler() : TaskSchedulers.shared();
    }

    /**
     * Runs a task on the main thread and returns a future.
     *
     * @param platform platform instance
     * @param task task to run
     * @return future for completion
     */
    public static CompletableFuture<Void> runOnMain(@Nullable Platform platform, Runnable task) {
        if (task == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable wrapped = () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        };
        if (platform == null || platform.isMainThread()) {
            wrapped.run();
        } else {
            dispatchOnMain(platform, wrapped, future);
        }
        return future;
    }

    /**
     * Runs a supplier on the main thread and returns a future.
     *
     * @param platform platform instance
     * @param supplier supplier to run
     * @param <T> value type
     * @return future for result
     */
    public static <T> CompletableFuture<T> callOnMain(@Nullable Platform platform, Supplier<T> supplier) {
        if (supplier == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable wrapped = () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        };
        if (platform == null || platform.isMainThread()) {
            wrapped.run();
        } else {
            dispatchOnMain(platform, wrapped, future);
        }
        return future;
    }

    /**
     * Ensures the completion stage resolves on the main thread.
     *
     * @param platform platform instance
     * @param stage source stage
     * @param <T> value type
     * @return future completing on main thread
     */
    public static <T> CompletableFuture<T> thenOnMain(@Nullable Platform platform, CompletionStage<T> stage) {
        CompletableFuture<T> target = new CompletableFuture<>();
        if (stage == null) {
            target.complete(null);
            return target;
        }
        stage.whenComplete((value, error) -> {
            if (error != null) {
                target.completeExceptionally(error);
                return;
            }
            if (platform == null || platform.isMainThread()) {
                target.complete(value);
                return;
            }
            dispatchOnMain(platform, () -> target.complete(value), target);
        });
        return target;
    }

    private static void dispatchOnMain(Platform platform, Runnable task, CompletableFuture<?> target) {
        try {
            platform.runOnMain(task);
        } catch (Throwable error) {
            target.completeExceptionally(error);
        }
    }

    /**
     * Creates a failed future on Java 8.
     *
     * @param error error to propagate
     * @param <T> future type
     * @return failed future
     */
    public static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
