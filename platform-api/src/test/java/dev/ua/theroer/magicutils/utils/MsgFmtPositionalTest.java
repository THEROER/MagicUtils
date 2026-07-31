package dev.ua.theroer.magicutils.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the numeric-placeholder ({0}, {1}) positional path against the
 * key/value-pairs heuristic: two String args like ("create 'x'", "boom") must
 * fill {0}/{1} positionally, not be mistaken for a key→value map.
 */
class MsgFmtPositionalTest {

    @Test
    void numericPlaceholdersWithTwoStringArgsAreFilledPositionally() {
        String out = MsgFmt.apply("Failed to {0}: {1}", "create 'x'", "boom");
        assertEquals("Failed to create 'x': boom", out);
    }

    @Test
    void namedPlaceholdersWithTwoStringArgsAreFilledPositionally() {
        String out = MsgFmt.apply("{action}: {reason}", "create 'x'", "boom");
        assertEquals("create 'x': boom", out);
    }

    @Test
    void explicitKeyValueViaArgsStillWorks() {
        String out = MsgFmt.apply("{action}: {reason}",
                MsgFmt.args("action", "create 'x'", "reason", "boom"));
        assertEquals("create 'x': boom", out);
    }
}
