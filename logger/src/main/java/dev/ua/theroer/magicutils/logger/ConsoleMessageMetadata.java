package dev.ua.theroer.magicutils.logger;

import org.jetbrains.annotations.Nullable;

/**
 * Structured console metadata used to preserve logger routing without parsing plain text.
 * 
 * @param level the log level of the message
 * @param subLoggerName the name of the sub-logger, or null for the root logger
 */
public record ConsoleMessageMetadata(
        LogLevel level,
        @Nullable String subLoggerName
) {
}
