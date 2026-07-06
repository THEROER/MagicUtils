package dev.ua.theroer.magicutils.build.release

import dev.ua.theroer.magicutils.build.publish.*

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

internal fun registerReleaseTasks(project: Project, publishingSpec: MagicUtilsPublishingSpec) {
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
        task.artifactUrl.set(project.provider { publishingSpec.smokeArtifactUrl(SemanticVersion.parse(versionProvider.get())) })
        task.notCompatibleWithConfigurationCache("Performs network polling.")
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
