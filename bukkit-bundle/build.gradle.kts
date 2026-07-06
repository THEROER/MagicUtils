import dev.ua.theroer.magicutils.build.target.*

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
// Downloads Paper for the default target (1.21.10) and starts it with the
// freshly built MagicUtils plugin jar (the classifier-less shadowJar).
tasks.named<xyz.jpenilla.runpaper.task.RunServer>("runServer") {
    minecraftVersion("1.21.10")
    pluginJars(tasks.named<Jar>("shadowJar").flatMap { it.archiveFile })
    // Auto-accept the Minecraft EULA for unattended smoke runs (dev/test only).
    val runDir = layout.projectDirectory.dir("run")
    doFirst {
        val dir = runDir.asFile
        dir.mkdirs()
        dir.resolve("eula.txt").writeText("eula=true\n")
    }
}
