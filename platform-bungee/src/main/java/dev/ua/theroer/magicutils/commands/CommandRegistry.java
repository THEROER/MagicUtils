package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerCore;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Static registry for BungeeCord commands.
 */
public final class CommandRegistry {
    private static final AtomicBoolean ADAPTER_REGISTERED = new AtomicBoolean(false);
    private static final Map<Object, CommandRegistry> REGISTRIES = new IdentityHashMap<>();
    private static volatile CommandRegistry defaultRegistry;

    private final ProxyServer proxy;
    private final Plugin plugin;
    private final PrefixedLoggerCore logger;
    private final String permissionPrefix;
    private final CommandManager<CommandSender> commandManager;
    private final Executor asyncExecutor;
    private final Map<String, BungeeCommandWrapper> registeredCommands = new ConcurrentHashMap<>();

    private CommandRegistry(ProxyServer proxy,
                            Plugin plugin,
                            String permissionPrefix,
                            LoggerCore loggerCore,
                            @Nullable Executor asyncExecutor) {
        if (proxy == null) {
            throw new IllegalArgumentException("ProxyServer instance is required");
        }
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin instance is required");
        }
        if (loggerCore == null) {
            throw new IllegalArgumentException("LoggerCore instance is required");
        }
        this.proxy = proxy;
        this.plugin = plugin;
        this.logger = loggerCore.withPrefix("Commands", "[Commands]");
        this.permissionPrefix = permissionPrefix != null ? permissionPrefix : "";
        this.asyncExecutor = asyncExecutor != null
                ? asyncExecutor
                : runnable -> proxy.getScheduler().runAsync(plugin, runnable);

        CommandLogger commandLogger = new CommandLogger() {
            @Override
            public void debug(String message) {
                logger.debug().send(message);
            }

            @Override
            public boolean isDebugEnabled() {
                return logger.isEnabled()
                        && logger.getLogger().isLevelEnabled(dev.ua.theroer.magicutils.logger.LogLevel.DEBUG);
            }

            @Override
            public void info(String message) {
                logger.info().send(message);
            }

            @Override
            public void warn(String message) {
                logger.warn().send(message);
            }

            @Override
            public void error(String message) {
                logger.error().send(message);
            }
        };

        TypeParserRegistry<CommandSender> parserRegistry = TypeParserRegistry.createWithDefaults(commandLogger);
        this.commandManager = new CommandManager<>(
                this.permissionPrefix,
                plugin.getDescription().getName().toLowerCase(Locale.ROOT),
                commandLogger,
                new BungeeCommandPlatform(proxy, commandLogger),
                parserRegistry
        );

