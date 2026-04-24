package dev.ua.theroer.magicutils.lang;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.PlayerLifecycle;
import dev.ua.theroer.magicutils.platform.PlayerLifecycleListener;
import dev.ua.theroer.magicutils.platform.PlayerLifecycleType;
import dev.ua.theroer.magicutils.platform.PlayerLocale;
import dev.ua.theroer.magicutils.platform.PlayerLocaleListener;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageManagerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    @Test
    void registerTranslationsAppliesToAlreadyLoadedLanguage() {
        TestPlatform platform = new TestPlatform(tempDir, null);
        ConfigManager configManager = new ConfigManager(platform);
        try {
            LanguageManager manager = new LanguageManager(platform, configManager);
            manager.init("en");

            manager.registerTranslations("en", Map.of(
                    "plugin.welcome", "<green>Hello</green>"
            ));

            assertEquals("<green>Hello</green>", manager.getMessage("plugin.welcome"));
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void registerTranslationsAppliesToLanguageLoadedLater() {
        TestPlatform platform = new TestPlatform(tempDir, null);
        ConfigManager configManager = new ConfigManager(platform);
        try {
            LanguageManager manager = new LanguageManager(platform, configManager);
            manager.init("en");

            manager.registerTranslations(Map.of(
                    "uk", Map.of("plugin.welcome", "<green>Привіт</green>")
            ));

            assertTrue(manager.loadLanguage("uk"));
            assertEquals("<green>Привіт</green>", manager.getMessageForLanguage("uk", "plugin.welcome"));
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void registerTranslationsDoesNotOverrideExistingCustomMessage() {
        TestPlatform platform = new TestPlatform(tempDir, null);
        ConfigManager configManager = new ConfigManager(platform);
        try {
            LanguageManager manager = new LanguageManager(platform, configManager);
            manager.init("en");
            manager.putCustomMessage("en", "plugin.welcome", "custom");

            manager.registerTranslations("en", Map.of(
                    "plugin.welcome", "default"
            ));

            assertEquals("custom", manager.getMessage("plugin.welcome"));
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void registerTranslationsPersistsGeneratedLanguageFiles() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir, null);
        ConfigManager configManager = new ConfigManager(platform);
        try {
            LanguageManager manager = new LanguageManager(platform, configManager);
            manager.registerTranslations(Map.of(
                    "en", Map.of("plugin.welcome", "<green>Hello</green>"),
                    "uk", Map.of("plugin.welcome", "<green>Привіт</green>")
            ));
            manager.init("en");
            assertTrue(manager.loadLanguage("uk"));

            Path englishFile = tempDir.resolve("lang/en.json");
            Path ukrainianFile = tempDir.resolve("lang/uk.json");

            assertTrue(Files.exists(englishFile));
            assertTrue(Files.exists(ukrainianFile));
            assertEquals(
                    "<green>Hello</green>",
                    OBJECT_MAPPER.readTree(Files.readString(englishFile))
                            .path("messages")
                            .path("plugin")
                            .path("welcome")
                            .asText()
            );
            assertEquals(
                    "<green>Привіт</green>",
                    OBJECT_MAPPER.readTree(Files.readString(ukrainianFile))
                            .path("messages")
                            .path("plugin")
                            .path("welcome")
                            .asText()
            );
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void autoDetectedLanguageResolvesLocaleTags() {
        TestPlatform platform = new TestPlatform(tempDir, null);
        ConfigManager configManager = new ConfigManager(platform);
        try {
            LanguageManager manager = new LanguageManager(platform, configManager);
            manager.init("en");

            UUID playerId = UUID.randomUUID();
            assertTrue(manager.setAutoDetectedPlayerLanguage(playerId, "uk_UA"));

            assertEquals("uk", manager.getPlayerLanguage(playerId));
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void manualOverrideWinsOverAutoDetectedLanguage() {
        TestPlatform platform = new TestPlatform(tempDir, null);
        ConfigManager configManager = new ConfigManager(platform);
        try {
            LanguageManager manager = new LanguageManager(platform, configManager);
            manager.init("en");

            UUID playerId = UUID.randomUUID();
            assertTrue(manager.setAutoDetectedPlayerLanguage(playerId, "uk_UA"));
            assertTrue(manager.setPlayerLanguage(playerId, "en"));

            assertEquals("en", manager.getPlayerLanguage(playerId));
            assertEquals("uk", manager.getAutoDetectedPlayerLanguages().get(playerId));
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void bindClientLocaleSyncAppliesLocalesAndClearsOnLeave() {
        TestPlatform platform = new TestPlatform(tempDir, null);
        ConfigManager configManager = new ConfigManager(platform);
        try {
            LanguageManager manager = new LanguageManager(platform, configManager);
            manager.init("en");

            UUID playerId = UUID.randomUUID();
            ListenerSubscription subscription = manager.bindClientLocaleSync(platform);
            try {
                platform.firePlayerLocale(new PlayerLocale(playerId, "Alice", "uk_UA"));
                assertEquals("uk", manager.getPlayerLanguage(playerId));

                platform.firePlayerLifecycle(new PlayerLifecycle(playerId, "Alice", PlayerLifecycleType.LEAVE));
                assertEquals("en", manager.getPlayerLanguage(playerId));
            } finally {
                subscription.close();
            }
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    private static final class TestPlatform implements Platform, ConfigFormatProvider {
        private final Path configDir;
        private final RuntimeException dispatchFailure;
        private final TaskScheduler scheduler = new DirectTaskScheduler();
        private final CopyOnWriteArrayList<PlayerLifecycleListener> playerLifecycleListeners = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<PlayerLocaleListener> playerLocaleListeners = new CopyOnWriteArrayList<>();

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
        public ListenerSubscription subscribePlayerLifecycle(PlayerLifecycleListener listener) {
            if (listener == null) {
                return ListenerSubscription.noop();
            }
            playerLifecycleListeners.add(listener);
            return () -> playerLifecycleListeners.remove(listener);
        }

        @Override
        public ListenerSubscription subscribePlayerLocales(PlayerLocaleListener listener) {
            if (listener == null) {
                return ListenerSubscription.noop();
            }
            playerLocaleListeners.add(listener);
            return () -> playerLocaleListeners.remove(listener);
        }

        @Override
        public String defaultConfigExtension() {
            return "json";
        }

        private void shutdown() {
            scheduler.shutdown();
        }

        private void firePlayerLifecycle(PlayerLifecycle lifecycle) {
            for (PlayerLifecycleListener listener : playerLifecycleListeners) {
                listener.onPlayerLifecycle(lifecycle);
            }
        }

        private void firePlayerLocale(PlayerLocale playerLocale) {
            for (PlayerLocaleListener listener : playerLocaleListeners) {
                listener.onPlayerLocale(playerLocale);
            }
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
