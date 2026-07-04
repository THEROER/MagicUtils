import org.gradle.api.GradleException
import java.io.File
import java.util.Properties

data class MagicUtilsTargetSpec(
    val name: String,
    val minecraft: String,
    val java: Int,
    val yarn: String?,
    val loader: String?,
    val fabricApi: String?,
    val paper: String?,
    val miniplaceholdersApi: String?,
    val pb4PlaceholderApi: String?,
    val neoforge: String?,
)

data class MagicUtilsPlatformSpec(
    val name: String,
    val projects: Set<String>,
    val disabledTargetPrefixes: Set<String> = emptySet(),
) {
    fun isEnabledFor(targetName: String): Boolean =
        disabledTargetPrefixes.none(targetName::startsWith)
}

data class MagicUtilsScenarioSpec(
    val name: String,
    val platforms: Set<String>,
    val description: String = "",
)

data class MagicUtilsMatrixDefinition(
    val targetsFile: String,
    val defaultTarget: String,
    val commonProjects: Set<String>,
    val platforms: Map<String, MagicUtilsPlatformSpec>,
    val scenarios: Map<String, MagicUtilsScenarioSpec>,
)

data class MagicUtilsMatrixResolvedContext(
    val definition: MagicUtilsMatrixDefinition,
    val target: MagicUtilsTargetSpec,
    val availablePlatforms: Set<String>,
    val selectedPlatforms: Set<String>,
    val includedProjects: Set<String>,
    val selectedScenario: String?,
    val requestedTaskNames: List<String>,
)

data class MagicUtilsPublishingSpec(
    val group: String,
    val repoUrl: String,
    val smokeArtifact: String,
)

/**
 * Maps a Gradle subproject name to its published artifact id. By default the
 * artifact id is [prefix] + projectName; [overrides] pins specific projects to
 * an explicit name. Consumers configure this via the matrix DSL, so the plugin
 * core carries no project-specific naming.
 */
data class MagicUtilsModuleNamingSpec(
    val prefix: String = "",
    val overrides: Map<String, String> = emptyMap(),
) {
    fun moduleName(projectName: String): String =
        overrides[projectName] ?: (prefix + projectName)
}

internal fun normalizeProjectPath(path: String): String =
    path.trim().removeSuffix(":").let {
        when {
            it.isEmpty() -> throw GradleException("Project path must not be empty.")
            it.startsWith(":") -> it
            else -> ":$it"
        }
    }

internal fun normalizeTargetName(rawTargetName: String): String =
    rawTargetName.trim().let {
        when {
            it.isEmpty() -> throw GradleException("Target name must not be empty.")
            it.startsWith("mc") -> it
            else -> "mc$it"
        }
    }

internal fun loadTargetProperties(targetsFile: File): Properties {
    if (!targetsFile.isFile) {
        throw GradleException("Missing targets file: ${targetsFile.absolutePath}")
    }

    return Properties().also { properties ->
        targetsFile.inputStream().use(properties::load)
    }
}

/**
 * Every target declared in [targetsFile], in file order. A target is any
 * `mcXXXX` prefix that has a `.minecraft` value (the one required key —
 * see [resolveMagicUtilsTargetSpec]). This is the single source of truth
 * the CI build/publish matrices are generated from, so no target list is
 * ever duplicated in a workflow.
 */
internal fun loadAllTargetNames(targetsFile: File): List<String> {
    val properties = loadTargetProperties(targetsFile)
    return properties.stringPropertyNames()
        .mapNotNull { key -> key.removeSuffix(".minecraft").takeIf { it != key } }
        .distinct()
        // Properties has no defined order; sort for stable, reproducible output.
        .sorted()
}

/** One (target, publish-tasks) unit for the CI publish matrix. */
data class MagicUtilsPublishUnit(
    val target: String,
    val publishTasks: List<String>,
    val suffix: Boolean,
)

/**
 * Which platforms a target supports, honouring each platform's
 * [MagicUtilsPlatformSpec.isEnabledFor] (disabled-prefix) rules.
 */
