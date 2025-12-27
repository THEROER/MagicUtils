package dev.ua.theroer.magicutils;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.OptionalArgument;
import dev.ua.theroer.magicutils.annotations.Suggest;
import dev.ua.theroer.magicutils.commands.CommandManager;
import dev.ua.theroer.magicutils.commands.CommandRegistry;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.HelpCommandSupport;
import dev.ua.theroer.magicutils.commands.MagicCommand;
import dev.ua.theroer.magicutils.commands.MagicPermissionDefault;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Simple help command that lists all registered commands and their subcommands.
 */
@CommandInfo(
        name = "mhelp",
        description = "Shows available commands and usages",
        aliases = {"help"},
        permissionDefault = MagicPermissionDefault.TRUE
)
public class HelpCommand extends MagicCommand {
    private final Logger logger;

    /** Default constructor. */
    public HelpCommand(Logger logger) {
        this.logger = logger;
    }

    /**
     * Execute help: list commands or details for a specific one.
     *
     * @param source command source
     * @param commandName optional command name
     * @param subCommand optional subcommand
     * @return result
     */
    public CommandResult execute(ServerCommandSource source,
                                 @OptionalArgument @Suggest("getCommandSuggestions") String commandName,
                                 @OptionalArgument String subCommand) {
        CommandManager<ServerCommandSource> manager = CommandRegistry.getCommandManager();
        HelpCommandSupport.HelpResult result = HelpCommandSupport.build(manager, commandName, subCommand);
        if (!result.success()) {
            return CommandResult.failure(result.errorMessage());
        }

        for (String line : result.lines()) {
            logger.noPrefix().to(source).send(line);
        }
        return CommandResult.success(false, "");
    }

    /**
     * Suggestions for command names.
     *
     * @return array of command/alias names
     */
    public String[] getCommandSuggestions() {
        return HelpCommandSupport.getCommandSuggestions(CommandRegistry.getCommandManager());
    }
}
