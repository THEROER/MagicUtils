package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Type parser for String arguments.
 */
public class StringTypeParser<S> implements TypeParser<S, String> {

    /** Default constructor. */
    public StringTypeParser() {
    }

    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == String.class;
    }

    @Override
    @Nullable
    public String parse(@Nullable String value, @NotNull Class<String> targetType, @NotNull S sender) {
        return value;
    }

    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull S sender) {
        return new ArrayList<>();
    }

    @Override
    public int getPriority() {
        return -100; // Lowest priority as fallback
    }
}
