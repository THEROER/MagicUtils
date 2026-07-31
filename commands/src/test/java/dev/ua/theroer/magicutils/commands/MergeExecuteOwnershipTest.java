package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.MergePolicy;
import dev.ua.theroer.magicutils.annotations.OptionalArgument;
import dev.ua.theroer.magicutils.annotations.ParamName;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import dev.ua.theroer.magicutils.annotations.Suggest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the Noxalis /nox worlds scenario: NoxalisWorlds contributes a
 * {@code worlds} node (EXTEND) with lifecycle leaves under it, then
 * NoxalisProxyAgent contributes a {@code worlds} node (OVERRIDE) that should own
 * the execute. Worlds loads first (as on the lobby server).
 */
class MergeExecuteOwnershipTest {

    @Test
    void agentOverrideOwnsWorldsExecuteAndWorldsLeavesSurvive() {
        CommandManager<TestSender> manager = commandManager();

        // Core registers the /nox root with its own banner execute.
        MagicCommand nox = MagicCommand.<TestSender>builder("nox")
                .execute(ctx -> CommandResult.success("nox-banner"))
                .build();
        manager.register(nox, nox.resolveInfo());

        // NoxalisWorlds contributes first: worlds (EXTEND) + a leaf worlds create.
        MagicCommand afterWorlds = manager.getCommand("nox").copy()
                .mergeSubCommands(new WorldsCarrier(), null);
        manager.register(afterWorlds, afterWorlds.resolveInfo());

        // NoxalisProxyAgent contributes second: worlds (OVERRIDE).
        MagicCommand afterAgent = manager.getCommand("nox").copy()
                .mergeSubCommands(new AgentCarrier(), null);
        manager.register(afterAgent, afterAgent.resolveInfo());

        // 1) /nox worlds must run the AGENT handler (OVERRIDE won).
        CommandResult worlds = manager.execute("nox", new TestSender("t"), List.of("worlds"));
        assertTrue(worlds.isSuccess(), "/nox worlds did not succeed");
        assertEquals("agent-worlds", worlds.getMessage(),
                "/nox worlds execute is not owned by the agent (OVERRIDE)");

        // 2) /nox worlds create <name> must still run the Worlds leaf.
        CommandResult create = manager.execute("nox", new TestSender("t"),
                List.of("worlds", "create", "arena1"));
        assertTrue(create.isSuccess(), "/nox worlds create did not succeed");
        assertEquals("created:arena1", create.getMessage(),
                "worlds create leaf was lost after the agent merge");
    }

    @Test
    void staticSuggestionsSurviveMergeIntoSharedRoot() {
        CommandManager<TestSender> manager = commandManager();

        MagicCommand nox = MagicCommand.<TestSender>builder("nox")
                .execute(ctx -> CommandResult.success("nox-banner"))
                .build();
        manager.register(nox, nox.resolveInfo());

        MagicCommand afterWorlds = manager.getCommand("nox").copy()
                .mergeSubCommands(new WorldsCarrier(), null);
        manager.register(afterWorlds, afterWorlds.resolveInfo());

        // /nox worlds create myworld <TAB on the type arg> must suggest the literals.
        List<String> merged = manager.getSuggestions("nox", new TestSender("t"),
                List.of("worlds", "create", "myworld", ""));
        assertTrue(merged.containsAll(List.of("void", "normal", "flat")),
                "worlds create type @Suggest literals were lost after merge: " + merged);
    }

    @Test
    void arraySuggestLiteralsWorkLikeBraceList() {
        CommandManager<TestSender> m = commandManager();
        SuggestProbe probe = new SuggestProbe();
        m.register(probe, probe.resolveInfo());

        // @Suggest({"a","b","c"}) — array of literals — each element is a ready value.
        assertEquals(List.of("a", "b", "c"),
                m.getSuggestions("probe", new TestSender("t"), List.of("pick", "")));
        // @Suggest("{a, b, c}") — the pre-existing brace-list form — still works.
        assertEquals(List.of("a", "b", "c"),
                m.getSuggestions("probe", new TestSender("t"), List.of("pickbrace", "")));
        // Literals also work on a nested path arg's optional trailing argument.
        assertEquals(List.of("a", "b", "c"),
                m.getSuggestions("probe", new TestSender("t"), List.of("grp", "make", "x", "")));
        // A real provider-method name still resolves to the method, not a literal.
        assertEquals(List.of("srv-1", "srv-2"),
                m.getSuggestions("probe", new TestSender("t"), List.of("dyn", "")));
    }

    @Test
    void providerMethodSuggestionSurvivesMerge() {
        CommandManager<TestSender> manager = commandManager();

        MagicCommand nox = MagicCommand.<TestSender>builder("nox")
                .execute(ctx -> CommandResult.success("nox-banner"))
                .build();
        manager.register(nox, nox.resolveInfo());

        // Contribute a carrier whose sub-command has @Suggest("provideServers"),
        // a method on the carrier — the real /nox worlds [server] shape.
        MagicCommand merged = manager.getCommand("nox").copy()
                .mergeSubCommands(new SuggestProbe(), null);
        manager.register(merged, merged.resolveInfo());

        // /nox dyn <TAB> must invoke provideServers(), not echo "provideServers".
        List<String> s = manager.getSuggestions("nox", new TestSender("t"), List.of("dyn", ""));
        assertEquals(List.of("srv-1", "srv-2"), s,
                "provider-method suggestion was lost/echoed as a literal after merge: " + s);
    }

