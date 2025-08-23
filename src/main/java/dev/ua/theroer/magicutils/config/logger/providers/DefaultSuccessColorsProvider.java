package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.Arrays;
import java.util.List;

import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

public class DefaultSuccessColorsProvider implements DefaultValueProvider<List<String>> {
    @Override
    public List<String> provide() {
        return Arrays.asList("#00ff44", "#00cc22");
    }
}
