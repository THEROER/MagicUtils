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
 *                     versions = listOf("1.21-1.21.11")
 *                     smokeValues = listOf("1.21", "1.21.11")
 *                 }
 *             }
 *         }
 *     }
 */
open class MagicUtilsSmokeDsl {
    private val platforms = linkedMapOf<String, MagicUtilsSmokePlatformBuilder>()

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
    var versions: List<String> = emptyList()
    var smokeValues: List<String> = emptyList()
    var gradleProperties: Map<String, String> = emptyMap()
    var smokeGradleProperties: Map<String, Map<String, String>> = emptyMap()
    var successPattern: String? = null

    internal fun build(): SmokeMatrixEntry = SmokeMatrixEntry(
        id = id,
        versions = versions,
        smokeValues = smokeValues,
        gradleProperties = gradleProperties,
        smokeGradleProperties = smokeGradleProperties,
        successPattern = successPattern,
    )
}
