import org.gradle.api.Plugin
import org.gradle.api.Project

class MagicUtilsCommonPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            val moduleNameMap = mapOf(
                "platform-api" to "magicutils-api",
                "platform-neoforge" to "magicutils-neoforge",
                "platform-bukkit" to "magicutils-bukkit",
                "platform-velocity" to "magicutils-velocity",
                "platform-fabric" to "magicutils-fabric",
                "core" to "magicutils-core",
                "config" to "magicutils-config",
                "lang" to "magicutils-lang",
                "logger" to "magicutils-logger",
                "commands" to "magicutils-commands",
                "placeholders" to "magicutils-placeholders",
                "http-client" to "magicutils-http-client",
                "commands-fabric" to "magicutils-commands-fabric",
                "logger-fabric" to "magicutils-logger-fabric",
                "placeholders-fabric" to "magicutils-placeholders-fabric",
                "fabric-bundle" to "magicutils-fabric-bundle",
                "config-yaml" to "magicutils-config-yaml",
                "config-toml" to "magicutils-config-toml",
                "processor" to "magicutils-processor"
            )
            project.extensions.extraProperties.set("moduleNameMap", moduleNameMap)
            project.extensions.extraProperties.set("getModuleName", { projectName: String ->
                moduleNameMap.getOrDefault(projectName, projectName)
            })
        }
    }
}
