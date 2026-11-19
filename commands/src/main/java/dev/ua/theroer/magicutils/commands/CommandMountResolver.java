package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.MergePolicy;
import dev.ua.theroer.magicutils.lang.InternalMessages;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Flattens a {@link MagicCommand} tree into the {@link SubCommandSpec}s another command
 * mounts: its {@code @SubCommand} methods, its dynamic sub-commands and (for a nested
 * mount) its direct action, each bound to the mounted instance. Also owns the
 * route-path normalization the merge logic in {@link MagicCommand} relies on.
 */
final class CommandMountResolver {

    private CommandMountResolver() {
    }

    static List<MountedSubCommand> snapshot(MagicCommand command, String routeOverride) {
        CommandInfo info = command.resolveInfo();
        if (info == null) {
            throw new IllegalArgumentException(
                    InternalMessages.ERR_MISSING_COMMANDINFO.get("class", command.getClass().getName())
            );
        }

        String mountedName = routeOverride != null ? routeOverride : info.name();
        List<String> rootAliases = routeOverride != null ? List.of() : Arrays.asList(info.aliases());
        List<MountedSubCommand> mounted = new ArrayList<>();

        MountedAction directAction = resolveMountedDirectAction(command, info);
        if (directAction != null) {
            mounted.add(new MountedSubCommand(
                    createSubCommandSpec(
                            mountedName,
                            List.of(),
                            info.description(),
                            rootAliases,
                            info.permission(),
                            info.permissionDefault(),
                            directAction.threading(),
                            directAction.arguments(),
                            directAction.executor(),
                            false,
                            info.merge()
                    ),
                    false
            ));
        }

        for (MountedSubCommand subCommand : resolve(command)) {
            List<String> rebasedPath = new ArrayList<>();
            rebasedPath.add(mountedName);
            rebasedPath.addAll(subCommand.spec().path());
            mounted.add(new MountedSubCommand(
                    createSubCommandSpec(
                            subCommand.spec().name(),
                            rebasedPath,
                            subCommand.spec().description(),
                            subCommand.spec().aliases(),
                            subCommand.spec().permission(),
                            subCommand.spec().permissionDefault(),
                            subCommand.spec().threading(),
                            subCommand.spec().arguments(),
                            subCommand.spec().executor(),
                            subCommand.replaceExisting(),
                            subCommand.spec().mergePolicy()
                    ),
                    subCommand.replaceExisting()
            ));
        }

        return mounted;
    }

    static List<MountedSubCommand> resolve(MagicCommand command) {
        List<MountedSubCommand> resolved = new ArrayList<>();
        for (MagicCommand.SubCommandInfo subInfo : CommandMethodIntrospector.subCommands(command.getClass())) {
            resolved.add(new MountedSubCommand(
                    createSubCommandSpec(
                            subInfo.annotation.name(),
                            Arrays.asList(subInfo.annotation.path()),
                            subInfo.annotation.description(),
                            Arrays.asList(subInfo.annotation.aliases()),
                            subInfo.annotation.permission(),
                            subInfo.annotation.permissionDefault(),
                            subInfo.annotation.threading(),
                            withSuggestionHost(CommandMethodIntrospector.arguments(subInfo.method), command),
                            MountedCommandExecutors.forMethod(command, subInfo.method),
                            false,
                            subInfo.annotation.merge()
                    ),
                    false
            ));
        }

        for (MagicCommand.DynamicSubCommand dynamicSubCommand : command.getDynamicSubCommands()) {
            MountedSubCommand mounted = new MountedSubCommand(
                    createSubCommandSpec(
                            dynamicSubCommand.spec().name(),
                            dynamicSubCommand.spec().path(),
                            dynamicSubCommand.spec().description(),
                            dynamicSubCommand.spec().aliases(),
                            dynamicSubCommand.spec().permission(),
                            dynamicSubCommand.spec().permissionDefault(),
                            dynamicSubCommand.spec().threading(),
                            withSuggestionHost(dynamicSubCommand.spec().arguments(), command),
                            MountedCommandExecutors.wrap(command, dynamicSubCommand.spec().executor()),
                            dynamicSubCommand.replaceExisting(),
                            dynamicSubCommand.spec().mergePolicy()
                    ),
                    dynamicSubCommand.replaceExisting()
            );
            if (dynamicSubCommand.replaceExisting()) {
                removeMatchingSubCommands(resolved, mounted.spec());
            }
            resolved.add(mounted);
        }

        return resolved;
    }

