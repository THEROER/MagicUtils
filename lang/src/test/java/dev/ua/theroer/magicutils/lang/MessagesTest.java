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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MessagesTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanupRegistry() throws Exception {
        clearMessagesRegistry();
    }

    @Test
    void registerNormalizesScopeAndScopedViewUsesScopedManager() throws Exception {
        try (TestLanguageManager defaultManager = TestLanguageManager.create(
                tempDir.resolve("default"), "default", "en");
             TestLanguageManager scopedManager = TestLanguageManager.create(
                     tempDir.resolve("scoped"), "scoped", "uk")) {
            Messages.setLanguageManager(defaultManager);
            Messages.register(" PluginA ", scopedManager);

            assertSame(defaultManager, Messages.getLanguageManager());
            assertSame(scopedManager, Messages.getLanguageManager("plugina"));
            assertEquals("default:sample.key", Messages.getRaw("sample.key"));
            assertEquals("scoped:sample.key", Messages.view(" PLUGINA ").getRaw("sample.key"));
        }
    }

    @Test
    void unregisterDefaultScopeClearsLegacyManagerAndFallsBackToRawKey() throws Exception {
        try (TestLanguageManager defaultManager = TestLanguageManager.create(
                tempDir.resolve("default"), "default", "en")) {
            Messages.setLanguageManager(defaultManager);

            Messages.unregister(" default ");

            assertNull(Messages.getLanguageManager());
            assertNull(Messages.getLanguageManager("default"));
            assertEquals("missing.key", Messages.getRaw("missing.key"));
            assertEquals("missing.key", Messages.view("default").getRaw("missing.key"));
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

    private static final class TestLanguageManager extends LanguageManager implements AutoCloseable {
        private final TestPlatform platform;
        private final ConfigManager configManager;
        private final String prefix;
        private final String languageCode;

        private TestLanguageManager(TestPlatform platform,
                                    ConfigManager configManager,
                                    String prefix,
                                    String languageCode) {
            super(platform, configManager);
            this.platform = platform;
            this.configManager = configManager;
            this.prefix = prefix;
            this.languageCode = languageCode;
        }

        private static TestLanguageManager create(Path configDir, String prefix, String languageCode) throws Exception {
            Files.createDirectories(configDir);
            TestPlatform platform = new TestPlatform(configDir);
            ConfigManager configManager = new ConfigManager(platform);
            return new TestLanguageManager(platform, configManager, prefix, languageCode);
        }

        @Override
        public String getMessage(String key) {
            return prefix + ":" + key;
        }

        @Override
        public String getMessage(String key, Map<String, String> placeholders) {
            return prefix + ":" + key;
        }

        @Override
        public String getMessage(String key, String... replacements) {
            return prefix + ":" + key;
        }

        @Override
        public boolean hasMessage(String key) {
            return true;
        }

        @Override
        public String getMessageForLanguage(String languageCode, String key) {
            return prefix + ":" + languageCode + ":" + key;
        }

        @Override
        public String getMessageForLanguage(String languageCode, String key, Map<String, String> placeholders) {
            return prefix + ":" + languageCode + ":" + key;
        }

        @Override
        public String getMessageForLanguage(String languageCode, String key, String... replacements) {
            return prefix + ":" + languageCode + ":" + key;
        }

        @Override
        public String getMessageEscaped(String key, Map<String, String> placeholders) {
            return prefix + ":" + key;
        }

        @Override
        public String getMessageEscaped(String key, String... replacements) {
            return prefix + ":" + key;
        }

        @Override
        public String getPlayerLanguage(UUID playerId) {
            return languageCode;
        }

        @Override
        public String getMessageForAudience(Audience audience, String key) {
            return prefix + ":" + key;
        }

        @Override
        public String getMessageForAudience(Audience audience, String key, Map<String, String> placeholders) {
            return prefix + ":" + key;
        }

        @Override
        public String getMessageForAudience(Audience audience, String key, String... replacements) {
            return prefix + ":" + key;
        }

        @Override
        public String getMessageForAudienceEscaped(Audience audience, String key, Map<String, String> placeholders) {
            return prefix + ":" + key;
        }

        @Override
        public String getMessageForAudienceEscaped(Audience audience, String key, String... replacements) {
            return prefix + ":" + key;
        }

        @Override
        public String getCurrentLanguage() {
            return languageCode;
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
        public void send(Component component) {
        }
    }
}
