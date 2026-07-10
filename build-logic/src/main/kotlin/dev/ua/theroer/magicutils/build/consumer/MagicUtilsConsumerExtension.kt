package dev.ua.theroer.magicutils.build.consumer

import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * How a downstream consumer ships MagicUtils. The set of *techniques* is the
 * same vocabulary on every loader, but not every technique is valid on every
 * loader (there is no jar-in-jar on Bukkit, and shading the mod bundle breaks
 * Fabric/NeoForge). [AUTO] picks the native technique for the loader; a
 * consumer only names an explicit mode to deviate. See
 * [MagicUtilsConsumerExtension.embedMode] and [resolveEmbedMode].
 */
enum class EmbedMode {
    /**
     * Use the loader's native embedding: [JAR_IN_JAR] on Fabric/NeoForge,
     * [SHADED] on Bukkit. The default, so most consumers never set this.
     */
    AUTO,

    /**
     * Ship MagicUtils as a nested mod/artifact inside the consumer jar — Loom
     * `include` on Fabric, ModDevGradle `jarJar` on NeoForge. Invalid on Bukkit
     * (no jar-in-jar there).
     */
    JAR_IN_JAR,

    /**
     * Relocate the MagicUtils classes into the consumer's fat jar (shadow).
     * Valid on Bukkit. Not yet supported on Fabric/NeoForge, where the bundle
     * carries mixins/entrypoint metadata that shading into the consumer mod
     * would break.
     */
    SHADED,

    /**
     * Do not package MagicUtils; it is provided beside the consumer at runtime
     * (a separately installed server plugin / nested mod). Valid on every loader.
     */
    EXTERNAL,
}

/** The loader a consumer plugin targets, for [resolveEmbedMode] validation. */
internal enum class ConsumerLoader(val label: String, val native: EmbedMode, val allowed: Set<EmbedMode>) {
    FABRIC("Fabric", EmbedMode.JAR_IN_JAR, setOf(EmbedMode.JAR_IN_JAR, EmbedMode.EXTERNAL)),
    NEOFORGE("NeoForge", EmbedMode.JAR_IN_JAR, setOf(EmbedMode.JAR_IN_JAR, EmbedMode.EXTERNAL)),
    // Velocity is a plain JVM plugin like Bukkit (flat proxy classpath, no
    // jar-in-jar), so it shades by default and can also run EXTERNAL beside the
    // standalone velocity-bundle.
    BUKKIT("Bukkit", EmbedMode.SHADED, setOf(EmbedMode.SHADED, EmbedMode.EXTERNAL)),
    VELOCITY("Velocity", EmbedMode.SHADED, setOf(EmbedMode.SHADED, EmbedMode.EXTERNAL)),
}

/**
 * Resolves the consumer's requested [EmbedMode] to a concrete technique for the
 * given [loader]: [EmbedMode.AUTO] becomes the loader's native mode, and an
 * explicit mode is validated against what the loader supports. An unsupported
 * combination (e.g. [EmbedMode.JAR_IN_JAR] on Bukkit) fails the build with a
 * message that names the alternatives, rather than silently doing something else.
 */
internal fun resolveEmbedMode(requested: EmbedMode, loader: ConsumerLoader): EmbedMode {
    val resolved = if (requested == EmbedMode.AUTO) loader.native else requested
    if (resolved !in loader.allowed) {
        val alternatives = loader.allowed.joinToString(" or ") { it.name }
        throw org.gradle.api.GradleException(
            "MagicUtils embedMode ${requested.name} is not supported on ${loader.label}. " +
                "Use $alternatives (or AUTO for the ${loader.label} default, ${loader.native.name})."
        )
    }
    return resolved
}

