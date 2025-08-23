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
 */
@Data
public class ColorSettings {
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
    
    // Convert to map for easy access
    public Map<String, List<String>> toMap() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("error", error);
        map.put("warn", warn);
        map.put("debug", debug);
        map.put("success", success);
        return map;
    }
}
