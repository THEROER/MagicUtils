package dev.ua.theroer.magicutils.build.consumer

import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*

/**
 * Consumer plugin for the Bukkit/Paper module of a downstream plugin.
 *
 * Bukkit's API is stable across the whole range, so there is no obf/deobf
 * split here — one artifact covers 1.21.x .. 26.2. The plugin applies
 * `java-library` + shadow, resolves the active target, pins the toolchain to
 * the target's Java, and adds the Paper API (compileOnly, version from the
 * target). MagicUtils modules are declared with [magicUtils]; the classifier is
 * derived from the target.
 */
class MagicUtilsConsumerBukkitPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("com.gradleup.shadow")
        project.pluginManager.apply("magicutils.target")

        val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
        project.magicUtilsConsumerExtension()

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(JavaLanguageVersion.of(target.java.get()))
            java.withSourcesJar()
        }

        project.tasks.withType(JavaCompile::class.java).configureEach { task ->
            task.options.release.set(target.java.get())
            task.options.compilerArgs.add("-parameters")
        }

        // Paper API for the active target (compileOnly — provided by the server).
        project.dependencies.add("compileOnly", "io.papermc.paper:paper-api:${target.paper.get()}")

        project.exposeMagicUtilsTargetFacts(target)

        // MagicUtils modules the consumer declared (plain api/impl — Bukkit has
        // no remap).
        project.addConsumerMagicUtilsModules(target, "api", "implementation")

        // Local dev server (runPaper/runFolia), only when the consumer opted in
        // with `magicutilsConsumer { devServer { ... } }`. The Folia runner is
        // registered because a MagicUtils-based plugin is Folia-compatible by
        // construction — running it there verifies that locally. The plugin jar
        // loaded is the consumer's shadow jar; consumers needing the standalone
        // MagicUtils bukkit bundle as a separate server plugin add it via
        // `devServerExtraPlugins`.
        project.afterEvaluate {
            val consumer = project.magicUtilsConsumerExtension()
            val spec = consumer.devServerSpec.orNull ?: return@afterEvaluate
            MagicUtilsDevServer.configureBukkit(
                project = project,
                spec = spec,
                targetMinecraftVersion = target.minecraft.get(),
                mcClassifier = target.mcClassifier,
                pluginArtifact = {
                    project.tasks.named("shadowJar", org.gradle.api.tasks.bundling.AbstractArchiveTask::class.java)
                        .flatMap { it.archiveFile }
                },
                extraPlugins = emptyList(),
            )
        }
    }
}
