package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandMountResolverTest {

    @Test
    void pathHelpersTrimAndCompareCaseInsensitively() {
        assertNull(CommandMountResolver.sanitizeSegment("   "));
        assertEquals("a", CommandMountResolver.sanitizeSegment(" a "));
        assertEquals(List.of("a", "b"), CommandMountResolver.normalizePath(Arrays.asList(" a", null, "", "b ")));
        assertEquals(List.of(), CommandMountResolver.normalizePath(null));
        assertTrue(CommandMountResolver.pathEquals(List.of("Admin", " x"), List.of("admin", "X")));
        assertFalse(CommandMountResolver.pathEquals(List.of("admin"), List.of("admin", "x")));
    }

    @Test
    void resolveLetsAReplacingDynamicSubCommandShadowTheAnnotatedOne() {
        Tools tools = new Tools();
        tools.addSubCommand(SubCommandSpec.builder("reload")
                .aliases("rl")
                .execute(execution -> CommandResult.success("dynamic"))
                .build(), true);

        List<CommandMountResolver.MountedSubCommand> resolved = CommandMountResolver.resolve(tools);

        assertEquals(2, resolved.size(), "annotated 'reload' is replaced, 'status' stays");
        CommandMountResolver.MountedSubCommand reload = resolved.stream()
                .filter(sub -> sub.spec().name().equals("reload")).findFirst().orElseThrow();
        assertTrue(reload.replaceExisting());
        assertEquals("dynamic", reload.spec().executor().execute(execution(tools)).getMessage());
    }

    @Test
    void snapshotRebasesUnderTheMountedNameAndCarriesTheDirectAction() {
        List<CommandMountResolver.MountedSubCommand> mounted = CommandMountResolver.snapshot(new Tools(), null);

        CommandMountResolver.MountedSubCommand direct = mounted.get(0);
        assertEquals("tools", direct.spec().name());
        assertEquals(List.of(), direct.spec().path());
        assertEquals(List.of("t"), direct.spec().aliases());
        assertTrue(mounted.stream().skip(1).allMatch(sub -> sub.spec().path().equals(List.of("tools"))));
    }

    @Test
    void snapshotWithARouteOverrideDropsRootAliases() {
        List<CommandMountResolver.MountedSubCommand> mounted = CommandMountResolver.snapshot(new Tools(), "kit");

        assertEquals("kit", mounted.get(0).spec().name());
        assertEquals(List.of(), mounted.get(0).spec().aliases());
        assertTrue(mounted.stream().skip(1).allMatch(sub -> sub.spec().path().equals(List.of("kit"))));
    }

    @Test
    void snapshotRequiresCommandInfo() {
        assertThrows(IllegalArgumentException.class, () -> CommandMountResolver.snapshot(new Anonymous(), null));
    }

    private static CommandExecution<Object> execution(MagicCommand command) {
        return new CommandExecution<>(command, "tools", "reload", new Object(), List.of(), List.of(), new Object[0]);
    }

    @CommandInfo(name = "tools", aliases = {"t"})
    static final class Tools extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute() {
            return CommandResult.success("root");
        }

        @SubCommand(name = "reload")
        public CommandResult reload() {
            return CommandResult.success("annotated");
        }

        @SubCommand(name = "status")
        public CommandResult status() {
            return CommandResult.success("status");
        }
    }

    static final class Anonymous extends MagicCommand {
    }
}
