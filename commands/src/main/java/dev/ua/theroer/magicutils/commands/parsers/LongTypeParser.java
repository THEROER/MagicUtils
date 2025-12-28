package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Type parser for Long arguments.
 *
 * @param <S> sender type
 */
public class LongTypeParser<S> implements TypeParser<S, Long> {

    /** Default constructor. */
    public LongTypeParser() {
    }

    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == Long.class || type == long.class;
    }

    @Override
    @Nullable
    public Long parse(@Nullable String value, @NotNull Class<Long> targetType, @NotNull S sender) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull S sender) {
        return new ArrayList<>();
    }
}
