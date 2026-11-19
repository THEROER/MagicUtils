package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.CommandManager.CommandAction;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Routes an invocation of a resolved command to its direct handler or subcommand,
 * enforces subcommand/argument permissions, binds arguments and invokes the handler.
 *
 * @param <S> sender type
 */
final class CommandDispatcher<S> {
    private final CommandManager<S> manager;
    private final CommandPlatform<S> platform;
    private final CommandLogger logger;
    private final CommandPermissions<S> permissions;
    private final CommandDescriptionResolver descriptions;
    private final CommandArgumentBinder<S> argumentBinder;
    private final CommandUsageFormatter<S> usageFormatter;

    CommandDispatcher(CommandManager<S> manager,
                      CommandPlatform<S> platform,
                      CommandLogger logger,
                      CommandPermissions<S> permissions,
                      CommandDescriptionResolver descriptions,
                      CommandArgumentBinder<S> argumentBinder,
                      CommandUsageFormatter<S> usageFormatter) {
        this.manager = manager;
        this.platform = platform;
        this.logger = logger;
        this.permissions = permissions;
        this.descriptions = descriptions;
        this.argumentBinder = argumentBinder;
        this.usageFormatter = usageFormatter;
    }

    CommandResult executeCommand(MagicCommand command, CommandInfo info, S sender,
            List<String> args, String normalizedCommandName, boolean bubbleErrors) throws CommandExecutionException {
        List<CommandAction<S>> subCommands = manager.getSubCommandActions(command);
        CommandAction<S> directAction = manager.getDirectAction(command, info);

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

        SubCommandTree.Node<S> root = manager.getSubCommandTree(command, info);
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

            Object[] methodArgs = argumentBinder.parseArgumentsForDirectMethod(arguments, args, sender,
                    normalizedCommandName, subCommandName);

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

            CommandResult commandResult = MountedCommandExecutors.resolveResult(result);
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
}
