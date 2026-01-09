import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import java.io.File
import java.util.Properties

abstract class MagicutilsTargetExtension {
    abstract val minecraft: Property<String>
    abstract val java: Property<Int>
    abstract val yarn: Property<String>
    abstract val loader: Property<String>
    abstract val paper: Property<String>
    abstract val miniplaceholders_api: Property<String>
    abstract val pb4_placeholder_api: Property<String>
    abstract val neoforge: Property<String>
}

class MagicutilsTargetPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            val extension = extensions.create("magicutilsTarget", MagicutilsTargetExtension::class.java)

            val targetsFile = File(rootDir, "gradle/targets.properties")
            val properties = Properties()
            if (targetsFile.exists()) {
                targetsFile.inputStream().use { properties.load(it) }
            }

            val rawTargetName = if (project.hasProperty("target")) {
                project.property("target") as String
            } else {
                properties.getProperty("target", "mc12110")
            }
            val targetName = if (rawTargetName.startsWith("mc")) {
                rawTargetName
            } else {
                "mc$rawTargetName"
            }

            extension.minecraft.set(properties.getProperty("$targetName.minecraft"))
            extension.java.set(properties.getProperty("$targetName.java")?.toInt())
            extension.yarn.set(properties.getProperty("$targetName.yarn"))
            extension.loader.set(properties.getProperty("$targetName.loader"))
            extension.pb4_placeholder_api.set(properties.getProperty("$targetName.pb4_placeholder_api"))
            extension.miniplaceholders_api.set(properties.getProperty("$targetName.miniplaceholders_api"))
            extension.paper.set(properties.getProperty("$targetName.paper"))
            extension.neoforge.set(properties.getProperty("$targetName.neoforge"))

            project.extensions.extraProperties.set("magicutilsTarget", extension)
        }
    }
}
