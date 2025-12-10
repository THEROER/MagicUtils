package dev.ua.theroer.magicutils.commands;

import lombok.Getter;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerGen;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.annotations.*;
import dev.ua.theroer.magicutils.commands.AllowedSender;
import dev.ua.theroer.magicutils.commands.SenderMismatchException;

import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.CommandMinecart;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages registration, execution, and tab completion of commands.
 */
public class CommandManager {
    private static final PrefixedLogger logger = Logger.create("Commands", "[Commands]");

    private final Map<String, MagicCommand> commands = new ConcurrentHashMap<>();
    private final Map<String, CommandInfo> commandInfos = new ConcurrentHashMap<>();
    private final String permissionPrefix;
    private final String pluginName;
    @Getter
    private final TypeParserRegistry typeParserRegistry;

    /**
     * Constructs a new CommandManager.
     * 
     * @param permissionPrefix the prefix for permissions
     * @param pluginName       the plugin name for namespaced commands
     */
    public CommandManager(String permissionPrefix, String pluginName) {
        this.permissionPrefix = permissionPrefix;
        this.pluginName = pluginName.toLowerCase();
        this.typeParserRegistry = TypeParserRegistry.createWithDefaults();
        PrefixedLoggerGen.debug(logger, "CommandManager initialized with permission prefix: " + permissionPrefix
                + " and plugin name: " + pluginName);
    }

    /**
     * Registers a command and its info.
     * 
     * @param command the command instance
     * @param info    the command info annotation
     */
    public void register(MagicCommand command, CommandInfo info) {
        String name = info.name().toLowerCase();
        commands.put(name, command);
        commandInfos.put(name, info);

        String namespacedName = pluginName + ":" + name;
        commands.put(namespacedName, command);
        commandInfos.put(namespacedName, info);

        PrefixedLoggerGen.debug(logger, "Registered command: " + name + " and " + namespacedName + " with class: "
                + command.getClass().getSimpleName());

        for (String alias : info.aliases()) {
            String aliasLower = alias.toLowerCase();
            commands.put(aliasLower, command);
            commandInfos.put(aliasLower, info);

            // Also register alias with plugin namespace
            String namespacedAlias = pluginName + ":" + aliasLower;
            commands.put(namespacedAlias, command);
            commandInfos.put(namespacedAlias, info);

            PrefixedLoggerGen.debug(logger,
                    "Registered alias: " + aliasLower + " and " + namespacedAlias + " for command: " + name);
        }

        List<MagicCommand.SubCommandInfo> subCommands = MagicCommand.getSubCommands(command.getClass());
        Method executeMethod = getExecuteMethod(command.getClass());

        PrefixedLoggerGen.debug(logger, "Command " + name + " has " + subCommands.size() + " subcommands: " +
                subCommands.stream().map(s -> s.annotation.name()).collect(Collectors.toList()));
        PrefixedLoggerGen.debug(logger, "Command " + name + " has direct execute method: " + (executeMethod != null));
    }

