package dev.ua.theroer.magicutils.diagnostics;

import java.util.EnumSet;
import java.util.concurrent.CompletionStage;

/**
 * A single runtime diagnostic check.
 */
public interface DiagnosticCheck {
    /**
     * Stable identifier for this check.
     *
     * @return check id
     */
    String id();

    /**
     * Logical suite grouping.
     *
     * @return suite identifier
     */
    String suite();

    /**
     * Human-readable description.
     *
     * @return description text
     */
    String description();

    /**
     * Importance when the check fails.
     *
     * @return severity
     */
    DiagnosticSeverity severity();

    /**
     * Supported execution modes.
     *
     * @return supported modes
     */
    default EnumSet<DiagnosticMode> supportedModes() {
        return EnumSet.allOf(DiagnosticMode.class);
    }

    /**
     * Runs the check.
     *
     * @param context diagnostic context
     * @return completion stage with the result
     */
    CompletionStage<DiagnosticResult> run(DiagnosticContext context);
}
