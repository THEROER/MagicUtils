package dev.ua.theroer.magicutils;

import org.jetbrains.annotations.NotNull;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.DefaultValue;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import dev.ua.theroer.magicutils.annotations.Suggest;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.MagicCommand;
import dev.ua.theroer.magicutils.lang.InternalMessages;

/**
 * Command for reloading different sections of MagicUtils.
 */
@CommandInfo(name = "reload", description = "MagicUtils reload command", permission = "commands.reload.use", aliases = { "rl" })
public class ReloadCommand extends MagicCommand {

    /**
     * Default constructor for ReloadCommand.
     */
    public ReloadCommand() {
    }

    /**
     * Reloads the command section.
     * 
     * @param commandName the command name to reload
     * @return the result of the reload operation
     */
    @SubCommand(name = "command", description = "Reload the command section", permission = "commands.reload.command.use")
    public CommandResult executeCommand(
            @Suggest(value = { "getCommandSuggestions", "@players",
                    "{all,specific}" }, permission = true) @DefaultValue("all") @NotNull String commandName) {
        if ("all".equals(commandName)) {
            return CommandResult.success(InternalMessages.RELOAD_ALL_COMMANDS.get());
        } else {
            return CommandResult.success(InternalMessages.RELOAD_COMMAND.get("command", commandName));
        }
    }

    /**
     * Reloads the section section.
     * 
     * @param sectionName the section name to reload
     * @return the result of the reload operation
     */
    @SubCommand(name = "section", description = "Reload the section section", permission = "commands.reload.section.use")
    public CommandResult executeSection(
            @Suggest(value = "getSectionSuggestions|@worlds", permission = false) @DefaultValue("all") @NotNull String sectionName) {
        if ("all".equals(sectionName)) {
            return CommandResult.success(InternalMessages.RELOAD_ALL_SECTIONS.get());
        } else {
            return CommandResult.success(InternalMessages.RELOAD_SECTION.get("section", sectionName));
        }
    }

    /**
     * Reloads the global section.
     * 
     * @param globalName the global name to reload
     * @return the result of the reload operation
     */
    @SubCommand(name = "global", description = "Reload the global section", permission = "commands.reload.global.use")
    public CommandResult executeGlobal(
            @Suggest("getGlobalSuggestions") @DefaultValue("all") @NotNull String globalName) {
        if ("all".equals(globalName)) {
            return CommandResult.success(InternalMessages.RELOAD_GLOBAL_SETTINGS.get());
        } else {
            return CommandResult.success(InternalMessages.RELOAD_GLOBAL_SETTING.get("setting", globalName));
        }
    }

    /**
     * Gets suggestions for command names.
     * 
     * @return an array of command suggestions
     */
    public String[] getCommandSuggestions() {
        return new String[0];
    }

    /**
     * Gets suggestions for section names.
     * 
     * @return an array of section suggestions
     */
    public String[] getSectionSuggestions() {
        return new String[0];
    }

    /**
     * Gets suggestions for global names.
     * 
     * @return an array of global suggestions
     */
    public String[] getGlobalSuggestions() {
        return new String[0];
    }
}
