package dev.ua.theroer.magicutils;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.OptionalArgument;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import dev.ua.theroer.magicutils.annotations.Suggest;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.MagicCommand;
import dev.ua.theroer.magicutils.commands.parsers.LanguageKeyTypeParser;
import dev.ua.theroer.magicutils.lang.InternalMessages;
import dev.ua.theroer.magicutils.lang.LanguageManager;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Command for managing plugin settings including language management.
 */
@CommandInfo(name = "settings", description = "MagicUtils settings command", permission = true, aliases = { "config",
        "cfg" })
public class SettingsCommand extends MagicCommand {

    @Setter
    private static LanguageManager languageManager;

    /**
     * Default constructor for SettingsCommand.
     * 
     * @param languageManager the language manager instance for handling language
     *                        operations
     * @param plugin          the JavaPlugin instance for plugin-related operations
     */
    public SettingsCommand(LanguageManager languageManager, JavaPlugin plugin) {
        setLanguageManager(languageManager);

        LanguageKeyTypeParser.setPlugin(plugin);
    }

    /**
     * Manages language settings with flexible argument handling.
     * 
     * @param langOrKey  first argument - can be language name or key
     * @param keyOrValue second argument - can be key or value
     * @param value      third argument - value when setting
     * @return the result of the operation
     */
    @SubCommand(name = "lang", aliases = { "language" }, description = "Manage language settings", permission = true)
    public CommandResult executeLang(
            @Suggest(value = { "getAvailableLanguages",
                    "@language_keys" }, permission = false) @OptionalArgument String langOrKey,

            @Suggest(value = "@language_keys", permission = false) @OptionalArgument String keyOrValue,

            @OptionalArgument String value) {
        return handleLanguageCommand(langOrKey, keyOrValue, value);
    }

    /**
     * Handles the language command logic.
     * 
     * @param langOrKey  first argument
     * @param keyOrValue second argument
     * @param value      third argument
     * @return command result
     */
    private CommandResult handleLanguageCommand(String langOrKey, String keyOrValue, String value) {
        if (languageManager == null) {
            return CommandResult.failure(InternalMessages.SETTINGS_LANG_NOT_INIT.get());
        }

        // 0 arguments: show current language and available languages
        if (langOrKey == null) {
            return showLanguageStatus();
        }

        // Check if first argument is a language name
        boolean isFirstArgLanguage = languageManager.getAvailableLanguages().contains(langOrKey);

        // 1 argument: either show language content or show key in current language
        if (keyOrValue == null) {
            if (isFirstArgLanguage) {
                return showLanguageInfo(langOrKey);
            } else {
                // Treat as key in current language
                return showKeyValue(languageManager.getCurrentLanguage(), langOrKey);
            }
        }

        // 2 arguments: either show key in specific language or set key in current
        // language
        if (value == null) {
            if (isFirstArgLanguage) {
                // Show key in specific language
                return showKeyValue(langOrKey, keyOrValue);
            } else {
                // Set key in current language
                return setKeyValue(languageManager.getCurrentLanguage(), langOrKey, keyOrValue);
            }
        }

        // 3 arguments: set key in specific language
        if (isFirstArgLanguage) {
            return setKeyValue(langOrKey, keyOrValue, value);
        } else {
            return CommandResult.failure(InternalMessages.SETTINGS_INVALID_ARGS.get());
        }
    }

    /**
     * Shows current language status and available languages.
     */
    private CommandResult showLanguageStatus() {
        String currentLang = languageManager.getCurrentLanguage();
        String availableLanguages = String.join(", ", languageManager.getAvailableLanguages());

        return CommandResult.success(InternalMessages.SETTINGS_CURRENT_LANG.get("language", currentLang) + "\n"
                + InternalMessages.SETTINGS_AVAILABLE_LANGS.get("languages", availableLanguages));
    }

    /**
     * Shows information about a specific language.
     */
    private CommandResult showLanguageInfo(String languageCode) {
        if (!languageManager.getAvailableLanguages().contains(languageCode)) {
            return CommandResult.failure(InternalMessages.SETTINGS_LANG_NOT_FOUND.get("language", languageCode));
        }

        return CommandResult.success(InternalMessages.SETTINGS_CURRENT_LANG.get("language", languageCode) + "\n"
                + InternalMessages.SETTINGS_AVAILABLE_LANGS.get("languages",
                        String.join(", ", languageManager.getAvailableLanguages())));
    }

    /**
     * Shows the value of a key in specified language.
     */
    private CommandResult showKeyValue(String languageCode, String key) {
        if (!languageManager.getAvailableLanguages().contains(languageCode)) {
            return CommandResult.failure(InternalMessages.SETTINGS_LANG_NOT_FOUND.get("language", languageCode));
        }

        if (!languageManager.hasMessageForLanguage(languageCode, key)) {
            return CommandResult
                    .failure(InternalMessages.SETTINGS_KEY_NOT_FOUND.get("key", key, "language", languageCode));
        }

        String message = languageManager.getMessageForLanguage(languageCode, key);

        return CommandResult.success(
                InternalMessages.SETTINGS_KEY_VALUE.get("language", languageCode, "key", key, "value", message));
    }

    /**
     * Sets the value of a key in specified language.
     */
    private CommandResult setKeyValue(String languageCode, String key, String newValue) {
        if (!languageManager.getAvailableLanguages().contains(languageCode)) {
            return CommandResult.failure(InternalMessages.SETTINGS_LANG_NOT_FOUND.get("language", languageCode));
        }

        Map<String, String> customMessages = new HashMap<>();
        customMessages.put(key, newValue);
        languageManager.saveCustomMessages(languageCode, customMessages);

        return CommandResult.success(
                InternalMessages.SETTINGS_KEY_SET.get("key", key, "value", newValue, "language", languageCode));
    }

    /**
     * Gets available languages for suggestions.
     * 
     * @return array of available language codes
     */
    public String[] getAvailableLanguages() {
        if (languageManager == null) {
            return new String[] { "en", "uk" };
        }
        return languageManager.getAvailableLanguages().toArray(new String[0]);
    }

    /**
     * Gets dynamic key suggestions based on context.
     * This method will be replaced by @language_keys suggestion parser.
     * 
     * @return array of key suggestions
     */
    public String[] getDynamicKeySuggestions() {
        // Fallback suggestions if dynamic parsing fails
        return new String[0];
    }
}
