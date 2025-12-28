package dev.ua.theroer.magicutils;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.commands.HelpCommandBase;
import dev.ua.theroer.magicutils.commands.MagicPermissionDefault;

/**
 * Simple help command that lists all registered commands and their subcommands.
 */
@CommandInfo(
        name = "mhelp",
        description = "Shows available commands and usages",
        aliases = {"help"},
        permissionDefault = MagicPermissionDefault.TRUE
)
public class HelpCommand extends HelpCommandBase {
    /**
     * Creates the help command.
     *
     * @param logger platform logger
     */
    public HelpCommand(Logger logger) {
        super(logger != null ? logger.getCore() : null, CommandRegistry::getCommandManager);
    }
}
