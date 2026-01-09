import org.gradle.api.Plugin
import org.gradle.api.Project
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.attributes.Usage
import org.gradle.kotlin.dsl.*

class MagicutilsShadedModulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("magicutils.shadow")

        project.tasks.withType(ShadowJar::class.java).configureEach { shadowJarTask ->
            shadowJarTask.relocate("com.fasterxml.jackson", "dev.ua.theroer.magicutils.libs.jackson")
        }

        val shadedElementsConfig = project.configurations.create("shadedElements")
        shadedElementsConfig.isCanBeConsumed = true
        shadedElementsConfig.isCanBeResolved = false
        shadedElementsConfig.attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))

        project.artifacts.add("shadedElements", project.tasks.named("shadowJar", ShadowJar::class.java).get())
    }
}
