package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Type parser for handling list suggestions like {value1,value2,value3}.
 * This parser doesn't parse values but provides suggestions from list syntax.
 *
 * @param <S> sender type
 */
public class ListTypeParser<S> implements TypeParser<S, String> {
    private static final Pattern LIST_PATTERN = Pattern.compile("^\\{([^}]+)\\}$");

    /** Default constructor. */
    public ListTypeParser() {
    }

    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return false;
    }

    @Override
    @Nullable
    public String parse(@Nullable String value, @NotNull Class<String> targetType, @NotNull S sender) {
        return null;
    }

    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull S sender) {
        return new ArrayList<>();
    }

    @Override
    public boolean canParseSuggestion(@NotNull String source) {
        return LIST_PATTERN.matcher(source).matches();
    }

    @Override
    @NotNull
    public List<String> parseSuggestion(@NotNull String source, @NotNull S sender) {
        Matcher matcher = LIST_PATTERN.matcher(source);
        if (matcher.matches()) {
            String listContent = matcher.group(1);
            return Arrays.asList(listContent.split(","));
        }
        return new ArrayList<>();
    }

    @Override
    public int getPriority() {
        return 80; // High priority for explicit lists
    }
}
