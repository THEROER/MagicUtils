package dev.ua.theroer.magicutils.build.consumer

import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.*

/**
 * Consumer plugin for the Bukkit/Paper module of a downstream plugin.
 *
 * Bukkit's API is stable across the whole range, so there is no obf/deobf
 * split here — one artifact covers 1.21.x .. 26.2. The plugin applies
 * `java-library` + shadow, resolves the active target, pins the toolchain to
 * the target's Java, and adds the Paper API (compileOnly, version from the
 * target). MagicUtils modules are declared with [magicUtils]; the classifier is
 * derived from the target.
 */
class MagicUtilsConsumerBukkitPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("com.gradleup.shadow")
        project.pluginManager.apply("magicutils.target")

        val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)
        val consumer = project.magicUtilsConsumerExtension()

        project.extensions.configure(JavaPluginExtension::class.java) { java ->
            java.toolchain.languageVersion.set(JavaLanguageVersion.of(target.java.get()))
            java.withSourcesJar()
        }

        project.tasks.withType(JavaCompile::class.java).configureEach { task ->
            task.options.release.set(target.java.get())
            task.options.compilerArgs.add("-parameters")
        }

        // Paper API for the active target (compileOnly — provided by the server).
        project.dependencies.add("compileOnly", "io.papermc.paper:paper-api:${target.paper.get()}")

        project.exposeMagicUtilsTargetFacts(target)

        // MagicUtils modules the consumer declared. Bukkit has no jar-in-jar, so
        // embedMode maps onto the dependency configuration + the shadow jar:
        //   SHADED   → api/implementation, so shadow relocates the modules into the
        //             fat jar (a self-contained plugin). This is the Bukkit default
        //             (AUTO resolves here), but see the SHADED note in EmbedMode:
        //             two shaded consumers on one server load two isolated copies —
        //             use EXTERNAL there.
        //   EXTERNAL → compileOnly, and dev/ua/theroer/magicutils is stripped from
        //             the shadow jar, so the built jar is thin and expects
        //             MagicUtils installed as a separate server plugin.
        //   JAR_IN_JAR → rejected by resolveEmbedMode (fail-fast; no JiJ on Bukkit).
        // Resolved at afterEvaluate: the consumer sets embedMode in its DSL block,
        // which runs after this plugin applies. Bukkit has no Loom early-observe of
        // configurations, so a late `add` is safe (unlike the Fabric consumer).
        project.afterEvaluate {
            val embed = resolveEmbedMode(consumer.embedMode.get(), ConsumerLoader.BUKKIT)
            val shaded = embed == EmbedMode.SHADED
            val apiConfig = if (shaded) "api" else "compileOnly"
            val implConfig = if (shaded) "implementation" else "compileOnly"
            val base = consumer.magicutilsVersion.get()
            consumer.apiModules.get().forEach { module ->
                project.dependencies.add(apiConfig, magicUtilsModuleCoordinate(module, base, target))
            }
            consumer.implementationModules.get().forEach { module ->
                project.dependencies.add(implConfig, magicUtilsModuleCoordinate(module, base, target))
            }

            // EXTERNAL: strip MagicUtils and its bundled libraries from the shadow
            // jar, so this jar carries none of them and the standalone MagicUtils
            // plugin provides the single copy at runtime. These reach the fat jar
            // transitively via the common module (whose MagicUtils deps are `api`),
            // so moving this module's deps to compileOnly alone does not remove
            // them; the shadow exclude does. jackson is the config modules' only
            // external dependency and the bundle plugin owns its own relocated
            // copy — shipping a second here produced a ClassCastException under
            // Paper's isolated classloaders, so it is excluded too.
            if (!shaded) {
                project.tasks.named("shadowJar", com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class.java)
                    .configure { shadow ->
                        // The `**/` prefix also catches multi-release copies under
                        // META-INF/versions/<n>/, which a root-anchored pattern
                        // (e.g. `com/fasterxml/...`) would leave behind.
                        shadow.exclude("**/dev/ua/theroer/magicutils/**")
                        shadow.exclude("**/com/fasterxml/jackson/**")
                    }
            }
        }

        // Local dev server (runPaper/runFolia), only when the consumer opted in
        // with `magicutilsConsumer { devServer { ... } }`. The Folia runner is
        // registered because a MagicUtils-based plugin is Folia-compatible by
        // construction — running it there verifies that locally. The plugin jar
        // loaded is the consumer's shadow jar; consumers needing the standalone
        // MagicUtils bukkit bundle as a separate server plugin add it via
        // `devServerExtraPlugins`.
        project.afterEvaluate {
            val consumer = project.magicUtilsConsumerExtension()
            val spec = consumer.devServerSpec.orNull ?: return@afterEvaluate
            MagicUtilsDevServer.configureBukkit(
                project = project,
                spec = spec,
                targetMinecraftVersion = target.minecraft.get(),
                mcClassifier = target.mcClassifier,
                pluginArtifact = {
                    project.tasks.named("shadowJar", org.gradle.api.tasks.bundling.AbstractArchiveTask::class.java)
                        .flatMap { it.archiveFile }
                },
                extraPlugins = emptyList(),
            )
        }
    }
}
