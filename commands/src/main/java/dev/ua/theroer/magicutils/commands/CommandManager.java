package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import dev.ua.theroer.magicutils.commands.CommandOptions.OptionIndex;
import dev.ua.theroer.magicutils.commands.CommandOptions.ParsedOptions;

import static dev.ua.theroer.magicutils.commands.CommandOptions.buildOptionIndex;
import static dev.ua.theroer.magicutils.commands.CommandOptions.isOptionPrefix;
import static dev.ua.theroer.magicutils.commands.CommandOptions.parseOptions;

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
    private final CommandActionCatalog<S> actionCatalog = new CommandActionCatalog<>();
    private final CommandDescriptionResolver descriptions;
    private final CommandPermissions<S> permissions;
    private final CommandUsageFormatter<S> usageFormatter;
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
        this.descriptions = new CommandDescriptionResolver(this.pluginName);
        this.permissions = new CommandPermissions<>(this.platform, this.logger, permissionPrefix, this.pluginName,
                descriptions, new ArgumentPermissionConditions<>(this.typeParserRegistry), actionCatalog,
                this::getSubCommandActions, this::isSenderArgument);
        this.usageFormatter = new CommandUsageFormatter<>(descriptions, this::isSenderArgument);
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
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(info, "info");
        command.freeze();
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
     * Returns the resolved command schema for the provided command label.
     *
     * @param name command name, alias, or namespaced label
     * @return resolved schema or null when the command is not registered
     */
    @Nullable
    public ResolvedCommandSchema describe(String name) {
        if (name == null) {
            return null;
        }
        MagicCommand command = commands.get(name.toLowerCase(Locale.ROOT));
        CommandInfo info = commandInfos.get(name.toLowerCase(Locale.ROOT));
        if (command == null || info == null) {
            return null;
        }
        return describe(command, info);
    }

    /**
     * Returns the resolved command schema for a registered command instance.
     *
     * @param command command instance
     * @param info resolved command info
     * @return immutable resolved schema or null when the command is unknown
     */
    @Nullable
    public ResolvedCommandSchema describe(MagicCommand command, CommandInfo info) {
        if (command == null || info == null) {
            return null;
        }
        CommandAction<S> directAction = getDirectAction(command, info);
        SubCommandTree.Node<S> subTree = getSubCommandTree(command, info);
        return new ResolvedCommandSchema(
                info.name(),
                descriptions.resolveCommandDescription(info),
                Arrays.asList(info.aliases()),
                info.permission(),
                info.permissionDefault(),
                directAction != null ? toResolvedAction(info.name(), directAction) : null,
                toResolvedNode(info.name(), subTree)
        );
    }

    private SubCommandTree.Node<S> getSubCommandTree(MagicCommand command, CommandInfo info) {
        return actionCatalog.tree(command, info);
    }

    CommandAction<S> getDirectAction(MagicCommand command, CommandInfo info) {
        CommandInfo resolvedInfo = info != null ? info : commandInfoByInstance.get(command);
        return actionCatalog.directAction(command, resolvedInfo);
    }

    List<CommandAction<S>> getSubCommandActions(MagicCommand command) {
        CommandInfo resolvedInfo = commandInfoByInstance.get(command);
        return actionCatalog.subActions(command, resolvedInfo);
    }

    private ResolvedCommandAction toResolvedAction(String commandName, CommandAction<S> action) {
        return new ResolvedCommandAction(
                action.name(),
                action.path(),
                descriptions.resolveActionDescription(commandName, action),
                action.aliases(),
                action.permission(),
                action.permissionDefault(),
                action.threading(),
                action.arguments()
        );
    }

    private ResolvedSubCommandNode toResolvedNode(String commandName, SubCommandTree.Node<S> node) {
        if (node == null) {
            return ResolvedSubCommandNode.root();
        }
        List<ResolvedSubCommandNode> children = new ArrayList<>();
        for (SubCommandTree.Node<S> child : node.children().values()) {
            children.add(toResolvedNode(commandName, child));
        }
        return new ResolvedSubCommandNode(
                node.name(),
                node.aliases(),
                children,
                node.action() != null ? toResolvedAction(commandName, node.action()) : null
        );
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
     * Executes a command asynchronously on the provided executor.
     *
     * @param name command name
     * @param sender sender handle
     * @param args command arguments
     * @param executor executor to run on (uses common pool when null)
     * @return future with command result
     */
    public CompletableFuture<CommandResult> executeAsync(String name, S sender, List<String> args,
                                                         Executor executor) {
        if (executor == null) {
            return CompletableFuture.supplyAsync(() -> execute(name, sender, args));
        }
        return CompletableFuture.supplyAsync(() -> execute(name, sender, args), executor);
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
     * Resolves the threading policy for the targeted command action.
     *
     * @param name command name
     * @param args command arguments
     * @return resolved threading policy
     */
    public CommandThreading resolveThreading(String name, List<String> args) {
        if (name == null) {
            return CommandThreading.MAIN;
        }
        MagicCommand command = commands.get(name.toLowerCase(Locale.ROOT));
        CommandInfo info = commandInfos.get(name.toLowerCase(Locale.ROOT));
        if (command == null || info == null) {
            return CommandThreading.MAIN;
        }

        List<CommandAction<S>> subCommands = getSubCommandActions(command);
        CommandAction<S> directAction = getDirectAction(command, info);
        if (directAction != null && (subCommands.isEmpty() || args == null || args.isEmpty())) {
            return directAction.threading();
        }
        if (subCommands.isEmpty()) {
            return directAction != null ? directAction.threading() : CommandThreading.MAIN;
        }
        if (args == null || args.isEmpty()) {
            return directAction != null ? directAction.threading() : CommandThreading.MAIN;
        }

        SubCommandTree.Node<S> root = getSubCommandTree(command, info);
        SubCommandTree.Traversal<S> traversal = SubCommandTree.traverse(root, args);
        if (traversal.lastActionNode() != null && traversal.lastActionNode().action() != null) {
            return traversal.lastActionNode().action().threading();
        }
        return directAction != null ? directAction.threading() : CommandThreading.MAIN;
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
        return permissions.hasCommandPermission(command, info, sender, baseCommandName, targetSubName);
    }

    /**
     * Checks whether the sender can access a resolved subcommand path or any executable child under it.
     *
     * @param name command label or alias
     * @param sender sender handle
     * @param pathSegments path tokens using either canonical names or aliases
     * @return true if the path resolves to an accessible node
     */
    public boolean canAccessSubCommandPath(String name, S sender, List<String> pathSegments) {
        if (name == null) {
            return false;
        }
        MagicCommand command = commands.get(name.toLowerCase(Locale.ROOT));
        CommandInfo info = commandInfos.get(name.toLowerCase(Locale.ROOT));
        if (command == null || info == null) {
            return false;
        }

        String baseCommandName = info.name().toLowerCase(Locale.ROOT);
        String targetSubName = (pathSegments != null && !pathSegments.isEmpty())
                ? pathSegments.get(0).toLowerCase(Locale.ROOT)
                : null;
        if (!permissions.hasCommandPermission(command, info, sender, baseCommandName, targetSubName)) {
            return false;
        }
        if (pathSegments == null || pathSegments.isEmpty()) {
            return true;
        }

        SubCommandTree.Node<S> root = getSubCommandTree(command, info);
        SubCommandTree.Node<S> node = SubCommandTree.resolveNode(root, pathSegments);
        if (node == null) {
            return false;
        }
        return permissions.hasAccessibleAction(node, sender, baseCommandName);
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
        if (!permissions.hasCommandPermission(command, info, sender, baseCommandName, targetSubName)) {
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
            String availableSubCommands = permissions.getAvailableSubCommands(subCommands, sender, normalizedCommandName);
            logger.debug("No arguments provided, available subcommands: " + availableSubCommands);
            return CommandResult
                    .failure(InternalMessages.CMD_SPECIFY_SUBCOMMAND.get("subcommands", availableSubCommands));
        }

        SubCommandTree.Node<S> root = getSubCommandTree(command, info);
        SubCommandTree.Traversal<S> traversal = SubCommandTree.traverse(root, args);

        if (traversal.consumed() == 0) {
            if (directAction != null) {
                logger.debug("No subcommand match, falling back to direct execute handler");
                return executeAction(command, info, directAction, sender, args, normalizedCommandName, null, bubbleErrors);
            }
            String subCommandName = args.get(0).toLowerCase(Locale.ROOT);
            logger.debug("Subcommand not found: " + subCommandName + ". Available: " +
                    permissions.getAvailableSubCommands(subCommands, sender, normalizedCommandName));
            return CommandResult.failure(InternalMessages.CMD_UNKNOWN_SUBCOMMAND.get("subcommand", subCommandName));
        }

        if (traversal.lastActionNode() == null) {
            if (traversal.consumed() >= args.size()) {
                List<String> path = args.subList(0, traversal.consumed());
                String available = String.join(", ",
                        permissions.getAvailableSubCommandsList(subCommands, sender, normalizedCommandName, path));
                logger.debug("No subcommand at path, available: " + available);
                return CommandResult.failure(
                        InternalMessages.CMD_SPECIFY_SUBCOMMAND.get("subcommands", available));
            }
            String unknown = args.get(traversal.consumed());
            List<String> path = args.subList(0, traversal.consumed());
            String available = String.join(", ",
                    permissions.getAvailableSubCommandsList(subCommands, sender, normalizedCommandName, path));
            logger.debug("Subcommand not found: " + unknown + ". Available: " + available);
            return CommandResult.failure(InternalMessages.CMD_UNKNOWN_SUBCOMMAND.get("subcommand", unknown));
        }

        CommandAction<S> targetSubCommand = traversal.lastActionNode().action();
        int consumed = traversal.lastActionIndex();
        String subCommandName = targetSubCommand.fullPath();
        logger.debug("Found subcommand: " + subCommandName + ", checking permissions...");

        String subPermission = permissions.resolvePermission(targetSubCommand.permission(),
                "commands." + normalizedCommandName + ".subcommand." + targetSubCommand.permissionSegment());
        platform.ensurePermissionRegistered(subPermission, targetSubCommand.permissionDefault(),
                descriptions.resolveActionDescription(normalizedCommandName, targetSubCommand));
        if (!subPermission.isEmpty()
                && !platform.hasPermission(sender, subPermission, targetSubCommand.permissionDefault())
                && !permissions.hasArgumentPermissionOverride(normalizedCommandName, subCommandName, sender)) {
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
                String usage = usageFormatter.buildUsage(info, subCommandName, arguments);
                return CommandResult.failure(InternalMessages.CMD_INVALID_ARGUMENTS.get("usage", usage));
            }

            if (!permissions.validateArgumentPermissions(normalizedCommandName, subCommandName, arguments, methodArgs, sender)) {
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

            CommandResult commandResult = MagicCommand.resolveResult(result);
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
        OptionIndex optionIndex = buildOptionIndex(arguments, this::isSenderArgument);
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
                TypeParseResult<Object> parsedOption = convertArgumentDetailed(optionValue, argument.getType(), sender);
                result[i] = parsedOption.value();
                filled[i] = true;
                if (parsedOption.isInvalid()) {
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

            if (permissions.lacksArgumentPermission(normalizedCommandName, subCommandName, argument, sender)) {
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

            TypeParseResult<Object> parsedArgument = convertArgumentDetailed(value, argument.getType(), sender);
            result[i] = parsedArgument.value();
            logger.debug("Parsed argument " + i + " (" + argument.getName() + "): " + result[i]
                    + " (type: " + (result[i] != null ? result[i].getClass().getSimpleName() : "null") + ")");

            if (providedByUser && parsedArgument.isInvalid()) {
                if (argument.isOptional() && canLaterArgumentConsume(arguments, i + 1, value, sender)) {
                    userArgIndex = Math.max(0, userArgIndex - 1);
                    logger.debug("Conversion failed for optional argument " + argument.getName()
                            + ", reusing value for next parameter");
                    continue;
                }
                logger.debug("Failed to convert argument " + i + " (" + argument.getName()
                        + ") from value: " + value + " to type: " + argument.getType().getSimpleName());
                return null;
            }

            // Check if conversion failed
            if (value != null && parsedArgument.isInvalid() && !argument.isOptional()) {
                logger.debug("Failed to convert argument " + i + " (" + argument.getName()
                        + ") from value: " + value + " to type: " + argument.getType().getSimpleName());
                return null;
            }

            // A default the author wrote (@DefaultValue) is a promise that the parameter
            // always has a value, so a default that fails to resolve is an error, not a
            // silent null: @DefaultValue("@sender") resolves to no player from console,
            // and passing null would NPE inside the command instead of telling the user
            // to name a player. @Nullable / @OptionalArgument opt out by declaring that
            // null is expected, so they keep the old behaviour.
            if (!providedByUser && parsedArgument.isInvalid() && !argument.isNullable()) {
                logger.debug("Default value did not resolve for argument " + i + " ("
                        + argument.getName() + "): " + value);
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

    private boolean canLaterArgumentConsume(List<CommandArgument> arguments, int startIndex, String value, S sender) {
        if (value == null) {
            return false;
        }
        for (int i = startIndex; i < arguments.size(); i++) {
            CommandArgument argument = arguments.get(i);
            if (argument == null || isSenderArgument(argument) || argument.isFlag()) {
                continue;
            }
            if (argument.isGreedy()) {
                if (argument.getType().equals(String.class)) {
                    return true;
                }
                TypeParseResult<Object> parsed = convertArgumentDetailed(value, argument.getType(), sender);
                return parsed.isSuccess() || argumentHasSuggestion(argument, value, sender);
            }
            if (isArgumentMatch(argument, value, sender)) {
                return true;
            }
            if (!argument.isOptional() && argument.getDefaultValue() == null) {
                return false;
            }
        }
        return false;
    }

    private Object convertArgument(String value, Class<?> type, S sender) {
        return convertArgumentDetailed(value, type, sender).value();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private TypeParseResult<Object> convertArgumentDetailed(String value, Class<?> type, S sender) {
        logger.debug("Converting argument: '" + value + "' to type: " + type.getSimpleName());
        return (TypeParseResult<Object>) typeParserRegistry.parseDetailed(value, (Class) type, sender);
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

    /**
     * Gets tab completion suggestions for a command.
     * 
     * @param name   the command name
     * @param sender the command sender
     * @param args   the command arguments
     * @return a list of suggestions
     */
    public List<String> getSuggestions(String name, S sender, List<String> args) {
        logger.debug(() -> "Getting suggestions for command: " + name + " with args: " + args);

        MagicCommand command = commands.get(name.toLowerCase());
        CommandInfo info = commandInfos.get(name.toLowerCase());
        // Normalize the command name (remove plugin namespace if present)
        String normalizedName = normalizeCommandName(name);
        String baseCommandName = info != null ? info.name().toLowerCase(Locale.ROOT) : normalizedName;

        if (command == null || info == null) {
            logger.debug(() -> "Command not found for suggestions: " + name);
            return Collections.emptyList();
        }

        String targetSubName = (args != null && !args.isEmpty()) ? args.get(0).toLowerCase(Locale.ROOT) : null;
        if (!permissions.hasCommandPermission(command, info, sender, baseCommandName, targetSubName)) {
            logger.debug(() -> "No permission for suggestions");
            return Collections.emptyList();
        }

        try {
            List<String> suggestions = generateSuggestions(command, info, sender, args, baseCommandName);
            logger.debug(() -> "Generated suggestions: " + suggestions);
            return suggestions;
        } catch (Exception e) {
            logger.debug(() -> "Error generating suggestions: " + e);
            return Collections.emptyList();
        }
    }

    private List<String> generateSuggestions(MagicCommand command, CommandInfo info, S sender,
            List<String> args, String normalizedCommandName) {
        List<CommandAction<S>> subCommands = getSubCommandActions(command);
        CommandAction<S> directAction = getDirectAction(command, info);

        logger.debug(() -> "Generating suggestions - executeHandler: " + (directAction != null)
                + ", subCommands: " + subCommands.size() + ", args: " + args);

        // If there's only a direct execute handler and no subcommands
        if (directAction != null && subCommands.isEmpty()) {
            List<CommandArgument> arguments = directAction.arguments();
            logger.debug(() -> "Using direct method suggestions with " + arguments.size() + " arguments");
            return generateDirectMethodSuggestions(command, arguments, sender, args, normalizedCommandName, null, Collections.emptyMap());
        }

        // If there are no subcommands and no execute handler
        if (subCommands.isEmpty() && directAction == null) {
            logger.debug(() -> "No subcommands and no execute handler");
            return Collections.emptyList();
        }

        SubCommandTree.Node<S> root = SubCommandTree.build(subCommands);

        // If no args and we have subcommands, show top-level subcommands
        if (args.isEmpty() && !subCommands.isEmpty()) {
            logger.debug("No args, showing subcommands");
            return permissions.getAvailableChildNames(root, sender, normalizedCommandName);
        }

        String currentInput = args.isEmpty() ? "" : args.get(args.size() - 1);
        List<String> fixedTokens = args.size() > 1 ? args.subList(0, args.size() - 1) : List.of();

        SubCommandTree.Traversal<S> traversal = SubCommandTree.traverse(root, fixedTokens);
        if (traversal.consumed() < fixedTokens.size() && traversal.lastActionNode() == null) {
            return Collections.emptyList();
        }

        SubCommandTree.Node<S> node = traversal.node();
        CommandAction<S> matchedAction = traversal.lastActionNode() != null ? traversal.lastActionNode().action() : null;
        int actionIndex = traversal.lastActionIndex();

        if (matchedAction != null && actionIndex < fixedTokens.size()) {
            String subPermission = permissions.resolvePermission(matchedAction.permission(),
                    "commands." + normalizedCommandName + ".subcommand." + matchedAction.permissionSegment());
            platform.ensurePermissionRegistered(subPermission, matchedAction.permissionDefault(),
                    descriptions.resolveActionDescription(normalizedCommandName, matchedAction));
            if (!subPermission.isEmpty()
                    && !platform.hasPermission(sender, subPermission, matchedAction.permissionDefault())) {
                return Collections.emptyList();
            }
            List<String> subArgs = args.subList(actionIndex, args.size());
            return generateArgumentSuggestions(command, matchedAction, sender, subArgs, currentInput,
                    normalizedCommandName, Collections.emptyMap()); // Initial call, no previous parsed args yet
        }

        List<String> availableChildren = permissions.getAvailableChildNames(node, sender, normalizedCommandName);
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

        String subPermission = permissions.resolvePermission(matchedAction.permission(),
                "commands." + normalizedCommandName + ".subcommand." + matchedAction.permissionSegment());
        platform.ensurePermissionRegistered(subPermission, matchedAction.permissionDefault(),
                descriptions.resolveActionDescription(normalizedCommandName, matchedAction));
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
        OptionIndex optionIndex = buildOptionIndex(arguments, this::isSenderArgument);
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
        logger.debug(() -> "generatePositionalSuggestions called with " + arguments.size()
                + " arguments and " + args.size() + " args");
        logger.debug(() -> "Raw args: " + args);

        if (arguments.isEmpty()) {
            logger.debug(() -> "No arguments defined for direct method");
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
                userInputArguments.add(new ArgumentInfo(arg));
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
                if (permissions.canSuggestArgument(normalizedCommandName, subCommandName, firstArg.argument, sender)) {
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
            if (permissions.canSuggestArgument(normalizedCommandName, subCommandName, targetArg.argument, sender)) {
                suggestions.addAll(generateSuggestionsForArgument(command, targetArg.argument, sender, currentInput,
                        allParsedArguments));
            }
        }

        if (targetArgumentIndex < userInputArguments.size()) {
            ArgumentInfo targetArg = userInputArguments.get(targetArgumentIndex);
            if ((targetArg.argument.isOptional() || targetArg.argument.getDefaultValue() != null)
                    && targetArgumentIndex + 1 < userInputArguments.size()) {
                ArgumentInfo nextArg = userInputArguments.get(targetArgumentIndex + 1);
                if (permissions.canSuggestArgument(normalizedCommandName, subCommandName, nextArg.argument, sender)) {
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
            if (!permissions.canSuggestArgument(normalizedCommandName, subCommandName, pending, sender)) {
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


    private static class ArgumentInfo {
        final CommandArgument argument;

        ArgumentInfo(CommandArgument argument) {
            this.argument = argument;
        }
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
            if (!permissions.canSuggestArgument(normalizedCommandName, subCommandName, argument, sender)) {
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
        logger.debug(() -> "generateSuggestionsForArgument called for argument: " + argument.getName()
                + " with input: '" + currentInput + "'");
        logger.debug(() -> "Argument suggestions: " + argument.getSuggestions());
        logger.debug(() -> "Argument type: " + argument.getType());

        List<String> suggestions = new ArrayList<>();

        // If no explicit suggestions, try to get suggestions from the argument type
        if (argument.getSuggestions().isEmpty()) {
            logger.debug(() -> "No explicit suggestions, getting suggestions for type: "
                    + argument.getType().getSimpleName());
            List<String> typeSuggestions = typeParserRegistry.getSuggestionsForArgumentFiltered(argument,
                    currentInput, sender, previousParsedArguments);
            if (!typeSuggestions.isEmpty()) {
                logger.debug(() -> "Got " + typeSuggestions.size() + " suggestions from type parser");
                return typeSuggestions;
            }
        }

        // Process explicit suggestions
        for (String suggestionSource : argument.getSuggestions()) {
            logger.debug(() -> "Processing suggestion source: '" + suggestionSource + "'");

            if (suggestionSource.contains("|")) {
                String[] sources = suggestionSource.split("\\|");
                for (String source : sources) {
                    List<String> sourceSuggestions = processSuggestionSource(command, source.trim(), sender,
                            currentInput, argument, previousParsedArguments);
                    logger.debug(() -> "Source '" + source.trim() + "' generated: " + sourceSuggestions);
                    suggestions.addAll(sourceSuggestions);
                }
            } else {
                List<String> sourceSuggestions = processSuggestionSource(command, suggestionSource, sender,
                        currentInput, argument, previousParsedArguments);
                logger.debug(() -> "Source '" + suggestionSource + "' generated: " + sourceSuggestions);
                suggestions.addAll(sourceSuggestions);
            }
        }

        List<String> filteredSuggestions = suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(currentInput.toLowerCase()))
                .distinct()
                .collect(Collectors.toList());

        logger.debug(() -> "Final filtered suggestions: " + filteredSuggestions);
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
            List<String> values = new ArrayList<>();
            for (String part : content.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
            return values;
        }

        List<Object> dynamicArgs = new ArrayList<>();
        List<Class<?>> dynamicArgTypes = new ArrayList<>();

        Class<?> playerType = platform.playerType();
        Class<?> senderType = platform.senderType();

        boolean methodTakesPlayer = false;

        // Provider methods resolve against the argument's suggestion host when set
        // (the original carrier instance a flat-mounted/merged command came from),
        // else the registered command itself. Without this, a source naming a
        // carrier method is invisible after mount because the registered command is
        // a wrapper that does not declare it — the method would then be misread as a
        // literal below.
        Object provider = currentArgument.getSuggestionHost() != null
                ? currentArgument.getSuggestionHost()
                : command;

        // Whether a provider method with this name exists at all (any signature).
        // If none does and no special form matched, the source is a static literal
        // value (e.g. @Suggest({"void", "normal", "flat"}) — each array element is a
        // ready-made completion, not a method name), so it is returned verbatim
        // below. A real method-name typo therefore still yields nothing (the method
        // is absent) — but so would an intended literal; we prefer the literal since
        // an existing-method name always wins here.
        boolean providerMethodExists = hasMethodNamed(provider.getClass(), source);

        // A no-arg provider is the common case, but only when the declaration asks for
        // nothing: with contextArgs the author wants those values, so a same-named
        // no-arg overload must not shadow the context-aware one below (it would drop
        // the context silently and suggest the wrong list).
        if (currentArgument.getContextArgs().isEmpty()) {
            try {
                Method method = provider.getClass().getMethod(source);
                Object result = method.invoke(provider);
                if (result instanceof String[]) {
                    return Arrays.asList((String[]) result);
                } else if (result instanceof List) {
                    return (List<String>) result;
                }
            } catch (Exception e) {
            }
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
        
        // Sender and the partially-typed word are both conveniences a provider may or
        // may not declare, so try the candidate signatures from richest to plainest
        // and call the first one that exists. Notably currentInput is offered even
        // when empty — tab on a bare argument is the common case, and a provider that
        // declares it should not need a second overload just to serve it.
        int senderArgs = dynamicArgs.size() - currentArgument.getContextArgs().size();
        List<Object> withoutSender = dynamicArgs.subList(senderArgs, dynamicArgs.size());
        List<Class<?>> withoutSenderTypes = dynamicArgTypes.subList(senderArgs, dynamicArgTypes.size());
        String input = currentInput != null ? currentInput : "";

        List<List<Object>> candidates = List.of(
                append(dynamicArgs, input),      // (sender?, context..., currentInput)
                dynamicArgs,                     // (sender?, context...)
                append(withoutSender, input),    // (context..., currentInput)
                withoutSender);                  // (context...)
        List<List<Class<?>>> candidateTypes = List.of(
                append(dynamicArgTypes, String.class),
                dynamicArgTypes,
                append(withoutSenderTypes, String.class),
                withoutSenderTypes);

        try {
            for (int i = 0; i < candidates.size(); i++) {
                Method matchingMethod = findMatchingMethod(provider.getClass(), source, candidateTypes.get(i));
                if (matchingMethod == null) {
                    continue;
                }
                Object result = matchingMethod.invoke(provider, candidates.get(i).toArray());
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

        // No special form and no provider method by this name: treat the source as a
        // literal completion value. This makes the intuitive array form
        // @Suggest({"a", "b", "c"}) work the same as @Suggest("{a, b, c}").
        if (!providerMethodExists) {
            String literal = source.trim();
            return literal.isEmpty() ? Collections.emptyList() : List.of(literal);
        }

        return Collections.emptyList();
    }

    /** @return {@code list} plus {@code extra}, as a new list. */
    private static <T> List<T> append(List<T> list, T extra) {
        List<T> out = new ArrayList<>(list);
        out.add(extra);
        return out;
    }

    /** @return true if {@code clazz} declares any accessible method named {@code name}. */
    private static boolean hasMethodNamed(Class<?> clazz, String name) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
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
        return usageFormatter.generateUsage(info, subCommands, directAction);
    }

    /**
     * Generates detailed usage strings for all subcommands.
     * 
     * @param command the command instance
     * @param info    the command info
     * @return list of usage strings for each subcommand
     */
    public List<String> generateSubCommandUsages(MagicCommand command, CommandInfo info) {
        return usageFormatter.generateSubCommandUsages(info, getSubCommandActions(command));
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
        return usageFormatter.formatArguments(arguments, forceOptional);
    }

    static final class CommandAction<S> {
        private final String name;
        private final List<String> path;
        private final String description;
        private final List<String> aliases;
        private final String permission;
        private final MagicPermissionDefault permissionDefault;
        private final CommandThreading threading;
        private final List<CommandArgument> arguments;
        private final Method method;
        private final CommandExecutor<S> executor;

        private CommandAction(String name,
                              List<String> path,
                              String description,
                              List<String> aliases,
                              String permission,
                              MagicPermissionDefault permissionDefault,
                              CommandThreading threading,
                              List<CommandArgument> arguments,
                              Method method,
                              CommandExecutor<S> executor) {
            this.name = name != null ? name : "";
            this.path = normalizePath(path);
            this.description = description != null ? description : "";
            this.aliases = aliases != null ? new ArrayList<>(aliases) : new ArrayList<>();
            this.permission = permission != null ? permission : "";
            this.permissionDefault = permissionDefault != null ? permissionDefault : MagicPermissionDefault.OP;
            this.threading = threading != null ? threading : CommandThreading.MAIN;
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
                    subInfo.annotation.threading(),
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
                    spec.threading(),
                    spec.arguments(),
                    null,
                    executor
            );
        }

        static <S> CommandAction<S> forMethod(String name,
                                              String description,
                                              String permission,
                                              MagicPermissionDefault permissionDefault,
                                              CommandThreading threading,
                                              List<CommandArgument> arguments,
                                              Method method) {
            return new CommandAction<>(
                    name,
                    List.of(),
                    description,
                    List.of(),
                    permission,
                    permissionDefault,
                    threading,
                    arguments,
                    method,
                    null
            );
        }

        static <S> CommandAction<S> forExecute(String name,
                                               String description,
                                               String permission,
                                               MagicPermissionDefault permissionDefault,
                                               CommandThreading threading,
                                               List<CommandArgument> arguments,
                                               CommandExecutor<S> executor) {
            return new CommandAction<>(
                    name,
                    List.of(),
                    description,
                    List.of(),
                    permission,
                    permissionDefault,
                    threading,
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

        CommandThreading threading() {
            return threading;
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

    static List<String> normalizePath(List<String> raw) {
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
        return permissions.hasPermissionForHelp(sender, permission, defaultValue, description);
    }

    boolean hasPermissionForHelp(MagicSender sender, String permission, MagicPermissionDefault defaultValue,
                                 String commandName, String description, @Nullable List<String> relativePath) {
        return permissions.hasPermissionForHelp(sender, permission, defaultValue, commandName, description,
                relativePath);
    }

    boolean hasPermissionByPrefixForHelp(MagicSender sender, String... prefixes) {
        return permissions.hasPermissionByPrefixForHelp(sender, prefixes);
    }

    String resolvePermission(String annotationPermission, String fallbackPermission) {
        return permissions.resolvePermission(annotationPermission, fallbackPermission);
    }

    String buildArgumentPermission(String normalizedCommandName, @Nullable String subCommandName, CommandArgument argument) {
        return permissions.buildArgumentPermission(normalizedCommandName, subCommandName, argument);
    }

    String languageScope() {
        return pluginName;
    }

}
