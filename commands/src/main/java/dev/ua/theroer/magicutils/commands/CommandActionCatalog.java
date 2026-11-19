package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.CommandManager.CommandAction;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves and caches the executable actions (direct execute handler and subcommands,
 * static and dynamic) of registered commands. Caches are invalidated when a command's
 * dynamic subcommands or dynamic execute handler change.
 *
 * @param <S> sender type
 */
final class CommandActionCatalog<S> {
    private final Map<MagicCommand, CommandCache<S>> commandCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Method> executeMethodCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<MagicCommand.SubCommandInfo>> subCommandInfoCache = new ConcurrentHashMap<>();
    private final Map<Method, List<CommandArgument>> argumentCache = new ConcurrentHashMap<>();

    CommandAction<S> directAction(MagicCommand command, CommandInfo info) {
        return getCommandCache(command, info).directAction();
    }

    List<CommandAction<S>> subActions(MagicCommand command, CommandInfo info) {
        return getCommandCache(command, info).subActions();
    }

    SubCommandTree.Node<S> tree(MagicCommand command, CommandInfo info) {
        return getCommandCache(command, info).tree();
    }

    private Method getExecuteMethodCached(Class<?> clazz) {
        return executeMethodCache.computeIfAbsent(clazz, MagicCommand::findExecuteMethod);
    }

    private List<MagicCommand.SubCommandInfo> getSubCommandInfoCached(Class<?> clazz) {
        return subCommandInfoCache.computeIfAbsent(clazz, MagicCommand::getSubCommands);
    }

    private List<CommandArgument> getArgumentsCached(Method method) {
        return argumentCache.computeIfAbsent(method, MagicCommand::getArguments);
    }

    private CommandCache<S> getCommandCache(MagicCommand command, CommandInfo info) {
        if (command == null || info == null) {
            return new CommandCache<>(0, null, List.of(), null, new SubCommandTree.Node<>(""));
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
                    info.permissionDefault(), info.threading(), arguments, executeMethod);
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
        SubCommandTree.Node<S> tree = SubCommandTree.build(combinedSubActions);

        return new CommandCache<>(dynamicSubs.size(), dynamicExecute, combinedSubActions, resolvedDirectAction, tree);
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
                    info.permissionDefault(), dynamicExecute.threading(), dynamicExecute.arguments(),
                    castExecutor(dynamicExecute.executor()));
        }
        if (baseAction != null) {
            return baseAction;
        }
        if (dynamicExecute != null) {
            return CommandAction.forExecute(info.name(), info.description(), info.permission(),
                    info.permissionDefault(), dynamicExecute.threading(), dynamicExecute.arguments(),
                    castExecutor(dynamicExecute.executor()));
        }
        return null;
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
        List<String> replacementPath = CommandManager.normalizePath(replacement.path());
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

    private static boolean pathEquals(List<String> left, List<String> right) {
        List<String> leftNorm = CommandManager.normalizePath(left);
        List<String> rightNorm = CommandManager.normalizePath(right);
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

    private static final class CommandCache<S> {
        private final int dynamicSubCount;
        private final MagicCommand.DynamicExecute dynamicExecute;
        private final List<CommandAction<S>> subActions;
        private final CommandAction<S> directAction;
        private final SubCommandTree.Node<S> tree;

        private CommandCache(int dynamicSubCount,
                             MagicCommand.DynamicExecute dynamicExecute,
                             List<CommandAction<S>> subActions,
                             CommandAction<S> directAction,
                             SubCommandTree.Node<S> tree) {
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

        SubCommandTree.Node<S> tree() {
            return tree;
        }
    }
}
