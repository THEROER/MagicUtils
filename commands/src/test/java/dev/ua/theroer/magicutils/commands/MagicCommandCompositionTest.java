package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.ParamName;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicCommandCompositionTest {

    @Test
    void builderBuildReturnsConcreteMagicCommand() {
        CommandManager<TestSender> manager = commandManager();
        MagicCommand command = MagicCommand.<TestSender>builder("demo")
                .description("Demo command")
                .aliases("d")
                .argument(CommandArgument.builder("target", String.class).build())
                .execute(ctx -> CommandResult.success(ctx.arg("target", String.class)))
                .subCommand(SubCommandSpec.<TestSender>builder("reload")
                        .description("Reload config")
                        .execute(ctx -> CommandResult.success("reloaded"))
                        .build())
                .build();

        manager.register(command, command.resolveInfo());

        ResolvedCommandSchema schema = manager.describe("demo");
        assertNotNull(schema);
        assertEquals("demo", schema.name());
        assertEquals(List.of("d"), schema.aliases());
        assertNotNull(schema.directAction());
        assertEquals(List.of("target"), schema.directAction().arguments().stream()
                .map(CommandArgument::getName)
                .toList());
        assertEquals("reload", schema.subCommands().children().get(0).name());

        CommandResult result = manager.execute("demo", new TestSender("tester"), List.of("payload"));
        assertTrue(result.isSuccess());
        assertEquals("payload", result.getMessage());
    }

    @Test
    void mountSnapshotsAnnotatedCommandUnderCustomRoute() {
        CommandManager<TestSender> manager = commandManager();
        BanCommand banCommand = new BanCommand();
        MagicCommand adminCommand = MagicCommand.<TestSender>builder("admin")
                .mount("punish", banCommand)
                .build();

        banCommand.withName("ban-renamed-after-mount");

        manager.register(adminCommand, adminCommand.resolveInfo());

        ResolvedCommandSchema schema = manager.describe("admin");
        assertNotNull(schema);

        ResolvedSubCommandNode punish = schema.subCommands().children().stream()
                .filter(node -> node.name().equals("punish"))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of(), punish.aliases());
        assertNotNull(punish.action());
        assertEquals("punish", punish.action().fullPath());
        assertEquals("ban-renamed-after-mount", banCommand.resolveInfo().name());

        CommandResult directResult = manager.execute("admin", new TestSender("tester"), List.of("punish", "Alex"));
        assertTrue(directResult.isSuccess());
        assertEquals("Alex", banCommand.lastTarget);

        CommandResult nestedResult = manager.execute("admin", new TestSender("tester"),
                List.of("punish", "temp", "Steve"));
        assertTrue(nestedResult.isSuccess());
        assertEquals("Steve", banCommand.lastTempTarget);
    }

    @Test
    void copyYieldsUnfrozenCommandThatReRegistersWithMoreSubCommands() {
        CommandManager<TestSender> manager = commandManager();

        // Aggregate root: a bare command that only carries a banner executor.
        MagicCommand root = MagicCommand.<TestSender>builder("root")
                .execute(ctx -> CommandResult.success("banner"))
                .build();
        manager.register(root, root.resolveInfo());
        assertTrue(root.isFrozen());

        // First contributor: copy the frozen root, mount a tree into the copy,
        // and re-register under the same name.
        MagicCommand firstCopy = manager.getCommand("root").copy();
        assertFalse(firstCopy.isFrozen());
        firstCopy.mount("punish", new BanCommand());
        manager.register(firstCopy, firstCopy.resolveInfo());

        ResolvedCommandSchema afterFirst = manager.describe("root");
        assertTrue(afterFirst.subCommands().children().stream()
                .anyMatch(node -> node.name().equals("punish")));

        // Second contributor: copy again — it must see the first mount — and add more.
        MagicCommand secondCopy = manager.getCommand("root").copy();
        secondCopy.mount("greet", new GreetCommand());
        manager.register(secondCopy, secondCopy.resolveInfo());

        ResolvedCommandSchema afterSecond = manager.describe("root");
        List<String> subNames = afterSecond.subCommands().children().stream()
                .map(ResolvedSubCommandNode::name)
                .toList();
        assertTrue(subNames.contains("punish"), "first contributor's sub-command was lost");
        assertTrue(subNames.contains("greet"), "second contributor's sub-command missing");

        // The root's own banner executor survives the copies.
        CommandResult banner = manager.execute("root", new TestSender("tester"), List.of());
        assertTrue(banner.isSuccess());
        assertEquals("banner", banner.getMessage());

        // Both mounted trees are executable through the re-registered root.
        CommandResult punish = manager.execute("root", new TestSender("tester"), List.of("punish", "Alex"));
        assertTrue(punish.isSuccess());
        CommandResult greet = manager.execute("root", new TestSender("tester"), List.of("greet", "Sam"));
        assertTrue(greet.isSuccess());
        assertEquals("Sam", greet.getMessage());
    }

    @Test
    void mountSubCommandsGraftsSubCommandsFlatIntoRoot() {
        CommandManager<TestSender> manager = commandManager();

        MagicCommand root = MagicCommand.<TestSender>builder("root")
                .execute(ctx -> CommandResult.success("banner"))
                .build();
        manager.register(root, root.resolveInfo());

        // Flat-mount a carrier's @SubCommand methods straight under the root.
        MagicCommand copy = manager.getCommand("root").copy();
        copy.mountSubCommands(new GreetCommand());
        manager.register(copy, copy.resolveInfo());

        ResolvedCommandSchema schema = manager.describe("root");
        List<String> subNames = schema.subCommands().children().stream()
                .map(ResolvedSubCommandNode::name)
                .toList();
        // The carrier's own name ("greet") must NOT appear as a nesting level;
        // its sub-command ("hello") sits directly under the root.
        assertFalse(subNames.contains("greet"), "carrier name leaked as a command level");
        assertTrue(subNames.contains("hello"), "carrier's sub-command was not grafted flat");

        CommandResult hello = manager.execute("root", new TestSender("tester"), List.of("hello", "Sam"));
        assertTrue(hello.isSuccess());
        assertEquals("Sam", hello.getMessage());
    }

    @Test
    void registeredCommandsRejectFurtherMutation() {
        CommandManager<TestSender> manager = commandManager();
        MagicCommand command = MagicCommand.<TestSender>builder("freeze")
                .execute(ctx -> CommandResult.success("ok"))
                .build();

        manager.register(command, command.resolveInfo());

        assertTrue(command.isFrozen());
        assertThrows(IllegalStateException.class, () -> command.addAlias("later"));
        assertThrows(IllegalStateException.class, () -> command.mount(new BanCommand()));
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

    @CommandInfo(name = "ban", aliases = {"block"})
    private static final class BanCommand extends MagicCommand {
        private String lastTarget;
        private String lastTempTarget;

        @SuppressWarnings("unused")
        public CommandResult execute(@ParamName("target") String target) {
            this.lastTarget = target;
            return CommandResult.success(target);
        }

        @SubCommand(name = "temp")
        public CommandResult temp(@ParamName("target") String target) {
            this.lastTempTarget = target;
            return CommandResult.success(target);
        }
    }

    @CommandInfo(name = "greet")
    private static final class GreetCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute(@ParamName("who") String who) {
            return CommandResult.success(who);
        }

        @SubCommand(name = "hello")
        @SuppressWarnings("unused")
        public CommandResult hello(@ParamName("who") String who) {
            return CommandResult.success(who);
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
