package dev.ua.theroer.magicutils.build.release

import dev.ua.theroer.magicutils.build.matrix.MagicUtilsFanoutInvocation
import dev.ua.theroer.magicutils.build.matrix.MagicUtilsMatrixDefinition
import dev.ua.theroer.magicutils.build.matrix.magicUtilsGradleWrapperName
import dev.ua.theroer.magicutils.build.matrix.publishUnits
import dev.ua.theroer.magicutils.build.matrix.registerMagicUtilsFanout
import dev.ua.theroer.magicutils.build.publish.MagicUtilsPublishingSpec
import dev.ua.theroer.magicutils.build.target.loadAllTargetNames
import dev.ua.theroer.magicutils.build.target.resolveMagicUtilsTargetSpec

import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import java.io.File

private const val RELEASE_GROUP = "release"

/**
 * Registers the local Maven release fan-out: `releaseMavenAll` publishes the full
 * module matrix into the configured Maven repo, one Gradle invocation per publish
 * unit (one representative target per Java level, from [publishUnits]), plus a
 * separate `publishBuildLogic` for the independently-versioned plugins. This is
 * the local, actions-free equivalent of the former publish-maven.yml.
 *
 * The publish repo URL comes from the publishing spec; credentials are read by
 * the module publish plugin from `PUBLISH_USER`/`PUBLISH_TOKEN` (env) or
 * `-Ppublish_user/password`. `-Prelease.dryRun=true` prints every command instead
 * of running it, so a release can be previewed without publishing anything.
 */
internal fun registerReleaseMavenTasks(
    project: Project,
    publishingSpec: MagicUtilsPublishingSpec,
    definition: MagicUtilsMatrixDefinition,
    targetsFile: File,
) {
    val dryRun = project.hasProperty("release.dryRun")
    val repoUrl = publishingSpec.repoUrl
    val units = resolvePublishUnits(definition, targetsFile)

    val perUnitTasks = registerMagicUtilsFanout(
        project = project,
        taskPrefix = "releaseMaven",
        taskGroup = RELEASE_GROUP,
        invocations = units.map { unit ->
            MagicUtilsFanoutInvocation(
                target = unit.target,
                // clean + --rerun-tasks so each fresh target invocation republishes
                // from scratch (the immutable releases repo rejects stale reuploads).
                args = buildList {
                    add("clean")
                    unit.publishTasks.forEach { add(it) }
                    add("--rerun-tasks")
                    add("-Ppublish_repo=$repoUrl")
                    add("-Pskip_shadow_publish=true")
                },
            )
        },
        childHomeSubdir = "release-maven-gradle-home",
        dryRun = dryRun,
        describe = { "Publish ${it.target} to $repoUrl (${it.args.filterNot { a -> a.startsWith("-") || a == "clean" }.joinToString(" ")})." },
    )

    // build-logic is a single, target-independent publish (no fan-out), mirroring
    // publish-maven.yml's publish-plugins job.
    val buildLogicTask = if (dryRun) {
        project.tasks.register("publishBuildLogic") { task ->
            task.group = RELEASE_GROUP
            task.description = "[dry-run] Publish the build-logic plugins to $repoUrl."
            task.doLast {
                task.logger.lifecycle("[dry-run] ./gradlew -p build-logic publish -Ppublish_repo=$repoUrl")
            }
        }
    } else {
        project.tasks.register("publishBuildLogic", Exec::class.java) { task ->
            task.group = RELEASE_GROUP
            task.description = "Publish the build-logic plugins to $repoUrl."
            task.workingDir = project.rootProject.projectDir
            task.commandLine(magicUtilsGradleWrapperName(), "-p", "build-logic", "publish", "-Ppublish_repo=$repoUrl")
        }
    }

    project.tasks.register("releaseMavenAll") { task ->
        task.group = RELEASE_GROUP
        task.description = "Publish the full module matrix + build-logic to $repoUrl " +
            "(one invocation per Java level; -Prelease.dryRun to preview)."
        task.dependsOn(perUnitTasks)
        task.dependsOn(buildLogicTask)
    }
}

/**
 * The publish units (one representative target per Java level, with the publish
 * tasks it runs). Shared by the Maven and Modrinth release fan-outs and matching
 * printPublishMatrix, so all three agree on which targets represent which Java
 * level. The `+java<N>` coordinate is byte-identical across targets of a level,
 * so one representative per level is enough.
 */
internal fun resolvePublishUnits(definition: MagicUtilsMatrixDefinition, targetsFile: File) =
    definition.publishUnits(loadAllTargetNames(targetsFile)) { target ->
        resolveMagicUtilsTargetSpec(
            targetsFile = targetsFile,
            defaultTarget = definition.defaultTarget,
            explicitTarget = target,
        ).java
    }

/**
 * Registers the local Modrinth release: `releaseModrinth` builds the platform
 * bundle jars for every Java level (one representative target per level, fanned
 * out) and then runs the existing `publishToModrinth` (matrix-driven, idempotent,
 * `MODRINTH_TOKEN`). The per-level bundle jars carry the `+java<N>` suffix in
 * their file names, so they accumulate side by side in each `<platform>-bundle/
 * build/libs` and publishToModrinth finds all of them. Actions-free equivalent of
 * a Modrinth publish job.
 */
internal fun registerReleaseModrinthTask(
    project: Project,
    definition: MagicUtilsMatrixDefinition,
    targetsFile: File,
) {
    val dryRun = project.hasProperty("release.dryRun")
    val units = resolvePublishUnits(definition, targetsFile)

    val bundleTasks = registerMagicUtilsFanout(
        project = project,
        taskPrefix = "releaseModrinthBundles",
        taskGroup = RELEASE_GROUP,
        invocations = units.map { unit ->
            // Build the full workspace scenario so every bundle platform available
            // on this representative target produces its +java<N> jar.
            MagicUtilsFanoutInvocation(unit.target, listOf("build", "-Pscenario=workspace"))
        },
        childHomeSubdir = "release-modrinth-gradle-home",
        dryRun = dryRun,
        describe = { "Build platform bundles for ${it.target} (Modrinth artifacts)." },
    )

    project.tasks.register("releaseModrinth") { task ->
        task.group = RELEASE_GROUP
        task.description = "Build the bundles for every Java level and publish them to Modrinth " +
            "(-Prelease.dryRun / -PmodrinthDryRun to preview)."
        task.dependsOn(bundleTasks)
        // publishToModrinth is matrix-driven and idempotent; run it after the jars
        // for all Java levels exist.
        task.dependsOn("publishToModrinth")
    }

    // Order publishToModrinth after every bundle build. Configured outside the
    // register block above: Gradle forbids configuring another task from within a
    // task-creation action.
    project.tasks.named("publishToModrinth").configure { publish ->
        bundleTasks.forEach { publish.mustRunAfter(it) }
    }
}
