package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandUsageFormatterTest {

    private enum Mode { ON, OFF }

    private final CommandUsageFormatter<Object> formatter =
            new CommandUsageFormatter<>(new CommandDescriptionResolver(""), arg -> arg.getName().equals("sender"));

    @Test
    void formatsPositionalOptionalAndOptionArguments() {
        List<CommandArgument> arguments = List.of(
                CommandArgument.builder("sender", Object.class).build(),
                CommandArgument.builder("target", String.class).build(),
                CommandArgument.builder("mode", Mode.class).optional().build(),
                CommandArgument.builder("count", Integer.class).option(new String[]{"c"}, new String[]{"count"}).build(),
                CommandArgument.builder("force", Boolean.class).option(new String[]{"f"}, new String[0], true).build());

        assertEquals("<target> [on|off] --count <count> [-f]", formatter.formatArguments(arguments, false));
        assertEquals("[target] [on|off] [--count <count>] [-f]", formatter.formatArguments(arguments, true));
        assertEquals("", formatter.formatArguments(List.of(), false));
    }

    @Test
    void buildUsageEscapesMiniMessageTags() {
        String usage = formatter.buildUsage(Holder.class.getAnnotation(CommandInfo.class), "sub",
                List.of(CommandArgument.builder("target", String.class).build()));
        assertEquals("/demo sub \\<target\\>", usage);
    }

    @CommandInfo(name = "demo")
    private static final class Holder {
    }
}