    /**
     * Tags each argument with {@code host} as the object its {@code @Suggest}
     * provider methods resolve against, unless already tagged by an inner mount.
     * Returns the same list (arguments are mutated in place) for call-site brevity.
     */
    private static List<CommandArgument> withSuggestionHost(List<CommandArgument> arguments, MagicCommand host) {
        if (arguments != null) {
            for (CommandArgument argument : arguments) {
                if (argument != null) {
                    argument.assignSuggestionHostIfAbsent(host);
                }
            }
        }
        return arguments;
    }

    private static MountedAction resolveMountedDirectAction(MagicCommand command, CommandInfo info) {
        Method executeMethod = CommandMethodIntrospector.findExecuteMethod(command.getClass());
        MagicCommand.DynamicExecute dynamic = command.getDynamicExecute();
        if (dynamic != null && (dynamic.replaceExisting() || executeMethod == null)) {
            return new MountedAction(dynamic.arguments(), MountedCommandExecutors.wrap(command, dynamic.executor()),
                    dynamic.threading());
        }
        if (executeMethod != null) {
            return new MountedAction(CommandMethodIntrospector.arguments(executeMethod), MountedCommandExecutors.forMethod(command, executeMethod),
                    info.threading());
        }
        if (dynamic != null) {
            return new MountedAction(dynamic.arguments(), MountedCommandExecutors.wrap(command, dynamic.executor()),
                    dynamic.threading());
        }
        return null;
    }

    private static void removeMatchingSubCommands(List<MountedSubCommand> commands, SubCommandSpec<?> replacement) {
        if (commands == null || commands.isEmpty() || replacement == null) {
            return;
        }
        List<String> replacementPath = normalizePath(replacement.path());
        List<String> replacementKeys = allKeysLower(replacement);
        commands.removeIf(existing -> pathEquals(existing.spec().path(), replacementPath)
                && hasAnyKey(existing.spec(), replacementKeys));
    }

    static boolean pathEquals(List<String> left, List<String> right) {
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

    private static boolean hasAnyKey(SubCommandSpec<?> spec, List<String> keys) {
        if (spec == null || keys == null || keys.isEmpty()) {
            return false;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            if (spec.name().equalsIgnoreCase(key)) {
                return true;
            }
            for (String alias : spec.aliases()) {
                if (alias != null && alias.equalsIgnoreCase(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> allKeysLower(SubCommandSpec<?> spec) {
        List<String> keys = new ArrayList<>();
        if (spec == null) {
            return keys;
        }
        if (spec.name() != null && !spec.name().isBlank()) {
            keys.add(spec.name().toLowerCase(Locale.ROOT));
        }
        for (String alias : spec.aliases()) {
            if (alias != null && !alias.isBlank()) {
                keys.add(alias.toLowerCase(Locale.ROOT));
            }
        }
        return keys;
    }

    static List<String> normalizePath(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String segment : raw) {
            String sanitized = sanitizeSegment(segment);
            if (sanitized != null) {
                normalized.add(sanitized);
            }
        }
        return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
    }

    static String sanitizeSegment(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static SubCommandSpec<Object> createSubCommandSpec(String name,
                                                               List<String> path,
                                                               String description,
                                                               List<String> aliases,
                                                               String permission,
                                                               MagicPermissionDefault permissionDefault,
                                                               CommandThreading threading,
                                                               List<CommandArgument> arguments,
                                                               CommandExecutor<Object> executor,
                                                               boolean replaceExisting,
                                                               MergePolicy mergePolicy) {
        SubCommandSpec.Builder<Object> builder = SubCommandSpec.<Object>builder(name)
                .description(description)
                .permission(permission)
                .permissionDefault(permissionDefault)
                .threading(threading)
                .execute(executor)
                .replaceExisting(replaceExisting)
                .mergePolicy(mergePolicy);
        if (path != null && !path.isEmpty()) {
            builder.path(path.toArray(new String[0]));
        }
        if (aliases != null && !aliases.isEmpty()) {
            builder.aliases(aliases.toArray(new String[0]));
        }
        if (arguments != null && !arguments.isEmpty()) {
            builder.arguments(arguments);
        }
        return builder.build();
    }

    private record MountedAction(List<CommandArgument> arguments,
                                 CommandExecutor<Object> executor,
                                 CommandThreading threading) {
    }

    record MountedSubCommand(SubCommandSpec<Object> spec, boolean replaceExisting) {
    }
}
