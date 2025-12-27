package dev.ua.theroer.magicutils.commands;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Unified interface for parsing command arguments and providing suggestions.
 * Combines functionality of ArgumentTypeParser and SuggestionParser.
 *
 * @param <S> sender type
 * @param <T> the type that this parser handles
 */
public interface TypeParser<S, T> {

    /**
     * Checks if this parser can handle the given type.
     *
     * @param type the class type to check
     * @return true if this parser can handle the type
     */
    boolean canParse(@NotNull Class<?> type);

    /**
     * Parses the given value to the target type.
     *
     * @param value      the string value to parse
     * @param targetType the target class type
     * @param sender     the command sender (for context like @sender)
     * @return the parsed object or null if parsing failed
     */
    @Nullable
    T parse(@Nullable String value, @NotNull Class<T> targetType, @NotNull S sender);

    /**
     * Gets suggestions for this type.
     *
     * @param sender the command sender for context
     * @return list of all possible suggestions
     */
    @NotNull
    List<String> getSuggestions(@NotNull S sender);

    /**
     * Gets suggestions for this type with access to the argument metadata.
     *
     * @param sender   the command sender for context
     * @param argument the command argument (may be null)
     * @return list of suggestions
     */
    @NotNull
    default List<String> getSuggestions(@NotNull S sender, @Nullable CommandArgument argument) {
        return getSuggestions(sender);
    }

    /**
     * Gets filtered suggestions based on current input.
     *
     * @param currentInput the current user input for filtering
     * @param sender       the command sender for context
     * @param argument     the command argument for context
     * @return filtered list of suggestions
     */
    @NotNull
    default List<String> getSuggestionsFiltered(@Nullable String currentInput, @NotNull S sender,
            @Nullable CommandArgument argument) {
        List<String> base = getSuggestions(sender, argument);
        if (currentInput == null || currentInput.isEmpty()) {
            return base;
        }

        final String lowered = currentInput.toLowerCase();
        return base.stream()
                .filter(suggestion -> suggestion != null && suggestion.toLowerCase().startsWith(lowered))
                .collect(Collectors.toList());
    }

    /**
     * Checks if this parser can handle a special suggestion source.
     * This allows parsers to handle special syntax like @players, @worlds, etc.
     *
     * @param source the suggestion source to check
     * @return true if this parser can handle the source
     */
    default boolean canParseSuggestion(@NotNull String source) {
        return false;
    }

    /**
     * Parses a special suggestion source and returns suggestions.
     *
     * @param source the suggestion source
     * @param sender the command sender for context
     * @return list of suggestions from the source
     */
    @NotNull
    default List<String> parseSuggestion(@NotNull String source, @NotNull S sender) {
        return getSuggestions(sender);
    }

    /**
     * Compare two values under a given mode (parsers may override).
     *
     * @param sender command sender
     * @param first first value
     * @param second second value
     * @param mode compare strategy
     * @return true if equal under mode
     */
    default boolean isEqual(@NotNull S sender, @Nullable Object first, @Nullable Object second,
            @NotNull CompareMode mode) {
        return ComparisonUtils.isEqual(first, second, mode);
    }

    /**
     * Check if value refers to the sender (parsers may override).
     *
     * @param sender command sender
     * @param value candidate value
     * @param mode compare strategy
     * @return true if represents sender
     */
    default boolean isSender(@NotNull S sender, @Nullable Object value, @NotNull CompareMode mode) {
        return ComparisonUtils.isSender(sender, value, mode);
    }

    /**
     * Gets the priority of this parser. Higher priority parsers are checked first.
     *
     * @return the priority value
     */
    default int getPriority() {
        return 0;
    }
}
