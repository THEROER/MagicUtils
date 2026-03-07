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

    public String name() {
        return name;
    }

    public List<String> path() {
        return Collections.unmodifiableList(path);
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

    public CommandThreading threading() {
        return threading;
    }

    public List<CommandArgument> arguments() {
        return Collections.unmodifiableList(arguments);
    }

    public List<String> fullPathSegments() {
        List<String> segments = new ArrayList<>(path);
        if (!name.isBlank()) {
            segments.add(name);
        }
        return segments;
    }

    public String fullPath() {
        return String.join(" ", fullPathSegments());
    }
}
