package dev.ua.theroer.magicutils.commands;

/**
 * Lightweight logger interface for command engine diagnostics.
 */
public interface CommandLogger {
    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message);

    default void error(String message, Throwable throwable) {
        error(message);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

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
