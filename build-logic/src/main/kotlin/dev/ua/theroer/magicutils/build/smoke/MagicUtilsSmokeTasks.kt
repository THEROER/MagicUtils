package dev.ua.theroer.magicutils.build.smoke

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Kotlin replacement for the server part of the former Python
 * `run_compatibility_smoke.py`: launches a real server per [SmokeCase], waits
 * for the success line, runs the diagnostics command in the server console,
 * reads the exported JSON report, and gates on the diagnostics verdict.
 *
 * Only the reusable MC-version-matrix + server-smoke mechanism lives here;
 * Modrinth/catalog checks are consumer-specific and intentionally excluded.
 */

private val FAILURE_PATTERNS = listOf(
    "Exception in thread",
    "FormattedException:",
    "SEVERE",
    "Could not load plugin",
    "Error occurred while enabling",
    "NoClassDefFoundError",
    "NoSuchMethodError",
)

private val DIAGNOSTICS_EXPORT_PATTERN = Regex("""Saved diagnostics report to\s+(.+)""")
private const val POST_SUCCESS_GRACE_MS = 10_000L
private const val DIAGNOSTICS_QUEUE_DELAY_MS = 10_000L
private const val GATE_REPORT_NAME = "gate-report.json"

internal fun registerSmokeTasks(project: Project, cases: List<SmokeCase>, defaultGate: SmokeGate) {
    project.tasks.register("runCompatibilitySmoke", MagicUtilsSmokeTask::class.java) { task ->
        task.group = "verification"
        task.description = "Launch each smoke case's server, run diagnostics, and gate per sub-range " +
            "(-Psmoke_gate=strict|partial|approval)."
        task.cases.set(cases)
        task.defaultGate.set(defaultGate)
        task.notCompatibleWithConfigurationCache("Spawns server processes and reads their console I/O.")
    }
    project.tasks.register("listSmokeMatrix") { task ->
        task.group = "help"
        task.description = "Print the resolved compatibility smoke cases."
        task.doLast {
            if (cases.isEmpty()) {
                println("No smoke cases declared. Configure magicMatrix { smoke { platform(...) { entry(...) } } }.")
            }
            cases.forEach { println("${it.id}: ${it.gradleCommand}  [success='${it.successPattern}']") }
        }
    }
}

abstract class MagicUtilsSmokeTask : DefaultTask() {
    @get:Internal
    abstract val cases: ListProperty<SmokeCase>

    @get:Internal
    abstract val defaultGate: org.gradle.api.provider.Property<SmokeGate>

    @TaskAction
    fun run() {
        val all = cases.get()
        if (all.isEmpty()) {
            throw GradleException(
                "No smoke cases declared. Configure magicMatrix { smoke { platform(...) { entry(...) } } } in settings.gradle."
            )
        }
        val filter = (project.findProperty("smokeCase") as? String)?.trim()
        val selected = if (filter.isNullOrEmpty()) all else all.filter { it.id == filter }
        if (selected.isEmpty()) {
            throw GradleException("No smoke case matches -PsmokeCase=$filter")
        }

        val logDir = project.layout.buildDirectory.dir("smoke-logs").get().asFile
        logDir.mkdirs()

        val caseFailure = linkedMapOf<String, String>()
        for (case in selected) {
            logger.lifecycle("==> ${case.id}: ${case.gradleCommand}")
            runCatching { runCase(case, logDir) }
                .onFailure { caseFailure[case.id] = it.message ?: "failed" }
        }

        val gateOverride = project.findProperty("smoke_gate") as? String
        val gate = if (gateOverride.isNullOrBlank()) defaultGate.get() else SmokeGate.from(gateOverride)
        val entryIds = selected.map(SmokeCase::entryId).distinct()
        val failedEntries = entryIds.filter { entry ->
            selected.any { it.entryId == entry && it.id in caseFailure }
        }
        writeGateReport(logDir, entryIds, failedEntries, caseFailure)

        val passedEntries = entryIds - failedEntries.toSet()
        if (failedEntries.isEmpty()) {
            logger.lifecycle("Smoke passed: ${entryIds.size} sub-range(s), ${selected.size} case(s).")
            return
        }

        val detail = failedEntries.joinToString("\n") { entry ->
            val reasons = selected.filter { it.entryId == entry && it.id in caseFailure }
                .joinToString("; ") { "${it.minecraftVersion}: ${caseFailure[it.id]}" }
            "  $entry ($reasons)"
        }
        when (gate) {
            SmokeGate.STRICT -> throw GradleException(
                "Smoke gate=strict: ${failedEntries.size} sub-range(s) failed, nothing publishes:\n$detail"
            )
            SmokeGate.PARTIAL -> logger.warn(
                "Smoke gate=partial: publishing ${passedEntries.size} passed sub-range(s); " +
                    "skipping ${failedEntries.size} failed:\n$detail"
            )
            SmokeGate.APPROVAL -> throw GradleException(
                "Smoke gate=approval: ${passedEntries.size} passed, ${failedEntries.size} need sign-off " +
                    "(see ${logDir.resolve(GATE_REPORT_NAME)}):\n$detail"
            )
        }
    }

