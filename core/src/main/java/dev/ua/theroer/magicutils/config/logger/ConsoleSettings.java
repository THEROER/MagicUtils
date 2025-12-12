package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigSection;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

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
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<String> gradient = new ArrayList<>(Arrays.asList("#ffcc00", "#ff6600"));

    @ConfigValue("strip-formatting")
    @Comment("Strip all formatting from console output")
    private boolean stripFormatting;

    @ConfigSection("colors")
    @Comment("Colors for different log levels")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private ColorSettings colors = new ColorSettings();

    /**
     * Gradient colors applied to console messages when explicit palettes are
     * absent.
     *
     * @return defensive copy of the configured gradient
     */
    public List<String> getGradient() {
        return new ArrayList<>(gradient);
    }

    /**
     * Replaces the console gradient palette.
     *
     * @param gradient list of hex colors to apply; null resets to an empty list
     */
    public void setGradient(List<String> gradient) {
        this.gradient = gradient != null ? new ArrayList<>(gradient) : new ArrayList<>();
    }

    /**
     * Color palette per log level for console output.
     *
     * @return deep copy of the color settings
     */
    public ColorSettings getColors() {
        ColorSettings copy = new ColorSettings();
        copy.setError(colors.getError());
        copy.setWarn(colors.getWarn());
        copy.setDebug(colors.getDebug());
        copy.setSuccess(colors.getSuccess());
        return copy;
    }

    /**
     * Sets console color palettes for each log level.
     *
     * @param colors color configuration to clone; null resets to defaults
     */
    public void setColors(ColorSettings colors) {
        if (colors == null) {
            this.colors = new ColorSettings();
            return;
        }
        ColorSettings copy = new ColorSettings();
        copy.setError(colors.getError());
        copy.setWarn(colors.getWarn());
        copy.setDebug(colors.getDebug());
        copy.setSuccess(colors.getSuccess());
        this.colors = copy;
    }
}
