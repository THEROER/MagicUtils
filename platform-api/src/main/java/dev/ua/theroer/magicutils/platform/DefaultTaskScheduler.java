package dev.ua.theroer.magicutils.platform;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class DefaultTaskScheduler implements TaskScheduler {
    private static final String PROP_CPU = "magicutils.scheduler.cpu";
    private static final String PROP_IO = "magicutils.scheduler.io";
    private static final String PROP_TIMER = "magicutils.scheduler.timer";

    private final ExecutorService cpuExecutor;
    private final ExecutorService ioExecutor;
    private final ScheduledExecutorService timerExecutor;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    DefaultTaskScheduler(String name) {
        String prefix = name != null && !name.isBlank() ? name : "MagicUtils";
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int cpuThreads = resolvePoolSize(PROP_CPU, Math.max(1, processors - 1));
        int ioThreads = resolvePoolSize(PROP_IO, Math.max(2, Math.min(32, processors * 2)));
        int timerThreads = resolvePoolSize(PROP_TIMER, 1);

        this.cpuExecutor = Executors.newFixedThreadPool(cpuThreads, new NamedThreadFactory(prefix + "-CPU"));
        this.ioExecutor = Executors.newFixedThreadPool(ioThreads, new NamedThreadFactory(prefix + "-IO"));
        this.timerExecutor = Executors.newScheduledThreadPool(timerThreads, new NamedThreadFactory(prefix + "-Timer"));
    }

    @Override
    public ExecutorService cpu() {
        return cpuExecutor;
    }

    @Override
    public ExecutorService io() {
        return ioExecutor;
    }

    @Override
    public ScheduledExecutorService scheduler() {
        return timerExecutor;
    }

    @Override
    public void shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        shutdownExecutor(cpuExecutor);
        shutdownExecutor(ioExecutor);
        shutdownExecutor(timerExecutor);
    }

    private static void shutdownExecutor(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static int resolvePoolSize(String property, int fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(1, parsed);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String baseName;
        private final AtomicInteger counter = new AtomicInteger(1);

        private NamedThreadFactory(String baseName) {
            this.baseName = Objects.requireNonNull(baseName, "baseName");
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, baseName + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
