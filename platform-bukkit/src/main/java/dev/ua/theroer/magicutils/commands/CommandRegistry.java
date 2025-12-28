package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.parsers.LanguageKeyTypeParser;
import dev.ua.theroer.magicutils.commands.parsers.OfflinePlayerTypeParser;
import dev.ua.theroer.magicutils.commands.parsers.PlayerTypeParser;
import dev.ua.theroer.magicutils.commands.parsers.WorldTypeParser;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Handles registration and initialization of commands in the plugin.
 */
public class CommandRegistry {
    private static PrefixedLogger logger;
    private static Logger messageLogger;

    @Getter
    private static CommandManager<CommandSender> commandManager;
    private static BukkitCommandPlatform platform;
    private static CommandMap commandMap;
    @Getter
    private static JavaPlugin plugin;
    @Getter
    private static String permissionPrefix;

    /**
     * Default constructor for CommandRegistry.
     */
    public CommandRegistry() {
    }

    /**
     * Initializes the command registry with the plugin and permission prefix.
     * 
     * @param plugin           the JavaPlugin instance
     * @param permissionPrefix the prefix for permissions
     */
    public static void initialize(JavaPlugin plugin, String permissionPrefix, Logger loggerInstance) {
        CommandRegistry.plugin = plugin;
        CommandRegistry.permissionPrefix = permissionPrefix;
        if (loggerInstance == null) {
            throw new IllegalArgumentException("Logger instance is required");
        }
        logger = loggerInstance.withPrefix("Commands", "[Commands]");
        messageLogger = loggerInstance;
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

        platform = new BukkitCommandPlatform(commandLogger);

        TypeParserRegistry<CommandSender> parserRegistry = TypeParserRegistry.createWithDefaults(commandLogger);
        parserRegistry.register(new PlayerTypeParser(logger));
        parserRegistry.register(new OfflinePlayerTypeParser(logger));
        parserRegistry.register(new WorldTypeParser(logger));
        LanguageKeyTypeParser.setPlugin(plugin);
        parserRegistry.register(new LanguageKeyTypeParser());

        CommandRegistry.commandManager = new CommandManager<>(permissionPrefix, plugin.getName(),
                commandLogger, platform, parserRegistry);
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

        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
            logger.info("Command registry initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize command registry: " + e.getMessage());
            throw new RuntimeException(InternalMessages.ERR_FAILED_GET_COMMANDMAP.get(), e);
        }
    }

    /**
     * Registers multiple commands at once.
     * 
     * @param commands the commands to register
     */
    public static void registerAll(MagicCommand... commands) {
        if (commandManager == null) {
            throw new IllegalStateException(InternalMessages.ERR_REGISTRY_NOT_INITIALIZED.get());
        }

        for (MagicCommand command : commands) {
            register(command);
        }
    }

    /**
     * Registers multiple builder-defined commands at once.
     *
     * @param specs command specs to register
     */
    public static void registerAll(CommandSpec<?>... specs) {
        if (commandManager == null) {
            throw new IllegalStateException(InternalMessages.ERR_REGISTRY_NOT_INITIALIZED.get());
        }
        if (specs == null) {
            return;
        }
        for (CommandSpec<?> spec : specs) {
            register(spec);
        }
    }

    /**
     * Registers a single command.
     * 
     * @param command the command to register
     */
    public static void register(MagicCommand command) {
        if (commandManager == null) {
            throw new IllegalStateException(InternalMessages.ERR_REGISTRY_NOT_INITIALIZED.get());
        }

        if (commandMap == null) {
            throw new IllegalStateException(InternalMessages.ERR_COMMANDMAP_NOT_AVAILABLE.get());
        }

        Class<?> clazz = command.getClass();
        CommandInfo info = command.overrideInfo(clazz.getAnnotation(CommandInfo.class));

        if (info == null) {
            throw new IllegalArgumentException(InternalMessages.ERR_MISSING_COMMANDINFO.get("class", clazz.getName()));
        }

        commandManager.register(command, info);

        // Generate beautiful usage string (use primary name)
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

        // Set detailed usage for help system
        if (!subCommandUsages.isEmpty()) {
            StringBuilder detailedUsage = new StringBuilder(usage);
            detailedUsage.append("\n§7Available subcommands:");
            for (String subUsage : subCommandUsages) {
                detailedUsage.append("\n§8  ").append(subUsage);
            }
            bukkitCommand.setDetailedUsage(detailedUsage.toString());
        }

        // Clear stale registrations (e.g., plugin.yml) before registering
        unregisterIfOwned(info.name());
        boolean registered = commandMap.register(plugin.getName().toLowerCase(), bukkitCommand);

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
            logger.warn(
                    "Failed to register command: " + info.name() + " (command may already exist)");
        }

        generatePermissions(command, info);

        // Register aliases with same usage information
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

            boolean aliasRegistered = commandMap.register(plugin.getName().toLowerCase(), aliasCommand);
            if (aliasRegistered) {
                logger.info(
                        InternalMessages.SYS_ALIAS_REGISTERED.get("alias", alias, "command", info.name()));
                // Do not print alias usage; primary usage already covers subcommands.
            }
        }
    }

    /**
     * Registers a single builder-defined command.
     *
     * @param spec command spec to register
     */
    public static void register(CommandSpec<?> spec) {
        if (spec == null) {
            return;
        }
        register(new DynamicCommand(spec));
    }

    private static void generatePermissions(MagicCommand command, CommandInfo info) {
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
                    "commands." + info.name() + ".subcommand." + subInfo.name());
            if (!subPermission.isEmpty()) {
                permissions.add(subPermission + " (" + subInfo.permissionDefault() + ")");
                String description = !subInfo.description().isEmpty() ? subInfo.description() : info.description();
                platform.ensurePermissionRegistered(subPermission, subInfo.permissionDefault(), description);
                incrementCount(counts, subInfo.permissionDefault());
            }

            registerArgumentPermissions(info.name(), subInfo.name(), subInfo.arguments(), permissions, counts);
        }

        // Wildcards for convenience
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

    private static void registerArgumentPermissions(String commandName, String subCommandName,
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

    private static String buildArgumentPermission(String normalizedCommandName, String subCommandName, CommandArgument argument) {
        StringBuilder sb = new StringBuilder("commands.").append(normalizedCommandName);
        if (subCommandName != null && !subCommandName.isEmpty()) {
            sb.append(".subcommand.").append(subCommandName);
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

    private static void incrementCount(EnumMap<MagicPermissionDefault, Integer> counts,
            MagicPermissionDefault def) {
        if (def == null) {
            def = MagicPermissionDefault.OP;
        }
        counts.put(def, counts.getOrDefault(def, 0) + 1);
    }

    private static void logPermissionSummary(String commandName,
            EnumMap<MagicPermissionDefault, Integer> counts) {
        logger.info("Permissions for /" + commandName + ":");
        counts.forEach((def, count) -> logger.info("  " + def.name() + ": " + count));
    }

    private static String resolvePermission(String annotationPermission, String fallbackPermission) {
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
    public static boolean unregister(String commandName) {
        return tryUnregister(commandName, false);
    }

    /**
     * Checks if the command registry is initialized.
     * 
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return commandManager != null && commandMap != null && plugin != null;
    }

    private static void unregisterIfOwned(String name) {
        try {
            var existing = commandMap.getCommand(name);
            if (existing != null && isOwned(existing)) {
                tryUnregister(name, true);
            }
            var namespaced = plugin.getName().toLowerCase() + ":" + name.toLowerCase();
            var existingNs = commandMap.getCommand(namespaced);
            if (existingNs != null && isOwned(existingNs)) {
                tryUnregister(name, true);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isOwned(org.bukkit.command.Command cmd) {
        if (cmd instanceof org.bukkit.command.PluginIdentifiableCommand pic) {
            return pic.getPlugin() == plugin;
        }
        return cmd.getClass().getSimpleName().contains("BukkitCommandWrapper");
    }

    private static boolean tryUnregister(String commandName, boolean silent) {
        if (commandMap == null) {
            return false;
        }
        try {
            Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<String, org.bukkit.command.Command> knownCommands = (Map<String, org.bukkit.command.Command>) knownCommandsField
                    .get(commandMap);

            boolean removed = false;
            removed |= knownCommands.remove(commandName.toLowerCase()) != null;
            removed |= knownCommands.remove(plugin.getName().toLowerCase() + ":" + commandName.toLowerCase()) != null;

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
}
