package dev.ua.theroer.magicutils.build.matrix

import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.GradleException
import java.io.File

/**
 * A single Gradle task run over every declared target — the wrapper the
 * `buildAllTargets` task fans out into. One Gradle build graph can only cover
 * one Minecraft target (the target is pinned in `settings.gradle`), so building
 * "everything for every advertised version" is inherently N Gradle invocations:
 * one per target from `targets.properties`, filtered and shaped by this spec.
 *
 * Configured by the consumer via `magicMatrix { allTargets { ... } }`; every axis
 * has a sensible default so a bare `allTargets { }` (or no block at all) means
 * "run `build`, over the `workspace` scenario, for every target in the file".
 */
data class MagicUtilsAllTargetsSpec(
    /**
     * Explicit target subset (normalized `mcXXXX` names). Empty means every
     * target declared in `targets.properties`, in file order.
     */
    val targets: List<String>,
    /**
     * Scenario whose platforms each per-target run builds. Null falls back to
     * the matrix default (`workspace` when present), the same as a bare build.
     */
    val scenario: String?,
    /** The per-target task to run: `build` (default), `check`, `publishToMavenLocal`. */
    val taskType: MagicUtilsAllTargetsTaskType,
)

enum class MagicUtilsAllTargetsTaskType(val gradleTask: String) {
    BUILD("build"),
    CHECK("check"),
    PUBLISH_TO_MAVEN_LOCAL("publishToMavenLocal");

    companion object {
        fun fromToken(raw: String): MagicUtilsAllTargetsTaskType {
            val normalized = raw.trim().lowercase().replace("-", "").replace("_", "")
            return when (normalized) {
                "build" -> BUILD
                "check" -> CHECK
                "publish", "publishtomavenlocal", "maven", "mavenlocal" -> PUBLISH_TO_MAVEN_LOCAL
                else -> throw GradleException(
                    "Unknown allTargets task type '$raw'. Use one of: build, check, publishToMavenLocal."
                )
            }
        }
    }
}

/**
 * Resolves the concrete, ordered list of targets this spec should fan out over.
 * Validates that any explicit subset actually exists in [targetsFile], so a typo
 * fails fast at configuration time rather than silently building nothing.
 */
internal fun MagicUtilsAllTargetsSpec.resolveTargets(targetsFile: File): List<String> {
    val declared = loadAllTargetNames(targetsFile)
    if (targets.isEmpty()) {
        return declared
    }
    val declaredSet = declared.toSet()
    val unknown = targets.filter { it !in declaredSet }
    if (unknown.isNotEmpty()) {
        throw GradleException(
            "allTargets requested unknown targets: ${unknown.joinToString(", ")}. " +
                "Declared targets: ${declared.joinToString(", ")}."
        )
    }
    // Preserve the consumer's requested order, deduplicated.
    return targets.distinct()
}
