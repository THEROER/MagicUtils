package dev.ua.theroer.magicutils.build.smoke

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

/**
 * How a failed sub-range affects the run:
 *  - STRICT   any failed entry fails the whole task (fail-closed default).
 *  - PARTIAL  green entries pass, red ones are skipped; the task succeeds.
 *  - APPROVAL run everything, then exit for manual sign-off if anything failed.
 */
enum class SmokeGate { STRICT, PARTIAL, APPROVAL;
    companion object {
        fun from(value: String?): SmokeGate =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: STRICT
    }
}

/** One concrete server-smoke case for a single Minecraft version. */
data class SmokeCase(
    val id: String,
    val entryId: String,
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

/**
 * A version_matrix entry before expansion into per-version cases. One entry is
 * one published sub-range: [versions] is the full Minecraft range this build
 * covers (advertised on the release), [smokeValues] the representative versions
 * actually launched to gate it, and [target] the build variant (its jar) the
 * whole range ships as. The gate and the publish therefore agree by construction
 * — the jar smoke-tested on [smokeValues] is the one released for [versions].
 */
data class SmokeMatrixEntry(
    val id: String,
    val versions: List<String>,
    val smokeValues: List<String> = emptyList(),
    /** Build variant for this sub-range; falls back to the matrix default target. */
    val target: String? = null,
    /**
     * Marks the platform's primary sub-range — the one an update service hands
     * out by default. Exactly one entry per platform should set it; a consumer
     * that does not use the primary concept can leave them all false.
     */
    val primary: Boolean = false,
    val gradleProperties: Map<String, String> = emptyMap(),
    val smokeGradleProperties: Map<String, Map<String, String>> = emptyMap(),
    val successPattern: String? = null,
) {
    /** The target this entry builds/gates on, or [default] when unset. */
    fun resolvedTarget(default: String): String = target ?: gradleProperties["target"] ?: default

    /**
     * Gradle -P flags for this entry: the resolved target plus any extra
     * properties, target-first and de-duplicated so `target` is never doubled.
     */
    internal fun targetGradleProperties(default: String): Map<String, String> =
        linkedMapOf("target" to resolvedTarget(default)) + gradleProperties
}

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
 * Fully expands a numeric range into *every* Minecraft version between the
 * endpoints — `"1.20-1.20.4"` -> `["1.20","1.20.1","1.20.2","1.20.3","1.20.4"]` —
 * not just the endpoints [expandVersionRange] returns. This is the version set a
 * release advertises (e.g. a Modrinth file marked for each supported game
 * version). Pure integer-component arithmetic, no version catalog needed: it
 * assumes every patch in the interval exists, which holds for Minecraft.
 *
 * Handles three shapes, mirroring the former Python release tooling:
 *  - cross-branch (minor bumps by one, 3-component start): `start`, the next
 *    minor, then that minor's patches 1..end.  `1.20.6-1.21` -> 1.20.6, 1.21.
 *  - same length: increments the last component.  `1.20-1.20.4` -> 1.20..1.20.4.
 *  - one deeper (`start` == `end` without its last part): `start` then patches
 *    1..end.  `1.21-1.21.3` handled by same-length; `1.21-1.21` -> [1.21].
 * A plain version or non-numeric/unsupported range returns itself unchanged.
 */
internal fun expandVersionRangeFull(token: String): List<String> {
    val trimmed = token.trim()
    val dash = trimmed.indexOf('-')
    if (dash <= 0) return listOf(trimmed)
    val startStr = trimmed.substring(0, dash).trim()
    val endStr = trimmed.substring(dash + 1).trim()
    val start = parseVersionKey(startStr) ?: return listOf(trimmed)
    val end = parseVersionKey(endStr) ?: return listOf(trimmed)

    fun fmt(parts: List<Int>) = parts.joinToString(".")

    // Cross-branch: minor + 1 within the same higher components.
    if (start.size == end.size && start.size >= 2 &&
        start.dropLast(2) == end.dropLast(2) &&
        end[end.size - 2] == start[start.size - 2] + 1
    ) {
        if (start.size == 2) return listOf(fmt(start), fmt(end))
        if (start.size == 3) {
            val nextRelease = end.dropLast(1)
            return listOf(fmt(start), fmt(nextRelease)) +
                (1..end.last()).map { fmt(nextRelease + it) }
        }
    }

    // Same length, same prefix: increment the last component.
    if (start.size == end.size) {
        if (start.dropLast(1) != end.dropLast(1) || end.last() < start.last()) {
            return listOf(trimmed)
        }
        val prefix = start.dropLast(1)
        return (start.last()..end.last()).map { fmt(prefix + it) }
    }

    // One component deeper: start, then its patches 1..end.
    if (start.size + 1 == end.size && start == end.dropLast(1)) {
        return listOf(fmt(start)) + (1..end.last()).map { fmt(start + it) }
    }

    return listOf(trimmed)
}

/** Fully expands each token (see [expandVersionRangeFull]), de-duplicated. */
internal fun List<String>.expandVersionsFull(): List<String> =
    flatMap(::expandVersionRangeFull).distinct()

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
 * value across all its matrix entries. The entry's target is applied as
 * `-Ptarget=...` (so the smoke run and the release build resolve the identical
 * jar), then per-version overrides in [SmokeMatrixEntry.smokeGradleProperties]
 * are merged over the entry defaults.
 */
internal fun SmokePlatformSpec.toSmokeCases(defaultTarget: String): List<SmokeCase> {
    if (runTask.isBlank()) {
        throw GradleException("Smoke platform '$name' must declare a runTask.")
    }
    return versionMatrix.flatMap { entry ->
        entry.resolvedSmokeValues().map { version ->
            val props = LinkedHashMap(entry.targetGradleProperties(defaultTarget))
            entry.smokeGradleProperties[version]?.let(props::putAll)
            val flags = gradlePropertyFlags(props)
            val command = buildString {
                append("./gradlew --refresh-dependencies ")
                append(runTask)
                if (flags.isNotEmpty()) append(' ').append(flags)
            }
            SmokeCase(
                id = "$name-${entry.id}-$version",
                entryId = "$name-${entry.id}",
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
internal fun List<SmokePlatformSpec>.toSmokeCases(defaultTarget: String): List<SmokeCase> =
    flatMap { it.toSmokeCases(defaultTarget) }