        registerMagicSenderAdapter();
        logger.debug().send("Bungee command registry initialized successfully");
    }

    /**
     * Initializes the default command registry.
     *
     * @param proxy Bungee proxy
     * @param plugin Bungee plugin
     * @param permissionPrefix permission prefix
     * @param loggerCore logger core
     */
    public static void initialize(ProxyServer proxy,
                                  Plugin plugin,
                                  String permissionPrefix,
                                  LoggerCore loggerCore) {
        initialize(proxy, plugin, permissionPrefix, loggerCore, null);
    }

    /**
     * Initializes the default command registry with custom executor.
     *
     * @param proxy Bungee proxy
     * @param plugin Bungee plugin
     * @param permissionPrefix permission prefix
     * @param loggerCore logger core
     * @param asyncExecutor async executor
     */
    public static void initialize(ProxyServer proxy,
                                  Plugin plugin,
                                  String permissionPrefix,
                                  LoggerCore loggerCore,
                                  @Nullable Executor asyncExecutor) {
        createDefault(proxy, plugin, permissionPrefix, loggerCore, asyncExecutor);
    }

    /**
     * Creates a new command registry.
     *
     * @param proxy Bungee proxy
     * @param plugin Bungee plugin
     * @param permissionPrefix permission prefix
     * @param loggerCore logger core
     * @return new registry
     */
    public static CommandRegistry create(ProxyServer proxy,
                                         Plugin plugin,
                                         String permissionPrefix,
                                         LoggerCore loggerCore) {
        return create(proxy, plugin, permissionPrefix, loggerCore, null, false);
    }

    /**
     * Creates a new command registry with custom executor.
     *
     * @param proxy Bungee proxy
     * @param plugin Bungee plugin
     * @param permissionPrefix permission prefix
     * @param loggerCore logger core
     * @param asyncExecutor async executor
     * @return new registry
     */
    public static CommandRegistry create(ProxyServer proxy,
                                         Plugin plugin,
                                         String permissionPrefix,
                                         LoggerCore loggerCore,
                                         @Nullable Executor asyncExecutor) {
        return create(proxy, plugin, permissionPrefix, loggerCore, asyncExecutor, false);
    }

    /**
     * Creates and sets the default command registry.
     *
     * @param proxy Bungee proxy
     * @param plugin Bungee plugin
     * @param permissionPrefix permission prefix
     * @param loggerCore logger core
     * @return new default registry
     */
    public static CommandRegistry createDefault(ProxyServer proxy,
                                                Plugin plugin,
                                                String permissionPrefix,
                                                LoggerCore loggerCore) {
        return create(proxy, plugin, permissionPrefix, loggerCore, null, true);
    }

    /**
     * Creates and sets the default command registry with custom executor.
     *
     * @param proxy Bungee proxy
     * @param plugin Bungee plugin
     * @param permissionPrefix permission prefix
     * @param loggerCore logger core
     * @param asyncExecutor async executor
     * @return new default registry
     */
    public static CommandRegistry createDefault(ProxyServer proxy,
                                                Plugin plugin,
                                                String permissionPrefix,
                                                LoggerCore loggerCore,
                                                @Nullable Executor asyncExecutor) {
        return create(proxy, plugin, permissionPrefix, loggerCore, asyncExecutor, true);
    }

    private static synchronized CommandRegistry create(ProxyServer proxy,
                                                       Plugin plugin,
                                                       String permissionPrefix,
                                                       LoggerCore loggerCore,
                                                       @Nullable Executor asyncExecutor,
                                                       boolean makeDefault) {
        CommandRegistry registry = new CommandRegistry(proxy, plugin, permissionPrefix, loggerCore, asyncExecutor);
        REGISTRIES.put(plugin, registry);
        if (makeDefault || defaultRegistry == null) {
            defaultRegistry = registry;
        }
        return registry;
    }

    /**
     * Shuts down the command registry for a plugin.
     *
     * @param plugin Bungee plugin
     */
    public static synchronized void shutdown(Object plugin) {
        if (plugin == null) {
            return;
        }
        CommandRegistry removed = REGISTRIES.remove(plugin);
        if (removed != null) {
            removed.unregisterAll();
            if (removed == defaultRegistry) {
                defaultRegistry = null;
            }
        }
    }

    /**
     * Clears all registries and unregisters all commands.
     */
    public static synchronized void clearRegistries() {
        for (CommandRegistry registry : REGISTRIES.values()) {
            registry.unregisterAll();
        }
        REGISTRIES.clear();
        defaultRegistry = null;
    }

    /**
     * Gets the command registry for a plugin.
     *
     * @param plugin Bungee plugin
     * @return registry or null
     */
    public static synchronized @Nullable CommandRegistry get(Object plugin) {
        if (plugin == null) {
            return null;
        }
        return REGISTRIES.get(plugin);
    }

    /**
     * Returns whether the default registry is initialized.
     *
     * @return true if initialized
     */
    public static synchronized boolean isInitialized() {
        return defaultRegistry != null && defaultRegistry.initialized();
    }

    /**
     * Returns whether the registry for a plugin is initialized.
     *
     * @param plugin Bungee plugin
     * @return true if initialized
     */
    public static synchronized boolean isInitialized(Object plugin) {
        CommandRegistry registry = get(plugin);
        return registry != null && registry.initialized();
    }

    /**
     * Gets the command manager from the default registry.
     *
     * @return command manager or null
     */
    public static @Nullable CommandManager<CommandSender> getCommandManager() {
        CommandRegistry registry = defaultRegistry;
        return registry != null ? registry.commandManager : null;
    }

    /**
     * Gets the command manager for a plugin.
     *
     * @param plugin Bungee plugin
     * @return command manager or null
     */
    public static @Nullable CommandManager<CommandSender> getCommandManager(Object plugin) {
        CommandRegistry registry = get(plugin);
        return registry != null ? registry.commandManager : null;
    }

    /**
     * Registers all commands for a plugin.
     *
     * @param plugin Bungee plugin
     * @param commands commands to register
     */
    public static void registerAll(Object plugin, MagicCommand... commands) {
        require(plugin).registerAllCommands(commands);
    }

    /**
     * Registers all command specs for a plugin.
     *
     * @param plugin Bungee plugin
     * @param specs specs to register
     */
    public static void registerAll(Object plugin, CommandSpec<?>... specs) {
        require(plugin).registerAllSpecs(specs);
    }

    /**
     * Registers a command for a plugin.
     *
     * @param plugin Bungee plugin
     * @param command command to register
     * @param extraAliases extra aliases
     */
    public static void register(Object plugin, MagicCommand command, String... extraAliases) {
        require(plugin).registerCommand(command, extraAliases);
    }

    /**
     * Registers a command spec for a plugin.
     *
     * @param plugin Bungee plugin
     * @param spec spec to register
     * @param extraAliases extra aliases
     */
    public static void register(Object plugin, CommandSpec<?> spec, String... extraAliases) {
        require(plugin).registerSpec(spec, extraAliases);
    }

    /**
     * Registers all commands in the default registry.
     *
     * @param commands commands to register
     */
    public static void registerAll(MagicCommand... commands) {
        requireDefault().registerAllCommands(commands);
    }

    /**
     * Registers all command specs in the default registry.
     *
     * @param specs specs to register
     */
    public static void registerAll(CommandSpec<?>... specs) {
        requireDefault().registerAllSpecs(specs);
    }

    /**
     * Registers a command in the default registry.
     *
     * @param command command to register
     * @param extraAliases extra aliases
     */
    public static void register(MagicCommand command, String... extraAliases) {
        requireDefault().registerCommand(command, extraAliases);
    }

    /**
     * Registers a command spec in the default registry.
     *
     * @param spec spec to register
     * @param extraAliases extra aliases
     */
    public static void register(CommandSpec<?> spec, String... extraAliases) {
        requireDefault().registerSpec(spec, extraAliases);
    }

    /**
     * Unregisters a command from the default registry.
     *
     * @param commandName command name
     * @return true if unregistered
     */
    public static boolean unregister(String commandName) {
        return requireDefault().unregisterCommand(commandName);
    }

    /**
     * Unregisters a command from a plugin registry.
     *
     * @param plugin Bungee plugin
     * @param commandName command name
     * @return true if unregistered
     */
    public static boolean unregister(Object plugin, String commandName) {
        return require(plugin).unregisterCommand(commandName);
    }

    /**
     * Registers multiple commands.
     *
     * @param commands commands
     */
    public void registerAllCommands(MagicCommand... commands) {
        if (commands == null) {
            return;
        }
        for (MagicCommand command : commands) {
            registerCommand(command);
        }
    }

    /**
     * Registers multiple command specs.
     *
     * @param specs specs
     */
    public void registerAllSpecs(CommandSpec<?>... specs) {
        if (specs == null) {
            return;
        }
        for (CommandSpec<?> spec : specs) {
            registerSpec(spec);
        }
    }

    /**
     * Registers a command with extra aliases.
     *
     * @param command command
     * @param extraAliases extra aliases
     */
    public void registerCommand(MagicCommand command, String... extraAliases) {
        if (command == null) {
            return;
        }
        CommandInfo info = command.overrideInfo(command.getClass().getAnnotation(CommandInfo.class));
        if (info == null) {
            throw new IllegalArgumentException(
                    InternalMessages.ERR_MISSING_COMMANDINFO.get("class", command.getClass().getName())
            );
        }
        commandManager.register(command, info);

        Set<String> labels = new LinkedHashSet<>();
        labels.add(info.name().toLowerCase(Locale.ROOT));
        if (info.aliases() != null) {
            Arrays.stream(info.aliases())
                    .filter(alias -> alias != null && !alias.isBlank())
                    .map(alias -> alias.toLowerCase(Locale.ROOT))
                    .forEach(labels::add);
        }
        if (extraAliases != null) {
            Arrays.stream(extraAliases)
                    .filter(alias -> alias != null && !alias.isBlank())
                    .map(alias -> alias.toLowerCase(Locale.ROOT))
                    .forEach(labels::add);
        }

        String primaryLabel = labels.iterator().next();
        String[] aliases = labels.stream()
                .skip(1)
                .toArray(String[]::new);
        BungeeCommandWrapper wrapper = new BungeeCommandWrapper(
                commandManager,
                primaryLabel,
                aliases,
                asyncExecutor
        );
        proxy.getPluginManager().registerCommand(plugin, wrapper);
        for (String label : labels) {
            registeredCommands.put(label, wrapper);
            logger.debug().send("Bungee command alias registered: " + label + " -> " + primaryLabel);
        }
    }

    /**
     * Registers a command.
     *
     * @param command command
     */
    public void registerCommand(MagicCommand command) {
        registerCommand(command, new String[0]);
    }

    /**
     * Registers a command spec with extra aliases.
     *
     * @param spec spec
     * @param extraAliases extra aliases
     */
    public void registerSpec(CommandSpec<?> spec, String... extraAliases) {
        if (spec == null) {
            return;
        }
        registerCommand(MagicCommand.fromSpec(spec), extraAliases);
    }

    /**
     * Registers a command spec.
     *
     * @param spec spec
     */
    public void registerSpec(CommandSpec<?> spec) {
        registerSpec(spec, new String[0]);
    }

    /**
     * Unregisters a command.
     *
     * @param commandName command name
     * @return true if unregistered
     */
    public boolean unregisterCommand(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return false;
        }
        String normalized = commandName.toLowerCase(Locale.ROOT);
        BungeeCommandWrapper wrapper = registeredCommands.remove(normalized);
        if (wrapper == null) {
            return false;
        }
        registeredCommands.entrySet().removeIf(entry -> entry.getValue() == wrapper);
        proxy.getPluginManager().unregisterCommand(wrapper);
        return true;
    }

    /**
     * Returns whether the registry is initialized.
     *
     * @return true if initialized
     */
    public boolean initialized() {
        return proxy != null && plugin != null && commandManager != null;
    }

    /**
     * Gets the command manager.
     *
     * @return command manager
     */
    public @NotNull CommandManager<CommandSender> commandManager() {
        return commandManager;
    }

    /**
     * Gets the Bungee proxy server.
     *
     * @return proxy server
     */
    public @NotNull ProxyServer proxy() {
        return proxy;
    }

    /**
     * Gets the Bungee plugin.
     *
     * @return plugin
     */
    public @NotNull Plugin plugin() {
        return plugin;
    }

    /**
     * Gets the permission prefix.
     *
     * @return permission prefix
     */
    public String permissionPrefix() {
        return permissionPrefix;
    }

    private void unregisterAll() {
        Set<BungeeCommandWrapper> wrappers = Set.copyOf(registeredCommands.values());
        registeredCommands.clear();
        for (BungeeCommandWrapper wrapper : wrappers) {
            proxy.getPluginManager().unregisterCommand(wrapper);
        }
    }

    private static synchronized CommandRegistry requireDefault() {
        if (defaultRegistry == null) {
            throw new IllegalStateException(InternalMessages.ERR_REGISTRY_NOT_INITIALIZED.get());
        }
        return defaultRegistry;
    }

    private static synchronized CommandRegistry require(Object plugin) {
        CommandRegistry registry = get(plugin);
        if (registry == null) {
            throw new IllegalStateException(InternalMessages.ERR_REGISTRY_NOT_INITIALIZED.get());
        }
        return registry;
    }

    private static void registerMagicSenderAdapter() {
        if (!ADAPTER_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        MagicSenderAdapters.register("bungee", new MagicSenderAdapter() {
            @Override
            public boolean supports(Object sender) {
                return sender instanceof CommandSender;
            }

            @Override
            public MagicSender wrap(Object sender) {
                if (!(sender instanceof CommandSender commandSender)) {
                    return null;
                }
                return BungeeCommandPlatform.wrapMagicSender(commandSender, ProxyServer.getInstance());
            }
        });
    }
}
