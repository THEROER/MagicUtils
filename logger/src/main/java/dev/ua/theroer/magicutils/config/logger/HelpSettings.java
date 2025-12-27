package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import lombok.Data;

/**
 * Help command formatting settings.
 */
@Data
public class HelpSettings {

    /**
     * Default constructor for HelpSettings.
     */
    public HelpSettings() {
    }

    @ConfigValue("use-logger-colors")
    @Comment("Use logger chat colors for help output")
    private boolean useLoggerColors = true;

    @ConfigValue("primary-color")
    @Comment("Primary help color (when use-logger-colors is false)")
    private String primaryColor = "#ff55ff";

    @ConfigValue("muted-color")
    @Comment("Muted help color (when use-logger-colors is false)")
    private String mutedColor = "gray";

    @ConfigValue("text-color")
    @Comment("Text help color")
    private String textColor = "white";

    @ConfigValue("line")
    @Comment("Header/footer line for help output")
    private String line = "-----------------------------";

    @ConfigValue("page-size")
    @Comment("Commands per page in help output")
    private int pageSize = 7;

    @ConfigValue("max-enum-values")
    @Comment("Max enum values to display in help output")
    private int maxEnumValues = 8;
}
