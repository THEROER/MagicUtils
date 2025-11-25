package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Type parser for String arguments.
 */
public class StringTypeParser implements TypeParser<String> {

    /**
     * Default constructor for StringTypeParser.
     */
    public StringTypeParser() {
    }

    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == String.class;
    }

    /**
     * Parses the given value into a String object.
     * 
     * @param value      the value to parse
     * @param targetType the target type
     * @param sender     the command sender
     * @return the parsed String object, or null if the value is null
     */
    @Override
    @Nullable
    public String parse(@Nullable String value, @NotNull Class<String> targetType, @NotNull CommandSender sender) {
        return value;
    }

    /**
     * Gets suggestions for the String type.
     * 
     * @param sender the command sender
     * @return a list of suggestions
     */
    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSender sender) {
        // Default empty suggestions for strings
        return new ArrayList<>();
    }

    /**
     * Gets the priority of the parser.
     * 
     * @return the priority
     */
    @Override
    public int getPriority() {
        return -100; // Lowest priority as fallback
    }
}