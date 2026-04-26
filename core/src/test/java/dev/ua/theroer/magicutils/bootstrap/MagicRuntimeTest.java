package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void closeRunsManagedResourcesInReverseOrderAndExposesComponents() throws Exception {
        try (TestContext context = newContext("reverse-order")) {
            TestPlatform platform = context.platform();
            ConfigManager configManager = context.configManager();
            LoggerCore logger = context.logger();
            List<String> closeOrder = new ArrayList<>();

            MagicRuntime runtime = MagicRuntime.builder(platform, configManager, logger)
                    .component(String.class, "value")
                    .manage("first", (AutoCloseable) () -> closeOrder.add("first"))
                    .onClose("second", () -> closeOrder.add("second"))
                    .manageConfigManager(false)
                    .autoRegisterShutdown(false)
                    .build();

            assertSame(platform, runtime.requireComponent(Platform.class));
            assertSame(configManager, runtime.requireComponent(ConfigManager.class));
            assertSame(logger, runtime.requireComponent(LoggerCore.class));
            assertEquals("value", runtime.requireComponent(String.class));

            runtime.close();

            assertEquals(List.of("second", "first"), closeOrder);
            assertTrue(runtime.isClosed());
        }
    }

    @Test
    void platformShutdownHookClosesRuntimeAndUnregistersIt() throws Exception {
        try (TestContext context = newContext("platform-shutdown")) {
            TestPlatform platform = context.platform();
            ConfigManager configManager = context.configManager();
            LoggerCore logger = context.logger();
            List<String> closeOrder = new ArrayList<>();

            int baselineHooks = platform.shutdownHookCount();
            MagicRuntime runtime = MagicRuntime.builder(platform, configManager, logger)
                    .manageConfigManager(false)
                    .onClose("marker", () -> closeOrder.add("closed"))
                    .build();

            assertEquals(baselineHooks + 1, platform.shutdownHookCount());
            assertFalse(runtime.isClosed());

            platform.fireShutdown();

            assertEquals(List.of("closed"), closeOrder);
            assertTrue(runtime.isClosed());
            assertEquals(0, platform.shutdownHookCount());
        }
    }

    @Test
    void lateManagedResourcesCloseImmediatelyAfterRuntimeShutdown() throws Exception {
        try (TestContext context = newContext("late-close")) {
            TestPlatform platform = context.platform();
            ConfigManager configManager = context.configManager();
            LoggerCore logger = context.logger();
            TestCloseable closeable = new TestCloseable();

            MagicRuntime runtime = MagicRuntime.builder(platform, configManager, logger)
                    .manageConfigManager(false)
                    .autoRegisterShutdown(false)
                    .build();

            runtime.close();
            runtime.manage("late", closeable);

            assertTrue(closeable.closed);
        }
    }

    private TestContext newContext(String name) throws IOException {
        Path directory = Files.createDirectories(tempDir.resolve(name));
        TestPlatform platform = new TestPlatform(directory);
        RecordingConfigManager configManager = new RecordingConfigManager(platform);
        LoggerCore logger = new LoggerCore(platform, configManager, this, "MagicRuntimeTest");
        return new TestContext(platform, configManager, logger);
    }

    private static final class TestCloseable implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class RecordingConfigManager extends ConfigManager {
        private int shutdownCalls;

        private RecordingConfigManager(Platform platform) {
            super(platform);
        }

        @Override
        public void shutdown() {
            shutdownCalls++;
            super.shutdown();
        }
    }

    private record TestContext(TestPlatform platform,
                               RecordingConfigManager configManager,
                               LoggerCore logger) implements AutoCloseable {
        @Override
        public void close() {
            if (configManager.shutdownCalls == 0) {
                configManager.shutdown();
            }
        }
    }

    private static final class TestPlatform implements Platform, ShutdownHookRegistrar {
        private final Path configDir;
        private final PlatformLogger logger = new TestPlatformLogger();
        private final CopyOnWriteArrayList<Runnable> hooks = new CopyOnWriteArrayList<>();

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
            return null;
        }

        @Override
        public Collection<Audience> onlinePlayers() {
            return List.of();
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
        public void registerShutdownHook(Runnable hook) {
            hooks.add(hook);
        }

        @Override
        public void unregisterShutdownHook(Runnable hook) {
            hooks.remove(hook);
        }

        int shutdownHookCount() {
            return hooks.size();
        }

        void fireShutdown() {
            for (Runnable hook : List.copyOf(hooks)) {
                hook.run();
            }
        }
    }

    private static final class TestPlatformLogger implements PlatformLogger {
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
}
