package dev.ua.theroer.magicutils.build.matrix

import dev.ua.theroer.magicutils.build.module.*
import dev.ua.theroer.magicutils.build.publish.*
import dev.ua.theroer.magicutils.build.release.*
import dev.ua.theroer.magicutils.build.smoke.*
import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.GradleException
import java.io.File

open class MagicUtilsMatrixSettingsExtension {
    var targetsFile: String = "gradle/targets.properties"
    var publishingFile: String = "gradle/publishing.properties"
    var defaultTarget: String = "mc12110"

    private val commonProjectPaths = linkedSetOf<String>()
    private val platformSpecs = linkedMapOf<String, MagicUtilsPlatformSpec>()
    private val scenarioSpecs = linkedMapOf<String, MagicUtilsScenarioSpec>()

    private var moduleNamePrefixValue: String = ""
    private val moduleNameOverrides = linkedMapOf<String, String>()

    /** Prefix prepended to a subproject name to form its artifact id. */
    fun moduleNamePrefix(prefix: String) {
        moduleNamePrefixValue = prefix
    }

    /** Pins [projectName]'s artifact id to [artifactName], overriding the prefix rule. */
    fun moduleName(projectName: String, artifactName: String) {
        moduleNameOverrides[projectName.trim()] = artifactName.trim()
    }

    internal fun toModuleNamingSpec(): MagicUtilsModuleNamingSpec =
        MagicUtilsModuleNamingSpec(moduleNamePrefixValue, moduleNameOverrides.toMap())

    private val smokeDsl = MagicUtilsSmokeDsl()

    /** Declares the compatibility smoke matrix (server-launch + diagnostics gate). */
    fun smoke(action: org.gradle.api.Action<MagicUtilsSmokeDsl>) {
        action.execute(smokeDsl)
    }

    internal fun toSmokeSpecs(): List<SmokePlatformSpec> = smokeDsl.toSpecs()

    internal fun smokeGate(): SmokeGate = smokeDsl.gate

    private val modrinthDsl = MagicUtilsModrinthDsl()

    /** Declares the Modrinth release (project + per-artifact loaders/game_versions). */
    fun modrinth(action: org.gradle.api.Action<MagicUtilsModrinthDsl>) {
        action.execute(modrinthDsl)
    }

    internal fun toModrinthSpec(): ModrinthReleaseSpec? = modrinthDsl.toSpec()

    private val allTargetsDsl = MagicUtilsAllTargetsDsl()

    /** Shapes the `buildAllTargets` fan-out (targets subset, scenario, task type). */
    fun allTargets(action: org.gradle.api.Action<MagicUtilsAllTargetsDsl>) {
        action.execute(allTargetsDsl)
    }

    internal fun toAllTargetsSpec(): MagicUtilsAllTargetsSpec = allTargetsDsl.toSpec()

    private val releaseDsl = MagicUtilsReleaseDsl()

    /** Shapes the `release` orchestrator (which steps run: maven/modrinth/tag/javadoc/...). */
    fun release(action: org.gradle.api.Action<MagicUtilsReleaseDsl>) {
        action.execute(releaseDsl)
    }

    internal fun toReleaseSpec(): dev.ua.theroer.magicutils.build.release.MagicUtilsReleaseSpec =
        releaseDsl.toSpec()

    fun commonProject(path: String) {
        commonProjectPaths += normalizeProjectPath(path)
    }

    fun commonProjects(vararg paths: String) {
        paths.forEach(::commonProject)
    }

    fun commonProjects(paths: Iterable<String>) {
        paths.forEach(::commonProject)
    }

    fun platform(name: String, projects: Iterable<String>) {
        platform(name, projects, emptyList())
    }

