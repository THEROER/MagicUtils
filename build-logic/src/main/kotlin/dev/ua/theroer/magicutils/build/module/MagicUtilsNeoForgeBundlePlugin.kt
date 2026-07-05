package dev.ua.theroer.magicutils.build.module

import dev.ua.theroer.magicutils.build.support.*

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

            // NeoForge ships no Kyori Adventure, so the bundle must carry it (the
            // component serializers the logger/command output need). Bundle the
            // Adventure API + serializers explicitly, since the project deps above
            // are added non-transitively to keep NeoForge itself out of the jar.
            val kyoriAdventure = project.extensions
                .getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
                .named("libs")
                .findVersion("kyoriAdventure")
                .get()
                .requiredVersion
            listOf(
                "net.kyori:adventure-api",
                "net.kyori:adventure-key",
                "net.kyori:adventure-text-serializer-gson",
                "net.kyori:adventure-text-serializer-json",
                "net.kyori:adventure-text-serializer-plain",
                "net.kyori:adventure-text-minimessage",
            ).forEach { coord ->
                dependencies.add("bundleContents", "$coord:$kyoriAdventure")
            }
            listOf(
                "net.kyori:examination-api:1.3.0",
                "net.kyori:examination-string:1.3.0",
                "net.kyori:option:1.1.0",
            ).forEach { coord ->
                dependencies.add("bundleContents", coord)
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
                    // Strip every module-info (top-level AND multi-release, e.g.
                    // snakeyaml's META-INF/versions/9/module-info.class). Left in, they
                    // put the bundled libs into a JPMS module when NeoForge loads the
                    // bundle as a standalone JiJ mod, which breaks class loading.
                    "**/module-info.class",
                    // Gson is provided by Minecraft/NeoForge on the game classloader.
                    // Shipping a second, non-relocated com.google.gson in the JiJ bundle
                    // clashes with it: MC's DetectedVersion parses version.json via gson,
                    // and the duplicate silently breaks that, leaving the game version
                    // unset ("Game version not set") before the client even starts.
                    // Adventure's gson serializer then binds to MC's gson instead.
                    "com/google/gson/**",
                )
            }

            tasks.configureEach {
                if (it.javaClass.simpleName == "GenerateModuleMetadata") {
                    it.enabled = false
                }
            }
        }

        // The real fat jar for this bundle is the `jar` task (it inlines
        // bundleContents), not `shadowJar`. The shadow plugin comes in via
        // magicutils.java-library and registers a `shadowRuntimeElements` variant
        // (the 36M `:all` archive that also drags in NeoForge/Minecraft) into the
        // `java` component. Unlike the shaded modules, this bundle never wants that
        // variant published, so drop it from the component unconditionally rather
        // than gating on skip_shadow_publish. Must run after the shadow plugin has
        // added the variant, hence afterEvaluate.
        project.afterEvaluate {
            (project.components.findByName("java") as? org.gradle.api.component.AdhocComponentWithVariants)?.let { java ->
                project.configurations.findByName("shadowRuntimeElements")?.let { shadow ->
                    java.withVariantsFromConfiguration(shadow) { it.skip() }
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
