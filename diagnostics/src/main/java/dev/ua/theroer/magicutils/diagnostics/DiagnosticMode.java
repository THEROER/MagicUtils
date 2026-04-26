package dev.ua.theroer.magicutils.diagnostics;

/**
 * Execution mode for runtime diagnostics.
 */
public enum DiagnosticMode {
    /**
     * Read-only or low-impact checks only.
     */
    SAFE,
    /**
     * Allows reversible probe actions such as temp file writes and reload probes.
     */
    STANDARD
}
