package dev.ua.theroer.magicutils.build.module

import dev.ua.theroer.magicutils.build.support.*
import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import net.fabricmc.loom.task.RemapJarTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

class MagicUtilsFabricBundlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("magicutils.target")

        val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
        // Obfuscation boundary / Loom flavour / classifier from shared conventions.
        val isDeobfuscated = target.isDeobfuscated

        project.pluginManager.apply("magicutils.java-library")
        project.pluginManager.apply(target.loomPluginId)
        project.pluginManager.apply("magicutils.common")

        val magicutilsTarget = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
        val moduleName = project.magicUtilsModuleName()

        with(project) {

            // (fabric.mod.json's `@MC_JAVA@` token is substituted centrally in the
            // shared java-library plugin, which this bundle applies.)

            val bundleShadowConfig = configurations.create("bundleShadow")
            bundleShadowConfig.isCanBeConsumed = false
            bundleShadowConfig.isCanBeResolved = true
            bundleShadowConfig.attributes.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))

            val bundleLibProjects = listOf(
                project(":platform-api"),
                project(":commands-brigadier"),
                project(":core"),
                project(":diagnostics"),
                // Platform-neutral messaging runtime (MessageBus + plugin-messaging
                // transport). Must be listed explicitly: on <26 it used to arrive
                // only transitively via commands-fabric's `api(":messaging")` through
                // Loom's namedElements fat jar, but the 26.x bundle is built from
                // `bundleShadow` alone (the jiJRemap path drops transitive api project
                // deps), so the messaging package silently vanished from the 26.x
                // (java25) bundle. Adding it here inlines it on every branch, the same
                // way the neoforge/jvm bundles list `:messaging` explicitly.
                project(":messaging"),
                // Jackson (its only external dep) is already bundled via config-yaml/toml.
                project(":http-client")
            )

            val bundleModProjects = listOf(
                project(":logger"),
                project(":commands"),
                project(":placeholders")
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

            // Minecraft + Mojang mappings (obfuscated only); the runnable bundle
            // takes the loader on the implementation configuration.
            applyMinecraftAndMappings(project, magicutilsTarget)
            project.dependencies.add(
                magicutilsTarget.implementationConfiguration,
                "net.fabricmc:fabric-loader:${magicutilsTarget.loader.get()}",
            )

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
            bundleModProjects.forEach { dep ->
                project.dependencies.add("include", project(dep.path))
            }
            bundleRemappedProjects.forEach { dep ->
                val depProject = project(dep.path)
                val depDependency = project.dependencies.add("include", depProject) as org.gradle.api.artifacts.ProjectDependency
                depDependency.targetConfiguration = "jiJRemap"
            }

            bundleLibProjects.forEach { dep ->
                project.dependencies.add("bundleShadow", project(dep.path))
            }
            if (isDeobfuscated) {
                // 26.x publishes the shadow jar directly (no remapJar) and runs it
                // from the dev launcher, which does not explode JiJ'd libraries.
                // Shade adventure + diagnostics into the classifier jar so it is
                // self-contained for both publish and dev runtime (on <26 these
                // reach the fat `:dev` jar transitively via `namedElements`).
                listOf(
                    "net.kyori:adventure-api:4.24.0",
                    "net.kyori:adventure-key:4.24.0",
                    "net.kyori:adventure-text-minimessage:4.24.0",
                    "net.kyori:adventure-text-serializer-plain:4.24.0",
                    "net.kyori:adventure-text-serializer-gson:4.24.0",
                    "net.kyori:adventure-text-serializer-ansi:4.24.0",
                    "net.kyori:adventure-text-serializer-json:4.24.0",
                    "net.kyori:ansi:1.1.1",
                    "net.kyori:examination-api:1.3.0",
                    "net.kyori:examination-string:1.3.0",
                    "net.kyori:option:1.1.0",
                ).forEach { project.dependencies.add("bundleShadow", it) }
            }
            bundleShadedProjects.forEach { dep ->
                val depProject = project(dep.path)
                val depDependency = project.dependencies.add("bundleShadow", depProject) as org.gradle.api.artifacts.ProjectDependency
                depDependency.targetConfiguration = "shadedElements"
            }
            // On 26.x there is no Loom `namedElements` (no remap); the deobfuscated
            // `jiJRemap` (plain jar) output is what feeds the bundle instead.
            val bundledFabricConfig = if (isDeobfuscated) "jiJRemap" else "namedElements"
            bundleNamedProjects.forEach { dep ->
                val depProject = project(dep.path)
                val depDependency = project.dependencies.add("bundleShadow", depProject) as org.gradle.api.artifacts.ProjectDependency
                depDependency.targetConfiguration = bundledFabricConfig
            }

            val shadowJar = tasks.named("shadowJar", ShadowJar::class.java)

            // Branch is in the version, so the shipped bundle jar is classifier-less.
            if (isDeobfuscated) {
                // No remap on 26.x: the shadow jar is the shipped artifact.
                shadowJar.configure { shadowJarTask ->
                    shadowJarTask.archiveBaseName.set(moduleName)
                    shadowJarTask.archiveClassifier.set("")
                    shadowJarTask.configurations.set(
                        setOf(project.configurations.getByName("bundleShadow"))
                    )
                    shadowJarTask.from(project.extensions.getByType(SourceSetContainer::class.java).getByName("main").output)
                    shadowJarTask.mergeServiceFiles()
                }
            } else {
                // remapJar is shipped (classifier-less); the fat `dev` shadow jar is
                // the named compile/dev-runtime variant.
                tasks.named("remapJar", RemapJarTask::class.java).configure { remapJarTask ->
                    remapJarTask.archiveClassifier.set("")
                    remapJarTask.archiveBaseName.set(moduleName)
                    remapJarTask.inputFile.set(shadowJar.get().archiveFile)
                }

                shadowJar.configure { shadowJarTask ->
                    shadowJarTask.archiveClassifier.set("dev")
                    shadowJarTask.configurations.set(
                        setOf(project.configurations.getByName("bundleShadow"))
                    )
                    shadowJarTask.from(project.extensions.getByType(SourceSetContainer::class.java).getByName("main").output)
                    shadowJarTask.mergeServiceFiles()
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
                if (isDeobfuscated) {
                    // The classifier-carrying shadow jar is the primary artifact.
                    publication.artifact(project.tasks.named("shadowJar", ShadowJar::class.java).get()) { artifact ->
                        artifact.builtBy(project.tasks.named("shadowJar").get())
                    }
                    publication.artifact(project.tasks.named("sourcesJar", Jar::class.java).get())
                    publication.artifact(project.tasks.named("javadocJar", Jar::class.java).get())
                } else {
                    publication.artifact(project.tasks.named("remapJar", RemapJarTask::class.java).get()) { artifact ->
                        artifact.builtBy(project.tasks.named("remapJar").get())
                    }
                    publication.artifact(project.tasks.named("sourcesJar", Jar::class.java).get())
                    publication.artifact(project.tasks.named("javadocJar", Jar::class.java).get())
                    publication.artifact(project.tasks.named("shadowJar", ShadowJar::class.java).get()) { artifact ->
                        artifact.classifier = "dev"
                    }
                }
                publication.stripPomDependencies()
            }

            project.magicUtilsPublishRepository(publishing)
        }
    }
}
