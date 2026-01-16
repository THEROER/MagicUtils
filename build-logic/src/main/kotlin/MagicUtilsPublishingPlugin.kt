import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.kotlin.dsl.*
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

class MagicUtilsPublishingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(MavenPublishPlugin::class.java)
        project.pluginManager.apply("magicutils.common")

        val getModuleName = project.extensions.extraProperties.get("getModuleName") as ((String) -> String)
        val moduleName = getModuleName(project.name)

        val skipShadowPublish = project.hasProperty("skip_shadow_publish")

        val isFabricOrBundle = project.name in setOf(
            "platform-fabric", "commands-fabric", "logger-fabric", "placeholders-fabric", "fabric-bundle"
        )

        if (!isFabricOrBundle) {
            project.extensions.configure(org.gradle.api.publish.PublishingExtension::class.java) { publishing ->
                publishing.publications.create("mavenJava", MavenPublication::class.java) { publication ->
                    publication.artifactId = moduleName
                    publication.from(project.components.getByName("java"))
                }
                if (project.name != "processor" && !skipShadowPublish) {
                    publishing.publications.create("mavenShadow", MavenPublication::class.java) { publication ->
                        publication.artifactId = "${moduleName}-all"
                        publication.artifact(project.tasks.named("shadowJar", ShadowJar::class.java).get())
                    }
                }
            }
        }

        project.extensions.configure(org.gradle.api.publish.PublishingExtension::class.java) { publishing ->
            if (project.hasProperty("publish_repo")) {
                publishing.repositories.maven { repo ->
                    repo.name = "ghPages"
                    repo.url = project.uri(project.property("publish_repo") as String)
                }
            }
        }

        project.tasks.withType(PublishToMavenLocal::class.java).configureEach { publishTask ->
            publishTask.dependsOn(project.tasks.named("shadowJar"))
        }
    }
}
