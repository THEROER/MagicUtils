package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.Arrays;
import java.util.List;

import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

/**
 * Default provider for success message colors.
 * Provides a bright green-to-green color scheme for success messages.
 */
public class DefaultSuccessColorsProvider implements DefaultValueProvider<List<String>> {

    /**
     * Default constructor for DefaultSuccessColorsProvider.
     */
    public DefaultSuccessColorsProvider() {
    }

    @Override
    public List<String> provide() {
        return Arrays.asList("#00ff44", "#00cc22");
    }
}
