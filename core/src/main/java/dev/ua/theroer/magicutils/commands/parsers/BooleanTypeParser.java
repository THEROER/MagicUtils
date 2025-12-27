package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Type parser for Boolean arguments.
 */
public class BooleanTypeParser<S> implements TypeParser<S, Boolean> {

    /** Default constructor. */
    public BooleanTypeParser() {
    }

    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == Boolean.class || type == boolean.class;
    }

    @Override
    @Nullable
    public Boolean parse(@Nullable String value, @NotNull Class<Boolean> targetType, @NotNull S sender) {
        if (value == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return null;
    }

    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull S sender) {
        return Arrays.asList("true", "false");
    }
}
