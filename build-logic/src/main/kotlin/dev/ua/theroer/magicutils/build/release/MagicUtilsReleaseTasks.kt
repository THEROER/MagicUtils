package dev.ua.theroer.magicutils.build.release

import dev.ua.theroer.magicutils.build.publish.*
import dev.ua.theroer.magicutils.build.target.javaSuffixedCoordinate

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.inject.Inject

/**
 * Kotlin replacement for scripts/publish_release.py. The client-side release
 * orchestration now lives as Gradle tasks in the `release` group:
 *
 *   ./gradlew releasePreflight -Pversion=X.Y.Z   # validate only (no writes)
 *   ./gradlew bumpVersion      -Pversion=X.Y.Z   # edit gradle.properties + commit
 *   ./gradlew dispatchRelease  -Pversion=X.Y.Z   # gh workflow run release.yml
 *   ./gradlew smokeTest        -Pversion=X.Y.Z   # poll the published POM
 *   ./gradlew release          -Pversion=X.Y.Z   # preflight -> bump -> dispatch
 *
 * The server-side chain (tagging, docs/javadoc dispatch, gh-pages publish)
 * stays in .github/workflows/release.yml — these tasks are the client trigger.
 */

private const val RELEASE_GROUP = "release"

internal fun registerReleaseTasks(
    project: Project,
    publishingSpec: MagicUtilsPublishingSpec,
    defaultTargetJava: Int,
    modrinthProjectId: String?,
) {
    val gradlePropertiesFile = project.rootProject.file("gradle.properties")
    // Read the raw -Pversion from the start parameter, not project.version: the
    // target plugin overwrites project.version with the +<minecraft> suffix
    // (e.g. 1.23.0+1.21.10), which is not a plain semver a release can validate.
    val versionProvider = project.provider {
        project.gradle.startParameter.projectProperties["version"]
            ?: throw org.gradle.api.GradleException("Pass the release version via -Pversion=X.Y.Z.")
    }

    project.tasks.register("releasePreflight", ReleasePreflightTask::class.java) { task ->
        task.group = RELEASE_GROUP
        task.description = "Validate a release version against gradle.properties and existing tags (no changes)."
        task.requestedVersion.set(versionProvider)
        task.gradlePropertiesText.set(project.provider { gradlePropertiesFile.readText() })
        task.notCompatibleWithConfigurationCache("Queries git/gh for existing tags.")
    }

    project.tasks.register("bumpVersion", BumpVersionTask::class.java) { task ->
        task.group = RELEASE_GROUP
        task.description = "Bump gradle.properties to -Pversion and commit the change."
        task.requestedVersion.set(versionProvider)
        task.gradlePropertiesPath.set(gradlePropertiesFile.absolutePath)
        task.notCompatibleWithConfigurationCache("Writes gradle.properties and runs git commit.")
    }

    project.tasks.register("dispatchRelease", DispatchReleaseTask::class.java) { task ->
        task.group = RELEASE_GROUP
        task.description = "Dispatch release.yml via gh for -Pversion (optionally -Pref=<branch>)."
        task.requestedVersion.set(versionProvider)
        task.ref.set(project.provider { project.findProperty("ref") as? String })
        task.notCompatibleWithConfigurationCache("Runs gh workflow run.")
    }

    project.tasks.register("smokeTest", SmokeTestTask::class.java) { task ->
        task.group = RELEASE_GROUP
        task.description = "Poll the published POM for -Pversion until it appears (or times out)."
        // The library ships only per-Java coordinates (`X.Y.Z+java<N>`); a bare
        // X.Y.Z POM is never published. Poll the default target's Java level as
        // the canonical "Maven is up" signal.
        task.artifactUrl.set(project.provider {
            val base = SemanticVersion.parse(versionProvider.get()).toString()
            publishingSpec.smokeArtifactUrl(javaSuffixedCoordinate(base, defaultTargetJava))
        })
        task.notCompatibleWithConfigurationCache("Performs network polling.")
    }

    project.tasks.register("verifyReleaseConsistency", VerifyReleaseConsistencyTask::class.java) { task ->
        task.group = RELEASE_GROUP
        task.description = "Verify -Pversion is consistent across gradle.properties, git tag, Maven and Modrinth " +
            "(strict fail unless -Preport)."
        task.requestedVersion.set(versionProvider)
        task.gradlePropertiesText.set(project.provider { gradlePropertiesFile.readText() })
        // Same java-suffixed coordinate the smoke poll uses (the only one that is
        // actually published), so the two checks agree on what "Maven has it" means.
        task.mavenPomUrl.set(project.provider {
            val base = SemanticVersion.parse(versionProvider.get()).toString()
            publishingSpec.smokeArtifactUrl(javaSuffixedCoordinate(base, defaultTargetJava))
        })
        task.modrinthProjectId.set(project.provider { modrinthProjectId })
        task.reportOnly.set(project.provider { project.hasProperty("report") })
        task.notCompatibleWithConfigurationCache("Queries git and the network.")
    }

    project.tasks.register("releaseTag", ReleaseTagTask::class.java) { task ->
        task.group = RELEASE_GROUP
        task.description = "Create the vX.Y.Z git tag locally; push to origin only with -Prelease.push=true."
        task.requestedVersion.set(versionProvider)
        // Default off: a local tag until you opt into publishing it, matching the
        // release spec's push=false default. -Prelease.push=true (or release { push = true })
        // turns the push on.
        task.push.set(project.provider { project.findProperty("release.push")?.toString()?.toBoolean() ?: false })
        task.notCompatibleWithConfigurationCache("Runs git tag/push.")
    }

    project.tasks.register("release") { task ->
        task.group = RELEASE_GROUP
        task.description = "Full client release: preflight -> bump -> dispatch (-Pversion=X.Y.Z)."
        task.dependsOn("releasePreflight", "bumpVersion", "dispatchRelease")
        // Enforce ordering; Gradle runs dependencies in declared order when chained.
        project.tasks.named("bumpVersion").configure { it.mustRunAfter("releasePreflight") }
        project.tasks.named("dispatchRelease").configure { it.mustRunAfter("bumpVersion") }
    }
}

