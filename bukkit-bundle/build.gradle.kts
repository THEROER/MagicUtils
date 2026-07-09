import dev.ua.theroer.magicutils.build.target.*
import dev.ua.theroer.magicutils.build.support.magicUtilsPrepareServerRunDir
import dev.ua.theroer.magicutils.build.support.magicUtilsRunJavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    id("magicutils.bukkit-bundle")
    // Local-only dev runner. Not part of the published build; provides `runServer`.
    // 3.x uses the PaperMC v3 download API (v2 is gone / HTTP 410). No version:
    // run-paper is on the classpath via build-logic, which pins its version.
    id("xyz.jpenilla.run-paper")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.COMMON_MATRIX
}

// `./gradlew :bukkit-bundle:runServer -Pscenario=bukkit`
// Downloads Paper and starts it with the freshly built MagicUtils plugin jar
// (the classifier-less shadowJar). The server Minecraft version defaults to
// 1.21.10 but can be overridden with `-PrunMinecraftVersion=1.20.6` so the
// compatibility smoke can launch the *same* built jar on every Minecraft
// version across its advertised range (one jar, many server versions) —
// mirroring verified-plugin's `-PpaperRunVersion`.
val runMinecraftVersion = (findProperty("runMinecraftVersion") as String?)
    ?.trim()?.takeIf { it.isNotEmpty() }
    ?: "1.21.10"
// Isolate the run directory per Minecraft version: Paper writes version-specific
// config files (e.g. paper-world-defaults.yml), and a newer version's config
// crashes an older server on startup. A per-version dir means the compatibility
// smoke can sweep one jar across many versions without cross-contamination.
val runDirName = "run-$runMinecraftVersion"
// The server must run on the JDK Mojang requires for its Minecraft version, NOT
// the build JVM: Paper 1.21 aborts (SIGABRT) on Java 25. Resolve the right JDK
// via the toolchain service so run-paper launches the server with it.
val runJavaVersion = magicUtilsRunJavaVersion(runMinecraftVersion)
val serverLauncher = extensions.getByType(JavaToolchainService::class.java).launcherFor {
    languageVersion.set(JavaLanguageVersion.of(runJavaVersion))
}
// Accept EULA + bind a free port + offline mode via the shared run-dir helper.
// Shared by the Paper (runServer) and Folia (runFolia) tasks below.
fun xyz.jpenilla.runpaper.task.RunServer.prepareMagicUtilsRunDir(dir: org.gradle.api.file.Directory, label: String) {
    doFirst {
        val port = magicUtilsPrepareServerRunDir(dir.asFile)
        logger.lifecycle("bukkit-bundle $label: Minecraft $runMinecraftVersion on port $port")
    }
}

tasks.named<xyz.jpenilla.runpaper.task.RunServer>("runServer") {
    minecraftVersion(runMinecraftVersion)
    javaLauncher.set(serverLauncher)
    runDirectory.set(layout.projectDirectory.dir(runDirName))
    pluginJars(tasks.named<Jar>("shadowJar").flatMap { it.archiveFile })
    // Auto-accept the Minecraft EULA for unattended smoke runs (dev/test only).
    prepareMagicUtilsRunDir(layout.projectDirectory.dir(runDirName), "runServer")
}

// Folia is a Paper fork with regionised multithreading; a plugin must be Folia-
// aware to run on it, so smoke-testing it separately from Paper confirms the
// bundle's `folia` Modrinth loader claim. run-paper's `folia { registerTask }`
// downloads a Folia build for the same Minecraft version into its own run dir.
runPaper.folia {
    registerTask {
        minecraftVersion(runMinecraftVersion)
        javaLauncher.set(serverLauncher)
        val foliaDir = layout.projectDirectory.dir("run-folia-$runMinecraftVersion")
        runDirectory.set(foliaDir)
        pluginJars(tasks.named<Jar>("shadowJar").flatMap { it.archiveFile })
        prepareMagicUtilsRunDir(foliaDir, "runFolia")
    }
}
