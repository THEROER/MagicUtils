package dev.ua.theroer.magicutils.lang;

import dev.ua.theroer.magicutils.platform.Audience;
import dev.ua.theroer.magicutils.utils.MsgFmt;

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
    /** Built-in help command description. */
    CMD_HELP_COMMAND_DESCRIPTION("commands.help.command_description"),
    /** Built-in help subcommand description. */
    CMD_HELP_SUBCOMMAND_DESCRIPTION("commands.help.subcommand_description"),
    /** Help command sender missing. */
    CMD_HELP_SENDER_UNAVAILABLE("commands.help.sender_unavailable"),
    /** Help is unavailable because no manager is ready. */
    CMD_HELP_UNAVAILABLE("commands.help.unavailable"),
    /** Help target command not found. */
    CMD_HELP_COMMAND_NOT_FOUND("commands.help.command_not_found"),
    /** Help header title. */
    CMD_HELP_TITLE("commands.help.title"),
    /** Help available commands section label. */
    CMD_HELP_AVAILABLE_COMMANDS("commands.help.available_commands"),
    /** Help empty state when nothing matches. */
    CMD_HELP_NO_COMMANDS_FOUND("commands.help.no_commands_found"),
    /** Help command label. */
    CMD_HELP_LABEL_COMMAND("commands.help.label.command"),
    /** Help description label. */
    CMD_HELP_LABEL_DESCRIPTION("commands.help.label.description"),
    /** Help aliases label. */
    CMD_HELP_LABEL_ALIASES("commands.help.label.aliases"),
    /** Help subcommands label. */
    CMD_HELP_LABEL_SUBCOMMANDS("commands.help.label.subcommands"),
    /** Help arguments label. */
    CMD_HELP_LABEL_ARGUMENTS("commands.help.label.arguments"),
    /** Help optional argument marker. */
    CMD_HELP_OPTIONAL("commands.help.optional"),
    /** Help default value marker. */
    CMD_HELP_DEFAULT_VALUE("commands.help.default_value"),
    /** Help values marker. */
    CMD_HELP_VALUES("commands.help.values"),
    /** Help page label. */
    CMD_HELP_PAGE_LABEL("commands.help.page_label"),
    /** Help previous page hover. */
    CMD_HELP_NAV_PREVIOUS("commands.help.nav.previous"),
    /** Help next page hover. */
    CMD_HELP_NAV_NEXT("commands.help.nav.next"),
    /** Help search query prefix. */
    CMD_HELP_QUERY_PREFIX("commands.help.query_prefix"),
    /** Help fallback description. */
    CMD_HELP_NO_DESCRIPTION("commands.help.no_description"),
    /** Help hover text for clickable entries. */
    CMD_HELP_HOVER_SHOW("commands.help.hover.show"),
    /** Help inline aliases text. */
    CMD_HELP_ALIASES_INLINE("commands.help.aliases_inline"),
    /** Help-generated argument permission description. */
    CMD_HELP_ARGUMENT_PERMISSION("commands.help.argument_permission"),
    /** Help usage separator between direct and subcommand forms. */
    CMD_HELP_USAGE_OR("commands.help.usage_or"),

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

    private static final Map<String, String> DEFAULT_MESSAGES = BundledTranslations.getTranslations("en");

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
        return getScoped(null, null, replacements);
    }

    /**
     * Get localized value using a scoped language manager with fallback to the global manager.
     *
     * @param scope scope identifier
     * @param replacements placeholder/value pairs
     * @return translated text with replacements or default message
     */
    public String getScoped(String scope, String... replacements) {
        return getScoped(scope, null, replacements);
    }

    /**
     * Get localized value for an audience using a scoped language manager with fallback to the global manager.
     *
     * @param scope scope identifier
     * @param audience target audience
     * @return translated text or default message
     */
    public String getScoped(String scope, Audience audience) {
        return getScoped(scope, audience, new String[0]);
    }

    /**
     * Get localized value with replacements for an audience using a scoped language manager
     * with fallback to the global manager.
     *
     * @param scope scope identifier
     * @param audience target audience
     * @param replacements placeholder/value pairs
     * @return translated text with replacements or default message
     */
    public String getScoped(String scope, Audience audience, String... replacements) {
        LanguageManager manager = findLanguageManager(scope);
        if (manager != null) {
            String resolved = audience != null
                    ? manager.getMessageFor(audience, getKey())
                    : manager.getMessage(getKey());
            if (resolved != null && !resolved.equals(getKey()) && !resolved.startsWith("magicutils.")) {
                return MsgFmt.apply(resolved, (Object[]) replacements);
            }
        }
        return MsgFmt.apply(getDefaultMessage(), (Object[]) replacements);
    }

    private static LanguageManager findLanguageManager(String scope) {
        LanguageManager manager = null;
        if (scope != null && !scope.isBlank()) {
            manager = Messages.getLanguageManager(scope);
        }
        return manager != null ? manager : Messages.getLanguageManager();
    }

    private String getDefaultMessage() {
        return DEFAULT_MESSAGES.getOrDefault(getKey(), getKey());
    }
}
