package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.logger.PrefixedLogger;
import dev.ua.theroer.magicutils.logger.PrefixedLoggerGen;
import dev.ua.theroer.magicutils.commands.parsers.*;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Registry for managing type parsers that handle both parsing and suggestions.
 */
public class TypeParserRegistry {
    private static final PrefixedLogger logger = Logger.create("Commands", "[Commands]");
    private final List<TypeParser<?>> parsers = new ArrayList<>();

    /**
     * Create a new empty TypeParserRegistry; prefer {@link #createWithDefaults()}.
     */
    private TypeParserRegistry() {
    }

    /**
     * Factory to register built-in parsers after construction.
     *
     * @return registry preloaded with default parsers
     */
    public static TypeParserRegistry createWithDefaults() {
        TypeParserRegistry registry = new TypeParserRegistry();
        registry.registerDefaultParsers();
        return registry;
    }

    /**
     * Registers default parsers for common types.
     */
    private void registerDefaultParsers() {
        // Entity parsers
        register(new PlayerTypeParser());
        register(new OfflinePlayerTypeParser());
        register(new WorldTypeParser());

        // Primitive type parsers
        register(new StringTypeParser());
        register(new IntegerTypeParser());
        register(new LongTypeParser());
        register(new BooleanTypeParser());
        register(new EnumTypeParser());

        // Special parsers
        register(new ListTypeParser());
        register(new LanguageKeyTypeParser());

        PrefixedLoggerGen.debug(logger, "Registered " + parsers.size() + " default type parsers");
    }

    /**
     * Registers a new type parser.
     * 
     * @param parser the parser to register
     */
    public void register(@NotNull TypeParser<?> parser) {
        parsers.add(parser);
        // Sort by priority (highest first)
        parsers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        PrefixedLoggerGen.debug(logger, "Registered type parser: " + parser.getClass().getSimpleName());
    }

    /**
     * Registers a list of type parsers.
     * 
     * @param parsers the list of parsers to register
     */
    public void registerAll(@NotNull List<TypeParser<?>> parsers) {
        parsers.forEach(this::register);
    }

    /**
     * Parses a value to the specified type using registered parsers.
     * 
     * @param <T>        the type to parse to
     * @param value      the string value to parse
     * @param targetType the target class type
     * @param sender     the command sender for context
     * @return the parsed object or null if no suitable parser found
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T parse(@Nullable String value, @NotNull Class<T> targetType, @NotNull CommandSender sender) {
        PrefixedLoggerGen.debug(logger, "Parsing value: '" + value + "' to type: " + targetType.getSimpleName());

        for (TypeParser<?> parser : parsers) {
            if (parser.canParse(targetType)) {
                PrefixedLoggerGen.debug(logger, "Using parser: " + parser.getClass().getSimpleName());
                try {
                    @SuppressWarnings("rawtypes")
                    Object result = parser.parse(value, (Class) targetType, sender);
                    if (result != null || value == null) {
                        PrefixedLoggerGen.debug(logger, "Successfully parsed to: "
                                + (result != null ? result.getClass().getSimpleName() + "=" + result : "null"));
                        return (T) result;
                    }
                } catch (Exception e) {
                    PrefixedLoggerGen.debug(logger,
                            "Parser " + parser.getClass().getSimpleName() + " failed: " + e.getMessage());
                }
            }
        }

        PrefixedLoggerGen.debug(logger, "No suitable parser found for type: " + targetType.getSimpleName());
        return null;
    }

    /**
     * Gets suggestions for a specific type.
     * 
     * @param targetType the target class type
     * @param sender     the command sender for context
     * @return list of suggestions
     */
    @NotNull
    public List<String> getSuggestionsForType(@NotNull Class<?> targetType, @NotNull CommandSender sender) {
        return getSuggestionsInternal(targetType, sender, null);
    }

    /**
     * Gets suggestions for the provided argument.
     *
     * @param argument the command argument metadata
     * @param sender   the command sender for context
     * @return list of suggestions
     */
    @NotNull
    public List<String> getSuggestionsForArgument(@NotNull CommandArgument argument, @NotNull CommandSender sender) {
        return getSuggestionsInternal(argument.getType(), sender, argument);
    }

    /**
     * Parses suggestions from a special source (like @players, {on,off}, etc).
     * 
     * @param source the suggestion source
     * @param sender the command sender for context
     * @return list of suggestions
     */
    @NotNull
    public List<String> parseSuggestion(@NotNull String source, @NotNull CommandSender sender) {
        PrefixedLoggerGen.debug(logger, "Parsing suggestion source: '" + source + "'");

        for (TypeParser<?> parser : parsers) {
            if (parser.canParseSuggestion(source)) {
                PrefixedLoggerGen.debug(logger, "Using parser for suggestion: " + parser.getClass().getSimpleName());
                try {
                    List<String> result = parser.parseSuggestion(source, sender);
                    if (!result.isEmpty()) {
                        PrefixedLoggerGen.debug(logger, "Successfully parsed " + result.size() + " suggestions");
                        return result;
                    }
                } catch (Exception e) {
                    PrefixedLoggerGen.debug(logger,
                            "Parser " + parser.getClass().getSimpleName() + " failed: " + e.getMessage());
                }
            }
        }

        PrefixedLoggerGen.debug(logger, "No suitable parser found for suggestion source: " + source);
        if ("@sender".equalsIgnoreCase(source)) {
            return new ArrayList<>();
        }
        return Arrays.asList(source); // Fallback to original source
    }

