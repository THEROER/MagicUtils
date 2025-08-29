package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.annotations.DefaultValue;
import dev.ua.theroer.magicutils.config.logger.providers.DefaultConsoleGradientProvider;
import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import lombok.Data;

import java.util.List;

/**
 * Console formatting settings.
 * Configuration class for managing console output formatting options including
 * automatic color generation and gradient settings for console messages.
 */
@Data
public class ConsoleSettings {
    
    /**
     * Default constructor for ConsoleSettings.
     * Initializes a new instance of ConsoleSettings with default values.
     */
    public ConsoleSettings() {
        // Default constructor - fields will be initialized with default values
    }
    @ConfigValue("auto-generate-colors")
    @DefaultValue("true")
    @Comment("Automatically generate colors based on plugin name")
    private boolean autoGenerateColors;
    
    @ConfigValue("gradient")
    @DefaultValue(provider = DefaultConsoleGradientProvider.class)
    @Comment("Default gradient colors for console messages")
    private List<String> gradient;
    
    @ConfigSection("colors")
    @Comment("Colors for different log levels")
    private ColorSettings colors = new ColorSettings();
    
    /**
     * Gets the color settings for different log levels.
     * 
     * @return the ColorSettings instance containing color configurations
     */
    public ColorSettings getColors() { return colors; }
}
