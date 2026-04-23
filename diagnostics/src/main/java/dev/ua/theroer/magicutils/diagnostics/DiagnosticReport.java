package dev.ua.theroer.magicutils.diagnostics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Aggregated runtime diagnostics report.
 *
 * @param runtimeName logical runtime name
 * @param mode execution mode
 * @param startedAt start timestamp
 * @param duration total duration
 * @param results collected results
 */
public record DiagnosticReport(
        String runtimeName,
        DiagnosticMode mode,
        Instant startedAt,
        Duration duration,
        Map<String, Object> technical,
        List<DiagnosticResult> results
) {
    public DiagnosticReport(
            String runtimeName,
            DiagnosticMode mode,
            Instant startedAt,
            Duration duration,
            List<DiagnosticResult> results
    ) {
        this(runtimeName, mode, startedAt, duration, Map.of(), results);
    }

    /**
     * Creates a normalized report.
     *
     * @param runtimeName logical runtime name
     * @param mode execution mode
     * @param startedAt start timestamp
     * @param duration total duration
     * @param results collected results
     */
    public DiagnosticReport {
        runtimeName = runtimeName != null && !runtimeName.isBlank() ? runtimeName.trim() : "MagicUtils";
        mode = mode != null ? mode : DiagnosticMode.SAFE;
        startedAt = startedAt != null ? startedAt : Instant.now();
        duration = duration != null ? duration : Duration.ZERO;
        technical = Map.copyOf(technical != null ? technical : Map.of());
        results = List.copyOf(results != null ? results : List.of());
    }

    /**
     * Count of OK results.
     *
     * @return OK count
     */
    public int okCount() {
        return count(DiagnosticStatus.OK);
    }

    /**
     * Count of WARN results.
     *
     * @return WARN count
     */
    public int warnCount() {
        return count(DiagnosticStatus.WARN);
    }

    /**
     * Count of FAIL results.
     *
     * @return FAIL count
     */
    public int failCount() {
        return count(DiagnosticStatus.FAIL);
    }

    /**
     * Count of SKIPPED results.
     *
     * @return SKIPPED count
     */
    public int skippedCount() {
        return count(DiagnosticStatus.SKIPPED);
    }

    /**
     * Returns true when at least one check failed.
     *
     * @return true when report contains failures
     */
    public boolean hasFailures() {
        return failCount() > 0;
    }

    private int count(DiagnosticStatus status) {
        int total = 0;
        for (DiagnosticResult result : results) {
            if (result.status() == status) {
                total++;
            }
        }
        return total;
    }
}
