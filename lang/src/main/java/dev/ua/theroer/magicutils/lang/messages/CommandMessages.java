package dev.ua.theroer.magicutils.lang.messages;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import lombok.Getter;

/**
 * Command messages. Defaults are loaded from bundled {@code lang/<code>.json}
 * resources via {@link dev.ua.theroer.magicutils.lang.BundledTranslations}.
 */
@Getter
public class CommandMessages {
    /**
     * Default constructor for CommandMessages.
     */
    public CommandMessages() {
    }

    @ConfigValue("no_permission")
    private String noPermission;

    @ConfigValue("execution_error")
    private String executionError;

    @ConfigValue("executed")
    private String executed;

    @ConfigValue("specify_subcommand")
    private String specifySubcommand;

    @ConfigValue("unknown_subcommand")
    private String unknownSubcommand;

    @ConfigValue("invalid_arguments")
    private String invalidArguments;

    @ConfigValue("not_found")
    private String notFound;

    @ConfigValue("internal_error")
    private String internalError;
}
