package dev.ua.theroer.magicutils.build.matrix

import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class MagicUtilsTargetPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            val extension = extensions.create("magicutilsTarget", MagicUtilsTargetExtension::class.java)

            val resolvedContext = gradle.extensions.extraProperties.properties["magicutilsMatrixResolved"]
                as? MagicUtilsMatrixResolvedContext
            val defaultTarget = resolvedContext?.definition?.defaultTarget ?: "mc12110"
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
                    defaultTarget = defaultTarget,
                    explicitTarget = explicitTarget,
                )
            }

            extension.name.set(targetSpec.name)
            extension.defaultTarget.set(targetSpec.name == defaultTarget)
            extension.minecraft.set(targetSpec.minecraft)
            extension.libraryMinecraft.set(targetSpec.libraryMinecraft)
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
