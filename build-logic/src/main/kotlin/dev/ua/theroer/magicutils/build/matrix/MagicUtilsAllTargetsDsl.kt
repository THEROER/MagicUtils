package dev.ua.theroer.magicutils.build.matrix

import dev.ua.theroer.magicutils.build.target.*

/**
 * Consumer-facing DSL for the "build everything, for every version" fan-out, e.g.:
 *
 *     magicMatrix {
 *         allTargets {
 *             // targets(...)            // default: every target in targets.properties
 *             // targets("mc12110", "mc262")
 *             scenario = "workspace"     // default: matrix default scenario
 *             taskType = "build"         // build | check | publishToMavenLocal
 *         }
 *     }
 *
 * Every axis is optional; a bare `allTargets { }` (or omitting the block entirely)
 * means "run `build` over the default scenario for every declared target". The
 * task itself (`buildAllTargets`) is always registered — the block only shapes it.
 */
open class MagicUtilsAllTargetsDsl {
    private val explicitTargets = linkedSetOf<String>()

    /** Scenario whose platforms each per-target run builds; null = matrix default. */
    var scenario: String? = null

    /** Per-target task: "build" (default), "check", or "publishToMavenLocal". */
    var taskType: String = "build"

    /** Restrict the fan-out to these targets (normalized to `mcXXXX`). */
    fun targets(vararg names: String) {
        names.forEach { explicitTargets += normalizeTargetName(it) }
    }

    /** Restrict the fan-out to these targets (normalized to `mcXXXX`). */
    fun targets(names: Iterable<String>) {
        names.forEach { explicitTargets += normalizeTargetName(it) }
    }

    internal fun toSpec(): MagicUtilsAllTargetsSpec = MagicUtilsAllTargetsSpec(
        targets = explicitTargets.toList(),
        scenario = scenario?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
        taskType = MagicUtilsAllTargetsTaskType.fromToken(taskType),
    )
}
