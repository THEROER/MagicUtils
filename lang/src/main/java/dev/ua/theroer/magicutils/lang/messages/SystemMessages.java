package dev.ua.theroer.magicutils.lang.messages;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import lombok.Getter;

/**
 * System messages. Defaults are loaded from bundled {@code lang/<code>.json}
 * resources via {@link dev.ua.theroer.magicutils.lang.BundledTranslations}.
 */
@Getter
public class SystemMessages {

    /**
     * Default constructor for SystemMessages.
     */
    public SystemMessages() {
    }

    @ConfigValue("loaded_language")
    private String loadedLanguage;

    @ConfigValue("failed_load_language")
    private String failedLoadLanguage;

    @ConfigValue("failed_save_messages")
    private String failedSaveMessages;

    @ConfigValue("created_default_config")
    private String createdDefaultConfig;

    @ConfigValue("section_not_reloadable")
    private String sectionNotReloadable;

    @ConfigValue("command_registered")
    private String commandRegistered;

    @ConfigValue("command_usage")
    private String commandUsage;

    @ConfigValue("subcommand_usages")
    private String subcommandUsages;

    @ConfigValue("alias_registered")
    private String aliasRegistered;

    @ConfigValue("alias_usage")
    private String aliasUsage;

    @ConfigValue("generated_permissions")
    private String generatedPermissions;

    @ConfigValue("unregistered_command")
    private String unregisteredCommand;
}