    fun platform(name: String, projects: Iterable<String>, disabledTargetPrefixes: Iterable<String>) {
        val normalizedName = name.trim().lowercase()
        require(normalizedName.isNotEmpty()) { "Platform name must not be empty." }

        val normalizedProjects = projects.map(::normalizeProjectPath).toCollection(linkedSetOf())
        require(normalizedProjects.isNotEmpty()) {
            "Platform '$normalizedName' must declare at least one project."
        }

        platformSpecs[normalizedName] = MagicUtilsPlatformSpec(
            name = normalizedName,
            projects = normalizedProjects,
            disabledTargetPrefixes = disabledTargetPrefixes
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet(),
        )
    }

    fun scenario(name: String, platforms: Iterable<String>) {
        scenario(name, platforms, "")
    }

    fun scenario(name: String, platforms: Iterable<String>, description: String) {
        val normalizedName = name.trim().lowercase()
        require(normalizedName.isNotEmpty()) { "Scenario name must not be empty." }

        val normalizedPlatforms = platforms
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toCollection(linkedSetOf())
        require(normalizedPlatforms.isNotEmpty()) {
            "Scenario '$normalizedName' must declare at least one platform."
        }

        scenarioSpecs[normalizedName] = MagicUtilsScenarioSpec(
            name = normalizedName,
            platforms = normalizedPlatforms,
            description = description.trim(),
        )
    }

    internal fun toDefinition(): MagicUtilsMatrixDefinition {
        if (commonProjectPaths.isEmpty()) {
            throw GradleException(
                "MagicUtils matrix must declare common projects via " +
                    "magicMatrix { commonProjects(...) } in settings.gradle."
            )
        }
        if (platformSpecs.isEmpty()) {
            throw GradleException(
                "MagicUtils matrix must declare platforms via " +
                    "magicMatrix { platform(...) } in settings.gradle."
            )
        }
        if (scenarioSpecs.isEmpty()) {
            throw GradleException(
                "MagicUtils matrix must declare scenarios via " +
                    "magicMatrix { scenario(...) } in settings.gradle."
            )
        }

        val unknownPlatforms = scenarioSpecs.values
            .flatMap { scenario -> scenario.platforms.filter { it !in platformSpecs } }
            .toSet()
        if (unknownPlatforms.isNotEmpty()) {
            throw GradleException(
                "MagicUtils matrix scenarios reference unknown platforms: " +
                    unknownPlatforms.sorted().joinToString(", ")
            )
        }

        return MagicUtilsMatrixDefinition(
            targetsFile = targetsFile,
            defaultTarget = defaultTarget,
            commonProjects = commonProjectPaths,
            platforms = platformSpecs.toMap(),
            scenarios = scenarioSpecs.toMap(),
        )
    }
}

// `internal`, not `private`: MagicUtilsTaskSelectionTest exercises the inference
// directly, and the test source set is a friend module of main.
internal data class MagicUtilsTaskSelection(
    val scenarioName: String?,
    val platforms: Set<String>?,
    val preferAllAvailable: Boolean = false,
)

private val QUERY_OR_FULL_GRAPH_TASKS = setOf(
    "assemble",
    "build",
    "check",
    "clean",
    "classes",
    "dependencies",
    "dependencyinsight",
    "help",
    "jar",
    "projects",
    "properties",
    "publish",
    "publishcommonmatrix",
    "publishdefaultmatrix",
    "publishfabricmatrix",
    "publishtomavenlocal",
    "tasks",
    "test",
)

private val EXTERNAL_PLATFORM_TASKS = mapOf(
    "runbukkit" to "bukkit",
    "runpaper" to "bukkit",
    "runfolia" to "bukkit",
    "runbungee" to "bungee",
    "runvelocity" to "velocity",
    "runfabric" to "fabric",
    "runneoforge" to "neoforge",
)

private fun parsePlatformList(raw: String?): Set<String> =
    raw.orEmpty()
        .split(',')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toCollection(linkedSetOf())

