package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.CommandManager.CommandAction;

import java.util.List;

/**
 * Resolves localized descriptions for commands and their actions within a plugin's
 * language scope.
 */
final class CommandDescriptionResolver {
    private final String pluginName;

    CommandDescriptionResolver(String pluginName) {
        this.pluginName = pluginName;
    }

    String resolveCommandDescription(CommandInfo info) {
        if (info == null) {
            return "";
        }
        return CommandDescriptions.resolveGlobal(pluginName, info.description(), info.name());
    }

    String resolveActionDescription(String commandName, CommandAction<?> action) {
        if (action == null) {
            return "";
        }
        List<String> relativePath = isDirectRootAction(commandName, action)
                ? List.of()
                : action.fullPathSegments();
        return CommandDescriptions.resolveGlobal(pluginName, action.description(), commandName, relativePath);
    }

    private static boolean isDirectRootAction(String commandName, CommandAction<?> action) {
        if (commandName == null || action == null) {
            return false;
        }
        return action.path().isEmpty() && commandName.equalsIgnoreCase(action.name());
    }
}
