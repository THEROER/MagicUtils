package dev.ua.theroer.magicutils.build.smoke

import org.gradle.api.Action

/**
 * Consumer-facing DSL for declaring the compatibility smoke matrix, e.g.:
 *
 *     magicMatrix {
 *         smoke {
 *             platform("bukkit") {
 *                 runTask = ":bukkit-bundle:runServer --args='nogui'"
 *                 successPattern = "Done ("
 *                 entry("paper-121x") {
 *                     versions = listOf("1.21-1.21.11")  // advertised on release
 *                     smokeValues = listOf("1.21", "1.21.11")  // gated on these
 *                     target = "mc12110"                 // built as this jar
 *                 }
 *             }
 *         }
 *     }
 */
open class MagicUtilsSmokeDsl {
    private val platforms = linkedMapOf<String, MagicUtilsSmokePlatformBuilder>()

    /** Default gate for failed sub-ranges; overridable with -Psmoke_gate=... . */
    var gate: SmokeGate = SmokeGate.STRICT

    fun platform(name: String, action: Action<MagicUtilsSmokePlatformBuilder>) {
        val builder = platforms.getOrPut(name) { MagicUtilsSmokePlatformBuilder(name) }
        action.execute(builder)
    }

    internal fun toSpecs(): List<SmokePlatformSpec> = platforms.values.map { it.build() }
}

open class MagicUtilsSmokePlatformBuilder(private val name: String) {
    var runTask: String = ""
    var successPattern: String = "Done ("
    var timeoutSeconds: Int = 300
    var diagnosticsRequired: Boolean = true
    var diagnosticsCommand: String = "magicutils diagnostics export"
    var diagnosticsTimeoutSeconds: Int = 60
    var diagnosticsFailOnWarn: Boolean = false

    private val entries = mutableListOf<MagicUtilsSmokeEntryBuilder>()

    fun entry(id: String, action: Action<MagicUtilsSmokeEntryBuilder>) {
        val builder = MagicUtilsSmokeEntryBuilder(id)
        action.execute(builder)
        entries += builder
    }

    internal fun build(): SmokePlatformSpec = SmokePlatformSpec(
        name = name,
        runTask = runTask,
        defaultSuccessPattern = successPattern,
        defaultTimeoutSeconds = timeoutSeconds,
        diagnosticsRequired = diagnosticsRequired,
        diagnosticsCommand = diagnosticsCommand,
        diagnosticsTimeoutSeconds = diagnosticsTimeoutSeconds,
        diagnosticsFailOnWarn = diagnosticsFailOnWarn,
        versionMatrix = entries.map { it.build() },
    )
}

open class MagicUtilsSmokeEntryBuilder(private val id: String) {
    /** Full Minecraft range this build covers — advertised on the release. */
    var versions: List<String> = emptyList()
    /** Representative versions actually launched to gate the range. */
    var smokeValues: List<String> = emptyList()
    /** Build variant (its jar) the whole range ships as; null = matrix default. */
    var target: String? = null
    /** Platform's primary sub-range (update-service default); at most one per platform. */
    var primary: Boolean = false
    /** Extra -P flags for rare cases; `target` above is the idiomatic way to pick one. */
    var gradleProperties: Map<String, String> = emptyMap()
    var smokeGradleProperties: Map<String, Map<String, String>> = emptyMap()
    var successPattern: String? = null

    internal fun build(): SmokeMatrixEntry = SmokeMatrixEntry(
        id = id,
        versions = versions,
        smokeValues = smokeValues,
        target = target,
        primary = primary,
        gradleProperties = gradleProperties,
        smokeGradleProperties = smokeGradleProperties,
        successPattern = successPattern,
    )
}
