import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.GradleException
import java.io.File

open class MagicUtilsMatrixSettingsExtension {
    var targetsFile: String = "gradle/targets.properties"
    var defaultTarget: String = "mc12110"

    private val commonProjectPaths = linkedSetOf<String>()
    private val platformSpecs = linkedMapOf<String, MagicUtilsPlatformSpec>()
    private val scenarioSpecs = linkedMapOf<String, MagicUtilsScenarioSpec>()

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
            throw GradleException("MagicUtils matrix must declare common projects.")
        }
        if (platformSpecs.isEmpty()) {
            throw GradleException("MagicUtils matrix must declare platforms.")
        }
        if (scenarioSpecs.isEmpty()) {
            throw GradleException("MagicUtils matrix must declare scenarios.")
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

private data class MagicUtilsTaskSelection(
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

private fun inferTaskSelection(
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

    for (rawTaskName in requestedTaskNames.map(String::trim).filter(String::isNotEmpty)) {
        val canonicalTaskName = rawTaskName.substringAfterLast(':').substringBefore('@').lowercase()
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
            return MagicUtilsTaskSelection(
                scenarioName = if ("workspace" in definition.scenarios) "workspace" else null,
                platforms = null,
            )
        }

        EXTERNAL_PLATFORM_TASKS[canonicalTaskName]?.let { platformName ->
            inferredPlatforms += platformName
        }

        for ((projectPath, platformName) in projectPathToPlatform) {
            if (rawTaskName == projectPath || rawTaskName.startsWith("$projectPath:")) {
                inferredPlatforms += platformName
            }
        }
    }

    return when {
        inferredPlatforms.isNotEmpty() -> MagicUtilsTaskSelection(
            scenarioName = null,
            platforms = inferredPlatforms,
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
    val targetsFile = File(settings.rootDir, definition.targetsFile)
    val target = resolveMagicUtilsTargetSpec(
        targetsFile = targetsFile,
        defaultTarget = definition.defaultTarget,
        explicitTarget = projectProperties["target"],
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

private fun applyMagicUtilsMatrixDefaults(extension: MagicUtilsMatrixSettingsExtension) {
    extension.commonProjects(
        "platform-api",
        "core",
        "config",
        "lang",
        "logger",
        "commands",
        "commands-brigadier",
        "placeholders",
        "http-client",
        "config-yaml",
        "config-toml",
        "diagnostics",
        "processor",
    )

    extension.platform("bukkit", listOf("platform-bukkit", "bukkit-bundle"))
    extension.platform("bungee", listOf("platform-bungee"))
    extension.platform("velocity", listOf("platform-velocity"))
    extension.platform("fabric", listOf(
        "platform-fabric",
        "commands-fabric",
        "logger-fabric",
        "placeholders-fabric",
        "fabric-bundle",
    ), listOf("mc26"))
    extension.platform("neoforge", listOf("platform-neoforge", "commands-neoforge"))

    extension.scenario("workspace", listOf("bukkit", "bungee", "velocity", "fabric", "neoforge"), "Full multi-platform workspace")
    extension.scenario("bukkit", listOf("bukkit"), "Bukkit and Paper modules")
    extension.scenario("bungee", listOf("bungee"), "BungeeCord modules")
    extension.scenario("velocity", listOf("velocity"), "Velocity modules")
    extension.scenario("fabric", listOf("fabric"), "Fabric modules")
    extension.scenario("neoforge", listOf("neoforge"), "NeoForge modules")
}

class MagicUtilsMatrixSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val extension = settings.extensions.create(
            "magicMatrix",
            MagicUtilsMatrixSettingsExtension::class.java,
        )
        applyMagicUtilsMatrixDefaults(extension)

        settings.gradle.settingsEvaluated {
            val definition = extension.toDefinition()
            val resolvedContext = resolveMatrixContext(settings, definition)

            settings.gradle.extensions.extraProperties.set("magicutilsMatrixDefinition", definition)
            settings.gradle.extensions.extraProperties.set("magicutilsMatrixResolved", resolvedContext)

            resolvedContext.includedProjects.forEach { projectPath ->
                settings.include(projectPath.removePrefix(":"))
            }
        }
    }
}
