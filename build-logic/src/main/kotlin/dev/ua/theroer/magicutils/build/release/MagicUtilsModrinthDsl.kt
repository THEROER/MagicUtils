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
 * The token is read from the MODRINTH_TOKEN env var at publish time, never here.
 */
open class MagicUtilsModrinthDsl {
    var projectId: String = ""
    var channel: String = "release"
    var featured: Boolean = false

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
            artifacts = artifacts.map { it.build() },
        )
    }
}

open class MagicUtilsModrinthArtifactBuilder(private val key: String) {
    var file: String = ""
    var loaders: List<String> = emptyList()
    var gameVersions: List<String> = emptyList()

    internal fun build(): ModrinthArtifact =
        ModrinthArtifact(key = key, file = file, loaders = loaders, gameVersions = gameVersions)
}