    enum Kind { VOID, NORMAL, FLAT }

    @Test
    void enumArgSuggestionsSurviveMergeAndParse() {
        CommandManager<TestSender> manager = commandManager();
        MagicCommand nox = MagicCommand.<TestSender>builder("nox")
                .execute(ctx -> CommandResult.success("nox-banner"))
                .build();
        manager.register(nox, nox.resolveInfo());

        MagicCommand merged = manager.getCommand("nox").copy()
                .mergeSubCommands(new EnumCarrier(), null);
        manager.register(merged, merged.resolveInfo());

        // /nox mk foo <TAB> — the enum type arg must auto-suggest its constants.
        List<String> s = manager.getSuggestions("nox", new TestSender("t"),
                List.of("mk", "foo", ""));
        assertTrue(s.containsAll(List.of("void", "normal", "flat")),
                "enum arg lost its auto-suggestions after merge: " + s);

        // And parsing the enum value must succeed after merge.
        CommandResult r = manager.execute("nox", new TestSender("t"), List.of("mk", "foo", "flat"));
        assertTrue(r.isSuccess(), "enum arg failed to parse after merge: " + r.getMessage());
        assertEquals("flat", r.getMessage());
    }

    @CommandInfo(name = "enum-carrier")
    private static final class EnumCarrier extends MagicCommand {
        @SubCommand(name = "mk")
        @SuppressWarnings("unused")
        public CommandResult mk(@ParamName("world") String world,
                                @ParamName("kind") @OptionalArgument Kind kind) {
            return CommandResult.success(kind == null ? "null" : kind.name().toLowerCase());
        }
    }

    @CommandInfo(name = "probe")
    private static final class SuggestProbe extends MagicCommand {
        @SubCommand(name = "pick")
        @SuppressWarnings("unused")
        public CommandResult pick(@ParamName("kind") @Suggest({"a", "b", "c"}) String kind) {
            return CommandResult.success(kind);
        }

        @SubCommand(name = "pickbrace")
        @SuppressWarnings("unused")
        public CommandResult pickBrace(@ParamName("kind") @Suggest("{a, b, c}") String kind) {
            return CommandResult.success(kind);
        }

        @SubCommand(name = "make", path = "grp")
        @SuppressWarnings("unused")
        public CommandResult make(
                @ParamName("id") String id,
                @ParamName("kind") @OptionalArgument @Suggest({"a", "b", "c"}) String kind) {
            return CommandResult.success(id);
        }

        // A source that names a real provider method must call it, not be treated
        // as a literal — this guards the method-wins-over-literal precedence.
        @SubCommand(name = "dyn")
        @SuppressWarnings("unused")
        public CommandResult dyn(@ParamName("who") @Suggest("provideServers") String who) {
            return CommandResult.success(who);
        }

        @SuppressWarnings("unused")
        public List<String> provideServers() {
            return List.of("srv-1", "srv-2");
        }
    }

    private static CommandManager<TestSender> commandManager() {
        return new CommandManager<>(
                "",
                "",
                CommandLogger.noop(),
                new TestCommandPlatform(),
                TypeParserRegistry.createWithDefaults(CommandLogger.noop())
        );
    }

    @CommandInfo(name = "worlds-carrier")
    private static final class WorldsCarrier extends MagicCommand {
        @SubCommand(name = "worlds", merge = MergePolicy.EXTEND)
        @SuppressWarnings("unused")
        public CommandResult worldsRoot() {
            return CommandResult.success("worlds-lifecycle");
        }

        @SubCommand(name = "create", path = "worlds")
        @SuppressWarnings("unused")
        public CommandResult create(
                @ParamName("world") String world,
                @ParamName("type") @OptionalArgument
                @Suggest({"void", "normal", "flat"}) String type) {
            return CommandResult.success("created:" + world);
        }
    }

    @CommandInfo(name = "agent-carrier")
    private static final class AgentCarrier extends MagicCommand {
        @SubCommand(name = "worlds", merge = MergePolicy.OVERRIDE)
        @SuppressWarnings("unused")
        public CommandResult worlds() {
            return CommandResult.success("agent-worlds");
        }
    }

    private static final class TestCommandPlatform implements CommandPlatform<TestSender> {
        @Override
        public Class<?> senderType() {
            return TestSender.class;
        }

        @Override
        public String getName(TestSender sender) {
            return sender.name;
        }

        @Override
        public boolean hasPermission(TestSender sender, String permission, MagicPermissionDefault defaultValue) {
            return true;
        }

        @Override
        public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
        }

        @Override
        public Object resolveSenderArgument(TestSender sender, CommandArgument argument) {
            return sender;
        }
    }

    private static final class TestSender {
        private final String name;

        private TestSender(String name) {
            this.name = name;
        }
    }
}
