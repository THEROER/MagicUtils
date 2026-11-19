package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.*;

import java.lang.reflect.Method;
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

        String mountedName = CommandMountResolver.sanitizeSegment(route);
        for (CommandMountResolver.MountedSubCommand mounted : CommandMountResolver.snapshot(command, mountedName)) {
            addSubCommand(mounted.spec(), mounted.replaceExisting());
        }
        return this;
    }

    /**
     * Adds another command's sub-commands directly into this command tree, at the
     * same level — <em>without</em> nesting them under the source command's name the
     * way {@link #mount(MagicCommand)} does.
     *
     * <p>Where {@code mount(cmd)} produces {@code /root <cmd-name> <sub>}, this
     * produces {@code /root <sub>}. Use it to let a plugin contribute top-level
     * sub-commands to a shared root command it does not own: define them as
     * {@code @SubCommand} methods on a carrier class, then flat-mount that carrier
     * into a copy of the root and re-register. The source command's own direct
     * {@code execute} (its bare-name action) is intentionally not carried over —
     * only its sub-commands are.
     *
     * @param command the command whose sub-commands to graft in at this level
     * @return this command
     */
    public MagicCommand mountSubCommands(MagicCommand command) {
        ensureMutable();
        if (command == null) {
            return this;
        }
        if (command == this) {
            throw new IllegalArgumentException("Cannot mount a command into itself");
        }
        for (CommandMountResolver.MountedSubCommand mounted : CommandMountResolver.resolve(command)) {
            addSubCommand(mounted.spec(), mounted.replaceExisting());
        }
        return this;
    }

    /**
     * Merges {@code command}'s sub-commands into this one so several plugins can
     * contribute to a shared tree (e.g. each adding under {@code /nox}) without
     * clobbering each other. Unlike {@link #mountSubCommands}, which blindly appends,
     * this deduplicates by (path + name): when a node already exists, ownership of
     * its <em>execute</em> is decided by {@link MergePolicy} —
     * {@link MergePolicy#CONTRIBUTE} keeps the existing handler (put-if-absent),
     * {@link MergePolicy#OVERRIDE} replaces it (logging {@code warn} if the existing
     * one was itself an explicit override), and {@link MergePolicy#EXTEND} never
     * claims the node. Leaf nodes that don't collide are simply added.
     *
     * @param command the carrier whose sub-commands to merge in
     * @param warn sink for a one-line conflict warning (may be {@code null})
     * @return this command
     */
    public MagicCommand mergeSubCommands(MagicCommand command, java.util.function.Consumer<String> warn) {
        ensureMutable();
        if (command == null || command == this) {
            return this;
        }
        for (CommandMountResolver.MountedSubCommand incoming : CommandMountResolver.resolve(command)) {
            SubCommandSpec<Object> spec = incoming.spec();
            int existingIndex = indexOfSubCommand(spec);
            if (existingIndex < 0) {
                this.dynamicSubCommands.add(new DynamicSubCommand(spec, incoming.replaceExisting()));
                continue;
            }
            resolveNodeConflict(existingIndex, spec, warn);
        }
        return this;
    }

    /** @return index in dynamicSubCommands of a node with the same path+name, or -1 */
    private int indexOfSubCommand(SubCommandSpec<?> spec) {
        List<String> path = CommandMountResolver.normalizePath(spec.path());
        for (int i = 0; i < dynamicSubCommands.size(); i++) {
            SubCommandSpec<?> existing = dynamicSubCommands.get(i).spec();
            if (CommandMountResolver.pathEquals(existing.path(), path)
                    && existing.name().equalsIgnoreCase(spec.name())) {
                return i;
            }
        }
        return -1;
    }

    /** Resolves execute ownership when an incoming node collides with an existing one. */
    private void resolveNodeConflict(int existingIndex, SubCommandSpec<Object> incoming,
                                     java.util.function.Consumer<String> warn) {
        MergePolicy policy = incoming.mergePolicy();
        if (policy == MergePolicy.EXTEND || policy == MergePolicy.CONTRIBUTE) {
            // Keep the existing owner's execute; the node is already present.
            return;
        }
        // OVERRIDE: take the node. Warn if the existing owner also explicitly claimed it.
        SubCommandSpec<?> existing = dynamicSubCommands.get(existingIndex).spec();
        if (existing.mergePolicy() == MergePolicy.OVERRIDE && warn != null) {
            String node = String.join(" ", CommandMountResolver.normalizePath(incoming.path()));
            warn.accept("Command node '" + (node.isEmpty() ? incoming.name() : node + " " + incoming.name())
                    + "' execute is claimed by more than one plugin — using the last one.");
        }
        dynamicSubCommands.set(existingIndex, new DynamicSubCommand(incoming, true));
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
        return new OverriddenCommandInfo(base, overrideName, addedAliases, removedAliases);
    }

    List<DynamicSubCommand> getDynamicSubCommands() {
        return new ArrayList<>(dynamicSubCommands);
    }

    DynamicExecute getDynamicExecute() {
        return dynamicExecute;
    }

    /**
     * Creates an unfrozen duplicate of this command's declarative state so it can be
     * modified and re-registered after the original was frozen by registration.
     *
     * <p>Registration {@link #freeze() freezes} a command, after which {@link #mount}
     * and the other mutators throw. This is the supported way to add sub-commands to
     * an already-registered command tree: copy it, mount into the copy, and register
     * the copy under the same name (registration replaces the previous owner by name).
     * Because a copy preserves the {@link #mount mounted} sub-commands of the source,
     * multiple contributors can each copy-mount-register in turn and their additions
     * accumulate.
     *
     * <p>The copy is a {@link DynamicCommand} carrying the source's effective
     * {@link #resolveInfo() info}, name/alias overrides, dynamic sub-commands (which
     * is where {@link #mount} records mounted trees) and dynamic execute. Sub-commands
     * declared as {@code @SubCommand} <em>methods</em> on a concrete subclass are part
     * of that subclass, not of this declarative state; a subclass that carries such
     * methods and needs them preserved across a copy should override this method to
     * return an instance of its own type. Commands built from a spec/builder or
     * assembled purely by {@link #mount} — such as an aggregate root command — copy
     * losslessly with the default implementation.
     *
     * @return an unfrozen copy that can be mutated and re-registered
     */
    public MagicCommand copy() {
        CommandInfo info = resolveInfo();
        if (info == null) {
            throw new IllegalStateException("Cannot copy a command with no resolvable CommandInfo");
        }
        DynamicCommand copy = new DynamicCommand(
                CommandSpec.builder(info.name())
                        .aliases(info.aliases())
                        .description(info.description())
                        .build());
        copy.withInfo(info);
        DynamicExecute execute = this.dynamicExecute;
        if (execute != null) {
            copy.setExecute(execute.executor(), execute.arguments(), execute.threading(),
                    execute.replaceExisting());
        }
        for (DynamicSubCommand sub : this.dynamicSubCommands) {
            copy.addSubCommand(sub.spec(), sub.replaceExisting());
        }
        return copy;
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
        return CommandMethodIntrospector.subCommands(clazz);
    }

    /**
     * Gets the direct execute method from a command class.
     *
     * @param clazz command class
     * @return execute method or {@code null}
     */
    public static Method findExecuteMethod(Class<?> clazz) {
        return CommandMethodIntrospector.findExecuteMethod(clazz);
    }

    /**
     * Gets all arguments for a subcommand method.
     * 
     * @param method the subcommand method
     * @return a list of CommandArgument
     */
    public static List<CommandArgument> getArguments(Method method) {
        return CommandMethodIntrospector.arguments(method);
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

        /**
         * Sets the command description.
         *
         * @param description description text
         * @return this builder
         */
        public Builder<S> description(String description) {
            delegate.description(description);
            return this;
        }

        /**
         * Sets the command aliases.
         *
         * @param aliases list of aliases
         * @return this builder
         */
        public Builder<S> aliases(String... aliases) {
            delegate.aliases(aliases);
            return this;
        }

        /**
         * Sets the required permission node.
         *
         * @param permission permission node
         * @return this builder
         */
        public Builder<S> permission(String permission) {
            delegate.permission(permission);
            return this;
        }

        /**
         * Sets the default permission state.
         *
         * @param permissionDefault default permission
         * @return this builder
         */
        public Builder<S> permissionDefault(MagicPermissionDefault permissionDefault) {
            delegate.permissionDefault(permissionDefault);
            return this;
        }

        /**
         * Sets the execution threading mode.
         *
         * @param threading execution threading mode
         * @return this builder
         */
        public Builder<S> threading(CommandThreading threading) {
            delegate.threading(threading);
            return this;
        }

        /**
         * Adds a command argument.
         *
         * @param argument command argument
         * @return this builder
         */
        public Builder<S> argument(CommandArgument argument) {
            delegate.argument(argument);
            return this;
        }

        /**
         * Sets the list of command arguments.
         *
         * @param arguments list of arguments
         * @return this builder
         */
        public Builder<S> arguments(List<CommandArgument> arguments) {
            delegate.arguments(arguments);
            return this;
        }

        /**
         * Sets the command executor.
         *
         * @param executor command executor
         * @return this builder
         */
        public Builder<S> execute(CommandExecutor<S> executor) {
            delegate.execute(executor);
            return this;
        }

        /**
         * Adds a subcommand.
         *
         * @param subCommand subcommand specification
         * @return this builder
         */
        public Builder<S> subCommand(SubCommandSpec<S> subCommand) {
            delegate.subCommand(subCommand);
            return this;
        }

        /**
         * Mounts another MagicCommand to this command.
         *
         * @param command command to mount
         * @return this builder
         */
        public Builder<S> mount(MagicCommand command) {
            mountedCommands.add(new MountedCommand(null, command));
            return this;
        }

        /**
         * Mounts another MagicCommand to a specific sub-path.
         *
         * @param route sub-path route
         * @param command command to mount
         * @return this builder
         */
        public Builder<S> mount(String route, MagicCommand command) {
            mountedCommands.add(new MountedCommand(route, command));
            return this;
        }

        /**
         * Builds the command specification.
         *
         * @return command specification
         */
        public CommandSpec<S> buildSpec() {
            return delegate.build();
        }

        /**
         * Builds the MagicCommand instance.
         *
         * @return MagicCommand instance
         */
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
        return CommandMethodIntrospector.usage(clazz);
    }
}
