package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Type parser for numeric arguments (int/long/double/float/short/byte/BigInteger/BigDecimal).
 *
 * @param <S> sender type
 */
public class NumberTypeParser<S> implements TypeParser<S, Number> {
    private static final String NUMBER_SUGGESTION = "@number";
    private static final List<String> DEFAULT_SUGGESTIONS = buildDefaultSuggestions();

    /** Default constructor. */
    public NumberTypeParser() {
    }

    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == Integer.class || type == int.class
                || type == Long.class || type == long.class
                || type == Double.class || type == double.class
                || type == Float.class || type == float.class
                || type == Short.class || type == short.class
                || type == Byte.class || type == byte.class
                || type == BigInteger.class
                || type == BigDecimal.class;
    }

    @Override
    @Nullable
    public Number parse(@Nullable String value, @NotNull Class<Number> targetType, @NotNull S sender) {
        if (value == null) {
            return null;
        }
        Class<?> rawType = targetType;
        try {
            if (rawType == Integer.class || rawType == int.class) {
                return Integer.parseInt(value);
            }
            if (rawType == Long.class || rawType == long.class) {
                return Long.parseLong(value);
            }
            if (rawType == Double.class || rawType == double.class) {
                return Double.parseDouble(value);
            }
            if (rawType == Float.class || rawType == float.class) {
                return Float.parseFloat(value);
            }
            if (rawType == Short.class || rawType == short.class) {
                return Short.parseShort(value);
            }
            if (rawType == Byte.class || rawType == byte.class) {
                return Byte.parseByte(value);
            }
            if (rawType == BigInteger.class) {
                return new BigInteger(value);
            }
            if (rawType == BigDecimal.class) {
                return new BigDecimal(value);
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull S sender) {
        return DEFAULT_SUGGESTIONS;
    }

    @Override
    public boolean canParseSuggestion(@NotNull String source) {
        return NUMBER_SUGGESTION.equalsIgnoreCase(source);
    }

    @Override
    @NotNull
    public List<String> parseSuggestion(@NotNull String source, @NotNull S sender) {
        if (canParseSuggestion(source)) {
            return DEFAULT_SUGGESTIONS;
        }
        return List.of();
    }

    private static List<String> buildDefaultSuggestions() {
        List<String> values = new ArrayList<>();
        for (int i = 0; i <= 100; i += 5) {
            values.add(Integer.toString(i));
        }
        return Collections.unmodifiableList(values);
    }
}
