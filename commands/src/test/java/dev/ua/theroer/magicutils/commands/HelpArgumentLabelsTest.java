package dev.ua.theroer.magicutils.commands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpArgumentLabelsTest {

    @Test
    void labelsPositionalsOptionsAndFlags() {
        CommandArgument positional = CommandArgument.builder("target", String.class).build();
        CommandArgument valued = CommandArgument.builder("amount", Integer.class)
                .option(new String[] {"a"}, new String[] {"amount"}).build();
        CommandArgument flag = CommandArgument.builder("force", boolean.class)
                .option(new String[0], new String[] {"force"}, true).build();

        assertEquals("target", HelpArgumentLabels.argumentLabel(positional));
        assertEquals("--amount, -a <amount>", HelpArgumentLabels.argumentLabel(valued));
        assertEquals("--force", HelpArgumentLabels.argumentLabel(flag));
        assertEquals("", HelpArgumentLabels.argumentLabel(null));
    }

    @Test
    void enumValuesAreLowerCasedAndOtherTypesHaveNone() {
        CommandArgument mode = CommandArgument.builder("mode", Mode.class).build();
        CommandArgument text = CommandArgument.builder("text", String.class).build();

        assertEquals(List.of("fast", "safe"), HelpArgumentLabels.enumValues(mode));
        assertTrue(HelpArgumentLabels.enumValues(text).isEmpty());
        assertEquals("fast, safe", HelpArgumentLabels.joinValues(List.of("fast", "safe")));
        assertEquals("", HelpArgumentLabels.joinValues(List.of()));
    }

    @Test
    void styleUsageHighlightsOptionalSegments() {
        HelpStyling.HelpStyle style = HelpStyling.DEFAULT_STYLE;
        String open = HelpStyling.openTag(style.primaryTag());
        String close = HelpStyling.closeTag(style.primaryTag());

        assertEquals(HelpStyling.escapeMiniText("/give <item> ") + open + "[amount]" + close,
                HelpArgumentLabels.styleUsage("/give <item> [amount]", style));
        assertEquals("", HelpArgumentLabels.styleUsage("", style));
    }

    private enum Mode {
        FAST,
        SAFE
    }
}
