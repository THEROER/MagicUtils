package dev.ua.theroer.magicutils.commands;

import java.util.List;

import org.bukkit.command.CommandSender;

/**
 * Represents the context of a command execution, including sender and arguments.
 */
public class CommandContext {
    private final CommandSender sender;
    private final List<String> args;

    /**
     * Constructs a new CommandContext.
     * @param sender the command sender
     * @param args the command arguments
     */
    public CommandContext(CommandSender sender, List<String> args) {
        this.sender = sender;
        this.args = args;
    }

    /**
     * Gets the command sender.
     * @return the command sender
     */
    public CommandSender getSender() {
        return sender;
    }

    /**
     * Gets the command arguments.
     * @return the list of arguments
     */
    public List<String> getArgs() {
        return args;
    }
} 