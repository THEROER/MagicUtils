package dev.ua.theroer.magicutils.lang.messages;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.annotations.DefaultValue;

/**
 * Settings messages
 */
public class SettingsMessages {
    /**
     * Default constructor for SettingsMessages.
     */
    public SettingsMessages() {
    }

    @ConfigValue("language_not_initialized")
    @DefaultValue("&cLanguage manager not initialized!")
    private String languageNotInitialized;

    @ConfigValue("invalid_arguments")
    @DefaultValue("&cInvalid arguments. First argument must be a language name when using 3 arguments.")
    private String invalidArguments;

    @ConfigValue("current_language")
    @DefaultValue("&aCurrent language: &f{language}")
    private String currentLanguage;

    @ConfigValue("available_languages")
    @DefaultValue("&aAvailable languages: &f{languages}")
    private String availableLanguages;

    @ConfigValue("language_not_found")
    @DefaultValue("&cLanguage '&f{language}&c' not found!")
    private String languageNotFound;

    @ConfigValue("key_not_found")
    @DefaultValue("&cKey '&f{key}&c' not found in language '&f{language}&c'")
    private String keyNotFound;

    @ConfigValue("key_value")
    @DefaultValue("&aLanguage: &f{language}\n&aKey: &f{key}\n&aValue: &f{value}")
    private String keyValue;

    @ConfigValue("key_set")
    @DefaultValue("&aSet key '&f{key}&a' to '&f{value}&a' in language '&f{language}&a'")
    private String keySet;

    /**
     * Gets a message by key.
     * 
     * @param key the key of the message
     * @return the message
     */
    public String getMessage(String key) {
        switch (key) {
            case "language_not_initialized":
                return languageNotInitialized;
            case "invalid_arguments":
                return invalidArguments;
            case "current_language":
                return currentLanguage;
            case "available_languages":
                return availableLanguages;
            case "language_not_found":
                return languageNotFound;
            case "key_not_found":
                return keyNotFound;
            case "key_value":
                return keyValue;
            case "key_set":
                return keySet;
            default:
                return null;
        }
    }
}