    /**
     * Gets the direct execute method if it exists.
     * 
     * @param clazz the command class
     * @return the execute method or null if not found
     */
    private Method getExecuteMethod(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals("execute") && !method.isAnnotationPresent(SubCommand.class)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Executes a command by name.
     * 
     * @param name   the command name
     * @param sender the command sender
     * @param args   the command arguments
     * @return the result of command execution
     */
    public CommandResult execute(String name, CommandSender sender, List<String> args) {
        PrefixedLoggerGen.debug(logger, "Attempting to execute command: " + name + " with args: " + args);

        MagicCommand command = commands.get(name.toLowerCase());
        CommandInfo info = commandInfos.get(name.toLowerCase());

        if (command == null || info == null) {
            PrefixedLoggerGen.debug(logger,
                    "Command not found: " + name + ". Available commands: " + commands.keySet());
            return CommandResult.notFound();
        }

        PrefixedLoggerGen.debug(logger, "Found command: " + name + ", checking permissions...");

        String baseCommandName = info.name().toLowerCase(Locale.ROOT);
        String targetSubName = (args != null && !args.isEmpty()) ? args.get(0).toLowerCase(Locale.ROOT) : null;
        String commandPermission = resolvePermission(info.permission(),
                "commands." + baseCommandName);
        ensurePermissionRegistered(commandPermission, info.permissionDefault(), info.description());
        if (!commandPermission.isEmpty() && !sender.hasPermission(commandPermission)
                && !hasSubOrArgPermission(command, info, sender, baseCommandName, targetSubName)) {
            PrefixedLoggerGen.debug(logger,
                    "Permission denied for " + sender.getName() + " on permission: " + commandPermission);
            return CommandResult.failure(InternalMessages.CMD_NO_PERMISSION.get());
        }

        try {
            return executeCommand(command, info, sender, args, baseCommandName);
        } catch (Exception e) {
            PrefixedLoggerGen.error(logger, "Error executing command " + name + ": " + e.getMessage());
            e.printStackTrace();
            return CommandResult.failure(InternalMessages.CMD_EXECUTION_ERROR.get());
        }
    }

    private CommandResult executeCommand(MagicCommand command, CommandInfo info, CommandSender sender,
            List<String> args, String normalizedCommandName) {
        List<MagicCommand.SubCommandInfo> subCommands = MagicCommand.getSubCommands(command.getClass());
        Method executeMethod = getExecuteMethod(command.getClass());

        PrefixedLoggerGen.debug(logger, "Executing command with " + subCommands.size()
                + " available subcommands and execute method: " + (executeMethod != null));

        // If there's a direct execute method and no subcommands, or if there are no
        // args and execute method exists
        if (executeMethod != null && (subCommands.isEmpty() || args.isEmpty())) {
            PrefixedLoggerGen.debug(logger, "Using direct execute method");
            return executeDirectMethod(command, info, executeMethod, sender, args, normalizedCommandName);
        }

        // If there are subcommands but no execute method and no args
        if (subCommands.isEmpty() && executeMethod == null) {
            PrefixedLoggerGen.debug(logger, "No subcommands and no execute method found, returning success");
            return CommandResult.success(InternalMessages.CMD_EXECUTED.get());
        }

        // If there are subcommands but no args provided
        if (args.isEmpty() && !subCommands.isEmpty()) {
            String availableSubCommands = getAvailableSubCommands(subCommands, sender, normalizedCommandName);
            PrefixedLoggerGen.debug(logger, "No arguments provided, available subcommands: " + availableSubCommands);
            return CommandResult
                    .failure(InternalMessages.CMD_SPECIFY_SUBCOMMAND.get("subcommands", availableSubCommands));
        }

        // Handle subcommand execution
        String subCommandName = args.get(0).toLowerCase(Locale.ROOT);
        PrefixedLoggerGen.debug(logger, "Looking for subcommand: " + subCommandName);

        MagicCommand.SubCommandInfo targetSubCommand = null;

        for (MagicCommand.SubCommandInfo subInfo : subCommands) {
            if (matchesSubCommand(subInfo, subCommandName)) {
                targetSubCommand = subInfo;
                break;
            }
        }

        if (targetSubCommand == null) {
            PrefixedLoggerGen.debug(logger, "Subcommand not found: " + subCommandName + ". Available: " +
                    getAvailableSubCommands(subCommands, sender, normalizedCommandName));
            return CommandResult.failure(InternalMessages.CMD_UNKNOWN_SUBCOMMAND.get("subcommand", subCommandName));
        }

        PrefixedLoggerGen.debug(logger, "Found subcommand: " + subCommandName + ", checking permissions...");

        String subPermission = resolvePermission(targetSubCommand.annotation.permission(),
                "commands." + normalizedCommandName + ".subcommand." + targetSubCommand.annotation.name());
        ensurePermissionRegistered(subPermission, targetSubCommand.annotation.permissionDefault(),
                targetSubCommand.annotation.description());
        if (!subPermission.isEmpty() && !sender.hasPermission(subPermission)
                && !hasArgumentPermissionOverride(normalizedCommandName, targetSubCommand.annotation.name(), sender)) {
            PrefixedLoggerGen.debug(logger,
                    "Permission denied for subcommand " + subCommandName + " on permission: " + subPermission);
            return CommandResult.failure(InternalMessages.CMD_NO_PERMISSION.get());
        }

        List<String> subArgs = args.size() > 1 ? args.subList(1, args.size()) : new ArrayList<>();
        PrefixedLoggerGen.debug(logger, "Executing subcommand: " + subCommandName + " with args: " + subArgs);

        return executeSubCommand(command, info, targetSubCommand, sender, subArgs, normalizedCommandName);
    }

    private CommandResult executeDirectMethod(MagicCommand command, CommandInfo info, Method executeMethod, CommandSender sender,
            List<String> args, String normalizedCommandName) {
        try {
            List<CommandArgument> arguments = MagicCommand.getArguments(executeMethod);

            PrefixedLoggerGen.debug(logger, "Parsing " + arguments.size() + " arguments for direct execute method");
            PrefixedLoggerGen.debug(logger,
                    "Method parameter types: " + Arrays.toString(executeMethod.getParameterTypes()));

            Object[] methodArgs = parseArgumentsForDirectMethod(arguments, args, sender, normalizedCommandName, null);

            if (methodArgs == null) {
                PrefixedLoggerGen.debug(logger, "Failed to parse arguments for direct execute method");
                String usage = buildUsage(info, null, arguments);
                return CommandResult.failure(InternalMessages.CMD_INVALID_ARGUMENTS.get("usage", usage));
            }

            if (!validateArgumentPermissions(normalizedCommandName, null, arguments, methodArgs, sender)) {
                return CommandResult.failure(InternalMessages.CMD_NO_PERMISSION.get());
            }

            // Log the actual arguments being passed
            for (int i = 0; i < methodArgs.length; i++) {
                PrefixedLoggerGen.debug(logger,
                        "Method arg[" + i + "]: "
                                + (methodArgs[i] != null
                                        ? methodArgs[i].getClass().getSimpleName() + "=" + methodArgs[i]
                                        : "null"));
            }

            PrefixedLoggerGen.debug(logger, "Invoking direct execute method with parsed arguments");
            Object result = executeMethod.invoke(command, methodArgs);

            CommandResult commandResult = result instanceof CommandResult ? (CommandResult) result
                    : CommandResult.success();
            PrefixedLoggerGen.debug(logger,
                    "Direct execute method executed successfully, result: " + commandResult.isSuccess());

            return commandResult;

        } catch (SenderMismatchException e) {
            PrefixedLoggerGen.debug(logger, "Sender mismatch: " + e.getMessage());
            return CommandResult.failure(e.getMessage());
        } catch (Exception e) {
            PrefixedLoggerGen.error(logger, "Error executing direct method: " + e.getMessage());
            e.printStackTrace();
            return CommandResult.failure(InternalMessages.CMD_EXECUTION_ERROR.get());
        }
    }

    private Object[] parseArgumentsForDirectMethod(List<CommandArgument> arguments, List<String> args,
            CommandSender sender, String normalizedCommandName, @Nullable String subCommandName) {
        Object[] result = new Object[arguments.size()];
        boolean[] filled = new boolean[arguments.size()];

        // First pass: auto-fill sender arguments
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument argument = arguments.get(i);
            if (argument.isSenderParameter() || argument.getType().equals(CommandSender.class)) {
                try {
                    result[i] = resolveSenderArgument(sender, argument);
                    filled[i] = true;
                    PrefixedLoggerGen.debug(logger, "Auto-filled sender argument " + i + " (" + argument.getName()
                            + "): " + sender.getName());
                } catch (SenderMismatchException ex) {
                    PrefixedLoggerGen.debug(logger, "Sender mismatch for argument " + argument.getName() + ": "
                            + ex.getMessage());
                    throw ex;
                }
            }
        }

        // Second pass: try to match user arguments intelligently
        List<String> remainingArgs = new ArrayList<>(args);

        for (int i = 0; i < arguments.size(); i++) {
            if (filled[i])
                continue; // Skip already filled arguments

            CommandArgument argument = arguments.get(i);

            if (lacksArgumentPermission(normalizedCommandName, subCommandName, argument, sender)) {
                PrefixedLoggerGen.debug(logger,
                        "Skipping argument " + argument.getName() + " due to missing permission, setting to default/null");
                if (argument.getDefaultValue() != null) {
                    result[i] = convertArgument(argument.getDefaultValue(), argument.getType(), sender);
                } else {
                    result[i] = null;
                }
                filled[i] = true;
                if (!remainingArgs.isEmpty() && !argument.getType().equals(CommandSender.class)) {
                    remainingArgs.remove(0);
                }
                continue;
            }

            if (argument.isGreedy()) {
                continue; // Greedy handled in final pass
            }
            String bestMatch = null;
            int bestMatchIndex = -1;

            // Try to find the best match for this parameter
            for (int j = 0; j < remainingArgs.size(); j++) {
                String userArg = remainingArgs.get(j);

                // Check if this user argument is a good match for this parameter
                if (isArgumentMatch(argument, userArg, sender)) {
                    bestMatch = userArg;
                    bestMatchIndex = j;
                    break; // Take first good match to maintain order priority
                }
            }

            // If we found a match, use it
            if (bestMatch != null) {
                result[i] = convertArgument(bestMatch, argument.getType(), sender);
                filled[i] = true;
                remainingArgs.remove(bestMatchIndex);
                PrefixedLoggerGen.debug(logger,
                        "Matched user arg '" + bestMatch + "' to parameter " + i + " (" + argument.getName() + ")");
            }
        }

        // Third pass: fill remaining arguments in order
        int userArgIndex = 0;
        for (int i = 0; i < arguments.size(); i++) {
            if (filled[i])
                continue; // Skip already filled arguments

            CommandArgument argument = arguments.get(i);
            String value;
            boolean providedByUser = false;

            if (argument.isGreedy()) {
                // Consume all remaining args joined by space
                if (userArgIndex < remainingArgs.size()) {
                    value = String.join(" ", remainingArgs.subList(userArgIndex, remainingArgs.size()));
                    userArgIndex = remainingArgs.size();
                    providedByUser = true;
                    PrefixedLoggerGen.debug(logger,
                            "Greedy parameter " + i + " (" + argument.getName() + ") consumed remaining args: " + value);
                } else if (argument.getDefaultValue() != null) {
                    value = argument.getDefaultValue();
                    PrefixedLoggerGen.debug(logger,
                            "Used default value for greedy parameter " + i + " (" + argument.getName() + "): " + value);
                } else if (argument.isOptional()) {
                    value = null;
                    PrefixedLoggerGen.debug(logger,
                            "Used null for optional greedy parameter " + i + " (" + argument.getName() + ")");
                } else {
                    PrefixedLoggerGen.debug(logger,
                            "Missing required greedy argument at position " + i + ": " + argument.getName());
                    return null;
                }
            } else {
                if (userArgIndex < remainingArgs.size()) {
                    value = remainingArgs.get(userArgIndex);
                    userArgIndex++;
                    providedByUser = true;
                    PrefixedLoggerGen.debug(logger,
                            "Filled remaining parameter " + i + " (" + argument.getName() + ") with user arg: " + value);
                } else if (argument.getDefaultValue() != null) {
                    value = argument.getDefaultValue();
                    PrefixedLoggerGen.debug(logger,
                            "Used default value for parameter " + i + " (" + argument.getName() + "): " + value);
                } else if (argument.isOptional()) {
                    value = null;
                    PrefixedLoggerGen.debug(logger,
                            "Used null for optional parameter " + i + " (" + argument.getName() + ")");
                } else {
                    PrefixedLoggerGen.debug(logger,
                            "Missing required argument at position " + i + ": " + argument.getName());
                    return null;
                }
            }

            result[i] = convertArgument(value, argument.getType(), sender);
            PrefixedLoggerGen.debug(logger, "Parsed argument " + i + " (" + argument.getName() + "): " + result[i]
                    + " (type: " + (result[i] != null ? result[i].getClass().getSimpleName() : "null") + ")");

            // If optional argument failed to convert, do not consume the user arg; let next parameter try it
            if (providedByUser && result[i] == null && argument.isOptional()) {
                userArgIndex = Math.max(0, userArgIndex - 1);
                PrefixedLoggerGen.debug(logger, "Conversion failed for optional argument " + argument.getName()
                        + ", reusing value for next parameter");
                continue;
            }

            // Check if conversion failed
            if (value != null && result[i] == null && !argument.isOptional()) {
                PrefixedLoggerGen.debug(logger, "Failed to convert argument " + i + " (" + argument.getName()
                        + ") from value: " + value + " to type: " + argument.getType().getSimpleName());
                return null;
            }
        }

        return result;
    }

