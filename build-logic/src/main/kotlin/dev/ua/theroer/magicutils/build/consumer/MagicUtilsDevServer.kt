package dev.ua.theroer.magicutils.build.consumer

import dev.ua.theroer.magicutils.build.target.*

import java.net.URI
import org.gradle.api.Project
import xyz.jpenilla.runpaper.RunPaperExtension
import xyz.jpenilla.runpaper.RunPaperPlugin
import xyz.jpenilla.runpaper.task.RunServer
import xyz.jpenilla.runvelocity.task.RunVelocity
import xyz.jpenilla.runwaterfall.task.RunWaterfall

/**
 * Resolves [MagicUtilsModrinthDependency.AUTO] version markers to a concrete
 * Modrinth version id by querying the Modrinth API for the newest version of the
 * project that matches the active Minecraft version and loader. Fixed version
 * ids pass through untouched. A failed lookup drops the dependency with a warning
 * rather than failing the build, so an offline `runServer` still starts.
 */
internal object MagicUtilsModrinthResolver {
    fun resolve(
        project: Project,
        deps: List<ResolvedModrinth>,
        loader: String,
        minecraftVersion: String,
    ): List<ResolvedModrinth> = deps.mapNotNull { dep ->
        if (dep.version != MagicUtilsModrinthDependency.AUTO) return@mapNotNull dep
        val resolved = queryLatest(dep.id, loader, minecraftVersion)
        if (resolved == null) {
            project.logger.warn(
                "MagicUtils devServer: could not auto-resolve Modrinth '${dep.id}' for " +
                    "$loader $minecraftVersion; skipping it.",
            )
            null
        } else {
            ResolvedModrinth(dep.id, resolved)
        }
    }

    private fun queryLatest(id: String, loader: String, minecraftVersion: String): String? = runCatching {
        val url = "https://api.modrinth.com/v2/project/$id/version" +
            "?loaders=%5B%22$loader%22%5D&game_versions=%5B%22$minecraftVersion%22%5D"
        val body = URI.create(url).toURL().openStream().use { it.readBytes().decodeToString() }
        // Newest first; take the first object's "id" field without a JSON lib.
        Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
    }.getOrNull()
}

/**
 * Registration of the local dev-server run tasks, shared by the consumer-*
 * platform plugins. Invoked only when the consumer opted into `devServer {}`.
 *
 * build-logic depends on the jpenilla run-* plugins directly (see build.gradle
 * .kts), so this configures their tasks with typed access — the plugins are
 * applied unconditionally here anyway, so there is nothing to gain from the
 * no-import reflection used for the optional Loom plugin, and typed access is
 * far less brittle.
 */
internal object MagicUtilsDevServer {

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
     * Wires the Fabric dev server. Loom already registers `runServer` and points
     * it at the `run/` directory, so we only stamp server.properties (MOTD/port/
     * online-mode) into that directory — the Fabric server reads it the same way
     * Paper does. No plugin-jar pinning: Loom loads the mod from the dev source
     * set. The `runClient` task is left untouched.
     */
    /**
     * Stamps the Fabric dev server's server.properties (MOTD/port/online-mode).
     * Loom already registers `runServer` and points it at `run/`; mod loading is
     * wired separately by [fabricModLocalRuntime], which must run in the main
     * configuration phase (not afterEvaluate) to survive Loom's early observe of
     * its mod configurations.
     */
    fun configureFabric(
        project: Project,
        spec: MagicUtilsDevServerSpec,
        targetMinecraftVersion: String,
    ) {
        project.applyDevServerConventions(spec)
        spec.paperVersion.convention(targetMinecraftVersion)
        spec.foliaVersion.convention(spec.paperVersion)

        val runServer = project.tasks.findByName("runServer") ?: return
        val motd = spec.resolvedMotd("Fabric")
        val port = spec.port.get()
        val onlineMode = spec.onlineMode.get()
        val pluginName = spec.pluginName.get()
        runServer.doFirst {
            prepareRunDirectory(project, "run", motd, port, onlineMode, pluginName)
        }
    }

