package dev.ua.theroer.magicutils.build.release

import org.gradle.api.Action

/**
 * Consumer-facing DSL for declaring a Modrinth release, e.g.:
 *
 *     magicMatrix {
 *         modrinth {
 *             projectId = "AbCdEf12"
 *             channel = "beta"
 *             artifact("fabric") {
 *                 file = "fabric-bundle/build/libs/magicutils-fabric-bundle-1.21.5-mc1.21.jar"
 *                 loaders = listOf("fabric")
 *                 gameVersions = listOf("1.21.10")
 *             }
 *         }
 *     }
 *
 * The token is read from the `modrinth_token` property or MODRINTH_TOKEN env at
 * publish time, never here.
 */
open class MagicUtilsModrinthDsl {
    var projectId: String = ""
    var channel: String = "release"
    var featured: Boolean = false
    /** Markdown changelog uploaded with every version. Empty = none. */
    var changelog: String = ""

    /**
     * Artifact-id prefix of the platform bundle jars, used when artifacts are
     * synthesised from the smoke matrix. MagicUtils itself publishes
     * `magicutils-<platform>-bundle`; a downstream consumer overrides this to its
     * own prefix (for example `commandflow-`).
     */
    var artifactPrefix: String = "magicutils-"

    /**
     * Suffix of the bundle Gradle module name, used when artifacts are synthesised
     * from the smoke matrix. MagicUtils bundles live in `<platform>-bundle`
     * modules; a consumer whose platform modules are named plainly (for example
     * `bukkit`, `fabric`) sets this to an empty string.
     */
    var moduleSuffix: String = "-bundle"

    private val artifacts = mutableListOf<MagicUtilsModrinthArtifactBuilder>()

    fun artifact(key: String, action: Action<MagicUtilsModrinthArtifactBuilder>) {
        val builder = MagicUtilsModrinthArtifactBuilder(key)
        action.execute(builder)
        artifacts += builder
    }

    internal fun toSpec(): ModrinthReleaseSpec? {
        if (projectId.isBlank() && artifacts.isEmpty()) return null
        return ModrinthReleaseSpec(
            projectId = projectId,
            channel = channel,
            featured = featured,
            changelog = changelog,
            artifactPrefix = artifactPrefix,
            moduleSuffix = moduleSuffix,
            artifacts = artifacts.map { it.build() },
        )
    }
}

open class MagicUtilsModrinthArtifactBuilder(private val key: String) {
    var file: String = ""
    /** Explicit Modrinth loaders; leave empty to derive from [platform]. */
    var loaders: List<String> = emptyList()
    var gameVersions: List<String> = emptyList()
    /** Platform (bukkit/velocity/fabric/neoforge) — drives the loader set when [loaders] is empty. */
    var platform: String = ""

    internal fun build(): ModrinthArtifact =
        ModrinthArtifact(
            key = key,
            file = file,
            loaders = loaders,
            gameVersions = gameVersions,
            platform = platform,
        )
}
