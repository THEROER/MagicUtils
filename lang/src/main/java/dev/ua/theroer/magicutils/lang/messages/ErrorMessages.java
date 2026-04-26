package dev.ua.theroer.magicutils.lang.messages;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import lombok.Getter;

/**
 * Error messages. Defaults are loaded from bundled {@code lang/<code>.json}
 * resources via {@link dev.ua.theroer.magicutils.lang.BundledTranslations}.
 */
@Getter
public class ErrorMessages {
    /**
     * Default constructor for ErrorMessages.
     */
    public ErrorMessages() {
    }

    @ConfigValue("message_not_set")
    private String messageNotSet;

    @ConfigValue("failed_get_commandmap")
    private String failedGetCommandMap;

    @ConfigValue("registry_not_initialized")
    private String registryNotInitialized;

    @ConfigValue("commandmap_not_available")
    private String commandMapNotAvailable;

    @ConfigValue("missing_commandinfo")
    private String missingCommandInfo;

    @ConfigValue("missing_configfile")
    private String missingConfigFile;

    @ConfigValue("required_config_missing")
    private String requiredConfigMissing;
}
