package dev.ua.theroer.magicutils.build.release

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
 * from the MODRINTH_TOKEN environment variable.
 */
private const val MODRINTH_API = "https://api.modrinth.com/v2"

internal fun registerModrinthTasks(project: Project, spec: ModrinthReleaseSpec?) {
    project.tasks.register("publishToModrinth", ModrinthPublishTask::class.java) { task ->
        task.group = "publishing"
        task.description = "Upload declared artifacts to Modrinth (-Pversion=X.Y.Z; MODRINTH_TOKEN env)."
        task.releaseSpec.set(spec)
        task.baseVersion.set(project.provider {
            (project.findProperty("version") as? String)
                ?: throw GradleException("Pass the release version via -Pversion=X.Y.Z.")
        })
        task.rootDir.set(project.rootDir.absolutePath)
        task.notCompatibleWithConfigurationCache("Performs network uploads.")
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

    @TaskAction
    fun run() {
        val spec = releaseSpec.orNull
            ?: throw GradleException(
                "No Modrinth release declared. Configure magicMatrix { modrinth { projectId = ...; artifact(...) } }."
            )
        if (spec.projectId.isBlank()) throw GradleException("Modrinth projectId is not set.")
        if (spec.artifacts.isEmpty()) throw GradleException("Modrinth release declares no artifacts.")

        val token = System.getenv("MODRINTH_TOKEN")?.takeIf { it.isNotBlank() }
            ?: throw GradleException("MODRINTH_TOKEN environment variable is not set.")

        val version = baseVersion.get()
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
        val existing = fetchExistingVersionNumbers(client, token, spec.projectId)

        for (artifact in spec.artifacts) {
            val versionNumber = spec.versionNumber(version, artifact)
            if (versionNumber in existing) {
                logger.lifecycle("Modrinth version '$versionNumber' already exists — skipping.")
                continue
            }
            val jar = resolveFile(artifact.file)
            uploadVersion(client, token, spec, artifact, versionNumber, jar)
            logger.lifecycle("Uploaded Modrinth version '$versionNumber' (${jar.name}).")
        }
    }

    private fun resolveFile(path: String): File {
        val file = File(path).let { if (it.isAbsolute) it else File(rootDir.get(), path) }
        if (!file.isFile) throw GradleException("Modrinth artifact file not found: ${file.absolutePath}")
        return file
    }

    private fun fetchExistingVersionNumbers(client: HttpClient, token: String, projectId: String): Set<String> {
        val request = HttpRequest.newBuilder(URI.create("$MODRINTH_API/project/$projectId/version?include_changelog=false"))
            .header("Authorization", token)
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw GradleException("Failed to list Modrinth versions (HTTP ${response.statusCode()}): ${response.body()}")
        }
        // Lightweight extraction of every "version_number":"..." without a JSON dep.
        return Regex("\"version_number\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(response.body())
            .map { it.groupValues[1] }
            .toSet()
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
            append("\"version_type\":\"${modrinthVersionType(spec.channel)}\",")
            append("\"loaders\":${arr(artifact.loaders)},")
            append("\"game_versions\":${arr(artifact.gameVersions)},")
            append("\"featured\":${spec.featured},")
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
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
