package dev.ua.theroer.magicutils.lang;

import java.util.Map;

/**
 * Internal messages keys for MagicUtils library (platform-agnostic).
 */
public enum InternalMessages {
    // Command messages
    /** Lacking permission to run command. */
    CMD_NO_PERMISSION("commands.no_permission"),
    /** Generic execution failure. */
    CMD_EXECUTION_ERROR("commands.execution_error"),
    /** Command executed successfully. */
    CMD_EXECUTED("commands.executed"),
    /** Prompt to specify subcommand. */
    CMD_SPECIFY_SUBCOMMAND("commands.specify_subcommand"),
    /** Unknown subcommand provided. */
    CMD_UNKNOWN_SUBCOMMAND("commands.unknown_subcommand"),
    /** Invalid arguments supplied. */
    CMD_INVALID_ARGUMENTS("commands.invalid_arguments"),
    /** Command not found. */
    CMD_NOT_FOUND("commands.not_found"),
    /** Internal error while running command. */
    CMD_INTERNAL_ERROR("commands.internal_error"),

    // Settings command
    /** Language manager not initialised. */
    SETTINGS_LANG_NOT_INIT("settings.language_not_initialized"),
    /** Settings command received invalid args. */
    SETTINGS_INVALID_ARGS("settings.invalid_arguments"),
    /** Current language value. */
    SETTINGS_CURRENT_LANG("settings.current_language"),
    /** Available languages list. */
    SETTINGS_AVAILABLE_LANGS("settings.available_languages"),
    /** Requested language missing. */
    SETTINGS_LANG_NOT_FOUND("settings.language_not_found"),
    /** Requested key missing. */
    SETTINGS_KEY_NOT_FOUND("settings.key_not_found"),
    /** Value of a specific key. */
    SETTINGS_KEY_VALUE("settings.key_value"),
    /** Key set confirmation. */
    SETTINGS_KEY_SET("settings.key_set"),

    // Reload command
    /** All commands reloaded. */
    RELOAD_ALL_COMMANDS("reload.all_commands"),
    /** Single command reloaded. */
    RELOAD_COMMAND("reload.command"),
    /** All sections reloaded. */
    RELOAD_ALL_SECTIONS("reload.all_sections"),
    /** Single section reloaded. */
    RELOAD_SECTION("reload.section"),
    /** All global settings reloaded. */
    RELOAD_GLOBAL_SETTINGS("reload.global_settings"),
    /** Single global setting reloaded. */
    RELOAD_GLOBAL_SETTING("reload.global_setting"),

    // System messages
    /** Language file loaded. */
    SYS_LOADED_LANGUAGE("system.loaded_language"),
    /** Failed to load language file. */
    SYS_FAILED_LOAD_LANGUAGE("system.failed_load_language"),
    /** Failed to save custom messages. */
    SYS_FAILED_SAVE_MESSAGES("system.failed_save_messages"),
    /** Created default config file. */
    SYS_CREATED_DEFAULT_CONFIG("system.created_default_config"),
    /** Section cannot be reloaded. */
    SYS_SECTION_NOT_RELOADABLE("system.section_not_reloadable"),
    /** Command registered. */
    SYS_COMMAND_REGISTERED("system.command_registered"),
    /** Command usage line. */
    SYS_COMMAND_USAGE("system.command_usage"),
    /** Subcommand usage list. */
    SYS_SUBCOMMAND_USAGES("system.subcommand_usages"),
    /** Alias registered. */
    SYS_ALIAS_REGISTERED("system.alias_registered"),
    /** Alias usage line. */
    SYS_ALIAS_USAGE("system.alias_usage"),
    /** Generated permissions notice. */
    SYS_GENERATED_PERMISSIONS("system.generated_permissions"),
    /** Command unregistered. */
    SYS_UNREGISTERED_COMMAND("system.unregistered_command"),

    // Error messages
    /** Message must be set before sending. */
    ERR_MESSAGE_NOT_SET("errors.message_not_set"),
    /** Failed to obtain CommandMap. */
    ERR_FAILED_GET_COMMANDMAP("errors.failed_get_commandmap"),
    /** Command registry not initialised. */
    ERR_REGISTRY_NOT_INITIALIZED("errors.registry_not_initialized"),
    /** CommandMap unavailable. */
    ERR_COMMANDMAP_NOT_AVAILABLE("errors.commandmap_not_available"),
    /** Missing @CommandInfo annotation. */
    ERR_MISSING_COMMANDINFO("errors.missing_commandinfo"),
    /** Missing @ConfigFile annotation. */
    ERR_MISSING_CONFIGFILE("errors.missing_configfile"),
    /** Required config value absent. */
    ERR_REQUIRED_CONFIG_MISSING("errors.required_config_missing");

    private static final Map<String, String> DEFAULT_MESSAGES = LanguageDefaults.englishTranslations();

    private final String key;

    InternalMessages(String key) {
        this.key = key;
    }

    /**
     * Fully qualified message key (magicutils.*).
     *
     * @return full translation key
     */
    public String getKey() {
        return "magicutils." + key;
    }

    /**
     * Get localized value or default.
     *
     * @return translated text or default message
     */
    public String get() {
        LanguageManager manager = Messages.getLanguageManager();
        if (manager != null) {
            String message = manager.getMessage(getKey());
            if (!message.equals(getKey())) {
                return message;
            }
        }
        return getDefaultMessage();
    }

    /**
     * Get localized value with replacements or default.
     *
     * @param replacements placeholder/value pairs
     * @return translated text with replacements or default message
     */
    public String get(String... replacements) {
        LanguageManager manager = Messages.getLanguageManager();
        if (manager != null) {
            String message = manager.getMessage(getKey(), replacements);
            if (!message.equals(getKey())) {
                return message;
            }
        }
        return applyPlaceholders(getDefaultMessage(), replacements);
    }

    private String getDefaultMessage() {
        return DEFAULT_MESSAGES.getOrDefault(getKey(), getKey());
    }

    private static String applyPlaceholders(String message, String... replacements) {
        if (message == null || replacements == null) {
            return message;
        }
        String result = message;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String placeholder = replacements[i];
            String value = replacements[i + 1];
            result = result.replace("{" + placeholder + "}", value);
        }
        return result;
    }
}
