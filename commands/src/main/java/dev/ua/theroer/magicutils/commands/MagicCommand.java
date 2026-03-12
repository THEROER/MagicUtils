package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.*;
import dev.ua.theroer.magicutils.lang.InternalMessages;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Abstract base class for all custom commands.
 * Provides utilities for command info, subcommands, and argument parsing.
 */
public abstract class MagicCommand {

    // Runtime overrides for command metadata
    private CommandInfo infoOverride;
    private String overrideName;
    private final List<String> addedAliases = new ArrayList<>();
    private final Set<String> removedAliases = new HashSet<>();
    private final List<DynamicSubCommand> dynamicSubCommands = new ArrayList<>();
    private DynamicExecute dynamicExecute;
    private boolean frozen;

    /**
     * Default constructor for MagicCommand.
     */
    public MagicCommand() {
    }

    /**
     * Gets the CommandInfo annotation from a class.
     * 
     * @param clazz the command class
     * @return an Optional containing CommandInfo if present
     */
    public static Optional<CommandInfo> getCommandInfo(Class<?> clazz) {
        CommandInfo info = clazz.getAnnotation(CommandInfo.class);
        return Optional.ofNullable(info);
    }

    /**
     * Creates a builder-backed command definition.
     *
     * @param name command name
     * @param <S> sender type
     * @return builder instance
     */
    public static <S> Builder<S> builder(String name) {
        return new Builder<>(name);
    }

