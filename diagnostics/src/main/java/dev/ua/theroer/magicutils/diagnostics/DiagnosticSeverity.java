package dev.ua.theroer.magicutils.diagnostics;

/**
 * Importance of a check when it fails.
 */
public enum DiagnosticSeverity {
    /**
     * Informational or optional check.
     */
    INFO,
    /**
     * Important non-fatal problem.
     */
    WARNING,
    /**
     * Critical failure that usually blocks normal operation.
     */
    CRITICAL
}
