import dev.ua.theroer.magicutils.build.target.*
import dev.ua.theroer.magicutils.build.support.magicUtilsPrepareServerRunDir
import dev.ua.theroer.magicutils.build.support.magicUtilsRunJavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("magicutils.neoforge-bundle")
    // The standalone entrypoint (@Mod class) compiles against the NeoForge API,
    // so this module needs ModDevGradle just like commands-neoforge does. The
    // magicutils.neoforge-bundle plugin only wires the jar-in-jar bundle content.
    id("net.neoforged.moddev")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.COMMON_MATRIX
}

val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)

// Local-only dev/smoke server run directory, isolated per Minecraft version so
// a newer version's generated config never crashes an older server (same reason
// the bukkit bundle isolates its run dir). The runtime Minecraft is pinned to
// the build target by ModDevGradle, so `-Ptarget=<mcXXXX>` selects the version.
val runServerDirectory = layout.projectDirectory.dir("run/${target.minecraft.get()}")

neoForge {
    version = target.neoforge.get()

    // Declare this project's source set as the `magicutils` mod so moddev's dev
    // run constructs the @Mod class (without a mods{} block no local mod loads).
    mods {
        register("magicutils") {
            sourceSet(sourceSets.getByName("main"))
        }
    }

    // Register a `runServer` for the compatibility smoke.
    //
    // KNOWN DEV LIMITATION: moddev's dev run loads the mod from the dev source set
    // (app classloader) while the server runs in FML's TRANSFORMER module layer, so
    // Minecraft types differ across the two → a ClassCastException when a command
    // touches an MC type. Syncing the published jar into run/mods instead trips a
    // JPMS ResolutionException (moddev loads it as a named module that phantom-
    // conflicts with `minecraft`). Neither is a problem for the PUBLISHED artifact:
    // it is a single flat-shaded mod jar (1519 classes + Adventure, 0 net.minecraft)
    // that a real NeoForge server loads via the FML mod-locator under one
    // classloader. So the neoforge bundle is publishable; only the moddev dev-smoke
    // can't exercise it end-to-end. Left runnable so the mod at least loads +
    // registers its command in dev.
    runs {
        register("server") {
            server()
            gameDirectory.set(runServerDirectory)
            programArgument("nogui")
        }
    }
}

// moddev puts the whole NeoForge jar on the runtime classpath; the shadowJar
// task (from magicutils.java-library) would then try to bundle it and blow past
// the 64k-entry zip limit. NeoForge is provided by the loader at runtime, so
// keep it out of the shadow archive — the same exclusion commands-neoforge uses.
val shadowRuntimeClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations["runtimeClasspath"])
    exclude(group = "net.neoforged")
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadowRuntimeClasspath)
}

// Expand ${version} in the mod metadata (like the fabric bundle does for
// fabric.mod.json) so the published mod carries the real version.
tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
}

// Dev-run only: put the MagicUtils runtime modules on `runtimeOnly` (non-
// transitive) + inherit bundleContents for Adventure/shaded config, so the dev
// mod loads and registers its command. (See the KNOWN DEV LIMITATION note above:
// the residual classloader split is a moddev-dev artifact, not a published-jar
// problem.) compileOnly wiring for the entrypoint is unchanged.
listOf(
    ":platform-api", ":logger", ":commands", ":commands-brigadier", ":placeholders",
    ":core", ":diagnostics", ":http-client", ":platform-neoforge", ":commands-neoforge",
    ":config", ":config-yaml", ":config-toml", ":lang",
).forEach { path ->
    (dependencies.add("runtimeOnly", project(path)) as ModuleDependency).isTransitive = false
}
configurations.named("runtimeOnly") {
    extendsFrom(configurations.named("bundleContents").get())
}

// Pin the server JDK to the target's Java (1.21.x → 21, 26.x → 25): moddev's
// runServer is a JavaExec that otherwise launches on the build JVM (25 here),
// and NeoForge for 1.21.10 aborts (exit 1) on Java 25 — the same JDK-mismatch
// the bukkit/fabric runners hit.
val nfRunJavaVersion = magicUtilsRunJavaVersion(target.minecraft.get())
val nfServerLauncher = extensions.getByType(JavaToolchainService::class.java).launcherFor {
    languageVersion.set(JavaLanguageVersion.of(nfRunJavaVersion))
}
tasks.named<JavaExec>("runServer").configure {
    standardInput = System.`in`
    javaLauncher.set(nfServerLauncher)
    doFirst {
        val port = magicUtilsPrepareServerRunDir(runServerDirectory.asFile)
        logger.lifecycle("neoforge-bundle runServer: Minecraft ${target.minecraft.get()} (Java $nfRunJavaVersion) on port $port")
    }
}