    /**
     * Parses suggestions with filtering by current input.
     * 
     * @param source       the suggestion source
     * @param currentInput current user input
     * @param sender       the command sender for context
     * @return filtered list of suggestions
     */
    @NotNull
    public List<String> parseSuggestionFiltered(@NotNull String source, @NotNull String currentInput,
            @NotNull CommandSender sender) {
        PrefixedLoggerGen.debug(logger,
                "Parsing filtered suggestion source: '" + source + "' with input: '" + currentInput + "'");

        List<String> suggestions = parseSuggestion(source, sender);

        if (currentInput == null || currentInput.isEmpty()) {
            return suggestions;
        }

        return suggestions.stream()
                .filter(suggestion -> suggestion.toLowerCase().startsWith(currentInput.toLowerCase()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Gets filtered suggestions for a specific type.
     * 
     * @param targetType   the target class type
     * @param currentInput current user input
     * @param sender       the command sender for context
     * @return filtered list of suggestions
     */
    @NotNull
    public List<String> getSuggestionsForTypeFiltered(@NotNull Class<?> targetType, @NotNull String currentInput,
            @NotNull CommandSender sender) {
        return getSuggestionsForTypeFiltered(targetType, currentInput, sender, null);
    }

    /**
     * Gets filtered suggestions for the provided argument.
     *
     * @param argument     the command argument metadata
     * @param currentInput current user input
     * @param sender       the command sender
     * @return filtered list of suggestions
     */
    @NotNull
    public List<String> getSuggestionsForArgumentFiltered(@NotNull CommandArgument argument,
            @NotNull String currentInput, @NotNull CommandSender sender) {
        return getSuggestionsForTypeFiltered(argument.getType(), currentInput, sender, argument);
    }

    private List<String> getSuggestionsForTypeFiltered(@NotNull Class<?> targetType, @Nullable String currentInput,
            @NotNull CommandSender sender, @Nullable CommandArgument argument) {
        List<String> suggestions = getSuggestionsInternal(targetType, sender, argument);

        if (currentInput == null || currentInput.isEmpty()) {
            return suggestions;
        }

        final String lowered = currentInput.toLowerCase();
        return suggestions.stream()
                .filter(suggestion -> suggestion != null && suggestion.toLowerCase().startsWith(lowered))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<String> getSuggestionsInternal(@NotNull Class<?> targetType, @NotNull CommandSender sender,
            @Nullable CommandArgument argument) {
        PrefixedLoggerGen.debug(logger, "Getting suggestions for type: " + targetType.getSimpleName());

        for (TypeParser<?> parser : parsers) {
            if (parser.canParse(targetType)) {
                PrefixedLoggerGen.debug(logger, "Using parser for suggestions: " + parser.getClass().getSimpleName());
                try {
                    List<String> suggestions = parser.getSuggestions(sender, argument);
                    PrefixedLoggerGen.debug(logger, "Got " + suggestions.size() + " suggestions");
                    return suggestions;
                } catch (Exception e) {
                    PrefixedLoggerGen.debug(logger, "Parser " + parser.getClass().getSimpleName()
                            + " failed to get suggestions: " + e.getMessage());
                }
            }
        }

        PrefixedLoggerGen.debug(logger, "No suitable parser found for suggestions");
        return new ArrayList<>();
    }

    /**
     * Checks if a string is a special suggestion source that can be parsed.
     * 
     * @param source the source string
     * @return true if any parser can handle this source
     */
    public boolean isSpecialSuggestion(@NotNull String source) {
        return parsers.stream().anyMatch(parser -> parser.canParseSuggestion(source));
    }

    /**
     * Gets all registered parsers.
     * 
     * @return an unmodifiable list of parsers
     */
    public List<TypeParser<?>> getParsers() {
        return Collections.unmodifiableList(parsers);
    }

    /**
     * Finds the first parser that can handle the given type.
     *
     * @param targetType class to resolve
     * @return parser or null if none match
     */
    @Nullable
    public TypeParser<?> findParserForType(@NotNull Class<?> targetType) {
        for (TypeParser<?> parser : parsers) {
            if (parser.canParse(targetType)) {
                return parser;
            }
        }
        return null;
    }

    /**
     * Removes a parser from the registry.
     * 
     * @param parser the parser to remove
     * @return true if the parser was removed
     */
    public boolean unregister(@NotNull TypeParser<?> parser) {
        boolean removed = parsers.remove(parser);
        if (removed) {
            PrefixedLoggerGen.debug(logger, "Unregistered type parser: " + parser.getClass().getSimpleName());
        }
        return removed;
    }

    /**
     * Removes all parsers of a specific class from the registry.
     * 
     * @param parserClass the class of parsers to remove
     * @return the number of parsers removed
     */
    public int unregisterByClass(@NotNull Class<? extends TypeParser<?>> parserClass) {
        int count = 0;
        Iterator<TypeParser<?>> iterator = parsers.iterator();
        while (iterator.hasNext()) {
            TypeParser<?> parser = iterator.next();
            if (parserClass.isInstance(parser)) {
                iterator.remove();
                count++;
                PrefixedLoggerGen.debug(logger, "Unregistered type parser: " + parser.getClass().getSimpleName());
            }
        }
        return count;
    }
}
