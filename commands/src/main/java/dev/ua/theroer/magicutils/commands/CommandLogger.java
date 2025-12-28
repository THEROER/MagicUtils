package dev.ua.theroer.magicutils.commands;

/**
 * Lightweight logger interface for command engine diagnostics.
 */
public interface CommandLogger {
    /**
     * Logs a debug message.
     *
     * @param message log message
     */
    void debug(String message);

    /**
     * Logs an info message.
     *
     * @param message log message
     */
    void info(String message);

    /**
     * Logs a warning message.
     *
     * @param message log message
     */
    void warn(String message);

    /**
     * Logs an error message.
     *
     * @param message log message
     */
    void error(String message);

    /**
     * Logs an error message with an optional exception.
     *
     * @param message log message
     * @param throwable exception to print
     */
    default void error(String message, Throwable throwable) {
        error(message);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    /**
     * Returns a no-op logger implementation.
     *
     * @return no-op logger
     */
    static CommandLogger noop() {
        return new CommandLogger() {
            @Override
            public void debug(String message) {
            }

            @Override
            public void info(String message) {
            }

            @Override
            public void warn(String message) {
            }

            @Override
            public void error(String message) {
            }
        };
    }
}
