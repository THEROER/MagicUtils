package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import lombok.Getter;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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
    private final CommandActionCatalog<S> actionCatalog = new CommandActionCatalog<>();
    private final CommandDescriptionResolver descriptions;
    private final CommandPermissions<S> permissions;
    private final CommandUsageFormatter<S> usageFormatter;
    private final CommandArgumentBinder<S> argumentBinder;
    private final CommandSuggestionEngine<S> suggestionEngine;
    private final CommandDispatcher<S> dispatcher;
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
    @SuppressWarnings("this-escape")
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
        this.argumentBinder = new CommandArgumentBinder<>(this.platform, this.logger, this.typeParserRegistry,
                permissions, this::isSenderArgument);
        this.suggestionEngine = new CommandSuggestionEngine<>(this, this.platform, this.logger,
                this.typeParserRegistry, permissions, descriptions, argumentBinder, this::isSenderArgument);
        this.dispatcher = new CommandDispatcher<>(this, this.platform, this.logger, permissions, descriptions, argumentBinder,
                usageFormatter);
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

    SubCommandTree.Node<S> getSubCommandTree(MagicCommand command, CommandInfo info) {
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
            return dispatcher.executeCommand(command, info, sender, args, baseCommandName, bubbleErrors);
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
            List<String> suggestions = suggestionEngine.generateSuggestions(command, info, sender, args, baseCommandName);
            logger.debug(() -> "Generated suggestions: " + suggestions);
            return suggestions;
        } catch (Exception e) {
            logger.debug(() -> "Error generating suggestions: " + e);
            return Collections.emptyList();
        }
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
