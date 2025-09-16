package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.annotations.DefaultValue;
import dev.ua.theroer.magicutils.config.logger.providers.DefaultDebugColorsProvider;
import dev.ua.theroer.magicutils.config.logger.providers.DefaultErrorColorsProvider;
import dev.ua.theroer.magicutils.config.logger.providers.DefaultSuccessColorsProvider;
import dev.ua.theroer.magicutils.config.logger.providers.DefaultWarnColorsProvider;
import dev.ua.theroer.magicutils.config.annotations.Comment;
import lombok.Data;

import java.util.List;

import java.util.Map;
import java.util.HashMap;

/**
 * Color settings for different log levels.
 * Configuration class that manages color schemes for various types of log
 * messages
 * including error, warning, debug, and success messages.
 */
@Data
public class ColorSettings {

    /**
     * Default constructor for ColorSettings.
     */
    public ColorSettings() {
    }

    @ConfigValue("error")
    @DefaultValue(provider = DefaultErrorColorsProvider.class)
    @Comment("Colors for error messages")
    private List<String> error;

    @ConfigValue("warn")
    @DefaultValue(provider = DefaultWarnColorsProvider.class)
    @Comment("Colors for warning messages")
    private List<String> warn;

    @ConfigValue("debug")
    @DefaultValue(provider = DefaultDebugColorsProvider.class)
    @Comment("Colors for debug messages")
    private List<String> debug;

    @ConfigValue("success")
    @DefaultValue(provider = DefaultSuccessColorsProvider.class)
    @Comment("Colors for success messages")
    private List<String> success;

    /**
     * Converts the color settings to a map for easy access.
     * Creates a map where keys are log level names and values are their
     * corresponding color lists.
     * 
     * @return a Map containing log level names as keys and their color lists as
     *         values
     */
    public Map<String, List<String>> toMap() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("error", error);
        map.put("warn", warn);
        map.put("debug", debug);
        map.put("success", success);
        return map;
    }
}
