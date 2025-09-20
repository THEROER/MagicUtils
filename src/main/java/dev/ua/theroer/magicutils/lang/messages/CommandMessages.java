package dev.ua.theroer.magicutils.lang.messages;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.annotations.DefaultValue;
import lombok.Getter;

/**
 * Command messages
 */
@Getter
public class CommandMessages {
    /**
     * Default constructor for CommandMessages.
     */
    public CommandMessages() {
    }

    @ConfigValue("no_permission")
    @DefaultValue("&cYou don't have permission to execute this command!")
    private String noPermission;

    @ConfigValue("execution_error")
    @DefaultValue("&cAn error occurred while executing the command")
    private String executionError;

    @ConfigValue("executed")
    @DefaultValue("&aCommand executed successfully")
    private String executed;

    @ConfigValue("specify_subcommand")
    @DefaultValue("&eSpecify a subcommand: &f{subcommands}")
    private String specifySubcommand;

    @ConfigValue("unknown_subcommand")
    @DefaultValue("&cUnknown subcommand: &f{subcommand}")
    private String unknownSubcommand;

    @ConfigValue("invalid_arguments")
    @DefaultValue("&cInvalid command arguments")
    private String invalidArguments;

    @ConfigValue("not_found")
    @DefaultValue("&cCommand not found")
    private String notFound;

    @ConfigValue("internal_error")
    @DefaultValue("&cAn internal error occurred while executing the command")
    private String internalError;
}
