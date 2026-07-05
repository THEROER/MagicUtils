package dev.ua.theroer.magicutils.build.module

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