/**
 * Consumer-facing configuration for the `magicutils.consumer-*` plugins.
 *
 * A downstream plugin/mod only needs to state which MagicUtils library version
 * to depend on (default read from the `magicutils_version` gradle property, or
 * the current library version) and which MagicUtils modules it uses. Everything
 * else — Loom flavour, mappings, mc<major.minor> classifier, Java toolchain — is
 * derived from the active target and applied by the plugin, so consumer build
 * scripts stay declarative and never spell out a classifier.
 */
abstract class MagicUtilsConsumerExtension {
    /** MagicUtils library version consumed for the active target. */
    abstract val magicutilsVersion: Property<String>

    /**
     * How the consumer ships MagicUtils. See [EmbedMode] for the techniques and
     * [resolveEmbedMode] for which are valid on each loader.
     *
     * Defaults to [EmbedMode.AUTO], which each consumer plugin resolves to its
     * loader's native technique ([EmbedMode.JAR_IN_JAR] on Fabric/NeoForge,
     * [EmbedMode.SHADED] on Bukkit), so most consumers never set this. Name an
     * explicit mode only to deviate — e.g. [EmbedMode.EXTERNAL] when several
     * MagicUtils consumers share one server and MagicUtils is installed
     * standalone. An explicit mode a loader does not support fails the build.
     *
     * The `-Pmagicutils_embed` gradle property is a one-flag CLI override: `true`
     * ⇒ [EmbedMode.AUTO], `false` ⇒ [EmbedMode.EXTERNAL].
     */
    abstract val embedMode: Property<EmbedMode>

    /**
     * MagicUtils modules to add on the `api` configuration (e.g.
     * `magicutils-config`, `magicutils-commands`). The plugin resolves each to
     * the active target's classifier. Fabric consumers list *extra* modules
     * here; the fabric bundle itself is wired automatically.
     */
    abstract val apiModules: ListProperty<String>

    /** MagicUtils modules to add on the `implementation` configuration. */
    abstract val implementationModules: ListProperty<String>

    /**
     * Third-party coordinates to jar-in-jar (`include`) into the mod AND put on
     * the (mod-aware) compile+runtime classpath — e.g. `eu.pb4:sgui:...`,
     * `com.zaxxer:HikariCP:...`. Fabric only. The plugin picks the right
     * mod/plain configuration for the target so consumers never do.
     */
    abstract val bundledLibraryCoordinates: ListProperty<String>

    /** Declares MagicUtils [modules] on the `api` configuration. */
    fun api(vararg modules: String) {
        apiModules.addAll(modules.toList())
    }

    /** Declares MagicUtils [modules] on the `implementation` configuration. */
    fun implementation(vararg modules: String) {
        implementationModules.addAll(modules.toList())
    }

    /** Declares third-party [coordinates] to bundle (JiJ) into the Fabric mod. */
    fun bundledLibraries(vararg coordinates: String) {
        bundledLibraryCoordinates.addAll(coordinates.toList())
    }

    /**
     * Nested spec for the local dev server. Non-null only after [devServer] has
     * been called at least once — its presence is the opt-in signal that makes
     * the consumer plugin register run tasks (and apply the jpenilla run-* Gradle
     * plugin). Consumers that never call [devServer] get no run tasks and no
     * extra plugins, so nothing is imposed.
     */
    abstract val devServerSpec: Property<MagicUtilsDevServerSpec>

    /**
     * Opt-in: enable and configure the local dev server run tasks
     * (`runPaper`/`runFolia` on bukkit, `runFabric` on fabric, plus the proxy
     * runners). Everything is defaulted from the active target and the plugin
     * name, so the empty form `devServer {}` already works.
     */
    fun devServer(action: Action<in MagicUtilsDevServerSpec>) {
        val spec = devServerSpec.orNull ?: objects.newInstance(MagicUtilsDevServerSpec::class.java)
        action.execute(spec)
        devServerSpec.set(spec)
    }

    /** No-arg form: enable the dev server with all defaults. */
    fun devServer() = devServer(Action { })

