package dev.ua.theroer.magicutils.commands.parsers.argument;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.ArgumentTypeParser;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parser for Integer arguments.
 */
public class IntegerArgumentParser implements ArgumentTypeParser<Integer> {
    
    /**
     * Create a new IntegerArgumentParser.
     */
    public IntegerArgumentParser() {}
    
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == int.class || type == Integer.class;
    }
    
    @Override
    @Nullable
    public Integer parse(@Nullable String value, @NotNull Class<Integer> targetType, @NotNull CommandSender sender) {
        if (value == null) {
            return null;
        }
        
        try {
            Integer result = Integer.parseInt(value);
            Logger.debug("Parsed integer: " + result);
            return result;
        } catch (NumberFormatException e) {
            Logger.debug("Failed to parse integer: " + value);
            return null;
        }
    }
    
    @Override
    public int getPriority() {
        return 10;
    }
}