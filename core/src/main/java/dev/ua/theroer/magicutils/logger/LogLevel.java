package dev.ua.theroer.magicutils.logger;

import lombok.Getter;

/**
 * Logging levels supported by the MagicUtils logger.
 */
@Getter
public enum LogLevel {
    /** Info log level. */
    INFO("INFO"),
    /** Warn log level. */
    WARN("WARN"),
    /** Error log level. */
    ERROR("ERROR"),
    /** Debug log level. */
    DEBUG("DEBUG"),
    /** Success log level. */
    SUCCESS("SUCCESS");

    private final String displayName;

    LogLevel(String displayName) {
        this.displayName = displayName;
    }
}
