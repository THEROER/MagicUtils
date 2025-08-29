package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Type parser for Integer arguments.
 */
public class IntegerTypeParser implements TypeParser<Integer> {
    
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == Integer.class || type == int.class;
    }
    
    @Override
    @Nullable
    public Integer parse(@Nullable String value, @NotNull Class<Integer> targetType, @NotNull CommandSender sender) {
        if (value == null) {
            return null;
        }
        
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSender sender) {
        return List.of("0", "1", "5", "10", "20", "50", "100");
    }
    
    @Override
    public int getPriority() {
        return 10;
    }
}