package dev.ua.theroer.magicutils.lang;

import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Internal messages keys for MagicUtils library
 */
public enum InternalMessages {
    // Command messages
    /** Message key for no permission error */
    CMD_NO_PERMISSION("commands.no_permission"),
    /** Message key for command execution error */
    CMD_EXECUTION_ERROR("commands.execution_error"),
    /** Message key for successful command execution */
    CMD_EXECUTED("commands.executed"),
    /** Message key for specify subcommand prompt */
    CMD_SPECIFY_SUBCOMMAND("commands.specify_subcommand"),
    /** Message key for unknown subcommand error */
    CMD_UNKNOWN_SUBCOMMAND("commands.unknown_subcommand"),
    /** Message key for invalid arguments error */
    CMD_INVALID_ARGUMENTS("commands.invalid_arguments"),
    /** Message key for command not found error */
    CMD_NOT_FOUND("commands.not_found"),
    /** Message key for internal command error */
    CMD_INTERNAL_ERROR("commands.internal_error"),

    // Settings command
    /** Message key for language manager not initialized */
    SETTINGS_LANG_NOT_INIT("settings.language_not_initialized"),
    /** Message key for invalid settings arguments */
    SETTINGS_INVALID_ARGS("settings.invalid_arguments"),
    /** Message key for current language display */
    SETTINGS_CURRENT_LANG("settings.current_language"),
    /** Message key for available languages list */
    SETTINGS_AVAILABLE_LANGS("settings.available_languages"),
    /** Message key for language not found error */
    SETTINGS_LANG_NOT_FOUND("settings.language_not_found"),
    /** Message key for settings key not found error */
    SETTINGS_KEY_NOT_FOUND("settings.key_not_found"),
    /** Message key for displaying key value */
    SETTINGS_KEY_VALUE("settings.key_value"),
    /** Message key for key set confirmation */
    SETTINGS_KEY_SET("settings.key_set"),

    // Reload command
    /** Message key for all commands reloaded */
    RELOAD_ALL_COMMANDS("reload.all_commands"),
    /** Message key for single command reloaded */
    RELOAD_COMMAND("reload.command"),
    /** Message key for all sections reloaded */
    RELOAD_ALL_SECTIONS("reload.all_sections"),
    /** Message key for single section reloaded */
    RELOAD_SECTION("reload.section"),
    /** Message key for global settings reloaded */
    RELOAD_GLOBAL_SETTINGS("reload.global_settings"),
    /** Message key for single global setting reloaded */
    RELOAD_GLOBAL_SETTING("reload.global_setting"),

    // System messages
    /** Message key for language loaded successfully */
    SYS_LOADED_LANGUAGE("system.loaded_language"),
    /** Message key for failed language loading */
    SYS_FAILED_LOAD_LANGUAGE("system.failed_load_language"),
    /** Message key for failed message saving */
    SYS_FAILED_SAVE_MESSAGES("system.failed_save_messages"),
    /** Message key for default config creation */
    SYS_CREATED_DEFAULT_CONFIG("system.created_default_config"),
    /** Message key for non-reloadable section */
    SYS_SECTION_NOT_RELOADABLE("system.section_not_reloadable"),
    /** Message key for command registration success */
    SYS_COMMAND_REGISTERED("system.command_registered"),
    /** Message key for command usage display */
    SYS_COMMAND_USAGE("system.command_usage"),
    /** Message key for subcommand usages header */
    SYS_SUBCOMMAND_USAGES("system.subcommand_usages"),
    /** Message key for alias registration success */
    SYS_ALIAS_REGISTERED("system.alias_registered"),
    /** Message key for alias usage display */
    SYS_ALIAS_USAGE("system.alias_usage"),
    /** Message key for generated permissions display */
    SYS_GENERATED_PERMISSIONS("system.generated_permissions"),
    /** Message key for command unregistration */
    SYS_UNREGISTERED_COMMAND("system.unregistered_command"),

    // Error messages
    /** Message key for message not set error */
    ERR_MESSAGE_NOT_SET("errors.message_not_set"),
    /** Message key for failed CommandMap retrieval */
    ERR_FAILED_GET_COMMANDMAP("errors.failed_get_commandmap"),
    /** Message key for registry not initialized error */
    ERR_REGISTRY_NOT_INITIALIZED("errors.registry_not_initialized"),
    /** Message key for CommandMap not available error */
    ERR_COMMANDMAP_NOT_AVAILABLE("errors.commandmap_not_available"),
    /** Message key for missing CommandInfo annotation */
    ERR_MISSING_COMMANDINFO("errors.missing_commandinfo"),
    /** Message key for missing ConfigFile annotation */
    ERR_MISSING_CONFIGFILE("errors.missing_configfile"),
    /** Message key for required config value missing */
    ERR_REQUIRED_CONFIG_MISSING("errors.required_config_missing");

    private static final Map<String, String> DEFAULT_MESSAGES = LanguageDefaults.englishTranslations();

    private final String key;

    InternalMessages(String key) {
        this.key = key;
    }

    /**
     * Gets the full message key with namespace prefix.
     * 
     * @return the full message key in format "magicutils.{key}"
     */
    public String getKey() {
        return "magicutils." + key;
    }

    /**
     * Gets the localized message for this key.
     * First tries to get from the Messages system if available, otherwise returns
     * the default hardcoded message.
     * 
     * @return the localized message string
     */
    public String get() {
        // First try to get from Messages (if plugin has set up localization)
        LanguageManager manager = Messages.getLanguageManager();
        if (manager != null) {
            String message = Messages.getRaw(getKey());
            if (!message.equals(getKey())) {
                return message;
            }
        }

        // Fallback to hardcoded defaults
        return getDefaultMessage();
    }

    /**
     * Gets the localized message for this key with placeholder replacements.
     * First tries to get from the Messages system if available, otherwise uses the
     * default hardcoded message.
     * 
     * @param replacements key-value pairs for placeholder replacement (key1,
     *                     value1, key2, value2, ...)
     * @return the localized message string with placeholders replaced
     */
    public String get(String... replacements) {
        // First try to get from Messages (if plugin has set up localization)
        LanguageManager manager = Messages.getLanguageManager();
        if (manager != null) {
            String message = Messages.getRaw(getKey(), replacements);
            if (!message.equals(getKey())) {
                return message;
            }
        }

        // Fallback to hardcoded defaults with replacements
        return applyPlaceholders(getDefaultMessage(), replacements);
    }

    /**
     * Gets the localized message for the given sender using their preferred language.
     *
     * @param sender command sender instance
     * @return resolved message string
     */
    public String get(CommandSender sender) {
        LanguageManager manager = Messages.getLanguageManager();
        if (manager != null) {
            String message = Messages.getRaw(sender, getKey());
            if (!message.equals(getKey())) {
                return message;
            }
        }
        return getDefaultMessage();
    }

    /**
     * Gets the localized message for the given sender using their preferred language
     * and applies replacements if necessary.
     *
     * @param sender        command sender instance
     * @param replacements placeholder-value pairs
     * @return resolved message string
     */
    public String get(CommandSender sender, String... replacements) {
        LanguageManager manager = Messages.getLanguageManager();
        if (manager != null) {
            String message = Messages.getRaw(sender, getKey(), replacements);
            if (!message.equals(getKey())) {
                return message;
            }
        }
        return applyPlaceholders(getDefaultMessage(), replacements);
    }

    /**
     * Get default hardcoded message
     */
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