    /** Injected by Gradle so [devServer] can instantiate the managed nested spec. */
    @get:javax.inject.Inject
    protected abstract val objects: org.gradle.api.model.ObjectFactory
}

/**
 * Configuration of the local dev server, shared by every platform runner. All
 * values are optional; the consumer plugin fills unset ones from the active
 * target (versions) and the plugin name (motd). `{pluginName}` in [motd] is
 * substituted with the resolved plugin display name at task-configuration time.
 */
abstract class MagicUtilsDevServerSpec {
    /** Server port written to `server.properties`. Default `25565`. */
    abstract val port: Property<Int>

    /**
     * MOTD written to `server.properties`. The tokens `{pluginName}` and
     * `{platform}` are substituted (the latter with the runner's platform, e.g.
     * `Paper`, `Folia`, `Fabric`). Default `"{pluginName} {platform} dev"`.
     */
    abstract val motd: Property<String>

    /** `online-mode` written to `server.properties`. Default `false`. */
    abstract val onlineMode: Property<Boolean>

    /**
     * Human-readable plugin name used for the MOTD default and for cleaning up
     * stale dev artifacts. Default: the `pluginDisplayName` gradle property, or
     * the root project name if that property is absent.
     */
    abstract val pluginName: Property<String>

    /**
     * Minecraft version the Paper/Folia dev server boots. Default: the active
     * target's Minecraft version.
     */
    abstract val paperVersion: Property<String>

    /** Minecraft version the Folia dev server boots. Default: [paperVersion]. */
    abstract val foliaVersion: Property<String>

    /**
     * Whether to register the `runFolia` task on bukkit consumers. Default
     * `true` — a MagicUtils-based plugin is Folia-compatible by construction, so
     * the Folia runner is the way to actually verify that locally.
     */
    abstract val folia: Property<Boolean>

    /**
     * Modrinth dependencies to load into the dev server (LuckPerms, PAPI, ...).
     * Each is declared once with a logical [modrinth] id and per-platform
     * versions; the consumer plugin picks the version for the active platform and
     * loads it the platform-native way (run-task `downloadPlugins` on Paper/proxy,
     * Loom `modLocalRuntime` on Fabric). A dependency with no version for the
     * active platform is skipped, so one declaration serves every module.
     */
    abstract val modrinthDependencies: ListProperty<MagicUtilsModrinthDependency>

    /**
     * Declares a Modrinth dependency by its project [id] (slug), configuring the
     * per-platform version in [action], e.g.
     * `modrinth("luckperms") { paper = "v5.5.17-bukkit"; fabric = "v5.5.17-fabric" }`.
     */
    fun modrinth(id: String, action: Action<in MagicUtilsModrinthDependency>) {
        val dep = objects.newInstance(MagicUtilsModrinthDependency::class.java)
        dep.id.set(id)
        action.execute(dep)
        modrinthDependencies.add(dep)
    }

    @get:javax.inject.Inject
    protected abstract val objects: org.gradle.api.model.ObjectFactory
}

/**
 * A single Modrinth dependency for the dev server. Per platform the version can
 * be given three ways, all through the same per-platform map keyed by target:
 *  - a fixed id for every target (key [ANY_TARGET]);
 *  - a per-target id (key = the target's `mcClassifier`, e.g. `mc1.21`, `mc26`),
 *    for deps whose Modrinth build differs between Minecraft versions;
 *  - [AUTO], meaning the consumer plugin queries the Modrinth API at
 *    configuration time for the latest version matching the active MC + loader.
 *
 * A platform with no entry for the active target (and no [ANY_TARGET] fallback)
 * is simply not loaded there, so one declaration serves every module/target.
 */
abstract class MagicUtilsModrinthDependency {
    /** Modrinth project id / slug (e.g. `luckperms`). */
    abstract val id: Property<String>

    /** Per-target Paper/Folia versions. Key: `mcClassifier` or [ANY_TARGET]. */
    abstract val paperVersions: MapProperty<String, String>

