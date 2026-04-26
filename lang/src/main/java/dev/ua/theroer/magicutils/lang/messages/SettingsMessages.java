package dev.ua.theroer.magicutils.lang.messages;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import lombok.Getter;

/**
 * Settings messages. Defaults are loaded from bundled {@code lang/<code>.json}
 * resources via {@link dev.ua.theroer.magicutils.lang.BundledTranslations}.
 */
@Getter
public class SettingsMessages {
    /**
     * Default constructor for SettingsMessages.
     */
    public SettingsMessages() {
    }

    @ConfigValue("language_not_initialized")
    private String languageNotInitialized;

    @ConfigValue("invalid_arguments")
    private String invalidArguments;

    @ConfigValue("current_language")
    private String currentLanguage;

    @ConfigValue("available_languages")
    private String availableLanguages;

    @ConfigValue("language_not_found")
    private String languageNotFound;

    @ConfigValue("key_not_found")
    private String keyNotFound;

    @ConfigValue("key_value")
    private String keyValue;

    @ConfigValue("key_set")
    private String keySet;
}
