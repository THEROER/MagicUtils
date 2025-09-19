package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.CommandArgument;
import dev.ua.theroer.magicutils.commands.TypeParser;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Type parser for Boolean arguments.
 */
public class BooleanTypeParser implements TypeParser<Boolean> {

    /**
     * Default constructor for BooleanTypeParser.
     */
    public BooleanTypeParser() {
    }

    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == Boolean.class || type == boolean.class;
    }

    /**
     * Parses the given value into a Boolean object.
     * 
     * @param value      the value to parse
     * @param targetType the target type
     * @param sender     the command sender
     * @return the parsed Boolean object, or null if the value is null
     */
    @Override
    @Nullable
    public Boolean parse(@Nullable String value, @NotNull Class<Boolean> targetType, @NotNull CommandSender sender) {
        if (value == null) {
            return null;
        }

        String lower = value.toLowerCase();
        if ("true".equals(lower) || "yes".equals(lower) || "on".equals(lower) || "1".equals(lower)) {
            return true;
        }
        if ("false".equals(lower) || "no".equals(lower) || "off".equals(lower) || "0".equals(lower)) {
            return false;
        }

        return null;
    }

    /**
     * Gets suggestions for the Boolean type.
     * 
     * @param sender the command sender
     * @return a list of suggestions
     */
    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSender sender, @Nullable CommandArgument argument) {
        List<String> base = new ArrayList<>(Arrays.asList("true", "false"));

        if (argument != null) {
            String defaultValue = argument.getDefaultValue();
            if (defaultValue != null) {
                String normalized = normalize(defaultValue);
                if (normalized != null) {
                    base.remove(normalized);
                    base.add(0, normalized);
                }
            }
        }

        return base;
    }

    @Override
    public List<String> getSuggestions(@NotNull CommandSender sender) {
        return getSuggestions(sender, null);
    }

    private String normalize(String value) {
        String lower = value.toLowerCase();
        if ("true".equals(lower) || "yes".equals(lower) || "on".equals(lower) || "1".equals(lower)) {
            return "true";
        }
        if ("false".equals(lower) || "no".equals(lower) || "off".equals(lower) || "0".equals(lower)) {
            return "false";
        }
        return null;
    }

    /**
     * Checks if the parser can parse the given suggestion.
     * 
     * @param source the suggestion to check
     * @return true if the parser can parse the suggestion, false otherwise
     */
    @Override
    public boolean canParseSuggestion(@NotNull String source) {
        return "{true,false}".equals(source) ||
                "{yes,no}".equals(source) ||
                "{on,off}".equals(source) ||
                "{on,off,toggle}".equals(source);
    }

    /**
     * Parses the given suggestion into a list of suggestions.
     * 
     * @param source   the suggestion to parse
     * @param sender   the command sender
     * @return a list of suggestions
     */
    @Override
    @NotNull
    public List<String> parseSuggestion(@NotNull String source, @NotNull CommandSender sender) {
        switch (source) {
            case "{true,false}":
                return Arrays.asList("true", "false");
            case "{yes,no}":
                return Arrays.asList("yes", "no");
            case "{on,off}":
                return Arrays.asList("on", "off");
            case "{on,off,toggle}":
                return Arrays.asList("on", "off", "toggle");
            default:
                return getSuggestions(sender, null);
        }
    }

    /**
     * Gets the priority of the parser.
     * 
     * @return the priority
     */
    @Override
    public int getPriority() {
        return 10;
    }
}
