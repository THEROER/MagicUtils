package dev.ua.theroer.magicutils.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for parsing command arguments to specific types.
 * @param <T> the type that this parser can parse to
 */
public interface ArgumentTypeParser<T> {
    
    /**
     * Checks if this parser can handle the given type.
     * @param type the class type to check
     * @return true if this parser can handle the type
     */
    boolean canParse(@NotNull Class<?> type);
    
    /**
     * Parses the given value to the target type.
     * @param value the string value to parse
     * @param targetType the target class type
     * @param sender the command sender (for context like @sender)
     * @return the parsed object or null if parsing failed
     */
    @Nullable
    T parse(@Nullable String value, @NotNull Class<T> targetType, @NotNull CommandSender sender);
    
    /**
     * Gets the priority of this parser. Higher priority parsers are checked first.
     * @return the priority value
     */
    default int getPriority() {
        return 0;
    }
}