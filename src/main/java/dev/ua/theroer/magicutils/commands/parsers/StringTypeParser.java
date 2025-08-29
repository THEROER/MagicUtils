package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Type parser for String arguments.
 */
public class StringTypeParser implements TypeParser<String> {
    
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == String.class;
    }
    
    @Override
    @Nullable
    public String parse(@Nullable String value, @NotNull Class<String> targetType, @NotNull CommandSender sender) {
        return value;
    }
    
    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSender sender) {
        // Default empty suggestions for strings
        return new ArrayList<>();
    }
    
    @Override
    public int getPriority() {
        return -100; // Lowest priority as fallback
    }
}