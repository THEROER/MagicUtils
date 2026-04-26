package dev.ua.theroer.magicutils.commands;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.config.ConfigManager;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.platform.ConfigFormatProvider;
import dev.ua.theroer.magicutils.platform.Platform;
import dev.ua.theroer.magicutils.platform.PlatformLogger;
import dev.ua.theroer.magicutils.platform.TaskScheduler;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRegistryIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void registerUsesUniqueLabelsThroughDefaultRegistry() {
        try (TestHarness harness = new TestHarness(tempDir)) {
            CommandRegistry.register(new DemoCommand(), "alias", "extra");

            assertEquals(Set.of("demo", "alias", "extra"), harness.velocityCommandManager.registeredLabels());
            assertEquals(3, harness.velocityCommandManager.registeredCommands.size());
            assertTrue(harness.velocityCommandManager.registeredCommands.values().stream()
                    .allMatch(VelocityCommandWrapper.class::isInstance));

            Object wrapper = harness.velocityCommandManager.registeredCommands.get("demo");
            assertSame(wrapper, harness.velocityCommandManager.registeredCommands.get("alias"));
            assertSame(wrapper, harness.velocityCommandManager.registeredCommands.get("extra"));
        }
    }

    @Test
    void unregisterAndShutdownUpdateRegistryState() {
        try (TestHarness harness = new TestHarness(tempDir)) {
            CommandRegistry.register(new DemoCommand());

            assertTrue(CommandRegistry.isInitialized());
            assertTrue(CommandRegistry.isInitialized(harness.plugin));
            assertNotNull(CommandRegistry.getCommandManager());
            assertNotNull(CommandRegistry.getCommandManager(harness.plugin));

            assertTrue(CommandRegistry.unregister("demo"));
            assertEquals(List.of("demo"), harness.velocityCommandManager.unregisteredLabels);

            CommandRegistry.shutdown(harness.plugin);

            assertFalse(CommandRegistry.isInitialized());
            assertFalse(CommandRegistry.isInitialized(harness.plugin));
            assertNull(CommandRegistry.getCommandManager());
            assertNull(CommandRegistry.getCommandManager(harness.plugin));
            assertThrows(IllegalStateException.class, () -> CommandRegistry.register(new DemoCommand()));
        }
    }

    @Test
    void createDefaultRegistersVelocitySenderAdapter() {
        try (TestHarness harness = new TestHarness(tempDir)) {
            CommandSource source = commandSource(Set.of("perm.node"));
            assertSame(harness.registry.commandManager(), CommandRegistry.getCommandManager());

            MagicSender sender = MagicSenderAdapters.wrap(source);

            assertNotNull(sender);
            assertSame(source, sender.handle());
            assertTrue(MagicSenderAdapters.hasPermission(source, "perm.node"));
            assertFalse(MagicSenderAdapters.hasPermission(source, "missing.node"));
        }
    }

    @CommandInfo(name = "demo", aliases = {"alias"})
    private static final class DemoCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute() {
            return CommandResult.success("ok");
        }
    }

    private static CommandSource commandSource(Set<String> permissions) {
        return (CommandSource) Proxy.newProxyInstance(
                CommandSource.class.getClassLoader(),
                new Class<?>[]{CommandSource.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "RegistryTestCommandSource";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if ("getPermissionValue".equals(method.getName())) {
                        return permissions.contains(args[0]) ? Tristate.TRUE : Tristate.UNDEFINED;
                    }
                    if ("hasPermission".equals(method.getName())) {
                        return permissions.contains(args[0]);
                    }
                    return null;
                }
        );
    }

    private static final class TestHarness implements AutoCloseable {
        private final TestPlatform platform;
        private final ConfigManager configManager;
        private final LoggerCore loggerCore;
        private final RecordingVelocityCommandManager velocityCommandManager = new RecordingVelocityCommandManager();
        private final Object plugin = new TestPlugin();
        private final CommandRegistry registry;
        private boolean closed;

        private TestHarness(Path tempDir) {
            CommandRegistry.clearRegistries();
            this.platform = new TestPlatform(tempDir);
            this.configManager = new ConfigManager(platform);
            this.loggerCore = new LoggerCore(platform, configManager, plugin, "VelocityTest");
            this.registry = CommandRegistry.createDefault(
                    proxyServer(velocityCommandManager),
                    plugin,
                    "",
                    loggerCore,
                    Runnable::run
            );
            assertNotNull(registry);
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
        }
    }

    private static ProxyServer proxyServer(RecordingVelocityCommandManager commandManager) {
        return (ProxyServer) Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "TestProxyServer";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if ("getCommandManager".equals(method.getName())) {
                        return commandManager;
                    }
                    if (method.getReturnType() == Optional.class) {
                        return Optional.empty();
                    }
                    if (Collection.class.isAssignableFrom(method.getReturnType())) {
                        return List.of();
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    return null;
                }
        );
    }

    private static final class RecordingVelocityCommandManager implements com.velocitypowered.api.command.CommandManager {
        private final Map<String, Command> registeredCommands = new LinkedHashMap<>();
        private final Map<String, CommandMeta> registeredMeta = new LinkedHashMap<>();
        private final List<String> unregisteredLabels = new ArrayList<>();

        @Override
        public CommandMeta.Builder metaBuilder(String alias) {
            return new TestCommandMetaBuilder(alias);
        }

        @Override
        public CommandMeta.Builder metaBuilder(BrigadierCommand command) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public void register(BrigadierCommand command) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public void register(CommandMeta meta, Command command) {
            for (String alias : meta.getAliases()) {
                registeredMeta.put(alias, meta);
                registeredCommands.put(alias, command);
            }
        }

        @Override
        public void unregister(String alias) {
            unregisteredLabels.add(alias);
            registeredMeta.remove(alias);
            registeredCommands.remove(alias);
        }

        @Override
        public void unregister(CommandMeta meta) {
            for (String alias : meta.getAliases()) {
                unregister(alias);
            }
        }

        @Override
        public CommandMeta getCommandMeta(String alias) {
            return registeredMeta.get(alias);
        }

        @Override
        public CompletableFuture<Boolean> executeAsync(CommandSource source, String commandLine) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> executeImmediatelyAsync(CommandSource source, String commandLine) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public Collection<String> getAliases() {
            return new ArrayList<>(registeredCommands.keySet());
        }

        @Override
        public boolean hasCommand(String alias) {
            return registeredCommands.containsKey(alias);
        }

        private Set<String> registeredLabels() {
            return new LinkedHashSet<>(registeredCommands.keySet());
        }
    }

    private static final class TestCommandMetaBuilder implements CommandMeta.Builder {
        private final LinkedHashSet<String> aliases = new LinkedHashSet<>();
        private Object plugin;

        private TestCommandMetaBuilder(String alias) {
            aliases.add(alias);
        }

        @Override
        public CommandMeta.Builder aliases(String... aliases) {
            if (aliases != null) {
                Collections.addAll(this.aliases, aliases);
            }
            return this;
        }

        @Override
        public CommandMeta.Builder hint(com.mojang.brigadier.tree.CommandNode<CommandSource> node) {
            return this;
        }

        @Override
        public CommandMeta.Builder plugin(Object plugin) {
            this.plugin = plugin;
            return this;
        }

        @Override
        public CommandMeta build() {
            return new TestCommandMeta(aliases, plugin);
        }
    }

    private static final class TestCommandMeta implements CommandMeta {
        private final Collection<String> aliases;
        private final Object plugin;

        private TestCommandMeta(Collection<String> aliases, Object plugin) {
            this.aliases = List.copyOf(aliases);
            this.plugin = plugin;
        }

        @Override
        public Collection<String> getAliases() {
            return aliases;
        }

        @Override
        public Collection<com.mojang.brigadier.tree.CommandNode<CommandSource>> getHints() {
            return List.of();
        }

        @Override
        public Object getPlugin() {
            return plugin;
        }
    }

    private static final class TestPlugin {
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
            Thread thread = new Thread(r, "command-registry-test-timer");
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
