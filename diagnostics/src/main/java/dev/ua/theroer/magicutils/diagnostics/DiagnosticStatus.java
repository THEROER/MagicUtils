package dev.ua.theroer.magicutils.diagnostics;

/**
 * Outcome of a diagnostic check.
 */
public enum DiagnosticStatus {
    /**
     * Check succeeded.
     */
    OK,
    /**
     * Check completed with a warning.
     */
    WARN,
    /**
     * Check failed.
     */
    FAIL,
    /**
     * Check was intentionally skipped.
     */
    SKIPPED
}
