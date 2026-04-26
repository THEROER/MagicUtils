package dev.ua.theroer.magicutils.diagnostics;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Parser for diagnostics suite command arguments.
 *
 * @param <S> sender type
 */
public final class DiagnosticSuiteNameTypeParser<S> implements TypeParser<S, DiagnosticSuiteName> {
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == DiagnosticSuiteName.class;
    }

    @Override
    @Nullable
    public DiagnosticSuiteName parse(
            @Nullable String value,
            @NotNull Class<DiagnosticSuiteName> targetType,
            @NotNull S sender
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new DiagnosticSuiteName(value);
    }

    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull S sender) {
        return DiagnosticSuiteName.builtinValues();
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
