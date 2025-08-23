package dev.ua.theroer.magicutils.commands.parsers.argument;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.ArgumentTypeParser;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parser for Long arguments.
 */
public class LongArgumentParser implements ArgumentTypeParser<Long> {
    
    /**
     * Create a new LongArgumentParser.
     */
    public LongArgumentParser() {}
    
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == long.class || type == Long.class;
    }
    
    @Override
    @Nullable
    public Long parse(@Nullable String value, @NotNull Class<Long> targetType, @NotNull CommandSender sender) {
        if (value == null) {
            return null;
        }
        
        try {
            Long result = Long.parseLong(value);
            Logger.debug("Parsed long: " + result);
            return result;
        } catch (NumberFormatException e) {
            Logger.debug("Failed to parse long: " + value);
            return null;
        }
    }
    
    @Override
    public int getPriority() {
        return 10;
    }
}