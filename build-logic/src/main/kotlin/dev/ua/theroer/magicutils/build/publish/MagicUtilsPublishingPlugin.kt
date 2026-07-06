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
        //
        // withVariantsFromConfiguration throws "Variant for configuration
        // 'shadowRuntimeElements' does not exist in component 'java'" unless the
        // shadow plugin has already added that variant to the component. It does so
        // from its own afterEvaluate hook, whose ordering vs. this one is not
        // guaranteed — and CI's injected plugins (Develocity via setup-gradle)
        // reorder it so ours runs first, which failed every publish. Modules
        // without real shading (e.g. :processor) never register the variant at all,
        // so there is simply nothing to skip. Guard the call: only skip when the
        // variant is actually present on the component.
        if (project.hasProperty("skip_shadow_publish")) {
            project.plugins.withId("com.gradleup.shadow") {
                project.afterEvaluate {
                    (project.components.findByName("java") as? AdhocComponentWithVariants)?.let { java ->
                        project.configurations.findByName("shadowRuntimeElements")?.let { shadow ->
                            runCatching { java.withVariantsFromConfiguration(shadow) { it.skip() } }
                        }
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
                publication.from(project.components.getByName("java"))
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