internal fun MagicUtilsMatrixDefinition.availablePlatformsFor(target: String): Set<String> =
    platforms.values.filter { it.isEnabledFor(target) }.map { it.name }.toSet()

/**
 * Publish units for every target. The default target publishes all
 * categories (DEFAULT_ONLY runs exactly once, here). Non-default targets
 * publish COMMON_MATRIX, plus FABRIC_MATRIX when the fabric platform is
 * enabled for that target. Non-default targets carry the `mcXXXX`
 * classifier suffix.
 */
internal fun MagicUtilsMatrixDefinition.publishUnits(allTargets: List<String>): List<MagicUtilsPublishUnit> =
    allTargets.map { target ->
        if (target == defaultTarget) {
            MagicUtilsPublishUnit(target, listOf("publishDefaultMatrix"), suffix = false)
        } else {
            val tasks = mutableListOf("publishCommonMatrix")
            if ("fabric" in availablePlatformsFor(target)) {
                tasks += "publishFabricMatrix"
            }
            MagicUtilsPublishUnit(target, tasks, suffix = true)
        }
    }

/** Minimal JSON array serializer for the matrix outputs (no dependency needed). */
internal fun List<MagicUtilsPublishUnit>.toMatrixJson(): String =
    joinToString(prefix = "[", postfix = "]", separator = ",") { unit ->
        """{"target":"${unit.target}",""" +
            """"tasks":"${unit.publishTasks.joinToString(" ")}",""" +
            """"suffix":${unit.suffix}}"""
    }

internal fun loadPublishingSpec(publishingFile: File): MagicUtilsPublishingSpec {
    if (!publishingFile.isFile) {
        throw GradleException("Missing publishing file: ${publishingFile.absolutePath}")
    }

    val properties = Properties().also { properties ->
        publishingFile.inputStream().use(properties::load)
    }

    fun requireValue(key: String): String =
        properties.getProperty(key)?.trim()?.takeIf(String::isNotEmpty)
            ?: throw GradleException(
                "Missing or empty '$key' in ${publishingFile.absolutePath}"
            )

    return MagicUtilsPublishingSpec(
        group = requireValue("group"),
        repoUrl = requireValue("repo.url").trimEnd('/'),
        smokeArtifact = requireValue("smoke.artifact"),
    )
}

internal fun resolveMagicUtilsTargetSpec(
    targetsFile: File,
    defaultTarget: String,
    explicitTarget: String?,
): MagicUtilsTargetSpec {
    val properties = loadTargetProperties(targetsFile)
    val fallbackTarget = properties.getProperty("target", defaultTarget)
    val targetName = normalizeTargetName(explicitTarget ?: fallbackTarget)

    fun requireValue(suffix: String): String =
        properties.getProperty("$targetName.$suffix")
            ?: throw GradleException(
                "Missing '$targetName.$suffix' in ${targetsFile.absolutePath}"
            )

    return MagicUtilsTargetSpec(
        name = targetName,
        minecraft = requireValue("minecraft"),
        java = requireValue("java").toInt(),
        yarn = properties.getProperty("$targetName.yarn"),
        loader = properties.getProperty("$targetName.loader"),
        fabricApi = properties.getProperty("$targetName.fabric_api"),
        paper = properties.getProperty("$targetName.paper"),
        miniplaceholdersApi = properties.getProperty("$targetName.miniplaceholders_api"),
        pb4PlaceholderApi = properties.getProperty("$targetName.pb4_placeholder_api"),
        neoforge = properties.getProperty("$targetName.neoforge"),
    )
}

internal fun capitalizeScenarioToken(value: String): String =
    value.split('-', '_', ' ')
        .filter { it.isNotBlank() }
        .joinToString("") { token ->
            token.replaceFirstChar { char ->
                if (char.isLowerCase()) {
                    char.titlecase()
                } else {
                    char.toString()
                }
            }
        }