/** Runs a command, returning trimmed stdout; throws on non-zero exit. */
private fun ExecOperations.capture(vararg args: String): String {
    val out = ByteArrayOutputStream()
    exec { spec ->
        spec.commandLine(*args)
        spec.standardOutput = out
        spec.isIgnoreExitValue = false
    }
    return out.toString().trim()
}

abstract class ReleaseTagTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @get:Input abstract val requestedVersion: Property<String>
    @get:Input abstract val push: Property<Boolean>

    @TaskAction
    fun run() {
        val requested = SemanticVersion.parse(requestedVersion.get())
        val tag = "v$requested"

        val localTags = runCatching { execOps.capture("git", "tag", "--list", tag) }
            .getOrDefault("")
            .lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
        if (tag in localTags) {
            logger.lifecycle("Tag $tag already exists locally — not re-tagging.")
        } else {
            execOps.capture("git", "tag", tag)
            logger.lifecycle("Created tag $tag.")
        }

        if (push.get()) {
            execOps.capture("git", "push", "origin", tag)
            logger.lifecycle("Pushed $tag to origin.")
        } else {
            logger.lifecycle("Not pushing $tag (pass -Prelease.push=true to push to origin).")
        }
    }
}

abstract class ReleasePreflightTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @get:Input abstract val requestedVersion: Property<String>
    @get:Input abstract val gradlePropertiesText: Property<String>

    @TaskAction
    fun run() {
        val requested = SemanticVersion.parse(requestedVersion.get())
        val current = readGradleVersion(gradlePropertiesText.get())

        val localTags = runCatching { execOps.capture("git", "tag", "--list", "v*") }
            .getOrDefault("")
            .lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
        val released = localTags.mapNotNull(SemanticVersion::fromTag).maxOrNull()

        validateReleaseVersion(requested, current, released, localTags)
        logger.lifecycle("Preflight OK: $requested (current $current, latest released ${released ?: "none"}).")
    }
}

abstract class BumpVersionTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @get:Input abstract val requestedVersion: Property<String>
    @get:Input abstract val gradlePropertiesPath: Property<String>

    @TaskAction
    fun run() {
        val requested = SemanticVersion.parse(requestedVersion.get())
        val file = java.io.File(gradlePropertiesPath.get())
        val current = readGradleVersion(file.readText())
        if (requested == current) {
            logger.lifecycle("gradle.properties already at $requested — nothing to bump.")
            return
        }
        file.writeText(bumpGradleVersion(file.readText(), requested))
        execOps.capture("git", "add", file.absolutePath)
        execOps.capture("git", "commit", "-m", "chore(release): bump version to $requested")
        logger.lifecycle("Bumped gradle.properties $current -> $requested and committed.")
    }
}

