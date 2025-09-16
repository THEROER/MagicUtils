package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.Arrays;
import java.util.List;

import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

/**
 * Default provider for error message colors.
 * Provides a light red-to-dark red color scheme for error messages.
 */
public class DefaultErrorColorsProvider implements DefaultValueProvider<List<String>> {

    /**
     * Default constructor for DefaultErrorColorsProvider.
     */
    public DefaultErrorColorsProvider() {
    }

    @Override
    public List<String> provide() {
        return Arrays.asList("#ff4444", "#cc0000");
    }
}
