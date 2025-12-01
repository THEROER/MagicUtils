package dev.ua.theroer.magicutils.commands;

import lombok.Getter;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerGen;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.annotations.*;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

        String normalizedName = normalizeCommandName(name);

        MagicCommand command = commands.get(name.toLowerCase());
        CommandInfo info = commandInfos.get(name.toLowerCase());

        if (command == null || info == null) {
            PrefixedLoggerGen.debug(logger,
                    "Command not found: " + name + ". Available commands: " + commands.keySet());
            return CommandResult.notFound();
        }

        PrefixedLoggerGen.debug(logger, "Found command: " + name + ", checking permissions...");

        if (info.permission()) {
            String permission = permissionPrefix + ".commands." + normalizedName + ".use";
            if (!sender.hasPermission(permission)) {
                PrefixedLoggerGen.debug(logger,
                        "Permission denied for " + sender.getName() + " on permission: " + permission);
                return CommandResult.failure(InternalMessages.CMD_NO_PERMISSION.get());
            }
        }

        try {
            return executeCommand(command, info, sender, args, normalizedName);
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
            return executeDirectMethod(command, info, executeMethod, sender, args);
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
        String subCommandName = args.get(0).toLowerCase();
        PrefixedLoggerGen.debug(logger, "Looking for subcommand: " + subCommandName);

        MagicCommand.SubCommandInfo targetSubCommand = null;

        for (MagicCommand.SubCommandInfo subInfo : subCommands) {
            if (subInfo.annotation.name().toLowerCase().equals(subCommandName)) {
                targetSubCommand = subInfo;
                break;
            }
        }

        if (targetSubCommand == null) {
            PrefixedLoggerGen.debug(logger, "Subcommand not found: " + subCommandName + ". Available: " +
                    subCommands.stream().map(s -> s.annotation.name()).collect(Collectors.toList()));
            return CommandResult.failure(InternalMessages.CMD_UNKNOWN_SUBCOMMAND.get("subcommand", subCommandName));
        }

        PrefixedLoggerGen.debug(logger, "Found subcommand: " + subCommandName + ", checking permissions...");

        if (targetSubCommand.annotation.permission()) {
            String permission = permissionPrefix + ".commands." + normalizedCommandName +
                    ".subcommand." + targetSubCommand.annotation.name() + ".use";
            if (!sender.hasPermission(permission)) {
                PrefixedLoggerGen.debug(logger,
                        "Permission denied for subcommand " + subCommandName + " on permission: " + permission);
                return CommandResult.failure(InternalMessages.CMD_NO_PERMISSION.get());
            }
        }

        List<String> subArgs = args.size() > 1 ? args.subList(1, args.size()) : new ArrayList<>();
        PrefixedLoggerGen.debug(logger, "Executing subcommand: " + subCommandName + " with args: " + subArgs);

        return executeSubCommand(command, info, targetSubCommand, sender, subArgs);
    }

    private CommandResult executeDirectMethod(MagicCommand command, CommandInfo info, Method executeMethod, CommandSender sender,
            List<String> args) {
        try {
            List<CommandArgument> arguments = MagicCommand.getArguments(executeMethod);

            PrefixedLoggerGen.debug(logger, "Parsing " + arguments.size() + " arguments for direct execute method");
            PrefixedLoggerGen.debug(logger,
                    "Method parameter types: " + Arrays.toString(executeMethod.getParameterTypes()));

            Object[] methodArgs = parseArgumentsForDirectMethod(arguments, args, sender);

            if (methodArgs == null) {
                PrefixedLoggerGen.debug(logger, "Failed to parse arguments for direct execute method");
                String usage = buildUsage(info, null, arguments);
                return CommandResult.failure(InternalMessages.CMD_INVALID_ARGUMENTS.get("usage", usage));
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

        } catch (Exception e) {
            PrefixedLoggerGen.error(logger, "Error executing direct method: " + e.getMessage());
            e.printStackTrace();
            return CommandResult.failure(InternalMessages.CMD_EXECUTION_ERROR.get());
        }
    }

    private Object[] parseArgumentsForDirectMethod(List<CommandArgument> arguments, List<String> args,
            CommandSender sender) {
        Object[] result = new Object[arguments.size()];
        boolean[] filled = new boolean[arguments.size()];

        // First pass: auto-fill CommandSender arguments
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument argument = arguments.get(i);
            if (argument.getType().equals(CommandSender.class)) {
                result[i] = sender;
                filled[i] = true;
                PrefixedLoggerGen.debug(logger, "Auto-filled CommandSender argument " + i + " (" + argument.getName()
                        + "): " + sender.getName());
            }
        }

        // Second pass: try to match user arguments intelligently
        List<String> remainingArgs = new ArrayList<>(args);

        for (int i = 0; i < arguments.size(); i++) {
            if (filled[i])
                continue; // Skip already filled arguments

            CommandArgument argument = arguments.get(i);
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
                if (argument.hasPermission() && !sender.hasPermission(argument.getPermission())) {
                    PrefixedLoggerGen.debug(logger, "Permission denied for argument " + argument.getName()
                            + " on permission: " + argument.getPermission());
                    return null;
                }

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

            if (providedByUser && argument.hasPermission() && !sender.hasPermission(argument.getPermission())) {
                PrefixedLoggerGen.debug(logger, "Permission denied for argument " + argument.getName()
                        + " on permission: " + argument.getPermission());
                return null;
            }

            result[i] = convertArgument(value, argument.getType(), sender);
            PrefixedLoggerGen.debug(logger, "Parsed argument " + i + " (" + argument.getName() + "): " + result[i]
                    + " (type: " + (result[i] != null ? result[i].getClass().getSimpleName() : "null") + ")");

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
            CommandSender sender, List<String> args) {
        try {
            Method method = subInfo.method;
            List<CommandArgument> arguments = MagicCommand.getArguments(method);

            PrefixedLoggerGen.debug(logger,
                    "Parsing " + arguments.size() + " arguments for method: " + method.getName());
            PrefixedLoggerGen.debug(logger, "Method parameter types: " + Arrays.toString(method.getParameterTypes()));

            Object[] methodArgs = parseArgumentsForDirectMethod(arguments, args, sender);

            if (methodArgs == null) {
                PrefixedLoggerGen.debug(logger, "Failed to parse arguments for subcommand");
                String usage = buildUsage(info, subInfo, arguments);
                return CommandResult.failure(InternalMessages.CMD_INVALID_ARGUMENTS.get("usage", usage));
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

        // Fallback to string if no parser could handle it
        PrefixedLoggerGen.debug(logger,
                "No parser found for type " + type.getSimpleName() + ", returning string value");
        return value;
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

        // Normalize the command name (remove plugin namespace if present)
        String normalizedName = normalizeCommandName(name);

        MagicCommand command = commands.get(name.toLowerCase());
        CommandInfo info = commandInfos.get(name.toLowerCase());

        if (command == null || info == null) {
            PrefixedLoggerGen.debug(logger, "Command not found for suggestions: " + name);
            return Arrays.asList("");
        }

        if (info.permission()) {
            String permission = permissionPrefix + ".commands." + normalizedName + ".use";
            if (!sender.hasPermission(permission)) {
                PrefixedLoggerGen.debug(logger, "No permission for suggestions: " + permission);
                return Arrays.asList("");
            }
        }

        try {
            List<String> suggestions = generateSuggestions(command, info, sender, args, normalizedName);
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
            return generateDirectMethodSuggestions(command, arguments, sender, args);
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
                List<String> directSuggestions = generateDirectMethodSuggestions(command, arguments, sender, args);

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

        String subCommandName = args.get(0).toLowerCase();
        MagicCommand.SubCommandInfo targetSubCommand = null;

        for (MagicCommand.SubCommandInfo subInfo : subCommands) {
            if (subInfo.annotation.name().toLowerCase().equals(subCommandName)) {
                targetSubCommand = subInfo;
                break;
            }
        }

        if (targetSubCommand == null) {
            return Arrays.asList("");
        }

        if (targetSubCommand.annotation.permission()) {
            String permission = permissionPrefix + ".commands." + normalizedCommandName +
                    ".subcommand." + targetSubCommand.annotation.name() + ".use";
            if (!sender.hasPermission(permission)) {
                return Arrays.asList("");
            }
        }

        List<String> subArgs = args.subList(1, args.size());
        return generateArgumentSuggestions(command, targetSubCommand, sender, subArgs, currentInput);
    }

    private List<String> generateDirectMethodSuggestions(MagicCommand command, List<CommandArgument> arguments,
            CommandSender sender, List<String> args) {
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

        // Build a list of arguments that need user input (skip only CommandSender, not
        // its subclasses)
        List<ArgumentInfo> userInputArguments = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument arg = arguments.get(i);
            PrefixedLoggerGen.debug(logger,
                    "Checking argument " + i + ": " + arg.getName() + " (type: " + arg.getType().getSimpleName() + ")");
            // Only skip if it's exactly CommandSender, not subclasses like Player
            if (arg.getType().equals(CommandSender.class)) {
                PrefixedLoggerGen.debug(logger, "  -> Skipped (CommandSender)");
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

            if (!firstArg.argument.hasPermission() || sender.hasPermission(firstArg.argument.getPermission())) {
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

                if (!secondArg.argument.hasPermission() || sender.hasPermission(secondArg.argument.getPermission())) {
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

            if (!currentArg.argument.hasPermission() || sender.hasPermission(currentArg.argument.getPermission())) {
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

                if (!nextArg.argument.hasPermission() || sender.hasPermission(nextArg.argument.getPermission())) {
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
            CommandSender sender, List<String> args, String currentInput) {
        List<CommandArgument> arguments = MagicCommand.getArguments(subInfo.method);

        if (arguments.isEmpty()) {
            return Arrays.asList("");
        }

        // Build a list of arguments that need user input (skip only CommandSender, not
        // its subclasses)
        List<ArgumentInfo> userInputArguments = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument arg = arguments.get(i);
            // Only skip if it's exactly CommandSender, not subclasses like Player
            if (!arg.getType().equals(CommandSender.class)) {
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

        if (argument.hasPermission() && !sender.hasPermission(argument.getPermission())) {
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
                    .collect(java.util.stream.Collectors.joining("|")));
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
                    .collect(java.util.stream.Collectors.joining("|")));
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
            // Skip CommandSender arguments in usage
            if (arg.getType().equals(CommandSender.class)) {
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
            if (arg.getType().equals(CommandSender.class)) {
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
                return "player";
            } else if (suggestion.equals("@worlds")) {
                return "world";
            } else if (suggestion.equals("@sender")) {
                return "player";
            }
        }

        // Fallback to type-based naming
        switch (typeName) {
            case "player":
                return "player";
            case "world":
                return "world";
            case "string":
                return "text";
            case "integer":
                return "number";
            case "long":
                return "number";
            case "boolean":
                return "true|false";
            default:
                return typeName;
        }
    }

    private String getAvailableSubCommands(List<MagicCommand.SubCommandInfo> subCommands, CommandSender sender,
            String commandName) {
        return String.join(", ", getAvailableSubCommandsList(subCommands, sender, commandName));
    }

    private List<String> getAvailableSubCommandsList(List<MagicCommand.SubCommandInfo> subCommands,
            CommandSender sender, String commandName) {
        List<String> available = new ArrayList<>();

        for (MagicCommand.SubCommandInfo subInfo : subCommands) {
            if (subInfo.annotation.permission()) {
                String permission = permissionPrefix + ".commands." + commandName +
                        ".subcommand." + subInfo.annotation.name() + ".use";
                if (sender.hasPermission(permission)) {
                    available.add(subInfo.annotation.name());
                }
            } else {
                available.add(subInfo.annotation.name());
            }
        }

        return available;
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
}
