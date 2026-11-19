package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.commands.CommandManager.CommandAction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prefix tree of subcommand actions keyed by path segment, plus traversal helpers
 * used by dispatch, permission checks, tab completion and schema export.
 */
final class SubCommandTree {

    private SubCommandTree() {
    }

    static <S> Node<S> build(List<CommandAction<S>> subCommands) {
        Node<S> root = new Node<>("");
        if (subCommands == null || subCommands.isEmpty()) {
            return root;
        }
        for (CommandAction<S> action : subCommands) {
            if (action == null) {
                continue;
            }
            Node<S> node = root;
            for (String segment : action.path()) {
                node = node.child(segment);
            }
            node = node.child(action.name());
            node.setAction(action);
        }
        return root;
    }

    static <S> Node<S> resolveNode(Node<S> root, List<String> pathSegments) {
        if (root == null) {
            return null;
        }
        Traversal<S> traversal = traverse(root, pathSegments);
        if (traversal.consumed() < (pathSegments != null ? pathSegments.size() : 0)) {
            return null;
        }
        return traversal.node();
    }

    static <S> Traversal<S> traverse(Node<S> root, List<String> tokens) {
        if (root == null) {
            return new Traversal<>(null, 0, null, 0);
        }
        Node<S> node = root;
        Node<S> lastAction = null;
        int lastActionIndex = 0;
        int index = 0;
        if (tokens != null) {
            for (String token : tokens) {
                if (token == null || token.isEmpty()) {
                    break;
                }
                Node<S> child = node.findChild(token);
                if (child == null) {
                    break;
                }
                node = child;
                index++;
                if (node.action() != null) {
                    lastAction = node;
                    lastActionIndex = index;
                }
            }
        }
        return new Traversal<>(node, index, lastAction, lastActionIndex);
    }

    record Traversal<S>(Node<S> node, int consumed, Node<S> lastActionNode, int lastActionIndex) {
    }

    static final class Node<S> {
        private final String name;
        private List<String> aliases = List.of();
        private final Map<String, Node<S>> children = new LinkedHashMap<>();
        private CommandAction<S> action;

        Node(String name) {
            this.name = name != null ? name : "";
        }

        String name() {
            return name;
        }

        List<String> aliases() {
            return aliases;
        }

        Map<String, Node<S>> children() {
            return children;
        }

        CommandAction<S> action() {
            return action;
        }

        void setAction(CommandAction<S> action) {
            this.action = action;
            if (action != null) {
                this.aliases = action.aliases();
            }
        }

        Node<S> child(String name) {
            String key = name != null ? name.toLowerCase(Locale.ROOT) : "";
            if (key.isEmpty()) {
                return this;
            }
            return children.computeIfAbsent(key, ignored -> new Node<>(name));
        }

        Node<S> findChild(String token) {
            if (token == null || token.isEmpty()) {
                return null;
            }
            String lowered = token.toLowerCase(Locale.ROOT);
            Node<S> direct = children.get(lowered);
            if (direct != null) {
                return direct;
            }
            for (Node<S> child : children.values()) {
                if (child.matches(token)) {
                    return child;
                }
            }
            return null;
        }

        boolean matches(String input) {
            if (input == null) {
                return false;
            }
            if (name.equalsIgnoreCase(input)) {
                return true;
            }
            for (String alias : aliases) {
                if (alias != null && alias.equalsIgnoreCase(input)) {
                    return true;
                }
            }
            return false;
        }
    }
}
