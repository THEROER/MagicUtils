package dev.ua.theroer.magicutils.lang.messages;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.annotations.DefaultValue;
import lombok.Getter;

/**
 * System messages
 */
@Getter
public class SystemMessages {

    /**
     * Default constructor for SystemMessages.
     */
    public SystemMessages() {
    }

    @ConfigValue("loaded_language")
    @DefaultValue("Loaded language: {language}")
    private String loadedLanguage;

    @ConfigValue("failed_load_language")
    @DefaultValue("Failed to load language: {language}")
    private String failedLoadLanguage;

    @ConfigValue("failed_save_messages")
    @DefaultValue("Failed to save custom messages for language: {language}")
    private String failedSaveMessages;

    @ConfigValue("created_default_config")
    @DefaultValue("Created default config: {file}")
    private String createdDefaultConfig;

    @ConfigValue("section_not_reloadable")
    @DefaultValue("Section not reloadable: {section}")
    private String sectionNotReloadable;

    @ConfigValue("command_registered")
    @DefaultValue("Successfully registered command: {command} with aliases: {aliases}")
    private String commandRegistered;

    @ConfigValue("command_usage")
    @DefaultValue("Command usage: {usage}")
    private String commandUsage;

    @ConfigValue("subcommand_usages")
    @DefaultValue("Subcommand usages:")
    private String subcommandUsages;

    @ConfigValue("alias_registered")
    @DefaultValue("Successfully registered alias: {alias} for command: {command}")
    private String aliasRegistered;

    @ConfigValue("alias_usage")
    @DefaultValue("Alias usage: {usage}")
    private String aliasUsage;

    @ConfigValue("generated_permissions")
    @DefaultValue("Generated permissions for {command}: {permissions}")
    private String generatedPermissions;

    @ConfigValue("unregistered_command")
    @DefaultValue("Unregistered command: {command}")
    private String unregisteredCommand;
}
