import org.gradle.api.GradleException
import java.io.File
import java.util.Properties

data class MagicUtilsTargetSpec(
    val name: String,
    val minecraft: String,
    val java: Int,
    val yarn: String?,
    val loader: String?,
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
