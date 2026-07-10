package dev.ua.theroer.magicutils.build.consumer

import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*

/**
 * Consumer plugin for the Velocity module of a downstream plugin.
 *
 * Velocity's API is decoupled from the Minecraft version, so unlike bukkit/fabric
 * there is no per-target API here: the version comes from the `velocityApiVersion`
 * gradle property (default [DEFAULT_VELOCITY_API]). The plugin applies
 * `java-library` + shadow, pins the Java toolchain to the active target, adds the
 * Velocity API (compileOnly + annotationProcessor), wires the consumer's
 * MagicUtils modules per [EmbedMode] (like the Bukkit consumer, since Velocity is
 * a plain JVM plugin on a flat proxy classpath), and — on `devServer {}` opt-in —
 * the `runVelocity` runner.
 *
 * [EmbedMode] mapping (Velocity has no jar-in-jar, so JAR_IN_JAR is rejected):
 *   SHADED   → `api`/`implementation`, so shadow relocates the modules into the
 *             fat jar (a self-contained plugin). Velocity default (AUTO ⇒ SHADED).
 *   EXTERNAL → `compileOnly`, and `dev/ua/theroer/magicutils` is stripped from
 *             the shadow jar, so the built jar is thin and expects the standalone
 *             `velocity-bundle` plugin installed beside it on the proxy. Use this
 *             when several MagicUtils consumers share one proxy.
 */
class MagicUtilsConsumerVelocityPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("com.gradleup.shadow")
        project.pluginManager.apply("magicutils.target")
        project.pluginManager.apply("xyz.jpenilla.run-velocity")

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

        val velocityApiVersion = project.providers.gradleProperty("velocityApiVersion")
            .orElse(DEFAULT_VELOCITY_API).get()
        project.dependencies.add("compileOnly", "com.velocitypowered:velocity-api:$velocityApiVersion")
        project.dependencies.add("annotationProcessor", "com.velocitypowered:velocity-api:$velocityApiVersion")

        project.exposeMagicUtilsTargetFacts(target)

        // MagicUtils modules per embedMode + the EXTERNAL shadow-strip — shared
        // with the Bukkit consumer (see configureJvmConsumerEmbed). Velocity's
        // default is SHADED (self-contained proxy plugin); EXTERNAL yields a thin
        // jar that expects the standalone velocity-bundle beside it on the proxy.
        project.configureJvmConsumerEmbed(target, ConsumerLoader.VELOCITY)

        project.afterEvaluate {
            val spec = consumer.devServerSpec.orNull ?: return@afterEvaluate
            MagicUtilsDevServer.configureVelocity(
                project = project,
                spec = spec,
                velocityVersion = velocityApiVersion,
                mcClassifier = target.mcClassifier,
                pluginArtifact = {
                    project.tasks.named("shadowJar", org.gradle.api.tasks.bundling.AbstractArchiveTask::class.java)
                        .flatMap { it.archiveFile }
                },
            )
        }
    }

    companion object {
        const val DEFAULT_VELOCITY_API = "3.1.1"
    }
}
