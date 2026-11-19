package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.CommandManager.CommandAction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Permission resolution and access checks for commands, subcommands and arguments:
 * node naming under the manager's prefix, lazy registration with the platform,
 * sub/argument permission fallbacks and access-filtered subcommand listings.
 *
 * @param <S> sender type
 */
final class CommandPermissions<S> {
    private final CommandPlatform<S> platform;
    private final CommandLogger logger;
    private final String permissionPrefix;
    private final String pluginName;
    private final CommandDescriptionResolver descriptions;
    private final ArgumentPermissionConditions<S> conditions;
    private final CommandActionCatalog<S> actionCatalog;
    private final Function<MagicCommand, List<CommandAction<S>>> subCommandActions;
    private final Predicate<CommandArgument> senderArgument;

    CommandPermissions(CommandPlatform<S> platform,
                       CommandLogger logger,
                       String permissionPrefix,
                       String pluginName,
                       CommandDescriptionResolver descriptions,
                       ArgumentPermissionConditions<S> conditions,
                       CommandActionCatalog<S> actionCatalog,
                       Function<MagicCommand, List<CommandAction<S>>> subCommandActions,
                       Predicate<CommandArgument> senderArgument) {
        this.platform = platform;
        this.logger = logger;
        this.permissionPrefix = permissionPrefix;
        this.pluginName = pluginName;
        this.descriptions = descriptions;
        this.conditions = conditions;
        this.actionCatalog = actionCatalog;
        this.subCommandActions = subCommandActions;
        this.senderArgument = senderArgument;
    }

    boolean hasCommandPermission(MagicCommand command, CommandInfo info, S sender,
            String baseCommandName, @Nullable String targetSubName) {
        String commandPermission = resolvePermission(info.permission(),
                "commands." + baseCommandName);
        platform.ensurePermissionRegistered(commandPermission, info.permissionDefault(),
                descriptions.resolveCommandDescription(info));
        if (commandPermission.isEmpty()) {
            return true;
        }
        if (platform.hasPermission(sender, commandPermission, info.permissionDefault())) {
            return true;
        }
        if (hasSubOrArgPermission(command, info, sender, baseCommandName, targetSubName)) {
            return true;
        }
        return false;
    }

    boolean validateArgumentPermissions(String normalizedCommandName, @Nullable String subCommandName,
            List<CommandArgument> arguments, Object[] values, S sender) {
        Map<String, Object> valuesByName = new HashMap<>();
        Map<String, CommandArgument> argumentsByName = new HashMap<>();

        for (int i = 0; i < arguments.size(); i++) {
            valuesByName.put(arguments.get(i).getName(), values[i]);
            argumentsByName.put(arguments.get(i).getName(), arguments.get(i));
        }

        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument argument = arguments.get(i);
            if (senderArgument.test(argument) || !argument.hasPermission()) {
                continue;
            }

            if (!conditions.shouldCheckPermission(argument, values[i], valuesByName, argumentsByName, sender)) {
                continue;
            }

            String fallback = buildArgumentPermission(normalizedCommandName, subCommandName, argument);
            String resolved = resolvePermission(argument.getPermission(), fallback);
            platform.ensurePermissionRegistered(resolved, argument.getPermissionDefault(),
                    "Argument " + argument.getName() + " for /" + normalizedCommandName
                            + (subCommandName != null ? " " + subCommandName : ""));
            if (resolved != null && !resolved.isEmpty()
                    && !platform.hasPermission(sender, resolved, argument.getPermissionDefault())) {
                logger.debug("Permission denied for argument " + argument.getName()
                        + " (missing permission " + resolved + ")");
                return false;
            }
        }
        return true;
    }

    boolean canSuggestArgument(String normalizedCommandName, @Nullable String subCommandName,
            CommandArgument argument, S sender) {
        if (senderArgument.test(argument)) {
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

    boolean lacksArgumentPermission(String normalizedCommandName, @Nullable String subCommandName,
            CommandArgument argument, S sender) {
        if (senderArgument.test(argument)) {
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

    private boolean hasSubOrArgPermission(MagicCommand command, CommandInfo info, S sender,
            String baseCommandName, @Nullable String targetSubName) {
        List<CommandAction<S>> subs = subCommandActions.apply(command);
        if (subs.isEmpty()) {
            return false;
        }
        if (targetSubName != null && !targetSubName.isEmpty()) {
            SubCommandTree.Node<S> root = actionCatalog.tree(command, info);
            SubCommandTree.Traversal<S> traversal = SubCommandTree.traverse(root, List.of(targetSubName));
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

    private boolean hasPermissionInNode(SubCommandTree.Node<S> node, S sender, String baseCommandName) {
        if (node == null) {
            return false;
        }
        if (node.action() != null) {
            if (canAccessAction(node.action(), sender, baseCommandName)
                    || hasArgumentPermissionOverride(baseCommandName, node.action().fullPath(), sender)) {
                return true;
            }
        }
        for (SubCommandTree.Node<S> child : node.children().values()) {
            if (hasPermissionInNode(child, sender, baseCommandName)) {
                return true;
            }
        }
        return false;
    }

    boolean hasArgumentPermissionOverride(String baseCommandName, @Nullable String subCommandName,
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

    String getAvailableSubCommands(List<CommandAction<S>> subCommands, S sender,
            String commandName) {
        return String.join(", ", getAvailableSubCommandsList(subCommands, sender, commandName, List.of()));
    }

    List<String> getAvailableSubCommandsList(List<CommandAction<S>> subCommands,
            S sender, String commandName, List<String> pathSegments) {
        SubCommandTree.Node<S> root = SubCommandTree.build(subCommands);
        SubCommandTree.Node<S> node = SubCommandTree.resolveNode(root, pathSegments);
        if (node == null) {
            return new ArrayList<>();
        }
        return getAvailableChildNames(node, sender, commandName);
    }

    List<String> getAvailableChildNames(SubCommandTree.Node<S> node, S sender, String commandName) {
        Set<String> available = new LinkedHashSet<>();
        for (SubCommandTree.Node<S> child : node.children().values()) {
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

    boolean hasAccessibleAction(SubCommandTree.Node<S> node, S sender, String commandName) {
        if (node == null) {
            return false;
        }
        if (node.action() != null && canAccessActionOrArguments(node.action(), sender, commandName)) {
            return true;
        }
        for (SubCommandTree.Node<S> child : node.children().values()) {
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

    boolean hasPermissionForHelp(MagicSender sender, String permission, MagicPermissionDefault defaultValue,
                                 String description) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        if (sender == null) {
            return false;
        }
        platform.ensurePermissionRegistered(permission, defaultValue,
                CommandDescriptions.resolveGlobal(pluginName, description));
        S raw = unwrapSender(sender);
        if (raw != null) {
            return platform.hasPermission(raw, permission, defaultValue);
        }
        return sender.hasPermission(permission);
    }

    boolean hasPermissionForHelp(MagicSender sender, String permission, MagicPermissionDefault defaultValue,
                                 String commandName, String description, @Nullable List<String> relativePath) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }
        if (sender == null) {
            return false;
        }
        platform.ensurePermissionRegistered(permission, defaultValue,
                CommandDescriptions.resolveGlobal(pluginName, description, commandName, relativePath));
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
