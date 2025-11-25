package dev.ua.theroer.magicutils.platform;

/**
 * Platform-friendly logger facade to avoid binding to a specific implementation.
 */
public interface PlatformLogger {
    void info(String message);

    void warn(String message);
    void warn(String message, Throwable throwable);

    void error(String message);

    void error(String message, Throwable throwable);

    void debug(String message);
}
