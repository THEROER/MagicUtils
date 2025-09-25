package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Data;

/**
 * Console formatting settings.
 * Configuration class for managing console output formatting options including
 * automatic color generation and gradient settings for console messages.
 */
@Data
public class ConsoleSettings {

    /**
     * Default constructor for ConsoleSettings.
     */
    public ConsoleSettings() {
    }

    @ConfigValue("auto-generate-colors")
    @Comment("Automatically generate colors based on plugin name")
    private boolean autoGenerateColors = true;

    @ConfigValue("gradient")
    @Comment("Default gradient colors for console messages")
    private List<String> gradient = new ArrayList<>(Arrays.asList("#ffcc00", "#ff6600"));

    @ConfigValue("strip-formatting")
    @Comment("Strip all formatting from console output")
    private boolean stripFormatting;

    @ConfigSection("colors")
    @Comment("Colors for different log levels")
    private ColorSettings colors = new ColorSettings();

    /**
     * Gets the color settings for different log levels.
     * 
     * @return the ColorSettings instance containing color configurations
     */
    public ColorSettings getColors() {
        return colors;
    }
}
