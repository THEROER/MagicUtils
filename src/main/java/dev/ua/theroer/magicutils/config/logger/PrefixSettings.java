package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import lombok.Data;

/**
 * Prefix configuration settings for logger.
 * Controls how prefixes are displayed in chat and console messages.
 */
@Data
public class PrefixSettings {

    /**
     * Default constructor for PrefixSettings.
     */
    public PrefixSettings() {
    }

    @ConfigValue("chat-mode")
    @Comment("Prefix mode for chat messages (NONE, SHORT, FULL, CUSTOM)")
    private String chatMode = "FULL";

    @ConfigValue("console-mode")
    @Comment("Prefix mode for console messages (NONE, SHORT, FULL, CUSTOM)")
    private String consoleMode = "SHORT";

    @ConfigValue("custom")
    @Comment("Custom prefix when mode is CUSTOM")
    private String custom = "[UAP]";

    @ConfigValue("use-gradient-chat")
    @Comment("Apply gradient to prefix in chat")
    private boolean useGradientChat = true;

    @ConfigValue("use-gradient-console")
    @Comment("Apply gradient to prefix in console")
    private boolean useGradientConsole;
}
