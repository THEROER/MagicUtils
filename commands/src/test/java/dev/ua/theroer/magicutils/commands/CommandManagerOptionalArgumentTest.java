package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.OptionalArgument;
import dev.ua.theroer.magicutils.annotations.ParamName;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandManagerOptionalArgumentTest {

    @Test
    void executeAllowsMissingOptionalEnumArgument() {
        CommandManager<TestSender> manager = commandManager();
        OptionalEnumCommand command = new OptionalEnumCommand();
        manager.register(command, commandInfo(OptionalEnumCommand.class));

        CommandResult result = manager.execute("demo", new TestSender("tester"), List.of());

        assertTrue(result.isSuccess());
        assertTrue(command.executed);
        assertNull(command.lastAction);
    }

    @Test
    void executeRejectsInvalidOptionalEnumArgumentWhenNoLaterArgumentCanConsumeIt() {
        CommandManager<TestSender> manager = commandManager();
        OptionalEnumCommand command = new OptionalEnumCommand();
        manager.register(command, commandInfo(OptionalEnumCommand.class));

        CommandResult result = manager.execute("demo", new TestSender("tester"), List.of("unknown"));

        assertFalse(result.isSuccess());
        assertEquals(InternalMessages.CMD_INVALID_ARGUMENTS.get("usage", "/demo [status|check]"),
                result.getMessage());
        assertFalse(command.executed);
    }

    @Test
    void executeAllowsSkippingOptionalEnumWhenLaterArgumentConsumesToken() {
        CommandManager<TestSender> manager = commandManager();
        OptionalEnumWithRequiredTargetCommand command = new OptionalEnumWithRequiredTargetCommand();
        manager.register(command, commandInfo(OptionalEnumWithRequiredTargetCommand.class));

        CommandResult result = manager.execute("demo", new TestSender("tester"), List.of("Alex"));

        assertTrue(result.isSuccess());
        assertTrue(command.executed);
        assertNull(command.lastAction);
        assertEquals("Alex", command.lastTarget);
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

    private static CommandInfo commandInfo(Class<? extends MagicCommand> type) {
        return type.getAnnotation(CommandInfo.class);
    }

    @CommandInfo(name = "demo")
    private static final class OptionalEnumCommand extends MagicCommand {
        private boolean executed;
        private DemoAction lastAction;

        public CommandResult execute(@ParamName("action") @OptionalArgument DemoAction action) {
            this.executed = true;
            this.lastAction = action;
            return CommandResult.success("ok");
        }
    }

    @CommandInfo(name = "demo")
    private static final class OptionalEnumWithRequiredTargetCommand extends MagicCommand {
        private boolean executed;
        private DemoAction lastAction;
        private String lastTarget;

        public CommandResult execute(@ParamName("action") @OptionalArgument DemoAction action,
                                     @ParamName("target") String target) {
            this.executed = true;
            this.lastAction = action;
            this.lastTarget = target;
            return CommandResult.success("ok");
        }
    }

    private enum DemoAction {
        STATUS,
        CHECK
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
