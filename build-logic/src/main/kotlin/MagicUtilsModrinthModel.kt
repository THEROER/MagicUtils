/**
 * Modrinth publishing model. Ports the reusable upload mechanism of the former
 * verified-plugin `publish_to_modrinth.sh` (project/token/channel + one version
 * per artifact with loaders and game_versions). Catalog-driven game_version
 * resolution was consumer/product specific and is NOT included — the consumer
 * declares game_versions explicitly per artifact.
 */

/** A single jar to upload as one Modrinth version. */
data class ModrinthArtifact(
    /** Stable key used in the version number suffix and the multipart file part. */
    val key: String,
    /** Path to the jar, relative to the project root or absolute. */
    val file: String,
    val loaders: List<String>,
    val gameVersions: List<String>,
)

/** Modrinth release configuration, declared by the consumer via the DSL. */
data class ModrinthReleaseSpec(
    val projectId: String,
    /** stable | beta | alpha — Modrinth version_type/channel. */
    val channel: String = "release",
    val featured: Boolean = false,
    val artifacts: List<ModrinthArtifact> = emptyList(),
) {
    /** version_number for an artifact: "<baseVersion>-<key>" (unique per loader/target). */
    fun versionNumber(baseVersion: String, artifact: ModrinthArtifact): String =
        "$baseVersion-${artifact.key}"
}

/** Modrinth's version_type expects release/beta/alpha; map common channel aliases. */
internal fun modrinthVersionType(channel: String): String = when (channel.trim().lowercase()) {
    "stable", "release" -> "release"
    "beta" -> "beta"
    "dev", "alpha" -> "alpha"
    else -> channel.trim().lowercase()
}
