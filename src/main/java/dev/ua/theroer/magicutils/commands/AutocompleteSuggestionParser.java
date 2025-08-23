package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.SubLogger;
import dev.ua.theroer.magicutils.commands.parsers.suggestion.*;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Registry for managing suggestion parsers and providing autocomplete suggestions.
 */
public class AutocompleteSuggestionParser {
    private static final SubLogger logger = Logger.getSubLogger("Commands", "[Commands]");
    private final List<SuggestionParser> parsers = new ArrayList<>();

    /**
     * Default constructor for AutocompleteSuggestionParser.
     */
    public AutocompleteSuggestionParser() {
        registerDefaultParsers();
    }

    /**
     * Registers default parsers for common suggestion types.
     */
    private void registerDefaultParsers() {
        register(new SpecialSuggestionParser());
        register(new ListSuggestionParser());
        register(new LanguageKeySuggestionParser());
        
        logger.debug("Registered " + parsers.size() + " default suggestion parsers");
    }

    /**
     * Registers a new suggestion parser.
     * @param parser the parser to register
     */
    public void register(@NotNull SuggestionParser parser) {
        parsers.add(parser);
        // Sort by priority (highest first)
        parsers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        logger.debug("Registered suggestion parser: " + parser.getClass().getSimpleName());
    }

    /**
     * Parse special arguments and return list of suggestions
     * @param source suggestion source (for example, @players, @worlds, {on,off,toggle})
     * @param sender the command sender for context
     * @return list of all possible values
     */
    public @NotNull List<String> parse(String source, CommandSender sender) {
        logger.debug("Parsing suggestion source: '" + source + "'");
        
        for (SuggestionParser parser : parsers) {
            if (parser.canParse(source)) {
                logger.debug("Using suggestion parser: " + parser.getClass().getSimpleName());
                try {
                    List<String> result = parser.parse(source, sender);
                    if (result != null && !result.isEmpty()) {
                        logger.debug("Successfully parsed " + result.size() + " suggestions");
                        return result;
                    }
                } catch (Exception e) {
                    logger.debug("Suggestion parser " + parser.getClass().getSimpleName() + " failed: " + e.getMessage());
                }
            }
        }
        
        logger.debug("No suitable suggestion parser found for source: " + source);
        return Arrays.asList(source); // Fallback to original source
    }

    /**
     * Parse special arguments with filtering by current input
     * @param source suggestion source
     * @param currentInput current user input
     * @param sender the command sender for context
     * @return filtered list of suggestions
     */
    public @NotNull List<String> parseFiltered(String source, String currentInput, CommandSender sender) {
        logger.debug("Parsing filtered suggestion source: '" + source + "' with input: '" + currentInput + "'");
        
        for (SuggestionParser parser : parsers) {
            if (parser.canParse(source)) {
                logger.debug("Using suggestion parser: " + parser.getClass().getSimpleName());
                try {
                    List<String> result = parser.parseFiltered(source, currentInput, sender);
                    if (result != null) {
                        logger.debug("Successfully parsed " + result.size() + " filtered suggestions");
                        return result;
                    }
                } catch (Exception e) {
                    logger.debug("Suggestion parser " + parser.getClass().getSimpleName() + " failed: " + e.getMessage());
                }
            }
        }
        
        logger.debug("No suitable suggestion parser found for source: " + source);
        return Arrays.asList(""); // Return empty suggestion if nothing found
    }

    /**
     * Checks if a string is a special argument that can be parsed.
     * @param arg the argument string
     * @return true if any parser can handle this argument
     */
    public boolean isSpecialArgument(String arg) {
        return parsers.stream().anyMatch(parser -> parser.canParse(arg));
    }

    /**
     * Gets all registered parsers.
     * @return an unmodifiable list of parsers
     */
    public List<SuggestionParser> getParsers() {
        return Collections.unmodifiableList(parsers);
    }

    /**
     * Removes a parser from the registry.
     * @param parser the parser to remove
     * @return true if the parser was removed
     */
    public boolean unregister(@NotNull SuggestionParser parser) {
        boolean removed = parsers.remove(parser);
        if (removed) {
            logger.debug("Unregistered suggestion parser: " + parser.getClass().getSimpleName());
        }
        return removed;
    }

    /**
     * Removes all parsers of a specific class from the registry.
     * @param parserClass the class of parsers to remove
     * @return the number of parsers removed
     */
    public int unregisterByClass(@NotNull Class<? extends SuggestionParser> parserClass) {
        int count = 0;
        Iterator<SuggestionParser> iterator = parsers.iterator();
        while (iterator.hasNext()) {
            SuggestionParser parser = iterator.next();
            if (parserClass.isInstance(parser)) {
                iterator.remove();
                count++;
                logger.debug("Unregistered suggestion parser: " + parser.getClass().getSimpleName());
            }
        }
        return count;
    }
}