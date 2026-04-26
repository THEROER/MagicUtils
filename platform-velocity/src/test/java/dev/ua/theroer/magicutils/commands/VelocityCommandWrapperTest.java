package dev.ua.theroer.magicutils.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.permission.Tristate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityCommandWrapperTest {

    @Test
    void hasPermissionUsesNormalizedAliasAndFirstArgument() {
        RecordingCommandManager manager = new RecordingCommandManager();
        VelocityCommandWrapper wrapper = new VelocityCommandWrapper(manager, "fallback", Runnable::run);
        CommandSource source = commandSource();

        boolean allowed = wrapper.hasPermission(new TestInvocation(source, new String[]{"reload", "target"}, "AdMiN"));

        assertTrue(allowed);
        assertEquals("admin", manager.lastAccessName);
        assertEquals("reload", manager.lastAccessTargetSubName);
        assertSame(source, manager.lastAccessSender);
    }

    @Test
    void suggestReturnsEmptyWhenPermissionIsDenied() {
        RecordingCommandManager manager = new RecordingCommandManager();
        manager.canAccess = false;
        manager.suggestions = List.of("reload");
        VelocityCommandWrapper wrapper = new VelocityCommandWrapper(manager, "fallback", Runnable::run);

        List<String> suggestions = wrapper.suggest(new TestInvocation(commandSource(), new String[]{"re"}, "Demo"));

        assertTrue(suggestions.isEmpty());
        assertEquals(0, manager.suggestionCalls);
    }

    @Test
    void suggestDelegatesToCommandManagerWhenPermissionIsGranted() {
        RecordingCommandManager manager = new RecordingCommandManager();
        manager.suggestions = List.of("reload", "reset");
        VelocityCommandWrapper wrapper = new VelocityCommandWrapper(manager, "fallback", Runnable::run);
        CommandSource source = commandSource();

        List<String> suggestions = wrapper.suggest(new TestInvocation(source, new String[]{"re"}, "DeMo"));

        assertIterableEquals(List.of("reload", "reset"), suggestions);
        assertEquals("demo", manager.lastSuggestionName);
        assertIterableEquals(List.of("re"), manager.lastSuggestionArgs);
        assertSame(source, manager.lastSuggestionSender);
    }

    @Test
    void suggestFallsBackToCommandLabelWhenAliasIsBlank() {
        RecordingCommandManager manager = new RecordingCommandManager();
        VelocityCommandWrapper wrapper = new VelocityCommandWrapper(manager, "fallback", Runnable::run);

        wrapper.suggest(new TestInvocation(commandSource(), new String[0], " "));

        assertEquals("fallback", manager.lastSuggestionName);
    }

    private static CommandSource commandSource() {
        return (CommandSource) Proxy.newProxyInstance(
                CommandSource.class.getClassLoader(),
                new Class<?>[]{CommandSource.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "TestCommandSource";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if ("getPermissionValue".equals(method.getName())) {
                        return Tristate.UNDEFINED;
                    }
                    if ("hasPermission".equals(method.getName())) {
                        return false;
                    }
                    return null;
                }
        );
    }

    private record TestInvocation(CommandSource source, String[] arguments, String alias)
            implements SimpleCommand.Invocation {
    }

    private static final class RecordingCommandManager extends CommandManager<CommandSource> {
        private boolean canAccess = true;
        private List<String> suggestions = List.of();
        private String lastAccessName;
        private String lastAccessTargetSubName;
        private CommandSource lastAccessSender;
        private String lastSuggestionName;
        private List<String> lastSuggestionArgs = List.of();
        private CommandSource lastSuggestionSender;
        private int suggestionCalls;

        private RecordingCommandManager() {
            super(
                    "",
                    "",
                    CommandLogger.noop(),
                    new NoopCommandPlatform(),
                    TypeParserRegistry.createWithDefaults(CommandLogger.noop())
            );
        }

        @Override
        public boolean canAccessCommand(String name, CommandSource sender, String targetSubName) {
            this.lastAccessName = name;
            this.lastAccessTargetSubName = targetSubName;
            this.lastAccessSender = sender;
            return canAccess;
        }

        @Override
        public List<String> getSuggestions(String name, CommandSource sender, List<String> args) {
            this.lastSuggestionName = name;
            this.lastSuggestionArgs = List.copyOf(args);
            this.lastSuggestionSender = sender;
            this.suggestionCalls++;
            return suggestions;
        }
    }

    private static final class NoopCommandPlatform implements CommandPlatform<CommandSource> {
        @Override
        public Class<?> senderType() {
            return CommandSource.class;
        }

        @Override
        public String getName(CommandSource sender) {
            return "source";
        }

        @Override
        public boolean hasPermission(CommandSource sender, String permission, MagicPermissionDefault defaultValue) {
            return false;
        }

        @Override
        public void ensurePermissionRegistered(String node, MagicPermissionDefault defaultValue, String description) {
        }

        @Override
        public Object resolveSenderArgument(CommandSource sender, CommandArgument argument) {
            return sender;
        }
    }
}
