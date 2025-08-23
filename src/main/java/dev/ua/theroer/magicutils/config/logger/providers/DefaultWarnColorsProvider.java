package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.Arrays;
import java.util.List;

import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

public class DefaultWarnColorsProvider implements DefaultValueProvider<List<String>> {
    @Override
    public List<String> provide() {
        return Arrays.asList("#ffaa00", "#ff6600");
    }
}
