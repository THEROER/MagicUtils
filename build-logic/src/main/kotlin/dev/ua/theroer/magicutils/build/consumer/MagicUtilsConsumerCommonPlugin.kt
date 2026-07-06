package dev.ua.theroer.magicutils.build.consumer

import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*

/**
 * Consumer plugin for a platform-agnostic (common) module of a downstream
 * plugin/mod that depends on MagicUtils.
 *
 * Applies `java-library`, resolves the active MagicUtils target
 * (magicutils.target: Minecraft/Java/loader from gradle/targets.properties +
 * -Ptarget), pins the Java toolchain to the target's Java, and exposes the
 * `magicutilsConsumer { }` extension. The consumer declares MagicUtils modules
 * with [magicUtils], so the mc<major.minor> classifier is never written by hand.
 */
class MagicUtilsConsumerCommonPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-library")
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

        // Add the MagicUtils modules the consumer declared (plain api/impl —
        // common modules are platform-agnostic, no remap).
        project.exposeMagicUtilsTargetFacts(target)
        project.addConsumerMagicUtilsModules(target, "api", "implementation")
    }
}
