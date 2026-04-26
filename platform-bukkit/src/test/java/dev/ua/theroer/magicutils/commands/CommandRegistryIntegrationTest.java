package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.Permission;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import dev.ua.theroer.magicutils.lang.Messages;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRegistryIntegrationTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanupStatics() throws Exception {
        resetRegistryState();
        setBukkitServer(null);
    }

    @Test
    void registerStoresMainAliasAndPermissionsInCommandMap() throws Exception {
        try (TestHarness harness = new TestHarness(tempDir, "TestPlugin")) {
            CommandRegistry.register(new DemoCommand());

            assertTrue(CommandRegistry.isInitialized());
            assertTrue(CommandRegistry.isInitialized(harness.plugin));
            assertSame(harness.registry.commandManager(), CommandRegistry.getCommandManager());
            assertSame(harness.registry.commandManager(), CommandRegistry.getCommandManager(harness.plugin));

            assertKnownCommand(harness.commandMap, "demo");
            assertKnownCommand(harness.commandMap, "testplugin:demo");
            assertKnownCommand(harness.commandMap, "alias");
            assertKnownCommand(harness.commandMap, "testplugin:alias");
            assertInstanceOf(BukkitCommandWrapper.class, harness.commandMap.knownCommands.get("demo"));
            assertInstanceOf(BukkitCommandWrapper.class, harness.commandMap.knownCommands.get("alias"));

            assertTrue(harness.permissions.containsKey("commands.demo"));
            assertTrue(harness.permissions.containsKey("commands.demo.argument.secret"));
            assertTrue(harness.permissions.containsKey("commands.demo.*"));
            assertTrue(harness.permissions.containsKey("commands.demo.subcommand.*"));
        }
    }

    @Test
    void unregisterAndShutdownUpdateRegistryState() throws Exception {
        try (TestHarness harness = new TestHarness(tempDir, "TestPlugin")) {
            CommandRegistry.register(new DemoCommand());

            assertTrue(CommandRegistry.unregister("demo"));
            assertFalse(harness.commandMap.knownCommands.containsKey("demo"));
            assertFalse(harness.commandMap.knownCommands.containsKey("testplugin:demo"));

            CommandRegistry.shutdown(harness.plugin);

            assertFalse(CommandRegistry.isInitialized());
            assertFalse(CommandRegistry.isInitialized(harness.plugin));
            assertNull(CommandRegistry.getCommandManager());
            assertNull(CommandRegistry.getCommandManager(harness.plugin));
            assertThrows(IllegalStateException.class, () -> CommandRegistry.register(new DemoCommand()));
        }
    }

    @Test
    void createDefaultRegistersBukkitSenderAdapter() throws Exception {
        try (TestHarness harness = new TestHarness(tempDir, "TestPlugin")) {
            CommandSender sender = commandSender(Set.of("perm.node"));

            MagicSender wrapped = MagicSenderAdapters.wrap(sender);

            assertNotNull(wrapped);
            assertSame(sender, wrapped.handle());
            assertTrue(MagicSenderAdapters.hasPermission(sender, "perm.node"));
            assertFalse(MagicSenderAdapters.hasPermission(sender, "missing.node"));
            assertNotNull(CommandRegistry.get(harness.plugin));
            assertNotNull(CommandRegistry.get("testplugin"));
        }
    }

    @Test
    void bukkitWrapperReturnsTrueWhenCommandHandlesFailureResult() throws Exception {
        try (TestHarness harness = new TestHarness(tempDir, "TestPlugin")) {
            CommandRegistry.register(new FailingCommand());

            Command command = harness.commandMap.getCommand("failing");
            assertInstanceOf(BukkitCommandWrapper.class, command);

            boolean handled = command.execute(commandSender(Set.of()), "failing", new String[0]);

            assertTrue(handled);
        }
    }

    @Test
    void registerResolvesAtPrefixedDescriptionsUsingDefaultLanguage() throws Exception {
        try (TestHarness harness = new TestHarness(tempDir, "TestPlugin")) {
            LanguageManager languageManager = new LanguageManager(harness.platform, harness.configManager);
            languageManager.registerTranslations(
                    "en",
                    java.util.Map.of("commands.localized.description", "English localized description")
            );
            languageManager.registerTranslations(
                    "uk",
                    java.util.Map.of("commands.localized.description", "Український локалізований опис")
            );
            languageManager.init("en");
            Messages.register(harness.plugin.getName(), languageManager);
            try {
                CommandRegistry.register(new LocalizedDescriptionCommand());

                Command command = harness.commandMap.getCommand("localized");
                assertNotNull(command);
                assertEquals("English localized description", command.getDescription());
                assertEquals("English localized description",
                        harness.permissions.get("commands.localized").getDescription());
            } finally {
                Messages.unregister(harness.plugin.getName());
                Messages.setLanguageManager(null);
            }
        }
    }

    @Test
    void registerResolvesImplicitDescriptionKeysUsingDefaultLanguage() throws Exception {
        try (TestHarness harness = new TestHarness(tempDir, "TestPlugin")) {
            LanguageManager languageManager = new LanguageManager(harness.platform, harness.configManager);
            languageManager.registerTranslations(
                    "en",
                    java.util.Map.of("commands.implicitlocalized.description", "English implicit localized description")
            );
            languageManager.registerTranslations(
                    "uk",
                    java.util.Map.of("commands.implicitlocalized.description", "Український неявний локалізований опис")
            );
            languageManager.init("en");
            Messages.register(harness.plugin.getName(), languageManager);
            try {
                CommandRegistry.register(new ImplicitLocalizedDescriptionCommand());

                Command command = harness.commandMap.getCommand("implicitlocalized");
                assertNotNull(command);
                assertEquals("English implicit localized description", command.getDescription());
                assertEquals("English implicit localized description",
                        harness.permissions.get("commands.implicitlocalized").getDescription());
            } finally {
                Messages.unregister(harness.plugin.getName());
                Messages.setLanguageManager(null);
            }
        }
    }

    @CommandInfo(name = "demo", aliases = {"alias"})
    private static final class DemoCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute(@Permission(node = "secret") String secret) {
            return CommandResult.success(secret);
        }
    }

    @CommandInfo(name = "failing")
    private static final class FailingCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute() {
            return CommandResult.failure(false);
        }
    }

    @CommandInfo(name = "localized", description = "@commands.localized.description")
    private static final class LocalizedDescriptionCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute() {
            return CommandResult.success("ok");
        }
    }

    @CommandInfo(name = "implicitlocalized")
    private static final class ImplicitLocalizedDescriptionCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute() {
            return CommandResult.success("ok");
        }
    }

    private static final class TestHarness implements AutoCloseable {
        private final RecordingCommandMap commandMap = new RecordingCommandMap();
        private final Map<String, org.bukkit.permissions.Permission> permissions = new LinkedHashMap<>();
        private final Server previousServer;
        private final Server server;
        private final JavaPlugin plugin;
        private final TestPlatform platform;
        private final ConfigManager configManager;
        private final LoggerCore loggerCore;
        private final dev.ua.theroer.magicutils.Logger logger;
        private final CommandRegistry registry;
        private boolean closed;

        private TestHarness(Path tempDir, String pluginName) throws Exception {
            resetRegistryState();
            this.previousServer = Bukkit.getServer();
            PluginManager pluginManager = pluginManager(permissions);
            BukkitScheduler scheduler = bukkitScheduler();
            this.server = createServer(commandMap, pluginManager, scheduler, tempDir);
            setBukkitServer(server);
            this.plugin = createPlugin(server, tempDir, pluginName);
            this.platform = new TestPlatform(tempDir);
            this.configManager = new ConfigManager(platform);
            this.loggerCore = new LoggerCore(platform, configManager, plugin, pluginName);
            this.logger = fabricateLogger(loggerCore, plugin);
            this.registry = CommandRegistry.createDefault(plugin, "", logger);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            CommandRegistry.clearRegistries();
            configManager.shutdown();
            platform.shutdown();
            try {
                setBukkitServer(previousServer);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            try {
                resetRegistryState();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void assertKnownCommand(RecordingCommandMap commandMap, String label) {
        assertTrue(commandMap.knownCommands.containsKey(label), "Missing command: " + label);
    }

    private static CommandSender commandSender(Set<String> permissions) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "TestCommandSender";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    return switch (method.getName()) {
                        case "getName" -> "tester";
                        case "getServer" -> Bukkit.getServer();
                        case "hasPermission" -> permissions.contains(args[0]);
                        case "getEffectivePermissions" -> Collections.<PermissionAttachmentInfo>emptySet();
                        default -> defaultValue(method.getReturnType());
                    };
                }
        );
    }

    private static void resetRegistryState() throws Exception {
        CommandRegistry.clearRegistries();
        MagicSenderAdapters.clear();

        Field adapterRegisteredField = CommandRegistry.class.getDeclaredField("ADAPTER_REGISTERED");
        adapterRegisteredField.setAccessible(true);
        ((AtomicBoolean) adapterRegisteredField.get(null)).set(false);
    }

    private static void setBukkitServer(Server server) throws Exception {
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, server);
    }

    private static dev.ua.theroer.magicutils.Logger fabricateLogger(LoggerCore core, JavaPlugin plugin)
            throws Exception {
        sun.misc.Unsafe unsafe = unsafe();
        dev.ua.theroer.magicutils.Logger logger =
                (dev.ua.theroer.magicutils.Logger) unsafe.allocateInstance(dev.ua.theroer.magicutils.Logger.class);
        putObject(unsafe, dev.ua.theroer.magicutils.Logger.class, logger, "core", core);
        putObject(unsafe, dev.ua.theroer.magicutils.Logger.class, logger, "plugin", plugin);
        putObject(unsafe, dev.ua.theroer.magicutils.Logger.class, logger, "prefixedLoggers", new HashMap<>());
        return logger;
    }

    @SuppressWarnings("deprecation")
    private static void putObject(sun.misc.Unsafe unsafe,
                                  Class<?> type,
                                  Object target,
                                  String fieldName,
                                  Object value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        long offset = unsafe.objectFieldOffset(field);
        unsafe.putObject(target, offset, value);
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    private static JavaPlugin createPlugin(Server server, Path tempDir, String pluginName) throws Exception {
        PluginDescriptionFile description = new PluginDescriptionFile(pluginName, "1.0.0",
                TestJavaPlugin.class.getName());
        sun.misc.Unsafe unsafe = unsafe();
        TestJavaPlugin plugin = (TestJavaPlugin) unsafe.allocateInstance(TestJavaPlugin.class);
        File dataFolder = Files.createDirectories(tempDir.resolve("plugin-data")).toFile();
        File pluginFile = tempDir.resolve(pluginName + ".jar").toFile();
        putObject(unsafe, JavaPlugin.class, plugin, "server", server);
        putObject(unsafe, JavaPlugin.class, plugin, "file", pluginFile);
        putObject(unsafe, JavaPlugin.class, plugin, "description", description);
        putObject(unsafe, JavaPlugin.class, plugin, "pluginMeta", description);
        putObject(unsafe, JavaPlugin.class, plugin, "dataFolder", dataFolder);
        putObject(unsafe, JavaPlugin.class, plugin, "classLoader", TestJavaPlugin.class.getClassLoader());
        putObject(unsafe, JavaPlugin.class, plugin, "configFile", new File(dataFolder, "config.yml"));
        putObject(unsafe, JavaPlugin.class, plugin, "logger", java.util.logging.Logger.getLogger(pluginName));
        return plugin;
    }

    private static Server createServer(RecordingCommandMap commandMap,
                                       PluginManager pluginManager,
                                       BukkitScheduler scheduler,
                                       Path tempDir) throws Exception {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "TestBukkitServer";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            return switch (method.getName()) {
                case "getName" -> "TestServer";
                case "getCommandMap" -> commandMap;
                case "getPluginManager" -> pluginManager;
                case "getScheduler" -> scheduler;
                case "getLogger" -> java.util.logging.Logger.getLogger("TestServer");
                case "getPluginsFolder", "getWorldContainer", "getUpdateFolderFile" -> tempDir.toFile();
                case "getUpdateFolder" -> "update";
                case "getOnlinePlayers", "getWorlds", "matchPlayer" -> List.of();
                case "getOfflinePlayers" -> new org.bukkit.OfflinePlayer[0];
                case "getOperators", "getWhitelistedPlayers", "getIPBans", "getBannedPlayers" -> Set.of();
                case "getCommandAliases" -> Map.of();
                case "isPrimaryThread", "isStopping" -> false;
                default -> defaultValue(method.getReturnType());
            };
        };

        Class<? extends Server> type = new ByteBuddy()
                .subclass(Object.class)
                .implement(Server.class)
                .defineField("commandMap", CommandMap.class, Visibility.PUBLIC)
                .method(not(isDeclaredBy(Object.class)))
                .intercept(InvocationHandlerAdapter.of(handler))
                .make()
                .load(CommandRegistryIntegrationTest.class.getClassLoader())
                .getLoaded()
                .asSubclass(Server.class);

        Server server = type.getDeclaredConstructor().newInstance();
        Field field = type.getDeclaredField("commandMap");
        field.setAccessible(true);
        field.set(server, commandMap);
        return server;
    }

    private static PluginManager pluginManager(Map<String, org.bukkit.permissions.Permission> permissions) {
        return (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "TestPluginManager";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    return switch (method.getName()) {
                        case "getPermission" -> permissions.get(args[0]);
                        case "addPermission" -> {
                            org.bukkit.permissions.Permission permission =
                                    (org.bukkit.permissions.Permission) args[0];
                            permissions.put(permission.getName(), permission);
                            yield null;
                        }
                        case "isPluginEnabled" -> false;
                        default -> defaultValue(method.getReturnType());
                    };
                }
        );
    }

    private static BukkitScheduler bukkitScheduler() {
        return (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[]{BukkitScheduler.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "TestBukkitScheduler";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == null || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == char.class) {
            return '\0';
        }
        if (Set.class.isAssignableFrom(type)) {
            return Set.of();
        }
        if (List.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
            return List.of();
        }
        if (Map.class.isAssignableFrom(type)) {
            return Map.of();
        }
        if (type.isArray()) {
            return java.lang.reflect.Array.newInstance(type.getComponentType(), 0);
        }
        return null;
    }

    private static final class RecordingCommandMap implements CommandMap {
        private final Map<String, Command> knownCommands = new LinkedHashMap<>();

        @Override
        public void registerAll(String fallbackPrefix, List<Command> commands) {
            if (commands == null) {
                return;
            }
            for (Command command : commands) {
                register(fallbackPrefix, command);
            }
        }

        @Override
        public boolean register(String label, String fallbackPrefix, Command command) {
            if (command == null) {
                return false;
            }
            String normalized = normalize(label);
            knownCommands.put(normalized, command);
            knownCommands.put(normalize(fallbackPrefix) + ":" + normalized, command);
            return true;
        }

        @Override
        public boolean register(String fallbackPrefix, Command command) {
            return register(command != null ? command.getName() : null, fallbackPrefix, command);
        }

        @Override
        public boolean dispatch(CommandSender sender, String commandLine) {
            return false;
        }

        @Override
        public void clearCommands() {
            knownCommands.clear();
        }

        @Override
        public Command getCommand(String name) {
            return knownCommands.get(normalize(name));
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String cmdLine) {
            return List.of();
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String cmdLine, org.bukkit.Location location) {
            return List.of();
        }

        @Override
        public Map<String, Command> getKnownCommands() {
            return knownCommands;
        }

        private static String normalize(String value) {
            return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final class TestJavaPlugin extends JavaPlugin {
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
            return false;
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
            Thread thread = new Thread(r, "bukkit-command-registry-test-timer");
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
