package dev.ua.theroer.magicutils.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Public immutable view of a registered command and its resolved actions.
 */
public final class ResolvedCommandSchema {
    private final String name;
    private final String description;
    private final List<String> aliases;
    private final String permission;
    private final MagicPermissionDefault permissionDefault;
    private final ResolvedCommandAction directAction;
    private final ResolvedSubCommandNode subCommands;

    public ResolvedCommandSchema(String name,
                                 String description,
                                 List<String> aliases,
                                 String permission,
                                 MagicPermissionDefault permissionDefault,
                                 ResolvedCommandAction directAction,
                                 ResolvedSubCommandNode subCommands) {
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
        this.aliases = aliases != null ? List.copyOf(aliases) : List.of();
        this.permission = permission != null ? permission : "";
        this.permissionDefault = permissionDefault != null ? permissionDefault : MagicPermissionDefault.OP;
        this.directAction = directAction;
        this.subCommands = subCommands != null ? subCommands : ResolvedSubCommandNode.root();
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

    public ResolvedCommandAction directAction() {
        return directAction;
    }

    public ResolvedSubCommandNode subCommands() {
        return subCommands;
    }

    public List<String> rootLabels() {
        List<String> labels = new ArrayList<>();
        if (!name.isBlank()) {
            labels.add(name);
        }
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank()) {
                labels.add(alias);
            }
        }
        return labels;
    }
}
