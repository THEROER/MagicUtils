package dev.ua.theroer.magicutils.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Builder spec for a subcommand.
 *
 * @param <S> sender type
 */
@SuppressWarnings("doclint:missing")
public final class SubCommandSpec<S> {
    private final String name;
    private final String description;
    private final List<String> aliases;
    private final String permission;
    private final MagicPermissionDefault permissionDefault;
    private final List<CommandArgument> arguments;
    private final CommandExecutor<S> executor;
    private final boolean replaceExisting;

    private SubCommandSpec(Builder<S> builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.aliases = List.copyOf(builder.aliases);
        this.permission = builder.permission;
        this.permissionDefault = builder.permissionDefault;
        this.arguments = List.copyOf(builder.arguments);
        this.executor = builder.executor;
        this.replaceExisting = builder.replaceExisting;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public List<String> aliases() {
        return Collections.unmodifiableList(aliases);
    }

    public String permission() {
        return permission;
    }

    public MagicPermissionDefault permissionDefault() {
        return permissionDefault;
    }

    public List<CommandArgument> arguments() {
        return Collections.unmodifiableList(arguments);
    }

    public CommandExecutor<S> executor() {
        return executor;
    }

    public boolean replaceExisting() {
        return replaceExisting;
    }

    public static <S> Builder<S> builder(String name) {
        return new Builder<>(name);
    }

    @SuppressWarnings("doclint:missing")
    public static final class Builder<S> {
        private final String name;
        private String description = "";
        private final List<String> aliases = new ArrayList<>();
        private String permission = "";
        private MagicPermissionDefault permissionDefault = MagicPermissionDefault.OP;
        private final List<CommandArgument> arguments = new ArrayList<>();
        private CommandExecutor<S> executor;
        private boolean replaceExisting = false;

        public Builder(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Subcommand name is required");
            }
            this.name = name;
        }

        public Builder<S> description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        public Builder<S> aliases(String... aliases) {
            if (aliases != null) {
                this.aliases.addAll(Arrays.asList(aliases));
            }
            return this;
        }

        public Builder<S> permission(String permission) {
            this.permission = permission != null ? permission : "";
            return this;
        }

        public Builder<S> permissionDefault(MagicPermissionDefault permissionDefault) {
            this.permissionDefault = permissionDefault != null ? permissionDefault : MagicPermissionDefault.OP;
            return this;
        }

        public Builder<S> argument(CommandArgument argument) {
            if (argument != null) {
                this.arguments.add(argument);
            }
            return this;
        }

        public Builder<S> arguments(List<CommandArgument> arguments) {
            if (arguments != null) {
                this.arguments.addAll(arguments);
            }
            return this;
        }

        public Builder<S> execute(CommandExecutor<S> executor) {
            this.executor = executor;
            return this;
        }

        public Builder<S> replaceExisting(boolean replaceExisting) {
            this.replaceExisting = replaceExisting;
            return this;
        }

        public SubCommandSpec<S> build() {
            if (executor == null) {
                throw new IllegalStateException("Subcommand executor is required");
            }
            return new SubCommandSpec<>(this);
        }
    }
}
