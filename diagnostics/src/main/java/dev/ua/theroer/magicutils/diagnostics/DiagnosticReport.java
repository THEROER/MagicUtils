package dev.ua.theroer.magicutils.diagnostics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

    /**
     * Returns the publish-readiness verdict using the default severity threshold
     * ({@link DiagnosticSeverity#CRITICAL}).
     *
     * <p>The report is publishable when no check with status {@code FAIL} carries a
     * severity greater than or equal to {@link DiagnosticSeverity#CRITICAL}.
     *
     * @return true when the report is ready for publication
     */
    public boolean isPublishable() {
        return isPublishable(DiagnosticSeverity.CRITICAL);
    }

    /**
     * Returns the publish-readiness verdict using a custom severity threshold.
     *
     * <p>The report is publishable when it contains no result whose status is
     * {@link DiagnosticStatus#FAIL} and whose severity is greater than or equal to
     * the given {@code threshold} (compared by enum order:
     * {@code INFO < WARNING < CRITICAL}). Non-{@code FAIL} statuses (including
     * {@code WARN}) never block publication.
     *
     * @param threshold minimal blocking severity; defaults to
     *                  {@link DiagnosticSeverity#CRITICAL} when {@code null}
     * @return true when the report is ready for publication
     */
    public boolean isPublishable(DiagnosticSeverity threshold) {
        return blockingResults(threshold).isEmpty();
    }

    /**
     * Returns the results that block publication for the given severity threshold.
     *
     * <p>A result blocks publication when its status is {@link DiagnosticStatus#FAIL}
     * and its severity is greater than or equal to the {@code threshold} (compared by
     * enum order: {@code INFO < WARNING < CRITICAL}).
     *
     * @param threshold minimal blocking severity; defaults to
     *                  {@link DiagnosticSeverity#CRITICAL} when {@code null}
     * @return immutable list of blocking results, empty when publishable
     */
    public List<DiagnosticResult> blockingResults(DiagnosticSeverity threshold) {
        DiagnosticSeverity effective = threshold != null ? threshold : DiagnosticSeverity.CRITICAL;
        List<DiagnosticResult> blocking = new ArrayList<>();
        for (DiagnosticResult result : results) {
            if (result.status() == DiagnosticStatus.FAIL
                    && result.severity().compareTo(effective) >= 0) {
                blocking.add(result);
            }
        }
        return List.copyOf(blocking);
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