    /** Per-target Fabric versions. */
    abstract val fabricVersions: MapProperty<String, String>

    /** Per-target Velocity versions. */
    abstract val velocityVersions: MapProperty<String, String>

    /** Per-target Waterfall/Bungee versions. */
    abstract val waterfallVersions: MapProperty<String, String>

    /** Per-target NeoForge versions. */
    abstract val neoforgeVersions: MapProperty<String, String>

    /** Paper/Folia: one version id for every target. */
    fun paper(version: String) = paperVersions.put(ANY_TARGET, version)

    /** Paper/Folia: pick the latest matching version via the Modrinth API. */
    fun paperAuto() = paperVersions.put(ANY_TARGET, AUTO)

    /** Paper/Folia: distinct version ids per target `mcClassifier`. */
    fun paper(vararg byTarget: Pair<String, String>) = paperVersions.putAll(byTarget.toMap())

    /** Fabric: one version id for every target. */
    fun fabric(version: String) = fabricVersions.put(ANY_TARGET, version)

    /** Fabric: pick the latest matching version via the Modrinth API. */
    fun fabricAuto() = fabricVersions.put(ANY_TARGET, AUTO)

    /** Fabric: distinct version ids per target `mcClassifier`. */
    fun fabric(vararg byTarget: Pair<String, String>) = fabricVersions.putAll(byTarget.toMap())

    /** Velocity: one version id for every target. */
    fun velocity(version: String) = velocityVersions.put(ANY_TARGET, version)

    /** Waterfall/Bungee: one version id for every target. */
    fun waterfall(version: String) = waterfallVersions.put(ANY_TARGET, version)

    /** NeoForge: one version id for every target. */
    fun neoforge(version: String) = neoforgeVersions.put(ANY_TARGET, version)

    /** NeoForge: pick the latest matching version via the Modrinth API. */
    fun neoforgeAuto() = neoforgeVersions.put(ANY_TARGET, AUTO)

    /** NeoForge: distinct version ids per target `mcClassifier`. */
    fun neoforge(vararg byTarget: Pair<String, String>) = neoforgeVersions.putAll(byTarget.toMap())

    companion object {
        /** Map key meaning "this version applies to every target". */
        const val ANY_TARGET = "*"

        /** Version value meaning "resolve the latest from the Modrinth API". */
        const val AUTO = "auto"
    }
}

internal fun Project.magicUtilsConsumerExtension(): MagicUtilsConsumerExtension {
    val existing = extensions.findByType(MagicUtilsConsumerExtension::class.java)
    if (existing != null) return existing
    val ext = extensions.create("magicutilsConsumer", MagicUtilsConsumerExtension::class.java)
    ext.magicutilsVersion.convention(
        providers.gradleProperty("magicutils_version").orElse(project.version.toString()),
    )
    // -Pmagicutils_embed=false selects EXTERNAL (true selects AUTO, i.e. the
    // loader's native embedding); with no property the default is AUTO.
    ext.embedMode.convention(
        providers.gradleProperty("magicutils_embed")
            .map { if (it.toBoolean()) EmbedMode.AUTO else EmbedMode.EXTERNAL }
            .orElse(EmbedMode.AUTO),
    )
    return ext
}

/**
 * Applies conventions to a dev-server spec that don't depend on the active
 * target: port, motd template, online-mode, folia toggle, and the plugin name
 * (from the `pluginDisplayName` property, else the root project name). Version
 * defaults are target-derived and are applied by the platform plugin, which has
 * the target in hand.
 */
internal fun Project.applyDevServerConventions(spec: MagicUtilsDevServerSpec) {
    spec.port.convention(25565)
    spec.motd.convention("{pluginName} {platform} dev")
    spec.onlineMode.convention(false)
    spec.folia.convention(true)
    spec.pluginName.convention(
        providers.gradleProperty("pluginDisplayName").orElse(rootProject.name),
    )
}