internal fun inferTaskSelection(
    requestedTaskNames: List<String>,
    definition: MagicUtilsMatrixDefinition,
): MagicUtilsTaskSelection {
    if (requestedTaskNames.isEmpty()) {
        return MagicUtilsTaskSelection(
            scenarioName = if ("workspace" in definition.scenarios) "workspace" else null,
            platforms = null,
        )
    }

    val scenarioAliasMap = definition.scenarios.values.associateBy { scenario ->
        capitalizeScenarioToken(scenario.name).lowercase()
    }
    val projectPathToPlatform = definition.platforms.values
        .flatMap { platform -> platform.projects.map { projectPath -> projectPath to platform.name } }
        .toMap()
    val inferredPlatforms = linkedSetOf<String>()
    // Whether any requested task named a subproject (`:velocity:build`). Such a request
    // is already scoped by the user, so it must never widen to the whole workspace.
    var sawProjectQualifiedTask = false

    for (rawTaskName in requestedTaskNames.map(String::trim).filter(String::isNotEmpty)) {
        val canonicalTaskName = rawTaskName.substringAfterLast(':').substringBefore('@').lowercase()

        // A subproject-qualified task scopes to that subproject's platform, whatever the
        // task is called. Checked before the full-graph shortcut below, because names like
        // `build`/`jar`/`tasks` are exactly the ones that would otherwise pull in every
        // platform — configuring, say, bukkit-bundle for a `:fabric-bundle:build`.
        // `:build` (root, lastIndexOf == 0) is not subproject-qualified.
        if (rawTaskName.lastIndexOf(':') > 0) {
            sawProjectQualifiedTask = true
            val owningProjectPath = rawTaskName.substringBeforeLast(':')
            for ((projectPath, platformName) in projectPathToPlatform) {
                if (owningProjectPath == projectPath || owningProjectPath.startsWith("$projectPath:")) {
                    inferredPlatforms += platformName
                }
            }
            continue
        }

        if (canonicalTaskName in QUERY_OR_FULL_GRAPH_TASKS || canonicalTaskName == "listbuildmatrix") {
            return MagicUtilsTaskSelection(scenarioName = "workspace", platforms = null, preferAllAvailable = true)
        }

        scenarioAliasMap[canonicalTaskName.removePrefix("build")]?.let { scenario ->
            if (canonicalTaskName == "build${capitalizeScenarioToken(scenario.name).lowercase()}") {
                return MagicUtilsTaskSelection(scenarioName = scenario.name, platforms = scenario.platforms)
            }
        }
        scenarioAliasMap[canonicalTaskName.removePrefix("check")]?.let { scenario ->
            if (canonicalTaskName == "check${capitalizeScenarioToken(scenario.name).lowercase()}") {
                return MagicUtilsTaskSelection(scenarioName = scenario.name, platforms = scenario.platforms)
            }
        }
        scenarioAliasMap[canonicalTaskName.removePrefix("publishtomavenlocal")]?.let { scenario ->
            if (canonicalTaskName == "publishtomavenlocal${capitalizeScenarioToken(scenario.name).lowercase()}") {
                return MagicUtilsTaskSelection(scenarioName = scenario.name, platforms = scenario.platforms)
            }
        }

        if (canonicalTaskName == "buildscenario" ||
            canonicalTaskName == "checkscenario" ||
            canonicalTaskName == "publishscenariotomavenlocal") {
            // These generic aggregates should build whatever the current
            // target actually supports — never fail because the default
            // "workspace" scenario references a platform disabled for this
            // target (e.g. fabric on mc26). preferAllAvailable resolves to
            // the target's available platforms downstream.
            return MagicUtilsTaskSelection(
                scenarioName = if ("workspace" in definition.scenarios) "workspace" else null,
                platforms = null,
                preferAllAvailable = true,
            )
        }

        EXTERNAL_PLATFORM_TASKS[canonicalTaskName]?.let { platformName ->
            inferredPlatforms += platformName
        }
    }

    return when {
        inferredPlatforms.isNotEmpty() -> MagicUtilsTaskSelection(
            scenarioName = null,
            platforms = inferredPlatforms,
        )
        // Scoped to subprojects that belong to no platform (a common module, or
        // build-logic itself): include the common projects only, rather than falling
        // back to the whole workspace.
        sawProjectQualifiedTask -> MagicUtilsTaskSelection(
            scenarioName = null,
            platforms = emptySet(),
        )
        else -> MagicUtilsTaskSelection(
            scenarioName = if ("workspace" in definition.scenarios) "workspace" else null,
            platforms = null,
        )
    }
}

