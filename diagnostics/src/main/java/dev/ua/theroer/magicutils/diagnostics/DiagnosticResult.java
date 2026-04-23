package dev.ua.theroer.magicutils.diagnostics;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of a single diagnostic check.
 *
 * @param checkId check identifier
 * @param suite suite identifier
 * @param status result status
 * @param severity failure importance
 * @param message human-readable message
 * @param duration measured duration
 * @param details structured details
 * @param error captured error, when present
 */
public record DiagnosticResult(
        String checkId,
        String suite,
        DiagnosticStatus status,
        DiagnosticSeverity severity,
        String message,
        Duration duration,
        Map<String, Object> details,
        @Nullable Throwable error
) {
    /**
     * Creates a normalized result record.
     *
     * @param checkId check identifier
     * @param suite suite identifier
     * @param status result status
     * @param severity failure importance
     * @param message human-readable message
     * @param duration measured duration
     * @param details structured details
     * @param error captured error, when present
     */
    public DiagnosticResult {
        checkId = normalizeText(checkId, "unknown.check");
        suite = normalizeText(suite, "general");
        status = status != null ? status : DiagnosticStatus.FAIL;
        severity = severity != null ? severity : DiagnosticSeverity.WARNING;
        message = message != null ? message : "";
        duration = duration != null ? duration : Duration.ZERO;
        details = details != null
                ? Map.copyOf(new LinkedHashMap<>(details))
                : Map.of();
    }

    /**
     * Creates an OK result.
     *
     * @param checkId check identifier
     * @param suite suite identifier
     * @param severity failure importance
     * @param message result message
     * @param details structured details
     * @return OK result
     */
    public static DiagnosticResult ok(
            String checkId,
            String suite,
            DiagnosticSeverity severity,
            String message,
            Map<String, Object> details
    ) {
        return new DiagnosticResult(checkId, suite, DiagnosticStatus.OK, severity, message, Duration.ZERO, details, null);
    }

    /**
     * Creates a WARN result.
     *
     * @param checkId check identifier
     * @param suite suite identifier
     * @param severity failure importance
     * @param message result message
     * @param details structured details
     * @return WARN result
     */
    public static DiagnosticResult warn(
            String checkId,
            String suite,
            DiagnosticSeverity severity,
            String message,
            Map<String, Object> details
    ) {
        return new DiagnosticResult(checkId, suite, DiagnosticStatus.WARN, severity, message, Duration.ZERO, details, null);
    }

    /**
     * Creates a FAIL result.
     *
     * @param checkId check identifier
     * @param suite suite identifier
     * @param severity failure importance
     * @param message result message
     * @param details structured details
     * @param error captured error
     * @return FAIL result
     */
    public static DiagnosticResult fail(
            String checkId,
            String suite,
            DiagnosticSeverity severity,
            String message,
            Map<String, Object> details,
            @Nullable Throwable error
    ) {
        return new DiagnosticResult(checkId, suite, DiagnosticStatus.FAIL, severity, message, Duration.ZERO, details, error);
    }

    /**
     * Creates a SKIPPED result.
     *
     * @param checkId check identifier
     * @param suite suite identifier
     * @param severity failure importance
     * @param message result message
     * @param details structured details
     * @return SKIPPED result
     */
    public static DiagnosticResult skipped(
            String checkId,
            String suite,
            DiagnosticSeverity severity,
            String message,
            Map<String, Object> details
    ) {
        return new DiagnosticResult(checkId, suite, DiagnosticStatus.SKIPPED, severity, message, Duration.ZERO, details, null);
    }

    /**
     * Returns a copy with the measured duration attached.
     *
     * @param duration measured duration
     * @return updated result
     */
    public DiagnosticResult withDuration(Duration duration) {
        return new DiagnosticResult(checkId, suite, status, severity, message, duration, details, error);
    }

    private static String normalizeText(@Nullable String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
