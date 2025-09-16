package dev.ua.theroer.magicutils.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Unified interface for parsing command arguments and providing suggestions.
 * Combines functionality of ArgumentTypeParser and SuggestionParser.
 * 
 * @param <T> the type that this parser handles
 */
public interface TypeParser<T> {

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
    T parse(@Nullable String value, @NotNull Class<T> targetType, @NotNull CommandSender sender);

    /**
     * Gets suggestions for this type.
     * 
     * @param sender the command sender for context
     * @return list of all possible suggestions
     */
    @NotNull
    List<String> getSuggestions(@NotNull CommandSender sender);

    /**
     * Gets filtered suggestions based on current input.
     * 
     * @param currentInput the current user input for filtering
     * @param sender       the command sender for context
     * @return filtered list of suggestions
     */
    @NotNull
    default List<String> getSuggestionsFiltered(@NotNull String currentInput, @NotNull CommandSender sender) {
        if (currentInput == null || currentInput.isEmpty()) {
            return getSuggestions(sender);
        }

        return getSuggestions(sender).stream()
                .filter(suggestion -> suggestion.toLowerCase().startsWith(currentInput.toLowerCase()))
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
    default List<String> parseSuggestion(@NotNull String source, @NotNull CommandSender sender) {
        return getSuggestions(sender);
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