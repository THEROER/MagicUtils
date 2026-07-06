package dev.ua.theroer.magicutils.build.matrix

import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.Project

/**
 * Registers `magicutilsHelp` — a single, human-readable overview of everything
 * the MagicUtils build-logic adds to a consumer project: the properties it
 * reads (targets, embed, scenario) and the tasks it contributes (matrix, smoke,
 * release). So nobody has to remember `-Ptarget=...` or dig through `tasks`.
 */
internal fun registerMagicUtilsHelpTask(
    project: Project,
    resolvedContext: MagicUtilsMatrixResolvedContext,
) {
    project.tasks.register("magicutilsHelp") { task ->
        task.group = "help"
        task.description = "Show MagicUtils build parameters and tasks."
        task.doLast {
            val definition = resolvedContext.definition
            val targetsFile = project.rootProject.file(definition.targetsFile)
            val allTargets = runCatching { loadAllTargetNames(targetsFile) }.getOrDefault(emptyList())

            val out = buildString {
                appendLine("MagicUtils build help")
                appendLine("=====================")
                appendLine()
                val libraryMc = resolvedContext.target.libraryMinecraft
                val mcLine = if (libraryMc == resolvedContext.target.minecraft) {
                    "Minecraft ${resolvedContext.target.minecraft}"
                } else {
                    "Minecraft ${resolvedContext.target.minecraft} runtime, library $libraryMc"
                }
                appendLine("Active target : ${resolvedContext.target.name} " +
                    "($mcLine, Java ${resolvedContext.target.java})")
                appendLine("Platforms     : ${resolvedContext.selectedPlatforms.joinToString(", ")}")
                appendLine()
                appendLine("Parameters (-P...)")
                appendLine("  -Ptarget=<mcXXXX>     Build for a specific target. Available: " +
                    allTargets.joinToString(", ").ifEmpty { "(see ${definition.targetsFile})" })
                appendLine("  -Pscenario=<name>     Limit to a platform scenario: " +
                    definition.scenarios.keys.joinToString(", "))
                appendLine("  -PincludePlatforms=a,b  Build only these platforms.")
                appendLine("  -Pmagicutils_embed=<bool>  Consumers: bundle MagicUtils into the artifact (default true); false expects it installed separately.")
                appendLine("  -PsmokeCase=<id>      Run a single compatibility smoke case.")
                appendLine("  -Pversion=X.Y.Z       Release version for the release tasks.")
                appendLine("  -PallTargets.targets=mcA,mcB   buildAllTargets: only these targets.")
                appendLine("  -PallTargets.scenario=<name>   buildAllTargets: platforms of this scenario.")
                appendLine("  -PallTargets.taskType=<t>      buildAllTargets: build | check | publishToMavenLocal.")
                appendLine()
                appendLine("Tasks")
                appendLine("  Build matrix : listBuildMatrix, buildScenario, checkScenario")
                appendLine("  All targets  : buildAllTargets (fan out over every version; -PallTargets.*)")
                appendLine("                 printBuildMatrix, printPublishMatrix, printReleaseMatrix (CI JSON)")
                appendLine("  Smoke        : listSmokeMatrix, runCompatibilitySmoke")
                appendLine("  Release      : releasePreflight, bumpVersion, dispatchRelease, smokeTest, release")
                appendLine("  Publish      : publishDefaultMatrix, publishCommonMatrix, publishFabricMatrix")
                appendLine()
                appendLine("Examples")
                appendLine("  ./gradlew build -Ptarget=mc262")
                appendLine("  ./gradlew runCompatibilitySmoke -PsmokeCase=fabric-121x")
                appendLine("  ./gradlew listBuildMatrix")
            }
            println(out)
        }
    }
}
