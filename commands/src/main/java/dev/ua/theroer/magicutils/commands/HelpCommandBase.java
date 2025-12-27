package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.OptionalArgument;
import dev.ua.theroer.magicutils.annotations.Suggest;
import dev.ua.theroer.magicutils.logger.LogBuilderCore;
import dev.ua.theroer.magicutils.logger.LogLevel;
import dev.ua.theroer.magicutils.logger.LoggerCore;
import dev.ua.theroer.magicutils.logger.MessageParser;
import dev.ua.theroer.magicutils.platform.Audience;
import java.util.function.Supplier;

/**
 * Shared help command implementation for all platforms.
 */
public class HelpCommandBase extends MagicCommand {
    private final LoggerCore logger;
    private final Supplier<CommandManager<?>> managerSupplier;
    private final String helpCommand;

    public HelpCommandBase(LoggerCore logger, Supplier<CommandManager<?>> managerSupplier) {
        this(logger, managerSupplier, "mhelp");
    }

    public HelpCommandBase(LoggerCore logger, Supplier<CommandManager<?>> managerSupplier, String helpCommand) {
        this.logger = logger;
        this.managerSupplier = managerSupplier != null ? managerSupplier : () -> null;
        this.helpCommand = helpCommand;
    }

    public CommandResult execute(MagicSender sender,
                                 @OptionalArgument @Suggest("getCommandSuggestions") String commandName,
                                 @OptionalArgument String subCommand) {
        CommandManager<?> manager = managerSupplier.get();
        HelpCommandSupport.HelpResult result = HelpCommandSupport.build(
                manager, commandName, subCommand, helpCommand, logger, sender);
        if (!result.success()) {
            return CommandResult.failure(result.errorMessage());
        }

        for (String line : result.lines()) {
            sendLine(sender, line);
        }
        return CommandResult.success(false, "");
    }

    public String[] getCommandSuggestions() {
        return HelpCommandSupport.getCommandSuggestions(managerSupplier.get());
    }

    protected void sendLine(MagicSender sender, String line) {
        if (sender == null) {
            return;
        }
        Audience audience = sender.audience();
        if (logger != null) {
            new LogBuilderCore(logger, LogLevel.INFO).noPrefix().to(audience).send(line);
            return;
        }
        if (audience != null) {
            audience.send(MessageParser.parseSmart(line));
        }
    }
}
