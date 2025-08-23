package dev.ua.theroer.magicutils.commands.parsers.argument;

import dev.ua.theroer.magicutils.commands.ArgumentTypeParser;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parser for String arguments.
 */
public class StringArgumentParser implements ArgumentTypeParser<String> {
    
    /**
     * Create a new StringArgumentParser.
     */
    public StringArgumentParser() {}
    
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
    public int getPriority() {
        return -100;
    }
}