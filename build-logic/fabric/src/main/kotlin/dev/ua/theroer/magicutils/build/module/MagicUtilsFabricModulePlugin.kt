package dev.ua.theroer.magicutils.build.module

import dev.ua.theroer.magicutils.build.support.*
import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import net.fabricmc.loom.task.RemapJarTask

class MagicUtilsFabricModulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // magicutils.target registers MagicUtilsTargetExtension; apply it before
        // reading the target so we can pick the right Loom plugin id.
        project.pluginManager.apply("magicutils.target")

        val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
        // Obfuscation boundary, Loom flavour and classifier come from the shared
        // target conventions (MagicUtilsTargetConventions) — see there.
        val isDeobfuscated = target.isDeobfuscated

        project.pluginManager.apply("magicutils.java-library")
        project.pluginManager.apply(target.loomPluginId)
        project.pluginManager.apply("magicutils.common")
        project.pluginManager.apply("magicutils.shadow")

        val shadowRuntimeClasspath =
            project.configurations.create("shadowRuntimeClasspath")

        shadowRuntimeClasspath.isCanBeResolved = true
        shadowRuntimeClasspath.isCanBeConsumed = false

        val magicutilsTarget = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
        val moduleName = project.magicUtilsModuleName()

        // Config selection / primary-jar task from shared conventions.
        val compileOnlyConfig = magicutilsTarget.compileOnlyConfiguration
        val mainJarTaskName = magicutilsTarget.mainJarTaskName

        with(project) {
            // (fabric.mod.json's `@MC_JAVA@` token is substituted centrally in the
            // shared java-library plugin, which this module applies.)

            // Minecraft + Mojang mappings (obfuscated only); modules take the
            // loader compile-only.
            applyMinecraftAndMappings(project, magicutilsTarget)
            project.dependencies.add(compileOnlyConfig, "net.fabricmc:fabric-loader:${magicutilsTarget.loader.get()}")
            project.dependencies.add(compileOnlyConfig, "eu.pb4:placeholder-api:${magicutilsTarget.pb4_placeholder_api.get()}")
            project.dependencies.add(compileOnlyConfig, "io.github.miniplaceholders:miniplaceholders-api:${magicutilsTarget.miniplaceholders_api.get()}")

            // The published jar carries no classifier (branch is in the version).
            if (isDeobfuscated) {
                // No remap: `jar` is the shipped artifact.
                tasks.named("jar", Jar::class.java).configure { jarTask ->
                    jarTask.archiveBaseName.set(moduleName)
                    jarTask.archiveClassifier.set("")
                }
            } else {
                // Loom: `remapJar` is shipped (classifier-less); the unmapped `jar`
                // keeps a `dev` classifier so the two never collide on disk.
                tasks.named("jar", Jar::class.java).configure { jarTask ->
                    jarTask.archiveBaseName.set(moduleName)
                    jarTask.archiveClassifier.set("dev")
                }
                tasks.named("remapJar", RemapJarTask::class.java).configure { remapJarTask ->
                    remapJarTask.archiveBaseName.set(moduleName)
                    remapJarTask.archiveClassifier.set("")
                    remapJarTask.dependsOn(tasks.named("jar", Jar::class.java))
                }
            }

            tasks.configureEach {
                if (it.javaClass.simpleName == "GenerateModuleMetadata") {
                    it.enabled = false
                }
            }

            val jiJRemapConfig = configurations.create("jiJRemap")
            jiJRemapConfig.isCanBeConsumed = true
            jiJRemapConfig.isCanBeResolved = false

            project.artifacts.add("jiJRemap", tasks.named(mainJarTaskName))
        }

        project.afterEvaluate {
            project.extensions.configure(org.gradle.api.publish.PublishingExtension::class.java) { publishing ->
                publishing.publications.create("mavenJava", MavenPublication::class.java) { publication ->
                    publication.artifactId = moduleName
                    publication.artifact(project.tasks.named(mainJarTaskName).get()) { artifact ->
                        artifact.builtBy(project.tasks.named(mainJarTaskName).get())
                    }
                    publication.artifact(project.tasks.named("sourcesJar", Jar::class.java).get())
                    publication.artifact(project.tasks.named("javadocJar", Jar::class.java).get())
                }

                project.magicUtilsPublishRepository(publishing)
            }
        }
    }
}
