package dev.ua.theroer.magicutils.build.release

import dev.ua.theroer.magicutils.build.support.findMagicUtilsModrinthToken
import dev.ua.theroer.magicutils.build.smoke.SmokePlatformSpec
import dev.ua.theroer.magicutils.build.smoke.expandVersionsFull
import dev.ua.theroer.magicutils.build.target.javaSuffixedCoordinate
import dev.ua.theroer.magicutils.build.target.resolveMagicUtilsTargetSpec

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Kotlin replacement for the reusable part of verified-plugin's
 * `publish_to_modrinth.sh`: uploads one Modrinth version per declared artifact
 * (idempotent — skips a version_number that already exists). The token comes
 * from the `modrinth_token` Gradle property or the MODRINTH_TOKEN env var, the
 * same property-or-env resolution as the Maven publish secrets.
 */
// v3 for native per-version `environment`; body shape is otherwise the same as v2.
private const val MODRINTH_API = "https://api.modrinth.com/v3"

internal fun registerModrinthTasks(
    project: Project,
    spec: ModrinthReleaseSpec?,
    smokeSpecs: List<SmokePlatformSpec>,
    defaultTarget: String,
    targetsFile: File,
) {
    project.tasks.register("publishToModrinth", ModrinthPublishTask::class.java) { task ->
        task.group = "publishing"
        task.description = "Upload artifacts to Modrinth (-Pversion=X.Y.Z; modrinth_token property or MODRINTH_TOKEN env). " +
            "Artifacts come from the smoke matrix unless the modrinth {} block lists them."
        // When the modrinth {} block declares no artifacts, synthesise them from
        // the smoke matrix — the single source of truth (same rows printReleaseMatrix
        // emits). This keeps the Modrinth release and the smoke gate in lockstep and
        // avoids a hand-maintained artifact list. When it declares no changelog,
        // generate one from git conventional-commit history (grouped feat/fix/…),
        // overridable with `-PmodrinthChangelogFile=path` or `-PmodrinthPreviousTag=`
        // to set the diff base.
        val resolvedSpec = spec?.let { base ->
            val withArtifacts = if (base.artifacts.isNotEmpty()) base
            else base.copy(
                artifacts = modrinthArtifactsFromMatrix(smokeSpecs, defaultTarget, targetsFile, base.baseVersionOrNull(project)),
            )
            if (withArtifacts.changelog.isNotBlank()) withArtifacts
            else withArtifacts.copy(changelog = resolveChangelog(project, base.baseVersionOrNull(project) ?: "dev"))
        }
        task.releaseSpec.set(resolvedSpec)
        task.baseVersion.set(project.provider {
            (project.findProperty("version") as? String)
                ?: throw GradleException("Pass the release version via -Pversion=X.Y.Z.")
        })
        task.rootDir.set(project.rootDir.absolutePath)
        // Resolved in the configuration phase (property-or-env), so the token
        // source is uniform with the Maven secrets. Optional: a dry run needs none.
        task.token.set(project.provider { project.findMagicUtilsModrinthToken() })
        task.notCompatibleWithConfigurationCache("Performs network uploads.")
    }
}

/**
 * The changelog for the release: an explicit `-PmodrinthChangelogFile=path` wins,
 * otherwise it is generated from git history (diffing from `-PmodrinthPreviousTag`
 * when given, else the latest commits). Configuration-time so it ships with the
 * spec; git is read from the project root.
 */
private fun resolveChangelog(project: Project, version: String): String {
    (project.findProperty("modrinthChangelogFile") as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
        val file = File(path).let { if (it.isAbsolute) it else File(project.rootDir, path) }
        if (file.isFile) return file.readText().trim()
    }
    val previousTag = (project.findProperty("modrinthPreviousTag") as? String)?.trim()?.takeIf { it.isNotEmpty() }
    return magicUtilsGenerateChangelog(project, version, previousTag)
}

/**
 * The bare release version for jar names, without the `+<minecraft>` suffix that
 * build-logic appends to `project.version` (so `1.23.0+1.21.10` → `1.23.0`). The
 * bundle jar is named `<base>+<target-minecraft>`, so we must not double the mc.
 */
private fun ModrinthReleaseSpec.baseVersionOrNull(project: Project): String? =
    (project.findProperty("version") as? String)?.trim()
        ?.substringBefore('+')
        ?.takeIf { it.isNotEmpty() }

/**
 * Builds one [ModrinthArtifact] per smoke sub-range, resolving each entry's
 * target to its runtime Minecraft (for the classifier-less bundle jar name) and
 * expanding its `versions` to the full Modrinth game-version list — the exact
 * rows `printReleaseMatrix` produces, reused here so the publish is matrix-driven.
 */
/**
 * Smoke platforms that are NOT published as their own Modrinth artifact: `folia`
 * is a bukkit-bundle Modrinth *loader*, exercised via its own smoke run against a
 * Folia server, but it ships no `folia-bundle` jar of its own.
 */
