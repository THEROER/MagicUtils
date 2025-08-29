package dev.ua.theroer.magicutils.lang;

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
    
    private final String key;
    
    InternalMessages(String key) {
        this.key = key;
    }
    
    /**
     * Gets the full message key with namespace prefix.
     * @return the full message key in format "magicutils.{key}"
     */
    public String getKey() {
        return "magicutils." + key;
    }
    
    /**
     * Gets the localized message for this key.
     * First tries to get from the Messages system if available, otherwise returns the default hardcoded message.
     * @return the localized message string
     */
    public String get() {
        // First try to get from Messages (if plugin has set up localization)
        if (Messages.getLanguageManager() != null) {
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
     * First tries to get from the Messages system if available, otherwise uses the default hardcoded message.
     * @param replacements key-value pairs for placeholder replacement (key1, value1, key2, value2, ...)
     * @return the localized message string with placeholders replaced
     */
    public String get(String... replacements) {
        // First try to get from Messages (if plugin has set up localization)
        if (Messages.getLanguageManager() != null) {
            String message = Messages.getRaw(getKey(), replacements);
            if (!message.equals(getKey())) {
                return message;
            }
        }
        
        // Fallback to hardcoded defaults with replacements
        String message = getDefaultMessage();
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return message;
    }
    
    /**
     * Get default hardcoded message
     */
    private String getDefaultMessage() {
        switch (this) {
            // Command messages
            case CMD_NO_PERMISSION: return "&cYou don't have permission to execute this command!";
            case CMD_EXECUTION_ERROR: return "&cAn error occurred while executing the command";
            case CMD_EXECUTED: return "&aCommand executed successfully";
            case CMD_SPECIFY_SUBCOMMAND: return "&eSpecify a subcommand: &f{subcommands}";
            case CMD_UNKNOWN_SUBCOMMAND: return "&cUnknown subcommand: &f{subcommand}";
            case CMD_INVALID_ARGUMENTS: return "&cInvalid command arguments";
            case CMD_NOT_FOUND: return "&cCommand not found";
            case CMD_INTERNAL_ERROR: return "&cAn internal error occurred while executing the command";
            
            // Settings command
            case SETTINGS_LANG_NOT_INIT: return "&cLanguage manager not initialized!";
            case SETTINGS_INVALID_ARGS: return "&cInvalid arguments. First argument must be a language name when using 3 arguments.";
            case SETTINGS_CURRENT_LANG: return "&aCurrent language: &f{language}";
            case SETTINGS_AVAILABLE_LANGS: return "&aAvailable languages: &f{languages}";
            case SETTINGS_LANG_NOT_FOUND: return "&cLanguage '&f{language}&c' not found!";
            case SETTINGS_KEY_NOT_FOUND: return "&cKey '&f{key}&c' not found in language '&f{language}&c'";
            case SETTINGS_KEY_VALUE: return "&aLanguage: &f{language}\n&aKey: &f{key}\n&aValue: &f{value}";
            case SETTINGS_KEY_SET: return "&aSet key '&f{key}&a' to '&f{value}&a' in language '&f{language}&a'";
            
            // Reload command
            case RELOAD_ALL_COMMANDS: return "&aAll commands reloaded!";
            case RELOAD_COMMAND: return "&aCommand &f{command} &areloaded!";
            case RELOAD_ALL_SECTIONS: return "&aAll sections reloaded!";
            case RELOAD_SECTION: return "&aSection &f{section} &areloaded!";
            case RELOAD_GLOBAL_SETTINGS: return "&aGlobal settings reloaded!";
            case RELOAD_GLOBAL_SETTING: return "&aGlobal setting &f{setting} &areloaded!";
            
            // System messages
            case SYS_LOADED_LANGUAGE: return "Loaded language: {language}";
            case SYS_FAILED_LOAD_LANGUAGE: return "Failed to load language: {language}";
            case SYS_FAILED_SAVE_MESSAGES: return "Failed to save custom messages for language: {language}";
            case SYS_CREATED_DEFAULT_CONFIG: return "Created default config: {file}";
            case SYS_SECTION_NOT_RELOADABLE: return "Section not reloadable: {section}";
            case SYS_COMMAND_REGISTERED: return "Successfully registered command: {command} with aliases: {aliases}";
            case SYS_COMMAND_USAGE: return "Command usage: {usage}";
            case SYS_SUBCOMMAND_USAGES: return "Subcommand usages:";
            case SYS_ALIAS_REGISTERED: return "Successfully registered alias: {alias} for command: {command}";
            case SYS_ALIAS_USAGE: return "Alias usage: {usage}";
            case SYS_GENERATED_PERMISSIONS: return "Generated permissions for {command}: {permissions}";
            case SYS_UNREGISTERED_COMMAND: return "Unregistered command: {command}";
            
            // Error messages
            case ERR_MESSAGE_NOT_SET: return "Message must be set before sending";
            case ERR_FAILED_GET_COMMANDMAP: return "Failed to get CommandMap";
            case ERR_REGISTRY_NOT_INITIALIZED: return "CommandRegistry not initialized! Call initialize() first.";
            case ERR_COMMANDMAP_NOT_AVAILABLE: return "CommandMap not available!";
            case ERR_MISSING_COMMANDINFO: return "Command class must have @CommandInfo annotation: {class}";
            case ERR_MISSING_CONFIGFILE: return "Class {class} must have @ConfigFile annotation";
            case ERR_REQUIRED_CONFIG_MISSING: return "Required config value missing: {path}";
            
            default: return getKey();
        }
    }
}