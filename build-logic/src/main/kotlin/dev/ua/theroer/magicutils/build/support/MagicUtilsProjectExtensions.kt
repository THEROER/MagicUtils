package dev.ua.theroer.magicutils.build.support

import dev.ua.theroer.magicutils.build.module.*

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

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

/**
 * Bundles the optional Redis messaging transport (Jedis) into a standalone
 * bundle and relocates it under the MagicUtils `libs` namespace, the same way
 * [MagicUtilsShadedModulePlugin] relocates Jackson.
 *
 * MagicUtils bundles are drop-in plugins that provide the messaging runtime for
 * the whole network, so shipping Jedis makes the Redis transport work out of the
 * box once an operator enables it in `messaging.yml`. The default
 * plugin-messaging transport needs no extra dependency, so a network can still
 * run without Redis. Called by every bundle plugin so the coordinate and
 * relocation prefix live in one place.
 *
 * @param bundleShadowConfiguration name of the shade configuration the bundle jar draws from
 */
internal fun Project.magicUtilsBundleRedis(bundleShadowConfiguration: String) {
    val jedis = extensions
        .getByType(VersionCatalogsExtension::class.java)
        .named("libs")
        .findLibrary("jedis")
        .get()
        .get()
    dependencies.add(bundleShadowConfiguration, jedis)

    tasks.named("shadowJar", ShadowJar::class.java).configure { shadowJarTask ->
        shadowJarTask.relocate("redis.clients.jedis", "dev.ua.theroer.magicutils.libs.jedis")
        // Jedis pulls in Apache Commons Pool for its connection pool.
        shadowJarTask.relocate("org.apache.commons.pool2", "dev.ua.theroer.magicutils.libs.commons.pool2")
    }
}
