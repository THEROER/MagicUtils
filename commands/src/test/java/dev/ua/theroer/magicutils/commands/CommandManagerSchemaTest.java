package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.ParamName;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommandManagerSchemaTest {

    @Test
    void describeExposesDirectActionAndNestedSubcommandTree() {
        CommandManager<TestSender> manager = new CommandManager<>(
                "",
                "schema",
                CommandLogger.noop(),
                new TestCommandPlatform(),
                TypeParserRegistry.createWithDefaults(CommandLogger.noop())
        );
        DemoCommand command = new DemoCommand();
        manager.register(command, DemoCommand.class.getAnnotation(CommandInfo.class));

        ResolvedCommandSchema schema = manager.describe("demo");

        assertNotNull(schema);
        assertEquals("demo", schema.name());
        assertEquals(List.of("d"), schema.aliases());
        assertNotNull(schema.directAction());
        assertEquals(List.of("target"), schema.directAction().arguments().stream()
                .filter(argument -> !argument.isOption())
                .map(CommandArgument::getName)
                .toList());

        ResolvedSubCommandNode root = schema.subCommands();
        ResolvedSubCommandNode admin = root.children().stream()
                .filter(node -> node.name().equals("admin"))
                .findFirst()
                .orElseThrow();
        ResolvedSubCommandNode ban = admin.children().stream()
                .filter(node -> node.name().equals("ban"))
                .findFirst()
                .orElseThrow();

        assertEquals(List.of("block"), ban.aliases());
        assertNotNull(ban.action());
        assertEquals("admin ban", ban.action().fullPath());
    }

    @CommandInfo(name = "demo", aliases = {"d"})
    private static final class DemoCommand extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute(@ParamName("target") String target) {
            return CommandResult.success(target);
        }

        @SubCommand(name = "ban", path = {"admin"}, aliases = {"block"})
        public CommandResult ban(@ParamName("player") String player) {
            return CommandResult.success(player);
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
            return sender.permissions.contains(permission) || permission == null || permission.isEmpty();
        }

        @Override
        public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
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

        @SuppressWarnings("unused")
        private TestSender grant(String permission) {
            permissions.add(permission);
            return this;
        }
    }
}
