package dev.ua.theroer.magicutils.diagnostics;

import dev.ua.theroer.magicutils.bootstrap.MagicRuntime;

import java.nio.file.Path;
import java.util.Collection;

/**
 * Service for executing and exporting runtime diagnostics.
 */
public interface DiagnosticsService {
    /**
     * Returns the runtime bound to this service.
     *
     * @return runtime container
     */
    MagicRuntime runtime();

    /**
     * Returns the backing registry.
     *
     * @return diagnostic registry
     */
    DiagnosticRegistry registry();

    /**
     * Runs all checks.
     *
     * @param request run request
     * @return collected report
     */
    DiagnosticReport runAll(DiagnosticRunRequest request);

    /**
     * Runs all checks in a suite.
     *
     * @param suite suite identifier
     * @param request run request
     * @return collected report
     */
    DiagnosticReport runSuite(String suite, DiagnosticRunRequest request);

    /**
     * Runs selected checks by id.
     *
     * @param ids check ids
     * @param request run request
     * @return collected report
     */
    DiagnosticReport runChecks(Collection<String> ids, DiagnosticRunRequest request);

    /**
     * Exports a report to JSON using the default runtime path.
     *
     * @param report report to export
     * @return written path
     */
    default Path exportJson(DiagnosticReport report) {
        return exportJson(report, DiagnosticReports.defaultExportPath(runtime()));
    }

    /**
     * Exports a report to JSON.
     *
     * @param report report to export
     * @param path destination path
     * @return written path
     */
    Path exportJson(DiagnosticReport report, Path path);
}
