package dev.ua.theroer.magicutils.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Public immutable view of an executable command action.
 */
public final class ResolvedCommandAction {
    private final String name;
    private final List<String> path;
    private final String description;
    private final List<String> aliases;
    private final String permission;
    private final MagicPermissionDefault permissionDefault;
    private final CommandThreading threading;
    private final List<CommandArgument> arguments;

    /**
     * Creates a new resolved command action.
     *
     * @param name action name
     * @param path command path segments
     * @param description action description
     * @param aliases action aliases
     * @param permission required permission
     * @param permissionDefault default permission state
     * @param threading execution threading mode
     * @param arguments list of command arguments
     */
    public ResolvedCommandAction(String name,
                                 List<String> path,
                                 String description,
                                 List<String> aliases,
                                 String permission,
                                 MagicPermissionDefault permissionDefault,
                                 CommandThreading threading,
                                 List<CommandArgument> arguments) {
        this.name = name != null ? name : "";
        this.path = path != null ? List.copyOf(path) : List.of();
        this.description = description != null ? description : "";
        this.aliases = aliases != null ? List.copyOf(aliases) : List.of();
        this.permission = permission != null ? permission : "";
        this.permissionDefault = permissionDefault != null ? permissionDefault : MagicPermissionDefault.OP;
        this.threading = threading != null ? threading : CommandThreading.MAIN;
        this.arguments = arguments != null ? List.copyOf(arguments) : List.of();
    }

    /**
     * Returns the name of the action.
     *
     * @return action name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the command path segments leading to this action.
     *
     * @return path segments
     */
    public List<String> path() {
        return Collections.unmodifiableList(path);
    }

    /**
     * Returns the description of the action.
     *
     * @return action description
     */
    public String description() {
        return description;
    }

    /**
     * Returns the aliases for this action.
     *
     * @return list of aliases
     */
    public List<String> aliases() {
        return Collections.unmodifiableList(aliases);
    }

    /**
     * Returns the required permission node.
     *
     * @return permission node
     */
    public String permission() {
        return permission;
    }

    /**
     * Returns the default permission state.
     *
     * @return default permission
     */
    public MagicPermissionDefault permissionDefault() {
        return permissionDefault;
    }

    /**
     * Returns the execution threading mode.
     *
     * @return threading mode
     */
    public CommandThreading threading() {
        return threading;
    }

    /**
     * Returns the list of arguments for this action.
     *
     * @return command arguments
     */
    public List<CommandArgument> arguments() {
        return Collections.unmodifiableList(arguments);
    }

    /**
     * Returns the full path segments including the action name.
     *
     * @return full path segments
     */
    public List<String> fullPathSegments() {
        List<String> segments = new ArrayList<>(path);
        if (!name.isBlank()) {
            segments.add(name);
        }
        return segments;
    }

    /**
     * Returns the full space-separated command path.
     *
     * @return full path string
     */
    public String fullPath() {
        return String.join(" ", fullPathSegments());
    }
}
