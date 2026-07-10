package dev.ua.theroer.magicutils.build.release

/**
 * Which steps the `release` orchestrator runs, and how. Every step is optional so
 * a consumer builds exactly the release pipeline they want (a plugin with no
 * Modrinth page turns `publishModrinth` off; a fork that tags out-of-band turns
 * `tag` off). Defaults describe a full library release: validate, bump, tag
 * locally (no push), publish Maven + Modrinth + Javadoc, verify afterwards.
 *
 * Declared in `magicMatrix { release { ... } }` and overridable per invocation
 * with `-Prelease.<step>=true|false` (see [applyReleaseOverrides]). Pure data so
 * the plan is unit-testable without a Gradle runtime.
 */
data class MagicUtilsReleaseSpec(
    /** Validate the requested version against gradle.properties + tags (releasePreflight). */
    val validateVersion: Boolean = true,
    /** Run the build/tests gate before publishing (heavy; disable with -Prelease.validate=false). */
    val validateBuild: Boolean = true,
    /** Bump gradle.properties to the requested version and commit it. */
    val bump: Boolean = true,
    /** Create the vX.Y.Z git tag. */
    val tag: Boolean = true,
    /** Push the tag to origin (off by default: local tag only until you opt in). */
    val push: Boolean = false,
    /** Publish the full module matrix + build-logic to the Maven repo. */
    val publishMaven: Boolean = true,
    /** Build the bundles and publish them to Modrinth. */
    val publishModrinth: Boolean = true,
    /** Generate and upload the aggregated Javadoc. */
    val publishJavadoc: Boolean = true,
    /** Verify version consistency across all sources after publishing. */
    val verify: Boolean = true,
    /**
     * Branch a release is allowed to run from (checked in releasePreflight).
     * Publishing off a feature branch means the tag and the immutable Maven
     * coordinate point at a commit that may never land on the release branch as
     * written, so the release history diverges from it. Defaults to `main`; set
     * to `null` to disable the gate (a fork with a different default branch, or
     * one that intentionally releases from anywhere). Overridable per invocation
     * with `-Prelease.branch=<name>`, and bypassable once with
     * `-Prelease.allowAnyBranch=true` for a genuine off-branch hotfix.
     */
    val releaseBranch: String? = "main",
)

/**
 * Validates that a release may run from [currentBranch].
 *
 * Returns null when allowed, or a human-readable reason when blocked. Pure so the
 * gate is unit-testable without a git checkout. The gate is a no-op when
 * [requiredBranch] is null (disabled), when [allowAnyBranch] is set (explicit
 * bypass), or when the current branch cannot be determined (detached HEAD / no
 * git) — in those two last cases the caller logs a warning rather than guessing.
 */
fun releaseBranchViolation(
    requiredBranch: String?,
    currentBranch: String?,
    allowAnyBranch: Boolean,
): String? {
    if (requiredBranch == null || allowAnyBranch) return null
    if (currentBranch.isNullOrBlank()) return null
    if (currentBranch == requiredBranch) return null
    return "Release must run from branch '$requiredBranch' but the current branch is " +
        "'$currentBranch'. Merge to '$requiredBranch' first, or pass " +
        "-Prelease.allowAnyBranch=true to override (e.g. an off-branch hotfix)."
}

/** A step of the release, in run order, with whether the spec enabled it. */
data class MagicUtilsReleaseStep(val name: String, val enabled: Boolean)

/**
 * The ordered release plan for [spec]. Order is fixed and meaningful: validate
 * and build gate before any mutation; bump/tag before publishing so the tag
 * exists when Maven/Modrinth go out; verify last. Callers wire the aggregate
 * task's dependsOn/mustRunAfter from this list, and print it so a dry run shows
 * exactly what a real run would do.
 */
fun releasePlan(spec: MagicUtilsReleaseSpec): List<MagicUtilsReleaseStep> = listOf(
    MagicUtilsReleaseStep("releasePreflight", spec.validateVersion),
    MagicUtilsReleaseStep("releaseValidateBuild", spec.validateBuild),
    MagicUtilsReleaseStep("bumpVersion", spec.bump),
    MagicUtilsReleaseStep("releaseTag", spec.tag),
    MagicUtilsReleaseStep("releaseMavenAll", spec.publishMaven),
    MagicUtilsReleaseStep("releaseModrinth", spec.publishModrinth),
    MagicUtilsReleaseStep("releaseJavadoc", spec.publishJavadoc),
    MagicUtilsReleaseStep("verifyReleaseConsistency", spec.verify),
)

/**
 * Applies `-Prelease.<step>=true|false` overrides on top of the DSL [spec]. A
 * property whose value isn't a clean boolean is ignored (the DSL default stands)
 * rather than failing the build, so a typo never blocks a release. [properties]
 * is the raw Gradle project-properties map (property name -> string value).
 */
fun applyReleaseOverrides(
    spec: MagicUtilsReleaseSpec,
    properties: Map<String, String>,
): MagicUtilsReleaseSpec {
    fun override(key: String, current: Boolean): Boolean =
        properties["release.$key"]?.trim()?.lowercase()?.let {
            when (it) {
                "true" -> true
                "false" -> false
                else -> current // ignore garbage, keep the DSL value
            }
        } ?: current

    // -Prelease.branch=<name> overrides the release branch; passing it empty
    // (`-Prelease.branch=`) disables the gate (parity with a null DSL default).
    // Absent property keeps the DSL value; present-but-empty means null.
    val branch = if (properties.containsKey("release.branch")) {
        properties["release.branch"]?.trim()?.ifEmpty { null }
    } else {
        spec.releaseBranch
    }

    return spec.copy(
        validateVersion = override("validateVersion", spec.validateVersion),
        validateBuild = override("validate", spec.validateBuild),
        bump = override("bump", spec.bump),
        tag = override("tag", spec.tag),
        push = override("push", spec.push),
        publishMaven = override("maven", spec.publishMaven),
        publishModrinth = override("modrinth", spec.publishModrinth),
        publishJavadoc = override("javadoc", spec.publishJavadoc),
        verify = override("verify", spec.verify),
        releaseBranch = branch,
    )
}
