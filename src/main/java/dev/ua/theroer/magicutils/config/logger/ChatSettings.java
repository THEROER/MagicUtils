package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Data;

/**
 * Chat formatting settings.
 * Configuration class for managing chat message formatting options including
 * automatic color generation and gradient settings.
 */
@Data
public class ChatSettings {

    /**
     * Default constructor for ChatSettings.
     */
    public ChatSettings() {
    }

    @ConfigValue("auto-generate-colors")
    @Comment("Automatically generate colors based on plugin name")
    private boolean autoGenerateColors = true;

    @ConfigValue("gradient")
    @Comment("Default gradient colors for chat messages")
    private List<String> gradient = new ArrayList<>(Arrays.asList("#7c3aed", "#ec4899"));

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
