package dev.ua.theroer.magicutils.commands.parsers.argument;

import dev.ua.theroer.magicutils.Logger;
import dev.ua.theroer.magicutils.commands.ArgumentTypeParser;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parser for Boolean arguments.
 */
public class BooleanArgumentParser implements ArgumentTypeParser<Boolean> {
    
    /**
     * Create a new BooleanArgumentParser.
     */
    public BooleanArgumentParser() {}
    
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }
    
    @Override
    @Nullable
    public Boolean parse(@Nullable String value, @NotNull Class<Boolean> targetType, @NotNull CommandSender sender) {
        if (value == null) {
            return null;
        }
        
        Boolean result = Boolean.parseBoolean(value);
        Logger.debug("Parsed boolean: " + result);
        return result;
    }
    
    @Override
    public int getPriority() {
        return 10;
    }
}