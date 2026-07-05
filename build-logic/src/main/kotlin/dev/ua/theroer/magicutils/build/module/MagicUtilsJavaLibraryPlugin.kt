package dev.ua.theroer.magicutils.build.module

import dev.ua.theroer.magicutils.build.support.*
import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.api.tasks.testing.Test
import org.gradle.api.file.FileCopyDetails
import org.gradle.kotlin.dsl.*
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * Single JDK used to compile every target. Bytecode compatibility with a
 * target's minimum Java is governed per-target by `options.release`
 * (see below), so one modern toolchain compiles all targets — no need to
 * provision a separate JDK per Minecraft version.
 */
const val MAGICUTILS_BUILD_JDK = 25

class MagicUtilsJavaLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("maven-publish")
        project.pluginManager.apply("com.gradleup.shadow")
        project.pluginManager.apply("magicutils.repositories")
        project.pluginManager.apply("magicutils.target")
        project.pluginManager.apply("magicutils.common")

        val magicutilsTarget = project.extensions.getByType(MagicUtilsTargetExtension::class.java)

        project.extensions.configure(JavaPluginExtension::class.java) { javaExtension ->
            // One fixed toolchain for all targets; per-target bytecode level is
            // set via options.release below, not via source/targetCompatibility.
            javaExtension.toolchain.languageVersion.set(JavaLanguageVersion.of(MAGICUTILS_BUILD_JDK))

            javaExtension.withSourcesJar()
            javaExtension.withJavadocJar()
        }

        project.tasks.withType(JavaCompile::class.java).configureEach { javaCompileTask ->
            javaCompileTask.options.release.set(magicutilsTarget.java.get())
            javaCompileTask.options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-parameters"))
        }

        // Bytecode is compiled with `options.release = target.java`, but Gradle
        // derives the published `org.gradle.jvm.version` metadata attribute from
        // the *toolchain* (MAGICUTILS_BUILD_JDK = 25), not from `release`. That
        // mismatch makes consumers on the target's own Java (e.g. 21 for 1.21.x)
        // reject the artifact as "only compatible with JVM 25". Pin the
        // TargetJvmVersion attribute on the consumable JVM variants to the
        // target's Java so metadata matches the actual bytecode level.
        val targetJvm = magicutilsTarget.java.get()
        listOf("apiElements", "runtimeElements").forEach { configName ->
            project.configurations.matching { it.name == configName }.configureEach { configuration ->
                configuration.attributes.attribute(
                    org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                    targetJvm,
                )
            }
        }

        project.tasks.withType(Test::class.java).configureEach { testTask ->
            testTask.useJUnitPlatform()
        }

        project.tasks.withType(ProcessResources::class.java).configureEach { resourcesTask ->
            resourcesTask.inputs.property("version", project.version)
            resourcesTask.filesMatching("fabric.mod.json") { details: FileCopyDetails ->
                details.expand(mapOf("version" to project.version))
            }
        }

        val moduleName = project.magicUtilsModuleName()

        // The Minecraft branch lives in the published version (`+<minecraft>`),
        // so the main jar carries no classifier — same as fabric-api. Classifiers
        // are reserved for genuinely different jars (`all`, `dev`, sources).
        project.tasks.withType(Jar::class.java).configureEach { jarTask ->
            jarTask.archiveBaseName.set(moduleName)
        }
    }
}
