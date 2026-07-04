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

        project.tasks.withType(Jar::class.java).configureEach { jarTask ->
            jarTask.archiveBaseName.set(moduleName)
        }

        project.tasks.named("jar", Jar::class.java).configure { jarTask ->
            if (project.name != "processor" && !project.plugins.hasPlugin("fabric-loom")) {
                jarTask.archiveClassifier.set("mc${magicutilsTarget.minecraft.get().substringBeforeLast('.')}")
            }
        }
    }
}
