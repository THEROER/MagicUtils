import org.gradle.api.Project

internal fun Project.magicUtilsModuleName(projectName: String = name): String {
    val moduleNameMap = extensions.extraProperties.get("moduleNameMap") as? Map<*, *>
        ?: error("magicutils.common must define moduleNameMap before reading module names")
    return moduleNameMap[projectName] as? String ?: projectName
}
