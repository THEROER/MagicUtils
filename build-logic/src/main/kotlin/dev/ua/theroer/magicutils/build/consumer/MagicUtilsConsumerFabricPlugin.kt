package dev.ua.theroer.magicutils.build.consumer

import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*

/**
 * Consumer plugin for the Fabric module of a downstream mod.
 *
 * This is the plugin that hides the hard part. For the active MagicUtils target
 * it: applies the right Loom flavour (remap on obfuscated <26, no-remap on
 * deobfuscated 26.x), adds Minecraft + Mojang mappings (obfuscated only) +
 * Fabric loader + Fabric API from the target, pins the Java toolchain, and
 * wires the `magicutils-fabric-bundle` dependency with the correct classifier
 * and configuration (jar-in-jar `include` + mod compile/runtime on obfuscated,
 * or plain otherwise). The consumer only applies `magicutils.consumer-fabric`
 * and declares its own project dependencies.
 *
 * Additional MagicUtils modules (e.g. `magicutils-http-client`) are declared
 * with [magicUtilsFabric], which returns the coordinate with the right
 * classifier so the consumer never spells it out.
 */
class MagicUtilsConsumerFabricPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("magicutils.target")

        val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
        // Applying the Loom plugin inside a binary plugin registers the `loom`
        // extension synchronously (unlike `apply false` + imperative apply in a
        // consumer .kts), so mappings/configs resolve cleanly below.
        project.pluginManager.apply(target.loomPluginId)

        val consumer = project.magicUtilsConsumerExtension()

        project.exposeMagicUtilsTargetFacts(target)

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(JavaLanguageVersion.of(target.java.get()))
            java.withSourcesJar()
        }
        project.tasks.withType(JavaCompile::class.java).configureEach { task ->
            task.options.release.set(target.java.get())
            task.options.compilerArgs.add("-parameters")
        }

        // Minecraft + Mojang mappings (obfuscated only). Loader/fabric-api use
        // the target's mod-aware implementation configuration.
        applyMinecraftAndMappings(project, target)
        val impl = target.implementationConfiguration
        val compileOnly = target.compileOnlyConfiguration
        val runtimeOnly = target.runtimeOnlyConfiguration
        project.dependencies.add(impl, "net.fabricmc:fabric-loader:${target.loader.get()}")
        project.dependencies.add(impl, "net.fabricmc.fabric-api:fabric-api:${target.fabric_api.get()}")

        // MagicUtils fabric bundle. The shipped jar is classifier-less (the branch
        // is in the +<minecraft> version). On obfuscated targets Loom ships a
        // remapped jar (thin, JiJ'd) and a fat `:dev` jar (named, adventure inline)
        // for compile/dev-runtime; on deobfuscated 26.x there is no remap, so the
        // classifier-less jar is itself the fat, self-contained artifact used for
        // both publish and dev runtime.
        val version = target.publishedVersion(consumer.magicutilsVersion.get())
        val module = "dev.ua.theroer:magicutils-fabric-bundle:$version"
        val shippedDep = module
        val fatDep = if (target.isDeobfuscated) shippedDep else "$module:dev"

        project.dependencies.add(compileOnly, fatDep)
        // AUTO → JAR_IN_JAR on Fabric; SHADED is rejected here (fail-fast). Only
        // JAR_IN_JAR actually JiJ's the bundle; EXTERNAL leaves it to the runtime.
        val embed = resolveEmbedMode(consumer.embedMode.get(), ConsumerLoader.FABRIC)
        if (embed == EmbedMode.JAR_IN_JAR) {
            project.dependencies.add("include", shippedDep)
        }
        // Dev runtime needs the fat jar on the classpath (JiJ is not exploded by
        // the dev launcher). Added as a plain runtime dependency, not a loadable
        // dev mod, so it does not re-declare the bundle's own hard-deps.
        project.dependencies.add(runtimeOnly, fatDep)

        // Extra MagicUtils modules the consumer declared (e.g. http-client),
        // resolved to the target classifier on the mod-aware configuration.
        project.addConsumerMagicUtilsModules(target, apiConfiguration = impl, implementationConfiguration = impl)

        // Third-party libraries the consumer wants jar-in-jar'd: `include` (JiJ
        // into the published mod) + the mod-aware implementation config (compile
        // + dev runtime). Lazy so it survives Loom's early configuration observe.
        val bundledLibs = consumer.bundledLibraryCoordinates.map { coords ->
            coords.map { project.dependencies.create(it) }
        }
        project.configurations.named("include").configure { it.dependencies.addAllLater(bundledLibs) }
        project.configurations.named(impl).configure { it.dependencies.addAllLater(bundledLibs) }

        // Modrinth dev mods on modLocalRuntime, wired here in the main phase (not
        // afterEvaluate) with a lazy provider so it survives Loom's early observe.
        MagicUtilsDevServer.fabricModLocalRuntime(project, consumer, target.minecraft.get(), target.mcClassifier)

        // Local dev server (Loom's runServer), only on `devServer {}` opt-in. Loom
        // already registers runServer for the active target; here we only stamp
        // the run directory's server.properties with the resolved MOTD/port.
        project.afterEvaluate {
            val spec = consumer.devServerSpec.orNull ?: return@afterEvaluate
            MagicUtilsDevServer.configureFabric(project, spec, target.minecraft.get())
        }
    }
}
