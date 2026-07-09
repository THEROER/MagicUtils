import dev.ua.theroer.magicutils.build.target.*
import dev.ua.theroer.magicutils.build.support.magicUtilsResolveRunPort

plugins {
    id("magicutils.bungee-bundle")
    // Local-only dev runner. Not part of the published build; provides `runWaterfall`,
    // which downloads a Waterfall proxy (the maintained BungeeCord fork) and boots
    // it with the freshly built plugin. Version pinned by build-logic (same run-*
    // plugin family as run-velocity).
    id("xyz.jpenilla.run-waterfall")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.COMMON_MATRIX
}

// `./gradlew :bungee-bundle:runWaterfall -Pscenario=bungee`
// Downloads Waterfall and starts it with the freshly built MagicUtils plugin jar
// (the classifier-less shadowJar). Used by the compatibility smoke; Waterfall
// prints the same `Done (` startup line the smoke greps for.
// The BungeeCord API is decoupled from Minecraft, so one proxy build covers a
// wide game-version range; the proxy version itself can still be overridden with
// `-PrunWaterfallVersion=...` for a targeted compatibility check.
val runWaterfallVersion = (findProperty("runWaterfallVersion") as String?)
    ?.trim()?.takeIf { it.isNotEmpty() }
    ?: "1.20"
tasks.named<xyz.jpenilla.runwaterfall.task.RunWaterfall>("runWaterfall") {
    waterfallVersion(runWaterfallVersion)
    pluginJars(tasks.named<Jar>("shadowJar").flatMap { it.archiveFile })
    // Bind a free port (or -PrunServerPort=N) so the unattended smoke never
    // collides with a real proxy on 25565/25577. Waterfall only loads plugins and
    // boots (we grep `Done (`); no client connects, so the exact port is
    // irrelevant. Patch the existing config.yml listener host line when present.
    val runDir = layout.projectDirectory.dir("run")
    doFirst {
        val port = magicUtilsResolveRunPort()
        val config = runDir.asFile.resolve("config.yml")
        if (config.exists()) {
            val patched = config.readText().replace(
                Regex("""(?m)^(\s*host:\s*)\S+$"""),
                "$10.0.0.0:$port"
            )
            config.writeText(patched)
        }
        logger.lifecycle("bungee-bundle runWaterfall: on port $port")
    }
}