private fun resolveMatrixContext(
    settings: Settings,
    definition: MagicUtilsMatrixDefinition,
): MagicUtilsMatrixResolvedContext {
    val projectProperties = settings.gradle.startParameter.projectProperties
    val explicitTarget = projectProperties["target"]
        ?: projectProperties["magicutils.target"]
        ?: System.getProperty("magicutils.target")
    val targetsFile = File(settings.rootDir, definition.targetsFile)
    val target = resolveMagicUtilsTargetSpec(
        targetsFile = targetsFile,
        defaultTarget = definition.defaultTarget,
        explicitTarget = explicitTarget,
    )

    val availablePlatforms = definition.platforms.values
        .filter { platform -> platform.isEnabledFor(target.name) }
        .mapTo(linkedSetOf()) { it.name }

    val explicitScenario = (
        projectProperties["scenario"]
            ?: System.getProperty("magicutils.scenario")
        )
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
    val explicitPlatforms = parsePlatformList(
        projectProperties["includePlatforms"] ?: System.getProperty("magicutils.includePlatforms")
    )

    val unknownExplicitPlatforms = explicitPlatforms - definition.platforms.keys
    if (unknownExplicitPlatforms.isNotEmpty()) {
        throw GradleException(
            "Unknown platforms requested via includePlatforms: " +
                unknownExplicitPlatforms.sorted().joinToString(", ")
        )
    }

    val inferredSelection = inferTaskSelection(settings.gradle.startParameter.taskNames, definition)
    val requestedPlatforms = when {
        explicitPlatforms.isNotEmpty() -> explicitPlatforms
        explicitScenario != null -> definition.scenarios[explicitScenario]?.platforms
            ?: throw GradleException("Unknown MagicUtils scenario '$explicitScenario'.")
        inferredSelection.platforms != null -> inferredSelection.platforms
        inferredSelection.preferAllAvailable -> availablePlatforms
        inferredSelection.scenarioName != null -> definition.scenarios[inferredSelection.scenarioName]?.platforms
            ?: availablePlatforms
        else -> availablePlatforms
    }

    val unsupportedPlatforms = requestedPlatforms - availablePlatforms
    if (unsupportedPlatforms.isNotEmpty()) {
        throw GradleException(
            "Target ${target.name} does not support platforms: " +
                unsupportedPlatforms.sorted().joinToString(", ")
        )
    }

    val selectedPlatforms = requestedPlatforms.intersect(availablePlatforms)
    val includedProjects = linkedSetOf<String>().apply {
        addAll(definition.commonProjects)
        for (platformName in selectedPlatforms) {
            addAll(definition.platforms.getValue(platformName).projects)
        }
    }

    return MagicUtilsMatrixResolvedContext(
        definition = definition,
        target = target,
        availablePlatforms = availablePlatforms,
        selectedPlatforms = selectedPlatforms,
        includedProjects = includedProjects,
        selectedScenario = explicitScenario ?: inferredSelection.scenarioName,
        requestedTaskNames = settings.gradle.startParameter.taskNames.toList(),
    )
}

/**
 * Platform plugin id -> the key holding its version in
 * `magicutils/platform-plugins.properties` (baked into the build-logic jar by the
 * `platformPluginVersions` task).
 *
 * Loom ships under two marker coordinates — the classic remapping id and the
 * no-remap id used on deobfuscated (26.x) targets — and both resolve to the same
 * `net.fabricmc:fabric-loom` artifact, so they share one version. run-velocity is
 * released in lockstep with run-paper, hence the shared `runPaperVersion`.
 */
