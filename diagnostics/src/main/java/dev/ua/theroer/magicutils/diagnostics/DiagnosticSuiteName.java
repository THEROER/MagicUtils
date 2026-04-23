package dev.ua.theroer.magicutils.diagnostics;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Typed diagnostics suite argument used by command parsers and suggestions.
 *
 * @param value normalized suite name
 */
public record DiagnosticSuiteName(String value) {
    private static final List<String> BUILTIN_VALUES = List.of(
            "magicutils.runtime",
            "magicutils.filesystem",
            "magicutils.config",
            "magicutils.scheduler",
            "magicutils.threading",
            "magicutils.commands",
            "magicutils.placeholders"
    );

    public DiagnosticSuiteName {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    @NotNull
    public static List<String> builtinValues() {
        return BUILTIN_VALUES;
    }

    @Override
    public String toString() {
        return value;
    }
}