abstract class DispatchReleaseTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @get:Input abstract val requestedVersion: Property<String>
    @get:[Input Optional] abstract val ref: Property<String>

    @TaskAction
    fun run() {
        val requested = SemanticVersion.parse(requestedVersion.get())
        val args = mutableListOf("gh", "workflow", "run", "release.yml", "-f", "version=$requested")
        ref.orNull?.let { args += listOf("--ref", it) }
        execOps.capture(*args.toTypedArray())
        logger.lifecycle("Dispatched release.yml for $requested${ref.orNull?.let { " on $it" } ?: ""}.")
    }
}

abstract class SmokeTestTask : DefaultTask() {
    @get:Input abstract val artifactUrl: Property<String>

    @TaskAction
    fun run() {
        val url = artifactUrl.get()
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        val request = HttpRequest.newBuilder(URI.create(url))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(10))
            .build()

        val deadline = System.currentTimeMillis() + Duration.ofMinutes(20).toMillis()
        var attempt = 0
        logger.lifecycle("Smoke test: polling $url")
        while (System.currentTimeMillis() < deadline) {
            attempt++
            val status = runCatching {
                client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
            }.getOrNull()
            if (status == 200) {
                logger.lifecycle("  attempt $attempt: 200 OK — artifact available.")
                return
            }
            logger.lifecycle("  attempt $attempt: ${status ?: "no response"}")
            Thread.sleep(Duration.ofSeconds(30).toMillis())
        }
        throw org.gradle.api.GradleException(
            "Smoke test timed out. GitHub Pages CDN can lag — verify gh-pages directly."
        )
    }
}

abstract class VerifyReleaseConsistencyTask @Inject constructor(
    private val execOps: ExecOperations,
) : DefaultTask() {
    @get:Input abstract val requestedVersion: Property<String>
    @get:Input abstract val gradlePropertiesText: Property<String>
    @get:Input abstract val mavenPomUrl: Property<String>
    @get:[Input Optional] abstract val modrinthProjectId: Property<String>
    @get:Input abstract val reportOnly: Property<Boolean>

    @TaskAction
    fun run() {
        val requested = SemanticVersion.parse(requestedVersion.get())
        val gradleVersion = readGradleVersion(gradlePropertiesText.get())

        val tags = runCatching { execOps.capture("git", "tag", "--list", "v*") }
            .getOrDefault("")
            .lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
        val tagExists = "v$requested" in tags

        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
        val mavenPublished = headOk(client, mavenPomUrl.get())
        val modrinthPublished = modrinthVersionPresent(client, modrinthProjectId.orNull, requested.toString())

        val report = evaluateReleaseConsistency(
            version = requested,
            gradlePropertiesVersion = gradleVersion,
            tagExists = tagExists,
            mavenPublished = mavenPublished,
            modrinthPublished = modrinthPublished,
        )

        logger.lifecycle("Release consistency for $requested:")
        for (status in report.statuses) {
            val mark = when (status.state) {
                SourceState.PRESENT -> "OK  "
                SourceState.ABSENT -> "FAIL"
                SourceState.SKIPPED -> "SKIP"
            }
            logger.lifecycle("  [$mark] ${status.source}: ${status.detail}")
        }

        if (report.consistent) {
            logger.lifecycle("All required sources agree.")
            return
        }
        val summary = "Release $requested is inconsistent:\n  - " + report.problems.joinToString("\n  - ")
        if (reportOnly.get()) {
            logger.warn(summary)
        } else {
            throw org.gradle.api.GradleException("$summary\n(Run with -Preport to print without failing.)")
        }
    }

    /** True if a HEAD on [url] returns 200 (artifact present), false otherwise. */
    private fun headOk(client: HttpClient, url: String): Boolean {
        val request = HttpRequest.newBuilder(URI.create(url))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(15))
            .build()
        return runCatching {
            client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200
        }.getOrDefault(false)
    }

    /**
     * Whether Modrinth already has [versionNumber] for [projectId]. Returns null
     * (check skipped, a warning not a failure) when no project is configured or
     * the request fails — Modrinth is published manually and may legitimately lag.
     * A public project's version list needs no token; MODRINTH_TOKEN is sent when
     * present so private/draft projects also resolve.
     */
    private fun modrinthVersionPresent(client: HttpClient, projectId: String?, versionNumber: String): Boolean? {
        if (projectId.isNullOrBlank()) return null
        val builder = HttpRequest.newBuilder(
            URI.create("https://api.modrinth.com/v3/project/$projectId/version?include_changelog=false")
        ).timeout(Duration.ofSeconds(20)).GET()
        System.getenv("MODRINTH_TOKEN")?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", it) }
        return runCatching {
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) return null
            versionNumber in parseModrinthVersionIds(response.body()).keys
        }.getOrNull()
    }
}
