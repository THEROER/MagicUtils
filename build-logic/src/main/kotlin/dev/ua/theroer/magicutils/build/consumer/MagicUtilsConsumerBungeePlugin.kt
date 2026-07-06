package dev.ua.theroer.magicutils.build.consumer

import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*

/**
 * Consumer plugin for the BungeeCord/Waterfall module of a downstream plugin.
 *
 * Like Velocity, the BungeeCord API is decoupled from the Minecraft version, so
 * there is no per-target API: the version comes from the `bungeeApiVersion`
 * gradle property (default [DEFAULT_BUNGEE_API]). Applies `java-library` +
 * shadow, pins the toolchain to the active target, adds the BungeeCord API
 * (compileOnly), wires the consumer's MagicUtils modules, and — on `devServer {}`
 * opt-in — the `runWaterfall` runner.
 */
class MagicUtilsConsumerBungeePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("com.gradleup.shadow")
        project.pluginManager.apply("magicutils.target")
        project.pluginManager.apply("xyz.jpenilla.run-waterfall")

        val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
        val consumer = project.magicUtilsConsumerExtension()

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(JavaLanguageVersion.of(target.java.get()))
            java.withSourcesJar()
        }
        project.tasks.withType(JavaCompile::class.java).configureEach { task ->
            task.options.release.set(target.java.get())
            task.options.compilerArgs.add("-parameters")
        }

        val bungeeApiVersion = project.providers.gradleProperty("bungeeApiVersion")
            .orElse(DEFAULT_BUNGEE_API).get()
        project.dependencies.add("compileOnly", "net.md-5:bungeecord-api:$bungeeApiVersion")

        project.exposeMagicUtilsTargetFacts(target)
        project.addConsumerMagicUtilsModules(target, "api", "implementation")

        project.afterEvaluate {
            val spec = consumer.devServerSpec.orNull ?: return@afterEvaluate
            MagicUtilsDevServer.configureWaterfall(
                project = project,
                spec = spec,
                waterfallVersion = bungeeApiVersion.substringBefore("-"),
                mcClassifier = target.mcClassifier,
                pluginArtifact = {
                    project.tasks.named("shadowJar", org.gradle.api.tasks.bundling.AbstractArchiveTask::class.java)
                        .flatMap { it.archiveFile }
                },
            )
        }
    }

    companion object {
        const val DEFAULT_BUNGEE_API = "1.20-R0.1"
    }
}
