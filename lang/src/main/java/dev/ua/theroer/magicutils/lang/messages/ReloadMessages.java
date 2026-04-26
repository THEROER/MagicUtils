package dev.ua.theroer.magicutils.lang.messages;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import lombok.Getter;

/**
 * Reload messages. Defaults are loaded from bundled {@code lang/<code>.json}
 * resources via {@link dev.ua.theroer.magicutils.lang.BundledTranslations}.
 */
@Getter
public class ReloadMessages {
    /**
     * Default constructor for ReloadMessages.
     */
    public ReloadMessages() {
    }

    @ConfigValue("all_commands")
    private String allCommands;

    @ConfigValue("command")
    private String command;

    @ConfigValue("all_sections")
    private String allSections;

    @ConfigValue("section")
    private String section;

    @ConfigValue("global_settings")
    private String globalSettings;

    @ConfigValue("global_setting")
    private String globalSetting;
}
