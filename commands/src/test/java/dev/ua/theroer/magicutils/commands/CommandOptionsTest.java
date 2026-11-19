package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.commands.CommandOptions.OptionIndex;
import dev.ua.theroer.magicutils.commands.CommandOptions.OptionToken;
import dev.ua.theroer.magicutils.commands.CommandOptions.ParsedOptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandOptionsTest {

    private final CommandArgument verbose = CommandArgument.builder("verbose", Boolean.class)
            .option(new String[]{"v"}, new String[]{"verbose"}, true)
            .build();
    private final CommandArgument count = CommandArgument.builder("count", Integer.class)
            .option(new String[]{"c"}, new String[]{"count"})
            .build();
    private final CommandArgument target = CommandArgument.builder("target", String.class).build();

    private OptionIndex index() {
        return CommandOptions.buildOptionIndex(List.of(verbose, count, target), arg -> false);
    }

    @Test
    void indexSkipsPositionalAndSenderArguments() {
        OptionIndex index = CommandOptions.buildOptionIndex(List.of(verbose, count, target), arg -> arg == count);
        assertEquals(List.of(verbose), index.optionArguments());
        assertTrue(index.hasOptions());
        assertFalse(CommandOptions.buildOptionIndex(List.of(target), arg -> false).hasOptions());
    }

    @Test
    void parsesFlagsInlineAndSeparateValues() {
        ParsedOptions parsed = CommandOptions.parseOptions(List.of("alex", "-V", "--count=3"), index(), false);
        assertEquals(List.of("alex"), parsed.positionals());
        assertEquals("true", parsed.values().get(verbose));
        assertEquals("3", parsed.values().get(count));

        parsed = CommandOptions.parseOptions(List.of("-c", "7", "bob"), index(), false);
        assertEquals("7", parsed.values().get(count));
        assertEquals(List.of("bob"), parsed.positionals());
    }

    @Test
    void doubleDashTerminatesOptions() {
        ParsedOptions parsed = CommandOptions.parseOptions(List.of("--", "-v"), index(), false);
        assertTrue(parsed.optionsTerminated());
        assertEquals(List.of("-v"), parsed.positionals());
        assertTrue(parsed.values().isEmpty());
    }

    @Test
    void missingValueFailsUnlessPendingAllowed() {
        assertNull(CommandOptions.parseOptions(List.of("--count"), index(), false));
        ParsedOptions pending = CommandOptions.parseOptions(List.of("--count"), index(), true);
        assertSame(count, pending.pendingValue());
    }

    @Test
    void negativeNumbersAreNotOptions() {
        assertNull(CommandOptions.parseOptionToken("-5"));
        assertNull(CommandOptions.parseOptionToken("--.5"));
        assertFalse(CommandOptions.isOptionPrefix("-1"));
        assertTrue(CommandOptions.isOptionPrefix("-"));
        assertTrue(CommandOptions.isOptionPrefix("--ver"));
        OptionToken token = CommandOptions.parseOptionToken("--Key=v=1");
        assertEquals("key", token.keyLower());
        assertEquals("v=1", token.value());
        assertTrue(token.longForm());
    }

    @Test
    void normalizesOptionNames() {
        assertEquals("name", CommandOptions.normalizeOptionName("  --name "));
        assertEquals("", CommandOptions.normalizeOptionName(null));
    }
}
