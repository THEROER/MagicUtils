package dev.ua.theroer.magicutils.lang;

import java.util.Map;

/**
 * Internal messages keys for MagicUtils library (platform-agnostic).
 */
public enum InternalMessages {
    // Command messages
    CMD_NO_PERMISSION("commands.no_permission"),
    CMD_EXECUTION_ERROR("commands.execution_error"),
    CMD_EXECUTED("commands.executed"),
    CMD_SPECIFY_SUBCOMMAND("commands.specify_subcommand"),
    CMD_UNKNOWN_SUBCOMMAND("commands.unknown_subcommand"),
    CMD_INVALID_ARGUMENTS("commands.invalid_arguments"),
    CMD_NOT_FOUND("commands.not_found"),
    CMD_INTERNAL_ERROR("commands.internal_error"),

    // Settings command
    SETTINGS_LANG_NOT_INIT("settings.language_not_initialized"),
    SETTINGS_INVALID_ARGS("settings.invalid_arguments"),
    SETTINGS_CURRENT_LANG("settings.current_language"),
    SETTINGS_AVAILABLE_LANGS("settings.available_languages"),
    SETTINGS_LANG_NOT_FOUND("settings.language_not_found"),
    SETTINGS_KEY_NOT_FOUND("settings.key_not_found"),
    SETTINGS_KEY_VALUE("settings.key_value"),
    SETTINGS_KEY_SET("settings.key_set"),

    // Reload command
    RELOAD_ALL_COMMANDS("reload.all_commands"),
    RELOAD_COMMAND("reload.command"),
    RELOAD_ALL_SECTIONS("reload.all_sections"),
    RELOAD_SECTION("reload.section"),
    RELOAD_GLOBAL_SETTINGS("reload.global_settings"),
    RELOAD_GLOBAL_SETTING("reload.global_setting"),

    // System messages
    SYS_LOADED_LANGUAGE("system.loaded_language"),
    SYS_FAILED_LOAD_LANGUAGE("system.failed_load_language"),
    SYS_FAILED_SAVE_MESSAGES("system.failed_save_messages"),
    SYS_CREATED_DEFAULT_CONFIG("system.created_default_config"),
    SYS_SECTION_NOT_RELOADABLE("system.section_not_reloadable"),
    SYS_COMMAND_REGISTERED("system.command_registered"),
    SYS_COMMAND_USAGE("system.command_usage"),
    SYS_SUBCOMMAND_USAGES("system.subcommand_usages"),
    SYS_ALIAS_REGISTERED("system.alias_registered"),
    SYS_ALIAS_USAGE("system.alias_usage"),
    SYS_GENERATED_PERMISSIONS("system.generated_permissions"),
    SYS_UNREGISTERED_COMMAND("system.unregistered_command"),

    // Error messages
    ERR_MESSAGE_NOT_SET("errors.message_not_set"),
    ERR_FAILED_GET_COMMANDMAP("errors.failed_get_commandmap"),
    ERR_REGISTRY_NOT_INITIALIZED("errors.registry_not_initialized"),
    ERR_COMMANDMAP_NOT_AVAILABLE("errors.commandmap_not_available"),
    ERR_MISSING_COMMANDINFO("errors.missing_commandinfo"),
    ERR_MISSING_CONFIGFILE("errors.missing_configfile"),
    ERR_REQUIRED_CONFIG_MISSING("errors.required_config_missing");

    private static final Map<String, String> DEFAULT_MESSAGES = LanguageDefaults.englishTranslations();

    private final String key;

    InternalMessages(String key) {
        this.key = key;
    }

    /**
     * Fully qualified message key (magicutils.*).
     */
    public String getKey() {
        return "magicutils." + key;
    }

    /**
     * Get localized value or default.
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
