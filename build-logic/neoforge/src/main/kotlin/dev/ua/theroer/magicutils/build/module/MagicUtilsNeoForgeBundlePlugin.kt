package dev.ua.theroer.magicutils.build.module

import dev.ua.theroer.magicutils.build.support.*
import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.kotlin.dsl.*

class MagicUtilsNeoForgeBundlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("magicutils.java-library")
        project.pluginManager.apply("magicutils.common")
        project.pluginManager.apply("magicutils.target")

        val moduleName = project.magicUtilsModuleName()
        val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)

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
                ":messaging",
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
            // Must be the target's Adventure major, not the catalog default: the modules
            // inlined above are compiled against it (magicUtilsAlignAdventure), so pinning
            // the catalog version here would ship Adventure 4 classes next to bytecode
            // built for Adventure 5 on the 26.x branch.
            val kyoriAdventure = magicUtilsAdventureVersion(project, target)
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
            dependencies.add("bundleContents", "net.kyori:option:1.1.0")
            // examination-* is Adventure 4 only; 5.x replaced it with jspecify.
            if (!target.isDeobfuscated) {
                listOf(
                    "net.kyori:examination-api:1.3.0",
                    "net.kyori:examination-string:1.3.0",
                ).forEach { coord ->
                    dependencies.add("bundleContents", coord)
                }
            }

            // The messaging module's MessageCodec (and the config module) need
            // Jackson at runtime. Project deps above are non-transitive, so add
            // jackson-databind explicitly, mirroring the Adventure handling.
            val jacksonVersion = project.extensions
                .getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
                .named("libs")
                .findVersion("jackson")
                .get()
                .requiredVersion
            listOf(
                "com.fasterxml.jackson.core:jackson-databind",
                "com.fasterxml.jackson.core:jackson-core",
                "com.fasterxml.jackson.core:jackson-annotations",
            ).forEach { coord ->
                dependencies.add("bundleContents", "$coord:$jacksonVersion")
            }

            // Minecraft's own dependency graph pins Jackson with a `strictly 2.13.4`
            // constraint, and NeoForge puts that graph on this project's configurations.
            // Asking for the catalog's Jackson then becomes an unsatisfiable conflict and
            // resolution fails outright ("Cannot find a version of jackson-databind that
            // satisfies the version constraints") — which is why the NeoForge bundle could
            // not build or dev-run on 1.21.x at all. The force below picks the catalog
            // version for this project: it decides which copy is packed into the bundle
            // and which one the dev run loads, and touches nothing outside this project,
            // so the game's own classpath keeps its strict pin. It has to apply to every
            // configuration, not just bundleContents, because `runtimeOnly` extends
            // bundleContents for the dev run and would otherwise hit the same conflict.
            configurations.all { configuration ->
                configuration.resolutionStrategy.force(
                    "com.fasterxml.jackson.core:jackson-databind:$jacksonVersion",
                    "com.fasterxml.jackson.core:jackson-core:$jacksonVersion",
                    "com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion",
                )
            }


            val relocateLibs = tasks.register("relocateLibs", ShadowJar::class.java) {
                it.configurations.set(setOf(bundleContents))
                it.archiveClassifier.set("relocated-libs")

                it.relocate("org.yaml.snakeyaml", "dev.ua.theroer.magicutils.libs.snakeyaml")
            }

            tasks.named("jar", Jar::class.java).configure { jarTask ->
                jarTask.archiveBaseName.set(moduleName)
                jarTask.duplicatesStrategy = DuplicatesStrategy.EXCLUDE

                jarTask.dependsOn(bundleContents)
                jarTask.from(relocateLibs.map { project.zipTree(it.archiveFile) })

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
                publication.stripPomDependencies()
            }

            project.magicUtilsPublishRepository(publishing)
        }
    }
}
