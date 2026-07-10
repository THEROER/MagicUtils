package dev.ua.theroer.magicutils.build.release

import dev.ua.theroer.magicutils.build.publish.*

import org.gradle.api.GradleException

/**
 * Pure, side-effect-free release helpers ported from the former
 * scripts/publish_release.py. Kept separate from the Gradle tasks so the
 * logic (semver rules, version bumping, smoke URL) is unit-testable without
 * a Gradle runtime.
 */

private val SEMVER_REGEX = Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$""")
private val TAG_REGEX = Regex("""^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$""")

data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {
    override fun toString() = "$major.$minor.$patch"

    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        fun parse(raw: String): SemanticVersion {
            val match = SEMVER_REGEX.matchEntire(raw.trim())
                ?: throw GradleException("Version must use plain semver X.Y.Z, e.g. 1.21.4 (got '$raw').")
            val (major, minor, patch) = match.destructured
            return SemanticVersion(major.toInt(), minor.toInt(), patch.toInt())
        }

        /** The semver embedded in a `vX.Y.Z` tag, or null if [tag] isn't such a tag. */
        fun fromTag(tag: String): SemanticVersion? {
            val normalized = tag.trim()
                .removePrefix("refs/tags/")
                .removeSuffix("^{}")
            return TAG_REGEX.matchEntire(normalized)?.let {
                val (major, minor, patch) = it.destructured
                SemanticVersion(major.toInt(), minor.toInt(), patch.toInt())
            }
        }
    }
}

/** The `version=` value from a gradle.properties body. */
internal fun readGradleVersion(gradlePropertiesText: String): SemanticVersion {
    val line = gradlePropertiesText.lineSequence()
        .firstOrNull { it.startsWith("version=") }
        ?: throw GradleException("Could not find 'version=' entry in gradle.properties.")
    return SemanticVersion.parse(line.substringAfter("version=").trim())
}

/** gradle.properties body with its `version=` line set to [version]. */
internal fun bumpGradleVersion(gradlePropertiesText: String, version: SemanticVersion): String {
    val regex = Regex("""(?m)^version=.*$""")
    if (!regex.containsMatchIn(gradlePropertiesText)) {
        throw GradleException("Could not find 'version=' entry in gradle.properties.")
    }
    return regex.replaceFirst(gradlePropertiesText, "version=$version")
}

/**
 * URL of the POM smoke-tested after a publish, per [MagicUtilsPublishingSpec].
 *
 * [versionCoordinate] is the FULL published coordinate, including the target
 * suffix the target plugin appends to `project.version` (e.g. `1.26.0+java21`).
 * Passing a bare `X.Y.Z` here would point at a POM that is never published — the
 * library only ships per-target coordinates — so the smoke poll would 404
 * forever. The `+` is percent-encoded for the HTTP request.
 */
internal fun MagicUtilsPublishingSpec.smokeArtifactUrl(versionCoordinate: String): String {
    val groupPath = group.replace('.', '/')
    val encoded = versionCoordinate.replace("+", "%2B")
    return "$repoUrl/$groupPath/$smokeArtifact/$encoded/$smokeArtifact-$encoded.pom"
}

/**
 * Parse a Modrinth `GET /project/{id}/version` response body into a map of
 * `version_number` -> version `id`. A regex can't pair id<->version_number
 * because nested file/dependency objects also carry `"id"`, so this does a
 * proper JSON parse. Later duplicates overwrite, so the map holds the newest id
 * per version_number. Kept here (pure) so both the Modrinth publish task and the
 * release-consistency check parse the response the same way.
 */
internal fun parseModrinthVersionIds(responseBody: String): Map<String, String> {
    @Suppress("UNCHECKED_CAST")
    val versions = groovy.json.JsonSlurper().parseText(responseBody) as? List<Map<String, Any?>>
        ?: return emptyMap()
    return versions.mapNotNull { v ->
        val id = v["id"] as? String
        val num = v["version_number"] as? String
        if (id != null && num != null) num to id else null
    }.toMap()
}

/**
 * Validate a requested release version against the current gradle.properties
 * version and the latest already-released version. Returns nothing; throws
 * [GradleException] with an actionable message on any violation.
 */
internal fun validateReleaseVersion(
    requested: SemanticVersion,
    current: SemanticVersion,
    latestReleased: SemanticVersion?,
    existingTags: Set<String>,
) {
    if (requested < current) {
        throw GradleException("Version $requested must not be lower than gradle.properties $current.")
    }
    if ("v$requested" in existingTags) {
        throw GradleException("Release tag 'v$requested' already exists.")
    }
    if (latestReleased != null && requested <= latestReleased) {
        throw GradleException("Version $requested must be greater than latest release $latestReleased.")
    }
}

/** Presence of a release version across the sources verifyReleaseConsistency checks. */
enum class SourceState { PRESENT, ABSENT, SKIPPED }

/** One line of the consistency report: a source name, its state, and detail. */
data class ReleaseSourceStatus(val source: String, val state: SourceState, val detail: String)

/**
 * Result of comparing a release version across gradle.properties, the git tag,
 * Reposilite Maven, and Modrinth. [consistent] is the strict verdict: all
 * required sources (everything except Modrinth, which publishes manually and may
 * legitimately lag) must agree, or [verifyReleaseConsistency]'s task fails.
 */
data class ReleaseConsistencyReport(
    val version: SemanticVersion,
    val statuses: List<ReleaseSourceStatus>,
    val consistent: Boolean,
    val problems: List<String>,
)

/**
 * Compare a release [version] across its four publishing surfaces. Each input is
 * the already-gathered fact for that source (I/O stays in the Gradle task; this
 * is the pure, testable comparison):
 *
 * - [gradlePropertiesVersion]: the version in gradle.properties.
 * - [tagExists]: whether a `vX.Y.Z` git tag exists.
 * - [mavenPublished]: whether the java-suffixed POM is reachable on Reposilite.
 * - [modrinthPublished]: whether Modrinth has the version; null = check skipped
 *   (no token / offline), which is a warning, never a strict failure.
 *
 * Modrinth is advisory because it is published by a separate manual command; the
 * other three must agree for the release to be considered consistent.
 */
fun evaluateReleaseConsistency(
    version: SemanticVersion,
    gradlePropertiesVersion: SemanticVersion,
    tagExists: Boolean,
    mavenPublished: Boolean,
    modrinthPublished: Boolean?,
): ReleaseConsistencyReport {
    val statuses = mutableListOf<ReleaseSourceStatus>()
    val problems = mutableListOf<String>()

    val gradleMatches = gradlePropertiesVersion == version
    statuses += ReleaseSourceStatus(
        "gradle.properties",
        if (gradleMatches) SourceState.PRESENT else SourceState.ABSENT,
        if (gradleMatches) "version=$version" else "version=$gradlePropertiesVersion (expected $version)",
    )
    if (!gradleMatches) problems += "gradle.properties is at $gradlePropertiesVersion, not $version."

    statuses += ReleaseSourceStatus(
        "git tag",
        if (tagExists) SourceState.PRESENT else SourceState.ABSENT,
        if (tagExists) "v$version present" else "v$version missing",
    )
    if (!tagExists) problems += "Git tag v$version does not exist."

    statuses += ReleaseSourceStatus(
        "Maven (Reposilite)",
        if (mavenPublished) SourceState.PRESENT else SourceState.ABSENT,
        if (mavenPublished) "POM published" else "POM not found",
    )
    if (!mavenPublished) problems += "Maven POM for $version is not published on Reposilite."

    statuses += when (modrinthPublished) {
        true -> ReleaseSourceStatus("Modrinth", SourceState.PRESENT, "version $version published")
        false -> ReleaseSourceStatus("Modrinth", SourceState.ABSENT, "version $version not published (publish manually with publishToModrinth)")
        null -> ReleaseSourceStatus("Modrinth", SourceState.SKIPPED, "not checked (no MODRINTH_TOKEN or unreachable)")
    }

    // Strict verdict excludes Modrinth: it lags legitimately until the manual publish.
    return ReleaseConsistencyReport(version, statuses, problems.isEmpty(), problems)
}
