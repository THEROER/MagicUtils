package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.Arrays;
import java.util.List;

import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

/**
 * Default provider for console gradient colors.
 * Provides a yellow-to-orange gradient color scheme for console output.
 */
public class DefaultConsoleGradientProvider implements DefaultValueProvider<List<String>> {
    
    /**
     * Constructs a new DefaultConsoleGradientProvider.
     */
    public DefaultConsoleGradientProvider() {
        // Default constructor
    }
    @Override
    public List<String> provide() {
        return Arrays.asList("#ffcc00", "#ff6600");
    }
}
