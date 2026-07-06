package dev.ua.theroer.magicutils.config;

import dev.ua.theroer.magicutils.config.annotations.ConfigFile;
import dev.ua.theroer.magicutils.config.annotations.ConfigReloadable;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.annotations.MaxValue;
import dev.ua.theroer.magicutils.config.annotations.MinValue;
import dev.ua.theroer.magicutils.config.annotations.SaveTo;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ListenerSubscription;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void subscriptionStopsChangeCallbacksAfterClose() throws IOException {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager manager = new ConfigManager(platform);
        try {
            PlainChangeConfig config = manager.register(PlainChangeConfig.class);
            AtomicInteger calls = new AtomicInteger();
            ListenerSubscription subscription = manager.subscribeChanges(PlainChangeConfig.class,
                    (updated, sections) -> calls.incrementAndGet());
            manager.shutdown();

            Files.writeString(tempDir.resolve("plain-change.json"),
                    "{\n  \"value\" : \"updated-once\"\n}\n");
            manager.reload(PlainChangeConfig.class);

            assertEquals("updated-once", config.value);
            assertEquals(1, calls.get());

            subscription.close();

            Files.writeString(tempDir.resolve("plain-change.json"),
                    "{\n  \"value\" : \"updated-twice\"\n}\n");
            manager.reload(PlainChangeConfig.class);

            assertEquals("updated-twice", config.value);
            assertEquals(1, calls.get());
        } finally {
            manager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void queuedExternalReloadDoesNotRunAfterShutdown() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager manager = new ConfigManager(platform);
        try {
            PlainChangeConfig config = manager.register(PlainChangeConfig.class);
            AtomicInteger calls = new AtomicInteger();
            manager.subscribeChanges(PlainChangeConfig.class, (updated, sections) -> calls.incrementAndGet());

            DeferredExecutor deferredExecutor = new DeferredExecutor();
            setPrivateField(manager, "reloadExecutor", deferredExecutor);
            Object entry = invokePrivate(manager, "getEntry", new Class<?>[] { Object.class }, config);
            invokePrivate(manager, "scheduleExternalReload", new Class<?>[] { entry.getClass() }, entry);

            manager.shutdown();
            deferredExecutor.runCaptured();

            assertEquals("plain-default", config.value);
            assertEquals(0, calls.get());
        } finally {
            manager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void reloadRetriesTransientConcurrentModificationForMaps() throws IOException {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager manager = new ConfigManager(platform);
        try {
            RetryMapConfig config = manager.register(RetryMapConfig.class);
            config.messages = new FailOnceMap<>(Map.of("hello", "world"));

            Files.writeString(tempDir.resolve("retry-map.json"),
                    "{\n  \"messages\" : null\n}\n");

            manager.reload(config);

            assertEquals(Map.of("hello", "world"), config.messages);
        } finally {
            manager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void reloadRetriesTransientConcurrentModificationForLists() throws IOException {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager manager = new ConfigManager(platform);
        try {
            RetryListConfig config = manager.register(RetryListConfig.class);
            config.items = new FailOnceList<>(List.of("one", "two"));

            Files.writeString(tempDir.resolve("retry-list.json"),
                    "{\n  \"items\" : null\n}\n");

            manager.reload(config);

            assertEquals(List.of("one", "two"), config.items);
        } finally {
            manager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void clampsMinMaxOnPlainConfigValueFields() throws IOException {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager manager = new ConfigManager(platform);
        try {
            BoundedConfig config = manager.register(BoundedConfig.class);

            Files.writeString(tempDir.resolve("bounded.json"),
                    "{\n  \"volume\" : 250,\n  \"depth\" : -5\n}\n");

            manager.reload(config);

            assertEquals(100, config.volume);
            assertEquals(0, config.depth);
        } finally {
            manager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void writesAndReadsSaveToSideFile() throws IOException {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager manager = new ConfigManager(platform);
        try {
            SaveToConfig config = manager.register(SaveToConfig.class);

            String main = Files.readString(tempDir.resolve("saveto-main.json"));
            assertTrue(main.contains("\"inline\""), main);
            assertFalse(main.contains("\"secret\""), main);

            Path sideFile = tempDir.resolve("secrets.json");
            assertTrue(Files.exists(sideFile), "side file should be created");
            assertTrue(Files.readString(sideFile).contains("\"secret\""));

            Files.writeString(sideFile, "{\n  \"secret\" : \"from-side-file\"\n}\n");
            manager.reload(config);

            assertEquals("from-side-file", config.secret);
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

    @ConfigFile("plain-change.json")
    static final class PlainChangeConfig {
        @ConfigValue("value")
        String value = "plain-default";
    }

    @ConfigFile("retry-map.json")
    static final class RetryMapConfig {
        @ConfigValue("messages")
        Map<String, String> messages = new LinkedHashMap<>(Map.of("default", "value"));
    }

    @ConfigFile("retry-list.json")
    static final class RetryListConfig {
        @ConfigValue("items")
        List<String> items = new ArrayList<>(List.of("default"));
    }

    @ConfigFile("bounded.json")
    static final class BoundedConfig {
        @ConfigValue("volume")
        @MaxValue(100)
        int volume = 50;

        @ConfigValue("depth")
        @MinValue(0)
        int depth = 10;
    }

    @ConfigFile("saveto-main.json")
    static final class SaveToConfig {
        @ConfigValue("inline")
        String inline = "in-main";

        @ConfigValue("secret")
        @SaveTo("secrets.json")
        String secret = "secret-default";
    }

    private static final class FailOnceMap<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;
        private final AtomicInteger remainingFailures = new AtomicInteger(1);

        private FailOnceMap(Map<? extends K, ? extends V> source) {
            super(source);
        }

        @Override
        public void forEach(java.util.function.BiConsumer<? super K, ? super V> action) {
            if (remainingFailures.getAndDecrement() > 0) {
                throw new ConcurrentModificationException("transient map iteration failure");
            }
            super.forEach(action);
        }

        @Override
        public Set<Map.Entry<K, V>> entrySet() {
            Set<Map.Entry<K, V>> delegate = super.entrySet();
            return new AbstractSet<>() {
                @Override
                public Iterator<Map.Entry<K, V>> iterator() {
                    if (remainingFailures.getAndDecrement() > 0) {
                        throw new ConcurrentModificationException("transient map entrySet failure");
                    }
                    return delegate.iterator();
                }

                @Override
                public int size() {
                    return delegate.size();
                }
            };
        }
    }

    private static final class FailOnceList<E> extends ArrayList<E> {
        private static final long serialVersionUID = 1L;
        private final AtomicInteger remainingFailures = new AtomicInteger(1);

        private FailOnceList(List<? extends E> source) {
            super(source);
        }

        @Override
        public Iterator<E> iterator() {
            if (remainingFailures.getAndDecrement() > 0) {
                throw new ConcurrentModificationException("transient list iteration failure");
            }
            return super.iterator();
        }

        @Override
        public Object[] toArray() {
            if (remainingFailures.getAndDecrement() > 0) {
                throw new ConcurrentModificationException("transient list snapshot failure");
            }
            return super.toArray();
        }
    }

    private static Object invokePrivate(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args
    ) throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class DeferredExecutor extends AbstractExecutorService {
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        void runCaptured() {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
            }
        }
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
