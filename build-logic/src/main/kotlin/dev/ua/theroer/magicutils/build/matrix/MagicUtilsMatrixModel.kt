package dev.ua.theroer.magicutils.build.matrix

import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.GradleException

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
