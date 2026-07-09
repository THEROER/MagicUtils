package dev.ua.theroer.magicutils.build.support

import org.gradle.api.Project
import java.io.File
import java.net.ServerSocket

/**
 * Shared run-server port helpers for the standalone dev/smoke runners (bukkit,
 * fabric, neoforge Minecraft servers, and the velocity/bungee proxies). Kept in
 * one place so every bundle runner resolves the port the same way instead of
 * copying the logic.
 *
 * The compatibility smoke launches many servers in sequence; a fixed port
 * (25565) collides with a real server the user is running (or a parallel sweep).
 * So the runner takes an explicit `-PrunServerPort=N` when the caller needs a
 * specific port, and otherwise binds a free OS-assigned port at launch time.
 */

/** Property the runner reads for an explicit server/proxy port. */
const val MAGICUTILS_RUN_PORT_PROPERTY = "runServerPort"

/**
 * The port a standalone runner should bind: the value of [MAGICUTILS_RUN_PORT_PROPERTY]
 * when it is a positive integer, else a free OS-assigned port probed now.
 *
 * There is an unavoidable TOCTOU window between probing the free port and the
 * server binding it, but for the sequential smoke that window is tiny and the
 * alternative (a hard-coded port) collides far more often in practice.
 */
fun Project.magicUtilsResolveRunPort(): Int {
    val requested = (findProperty(MAGICUTILS_RUN_PORT_PROPERTY) as? String)?.trim()?.toIntOrNull()
    if (requested != null && requested > 0) return requested
    return ServerSocket(0).use { it.localPort }
}

/**
 * Sets `key=value` in a `server.properties`-style file, replacing the key in
 * place if present (ignoring commented lines) and appending it otherwise. Every
 * other line is left untouched, so the server's own generated properties survive.
 */
fun magicUtilsWriteServerProperty(file: File, key: String, value: String) {
    val lines = if (file.exists()) file.readLines().toMutableList() else mutableListOf()
    val idx = lines.indexOfFirst {
        it.substringBefore('=').trim() == key && !it.trimStart().startsWith("#")
    }
    if (idx >= 0) lines[idx] = "$key=$value" else lines.add("$key=$value")
    file.writeText(lines.joinToString("\n") + "\n")
}

/**
 * Prepares a Minecraft server run directory for an unattended dev/smoke launch:
 * creates it, accepts the EULA, and stamps `server-port` (a free port unless
 * [port] is given) + `online-mode` into server.properties. Returns the port used.
 *
 * Single source of truth for both the standalone bundle runners and the consumer
 * dev-server wiring, so the eula/port/offline-mode handling is not duplicated.
 * Callers that also want a MOTD or stale-jar cleanup layer those on top.
 */
fun Project.magicUtilsPrepareServerRunDir(runDir: File, port: Int? = null): Int {
    runDir.mkdirs()
    runDir.resolve("eula.txt").writeText("#Accepted via MagicUtils\neula=true\n")
    val resolvedPort = port ?: magicUtilsResolveRunPort()
    val props = runDir.resolve("server.properties")
    magicUtilsWriteServerProperty(props, "server-port", resolvedPort.toString())
    magicUtilsWriteServerProperty(props, "online-mode", "false")
    return resolvedPort
}

/** Property overriding the JDK major version the dev/smoke server runs on. */
const val MAGICUTILS_RUN_JAVA_PROPERTY = "runJavaVersion"

/**
 * The JDK major version a Minecraft server of [minecraftVersion] must run on:
 * `-PrunJavaVersion=N` when set, else Mojang's own requirement per version —
 * 1.20.4 and earlier need Java 17, 1.20.5 through 1.21.x need Java 21, and the
 * 26.x line needs Java 25. Running a server on a *newer* JDK than it supports
 * crashes the JVM natively (SIGABRT) — e.g. Paper 1.21 aborts on Java 25 — so
 * the smoke must pin the right JDK per sampled version, not use the build JVM.
 */
fun Project.magicUtilsRunJavaVersion(minecraftVersion: String): Int {
    (findProperty(MAGICUTILS_RUN_JAVA_PROPERTY) as? String)?.trim()?.toIntOrNull()
        ?.let { return it }
    val parts = minecraftVersion.trim().split('.').mapNotNull { it.toIntOrNull() }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }
    return when {
        major >= 26 -> 25
        major == 1 && minor == 20 && patch <= 4 -> 17
        else -> 21 // 1.20.5 .. 1.21.x
    }
}
