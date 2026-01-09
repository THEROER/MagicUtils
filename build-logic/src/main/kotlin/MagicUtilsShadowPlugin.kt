import org.gradle.api.Plugin
import org.gradle.api.Project
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.kotlin.dsl.*

class MagicutilsShadowPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("com.gradleup.shadow")

        project.plugins.withId("com.gradleup.shadow") {

            project.tasks.withType(ShadowJar::class.java).configureEach { shadowJarTask ->
                shadowJarTask.isZip64 = true

                project.configurations.findByName("shadowRuntimeClasspath")?.let { cfg ->
                    shadowJarTask.configurations.set(setOf(cfg))
                }

                shadowJarTask.mergeServiceFiles()
            }
        }
    }
}
