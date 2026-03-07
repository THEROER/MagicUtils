package dev.ua.theroer.magicutils.logger;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import dev.ua.theroer.magicutils.placeholders.MagicPlaceholders;
import dev.ua.theroer.magicutils.placeholders.PlaceholderContext;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggerCoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanupPlaceholders() {
        MagicPlaceholders.clearAll();
    }

    @Test
    void debugPlaceholderLoggingCanBeToggledAndFiltersByOwner() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager configManager = new ConfigManager(platform);
        try {
            Object owner = new Object();
            LoggerCore core = new LoggerCore(platform, configManager, owner, "TestPlugin");
            MagicPlaceholders.registerLocal(owner, "user", (audience, argument) -> "alice");
            MagicPlaceholders.registerLocal("foreign", "user", (audience, argument) -> "bob");

            int initialDebugCount = platform.logger.debugMessages.size();

            setDebugPlaceholders(core, true);

            assertEquals("alice",
                    MagicPlaceholders.render(PlaceholderContext.builder().ownerKey(owner).build(), "{user}"));
            assertEquals(initialDebugCount + 1, platform.logger.debugMessages.size());
            assertTrue(platform.logger.debugMessages.get(platform.logger.debugMessages.size() - 1)
                    .contains("key=local:user"));

            assertEquals("bob",
                    MagicPlaceholders.render(PlaceholderContext.builder().ownerKey("foreign").build(), "{user}"));
            assertEquals(initialDebugCount + 1, platform.logger.debugMessages.size());

            setDebugPlaceholders(core, false);
            assertEquals("alice",
                    MagicPlaceholders.render(PlaceholderContext.builder().ownerKey(owner).build(), "{user}"));
            assertEquals(initialDebugCount + 1, platform.logger.debugMessages.size());
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    private static void setDebugPlaceholders(LoggerCore core, boolean enabled) throws Exception {
        Field field = core.getConfig().getClass().getDeclaredField("debugPlaceholders");
        field.setAccessible(true);
        field.setBoolean(core.getConfig(), enabled);

        Method method = LoggerCore.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);
        method.invoke(core);
    }

    private static final class TestPlatform implements Platform, ConfigFormatProvider {
        private final Path configDir;
        private final CapturingLogger logger = new CapturingLogger();
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
            return logger;
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
            return true;
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

    private static final class CapturingLogger implements PlatformLogger {
        private final List<String> infoMessages = new ArrayList<>();
        private final List<String> warnMessages = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();
        private final List<String> debugMessages = new ArrayList<>();

        @Override
        public void info(String message) {
            infoMessages.add(message);
        }

        @Override
        public void warn(String message) {
            warnMessages.add(message);
        }

        @Override
        public void warn(String message, Throwable throwable) {
            warnMessages.add(message + " :: " + throwable.getClass().getSimpleName());
        }

        @Override
        public void error(String message) {
            errorMessages.add(message);
        }

        @Override
        public void error(String message, Throwable throwable) {
            errorMessages.add(message + " :: " + throwable.getClass().getSimpleName());
        }

        @Override
        public void debug(String message) {
            debugMessages.add(message);
        }
    }

    private static final class DirectTaskScheduler implements TaskScheduler {
        private final Executor directExecutor = Runnable::run;
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "logger-test-timer");
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

    private enum NoOpAudience implements Audience {
        INSTANCE;

        @Override
        public void send(Component component) {
        }
    }
}