    private fun writeGateReport(
        logDir: File,
        entryIds: List<String>,
        failedEntries: List<String>,
        caseFailure: Map<String, String>,
    ) {
        val passed = entryIds - failedEntries.toSet()
        val json = buildString {
            append("""{"passedEntries":""")
            append(passed.joinToString(prefix = "[", postfix = "]") { "\"$it\"" })
            append(""","failedEntries":""")
            append(failedEntries.joinToString(prefix = "[", postfix = "]") { "\"$it\"" })
            append(""","failedCases":""")
            append(caseFailure.keys.joinToString(prefix = "[", postfix = "]") { "\"$it\"" })
            append("}")
        }
        logDir.resolve(GATE_REPORT_NAME).writeText(json)
    }

    private fun runCase(case: SmokeCase, logDir: File) {
        val logFile = logDir.resolve("${case.id}.log")
        val process = ProcessBuilder("bash", "-lc", case.gradleCommand)
            .directory(project.rootDir)
            .redirectErrorStream(true)
            .start()

        val captured = StringBuilder()
        val deadline = System.currentTimeMillis() + case.timeoutSeconds * 1000L
        var successAt: Long? = null
        var diagnosticsQueuedAt: Long? = null
        var diagnosticsRequestedAt: Long? = null
        var reportPath: File? = null
        var verified = !case.diagnosticsRequired
        val reader = process.inputStream.bufferedReader()
        val writer = process.outputStream.bufferedWriter()

        try {
            while (true) {
                val now = System.currentTimeMillis()
                if (now >= deadline) {
                    throw GradleException("timed out after ${case.timeoutSeconds}s; see $logFile")
                }
                // No diagnostics needed: stop shortly after success stabilizes.
                if (successAt != null && !case.diagnosticsRequired && now >= successAt + POST_SUCCESS_GRACE_MS) {
                    return
                }
                // Queue diagnostics a moment after startup, then send it once.
                if (case.diagnosticsRequired && diagnosticsQueuedAt != null &&
                    diagnosticsRequestedAt == null && now >= diagnosticsQueuedAt + DIAGNOSTICS_QUEUE_DELAY_MS
                ) {
                    logger.lifecycle("running diagnostics: ${case.diagnosticsCommand}")
                    writer.write(case.diagnosticsCommand + "\n")
                    writer.flush()
                    diagnosticsRequestedAt = now
                }
                if (case.diagnosticsRequired && diagnosticsRequestedAt != null && !verified &&
                    now >= diagnosticsRequestedAt + case.diagnosticsTimeoutSeconds * 1000L
                ) {
                    throw GradleException("diagnostics export did not finish within ${case.diagnosticsTimeoutSeconds}s; see $logFile")
                }

                if (!reader.ready()) {
                    if (!process.isAlive && captured.isNotEmpty()) break
                    Thread.sleep(100)
                    continue
                }
                val line = reader.readLine() ?: break
                captured.append(line).append('\n')

                FAILURE_PATTERNS.firstOrNull { line.contains(it) }?.let {
                    throw GradleException("failure pattern '$it' in output; see $logFile")
                }
                if (successAt == null && line.contains(case.successPattern)) {
                    successAt = System.currentTimeMillis()
                    if (case.diagnosticsRequired) diagnosticsQueuedAt = successAt
                }
                if (case.diagnosticsRequired && diagnosticsRequestedAt != null && reportPath == null) {
                    DIAGNOSTICS_EXPORT_PATTERN.find(stripFormatting(line))?.let { m ->
                        reportPath = File(m.groupValues[1].trim())
                    }
                }
                if (reportPath?.isFile == true) {
                    gateReport(reportPath!!, case)
                    verified = true
                    return
                }
            }
            if (successAt == null) {
                throw GradleException("success pattern '${case.successPattern}' not found; see $logFile")
            }
            if (case.diagnosticsRequired && !verified) {
                throw GradleException("diagnostics verification did not complete; see $logFile")
            }
        } finally {
            runCatching { writer.close() }
            destroy(process)
            logFile.writeText(captured.toString())
        }
    }

    /** Reads the exported diagnostics JSON and fails when the verdict blocks publishing. */
    private fun gateReport(reportPath: File, case: SmokeCase) {
        val json = reportPath.readText()
        val fail = readSummaryCount(json, "fail")
        val warn = readSummaryCount(json, "warn")
        val ok = readSummaryCount(json, "ok")
        val skipped = readSummaryCount(json, "skipped")
        if (fail > 0) {
            throw GradleException("diagnostics reported $fail FAIL result(s); see $reportPath")
        }
        if (case.diagnosticsFailOnWarn && warn > 0) {
            throw GradleException("diagnostics reported $warn WARN result(s); see $reportPath")
        }
        logger.lifecycle("diagnostics OK for ${case.id}: $ok ok, $warn warn, $fail fail, $skipped skipped")
    }

    /** Minimal extraction of `summary.<key>` from the diagnostics JSON (avoids a JSON dep). */
    private fun readSummaryCount(json: String, key: String): Int {
        val match = Regex("\"$key\"\\s*:\\s*(\\d+)").find(json.substringAfter("\"summary\"", json))
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun stripFormatting(line: String): String =
        line.replace(Regex("""</?[^>\n]+>"""), "").replace(Regex("""\[[0-?]*[ -/]*[@-~]"""), "")

    private fun destroy(process: Process) {
        if (!process.isAlive) return
        process.destroy()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
    }
}
