package dev.ua.theroer.magicutils.config.logger;

import dev.ua.theroer.magicutils.config.annotations.Comment;
import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import lombok.Data;

/**
 * Default settings for logger behavior.
 * Controls default message routing and formatting options.
 */
@Data
public class DefaultSettings {

    /**
     * Default constructor for DefaultSettings.
     */
    public DefaultSettings() {
    }

    @ConfigValue("target")
    @Comment("Default target for messages (CHAT, CONSOLE, BOTH)")
    private String target = "BOTH";
}
