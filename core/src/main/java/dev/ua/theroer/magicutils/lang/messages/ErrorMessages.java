package dev.ua.theroer.magicutils.lang.messages;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.annotations.DefaultValue;
import lombok.Getter;

/**
 * Error messages
 */
@Getter
public class ErrorMessages {
    /**
     * Default constructor for ErrorMessages.
     */
    public ErrorMessages() {
    }

    @ConfigValue("message_not_set")
    @DefaultValue("Message must be set before sending")
    private String messageNotSet;

    @ConfigValue("failed_get_commandmap")
    @DefaultValue("Failed to get CommandMap")
    private String failedGetCommandMap;

    @ConfigValue("registry_not_initialized")
    @DefaultValue("CommandRegistry not initialized! Call initialize() first.")
    private String registryNotInitialized;

    @ConfigValue("commandmap_not_available")
    @DefaultValue("CommandMap not available!")
    private String commandMapNotAvailable;

    @ConfigValue("missing_commandinfo")
    @DefaultValue("Command class must have @CommandInfo annotation: {class}")
    private String missingCommandInfo;

    @ConfigValue("missing_configfile")
    @DefaultValue("Class {class} must have @ConfigFile annotation")
    private String missingConfigFile;

    @ConfigValue("required_config_missing")
    @DefaultValue("Required config value missing: {path}")
    private String requiredConfigMissing;
}
