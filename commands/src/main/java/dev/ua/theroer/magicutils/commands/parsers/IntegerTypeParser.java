package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Type parser for Integer arguments.
 *
 * @param <S> sender type
 */
public class IntegerTypeParser<S> implements TypeParser<S, Integer> {

    /** Default constructor. */
    public IntegerTypeParser() {
    }

    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == Integer.class || type == int.class;
    }

    @Override
    @Nullable
    public Integer parse(@Nullable String value, @NotNull Class<Integer> targetType, @NotNull S sender) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
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
