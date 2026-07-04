import org.gradle.api.Plugin
import org.gradle.api.Project

class MagicUtilsCommonPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            // Module naming is configured by the matrix settings DSL and shared
            // via gradle extraProperties. Consumers that apply this plugin without
            // the settings plugin get an identity mapping (artifactId == projectName).
            val namingSpec = project.gradle.extensions.extraProperties
                .let { if (it.has("magicutilsModuleNaming")) it.get("magicutilsModuleNaming") else null }
                    as? MagicUtilsModuleNamingSpec
                ?: MagicUtilsModuleNamingSpec()
            project.extensions.extraProperties.set("magicutilsModuleNaming", namingSpec)
            project.extensions.extraProperties.set("getModuleName", { projectName: String ->
                namingSpec.moduleName(projectName)
            })

            if (extensions.findByName(MAGICUTILS_PUBLISH_EXTENSION_NAME) == null) {
                val publishExtension = extensions.create(
                    MAGICUTILS_PUBLISH_EXTENSION_NAME,
                    MagicUtilsPublishExtension::class.java,
                )
                publishExtension.category.convention(MagicUtilsPublishCategory.DEFAULT_ONLY)
            }
        }
    }
}
