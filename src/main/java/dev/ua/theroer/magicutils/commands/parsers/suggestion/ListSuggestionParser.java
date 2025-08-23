package dev.ua.theroer.magicutils.commands.parsers.suggestion;

import java.util.Arrays;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import dev.ua.theroer.magicutils.commands.SuggestionParser;

/**
 * Parser for list-based suggestion sources like {on,off,toggle}.
 */
public class ListSuggestionParser implements SuggestionParser {
    
    /**
     * Creates a new ListSuggestionParser.
     */
    public ListSuggestionParser() {
        // Default constructor
    }

    @Override
    public boolean canParse(@NotNull String source) {
        return source.startsWith("{") && source.endsWith("}");
    }

    @Override
    @NotNull
    public List<String> parse(@NotNull String source, @NotNull CommandSender sender) {
        String content = source.substring(1, source.length() - 1);
        return Arrays.asList(content.split(",\\s*"));
    }

    @Override
    public int getPriority() {
        return 50;
    }
}