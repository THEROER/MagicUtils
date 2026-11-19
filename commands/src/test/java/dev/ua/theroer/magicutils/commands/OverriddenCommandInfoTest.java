package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OverriddenCommandInfoTest {

    @Test
    void appliesNameAndAliasOverridesAndDelegatesTheRest() {
        CommandInfo base = Declared.class.getAnnotation(CommandInfo.class);
        List<String> added = new ArrayList<>(List.of("extra"));
        Set<String> removed = new HashSet<>(Set.of("old"));

        CommandInfo info = new OverriddenCommandInfo(base, "renamed", added, removed);

        assertEquals("renamed", info.name());
        assertArrayEquals(new String[] {"keep", "extra"}, info.aliases());
        assertEquals(base.description(), info.description());
        assertEquals(base.permission(), info.permission());
        assertEquals(base.threading(), info.threading());
        assertSame(CommandInfo.class, info.annotationType());
    }

    @Test
    void magicCommandUsesTheOverrideViewOnlyWhenSomethingChanged() {
        Declared command = new Declared();
        CommandInfo declared = Declared.class.getAnnotation(CommandInfo.class);
        assertSame(declared, command.resolveInfo());

        command.withName("other").removeAlias("OLD");

        assertEquals("other", command.resolveInfo().name());
        assertArrayEquals(new String[] {"keep"}, command.resolveInfo().aliases());
    }

    @CommandInfo(name = "declared", aliases = {"keep", "old"}, description = "d", permission = "p")
    static final class Declared extends MagicCommand {
    }
}
