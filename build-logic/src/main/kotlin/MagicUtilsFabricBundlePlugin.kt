import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

class MagicutilsFabricBundlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("magicutils.java-library")
        project.pluginManager.apply("fabric-loom")
        project.pluginManager.apply("magicutils.target")
        project.pluginManager.apply("magicutils.common")

        val magicutilsTarget = project.extensions.getByType(MagicutilsTargetExtension::class.java)
        val getModuleName = project.extensions.extraProperties.get("getModuleName") as ((String) -> String)
        val moduleName = getModuleName(project.name)

        with(project) {

            val bundleShadowConfig = configurations.create("bundleShadow")
            bundleShadowConfig.isCanBeConsumed = false
            bundleShadowConfig.isCanBeResolved = true
            bundleShadowConfig.attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))

            val bundleProjects = listOf(
                project(":platform-api"),
                project(":logger"),
                project(":commands"),
                project(":placeholders"),
                project(":core")
            )

            val bundleShadedProjects = listOf(
                project(":config"),
                project(":config-yaml"),
                project(":config-toml"),
                project(":lang")
            )

            val bundleRemappedProjects = listOf(
                project(":platform-fabric"),
                project(":logger-fabric"),
                project(":commands-fabric"),
                project(":placeholders-fabric")
            )

            val bundleNamedProjects = listOf(
                project(":platform-fabric"),
                project(":logger-fabric"),
                project(":commands-fabric"),
                project(":placeholders-fabric")
            )

            project.dependencies.add("minecraft", "com.mojang:minecraft:${magicutilsTarget.minecraft.get()}")
            project.dependencies.add("mappings", "net.fabricmc:yarn:${magicutilsTarget.yarn.get()}:v2")
            project.dependencies.add("modImplementation", "net.fabricmc:fabric-loader:${magicutilsTarget.loader.get()}")

            project.dependencies.add("include", "net.kyori:adventure-api:4.24.0")
            project.dependencies.add("include", "net.kyori:adventure-key:4.24.0")
            project.dependencies.add("include", "net.kyori:adventure-text-minimessage:4.24.0")
            project.dependencies.add("include", "net.kyori:adventure-text-serializer-plain:4.24.0")
            project.dependencies.add("include", "net.kyori:adventure-text-serializer-gson:4.24.0")
            project.dependencies.add("include", "net.kyori:adventure-text-serializer-ansi:4.24.0")
            project.dependencies.add("include", "net.kyori:adventure-text-serializer-json:4.24.0")
            project.dependencies.add("include", "net.kyori:ansi:1.1.1")
            project.dependencies.add("include", "net.kyori:examination-api:1.3.0")
            project.dependencies.add("include", "net.kyori:examination-string:1.3.0")
            project.dependencies.add("include", "net.kyori:option:1.1.0")

            bundleShadedProjects.forEach { dep ->
                val depProject = project(dep.path)
                val depDependency = project.dependencies.add("include", depProject) as org.gradle.api.artifacts.ProjectDependency
                depDependency.targetConfiguration = "shadedElements"
            }
            bundleProjects.forEach { dep ->
                project.dependencies.add("include", project(dep.path))
            }
            bundleRemappedProjects.forEach { dep ->
                val depProject = project(dep.path)
                val depDependency = project.dependencies.add("include", depProject) as org.gradle.api.artifacts.ProjectDependency
                depDependency.targetConfiguration = "jiJRemap"
            }

            bundleShadedProjects.forEach { dep ->
                val depProject = project(dep.path)
                val depDependency = project.dependencies.add("bundleShadow", depProject) as org.gradle.api.artifacts.ProjectDependency
                depDependency.targetConfiguration = "shadedElements"
            }
            bundleProjects.forEach { dep ->
                project.dependencies.add("bundleShadow", project(dep.path))
            }
            bundleNamedProjects.forEach { dep ->
                val depProject = project(dep.path)
                val depDependency = project.dependencies.add("bundleShadow", depProject) as org.gradle.api.artifacts.ProjectDependency
                depDependency.targetConfiguration = "namedElements"
            }

            tasks.named("remapJar", RemapJarTask::class.java).configure { remapJarTask ->
                remapJarTask.archiveClassifier.set("mc${magicutilsTarget.minecraft.get().substringBeforeLast('.')}")
                remapJarTask.archiveBaseName.set(moduleName)
            }

            tasks.named("shadowJar", ShadowJar::class.java).configure { shadowJarTask ->
                shadowJarTask.archiveClassifier.set("dev")
            
                shadowJarTask.configurations.set(
                    setOf(project.configurations.getByName("bundleShadow"))
                )
            
                shadowJarTask.exclude("fabric.mod.json")
                shadowJarTask.mergeServiceFiles()
                val processResources = tasks.named("processResources", ProcessResources::class.java)
                shadowJarTask.from(processResources) {
                    it.include("fabric.mod.json")
                }
            }

            tasks.configureEach {
                if (it.javaClass.simpleName == "GenerateModuleMetadata") {
                    it.enabled = false
                }
            }
        }

        project.extensions.configure(org.gradle.api.publish.PublishingExtension::class.java) { publishing ->
            publishing.publications.create("mavenJava", MavenPublication::class.java) { publication ->
                publication.artifactId = moduleName
                publication.artifact(project.tasks.named("remapJar", RemapJarTask::class.java).get()) { artifact ->
                    artifact.builtBy(project.tasks.named("remapJar").get())
                }
                publication.artifact(project.tasks.named("sourcesJar", Jar::class.java).get())
                publication.artifact(project.tasks.named("javadocJar", Jar::class.java).get())
                publication.artifact(project.tasks.named("shadowJar", ShadowJar::class.java).get()) { artifact ->
                    artifact.classifier = "dev"
                }
                publication.pom.withXml { xml ->
                    xml.asElement().getElementsByTagName("dependencies").item(0)?.let { node ->
                        node.parentNode.removeChild(node)
                    }
                }
            }
        }
    }
}
