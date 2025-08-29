package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Type parser for Boolean arguments.
 */
public class BooleanTypeParser implements TypeParser<Boolean> {
    
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type == Boolean.class || type == boolean.class;
    }
    
    @Override
    @Nullable
    public Boolean parse(@Nullable String value, @NotNull Class<Boolean> targetType, @NotNull CommandSender sender) {
        if (value == null) {
            return null;
        }
        
        String lower = value.toLowerCase();
        if ("true".equals(lower) || "yes".equals(lower) || "on".equals(lower) || "1".equals(lower)) {
            return true;
        }
        if ("false".equals(lower) || "no".equals(lower) || "off".equals(lower) || "0".equals(lower)) {
            return false;
        }
        
        return null;
    }
    
    @Override
    @NotNull
    public List<String> getSuggestions(@NotNull CommandSender sender) {
        return Arrays.asList("true", "false");
    }
    
    @Override
    public boolean canParseSuggestion(@NotNull String source) {
        return "{true,false}".equals(source) || 
               "{yes,no}".equals(source) || 
               "{on,off}".equals(source) ||
               "{on,off,toggle}".equals(source);
    }
    
    @Override
    @NotNull
    public List<String> parseSuggestion(@NotNull String source, @NotNull CommandSender sender) {
        switch (source) {
            case "{true,false}":
                return Arrays.asList("true", "false");
            case "{yes,no}":
                return Arrays.asList("yes", "no");
            case "{on,off}":
                return Arrays.asList("on", "off");
            case "{on,off,toggle}":
                return Arrays.asList("on", "off", "toggle");
            default:
                return getSuggestions(sender);
        }
    }
    
    @Override
    public int getPriority() {
        return 10;
    }
}