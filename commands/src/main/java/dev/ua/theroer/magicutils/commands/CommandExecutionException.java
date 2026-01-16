package dev.ua.theroer.magicutils.commands;

/**
 * Signals that a command failed with an unexpected runtime error.
 */
public class CommandExecutionException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * User-facing error message.
     */
    private final String userMessage;

    /**
     * Creates a new execution exception with a user-facing message.
     *
     * @param userMessage message intended for the command sender
     * @param cause underlying failure
     */
    public CommandExecutionException(String userMessage, Throwable cause) {
        super(cause);
        this.userMessage = userMessage;
    }

    /**
     * Returns the message intended for the command sender.
     *
     * @return user-facing error message
     */
    public String getUserMessage() {
        return userMessage;
    }
}