private val MODRINTH_NON_PUBLISHED_PLATFORMS = setOf("folia")

private fun modrinthArtifactsFromMatrix(
    smokeSpecs: List<SmokePlatformSpec>,
    defaultTarget: String,
    targetsFile: File,
    version: String?,
): List<ModrinthArtifact> = smokeSpecs
    .filter { it.name !in MODRINTH_NON_PUBLISHED_PLATFORMS }
    .flatMap { platform ->
    // Group smoke entries by Java level: every Minecraft version sharing a Java
    // level ships the same bundle jar (the coordinate is `+java<N>`), so they
    // fold into one Modrinth version whose game_versions is their union —
    // otherwise we'd upload the same file once per Minecraft version.
    val ver = version ?: "{version}"
    platform.versionMatrix
        .groupBy { entry ->
            resolveMagicUtilsTargetSpec(
                targetsFile = targetsFile,
                defaultTarget = defaultTarget,
                explicitTarget = entry.target ?: defaultTarget,
            ).java
        }
        .map { (java, entries) ->
            // The bundle jar is named by the Java level (the published coordinate
            // is `<base>+java<N>`), so all Minecraft versions sharing a Java level
            // map to one jar. Merge their advertised game versions into ONE
            // Modrinth version instead of re-uploading the same file per MC.
            val fileName = "magicutils-${platform.name}-bundle-${javaSuffixedCoordinate(ver, java)}.jar"
            val gameVersions = entries.flatMap { it.versions.expandVersionsFull() }.distinct()
            // Stable, valid Modrinth file part, unique per jar.
            val key = "${platform.name}-java$java"
            ModrinthArtifact(
                key = key,
                file = "${platform.name}-bundle/build/libs/$fileName",
                loaders = emptyList(), // derived from platform
                gameVersions = gameVersions,
                platform = platform.name,
            )
        }
}

abstract class ModrinthPublishTask : DefaultTask() {
    // Not a Gradle @Input: the spec isn't a stable up-to-date key (this task
    // always performs a network upload) and may legitimately be null so the
    // @TaskAction can raise a readable "No Modrinth release declared" error
    // instead of Gradle's opaque "property doesn't have a configured value".
    @get:Internal abstract val releaseSpec: Property<ModrinthReleaseSpec>
    @get:Input abstract val baseVersion: Property<String>
    @get:Input abstract val rootDir: Property<String>

    // @Internal, not @Input: a secret must never enter the up-to-date hash or the
    // build cache. Optional so a dry run (which returns before needing it) works
    // without a token configured.
    @get:Internal abstract val token: Property<String>

    @TaskAction
    fun run() {
        val spec = releaseSpec.orNull
            ?: throw GradleException(
                "No Modrinth release declared. Configure magicMatrix { modrinth { projectId = ...; artifact(...) } }."
            )
        if (spec.projectId.isBlank()) throw GradleException("Modrinth projectId is not set.")
        if (spec.artifacts.isEmpty()) throw GradleException("Modrinth release declares no artifacts.")

        // Strip the `+<minecraft>` suffix build-logic appends to project.version,
        // so the Modrinth version_number is the bare release version.
        val version = baseVersion.get().substringBefore('+')

        // -PmodrinthDryRun prints the resolved upload plan (version_number, jar,
        // loaders, game_versions) and stops — no token, no network. Handy to eyeball
        // the matrix-driven artifacts before a real publish.
        if ((project.findProperty("modrinthDryRun") as? String)?.toBoolean() == true) {
            logger.lifecycle("Modrinth dry run — project '${spec.projectId}', channel '${spec.channel}', ${spec.artifacts.size} artifact(s):")
            for (artifact in spec.artifacts) {
                logger.lifecycle("  ${spec.versionNumber(version, artifact)}")
                logger.lifecycle("    file:    ${artifact.file}")
                logger.lifecycle("    loaders: ${artifact.resolvedLoaders().joinToString(", ")}")
                logger.lifecycle("    mc:      ${artifact.gameVersions.joinToString(", ")}")
            }
            if (spec.changelog.isNotBlank()) {
                logger.lifecycle("\nChangelog:\n${spec.changelog}")
            }
            return
        }

        val token = token.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("Modrinth token is not set (modrinth_token property or MODRINTH_TOKEN env).")

        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
        val existing = fetchExistingVersions(client, token, spec.projectId)
        val forceRepublish = (project.findProperty("modrinthForceRepublish") as? String)?.toBoolean() == true

        for (artifact in spec.artifacts) {
            val versionNumber = spec.versionNumber(version, artifact)
            val existingId = existing[versionNumber]
            if (existingId != null) {
                if (!forceRepublish) {
                    logger.lifecycle("Modrinth version '$versionNumber' already exists — skipping.")
                    continue
                }
                deleteVersion(client, token, existingId)
                logger.lifecycle("Deleted existing Modrinth version '$versionNumber' ($existingId) for republish.")
            }
            val jar = resolveFile(artifact.file)
            uploadVersion(client, token, spec, artifact, versionNumber, jar)
            logger.lifecycle("Uploaded Modrinth version '$versionNumber' (${jar.name}).")
        }
    }