/**
 * Resolves [MagicUtilsDevServerSpec.motd] with `{pluginName}` and `{platform}`
 * substituted. [platform] is the runner's platform label (e.g. `Paper`,
 * `Folia`, `Fabric`), supplied by the platform plugin that owns the run task.
 */
internal fun MagicUtilsDevServerSpec.resolvedMotd(platform: String): String =
    motd.get()
        .replace("{pluginName}", pluginName.get())
        .replace("{platform}", platform)

/** A resolved Modrinth dependency: its id and the version to load (or [AUTO]). */
internal data class ResolvedModrinth(val id: String, val version: String)

/**
 * Modrinth dependencies to load for [platform] on the target identified by
 * [mcClassifier] (e.g. `mc1.21`, `mc26`). For each declared dependency the
 * version is looked up target-first, then the [ANY_TARGET] fallback; a
 * dependency with neither is skipped. A version of [AUTO] is passed through for
 * the caller to resolve against the Modrinth API. `folia` reuses `paper`.
 */
internal fun MagicUtilsDevServerSpec.modrinthFor(
    platform: String,
    mcClassifier: String,
): List<ResolvedModrinth> =
    modrinthDependencies.get().mapNotNull { dep ->
        val versions = when (platform) {
            "paper", "folia" -> dep.paperVersions
            "fabric" -> dep.fabricVersions
            "velocity" -> dep.velocityVersions
            "waterfall" -> dep.waterfallVersions
            "neoforge" -> dep.neoforgeVersions
            else -> return@mapNotNull null
        }.get()
        val version = versions[mcClassifier]
            ?: versions[MagicUtilsModrinthDependency.ANY_TARGET]
            ?: return@mapNotNull null
        ResolvedModrinth(dep.id.get(), version)
    }

/**
 * Maven coordinate of a MagicUtils module for the active target. The consumer
 * passes the bare base [version] (e.g. `1.22.0`); the target's Fabric-style
 * `+<minecraft>` suffix is added here. No classifier: the branch is in the
 * version, and the module's main jar is published classifier-less (fabric-api
 * style).
 */
internal fun magicUtilsModuleCoordinate(
    module: String,
    version: String,
    target: MagicUtilsTargetExtension,
): String = "dev.ua.theroer:$module:${target.publishedVersion(version)}"

/**
 * Exposes the resolved target's facts as extra properties so consumer build
 * scripts can reference them without importing the internal target extension
 * type. Called by every consumer-* platform plugin (one place, no duplication).
 *
 * - `magicutilsMinecraftVersion` — e.g. `26.2`
 * - `magicutilsLoaderVersion`    — Fabric loader (empty if the target has none)
 * - `magicutilsNeoforgeVersion`  — NeoForge version (only set if the target defines it)
 * - `magicutilsJavaVersion`      — Int Java level
 * - `magicutilsIsDeobfuscated`   — Boolean
 * - `magicutilsClassifier`       — e.g. `mc26`
 * - `magicutilsPublishedVersion` — the base `magicutils_version` with the
 *   target's `+<minecraft>` suffix (e.g. `1.22.0+26.2`), for the rare coordinate
 *   a script must build by hand (a classifier-less bundle jar).
 */
internal fun Project.exposeMagicUtilsTargetFacts(target: MagicUtilsTargetExtension) {
    val base = magicUtilsConsumerExtension().magicutilsVersion.get()
    extensions.extraProperties.set("magicutilsMinecraftVersion", target.minecraft.get())
    extensions.extraProperties.set("magicutilsLoaderVersion", target.loader.getOrElse(""))
    extensions.extraProperties.set("magicutilsJavaVersion", target.java.get())
    extensions.extraProperties.set("magicutilsIsDeobfuscated", target.isDeobfuscated)
    extensions.extraProperties.set("magicutilsClassifier", target.mcClassifier)
    extensions.extraProperties.set("magicutilsPublishedVersion", target.publishedVersion(base))
    // Optional per-platform facts (not every targets.properties defines them).
    target.neoforge.orNull?.let { extensions.extraProperties.set("magicutilsNeoforgeVersion", it) }
}

