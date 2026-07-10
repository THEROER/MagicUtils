package dev.ua.theroer.magicutils.bootstrap;

import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.lang.Messages;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageBootstrapTest {
    private static final String SCOPE = "LanguageBootstrapTest";

    @TempDir
    Path tempDir;

    private final List<ConfigManager> openManagers = new java.util.ArrayList<>();

    @AfterEach
    void resetGlobalState() {
        // apply()/hooks mutate the global Messages singleton; keep tests isolated.
        Messages.unregister(SCOPE);
        Messages.setLanguageManager(null);
        // Release config watchers/handles so @TempDir cleanup can delete the dir.
        for (ConfigManager manager : openManagers) {
            manager.shutdown();
        }
        openManagers.clear();
    }

    @Test
    void recommendedDefaultsKeepEveryFlagEnabled() {
        LanguageBootstrap lang = new LanguageBootstrap().withRecommendedDefaults();

        assertTrue(lang.bindsClientLocaleSync());
        assertTrue(lang.registersMessages());
        assertTrue(lang.setsMessagesManager());
    }

    @Test
    void minimalDisablesEveryFlag() {
        LanguageBootstrap lang = new LanguageBootstrap().minimal();

        assertFalse(lang.bindsClientLocaleSync());
        assertFalse(lang.registersMessages());
        assertFalse(lang.setsMessagesManager());
    }

    @Test
    void applyWithDefaultsRegistersScopeAndGlobalManager() throws IOException {
        Fixture fixture = newFixture("defaults");
        LanguageManager manager = fixture.languageManager();

        new LanguageBootstrap().apply(SCOPE, manager, fixture.logger());

        assertSame(manager, Messages.getLanguageManager(SCOPE));
        assertSame(manager, Messages.getLanguageManager());
        assertSame(manager, fixture.logger().getLanguageManager());
    }

    @Test
    void applyMinimalSkipsMessagesAndLoggerWiring() throws IOException {
        Fixture fixture = newFixture("minimal");
        LanguageManager manager = fixture.languageManager();

        new LanguageBootstrap().minimal().apply(SCOPE, manager, fixture.logger());

        assertNull(Messages.getLanguageManager(SCOPE));
        assertNull(Messages.getLanguageManager());
    }

    @Test
    void closeHooksRunWhenFlagsEnabled() throws IOException {
        Fixture fixture = newFixture("close-hooks");
        LanguageManager manager = fixture.languageManager();
        LanguageBootstrap lang = new LanguageBootstrap();
        lang.apply(SCOPE, manager, fixture.logger());

        try (MagicRuntime runtime = MagicRuntime.builder(
                        fixture.platform(), fixture.configManager(), fixture.logger())
                .manageConfigManager(false)
                .autoRegisterShutdown(false)
                .build()) {
            lang.installMessagesCloseHooks(runtime, SCOPE, manager);

            assertSame(manager, Messages.getLanguageManager(SCOPE));
            runtime.close();

            assertNull(Messages.getLanguageManager(SCOPE));
            assertNull(Messages.getLanguageManager());
        }
    }

    private Fixture newFixture(String name) throws IOException {
        Path directory = Files.createDirectories(tempDir.resolve(name));
        TestPlatform platform = new TestPlatform(directory);
        ConfigManager configManager = new ConfigManager(platform);
        openManagers.add(configManager);
        LoggerCore logger = new LoggerCore(platform, configManager, this, SCOPE);
        LanguageManager manager = new LanguageManager(platform, configManager);
        return new Fixture(platform, configManager, logger, manager);
    }

    private record Fixture(TestPlatform platform,
                           ConfigManager configManager,
                           LoggerCore logger,
                           LanguageManager languageManager) {
    }

    private static final class TestPlatform implements Platform {
        private final Path configDir;
        private final PlatformLogger logger = new NoopPlatformLogger();

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
    }

    private static final class NoopPlatformLogger implements PlatformLogger {
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
