package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.annotations.DefaultValue;
import dev.ua.theroer.magicutils.config.logger.providers.DefaultChatGradientProvider;

import java.util.List;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import lombok.Data;

/**
 * Chat formatting settings.
 * Configuration class for managing chat message formatting options including
 * automatic color generation and gradient settings.
 */
@Data
public
class ChatSettings {
    
    /**
     * Default constructor for ChatSettings.
     * Initializes a new instance of ChatSettings with default values.
     */
    public ChatSettings() {
        // Default constructor - fields will be initialized with default values
    }
    @ConfigValue("auto-generate-colors")
    @DefaultValue("true")
    @Comment("Automatically generate colors based on plugin name")
    private boolean autoGenerateColors;
    
    @ConfigValue("gradient")
    @DefaultValue(provider = DefaultChatGradientProvider.class)
    @Comment("Default gradient colors for chat messages")
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
