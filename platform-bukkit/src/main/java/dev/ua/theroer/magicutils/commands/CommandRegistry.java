package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.parsers.LanguageKeyTypeParser;
import dev.ua.theroer.magicutils.commands.parsers.OfflinePlayerTypeParser;
import dev.ua.theroer.magicutils.commands.parsers.PlayerTypeParser;
import dev.ua.theroer.magicutils.commands.parsers.WorldTypeParser;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;

import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles registration and initialization of commands in the plugin.
 */
public class CommandRegistry {
    private static final Map<String, CommandRegistry> REGISTRIES = new ConcurrentHashMap<>();
    private static final AtomicBoolean ADAPTER_REGISTERED = new AtomicBoolean();
    private static volatile CommandRegistry defaultRegistry;

    private final PrefixedLogger logger;
    private final Logger messageLogger;
    private final CommandManager<CommandSender> commandManager;
    private final BukkitCommandPlatform platform;
    private final CommandMap commandMap;
    private final JavaPlugin plugin;
    private final String permissionPrefix;

    private CommandRegistry(JavaPlugin plugin, String permissionPrefix, Logger loggerInstance) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin instance is required");
        }
        if (loggerInstance == null) {
            throw new IllegalArgumentException("Logger instance is required");
        }
        this.plugin = plugin;
        this.permissionPrefix = permissionPrefix != null ? permissionPrefix : "";
        this.logger = loggerInstance.withPrefix("Commands", "[Commands]");
        this.messageLogger = loggerInstance;

        CommandLogger commandLogger = new CommandLogger() {
            @Override
            public void debug(String message) {
                logger.debug(message);
            }

            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void warn(String message) {
                logger.warn(message);
            }

            @Override
            public void error(String message) {
                logger.error(message);
            }
        };

        this.platform = new BukkitCommandPlatform(commandLogger);

        TypeParserRegistry<CommandSender> parserRegistry = TypeParserRegistry.createWithDefaults(commandLogger);
        parserRegistry.register(new PlayerTypeParser(logger));
        parserRegistry.register(new OfflinePlayerTypeParser(logger));
        parserRegistry.register(new WorldTypeParser(logger));
        LanguageKeyTypeParser.setPlugin(plugin);
        parserRegistry.register(new LanguageKeyTypeParser());

        this.commandManager = new CommandManager<>(this.permissionPrefix, plugin.getName(),
                commandLogger, platform, parserRegistry);

        registerMagicSenderAdapter();
        this.commandMap = resolveCommandMap();
        logger.info("Command registry initialized successfully");
    }

    /**
     * Initializes the command registry with the plugin and permission prefix.
     *
     * @param plugin           the JavaPlugin instance
     * @param permissionPrefix the prefix for permissions
     * @param loggerInstance   logger instance for command output
     */
    public static void initialize(JavaPlugin plugin, String permissionPrefix, Logger loggerInstance) {
        createDefault(plugin, permissionPrefix, loggerInstance);
    }

    /**
     * Creates a registry instance without replacing the default registry.
     *
     * @param plugin           the JavaPlugin instance
     * @param permissionPrefix the prefix for permissions
     * @param loggerInstance   logger instance for command output
     * @return registry instance
     */
    public static CommandRegistry create(JavaPlugin plugin, String permissionPrefix, Logger loggerInstance) {
        return create(plugin, permissionPrefix, loggerInstance, false);
    }

    /**
     * Creates a registry instance and sets it as default.
     *
     * @param plugin           the JavaPlugin instance
     * @param permissionPrefix the prefix for permissions
     * @param loggerInstance   logger instance for command output
     * @return registry instance
     */
    public static CommandRegistry createDefault(JavaPlugin plugin, String permissionPrefix, Logger loggerInstance) {
        return create(plugin, permissionPrefix, loggerInstance, true);
    }

    private static CommandRegistry create(JavaPlugin plugin,
            String permissionPrefix,
            Logger loggerInstance,
            boolean makeDefault) {
        CommandRegistry registry = new CommandRegistry(plugin, permissionPrefix, loggerInstance);
        REGISTRIES.put(registryKey(plugin), registry);
        if (makeDefault || defaultRegistry == null) {
            defaultRegistry = registry;
        }
        return registry;
    }

    /**
     * Returns the registry instance for a plugin.
     *
     * @param plugin plugin instance
     * @return registry or null
     */
    public static CommandRegistry get(JavaPlugin plugin) {
        if (plugin == null) {
            return null;
        }
        return REGISTRIES.get(registryKey(plugin));
    }

    /**
     * Returns the registry instance by plugin name.
     *
     * @param pluginName plugin name
     * @return registry or null
     */
    public static CommandRegistry get(String pluginName) {
        if (pluginName == null) {
            return null;
        }
        return REGISTRIES.get(pluginName.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Registers multiple commands at once.
     *
     * @param commands the commands to register
     */
    public static void registerAll(MagicCommand... commands) {
        requireDefault().registerAllCommands(commands);
    }

    /**
     * Registers multiple builder-defined commands at once.
     *
     * @param specs command specs to register
     */
    public static void registerAll(CommandSpec<?>... specs) {
        requireDefault().registerAllSpecs(specs);
    }

    /**
     * Registers a single command.
     *
     * @param command the command to register
     */
    public static void register(MagicCommand command) {
        requireDefault().registerCommand(command);
    }

    /**
     * Registers a single builder-defined command.
     *
     * @param spec command spec to register
     */
    public static void register(CommandSpec<?> spec) {
        requireDefault().registerSpec(spec);
    }

    /**
     * Unregisters a command by name.
     *
     * @param commandName the command name to unregister
     * @return true if the command was successfully unregistered
     */
    public static boolean unregister(String commandName) {
        return requireDefault().unregisterCommand(commandName);
    }

    /**
     * Checks if the command registry is initialized.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return defaultRegistry != null && defaultRegistry.initialized();
    }

    /**
     * Checks if a registry is initialized for the provided plugin.
     *
     * @param plugin plugin instance
     * @return true if initialized
     */
    public static boolean isInitialized(JavaPlugin plugin) {
        CommandRegistry registry = get(plugin);
        return registry != null && registry.initialized();
    }

    /**
     * Returns the default command manager.
     *
     * @return command manager or null
     */
    public static CommandManager<CommandSender> getCommandManager() {
        CommandRegistry registry = defaultRegistry;
        return registry != null ? registry.commandManager : null;
    }

    /**
     * Returns the default plugin.
     *
     * @return plugin or null
     */
    public static JavaPlugin getPlugin() {
        CommandRegistry registry = defaultRegistry;
        return registry != null ? registry.plugin : null;
    }

    /**
     * Returns the default permission prefix.
     *
     * @return permission prefix or null
     */
    public static String getPermissionPrefix() {
        CommandRegistry registry = defaultRegistry;
        return registry != null ? registry.permissionPrefix : null;
    }

    /**
     * Returns the instance command manager.
     *
     * @return command manager
     */
    public CommandManager<CommandSender> commandManager() {
        return commandManager;
    }

    /**
     * Returns the instance plugin.
     *
     * @return plugin instance
     */
    public JavaPlugin plugin() {
        return plugin;
    }

    /**
     * Returns the instance permission prefix.
     *
     * @return permission prefix
     */
    public String permissionPrefix() {
        return permissionPrefix;
    }

    /**
     * Returns true if this registry is initialized.
     *
     * @return true if ready
     */
    public boolean initialized() {
        return commandMap != null && commandManager != null && plugin != null;
    }

    /**
     * Registers multiple commands at once.
     *
     * @param commands commands to register
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
     * Registers multiple builder-defined commands at once.
     *
     * @param specs command specs to register
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
     * Registers a single command.
     *
     * @param command command to register
     */
    public void registerCommand(MagicCommand command) {
        if (command == null) {
            return;
        }

        Class<?> clazz = command.getClass();
        CommandInfo info = command.overrideInfo(clazz.getAnnotation(CommandInfo.class));

        if (info == null) {
            throw new IllegalArgumentException(InternalMessages.ERR_MISSING_COMMANDINFO.get("class", clazz.getName()));
        }

        commandManager.register(command, info);

        String usage = commandManager.generateUsage(command, info);
        List<String> subCommandUsages = commandManager.generateSubCommandUsages(command, info);

        BukkitCommandWrapper bukkitCommand = BukkitCommandWrapper.create(
                info.name(),
                commandManager,
                Arrays.asList(info.aliases()),
                logger,
                messageLogger);

        bukkitCommand.setDescription(info.description());
        bukkitCommand.setUsage(usage);
        String commandPermission = resolvePermission(info.permission(),
                "commands." + info.name());
        if (!commandPermission.isEmpty()) {
            bukkitCommand.setPermission(commandPermission);
        }

        if (!subCommandUsages.isEmpty()) {
            StringBuilder detailedUsage = new StringBuilder(usage);
            detailedUsage.append("\n§7Available subcommands:");
            for (String subUsage : subCommandUsages) {
                detailedUsage.append("\n§8  ").append(subUsage);
            }
            bukkitCommand.setDetailedUsage(detailedUsage.toString());
        }

        unregisterIfOwned(info.name());
        boolean registered = commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), bukkitCommand);

        if (registered) {
            logger.info(InternalMessages.SYS_COMMAND_REGISTERED.get("command", info.name(),
                    "aliases", Arrays.toString(info.aliases())));
            logger.info(InternalMessages.SYS_COMMAND_USAGE.get("usage", usage));
            if (!subCommandUsages.isEmpty()) {
                logger.info(InternalMessages.SYS_SUBCOMMAND_USAGES.get());
                for (String subUsage : subCommandUsages) {
                    logger.info("  " + subUsage);
                }
            }
        } else {
            logger.warn("Failed to register command: " + info.name() + " (command may already exist)");
        }

        generatePermissions(command, info);

        for (String alias : info.aliases()) {
            String aliasUsage = usage.replace("/" + info.name(), "/" + alias);

            unregisterIfOwned(alias);
            BukkitCommandWrapper aliasCommand = BukkitCommandWrapper.create(
                    alias,
                    commandManager,
                    Arrays.asList(info.name()),
                    logger,
                    messageLogger);
            aliasCommand.setDescription(info.description());
            aliasCommand.setUsage(aliasUsage);
            if (!commandPermission.isEmpty()) {
                aliasCommand.setPermission(commandPermission);
            }

            if (!subCommandUsages.isEmpty()) {
                StringBuilder aliasDetailedUsage = new StringBuilder(aliasUsage);
                aliasDetailedUsage.append("\n§7Available subcommands:");
                for (String subUsage : subCommandUsages) {
                    String aliasSubUsage = subUsage.replace("/" + info.name(), "/" + alias);
                    aliasDetailedUsage.append("\n§8  ").append(aliasSubUsage);
                }
                aliasCommand.setDetailedUsage(aliasDetailedUsage.toString());
            }

            boolean aliasRegistered = commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), aliasCommand);
            if (aliasRegistered) {
                logger.info(InternalMessages.SYS_ALIAS_REGISTERED.get("alias", alias, "command", info.name()));
            }
        }
    }

    /**
     * Registers a single builder-defined command.
     *
     * @param spec command spec to register
     */
    public void registerSpec(CommandSpec<?> spec) {
        if (spec == null) {
            return;
        }
        registerCommand(new DynamicCommand(spec));
    }

    private void generatePermissions(MagicCommand command, CommandInfo info) {
        Set<String> permissions = new LinkedHashSet<>();
        EnumMap<MagicPermissionDefault, Integer> counts = new EnumMap<>(MagicPermissionDefault.class);

        String commandPermission = resolvePermission(info.permission(),
                "commands." + info.name());
        if (!commandPermission.isEmpty()) {
            permissions.add(commandPermission + " (" + info.permissionDefault() + ")");
            platform.ensurePermissionRegistered(commandPermission, info.permissionDefault(), info.description());
            incrementCount(counts, info.permissionDefault());
        }

        CommandManager.CommandAction<CommandSender> directAction = commandManager.getDirectAction(command, info);
        if (directAction != null) {
            registerArgumentPermissions(info.name(), null, directAction.arguments(), permissions, counts);
        }

        for (CommandManager.CommandAction<CommandSender> subInfo : commandManager.getSubCommandActions(command)) {
            String subPermission = resolvePermission(subInfo.permission(),
                    "commands." + info.name() + ".subcommand." + subInfo.permissionSegment());
            if (!subPermission.isEmpty()) {
                permissions.add(subPermission + " (" + subInfo.permissionDefault() + ")");
                String description = !subInfo.description().isEmpty() ? subInfo.description() : info.description();
                platform.ensurePermissionRegistered(subPermission, subInfo.permissionDefault(), description);
                incrementCount(counts, subInfo.permissionDefault());
            }

            registerArgumentPermissions(info.name(), subInfo.fullPath(), subInfo.arguments(), permissions, counts);
        }

        String commandWildcard = resolvePermission(null, "commands." + info.name() + ".*");
        platform.ensurePermissionRegistered(commandWildcard, info.permissionDefault(),
                "All permissions for /" + info.name());
        permissions.add(commandWildcard + " (" + info.permissionDefault() + ")");
        incrementCount(counts, info.permissionDefault());

        String subWildcard = resolvePermission(null, "commands." + info.name() + ".subcommand.*");
        platform.ensurePermissionRegistered(subWildcard, info.permissionDefault(),
                "All subcommands for /" + info.name());
        permissions.add(subWildcard + " (" + info.permissionDefault() + ")");
        incrementCount(counts, info.permissionDefault());

        if (!permissions.isEmpty()) {
            logPermissionSummary(info.name(), counts);
        }
    }

    private void registerArgumentPermissions(String commandName, String subCommandName,
            List<CommandArgument> arguments, Set<String> permissions, EnumMap<MagicPermissionDefault, Integer> counts) {
        String normalized = commandName.toLowerCase(Locale.ROOT);
        for (CommandArgument argument : arguments) {
            if (argument.isSenderParameter() || !argument.hasPermission()) {
                continue;
            }
            String fallback = buildArgumentPermission(normalized, subCommandName, argument);
            String resolved = resolvePermission(argument.getPermission(), fallback);
            if (resolved == null || resolved.isEmpty()) {
                continue;
            }
            permissions.add(resolved + " (" + argument.getPermissionDefault() + ")");
            String desc = "Argument " + argument.getName() + " for /" + commandName
                    + (subCommandName != null ? " " + subCommandName : "");
            platform.ensurePermissionRegistered(resolved, argument.getPermissionDefault(), desc);
            incrementCount(counts, argument.getPermissionDefault());
        }
    }

    private static String buildArgumentPermission(String normalizedCommandName, String subCommandName,
            CommandArgument argument) {
        StringBuilder sb = new StringBuilder("commands.").append(normalizedCommandName);
        String normalizedSub = normalizeSubCommandSegment(subCommandName);
        if (!normalizedSub.isEmpty()) {
            sb.append(".subcommand.").append(normalizedSub);
        }
        String node = argument.getPermissionNode();
        if (node == null || node.isEmpty()) {
            node = argument.getName();
        }
        if (argument.isIncludeArgumentSegment()) {
            sb.append(".argument.");
        } else {
            sb.append(".");
        }
        sb.append(node);
        return sb.toString();
    }

    private static String normalizeSubCommandSegment(String subCommandName) {
        if (subCommandName == null) {
            return "";
        }
        String trimmed = subCommandName.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.replaceAll("\\s+", ".");
    }

    private static void incrementCount(EnumMap<MagicPermissionDefault, Integer> counts,
            MagicPermissionDefault def) {
        if (def == null) {
            def = MagicPermissionDefault.OP;
        }
        counts.put(def, counts.getOrDefault(def, 0) + 1);
    }

    private void logPermissionSummary(String commandName, EnumMap<MagicPermissionDefault, Integer> counts) {
        logger.info("Permissions for /" + commandName + ":");
        counts.forEach((def, count) -> logger.info("  " + def.name() + ": " + count));
    }

    private String resolvePermission(String annotationPermission, String fallbackPermission) {
        String permission = (annotationPermission != null && !annotationPermission.isEmpty())
                ? annotationPermission
                : fallbackPermission;
        if (permission == null || permission.isEmpty()) {
            return "";
        }
        String prefix = permissionPrefix != null ? permissionPrefix : "";
        if (!prefix.isEmpty()) {
            if (permission.startsWith(prefix + ".")) {
                return permission;
            }
            if (permission.startsWith(".")) {
                return prefix + permission;
            }
            return prefix + "." + permission;
        }
        return permission.startsWith(".") ? permission.substring(1) : permission;
    }

    /**
     * Unregisters a command by name.
     *
     * @param commandName the command name to unregister
     * @return true if the command was successfully unregistered
     */
    public boolean unregisterCommand(String commandName) {
        return tryUnregister(commandName, false);
    }

    private void unregisterIfOwned(String name) {
        try {
            var existing = commandMap.getCommand(name);
            if (existing != null && isOwned(existing)) {
                tryUnregister(name, true);
            }
            var namespaced = plugin.getName().toLowerCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT);
            var existingNs = commandMap.getCommand(namespaced);
            if (existingNs != null && isOwned(existingNs)) {
                tryUnregister(name, true);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isOwned(Command cmd) {
        if (cmd instanceof PluginIdentifiableCommand pic) {
            return pic.getPlugin() == plugin;
        }
        return cmd.getClass().getSimpleName().contains("BukkitCommandWrapper");
    }

    private boolean tryUnregister(String commandName, boolean silent) {
        try {
            Map<String, Command> knownCommands = resolveKnownCommands(commandMap);
            if (knownCommands == null) {
                if (!silent) {
                    logger.warn("Failed to unregister command " + commandName + ": knownCommands not accessible");
                }
                return false;
            }

            boolean removed = false;
            removed |= knownCommands.remove(commandName.toLowerCase(Locale.ROOT)) != null;
            removed |= knownCommands.remove(
                    plugin.getName().toLowerCase(Locale.ROOT) + ":" + commandName.toLowerCase(Locale.ROOT)) != null;

            if (removed && !silent) {
                logger.info(InternalMessages.SYS_UNREGISTERED_COMMAND.get("command", commandName));
            }
            return removed;
        } catch (Exception e) {
            if (!silent) {
                logger.warn("Failed to unregister command " + commandName + ": " + e.getMessage());
            }
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> resolveKnownCommands(CommandMap map) {
        if (map == null) {
            return null;
        }
        Class<?> current = map.getClass();
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField("knownCommands");
                field.setAccessible(true);
                return (Map<String, Command>) field.get(map);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

    private static void registerMagicSenderAdapter() {
        if (!ADAPTER_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        MagicSenderAdapters.register("bukkit", new MagicSenderAdapter() {
            @Override
            public boolean supports(Object sender) {
                return sender instanceof CommandSender;
            }

            @Override
            public MagicSender wrap(Object sender) {
                return BukkitCommandPlatform.wrapMagicSender((CommandSender) sender);
            }
        });
    }

    private CommandMap resolveCommandMap() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            return (CommandMap) commandMapField.get(Bukkit.getServer());
        } catch (Exception e) {
            logger.error("Failed to initialize command registry: " + e.getMessage());
            throw new RuntimeException(InternalMessages.ERR_FAILED_GET_COMMANDMAP.get(), e);
        }
    }

    private static CommandRegistry requireDefault() {
        CommandRegistry registry = defaultRegistry;
        if (registry == null) {
            throw new IllegalStateException(InternalMessages.ERR_REGISTRY_NOT_INITIALIZED.get());
        }
        return registry;
    }

    private static String registryKey(JavaPlugin plugin) {
        return plugin.getName().toLowerCase(Locale.ROOT);
    }
}