    private boolean isArgumentMatch(CommandArgument argument, String userArg, CommandSender sender) {
        if (argument.isGreedy()) {
            return false;
        }

        // Check if the argument type can successfully parse this value
        Object converted = convertArgument(userArg, argument.getType(), sender);

        // If conversion succeeded and didn't just fall back to string
        if (converted != null && !converted.equals(userArg)) {
            return true;
        }

        // Check if this value is in the argument's suggestions
        if (argumentHasSuggestion(argument, userArg, sender)) {
            return true;
        }

        // For string arguments, accept anything as last resort
        if (argument.getType().equals(String.class)) {
            return true;
        }

        return false;
    }

    private boolean argumentHasSuggestion(CommandArgument argument, String value, CommandSender sender) {
        for (String suggestionSource : argument.getSuggestions()) {
            // Use the suggestion parser to get all possible values
            List<String> suggestions = typeParserRegistry.parseSuggestion(suggestionSource, sender);
            for (String suggestion : suggestions) {
                if (suggestion.equalsIgnoreCase(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private CommandResult executeSubCommand(MagicCommand command, CommandInfo info, MagicCommand.SubCommandInfo subInfo,
            CommandSender sender, List<String> args, String normalizedCommandName) {
        try {
            Method method = subInfo.method;
            List<CommandArgument> arguments = MagicCommand.getArguments(method);

            PrefixedLoggerGen.debug(logger,
                    "Parsing " + arguments.size() + " arguments for method: " + method.getName());
            PrefixedLoggerGen.debug(logger, "Method parameter types: " + Arrays.toString(method.getParameterTypes()));

            Object[] methodArgs = parseArgumentsForDirectMethod(arguments, args, sender, normalizedCommandName,
                    subInfo.annotation.name());

            if (methodArgs == null) {
                PrefixedLoggerGen.debug(logger, "Failed to parse arguments for subcommand");
                String usage = buildUsage(info, subInfo, arguments);
                return CommandResult.failure(InternalMessages.CMD_INVALID_ARGUMENTS.get("usage", usage));
            }

            if (!validateArgumentPermissions(normalizedCommandName, subInfo.annotation.name(), arguments, methodArgs, sender)) {
                return CommandResult.failure(InternalMessages.CMD_NO_PERMISSION.get());
            }

            // Log the actual arguments being passed
            for (int i = 0; i < methodArgs.length; i++) {
                PrefixedLoggerGen.debug(logger,
                        "Method arg[" + i + "]: "
                                + (methodArgs[i] != null
                                        ? methodArgs[i].getClass().getSimpleName() + "=" + methodArgs[i]
                                        : "null"));
            }

            PrefixedLoggerGen.debug(logger, "Invoking method with parsed arguments");
            Object result = method.invoke(command, methodArgs);

            CommandResult commandResult = result instanceof CommandResult ? (CommandResult) result
                    : CommandResult.success();
            PrefixedLoggerGen.debug(logger, "Subcommand executed successfully, result: " + commandResult.isSuccess());

            return commandResult;

        } catch (SenderMismatchException e) {
            PrefixedLoggerGen.debug(logger, "Sender mismatch: " + e.getMessage());
            return CommandResult.failure(e.getMessage());
        } catch (Exception e) {
            PrefixedLoggerGen.error(logger, "Error executing subcommand: " + e.getMessage());
            e.printStackTrace();
            return CommandResult.failure(InternalMessages.CMD_EXECUTION_ERROR.get());
        }
    }

    private Object convertArgument(String value, Class<?> type, CommandSender sender) {
        PrefixedLoggerGen.debug(logger, "Converting argument: '" + value + "' to type: " + type.getSimpleName());

        // Use the argument parser registry to convert the argument
        Object result = typeParserRegistry.parse(value, type, sender);

        if (result != null || value == null) {
            return result;
        }

        // Fallback: only return raw value for String targets; otherwise treat as unparsed
        if (type.equals(String.class)) {
            PrefixedLoggerGen.debug(logger,
                    "No parser found for type " + type.getSimpleName() + ", returning string value");
            return value;
        }

        PrefixedLoggerGen.debug(logger,
                "No parser found for type " + type.getSimpleName() + ", returning null");
        return null;
    }

    private Object resolveSenderArgument(CommandSender sender, CommandArgument argument) {
        CommandSender effective = unwrapSender(sender);
        AllowedSender[] allowed = argument.getAllowedSenders();
        AllowedSender calleeKind = classifySender(effective);
        boolean proxied = sender instanceof ProxiedCommandSender;

        if (!isAllowedSender(allowed, calleeKind, proxied)) {
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }

        Class<?> targetType = argument.getType();

        if (targetType.equals(CommandSender.class)) {
            return effective;
        }
        if (targetType.equals(ProxiedCommandSender.class)) {
            if (sender instanceof ProxiedCommandSender p) {
                return p;
            }
            // fall through to generic handling
        }
        if (targetType.equals(Player.class)) {
            if (effective instanceof Player p) {
                return p;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }
        if (targetType.equals(ConsoleCommandSender.class)) {
            if (effective instanceof ConsoleCommandSender c) {
                return c;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }
        if (targetType.equals(BlockCommandSender.class)) {
            if (effective instanceof BlockCommandSender b) {
                return b;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }
        if (targetType.equals(CommandMinecart.class)) {
            if (effective instanceof CommandMinecart cart) {
                return cart;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }
        if (targetType.equals(RemoteConsoleCommandSender.class)) {
            if (effective instanceof RemoteConsoleCommandSender r) {
                return r;
            }
            throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
        }

        if (targetType.isInstance(effective)) {
            return targetType.cast(effective);
        }

        throw new SenderMismatchException(buildSenderError(argument.getType(), allowed));
    }

    private CommandSender unwrapSender(CommandSender sender) {
        if (sender instanceof ProxiedCommandSender proxied && proxied.getCallee() instanceof CommandSender callee) {
            return callee;
        }
        return sender;
    }

    private AllowedSender classifySender(CommandSender sender) {
        if (sender instanceof Player) {
            return AllowedSender.PLAYER;
        }
        if (sender instanceof ConsoleCommandSender) {
            return AllowedSender.CONSOLE;
        }
        if (sender instanceof BlockCommandSender) {
            return AllowedSender.BLOCK;
        }
        if (sender instanceof CommandMinecart) {
            return AllowedSender.MINECART;
        }
        if (sender instanceof RemoteConsoleCommandSender) {
            return AllowedSender.REMOTE;
        }
        if (sender instanceof ProxiedCommandSender) {
            return AllowedSender.PROXIED;
        }
        return AllowedSender.ANY;
    }

    private boolean isAllowedSender(AllowedSender[] allowed, AllowedSender calleeKind, boolean proxied) {
        if (allowed == null || allowed.length == 0) {
            return true;
        }
        for (AllowedSender a : allowed) {
            if (a == AllowedSender.ANY) {
                return true;
            }
            if (a == calleeKind) {
                return true;
            }
            if (a == AllowedSender.PROXIED && proxied) {
                return true;
            }
        }
        return false;
    }

    private String buildSenderError(Class<?> targetType, AllowedSender[] allowed) {
        // Prefer explicit allowed list; if ANY present, infer from type
        Set<AllowedSender> required = new LinkedHashSet<>();
        if (allowed != null) {
            required.addAll(Arrays.asList(allowed));
        }
        required.remove(AllowedSender.ANY);

        if (required.isEmpty()) {
            AllowedSender inferred = inferSenderFromType(targetType);
            if (inferred != AllowedSender.ANY) {
                required.add(inferred);
            }
        }

        if (required.isEmpty()) {
            return "This command cannot be used by this sender";
        }

        String messageBody = required.stream()
                .map(this::describeSender)
                .distinct()
                .collect(Collectors.joining(" or "));

        return "This command can only be used by " + messageBody;
    }

    private AllowedSender inferSenderFromType(Class<?> type) {
        if (type.equals(Player.class)) {
            return AllowedSender.PLAYER;
        }
        if (type.equals(ConsoleCommandSender.class)) {
            return AllowedSender.CONSOLE;
        }
        if (type.equals(BlockCommandSender.class)) {
            return AllowedSender.BLOCK;
        }
        if (type.equals(CommandMinecart.class)) {
            return AllowedSender.MINECART;
        }
        if (type.equals(RemoteConsoleCommandSender.class)) {
            return AllowedSender.REMOTE;
        }
        if (type.equals(ProxiedCommandSender.class)) {
            return AllowedSender.PROXIED;
        }
        return AllowedSender.ANY;
    }

    private String describeSender(AllowedSender sender) {
        return switch (sender) {
            case PLAYER -> "players";
            case CONSOLE -> "console";
            case BLOCK -> "command blocks";
            case MINECART -> "command minecarts";
            case PROXIED -> "proxied senders";
            case REMOTE -> "remote console";
            default -> "valid senders";
        };
    }

    private boolean validateArgumentPermissions(String normalizedCommandName, @Nullable String subCommandName,
            List<CommandArgument> arguments, Object[] values, CommandSender sender) {
        Map<String, Object> valuesByName = new HashMap<>();
        Map<String, CommandArgument> argumentsByName = new HashMap<>();

        for (int i = 0; i < arguments.size(); i++) {
            valuesByName.put(arguments.get(i).getName(), values[i]);
            argumentsByName.put(arguments.get(i).getName(), arguments.get(i));
        }

        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument argument = arguments.get(i);
            if (argument.isSenderParameter() || !argument.hasPermission()) {
                continue;
            }

            if (!shouldCheckPermission(argument, values[i], valuesByName, argumentsByName, sender)) {
                continue;
            }

            String fallback = buildArgumentPermission(normalizedCommandName, subCommandName, argument);
            String resolved = resolvePermission(argument.getPermission(), fallback);
            ensurePermissionRegistered(resolved, argument.getPermissionDefault(),
                    "Argument " + argument.getName() + " for /" + normalizedCommandName
                            + (subCommandName != null ? " " + subCommandName : ""));
            if (resolved != null && !resolved.isEmpty() && !sender.hasPermission(resolved)) {
                PrefixedLoggerGen.debug(logger, "Skipping permission check for argument " + argument.getName()
                        + " (missing permission " + resolved + "), treating as optional");
                continue;
            }
        }
        return true;
    }

    private boolean canSuggestArgument(String normalizedCommandName, @Nullable String subCommandName,
            CommandArgument argument, CommandSender sender) {
        if (argument.isSenderParameter()) {
            return false;
        }
        if (!argument.hasPermission()) {
            return true;
        }
        String fallback = buildArgumentPermission(normalizedCommandName, subCommandName, argument);
        String resolved = resolvePermission(argument.getPermission(), fallback);
        ensurePermissionRegistered(resolved, argument.getPermissionDefault(),
                "Argument " + argument.getName() + " for /" + normalizedCommandName
                        + (subCommandName != null ? " " + subCommandName : ""));
        return resolved == null || resolved.isEmpty() || sender.hasPermission(resolved);
    }

    private boolean lacksArgumentPermission(String normalizedCommandName, @Nullable String subCommandName,
            CommandArgument argument, CommandSender sender) {
        if (argument.isSenderParameter()) {
            return false;
        }
        if (!argument.hasPermission()) {
            return false;
        }
        String fallback = buildArgumentPermission(normalizedCommandName, subCommandName, argument);
        String resolved = resolvePermission(argument.getPermission(), fallback);
        ensurePermissionRegistered(resolved, argument.getPermissionDefault(),
                "Argument " + argument.getName() + " for /" + normalizedCommandName
                        + (subCommandName != null ? " " + subCommandName : ""));
        return resolved != null && !resolved.isEmpty() && !sender.hasPermission(resolved);
    }

    private boolean shouldCheckPermission(CommandArgument argument, Object value, Map<String, Object> valuesByName,
            Map<String, CommandArgument> argumentsByName, CommandSender sender) {
        PermissionConditionType condition = argument.getPermissionCondition();
        String[] names = argument.getPermissionConditionArgs();

        List<ArgValue> targets = collectTargets(argument, value, names, valuesByName, argumentsByName);

        switch (condition) {
            case ALWAYS:
                return true;
            case NOT_NULL:
                return targets.stream().anyMatch(av -> av.value != null);
            case SELF:
                return targets.stream().anyMatch(av -> av.value != null && isSenderMatch(sender, av.meta(), av.value()));
            case OTHER:
            case ANY_OTHER:
                return targets.stream().anyMatch(av -> av.value != null && !isSenderMatch(sender, av.meta(), av.value()));
            case DISTINCT:
                return isDistinct(sender, targets, false);
            case ALL_DISTINCT:
                return isDistinct(sender, targets, true);
            case EXISTS:
                return targets.stream().anyMatch(av -> av.value != null);
            case EQUALS:
                return areAllEqual(sender, targets);
            case NOT_EQUALS:
                return areAllDistinct(sender, targets);
            default:
                return false;
        }
    }

    private List<ArgValue> collectTargets(CommandArgument selfArgument, Object selfValue, String[] names,
            Map<String, Object> valuesByName, Map<String, CommandArgument> argumentsByName) {
        List<ArgValue> targets = new ArrayList<>();
        if (names != null && names.length > 0) {
            for (String name : names) {
                Object val = valuesByName.get(name);
                CommandArgument meta = argumentsByName.getOrDefault(name, selfArgument);
                targets.add(new ArgValue(meta, val));
            }
        } else {
            targets.add(new ArgValue(selfArgument, selfValue));
        }
        return targets;
    }

    private boolean isDistinct(CommandSender sender, List<ArgValue> targets, boolean requireAllDistinct) {
        List<ArgValue> nonNull = targets.stream().filter(av -> av.value != null).toList();
        if (nonNull.size() < 2) {
            return false;
        }

        for (int i = 0; i < nonNull.size(); i++) {
            for (int j = i + 1; j < nonNull.size(); j++) {
                ArgValue a = nonNull.get(i);
                ArgValue b = nonNull.get(j);
                boolean equal = areEqual(sender, a.meta(), a.value(), b.meta(), b.value());
                if (requireAllDistinct && equal) {
                    return false;
                }
                if (!requireAllDistinct && !equal) {
                    return true;
                }
            }
        }

        return requireAllDistinct;
    }

    private boolean areAllEqual(CommandSender sender, List<ArgValue> targets) {
        List<ArgValue> nonNull = targets.stream().filter(av -> av.value != null).toList();
        if (nonNull.size() < 2) {
            return false;
        }
        ArgValue first = nonNull.get(0);
        for (int i = 1; i < nonNull.size(); i++) {
            ArgValue other = nonNull.get(i);
            if (!areEqual(sender, first.meta(), first.value(), other.meta(), other.value())) {
                return false;
            }
        }
        return true;
    }

    private boolean areAllDistinct(CommandSender sender, List<ArgValue> targets) {
        return isDistinct(sender, targets, true);
    }

    private boolean isSenderMatch(CommandSender sender, CommandArgument argument, Object value) {
        CompareMode mode = argument.getCompareMode();
        TypeParser<?> parser = typeParserRegistry.findParserForType(argument.getType());
        if (parser != null) {
            try {
                return parser.isSender(sender, value, mode);
            } catch (Exception ignored) {
            }
        }
        return defaultIsSender(sender, value, mode);
    }

    private boolean areEqual(CommandSender sender, CommandArgument leftMeta, Object left, CommandArgument rightMeta,
            Object right) {
        CompareMode leftMode = leftMeta != null ? leftMeta.getCompareMode() : CompareMode.AUTO;
        CompareMode rightMode = rightMeta != null ? rightMeta.getCompareMode() : CompareMode.AUTO;

        TypeParser<?> leftParser = leftMeta != null ? typeParserRegistry.findParserForType(leftMeta.getType()) : null;
        if (leftParser != null) {
            try {
                return leftParser.isEqual(sender, left, right, leftMode);
            } catch (Exception ignored) {
            }
        }

        TypeParser<?> rightParser = rightMeta != null ? typeParserRegistry.findParserForType(rightMeta.getType()) : null;
        if (rightParser != null) {
            try {
                return rightParser.isEqual(sender, left, right, rightMode);
            } catch (Exception ignored) {
            }
        }

        return defaultEquals(sender, left, right, leftMode != null ? leftMode : rightMode);
    }

    private boolean defaultIsSender(CommandSender sender, Object value, CompareMode mode) {
        return defaultEquals(sender, value, sender, mode);
    }

    private boolean defaultEquals(CommandSender sender, Object first, Object second, CompareMode mode) {
        if (first == null || second == null) {
            return first == second;
        }
        if (mode == CompareMode.EQUALS) {
            return first.equals(second);
        }
        if (mode == CompareMode.UUID || mode == CompareMode.AUTO) {
            UUID aUuid = extractUuid(first);
            UUID bUuid = extractUuid(second);
            if (aUuid != null && bUuid != null) {
                return aUuid.equals(bUuid);
            }
            if (mode == CompareMode.UUID) {
                return false;
            }
        }
        if (mode == CompareMode.NAME || mode == CompareMode.AUTO) {
            String aName = extractName(first);
            String bName = extractName(second);
            if (aName != null && bName != null) {
                return aName.equalsIgnoreCase(bName);
            }
            if (mode == CompareMode.NAME) {
                return false;
            }
        }
        return first.equals(second);
    }

    private UUID extractUuid(Object obj) {
        if (obj instanceof org.bukkit.command.CommandSender cs && cs instanceof org.bukkit.entity.Player) {
            return ((org.bukkit.entity.Player) cs).getUniqueId();
        }
        if (obj instanceof org.bukkit.OfflinePlayer op) {
            return op.getUniqueId();
        }
        if (obj instanceof UUID uuid) {
            return uuid;
        }
        try {
            var method = obj.getClass().getMethod("getUniqueId");
            Object res = method.invoke(obj);
            if (res instanceof UUID uuidRes) {
                return uuidRes;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractName(Object obj) {
        if (obj instanceof org.bukkit.command.CommandSender cs) {
            return cs.getName();
        }
        if (obj instanceof org.bukkit.OfflinePlayer op) {
            return op.getName();
        }
        try {
            var method = obj.getClass().getMethod("getName");
            Object res = method.invoke(obj);
            if (res instanceof String s) {
                return s;
            }
        } catch (Exception ignored) {
        }
        return obj.toString();
    }

    private String buildArgumentPermission(String normalizedCommandName, @Nullable String subCommandName, CommandArgument argument) {
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

    private record ArgValue(CommandArgument meta, Object value) {
    }

    /**
     * Gets tab completion suggestions for a command.
     * 
     * @param name   the command name
     * @param sender the command sender
     * @param args   the command arguments
     * @return a list of suggestions
     */
    public List<String> getSuggestions(String name, CommandSender sender, List<String> args) {
        PrefixedLoggerGen.debug(logger, "Getting suggestions for command: " + name + " with args: " + args);

        MagicCommand command = commands.get(name.toLowerCase());
        CommandInfo info = commandInfos.get(name.toLowerCase());
        // Normalize the command name (remove plugin namespace if present)
        String normalizedName = normalizeCommandName(name);
        String baseCommandName = info != null ? info.name().toLowerCase(Locale.ROOT) : normalizedName;

        if (command == null || info == null) {
            PrefixedLoggerGen.debug(logger, "Command not found for suggestions: " + name);
            return Arrays.asList("");
        }

        String commandPermission = resolvePermission(info.permission(),
                "commands." + baseCommandName);
        ensurePermissionRegistered(commandPermission, info.permissionDefault(), info.description());
        String targetSubName = (args != null && !args.isEmpty()) ? args.get(0).toLowerCase(Locale.ROOT) : null;
        if (!commandPermission.isEmpty() && !sender.hasPermission(commandPermission)
                && !hasSubOrArgPermission(command, info, sender, baseCommandName, targetSubName)) {
            PrefixedLoggerGen.debug(logger, "No permission for suggestions: " + commandPermission);
            return Arrays.asList("");
        }

        try {
            List<String> suggestions = generateSuggestions(command, info, sender, args, baseCommandName);
            PrefixedLoggerGen.debug(logger, "Generated suggestions: " + suggestions);
            return suggestions;
        } catch (Exception e) {
            PrefixedLoggerGen.debug(logger, "Error generating suggestions: " + e.getMessage());
            return Arrays.asList("");
        }
    }

    private List<String> generateSuggestions(MagicCommand command, CommandInfo info, CommandSender sender,
            List<String> args, String normalizedCommandName) {
        List<MagicCommand.SubCommandInfo> subCommands = MagicCommand.getSubCommands(command.getClass());
        Method executeMethod = getExecuteMethod(command.getClass());

        PrefixedLoggerGen.debug(logger, "Generating suggestions - executeMethod: " + (executeMethod != null)
                + ", subCommands: " + subCommands.size() + ", args: " + args);

        // If there's only a direct execute method and no subcommands
        if (executeMethod != null && subCommands.isEmpty()) {
            List<CommandArgument> arguments = MagicCommand.getArguments(executeMethod);
            PrefixedLoggerGen.debug(logger, "Using direct method suggestions with " + arguments.size() + " arguments");
            return generateDirectMethodSuggestions(command, arguments, sender, args, normalizedCommandName, null);
        }

        // If there are no subcommands and no execute method
        if (subCommands.isEmpty() && executeMethod == null) {
            PrefixedLoggerGen.debug(logger, "No subcommands and no execute method");
            return Arrays.asList("");
        }

        // If no args and we have subcommands, show subcommands
        if (args.isEmpty() && !subCommands.isEmpty()) {
            PrefixedLoggerGen.debug(logger, "No args, showing subcommands");
            return getAvailableSubCommandsList(subCommands, sender, normalizedCommandName);
        }

        // If we have both execute method and subcommands, and first arg doesn't match
        // any subcommand
        if (executeMethod != null && !subCommands.isEmpty() && args.size() == 1) {
            String currentInput = args.get(0);
            List<String> availableSubCommands = getAvailableSubCommandsList(subCommands, sender, normalizedCommandName);

            // Check if current input matches any subcommand
            boolean matchesSubCommand = availableSubCommands.stream()
                    .anyMatch(sub -> sub.toLowerCase().equals(currentInput.toLowerCase()));

            if (!matchesSubCommand) {
                // Try to provide suggestions for direct execute method
                List<CommandArgument> arguments = MagicCommand.getArguments(executeMethod);
                List<String> directSuggestions = generateDirectMethodSuggestions(command, arguments, sender, args,
                        normalizedCommandName, null);

                // Also include subcommands that start with current input
                List<String> filteredSubCommands = availableSubCommands.stream()
                        .filter(sub -> sub.toLowerCase().startsWith(currentInput.toLowerCase()))
                        .collect(Collectors.toList());

                // Combine both suggestion lists
                List<String> combined = new ArrayList<>(directSuggestions);
                combined.addAll(filteredSubCommands);
                return combined;
            }
        }

        String currentInput = args.isEmpty() ? "" : args.get(args.size() - 1);

        if (args.size() == 1) {
            List<String> availableSubCommands = getAvailableSubCommandsList(subCommands, sender, normalizedCommandName);
            return availableSubCommands.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(currentInput.toLowerCase()))
                    .collect(Collectors.toList());
        }

        String subCommandName = args.get(0).toLowerCase(Locale.ROOT);
        MagicCommand.SubCommandInfo targetSubCommand = null;

        for (MagicCommand.SubCommandInfo subInfo : subCommands) {
            if (matchesSubCommand(subInfo, subCommandName)) {
                targetSubCommand = subInfo;
                break;
            }
        }

        if (targetSubCommand == null) {
            return Arrays.asList("");
        }

        String subPermission = resolvePermission(targetSubCommand.annotation.permission(),
                "commands." + normalizedCommandName + ".subcommand." + targetSubCommand.annotation.name());
        ensurePermissionRegistered(subPermission, targetSubCommand.annotation.permissionDefault(),
                targetSubCommand.annotation.description());
        if (!subPermission.isEmpty() && !sender.hasPermission(subPermission)) {
            return Arrays.asList("");
        }

        List<String> subArgs = args.subList(1, args.size());
        return generateArgumentSuggestions(command, targetSubCommand, sender, subArgs, currentInput,
                normalizedCommandName);
    }

    private List<String> generateDirectMethodSuggestions(MagicCommand command, List<CommandArgument> arguments,
            CommandSender sender, List<String> args, String normalizedCommandName, @Nullable String subCommandName) {
        PrefixedLoggerGen.debug(logger, "generateDirectMethodSuggestions called with " + arguments.size()
                + " arguments and " + args.size() + " args");
        PrefixedLoggerGen.debug(logger, "Raw args: " + args);

        if (arguments.isEmpty()) {
            PrefixedLoggerGen.debug(logger, "No arguments defined for direct method");
            return Arrays.asList("");
        }

        // Log all arguments first
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument arg = arguments.get(i);
            PrefixedLoggerGen.debug(logger, "Raw argument " + i + ": " + arg.getName() + " (type: "
                    + arg.getType().getSimpleName() + ", suggestions: " + arg.getSuggestions() + ")");
        }

        // Build a list of arguments that need user input (skip sender-bound parameters)
        List<ArgumentInfo> userInputArguments = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument arg = arguments.get(i);
            PrefixedLoggerGen.debug(logger,
                    "Checking argument " + i + ": " + arg.getName() + " (type: " + arg.getType().getSimpleName() + ")");
            // Skip explicit sender parameters
            if (arg.getType().equals(CommandSender.class) || arg.isSenderParameter()) {
                PrefixedLoggerGen.debug(logger, "  -> Skipped (sender)");
            } else {
                userInputArguments.add(new ArgumentInfo(i, arg));
                PrefixedLoggerGen.debug(logger, "  -> Added as user input argument");
            }
        }

        PrefixedLoggerGen.debug(logger, "User input arguments: " + userInputArguments.size());
        for (int i = 0; i < userInputArguments.size(); i++) {
            ArgumentInfo info = userInputArguments.get(i);
            PrefixedLoggerGen.debug(logger, "  - User arg " + i + " (orig index " + info.originalIndex + "): "
                    + info.argument.getName() + " (type: " + info.argument.getType().getSimpleName() + ", optional: "
                    + info.argument.isOptional() + ", hasDefault: " + (info.argument.getDefaultValue() != null) + ")");
        }

        if (userInputArguments.isEmpty()) {
            PrefixedLoggerGen.debug(logger, "No arguments require user input");
            return Arrays.asList("");
        }

        // If no args provided, suggest for all possible first arguments (including
        // optional ones)
        if (args.isEmpty()) {
            PrefixedLoggerGen.debug(logger, "No user args provided, generating suggestions for first argument(s)");
            List<String> suggestions = new ArrayList<>();

            // Always include suggestions for the first user argument
            ArgumentInfo firstArg = userInputArguments.get(0);
            PrefixedLoggerGen.debug(logger,
                    "Generating suggestions for first argument: " + firstArg.argument.getName());

            if (canSuggestArgument(normalizedCommandName, subCommandName, firstArg.argument, sender)) {
                List<String> firstArgSuggestions = generateSuggestionsForArgument(command, firstArg.argument, sender,
                        "");
                suggestions.addAll(firstArgSuggestions);
                PrefixedLoggerGen.debug(logger, "First argument suggestions: " + firstArgSuggestions);
            }

            // If first argument is optional, also include suggestions for the second
            // argument
            if ((firstArg.argument.isOptional() || firstArg.argument.getDefaultValue() != null)
                    && userInputArguments.size() > 1) {
                ArgumentInfo secondArg = userInputArguments.get(1);
                PrefixedLoggerGen.debug(logger,
                        "First argument is optional, also generating suggestions for second argument: "
                                + secondArg.argument.getName());

                if (canSuggestArgument(normalizedCommandName, subCommandName, secondArg.argument, sender)) {
                    List<String> secondArgSuggestions = generateSuggestionsForArgument(command, secondArg.argument,
                            sender, "");
                    suggestions.addAll(secondArgSuggestions);
                    PrefixedLoggerGen.debug(logger, "Second argument suggestions: " + secondArgSuggestions);
                }
            }

            PrefixedLoggerGen.debug(logger, "Combined suggestions for empty args: " + suggestions);
            return suggestions.stream().distinct().collect(Collectors.toList());
        }

        // Determine which argument we're currently suggesting for
        int currentArgIndex = args.size() - 1;
        PrefixedLoggerGen.debug(logger,
                "Current argument index: " + currentArgIndex + " (based on args.size() = " + args.size() + ")");

        // Handle the case where we might be suggesting for an argument beyond the
        // current input
        // This happens when optional arguments are skipped
        List<String> suggestions = new ArrayList<>();

        // Current argument suggestions
        if (currentArgIndex < userInputArguments.size()) {
            ArgumentInfo currentArg = userInputArguments.get(currentArgIndex);
            String currentInput = args.get(args.size() - 1);

            PrefixedLoggerGen.debug(logger, "Generating suggestions for current argument " + currentArgIndex + ": "
                    + currentArg.argument.getName() + " with input: '" + currentInput + "'");

            if (canSuggestArgument(normalizedCommandName, subCommandName, currentArg.argument, sender)) {
                suggestions.addAll(generateSuggestionsForArgument(command, currentArg.argument, sender, currentInput));
            }
        }

        // If current argument is optional and we're not at the end, also suggest for
        // next argument
        if (currentArgIndex < userInputArguments.size()) {
            ArgumentInfo currentArg = userInputArguments.get(currentArgIndex);
            if ((currentArg.argument.isOptional() || currentArg.argument.getDefaultValue() != null)
                    && currentArgIndex + 1 < userInputArguments.size()) {

                ArgumentInfo nextArg = userInputArguments.get(currentArgIndex + 1);
                PrefixedLoggerGen.debug(logger, "Current argument is optional, also suggesting for next argument: "
                        + nextArg.argument.getName());

                if (canSuggestArgument(normalizedCommandName, subCommandName, nextArg.argument, sender)) {
                    suggestions.addAll(generateSuggestionsForArgument(command, nextArg.argument, sender, ""));
                }
            }
        }

        return suggestions.stream().distinct().collect(Collectors.toList());
    }

    // Helper class to track argument info with original indices
    private static class ArgumentInfo {
        final int originalIndex;
        final CommandArgument argument;

        ArgumentInfo(int originalIndex, CommandArgument argument) {
            this.originalIndex = originalIndex;
            this.argument = argument;
        }
    }

    private List<String> generateArgumentSuggestions(MagicCommand command, MagicCommand.SubCommandInfo subInfo,
            CommandSender sender, List<String> args, String currentInput, String normalizedCommandName) {
        List<CommandArgument> arguments = MagicCommand.getArguments(subInfo.method);

        if (arguments.isEmpty()) {
            return Arrays.asList("");
        }

        // Build a list of arguments that need user input (skip sender-bound params)
        List<ArgumentInfo> userInputArguments = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument arg = arguments.get(i);
            if (!arg.getType().equals(CommandSender.class) && !arg.isSenderParameter()) {
                userInputArguments.add(new ArgumentInfo(i, arg));
            }
        }

        if (userInputArguments.isEmpty()) {
            return Arrays.asList("");
        }

        int argIndex = Math.max(0, args.size() - 1);
        if (argIndex >= userInputArguments.size()) {
            return Arrays.asList("");
        }

        ArgumentInfo argumentInfo = userInputArguments.get(argIndex);
        CommandArgument argument = argumentInfo.argument;

        if (!canSuggestArgument(normalizedCommandName, subInfo.annotation.name(), argument, sender)) {
            return Arrays.asList("");
        }

        return generateSuggestionsForArgument(command, argument, sender, currentInput);
    }

    private List<String> generateSuggestionsForArgument(MagicCommand command, CommandArgument argument,
            CommandSender sender, String currentInput) {
        PrefixedLoggerGen.debug(logger, "generateSuggestionsForArgument called for argument: " + argument.getName()
                + " with input: '" + currentInput + "'");
        PrefixedLoggerGen.debug(logger, "Argument suggestions: " + argument.getSuggestions());
        PrefixedLoggerGen.debug(logger, "Argument type: " + argument.getType());

        List<String> suggestions = new ArrayList<>();

        // If no explicit suggestions, try to get suggestions from the argument type
        if (argument.getSuggestions().isEmpty()) {
            PrefixedLoggerGen.debug(logger,
                    "No explicit suggestions, getting suggestions for type: " + argument.getType().getSimpleName());
            List<String> typeSuggestions = typeParserRegistry.getSuggestionsForArgumentFiltered(argument,
                    currentInput, sender);
            if (!typeSuggestions.isEmpty()) {
                PrefixedLoggerGen.debug(logger, "Got " + typeSuggestions.size() + " suggestions from type parser");
                return typeSuggestions;
            }
        }

        // Process explicit suggestions
        for (String suggestionSource : argument.getSuggestions()) {
            PrefixedLoggerGen.debug(logger, "Processing suggestion source: '" + suggestionSource + "'");

            if (suggestionSource.contains("|")) {
                String[] sources = suggestionSource.split("\\|");
                for (String source : sources) {
                    List<String> sourceSuggestions = processSuggestionSource(command, source.trim(), sender,
                            currentInput);
                    PrefixedLoggerGen.debug(logger, "Source '" + source.trim() + "' generated: " + sourceSuggestions);
                    suggestions.addAll(sourceSuggestions);
                }
            } else {
                List<String> sourceSuggestions = processSuggestionSource(command, suggestionSource, sender,
                        currentInput);
                PrefixedLoggerGen.debug(logger, "Source '" + suggestionSource + "' generated: " + sourceSuggestions);
                suggestions.addAll(sourceSuggestions);
            }
        }

        List<String> filteredSuggestions = suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(currentInput.toLowerCase()))
                .distinct()
                .collect(Collectors.toList());

        PrefixedLoggerGen.debug(logger, "Final filtered suggestions: " + filteredSuggestions);
        return filteredSuggestions;
    }

    @SuppressWarnings("unchecked")
    private List<String> processSuggestionSource(MagicCommand command, String source, CommandSender sender,
            String currentInput) {
        PrefixedLoggerGen.debug(logger, "Processing suggestion source: " + source);

        if (typeParserRegistry.isSpecialSuggestion(source)) {
            return typeParserRegistry.parseSuggestionFiltered(source, currentInput, sender);
        }

        if (source.startsWith("{") && source.endsWith("}")) {
            String content = source.substring(1, source.length() - 1);
            return Arrays.asList(content.split(",\\s*"));
        }

        try {
            Method method = command.getClass().getMethod(source);
            Object result = method.invoke(command);

            if (result instanceof String[]) {
                return Arrays.asList((String[]) result);
            } else if (result instanceof List) {
                return (List<String>) result;
            }
        } catch (Exception e) {
            PrefixedLoggerGen.debug(logger, "Failed to call suggestion method " + source + ": " + e.getMessage());
        }

        try {
            Method method = command.getClass().getMethod(source, Player.class);
            Player player = sender instanceof Player ? (Player) sender : null;
            Object result = method.invoke(command, player);

            if (result instanceof String[]) {
                return Arrays.asList((String[]) result);
            } else if (result instanceof List) {
                return (List<String>) result;
            }
        } catch (Exception e) {
            PrefixedLoggerGen.debug(logger,
                    "Failed to call suggestion method " + source + " with Player parameter: " + e.getMessage());
        }

        if ("@sender".equalsIgnoreCase(source)) {
            return Collections.emptyList();
        }

        return Arrays.asList("");
    }

    private boolean hasSubOrArgPermission(MagicCommand command, CommandInfo info, CommandSender sender,
            String baseCommandName, @Nullable String targetSubName) {
        List<MagicCommand.SubCommandInfo> subs = MagicCommand.getSubCommands(command.getClass());
        if (targetSubName != null) {
            for (MagicCommand.SubCommandInfo subInfo : subs) {
                if (!matchesSubCommand(subInfo, targetSubName)) {
                    continue;
                }
                String subPermission = resolvePermission(subInfo.annotation.permission(),
                        "commands." + baseCommandName + ".subcommand." + subInfo.annotation.name());
                if (subPermission.isEmpty() || sender.hasPermission(subPermission)
                        || hasArgumentPermissionOverride(baseCommandName, subInfo.annotation.name(), sender)) {
                    return true;
                }
            }
        }
        // Fallback: any subcommand permission granted
        for (MagicCommand.SubCommandInfo subInfo : subs) {
            String subPermission = resolvePermission(subInfo.annotation.permission(),
                    "commands." + baseCommandName + ".subcommand." + subInfo.annotation.name());
            if (subPermission.isEmpty() || sender.hasPermission(subPermission)
                    || hasArgumentPermissionOverride(baseCommandName, subInfo.annotation.name(), sender)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasArgumentPermissionOverride(String baseCommandName, @Nullable String subCommandName,
            CommandSender sender) {
        String prefix = "commands." + baseCommandName;
        if (subCommandName != null && !subCommandName.isEmpty()) {
            prefix += ".subcommand." + subCommandName;
        }
        String argPrefix = prefix + ".argument.";
        String argPrefixNoSegment = prefix + ".";
        for (org.bukkit.permissions.PermissionAttachmentInfo pai : sender.getEffectivePermissions()) {
            if (!pai.getValue()) {
                continue;
            }
            String perm = pai.getPermission();
            if (perm == null) {
                continue;
            }
            if (perm.startsWith(argPrefix) || perm.startsWith(argPrefixNoSegment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates a usage string for a command based on its structure.
     * 
     * @param command the command instance
     * @param info    the command info
     * @return formatted usage string
     */
    public String generateUsage(MagicCommand command, CommandInfo info) {
        List<MagicCommand.SubCommandInfo> subCommands = MagicCommand.getSubCommands(command.getClass());
        Method executeMethod = getExecuteMethod(command.getClass());

        StringBuilder usage = new StringBuilder();
        usage.append("/").append(info.name());

        // If command has direct execute method and no subcommands
        if (executeMethod != null && subCommands.isEmpty()) {
            List<CommandArgument> arguments = MagicCommand.getArguments(executeMethod);
            appendArgumentsToUsage(usage, arguments);
        }
        // If command has only subcommands
        else if (!subCommands.isEmpty() && executeMethod == null) {
            usage.append(" <");
            usage.append(subCommands.stream()
                    .map(sub -> sub.annotation.name())
                    .collect(Collectors.joining(" | ")));
            usage.append(">");
        }
        // If command has both execute method and subcommands
        else if (executeMethod != null && !subCommands.isEmpty()) {
            List<CommandArgument> arguments = MagicCommand.getArguments(executeMethod);

            // Show direct arguments as optional since subcommands are available
            if (!arguments.isEmpty()) {
                appendArgumentsToUsage(usage, arguments, true);
            }

            usage.append(" OR /").append(info.name()).append(" <");
            usage.append(subCommands.stream()
                    .map(sub -> sub.annotation.name())
                    .collect(Collectors.joining(" | ")));
            usage.append(">");
        }

        return usage.toString();
    }

    /**
     * Generates detailed usage strings for all subcommands.
     * 
     * @param command the command instance
     * @param info    the command info
     * @return list of usage strings for each subcommand
     */
    public List<String> generateSubCommandUsages(MagicCommand command, CommandInfo info) {
        List<String> usages = new ArrayList<>();
        List<MagicCommand.SubCommandInfo> subCommands = MagicCommand.getSubCommands(command.getClass());

        for (MagicCommand.SubCommandInfo subInfo : subCommands) {
            StringBuilder usage = new StringBuilder();
            usage.append("/").append(info.name()).append(" ").append(subInfo.annotation.name());

            List<CommandArgument> arguments = MagicCommand.getArguments(subInfo.method);
            appendArgumentsToUsage(usage, arguments);

            if (!subInfo.annotation.description().isEmpty()) {
                usage.append(" - ").append(subInfo.annotation.description());
            }

            usages.add(usage.toString());
        }

        return usages;
    }

    /**
     * Appends arguments to usage string.
     * 
     * @param usage     the usage string builder
     * @param arguments the list of arguments
     */
    private void appendArgumentsToUsage(StringBuilder usage, List<CommandArgument> arguments) {
        appendArgumentsToUsage(usage, arguments, false);
    }

    /**
     * Appends arguments to usage string.
     * 
     * @param usage         the usage string builder
     * @param arguments     the list of arguments
     * @param forceOptional whether to force all arguments to be displayed as
     *                      optional
     */
    private void appendArgumentsToUsage(StringBuilder usage, List<CommandArgument> arguments, boolean forceOptional) {
        for (CommandArgument arg : arguments) {
            // Skip CommandSender/sender arguments in usage
            if (arg.getType().equals(CommandSender.class) || arg.isSenderParameter()) {
                continue;
            }

            usage.append(" ");

            boolean isOptional = forceOptional || arg.isOptional() || arg.getDefaultValue() != null;

            if (isOptional) {
                usage.append("[");
            } else {
                usage.append("<");
            }

            // Generate argument name based on type and suggestions
            String argName = generateArgumentName(arg);
            usage.append(argName);

            if (isOptional) {
                usage.append("]");
            } else {
                usage.append(">");
            }
        }
    }

    private String buildUsage(CommandInfo info, MagicCommand.SubCommandInfo subInfo, List<CommandArgument> arguments) {
        StringBuilder usage = new StringBuilder("/").append(info.name());
        if (subInfo != null) {
            usage.append(" ").append(subInfo.annotation.name());
        }
        for (CommandArgument arg : arguments) {
            if (arg.getType().equals(CommandSender.class) || arg.isSenderParameter()) {
                continue;
            }
            boolean optional = arg.isOptional() || arg.getDefaultValue() != null;
            usage.append(" ");
            usage.append(optional ? "[" : "<");
            usage.append(generateArgumentName(arg));
            usage.append(optional ? "]" : ">");
        }
        return usage.toString();
    }

    /**
     * Generates a descriptive name for an argument based on its type and
     * suggestions.
     * 
     * @param argument the command argument
     * @return descriptive argument name
     */
    private String generateArgumentName(CommandArgument argument) {
        String displayName = argument.getName();
        String typeName = argument.getType().getSimpleName().toLowerCase();

        // Check if argument has suggestions that can provide better naming
        for (String suggestion : argument.getSuggestions()) {
            if (suggestion.startsWith("{") && suggestion.endsWith("}")) {
                String content = suggestion.substring(1, suggestion.length() - 1);
                String[] options = content.split(",\\s*");
                if (options.length <= 3) {
                    // If few options, show them directly
                    return String.join("|", options);
                } else {
                    // If many options, show type with hint
                    return typeName + ":" + options[0] + "|...";
                }
            } else if (suggestion.equals("@players")) {
                return displayName != null ? displayName : "player";
            } else if (suggestion.equals("@worlds")) {
                return displayName != null ? displayName : "world";
            } else if (suggestion.equals("@sender")) {
                return displayName != null ? displayName : "player";
            }
        }

        // Fallback to type-based naming
        switch (typeName) {
            case "player":
                return displayName != null ? displayName : "player";
            case "world":
                return displayName != null ? displayName : "world";
            case "string":
                return displayName != null ? displayName : "text";
            case "integer":
                return displayName != null ? displayName : "number";
            case "long":
                return displayName != null ? displayName : "number";
            case "boolean":
                return displayName != null ? displayName : "true|false";
            default:
                return displayName != null ? displayName : typeName;
        }
    }

    private String getAvailableSubCommands(List<MagicCommand.SubCommandInfo> subCommands, CommandSender sender,
            String commandName) {
        return String.join(", ", getAvailableSubCommandsList(subCommands, sender, commandName));
    }

    private List<String> getAvailableSubCommandsList(List<MagicCommand.SubCommandInfo> subCommands,
            CommandSender sender, String commandName) {
        Set<String> available = new LinkedHashSet<>();

        for (MagicCommand.SubCommandInfo subInfo : subCommands) {
            String subPermission = resolvePermission(subInfo.annotation.permission(),
                    "commands." + commandName + ".subcommand." + subInfo.annotation.name());
            if (subPermission.isEmpty() || sender.hasPermission(subPermission)) {
                available.add(subInfo.annotation.name());
                for (String alias : subInfo.annotation.aliases()) {
                    available.add(alias);
                }
            }
        }

        return new ArrayList<>(available);
    }

    private boolean matchesSubCommand(MagicCommand.SubCommandInfo subInfo, String input) {
        if (subInfo.annotation.name().equalsIgnoreCase(input)) {
            return true;
        }
        for (String alias : subInfo.annotation.aliases()) {
            if (alias.equalsIgnoreCase(input)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalizes command name by removing plugin namespace if present.
     * 
     * @param commandName the command name (possibly with namespace)
     * @return the normalized command name
     */
    private String normalizeCommandName(String commandName) {
        if (commandName.contains(":")) {
            return commandName.substring(commandName.indexOf(":") + 1);
        }
        return commandName;
    }

    private void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
        if (node == null || node.isEmpty()) {
            return;
        }
        var pluginManager = org.bukkit.Bukkit.getPluginManager();
        Permission existing = pluginManager.getPermission(node);
        PermissionDefault bukkitDefault = toBukkitDefault(defaultValue);
        if (existing == null) {
            Permission permission = new Permission(node, description != null ? description : "", bukkitDefault);
            pluginManager.addPermission(permission);
            PrefixedLoggerGen.debug(logger, "Registered permission node: " + node + " (default " + bukkitDefault + ")");
        } else if (existing.getDefault() != bukkitDefault) {
            existing.setDefault(bukkitDefault);
            PrefixedLoggerGen.debug(logger, "Updated permission default for node: " + node + " -> " + bukkitDefault);
        }
    }

    private PermissionDefault toBukkitDefault(MagicPermissionDefault defaultValue) {
        if (defaultValue == null) {
            return PermissionDefault.OP;
        }
        return switch (defaultValue) {
            case FALSE -> PermissionDefault.FALSE;
            case NOT_OP -> PermissionDefault.NOT_OP;
            case TRUE -> PermissionDefault.TRUE;
            default -> PermissionDefault.OP;
        };
    }

    /**
     * Checks if a command is registered.
     * 
     * @param name the command name
     * @return true if registered
     */
    public boolean isRegistered(String name) {
        return commands.containsKey(name.toLowerCase());
    }

    /**
     * Gets all registered commands.
     * 
     * @return a collection of MagicCommand
     */
    public Collection<MagicCommand> getAll() {
        return commands.values();
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
}
