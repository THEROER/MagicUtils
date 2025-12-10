package dev.ua.theroer.magicutils.commands.parsers;

import dev.ua.theroer.magicutils.commands.TypeParser;
import dev.ua.theroer.magicutils.commands.CommandArgument;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Generic enum parser to avoid registering a parser per enum type.
 */
public class EnumTypeParser implements TypeParser<Enum<?>> {

    /** Default constructor. */
    public EnumTypeParser() {
    }
    @Override
    public boolean canParse(@NotNull Class<?> type) {
        return type.isEnum();
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Enum<?> parse(@Nullable String value, @NotNull Class targetType, @NotNull CommandSender sender) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            return Enum.valueOf(targetType, normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public List<String> getSuggestions(@NotNull CommandSender sender) {
        return List.of();
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<String> getSuggestions(@NotNull CommandSender sender, @Nullable CommandArgument argument) {
        if (argument == null) {
            return List.of();
        }
        Class<?> type = argument.getType();
        if (!type.isEnum()) {
            return List.of();
        }
        return Arrays.stream(((Class<? extends Enum>) type).getEnumConstants())
                .map(e -> e.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    @Override
    public boolean canParseSuggestion(@NotNull String source) {
        return false;
    }

    @Override
    public List<String> parseSuggestion(@NotNull String source, @NotNull CommandSender sender) {
        return List.of();
    }
}
