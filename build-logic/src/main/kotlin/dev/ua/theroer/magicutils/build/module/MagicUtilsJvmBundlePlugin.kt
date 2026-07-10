package dev.ua.theroer.magicutils.build.module

import dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerBungeePlugin
import dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerVelocityPlugin
import dev.ua.theroer.magicutils.build.support.*
import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

/**
 * Builds a standalone, shaded, drop-in plugin for a plain-JVM server platform
 * (Bukkit/Paper, BungeeCord, Velocity). All three shade every MagicUtils module
 * into one runtime jar via the `bundleShadow` configuration and differ only in:
 *
 *  - which `platform-*` module carries the entrypoint,
 *  - the compile-only server API coordinate (from the target for Paper, from a
 *    gradle property for the version-decoupled proxy APIs),
 *  - the plugin-descriptor resource file(s) filtered for `${version}` (and, for
 *    Bukkit, the derived `${apiVersion}`).
 *
 * Those three axes are the [JvmBundlePlatform] table below; everything else (the
 * `bundleShadow` wiring, shaded config modules, shadowJar setup, publication) is
 * shared. The loader-based Fabric/NeoForge bundles jar-in-jar instead of shading
 * and keep their own plugins.
 */
abstract class MagicUtilsJvmBundlePlugin(private val platform: JvmBundlePlatform) : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("magicutils.java-library")
        project.pluginManager.apply("magicutils.common")
        project.pluginManager.apply("magicutils.target")
        project.pluginManager.apply("magicutils.shadow")

        val moduleName = project.magicUtilsModuleName()

        with(project) {
            val bundleShadow = configurations.create("bundleShadow")
            bundleShadow.isCanBeConsumed = false
            bundleShadow.isCanBeResolved = true
            bundleShadow.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
            )

            val bundleProjects = listOf(
                project(":platform-api"),
                project(":logger"),
                project(":commands"),
                project(":diagnostics"),
                project(":http-client"),
                project(":placeholders"),
                project(":core"),
                project(platform.platformProjectPath)
            )

            val bundleShadedProjects = listOf(
                project(":config"),
                project(":config-yaml"),
                project(":config-toml"),
                project(":lang")
            )

            bundleShadedProjects.forEach { dep ->
                val depProject = project(dep.path)
                val depDependency = dependencies.add("bundleShadow", depProject) as org.gradle.api.artifacts.ProjectDependency
                depDependency.targetConfiguration = "shadedElements"
            }

            bundleProjects.forEach { dep ->
                dependencies.add("bundleShadow", project(dep.path))
                dependencies.add("compileOnly", project(dep.path))
            }

            bundleShadedProjects.forEach { dep ->
                dependencies.add("compileOnly", project(dep.path))
            }

            // Ship the optional Redis transport (Jedis) so operators can enable it
            // in messaging.yml; the default plugin-messaging transport needs it not.
            magicUtilsBundleRedis("bundleShadow")

            // Server API on the compile classpath for the plugin entrypoint; the
            // server provides it at runtime, so it stays out of the bundle jar
            // (compileOnly, not bundleShadow). The plugin descriptor is the
            // hand-authored resource file(s) filtered below.
            dependencies.add("compileOnly", platform.apiCoordinate(project))

            // Extra ${...} tokens the descriptor needs beyond ${version}: Bukkit's
            // plugin.yml/paper-plugin.yml carry an ${apiVersion} derived from the
            // runtime Minecraft; the proxies have none.
            val extraTokens = platform.extraResourceTokens(project)

            tasks.named("processResources", ProcessResources::class.java).configure { resources ->
                resources.inputs.property("version", version)
                extraTokens.forEach { (key, value) -> resources.inputs.property(key, value) }
                resources.filesMatching(platform.resourceFiles) { details ->
                    details.expand(mapOf("version" to version) + extraTokens)
                }
            }

            tasks.named("shadowJar", ShadowJar::class.java).configure { shadowJarTask ->
                shadowJarTask.archiveBaseName.set(moduleName)
                shadowJarTask.archiveClassifier.set("")
                shadowJarTask.configurations.set(setOf(bundleShadow))
                shadowJarTask.mergeServiceFiles()
            }

            tasks.configureEach {
                if (it.javaClass.simpleName == "GenerateModuleMetadata") {
                    it.enabled = false
                }
            }
        }

        project.extensions.configure(org.gradle.api.publish.PublishingExtension::class.java) { publishing ->
            publishing.publications.create("mavenJava", MavenPublication::class.java) { publication ->
                publication.artifactId = moduleName
                publication.artifact(project.tasks.named("shadowJar", ShadowJar::class.java).get())
                publication.artifact(project.tasks.named("sourcesJar").get())
                publication.artifact(project.tasks.named("javadocJar").get())
                publication.stripPomDependencies()
            }

            project.magicUtilsPublishRepository(publishing)
        }
    }
}

/**
 * The three axes that distinguish a JVM-bundle platform. Each concrete plugin
 * (registered under `magicutils.<platform>-bundle`) passes one of these.
 */
enum class JvmBundlePlatform(
    val platformProjectPath: String,
    val resourceFiles: List<String>,
) {
    BUKKIT(":platform-bukkit", listOf("plugin.yml", "paper-plugin.yml")) {
        override fun apiCoordinate(project: Project): String {
            val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
            return "io.papermc.paper:paper-api:${target.paper.get()}"
        }

        override fun extraResourceTokens(project: Project): Map<String, String> {
            val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
            return mapOf("apiVersion" to bukkitApiVersion(target.minecraft.get()))
        }
    },
    BUNGEE(":platform-bungee", listOf("plugin.yml")) {
        override fun apiCoordinate(project: Project): String {
            val version = project.providers.gradleProperty("bungeeApiVersion")
                .orElse(MagicUtilsConsumerBungeePlugin.DEFAULT_BUNGEE_API).get()
            return "net.md-5:bungeecord-api:$version"
        }
    },
    VELOCITY(":platform-velocity", listOf("velocity-plugin.json")) {
        override fun apiCoordinate(project: Project): String {
            val version = project.providers.gradleProperty("velocityApiVersion")
                .orElse(MagicUtilsConsumerVelocityPlugin.DEFAULT_VELOCITY_API).get()
            return "com.velocitypowered:velocity-api:$version"
        }
    };

    /** The compileOnly server-API coordinate for this platform. */
    abstract fun apiCoordinate(project: Project): String

    /** Descriptor tokens beyond `${version}`; empty for the proxies. */
    open fun extraResourceTokens(project: Project): Map<String, String> = emptyMap()
}

class MagicUtilsBukkitBundlePlugin : MagicUtilsJvmBundlePlugin(JvmBundlePlatform.BUKKIT)
class MagicUtilsBungeeBundlePlugin : MagicUtilsJvmBundlePlugin(JvmBundlePlatform.BUNGEE)
class MagicUtilsVelocityBundlePlugin : MagicUtilsJvmBundlePlugin(JvmBundlePlatform.VELOCITY)

private fun bukkitApiVersion(minecraftVersion: String): String {
    val parts = minecraftVersion.split('.')
    return if (parts.size >= 3) {
        "${parts[0]}.${parts[1]}"
    } else {
        minecraftVersion
    }
}
