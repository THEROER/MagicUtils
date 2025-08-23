package dev.ua.theroer.magicutils.config.logger.providers;

import java.util.HashMap;
import java.util.Map;

import dev.ua.theroer.magicutils.config.SubLoggerConfig;
import dev.ua.theroer.magicutils.config.annotations.DefaultValueProvider;

public class DefaultSubLoggersProvider implements DefaultValueProvider<Map<String, SubLoggerConfig>> {
    @Override
    public Map<String, SubLoggerConfig> provide() {
        Map<String, SubLoggerConfig> defaults = new HashMap<>();
        // Example default sub-loggers
        defaults.put("database", SubLoggerConfig.builder().enabled(true).build());
        defaults.put("api", SubLoggerConfig.builder().enabled(true).build());
        return defaults;
    }
}