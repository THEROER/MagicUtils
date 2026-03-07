package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void setLanguageAsyncReturnsFalseWhenMainThreadDispatchFails() {
        TestPlatform platform = new TestPlatform(tempDir, new IllegalStateException("no main thread"));
        ConfigManager configManager = new ConfigManager(platform);
        try {
            LanguageManager manager = new LanguageManager(platform, configManager);
            manager.init("en");

            boolean result = manager.setLanguageAsync("uk").join();

            assertFalse(result);
            assertEquals("en", manager.getCurrentLanguage());
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void setLanguageAsyncAppliesLanguageWhenDispatchSucceeds() {
        TestPlatform platform = new TestPlatform(tempDir, null);
        ConfigManager configManager = new ConfigManager(platform);
        try {
            LanguageManager manager = new LanguageManager(platform, configManager);
            manager.init("en");

            boolean result = manager.setLanguageAsync("uk").join();

            assertTrue(result);
            assertEquals("uk", manager.getCurrentLanguage());
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    private static final class TestPlatform implements Platform, ConfigFormatProvider {
        private final Path configDir;
        private final RuntimeException dispatchFailure;
        private final TaskScheduler scheduler = new DirectTaskScheduler();

        private TestPlatform(Path configDir, RuntimeException dispatchFailure) {
            this.configDir = configDir;
            this.dispatchFailure = dispatchFailure;
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
            if (dispatchFailure != null) {
                throw dispatchFailure;
            }
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

        @Override
        public String defaultConfigExtension() {
            return "json";
        }

        private void shutdown() {
            scheduler.shutdown();
        }
    }

    private static final class DirectTaskScheduler implements TaskScheduler {
        private final Executor directExecutor = Runnable::run;
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "language-test-timer");
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

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void warn(String message, Throwable throwable) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }

        @Override
        public void debug(String message) {
        }
    }

    private enum NoOpAudience implements Audience {
        INSTANCE;

        @Override
        public void send(Component component) {
        }
    }
}
