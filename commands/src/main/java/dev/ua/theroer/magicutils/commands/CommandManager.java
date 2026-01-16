package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import lombok.Getter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages registration, execution, and tab completion of commands.
 *
 * @param <S> sender type
 */
public class CommandManager<S> {
    private final CommandLogger logger;
    private final CommandPlatform<S> platform;

    private final Map<String, MagicCommand> commands = new ConcurrentHashMap<>();
    private final Map<String, CommandInfo> commandInfos = new ConcurrentHashMap<>();
    private final Map<MagicCommand, CommandInfo> commandInfoByInstance = new ConcurrentHashMap<>();
    private final Map<MagicCommand, CommandCache<S>> commandCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> executeMethodCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<MagicCommand.SubCommandInfo>> subCommandInfoCache = new ConcurrentHashMap<>();
    private final Map<Method, List<CommandArgument>> argumentCache = new ConcurrentHashMap<>();
    private final String permissionPrefix;
    private final String pluginName;
    @Getter
    private final TypeParserRegistry<S> typeParserRegistry;

    /**
     * Constructs a new CommandManager.
     *
     * @param permissionPrefix the prefix for permissions
     * @param pluginName       the plugin name for namespaced commands
     * @param logger           command logger implementation
     * @param platform         platform adapter
     * @param typeParserRegistry type parser registry
     */
    public CommandManager(String permissionPrefix,
                          String pluginName,
                          CommandLogger logger,
                          CommandPlatform<S> platform,
                          TypeParserRegistry<S> typeParserRegistry) {
        this.permissionPrefix = permissionPrefix;
        this.pluginName = pluginName != null ? pluginName.toLowerCase(Locale.ROOT) : "";
        this.logger = logger != null ? logger : CommandLogger.noop();
        this.platform = Objects.requireNonNull(platform, "platform");
        this.typeParserRegistry = Objects.requireNonNull(typeParserRegistry, "typeParserRegistry");
        this.logger.debug("CommandManager initialized with permission prefix: " + permissionPrefix
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
        commandInfoByInstance.put(command, info);

        String namespacedName = "";
        if (!pluginName.isEmpty()) {
            namespacedName = pluginName + ":" + name;
            commands.put(namespacedName, command);
            commandInfos.put(namespacedName, info);
        }

        logger.debug("Registered command: " + name + (namespacedName.isEmpty() ? "" : " and " + namespacedName)
                + " with class: " + command.getClass().getSimpleName());

        for (String alias : info.aliases()) {
            String aliasLower = alias.toLowerCase();
            commands.put(aliasLower, command);
            commandInfos.put(aliasLower, info);

            String namespacedAlias = "";
            if (!pluginName.isEmpty()) {
                // Also register alias with plugin namespace
                namespacedAlias = pluginName + ":" + aliasLower;
                commands.put(namespacedAlias, command);
                commandInfos.put(namespacedAlias, info);
            }

            logger.debug("Registered alias: " + aliasLower + (namespacedAlias.isEmpty() ? "" : " and " + namespacedAlias)
                    + " for command: " + name);
        }

        List<CommandAction<S>> subCommands = getSubCommandActions(command);
        CommandAction<S> directAction = getDirectAction(command, info);

        logger.debug("Command " + name + " has " + subCommands.size() + " subcommands: " +
                subCommands.stream().map(CommandAction::fullPath).collect(Collectors.toList()));
        logger.debug("Command " + name + " has direct execute handler: " + (directAction != null));
    }

    /**
     * Gets the direct execute method if it exists.
     * 
     * @param clazz the command class
     * @return the execute method or null if not found
     */
    private Method findExecuteMethod(Class<?> clazz) {
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals("execute") && !method.isAnnotationPresent(SubCommand.class)) {
                    return method;
                }
            }
        }
        return null;
    }

    private Method getExecuteMethodCached(Class<?> clazz) {
        return executeMethodCache.computeIfAbsent(clazz, this::findExecuteMethod);
    }

    private List<MagicCommand.SubCommandInfo> getSubCommandInfoCached(Class<?> clazz) {
        return subCommandInfoCache.computeIfAbsent(clazz, MagicCommand::getSubCommands);
    }

    private List<CommandArgument> getArgumentsCached(Method method) {
        return argumentCache.computeIfAbsent(method, MagicCommand::getArguments);
    }

    private CommandCache<S> getCommandCache(MagicCommand command, CommandInfo info) {
        if (command == null || info == null) {
            return new CommandCache<>(0, null, List.of(), null, new SubCommandNode<>(""));
        }
        CommandCache<S> cached = commandCache.get(command);
        if (cached == null || cached.isStale(command)) {
            cached = buildCommandCache(command, info);
            commandCache.put(command, cached);
        }
        return cached;
    }

    private CommandCache<S> buildCommandCache(MagicCommand command, CommandInfo info) {
        Class<?> clazz = command.getClass();
        Method executeMethod = getExecuteMethodCached(clazz);
        CommandAction<S> baseDirectAction = null;
        if (executeMethod != null) {
            List<CommandArgument> arguments = getArgumentsCached(executeMethod);
            baseDirectAction = CommandAction.forMethod(info.name(), info.description(), info.permission(),
                    info.permissionDefault(), arguments, executeMethod);
        }

        List<CommandAction<S>> staticSubActions = new ArrayList<>();
        for (MagicCommand.SubCommandInfo subInfo : getSubCommandInfoCached(clazz)) {
            List<CommandArgument> arguments = getArgumentsCached(subInfo.method);
            staticSubActions.add(CommandAction.forSubCommand(subInfo, arguments));
        }

        List<MagicCommand.DynamicSubCommand> dynamicSubs = command.getDynamicSubCommands();
        MagicCommand.DynamicExecute dynamicExecute = command.getDynamicExecute();

        List<CommandAction<S>> combinedSubActions = resolveSubCommandActions(staticSubActions, dynamicSubs);
        CommandAction<S> resolvedDirectAction = resolveDirectAction(info, baseDirectAction, dynamicExecute);
        SubCommandNode<S> tree = buildSubCommandTree(combinedSubActions);

        return new CommandCache<>(dynamicSubs.size(), dynamicExecute, combinedSubActions, resolvedDirectAction, tree);
    }

    private SubCommandNode<S> getSubCommandTree(MagicCommand command, CommandInfo info) {
        return getCommandCache(command, info).tree();
    }

    private List<CommandAction<S>> resolveSubCommandActions(List<CommandAction<S>> staticActions,
                                                           List<MagicCommand.DynamicSubCommand> dynamicSubs) {
        if (staticActions == null || staticActions.isEmpty()) {
            staticActions = List.of();
        }
        List<CommandAction<S>> resolved = new ArrayList<>(staticActions);
        if (dynamicSubs != null) {
            for (MagicCommand.DynamicSubCommand dynamic : dynamicSubs) {
                CommandAction<S> action = CommandAction.forDynamicSubCommand(dynamic.spec(),
                        castExecutor(dynamic.spec().executor()));
                if (dynamic.replaceExisting()) {
                    removeMatchingSubCommands(resolved, action);
                }
                resolved.add(action);
            }
        }
        return resolved;
    }

    private CommandAction<S> resolveDirectAction(CommandInfo info,
                                                 @Nullable CommandAction<S> baseAction,
                                                 @Nullable MagicCommand.DynamicExecute dynamicExecute) {
        if (dynamicExecute != null && (dynamicExecute.replaceExisting() || baseAction == null)) {
            return CommandAction.forExecute(info.name(), info.description(), info.permission(),
                    info.permissionDefault(), dynamicExecute.arguments(),
                    castExecutor(dynamicExecute.executor()));
        }
        if (baseAction != null) {
            return baseAction;
        }
        if (dynamicExecute != null) {
            return CommandAction.forExecute(info.name(), info.description(), info.permission(),
                    info.permissionDefault(), dynamicExecute.arguments(),
                    castExecutor(dynamicExecute.executor()));
        }
        return null;
    }

    CommandAction<S> getDirectAction(MagicCommand command, CommandInfo info) {
        CommandInfo resolvedInfo = info != null ? info : commandInfoByInstance.get(command);
        CommandCache<S> cache = getCommandCache(command, resolvedInfo);
        return cache.directAction();
    }

    List<CommandAction<S>> getSubCommandActions(MagicCommand command) {
        CommandInfo resolvedInfo = commandInfoByInstance.get(command);
        CommandCache<S> cache = getCommandCache(command, resolvedInfo);
        return cache.subActions();
    }

    @SuppressWarnings("unchecked")
    private CommandExecutor<S> castExecutor(CommandExecutor<?> executor) {
        return (CommandExecutor<S>) executor;
    }

    private void removeMatchingSubCommands(List<CommandAction<S>> actions, CommandAction<S> replacement) {
        if (actions.isEmpty() || replacement == null) {
            return;
        }
        List<String> keys = replacement.allKeysLower();
        List<String> replacementPath = normalizePath(replacement.path());
        actions.removeIf(existing -> pathEquals(existing.path(), replacementPath) && hasAnyKey(existing, keys));
    }

    private boolean hasAnyKey(CommandAction<S> action, List<String> keys) {
        if (action == null || keys == null || keys.isEmpty()) {
            return false;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            if (action.matches(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean pathEquals(List<String> left, List<String> right) {
        List<String> leftNorm = normalizePath(left);
        List<String> rightNorm = normalizePath(right);
        if (leftNorm.size() != rightNorm.size()) {
            return false;
        }
        for (int i = 0; i < leftNorm.size(); i++) {
            if (!leftNorm.get(i).equalsIgnoreCase(rightNorm.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Executes a command by name.
     * 
     * @param name   the command name
     * @param sender the command sender
     * @param args   the command arguments
     * @return the result of command execution
     */
    public CommandResult execute(String name, S sender, List<String> args) {
        try {
            return executeInternal(name, sender, args, false);
        } catch (CommandExecutionException e) {
            return CommandResult.failure(e.getUserMessage());
        }
    }

    /**
     * Executes a command, optionally bubbling unexpected failures.
     *
     * @param name command name
     * @param sender sender handle
     * @param args command arguments
     * @return command result
     * @throws CommandExecutionException when execution fails unexpectedly and bubbling is enabled
     */
    public CommandResult executeOrThrow(String name, S sender, List<String> args) throws CommandExecutionException {
        return executeInternal(name, sender, args, true);
    }

    /**
     * Checks if a sender can access the command (base permission or any sub/arg permission).
     *
     * @param name command label or alias
     * @param sender sender handle
     * @param targetSubName optional first subcommand token (for more accurate checks)
     * @return true if accessible
     */
    public boolean canAccessCommand(String name, S sender, @Nullable String targetSubName) {
        if (name == null) {
            return false;
        }
        MagicCommand command = commands.get(name.toLowerCase(Locale.ROOT));
        CommandInfo info = commandInfos.get(name.toLowerCase(Locale.ROOT));
        if (command == null || info == null) {
            return false;
        }
        String baseCommandName = info.name().toLowerCase(Locale.ROOT);
        return hasCommandPermission(command, info, sender, baseCommandName, targetSubName);
    }

    private CommandResult executeInternal(String name, S sender, List<String> args, boolean bubbleErrors)
            throws CommandExecutionException {
        logger.debug("Attempting to execute command: " + name + " with args: " + args);

        MagicCommand command = commands.get(name.toLowerCase(Locale.ROOT));
        CommandInfo info = commandInfos.get(name.toLowerCase(Locale.ROOT));

        if (command == null || info == null) {
            logger.debug("Command not found: " + name + ". Available commands: " + commands.keySet());
            return CommandResult.notFound();
        }

        logger.debug("Found command: " + name + ", checking permissions...");

        String baseCommandName = info.name().toLowerCase(Locale.ROOT);
        String targetSubName = (args != null && !args.isEmpty()) ? args.get(0).toLowerCase(Locale.ROOT) : null;
        if (!hasCommandPermission(command, info, sender, baseCommandName, targetSubName)) {
            return CommandResult.failure(InternalMessages.CMD_NO_PERMISSION.get());
        }

        try {
            return executeCommand(command, info, sender, args, baseCommandName, bubbleErrors);
        } catch (CommandExecutionException e) {
            if (bubbleErrors) {
                throw e;
            }
            logger.error("Error executing command " + name + ": " + e.getMessage(), e.getCause());
            return CommandResult.failure(e.getUserMessage());
        } catch (Exception e) {
            logger.error("Error executing command " + name + ": " + e.getMessage(), e);
            if (bubbleErrors) {
                throw new CommandExecutionException(InternalMessages.CMD_EXECUTION_ERROR.get(), e);
            }
            return CommandResult.failure(InternalMessages.CMD_EXECUTION_ERROR.get());
        }
    }

    private boolean hasCommandPermission(MagicCommand command, CommandInfo info, S sender,
            String baseCommandName, @Nullable String targetSubName) {
        String commandPermission = resolvePermission(info.permission(),
                "commands." + baseCommandName);
        platform.ensurePermissionRegistered(commandPermission, info.permissionDefault(), info.description());
        if (commandPermission.isEmpty()) {
            return true;
        }
        if (platform.hasPermission(sender, commandPermission, info.permissionDefault())) {
            return true;
        }
        if (hasSubOrArgPermission(command, info, sender, baseCommandName, targetSubName)) {
            return true;
        }
        logger.debug("Permission denied for " + platform.getName(sender) + " on permission: " + commandPermission);
        return false;
    }

    private CommandResult executeCommand(MagicCommand command, CommandInfo info, S sender,
            List<String> args, String normalizedCommandName, boolean bubbleErrors) throws CommandExecutionException {
        List<CommandAction<S>> subCommands = getSubCommandActions(command);
        CommandAction<S> directAction = getDirectAction(command, info);

        logger.debug("Executing command with " + subCommands.size()
                + " available subcommands and execute handler: " + (directAction != null));

        // If there's a direct execute handler and no subcommands, or if there are no args
        if (directAction != null && (subCommands.isEmpty() || args.isEmpty())) {
            logger.debug("Using direct execute handler");
            return executeAction(command, info, directAction, sender, args, normalizedCommandName, null, bubbleErrors);
        }

        // If there are subcommands but no execute handler and no args
        if (subCommands.isEmpty() && directAction == null) {
            logger.debug("No subcommands and no execute handler found, returning success");
            return CommandResult.success(InternalMessages.CMD_EXECUTED.get());
        }

        // If there are subcommands but no args provided
        if (args.isEmpty() && !subCommands.isEmpty()) {
            String availableSubCommands = getAvailableSubCommands(subCommands, sender, normalizedCommandName);
            logger.debug("No arguments provided, available subcommands: " + availableSubCommands);
            return CommandResult
                    .failure(InternalMessages.CMD_SPECIFY_SUBCOMMAND.get("subcommands", availableSubCommands));
        }

        SubCommandNode<S> root = getSubCommandTree(command, info);
        SubCommandTraversal<S> traversal = traverseSubCommands(root, args);

        if (traversal.consumed() == 0) {
            String subCommandName = args.get(0).toLowerCase(Locale.ROOT);
            logger.debug("Subcommand not found: " + subCommandName + ". Available: " +
                    getAvailableSubCommands(subCommands, sender, normalizedCommandName));
            return CommandResult.failure(InternalMessages.CMD_UNKNOWN_SUBCOMMAND.get("subcommand", subCommandName));
        }

        if (traversal.lastActionNode() == null) {
            if (traversal.consumed() >= args.size()) {
                List<String> path = args.subList(0, traversal.consumed());
                String available = String.join(", ",
                        getAvailableSubCommandsList(subCommands, sender, normalizedCommandName, path));
                logger.debug("No subcommand at path, available: " + available);
                return CommandResult.failure(
                        InternalMessages.CMD_SPECIFY_SUBCOMMAND.get("subcommands", available));
            }
            String unknown = args.get(traversal.consumed());
            List<String> path = args.subList(0, traversal.consumed());
            String available = String.join(", ",
                    getAvailableSubCommandsList(subCommands, sender, normalizedCommandName, path));
            logger.debug("Subcommand not found: " + unknown + ". Available: " + available);
            return CommandResult.failure(InternalMessages.CMD_UNKNOWN_SUBCOMMAND.get("subcommand", unknown));
        }

        CommandAction<S> targetSubCommand = traversal.lastActionNode().action();
        int consumed = traversal.lastActionIndex();
        String subCommandName = targetSubCommand.fullPath();
        logger.debug("Found subcommand: " + subCommandName + ", checking permissions...");

        String subPermission = resolvePermission(targetSubCommand.permission(),
                "commands." + normalizedCommandName + ".subcommand." + targetSubCommand.permissionSegment());
        platform.ensurePermissionRegistered(subPermission, targetSubCommand.permissionDefault(),
                targetSubCommand.description());
        if (!subPermission.isEmpty()
                && !platform.hasPermission(sender, subPermission, targetSubCommand.permissionDefault())
                && !hasArgumentPermissionOverride(normalizedCommandName, subCommandName, sender)) {
            logger.debug("Permission denied for subcommand " + subCommandName + " on permission: " + subPermission);
            return CommandResult.failure(InternalMessages.CMD_NO_PERMISSION.get());
        }

        List<String> subArgs = args.size() > consumed ? args.subList(consumed, args.size()) : new ArrayList<>();
        logger.debug("Executing subcommand: " + subCommandName + " with args: " + subArgs);

        return executeAction(command, info, targetSubCommand, sender, subArgs, normalizedCommandName,
                subCommandName, bubbleErrors);
    }

    private CommandResult executeAction(MagicCommand command, CommandInfo info, CommandAction<S> action,
            S sender, List<String> args, String normalizedCommandName, @Nullable String subCommandName,
            boolean bubbleErrors) throws CommandExecutionException {
        if (action == null) {
            return CommandResult.failure(InternalMessages.CMD_EXECUTION_ERROR.get());
        }
        try {
            List<CommandArgument> arguments = action.arguments();
            String label = subCommandName != null ? "subcommand " + subCommandName : "direct execute";

            logger.debug("Parsing " + arguments.size() + " arguments for " + label);
            if (action.method() != null) {
                logger.debug("Method parameter types: " + Arrays.toString(action.method().getParameterTypes()));
            }

            Object[] methodArgs = parseArgumentsForDirectMethod(arguments, args, sender, normalizedCommandName,
                    subCommandName);

            if (methodArgs == null) {
                logger.debug("Failed to parse arguments for " + label);
                String usage = buildUsage(info, subCommandName, arguments);
                return CommandResult.failure(InternalMessages.CMD_INVALID_ARGUMENTS.get("usage", usage));
            }

            if (!validateArgumentPermissions(normalizedCommandName, subCommandName, arguments, methodArgs, sender)) {
                return CommandResult.failure(InternalMessages.CMD_NO_PERMISSION.get());
            }

            for (int i = 0; i < methodArgs.length; i++) {
                logger.debug("Method arg[" + i + "]: "
                                + (methodArgs[i] != null
                                        ? methodArgs[i].getClass().getSimpleName() + "=" + methodArgs[i]
                                        : "null"));
            }

            Object result;
            if (action.method() != null) {
                logger.debug("Invoking " + label + " method with parsed arguments");
                result = action.method().invoke(command, methodArgs);
            } else if (action.executor() != null) {
                logger.debug("Invoking " + label + " executor with parsed arguments");
                CommandExecution<S> execution = new CommandExecution<>(command, normalizedCommandName, subCommandName,
                        sender, args, arguments, methodArgs);
                result = action.executor().execute(execution);
            } else {
                return CommandResult.failure(InternalMessages.CMD_EXECUTION_ERROR.get());
            }

            CommandResult commandResult = result instanceof CommandResult ? (CommandResult) result
                    : CommandResult.success();
            logger.debug(label + " executed successfully, result: " + commandResult.isSuccess());

            return commandResult;

        } catch (SenderMismatchException e) {
            logger.debug("Sender mismatch: " + e.getMessage());
            return CommandResult.failure(e.getMessage());
        } catch (Exception e) {
            logger.error("Error executing " + (subCommandName != null ? "subcommand" : "direct command") + ": "
                    + e.getMessage(), e);
            if (bubbleErrors) {
                throw new CommandExecutionException(InternalMessages.CMD_EXECUTION_ERROR.get(), e);
            }
            return CommandResult.failure(InternalMessages.CMD_EXECUTION_ERROR.get());
        }
    }

    private Object[] parseArgumentsForDirectMethod(List<CommandArgument> arguments, List<String> args,
            S sender, String normalizedCommandName, @Nullable String subCommandName) {
        Object[] result = new Object[arguments.size()];
        boolean[] filled = new boolean[arguments.size()];
        OptionIndex optionIndex = buildOptionIndex(arguments);
        ParsedOptions parsedOptions = optionIndex.hasOptions()
                ? parseOptions(args, optionIndex, false)
                : new ParsedOptions(args != null ? new ArrayList<>(args) : new ArrayList<>(),
                        new HashMap<>(), new HashSet<>(), null, false);
        if (parsedOptions == null) {
            return null;
        }

        // First pass: auto-fill sender arguments
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument argument = arguments.get(i);
            if (isSenderArgument(argument)) {
                try {
                    result[i] = platform.resolveSenderArgument(sender, argument);
                    filled[i] = true;
                    logger.debug("Auto-filled sender argument " + i + " (" + argument.getName()
                            + "): " + platform.getName(sender));
                } catch (SenderMismatchException ex) {
                    logger.debug("Sender mismatch for argument " + argument.getName() + ": "
                            + ex.getMessage());
                    throw ex;
                }
            }
        }

        // Option arguments (including flags)
        if (optionIndex.hasOptions()) {
            for (int i = 0; i < arguments.size(); i++) {
                if (filled[i]) {
                    continue;
                }
                CommandArgument argument = arguments.get(i);
                if (isSenderArgument(argument) || !argument.isOption()) {
                    continue;
                }
                if (argument.isFlag() && !parsedOptions.values().containsKey(argument)) {
                    String defaultValue = argument.getDefaultValue();
                    if (defaultValue == null) {
                        defaultValue = "false";
                    }
                    result[i] = convertArgument(defaultValue, argument.getType(), sender);
                    filled[i] = true;
                    continue;
                }
                String optionValue = parsedOptions.values().get(argument);
                if (optionValue == null) {
                    continue;
                }
                result[i] = convertArgument(optionValue, argument.getType(), sender);
                filled[i] = true;
                if (result[i] == null && !argument.isOptional()) {
                    logger.debug("Failed to convert option value for argument " + argument.getName()
                            + " from value: " + optionValue);
                    return null;
                }
            }
        }

        // Second pass: try to match user arguments intelligently
        List<String> remainingArgs = new ArrayList<>(parsedOptions.positionals());

        for (int i = 0; i < arguments.size(); i++) {
            if (filled[i])
                continue; // Skip already filled arguments

            CommandArgument argument = arguments.get(i);
            if (argument.isFlag()) {
                continue;
            }

            if (lacksArgumentPermission(normalizedCommandName, subCommandName, argument, sender)) {
                logger.debug("Skipping argument " + argument.getName() + " due to missing permission, setting to default/null");
                if (argument.getDefaultValue() != null) {
                    result[i] = convertArgument(argument.getDefaultValue(), argument.getType(), sender);
                } else {
                    result[i] = null;
                }
                filled[i] = true;
                if (!remainingArgs.isEmpty() && !isSenderArgument(argument) && !argument.isOption()) {
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
                logger.debug("Matched user arg '" + bestMatch + "' to parameter " + i + " (" + argument.getName() + ")");
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

            if (argument.isFlag()) {
                String defaultValue = argument.getDefaultValue();
                if (defaultValue == null) {
                    defaultValue = "false";
                }
                result[i] = convertArgument(defaultValue, argument.getType(), sender);
                filled[i] = true;
                continue;
            }

            if (argument.isGreedy()) {
                // Consume all remaining args joined by space
                if (userArgIndex < remainingArgs.size()) {
                    value = String.join(" ", remainingArgs.subList(userArgIndex, remainingArgs.size()));
                    userArgIndex = remainingArgs.size();
                    providedByUser = true;
                    logger.debug("Greedy parameter " + i + " (" + argument.getName() + ") consumed remaining args: " + value);
                } else if (argument.getDefaultValue() != null) {
                    value = argument.getDefaultValue();
                    logger.debug("Used default value for greedy parameter " + i + " (" + argument.getName() + "): " + value);
                } else if (argument.isOptional()) {
                    value = null;
                    logger.debug("Used null for optional greedy parameter " + i + " (" + argument.getName() + ")");
                } else {
                    logger.debug("Missing required greedy argument at position " + i + ": " + argument.getName());
                    return null;
                }
            } else {
                if (userArgIndex < remainingArgs.size()) {
                    value = remainingArgs.get(userArgIndex);
                    userArgIndex++;
                    providedByUser = true;
                    logger.debug("Filled remaining parameter " + i + " (" + argument.getName() + ") with user arg: " + value);
                } else if (argument.getDefaultValue() != null) {
                    value = argument.getDefaultValue();
                    logger.debug("Used default value for parameter " + i + " (" + argument.getName() + "): " + value);
                } else if (argument.isOptional()) {
                    value = null;
                    logger.debug("Used null for optional parameter " + i + " (" + argument.getName() + ")");
                } else {
                    logger.debug("Missing required argument at position " + i + ": " + argument.getName());
                    return null;
                }
            }

            result[i] = convertArgument(value, argument.getType(), sender);
            logger.debug("Parsed argument " + i + " (" + argument.getName() + "): " + result[i]
                    + " (type: " + (result[i] != null ? result[i].getClass().getSimpleName() : "null") + ")");

            // If optional argument failed to convert, do not consume the user arg; let next parameter try it
            if (providedByUser && result[i] == null && argument.isOptional()) {
                userArgIndex = Math.max(0, userArgIndex - 1);
                logger.debug("Conversion failed for optional argument " + argument.getName()
                        + ", reusing value for next parameter");
                continue;
            }

            // Check if conversion failed
            if (value != null && result[i] == null && !argument.isOptional()) {
                logger.debug("Failed to convert argument " + i + " (" + argument.getName()
                        + ") from value: " + value + " to type: " + argument.getType().getSimpleName());
                return null;
            }
        }

        return result;
    }

    private boolean isArgumentMatch(CommandArgument argument, String userArg, S sender) {
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

    private boolean argumentHasSuggestion(CommandArgument argument, String value, S sender) {
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

    private Object convertArgument(String value, Class<?> type, S sender) {
        logger.debug("Converting argument: '" + value + "' to type: " + type.getSimpleName());

        // Use the argument parser registry to convert the argument
        Object result = typeParserRegistry.parse(value, type, sender);

        if (result != null || value == null) {
            return result;
        }

        // Fallback: only return raw value for String targets; otherwise treat as unparsed
        if (type.equals(String.class)) {
            logger.debug("No parser found for type " + type.getSimpleName() + ", returning string value");
            return value;
        }

        logger.debug("No parser found for type " + type.getSimpleName() + ", returning null");
        return null;
    }

    /**
     * Returns true when the argument represents the sender parameter.
     *
     * @param argument command argument
     * @return true if sender argument
     */
    public boolean isSenderArgument(CommandArgument argument) {
        return argument.isSenderParameter() || platform.isSenderType(argument.getType());
    }

    private boolean validateArgumentPermissions(String normalizedCommandName, @Nullable String subCommandName,
            List<CommandArgument> arguments, Object[] values, S sender) {
        Map<String, Object> valuesByName = new HashMap<>();
        Map<String, CommandArgument> argumentsByName = new HashMap<>();

        for (int i = 0; i < arguments.size(); i++) {
            valuesByName.put(arguments.get(i).getName(), values[i]);
            argumentsByName.put(arguments.get(i).getName(), arguments.get(i));
        }

        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument argument = arguments.get(i);
            if (isSenderArgument(argument) || !argument.hasPermission()) {
                continue;
            }

            if (!shouldCheckPermission(argument, values[i], valuesByName, argumentsByName, sender)) {
                continue;
            }

            String fallback = buildArgumentPermission(normalizedCommandName, subCommandName, argument);
            String resolved = resolvePermission(argument.getPermission(), fallback);
            platform.ensurePermissionRegistered(resolved, argument.getPermissionDefault(),
                    "Argument " + argument.getName() + " for /" + normalizedCommandName
                            + (subCommandName != null ? " " + subCommandName : ""));
            if (resolved != null && !resolved.isEmpty()
                    && !platform.hasPermission(sender, resolved, argument.getPermissionDefault())) {
                logger.debug("Skipping permission check for argument " + argument.getName()
                        + " (missing permission " + resolved + "), treating as optional");
                continue;
            }
        }
        return true;
    }

    private boolean canSuggestArgument(String normalizedCommandName, @Nullable String subCommandName,
            CommandArgument argument, S sender) {
        if (isSenderArgument(argument)) {
            return false;
        }
        if (!argument.hasPermission()) {
            return true;
        }
        String fallback = buildArgumentPermission(normalizedCommandName, subCommandName, argument);
        String resolved = resolvePermission(argument.getPermission(), fallback);
        platform.ensurePermissionRegistered(resolved, argument.getPermissionDefault(),
                "Argument " + argument.getName() + " for /" + normalizedCommandName
                        + (subCommandName != null ? " " + subCommandName : ""));
        return resolved == null || resolved.isEmpty()
                || platform.hasPermission(sender, resolved, argument.getPermissionDefault());
    }

    private boolean lacksArgumentPermission(String normalizedCommandName, @Nullable String subCommandName,
            CommandArgument argument, S sender) {
        if (isSenderArgument(argument)) {
            return false;
        }
        if (!argument.hasPermission()) {
            return false;
        }
        String fallback = buildArgumentPermission(normalizedCommandName, subCommandName, argument);
        String resolved = resolvePermission(argument.getPermission(), fallback);
        platform.ensurePermissionRegistered(resolved, argument.getPermissionDefault(),
                "Argument " + argument.getName() + " for /" + normalizedCommandName
                        + (subCommandName != null ? " " + subCommandName : ""));
        return resolved != null && !resolved.isEmpty()
                && !platform.hasPermission(sender, resolved, argument.getPermissionDefault());
    }

    private boolean shouldCheckPermission(CommandArgument argument, Object value, Map<String, Object> valuesByName,
            Map<String, CommandArgument> argumentsByName, S sender) {
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

    private boolean isDistinct(S sender, List<ArgValue> targets, boolean requireAllDistinct) {
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

    private boolean areAllEqual(S sender, List<ArgValue> targets) {
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

    private boolean areAllDistinct(S sender, List<ArgValue> targets) {
        return isDistinct(sender, targets, true);
    }

    private boolean isSenderMatch(S sender, CommandArgument argument, Object value) {
        CompareMode mode = argument.getCompareMode();
        TypeParser<S, ?> parser = typeParserRegistry.findParserForType(argument.getType());
        if (parser != null) {
            try {
                return parser.isSender(sender, value, mode);
            } catch (Exception ignored) {
            }
        }
        return defaultIsSender(sender, value, mode);
    }

    private boolean areEqual(S sender, CommandArgument leftMeta, Object left, CommandArgument rightMeta,
            Object right) {
        CompareMode leftMode = leftMeta != null ? leftMeta.getCompareMode() : CompareMode.AUTO;
        CompareMode rightMode = rightMeta != null ? rightMeta.getCompareMode() : CompareMode.AUTO;

        TypeParser<S, ?> leftParser = leftMeta != null ? typeParserRegistry.findParserForType(leftMeta.getType()) : null;
        if (leftParser != null) {
            try {
                return leftParser.isEqual(sender, left, right, leftMode);
            } catch (Exception ignored) {
            }
        }

        TypeParser<S, ?> rightParser = rightMeta != null ? typeParserRegistry.findParserForType(rightMeta.getType()) : null;
        if (rightParser != null) {
            try {
                return rightParser.isEqual(sender, left, right, rightMode);
            } catch (Exception ignored) {
            }
        }

        return defaultEquals(sender, left, right, leftMode != null ? leftMode : rightMode);
    }

    private boolean defaultIsSender(S sender, Object value, CompareMode mode) {
        return ComparisonUtils.isSender(sender, value, mode);
    }

    private boolean defaultEquals(S sender, Object first, Object second, CompareMode mode) {
        return ComparisonUtils.isEqual(first, second, mode);
    }

    String buildArgumentPermission(String normalizedCommandName, @Nullable String subCommandName, CommandArgument argument) {
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

    private String normalizeSubCommandSegment(@Nullable String subCommandName) {
        if (subCommandName == null) {
            return "";
        }
        String trimmed = subCommandName.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.replaceAll("\\s+", ".");
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
    public List<String> getSuggestions(String name, S sender, List<String> args) {
        logger.debug("Getting suggestions for command: " + name + " with args: " + args);

        MagicCommand command = commands.get(name.toLowerCase());
        CommandInfo info = commandInfos.get(name.toLowerCase());
        // Normalize the command name (remove plugin namespace if present)
        String normalizedName = normalizeCommandName(name);
        String baseCommandName = info != null ? info.name().toLowerCase(Locale.ROOT) : normalizedName;

        if (command == null || info == null) {
            logger.debug("Command not found for suggestions: " + name);
            return Collections.emptyList();
        }

        String targetSubName = (args != null && !args.isEmpty()) ? args.get(0).toLowerCase(Locale.ROOT) : null;
        if (!hasCommandPermission(command, info, sender, baseCommandName, targetSubName)) {
            logger.debug("No permission for suggestions");
            return Collections.emptyList();
        }

        try {
            List<String> suggestions = generateSuggestions(command, info, sender, args, baseCommandName);
            logger.debug("Generated suggestions: " + suggestions);
            return suggestions;
        } catch (Exception e) {
            logger.debug("Error generating suggestions: " + e);
            return Collections.emptyList();
        }
    }

    private List<String> generateSuggestions(MagicCommand command, CommandInfo info, S sender,
            List<String> args, String normalizedCommandName) {
        List<CommandAction<S>> subCommands = getSubCommandActions(command);
        CommandAction<S> directAction = getDirectAction(command, info);

        logger.debug("Generating suggestions - executeHandler: " + (directAction != null)
                + ", subCommands: " + subCommands.size() + ", args: " + args);

        // If there's only a direct execute handler and no subcommands
        if (directAction != null && subCommands.isEmpty()) {
            List<CommandArgument> arguments = directAction.arguments();
            logger.debug("Using direct method suggestions with " + arguments.size() + " arguments");
            return generateDirectMethodSuggestions(command, arguments, sender, args, normalizedCommandName, null, Collections.emptyMap());
        }

        // If there are no subcommands and no execute handler
        if (subCommands.isEmpty() && directAction == null) {
            logger.debug("No subcommands and no execute handler");
            return Collections.emptyList();
        }

        SubCommandNode<S> root = buildSubCommandTree(subCommands);

        // If no args and we have subcommands, show top-level subcommands
        if (args.isEmpty() && !subCommands.isEmpty()) {
            logger.debug("No args, showing subcommands");
            return getAvailableChildNames(root, sender, normalizedCommandName);
        }

        String currentInput = args.isEmpty() ? "" : args.get(args.size() - 1);
        List<String> fixedTokens = args.size() > 1 ? args.subList(0, args.size() - 1) : List.of();

        SubCommandTraversal<S> traversal = traverseSubCommands(root, fixedTokens);
        if (traversal.consumed() < fixedTokens.size() && traversal.lastActionNode() == null) {
            return Collections.emptyList();
        }

        SubCommandNode<S> node = traversal.node();
        CommandAction<S> matchedAction = traversal.lastActionNode() != null ? traversal.lastActionNode().action() : null;
        int actionIndex = traversal.lastActionIndex();

        if (matchedAction != null && actionIndex < fixedTokens.size()) {
            String subPermission = resolvePermission(matchedAction.permission(),
                    "commands." + normalizedCommandName + ".subcommand." + matchedAction.permissionSegment());
            platform.ensurePermissionRegistered(subPermission, matchedAction.permissionDefault(),
                    matchedAction.description());
            if (!subPermission.isEmpty()
                    && !platform.hasPermission(sender, subPermission, matchedAction.permissionDefault())) {
                return Collections.emptyList();
            }
            List<String> subArgs = args.subList(actionIndex, args.size());
            return generateArgumentSuggestions(command, matchedAction, sender, subArgs, currentInput,
                    normalizedCommandName, Collections.emptyMap()); // Initial call, no previous parsed args yet
        }

        List<String> availableChildren = getAvailableChildNames(node, sender, normalizedCommandName);
        List<String> filteredChildren = availableChildren.stream()
                .filter(sub -> sub.toLowerCase(Locale.ROOT).startsWith(currentInput.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());

        boolean matchesChild = availableChildren.stream()
                .anyMatch(sub -> sub.equalsIgnoreCase(currentInput));

        if (matchedAction == null) {
            if (directAction != null && fixedTokens.isEmpty() && !matchesChild) {
                List<CommandArgument> arguments = directAction.arguments();
                List<String> directSuggestions = generateDirectMethodSuggestions(command, arguments, sender, args,
                        normalizedCommandName, null, new HashMap<>()); // Pass empty map here
                List<String> combined = new ArrayList<>(directSuggestions);
                combined.addAll(filteredChildren);
                return combined;
            }
            return filteredChildren;
        }

        String subPermission = resolvePermission(matchedAction.permission(),
                "commands." + normalizedCommandName + ".subcommand." + matchedAction.permissionSegment());
        platform.ensurePermissionRegistered(subPermission, matchedAction.permissionDefault(),
                matchedAction.description());
        if (!subPermission.isEmpty()
                && !platform.hasPermission(sender, subPermission, matchedAction.permissionDefault())) {
            return filteredChildren.isEmpty() ? Collections.emptyList() : filteredChildren;
        }

        List<String> subArgs = args.subList(actionIndex, args.size());
        List<String> argumentSuggestions = generateArgumentSuggestions(command, matchedAction, sender, subArgs,
                currentInput, normalizedCommandName, Collections.emptyMap()); // Initial call, no previous parsed args yet

        if (!filteredChildren.isEmpty()) {
            if (!matchesChild) {
                List<String> combined = new ArrayList<>(argumentSuggestions);
                combined.addAll(filteredChildren);
                return combined;
            }
            return filteredChildren;
        }

        return argumentSuggestions;
    }

    private List<String> generateDirectMethodSuggestions(MagicCommand command, List<CommandArgument> arguments,
            S sender, List<String> args, String normalizedCommandName, @Nullable String subCommandName,
            @NotNull Map<String, Object> previousParsedArguments) {
        OptionIndex optionIndex = buildOptionIndex(arguments);
        if (optionIndex.hasOptions()) {
            return generateSuggestionsWithOptions(command, arguments, optionIndex, sender, args,
                    normalizedCommandName, subCommandName, previousParsedArguments);
        }
        return generatePositionalSuggestions(command, arguments, sender, args, normalizedCommandName, subCommandName,
                previousParsedArguments);
    }

    private List<String> generatePositionalSuggestions(MagicCommand command, List<CommandArgument> arguments,
            S sender, List<String> args, String normalizedCommandName, @Nullable String subCommandName,
            @NotNull Map<String, Object> allParsedArguments) {
        return generatePositionalSuggestions(command, arguments, sender, args, normalizedCommandName, subCommandName,
                allParsedArguments, Collections.emptySet());
    }

    private List<String> generatePositionalSuggestions(MagicCommand command, List<CommandArgument> arguments,
            S sender, List<String> args, String normalizedCommandName, @Nullable String subCommandName,
            @NotNull Map<String, Object> allParsedArguments, @NotNull Set<CommandArgument> skipArguments) {
        logger.debug("generatePositionalSuggestions called with " + arguments.size()
                + " arguments and " + args.size() + " args");
        logger.debug("Raw args: " + args);

        if (arguments.isEmpty()) {
            logger.debug("No arguments defined for direct method");
            return Collections.emptyList();
        }

        // Build a list of arguments that need user input (skip sender-bound parameters)
        allParsedArguments = new HashMap<>(allParsedArguments);
        List<ArgumentInfo> userInputArguments = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument arg = arguments.get(i);
            if (skipArguments.contains(arg)) {
                continue;
            }
            // Skip explicit sender parameters, they are handled by platform.resolveSenderArgument and not user input
            if (isSenderArgument(arg)) {
                try {
                    allParsedArguments.put(arg.getName(), platform.resolveSenderArgument(sender, arg));
                } catch (SenderMismatchException ex) {
                    // Ignore, this should not prevent suggestions and will be caught during execution
                }
            } else if (arg.isFlag()) {
                // Flags are options, not positional arguments, handled elsewhere
                continue;
            } else {
                userInputArguments.add(new ArgumentInfo(i, arg));
            }
        }

        int currentArgPosition = 0;
        for (int i = 0; i < args.size() - 1; i++) {
            if (currentArgPosition >= userInputArguments.size()) {
                break;
            }

            ArgumentInfo argInfo = userInputArguments.get(currentArgPosition);
            String token = args.get(i);
            Object parsedValue = convertArgument(token, argInfo.argument.getType(), sender);

            if (parsedValue != null) {
                allParsedArguments.put(argInfo.argument.getName(), parsedValue);
                currentArgPosition++;
            } else if (argInfo.argument.isOptional()) {
                allParsedArguments.put(argInfo.argument.getName(), null);
                currentArgPosition++;
                i--; 
            } else {
                break;
            }
        }

        List<String> suggestions = new ArrayList<>();

        if (args.isEmpty()) {
            if (!userInputArguments.isEmpty()) {
                ArgumentInfo firstArg = userInputArguments.get(0);
                if (canSuggestArgument(normalizedCommandName, subCommandName, firstArg.argument, sender)) {
                    suggestions.addAll(generateSuggestionsForArgument(command, firstArg.argument, sender, "",
                            allParsedArguments));
                }
            }
            return suggestions.stream().distinct().collect(Collectors.toList());
        }

        String currentInput = args.get(args.size() - 1);
        int targetArgumentIndex = currentArgPosition;

        if (targetArgumentIndex < userInputArguments.size()) {
            ArgumentInfo targetArg = userInputArguments.get(targetArgumentIndex);
            if (canSuggestArgument(normalizedCommandName, subCommandName, targetArg.argument, sender)) {
                suggestions.addAll(generateSuggestionsForArgument(command, targetArg.argument, sender, currentInput,
                        allParsedArguments));
            }
        }

        if (targetArgumentIndex < userInputArguments.size()) {
            ArgumentInfo targetArg = userInputArguments.get(targetArgumentIndex);
            if ((targetArg.argument.isOptional() || targetArg.argument.getDefaultValue() != null)
                    && targetArgumentIndex + 1 < userInputArguments.size()) {
                ArgumentInfo nextArg = userInputArguments.get(targetArgumentIndex + 1);
                if (canSuggestArgument(normalizedCommandName, subCommandName, nextArg.argument, sender)) {
                    suggestions.addAll(generateSuggestionsForArgument(command, nextArg.argument, sender, "",
                            allParsedArguments));
                }
            }
        }

        return suggestions.stream().distinct().collect(Collectors.toList());
    }

    private List<String> generateSuggestionsWithOptions(MagicCommand command, List<CommandArgument> arguments,
            OptionIndex optionIndex, S sender, List<String> args,
            String normalizedCommandName, @Nullable String subCommandName,
            @NotNull Map<String, Object> allParsedArguments) {
        if (arguments.isEmpty()) {
            return Collections.emptyList();
        }
        String currentInput = args.isEmpty() ? "" : args.get(args.size() - 1);
        List<String> priorTokens = args.size() > 1 ? args.subList(0, args.size() - 1) : List.of();
        ParsedOptions parsed = parseOptions(priorTokens, optionIndex, true);
        if (parsed == null) {
            return Collections.emptyList();
        }

        for (Map.Entry<CommandArgument, String> entry : parsed.values().entrySet()) {
            CommandArgument arg = entry.getKey();
            String value = entry.getValue();
            Object converted = convertArgument(value, arg.getType(), sender);
            if (converted != null) {
                allParsedArguments.put(arg.getName(), converted);
            }
        }
        
        CommandArgument pending = parsed.pendingValue();
        if (pending != null) {
            if (!canSuggestArgument(normalizedCommandName, subCommandName, pending, sender)) {
                return Collections.emptyList();
            }
            return generateSuggestionsForArgument(command, pending, sender, currentInput, allParsedArguments);
        }

        boolean optionsTerminated = parsed.optionsTerminated();
        boolean currentIsOption = !optionsTerminated && isOptionPrefix(currentInput);
        List<String> suggestions = new ArrayList<>();
        Set<CommandArgument> providedByOptions = parsed.usedOptions() != null
                ? new HashSet<>(parsed.usedOptions())
                : Collections.emptySet();

        if (!currentIsOption) {
            List<String> positional = new ArrayList<>(parsed.positionals());
            positional.add(currentInput);
            suggestions.addAll(generatePositionalSuggestions(command, arguments, sender, positional,
                    normalizedCommandName, subCommandName, allParsedArguments, providedByOptions));
        }

        if (!optionsTerminated && (currentInput.isEmpty() || currentIsOption)) {
            suggestions.addAll(buildOptionSuggestions(optionIndex, parsed.usedOptions(), currentInput,
                    normalizedCommandName, subCommandName, sender, allParsedArguments));
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

    private static final class OptionIndex {
        private final Map<String, CommandArgument> shortOptions;
        private final Map<String, CommandArgument> longOptions;
        private final List<CommandArgument> optionArguments;

        private OptionIndex(Map<String, CommandArgument> shortOptions,
                            Map<String, CommandArgument> longOptions,
                            List<CommandArgument> optionArguments) {
            this.shortOptions = shortOptions;
            this.longOptions = longOptions;
            this.optionArguments = optionArguments;
        }

        boolean hasOptions() {
            return !optionArguments.isEmpty();
        }

        List<CommandArgument> optionArguments() {
            return optionArguments;
        }

        CommandArgument resolve(OptionToken token) {
            if (token == null) {
                return null;
            }
            String key = token.keyLower();
            if (token.longForm()) {
                return longOptions.get(key);
            }
            return shortOptions.get(key);
        }
    }

    private record OptionToken(String key, boolean longForm, String value, boolean hasInlineValue) {
        String keyLower() {
            return key != null ? key.toLowerCase(Locale.ROOT) : "";
        }
    }

    private static final class ParsedOptions {
        private final List<String> positionals;
        private final Map<CommandArgument, String> values;
        private final Set<CommandArgument> usedOptions;
        private final CommandArgument pendingValue;
        private final boolean optionsTerminated;

        private ParsedOptions(List<String> positionals,
                              Map<CommandArgument, String> values,
                              Set<CommandArgument> usedOptions,
                              CommandArgument pendingValue,
                              boolean optionsTerminated) {
            this.positionals = positionals;
            this.values = values;
            this.usedOptions = usedOptions;
            this.pendingValue = pendingValue;
            this.optionsTerminated = optionsTerminated;
        }

        List<String> positionals() {
            return positionals;
        }

        Map<CommandArgument, String> values() {
            return values;
        }

        Set<CommandArgument> usedOptions() {
            return usedOptions;
        }

        CommandArgument pendingValue() {
            return pendingValue;
        }

        boolean optionsTerminated() {
            return optionsTerminated;
        }
    }

    private OptionIndex buildOptionIndex(List<CommandArgument> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return new OptionIndex(new HashMap<>(), new HashMap<>(), List.of());
        }
        Map<String, CommandArgument> shortOptions = new HashMap<>();
        Map<String, CommandArgument> longOptions = new HashMap<>();
        List<CommandArgument> optionArgs = new ArrayList<>();

        for (CommandArgument argument : arguments) {
            if (argument == null || isSenderArgument(argument) || !argument.isOption()) {
                continue;
            }
            optionArgs.add(argument);
            for (String name : argument.getShortOptionNames()) {
                String normalized = normalizeOptionName(name);
                if (!normalized.isEmpty()) {
                    shortOptions.put(normalized.toLowerCase(Locale.ROOT), argument);
                }
            }
            for (String name : argument.getLongOptionNames()) {
                String normalized = normalizeOptionName(name);
                if (!normalized.isEmpty()) {
                    longOptions.put(normalized.toLowerCase(Locale.ROOT), argument);
                }
            }
        }

        return new OptionIndex(shortOptions, longOptions, optionArgs);
    }

    private ParsedOptions parseOptions(List<String> args, OptionIndex index, boolean allowPending) {
        List<String> positionals = new ArrayList<>();
        Map<CommandArgument, String> values = new HashMap<>();
        Set<CommandArgument> used = new HashSet<>();
        CommandArgument pending = null;
        if (args == null || args.isEmpty() || index == null || !index.hasOptions()) {
            return new ParsedOptions(args != null ? new ArrayList<>(args) : new ArrayList<>(), values, used, pending,
                    false);
        }
        boolean endOptions = false;
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (pending != null) {
                values.put(pending, token);
                used.add(pending);
                pending = null;
                continue;
            }
            if (!endOptions && "--".equals(token)) {
                endOptions = true;
                continue;
            }
            if (!endOptions) {
                OptionToken optionToken = parseOptionToken(token);
                if (optionToken != null) {
                    CommandArgument argument = index.resolve(optionToken);
                    if (argument != null) {
                        used.add(argument);
                        if (optionToken.hasInlineValue()) {
                            values.put(argument, optionToken.value());
                        } else if (argument.isFlag()) {
                            values.put(argument, "true");
                        } else if (i + 1 < args.size()) {
                            values.put(argument, args.get(++i));
                        } else if (allowPending) {
                            pending = argument;
                        } else {
                            return null;
                        }
                        continue;
                    }
                }
            }
            positionals.add(token);
        }
        if (!allowPending && pending != null) {
            return null;
        }
        return new ParsedOptions(positionals, values, used, pending, endOptions);
    }

    private static OptionToken parseOptionToken(String token) {
        if (token == null || token.length() < 2 || !token.startsWith("-") || "-".equals(token)) {
            return null;
        }
        if (token.startsWith("--")) {
            String keyValue = token.substring(2);
            if (keyValue.isEmpty() || isNumericOptionToken(keyValue)) {
                return null;
            }
            int eq = keyValue.indexOf('=');
            String key = eq >= 0 ? keyValue.substring(0, eq) : keyValue;
            if (key.isEmpty()) {
                return null;
            }
            String value = eq >= 0 ? keyValue.substring(eq + 1) : null;
            return new OptionToken(key, true, value, eq >= 0);
        }

        String keyValue = token.substring(1);
        if (keyValue.isEmpty() || isNumericOptionToken(keyValue)) {
            return null;
        }
        int eq = keyValue.indexOf('=');
        String key = eq >= 0 ? keyValue.substring(0, eq) : keyValue;
        if (key.isEmpty()) {
            return null;
        }
        String value = eq >= 0 ? keyValue.substring(eq + 1) : null;
        return new OptionToken(key, false, value, eq >= 0);
    }

    private static boolean isOptionPrefix(String token) {
        if (token == null || token.isEmpty() || !token.startsWith("-")) {
            return false;
        }
        if ("-".equals(token) || "--".equals(token)) {
            return true;
        }
        String raw = token.startsWith("--") ? token.substring(2) : token.substring(1);
        if (raw.isEmpty()) {
            return true;
        }
        return !isNumericOptionToken(raw);
    }

    private static boolean isNumericOptionToken(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        char first = value.charAt(0);
        return Character.isDigit(first) || first == '.';
    }

    private static String normalizeOptionName(String name) {
        String trimmed = name != null ? name.trim() : "";
        while (trimmed.startsWith("-")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    private List<String> buildOptionSuggestions(OptionIndex optionIndex, Set<CommandArgument> usedOptions,
            String currentInput, String normalizedCommandName, @Nullable String subCommandName, S sender,
            @NotNull Map<String, Object> allParsedArguments) {
        if (optionIndex == null || !optionIndex.hasOptions()) {
            return List.of();
        }
        String lowered = currentInput != null ? currentInput.toLowerCase(Locale.ROOT) : "";
        List<String> suggestions = new ArrayList<>();

        for (CommandArgument argument : optionIndex.optionArguments()) {
            if (argument == null) {
                continue;
            }
            if (usedOptions != null && usedOptions.contains(argument)) {
                continue;
            }
            if (!canSuggestArgument(normalizedCommandName, subCommandName, argument, sender)) {
                continue;
            }
            for (String longName : argument.getLongOptionNames()) {
                String token = "--" + longName;
                if (lowered.isEmpty() || token.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                    suggestions.add(token);
                }
            }
            for (String shortName : argument.getShortOptionNames()) {
                String token = "-" + shortName;
                if (lowered.isEmpty() || token.toLowerCase(Locale.ROOT).startsWith(lowered)) {
                    suggestions.add(token);
                }
            }
        }

        return suggestions;
    }

    private List<String> generateArgumentSuggestions(MagicCommand command, CommandAction<S> subInfo,
            S sender, List<String> args, String currentInput, String normalizedCommandName,
            Map<String, Object> previousParsedArguments) {
        List<CommandArgument> arguments = subInfo != null ? subInfo.arguments() : List.of();
        return generateDirectMethodSuggestions(command, arguments, sender, args, normalizedCommandName,
                subInfo != null ? subInfo.fullPath() : null, previousParsedArguments);
    }

    private List<String> generateSuggestionsForArgument(MagicCommand command, CommandArgument argument,
            S sender, String currentInput, Map<String, Object> previousParsedArguments) {
        logger.debug("generateSuggestionsForArgument called for argument: " + argument.getName()
                + " with input: '" + currentInput + "'");
        logger.debug("Argument suggestions: " + argument.getSuggestions());
        logger.debug("Argument type: " + argument.getType());

        List<String> suggestions = new ArrayList<>();

        // If no explicit suggestions, try to get suggestions from the argument type
        if (argument.getSuggestions().isEmpty()) {
            logger.debug("No explicit suggestions, getting suggestions for type: " + argument.getType().getSimpleName());
            List<String> typeSuggestions = typeParserRegistry.getSuggestionsForArgumentFiltered(argument,
                    currentInput, sender, previousParsedArguments);
            if (!typeSuggestions.isEmpty()) {
                logger.debug("Got " + typeSuggestions.size() + " suggestions from type parser");
                return typeSuggestions;
            }
        }

        // Process explicit suggestions
        for (String suggestionSource : argument.getSuggestions()) {
            logger.debug("Processing suggestion source: '" + suggestionSource + "'");

            if (suggestionSource.contains("|")) {
                String[] sources = suggestionSource.split("\\|");
                for (String source : sources) {
                    List<String> sourceSuggestions = processSuggestionSource(command, source.trim(), sender,
                            currentInput, argument, previousParsedArguments);
                    logger.debug("Source '" + source.trim() + "' generated: " + sourceSuggestions);
                    suggestions.addAll(sourceSuggestions);
                }
            } else {
                List<String> sourceSuggestions = processSuggestionSource(command, suggestionSource, sender,
                        currentInput, argument, previousParsedArguments);
                logger.debug("Source '" + suggestionSource + "' generated: " + sourceSuggestions);
                suggestions.addAll(sourceSuggestions);
            }
        }

        List<String> filteredSuggestions = suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(currentInput.toLowerCase()))
                .distinct()
                .collect(Collectors.toList());

        logger.debug("Final filtered suggestions: " + filteredSuggestions);
        return filteredSuggestions;
    }

    @SuppressWarnings("unchecked")
    private List<String> processSuggestionSource(MagicCommand command, String source, S sender,
            String currentInput, CommandArgument currentArgument, Map<String, Object> previousParsedArguments) {
        logger.debug("Processing suggestion source: " + source);

        if ("@commands".equalsIgnoreCase(source)) {
            return Arrays.asList(HelpCommandSupport.getCommandSuggestions(this));
        }


        if (typeParserRegistry.isSpecialSuggestion(source)) {
            return typeParserRegistry.parseSuggestionFiltered(source, currentInput, sender);
        }

        if (source.startsWith("{") && source.endsWith("}")) {
            String content = source.substring(1, source.length() - 1);
            return Arrays.asList(content.split(",\\s*"));
        }

        List<Object> dynamicArgs = new ArrayList<>();
        List<Class<?>> dynamicArgTypes = new ArrayList<>();

        Class<?> playerType = platform.playerType();
        Class<?> senderType = platform.senderType();

        boolean methodTakesPlayer = false;

        try {
            Method method = command.getClass().getMethod(source);
            Object result = method.invoke(command);
            if (result instanceof String[]) {
                return Arrays.asList((String[]) result);
            } else if (result instanceof List) {
                return (List<String>) result;
            }
        } catch (Exception e) {
        }

        if (currentArgument.getContextArgs().isEmpty()) {
            if (playerType != null) {
                Object player = platform.getPlayerSender(sender);
                if (player != null) {
                    dynamicArgs.add(player);
                    dynamicArgTypes.add(playerType);
                    methodTakesPlayer = true;
                }
            }
            if (senderType != null && !methodTakesPlayer) {
                dynamicArgs.add(sender);
                dynamicArgTypes.add(senderType);
            }
        }

        for (String contextArgName : currentArgument.getContextArgs()) {
            Object argValue = previousParsedArguments.get(contextArgName);
            if (argValue != null) {
                dynamicArgs.add(argValue);
                dynamicArgTypes.add(argValue.getClass());
            } else {
                logger.debug("Missing context argument '" + contextArgName + "' for suggestion method " + source);
                return Collections.emptyList();
            }
        }
        
        if (currentInput != null && !currentInput.isEmpty()) {
            dynamicArgs.add(currentInput);
            dynamicArgTypes.add(String.class);
        }

        try {
            Method matchingMethod = findMatchingMethod(command.getClass(), source, dynamicArgTypes);
            if (matchingMethod != null) {
                Object result = matchingMethod.invoke(command, dynamicArgs.toArray());
                if (result instanceof String[]) {
                    return Arrays.asList((String[]) result);
                } else if (result instanceof List) {
                    return (List<String>) result;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to call suggestion method " + source + " with dynamic parameters: " + e.getMessage());
        }

        if ("@sender".equalsIgnoreCase(source)) {
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }

    private Method findMatchingMethod(Class<?> clazz, String methodName, List<Class<?>> paramTypes) {
        for (Method method : clazz.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] methodParamTypes = method.getParameterTypes();
            if (methodParamTypes.length != paramTypes.size()) {
                continue;
            }
            boolean typesMatch = true;
            for (int i = 0; i < paramTypes.size(); i++) {
                Class<?> expected = paramTypes.get(i);
                Class<?> actual = methodParamTypes[i];
                if (!actual.isAssignableFrom(expected) && !isPrimitiveWrapperOf(actual, expected)) {
                    typesMatch = false;
                    break;
                }
            }
            if (typesMatch) {
                return method;
            }
        }
        return null;
    }

    private boolean isPrimitiveWrapperOf(Class<?> wrapper, Class<?> primitive) {
        if (!wrapper.isPrimitive() && primitive.isPrimitive()) {
            if (wrapper == Integer.class && primitive == int.class) return true;
            if (wrapper == Long.class && primitive == long.class) return true;
            if (wrapper == Short.class && primitive == short.class) return true;
            if (wrapper == Byte.class && primitive == byte.class) return true;
            if (wrapper == Character.class && primitive == char.class) return true;
            if (wrapper == Float.class && primitive == float.class) return true;
            if (wrapper == Double.class && primitive == double.class) return true;
            if (wrapper == Boolean.class && primitive == boolean.class) return true;
        }
        return false;
    }

    private boolean hasSubOrArgPermission(MagicCommand command, CommandInfo info, S sender,
            String baseCommandName, @Nullable String targetSubName) {
        List<CommandAction<S>> subs = getSubCommandActions(command);
        if (subs.isEmpty()) {
            return false;
        }
        if (targetSubName != null && !targetSubName.isEmpty()) {
            SubCommandNode<S> root = getSubCommandTree(command, info);
            SubCommandTraversal<S> traversal = traverseSubCommands(root, List.of(targetSubName));
            if (traversal.consumed() > 0 && hasPermissionInNode(traversal.node(), sender, baseCommandName)) {
                return true;
            }
        }
        // Fallback: any subcommand permission granted
        for (CommandAction<S> subInfo : subs) {
            if (canAccessAction(subInfo, sender, baseCommandName)
                    || hasArgumentPermissionOverride(baseCommandName, subInfo.fullPath(), sender)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPermissionInNode(SubCommandNode<S> node, S sender, String baseCommandName) {
        if (node == null) {
            return false;
        }
        if (node.action() != null) {
            if (canAccessAction(node.action(), sender, baseCommandName)
                    || hasArgumentPermissionOverride(baseCommandName, node.action().fullPath(), sender)) {
                return true;
            }
        }
        for (SubCommandNode<S> child : node.children().values()) {
            if (hasPermissionInNode(child, sender, baseCommandName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasArgumentPermissionOverride(String baseCommandName, @Nullable String subCommandName,
            S sender) {
        String prefix = "commands." + baseCommandName;
        String normalizedSub = normalizeSubCommandSegment(subCommandName);
        if (!normalizedSub.isEmpty()) {
            prefix += ".subcommand." + normalizedSub;
        }
        String argPrefix = resolvePermission(null, prefix + ".argument");
        String argPrefixNoSegment = resolvePermission(null, prefix);
        return platform.hasPermissionByPrefix(sender, argPrefix, argPrefixNoSegment);
    }

    /**
     * Generates a usage string for a command based on its structure.
     * 
     * @param command the command instance
     * @param info    the command info
     * @return formatted usage string
     */
    public String generateUsage(MagicCommand command, CommandInfo info) {
        List<CommandAction<S>> subCommands = getSubCommandActions(command);
        CommandAction<S> directAction = getDirectAction(command, info);

        StringBuilder usage = new StringBuilder();
        usage.append("/").append(info.name());

        // If command has direct execute handler and no subcommands
        if (directAction != null && subCommands.isEmpty()) {
            appendArgumentsToUsage(usage, directAction.arguments());
        }
        // If command has only subcommands
        else if (!subCommands.isEmpty() && directAction == null) {
            usage.append(" <");
            usage.append(getTopLevelSubCommandNames(subCommands).stream()
                    .collect(Collectors.joining(" | ")));
            usage.append(">");
        }
        // If command has both execute handler and subcommands
        else if (directAction != null && !subCommands.isEmpty()) {
            List<CommandArgument> arguments = directAction.arguments();

            // Show direct arguments as optional since subcommands are available
            if (!arguments.isEmpty()) {
                appendArgumentsToUsage(usage, arguments, true);
            }

            usage.append(" OR /").append(info.name()).append(" <");
            usage.append(getTopLevelSubCommandNames(subCommands).stream()
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
        List<CommandAction<S>> subCommands = getSubCommandActions(command);

        for (CommandAction<S> subInfo : subCommands) {
            StringBuilder usage = new StringBuilder();
            usage.append("/").append(info.name()).append(" ").append(subInfo.fullPath());

            appendArgumentsToUsage(usage, subInfo.arguments());

            if (subInfo.description() != null && !subInfo.description().isEmpty()) {
                usage.append(" - ").append(subInfo.description());
            }

            usages.add(usage.toString());
        }

        return usages;
    }

    /**
     * Formats argument list for usage display, skipping sender parameters.
     *
     * @param arguments arguments to format
     * @return formatted argument string without leading/trailing spaces
     */
    public String formatArguments(List<CommandArgument> arguments) {
        return formatArguments(arguments, false);
    }

    /**
     * Formats argument list for usage display, skipping sender parameters.
     *
     * @param arguments     arguments to format
     * @param forceOptional whether to force all arguments to be optional
     * @return formatted argument string without leading/trailing spaces
     */
    public String formatArguments(List<CommandArgument> arguments, boolean forceOptional) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        StringBuilder usage = new StringBuilder();
        appendArgumentsToUsage(usage, arguments, forceOptional);
        return usage.toString().trim();
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
        List<CommandArgument> positional = new ArrayList<>();
        List<CommandArgument> options = new ArrayList<>();

        for (CommandArgument arg : arguments) {
            if (isSenderArgument(arg)) {
                continue;
            }
            if (arg.isOption()) {
                options.add(arg);
            } else {
                positional.add(arg);
            }
        }

        for (CommandArgument arg : positional) {
            usage.append(" ");

            boolean isOptional = forceOptional || arg.isOptional() || arg.getDefaultValue() != null;

            if (isOptional) {
                usage.append("[");
            } else {
                usage.append("<");
            }

            String argName = generateArgumentName(arg);
            usage.append(argName);

            if (isOptional) {
                usage.append("]");
            } else {
                usage.append(">");
            }
        }

        for (CommandArgument arg : options) {
            usage.append(" ");
            boolean isOptional = forceOptional || arg.isOptional() || arg.getDefaultValue() != null;
            String optionToken = buildPrimaryOptionToken(arg);
            String token = optionToken;
            if (!arg.isFlag()) {
                token += " <" + generateArgumentName(arg) + ">";
            }
            if (isOptional) {
                usage.append("[").append(token).append("]");
            } else {
                usage.append(token);
            }
        }
    }

    private String buildUsage(CommandInfo info, @Nullable String subCommandName, List<CommandArgument> arguments) {
        StringBuilder usage = new StringBuilder("/").append(info.name());
        if (subCommandName != null && !subCommandName.isEmpty()) {
            usage.append(" ").append(subCommandName);
        }
        appendArgumentsToUsage(usage, arguments, false);
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

    private String buildPrimaryOptionToken(CommandArgument argument) {
        if (argument == null) {
            return "";
        }
        List<String> longNames = argument.getLongOptionNames();
        if (!longNames.isEmpty()) {
            return "--" + longNames.get(0);
        }
        List<String> shortNames = argument.getShortOptionNames();
        if (!shortNames.isEmpty()) {
            return "-" + shortNames.get(0);
        }
        String name = argument.getName();
        return name != null ? name : "";
    }

    private String getAvailableSubCommands(List<CommandAction<S>> subCommands, S sender,
            String commandName) {
        return String.join(", ", getAvailableSubCommandsList(subCommands, sender, commandName, List.of()));
    }

    private List<String> getAvailableSubCommandsList(List<CommandAction<S>> subCommands,
            S sender, String commandName, List<String> pathSegments) {
        SubCommandNode<S> root = buildSubCommandTree(subCommands);
        SubCommandNode<S> node = resolveNode(root, pathSegments);
        if (node == null) {
            return new ArrayList<>();
        }
        return getAvailableChildNames(node, sender, commandName);
    }

    private List<String> getAvailableChildNames(SubCommandNode<S> node, S sender, String commandName) {
        Set<String> available = new LinkedHashSet<>();
        for (SubCommandNode<S> child : node.children().values()) {
            if (!hasAccessibleAction(child, sender, commandName)) {
                continue;
            }
            available.add(child.name());
            if (child.action() != null && canAccessActionOrArguments(child.action(), sender, commandName)) {
                for (String alias : child.aliases()) {
                    if (alias != null && !alias.isBlank()) {
                        available.add(alias);
                    }
                }
            }
        }
        return new ArrayList<>(available);
    }

    private List<String> getTopLevelSubCommandNames(List<CommandAction<S>> subCommands) {
        Set<String> names = new LinkedHashSet<>();
        if (subCommands == null) {
            return new ArrayList<>();
        }
        for (CommandAction<S> sub : subCommands) {
            if (sub == null) {
                continue;
            }
            List<String> path = sub.path();
            if (path != null && !path.isEmpty()) {
                String first = path.get(0);
                if (first != null && !first.isBlank()) {
                    names.add(first);
                    continue;
                }
            }
            String name = sub.name();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    private boolean canAccessAction(CommandAction<S> action, S sender, String commandName) {
        if (action == null) {
            return false;
        }
        String subPermission = resolvePermission(action.permission(),
                "commands." + commandName + ".subcommand." + action.permissionSegment());
        if (subPermission.isEmpty()) {
            return true;
        }
        return platform.hasPermission(sender, subPermission, action.permissionDefault());
    }

    private boolean hasAccessibleAction(SubCommandNode<S> node, S sender, String commandName) {
        if (node == null) {
            return false;
        }
        if (node.action() != null && canAccessActionOrArguments(node.action(), sender, commandName)) {
            return true;
        }
        for (SubCommandNode<S> child : node.children().values()) {
            if (hasAccessibleAction(child, sender, commandName)) {
                return true;
            }
        }
        return false;
    }

    private boolean canAccessActionOrArguments(CommandAction<S> action, S sender, String commandName) {
        if (action == null) {
            return false;
        }
        return canAccessAction(action, sender, commandName)
                || hasArgumentPermissionOverride(commandName, action.fullPath(), sender);
    }

    private SubCommandNode<S> buildSubCommandTree(List<CommandAction<S>> subCommands) {
        SubCommandNode<S> root = new SubCommandNode<>("");
        if (subCommands == null || subCommands.isEmpty()) {
            return root;
        }
        for (CommandAction<S> action : subCommands) {
            if (action == null) {
                continue;
            }
            SubCommandNode<S> node = root;
            for (String segment : action.path()) {
                node = node.child(segment);
            }
            node = node.child(action.name());
            node.setAction(action);
        }
        return root;
    }

    private SubCommandNode<S> resolveNode(SubCommandNode<S> root, List<String> pathSegments) {
        if (root == null) {
            return null;
        }
        SubCommandTraversal<S> traversal = traverseSubCommands(root, pathSegments);
        if (traversal.consumed() < (pathSegments != null ? pathSegments.size() : 0)) {
            return null;
        }
        return traversal.node();
    }

    private SubCommandTraversal<S> traverseSubCommands(SubCommandNode<S> root, List<String> tokens) {
        if (root == null) {
            return new SubCommandTraversal<>(null, 0, null, 0);
        }
        SubCommandNode<S> node = root;
        SubCommandNode<S> lastAction = null;
        int lastActionIndex = 0;
        int index = 0;
        if (tokens != null) {
            for (String token : tokens) {
                if (token == null || token.isEmpty()) {
                    break;
                }
                SubCommandNode<S> child = node.findChild(token);
                if (child == null) {
                    break;
                }
                node = child;
                index++;
                if (node.action() != null) {
                    lastAction = node;
                    lastActionIndex = index;
                }
            }
        }
        return new SubCommandTraversal<>(node, index, lastAction, lastActionIndex);
    }

    private static final class CommandCache<S> {
        private final int dynamicSubCount;
        private final MagicCommand.DynamicExecute dynamicExecute;
        private final List<CommandAction<S>> subActions;
        private final CommandAction<S> directAction;
        private final SubCommandNode<S> tree;

        private CommandCache(int dynamicSubCount,
                             MagicCommand.DynamicExecute dynamicExecute,
                             List<CommandAction<S>> subActions,
                             CommandAction<S> directAction,
                             SubCommandNode<S> tree) {
            this.dynamicSubCount = dynamicSubCount;
            this.dynamicExecute = dynamicExecute;
            this.subActions = subActions != null ? Collections.unmodifiableList(subActions) : List.of();
            this.directAction = directAction;
            this.tree = tree;
        }

        boolean isStale(MagicCommand command) {
            if (command == null) {
                return false;
            }
            return command.getDynamicSubCommands().size() != dynamicSubCount
                    || command.getDynamicExecute() != dynamicExecute;
        }

        List<CommandAction<S>> subActions() {
            return subActions;
        }

        CommandAction<S> directAction() {
            return directAction;
        }

        SubCommandNode<S> tree() {
            return tree;
        }
    }

    private record SubCommandTraversal<S>(SubCommandNode<S> node, int consumed,
                                          SubCommandNode<S> lastActionNode, int lastActionIndex) {
    }

    private static final class SubCommandNode<S> {
        private final String name;
        private List<String> aliases = List.of();
        private final Map<String, SubCommandNode<S>> children = new LinkedHashMap<>();
        private CommandAction<S> action;

        SubCommandNode(String name) {
            this.name = name != null ? name : "";
        }

        String name() {
            return name;
        }

        List<String> aliases() {
            return aliases;
        }

        Map<String, SubCommandNode<S>> children() {
            return children;
        }

        CommandAction<S> action() {
            return action;
        }

        void setAction(CommandAction<S> action) {
            this.action = action;
            if (action != null) {
                this.aliases = action.aliases();
            }
        }

        SubCommandNode<S> child(String name) {
            String key = name != null ? name.toLowerCase(Locale.ROOT) : "";
            if (key.isEmpty()) {
                return this;
            }
            return children.computeIfAbsent(key, ignored -> new SubCommandNode<>(name));
        }

        SubCommandNode<S> findChild(String token) {
            if (token == null || token.isEmpty()) {
                return null;
            }
            String lowered = token.toLowerCase(Locale.ROOT);
            SubCommandNode<S> direct = children.get(lowered);
            if (direct != null) {
                return direct;
            }
            for (SubCommandNode<S> child : children.values()) {
                if (child.matches(token)) {
                    return child;
                }
            }
            return null;
        }

        boolean matches(String input) {
            if (input == null) {
                return false;
            }
            if (name.equalsIgnoreCase(input)) {
                return true;
            }
            for (String alias : aliases) {
                if (alias != null && alias.equalsIgnoreCase(input)) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class CommandAction<S> {
        private final String name;
        private final List<String> path;
        private final String description;
        private final List<String> aliases;
        private final String permission;
        private final MagicPermissionDefault permissionDefault;
        private final List<CommandArgument> arguments;
        private final Method method;
        private final CommandExecutor<S> executor;

        private CommandAction(String name,
                              List<String> path,
                              String description,
                              List<String> aliases,
                              String permission,
                              MagicPermissionDefault permissionDefault,
                              List<CommandArgument> arguments,
                              Method method,
                              CommandExecutor<S> executor) {
            this.name = name != null ? name : "";
            this.path = normalizePath(path);
            this.description = description != null ? description : "";
            this.aliases = aliases != null ? new ArrayList<>(aliases) : new ArrayList<>();
            this.permission = permission != null ? permission : "";
            this.permissionDefault = permissionDefault != null ? permissionDefault : MagicPermissionDefault.OP;
            this.arguments = arguments != null ? new ArrayList<>(arguments) : new ArrayList<>();
            this.method = method;
            this.executor = executor;
        }

        static <S> CommandAction<S> forSubCommand(MagicCommand.SubCommandInfo subInfo) {
            List<CommandArgument> arguments = MagicCommand.getArguments(subInfo.method);
            return forSubCommand(subInfo, arguments);
        }

        static <S> CommandAction<S> forSubCommand(MagicCommand.SubCommandInfo subInfo,
                                                  List<CommandArgument> arguments) {
            return new CommandAction<>(
                    subInfo.annotation.name(),
                    Arrays.asList(subInfo.annotation.path()),
                    subInfo.annotation.description(),
                    Arrays.asList(subInfo.annotation.aliases()),
                    subInfo.annotation.permission(),
                    subInfo.annotation.permissionDefault(),
                    arguments,
                    subInfo.method,
                    null
            );
        }

        static <S> CommandAction<S> forDynamicSubCommand(SubCommandSpec<?> spec, CommandExecutor<S> executor) {
            return new CommandAction<>(
                    spec.name(),
                    spec.path(),
                    spec.description(),
                    spec.aliases(),
                    spec.permission(),
                    spec.permissionDefault(),
                    spec.arguments(),
                    null,
                    executor
            );
        }

        static <S> CommandAction<S> forMethod(String name,
                                              String description,
                                              String permission,
                                              MagicPermissionDefault permissionDefault,
                                              List<CommandArgument> arguments,
                                              Method method) {
            return new CommandAction<>(
                    name,
                    List.of(),
                    description,
                    List.of(),
                    permission,
                    permissionDefault,
                    arguments,
                    method,
                    null
            );
        }

        static <S> CommandAction<S> forExecute(String name,
                                               String description,
                                               String permission,
                                               MagicPermissionDefault permissionDefault,
                                               List<CommandArgument> arguments,
                                               CommandExecutor<S> executor) {
            return new CommandAction<>(
                    name,
                    List.of(),
                    description,
                    List.of(),
                    permission,
                    permissionDefault,
                    arguments,
                    null,
                    executor
            );
        }

        String name() {
            return name;
        }

        List<String> path() {
            return Collections.unmodifiableList(path);
        }

        List<String> fullPathSegments() {
            List<String> segments = new ArrayList<>(path);
            if (!name.isEmpty()) {
                segments.add(name);
            }
            return segments;
        }

        String fullPath() {
            return String.join(" ", fullPathSegments());
        }

        String permissionSegment() {
            List<String> segments = fullPathSegments();
            if (segments.isEmpty()) {
                return "";
            }
            return segments.stream()
                    .filter(part -> part != null && !part.isBlank())
                    .map(part -> part.toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining("."));
        }

        String description() {
            return description;
        }

        List<String> aliases() {
            return Collections.unmodifiableList(aliases);
        }

        String permission() {
            return permission;
        }

        MagicPermissionDefault permissionDefault() {
            return permissionDefault;
        }

        List<CommandArgument> arguments() {
            return Collections.unmodifiableList(arguments);
        }

        Method method() {
            return method;
        }

        CommandExecutor<S> executor() {
            return executor;
        }

        boolean matches(String input) {
            if (input == null) {
                return false;
            }
            if (name.equalsIgnoreCase(input)) {
                return true;
            }
            for (String alias : aliases) {
                if (alias.equalsIgnoreCase(input)) {
                    return true;
                }
            }
            return false;
        }

        List<String> allKeysLower() {
            List<String> keys = new ArrayList<>();
            if (!name.isEmpty()) {
                keys.add(name.toLowerCase(Locale.ROOT));
            }
            for (String alias : aliases) {
                if (alias != null && !alias.isEmpty()) {
                    keys.add(alias.toLowerCase(Locale.ROOT));
                }
            }
            return keys;
        }
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

    private static List<String> normalizePath(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String part : raw) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized.isEmpty() ? List.of() : normalized;
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

    /**
     * Gets stored command info for a registered command instance.
     *
     * @param command command instance
     * @return command info or null
     */
    public CommandInfo getCommandInfo(MagicCommand command) {
        if (command == null) {
            return null;
        }
        return commandInfoByInstance.get(command);
    }

    /**
     * Gets a command by name or alias.
     *
     * @param name command name
     * @return command instance or null
     */
    public MagicCommand getCommand(String name) {
        if (name == null) {
            return null;
        }
        return commands.get(name.toLowerCase(Locale.ROOT));
    }

    boolean hasPermissionForHelp(MagicSender sender, String permission, MagicPermissionDefault defaultValue,
                                 String description) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        if (sender == null) {
            return false;
        }
        platform.ensurePermissionRegistered(permission, defaultValue, description != null ? description : "");
        S raw = unwrapSender(sender);
        if (raw != null) {
            return platform.hasPermission(raw, permission, defaultValue);
        }
        return sender.hasPermission(permission);
    }

    boolean hasPermissionByPrefixForHelp(MagicSender sender, String... prefixes) {
        if (sender == null) {
            return false;
        }
        S raw = unwrapSender(sender);
        if (raw != null) {
            return platform.hasPermissionByPrefix(raw, prefixes);
        }
        if (prefixes == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix == null || prefix.isEmpty()) {
                continue;
            }
            String withDot = prefix.endsWith(".") ? prefix : prefix + ".";
            if (sender.hasPermission(withDot + "*")) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private @Nullable S unwrapSender(MagicSender sender) {
        if (sender == null) {
            return null;
        }
        Object raw = sender.handle();
        if (raw == null) {
            return null;
        }
        Class<?> senderType = platform.senderType();
        if (senderType != null && senderType.isInstance(raw)) {
            return (S) raw;
        }
        return null;
    }

    String resolvePermission(String annotationPermission, String fallbackPermission) {
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
