package dev.ua.theroer.magicutils.logger;

import org.jetbrains.annotations.Nullable;

/**
 * Structured console metadata used to preserve logger routing without parsing plain text.
 */
public record ConsoleMessageMetadata(
        LogLevel level,
        @Nullable String mainPrefixText,
        @Nullable String subLoggerName,
        @Nullable String subLoggerPrefix
) {
    /**
     * Returns a copy with the resolved main logger prefix text.
     *
     * @param prefixText rendered primary prefix
     * @return updated metadata
     */
    public ConsoleMessageMetadata withMainPrefixText(@Nullable String prefixText) {
        return new ConsoleMessageMetadata(level, prefixText, subLoggerName, subLoggerPrefix);
    }
}
