package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.HashMap;
import java.util.Map;

import dev.ua.theroer.magicutils.config.SubLoggerConfig;
import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

/**
 * Default provider for sub-logger configurations.
 * Provides default configuration for common sub-loggers like database and API loggers.
 */
public class DefaultSubLoggersProvider implements DefaultValueProvider<Map<String, SubLoggerConfig>> {
    
    /**
     * Constructs a new DefaultSubLoggersProvider.
     */
    public DefaultSubLoggersProvider() {
        // Default constructor
    }
    @Override
    public Map<String, SubLoggerConfig> provide() {
        Map<String, SubLoggerConfig> defaults = new HashMap<>();
        // Example default sub-loggers
        defaults.put("database", SubLoggerConfig.builder().enabled(true).build());
        defaults.put("api", SubLoggerConfig.builder().enabled(true).build());
        return defaults;
    }
}