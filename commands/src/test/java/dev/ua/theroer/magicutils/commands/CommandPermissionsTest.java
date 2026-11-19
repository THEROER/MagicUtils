package dev.ua.theroer.magicutils.commands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandPermissionsTest {

    private static CommandPermissions<Object> permissions(String prefix) {
        CommandPlatform<Object> platform = new CommandPlatform<>() {
            @Override
            public Class<?> senderType() {
                return Object.class;
            }

            @Override
            public String getName(Object sender) {
                return String.valueOf(sender);
            }

            @Override
            public boolean hasPermission(Object sender, String permission, MagicPermissionDefault defaultValue) {
                return false;
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
        return new CommandPermissions<>(platform, CommandLogger.noop(), prefix, "demo",
                new CommandDescriptionResolver("demo"),
                new ArgumentPermissionConditions<>(TypeParserRegistry.createWithDefaults(CommandLogger.noop())),
                new CommandActionCatalog<>(), command -> List.of(), argument -> false);
    }

    @Test
    void resolvePermissionAppliesPrefixOnce() {
        CommandPermissions<Object> prefixed = permissions("myplugin");
        assertEquals("myplugin.commands.demo", prefixed.resolvePermission("", "commands.demo"));
        assertEquals("myplugin.custom", prefixed.resolvePermission("myplugin.custom", "ignored"));
        assertEquals("myplugin.dot", prefixed.resolvePermission(".dot", "ignored"));
        assertEquals("", prefixed.resolvePermission(null, null));

        CommandPermissions<Object> bare = permissions("");
        assertEquals("dot", bare.resolvePermission(".dot", "ignored"));
        assertEquals("commands.demo", bare.resolvePermission(null, "commands.demo"));
    }

    @Test
    void buildArgumentPermissionUsesSubcommandPathAndArgumentSegment() {
        CommandPermissions<Object> perms = permissions("");
        CommandArgument plain = CommandArgument.builder("target", String.class).build();
        assertEquals("commands.demo.subcommand.user.list.argument.target",
                perms.buildArgumentPermission("demo", "user  list", plain));

        CommandArgument custom = CommandArgument.builder("target", String.class)
                .permissionNode("other")
                .includeArgumentSegment(false)
                .build();
        assertEquals("commands.demo.other", perms.buildArgumentPermission("demo", null, custom));
    }
}
