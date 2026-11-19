import dev.ua.theroer.magicutils.build.target.*
import dev.ua.theroer.magicutils.build.support.magicUtilsResolveRunPort

plugins {
    id("magicutils.velocity-bundle")
    // Local-only dev runner. Not part of the published build; provides `runVelocity`,
    // which downloads a Velocity proxy and boots it with the freshly built plugin.
    // Version pinned by build-logic (same run-* plugin family as run-paper).
    id("xyz.jpenilla.run-velocity")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.COMMON_MATRIX
}

// `./gradlew :velocity-bundle:runVelocity -Pscenario=velocity`
// Downloads Velocity and starts it with the freshly built MagicUtils plugin jar
// (the classifier-less shadowJar). Used by the compatibility smoke; Velocity
// prints the same `Done (` startup line the smoke greps for.
// Velocity's API is decoupled from Minecraft, so one proxy build covers a wide
// game-version range; the proxy version itself can still be overridden with
// `-PrunVelocityVersion=...` for a targeted compatibility check.
// Default proxy: the newest Velocity of the line this target compiles against —
// Velocity 3.5.1 (last 3.x, Adventure 4) for the 1.20/1.21 targets, Velocity 4.3.1
// (Adventure 5, Java 25) for 26.2+.
val runVelocityVersion = (findProperty("runVelocityVersion") as String?)
    ?.trim()?.takeIf { it.isNotEmpty() }
    ?: if (extensions.getByType<MagicUtilsTargetExtension>().usesAdventure5) "4.3.1" else "3.5.1"
tasks.named<xyz.jpenilla.runvelocity.task.RunVelocity>("runVelocity") {
    velocityVersion(runVelocityVersion)
    pluginJars(tasks.named<Jar>("shadowJar").flatMap { it.archiveFile })
    // Bind a free port (or -PrunServerPort=N) so the unattended smoke never
    // collides with a real proxy on 25565/25577. Velocity only loads plugins and
    // boots (we grep `Done (`); no client connects, so the exact port is
    // irrelevant. Patch the existing velocity.toml bind line when present.
    val runDir = layout.projectDirectory.dir("run")
    doFirst {
        val port = magicUtilsResolveRunPort()
        val toml = runDir.asFile.resolve("velocity.toml")
        if (toml.exists()) {
            val patched = toml.readText().replace(
                Regex("""(?m)^bind\s*=\s*".*"$"""),
                """bind = "0.0.0.0:$port""""
            )
            toml.writeText(patched)
        }
        logger.lifecycle("velocity-bundle runVelocity: on port $port")
    }
}
