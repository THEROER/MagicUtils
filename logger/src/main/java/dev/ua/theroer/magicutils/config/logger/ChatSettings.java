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
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<String> gradient = new ArrayList<>(Arrays.asList("#7c3aed", "#ec4899"));

    @ConfigSection("colors")
    @Comment("Colors for different log levels")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private ColorSettings colors = new ColorSettings();

    /**
     * Gradient colors applied to chat messages when a level-specific palette is
     * not provided.
     *
     * @return defensive copy of the configured gradient colors
     */
    public List<String> getGradient() {
        return new ArrayList<>(gradient);
    }

    /**
     * Replaces the chat gradient palette.
     *
     * @param gradient list of hex colors to apply; null resets to an empty list
     */
    public void setGradient(List<String> gradient) {
        this.gradient = gradient != null ? new ArrayList<>(gradient) : new ArrayList<>();
    }

    /**
     * Color palette per log level for chat output.
     *
     * @return deep copy of the color settings
     */
    public ColorSettings getColors() {
        ColorSettings copy = new ColorSettings();
        copy.setError(new ArrayList<>(colors.getError()));
        copy.setWarn(new ArrayList<>(colors.getWarn()));
        copy.setDebug(new ArrayList<>(colors.getDebug()));
        copy.setSuccess(new ArrayList<>(colors.getSuccess()));
        return copy;
    }

    /**
     * Sets chat color palettes for each log level.
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
