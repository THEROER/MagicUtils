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

class MagicUtilsBukkitBundlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("magicutils.java-library")
        project.pluginManager.apply("magicutils.common")
        project.pluginManager.apply("magicutils.target")
        project.pluginManager.apply("magicutils.shadow")

        val magicutilsTarget = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
        val moduleName = project.magicUtilsModuleName()
        val apiVersion = bukkitApiVersion(magicutilsTarget.minecraft.get())

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
                project(":platform-bukkit")
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

            dependencies.add("compileOnly", "io.papermc.paper:paper-api:${magicutilsTarget.paper.get()}")

            tasks.named("processResources", ProcessResources::class.java).configure { resources ->
                resources.inputs.property("version", version)
                resources.inputs.property("apiVersion", apiVersion)
                resources.filesMatching(listOf("plugin.yml", "paper-plugin.yml")) { details ->
                    details.expand(
                        mapOf(
                            "version" to version,
                            "apiVersion" to apiVersion,
                        )
                    )
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
}

private fun bukkitApiVersion(minecraftVersion: String): String {
    val parts = minecraftVersion.split('.')
    return if (parts.size >= 3) {
        "${parts[0]}.${parts[1]}"
    } else {
        minecraftVersion
    }
}
