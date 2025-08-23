package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.Arrays;
import java.util.List;

import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

public class DefaultErrorColorsProvider implements DefaultValueProvider<List<String>> {
    @Override
    public List<String> provide() {
        return Arrays.asList("#ff4444", "#cc0000");
    }
}
