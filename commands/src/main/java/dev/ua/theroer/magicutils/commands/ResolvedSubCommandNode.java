package dev.ua.theroer.magicutils.commands;

import java.util.Collections;
import java.util.List;

/**
 * Public immutable view of the resolved subcommand tree.
 */
public final class ResolvedSubCommandNode {
    private static final ResolvedSubCommandNode ROOT = new ResolvedSubCommandNode("", List.of(), List.of(), null);

    private final String name;
    private final List<String> aliases;
    private final List<ResolvedSubCommandNode> children;
    private final ResolvedCommandAction action;

    /**
     * Creates a new resolved subcommand node.
     *
     * @param name subcommand name
     * @param aliases subcommand aliases
     * @param children list of child subcommand nodes
     * @param action executable action for this node (can be null)
     */
    public ResolvedSubCommandNode(String name,
                                  List<String> aliases,
                                  List<ResolvedSubCommandNode> children,
                                  ResolvedCommandAction action) {
        this.name = name != null ? name : "";
        this.aliases = aliases != null ? List.copyOf(aliases) : List.of();
        this.children = children != null ? List.copyOf(children) : List.of();
        this.action = action;
    }

    /**
     * Returns an empty root node.
     *
     * @return empty root
     */
    public static ResolvedSubCommandNode root() {
        return ROOT;
    }

    /**
     * Returns the subcommand name.
     *
     * @return subcommand name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the subcommand aliases.
     *
     * @return list of aliases
     */
    public List<String> aliases() {
        return Collections.unmodifiableList(aliases);
    }

    /**
     * Returns the child subcommand nodes.
     *
     * @return child nodes
     */
    public List<ResolvedSubCommandNode> children() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Returns the executable action for this node.
     *
     * @return executable action or null
     */
    public ResolvedCommandAction action() {
        return action;
    }
}
