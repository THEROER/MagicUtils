package dev.ua.theroer.magicutils.commands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandArgumentBinderTest {

    private static CommandArgumentBinder<Object> binder() {
        CommandPlatform<Object> platform = new CommandPlatform<>() {
            @Override
            public Class<?> senderType() {
                return Void.class;
            }

            @Override
            public String getName(Object sender) {
                return String.valueOf(sender);
            }

            @Override
            public boolean hasPermission(Object sender, String permission, MagicPermissionDefault defaultValue) {
                return true;
            }

            @Override
            public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue,
                                                   String description) {
            }

            @Override
            public Object resolveSenderArgument(Object sender, CommandArgument argument) {
                return sender;
            }
        };
        TypeParserRegistry<Object> registry = TypeParserRegistry.createWithDefaults(CommandLogger.noop());
        CommandPermissions<Object> permissions = new CommandPermissions<>(platform, CommandLogger.noop(), "", "",
                new CommandDescriptionResolver(""), new ArgumentPermissionConditions<>(registry),
                new CommandActionCatalog<>(), command -> List.of(), argument -> false);
        return new CommandArgumentBinder<>(platform, CommandLogger.noop(), registry, permissions, argument -> false);
    }

    @Test
    void bindsPositionalDefaultsOptionsAndGreedyTail() {
        List<CommandArgument> arguments = List.of(
                CommandArgument.builder("count", Integer.class).defaultValue("1").build(),
                CommandArgument.builder("loud", Boolean.class).option(new String[]{"l"}, new String[0], true).build(),
                CommandArgument.builder("message", String.class).greedy().build());

        Object[] bound = binder().parseArgumentsForDirectMethod(arguments, List.of("3", "-l", "hello", "world"),
                "sender", "demo", null);
        assertArrayEquals(new Object[]{3, true, "hello world"}, bound);

        Object[] withoutFlag = binder().parseArgumentsForDirectMethod(arguments, List.of("5", "hey"), "sender",
                "demo", null);
        assertArrayEquals(new Object[]{5, false, "hey"}, withoutFlag);
    }

    @Test
    void missingRequiredArgumentFailsBinding() {
        List<CommandArgument> arguments = List.of(CommandArgument.builder("target", String.class).build());
        assertNull(binder().parseArgumentsForDirectMethod(arguments, List.of(), "sender", "demo", null));
    }
}
