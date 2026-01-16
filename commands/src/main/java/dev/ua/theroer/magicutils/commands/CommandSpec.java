package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Builder spec for a command.
 *
 * @param <S> sender type
 */
public final class CommandSpec<S> {
    private final String name;
    private final String description;
    private final List<String> aliases;
    private final String permission;
    private final MagicPermissionDefault permissionDefault;
    private final List<CommandArgument> arguments;
    private final CommandExecutor<S> executor;
    private final List<SubCommandSpec<S>> subCommands;

    private CommandSpec(Builder<S> builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.aliases = List.copyOf(builder.aliases);
        this.permission = builder.permission;
        this.permissionDefault = builder.permissionDefault;
        this.arguments = List.copyOf(builder.arguments);
        this.executor = builder.executor;
        this.subCommands = List.copyOf(builder.subCommands);
    }

    /**
     * Returns the primary command name.
     *
     * @return command name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the command description.
     *
     * @return description text
     */
    public String description() {
        return description;
    }

    /**
     * Returns configured aliases.
     *
     * @return immutable alias list
     */
    public List<String> aliases() {
        return Collections.unmodifiableList(aliases);
    }

    /**
     * Returns the explicit permission node.
     *
     * @return permission string or empty when unset
     */
    public String permission() {
        return permission;
    }

    /**
     * Returns the default permission value.
     *
     * @return default permission enum
     */
    public MagicPermissionDefault permissionDefault() {
        return permissionDefault;
    }

    /**
     * Returns the argument definitions.
     *
     * @return immutable argument list
     */
    public List<CommandArgument> arguments() {
        return Collections.unmodifiableList(arguments);
    }

    /**
     * Returns the execution handler.
     *
     * @return command executor
     */
    public CommandExecutor<S> executor() {
        return executor;
    }

    /**
     * Returns subcommand specs.
     *
     * @return immutable subcommand list
     */
    public List<SubCommandSpec<S>> subCommands() {
        return Collections.unmodifiableList(subCommands);
    }

    /**
     * Creates a builder for a command name.
     *
     * @param name command name
     * @param <S> sender type
     * @return new builder instance
     */
    public static <S> Builder<S> builder(String name) {
        return new Builder<>(name);
    }

    CommandInfo toCommandInfo() {
        return new CommandInfo() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return CommandInfo.class;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public String[] aliases() {
                return aliases.toArray(new String[0]);
            }

            @Override
            public String permission() {
                return permission;
            }

            @Override
            public MagicPermissionDefault permissionDefault() {
                return permissionDefault;
            }
        };
    }

    /**
     * Builder for command specifications.
     *
     * @param <S> sender type
     */
    public static final class Builder<S> {
        private final String name;
        private String description = "";
        private final List<String> aliases = new ArrayList<>();
        private String permission = "";
        private MagicPermissionDefault permissionDefault = MagicPermissionDefault.OP;
        private final List<CommandArgument> arguments = new ArrayList<>();
        private CommandExecutor<S> executor;
        private final List<SubCommandSpec<S>> subCommands = new ArrayList<>();

        /**
         * Creates a builder for the specified command name.
         *
         * @param name command name
         */
        public Builder(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Command name is required");
            }
            this.name = name;
        }

        /**
         * Sets the command description.
         *
         * @param description description text
         * @return this builder
         */
        public Builder<S> description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        /**
         * Adds command aliases.
         *
         * @param aliases alias list
         * @return this builder
         */
        public Builder<S> aliases(String... aliases) {
            if (aliases != null) {
                this.aliases.addAll(Arrays.asList(aliases));
            }
            return this;
        }

        /**
         * Sets explicit permission node.
         *
         * @param permission permission string
         * @return this builder
         */
        public Builder<S> permission(String permission) {
            this.permission = permission != null ? permission : "";
            return this;
        }

        /**
         * Sets the default permission value.
         *
         * @param permissionDefault default permission enum
         * @return this builder
         */
        public Builder<S> permissionDefault(MagicPermissionDefault permissionDefault) {
            this.permissionDefault = permissionDefault != null ? permissionDefault : MagicPermissionDefault.OP;
            return this;
        }

        /**
         * Adds a single argument.
         *
         * @param argument argument definition
         * @return this builder
         */
        public Builder<S> argument(CommandArgument argument) {
            if (argument != null) {
                this.arguments.add(argument);
            }
            return this;
        }

        /**
         * Adds multiple arguments.
         *
         * @param arguments argument list
         * @return this builder
         */
        public Builder<S> arguments(List<CommandArgument> arguments) {
            if (arguments != null) {
                this.arguments.addAll(arguments);
            }
            return this;
        }

        /**
         * Sets the execution handler.
         *
         * @param executor executor implementation
         * @return this builder
         */
        public Builder<S> execute(CommandExecutor<S> executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Adds a subcommand specification.
         *
         * @param subCommand subcommand spec
         * @return this builder
         */
        public Builder<S> subCommand(SubCommandSpec<S> subCommand) {
            if (subCommand != null) {
                this.subCommands.add(subCommand);
            }
            return this;
        }

        /**
         * Builds the immutable command spec.
         *
         * @return command spec
         */
        public CommandSpec<S> build() {
            return new CommandSpec<>(this);
        }
    }
}