    /**
     * Registers the consumer's Modrinth Fabric mods on Loom's `modLocalRuntime`
     * (dev runtime only, never published) via a lazy `addAllLater` provider — the
     * same pattern the bundled-library wiring uses to survive Loom eagerly
     * observing the mod configurations. The provider resolves the per-target
     * version (and any [MagicUtilsModrinthDependency.AUTO] lookups) when the
     * configuration is realised. Must be called from the main configuration phase.
     */
    fun fabricModLocalRuntime(
        project: Project,
        consumer: MagicUtilsConsumerExtension,
        targetMinecraftVersion: String,
        mcClassifier: String,
    ) {
        val deps = consumer.devServerSpec.map { spec ->
            val resolved = MagicUtilsModrinthResolver.resolve(
                project, spec.modrinthFor("fabric", mcClassifier), "fabric", targetMinecraftVersion,
            )
            resolved.map { project.dependencies.create("maven.modrinth:${it.id}:${it.version}") }
        }.orElse(emptyList())

        // The Modrinth Maven is only needed when there is at least one such mod;
        // registering it unconditionally is harmless and keeps this lazy.
        project.repositories.maven { repo ->
            repo.name = "modrinth"
            repo.setUrl("https://api.modrinth.com/maven")
            repo.content { it.includeGroup("maven.modrinth") }
        }
        // Loom registers `modLocalRuntime` lazily — it may not exist yet at apply
        // time — so hook it via matching/configureEach, which also fires for a
        // configuration created later, rather than `named` which throws if absent.
        project.configurations.matching { it.name == "modLocalRuntime" }.configureEach {
            it.dependencies.addAllLater(deps)
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

    /**
     * Wires the NeoForge dev server. ModDevGradle has no `pluginJars`/
     * `downloadPlugins`: a mod is loaded by placing its jar in the run
     * directory's `mods/` folder. The consumer declares the run itself
     * (`neoForge { runs { server() } }`, with its own game directory and args);
     * here we only feed that run's `mods/` folder by syncing the consumer's own
     * jar and any Modrinth mods into `run/mods`, wiring the sync ahead of the
     * `runServer` task. Modrinth mods are pulled from the Modrinth Maven via a
     * dedicated resolvable configuration.
     */
    fun configureNeoForge(
        project: Project,
        spec: MagicUtilsDevServerSpec,
        targetMinecraftVersion: String,
        mcClassifier: String,
    ) {
        project.applyDevServerConventions(spec)

        val modsDir = project.layout.projectDirectory.dir("run/mods")

        // The consumer's own built mod jar.
        val jarTask = project.tasks.named("jar", org.gradle.api.tasks.bundling.AbstractArchiveTask::class.java)

        // Modrinth mods resolved for the neoforge platform, pulled from the
        // Modrinth Maven into a dedicated resolvable configuration.
        val modrinth = MagicUtilsModrinthResolver.resolve(
            project, spec.modrinthFor("neoforge", mcClassifier), "neoforge", targetMinecraftVersion,
        )
        val modrinthMods = project.configurations.create("magicutilsNeoForgeDevMods") {
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
            it.isTransitive = false
        }
        if (modrinth.isNotEmpty()) {
            project.repositories.maven { repo ->
                repo.name = "modrinth"
                repo.setUrl("https://api.modrinth.com/maven")
                repo.content { it.includeGroup("maven.modrinth") }
            }
            modrinth.forEach { dep ->
                project.dependencies.add(modrinthMods.name, "maven.modrinth:${dep.id}:${dep.version}")
            }
        }

        val syncDevMods = project.tasks.register("syncNeoForgeDevMods", org.gradle.api.tasks.Copy::class.java) { task ->
            task.group = "run"
            task.description = "Copy this mod and its Modrinth dev mods into the NeoForge run/mods folder."
            task.dependsOn(jarTask)
            task.from(jarTask.flatMap { it.archiveFile })
            task.from(modrinthMods)
            task.into(modsDir)
        }

        // Make the moddev-registered runServer depend on the mod sync so the run
        // directory is populated before launch. Match by name so we do not import
        // moddev's task types.
        project.tasks.matching { it.name == "runServer" || it.name == "runServerServer" }.configureEach {
            it.dependsOn(syncDevMods)
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
                prepareRunDirectory(project, runDirName, motd, port, onlineMode, pluginName)
            }
        }
    }

    /**
     * Writes eula.txt (accepted) and stamps the resolved MOTD/port/online-mode
     * into server.properties on every run (updating those three keys in place and
     * preserving any others the server wrote), then clears stale plugin jars from
     * prior runs so an old build of this plugin (or MagicUtils) never shadows the
     * freshly built one on the dev server.
     */
    private fun prepareRunDirectory(
        project: Project,
        runDirName: String,
        motd: String,
        port: Int,
        onlineMode: Boolean,
        pluginName: String,
    ) {
        val runDir = project.layout.projectDirectory.dir(runDirName).asFile
        runDir.mkdirs()

        runDir.resolve("eula.txt").writeText("#Accepted via MagicUtils devServer\neula=true\n")

        writeServerProperties(runDir.resolve("server.properties"), motd, port, onlineMode)

        // Drop stale artifacts of this plugin and of MagicUtils so the dev server
        // loads only the freshly built jars pinned via pluginJars.
        val pluginsDir = runDir.resolve("plugins")
        if (pluginsDir.isDirectory) {
            val stalePrefixes = listOf(
                pluginName.lowercase().replace(' ', '-'),
                "magicutils",
            )
            pluginsDir.listFiles()?.forEach { file ->
                val lower = file.name.lowercase()
                if (file.extension == "jar" && stalePrefixes.any { lower.startsWith(it) }) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Sets `motd`, `online-mode` and `server-port` in [file], overwriting those
     * keys if present and appending them otherwise, while leaving every other
     * line the server wrote untouched. Creating the file from scratch is just the
     * empty-existing case.
     */
    private fun writeServerProperties(file: java.io.File, motd: String, port: Int, onlineMode: Boolean) {
        val managed = linkedMapOf(
            "motd" to motd,
            "online-mode" to onlineMode.toString(),
            "server-port" to port.toString(),
        )
        val existing = if (file.exists()) file.readLines() else emptyList()
        val seen = mutableSetOf<String>()
        val rewritten = existing.map { line ->
            val key = line.substringBefore('=', "").trim()
            val replacement = managed[key]
            if (replacement != null && !line.trimStart().startsWith("#")) {
                seen += key
                "$key=$replacement"
            } else {
                line
            }
        }
        val appended = managed.entries.filter { it.key !in seen }.map { "${it.key}=${it.value}" }
        file.writeText((rewritten + appended).joinToString("\n") + "\n")
    }
}
