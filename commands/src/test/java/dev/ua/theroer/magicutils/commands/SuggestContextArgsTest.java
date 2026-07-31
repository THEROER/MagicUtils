package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.Greedy;
import dev.ua.theroer.magicutils.annotations.ParamName;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import dev.ua.theroer.magicutils.annotations.Suggest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contract of {@link Suggest#contextArgs()}: a provider must receive the earlier
 * argument's parsed value, and must stay reachable whether or not the user has
 * started typing the current word.
 */
class SuggestContextArgsTest {

    /**
     * A provider overloaded with a no-arg form must still get its context argument:
     * the declaration asks for {@code contextArgs}, so honouring the no-arg overload
     * instead silently drops the context and suggests the wrong thing.
     */
    @Test
    void contextAwareProviderIsPreferredOverNoArgOverload() {
        CommandManager<TestSender> manager = commandManager();
        AuthorsCarrier carrier = new AuthorsCarrier();
        manager.register(carrier, carrier.resolveInfo());

        List<String> s = manager.getSuggestions("maps", new TestSender("t"),
                List.of("remove", "alpha", ""));

        assertEquals(List.of("ann", "bob"), s,
                "context-aware overload was skipped in favour of the no-arg one");
    }

    /** The same provider must be reached once the user has typed a prefix. */
    @Test
    void contextAwareProviderReceivesCurrentInput() {
        CommandManager<TestSender> manager = commandManager();
        AuthorsCarrier carrier = new AuthorsCarrier();
        manager.register(carrier, carrier.resolveInfo());

        List<String> s = manager.getSuggestions("maps", new TestSender("t"),
                List.of("remove", "alpha", "b"));

        assertEquals(List.of("bob"), s);
    }

    /**
     * A provider that declares {@code currentInput} must also be called when nothing
     * has been typed yet — tab on a bare argument is the most common case, and an
     * author should not have to write a second overload just to serve it.
     */
    @Test
    void providerWithCurrentInputIsCalledOnEmptyInput() {
        CommandManager<TestSender> manager = commandManager();
        AuthorsCarrier carrier = new AuthorsCarrier();
        manager.register(carrier, carrier.resolveInfo());

        List<String> s = manager.getSuggestions("maps", new TestSender("t"),
                List.of("pick", ""));

        assertEquals(List.of("one", "two"), s,
                "provider taking (currentInput) was not called with an empty input");
    }

    @CommandInfo(name = "maps")
    private static final class AuthorsCarrier extends MagicCommand {

        @SubCommand(name = "remove")
        @SuppressWarnings("unused")
        public CommandResult remove(
                @ParamName("id") String id,
                @ParamName("names") @Suggest(value = "authorsOf", contextArgs = "id")
                @Greedy String names) {
            return CommandResult.success("removed");
        }

        @SubCommand(name = "pick")
        @SuppressWarnings("unused")
        public CommandResult pick(
                @ParamName("name") @Suggest("filtered") String name) {
            return CommandResult.success("picked");
        }

        /** The overload the manager must NOT prefer when contextArgs are declared. */
        @SuppressWarnings("unused")
        public List<String> authorsOf() {
            return List.of("no-context");
        }

        @SuppressWarnings("unused")
        public List<String> authorsOf(String id) {
            return "alpha".equals(id) ? List.of("ann", "bob") : List.of();
        }

        @SuppressWarnings("unused")
        public List<String> authorsOf(String id, String currentInput) {
            List<String> all = authorsOf(id);
            return all.stream().filter(a -> a.startsWith(currentInput)).toList();
        }

        @SuppressWarnings("unused")
        public List<String> filtered(String currentInput) {
            return List.of("one", "two").stream()
                    .filter(v -> v.startsWith(currentInput)).toList();
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
