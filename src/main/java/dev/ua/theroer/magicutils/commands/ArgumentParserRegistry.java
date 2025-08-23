package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.SubLogger;
import dev.ua.theroer.magicutils.commands.parsers.argument.*;


import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Registry for managing argument type parsers.
 */
public class ArgumentParserRegistry {
    private static final SubLogger logger = Logger.getSubLogger("Commands", "[Commands]");
    private final List<ArgumentTypeParser<?>> parsers = new ArrayList<>();
    
    /**
     * Create a new ArgumentParserRegistry.
     */
    public ArgumentParserRegistry() {
        registerDefaultParsers();
    }
    
    /**
     * Registers default parsers for common types.
     */
    private void registerDefaultParsers() {
        register(new StringArgumentParser());
        register(new PlayerArgumentParser());
        register(new WorldArgumentParser());
        register(new IntegerArgumentParser());
        register(new LongArgumentParser());
        register(new BooleanArgumentParser());
        
        logger.debug("Registered " + parsers.size() + " default argument parsers");
    }
    
    /**
     * Registers a new argument parser.
     * @param parser the parser to register
     */
    public void register(@NotNull ArgumentTypeParser<?> parser) {
        parsers.add(parser);
        // Sort by priority (highest first)
        parsers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        logger.debug("Registered argument parser: " + parser.getClass().getSimpleName());
    }
    
    /**
     * Parses a value to the specified type using registered parsers.
     * @param <T> the type to parse to
     * @param value the string value to parse
     * @param targetType the target class type
     * @param sender the command sender for context
     * @return the parsed object or null if no suitable parser found
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T parse(@Nullable String value, @NotNull Class<T> targetType, @NotNull CommandSender sender) {
        logger.debug("Parsing value: '" + value + "' to type: " + targetType.getSimpleName());
        
        for (ArgumentTypeParser<?> parser : parsers) {
            if (parser.canParse(targetType)) {
                logger.debug("Using parser: " + parser.getClass().getSimpleName());
                try {
                    @SuppressWarnings("rawtypes")
                    Object result = parser.parse(value, (Class) targetType, sender);
                    if (result != null || value == null) {
                        logger.debug("Successfully parsed to: " + (result != null ? result.getClass().getSimpleName() + "=" + result : "null"));
                        return (T) result;
                    }
                } catch (Exception e) {
                    logger.debug("Parser " + parser.getClass().getSimpleName() + " failed: " + e.getMessage());
                }
            }
        }
        
        logger.debug("No suitable parser found for type: " + targetType.getSimpleName());
        return null;
    }
    
    /**
     * Gets all registered parsers.
     * @return an unmodifiable list of parsers
     */
    public List<ArgumentTypeParser<?>> getParsers() {
        return Collections.unmodifiableList(parsers);
    }
    
    /**
     * Removes a parser from the registry.
     * @param parser the parser to remove
     * @return true if the parser was removed
     */
    public boolean unregister(@NotNull ArgumentTypeParser<?> parser) {
        boolean removed = parsers.remove(parser);
        if (removed) {
            logger.debug("Unregistered argument parser: " + parser.getClass().getSimpleName());
        }
        return removed;
    }
    
    /**
     * Removes all parsers of a specific class from the registry.
     * @param parserClass the class of parsers to remove
     * @return the number of parsers removed
     */
    public int unregisterByClass(@NotNull Class<? extends ArgumentTypeParser<?>> parserClass) {
        int count = 0;
        Iterator<ArgumentTypeParser<?>> iterator = parsers.iterator();
        while (iterator.hasNext()) {
            ArgumentTypeParser<?> parser = iterator.next();
            if (parserClass.isInstance(parser)) {
                iterator.remove();
                count++;
                logger.debug("Unregistered argument parser: " + parser.getClass().getSimpleName());
            }
        }
        return count;
    }
}