private val PLATFORM_PLUGIN_VERSION_KEYS = linkedMapOf(
    "fabric-loom" to "fabricLoomVersion",
    "net.fabricmc.fabric-loom" to "fabricLoomVersion",
    "net.neoforged.moddev" to "neoForgeModdevVersion",
    "xyz.jpenilla.run-paper" to "runPaperVersion",
    "xyz.jpenilla.run-velocity" to "runPaperVersion",
    "xyz.jpenilla.run-waterfall" to "runProxyVersion",
)

/**
 * Reads the platform plugin versions baked into this jar. Empty when the resource is
 * absent, which keeps an older build-logic jar usable (callers then have to declare
 * the versions themselves, exactly as before this resource existed).
 */
internal fun loadPlatformPluginVersions(): Map<String, String> {
    val properties = java.util.Properties()
    val stream = MagicUtilsMatrixSettingsPlugin::class.java.classLoader
        .getResourceAsStream("magicutils/platform-plugins.properties")
        ?: return emptyMap()
    stream.use(properties::load)

    val versions = linkedMapOf<String, String>()
    PLATFORM_PLUGIN_VERSION_KEYS.forEach { (pluginId, key) ->
        val version = properties.getProperty(key)?.trim()
        if (!version.isNullOrEmpty()) {
            versions[pluginId] = version
        }
    }
    return versions
}

class MagicUtilsMatrixSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        // The platform plugins (Loom, ModDevGradle, the jpenilla run-* runners) are
        // `compileOnly` in build-logic so that, say, a NeoForge consumer never has to
        // resolve Fabric Loom. That means they are NOT on the buildscript classpath and
        // every consumer would otherwise have to repeat each version in its own
        // `pluginManagement`. Declaring them here — from the versions baked into this
        // jar — keeps one source of truth: a project can then write
        // `plugins { id("fabric-loom") }` with no version, and only the platforms it
        // actually asks for get resolved.
        val platformPluginVersions = loadPlatformPluginVersions()
        if (platformPluginVersions.isNotEmpty()) {
            settings.pluginManagement { pluginManagement ->
                pluginManagement.plugins { plugins ->
                    platformPluginVersions.forEach { (pluginId, version) ->
                        plugins.id(pluginId).version(version)
                    }
                }
            }
        }

        val extension = settings.extensions.create(
            "magicMatrix",
            MagicUtilsMatrixSettingsExtension::class.java,
        )
        // The matrix (common projects, platforms, scenarios, module naming) is
        // configured entirely by the consumer via the `magicMatrix { ... }` DSL
        // in settings.gradle — the plugin ships no project-specific defaults.

        settings.gradle.settingsEvaluated {
            val definition = extension.toDefinition()
            val resolvedContext = resolveMatrixContext(settings, definition)
            val publishingSpec = loadPublishingSpec(File(settings.rootDir, extension.publishingFile))

            settings.gradle.extensions.extraProperties.set("magicutilsMatrixDefinition", definition)
            settings.gradle.extensions.extraProperties.set("magicutilsMatrixResolved", resolvedContext)
            settings.gradle.extensions.extraProperties.set("magicutilsPublishingSpec", publishingSpec)
            settings.gradle.extensions.extraProperties.set("magicutilsModuleNaming", extension.toModuleNamingSpec())
            settings.gradle.extensions.extraProperties.set("magicutilsSmokeSpecs", extension.toSmokeSpecs())
            settings.gradle.extensions.extraProperties.set("magicutilsSmokeGate", extension.smokeGate())
            settings.gradle.extensions.extraProperties.set("magicutilsModrinthSpec", extension.toModrinthSpec())
            settings.gradle.extensions.extraProperties.set("magicutilsAllTargetsSpec", extension.toAllTargetsSpec())
            settings.gradle.extensions.extraProperties.set("magicutilsReleaseSpec", extension.toReleaseSpec())

            resolvedContext.includedProjects.forEach { projectPath ->
                settings.include(projectPath.removePrefix(":"))
            }
        }
    }
}
