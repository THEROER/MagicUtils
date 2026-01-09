import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask

class MagicutilsFabricModulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("magicutils.java-library")
        project.pluginManager.apply("fabric-loom")
        project.pluginManager.apply("magicutils.target")
        project.pluginManager.apply("magicutils.common")
        project.pluginManager.apply("magicutils.shadow")

        val shadowRuntimeClasspath =
            project.configurations.create("shadowRuntimeClasspath")

        shadowRuntimeClasspath.isCanBeResolved = true
        shadowRuntimeClasspath.isCanBeConsumed = false

        val magicutilsTarget = project.extensions.getByType(MagicutilsTargetExtension::class.java)
        val getModuleName = project.extensions.extraProperties.get("getModuleName") as ((String) -> String)
        val moduleName = getModuleName(project.name)

        with(project) {
            project.dependencies.add("minecraft", "com.mojang:minecraft:${magicutilsTarget.minecraft.get()}")
            project.dependencies.add("mappings", loomMappingsString(magicutilsTarget.yarn.get()))
            project.dependencies.add("modCompileOnly", "net.fabricmc:fabric-loader:${magicutilsTarget.loader.get()}")
            project.dependencies.add("modCompileOnly", "eu.pb4:placeholder-api:${magicutilsTarget.pb4_placeholder_api.get()}")
            project.dependencies.add("modCompileOnly", "io.github.miniplaceholders:miniplaceholders-api:${magicutilsTarget.miniplaceholders_api.get()}")

            // Configure remapJar as the main artifact
            tasks.named("remapJar", RemapJarTask::class.java).configure { remapJarTask ->
                remapJarTask.archiveBaseName.set(moduleName)
                remapJarTask.archiveClassifier.set("mc${magicutilsTarget.minecraft.get().substringBeforeLast('.')}")
                remapJarTask.dependsOn(tasks.named("jar", Jar::class.java))
            }

            tasks.configureEach {
                if (it.javaClass.simpleName == "GenerateModuleMetadata") {
                    it.enabled = false
                }
            }

            val jiJRemapConfig = configurations.create("jiJRemap")
            jiJRemapConfig.isCanBeConsumed = true
            jiJRemapConfig.isCanBeResolved = false

            project.artifacts.add("jiJRemap", tasks.named("remapJar", RemapJarTask::class.java))
        }

        project.afterEvaluate {
            project.extensions.configure(org.gradle.api.publish.PublishingExtension::class.java) { publishing ->
                publishing.publications.create("mavenJava", MavenPublication::class.java) { publication ->
                    publication.artifactId = moduleName
                    publication.artifact(project.tasks.named("remapJar", RemapJarTask::class.java).get()) { artifact ->
                        artifact.builtBy(project.tasks.named("remapJar").get())
                    }
                    publication.artifact(project.tasks.named("sourcesJar", Jar::class.java).get())
                    publication.artifact(project.tasks.named("javadocJar", Jar::class.java).get())
                }
            }
        }
    }

    private fun loomMappingsString(yarnVersion: String): String {
        return "net.fabricmc:yarn:$yarnVersion:v2"
    }
}
