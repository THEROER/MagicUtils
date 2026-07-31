package dev.ua.theroer.magicutils.build.consumer

import org.gradle.api.Project
import xyz.jpenilla.runpaper.RunPaperExtension
import xyz.jpenilla.runpaper.RunPaperPlugin
import xyz.jpenilla.runpaper.task.RunServer
import xyz.jpenilla.runvelocity.task.RunVelocity
import xyz.jpenilla.runwaterfall.task.RunWaterfall

/**
 * Dev-server wiring for the platforms driven by jpenilla's run-* plugins: Paper, Folia,
 * Velocity and Waterfall.
 *
 * Split out of [MagicUtilsDevServer] because these are the only runners whose *task
 * types* have to be imported. Keeping them here means the platform-neutral build-logic
 * artifact never carries the Paper/proxy toolchain, so a Fabric or NeoForge consumer
 * does not resolve it. The shared run-directory handling still comes from
 * [MagicUtilsDevServer.prepareRunDirectory].
 */
object MagicUtilsJvmDevServer {

    /**
     * Wires the Paper + Folia runners on a bukkit consumer. [pluginArtifact]
     * yields the plugin jar to load (the consumer's shadow jar); [extraPlugins]
     * are additional server-plugin jars to drop in (e.g. the shared MagicUtils
     * jar). All are resolved lazily so we never force task realisation here.
     */
    fun configureBukkit(
        project: Project,
        spec: MagicUtilsDevServerSpec,
        targetMinecraftVersion: String,
        mcClassifier: String,
        pluginArtifact: () -> Any,
        extraPlugins: List<() -> Any>,
    ) {
        project.pluginManager.apply(RunPaperPlugin::class.java)
        project.applyDevServerConventions(spec)
        spec.paperVersion.convention(targetMinecraftVersion)
        spec.foliaVersion.convention(spec.paperVersion)

        val runPaper = project.extensions.getByType(RunPaperExtension::class.java)
        // We pin plugin jars explicitly (below), so turn off run-paper's shadow
        // auto-detection, which can grab the wrong artifact in a multi-jar module.
        runPaper.disablePluginJarDetection()

        val registerFolia = spec.folia.get()
        if (registerFolia) {
            val foliaVersion = spec.foliaVersion.get()
            runPaper.folia { folia ->
                folia.registerTask { runTask ->
                    runTask.minecraftVersion(foliaVersion)
                    runTask.runDirectory.set(project.layout.projectDirectory.dir("run-folia"))
                }
            }
        }

        configureRunTask(project, spec, "runServer", "Paper", mcClassifier, pluginArtifact, extraPlugins)
        if (registerFolia) {
            configureRunTask(project, spec, "runFolia", "Folia", mcClassifier, pluginArtifact, extraPlugins)
        }
    }

    /**
     * Wires the Velocity runner (`runVelocity`). Proxies have no MOTD/Folia; we
     * pin the proxy version, the consumer's shadow jar, and any Modrinth plugins.
     * The Velocity API version is not target-derived, so it is passed in.
     */
    fun configureVelocity(
        project: Project,
        spec: MagicUtilsDevServerSpec,
        velocityVersion: String,
        mcClassifier: String,
        pluginArtifact: () -> Any,
    ) {
        project.applyDevServerConventions(spec)
        val modrinth = MagicUtilsModrinthResolver.resolve(
            project, spec.modrinthFor("velocity", mcClassifier), "velocity", velocityVersion,
        )
        project.tasks.withType(RunVelocity::class.java).configureEach { task ->
            task.velocityVersion(velocityVersion)
            task.pluginJars(project.provider { pluginArtifact() })
            task.downloadPlugins { d -> modrinth.forEach { d.modrinth(it.id, it.version) } }
        }
    }

    /** Wires the Waterfall/Bungee runner (`runWaterfall`). See [configureVelocity]. */
    fun configureWaterfall(
        project: Project,
        spec: MagicUtilsDevServerSpec,
        waterfallVersion: String,
        mcClassifier: String,
        pluginArtifact: () -> Any,
    ) {
        project.applyDevServerConventions(spec)
        val modrinth = MagicUtilsModrinthResolver.resolve(
            project, spec.modrinthFor("waterfall", mcClassifier), "waterfall", waterfallVersion,
        )
        project.tasks.withType(RunWaterfall::class.java).configureEach { task ->
            task.waterfallVersion(waterfallVersion)
            task.pluginJars(project.provider { pluginArtifact() })
            task.downloadPlugins { d -> modrinth.forEach { d.modrinth(it.id, it.version) } }
        }
    }

    private fun configureRunTask(
        project: Project,
        spec: MagicUtilsDevServerSpec,
        taskName: String,
        platform: String,
        mcClassifier: String,
        pluginArtifact: () -> Any,
        extraPlugins: List<() -> Any>,
    ) {
        val isFolia = platform == "Folia"
        val runVersion = if (isFolia) spec.foliaVersion.get() else spec.paperVersion.get()
        val motd = spec.resolvedMotd(platform)
        val port = spec.port.get()
        val onlineMode = spec.onlineMode.get()
        val pluginName = spec.pluginName.get()
        val runDirName = if (isFolia) "run-folia" else "run-paper-$runVersion"

        val loader = if (isFolia) "folia" else "paper"
        val modrinth = MagicUtilsModrinthResolver.resolve(
            project, spec.modrinthFor(loader, mcClassifier), loader, runVersion,
        )

        project.tasks.named(taskName, RunServer::class.java).configure { task ->
            task.minecraftVersion(runVersion)
            task.pluginJars(project.provider { pluginArtifact() })
            extraPlugins.forEach { extra -> task.pluginJars(project.provider { extra() }) }
            task.downloadPlugins { downloads ->
                modrinth.forEach { downloads.modrinth(it.id, it.version) }
            }
            if (!isFolia) {
                task.runDirectory.set(project.layout.projectDirectory.dir(runDirName))
            }
            task.doFirst {
                MagicUtilsDevServer.prepareRunDirectory(project, runDirName, motd, port, onlineMode, pluginName)
            }
        }
    }
}
