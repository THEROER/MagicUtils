package dev.ua.theroer.magicutils.diagnostics;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Runtime diagnostics execution options.
 *
 * @param mode execution mode
 * @param verbose whether verbose output is enabled
 * @param timeout per-check timeout
 * @param filter optional check filter
 */
public record DiagnosticRunRequest(
        DiagnosticMode mode,
        boolean verbose,
        Duration timeout,
        @Nullable Predicate<DiagnosticCheck> filter
) {
    /**
     * Default per-check timeout.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Creates a normalized run request.
     *
     * @param mode execution mode
     * @param verbose whether verbose output is enabled
     * @param timeout per-check timeout
     * @param filter optional check filter
     */
    public DiagnosticRunRequest {
        mode = mode != null ? mode : DiagnosticMode.SAFE;
        timeout = timeout != null && !timeout.isNegative() && !timeout.isZero() ? timeout : DEFAULT_TIMEOUT;
    }

    /**
     * Safe-mode request with defaults.
     *
     * @return SAFE request
     */
    public static DiagnosticRunRequest safe() {
        return new DiagnosticRunRequest(DiagnosticMode.SAFE, false, DEFAULT_TIMEOUT, null);
    }

    /**
     * Standard-mode request with defaults.
     *
     * @return STANDARD request
     */
    public static DiagnosticRunRequest standard() {
        return new DiagnosticRunRequest(DiagnosticMode.STANDARD, false, DEFAULT_TIMEOUT, null);
    }

    /**
     * Returns a copy with a filter applied.
     *
     * @param filter filter to apply
     * @return updated request
     */
    public DiagnosticRunRequest withFilter(@Nullable Predicate<DiagnosticCheck> filter) {
        if (filter == null) {
            return new DiagnosticRunRequest(mode, verbose, timeout, this.filter);
        }
        Predicate<DiagnosticCheck> combined = this.filter != null
                ? this.filter.and(filter)
                : filter;
        return new DiagnosticRunRequest(mode, verbose, timeout, combined);
    }

    /**
     * Returns true when a check should be included.
     *
     * @param check candidate check
     * @return true when included
     */
    public boolean includes(DiagnosticCheck check) {
        Objects.requireNonNull(check, "check");
        return filter == null || filter.test(check);
    }
}
