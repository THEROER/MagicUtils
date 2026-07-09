package dev.ua.theroer.magicutils.build.module

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
 * Builds the standalone `velocity-bundle` plugin: a shaded, drop-in Velocity
 * plugin that jar-in-jars every MagicUtils module so proxy owners install one
 * runtime. This mirrors [MagicUtilsBukkitBundlePlugin] (Velocity is a plain JVM
 * plugin like Bukkit, so it shades via the `bundleShadow` configuration rather
 * than jar-in-jarring like the loader-based Fabric/NeoForge bundles).
 *
 * Unlike bukkit/fabric there is no per-target Velocity API: Velocity's API is
 * decoupled from the Minecraft version, so the version comes from the
 * `velocityApiVersion` gradle property (default
 * [MagicUtilsConsumerVelocityPlugin.DEFAULT_VELOCITY_API]).
 */
class MagicUtilsVelocityBundlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("magicutils.java-library")
        project.pluginManager.apply("magicutils.common")
        project.pluginManager.apply("magicutils.target")
        project.pluginManager.apply("magicutils.shadow")

        val moduleName = project.magicUtilsModuleName()
        val velocityApiVersion = project.providers.gradleProperty("velocityApiVersion")
            .orElse(DEFAULT_VELOCITY_API).get()

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
                project(":platform-velocity")
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

            // Velocity API on the compile classpath for the plugin entrypoint
            // (@Subscribe/ProxyServer/@DataDirectory). It is provided by the proxy
            // at runtime, so it stays out of the bundle jar (compileOnly, not
            // bundleShadow). No annotationProcessor: the plugin descriptor is the
            // hand-authored velocity-plugin.json below, not the @Plugin AP.
            dependencies.add("compileOnly", "com.velocitypowered:velocity-api:$velocityApiVersion")

            // Fill velocity-plugin.json's ${version} from the build version, the
            // same token flow bukkit-bundle uses for plugin.yml.
            tasks.named("processResources", ProcessResources::class.java).configure { resources ->
                resources.inputs.property("version", version)
                resources.filesMatching(listOf("velocity-plugin.json")) { details ->
                    details.expand(mapOf("version" to version))
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
                publication.pom.withXml { xml ->
                    xml.asElement().getElementsByTagName("dependencies").item(0)?.let { node ->
                        node.parentNode.removeChild(node)
                    }
                }
            }

            project.magicUtilsPublishRepository(publishing)
        }
    }

    companion object {
        const val DEFAULT_VELOCITY_API = "3.1.1"
    }
}
