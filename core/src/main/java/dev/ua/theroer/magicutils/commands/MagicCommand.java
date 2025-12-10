package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.*;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.annotations.Greedy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

import org.jetbrains.annotations.Nullable;

/**
 * Abstract base class for all custom commands.
 * Provides utilities for command info, subcommands, and argument parsing.
 */
public abstract class MagicCommand {

    // Runtime overrides for command metadata
    private String overrideName;
    private final List<String> addedAliases = new ArrayList<>();
    private final Set<String> removedAliases = new HashSet<>();

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
     * Override command name at runtime.
     *
     * @param name new name
     * @return this
     */
    public MagicCommand withName(String name) {
        this.overrideName = name;
        return this;
    }

    /**
     * Add an alias at runtime.
     *
     * @param alias alias to add
     * @return this
     */
    public MagicCommand addAlias(String alias) {
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
        if (alias != null && !alias.isEmpty()) {
            this.removedAliases.add(alias.toLowerCase(Locale.ROOT));
        }
        return this;
    }

    /**
     * Apply runtime overrides to a CommandInfo annotation.
     *
     * @param original original info
     * @return overridden info
     */
    public CommandInfo overrideInfo(CommandInfo original) {
        if (original == null || (overrideName == null && addedAliases.isEmpty() && removedAliases.isEmpty())) {
            return original;
        }
        return new CommandInfo() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return CommandInfo.class;
            }

            @Override
            public String name() {
                return overrideName != null ? overrideName : original.name();
            }

            @Override
            public String description() {
                return original.description();
            }

            @Override
            public String[] aliases() {
                List<String> aliases = new ArrayList<>();
                aliases.addAll(Arrays.asList(original.aliases()));
                aliases.addAll(addedAliases);
                aliases.removeIf(a -> removedAliases.contains(a.toLowerCase(Locale.ROOT)));
                return aliases.toArray(new String[0]);
            }

            @Override
            public String permission() {
                return original.permission();
            }

            @Override
            public MagicPermissionDefault permissionDefault() {
                return original.permissionDefault();
            }
        };
    }

    /**
     * Gets all subcommands from a command class.
     * 
     * @param clazz the command class
     * @return a list of SubCommandInfo
     */
    public static List<SubCommandInfo> getSubCommands(Class<?> clazz) {
        List<SubCommandInfo> result = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            SubCommand sub = method.getAnnotation(SubCommand.class);
            if (sub != null) {
                result.add(new SubCommandInfo(sub, method));
            }
        }
        return result;
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

            for (Annotation annotation : paramAnnotations[i]) {
                if (annotation instanceof DefaultValue) {
                    defaultValue = ((DefaultValue) annotation).value();
                    optional = true;
                }
                if (annotation instanceof OptionalArgument || annotation instanceof Nullable) {
                    optional = true;
                }
                if (annotation instanceof Suggest) {
                    Suggest suggest = (Suggest) annotation;
                    suggestions.addAll(Arrays.asList(suggest.value()));
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
            usage.append(" <");
            for (int i = 0; i < subCommands.size(); i++) {
                if (i > 0)
                    usage.append("|");
                usage.append(subCommands.get(i).annotation.name());
            }
            usage.append(">");
        }

        return usage.toString();
    }
}
