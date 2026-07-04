import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import java.io.File

abstract class MagicUtilsTargetExtension {
    abstract val name: Property<String>
    abstract val minecraft: Property<String>
    abstract val java: Property<Int>
    abstract val yarn: Property<String>
    abstract val loader: Property<String>
    abstract val fabric_api: Property<String>
    abstract val paper: Property<String>
    abstract val miniplaceholders_api: Property<String>
    abstract val pb4_placeholder_api: Property<String>
    abstract val neoforge: Property<String>
}

class MagicUtilsTargetPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            val extension = extensions.create("magicutilsTarget", MagicUtilsTargetExtension::class.java)

            val resolvedContext = gradle.extensions.extraProperties.properties["magicutilsMatrixResolved"]
                as? MagicUtilsMatrixResolvedContext
            val targetSpec = if (resolvedContext != null) {
                resolvedContext.target
            } else {
                val explicitTarget = if (project.hasProperty("target")) {
                    project.property("target") as String
                } else {
                    project.findProperty("magicutils.target") as? String
                        ?: System.getProperty("magicutils.target")
                }
                resolveMagicUtilsTargetSpec(
                    targetsFile = File(rootDir, "gradle/targets.properties"),
                    defaultTarget = "mc12110",
                    explicitTarget = explicitTarget,
                )
            }

            extension.name.set(targetSpec.name)
            extension.minecraft.set(targetSpec.minecraft)
            extension.java.set(targetSpec.java)
            extension.yarn.set(targetSpec.yarn)
            extension.loader.set(targetSpec.loader)
            extension.fabric_api.set(targetSpec.fabricApi)
            extension.pb4_placeholder_api.set(targetSpec.pb4PlaceholderApi)
            extension.miniplaceholders_api.set(targetSpec.miniplaceholdersApi)
            extension.paper.set(targetSpec.paper)
            extension.neoforge.set(targetSpec.neoforge)

            project.extensions.extraProperties.set("magicutilsTargetName", targetSpec.name)
            project.extensions.extraProperties.set("magicutilsTarget", extension)
        }
    }
}
