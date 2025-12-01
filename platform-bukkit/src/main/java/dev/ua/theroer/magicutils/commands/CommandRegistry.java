package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerGen;
import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Handles registration and initialization of commands in the plugin.
 */
public class CommandRegistry {
    private static final PrefixedLogger logger = Logger.create("Commands", "[Commands]");

    @Getter
    private static CommandManager commandManager;
    private static CommandMap commandMap;
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
    public static void initialize(JavaPlugin plugin, String permissionPrefix) {
        CommandRegistry.plugin = plugin;
        CommandRegistry.permissionPrefix = permissionPrefix;
        CommandRegistry.commandManager = new CommandManager(permissionPrefix, plugin.getName());

        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
            PrefixedLoggerGen.info(logger, "Command registry initialized successfully");
        } catch (Exception e) {
            PrefixedLoggerGen.error(logger, "Failed to initialize command registry: " + e.getMessage());
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
        CommandInfo info = clazz.getAnnotation(CommandInfo.class);

        if (info == null) {
            throw new IllegalArgumentException(InternalMessages.ERR_MISSING_COMMANDINFO.get("class", clazz.getName()));
        }

        commandManager.register(command, info);

        // Generate beautiful usage string
        String usage = commandManager.generateUsage(command, info);
        List<String> subCommandUsages = commandManager.generateSubCommandUsages(command, info);

        BukkitCommandWrapper bukkitCommand = BukkitCommandWrapper.create(
                info.name(),
                commandManager,
                Arrays.asList(info.aliases()));

        bukkitCommand.setDescription(info.description());
        bukkitCommand.setUsage(usage);

        // Set detailed usage for help system
        if (!subCommandUsages.isEmpty()) {
            StringBuilder detailedUsage = new StringBuilder(usage);
            detailedUsage.append("\n§7Available subcommands:");
            for (String subUsage : subCommandUsages) {
                detailedUsage.append("\n§8  ").append(subUsage);
            }
            bukkitCommand.setDetailedUsage(detailedUsage.toString());
        }

        boolean registered = commandMap.register(plugin.getName().toLowerCase(), bukkitCommand);

        if (registered) {
            PrefixedLoggerGen.info(logger, InternalMessages.SYS_COMMAND_REGISTERED.get("command", info.name(),
                    "aliases", Arrays.toString(info.aliases())));
            PrefixedLoggerGen.info(logger, InternalMessages.SYS_COMMAND_USAGE.get("usage", usage));
            if (!subCommandUsages.isEmpty()) {
                PrefixedLoggerGen.info(logger, InternalMessages.SYS_SUBCOMMAND_USAGES.get());
                for (String subUsage : subCommandUsages) {
                    PrefixedLoggerGen.info(logger, "  " + subUsage);
                }
            }
        } else {
            PrefixedLoggerGen.warn(logger,
                    "Failed to register command: " + info.name() + " (command may already exist)");
        }

        generatePermissions(clazz, info);

        // Register aliases with same usage information
        for (String alias : info.aliases()) {
            String aliasUsage = usage.replace("/" + info.name(), "/" + alias);

            BukkitCommandWrapper aliasCommand = BukkitCommandWrapper.create(
                    alias,
                    commandManager,
                    Arrays.asList(info.name()));
            aliasCommand.setDescription(info.description());
            aliasCommand.setUsage(aliasUsage);

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
                PrefixedLoggerGen.info(logger,
                        InternalMessages.SYS_ALIAS_REGISTERED.get("alias", alias, "command", info.name()));
                PrefixedLoggerGen.info(logger, InternalMessages.SYS_ALIAS_USAGE.get("usage", aliasUsage));
            }
        }
    }

    private static void generatePermissions(Class<?> clazz, CommandInfo info) {
        Set<String> permissions = new HashSet<>();

        if (info.permission()) {
            permissions.add(permissionPrefix + ".commands." + info.name() + ".use");
        }

        for (MagicCommand.SubCommandInfo subInfo : MagicCommand.getSubCommands(clazz)) {
            SubCommand sub = subInfo.annotation;
            if (sub.permission()) {
                permissions.add(permissionPrefix + ".commands." + info.name() +
                        ".subcommand." + sub.name() + ".use");
            }
        }

        if (!permissions.isEmpty()) {
            PrefixedLoggerGen.info(logger, InternalMessages.SYS_GENERATED_PERMISSIONS.get("command", info.name(),
                    "permissions", permissions.toString()));
        }
    }

    /**
     * Unregisters a command by name.
     * 
     * @param commandName the command name to unregister
     * @return true if the command was successfully unregistered
     */
    public static boolean unregister(String commandName) {
        if (commandMap == null) {
            return false;
        }

        try {
            Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<String, org.bukkit.command.Command> knownCommands = (Map<String, org.bukkit.command.Command>) knownCommandsField
                    .get(commandMap);

            knownCommands.remove(commandName.toLowerCase());
            knownCommands.remove(plugin.getName().toLowerCase() + ":" + commandName.toLowerCase());

            PrefixedLoggerGen.info(logger, InternalMessages.SYS_UNREGISTERED_COMMAND.get("command", commandName));
            return true;
        } catch (Exception e) {
            PrefixedLoggerGen.error(logger, "Failed to unregister command " + commandName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the command registry is initialized.
     * 
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return commandManager != null && commandMap != null && plugin != null;
    }

    /**
     * Gets the plugin instance.
     * 
     * @return the plugin instance
     */
    public static JavaPlugin getPlugin() {
        return plugin;
    }
}
