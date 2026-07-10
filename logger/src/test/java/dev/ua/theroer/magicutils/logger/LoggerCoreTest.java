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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
            configManager.shutdown();
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

    @Test
    void structuredConsoleAudienceKeepsLevelAndSubLoggerWithoutPlainTextParsing() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager configManager = new ConfigManager(platform);
        try {
            LoggerCore core = new LoggerCore(platform, configManager, new Object(), "TestPlugin");
            configManager.shutdown();
            setConsolePrefixModeNone(core);

            core.debug().send("plain debug");

            ConsoleDelivery debugDelivery = platform.consoleAudience.lastDelivery();
            assertNotNull(debugDelivery);
            assertEquals(LogLevel.DEBUG, debugDelivery.metadata().level());
            // Console component should contain the message without prefix
            String debugText = PlainTextComponentSerializer.plainText().serialize(debugDelivery.component());
            assertTrue(debugText.contains("plain debug"));
            // Console component should NOT contain prefix text
            assertTrue(!debugText.contains("[TP"), "Console component should not contain prefix");

            PrefixedLoggerCore commands = core.withPrefix("Commands", "[Commands]");
            commands.debug().send("permission check");

            ConsoleDelivery commandDelivery = platform.consoleAudience.lastDelivery();
            assertNotNull(commandDelivery);
            assertEquals(LogLevel.DEBUG, commandDelivery.metadata().level());
            assertEquals("Commands", commandDelivery.metadata().subLoggerName());
            // Console component should contain only the message, no prefix
            String cmdText = PlainTextComponentSerializer.plainText().serialize(commandDelivery.component());
            assertTrue(cmdText.contains("permission check"));
            assertTrue(!cmdText.contains("[Commands]"), "Console component should not contain sub-logger prefix");
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void chatPrefixCarriesBrandButNotTheLogLevel() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager configManager = new ConfigManager(platform);
        try {
            LoggerCore core = new LoggerCore(platform, configManager, new Object(), "TestPlugin");
            configManager.shutdown();

            // Chat defaults to FULL, so the brand appears; the level must not.
            for (LogLevel level : new LogLevel[] {LogLevel.SUCCESS, LogLevel.WARN, LogLevel.ERROR, LogLevel.INFO}) {
                LogMessageFormatter.FormattedMessage formatted = LogMessageFormatter.formatDetailed(
                        core, "hello", level, LogTarget.CHAT, null, null, null);
                String chat = PlainTextComponentSerializer.plainText().serialize(formatted.chatComponent());

                assertTrue(chat.contains("[TestPlugin]"),
                        "Chat prefix should keep the plugin brand for " + level + ", was: " + chat);
                assertTrue(!chat.contains(level.name()),
                        "Chat prefix should not spell out the log level " + level + ", was: " + chat);
                assertTrue(chat.contains("hello"), "Chat message content should be present for " + level);
            }
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    @Test
    void chatCarriesLevelColourEvenWithGradientsDisabled() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ConfigManager configManager = new ConfigManager(platform);
        try {
            LoggerCore core = new LoggerCore(platform, configManager, new Object(), "TestPlugin");
            configManager.shutdown();
            setChatGradient(core, false);

            String success = chatDownsampledColour(core, LogLevel.SUCCESS);
            String error = chatDownsampledColour(core, LogLevel.ERROR);

            // With gradients off, a single solid level colour must still be applied,
            // and SUCCESS must not read as ERROR.
            assertNotNull(success, "SUCCESS chat message should carry a colour");
            assertNotNull(error, "ERROR chat message should carry a colour");
            assertTrue(!success.equals(error),
                    "SUCCESS and ERROR chat messages should not share the same colour");
        } finally {
            configManager.shutdown();
            platform.shutdown();
        }
    }

    private static String chatDownsampledColour(LoggerCore core, LogLevel level) {
        LogMessageFormatter.FormattedMessage formatted = LogMessageFormatter.formatDetailed(
                core, "hello", level, LogTarget.CHAT, null, null, null);
        return findFirstColour(formatted.chatComponent());
    }

    private static String findFirstColour(Component component) {
        if (component.color() != null) {
            return component.color().asHexString();
        }
        for (Component child : component.children()) {
            String childColour = findFirstColour(child);
            if (childColour != null) {
                return childColour;
            }
        }
        return null;
    }

    private static void setChatGradient(LoggerCore core, boolean enabled) throws Exception {
        Field prefixField = core.getConfig().getClass().getDeclaredField("prefix");
        prefixField.setAccessible(true);
        Object prefixSettings = prefixField.get(core.getConfig());
        Field gradientField = prefixSettings.getClass().getDeclaredField("useGradientChat");
        gradientField.setAccessible(true);
        gradientField.setBoolean(prefixSettings, enabled);
    }

    private static void setDebugPlaceholders(LoggerCore core, boolean enabled) throws Exception {
        Field field = core.getConfig().getClass().getDeclaredField("debugPlaceholders");
        field.setAccessible(true);
        field.setBoolean(core.getConfig(), enabled);

        Method method = LoggerCore.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);
        method.invoke(core);
    }

    private static void setConsolePrefixModeNone(LoggerCore core) throws Exception {
        Field prefixField = core.getConfig().getClass().getDeclaredField("prefix");
        prefixField.setAccessible(true);
        Object prefixSettings = prefixField.get(core.getConfig());
        Field consoleModeField = prefixSettings.getClass().getDeclaredField("consoleMode");
        consoleModeField.setAccessible(true);
        consoleModeField.set(prefixSettings, PrefixMode.NONE.name());
    }

    private static final class TestPlatform implements Platform, ConfigFormatProvider {
        private final Path configDir;
        private final CapturingLogger logger = new CapturingLogger();
        private final CapturingConsoleAudience consoleAudience = new CapturingConsoleAudience();
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
            return consoleAudience;
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

    private record ConsoleDelivery(Component component, ConsoleMessageMetadata metadata) {
    }

    private static final class CapturingConsoleAudience implements StructuredConsoleAudience {
        private final List<ConsoleDelivery> deliveries = new ArrayList<>();

        @Override
        public void send(Component component) {
        }

        @Override
        public void sendConsole(Component component, ConsoleMessageMetadata metadata) {
            deliveries.add(new ConsoleDelivery(component, metadata));
        }

        private ConsoleDelivery lastDelivery() {
            return deliveries.isEmpty() ? null : deliveries.get(deliveries.size() - 1);
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

}
