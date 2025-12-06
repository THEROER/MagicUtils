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
            public String permissionDefault() {
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
            String permissionCondition = null;
            String permissionMessage = InternalMessages.CMD_NO_PERMISSION.get();
            boolean greedy = false;

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
                    permissionCondition = perm.when();
                    permissionMessage = perm.message();
                }
                if (annotation instanceof Greedy) {
                    greedy = true;
                }
            }

            CommandArgument.Builder builder = CommandArgument.builder(name, type);
            if (optional)
                builder.optional();
            if (defaultValue != null)
                builder.defaultValue(defaultValue);
            if (!suggestions.isEmpty())
                builder.suggestions(suggestions);
            if (permission != null)
                builder.permission(permission);
            if (permissionCondition != null)
                builder.permissionCondition(permissionCondition);
            if (permissionMessage != null)
                builder.permissionMessage(permissionMessage);
            if (greedy)
                builder.greedy();
            if (permissionMessage != null)
                builder.permissionMessage(permissionMessage);

            args.add(builder.build());
        }
        return args;
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
