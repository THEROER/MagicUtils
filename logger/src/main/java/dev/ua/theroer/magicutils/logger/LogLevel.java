package dev.ua.theroer.magicutils.logger;

import lombok.Getter;

/**
 * Logging levels supported by the MagicUtils logger.
 */
@Getter
public enum LogLevel {
    /** Success log level. */
    SUCCESS("SUCCESS"),
    /** Info log level. */
    INFO("INFO"),
    /** Warn log level. */
    WARN("WARN"),
    /** Error log level. */
    ERROR("ERROR"),
    /** Debug log level. */
    DEBUG("DEBUG"),
    /** Trace log leve */
    TRACE("TRACE");

    private final String displayName;

    LogLevel(String displayName) {
        this.displayName = displayName;
    }
}
