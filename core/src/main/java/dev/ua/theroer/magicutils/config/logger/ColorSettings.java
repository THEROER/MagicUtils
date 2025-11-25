package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

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
    @Comment("Colors for error messages")
    private List<String> error = new ArrayList<>(Arrays.asList("#ff4444", "#cc0000"));

    @ConfigValue("warn")
    @Comment("Colors for warning messages")
    private List<String> warn = new ArrayList<>(Arrays.asList("#ffbb33", "#ff8800"));

    @ConfigValue("debug")
    @Comment("Colors for debug messages")
    private List<String> debug = new ArrayList<>(Arrays.asList("#33b5e5", "#0099cc"));

    @ConfigValue("success")
    @Comment("Colors for success messages")
    private List<String> success = new ArrayList<>(Arrays.asList("#00c851", "#007e33"));

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
