package dev.ua.theroer.magicutils.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;
import org.jetbrains.annotations.Nullable;

import java.io.UncheckedIOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Rendering and export helpers for diagnostics reports.
 */
public final class DiagnosticReports {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_SECTION_RESULTS = 5;

    private DiagnosticReports() {
    }

    /**
     * Renders a report as chat-ready text lines.
     *
     * @param report report to render
     * @return output lines
     */
    public static List<String> renderText(DiagnosticReport report) {
        Objects.requireNonNull(report, "report");
        List<String> lines = new ArrayList<>();
        lines.add(headerLine(report));
        lines.add(summaryLine(report));

        List<DiagnosticResult> failures = resultsByStatus(report, DiagnosticStatus.FAIL);
        List<DiagnosticResult> warnings = resultsByStatus(report, DiagnosticStatus.WARN);
        List<DiagnosticResult> skipped = resultsByStatus(report, DiagnosticStatus.SKIPPED);
        int nonOkCount = failures.size() + warnings.size() + skipped.size();

        appendSuiteSummary(lines, report, nonOkCount > 0);
        if (nonOkCount <= 0) {
            lines.add("<green>No warnings, failures, or skipped checks.</green> <gray>All checks passed.</gray>");
            return List.copyOf(lines);
        }
        if (report.okCount() > 0) {
            lines.add("<dark_gray>Showing non-OK checks only.</dark_gray> <gray>Hidden OK checks:</gray> <green>"
                    + report.okCount()
                    + "</green>");
        }
        appendSection(lines, "Failures", failures);
        appendSection(lines, "Warnings", warnings);
        appendSection(lines, "Skipped", skipped);
        return List.copyOf(lines);
    }

    public static List<String> renderVerboseText(DiagnosticReport report) {
        Objects.requireNonNull(report, "report");
        List<String> lines = new ArrayList<>();
        lines.add(headerLine(report));
        for (DiagnosticResult result : report.results()) {
            lines.add(statusTag(result.status())
                    + " <white>"
                    + escapeText(result.checkId())
                    + "</white> <dark_gray>-</dark_gray> <gray>"
                    + escapeText(sanitizeMessage(result.message()))
                    + "</gray>");
        }
        lines.add(summaryLine(report));
        return List.copyOf(lines);
    }

    /**
     * Builds the summary line for a report.
     *
     * @param report report to summarize
     * @return summary line
     */
    public static String summaryLine(DiagnosticReport report) {
        Objects.requireNonNull(report, "report");
        return "<aqua>Summary:</aqua> "
                + "<green>" + report.okCount() + " OK</green><dark_gray>, </dark_gray>"
                + "<yellow>" + report.warnCount() + " WARN</yellow><dark_gray>, </dark_gray>"
                + "<red>" + report.failCount() + " FAIL</red><dark_gray>, </dark_gray>"
                + "<gray>" + report.skippedCount() + " SKIPPED</gray><dark_gray> in </dark_gray>"
                + "<white>" + escapeText(formatDuration(report.duration())) + "</white>";
    }

    private static String headerLine(DiagnosticReport report) {
        return "<dark_gray>[</dark_gray><aqua>Diagnostics</aqua><dark_gray>]</dark_gray> <white>"
                + escapeText(report.runtimeName())
                + "</white> <dark_gray>("
                + escapeText(report.mode().name())
                + ")</dark_gray>";
    }

    /**
     * Default JSON export path for a runtime.
     *
     * @param runtime runtime container
     * @return default export path
     */
    public static Path defaultExportPath(MagicRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        return runtime.platform().configDir().resolve("diagnostics").resolve("latest.json");
    }

