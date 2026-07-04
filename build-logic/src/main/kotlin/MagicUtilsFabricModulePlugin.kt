import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask

class MagicUtilsFabricModulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // magicutils.target registers MagicUtilsTargetExtension; apply it before
        // reading the target so we can pick the right Loom plugin id.
        project.pluginManager.apply("magicutils.target")

        val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
        // 26.x ships a deobfuscated jar: use the new no-remap Loom plugin id,
        // drop mappings, and let `jar` be the artifact (no remapJar). Older,
        // obfuscated targets keep the classic remapping `fabric-loom` path.
        val isDeobfuscated = target.minecraft.get().substringBefore('.').toInt() >= 26

        project.pluginManager.apply("magicutils.java-library")
        project.pluginManager.apply(if (isDeobfuscated) "net.fabricmc.fabric-loom" else "fabric-loom")
        project.pluginManager.apply("magicutils.common")
        project.pluginManager.apply("magicutils.shadow")

        val shadowRuntimeClasspath =
            project.configurations.create("shadowRuntimeClasspath")

        shadowRuntimeClasspath.isCanBeResolved = true
        shadowRuntimeClasspath.isCanBeConsumed = false

        val magicutilsTarget = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
        val loom = project.extensions.getByType(LoomGradleExtensionAPI::class.java)
        val moduleName = project.magicUtilsModuleName()

        // On deobfuscated (26.x) targets the new Loom plugin does not remap, so
        // there is no remapJar task and no mappings — `jar` is the artifact and
        // dependencies use plain (implementation/compileOnly) configurations.
        val classifier = "mc${magicutilsTarget.minecraft.get().substringBeforeLast('.')}"
        val compileOnlyConfig = if (isDeobfuscated) "compileOnly" else "modCompileOnly"
        // Name of the task producing the primary (published, JiJ) artifact.
        val mainJarTaskName = if (isDeobfuscated) "jar" else "remapJar"

        with(project) {
            project.dependencies.add("minecraft", "com.mojang:minecraft:${magicutilsTarget.minecraft.get()}")
            if (!isDeobfuscated) {
                project.dependencies.add("mappings", loom.officialMojangMappings())
            }
            project.dependencies.add(compileOnlyConfig, "net.fabricmc:fabric-loader:${magicutilsTarget.loader.get()}")
            project.dependencies.add(compileOnlyConfig, "eu.pb4:placeholder-api:${magicutilsTarget.pb4_placeholder_api.get()}")
            project.dependencies.add(compileOnlyConfig, "io.github.miniplaceholders:miniplaceholders-api:${magicutilsTarget.miniplaceholders_api.get()}")

            if (isDeobfuscated) {
                tasks.named("jar", Jar::class.java).configure { jarTask ->
                    jarTask.archiveBaseName.set(moduleName)
                    jarTask.archiveClassifier.set(classifier)
                }
            } else {
                tasks.named("remapJar", RemapJarTask::class.java).configure { remapJarTask ->
                    remapJarTask.archiveBaseName.set(moduleName)
                    remapJarTask.archiveClassifier.set(classifier)
                    remapJarTask.dependsOn(tasks.named("jar", Jar::class.java))
                }
            }

            tasks.configureEach {
                if (it.javaClass.simpleName == "GenerateModuleMetadata") {
                    it.enabled = false
                }
            }

            val jiJRemapConfig = configurations.create("jiJRemap")
            jiJRemapConfig.isCanBeConsumed = true
            jiJRemapConfig.isCanBeResolved = false

            project.artifacts.add("jiJRemap", tasks.named(mainJarTaskName))
        }

        project.afterEvaluate {
            project.extensions.configure(org.gradle.api.publish.PublishingExtension::class.java) { publishing ->
                publishing.publications.create("mavenJava", MavenPublication::class.java) { publication ->
                    publication.artifactId = moduleName
                    publication.artifact(project.tasks.named(mainJarTaskName).get()) { artifact ->
                        artifact.builtBy(project.tasks.named(mainJarTaskName).get())
                    }
                    publication.artifact(project.tasks.named("sourcesJar", Jar::class.java).get())
                    publication.artifact(project.tasks.named("javadocJar", Jar::class.java).get())
                }

                project.magicUtilsPublishRepository(publishing)
            }
        }
    }
}
