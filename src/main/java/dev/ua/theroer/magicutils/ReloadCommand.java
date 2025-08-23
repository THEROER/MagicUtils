package dev.ua.theroer.magicutils;

import org.jetbrains.annotations.NotNull;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.DefaultValue;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import dev.ua.theroer.magicutils.annotations.Suggest;
import dev.ua.theroer.magicutils.commands.CommandResult;
import dev.ua.theroer.magicutils.commands.MagicCommand;

/**
 * Command for reloading different sections of MagicUtils.
 */
@CommandInfo(name = "reload", description = "MagicUtils reload command", permission = true, aliases = {"rl"})
public class ReloadCommand extends MagicCommand {

    /**
     * Default constructor for ReloadCommand.
     */
    public ReloadCommand() {}

    /**
     * Reloads the command section.
     * @param commandName the command name to reload
     * @return the result of the reload operation
     */
    @SubCommand(name = "command", description = "Reload the command section", permission = true)
    public CommandResult executeCommand(
        @Suggest(value = {"getCommandSuggestions", "@players", "{all,specific}"}, permission = true)
        @DefaultValue("all")
        @NotNull String commandName
    ) {
        if ("all".equals(commandName)) {
            return CommandResult.success("All commands reloaded!");
        } else {
            return CommandResult.success("Command " + commandName + " reloaded!");
        }
    }

    /**
     * Reloads the section section.
     * @param sectionName the section name to reload
     * @return the result of the reload operation
     */
    @SubCommand(name = "section", description = "Reload the section section", permission = true)
    public CommandResult executeSection(
        @Suggest(value = "getSectionSuggestions|@worlds", permission = false)
        @DefaultValue("all")
        @NotNull String sectionName
    ) {
        if ("all".equals(sectionName)) {
            return CommandResult.success("All sections reloaded!");
        } else {
            return CommandResult.success("Section " + sectionName + " reloaded!");
        }
    }

    /**
     * Reloads the global section.
     * @param globalName the global name to reload
     * @return the result of the reload operation
     */
    @SubCommand(name = "global", description = "Reload the global section", permission = true)
    public CommandResult executeGlobal(
        @Suggest("getGlobalSuggestions")
        @DefaultValue("all")
        @NotNull String globalName
    ) {
        if ("all".equals(globalName)) {
            return CommandResult.success("Global settings reloaded!");
        } else {
            return CommandResult.success("Global setting " + globalName + " reloaded!");
        }
    }

    /**
     * Gets suggestions for command names.
     * @return an array of command suggestions
     */
    public String[] getCommandSuggestions() {
        return new String[0];
    }

    /**
     * Gets suggestions for section names.
     * @return an array of section suggestions
     */
    public String[] getSectionSuggestions() {
        return new String[0];
    }
    
    /**
     * Gets suggestions for global names.
     * @return an array of global suggestions
     */
    public String[] getGlobalSuggestions() {
        return new String[0];
    }
}