package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.commands.CommandManager.CommandAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SubCommandTreeTest {

    private static CommandAction<Object> action(String name, List<String> path, String... aliases) {
        SubCommandSpec<Object> spec = SubCommandSpec.<Object>builder(name)
                .path(path.toArray(String[]::new))
                .aliases(aliases)
                .execute(execution -> CommandResult.success())
                .build();
        return CommandAction.forDynamicSubCommand(spec, spec.executor());
    }

    @Test
    void buildsNestedNodesAndTraversesByNameOrAlias() {
        CommandAction<Object> list = action("list", List.of("user"), "ls");
        CommandAction<Object> reload = action("reload", List.of());
        SubCommandTree.Node<Object> root = SubCommandTree.build(List.of(list, reload));

        assertEquals(List.of("user", "reload"), List.copyOf(root.children().keySet()));

        SubCommandTree.Traversal<Object> traversal = SubCommandTree.traverse(root, List.of("USER", "ls", "extra"));
        assertEquals(2, traversal.consumed());
        assertSame(list, traversal.lastActionNode().action());
        assertEquals(2, traversal.lastActionIndex());

        SubCommandTree.Traversal<Object> partial = SubCommandTree.traverse(root, List.of("user"));
        assertEquals(1, partial.consumed());
        assertNull(partial.lastActionNode());
    }

    @Test
    void resolveNodeRequiresFullPathMatch() {
        SubCommandTree.Node<Object> root = SubCommandTree.build(List.of(action("list", List.of("user"))));
        assertNotNull(SubCommandTree.resolveNode(root, List.of("user", "list")));
        assertNull(SubCommandTree.resolveNode(root, List.of("user", "missing")));
        assertSame(root, SubCommandTree.resolveNode(root, List.of()));
    }
}