    /**
     * Wraps a compatibility command spec into a runtime {@link MagicCommand}.
     *
     * @param spec command spec
     * @return built command
     */
    public static MagicCommand fromSpec(CommandSpec<?> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Command spec is required");
        }
        return new DynamicCommand(spec);
    }

    /**
     * Resolves the effective command metadata after runtime overrides.
     *
     * @return effective command info or {@code null} if this command has no metadata
     */
    public final CommandInfo resolveInfo() {
        return overrideInfo(getClass().getAnnotation(CommandInfo.class));
    }

    /**
     * Returns whether this command is frozen for runtime use.
     *
     * @return true when mutations are no longer allowed
     */
    public final boolean isFrozen() {
        return frozen;
    }

    /**
     * Override command name at runtime.
     *
     * @param name new name
     * @return this
     */
    public MagicCommand withName(String name) {
        ensureMutable();
        this.overrideName = name;
        return this;
    }

    /**
     * Override full command info at runtime (builder API).
     *
     * @param info command metadata
     * @return this
     */
    public MagicCommand withInfo(CommandInfo info) {
        ensureMutable();
        this.infoOverride = info;
        return this;
    }

    /**
     * Add an alias at runtime.
     *
     * @param alias alias to add
     * @return this
     */
    public MagicCommand addAlias(String alias) {
        ensureMutable();
        if (alias != null && !alias.isEmpty()) {
            this.addedAliases.add(alias);
        }
        return this;
    }

    /**
     * Remove an alias (by exact match, case-insensitive) at runtime.
     *
     * @param alias alias to remove
     * @return this
     */
    public MagicCommand removeAlias(String alias) {
        ensureMutable();
        if (alias != null && !alias.isEmpty()) {
            this.removedAliases.add(alias.toLowerCase(Locale.ROOT));
        }
        return this;
    }

    /**
     * Mounts another command tree under this command using the child's own root label.
     *
     * <p>The mounted command is snapshotted into dynamic subcommands. Metadata and route
     * structure are copied at mount time, while execution still targets the mounted command
     * instance.
     *
     * @param command command tree to mount
     * @return this command
     */
    public MagicCommand mount(MagicCommand command) {
        return mount(null, command);
    }

    /**
     * Mounts another command tree under this command using a parent-local route label.
     *
     * <p>This overrides the mounted route segment without renaming the source command
     * definition itself. When a custom route is used, root aliases from the mounted command
     * are not exposed automatically.
     *
     * @param route route label inside this command tree
     * @param command command tree to mount
     * @return this command
     */
    public MagicCommand mount(String route, MagicCommand command) {
        ensureMutable();
        if (command == null) {
            return this;
        }
        if (command == this) {
            throw new IllegalArgumentException("Cannot mount a command into itself");
        }

        String mountedName = sanitizeSegment(route);
        for (MountedSubCommand mounted : snapshotMountedSubCommands(command, mountedName)) {
            addSubCommand(mounted.spec(), mounted.replaceExisting());
        }
        return this;
    }

    /**
     * Add a dynamic subcommand (builder API).
     *
     * @param subCommand subcommand spec
     * @return this
     */
    public MagicCommand addSubCommand(SubCommandSpec<?> subCommand) {
        return addSubCommand(subCommand, subCommand != null && subCommand.replaceExisting());
    }

    /**
     * Add a dynamic subcommand with explicit replace flag.
     *
     * @param subCommand subcommand spec
     * @param replaceExisting whether to override matching subcommands
     * @return this
     */
    public MagicCommand addSubCommand(SubCommandSpec<?> subCommand, boolean replaceExisting) {
        ensureMutable();
        if (subCommand == null) {
            return this;
        }
        this.dynamicSubCommands.add(new DynamicSubCommand(subCommand, replaceExisting));
        return this;
    }

    /**
     * Define a dynamic execute handler (builder API).
     *
     * @param executor command executor
     * @param arguments argument definitions
     * @param replaceExisting whether to replace annotated execute method
     * @return this
     */
    public MagicCommand setExecute(CommandExecutor<?> executor, List<CommandArgument> arguments,
                                   boolean replaceExisting) {
        return setExecute(executor, arguments, CommandThreading.MAIN, replaceExisting);
    }

    /**
     * Define a dynamic execute handler (builder API).
     *
     * @param executor command executor
     * @param arguments argument definitions
     * @param threading threading policy
     * @param replaceExisting whether to replace annotated execute method
     * @return this
     */
    public MagicCommand setExecute(CommandExecutor<?> executor, List<CommandArgument> arguments,
                                   CommandThreading threading, boolean replaceExisting) {
        ensureMutable();
        if (executor == null) {
            return this;
        }
        List<CommandArgument> args = arguments != null ? new ArrayList<>(arguments) : List.of();
        this.dynamicExecute = new DynamicExecute(executor, args, threading, replaceExisting);
        return this;
    }

    /**
     * Define a dynamic execute handler (builder API).
     *
     * @param executor command executor
     * @param arguments argument definitions
     * @return this
     */
    public MagicCommand setExecute(CommandExecutor<?> executor, List<CommandArgument> arguments) {
        return setExecute(executor, arguments, false);
    }

    /**
     * Define a dynamic execute handler (builder API) with explicit threading policy.
     *
     * @param executor command executor
     * @param arguments argument definitions
     * @param threading threading policy
     * @return this
     */
    public MagicCommand setExecute(CommandExecutor<?> executor, List<CommandArgument> arguments,
                                   CommandThreading threading) {
        return setExecute(executor, arguments, threading, false);
    }

    /**
     * Define a dynamic execute handler (builder API).
     *
     * @param executor command executor
     * @param arguments argument definitions
     * @return this
     */
    public MagicCommand setExecute(CommandExecutor<?> executor, CommandArgument... arguments) {
        List<CommandArgument> args = arguments != null ? Arrays.asList(arguments) : List.of();
        return setExecute(executor, args, false);
    }

    /**
     * Define a dynamic execute handler (builder API) with explicit threading policy.
     *
     * @param executor command executor
     * @param threading threading policy
     * @param arguments argument definitions
     * @return this
     */
    public MagicCommand setExecute(CommandExecutor<?> executor, CommandThreading threading,
                                   CommandArgument... arguments) {
        List<CommandArgument> args = arguments != null ? Arrays.asList(arguments) : List.of();
        return setExecute(executor, args, threading, false);
    }

    /**
     * Apply runtime overrides to a CommandInfo annotation.
     *
     * @param original original info
     * @return overridden info
     */
    public CommandInfo overrideInfo(CommandInfo original) {
        CommandInfo base = infoOverride != null ? infoOverride : original;
        if (base == null) {
            return null;
        }
        if (overrideName == null && addedAliases.isEmpty() && removedAliases.isEmpty()) {
            return base;
        }
        return new CommandInfo() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return CommandInfo.class;
            }

            @Override
            public String name() {
                return overrideName != null ? overrideName : base.name();
            }

            @Override
            public String description() {
                return base.description();
            }

            @Override
            public String[] aliases() {
                List<String> aliases = new ArrayList<>();
                aliases.addAll(Arrays.asList(base.aliases()));
                aliases.addAll(addedAliases);
                aliases.removeIf(a -> removedAliases.contains(a.toLowerCase(Locale.ROOT)));
                return aliases.toArray(new String[0]);
            }

            @Override
            public String permission() {
                return base.permission();
            }

            @Override
            public MagicPermissionDefault permissionDefault() {
                return base.permissionDefault();
            }

            @Override
            public CommandThreading threading() {
                return base.threading();
            }
        };
    }

    List<DynamicSubCommand> getDynamicSubCommands() {
        return new ArrayList<>(dynamicSubCommands);
    }

    DynamicExecute getDynamicExecute() {
        return dynamicExecute;
    }

    void freeze() {
        this.frozen = true;
    }

    /**
     * Gets all subcommands from a command class.
     * 
     * @param clazz the command class
     * @return a list of SubCommandInfo
     */
    public static List<SubCommandInfo> getSubCommands(Class<?> clazz) {
        List<SubCommandInfo> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                SubCommand sub = method.getAnnotation(SubCommand.class);
                if (sub == null) {
                    continue;
                }
                String signature = buildSignature(method);
                if (seen.add(signature)) {
                    result.add(new SubCommandInfo(sub, method));
                }
            }
        }
        return result;
    }

    /**
     * Gets the direct execute method from a command class.
     *
     * @param clazz command class
     * @return execute method or {@code null}
     */
    public static Method findExecuteMethod(Class<?> clazz) {
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals("execute") && !method.isAnnotationPresent(SubCommand.class)) {
                    return method;
                }
            }
        }
        return null;
    }

    /**
     * Gets all arguments for a subcommand method.
     * 
     * @param method the subcommand method
     * @return a list of CommandArgument
     */
    public static List<CommandArgument> getArguments(Method method) {
        List<CommandArgument> args = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            String name = param.getName();
            ParamName annotatedName = param.getAnnotation(ParamName.class);
            if (annotatedName != null && !annotatedName.value().isEmpty()) {
                name = annotatedName.value();
            }
            Class<?> type = param.getType();
            boolean optional = false;
            String defaultValue = null;
            List<String> suggestions = new ArrayList<>();
            String permission = null;
            PermissionConditionType permissionCondition = PermissionConditionType.ALWAYS;
            String[] permissionConditionArgs = new String[0];
            CompareMode compareMode = CompareMode.AUTO;
            String permissionMessage = InternalMessages.CMD_NO_PERMISSION.get();
            String permissionNode = null;
            boolean includeArgumentSegment = true;
            MagicPermissionDefault permissionDefault = MagicPermissionDefault.OP;
            boolean greedy = false;
            boolean hasPermissionAnnotation = false;
            AllowedSender[] allowedSenders = new AllowedSender[] { AllowedSender.ANY };
            boolean isSenderParameter = false;
            List<String> optionShortNames = new ArrayList<>();
            List<String> optionLongNames = new ArrayList<>();
            boolean isFlag = false;
            List<String> contextArgs = new ArrayList<>();

            for (Annotation annotation : paramAnnotations[i]) {
                if (annotation instanceof DefaultValue) {
                    defaultValue = ((DefaultValue) annotation).value();
                    optional = true;
                }
                if (annotation instanceof OptionalArgument || isNullableAnnotation(annotation)) {
                    optional = true;
                }
                if (annotation instanceof Suggest) {
                    Suggest suggest = (Suggest) annotation;
                    suggestions.addAll(Arrays.asList(suggest.value()));
                    if (suggest.contextArgs() != null && suggest.contextArgs().length > 0) {
                        contextArgs.addAll(Arrays.asList(suggest.contextArgs()));
                    }
                }
                if (annotation instanceof Permission) {
                    Permission perm = (Permission) annotation;
                    permission = perm.value();
                    permissionCondition = perm.condition();
                    permissionConditionArgs = perm.conditionArgs();
                    compareMode = perm.compare();
                    permissionNode = perm.node();
                    includeArgumentSegment = perm.includeArgumentSegment();
                    permissionDefault = perm.defaultValue();
                    hasPermissionAnnotation = true;
                    if ((permissionCondition == PermissionConditionType.ALWAYS || permissionCondition == null)
                            && perm.when() != null && !perm.when().isEmpty()) {
                        ParsedCondition parsed = parseCondition(perm.when());
                        if (parsed != null) {
                            permissionCondition = parsed.condition();
                            permissionConditionArgs = parsed.args();
                        }
                    }
                    permissionMessage = perm.message();
                }
                if (annotation instanceof Greedy) {
                    greedy = true;
                }
                if (annotation instanceof Sender senderAnno) {
                    isSenderParameter = true;
                    allowedSenders = senderAnno.value();
                }
                if (annotation instanceof Option option) {
                    optionShortNames.addAll(Arrays.asList(option.shortNames()));
                    optionLongNames.addAll(Arrays.asList(option.longNames()));
                    if (option.flag()) {
                        isFlag = true;
                    }
                }
            }

            if (isFlag) {
                optional = true;
                if (defaultValue == null) {
                    defaultValue = "false";
                }
            }

            CommandArgument.Builder builder = CommandArgument.builder(name, type);
            if (optional)
                builder.optional();
            if (defaultValue != null)
                builder.defaultValue(defaultValue);
            if (!suggestions.isEmpty())
                builder.suggestions(suggestions);
            if (hasPermissionAnnotation) {
                builder.markPermissionDeclared();
                builder.permission(permission != null ? permission : "");
            }
            builder.permissionCondition(permissionCondition);
            builder.permissionConditionArgs(permissionConditionArgs);
            builder.compareMode(compareMode);
            builder.permissionNode(permissionNode);
            builder.includeArgumentSegment(includeArgumentSegment);
            builder.permissionDefault(permissionDefault);
            if (permissionMessage != null)
                builder.permissionMessage(permissionMessage);
            if (greedy)
                builder.greedy();
            if (permissionMessage != null)
                builder.permissionMessage(permissionMessage);
            if (isSenderParameter) {
                builder.sender(allowedSenders);
            }
            if (!optionShortNames.isEmpty() || !optionLongNames.isEmpty()) {
                builder.option(optionShortNames.toArray(new String[0]),
                        optionLongNames.toArray(new String[0]),
                        isFlag);
            }
            builder.contextArgs(contextArgs.toArray(new String[0]));

            args.add(builder.build());
        }
        return args;
    }

    private static ParsedCondition parseCondition(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        int open = trimmed.indexOf('(');
        int close = trimmed.lastIndexOf(')');
        if (open == -1 || close <= open) {
            return null;
        }
        String keyword = trimmed.substring(0, open).trim();
        String argsPart = trimmed.substring(open + 1, close);
        String[] parts = Arrays.stream(argsPart.split("[, ]+")).filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        return switch (keyword) {
            case "self" -> new ParsedCondition(PermissionConditionType.SELF, parts);
            case "other", "anyother" -> new ParsedCondition(
                    parts.length > 1 ? PermissionConditionType.ANY_OTHER : PermissionConditionType.OTHER, parts);
            case "not_null", "notnull" -> new ParsedCondition(PermissionConditionType.NOT_NULL, parts);
            case "distinct" -> new ParsedCondition(PermissionConditionType.DISTINCT, parts);
            case "all_distinct", "alldistinct" -> new ParsedCondition(PermissionConditionType.ALL_DISTINCT, parts);
            case "equals", "eq" -> new ParsedCondition(PermissionConditionType.EQUALS, parts);
            case "not_equals", "noteq", "neq" -> new ParsedCondition(PermissionConditionType.NOT_EQUALS, parts);
            case "exists" -> new ParsedCondition(PermissionConditionType.EXISTS, parts);
            default -> null;
        };
    }

    private record ParsedCondition(PermissionConditionType condition, String[] args) {
    }

    private static String buildSignature(Method method) {
        StringBuilder sb = new StringBuilder(method.getName()).append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(params[i].getName());
        }
        return sb.append(')').toString();
    }

    private static boolean isNullableAnnotation(Annotation annotation) {
        if (annotation == null) {
            return false;
        }
        String name = annotation.annotationType().getName();
        if (name == null) {
            return false;
        }
        return name.endsWith(".Nullable") || name.endsWith(".CheckForNull");
    }

    /**
     * Holds information about a subcommand and its method.
     */
    public static class SubCommandInfo {
        /** The SubCommand annotation. */
        public final SubCommand annotation;
        /** The method implementing the subcommand. */
        public final Method method;

        /**
         * Constructs a SubCommandInfo.
         * 
         * @param annotation the SubCommand annotation
         * @param method     the method
         */
        public SubCommandInfo(SubCommand annotation, Method method) {
            this.annotation = annotation;
            this.method = method;
        }
    }

    static final class DynamicSubCommand {
        private final SubCommandSpec<?> spec;
        private final boolean replaceExisting;

        DynamicSubCommand(SubCommandSpec<?> spec, boolean replaceExisting) {
            this.spec = spec;
            this.replaceExisting = replaceExisting;
        }

        SubCommandSpec<?> spec() {
            return spec;
        }

        boolean replaceExisting() {
            return replaceExisting;
        }
    }

    static final class DynamicExecute {
        private final CommandExecutor<?> executor;
        private final List<CommandArgument> arguments;
        private final CommandThreading threading;
        private final boolean replaceExisting;

        DynamicExecute(CommandExecutor<?> executor, List<CommandArgument> arguments,
                       CommandThreading threading, boolean replaceExisting) {
            this.executor = executor;
            this.arguments = arguments != null ? new ArrayList<>(arguments) : List.of();
            this.threading = threading != null ? threading : CommandThreading.MAIN;
            this.replaceExisting = replaceExisting;
        }

        CommandExecutor<?> executor() {
            return executor;
        }

        List<CommandArgument> arguments() {
            return new ArrayList<>(arguments);
        }

        CommandThreading threading() {
            return threading;
        }

        boolean replaceExisting() {
            return replaceExisting;
        }
    }

    private void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException("MagicCommand can no longer be modified after registration");
        }
    }

    private List<MountedSubCommand> snapshotMountedSubCommands(MagicCommand command, String routeOverride) {
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
                            false
                    ),
                    false
            ));
        }

        for (MountedSubCommand subCommand : resolveMountedSubCommands(command)) {
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
                            subCommand.replaceExisting()
                    ),
                    subCommand.replaceExisting()
            ));
        }

        return mounted;
    }

    private List<MountedSubCommand> resolveMountedSubCommands(MagicCommand command) {
        List<MountedSubCommand> resolved = new ArrayList<>();
        for (SubCommandInfo subInfo : getSubCommands(command.getClass())) {
            resolved.add(new MountedSubCommand(
                    createSubCommandSpec(
                            subInfo.annotation.name(),
                            Arrays.asList(subInfo.annotation.path()),
                            subInfo.annotation.description(),
                            Arrays.asList(subInfo.annotation.aliases()),
                            subInfo.annotation.permission(),
                            subInfo.annotation.permissionDefault(),
                            subInfo.annotation.threading(),
                            getArguments(subInfo.method),
                            createMethodExecutor(command, subInfo.method),
                            false
                    ),
                    false
            ));
        }

        for (DynamicSubCommand dynamicSubCommand : command.getDynamicSubCommands()) {
            MountedSubCommand mounted = new MountedSubCommand(
                    createSubCommandSpec(
                            dynamicSubCommand.spec().name(),
                            dynamicSubCommand.spec().path(),
                            dynamicSubCommand.spec().description(),
                            dynamicSubCommand.spec().aliases(),
                            dynamicSubCommand.spec().permission(),
                            dynamicSubCommand.spec().permissionDefault(),
                            dynamicSubCommand.spec().threading(),
                            dynamicSubCommand.spec().arguments(),
                            wrapExecutor(command, dynamicSubCommand.spec().executor()),
                            dynamicSubCommand.replaceExisting()
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

    private MountedAction resolveMountedDirectAction(MagicCommand command, CommandInfo info) {
        Method executeMethod = findExecuteMethod(command.getClass());
        DynamicExecute dynamic = command.getDynamicExecute();
        if (dynamic != null && (dynamic.replaceExisting() || executeMethod == null)) {
            return new MountedAction(dynamic.arguments(), wrapExecutor(command, dynamic.executor()),
                    dynamic.threading());
        }
        if (executeMethod != null) {
            return new MountedAction(getArguments(executeMethod), createMethodExecutor(command, executeMethod),
                    info.threading());
        }
        if (dynamic != null) {
            return new MountedAction(dynamic.arguments(), wrapExecutor(command, dynamic.executor()),
                    dynamic.threading());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static CommandExecutor<Object> wrapExecutor(MagicCommand command, CommandExecutor<?> executor) {
        if (executor == null) {
            return null;
        }
        CommandExecutor<Object> typed = (CommandExecutor<Object>) executor;
        return execution -> typed.execute(new CommandExecution<>(
                command,
                execution.commandName(),
                execution.subCommandName(),
                execution.sender(),
                execution.rawArgs(),
                execution.arguments(),
                execution.parsedArgs()
        ));
    }

    private static CommandExecutor<Object> createMethodExecutor(MagicCommand command, Method method) {
        return execution -> {
            try {
                Object result = method.invoke(command, execution.parsedArgs());
                if (result instanceof CommandResult commandResult) {
                    return commandResult;
                }
                return CommandResult.success();
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Failed to invoke mounted command method", cause);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to invoke mounted command method", e);
            }
        };
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

    private static boolean pathEquals(List<String> left, List<String> right) {
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

    private static List<String> normalizePath(List<String> raw) {
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

    private static String sanitizeSegment(String value) {
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
                                                               boolean replaceExisting) {
        SubCommandSpec.Builder<Object> builder = SubCommandSpec.<Object>builder(name)
                .description(description)
                .permission(permission)
                .permissionDefault(permissionDefault)
                .threading(threading)
                .execute(executor)
                .replaceExisting(replaceExisting);
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

    private record MountedSubCommand(SubCommandSpec<Object> spec, boolean replaceExisting) {
    }

    /**
     * Builder API that produces a concrete {@link MagicCommand}.
     *
     * @param <S> sender type
     */
    public static final class Builder<S> {
        private final CommandSpec.Builder<S> delegate;
        private final List<MountedCommand> mountedCommands = new ArrayList<>();

        private Builder(String name) {
            this.delegate = CommandSpec.builder(name);
        }

        public Builder<S> description(String description) {
            delegate.description(description);
            return this;
        }

        public Builder<S> aliases(String... aliases) {
            delegate.aliases(aliases);
            return this;
        }

        public Builder<S> permission(String permission) {
            delegate.permission(permission);
            return this;
        }

        public Builder<S> permissionDefault(MagicPermissionDefault permissionDefault) {
            delegate.permissionDefault(permissionDefault);
            return this;
        }

        public Builder<S> threading(CommandThreading threading) {
            delegate.threading(threading);
            return this;
        }

        public Builder<S> argument(CommandArgument argument) {
            delegate.argument(argument);
            return this;
        }

        public Builder<S> arguments(List<CommandArgument> arguments) {
            delegate.arguments(arguments);
            return this;
        }

        public Builder<S> execute(CommandExecutor<S> executor) {
            delegate.execute(executor);
            return this;
        }

        public Builder<S> subCommand(SubCommandSpec<S> subCommand) {
            delegate.subCommand(subCommand);
            return this;
        }

        public Builder<S> mount(MagicCommand command) {
            mountedCommands.add(new MountedCommand(null, command));
            return this;
        }

        public Builder<S> mount(String route, MagicCommand command) {
            mountedCommands.add(new MountedCommand(route, command));
            return this;
        }

        public CommandSpec<S> buildSpec() {
            return delegate.build();
        }

        public MagicCommand build() {
            MagicCommand command = fromSpec(buildSpec());
            for (MountedCommand mountedCommand : mountedCommands) {
                command.mount(mountedCommand.route(), mountedCommand.command());
            }
            return command;
        }
    }

    private record MountedCommand(String route, MagicCommand command) {
    }

    /**
     * Gets the usage string for a command class.
     * 
     * @param clazz the command class
     * @return the usage string, or null if not available
     */
    public static String getUsage(Class<?> clazz) {
        CommandInfo info = clazz.getAnnotation(CommandInfo.class);
        if (info == null)
            return null;

        StringBuilder usage = new StringBuilder("/" + info.name());
        List<SubCommandInfo> subCommands = getSubCommands(clazz);

        if (!subCommands.isEmpty()) {
            Set<String> topLevel = new LinkedHashSet<>();
            for (SubCommandInfo sub : subCommands) {
                String[] path = sub.annotation.path();
                if (path != null && path.length > 0 && path[0] != null && !path[0].trim().isEmpty()) {
                    topLevel.add(path[0].trim());
                } else {
                    topLevel.add(sub.annotation.name());
                }
            }
            usage.append(" <");
            int index = 0;
            for (String name : topLevel) {
                if (index > 0) {
                    usage.append("|");
                }
                usage.append(name);
                index++;
            }
            usage.append(">");
        }

        return usage.toString();
    }
}
