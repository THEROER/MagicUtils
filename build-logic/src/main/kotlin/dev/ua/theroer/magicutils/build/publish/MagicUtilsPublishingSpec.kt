package dev.ua.theroer.magicutils.build.publish

import org.gradle.api.GradleException
import java.io.File
import java.util.Properties

data class MagicUtilsPublishingSpec(
    val group: String,
    val repoUrl: String,
    val smokeArtifact: String,
)

internal fun loadPublishingSpec(publishingFile: File): MagicUtilsPublishingSpec {
    if (!publishingFile.isFile) {
        throw GradleException("Missing publishing file: ${publishingFile.absolutePath}")
    }

    val properties = Properties().also { properties ->
        publishingFile.inputStream().use(properties::load)
    }

    fun requireValue(key: String): String =
        properties.getProperty(key)?.trim()?.takeIf(String::isNotEmpty)
            ?: throw GradleException(
                "Missing or empty '$key' in ${publishingFile.absolutePath}"
            )

    return MagicUtilsPublishingSpec(
        group = requireValue("group"),
        repoUrl = requireValue("repo.url").trimEnd('/'),
        smokeArtifact = requireValue("smoke.artifact"),
    )
}
