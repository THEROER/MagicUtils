package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.Permission;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandManagerPermissionTest {

    @Test
    void executeDeniesProtectedArgumentWithoutPermission() {
        TestCommandPlatform platform = new TestCommandPlatform();
        CommandManager<TestSender> manager = commandManager(platform);
        ProtectedArgumentCommand command = new ProtectedArgumentCommand();
        manager.register(command, commandInfo(ProtectedArgumentCommand.class));

        TestSender sender = new TestSender("tester");
        sender.grant("commands.demo");

        CommandResult result = manager.execute("demo", sender, List.of("payload"));

        assertFalse(result.isSuccess());
        assertEquals(InternalMessages.CMD_NO_PERMISSION.get(), result.getMessage());
        assertFalse(command.executed);
        assertTrue(platform.registeredPermissions.contains("commands.demo.argument.secret"));
    }

    @Test
    void executeAllowsProtectedArgumentWhenPermissionIsGranted() {
        TestCommandPlatform platform = new TestCommandPlatform();
        CommandManager<TestSender> manager = commandManager(platform);
        ProtectedArgumentCommand command = new ProtectedArgumentCommand();
        manager.register(command, commandInfo(ProtectedArgumentCommand.class));

        TestSender sender = new TestSender("tester");
        sender.grant("commands.demo");
        sender.grant("commands.demo.argument.secret");

        CommandResult result = manager.execute("demo", sender, List.of("payload"));

        assertTrue(result.isSuccess());
        assertEquals("payload", command.lastSecret);
        assertTrue(command.executed);
    }

    @Test
    void canAccessCommandWithSubcommandArgumentWildcardPermission() {
        TestCommandPlatform platform = new TestCommandPlatform();
        CommandManager<TestSender> manager = commandManager(platform);
        manager.register(new SubcommandArgumentCommand(), commandInfo(SubcommandArgumentCommand.class));

        TestSender sender = new TestSender("tester");
        sender.grant("commands.demo.subcommand.reload.argument.*");

        assertTrue(manager.canAccessCommand("demo", sender, "reload"));
    }

    private static CommandManager<TestSender> commandManager(TestCommandPlatform platform) {
        return new CommandManager<>(
                "",
                "",
                CommandLogger.noop(),
                platform,
                TypeParserRegistry.createWithDefaults(CommandLogger.noop())
        );
    }

    private static CommandInfo commandInfo(Class<? extends MagicCommand> type) {
        return type.getAnnotation(CommandInfo.class);
    }

    @CommandInfo(name = "demo")
    private static final class ProtectedArgumentCommand extends MagicCommand {
        private boolean executed;
        private String lastSecret;

        @SuppressWarnings("unused")
        public CommandResult execute(@Permission(node = "secret") String secret) {
            this.executed = true;
            this.lastSecret = secret;
            return CommandResult.success("ok");
        }
    }

    @CommandInfo(name = "demo")
    private static final class SubcommandArgumentCommand extends MagicCommand {
        @SubCommand(name = "reload")
        public CommandResult reload(@Permission(node = "target") String target) {
            return CommandResult.success(target);
        }
    }

    private static final class TestCommandPlatform implements CommandPlatform<TestSender> {
        private final List<String> registeredPermissions = new ArrayList<>();

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
            return sender.permissions.contains(permission);
        }

        @Override
        public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
            registeredPermissions.add(node);
        }

        @Override
        public Object resolveSenderArgument(TestSender sender, CommandArgument argument) {
            throw new SenderMismatchException("sender arguments are not used in this test");
        }
    }

    private static final class TestSender {
        private final String name;
        private final Set<String> permissions = new HashSet<>();

        private TestSender(String name) {
            this.name = name;
        }

        private void grant(String permission) {
            permissions.add(permission);
        }
    }
}
