package dev.ua.theroer.magicutils.build.consumer

import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*

/**
 * Consumer plugin for the NeoForge module of a downstream mod.
 *
 * NeoForge is driven by ModDevGradle (`net.neoforged.moddev`), not the jpenilla
 * run-* plugins: there is no `pluginJars`/`downloadPlugins`, the mod is loaded by
 * copying its jar into the run directory's `mods/` folder. This plugin applies
 * moddev, pins the toolchain and the NeoForge version to the active target,
 * wires the consumer's MagicUtils modules, and, on `devServer {}` opt-in,
 * feeds the consumer-declared run with the jar-sync into `run/mods`.
 *
 * The moddev `neoForge {}` extension is configured reflectively so build-logic
 * does not compile against moddev's API (moddev is applied by id, resolved from
 * the consumer's plugin repositories).
 */
class MagicUtilsConsumerNeoForgePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("magicutils.target")
        project.pluginManager.apply("net.neoforged.moddev")

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

        // neoForge { version = <target neoforge> } via the moddev extension,
        // reached reflectively to keep moddev off build-logic's compile classpath.
        // Use the String setter (setVersion) rather than getVersion().set(...):
        // in current ModDevGradle getVersion() throws "Mod development has not
        // been enabled yet" until a version is set, so reading the property first
        // is not allowed. setVersion is what enables the workflow.
        val neoForge = project.extensions.getByName("neoForge")
        neoForge.javaClass.methods
            .first { it.name == "setVersion" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
            .invoke(neoForge, target.neoforge.get())

        project.exposeMagicUtilsTargetFacts(target)
        project.addConsumerMagicUtilsModules(target, "api", "implementation")

        // Embed the MagicUtils NeoForge bundle as a nested mod (jar-in-jar via
        // ModDevGradle's `jarJar` configuration), symmetric to the Fabric plugin's
        // `include` of magicutils-fabric-bundle. NeoForge loads the bundle under
        // its own mod — mixins/entrypoint/metadata intact — so the consumer never
        // spells out the coordinate or the JiJ wiring. The bundle jar is
        // classifier-less (the branch is in the +<minecraft> version). Opt out
        // with `magicutils_embed=false` / `magicutilsConsumer { embedMode =
        // EmbedMode.EXTERNAL }` when the consumer bundles MagicUtils some other way
        // (e.g. shaded via its own embeddedRuntime), as verified-plugin does.
        // AUTO → JAR_IN_JAR on NeoForge; SHADED is rejected here (fail-fast).
        val embed = resolveEmbedMode(consumer.embedMode.get(), ConsumerLoader.NEOFORGE)
        if (embed == EmbedMode.JAR_IN_JAR) {
            val bundleCoordinate = project.provider {
                val version = target.publishedVersion("magicutils-neoforge-bundle", consumer.magicutilsVersion.get())
                project.dependencies.create("dev.ua.theroer:magicutils-neoforge-bundle:$version")
            }
            // moddev registers `jarJar` at apply time, but hook it via
            // matching/configureEach (not `named`) so a late-created configuration
            // is still fed, mirroring the Fabric plugin's defensive `include` wiring.
            project.configurations.matching { it.name == "jarJar" }.configureEach {
                it.dependencies.addLater(bundleCoordinate)
            }
        }

        project.afterEvaluate {
            val spec = consumer.devServerSpec.orNull ?: return@afterEvaluate
            MagicUtilsDevServer.configureNeoForge(
                project = project,
                spec = spec,
                targetMinecraftVersion = target.minecraft.get(),
                mcClassifier = target.mcClassifier,
            )
        }
    }
}
