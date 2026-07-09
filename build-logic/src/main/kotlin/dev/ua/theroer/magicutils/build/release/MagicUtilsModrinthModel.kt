package dev.ua.theroer.magicutils.build.release

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
    /**
     * Modrinth loaders for this artifact. When empty, resolved from [platform]
     * via [modrinthLoadersForPlatform] so a release only declares the platform.
     */
    val loaders: List<String>,
    val gameVersions: List<String>,
    /** Platform this artifact belongs to (bukkit/velocity/fabric/neoforge). */
    val platform: String = "",
) {
    /** Effective loaders: explicit [loaders] if given, else derived from [platform]. */
    fun resolvedLoaders(): List<String> =
        loaders.ifEmpty { modrinthLoadersForPlatform(platform) }
}

/** Modrinth release configuration, declared by the consumer via the DSL. */
data class ModrinthReleaseSpec(
    val projectId: String,
    /** stable | beta | alpha — Modrinth version_type/channel. */
    val channel: String = "release",
    val featured: Boolean = false,
    /** Markdown changelog uploaded with every version (empty = none). */
    val changelog: String = "",
    val artifacts: List<ModrinthArtifact> = emptyList(),
) {
    /**
     * version_number for an artifact: "<baseVersion>-<channel>-<key>" — the
     * channel is included (matching verified-plugin) so a beta/alpha of the same
     * base version never collides with the stable one.
     */
    fun versionNumber(baseVersion: String, artifact: ModrinthArtifact): String {
        val channelTag = when (modrinthVersionType(channel)) {
            "release" -> baseVersion
            else -> "$baseVersion-${modrinthVersionType(channel)}"
        }
        return "$channelTag-${artifact.key}"
    }
}

/** Modrinth's version_type expects release/beta/alpha; map common channel aliases. */
internal fun modrinthVersionType(channel: String): String = when (channel.trim().lowercase()) {
    "stable", "release" -> "release"
    "beta" -> "beta"
    "dev", "alpha" -> "alpha"
    else -> channel.trim().lowercase()
}

/**
 * Canonical Modrinth `loaders` for a MagicUtils platform, mirroring
 * verified-plugin's `publish_to_modrinth.sh` `platform_loaders_json`. A consumer
 * that declares an artifact by platform gets the right loader set automatically,
 * so the paper/spigot/bukkit/folia (etc.) list is never hand-written per release.
 *
 * The bukkit set includes `purpur` (a Paper fork) and bungee maps to both
 * `bungeecord` and `waterfall` (the maintained fork). The fabric set includes
 * `quilt` — Quilt loads Fabric mods, and the MagicUtils fabric bundle is a plain
 * Fabric mod, so advertising quilt widens reach at no cost (matches how peer
 * cross-platform libraries on Modrinth, e.g. xdlib, ship `[fabric, quilt]`).
 */
internal fun modrinthLoadersForPlatform(platform: String): List<String> =
    when (platform.trim().lowercase()) {
        "bukkit" -> listOf("paper", "purpur", "spigot", "bukkit", "folia")
        "bungee" -> listOf("bungeecord", "waterfall")
        "velocity" -> listOf("velocity")
        "fabric" -> listOf("fabric", "quilt")
        "neoforge" -> listOf("neoforge")
        else -> emptyList()
    }

/** Modrinth v3 `environment` — a loader-field only valid for mod loaders (fabric/neoforge); null for plugin loaders. */
internal fun modrinthEnvironmentForPlatform(platform: String): String? =
    when (platform.trim().lowercase()) {
        "fabric", "neoforge" -> "server_only_client_optional"
        else -> null
    }
