package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.config.annotations.ConfigFile;
import dev.ua.theroer.magicutils.config.annotations.ConfigReloadable;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.ShutdownHookRegistrar;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicRuntimeBindingTest {
    @TempDir
    Path tempDir;

    @Test
    void resourceSwapUpdatesNamedComponentsAndClosesPreviousValue() throws Exception {
        try (TestContext context = newContext("resource-swap")) {
            MagicRuntime runtime = newRuntime(context);
            TestCloseable first = new TestCloseable("first");
            MagicRuntimeResource<TestCloseable> resource = runtime.resource("http.monitoring", first);

            assertSame(first, resource.require());
            assertSame(first, runtime.requireNamedComponent("http.monitoring", TestCloseable.class));

            TestCloseable second = new TestCloseable("second");
            resource.set(second);

            assertTrue(first.closed);
            assertFalse(second.closed);
            assertSame(second, resource.require());
            assertSame(second, runtime.requireNamedComponent("http.monitoring", TestCloseable.class));

            resource.close();

            assertTrue(second.closed);
            assertTrue(runtime.findNamedComponent("http.monitoring", TestCloseable.class).isEmpty());
        }
    }

    @Test
    void configBindingRebuildsOnMatchingSectionsAndStopsAfterClose() throws Exception {
        try (TestContext context = newContext("config-binding")) {
            MagicRuntime runtime = newRuntime(context);
            MagicRuntimeConfigBinding<ServiceConfig, TestCloseable> binding = runtime.bindConfig(
                    "service.client",
                    ServiceConfig.class,
                    config -> new TestCloseable(config.service.baseUrl),
                    "service"
            );
            context.configManager().shutdown();

            TestCloseable initial = binding.require();
            assertEquals("https://initial.example", initial.value);
            assertSame(initial, runtime.requireNamedComponent("service.client", TestCloseable.class));

            Files.writeString(tempDir.resolve("config-binding/service.json"),
                    "{\n" +
                            "  \"service\" : { \"baseUrl\" : \"https://updated.example\" },\n" +
                            "  \"feature\" : { \"enabled\" : true }\n" +
                            "}\n");

            context.configManager().reload(ServiceConfig.class, "feature");

            assertSame(initial, binding.require());
            assertFalse(initial.closed);

            context.configManager().reload(ServiceConfig.class, "service");

            TestCloseable updated = binding.require();
            assertEquals("https://updated.example", updated.value);
            assertTrue(initial.closed);
            assertSame(updated, runtime.requireNamedComponent("service.client", TestCloseable.class));

            binding.close();

            assertTrue(updated.closed);
            assertTrue(binding.current().isEmpty());
            assertTrue(runtime.findNamedComponent("service.client", TestCloseable.class).isEmpty());

            Files.writeString(tempDir.resolve("config-binding/service.json"),
                    "{\n" +
                            "  \"service\" : { \"baseUrl\" : \"https://third.example\" },\n" +
                            "  \"feature\" : { \"enabled\" : false }\n" +
                            "}\n");

            context.configManager().reload(ServiceConfig.class, "service");

            assertTrue(binding.current().isEmpty());
        }
    }

    private MagicRuntime newRuntime(TestContext context) {
        return MagicRuntime.builder(context.platform(), context.configManager(), context.logger())
                .manageConfigManager(false)
                .autoRegisterShutdown(false)
                .build();
    }

    private TestContext newContext(String name) throws IOException {
        Path directory = Files.createDirectories(tempDir.resolve(name));
        TestPlatform platform = new TestPlatform(directory);
        ConfigManager configManager = new ConfigManager(platform);
        LoggerCore logger = new LoggerCore(platform, configManager, this, "MagicRuntimeBindingTest");
        return new TestContext(platform, configManager, logger);
    }

    @ConfigFile("service.json")
    @ConfigReloadable(sections = { "service", "feature" })
    public static final class ServiceConfig {
        @ConfigSection("service")
        ServiceSection service = new ServiceSection();

        @ConfigSection("feature")
        FeatureSection feature = new FeatureSection();
    }

    public static final class ServiceSection {
        @ConfigValue("baseUrl")
        String baseUrl = "https://initial.example";
    }

    public static final class FeatureSection {
        @ConfigValue("enabled")
        boolean enabled = false;
    }

    private static final class TestCloseable implements AutoCloseable {
        private final String value;
        private boolean closed;

        private TestCloseable(String value) {
            this.value = value;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private record TestContext(TestPlatform platform,
                               ConfigManager configManager,
                               LoggerCore logger) implements AutoCloseable {
        @Override
        public void close() {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    private static final class TestPlatform implements Platform, ShutdownHookRegistrar {
        private final Path configDir;
        private final TaskScheduler scheduler = new DirectTaskScheduler();
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

        @Override
        public void registerShutdownHook(Runnable hook) {
            hooks.add(hook);
        }

        @Override
        public void unregisterShutdownHook(Runnable hook) {
            hooks.remove(hook);
        }

        private void shutdown() {
            scheduler.shutdown();
            hooks.clear();
        }
    }

    private static final class DirectTaskScheduler implements TaskScheduler {
        private final Executor directExecutor = Runnable::run;
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "runtime-binding-test");
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
