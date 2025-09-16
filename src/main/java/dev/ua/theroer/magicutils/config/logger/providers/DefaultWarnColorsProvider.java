package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.Arrays;
import java.util.List;

import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

/**
 * Default provider for warning message colors.
 * Provides an orange-to-darker orange color scheme for warning messages.
 */
public class DefaultWarnColorsProvider implements DefaultValueProvider<List<String>> {

    /**
     * Default constructor for DefaultWarnColorsProvider.
     */
    public DefaultWarnColorsProvider() {
    }

    @Override
    public List<String> provide() {
        return Arrays.asList("#ffaa00", "#ff6600");
    }
}
