package dev.ua.theroer.magicutils.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Interface for parsing suggestion sources and providing autocomplete suggestions.
 */
public interface SuggestionParser {
    
    /**
     * Checks if this parser can handle the given suggestion source.
     * @param source the suggestion source to check (e.g., "@players", "{on,off,toggle}")
     * @return true if this parser can handle the source
     */
    boolean canParse(@NotNull String source);
    
    /**
     * Parses the suggestion source and returns all possible suggestions.
     * @param source the suggestion source
     * @param sender the command sender for context
     * @return list of all possible suggestions
     */
    @NotNull
    List<String> parse(@NotNull String source, @NotNull CommandSender sender);
    
    /**
     * Parses the suggestion source and returns filtered suggestions.
     * @param source the suggestion source
     * @param currentInput the current user input for filtering
     * @param sender the command sender for context
     * @return filtered list of suggestions
     */
    @NotNull
    default List<String> parseFiltered(@NotNull String source, @NotNull String currentInput, @NotNull CommandSender sender) {
        List<String> allSuggestions = parse(source, sender);
        
        if (currentInput == null || currentInput.isEmpty()) {
            return allSuggestions;
        }
        
        return allSuggestions.stream()
                .filter(suggestion -> suggestion.toLowerCase().startsWith(currentInput.toLowerCase()))
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Gets the priority of this parser. Higher priority parsers are checked first.
     * @return the priority value
     */
    default int getPriority() {
        return 0;
    }
}