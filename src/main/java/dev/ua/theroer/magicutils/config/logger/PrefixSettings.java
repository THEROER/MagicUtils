package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.annotations.DefaultValue;
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
    @DefaultValue("FULL")
    @Comment("Prefix mode for chat messages (NONE, SHORT, FULL, CUSTOM)")
    private String chatMode;

    @ConfigValue("console-mode")
    @DefaultValue("SHORT")
    @Comment("Prefix mode for console messages (NONE, SHORT, FULL, CUSTOM)")
    private String consoleMode;

    @ConfigValue("custom")
    @DefaultValue("[UAP]")
    @Comment("Custom prefix when mode is CUSTOM")
    private String custom;

    @ConfigValue("use-gradient-chat")
    @DefaultValue("true")
    @Comment("Apply gradient to prefix in chat")
    private boolean useGradientChat;

    @ConfigValue("use-gradient-console")
    @DefaultValue("false")
    @Comment("Apply gradient to prefix in console")
    private boolean useGradientConsole;
}