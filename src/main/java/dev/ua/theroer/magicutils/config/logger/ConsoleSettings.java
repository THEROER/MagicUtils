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
 */
@Data
public class ConsoleSettings {
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
    
    public ColorSettings getColors() { return colors; }
}
