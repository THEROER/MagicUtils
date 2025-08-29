package dev.ua.theroer.magicutils.lang.messages;

import dev.ua.theroer.magicutils.config.annotations.ConfigValue;
import dev.ua.theroer.magicutils.config.annotations.DefaultValue;

/**
 * Command messages
 */
public class CommandMessages {
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
    
    public String getMessage(String key) {
        switch (key) {
            case "no_permission": return noPermission;
            case "execution_error": return executionError;
            case "executed": return executed;
            case "specify_subcommand": return specifySubcommand;
            case "unknown_subcommand": return unknownSubcommand;
            case "invalid_arguments": return invalidArguments;
            case "not_found": return notFound;
            case "internal_error": return internalError;
            default: return null;
        }
    }
}