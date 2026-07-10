package dev.ua.theroer.magicutils.build.support

import dev.ua.theroer.magicutils.build.module.*

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

internal fun Project.magicUtilsModuleName(projectName: String = name): String {
    val namingSpec = extensions.extraProperties
        .let { if (it.has("magicutilsModuleNaming")) it.get("magicutilsModuleNaming") else null }
            as? MagicUtilsModuleNamingSpec
        ?: MagicUtilsModuleNamingSpec()
    return namingSpec.moduleName(projectName)
}

/**
 * Registers the MagicUtils publish repository on [publishing], if a target is
 * configured. Single source of truth for artifact publication — every module
 * and bundle plugin calls this instead of copying the block, so the publish
 * backend can change in one place.
 *
 * The URL comes from the `publish_repo` property (a `file:` path for the CI
 * gh-pages assembly, or an `http(s):` URL for a real Maven server). Credentials
 * are applied only when supplied, so authentication-less backends (a local
 * directory, gh-pages) keep working unchanged:
 *  - username: `publish_user` property or `PUBLISH_USER` env;
 *  - password: `publish_password` property or `PUBLISH_TOKEN` env.
 */
internal fun Project.magicUtilsPublishRepository(publishing: PublishingExtension) {
    if (!hasProperty("publish_repo")) {
        return
    }
    val repoUrl = property("publish_repo") as String
    val user = findMagicUtilsPublishSecret("publish_user", "PUBLISH_USER")
    val password = findMagicUtilsPublishSecret("publish_password", "PUBLISH_TOKEN")

    publishing.repositories.maven { repo ->
        repo.name = "magicutilsPublish"
        repo.url = uri(repoUrl)
        if (user != null && password != null) {
            repo.credentials { creds ->
                creds.username = user
                creds.password = password
            }
        }
    }
}

private fun Project.findMagicUtilsPublishSecret(propertyName: String, envName: String): String? =
    (findProperty(propertyName) as? String)?.trim()?.takeIf(String::isNotEmpty)
        ?: System.getenv(envName)?.trim()?.takeIf(String::isNotEmpty)

/**
 * Drops the `<dependencies>` node from the generated POM. Bundle publications
 * ship a fat/jar-in-jar artifact whose dependencies are already inside the jar,
 * so advertising them as Maven deps would make consumers double-resolve them.
 * Shared by every bundle plugin instead of copying the `pom.withXml` block.
 */
internal fun MavenPublication.stripPomDependencies() {
    pom.withXml { xml ->
        xml.asElement().getElementsByTagName("dependencies").item(0)?.let { node ->
            node.parentNode.removeChild(node)
        }
    }
}
