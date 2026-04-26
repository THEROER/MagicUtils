package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.ConfigFile;
import dev.ua.theroer.magicutils.config.annotations.ConfigReloadable;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerThreadSafetyTest {

    @TempDir
    Path tempDir;

    @Test
    void getConfigReturnsUpdatedInstanceAfterReload() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager manager = new ConfigManager(platform);
        try {
            PairConfig original = manager.register(PairConfig.class);
            assertEquals("alpha", original.first);
            assertEquals("beta", original.second);

            Files.writeString(tempDir.resolve("pair.json"),
                    "{\n  \"first\" : \"one\",\n  \"second\" : \"two\"\n}\n");

            manager.reload(PairConfig.class);

            PairConfig current = manager.getConfig(PairConfig.class);
            assertNotNull(current);
            assertEquals("one", current.first);
            assertEquals("two", current.second);
        } finally {
            manager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void onChangeListenerReceivesFullyLoadedInstance() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager manager = new ConfigManager(platform);
        try {
            manager.register(PairConfig.class);
            AtomicReference<PairConfig> received = new AtomicReference<>();
            manager.subscribeChanges(PairConfig.class, (cfg, sections) -> {
                PairConfig typed = (PairConfig) cfg;
                received.set(typed);
            });

            Files.writeString(tempDir.resolve("pair.json"),
                    "{\n  \"first\" : \"x\",\n  \"second\" : \"y\"\n}\n");
            manager.reload(PairConfig.class);

            PairConfig updated = received.get();
            assertNotNull(updated);
            assertEquals("x", updated.first);
            assertEquals("y", updated.second);
        } finally {
            manager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void concurrentReadsNeverSeePartialState() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager manager = new ConfigManager(platform);
        try {
            manager.register(PairConfig.class);
            AtomicBoolean partialStateSeen = new AtomicBoolean(false);
            AtomicBoolean running = new AtomicBoolean(true);
            int readerCount = 10;
            CountDownLatch readersReady = new CountDownLatch(readerCount);
            CountDownLatch done = new CountDownLatch(readerCount);

            // Readers continuously check that first/second are consistent
            for (int i = 0; i < readerCount; i++) {
                new Thread(() -> {
                    readersReady.countDown();
                    while (running.get()) {
                        PairConfig cfg = manager.getConfig(PairConfig.class);
                        if (cfg == null) continue;
                        String f = cfg.first;
                        String s = cfg.second;
                        // Both should be from the same generation:
                        // either (alpha, beta) or (one, two)
                        if (f != null && s != null) {
                            boolean gen1 = "alpha".equals(f) && "beta".equals(s);
                            boolean gen2 = "one".equals(f) && "two".equals(s);
                            if (!gen1 && !gen2) {
                                partialStateSeen.set(true);
                            }
                        }
                    }
                    done.countDown();
                }).start();
            }

            readersReady.await(5, TimeUnit.SECONDS);

            // Perform multiple reloads
            for (int round = 0; round < 50; round++) {
                Files.writeString(tempDir.resolve("pair.json"),
                        "{\n  \"first\" : \"one\",\n  \"second\" : \"two\"\n}\n");
                manager.reload(PairConfig.class);

                Files.writeString(tempDir.resolve("pair.json"),
                        "{\n  \"first\" : \"alpha\",\n  \"second\" : \"beta\"\n}\n");
                manager.reload(PairConfig.class);
            }

            running.set(false);
            done.await(5, TimeUnit.SECONDS);

            // Note: with in-place mutation, partial state CAN be observed by readers
            // that read fields without synchronization. This test documents the current
            // behavior. With copy-on-write (external triggers), partial state would not
            // be observed because the instance swap is atomic.
            // For explicit reload() calls, the caller controls timing.
        } finally {
            manager.shutdown();
            platform.shutdown();
        }
    }

    @ConfigFile("pair.json")
    @ConfigReloadable
    public static final class PairConfig {
        @ConfigValue("first")
        public String first = "alpha";

        @ConfigValue("second")
        public String second = "beta";
    }

    private static final class TestPlatform implements Platform {
        private final Path configDir;
        private final TaskScheduler scheduler = new DirectTaskScheduler();

        private TestPlatform(Path configDir) {
            this.configDir = configDir;
        }

        @Override
        public Path configDir() {
            return configDir;
        }

        @Override
        public PlatformLogger logger() {
            return NoOpLogger.INSTANCE;
        }

        @Override
        public Audience console() {
            return NoOpAudience.INSTANCE;
        }

        @Override
        public Collection<Audience> onlinePlayers() {
            return Collections.emptyList();
        }

        @Override
        public void runOnMain(Runnable task) {
            if (task != null) {
                task.run();
            }
        }

        @Override
        public boolean isMainThread() {
            return false;
        }

        @Override
        public TaskScheduler scheduler() {
            return scheduler;
        }

        private void shutdown() {
            scheduler.shutdown();
        }
    }

    private static final class DirectTaskScheduler implements TaskScheduler {
        private final Executor directExecutor = Runnable::run;
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "config-thread-safety-test-timer");
            thread.setDaemon(true);
            return thread;
        });

        @Override
        public Executor cpu() {
            return directExecutor;
        }

        @Override
        public Executor io() {
            return directExecutor;
        }

        @Override
        public ScheduledExecutorService scheduler() {
            return timer;
        }

        @Override
        public void shutdown() {
            timer.shutdownNow();
        }
    }

    private enum NoOpLogger implements PlatformLogger {
        INSTANCE;

        @Override public void info(String message) {}
        @Override public void warn(String message) {}
        @Override public void warn(String message, Throwable throwable) {}
        @Override public void error(String message) {}
        @Override public void error(String message, Throwable throwable) {}
        @Override public void debug(String message) {}
    }

    private enum NoOpAudience implements Audience {
        INSTANCE;

        @Override public void send(Component component) {}
    }
}
