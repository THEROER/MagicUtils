package dev.ua.theroer.magicutils.diagnostics;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticReportPublishableTest {

    @Test
    void reportWithoutFailuresIsPublishableAtAnyThreshold() {
        DiagnosticReport report = report(
                DiagnosticResult.ok("check.ok", "suite", DiagnosticSeverity.CRITICAL, "ok", Map.of()),
                DiagnosticResult.skipped("check.skipped", "suite", DiagnosticSeverity.WARNING, "skipped", Map.of())
        );

        assertTrue(report.isPublishable());
        assertTrue(report.isPublishable(DiagnosticSeverity.CRITICAL));
        assertTrue(report.isPublishable(DiagnosticSeverity.WARNING));
        assertTrue(report.isPublishable(DiagnosticSeverity.INFO));
        assertTrue(report.blockingResults(DiagnosticSeverity.INFO).isEmpty());
    }

    @Test
    void criticalFailBlocksAtEveryThreshold() {
        DiagnosticReport report = report(
                DiagnosticResult.fail("check.critical", "suite", DiagnosticSeverity.CRITICAL, "boom", Map.of(), null)
        );

        assertFalse(report.isPublishable());
        assertFalse(report.isPublishable(DiagnosticSeverity.CRITICAL));
        assertFalse(report.isPublishable(DiagnosticSeverity.WARNING));
        assertFalse(report.isPublishable(DiagnosticSeverity.INFO));
        assertEquals(1, report.blockingResults(DiagnosticSeverity.CRITICAL).size());
        assertEquals("check.critical", report.blockingResults(DiagnosticSeverity.CRITICAL).get(0).checkId());
    }

    @Test
    void warningFailBlocksOnlyBelowCriticalThreshold() {
        DiagnosticReport report = report(
                DiagnosticResult.fail("check.warning", "suite", DiagnosticSeverity.WARNING, "warn-fail", Map.of(), null)
        );

        assertTrue(report.isPublishable());
        assertTrue(report.isPublishable(DiagnosticSeverity.CRITICAL));
        assertFalse(report.isPublishable(DiagnosticSeverity.WARNING));
        assertFalse(report.isPublishable(DiagnosticSeverity.INFO));
        assertTrue(report.blockingResults(DiagnosticSeverity.CRITICAL).isEmpty());
        assertEquals(1, report.blockingResults(DiagnosticSeverity.WARNING).size());
    }

    @Test
    void warnStatusDoesNotBlockEvenWithCriticalSeverity() {
        DiagnosticReport report = report(
                DiagnosticResult.warn("check.warnStatus", "suite", DiagnosticSeverity.CRITICAL, "warning", Map.of())
        );

        assertTrue(report.isPublishable());
        assertTrue(report.isPublishable(DiagnosticSeverity.INFO));
        assertTrue(report.blockingResults(DiagnosticSeverity.INFO).isEmpty());
    }

    @Test
    void nullThresholdFallsBackToCriticalDefault() {
        DiagnosticReport report = report(
                DiagnosticResult.fail("check.warning", "suite", DiagnosticSeverity.WARNING, "warn-fail", Map.of(), null)
        );

        assertTrue(report.isPublishable(null));
        assertTrue(report.blockingResults(null).isEmpty());
    }

    @Test
    void mixedReportReportsExactlyTheBlockingFailures() {
        DiagnosticReport report = report(
                DiagnosticResult.ok("check.ok", "suite", DiagnosticSeverity.INFO, "ok", Map.of()),
                DiagnosticResult.warn("check.warn", "suite", DiagnosticSeverity.CRITICAL, "warn", Map.of()),
                DiagnosticResult.fail("check.info.fail", "suite", DiagnosticSeverity.INFO, "info-fail", Map.of(), null),
                DiagnosticResult.fail("check.warning.fail", "suite", DiagnosticSeverity.WARNING, "warn-fail", Map.of(), null),
                DiagnosticResult.fail("check.critical.fail", "suite", DiagnosticSeverity.CRITICAL, "crit-fail", Map.of(), null),
                DiagnosticResult.skipped("check.skipped", "suite", DiagnosticSeverity.CRITICAL, "skipped", Map.of())
        );

        // Default CRITICAL threshold: only the CRITICAL fail blocks.
        assertFalse(report.isPublishable());
        assertEquals(1, report.blockingResults(DiagnosticSeverity.CRITICAL).size());
        assertEquals("check.critical.fail", report.blockingResults(DiagnosticSeverity.CRITICAL).get(0).checkId());

        // WARNING threshold: WARNING and CRITICAL fails block.
        assertFalse(report.isPublishable(DiagnosticSeverity.WARNING));
        assertEquals(2, report.blockingResults(DiagnosticSeverity.WARNING).size());

        // INFO threshold: all three fails block; the WARN status stays out.
        assertFalse(report.isPublishable(DiagnosticSeverity.INFO));
        assertEquals(3, report.blockingResults(DiagnosticSeverity.INFO).size());
    }

    private static DiagnosticReport report(DiagnosticResult... results) {
        return new DiagnosticReport(
                "MagicUtils",
                DiagnosticMode.SAFE,
                Instant.EPOCH,
                Duration.ofMillis(1),
                List.of(results)
        );
    }
}
