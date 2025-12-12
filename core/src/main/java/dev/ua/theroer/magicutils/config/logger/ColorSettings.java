package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

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
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<String> error = new ArrayList<>(Arrays.asList("#ff4444", "#cc0000"));

    @ConfigValue("warn")
    @Comment("Colors for warning messages")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<String> warn = new ArrayList<>(Arrays.asList("#ffbb33", "#ff8800"));

    @ConfigValue("debug")
    @Comment("Colors for debug messages")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<String> debug = new ArrayList<>(Arrays.asList("#33b5e5", "#0099cc"));

    @ConfigValue("success")
    @Comment("Colors for success messages")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
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
        map.put("error", new ArrayList<>(error));
        map.put("warn", new ArrayList<>(warn));
        map.put("debug", new ArrayList<>(debug));
        map.put("success", new ArrayList<>(success));
        return map;
    }

    /**
     * Error colors.
     *
     * @return defensive copy of the error palette
     */
    public List<String> getError() {
        return new ArrayList<>(error);
    }

    /**
     * Sets colors used for error messages.
     *
     * @param error palette to copy; null clears the list
     */
    public void setError(List<String> error) {
        this.error = error != null ? new ArrayList<>(error) : new ArrayList<>();
    }

    /**
     * Warning colors.
     *
     * @return defensive copy of the warning palette
     */
    public List<String> getWarn() {
        return new ArrayList<>(warn);
    }

    /**
     * Sets colors used for warnings.
     *
     * @param warn palette to copy; null clears the list
     */
    public void setWarn(List<String> warn) {
        this.warn = warn != null ? new ArrayList<>(warn) : new ArrayList<>();
    }

    /**
     * Debug colors.
     *
     * @return defensive copy of the debug palette
     */
    public List<String> getDebug() {
        return new ArrayList<>(debug);
    }

    /**
     * Sets colors used for debug messages.
     *
     * @param debug palette to copy; null clears the list
     */
    public void setDebug(List<String> debug) {
        this.debug = debug != null ? new ArrayList<>(debug) : new ArrayList<>();
    }

    /**
     * Success colors.
     *
     * @return defensive copy of the success palette
     */
    public List<String> getSuccess() {
        return new ArrayList<>(success);
    }

    /**
     * Sets colors used for success messages.
     *
     * @param success palette to copy; null clears the list
     */
    public void setSuccess(List<String> success) {
        this.success = success != null ? new ArrayList<>(success) : new ArrayList<>();
    }
}
