package dev.ua.theroer.magicutils.platform;

/**
 * Platform-friendly logger facade to avoid binding to a specific implementation.
 */
public interface PlatformLogger {
    /**
     * Log an info-level message.
     *
     * @param message text to log
     */
    void info(String message);

    /**
     * Log a warning message.
     *
     * @param message text to log
     */
    void warn(String message);

    /**
     * Log a warning with throwable.
     *
     * @param message text to log
     * @param throwable cause to attach
     */
    void warn(String message, Throwable throwable);

    /**
     * Log an error message.
     *
     * @param message text to log
     */
    void error(String message);

    /**
     * Log an error with throwable.
     *
     * @param message text to log
     * @param throwable cause to attach
     */
    void error(String message, Throwable throwable);

    /**
     * Log a debug-level message.
     *
     * @param message text to log
     */
    void debug(String message);

    /**
     * Returns whether debug output is currently enabled by the platform logger.
     *
     * @return true if debug logs would be emitted
     */
    default boolean isDebugEnabled() {
        return true;
    }

    /**
     * Returns whether trace output is currently enabled by the platform logger.
     *
     * @return true if trace logs would be emitted
     */
    default boolean isTraceEnabled() {
        return isDebugEnabled();
    }
}