/**
 * Registers the modules declared on the consumer extension on the given
 * configurations, resolved to the active target's classifier. Uses lazy
 * `addAllLater` providers rather than `afterEvaluate` + `add`, so the modules
 * are contributed even after Loom eagerly observes the mod configurations
 * (adding late would fail with "configuration was resolved"). Fabric consumers
 * pass mod-aware configuration names; bukkit/common pass plain ones.
 */
internal fun Project.addConsumerMagicUtilsModules(
    target: MagicUtilsTargetExtension,
    apiConfiguration: String,
    implementationConfiguration: String,
) {
    val consumer = magicUtilsConsumerExtension()

    fun register(configurationName: String, modules: org.gradle.api.provider.ListProperty<String>) {
        val deps = consumer.magicutilsVersion.flatMap { version ->
            modules.map { list ->
                list.map { module ->
                    dependencies.create(magicUtilsModuleCoordinate(module, version, target))
                }
            }
        }
        configurations.named(configurationName).configure { it.dependencies.addAllLater(deps) }
    }

    register(apiConfiguration, consumer.apiModules)
    register(implementationConfiguration, consumer.implementationModules)
}

/**
 * Wires the consumer's MagicUtils modules onto a plain-JVM loader (Bukkit,
 * Velocity) honouring [EmbedMode], and — for EXTERNAL — strips MagicUtils and its
 * bundled jackson from the shadow jar so the standalone bundle owns the single
 * runtime copy. Bukkit and Velocity are identical here (both are flat-classpath
 * JVM plugins with no jar-in-jar), so this is the one place that logic lives;
 * each consumer plugin just passes its [ConsumerLoader]. BungeeCord is always
 * shaded (no EXTERNAL split) and uses [addConsumerMagicUtilsModules] instead.
 *
 * Runs in `afterEvaluate` because the consumer sets `embedMode` in its DSL block,
 * which executes after the plugin applies; a plain JVM loader has no Loom
 * early-observe of configurations, so a late `add` is safe.
 */
internal fun Project.configureJvmConsumerEmbed(
    target: MagicUtilsTargetExtension,
    loader: ConsumerLoader,
) {
    val consumer = magicUtilsConsumerExtension()
    afterEvaluate {
        val shaded = resolveEmbedMode(consumer.embedMode.get(), loader) == EmbedMode.SHADED
        val apiConfig = if (shaded) "api" else "compileOnly"
        val implConfig = if (shaded) "implementation" else "compileOnly"
        val base = consumer.magicutilsVersion.get()
        consumer.apiModules.get().forEach { module ->
            dependencies.add(apiConfig, magicUtilsModuleCoordinate(module, base, target))
        }
        consumer.implementationModules.get().forEach { module ->
            dependencies.add(implConfig, magicUtilsModuleCoordinate(module, base, target))
        }

        // EXTERNAL: the modules reach the fat jar transitively via the common
        // module (whose MagicUtils deps are `api`), so moving this module's own
        // deps to compileOnly doesn't remove them — the shadow exclude does. The
        // `**/` prefix also catches multi-release copies under
        // META-INF/versions/<n>/. jackson is the config modules' only external
        // dependency and the bundle ships its own relocated copy; a second one
        // here clashes under the isolated plugin/proxy classloaders, so it goes
        // too.
        if (!shaded) {
            tasks.named("shadowJar", com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class.java)
                .configure { shadow ->
                    shadow.exclude("**/dev/ua/theroer/magicutils/**")
                    shadow.exclude("**/com/fasterxml/jackson/**")
                }
        }
    }
}