    private fun deleteVersion(client: HttpClient, token: String, versionId: String) {
        val request = HttpRequest.newBuilder(URI.create("$MODRINTH_API/version/$versionId"))
            .header("Authorization", token)
            .timeout(Duration.ofSeconds(30))
            .DELETE()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw GradleException("Failed to delete Modrinth version $versionId (HTTP ${response.statusCode()}): ${response.body()}")
        }
    }

    private fun resolveFile(path: String): File {
        val file = File(path).let { if (it.isAbsolute) it else File(rootDir.get(), path) }
        if (!file.isFile) throw GradleException("Modrinth artifact file not found: ${file.absolutePath}")
        return file
    }

    /** Map of existing version_number → version id for the project. */
    private fun fetchExistingVersions(client: HttpClient, token: String, projectId: String): Map<String, String> {
        val request = HttpRequest.newBuilder(URI.create("$MODRINTH_API/project/$projectId/version?include_changelog=false"))
            .header("Authorization", token)
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw GradleException("Failed to list Modrinth versions (HTTP ${response.statusCode()}): ${response.body()}")
        }
        return parseModrinthVersionIds(response.body())
    }

    private fun uploadVersion(
        client: HttpClient,
        token: String,
        spec: ModrinthReleaseSpec,
        artifact: ModrinthArtifact,
        versionNumber: String,
        jar: File,
    ) {
        val data = buildVersionJson(spec, artifact, versionNumber, jar.name)
        val boundary = "----MagicUtilsBoundary${System.nanoTime()}"
        val body = multipartBody(boundary, data, artifact.key, jar)
        val request = HttpRequest.newBuilder(URI.create("$MODRINTH_API/version"))
            .header("Authorization", token)
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .timeout(Duration.ofMinutes(5))
            .POST(HttpRequest.BodyPublishers.ofByteArrays(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw GradleException(
                "Modrinth upload of '$versionNumber' failed (HTTP ${response.statusCode()}): ${response.body()}"
            )
        }
    }

    private fun buildVersionJson(
        spec: ModrinthReleaseSpec,
        artifact: ModrinthArtifact,
        versionNumber: String,
        fileName: String,
    ): String {
        fun arr(values: List<String>) = values.joinToString(",", "[", "]") { "\"${jsonEscape(it)}\"" }
        return buildString {
            append("{")
            append("\"name\":\"${jsonEscape(versionNumber)}\",")
            append("\"version_number\":\"${jsonEscape(versionNumber)}\",")
            // Changelog (Markdown) shipped with every version; omitted when blank.
            if (spec.changelog.isNotBlank()) {
                append("\"changelog\":\"${jsonEscape(spec.changelog)}\",")
            }
            append("\"version_type\":\"${modrinthVersionType(spec.channel)}\",")
            append("\"loaders\":${arr(artifact.resolvedLoaders())},")
            append("\"game_versions\":${arr(artifact.gameVersions)},")
            // `environment` is a v3 loader-field only defined for mod loaders
            // (fabric/neoforge); plugin loaders (bukkit/velocity/bungee) reject it.
            modrinthEnvironmentForPlatform(artifact.platform)?.let {
                append("\"environment\":\"$it\",")
            }
            append("\"featured\":${spec.featured},")
            append("\"status\":\"listed\",")
            append("\"dependencies\":[],")
            append("\"project_id\":\"${jsonEscape(spec.projectId)}\",")
            append("\"file_parts\":[\"${jsonEscape(artifact.key)}\"],")
            append("\"primary_file\":\"${jsonEscape(artifact.key)}\"")
            append("}")
        }
    }

    /** Builds a multipart/form-data body: the `data` JSON part + the jar file part. */
    private fun multipartBody(boundary: String, dataJson: String, fileKey: String, jar: File): List<ByteArray> {
        val crlf = "\r\n"
        val parts = mutableListOf<ByteArray>()
        parts += ("--$boundary$crlf" +
            "Content-Disposition: form-data; name=\"data\"$crlf" +
            "Content-Type: application/json$crlf$crlf" +
            dataJson + crlf).toByteArray()
        parts += ("--$boundary$crlf" +
            "Content-Disposition: form-data; name=\"$fileKey\"; filename=\"${jar.name}\"$crlf" +
            "Content-Type: application/java-archive$crlf$crlf").toByteArray()
        parts += jar.readBytes()
        parts += ("$crlf--$boundary--$crlf").toByteArray()
        return parts
    }

    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
