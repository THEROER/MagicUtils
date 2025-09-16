package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.bukkit.command.CommandSender;
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
 */
public class ListTypeParser implements TypeParser<String> {
    private static final Pattern LIST_PATTERN = Pattern.compile("^\\{([^}]+)\\}$");

    /**
     * Default constructor for ListTypeParser.
     */
    public ListTypeParser() {
    }

    /**
     * Checks if this parser can parse the specified type.
     * 
     * @param type the type to check
     * @return true if this parser can parse the type, false otherwise
     */
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        // This parser is only for suggestions, not parsing
        return false;
    }

    @Override
    @Nullable
    public String parse(@Nullable String value, @NotNull Class<String> targetType, @NotNull CommandSender sender) {
        // This parser doesn't parse values
        return null;
    }

    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSender sender) {
        // No default suggestions
        return new ArrayList<>();
    }

    @Override
    public boolean canParseSuggestion(@NotNull String source) {
        return LIST_PATTERN.matcher(source).matches();
    }

    @Override
    @NotNull
    public List<String> parseSuggestion(@NotNull String source, @NotNull CommandSender sender) {
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