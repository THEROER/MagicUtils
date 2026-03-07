package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.ConfigFile;
import dev.ua.theroer.magicutils.config.annotations.ConfigReloadable;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerRegressionTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndReloadsInheritedConfigFields() throws IOException {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager manager = new ConfigManager(platform);
        try {
            InheritedConfig config = manager.register(InheritedConfig.class);

            String initial = Files.readString(tempDir.resolve("inherited.json"));
            assertTrue(initial.contains("\"base\""), initial);
            assertTrue(initial.contains("\"child\""), initial);

            Files.writeString(tempDir.resolve("inherited.json"),
                    "{\n  \"base\" : \"from-disk\",\n  \"child\" : \"child-updated\"\n}\n");

            manager.reload(config);

            assertEquals("from-disk", config.base);
            assertEquals("child-updated", config.child);
        } finally {
            manager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void preservesMixedListsWhenSavingToml() {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager manager = new ConfigManager(platform);
        try {
            MixedTomlConfig config = manager.register(MixedTomlConfig.class);
            manager.save(config);

            String toml = Files.readString(tempDir.resolve("mixed-list.toml"));
            assertTrue(toml.contains("\"items\" = [") || toml.contains("items = ["), toml);
            assertTrue(toml.contains("\"plain\""), toml);
            assertTrue(toml.contains("\"k\" = \"v\"") || toml.contains("k = \"v\""), toml);

            config.items = new ArrayList<>();
            manager.reload(config);

            assertEquals(2, config.items.size());
            assertEquals("plain", config.items.get(0));
            assertInstanceOf(Map.class, config.items.get(1));
            assertEquals("v", ((Map<?, ?>) config.items.get(1)).get("k"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            manager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void reloadsOnlyRequestedSections() throws IOException {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager manager = new ConfigManager(platform);
        try {
            SectionReloadConfig config = manager.register(SectionReloadConfig.class);

            manager.shutdown();
            Files.writeString(tempDir.resolve("section-reload.json"),
                    "{\n" +
                            "  \"database\" : { \"url\" : \"jdbc:test-updated\" },\n" +
                            "  \"feature\" : { \"enabled\" : true }\n" +
                            "}\n");

            manager.reload(SectionReloadConfig.class, "database");

            assertEquals("jdbc:test-updated", config.database.url);
            assertFalse(config.feature.enabled);
        } finally {
            manager.shutdown();
            platform.shutdown();
        }
    }

    private static class BaseConfig {
        @ConfigValue("base")
        String base = "base-default";
    }

    @ConfigFile("inherited.json")
    static final class InheritedConfig extends BaseConfig {
        @ConfigValue("child")
        String child = "child-default";
    }

    @ConfigFile("mixed-list.toml")
    static final class MixedTomlConfig {
        @ConfigValue("items")
        List<Object> items = new ArrayList<>(List.of("plain", Map.of("k", "v")));
    }

    @ConfigFile("section-reload.json")
    @ConfigReloadable(sections = { "database", "feature" })
    static final class SectionReloadConfig {
        @ConfigSection("database")
        DatabaseSection database = new DatabaseSection();

        @ConfigSection("feature")
        FeatureSection feature = new FeatureSection();
    }

    static final class DatabaseSection {
        @ConfigValue("url")
        String url = "jdbc:test-default";
    }

    static final class FeatureSection {
        @ConfigValue("enabled")
        boolean enabled = false;
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
            Thread thread = new Thread(r, "config-test-timer");
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
