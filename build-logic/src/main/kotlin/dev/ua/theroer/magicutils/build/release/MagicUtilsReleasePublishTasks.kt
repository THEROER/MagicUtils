package dev.ua.theroer.magicutils.build.release

import dev.ua.theroer.magicutils.build.matrix.MagicUtilsFanoutInvocation
import dev.ua.theroer.magicutils.build.matrix.MagicUtilsMatrixDefinition
import dev.ua.theroer.magicutils.build.matrix.magicUtilsGradleWrapperName
import dev.ua.theroer.magicutils.build.matrix.publishUnits
import dev.ua.theroer.magicutils.build.matrix.registerMagicUtilsFanout
import dev.ua.theroer.magicutils.build.publish.MagicUtilsPublishingSpec
import dev.ua.theroer.magicutils.build.support.findMagicUtilsPublishSecret
import dev.ua.theroer.magicutils.build.target.loadAllTargetNames
import dev.ua.theroer.magicutils.build.target.resolveMagicUtilsTargetSpec

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.Base64

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
    // Each child runs with an isolated Gradle home and cannot read the caller's
    // ~/.gradle/gradle.properties, so the publish creds the parent resolved are
    // forwarded as env (the child's publish helper reads PUBLISH_USER/PUBLISH_TOKEN).
    val publishEnv = magicUtilsPublishEnv(project)

    val perUnitTasks = registerMagicUtilsFanout(
        project = project,
        taskPrefix = "releaseMaven",
        taskGroup = RELEASE_GROUP,
        invocations = units.map { unit ->
            MagicUtilsFanoutInvocation(
                target = unit.target,
                // clean + --rerun-tasks so each fresh target invocation republishes
                // from scratch. skip_existing keeps it resumable: a coordinate a
                // prior (failed) run already uploaded is skipped instead of 409ing
                // against the immutable releases repo.
                args = buildList {
                    add("clean")
                    unit.publishTasks.forEach { add(it) }
                    add("--rerun-tasks")
                    add("-Ppublish_repo=$repoUrl")
                    add("-Pskip_shadow_publish=true")
                    add("-Pskip_existing")
                },
                env = publishEnv,
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
            task.commandLine(magicUtilsGradleWrapperName(), "-p", "build-logic", "publish", "-Ppublish_repo=$repoUrl", "-Pskip_existing")
            publishEnv.forEach { (key, value) -> task.environment(key, value) }
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
 * Publish secrets the parent resolved (property-or-env), as an env map to forward
 * to child fan-out invocations. The children run with an isolated
 * `--gradle-user-home` and cannot read ~/.gradle/gradle.properties, so without
 * this their publish would 401. Empty entries are omitted so we never set a blank
 * env var. Passed as env (not `-P`) so secrets never appear in `ps`.
 */
private fun magicUtilsPublishEnv(project: Project): Map<String, String> = buildMap {
    project.findMagicUtilsPublishSecret("publish_user", "PUBLISH_USER")?.let { put("PUBLISH_USER", it) }
    project.findMagicUtilsPublishSecret("publish_password", "PUBLISH_TOKEN")?.let { put("PUBLISH_TOKEN", it) }
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

/**
 * Registers `releaseJavadoc`: generate the aggregated Javadoc zip (via the
 * existing `aggregatedJavadocZip`) and upload it to the publish repo at the
 * stable `latest` path plus a versioned copy. Actions-free equivalent of
 * publish-javadoc.yml. `-Prelease.dryRun=true` prints the target URLs instead of
 * uploading.
 */
internal fun registerReleaseJavadocTask(
    project: Project,
    publishingSpec: MagicUtilsPublishingSpec,
) {
    val dryRun = project.hasProperty("release.dryRun")
    project.tasks.register("releaseJavadoc", UploadJavadocTask::class.java) { task ->
        task.group = RELEASE_GROUP
        task.description = "Generate and upload the aggregated Javadoc to ${publishingSpec.repoUrl} " +
            "(-Prelease.dryRun to preview)."
        task.dependsOn("aggregatedJavadocZip")
        task.zipPath.set(project.layout.buildDirectory.file("docs/$JAVADOC_ZIP_NAME").map { it.asFile.toPath() })
        task.latestUrl.set(javadocLatestUrl(publishingSpec.repoUrl, publishingSpec.group))
        task.versionUrl.set(project.provider {
            // The raw -Pversion, not project.version: the target plugin overwrites
            // project.version with the +java<N> suffix, which is not a release version.
            project.gradle.startParameter.projectProperties["version"]?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { javadocVersionUrl(publishingSpec.repoUrl, publishingSpec.group, it) }
        })
        task.username.set(project.provider { project.findMagicUtilsPublishSecret("publish_user", "PUBLISH_USER") })
        task.password.set(project.provider { project.findMagicUtilsPublishSecret("publish_password", "PUBLISH_TOKEN") })
        task.dryRun.set(dryRun)
        task.notCompatibleWithConfigurationCache("Performs a network upload.")
    }
}

/** The aggregated Javadoc zip file name produced by aggregatedJavadocZip. */
private const val JAVADOC_ZIP_NAME = "magicutils-javadoc.zip"

abstract class UploadJavadocTask : DefaultTask() {
    @get:Input abstract val zipPath: Property<Path>
    @get:Input abstract val latestUrl: Property<String>
    @get:[Input Optional] abstract val versionUrl: Property<String>
    @get:[Input Optional] abstract val username: Property<String>
    @get:[Input Optional] abstract val password: Property<String>
    @get:Input abstract val dryRun: Property<Boolean>

    @TaskAction
    fun run() {
        val zip = zipPath.get().toFile()
        val targets = listOfNotNull(latestUrl.get(), versionUrl.orNull)

        if (dryRun.get()) {
            logger.lifecycle("[dry-run] would upload ${zip.name} to:")
            targets.forEach { logger.lifecycle("  $it") }
            logger.lifecycle("  credentials: ${if (username.orNull != null && password.orNull != null) "present" else "absent"}")
            return
        }

        if (!zip.isFile) throw GradleException("Javadoc zip not found at ${zip.absolutePath} (did aggregatedJavadocZip run?).")
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
        val authHeader = username.orNull?.let { user ->
            password.orNull?.let { pass ->
                "Basic " + Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
            }
        }
        for (url in targets) {
            val builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .PUT(HttpRequest.BodyPublishers.ofFile(zip.toPath()))
            authHeader?.let { builder.header("Authorization", it) }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw GradleException("Javadoc upload to $url failed (HTTP ${response.statusCode()}): ${response.body()}")
            }
            logger.lifecycle("Uploaded ${zip.name} -> $url")
        }
    }
}
