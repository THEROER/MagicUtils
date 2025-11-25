package dev.ua.theroer.magicutils.logger;

/**
 * Prefix display mode for log messages.
 */
public enum PrefixMode {
    /** No prefix. */
    NONE,
    /** Short prefix (e.g., abbreviation). */
    SHORT,
    /** Full prefix (full plugin name). */
    FULL,
    /** Custom prefix from configuration. */
    CUSTOM
}
