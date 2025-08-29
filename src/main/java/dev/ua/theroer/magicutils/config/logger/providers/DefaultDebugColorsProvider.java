package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.Arrays;
import java.util.List;

import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

/**
 * Default provider for debug message colors.
 * Provides a light blue-to-blue color scheme for debug messages.
 */
public class DefaultDebugColorsProvider implements DefaultValueProvider<List<String>> {
    
    /**
     * Constructs a new DefaultDebugColorsProvider.
     */
    public DefaultDebugColorsProvider() {
        // Default constructor
    }
    @Override
    public List<String> provide() {
        return Arrays.asList("#00aaff", "#0066cc");
    }
}
