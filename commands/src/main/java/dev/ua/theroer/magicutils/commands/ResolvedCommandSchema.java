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

    /**
     * Creates a new resolved command schema.
     *
     * @param name command name
     * @param description command description
     * @param aliases command aliases
     * @param permission required permission
     * @param permissionDefault default permission state
     * @param directAction action for the root command (can be null)
     * @param subCommands subcommand tree
     */
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

    /**
     * Returns the command name.
     *
     * @return command name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the command description.
     *
     * @return command description
     */
    public String description() {
        return description;
    }

    /**
     * Returns the command aliases.
     *
     * @return command aliases
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
     * Returns the direct action for the root command.
     *
     * @return direct action or null
     */
    public ResolvedCommandAction directAction() {
        return directAction;
    }

    /**
     * Returns the subcommand tree.
     *
     * @return subcommands
     */
    public ResolvedSubCommandNode subCommands() {
        return subCommands;
    }

    /**
     * Returns all root labels (name + aliases) for this command.
     *
     * @return root labels
     */
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
