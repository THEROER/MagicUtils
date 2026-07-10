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
