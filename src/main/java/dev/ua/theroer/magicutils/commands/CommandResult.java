package dev.ua.theroer.magicutils.commands;

import lombok.Getter;

/**
 * Represents the result of a command execution, including status and message.
 */
@Getter
public class CommandResult {
    private final boolean success;
    private final String message;
    private final boolean sendMessage;
    private final boolean sendPrefix;

    /**
     * Creates a new CommandResult.
     * @param success whether the command was successful
     * @param message the result message
     * @param sendMessage whether to send the message
     * @param sendPrefix whether to send the prefix
     */
    private CommandResult(boolean success, String message, boolean sendMessage, boolean sendPrefix) {
        this.success = success;
        this.message = message;
        this.sendMessage = sendMessage;
        this.sendPrefix = sendPrefix;
    }

    /**
     * Creates a successful CommandResult with no message.
     * @return a successful CommandResult
     */
    public static CommandResult success() {
        return new CommandResult(true, "", false, false);
    }

    /**
     * Creates a successful CommandResult with a message.
     * @param message the result message
     * @return a successful CommandResult
     */
    public static CommandResult success(String message) {
        return new CommandResult(true, message, true, true);
    }

    /**
     * Creates a successful CommandResult with a message and prefix option.
     * @param message the result message
     * @param sendPrefix whether to send the prefix
     * @return a successful CommandResult
     */
    public static CommandResult success(String message, boolean sendPrefix) {
        return new CommandResult(true, message, true, sendPrefix);
    }

    /**
     * Creates a successful CommandResult with message and sendMessage option.
     * @param sendMessage whether to send the message
     * @param message the result message
     * @return a successful CommandResult
     */
    public static CommandResult success(boolean sendMessage, String message) {
        return new CommandResult(true, message, sendMessage, false);
    }

    /**
     * Creates a failed CommandResult with a message.
     * @param message the failure message
     * @return a failed CommandResult
     */
    public static CommandResult failure(String message) {
        return new CommandResult(false, message, true, true);
    }

    /**
     * Creates a CommandResult for a not found command.
     * @return a not found CommandResult
     */
    public static CommandResult notFound() {
        return new CommandResult(false, "Command not found", true, true);
    }
} 