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
 * Publish units, one per distinct Java level rather than per Minecraft target.
 *
 * The published coordinate is `<base>+java<N>` (see [publishedVersion]) because
 * MagicUtils' compiled bytecode depends only on the Java level, not the
 * Minecraft version. Several targets can share a Java level (e.g. mc1205 /
 * mc12110 / mc12111 are all Java 21); publishing each would re-upload a
 * byte-identical jar to the same coordinate and 409 in the immutable releases
 * repo. So we pick one representative target per Java level — the default target
 * for its own level, otherwise the first target in file order — and publish the
 * full module set there (`publishDefaultMatrix` covers every category, plus the
 * Fabric matrix when that representative enables the fabric platform).
 *
 * The remaining targets still exist for the build/smoke matrix (each Minecraft
 * version boots a real server); they just don't publish.
 */
internal fun MagicUtilsMatrixDefinition.publishUnits(
    allTargets: List<String>,
    javaLevelOf: (String) -> Int,
): List<MagicUtilsPublishUnit> {
    val representatives = LinkedHashMap<Int, String>()
    for (target in allTargets) {
        val java = javaLevelOf(target)
        // Prefer the default target as its level's representative; otherwise the
        // first-seen target for that level wins (file order).
        if (java !in representatives || target == defaultTarget) {
            representatives[java] = target
        }
    }
    return representatives.values.map { target ->
        val tasks = mutableListOf("publishDefaultMatrix")
        if ("fabric" in availablePlatformsFor(target)) {
            tasks += "publishFabricMatrix"
        }
        // suffix is always false now: the +java<N> suffix is intrinsic to the
        // coordinate (publishedVersion), not a per-target CI toggle.
        MagicUtilsPublishUnit(target, tasks.distinct(), suffix = false)
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