    /**
     * Writes a report as pretty-printed JSON.
     *
     * @param report report to export
     * @param path destination path
     * @return written path
     */
    public static Path writeJson(DiagnosticReport report, Path path) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(path, "path");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), toJsonMap(report));
            return path;
        } catch (java.io.IOException error) {
            throw new UncheckedIOException("Failed to export diagnostics report to " + path, error);
        }
    }

    /**
     * Sanitizes arbitrary text for report output.
     *
     * @param message raw message
     * @return sanitized message
     */
    public static String sanitizeMessage(@Nullable String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return message.replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
    }

    private static Map<String, Object> toJsonMap(DiagnosticReport report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("runtimeName", report.runtimeName());
        root.put("mode", report.mode().name());
        root.put("startedAt", report.startedAt().toString());
        root.put("durationMillis", report.duration().toMillis());
        root.put("technical", report.technical());
        root.put("summary", Map.of(
                "ok", report.okCount(),
                "warn", report.warnCount(),
                "fail", report.failCount(),
                "skipped", report.skippedCount()
        ));
        List<Map<String, Object>> results = new ArrayList<>();
        for (DiagnosticResult result : report.results()) {
            results.add(toJsonMap(result));
        }
        root.put("results", results);
        return root;
    }

    private static Map<String, Object> toJsonMap(DiagnosticResult result) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("checkId", result.checkId());
        root.put("suite", result.suite());
        root.put("status", result.status().name());
        root.put("severity", result.severity().name());
        root.put("message", result.message());
        root.put("durationMillis", result.duration().toMillis());
        root.put("details", result.details());
        if (result.error() != null) {
            root.put("errorClass", result.error().getClass().getName());
            root.put("errorMessage", sanitizeMessage(result.error().getMessage()));
            root.put("error", toJsonMap(result.error()));
        }
        return root;
    }

    private static Map<String, Object> toJsonMap(Throwable error) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("class", error.getClass().getName());
        root.put("message", sanitizeMessage(error.getMessage()));
        root.put("stackTrace", renderStackTrace(error));
        root.put("causeChain", renderCauseChain(error));
        return root;
    }

    private static List<String> renderStackTrace(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        List<String> lines = new ArrayList<>();
        for (String line : writer.toString().split("\\R")) {
            String trimmed = line.stripTrailing();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return List.copyOf(lines);
    }

    private static List<Map<String, Object>> renderCauseChain(Throwable error) {
        List<Map<String, Object>> causes = new ArrayList<>();
        Throwable current = error.getCause();
        while (current != null && current != error) {
            Map<String, Object> cause = new LinkedHashMap<>();
            cause.put("class", current.getClass().getName());
            cause.put("message", sanitizeMessage(current.getMessage()));
            causes.add(Map.copyOf(cause));
            current = current.getCause();
        }
        return List.copyOf(causes);
    }

    private static String formatDuration(Duration duration) {
        Duration resolved = duration != null ? duration : Duration.ZERO;
        return resolved.toMillis() + " ms";
    }

    private static List<DiagnosticResult> resultsByStatus(DiagnosticReport report, DiagnosticStatus status) {
        List<DiagnosticResult> results = new ArrayList<>();
        for (DiagnosticResult result : report.results()) {
            if (result.status() == status) {
                results.add(result);
            }
        }
        return List.copyOf(results);
    }

    private static void appendSuiteSummary(List<String> lines, DiagnosticReport report, boolean issuesOnly) {
        Map<String, SuiteCounts> countsBySuite = new LinkedHashMap<>();
        for (DiagnosticResult result : report.results()) {
            countsBySuite.computeIfAbsent(normalizeSuite(result.suite()), ignored -> new SuiteCounts())
                    .record(result.status());
        }
        List<String> suiteLines = new ArrayList<>();
        for (Map.Entry<String, SuiteCounts> entry : countsBySuite.entrySet()) {
            SuiteCounts counts = entry.getValue();
            if (issuesOnly && !counts.hasIssues()) {
                continue;
            }
            suiteLines.add("<dark_gray>-</dark_gray> <white>"
                    + escapeText(entry.getKey())
                    + "</white>: "
                    + counts.format());
        }
        if (suiteLines.isEmpty()) {
            return;
        }
        lines.add("<aqua>Suites:</aqua>");
        lines.addAll(suiteLines);
    }

    private static void appendSection(List<String> lines, String title, List<DiagnosticResult> results) {
        if (results.isEmpty()) {
            return;
        }
        lines.add("<aqua>" + escapeText(title) + ":</aqua>");
        int shown = 0;
        for (DiagnosticResult result : results) {
            if (shown >= MAX_SECTION_RESULTS) {
                break;
            }
            lines.add(statusTag(result.status())
                    + " <white>"
                    + escapeText(compactCheckId(result))
                    + "</white> <dark_gray>-</dark_gray> <gray>"
                    + escapeText(sanitizeMessage(result.message()))
                    + "</gray>");
            String hint = resultHint(result);
            if (!hint.isBlank()) {
                lines.add("<dark_gray>  hint:</dark_gray> <gray>" + escapeText(hint) + "</gray>");
            }
            shown++;
        }
        int hidden = results.size() - shown;
        if (hidden > 0) {
            lines.add("<dark_gray>-</dark_gray> <gray>... and "
                    + hidden
                    + " more "
                    + escapeText(title.toLowerCase())
                    + ".</gray>");
        }
    }

    private static String compactCheckId(DiagnosticResult result) {
        String checkId = result.checkId();
        String suite = result.suite();
        if (checkId == null || checkId.isBlank()) {
            return "";
        }
        if (suite != null && !suite.isBlank() && checkId.startsWith(suite + ".")) {
            return checkId.substring(suite.length() + 1);
        }
        int firstDot = checkId.indexOf('.');
        if (firstDot >= 0 && firstDot + 1 < checkId.length()) {
            return checkId.substring(firstDot + 1);
        }
        return checkId;
    }

    private static String resultHint(DiagnosticResult result) {
        Map<String, Object> details = result.details();
        if (details == null || details.isEmpty()) {
            return "";
        }
        Object actionHint = details.get("actionHint");
        if (actionHint instanceof String value && !value.isBlank()) {
            return sanitizeMessage(value);
        }
        Object missingRequirements = details.get("missingRequirements");
        if (missingRequirements instanceof Collection<?> values && !values.isEmpty()) {
            StringJoiner joiner = new StringJoiner(", ");
            for (Object value : values) {
                if (value != null) {
                    String text = sanitizeMessage(String.valueOf(value));
                    if (!text.isBlank()) {
                        joiner.add(text);
                    }
                }
            }
            String joined = joiner.toString();
            if (!joined.isBlank()) {
                return "Missing: " + joined;
            }
        }
        return "";
    }

    private static String normalizeSuite(@Nullable String suite) {
        if (suite == null || suite.isBlank()) {
            return "default";
        }
        return suite;
    }

    private static final class SuiteCounts {
        private int ok;
        private int warn;
        private int fail;
        private int skipped;

        private void record(@Nullable DiagnosticStatus status) {
            DiagnosticStatus resolved = status != null ? status : DiagnosticStatus.FAIL;
            switch (resolved) {
                case OK -> ok++;
                case WARN -> warn++;
                case FAIL -> fail++;
                case SKIPPED -> skipped++;
            }
        }

        private boolean hasIssues() {
            return warn > 0 || fail > 0 || skipped > 0;
        }

        private String format() {
            List<String> parts = new ArrayList<>();
            if (fail > 0) {
                parts.add("<red>" + fail + " FAIL</red>");
            }
            if (warn > 0) {
                parts.add("<yellow>" + warn + " WARN</yellow>");
            }
            if (skipped > 0) {
                parts.add("<gray>" + skipped + " SKIPPED</gray>");
            }
            if (ok > 0 && parts.isEmpty()) {
                parts.add("<green>" + ok + " OK</green>");
            } else if (ok > 0) {
                parts.add("<green>" + ok + " OK</green>");
            }
            return String.join("<dark_gray>, </dark_gray>", parts);
        }
    }

    private static String statusTag(DiagnosticStatus status) {
        DiagnosticStatus resolved = status != null ? status : DiagnosticStatus.FAIL;
        return switch (resolved) {
            case OK -> "<green>[OK]</green>";
            case WARN -> "<yellow>[WARN]</yellow>";
            case FAIL -> "<red>[FAIL]</red>";
            case SKIPPED -> "<gray>[SKIPPED]</gray>";
        };
    }

    static String escapeText(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("<", "\\<")
                .replace(">", "\\>");
    }
}
