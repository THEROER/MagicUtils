import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*

class MagicUtilsNeoForgeBundlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("magicutils.java-library")
        project.pluginManager.apply("magicutils.common")
        project.pluginManager.apply("magicutils.target")

        val moduleName = project.magicUtilsModuleName()

        with(project) {
            val bundleContents = configurations.create("bundleContents")
            bundleContents.isCanBeConsumed = false
            bundleContents.isCanBeResolved = true
            bundleContents.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                objects.named(Usage::class.java, Usage.JAVA_RUNTIME),
            )

            fun addBundleProject(path: String, targetConfiguration: String? = null) {
                val dependency = dependencies.add("bundleContents", project(path)) as ProjectDependency
                dependency.isTransitive = false
                if (targetConfiguration != null) {
                    dependency.targetConfiguration = targetConfiguration
                }
                dependencies.add("compileOnly", project(path))
            }

            listOf(
                ":platform-api",
                ":logger",
                ":commands",
                ":commands-brigadier",
                ":placeholders",
                ":core",
                ":diagnostics",
                ":http-client",
                ":platform-neoforge",
                ":commands-neoforge",
            ).forEach(::addBundleProject)

            listOf(
                ":config",
                ":config-yaml",
                ":config-toml",
                ":lang",
            ).forEach { path ->
                addBundleProject(path, "shadedElements")
            }

            tasks.named("jar", Jar::class.java).configure { jarTask ->
                jarTask.archiveBaseName.set(moduleName)
                jarTask.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                jarTask.dependsOn(bundleContents)
                jarTask.inputs.files(bundleContents)
                    .withPropertyName("bundleContents")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                jarTask.from(provider {
                    bundleContents.files.map { file ->
                        if (file.isDirectory) file else zipTree(file)
                    }
                })
                jarTask.exclude(
                    "META-INF/MANIFEST.MF",
                    "META-INF/*.DSA",
                    "META-INF/*.RSA",
                    "META-INF/*.SF",
                    "fabric.mod.json",
                    "module-info.class",
                    "net/kyori/**",
                )
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
                publication.from(project.components.getByName("java"))
                publication.pom.withXml { xml ->
                    xml.asElement().getElementsByTagName("dependencies").item(0)?.let { node ->
                        node.parentNode.removeChild(node)
                    }
                }
            }

            project.magicUtilsPublishRepository(publishing)
        }
    }
}
