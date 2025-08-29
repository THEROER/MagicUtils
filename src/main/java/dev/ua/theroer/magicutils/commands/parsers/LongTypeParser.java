package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Type parser for Long arguments.
 */
public class LongTypeParser implements TypeParser<Long> {
    
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == Long.class || type == long.class;
    }
    
    @Override
    @Nullable
    public Long parse(@Nullable String value, @NotNull Class<Long> targetType, @NotNull CommandSender sender) {
        if (value == null) {
            return null;
        }
        
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSender sender) {
        return List.of("0", "1", "100", "1000", "10000");
    }
    
    @Override
    public int getPriority() {
        return 10;
    }
}