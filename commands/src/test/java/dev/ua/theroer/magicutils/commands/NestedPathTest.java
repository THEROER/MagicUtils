package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A two-level {@code @SubCommand} path must be given as separate segments
 * ({@code path = {"map", "author"}}), not one space-joined string
 * ({@code path = "map author"}). This pins which form a normal {@code map author
 * remove} input actually reaches.
 */
class NestedPathTest {

    private static final List<String> CALLS = new ArrayList<>();

    @Test
    void arrayPathIsReachable() {
        CALLS.clear();
        CommandManager<TestSender> m = commandManager();
        ArrayCarrier c = new ArrayCarrier();
        m.register(c, c.resolveInfo());

        m.execute("maps", new TestSender("t"), List.of("author", "remove", "alice"));
        assertEquals(List.of("array:alice"), CALLS,
                "segment-array path {\"author\"} should be reachable");
    }

    @Test
    void spaceJoinedPathIsNotReachable() {
        CALLS.clear();
        CommandManager<TestSender> m = commandManager();
        SpaceCarrier c = new SpaceCarrier();
        m.register(c, c.resolveInfo());

        m.execute("maps", new TestSender("t"), List.of("author", "sub", "remove", "alice"));
        assertEquals(List.of(), CALLS,
                "space-joined path \"author sub\" reached the leaf — form may be valid after all");
    }

    /**
     * A node that is BOTH a leaf and a parent: {@code spawn} has its own execute, and
     * {@code spawn add} nests under it. Mirrors {@code /noxw map spawn} (hub) plus
     * {@code /noxw map spawn add <name>}.
     */
    @Test
    void nodeCanBeBothLeafAndParent() {
        CALLS.clear();
        CommandManager<TestSender> m = commandManager();
        HubCarrier c = new HubCarrier();
        m.register(c, c.resolveInfo());

        m.execute("maps", new TestSender("t"), List.of("spawn"));
        m.execute("maps", new TestSender("t"), List.of("spawn", "add", "x"));
        assertEquals(List.of("hub", "add:x"), CALLS,
                "the hub node and its child leaf must both be reachable");
    }

    @CommandInfo(name = "maps")
    private static final class ArrayCarrier extends MagicCommand {
        @SubCommand(name = "remove", path = {"author"})
        @SuppressWarnings("unused")
        public CommandResult remove(String name) {
            CALLS.add("array:" + name);
            return CommandResult.success();
        }
    }

    @CommandInfo(name = "maps")
    private static final class HubCarrier extends MagicCommand {
        @SubCommand(name = "spawn")
        @SuppressWarnings("unused")
        public CommandResult spawn() {
            CALLS.add("hub");
            return CommandResult.success();
        }

        @SubCommand(name = "add", path = {"spawn"})
        @SuppressWarnings("unused")
        public CommandResult add(String name) {
            CALLS.add("add:" + name);
            return CommandResult.success();
        }
    }

    @CommandInfo(name = "maps")
    private static final class SpaceCarrier extends MagicCommand {
        @SubCommand(name = "remove", path = "author sub")
        @SuppressWarnings("unused")
        public CommandResult remove(String name) {
            CALLS.add("space:" + name);
            return CommandResult.success();
        }
    }

    private static CommandManager<TestSender> commandManager() {
        return new CommandManager<>(
                "", "", CommandLogger.noop(),
                new TestCommandPlatform(),
                TypeParserRegistry.createWithDefaults(CommandLogger.noop()));
    }

    private static final class TestCommandPlatform implements CommandPlatform<TestSender> {
        @Override public Class<?> senderType() { return TestSender.class; }
        @Override public String getName(TestSender sender) { return sender.name; }
        @Override public boolean hasPermission(TestSender s, String p, MagicPermissionDefault d) { return true; }
        @Override public void ensurePermissionRegistered(String n, MagicPermissionDefault d, String desc) { }
        @Override public Object resolveSenderArgument(TestSender s, CommandArgument a) { return s; }
    }

    private static final class TestSender {
        private final String name;
        private TestSender(String name) { this.name = name; }
    }
}
