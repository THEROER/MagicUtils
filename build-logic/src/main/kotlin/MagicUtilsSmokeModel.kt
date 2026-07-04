import org.gradle.api.GradleException

/**
 * Compatibility smoke matrix — the reusable, MC-version-range part of what the
 * former Python `release_support.py` did (Modrinth/catalog integration is
 * consumer-specific and intentionally NOT ported here).
 *
 * A consumer declares platforms and version_matrix entries; the model expands
 * numeric version ranges into concrete smoke values and produces [SmokeCase]s
 * that the smoke task runs: launch a server (`gradleCommand`), wait for
 * `successPattern`, run `diagnosticsCommand`, read the exported report, and
 * gate on the diagnostics verdict.
 */

/** One concrete server-smoke case for a single Minecraft version. */
data class SmokeCase(
    val id: String,
    val platform: String,
    val minecraftVersion: String,
    val gradleCommand: String,
    val successPattern: String,
    val timeoutSeconds: Int,
    val diagnosticsRequired: Boolean,
    val diagnosticsCommand: String,
    val diagnosticsTimeoutSeconds: Int,
    val diagnosticsFailOnWarn: Boolean,
)

/** A version_matrix entry before expansion into per-version cases. */
data class SmokeMatrixEntry(
    val id: String,
    val versions: List<String>,
    val smokeValues: List<String> = emptyList(),
    val gradleProperties: Map<String, String> = emptyMap(),
    val smokeGradleProperties: Map<String, Map<String, String>> = emptyMap(),
    val successPattern: String? = null,
)

/** Per-platform smoke config: how to launch the server + diagnostics settings. */
data class SmokePlatformSpec(
    val name: String,
    /** Gradle task(s) that launch the server, e.g. ":bukkit-bundle:runServer --args='nogui'". */
    val runTask: String,
    val defaultSuccessPattern: String,
    val defaultTimeoutSeconds: Int = 300,
    val diagnosticsRequired: Boolean = true,
    val diagnosticsCommand: String = "magicutils diagnostics export",
    val diagnosticsTimeoutSeconds: Int = 60,
    val diagnosticsFailOnWarn: Boolean = false,
    val versionMatrix: List<SmokeMatrixEntry> = emptyList(),
)

private val VERSION_KEY = Regex("""\d+(?:\.\d+)*""")

/** Parses a dotted version into comparable int parts, or null if not numeric. */
internal fun parseVersionKey(value: String): List<Int>? {
    val trimmed = value.trim()
    if (!VERSION_KEY.matches(trimmed)) return null
    return trimmed.split('.').map(String::toInt)
}

/**
 * Expands a numeric range like `"1.20-1.20.4"` into the endpoints
 * `["1.20", "1.20.4"]` (we smoke-test the boundaries of a range, not every
 * patch — matches the Python default of first+last). A plain version returns
 * itself. Non-range, non-numeric tokens return themselves unchanged.
 */
internal fun expandVersionRange(token: String): List<String> {
    val trimmed = token.trim()
    val dash = trimmed.indexOf('-')
    if (dash <= 0) return listOf(trimmed)
    val low = trimmed.substring(0, dash).trim()
    val high = trimmed.substring(dash + 1).trim()
    // Only treat as a range when both ends parse as numeric versions.
    if (parseVersionKey(low) == null || parseVersionKey(high) == null) {
        return listOf(trimmed)
    }
    return if (low == high) listOf(low) else listOf(low, high)
}

private fun List<String>.expandAll(): List<String> =
    flatMap(::expandVersionRange).distinct()

/**
 * The concrete smoke values for an entry: explicit [SmokeMatrixEntry.smokeValues]
 * if given, else the expanded endpoints of [SmokeMatrixEntry.versions].
 */
internal fun SmokeMatrixEntry.resolvedSmokeValues(): List<String> =
    if (smokeValues.isNotEmpty()) smokeValues.expandAll() else versions.expandAll()

/** Builds the -P flags string from a properties map (stable, sorted order). */
private fun gradlePropertyFlags(props: Map<String, String>): String =
    props.entries.sortedBy { it.key }.joinToString(" ") { "-P${it.key}=${it.value}" }

/**
 * Expands a platform spec into concrete [SmokeCase]s — one per resolved smoke
 * value across all its matrix entries. Per-version overrides in
 * [SmokeMatrixEntry.smokeGradleProperties] are merged over the entry defaults.
 */
internal fun SmokePlatformSpec.toSmokeCases(): List<SmokeCase> {
    if (runTask.isBlank()) {
        throw GradleException("Smoke platform '$name' must declare a runTask.")
    }
    return versionMatrix.flatMap { entry ->
        entry.resolvedSmokeValues().map { version ->
            val props = LinkedHashMap(entry.gradleProperties)
            entry.smokeGradleProperties[version]?.let(props::putAll)
            val flags = gradlePropertyFlags(props)
            val command = buildString {
                append("./gradlew --refresh-dependencies ")
                append(runTask)
                if (flags.isNotEmpty()) append(' ').append(flags)
            }
            SmokeCase(
                id = "$name-${entry.id}-$version",
                platform = name,
                minecraftVersion = version,
                gradleCommand = command,
                successPattern = entry.successPattern ?: defaultSuccessPattern,
                timeoutSeconds = defaultTimeoutSeconds,
                diagnosticsRequired = diagnosticsRequired,
                diagnosticsCommand = diagnosticsCommand,
                diagnosticsTimeoutSeconds = diagnosticsTimeoutSeconds,
                diagnosticsFailOnWarn = diagnosticsFailOnWarn,
            )
        }
    }
}

/** All smoke cases for a set of platform specs. */
internal fun List<SmokePlatformSpec>.toSmokeCases(): List<SmokeCase> =
    flatMap { it.toSmokeCases() }
