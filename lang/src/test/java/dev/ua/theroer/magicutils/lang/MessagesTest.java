package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanupRegistry() throws Exception {
        clearMessagesRegistry();
    }

    @Test
    void registerNormalizesScopeAndScopedViewUsesScopedManager() throws Exception {
        try (TestEnv defaultEnv = TestEnv.create(tempDir.resolve("default"));
             TestEnv scopedEnv = TestEnv.create(tempDir.resolve("scoped"))) {

            defaultEnv.manager.registerTranslations("en", Map.of("plugin.welcome", "Hi"));
            defaultEnv.manager.init("en");
            scopedEnv.manager.registerTranslations("en", Map.of("plugin.welcome", "ScopedHi"));
            scopedEnv.manager.init("en");

            Messages.setLanguageManager(defaultEnv.manager);
            Messages.register(" PluginA ", scopedEnv.manager);

            assertSame(defaultEnv.manager, Messages.getLanguageManager());
            assertSame(scopedEnv.manager, Messages.getLanguageManager("plugina"));
            assertEquals("Hi", Messages.getRaw("plugin.welcome"));
            assertEquals("ScopedHi", Messages.view(" PLUGINA ").getRaw("plugin.welcome"));
        }
    }

    @Test
    void unregisterDefaultScopeFallsBackToRawKey() throws Exception {
        try (TestEnv env = TestEnv.create(tempDir.resolve("default"))) {
            env.manager.init("en");
            Messages.setLanguageManager(env.manager);

            Messages.unregister(" default ");

            assertNull(Messages.getLanguageManager());
            assertEquals("missing.key", Messages.getRaw("missing.key"));
            assertEquals("missing.key", Messages.view("default").getRaw("missing.key"));
        }
    }

    @Test
    void getRawWithVarargsAppliesPlaceholders() throws Exception {
        try (TestEnv env = TestEnv.create(tempDir.resolve("default"))) {
            env.manager.registerTranslations("en", Map.of("greet", "Hello {name}, score {score}"));
            env.manager.init("en");
            Messages.setLanguageManager(env.manager);

            assertEquals("Hello Alice, score 42",
                    Messages.getRaw(null, "greet", "name", "Alice", "score", "42"));
            assertEquals("Hello Bob, score 7",
                    Messages.getRaw(null, "greet", Map.of("name", "Bob", "score", "7")));
        }
    }

    @Test
    void getEscapesPlaceholderValuesIntoComponent() throws Exception {
        try (TestEnv env = TestEnv.create(tempDir.resolve("default"))) {
            env.manager.registerTranslations("en", Map.of("warn", "<red>{msg}</red>"));
            env.manager.init("en");
            Messages.setLanguageManager(env.manager);

            Component component = Messages.get(null, "warn", "msg", "<bold>injected</bold>");

            assertNotNull(component);
            // The escaped placeholder must be present as literal text in the
            // component's flattened content, not parsed as a <bold> child.
            StringBuilder flattened = new StringBuilder();
            component.iterator(net.kyori.adventure.text.ComponentIteratorType.DEPTH_FIRST)
                    .forEachRemaining(node -> {
                        if (node instanceof net.kyori.adventure.text.TextComponent text) {
                            flattened.append(text.content());
                        }
                    });
            assertTrue(flattened.toString().contains("<bold>injected</bold>"),
                    "Escaped tags must appear as literal text, got: " + flattened);
        }
    }

    @Test
    void isOverrideDetectsConfiguredCustomValue() throws Exception {
        try (TestEnv env = TestEnv.create(tempDir.resolve("default"))) {
            env.manager.init("en");
            Messages.setLanguageManager(env.manager);

            String englishDefault = BundledTranslations.getTranslations("en")
                    .get("magicutils.commands.no_permission");
            assertNotNull(englishDefault);

            assertTrue(Messages.isOverride("custom value", "magicutils.commands.no_permission"));
            assertEquals(false, Messages.isOverride(englishDefault, "magicutils.commands.no_permission"));
            assertEquals(false, Messages.isOverride(null, "magicutils.commands.no_permission"));
            assertEquals(false, Messages.isOverride("", "magicutils.commands.no_permission"));
        }
    }

    @Test
    void resolveOverridePicksConfiguredWhenSet() throws Exception {
        try (TestEnv env = TestEnv.create(tempDir.resolve("default"))) {
            env.manager.registerTranslations("en", Map.of("greet", "Hi {name}"));
            env.manager.init("en");
            Messages.setLanguageManager(env.manager);

            assertEquals("Custom Alice",
                    Messages.resolveOverride(null, "Custom {name}", "greet", "name", "Alice"));
            assertEquals("Hi Alice",
                    Messages.resolveOverride(null, null, "greet", "name", "Alice"));
        }
    }

    @Test
    void englishUltimateFallbackResolvesBundledKey() throws Exception {
        try (TestEnv env = TestEnv.create(tempDir.resolve("default"))) {
            env.manager.init("uk");
            Messages.setLanguageManager(env.manager);

            String englishDefault = BundledTranslations.getTranslations("en")
                    .get("magicutils.commands.no_permission");
            String resolved = Messages.getRaw("magicutils.commands.no_permission");

            assertNotNull(englishDefault);
            assertNotNull(resolved);
            assertTrue(resolved.length() > 0);
            assertTrue(!resolved.equals("magicutils.commands.no_permission"),
                    "English fallback must resolve the key");
        }
    }

    private static void clearMessagesRegistry() throws Exception {
        Field managersField = Messages.class.getDeclaredField("LANGUAGE_MANAGERS");
        managersField.setAccessible(true);
        ((Map<?, ?>) managersField.get(null)).clear();

        Field managerField = Messages.class.getDeclaredField("languageManager");
        managerField.setAccessible(true);
        managerField.set(null, null);
    }

    private static final class TestEnv implements AutoCloseable {
        final TestPlatform platform;
        final ConfigManager configManager;
        final LanguageManager manager;

        private TestEnv(TestPlatform platform, ConfigManager configManager, LanguageManager manager) {
            this.platform = platform;
            this.configManager = configManager;
            this.manager = manager;
        }

        static TestEnv create(Path configDir) throws Exception {
            java.nio.file.Files.createDirectories(configDir);
            TestPlatform platform = new TestPlatform(configDir);
            ConfigManager configManager = new ConfigManager(platform);
            LanguageManager manager = new LanguageManager(platform, configManager);
            return new TestEnv(platform, configManager, manager);
        }

        @Override
        public void close() {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    private static final class TestPlatform implements Platform, ConfigFormatProvider {
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

    private static final class DirectTaskScheduler implements TaskScheduler {
        private final Executor directExecutor = Runnable::run;
        private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "messages-test-timer");
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
        public UUID id() {
            return null;
        }

        @Override
        public void send(Component component) {
        }
    }
}
