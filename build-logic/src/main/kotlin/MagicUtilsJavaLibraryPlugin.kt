import org.gradle.api.JavaVersion
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
            javaExtension.toolchain.languageVersion.set(JavaLanguageVersion.of(magicutilsTarget.java.get()))
            javaExtension.sourceCompatibility = JavaVersion.toVersion(magicutilsTarget.java.get())
            javaExtension.targetCompatibility = JavaVersion.toVersion(magicutilsTarget.java.get())

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

        val getModuleName = project.extensions.extraProperties.get("getModuleName") as ((String) -> String)
        val moduleName = getModuleName(project.name)

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
