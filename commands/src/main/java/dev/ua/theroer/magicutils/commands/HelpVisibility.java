package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import java.util.ArrayList;
import java.util.List;

import static dev.ua.theroer.magicutils.commands.HelpArgumentLabels.argumentLabel;
import static dev.ua.theroer.magicutils.commands.HelpCommandSupport.baseCommandName;
import static dev.ua.theroer.magicutils.commands.HelpCommandSupport.message;

/**
 * Permission-based visibility of commands, subcommands and arguments in help output.
 */
final class HelpVisibility {

    private HelpVisibility() {
    }

    static boolean canViewDirect(CommandManager<?> manager, MagicSender sender, CommandInfo info,
                                 String baseCommandName) {
        if (manager == null || sender == null || info == null) {
            return true;
        }
        String permission = manager.resolvePermission(info.permission(), "commands." + baseCommandName);
        return manager.hasPermissionForHelp(sender, permission, info.permissionDefault(),
                info.name(), info.description(), List.of())
                || hasArgumentPermissionOverride(manager, sender, baseCommandName, null);
    }

    static boolean canViewSubCommand(CommandManager<?> manager, MagicSender sender, String baseCommandName,
                                     CommandManager.CommandAction<?> subInfo) {
        if (manager == null || sender == null || subInfo == null) {
            return true;
        }
        String permission = manager.resolvePermission(subInfo.permission(),
                "commands." + baseCommandName + ".subcommand." + subInfo.permissionSegment());
        return manager.hasPermissionForHelp(sender, permission, subInfo.permissionDefault(),
                baseCommandName, subInfo.description(), subInfo.fullPathSegments())
                || hasArgumentPermissionOverride(manager, sender, baseCommandName, subInfo.fullPath());
    }

    static List<CommandManager.CommandAction<?>> filterSubCommands(CommandManager<?> manager,
                                                                   MagicSender sender,
                                                                   String baseCommandName,
                                                                   List<CommandManager.CommandAction<?>> subs) {
        if (subs == null || subs.isEmpty()) {
            return List.of();
        }
        if (manager == null || sender == null) {
            return new ArrayList<>(subs);
        }
        List<CommandManager.CommandAction<?>> allowed = new ArrayList<>();
        for (CommandManager.CommandAction<?> sub : subs) {
            if (canViewSubCommand(manager, sender, baseCommandName, sub)) {
                allowed.add(sub);
            }
        }
        return allowed;
    }

    static List<CommandArgument> filterVisibleArguments(CommandManager<?> manager,
                                                        MagicSender sender,
                                                        String baseCommandName,
                                                        String subCommandName,
                                                        List<CommandArgument> args) {
        if (args == null || args.isEmpty()) {
            return List.of();
        }
        List<CommandArgument> visible = new ArrayList<>();
        for (CommandArgument arg : args) {
            if (arg == null) {
                continue;
            }
            boolean senderArg = arg.isSenderParameter();
            if (!senderArg && manager != null) {
                senderArg = manager.isSenderArgument(arg);
            }
            if (senderArg) {
                continue;
            }
            if (!canViewArgument(manager, sender, baseCommandName, subCommandName, arg)) {
                continue;
            }
            visible.add(arg);
        }
        return visible;
    }

    static boolean canViewArgument(CommandManager<?> manager, MagicSender sender, String baseCommandName,
                                   String subCommandName, CommandArgument argument) {
        if (manager == null || sender == null || argument == null) {
            return true;
        }
        if (!argument.hasPermission()) {
            return true;
        }
        String fallback = manager.buildArgumentPermission(baseCommandName, subCommandName, argument);
        String resolved = manager.resolvePermission(argument.getPermission(), fallback);
        String argName = argument.getName();
        if (argName == null || argName.isEmpty()) {
            argName = argumentLabel(argument);
        }
        String subCommandSuffix = subCommandName != null && !subCommandName.isEmpty() ? " " + subCommandName : "";
        String description = message(manager, null, InternalMessages.CMD_HELP_ARGUMENT_PERMISSION,
                "argument", argName,
                "command", baseCommandName,
                "subcommandSuffix", subCommandSuffix);
        return manager.hasPermissionForHelp(sender, resolved, argument.getPermissionDefault(), description);
    }

    static boolean hasArgumentPermissionOverride(CommandManager<?> manager, MagicSender sender,
                                                 String baseCommandName, String subCommandName) {
        if (manager == null || sender == null) {
            return false;
        }
        String prefix = "commands." + baseCommandName;
        String normalized = normalizeSubCommandSegment(subCommandName);
        if (!normalized.isEmpty()) {
            prefix += ".subcommand." + normalized;
        }
        String argPrefix = manager.resolvePermission(null, prefix + ".argument");
        String argPrefixNoSegment = manager.resolvePermission(null, prefix);
        return manager.hasPermissionByPrefixForHelp(sender, argPrefix, argPrefixNoSegment);
    }

    static String normalizeSubCommandSegment(String subCommandName) {
        if (subCommandName == null) {
            return "";
        }
        String trimmed = subCommandName.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.replaceAll("\\s+", ".");
    }
}
