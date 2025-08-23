package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.Arrays;
import java.util.List;

import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

public class DefaultConsoleGradientProvider implements DefaultValueProvider<List<String>> {
    @Override
    public List<String> provide() {
        return Arrays.asList("#ffcc00", "#ff6600");
    }
}
