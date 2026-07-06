package dev.ua.theroer.magicutils.build.publish

import dev.ua.theroer.magicutils.build.support.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.kotlin.dsl.*

class MagicUtilsPublishingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(MavenPublishPlugin::class.java)
        project.pluginManager.apply("magicutils.common")

        val moduleName = project.magicUtilsModuleName()

        // Production publishing skips the fat build (it exists for local dev only);
        // hide the shadow variant from the component so `:all` is not published.
        // Runs after the shadow plugin has added shadowRuntimeElements to the
        // component (withVariantsFromConfiguration must follow addVariants...).
        if (project.hasProperty("skip_shadow_publish")) {
            project.afterEvaluate {
                (project.components.findByName("java") as? AdhocComponentWithVariants)?.let { java ->
                    project.configurations.findByName("shadowRuntimeElements")?.let { shadow ->
                        java.withVariantsFromConfiguration(shadow) { it.skip() }
                    }
                }
            }
        }

        project.extensions.configure(org.gradle.api.publish.PublishingExtension::class.java) { publishing ->
            publishing.publications.create("mavenJava", MavenPublication::class.java) { publication ->
                publication.artifactId = moduleName
                // The `java` component carries the main jar (classifier-less) plus,
                // unless skipped above, the shadow jar under the `all` classifier —
                // one module, variants by classifier, mirroring the Fabric bundle's
                // `dev`. No separate `-all` artifactId.
                //
                // Bind the component in afterEvaluate: the shadow plugin registers
                // its shadowRuntimeElements variant on the component from its own
                // afterEvaluate hook. Reading the component at apply time (or having
                // publish compute componentArtifacts before that hook runs, as CI's
                // injected plugins reorder it to) throws "Variant for configuration
                // 'shadowRuntimeElements' does not exist in component 'java'".
                // afterEvaluate runs after shadow's registration, so the component
                // is complete.
                project.afterEvaluate {
                    publication.from(project.components.getByName("java"))
                }
            }
        }

        project.extensions.configure(org.gradle.api.publish.PublishingExtension::class.java) { publishing ->
            project.magicUtilsPublishRepository(publishing)
        }

        project.tasks.withType(PublishToMavenLocal::class.java).configureEach { publishTask ->
            publishTask.dependsOn(project.tasks.named("shadowJar"))
        }
    }
}
