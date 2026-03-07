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

    public ResolvedSubCommandNode(String name,
                                  List<String> aliases,
                                  List<ResolvedSubCommandNode> children,
                                  ResolvedCommandAction action) {
        this.name = name != null ? name : "";
        this.aliases = aliases != null ? List.copyOf(aliases) : List.of();
        this.children = children != null ? List.copyOf(children) : List.of();
        this.action = action;
    }

    public static ResolvedSubCommandNode root() {
        return ROOT;
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return Collections.unmodifiableList(aliases);
    }

    public List<ResolvedSubCommandNode> children() {
        return Collections.unmodifiableList(children);
    }

    public ResolvedCommandAction action() {
        return action;
    }
}
